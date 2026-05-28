# YAPPC Architecture

**Status:** Active  
**Last Updated:** 2026-05-27  
**Owner:** Architecture Team

> **Changes since 2026-03-24:**
> - Security layer added: `JwtAuthController`, `EncryptionService`, `SecurityAuditLogger`, `SecurityHeadersServlet` in `core/services-lifecycle`
> - Lifecycle gate layer added: `PhaseGateValidator` in `core/services-lifecycle/gate/`
> - AI suggestion service added: `AISuggestionService` + `SuggestionPanel.tsx` in `core/ai` and frontend
> - Data persistence: `AgentStateRepository`, `ConversationRepository`, `JdbcHumanApprovalService` live in `infrastructure/datacloud`
> - Observability: `AIMetricsCollector`, `BusinessMetrics`, Prometheus alerting rules added
> - JMH performance benchmarks added to `core/services-lifecycle` and `core/ai`
> - Lifecycle route decision-support panel added with AI defaults, progressive-disclosure guidance, and one-click automation approval trigger
> - `LifecycleApiController` now emits DAG execution metadata with per-phase timing telemetry and graceful failure envelopes

---

## Overview

YAPPC is an **AI-powered project scaffolding and code-generation platform** built on the Ghatana Platform Standards with:
- **Capability-based module taxonomy** — each Gradle module owns exactly one capability
- **ActiveJ async runtime** for high-performance, non-blocking I/O
- **Data-Cloud** as canonical persistence layer
- **Multi-tenancy** with strict tenant isolation at every layer
- **Event-driven** for cross-module and cross-product communication

### Implementation Reality Snapshot (2026-05-27)

- Architecture direction and phase boundary ownership are stable and aligned with Kernel/Data Cloud/AEP contracts.
- Backend lifecycle, phase packet, ProductUnitIntent handoff, and degraded fail-closed semantics are implemented and verified.
- Frontend is now transitioning from heuristic phase-status rendering to backend-owned typed phase panel models.
- Remaining engineering work is primarily hardening: cockpit decomposition, shell consistency, and documentation drift removal.

### Known Hardening Gaps by Phase (2026-05-27)

| Lifecycle Phase | Current State | Hardening Focus |
|----------------|---------------|-----------------|
| INTENT | Implemented | Deeper model/prompt evaluation breadth and long-run regression coverage |
| SHAPE | Implemented | Larger project canvas performance and artifact lineage regression depth |
| VALIDATE | Implemented | Expanded policy fixture combinations and gate confidence explainability |
| GENERATE | Implemented | Canonical backend-owned assurance panel composition across all UI surfaces |
| RUN | Implemented | Retry/rollback/promote operational drills and environment-specific resilience |
| OBSERVE | Implemented | Typed runtime diagnostics enrichment and operator remediation runbooks |
| LEARN | Implemented | Longer-horizon feedback loop verification and governance signal traceability |
| EVOLVE | Implemented | Broader impact-analysis and diff-review end-to-end regression coverage |

### Current Hardening Focus Areas

- Phase cockpit decomposition into thin routes, mappers, action hooks, and focused presentational panels.
- Full removal of frontend heuristic lifecycle inference in favor of typed backend packet models.
- Product shell consistency across phase cockpit, kernel health, product-family, and admin surfaces.
- Ongoing docs reconciliation to keep architecture and backlog language synchronized with current implementation.

---

## System Architecture

### Source of Truth for API Routes

**Route Manifest (`products/yappc/docs/api/route-manifest.yaml`)** is the canonical source of truth for all YAPPC API routes.

- **Purpose**: Single machine-readable source defining all routes, authentication modes, scopes, ownership, and architectural boundaries
- **Validation**: The `checkYappcOpenApiParity` Gradle task validates parity between the manifest and OpenAPI specification
- **Generation**: Route metadata is used to generate `RouteAuthorizationRegistry` at build time, eliminating manual duplication
- **Integration Points**:
  - OpenAPI (`products/yappc/docs/api/openapi.yaml`): Every route must have matching path and operationId
  - Frontend Client: Generated from OpenAPI, operationId maps to client method names
  - Backend Registry: Generated from manifest, auth level determines credential requirements

