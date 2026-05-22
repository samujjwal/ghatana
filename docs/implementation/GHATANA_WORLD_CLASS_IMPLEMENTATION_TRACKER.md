# Deep and Wide Expert Audit — Ultra-Seamless Polyglot Product Development Kernel

**Repository:** `samujjwal/ghatana`  
**Target commit:** `6a42f9b405c3cf7b0bb0b4370d43d3df070f24a0`  
**Commit message:** `build good 1`  
**Audit mode:** full current-snapshot audit and implementation plan, not commit-diff-only review  
**Primary objective:** evolve Ghatana into an extremely easy-to-use, highly effective, performant, polyglot Product Development Kernel that can develop, build, test, package, deploy, verify, promote, roll back, observe, and evolve diverse applications while products focus on business logic only.

---

## 1. Executive Verdict

At commit `6a42f9b405c3cf7b0bb0b4370d43d3df070f24a0`, Ghatana has moved from a Java/TypeScript lifecycle-pilot platform into a credible **early polyglot Kernel foundation**. The snapshot now wires adapters for Java, TypeScript React, TypeScript Node API, Rust/Cargo, Python/pyproject, Docker Buildx, and Compose. It also introduces stronger lifecycle UX commands, recovery/explain surfaces, run-history checks, product interaction brokers, event interaction brokers, interaction performance gates, and cross-product interaction flows.

However, this is not yet the final “any application, any language, business-logic-only product” platform. It is now a strong implementation baseline with several high-priority hardening gaps: product onboarding must be dramatically simpler; Rust/Python support must be proven with real product fixtures; product interaction brokerage must move from foundation to fully governed runtime; plugin interaction needs comparable hardening; Studio must become the primary simplicity layer; and performance must graduate from conformance checks to measurable affected-surface optimization.

### Overall maturity score

| Dimension | Score | Summary |
|---|---:|---|
| Ease of use | 6.5 / 10 | CLI has plan/explain/recover affordances, but product onboarding and Studio guidance are still too manual. |
| Kernel lifecycle capability | 8.0 / 10 | Strong lifecycle planner/executor/adapters/checks exist. Needs clearer UX, performance, and runtime-backed evidence. |
| Polyglot readiness | 7.0 / 10 | Java/TS mature; Rust/Python adapters exist; real multi-language product fixture proof still needs expansion. |
| Product business-logic isolation | 7.5 / 10 | Direction is correct; product-local lifecycle patterns and cross-product coupling must remain aggressively blocked. |
| Product-to-product interaction | 7.0 / 10 | Request and event brokers exist; needs real service-backed handlers, durable evidence, and Studio runtime truth. |
| Plugin-to-plugin interaction | 6.0 / 10 | Plugin API/bus exists; needs production interaction broker parity with product interactions. |
| Common platform feature bridge | 7.0 / 10 | Audit, consent, risk, notification, runtime truth concepts exist; consumption patterns need standardization. |
| Performance/scalability | 5.5 / 10 | Checks exist; affected-surface and cache-aware execution must be implemented end-to-end. |
| Studio/developer experience | 6.0 / 10 | Studio packages exist; seamless lifecycle/product onboarding UX still needs implementation. |
| Production-readiness governance | 8.0 / 10 | Very strong script gates; must keep gates tied to real capabilities, not just scan coverage. |

---

## 2. What Changed Since the Prior Execution Focus

The previous audit at `f53ba36...` correctly emphasized product interactions and polyglot target gaps. At `6a42f9...`, the repo has materially advanced:

1. **Rust/Cargo and Python/pyproject adapters are now registered in the default toolchain registry.**
2. **A TypeScript Node API adapter is now registered.**
3. **Root world-class readiness gates now include Rust/Python adapter conformance, polyglot fixture checks, lifecycle explain/recover, lifecycle run history, product interaction broker, plugin interaction broker, interaction performance, and Studio production profile checks.**
4. **Kernel product CLI now includes lifecycle intent aliases, explain mode, recover mode, adapter contract compliance checks, dry-run gate providers, and human-readable summaries.**
5. **Product interaction request/response and event brokers exist as Java broker implementations with policy/evidence hooks, timeout handling, idempotent replay, metrics, and failure normalization.**

