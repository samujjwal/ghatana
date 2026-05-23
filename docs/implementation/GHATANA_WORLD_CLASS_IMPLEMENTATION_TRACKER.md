# Production-Ready Product Development Kernel — Deep/Wide Expert Audit and Implementation Plan

**Repository:** `samujjwal/ghatana`  
**Target commit:** `f302e89c8e7116e8821a7957b4a06a5d7dff81e7`  
**Commit message:** `dd ff gg 1`  
**Audit mode:** source-grounded deep/wide expert review; long-running validations intentionally deferred  
**Primary goal:** progress toward a production-ready, extremely easy-to-use, highly effective, performant, polyglot Kernel platform that can develop/build/deploy products while products own only business logic.

---

## 1. Scope and Method

This audit reviewed the current code snapshot and committed configuration/evidence at the target commit. I focused on bugs, incomplete capabilities, missing feature paths, stability risks, production-readiness risks, and feature-completeness gaps. I did **not** claim that full CI, long-running Gradle builds, Docker/Buildx flows, Playwright E2E, load tests, or release drills were executed in this session.

The review covered:

- Kernel lifecycle UX and CLI.
- Product registry and lifecycle profiles.
- Polyglot adapters: Java, TypeScript/Node/React, Rust, Python, Docker, Compose.
- Product interaction brokers and product-to-product contracts.
- Plugin interaction model.
- Product pilots: Digital Marketing and PHR.
- Product readiness gates: SLO budgets, cost budgets, domain invariants, OpenAPI release quality/breaking changes.
- Data Cloud provider/evidence bridge.
- Feature completeness and production-hardening gaps.

---

## 2. Executive Summary

The target commit shows real progress toward a production-grade Kernel platform. The platform is no longer only a lifecycle-manifest/checklist exercise. It now has:

- Enabled lifecycle pilots for PHR and Digital Marketing.
- Polyglot adapter registration for Java, TypeScript web, TypeScript Node API, Rust/Cargo, Python/pyproject, Docker Buildx, and Compose.
- Product interaction contracts with schema hashes in Digital Marketing and PHR manifests.
- Product interaction request/event brokers with policy, evidence, metrics, timeout, replay/cache, and handler registry support.
- Additional production-readiness checks for product SLO budgets, cost budgets, domain invariants, OpenAPI breaking changes, interaction runtime truth, product interaction broker, interaction performance, lifecycle explain/recover, and run history.

However, the platform is **not yet production-ready as a product development Kernel**. The biggest risks are:

1. **Readiness gates are becoming broad but still over-indexed on static/config evidence.** SLO and cost budgets are declared and validated structurally, but not enforced or proven against runtime telemetry.
2. **Domain invariant checks are too shallow.** The current check largely infers invariant coverage from file names and test existence, not from domain invariant semantics.
3. **Product features are still incomplete.** PHR and Digital Marketing have good lifecycle and interaction scaffolding, but their full business workflows are not yet feature-complete.
4. **Interaction broker is improving but still has correctness risks.** The broker now has production-mode evidence enforcement and trusted policy context, but payload hashing via `payload.toString()` is not canonical enough for idempotency, and default builder ergonomics can surprise developers by rejecting no-op evidence in development mode.
5. **OpenAPI breaking-change detection is too narrow.** It detects removed method/path pairs, but not schema, parameter, auth, response, enum, nullability, or semantic breaking changes.
6. **Polyglot adapters exist but need deeper production proof.** Rust and Python adapters are present, but they still need realistic product fixtures, output manifest hardening, dependency/cache controls, security scanning, and runtime packaging proof.
7. **Easy-to-use Kernel UX is still not finished.** CLI aliases and explain/recover exist, but Studio-first guided workflows, “what failed / what to do next,” and product-team abstraction still need hardening.

**Bottom line:** The repo is progressing in the right direction, but the next iteration should prioritize converting structural gates into real runtime-backed capability and completing end-to-end product features for PHR and Digital Marketing.

---

## 3. Current-State Classification

