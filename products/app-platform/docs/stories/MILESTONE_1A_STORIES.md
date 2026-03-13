# MILESTONE 1A — CORE KERNEL FOUNDATION

## Sprints 1–2 | 78 Stories | K-05, K-07, K-02, K-15

> **Story Template**: Each story includes ID, title, feature ref, points, sprint, team, description, Given/When/Then ACs, key tests, and dependencies.

---

# EPIC K-05: EVENT BUS, EVENT STORE & WORKFLOW ORCHESTRATION (32 Stories)

## Feature K05-F01 — Append-Only Event Store (5 Stories)

---

### STORY-K05-001: Create event store schema and append-only table

**Feature**: K05-F01 · **Points**: 5 · **Sprint**: 1 · **Team**: Alpha

Create PostgreSQL `event_store` table with REVOKE UPDATE/DELETE. Schema: event_id UUID PK, event_type VARCHAR, aggregate_id UUID, aggregate_type VARCHAR, sequence_number BIGINT, data JSONB, metadata JSONB, created_at_utc TIMESTAMPTZ, created_at_bs VARCHAR(10). Create Flyway migration scripts, UNIQUE index on (aggregate_id, sequence_number), and indexes on (event_type), (created_at_utc).

**ACs**:

1. Given migration runs, When event_store table created, Then REVOKE UPDATE/DELETE enforced on application role
2. Given valid INSERT, When row added, Then all columns stored with correct types and constraints
3. Given UPDATE or DELETE on event_store, When SQL executed beneath app role, Then permission error returned

**Tests**: migration_createsTable · insert_validRow_succeeds · update_blocked · delete_blocked · uniqueIndex_sequenceConflict · perf_bulkInsert_10k

**Dependencies**: PostgreSQL cluster provisioned

---

### STORY-K05-002: Implement appendEvent write API

**Feature**: K05-F01 · **Points**: 5 · **Sprint**: 1 · **Team**: Alpha

Implement `appendEvent(aggregateId, aggregateType, eventType, data, metadata): EventRecord` function. Generates UUID event_id, auto-increments sequence_number per aggregate using optimistic concurrency (catch unique violation → ConflictError). Returns complete EventRecord.

**ACs**:

1. Given valid event data, When appendEvent called, Then event stored with monotonically increasing sequence per aggregate
2. Given duplicate event_id (idempotency), When appendEvent called, Then returns existing record without error
3. Given concurrent writes to same aggregate with same sequence, When conflict detected, Then throws ConflictError

**Tests**: appendEvent_valid_stores · appendEvent_duplicate_idempotent · appendEvent_sequenceConflict · appendEvent_nullPayload_rejects · perf_10kEventsPerSec

**Dependencies**: STORY-K05-001

---

### STORY-K05-003: Implement getEventsByAggregate read API

**Feature**: K05-F01 · **Points**: 3 · **Sprint**: 1 · **Team**: Alpha

Implement `getEventsByAggregate(aggregateId, fromSequence?, toSequence?): EventRecord[]` for reading event streams. Supports pagination, ascending sequence order. Used for aggregate reconstruction and projection rebuild.

**ACs**:

1. Given aggregate with 100 events, When getEventsByAggregate called, Then returns all events in sequence order
2. Given fromSequence=50, When called, Then returns events from sequence 50 onwards
3. Given non-existent aggregateId, When called, Then returns empty array

**Tests**: getEvents_allEvents_ordered · getEvents_fromSequence_filtered · getEvents_emptyAggregate · perf_1000events_sub5ms

**Dependencies**: STORY-K05-001

---

### STORY-K05-004: Add dual-calendar timestamp enrichment

**Feature**: K05-F01 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Integrate K-15 calendar conversion to automatically populate created_at_bs whenever an event is appended. Server-side enrichment ensures consistency regardless of client clock.

**ACs**:

1. Given event appended, When stored, Then created_at_bs populated with correct BS date matching created_at_utc
2. Given K-15 unavailable, When event appended, Then created_at_bs set to null with degradation flag in metadata

**Tests**: enrichment_populatesBs · enrichment_k15Down_graceful · enrichment_roundTrip_bs_greg_match

**Dependencies**: STORY-K05-001, K-15

---

### STORY-K05-005: Implement event store range partitioning

**Feature**: K05-F01 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Partition event_store by created_at_utc (monthly partitions) using PostgreSQL declarative partitioning. Create auto-partition management (create next 3 months ahead). Add partition-aware index maintenance.

**ACs**:

1. Given monthly partition scheme, When events written across months, Then each month's data in separate partition
2. Given query for single month, When executed, Then only relevant partition scanned (partition pruning)
3. Given new month approaching, When auto-create runs, Then next partition created before needed

**Tests**: partition_monthBoundary_correct · partition_pruning_singleMonth · partition_autoCreate_future · perf_query_partitioned_vs_unpartitioned

**Dependencies**: STORY-K05-001

## Feature K05-F02 — Schema Registry & Validation (3 Stories)

---

### STORY-K05-006: Implement event schema registry storage

**Feature**: K05-F02 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Create schema registry table (event_type, version INT, json_schema JSONB, status ENUM, created_at). Implement POST `/schemas` for registering new schema versions. Enforce semantic versioning — major version change = breaking.

**ACs**:

1. Given valid JSON Schema for OrderPlaced v1, When POST /schemas, Then schema stored with version=1, status=ACTIVE
2. Given schema already exists at v1, When new schema posted, Then registered as v2
3. Given invalid JSON Schema syntax, When posted, Then 400 with validation errors

**Tests**: register_valid_stores · register_versionIncrement · register_invalidSchema_rejects · register_duplicate_conflict

**Dependencies**: PostgreSQL

---

### STORY-K05-007: Implement event validation middleware

**Feature**: K05-F02 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Create middleware that validates every event against its registered schema before storage. Intercept appendEvent calls, lookup schema by event_type, validate data against JSON Schema. Reject invalid events with SCHEMA_VALIDATION_ERROR.

**ACs**:

1. Given event matching registered schema, When appended, Then validation passes transparently
2. Given event missing required field, When appended, Then rejected with SCHEMA_VALIDATION_ERROR listing missing fields
3. Given unregistered event_type, When appended, Then configurable: reject or allow (config flag)

**Tests**: validate_conforming_passes · validate_missingField_rejects · validate_unknownType_configurable · perf_validation_sub1ms

**Dependencies**: STORY-K05-006, STORY-K05-002

---

### STORY-K05-008: Implement schema evolution backward compatibility check

**Feature**: K05-F02 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

When registering new schema version, validate backward compatibility. New schema must be able to read events written with previous version. Reject breaking changes (removing required fields, changing field types).

**ACs**:

1. Given v1 with field A required, When v2 adds optional field B, Then registered (backward compatible)
2. Given v1 with field A required, When v2 removes field A, Then rejected as backward-incompatible
3. Given v1 with field A as string, When v2 changes A to integer, Then rejected as breaking change

**Tests**: compat_addOptional_passes · compat_removeRequired_fails · compat_typeChange_fails · compat_narrowEnum_fails

**Dependencies**: STORY-K05-006

## Feature K05-F03 — At-Least-Once Delivery with Consumer Groups (4 Stories)

---

### STORY-K05-009: Implement Kafka event publisher with outbox relay

**Feature**: K05-F03 · **Points**: 5 · **Sprint**: 2 · **Team**: Alpha

Implement outbox relay service that polls `event_outbox` table for unpublished events, publishes to Kafka topics (partition key = aggregate_id), and marks as published. Batch mode (configurable, default 100). Topic naming: `siddhanta.{aggregate_type}.events`.

**ACs**:

1. Given event in outbox, When relay polls, Then event published to Kafka within 100ms
2. Given Kafka unavailable, When publish fails, Then events remain in outbox for retry — zero data loss
3. Given 10,000 events in batch, When relay processes, Then all published within 1 second

**Tests**: publish_success_marksOutbox · publish_kafkaDown_retries · publish_batchSize_100 · publish_partitionKey_aggregateId · perf_50kPerSec

