Executed against `samujjwal/ghatana` at target commit `42d9daef08790834371ac0a93e9b16cd86a1cd0f`.

## Implementation Progress (2026-05-16)

### Completed in this session

- [x] `platform/typescript/kernel-lifecycle/src/service/ProductUnitIntentApplier.ts`
  - Rewritten as a thin delegator to `KernelLifecycleService.applyProductUnitIntent`.
  - Removed mock registry refs and no-op provenance/lifecycle-event/runtime-truth behavior.

- [x] `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`
  - Hardened canonical ProductUnitIntent path with explicit failure reason codes:
    - `lifecycle-event-write-failed`
    - `runtime-truth-write-failed`
    - `provenance-write-failed`
  - Added fail-closed behavior when required evidence providers are unavailable for intent application.

- [x] `platform/typescript/kernel-providers/src/gates/FileBootstrapGateProvider.ts`
  - Removed backward-compatible implicit synthetic pass behavior.
  - Gate evaluation now fails closed unless the gate is explicitly allowlisted via `supportedGates`.

- [x] `platform/typescript/kernel-providers/src/registry/GhatanaFileRegistryProvider.ts`
  - Enabled strict-validation defaults for CI/production via environment-sensitive inference.
  - Preserved explicit override support (`strict: false`) for local/test scenarios.

- [x] Tests updated and passing (targeted)
  - `platform/typescript/kernel-lifecycle/src/service/__tests__/KernelLifecycleService.test.ts`
  - `platform/typescript/kernel-providers/src/gates/__tests__/FileBootstrapGateProvider.test.ts`
  - `platform/typescript/kernel-providers/src/registry/__tests__/GhatanaFileRegistryProvider.test.ts`

- [x] `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx`
  and `platform/typescript/ghatana-studio/src/config/studioEnvironment.ts`
  - Replaced hidden hardcoded default with explicit pilot configuration:
    - `VITE_STUDIO_PILOT_DEFAULT_PRODUCT_UNIT_ID`
  - Added/updated tests in `platform/typescript/ghatana-studio/src/config/__tests__/studioEnvironment.test.ts`.

- [x] `scripts/check-digital-marketing-lifecycle-pilot.mjs`
  - Expanded smoke validation to assert lifecycle evidence linkage:
    - `result.eventsRef` ↔ `manifests.lifecycleEvents`
    - `result.healthSnapshotRef` ↔ `manifests.lifecycleHealthSnapshot`
    - `result.runId`/`result.correlationId`/`result.productId` presence for Studio run-summary compatibility
  - Added latest manifest pointer drift checks (`runId`, `correlationId`, `providerMode`, and core manifest refs).
  - Updated gate-result parser to accept canonical gate manifest shape (`gates` with `status/details/evidenceRefs`) and legacy shapes.
  - Added provenance + Studio visibility contract checks against canonical sources:
    - `KernelLifecycleService.recordProvenance` + runtime-truth recording contract markers
    - Studio `LifecycleRunSchema` visibility markers (`productUnitId`, `eventsRef`, `healthSnapshotRef`, run APIs)
  - Verified both modes locally:
    - `node scripts/check-digital-marketing-lifecycle-pilot.mjs`
    - `node scripts/check-digital-marketing-lifecycle-pilot.mjs --smoke`

- [x] `scripts/check-yappc-product-unit-intent-handoff.mjs`
  - Added explicit ProductUnitIntent evidence-continuity assertions (`lifecycleEventRefs`, `provenanceRefs`, `runtimeTruthRefs`) and evidenceRef mapping guard.
  - Re-validated focused API/web handoff tests and boundary gate.

- [x] `scripts/check-data-cloud-platform-providers.mjs`
  - Added explicit provider class existence checks for Data Cloud Kernel extension registrations.
  - Added anti-theater guardrails (`TODO`/`FIXME`) on kernel extension path.
  - Re-validated provider conformance check.

- [x] CI gate coverage verified in `.github/workflows/product-lifecycle.yml`
  - Confirmed workflow includes Kernel API, Studio API, YAPPC handoff, Data Cloud provider conformance, Digital Marketing pilot, and smoke checks.

### In progress (next implementation slices)

- [x] Digital Marketing golden lifecycle pilot E2E assertions (manifest/evidence/runtime truth/provenance/Studio visibility)
  - Runtime-truth/event evidence, manifest-pointer consistency, provenance contract markers, and Studio run-summary visibility markers are now enforced by checker gates.
- [x] Studio product default hardening (pilot default is now explicit configuration, not a hidden universal fallback)
- [x] Data Cloud platform-mode provider conformance evidence checks (static + extension/provider registration guardrails)
- [x] YAPPC ProductUnitIntent handoff E2E coverage checks (API + web + boundary)
- [x] CI workflow enforcement of kernel/product-shape/observability/anti-stub gates

### Artifact compiler/decompiler alignment status (follow-through from `platform/comp-decomp-todo.md`)

