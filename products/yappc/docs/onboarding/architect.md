# YAPPC Onboarding — Architect

This guide gives a software architect the structural orientation they need to reason about YAPPC, make architectural decisions within it, and extend it correctly.

---

## System Context

YAPPC is a lifecycle management platform. Its core responsibility is tracking work-item state through eight discrete phases, enforcing governance gate rules at each transition, running approval workflows, and augmenting the whole process with AI-generated suggestions.

YAPPC lives in `products/yappc/` and is one of several products built on the Ghatana monorepo's shared platform. It has its own Gradle root at `products/yappc/build.gradle.kts`. Start with `products/yappc/settings.gradle.kts`, `MODULE_CATALOG.md`, and `START_HERE_ARCHITECTURE.md` when validating whether a module name is current.

---

## Module Topology

```
products/yappc/
├── services/                 # Deployable YAPPC application entrypoint
├── core/
│   ├── services-platform/    # Shared HTTP/platform service plumbing
│   ├── services-lifecycle/   # Lifecycle HTTP/service library and route composition
│   ├── yappc-domain-impl/    # Canonical internal domain implementation
│   ├── yappc-services/       # Lifecycle and product business logic
│   ├── yappc-infrastructure/ # Product-specific adapters and persistence integration
│   ├── yappc-api/            # Current API-facing Java types
│   ├── yappc-shared/         # Shared contracts and cross-module value types
│   ├── yappc-agents/         # Consolidated agent implementation surface
│   ├── ai/                   # AI provider integration and safety flows
│   ├── knowledge-graph/      # Knowledge graph capability
│   ├── agents/               # Capability family still containing runtime/workflow specialists
│   ├── scaffold/             # Scaffold API/core/generators/templates family
│   └── refactorer/           # Refactorer API/engine family
├── infrastructure/
│   └── datacloud/            # Data Cloud integration surface
├── libs/
│   └── java/yappc-domain/    # Public product domain contracts
└── build.gradle.kts
```

**Retired or compatibility-only names**

- `backend:api` was removed; use `services/` plus `core/services-lifecycle` for runtime and HTTP work.
- `core:domain` was replaced by `core:yappc-domain-impl`.
- `core:framework` was absorbed into current infrastructure/service modules.
- `core:lifecycle` is removed; do not describe it as the active entry surface.

**Enforced dependency rules** (ArchUnit-tested and module-catalog backed):

- `agents/*` **must not** import from `scaffold` or `refactorer`
- `scaffold` **must not** import from `agents`
- `yappc-domain-impl` must stay free of product-local framework leakage
- `yappc-infrastructure` is the adapter boundary, not `yappc-services`
- `services/` is the deployable composition root; route/business logic belongs in the underlying modules, not in the app wrapper

---

## Runtime Model

YAPPC is an **ActiveJ** application. This has several architectural implications you must carry in all design decisions:

| Concern | Implication |
|---------|-------------|
| Threading | Single-threaded event loop per server instance. No blocking I/O permitted. |
| Async primitive | `io.activej.promise.Promise<T>` — not `CompletableFuture`, not Project Reactor |
| HTTP server | `AsyncServlet` / `RoutingServlet` — not Spring MVC or JAX-RS |
| DI | `io.activej.inject` — not Spring, not Guice |
| Blocking I/O | Must be wrapped in `Promise.ofBlocking(Executor, ...)` |
| Tests | Must extend `EventloopTestBase`; use `runPromise(() -> ...)` |

Violating the event-loop rule causes subtle bugs (starvation, out-of-order promise resolution) that are extremely difficult to reproduce.

---

## Key Architectural Patterns

### Phase Gate as a Policy Object

The phase gate rules are pure domain objects that implement a policy interface. They receive a `PhaseTransitionContext` and return a `Promise<ValidationResult>`. They have no framework dependencies and are trivially testable.

### AI Integration via Fallback Chain

All LLM calls go through `AIFallbackService` → implemented by `DefaultAIFallbackService` in `core/ai`. The fallback chain:

1. Primary provider (configurable)
2. Secondary provider
3. Tertiary provider
4. Exception `AllProvidersFailedException` if all circuits are OPEN

