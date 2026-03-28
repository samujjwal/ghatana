# AI Agent Instructions for the Ghatana Repository

> **Purpose**: This file is the repo-specific source of truth for AI-assisted changes in Ghatana.
> **Rule of precedence**: Follow the conventions already present in the touched workspace. Do not introduce a new stack, architecture, naming scheme, or library pattern when the repo already has an established one.
> **Last Updated:** 2026-03-27

## 1. Non-Negotiable Repo Rules

1. **Reuse before creating**. Check `platform/*`, the relevant `products/*` area, and existing contracts before adding new abstractions.
2. **Do not deviate from the existing Ghatana repo shape**. Extend the current patterns of the specific module you are changing instead of importing a generic outside style guide.
3. **Keep boundaries explicit**. Domain logic must not silently leak into transport, UI, persistence, or infra glue.
4. **No silent failures**. Errors must be surfaced, logged, and testable.
5. **No hardcoded secrets or unsafe defaults**. Validate untrusted input at boundaries.
6. **Zero-warning mindset**. Keep lint, formatting, static checks, and build health clean.
7. **Tests are part of the change**. Add or update the right level of test for every meaningful behavior change or bug fix.
8. **Public Java APIs require documentation tags**. Keep JavaDoc and required `@doc.*` tags aligned with the existing doc-tag checks.
9. **Prefer existing dependencies**. Do not add overlapping libraries without a clear repo-specific need.
10. **Make observability part of the feature**. Important flows should be diagnosable through logs, metrics, traces, and health signals.

## 2. How Ghatana Is Organized

Use the repo’s real structure and dependency direction when making changes:

- `platform/contracts`: shared platform-level contracts.
- `platform/java/*`: shared Java platform modules such as `core`, `database`, `http`, `observability`, `security`, `testing`, `workflow`, `agent-core`, and related infrastructure.
- `platform/typescript/*`: shared TypeScript packages such as API helpers, design system, tokens, theme, realtime, and utilities.
- `products/*`: product-specific systems and applications such as `aep`, `data-cloud`, `dcmaar`, `flashit`, `software-org`, `tutorputor`, `virtual-org`, and `yappc`.
- Product-local workspaces may contain their own `pnpm-workspace.yaml`, `Cargo.toml`, Gradle build, mobile apps, or web apps. Respect local conventions inside those workspaces.
- Supporting repo-level areas like `docs/`, `monitoring/`, `config/`, and Gradle guardrails are part of the engineering contract and should stay coherent with code changes.

### Dependency Guidance

- Prefer downward, explicit dependency flow.
- Shared platform modules should stay generic and product-agnostic.
- Product-specific logic belongs in the owning product area, not in a shared platform package.
- Do not turn shared packages into dumping grounds.
- Keep transport models, domain models, persistence models, and event schemas distinct where boundaries matter.

## 3. Core Engineering Principles

### Design

- Prefer clarity over cleverness.
- Optimize for maintainability, debuggability, correctness, and observability before micro-optimizing.
- Keep business logic explicit, testable, and light on framework coupling.
- Favor composition over inheritance unless the existing module clearly uses inheritance well.
- Avoid hidden magic, opaque meta-programming, and abstractions the team cannot debug quickly.

### Organization

- Structure code by capability or domain where the surrounding module does the same.
- Keep files and classes focused on one reason to change.
- Split modules only when the new boundary is meaningful and consistent with nearby code.
- Avoid vague module names like `helpers`, `misc`, `common`, or `utils` unless the scope is genuinely tight and obvious.

### Naming

- Use names that reflect the domain and intent.
- Prefer `AccountSummaryService`, `PolicyEvaluator`, or `CreateIncidentUseCase` over `Helper`, `Manager`, or `Service2`.
- Avoid abbreviations unless they are already standard in the repo or domain.

### Errors and Failure Handling

- Errors should be categorized, actionable, observable, and non-silent.
- Do not swallow exceptions.
- Return domain-safe messages to callers and keep internal detail in logs/telemetry.
- Model retries, timeouts, circuit breaking, or fallback behavior explicitly where the surrounding system needs them.

### Testing

Choose the right mix for the touched surface:

- Unit tests for business logic and transformations.
- Integration tests for boundaries such as database, filesystem, message flows, or external adapters.
- Contract tests for APIs, schema-driven integrations, or event payloads.
- End-to-end tests for meaningful user or workflow paths.
- Add regression coverage for every important bug fix.

### Observability and Operations

- Emit structured logs for important state changes and failures.
- Use metrics and traces for critical flows and performance-sensitive paths.
- Preserve correlation or request IDs where the surrounding system supports them.
- Keep readiness/health semantics correct for deployable services.

