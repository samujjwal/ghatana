# YAPPC AGENTIC PLATFORM — IMPLEMENTATION PLAN TO 10/10

**Objective:** Bring every maturity dimension from its current score to **10/10**.  
**Method:** Each section defines the current gap, a concrete task list, a validation contract, and the exact platform modules to reuse.  
**Dependency note:** Dimensions 3–10 each have security-related pre-conditions satisfied by Dimension 4 tasks. Complete Dimension 4 (Security) first.

---

## Summary Scorecard

| # | Dimension | Current | Target | Phase |
|---|-----------|---------|--------|-------|
| 1 | [Domain Model](#1-domain-model) | 4/10 | 10/10 | 1 |
| 2 | [Agent Framework](#2-agent-framework) | 3/10 | 10/10 | 2 |
| 3 | [Lifecycle Management](#3-lifecycle-management) | 2/10 | 10/10 | 3 |
| 4 | [Security and Governance](#4-security-and-governance) | 1/10 | 10/10 | 1 (FIRST) |
| 5 | [Persistence and Durability](#5-persistence-and-durability) | 2/10 | 10/10 | 1+2 |
| 6 | [Observability](#6-observability) | 2/10 | 10/10 | 1+3 |
| 7 | [Orchestration](#7-orchestration) | 2/10 | 10/10 | 3 |
| 8 | [Config/Runtime Parity](#8-configruntime-parity) | 1/10 | 10/10 | 2+3 |
| 9 | [Knowledge and Memory](#9-knowledge-and-memory) | 2/10 | 10/10 | 2+4 |
| 10 | [Plugin Architecture](#10-plugin-architecture) | 3/10 | 10/10 | 4 |

**Recommended Execution Order: Dimension 4 → Dimension 5 → Dimension 1 → Dimension 8 → Dimension 2 → Dimension 6 → Dimension 3 → Dimension 7 → Dimension 9 → Dimension 10**

---

## 1. Domain Model

**Current: 4/10**

### Why Not 10/10 Today

| Gap | File Evidence | Impact |
|-----|--------------|--------|
| Two `Metric` classes across modules | `services/domain` and `services/infrastructure` have separate metric DTOs | Ambiguous contracts |
| Two knowledge graph implementations | `com.ghatana.yappc.knowledge.*` and `com.ghatana.yappc.kg.*` in same service | Dead code confusion |
| `PersonaMapping` in two places | `PersonaMapping.java` Java class AND `personas.yaml` | Single source of truth violation |
| Aggregate root invariants not enforced | `ProjectEntity.advanceStage()` uses hardcoded map, not `TransitionPolicy` | Domain logic scattered |
| No `AggregateRoot<T>` base class | Domain events emitted ad-hoc | Inconsistent event emission |
| No domain event schema version | `DomainEvent` lacks `schema_version` field | Breaking changes undetectable |
| `YappcDataCloudRepository` returns `Object` in some paths | Type erasure at persistence boundary | Type safety gap |

### Tasks to 10/10

#### 1.1 — Consolidate Domain Classes (1 day)

**Module:** `libs/java/yappc-domain`

```
[ ] 1.1.1  Create canonical Metric.java in libs/java/yappc-domain
           Fields: metricId, name, value, unit, timestamp, projectId, tenantId
           Deprecate both existing Metric classes in services/domain and services/infrastructure
[ ] 1.1.2  Migrate all usages from deprecated Metric to canonical Metric
           grep_search('class Metric', includePattern='products/yappc/**')
[ ] 1.1.3  Remove deprecated Metric classes, verify no compilation errors
```

#### 1.2 — Consolidate Knowledge Graph (half day)

**Module:** `core/knowledge-graph`

```
[ ] 1.2.1  Confirm which KG implementation has DataCloud backing:
           expected: com.ghatana.yappc.knowledge.* (KnowledgeGraphDataCloudPlugin)
[ ] 1.2.2  Mark com.ghatana.yappc.kg.* package as @Deprecated
[ ] 1.2.3  Migrate any KG usages from .kg.* to .knowledge.*
[ ] 1.2.4  Delete .kg.* package
```

#### 1.3 — Authoritative PersonaMapping (half day)

**Module:** `backend/auth`, `config/`

```
[ ] 1.3.1  Create config/personas/personas.yaml as THE authoritative persona definition:
           personas:
             - id: product_manager, displayName: "Product Manager", role: EDITOR
             - id: developer, displayName: "Developer", role: EDITOR
             - ...
[ ] 1.3.2  Implement PersonaLoader.java that reads personas.yaml at startup
           Uses Jackson YAML mapper; exposes Map<String, PersonaDefinition>
[ ] 1.3.3  Refactor PersonaMapping.java to delegate to PersonaLoader (no more hardcoded switches)
[ ] 1.3.4  Write PersonaLoaderTest extending EventloopTestBase
```

#### 1.4 — AggregateRoot Base Pattern (1 day)

**Module:** `libs/java/yappc-domain`

```java
// Implement in libs/java/yappc-domain:
public abstract class AggregateRoot<ID> {
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    protected void raiseEvent(DomainEvent event) {
        uncommittedEvents.add(event);
    }

    public List<DomainEvent> flushEvents() {
        List<DomainEvent> events = List.copyOf(uncommittedEvents);
        uncommittedEvents.clear();
        return events;
    }
}
```

```
[ ] 1.4.1  Implement AggregateRoot<ID> in libs/java/yappc-domain
[ ] 1.4.2  Extend ProjectEntity, WorkspaceEntity, TeamEntity from AggregateRoot
[ ] 1.4.3  Replace ad-hoc event creation in entity methods with raiseEvent()
[ ] 1.4.4  Update JdbcEventRepository to consume events from flushEvents()
[ ] 1.4.5  Write AggregateRootTest verifying event accumulation and flush
```

#### 1.5 — Domain Event Versioning (half day)

**Module:** `libs/java/yappc-domain`

```
[ ] 1.5.1  Add schemaVersion field to DomainEvent interface (default "v1")
[ ] 1.5.2  Add correlationId field for distributed tracing
[ ] 1.5.3  Add causationId (ID of event that caused this event) for lineage
[ ] 1.5.4  Update all DomainEvent implementations to set schemaVersion
[ ] 1.5.5  Add DomainEventRegistry that validates schema versions at startup
```

#### 1.6 — Type-Safe Repository Contracts (half day)

**Module:** `infrastructure/datacloud`

```
[ ] 1.6.1  Type-parameterize YappcDataCloudRepository<T> to enforce <T extends Identifiable>
[ ] 1.6.2  Add compile-time check that collection name matches entity class
[ ] 1.6.3  Run ./gradlew checkstyleMain pmdMain to verify no type-safety warnings
```

### Validation Contract for 10/10

- `./gradlew compileJava` with zero warnings under `-Xlint:all`
- One canonical `Metric` class in `libs/java/yappc-domain`; zero others
- One KG implementation in `core/knowledge-graph`
- `PersonaLoader` reads `personas.yaml`; `PersonaMappingTest` passes asserting all personas loaded
- `ProjectEntityTest.shouldEmitEventsOnPhaseAdvance()` passes (via `AggregateRoot.flushEvents()`)
- Every `DomainEvent` subclass has `schemaVersion` set via `assertThat(event.schemaVersion()).isNotBlank()`

---

## 2. Agent Framework

**Current: 3/10**

### Why Not 10/10 Today

| Gap | Evidence |
|-----|---------|
| 228 agent YAMLs not loaded at startup | `AgentDefinitionLoader` class does not exist |
| `CatalogAgentDispatcher` not connected to JDBC registry | Dispatcher has its own `ConcurrentHashMap` |
| No agent heartbeat or health tracking | `last_heartbeat` column exists in DB but nothing writes to it |
| No execution timeout or retry around `AgentTurnPipeline` | `execute()` wrapperless |
| No parallel agent execution with aggregation | `ParallelAgentExecutor` does not exist |
| Memory not durable | `EventLogMemoryStore` is ConcurrentHashMap |
| No agent execution history | `JdbcTaskStateStore` from platform not wired |
| CAPTURE phase governance stub | `EventLogMemoryStore.applyGovernance()` logs and does nothing |
| No confidence-based autonomy levels | All agents run at same autonomous tier |

### Tasks to 10/10

#### 2.1 — AgentDefinitionLoader (2 days)

**Module:** `core/agents`

```java
/**
 * @doc.type class
 * @doc.purpose Loads all YAML agent definitions at startup and seeds the agent registry
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle perceive
 */
public class AgentDefinitionLoader implements Initializable {

    private final AgentRegistryRepository repository;
    private final CatalogAgentDispatcher dispatcher;
    private final YamlObjectMapper mapper;

    public Promise<Void> loadAll(String configDir) {
        return Promise.ofBlocking(executor, () -> {
            Path registryPath = Paths.get(configDir, "agents/registry.yaml");
            AgentCatalogManifest manifest = mapper.readValue(registryPath, AgentCatalogManifest.class);
            for (AgentEntry entry : manifest.getAgents()) {
                Path defPath = Paths.get(configDir, "agents/definitions", entry.getFile());
                AgentDefinition def = mapper.readValue(defPath, AgentDefinition.class);
                AgentRegistryRecord record = AgentRegistryRecord.fromDefinition(def, tenantId);
                repository.upsert(record);
                registerWithDispatcher(def);
            }
        });
    }
}
```

```
[ ] 2.1.1  Create AgentDefinition.java (Java model for agent YAML)
           Fields: id, name, version, generator{type, model, steps[]},
                   capabilities[], tools[], supervisedBy, escalatesTo,
                   inputSchema, outputSchema
[ ] 2.1.2  Create AgentCatalogManifest.java (model for registry.yaml)
[ ] 2.1.3  Implement AgentDefinitionLoader (see above)
[ ] 2.1.4  Wire AgentDefinitionLoader into YappcLifecycleService.onStart()
           using Promise.ofBlocking(executor, loader.loadAll(configDir))
[ ] 2.1.5  Implement registerWithDispatcher:
           - RULE_BASED generator_type → create RuleBasedTypedAgent wrapper
           - LLM generator_type → create LlmTypedAgent wrapper backed by LLMGateway
           - PIPELINE generator_type → create PipelineTypedAgent wrapper
[ ] 2.1.6  Write AgentDefinitionLoaderTest extending EventloopTestBase
           Asserts all 228 agents loaded into registry after loadAll()
```

#### 2.2 — JDBC Registry ↔ Dispatcher Sync (1 day)

**Module:** `core/agents`

```
[ ] 2.2.1  Add CatalogAgentDispatcher.registerFromRegistry(AgentRegistryRecord) method
[ ] 2.2.2  Implement RegistryReadThroughDispatcher that on cache-miss queries JdbcAgentRegistryRepository
[ ] 2.2.3  Remove direct ConcurrentHashMap construction from CatalogAgentDispatcher
           Use LoadingCache<AgentId, TypedAgent<?,?>> instead
[ ] 2.2.4  Test RegistryReadThroughDispatcherTest: miss → DB read → hit from cache
```

#### 2.3 — Agent Heartbeat Service (half day)

**Module:** `core/agents`

```
[ ] 2.3.1  Implement AgentHeartbeatService
           Uses ActiveJ's ScheduledExecutorService (eventloop.schedule)
           Every 30s: update last_heartbeat and health_status for registered Java agents
[ ] 2.3.2  Wire AgentHeartbeatService into all three service modules
[ ] 2.3.3  Test: advance clock by 31s, assert registry row updated
```

#### 2.4 — Timeout, Retry, and Cancellation (1 day)

**Module:** `core/agents`, `platform/java/agent-resilience` (if exists, else `core/agents`)

```java
// In AgentTurnPipeline (or wrapping class):
public Promise<AgentResult<O>> executeWithPolicy(AgentTurnContext ctx, ResiliencePolicy policy) {
    return pipeline.execute(ctx)
        .timeout(policy.timeout())          // ActiveJ Promise.timeout()
        .then(result -> applyRetry(result, policy));
}
```

```
[ ] 2.4.1  Define ResiliencePolicy record: maxAttempts, backoffMs, timeoutMs
[ ] 2.4.2  Wrap AgentTurnPipeline.execute() in executeWithPolicy()
[ ] 2.4.3  Wire ResiliencePolicy from agent YAML definition (or defaults)
[ ] 2.4.4  Implement CancellationToken: Promise<Void> cancel → sets cancelled flag
[ ] 2.4.5  Test AgentTimeoutTest: mock slow outputGenerator, assertTimeout fires
```

#### 2.5 — Durable Execution History (1 day)

**Module:** `core/agents`, `platform/java/agent-memory`

```
[ ] 2.5.1  Wire JdbcTaskStateStore (from platform/java/agent-memory) into BaseAgent
[ ] 2.5.2  At CAPTURE phase: taskStateStore.recordExecution(turnId, agentId, input, output, duration)
[ ] 2.5.3  At REFLECT phase (fire-and-forget): taskStateStore.updateReflection(turnId, reflection)
[ ] 2.5.4  Implement AgentHistoryApi: GET /api/v1/agents/{id}/history?limit=20
[ ] 2.5.5  Write AgentHistoryTest: run 3 turns, query history, assert 3 records returned
```

#### 2.6 — Parallel Agent Execution (1 day)

**Module:** `core/agents`

```java
/**
 * @doc.type class
 * @doc.purpose Executes multiple agents in parallel using Promise.all()
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class ParallelAgentExecutor {
    public <I, O> Promise<List<AgentResult<O>>> executeAll(
            List<TypedAgent<I, O>> agents, AgentContext ctx, I input) {
        List<Promise<AgentResult<O>>> promises = agents.stream()
            .map(a -> a.process(ctx, input))
            .toList();
        return Promises.all(promises);
    }
}
```

```
[ ] 2.6.1  Implement ParallelAgentExecutor (above)
[ ] 2.6.2  Implement ResultAggregator: majority-vote, confidence-weight, first-wins strategies
[ ] 2.6.3  Wire BudgetGateAgent to cap total agent invocations per pipeline execution
[ ] 2.6.4  Test ParallelAgentExecutorTest: 3 agents, all succeed, aggregation works
[ ] 2.6.5  Test: 1 agent fails, others succeed, failure isolated via AgentResult.isFailure()
```

#### 2.7 — Memory Governance Activation (1 day)

**Module:** `core/agents`, `platform/java/agent-memory`

```
[ ] 2.7.1  Replace EventLogMemoryStore with PersistentMemoryPlane in BaseAgent wiring
           (PersistentMemoryPlane is in platform/java/agent-memory)
[ ] 2.7.2  Wire MemoryRedactionFilter BEFORE PersistentMemoryPlane.storeEpisode()
[ ] 2.7.3  Wire MemorySecurityManager on all PersistentMemoryPlane.retrieveEpisodes() calls
[ ] 2.7.4  Test: store episode with PII field, retrieve, assert PII redacted per filter config
[ ] 2.7.5  Test: store episode for tenant-A, retrieve as tenant-B, assert empty result
```

#### 2.8 — Confidence-Based Autonomy (half day)

**Module:** `core/agents`

```
[ ] 2.8.1  Add AutonomyLevel enum: AUTONOMOUS | SUPERVISED | MANUAL
[ ] 2.8.2  Add confidence score to AgentResult<O>: double confidence [0.0-1.0]
[ ] 2.8.3  Implement AutonomyRouter: if confidence < threshold → raise HumanApprovalRequest
[ ] 2.8.4  Wire AutonomyRouter into AgentTurnPipeline after REASON phase
[ ] 2.8.5  Make confidence thresholds configurable per agent in YAML (confidence_threshold field)
```

### Validation Contract for 10/10

- All 228 agents present in `yappc.agent_registry` table after service start
- `AgentDispatchTest.shouldDispatchYamlDefinedLlmAgent()` passes
- Agent heartbeat row updated within 35s of service start
- `AgentTimeoutTest` passes — times out in <50ms when mock delays 5s
- Three turns in `AgentHistoryApiTest` → three records in history response
- Parallel agent test: 3 concurrent agents resolve independently in `Promise.all()`
- Episode stored with PII → retrieved with PII redacted

---

## 3. Lifecycle Management

**Current: 2/10**

### Why Not 10/10 Today

| Gap | Evidence |
|-----|---------|
| `/api/v1/lifecycle/advance` returns `{"status":"not_implemented"}` | `YappcLifecycleService.java` |
| API controllers not mounted in HTTP routing | `IntentApiController`, `ShapeApiController`, `GenerationApiController` exist but not in servlet |
| Transitions enforced via hardcoded Java map | `ProjectEntity.advanceStage()` |
| Entry/exit criteria from `stages.yaml` not enforced | Stages loaded but never validated |
| Required artifacts from `transitions.yaml` not checked | Transition schema present, no enforcement |
| No durable phase transition record | Phase advance leaves no trace after restart |
| AEP not triggered on lifecycle events | No event bridge |
| Approval gate not wired to phase transitions | `human-in-the-loop-coordinator.yaml` declared, no implementation |

### Tasks to 10/10

#### 3.1 — Mount API Controllers in HTTP Server (1 day)

**Module:** `services/lifecycle/YappcLifecycleService.java`

```java
// In YappcLifecycleService.buildServlet():
return RoutingServlet.create()
    .map(GET,  "/health",                   healthHandler)
    .map(GET,  "/api/v1/lifecycle/phases",  lifecycleApiController::getPhases)
    .map(POST, "/api/v1/lifecycle/advance", lifecycleApiController::advance)
    .map(POST, "/api/v1/intent/analyze",    intentApiController::analyzeIntent)
    .map(POST, "/api/v1/intent/capture",    intentApiController::captureIntent)
    .map(GET,  "/api/v1/intent/history",    intentApiController::getHistory)
    .map(POST, "/api/v1/shape/analyze",     shapeApiController::analyzeContext)
    .map(POST, "/api/v1/generate/code",     generationApiController::generateCode)
    .map(GET,  "/api/v1/generate/status",   generationApiController::getStatus)
    .map("*",  "/api/v1/run/*",             runApiController)
    .map("*",  "/api/v1/observe/*",         observeApiController)
    .map("*",  "/api/v1/learn/*",           learningApiController)
    .map("*",  "/api/v1/evolve/*",          evolutionApiController);
```

```
[ ] 3.1.1  Audit all controllers in core/lifecycle for HTTP integration requirements
[ ] 3.1.2  Wire all controllers in YappcLifecycleService.buildServlet()
[ ] 3.1.3  Verify correct DI injection in LifecycleServiceModule
[ ] 3.1.4  Integration test: POST /api/v1/intent/analyze returns 200 with real LLM
           (mock LLM in test mode via @Named("test") binding)
```

#### 3.2 — AdvancePhaseUseCase (2 days)

**Module:** `services/lifecycle`

```java
/**
 * @doc.type class
 * @doc.purpose Core use case: validates and executes a lifecycle phase transition
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class AdvancePhaseUseCase {

    public Promise<TransitionResult> execute(TransitionRequest request) {
        return Promise.ofBlocking(executor, () -> {
            // 1. Load project from DataCloud
            Project project = projectRepository.findById(request.projectId());

            // 2. Validate transition allowed (loaded from transitions.yaml)
            TransitionSpec allowed = transitionConfig.findTransition(
                project.currentPhase(), request.targetPhase());
            if (allowed == null) throw new InvalidTransitionException(...);

            // 3. Check required artifacts present
            for (String artifactId : allowed.requiredArtifacts()) {
                if (!artifactStore.exists(request.projectId(), artifactId))
                    throw new MissingArtifactException(artifactId);
            }
        })
        // 4. Evaluate policy gates
        .then(ignored -> policyEngine.evaluate(
            PolicyRequest.phaseTransition(project, request.targetPhase())))
        .then(policyResult -> {
            if (!policyResult.allowed()) return Promise.of(TransitionResult.blocked(policyResult));
            // 5. Persist new phase state
            return projectRepository.updatePhase(request.projectId(), request.targetPhase())
                // 6. Emit durable domain event
                .then(ignored -> eventPublisher.publish(PhaseAdvancedEvent.of(project, request)))
                // 7. Return success
                .map(ignored -> TransitionResult.success(request.targetPhase()));
        });
    }
}
```

```
[ ] 3.2.1  Implement TransitionSpec — Java record loaded from transitions.yaml
[ ] 3.2.2  Implement TransitionConfigLoader — reads transitions.yaml at startup
[ ] 3.2.3  Implement AdvancePhaseUseCase (above skeleton)
[ ] 3.2.4  Wire AdvancePhaseUseCase into LifecycleApiController.advance()
[ ] 3.2.5  Write AdvancePhaseUseCaseTest extending EventloopTestBase:
           - Happy path: valid transition → TransitionResult.success()
           - Missing artifact → TransitionResult.blocked(MISSING_ARTIFACT)
           - Policy denied → TransitionResult.blocked(POLICY_GATE)
           - Invalid transition → InvalidTransitionException
```

#### 3.3 — Stage Entry/Exit Criteria Enforcement (1 day)

**Module:** `services/lifecycle`

```
[ ] 3.3.1  Implement StageSpec — Java record for stages.yaml entry
           Fields: id, name, entryCriteria[], exitCriteria[], requiredArtifacts[],
                   agentAssignments[], qualityGates[]
[ ] 3.3.2  Implement StageConfigLoader — reads stages.yaml at startup
[ ] 3.3.3  Implement GateEvaluator.evaluateEntryCriteria(project, stage):
           For each criterion: check artifact present OR previous stage complete OR metric >threshold
[ ] 3.3.4  Inject GateEvaluator into AdvancePhaseUseCase (step between 3 and 4 above)
[ ] 3.3.5  Write GateEvaluatorTest: simulate all criterion types
```

#### 3.4 — AEP Bridge (1 day)

**Module:** `services/lifecycle`

```java
/**
 * @doc.type class
 * @doc.purpose Bridges YAPPC lifecycle events to AEP for durable orchestration
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class AepEventBridge implements DomainEventListener {

    private final DataCloudEventCloudClient aepClient;

    @Override
    public Promise<Void> onEvent(DomainEvent event) {
        if (event instanceof PhaseAdvancedEvent e) {
            return aepClient.publish(AepEvent.fromDomain(e));
        }
        if (event instanceof AgentCompletedEvent e) {
            return aepClient.publish(AepEvent.fromDomain(e));
        }
        return Promise.complete();
    }
}
```

```
[ ] 3.4.1  Implement AepEventBridge (above)
[ ] 3.4.2  Wire AepEventBridge to JdbcEventRepository outbox pattern:
           Poll new events from yappc.events WHERE dispatched=false
           Publish to AEP event cloud, mark dispatched=true
[ ] 3.4.3  Implement YAPPC UnifiedOperators:
           - PhaseTransitionValidatorOperator
           - GateOrchestratorOperator
           - AgentDispatchOperator
           - LifecycleStatePublisherOperator
[ ] 3.4.4  Load lifecycle-management-v1.yaml via AEP PipelineMaterializer at startup
[ ] 3.4.5  Integration test: phase advance → event published → AEP operator triggered
```

#### 3.5 — Human Approval Gate (1.5 days)

**Module:** `backend/api`

```sql
-- New table for durable approval requests
CREATE TABLE yappc.approval_requests (
  id TEXT PRIMARY KEY,
  project_id TEXT NOT NULL,
  requesting_agent_id TEXT,
  approval_type TEXT NOT NULL, -- PHASE_ADVANCE | DEPLOYMENT | RISK_ACCEPTANCE
  context JSONB NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED | EXPIRED
  tenant_id TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  decided_at TIMESTAMPTZ,
  decided_by TEXT,
  expires_at TIMESTAMPTZ
);
```

```
[ ] 3.5.1  Run migration to create yappc.approval_requests table
[ ] 3.5.2  Implement JdbcApprovalRepository with JDBC
[ ] 3.5.3  Implement HumanApprovalService:
           - requestApproval() → create row, push WebSocket notification
           - approve(id, decidedBy) → update row, publish ApprovalDecidedEvent
           - reject(id, reason) → update row, publish ApprovalDecidedEvent
           - pendingFor(projectId) → list PENDING rows
[ ] 3.5.4  Add REST endpoints:
           GET  /api/v1/approvals/pending
           POST /api/v1/approvals/{id}/approve
           POST /api/v1/approvals/{id}/reject
[ ] 3.5.5  Wire AepEventBridge to listen for ApprovalDecidedEvent → resume pipeline
[ ] 3.5.6  Implement HumanInTheLoopCoordinatorAgent as TypedAgent<ApprovalRequest, ApprovalDecision>
[ ] 3.5.7  Test: create request, approve, assert AEP pipeline resumes
```

### Validation Contract for 10/10

- `POST /api/v1/lifecycle/advance` returns `{"status":"ADVANCED","toPhase":"DESIGN"}` after proper setup
- `POST /api/v1/intent/analyze` returns structured intent analysis (not 404)
- Invalid transition returns 422 with `{"error":"INVALID_TRANSITION","from":"IDEATION","to":"DEPLOY"}`
- Missing artifact returns 422 with list of missing artifact IDs
- Phase advance stored in DataCloud `phase_states` collection, visible after restart
- Approval request created → WebSocket push received → approve → AEP event fires
- All 8 lifecycle phases reachable via HTTP

---

## 4. Security and Governance

**Current: 1/10 — Address This Dimension FIRST**

### Why Not 10/10 Today

| Gap | Severity | Evidence |
|-----|---------|---------|
| `DefaultHookExecutor` runs `sh -c <raw_command>` | CRITICAL | `ProcessBuilder("sh", "-c", command)` |
| `ReleaseAutomationManager` unvalidated git args | HIGH | `ProcessBuilder("git", "commit", "-m", commitMessage)` |
| `PolicyEngine` stub — always permits | HIGH | `return Promise.of(true)` |
| `SecurityServiceAdapter` stub — always CLEAN | HIGH | Hardcoded CLEAN/PASS returns |
| No auth filter in YAPPC HTTP services | HIGH | `RoutingServlet` has no filter chain |
| `YappcDataCloudRepository.DEFAULT_TENANT="default"` | HIGH | Shared data across all tenants |
| WebSocket no principal validation | HIGH | `X-Tenant-Id` read as plain string |
| Memory governance stub | MEDIUM | `applyGovernance()` is log+no-op |

### Tasks to 10/10

#### 4.1 — Fix Command Injection (Day 1 — MANDATORY FIRST TASK)

**Module:** `core/scaffold/core`

```java
/**
 * @doc.type class
 * @doc.purpose Executes lifecycle hooks with strict command allowlisting
 * @doc.layer product
 * @doc.pattern Service
 */
public class SafeHookExecutor {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
        "mvn", "gradle", "./gradlew", "npm", "pnpm", "yarn",
        "java", "node", "python3", "pip3"
    );

    private final Path projectRootBase;

    public HookResult execute(HookDefinition hook, Path workingDir) {
        String command = hook.getCommand();

        // 1. Command allowlist validation
        String baseCommand = command.split(" ")[0]; // get first token only for allowlist check
        // But NEVER use sh -c — pass each argument separately
        List<String> cmdArgs = parseHookArgs(hook); // structured field, not raw string concatenation
        String executable = cmdArgs.get(0);
        if (!ALLOWED_COMMANDS.contains(executable.toLowerCase())) {
            throw new SecurityException("Hook command not in allowlist: " + executable);
        }

        // 2. Working directory validation (no path traversal)
        Path resolvedDir = workingDir.toAbsolutePath().normalize();
        if (!resolvedDir.startsWith(projectRootBase.toAbsolutePath().normalize())) {
            throw new SecurityException("Working directory outside project root: " + resolvedDir);
        }

        // 3. Execute without shell interpreter
        ProcessBuilder pb = new ProcessBuilder(cmdArgs);  // List<String>, NOT "sh", "-c"
        pb.directory(resolvedDir.toFile());
        pb.redirectErrorStream(true);
        // 4. Timeout enforcement
        Process p = pb.start();
        boolean finished = p.waitFor(hook.getTimeoutSeconds(), TimeUnit.SECONDS);
        if (!finished) { p.destroyForcibly(); throw new HookTimeoutException(hook.getId()); }

        return new HookResult(p.exitValue(), captureOutput(p));
    }

    private List<String> parseHookArgs(HookDefinition hook) {
        // Hook YAML must define command and args separately:
        // command: mvn
        // args: [clean, test, -Psomething]
        List<String> args = new ArrayList<>();
        args.add(hook.getCommand());
        args.addAll(hook.getArgs()); // List<String> not raw string
        return args;
    }
}
```

```
[ ] 4.1.1  Delete DefaultHookExecutor.java — no refactor, full replacement
[ ] 4.1.2  Implement SafeHookExecutor (above)
[ ] 4.1.3  Update HookDefinition YAML schema: add args: List<String> field,
           deprecate raw string command field
[ ] 4.1.4  Update all hook YAML files to use structured args
[ ] 4.1.5  Write SafeHookExecutorTest:
           - allowlisted command → succeeds
           - non-allowlisted command → SecurityException
           - path traversal working dir → SecurityException
           - timeout exceeded → HookTimeoutException
[ ] 4.1.6  Verify with grep_search that "sh", "-c" does not appear in any ProcessBuilder call
```

#### 4.2 — Fix Git Operation Safety (half day)

**Module:** `core/scaffold/core`

```java
// In ReleaseAutomationManager.java:
private static final Pattern SAFE_COMMIT_MSG = Pattern.compile("^[a-zA-Z0-9 \\-_\\[\\]().,:/]{1,200}$");
private static final Set<String> ALLOWED_REMOTES = loadAllowedRemotes(); // from config

private void validateCommitMessage(String msg) {
    if (!SAFE_COMMIT_MSG.matcher(msg).matches()) {
        throw new SecurityException("Commit message contains unsafe characters");
    }
}

private void validateRemote(String remoteUrl) {
    if (ALLOWED_REMOTES.stream().noneMatch(remoteUrl::startsWith)) {
        throw new SecurityException("Git remote not in allowlist: " + remoteUrl);
    }
}
```

```
[ ] 4.2.1  Add validateCommitMessage() and validateRemote() to ReleaseAutomationManager
[ ] 4.2.2  Load allowed remotes from config/security/allowed-git-remotes.yaml
[ ] 4.2.3  Write ReleaseAutomationManagerSecurityTest:
           - valid message → proceeds
           - shell injection attempt in message → SecurityException
           - unauthorized remote → SecurityException
```

#### 4.3 — Wire Auth Filters (1 day)

**Module:** `services/lifecycle`, `services/ai`, `services/scaffold`

```java
// In each service module, update buildServlet() to wrap with filters:
HttpFilter authChain = HttpFilter.ofFilter(
    new ApiKeyAuthFilter(apiKeyValidator),
    new TenantExtractionFilter(tenantResolver),
    new TenantIsolationEnforcer(TenantIsolationEnforcer.Mode.STRICT)
);

return RoutingServlet.create()
    .map(GET, "/health", healthHandler)      // no auth on health
    .map("*", "/api/*",  authChain.serve(apiServlet));  // auth required for all API
```

```
[ ] 4.3.1  Wire auth filter chain in YappcLifecycleService.buildServlet()
[ ] 4.3.2  Wire auth filter chain in YappcAiService.buildServlet()
[ ] 4.3.3  Wire auth filter chain in YappcScaffoldService.buildServlet()
[ ] 4.3.4  Exclude /health from auth filter on all services
[ ] 4.3.5  Integration test: missing X-API-Key → 401 Unauthorized
[ ] 4.3.6  Integration test: valid X-API-Key with tenant scope → 200 with TenantContext populated
[ ] 4.3.7  Integration test: wrong tenant's resource → 403 Forbidden
```

#### 4.4 — Fix Tenant Isolation Bug (half day)

**Module:** `infrastructure/datacloud`

```java
// YappcDataCloudRepository — fix DEFAULT_TENANT
// BEFORE (wrong):
private static final String DEFAULT_TENANT = "default";

// AFTER (correct):
private String resolveTenantId() {
    String tenantId = TenantContext.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
        throw new TenantContextMissingException(
            "YappcDataCloudRepository requires a tenant context");
    }
    return tenantId;
}
// Replace all uses of DEFAULT_TENANT with resolveTenantId()
```

```
[ ] 4.4.1  Remove DEFAULT_TENANT constant from YappcDataCloudRepository
[ ] 4.4.2  Replace all usages with resolveTenantId() method call
[ ] 4.4.3  Write TenantIsolationTest:
           - Insert doc under tenant-A, query under tenant-B → empty result
           - Insert doc under tenant-A, query under tenant-A → doc returned
           - Query without TenantContext set → TenantContextMissingException
```

#### 4.5 — Implement Real PolicyEngine (2 days)

**Module:** `services/lifecycle`, `config/policies/`

```yaml
# config/policies/lifecycle-policies.yaml
policies:
  - id: phase_advance_policy
    version: "1.0"
    rules:
      - id: require_test_coverage
        description: "Test coverage must be >= 80% before DESIGN → PLANNING transition"
        condition:
          type: METRIC_THRESHOLD
          metric: test_coverage
          operator: GTE
          value: 80
        applies_to:
          transition: { from: DESIGN, to: PLANNING }
        action: BLOCK
      - id: require_security_scan
        description: "Security scan must pass before any production deployment"
        condition:
          type: ARTIFACT_PRESENT
          artifact_id: security_scan_report
        applies_to:
          transition: { to: DEPLOY }
        action: BLOCK
```

```
[ ] 4.5.1  Create config/policies/ directory
[ ] 4.5.2  Write lifecycle-policies.yaml with at least 5 meaningful policy rules
[ ] 4.5.3  Implement PolicyDefinition, PolicyRule, PolicyCondition Java records
[ ] 4.5.4  Implement PolicyConfigLoader — reads all YAML files from config/policies/
[ ] 4.5.5  Implement real PolicyEngine.evaluate():
           - Load applicable rules for PolicyRequest context (transition type, phase, etc.)
           - Evaluate each condition: METRIC_THRESHOLD, ARTIFACT_PRESENT, CONFIDENCE_SCORE
           - Return PolicyResult.allow() or PolicyResult.block(rule, reason)
[ ] 4.5.6  Replace Promise.of(true) stub in services/lifecycle with real PolicyEngine
[ ] 4.5.7  Write PolicyEngineTest:
           - rule matches, condition met → allow
           - rule matches, condition not met → block with rule ID and reason
           - no applicable rules → allow (default-permit behavior, explicit)
```

#### 4.6 — Implement SecurityServiceAdapter (1 day)

**Module:** `infrastructure/datacloud` or new `core/security`

```
[ ] 4.6.1  Implement SecurityScanner interface with scanProject(projectId) → SecurityReport
[ ] 4.6.2  Implement OsvScannerAdapter:
           Calls OSV-Scanner (via SafeHookExecutor, NOT sh -c) if available
           Falls back to StaticAnalysisScanner if not
[ ] 4.6.3  Implement StaticAnalysisScanner:
           Uses OpenRewrite rules to detect known insecure patterns in generated code
[ ] 4.6.4  Replace SecurityServiceAdapter stub with real scanner wiring
[ ] 4.6.5  Write SecurityScannerTest: inject code with known vulnerability, assert VULNERABLE result
```

#### 4.7 — WebSocket Authentication (half day)

**Module:** `backend/websocket`

```java
// WebSocketEndpointImpl.onHandshake():
@Override
public Promise<Void> onHandshake(WebSocketHandshakeContext ctx) {
    String apiKey = ctx.header("X-API-Key");
    if (apiKey == null || apiKey.isBlank()) {
        return ctx.reject(HttpResponse.ofCode(401));
    }
    return apiKeyValidator.validate(apiKey)
        .then(principal -> {
            if (principal == null) return ctx.reject(HttpResponse.ofCode(401));
            TenantContext.scope(principal);
            ctx.setAttribute("principal", principal);
            return ctx.accept();
        });
}
```

```
[ ] 4.7.1  Update WebSocket handshake to validate X-API-Key
[ ] 4.7.2  Reject connections with missing or invalid API key
[ ] 4.7.3  Set TenantContext on successful handshake
[ ] 4.7.4  Propagate principal to all MessageRouter handlers
[ ] 4.7.5  Test: open WebSocket without API key → 401 close frame
```

### Validation Contract for 10/10

- `TestSafety: grep -rn "sh.*-c" products/yappc --include="*.java"` → zero matches
- `TestSafety: grep -rn "DEFAULT_TENANT" products/yappc --include="*.java"` → zero matches
- Auth integration test: API request without key → 401; with wrong tenant → 403
- Policy evaluation test: 5 rules exercised, all with correct allow/block outcomes
- Security scan test: injected vulnerability detected, not CLEAN
- WebSocket 401 test passes
- Tenant isolation DataCloud test passes

---

## 5. Persistence and Durability

**Current: 2/10**

### Why Not 10/10 Today

| Component | Current Backend | Gap |
|-----------|----------------|-----|
| `EventLogMemoryStore` (agent memory) | `ConcurrentHashMap` | Lost on restart |
| `InMemoryArtifactStore` | `ConcurrentHashMap` | Lost on restart |
| `InMemoryEventPublisher` | No persistence | Events lost |
| Alerts, incidents, code reviews, compliance, metrics, traces, logs | InMemory repos | Lost on restart |
| `EmbeddedYAPPCClient` task registry | `ConcurrentHashMap` | Lost on restart |
| `InMemoryAlertRepository` | `ConcurrentHashMap` | Lost on restart |
| Agent episodes | `EventLogMemoryStore` → `ConcurrentHashMap` | Lost on restart |

### Tasks to 10/10

#### 5.1 — Replace InMemoryEventPublisher (1 day)

```
[ ] 5.1.1  Implement JdbcBackedEventPublisher:
           publish(event) → INSERT INTO yappc.events (id, type, payload, tenant_id, dispatched=false)
           Uses JdbcEventRepository (already implemented)
[ ] 5.1.2  Implement OutboxPoller using ActiveJ eventloop.scheduleAtFixedRate():
           SELECT * FROM yappc.events WHERE dispatched=false ORDER BY created_at LIMIT 100
           → publish to JVM event bus → UPDATE dispatched=true
[ ] 5.1.3  Replace InMemoryEventPublisher binding in LifecycleServiceModule
[ ] 5.1.4  Test: publish 3 events, restart service, assert 3 events polled from outbox
```

#### 5.2 — Replace InMemoryArtifactStore (1 day)

```
[ ] 5.2.1  Implement DataCloudArtifactStore:
           store(projectId, artifactId, content) → DataCloud collection 'artifacts'
           retrieve(projectId, artifactId) → DataCloud query
[ ] 5.2.2  Replace InMemoryArtifactStore in core/lifecycle DI module
[ ] 5.2.3  Test: store artifact, simulate restart, retrieve — same content returned
```

#### 5.3 — Replace InMemory Repositories (2 days)

```
[ ] 5.3.1  List all InMemory* repositories:
           grep_search('class InMemory', 'products/yappc/**/*.java')
[ ] 5.3.2  For each InMemory*Repository: verify JDBC counterpart exists in backend/persistence
[ ] 5.3.3  For each without JDBC counterpart: implement JdbcXxxRepository:
           - JdbcAlertRepository → yappc.alerts (should exist based on InMemoryAlertRepository)
           - JdbcIncidentRepository → yappc.incidents
           - JdbcCodeReviewRepository → yappc.code_reviews
           - JdbcComplianceRepository → yappc.compliance
           - JdbcMetricValuesRepository → yappc.metric_values
[ ] 5.3.4  Update each DI module to bind JDBC instead of InMemory
[ ] 5.3.5  For each repository: write JdbcXxxRepositoryTest with real embedded PostgreSQL
           (use TestContainers or embedded postgres fixture)
```

#### 5.4 — EmbeddedYAPPCClient Durability (half day)

```
[ ] 5.4.1  Replace ConcurrentHashMap task registry in EmbeddedYAPPCClient
           with JdbcTaskStateStore.registerTask() / getTask()
[ ] 5.4.2  Implement task status query: GET /api/v1/tasks/{id}/status backed by JDBC
[ ] 5.4.3  Test: submit task, service restart, query task status — RUNNING from DB
```

#### 5.5 — Database Schema Migration Tool (1 day)

```
[ ] 5.5.1  Introduce Flyway or Liquibase for JDBC schema management
           (check if already declared in build — if not, add to libs.versions.toml)
[ ] 5.5.2  Create src/main/resources/db/migration/ with:
           V1__init_core_tables.sql (existing tables)
           V2__agent_memory_tables.sql (memory_items, task_state)
           V3__approval_requests.sql (new approval_requests table)
           V4__policies.sql (new policies table)
[ ] 5.5.3  Run migrations as part of service startup
[ ] 5.5.4  Test: fresh schema → all tables present after migration
```

### Validation Contract for 10/10

- Zero `class InMemory` matches in production code paths (only test helpers)
- All 5 lifecycle phase services write to JDBC-backed storage
- Restart test: stop service, start service, all pre-restart data present
- Artifact survive restart test passes
- Event outbox test: 3 published events → 3 rows in yappc.events with `dispatched=true` after poll
- Flyway migration runs cleanly on fresh PostgreSQL

---

## 6. Observability

**Current: 2/10**

### Why Not 10/10 Today

| Gap | Evidence |
|-----|---------|
| `NoopMetricsCollector` active | `LifecycleServiceModule` binds Noop |
| `AuditLogger.noop()` active | `LifecycleServiceModule` invokes noop factory |
| No OpenTelemetry tracing | No trace spans in any service |
| No audit query API | `JdbcPersistentAuditService` not exposed |
| Agent history not queryable | No HTTP endpoint |
| No pipeline execution lineage | AEP checkpoint data not surfaced |
| No correlation IDs across services | Requests not traceable end-to-end |

### Tasks to 10/10

#### 6.1 — Activate Metrics (half day)

```
[ ] 6.1.1  Add MicrometerMetricsCollector implementation (may exist in platform/java/observability)
           If missing: create it wrapping MeterRegistry (Prometheus-backed)
[ ] 6.1.2  Replace NoopMetricsCollector binding in LifecycleServiceModule, AiServiceModule, ScaffoldServiceModule
[ ] 6.1.3  Add Prometheus scrape endpoint GET /metrics in each service (or unified gateway)
[ ] 6.1.4  Verify IntentServiceImpl metric calls now emit real metrics
[ ] 6.1.5  Add Grafana dashboard config: agent_execution_duration_ms, phase_advance_count, llm_call_latency
[ ] 6.1.6  Test: capture intent, query /metrics, assert intent.capture.duration histogram present
```

#### 6.2 — Activate Durable Audit Logging (1 day)

```
[ ] 6.2.1  Wire JdbcPersistentAuditService (from products/aep) into lifecycle service
           (or implement standalone JdbcAuditService in backend/persistence if AEP dep adds too much)
[ ] 6.2.2  Replace AuditLogger.noop() binding in all service modules
[ ] 6.2.3  Implement AuditQueryService: 
           GET /api/v1/audit/events?projectId=&agentId=&from=&to=&type=&limit=
[ ] 6.2.4  Wire AuditLogger into all 8 phase services
[ ] 6.2.5  Test: capture intent → audit event in DB → queryable via API
```

#### 6.3 — OpenTelemetry Distributed Tracing (2 days)

```
[ ] 6.3.1  Add opentelemetry-java-sdk to libs.versions.toml
           (check if exists via file_search('opentelemetry', 'gradle/**'))
[ ] 6.3.2  Implement TracingContextPropagator:
           on HTTP request: extract W3C traceparent header → set Span
           on HTTP response: inject traceparent header
[ ] 6.3.3  Wire TracingContextPropagator in all service filter chains
[ ] 6.3.4  Add Span.start() / Span.end() around:
           - Each AgentTurnPipeline execution (per-turn span)
           - Each LLM gateway call (per-LLM-call span)
           - Each JDBC query (per-query span via JDBC interceptor)
[ ] 6.3.5  Configure OTLP exporter to Jaeger or Tempo
[ ] 6.3.6  Test: single request → trace visible in Jaeger with all child spans
```

#### 6.4 — Agent History API (half day)

```
[ ] 6.4.1  Implement AgentHistoryController:
           GET /api/v1/agents/{id}/history?limit=20&offset=0
           → query JdbcTaskStateStore (from platform/java/agent-memory)
[ ] 6.4.2  Implement AgentRationaleController:
           GET /api/v1/agents/{id}/rationale/{turnId}
           → query PersistentMemoryPlane for episodic memory of that turn
[ ] 6.4.3  Wire controllers into service HTTP routing
[ ] 6.4.4  Test: execute agent 3 times, query history, assert 3 records with durations
```

#### 6.5 — Correlation ID Propagation (half day)

```
[ ] 6.5.1  Add CorrelationIdFilter to all service filter chains:
           On request: read X-Correlation-ID header or generate UUID
           Add correlation ID to SLF4J MDC: MDC.put("correlationId", id)
           Return X-Correlation-ID in response header
[ ] 6.5.2  Include correlation ID in all audit events, metrics tags, and trace spans
[ ] 6.5.3  Test: request with X-Correlation-ID → same ID in response header and DB audit row
```

#### 6.6 — Health Aggregation API (half day)

```
[ ] 6.6.1  Implement unified health endpoint: GET /health/detailed
           Returns: { services: {lifecycle: UP, ai: UP, scaffold: UP},
                      db: {reachable: true, version: "..."},
                      agents: {registered: 228, healthy: 225} }
[ ] 6.6.2  Fix InfrastructureService.isDatabaseReachable() stub to do real JDBC ping
[ ] 6.6.3  Wire health endpoint into YappcLauncher (as a dedicated health server on :8090)
```

### Validation Contract for 10/10

- `GET /metrics` returns Prometheus text with `yappc_intent_capture_duration_seconds` histogram
- `GET /api/v1/audit/events?projectId=test` returns at least one audit event after any API call
- Jaeger UI shows a trace with agent turn span and LLM call child span
- `GET /api/v1/agents/{id}/history` returns last 20 executions with durations
- Every log line has `correlationId=...` in structured JSON fields
- `GET /health/detailed` shows all services UP and correct agent count

---

## 7. Orchestration

**Current: 2/10**

### Why Not 10/10 Today

| Gap | Evidence |
|-----|---------|
| AEP not integrated | No YAPPC code connects to AEP |
| `DurableWorkflowEngine` not wired | Platform has it, YAPPC doesn't use it |
| `canonical-workflows.yaml` not materialized | Declared only |
| No checkpoint recovery for long-running tasks | `CheckpointAwareExecutionQueue` unused |
| No event-driven operator triggers | `TriggerListener` unused |
| No dead-letter handling | DLQ config in YAML but no Java code |

### Tasks to 10/10

#### 7.1 — YAPPC UnifiedOperator Implementations (2 days)

**Module:** `products/yappc/core/operators` (new package)

```java
/**
 * @doc.type class
 * @doc.purpose Validates that a lifecycle phase transition is allowed
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class PhaseTransitionValidatorOperator extends UnifiedOperator {

    private final TransitionConfigLoader transitionConfig;
    private final GateEvaluator gateEvaluator;

    @Override
    public Promise<OperatorResult> process(OperatorContext ctx) {
        TransitionRequest req = ctx.payload(TransitionRequest.class);
        TransitionSpec spec = transitionConfig.findTransition(req.from(), req.to());
        if (spec == null) return Promise.of(OperatorResult.reject("INVALID_TRANSITION"));
        return gateEvaluator.evaluateEntryCriteria(req.project(), req.to())
            .map(gateResult -> gateResult.passed()
                ? OperatorResult.forward(req)
                : OperatorResult.reject(gateResult.failureReason()));
    }
}
```

```
[ ] 7.1.1  Implement PhaseTransitionValidatorOperator (above)
[ ] 7.1.2  Implement GateOrchestratorOperator:
           - Dispatches to PolicyEngine + GateEvaluator
           - Routes to HumanApprovalService if gate requires human
[ ] 7.1.3  Implement AgentDispatchOperator:
           - Reads agent assignment from StageSpec
           - Dispatches via CatalogAgentDispatcher
           - Emits AgentCompletedEvent on success
[ ] 7.1.4  Implement LifecycleStatePublisherOperator:
           - Persists new phase state to DataCloud
           - Publishes PhaseAdvancedEvent to JdbcBackedEventPublisher
[ ] 7.1.5  Test each operator in isolation with EventloopTestBase
```

#### 7.2 — AEP Pipeline Materialization (1.5 days)

**Module:** `services/lifecycle`

```
[ ] 7.2.1  Add AEP dependency to yappc services/lifecycle gradle module
[ ] 7.2.2  Implement YappcAepPipelineBootstrapper implementing Initializable:
           onStart() → PipelineMaterializer.load("config/pipelines/lifecycle-management-v1.yaml")
           Registers YAPPC operators with AEP registry
[ ] 7.2.3  Wire YappcAepPipelineBootstrapper into LifecycleServiceModule
[ ] 7.2.4  Implement TriggerListenerBootstrap:
           Subscribes AEP TriggerListener to phase.transition events from yappc event cloud
[ ] 7.2.5  End-to-end test: advance phase → JDBC event recorded → AEP triggered →
           PhaseTransitionValidatorOperator executed → state persisted → event published
```

#### 7.3 — Workflow Template Materialization (2 days)

**Module:** `core/agents`

```java
/**
 * @doc.type class
 * @doc.purpose Loads canonical workflow templates and creates DurableWorkflowEngine instances
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle perceive
 */
public class WorkflowMaterializer {

    private final DurableWorkflowEngine workflowEngine;
    private final YamlObjectMapper mapper;

    public Promise<Map<String, WorkflowHandle>> materializeAll(String configDir) {
        return Promise.ofBlocking(executor, () -> {
            Path workflowsDir = Paths.get(configDir, "workflows");
            CanonicalWorkflowsManifest manifest = mapper.readValue(
                workflowsDir.resolve("canonical-workflows.yaml"),
                CanonicalWorkflowsManifest.class);
            Map<String, WorkflowHandle> handles = new LinkedHashMap<>();
            for (WorkflowTemplate template : manifest.getWorkflows()) {
                WorkflowDefinition def = WorkflowDefinition.fromTemplate(template);
                WorkflowHandle handle = workflowEngine.registerDefinition(def);
                handles.put(template.getId(), handle);
            }
            return handles;
        });
    }
}
```

```
[ ] 7.3.1  Implement WorkflowTemplate Java record (from canonical-workflows.yaml)
[ ] 7.3.2  Implement CanonicalWorkflowsManifest (top-level YAML)
[ ] 7.3.3  Implement WorkflowMaterializer (above)
[ ] 7.3.4  Wire WorkflowMaterializer into LifecycleServiceModule.onStart()
[ ] 7.3.5  Implement WorkflowExecutionController:
           POST /api/v1/workflows/{templateId}/start → returns workflowRunId
           GET  /api/v1/workflows/runs/{runId}/status
[ ] 7.3.6  Test: start new-feature workflow, verify DurableWorkflowEngine running it
[ ] 7.3.7  Test: checkpoint recovery — kill mid-step, restart, assert workflow resumes
```

#### 7.4 — Dead-Letter and Error Handling (half day)

```
[ ] 7.4.1  Implement DlqPublisher — writes failed pipeline events to yappc.dlq table
[ ] 7.4.2  Create DLQ management API: GET /api/v1/dlq, POST /api/v1/dlq/{id}/retry
[ ] 7.4.3  Wire DlqPublisher to AEP pipeline error handler
[ ] 7.4.4  Test: inject operator failure → event in DLQ → retry → success
```

### Validation Contract for 10/10

- All 4 YAPPC operators (Validator, Gate, Dispatch, Publisher) registered in AEP at startup
- AEP pipeline materializes from `lifecycle-management-v1.yaml` without error
- `POST /api/v1/workflows/new-feature/start` returns `{workflowRunId: "..."}` and persists to DataCloud
- Workflow survives restart (checkpoint recovery test)
- Dead-letter event appears in DLQ table after operator failure
- DLQ retry successfully processes event

---

## 8. Config/Runtime Parity

**Current: 1/10**

### Why Not 10/10 Today

| Config Asset | Declared | Validated in Build | Loaded at Runtime | Enforced at Runtime |
|-------------|---------|-------------------|------------------|--------------------| 
| 228 agent definitions | ✅ | ✅ (format) | ❌ | ❌ |
| Lifecycle stages | ✅ | ❌ | ❌ | ❌ |
| Lifecycle transitions | ✅ | ❌ | ❌ | ❌ |
| Canonical workflows | ✅ | ❌ | ❌ | ❌ |
| AEP pipelines | ✅ | ❌ | ❌ | ❌ |
| Policy files | ❌ | ❌ | ❌ | ❌ |
| Persona config | ✅ | ❌ | ❌ (Java class) | Partial |

### Tasks to 10/10

#### 8.1 — Build-Time Validation for All Config Assets (2 days)

**Module:** `buildSrc/`

```groovy
// buildSrc: add CustomConfigValidationPlugin or extend existing Gradle tasks

// Task: validate agent registry cross-references
task validateAgentRegistry {
    doLast {
        def registry = yaml.load(file("config/agents/registry.yaml"))
        registry.agents.each { agent ->
            def defFile = file("config/agents/definitions/${agent.file}")
            if (!defFile.exists()) {
                throw new GradleException("Agent definition file missing: ${agent.file}")
            }
            def def = yaml.load(defFile)
            // Validate tools referenced exist
            def.tools.each { toolId ->
                if (!knownTools.contains(toolId)) {
                    throw new GradleException("Agent ${agent.id} references unknown tool: ${toolId}")
                }
            }
        }
    }
}
```

```
[ ] 8.1.1  Implement Gradle task validateAgentRegistry:
           - All 228 agent definition files exist
           - All tools: references resolve to registered tool IDs
           - All supervisedBy: and escalatesTo: references resolve to valid agent IDs
[ ] 8.1.2  Implement Gradle task validateLifecycleConfig:
           - All stage IDs in transitions.yaml exist in stages.yaml
           - All artifact IDs in transitions.yaml exist in artifact-catalog.yaml (or list)
           - All agent assignments in stages.yaml resolve to known agent IDs
[ ] 8.1.3  Implement Gradle task validateWorkflowConfig:
           - All stage references in canonical-workflows.yaml exist in stages.yaml
           - All task references in workflows.yaml resolve to known agent IDs
[ ] 8.1.4  Implement Gradle task validatePipelineConfig:
           - All operator IDs in lifecycle-management-v1.yaml resolve to registered operators
[ ] 8.1.5  Add all validation tasks to check task dependency in build.gradle.kts
[ ] 8.1.6  CI enforces ./gradlew check fails if any config validation fails
[ ] 8.1.7  Test: introduce a deliberate missing file reference, assert build fails with clear message
```

#### 8.2 — Runtime Loaders for All Config Assets (2 days)

```
[ ] 8.2.1  Implement TransitionConfigLoader.load(path) → List<TransitionSpec>
           Wire into AdvancePhaseUseCase
[ ] 8.2.2  Implement StageConfigLoader.load(path) → Map<String, StageSpec>
           Wire into GateEvaluator  
[ ] 8.2.3  Implement AgentDefinitionLoader.loadAll(configDir) → void (done in D2 above)
[ ] 8.2.4  Implement PolicyConfigLoader.loadAll(policiesDir) → List<PolicyDefinition>
           Wire into PolicyEngine
[ ] 8.2.5  Implement WorkflowMaterializer.materializeAll(configDir) → Map<id, WorkflowHandle>
           (done in D7 above)
[ ] 8.2.6  Implement PersonaLoader.load(path) → Map<String, PersonaDefinition>
           (done in D1 above)
[ ] 8.2.7  All loaders must: log loaded item counts, fail fast with clear error on missing/invalid YAML
```

#### 8.3 — Config Hot Reload (1 day)

```
[ ] 8.3.1  Implement ConfigWatchService using Java WatchService API:
           Monitors config/ directory for file changes
           On change: trigger reload for affected loader
[ ] 8.3.2  Design reload contract:
           - PolicyDefinitions: hot reload (low risk)
           - AgentDefinitions: hot add/update (no downtime)
           - Plugins: hot reload via PluginRegistry.reload(id)
           - WorkflowDefinitions: hot add (new templates), NOT hot replace for running workflows
           - StageSpec/TransitionSpec: NOT hot-reloadable (require restart) — log warning if changed
[ ] 8.3.3  Test: update policy YAML while service running → new policy active within 10s
[ ] 8.3.4  Test: update agent YAML while service running → new agent available for dispatch
```

#### 8.4 — Schema Documentation and Versioning (half day)

```
[ ] 8.4.1  Create config/schemas/ directory with JSON Schema files:
           - agent-definition-schema.json
           - lifecycle-stage-schema.json
           - transition-schema.json
           - workflow-template-schema.json
           - pipeline-schema.json
[ ] 8.4.2  Add schema version to all YAML files (apiVersion field)
[ ] 8.4.3  Gradle validation task: validate all YAML files against their JSON schema
[ ] 8.4.4  README.md in config/ directory documenting all config families
```

### Validation Contract for 10/10

- `./gradlew validateAgentRegistry` fails with clear error if any agent file is missing
- `./gradlew validateLifecycleConfig` fails if a stage ID in transitions doesn't exist
- All loaders log success counts at INFO level: "Loaded 228 agent definitions"
- Policy YAML change detected within 10s by FileWatcher, new policy effective
- JSON Schema validation passes for all existing config YAML files

---

## 9. Knowledge and Memory

**Current: 2/10**

### Why Not 10/10 Today

| Layer | Current Backend | Gap |
|-------|----------------|-----|
| Episodic | `ConcurrentHashMap` (EventLogMemoryStore) | No durability |
| Semantic | `ConcurrentHashMap` | No retrieval, no indexing |
| Procedural | `ConcurrentHashMap` | No persistence, no query |
| Knowledge Graph | DataCloud ✅ | No retrieval integration to agents |
| Semantic search | None | `HybridRetriever` (platform) not wired |
| NLQ | DataCloud NLQ ✅ | Not exposed to YAPPC agents |
| Memory governance | Stub | `applyGovernance()` is a no-op |
| Cross-agent knowledge sharing | None | Each agent has isolated memory |
| Organizational memory | None | No cross-project knowledge accumulation |

### Tasks to 10/10

#### 9.1 — Wire PersistentMemoryPlane (1 day)

**Module:** `core/agents`, `platform/java/agent-memory`

```
[ ] 9.1.1  Verify PersistentMemoryPlane constructor dependencies (check platform source)
[ ] 9.1.2  Add platform/java/agent-memory to services/lifecycle Gradle dependencies
[ ] 9.1.3  Create Flyway migration for memory tables:
           V5__agent_memory.sql:
             CREATE TABLE yappc.memory_items (
               id TEXT PRIMARY KEY, agent_id TEXT, memory_type TEXT,
               content JSONB, embedding FLOAT[], metadata JSONB,
               tenant_id TEXT NOT NULL, created_at TIMESTAMPTZ, expires_at TIMESTAMPTZ
             );
[ ] 9.1.4  Wire JdbcMemoryItemRepository with JDBC ConnectionPool
[ ] 9.1.5  Replace EventLogMemoryStore binding in BaseAgent with PersistentMemoryPlane
[ ] 9.1.6  Test: BaseAgent runs turn, episode stored in memory_items table, survives restart
```

#### 9.2 — Wire Memory Governance (half day)

```
[ ] 9.2.1  Wire MemoryRedactionFilter: loaded from config with PII field patterns
[ ] 9.2.2  Wire MemorySecurityManager: enforces tenant isolation on all memory reads
[ ] 9.2.3  Create config/memory/redaction-rules.yaml:
           rules:
             - field: email, action: REDACT
             - field: phone, action: REDACT
             - field: api_key, action: REMOVE
[ ] 9.2.4  Test: store episode with email field → retrieve → email redacted
```

#### 9.3 — Wire HybridRetriever (1 day)

**Module:** `core/agents`, `platform/java/agent-memory`

```
[ ] 9.3.1  Verify HybridRetriever constructor: expects BM25Retriever + DenseVectorRetriever
[ ] 9.3.2  Implement YappcBM25Retriever backed by DataCloud full-text search
[ ] 9.3.3  Implement YappcDenseVectorRetriever backed by DataCloud vector store
           (if DataCloud exposes vector search, use it; else use PostgreSQL pgvector)
[ ] 9.3.4  Wire HybridRetriever as KnowledgeRetrievalTool in agent tool registry
[ ] 9.3.5  Test: store fact (semantic memory), query with natural language string,
           assert relevant fact retrieved in top-3 results
```

#### 9.4 — NLQ as Agent Tool (half day)

**Module:** `infrastructure/datacloud`

```
[ ] 9.4.1  Implement NlqKnowledgeTool as a TypedAgent tool wrapper:
           input: { query: "What is the architecture of module X?" }
           delegates to DataCloud NLQService
           output: { results: [{content, confidence}] }
[ ] 9.4.2  Register NlqKnowledgeTool in YAPPC tool registry
[ ] 9.4.3  Wire into discovery agents in phase 8 (Observe) and phase 10 (Learn)
[ ] 9.4.4  Test: query NLQ tool with natural language → results returned from DataCloud
```

#### 9.5 — Procedural Memory and Policy Learning (1 day)

```
[ ] 9.5.1  Implement JdbcPolicyStore:
           CREATE TABLE yappc.learned_policies (
             id TEXT PRIMARY KEY, agent_id TEXT, name TEXT, description TEXT,
             procedure TEXT, confidence FLOAT, source TEXT, version INT,
             tenant_id TEXT, created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ
           );
[ ] 9.5.2  Wire JdbcPolicyStore as the backing store for PersistentMemoryPlane procedural memory
[ ] 9.5.3  Implement PolicyLearningService:
           After each agent turn: if result confidence > 0.9 → store as learned policy
[ ] 9.5.4  Implement learned policy query: GET /api/v1/agents/{id}/policies
[ ] 9.5.5  Integrate with agent-learning module from platform/java if it exists
[ ] 9.5.6  Test: high-confidence turn → policy stored → queried via API
```

#### 9.6 — Organizational Knowledge Base (1.5 days)

```
[ ] 9.6.1  Implement CrossProjectKnowledgeExtractor extending LearningServiceImpl:
           After each project completes a phase → extract patterns via LLM
           Store patterns in DataCloud `org_knowledge` collection with no project-scope
[ ] 9.6.2  Implement OrganizationalKnowledgeTool: retrieves cross-project patterns
[ ] 9.6.3  Wire OrganizationalKnowledgeTool into phase 1 (Intent) and phase 10 (Learn) agents
[ ] 9.6.4  Test: project A learns X → project B's intent agent retrieves X as knowledge
```

### Validation Contract for 10/10

- Agent turn episode present in `yappc.memory_items` table after execution
- PII redaction test: email field stored → retrieved redacted
- Cross-tenant isolation: tenant-A memory not readable by tenant-B agent
- HybridRetriever test: relevant fact retrieved for natural language query
- NLQ tool test: question about stored entity → answer returned
- Learned policy present in DB after high-confidence agent turn
- Cross-project knowledge: project-A pattern retrieved by project-B agent

---

## 10. Plugin Architecture

**Current: 3/10**

### Why Not 10/10 Today

| Gap | Evidence |
|-----|---------|
| No class loader isolation | Plugins run in service class loader |
| No permission model | Any plugin can do anything |
| No plugin audit | Plugin calls not logged |
| No hot reload | Plugin update requires service restart |
| No plugin versioning enforcement | No `minPlatformVersion` check |
| No ResourceBudget per plugin | Plugin can consume unlimited CPU/memory |
| Plugin tests minimal | ScaffoldService plugin discovery tested, not isolation |

### Tasks to 10/10

#### 10.1 — Plugin Sandbox with ClassLoader Isolation (2 days)

**Module:** `core/framework`

```java
/**
 * @doc.type class
 * @doc.purpose Executes plugins in an isolated ClassLoader with enforced permission model
 * @doc.layer product
 * @doc.pattern Service
 */
public class PluginSandbox {

    public <T> T loadPlugin(PluginDescriptor descriptor, Class<T> contract)
            throws PluginLoadException {
        // 1. Validate platform version compatibility
        if (!platformVersion.isCompatible(descriptor.minPlatformVersion())) {
            throw new PluginIncompatibleException(descriptor.id(), descriptor.minPlatformVersion());
        }

        // 2. Create isolated child ClassLoader
        URLClassLoader pluginClassLoader = new URLClassLoader(
            descriptor.classpath().toArray(URL[]::new),
            PluginSandbox.class.getClassLoader()  // parent: platform only
        );

        // 3. Load and instantiate plugin class
        Class<?> rawClass = pluginClassLoader.loadClass(descriptor.mainClass());
        T instance = contract.cast(rawClass.getDeclaredConstructor().newInstance());

        // 4. Wrap with permission-checking proxy
        return PermissionProxy.wrap(instance, contract, descriptor.permissions());
    }
}
```

```
[ ] 10.1.1  Implement PluginDescriptor record:
            id, version, minPlatformVersion, mainClass, classpath, permissions: PermissionSet
[ ] 10.1.2  Implement PermissionSet record:
            allowedNetworkHosts: List<String>
            allowedFilePaths: List<String>  (relative to sandbox root)
            allowedJavaPackages: List<String>
[ ] 10.1.3  Implement PluginSandbox (above)
[ ] 10.1.4  Implement PermissionProxy using java.lang.reflect.Proxy:
            intercepts method calls, validates SecurityManager-like policies
[ ] 10.1.5  Implement ResourceBudget enforcement:
            maxWallMs via wrapper thread with timeout
            maxMemoryMB via ThreadGroup memory monitoring
[ ] 10.1.6  Write PluginSandboxTest:
            - compliant plugin runs successfully
            - plugin accessing unauthorized network → SecurityException
            - plugin exceeding time budget → PluginTimeoutException
[ ] 10.1.7  Update PluginManager to use PluginSandbox instead of direct instantiation
```

#### 10.2 — Plugin Audit (half day)

```
[ ] 10.2.1  Implement PluginAuditInterceptor:
            Before plugin.initialize() → audit log (agent_id, plugin_id, action=INIT)
            Before plugin.generate() → audit log (agent_id, plugin_id, action=GENERATE, inputHash)
            After completion → audit log (duration, outputHash, status)
[ ] 10.2.2  Wire PluginAuditInterceptor into PluginSandbox wrapping
[ ] 10.2.3  Expose plugin audit: GET /api/v1/plugins/{id}/audit
[ ] 10.2.4  Test: call plugin.generate(), assert 2 audit records (BEFORE, AFTER) in DB
```

#### 10.3 — Plugin Hot Reload (1 day)

```
[ ] 10.3.1  Implement PluginRegistry.reload(pluginId):
            Unload existing ClassLoader (close URLClassLoader)
            Load new JAR from updated classpath
            Re-initialize new instance
            Atomically swap reference (volatile)
[ ] 10.3.2  Wire ConfigWatchService to detect changes in plugin JAR directory
[ ] 10.3.3  Expose reload API: POST /api/v1/plugins/{id}/reload (admin-only, RBAC)
[ ] 10.3.4  Test: reload plugin, assert old class unloaded (no GC reference), new class loaded
[ ] 10.3.5  Test: reload under load — concurrent calls during reload handled gracefully
```

#### 10.4 — Plugin Versioning and Compatibility (half day)

```
[ ] 10.4.1  Add schemaVersion to plugin descriptor YAML: minPlatformVersion, maxPlatformVersion
[ ] 10.4.2  Platform version exposed as PlatformVersion.current() constant
[ ] 10.4.3  PluginSandbox.validateCompatibility() rejects incompatible plugins with clear message
[ ] 10.4.4  Plugin catalog: GET /api/v1/plugins → list all with version and compatibility status
[ ] 10.4.5  Test: load plugin with minPlatformVersion > current → PluginIncompatibleException
```

#### 10.5 — Plugin SDK Documentation (half day)

```
[ ] 10.5.1  Create docs/plugin-sdk/PLUGIN_DEVELOPMENT_GUIDE.md
[ ] 10.5.2  Implement YappcPlugin JavaDoc with @doc tags (all public methods)
[ ] 10.5.3  Create example plugin: products/yappc/examples/sample-build-generator-plugin/
[ ] 10.5.4  Example plugin: generates Maven POM from ProjectDescriptor
[ ] 10.5.5  Integration test for example plugin: runs in sandbox, produces valid POM
```

### Validation Contract for 10/10

- Plugin running in sandbox cannot access filesystem paths outside its allowedFilePaths list
- Plugin exceeding `maxWallMs` → `PluginTimeoutException` caught and logged
- All plugin invocations appear in audit log with BEFORE/AFTER records
- Hot reload test: load plugin V1, reload V2, assert V2 behavior active without restart
- Incompatible plugin (wrong platform version) → rejected with clear error
- `GET /api/v1/plugins` returns descriptor list including versions and compatibility

---

## Master Task Table (All 10 Dimensions)

| ID | Dimension | Task | Effort | Phase |
|----|-----------|------|--------|-------|
| **SECURITY FIRST** | | | | |
| 4.1 | Security | Fix DefaultHookExecutor command injection | M | 1.0 |
| 4.4 | Security | Fix DEFAULT_TENANT tenant isolation bug | S | 1.0 |
| 4.3 | Security | Wire auth filters in all services | S | 1.0 |
| 4.7 | Security | WebSocket handshake auth | S | 1.0 |
| 4.2 | Security | Fix git operation safety | S | 1.0 |
| **PERSISTENCE NEXT** | | | | |
| 5.1 | Persistence | Replace InMemoryEventPublisher | M | 1.1 |
| 5.2 | Persistence | Replace InMemoryArtifactStore | M | 1.1 |
| 5.3 | Persistence | Replace all InMemory*Repositories | L | 1.1 |
| 5.5 | Persistence | Flyway migration tool | M | 1.1 |
| **DOMAIN MODEL** | | | | |
| 1.1 | Domain | Consolidate Metric class | S | 1.2 |
| 1.2 | Domain | Consolidate Knowledge Graph | S | 1.2 |
| 1.3 | Domain | Authoritative PersonaMapping | S | 1.2 |
| 1.4 | Domain | AggregateRoot base class | M | 1.2 |
| 1.5 | Domain | Domain event versioning | S | 1.2 |
| **CONFIG PARITY** | | | | |
| 8.1 | Config | Build-time config validation | L | 1.3 |
| 8.2 | Config | Runtime loaders for all config | L | 1.3 |
| 8.3 | Config | Config hot reload | M | 1.3 |
| **AGENT FRAMEWORK** | | | | |
| 2.1 | Agents | AgentDefinitionLoader (228 agents) | L | 2.0 |
| 2.2 | Agents | JDBC registry ↔ dispatcher sync | M | 2.0 |
| 2.3 | Agents | Agent heartbeat service | S | 2.0 |
| 2.4 | Agents | Timeout, retry, cancellation | M | 2.0 |
| 2.5 | Agents | Durable execution history | M | 2.0 |
| 2.6 | Agents | Parallel agent execution | M | 2.0 |
| 2.7 | Agents | Memory governance activation | M | 2.0 |
| 2.8 | Agents | Confidence-based autonomy | S | 2.0 |
| 4.5 | Security | Real PolicyEngine | L | 2.0 |
| 4.6 | Security | Real SecurityServiceAdapter | L | 2.0 |
| **OBSERVABILITY** | | | | |
| 6.1 | Observability | Activate Micrometer metrics | S | 2.1 |
| 6.2 | Observability | JdbcPersistentAuditService | M | 2.1 |
| 6.3 | Observability | OpenTelemetry tracing | L | 2.1 |
| 6.4 | Observability | Agent history API | S | 2.1 |
| 6.5 | Observability | Correlation ID propagation | S | 2.1 |
| 6.6 | Observability | Health aggregation API | S | 2.1 |
| **LIFECYCLE** | | | | |
| 3.1 | Lifecycle | Mount API controllers | M | 3.0 |
| 3.2 | Lifecycle | AdvancePhaseUseCase | L | 3.0 |
| 3.3 | Lifecycle | Stage entry/exit enforcement | M | 3.0 |
| 3.4 | Lifecycle | AEP event bridge | L | 3.0 |
| 3.5 | Lifecycle | Human approval gate | L | 3.0 |
| **ORCHESTRATION** | | | | |
| 7.1 | Orchestration | YAPPC UnifiedOperator impls | L | 3.1 |
| 7.2 | Orchestration | AEP pipeline materialization | L | 3.1 |
| 7.3 | Orchestration | Workflow template materialization | L | 3.1 |
| 7.4 | Orchestration | DLQ and error handling | M | 3.1 |
| **KNOWLEDGE/MEMORY** | | | | |
| 9.1 | Memory | Wire PersistentMemoryPlane | M | 4.0 |
| 9.2 | Memory | Wire memory governance | S | 4.0 |
| 9.3 | Memory | Wire HybridRetriever | M | 4.0 |
| 9.4 | Memory | NLQ as agent tool | S | 4.0 |
| 9.5 | Memory | Procedural memory and policy learning | M | 4.0 |
| 9.6 | Memory | Organizational knowledge base | L | 4.0 |
| **PLUGINS** | | | | |
| 10.1 | Plugins | ClassLoader isolation sandbox | L | 4.1 |
| 10.2 | Plugins | Plugin audit | S | 4.1 |
| 10.3 | Plugins | Plugin hot reload | M | 4.1 |
| 10.4 | Plugins | Plugin versioning | S | 4.1 |
| 10.5 | Plugins | Plugin SDK documentation | S | 4.1 |

**Effort key:** S = 0.5–1 day · M = 1–2 days · L = 2–4 days · XL = 4–7 days

---

## Final Validation Contract (All 10 Dimensions at 10/10)

```
SYSTEM IS 10/10 WHEN ALL OF THESE PASS:

# Security (Dimension 4)
- grep -rn "sh.*-c" products/yappc --include="*.java" → 0 results
- grep -rn "DEFAULT_TENANT" products/yappc --include="*.java" → 0 results
- curl -X POST /api/v1/lifecycle/advance → 401 (no API key in header)
- curl -H "X-API-Key: valid-key" -X POST /api/v1/lifecycle/advance → 200 (not 401)
- INSERT INTO yappc.projects (tenant_id='A'); SELECT * WHERE tenant_id='B' → 0 rows
- PolicyEngineTest: all 5 rule types evaluated correctly
- SecurityScannerTest: injected vulnerability → VULNERABLE result (not CLEAN)

# Domain Model (Dimension 1)
- javac products/yappc/libs/java/yappc-domain -Xlint:all → 0 warnings
- grep -rn "class Metric" products/yappc --include="*.java" → 1 result only
- PersonaLoaderTest.shouldLoad21Personas() → passes

# Persistence (Dimension 5)
- StopService → StartService → ALL pre-restart data present
- ArtifactStore survives restart test → passes
- OutboxPollerTest: 3 events published → 3 rows with dispatched=true

# Agent Framework (Dimension 2)
- SELECT COUNT(*) FROM yappc.agent_registry → 228 (after startup)
- AgentTimeoutTest: 5s mock → timeout fires in <50ms
- ParallelAgentTest: 3 agents → all resolved independently
- Memory: SELECT FROM yappc.memory_items WHERE agent_id='test-agent' → 1 row after turn

# Lifecycle (Dimension 3)
- POST /api/v1/intent/analyze → 200 with structured response (not 404)
- POST /api/v1/lifecycle/advance {from:IDEATION, to:DESIGN} → 200 success
- POST /api/v1/lifecycle/advance {from:IDEATION, to:DEPLOY} → 422 INVALID_TRANSITION
- Approval: request created → WebSocket push received → approve → AEP event published

# Observability (Dimension 6)
- GET /metrics → 200 with yappc_intent_capture_duration_seconds histogram
- SELECT FROM yappc.audit_events WHERE agent_id IS NOT NULL LIMIT 1 → 1 row
- Jaeger: trace visible with agent turn span + LLM child span
- GET /api/v1/agents/{id}/history → [{turnId, duration, status}] × 3

# Orchestration (Dimension 7)
- POST /api/v1/workflows/new-feature/start → {workflowRunId: "..."}
- GET /api/v1/workflows/runs/{runId}/status → RUNNING (checkpoint in DB)
- Kill service mid-workflow → restart → GET status → RUNNING (resumed from checkpoint)
- Operator failure → DLQ row in yappc.dlq table
- GET /api/v1/dlq → [{id, failureReason}]

# Config Parity (Dimension 8)
- ./gradlew validateAgentRegistry → BUILD SUCCESS (228 agents validated)
- Introduce config error → ./gradlew check → BUILD FAILED with clear message
- Log line at INFO: "Loaded 228 agent definitions from config/agents/registry.yaml"
- Update policy YAML → new policy active within 10s (hot reload)

# Knowledge/Memory (Dimension 9)
- Store episode with email → retrieve → email field redacted
- SELECT FROM yappc.memory_items WHERE memory_type='SEMANTIC' → 1 row after storeFact()
- HybridRetrieverTest: relevant fact retrieved for NL query
- Cross-tenant: tenant-A episode not visible to tenant-B agent
- Org knowledge: project-A pattern → project-B retrieves it

# Plugin Architecture (Dimension 10)
- PluginSandbox: unauthorized filesystem access → SecurityException
- PluginSandbox: >maxWallMs → PluginTimeoutException
- Plugin audit: 2 rows in DB after plugin.generate() invocation
- Hot reload: V2 behavior active after reload without restart
- Incompatible version → PluginIncompatibleException with version details
```

---

## Definition of Done Per Dimension

A dimension is **10/10** when:

1. All tasks in the dimension's section above are marked complete
2. All validation contract checks for that dimension pass in CI
3. `./gradlew test` passes with zero failures related to that dimension
4. JavaDoc + `@doc.*` tags present on all new public classes
5. No checkstyle or PMD warnings introduced
6. `./gradlew spotlessApply` applied and committed

**The platform is production-ready when all 10 dimensions are 10/10.**