**Route Entry Schema**:
- **Required Fields**: `method`, `path`, `auth`, `owner`, `boundary`, `operationId`
- **Optional Fields**: `scopes` (required when auth=required), `auditEventType`, `privacyClassification`
- **Auth Levels**: `public` (no auth), `required` (session cookie or API key/Bearer), `optional` (guest access)
- **Boundaries**: `YAPPC` (owned by YAPPC), `DATA_CLOUD_AEP` (delegates to platform services)

**See**: `products/yappc/docs/api/route-manifest.yaml` for complete schema documentation and examples.

### High-Level Layers

```
┌──────────────────────────────────────────────────────────────┐
│              Application Entry Point (services/)             │
│         services  [application — the only deployable]        │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                  Core Libraries (core/)                      │
│  services-platform  services-lifecycle  (reusable, non-app)  │
│  yappc-domain-impl  yappc-services  yappc-agents  yappc-api   │
│  yappc-infrastructure  yappc-shared                          │
└──────┬───────────────────────────────────┬───────────────────┘
       │                                   │
       ▼                                   ▼
┌─────────────────┐              ┌──────────────────────────┐
│   AI/Agents     │              │   Scaffold Sub-System    │
│  core/ai        │              │  scaffold:core (agg.)    │
│  agents:runtime │              │  ├─ templates            │
│  agents:workflow│              │  ├─ engine               │
│  agents:common  │              │  └─ generators           │
│  code-specialists│             └──────────────────────────┘
│  delivery-spec. │
│  arch-specialists│
│  testing-spec.  │
└─────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│              Platform Libraries (platform:java:*)            │
│    platform:java:domain  platform:java:core                  │
│    platform:java:http    platform:java:observability         │
└──────────────────────────────────────────────────────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────┐
│           Data-Cloud / AEP Integration Layer                 │
│    infrastructure:datacloud        AEP (agentic-event-proc.) │
└──────────────────────────────────────────────────────────────┘
```

### Module Structure

```
yappc/
├── services/                     # DEPLOYABLE ENTRY POINT — only app here
│   └── build.gradle.kts          # application plugin — the single runnable artifact
│
├── core/                         # Reusable backend capability modules
│   ├── services-platform/        # HTTP platform wiring (was: services:platform)
│   ├── services-lifecycle/       # Lifecycle orchestration library (was: services:lifecycle)
│   │
│   ├── yappc-domain-impl/        # Internal domain impl (was: yappc-domain) — NOT public contract
│   ├── yappc-services/           # Business orchestration services
│   ├── yappc-infrastructure/     # Repository implementations, DC adapters
│   ├── yappc-agents/             # High-level agent façade (consumer of agents/*)
│   ├── yappc-api/                # API contracts + request/response types
│   ├── yappc-shared/             # Shared internal utilities
│   │
│   ├── ai/                       # LLM / AI integration (prompts, completions)
│   │
│   ├── agents/                   # AI agent modules
│   │   ├── common/               # Shared agent utilities & base types
│   │   ├── runtime/              # Agent execution runtime (ActiveJ)
│   │   ├── workflow/             # Multi-step agent workflow engine
│   │   ├── code-specialists/     # Code analysis, language experts, debug
│   │   ├── delivery-specialists/ # Release, DevOps, compliance, security agents
│   │   ├── architecture-specialists/ # Architecture review agents
│   │   └── testing-specialists/  # Testing strategy agents
│   │
│   ├── scaffold/                 # Project scaffolding sub-system
│   │   ├── api/                  # Scaffold public API contracts
│   │   ├── core/                 # Aggregator (re-exports templates+engine+generators)
│   │   ├── templates/            # Foundational layer: models, errors, IO, RCA, docs
│   │   ├── engine/               # Orchestration layer: AI, cache, config, telemetry
│   │   └── generators/           # Generation layer: language gens, pack/plugin/CI
│   │
│   ├── knowledge-graph/          # Knowledge graph engine
│   ├── spi/                      # Extension SPI (pluggable capabilities)
│   ├── refactorer/
│   │   ├── api/                  # Refactoring request/response contracts
│   │   └── engine/               # Code analysis + transformation engine
│   └── cli-tools/                # CLI tooling utilities
│
├── infrastructure/
│   └── datacloud/                # Data-Cloud integration adapter
│
├── libs/java/
│   └── yappc-domain/             # CANONICAL PUBLIC domain types (DTOs, enums, events)
│                                 # This is the one true contract module; use this for deps
│                                 # NOT core:yappc-domain-impl (internal only)
│
├── platform/                     # Ghatana platform libraries (do not modify)
│
├── frontend/                     # TypeScript/React frontend
│   ├── apps/                     # Web applications
│   ├── libs/                     # Canonical libraries: yappc-ui, yappc-canvas, yappc-ai,
│   │                             #   yappc-state, yappc-core (+ product-specific libs)
│   └── compat/                   # Deprecated shims: base-ui, theme, utils, etc.
│                                 #   (DO NOT add new packages here; migrate to libs/yappc-*)
│
└── tools/                        # Validation & build tooling
    └── validation-tests/         # Architectural fitness tests
```