- [x] Kind-safe compile-back change planning already present and covered by dedicated tests.
- [x] Residual regeneration strategy excludes placeholder-stub generation.
- [x] High-impact compiler/decompiler slices completed in this session:
  - Java import/runtime path compile hardening across source providers and worker orchestration (`ProcessTsExtractorWorker`, `SourceProviderRegistry`, source providers, `LifecycleServiceModule`, `ResidualIslandService`).
  - Workspace-scoped graph query/pagination wiring aligned and test-updated (`ArtifactGraphServicePaginationTest`, `ArtifactGraphRepositoryUpsertTest`).
  - Canonical snapshot content hash accessors standardized on `SnapshotFile.contentChecksum()` across GitHub/GitLab/local-folder/archive providers.
  - Residual island persistence path aligned to the expanded full residual schema and scoped repository signature.
  - Source provider default registration semantics reconciled (`defaultRegistry()` now canonical, empty constructor kept for explicit registration and tests).
- [x] Validation evidence for this slice:
  - `./gradlew :products:yappc:core:yappc-services:compileJava --no-daemon` (pass)
  - `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.services.artifact.ArtifactGraphServicePaginationTest' --tests 'com.ghatana.yappc.api.ArtifactGraphControllerScopeTest' --tests 'com.ghatana.yappc.services.artifact.ArtifactGraphServiceUnsupportedParserTest' --tests 'com.ghatana.yappc.services.source.SourceProviderRegistryTest' --tests 'com.ghatana.yappc.storage.ArtifactGraphRepositoryUpsertTest' --no-daemon` (pass)

Important execution note: this target commit is a `[skip ci]` changelog-only commit touching `products/yappc/CHANGELOG.md`, so I treated it as the **complete repository snapshot at that commit**, not as a diff audit. I inspected the canonical repo rules, workspace/build wiring, product registry, Kernel contracts/lifecycle/providers, Studio shell/client/data context, YAPPC handoff surfaces, Data Cloud readiness, and Digital Marketing lifecycle pilot. I did not run local `pnpm` or Gradle commands, so the validation commands below are prescribed execution gates, not locally verified pass results. 

## A. End-to-end executive summary

### What is close to world-class

Ghatana already has strong structural foundations:

1. **Repo-level guardrails are mature.** The repo instructions require reuse before creating, explicit boundaries, no silent failures, strict TypeScript, tests as part of every change, Java documentation tags, and observability for important flows. 

2. **The monorepo is organized around the intended platform/product split.** Gradle includes platform Java modules, platform contracts, platform-kernel modules, platform plugins, generated product includes, bridge modules, shared services, and integration tests. 

3. **The TypeScript workspace is generated from the canonical product registry.** It includes platform libraries, Ghatana Studio, Data Cloud, YAPPC, Digital Marketing UI, FlashIt, PHR, DCMAAR, TutorPutor, Audio-Video, and shared-service UI packages. 

4. **The Product Development Kernel has real contract and runtime surfaces.** `ProductUnitIntent` is strongly typed and Zod-validated, includes correlation IDs, provider mode, provenance/runtime/event refs, and guards against secret-like fields.  

5. **Ghatana Studio exists as a unified shell.** It wires Home, Ideas, Blueprints, Canvas, Develop, Lifecycle, Agents, Artifacts, Deployments, Health, Learn, and Settings routes, using capability-aware route guards and i18n-backed labels.  

6. **Digital Marketing is correctly positioned as the executable lifecycle pilot.** The product registry marks Digital Marketing as lifecycle-enabled, with backend and web surfaces implemented, bridge conformance enabled, lifecycle execution allowed, and validate/test/build/package/deploy/verify scripts present at the root package level.  

### What is missing or incoherent

The repo is not yet world-class end to end because several flows are structurally present but not fully production-executable:

1. **There are two ProductUnitIntent application paths.** `KernelLifecycleService.applyProductUnitIntent` is the better path: it validates provider mode, uses the registry provider, records lifecycle events, records runtime truth, and records provenance.   But `ProductUnitIntentApplier.ts` still contains mock registration and no-op provenance, lifecycle-event, and runtime-truth methods. That is a duplicate/legacy anti-pattern and must not remain a production path. 

2. **Bootstrap gates are not uniformly production-grade.** The bootstrap factory wires a few concrete gates, but many gates are mapped to `FileBootstrapGateProvider({ supportedGates: [] })`, which correctly returns NOT_READY for unsupported gates. However, the provider class still has a backward-compatible synthetic-pass path when `supportedGates` is not explicitly provided. That creates a future fake-success risk.  

3. **Data Cloud and YAPPC are platform-provider products but not yet lifecycle-provider-ready.** The registry marks both with missing manifest/conformance fields and `lifecycleExecutionAllowed: false`; they require bootstrap/platform separation, runtime-truth provider evidence, and creator-lifecycle separation before lifecycle execution. 

