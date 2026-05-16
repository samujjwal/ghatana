# Full End-to-End Ghatana World-Class Product Audit

Target commit: `5f49f8677e1934f73793dfae9d6df3a008377f85`

Audit basis:
- Repo snapshot at the target commit, inspected through GitHub connector.
- `.github/copilot-instructions.md`
- Hardened Ghatana Unified Product Development Blueprint
- Ghatana Domain Workstream Map

Important limitation:
- This is a static source/config audit. I did not execute `pnpm`, `gradle`, Playwright, Vitest, Docker, or lifecycle commands in a checked-out repository runtime. Validation commands below are required next-step commands, not completed runtime evidence.

---

## A. Executive Summary

### What is close to world-class

1. **Platform governance is materially stronger than a normal monorepo.**
   The root `package.json` contains many production-readiness, boundary, lifecycle, registry, product-shape, security, observability, design-system, and cleanup checks. This shows the repo is moving toward an executable architecture governance model rather than relying only on documents.

2. **The canonical product registry is the right central source of truth.**
   `config/canonical-product-registry.json` classifies products, surfaces, lifecycle status, migration readiness, conformance, toolchains, artifacts, deployments, environments, gates, and future work. This is the correct pattern for preventing products from independently inventing lifecycle concepts.

3. **Digital Marketing is correctly treated as the lifecycle pilot.**
   It is the only product marked `lifecycleStatus: enabled` with `lifecycle.enabled: true`, and it is explicitly scoped as the validated lifecycle pilot. Its `kernel-product.yaml` includes required manifests, surfaces, phases, approvals, artifacts, gates, package config, local deployment config, provider modes, retention policy, and verify health checks.

4. **Product Development Kernel packages exist and are wired.**
   The repo has concrete TypeScript packages for `kernel-product-contracts`, `kernel-lifecycle`, `kernel-providers`, `kernel-toolchains`, `kernel-artifacts`, `kernel-deployment`, and `kernel-release`, plus Java `platform-kernel` modules.

5. **Kernel lifecycle execution has real fail-closed behavior.**
   `KernelLifecycleService`, `ProductLifecyclePlanner`, `ProductLifecycleExecutor`, and `ProductLifecycleStepRunner` have real plan, execute, manifest, provider-mode, event, approval, gate, artifact, runtime-truth, provenance, and failure handling concepts.

6. **Toolchain execution is not merely a script placeholder.**
   Toolchain contracts model adapter capability, phase, surface, output validation, duration, artifacts, tests, coverage, evidence, and observability. `SpawnCommandRunner` avoids shell execution and supports timeout, output limits, process-tree termination, and redaction.

7. **Studio has the right unified navigation skeleton.**
   `@ghatana/ghatana-studio` exists and declares the intended unified experience. Navigation metadata assigns ownership to Studio, YAPPC, Kernel, Data Cloud, or shared areas and tracks current-state route status.

8. **Data Cloud bridge implementation has real adapter infrastructure.**
   The Data Cloud kernel bridge uses authorization, audit, health, retries, and ActiveJ Promise wrapping through `AbstractKernelBridge`, with tests for mapping, failure propagation, null guards, tenant-safe isolation, transaction lifecycle, and query result mapping.

### What is missing or incoherent

1. **Studio Lifecycle UI is still too static.**
   It hardcodes Digital Marketing as the only product, uses a no-op product selector, uses a local/dev/staging/production environment list that does not match the Digital Marketing registry/config, renders a static approval queue, and shows manifests as raw JSON.

2. **Studio provider mode is not fully wired from UI state to execution calls.**
   The client supports `providerMode`, but `StudioLifecycleDataContext` does not pass selected provider mode into `createPlan` or `executePhase`.

3. **Manifest failures are hidden in Studio.**
   `loadRunManifests` catches manifest-load errors and returns `undefined`. This hides partial/corrupt lifecycle truth instead of surfacing an actionable degraded/partial-manifest state.