---

## Technology Stack

### Backend (Java)

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Runtime** | Java 21 | Modern Java features, virtual threads |
| **Async I/O** | ActiveJ 6.0 | High-performance non-blocking I/O |
| **Persistence** | Data-Cloud | Canonical data layer, multi-model storage |
| **HTTP** | ActiveJ HTTP (`libs:http-server`) | REST endpoints |
| **Observability** | Micrometer + OpenTelemetry (`libs:observability`) | Metrics, traces, logs |
| **AI/ML** | OpenAI, Anthropic, Ollama (`core/ai`) | LLM integrations |
| **DI** | ActiveJ Inject | Dependency injection |
| **Build** | Gradle 9.2.1 | Multi-module monorepo build |

### Frontend (TypeScript/React)

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Framework** | React 18, Next.js 14 | Modern React with SSR |
| **Language** | TypeScript 5 | Type-safe development |
| **Styling** | Tailwind CSS | Utility-first CSS |
| **State** | Jotai, TanStack Query | Client state, server state |
| **Build** | Turbo, Vite | Fast monorepo builds |

### Infrastructure

| Service | Technology | Purpose |
|---------|------------|---------|
| **Cache** | Redis/Dragonfly/Kvrocks | Session, rate-limit, local state |
| **Database** | PostgreSQL | Relational data |
| **Container** | Docker | Containerization |
| **Orchestration** | Kubernetes | Production deployment |

---

## Kernel Integration Boundary

YAPPC serves as the **creator, visibility, intelligence, health, and control-plane layer** over Kernel and other platform components. YAPPC does not duplicate Kernel execution, deployment, artifact, or gate logic.

### Responsibility Separation

```
YAPPC Creator Lifecycle
  Intent → Shape → Validate → Generate → Run → Observe → Learn → Evolve
    ↓ ProductUnitIntent + lifecycle events
Kernel Product Lifecycle
  dev → validate → test → build → package → deploy → verify
```

**Key Principles:**
- **YAPPC phase gates** are creator/SDLC gates for project lifecycle transitions
- **Kernel delivery gates** are execution/governance gates for ProductUnit lifecycle
- YAPPC may display Kernel gates but must not execute them
- YAPPC generates ProductUnitIntent for lifecycle-governed ProductUnits; Kernel executes the lifecycle
- YAPPC consumes Kernel public events, manifests, snapshots, APIs, or CLI results
- YAPPC must not parse private Kernel logs or mutate Kernel registry files

### Data Flow

```
YAPPC Creator (Generate/Run)
  ↓ ProductUnitIntent
Kernel Product Lifecycle (plan/validate/test/build/package/deploy/verify)
  ↓ Lifecycle events, health snapshots, manifests
YAPPC Visibility (Observe/Learn)
  ↓ Health views, recommendations, explanations
```

### Forbidden Direct Integrations

- YAPPC must not directly mutate `config/canonical-product-registry.json`
- YAPPC must not define Kernel Product Lifecycle enum locally
- YAPPC kernel-health feature must import only public Kernel contracts
- YAPPC must not parse stdout/stderr logs for lifecycle status
- YAPPC CreateCommand with `target=kernel-product-unit` must write intent, not registry