**Dependencies**: STORY-K05-002, Kafka cluster

---

### STORY-K05-010: Implement consumer group framework

**Feature**: K05-F03 · **Points**: 5 · **Sprint**: 2 · **Team**: Alpha

Implement `EventConsumer` base class with `subscribe(topic, groupId, handler)`. Manual offset commit after successful processing. JSON deserialization with schema validation. Error handling: transient → retry 3x, permanent → DLQ routing.

**ACs**:

1. Given consumer subscribes, When event published, Then handler invoked with deserialized event within 50ms
2. Given handler throws transient error, When retried 3 times, Then succeeds and offset committed
3. Given handler throws permanent error, When retries exhausted, Then event routed to DLQ, offset committed, consumer continues

**Tests**: consumer_valid_invokesHandler · consumer_transient_retries · consumer_permanent_dlq · consumer_offsetCommit_onSuccess · perf_100kPerSec

**Dependencies**: STORY-K05-009

---

### STORY-K05-011: Implement offset management and checkpoint

**Feature**: K05-F03 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Implement manual offset commit strategy with checkpoint storage. On consumer restart, resume from last committed offset. Support consumer group rebalance — revoked partitions stop processing, assigned partitions resume.

**ACs**:

1. Given consumer crashes, When restarted, Then resumes from last committed offset without missing events
2. Given rebalance event, When partitions revoked, Then processing stops for revoked partitions gracefully
3. Given consumer lag, When lag exceeds threshold, Then alert emitted

**Tests**: offset_crashRestart_noLoss · offset_rebalance_graceful · offset_lagAlert_threshold · offset_manualCommit_afterProcess

**Dependencies**: STORY-K05-010

---

### STORY-K05-012: Implement consumer error handling and DLQ routing

**Feature**: K05-F03 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Implement error classification (transient vs permanent), configurable retry policies per consumer, and automatic DLQ routing. DLQ message includes original event, error details, retry count, and consumer group. Emit ConsumerErrorOccurred event.

**ACs**:

1. Given deserialization failure, When processing, Then classified as permanent → DLQ immediately
2. Given transient network error, When retried 3x with backoff, Then succeeds without DLQ
3. Given DLQ message, When inspected, Then contains original event + error details + retry count

**Tests**: error_deserialization_permanent · error_transient_retrySuccess · error_dlqMessage_complete · error_retryBackoff_exponential

**Dependencies**: STORY-K05-010, K-19 (DLQ)

## Feature K05-F04 — Idempotency Framework (3 Stories)

---

### STORY-K05-013: Implement Redis-backed idempotency dedup store

**Feature**: K05-F04 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Implement idempotency store using Redis SET NX with configurable TTL (default 24h). Store idempotency_key → response_hash mapping. Concurrent requests with same key: only first executes, others receive cached response.

**ACs**:

1. Given new idempotency_key, When command processed, Then key stored in Redis with TTL, response cached
2. Given existing key within TTL, When same command sent, Then cached response returned without re-execution
3. Given key expired past TTL, When re-sent, Then treated as new command and re-executed

**Tests**: idemp_newKey_executes · idemp_existingKey_cached · idemp_expired_reExecutes · idemp_concurrent_onlyOne · perf_check_sub1ms

**Dependencies**: Redis cluster

---

### STORY-K05-014: Implement idempotency guard middleware

**Feature**: K05-F04 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Create `IdempotencyGuard` middleware that wraps request handlers. Extracts idempotency_key from X-Idempotency-Key header, checks dedup store, executes handler if new, caches response. Configurable per-route.

**ACs**:

1. Given X-Idempotency-Key header present, When request processed, Then idempotency enforced
2. Given no header, When request processed, Then passes through without idempotency check
3. Given handler fails after key stored, When error occurs, Then key removed from store (retryable)

**Tests**: guard_withHeader_enforces · guard_noHeader_passthrough · guard_handlerFail_cleansUp · guard_perRoute_configurable

**Dependencies**: STORY-K05-013

---

### STORY-K05-015: Implement PostgreSQL fallback dedup store

**Feature**: K05-F04 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement PostgreSQL-backed idempotency store as fallback when Redis is unavailable. Uses idempotency_keys table with UPSERT. Degraded performance but guaranteed correctness.

**ACs**:

1. Given Redis unavailable, When idempotency check runs, Then falls back to PostgreSQL check seamlessly
2. Given PostgreSQL fallback active, When check performed, Then result correct (slower but accurate)
3. Given Redis recovers, When next request, Then switches back to Redis automatically

**Tests**: fallback_redisDown_postgres · fallback_correctness_maintained · fallback_redisRecovery_switchBack · perf_fallback_sub50ms

**Dependencies**: STORY-K05-013

## Feature K05-F05 — Saga Orchestration Engine & Compensation (5 Stories)

---

### STORY-K05-016: Implement saga definition registry

**Feature**: K05-F05 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Create saga definition storage and registration API. SagaDefinition: name, version, steps[] (name, service, action_topic, compensation_topic, timeout_ms). Validate DAG structure. Support versioning with active version pinning.

**ACs**:

1. Given valid saga definition with 3 steps, When registered, Then stored with version=1, status=ACTIVE
2. Given cyclic step dependencies, When registered, Then rejected with CYCLIC_DEPENDENCY error
3. Given existing definition, When new version registered, Then previous version remains for running instances

**Tests**: register_valid_stores · register_cyclic_rejects · register_versioning_coexist · register_emptySteps_rejects

**Dependencies**: PostgreSQL

---

### STORY-K05-017: Implement saga orchestration engine

**Feature**: K05-F05 · **Points**: 8 · **Sprint**: 2 · **Team**: Alpha

Implement saga orchestrator that creates and manages saga instances. States: STARTED → step_pending → step_complete → ... → COMPLETED | COMPENSATING → COMPENSATED | FAILED. Event-sourced saga state persistence. Listens for step completion events and advances saga.

**ACs**:

1. Given 3-step saga (A→B→C), When all steps succeed, Then saga → COMPLETED with all step events recorded
2. Given step B fails, When failure detected, Then compensation for A triggered, saga → COMPENSATING → COMPENSATED
3. Given saga engine restarts, When pending sagas rehydrated from events, Then resume from last completed step

**Tests**: saga_allSuccess_completed · saga_stepFail_compensates · saga_rehydrate_resumes · saga_concurrent_isolated · perf_100sagas_under5s

**Dependencies**: STORY-K05-016, STORY-K05-010

---

### STORY-K05-018: Implement compensation handler framework

**Feature**: K05-F05 · **Points**: 5 · **Sprint**: 2 · **Team**: Alpha

Create compensation handler registry. Each service registers `compensate(sagaId, stepName, originalEvent): CompensationResult`. Retry 3x with exponential backoff. Idempotent compensations. Every attempt audit-logged.

**ACs**:

1. Given step A completed, When compensation triggered, Then compensation handler invoked refreshing A's effects
2. Given compensation invoked twice, When second call, Then idempotent — no double-reversal
3. Given compensation fails after 3 retries, When exhausted, Then saga → FAILED, manual review alert raised

**Tests**: compensate_reverses_effects · compensate_idempotent · compensate_retryExhausted_failed · compensate_auditLogged

**Dependencies**: STORY-K05-017, K-07

---

### STORY-K05-019: Implement saga timeout and auto-compensation

**Feature**: K05-F05 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Implement timeout monitoring for saga steps. If step does not complete within configured timeout_ms, automatically trigger compensation chain. Timeout checker runs as scheduled job polling for timed-out steps.

**ACs**:

1. Given step timeout = 30s, When step not completed in 30s, Then compensation chain triggered automatically
2. Given step completes at 29s (just before timeout), When result arrives, Then processed normally, no compensation
3. Given timeout checker restart, When pending timeouts exist, Then resume monitoring without miss

**Tests**: timeout_exceeded_compensates · timeout_justInTime_noCompensation · timeout_checkerRestart_resumes · timeout_configurable_perStep

