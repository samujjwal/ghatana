EPIC-ID: EPIC-K-19
EPIC NAME: DLQ Management & Event Replay Tooling
LAYER: KERNEL
MODULE: K-19 DLQ Management & Event Replay
VERSION: 1.0.1
ARB-REF: P0-04

---

#### Section 1 — Objective

Deliver the K-19 DLQ Management & Event Replay module to prevent silent data loss from poison-pill events and provide operational tooling for dead letter queue monitoring, root cause analysis, and safe replay. This epic directly remediates ARB finding P0-04 (No Dead Letter Queue Processing Strategy).

---

#### Section 2 — Scope

- **In-Scope:**
  1. DLQ monitoring dashboard with real-time metrics and alerting.
  2. Automated DLQ threshold alerting (size, age, growth rate).
  3. Root cause analysis (RCA) workflow requirement before replay.
  4. Manual and automated replay with safety controls.
  5. Poison pill quarantine and classification.
  6. Integration with K-05 Event Bus DLQ infrastructure.
- **Out-of-Scope:**
  1. The DLQ storage itself (provided by K-05 Event Bus).
  2. Domain-specific event handling logic.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-06 (Observability), EPIC-K-07 (Audit Framework), EPIC-K-13 (Admin Portal)
- **Kernel Readiness Gates:** K-05 must be stable.
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 DLQ Monitoring Dashboard:** Provide a real-time dashboard (integrated into K-13 Admin Portal) showing: DLQ size per topic/consumer group, event age distribution, failure reason breakdown, growth rate, and replay history.
2. **FR2 Threshold Alerting:** Emit alerts via K-06 when: (a) DLQ size exceeds configurable threshold (default: 100 events), (b) oldest DLQ event exceeds age threshold (default: 1 hour), (c) DLQ growth rate exceeds threshold (default: 10 events/minute sustained for 5 minutes). Alert severity escalates: P2 → P1 → P0 based on thresholds.
3. **FR3 Root Cause Analysis Requirement:** Before any DLQ replay, an operator must record a root cause analysis (RCA) entry specifying: failure category (schema mismatch, transient error, bug, data corruption), fix applied (code fix, config change, data correction), and approval (maker-checker for production replays).
4. **FR4 Safe Replay:** Provide replay tooling that: (a) replays events one-at-a-time or in batches with configurable parallelism, (b) validates each event against current schema before replay, (c) applies idempotency checks to prevent duplicate processing, (d) pauses replay if new failures exceed threshold, (e) reports replay progress and success/failure counts.
5. **FR5 Poison Pill Quarantine:** Events that fail replay after configurable max attempts (default: 3) are moved to a permanent quarantine with classification (unprocessable, schema mismatch, data corruption). Quarantined events require manual resolution or explicit discard with audit trail.
6. **FR6 Automated Replay for Transient Failures:** For events classified as transient failures (e.g., downstream timeout), support automated delayed replay with exponential backoff (1 min, 5 min, 30 min). Requires pre-configuration per consumer group.
7. **FR7 DLQ Event Enrichment:** DLQ entries must retain: original event, original topic, consumer group, failure timestamp, failure reason/stacktrace, retry count, and correlation IDs (trace_id, saga_id).
8. **FR8 Dual-Calendar Support:** All DLQ timestamps use dual-calendar via K-15.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** All DLQ management logic is jurisdiction-agnostic.
2. **Jurisdiction Plugin:** N/A.
3. **Resolution Flow:** N/A.
4. **Hot Reload:** Alert thresholds and replay policies hot-reloadable via K-02.
5. **Backward Compatibility:** N/A.
6. **Future Jurisdiction:** No changes needed.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `DlqEntry`: `{ dlq_id: UUID, original_event_id: UUID, topic: String, consumer_group: String, failure_reason: String, stack_trace: String, retry_count: Int, status: Enum(PENDING, REPLAYING, QUARANTINED, RESOLVED, DISCARDED), created_at: DualDate }`
  - `DlqReplayRecord`: `{ replay_id: UUID, dlq_id: UUID, rca_id: UUID, replayed_by: String, result: Enum(SUCCESS, FAILED), replayed_at: DualDate }`
  - `RcaRecord`: `{ rca_id: UUID, category: Enum, description: String, fix_applied: String, approved_by: String, created_at: DualDate }`
