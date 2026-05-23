# Deep & Wide Production-Readiness Audit and Implementation Plan

**Repo:** `samujjwal/ghatana`  
**Target commit:** `5323e76480f52d237d7adb4142d1258a82b2dc4d`  
**Commit message:** `daf fdsa f`  
**Audit mode:** static expert review and implementation planning; long-running verification intentionally deferred  
**Primary goal:** progress Ghatana toward an extremely easy-to-use, highly effective, performant, production-ready Product Development Kernel that can develop, build, test, package, deploy, verify, promote, rollback, observe, and evolve diverse applications while keeping product teams focused on business logic only.

---

## 1. Executive Summary

The current snapshot is materially more advanced than earlier lifecycle-only states. The Kernel platform now has:

- broader world-class gates in root scripts, including product interaction broker, plugin interaction broker, interaction performance, Rust/Python adapter conformance, polyglot fixture checks, lifecycle explain/recover, lifecycle run history, and Studio production profile checks;
- execution-ready Java, TypeScript web, TypeScript Node API, Rust/Cargo, Python/pyproject, Docker Buildx, and Compose adapters registered in the toolchain registry;
- ProductInteraction request/response and event brokers in Kernel core;
- manifest-declared PHR and Digital Marketing interactions;
- Data Cloud-backed product interaction evidence provider;
- plugin interaction bus with typed envelopes, policy evaluation hooks, evidence writer support, audit records, and metrics;
- CLI support for `plan`, `explain`, `recover`, lifecycle aliases, JSON output, dry-run, approval flags, tenant/workspace/project context, and adapter contract compliance checks.

This is strong progress toward a production-grade platform. The main remaining issue is not “missing everything”; it is **production depth and consistency**. Several capabilities are now present but still need hardening so they can be trusted for real products and diverse languages:

1. **World-class scripts may be brittle because many Gradle npm scripts use `gradlew` instead of `./gradlew`.** This is a static stability bug that can break local/CI execution depending on PATH.
2. **Adapters are execution-ready but not yet production-ready.** Rust/Python/Node support exists, but production packaging, runtime service conventions, report parsing, SLO evidence, and cross-platform details still need depth.
3. **Product interaction brokers exist, but the production runtime path must be completed end-to-end.** Broker logic, policy, evidence, and event paths exist; now they must be wired to real product services, observability backends, Data Cloud runtime truth, and Studio UX.
4. **PHR and Digital Marketing interaction handlers still need real domain backing.** Earlier handlers were simple; even if tests pass, production readiness requires real consent and preference services, not static outcomes.
5. **Platform mode remains incomplete.** The CLI still rejects platform mode unless a Data Cloud-backed provider bridge is registered; production systems need platform mode as a first-class path.
6. **Studio must become the default easy path.** CLI has improved, but Studio still needs guided product registration, lifecycle action launcher, interaction graph, recovery UX, and production-profile status.
7. **Evidence quality must move from “files exist and gates run” to “behavior is proven.”** Verification should remain deferred during this audit, but the implementation plan must target behavior-level proof.

---

## 2. Current-State Classification