These changes shift the current-state classification from “target architecture” to “existing but needs hardening” for several previously missing capabilities.

---

## 3. Current-State Classification

| Capability | Current state | Expert assessment |
|---|---|---|
| Root world-class gate | Existing and executable | `check:phase8` is broad and now includes polyglot and interaction gates. It is strong but at risk of becoming too large/slow without tiering. |
| Kernel CLI | Existing but partial | Supports flexible product commands, plan/explain/recover, dry-run, and summaries. Needs a friendlier stable user contract and Studio parity. |
| Java lifecycle support | Existing and executable | Gradle Java adapter and Java product surfaces exist. Needs richer test/report/health artifact semantics. |
| TypeScript React lifecycle support | Existing and executable | pnpm/Vite React adapter exists. Needs stronger route, a11y, i18n, and artifact parity. |
| TypeScript Node lifecycle support | Existing but partial | `PnpmNodeApiAdapter` exists. Needs real Node service product fixture and runtime health convention proof. |
| Rust lifecycle support | Existing but partial | `CargoRustAdapter` exists. Needs real product fixture, cargo workspace handling, cross-platform artifact naming, coverage, and packaging depth. |
| Python lifecycle support | Existing but partial | `PythonPyprojectAdapter` exists. Needs real product fixture, venv/uv/poetry strategy, type/lint standard, FastAPI/worker proof. |
| Docker/Compose lifecycle support | Existing and executable | Docker Buildx and Compose adapters exist; environment-blocked classification must remain strict. |
| ProductUnit surface model | Existing but partial | Surface types still need richer language/runtime/build-system semantics. |
| Product interaction request broker | Existing but partial | Java broker exists with policy/evidence/timeout hooks. Needs production persistence, real authz, registry-driven handler resolution. |
| Product interaction event broker | Existing but partial | Event broker exists. Needs durable provider, topic registry, replay/idempotency, DLQ/backpressure. |
| Product interaction manifests | Existing but partial | PHR and DMOS declare interactions. Need broader schema and runtime evidence consistency. |
| Plugin interaction broker | Existing but partial | Checks reference plugin broker, but plugin interaction still trails product interaction maturity. |
| Data Cloud provider bridge | Existing but partial | Important for runtime truth/evidence. Needs stronger interaction evidence provider proof and no plane leaks. |
| YAPPC handoff | Existing but partial | Boundary checks exist; continue keeping Kernel dependent only on contracts/evidence. |
| Studio lifecycle UX | Existing but partial | Needs guided ProductUnit registration, lifecycle launcher, explain/recover UI, interaction graph, polyglot surface detection. |
| Performance | Existing but partial | Scripts reference affected-surface execution and interaction performance, but actual optimization must be proven in planner/executor. |
| PHR pilot | Existing and executable, product incomplete | Good Kernel pilot; real consent/FHIR/workflow depth still needs completion. |
| Digital Marketing pilot | Existing and executable, product incomplete | Good Kernel pilot; real campaign/customer/connector workflows still need completion. |

---

## 4. North-Star Architecture

The Kernel should let a team build a product by declaring:

```yaml
productId: example
surfaces:
  api:
    language: rust
    runtime: service
    source: products/example/api
  web:
    language: typescript
    runtime: react-web
    source: products/example/web
  worker:
    language: python
    runtime: worker
    source: products/example/worker
businessCapabilities:
  - customer-onboarding
  - payment-reconciliation
```

The product team should not configure or understand Cargo, pnpm, Python packaging, Docker, manifests, lifecycle evidence, provider modes, or policy gates beyond product-specific intent.

Kernel must derive:

- lifecycle phases
- adapters
- default commands
- expected outputs
- gates
- artifact manifests
- deployment manifests
- runtime truth
- interaction contracts
- observability expectations
- recovery guidance

---

## 5. Ease-of-Use Audit

### 5.1 What is good

The CLI now supports multiple user-friendly lifecycle paths:

- `product <productId> <phase>`
- `product <phase> <productId>`
- `product <productId> explain <phase>`
- `product <productId> recover`
- aliases such as `develop`, `ship-local`, and `verify-local`
- JSON output
- dry-run mode
- surface selection
- environment selection
- approval/correlation/tenant/workspace/project metadata

This is a strong step toward low-cognitive-load lifecycle execution.

### 5.2 What remains too hard

1. Product authors still need to understand `kernel-product.yaml` too deeply.
2. Surface declarations still require adapter IDs in many cases.
3. Recovery output is useful, but it must become specific to adapter/language/policy/interaction failures.
4. Product registration still needs a wizard/scaffolder that generates registry, workspace, Gradle/pnpm, and manifest entries safely.
5. Studio does not yet appear to be the canonical guided workflow for lifecycle, product setup, interactions, and polyglot surfaces.
6. Many package scripts exist; they are useful wrappers but can overwhelm users without a simpler “one command mental model.”

### 5.3 Required UX target

The preferred user mental model should be:

```bash
pnpm kernel product create
pnpm kernel product <productId> dev
pnpm kernel product <productId> validate
pnpm kernel product <productId> test
pnpm kernel product <productId> build
pnpm kernel product <productId> package
pnpm kernel product <productId> ship-local
pnpm kernel product <productId> verify-local
pnpm kernel product <productId> recover
```

Advanced details should be discoverable, not required.

---

## 6. Polyglot Adapter Audit

### 6.1 Java / Gradle

**Current state:** Strongest backend language path.

**What works:**

- Gradle module registration exists.
- Java products and platform modules are widespread.
- Java checks and ArchUnit usage are established.

**Gaps:**

- Standard Java service health/readiness contract must be abstracted from product conventions.
- Adapter should parse test reports and produce standardized `test-report` evidence.
- Adapter should classify Gradle download/toolchain/cache errors separately.
- Adapter should enforce public API JavaDoc `@doc.*` rules as a lifecycle gate.

**Next tasks:**

- Add Gradle test report parser.
- Add Java artifact manifest fields: module path, main class, jar path, checksum, Java version, test result refs.
- Add Java health convention registry.
- Add Java service fixture product.

### 6.2 TypeScript React / pnpm Vite

**Current state:** Mature web path.

**What works:**

- `PnpmViteReactAdapter` is registered.
- Digital Marketing and PHR web surfaces use pnpm/Vite-style lifecycle.
- Studio and shared UI packages exist.

**Gaps:**

- Route contract, a11y, i18n, bundle budget, API contract parity should become standardized adapter evidence, not just product-local tests.
- Product UI should consume shared route/status/error/empty/loading components consistently.
- Static bundle manifest needs stronger source-to-route-to-artifact linkage.

### 6.3 TypeScript Node API

**Current state:** New/partial.

**What works:**

- `PnpmNodeApiAdapter` exists and handles package path, script selection, preflight, output validation, and artifact manifests.

**Gaps:**

- Needs a real Node API product fixture.
- Needs health/readiness convention for Node services.
- Needs API contract/openapi generation evidence.
- Needs test-report parsing.

### 6.4 Rust / Cargo

**Current state:** New/partial.

**What works:**

- `CargoRustAdapter` exists.
- Supports validate/test/build/package.
- Validate runs `cargo fmt --check`, `cargo check`, and `cargo clippy -- -D warnings`.
- Test runs `cargo test`.
- Build/package runs `cargo build --release`.
- Missing Cargo/toolchain is classified as environment-blocked.

**Gaps:**

- Needs real Rust service/library fixture.
- Needs workspace/member detection.
- Needs binary name resolution from Cargo metadata instead of default product ID.
- Needs cross-platform artifact handling.
- Needs code coverage strategy, probably `cargo llvm-cov` when configured.
- Needs container packaging and service health convention.

**Priority:** P0 for fixture proof, P1 for production hardening.

### 6.5 Python / pyproject

**Current state:** New/partial.

**What works:**

