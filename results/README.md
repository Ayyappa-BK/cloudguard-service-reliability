# Actual measured results

Measured October 16, 2026 on local macOS ARM64 using Temurin Java 21.0.12.1, Python 3.13, one Java replica and one real HTTP simulator, concurrency eight. No AWS resources were used. These are short, unpaced loopback load experiments, not production capacity claims or proof of a rolling 30-day SLO. Raw JSON includes timestamp, duration, errors, fault status codes and fault latencies. Baseline availability includes transport errors. Deliberate fault and recovery probes are recorded separately and excluded from baseline figures.

| Experiment | Baseline requests | Availability | Requests/s | p95 ms | p99 ms | Recovery s |
|---|---:|---:|---:|---:|---:|---:|
| [local-java21.json](local-java21.json) | 1,000 | 100.00000% | 1170.9 | 5.71 | 14.81 | 2.33 |
| [sustained-java21.json](sustained-java21.json) | 143,830 | 99.99513% | 4786.7 | 2.49 | 4.51 | 2.36 |
| [slow-fault-java21.json](slow-fault-java21.json) | 1,000 | 100.00000% | 1511.9 | 3.63 | 55.33 | 2.38 |
| [sustained-repeat-java21.json](sustained-repeat-java21.json) | 129,427 | 100.00000% | 4312.9 | 3.33 | 6.45 | 2.35 |

The first 30-second run had seven transient failures and failed its original strict zero-error assertion. Its observed 99.99513% availability exceeded the 99.9% sample objective, but the failures remain in the evidence. The simulator's listen backlog was then raised from Python's default to 128 and a fresh service was tested: the second 30-second run completed 129,427 requests without baseline errors. This comparison is not sufficient to prove backlog exhaustion caused the initial failures; concurrent build activity and host scheduling are confounders. No full root cause is claimed.

Error injection produced six HTTP 503 responses in every experiment. Slow injection's first three requests took approximately 0.66–0.68 seconds (two bounded attempts with backoff), then the open circuit rejected subsequent calls in about 3–4ms. After clearing faults, three consecutive successful probes arrived in about 2.3–2.4 seconds. This includes the circuit wait and polling interval; it is not an exact state transition duration. Counters and histograms from the final run are in [prometheus-snapshot.txt](prometheus-snapshot.txt).

Reproduce from a clean local service:

```sh
python3 scripts/load.py --requests 1000 --concurrency 8 --recovery
python3 scripts/load.py --duration 30 --concurrency 8 --recovery --min-availability 99.9
python3 scripts/load.py --requests 1000 --concurrency 8 --recovery --fault slow --output results/slow.json
```

The default load assertion requires 100% baseline success; the sustained command explicitly checks 99.9%. All fault phases must recover within ten seconds. Eight JUnit tests pass locally on Java 21, including real HTTP timeout/recovery, retry classification, trace propagation, and management endpoints/Prometheus export. Terraform init, format and validate passed locally. Container, Prometheus rule tests, Kubernetes schema checks and full monitoring pipeline are verified in [GitHub Actions](https://github.com/Ayyappa-BK/cloudguard-service-reliability/actions). AWS apply and EKS rollout require an existing account/cluster and have not been executed.
