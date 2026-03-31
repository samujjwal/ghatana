# YAPPC Onboarding — Architect

This guide gives a software architect the structural orientation they need to reason about YAPPC, make architectural decisions within it, and extend it correctly.

---

## System Context

YAPPC is a lifecycle management platform. Its core responsibility is tracking work-item state through eight discrete phases, enforcing governance gate rules at each transition, running approval workflows, and augmenting the whole process with AI-generated suggestions.

YAPPC lives in `products/yappc/` and is one of several products built on the Ghatana monorepo's shared platform. It has its own Gradle root at `products/yappc/build.gradle.kts` and contains eighteen Gradle submodules organised into five domain clusters.

---

## Module Topology

```
products/yappc/
├── core/
│   ├── domain/            # Pure lifecycle domain model — phases, transitions, gates
│   ├── spi/               # Extension points (WorkflowEventListener, etc.)
│   ├── yappc-shared/      # Shared value objects, error types, enums
│   ├── framework/         # DI wiring helpers, shared ActiveJ utilities
│   ├── ai/                # LLM integration, prompt versioning, safety filter, fallback
│   ├── knowledge-graph/   # Work-item relationship graph
│   ├── agents/
│   │   ├── runtime/       # Agent execution engine
│   │   ├── workflow/      # Planning/orchestration agents
│   │   └── specialists/   # Domain-specific suggestion agents
│   ├── scaffold/
│   │   ├── api/           # HTTP contract layer (routing, request/response types)
│   │   ├── core/          # Scaffold orchestration
│   │   └── packs/         # Reusable scaffold templates
│   ├── refactorer/        # Code refactoring suggestion pipeline
│   └── services-lifecycle/ # Entry point: HTTP server, DI module, GDPR, lifecycle orchestration
└── build.gradle.kts
```

**Enforced dependency rules** (ArchUnit-tested):

- `agents/*` **must not** import from `scaffold` or `refactorer`
- `scaffold` **must not** import from `agents`
- `domain` has no upstream dependencies within YAPPC
- `spi` may only depend on `domain` and Ghatana platform contracts
- All modules may depend on `yappc-shared` and `framework`

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

1. Add the enum value to `LifecyclePhase` in `core/domain`.
2. Implement the `PhaseGate` rules for all edges touching the new phase.
3. Register the rules in `LifecycleServiceModule`.
4. Update the ArchUnit dependency test if the new gate has cross-cluster dependencies.
5. Add migration for any phase-indexed persistence tables.
6. Update the Grafana dashboard to include the new phase in the transition filter.

### Adding a New AI Suggestion Type

1. Create a new `SpecialistAgent` in `core/agents/specialists/`.
2. Implement `TypedAgent<LifecycleContext, SuggestionResult>`.
3. Register the agent via the platform agent registry in `platform/java/agent-core`.
4. Add a prompt template in `core/ai/src/main/resources/prompts/`.
5. Wire into the suggestion pipeline in `core/agents/workflow/`.

### Adding a New Platform API Boundary

All new HTTP routes must be added to `RoutingServlet` in `YappcLifecycleService`, delegate to a dedicated controller, and be secured via the existing `TenantContextFilter` + `JwtAuthFilter` chain.

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
