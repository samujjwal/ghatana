# YAPPC Backend Stabilization + Product Lifecycle Intelligence Review

**Date:** 2026-03-09  
**Team:** Principal Platform Architect · AI Systems Architect · Backend Systems Engineer · Agent Architecture Designer · DevOps & Reliability Architect · Product Systems Designer  
**Methodology:** Direct codebase analysis — Java sources, DI modules, migration scripts, platform libraries, YAML configs. No secondary document inference.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Backend System Inventory](#2-backend-system-inventory)
3. [Declarative Config and Control Plane Assessment](#3-declarative-config-and-control-plane-assessment)
4. [Stability Gate Review](#4-stability-gate-review)
5. [Product Lifecycle Capability Assessment](#5-product-lifecycle-capability-assessment)
6. [Agent Architecture Assessment](#6-agent-architecture-assessment)
7. [Human-Agent Interaction Model](#7-human-agent-interaction-model)
8. [Workflow and Orchestration Assessment](#8-workflow-and-orchestration-assessment)
9. [Data & Memory Architecture](#9-data--memory-architecture)
10. [Observability Architecture](#10-observability-architecture)
11. [Plugin Architecture](#11-plugin-architecture)
12. [Security & Governance Model](#12-security--governance-model)
13. [Gap Analysis](#13-gap-analysis)
14. [Target Backend Architecture](#14-target-backend-architecture)
15. [Implementation Roadmap](#15-implementation-roadmap)
16. [Risk Analysis](#16-risk-analysis)
17. [Final System Readiness Score](#17-final-system-readiness-score)

---

## 1. Executive Summary

The YAPPC backend is a Java 21 / ActiveJ application with substantial structural ambition and a dangerous gap between declared architecture and actual runtime behavior. Code exploration reveals the following critical reality:

**The backend runs with no authentication enforcement.** `SecurityMiddleware` exists as a complete, correct class but is never inserted into the `ApiApplication` servlet middleware chain. Every endpoint — including requirements, approvals, audit, AI suggestions, version control, WebSocket — is publicly accessible without any JWT validation. This is a **P0 blocker** for any production deployment.

**More than 14 production-path repositories are in-memory.** Including notification, team, code review, security scan, metrics, alerts, logs, traces, compliance, incidents, and vulnerabilities. Every one of these is lost on process restart. The `DurableWorkflowEngine`, despite its name, uses `InMemoryWorkflowStateStore` as its only implementation.

**An active DI binding conflict exists in production.** `SharedBaseModule` provides `InMemoryRequirementRepository`, `InMemoryAISuggestionRepository`, and `InMemoryWorkspaceRepository`. `ProductionModule` installs `SharedBaseModule` and then re-provides JDBC versions of the same interfaces. ActiveJ's DI behavior under duplicate bindings is undefined for the production runtime, creating a silent corruption risk.

**The AI subsystem is a NoOp unless API keys are explicitly set.** `NoOpLLMGateway` silently returns empty results as the fallback. Multiple GraphQL resolvers have `// STUB: Replace with actual service call` comments. The LLM gateway has no circuit-breaker, retry, cost-governor, or latency protection.

**200+ agent YAML definitions exist but are not loaded at startup.** The `FileBasedCatalog` loader exists in `platform/java/agent-framework`, but no startup wiring in `ApiApplication` or any bootstrap service instantiates and indexes the catalog. Agent capabilities, orchestrators, lifecycle phases, and event routing are declared but not executable.

**RBAC is statically configured in-memory.** `InMemoryRolePermissionRegistry` with default role mappings is used in both dev and production. Tenant-scoped permissions are not stored, not enforced per-tenant, and not observable.

The system is **not ready for production lifecycle intelligence deployment** in its current state. It requires P0 security and persistence repair before any AI or agent capabilities can be safely operated.

---

## 2. Backend System Inventory

### 2.1 Runtime Services

| Service | Module | Status | Notes |
|---|---|---|---|
| HTTP API Server | `backend/api` — `ApiApplication.java` | **Implemented** | ActiveJ HttpServerLauncher, Java 21 |
| JWT Authentication | `backend/api` — `SecurityMiddleware.java` | **Declared but not operational** | Class exists, NOT wired in middleware chain |
| CORS + Error Handling | `backend/api` — `CorsMiddleware`, `GlobalExceptionHandler` | **Implemented** | Wired in servlet chain |
| WebSocket | `backend/api` — `WebSocketController`, `ConnectionManager`, handlers | **Partially wired** | Handlers exist; production durability/fan-out unverified |
| GraphQL | `backend/api` — `GraphQLController` | **Partially wired** | Resolvers are STUB-marked; not calling real services |
| AEP Integration | `backend/api/aep` — `AepService`, `AepClientFactory` | **Partially wired** | LIBRARY/SERVICE mode abstraction complete; actual AEP availability env-dependent |
| Build Executor | `backend/api/build` — `BuildExecutorService` | **Partially wired** | Endpoint wired; underlying execution depth unknown |
| Deployment / Canary | `backend/api/deployment` — `DeploymentController`, `CanaryService` | **Partially wired** | DTOs complete; actual deployment invocation needs verification |
| Scaffold Engine | `core/scaffold` | **Partially wired** | Grpc generated sources exist; runtime integration needs verification |
| Refactorer | `core/refactorer` — `Polyfix`, gRPC server | **Implemented** | Most complete subsystem; A2A protocol, gRPC, codemods |
| Agent Orchestrator (YAPPC domain) | `core/domain/agent` — `AgentOrchestrator`, `AgentRegistry` | **Partially wired** | In-memory registry; not connected to platform agent-registry |

### 2.2 Persistence / Repository Layer

| Repository | Interface | Implementation in Prod | Status |
|---|---|---|---|
| RequirementRepository | `backend/persistence` | **DI CONFLICT** — both `InMemoryRequirementRepository` (SharedBaseModule) and `JdbcRequirementRepository` (ProductionModule) | **Broken** |
| AISuggestionRepository | `backend/persistence` | **DI CONFLICT** — both `InMemoryAISuggestionRepository` (SharedBaseModule) and `JdbcAISuggestionRepository` (ProductionModule) | **Broken** |
| WorkspaceRepository | `backend/persistence` | **DI CONFLICT** — both `InMemoryWorkspaceRepository` (SharedBaseModule) and `JdbcWorkspaceRepository` (ProductionModule) | **Broken** |
| ProjectRepository | `backend/persistence` | `JdbcProjectRepository` | **Implemented** |
| SprintRepository | `backend/persistence` | `JdbcSprintRepository` | **Implemented** |
| StoryRepository | `backend/persistence` | `JdbcStoryRepository` | **Implemented** |
| AgentRegistryRepository | `backend/persistence` | `JdbcAgentRegistryRepository` | **Implemented** |
| AlertRepository | `backend/persistence` | `JdbcAlertRepository` (Prod) | **Implemented** |
| CodeReviewRepository | `backend/persistence` | `JdbcCodeReviewRepository` (Prod) | **Implemented** |
| MetricRepository | `backend/persistence` | `JdbcMetricRepository` (Prod) | **Implemented** |
| IncidentRepository | `backend/persistence` | `JdbcIncidentRepository` (Prod) | **Implemented** |
| LogEntryRepository | `backend/persistence` | No JDBC impl found; InMemory only | **Missing** |
| TraceRepository | `backend/persistence` | No JDBC impl found; InMemory only | **Missing** |
| VulnerabilityRepository | `backend/persistence` | `JdbcVulnerabilityRepository` (Prod) | **Implemented** |
| SecurityScanRepository | `backend/persistence` | `JdbcSecurityScanRepository` (Prod) | **Implemented** |
| ComplianceRepository | `backend/persistence` | `JdbcComplianceRepository` (Prod) | **Implemented** |
| TeamRepository | `backend/persistence` | InMemory only | **Missing JDBC** |
| NotificationRepository | `backend/persistence` | InMemory only | **Missing JDBC** |
| BootstrappingSessionRepository | `backend/persistence` | `JdbcBootstrappingSessionRepository` (Prod) | **Implemented** |
| ChannelRepository | `backend/persistence` | `JdbcChannelRepository` (Prod) | **Implemented** |
| EventRepository | `backend/persistence` | `JdbcEventRepository` (Prod) | **Implemented** |
| VersionRecord | `SharedBaseModule` wires `InMemoryVersionRecord` | In-memory in BOTH dev and prod | **Not durable** |
| AuditService | `DevelopmentModule` wires `InMemoryAuditService`; `ProductionModule` wires `InMemoryAuditQueryService` | **In-memory in production** | **Not durable** |
| AI Workflow Repository | `core/ai` — `InMemoryAiWorkflowRepository`, `InMemoryAiPlanRepository` | In-memory only | **Missing persistence** |

### 2.3 Config / Catalog Layers

| Layer | Location | Volume | Load Status |
|---|---|---|---|
| Agent definitions (YAML) | `config/agents/definitions/**` | 150+ agent YAMLs across 13 lifecycle phases | **Not loaded at startup** |
| Agent registry | `config/agents/registry.yaml` | Registry index | Not loaded at startup |
| Agent capabilities | `config/agents/capabilities.yaml` | Capability taxonomy | Not loaded at startup |
| Agent event routing | `config/agents/event-routing.yaml` | Event-to-agent mappings | Not loaded at startup |
| Agent event schemas | `config/agents/event-schemas/*.json` | 5 JSON schemas | `EventSchemaValidator` loads these — validated |
| Lifecycle stages | `config/lifecycle/stages.yaml` | 10 stages | Partially loaded |
| Lifecycle transitions | `config/lifecycle/transitions.yaml` | Transition rules | Partially loaded |
| Workflow pipelines | `config/pipelines/*.yaml` | 2 pipelines | Not loaded at startup |
| Task domains | `config/tasks/domains/**` | 26+ domain YAMLs | `YamlTaskDefinitionProvider` loads these — implemented |
| OPA policies | `config/policies/core.rego` | 1 Rego file | **Not enforced at runtime** — no OPA engine wired |
| Personas | `config/personas.yaml` | Persona definitions | Partially loaded |
| Domain schema | `config/schemas/domain-definition.schema.json` | JSON schema | Build-time validated |
| Canonical workflows | `config/workflows/canonical-workflows.yaml` | Workflow definitions | Not loaded at startup |

### 2.4 Agent Frameworks

| Framework | Module | Status |
|---|---|---|
| Platform Agent Framework | `platform/java/agent-framework` | **Implemented** — TypedAgent, DeterministicAgent, RuleEngine, FSM, CompositeAgent, AdaptiveAgent, CatalogLoader |
| YAPPC Domain Agent Model | `core/domain/agent` — AbstractAIAgent, AgentOrchestrator | **Partially wired** — not connected to platform framework |
| Agent Memory | `platform/java/agent-memory` | **Implemented** — rich model (episodes, facts, procedures, artifacts); EventLogMemoryStore wired in SharedBaseModule |
| Agent Registry | `platform/java/agent-registry` — `DataCloudAgentRegistry` | **Partially wired** — implementation exists; not the active registry in YAPPC |
| Agent Learning | `platform/java/agent-learning` | **Partially wired** — FeedbackLearningService has STUB markers |
| Agent Dispatch | `platform/java/agent-dispatch` | **Declared but not operational** in YAPPC context |

### 2.5 Workflow / Orchestration Engines

| Engine | Module | Status | Durability |
|---|---|---|---|
| DurableWorkflowEngine | `platform/java/workflow` | **Partially wired** — StateStore interface is pluggable | InMemoryWorkflowStateStore only — **not durable** |
| DAGPipelineExecutor | `platform/java/workflow/pipeline` | **Implemented** | No persistence |
| AiWorkflowService | `core/domain/workflow` | **Partially wired** | InMemoryAiWorkflowRepository |
| ApprovalWorkflowService | `backend/api/service` | **Partially wired** | Backed by AuditService (InMemory) |

### 2.6 Eventing / Outbox Systems

| System | Module | Status |
|---|---|---|
| AEP Event Router | `backend/api/aep` — `YappcAgentEventRouter` | **Partially wired** — requires AEP connectivity |
| Domain Events | `backend/persistence/events` | **Declared** — DomainEvent, EventPublisher interfaces defined; JdbcEventRepository exists |
| InMemoryEventBus | `core/refactorer` | **Implemented** — used within refactorer subsystem only |
| InMemoryEventPublisher | `core/lifecycle` | **Declared** — wraps to AEP; only InMemory impl exists |

### 2.7 External Integrations

| Integration | Mode | Status |
|---|---|---|
| OpenAI LLM | `DevelopmentModule` — conditional on env var `OPENAI_API_KEY` | **Partially wired** — falls back to NoOpLLMGateway |
| Anthropic LLM | `DevelopmentModule` — conditional on env var `ANTHROPIC_API_KEY` | **Partially wired** — falls back to NoOpLLMGateway |
| AEP Service | LIBRARY (in-process) or SERVICE (HTTP) | **Partially wired** — env-configurable |
| Flyway DB Migrations | `FlywayConfiguration` in production | **Implemented** — DB schema managed |
| HikariCP Connection Pool | `DataSourceModule` in production | **Implemented** |
| Prometheus Metrics | `ProductionModule` — `NoopMetricsCollector` unless explicitly overridden | **Partially wired** |

---

## 3. Declarative Config and Control Plane Assessment

### 3.1 Agent YAML Catalog — 150+ Definitions

**Volume:** 15 phase-categorized directories contain definitions for ideation, planning, requirements, architecture, delivery, build, testing, release, operations, learning, enhancement, scaling, and devsecops agents. There are also orchestrator and meta-agent definitions.

**Schema Status:**
- `validateAgentCatalog` Gradle task exists and runs at build time
- Validates capability references, delegation chains, registry cross-references
- This is one of the stronger declarative control plane checks

**Runtime Loading Status:** `platform/java/agent-framework` contains `FileBasedCatalog` and `CatalogLoader`. However, inspection of `ApiApplication`, `ProductionModule`, and `SharedBaseModule` reveals **no startup wiring that instantiates `FileBasedCatalog` for the YAPPC agent definitions**. The agent definitions exist and are validated in the build, but they are not loaded into a running catalog registry that YAPPC can query at runtime.

**Enforcement Status:** Without catalog loading, no agent capability discovery, no agent-to-task routing based on YAML declarations, and no phase-based orchestration is possible at runtime. The YAML catalog is a build-time artifact, not a runtime control plane.

**Classification:** *Validated in build-time checks. Not loaded at startup. Not enforced at runtime.*

### 3.2 Lifecycle Stage and Transition Config

**Location:** `config/lifecycle/stages.yaml`, `config/lifecycle/transitions.yaml`

The lifecycle module (`core/lifecycle`) contains `InMemoryArtifactStore` and `InMemoryEventPublisher`. There is no JDBC-backed lifecycle state persistence. The lifecycle stage model exists in code but lifecycle transitions are not durably tracked.

**Classification:** *Loaded in lifecycle module. Not durably persisted. Not observable across restarts.*

### 3.3 OPA Rego Policy

**Location:** `config/policies/core.rego`

No OPA engine instance or policy evaluation hook was found in `ApiApplication`, any DI module, or any controller. The governance module in `platform/java/governance` contains `PolicyEngine.java` and security filters, but these classes are **not wired into the YAPPC runtime** through either `ProductionModule` or `SharedBaseModule`.

**Classification:** *Declared in file. Not loaded. Not enforced. Policy evaluation does not occur at runtime.*

### 3.4 Event Schemas

**Location:** `config/agents/event-schemas/*.json`

`EventSchemaValidator` in `backend/api/aep` loads and validates these schemas. This is one case where YAML/JSON config is actually operationalized — it's a functional runtime validation of AEP event payloads.

**Classification:** *Loaded at AEP integration startup. Validated at event time. Operational.*

### 3.5 Task Domain Definitions

**Location:** `config/tasks/domains/**` — 26+ YAML files

`YamlTaskDefinitionProvider` in `core/domain/service` loads these via classpath. This is a second genuine case of declarative config becoming runtime behavior.

**Classification:** *Loaded at startup via YamlTaskDefinitionProvider. Partially operational.*

### 3.6 Pipeline YAML

**Location:** `config/pipelines/agent-orchestration-v1.yaml`, `lifecycle-management-v1.yaml`

No class was found loading these at startup. The `DAGPipelineExecutor` in the workflow platform exists but pipelines require programmatic construction — the YAML is not wired into the executor.

**Classification:** *Declared. Not loaded. Not operational.*

### 3.7 Summary Table

| Config Asset | Validated at Build | Loaded at Startup | Enforced at Runtime | Observable |
|---|---|---|---|---|
| Agent YAML definitions | ✅ (validateAgentCatalog) | ❌ | ❌ | ❌ |
| Agent event schemas | ❌ | ✅ | ✅ | Partially |
| Task domain YAMLs | ❌ | ✅ | ✅ | Partially |
| Lifecycle stage/transition YAML | ❌ | Partially | Partially | ❌ |
| Pipeline YAML | ❌ | ❌ | ❌ | ❌ |
| OPA Rego policy | ❌ | ❌ | ❌ | ❌ |
| Canonical workflow YAML | ❌ | ❌ | ❌ | ❌ |
| Agent instance configs | ❌ | ❌ | ❌ | ❌ |

---

## 4. Stability Gate Review

Each criterion is scored **Green / Yellow / Red** and classified as **Implemented / Partially Wired / Declared but Not Operational / Missing.**

### 4.1 Build Integrity — 🟡 Yellow

- Gradle multi-project build is configured with Java 21 toolchain
- `validateAgentCatalog` task validates YAML assets at build time
- Flyway migration management exists for schema evolution
- **Risk:** SharedBaseModule + ProductionModule both provide bindings for the same repository interfaces (`RequirementRepository`, `AISuggestionRepository`, `WorkspaceRepository`). This is a runtime DI binding conflict — ActiveJ may throw or silently pick one, leading to non-deterministic state

**Score: 6/10**

### 4.2 Startup Integrity — 🟡 Yellow

- `ApiApplication` extends `HttpServerLauncher` with `getBusinessLogicModule()` returning `ProductionModule`
- `ProductionModule` installs `SharedBaseModule`, then overlaps three repository bindings
- Flyway runs on startup in production
- `WorkflowAgentInitializer` exists as a service class but startup wiring is unclear
- **Risk:** DI conflict at startup is real; if ActiveJ throws on duplicate binding, production will not start

**Score: 5/10**

### 4.3 Dependency Wiring Integrity — 🔴 Red

- `SecurityMiddleware` is constructed in tests and independently but **never inserted** into the production servlet chain in `ApiApplication.servlet()`
- The middleware chain at line 439: `return new CorsMiddleware(new GlobalExceptionHandler(routingServlet));` — `SecurityMiddleware` absent
- `NoOpLLMGateway` is the default fallback without API keys
- `NoopMetricsCollector` is the production observability default
- Catalog loader for agent definitions not wired

**Score: 3/10 — P0 auth wiring failure**

### 4.4 Auth / AuthZ Enforcement — 🔴 Red — P0 BLOCKER

The primary `SecurityMiddleware` class implements JWT validation and RBAC. It is **not applied** in the production middleware chain.

Evidence:
```java
// ApiApplication.java line 439
return new CorsMiddleware(new GlobalExceptionHandler(routingServlet));
// SecurityMiddleware is ABSENT from this chain
```

Every authenticated endpoint — `/api/requirements`, `/api/audit`, `/api/ai/suggestions`, `/api/workspaces`, `/api/approvals`, `/api/version`, `/api/architecture`, WebSocket (`/ws`), GraphQL (`/graphql`) — is **accessible without authentication**.

Even if SecurityMiddleware were wired, the `InMemoryRolePermissionRegistry` uses hardcoded default role definitions. RBAC is not persisted, not tenant-scoped, and not auditable.

**Score: 0/10 — Catastrophic failure. P0 blocker for any production use.**

### 4.5 Tenant Isolation — 🔴 Red

- `TenantContextExtractor` exists in `backend/api/common`
- `TenantIsolationEnforcer` exists in `platform/java/governance`
- Neither is wired in the production middleware chain
- No evidence that any repository call enforces tenant ID scoping
- Tenant ID is likely extracted but not used to filter queries

**Score: 1/10 — P0 blocker**

### 4.6 Persistence Durability — 🔴 Red

| Domain | Durability |
|---|---|
| Requirements, AISuggestions, Workspaces | JDBC in prod, but DI conflict risks routing to InMemory version |
| Projects, Sprints, Stories, AgentRegistry | JDBC — durable |
| Version history | InMemoryVersionRecord — lost on restart |
| Audit events | InMemoryAuditQueryService — lost on restart |
| AI workflow instances | InMemoryAiWorkflowRepository — lost on restart |
| AI plans | InMemoryAiPlanRepository — lost on restart |
| Team, Notification, Log, Trace | InMemory only — lost on restart |
| Workflow execution state | InMemoryWorkflowStateStore — lost on restart |

**Score: 3/10 — Critical for lifecycle intelligence continuity**

### 4.7 Workflow Durability — 🔴 Red

`DurableWorkflowEngine` is misleadingly named. Its `StateStore` is pluggable but the only implementation is `InMemoryWorkflowStateStore`. Any workflow execution is lost on restart. No checkpoint, replay, or recovery is possible.

**Score: 1/10**

### 4.8 Agent Registry Durability — 🟡 Yellow

`JdbcAgentRegistryRepository` exists in `backend/persistence` and is wired in `ProductionModule`. The platform `DataCloudAgentRegistry` also exists but is not the active registry in YAPPC's DI. The JDBC registry provides persistence, but no YAML catalog entries flow into it at startup.

**Score: 4/10**

### 4.9 Config Correctness — 🟡 Yellow

- YAML agent definitions are schema-validated at build time
- OPA policies are not enforced at runtime
- Pipeline YAML is not loaded
- Task domain YAML is correctly loaded

**Score: 5/10**

### 4.10 Observability Coverage — 🔴 Red

- `NoopMetricsCollector` is the production default. No metrics are emitted unless `MetricsCollector` is explicitly bound to a real implementation.
- Prometheus config (`prometheus.yappc.yml`) exists but the metrics emission point is a NoOp
- No tracing correlation IDs are enforced across the request lifecycle
- `InMemoryTraceRepository` means traces are lost on restart
- Audit trail uses `InMemoryAuditQueryService` in production — not queryable after restart

**Score: 2/10**

### 4.11 Failure Handling — 🟡 Yellow

- `GlobalExceptionHandler` wraps the servlet chain — runtime exceptions return 500 responses
- No circuit breaker on LLM calls
- No retry logic on repository failures
- No compensating transaction support on workflow failures

**Score: 4/10**

### 4.12 Operational Safety — 🔴 Red

- Any unauthenticated caller can POST to `/api/v1/build/execute` and trigger builds on the server
- WebSocket handler at `/ws` has no authentication gate
- GraphQL endpoint at `/graphql` has no authentication gate
- No rate limiting in place
- `BuildExecutorService` runs processes on the server without filesystem sandboxing observed

**Score: 1/10 — Remote code execution risk surface**

### 4.13 Plugin Loading Safety — 🟡 Yellow

`platform/java/plugin` module exists. `NoOpPolyfixHook` is used as the default `PostGenerationHook`. Plugin lifecycle is not sandboxed. No version enforcement on plugin interfaces at runtime.

**Score: 4/10**

### 4.14 External Integration Safety — 🟡 Yellow

LLM calls fall back to `NoOpLLMGateway` silently rather than failing clearly. AEP integration is mode-switched. No timeout or cost protection on LLM invocations beyond basic HTTP client defaults.

**Score: 4/10**

### 4.15 Stability Gate Summary

| Criterion | Score | Classification |
|---|---|---|
| Build Integrity | 6/10 | Partially Wired |
| Startup Integrity | 5/10 | Partially Wired |
| Dependency Wiring | 3/10 🔴 | Declared but Not Operational |
| Auth / AuthZ | **0/10** 🔴 P0 | **Missing from runtime** |
| Tenant Isolation | 1/10 🔴 P0 | Declared but Not Operational |
| Persistence Durability | 3/10 🔴 | Partially Wired |
| Workflow Durability | 1/10 🔴 | Declared but Not Operational |
| Agent Registry | 4/10 🟡 | Partially Wired |
| Config Correctness | 5/10 🟡 | Partially Wired |
| Observability | 2/10 🔴 | Declared but Not Operational |
| Failure Handling | 4/10 🟡 | Partially Wired |
| Operational Safety | **1/10** 🔴 P0 | Missing |
| Plugin Safety | 4/10 🟡 | Partially Wired |
| External Integration Safety | 4/10 🟡 | Partially Wired |

**Overall Stability Score: 3.1 / 10**

**P0 Blockers identified: Authentication bypass, Tenant isolation gap, Remote execution risk, Workflow state loss, Critical DI conflict.**

---

## 5. Product Lifecycle Capability Assessment

The target lifecycle loop has 10 stages. Each is evaluated against current code support.

### Stage 1 — Ideation

**Current Code Support:**
- `RequirementService` + `RequirementsController` — create and query requirements
- `InMemoryRequirementRepository` (or JDBC, if DI conflict resolves correctly)
- Ideation-phase agent definitions exist in YAML (15 agent YAMLs)

**Runtime Support:** Requirements CRUD is functional at API level (assuming auth bypass doesn't block intent).  
**Persistence Support:** Requirements are JDBC-persisted in ProductionModule — **if** the DI conflict resolves to the JDBC binding.  
**Observability:** No lifecycle-event telemetry on requirement creation. Audit service is InMemory.  
**Human Interaction:** REST CRUD API is the only interface.  
**Agent Interaction:** Not connected. Ideation agents in YAML are not loaded or executable.

**Classification: Partially wired (CRUD only). No AI-agent interaction. No lifecycle-stage-aware tracking.**

---

### Stage 2 — Design

**Current Code Support:** `ArchitectureController`, `ArchitectureAnalysisService` exist. Canvas-related WebSocket handlers exist (`CanvasCollaborationHandler`).  
**Persistence:** `ArchitectureAnalysisService` state is in-memory.  
**Agent Interaction:** Design agent YAML exists but not loaded.

**Classification: Partially wired (analysis API). Canvas collaboration partially implemented.**

---

### Stage 3 — Planning

**Current Code Support:** `SprintService`, `StoryService`, `ProjectService`, sprint/story repositories (JDBC). `RailService` provides workspace navigation.  
**Persistence:** JDBC-backed for sprints and stories.  
**Agent Interaction:** Planning orchestrator agent YAML exists but not loaded.  
**Missing:** Capacity model, dependency graph, milestone tracking — no service or repository implementation found.

**Classification: Partially implemented (sprint/story management). Planning intelligence missing.**

---

### Stage 4 — Implementation

**Current Code Support:** `CodeGenerationController`, `CodeGenerationService`, `ScaffoldController/ScaffoldEngine`, `BuildController/BuildExecutorService`. Refactorer subsystem (Polyfix) is the most complete.  
**AI Support:** `CodeGeneratorAgent` exists in `core/domain/agent`. `CodeGenerationToolProvider` has `UnsupportedOperationException` on actual generation logic.  
**Persistence:** Build state is in-memory.

**Classification: Partially wired (scaffold, build trigger). Code generation AI incomplete.**

---

### Stage 5 — Testing

**Current Code Support:** `TestGenerationController/TestGenerationService`, `SecurityTestController`, coverage analysis DTOs.  
**AI Support:** `TestGenerationToolProvider` has multiple `// TODO` markers on test generation flows.  
**Persistence:** No test result repository found.

**Classification: Declared but not operational. No functional test generation pipeline.**

---

### Stage 6 — Deployment

**Current Code Support:** `DeploymentController/DeploymentService`, `CanaryController/CanaryService`, `CanaryDeployment`. Good DTO coverage.  
**Persistence:** Deploy state in-memory.  
**Integration:** Deployment invocation not verified (likely calls external tooling via HTTP/SDK).

**Classification: Partially wired (controller + DTOs). Deployment durability not confirmed.**

---

### Stage 7 — Operation

**Current Code Support:** Metric, alert, incident, log, trace controllers and services exist. Repositories are a mix of JDBC (metrics, alerts, incidents) and InMemory (logs, traces).

**Classification: Partially wired. Some operational data is durable; traces and logs are not.**

---

### Stage 8 — Observation / Monitoring

**Current Code Support:** `DashboardController/DashboardService`. Metric and alert infrastructure exists. Prometheus config exists.  
**Observability Gap:** `NoopMetricsCollector` means no metrics are emitted in default prod configuration.

**Classification: Partially wired (infrastructure). Not operational without metric collector binding.**

---

### Stage 9 — Learning

**Current Code Support:** `FeedbackLearningService` exists in `core/ai`. `StudentAgent`, learning orchestrator YAML.  
**Status:** `FeedbackLearningService` has explicit `// STUB` comments at the core processing, aggregation, and metrics methods.

**Classification: Declared but not operational.**

---

### Stage 10 — Enhancement

**Current Code Support:** `AISuggestionsController/AISuggestionService`, accept/reject suggestion flows. Enhancement orchestrator YAML.  
**AI:** `LLMSuggestionGenerator` uses the wired LLM gateway (which may be NoOp).  
**Persistence:** `InMemoryAISuggestionRepository` in SharedBaseModule — potential DI conflict with JDBC version.

**Classification: Partially wired — AI suggestion loop exists but acceptance/rejection durability is uncertain.**

---

### Lifecycle Stage Summary

| Stage | Code | Runtime | Persistence | Observability | Agent Interaction |
|---|---|---|---|---|---|
| Ideation | Partial | Partial | Partial (DI risk) | ❌ | ❌ |
| Design | Partial | Partial | ❌ | ❌ | ❌ |
| Planning | Partial | Partial | ✅ (sprints/stories) | ❌ | ❌ |
| Implementation | Partial | Partial | ❌ | ❌ | ❌ |
| Testing | Declared | ❌ | ❌ | ❌ | ❌ |
| Deployment | Partial | Partial | ❌ | ❌ | ❌ |
| Operation | Partial | Partial | Partial | Partial | ❌ |
| Observation | Partial | ❌ (NoOp) | ❌ | ❌ | ❌ |
| Learning | Declared | ❌ (STUB) | ❌ | ❌ | ❌ |
| Enhancement | Partial | Partial | Partial (DI risk) | ❌ | ❌ |

---

## 6. Agent Architecture Assessment

### 6.1 What Exists

**Platform Agent Framework (`platform/java/agent-framework`):**
- `Agent` interface, `TypedAgent`, `AgentDescriptor`, `AgentCapabilities`, `AgentConfig`
- `DeterministicAgent` — FSM-based, RuleEngine, Operator pattern
- `CompositeAgent` — aggregates multiple agents
- `AdaptiveAgent` — adapts behavior over time
- `CatalogLoader` + `FileBasedCatalog` — YAML loading infrastructure
- This is a **well-designed, implemented framework**

**YAPPC Domain Agent Model (`core/domain/agent`):**
- `AIAgent` interface, `AbstractAIAgent`, concrete implementations: `CopilotAgent`, `CodeGeneratorAgent`, `AnomalyDetectorAgent`, `PredictionAgent`, `RecommendationAgent`, `SearchAgent`, `SentimentAgent`, `QueryParserAgent`, `WorkflowRouterAgent`, `DocGeneratorAgent`
- `AgentRegistry` and `AgentOrchestrator` — **in-memory Map-based implementations**
- `AgentController` with HTTP routes for agent execution

**Platform Agent Registry (`platform/java/agent-registry`):**
- `DataCloudAgentRegistry` — backed by Data Cloud event store
- `DataCloudCheckpointStore` — checkpoint support
- **Not wired into YAPPC's DI** — the active registry in YAPPC is the domain `AgentRegistry` (in-memory)

### 6.2 Agent Class Coverage

| Agent Class | Code Status | Runtime Status |
|---|---|---|
| Human Agents | No dedicated model — humans interact via REST/WebSocket | REST API partially wired |
| Deterministic Agents | `DeterministicAgent` in platform framework; RuleEngine/FSM complete | Not wired to YAPPC routing |
| Automated Workflow Agents | `WorkflowRouterAgent` in domain; `AiWorkflowService` | InMemory state; partially wired |
| AI Agents | Multiple concrete agents; `LLMSuggestionGenerator`, `RagService` | LLM fallback is NoOp; partially wired |
| Hybrid Human+AI | `ApprovalWorkflowService` for human approval of AI suggestions | Partially wired; InMemory state |

### 6.3 Agent Registration

- YAPPC domain `AgentRegistry` is a `HashMap<AgentName, AIAgent>`. Agents are registered programmatically in startup code.  
- Platform `DataCloudAgentRegistry` would support durable registration but is not the active registry.  
- YAML catalog definitions are not connected to any registry at runtime.

**Status: Partially wired (in-memory domain registry). Platform registry not active.**

### 6.4 Agent Capability Discovery

`CapabilityMatcher` in `core/domain/service` and `core/service` implements scoring-based capability matching. `TaskOrchestrator` uses `CapabilityMatcher` to route tasks to agents.

**This is one of the better-connected sub-systems.** `TaskServiceImpl`, `TaskOrchestrator`, `CapabilityMatcher`, `TaskValidator`, and `TaskAgentAdapter` form a coherent execution unit for task-to-agent dispatch.

**Status: Implemented — capability matching and task routing is real.**

### 6.5 Agent Collaboration

No multi-agent collaboration protocol was found at runtime level. The orchestrator agent YAML definitions declare delegation chains, but no implementation loads or enforces these chains at runtime. Agents operate in isolation.

**Status: Declared (YAML). Not operational (runtime).**

### 6.6 Agent State Persistence

- Domain agents are stateless per invocation
- `EventLogMemoryStore` is wired in SharedBaseModule for agent memory — this may use Event Sourcing if backed by a log store, but the implementation name suggests in-process storage
- No cross-restart agent state continuity

**Status: Partially wired. Memory model exists; durability uncertain.**

### 6.7 Agent Auditability

No agent execution trace is persisted durably. `TraceRepository` is `InMemoryTraceRepository`. Agent invocations are logged via SLF4J but not to a queryable audit store.

**Status: Missing in production conditions.**

---

## 7. Human-Agent Interaction Model

### 7.1 Current State

Human interaction with agents occurs through:
1. **REST API endpoints** — `/api/requirements`, `/api/ai/suggestions`, `/api/approvals`, `/api/build` etc.
2. **WebSocket** — `/ws` for real-time updates; `CanvasCollaborationHandler`, `ChatHandler`, `NotificationHandler`
3. **GraphQL** — `/graphql` — resolvers are STUB-marked, not calling real services

The `ApprovalController/ApprovalWorkflowService` implements a human approval loop for AI suggestions — this is the most complete human-in-the-loop interaction pattern.

### 7.2 Missing Capabilities

| Capability | Status |
|---|---|
| Structured task submission to AI agents | Partially wired (TaskService/AgentController) |
| Conversational AI interface | Declared (ChatHandler); not backed by conversation state |
| Approval and override of agent actions | Partially wired (ApprovalWorkflowService; InMemory) |
| Escalation paths | Not implemented |
| Explanation / transparency | Not implemented |
| Real-time agent progress visibility | WebSocket infrastructure exists; not connected to agent execution events |

### 7.3 Target Design (Recommended)

```
Human → REST or WebSocket command →
  TaskSubmissionService →
    TaskOrchestrator (CapabilityMatcher) →
      AgentExecution (with progress events emitted to EventBus) →
        WebSocket push to human →
          Human approval gate (if required) →
            ApprovalWorkflowService (durable) →
              Agent continuation or override
```

This pattern is architecturally achievable with existing components if the missing links (auth, durability, event push) are resolved.

---

## 8. Workflow and Orchestration Assessment

### 8.1 Platform Workflow Engine

`DurableWorkflowEngine` in `platform/java/workflow`:
- Accepts `Workflow` with `WorkflowStep` list
- Executes steps sequentially or with retry
- `StateStore` interface is properly abstracted
- **Only implementation: `InMemoryWorkflowStateStore`**
- Name is aspirationally "Durable" but is not

`DAGPipelineExecutor` in `platform/java/workflow/pipeline`:
- Builds a `DAG` of `PipelineNode` entries
- Topological sort + parallel execution in dependency order
- No persistence — in-memory execution graph only
- This is a **solid execution engine** for ephemeral pipelines

### 8.2 YAPPC AI Workflow Service

`AiWorkflowService` in `core/domain/workflow`:
- Manages `AiWorkflowInstance` lifecycle
- Backed by `InMemoryAiWorkflowRepository` and `InMemoryAiPlanRepository`
- `AiWorkflowServiceTest` exists and tests the service behavior

**Status: Well-designed service, completely non-durable.**

### 8.3 Missing Orchestration Capabilities

| Capability | Status |
|---|---|
| Durable workflow state | Missing — only InMemory implementations |
| Replay and recovery | Missing |
| Human intervention checkpoints | Partially wired (ApprovalService) |
| Compensation / rollback | Missing |
| Parallel step execution | Implemented in DAGPipelineExecutor |
| Timeout handling | Missing |
| Audit trail on workflow steps | Missing |
| Long-duration workflow support | Missing |

### 8.4 Target Design

For production lifecycle orchestration, a `JdbcWorkflowStateStore` must be implemented and registered as the active `DurableWorkflowEngine.WorkflowStateStore`. The pipeline YAML config should be loaded at startup and pipeline instances created from the `PipelineBuilder` API.

---

## 9. Data & Memory Architecture

### 9.1 Current Persistence Map

| Data Domain | Persistent? | Store Type | Notes |
|---|---|---|---|
| Requirements | Partially | JDBC (if DI resolves correctly) | DI conflict risk |
| Projects / Sprints / Stories | ✅ | JDBC | Solid |
| AI Suggestions | Partially | JDBC (if DI resolves correctly) | DI conflict risk |
| Workspaces | Partially | JDBC (if DI resolves correctly) | DI conflict risk |
| Version History | ❌ | InMemory | Lost on restart |
| Audit Events | ❌ | InMemory (prod) | Non-queryable after restart |
| AI Workflow Instances | ❌ | InMemory | Lost on restart |
| Agent Execution Traces | ❌ | InMemory | Lost on restart |
| Workflow Execution State | ❌ | InMemory | Lost on restart |
| Team / Notification | ❌ | InMemory | Lost on restart |
| Agent Memory (episodes/facts) | ❌ | EventLogMemoryStore (in-proc) | Unknown durability |
| Knowledge Graph | ❌ | InMemoryKgService | Lost on restart |

### 9.2 Agent Memory Model

Platform `agent-memory` module is rich and correct:
- `MemoryItem` with typed artifact model (Decision, Lesson, Observation, ToolUse, Entity)
- `EnhancedEpisode` — turn-level memory records
- `EnhancedFact` — versioned semantic memory
- `EnhancedProcedure` — learned procedure steps
- `MemoryAwareBaseAgent` — base class for memory-integrated agents

The model is excellent. The storage is not durable in YAPPC's current wiring.

### 9.3 Knowledge Graph

`InMemoryKgService` — in-memory graph with nodes and edges. `KgService` interface is well-designed. No graph database backend (Neo4j/ArangoDB/etc.) is present.

**Status: In-memory only. Not multi-tenant. Not queryable across restarts.**

---

## 10. Observability Architecture

### 10.1 Current State

**Metrics:**
- `MetricsCollector` interface in `platform/java/observability`
- `NoopMetricsCollector` is the default in both dev and production unless explicitly overridden
- Prometheus config file exists but metrics are not being emitted to it
- `MetricsCollectorFactory` exists but the factory instantiation is not observed wired to a real backend

**Tracing:**
- `TraceService` and `InMemoryTraceRepository` — traces are stored in memory only
- No OpenTelemetry trace exporter wired

**Logging:**
- SLF4J/LogBack throughout — this is functional
- Log entries stored in `InMemoryLogEntryRepository` — lost on restart

**Audit:**
- `AuditController` with record/query endpoints exists
- `InMemoryAuditQueryService` is the production implementation — audit queries return nothing after a restart

### 10.2 Missing Observability Capabilities

| Capability | Status |
|---|---|
| Real-time metrics emission | Missing (NoOp default) |
| Distributed tracing (OTEL) | Missing |
| Durable audit trail | Missing |
| Agent execution telemetry | Missing |
| Lifecycle stage transition events | Missing |
| Correlation IDs across request chain | Not enforced |
| Tenant-scoped observability | Not implemented |
| SLI/SLO tracking | Missing |

---

## 11. Plugin Architecture

### 11.1 Current State

- `platform/java/plugin` module exists
- `PostGenerationHook` interface in `core/scaffold`
- `NoOpPolyfixHook` as default implementation
- Plugins are loaded via classpath — no runtime isolation, no version enforcement, no sandboxing

### 11.2 Agent Tool Provider Pattern

`CodeGenerationToolProvider` and `TestGenerationToolProvider` in `core/agents` implement tool-based extension. This is the most complete plugin-adjacent pattern — tools can be injected into agent contexts.

### 11.3 Missing Plugin Capabilities

| Capability | Status |
|---|---|
| Runtime plugin loading | Missing |
| Plugin sandboxing | Missing |
| Plugin versioning | Missing |
| Plugin health monitoring | Missing |
| Safe plugin unloading | Missing |

---

## 12. Security & Governance Model

### 12.1 P0: Authentication Not Enforced

```
ApiApplication.servlet() middleware chain:
  CorsMiddleware
    └── GlobalExceptionHandler
          └── RoutingServlet  ← SecurityMiddleware NOT present
```

**Fix Required:** Thread `SecurityMiddleware` between `GlobalExceptionHandler` and `RoutingServlet`:

```java
// BEFORE (current insecure state)
return new CorsMiddleware(new GlobalExceptionHandler(routingServlet));

// AFTER (secure)
return new CorsMiddleware(
    new GlobalExceptionHandler(
        SecurityMiddleware.create(jwtTokenProvider, routingServlet)));
```

### 12.2 P0: RBAC In-Memory Only

`InMemoryRolePermissionRegistry` with hardcoded default role mappings. No tenant-scoped roles. No persistence. No observable RBAC decisions.

**Fix Required:** Implement `JdbcRolePermissionRegistry` with tenant-ID-scoped role assignment. Load from database at startup, cache with invalidation on role changes.

### 12.3 P0: Tenant Isolation Not Enforced

`TenantContextExtractor` extracts tenant from JWT claims (after auth is wired). `TenantIsolationEnforcer` exists in governance module. Neither is applied to repository calls.

**Fix Required:** Add tenant predicate to every repository query. Enforce at the middleware layer and repository layer.

### 12.4 P1: OPA Policy Not Enforced

`config/policies/core.rego` defines governance policies. No OPA engine is instantiated at runtime. The governance platform library has `PolicyEngine` but it is not wired.

### 12.5 P1: Secret Handling

`JwtTokenProvider` and `GatewayJwtTokenProvider` read from environment variables — appropriate. LLM API keys are read from environment. No secret manager integration observed.

### 12.6 P1: Build Execution Safety

`BuildController` at `/api/v1/build/execute` triggers server-side builds without authentication (P0 above) and without filesystem isolation. This is a remote code execution risk surface.

---

## 13. Gap Analysis

### P0 Blockers — Must Fix Before Any Production Use

| # | Gap | Impact | File / Module |
|---|---|---|---|
| P0-1 | SecurityMiddleware not in servlet chain | All endpoints unauthenticated | `backend/api/ApiApplication.java:439` |
| P0-2 | Tenant isolation not enforced | Cross-tenant data leakage possible | `backend/api/common/TenantContextExtractor.java`, all repositories |
| P0-3 | Build endpoint unauthenticated + unsandboxed | Remote code execution | `backend/api/build/BuildController.java`, `BuildExecutorService.java` |
| P0-4 | DI binding conflict (SharedBaseModule + ProductionModule) | Non-deterministic repository binding for Requirements, AISuggestions, Workspaces | `backend/api/config/SharedBaseModule.java:118-136`, `ProductionModule.java:244-259` |
| P0-5 | InMemoryAuditQueryService in production | Audit trail lost on restart; compliance violation | `backend/api/config/ProductionModule.java:190` |
| P0-6 | InMemoryWorkflowStateStore in DurableWorkflowEngine | All workflow executions lost on restart | `platform/java/workflow/engine/DurableWorkflowEngine.java:272` |

### P1 Stability Gaps — Fix Before Lifecycle Intelligence

| # | Gap | Impact | Module |
|---|---|---|---|
| P1-1 | RBAC in-memory only | No durable/tenant-scoped permissions | `backend/api/security/SecurityConfig.java`, `platform/java/security/rbac` |
| P1-2 | VersionService uses InMemoryVersionRecord in production | Version history lost on restart | `backend/api/config/SharedBaseModule.java:103-107` |
| P1-3 | AI workflow state (AiWorkflowRepository, AiPlanRepository) in-memory | Workflow continuity broken | `core/domain/workflow` |
| P1-4 | Team/Notification repositories in-memory only | Team composition lost on restart | `backend/persistence/repository/inmemory` |
| P1-5 | Trace/Log repositories in-memory only | Operational telemetry not durable | `backend/persistence/repository/inmemory` |
| P1-6 | NoopMetricsCollector as production default | No metrics emitted | `ProductionModule.java:203-206` |
| P1-7 | Agent YAML catalog not loaded at startup | 150+ agent definitions unreachable at runtime | `ApiApplication.java`, no catalog loader instantiation |
| P1-8 | Pipeline YAML not loaded | Declared pipelines not executable | `config/pipelines`, no loader found |
| P1-9 | OPA Rego policy not enforced | Policy governance is theater | `config/policies/core.rego`, `platform/java/governance` |
| P1-10 | GraphQL resolvers are STUB | GraphQL API returns empty/fake data | `core/ai/src`.../graphql/resolver/*.java |
| P1-11 | InMemoryKnowledgeGraph | No cross-restart knowledge persistence | `core/refactorer/server/kg/InMemoryKgService.java` |
| P1-12 | WebSocket connection state in-memory | Connection state lost on restart | `backend/api/websocket/ConnectionManager.java` |
| P1-13 | LLM generator has no cost/rate protection | Unbounded OpenAI spend possible | `backend/api/service/LLMSuggestionGenerator.java` |

### P2 Evolution Work — Lifecycle Intelligence Features

| # | Gap | Capability Needed |
|---|---|---|
| P2-1 | No lifecycle-stage-aware event emission | Events when requirements created, sprints planned, etc. |
| P2-2 | No agent-lifecycle integration | Connect agent execution to lifecycle stage transitions |
| P2-3 | No durable multi-agent collaboration | Orchestrator → delegate → report chain |
| P2-4 | No learning loop closure | Feedback→LLM reflection→policy update cycle |
| P2-5 | No product knowledge graph | Cross-lifecycle artifact relationships |
| P2-6 | No cross-phase artifact linking | Requirements ↔ stories ↔ code ↔ tests ↔ deployments |
| P2-7 | No AI-native workflow execution | Pipeline execution from YAML with LLM decision nodes |
| P2-8 | No full observability stack | Metrics + traces + audit all durable and queryable |
| P2-9 | No tenant-scoped agent memory | Agent memory scoped to and isolated by tenant |
| P2-10 | Plugin ecosystem | Extensible tool, agent, and lifecycle stage providers |

---

## 14. Target Backend Architecture

### 14.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    USERS / EXTERNAL SYSTEMS                      │
└─────────────────────┬───────────────────────────────────────────┘
                      │ HTTPS / WSS
┌─────────────────────▼───────────────────────────────────────────┐
│            SECURITY GATEWAY (platform/java/security)             │
│   CorsMiddleware → SecurityMiddleware (JWT) → TenantExtractor    │
│   TenantIsolationEnforcer → OPA PolicyEngine → Routing           │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│                   API LAYER (backend/api)                         │
│  REST Controllers  │  WebSocket Handlers  │  GraphQL Resolvers   │
│  (Lifecycle CRUD, Agent Commands, Approvals, Dashboard)          │
└──────┬──────────────┬──────────────────────┬────────────────────┘
       │              │                      │
┌──────▼──────┐ ┌─────▼──────┐    ┌──────────▼──────────────────┐
│  DOMAIN     │ │ WORKFLOW   │    │   AGENT ORCHESTRATION        │
│  SERVICES   │ │ ENGINE     │    │   TaskOrchestrator           │
│ (Req, story,│ │  DAG+Durable│   │   CapabilityMatcher          │
│  sprint,    │ │  state in  │   │   AgentRegistry (DataCloud)  │
│  approval,  │ │  Postgres) │   │   AgentExecution             │
│  workspace) │ └─────┬──────┘   │   AgentMemory (EventLog)     │
└──────┬──────┘       │          └──────────┬──────────────────-─┘
       │              │                     │
┌──────▼──────────────▼─────────────────────▼────────────────────┐
│              PERSISTENCE LAYER (Postgres via JDBC)               │
│  Requirements │ Workflows │ Agent Registry │ Audit │ Memory      │
│  Version History │ Traces │ Sprints │ Stories │ Teams │ Events   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│              EVENT PLANE (AEP — Agentic Event Processor)         │
│  Lifecycle stage events │ Agent dispatch events │ Domain events  │
│  Event routing (YAML-driven) │ Schema validation                 │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│              INTELLIGENCE LAYER                                   │
│  LLM Gateway (OpenAI/Anthropic) with cost governor               │
│  RAG Service (vector search) │ Knowledge Graph (persisted)       │
│  Agent Learning (feedback→reflection→policy)                     │
│  Feedback Loop: Operation → Learning → Enhancement               │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│              OBSERVABILITY (platform/java/observability)          │
│  Micrometer metrics → Prometheus │ OTEL traces → Jaeger/Tempo    │
│  Durable audit log │ Correlation IDs │ Tenant-scoped telemetry   │
└─────────────────────────────────────────────────────────────────┘
```

### 14.2 Key Module Changes Required

| Component | Current | Target |
|---|---|---|
| Middleware chain | `CORS → Error → Router` | `CORS → Security(JWT) → TenantExtract → OPA → Router` |
| RBAC | `InMemoryRolePermissionRegistry` | `JdbcRolePermissionRegistry` with tenant scoping |
| Audit | `InMemoryAuditQueryService` | `JdbcAuditRepository` with append-only event log |
| Workflow state | `InMemoryWorkflowStateStore` | `JdbcWorkflowStateStore` implementing `DurableWorkflowEngine.WorkflowStateStore` |
| AI workflow repos | `InMemoryAiWorkflowRepository`, `InMemoryAiPlanRepository` | JDBC-backed implementations |
| Agent registry | YAPPC domain `HashMap` registry | `DataCloudAgentRegistry` + YAML catalog loader at startup |
| Version history | `InMemoryVersionRecord` | `JdbcVersionRecord` |
| Knowledge graph | `InMemoryKgService` | Graph DB (Neo4j/Embedded) or SQL adjacency list |
| Team/Notification | `InMemoryTeamRepository`, `InMemoryNotificationRepository` | JDBC implementations |
| Trace/Log repos | `InMemoryTraceRepository`, `InMemoryLogEntryRepository` | JDBC or TimeSeries DB implementations |
| Metrics | `NoopMetricsCollector` | `MicrometerMetricsCollector` bound to Prometheus registry |
| SharedBaseModule | Provides InMemory repos that conflict with ProductionModule | Remove InMemory repo bindings; SharedBaseModule provides only controller/service bindings |
| Agent catalog | Not loaded at startup | `FileBasedCatalog` instantiated in `ApiApplication` bootstrap |
| OPA | Not enforced | `PolicyEngine` wired in middleware chain |

---

## 15. Implementation Roadmap

### Phase 0 — Build, Wiring, and Security Repair

**Duration estimate:** 2–3 weeks  
**Objectives:** Make the backend secure, startable, and deterministically wired.

#### 0.1 Fix Authentication Wiring (P0-1)

**File:** `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/ApiApplication.java`

Change the `servlet()` method to thread `SecurityMiddleware` into the chain:
```java
// AFTER
AsyncServlet servlet = RoutingServlet.builder(reactor) /* ... all routes ... */ .build();
AsyncServlet withSecurity = SecurityMiddleware.create(jwtTokenProvider, servlet);
return new CorsMiddleware(new GlobalExceptionHandler(withSecurity));
```

Inject `JwtTokenProvider` and `SecurityConfig` into the `servlet()` `@Provides` method signature.

#### 0.2 Resolve DI Binding Conflict (P0-4)

**File:** `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/SharedBaseModule.java`

Remove the `@Provides` methods for `AISuggestionRepository`, `RequirementRepository`, and `WorkspaceRepository` from `SharedBaseModule`. These bindings belong exclusively in `DevelopmentModule` (InMemory) and `ProductionModule` (JDBC). SharedBaseModule should only provide controller bindings, service bindings, and platform utilities.

#### 0.3 Replace Production Audit with Durable Store (P0-5)

**File:** `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/ProductionModule.java`

Replace `InMemoryAuditQueryService` with a new `JdbcAuditRepository` implementing both write (record audit events) and query (paginated audit event reads) operations. Add the corresponding Flyway migration for the `audit_events` table if not already present.

#### 0.4 Add Tenant Middleware (P0-2)

**File:** `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/ApiApplication.java`

After JWT auth, thread `TenantContextExtractor` and a new `TenantEnforcementMiddleware` that calls `TenantIsolationEnforcer`.

#### 0.5 Sandbox BuildExecutorService (P0-3)

**File:** `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/build/BuildExecutorService.java`

Add:
- Require authenticated and authorized `agent:build` permission
- Run subprocess with restricted working directory
- Set hard timeouts on process execution
- Reject paths outside allowed workspace directories

**Exit Criteria for Phase 0:**
- [ ] All endpoints require JWT
- [ ] No DI binding conflicts at startup
- [ ] Audit events written to database survive restart
- [ ] Tenant ID extracted and available to all repository calls
- [ ] BuildExecutorService rejects unauthenticated callers

---

### Phase 1 — Persistence and Orchestration Hardening

**Duration estimate:** 3–4 weeks  
**Objectives:** Make all production state durable. Activate real metrics. Resolve InMemory repository backlog.

#### 1.1 Implement JdbcWorkflowStateStore (P0-6, P1-3)

**File:** New — `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/engine/JdbcWorkflowStateStore.java`

Implement `DurableWorkflowEngine.WorkflowStateStore` using JDBC. Store `WorkflowRun` serialized as JSON in a `workflow_runs` table. Bind in `ProductionModule`.

#### 1.2 Implement JdbcAiWorkflowRepository and JdbcAiPlanRepository (P1-3)

**Files:** New JDBC implementations in `backend/persistence`  
**Action:** Add Flyway migrations for `ai_workflow_instances` and `ai_plans` tables. Wire in `ProductionModule`.

#### 1.3 Implement JdbcVersionRecord (P1-2)

**File:** New — `backend/persistence/src/main/java/com/ghatana/yappc/api/repository/jdbc/JdbcVersionRecord.java`  
**Action:** Replace `InMemoryVersionRecord` in `SharedBaseModule`. Bind JDBC version in `ProductionModule`.

#### 1.4 Resolve Remaining InMemory Repositories (P1-4, P1-5)

Implement JDBC versions for:
- `TeamRepository` → `JdbcTeamRepository`
- `NotificationRepository` → `JdbcNotificationRepository`
- `TraceRepository` → `JdbcTraceRepository`
- `LogEntryRepository` → `JdbcLogEntryRepository`

#### 1.5 Wire Real MetricsCollector (P1-6)

**File:** `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/ProductionModule.java`

Replace `NoopMetricsCollector` with a `MicrometerMetricsCollector` that registers with a Prometheus `MeterRegistry` bound to the Prometheus HTTP endpoint.

#### 1.6 Replace InMemoryRolePermissionRegistry (P1-1)

**File:** New — `backend/api/src/main/java/com/ghatana/yappc/api/security/JdbcRolePermissionRegistry.java`

Implement with tenant-ID-scoped role lookups. Migrate default roles into Flyway seed data.

**Exit Criteria for Phase 1:**
- [ ] All production state survives process restart
- [ ] Prometheus metrics endpoint returns real data
- [ ] RBAC decisions are tenant-scoped and durable
- [ ] Workflow instances resume across restarts (replay not required yet)

---

### Phase 2 — Real Lifecycle Execution and Durable Workflow State

**Duration estimate:** 4–6 weeks  
**Objectives:** Activate agent catalog, load pipeline YAML, wire lifecycle events.

#### 2.1 Boot-time Agent Catalog Loading (P1-7)

**File:** `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/ApiApplication.java`  
**Action:** Instantiate `FileBasedCatalog` pointing to `config/agents/definitions`. Register loaded agents into `AgentRegistryRepository` (JDBC). Update `AgentRegistryService` to serve capability queries from the durable registry.

#### 2.2 Activate DataCloudAgentRegistry (P1-7)

**File:** `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/ProductionModule.java`  
**Action:** Replace domain `AgentRegistry` (HashMap) with `DataCloudAgentRegistry` as the active registry. Connect `DataCloudCheckpointStore`.

#### 2.3 Wire OPA PolicyEngine (P1-9)

**File:** `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/ProductionModule.java`  
**Action:** Instantiate `PolicyEngine` from `platform/java/governance`. Load `config/policies/core.rego`. Insert policy evaluation into the middleware chain after authentication.

#### 2.4 Fix GraphQL Resolvers (P1-10)

Replace `// STUB: Replace with actual service call` in:
- `RequirementResolver` → call `RequirementService`
- `ProjectResolver` → call `ProjectService`
- `SuggestionMutations` → call `AISuggestionService`

#### 2.5 Emit Lifecycle Events

**New file:** `backend/api/service/LifecycleEventEmitter.java`  
**Action:** On requirement creation, sprint planning, story state change, deployment, etc. — emit a typed lifecycle event through the `YappcAgentEventRouter` into AEP. Define event schemas in `config/agents/event-schemas/`.

#### 2.6 Load Pipeline YAML and Wire to DAGPipelineExecutor (P1-8)

**Action:** At startup, load `config/pipelines/*.yaml`. Parse pipeline nodes and edges. Register pipelines in a `PipelineRegistry`. Expose `/api/pipelines` endpoint for execution.

**Exit Criteria for Phase 2:**
- [ ] Agent catalog is loaded from YAML and queryable from API
- [ ] Lifecycle events flow through AEP on every stage transition
- [ ] GraphQL returns real data
- [ ] OPA enforces policy on agent actions
- [ ] Pipeline YAML creates executable DAG instances

---

### Phase 3 — Product Intelligence, Memory, and AI-Native Collaboration

**Duration estimate:** 6–10 weeks  
**Objectives:** Close the product intelligence loop. Make AI agents genuinely useful.

#### 3.1 Persistent Agent Memory (P2-9)

**Action:** Wire `MemoryAwareBaseAgent` with a `JdbcEventLogMemoryStore` backed by an append-only `agent_memory_events` table. Scope all memory operations to `(tenant_id, agent_id, session_id)`.

#### 3.2 Knowledge Graph Persistence (P1-11)

**Action:** Replace `InMemoryKgService` with a SQL adjacency-list `JdbcKgService` or embed a graph engine. Store nodes and edges with tenant scoping. Expose graph query API for architecture analysis and cross-lifecycle artifact linking.

#### 3.3 Learning Loop Closure (P2-4)

**Action:** Un-STUB `FeedbackLearningService`. Connect: user feedback → episode storage → batch LLM reflection → pattern recognition → policy update in the platform `agent-learning` module. Store learned policies in `JdbcPolicyRepository`.

#### 3.4 LLM Cost and Rate Protection

**Action:** Add cost-governor layer over `LLMGateway`:
- Per-tenant call budget tracking
- Rate limiter on tokens/minute
- Timeout on LLM calls (30s hard timeout)
- Fallback behavior (return cached suggestion if LLM timeout)

#### 3.5 Human-AI Approval Escalation

**Action:** Make `ApprovalWorkflowService` durable (JDBC-backed). Connect agent-generated suggestions through the approval workflow before execution. Add WebSocket push for approval requests to the relevant human.

**Exit Criteria for Phase 3:**
- [ ] Agent memory persists across restarts and is tenant-scoped
- [ ] Learning loop produces persisted policies from feedback
- [ ] Knowledge graph survives restart and links lifecycle artifacts
- [ ] LLM calls have cost protection and timeouts
- [ ] Human approval of AI suggestions is durable and auditable

---

### Phase 4 — Pluginized Ecosystem and Cross-Product Platform Reuse

**Duration estimate:** 8–12 weeks  
**Objectives:** Enable safe extensibility. Enable YAPPC patterns for use across other products.

#### 4.1 Runtime Plugin Loading

**Action:** Implement versioned plugin interface in `platform/java/plugin`. Support classpath isolation via child classloaders. Add plugin health monitoring. Reject unsigned plugins.

#### 4.2 Tool Provider SPI

**Action:** Stabilize `CodeGenerationToolProvider` and `TestGenerationToolProvider`. Remove `UnsupportedOperationException` stubs. Define `ToolProvider` SPI with clear input/output contracts. Allow external tools to register via the plugin system.

#### 4.3 Cross-Product Platform Extraction

**Action:** Promote stable YAPPC patterns to `platform/java`:
- Lifecycle event model → `platform/java/event-cloud`
- Durable workflow state → `platform/java/workflow`
- Approval workflow → `platform/java/domain`
- LLM cost governor → `platform/java/ai-integration`

**Exit Criteria for Phase 4:**
- [ ] New lifecycle stages can be added via plugin
- [ ] New AI agents can be contributed via tool provider SPI
- [ ] At least two other products (`aep`, `data-cloud`) reuse a stabilized platform library from YAPPC work

---

## 16. Risk Analysis

### Technical Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| ActiveJ DI conflict causes silent InMemory binding in production | High | Critical | Phase 0: Remove SharedBaseModule repo bindings |
| Auth bypass exploited before Phase 0 completes | High | Catastrophic | Immediate hotfix required; deploy behind VPN until resolved |
| InMemory audit log causes compliance violation | Medium | High | Phase 0: Minimum — write audit events to a log file if JDBC migration isn't immediate |
| DurableWorkflowEngine state loss causes incomplete agent execution | High | High | Phase 1: Must have JDBC state store before any agent workflows in production |
| NoOpLLMGateway silently used in production | High | Medium | Phase 1: Add explicit startup check that LLM is configured; fail fast rather than fallback silently |
| Agent YAML catalog grows without loading — perpetual disconnect | Ongoing | High | Phase 2: Boot-time catalog loading is gate condition |
| GraphQL resolvers serving stubs to production clients | High | High | Phase 2: Fix resolvers before exposing GraphQL publicly |

### Architectural Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| YAPPC domain agent model diverges further from platform agent framework | High | High | Phase 2: Migrate to DataCloudAgentRegistry |
| InMemory knowledge graph creates unresolvable data silos | High | High | Phase 3: Migrate to persistent graph before knowledge accumulates |
| Learning loop feedback data is lost before any insight is generated | High | High | Phase 3 is dependency blocked until Phase 1 persistence is resolved |

---

## 17. Final System Readiness Score

### Capability Readiness (Current State)

| Dimension | Score | Status |
|---|---|---|
| Build & Startup | 5/10 | Partially Wired |
| Authentication & Authorization | 1/10 | **P0 Failure** |
| Tenant Isolation | 1/10 | **P0 Failure** |
| Persistence Durability | 3/10 | Partially Wired |
| Workflow Durability | 1/10 | Declared but Not Operational |
| Observability | 2/10 | Declared but Not Operational |
| Agent Framework | 5/10 | Partially Wired |
| Lifecycle Intelligence | 2/10 | Partially Wired |
| AI/LLM Integration | 3/10 | Partially Wired |
| Human-Agent Interaction | 4/10 | Partially Wired |
| Plugin / Extensibility | 2/10 | Declared but Not Operational |
| Security & Governance | 1/10 | **P0 Failure** |
| **Overall** | **2.5/10** | **Not Ready for Production** |

### Post-Phase Readiness Trajectory

| After Phase | Projected Score | Status |
|---|---|---|
| Phase 0 | 5.5/10 | Minimally Safe |
| Phase 1 | 6.5/10 | Staging Ready |
| Phase 2 | 7.5/10 | Lifecycle Intelligence Operational |
| Phase 3 | 8.5/10 | AI-Native Collaboration Active |
| Phase 4 | 9.0/10 | Production Ecosystem |

### Summary Judgment

> The YAPPC backend has a strong structural skeleton and genuine architectural ambition. The platform libraries (`agent-framework`, `workflow`, `agent-memory`, `governance`) are well-designed. The domain model is coherent. The code quality is high where implementation exists.
>
> However, the system currently poses **catastrophic risk in production deployment** due to the unauthenticated middleware chain, non-durable audit and workflow state, and silent NoOp defaults for AI and metrics. These are not missing features — they are broken wirings of components that exist and are correct in isolation.
>
> **The highest-leverage action in this codebase is a single line change in `ApiApplication.java`:** inserting `SecurityMiddleware` into the servlet chain. This unblocks all downstream auth, RBAC, and tenant enforcement work.
>
> After authentication is restored, the DI binding conflicts and InMemory repository backlog are manageable engineering work with clear JDBC counterparts already partially implemented.
>
> The lifecycle intelligence architecture is achievable from this codebase without a rewrite — it requires wiring existing components that are proven correct individually but not yet integrated into a coherent runtime.

---

*End of Report*

**Anchored to code evidence in:**
- `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/ApiApplication.java` (L439 — middleware chain)
- `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/SharedBaseModule.java` (L118-136 — InMemory repo bindings)
- `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/config/ProductionModule.java` (L150, L190, L197 — InMemory audit, InMemoryRBac)
- `platform/java/workflow/src/main/java/com/ghatana/platform/workflow/engine/DurableWorkflowEngine.java` (L272 — InMemoryWorkflowStateStore)
- `products/yappc/backend/api/src/main/java/com/ghatana/yappc/api/service/NoOpLLMGateway.java`
- `products/yappc/core/ai/src/main/java/com/ghatana/yappc/ai/requirements/ai/feedback/FeedbackLearningService.java` (STUB markers)
- `products/yappc/core/ai/src/main/java/com/ghatana/yappc/ai/requirements/api/graphql/` (STUB markers throughout resolvers)
- `platform/java/agent-framework/src/main/java/com/ghatana/agent/catalog/loader/FileBasedCatalog.java` (not instantiated in ApiApplication)
