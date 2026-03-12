EPIC-ID: EPIC-K-06
EPIC NAME: Observability Stack
LAYER: KERNEL
MODULE: K-06 Observability
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the K-06 Observability stack, providing unified structured logging, distributed tracing, and metrics collection for the entire platform. This epic enforces Principle 11 (No Kernel Duplication) by requiring all domain modules and plugins to use a single SDK for telemetry. It ensures deep visibility into platform performance, SLA/SLO tracking, and tenant-scoped observability with strict data residency enforcement.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Unified logging, tracing (OpenTelemetry based), and metrics aggregation.
  2. Single Observability SDK for all modules.
  3. Alerting framework with SLO/SLA tracking and routing.
  4. Tenant and Jurisdiction-scoped telemetry segregation.
  5. Correlation of logs/traces with dual-calendar timestamps.
- **Out-of-Scope:**
  1. Business-level audit logs (handled by K-07 Audit Framework, though K-06 monitors the performance of K-07).
- **Dependencies:** EPIC-K-15 (Dual-Calendar)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Unified SDK:** All modules must emit telemetry via a standardized SDK interface. Direct writes to external sinks (e.g., direct to Datadog/Prometheus) are prohibited.
2. **FR2 Context Propagation:** The stack must automatically propagate `trace_id`, `span_id`, `tenant_id`, and `jurisdiction` across all synchronous (API) and asynchronous (K-05 Event Bus) boundaries.
3. **FR3 Dual-Calendar Stamping:** All log entries must include `timestamp_gregorian` and `timestamp_bs`.
4. **FR4 Alerting Engine:** Evaluate metrics against defined thresholds and trigger alerts with runbook references.
5. **FR5 Data Residency Rules:** Telemetry data must be routed to jurisdiction-compliant storage sinks based on Config Engine rules.
6. **FR6 PII Masking:** Automatically redact/mask identified PII (e.g., National ID numbers) from all log payloads before storage.
7. **FR7 SLO/SLA Framework:** Provide a framework for defining Service Level Objectives (SLOs) and Service Level Agreements (SLAs) with metric targets, measurement windows, error budgets, and burn rate alerts. Support SLI (Service Level Indicator) definitions for availability, latency, throughput, and error rate.
8. **FR8 Alert Metadata:** All alerts must include runbook URL, severity level (P0/P1/P2/P3), escalation policy, and affected services/tenants. Alerts must be routable to appropriate on-call teams via integration with PagerDuty/OpsGenie.
9. **FR9 ML-Based PII Detection:** In addition to regex-based PII masking (FR6), integrate NER (Named Entity Recognition) models via K-09 AI Governance to detect PII in unstructured text fields (free-text comments, notes, descriptions). Combine ML detection with regex for comprehensive coverage. ML model runs asynchronously on log batches; any detected PII triggers retroactive redaction and alert. Fallback: regex-only masking if ML model is unavailable. [ARB P2-16]
10. **FR10 Critical User Journey SLIs:** Define and track Service Level Indicators for critical user journeys: (a) Order-to-Execution: time from order submission to exchange acknowledgment (target P99 < 100ms), (b) Settlement Finality: time from trade execution to settlement confirmation (target: T+1 or jurisdiction-defined), (c) Compliance Check: time from order submission to compliance decision (target P99 < 10ms), (d) Evidence Export: time from export request to package delivery (target < 5 minutes for standard requests). SLIs must be tracked with error budgets and burn-rate alerts. [ARB D.10]

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The observability collection and routing engine is jurisdiction-agnostic.
2. **Jurisdiction Plugin:** Residency rules (where logs can be stored) and PII regex patterns are defined in Jurisdiction Config Packs.
3. **Resolution Flow:** Telemetry pipeline inspects `jurisdiction` tags and routes accordingly.
4. **Hot Reload:** Alerting thresholds and routing rules can be updated dynamically.
5. **Backward Compatibility:** N/A
6. **Future Jurisdiction:** A new country implies a new storage sink and routing rule configuration, requiring zero code changes.

---

#### Section 5 — Data Model Impact