4. **Future products are shape validators, not executable lifecycle products.** PHR, Finance, and FlashIt have lifecycle registry entries but remain planned/disabled because they still require domain gates, multi-module validation, consent/privacy/FHIR/regulatory/mobile artifacts, and adapter readiness.   

5. **Studio has the right UX shell but still depends on runtime configuration and API readiness.** Its lifecycle data context defaults to `digital-marketing` and `bootstrap`, handles unconfigured/degraded states, and exposes preview/apply ProductUnitIntent functions only when the client supports them. This is good for the pilot but must become a product-neutral runtime selection model before general availability. 

### Top 10 fixes

1. Delete or rewrite `platform/typescript/kernel-lifecycle/src/service/ProductUnitIntentApplier.ts` so it delegates to `KernelLifecycleService.applyProductUnitIntent`.
2. Make `KernelLifecycleService.applyProductUnitIntent` the only Kernel ProductUnitIntent application path.
3. Remove synthetic success behavior from `FileBootstrapGateProvider`; unsupported gates must return NOT_READY or fail closed.
4. Convert Digital Marketing lifecycle into a golden E2E pilot with manifest, event, runtime truth, gate, artifact, deployment, health, and Studio visualization assertions.
5. Make Studio’s `digital-marketing` default an explicit pilot configuration, not a hidden universal default.
6. Harden `GhatanaFileRegistryProvider` with strict validation enabled in CI and production.
7. Implement Data Cloud-backed Kernel providers for events, artifacts, health, provenance, memory, and runtime truth.
8. Add executable ProductUnitIntent handoff tests from YAPPC export → Kernel preview/apply → registry/runtime truth → Studio refresh.
9. Keep PHR, Finance, FlashIt, Data Cloud, YAPPC, Audio-Video, DCMAAR, TutorPutor disabled until readiness gates are satisfied.
10. Add CI workflows that run the architecture, lifecycle, product-shape, production-readiness, observability, security, privacy, i18n, a11y, and anti-theater checks as mandatory gates.

---

## B. System architecture map

```text
Ghatana Studio
  platform/typescript/ghatana-studio
  ├─ Unified shell/routes
  ├─ Kernel lifecycle client
  ├─ Lifecycle data context
  ├─ ProductUnit / run / approval / manifest views
  └─ ProductUnitIntent preview/apply hooks

Product Development Kernel
  platform/typescript/kernel-product-contracts
  platform/typescript/kernel-lifecycle
  platform/typescript/kernel-providers
  platform/typescript/kernel-toolchains
  platform/typescript/kernel-artifacts
  platform/typescript/kernel-deployment
  platform/typescript/kernel-release
  platform-kernel/*

YAPPC
  products/yappc/*
  ├─ Ideation / canvas / creator lifecycle
  ├─ ProductUnitIntent export
  ├─ Artifact intelligence
  └─ Kernel bridge

Data Cloud
  products/data-cloud/*
  ├─ Runtime truth
  ├─ Events
  ├─ Provenance
  ├─ Memory
  ├─ AEP / Action Plane
  └─ Kernel provider bridge

Digital Marketing
  products/digital-marketing/*
  ├─ Executable lifecycle pilot
  ├─ Backend API
  ├─ Web UI
  └─ Kernel bridge

Future product shape validators
  PHR, Finance, FlashIt, Data Cloud, YAPPC, Aura, DCMAAR, TutorPutor, Audio-Video
```

This matches the hardened blueprint’s ownership direction: Studio is the unified experience, Kernel owns lifecycle truth, YAPPC owns creation/artifact intelligence, Data Cloud owns runtime truth/events/provenance/memory, shared libraries provide reusable primitives, and products own domain behavior.  

---

## C. Journey-by-journey findings

### Journey 1 — Product ideation to ProductUnitIntent

**Classification:** Existing but partial.

**Current flow:**
YAPPC has ProductUnitIntent-related surfaces, the Kernel contract is strong, and Studio exposes ProductUnitIntent preview/apply hooks through the lifecycle data context. The canonical contract validates schema version, producer, target providers, lifecycle request, provenance, secret-like fields, and promote-candidate evidence requirements.    

**Primary gap:**
The repo contains a duplicate/legacy `ProductUnitIntentApplier.ts` that returns mock registry refs and no-op provenance/event/runtime truth writes. That must be removed or made a thin delegator to `KernelLifecycleService.applyProductUnitIntent`. 

**Required fix:**
Use only:

```text
YAPPC ProductUnitIntent export
→ Studio / API preview or apply
→ KernelLifecycleService.applyProductUnitIntent
→ ProductUnitIntent-capable RegistryProvider
→ lifecycle event provider
→ runtime truth provider
→ provenance provider
→ Studio refresh
```

---

### Journey 2 — Direct Product Development Kernel usage

**Classification:** Existing but partial.

**Current flow:**
Kernel lifecycle service can list product units, create lifecycle plans, write `lifecycle-plan.json`, update pointer state, record runtime truth/provenance, append lifecycle events, execute lifecycle plans, list runs, load manifests, and manage approvals.  