- `PythonPyprojectAdapter` exists.
- Supports validate/test/build/package.
- Preflight checks `pyproject.toml` and Python executable.
- Default validate uses `python -m compileall`.
- Default test uses `python -m pytest`.
- Default build/package uses `python -m build`.
- Custom commands are supported through surface config.

**Gaps:**

- Needs real Python service/worker/library fixture.
- Needs explicit Python environment strategy: `venv`, `uv`, `poetry`, or repo-standard fallback.
- Needs typed API/lint standard: `mypy`/`pyright`, `ruff`, or configured command conventions.
- Needs FastAPI/service health convention.
- Needs wheel/sdist artifact metadata.
- Needs test-report parsing.

**Priority:** P0 for fixture proof, P1 for production hardening.

---

## 7. Product Interaction Audit

### 7.1 Current strength

The platform now has meaningful product interaction primitives:

- `ProductInteractionBroker` for request/response.
- `ProductInteractionEventBroker` for event publish/subscribe.
- `ProductInteractionRequest` and `ProductInteractionOutcome` envelopes.
- `ProductInteractionContract` TypeScript schemas.
- PHR/DMOS interaction declarations.
- interaction runtime truth checks.
- cross-product boundary checks.
- cross-product flow tests.
- interaction performance checks.

### 7.2 Expert assessment

This is a strong architectural direction. The most important design decision is correct: products must not import each other’s internals; they should interact through Kernel contracts, handlers, broker, events, providers, and evidence.

However, the current implementation still needs to move from “broker primitives and focused tests” to “production interaction runtime.”

### 7.3 Priority gaps

| Gap | Severity | Required work |
|---|---:|---|
| Handler discovery | P0 | Add registry-driven handler discovery rather than manual builder registration in tests. |
| Real policy enforcement | P0 | Centralize auth, tenant, purpose, role, consent, degraded-mode policy. |
| Real evidence persistence | P0 | Persist `ProductInteractionEvidenceRecord` via file provider in bootstrap and Data Cloud provider in platform mode. |
| Event durability | P0 | Product event broker must support durable provider, replay, idempotency, DLQ/backpressure. |
| Service-backed handlers | P0 | PHR consent and DMOS preferences must be backed by real domain services, not hardcoded outcomes. |
| Contract version negotiation | P1 | Add compatibility matrix and deprecation policy. |
| Interaction graph | P1 | Studio must show product dependency/interaction graph and runtime statuses. |
| Interaction SLOs | P1 | Add latency/error/throughput metrics with alert thresholds. |
| Security review | P0 | Ensure cross-product interactions cannot exfiltrate PII across tenant/workspace/purpose boundaries. |

---

## 8. Plugin Interaction Audit

### 8.1 Current state

The plugin API ownership ADR remains important: `kernel-plugin` owns platform-facing plugin APIs, while `kernel-core` owns runtime orchestration. A plugin interaction bus already supports request/response and pub/sub patterns. Root scripts now include `check:kernel-plugin-interactions` and `check:plugin-interaction-broker`.

### 8.2 Expert assessment

Plugin interaction must be hardened to match product interaction rigor. Plugin-to-plugin calls can become a hidden coupling vector if not governed. The platform needs typed contracts, topic schema registry, lifecycle dependency graph, cycle detection, version negotiation, policy enforcement, audit, metrics, traces, and failure modes.

### 8.3 Required plugin interaction patterns

1. **Typed request/response** for direct answer needs.
2. **Event publish/subscribe** for loosely-coupled plugin reactions.
3. **Capability lookup** for optional integrations.
4. **Policy-gated delegation** for sensitive capabilities such as consent, audit, risk, notification, identity, and secrets.

### 8.4 Gaps

- Make plugin interaction broker explicit and production-grade.
- Add plugin interaction evidence records.
- Add plugin graph cycle/startup-order validation.
- Add plugin contract compatibility tests.
- Add Studio plugin graph and health UX.

---

## 9. Common Platform Feature Bridge Audit

Common features should be consumed through platform contracts, not reimplemented by products.