## 4. Repo-Specific Java Standards

Ghatana has strong Java conventions enforced by Gradle guardrails and shared platform modules.

### Required Practices

- Java toolchain is Java 21 unless the module explicitly says otherwise.
- Reuse `platform:java:*` modules before creating new infrastructure code.
- Keep domain logic separate from framework wiring and persistence details.
- Prefer constructor injection.
- Avoid field injection.
- Keep exceptions meaningful and layered.
- Use immutable objects and records where they improve clarity.
- Use composition over inheritance by default.

### Async and Concurrency

- In ActiveJ-based code, use `Promise` and the event loop model consistently.
- Never block the event loop.
- Wrap blocking I/O with the approved async bridge such as `Promise.ofBlocking(...)` when working in ActiveJ flows.
- Do not mix incompatible async paradigms in the same flow unless the module already has a clear adapter boundary.

### Testing

- Follow the existing test harness in the touched module.
- For ActiveJ async tests, extend `EventloopTestBase`.
- Use `runPromise(() -> ...)` for promise-based execution.
- Do not call `.getResult()` directly on ActiveJ promises in tests.

**Meaningful example: ActiveJ async test**

```java
@DisplayName("My Service Tests")
class MyServiceTest extends EventloopTestBase {
    @Test
    void shouldProcessAsync() {
        MyService service = new MyService();

        String result = runPromise(() -> service.processAsync("input"));

        assertThat(result).isEqualTo("expected");
    }
}
```

### Shared Platform Abstractions

Prefer existing platform modules when they fit:

- HTTP concerns: `platform:java:http`
- Database concerns: `platform:java:database`
- Observability concerns: `platform:java:observability`
- Security concerns: `platform:java:security`
- Testing utilities: `platform:java:testing`
- AI integration concerns: `platform:java:ai-integration`

Do not bypass these with ad hoc local infrastructure unless there is a strong module-specific reason.

### Java Documentation Tags

Public Java classes should include the required documentation tags enforced by the repo:

```java
/**
 * @doc.type class
 * @doc.purpose Processes incoming events for tenant-scoped workflows.
 * @doc.layer product
 * @doc.pattern Service
 */
```

Add more specific tags only where the existing module conventions require them.

## 5. TypeScript Standards

TypeScript is used across platform packages, web apps, backend services, browser extensions, desktop apps, and product-specific workspaces.

### Language Rules

- Use strict TypeScript.
- Do not introduce `any` except in tightly justified boundary adapters.
- Prefer `unknown` over `any`.
- Keep async flows explicit and never ignore promises.
- Prefer discriminated unions over stringly typed state machines.
- Separate DTOs, domain models, persistence models, and wire contracts when the boundary is important.

### Style and Design

- Keep functions small and single-purpose.
- Prefer early returns over deep nesting.
- Keep transformation logic pure where practical.
- Avoid giant multi-responsibility files.
- Keep framework-specific code thin around domain logic.

### Tooling

Use the tools already present in the touched workspace:

- Package management: `pnpm`
- Formatting: `Prettier` where configured
- Linting: `ESLint`
- Type checking: `tsc`
- Tests: `Vitest` or `Jest`, based on the local package
- E2E: `Playwright` where applicable
- Runtime validation: `Zod` where boundary validation already uses schemas
- Build/workspace orchestration: Turborepo or workspace-native tooling already checked into the repo

Do not force a single frontend/backend tool choice across the entire monorepo when the local workspace already has an established setup.

**Meaningful example: validate at the boundary**

```ts
import { z } from "zod";

const CreateAgentRequest = z.object({
  tenantId: z.string().min(1),
  name: z.string().min(1),
  mode: z.enum(["active", "draft"]),
});

type CreateAgentRequest = z.infer<typeof CreateAgentRequest>;

export function parseCreateAgentRequest(input: unknown): CreateAgentRequest {
  return CreateAgentRequest.parse(input);
}
```

## 6. React and Frontend Standards

Apply these rules in React web apps and shared UI packages unless the local app already has a more specific documented pattern.

### UI and Component Design

- Use functional components.
- Keep UI simple, readable, and action-oriented.
- Keep components focused: presentational, composed container, or domain widget.
- Extract reusable components only after a real reuse pattern appears.
- Avoid hiding business logic in JSX.

### State Management

Prefer the repo’s existing layered approach where present:

- Server state: `TanStack Query`
- Local UI state: component state first
- Shared app state: `Jotai` where already used
- Form state: `React Hook Form` for non-trivial forms

Avoid:

- redundant derived state
- global state for local concerns
- mixing unrelated state patterns in the same feature

### Styling and Accessibility

