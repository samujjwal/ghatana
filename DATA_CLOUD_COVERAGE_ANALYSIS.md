# Data-Cloud Module Test Coverage Analysis & Improvement Plan

**Analysis Date:** April 6, 2026  
**Current Coverage:** 12-20% (estimated)  
**Target Coverage:** 80%+  
**Analysis Scope:** platform/ and platform-launcher/ subdirectories

---

## Data-Cloud Module Structure

### Main Modules (13 total)

1. **spi** — Service Provider Interface (storage plugins, contracts)
2. **platform-entity** — Entity domain models, schema validation, versioning
3. **platform-event** — Event durability, streaming, checkpoint management
4. **platform-config** — Configuration management
5. **platform-analytics** — Analytics query engine, anomaly detection, reports
6. **platform-launcher** — DI modules, resilience patterns, feature flags
7. **platform-client** — Client SDK (minimal infra deps)
8. **platform-plugins** — Storage plugin implementations
9. **platform-api** — REST/gRPC/GraphQL controllers, AI services, governance
10. **launcher** — HTTP/gRPC servers, request handlers, security
11. **sdk** — Public SDK library
12. **agent-registry** — Data quality agents, schema validation, event publishing
13. **feature-store-ingest** — Feature extraction and store ingestion
14. **data-cloud-cache** — Query caching layer
15. **api** — OpenAPI specification layer

### Source Files Count

- **Estimated total source files:** 280+ Java files across all modules
- **Large modules by file count:**
  - `platform-api`: ~100 files (ai, analytics, application, api, client, memory, learning, workspace, security, etc.)
  - `launcher`: ~70 files (HTTP handlers, bootstrap, support, voice)
  - `platform-entity`: ~50 files (entity models, schema, versioning, quality, search, webhook, etc.)
  - `platform-event`: ~35 files (durability, streaming, repos, secrets, checkpoints)
  - Others: ~25 files each

### Current Test Files Count

- **Total test files visible:** ~100 files
- **Test distribution:**
  - `launcher/src/test`: ~60 tests (comprehensive)
  - `platform-analytics/src/test`: ~8 tests
  - `platform-entity/src/test`: ~10 tests
  - `feature-store-ingest/src/test`: ~6 tests
  - `spi/src/test`: ~5 tests
  - `api/src/test`: ~3 tests

### Estimated Line Coverage

- **Untested lines:** ~3,000-4,500 lines (based on 12-20% coverage of ~280 files)
- **Tested lines:** ~500-1,000 lines
- \*\*Total:" ~4,000-5,500 lines of code

---

## Coverage Analysis

### Current Coverage Breakdown

| Module               | Est. Files | Est. Lines | Tested %    | Status            |
| -------------------- | ---------- | ---------- | ----------- | ----------------- |
| launcher             | 70         | 900        | 70%         | Good              |
| feature-store-ingest | 12         | 150        | 50%         | Fair              |
| platform-analytics   | 20         | 280        | 25%         | Poor              |
| platform-entity      | 50         | 650        | 20%         | Poor              |
| platform-event       | 35         | 480        | 10%         | Critical          |
| platform-api         | 100        | 1400       | 5%          | Critical          |
| agent-registry       | 8          | 120        | 0%          | Critical          |
| data-cloud-cache     | 5          | 80         | 0%          | Critical          |
| platform-config      | 8          | 120        | 0%          | Critical          |
| platform-launcher    | 25         | 350        | 5%          | Critical          |
| spi                  | 15         | 200        | 15%         | Poor              |
| api                  | 8          | 110        | 10%         | Critical          |
| **TOTAL**            | **~280**   | **~5,320** | **~12-15%** | **Overall: Poor** |

---

## Major Coverage Gaps (Top 5 Critical Areas)

### Gap 1: platform-api (100 files, ~1400 LOC, 5% coverage)

**Impact:** ⚠️ **CRITICAL** — Largest untested module

