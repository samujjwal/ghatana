EPIC-ID: EPIC-K-05
EPIC NAME: Event Bus, Event Store & Workflow Orchestration
LAYER: KERNEL
MODULE: K-05 Event Bus
VERSION: 1.1.1

---

#### Section 1 — Objective

Deliver the central Event Bus and Event Store (K-05) that forms the nervous system of the platform. This module guarantees Principle 7 (Event-Sourced, Immutable State) by ensuring every platform state change is durably recorded as an immutable, versioned event. It provides ordered, at-least-once delivery, an Event Schema Registry, and support for distributed Sagas / compensations, enforcing the rule that no domain modules communicate via direct synchronous calls.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Durable, append-only Event Store.
  2. High-throughput Event Bus for pub/sub.
  3. Event Schema Registry for schema versioning and validation.
  4. Saga / distributed transaction orchestration framework.
  5. Dead-letter queues (DLQ) and replay tooling.
- **Out-of-Scope:**
  1. Specific domain workflow logic (e.g., Order Lifecycle).
- **Dependencies:** EPIC-K-02 (Configuration Engine), EPIC-K-07 (Audit Framework), EPIC-K-06 (Observability), EPIC-K-18 (Resilience Patterns), EPIC-K-19 (DLQ Management)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Append-Only Storage:** The Event Store must be strictly append-only. Updates or deletes of events are physically impossible.
2. **FR2 Schema Validation:** Every published event must be validated against its registered schema version in the Event Schema Registry before acceptance.
3. **FR3 Delivery Guarantees:** The Event Bus must provide ordered, at-least-once delivery to all registered subscribers.
4. **FR4 Idempotency Framework:** Subscribers must receive an `idempotency_key` (derived from event ID and causality) to safely handle retries.
5. **FR5 Saga Orchestration:** Provide a state machine primitive for long-running workflows that supports explicit compensation steps on failure.
6. **FR6 Replay:** Provide administrative APIs to replay events from a specific offset or timestamp to rebuild read projections.
7. **FR7 Dual-Calendar:** Events must carry both `timestamp_gregorian` and `timestamp_bs` at the envelope level.
8. **FR8 Projection Rebuild:** Provide a projection rebuild framework with progress tracking, partial rebuild support (by entity ID range), and consistency guarantees. Rebuilds must be non-blocking and allow read queries against the old projection until the new one is complete.
9. **FR9 Event Schema Evolution:** Enforce schema evolution rules (Avro/Protobuf compatibility): allow adding optional fields, allow removing fields with defaults, prohibit removing mandatory fields, prohibit changing field types. Provide schema migration tooling for breaking changes with version coexistence support.
10. **FR10 Backpressure & Flow Control:** When consumer lag exceeds a configurable threshold (default: 100,000 events or 60 seconds), the bus must: (a) emit `ConsumerLagAlert` to K-06, (b) activate producer throttling by returning backpressure signals (HTTP 429 / gRPC RESOURCE_EXHAUSTED) to non-critical producers, (c) maintain priority lanes — critical producers (K-16 Ledger, D-01 OMS) are never throttled, (d) auto-scale consumer groups if infrastructure supports it. Max consumer lag SLO: < 30 seconds for critical topics, < 5 minutes for analytics topics. [ARB P1-07]
11. **FR11 Saga Timeout & Performance Budget:** Every saga definition must declare a `max_duration` (default: 30 seconds for trading sagas, 5 minutes for settlement sagas). If a saga exceeds its timeout: (a) automatic compensation is triggered for completed steps, (b) saga is marked TIMED_OUT, (c) `SagaTimeoutEvent` is emitted, (d) alert is raised via K-06. Saga step-level timeouts are also enforced (default: 10 seconds per step). [ARB P2-17]
12. **FR12 DLQ Management Integration:** Dead Letter Queues must: (a) retain full event metadata including original topic, consumer group, failure reason, and retry count, (b) emit `DlqThresholdBreachedEvent` when DLQ size exceeds configurable threshold (default: 100 events), (c) provide replay API for manual or automated DLQ reprocessing with root cause analysis requirement, (d) support DLQ event routing to K-19 DLQ Management for monitoring dashboard. [ARB P0-04]
13. **FR13 Projection Read Consistency:** During projection rebuilds, the system must implement blue-green projection strategy: (a) serve all reads from the current (old) projection until rebuild completes, (b) verify rebuilt projection against a checksum of the old projection's final state, (c) atomically swap to the new projection only after verification, (d) expose `ProjectionRebuildStatus` API returning progress percentage, ETA, and current serving version. [ARB P1-12]
14. **FR14 Saga Trace Correlation:** Every saga must propagate a single `trace_id` across all steps and compensation actions. The saga coordinator must create a parent span, and each saga step must create a child span linked to the parent. This enables end-to-end distributed tracing of cross-module workflows via K-06 Observability. [ARB D.9]

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The eventing infrastructure is purely Generic Core.
2. **Jurisdiction Plugin:** N/A directly, though events emitted by plugins route through this bus.
3. **Resolution Flow:** N/A
4. **Hot Reload:** Event Schema updates are backward-compatible.
5. **Backward Compatibility:** Schema registry enforces Avro/Protobuf compatibility rules (no removing mandatory fields).
6. **Future Jurisdiction:** Supported natively.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `EventEnvelope`: `{ event_id: UUID, stream_id: String, version: Int, schema_ref: String, timestamp_greg: Timestamp, timestamp_bs: String, payload: Bytes, causality_id: UUID }`
  - `EventSchema`: `{ schema_id: String, version: String, definition: String }`
