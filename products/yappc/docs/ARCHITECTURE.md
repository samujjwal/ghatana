# YAPPC Architecture

**Status:** Active  
**Last Updated:** 2026-03-24  
**Owner:** Architecture Team

> **Recent structural changes (2026-03-24):**
> - Phase 2: `services:platform` and `services:lifecycle` moved from `services/` to `core/` — they are reusable libraries, not deployables
> - Phase 3: `core:yappc-domain` renamed to `core:yappc-domain-impl` — internal implementation only; `libs:java:yappc-domain` is the canonical public contract
> - Phase 5: `frontend/libs/theme` moved to `frontend/compat/theme` — not first-class
> - Phase 4 tracker: direct AEP/DataCloud imports in capability modules annotated with `TODO(ADAPTER-SEAM)` for future remediation

---

## Overview

YAPPC is an **AI-powered project scaffolding and code-generation platform** built on the Ghatana Platform Standards with:
- **Capability-based module taxonomy** — each Gradle module owns exactly one capability
- **ActiveJ async runtime** for high-performance, non-blocking I/O
- **Data-Cloud** as canonical persistence layer
- **Multi-tenancy** with strict tenant isolation at every layer
- **Event-driven** for cross-module and cross-product communication

---

## System Architecture

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

## Scaffold Sub-System

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
