# TutorPutor SLO Definitions

**Product:** TutorPutor  
**Date:** 2026-04-27  
**Status:** Approved  
**Owner:** Platform Engineering

---

## Overview

This document defines Service Level Objectives (SLOs) for every critical TutorPutor user journey. Each SLO covers:

- **Availability** — the fraction of time the feature is accessible to users
- **Latency** — p50/p95/p99 response time bounds
- **Error rate** — the fraction of requests that return a 5xx or unrecoverable client-impacting error
- **Throughput** (where applicable) — minimum sustained request rate

SLOs are backed by Prometheus metrics exposed at `/metrics` on the `tutorputor-platform` service and forwarded to Grafana.

---

## Critical Journey SLOs

### SLO-01: Learner Dashboard Load

| Metric | Target | Alert Threshold | Window |
|---|---|---|---|
| Availability | ≥ 99.5% | < 99% | 30-day rolling |
| Latency p50 | ≤ 200 ms | > 400 ms | 5-min |
| Latency p95 | ≤ 800 ms | > 1500 ms | 5-min |
| Latency p99 | ≤ 2000 ms | > 3000 ms | 5-min |
| Error rate | ≤ 0.5% | > 1% | 5-min |

**Measurement:** `GET /api/v1/learning/dashboard` — http_request_duration_seconds{route="/api/v1/learning/dashboard"}

---

### SLO-02: Module Detail Load

| Metric | Target | Alert Threshold | Window |
|---|---|---|---|
| Availability | ≥ 99.5% | < 99% | 30-day rolling |
| Latency p50 | ≤ 300 ms | > 600 ms | 5-min |
| Latency p95 | ≤ 1000 ms | > 2000 ms | 5-min |
| Error rate | ≤ 0.5% | > 1% | 5-min |

**Measurement:** `GET /api/v1/modules/:slug` — http_request_duration_seconds{route="/api/v1/modules/:slug"}

---

### SLO-03: AI Tutor Query Response

| Metric | Target | Alert Threshold | Window |
|---|---|---|---|
| Availability | ≥ 99% | < 98% | 30-day rolling |
| Latency p50 | ≤ 2000 ms | > 4000 ms | 5-min |
| Latency p95 | ≤ 6000 ms | > 10000 ms | 5-min |
| Error rate | ≤ 1% | > 3% | 5-min |
| Rate limit breaches | ≤ 2% | > 5% | 1-min |

**Measurement:** `POST /api/v1/ai/tutor/query` — http_request_duration_seconds{route="/api/v1/ai/tutor/query"}

**Notes:** LLM upstream latency variability expected; p95 and p99 are intentionally relaxed vs. synchronous APIs.

---

### SLO-04: Content Generation Job Completion

| Metric | Target | Alert Threshold | Window |
|---|---|---|---|
| Job completion rate | ≥ 98% | < 95% | 1-hour rolling |
| Job p50 duration | ≤ 30 s | > 60 s | 1-hour rolling |
| Job p95 duration | ≤ 120 s | > 300 s | 1-hour rolling |
| Queue backlog depth | ≤ 50 jobs | > 200 jobs | 1-min |
| Dead-letter queue depth | ≤ 5 | > 10 | 5-min |

**Measurement:**
- `tutorputor_content_generation_job_duration_seconds` (histogram)
- `tutorputor_content_generation_jobs_total{status="completed|failed"}`
- `tutorputor_queue_backlog_depth{queue="content-generation"}`

---

### SLO-05: Publish Validation

| Metric | Target | Alert Threshold | Window |
|---|---|---|---|
| Availability | ≥ 99.5% | < 99% | 30-day rolling |
| Latency p95 | ≤ 3000 ms | > 6000 ms | 5-min |
| Error rate | ≤ 1% | > 2% | 5-min |

**Measurement:** `POST /api/content-studio/experiences/:id/publish` — http_request_duration_seconds{route="/api/content-studio/experiences/:id/publish"}

---

### SLO-06: Simulation Render Start

| Metric | Target | Alert Threshold | Window |
|---|---|---|---|
| Availability | ≥ 99% | < 98% | 30-day rolling |
| Time-to-interactive p50 | ≤ 2000 ms | > 4000 ms | 5-min |
| Time-to-interactive p95 | ≤ 5000 ms | > 8000 ms | 5-min |
| Error rate | ≤ 1% | > 2% | 5-min |