| Area | Current state | Classification | Production-readiness concern |
|---|---|---:|---|
| Kernel lifecycle scripts | Broad root scripts and `kernel-product.mjs` exist | Existing but partial | UX is improving, but still script-heavy and not fully Studio-guided |
| PHR lifecycle | Enabled with backend/web surfaces and healthcare gates | Existing but partial | Rollback still target-partial; healthcare feature completeness remains incomplete |
| Digital Marketing lifecycle | Enabled with backend/web surfaces | Existing but partial | Business workflows and connector readiness still need deeper implementation |
| Java adapter | Registered and current pilot backend path uses Java | Existing and executable | Needs richer test/artifact/result parsing and production output proof |
| TypeScript web adapter | Registered and used by PHR/DMOS web | Existing and executable | Needs route/a11y/i18n/runtime contract proof across products |
| TypeScript Node API adapter | Registered | Existing but partial | Needs real product fixture and service runtime/deploy proof |
| Rust/Cargo adapter | Registered and implemented | Existing but partial | Needs realistic fixture, security scan, packaging, and runtime proof |
| Python/pyproject adapter | Registered and implemented | Existing but partial | Needs pyproject fixture, venv/dependency strategy, security scan, and service proof |
| Product interaction contracts | PHR/DMOS manifests declare request/response and event contracts | Existing but partial | Needs broker-mediated product flows in actual application workflows |
| Product interaction broker | Exists with evidence, policy, timeout, metrics, idempotency cache | Existing but partial | Payload hash and policy/evidence integration need hardening |
| Product interaction event broker | Exists | Existing but partial | Durable event delivery, replay, DLQ, subscriber isolation need proof |
| Data Cloud interaction evidence provider | Exists | Existing but partial | Needs full evidence record conformance and lifecycle integration |
| SLO budgets | Config exists and static check exists | Declared/static only | Does not prove or enforce runtime SLOs |
| Cost budgets | Config exists and static check exists | Declared/static only | Does not enforce usage or cost at runtime |
| Domain invariant check | Exists | Partial/weak | Checks test existence/names, not invariant semantics |
| OpenAPI breaking changes | Exists | Partial | Only path/method removals; misses most API-breaking changes |
| Studio UX | Multiple Studio/artifact workflow gates exist | Partial | Needs seamless product lifecycle launch/explain/recover UI |
| Feature completeness | PHR/DMOS scaffolding and routes exist | Partial | Need end-to-end business workflow completeness |

---

## 4. High-Priority Findings

### F-001 — Product SLO budgets are declared, not proven or enforced

**Severity:** P0  
**Area:** production readiness, performance, reliability  
**Current state:** `config/product-slo-budgets.json` defines latency, throughput, memory, queue, and background job runtime targets per active product. The check validates structural presence and positive values.

**Risk:** This creates a good readiness contract but does not prove that any product meets those SLOs. A product can pass while being slow, memory-heavy, or unstable under load.

**Required implementation:**

- Add runtime telemetry mapping from each SLO budget to actual metrics.
- Add local smoke performance tests for representative workflows.
- Add CI-safe lightweight latency/throughput assertions for core workflows.
- Add production/staging dashboard evidence for p50/p95/p99.
- Add budget drift and regression reporting.
- Extend evidence from “budget exists” to “budget measured and pass/fail computed.”

**Likely files/modules:**

- `config/product-slo-budgets.json`
- `scripts/check-product-slo-budgets.mjs`
- `platform/java/observability`
- `platform/typescript/kernel-lifecycle`
- `products/digital-marketing/**`
- `products/phr/**`
- `.kernel/evidence/product-slo-budgets.json`

**Tests:**

- Unit tests for parsing and validating budgets.
- Integration test mapping budgets to emitted metrics.
- Lightweight benchmark test for one workflow per active product.

**Done criteria:** SLO gate fails if no measured evidence exists for required workflows or if measured p95/p99 exceeds budget.

---

### F-002 — Product cost budgets are not runtime-enforced

**Severity:** P0  
**Area:** cost governance, AI governance, platform economics  
**Current state:** `config/product-cost-budgets.json` declares AI, query, export, stream, storage growth, and background compute budgets. The check validates positive values.

**Risk:** The system can pass with declared budgets while no actual product usage is measured or enforced. This is risky for AI-native, query-heavy, or stream-heavy products.

**Required implementation:**

- Add product-level cost meter contracts.
- Track AI token use, model cost, export cost, query cost, stream cost, storage growth, and background compute.
- Add budget status provider: `within-budget`, `warning`, `blocked`, `unknown`.
- Add per-product budget dashboards in Studio.
- Add enforcement policies for optional/expensive operations.
- Add fail-closed behavior for production if budget evidence is unavailable for governed operations.