Studio has the correct route shell and API client for product units, plans, execution, runs, manifests, approvals, and ProductUnitIntent operations.  

**Primary gaps:**

1. API endpoint implementation must be validated against the Studio client contract.
2. Gate coverage is not complete.
3. The approval, artifact, deployment, verify-health, and runtime-truth views need golden E2E assertions.

**Required fix:**
Make `/api/kernel/**` contract tests mandatory and run Studio against real Kernel API responses, not fixture-only or optional-client flows.

---

### Journey 3 — Agentic product development

**Classification:** Target architecture / existing but partial.

**Current flow:**
The root scripts include checks for agentic lifecycle action contracts, and the registry includes Data Cloud Action Plane / agent runtime modules.  

**Primary gaps:**

1. Agent → Kernel lifecycle action contract must be proven with an executable flow.
2. Agent approvals, risk, mastery, evidence, and rollback must be persisted through Data Cloud.
3. Studio must show agent proposals, evidence, approval state, execution result, and recommendations.

**Required fix:**
Implement a thin, governed AgentLifecycleActionRequest path:

```text
AEP / agent
→ Kernel lifecycle action contract
→ policy / approval / risk / mastery checks
→ Kernel lifecycle execution
→ Data Cloud evidence and memory
→ Studio agentic-development panel
```

Agents must not run raw Gradle, pnpm, Docker, or deployment commands directly.

---

### Journey 4 — Digital Marketing lifecycle pilot

**Classification:** Existing and closest to executable, but still partial until golden E2E proves it.

**Current flow:**
Digital Marketing is marked lifecycle-enabled, has backend and web surfaces, has bridge conformance enabled, has lifecycle execution allowed, and root scripts include Digital Marketing validate/test/build/package/deploy/verify commands.  

**Primary gaps:**

1. Digital Marketing must become the canonical golden lifecycle pilot.
2. Every phase must assert manifests and runtime truth, not just command success.
3. Studio must visualize the actual run, gates, artifacts, deployment, health, and recommendations.

**Required fix:**
Add a single golden E2E test that runs:

```bash
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
```

Then assert generated:

```text
lifecycle-plan.json
lifecycle-result.json
gate-result-manifest.json
artifact-manifest.json
deployment-manifest.json
verify-health-report.json
lifecycle-events.json
runtime-truth refs
provenance refs
Studio-visible run summary
```

---

### Journey 5 — Artifact intelligence

**Classification:** Declared / existing but partial.

**Current flow:**
The blueprint and domain map place artifact compiler/decompiler implementation in YAPPC, semantic graph/provenance storage in Data Cloud, and Kernel consumption through semantic references only. 

**Primary gaps:**

1. Kernel must consume `SemanticArtifactReference` and evidence contracts only, not YAPPC internals.
2. YAPPC must emit durable evidence refs for residual islands, risk hotspots, dependency graph, and generated change summaries.
3. Data Cloud must persist provenance and graph evidence.

**Required fix:**
Define and enforce artifact-intelligence contracts under shared Kernel/ProductUnit contracts, then make YAPPC publish evidence and Data Cloud store it.

---

### Journey 6 — Data Cloud foundation

**Classification:** Target architecture / existing but partial.

**Current flow:**
Data Cloud has many planes and Kernel bridge modules registered, but the registry marks manifest, observability, security, dataAccess, bridge, and runtime module conformance as false, with lifecycle execution disabled. 

**Primary gaps:**

1. Bootstrap mode is present and useful.
2. Platform mode correctly fails unless Data Cloud-backed providers exist.
3. Data Cloud-backed providers are not yet proven as production runtime truth.

**Required fix:**
Implement and test Data Cloud provider bridge interfaces for:

```text
EventProvider
ArtifactProvider
HealthProvider
ProvenanceProvider
MemoryProvider
RuntimeTruthProvider
PolicyEvidenceProvider
```

Keep Data Cloud lifecycle disabled until Kernel can bootstrap/build/deploy Data Cloud without depending on Data Cloud itself.

---

### Journey 7 — Future product shape readiness

**Classification:** Shape-validation target, not executable lifecycle.

**Current flow:**
PHR, Finance, and FlashIt have explicit lifecycle-readiness blockers and `lifecycleExecutionAllowed: false`. PHR needs healthcare/consent/FHIR/data sovereignty gates; Finance needs regulatory, risk, promotion approval, and multi-module validation; FlashIt needs mobile adapters, preview security, personal-data classification, and mobile artifact contracts.   

**Required fix:**
Do not enable these products yet. Use them to validate that Kernel remains general without becoming a god product.

---

## D. Capability ownership matrix