Circuit state is managed entirely in-process (no external store). This is intentional — state is ephemeral and per-instance. For multi-instance deployments, each instance manages its own circuit. This is acceptable because provider outages affect all instances simultaneously.

### Safety Gating for AI Output

All AI responses flow through `AISafetyFilter` before being returned to callers. The filter is configured via `AISafetyFilter.Config`:

| Category | Default | Purpose |
|----------|---------|---------|
| `blockHarmfulContent` | `true` | Halts bomb/malware/harm instructions |
| `blockPromptInjection` | `true` | Halts jailbreak/override markers |
| `redactPii` | `true` | Redacts SSN, card numbers, secrets |

### GDPR via SPI

GDPR operations use the `GdprDataService` SPI:
- Register collections at startup via `DeletableCollection` / `ExportableCollection`
- The service sequences operations through a `Promise` chain (not parallel) to avoid overwhelming downstream stores with simultaneous deletes during data-subject requests

### Feature Flags

Feature flags are a first-class lifecycle concept. Rules are evaluated per-tenant per-transition. The `featureFlagService` bean in `LifecycleServiceModule` provides synchronous (memoised) evaluation.

---

## Extending YAPPC

### Adding a New Lifecycle Phase

1. Add the phase contract in the active domain surface (`libs/java/yappc-domain` for public contracts, `core/yappc-domain-impl` for internal implementation details).
2. Implement or update gate logic in the owning lifecycle/business module.
3. Register route, workflow, or policy wiring in `core/services-lifecycle` where the HTTP/runtime composition lives.
4. Update persistence and observability surfaces in the owning adapter modules.
5. Add regression coverage in the same module family that owns the new behavior.

### Adding a New AI Suggestion Type

1. Decide whether the behavior belongs in the consolidated `core/yappc-agents` surface or a remaining `core/agents/*` capability module.
2. Implement `TypedAgent<LifecycleContext, SuggestionResult>`.
3. Register the agent via the current product/registry wiring rather than introducing new legacy agent entrypoints.
4. Add a prompt template in `core/ai/src/main/resources/prompts/`.
5. Wire into the active orchestration path in `core/yappc-agents` or `core/agents/workflow`, depending on where the current caller lives.

### Adding a New Platform API Boundary

All new HTTP routes must be composed through the ActiveJ routing surface in `core/services-lifecycle`, delegate to a dedicated controller/use case, and be secured via the current auth filter chain.

Do **not** add logic directly into `YappcLifecycleService`. It is a composition root, not a handler.

---

## ADRs You Should Read

All Architecture Decision Records are in `docs/adr/`. Key ADRs for YAPPC:

| ADR | Decision |
|-----|---------|
| ADR-001 | ActiveJ over Spring (event-loop model, no reflection overhead) |
| ADR-003 | In-process circuit breaker (no external state store) |
| ADR-007 | Sequential GDPR deletion (SPI pattern, no parallel fan-out) |
| ADR-012 | Platform agent registry (AEP Central Registry) |

---

## Quality Gates

| Gate | Threshold | Enforced By |
|------|-----------|-------------|
| Unit test coverage | ≥ 80% branch, ≥ 80% line | JaCoCo in `products/yappc/build.gradle.kts` |
| ArchUnit boundary checks | Zero violations | `core/domain/.../ArchitectureBoundaryTest` |
| PMD/Checkstyle/SpotBugs | Zero new findings | `buildSrc` convention plugins |
| JavaDoc `@doc.*` tags | All 4 tags required | Custom Gradle doc-tag check |
| SLO availability | ≥ 99.9% | Prometheus burn-rate alerts + Grafana dashboard |

---

## Infrastructure Topology

```
Internet
   │
   └─► Ingress / Load Balancer
           │
           ▼
   yappc-lifecycle pods (N replicas, event-loop per instance)
           │
     ┌─────┴──────────────┐
     │                    │
     ▼                    ▼
 PostgreSQL            LLM Gateway
 (tenant-scoped)       (ext. provider)
     │
     ▼
 Event Bus (→ AEP for cross-product events)
```

There is no shared in-process state between pods beyond what is stored in the database. Circuit breaker state is per-pod and per-provider.