- **Dual-Calendar Fields:** `created_at` in all entities.
- **Event Schema Changes:** `DlqThresholdBreachedEvent`, `DlqReplayCompletedEvent`, `DlqEventQuarantinedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                        |
| ----------------- | -------------------------------------------------------------------------------------------------- |
| Event Name        | `DlqThresholdBreachedEvent`                                                                        |
| Schema Version    | `v1.0.0`                                                                                           |
| Trigger Condition | DLQ size, age, or growth rate exceeds configured threshold.                                        |
| Payload           | `{ "topic": "...", "consumer_group": "...", "dlq_size": 150, "threshold": 100, "severity": "P1" }` |
| Consumers         | K-06 Alerting, Admin Portal, On-Call Escalation                                                    |
| Idempotency Key   | `hash(topic + consumer_group + threshold_type + window)`                                           |
| Replay Behavior   | N/A (alert event).                                                                                 |
| Retention Policy  | 90 days.                                                                                           |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                            |
| ---------------- | ---------------------------------------------------------------------- |
| Command Name     | `ReplayDlqEventsCommand`                                               |
| Schema Version   | `v1.0.0`                                                               |
| Validation Rules | RCA record exists and approved, DLQ events exist, requester authorized |
| Handler          | `DlqCommandHandler` in K-19                                            |
| Success Event    | `DlqReplayCompleted`                                                   |
| Failure Event    | `DlqReplayFailed`                                                      |
| Idempotency      | Command ID must be unique; duplicate commands return replay status     |

| Field            | Description                                                                     |
| ---------------- | ------------------------------------------------------------------------------- |
| Command Name     | `DiscardDlqEventCommand`                                                        |
| Schema Version   | `v1.0.0`                                                                        |
| Validation Rules | Event in quarantine, RCA recorded, maker-checker approval, requester authorized |
| Handler          | `DlqCommandHandler` in K-19                                                     |
| Success Event    | `DlqEventDiscarded`                                                             |
| Failure Event    | `DlqDiscardFailed`                                                              |
| Idempotency      | Command ID must be unique                                                       |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Pattern Recognition
- **Workflow Steps Exposed:** DLQ failure analysis.
- **Model Registry Usage:** `dlq-failure-classifier-v1`
- **Explainability Requirement:** AI classifies DLQ failures into categories (transient, schema, bug, data) to assist RCA.
- **Human Override Path:** Operator can override AI classification.
- **Drift Monitoring:** Classification accuracy monitored.
- **Fallback Behavior:** Manual classification by operator.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                               |
| ------------------------- | ---------------------------------------------------------------------------------------------- |
| Latency / Throughput      | Dashboard refresh < 5s; replay throughput configurable (default: 100 events/sec)               |
| Scalability               | Horizontally scalable replay workers                                                           |
| Availability              | 99.99% uptime                                                                                  |
| Consistency Model         | Strong consistency for DLQ state                                                               |
| Security                  | Replay actions require maker-checker for production; RBAC-restricted                           |
| Data Residency            | DLQ data follows K-08 residency rules                                                          |
| Data Retention            | DLQ entries retained per audit policy; quarantined events retained indefinitely until resolved |
| Auditability              | All replay and discard actions logged to K-07                                                  |
| Observability             | Metrics: `dlq.size`, `dlq.age.max`, `dlq.replay.success_rate`, `dlq.quarantine.count`          |
| Extensibility             | Custom failure classifiers via plugin                                                          |
| Upgrade / Compatibility   | Backward compatible API                                                                        |
| On-Prem Constraints       | Operates with local storage                                                                    |
| Ledger Integrity          | Ensures no silent loss of financial events                                                     |
| Dual-Calendar Correctness | All timestamps use DualDate                                                                    |

---

#### Section 10 — Acceptance Criteria

1. **Given** a DLQ with 150 events (threshold: 100), **When** the monitoring job runs, **Then** a `DlqThresholdBreachedEvent` is emitted with severity P1.
2. **Given** an operator with an approved RCA, **When** they initiate replay of 50 DLQ events, **Then** events are replayed with idempotency checks and a progress report is provided.
3. **Given** a poison pill event that fails replay 3 times, **When** the max retry is exhausted, **Then** the event is moved to quarantine and `DlqEventQuarantinedEvent` is emitted.
4. **Given** a quarantined event, **When** an operator discards it without RCA and maker-checker approval, **Then** the discard is rejected.
5. **Given** a transient failure classification, **When** automated replay is configured, **Then** the event is retried with exponential backoff without manual intervention.

---

#### Section 11 — Failure Modes & Resilience

- **DLQ Storage Full:** Alert raised; oldest resolved entries purged; replay paused.
- **Replay Worker Failure:** Replay pauses; resumes from last successful event on recovery.
- **Dashboard Unavailable:** Alerting continues via K-06; dashboard is non-critical path.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                                        |
| ------------------- | ------------------------------------------------------------------------------------------------------- |
| Metrics             | `dlq.size` (gauge), `dlq.replay.count` (counter), `dlq.quarantine.count` (gauge), `dlq.age.max` (gauge) |
| Logs                | Structured: `dlq_id`, `topic`, `action`, `result`                                                       |
| Traces              | Span for replay operations linked to original event trace                                               |
| Audit Events        | `DlqReplayInitiated`, `DlqEventDiscarded`, `DlqRcaRecorded` [LCA-AUDIT-001]                             |
| Regulatory Evidence | DLQ resolution records for compliance audit                                                             |

---

#### Section 13 — Compliance & Regulatory Traceability

- Data integrity assurance [LCA-DATA-001]
- Audit trails for event resolution [LCA-AUDIT-001]
- Operational resilience evidence [LCA-OPS-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `DlqClient.getStatus(topic, consumerGroup)`, `DlqClient.replay(dlqIds, rcaId)`, `DlqClient.quarantine(dlqId)`, `DlqClient.discard(dlqId, reason)`.
- **Jurisdiction Plugin Extension Points:** N/A.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                                              |
| --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin?                  | Yes, infrastructure is jurisdiction-agnostic.                                                                                |
| Can new failure classifiers be added?                                 | Yes, via AI model registry or plugin.                                                                                        |
| Can this run in an air-gapped deployment?                             | Yes, uses local storage.                                                                                                     |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. Failed token transfer events and smart-contract revert messages are classified and quarantined with full chain context. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Priority-based DLQ processing ensures T+0 settlement failures are retried within SLA windows.                           |

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