| Area | State | Expert assessment |
|---|---:|---|
| Kernel CLI lifecycle UX | Existing but partial | CLI supports product/phase, plan, explain, recover, aliases, JSON, dry-run, approval and context flags. Needs UX polish, stable commands, and fewer script variants. |
| ProductUnit surface model | Existing but partial | Product surfaces now include optional `language`, `runtime`, `buildSystem`, `cratePath`, `cargoToml`, `pyprojectPath`. Needs stricter enum contracts and guided detection. |
| Java adapter | Existing and executable | Gradle Java adapter is execution-ready, but production report parsing, SLO, doc/ArchUnit integration, and error taxonomy need depth. |
| TypeScript web adapter | Existing and executable | Vite/React path exists. Needs stronger route/a11y/i18n/report evidence and Playwright integration as an adapter, not only script gate. |
| TypeScript Node adapter | Existing and executable | `PnpmNodeApiAdapter` exists. Needs service runtime/dev orchestration, health/readiness conventions, and Node package metadata manifests. |
| Rust adapter | Existing but partial | `CargoRustAdapter` runs fmt/check/clippy/test/build. Needs cargo metadata parsing, cross-platform binary naming, workspace support, test report extraction, and production service packaging. |
| Python adapter | Existing but partial | `PythonPyprojectAdapter` exists. Needs venv/package-manager strategy, pyproject tool discovery, pytest report parsing, mypy/ruff/pyright policy, and production service packaging. |
| Docker Buildx adapter | Existing but partial | Execution-ready. Must keep environment-blocked Docker failures honest and add image digest/SBOM/provenance only when real. |
| Compose adapter | Existing but partial | Local deploy/verify/promotion/rollback support exists. Production/staging are intentionally blocked. Needs env validation and rollback depth. |
| Product interaction request broker | Existing but partial | Broker validates request, policy, timeout, handler, evidence writer, metrics. Needs central registry, auth integration, runtime evidence, and real product services. |
| Product interaction event broker | Existing but partial | Event broker publishes to subscribers with evidence and metrics. Needs durable event provider, idempotency, replay, DLQ, and backpressure. |
| Plugin interaction bus | Existing but partial | Typed envelopes, policy, audit, evidence writer, metrics exist in default bus. Needs production-grade broker semantics, durable pub/sub, dependency graph, circuit breaking. |
| Data Cloud interaction evidence | Existing but partial | Provider exists and persists typed interaction evidence through Data Cloud adapter. Needs schema lifecycle, retention, query/readback, and runtime-truth visualization. |
| PHR pilot | Existing but partial | Enabled with lifecycle, healthcare gates, interactions. Needs real domain-backed interactions, FHIR/consent completeness, healthcare UI/E2E depth. |
| Digital Marketing pilot | Existing but partial | Enabled with lifecycle, interactions, DMOS bridge. Needs complete campaign workflows, connector readiness, consent enforcement, reporting, UI journeys. |
| Studio | Existing but partial | Strong artifact/lifecycle packages exist, but must become the primary low-cognitive-load UI for registration, lifecycle, evidence, interactions, and recovery. |
| Data Cloud bridge | Existing but partial | Kernel bridge and evidence provider exist. Must avoid plane internals and support production provider mode. |
| YAPPC handoff | Existing but partial | Boundary checks exist. Must remain ProductUnitIntent/artifact-evidence handoff only. |
| Long-running verification | Deferred by request | Do not spend time running full gates here; use static review and prescribe focused verification. |

---

## 3. Critical Static Bugs and Stability Risks

### 3.1 Root scripts call `gradlew` without `./gradlew`

Several npm scripts use `gradlew ...` instead of `./gradlew ...`. On common Unix shells, the current working directory is not automatically on `PATH`, so these commands can fail even when `gradlew` exists in the repo root.

**Impact:** phase8/world-class checks can fail for environment/path reasons unrelated to code quality.

**Fix:** update all root package scripts that invoke Gradle to call `./gradlew` unless a cross-platform wrapper abstraction is introduced.

**Likely touched:**

- `package.json`
- scripts that generate package scripts
- docs mentioning commands

**Regression:** add `scripts/check-gradle-wrapper-script-usage.mjs` to fail on root scripts containing `"gradlew ` without `"./gradlew `.

### 3.2 CLI platform mode is explicitly not registered

`scripts/kernel-product.mjs` rejects `--mode platform` with an error stating that platform mode requires the Data Cloud-backed provider bridge not registered in the snapshot.

**Impact:** production-grade Kernel cannot be considered complete while the primary production provider mode is unavailable.

**Fix:** implement provider mode registry with bootstrap and platform implementations, allowing platform mode to use Data Cloud-backed providers for registry, events, artifacts, health, approvals, provenance, runtime truth, and interaction evidence.

### 3.3 Interaction brokers are present but not yet product-service complete

The broker infrastructure is the right abstraction, but product handlers must be backed by real domain services. Static or simplified handler responses are unacceptable for production.

**Fix:** make PHR `ConsentStatusInteractionHandler` call the real consent domain service and audit trail. Make DMOS `NotificationPreferenceInteractionHandler` call the real notification preference service/persistence layer.

### 3.4 Adapter registry mixes execution-ready and declared-only adapters

The registry correctly marks some adapters `declared-only` or `planned`, but the UX and validation must ensure product teams never believe declared-only adapters are usable.

**Fix:** Kernel plan/explain must show `execution-ready`, `declared-only`, and `blocked` clearly. Product registration should hide or disable declared-only adapters by default.

### 3.5 Surface model is extensible but weakly typed

`ProductSurface` permits `language?: string`, `runtime?: string`, `buildSystem?: string` and arbitrary keys. This is flexible, but production-grade product registration needs enums and validation.

**Fix:** add strict Zod/TS schema for `language`, `runtime`, `buildSystem`, and per-language config fields.

