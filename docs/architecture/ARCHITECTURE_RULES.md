# Ghatana Architecture Rules

**Status:** Authoritative  
**Last Updated:** 2026-04-24  
**Owner:** Platform Architecture Team  
**Slack:** #platform-architecture  
**Enforcement:** `eslint-rules/ghatana-architecture-rules.js`, ArchUnit tests, `scripts/check-cross-workspace-deps.mjs`

---

## 1. Purpose

This document is the single authoritative reference for all enforced architecture
rules in the Ghatana monorepo. Every engineer must understand these rules before
making structural changes.

---

## 2. Dependency Direction Specification

### 2.1 Canonical Direction

```
platform/contracts
       ↓
platform/java/*  ←  platform/typescript/*
       ↓
shared-services/*
       ↓
products/*  (no product may import another product)
```

**Rule 1 — No upward imports.**
Products must not import platform-internal implementation classes.
Products may only import contracts and public SPI interfaces from platform packages.

**Rule 2 — No peer product imports.**
`products/aep` must not import `products/yappc`.
`products/yappc` must not import `products/aep`.
Cross-product integration must go through `platform/contracts` event schemas or
shared `DataCloudClient` SPI, never via direct class references.

**Rule 3 — platform/typescript is independent of platform/java.**
TypeScript packages must not import Java packages at build time.
Integration happens through HTTP/WebSocket/SSE at runtime.

### 2.2 Permitted Exceptions

| Exception | Justification | Scope |
|-----------|-------------|-------|
| `products/yappc/core/yappc-infrastructure` imports `products/aep:aep-engine` | Runtime wiring adapter only; no domain logic crosses the boundary | `yappc-infrastructure` module only |
| `products/yappc/core/knowledge-graph` imports `products/data-cloud:platform-launcher` | Temporary; tracked in `TODO(ADAPTER-SEAM)` comment; must be resolved via `DataStorePort` | `knowledge-graph` module only |

All other cross-product imports are forbidden and enforced by ArchUnit tests in
`products/*/src/test/java/**/*ArchitectureTest.java`.

---

## 3. Module Boundary Rules

### 3.1 Domain Logic Containment

- Domain logic (business rules, validations, entity models) must live in
  `*-domain` or `*-spi` modules.
- HTTP routing and request/response mapping must not contain business logic.
- Database query code must not contain business logic.
- Violation: having a `Service` class that also constructs SQL queries inline.

### 3.2 Package Structure

Each Java module follows:
```
com.ghatana.<product>.<capability>
    .api         ← HTTP controller / transport
    .domain      ← pure domain types and business rules
    .service     ← service implementations (orchestration)
    .persistence ← repository implementations
    .port        ← inbound/outbound port interfaces (for adapters)
    .adapter     ← adapter implementations (implements port)
```

TypeScript packages follow:
```
src/
  domain/    ← pure domain types, no framework imports
  services/  ← business logic, framework-light
  hooks/     ← React hooks (UI integration layer)
  components/ ← React components (presentation layer)
  api/       ← HTTP client calls, Zod schemas
  __tests__/ ← co-located tests
```

### 3.3 Shared Services

`shared-services/` modules (auth-gateway, incident-service, ai-inference-service)
are infrastructure glue. They may import `platform/java/*` but must not import
from `products/*`. They are consumed by products at runtime via HTTP APIs only.

---

## 4. Naming Rules

### 4.1 Java

| Element | Convention | Examples |
|---------|-----------|---------|
| Service interface | `<Noun>Service` | `ValidationService`, `TenantService` |
| Service implementation | `<Noun>ServiceImpl` | `ValidationServiceImpl` |
| Repository | `<Entity>Repository` | `KGNodeRepository` |
| Port (outbound) | `<Capability>Port` | `DataStorePort`, `CiCdPort` |
| Adapter (implements port) | `<Target><Capability>Adapter` | `AepAgentRuntimeAdapter` |
| Controller (HTTP) | `<Resource>Controller` | `LifecycleApiController` |
| Domain record/value object | `<Noun>` | `TenantContext`, `AuditEvent` |
| Test class | `<Subject>Test` | `ValidationServiceImplTest` |
| Integration test | `<Subject>IT` or `<Subject>IntegrationTest` | `LifecycleApiControllerIntegrationTest` |

### 4.2 TypeScript

