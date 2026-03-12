EPIC-ID: EPIC-K-17
EPIC NAME: Distributed Transaction Coordinator
LAYER: KERNEL
MODULE: K-17 Distributed Transaction Coordinator
VERSION: 1.0.1
ARB-REF: P0-01

---

#### Section 1 — Objective

Deliver the K-17 Distributed Transaction Coordinator to guarantee cross-stream saga ordering and data consistency for multi-aggregate workflows spanning Order → Position → Ledger → Settlement. This epic directly remediates ARB finding P0-01 (Event Ordering Guarantees Insufficient for Cross-Stream Sagas) by providing an Outbox pattern with version vectors, optimistic concurrency control, and compensation retry logic.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Outbox pattern implementation for transactional event publishing across aggregates.
  2. Version vector tracking for cross-stream causal ordering.
  3. Optimistic concurrency control (OCC) with configurable retry policies.
  4. Compensation orchestration with automatic and manual retry paths.
  5. Distributed transaction log with queryable state for debugging.
  6. Integration with K-05 Event Bus for event dispatch.
- **Out-of-Scope:**
  1. Two-phase commit (2PC) — explicitly avoided for performance reasons.
  2. Specific domain saga definitions (e.g., Order→Settlement flow) — these are defined in domain epics.
- **Dependencies:** EPIC-K-02 (Config Engine), EPIC-K-05 (Event Bus), EPIC-K-06 (Observability), EPIC-K-07 (Audit Framework), EPIC-K-16 (Ledger Framework)
- **Kernel Readiness Gates:** K-05 must be stable.
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Outbox Pattern:** Every state-changing operation across aggregates must first write to a local outbox table within the same database transaction. A background relay process publishes outbox entries to K-05 Event Bus in strict order. This guarantees at-least-once, causally-ordered delivery without distributed locks.
2. **FR2 Version Vectors:** Maintain version vectors per aggregate root. Cross-stream saga steps must declare expected version vectors. If the actual version diverges (concurrent modification), the step fails and triggers retry or compensation.
3. **FR3 Optimistic Concurrency Control:** All saga steps must use OCC with configurable retry policy: max retries (default: 3), backoff strategy (exponential, base 100ms), and conflict resolution strategy (retry or compensate).
4. **FR4 Compensation Orchestration:** When a saga step fails after retries, the coordinator must: (a) execute compensation actions for all completed steps in reverse order, (b) retry failed compensations up to a configurable limit (default: 5), (c) if compensation also fails, mark the saga as STUCK and emit `SagaCompensationFailedEvent` for manual intervention.
5. **FR5 Transaction Log:** Maintain a queryable distributed transaction log recording: saga ID, step sequence, status (PENDING/COMMITTED/COMPENSATED/STUCK), timestamps (dual-calendar), version vectors, and correlation with K-05 events.
6. **FR6 Idempotent Steps:** All saga steps must be idempotent. The coordinator must deduplicate step execution using `step_id + saga_id` as the idempotency key.
7. **FR7 Dual-Calendar Stamping:** All transaction log entries must include dual-calendar timestamps via K-15.
8. **FR8 Saga Definition Registry:** Provide a registry for saga definitions (step sequences, compensation actions, timeout budgets) that domain modules register at startup. Saga definitions are versioned and immutable once deployed.

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The coordinator, outbox relay, version vectors, and compensation logic are entirely jurisdiction-agnostic.
2. **Jurisdiction Plugin:** N/A — this is pure infrastructure.
3. **Resolution Flow:** N/A.
4. **Hot Reload:** Saga timeout and retry configurations are hot-reloadable via K-02.
5. **Backward Compatibility:** Saga definitions are versioned; in-flight sagas complete using the definition active at saga start.
6. **Future Jurisdiction:** No changes needed.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `Outbox`: `{ outbox_id: UUID, aggregate_type: String, aggregate_id: String, event_type: String, payload: JSON, created_at: DualDate, published: Boolean, published_at: Timestamp }`
  - `SagaInstance`: `{ saga_id: UUID, definition_id: String, version: String, status: Enum(RUNNING, COMPLETED, COMPENSATING, STUCK, TIMED_OUT), steps: List<SagaStep>, started_at: DualDate, completed_at: DualDate }`
  - `SagaStep`: `{ step_id: UUID, saga_id: UUID, step_name: String, status: Enum, version_vector: Map<String,Int>, retries: Int, compensated: Boolean }`
  - `TransactionLog`: `{ log_id: UUID, saga_id: UUID, step_id: UUID, action: String, timestamp: DualDate, details: JSON }`
