# SLIs and error budget

Scope: every completed `/api/check` request observed at the service, including circuit-open responses. Good availability event = HTTP 200; every other response or client-observed transport failure is bad. Service counters measure completed responses; external load probes include connection failures. Production edge telemetry is required to include requests that never reach a pod. Health, scrape, and simulator routes are excluded.

Availability objective: 99.9% over a rolling 30 days. Error budget = 0.001 × eligible requests. Equivalent time budget under uniform traffic is 43.2 minutes per 30 days. Latency SLI = fraction of all completed eligible requests ≤300ms, including failed responses; target 99%. Availability is evaluated alongside latency so fast errors cannot appear healthy. Aggregate histogram buckets and counters across replicas before division; never average pod percentiles.

PromQL 30-day availability:

```promql
sum(increase(cloudguard_requests_total{outcome="success"}[30d]))
/ sum(increase(cloudguard_requests_total[30d]))
```

Latency uses `cloudguard_request_latency_seconds_bucket{le="0.3"}` divided by count over the same window. Prometheus local retention defaults to 15 days; use `--storage.tsdb.retention.time=35d` and persistent storage for a real 30-day evaluation. Zero traffic is undefined, not 100% available. Missing scrapes have a separate alert and require external synthetic monitoring.

Fast-burn alert requires both 5-minute and 1-hour error ratios above 1.44% (14.4× budget burn). Local rules demonstrate failure detection; route Alertmanager to an approved pager destination before production. A short benchmark is evidence of tested behavior only and cannot establish long-term SLO compliance.