For detailed architecture, see [KERNEL_VISIBILITY_AND_CONTROL_PLANE.md](architecture/KERNEL_VISIBILITY_AND_CONTROL_PLANE.md).

---

## Design Principles

### 1. Capability-Based Module Taxonomy

Each Gradle module owns exactly one capability. The `checkModuleSize` task enforces a hard limit of **150 Java source files per module**. When a module exceeds this limit it must be split — the scaffold sub-system's `templates/engine/generators` split and the agents `delivery-specialists` split are canonical examples.

### 2. Async-First (ActiveJ Only)

- All I/O returns `Promise<T>` — never block the event loop
- Use `Promise.ofBlocking(executor, ...)` for unavoidable blocking operations
- **NEVER** mix `CompletableFuture`, Spring Reactor/WebFlux, or other async runtimes
- All async tests MUST extend `EventloopTestBase` from `libs:activej-test-utils`

```java
public Promise<Project> createProject(CreateProjectRequest request) {
    return validateRequest(request)
        .then(validated -> repository.save(validated))
        .then(saved -> publishEvent(saved))
        .map(__ -> saved);
}
```

### 3. Data-Cloud as Single Source of Truth

All persistent state goes through the Data-Cloud integration layer. Repository interfaces live in the domain; implementations live in `core/yappc-infrastructure` using the Data-Cloud adapters in `infrastructure/datacloud`.

### 4. Multi-Tenancy at Every Layer

- Every request carries an implicit `TenantContext`
- Row-level isolation enforced in Data-Cloud
- Module-level: all queries scope by `tenantId`

### 5. Strict Dependency Flow

```
services (app)  →  core/services-platform  +  core/services-lifecycle
                        │
                        ▼
              core/yappc-*  →  core/agents  →  core/scaffold  →  core/ai
                                                                      ↓
                           libs/java/yappc-domain  (public contract)
                                                                      ↓
                                               platform libraries (do not modify)
```

**BOUNDARY RULE**: `core/*` modules must NOT directly import `products:aep:*` or
`products:data-cloud:*` (except `data-cloud:spi` temporarily until adapter seams are in place).
These external product deps should go through adapter ports defined in YAPPC.
Current violations are annotated with `TODO(ADAPTER-SEAM)` in the relevant build files.

Cross-product integration is via Data-Cloud events and AEP (`agentic-event-processor`) only.

---

## Security Architecture

Security is enforced in layers, from the network edge to the domain.

### Authentication & Authorization Flow

```
HTTP Request
    │
    ▼
┌─────────────────────────────────┐
│  SecurityHeadersServlet         │  HSTS, CSP, X-Frame-Options,
│  (outermost servlet wrapper)    │  Referrer-Policy on all responses
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│  TenantContextFilter            │  Extracts tenantId from JWT claims
│                                 │  or API-key header; sets TenantContext
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│  JwtAuthFilter / ApiKeyFilter   │  Validates token signature,
│  (platform:java:security)       │  expiry, audience, scope
└──────────────┬──────────────────┘
               │
               ▼
┌─────────────────────────────────┐
│  RBAC guard (per-route)         │  Role/permission check before handler
└──────────────┬──────────────────┘
               │
               ▼
         Route handler
```

### Key Security Components

| Class | Module | Responsibility |
|-------|--------|----------------|
| `SecurityHeadersServlet` | `core/services-lifecycle` | Middleware: attaches security response headers |
| `JwtAuthController` | `core/services-lifecycle` | `GET /api/auth/validate`, `POST /api/auth/login` |
| `EncryptionService` | `core/yappc-infrastructure` | AES-256-GCM encryption for sensitive fields at rest |
| `SecurityAuditLogger` | `core/services-lifecycle` | Structured audit log for 12 security event types |
| `JwtAuthFilter` | `platform:java:security` | Platform-level JWT validation (reused here) |
| `ApiKeyAuthFilter` | `platform:java:security` | API-key authentication for service-to-service calls |
| `TenantContextFilter` | `core/services-lifecycle` | Propagates `TenantContext` through the request |

### Encryption at Rest