**Measurement:** `GET /api/v1/modules/:slug/simulation` (client-side: simulation_render_start_ms histogram via OpenTelemetry)

---

### SLO-07: LTI Launch

| Metric | Target | Alert Threshold | Window |
|---|---|---|---|
| Availability | ≥ 99.9% | < 99.5% | 30-day rolling |
| Latency p95 | ≤ 1000 ms | > 2000 ms | 5-min |
| Error rate | ≤ 0.1% | > 0.5% | 5-min |

**Measurement:** `POST /api/v1/integration/lti/launch` — http_request_duration_seconds{route="/api/v1/integration/lti/launch"}

**Notes:** LTI launch failures have immediate classroom impact; tighter error threshold than most routes.

---

### SLO-08: Payment Checkout

| Metric | Target | Alert Threshold | Window |
|---|---|---|---|
| Availability | ≥ 99.9% | < 99.5% | 30-day rolling |
| Latency p95 | ≤ 3000 ms | > 6000 ms | 5-min |
| Error rate | ≤ 0.5% | > 1% | 5-min |
| Stripe webhook ack rate | ≥ 99.9% | < 99% | 1-hour rolling |

**Measurement:** `POST /api/v1/integration/billing/checkout` and `POST /api/v1/integration/billing/webhook`

---

## Dependency Health SLOs

### DEP-01: PostgreSQL

| Metric | Target | Alert Threshold |
|---|---|---|
| Query latency p95 | ≤ 100 ms | > 300 ms |
| Connection pool exhaustion | 0 events/hour | > 1 event/hour |
| Replication lag | ≤ 10 s | > 60 s |

### DEP-02: Redis

| Metric | Target | Alert Threshold |
|---|---|---|
| Command latency p95 | ≤ 5 ms | > 20 ms |
| Memory usage | ≤ 80% | > 90% |
| Eviction rate | 0 keys/min | > 5 keys/min |

### DEP-03: Content Generation gRPC

| Metric | Target | Alert Threshold |
|---|---|---|
| Availability | ≥ 99.5% | < 99% |
| RPC latency p95 | ≤ 5000 ms | > 10000 ms |
| Circuit breaker opens | 0/hour | > 3/hour |

### DEP-04: LLM Provider

| Metric | Target | Alert Threshold |
|---|---|---|
| Availability (platform-side) | ≥ 99% | < 97% |
| Response latency p95 | ≤ 10000 ms | > 20000 ms |
| Error rate | ≤ 2% | > 5% |

### DEP-05: Stripe

| Metric | Target | Alert Threshold |
|---|---|---|
| API latency p95 | ≤ 2000 ms | > 5000 ms |
| Webhook delivery success | ≥ 99.9% | < 99% |

### DEP-06: Email / Push Delivery

| Metric | Target | Alert Threshold |
|---|---|---|
| Delivery success rate | ≥ 99% | < 98% |
| Delivery latency p95 | ≤ 60 s | > 300 s |

---

## Alert Policy Definitions

Alert policies are implemented as Prometheus alerting rules. Rules file: `monitoring/tutorputor/alerts/slo-alerts.yml`.

### Critical Alerts (page on-call immediately)

```yaml
# SLO-01: Learner Dashboard — high error rate
- alert: TutorPutorDashboardHighErrorRate
  expr: |
    rate(http_requests_total{service="tutorputor-platform",route="/api/v1/learning/dashboard",status=~"5.."}[5m])
    / rate(http_requests_total{service="tutorputor-platform",route="/api/v1/learning/dashboard"}[5m]) > 0.01
  for: 2m
  labels:
    severity: critical
    product: tutorputor
    slo: SLO-01
  annotations:
    summary: "TutorPutor learner dashboard error rate exceeded 1%"
    runbook: "https://docs.tutorputor.internal/runbooks/dashboard-errors"

# SLO-07: LTI Launch — any error spike
- alert: TutorPutorLTILaunchErrors
  expr: |
    rate(http_requests_total{service="tutorputor-platform",route="/api/v1/integration/lti/launch",status=~"5.."}[5m])
    / rate(http_requests_total{service="tutorputor-platform",route="/api/v1/integration/lti/launch"}[5m]) > 0.005
  for: 1m
  labels:
    severity: critical
    product: tutorputor
    slo: SLO-07
  annotations:
    summary: "TutorPutor LTI launch error rate exceeded 0.5%"
    runbook: "https://docs.tutorputor.internal/runbooks/lti-errors"

# SLO-04: Content generation queue backup
- alert: TutorPutorContentQueueBacklog
  expr: tutorputor_queue_backlog_depth{queue="content-generation"} > 200
  for: 5m
  labels:
    severity: critical
    product: tutorputor
    slo: SLO-04
  annotations:
    summary: "TutorPutor content generation queue depth exceeded 200 jobs"
    runbook: "https://docs.tutorputor.internal/runbooks/content-generation-queue"
```