4. **Kernel product YAML parsing is not strict enough.**
   `ProductLifecyclePlanner.loadProductConfig` parses YAML and casts objects into kernel config types without a strict schema validation boundary.

5. **Planner fallback uses console warnings.**
   Provider fallback paths in `ProductLifecyclePlanner` use `console.warn`, not structured logger/events/diagnostic reason codes. This weakens observability and can hide degraded platform-provider behavior.

6. **Fake command execution is exported from the public toolchain package index.**
   `FakeCommandRunner` is exported from `platform/typescript/kernel-toolchains/src/index.ts`. Even if it is used only in tests today, it should move to a test-only subpath or shared testing package to avoid production-path misuse.

7. **Several adapters are exported but not execution-ready.**
   The toolchain registry correctly marks Kubernetes, Helm, Terraform, Xcode iOS, Gradle Android, Playwright, and Vitest as partial/planning-only or declared-only. The public package exports should reinforce this and prevent consumers from accidentally treating them as executable.

8. **Artifact policy validation has a no-artifact blind spot.**
   `validateArtifactPolicy` reports compliance when `manifest.artifacts` is empty unless a separate expected-artifact validation is called. In required build/package contexts, an empty manifest must fail closed.

9. **Data Cloud and YAPPC are platform-provider products, not ordinary lifecycle products yet.**
   Their registry entries have implemented surfaces, but conformance flags are false and lifecycle readiness requires platform-provider/bootstrap separation work. They must not be presented as currently executable lifecycle products.

10. **Dependency versioning is inconsistent.**
    The root workspace uses a catalog, but several TypeScript packages pin local dependency versions such as `zod`, `typescript`, `eslint`, and Vitest. This creates drift against the workspace-level governance model.

---

## B. Current-State Classification

| Capability | Current state | Evidence / reason |
|---|---|---|
| Root governance checks | Existing but partial | Many scripts exist, but audit still finds UI/API/adapter gaps and hardcoded package versions. |
| Canonical product registry | Existing and executable | Registry drives pnpm workspace and Gradle generated includes. |
| Digital Marketing lifecycle pilot | Existing and closest to executable | Registry marks lifecycle enabled/ready and `kernel-product.yaml` is detailed. Runtime commands were not executed in this audit. |
| PHR lifecycle | Existing but partial / planned | Registry has lifecycle config and gates, but lifecycle disabled. |
| Finance lifecycle | Existing but partial / planned | Registry has large module graph and lifecycle metadata, but lifecycle disabled. |
| FlashIt lifecycle | Existing but partial / planned | Mobile adapters and mobile artifact contracts are missing/disabled. |
| Data Cloud platform-provider lifecycle | Target / partial | Surfaces and modules exist, but manifest/conformance false and provider-mode work remains. |
| YAPPC platform-provider lifecycle | Target / partial | Surfaces/modules exist, but manifest/conformance false and creator/Kernel boundary still needs enforcement. |
| Studio shell | Existing but partial | Navigation and routes exist; several routes are degraded/blocked/empty. |
| Studio Lifecycle UI | Existing but partial | UI exists but product/env/provider/approval flows are incomplete. |
| Kernel lifecycle planning | Existing but partial | Planner exists; strict config validation and structured fallback diagnostics are missing. |
| Kernel lifecycle execution | Existing but partial | Executor exists with gates/approvals/artifacts/manifests; runtime command validation must be proven with E2E. |
| Toolchain adapters | Mixed | Gradle, pnpm, Docker, Compose are execution-ready; several others are planning-only/declared. |
| Artifact manifests | Existing but partial | Artifact trust/signature/SBOM/attestation fields exist; policy must fail closed for required empty manifests. |
| Data Cloud bridge | Existing but partial | Java bridge is real; runtime truth provider integration and provider context still need full end-to-end proof. |
| Agentic product development | Declared / partial | AEP modules and checks exist; Kernel-mediated agent lifecycle execution still needs end-to-end proof. |
| Future product shape matrix | Existing but partial | Registry has shape readiness reason codes; products must remain disabled until required adapters/gates/manifests are executable. |