**Done criteria:** Costs are measured and visible per product/workflow, not only declared.

---

### F-003 — Domain invariant check is too shallow for production correctness

**Severity:** P0  
**Area:** domain correctness, feature completeness  
**Current state:** `check-product-domain-invariants.mjs` scans active business products and counts Java/TS tests whose file names contain `invariant`, `domain`, `workflow`, or `lifecycle`.

**Risk:** This can pass based on file names without proving meaningful domain invariants. It does not inspect assertions, scenarios, fixtures, or business-critical workflows.

**Required implementation:**

- Define a `product-domain-invariants.yaml` or JSON contract per product.
- Require each invariant to map to executable tests by stable test ID.
- Require invariant categories:
  - identity/authorization
  - tenant/workspace isolation
  - lifecycle state transitions
  - data integrity
  - audit/evidence
  - privacy/consent
  - product-specific business correctness
- Validate that tests execute and produce result artifacts.
- For PHR, invariants must include consent, FHIR validation, patient data access, audit, and data sovereignty.
- For Digital Marketing, invariants must include campaign lifecycle, consent gating, lead capture, connector failure behavior, notification retry/DLQ, and tenant scoping.

**Done criteria:** The check validates declared invariant IDs against actual executed test results and evidence, not just filenames.

---

### F-004 — OpenAPI breaking-change detection is incomplete

**Severity:** P0  
**Area:** API contract stability, release safety  
**Current state:** `check-openapi-breaking-changes.mjs` compares baseline vs current specs and flags removed method/path pairs.

**Risk:** Many breaking changes can pass undetected:

- required request field added
- response field removed
- response type changed
- enum value removed
- status code removed
- auth/security requirement changed
- path parameter renamed
- query parameter made required
- request/response content type removed
- nullability changed
- schema `$ref` target changed

**Required implementation:**

- Add schema-aware OpenAPI diffing.
- Track request/response schema changes.
- Enforce semver/release waiver workflow.
- Require migration notes for allowed breaking changes.
- Emit evidence with per-operation diff severity.

**Done criteria:** Breaking-change gate catches operation, parameter, request, response, schema, enum, status code, and security changes.

---

### F-005 — ProductInteractionBroker has idempotency/canonicalization risks

**Severity:** P0  
**Area:** interaction correctness, reliability  
**Current state:** The broker now includes contract metadata and payload hash in replay key. Payload hash is currently computed from `payload.toString()`.

**Risk:** Java `toString()` is not canonical serialization. Two semantically equal payloads can hash differently, and different payloads can hash the same if `toString()` is not implemented well. This weakens idempotency conflict detection.

**Required implementation:**

- Require handlers/contracts to provide canonical payload serialization or schema-backed canonical JSON.
- Use deterministic field order and UTF-8 canonical JSON hashing.
- Include contract schema hash in idempotency key.
- Add tests for record payloads, nested payloads, map ordering, nulls, and equivalent field ordering.

**Done criteria:** Idempotency key is stable across equivalent payloads and sensitive to real semantic changes.

---

### F-006 — Broker default mode can harm developer ergonomics

**Severity:** P1  
**Area:** developer experience, testability  
**Current state:** `BrokerMode` defines `PRODUCTION`, `DEVELOPMENT`, and `TEST`. No-op evidence is rejected in production and development, and allowed only in test mode.

**Risk:** That is safe, but the builder default is development with no-op evidence unless overridden. A plain `ProductInteractionBroker.builder().build()` can throw unexpectedly. This protects production, but it is not easy to use.

**Required implementation:**

- Make the builder default explicit: either default to `TEST`, or require `brokerMode(...)` before build.
- Add `developmentFactory(fileEvidenceWriter)` and `testFactory()` named constructors.
- Improve error message with a concrete recovery command/example.
- Ensure docs and tests use the right factory.

**Done criteria:** Product teams can instantiate a safe broker without reading internals, and unsafe default construction is impossible or self-explanatory.

---

### F-007 — Polyglot adapters exist but need production-grade fixture proof

**Severity:** P0  
**Area:** polyglot platform completeness  
**Current state:** The default registry registers Java, TypeScript web, TypeScript Node API, Rust/Cargo, Python/pyproject, Docker Buildx, and Compose. Rust/Python adapters exist.