| Element | Convention | Examples |
|---------|-----------|---------|
| Package scope | `@ghatana/` (platform) or `@yappc/` (product) | `@ghatana/design-system`, `@yappc/state` |
| Service class | `<Noun>Service` | `AnomalyDetectionService` |
| Hook | `use<Noun>` | `useAgentState`, `useTenantConfig` |
| Component | `PascalCase` | `AgentList`, `LifecyclePanel` |
| Type / Interface | `PascalCase` | `AgentResult`, `TenantConfig` |
| Zod schema | `<Noun>Schema` | `CreateAgentRequestSchema` |
| Test file | `<Subject>.test.ts(x)` | `AnomalyDetectionService.test.ts` |

---

## 5. Anti-Patterns (Forbidden)

The following patterns are explicitly forbidden and will fail CI:

| Anti-Pattern | Rule | Detection |
|-------------|------|----------|
| Cross-product direct import | Rule 2 above | ArchUnit, `check-cross-workspace-deps.mjs` |
| Business logic in HTTP controller | §3.1 | Code review + ArchUnit layer check |
| `any` in TypeScript production code | TS strict mode | `tsc --noEmit --strict` |
| Hardcoded secrets or credentials | Security contract | TruffleHog scan |
| `System.out.println` / `System.err.println` | Observability contract | Checkstyle `RegexpSingleline` |
| Blocking I/O on ActiveJ event loop | §4.2 of copilot-instructions.md | Code review + static analysis |
| Global mutable state in service singletons | §3.1 | Code review |
| Test that calls `.getResult()` on ActiveJ Promise directly | Testing contract | Code review |
| Duplicate tenant extraction (outside platform/http) | PROPAGATION_CONTRACTS §1 | ArchUnit |
| Enum-based feature flags with no deprecation path | Code health | Code review |

---

## 6. Enforced Dependency Policy (TypeScript)

### 6.1 Platform packages (`platform/typescript/*`)

- Must not import from `products/*`.
- Must not import from `shared-services/*`.
- May import from other `platform/typescript/*` packages only if the dependency
  is explicitly listed in the package's `LIBRARY_GOVERNANCE.md` entry.
- Enforced by: `eslint-rules/ghatana-architecture-rules.js` + `scripts/check-cross-workspace-deps.mjs`.

### 6.2 Product packages (`products/yappc/frontend/*`)

- Must not import from another product's frontend (`products/aep/frontend/*`).
- Should import platform packages (`@ghatana/*`) rather than re-implementing.
- May not create local re-exports of platform packages.

### 6.3 Circular Dependency Prevention

No TypeScript package may form a circular dependency with another package.
Enforced by `dependency-convergence.yml` CI workflow.

---

## 7. CI Enforcement Summary

| Rule | Tool | Workflow |
|------|-----|---------|
| Java cross-product imports | ArchUnit (`*ArchitectureTest.java`) | `test-tier-classification.yml` tier-0 |
| TypeScript cross-workspace deps | `check-cross-workspace-deps.mjs` | `dependency-convergence.yml` |
| TypeScript circular deps | ESLint `import/no-cycle` | `dependency-convergence.yml` |
| TypeScript `any` usage | `tsc --noEmit --strict` | `test-tier-classification.yml` tier-0 |
| Secrets scan | TruffleHog | `.github/workflows/security-scan.yml` |
| System.out usage | Checkstyle | `test-tier-classification.yml` tier-0 |
| Proto breaking changes | `buf breaking` | `platform-ts-browser-a11y-perf.yml` |
| OpenAPI contract compatibility | `contractCompatibilityGate` | `test-tier-classification.yml` tier-0 |

---

## 8. ADR Index

Architecture Decision Records for significant structural decisions:

| ADR | Decision | Date |
|-----|---------|------|
| ADR-001 | Typed Agent Framework | See `docs/adr/ADR-001-typed-agent-framework.md` |
| ADR-002 | DAG Pipeline Execution | See `docs/adr/ADR-002-dag-pipeline-execution.md` |
| ADR-003 | Four-Tier Event Cloud | See `docs/adr/ADR-003-four-tier-event-cloud.md` |

New architectural decisions that deviate from the rules in this document require
an ADR before merging.

---

## 9. Related Documents

- `.github/copilot-instructions.md` — implementation-level conventions
- `docs/architecture/PROPAGATION_CONTRACTS.md` — tenant/auth/correlation/audit
- `docs/GENERATED_ARTIFACT_POLICY.md` — build output policy
- `eslint-rules/ghatana-architecture-rules.js` — TypeScript dependency enforcement
- `platform/typescript/LIBRARY_GOVERNANCE.md` — TypeScript package registry