### Warning Alerts (notify team channel, no page)

```yaml
# SLO-03: AI tutor — slow responses
- alert: TutorPutorAITutorSlowP95
  expr: |
    histogram_quantile(0.95,
      rate(http_request_duration_seconds_bucket{service="tutorputor-platform",route="/api/v1/ai/tutor/query"}[5m])
    ) > 10
  for: 5m
  labels:
    severity: warning
    product: tutorputor
    slo: SLO-03
  annotations:
    summary: "TutorPutor AI tutor p95 latency exceeded 10s"

# DEP-01: Postgres slow queries
- alert: TutorPutorPostgresSlowQueries
  expr: |
    histogram_quantile(0.95,
      rate(db_query_duration_seconds_bucket{service="tutorputor-platform"}[5m])
    ) > 0.3
  for: 5m
  labels:
    severity: warning
    product: tutorputor
    dependency: postgres
  annotations:
    summary: "TutorPutor Postgres p95 query time exceeded 300ms"

# DEP-02: Redis memory pressure
- alert: TutorPutorRedisHighMemory
  expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.9
  for: 5m
  labels:
    severity: warning
    product: tutorputor
    dependency: redis
  annotations:
    summary: "TutorPutor Redis memory usage exceeded 90%"

# SLO-04: Content generation dead-letter queue
- alert: TutorPutorContentDLQDepth
  expr: tutorputor_queue_depth{queue="content-generation-dlq"} > 10
  for: 5m
  labels:
    severity: warning
    product: tutorputor
    slo: SLO-04
  annotations:
    summary: "TutorPutor content generation dead-letter queue exceeded 10 jobs"
```

---

## Grafana Dashboard Layout

The SLO dashboard is deployed at `monitoring/tutorputor/dashboards/slo-dashboard.json` and visible at:

**URL:** http://localhost:3001/d/tutorputor-slo/tutorputor-slos

### Panels

| Panel | Metric | Visualization |
|---|---|---|
| Learner Dashboard Latency | SLO-01 p50/p95/p99 | Time-series |
| AI Tutor Response Time | SLO-03 p50/p95 | Time-series |
| Content Generation Queue | SLO-04 backlog + DLQ | Gauge + Time-series |
| LTI Launch Error Rate | SLO-07 5xx rate | Stat + Time-series |
| Payment Webhook Success | SLO-08 ack rate | Stat |
| Dependency Health | All DEP SLOs | Status grid |
| Error Budget Burn Rate | All SLOs | Burn-rate panel |

---

## Error Budget Policy

Each SLO has an **error budget** = 1 - availability target over the measurement window.

| SLO | Monthly Error Budget | Policy When Budget < 10% |
|---|---|---|
| SLO-01 Learner Dashboard | 3.6 hours/month | Feature freeze; all eng on reliability |
| SLO-03 AI Tutor | 7.2 hours/month | Reduce AI traffic, enable fallback |
| SLO-04 Content Generation | N/A (job-based) | Pause new content jobs; drain queue |
| SLO-07 LTI Launch | 26 min/month | Immediate escalation; notify institution admins |
| SLO-08 Payment Checkout | 26 min/month | Immediate escalation; notify finance |

---

## Audit / Provenance Dashboard

A separate dashboard at `monitoring/tutorputor/dashboards/content-trust.json` tracks:

- Content trust score distribution (histogram by domain)
- Human review queue depth and age
- Auto-rejected content rate
- Provenance graph coverage (% of generated assertions with source citations)
- Simulation correctness failure rate by domain

---

*Last updated: 2026-04-27*