- **Dual-Calendar Fields:** Envelope metadata contains `timestamp_bs`.
- **Event Schema Changes:** N/A (it hosts the schemas).

---

#### Section 6 — Event Model Definition

| Field             | Description                            |
| ----------------- | -------------------------------------- |
| Event Name        | N/A (Infrastructure)                   |
| Schema Version    | N/A                                    |
| Trigger Condition | N/A                                    |
| Payload           | N/A                                    |
| Consumers         | All Modules                            |
| Idempotency Key   | `event_id`                             |
| Replay Behavior   | Native capability.                     |
| Retention Policy  | Permanent (Immutable Source of Truth). |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                       |
| ---------------- | ----------------------------------------------------------------- |
| Command Name     | `PublishEventCommand`                                             |
| Schema Version   | `v1.0.0`                                                          |
| Validation Rules | Event schema valid, event type registered, payload matches schema |
| Handler          | `EventBusCommandHandler` in K-05 Event Bus                        |
| Success Event    | `EventPublished`                                                  |
| Failure Event    | `EventPublishFailed`                                              |
| Idempotency      | Event ID must be unique; duplicate events are deduplicated        |

| Field            | Description                                                                 |
| ---------------- | --------------------------------------------------------------------------- |
| Command Name     | `StartSagaCommand`                                                          |
| Schema Version   | `v1.0.0`                                                                    |
| Validation Rules | Saga definition exists, initial context valid                               |
| Handler          | `SagaCommandHandler` in K-05 Event Bus                                      |
| Success Event    | `SagaStarted`                                                               |
| Failure Event    | `SagaStartFailed`                                                           |
| Idempotency      | Command ID must be unique; duplicate commands return original saga instance |

| Field            | Description                                                                       |
| ---------------- | --------------------------------------------------------------------------------- |
| Command Name     | `RebuildProjectionCommand`                                                        |
| Schema Version   | `v1.0.0`                                                                          |
| Validation Rules | Projection name exists, requester authorized                                      |
| Handler          | `ProjectionCommandHandler` in K-05 Event Bus                                      |
| Success Event    | `ProjectionRebuilt`                                                               |
| Failure Event    | `ProjectionRebuildFailed`                                                         |
| Idempotency      | Command ID must be unique; duplicate commands return progress of existing rebuild |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection
- **Workflow Steps Exposed:** Event stream monitoring.
- **Model Registry Usage:** `event-flow-anomaly-v1`
- **Explainability Requirement:** AI monitors the bus for abnormal event volumes (e.g., sudden spike in `OrderRejected` events) and generates alerts.
- **Human Override Path:** N/A
- **Drift Monitoring:** N/A
- **Fallback Behavior:** Standard rate-limiting.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                         |
| ------------------------- | ---------------------------------------------------------------------------------------- |
| Latency / Throughput      | P99 publish latency < 2ms; 100,000 TPS                                                   |
| Scalability               | Horizontally scalable partitioned topics (e.g., Kafka/Pulsar model)                      |
| Availability              | 99.999% uptime                                                                           |
| Consistency Model         | Strict ordering per `stream_id`                                                          |
| Security                  | mTLS for all publishers/subscribers; payload encryption for sensitive topics             |
| Data Residency            | Event store replication bound by Jurisdiction rules                                      |
| Data Retention            | Permanent                                                                                |
| Auditability              | The Event Store _is_ the ultimate system audit log                                       |
| Observability             | Metrics: `event.publish.latency`, `event.lag`                                            |
| Extensibility             | New event types via schema registration < 1 hour; new projection types via plugin        |
| Projection Rebuild        | Rebuild throughput > 50,000 events/sec; progress tracking with ETA; zero downtime        |
| Schema Evolution          | Backward/forward compatible schema changes; breaking changes require version coexistence |
| On-Prem Constraints       | Runs efficiently on local clusters                                                       |
| Ledger Integrity          | N/A                                                                                      |
| Dual-Calendar Correctness | Envelope timestamps verified.                                                            |