Sensitive domain fields (approval notes, user secrets, AI model keys) are encrypted before storage using `EncryptionService`, which:
- Generates a random 96-bit IV per encryption operation
- Prepends the IV to the ciphertext for self-contained storage
- Derives the AES-256 key from the `YAPPC_ENCRYPTION_KEY` environment variable
- Exposes `encrypt(String) → String` and `decrypt(String) → String` (Base64 outside)

---

## AI Integration Architecture

### Component Map

```
┌───────────────────────────────────────────────────────────────┐
│                    YAPPC AI Layer (core/ai)                   │
│                                                               │
│  AISuggestionService ──────────► AIModelRouter               │
│  (suggestion/)                   (router/)                    │
│       │                              │                        │
│       │                              ▼                        │
│  PromptVersioningService     ModelAdapter (per provider)      │
│  (prompt/)                   ├── OpenAI adapter               │
│                              ├── Anthropic adapter            │
│  AIMetricsCollector ◄────────┤── Ollama adapter               │
│  (metrics/)                  └── fallback chain               │
│                                                               │
│  ABTestingEvaluationService  CostTrackingService              │
│  ConversationRepository      (persist to DataCloud)           │
└───────────────────────────────────────────────────────────────┘
```

### Suggestion Flow

```
HTTP POST /api/v1/projects/{id}/suggestions
    │
    ▼
SuggestionPanel.tsx (Frontend)        // TanStack Query: POST to backend
    │
    ▼
YappcLifecycleService route handler   // validates JWT + tenant
    │
    ▼
AISuggestionService.suggest(          // builds structured prompt
    projectId, phase, context)        // routes via AIModelRouter
    │                                 // parses [TYPE] prefixes in response
    ▼
AIModelRouter.route(AIRequest)        // selects model, applies A/B test
    │                                 // checks semantic cache
    ▼
ModelAdapter.execute(request)         // actual LLM call
    │
    ▼
AISuggestionService                   // parses response lines
    │                                 // caps at MAX_SUGGESTIONS=5
    ▼
List<Suggestion>                      // REQUIREMENT / DESIGN / TEST / RISK / ACTION
```

### LLM Model Selection

The `AIModelRouter` selects models based on `TaskType`:

| TaskType | Primary Model | Fallback | Notes |
|----------|--------------|---------|-------|
| `REASONING` | GPT-4 / Claude 3 Opus | Claude 3 Sonnet | Used by AISuggestionService |
| `CODE_GENERATION` | GPT-4o / Claude 3.5 Sonnet | GPT-3.5 Turbo | Scaffold generators |
| `CODE_ANALYSIS` | Claude 3 Sonnet | GPT-4o-mini | Refactorer |
| `FAST_RESPONSE` | GPT-3.5 Turbo | Ollama | Chat, autocomplete |
| `DOCUMENTATION` | GPT-4o | Claude 3 Haiku | Doc generation |

### Phase Gate Validation Flow

```
advancePhase(projectId, targetPhase)
    │
    ▼
PhaseGateValidator.validate(
    projectId, targetPhase, conditions)
    │
    ├── GateEvaluator.evaluateEntry(stageSpec, conditions)  → GateResult
    ├── GateEvaluator.evaluateExit(priorStageSpec, conditions) → GateResult
    └── YappcArtifactRepository.listVersions() → artifact presence check
    │
    ▼
ValidationResult
    ├── allClear()  → proceed
    └── blockers()  → return 422 with blocker details
```

---

## Performance Characteristics

### Production Latency Targets

| Operation | Target p99 | Typical | Benchmark |
|-----------|-----------|---------|----------|
| Phase gate validation | 50 ms | 18 ms | `LifecyclePerformanceBenchmarks#benchPhaseGateValidation` |
| Phase gate + artifact lookup | 80 ms | 35 ms | `#benchPhaseGateWithArtifactLookup` |
| Full phase transition | 150 ms | 65 ms | `#benchFullPhaseTransition` |
| Approval submit | 30 ms | 12 ms | `#benchApprovalSubmit` |
| Approval query | 20 ms | 8 ms | `#benchApprovalQuery` |
| Feature flag (warm cache) | 5 ms | <1 ms | `#benchFeatureFlagEvaluation` |
| Liveness probe | 5 ms | <1 ms | `#benchLivenessCheck` |
| Readiness probe | 10 ms | 4 ms | `#benchReadinessCheck` |
| AI suggestion (end-to-end) | 2 000 ms | ~800 ms | `RequirementAIBenchmarks` |