---

## 4. Feature Completeness Audit by Capability

### 4.1 Kernel ease-of-use

**Current:** CLI has aliases (`develop`, `ship-local`, `verify-local`), `status`, `recover`, `explain`, JSON output, context flags, and dry-run.

**Missing:**

- canonical user-facing command docs
- one-command product creation/registration
- Studio wizard parity with CLI
- stable command grammar that does not require remembering positional variants
- consistent failure/recovery taxonomy
- product registration validation with auto-fixes
- generated registry update flow

**Target:**

```bash
pnpm kernel product create
pnpm kernel product <productId> plan <phase>
pnpm kernel product <productId> explain <phase>
pnpm kernel product <productId> <phase>
pnpm kernel product <productId> recover <phase>
```

### 4.2 Polyglot adapters

**Current:** Java, TS web, TS Node, Rust, Python adapters exist and are registered as execution-ready.

**Missing production depth:**

- standard test report parsing across languages
- standard coverage parsing across languages
- language-specific artifact fingerprints
- service health/readiness conventions for Rust/Python/Node
- package manager lockfile validation
- dependency vulnerability hooks
- caching and affected-surface execution
- cross-platform path/command behavior
- generated fixtures proving adapter behavior in a representative polyglot product

**Target:** each adapter must emit:

- preflight report
- execution plan
- step results
- artifact manifest
- evidence refs
- failure classifier
- recovery actions
- performance timings

### 4.3 Product interaction broker

**Current:** `ProductInteractionBroker` exists with fail-closed preflight, handler lookup, contract version check, payload type check, timeout handling, idempotent replay cache, evidence writer, metrics, and outcome normalization.

**Missing production depth:**

- contract registry loading from ProductUnits/manifests
- external auth/authorization integration
- tenant/workspace policy integration
- circuit breaker and backoff
- provider health integration
- Data Cloud evidence writer default for platform mode
- structured audit events
- OpenTelemetry spans and metrics export
- Studio interaction graph
- async event parity with request/response
- version compatibility negotiation beyond exact handler schema equality

### 4.4 Product interaction event broker

**Current:** `ProductInteractionEventBroker` supports topic subscribers, policy checks, sequential dispatch, evidence writer, and metrics.

**Missing production depth:**

- durable event storage
- event replay
- idempotency keys
- DLQ
- subscriber backpressure
- retry policy
- event schema validation
- parallel delivery where safe
- ordering guarantees declaration

### 4.5 Plugin interaction

**Current:** `PluginInteractionBus` and `DefaultPluginInteractionBus` provide typed request/response and pub/sub with envelopes, policy evaluator, evidence writer, audit records, and metrics.

**Missing production depth:**

- lifecycle dependency resolver integrated into startup
- cycle detection and compatibility matrix
- plugin-level health impact
- durable event provider for plugin pub/sub
- timeout enforcement in default implementation
- circuit breaker and backpressure
- Studio plugin interaction graph
- plugin permission model and contract registry

### 4.6 Shared platform feature bridge

**Current:** root scripts and platform packages show many common gates and plugins, including identity, audit, consent, notification, risk, runtime truth, observability, design system, and product shell.

**Missing production depth:**

- single consumption pattern for each common capability
- product examples for each capability
- no duplicate local product implementations
- cross-language client contracts for common capabilities
- Data Cloud-backed runtime truth in platform mode
- production evidence for all common feature gates

---

## 5. Product Pilot Completeness

### 5.1 Digital Marketing

**Current:** enabled lifecycle product with backend-api and web surfaces, interaction declarations, policy packs, local environment, telemetry, and lifecycle phases. It publishes marketing lead-captured events, consumes PHR consent status, and provides notification preferences.

**Missing:**

- complete campaign activation path with broker-mediated PHR consent check
- real notification preference persistence/service
- Google Ads connector readiness and failure modes
- lead capture and conversion tracking domain invariants
- audience/segment consent boundaries
- dashboards/reports with backend-owned data contracts
- UI degraded states for interaction/provider failures
- Playwright journeys covering campaigns, leads, reports, admin, connector, and consent failures

### 5.2 PHR

**Current:** enabled lifecycle product with backend-api and web surfaces, healthcare gates, interaction declarations, package/deploy/verify contracts, and rollback readiness marked target-partial. It provides consent status and consumes DMOS notification preferences.

**Missing:**

