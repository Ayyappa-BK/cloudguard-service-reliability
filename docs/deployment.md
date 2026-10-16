# Deployment

## Kubernetes

Build `docker build -t cloudguard:local .`; load into a local kind cluster using `kind load docker-image cloudguard:local`; apply `kubectl apply -f k8s/cloudguard.yml`. Supply a real downstream Service at `downstream:8081` and an OTel receiver at `otel:4318` or edit those environment values. The simulator is intentionally absent from production manifests. For a local cluster you can run the Compose simulator and configure a reachable host address.

Requires Kubernetes 1.30+, metrics-server for HPA, and Prometheus service discovery configured to honor pod annotations. Static Compose scraping is only for the local single-instance lab. Multiple replicas need per-pod scraping. ClusterIP keeps management private; restrict management ingress to the monitoring namespace using your cluster CNI policies. Add TLS/authentication at the ingress and configure pod placement across zones before production. CPU target is 60%, 2–8 replicas, 120-second downscale stabilization; PDB preserves one pod during voluntary disruptions.

## AWS (optional, billable)

Terraform provisions ECR, CloudWatch log group, an ErrorRate alarm, SNS topic, dashboard and an IRSA collector role. It intentionally uses an existing EKS cluster/OIDC provider; it does not create a paid cluster or networking. Set `region`, `cluster_name`, `oidc_provider_arn`, `oidc_issuer` via a noncommitted tfvars file. Use encrypted remote state with locking under your organization's standards.

```sh
cd terraform
terraform init
terraform fmt -check
terraform validate
terraform plan -out=review.tfplan
# Review costs and plan before applying:
terraform apply review.tfplan
```

Subscribe and confirm an SNS destination; a topic alone sends no human notification. Deploy `scripts/cloudwatch.py` with Python 3.13 + boto3 as an optional collector using serviceaccount `cloudguard-collector` annotated with the Terraform role ARN. Set PROMETHEUS_URL to your internal Prometheus endpoint; run one collector replica. It queries reset-aware counter increases aggregated across pods, emits ErrorRate percentage and request counts every minute, and skips empty intervals. Deploy with `pip install -r scripts/requirements-aws.txt && python3 scripts/cloudwatch.py` using the IRSA role. IAM permits PutMetricData only into CloudGuard. The alarm is a 3-of-5 one-minute threshold, separate from Prometheus multi-window burn alert. Missing traffic or exporter failures intentionally cause the CloudWatch alarm to enter ALARM.

Route JSON stdout via the existing Fluent Bit/CloudWatch Observability agent to `/cloudguard/service`; `monitoring/fluent-bit-output.conf` supplies the output configuration. Attach least-privilege log writer IAM to that existing agent (not the metrics role). Local observability requires no AWS. AWS resources and delivery integrations must be validated in a staging AWS account before production.

## CI/CD

CI uploads test and experiment evidence. A version tag publishes to GHCR. For AWS rollout set repository variables AWS_REGION, AWS_ROLE_ARN, EKS_CLUSTER, and create a protected `production` GitHub environment. The role must trust this repository/environment through GitHub OIDC and have EKS access mapped to Kubernetes RBAC for updating CloudGuard. Manually dispatch deployment with the tested immutable image reference; inspect diff and approve the protected environment. Workflow waits for rollout and fails on rollout failure; use the runbook for rollback. Terraform changes are reviewed and applied separately.