---

## C. Top 10 Fixes

1. Make Studio Lifecycle product, environment, phase, and provider controls fully registry/API-driven.
2. Pass `providerMode`, `environment`, and selected product ID from `StudioLifecycleDataContext` into all plan/execute calls.
3. Replace silent manifest `.catch(() => undefined)` handling with typed partial-manifest/degraded state and visible diagnostics.
4. Implement real approval queue/read/decision flow in Studio instead of the static “no pending approvals” panel.
5. Add strict schema validation for `kernel-product.yaml`, lifecycle profiles, and toolchain registry before planning.
6. Move `FakeCommandRunner` out of the production public package export path.
7. Enforce artifact policy against expected artifacts so empty required manifests fail closed.
8. Implement Data Cloud-backed Kernel providers and prove bootstrap mode remains independent.
9. Create a Digital Marketing end-to-end lifecycle smoke/e2e proof: validate → test → build → package → deploy → verify.
10. Normalize TypeScript package dependencies to workspace/catalog policy and remove local hardcoded version drift.

---

## D. Journey-by-Journey Findings

### Journey 1 — Product Ideation to ProductUnitIntent

Current state: **Existing but partial**

What works:
- `KernelLifecycleService.applyProductUnitIntent` validates intent, supports preview/apply, rejects invalid provider mode, fails when the registry provider cannot apply ProductUnitIntent, emits lifecycle events, and records runtime truth/provenance when providers exist.

Gaps:
- Studio/YAPPC visible handoff is not yet proven end-to-end.
- Product selector and route state in Studio are not enough to support a real imported/generated ProductUnit.
- YAPPC remains a platform-provider product with manifest/conformance false in the registry.
- Data Cloud semantic graph/provenance/memory storage for generated intent evidence is not proven.

Required fixes:
- Add an explicit ProductUnitIntent export API/route in YAPPC.
- Add an apply/preview ProductUnitIntent UI in Studio.
- Add contract tests for ProductUnitIntent preview/apply in bootstrap and platform modes.
- Add Data Cloud provenance/memory recording test for intent application.

### Journey 2 — Direct Product Development Kernel Usage

Current state: **Existing but partial**

What works:
- Kernel can list/get ProductUnits, create plans, execute plans if an executor is configured, write lifecycle manifests, store pointers, emit events, and record runtime truth/provenance.
- Digital Marketing is enabled as the pilot.
- Studio has a Lifecycle page with phase, environment, provider mode, dry-run, run list, manifest panels, and failure diagnostics.

Gaps:
- Product selector is hardcoded/no-op.
- Environment options are not registry-derived.
- Provider mode state is not passed from data context into lifecycle client calls.
- Approval queue is static.
- Manifest rendering is raw JSON rather than domain-specific panels.
- Deployments route is marked blocked, so lifecycle delivery is not fully customer-ready.

Required fixes:
- Make LifecyclePage consume ProductUnit lifecycle metadata from API.
- Replace hardcoded environment constants with product config environments.
- Surface gates, artifacts, deployments, approvals, and verify health using shared domain components.

### Journey 3 — Agentic Product Development

Current state: **Declared / partial**

What works:
- AEP/Data Cloud Action Plane modules exist in registry.
- Root scripts include `check:agentic-lifecycle-action-contracts`.
- Kernel contracts and service layer include agentic lifecycle and provider-mode concepts.

Gaps:
- No proven agentic UI flow in Studio.
- No visible end-to-end proof that agents must invoke Kernel lifecycle action contracts instead of raw Gradle/pnpm/Docker.
- Mastery, risk, approval, verification, and trace ledger integration are not proven end-to-end.

Required fixes:
- Add `AgentLifecycleActionRequest` contract tests.
- Add agent tool-catalog enforcement.
- Add Action Plane → Kernel → Data Cloud evidence integration tests.
- Add Studio agent proposal/review/approval UX.

### Journey 4 — Digital Marketing Lifecycle Pilot

Current state: **Closest to executable**