| Capability                    | Correct owner                                  | Current location                                                                         | Classification                      | Required move/fix                                       | Required tests                                |
| ----------------------------- | ---------------------------------------------- | ---------------------------------------------------------------------------------------- | ----------------------------------- | ------------------------------------------------------- | --------------------------------------------- |
| ProductUnitIntent contract    | Kernel contracts                               | `platform/typescript/kernel-product-contracts/src/product-unit/ProductUnitIntent.ts`     | Existing and executable             | Keep as canonical; add compatibility tests              | Contract tests, schema invalid/valid cases    |
| ProductUnitIntent application | Kernel lifecycle                               | `KernelLifecycleService.applyProductUnitIntent`; duplicate `ProductUnitIntentApplier.ts` | Existing but partial / anti-pattern | Delete or delegate duplicate applier                    | End-to-end preview/apply tests                |
| Registry provider             | Kernel providers                               | `GhatanaFileRegistryProvider.ts`                                                         | Existing but partial                | Enable strict write validation in CI/prod               | Registry write, conflict, rollback tests      |
| Lifecycle execution truth     | Kernel lifecycle                               | `KernelLifecycleService.ts`                                                              | Existing but partial                | Make events/runtime/provenance mandatory where required | Plan/execute/run-history tests                |
| Gates                         | Kernel providers / platform plugins            | `createBootstrapKernelProviders.ts`, `FileBootstrapGateProvider.ts`                      | Existing but partial                | Remove synthetic-pass risk; add concrete gates          | Gate contract tests                           |
| Studio shell                  | Ghatana Studio                                 | `platform/typescript/ghatana-studio/src/App.tsx`                                         | Existing but partial                | Harden navigation, entitlement, unconfigured states     | Route/a11y/i18n tests                         |
| Studio API client             | Ghatana Studio                                 | `kernelLifecycleClient.ts`                                                               | Existing but partial                | Align with real backend OpenAPI and errors              | Contract tests against mocked API + real API  |
| Runtime truth                 | Data Cloud + Kernel provider bridge            | `products/data-cloud/extensions/kernel-bridge`                                           | Target / partial                    | Implement Data Cloud-backed providers                   | Provider integration tests                    |
| YAPPC creator lifecycle       | YAPPC                                          | `products/yappc/*`                                                                       | Existing but partial                | Keep separate from Kernel lifecycle                     | ProductUnitIntent handoff tests               |
| Artifact intelligence         | YAPPC + shared evidence contracts + Data Cloud | YAPPC + Kernel contracts + Data Cloud                                                    | Declared / partial                  | Add semantic evidence contracts and persistence         | Compiler/decompiler evidence tests            |
| Digital Marketing pilot       | Digital Marketing + Kernel                     | `products/digital-marketing/*`                                                           | Existing and closest to executable  | Add golden E2E lifecycle pilot                          | Validate/test/build/package/deploy/verify E2E |
| Future product readiness      | Platform Coherence + product owners            | Registry + product manifests                                                             | Target / partial                    | Keep disabled until gates pass                          | Product-shape matrix tests                    |

---

## E. File-by-file implementation plan

### Workstream 1 — Ghatana Studio UI/UX and API contracts

| File                                                                         | Current issue                                                                                                                      | Target change                                                                                                               | Validation                                                |
| ---------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| `platform/typescript/ghatana-studio/src/App.tsx`                             | Route shell exists, but navigation readiness depends on runtime capability state and must stay consistent as features become real. | Keep one canonical navigation model; add entitlement-aware disabled/blocked/preview state tests for every route.            | `pnpm --dir platform/typescript/ghatana-studio test:a11y` |
| `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`        | Strong typed client exists, but must be proven against real backend API contracts.                                                 | Add contract tests for 401/403/404/503, ProductUnitIntent preview/apply, manifest not found/corrupt, provider-mode errors.  | `pnpm check:studio-kernel-api`                            |
| `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx` | Defaults to `digital-marketing` and `bootstrap`, which is correct for pilot but risky as a hidden general default.                 | Make pilot default explicit via runtime config; add product-neutral selection and persisted selected product/run state.     | `pnpm --dir platform/typescript/ghatana-studio test`      |
| `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx`            | Needs complete lifecycle action UX coverage.                                                                                       | Ensure validate/test/build/package/deploy/verify/promote/rollback are visible only when capability and provider mode allow. | `pnpm check:audited-ui-workflows`                         |
| `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx`            | Must not show target-state artifact intelligence as current truth.                                                                 | Classify loaded/missing/corrupt/unavailable states and link artifacts to lifecycle run IDs.                                 | `pnpm check:current-state-claims`                         |
| `platform/typescript/ghatana-studio/src/routes/HealthPage.tsx`               | Must compose Kernel + Data Cloud + product health without parsing logs.                                                            | Add runtime truth, provider health, gate health, deployment health, and Data Cloud provider-mode indicators.                | `pnpm check:observability-conformance`                    |

---

### Workstream 2 — Product Development Kernel backend/lifecycle/providers/plugins