- **New Entities:** N/A (TimeSeries data, Trace spans)
- **Dual-Calendar Fields:** Standard log wrapper includes `timestamp_bs`.
- **Event Schema Changes:** N/A

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                   |
| ----------------- | ------------------------------------------------------------------------------------------------------------- |
| Event Name        | `SloBreachAlert`                                                                                              |
| Schema Version    | `v1.0.0`                                                                                                      |
| Trigger Condition | A predefined metric breaches its SLO threshold for a sustained duration.                                      |
| Payload           | `{ "metric": "api.latency.p99", "value": 450, "threshold": 200, "tenant_id": "...", "severity": "CRITICAL" }` |
| Consumers         | PagerDuty/OpsGenie integration, Admin Portal                                                                  |
| Idempotency Key   | `hash(alert_rule_id + time_window)`                                                                           |
| Replay Behavior   | Ignored.                                                                                                      |
| Retention Policy  | 1 year.                                                                                                       |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `ConfigureAlertCommand`                                              |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Metric exists, threshold valid, notification channel configured      |
| Handler          | `AlertCommandHandler` in K-06 Observability                          |
| Success Event    | `AlertConfigured`                                                    |
| Failure Event    | `AlertConfigurationFailed`                                           |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `AcknowledgeAlertCommand`                                            |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Alert exists, requester authorized                                   |
| Handler          | `AlertCommandHandler` in K-06 Observability                          |
| Success Event    | `AlertAcknowledged`                                                  |
| Failure Event    | `AlertAcknowledgmentFailed`                                          |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `CreateDashboardCommand`                                             |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Dashboard definition valid, metrics exist                            |
| Handler          | `DashboardCommandHandler` in K-06 Observability                      |
| Success Event    | `DashboardCreated`                                                   |
| Failure Event    | `DashboardCreationFailed`                                            |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Metric and log stream ingestion.
- **Model Registry Usage:** `telemetry-anomaly-detector-v1`
- **Explainability Requirement:** AI detects unusual patterns (e.g., sudden drop in order volume despite no latency increase) and alerts operators.
- **Human Override Path:** Operator marks alert as false positive to tune the model.
- **Drift Monitoring:** Monitored via feedback loop.
- **Fallback Behavior:** Standard static threshold alerting.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                  |
| ------------------------- | --------------------------------------------------------------------------------- |
| Latency / Throughput      | Telemetry emission must not block business threads; async batching < 1ms overhead |
| Scalability               | Horizontally scalable ingestion collectors                                        |
| Availability              | 99.99% uptime                                                                     |
| Consistency Model         | Eventual consistency for metrics/logs                                             |
| Security                  | mTLS for telemetry transit; PII masked                                            |
| Data Residency            | Enforced routing based on jurisdiction tags                                       |
| Data Retention            | Configurable (e.g., 30 days hot, 1 year cold)                                     |
| Auditability              | Changes to alert rules logged                                                     |
| Observability             | Meta-observability (metrics on the metrics pipeline)                              |
| Extensibility             | N/A                                                                               |
| Upgrade / Compatibility   | OpenTelemetry standard compliance                                                 |
| On-Prem Constraints       | Must support local sinks (e.g., local Prometheus/Grafana)                         |
| Ledger Integrity          | N/A                                                                               |
| Dual-Calendar Correctness | Log timestamps reflect accurate conversion                                        |

---

#### Section 10 — Acceptance Criteria

1. **Given** a request traversing API Gateway -> OMS -> Event Bus -> Risk Engine, **When** viewing the trace, **Then** all spans are linked under a single `trace_id` with accurate timing.
2. **Given** a log entry containing a National ID, **When** processed by the PII masker, **Then** the ID is replaced with `***REDACTED***` before storage.
3. **Given** an SLO defined as "API latency P99 < 200ms over 30-day window with 99.9% success rate", **When** the current error budget is 80% consumed, **Then** the system emits a burn rate alert to the on-call team.
4. **Given** an alert triggered for "Database connection pool exhausted", **When** generated, **Then** it includes runbook URL, severity P1, escalation policy, and affected tenants.
5. **Given** an SLO threshold of 200ms for API latency, **When** P99 exceeds 200ms for 5 minutes, **Then** an `SloBreachAlert` is generated.
6. **Given** telemetry tagged with jurisdiction `NP`, **When** processed, **Then** it is strictly routed to the Nepal-hosted log cluster, avoiding cross-border transfer.

---

#### Section 11 — Failure Modes & Resilience

- **Sink Unreachable:** Agents buffer data locally. If buffer fills, oldest telemetry is dropped to protect application memory.
- **High Volume Spike:** Agents enforce rate limiting/sampling dynamically to prevent overwhelming the ingestion tier.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                              |
| ------------------- | ------------------------------------------------------------- |
| Metrics             | Stack internal metrics: `log.ingest.rate`, `trace.drop.count` |
| Logs                | Internal collector logs                                       |
| Traces              | N/A                                                           |
| Audit Events        | Alert configuration changes                                   |
| Regulatory Evidence | Uptime and performance SLAs [ASR-OPS-001]                     |

---

#### Section 13 — Compliance & Regulatory Traceability

- Operational resilience and monitoring [ASR-OPS-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `TelemetryClient` (wrapping OpenTelemetry APIs).
- **Jurisdiction Plugin Extension Points:** Configurable sink routing and PII regex rules.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                             |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                  | Yes, via distinct routing rules.                                                                            |
| Can this run in an air-gapped deployment?                             | Yes, using local open-source sinks (Prometheus/Jaeger).                                                     |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. On-chain transaction tracing and token transfer metrics are captured via standard OpenTelemetry spans. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Sub-millisecond trace granularity and real-time dashboards support T+0 settlement monitoring.          |

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