What works:
- Registry marks Digital Marketing as lifecycle enabled and ready.
- `kernel-product.yaml` defines manifests, plugins, policy packs, local environment, surfaces, phases, approvals, artifacts, gates, Docker packaging, Compose deployment, provider modes, retention, and verify health.
- Gradle and pnpm adapters are execution-ready in the toolchain registry.
- Docker Buildx and Compose Local adapters are execution-ready in the toolchain registry.

Gaps:
- Runtime execution was not run in this audit.
- Studio still does not honor Digital Marketing’s local-only environment model.
- Approval handling must become real before deploy/promote/rollback are production-safe.
- Manifest UI is raw JSON and does not yet provide customer-grade explanation.

Required fixes:
- Add a no-mock lifecycle smoke suite for Digital Marketing.
- Assert generated manifests against schema and registry.
- Add Playwright Studio lifecycle pilot test.
- Add approval provider fixture/integration test and UI.

### Journey 5 — Artifact Intelligence

Current state: **Existing but partial / target integration**

What works:
- YAPPC has module graph for AI, agents, scaffold, knowledge graph, refactorer, and kernel bridge.
- Registry explicitly references artifact-intelligence evidence contracts as required for YAPPC readiness.
- Kernel artifact contracts model artifact references, manifests, trust, signatures, SBOM, attestations, retention, and deployment links.

Gaps:
- YAPPC artifact compiler/decompiler output is not proven as Data Cloud provenance/graph evidence.
- Kernel consumption of semantic artifact references is not yet proven.
- Studio residual island/risk/recommendation panels are not proven.

Required fixes:
- Define and enforce `ArtifactGraphSummary`, `ProductShapeEvidence`, `ResidualIslandReport`, and `RiskHotspotReport`.
- Store artifact intelligence evidence in Data Cloud.
- Keep Kernel consuming references only, not YAPPC internals.
- Add Studio/YAPPC visualization for semantic evidence.

### Journey 6 — Data Cloud Foundation

Current state: **Existing but partial**

What works:
- Kernel service supports bootstrap mode and platform mode.
- Platform mode validates required Data Cloud-backed providers such as events, artifacts, health, approvals, provenance, memory, and runtime truth.
- Data Cloud has broad plane/module coverage.
- Data Cloud bridge implementation has authorization, audit, health, retry, Promise wrapping, and tests.

Gaps:
- Data Cloud registry conformance is false for manifest, observability, security, data access, bridge, agent definitions, mastery bindings, evaluation packs, and runtime module.
- Data Cloud lifecycle readiness explicitly requires bootstrap/platform separation and runtime truth provider.
- The bridge is Java-side and does not prove complete TypeScript Kernel provider context integration.

Required fixes:
- Implement Data Cloud-backed TypeScript provider context for Kernel lifecycle.
- Prove Kernel can bootstrap/build/deploy Data Cloud without depending on Data Cloud providers.
- Add provider-mode contract tests and runtime truth evidence tests.

### Journey 7 — Future Product Shape Readiness

Current state: **Planned / partial**

Findings:
- PHR, Finance, FlashIt, TutorPutor, Audio-Video, DCMAAR, Aura, Data Cloud, and YAPPC have useful registry entries and readiness reason codes.
- They must remain disabled or partial until required adapters, gates, manifests, and product-specific evidence are executable.
- FlashIt must not enable mobile lifecycle until Xcode iOS and Gradle Android adapters are execution-ready and mobile IPA/AAB artifacts exist.
- Finance must not enable lifecycle until regulatory gates, promotion approval, multi-module build validation, and portal/operator/SDK adapters are proven.
- PHR must not enable lifecycle until consent, PII classification, audit evidence, FHIR validation, and data sovereignty gates are proven.

---

## E. Capability Ownership Matrix