---

#### Section 10 — Acceptance Criteria

1. **Given** an event payload missing a required schema field, **When** published, **Then** the bus rejects it synchronously and does not append it to the store.
2. **Given** a subscriber that crashes during processing, **When** it restarts, **Then** it resumes consumption from its last acknowledged offset.
3. **Given** a saga executing a cross-module workflow, **When** step 3 fails, **Then** the engine automatically triggers the registered compensation events for steps 2 and 1.
4. **Given** a saga fails at step 3 of 5, **When** the compensation logic executes, **Then** steps 3, 2, 1 are rolled back in reverse order, and the saga is marked FAILED.
5. **Given** a projection rebuild is initiated for the OrderBook projection, **When** executed, **Then** the system replays all OrderPlaced/OrderCancelled events, builds a new projection in parallel, tracks progress (% complete, ETA), and atomically swaps to the new projection when complete.
6. **Given** an event schema adds an optional field `client_segment`, **When** validated, **Then** the schema registry accepts it as backward-compatible, and old consumers ignore the new field while new consumers read it.
7. **Given** an event schema attempts to remove a mandatory field, **When** validated, **Then** the schema registry rejects it with a compatibility error and requires a new major version with migration plan.

---

#### Section 11 — Failure Modes & Resilience

- **Storage Full:** Triggers automatic volume expansion or archival to cold storage (retaining index).
- **Poison Pill Event:** Subscriber moves unprocessable event to DLQ after 3 retries and continues; alert raised.
- **Partition Tolerance:** Ensures leader election without split-brain.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                          |
| ------------------- | --------------------------------------------------------- |
| Metrics             | `bus.throughput.tps`, `consumer.lag.messages`, `dlq.size` |
| Logs                | Structured: `event_id`, `topic`, `action`                 |
| Traces              | Trace context propagated through event headers.           |
| Audit Events        | Covered by Event Store inherently.                        |
| Regulatory Evidence | Full event replay provides complete system state history. |

---

#### Section 13 — Compliance & Regulatory Traceability

- Record retention — Immutable ledger of all actions [LCA-RET-001]
- Audit trails [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `EventBus.publish(event)` → `EventPublished`, `EventBus.subscribe(eventType, handler)` → `Subscription`, `EventStore.replay(fromOffset, toOffset)` → `EventStream`, `Saga.start(definition, context)` → `SagaInstance`, `Projection.rebuild(projectionName, options)` → `RebuildStatus`, `SchemaRegistry.evolve(eventType, newSchema, migrationFn)` → `SchemaVersion`
- **Event Schema Registry Interface:** `SchemaRegistry.register(eventType, schema)`, `SchemaRegistry.validate(event)`, `SchemaRegistry.checkCompatibility(oldSchema, newSchema)`
- **Projection Interface:** `Projection.apply(event)`, `Projection.checkpoint()`, `Projection.restore(checkpoint)`
- **Standard Event Envelope Contract:** All events on the bus MUST conform to this envelope:
  ```
  { event_id: UUID, event_type: String, aggregate_id: UUID, aggregate_type: String,
    sequence_number: Int, timestamp: ISO8601, timestamp_bs: String,
    schema_ref: String, causality_id: UUID, trace_id: String, payload: Object }
  ```
- **Jurisdiction Plugin Extension Points:** N/A (Generic Core)
- **Events Emitted (Platform-Level):** `ConsumerLagAlert`, `DlqThresholdBreachedEvent`, `SagaTimeoutEvent`, `ProjectionRebuilt`, `SchemaEvolutionCompleted`
- **Events Consumed:** `ConfigUpdated` (from K-02), `ObservabilityAlert` (from K-06)
- **Webhook Extension Points:** `POST /webhooks/event-bus-alerts` for external monitoring integration (DLQ threshold, consumer lag)

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                             | Expected Answer                                                                                      |
| ---------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| Can this module support India/Bangladesh via plugin? | Yes — event infrastructure is jurisdiction-neutral; new jurisdictions publish events on the same bus |
| Can this run in an air-gapped deployment?            | Yes — standard clustered deployment (Kafka/Pulsar); no external dependencies                         |
| Can the event store backend be swapped?              | Yes — EventStore interface is abstracted; can swap Kafka→Pulsar→EventStoreDB without SDK changes     |
| Can event retention be made jurisdiction-specific?   | Yes — topic-level retention policies configurable via K-02 per jurisdiction                          |
| Can long-term event replay (10yr+) be supported?     | Yes — cold storage archival with index; replay API supports cross-tier (hot/warm/cold) reads         |
| Can WASM-based saga steps be supported?              | Yes — saga step handlers are pluggable; WASM runtime integration via K-04                            |

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