- **Dual-Calendar Fields:** `started_at`, `completed_at` in `SagaInstance`; `timestamp` in `TransactionLog`.
- **Event Schema Changes:** `SagaStepCommittedEvent`, `SagaCompensationFailedEvent`, `SagaStuckEvent`, `OutboxRelayLagEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                                 |
| ----------------- | --------------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `SagaCompensationFailedEvent`                                                                                               |
| Schema Version    | `v1.0.0`                                                                                                                    |
| Trigger Condition | A saga compensation action fails after exhausting all retries.                                                              |
| Payload           | `{ "saga_id": "...", "step_name": "...", "failure_reason": "...", "retry_count": 5, "requires_manual_intervention": true }` |
| Consumers         | Admin Portal, K-06 Alerting, On-Call Escalation                                                                             |
| Idempotency Key   | `hash(saga_id + step_name + retry_count)`                                                                                   |
| Replay Behavior   | N/A (alert event).                                                                                                          |
| Retention Policy  | Permanent.                                                                                                                  |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                           |
| ---------------- | --------------------------------------------------------------------- |
| Command Name     | `StartDistributedSagaCommand`                                         |
| Schema Version   | `v1.0.0`                                                              |
| Validation Rules | Saga definition exists, initial context valid, caller authorized      |
| Handler          | `SagaCoordinatorHandler` in K-17                                      |
| Success Event    | `SagaStarted`                                                         |
| Failure Event    | `SagaStartFailed`                                                     |
| Idempotency      | Saga ID must be unique; duplicate commands return original saga state |

| Field            | Description                                                                                    |
| ---------------- | ---------------------------------------------------------------------------------------------- |
| Command Name     | `ResolveSagaManuallyCommand`                                                                   |
| Schema Version   | `v1.0.0`                                                                                       |
| Validation Rules | Saga in STUCK state, requester authorized (compliance/admin role), resolution action specified |
| Handler          | `SagaCoordinatorHandler` in K-17                                                               |
| Success Event    | `SagaManuallyResolved`                                                                         |
| Failure Event    | `SagaManualResolutionFailed`                                                                   |
| Idempotency      | Command ID must be unique                                                                      |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Saga execution monitoring.
- **Model Registry Usage:** `saga-anomaly-detector-v1`
- **Explainability Requirement:** AI flags sagas with unusual step durations or abnormal compensation rates.
- **Human Override Path:** Operator can force-complete or force-compensate a stuck saga.
- **Drift Monitoring:** Compensation rate > 5% triggers retraining alert.
- **Fallback Behavior:** Standard timeout and retry logic applies.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                           |
| ------------------------- | ------------------------------------------------------------------------------------------ |
| Latency / Throughput      | Outbox relay lag < 100ms P99; 50,000 sagas/second                                          |
| Scalability               | Horizontally scalable relay workers; partitioned by aggregate type                         |
| Availability              | 99.999% uptime                                                                             |
| Consistency Model         | Causal consistency across aggregates; strong within aggregate                              |
| Security                  | Transaction log encrypted at rest; access restricted to system accounts                    |
| Data Residency            | Transaction logs follow K-08 residency rules                                               |
| Data Retention            | Transaction logs retained per audit policy (minimum 10 years)                              |
| Auditability              | All saga state changes logged to K-07                                                      |
| Observability             | Metrics: `saga.duration`, `saga.compensation.rate`, `outbox.relay.lag`, `saga.stuck.count` |
| Extensibility             | New saga definitions via registry without code changes                                     |
| Upgrade / Compatibility   | Saga definition versioning supports rolling upgrades                                       |
| On-Prem Constraints       | Operates with local database outbox; no external dependencies                              |
| Ledger Integrity          | Guarantees ledger postings are either fully committed or fully compensated                 |
| Dual-Calendar Correctness | All timestamps use DualDate                                                                |

---

#### Section 10 — Acceptance Criteria

1. **Given** an Order→Position→Ledger saga, **When** the Ledger step fails, **Then** Position and Order steps are compensated in reverse order within 5 seconds.
2. **Given** two concurrent sagas modifying the same account, **When** a version conflict occurs, **Then** one saga retries with exponential backoff and the other succeeds.
3. **Given** a saga in STUCK state, **When** an admin resolves it manually, **Then** the resolution is audited in K-07 and the transaction log is updated.
4. **Given** the outbox relay is down for 60 seconds, **When** it recovers, **Then** all pending outbox entries are published in order with zero data loss.
5. **Given** a saga step completes successfully, **When** replayed (duplicate delivery), **Then** the step is idempotently skipped.

---

#### Section 11 — Failure Modes & Resilience

- **Outbox Relay Failure:** Events queue in local DB; relay resumes on recovery; `OutboxRelayLagEvent` emitted if lag > threshold.
- **Version Conflict:** Retry with backoff; compensate after max retries.
- **Compensation Failure:** Retry compensation up to 5 times; escalate to STUCK state with manual intervention required.
- **Database Partition:** Outbox writes continue locally; relay pauses until connectivity restored.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                                    |
| ------------------- | ----------------------------------------------------------------------------------- |
| Metrics             | `saga.active.count`, `saga.duration.p99`, `saga.compensation.rate`, `outbox.lag.ms` |
| Logs                | Structured: `saga_id`, `step_name`, `status`, `version_vector`                      |
| Traces              | Parent span per saga; child span per step; propagated via K-06                      |
| Audit Events        | `SagaStarted`, `SagaCompleted`, `SagaCompensated`, `SagaStuck` [LCA-AUDIT-001]      |
| Regulatory Evidence | Transaction log for settlement dispute resolution                                   |

---

#### Section 13 — Compliance & Regulatory Traceability

- Settlement finality guarantees [LCA-SETTL-001]
- Audit trails for all financial state changes [LCA-AUDIT-001]
- Compensation records for regulatory examination [LCA-COMP-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `SagaClient.start(sagaDefinitionId, context)`, `SagaClient.getStatus(sagaId)`, `SagaClient.resolve(sagaId, resolution)`.
- **Jurisdiction Plugin Extension Points:** N/A.

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                                    |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Can this module support India/Bangladesh via plugin?                  | Yes, saga infrastructure is jurisdiction-agnostic.                                                                 |
| Can new saga types be added?                                          | Yes, via saga definition registry.                                                                                 |
| Can this run in an air-gapped deployment?                             | Yes, uses local DB outbox.                                                                                         |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. Saga orchestration supports cross-chain atomic swaps and DvP (Delivery vs. Payment) for tokenized securities. |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Synchronous saga mode with sub-second timeout supports atomic T+0 settlement finality.                        |

---

## Changelog

### Version 1.0.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