- real consent status backed by patient consent domain
- FHIR R4 validation depth and golden resources
- patient data access controls
- audit access history UI and service path
- data sovereignty evidence generated from real persistence/routing
- healthcare gate outputs tied to runtime evidence
- rollback enablement after stable deploy history, artifact selection policy, healthcare post-rollback gates, and approval contract

---

## 6. Data Cloud and YAPPC Boundaries

### 6.1 Data Cloud

**Correct direction:** Data Cloud has Kernel bridge and interaction evidence provider. This keeps Kernel dependent on contracts/adapters, not plane internals.

**Gaps:**

- platform mode is not fully wired from CLI
- evidence provider needs schema lifecycle, readback, retention, and health checks
- interaction evidence should be queryable and visible in Studio
- Data Cloud runtime truth should not be bypassed by local files in production

### 6.2 YAPPC

**Correct direction:** YAPPC should own artifact intelligence and ProductUnitIntent creation, while Kernel consumes contracts/evidence only.

**Gaps:**

- keep Kernel free of YAPPC compiler/decompiler internals
- ProductUnitIntent handoff must be versioned, validated, and evidence-backed
- generated change-set/risk/dependency evidence should feed Kernel lifecycle, not bypass it

---

## 7. Deep Gap Matrix

| ID | Area | Severity | Current state | Desired state | Concrete task | Owner modules | Validation |
|---|---|---:|---|---|---|---|---|
| BUG-001 | Script stability | P0 | Gradle scripts use `gradlew` | Stable wrapper invocation | Replace with `./gradlew` or wrapper abstraction | `package.json`, generator scripts | new `check:gradle-wrapper-script-usage` |
| KUX-001 | CLI simplicity | P0 | Many aliases/scripts | One canonical mental model | Normalize `product <id> <phase>` grammar and docs | `scripts/kernel-product.mjs` | CLI tests |
| KUX-002 | Recovery UX | P0 | `recover` exists | actionable recovery by failure code | Map every failure code to recovery action | kernel-lifecycle, CLI, Studio | `check:lifecycle-explain-recover` |
| KUX-003 | Studio lifecycle | P0 | partial | guided one-click lifecycle | Lifecycle launcher, plan/explain/result/recover views | `ghatana-studio` | Studio E2E |
| POLY-001 | Surface typing | P0 | strings + arbitrary keys | strict language/runtime/buildSystem model | Add schemas and validation | kernel contracts/lifecycle | contract tests |
| POLY-002 | Rust depth | P1 | adapter exists | production-ready Rust support | cargo metadata, report parsing, SLO, cross-platform binary handling | kernel-toolchains | rust conformance |
| POLY-003 | Python depth | P1 | adapter exists | production-ready Python support | venv/tool discovery, pytest report, type/lint policy, FastAPI worker conventions | kernel-toolchains | python conformance |
| POLY-004 | Node depth | P1 | Node adapter exists | production Node service support | health/readiness/dev process conventions | kernel-toolchains | Node adapter tests |
| INT-001 | Product interaction broker | P0 | broker exists | production broker | registry, auth, policy, provider health, evidence, observability | kernel-core | product broker tests |
| INT-002 | Event broker | P0 | in-process sequential broker | durable event path | persistence, retry, DLQ, replay, idempotency | kernel-core/Data Cloud | event broker tests |
| INT-003 | Interaction evidence | P0 | Data Cloud provider exists | durable queryable evidence | schema lifecycle, retention, readback, Studio viewer | Data Cloud bridge, Studio | interaction runtime truth |
| INT-004 | DMOS real consent | P0 | manifest consumes PHR | campaign activation uses broker | Wire activation workflow through broker | DMOS app/api | campaign E2E |
| INT-005 | PHR real consent | P0 | consent handler exists | domain-backed consent | Replace static logic with consent service | PHR backend | consent tests |
| PLUG-001 | Plugin broker | P0 | bus exists | governed production broker | timeout/circuit breaker/durable pubsub/dependency graph | kernel-plugin | plugin broker tests |
| PLUG-002 | Plugin cycles | P0 | not fully proven | no hidden cyclic deps | graph cycle/startup validation | kernel-plugin | resolver tests |
| DC-001 | Platform mode | P0 | CLI rejects platform mode | Data Cloud-backed platform mode | register provider bridge | kernel-providers/Data Cloud | platform mode tests |
| STU-001 | Interaction Studio | P1 | absent/partial | graph/timeline/evidence viewer | Product interaction views | Studio | UI tests |
| STU-002 | Product registration | P0 | partial | wizard with surface detection | registration wizard + generated registry workflow | Studio/scripts | E2E |
| PERF-001 | Affected surface execution | P1 | checks exist | skip unchanged safely | surface dependency/hash graph | kernel-lifecycle | performance tests |
| PERF-002 | Interaction SLO | P1 | metrics counters | enforce SLO budgets | latency budget config + regression gate | kernel-core/scripts | interaction performance |
| GOV-001 | Evidence freshness | P0 | evidence files exist | evidence commit/source validation | add freshness and source-ref checks | scripts/kernel evidence | release evidence check |
| GOV-002 | Current-state truth | P0 | many docs/gates | no target-state claims | doc claim evidence gate by product | scripts/docs | doc truth |
| SEC-001 | Tenant isolation | P0 | fail-closed checks exist | full wrong tenant/workspace tests | expand broker and product tests | kernel/integration | cross-product tests |
| OBS-001 | OTel | P1 | metrics counters | logs/metrics/traces | add structured telemetry export | kernel/platform | o11y conformance |
| REL-001 | PHR rollback | P0 | target-partial | enabled safe rollback | artifact history, approval, healthcare post-rollback gates | PHR/Kernel | rollback tests |