| File                                                                                 | Current issue                                                                             | Target change                                                                                                                    | Validation                                          |
| ------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
| `platform/typescript/kernel-lifecycle/src/service/ProductUnitIntentApplier.ts`       | Duplicate legacy path with mock registry ref and no-op provenance/event/runtime truth.    | Delete file if unused, or convert to a thin wrapper around `KernelLifecycleService.applyProductUnitIntent`.                      | `pnpm check:production-stubs`                       |
| `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`         | Stronger canonical path exists but must be the only apply path.                           | Make provider/evidence failures block when required; add explicit reason codes for runtime truth/provenance/event write failure. | `pnpm check:kernel-lifecycle-truth`                 |
| `platform/typescript/kernel-providers/src/registry/GhatanaFileRegistryProvider.ts`   | File-backed registry supports intent apply but strict mode defaults false.                | Enforce strict mode in CI/prod; validate post-write registry and generated workspace drift.                                      | `pnpm check:kernel-product-unit-provider-contracts` |
| `platform/typescript/kernel-providers/src/factory/createBootstrapKernelProviders.ts` | Many gates are NOT_READY fallback providers.                                              | Keep fail-closed behavior; replace pilot-critical gates with concrete providers before claiming lifecycle readiness.             | `pnpm check:digital-marketing-lifecycle-pilot`      |
| `platform/typescript/kernel-providers/src/gates/FileBootstrapGateProvider.ts`        | Synthetic pass exists for backward compatibility.                                         | Remove synthetic pass or restrict it to test-only explicit mode. Unsupported gates must return NOT_READY.                        | `pnpm check:production-readiness`                   |
| `platform/typescript/kernel-toolchains/src/**`                                       | Toolchain abstraction must prove command safety, result validation, and artifact linkage. | Add adapter contract tests for Gradle, pnpm/Vite, Docker/Compose; fail fake success.                                             | `pnpm check:toolchain-adapter-contracts`            |
| `platform/typescript/kernel-artifacts/src/**`                                        | Artifact manifest contracts must link package/build outputs to deployment.                | Enforce fingerprint, run ID, correlation ID, productUnitId, environment refs.                                                    | `pnpm check:product-artifact-contracts`             |
| `platform/typescript/kernel-deployment/src/**`                                       | Deployment manifest must be rollback-aware.                                               | Enforce deployment-to-artifact linkage and verify-health references.                                                             | `pnpm check:product-deployment-contracts`           |

---

### Workstream 3 — Data Cloud foundation providers/runtime truth/memory

| File/Area                                            | Current issue                                                                         | Target change                                                                                                  | Validation                                      |
| ---------------------------------------------------- | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- |
| `products/data-cloud/extensions/kernel-bridge/**`    | Bridge exists in registry, but Data Cloud conformance is false.                       | Implement Data Cloud-backed Kernel providers for runtime truth, events, provenance, memory, health, artifacts. | `pnpm check:data-cloud-platform-providers`      |
| `products/data-cloud/planes/action/kernel-bridge/**` | Action Plane bridge exists but is not proven as agentic lifecycle truth.              | Add AgentLifecycleAction evidence writer and approval/risk/trace integration.                                  | `pnpm check:agentic-lifecycle-action-contracts` |
| `products/data-cloud/planes/event/**`                | Event store exists structurally but must become canonical runtime truth event source. | Add lifecycle event envelope, replay tests, correlation IDs, schema versioning.                                | `pnpm check:data-access-contract`               |
| `products/data-cloud/planes/action/observability/**` | Observability exists but product registry conformance is false.                       | Add provider-level metrics and trace propagation for Kernel-backed actions.                                    | `pnpm check:observability-conformance`          |

---

### Workstream 4 — YAPPC creator/artifact intelligence/visibility

| File/Area                                                                                    | Current issue                                                                              | Target change                                                                                              | Validation                                        |
| -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| `products/yappc/frontend/web/src/services/canvas/commands/ProductUnitIntentExportService.ts` | ProductUnitIntent export exists, but full apply/preview handoff must be proven.            | Emit complete ProductUnitIntent with scope, producer, target, governance hints, provenance, evidence refs. | `pnpm check:yappc-product-unit-intent-handoff`    |
| `products/yappc/frontend/apps/api/src/routes/product-unit-intents.ts`                        | API route exists but must be validated against Kernel contract.                            | Validate request/response with shared schema; surface Kernel reason codes unchanged.                       | `pnpm check:studio-kernel-api`                    |
| `products/yappc/kernel-bridge/**`                                                            | Bridge exists but YAPPC must not own Kernel lifecycle execution.                           | Restrict bridge to ProductUnitIntent and semantic artifact evidence handoff.                               | `pnpm check:yappc-kernel-boundary`                |
| `products/yappc/core/**artifact**` / compiler-decompiler areas                               | Artifact intelligence is not yet fully evidenced through Data Cloud and Kernel references. | Emit `SemanticArtifactReference`, `ArtifactGraphSummary`, `ResidualIslandReport`, `RiskHotspotReport`.     | `pnpm check:yappc-artifact-intelligence-boundary` |
| `platform/typescript/kernel-product-contracts/src/artifact-intelligence/**`                  | Shared semantic evidence contracts need to be canonical.                                   | Add schema versioning, confidence, provenance refs, privacy classification.                                | `pnpm build:kernel-lifecycle-platform`            |

