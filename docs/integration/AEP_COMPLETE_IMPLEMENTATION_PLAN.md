# AEP — Complete Integration & UI/UX Implementation Plan

> **Version**: 1.0.0
> **Date**: 2026-03-11
> **Scope**: Full integration of AEP with the Agentic Framework (BaseAgent, AgentTurnPipeline,
> AgentDispatcher, MemoryPlane, ConsolidationPipeline, AgentRegistry) plus a complete UI/UX
> overhaul of the AEP frontend from a single page to a full-featured application.
> **Audience**: Engineers implementing the plan. Every section is self-contained and
> actionable. No external tools or frameworks are introduced — only what already exists in
> the codebase is used.

---

## Table of Contents

**Part I — Backend Integration**

1. [Executive Summary](#1-executive-summary)
2. [Current State Inventory](#2-current-state-inventory)
3. [Critical Bugs to Fix First](#3-critical-bugs-to-fix-first)
4. [Phase 1 — Wire AIAgentOrchestrationManager to Agent Framework](#phase-1--wire-aiagentorchestrationmanager-to-agent-framework)
5. [Phase 2 — Agent Dispatch & Catalog Integration](#phase-2--agent-dispatch--catalog-integration)
6. [Phase 3 — Memory Plane Integration](#phase-3--memory-plane-integration)
7. [Phase 4 — Learning Loop (ConsolidationPipeline)](#phase-4--learning-loop-consolidationpipeline)
8. [Phase 5 — AgentRegistry & Multi-Tenancy](#phase-5--agentregistry--multi-tenancy)
9. [Phase 6 — Environment-Driven Configuration](#phase-6--environment-driven-configuration)
10. [Phase 7 — New REST & SSE Endpoints](#phase-7--new-rest--sse-endpoints)
11. [Phase 8 — gRPC Agent Service Hardening](#phase-8--grpc-agent-service-hardening)

**Part II — Frontend / UI/UX**

12. [UI Architecture Overview](#12-ui-architecture-overview)
13. [Page 1 — Pipeline Builder (Redesign)](#page-1--pipeline-builder-redesign)
14. [Page 2 — Agent Registry](#page-2--agent-registry)
15. [Page 3 — Agent Detail & Memory Browser](#page-3--agent-detail--memory-browser)
16. [Page 4 — Monitoring Dashboard](#page-4--monitoring-dashboard)
17. [Page 5 — Pattern Studio](#page-5--pattern-studio)
18. [Page 6 — HITL (Human-in-the-Loop) Review Queue](#page-6--hitl-human-in-the-loop-review-queue)
19. [Page 7 — Learning & Skill Promotion](#page-7--learning--skill-promotion)
20. [Shared UI Components](#shared-ui-components)
21. [Routing, State & API Layer](#routing-state--api-layer)

**Part III — Test Coverage**

22. [Test Plan](#22-test-plan)

**Part IV — Files Reference**

23. [New Files to Create](#23-new-files-to-create)
24. [Files to Modify](#24-files-to-modify)
25. [Definition of Done](#25-definition-of-done)

---

## 1. Executive Summary

### What Works Today

| Component                       | State                                                                                                                                                  |
| ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `PipelineExecutionEngine`       | Fully functional DAG executor                                                                                                                          |
| `DefaultOperatorCatalog`        | Thread-safe, 40+ built-in operators                                                                                                                    |
| `Orchestrator`                  | Pipeline cache, checkpoint-aware execution queue; checkpoint store is **`InMemoryCheckpointStorage`** — PostgreSQL migration deferred to Phase 6 below |
| `CheckpointAwareExecutionQueue` | Idempotency deduplication, restart recovery                                                                                                            |
| `PatternCompiler` + NFA engine  | Multi-phase compilation, pattern detection                                                                                                             |
| `AepHttpServer`                 | REST endpoints for pipelines, patterns, events, capabilities                                                                                           |
| `AgentGrpcService`              | gRPC management + execution services                                                                                                                   |
| AEP frontend                    | `PipelineBuilderPage` with DAG canvas (ReactFlow + Jotai)                                                                                              |

### What is Broken or Missing

| Gap                                                                                                        | Severity     | Root Cause                                                      |
| ---------------------------------------------------------------------------------------------------------- | ------------ | --------------------------------------------------------------- |
| `AIAgentOrchestrationManagerImpl` calls `agentRegistryService.executeAgent(...).getResult()` synchronously | **CRITICAL** | Blocks the ActiveJ event loop; causes NPE and deadlocks         |
| `AIAgentOrchestrationManagerImpl` does not use `BaseAgent` / `AgentTurnPipeline`                           | HIGH         | Agent framework lifecycle is bypassed entirely                  |
| No `AgentDispatcher` / `CatalogAgentDispatcher` integration                                                | HIGH         | Three-tier dispatch (J/S/L) not used                            |
| No `MemoryPlane` integration                                                                               | HIGH         | Agents have no episodic/semantic/procedural memory across turns |
| No `ConsolidationPipeline` integration                                                                     | HIGH         | Learning loop never runs                                        |
| `AgentRegistry` backed by Data-Cloud not used — uses `AgentRegistryService` stub                           | MEDIUM       | Durability and tenant isolation missing                         |
| All connector configs hardcoded (Kafka `localhost:9092`, Redis `localhost:6379`, SQS region `us-east-1`)   | MEDIUM       | Not deployable with real infrastructure                         |
| No REST endpoints for agent list/detail/execute/memory                                                     | MEDIUM       | UI cannot show agents                                           |
| No SSE endpoints for live pipeline / agent status                                                          | MEDIUM       | UI is polling-free only                                         |
| No HITL endpoint or workflow                                                                               | MEDIUM       | Human review of low-confidence outputs not possible             |
| AEP UI has only 1 page                                                                                     | MEDIUM       | No monitoring, no agent browser, no memory explorer             |
| `AgentRegistryService` has no `tenantId` on lookups                                                        | MEDIUM       | Multi-tenancy not enforced                                      |

---

## 2. Current State Inventory

### 2.1 AEP Java Modules

```
products/aep/
├── platform/          ← Core logic
│   ├── di/            ← 6 ActiveJ DI modules
│   ├── orchestrator/  ← AIAgentOrchestrationManagerImpl, Orchestrator
│   ├── pipeline/      ← PipelineExecutionEngine, StageRunner, AgentStepRunner
│   ├── pattern/       ← PatternCompiler, NFA engine
│   ├── connector/     ← Kafka, RabbitMQ, SQS, S3, HTTP strategies
│   ├── ingress/       ← Rate limiting, idempotency (Redis)
│   └── web/           ← HTTP controllers (Pattern, Capabilities, Session)
├── launcher/          ← AepLauncher, AepHttpServer (full route map)
└── ui/                ← React/TypeScript frontend
```

### 2.2 Agent Framework Modules (platform/java/)

```
agent-framework/       ← BaseAgent, AgentTurnPipeline, AgentDescriptor, TypedAgent
agent-memory/          ← MemoryPlane SPI, PersistentMemoryPlane, JdbcMemoryItemRepository
agent-learning/        ← ConsolidationPipeline, EvaluationGate, SkillPromotionWorkflow
agent-registry/        ← AgentRegistry SPI, DataCloudAgentRegistry
agent-dispatch/        ← AgentDispatcher, CatalogAgentDispatcher, 3-tier (J/S/L)
```

### 2.3 Existing REST API (AepHttpServer)

| Method              | Path                                 | Status     |
| ------------------- | ------------------------------------ | ---------- |
| GET                 | `/health`, `/ready`, `/live`         | ✅ Works   |
| GET                 | `/info`, `/metrics`                  | ✅ Works   |
| POST                | `/api/v1/events`                     | ✅ Works   |
| POST                | `/api/v1/events/batch`               | ✅ Works   |
| POST/PUT/DELETE     | `/api/v1/deployments/:id`            | ✅ Works   |
| GET/POST/DELETE     | `/api/v1/patterns/:id`               | ✅ Works   |
| GET/POST/PUT/DELETE | `/api/v1/pipelines/:id`              | ✅ Works   |
| POST                | `/api/v1/pipelines/validate`         | ✅ Works   |
| GET                 | `/admin/capabilities/*`              | ✅ Works   |
| POST                | `/api/v1/analytics/anomalies`        | ✅ Works   |
| GET                 | `/api/v1/agents`                     | ❌ Missing |
| GET                 | `/api/v1/agents/:id`                 | ❌ Missing |
| POST                | `/api/v1/agents/:id/execute`         | ❌ Missing |
| GET                 | `/api/v1/agents/:id/memory`          | ❌ Missing |
| GET                 | `/api/v1/agents/:id/memory/episodes` | ❌ Missing |
| GET                 | `/api/v1/agents/:id/policies`        | ❌ Missing |
| POST                | `/api/v1/agents/:id/hitl/approve`    | ❌ Missing |
| GET (SSE)           | `/events/pipeline-runs`              | ❌ Missing |
| GET (SSE)           | `/events/agent-outputs`              | ❌ Missing |
| GET (SSE)           | `/events/hitl-queue`                 | ❌ Missing |

---

## 3. Critical Bugs to Fix First

These must be fixed **before** any new feature work. They cause NPE, deadlocks, and event-loop
starvation.

### Bug 1 — Synchronous `.getResult()` in `AIAgentOrchestrationManagerImpl`

**File**: `products/aep/platform/src/main/java/com/ghatana/orchestrator/ai/impl/AIAgentOrchestrationManagerImpl.java`

**Problem** (line ~180 in `executeChainInternal`):

```java
// ❌ FORBIDDEN: .getResult() throws NPE if the Promise is not yet resolved
// and blocks the calling thread, stalling the ActiveJ event loop.
List<Event> outputEvents = agentRegistryService.executeAgent(
    currentAgentId, event, context).getResult();
```

**Fix**: Rewrite `executeChainInternal` to chain Promises properly:

```java
private Promise<List<Event>> executeChainInternal(
    String executionId, String chainId, List<String> agentIds,
    List<Event> currentEvents, AgentExecutionContext context, int stepIndex) {

    if (stepIndex >= agentIds.size()) {
        updateExecutionStatus(executionId, ExecutionState.COMPLETED, 100.0, null);
        metrics.incrementCounter("ai.chain.completed");
        return Promise.of(currentEvents);
    }

    String currentAgentId = agentIds.get(stepIndex);
    updateExecutionStatus(executionId, ExecutionState.RUNNING,
        (double) stepIndex / agentIds.size() * 100.0, null);

    // ✅ Chain all event promises, then recurse
    List<Promise<List<Event>>> eventPromises = currentEvents.stream()
        .map(event -> Promise.ofBlocking(blockingExecutor,
            () -> agentRegistryService.executeAgent(currentAgentId, event, context)))
        .toList();

    return Promises.all(eventPromises)
        .map(results -> results.stream().flatMap(List::stream).toList())
        .then(accumulated ->
            executeChainInternal(executionId, chainId, agentIds,
                accumulated, context, stepIndex + 1));
}
```

### Bug 2 — `AgentRegistryService` Has No `tenantId`

**File**: `products/aep/platform/src/main/java/com/ghatana/pipeline/registry/AgentRegistryService.java`

All public methods must accept `TenantId tenantId` as first parameter. Without it, agents from
different tenants can see each other's agents — a multi-tenancy security violation.

### Bug 3 — Redis connection in `AepIngressModule` has no connection validation

If Redis is unavailable, `JedisPool` construction succeeds but first borrow throws. Add a
startup health check:

```java
@Provides
JedisPool jedisPool() {
    String host = System.getenv().getOrDefault("AEP_REDIS_HOST", "localhost");
    int    port = Integer.parseInt(System.getenv().getOrDefault("AEP_REDIS_PORT", "6379"));
    JedisPoolConfig cfg = new JedisPoolConfig();
    cfg.setTestOnBorrow(true);   // Validate on borrow
    cfg.setMaxTotal(16); cfg.setMaxIdle(8); cfg.setMinIdle(2);
    return new JedisPool(cfg, host, port, 5_000);
}
```

---

## Phase 1 — Wire AIAgentOrchestrationManager to Agent Framework

### Goal

Replace the hand-rolled chain execution loop with proper use of `AgentTurnPipeline` and
`BaseAgent`. Every agent invocation must go through the full
PERCEIVE → REASON → ACT → CAPTURE → REFLECT lifecycle.

### 1.1 Introduce `AepAgentAdapter`

Create a bridge that wraps an AEP `AgentDefinition` into a `BaseAgent<Event, List<Event>>` so
the agent-framework lifecycle applies.

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/agent/AepAgentAdapter.java`

```java
/**
 * @doc.type class
 * @doc.purpose Adapts AEP AgentDefinition into agent-framework BaseAgent lifecycle.
 * @doc.layer product
 * @doc.pattern Adapter
 * @doc.gaa.lifecycle perceive,reason,act,capture,reflect
 */
public class AepAgentAdapter extends BaseAgent<Event, List<Event>> {

    private final AgentDefinition definition;
    private final AgentStepRunner stepRunner;
    private final MemoryPlane memoryPlane;

    public AepAgentAdapter(AgentDefinition definition,
                           AgentStepRunner stepRunner,
                           MemoryPlane memoryPlane) {
        this.definition  = definition;
        this.stepRunner  = stepRunner;
        this.memoryPlane = memoryPlane;
    }

    @Override
    protected Promise<Event> perceive(Event rawInput, AgentContext ctx) {
        // Retrieve recent episodic memory to enrich context
        return Promise.ofBlocking(ctx.blockingExecutor(), () -> {
            List<MemoryItem> recent = memoryPlane.queryEpisodes(
                MemoryQuery.builder()
                    .agentId(definition.id())
                    .tenantId(ctx.getTenantId())
                    .limit(5)
                    .build());
            // Attach to context for use in reason()
            ctx.setAttribute("recent_episodes", recent);
            return rawInput;
        });
    }

    @Override
    protected Promise<AgentResult<List<Event>>> act(Event input, AgentContext ctx) {
        return Promise.ofBlocking(ctx.blockingExecutor(), () -> {
            List<Event> output = stepRunner.execute(definition, input,
                toAepContext(ctx));
            return AgentResult.<List<Event>>builder()
                .output(output)
                .status(AgentResultStatus.SUCCESS)
                .agentId(definition.id())
                .build();
        });
    }

    @Override
    protected Promise<Void> capture(AgentResult<List<Event>> result,
                                    Event input, AgentContext ctx) {
        EnhancedEpisode episode = EnhancedEpisode.builder()
            .agentId(definition.id())
            .tenantId(ctx.getTenantId())
            .input(input.payload())
            .output(result.output().stream()
                .map(Event::payload).toList())
            .outcome(result.status().name())
            .timestamp(Instant.now())
            .build();
        return Promise.ofBlocking(ctx.blockingExecutor(),
            () -> { memoryPlane.storeEpisode(episode); return null; });
    }

    @Override
    protected Promise<Void> reflect(AgentResult<List<Event>> result,
                                    Event input, AgentContext ctx) {
        // Fire-and-forget: publish pattern.learning to AEP so PatternDetectionAgent
        // can index new procedures — never awaited, never blocking the user response.
        // This closes the REFLECT → AEP → PERCEIVE learning loop (see YAPPC_AEP plan §9).
        EventCloud eventCloud = ctx.getAttribute("eventCloud", EventCloud.class);
        if (eventCloud != null) {
            eventCloud.append(ctx.getTenantId(), "pattern.learning", Map.of(
                "agentId",  definition.id(),
                "outcome",  result.status().name(),
                "output",   result.output().stream().map(Event::payload).toList()
            ));
            // intentionally fire-and-forget: no .whenComplete() or await
        }
        return Promise.of(null);
    }
}
```

### 1.2 Replace `executeChainInternal` with `AgentTurnPipeline`

In `AIAgentOrchestrationManagerImpl`, replace the raw loop with:

```java
@Override
public Promise<List<Event>> executeChain(
    String chainId, Event inputEvent, AgentExecutionContext aepCtx) {

    List<String> agentIds = agentChains.get(chainId);
    if (agentIds == null || agentIds.isEmpty()) {
        return Promise.ofException(
            new IllegalArgumentException("Chain not found: " + chainId));
    }

    String executionId = "exec_" + executionIdCounter.incrementAndGet();
    executionStatuses.put(executionId, AgentExecutionStatus.running(executionId));

    // Build a typed context bridging AEP context to agent-framework AgentContext
    AgentContext frameworkCtx = AepContextBridge.toFrameworkContext(aepCtx, memoryPlane);

    // For each agent in the chain, build an AepAgentAdapter and run via AgentTurnPipeline
    return Promises.reduceEx(agentIds, List.of(inputEvent),
        (currentInput, agentId) -> {
            AgentDefinition def = agentDefinitions.get(agentId);
            AepAgentAdapter adapter = new AepAgentAdapter(def, agentStepRunner, memoryPlane);
            AgentTurnPipeline<Event, List<Event>> pipeline =
                AgentTurnPipeline.of(adapter);
            // Pipeline executes full PERCEIVE→REASON→ACT→CAPTURE→REFLECT
            return pipeline.execute(currentInput.get(0), frameworkCtx)
                .map(result -> result.output());
        })
        .whenComplete((result, e) -> {
            if (e == null) {
                executionStatuses.put(executionId,
                    AgentExecutionStatus.completed(executionId, result));
                metrics.incrementCounter("ai.chain.completed");
            } else {
                executionStatuses.put(executionId,
                    AgentExecutionStatus.failed(executionId, e.getMessage()));
                metrics.incrementCounter("ai.chain.failed");
            }
        });
}
```

### 1.3 `AepContextBridge`

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/agent/AepContextBridge.java`

```java
/**
 * @doc.type class
 * @doc.purpose Converts AEP AgentExecutionContext to agent-framework AgentContext.
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class AepContextBridge {

    public static AgentContext toFrameworkContext(
            AgentExecutionContext aepCtx, MemoryPlane memoryPlane) {
        return AgentContext.builder()
            .turnId(UUID.randomUUID().toString())
            .agentId(aepCtx.agentId())
            .tenantId(aepCtx.tenantId())
            .userId(aepCtx.userId())
            .sessionId(aepCtx.sessionId())
            .startTime(Instant.now())
            .memoryStore(memoryPlane)
            .blockingExecutor(aepCtx.executor())
            .build();
    }
}
```

---

## Phase 2 — Agent Dispatch & Catalog Integration

### Goal

Replace direct `AgentRegistryService.executeAgent()` calls with the three-tier dispatcher
(`CatalogAgentDispatcher`) so agents can be resolved from YAML catalogs, remote services, or
LLM prompts.

### 2.1 Add `AgentDispatcher` Dependency

In `AepOrchestrationModule`:

```java
@Provides
AgentDispatcher agentDispatcher(
    OperatorCatalog operatorCatalog,
    AgentRegistry agentRegistry,
    AIIntegrationService aiIntegrationService) {
    return new CatalogAgentDispatcher(
        agentRegistry,
        operatorCatalog,
        aiIntegrationService);
}
```

`CatalogAgentDispatcher` resolves:

- **TIER_J** — Java class registered in `operatorCatalog`
- **TIER_S** — Remote service listed in `agentRegistry` (Data-Cloud backed)
- **TIER_L** — LLM fallback via `aiIntegrationService`

### 2.2 Use Dispatcher in `AIAgentOrchestrationManagerImpl`

```java
// In executeChainInternal, per-agent step:
AgentDispatcher.DispatchResult dispatchResult =
    agentDispatcher.resolve(currentAgentId);

// Then execute via the resolved tier:
return agentDispatcher.dispatch(currentAgentId, event, frameworkCtx);
```

### 2.3 Load AEP Operator Catalog from YAML

Create `AepOperatorCatalogLoader` that reads `resources/operators/` YAML files and registers
each into `DefaultOperatorCatalog` at startup:

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/catalog/AepOperatorCatalogLoader.java`

```java
/**
 * @doc.type class
 * @doc.purpose Loads AEP operator agent YAML definitions into DefaultOperatorCatalog at startup.
 * @doc.layer product
 * @doc.pattern Service
 */
public class AepOperatorCatalogLoader {

    private final ObjectMapper yamlMapper;
    private final DefaultOperatorCatalog catalog;
    private final AgentDefinitionLoader platformLoader; // From platform/java/agent-framework

    public Promise<Integer> loadFromClasspath() {
        return platformLoader.loadFromClasspath("operators/")
            .map(definitions -> {
                definitions.forEach(catalog::register);
                return definitions.size();
            });
    }
}
```

Call `loadFromClasspath()` during `AepLauncher` startup, before the HTTP server starts.

---

## Phase 3 — Memory Plane Integration

### Goal

Every agent execution stores episodes to `PersistentMemoryPlane` and retrieves relevant
context during the `perceive()` phase. Memory is tenant-scoped and backed by PostgreSQL
(`JdbcMemoryItemRepository`).

### 3.1 Bind `MemoryPlane` in `AepOrchestrationModule`

```java
@Provides
MemoryPlane memoryPlane(
    JdbcMemoryItemRepository memoryRepo,
    JdbcTaskStateRepository taskStateRepo,
    ExecutorService blockingExecutor) {
    return new PersistentMemoryPlane(memoryRepo, taskStateRepo, blockingExecutor);
}

@Provides
JdbcMemoryItemRepository memoryItemRepository(DataSource dataSource) {
    return new JdbcMemoryItemRepository(dataSource);
}

@Provides
JdbcTaskStateRepository taskStateRepository(DataSource dataSource) {
    return new JdbcTaskStateRepository(dataSource);
}
```

### 3.2 Flyway Migration — Memory Tables

Add to `products/aep/platform/src/main/resources/db/migration/`:

**`V5__memory_plane.sql`**:

```sql
-- Memory items (all tiers)
CREATE TABLE aep_memory_items (
    id              UUID PRIMARY KEY,
    agent_id        TEXT        NOT NULL,
    tenant_id       TEXT        NOT NULL,
    sphere_id       TEXT,
    item_type       TEXT        NOT NULL,  -- EPISODE, FACT, PROCEDURE, etc.
    payload         JSONB       NOT NULL,
    embedding       vector(1536),          -- pgvector for semantic search
    confidence      FLOAT8      DEFAULT 1.0,
    validity_status TEXT        NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON aep_memory_items (agent_id, tenant_id, item_type, created_at DESC);
CREATE INDEX ON aep_memory_items USING ivfflat (embedding vector_cosine_ops);

-- Task state
CREATE TABLE aep_task_states (
    task_id         TEXT        NOT NULL,
    agent_id        TEXT        NOT NULL,
    tenant_id       TEXT        NOT NULL,
    state_data      JSONB       NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (task_id, agent_id, tenant_id)
);
```

### 3.3 Memory Retrieval in `AepAgentAdapter.perceive()`

Already shown in Phase 1 §1.1. Key parameters:

- Query **last 5 episodes** (fast, bounded)
- Query **relevant facts** by semantic similarity using `memoryPlane.searchSemantic()`
- Attach to `AgentContext` attributes so `act()` can use them

### 3.4 Episode Storage in `AepAgentAdapter.capture()`

Already shown in Phase 1 §1.1. Key requirements:

- Use `Promise.ofBlocking()` — never block event loop
- Include input payload, output events, outcome status, and timestamp
- Include `traceId` from `AgentContext` for correlation

---

## Phase 4 — Learning Loop (ConsolidationPipeline)

### Goal

After each agent turn, the agent's episodic memory is periodically consolidated into semantic
facts and procedural policies via `ConsolidationPipeline`. Low-confidence policies are flagged
for human review (HITL).

### 4.1 Background Consolidation Scheduler

In `AepOrchestrationModule`:

```java
@Provides
ConsolidationPipeline consolidationPipeline(
    MemoryPlane memoryPlane,
    AIIntegrationService aiIntegrationService,
    ExecutorService blockingExecutor) {
    return new ConsolidationPipeline(
        List.of(
            new EpisodicToSemanticConsolidator(aiIntegrationService, memoryPlane),
            new EpisodicToProceduralConsolidator(aiIntegrationService, memoryPlane)
        ),
        new EntrenchmentConflictResolver()
    );
}
```

Schedule consolidation every 5 minutes per active agent:

```java
@Provides
LearningScheduler learningScheduler(
    ConsolidationPipeline consolidationPipeline,
    AgentRegistry agentRegistry,
    ScheduledExecutorService scheduler,
    MetricsCollector metrics) {
    return new LearningScheduler(
        consolidationPipeline, agentRegistry, scheduler, metrics,
        Duration.ofMinutes(5));
}
```

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/learning/LearningScheduler.java`

```java
/**
 * @doc.type class
 * @doc.purpose Periodically triggers ConsolidationPipeline for all active agents.
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle reflect
 */
public class LearningScheduler {

    public void start() {
        scheduler.scheduleWithFixedDelay(
            this::consolidateAll,
            initialDelay.toSeconds(),
            period.toSeconds(),
            TimeUnit.SECONDS);
    }

    private void consolidateAll() {
        agentRegistry.listAgentIds().whenResult(agentIds ->
            agentIds.forEach(agentId ->
                consolidationPipeline.consolidate(agentId, Instant.now().minus(period))
                    .whenException(e ->
                        log.warn("Consolidation failed for agent {}: {}", agentId, e.getMessage()))
            )
        );
    }
}
```

### 4.2 HITL Flagging

After `EpisodicToProceduralConsolidator` extracts a procedure, it scores confidence. Wrap
the result with an `EvaluationGate`:

```java
SkillPromotionWorkflow promotionWorkflow = new SkillPromotionWorkflow(
    List.of(
        new SafetyEvaluationGate(),
        new RegressionEvaluationGate()
    )
);
```

If `GateResult.passed() == false` or `GateResult.score() < 0.7`, publish a `HitlReviewItem`
to the `hitlQueue` (in-memory, backed by `EventLogStore`):

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/hitl/HitlQueue.java`

```java
/**
 * @doc.type class
 * @doc.purpose Queues low-confidence or safety-failed policy candidates for human review.
 * @doc.layer product
 * @doc.pattern Service
 */
public class HitlQueue {
    private final EventLogStore eventLogStore;
    private final ExecutorService executor;

    public Promise<Void> enqueue(HitlReviewItem item) {
        return Promise.ofBlocking(executor,
            () -> eventLogStore.append(
                EventEntry.of("aep.hitl.review_requested", jackson.writeValueAsBytes(item))));
    }

    public Promise<List<HitlReviewItem>> listPending(TenantId tenantId) { ... }
    public Promise<Void> approve(String itemId, String reviewerId) { ... }
    public Promise<Void> reject(String itemId, String reviewerId, String reason) { ... }
}
```

---

## Phase 5 — AgentRegistry & Multi-Tenancy

### Goal

Replace `AgentRegistryService` (stub) with `DataCloudAgentRegistry` (Data-Cloud backed,
durable, tenant-scoped). All `AgentRegistry` operations are event-sourced via `EventLogStore`.

### 5.1 Bind `DataCloudAgentRegistry`

In `AepOrchestrationModule`:

```java
@Provides
AgentRegistry agentRegistry(EventLogStore eventLogStore, ExecutorService blockingExecutor) {
    return new DataCloudAgentRegistry(eventLogStore, blockingExecutor);
}
```

### 5.2 Add `TenantId` to `AgentRegistryService`

Every method in `AgentRegistryService` that touches agents must receive `TenantId tenantId`
as the first argument. The underlying `DataCloudAgentRegistry` already isolates by tenant.

Before (broken):

```java
public Promise<List<AgentInfo>> listAgents() { ... }
```

After (correct):

```java
public Promise<List<AgentInfo>> listAgents(TenantId tenantId) { ... }
```

Update all call sites in `AIAgentOrchestrationManagerImpl`, controllers, and gRPC service.

### 5.3 Registration at Startup

During `AepLauncher` startup, after operator catalog is loaded, register all catalog agents
into `AgentRegistry` for the default platform tenant:

```java
agentDefinitionLoader.loadFromClasspath("agents/")
    .then(definitions ->
        Promises.all(definitions.stream()
            .map(def -> agentRegistry.register(def, defaultConfig()))
            .toList()))
    .whenComplete((ok, e) ->
        log.info("Registered {} platform agents", ok != null ? ok.size() : 0));
```

---

## Phase 6 — Environment-Driven Configuration

Remove all hardcoded connection strings. Every external dependency reads from environment
variables with safe defaults.

### 6.1 `AepConnectorModule` — Kafka

```java
@Provides
KafkaConsumerConfig kafkaConsumerConfig() {
    return KafkaConsumerConfig.builder()
        .bootstrapServers(env("AEP_KAFKA_BROKERS", "localhost:9092"))
        .groupId(env("AEP_KAFKA_GROUP", "aep-consumer-group"))
        .topics(List.of(env("AEP_KAFKA_TOPIC", "events")))
        .batchSize(intEnv("AEP_KAFKA_BATCH_SIZE", 100))
        .build();
}
```

### 6.2 `AepConnectorModule` — SQS / S3

```java
@Provides
SqsConfig sqsConfig() {
    return SqsConfig.builder()
        .region(env("AEP_AWS_REGION", "us-east-1"))
        .queueName(env("AEP_SQS_QUEUE", "aep-events"))
        .queueUrl(env("AEP_SQS_URL", ""))   // Required in production
        .build();
}

@Provides
S3Config s3Config() {
    return S3Config.builder()
        .region(env("AEP_AWS_REGION", "us-east-1"))
        .bucketName(env("AEP_S3_BUCKET", "aep-storage"))
        .build();
}
```

### 6.3 `AepIngressModule` — Redis

```java
@Provides
JedisPool jedisPool() {
    String host = env("AEP_REDIS_HOST", "localhost");
    int    port = intEnv("AEP_REDIS_PORT", 6379);
    // ... pool config with testOnBorrow
}
```

### 6.4 Config Reference Table

| Env Var                | Default                           | Used By                  |
| ---------------------- | --------------------------------- | ------------------------ |
| `AEP_KAFKA_BROKERS`    | `localhost:9092`                  | `AepConnectorModule`     |
| `AEP_KAFKA_GROUP`      | `aep-consumer-group`              | `AepConnectorModule`     |
| `AEP_KAFKA_TOPIC`      | `events`                          | `AepConnectorModule`     |
| `AEP_KAFKA_BATCH_SIZE` | `100`                             | `AepConnectorModule`     |
| `AEP_RABBITMQ_HOST`    | `localhost`                       | `AepConnectorModule`     |
| `AEP_RABBITMQ_PORT`    | `5672`                            | `AepConnectorModule`     |
| `AEP_SQS_URL`          | _(required in prod)_              | `AepConnectorModule`     |
| `AEP_AWS_REGION`       | `us-east-1`                       | `AepConnectorModule`     |
| `AEP_S3_BUCKET`        | `aep-storage`                     | `AepConnectorModule`     |
| `AEP_REDIS_HOST`       | `localhost`                       | `AepIngressModule`       |
| `AEP_REDIS_PORT`       | `6379`                            | `AepIngressModule`       |
| `AEP_REDIS_TIMEOUT_MS` | `5000`                            | `AepIngressModule`       |
| `AEP_WORKER_THREADS`   | `availableProcessors()`           | `AepCoreModule`          |
| `AEP_HTTP_PORT`        | `8080`                            | `AepLauncher`            |
| `AEP_PG_URL`           | `jdbc:postgresql://localhost/aep` | `AepOrchestrationModule` |
| `AEP_PG_USER`          | `aep`                             | `AepOrchestrationModule` |
| `AEP_PG_PASS`          | _(required in prod)_              | `AepOrchestrationModule` |

### 6.5 `EnvConfig` Utility

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/config/EnvConfig.java`

```java
/**
 * @doc.type class
 * @doc.purpose Zero-dependency env-var helper with type coercion and logged defaults.
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class EnvConfig {
    public static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) {
            log.debug("Env var {} not set; using default: {}", key, defaultValue);
            return defaultValue;
        }
        return val.strip();
    }

    public static int intEnv(String key, int defaultValue) {
        try { return Integer.parseInt(env(key, String.valueOf(defaultValue))); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
```

---

## Phase 7 — New REST & SSE Endpoints

### 7.1 Agent Registry Endpoints

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/web/AgentController.java`

```java
/**
 * @doc.type class
 * @doc.purpose REST controller for agent registry, execution, and memory.
 * @doc.layer product
 * @doc.pattern Service
 */
public class AgentController {

    // GET /api/v1/agents?tenantId={tenantId}&capability={cap}&status={status}
    public Promise<HttpResponse> listAgents(HttpRequest request)

    // GET /api/v1/agents/:agentId?tenantId={tenantId}
    public Promise<HttpResponse> getAgent(HttpRequest request, String agentId)

    // POST /api/v1/agents/:agentId/execute
    // Body: { "input": {...}, "tenantId": "...", "userId": "..." }
    public Promise<HttpResponse> executeAgent(HttpRequest request, String agentId)

    // GET /api/v1/agents/:agentId/memory?tenantId={tenantId}&type={type}&limit={n}
    public Promise<HttpResponse> getMemory(HttpRequest request, String agentId)

    // GET /api/v1/agents/:agentId/memory/episodes?tenantId={tenantId}&limit={n}
    public Promise<HttpResponse> getEpisodes(HttpRequest request, String agentId)

    // GET /api/v1/agents/:agentId/policies?tenantId={tenantId}
    public Promise<HttpResponse> getPolicies(HttpRequest request, String agentId)

    // GET /api/v1/agents/:agentId/status/:executionId
    public Promise<HttpResponse> getExecutionStatus(HttpRequest request,
        String agentId, String executionId)
}
```

### 7.2 HITL Endpoints

**New file**: `products/aep/platform/src/main/java/com/ghatana/aep/web/HitlController.java`

```java
// GET /api/v1/hitl?tenantId={tenantId}&status=PENDING
public Promise<HttpResponse> listReviewItems(HttpRequest request)

// GET /api/v1/hitl/:itemId
public Promise<HttpResponse> getReviewItem(HttpRequest request, String itemId)

// POST /api/v1/hitl/:itemId/approve
// Body: { "reviewerId": "...", "note": "..." }
public Promise<HttpResponse> approveItem(HttpRequest request, String itemId)

// POST /api/v1/hitl/:itemId/reject
// Body: { "reviewerId": "...", "reason": "..." }
public Promise<HttpResponse> rejectItem(HttpRequest request, String itemId)
```

### 7.3 SSE Endpoints

Add to `AepHttpServer` route map:

```java
// Server-Sent Events — pipeline run status stream
GET /events/pipeline-runs?tenantId={tenantId}
    → Emits: { "type": "RUN_STARTED"|"RUN_COMPLETED"|"STAGE_FAILED", "data": {...} }

// Server-Sent Events — live agent output stream
GET /events/agent-outputs?tenantId={tenantId}&agentId={agentId}
    → Emits: { "type": "AGENT_OUTPUT", "agentId": "...", "output": [...] }

// Server-Sent Events — HITL new review items
GET /events/hitl-queue?tenantId={tenantId}
    → Emits: { "type": "REVIEW_REQUESTED", "itemId": "...", "agentId": "..." }
```

**Implementation pattern** (ActiveJ SSE):

```java
GET("/events/pipeline-runs", request -> {
    String tenantId = request.getQueryParameter("tenantId");
    return HttpResponse.ok200()
        .withHeader(HttpHeaders.CONTENT_TYPE, "text/event-stream")
        .withHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
        .withBodyStream(
            ChannelSupplier.ofIterable(
                eventBus.subscribe("pipeline-runs." + tenantId))
            .map(event -> encodeSSE(event))
        );
})
```

Register `AgentController` and `HitlController` routes in `AepHttpServer`.

---

## Phase 8 — gRPC Agent Service Hardening

### Goal

The existing `AgentGrpcService` uses `AgentFrameworkRegistry` (a separate registry type).
Wire it to use `DataCloudAgentRegistry` and `AgentDispatcher` for actual execution.

### 8.1 Execution via Dispatcher

In `ExecutionService.executeAgent()`:

```java
@Override
public void executeAgent(ExecuteAgentRequestProto request,
    StreamObserver<ExecuteAgentResponseProto> responseObserver) {

    AgentContext ctx = buildContext(request);
    agentDispatcher.dispatch(request.getAgentId(), toEvent(request), ctx)
        .whenComplete((result, e) -> {
            if (e != null) {
                responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onNext(toProto(result));
                responseObserver.onCompleted();
            }
        });
}
```

Never call `.getResult()` — always use `whenComplete` or chain `then()`.

---

## 12. UI Architecture Overview

### Current State

```
AEP UI (products/aep/ui/)
└── src/
    ├── main.tsx                    ← Renders PipelineBuilderPage directly (no router)
    ├── pages/
    │   └── PipelineBuilderPage.tsx ← Only page
    ├── components/pipeline/        ← Canvas, toolbar, panels, nodes
    ├── stores/pipeline.store.ts    ← Jotai atoms
    ├── api/pipeline.api.ts         ← 8 REST calls
    └── types/pipeline.types.ts     ← Domain types
```

### Target State

```
AEP UI (products/aep/ui/)
└── src/
    ├── main.tsx               ← React Router v7 with layout
    ├── App.tsx                ← Root with <Outlet>, nav shell, auth context
    ├── router.tsx             ← createBrowserRouter with all routes
    ├── pages/
    │   ├── PipelineBuilderPage.tsx   ← Redesigned (page 1)
    │   ├── AgentRegistryPage.tsx     ← New (page 2)
    │   ├── AgentDetailPage.tsx       ← New (page 3)
    │   ├── MonitoringDashboardPage.tsx ← New (page 4)
    │   ├── PatternStudioPage.tsx     ← New (page 5)
    │   ├── HitlReviewPage.tsx        ← New (page 6)
    │   └── LearningPage.tsx          ← New (page 7)
    ├── components/
    │   ├── pipeline/          ← Existing (refactored)
    │   ├── agents/            ← New: AgentCard, AgentTable, AgentStatusBadge
    │   ├── memory/            ← New: EpisodeTimeline, FactTable, PolicyCard
    │   ├── monitoring/        ← New: RunTable, AgentHealthGrid, EventRateChart
    │   ├── hitl/              ← New: ReviewCard, ApproveRejectPanel
    │   ├── learning/          ← New: ConsolidationChart, SkillPromotionList
    │   └── shared/            ← Layout, NavBar, TenantSelector, SseStatus
    ├── stores/
    │   ├── pipeline.store.ts  ← Existing (small additions)
    │   ├── agents.store.ts    ← New: agent list, selected agent
    │   ├── monitoring.store.ts ← New: run list, SSE connection
    │   └── hitl.store.ts      ← New: review queue, pending count
    ├── api/
    │   ├── pipeline.api.ts    ← Existing
    │   ├── agents.api.ts      ← New
    │   ├── memory.api.ts      ← New
    │   ├── hitl.api.ts        ← New
    │   └── sse.ts             ← New: SSE client with auto-reconnect
    ├── hooks/
    │   ├── useAgents.ts       ← TanStack Query for agent list
    │   ├── useAgentMemory.ts  ← TanStack Query for memory
    │   ├── usePipelineRuns.ts ← SSE subscription + TanStack Query
    │   └── useHitlQueue.ts    ← SSE subscription + polling fallback
    └── types/
        ├── pipeline.types.ts  ← Existing
        ├── agent.types.ts     ← New
        ├── memory.types.ts    ← New
        └── hitl.types.ts      ← New
```

### Technology Decisions

| Concern      | Library                  | Reason                                |
| ------------ | ------------------------ | ------------------------------------- |
| Client state | `jotai`                  | Already in use                        |
| Server state | `@tanstack/react-query`  | Add this; replaces manual fetch state |
| Routing      | `react-router` v7        | Already installed                     |
| Charts       | `recharts`               | Lightweight, composable (add to deps) |
| Real-time    | Native `EventSource` API | No extra dep; wrapped in `sse.ts`     |
| Code editor  | `@monaco-editor/react`   | Pattern YAML editing (add to deps)    |

---

## Page 1 — Pipeline Builder (Redesign)

### What Changes

The existing `PipelineBuilderPage` is functional but has UX issues:

- No router integration (no URL per pipeline)
- No live validation feedback
- No run-now capability
- Stage palette is side-by-side, not collapsible
- No keyboard shortcuts displayed
- Cannot load from existing pipelines (no list page)

### Redesigned Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ AEP  Pipeline Builder  [pipeline-name ▼]    [DRAFT]  [Save] [▶ Run] [⋮] │
├──────────┬──────────────────────────────────────┬───────────────┤
│ PALETTE  │                                      │  PROPERTIES   │
│          │         DAG CANVAS                   │               │
│ [search] │   ┌─────┐      ┌──────┐             │  Stage Name   │
│          │   │Ingest│ ──► │Enrich│             │  [___________]│
│ Ingestion│   └─────┘      └──────┘             │               │
│ > Kafka  │                                      │  Agents       │
│ > HTTP   │   drag operators here                │  [+] Add agent│
│ > SQS    │                                      │               │
│          │                                      │  Config       │
│ Analysis │   [Mini-map]   [Fit] [+] [-]         │  ──────────── │
│ > Pattern│                                      │  key: value   │
│ > ML     ├──────────────────────────────────────┤               │
│          │ ⚠ 2 warnings  ✓ Valid  [Show errors] │               │
└──────────┴──────────────────────────────────────┴───────────────┘
```

### Key Changes

1. **URL-based routing**: `/aep/pipelines/new` and `/aep/pipelines/:id`
2. **TanStack Query** for save/load: `useMutation` for save, `useQuery` for load
3. **Live validation**: Debounced (500 ms) call to `/api/v1/pipelines/validate` after each
   change; results shown inline in a status bar
4. **Run Now button**: Calls `POST /api/v1/events` with a test event and the current pipeline
   spec; shows a drawer with live SSE output
5. **Collapsible palette** (`⇦/⇨` toggle)
6. **Keyboard shortcut help** (`?` key shows modal)
7. **Import/Export**: JSON and YAML both supported
8. **Undo/redo limit**: Capped at 50 states (already tracked in `historyAtom`)

### Types to Add (`types/pipeline.types.ts`)

```typescript
interface PipelineRun {
  runId: string;
  pipelineId: string;
  status: "RUNNING" | "COMPLETED" | "FAILED";
  startedAt: string;
  completedAt?: string;
  outputEvents: AepEvent[];
  error?: string;
}
```

---

## Page 2 — Agent Registry

**Route**: `/aep/agents`

### Purpose

Browse all agents registered in the platform agent registry. Filter by capability, status,
agent type.

### Layout

```
┌────────────────────────────────────────────────────────────────┐
│ Agent Registry                          [+ Register Agent]      │
├────────────────────────────────────────────────────────────────┤
│ [Search agents...]  [Type ▼]  [Status ▼]  [Capability ▼]      │
├────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────┐   │
│ │ 🤖 schema-validator-agent   v1.0.0   DETERMINISTIC  ●   │   │
│ │    Validates event payloads against registered schemas    │   │
│ │    Capabilities: json-schema-validation, data-validation  │   │
│ │    Last active: 2 min ago · 1,204 executions today        │   │
│ │    [View Detail]  [Execute]  [View Memory]                │   │
│ └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│ ┌────────────────────────────────────────────────────────────┐  │
│ │ 🧠 fraud-detection-agent    v2.1.0   PROBABILISTIC  ●     │  │
│ │    ...                                                     │  │
│ └────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
```

### State & API

```typescript
// hooks/useAgents.ts
export function useAgents(tenantId: string, filters: AgentFilters) {
  return useQuery({
    queryKey: ["agents", tenantId, filters],
    queryFn: () => agentsApi.listAgents(tenantId, filters),
    staleTime: 30_000,
  });
}

// api/agents.api.ts
export const agentsApi = {
  listAgents: (tenantId, filters) =>
    axios.get("/api/v1/agents", { params: { tenantId, ...filters } }),
  getAgent: (agentId, tenantId) =>
    axios.get(`/api/v1/agents/${agentId}`, { params: { tenantId } }),
  executeAgent: (agentId, input, tenantId) =>
    axios.post(`/api/v1/agents/${agentId}/execute`, { input, tenantId }),
};
```

### Types (`types/agent.types.ts`)

```typescript
interface AgentInfo {
  agentId: string;
  name: string;
  version: string;
  description: string;
  agentType:
    | "DETERMINISTIC"
    | "PROBABILISTIC"
    | "HYBRID"
    | "ADAPTIVE"
    | "COMPOSITE"
    | "REACTIVE";
  capabilities: string[];
  status:
    | "HEALTHY"
    | "DEGRADED"
    | "UNHEALTHY"
    | "STARTING"
    | "STOPPING"
    | "UNKNOWN";
  executionCount: number;
  lastActive?: string;
}

interface AgentFilters {
  capability?: string;
  status?: string;
  agentType?: string;
  search?: string;
}
```

---

## Page 3 — Agent Detail & Memory Browser

**Route**: `/aep/agents/:agentId`

### Layout (tabbed)

```
┌──────────────────────────────────────────────────────────────────┐
│ ← Back  schema-validator-agent  v1.0.0  ● HEALTHY  [Execute]     │
├──────────────────────────────────────────────────────────────────┤
│ [Overview]  [Memory]  [Policies]  [Executions]  [Config]         │
├──────────────────────────────────────────────────────────────────┤
│                                                                    │
│ TAB: Memory                                                        │
│                                                                    │
│ [Episodes]  [Facts]  [Procedures]  [Working Memory]               │
│                                                                    │
│ ▼ Episode Timeline                                                 │
│                                                                    │
│ 11:43:02  INPUT: {schema:"user-event-v2", payload:{…}}            │
│           OUTPUT: [{valid:true}]   OUTCOME: SUCCESS  ▸            │
│                                                                    │
│ 11:40:15  INPUT: {schema:"order-v1", payload:{…}}                 │
│           OUTPUT: [{valid:false, error:"missing field 'qty'"}]     │
│           OUTCOME: SUCCESS  ▸                                      │
│                                                                    │
│ ▼ Facts (Semantic Memory) — 24 facts                              │
│ "user-event-v2 requires field 'userId'"  confidence: 0.97  ACTIVE │
│ "order-v1 field 'qty' is required"       confidence: 0.95  ACTIVE │
│                                                                    │
│ ▼ Procedures (Procedural Memory) — 3 policies                      │
│ [P-001] Validate payload before schema check  conf: 0.82  ● Human │
└──────────────────────────────────────────────────────────────────┘
```

### Episodes Tab

- Chronological list (newest first), virtual scrolling (100+ episodes)
- Each episode expandable: shows full input, output, context, outcome
- One-click re-run with same input

### Facts Tab

- Searchable table: subject, predicate, object, confidence, status
- Color-coded validity: green = ACTIVE, yellow = STALE, grey = ARCHIVED
- Inline edit confidence (moderator only)

### Procedures Tab

- Card per policy: steps list, confidence badge (green ≥ 0.9, yellow ≥ 0.7, red < 0.7)
- Red badges → link to HITL Review page

### API hooks

```typescript
// hooks/useAgentMemory.ts
export function useEpisodes(agentId: string, tenantId: string, limit = 20) {
  return useQuery({
    queryKey: ['episodes', agentId, tenantId, limit],
    queryFn: () => memoryApi.getEpisodes(agentId, tenantId, limit),
  });
}

export function useFacts(agentId: string, tenantId: string) {
  return useQuery({ ... });
}

export function usePolicies(agentId: string, tenantId: string) {
  return useQuery({ ... });
}
```

---

## Page 4 — Monitoring Dashboard

**Route**: `/aep/monitoring`

### Layout

```
┌──────────────────────────────────────────────────────────────────┐
│ Monitoring  ● LIVE  [Last 15 min ▼]  [Tenant: default ▼]        │
├──────────────────────────────────────────────────────────────────┤
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌───────────┐  │
│  │ 1,204      │  │ 98.7%      │  │ 142 ms     │  │ 3 ⬛       │  │
│  │ Events/min │  │ Success    │  │ p99 latency│  │ HITL Queue│  │
│  └────────────┘  └────────────┘  └────────────┘  └───────────┘  │
├──────────────────────────────────────────────────────────────────┤
│ Pipeline Runs                              [See All]              │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ fraud-detection-pipeline  ▶ RUNNING  Started 11:40  12 stages│ │
│ │ ████████████░░░░░░░░░░  65%  ETA: ~2 min                    │ │
│ └──────────────────────────────────────────────────────────────┘ │
│ ┌──────────────────────────────────────────────────────────────┐ │
│ │ user-enrichment-pipeline  ✓ COMPLETED  11:38  8 stages  0 err│ │
│ └──────────────────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────┤
│ Agent Health Grid                                                  │
│ ┌───────────────────────────────────────────────────────────┐    │
│ │ ● schema-validator  ● fraud-classifier  ○ enricher        │    │
│ │ ● pattern-detector  ○ recommender       ● deduplicator    │    │
│ └───────────────────────────────────────────────────────────┘    │
├──────────────────────────────────────────────────────────────────┤
│ Event Rate                                           [Export CSV] │
│ [Sparkline chart: events/s over last 15 min per topic]            │
└──────────────────────────────────────────────────────────────────┘
```

### Live Updates via SSE

```typescript
// hooks/usePipelineRuns.ts
export function useLivePipelineRuns(tenantId: string) {
  const queryClient = useQueryClient();
  const [runs, setRuns] = useAtom(pipelineRunsAtom);

  useEffect(() => {
    const es = new EventSource(`/events/pipeline-runs?tenantId=${tenantId}`);
    es.onmessage = (e) => {
      const event = JSON.parse(e.data);
      setRuns((prev) => updateRun(prev, event));
      // Also invalidate the TanStack Query cache for agent health
      queryClient.invalidateQueries({ queryKey: ["agents", tenantId] });
    };
    return () => es.close();
  }, [tenantId]);

  return runs;
}
```

### Charts (`recharts`)

- **Event Rate**: `<LineChart>` with `<Line>` per topic, time x-axis auto-scrolling
- **Latency**: `<BarChart>` showing p50/p99 per pipeline
- **Error Rate**: `<AreaChart>` stacked errors by stage

### Stat Cards

```typescript
interface MonitoringStats {
  eventsPerMinute: number;
  successRate: number; // 0-100
  p99LatencyMs: number;
  hitlQueueSize: number;
}
```

---

## Page 5 — Pattern Studio

**Route**: `/aep/patterns`

### Layout (split-pane)

```
┌────────────────────────────────────────────────────────────────────┐
│ Pattern Studio                            [+ New Pattern]  [Import] │
├────────────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────┐  ┌─────────────────────────────┐  │
│ │ Patterns                    │  │ Pattern Editor              │  │
│ │                             │  │                             │  │
│ │ ● fraud-sequence (ACTIVE)   │  │  name: fraud-sequence       │  │
│ │ ● anomaly-threshold (ACTIVE)│  │  description: Detect...     │  │
│ │ ○ test-pattern (DRAFT)      │  │  specification:             │  │
│ │                             │  │  ┌──────────────────────┐   │  │
│ │ [Search patterns...]        │  │  │ SEQ(                │   │  │
│ │                             │  │  │   login_attempt,    │   │  │
│ │                             │  │  │   transfer,         │   │  │
│ │                             │  │  │   WITHIN(5m)        │   │  │
│ │                             │  │  │ )                   │   │  │
│ │                             │  │  └──────────────────────┘   │  │
│ │                             │  │  [Monaco Editor YAML mode]  │  │
│ │                             │  │                             │  │
│ │                             │  │  [Validate]  [Save Draft]   │  │
│ │                             │  │  [Publish]   [Delete]       │  │
│ └─────────────────────────────┘  └─────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────┘
```

### Features

1. **Monaco Editor** — YAML mode, JSON Schema validation against pattern specification schema
2. **Live validation** — Call `POST /api/v1/patterns/validate` on editor change (debounced)
3. **Inline error markers** — Monaco decorations showing validation errors in gutter
4. **Publish flow** — Saves draft → validates → activates
5. **Pattern list** — Status indicator (green = ACTIVE, grey = DRAFT, red = ERROR)
6. **Test panel** (expandable below editor) — Paste sample events, run pattern against them

### Types (`types/pattern.types.ts`)

```typescript
interface Pattern {
  patternId: string;
  name: string;
  description?: string;
  specification: string; // YAML/DSL string
  status: "DRAFT" | "ACTIVE" | "INACTIVE" | "ERROR";
  agentHints?: string[];
  confidence?: number;
  createdAt: string;
  updatedAt: string;
}
```

---

## Page 6 — HITL (Human-in-the-Loop) Review Queue

**Route**: `/aep/hitl`

### Purpose

Review low-confidence policies or safety-failed skill promotion candidates. Approve or reject,
with a mandatory reason on rejection.

### Layout

```
┌────────────────────────────────────────────────────────────────────┐
│ HITL Review Queue  🔴 3 pending                    [Filters ▼]     │
├────────────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────────┐   │
│ │ 🟡 Policy Candidate — fraud-detection-agent               4m │   │
│ │                                                              │   │
│ │ Proposed procedure:                                          │   │
│ │   IF login_attempt within 30s AND transfer > $1000          │   │
│ │   THEN flag_fraud                                            │   │
│ │                                                              │   │
│ │ Confidence: 0.64 (below threshold 0.70)                     │   │
│ │ Gate failed: SafetyEvaluationGate (score: 0.58)             │   │
│ │ Derived from: 12 episodes (Nov 10 – Mar 11)                 │   │
│ │                                                              │   │
│ │ [Show all 12 source episodes]                               │   │
│ │                                                              │   │
│ │ Review note: [___________________________________]           │   │
│ │                         [✓ Approve]  [✗ Reject]             │   │
│ └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│ ┌──────────────────────────────────────────────────────────────┐   │
│ │ 🔴 Safety Violation — recommender-agent                   1h │   │
│ │ ...                                                          │   │
│ └──────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────┘
```

### Live Queue via SSE

```typescript
// hooks/useHitlQueue.ts
export function useHitlQueue(tenantId: string) {
  const [items, setItems] = useAtom(hitlItemsAtom);

  useEffect(() => {
    const es = new EventSource(`/events/hitl-queue?tenantId=${tenantId}`);
    es.onmessage = (e) => {
      const event = JSON.parse(e.data);
      if (event.type === "REVIEW_REQUESTED") {
        setItems((prev) => [event.item, ...prev]);
      } else if (event.type === "REVIEW_RESOLVED") {
        setItems((prev) => prev.filter((i) => i.itemId !== event.itemId));
      }
    };
    return () => es.close();
  }, [tenantId]);

  return items;
}
```

### Approve/Reject Mutations

```typescript
export function useApproveItem() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, note }: { itemId: string; note: string }) =>
      hitlApi.approveItem(itemId, note),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["hitl"] }),
  });
}
```

### Types (`types/hitl.types.ts`)

```typescript
interface HitlReviewItem {
  itemId: string;
  agentId: string;
  tenantId: string;
  itemType: "POLICY_CANDIDATE" | "SAFETY_VIOLATION" | "LOW_CONFIDENCE";
  proposedProcedure: string; // Human-readable
  confidence: number;
  gateFailed?: string;
  gateScore?: number;
  sourceEpisodeIds: string[];
  createdAt: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  reviewerId?: string;
  reviewNote?: string;
}
```

---

## Page 7 — Learning & Skill Promotion

**Route**: `/aep/learning`

### Purpose

Visibility into the learning loop: how many episodes were consolidated, how many facts and
procedures were extracted, which policies are awaiting approval.

### Layout

```
┌────────────────────────────────────────────────────────────────────┐
│ Learning Loop                         [Run Consolidation Now]       │
├──────────────────────┬─────────────────────────────────────────────┤
│ Agent Selector       │ Consolidation History                        │
│ ┌──────────────────┐ │ ┌─────────────────────────────────────────┐ │
│ │ All agents ▼     │ │ │ Mar 11 11:40  fraud-detection-agent     │ │
│ └──────────────────┘ │ │   +8 facts   +2 procedures   0 conflicts│ │
│                      │ └─────────────────────────────────────────┘ │
│ This week            │ ┌─────────────────────────────────────────┐ │
│ Episodes: 1,204      │ │ Mar 11 11:35  schema-validator-agent    │ │
│ Facts extracted: 89  │ │   +3 facts   +0 procedures   1 conflict │ │
│ Procedures: 12       │ └─────────────────────────────────────────┘ │
│ HITL: 3              │                                              │
│                      │ Skill Promotion Queue                        │
│ [Bar chart]          │ ┌─────────────────────────────────────────┐ │
│  episodes ▓          │ │ [P-012] fraud pattern v3  → PENDING     │ │
│  facts    ▓          │ │         conf 0.82  gate: Safety 0.77 ✓  │ │
│  procs    ▓          │ │         gate: Regression 0.85 ✓         │ │
│                      │ │         [Promote to Production]         │ │
│                      │ └─────────────────────────────────────────┘ │
└──────────────────────┴─────────────────────────────────────────────┘
```

---

## Shared UI Components

### NavBar

```typescript
// components/shared/NavBar.tsx
const NAV_ITEMS = [
  { label: "Pipelines", path: "/aep/pipelines", icon: "⚡" },
  { label: "Agents", path: "/aep/agents", icon: "🤖" },
  { label: "Monitor", path: "/aep/monitoring", icon: "📊" },
  { label: "Patterns", path: "/aep/patterns", icon: "🔍" },
  { label: "HITL", path: "/aep/hitl", icon: "👤", badge: hitlCount },
  { label: "Learning", path: "/aep/learning", icon: "🧠" },
];
```

### TenantSelector

```typescript
// components/shared/TenantSelector.tsx
// Jotai atom: tenantIdAtom — propagates to all queries/SSE connections
export const tenantIdAtom = atom<string>("default");
```

Every API call and SSE connection reads `tenantIdAtom` and passes `tenantId` automatically.

### SseStatus

```typescript
// components/shared/SseStatus.tsx
// Green dot = SSE connected, yellow = reconnecting, grey = disconnected
// Shown in top-right corner of NavBar
```

### AgentStatusBadge

```typescript
// components/agents/AgentStatusBadge.tsx
const STATUS_COLORS = {
  HEALTHY: "bg-green-500",
  DEGRADED: "bg-yellow-500",
  UNHEALTHY: "bg-red-500",
  STARTING: "bg-blue-300",
  STOPPING: "bg-gray-400",
  UNKNOWN: "bg-gray-200",
};
```

### ConfidenceBadge

```typescript
// components/shared/ConfidenceBadge.tsx
// green if >= 0.9, yellow if >= 0.7, red if < 0.7
function getColor(confidence: number) {
  if (confidence >= 0.9) return "text-green-600 bg-green-50";
  if (confidence >= 0.7) return "text-yellow-700 bg-yellow-50";
  return "text-red-700 bg-red-50";
}
```

---

## Routing, State & API Layer

### Router (`router.tsx`)

```typescript
export const router = createBrowserRouter([
  {
    path: '/aep',
    element: <AppLayout />,  // NavBar + TenantSelector + <Outlet>
    children: [
      { index: true, element: <Navigate to="/aep/pipelines" /> },
      { path: 'pipelines',      element: <PipelineListPage /> },
      { path: 'pipelines/new',  element: <PipelineBuilderPage /> },
      { path: 'pipelines/:id',  element: <PipelineBuilderPage /> },
      { path: 'agents',         element: <AgentRegistryPage /> },
      { path: 'agents/:agentId', element: <AgentDetailPage /> },
      { path: 'monitoring',     element: <MonitoringDashboardPage /> },
      { path: 'patterns',       element: <PatternStudioPage /> },
      { path: 'patterns/:id',   element: <PatternStudioPage /> },
      { path: 'hitl',           element: <HitlReviewPage /> },
      { path: 'learning',       element: <LearningPage /> },
    ],
  },
]);
```

### SSE Client (`api/sse.ts`)

> **Alignment note**: The DATACLOUD plan's UI stack uses `@ghatana/realtime`'s `useEventStream` hook
> for all SSE subscriptions. When `@ghatana/realtime` is published, replace `SseClient` + direct
> `EventSource` usage below with `import { useEventStream } from '@ghatana/realtime'` to avoid
> duplicating reconnect/backoff logic across products. Until then, maintain the class below.

```typescript
export class SseClient {
  private es: EventSource | null = null;
  private listeners = new Map<string, Set<(data: unknown) => void>>();
  private reconnectMs = 3_000;

  connect(url: string) {
    this.es = new EventSource(url);
    this.es.onmessage = (e) => {
      const event = JSON.parse(e.data) as {
        type: string;
        [k: string]: unknown;
      };
      this.listeners.get(event.type)?.forEach((fn) => fn(event));
    };
    this.es.onerror = () => {
      this.es?.close();
      setTimeout(() => this.connect(url), this.reconnectMs);
    };
  }

  on(eventType: string, handler: (data: unknown) => void) {
    if (!this.listeners.has(eventType)) {
      this.listeners.set(eventType, new Set());
    }
    this.listeners.get(eventType)!.add(handler);
    return () => this.listeners.get(eventType)?.delete(handler);
  }

  disconnect() {
    this.es?.close();
  }
}
```

### `package.json` additions

```json
{
  "dependencies": {
    "@tanstack/react-query": "^5.67.0",
    "recharts": "^2.14.1",
    "@monaco-editor/react": "^4.7.0"
  }
}
```

---

## 22. Test Plan

### Backend Tests

All async tests MUST extend `EventloopTestBase` from `libs:activej-test-utils`.

| Test Class                              | What It Tests                                                                   |
| --------------------------------------- | ------------------------------------------------------------------------------- |
| `AepAgentAdapterTest`                   | PERCEIVE→ACT→CAPTURE lifecycle; memory episode stored correctly                 |
| `AIAgentOrchestrationManagerImplTest`   | `executeChain` chains Promises without `.getResult()`; status updates correctly |
| `AepContextBridgeTest`                  | Maps all `AgentExecutionContext` fields; no null fields                         |
| `LearningSchedulerTest`                 | Triggers `ConsolidationPipeline` on schedule; skips gracefully on error         |
| `HitlQueueTest`                         | `enqueue` appends to `EventLogStore`; `listPending` returns only PENDING items  |
| `AgentControllerTest`                   | `listAgents` requires `tenantId`; `executeAgent` delegates to `AgentDispatcher` |
| `HitlControllerTest`                    | `approve` changes status; `reject` requires reason                              |
| `AepEnvConfigTest`                      | Returns env value when set; returns default otherwise                           |
| `DataCloudAgentRegistryIntegrationTest` | Register + resolve + list across tenants                                        |

**Correct test pattern:**

```java
@DisplayName("AepAgentAdapter lifecycle tests")
class AepAgentAdapterTest extends EventloopTestBase {

    @Test
    void shouldStoreEpisodeAfterSuccessfulAct() {
        // GIVEN
        MemoryPlane memory = new InMemoryMemoryPlane();
        AgentStepRunner runner = (def, event, ctx) -> List.of(outputEvent());
        AepAgentAdapter adapter = new AepAgentAdapter(testDefinition(), runner, memory);
        AgentContext ctx = testContext();

        // WHEN
        AgentResult<List<Event>> result = runPromise(() ->
            AgentTurnPipeline.of(adapter).execute(inputEvent(), ctx));

        // THEN
        assertThat(result.status()).isEqualTo(AgentResultStatus.SUCCESS);
        List<MemoryItem> episodes = runPromise(() ->
            memory.queryEpisodes(MemoryQuery.builder()
                .agentId("test-agent").limit(10).build()));
        assertThat(episodes).hasSize(1);
        assertThat(episodes.get(0).getType()).isEqualTo(MemoryItemType.EPISODE);
    }
}
```

### Frontend Tests

| Test File                          | What It Tests                                                  |
| ---------------------------------- | -------------------------------------------------------------- |
| `AgentRegistryPage.test.tsx`       | Renders agent list; filter works; navigate to detail           |
| `AgentDetailPage.test.tsx`         | Shows episodes tab; loads facts; loads policies                |
| `HitlReviewPage.test.tsx`          | Shows pending items; approve calls API; reject requires reason |
| `MonitoringDashboardPage.test.tsx` | SSE events update run table; stat cards reflect data           |
| `PatternStudioPage.test.tsx`       | Monaco editor loads; validate calls API; publish flows         |
| `useLivePipelineRuns.test.ts`      | SSE message updates local state; reconnects on error           |
| `useHitlQueue.test.ts`             | SSE message adds item to queue; resolve removes it             |
| `SseClient.test.ts`                | reconnects after `onerror`; listeners receive parsed events    |

---

## 23. New Files to Create

### Java

| File Path                                                                    | Purpose                            |
| ---------------------------------------------------------------------------- | ---------------------------------- |
| `products/aep/platform/.../aep/agent/AepAgentAdapter.java`                   | Bridge AEP→BaseAgent lifecycle     |
| `products/aep/platform/.../aep/agent/AepContextBridge.java`                  | AgentExecutionContext→AgentContext |
| `products/aep/platform/.../aep/catalog/AepOperatorCatalogLoader.java`        | Load operator YAML                 |
| `products/aep/platform/.../aep/learning/LearningScheduler.java`              | Periodic consolidation             |
| `products/aep/platform/.../aep/hitl/HitlQueue.java`                          | Human review queue                 |
| `products/aep/platform/.../aep/hitl/HitlReviewItem.java`                     | Review item record                 |
| `products/aep/platform/.../aep/web/AgentController.java`                     | REST: agent CRUD + execute         |
| `products/aep/platform/.../aep/web/HitlController.java`                      | REST: HITL approve/reject          |
| `products/aep/platform/.../aep/config/EnvConfig.java`                        | Env var helper                     |
| `products/aep/platform/src/main/resources/db/migration/V5__memory_plane.sql` | Memory tables                      |

### TypeScript

| File Path                                                       | Purpose                    |
| --------------------------------------------------------------- | -------------------------- |
| `products/aep/ui/src/router.tsx`                                | React Router v7 routes     |
| `products/aep/ui/src/App.tsx`                                   | Root with NavBar + Outlet  |
| `products/aep/ui/src/pages/AgentRegistryPage.tsx`               | Agent list page            |
| `products/aep/ui/src/pages/AgentDetailPage.tsx`                 | Agent detail + memory tabs |
| `products/aep/ui/src/pages/MonitoringDashboardPage.tsx`         | Live monitoring            |
| `products/aep/ui/src/pages/PatternStudioPage.tsx`               | Pattern editor             |
| `products/aep/ui/src/pages/HitlReviewPage.tsx`                  | HITL review queue          |
| `products/aep/ui/src/pages/LearningPage.tsx`                    | Learning loop visibility   |
| `products/aep/ui/src/pages/PipelineListPage.tsx`                | All pipelines (new)        |
| `products/aep/ui/src/components/shared/NavBar.tsx`              | Top nav                    |
| `products/aep/ui/src/components/shared/TenantSelector.tsx`      | Tenant switcher            |
| `products/aep/ui/src/components/shared/SseStatus.tsx`           | SSE connection indicator   |
| `products/aep/ui/src/components/shared/ConfidenceBadge.tsx`     | Confidence color badge     |
| `products/aep/ui/src/components/agents/AgentCard.tsx`           | Agent list card            |
| `products/aep/ui/src/components/agents/AgentTable.tsx`          | Filterable table           |
| `products/aep/ui/src/components/agents/AgentStatusBadge.tsx`    | Health status dot          |
| `products/aep/ui/src/components/memory/EpisodeTimeline.tsx`     | Episode list               |
| `products/aep/ui/src/components/memory/FactTable.tsx`           | Semantic facts table       |
| `products/aep/ui/src/components/memory/PolicyCard.tsx`          | Procedural policy card     |
| `products/aep/ui/src/components/monitoring/RunTable.tsx`        | Live pipeline runs         |
| `products/aep/ui/src/components/monitoring/AgentHealthGrid.tsx` | Agent health dots          |
| `products/aep/ui/src/components/monitoring/StatCard.tsx`        | KPI stat card              |
| `products/aep/ui/src/components/hitl/ReviewCard.tsx`            | HITL review card           |
| `products/aep/ui/src/components/hitl/ApproveRejectPanel.tsx`    | Approve/reject buttons     |
| `products/aep/ui/src/stores/agents.store.ts`                    | Agent list atoms           |
| `products/aep/ui/src/stores/monitoring.store.ts`                | Run list + SSE state       |
| `products/aep/ui/src/stores/hitl.store.ts`                      | HITL queue atoms           |
| `products/aep/ui/src/api/agents.api.ts`                         | Agent REST calls           |
| `products/aep/ui/src/api/memory.api.ts`                         | Memory REST calls          |
| `products/aep/ui/src/api/hitl.api.ts`                           | HITL REST calls            |
| `products/aep/ui/src/api/sse.ts`                                | SSE client class           |
| `products/aep/ui/src/hooks/useAgents.ts`                        | TanStack Query for agents  |
| `products/aep/ui/src/hooks/useAgentMemory.ts`                   | TanStack Query for memory  |
| `products/aep/ui/src/hooks/usePipelineRuns.ts`                  | SSE + query for runs       |
| `products/aep/ui/src/hooks/useHitlQueue.ts`                     | SSE + query for HITL       |
| `products/aep/ui/src/types/agent.types.ts`                      | Agent domain types         |
| `products/aep/ui/src/types/memory.types.ts`                     | Memory domain types        |
| `products/aep/ui/src/types/hitl.types.ts`                       | HITL domain types          |

---

## 24. Files to Modify

### Java

| File                                           | Change                                                                              |
| ---------------------------------------------- | ----------------------------------------------------------------------------------- |
| `AIAgentOrchestrationManagerImpl.java`         | Fix `.getResult()` bug; use `AgentTurnPipeline`                                     |
| `AIAgentOrchestrationManager.java` (interface) | No change; implementation changes only                                              |
| `AepOrchestrationModule.java`                  | Bind `AgentDispatcher`, `MemoryPlane`, `ConsolidationPipeline`, `LearningScheduler` |
| `AepCoreModule.java`                           | Bind `AgentDefinitionLoader`                                                        |
| `AepConnectorModule.java`                      | Replace all hardcoded strings with `EnvConfig.env()`                                |
| `AepIngressModule.java`                        | Replace hardcoded Redis with `EnvConfig.env()` + `testOnBorrow`                     |
| `AepHttpServer.java`                           | Register `AgentController`, `HitlController`; add SSE routes                        |
| `AepLauncher.java`                             | Call `AepOperatorCatalogLoader.loadFromClasspath()` before HTTP start               |
| `AgentRegistryService.java`                    | Add `TenantId tenantId` to all methods                                              |

### TypeScript

| File                      | Change                                                                         |
| ------------------------- | ------------------------------------------------------------------------------ |
| `main.tsx`                | Replace direct `<PipelineBuilderPage>` with `<RouterProvider router={router}>` |
| `PipelineBuilderPage.tsx` | Add URL param for pipeline ID; add `useQuery` for load; add Run Now button     |
| `pipeline.api.ts`         | Small: add `runPipeline()` and `listPipelines()`                               |
| `pipeline.store.ts`       | Small: add `currentRunAtom`                                                    |
| `package.json`            | Add `@tanstack/react-query`, `recharts`, `@monaco-editor/react`                |

---

## 25. Definition of Done

Code is **complete** for each phase only when:

- [ ] All Java tests pass using `EventloopTestBase` (no `.getResult()` calls)
- [ ] `./gradlew spotlessApply` passes with zero warnings
- [ ] `./gradlew checkstyleMain pmdMain` passes
- [ ] All public classes have JavaDoc with `@doc.type`, `@doc.purpose`, `@doc.layer`,
      `@doc.pattern` tags
- [ ] All promises use `.then()` / `.whenComplete()` chaining — never `.getResult()`
- [ ] All connection strings read from environment via `EnvConfig`
- [ ] Frontend: no `any` types, strict TypeScript compilation
- [ ] Frontend: all new pages have minimum render test (React Testing Library)
- [ ] SSE connections auto-reconnect on error
- [ ] Multi-tenancy: `tenantId` passed on every agent registry, memory, and HITL call
- [ ] GAA memory operations: all episode/fact/procedure writes use `Promise.ofBlocking()`
      wrapping `EventLogStore.append()` or `JdbcMemoryItemRepository.save()`

---

## Phase Execution Order

```
Phase 0 (now) ──► Fix Bug 1 (getResult in executeChainInternal)
                  Fix Bug 2 (tenantId missing in AgentRegistryService)
                  Fix Bug 3 (Redis startup validation)

Phase 1 ────────► AepAgentAdapter + AepContextBridge + AgentTurnPipeline wiring

Phase 2 ────────► AgentDispatcher + CatalogAgentDispatcher + catalog loader

Phase 3 ────────► PersistentMemoryPlane binding + V5 migration + perceive/capture

Phase 4 ────────► LearningScheduler + ConsolidationPipeline + HitlQueue

Phase 5 ────────► DataCloudAgentRegistry binding + startup registration

Phase 6 ────────► EnvConfig + all hardcoded config removal

Phase 7 ────────► AgentController + HitlController + SSE endpoints

Phase 8 ────────► gRPC AgentGrpcService hardening

UI Phase A ─────► Router + NavBar + PipelineListPage + PipelineBuilder URL routing

UI Phase B ─────► AgentRegistryPage + AgentDetailPage (overview + memory tabs)

UI Phase C ─────► MonitoringDashboardPage + SSE hooks + recharts

UI Phase D ─────► PatternStudioPage + Monaco editor + validation

UI Phase E ─────► HitlReviewPage + SSE HITL queue + approve/reject

UI Phase F ─────► LearningPage + consolidation history + skill promotion list
```