- **Missing:** AI assistance layer, application services, governance, learning models, client autonomy, webhooks
- **Untested lines:** ~1,300
- **Risk:** Core business logic (entity CRUD, collection management, autonomy control) lacks unit tests
- **Estimated tests needed:** 25-30

### Gap 2: platform-event (35 files, ~480 LOC, 10% coverage)

**Impact:** ⚠️ **CRITICAL** — Event infrastructure

- **Missing:** Event durability service, replay service, streaming plugins, secret management, checkpoint handling
- **Untested lines:** ~430
- **Risk:** Event flow failures, data loss, checkpoint corruption not caught
- **Estimated tests needed:** 18-22

### Gap 3: platform-launcher (25 files, ~350 LOC, 5% coverage)

**Impact:** ⚠️ **CRITICAL** — Dependency injection & resilience

- **Missing:** DI module composition, circuit breaker configuration, storage module setup, attention manager
- **Untested lines:** ~330
- **Risk:** Boot-time failures, misconfigured dependencies, resilience failures
- **Estimated tests needed:** 12-15

### Gap 4: platform-api sub-packages (100 files, varies)

**Impact:** ⚠️ **CRITICAL** — Multiple untested subsystems

- **ai/** (6 files, ~100 LOC): `AIAssistService`, `LLMProvider`, `ContextWindowManager`, `PromptTemplateManager` — 0 tests
- **governance/** (2 files, ~40 LOC): `PolicyService`, `DataRetentionManager` — 0 tests
- **learning/** (4 files, ~80 LOC): `ModelTrainingService`, `ModelEvaluationService`, `LearningProgressTracker` — 0 tests
- **application/** (15 files, ~300 LOC): entity/collection/workflow services, webhook delivery — ~1-2 tests
- **client/** (8 files, ~120 LOC): autonomy controller, context gateway, learning signals — 0 tests
- **Estimated tests needed:** 30-40

### Gap 5: agent-registry & data-cloud-cache (13 files, ~200 LOC, 0% coverage)

**Impact:** ⚠️ **CRITICAL** — Agent execution & caching

- **agent-registry:** Three production agents (SchemaValidator, DataAnomalyDetector, DataSync) — 0 tests
- **data-cloud-cache:** Query caching layer — 0 tests
- **Estimated tests needed:** 8-12

---

## Priority Assessment by Coverage Gain Impact

### Priority 1 Areas (Most Tests Needed)

These areas have 0% coverage and are core to system functionality.

1. **AI Assist Service & Co**
   - Module: `platform-api/ai/`
   - Files: `AIAssistService`, `AIAssistServiceImpl`, `LLMProvider`, `ContextWindowManager`, `PromptTemplateManager`
   - Risk: AI feature failures, prompt injection, context window overflows not caught
   - Tests needed: **5-7**
   - Impact: **+3-4% coverage increase**

2. **Entity Service Layer**
   - Module: `platform-api/application/`
   - Files: `EntityService`, `EntityServiceImpl`, `EntityValidationService`, `CollectionService`
   - Risk: Entity CRUD failures, validation bypasses, collection corruption
   - Tests needed: **6-8**
   - Impact: **+3-4% coverage increase**

3. **Event Durability & Replay**
   - Module: `platform-event/`
   - Files: `EventDurabilityService`, `EventReplayService`, `EventCheckpointRepository`
   - Risk: Event loss, incomplete replays, checkpoint corruption
   - Tests needed: **5-8**
   - Impact: **+3-4% coverage increase**

4. **Governance & Policy Layer**
   - Module: `platform-api/governance/`
   - Files: `PolicyService`, `DataRetentionManager`
   - Risk: Retention violations, policy unenforcement, data exposure
   - Tests needed: **4-6**
   - Impact: **+2-3% coverage increase**

5. **Agent Registry & Execution**
   - Module: `agent-registry/`
   - Files: `DataCloudAgentRegistry`, `SchemaValidatorAgent`, `DataAnomalyDetectorAgent`, `DataSyncAgent`
   - Risk: Agent execution failures, schema validation bypasses, anomaly detection misses
   - Tests needed: **6-8**
   - Impact: **+3-4% coverage increase**

### Priority 2 Areas (Moderate Tests Needed)

These areas have partial coverage but critical gaps remain.

6. **Webhook & Event Publishing**
   - Module: `platform-api/application/webhook/`
   - Files: `WebhookService`, `WebhookDeliveryService`
   - Risk: Webhook failures, missed deliveries, retry exhaustion
   - Tests needed: **4-6**
   - Impact: **+2-3% coverage increase**

7. **Learning & Model Services**
   - Module: `platform-api/learning/`
   - Files: `ModelTrainingService`, `ModelEvaluationService`, `LearningProgressTracker`, `ModelVersionRepository`
   - Risk: Model training failures, evaluation errors, progress corruption
   - Tests needed: **4-6**
   - Impact: **+2-3% coverage increase**

8. **Event Streaming Plugins & Secret Management**
   - Module: `platform-event/spi/`
   - Files: `StreamingPlugin`, `StoragePlugin`, `SecretProvider`, `SecretResolver`
   - Risk: Plugin adapter failures, secret leaks, storage failures
   - Tests needed: **5-7**
   - Impact: **+2-3% coverage increase**

9. **Data-Cloud Query Caching**
   - Module: `data-cloud-cache/`
   - Files: `DataCloudQueryCacheService`
   - Risk: Cache coherency misses, stale data served, cache poisoning
   - Tests needed: **3-5**
   - Impact: **+2% coverage increase**

10. **Platform-Launcher Dependency Injection**

- Module: `platform-launcher/di/`
- Files: `DataCloudCoreModule`, `DataCloudStorageModule`, `DataCloudStreamingModule`, `DataCloudBrainModule`
- Risk: Dependency misconfigurations, partial initialization, runtime NPEs
- Tests needed: **4-6**
- Impact: **+2-3% coverage increase**

### Priority 3 Areas (Lower Priority, But Important)

These areas have some coverage but need expansion.

11. **Analytics Query Engine & Anomaly Detection**

- Module: `platform-analytics/`
- Files: `AnalyticsQueryEngine`, `ReportService`, `StatisticalAnomalyDetector`
- Risk: Query errors, report generation failures, anomaly misses
- Tests needed: **4-6**
- Impact: **+2% coverage increase**

12. **Entity Versioning & Diff**

- Module: `platform-entity/version/`
- Files: `EntityVersion`, `VersionRecord`, `VersionDiff`, `VersionMetadata`
- Risk: Version corruption, diff calculation errors, replay failures
- Tests needed: **3-5**
- Impact: **+2% coverage increase**

13. **Security & RBAC**

- Module: `platform-api/security/`
- Files: `RBACService`, `AuditLogService`, `SecurityController`
- Risk: Authorization bypasses, audit trail gaps, permission escalation
- Tests needed: **4-6**
- Impact: **+2% coverage increase**

14. **Workflow & Pipeline Checkpoint**

- Module: `launcher/http/handlers/`
- Files: `PipelineCheckpointHandler`, checkpoint serialization
- Risk: Checkpoint state corruption, replay failures, recovery failures
- Tests needed: **3-5**
- Impact: **+1-2% coverage increase**

15. **Voice & Speech Recognition Adapters**

- Module: `launcher/http/voice/`
- Files: `HttpWhisperSttAdapter`, `VoiceSttPort`, `VoiceTtsPort`
- Risk: Voice transcription errors, STT/TTS failures, retention policy breaches
- Tests needed: **3-5**
- Impact: **+1-2% coverage increase**

16. **Memory Tier Management**

- Module: `platform-api/memory/`
- Files: `MemoryService`, `MemoryTierRouter`, `SemanticMemoryRepository`, `EpisodicMemoryRepository`
- Risk: Memory tier misrouting, stale memory retrieval, tier transition failures
- Tests needed: **4-6**
- Impact: **+2% coverage increase**

17. **Brain State & Attention Management**

- Module: `platform-launcher/attention/`, `platform-api/brain/`
- Files: `AttentionManager`, `BrainStateManager`, `DefaultSalienceScorer`
- Risk: Attention ranking errors, salience calculation failures, brain state corruption
- Tests needed: **4-6**
- Impact: **+2% coverage increase**

18. **Client SDK & Autonomy Layer**

- Module: `platform-launcher/client/`, `platform-api/client/autonomy/`
- Files: `HttpDataCloudClient`, `AutonomyController`, `AutonomyPolicy`, `LearningLoop`
- Risk: Client-server contract breakage, autonomy override failures, feedback loop corruption
- Tests needed: **4-6**
- Impact: **+2% coverage increase**

---

## Recommended Test Areas (Sorted by Impact)

### **1. AIAssistService & LLM Integration Tests**

- **Why it matters:**
  - Core AI feature (AI Assist) used in user-facing product
  - Complex context window management and prompt template logic
  - Integration with external LLM providers
  - Context coherency critical for user experience
- **Estimated impact:** +4% coverage (5-7 tests)
- **Test focus:**
  - Happy path: valid prompt → successful LLM call → response
  - Edge cases: context window overflow, empty context, malformed prompts
  - Error handling: LLM provider down, timeout, rate limiting
  - Mocking LLMProvider to verify context construction

### **2. EntityService CRUD & Validation**

- **Why it matters:**
  - Central application service for entity operations
  - Used by all controllers (EntityCrudHandler, EntityEnrichmentHandler, etc.)
  - Schema validation must be enforced at service boundary
  - Collection isolation critical for multi-tenant safety
- **Estimated impact:** +4% coverage (6-8 tests)
- **Test focus:**
  - Create: validate schema, enforce tenant isolation, check uniqueness
  - Read: pagination, filtering, permission checks
  - Update: schema compatibility, change tracking, version bumps
  - Delete: cascading, soft vs hard delete, audit trail

### **3. EventDurabilityService & Checkpoint Management**

- **Why it matters:**
  - Ensures no event loss (durability SLA critical per ADR-012)
  - Checkpoint replay is central to event recovery
  - Plugin-based storage allows multiple backends (need contract tests)
  - Handles out-of-order events, retries, DLQ routing
- **Estimated impact:** +4% coverage (5-8 tests)
- **Test focus:**
  - Event persistence: write and read back correctly
  - Checkpoint creation: initial, intermediate, final checkpoints
  - Replay: deterministic order, deduplication, state reconstruction
  - Fault injection: storage failure, network timeout, partial writes

### **4. PolicyService & DataRetentionManager**

- **Why it matters:**
  - Governance is mandatory (compliance requirement)
  - Data retention violations have legal implications
  - Policy decisions affect downstream deletion and archival
  - Must prevent data exposure across retention tiers
- **Estimated impact:** +3% coverage (4-6 tests)
- **Test focus:**
  - Policy evaluation: decision path, rule composition
  - Retention check: age calculation, tier classification, expiry detection
  - Enforcement: block operations on expired data, schedule purges
  - Audit: policy decisions logged and queryable

### **5. DataCloudAgentRegistry & Agent Execution**

- **Why it matters:**
  - Schema validation prevents downstream data corruption
  - Anomaly detection catches data quality issues early
  - Data sync maintains cross-system consistency
  - Agents are DETERMINISTIC type (execution must be repeatable)
- **Estimated impact:** +4% coverage (6-8 tests)
- **Test focus:**
  - Agent registry: resolve agents, execute with context
  - SchemaValidatorAgent: validate against schema, report violations
  - DataAnomalyDetectorAgent: detect statistical anomalies, threshold application
  - DataSyncAgent: sync to external systems, handle partial failures, retry logic

### **6. WebhookService & Delivery**

- **Why it matters:**
  - User-facing feature (webhook integration)
  - Delivery SLA: must retry, must not lose events
  - Dead-letter queue critical for observability
  - Payload serialization must be reproducible
- **Estimated impact:** +3% coverage (4-6 tests)
- **Test focus:**
  - Webhook registration: CRUD, validation, filtering
  - Delivery: fire-and-forget, async execution, error capture
  - Retry: exponential backoff, max retries, DLQ escalation
  - Signature: HMAC generation, timestamping, replay protection

### **7. ModelTrainingService & Learning Pipeline**

- **Why it matters:**
  - ML-driven features (recommendations, anomaly detection)
  - Model versioning prevents train-serve skew
  - Progress tracking enables resumable training
  - Evaluation prevents bad models in production
- **Estimated impact:** +3% coverage (4-6 tests)
- **Test focus:**
  - Training: data preparation, model fitting, version creation
  - Evaluation: accuracy, precision, recall metrics
  - Versioning: promote models, rollback, A/B testing
  - Progress tracking: resume interrupted training, monitor ingestion rate

### **8. Event Streaming Plugin Contract Tests**

- **Why it matters:**
  - Multiple plugin implementations (Kafka, RabbitMQ, in-memory)
  - Contract tests ensure consistent behavior across adapters
  - Plugin failures cascade to all consumers
  - Secret management prevents credential leaks
- **Estimated impact:** +3% coverage (5-7 tests)
- **Test focus:**
  - StreamingPlugin: produce, consume, offset management
  - StoragePlugin: archive, restore, retention
  - SecretProvider: resolve from env, file, vault
  - Error scenarios: connection loss, auth failure, quota exceeded

### **9. DataCloudQueryCacheService**

- **Why it matters:**
  - Cache enables sub-second response times for analytics
  - Coherency critical (stale cache = incorrect analytics)
  - Cache poisoning can mislead dashboards
  - Invalidation strategy must be sound
- **Estimated impact:** +2% coverage (3-5 tests)
- **Test focus:**
  - Cache hit: verify response matches stored data
  - Cache miss: verify freshly computed and stored
  - Invalidation: on write, on TTL, manual
  - Coherency: multi-query consistency, stale write prevention

### **10. Platform-Launcher Dependency Injection**

- **Why it matters:**
  - DI modules compose entire application
  - Misconfiguration causes boot failures
  - Modules have complex dependencies (storage → event → DIcore)
  - Partial initialization causes NPE crashes at runtime
- **Estimated impact:** +3% coverage (4-6 tests)
- **Test focus:**
  - Module composition: all required bindings present
  - Dependency resolution: no circular deps, all satisfiable
  - Boot sequence: initialization order correct
  - Feature flags: conditional module loading (brain, streaming, etc.)

### **11. AnalyticsQueryEngine & Anomaly Detection**

- **Why it matters:**
  - Analytics powers dashboards and reports
  - Anomaly detection catches data quality issues
  - Query errors mislead analysis
  - Statistical tests must be validated against baseline
- **Estimated impact:** +2% coverage (4-6 tests)
- **Test focus:**
  - Query parsing: filter/sort/group parsing
  - Aggregation: COUNT/SUM/AVG correctness
  - Anomaly: threshold application, statistical significance
  - Report generation: template rendering, export formats

### **12. Entity Versioning & Diff Calculation**

- **Why it matters:**
  - Version history enables audit and rollback
  - Diffs show what changed (critical for audit trailing)
  - Version conflicts prevented
  - Merge logic resolves conflicts
- **Estimated impact:** +2% coverage (3-5 tests)
- **Test focus:**
  - Version creation: metadata, timestamp, author
  - Diff calculation: added/modified/deleted fields
  - Merge: 3-way merge, conflict detection
  - Rollback: restore previous version, tree walk

### **13. RBAC & Security Controls**

- **Why it matters:**
  - Authorization is mandatory for multi-tenant safety
  - Audit trail required for compliance
  - Privilege escalation vulnerability (high risk)
  - Tenant isolation depends on RBAC enforcement
- **Estimated impact:** +2% coverage (4-6 tests)
- **Test focus:**
  - RBAC: role assignment, permission check, deny logic
  - Audit: operation logging, decision tracking
  - Tenant isolation: cross-tenant access denied
  - Security: SQL injection prevention, parameter validation

### **14. Pipeline Checkpoint State & Serialization**

- **Why it matters:**
  - Checkpoints enable long-running pipeline resumption
  - State corruption = pipeline restart needed
  - Serialization must be deterministic
  - Partition awareness required for distributed checkpoints
- **Estimated impact:** +2% coverage (3-5 tests)
- **Test focus:**
  - Checkpoint save: state serialization, atomic write
  - Checkpoint load: correct state restoration
  - Distributed checkpoints: per-partition coordination
  - Error recovery: resume from last checkpoint

### **15. Voice STT/TTS & Transcript Retention**

- **Why it matters:**
  - Voice features are user-facing
  - Transcript retention policy prevents data exposure
  - STT provider integration critical for accuracy
  - TTS quality impacts user experience
- **Estimated impact:** +2% coverage (3-5 tests)
- **Test focus:**
  - STT: audio -> text using Whisper API
  - TTS: text -> audio using service provider
  - Retention: enforce policy, expire transcripts
  - Error handling: provider down, invalid audio, timeout

### **16. Memory Tier Management & Routing**

- **Why it matters:**
  - Memory tier determines retrieval speed (semantic vs episodic)
  - Misrouting causes incorrect memory use
  - Tier transitions must be consistent
  - Memory reconciliation prevents inconsistency
- **Estimated impact:** +2% coverage (4-6 tests)
- **Test focus:**
  - Memory storage: semantic and episodic repositories
  - Tier routing: policy-based assignment (recent to L1, old to L2)
  - Retrieval: query both tiers, rank results
  - Reconciliation: detect and fix tier inconsistencies

### **17. Attention Management & Salience Scoring**

- **Why it matters:**
  - Salience determines which memories are prioritized
  - Incorrect scoring causes suboptimal memory use
  - Attention state affects system behavior
  - Saliency must correlate with relevance
- **Estimated impact:** +2% coverage (4-6 tests)
- **Test focus:**
  - Salience scoring: recency, frequency, importance weights
  - Attention ranking: top-k memory selection
  - State transitions: focus shifts, attention reset
  - Metrics: verify salience correlates with query relevance

### **18. Client SDK & Autonomy Control**

- **Why it matters:**
  - SDK is public API (breaking changes costly)
  - Autonomy policy enforces action guardrails
  - Feedback loop drives learning
  - Contract tests prevent SDK/server divergence
- **Estimated impact:** +2% coverage (4-6 tests)
- **Test focus:**
  - SDK client: HTTP communication, request marshaling, timeout handling
  - Autonomy policy: check action, enforce constraints
  - Learning loop: feedback submission, model update
  - Feedback: positive/negative, feature importance

---

## Implementation Roadmap (By Sprint)

### **Sprint 1 (Week 1-2): Critical Path - High Impact, High Risk**

Focus: Stabilize core infrastructure before feature tests

1. **EventDurabilityService** (5 tests)
2. **EntityService CRUD** (6 tests)
3. **Platform-Launcher DI** (4 tests)
4. **AIAssistService** (5 tests)

Total: **20 tests**, ~5-7 points, **+10% coverage gain**

### **Sprint 2 (Week 3-4): Agent & Governance Layer**

Foundation: Agent execution and data governance

5. **DataCloudAgentRegistry** (6 tests)
6. **PolicyService & DataRetentionManager** (4 tests)
7. **Event Streaming Plugins** (5 tests)

Total: **15 tests**, ~4-6 points, **+5-7% coverage gain**

### **Sprint 3 (Week 5-6): Learning & Analytics**

Enablement: ML features and insights

8. **ModelTrainingService** (4 tests)
9. **AnalyticsQueryEngine** (4 tests)
10. **DataCloudQueryCache** (3 tests)

Total: **11 tests**, ~3-5 points, **+4-5% coverage gain**

### **Sprint 4 (Week 7-8): Application Services & Features**

Completeness: Customer-facing features

11. **WebhookService** (4 tests)
12. **Memory Tier Management** (4 tests)
13. **Attention & Salience** (4 tests)

Total: **12 tests**, ~3-5 points, **+4% coverage gain**

### **Sprint 5 (Week 9-10): Entity Domain & Capabilities**

Domain layer: Entity versioning, entities edge cases

14. **Entity Versioning** (3 tests)
15. **RBAC & Security** (4 tests)
16. **Client SDK & Autonomy** (4 tests)

Total: **11 tests**, ~3-4 points, **+3-4% coverage gain**

### **Sprint 6 (Week 11-12): Voice & Operations**

Integrations: Voice features and operational tests

17. **Voice STT/TTS** (3 tests)
18. **Pipeline Checkpoints** (3 tests)

Total: **6 tests**, ~2-3 points, **+2-3% coverage gain**

---

## Estimated Total Impact

| Phase         | Tests  | Lines Covered | New %    | Cumulative % |
| ------------- | ------ | ------------- | -------- | ------------ |
| Current       | 100    | ~800          | 15%      | 15%          |
| Sprint 1      | 20     | ~600          | +11%     | 26%          |
| Sprint 2      | 15     | ~500          | +9%      | 35%          |
| Sprint 3      | 11     | ~400          | +7%      | 42%          |
| Sprint 4      | 12     | ~450          | +8%      | 50%          |
| Sprint 5      | 11     | ~350          | +6%      | 56%          |
| Sprint 6      | 6      | ~200          | +4%      | 60%          |
| **Full Plan** | **75** | **~2,900**    | **+54%** | **69%**      |

**Note:** Additional integration tests, edge case coverage, and mutation testing could push to 80%+.

---

## Implementation Strategy

### Test Types Required

- **Unit Tests** (60%): Business logic, transformations, validation
- **Integration Tests** (25%): API contracts, plugin adapters, DI composition
- **Contract Tests** (10%): SDK/server, plugin interface compliance
- **E2E Tests** (5%): Critical user journeys (already have some)

### Mocking & Fixtures Strategy

- Mock external providers: LLMProvider, VoiceSTTPort, ModelTrainingService
- Use testcontainers for: PostgreSQL, Kafka, Redis
- Fixture builders for: Entity, Collection, Event, Agent
- Chaos engineering tests for: network partition, resource exhaustion

### Quality Gates

- **Code coverage threshold:** 80%+ by end of plan
- **Mutation score:** 70%+ (kill > 70% of injected mutations)
- **Linting:** Zero warnings in test code
- **Performance:** Unit tests < 100ms, integration < 500ms per test

---

## Summary

The data-cloud module has **280+ source files** with only **~100 test files**, resulting in **12-20% coverage**. The major gaps are in **platform-api** (1,400 LOC untested), **platform-event** (430 LOC), and **agent-registry** (120 LOC).

**This analysis recommends 75 high-value tests across 18 areas that will:**

- Increase coverage from **15%** to **60%+**
- Cover **critical infrastructure** (events, entities, agents, governance)
- Enable **safer refactoring** via regression test harness
- Provide **observability** for debugging production issues
- Ship in **6 parallel sprints** (12-15 weeks total)

The implementation prioritizes **high-risk, high-impact areas first** (event durability, entity services, agent execution, AI assist), ensuring core data-cloud reliability before expanding to feature-level coverage.