| Capability | Correct owner | Current location | Problem | Required move/fix | Tests |
|---|---|---|---|---|---|
| ProductUnit contracts | Kernel | `platform/typescript/kernel-product-contracts` | Correct location | Harden schemas and API compatibility | Contract tests |
| Lifecycle planning/execution | Kernel | `platform/typescript/kernel-lifecycle` | YAML boundary too weak; fallback diagnostics weak | Add strict schema validation and structured diagnostics | Planner/service tests |
| Toolchain adapters | Kernel Toolchains | `platform/typescript/kernel-toolchains` | Fake runner exported; planning-only adapters public | Move fake runner to test subpath; enforce readiness gating | Adapter contract tests |
| Artifact manifests | Kernel Artifacts | `platform/typescript/kernel-artifacts` | Empty required manifest can pass artifact policy | Fail closed when expected artifacts are required | Artifact policy tests |
| Deployment manifests | Kernel Deployment | `platform/typescript/kernel-deployment` | Needs DM E2E proof | Validate deploy/verify manifests from Compose Local | Deployment contract tests |
| Digital Marketing lifecycle | Product + Kernel bridge | `products/digital-marketing/**` | Good pilot; UI not aligned | Add e2e smoke and Studio flow | Gradle/pnpm/docker/compose smoke |
| Data Cloud provider bridge | Data Cloud | `products/data-cloud/extensions/kernel-bridge` | Java bridge exists; full provider-mode proof missing | Implement TS provider context and runtime truth tests | Provider-mode integration tests |
| YAPPC artifact intelligence | YAPPC | `products/yappc/**`; contracts target in Kernel | Integration not proven | Publish evidence contracts; store in Data Cloud | Contract + graph tests |
| Studio shell | Studio/shared | `platform/typescript/ghatana-studio` | Good skeleton; hardcoded gaps | Make registry/API-driven and i18n complete | Vitest + Playwright |
| Product shape readiness | Platform Coherence | `config/canonical-product-registry.json` | Good metadata; needs automated enforcement | Keep disabled products disabled until checks pass | Product-shape matrix checks |

---

## F. File-by-File Implementation Plan

### Workstream 1 — Ghatana Studio UI/UX and API contracts

#### `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx`
- Issue: Hardcodes only Digital Marketing; product selector is no-op; environments are static and include values not supported by Digital Marketing.
- Change: Load ProductUnits and lifecycle metadata from context/API; use selected product ID; derive supported environments from registry/config; show unsupported phase/env as blocked with reason codes.
- Add: Real approval queue, approval decision actions, manifest-specific panels, gate/artifact/deployment health cards.
- Tests: Vitest route interaction tests; Playwright Studio lifecycle pilot flow.
- Validation: `pnpm --dir platform/typescript/ghatana-studio test`, `pnpm check:studio-kernel-api`.

#### `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx`
- Issue: Selected provider mode is stored but not passed into create/execute calls; manifest load errors are swallowed.
- Change: Pass `selectedProviderMode`, `selectedEnvironment`, and selected product ID into lifecycle client calls. Replace `.catch(() => undefined)` with typed manifest load result: `loaded | missing | corrupt | unauthorized | unavailable`.
- Tests: Data context tests for provider-mode propagation and manifest failure states.
- Validation: `pnpm --dir platform/typescript/ghatana-studio test`.

#### `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`
- Issue: Client supports providerMode, but schemas omit some service reason codes and API compatibility needs server-backed proof.
- Change: Expand error/failure codes; add scope query support where backend supports it; add typed approval listing when API exists.
- Tests: API client schema tests and contract fixtures.
- Validation: `pnpm check:studio-kernel-api`.

#### `platform/typescript/ghatana-studio/src/App.tsx`
- Issue: Some strings/config values are hardcoded (`Retry Studio`, docs URL, version).
- Change: Move to i18n/config; route error logging should use structured app logger instead of raw `console.error`.
- Tests: i18n/a11y navigation tests.
- Validation: `pnpm check:shared-product-shells`, `pnpm check:design-system-conformance`.

### Workstream 2 — Product Development Kernel backend/lifecycle/providers/plugins