---

## 8. Phased Roadmap

### Phase 0 — Stabilize execution foundation

**Build/fix:**

- Fix `gradlew` script invocations.
- Verify command spelling and wrappers statically.
- Confirm phase8 no longer depends on environment-specific accidental PATH.
- Generate baseline current-state matrix for adapters, brokers, providers, and products.

**Validation:** static checks only first; defer full phase8 run until fixes are in.

### Phase 1 — Make Kernel easy by default

**Build/fix:**

- simplify CLI grammar
- add `explain` and `recover` docs
- expose failure recovery table
- add product registration wizard design and initial implementation
- hide adapter/provider internals from default output

### Phase 2 — Harden polyglot adapters

**Build/fix:**

- enrich Java/TS/Rust/Python adapters with report parsing and artifact evidence
- add language/runtime/buildSystem schema
- add fixture product covering Java + TS + Rust + Python
- add affected-surface execution graph

### Phase 3 — Production broker hardening

**Build/fix:**

- finish ProductInteractionBroker registry/policy/evidence/observability path
- finish ProductInteractionEventBroker durable event path
- replace direct handler tests with broker-mediated tests
- add Data Cloud evidence readback and Studio viewer

### Phase 4 — Plugin interaction hardening

**Build/fix:**

- add plugin dependency graph and cycle checks
- add timeout/circuit breaker/backpressure
- make plugin pub/sub durable where required
- add plugin interaction evidence and Studio view

### Phase 5 — Product pilot completion

**Build/fix:**

- DMOS real campaign/customer/lead/analytics/connector workflows
- DMOS consent-driven campaign activation
- PHR real consent/FHIR/audit/data-sovereignty workflows
- PHR notification-preference consumption
- full UI journeys and degraded states

### Phase 6 — Platform mode and production operations

**Build/fix:**

- Data Cloud-backed provider mode
- evidence freshness/source-ref checks
- release scorecards
- SLO and cost budgets
- observability dashboards
- production/staging deployment target contracts

---

## 9. Testing Strategy

### Static and contract tests first

- product registry
- domain registry
- architecture boundaries
- current-state claims
- product interaction contracts
- plugin interaction contracts
- adapter registry conformance
- gradle wrapper script usage

### Focused behavior tests

- Rust adapter fixture
- Python adapter fixture
- Node API adapter fixture
- broker request/response success, denied, blocked, timeout, evidence failure
- event broker publish, no subscriber, denied, delivery failure
- plugin interaction typed request and pub/sub
- cross-product PHR ↔ DMOS broker-mediated tests

### Deferred long-running verification

Per request, defer:

- full `pnpm check:phase8`
- full `pnpm check:world-class-platform-readiness`
- full product E2E suites
- full Docker/Buildx packaging
- full Compose deploy/verify
- full performance/load suites

Run these only after static bugs and missing capabilities above are addressed.

---

## 10. Validation Strategy

### Immediate static/focused commands

```bash
pnpm check:product-registry
pnpm check:domain-registry
pnpm check:architecture-boundaries
pnpm check:toolchain-adapter-contracts
pnpm check:product-interaction-contracts
pnpm check:cross-product-interaction-boundaries
pnpm check:interaction-runtime-truth
pnpm check:java-adapter-conformance
pnpm check:typescript-web-adapter-conformance
pnpm check:rust-adapter-conformance
pnpm check:python-adapter-conformance
pnpm check:kernel-product-cli
pnpm check:lifecycle-explain-recover
pnpm check:lifecycle-run-history
```