**Risk:** Adapter existence is not enough. Production readiness requires at least one canonical fixture/product surface for each language and phase with real outputs, evidence, failure classification, and package/deploy behavior.

**Required implementation:**

- Create `examples/polyglot-product` or `platform/test-fixtures/polyglot-product` with:
  - Java service
  - TypeScript web
  - TypeScript Node service
  - Rust service/binary
  - Python service/worker
- Run validate/test/build/package/verify plan for each surface.
- Validate missing toolchain behavior is `environment-blocked`.
- Add artifact manifest assertions per language.
- Add Docker packaging where applicable.

**Done criteria:** `pnpm check:polyglot-product-fixture` proves all supported language surfaces through Kernel lifecycle without product-local runners.

---

### F-008 — Product feature completeness remains partial for PHR and Digital Marketing

**Severity:** P0  
**Area:** product readiness  
**Current state:** PHR and Digital Marketing are enabled pilots with routes, manifests, lifecycle gates, and interactions.

**Risk:** Enabled lifecycle does not mean product feature completeness. The pilots need full business workflow implementation, data correctness, UI coverage, API correctness, and degraded/error handling.

**Required implementation:**

Digital Marketing must complete:

- customer/account management
- campaign lifecycle
- campaign activation with consent gates
- lead capture and conversion tracking
- audience/segment management
- connector configuration and Google Ads readiness
- notification retry/DLQ
- reporting/dashboard workflows
- operator/admin flows

PHR must complete:

- patient profile
- record summary and timeline
- encounters, medications, allergies, conditions, labs, immunizations, documents
- consent management and sharing authorization
- access audit history
- FHIR R4 handling
- data sovereignty evidence
- emergency/break-glass workflow

**Done criteria:** Each workflow has backend API, persistence, UI route, access rules, tests, audit/evidence, loading/error/degraded states, and lifecycle validation.

---

## 5. Capability Completeness Analysis

### 5.1 Kernel lifecycle UX

**Good progress:**

- CLI supports plan, explain, recover/status patterns.
- Lifecycle phases are broad.
- Root scripts include lifecycle explain/recover and run-history checks.

**Remaining gaps:**

- Studio must become the primary “no-details-required” lifecycle launcher.
- CLI output must consistently use product-team language, not adapter/provider internals.
- Recover guidance must map to concrete commands and files.
- Product registration wizard must detect surfaces and produce manifest diffs.
- Dry-run/explain must show what Kernel will do, what product owns, and what platform owns.

### 5.2 Product registration and shape model

**Good progress:**

- Product registry and generated wiring exist.
- Multiple product kinds and surfaces are represented.

**Remaining gaps:**

- Language/runtime fields must be explicit and consistent across all products.
- Surface model must distinguish `react-web`, `node-service`, `java-service`, `rust-service`, `python-worker`, `static-web`, `sdk`, etc.
- Product shape matrix must be enforced against real fixtures.

### 5.3 Platform feature bridge

**Good progress:**

- Plugins and providers exist for core concerns.
- Digital Marketing bridge composes platform plugins and bridge ports.
- PHR has healthcare gate packs and interaction contracts.

**Remaining gaps:**

- Common features need a single product consumption pattern.
- Avoid product-specific adapters becoming common platform code accidentally.
- Consent/audit/notification/risk must be platform capabilities with product-specific policy only.
- Studio should show feature bridge status per product.

### 5.4 Product-to-product interaction

**Good progress:**

- Manifest interactions include schema refs and SHA-256 hashes.
- Broker exists with evidence, policy resolver, cache TTL, handler registry, metrics, and fail-closed behavior.
- Data Cloud interaction evidence provider exists.

**Remaining gaps:**

- Wire the broker into real product workflows, not only tests.
- Add broker-mediated flows for DMOS campaign activation and PHR care-plan notification.
- Persist interaction evidence through Data Cloud in platform mode and file-backed provider in bootstrap mode.
- Add UI visibility for cross-product dependency health.
- Add async event flow proof for lead-captured and consent-revoked.

### 5.5 Plugin-to-plugin interaction

**Good progress:**

- Plugin API ownership is established.
- Plugin interaction bus exists.
- Root scripts now include plugin interaction broker checks.

**Remaining gaps:**

