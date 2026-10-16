# Incident response

## Service down

Acknowledge page, record UTC detection time, incident owner, and impact. Check readiness and liveness on port 9091, deployment events, available replicas, HPA, CPU/memory, restarts and last deployment. Compare edge failures with Prometheus scrape health. If correlated with a release, roll back with `kubectl rollout undo deployment/cloudguard`, then verify rollout status and run external requests. Do not restart healthy replicas merely because the dependency is unavailable.

## Dependency failure

Inspect 503 rate, downstream latency, circuit-open gauge, retries, and trace spans in Grafana Explore → Tempo. Read JSON logs by trace ID. Confirm DNS, network access and upstream health. Stop any fault experiment (`mode=healthy`) and check TTL. Reduce load or roll back a confirmed faulty change; do not increase retries during an outage. After upstream recovery, expect half-open probes after two seconds; require successful probes and sustained normal error rate before resolution. Escalate if the circuit stays open; verify the upstream independently.

## Latency or saturation

Inspect p95/p99, timeout rate, CPU throttling and thread pressure. HPA requires metrics-server and CPU requests. Verify scaling events and available capacity; max replicas is eight. Temporarily raise capacity only after identifying the bottleneck. Upstream limits can make scaling amplify failure.

## Root-cause analysis

Preserve deployment SHA, configuration, time range, request rates, error/latency histograms, trace IDs, logs and Kubernetes events. Separate trigger, failure mechanism, contributing conditions and detection gaps. Build a UTC timeline from first impact through mitigation and validated recovery. Quantify affected requests and budget consumed; identify missing data explicitly. Reproduce in the local simulator and add a regression test for the mechanism. Record corrective actions with owner, due date and verification criterion. Review without blame; close only after remediation evidence is attached.

## Repeatable exercise

Start a clean local service, run `scripts/load.py --recovery`, retain raw JSON and environment details, then repeat with a slow fault. Never run uncontrolled experiments on production. Recovery is first three consecutive successful probes after clearing the fault; it includes polling interval and is not an exact state-transition timestamp.