### Add missing focused commands

```bash
pnpm check:gradle-wrapper-script-usage
pnpm check:product-interaction-evidence-readback
pnpm check:plugin-interaction-cycles
pnpm check:studio-lifecycle-ux
pnpm check:polyglot-fixture-behavior
```

### Later long-running gates

```bash
pnpm check:phase8
pnpm check:world-class-platform-readiness
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
pnpm package:phr
pnpm deploy:local:phr
pnpm verify:local:phr
```

---

## 11. Expert Implementation Tickets

### Ticket 1 — Fix Gradle wrapper script invocation

**Where:** `package.json`, generator scripts.  
**Task:** replace `gradlew` with `./gradlew` in Unix shell npm scripts or introduce a cross-platform Gradle wrapper command abstraction.  
**Done:** static gate prevents regression.

### Ticket 2 — Strict product surface schema

**Where:** `platform/typescript/kernel-product-contracts`, `kernel-lifecycle`.  
**Task:** add enum-backed schemas for `language`, `runtime`, `buildSystem`, adapter compatibility, and required path fields.  
**Done:** invalid language/runtime combos fail with recovery guidance.

### Ticket 3 — ProductInteractionBroker production registry

**Where:** `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/interaction`.  
**Task:** load handlers/contracts from ProductUnit manifest registry and enforce provider/consumer compatibility.  
**Done:** consumers cannot call undeclared provider contracts.

### Ticket 4 — Product interaction evidence readback

**Where:** Data Cloud bridge and scripts.  
**Task:** persist and read back interaction evidence records with tenant/workspace/product/contract indexes.  
**Done:** runtime truth validates evidence content, not only evidence ref existence.

### Ticket 5 — Replace static PHR consent handler behavior

**Where:** `products/phr/src/main/java/.../ConsentStatusInteractionHandler.java`.  
**Task:** call real consent service, verify patient/subject scope, write audit evidence.  
**Done:** wrong patient/tenant/workspace/purpose fails closed; valid consent succeeds.

### Ticket 6 — Replace static DMOS notification preference handler behavior

**Where:** `products/digital-marketing/dm-kernel-bridge`.  
**Task:** call real notification preference service and persistence layer.  
**Done:** preference result is tenant-scoped and evidence-backed.

### Ticket 7 — Rust adapter production hardening

**Where:** `CargoRustAdapter.ts`.  
**Task:** parse cargo metadata/test output, support workspaces/target triples, handle binary naming, emit manifest details.  
**Done:** fixture proves build/test/package across service and library shapes.

### Ticket 8 — Python adapter production hardening

**Where:** `PythonPyprojectAdapter.ts`.  
**Task:** define Python env strategy, parse pytest reports, support type/lint configured tools, package service/worker/library.  
**Done:** fixture proves validate/test/build/package.

### Ticket 9 — Studio lifecycle and interaction UX

**Where:** `platform/typescript/ghatana-studio`.  
**Task:** add product registration wizard, lifecycle launcher, interaction graph, evidence viewer, recovery summaries.  
**Done:** product team can run lifecycle without understanding adapters/providers.

### Ticket 10 — Platform provider mode

**Where:** `kernel-providers`, Data Cloud bridge, CLI.  
**Task:** register platform-mode providers and make `--mode platform` executable in production profiles.  
**Done:** platform mode no longer throws not-registered error.

---

## 12. Production Readiness Definition of Done

Ghatana Kernel becomes production-ready when:

- product teams can register Java, TypeScript, Rust, and Python surfaces without lifecycle internals;
- every phase supports plan, explain, execute, summarize, and recover;
- Studio and CLI both provide simple, consistent, low-cognitive-load workflows;
- Java/TS/Rust/Python adapters produce trustworthy artifacts, evidence, failures, and recovery actions;
- product interaction broker is the only supported product-to-product request path;
- product event broker is durable, replayable, and evidence-backed;
- plugin interactions are typed, governed, observable, and cycle-safe;
- Data Cloud-backed platform mode is available for production runtime truth and evidence;
- PHR and Digital Marketing use real business services behind their interaction handlers;
- no product imports another product’s internals;
- no product owns platform lifecycle code;
- all critical evidence is fresh, source-ref-bound, and behavior-backed;
- full phase8/world-class gates pass after static blockers are fixed.