- Plugin interaction should have the same rigor as product interaction:
  - contract ID
  - version
  - caller identity
  - tenant/workspace/correlation
  - policy decision
  - timeout/retry/circuit breaker
  - evidence
  - metrics/traces
- Add plugin interaction graph and cycle detection.
- Add durable pub/sub where needed.

---

## 6. Product-Specific Expert Findings

### 6.1 Digital Marketing

**Current strengths:**

- Enabled lifecycle product.
- Backend and web surfaces defined.
- Product interactions declared with schema refs and hashes.
- Required platform plugins declared.
- Privacy/security policy packs declared.

**Top risks:**

1. Campaign activation must not proceed on stale/missing consent status.
2. Google Ads connector readiness must not claim real activation if credentials/API calls are not real.
3. Notification preference handler must be backed by real user/customer preference data.
4. Lead-captured event flow needs durable eventing, idempotency, and retry/DLQ proof.
5. UI must expose degraded/blocked states in simple language.

**Implementation priorities:**

- Wire campaign activation to `ProductInteractionBroker` for PHR consent status.
- Add `CampaignActivationDomainInvariantTest` with consent allowed/denied/degraded paths.
- Add connector readiness state machine: `not-configured`, `configured`, `validated`, `degraded`, `blocked`, `active`.
- Add reporting dashboard backed by real API contract, not static cards.
- Add end-to-end route tests for campaign plan → consent check → activation → audit evidence.

### 6.2 PHR

**Current strengths:**

- Enabled lifecycle product.
- Healthcare gate packs declared.
- Consent-status interaction provider declared.
- Record, consent, labs, medications, emergency, settings route contracts exist.

**Top risks:**

1. Consent-status handler must be backed by real consent domain data, not simplified purpose checks.
2. FHIR validation must be schema/profile-backed, not only structural/minimal validation.
3. Break-glass emergency workflow must be fully audited and approval-aware.
4. Tenant/data sovereignty evidence must be generated from real storage/provider decisions.
5. PHR rollback remains target-partial.

**Implementation priorities:**

- Implement real consent grant model with expiry, purpose, subject, grantee, revocation, and audit.
- Wire `phr.consent-status.v1` handler to real consent service.
- Add FHIR R4 validation evidence with resource type/profile/status details.
- Add patient access matrix tests for patient/caregiver/clinician/admin.
- Add emergency access workflow with audit, reason, time-boxing, notification, and post-review.

---

## 7. Production-Readiness Gap Matrix

| ID | Area | Current state | Gap | Severity | Owner | Done criteria |
|---|---|---|---|---:|---|---|
| PR-001 | SLO budgets | Static config/check | No runtime measured proof | P0 | Kernel + Observability | SLO check consumes measured metrics |
| PR-002 | Cost budgets | Static config/check | No usage/cost enforcement | P0 | Kernel + Cost provider | Cost budgets tied to metering and policy |
| PR-003 | Domain invariants | Filename/test count heuristic | No invariant-to-test proof | P0 | Kernel + Products | Each invariant maps to executable evidence |
| PR-004 | OpenAPI breaking changes | Path/method removals only | Schema/param/security breaks missed | P0 | API platform | Schema-aware diff gate |
| PR-005 | Product broker idempotency | `payload.toString()` hash | Non-canonical | P0 | Kernel core | Canonical JSON/schema-based payload hash |
| PR-006 | Broker evidence UX | Production rejects no-op | Builder default can surprise | P1 | Kernel core | Explicit safe factories and error guidance |
| PR-007 | Product interaction runtime | Broker exists | Not wired into all workflows | P0 | Products + Kernel | Real workflow uses broker |
| PR-008 | Async product events | Event broker exists | Durable/replay/DLQ not proven | P1 | Kernel + Data Cloud | Event flow evidence and replay tests |
| PR-009 | Polyglot fixtures | Adapters exist | Need real fixture/product proof | P0 | Kernel toolchains | Java/TS/Rust/Python fixture lifecycle passes |
| PR-010 | Rust adapter | Implemented | Needs security/package/runtime proof | P1 | Kernel toolchains | Cargo fixture build/test/package evidence |
| PR-011 | Python adapter | Implemented | Needs env/dependency/security proof | P1 | Kernel toolchains | pyproject fixture build/test/package evidence |
| PR-012 | Studio UX | Partial | Not yet no-cognitive-load lifecycle UX | P0 | Studio | One-click plan/execute/recover/run-history |
| PR-013 | PHR rollback | Target-partial | Not enabled | P0 | PHR + Kernel release | Stable deploy history + post-rollback gates |
| PR-014 | DMOS connector | Partial | Real connector readiness unclear | P0 | DMOS | No fake connector success; real degraded states |
| PR-015 | Evidence freshness | Some evidence generated-on-demand/stale flags | Need freshness policy by evidence type | P1 | Kernel evidence | Gates fail stale critical evidence |
| PR-016 | Product feature completeness | Routes/manifests present | Workflows incomplete | P0 | Product teams | Each workflow has API/UI/test/evidence |
| PR-017 | Cost/SLO Studio UX | none/partial | Product teams cannot see budgets easily | P2 | Studio | Budget dashboard per product/workflow |
| PR-018 | Product shape expansion | Many products registered | Not all lifecycle-enabled | P1 | Kernel | Clear planned vs executable product statuses |
| PR-019 | Test authenticity | Checks exist | Need semantic test quality proof | P1 | Testing platform | Tests prove assertions, not names |
| PR-020 | Release evidence | More evidence uploaded | Must avoid stale target-state claims | P0 | Governance | Evidence claim checker verifies freshness/source |