| Feature | Current maturity | Required direction |
|---|---:|---|
| Identity/auth | Partial | Centralize across lifecycle, product interactions, plugin interactions, APIs. |
| Authorization | Partial | Use broker-level authorization, not handler-local-only checks. |
| Tenant/workspace scope | Improving | Fail closed in every broker/provider/adapter. |
| Audit | Partial | All lifecycle/product/plugin interactions must emit audit evidence. |
| Consent | Partial | PHR-specific gates exist; broker must enforce purpose-limited consent. |
| Privacy/PII | Partial | Declare PII classification in interaction contracts and enforce in broker. |
| Data sovereignty | Partial | PHR gates exist; interaction evidence must include sovereignty context. |
| Runtime truth | Partial | Needs Data Cloud-backed runtime truth across lifecycle/interactions. |
| Notifications | Partial | DMOS bridge uses notification plugin; need durable retry/DLQ proof everywhere. |
| Risk | Partial | Risk plugin is used by DMOS bridge; needs lifecycle/product interaction standard. |
| Observability | Partial | Add standard spans/metrics/logs for adapters, brokers, providers, gates. |
| i18n/a11y | Partial | Shared UI exists; must be enforced on product and Studio lifecycle pages. |

---

## 10. Studio and Developer Experience Audit

### 10.1 Target Studio experience

Studio should be the product team’s control tower:

- register product
- detect surfaces/languages
- view lifecycle plan
- run lifecycle phase
- explain what Kernel will do
- see run history
- see artifacts and evidence
- see product and plugin interactions
- see health/runtime truth
- recover from failures
- promote/rollback safely

### 10.2 Gaps

- Guided ProductUnit registration wizard is still needed.
- Polyglot surface detection should be visual and actionable.
- Interaction graph is needed for PHR↔DMOS and future products.
- Failure recovery must map to adapter/language/policy/interaction-specific fixes.
- Studio must hide raw manifests by default but allow debug drill-down.

---

## 11. Performance and Scalability Audit

### 11.1 Current state

Root gates now include interaction performance and affected-surface execution checks, but the platform still needs full proof that it can scale to many products/surfaces without slow full-repo execution.

### 11.2 Required capabilities

- affected product resolution
- affected surface resolution
- adapter-level cache keys
- artifact fingerprint reuse
- test tier selection by changed files
- parallel execution with dependency graph
- lifecycle run duration baselines
- slow-step diagnostics
- interaction latency metrics
- large-repo regression tests

### 11.3 Key risks

- `check:phase8` can become too slow for day-to-day development.
- Deep world-class gate should be tiered into fast, focused, nightly, and release gates.
- Polyglot adapters can create heavy toolchain preflights unless cached.

---

## 12. PHR Pilot Audit

### 12.1 Current strength

PHR is enabled as a second lifecycle pilot with healthcare gates and product interactions. It provides `phr.consent-status.v1` and consumes DMOS notification preference.

### 12.2 Critical gaps

- Consent status interaction must be backed by real consent domain logic.
- FHIR validation must be robust enough for pilot workflows, not minimal structural checks.
- Healthcare gates must generate real evidence, not just file refs.
- PHR UI must show consent, sharing, audit, data sovereignty, and interaction evidence in a simple way.
- PHR rollback remains target/partial and must be safe before enablement.

### 12.3 PHR next development tickets

1. Implement real consent service and wire `ConsentStatusInteractionHandler` to it.
2. Add subject/patient authorization enforcement.
3. Add FHIR R4 resource coverage tests for Patient, Observation, Encounter, Condition, Medication, Allergy, Lab Result, Immunization, DocumentReference.
4. Add consent-revoked event publication.
5. Add PHR interaction audit page.
6. Add healthcare post-rollback verification gates.

---

## 13. Digital Marketing Pilot Audit

### 13.1 Current strength

Digital Marketing is the validated lifecycle pilot and now declares interactions with PHR. It provides notification preference and consumes PHR consent status.

### 13.2 Critical gaps

- Notification preference handler must be backed by real user/customer preferences.
- Campaign activation must call PHR consent status through Kernel broker before launch.
- Lead-captured event must be durable, consent-aware, and privacy-limited.
- Google Ads connector readiness must never fake success.
- DMOS UI must show consent-blocked/degraded states clearly.