**Dependencies**: STORY-K05-017

---

### STORY-K05-020: Implement saga state persistence and recovery

**Feature**: K05-F05 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Persist saga state using event sourcing (SagaStarted, SagaStepCompleted, SagaStepFailed, SagaCompensationStarted, SagaCompleted events). On engine restart, rehydrate all in-progress sagas from event stream.

**ACs**:

1. Given saga in progress, When engine crashes and restarts, Then saga reconstructed from events to correct state
2. Given replay of saga events, When reconstructed, Then state identical to pre-crash state
3. Given completed saga, When queried, Then full event history returned for audit

**Tests**: persistence_crashRecovery · persistence_replay_consistent · persistence_auditHistory · persistence_1000sagas_recovery

**Dependencies**: STORY-K05-017, STORY-K05-001

## Feature K05-F06 — Event Replay & Projection Rebuild (4 Stories)

---

### STORY-K05-021: Implement event replay engine

**Feature**: K05-F06 · **Points**: 5 · **Sprint**: 2 · **Team**: Alpha

Build replay engine that re-publishes historical events to a designated replay topic. Supports full replay (from beginning) and partial replay (from sequence/timestamp). Rate-limited to avoid overwhelming consumers.

**ACs**:

1. Given replay requested for aggregate X, When engine runs, Then all events for X re-published in order
2. Given replay rate limit = 1000 events/sec, When replay runs, Then does not exceed configured rate
3. Given replay in progress, When cancelled, Then stops at current position with checkpoint

**Tests**: replay_fullAggregate_inOrder · replay_rateLimit_respected · replay_cancel_checkpoints · replay_fromTimestamp_filtered

**Dependencies**: STORY-K05-003, STORY-K05-009

---

### STORY-K05-022: Implement projection rebuild from events

**Feature**: K05-F06 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Create projection rebuild framework. Projections can register for rebuild by clearing their state and consuming replay topic. Track rebuild progress (% complete). Support concurrent rebuilds for independent projections.

**ACs**:

1. Given projection registered for rebuild, When replay starts, Then projection clears state and re-applies events
2. Given rebuild in progress, When progress queried, Then returns percentage complete
3. Given two independent projections rebuilding, When concurrent, Then no interference between them

**Tests**: rebuild_clearsAndReapplies · rebuild_progress_tracked · rebuild_concurrent_independent · rebuild_failure_resumable

**Dependencies**: STORY-K05-021

---

### STORY-K05-023: Implement selective replay with filtering

**Feature**: K05-F06 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Extend replay engine with filters: by aggregate_type, event_type, time range (BS or Gregorian), and sequence range. Compose multiple filters. Dry-run mode that counts matching events without publishing.

**ACs**:

1. Given filter by event_type=OrderPlaced, When replay runs, Then only OrderPlaced events replayed
2. Given time range filter 2081-01-01 to 2081-06-30 BS, When replay, Then only events in that BS date range
3. Given dry-run mode, When executed, Then returns count of matching events without publishing

**Tests**: filter_byType_correct · filter_byTimeRange_bs · filter_dryRun_countOnly · filter_composed_multipleFilters

**Dependencies**: STORY-K05-021

---

### STORY-K05-024: Implement replay progress tracking and dashboard

**Feature**: K05-F06 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Track replay progress: total events, processed events, errors, estimated time remaining. Expose via API and Prometheus metrics. Emit ReplayStarted, ReplayProgress, ReplayCompleted events.

**ACs**:

1. Given replay in progress, When GET /replay/{id}/progress, Then returns total/processed/errors/eta
2. Given replay completes, When finished, Then ReplayCompleted event emitted with summary
3. Given replay errors, When threshold exceeded (>1%), Then alert triggered

**Tests**: progress_tracked_accurately · progress_completionEvent · progress_errorThreshold_alerts · progress_apiExposed

**Dependencies**: STORY-K05-021, K-06

## Feature K05-F07 — Backpressure & Flow Control (2 Stories)

---

### STORY-K05-025: Implement consumer backpressure with pause/resume

**Feature**: K05-F07 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Implement backpressure mechanism that pauses Kafka consumption when downstream processing is slow. Monitor processing queue depth — if exceeds threshold, pause consumer; when drains below threshold, resume. Emit BackpressureActivated/Deactivated events.

**ACs**:

1. Given processing queue depth > 1000, When threshold exceeded, Then consumer paused
2. Given queue drains to < 500, When below resume threshold, Then consumer resumed
3. Given backpressure active, When queried, Then status reflected in health endpoint

**Tests**: backpressure_activate_onThreshold · backpressure_resume_belowThreshold · backpressure_healthEndpoint · perf_noDataLoss_duringBackpressure

**Dependencies**: STORY-K05-010

---

### STORY-K05-026: Implement producer flow control

**Feature**: K05-F07 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement producer-side flow control. If Kafka producer buffer full, apply back-pressure to event store writes by slowing outbox relay polling interval. Configurable buffer thresholds. Metrics for producer buffer utilization.

**ACs**:

1. Given producer buffer > 80% full, When relay polls, Then polling interval increased (backoff)
2. Given buffer drains to < 50%, When next poll cycle, Then polling interval restored to normal
3. Given buffer metrics, When exposed to Prometheus, Then buffer_utilization_percent gauge available

**Tests**: flow_bufferFull_slowsRelay · flow_bufferDrains_restores · flow_metricsExposed · flow_noDataLoss

**Dependencies**: STORY-K05-009

## Feature K05-F08 — DLQ Integration (2 Stories)

---

### STORY-K05-027: Implement event bus DLQ topic integration

**Feature**: K05-F08 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Create dedicated DLQ topics per source topic (pattern: `siddhanta.{source}.dlq`). Route failed events with enriched metadata: original_topic, error_message, retry_count, failed_at, consumer_group. Ensure DLQ messages are self-describing.

**ACs**:

1. Given consumer permanently fails on event, When DLQ routed, Then message includes original event + error metadata
2. Given DLQ topic naming, When created, Then follows `siddhanta.{source}.dlq` pattern
3. Given DLQ message, When inspected, Then contains sufficient context for manual investigation

**Tests**: dlq_routing_enriched · dlq_topicNaming_correct · dlq_selfDescribing · dlq_noDataLoss

**Dependencies**: STORY-K05-012

---

### STORY-K05-028: Implement DLQ routing configuration

**Feature**: K05-F08 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Make DLQ routing configurable per consumer: enable/disable, max retries before DLQ, error classification rules (which errors are transient vs permanent). Configuration via K-02.

**ACs**:

1. Given DLQ disabled for consumer X, When permanent error, Then error logged but not routed to DLQ
2. Given max_retries=5 configured, When 5 retries exhausted, Then routed to DLQ
3. Given custom error classification, When specific error code, Then classified according to config

**Tests**: config_dlqDisabled_noRouting · config_maxRetries_custom · config_errorClassification · config_hotReload

**Dependencies**: STORY-K05-027, K-02

## Feature K05-F09 — Schema Evolution (2 Stories)

---

### STORY-K05-029: Implement schema versioning with semantic versioning

**Feature**: K05-F09 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Enforce semantic versioning for event schemas: PATCH (documentation changes), MINOR (backward-compatible additions), MAJOR (breaking changes — gated by approval). Track version history per event_type.

**ACs**:

1. Given schema change adding optional field, When registered, Then MINOR version bump
2. Given schema change removing required field, When registered, Then flagged as MAJOR (requires approval)
3. Given version history, When queried for event_type, Then returns all versions with change descriptions

**Tests**: versioning_minor_addOptional · versioning_major_removeRequired · versioning_history_complete · versioning_patchDocOnly

**Dependencies**: STORY-K05-006

---

### STORY-K05-030: Implement breaking change detection and migration tooling

**Feature**: K05-F09 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Detect breaking schema changes automatically: removed fields, type changes, narrowed enums. Provide migration guidance: suggested transformers for old → new format. Block breaking changes unless migration plan attached.

**ACs**:

1. Given type change string→int, When detected, Then flagged as breaking with migration suggestion
2. Given MAJOR change, When no migration plan attached, Then registration blocked
3. Given migration plan attached, When approved, Then MAJOR version registered

**Tests**: detect_typeChange_breaking · detect_noMigration_blocked · detect_withMigration_allowed · detect_enumNarrowed_breaking

**Dependencies**: STORY-K05-008

## Feature K05-F10 — Trace Correlation for Sagas (2 Stories)

---

### STORY-K05-031: Implement saga trace correlation ID propagation

**Feature**: K05-F10 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Ensure saga_id and correlation_id are propagated through all saga step events and messages. Inject into Kafka message headers and HTTP request headers for cross-service tracing.

**ACs**:

1. Given saga started, When step event published, Then saga_id and correlation_id in message headers
2. Given downstream service receives step event, When processing, Then correlation_id available in request context
3. Given Jaeger trace, When queried by saga_id, Then all steps visible in single trace

**Tests**: correlation_sagaIdInHeaders · correlation_downstreamPropagated · correlation_jaegerTrace_complete

**Dependencies**: STORY-K05-017, K-06

---

### STORY-K05-032: Implement cross-service saga trace dashboard

**Feature**: K05-F10 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Create Grafana dashboard showing saga execution visualization: steps completed, step latencies, compensation events, failure rates. Searchable by saga_id or correlation_id.

**ACs**:

1. Given saga completed, When dashboard queried, Then shows all steps with latencies
2. Given saga with compensation, When viewed, Then compensation steps highlighted in red
3. Given search by saga_id, When executed, Then returns full saga timeline

**Tests**: dashboard_completedSaga_visible · dashboard_compensation_highlighted · dashboard_search_bySagaId · dashboard_latencyBreakdown

**Dependencies**: STORY-K05-031, K-06

---

# EPIC K-07: AUDIT FRAMEWORK (16 Stories)

## Feature K07-F01 — Audit SDK Enforcement (2 Stories)

---

### STORY-K07-001: Implement audit SDK core API

**Feature**: K07-F01 · **Points**: 3 · **Sprint**: 1 · **Team**: Alpha

Implement `AuditSDK.log(action, resourceType, resourceId, { oldValue?, newValue?, metadata? })`. Auto-enriches with actor_id (from JWT context), correlation_id, timestamp_utc, calendar_date (CalendarDate, optional, populated by K-15 when T1 calendar pack is installed). Async write with in-memory buffer (max 1000) and retry on failure.

**ACs**:

1. Given SDK configured, When audit.log() called, Then entry created with actor, correlation, dual timestamps
2. Given no JWT context (system operation), When audit.log() called, Then actor_id = "SYSTEM"
3. Given audit store unavailable, When called, Then buffered in memory up to 1000 entries and retried

**Tests**: sdk_logAction_creates · sdk_noJwt_system · sdk_storeDown_buffers · sdk_bufferOverflow_drops_oldest · perf_overhead_sub1ms

**Dependencies**: K-15, K-01

---

### STORY-K07-002: Implement Express/Fastify audit middleware

**Feature**: K07-F01 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Create middleware that automatically audits POST/PUT/PATCH/DELETE requests. Configurable opt-in for GET requests. Captures route, method, status code, request/response body diff. Publishable as npm internal package.

**ACs**:

1. Given middleware on POST route, When request processed, Then audit entry auto-created with route + method + body
2. Given GET request without opt-in, When processed, Then no audit entry created
3. Given opt-in enabled for GET /orders, When GET called, Then audit entry created

**Tests**: middleware_post_autoAudit · middleware_get_noAudit · middleware_get_optIn · middleware_responseDiff_captured

**Dependencies**: STORY-K07-001

## Feature K07-F02 — Hash-Chain Immutability (3 Stories)

---

### STORY-K07-003: Create audit log table with hash chain schema

**Feature**: K07-F02 · **Points**: 3 · **Sprint**: 1 · **Team**: Alpha

Create `audit_log` table: id BIGSERIAL, timestamp_utc TIMESTAMPTZ, calendar_date JSONB, actor_id UUID, actor_type VARCHAR, action VARCHAR, resource_type VARCHAR, resource_id VARCHAR, old_value JSONB, new_value JSONB, metadata JSONB, previous_hash VARCHAR(64), entry_hash VARCHAR(64). REVOKE UPDATE/DELETE. Partitioned by month.

**ACs**:

1. Given migration runs, When table created, Then REVOKE UPDATE/DELETE enforced and partitions created
2. Given first audit entry, When created, Then previous_hash = genesis hash "0" × 64
3. Given subsequent entry, When created, Then previous_hash = entry_hash of prior entry

**Tests**: table_created_immutable · hashChain_genesis · hashChain_linked · partition_byMonth · update_blocked · delete_blocked

**Dependencies**: PostgreSQL

---

### STORY-K07-004: Implement SHA-256 hash computation for audit entries

**Feature**: K07-F02 · **Points**: 3 · **Sprint**: 1 · **Team**: Alpha

Implement hash computation: entry_hash = SHA-256(previous_hash || action || resource_type || resource_id || timestamp_utc || JSON(data)). Use database trigger or application-level computation with advisory lock to serialize hash chain.

**ACs**:

1. Given audit entry, When hash computed, Then entry_hash = SHA-256 of canonical concatenation
2. Given concurrent audit writes, When serialized via lock, Then hash chain maintains strict ordering
3. Given entry with special characters in data, When hashed, Then deterministic result (canonical JSON)

**Tests**: hash_computation_deterministic · hash_serialized_ordering · hash_specialChars_canonical · perf_hashCompute_sub0.5ms

**Dependencies**: STORY-K07-003

---

### STORY-K07-005: Implement chain validation verification function

**Feature**: K07-F02 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Implement `chain_valid(from_id?, to_id?): { valid: boolean, broken_at?: id, details?: string }`. Walks the chain recomputing hashes to verify integrity. Scheduled nightly validation job. Alert on chain break.

**ACs**:

1. Given intact chain of 10,000 entries, When chain_valid() called, Then returns valid=true
2. Given tampered entry (modified data), When chain_valid() called, Then returns valid=false, broken_at=tampered_id
3. Given nightly job, When chain break detected, Then CRITICAL alert emitted

**Tests**: chainValid_intact_true · chainValid_tampered_detectsBreak · chainValid_nightlyJob_alerts · perf_10kEntries_under5s

**Dependencies**: STORY-K07-004

## Feature K07-F03 — Standardized Event Schema (2 Stories)

---

### STORY-K07-006: Define standardized audit event schema

**Feature**: K07-F03 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Define canonical audit event schema registered in K-05 schema registry. Fields: action (CREATE/READ/UPDATE/DELETE/LOGIN/LOGOUT/APPROVE/REJECT), resource_type, resource_id, actor context (id, type, ip, tenant), change diff, metadata. Publish as shared schema package.

**ACs**:

1. Given schema definition, When registered in schema registry, Then all audit events validated against it
2. Given unknown action type, When audit created, Then rejected with INVALID_ACTION_TYPE
3. Given schema package, When imported by service, Then type-safe audit logging available

**Tests**: schema_registered · schema_invalidAction_rejects · schema_typeSafe_compile · schema_allRequiredFields

**Dependencies**: K-05 schema registry

---

### STORY-K07-007: Implement automatic audit enrichment

**Feature**: K07-F03 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Auto-enrich audit entries with: actor context (from JWT/service identity), request metadata (IP, user-agent, session_id), correlation_id (from request context), dual-calendar timestamps (K-15), tenant_id. No manual enrichment required by callers.

**ACs**:

1. Given HTTP request context, When audit.log() called, Then IP, user-agent, session_id auto-captured
2. Given service-to-service call, When audit created, Then calling service identity captured
3. Given all enrichments, When audit stored, Then no fields require manual population by caller

**Tests**: enrich_httpContext_captured · enrich_serviceIdentity · enrich_dualTimestamps · enrich_correlationId · enrich_tenantId