- Follow the styling system already used by the touched app.
- Prefer Tailwind in apps that already standardize on Tailwind.
- Prefer existing design-system components and tokens when available.
- Do not mix competing styling approaches without a clear reason.
- Use semantic HTML, keyboard support, proper labels, focus states, and accessible contrast.

### Frontend Testing

- Test user behavior rather than implementation details.
- Use React Testing Library with the package’s current test runner.
- Use Playwright for meaningful browser-level flows where the app already supports E2E coverage.

**Meaningful example: server state stays out of ad hoc effect code**

```tsx
function AgentList() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["agents"],
    queryFn: fetchAgents,
  });

  if (isLoading) return <LoadingState />;
  if (isError) return <ErrorState />;
  if (!data?.length) return <EmptyState />;

  return <AgentTable agents={data} />;
}
```

## 7. React Native and Mobile Standards

- Reuse shared domain logic where it genuinely helps, but do not force shared UI across web and mobile.
- Respect platform expectations instead of copying desktop/web mental models directly.
- Handle offline behavior, retries, app backgrounding, permissions, and large lists explicitly.
- Be conscious of render cost, battery, memory, and network instability.
- Use the testing stack already configured in the relevant mobile app.
- For repo-specific React Native testing guidance, follow local docs such as the Guardian mobile testing guidance instead of inventing a new pattern.

## 8. Python Standards

Python appears in automation, AI, data, and service workflows. Keep it disciplined.

- Type annotate public functions and important internals.
- Keep modules focused and production code separate from experiments or notebooks.
- Prefer explicit models over passing loose dictionaries everywhere.
- Use the workspace’s configured tooling such as `ruff`, `pytest`, `mypy`, `pyright`, `uv`, or `poetry` where present.
- Validate service boundaries with typed schemas such as `Pydantic` when the module uses them.
- Avoid hidden global mutable state in service code.

## 9. Rust Standards

Rust is used in selected product and adapter areas. Keep it practical.

- Use Rust where reliability, performance, safety, plugin isolation, or systems-level control clearly matter.
- Prefer clear ownership boundaries and small, well-scoped crates.
- Keep `unsafe` isolated, documented, and justified.
- Model invariants in types where that improves correctness without over-complicating the code.
- Use standard tooling such as `cargo`, `rustfmt`, `clippy`, and crate-native tests.
- Avoid over-engineered trait hierarchies before the abstraction need is proven.

## 10. Infrastructure and Platform Operations

- Infrastructure changes must be reproducible, reviewable, secure, and automated.
- Prefer configuration as code over manual drift.
- Keep app config, infra config, and secrets separate.
- Every deployable service should have correct health semantics, observability hooks, and a rollback-aware delivery path.
- Keep networking private-by-default where possible, use TLS by default, and enforce least privilege.
- Reuse repo-standard monitoring and deployment patterns in `monitoring/`, Docker Compose files, Gradle automation, and GitHub Actions workflows before creating a new one.

## 11. Contracts, Events, and Compatibility

- Define and evolve contracts intentionally.
- Use explicit schemas for APIs, events, and shared protocol boundaries.
- Include metadata such as IDs, timestamps, source, correlation IDs, and schema version where the integration pattern expects them.
- Design consumers to be idempotent when processing events or retries.
- Breaking changes require a migration or compatibility strategy.

## 12. Library Governance

Before adding a new dependency, justify it against the existing repo:

- Why the current stack cannot solve the problem well enough.
- Maintenance quality and adoption.
- Security and license posture.
- Bundle size or runtime impact.
- Team familiarity and long-term support cost.

Prefer well-maintained, composable libraries already aligned with the repo. Avoid duplicate libraries solving the same problem.

## 13. Pull Request and Change Quality

Every coherent change should include:

- a clear purpose
- focused scope
- design notes when the tradeoff is non-obvious
- test evidence
- migration or rollout notes when applicable

Avoid mixing unrelated refactors into feature or bug-fix changes.

## 14. Practical Default Decision Rules

When in doubt:

- choose the simpler design
- choose fewer dependencies
- choose stronger typing
- choose explicit contracts
- choose better observability
- choose maintainability over novelty
- choose the local Ghatana convention over a generic external best practice

## 15. Definition of Done

Code is not done until all of the following are true:

- It follows the existing conventions of the touched Ghatana module.
- Existing shared platform code was checked before creating new abstractions.
- The change builds, types, or compiles in the relevant workspace.
- Relevant tests were added or updated and pass where feasible.
- Formatting, linting, and static checks remain healthy.
- Public Java APIs include required JavaDoc and `@doc.*` tags.
- Errors and important flows are observable.
- Inputs are validated at the correct boundaries.
- The change does not introduce repo drift in architecture, naming, or dependency choices.