### 13.3 DMOS next development tickets

1. Implement real notification preference service.
2. Add campaign activation consent preflight.
3. Add lead-captured event emission through product event broker.
4. Add interaction evidence on campaign activation.
5. Add UI blocked/degraded state for healthcare consent dependency.
6. Add connector readiness and DLQ visibility.

---

## 14. Data Cloud and YAPPC Boundary Audit

### 14.1 Data Cloud

Data Cloud is the correct platform-backed provider direction for runtime truth, evidence, events, provenance, health, and interaction history. Kernel must continue consuming it only through provider/bridge contracts.

Required work:

- Data Cloud product interaction evidence provider must be fully wired.
- Route/runtime truth must remain drift-checked.
- Data Cloud plane internals must remain isolated from Kernel.
- Event durability and replay should use Data Cloud provider contracts.

### 14.2 YAPPC

YAPPC should own higher-order artifact intelligence, repository interpretation, generation plans, and product intent generation. Kernel may consume ProductUnitIntent, semantic artifact refs, generated change-set summary, dependency graph evidence, and risk hotspot reports — never YAPPC compiler/decompiler internals.

---

## 15. Gap Matrix

| ID | Area | Current state | Desired state | Concrete task | Severity | Owner |
|---|---|---|---|---|---:|---|
| UX-001 | Product onboarding | Manual manifests | Guided wizard/scaffold | Build Studio + CLI product registration flow | P0 | Studio/Kernel CLI |
| UX-002 | Lifecycle simplicity | Many scripts + flexible CLI | One mental model | Document and enforce `pnpm kernel product <id> <phase>` | P0 | Kernel CLI |
| UX-003 | Recovery | Generic recover output | Failure-specific recovery | Map adapter/policy/interaction failures to concrete fixes | P0 | Kernel lifecycle |
| POLY-001 | Rust fixture | Adapter exists | Real product proof | Add Rust service/library fixture through full lifecycle | P0 | Kernel toolchains |
| POLY-002 | Python fixture | Adapter exists | Real product proof | Add Python FastAPI/worker fixture through full lifecycle | P0 | Kernel toolchains |
| POLY-003 | Node API fixture | Adapter exists | Real Node service proof | Add TypeScript Node API fixture | P1 | Kernel toolchains |
| POLY-004 | Surface model | Type-based | Language/runtime/build-system model | Extend ProductUnit surface contract | P0 | Kernel contracts |
| PERF-001 | Affected execution | Checks exist | Real optimization | Implement surface hash/dependency graph and skip-safe execution | P0 | Kernel lifecycle |
| PERF-002 | Phase8 speed | Large all-in gate | Tiered gates | Split fast/focused/nightly/release gate profiles | P1 | Repo governance |
| INT-001 | Product broker | Broker exists | Production registry-driven runtime | Add handler discovery, policy, evidence provider integration | P0 | Kernel core |
| INT-002 | Product events | Event broker exists | Durable event broker | Add event provider, replay, DLQ, idempotency | P0 | Kernel/Data Cloud |
| INT-003 | Interaction handlers | Simple examples | Real service-backed handlers | Wire PHR consent and DMOS preference to real services | P0 | PHR/DMOS |
| INT-004 | Interaction Studio | Missing | Graph/timeline/evidence UI | Add product interaction UI in Studio | P1 | Studio |
| PLUG-001 | Plugin broker | Partial | Production-grade governed plugin interactions | Add typed contracts/evidence/metrics/cycles | P0 | kernel-plugin |
| PLUG-002 | Plugin cycle detection | Partial | fail-closed graph validation | Add plugin graph check and startup-order planner | P0 | kernel-plugin |
| OBS-001 | Observability | Partial | standard lifecycle/adapter/broker telemetry | Add metrics/spans/log events and dashboards | P0 | platform observability |
| SEC-001 | Interaction security | Partial | central policy enforcement | Broker-level auth/tenant/purpose/consent checks | P0 | Kernel core/security |
| PHR-001 | Consent interaction | Handler exists | real consent-backed result | Wire handler to consent domain | P0 | PHR |
| DMOS-001 | Campaign consent | Declared consume | launch-time broker preflight | Add campaign activation consent check | P0 | Digital Marketing |
| DC-001 | Evidence provider | Partial | durable interaction evidence | Data Cloud-backed interaction evidence provider | P0 | Data Cloud bridge |
| STUDIO-001 | Lifecycle UX | Partial | simple product control tower | Add lifecycle run/explain/recover/interaction graph views | P0 | Studio |