**Dependencies**: STORY-K07-001, K-01, K-15

## Feature K07-F04 — Evidence Export (3 Stories)

---

### STORY-K07-008: Implement CSV export for audit entries

**Feature**: K07-F04 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement GET `/audit/export/csv` with date range, resource_type, actor_id filters. Stream large exports (>100K rows) without memory exhaustion. Include chain verification hash in export footer.

**ACs**:

1. Given date range filter, When CSV exported, Then only entries in range included
2. Given 500K entries, When export streamed, Then memory stays below 256MB
3. Given export, When footer included, Then contains hash of first and last entry for verification

**Tests**: csv_dateFilter_correct · csv_streaming_memoryBound · csv_footer_hashIncluded · csv_emptyRange_emptyFile

**Dependencies**: STORY-K07-003

---

### STORY-K07-009: Implement JSON export with advanced filtering

**Feature**: K07-F04 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement GET `/audit/export/json` with filters: date range, actor_id, resource_type, resource_id, action. Support JSON Lines format for streaming. Include chain hashes for integrity verification.

**ACs**:

1. Given multiple filters combined, When exported, Then AND logic applied
2. Given JSON Lines format, When streamed, Then one JSON object per line
3. Given export request, When audit entry exists, Then entry_hash and previous_hash included per record

**Tests**: json_multiFilter_and · json_linesFormat · json_hashesIncluded · json_largeExport_streamed

**Dependencies**: STORY-K07-003

---

### STORY-K07-010: Implement PDF evidence package generation

**Feature**: K07-F04 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Generate regulator-ready PDF evidence packages. Include: cover page (date range, requester, purpose), tabular audit entries, hash chain verification summary, digital signature. Dual-calendar dates in all headers.

**ACs**:

1. Given audit query, When PDF generated, Then includes cover page, entries table, chain verification
2. Given dual-calendar requirement, When PDF rendered, Then all dates shown in both BS and Gregorian
3. Given PDF, When digitally signed, Then signature verifiable using platform certificate

**Tests**: pdf_coverPage_included · pdf_dualDates · pdf_digitalSignature_verifiable · pdf_100kEntries_generated · pdf_formatting_regulatorReady

**Dependencies**: STORY-K07-008, K-15

## Feature K07-F05 — Retention Policy Enforcement (2 Stories)

---

### STORY-K07-011: Implement retention policy configuration

**Feature**: K07-F05 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Define retention policies per resource_type via K-02 configuration: retention_years (default 7 for financial, 5 for operational), archive_to (S3/cold storage), delete_after_archive (true/false). Maker-checker for policy changes.

**ACs**:

1. Given retention policy for "orders" = 7 years, When configured, Then stored in K-02 with maker-checker
2. Given policy change, When approved, Then new policy effective for future enforcement cycles
3. Given query for policy, When GET /audit/retention/{resource_type}, Then returns current policy

**Tests**: retention_config_stores · retention_makerChecker_required · retention_queryPolicy · retention_defaultFallback

**Dependencies**: K-02

---

### STORY-K07-012: Implement automated retention enforcement

**Feature**: K07-F05 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Scheduled job (nightly) that enforces retention policies: archive expired partitions to S3/Ceph, optionally drop archived partitions after verification. Log all retention actions to audit trail (meta-audit).

**ACs**:

1. Given entries older than 7 years, When enforcement runs, Then archived to S3 and partition marked for drop
2. Given archive verified (checksum match), When drop enabled, Then partition dropped
3. Given all retention actions, When executed, Then logged to audit trail (who ran, what archived, counts)

**Tests**: retention_archive_toS3 · retention_drop_afterVerify · retention_metaAudit_logged · retention_partialFailure_resumes

**Dependencies**: STORY-K07-011, S3/Ceph

## Feature K07-F06 — Maker-Checker Linkage (2 Stories)

---

### STORY-K07-013: Implement maker-checker audit linkage

**Feature**: K07-F06 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Link audit entries for maker-checker workflows. When maker creates request, audit entry includes request_id. When checker approves/rejects, audit entry references same request_id. Support multi-level approval chains.

**ACs**:

1. Given maker creates change request, When checker approves, Then both audit entries linked by request_id
2. Given multi-level approval (L1→L2), When L2 approves, Then all 3 entries (maker, L1, L2) linked
3. Given audit query by request_id, When executed, Then returns complete approval chain

**Tests**: makerChecker_linked · multiLevel_linked · queryByRequestId_complete · rejection_linked

**Dependencies**: STORY-K07-003

---

### STORY-K07-014: Implement approval chain audit trail

**Feature**: K07-F06 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Track complete approval workflow in audit: INITIATED → PENDING_APPROVAL → APPROVED/REJECTED → APPLIED/ROLLED_BACK. Each state transition is an audit entry. Support time-bounded approvals (auto-expire).

**ACs**:

1. Given approval request, When created, Then state=INITIATED audit entry stored
2. Given approval expires (>24h), When timeout, Then auto-reject with EXPIRED reason in audit
3. Given full workflow, When queried, Then returns state transition timeline

**Tests**: approval_initiated_audited · approval_expired_autoReject · approval_timeline_complete · approval_rollback_audited

**Dependencies**: STORY-K07-013

## Feature K07-F07 — External Hash Anchoring (2 Stories)

---

### STORY-K07-015: Implement external hash anchoring endpoint

**Feature**: K07-F07 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement scheduled anchoring: compute Merkle root of audit entries since last anchor, publish hash to external store (configurable: blockchain, external DB, file). POST `/audit/anchor` for manual anchoring.

**ACs**:

1. Given 1000 audit entries since last anchor, When anchor triggered, Then Merkle root computed and published
2. Given anchor stored externally, When queried, Then returns anchor hash, timestamp, entry range
3. Given manual anchor via POST, When invoked by admin, Then immediate anchor created

**Tests**: anchor_merkleRoot_computed · anchor_externalStore_published · anchor_manual_triggered · anchor_scheduled_nightly

**Dependencies**: STORY-K07-004

---

### STORY-K07-016: Implement hash anchor verification

**Feature**: K07-F07 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement verification: recompute Merkle root from audit entries and compare against external anchor. GET `/audit/anchor/{id}/verify`. Scheduled weekly verification. Alert on mismatch.

**ACs**:

1. Given valid anchor, When verified, Then Merkle root matches external store
2. Given tampered entries, When verified, Then mismatch detected with specific entry range
3. Given weekly verification job, When mismatch found, Then CRITICAL alert emitted

**Tests**: verify_valid_matches · verify_tampered_mismatch · verify_weeklyJob_alerts · verify_anchorNotFound_error

**Dependencies**: STORY-K07-015

---

# EPIC K-02: CONFIGURATION ENGINE (17 Stories)

## Feature K02-F01 — Schema Registration & Validation (3 Stories)

---

### STORY-K02-001: Implement config schema registration API

**Feature**: K02-F01 · **Points**: 3 · **Sprint**: 1 · **Team**: Alpha

Implement POST `/config/schemas` for registering configuration schemas. Schema defines: namespace, version, JSON Schema, defaults, validation rules. Support schema discovery via GET `/config/schemas`.

**ACs**:

1. Given valid config schema for "trading.lot-sizes", When registered, Then stored with version and defaults
2. Given duplicate namespace+version, When posted, Then 409 Conflict returned
3. Given GET /config/schemas, When called, Then returns all registered schemas with metadata

**Tests**: register_valid · register_duplicate_conflict · discover_all_schemas · register_invalidJsonSchema_rejects

**Dependencies**: PostgreSQL

---

### STORY-K02-002: Implement config value validation against schema

**Feature**: K02-F01 · **Points**: 3 · **Sprint**: 1 · **Team**: Alpha

Validate all config values against their registered schema before storage. Support JSON Schema validation with custom validators (e.g., BS date format, ISIN format). Reject invalid values with detailed error messages.

**ACs**:

1. Given config value matching schema, When PUT /config/{key}, Then stored successfully
2. Given value violating schema constraint (e.g., negative lot size), When stored, Then 422 with field-level errors
3. Given custom validator for BS date, When invalid BS date provided, Then rejected with specific error

**Tests**: validate_valid_stores · validate_violation_422 · validate_customBsDate · validate_missingRequired_rejects

**Dependencies**: STORY-K02-001

---

### STORY-K02-003: Implement config schema browsing and documentation

**Feature**: K02-F01 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Provide API for discovering and browsing config schemas with human-readable documentation. GET `/config/schemas/{namespace}` returns schema with description, examples, defaults, and changelog.

**ACs**:

1. Given schema with documentation, When browsed, Then descriptions and examples returned
2. Given schema with changelog, When queried, Then version history with change summaries shown
3. Given search by keyword, When executed, Then matching schemas returned

**Tests**: browse_withDocs · browse_changelog · browse_search · browse_notFound_404

**Dependencies**: STORY-K02-001

## Feature K02-F02 — 5-Level Hierarchy Resolution (3 Stories)

---

### STORY-K02-004: Implement GLOBAL → JURISDICTION hierarchy levels

**Feature**: K02-F02 · **Points**: 3 · **Sprint**: 1 · **Team**: Alpha

Implement config resolution for top 2 levels: GLOBAL (platform defaults) and JURISDICTION (country-specific overrides). JURISDICTION values override GLOBAL for same key. Support wildcard/fallback patterns.

**ACs**:

1. Given GLOBAL lot_size=10, JURISDICTION(NP) lot_size=50, When resolved for NP, Then returns 50
2. Given GLOBAL lot_size=10, no JURISDICTION(IN) override, When resolved for IN, Then returns 10 (global fallback)
3. Given config key not in any level, When resolved, Then returns schema default or null with warning

**Tests**: resolve_jurisdictionOverride · resolve_globalFallback · resolve_schemaDefault · resolve_missingKey_warning

**Dependencies**: STORY-K02-001

---

### STORY-K02-005: Implement TENANT → USER → SESSION hierarchy levels

**Feature**: K02-F02 · **Points**: 3 · **Sprint**: 1 · **Team**: Alpha

Implement lower 3 hierarchy levels. Full resolution order: SESSION > USER > TENANT > JURISDICTION > GLOBAL. Cache resolved values per session. Support per-tenant feature flags.

**ACs**:

1. Given TENANT theme="dark", USER theme="light", When resolved for user, Then returns "light"
2. Given SESSION timezone="NPT", When resolved, Then SESSION overrides all lower levels
3. Given 5-level resolution, When cached per session, Then subsequent lookups < 0.5ms

**Tests**: resolve_tenantOverride · resolve_userOverride · resolve_sessionOverride · resolve_cachePerformance · resolve_featureFlags

**Dependencies**: STORY-K02-004

---

### STORY-K02-006: Implement config merge strategy with precedence

**Feature**: K02-F02 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Implement deep merge for object-type configs across hierarchy levels. Higher precedence level values override, but non-conflicting keys from lower levels preserved. Array merge strategies: replace (default), append, or merge-by-key.

**ACs**:

1. Given GLOBAL { a: 1, b: 2 }, TENANT { b: 3, c: 4 }, When merged, Then { a: 1, b: 3, c: 4 }
2. Given array merge strategy=replace, When TENANT overrides, Then array fully replaced
3. Given array merge strategy=append, When merged, Then TENANT items appended to GLOBAL items

**Tests**: merge_deepObject · merge_arrayReplace · merge_arrayAppend · merge_nested3Levels · merge_nullOverride

**Dependencies**: STORY-K02-004

## Feature K02-F03 — Hot-Reload with Canary Rollout (3 Stories)

---

### STORY-K02-007: Implement hot-reload config change detection

**Feature**: K02-F03 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Implement change detection via PostgreSQL LISTEN/NOTIFY and Kafka ConfigChanged events. Services subscribe to config namespaces they use. On change, invalidate cache and re-resolve. No restart required.

**ACs**:

1. Given config value updated, When change published, Then all subscribed services receive notification within 1 second
2. Given service subscribed to "trading.\*", When trading.lot_sizes changed, Then service notified
3. Given notification received, When cache invalidated, Then next resolution uses new value

**Tests**: hotReload_detection_sub1s · hotReload_wildcardSubscription · hotReload_cacheInvalidated · hotReload_multiService_broadcast

**Dependencies**: STORY-K02-002, K-05

---

### STORY-K02-008: Implement canary rollout for config changes

**Feature**: K02-F03 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Implement canary rollout: new config value applied to configurable percentage of sessions (e.g., 5%). Gradually increase percentage. Monitor error rates during rollout. Auto-rollback if error rate exceeds threshold.

**ACs**:

1. Given canary at 5%, When 100 sessions resolve config, Then ~5 get new value, ~95 get old value
2. Given canary percentage increased to 50%, When resolved, Then ~50% get new value
3. Given error rate >5% during canary, When threshold exceeded, Then auto-rollback to previous value

**Tests**: canary_5percent_distribution · canary_gradualIncrease · canary_autoRollback_onError · canary_fullRollout_100percent

**Dependencies**: STORY-K02-007, K-06

---

### STORY-K02-009: Implement config change rollback mechanism

**Feature**: K02-F03 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement instant rollback to any previous config version. Store full config version history. POST `/config/{key}/rollback/{version}` reverts to specified version. Rollback creates new version (append-only history).

**ACs**:

1. Given config at v3, When rollback to v1, Then v4 created with v1's value (append-only)
2. Given rollback, When executed, Then hot-reload triggers immediate propagation
3. Given audit trail, When rollback performed, Then audit entry with actor, old version, new version

**Tests**: rollback_createsNewVersion · rollback_triggersHotReload · rollback_auditLogged · rollback_toInvalid_rejects

**Dependencies**: STORY-K02-007, K-07

## Feature K02-F04 — Dual-Calendar Effective Dates (2 Stories)

---

### STORY-K02-010: Implement dual-calendar effective date support

**Feature**: K02-F04 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Support effective_from and effective_to dates in both BS and Gregorian for config values. Config resolution filters by current date in both calendars. Allow scheduling future config changes by effective date.

**ACs**:

1. Given config effective_from=2081-04-01 BS, When current date is 2081-03-30 BS, Then old value returned
2. Given effective_from passed, When current date >= effective_from, Then new value active
3. Given config scheduled for future BS date, When stored, Then visible in schedule, not yet active

**Tests**: effective_futureNotActive · effective_pastActive · effective_exactBoundary · effective_bsAndGreg_consistent

**Dependencies**: STORY-K02-002, K-15

---

### STORY-K02-011: Implement config temporal queries

**Feature**: K02-F04 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Support point-in-time config queries: GET `/config/{key}?as_of=2081-01-15`. Returns config value that was active at the specified BS or Gregorian date. Essential for historical reconciliation.

**ACs**:

1. Given config changed 3 times, When queried as_of first change date, Then returns value from that period
2. Given BS date format, When as_of uses BS date, Then correctly resolved via K-15 conversion
3. Given future date, When queried, Then returns value scheduled for that date or current if none scheduled

**Tests**: temporal_historicalQuery · temporal_bsDateFormat · temporal_futureScheduled · temporal_beforeFirstVersion

**Dependencies**: STORY-K02-010

## Feature K02-F05 — Air-Gap Signed Config Bundles (2 Stories)

---

### STORY-K02-012: Implement air-gap config bundle generation

**Feature**: K02-F05 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Generate complete config bundles for air-gapped deployments. Bundle contains all config values, schemas, and hierarchy for a target environment. Export as signed tarball with manifest (checksums, version, timestamp).

**ACs**:

1. Given target environment "nepal-prod", When bundle generated, Then contains all applicable configs
2. Given bundle manifest, When inspected, Then lists all configs with SHA-256 checksums
3. Given bundle, When transferred to air-gap, Then importable without network access

**Tests**: bundle_complete_forEnv · bundle_manifest_checksums · bundle_import_airgap · bundle_largeConfig_1000keys