JMH benchmarks live in:
- `core/services-lifecycle/src/test/java/.../performance/LifecyclePerformanceBenchmarks.java`
- `core/ai/src/test/java/.../ai/requirements/ai/RequirementAIBenchmarks.java`

---



The scaffold sub-system generates project scaffolding, manages packs/plugins, and runs language-specific generators. It was split from a 254-file monolith into three focused modules:

| Module | Purpose | File Count |
|--------|---------|-----------|
| `scaffold:templates` | Foundational types: model, error, IO, RCA, docs, validation | ~65 files |
| `scaffold:engine` | Orchestration: AI-assist, cache, config, telemetry, deployment | ~46 files |
| `scaffold:generators` | Language generators + pack/plugin/CI/multi-repo builders | ~143 files |
| `scaffold:core` | Aggregator — re-exports all three via `api()` deps | 0 source files |
| `scaffold:api` | Public API contracts for scaffold consumers | — |

**Dependency chain**: `templates` ← `engine` ← `generators` ← `core(aggregator)`

---

## Agents Sub-System

YAPPC's AI agents are organized by specialization domain:

| Module | Responsibility |
|--------|---------------|
| `agents:common` | Shared base types, mixins, utilities |
| `agents:runtime` | ActiveJ execution runtime for agent turns |
| `agents:workflow` | Multi-step agent coordination / pipeline |
| `agents:code-specialists` | Code analysis, language experts, debug agents |
| `agents:delivery-specialists` | Release, DevOps, compliance, security pipeline agents |
| `agents:architecture-specialists` | Architecture review + fitness agents |
| `agents:testing-specialists` | Testing strategy, coverage, mutation agents |

`code-specialists` re-exports `delivery-specialists` via `api()` for backward compatibility with consumers that import both.

---

## Testing Standards

### Unit Tests (Java)

```java
class ProjectServiceTest extends EventloopTestBase {
    @Test
    void shouldCreateProject() {
        ProjectService service = new ProjectService(repository);
        Project result = runPromise(() -> service.createProject(new CreateProjectRequest("My Project")));
        assertThat(result.getName()).isEqualTo("My Project");
    }
}
```

- **MUST** extend `EventloopTestBase` (from `libs:activej-test-utils`) for all async tests
- **NEVER** call `.getResult()` directly on a `Promise`
- Use `TestDataBuilders` from the same library for test fixtures

### Frontend Tests

- `React Testing Library` + `Jest` for component tests
- Do NOT mock the Navigator — mock individual screens instead
- See `frontend/apps/JEST_TESTING_GUIDE.md` for React Native specifics

---

## Observability

All modules use the platform observability stack from `libs:observability`:

- **Metrics**: Micrometer (Prometheus export)
- **Tracing**: OpenTelemetry (OTLP export)
- **Logging**: SLF4J + structured JSON; all logs include `tenantId` and `correlationId`

---

## Build Commands

```bash
# Full YAPPC build
./gradlew :products:yappc:build

# Check module size limits (max 150 files per module)
./gradlew :products:yappc:checkModuleSize

# Format
./gradlew :products:yappc:spotlessApply

# Static analysis
./gradlew :products:yappc:checkstyleMain :products:yappc:pmdMain
```

---

## References

### Related Documentation

- [Developer Onboarding](ONBOARDING.md)
- [Deployment Guide](guides/DEPLOYMENT_GUIDE.md)
- [ActiveJ Testing Guide](guides/ACTIVEJ_TEST_MIGRATION_GUIDE.md)
- [Scaffold Module Guide](SCAFFOLD_GUIDE.md)

### Architecture Decision Records (ADRs)

- [ADR-001: ActiveJ Adoption](adr/ADR-001-activej-adoption.md)
- [ADR-002: Data-Cloud Integration](adr/ADR-002-datacloud-integration.md)
- [ADR-003: Capability-Based Module Taxonomy](adr/ADR-003-module-taxonomy.md)

---

**Status:** Living Document  
**Owner:** Architecture Team  
**Review Cycle:** Quarterly  
**Next Review:** 2026-06-25