#### `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`
- Issue: YAML config is parsed and cast without strict schema validation; `console.warn` fallback weakens observability.
- Change: Add strict schema validation for `kernel-product.yaml`, lifecycle profiles, and toolchain registry. Replace console warnings with injectable logger/diagnostic event. Make `allowBootstrapFallback` explicit in returned plan evidence.
- Tests: Invalid config, disabled product, missing adapter, missing platform provider, fallback disabled/enabled tests.
- Validation: `pnpm --dir platform/typescript/kernel-lifecycle test`.

#### `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`
- Issue: Strong core service exists, but manifest reads return unknown and UI-facing APIs need typed manifest errors.
- Change: Validate manifests on read, return typed errors, include provider mode/environment/scope consistently, add approval-list API support.
- Tests: Corrupt/missing manifest tests, runtimeTruth/provenance failure tests, approval provider tests.
- Validation: `pnpm check:kernel-lifecycle-truth`.

#### `platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts`
- Issue: Execution writes required manifests and fail-closes gates/approvals/artifacts, but E2E proof is needed.
- Change: Add DM golden lifecycle tests for required manifest production and approval-required behavior.
- Tests: Golden run test with fake filesystem and real adapters in dry run; integration smoke with real commands where feasible.
- Validation: `pnpm check:digital-marketing-lifecycle-pilot`.

### Workstream 3 — Toolchain Adapter & Execution Runtime

#### `platform/typescript/kernel-toolchains/src/index.ts`
- Issue: `FakeCommandRunner` is exported from the main production package API.
- Change: Move fake runner to `src/testing` and expose only through `./testing` export or `@ghatana/platform-testing`.
- Tests: Import-boundary tests that production code cannot import fake runner.
- Validation: `pnpm check:production-stubs`.

#### `platform/typescript/kernel-toolchains/src/adapters/*.ts`
- Issue: Real adapters exist for core pilot path, but OTel metrics/traces and streaming evidence are incomplete.
- Change: Add adapter telemetry port, command safety policy, structured log events, stdout/stderr stream event normalization, and per-adapter contract tests.
- Tests: Gradle/pnpm/docker/compose tests; planned adapters must fail planning/execution unless feature flag and registry readiness allow it.
- Validation: `pnpm check:toolchain-adapter-contracts`.

#### `config/toolchain-adapter-registry.json`
- Issue: Correctly marks many adapters partial/planned, but package exports must reflect readiness.
- Change: Enforce status/readiness/safeForDefault/lifecycleEnabled consistency and block planned adapters from executable plans.
- Tests: Registry schema drift and lifecycle registry compatibility tests.
- Validation: `pnpm check:toolchain-adapter-registry-schema`.

### Workstream 4 — Artifact, Supply Chain & Provenance

#### `platform/typescript/kernel-artifacts/src/domain/ArtifactManifest.ts`
- Issue: Rich manifest fields exist, but generated artifacts default `found: true`.
- Change: Separate discovered artifact entries from expected artifact declarations; avoid marking artifacts found without filesystem/provider evidence.
- Tests: Manifest generation tests for discovered vs expected artifacts.
- Validation: `pnpm check:product-artifact-contracts`.

#### `platform/typescript/kernel-artifacts/src/validator/ProductArtifactValidator.ts`
- Issue: `validateArtifactPolicy` can pass an empty manifest when required artifacts are expected.
- Change: Add policy field such as `minArtifactCount` or require callers to pass expected declarations; fail closed when required expected artifacts are missing.
- Tests: Empty manifest with signature/SBOM/digest policy must fail.
- Validation: `pnpm --dir platform/typescript/kernel-artifacts test`.

### Workstream 5 — Data Cloud foundation providers/runtime truth/memory

#### `products/data-cloud/extensions/kernel-bridge/build.gradle.kts`
- Issue: Group is `com.ghatana.platform.shared-services`, which blurs ownership for a product-owned bridge.
- Change: Rename group to product-owned namespace such as `com.ghatana.products.datacloud` or repo-standard product group.
- Tests: Gradle module check.
- Validation: `./gradlew :products:data-cloud:extensions:kernel-bridge:check`.

