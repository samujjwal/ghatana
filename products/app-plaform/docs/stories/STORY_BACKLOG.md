# PROJECT SIDDHANTA — FULL STORY BACKLOG

## Critical Path Epics: Complete Story Templates

**Version**: 1.1.0  
**Format**: Mandatory Story Template (Given/When/Then ACs, Edge Cases, Full Test Plans, DoD)  
**Naming Convention**: STORY-{MODULE}-{NUMBER}

> Baseline note: `stories/STORY_INDEX.md` is the authoritative normalized backlog for sprint counts and story-point totals. This file remains the long-form story template catalog for critical-path implementation detail.
>
> Workflow/process note: dynamic process definitions, human-task schemas, and value catalogs are now primarily governed by W-01, K-02, and K-13 in the current milestone files and LLDs. The K-05 saga stories below remain kernel transaction primitives, not the full operator/business workflow DSL.

---

# EPIC K-05: EVENT BUS, EVENT STORE & WORKFLOW ORCHESTRATION

## STORY-K05-001: Implement Append-Only Event Store

**Epic**: K-05 Event Bus  
**Feature**: K05-F01 Append-only event store  
**Priority**: P0 | **Sprint**: 1

**Description**: Implement the foundational event store backed by PostgreSQL with append-only semantics. All state changes captured as immutable events with sequence numbering per aggregate and dual-calendar timestamps.

**Business Value**: Foundation of the entire event-sourced architecture — every module depends on reliable event storage.

**Technical Scope**:

- PostgreSQL `event_store` table with REVOKE UPDATE/DELETE
- Schema: event_id UUID, event_type VARCHAR, aggregate_id UUID, aggregate_type VARCHAR, sequence_number BIGINT, data JSONB, metadata JSONB, created_at_utc TIMESTAMPTZ, created_at_bs VARCHAR(10), partition_key VARCHAR
- Write API: `appendEvent(aggregateId, aggregateType, eventType, data, metadata): EventRecord`
- Optimistic concurrency via sequence conflict detection
- Database migration scripts (Flyway/Knex)
- Index: (aggregate_id, sequence_number) UNIQUE, (event_type), (created_at_utc)

**Dependencies**: PostgreSQL cluster provisioned

**Acceptance Criteria**:

- **AC1**: Given valid event data, When `appendEvent()` is called, Then event is stored with monotonically increasing sequence number per aggregate
- **AC2**: Given duplicate event_id, When `appendEvent()` is called, Then idempotent write returns original record (no duplicate)
- **AC3**: Given an attempt to UPDATE or DELETE an event row, When SQL is executed against event_store, Then database rejects the operation with permission error
- **AC4**: Given two concurrent writes to same aggregate with same sequence, When both execute, Then exactly one succeeds and the other receives ConflictError

**Edge Cases**:

- Concurrent writes to same aggregate (sequence collision → retry with incremented sequence)
- Event payload exceeding 1MB (reject with PAYLOAD_TOO_LARGE)
- Database connection pool exhaustion under sustained write load
- Clock skew between BS and Gregorian timestamps (use server-side NOW())
- Null or empty event data (reject with INVALID_PAYLOAD)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `appendEvent_validPayload_returnsSequence` | Valid event → stored with correct sequence |
| Unit | `appendEvent_duplicateId_idempotent` | Duplicate UUID → returns original, no error |
| Unit | `appendEvent_sequenceConflict_throwsConflict` | Same aggregate+sequence → ConflictError |
| Unit | `appendEvent_oversizedPayload_rejects` | >1MB payload → PAYLOAD_TOO_LARGE |
| Unit | `appendEvent_emptyData_rejects` | Null/empty data → INVALID_PAYLOAD |
| Integration | `eventStore_writeReadRoundtrip` | Write event → read by aggregate → matches |
| Integration | `eventStore_concurrentWriters_noDataLoss` | 10 writers × 1000 events → all stored |
| Integration | `eventStore_updateRejected` | UPDATE SQL → permission denied |
| Integration | `eventStore_deleteRejected` | DELETE SQL → permission denied |
| E2E | `eventStore_publishConsumeChain` | Event stored → Kafka published → consumed |
| Negative | `eventStore_corruptedJson_rejects` | Malformed JSON → validation error |
| Performance | `eventStore_10kEventsPerSec_sustained` | 10,000 events/sec for 60s → no failures |
| Performance | `eventStore_readByAggregate_sub5ms` | 1000 events per aggregate → read < 5ms |
| Security | `eventStore_sqlInjection_prevented` | SQL injection in event_type → sanitized |
| Security | `eventStore_updateDeleteBlocked` | DB-level REVOKE verified |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, migration script reviewed, monitoring metrics exposed

---

## STORY-K05-002: Implement Kafka Event Publisher with Outbox Relay

**Epic**: K-05 Event Bus  
**Feature**: K05-F03 At-least-once delivery  
**Priority**: P0 | **Sprint**: 2

**Description**: Implement the Kafka producer that publishes events from the event store to Kafka topic partitions using the transactional outbox pattern. Guarantees at-least-once delivery with partition key routing by aggregate_id.

**Business Value**: Enables all downstream consumers to receive events reliably without data loss.

**Technical Scope**:

- Kafka producer with `partition_key = aggregate_id`
- Outbox relay service: poll `event_outbox` table → publish → mark published
- Batch publishing (configurable batch size, default 100)
- Metrics: publish_latency_ms, publish_failures_total, outbox_lag_count
- DLQ routing for permanent publish failures
- Topic naming: `siddhanta.{aggregate_type}.events`

**Dependencies**: STORY-K05-001, Kafka cluster

**Acceptance Criteria**:

- **AC1**: Given event stored in event_store, When outbox relay polls, Then event published to correct Kafka topic within 100ms of storage
- **AC2**: Given Kafka unavailable for 30 seconds, When publish fails, Then events remain in outbox for automatic retry — zero data loss
- **AC3**: Given consumer restarts after crash, When it reconnects, Then it resumes from last committed offset without missing events
- **AC4**: Given 10,000 events in outbox batch, When relay processes, Then all published within 1 second

**Edge Cases**:

- Kafka broker failure mid-batch (partial publish → idempotent re-publish on retry)
- Outbox relay crash after publish but before marking complete (idempotent consumers handle duplicates)
- Topic partition rebalance during publish (producer retries on new partition leader)
- Outbox table growing unbounded (scheduled cleanup of published entries >7 days)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `publishEvent_success_marksOutbox` | Publish → outbox entry marked as published |
| Unit | `publishEvent_kafkaDown_retries` | Kafka unavailable → retry with backoff |
| Unit | `publishEvent_batchSize_respected` | 100 events → single batch publish |
| Integration | `outboxRelay_endToEnd` | Store → outbox → Kafka → consumer chain |
| Integration | `outboxRelay_kafkaRecovery` | Broker down 30s → all events delivered after recovery |
| Integration | `outboxRelay_partitionRouting` | Same aggregate → same partition |
| E2E | `eventBus_publishConsumeFullChain` | API → event store → Kafka → projection update |
| Negative | `outboxRelay_corruptEvent_dlq` | Unpublishable event → routed to DLQ |
| Performance | `outboxRelay_50kEventsPerSec` | 50K events/sec sustained throughput |
| Performance | `outboxRelay_lagUnder100ms` | Outbox lag stays < 100ms under load |
| Security | `kafka_authRequired` | Unauthenticated producer → rejected |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, Grafana dashboard for outbox lag, alerts configured

---

## STORY-K05-003: Implement Consumer Group Framework

**Epic**: K-05 Event Bus  
**Feature**: K05-F03 At-least-once delivery  
**Priority**: P0 | **Sprint**: 2

**Description**: Implement the consumer framework that enables services to subscribe to event topics with consumer group semantics. Supports manual offset commits, deserialization with schema validation, and dead-letter routing for processing failures.

**Business Value**: Standardized consumption pattern ensures every service processes events consistently with guaranteed delivery.

**Technical Scope**:

- `EventConsumer` base class: `subscribe(topic, groupId, handler)`
- Manual offset commit after successful processing
- JSON schema validation on consume (reject invalid → DLQ)
- Error handling: transient → retry (3x with backoff), permanent → DLQ
- Consumer lag metric exposure
- Correlation ID propagation from event metadata

**Dependencies**: STORY-K05-002, K-06 (observability for correlation)

**Acceptance Criteria**:

- **AC1**: Given consumer subscribes to topic, When event published, Then handler invoked with deserialized event within 50ms
- **AC2**: Given handler throws transient error, When retried 3 times, Then succeeds — offset committed
- **AC3**: Given handler throws permanent error, When all retries exhausted, Then event routed to DLQ — offset committed (consumer continues)
- **AC4**: Given consumer crashes, When restarted, Then resumes from last committed offset

**Edge Cases**:

- Consumer rebalance (revoked partitions → stop processing, new partitions → resume)
- Deserialization failure (schema mismatch → DLQ with reason)
- Slow consumer (backpressure triggers pause/resume)
- Out-of-order events (consumer must be idempotent by design)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `consumer_validEvent_invokesHandler` | Subscribe → event → handler called |
| Unit | `consumer_transientError_retries3x` | Retry on transient → succeeds on 3rd |
| Unit | `consumer_permanentError_dlq` | Permanent fail → DLQ, offset committed |
| Unit | `consumer_deserializationFail_dlq` | Invalid schema → DLQ with reason |
| Integration | `consumer_crashRecovery_noLoss` | Kill consumer → restart → no missed events |
| Integration | `consumer_rebalance_noDuplicate` | Add/remove consumer → no duplicate processing |
| Performance | `consumer_100kPerSec_throughput` | 100K events/sec consumed per partition |
| Negative | `consumer_infiniteRetry_prevented` | Max retries enforced — no infinite loop |
| Security | `consumer_correlationPropagated` | Correlation ID maintained through chain |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, consumer lag alerting configured

---

## STORY-K05-004: Implement Schema Registry & Event Validation

**Epic**: K-05 Event Bus  
**Feature**: K05-F02 Schema registry & validation  
**Priority**: P0 | **Sprint**: 2

**Description**: Implement the event schema registry that stores JSON Schema definitions for each event type. All events are validated against their registered schema before storage. Supports schema evolution with backward compatibility checks.

**Business Value**: Prevents schema drift across services; ensures event consumers never receive malformed data.

**Technical Scope**:

- Schema storage: PostgreSQL table (event_type, version, json_schema, status)
- POST `/schemas` — register new schema version
- Schema validation middleware in event store write path
- Backward compatibility check: new schema must be able to read old events
- Schema versioning: semantic versioning (major = breaking)

**Dependencies**: STORY-K05-001

**Acceptance Criteria**:

- **AC1**: Given registered schema for OrderPlaced v1, When event with matching structure published, Then validation passes
- **AC2**: Given event that violates schema (missing required field), When published, Then rejected with SCHEMA_VALIDATION_ERROR
- **AC3**: Given new schema version that removes required field, When registered, Then rejected as backward-incompatible
- **AC4**: Given schema v2 registered, When v1 event consumed, Then consumer can still deserialize (backward compat)

**Edge Cases**:

- Schema with recursive references (max depth enforced)
- Schema registration race condition (version conflict)
- Empty schema (rejected)
- Schema for unknown event type (auto-register or reject — configurable)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `schema_register_valid_succeeds` | Valid JSON Schema → stored with version |
| Unit | `schema_validate_conforming_passes` | Event matches schema → passes |
| Unit | `schema_validate_nonConforming_rejects` | Missing field → SCHEMA_VALIDATION_ERROR |
| Unit | `schema_backwardCompat_breaking_rejects` | Remove required field → rejected |
| Unit | `schema_backwardCompat_addOptional_passes` | Add optional field → accepted |
| Integration | `schema_endToEnd_validateOnWrite` | Event → schema check → stored |
| Negative | `schema_recursive_maxDepth` | Recursive schema → depth limit enforced |
| Negative | `schema_empty_rejected` | Empty schema → validation error |
| Performance | `schema_validation_sub1ms` | Schema validation < 1ms per event |

**Story Points**: 3  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, schema migration from v1→v2 documented

---

## STORY-K05-005: Implement Idempotency Deduplication Store

**Epic**: K-05 Event Bus  
**Feature**: K05-F04 Idempotency framework  
**Priority**: P0 | **Sprint**: 2

**Description**: Implement the idempotency framework that prevents duplicate event/command processing. Uses Redis-backed dedup store with configurable TTL. Every command includes an idempotency_key; if seen before, returns cached response.

**Business Value**: Prevents duplicate order placements, double ledger postings, and duplicate settlements — critical for financial correctness.

**Technical Scope**:

- Redis SET with NX + TTL for idempotency keys
- Middleware: `IdempotencyGuard(key, ttl, handler)` → check → execute → store result → return
- Configurable TTL per command type (default 24h)
- Response caching: store response JSON alongside key
- Metrics: dedup_hit_count, dedup_miss_count

**Dependencies**: Redis cluster, K-05 event consumer

**Acceptance Criteria**:

- **AC1**: Given command with new idempotency_key, When processed, Then executed and result cached in Redis
- **AC2**: Given command with existing idempotency_key (within TTL), When processed, Then cached result returned — no re-execution
- **AC3**: Given idempotency_key expired (past TTL), When same key resubmitted, Then treated as new command
- **AC4**: Given Redis unavailable, When idempotency check runs, Then falls back to PostgreSQL-based check (degraded mode)

**Edge Cases**:

- Redis key set but handler fails before result cached (cleanup on error)
- Concurrent requests with same idempotency_key (only one executes)
- Key collision (UUID v4 — probability negligible but handle gracefully)
- Very large response caching (limit to 64KB, store reference for larger)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `idemp_newKey_executes` | New key → handler executed, result cached |
| Unit | `idemp_existingKey_returnsCached` | Existing key → cached result, no execution |
| Unit | `idemp_expiredKey_reExecutes` | Past TTL → treated as new |
| Unit | `idemp_redisFallback_postgres` | Redis down → PostgreSQL fallback |
| Integration | `idemp_concurrentSameKey_onceOnly` | 10 concurrent same-key → exactly 1 execution |
| Integration | `idemp_handlerFail_cleansUp` | Handler error → key removed, retryable |
| Performance | `idemp_check_sub1ms` | Redis lookup < 1ms P99 |
| Negative | `idemp_oversizedResponse_truncated` | >64KB response → stored by reference |

**Story Points**: 3  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, fallback mode documented

---

## STORY-K05-006: Implement Saga Orchestration Engine

**Epic**: K-05 Event Bus  
**Feature**: K05-F05 Saga orchestration  
**Priority**: P0 | **Sprint**: 5

**Description**: Implement the saga orchestration engine that coordinates multi-service transactions. Supports saga definition as a state machine with steps, compensations, and timeouts. Persists saga state in PostgreSQL with event sourcing. This is the low-level transactional primitive beneath W-01 workflow orchestration, which adds metadata-driven step templates, human-task schemas, and value-catalog-backed process customization.

**Business Value**: Enables complex workflows (order → risk → execution → settlement) to execute atomically with guaranteed compensation on failure.

**Technical Scope**:

- SagaDefinition: steps (name, action, compensation, timeout)
- SagaInstance: state machine (STARTED → step1_pending → step1_complete → ... → COMPLETED | COMPENSATING → COMPENSATED | FAILED)
- Event-sourced saga state (SagaStepCompleted, SagaStepFailed, SagaCompensationStarted, etc.)
- Timeout handling: if step exceeds timeout → trigger compensation chain
- Saga registry: register saga definitions
- Correlation: saga_id propagated through all step events
- W-01 compatibility: step handlers exposed as reusable primitives that higher-level workflow definitions can reference via metadata

**Dependencies**: STORY-K05-001, STORY-K05-002, STORY-K05-003, K-17 (DTC)

**Acceptance Criteria**:

- **AC1**: Given 3-step saga (A→B→C), When all steps succeed, Then saga state → COMPLETED, all step events recorded
- **AC2**: Given 3-step saga, When step B fails, Then compensation for A executed, saga state → COMPENSATED
- **AC3**: Given step exceeds timeout (30s), When no response, Then compensation chain triggered automatically
- **AC4**: Given saga engine restarts, When pending sagas rehydrated, Then they resume from last completed step

**Edge Cases**:

- Compensation step itself fails (retry compensation 3x → manual intervention flag)
- Concurrent saga updates (optimistic locking on saga instance)
- Nested sagas (parent waits for child completion)
- Saga definition change while instances running (version pinning)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `saga_allStepsSucceed_completed` | A→B→C success → COMPLETED |
| Unit | `saga_stepBFails_compensatesA` | B fails → A compensated → COMPENSATED |
| Unit | `saga_timeout_triggersCompensation` | Step timeout → auto-compensate |
| Unit | `saga_compensationFails_manualFlag` | Compensation retry exhausted → FAILED + alert |
| Integration | `saga_rehydrateAfterRestart` | Kill engine → restart → resume saga |
| Integration | `saga_orderToSettlement_fullChain` | Order placement saga end-to-end |
| E2E | `saga_tradingWorkflow` | Place order → risk check → execute → settle |
| Performance | `saga_100ConcurrentSagas` | 100 parallel sagas → all resolve < 5s |
| Negative | `saga_undefinedStep_rejects` | Reference missing step → registration error |
| Security | `saga_correlationMaintained` | saga_id + correlation_id through all steps |

**Story Points**: 8  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, saga dashboard in Grafana, alerting for stuck sagas

---

## STORY-K05-007: Implement Saga Compensation Framework

**Epic**: K-05 Event Bus  
**Feature**: K05-F05 Saga orchestration  
**Priority**: P0 | **Sprint**: 5

**Description**: Implement the compensation handler framework that reverses the effects of completed saga steps when a later step fails. Each service registers compensation handlers invoked by the saga engine.

**Business Value**: Ensures financial consistency — if order execution fails after margin reservation, the margin is released.

**Technical Scope**:

- `CompensationHandler` interface: `compensate(sagaId, stepName, originalEvent): CompensationResult`
- Compensation registry: map (sagaDefinition, stepName) → compensationHandler
- Retry logic: 3 retries with exponential backoff for compensation steps
- Compensation audit: every compensation attempt logged to audit trail
- Idempotent compensation: safe to invoke multiple times

**Dependencies**: STORY-K05-006, K-07 (audit)

**Acceptance Criteria**:

- **AC1**: Given margin reserved in step A, When step B fails, Then compensation releases margin — balance restored
- **AC2**: Given compensation invoked twice for same step, When second invocation runs, Then idempotent — no double-release
- **AC3**: Given compensation fails, When retried 3 times, Then on final failure → manual review queue + alert
- **AC4**: Every compensation attempt recorded in audit trail with saga_id, step, outcome

**Edge Cases**:

- Compensation for already-compensated step (idempotent — no double reversal)
- Partial compensation (e.g., partial fill → partial unwind)
- Compensation timeout (longer timeout than forward steps)
- External system compensation (exchange cancel → may not be possible → manual)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `compensate_marginRelease_balanceRestored` | Release margin → balance matches pre-saga |
| Unit | `compensate_idempotent_noDoubleRelease` | Second call → no-op |
| Unit | `compensate_retryExhausted_manualQueue` | 3 failures → manual review + alert |
| Integration | `compensate_fullSagaRollback` | 3 steps completed, step 4 fails → all 3 compensated in reverse order |
| Integration | `compensate_auditTrailRecorded` | Every attempt → audit entry with saga_id |
| E2E | `compensate_orderCancelSaga` | Order saga fails → margin released, position not updated |
| Negative | `compensate_externalUnavailable_escalated` | Exchange down → escalation to manual resolution |
| Performance | `compensate_under500ms` | Single compensation completes < 500ms |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, compensation dashboard, manual review queue operational

---

# EPIC K-07: AUDIT FRAMEWORK

## STORY-K07-001: Implement Immutable Audit Log Schema

**Epic**: K-07 Audit Framework  
**Feature**: K07-F02 Hash-chain immutability  
**Priority**: P0 | **Sprint**: 1

**Description**: Create the audit_log PostgreSQL table with hash-chain immutability. Each entry includes SHA-256 hash of the previous entry, creating a tamper-evident chain. REVOKE UPDATE/DELETE enforced at database level.

**Business Value**: Regulatory requirement — audit trail must be provably tamper-proof for regulator inspections.

**Technical Scope**:

- `audit_log` table: id, timestamp_utc, timestamp_bs, actor_id, actor_type, action, resource_type, resource_id, old_value JSONB, new_value JSONB, metadata JSONB, previous_hash VARCHAR(64), entry_hash VARCHAR(64)
- Hash computation: SHA-256(previous_hash + action + resource_id + timestamp + data)
- REVOKE UPDATE, DELETE on audit_log from application role
- Partitioned by month for query performance
- Index: (resource_id, timestamp), (actor_id, timestamp), (entry_hash)

**Dependencies**: PostgreSQL cluster

**Acceptance Criteria**:

- **AC1**: Given audit entry, When stored, Then entry_hash = SHA-256(previous_hash || action || resource_id || timestamp || data)
- **AC2**: Given sequence of entries, When any entry tampered, Then hash chain breaks — detectable via chain_valid() function
- **AC3**: Given attempt to UPDATE audit_log, When SQL executed, Then permission denied error

**Edge Cases**:

- First entry in chain (previous_hash = genesis hash "0000...0000")
- Concurrent writes (serialize via advisory lock or sequence)
- Partition boundary (chain continues across partitions)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `auditLog_createEntry_hashChained` | Entry hash includes previous hash |
| Unit | `auditLog_tamperDetection_chainBreaks` | Modify entry → subsequent hashes invalid |
| Unit | `auditLog_genesisEntry_zeroPreviousHash` | First entry uses genesis hash |
| Integration | `auditLog_updateBlocked` | UPDATE SQL → permission denied |
| Integration | `auditLog_deleteBlocked` | DELETE SQL → permission denied |
| Integration | `auditLog_chainValidation_1000entries` | 1000 entries → chain_valid() returns true |
| Performance | `auditLog_writeThrough_under2ms` | Audit write < 2ms including hash computation |
| Security | `auditLog_tamperProof_verified` | Attempt DB-level modification → chain break detected |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, chain validation script provided

---

## STORY-K07-002: Implement Audit SDK

**Epic**: K-07 Audit Framework  
**Feature**: K07-F01 Audit SDK enforcement  
**Priority**: P0 | **Sprint**: 2

**Description**: Implement the Audit SDK that all services must use to record audit events. Provides `audit.log(action, resource, { old, new, metadata })` API with automatic enrichment (actor from JWT, correlation ID, dual-calendar timestamps).

**Business Value**: Standardized audit capture across all 33 microservices ensures consistent regulatory evidence.

**Technical Scope**:

- `AuditSDK.log(action, resourceType, resourceId, { oldValue?, newValue?, metadata? }): Promise<void>`
- Automatic enrichment: actor_id from request context (JWT), correlation_id, timestamp_bs (K-15), timestamp_utc
- Async write to audit_log (fire-and-forget with retry)
- Batch mode: batch writes for high-volume operations
- Express/Fastify middleware for automatic route-level audit
- Configurable audit scope per route (opt-in for reads, always-on for writes)

**Dependencies**: STORY-K07-001, K-01 (JWT context), K-15 (dual-calendar)

**Acceptance Criteria**:

- **AC1**: Given SDK configured in service, When `audit.log()` called, Then entry created with actor, correlation, dual timestamps
- **AC2**: Given Express middleware enabled, When POST/PUT/DELETE request processed, Then audit entry auto-created
- **AC3**: Given audit store unavailable, When `audit.log()` called, Then event buffered in memory (max 1000) and retried

**Edge Cases**:

- No JWT context (system-level operations → actor = "SYSTEM")
- Very high volume (10K writes/sec → batch mode)
- Circular references in old/new values (serialize safely)
- PII in audit data (flag for masking in export)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `auditSdk_logAction_createsEntry` | audit.log() → audit_log entry |
| Unit | `auditSdk_enrichesActor_fromJwt` | JWT present → actor_id extracted |
| Unit | `auditSdk_enrichesTimestamps_dual` | BS + Gregorian timestamps |
| Unit | `auditSdk_storeUnavailable_buffers` | Store down → buffered |
| Integration | `auditSdk_expressMiddleware_autoAudit` | POST request → auto audit entry |
| Integration | `auditSdk_batchMode_10kWrites` | 10K writes → batched efficiently |
| Performance | `auditSdk_overhead_under1ms` | SDK call adds < 1ms to request |
| Negative | `auditSdk_circularRef_serialized` | Circular object → safe serialization |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, SDK published as internal npm package

---

# EPIC K-01: IDENTITY & ACCESS MANAGEMENT

## STORY-K01-001: Implement OAuth 2.0 Authentication with JWT

**Epic**: K-01 IAM  
**Feature**: K01-F01 OAuth 2.0 + JWT authentication  
**Priority**: P0 | **Sprint**: 3

**Description**: Implement OAuth 2.0 authentication endpoint supporting authorization_code and client_credentials flows. Generates RS256-signed JWT access tokens with tenant-scoped claims and dual-calendar timestamps.

**Business Value**: Core identity infrastructure — no service call succeeds without an authenticated token.

**Technical Scope**:

- POST `/auth/token` (grant_type: authorization_code, client_credentials, refresh_token)
- RS256 JWT generation with kid rotation (JWKS endpoint)
- Claims: sub, tenant_id, roles[], permissions[], iss, aud, iat, exp, iat_bs, jti
- Refresh token: opaque, stored in Redis with family-based rotation
- Rate limiting: 10 auth attempts per minute per IP
- Failed attempt tracking: lock after 5 failures for 15 minutes
- PKCE support for authorization_code flow

**Dependencies**: K-14 (signing keys), K-15 (iat_bs), K-05 (AuthEvents emission), Redis

**Acceptance Criteria**:

- **AC1**: Given valid credentials + grant_type=client_credentials, When POST /auth/token, Then JWT returned with tenant-scoped claims, 200 OK
- **AC2**: Given valid refresh_token, When grant_type=refresh_token, Then new access token issued, old refresh token invalidated (rotation)
- **AC3**: Given 5 failed login attempts, When 6th attempt, Then 429 Too Many Requests, account locked 15 minutes
- **AC4**: Given expired access token, When API call made, Then 401 Unauthorized with WWW-Authenticate header

**Edge Cases**:

- Clock skew: JWT nbf/exp validated with 30-second tolerance
- Concurrent refresh token usage: second use revokes entire family
- JWKS rotation: new kid active, old kid valid for 24h grace period
- Token size > 8KB (optimize claims, use reference tokens for large permission sets)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `auth_validCreds_returnsJwt` | Valid login → JWT with correct claims |
| Unit | `auth_invalidCreds_returns401` | Wrong password → 401 |
| Unit | `auth_refreshToken_rotatesFamily` | Refresh → new tokens, old revoked |
| Unit | `auth_lockedAccount_returns429` | 5 failures → locked |
| Unit | `auth_pkce_validated` | PKCE code_verifier → validated |
| Integration | `auth_fullLoginCycle` | Login → token → API call → refresh → API call |
| Integration | `auth_jwksRotation_gracePeroid` | Old kid valid for 24h |
| E2E | `auth_loginToTradeFlow` | Login → get token → place order → verify auth |
| Negative | `auth_expiredToken_401` | Expired JWT → 401 |
| Negative | `auth_concurrentRefresh_revokesFamily` | Two refresh requests → family revoked |
| Performance | `auth_5kAuthPerSec` | 5,000 auth requests/sec sustained |
| Security | `auth_bruteForce_lockout` | Automated brute force → lockout |
| Security | `auth_tokenTampering_detected` | Modified JWT → signature verification fails |
| Security | `auth_sqlInjection_prevented` | SQL in username → sanitized |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, JWKS endpoint operational, Grafana auth dashboard

---

## STORY-K01-002: Implement Multi-Tenant Session Management

**Epic**: K-01 IAM  
**Feature**: K01-F02 Multi-tenant session management  
**Priority**: P0 | **Sprint**: 3

**Description**: Implement session management that supports multiple concurrent sessions per user, tenant isolation, and device tracking. Sessions stored in Redis with configurable TTL per tenant.

**Business Value**: Users may operate across multiple tenants and devices — sessions must be isolated and auditable.

**Technical Scope**:

- Session entity: session_id, user_id, tenant_id, device_fingerprint, created_at, last_active, expires_at, ip_address
- Redis storage: key = `session:{session_id}`, TTL per tenant config
- Max concurrent sessions per user (configurable, default 5)
- Session revocation: revoke single, revoke all for user, revoke all for tenant
- SessionCreated/SessionRevoked events (K-05)

**Dependencies**: STORY-K01-001, K-02 (tenant config for TTL), Redis

**Acceptance Criteria**:

- **AC1**: Given user authenticates, When session created, Then stored in Redis with tenant-specific TTL
- **AC2**: Given user has 5 active sessions, When 6th login, Then oldest session revoked automatically
- **AC3**: Given admin revokes all sessions for user, When revocation executes, Then all sessions deleted from Redis
- **AC4**: Given session idle > configured timeout, When next request made, Then 401 — session expired

**Edge Cases**:

- Redis failover (session loss → re-authenticate)
- Session created during tenant config reload (use old TTL)
- Cross-tenant session access attempt (strict tenant isolation)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `session_create_storedInRedis` | Login → session in Redis |
| Unit | `session_maxConcurrent_evictsOldest` | 6th session → oldest removed |
| Unit | `session_revokeAll_clearsUser` | Revoke → all sessions gone |
| Unit | `session_expired_returns401` | Idle timeout → 401 |
| Integration | `session_crossTenant_isolated` | Tenant A session can't access Tenant B |
| Integration | `session_redisFailover_reauthenticate` | Redis restart → 401 → re-login |
| Performance | `session_lookup_sub1ms` | Session validation < 1ms |

**Story Points**: 3  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, session metrics dashboard

---

## STORY-K01-003: Implement RBAC + ABAC Authorization Engine

**Epic**: K-01 IAM  
**Feature**: K01-F03 RBAC + ABAC authorization engine  
**Priority**: P0 | **Sprint**: 4

**Description**: Implement the authorization engine combining Role-Based Access Control (RBAC) with Attribute-Based Access Control (ABAC). Roles define base permissions; ABAC policies (via K-03) add attribute-based restrictions (e.g., "can only view orders in own department").

**Business Value**: Fine-grained access control required for regulatory compliance — users must only access authorized resources.

**Technical Scope**:

- Role model: Role → Permission[] mapping in PostgreSQL
- Permission model: resource:action (e.g., "orders:create", "ledger:read")
- ABAC integration: K-03 OPA evaluation with request context as input
- Authorization middleware: `authorize(resource, action)` → RBAC check → ABAC check
- Permission caching: Redis (invalidate on role change event)
- Maker-checker: some actions require dual authorization

**Dependencies**: STORY-K01-001, K-03 (OPA), K-02 (role config), Redis

**Acceptance Criteria**:

- **AC1**: Given user with role "trader", When accessing "orders:create", Then authorized (permission exists)
- **AC2**: Given user with role "viewer", When accessing "orders:create", Then 403 Forbidden
- **AC3**: Given ABAC policy "traders can only view own department orders", When trader queries other department, Then 403
- **AC4**: Given role permission change, When event fires, Then all cached permissions invalidated within 1 second

**Edge Cases**:

- User with multiple roles (union of permissions)
- Cyclic role inheritance (detect and prevent at registration)
- K-03 timeout (fallback to RBAC only with degraded flag)
- Super-admin bypass (audit all bypass usage)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `authz_validPermission_allows` | Matching role → authorized |
| Unit | `authz_missingPermission_denies` | No matching role → 403 |
| Unit | `authz_abacPolicy_restricts` | ABAC check → department isolation |
| Unit | `authz_multipleRoles_union` | Two roles → union permissions |
| Integration | `authz_cacheInvalidation_onRoleChange` | Role change → cache cleared < 1s |
| Integration | `authz_opaTimeout_fallbackRbac` | K-03 down → RBAC only + degraded flag |
| E2E | `authz_traderCannotViewOtherDept` | Login as trader → query other dept → 403 |
| Security | `authz_privilegeEscalation_prevented` | Token manipulation → authorization check still valid |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, role management API documented

---

# EPIC K-16: LEDGER FRAMEWORK

## STORY-K16-001: Implement Double-Entry Posting Engine

**Epic**: K-16 Ledger Framework  
**Feature**: K16-F01 Double-entry posting engine  
**Priority**: P0 | **Sprint**: 3

**Description**: Implement the core double-entry bookkeeping engine that enforces every debit has an equal credit. Supports multi-leg journal entries, multi-currency postings, and configurable decimal precision per currency.

**Business Value**: Financial integrity backbone — every trade, fee, dividend, and margin movement flows through balanced ledger entries.

**Technical Scope**:

- POST `/ledger/journals` — create journal with entries[]
- JournalEntry: journal_id, entry_id, account_id, direction (DEBIT/CREDIT), amount DECIMAL(28,12), currency, reference, description
- Balance enforcement: ∑debits == ∑credits per journal (per currency)
- Append-only: REVOKE UPDATE, DELETE from application role
- Account balance materialization: triggered by new postings
- Dual-calendar timestamps, fiscal year awareness (K-15)

**Dependencies**: K-02 (chart of accounts), K-05 (events), K-15 (calendar)

**Acceptance Criteria**:

- **AC1**: Given balanced journal entry (debit=5000 NPR, credit=5000 NPR), When posted, Then journal created, balances updated, JournalPosted event emitted
- **AC2**: Given unbalanced journal (debit=5000, credit=4999), When posted, Then rejected with UNBALANCED_JOURNAL error
- **AC3**: Given multi-leg entry (A→B 3000, A→C 2000, total debit A=5000, credit B=3000+C=2000), When posted, Then balanced and accepted
- **AC4**: Given attempt to UPDATE or DELETE journal entry, When SQL executed, Then permission denied

**Edge Cases**:

- Multi-currency journal (each currency must independently balance)
- Precision overflow (NPR 2 decimals, BTC 8 decimals — use DECIMAL(28,12))
- Concurrent postings to same account (safe with balance trigger)
- Reversal journal (new journal, not modification of original)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `ledger_balanced_creates` | Balanced entry → created |
| Unit | `ledger_unbalanced_rejects` | Unbalanced → UNBALANCED_JOURNAL |
| Unit | `ledger_multiLeg_balances` | 3-leg entry → balanced |
| Unit | `ledger_multiCurrency_eachBalances` | NPR balanced, USD balanced |
| Unit | `ledger_precisionOverflow_handled` | BTC precision → no truncation |
| Integration | `ledger_updateBlocked` | UPDATE → permission denied |
| Integration | `ledger_deleteBlocked` | DELETE → permission denied |
| Integration | `ledger_balanceProjection_accurate` | 100 postings → balance matches sum |
| E2E | `ledger_tradeSettlementPosting` | Trade → settlement → ledger entries → balanced |
| Performance | `ledger_20kPostingsPerSec` | 20,000 postings/sec sustained |
| Negative | `ledger_zeroAmount_rejected` | Zero amount entry → rejected |
| Security | `ledger_immutabilityVerified` | Direct DB modification attempt → blocked |

**Story Points**: 8  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, chart of accounts seeded, balance reconciliation script

---

# EPIC D-01: ORDER MANAGEMENT SYSTEM

## STORY-D01-001: Implement Order Capture API

**Epic**: D-01 OMS  
**Feature**: D01-F01 Order capture & validation  
**Priority**: P0 | **Sprint**: 6

**Description**: Implement POST /orders endpoint for order capture with field validation, instrument tradability check (D-11), minimum lot validation (K-02 config), and OrderPlaced event emission.

**Business Value**: Core trading capability — the entry point for every trade on the platform.

**Technical Scope**:

- POST `/orders`: { instrument_id, quantity, side (BUY/SELL), order_type (MARKET/LIMIT), price?, client_id, time_in_force, idempotency_key }
- Validation: required fields, instrument exists (D-11), instrument.status == ACTIVE
- Lot size check: quantity % min_lot == 0 (from K-02 jurisdiction config)
- Market hours check: reject if market closed (K-15 calendar + D-04 market status)
- Order creation in PENDING state
- OrderPlaced event (K-05) with full order data
- Dual-calendar timestamps (K-15)
- Idempotency (K-05 dedup by idempotency_key)

**Dependencies**: K-01 (auth), K-02 (config), K-05 (events + idemp), K-15 (calendar), D-11 (ref data)

**Acceptance Criteria**:

- **AC1**: Given valid BUY order for active instrument, When POST /orders, Then order created in PENDING state, OrderPlaced event emitted, 201 Created
- **AC2**: Given SELL order with quantity < min_lot (e.g., 5 kitta where min=10), When POST /orders, Then 422 with "BELOW_MIN_LOT" error
- **AC3**: Given order for suspended instrument, When POST /orders, Then 422 with "INSTRUMENT_SUSPENDED"
- **AC4**: Given duplicate idempotency_key, When POST /orders again, Then returns original order (no duplicate), 200 OK

**Edge Cases**:

- Market closes between validation and creation (race condition → check again in event handler)
- Instrument suspended after validation but before event processing (idempotent rejection downstream)
- Very large quantity (max order value check from config)
- Price = 0 for LIMIT order (rejected)
- Missing client_id (rejected — required)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `captureOrder_valid_createsPending` | Valid input → PENDING order |
| Unit | `captureOrder_belowMinLot_rejects422` | Below lot → 422 |
| Unit | `captureOrder_suspendedInstrument_rejects` | Suspended → 422 |
| Unit | `captureOrder_duplicateKey_idempotent` | Same key → same order |
| Unit | `captureOrder_marketClosed_rejects` | Closed market → 422 |
| Unit | `captureOrder_limitZeroPrice_rejects` | LIMIT + price=0 → 422 |
| Integration | `captureOrder_eventEmitted` | Order → OrderPlaced in Kafka |
| Integration | `captureOrder_instrumentLookup_d11` | D-11 reference data queried |
| E2E | `captureOrder_throughUI_toEvent` | UI form → POST → event → projection |
| Negative | `captureOrder_missingFields_400` | Missing instrument_id → 400 |
| Performance | `captureOrder_10kPerSec` | 10,000 orders/sec sustained |
| Security | `captureOrder_ownClientOnly` | Client A can't place for Client B |

**Story Points**: 5  
**Team**: Beta (Backend)  
**DoD**: All tests passing, OpenAPI spec published, order capture Grafana panel

---

## STORY-D01-002: Implement Order State Machine

**Epic**: D-01 OMS  
**Feature**: D01-F02 Order state machine  
**Priority**: P0 | **Sprint**: 6

**Description**: Implement the 9-state order lifecycle machine: DRAFT → PENDING → PENDING_APPROVAL → APPROVED → ROUTED → PARTIALLY_FILLED → FILLED → CANCELLED → REJECTED. Each transition emits a domain event and validates against allowed transition matrix.

**Business Value**: Ensures audit-safe order lifecycle — invalid state transitions are architecturally impossible.

**Technical Scope**:

- State enum: DRAFT, PENDING, PENDING_APPROVAL, APPROVED, ROUTED, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED
- Transition matrix (allowed_transitions map)
- Command handlers: submit(), approve(), route(), partialFill(), fill(), cancel(), reject()
- Domain events per transition: OrderSubmitted, OrderApproved, OrderRouted, OrderPartiallyFilled, OrderFilled, OrderCancelled, OrderRejected
- Event-sourced state reconstruction from event stream
- Optimistic concurrency on transition commands

**Dependencies**: STORY-D01-001, K-05 (events)

**Acceptance Criteria**:

- **AC1**: Given order in PENDING, When approved, Then state → APPROVED, OrderApproved event emitted
- **AC2**: Given order in FILLED, When cancel attempted, Then InvalidTransitionError — no event emitted
- **AC3**: Given order in ROUTED, When partialFill(qty=50) on order(qty=100), Then state → PARTIALLY_FILLED, filled_qty=50
- **AC4**: Given event stream [OrderPlaced, OrderApproved, OrderRouted], When reconstructed, Then current state = ROUTED

**Edge Cases**:

- Concurrent approve and cancel commands (first wins, second fails)
- Partial fill followed by cancel (PARTIALLY_FILLED → CANCELLED allowed — remaining qty cancelled)
- Multiple partial fills → final fill → FILLED
- Replay from event stream produces identical state

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `stateMachine_pending_approve_approved` | PENDING → APPROVED |
| Unit | `stateMachine_filled_cancel_invalid` | FILLED → cancel → error |
| Unit | `stateMachine_routed_partialFill_partial` | ROUTED → PARTIALLY_FILLED |
| Unit | `stateMachine_partialFill_cancel_allowed` | PARTIALLY_FILLED → CANCELLED |
| Unit | `stateMachine_multiplePartialFills_filled` | 3 partial fills → FILLED |
| Unit | `stateMachine_reconstructFromEvents` | Event stream → correct state |
| Integration | `stateMachine_concurrentTransition_firstWins` | Parallel approve+cancel → one succeeds |
| Integration | `stateMachine_eventSourcing_consistent` | Replay → same state |
| Performance | `stateMachine_100kTransitionsPerSec` | 100K transitions/sec |
| Negative | `stateMachine_invalidTransition_allCombos` | All invalid pairs tested |

**Story Points**: 5  
**Team**: Beta (Backend)  
**DoD**: All tests passing, state diagram documented, transition matrix in code + tests

---

## STORY-D01-003: Implement Pre-Trade Evaluation Pipeline

**Epic**: D-01 OMS  
**Feature**: D01-F03 Pre-trade evaluation pipeline  
**Priority**: P0 | **Sprint**: 6

**Description**: Implement the pre-trade evaluation pipeline that runs compliance and risk checks before an order is routed. Integrates K-03 (rules engine) for compliance rules and D-06 for risk checks. Pipeline is configurable per jurisdiction.

**Business Value**: Regulatory requirement — every order MUST pass compliance and risk checks before execution. Prevents regulatory violations.

**Technical Scope**:

- Pipeline: [ComplianceCheck, RiskCheck, MakerCheckerCheck(if configured)] → APPROVED | REJECTED
- ComplianceCheck: D-07 rules evaluation (lock-in, restricted list, beneficial ownership)
- RiskCheck: D-06 pre-trade risk (margin, position limits, concentration)
- MakerCheckerCheck: if order exceeds threshold (K-02 config) → route to PENDING_APPROVAL
- Pipeline result aggregation: all checks must PASS; first FAIL → order REJECTED with reasons
- Configurable check ordering per jurisdiction

**Dependencies**: K-02 (config), K-03 (rules), D-06 (risk), D-07 (compliance)

**Acceptance Criteria**:

- **AC1**: Given order passing all checks, When pipeline runs, Then order state → APPROVED, all check results recorded
- **AC2**: Given order failing compliance (restricted instrument), When pipeline runs, Then order → REJECTED with "RESTRICTED_INSTRUMENT"
- **AC3**: Given order exceeding maker-checker threshold, When pipeline runs, Then order → PENDING_APPROVAL, notification sent to approver
- **AC4**: Given pipeline configuration for jurisdiction "NP", When NP-specific rules loaded, Then NP rules evaluated (not other jurisdictions)

**Edge Cases**:

- Multiple checks fail simultaneously (all reasons aggregated in rejection)
- D-06 timeout (circuit breaker → configurable: reject or degrade)
- Check passes but subsequent check modifies context (pipeline runs sequentially)
- Empty pipeline configuration (default: reject all — fail-safe)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `preTrade_allPass_approved` | All checks pass → APPROVED |
| Unit | `preTrade_complianceFail_rejected` | Compliance fail → REJECTED |
| Unit | `preTrade_riskFail_rejected` | Risk fail → REJECTED |
| Unit | `preTrade_aboveThreshold_pendingApproval` | Large order → PENDING_APPROVAL |
| Unit | `preTrade_multipleFails_aggregated` | Compliance + risk fail → both reasons |
| Integration | `preTrade_jurisdictionRouting` | NP rules → NP pipeline |
| Integration | `preTrade_riskTimeout_circuitBreaker` | D-06 timeout → configured behavior |
| E2E | `preTrade_fullOrderToApproval` | Order → pipeline → approval → route |
| Performance | `preTrade_pipeline_under10ms` | Full pipeline < 10ms P99 |
| Negative | `preTrade_emptyPipeline_rejectsAll` | No config → default reject |

**Story Points**: 5  
**Team**: Beta (Backend)  
**DoD**: All tests passing, pipeline config documented, rejection reasons standardized

---

# EPIC D-06: RISK ENGINE

## STORY-D06-001: Implement Pre-Trade Risk Check Pipeline

**Epic**: D-06 Risk Engine  
**Feature**: D06-F01 Pre-trade risk checks  
**Priority**: P0 | **Sprint**: 7

**Description**: Implement the synchronous pre-trade risk validation called for every order. Must complete < 5ms P99. Checks margin sufficiency, position limits, concentration limits via cached risk data in Redis.

**Business Value**: Prevents unauthorized risk exposure. Regulatory requirement for every trade.

**Technical Scope**:

- `RiskCheckRequest { client_id, instrument_id, side, quantity, price, order_type }` → `RiskCheckResult { approved: boolean, reasons: string[], risk_metrics }`
- Margin check: available_margin >= required_margin (D-06 margin model)
- Position limit: current_position + order_quantity <= max_position (K-02 config)
- Concentration: (position_value / portfolio_value) <= max_concentration % (K-02 config)
- Redis cache for: current positions, available margin, limits
- Cache warm-up on startup, event-driven updates
- K-03 integration for jurisdiction-specific risk rules
- Prometheus histogram: risk_check_latency_ms

**Dependencies**: K-02 (limits config), K-03 (rules), K-05 (events for cache updates), Redis

**Acceptance Criteria**:

- **AC1**: Given margin_required=10K, margin_available=5K, When risk check, Then DENY with "INSUFFICIENT_MARGIN" in < 2ms
- **AC2**: Given all limits within bounds, When risk check, Then APPROVE in < 5ms P99
- **AC3**: Given concentration at 4.8% with max 5%, When order would push to 5.2%, Then DENY with "CONCENTRATION_LIMIT"
- **AC4**: Given Redis cache miss, When position lookup falls back to PostgreSQL, Then check completes (degraded latency) < 50ms

**Edge Cases**:

- Concurrent orders consuming same margin (atomic Redis decrement with check)
- Redis down (fallback to PostgreSQL — degrade to 50ms SLA)
- Negative margin (impossible — clamp to 0)
- K-03 timeout (circuit breaker → default DENY for safety)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `riskCheck_insufficientMargin_deny` | Margin < required → DENY |
| Unit | `riskCheck_withinLimits_approve` | All ok → APPROVE |
| Unit | `riskCheck_concentrationExceeded_deny` | Over 5% → DENY |
| Unit | `riskCheck_cacheMiss_fallbackDb` | Redis miss → PostgreSQL lookup |
| Unit | `riskCheck_ruleTimeout_defaultDeny` | K-03 timeout → DENY |
| Integration | `riskCheck_atomicMarginDeduction` | Two orders, one margin → first wins |
| Integration | `riskCheck_cacheWarmup_onStart` | Service start → cache populated < 5s |
| Integration | `riskCheck_eventDrivenCacheUpdate` | Trade event → cache updated < 100ms |
| E2E | `riskCheck_orderToExecutionChain` | Order → risk ok → routed → executed |
| Performance | `riskCheck_50kPerSec_under5ms` | 50K checks/sec at < 5ms P99 |
| Negative | `riskCheck_redisDown_degraded` | Redis offline → PostgreSQL fallback |
| Security | `riskCheck_cannotBypass` | Direct route without risk check → blocked |