---

## 16. Phased Roadmap

### Phase 0 — Snapshot truth and baseline

**Build/fix:**

- freeze current state classification
- document actual adapter readiness
- document actual interaction readiness
- identify product-local lifecycle leaks
- identify direct product references

**Validate:**

```bash
pnpm check:product-registry
pnpm check:domain-registry
pnpm check:architecture-boundaries
pnpm check:product-interaction-contracts
pnpm check:cross-product-interaction-boundaries
```

### Phase 1 — Extreme ease-of-use Kernel UX

**Build/fix:**

- stable CLI grammar
- explain/recover summaries
- Studio lifecycle launcher
- guided ProductUnit registration
- progressive disclosure of manifests/evidence

**Validate:**

```bash
pnpm check:kernel-product-cli
pnpm check:lifecycle-explain-recover
pnpm check:lifecycle-run-history
pnpm check:studio-kernel-api
```

### Phase 2 — Polyglot foundation hardening

**Build/fix:**

- product surface language/runtime model
- real Rust fixture
- real Python fixture
- real Node API fixture
- artifact manifest normalization across languages

**Validate:**

```bash
pnpm check:java-adapter-conformance
pnpm check:typescript-web-adapter-conformance
pnpm check:rust-adapter-conformance
pnpm check:python-adapter-conformance
pnpm check:polyglot-product-fixture
```

### Phase 3 — Product interaction runtime hardening

**Build/fix:**

- handler registry discovery
- broker policy evaluator
- evidence writer
- Data Cloud-backed interaction evidence provider
- event durability/replay/DLQ
- Studio interaction graph

**Validate:**

```bash
pnpm check:product-interaction-broker
pnpm check:interaction-runtime-truth
pnpm check:interaction-performance
pnpm check:cross-product-interaction-flows
```

### Phase 4 — Plugin interaction runtime hardening

**Build/fix:**

- plugin interaction contracts
- plugin interaction broker
- plugin topic registry
- cycle detection
- lifecycle ordering
- plugin interaction evidence

**Validate:**

```bash
pnpm check:kernel-plugin-interactions
pnpm check:plugin-interaction-broker
```

### Phase 5 — Common platform bridge completion

**Build/fix:**

- identity/authz
- consent
- audit
- risk
- notification
- runtime truth
- observability
- secrets/config
- i18n/a11y/product shell

**Validate:**

```bash
pnpm check:bridge-compliance
pnpm check:observability-conformance
pnpm check:security-workflow-coverage
pnpm check:secret-default-credentials
```

### Phase 6 — PHR full pilot completion

**Build/fix:**

- healthcare workflows
- FHIR validation
- real consent service
- real interaction evidence
- PHR interaction/audit UI
- rollback readiness

**Validate:**

```bash
pnpm validate:phr
pnpm test:phr
pnpm build:phr
pnpm package:phr
pnpm deploy:local:phr
pnpm verify:local:phr
pnpm check:phr-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/phr
```

### Phase 7 — Digital Marketing full pilot completion

**Build/fix:**

- campaign workflows
- customer/lead workflows
- connector readiness
- real preference service
- PHR consent preflight
- lead-captured event

**Validate:**

```bash
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
pnpm check:digital-marketing-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/digital-marketing
```

### Phase 8 — Performance and production hardening

**Build/fix:**

- affected-surface execution
- parallel execution safety
- cache keys
- lifecycle timing dashboards
- interaction SLOs
- evidence retention
- failure injection

**Validate:**