**Dependencies**: STORY-K02-002

---

### STORY-K02-013: Implement Ed25519 config bundle signing and verification

**Feature**: K02-F05 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Sign bundles with Ed25519 (platform key via K-14). Verify signature on import. Reject tampered or unsigned bundles. Support key rotation with multiple valid signing keys.

**ACs**:

1. Given bundle, When signed with Ed25519, Then signature appended to bundle
2. Given valid signed bundle, When imported, Then signature verified and configs applied
3. Given tampered bundle, When import attempted, Then rejected with INVALID_SIGNATURE

**Tests**: sign_ed25519_valid · verify_valid_accepts · verify_tampered_rejects · verify_rotatedKey_valid

**Dependencies**: STORY-K02-012, K-14

## Feature K02-F06 — Maker-Checker for Config Changes (2 Stories)

---

### STORY-K02-014: Implement maker-checker workflow for config changes

**Feature**: K02-F06 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Require maker-checker approval for production config changes. Maker submits change → pending approval. Checker (different user, sufficient role) approves/rejects. Only approved changes take effect. Configurable which namespaces require approval.

**ACs**:

1. Given maker submits config change, When submitted, Then change stored as PENDING_APPROVAL
2. Given checker approves, When approved, Then config value applied and ConfigChanged event emitted
3. Given same user tries to approve own change, When attempted, Then rejected (maker ≠ checker)

**Tests**: makerChecker_pendingOnSubmit · makerChecker_approveApplies · makerChecker_selfApprove_blocked · makerChecker_rejection_logged

**Dependencies**: STORY-K02-002, K-01, K-07

---

### STORY-K02-015: Implement config change approval notifications

**Feature**: K02-F06 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Notify eligible checkers when config change awaits approval. Support email and in-app notifications. Include change diff, namespace, submitter, and urgency level. Auto-escalate if not reviewed within SLA.

**ACs**:

1. Given change pending, When submitted, Then notification sent to eligible approvers
2. Given change not reviewed in 4 hours, When SLA exceeded, Then escalation notification to manager
3. Given notification, When received, Then contains change diff and one-click approve/reject links

**Tests**: notify_approverOnPending · notify_slaEscalation · notify_changeDiff_included · notify_multiChannel

**Dependencies**: STORY-K02-014

## Feature K02-F07 — CQRS Command/Query Separation (2 Stories)

---

### STORY-K02-016: Implement config CQRS write (command) side

**Feature**: K02-F07 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Separate config writes into command handlers: SetConfigCommand, DeleteConfigCommand, RollbackConfigCommand. Each command validated, audit-logged, and produces ConfigChanged event for read side projection.

**ACs**:

1. Given SetConfigCommand, When processed, Then value validated → stored → ConfigChanged event emitted
2. Given DeleteConfigCommand, When processed, Then value soft-deleted → event emitted
3. Given command processing, When audit logged, Then command details in audit trail

**Tests**: command_set_storesAndEmits · command_delete_softDeletes · command_audit_logged · command_invalid_rejected

**Dependencies**: STORY-K02-002, K-05, K-07

---

### STORY-K02-017: Implement config CQRS read (query) side

**Feature**: K02-F07 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement optimized read-side projection for config queries. Materialize resolved values per context (tenant, jurisdiction) in Redis. Update projections from ConfigChanged events. Sub-millisecond reads.

**ACs**:

1. Given config query for tenant X, When resolved, Then reads from materialized projection < 0.5ms
2. Given ConfigChanged event, When received, Then projection updated within 100ms
3. Given Redis unavailable, When queried, Then falls back to live resolution from PostgreSQL

**Tests**: query_projection_sub05ms · query_projectionUpdate_onEvent · query_fallback_postgres · query_cacheHitRate_99pct

**Dependencies**: STORY-K02-016, Redis

---

# EPIC K-15: DUAL-CALENDAR SERVICE (13 Stories)

## Feature K15-F01 — BS ↔ Gregorian Conversion (3 Stories)

---

### STORY-K15-001: Implement BS ↔ Gregorian JDN conversion library

**Feature**: K15-F01 · **Points**: 5 · **Sprint**: 1 · **Team**: Alpha

Implement core conversion functions using Julian Day Number (JDN) arithmetic + lookup table for BS month lengths (2000 BS – 2100 BS). Pure function library with zero external dependencies. Functions: `bsToGregorian(y,m,d)` and `gregorianToBs(y,m,d)`.

**ACs**:

1. Given Gregorian 2024-04-13, When converted to BS, Then returns 2081-01-01
2. Given BS 2081-01-01, When converted to Gregorian, Then returns 2024-04-13
3. Given all dates 2000-2100 BS, When round-trip converted, Then original = result for every date

**Tests**: bsToGreg_newYear · gregToBs_newYear · roundTrip_fullRange · invalidBs_month13_throws · invalidBs_day33_throws · perf_1M_conversions

**Dependencies**: None (foundational)

---

### STORY-K15-002: Implement BS month length lookup table

**Feature**: K15-F01 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Create verified lookup table of BS month lengths for years 2000-2100 BS. Validate against authoritative source (Nepal Government calendar). Support 29, 30, 31, and 32-day months. Provide table update mechanism for corrections.

**ACs**:

1. Given year 2081 BS, When month lengths queried, Then returns accurate array of 12 month lengths
2. Given 32-day month (certain Ashadh), When queried, Then correctly returns 32
3. Given lookup table, When cross-validated against 3 authoritative sources, Then 100% match

**Tests**: monthLength_2081_accurate · monthLength_32dayMonth · monthLength_crossValidated · monthLength_outOfRange_error

**Dependencies**: None

---

### STORY-K15-003: Implement conversion REST API endpoint

**Feature**: K15-F01 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Expose conversion functions via REST API: GET `/calendar/convert?from=greg&date=2024-04-13` and GET `/calendar/convert?from=bs&date=2081-01-01`. Return both representations as a CalendarDate object. Cache frequently requested dates.

**ACs**:

1. Given GET with Gregorian input, When called, Then returns { gregorian, bs, dual_date }
2. Given GET with BS input, When called, Then returns { gregorian, bs, dual_date }
3. Given frequently requested date, When cached, Then response < 0.1ms

**Tests**: api_gregInput_returnsBs · api_bsInput_returnsGreg · api_cache_performance · api_invalidDate_400

**Dependencies**: STORY-K15-001

## Feature K15-F02 — CalendarDate Generation & Multi-Calendar Storage (2 Stories)

---

### STORY-K15-004: Implement CalendarDate type and generation utility

**Feature**: K15-F02 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Create `CalendarDate` type: `{ primary: string (ISO8601 UTC), timezone: string (IANA), calendars: Record<CalendarId, CalendarDateTime> }`. Utility `toCalendarDate(date, packCalendars)` generates a CalendarDate populated with all registered calendar representations (e.g., Gregorian, BS) via the installed T1 calendar packs.

**ACs**:

1. Given Gregorian Date, When toCalendarDate() called with T1 BS pack, Then CalendarDate with both Gregorian and BS representations returned
2. Given CalendarDate, When serialized to JSON, Then all registered calendar representations included
3. Given fiscal year crossing (Shrawan 1) in BS calendar, When generated, Then correct fiscal year label in BS entry

**Tests**: calendarDate_fromGreg · calendarDate_fromBs · calendarDate_fiscalYear · calendarDate_jsonSerialization · calendarDate_calendarsRecord

**Dependencies**: STORY-K15-001

---

### STORY-K15-005: Implement CalendarDate storage middleware

**Feature**: K15-F02 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Create database middleware for CalendarDate storage: tables with date/timestamp columns store canonical UTC in the primary timestamp column. An optional `calendar_date JSONB` column is automatically populated by K-15 when T1 calendar packs are installed. Validate CalendarDate structure on write.

**ACs**:

1. Given INSERT with utc timestamp and T1 pack installed, When calendar_date JSONB provided, Then validated as valid CalendarDate structure
2. Given INSERT with calendar_date JSONB, When primary UTC timestamp missing, Then rejected with MISSING_UTC_TIMESTAMP
3. Given schema migration adding a date column, When T1 pack configured, Then migration script hints include optional `calendar_date JSONB` column

**Tests**: enforce_missingUtc_rejects · enforce_validCalendarDate_accepts · enforce_invalidCalendarDate_rejects · enforce_migration_hint

**Dependencies**: STORY-K15-004

## Feature K15-F03 — Holiday Calendar Management (2 Stories)

---

### STORY-K15-006: Implement holiday calendar CRUD

**Feature**: K15-F03 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Implement holiday management: POST/GET/PUT/DELETE `/calendar/holidays`. Holidays defined as {date_bs, date_gregorian, name, type (PUBLIC/TRADING/SETTLEMENT), jurisdiction, recurring_bs_date?}. T1 config — jurisdiction-specific holiday lists via K-02.

**ACs**:

1. Given new holiday "Dashain", When created, Then stored with both BS and Gregorian dates
2. Given recurring holiday (BS date-based), When next year generated, Then same BS date used
3. Given jurisdiction=NP, When holidays queried for NP, Then returns NP-specific holidays

**Tests**: holiday_create · holiday_recurring_bsBased · holiday_jurisdiction_filtered · holiday_delete · holiday_bulkImport

**Dependencies**: STORY-K15-004, K-02

---

### STORY-K15-007: Implement holiday-aware business day calculation

**Feature**: K15-F03 · **Points**: 2 · **Sprint**: 1 · **Team**: Alpha

Implement `isBusinessDay(date, jurisdiction)`, `nextBusinessDay(date, jurisdiction)`, and `businessDaysBetween(from, to, jurisdiction)`. Considers weekends (Saturday in Nepal) and public holidays.

**ACs**:

1. Given Saturday in Nepal, When isBusinessDay called, Then returns false
2. Given public holiday, When nextBusinessDay called, Then skips to next working day
3. Given date range including holidays, When businessDaysBetween called, Then returns correct count

**Tests**: businessDay_saturday_false · businessDay_holiday_false · nextBusinessDay_skipsHoliday · businessDaysBetween_accurate

**Dependencies**: STORY-K15-006

## Feature K15-F04 — Fiscal Year Boundary Calculation (2 Stories)

---

### STORY-K15-008: Implement BS fiscal year boundary calculation

**Feature**: K15-F04 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Nepal fiscal year runs Shrawan 1 to Ashadh end (BS). Implement `getFiscalYear(date): { startBs, endBs, startGreg, endGreg, label }`. Support configurable fiscal year start month per jurisdiction.

**ACs**:

1. Given BS date in Shrawan 2081, When getFiscalYear called, Then returns FY 2081/82
2. Given Gregorian date, When getFiscalYear called, Then correctly maps to BS fiscal year
3. Given jurisdiction with different FY start, When configured, Then uses jurisdiction-specific start

**Tests**: fiscalYear_shrawan_correct · fiscalYear_fromGreg · fiscalYear_customStart · fiscalYear_yearEnd_boundary

**Dependencies**: STORY-K15-001

---

### STORY-K15-009: Implement fiscal quarter and period calculation

**Feature**: K15-F04 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement fiscal quarter and trimester calculation based on BS fiscal year. `getFiscalQuarter(date)` returns Q1-Q4, `getFiscalPeriod(date)` returns monthly period number (1-12 within fiscal year).

**ACs**:

1. Given Shrawan 2081, When quarter calculated, Then Q1 of FY 2081/82
2. Given Poush 2081, When period calculated, Then period 6 of FY 2081/82
3. Given quarter boundaries, When queries span quarters, Then correct aggregation grouping

**Tests**: quarter_shrawan_q1 · quarter_poush_q2 · period_monthly_correct · quarterBoundary_edge

**Dependencies**: STORY-K15-008

## Feature K15-F05 — Settlement T+n Calculation (2 Stories)

---

### STORY-K15-010: Implement T+n settlement date calculation

**Feature**: K15-F05 · **Points**: 3 · **Sprint**: 2 · **Team**: Alpha

Implement `settlementDate(tradeDate, tPlusDays, jurisdiction): CalendarDate`. Counts only business days (skips weekends and holidays). T+1 from Friday in Nepal = Sunday (Saturday is weekend). Returns a CalendarDate with Gregorian and BS representations via K-15.

**ACs**:

1. Given trade on Thursday, T+2, When calculated, Then returns following Sunday (skips Saturday)
2. Given trade before Dashain holiday, T+1, When calculated, Then skips holiday
3. Given T+0, When calculated, Then returns same day if business day, else next business day

**Tests**: settlement_tPlus2_skipsWeekend · settlement_skipsHoliday · settlement_tPlus0 · settlement_fridayTPlus1 · settlement_dualDate

**Dependencies**: STORY-K15-007

---

### STORY-K15-011: Implement weekend and holiday skip logic

**Feature**: K15-F05 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Implement configurable weekend definition per jurisdiction (Nepal: Saturday; most countries: Saturday+Sunday). Support half-day trading (Friday in some markets). `addBusinessDays(date, n, jurisdiction)`.

**ACs**:

1. Given jurisdiction NP (Saturday weekend), When addBusinessDays, Then Saturday skipped
2. Given jurisdiction US (Saturday+Sunday weekend), When addBusinessDays, Then both skipped
3. Given consecutive holidays + weekend, When T+3 calculated, Then all non-business days skipped

**Tests**: weekend_nepal_saturday · weekend_us_satSun · consecutive_holidaysWeekend · addBusinessDays_negative

**Dependencies**: STORY-K15-007

## Feature K15-F06 — Edge Cases & Leap Year Handling (2 Stories)

---

### STORY-K15-012: Implement BS calendar edge case handling

**Feature**: K15-F06 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Handle BS calendar edge cases: months with 32 days (certain Ashadh months), year boundary transitions (Chaitra → Baisakh), and years with inconsistent month lengths due to lunisolar corrections.

**ACs**:

1. Given BS month with 32 days, When day 32 converted, Then correct Gregorian date returned
2. Given last day of Chaitra, When next day calculated, Then returns Baisakh 1 of next year
3. Given date just outside lookup range, When converted, Then graceful error with range information

**Tests**: edgeCase_32dayMonth · edgeCase_yearBoundary · edgeCase_outsideRange · edgeCase_monthVarByYear

**Dependencies**: STORY-K15-001

---

### STORY-K15-013: Implement comprehensive conversion accuracy test suite

**Feature**: K15-F06 · **Points**: 2 · **Sprint**: 2 · **Team**: Alpha

Create exhaustive test suite covering every day from 2000 BS to 2100 BS (~36,500 days). Cross-validate against multiple authoritative sources. Generate accuracy report.

**ACs**:

1. Given full date range 2000-2100 BS, When all dates tested, Then 100% round-trip accuracy
2. Given cross-validation against 2 external sources, When compared, Then 100% match
3. Given accuracy report, When generated, Then documents total dates tested, mismatches (should be 0), sources

**Tests**: accuracy_fullRange_roundTrip · accuracy_crossValidation · accuracy_report_generated · accuracy_boundary_years

**Dependencies**: STORY-K15-001, STORY-K15-002

---

# MILESTONE 1A SUMMARY

| Epic                      | Feature Count | Story Count | Total SP |
| ------------------------- | ------------- | ----------- | -------- |
| K-05 Event Bus            | 10            | 32          | 100      |
| K-07 Audit Framework      | 7             | 16          | 38       |
| K-02 Configuration Engine | 7             | 17          | 42       |
| K-15 Dual-Calendar        | 6             | 13          | 30       |
| **TOTAL**                 | **30**        | **78**      | **210**  |

**Sprint 1**: STORY-K05-001 through 004, K07-001 through 007, K02-001 through 006, K15-001 through 007 (~38 stories)  
**Sprint 2**: STORY-K05-005 through 032, K07-008 through 016, K02-007 through 017, K15-008 through 013 (~40 stories)