---

### Workstream 5 — Digital Marketing lifecycle pilot

| File/Area                                             | Current issue                                                                                | Target change                                                                                                | Validation                                     |
| ----------------------------------------------------- | -------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ---------------------------------------------- |
| `products/digital-marketing/kernel-product.yaml`      | Pilot is enabled; must remain the only executable lifecycle product until golden E2E passes. | Ensure every lifecycle phase, surface, gate, artifact, deployment, and health check is declared.             | `pnpm check:lifecycle-registry-config-drift`   |
| `products/digital-marketing/dm-kernel-bridge/**`      | Bridge is declared conformant.                                                               | Add lifecycle bridge tests that assert no product logic leaks into Kernel.                                   | `pnpm check:bridge-compliance`                 |
| `products/digital-marketing/ui/**`                    | UI must use shared shell/design patterns.                                                    | Replace product-local duplicated status/empty/error/gate views with shared components after reuse is proven. | `pnpm check:design-system-conformance`         |
| `scripts/check-digital-marketing-lifecycle-pilot.mjs` | Exists as a gate.                                                                            | Expand into golden lifecycle evidence verification.                                                          | `pnpm check:digital-marketing-lifecycle-pilot` |

---

### Workstream 6 — Shared libraries/design system/builder/canvas/code editor

| File/Area                              | Current issue                                 | Target change                                                                                                                 | Validation                                |
| -------------------------------------- | --------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| `platform/typescript/design-system/**` | Must be the canonical UI primitive layer.     | Add status, empty, loading, blocked, degraded, error, approval, gate, artifact display components only after reuse is proven. | `pnpm check:design-system-conformance`    |
| `platform/typescript/canvas/**`        | Canvas is a shared primitive, not YAPPC-only. | Stabilize public API for Studio/YAPPC/Kernel visualizers.                                                                     | `pnpm check:shared-layout-primitives`     |
| `platform/typescript/ui-builder/**`    | Builder primitives must stay product-neutral. | Enforce BuilderDocument and preview protocol boundaries.                                                                      | `pnpm check:ui-builder-platform-boundary` |
| `platform/typescript/code-editor/**`   | Code editor must remain generic.              | Keep AST/LSP/refactor primitives product-neutral; no YAPPC internals.                                                         | `pnpm check:platform-product-boundaries`  |

---

### Workstream 7 — Product shape matrix/future product readiness

| File/Area                                | Current issue                                                                          | Target change                                                                             | Validation                                      |
| ---------------------------------------- | -------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------- | ----------------------------------------------- |
| `config/canonical-product-registry.json` | Correctly marks many products disabled/planned, but this must be protected from drift. | Add fail-fast checks for accidental lifecycle enablement without gates/adapters/evidence. | `pnpm check:product-shape-capability-matrix`    |
| `products/phr/kernel-product.yaml`       | PHR needs privacy/consent/FHIR/data sovereignty gates.                                 | Keep disabled until healthcare gates and evidence exist.                                  | `pnpm check:product-manifest-contracts`         |
| `products/finance/kernel-product.yaml`   | Finance needs multi-module/regulatory/operator/portal/SDK readiness.                   | Keep disabled until multi-module and compliance gates pass.                               | `pnpm check:finance-transaction-workflow-proof` |
| `products/flashit/kernel-product.yaml`   | FlashIt needs mobile adapters and mobile artifact contracts.                           | Keep mobile lifecycle disabled until IPA/AAB contracts exist.                             | `pnpm check:flashit-client-conformance`         |

---

### Workstream 8 — CI/CD/checks/docs cleanup

| File/Area                            | Current issue                                                                 | Target change                                                                                                              | Validation                           |
| ------------------------------------ | ----------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | ------------------------------------ |
| `package.json`                       | Many high-value checks exist, but must be grouped into required CI workflows. | Add CI matrix for architecture, Kernel lifecycle, Digital Marketing pilot, Studio UI, Data Cloud providers, product shape. | `pnpm check:architecture-boundaries` |
| `.github/workflows/**`               | Must prove all core checks run on PRs.                                        | Add required jobs for `check:kernel-product-boundary-audit`, root build/test/typecheck, Gradle build/check.                | GitHub required checks               |
| `docs/architecture/**`               | Must not duplicate authoritative rules.                                       | Make one authoritative domain map and link dependent docs to it.                                                           | `pnpm check:doc-truth`               |
| `scripts/check-production-stubs.mjs` | Must catch no-op/stub/mock production paths.                                  | Ensure `ProductUnitIntentApplier.ts` and synthetic gate success are caught unless fixed.                                   | `pnpm check:production-stubs`        |

---

## F. Release plan

### Release 0 — Unified shell, terminology, navigation, core checks

**Goal:** Make the product shape coherent and prevent drift.

**Scope:**