```bash
pnpm check:affected-surface-execution
pnpm check:interaction-performance
pnpm check:phase8
pnpm check:world-class-platform-readiness
```

---

## 17. Testing Strategy

### Kernel tests

- ProductUnit contracts
- ProductInteractionContract schemas
- lifecycle planner
- lifecycle executor
- explain/recover/run-history
- adapter contract compliance
- artifact/deployment/release/rollback manifests
- product interaction request broker
- product interaction event broker
- plugin interaction broker
- failure classification
- performance timing

### Adapter tests

- Gradle Java adapter
- pnpm Vite React adapter
- pnpm Node API adapter
- Cargo Rust adapter
- Python pyproject adapter
- Docker Buildx adapter
- Compose adapter
- polyglot fixture product

### Product tests

- PHR domain/FHIR/consent/audit/gates/UI/E2E
- DMOS campaign/customer/lead/analytics/connector/UI/E2E
- PHR↔DMOS request-response interactions
- PHR↔DMOS event interactions
- degraded/blocked states

### Boundary tests

- no direct product internals
- no product-local lifecycle runner
- no Data Cloud plane imports from Kernel
- no YAPPC internal imports from Kernel
- no plugin API ownership drift
- no production stubs/fake success

---

## 18. Validation Strategy

Every validation command must report one of:

- code failure
- product failure
- test failure
- environment blocked
- dependency blocked
- configuration blocked
- policy blocked
- interaction blocked
- provider unavailable

Environment-blocked Docker, Rust, Python, or external provider failures must not be converted to success.

Core validation set:

```bash
pnpm check:phase0
pnpm check:phase1
pnpm check:phase2
pnpm check:phase3
pnpm check:phase4
pnpm check:phase5
pnpm check:phase6
pnpm check:phase7
pnpm check:phase8
pnpm check:world-class-platform-readiness
```

The fast local development validation should be split from world-class validation:

```bash
pnpm check:kernel-product-cli
pnpm check:product-interaction-contracts
pnpm check:rust-adapter-conformance
pnpm check:python-adapter-conformance
pnpm check:polyglot-product-fixture
```

---

## 19. Definition of Done

The Kernel reaches the target when:

1. A product can be registered through Studio or CLI without hand-editing multiple registry/workspace files.
2. Java, TypeScript, Rust, and Python surfaces can run through the same lifecycle model.
3. Product teams do not write lifecycle runners.
4. Product teams do not wire generic audit/auth/consent/notification/risk/runtime-truth plumbing.
5. Product-to-product interactions are brokered, policy-gated, evidenced, observable, and never direct imports.
6. Plugin-to-plugin interactions are brokered, typed, observable, and cycle-safe.
7. All lifecycle phases support plan, explain, execute, summarize, recover.
8. Studio provides simple status and recovery UX with advanced details hidden by default.
9. Performance is proven through affected-surface execution and timing metrics.
10. Validation proves real capability instead of script-only checklist compliance.

---

## 20. Highest-Priority Engineering Tickets

1. **Implement real ProductInteractionBroker registry discovery.**
2. **Wire ProductInteractionEvidenceWriter to bootstrap file and Data Cloud provider modes.**
3. **Back PHR consent status handler with real consent domain service.**
4. **Back DMOS notification preference handler with real preference service.**
5. **Add durable ProductInteractionEventBroker provider with replay/DLQ/idempotency.**
6. **Add Rust fixture product and run full validate/test/build/package lifecycle.**
7. **Add Python fixture product and run full validate/test/build/package lifecycle.**
8. **Extend ProductUnit surface model with language/runtime/buildSystem.**
9. **Add Studio guided product registration and polyglot surface detection.**
10. **Add lifecycle failure-specific recovery guidance.**
11. **Add affected-surface execution with cacheable fingerprints.**
12. **Add plugin interaction broker parity with product interaction broker.**
13. **Split phase8 into fast/focused/nightly/release gates for performance.**
14. **Add interaction graph and evidence timeline to Studio.**
15. **Finish PHR and DMOS real product workflows, not just lifecycle shells.**