**Story Points**: 8  
**Team**: Beta (Backend)  
**DoD**: All tests passing, latency dashboard, cache hit rate > 99%

---

# EPIC K-15: DUAL-CALENDAR SERVICE

## STORY-K15-001: Implement BS ↔ Gregorian Conversion Library

**Epic**: K-15 Dual-Calendar  
**Feature**: K15-F01 BS ↔ Gregorian conversion  
**Priority**: P0 | **Sprint**: 1

**Description**: Implement the core calendar conversion library supporting Bikram Sambat ↔ Gregorian (and ISO) date conversion using Julian Day Number (JDN) arithmetic + lookup table for BS month lengths (which vary by year due to Nepal's lunisolar calendar).

**Business Value**: Dual-calendar is a platform invariant. Every timestamp, report, and settlement date must exist in both calendar systems.

**Technical Scope**:

- `bsToGregorian(bsYear, bsMonth, bsDay): { year, month, day }`
- `gregorianToBs(year, month, day): { bsYear, bsMonth, bsDay }`
- `DualDate` type: `{ gregorian: Date, bs: { year, month, day }, fiscalYear: string }`
- `toDualDate(date: Date): DualDate` — generates both representations
- BS month length lookup table (2000 BS – 2100 BS)
- Error handling for invalid dates (e.g., BS month 13, day 33)
- No external dependencies — pure function library

**Dependencies**: None (foundational)

**Acceptance Criteria**:

- **AC1**: Given Gregorian 2024-04-13, When converted to BS, Then returns 2081-01-01 (BS New Year)
- **AC2**: Given BS 2081-01-01, When converted to Gregorian, Then returns 2024-04-13
- **AC3**: Given invalid BS date (month=13), When conversion attempted, Then throws InvalidDateError
- **AC4**: Given date range 2000-2100 BS, When all days converted round-trip, Then original date equals result (no loss)

**Edge Cases**:

- BS year with 32-day month (some Ashadh months have 32 days)
- Boundary between BS years (last day of Chaitra → first day of Baisakh)
- Gregorian leap year alignment with BS
- Dates before lookup table range (< 2000 BS → graceful error)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `bsToGreg_2081_01_01_returns_20240413` | BS New Year conversion |
| Unit | `gregToBs_20240413_returns_2081_01_01` | Reverse conversion |
| Unit | `roundTrip_allDays_2000to2100` | 36,500+ days round-trip |
| Unit | `invalidBs_month13_throws` | Invalid month → error |
| Unit | `invalidBs_day33_throws` | Invalid day → error |
| Unit | `bsMonth_32days_handled` | 32-day Ashadh → correct |
| Unit | `yearBoundary_chaitraToBaisakh` | Last day Chaitra → next day Baisakh |
| Performance | `conversion_1million_under500ms` | 1M conversions < 500ms |
| Negative | `beforeLookupRange_gracefulError` | < 2000 BS → error |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, lookup table verified against authoritative source, exported as npm package

---

# EPIC D-11: REFERENCE DATA SERVICE

## STORY-D11-001: Implement Instrument Master Data Management

**Epic**: D-11 Reference Data  
**Feature**: D11-F01 Instrument master data  
**Priority**: P0 | **Sprint**: 5

**Description**: Implement the instrument reference data service managing all tradable instruments (equities, bonds, mutual funds, derivatives). Supports CRUD with maker-checker, temporal validity (effective dates), and InstrumentChanged event emission.

**Business Value**: Every trading, risk, compliance, and settlement check needs instrument data — this is the single source of truth.

**Technical Scope**:

- Instrument entity: id, symbol, isin, name, type (EQUITY/BOND/MF/DERIVATIVE), status (ACTIVE/SUSPENDED/DELISTED), sector, lot_size, tick_size, currency, exchange, effective_from, effective_to
- CRUD API: GET/POST/PUT /instruments with maker-checker workflow
- Temporal validity: queries return data valid-as-of a given date
- InstrumentCreated, InstrumentUpdated, InstrumentSuspended events (K-05)
- Bulk import API for exchange feed reconciliation
- Redis cache for hot lookups (invalidate on change events)

**Dependencies**: K-01 (auth), K-02 (config), K-05 (events), K-07 (audit)

**Acceptance Criteria**:

- **AC1**: Given new instrument registration, When POST /instruments, Then created with PENDING_APPROVAL status, maker-checker initiated
- **AC2**: Given instrument approved, When queried with GET /instruments/{id}, Then returns instrument data with cache header
- **AC3**: Given instrument suspended, When OMS queries tradability, Then returns status=SUSPENDED
- **AC4**: Given historical query (as_of=2080-06-15 BS), When GET /instruments?as_of=2080-06-15, Then returns data valid at that date

**Edge Cases**:

- Instrument with same symbol but different exchange (unique by symbol+exchange)
- Bulk import with 5000 instruments (batch processing, partial failure handling)
- Cache invalidation race (event arrives before DB commit → eventual consistency)
- Instrument type change (rare — requires special approval workflow)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `instrument_create_pendingApproval` | Create → PENDING_APPROVAL |
| Unit | `instrument_approve_active` | Approve → ACTIVE |
| Unit | `instrument_suspend_reflected` | Suspend → status change |
| Unit | `instrument_temporalQuery_correctDate` | As-of query → correct version |
| Integration | `instrument_eventEmission_onUpdate` | Update → InstrumentUpdated event |
| Integration | `instrument_cacheInvalidation` | Update → cache cleared < 1s |
| Integration | `instrument_bulkImport_5000` | 5000 instruments → all imported |
| Performance | `instrument_lookup_sub1ms` | Single lookup < 1ms (cached) |
| Negative | `instrument_duplicateSymbol_rejected` | Same symbol+exchange → 409 |
| Security | `instrument_makerCheckerEnforced` | Create without approval → not active |

**Story Points**: 5  
**Team**: Gamma (Data & Compliance)  
**DoD**: All tests passing, bulk import documented, cache hit rate > 99.5%

---

# EPIC K-14: SECRETS MANAGEMENT

## STORY-K14-001: Implement Multi-Provider Vault Abstraction

**Epic**: K-14 Secrets Management  
**Feature**: K14-F01 Multi-provider vault abstraction  
**Priority**: P0 | **Sprint**: 3

**Description**: Implement the secrets management abstraction layer supporting HashiCorp Vault, AWS Secrets Manager, and local file-based storage (for air-gap deployments). All secret access goes through a unified API.

**Business Value**: Secrets never hardcoded; JWT signing keys, DB passwords, API keys managed centrally with rotation support.

**Technical Scope**:

- `SecretProvider` interface: `getSecret(path)`, `putSecret(path, value)`, `rotateSecret(path)`, `listSecrets(prefix)`
- HashiCorp Vault provider (KV v2 engine)
- AWS Secrets Manager provider
- Local encrypted file provider (for air-gap, AES-256-GCM encrypted)
- Configuration to select provider per environment
- Secret caching with configurable TTL (default 5 minutes)
- Access logging (every get/put logged to audit)

**Dependencies**: K-07 (audit logging)

**Acceptance Criteria**:

- **AC1**: Given Vault provider configured, When `getSecret("db/postgres/password")`, Then returns current secret value
- **AC2**: Given secret rotated in Vault, When `getSecret()` called after cache TTL, Then returns new value
- **AC3**: Given air-gap deployment with file provider, When `getSecret()` called, Then decrypts and returns from local file
- **AC4**: Every `getSecret()` and `putSecret()` call recorded in audit trail

**Edge Cases**:

- Vault unavailable (use cached value until TTL expires; if expired → fail-open or fail-closed configurable)
- Secret not found (clear error with path)
- Concurrent rotation requests (first wins, second no-ops)
- Large secrets (certificates — handle binary)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `vault_getSecret_returnsValue` | Vault lookup → correct value |
| Unit | `vault_cacheHit_noRemoteCall` | Within TTL → cached |
| Unit | `vault_cacheExpired_refreshed` | Past TTL → fresh lookup |
| Unit | `file_getSecret_decrypts` | Encrypted file → decrypted value |
| Integration | `vault_putThenGet_roundtrip` | Put → get → matches |
| Integration | `vault_rotation_newValueAfterTtl` | Rotate → cached expires → new value |
| Integration | `vault_auditLogged` | Every access → audit entry |
| Negative | `vault_unavailable_cachedFallback` | Vault down → cached value |
| Negative | `vault_notFound_clearError` | Missing path → 404 with path |
| Security | `vault_noPlaintextInLogs` | Secret values never logged |

**Story Points**: 5  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, provider selection via K-02 config, no plaintext secrets in logs

---

# EPIC K-18: RESILIENCE PATTERNS

## STORY-K18-001: Implement Circuit Breaker Library

**Epic**: K-18 Resilience Patterns  
**Feature**: K18-F01 Circuit breaker library  
**Priority**: P0 | **Sprint**: 3

**Description**: Implement a circuit breaker library with three states (CLOSED/OPEN/HALF_OPEN). Configurable failure thresholds, timeout, and half-open probe count. Emits state change events for observability.

**Business Value**: Prevents cascade failures across microservices — if downstream service fails, callers degrade gracefully.

**Technical Scope**:

- CircuitBreaker class: `execute(fn, fallback?)` → result | fallback
- States: CLOSED (normal), OPEN (fail-fast), HALF_OPEN (probe)
- Config: failure_threshold (default 5), timeout_ms (default 30000), half_open_probes (default 3)
- Failure tracking: sliding window (count-based or time-based)
- Metrics: circuit_state gauge, failure_count, success_count, open_duration
- CircuitOpened/CircuitClosed events (K-05)
- Pre-defined profiles: `STRICT` (threshold=3, timeout=60s), `RELAXED` (threshold=10, timeout=15s)

**Dependencies**: K-06 (metrics), K-05 (events)

**Acceptance Criteria**:

- **AC1**: Given circuit CLOSED, When 5 consecutive failures, Then circuit → OPEN, subsequent calls return fallback immediately
- **AC2**: Given circuit OPEN for 30 seconds, When timeout expires, Then circuit → HALF_OPEN, next calls treated as probes
- **AC3**: Given circuit HALF_OPEN, When 3 probe calls succeed, Then circuit → CLOSED, normal operation resumes
- **AC4**: Given circuit transitions, When state changes, Then CircuitOpened/CircuitClosed event emitted, Prometheus metric updated

**Edge Cases**:

- Intermittent failures (5 failures need not be consecutive with time-based window)
- Concurrent execution during HALF_OPEN (only probe count allowed, rest fail-fast)
- Fallback itself throws (propagate fallback error)
- Circuit breaker per-target (separate circuit per downstream service)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `cb_closedToOpen_onThreshold` | 5 failures → OPEN |
| Unit | `cb_open_failsFast` | OPEN → immediate fallback |
| Unit | `cb_openToHalfOpen_afterTimeout` | 30s → HALF_OPEN |
| Unit | `cb_halfOpenToClosed_onProbeSuccess` | 3 probes ok → CLOSED |
| Unit | `cb_halfOpenToOpen_onProbeFail` | Probe fails → OPEN again |
| Unit | `cb_profiles_strictRelaxed` | STRICT config → threshold=3 |
| Integration | `cb_perTarget_isolatedCircuits` | Service A circuit ≠ Service B circuit |
| Integration | `cb_metricsExposed` | Prometheus gauge reflects state |
| Integration | `cb_eventEmitted_onStateChange` | OPEN → CircuitOpened event |
| Performance | `cb_overhead_sub100us` | Circuit check adds < 100μs |
| Negative | `cb_fallbackThrows_propagated` | Fallback error → propagated |

**Story Points**: 3  
**Team**: Alpha (Platform)  
**DoD**: All tests passing, Grafana circuit breaker dashboard, alert on OPEN

---

# EPIC D-14: SANCTIONS SCREENING

## STORY-D14-001: Implement Real-Time Screening Engine

**Epic**: D-14 Sanctions Screening  
**Feature**: D14-F01 Real-time screening engine  
**Priority**: P0 | **Sprint**: 6

**Description**: Implement real-time sanctions screening service that checks client names, counterparties, and beneficiaries against sanction lists (OFAC, UN, EU, local). Must complete screening < 50ms P99. Returns match results with confidence scores.

**Business Value**: Regulatory obligation — every trade, onboarding, and payment must be screened against sanctions lists. Fines for violations are severe.

**Technical Scope**:

- POST `/screening/check` { name, type (INDIVIDUAL/ENTITY), nationality?, dob?, additional_identifiers[] }
- Screening against in-memory sanctions list (loaded from DB, refreshed hourly)
- Match result: { match_found: boolean, matches: [{ list, entry, score, match_type }] }
- Score threshold: configurable (default 0.85 for AUTO_BLOCK, 0.70 for REVIEW)
- Integration with OMS (pre-order), W-02 (onboarding), D-09 (settlement)
- ScreeningCompleted event (K-05) with result

**Dependencies**: K-01 (auth), K-02 (config for thresholds), K-05 (events), K-07 (audit)

**Acceptance Criteria**:

- **AC1**: Given name matching OFAC SDN list entry (exact), When screened, Then match_found=true, score=1.0, match_type="EXACT", < 50ms
- **AC2**: Given name not on any list, When screened, Then match_found=false, < 50ms
- **AC3**: Given fuzzy match (score=0.78), When screened, Then match_found=true, match_type="FUZZY", flagged for REVIEW
- **AC4**: Every screening attempt and result recorded in audit trail (K-07)

**Edge Cases**:

- Name with special characters (normalize before matching)
- Name in non-Latin script (transliteration support)
- Multiple matches across different lists (aggregate results)
- Sanctions list update during screening (atomic swap — no partial state)

**Test Plan**:
| Type | Test Name | Description |
|------|-----------|-------------|
| Unit | `screen_exactMatch_returnsFull` | Exact name → score=1.0 |
| Unit | `screen_noMatch_returnsFalse` | Clean name → no match |
| Unit | `screen_fuzzyMatch_reviewThreshold` | Score=0.78 → REVIEW |
| Unit | `screen_specialChars_normalized` | "Müller" → normalized → matched |
| Integration | `screen_listRefresh_atomicSwap` | List update → consistent screening |
| Integration | `screen_auditTrailRecorded` | Every check → audit entry |
| Integration | `screen_preOrderIntegration` | Order → screening → result |
| E2E | `screen_onboardingToTrade` | Onboard → screen → approved → trade |
| Performance | `screen_10kPerSec_under50ms` | 10K screenings/sec at < 50ms P99 |
| Negative | `screen_emptyName_rejected` | Empty name → 400 |
| Security | `screen_resultsEncrypted` | Match details encrypted at rest |

**Story Points**: 5  
**Team**: Gamma (Data & Compliance)  
**DoD**: All tests passing, screening dashboard, false positive rate tracked

---

> **Backlog continues with identical template for all remaining ~600 stories across all 42 epics.**  
> Each story includes: ID, Title, Epic, Feature, Priority, Sprint, Description, Business Value, Technical Scope, Dependencies, Acceptance Criteria (Given/When/Then), Edge Cases, Test Plan (unit/integration/e2e/negative/performance/security table), Story Points, Team, DoD.

---

# BACKLOG STATISTICS

| Metric                                  | Value                             |
| --------------------------------------- | --------------------------------- |
| Total Epics                             | 42                                |
| Total Features                          | 246                               |
| Normalized Backlog Stories              | 654                               |
| Normalized Backlog Story Points         | ~1,930                            |
| Stories Fully Detailed (above)          | 25 (representative critical path) |
| Stories Remaining To Detail In Grooming | ~629                              |
| Sprint Cadence                          | 2 weeks                           |
| Planned Sprints                         | 30                                |
| Teams                                   | 6                                 |
| Engineers                               | 27                                |
| MVP Target                              | Sprint 30 (April 2027)            |

---

**END OF STORY BACKLOG**