#### `products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudKernelAdapterImpl.java`
- Issue: Bridge is strong, but tenant scoping depends on caller-provided dataset IDs and full provider-mode runtime truth integration is not yet proven.
- Change: Add dataset scope validation, metrics/traces per operation, provider IDs, Testcontainers or integration proof against a real store.
- Tests: Tenant spoofing tests, retry/timeout tests, provider integration tests.
- Validation: `pnpm check:data-cloud-platform-providers`, `pnpm check:data-access-contract`.

### Workstream 6 — YAPPC creator/artifact intelligence/visibility

#### `products/yappc/kernel-bridge/**`
- Issue: YAPPC is a platform-provider product with conformance false; Kernel must consume only ProductUnitIntent and semantic evidence references.
- Change: Add explicit bridge contracts for ProductUnitIntent export and artifact intelligence evidence publication.
- Tests: Boundary tests that Kernel does not import YAPPC internals.
- Validation: `pnpm check:yappc-product-unit-intent-handoff`, `pnpm check:yappc-artifact-intelligence-boundary`.

#### `platform/typescript/kernel-product-contracts/src/artifact-intelligence/**`
- Issue: Artifact evidence contracts are required for Journey 5.
- Change: Stabilize `SemanticArtifactReference`, `ArtifactGraphSummary`, `ProductShapeEvidence`, `ResidualIslandReport`, and `RiskHotspotReport`.
- Tests: Schema compatibility tests and fixture-based generated evidence tests.
- Validation: `pnpm --dir platform/typescript/kernel-product-contracts test`.

### Workstream 7 — Digital Marketing pilot

#### `products/digital-marketing/kernel-product.yaml`
- Issue: Strong config exists; must be proven with commands and Studio.
- Change: Add missing explicit `verify` phase if profile/planner expects it consistently; ensure approval requirements work in local deploy; ensure all required gates have executable providers.
- Tests: End-to-end validate/test/build/package/deploy/verify smoke.
- Validation: `pnpm check:digital-marketing-lifecycle-pilot`.

#### `products/digital-marketing/dm-kernel-bridge/**`
- Issue: Registry claims bridge true with adapter/tests. Keep it the pilot bridge.
- Change: Add lifecycle evidence integration tests for Digital Marketing bridge.
- Tests: Adapter tests, retry/DLQ tests, lifecycle bridge smoke.
- Validation: `./gradlew :products:digital-marketing:dm-kernel-bridge:check`.

### Workstream 8 — CI/CD, checks, docs cleanup

#### `package.json`
- Issue: Many checks exist, but individual package versions drift from catalog in package-local manifests.
- Change: Add check that all workspace packages use `catalog:` for approved shared dependencies or a documented exception.
- Tests: dependency metadata check.
- Validation: `pnpm check:platform-package-governance`.

#### `platform/typescript/*/package.json`
- Issue: Several packages hardcode versions for zod/typescript/eslint/vitest while root catalog exists.
- Change: Convert to `catalog:` where appropriate.
- Tests: package governance test.
- Validation: `pnpm check:deprecated-packages`, `pnpm check:platform-package-governance`.

#### `docs/**`
- Issue: Need prevent target-state claims being treated as current implementation.
- Change: Add current-state labels to docs and fail docs with unclassified implementation claims.
- Tests: doc-claims evidence check.
- Validation: `pnpm check:current-state-claims`, `pnpm check:doc-claims-evidence`.

---

## G. Release Plan

### Release 0 — Unified shell, terminology, navigation, and core checks
Goal: Make Studio honest, coherent, and registry-driven.

Exit criteria:
- Studio nav uses ownership and status vocabulary.
- Lifecycle page product/env/phase/provider controls are API-driven.
- No hardcoded unsupported environments.
- Manifest failures are visible.

Validation:
```bash
pnpm check:studio-kernel-api
pnpm check:shared-product-shells
pnpm check:design-system-conformance
pnpm --dir platform/typescript/ghatana-studio test
```

