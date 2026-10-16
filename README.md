# CloudGuard

Java 21 / Spring Boot service reliability lab with real HTTP dependency calls, bounded timeouts, retry and circuit-breaker recovery, SLO metrics, traces, local fault injection, and optional AWS monitoring.

## Run locally (no AWS account or charges)

Install JDK 21 and Maven 3.9+, then:

```sh
mvn -B verify
bash scripts/local.sh
# in another terminal
curl localhost:8080/api/check
curl localhost:9091/actuator/health/readiness
curl localhost:9091/actuator/health/liveness
python3 scripts/load.py --requests 1000 --concurrency 8 --recovery
```

Or install Docker Compose and run `docker compose up --build`. Service: localhost:8080, Prometheus: localhost:9090, Grafana: localhost:3000. Grafana provisions the CloudGuard dashboard and Tempo trace datasource. Management and monitoring ports bind to loopback. Stop with `docker compose down`. Anonymous Grafana access is local demo only.

```sh
curl -X POST localhost:8081/fault -H 'Content-Type: application/json' -d '{"mode":"error","ttl":10}'
curl -X POST localhost:8081/fault -H 'Content-Type: application/json' -d '{"mode":"slow","ttl":10}'
curl -X POST localhost:8081/fault -H 'Content-Type: application/json' -d '{"mode":"healthy","ttl":10}'
```

Faults expire automatically within 60 seconds. Never deploy this simulator or its unauthenticated control interface to production.

## Design

Client → stateless CloudGuard → HTTP downstream. A circuit breaker surrounds a retry operation: each logical request counts once toward the circuit failure rate. Retries apply only to transport errors and upstream 5xx; two attempts, 50ms backoff, 200ms connect and 300ms read timeout per attempt. No retries on non-idempotent operations are implemented. Open circuit rejects immediately; after two seconds two successful probes close it. Each replica has independent breaker state. Graceful shutdown drains requests; Kubernetes removes unready pods. Liveness intentionally excludes dependency health to avoid restart cascades.

Micrometer records `cloudguard_requests_total{outcome}` and `cloudguard_request_latency_seconds_*`; Spring also emits HTTP server metrics. Boot's observed RestClient propagates W3C trace context through Micrometer/OpenTelemetry. JSON console logs include trace/span IDs when a span is active. Local traces flow through OTel Collector into Tempo. Production should reduce sampling from 100%.

See [SLO definitions](docs/slo.md), [operations](docs/runbooks.md), [AWS and Kubernetes deployment](docs/deployment.md), and [measured results](results/README.md).

## Verification and delivery

`mvn verify` runs JUnit unit, real HTTP integration, timeout and recovery tests. `python3 scripts/load.py --recovery` runs the repeatable external experiment. GitHub Actions verifies Java 21, Docker Compose, Prometheus rules, Terraform, and Kubernetes schemas, builds a container, and reruns the experiment. Tags `v*` publish an immutable commit-tagged image to GHCR after validation. The optional, manually approved deployment workflow rolls that image into an existing EKS cluster using GitHub OIDC. No AWS resources are created by CI.

If publishing manually:

```sh
git init -b main
git add . && git commit -m "Build CloudGuard reliability platform"
gh auth login
gh repo create cloudguard-service-reliability --public --source=. --remote=origin --push
gh run list
```