---

## 8. Development Roadmap

### Phase 0 — Stabilize truth and evidence semantics

**Build/fix:**

- Define evidence freshness policy by evidence type.
- Mark generated-on-demand evidence separately from executed evidence.
- Make current-state claim checker verify that “passed” evidence is fresh and source-backed.
- Add fail conditions for stale runtime-production evidence for critical gates.

**Validation:**

- `pnpm check:doc-claims-evidence`
- `pnpm check:current-state-claims`
- `pnpm check:world-class-platform-readiness`

### Phase 1 — Turn static SLO/cost budgets into measured controls

**Build/fix:**

- Add product metric mapping file.
- Add measured SLO evidence provider.
- Add cost metering provider.
- Add lightweight workflow performance tests for PHR and DMOS.
- Add budget status to Studio.

**Validation:**

- `pnpm check:product-slo-budgets`
- `pnpm check:product-cost-budgets`
- new `pnpm check:product-slo-measurements`
- new `pnpm check:product-cost-metering`

### Phase 2 — Harden domain invariant proof

**Build/fix:**

- Add per-product invariant declaration files.
- Require invariant IDs to map to test IDs.
- Add PHR and DMOS domain invariant suites.
- Include failed/edge/degraded paths.

**Validation:**

- `pnpm check:product-domain-invariants`
- product-specific invariant tests

### Phase 3 — Finish product interaction runtime integration

**Build/fix:**

- Canonicalize broker payload hashing.
- Add broker factories for production/development/test.
- Wire DMOS campaign activation to PHR consent through broker.
- Wire PHR notification preference check to DMOS through broker.
- Add interaction evidence persistence through Data Cloud provider.
- Add async event flows.

**Validation:**

- `pnpm check:product-interaction-broker`
- `pnpm check:interaction-runtime-truth`
- `pnpm check:cross-product-interaction-flows`
- `pnpm check:interaction-performance`

### Phase 4 — Prove polyglot platform support with real fixtures

**Build/fix:**

- Add canonical polyglot fixture product.
- Add Java, React, Node, Rust, Python surfaces.
- Validate/test/build/package/verify each surface.
- Add output manifest and error classification assertions.

**Validation:**

- `pnpm check:java-adapter-conformance`
- `pnpm check:typescript-web-adapter-conformance`
- `pnpm check:rust-adapter-conformance`
- `pnpm check:python-adapter-conformance`
- `pnpm check:polyglot-product-fixture`

### Phase 5 — Complete PHR pilot workflows

**Build/fix:**

- Real consent domain model.
- FHIR validation and evidence.
- Patient/caregiver/clinician/admin access matrix.
- Emergency break-glass workflow.
- Record summary/timeline/labs/medications/documents backend + UI.
- Healthcare rollback readiness.

**Validation:**

- PHR backend/domain tests.
- PHR UI tests.
- PHR Playwright healthcare journeys.
- `pnpm check:phr-lifecycle-pilot`

### Phase 6 — Complete Digital Marketing pilot workflows

**Build/fix:**

