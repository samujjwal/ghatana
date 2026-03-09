# Agent with Memory — Production Implementation Plan

> **Date**: February 18, 2026
> **Scope**: Multi-level memory + safe learning loops for the Ghatana agent framework
> **Baseline**: Full codebase analysis of `ghatana-new` — 88 modules, 30 proto contracts, 84+ agent-framework source files

---

## Table of Contents

1. [Current State Assessment](#1-current-state-assessment)
2. [Architecture Decisions](#2-architecture-decisions)
3. [Duplicate Resolution Plan](#3-duplicate-resolution-plan)
4. [Phase 0 — Foundations (Contracts + Data Model)](#4-phase-0--foundations)
5. [Phase 1 — Minimum Viable Memory](#5-phase-1--minimum-viable-memory)
6. [Phase 2 — Task-State Memory](#6-phase-2--task-state-memory)
7. [Phase 3 — Semantic + Procedural Memory](#7-phase-3--semantic--procedural-memory)
8. [Phase 4 — Consolidation + Retention](#8-phase-4--consolidation--retention)
9. [Phase 5 — Production Hardening](#9-phase-5--production-hardening)
10. [Module Dependency Map](#10-module-dependency-map)
11. [Existing Infrastructure Reuse Matrix](#11-existing-infrastructure-reuse-matrix)
12. [File Inventory Summary](#12-file-inventory-summary)

---

## 1. Current State Assessment

### What Exists (REUSE — Do Not Duplicate)

| Concern | Module | Key Types | Status |
|---|---|---|---|
| Agent contract | `agent-framework` | `TypedAgent<I,O>`, `AgentDescriptor`, `AgentConfig`, `AgentResult<O>` | Production-ready |
| Agent lifecycle | `agent-framework` | `AbstractTypedAgent` (state machine), `BaseAgent` (GAA: PERCEIVE→REASON→ACT→CAPTURE→REFLECT) | Production-ready |
| Memory SPI | `agent-framework` | `MemoryStore` (episodic/semantic/procedural/preference), `Episode`, `Fact`, `Policy`, `Preference` | Interface + in-memory impl |
| Memory proto | `contracts` | `memory_events.proto` — `MemoryCaptured`, `OutcomeObserved`, `FactUpserted`, `PolicyUpserted`, `PreferenceLearned`, `MemoryCompacted`, `MemoryDeleted`, `MemoryRelationship` | Contract defined |
| LLM gateway | `ai-integration` | `LLMGateway`, `DefaultLLMGateway`, `CompletionService`, `ToolAwareCompletionService` | Production-ready |
| Vector storage | `ai-integration` | `VectorStore`, `PgVectorStore`, `VectorStoreClient`, `VectorSearchResult` | Production-ready (pgvector) |
| Embeddings | `ai-integration` | `EmbeddingService`, `OpenAIEmbeddingService`, `EmbeddingResult` | Production-ready |
| Semantic cache | `ai-integration` | `SemanticCacheService` (cosine similarity, LRU, TTL) | Production-ready |
| Event log | `event-cloud` | `EventCloud` (append-only, subscribe, query, filter DSL), `InMemoryEventCloud` | Production-ready |
| Observability | `observability` | `MetricsCollector`, `TracingProvider`, `TraceStorage` (ClickHouse), 63 files | Production-ready |
| Resilience | `core` | `CircuitBreaker`, `RetryPolicy`, `DeadLetterQueue` | Production-ready |
| Governance | `core` + `governance` | `RetentionPolicy`, `DataClassification`, `PolicyEngine`, `GovernancePolicy` | Production-ready |
| Validation | `core` | `ValidationService`, `ValidationFactory`, 7 built-in validators | Production-ready |
| State store | `core` | `HybridStateStore<K,V>`, `InMemoryStateStore`, `SyncStrategy` | Production-ready |
| Database | `database` | `JdbcTemplate`, `JpaRepository`, `FlywayMigration`, `ConnectionPool`, `Cache`, Redis adapters | Production-ready |
| Session mgmt | `observability` | `SessionManager`, `RedisSessionManager`, `SessionState` | Production-ready |
| Plugin system | `plugin` | `Plugin`, `PluginRegistry`, `AIModelPlugin`, `StoragePlugin` | Production-ready |
| Evaluation | `ai-integration/evaluation` | `EvaluationRunner` (golden datasets, precision/recall/F1) | Functional |
| Domain memory | `domain` | `Memory` interface (key-value with TTL, checkpoint/rollback) | Interface only |
| Agent coordination | `agent-framework` | `OrchestrationStrategy`, `DelegationManager`, `ConversationManager` | Production-ready |
| Agent registry | `agent-framework` | `UnifiedAgentRegistry`, `InMemoryUnifiedAgentRegistry`, `AgentProviderRegistry` (SPI) | Production-ready |
| Agent security | `agent-framework` | `AgentAuthorizationService`, `SecretProvider` | Production-ready |

### What's Missing (BUILD)

| Gap | Severity | Phase |
|---|---|---|
| Canonical memory item schema (cross-tier interoperability) | **Critical** | 0 |
| Typed artifacts (Decision, ToolUse, Observation, Error, Lesson, Entity) | **Critical** | 0 |
| trace_id + span per memory read/write | **Critical** | 0 |
| Working memory (bounded, in-run-only) | **High** | 1 |
| Vector-based retrieval wired into MemoryStore | **High** | 1 |
| Structured context injection (reading strategy) | **High** | 1 |
| Task-state store (phases, checkpoints, blockers, dependencies) | **High** | 2 |
| Resume reconciliation | **High** | 2 |
| Enhanced semantic memory (provenance, confidence decay, version, links) | **High** | 3 |
| Enhanced procedural memory (versioned bundles, success-rate, context-aware selection) | **High** | 3 |
| Consolidation pipeline (episodic → semantic → procedural) | **High** | 4 |
| Retention as utility optimization (decay functions, scoring) | **Medium** | 4 |
| Hybrid retrieval (BM25 + dense vectors) | **Medium** | 4 |
| Time-aware reranking | **Medium** | 4 |
| Persistent MemoryStore (PostgreSQL + pgvector backed) | **High** | 1 |
| Learning evaluation gates | **High** | 5 |
| Skill promotion with rollback | **High** | 5 |
| Learning/eval service APIs (grade_trace, promote_skill, rollback) | **High** | 5 |
| Ingestion sanitization + PII policy tags | **Medium** | 5 |

---

## 2. Architecture Decisions

### AD-1: Module Structure

**Decision**: Create two new Gradle modules under `platform/java/`:
- `agent-memory` — Memory plane (stores, types, retrieval, consolidation, task-state)
- `agent-learning` — Learning plane (evaluation gates, skill promotion, rollback, policy updates)

**Rationale**: Separating memory and learning from `agent-framework` allows products to depend on memory without pulling in all agent types. The existing `agent-framework/memory` package becomes a **thin facade** over `agent-memory`.

```
platform/java/agent-memory/     ← NEW MODULE
platform/java/agent-learning/   ← NEW MODULE
platform/java/agent-framework/  ← UPDATED (depends on agent-memory, agent-learning)
```

### AD-2: Canonical Memory Item Schema

**Decision**: Introduce `MemoryItem` as a sealed interface hierarchy at the contract level. All memory tiers share a common envelope (id, type, content, time, provenance, embeddings, validity, links) while each tier adds typed fields.

### AD-3: MemoryStore Evolution

**Decision**: Evolve the existing `MemoryStore` interface into `MemoryPlane` (a richer SPI) while keeping `MemoryStore` as a backward-compatible facade. This preserves all existing agent-framework consumers.

### AD-4: Wiring Strategy

**Decision**: Wire existing infrastructure (VectorStore, EmbeddingService, EventCloud, SemanticCacheService, SessionManager) into the memory plane via constructor injection. No new infrastructure — reuse existing pgvector, Redis, ClickHouse, and EventCloud implementations.

### AD-5: Observability Contract

**Decision**: Every memory operation (read/write/consolidate) emits OpenTelemetry spans via the existing `TracingProvider`. Memory-specific metrics flow through `MetricsCollector`. No new observability infrastructure — extend existing.

---

## 3. Duplicate Resolution Plan

| Duplicate | Resolution | Action |
|---|---|---|
| `Agent` interface in `ai-integration`, `domain`, `agent-framework` (legacy) | `TypedAgent<I,O>` in `agent-framework` is canonical | Mark `ai-integration.Agent` and `domain.Agent` as `@Deprecated(forRemoval=true)`. Adapters already exist (`LegacyAgentAdapter`, `BaseAgentAdapter`). Products migrate to `TypedAgent`. |
| `Memory` in `domain` vs `MemoryStore` in `agent-framework` | `MemoryStore` (agent-framework) evolves into `MemoryPlane` | `domain.Memory` (key-value) becomes `WorkingMemory` tier within `MemoryPlane`. Mark `domain.Memory` as `@Deprecated(forRemoval=true)`. |
| `ABTestingEvaluationService` duplicated in `ai-integration` | Keep the one in `ai-integration/src` (richer) | Delete the duplicate or merge functionality. |
| `RedisCacheConfig` in `database` × 2 | Keep `core.cache.redis.RedisCacheConfig` | Remove `platform.database.cache.RedisCacheConfig`. |
| `GovernancePolicy` in `agent-framework/memory` vs `governance` module | `governance.PolicyEngine` is the canonical policy system | `agent-framework.GovernancePolicy` delegates to `governance.PolicyEngine`. |

---

## 4. Phase 0 — Foundations (Contracts + Data Model)

### 4.1 New Proto Contracts

#### FILE: `platform/contracts/src/main/proto/ghatana/contracts/agent/v1/memory_item.proto` — **CREATE**

Canonical memory item schema shared across all tiers.

```
message MemoryItemProto {
  string id = 1;
  MemoryItemType type = 2;               // EPISODE, FACT, PROCEDURE, TASK_STATE, WORKING, PREFERENCE
  google.protobuf.Timestamp created_at = 3;
  google.protobuf.Timestamp updated_at = 4;
  google.protobuf.Timestamp expires_at = 5;
  ProvenanceProto provenance = 6;        // source, confidence_source, trace_id, agent_id
  repeated float embedding = 7;
  ValidityProto validity = 8;            // confidence, last_verified, decay_rate, status
  repeated MemoryLinkProto links = 9;    // supports, contradicts, derived_from, supersedes
  map<string, string> labels = 10;
  map<string, string> annotations = 11;
  string tenant_id = 12;
  string sphere_id = 13;                 // privacy boundary (context-policy)
  DataClassificationProto classification = 14;
  oneof content {
    EpisodeContentProto episode = 20;
    FactContentProto fact = 21;
    ProcedureContentProto procedure = 22;
    TaskStateContentProto task_state = 23;
    WorkingContentProto working = 24;
    PreferenceContentProto preference = 25;
    ArtifactContentProto artifact = 26;  // generic typed artifact
  }
}

message ProvenanceProto {
  string source = 1;                     // "tool:grep", "user:input", "consolidation:v2", "inference:gpt-4"
  ConfidenceSource confidence_source = 2;
  string trace_id = 3;
  string agent_id = 4;
  string session_id = 5;
  string parent_item_id = 6;            // item this was derived from
}

message ValidityProto {
  double confidence = 1;                 // [0.0, 1.0]
  google.protobuf.Timestamp last_verified = 2;
  double decay_rate = 3;                 // per-day confidence decay
  ValidityStatus status = 4;            // ACTIVE, STALE, DEPRECATED, ARCHIVED
}

message MemoryLinkProto {
  string target_item_id = 1;
  LinkType link_type = 2;               // SUPPORTS, CONTRADICTS, DERIVED_FROM, SUPERSEDES, RELATED
  double strength = 3;                   // [0.0, 1.0]
  string description = 4;
}

// Typed artifact content for Decision, ToolUse, Observation, Error, Lesson, Entity
message ArtifactContentProto {
  ArtifactType artifact_type = 1;
  string summary = 2;
  google.protobuf.Struct structured_data = 3;
  repeated string tags = 4;
}

// Enums: MemoryItemType, ValidityStatus, LinkType, ArtifactType
```

#### FILE: `platform/contracts/src/main/proto/ghatana/contracts/agent/v1/memory_service.proto` — **CREATE**

Memory service APIs (integration checklist from the plan).

```
service MemoryService {
  rpc WriteArtifact(WriteArtifactRequest) returns (WriteArtifactResponse);
  rpc ReadMemory(ReadMemoryRequest) returns (ReadMemoryResponse);
  rpc SearchMemory(SearchMemoryRequest) returns (SearchMemoryResponse);
  rpc Checkpoint(CheckpointRequest) returns (CheckpointResponse);
  rpc Consolidate(ConsolidateRequest) returns (ConsolidateResponse);
  rpc ApplyRetention(RetentionRequest) returns (RetentionResponse);
  rpc GetMemoryStats(MemoryStatsRequest) returns (MemoryStatsResponse);
}
```

#### FILE: `platform/contracts/src/main/proto/ghatana/contracts/agent/v1/task_state.proto` — **CREATE**

Task-state memory contract for multi-session workflows.

```
message TaskStateProto {
  string task_id = 1;
  string tenant_id = 2;
  string agent_id = 3;
  string name = 4;
  string description = 5;
  TaskPhase current_phase = 6;
  repeated TaskPhaseProto phases = 7;
  repeated CheckpointProto checkpoints = 8;
  repeated BlockerProto blockers = 9;
  repeated InvariantProto invariants = 10;
  DoneCriteriaProto done_criteria = 11;
  repeated TaskDependencyProto dependencies = 12;
  EnvironmentSnapshotProto environment_snapshot = 13;
  TaskLifecycleStatus status = 14;
  google.protobuf.Timestamp created_at = 15;
  google.protobuf.Timestamp updated_at = 16;
  google.protobuf.Timestamp last_active_at = 17;
  map<string, string> metadata = 18;
}

service TaskStateService {
  rpc CreateTask(CreateTaskRequest) returns (TaskStateProto);
  rpc GetTask(GetTaskRequest) returns (TaskStateProto);
  rpc UpdatePhase(UpdatePhaseRequest) returns (TaskStateProto);
  rpc AddCheckpoint(AddCheckpointRequest) returns (CheckpointProto);
  rpc ReportBlocker(ReportBlockerRequest) returns (BlockerProto);
  rpc ResolveBlocker(ResolveBlockerRequest) returns (BlockerProto);
  rpc ReconcileOnResume(ReconcileRequest) returns (ReconcileResponse);
  rpc ArchiveTask(ArchiveTaskRequest) returns (ArchiveTaskResponse);
}
```

#### FILE: `platform/contracts/src/main/proto/ghatana/contracts/learning/v1/learning_service.proto` — **CREATE**

Learning/evaluation service APIs.

```
service LearningService {
  rpc GradeTrace(GradeTraceRequest) returns (GradeTraceResponse);
  rpc PromoteSkill(PromoteSkillRequest) returns (PromoteSkillResponse);
  rpc Rollback(RollbackRequest) returns (RollbackResponse);
  rpc EvaluateUpdate(EvaluateUpdateRequest) returns (EvaluateUpdateResponse);
  rpc ListSkillVersions(ListSkillVersionsRequest) returns (ListSkillVersionsResponse);
}
```

### 4.2 New Java Module: `agent-memory`

#### FILE: `platform/java/agent-memory/build.gradle.kts` — **CREATE**

```kotlin
plugins { id("java-library") }
group = "com.ghatana.platform.agent"
version = "1.0.0-SNAPSHOT"

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:event-cloud"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:database"))
    api(project(":platform:contracts"))

    implementation("io.activej:activej-promise")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    compileOnly("org.jetbrains:annotations")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
}
```

#### Package: `com.ghatana.agent.memory.model` — Canonical Memory Item

| File | Type | Description |
|---|---|---|
| `MemoryItem.java` | Sealed interface | Cross-tier canonical envelope: `getId()`, `getType()`, `getCreatedAt()`, `getUpdatedAt()`, `getProvenance()`, `getEmbedding()`, `getValidity()`, `getLinks()`, `getLabels()`, `getTenantId()`, `getSphereId()`, `getClassification()` |
| `MemoryItemType.java` | Enum | `EPISODE`, `FACT`, `PROCEDURE`, `TASK_STATE`, `WORKING`, `PREFERENCE`, `ARTIFACT` |
| `Provenance.java` | @Value @Builder | `source`, `confidenceSource`, `traceId`, `agentId`, `sessionId`, `parentItemId` |
| `Validity.java` | @Value @Builder | `confidence`, `lastVerified`, `decayRate`, `status` (ACTIVE/STALE/DEPRECATED/ARCHIVED) |
| `MemoryLink.java` | @Value @Builder | `targetItemId`, `linkType` (SUPPORTS/CONTRADICTS/DERIVED_FROM/SUPERSEDES/RELATED), `strength`, `description` |
| `ValidityStatus.java` | Enum | `ACTIVE`, `STALE`, `DEPRECATED`, `ARCHIVED` |
| `LinkType.java` | Enum | `SUPPORTS`, `CONTRADICTS`, `DERIVED_FROM`, `SUPERSEDES`, `RELATED` |

#### Package: `com.ghatana.agent.memory.model.artifact` — Typed Artifacts

| File | Type | Description |
|---|---|---|
| `TypedArtifact.java` | Sealed interface extends MemoryItem | Base for all typed artifacts |
| `ArtifactType.java` | Enum | `DECISION`, `TOOL_USE`, `OBSERVATION`, `ERROR`, `LESSON`, `ENTITY`, `PLAN`, `HYPOTHESIS` |
| `Decision.java` | Record implements TypedArtifact | `rationale`, `alternatives`, `chosenOption`, `confidence`, `context` |
| `ToolUse.java` | Record implements TypedArtifact | `toolName`, `input`, `output`, `durationMs`, `success`, `errorMessage` |
| `Observation.java` | Record implements TypedArtifact | `content`, `source`, `significance`, `relatedEntities` |
| `Error.java` | Record implements TypedArtifact | `errorType`, `message`, `stackTrace`, `recoveryAction`, `category` |
| `Lesson.java` | Record implements TypedArtifact | `condition`, `insight`, `applicability`, `derivedFromEpisodes`, `verificationStatus` |
| `Entity.java` | Record implements TypedArtifact | `entityType`, `name`, `attributes`, `externalIds`, `relationships` |

#### Package: `com.ghatana.agent.memory.model.episode` — Enhanced Episodic Memory

| File | Type | Description |
|---|---|---|
| `EnhancedEpisode.java` | @Value @Builder implements MemoryItem | Extends current `Episode` with provenance, embedding, validity, links, typed input/output, tool executions, cost metrics, redaction level. **Backward-compatible** with existing `Episode`. |
| `EpisodeBuilder.java` | Static factory | `fromLegacyEpisode(Episode)` migration helper |

#### Package: `com.ghatana.agent.memory.model.fact` — Enhanced Semantic Memory

| File | Type | Description |
|---|---|---|
| `EnhancedFact.java` | @Value @Builder implements MemoryItem | Extends current `Fact` with: provenance, confidence decay, TTL, `lastVerified`, version, links (supports/contradicts/derived_from). |
| `FactVersion.java` | @Value @Builder | Version history entry: `version`, `content`, `changedAt`, `changedBy`, `changeReason` |

#### Package: `com.ghatana.agent.memory.model.procedure` — Enhanced Procedural Memory

| File | Type | Description |
|---|---|---|
| `EnhancedProcedure.java` | @Value @Builder implements MemoryItem | Extends current `Policy` with: versioned steps, success rate, context prerequisites, environment constraints, parameterized templates |
| `ProcedureStep.java` | @Value @Builder | `ordinal`, `description`, `toolName`, `expectedOutcome`, `fallbackStep` |
| `ProcedureVersion.java` | @Value @Builder | `version`, `steps`, `successRate`, `usageCount`, `promotedAt`, `evaluationResult` |

#### Package: `com.ghatana.agent.memory.model.taskstate` — Task-State Memory

| File | Type | Description |
|---|---|---|
| `TaskState.java` | @Value @Builder implements MemoryItem | `taskId`, `name`, `currentPhase`, `phases`, `checkpoints`, `blockers`, `invariants`, `doneCriteria`, `dependencies`, `environmentSnapshot`, `status`, `metadata` |
| `TaskPhase.java` | @Value @Builder | `id`, `name`, `description`, `status`, `ordinal`, `estimatedEffort`, `actualEffort`, `startedAt`, `completedAt` |
| `TaskCheckpoint.java` | @Value @Builder | `id`, `phaseId`, `snapshot`, `createdAt`, `description`, `isRestorable` |
| `TaskBlocker.java` | @Value @Builder | `id`, `description`, `severity`, `reportedAt`, `resolvedAt`, `resolution` |
| `TaskInvariant.java` | @Value @Builder | `id`, `description`, `checkExpression`, `lastCheckedAt`, `satisfied` |
| `DoneCriteria.java` | @Value @Builder | `criteria` (list of `Criterion`), `allRequired` |
| `TaskDependency.java` | @Value @Builder | `dependsOnTaskId`, `type` (BLOCKS/INFORMS), `status` |
| `EnvironmentSnapshot.java` | @Value @Builder | `capturedAt`, `properties` (Map), `checksums` (Map) |
| `TaskLifecycleStatus.java` | Enum | `CREATED`, `PLANNING`, `IN_PROGRESS`, `BLOCKED`, `PAUSED`, `COMPLETED`, `FAILED`, `ARCHIVED` |

#### Package: `com.ghatana.agent.memory.model.working` — Working Memory

| File | Type | Description |
|---|---|---|
| `WorkingMemory.java` | Interface | Bounded in-run state: `put(key, value)`, `get(key)`, `remove(key)`, `getAll()`, `size()`, `capacity()`, `clear()`, `snapshot()`. Eviction callback. |
| `BoundedWorkingMemory.java` | Class | LRU-bounded implementation with configurable max entries and max bytes. Thread-safe via `ConcurrentHashMap` + `ReadWriteLock`. Eviction events emitted to `MetricsCollector`. |
| `WorkingMemoryConfig.java` | @Value @Builder | `maxEntries`, `maxBytes`, `evictionPolicy` (LRU/LFU/PRIORITY) |

#### Package: `com.ghatana.agent.memory.store` — Memory Plane SPI

| File | Type | Description |
|---|---|---|
| `MemoryPlane.java` | Interface | **Evolution of `MemoryStore`**: all existing methods + `writeArtifact(TypedArtifact)`, `readItems(MemoryQuery)→List<MemoryItem>`, `searchSemantic(query, filters, k, timeRange)→List<ScoredMemoryItem>`, `getWorkingMemory()→WorkingMemory`, `getTaskStateStore()→TaskStateStore`, `checkpoint(taskId)`, `consolidate(ConsolidationRequest)`, `applyRetention(RetentionConfig)`, `getStats()→MemoryPlaneStats` |
| `ScoredMemoryItem.java` | @Value | `item` (MemoryItem), `score`, `retrievalMetadata` (Map — "why retrieved") |
| `MemoryQuery.java` | @Value @Builder | `itemTypes`, `tenantId`, `agentId`, `sphereId`, `timeRange`, `tags`, `minConfidence`, `validityStatuses`, `linkFilters`, `textQuery`, `limit`, `offset` |
| `MemoryPlaneStats.java` | @Value @Builder | Counts per tier, storage bytes per tier, index health, last consolidation timestamp |

#### Package: `com.ghatana.agent.memory.store.taskstate` — Task-State Store

| File | Type | Description |
|---|---|---|
| `TaskStateStore.java` | Interface | `createTask(TaskState)`, `getTask(taskId)`, `updatePhase(taskId, phaseUpdate)`, `addCheckpoint(taskId, checkpoint)`, `reportBlocker(taskId, blocker)`, `resolveBlocker(taskId, blockerId, resolution)`, `reconcileOnResume(taskId)→ReconcileResult`, `archiveTask(taskId)`, `listActiveTasks(agentId)`, `garbageCollect(inactiveSince)` |
| `ReconcileResult.java` | @Value @Builder | `conflicts` (List<Conflict>), `autoResolved`, `requiresHumanReview`, `recommendations` |
| `Conflict.java` | @Value @Builder | `field`, `storedValue`, `currentValue`, `severity`, `autoResolvable` |

#### Package: `com.ghatana.agent.memory.retrieval` — Retrieval Pipeline

| File | Type | Description |
|---|---|---|
| `RetrievalPipeline.java` | Interface | `retrieve(RetrievalRequest)→RetrievalResult` |
| `RetrievalRequest.java` | @Value @Builder | `query`, `itemTypes`, `k`, `timeRange`, `filters`, `rerankerConfig`, `includeSimilarityScores`, `includeExplanation` |
| `RetrievalResult.java` | @Value @Builder | `items` (List<ScoredMemoryItem>), `totalCandidates`, `retrievalTimeMs`, `strategyUsed`, `explanation` |
| `HybridRetriever.java` | Class | Combines dense vector retrieval (via `VectorStore`) + sparse lexical retrieval (via PostgreSQL full-text search). Configurable weight: `hybridScore = α * denseScore + (1-α) * sparseScore`. |
| `TimeAwareReranker.java` | Class | Reranks retrieved items by: `finalScore = relevanceScore * recencyWeight(age, halfLife) * importanceWeight`. Configurable half-life per memory tier. |
| `ContextInjector.java` | Interface | `formatForInjection(List<ScoredMemoryItem>, InjectionConfig)→String` |
| `StructuredContextInjector.java` | Class | Formats retrieved items into structured context blocks with provenance display, recency/validity indicators, conflict markers (supports/contradicts labels), and grouped by tier. |
| `InjectionConfig.java` | @Value @Builder | `maxTokens`, `groupByTier`, `includeProvenance`, `includeConfidence`, `includeConflictMarkers`, `format` (MARKDOWN/XML/JSON) |

#### Package: `com.ghatana.agent.memory.persistence` — Persistent Implementations

| File | Type | Description |
|---|---|---|
| `PersistentMemoryPlane.java` | Class implements MemoryPlane | **Production implementation** wiring together: `PgVectorStore` for embedding storage/search, `JdbcTemplate` for metadata CRUD, `EventCloud` for append-only archival, `EmbeddingService` for auto-embedding, `SemanticCacheService` for retrieval caching, `RedisSessionManager` for session state. All operations emit spans via `TracingProvider`. |
| `MemoryItemRepository.java` | Interface | JDBC-based persistence: `save(MemoryItem)`, `findById(id)`, `findByQuery(MemoryQuery)→Page<MemoryItem>`, `delete(id)`, `softDelete(id)` |
| `JdbcMemoryItemRepository.java` | Class | PostgreSQL implementation using `JdbcTemplate`. JSONB for content, `tsvector` for full-text search, separate `memory_embeddings` table linked by item_id. |
| `InMemoryMemoryPlane.java` | Class implements MemoryPlane | **Testing implementation** extending existing `EventLogMemoryStore` with new tier support. |
| `TaskStateRepository.java` | Interface | Task-state JDBC persistence |
| `JdbcTaskStateRepository.java` | Class | PostgreSQL implementation with JSONB for phases/checkpoints/blockers |

#### Package: `com.ghatana.agent.memory.observability` — Memory Observability

| File | Type | Description |
|---|---|---|
| `MemoryMetrics.java` | Class | Metric constants + recording methods: `MEMORY_WRITE_COUNT`, `MEMORY_READ_COUNT`, `MEMORY_SEARCH_LATENCY`, `MEMORY_CONSOLIDATION_COUNT`, `MEMORY_TIER_SIZE`, `MEMORY_RETRIEVAL_SCORE`, `MEMORY_CACHE_HIT_RATE` |
| `TracedMemoryPlane.java` | Class implements MemoryPlane (decorator) | Wraps any `MemoryPlane` with OpenTelemetry spans for every operation. Attributes: `memory.tier`, `memory.operation`, `memory.item_count`, `memory.latency_ms`, `memory.trace_id`. Also records `MemoryMetrics`. |
| `RetrievalExplanation.java` | @Value @Builder | `retrievalStrategy`, `candidatesScanned`, `scores` (Map<itemId, Map<factor, score>>), `filtersApplied`, `rerankerInputs` |

#### Database Migrations

| File | Description |
|---|---|
| `platform/java/agent-memory/src/main/resources/db/migration/V001__create_memory_items.sql` | `memory_items` table: id, type, tenant_id, sphere_id, agent_id, content (JSONB), provenance (JSONB), validity (JSONB), links (JSONB), labels (JSONB), classification, created_at, updated_at, expires_at, deleted_at. Indexes: type+tenant, agent+type, created_at, GIN on content, GIN on labels. `tsvector` column for full-text search with GIN index. |
| `platform/java/agent-memory/src/main/resources/db/migration/V002__create_memory_embeddings.sql` | `memory_embeddings` table: item_id (FK), embedding (vector(1536)), model_version, created_at. IVFFlat index on embedding for cosine distance. |
| `platform/java/agent-memory/src/main/resources/db/migration/V003__create_task_states.sql` | `task_states` table: task_id (PK), tenant_id, agent_id, name, status, current_phase, phases (JSONB), checkpoints (JSONB), blockers (JSONB), invariants (JSONB), done_criteria (JSONB), dependencies (JSONB), environment_snapshot (JSONB), created_at, updated_at, last_active_at. Indexes: tenant+status, agent+status, last_active_at. |
| `platform/java/agent-memory/src/main/resources/db/migration/V004__create_memory_links.sql` | `memory_links` table: source_id, target_id, link_type, strength, description, created_at. Bidirectional indexes. |

### 4.3 New Java Module: `agent-learning`

#### FILE: `platform/java/agent-learning/build.gradle.kts` — **CREATE**

```kotlin
plugins { id("java-library") }
group = "com.ghatana.platform.agent"
version = "1.0.0-SNAPSHOT"

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:agent-memory"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:contracts"))

    implementation("io.activej:activej-promise")
    implementation("com.fasterxml.jackson.core:jackson-databind")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
}
```

#### Package: `com.ghatana.agent.learning` — Learning Plane Core

| File | Type | Description |
|---|---|---|
| `LearningPlane.java` | Interface | `gradeTrace(traceId)→TraceGrade`, `promoteSkill(procedureId, version)→PromotionResult`, `rollback(procedureId, toVersion)→RollbackResult`, `evaluateUpdate(UpdateCandidate)→EvaluationGateResult`, `listPendingUpdates()`, `getSkillHistory(procedureId)` |
| `TraceGrade.java` | @Value @Builder | `traceId`, `overallScore`, `dimensionScores` (Map: accuracy, helpfulness, safety, efficiency), `episodes`, `recommendations` |
| `PromotionResult.java` | @Value @Builder | `procedureId`, `fromVersion`, `toVersion`, `promotedAt`, `evaluationPassed`, `regressionResults` |
| `RollbackResult.java` | @Value @Builder | `procedureId`, `fromVersion`, `toVersion`, `rolledBackAt`, `reason` |
| `UpdateCandidate.java` | @Value @Builder | `type` (FACT/PROCEDURE/RETRIEVAL_POLICY), `content`, `derivedFrom`, `proposedBy` |
| `EvaluationGateResult.java` | @Value @Builder | `passed`, `score`, `regressionResults`, `safetyChecks`, `recommendations`, `blockers` |

#### Package: `com.ghatana.agent.learning.evaluation` — Evaluation Gates

| File | Type | Description |
|---|---|---|
| `EvaluationGate.java` | Interface | `evaluate(UpdateCandidate, EvaluationContext)→EvaluationGateResult`. Pluggable evaluation strategy. |
| `RegressionEvaluationGate.java` | Class | Replays historical traces against the candidate update, compares outcomes. Uses existing `EvaluationRunner` for precision/recall/F1. |
| `SafetyEvaluationGate.java` | Class | Checks candidate against safety policies: PII exposure, hallucination patterns, tool misuse. Uses `governance.PolicyEngine`. |
| `CompositeEvaluationGate.java` | Class | Chains multiple gates (ALL must pass). Configurable ordering: safety → regression → A/B. |
| `EvaluationContext.java` | @Value @Builder | `replayTraces`, `goldenDataset`, `safetyPolicies`, `minScoreThreshold`, `regressionTolerance` |

#### Package: `com.ghatana.agent.learning.consolidation` — Consolidation Pipeline

| File | Type | Description |
|---|---|---|
| `ConsolidationPipeline.java` | Interface | `consolidate(ConsolidationRequest)→ConsolidationResult` |
| `ConsolidationRequest.java` | @Value @Builder | `sourceTier` (EPISODIC), `targetTier` (SEMANTIC/PROCEDURAL), `timeRange`, `minEpisodeCount`, `patternDetectionStrategy` |
| `ConsolidationResult.java` | @Value @Builder | `itemsProcessed`, `patternsDetected`, `factsExtracted`, `proceduresProposed`, `conflictsDetected`, `consolidationTimeMs` |
| `EpisodicToSemanticConsolidator.java` | Class | Extracts facts from episodic clusters: groups similar episodes → uses LLM to extract generalizable facts → verifies against existing semantic KB → handles contradictions (truth maintenance) → attaches provenance. |
| `EpisodicToProcedural Consolidator.java` | Class | Clusters successful episodes with similar tool sequences → extracts reusable procedures → parameterizes templates → computes success rates → proposes as `EnhancedProcedure` candidates. |
| `ConflictResolver.java` | Interface | `resolve(existingFact, newFact)→ConflictResolution` |
| `EntrenchmentConflictResolver.java` | Class | Classical belief revision: maintains entrenchment/priority over facts. Higher-provenance facts override lower ones. Logs all resolutions for audit. |
| `ConsolidationScheduler.java` | Class | Triggers consolidation on configurable schedule or when episode count exceeds threshold. Uses existing `EventCloud.subscribe()` for event-driven triggers. |
| `ConflictResolution.java` | @Value @Builder | `action` (KEEP_EXISTING/REPLACE/MERGE/FLAG_FOR_REVIEW), `rationale`, `auditTrail` |

#### Package: `com.ghatana.agent.learning.retention` — Retention & Decay

| File | Type | Description |
|---|---|---|
| `RetentionManager.java` | Interface | `applyRetention(RetentionConfig)→RetentionResult`, `computeUtilityScores(itemType)`, `getRetentionPolicy(itemType)→RetentionPolicyConfig` |
| `UtilityBasedRetentionManager.java` | Class | Computes utility score per item: `utility = recency(age, halfLife) * relevance(retrievalCount, lastRetrievedAt) * importance(explicitFlags, derivedImpact)`. Items below threshold → archive/delete. |
| `RetentionConfig.java` | @Value @Builder | Per-tier config: `maxItems`, `maxStorageBytes`, `maxAge`, `minUtilityScore`, `archiveBeforeDelete`, `immuneLabels` (items with these labels never expire) |
| `DecayFunction.java` | Interface | `compute(age, params)→double`. Pluggable. |
| `ExponentialDecay.java` | Class | `e^(-λt)` with configurable half-life |
| `PowerLawDecay.java` | Class | `t^(-α)` — matches human forgetting curve research |
| `StepDecay.java` | Class | TTL-based: 1.0 until expiry, then 0.0 |
| `RetentionResult.java` | @Value @Builder | `itemsEvaluated`, `itemsArchived`, `itemsDeleted`, `itemsRetained`, `storageFreedBytes` |

### 4.4 Updates to Existing Modules

#### FILE: `settings.gradle.kts` — **UPDATE**

Add two new module includes:

```kotlin
// After ":platform:java:agent-framework"
include(":platform:java:agent-memory")
include(":platform:java:agent-learning")
```

#### FILE: `platform/java/agent-framework/build.gradle.kts` — **UPDATE**

Add dependencies:

```kotlin
api(project(":platform:java:agent-memory"))
api(project(":platform:java:agent-learning"))
```

#### FILE: `platform/java/agent-framework/.../memory/MemoryStore.java` — **UPDATE**

Add default method for forward migration:

```java
/**
 * @deprecated Migrate to {@link MemoryPlane} for enhanced memory capabilities.
 * This interface is preserved for backward compatibility.
 */
default MemoryPlane asMemoryPlane() {
    throw new UnsupportedOperationException("Upgrade to MemoryPlane implementation");
}
```

#### FILE: `platform/java/agent-framework/.../api/AgentContext.java` — **UPDATE**

Add `MemoryPlane` accessor alongside existing `getMemoryStore()`:

```java
/**
 * Returns the enhanced memory plane (superset of MemoryStore).
 * Falls back to MemoryStore wrapped in a compatibility adapter if MemoryPlane not available.
 */
default MemoryPlane getMemoryPlane() {
    return getMemoryStore().asMemoryPlane();
}

/** Returns the working memory for this execution context. */
default WorkingMemory getWorkingMemory() {
    return getMemoryPlane().getWorkingMemory();
}

/** Returns the task-state store for multi-session workflows. */
default TaskStateStore getTaskStateStore() {
    return getMemoryPlane().getTaskStateStore();
}
```

#### FILE: `platform/java/agent-framework/.../runtime/BaseAgent.java` — **UPDATE**

Enhance CAPTURE and REFLECT phases:

```java
// CAPTURE: Write enhanced episode with provenance + embedding + trace_id
// (existing Episode creation + new EnhancedEpisode creation with provenance from span context)

// REFLECT: Default implementation now calls consolidation check
// (if episode count exceeds threshold, trigger async consolidation)
```

---

## 5. Phase 1 — Minimum Viable Memory (Working + Episodic + Retrieval)

### 5.1 Working Memory Implementation

Files already defined in Phase 0 (`BoundedWorkingMemory`, `WorkingMemoryConfig`).

**Integration point**: Wire `BoundedWorkingMemory` into `DefaultAgentContext.builder()`:

#### FILE: `platform/java/agent-framework/.../api/DefaultAgentContext.java` — **UPDATE**

```java
// Add working memory field, initialize from builder with default config
private final WorkingMemory workingMemory;

// In builder: default WorkingMemoryConfig(maxEntries=1000, maxBytes=10MB, LRU)
```

### 5.2 Enhanced Episodic Store with Vector Retrieval

#### FILE: `platform/java/agent-memory/.../persistence/PersistentMemoryPlane.java` — **CREATE** (core Phase 1 deliverable)

Key implementation:

```java
// On storeEpisode():
//   1. Generate embedding via EmbeddingService
//   2. Write MemoryItem to PostgreSQL (JdbcMemoryItemRepository)
//   3. Write embedding to memory_embeddings (via PgVectorStore)
//   4. Append event to EventCloud (archival)
//   5. Emit tracing span + metrics

// On searchEpisodes():
//   1. Generate query embedding via EmbeddingService
//   2. Hybrid retrieval: vector search (PgVectorStore) + text search (PostgreSQL tsvector)
//   3. Time-aware reranking
//   4. Return ScoredMemoryItem list with explanation
//   5. Cache in SemanticCacheService

// Reuses: PgVectorStore, EmbeddingService, EventCloud, SemanticCacheService, TracingProvider
```

### 5.3 Structured Context Injection

Files already defined in Phase 0 (`StructuredContextInjector`, `InjectionConfig`).

**Integration point**: `BaseAgent.PERCEIVE` phase loads relevant memories via retrieval pipeline and injects them as structured context.

#### FILE: `platform/java/agent-framework/.../runtime/BaseAgent.java` — **UPDATE**

```java
// Enhanced PERCEIVE phase:
//   1. Load working memory snapshot
//   2. If MemoryPlane available:
//      a. Build RetrievalRequest from current input + task context
//      b. Call MemoryPlane.searchSemantic() via HybridRetriever
//      c. Format via StructuredContextInjector
//      d. Prepend to working context
//   3. Existing perceive logic
```

### 5.4 MemoryStore ↔ MemoryPlane Compatibility Adapter

#### FILE: `platform/java/agent-memory/.../store/MemoryStoreAdapter.java` — **CREATE**

```java
/**
 * Wraps a MemoryPlane as a MemoryStore for backward compatibility.
 * Allows existing agents to use MemoryPlane transparently.
 */
public class MemoryStoreAdapter implements MemoryStore {
    private final MemoryPlane memoryPlane;
    // Delegates all MemoryStore methods to MemoryPlane equivalents
    // Converts Episode ↔ EnhancedEpisode, Fact ↔ EnhancedFact, etc.
}
```

#### FILE: `platform/java/agent-memory/.../store/LegacyMemoryPlaneAdapter.java` — **CREATE**

```java
/**
 * Wraps a legacy MemoryStore as a MemoryPlane.
 * Working memory: in-memory BoundedWorkingMemory (not persisted)
 * Task state: throws UnsupportedOperationException
 * Search: delegates to MemoryStore.searchEpisodes/searchFacts (string-based)
 */
public class LegacyMemoryPlaneAdapter implements MemoryPlane { ... }
```

### Phase 1 Acceptance Criteria

- [ ] Pause and resume a conversation → episodic replay recovers last plan + key decisions
- [ ] `searchEpisodes("JWT error")` returns relevant episodes ranked by vector similarity + recency
- [ ] Working memory bounded to configured limit; eviction metrics visible in Grafana
- [ ] All memory operations emit OpenTelemetry spans visible in trace UI
- [ ] Existing agents (using `MemoryStore`) work unchanged via adapter

---

## 6. Phase 2 — Task-State Memory (Multi-Session Reliability)

### 6.1 Task-State Store Implementation

Files already defined in Phase 0 (`TaskStateStore`, `JdbcTaskStateRepository`, `TaskState`, related types).

#### FILE: `platform/java/agent-memory/.../store/taskstate/JdbcTaskStateStore.java` — **CREATE**

```java
/**
 * PostgreSQL-backed task state store.
 * - JSONB storage for phases, checkpoints, blockers
 * - Pessimistic locking on update to prevent concurrent modification
 * - Automatic last_active_at refresh
 * - Garbage collection of tasks inactive > configurable threshold
 */
public class JdbcTaskStateStore implements TaskStateStore {
    // Dependencies: JdbcTemplate, TracingProvider, MetricsCollector

    // reconcileOnResume():
    //   1. Load stored environment snapshot
    //   2. Capture current environment
    //   3. Compare field-by-field
    //   4. For each conflict:
    //      a. If auto-resolvable (timestamps, counters): update stored
    //      b. If manual review needed: flag in ReconcileResult
    //   5. Return ReconcileResult with conflict list + recommendations
}
```

### 6.2 Resume Flow in BaseAgent

#### FILE: `platform/java/agent-framework/.../runtime/BaseAgent.java` — **UPDATE**

```java
// Enhanced PERCEIVE phase (addition to Phase 1):
//   1. Check TaskStateStore for active tasks for this agent
//   2. If resuming:
//      a. Call reconcileOnResume()
//      b. If conflicts: inject conflict report into working context
//      c. Load last checkpoint snapshot into working memory
//   3. Continue with existing perceive logic
```

### 6.3 Task-State Lifecycle Hooks

#### FILE: `platform/java/agent-memory/.../store/taskstate/TaskStateLifecycleManager.java` — **CREATE**

```java
/**
 * Manages task lifecycle: creation, phase transitions, GC, archival.
 * - Scheduled GC: archives tasks inactive > 30 days (configurable)
 * - Event emission: TaskStateCreated, TaskPhaseUpdated, TaskCompleted → EventCloud
 * - Invariant checking: validates invariants on each phase transition
 */
```

### Phase 2 Acceptance Criteria

- [ ] Agent runs a 5-10 session workflow, reports accurate status ("67% complete, blocker resolved")
- [ ] Resume after 7 days: reconciliation detects environment changes and flags conflicts
- [ ] Orphaned tasks archived after configurable inactivity period
- [ ] Task dependency graph prevents dependent tasks from proceeding until blockers resolve

---

## 7. Phase 3 — Semantic + Procedural Memory (Knowledge + Skills)

### 7.1 Enhanced Semantic Store

Files already defined in Phase 0 (`EnhancedFact`, `FactVersion`).

#### FILE: `platform/java/agent-memory/.../store/semantic/SemanticMemoryManager.java` — **CREATE**

```java
/**
 * Manages semantic memory lifecycle:
 * - Fact versioning: every update creates a new version
 * - Confidence decay: confidence = initial * e^(-decayRate * ageInDays)
 * - Verification scheduling: facts below confidence threshold queued for re-verification
 * - Conflict detection: on new fact, check existing facts for contradictions via embedding similarity + predicate matching
 * - Link management: auto-create SUPPORTS/CONTRADICTS links
 */
public class SemanticMemoryManager {
    // Dependencies: MemoryPlane, EmbeddingService, VectorStore, ConflictResolver
}
```

### 7.2 Enhanced Procedural Store

Files already defined in Phase 0 (`EnhancedProcedure`, `ProcedureStep`, `ProcedureVersion`).

#### FILE: `platform/java/agent-memory/.../store/procedural/ProceduralMemoryManager.java` — **CREATE**

```java
/**
 * Manages procedural memory:
 * - Version tracking: each successful refinement increments version
 * - Success rate: track success/failure per execution, rolling 30-day window
 * - Context-aware selection: score = similarity(currentContext, procedure.prerequisites) *
 *                            envMatch(currentEnv, procedure.envConstraints) *
 *                            successRate * recencyWeight
 * - Parameterized procedures: template variables resolved at execution time
 */
public class ProceduralMemoryManager {
    // Reuses: existing Policy fields + new EnhancedProcedure fields
}
```

#### FILE: `platform/java/agent-memory/.../store/procedural/ProcedureSelector.java` — **CREATE**

```java
/**
 * Context-aware procedure selection:
 *   candidateScore = α * semanticSimilarity(task, procedure.situation)
 *                  + β * environmentMatch(currentEnv, procedure.envConstraints)
 *                  + γ * procedure.successRate
 *                  + δ * recencyWeight(procedure.lastUsedAt)
 *
 * Weights α, β, γ, δ configurable. Returns top-k candidates.
 */
```

### Phase 3 Acceptance Criteria

- [ ] After solving JWT issues 5 times, agent promotes a "debug_jwt_expiry" procedure
- [ ] On 6th similar issue, agent retrieves and applies procedure in < 2 minutes
- [ ] Contradicting facts auto-detected; lower-provenance fact deprecated, CONTRADICTS link created
- [ ] Fact confidence visibly decays; stale facts trigger re-verification prompt

---

## 8. Phase 4 — Consolidation + Retention Policy

### 8.1 Consolidation Pipeline

Files already defined in Phase 0 (`ConsolidationPipeline`, `EpisodicToSemanticConsolidator`, `EpisodicToProceduralConsolidator`, `ConflictResolver`, `ConsolidationScheduler`).

#### FILE: `platform/java/agent-learning/.../consolidation/LLMFactExtractor.java` — **CREATE**

```java
/**
 * Uses LLMGateway to extract generalizable facts from episode clusters.
 * Prompt strategy:
 *   1. Cluster similar episodes (embedding cosine > 0.85)
 *   2. Present cluster to LLM with: "Extract stable facts from these experiences"
 *   3. Parse structured output into Fact candidates
 *   4. Validate against existing semantic KB
 *   5. Score confidence based on cluster size + consistency
 *
 * Reuses: LLMGateway, PromptTemplateManager, EmbeddingService
 */
```

#### FILE: `platform/java/agent-learning/.../consolidation/ProcedureInducer.java` — **CREATE**

```java
/**
 * Induces reusable procedures from successful episode sequences.
 * Strategy:
 *   1. Group episodes by task type + success outcome
 *   2. Extract common tool-call sequences (longest common subsequence)
 *   3. Parameterize variable parts (file paths, IDs, etc.)
 *   4. Compute success rate across the cluster
 *   5. Propose as EnhancedProcedure candidate
 *
 * Reuses: LLMGateway (for parameterization), EmbeddingService (for clustering)
 */
```

### 8.2 Retention as Utility Optimization

Files already defined in Phase 0 (`UtilityBasedRetentionManager`, `DecayFunction`, `ExponentialDecay`, `PowerLawDecay`, `StepDecay`).

#### FILE: `platform/java/agent-learning/.../retention/RetentionScheduler.java` — **CREATE**

```java
/**
 * Scheduled retention enforcement:
 *   1. Compute utility scores for all items in each tier
 *   2. Identify items below threshold
 *   3. Archive (to EventCloud) before deletion
 *   4. Delete from online stores
 *   5. Emit retention metrics + audit events
 *   6. Verify: retrieval latency stays within SLA, task success stable
 *
 * Schedule: configurable (default: daily for episodic, weekly for semantic)
 * Reuses: EventCloud (archive), MetricsCollector, AuditService
 */
```

### 8.3 Hybrid Retrieval

#### FILE: `platform/java/agent-memory/.../retrieval/BM25Retriever.java` — **CREATE**

```java
/**
 * Sparse lexical retrieval using PostgreSQL tsvector/tsquery.
 * Computes BM25-like scores using ts_rank_cd with normalization.
 * Reuses: JdbcTemplate, existing tsvector column from V001 migration
 */
```

#### FILE: `platform/java/agent-memory/.../retrieval/HybridRetriever.java` — **UPDATE** (already in Phase 0 skeleton)

Wire in `BM25Retriever` alongside `VectorStore` dense retrieval.

### Phase 4 Acceptance Criteria

- [ ] Memory doesn't bloat unbounded; storage within configured budgets
- [ ] Consolidation extracts ≥ 3 reusable facts from 20-episode cluster
- [ ] Retrieval latency P99 < 100ms with 100K items
- [ ] Hybrid retrieval improves recall by ≥ 15% vs pure vector search (measured on test dataset)

---

## 9. Phase 5 — Production Hardening (Governance + Eval Gates + Rollout)

### 9.1 Evaluation Gates Integration

Files already defined in Phase 0 (`EvaluationGate`, `RegressionEvaluationGate`, `SafetyEvaluationGate`, `CompositeEvaluationGate`).

#### FILE: `platform/java/agent-learning/.../evaluation/SkillPromotionWorkflow.java` — **CREATE**

```java
/**
 * End-to-end workflow for promoting a procedure/fact update:
 *   1. Extract candidate from consolidation pipeline
 *   2. Run SafetyEvaluationGate (PII, hallucination, tool misuse checks)
 *   3. Run RegressionEvaluationGate (replay traces, compare outcomes)
 *   4. If A/B testing enabled: run ABTestingService (from ai-integration)
 *   5. If all gates pass: promote to production tier
 *   6. If any gate fails: reject + log rationale + notify
 *   7. Emit audit events for entire flow
 *
 * Rollback: if promoted skill degrades metrics within observation window,
 *           auto-rollback to previous version + alert
 *
 * Reuses: EvaluationRunner, ABTestingService, PolicyEngine, AuditService
 */
```

### 9.2 Security Posture

#### FILE: `platform/java/agent-memory/.../security/MemorySecurityManager.java` — **CREATE**

```java
/**
 * Memory-specific security:
 *   - Ingestion sanitization: validate + redact incoming memory items
 *   - PII detection: scan content before storage, apply DataClassification
 *   - Policy tags: PII/SECRET/PUBLIC/INTERNAL labels in MemoryItem.labels
 *   - Sphere enforcement: memory items respect context-policy Sphere boundaries
 *   - Encryption at rest: delegate to EncryptionService for SECRET-classified items
 *
 * Reuses: PiiRedactor (core), EncryptionService (security), SphereService (context-policy),
 *         DataClassification (core/governance)
 */
```

#### FILE: `platform/java/agent-memory/.../security/MemoryRedactionFilter.java` — **CREATE**

```java
/**
 * Applies redaction rules from RedactionConfigProto (learning/v1/redaction.proto).
 * Supports: REMOVE, MASK, REPLACE, HASH, TOKENIZE, SUMMARIZE.
 * Applied on write (before persistence) and on read (based on reader's access level).
 *
 * Reuses: existing RedactionRuleProto, PiiRedactor
 */
```

### 9.3 Versioned Skill Management

#### FILE: `platform/java/agent-learning/.../skills/SkillVersionManager.java` — **CREATE**

```java
/**
 * Git-like version management for procedural memory:
 *   - Each procedure has immutable version history
 *   - Promote: version N → version N+1 (only after evaluation gate)
 *   - Rollback: O(1) revert to any previous version
 *   - Diff: compare two versions (step additions/removals/modifications)
 *   - Schema migration: handle changes to ProcedureStep format across versions
 */
```

### Phase 5 Acceptance Criteria

- [ ] No update (weightless or otherwise) promoted without passing regression + safety gates
- [ ] Rollback is one API call; previous version active within 1 second
- [ ] PII in memory items auto-detected and redacted before storage
- [ ] All promotion/rollback events recorded in audit trail (AuditService)
- [ ] Skill version history browsable; diffs reviewable

---

## 10. Module Dependency Map

```
core (zero deps)
├── database (→ core)
├── event-cloud (→ core)
├── observability (→ core, runtime, config)
├── governance (→ core, contracts)
├── security (→ core)
├── context-policy (→ core, observability, database)
│
├── ai-integration (→ core, observability)
│   ├── VectorStore / PgVectorStore
│   ├── EmbeddingService
│   ├── LLMGateway
│   ├── SemanticCacheService
│   ├── EvaluationRunner
│   └── ABTestingService
│
├── agent-memory [NEW] (→ core, observability, event-cloud, ai-integration, database, contracts)
│   ├── Model: MemoryItem, TypedArtifact, EnhancedEpisode/Fact/Procedure, TaskState, WorkingMemory
│   ├── Store: MemoryPlane, TaskStateStore, PersistentMemoryPlane
│   ├── Retrieval: HybridRetriever, TimeAwareReranker, StructuredContextInjector
│   ├── Persistence: JdbcMemoryItemRepository, JdbcTaskStateRepository
│   ├── Observability: TracedMemoryPlane, MemoryMetrics
│   └── Security: MemorySecurityManager, MemoryRedactionFilter
│
├── agent-learning [NEW] (→ core, agent-memory, observability, ai-integration, contracts)
│   ├── Core: LearningPlane, TraceGrade, EvaluationGateResult
│   ├── Evaluation: EvaluationGate, RegressionEvaluationGate, SafetyEvaluationGate
│   ├── Consolidation: ConsolidationPipeline, EpisodicToSemanticConsolidator, ConflictResolver
│   ├── Retention: RetentionManager, UtilityBasedRetentionManager, DecayFunctions
│   └── Skills: SkillVersionManager, SkillPromotionWorkflow
│
└── agent-framework (→ core, observability, ai-integration, agent-memory [NEW], agent-learning [NEW])
    ├── TypedAgent, AgentDescriptor, AbstractTypedAgent, BaseAgent
    ├── AgentContext (updated: getMemoryPlane(), getWorkingMemory(), getTaskStateStore())
    ├── MemoryStore (preserved, delegates to MemoryPlane via adapter)
    ├── Legacy adapters preserved for backward compatibility
    └── All agent types: Deterministic, Probabilistic, Hybrid, Adaptive, Composite, Reactive, LLM
```

---

## 11. Existing Infrastructure Reuse Matrix

| New Component | Reuses From | How |
|---|---|---|
| Vector retrieval in memory | `ai-integration` → `PgVectorStore`, `VectorStore` | Injected into `PersistentMemoryPlane`; query via existing pgvector SQL |
| Embedding generation | `ai-integration` → `EmbeddingService`, `OpenAIEmbeddingService` | Called on every memory write to auto-compute embeddings |
| Semantic caching | `ai-integration` → `SemanticCacheService` | Cache retrieval results to reduce repeated searches |
| Append-only archival | `event-cloud` → `EventCloud`, `InMemoryEventCloud` | Memory items archived to EventCloud on retention/deletion |
| Distributed tracing | `observability` → `TracingProvider`, `TracingManager` | `TracedMemoryPlane` decorator emits spans for every operation |
| Metrics | `observability` → `MetricsCollector` | `MemoryMetrics` registers counters/histograms per tier |
| Session state | `observability` → `RedisSessionManager` | Session-scoped memory uses existing session infrastructure |
| PostgreSQL persistence | `database` → `JdbcTemplate`, `ConnectionPool`, `FlywayMigration` | `JdbcMemoryItemRepository` uses existing JDBC infrastructure |
| Redis caching | `database` → `AsyncRedisCache`, `RedisCacheConfig` | Working memory overflow + retrieval cache |
| Governance | `core`, `governance` → `RetentionPolicy`, `PolicyEngine`, `DataClassification` | Retention manager enforces governance-defined policies |
| PII redaction | `core` → `PiiRedactor`; `security` → `EncryptionService` | Memory ingestion sanitization |
| Evaluation | `ai-integration/evaluation` → `EvaluationRunner` | Regression gate replays traces and measures precision/recall/F1 |
| A/B testing | `ai-integration` → `ABTestingService` | A/B evaluation gate for skill promotion |
| LLM for consolidation | `ai-integration` → `LLMGateway`, `PromptTemplateManager` | Fact extraction + procedure parameterization |
| Context-policy | `context-policy` → `SphereService`, `SphereAccess` | Memory items respect sphere privacy boundaries |
| Audit trail | `audit` → `AuditService` | All promotion/rollback/deletion events audited |
| Resilience | `core` → `CircuitBreaker`, `RetryPolicy` | Memory service calls protected by circuit breakers |
| Plugin system | `plugin` → `StoragePlugin` | Future: pluggable memory backends via StoragePlugin SPI |
| Validation | `core` → `ValidationService`, `ValidationFactory` | Memory item input validation |
| Feature flags | `core` → `FeatureService` | Feature-gate new memory tiers during rollout |
| Conflict resolution | `governance` → `PolicyEngine` | Policy-based conflict resolution for contradicting facts |
| Proto contracts | `contracts` → `memory_events.proto`, `memory_item.proto` [NEW] | Wire format for memory events across services |
| Training pipeline | `ai-integration/training` → `TrainingPipelineOrchestrator` | Future: weight-updates via managed training pipeline |
| Cost tracking | `ai-integration` → `CostTrackingService` | Track LLM costs for consolidation + retrieval |

---

## 12. File Inventory Summary

### New Files by Module

#### `platform/contracts/` — 3 new proto files

| File | Phase | Type |
|---|---|---|
| `src/main/proto/ghatana/contracts/agent/v1/memory_item.proto` | 0 | CREATE |
| `src/main/proto/ghatana/contracts/agent/v1/memory_service.proto` | 0 | CREATE |
| `src/main/proto/ghatana/contracts/agent/v1/task_state.proto` | 0 | CREATE |
| `src/main/proto/ghatana/contracts/learning/v1/learning_service.proto` | 0 | CREATE |

#### `platform/java/agent-memory/` — 39 new files

| File (under `src/main/java/com/ghatana/agent/memory/`) | Phase | Type |
|---|---|---|
| `build.gradle.kts` | 0 | CREATE |
| `model/MemoryItem.java` | 0 | CREATE |
| `model/MemoryItemType.java` | 0 | CREATE |
| `model/Provenance.java` | 0 | CREATE |
| `model/Validity.java` | 0 | CREATE |
| `model/MemoryLink.java` | 0 | CREATE |
| `model/ValidityStatus.java` | 0 | CREATE |
| `model/LinkType.java` | 0 | CREATE |
| `model/artifact/TypedArtifact.java` | 0 | CREATE |
| `model/artifact/ArtifactType.java` | 0 | CREATE |
| `model/artifact/Decision.java` | 0 | CREATE |
| `model/artifact/ToolUse.java` | 0 | CREATE |
| `model/artifact/Observation.java` | 0 | CREATE |
| `model/artifact/Error.java` | 0 | CREATE |
| `model/artifact/Lesson.java` | 0 | CREATE |
| `model/artifact/Entity.java` | 0 | CREATE |
| `model/episode/EnhancedEpisode.java` | 0 | CREATE |
| `model/episode/EpisodeBuilder.java` | 0 | CREATE |
| `model/fact/EnhancedFact.java` | 0 | CREATE |
| `model/fact/FactVersion.java` | 0 | CREATE |
| `model/procedure/EnhancedProcedure.java` | 0 | CREATE |
| `model/procedure/ProcedureStep.java` | 0 | CREATE |
| `model/procedure/ProcedureVersion.java` | 0 | CREATE |
| `model/taskstate/TaskState.java` | 0 | CREATE |
| `model/taskstate/TaskPhase.java` | 0 | CREATE |
| `model/taskstate/TaskCheckpoint.java` | 0 | CREATE |
| `model/taskstate/TaskBlocker.java` | 0 | CREATE |
| `model/taskstate/TaskInvariant.java` | 0 | CREATE |
| `model/taskstate/DoneCriteria.java` | 0 | CREATE |
| `model/taskstate/TaskDependency.java` | 0 | CREATE |
| `model/taskstate/EnvironmentSnapshot.java` | 0 | CREATE |
| `model/taskstate/TaskLifecycleStatus.java` | 0 | CREATE |
| `model/working/WorkingMemory.java` | 0 | CREATE |
| `model/working/BoundedWorkingMemory.java` | 1 | CREATE |
| `model/working/WorkingMemoryConfig.java` | 0 | CREATE |
| `store/MemoryPlane.java` | 0 | CREATE |
| `store/ScoredMemoryItem.java` | 0 | CREATE |
| `store/MemoryQuery.java` | 0 | CREATE |
| `store/MemoryPlaneStats.java` | 0 | CREATE |
| `store/MemoryStoreAdapter.java` | 1 | CREATE |
| `store/LegacyMemoryPlaneAdapter.java` | 1 | CREATE |
| `store/taskstate/TaskStateStore.java` | 0 | CREATE |
| `store/taskstate/ReconcileResult.java` | 2 | CREATE |
| `store/taskstate/Conflict.java` | 2 | CREATE |
| `store/taskstate/JdbcTaskStateStore.java` | 2 | CREATE |
| `store/taskstate/TaskStateLifecycleManager.java` | 2 | CREATE |
| `store/semantic/SemanticMemoryManager.java` | 3 | CREATE |
| `store/procedural/ProceduralMemoryManager.java` | 3 | CREATE |
| `store/procedural/ProcedureSelector.java` | 3 | CREATE |
| `retrieval/RetrievalPipeline.java` | 0 | CREATE |
| `retrieval/RetrievalRequest.java` | 0 | CREATE |
| `retrieval/RetrievalResult.java` | 0 | CREATE |
| `retrieval/HybridRetriever.java` | 1+4 | CREATE |
| `retrieval/TimeAwareReranker.java` | 1 | CREATE |
| `retrieval/ContextInjector.java` | 1 | CREATE |
| `retrieval/StructuredContextInjector.java` | 1 | CREATE |
| `retrieval/InjectionConfig.java` | 1 | CREATE |
| `retrieval/BM25Retriever.java` | 4 | CREATE |
| `persistence/PersistentMemoryPlane.java` | 1 | CREATE |
| `persistence/MemoryItemRepository.java` | 1 | CREATE |
| `persistence/JdbcMemoryItemRepository.java` | 1 | CREATE |
| `persistence/InMemoryMemoryPlane.java` | 1 | CREATE |
| `persistence/TaskStateRepository.java` | 2 | CREATE |
| `persistence/JdbcTaskStateRepository.java` | 2 | CREATE |
| `observability/MemoryMetrics.java` | 0 | CREATE |
| `observability/TracedMemoryPlane.java` | 0 | CREATE |
| `observability/RetrievalExplanation.java` | 1 | CREATE |
| `security/MemorySecurityManager.java` | 5 | CREATE |
| `security/MemoryRedactionFilter.java` | 5 | CREATE |

SQL Migrations (under `src/main/resources/db/migration/`):

| File | Phase | Type |
|---|---|---|
| `V001__create_memory_items.sql` | 1 | CREATE |
| `V002__create_memory_embeddings.sql` | 1 | CREATE |
| `V003__create_task_states.sql` | 2 | CREATE |
| `V004__create_memory_links.sql` | 3 | CREATE |

#### `platform/java/agent-learning/` — 22 new files

| File (under `src/main/java/com/ghatana/agent/learning/`) | Phase | Type |
|---|---|---|
| `build.gradle.kts` | 0 | CREATE |
| `LearningPlane.java` | 0 | CREATE |
| `TraceGrade.java` | 0 | CREATE |
| `PromotionResult.java` | 0 | CREATE |
| `RollbackResult.java` | 0 | CREATE |
| `UpdateCandidate.java` | 0 | CREATE |
| `EvaluationGateResult.java` | 0 | CREATE |
| `evaluation/EvaluationGate.java` | 5 | CREATE |
| `evaluation/RegressionEvaluationGate.java` | 5 | CREATE |
| `evaluation/SafetyEvaluationGate.java` | 5 | CREATE |
| `evaluation/CompositeEvaluationGate.java` | 5 | CREATE |
| `evaluation/EvaluationContext.java` | 5 | CREATE |
| `evaluation/SkillPromotionWorkflow.java` | 5 | CREATE |
| `consolidation/ConsolidationPipeline.java` | 4 | CREATE |
| `consolidation/ConsolidationRequest.java` | 4 | CREATE |
| `consolidation/ConsolidationResult.java` | 4 | CREATE |
| `consolidation/EpisodicToSemanticConsolidator.java` | 4 | CREATE |
| `consolidation/EpisodicToProceduralConsolidator.java` | 4 | CREATE |
| `consolidation/ConflictResolver.java` | 4 | CREATE |
| `consolidation/EntrenchmentConflictResolver.java` | 4 | CREATE |
| `consolidation/ConsolidationScheduler.java` | 4 | CREATE |
| `consolidation/ConflictResolution.java` | 4 | CREATE |
| `consolidation/LLMFactExtractor.java` | 4 | CREATE |
| `consolidation/ProcedureInducer.java` | 4 | CREATE |
| `retention/RetentionManager.java` | 4 | CREATE |
| `retention/UtilityBasedRetentionManager.java` | 4 | CREATE |
| `retention/RetentionConfig.java` | 4 | CREATE |
| `retention/DecayFunction.java` | 4 | CREATE |
| `retention/ExponentialDecay.java` | 4 | CREATE |
| `retention/PowerLawDecay.java` | 4 | CREATE |
| `retention/StepDecay.java` | 4 | CREATE |
| `retention/RetentionResult.java` | 4 | CREATE |
| `retention/RetentionScheduler.java` | 4 | CREATE |
| `skills/SkillVersionManager.java` | 5 | CREATE |

#### Updated Existing Files — 7 files

| File | Phase | Change |
|---|---|---|
| `settings.gradle.kts` | 0 | Add `include(":platform:java:agent-memory")`, `include(":platform:java:agent-learning")` |
| `platform/java/agent-framework/build.gradle.kts` | 0 | Add `api(project(":platform:java:agent-memory"))`, `api(project(":platform:java:agent-learning"))` |
| `agent-framework/.../memory/MemoryStore.java` | 0 | Add `default MemoryPlane asMemoryPlane()` method |
| `agent-framework/.../api/AgentContext.java` | 1 | Add `getMemoryPlane()`, `getWorkingMemory()`, `getTaskStateStore()` default methods |
| `agent-framework/.../api/DefaultAgentContext.java` | 1 | Add `WorkingMemory` field + builder support |
| `agent-framework/.../runtime/BaseAgent.java` | 1-2 | Enhanced PERCEIVE (retrieval + injection + resume), CAPTURE (enhanced episode + embedding), REFLECT (consolidation check trigger) |
| `platform/contracts/build.gradle.kts` | 0 | Ensure new proto files included in protobuf compilation |

#### Test Files (representative — parallel to source structure)

| Module | Estimated Test Files | Phase |
|---|---|---|
| `agent-memory` | ~35 tests (model, store, retrieval, persistence, observability) | 0-5 |
| `agent-learning` | ~20 tests (evaluation gates, consolidation, retention, skills) | 0-5 |
| `agent-framework` (updates) | ~5 updated test files (BaseAgent, AgentContext, MemoryStore compat) | 1-2 |

---

## Grand Total

| Category | Count |
|---|---|
| **New proto files** | 4 |
| **New Java source files** | ~70 |
| **New SQL migration files** | 4 |
| **New build files** | 2 |
| **Updated existing files** | 7 |
| **New test files** | ~55 |
| **Total new/changed files** | **~142** |
| **Existing files reused (not modified)** | **~200+** (VectorStore, PgVectorStore, EmbeddingService, EventCloud, TracingProvider, MetricsCollector, SemanticCacheService, JdbcTemplate, PolicyEngine, etc.) |

---

## Appendix A: Phase Execution Timeline

| Phase | Key Deliverables | Estimated LOC | Prereqs |
|---|---|---|---|
| **Phase 0** | Contracts + data model + module scaffolding | ~3,000 | None |
| **Phase 1** | Working memory + episodic retrieval + persistent store + context injection | ~5,000 | Phase 0 |
| **Phase 2** | Task-state store + resume reconciliation + lifecycle mgmt | ~3,000 | Phase 1 |
| **Phase 3** | Semantic manager + procedural manager + context-aware selection | ~3,000 | Phase 1 |
| **Phase 4** | Consolidation pipeline + retention policies + hybrid retrieval | ~4,000 | Phase 3 |
| **Phase 5** | Evaluation gates + skill promotion + security hardening | ~3,000 | Phase 4 |
| **Total** | | **~21,000** | |

## Appendix B: Key Design Invariants

1. **Every memory write emits a trace span** — non-negotiable for debuggability
2. **Every memory item has provenance** — source + trace_id + agent_id always populated
3. **No update promoted without evaluation gate** — even "weightless" updates (fact changes, procedure additions) gate through `CompositeEvaluationGate`
4. **Backward compatibility preserved** — existing `MemoryStore` consumers work unchanged via adapters
5. **Multi-tenant isolation enforced** — all queries scoped by tenant_id; `SphereService` boundaries respected
6. **Consolidation is async and idempotent** — never blocks agent execution; safe to re-run
7. **Retention is utility-based, not time-only** — items with high retrieval frequency survive longer
8. **All memory operations are Promise-based** — consistent with the ActiveJ async model used across platform

## Appendix C: Quality Gates for Production Grade

| Gate | Threshold | Tool |
|---|---|---|
| Unit test coverage | ≥ 85% line coverage | JaCoCo |
| Integration tests | All memory tiers + retrieval pass E2E | TestContainers (PostgreSQL + pgvector + Redis) |
| Performance | Retrieval P99 < 100ms at 100K items, Write P99 < 50ms | JMH benchmarks |
| Memory footprint | Working memory bounded; GC pause < 50ms | JMH + JFR |
| Thread safety | All stores pass concurrent access tests | JUnit5 + multiple threads |
| Backward compat | Existing agent-framework tests pass unchanged | Existing test suite |
| Security | PII detected and redacted in all stored content | Integration tests + PiiRedactor |
| Observability | All operations visible in trace UI | OpenTelemetry + ClickHouse |
| API documentation | All public interfaces have Javadoc | Checkstyle rule |
| Proto backward compat | No breaking changes to existing proto fields | `buf breaking` or manual review |