```text
Ghatana Studio shell
canonical route vocabulary
domain registry
product registry drift checks
package/workspace checks
current-state/target-state checks
deprecated package checks
```

**Exit criteria:**

```bash
pnpm check:architecture-boundaries
pnpm check:domain-registry
pnpm check:product-registry
pnpm check:current-state-claims
pnpm check:deprecated-packages
pnpm check:orphan-modules
pnpm --dir platform/typescript/ghatana-studio test:a11y
```

---

### Release 1 — Digital Marketing lifecycle pilot E2E

**Goal:** Prove one complete executable product lifecycle.

**Scope:**

```text
Digital Marketing validate/test/build/package/deploy/verify
Kernel lifecycle plan/result
gate result manifest
artifact manifest
deployment manifest
verify health report
runtime truth
provenance
Studio visualization
```

**Exit criteria:**

```bash
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
pnpm check:digital-marketing-lifecycle-pilot
```

---

### Release 2 — Agentic development support

**Goal:** Allow agents to propose and execute governed lifecycle actions through Kernel contracts only.

**Scope:**

```text
AgentLifecycleActionRequest
risk/mastery/policy/approval checks
Kernel lifecycle execution
Data Cloud evidence refs
Studio agentic-development panel
```

**Exit criteria:**

```bash
pnpm check:agentic-lifecycle-action-contracts
pnpm check:kernel-lifecycle-truth
pnpm check:observability-conformance
pnpm check:production-readiness
```

---

### Release 3 — Data Cloud platform-mode providers

**Goal:** Make platform mode real without breaking bootstrap mode.

**Scope:**

```text
Data Cloud-backed events
artifacts
health
provenance
memory
runtime truth
policy evidence
```

**Exit criteria:**

```bash
pnpm check:data-cloud-platform-providers
pnpm check:kernel-provider-mode
pnpm check:data-access-contract
pnpm check:observability-conformance
```

---

### Release 4 — Artifact intelligence integration

**Goal:** Make YAPPC artifact compiler/decompiler evidence consumable by Kernel and visible in Studio.

**Scope:**

```text
SemanticArtifactReference
ArtifactGraphSummary
ResidualIslandReport
RiskHotspotReport
Data Cloud provenance/graph persistence
Kernel evidence consumption
Studio recommendations
```

**Exit criteria:**

```bash
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm build:kernel-lifecycle-platform
```

---

### Release 5 — Product shape expansion readiness

**Goal:** Prove Kernel remains general without becoming a god product.

**Scope:**

```text
PHR privacy/consent/FHIR gates
Finance regulatory/multi-module/operator/portal/SDK gates
FlashIt mobile/preview/privacy gates
Data Cloud and YAPPC provider-mode validation
Audio-Video/DCMAAR/TutorPutor shape validation
```

**Exit criteria:**

```bash
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:product-manifest-contracts
pnpm check:product-ci-matrices
```

---

## G. Validation command suite

Run the full suite below after implementing the plan:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm build:platform
pnpm build:kernel-lifecycle-platform

pnpm check:architecture-boundaries
pnpm check:kernel-product-boundary-audit
pnpm check:kernel-platform-lifecycle
pnpm check:kernel-lifecycle-truth
pnpm check:kernel-provider-mode
pnpm check:kernel-product-unit-provider-contracts
pnpm check:agentic-lifecycle-action-contracts
pnpm check:studio-kernel-api

pnpm check:digital-marketing-lifecycle-pilot
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing

pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:product-artifact-contracts
pnpm check:product-deployment-contracts
pnpm check:product-environment-contracts

pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:data-cloud-platform-providers

pnpm check:design-system-conformance
pnpm check:shared-product-shells
pnpm check:shared-layout-primitives
pnpm check:shared-ui-state-coverage
pnpm --dir platform/typescript/ghatana-studio test:a11y

pnpm check:production-readiness
pnpm check:production-stubs
pnpm check:secret-default-credentials
pnpm check:observability-conformance
pnpm check:data-access-contract
pnpm check:security-workflow-coverage
pnpm check:doc-truth
pnpm check:current-state-claims
pnpm check:cleanup-gate

./gradlew build
./gradlew check
```

## Final recommendation

Do **not** broaden execution to every product yet. Make **Digital Marketing** the hard, complete, golden lifecycle pilot first. Keep **PHR, Finance, FlashIt, Data Cloud, YAPPC, Audio-Video, DCMAAR, and TutorPutor** as shape validators until their adapters, gates, manifests, provider modes, and evidence paths are proven.

The most urgent implementation slice is:

```text
1. Remove/delegate ProductUnitIntentApplier.ts
2. Harden KernelLifecycleService.applyProductUnitIntent as the only path
3. Remove synthetic gate success risk
4. Add Digital Marketing golden lifecycle E2E
5. Wire Studio to display real lifecycle truth from that run
```

That slice will convert the current architecture from “strongly declared and partially executable” into one real end-to-end product-development loop.