- Campaign lifecycle domain workflow.
- Consent-gated activation.
- Google Ads connector readiness and no-fake-success model.
- Lead capture/conversion tracking.
- Notification retry/DLQ.
- Reporting/dashboard and admin/operator UX.

**Validation:**

- DMOS backend/domain tests.
- DMOS UI tests.
- DMOS Playwright journeys.
- `pnpm check:digital-marketing-lifecycle-pilot`

### Phase 7 — Production hardening and release confidence

**Build/fix:**

- OpenAPI schema-aware breaking change detection.
- Load/stress smoke tests for core workflows.
- Data Cloud durability and failure injection coverage.
- Production-mode provider wiring.
- Studio production profile hardening.
- Runbook and rollback drills.

**Validation:**

- `pnpm check:phase8`
- `pnpm check:world-class-platform-readiness`
- strict release workflow gates

---

## 9. Immediate Ticket List

### Kernel/Core

1. Implement canonical JSON hashing for `ProductInteractionBroker` idempotency keys.
2. Add `ProductInteractionBroker.testFactory()`, `developmentFactory(...)`, and `productionFactory(...)` examples.
3. Add evidence writer implementations:
   - file-backed bootstrap writer
   - Data Cloud-backed platform writer adapter
4. Add event broker durable provider and DLQ semantics.
5. Add measured SLO evidence provider.
6. Add cost metering provider.
7. Expand product interaction metrics to include contract ID, provider, consumer, tenant, status, reason code.
8. Add policy decision audit event for each broker execution.
9. Add product interaction graph and run-history query APIs.
10. Add product shape/language/runtime validation across Java/TS/Rust/Python.

### Kernel Toolchains

1. Add realistic polyglot fixture product.
2. Harden Rust adapter output parsing and artifact manifest generation.
3. Harden Python adapter env/dependency handling and artifact manifests.
4. Add Node service runtime/deploy fixture.
5. Add missing-toolchain environment-blocked tests.
6. Add security scan hooks per package ecosystem.
7. Add dependency cache strategy per language.

### Governance / CI

1. Replace filename-based domain invariant check with invariant manifest and executed test evidence.
2. Expand OpenAPI breaking-change detection to schema-aware diff.
3. Add evidence freshness policy and critical evidence expiration checks.
4. Add runtime SLO measurement check.
5. Add cost measurement/enforcement check.
6. Add “no target-state as current-state” validation for product readiness docs.

### Digital Marketing

1. Implement consent-gated campaign activation with broker call to PHR.
2. Replace notification preference handler with real preference service.
3. Implement durable lead-captured event publish flow.
4. Add Google Ads connector readiness state machine.
5. Implement campaign lifecycle workflow tests.
6. Implement dashboard/report data contracts and no-stub checks.
7. Add UI degraded/blocked/consent-denied states.

### PHR

1. Implement real consent grant/revocation/expiry domain model.
2. Back consent-status interaction handler by real consent service.
3. Add FHIR R4 validation evidence per resource type/profile.
4. Add patient data access matrix tests.
5. Implement break-glass emergency workflow with audit and post-review.
6. Implement data sovereignty evidence from actual storage/provider decisions.
7. Enable rollback only after healthcare post-rollback gates are real.

### Studio

1. Add product readiness dashboard with:
   - lifecycle status
   - SLO budget status
   - cost budget status
   - interaction graph
   - missing feature/workflow checklist
2. Add one-click plan/execute/recover per product and phase.
3. Add product interaction evidence viewer.
4. Add polyglot surface detection wizard.
5. Add “business logic only” onboarding flow.

---

## 10. Definition of Done

The platform is production-ready only when:

- PHR and Digital Marketing are feature-complete for their pilot workflows.
- Java, TypeScript, Rust, and Python surfaces are proven through Kernel lifecycle fixtures.
- Product teams can register and run products without knowing adapter internals.
- SLO/cost budgets are measured and enforced, not just declared.
- Domain invariants are semantically mapped to executed tests and evidence.
- OpenAPI breaking changes are schema-aware and waiver-governed.
- Product interactions are brokered, policy-checked, evidence-backed, observable, and used in real product workflows.
- Plugin interactions are typed, observable, policy-aware, and cycle-safe.
- Long-running/runtime validations are part of explicit release gates and never represented as passed unless actually executed.
- Studio and CLI provide clear recovery guidance and hide platform complexity by default.