### Release 1 — Digital Marketing lifecycle pilot E2E
Goal: Prove Digital Marketing validate → test → build → package → deploy → verify.

Exit criteria:
- All required manifests are generated and schema-valid.
- Approvals block deploy/promote/rollback when required.
- Artifacts fail closed when missing.
- Studio displays run, gates, artifacts, deployment, verify health, and failures.

Validation:
```bash
pnpm build:kernel-lifecycle-platform
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:kernel-lifecycle-truth
pnpm check:product-artifact-contracts
pnpm check:product-deployment-contracts
```

### Release 2 — Agentic development support
Goal: Route agentic product-development through Kernel contracts.

Exit criteria:
- Agents cannot run raw lifecycle commands directly.
- `AgentLifecycleActionRequest` is enforced.
- Risk/mastery/approval/evidence are visible in Studio.

Validation:
```bash
pnpm check:agentic-lifecycle-action-contracts
pnpm check:yappc-product-unit-intent-handoff
pnpm check:data-cloud-platform-providers
```

### Release 3 — Data Cloud platform-mode providers
Goal: Implement Data Cloud-backed providers while preserving bootstrap independence.

Exit criteria:
- Kernel bootstrap can build/deploy Data Cloud without Data Cloud providers.
- Platform mode requires Data Cloud-backed events/artifacts/health/approval/provenance/memory/runtimeTruth.
- Runtime truth and provenance are queryable.

Validation:
```bash
pnpm check:kernel-provider-mode
pnpm check:data-cloud-platform-providers
pnpm check:data-access-contract
./gradlew :products:data-cloud:extensions:kernel-bridge:check
```

### Release 4 — Artifact intelligence integration
Goal: Connect YAPPC semantic artifact evidence to Data Cloud and Kernel references.

Exit criteria:
- YAPPC emits semantic evidence contracts.
- Data Cloud stores provenance/graph/memory.
- Kernel consumes references only.
- Studio visualizes residual islands, risks, and recommendations.

Validation:
```bash
pnpm check:yappc-artifact-intelligence-boundary
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm check:kernel-boundaries
```

### Release 5 — Product shape expansion readiness
Goal: Enable future products only when their required gates/adapters/manifests are executable.

Exit criteria:
- PHR, Finance, FlashIt, TutorPutor, DCMAAR, Audio-Video, Aura remain disabled until checks pass.
- Product shape matrix is authoritative.
- Registry statuses match actual executable readiness.

Validation:
```bash
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:product-registry-drift
```

---

## H. Validation Command Suite

Run this after implementation work, not as proof of this static audit:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm build:platform
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-platform-lifecycle
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:design-system-conformance
pnpm check:shared-product-shells
pnpm check:shared-layout-primitives
pnpm check:shared-ui-state-coverage
pnpm check:production-readiness
pnpm check:secret-default-credentials
pnpm check:observability-conformance
pnpm check:data-access-contract
pnpm check:kernel-boundaries
pnpm check:kernel-provider-mode
pnpm check:data-cloud-platform-providers
pnpm check:toolchain-adapter-contracts
pnpm check:toolchain-adapter-registry-schema
pnpm check:product-artifact-contracts
pnpm check:product-deployment-contracts
pnpm check:product-environment-contracts
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
./gradlew build
./gradlew check
```

Targeted module commands:
```bash
pnpm --dir platform/typescript/kernel-product-contracts build
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm --dir platform/typescript/kernel-lifecycle build
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm --dir platform/typescript/kernel-toolchains build
pnpm --dir platform/typescript/kernel-toolchains test
pnpm --dir platform/typescript/kernel-artifacts build
pnpm --dir platform/typescript/kernel-artifacts test
pnpm --dir platform/typescript/ghatana-studio build
pnpm --dir platform/typescript/ghatana-studio test
./gradlew :products:digital-marketing:dm-kernel-bridge:check
./gradlew :products:data-cloud:extensions:kernel-bridge:check
```
