# Ghatana Implementation Plan A — Phases 1–2
## Governance Truth + Product Development Kernel Foundation

**Target repository:** `samujjwal/ghatana`  
**Target commit:** `37a7b1f815e726a37276ed4c1933b7d47cd36d56`  
**Commit label observed:** `kernel - 5-15-1`  
**Scope:** Execute Phase 1 and Phase 2 from `ghatana_domain_workstream_map.md`.

This plan is intentionally self-contained. It can be assigned to an implementation agent/team without requiring the second plan. It must always be executed against the target commit snapshot, not against a diff or previous audit output.

---

## 0. Non-negotiable execution rules

1. Work from the repository state at commit `37a7b1f815e726a37276ed4c1933b7d47cd36d56`.
2. Do not invent new architecture. Extend current repo conventions.
3. Reuse existing modules before creating new ones.
4. Do not add compatibility shims. Use fix-forward migrations only.
5. Do not create product-specific platform code.
6. Do not enable PHR, Finance, FlashIt, Data Cloud, or YAPPC lifecycle execution unless all required gates and evidence are validated.
7. Treat Digital Marketing as the executable lifecycle pilot.
8. Every change must include validation: unit, integration, contract, schema, boundary, and regression checks as appropriate.
9. No TODO/FIXME in production code.
10. No fake success, no stubs in production-critical paths, no test theater.
11. TypeScript must remain strict and fully typed. Java public APIs must include required `@doc.*` tags.
12. Every new or changed check must be deterministic, fast enough for CI, and must fail with actionable reason codes.

---

## 1. Current-state anchors at the target commit

Before editing, validate these anchors:

```bash
git checkout 37a7b1f815e726a37276ed4c1933b7d47cd36d56
git rev-parse HEAD
node --version
pnpm --version
java -version
```

Expected structural anchors:

- Root `package.json` already has scripts for `check:domain-registry`, `check:domain-boundaries`, `check:deprecated-imports`, `check:current-state-claims`, `check:duplication-exceptions`, `check:kernel-boundaries`, `check:product-registry`, `check:product-registry-artifacts`, `check:kernel-platform-lifecycle`, and `check:kernel-product-boundary-audit`.
- Root `settings.gradle.kts` includes platform Java modules, platform contracts, platform-kernel modules, platform plugins, generated product modules, bridge modules, shared services, and integration tests.
- `config/domain-registry.json` already exists and defines the major domains with classifications and required checks.
- `config/canonical-product-registry.json` is the product truth source.
- `config/generated/settings-gradle-includes.kts` and `pnpm-workspace.yaml` are generated from the canonical product registry.
- `platform/typescript/kernel-product-contracts`, `kernel-lifecycle`, `kernel-providers`, `kernel-toolchains`, `kernel-artifacts`, `kernel-deployment`, and `kernel-release` exist.

Run baseline checks before edits:

```bash
pnpm install --frozen-lockfile
pnpm check:domain-registry
pnpm check:domain-boundaries
pnpm check:deprecated-imports
pnpm check:current-state-claims
pnpm check:duplication-exceptions
pnpm check:product-registry
pnpm check:product-registry-artifacts
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-platform-lifecycle
./gradlew help
```

Record any existing failures as baseline evidence. Do not hide them by weakening checks.

---

# Phase 1 — Establish Governance Truth

## Phase 1 goal

Make the repository self-describing and self-checking so every domain, product, package, lifecycle status, boundary, and target/current-state claim is governed by executable checks.

This phase should not add product features. It creates the guardrails that prevent drift.

---

## Workstream 1.1 — Harden `config/domain-registry.json`

### Files to inspect first

- `config/domain-registry.json`
- `scripts/validate-domain-registry.mjs`
- `scripts/check-domain-boundaries.mjs`
- `scripts/check-deprecated-imports.mjs`
- `scripts/check-current-state-claims.mjs`
- `scripts/check-duplication-exceptions.mjs`
- `docs/architecture/DOMAIN_WORKSTREAM_MAP.md`
- `.github/copilot-instructions.md`
- `platform/typescript/LIBRARY_GOVERNANCE.md`
- `config/canonical-product-registry.json`

### Required implementation tasks

1. Validate that every domain in `ghatana_domain_workstream_map.md` exists in `config/domain-registry.json`.
2. Add missing domains only if absent: platform coherence/governance, Kernel lifecycle, toolchain runtime, artifacts/provenance, deployment/release, Data Cloud runtime truth, AEP/agent governance, semantic artifact intelligence, canvas/context, UI builder, design-system generator, Studio, product packs, event/streaming, security/privacy/policy, observability/health, testing/quality gates.
3. Ensure every domain entry has `id`, `name`, `ownerLayer`, `classification`, `primaryLocations`, `secondaryLocations`, `allowedConsumers`, `forbiddenDependencies`, `requiredChecks`, `productAssociations`, and `currentStateEvidence`.
4. Normalize `classification` to one of `existing-executable`, `existing-partial`, `declared-only`, `target-architecture`, or `anti-pattern`.
5. Add or validate `boundaryPolicy` per domain if the validator supports it. If not, extend the validator first. Recommended fields: `mayImport`, `mustNotImport`, `mayOwn`, `mustNotOwn`, `allowedOutputContracts`.
6. Add or validate `sourceOfTruth` per domain, such as `config/canonical-product-registry.json` for product domains and `platform/typescript/kernel-product-contracts` for Kernel contracts.
7. Add `independentExecutionChecks` so each domain can be validated without running all products.
8. Add `fullRegressionChecks` for CI/nightly or final merge validation.
9. Add `reasonCodes` for non-executable classifications.
10. Reject domain entries that only point to docs and no executable checks unless classified as `declared-only` or `target-architecture`.

### Validation

```bash
pnpm check:domain-registry
node scripts/validate-domain-registry.mjs
```

Add or update tests in the existing scripts test convention. Required cases:

1. Fails when a domain has no `id`.
2. Fails when `classification` is outside allowed values.
3. Fails when `primaryLocations` is empty.
4. Fails when `requiredChecks` is empty.
5. Fails when a location does not exist unless marked target/declared.
6. Fails when `productAssociations` reference unknown products.
7. Passes for all current domain entries.

---

## Workstream 1.2 — Make documentation authority explicit

### Files to inspect first

- `.github/copilot-instructions.md`
- `docs/architecture/DOMAIN_WORKSTREAM_MAP.md`
- `docs/GOVERNANCE.md`
- `platform/typescript/LIBRARY_GOVERNANCE.md`
- `config/documentation-truth-scope.json`
- `scripts/check-doc-truth.mjs`
- `scripts/check-doc-authority.mjs` if present
- `scripts/check-current-state-claims.mjs`

### Required implementation tasks

1. Identify all documents that declare package registry, domain registry, product registry, lifecycle ownership, YAPPC ownership, Data Cloud ownership, Kernel ownership, status vocabulary, and current-state vs target-state discipline.
2. Ensure each duplicated rule has one authoritative source and all other docs link/reference it.
3. Create or update `config/documentation-authority-map.json` with `ruleId`, `authoritativeDocument`, `dependentDocuments`, `allowedDuplicateSummary`, and `forbiddenDuplicatePatterns`.
4. Extend or create `scripts/check-doc-authority.mjs`.
5. The script must fail when multiple documents define a canonical rule without referencing the authoritative source, a document claims target architecture as current executable state, or old package registries redefine `.github/copilot-instructions.md` or `platform/typescript/LIBRARY_GOVERNANCE.md`.
6. Keep or add `"check:doc-authority": "node ./scripts/check-doc-authority.mjs"` only after the script is deterministic.

### Validation

```bash
node scripts/check-doc-authority.mjs
pnpm check:current-state-claims
pnpm check:doc-truth
pnpm check:doc-claims-evidence
```

Required negative tests:

1. Reject “Kernel owns YAPPC artifact compiler implementation.”
2. Reject “Data Cloud providers are executable” if only target/partial.
3. Reject “PHR lifecycle is enabled” while registry says planned/disabled.
4. Allow short summaries that explicitly point to the authoritative document.

---

## Workstream 1.3 — Enforce platform/product boundary rules

### Files to inspect first

- `scripts/check-domain-boundaries.mjs`
- `scripts/check-kernel-boundaries.mjs`
- `scripts/check-yappc-artifact-intelligence-boundary.mjs`
- `scripts/check-data-cloud-platform-providers.mjs`
- `scripts/check-kernel-provider-mode.mjs`
- `platform/typescript/.dependency-cruiser.cjs`
- `config/domain-registry.json`
- `config/canonical-product-registry.json`

### Required implementation tasks

1. Extend `check-domain-boundaries` to load `config/domain-registry.json`.
2. Enforce: `platform/**` must not import `products/**`; `platform/typescript/kernel-*` must not import YAPPC implementation internals; `platform/typescript/kernel-*` must not import Data Cloud plane internals; product-owned bridges must live in the owning product; shared platform packages must not contain product-prefixed package names.
3. Add allowlist support only through `config/domain-boundary-exceptions.json` with `source`, `target`, `reason`, `expiry`, `owner`, and `trackingIssue`.
4. Fail on expired exceptions.
5. Add actionable report output by source domain, violating import path, owning domain, violated rule, and recommended fix.
6. Extend `check-kernel-boundaries` to distinguish contract references from implementation imports.

### Validation

```bash
pnpm check:domain-boundaries
pnpm check:kernel-boundaries
pnpm check:deprecated-imports
```

Required tests:

1. Synthetic platform-to-product import fails.
2. Kernel-to-Data-Cloud-plane import fails.
3. Kernel-to-`kernel-product-contracts` import passes.
4. Product-to-platform import passes if platform package is canonical.
5. Expired exception fails.

---

## Workstream 1.4 — Enforce package and generated registry consistency

### Files to inspect first

- `config/canonical-product-registry.json`
- `config/generated/settings-gradle-includes.kts`
- `pnpm-workspace.yaml`
- `scripts/generate-product-registry-artifacts.mjs`
- `scripts/check-product-registry-artifacts.mjs`
- `scripts/check-platform-package-governance.js`
- `platform/typescript/LIBRARY_GOVERNANCE.md`
- `.github/copilot-instructions.md`

### Required implementation tasks

1. Ensure generated Gradle includes are fully derived from the canonical product registry.
2. Ensure `pnpm-workspace.yaml` product package patterns are fully derived from the canonical product registry.
3. Ensure no manual edits are required in generated files.
4. Ensure `scripts/generate-product-registry-artifacts.mjs --check` fails on drift.
5. Ensure `platform/typescript/LIBRARY_GOVERNANCE.md` does not redefine rules that are authoritative elsewhere unless it explicitly points to the source.
6. Validate active canonical TypeScript packages have `package.json`, `README.md`, `tsconfig.json`, correct package name, and no deprecated package usage.
7. Add package ownership fields to `config/domain-registry.json` or link existing governance registry.
8. Reject `@ghatana/canvas-core`, `@ghatana/canvas-react`, `@ghatana/ui`, `@ghatana/utils`, and product-prefixed platform packages.

### Validation

```bash
pnpm check:product-registry-artifacts
pnpm check:platform-package-governance
pnpm check:deprecated-imports
pnpm check:duplicate-packages
pnpm check:product-workspace-registration
```

---

## Workstream 1.5 — Current-state and target-state claim enforcement

### Files to inspect first

- `scripts/check-current-state-claims.mjs`
- `scripts/check-doc-claims-evidence.mjs`
- `docs/architecture/DOMAIN_WORKSTREAM_MAP.md`
- `config/domain-registry.json`
- `config/canonical-product-registry.json`

### Required implementation tasks

1. Build a claim vocabulary: enabled, executable, production-ready, validated, current, implemented, partial, planned, target, declared only.
2. Map claims to registry state.
3. Fail docs that state Data Cloud provider mode is fully executable if registry says partial/false conformance, YAPPC lifecycle is executable as ordinary Kernel product, Finance/PHR/FlashIt lifecycle is enabled while registry says disabled/planned, or shared primitives are production-ready without package checks.
4. Allow claims when they include a classification such as `existing but partial`, `target architecture`, `planned`, or `validated pilot`.
5. Require evidence refs for claims of executability.
6. Add tests for false positives and false negatives.

### Validation

```bash
pnpm check:current-state-claims
pnpm check:doc-claims-evidence
```

---

## Workstream 1.6 — Duplication and cleanup enforcement

### Files to inspect first

- `scripts/check-duplication-exceptions.mjs`
- `scripts/check-cleanup-gate.mjs`
- `config/duplication-exceptions.json` if present
- `platform/typescript/LIBRARY_GOVERNANCE.md`
- `.github/copilot-instructions.md`

### Required implementation tasks

1. Create or harden `config/duplication-exceptions.json`.
2. Require each exception to include `duplicatePattern`, `canonicalOwner`, `temporaryConsumer`, `reason`, `expiry`, and `removalPlan`.
3. Fail if a duplicate has no exception.
4. Fail if an exception expired.
5. Fail if a new duplicate package name appears.
6. Fail if a product-local shared UI component duplicates canonical platform UI without exception.
7. Add cleanup reporting for deprecated files, deprecated packages, folder-only shells, stale docs, and generated files out of sync.

### Validation

```bash
pnpm check:duplication-exceptions
pnpm check:cleanup-gate
pnpm check:deprecated-imports
```

---

## Phase 1 final acceptance gate

```bash
pnpm check:domain-registry
pnpm check:domain-boundaries
pnpm check:deprecated-imports
pnpm check:current-state-claims
pnpm check:duplication-exceptions
pnpm check:doc-truth
pnpm check:doc-claims-evidence
pnpm check:product-registry
pnpm check:product-registry-artifacts
pnpm check:platform-package-governance
pnpm check:production-readiness
```

Expected result: all governance checks pass; failures include reason codes and remediation; no product status is overclaimed; no lifecycle status is enabled without registry evidence; generated workspace/Gradle artifacts are consistent with registry truth.

---

# Phase 2 — Stabilize Kernel Lifecycle Foundation

## Phase 2 goal

Make Kernel contracts, lifecycle planning/execution, toolchain selection, artifact/deployment/release contracts, and the Digital Marketing pilot coherent, typed, validated, observable, and regression-safe.

This phase does not enable new products. It strengthens the foundation and keeps non-ready products explicitly disabled.

---

## Workstream 2.1 — Harden `@ghatana/kernel-product-contracts`

### Files to inspect first

- `platform/typescript/kernel-product-contracts/package.json`
- `platform/typescript/kernel-product-contracts/src/index.ts`
- `platform/typescript/kernel-product-contracts/src/**`
- `platform/typescript/kernel-product-contracts/src/artifact-intelligence/**`
- `config/canonical-product-registry.json`
- product `kernel-product.yaml` files

### Required implementation tasks

1. Inventory all exported contract types.
2. Ensure every public contract has a TypeScript type/interface, Zod schema where external or persisted, parse/validate function for boundary inputs, discriminated unions for status/state models, schema version field, and correlation/evidence metadata where lifecycle truth is emitted.
3. Normalize and validate `ProductUnit`, `ProductUnitIntent`, `ProductUnitSurface`, `ProductUnitKind`, `LifecyclePhase`, `LifecyclePlan`, `LifecycleResult`, `LifecycleEvent`, `GateResult`, `ArtifactReference`, `DeploymentReference`, `EnvironmentReference`, `HealthSnapshot`, `AgentLifecycleActionRequest`, `SemanticArtifactReference`, `ArtifactGraphSummary`, `ProductShapeEvidence`, `DependencyGraphEvidence`, `ResidualIslandReport`, `RiskHotspotReport`, and `GeneratedChangeSetSummary`.
4. Do not import YAPPC implementation code into contracts.
5. Do not import Data Cloud plane implementation code into contracts.
6. Keep product-specific values as data, not hardcoded TypeScript enums, unless already canonical.
7. Add migration/version policy fields: `schemaVersion`, `contractVersion`, `producer`, `createdAt`, and `correlationId`.
8. Add contract test fixtures for valid Digital Marketing, planned PHR, planned Finance, planned FlashIt, platform-provider Data Cloud, and platform-provider YAPPC.
9. Add negative fixtures: product marked enabled without required lifecycle config, product using unknown toolchain adapter, product claiming mobile bundle without manifest, platform-provider treated as ordinary product lifecycle target.

### Validation

```bash
pnpm --dir platform/typescript/kernel-product-contracts typecheck
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm check:product-registry
pnpm check:product-manifest-contracts
pnpm check:agentic-lifecycle-action-contracts
```

---

## Workstream 2.2 — Validate product registry against Kernel contracts

### Files to inspect first

- `config/canonical-product-registry.json`
- `scripts/validate-product-registry.mjs`
- `scripts/check-product-registry-artifacts.mjs`
- `scripts/check-product-kind-classification.mjs`
- `scripts/check-product-manifest-contracts.mjs`
- `scripts/generate-product-registry-artifacts.mjs`
- `platform/typescript/kernel-product-contracts`

### Required implementation tasks

1. Update product registry validation to use Kernel contract schemas.
2. Validate every product has `id`, `name`, `type`, `kind`, `surfaces`, `metadata.owner`, `metadata.status`, and lifecycle status classification.
3. Validate lifecycle state: Digital Marketing may be enabled; PHR, Finance, and FlashIt remain disabled/planned unless required gates are satisfied; Data Cloud and YAPPC remain platform-provider special cases until bootstrap/provider rules pass.
4. Validate every toolchain adapter has a corresponding known adapter in `kernel-toolchains`.
5. Validate every artifact declaration maps to `kernel-artifacts`.
6. Validate every deployment target maps to `kernel-deployment`.
7. Validate every required gate maps to known policy/gate contract.
8. Fail if `lifecycle.enabled=true` but `lifecycleMigration.status !== "ready"`, required gate evidence is missing, toolchain adapters are target-only, or artifact manifests are missing.
9. Regenerate artifacts and require no diff.

### Validation

```bash
pnpm check:product-registry
pnpm check:product-kind-classification
pnpm check:product-manifest-contracts
pnpm check:product-registry-artifacts
node scripts/generate-product-registry-artifacts.mjs --check
```

---

## Workstream 2.3 — Harden `@ghatana/kernel-lifecycle`

### Files to inspect first

- `platform/typescript/kernel-lifecycle/package.json`
- `platform/typescript/kernel-lifecycle/src/**`
- `platform/typescript/kernel-providers/src/**`
- `platform/typescript/kernel-product-contracts/src/**`
- `scripts/kernel-product.mjs`
- `scripts/check-kernel-platform-lifecycle.mjs`
- `scripts/check-digital-marketing-lifecycle-pilot.mjs`

### Required implementation tasks

1. Inventory lifecycle execution flow: product resolution, registry provider resolution, surface resolution, phase planning, adapter resolution, gate planning, artifact collection, event emission, health aggregation, result writing.
2. Define/confirm phases: `dev`, `validate`, `test`, `build`, `package`, `deploy`, `verify`, `promote`, `rollback`.
3. Ensure every lifecycle plan includes product ID, kind, lifecycle profile, surfaces, phases, adapters, gates, artifacts, deployment target, environment, provider mode, and correlation ID.
4. Ensure every lifecycle result includes phase results, gate results, adapter results, artifact references, deployment references, health summary, failure reason codes, warnings, skipped phases with reason, and machine-readable status.
5. Add canonical truth writer outputs: `lifecycle-plan.json`, `lifecycle-result.json`, `gate-result-manifest.json`, `artifact-manifest.json`, `deployment-manifest.json`, `verify-health-report.json`, `rollback-manifest.json`, `lifecycle-health-snapshot.json`, `lifecycle-events.json`.
6. If some outputs are not implemented, represent them as explicit `NOT_READY`/`skipped` with reason codes, not fake success.
7. Add deterministic run IDs and idempotency checks for repeated plan generation.
8. Add error classification: registry error, product disabled, adapter missing, gate failed, artifact missing, deployment target unsupported, provider unavailable, validation failed.
9. Add tests for disabled/planned PHR, Finance, FlashIt, Data Cloud, and YAPPC.

### Validation

```bash
pnpm --dir platform/typescript/kernel-lifecycle typecheck
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm check:kernel-platform-lifecycle
pnpm check:kernel-boundaries
pnpm check:kernel-provider-mode
```

---

## Workstream 2.4 — Harden `@ghatana/kernel-toolchains`

### Files to inspect first

- `platform/typescript/kernel-toolchains/src/**`
- `config/command-registry-manifest.json`
- `scripts/check-toolchain-adapter-contracts.mjs`
- `scripts/check-toolchain-adapter-registry-schema.mjs`
- `scripts/kernel-product.mjs`

### Required implementation tasks

1. Ensure a single adapter interface exists with `id`, `displayName`, `supportedSurfaces`, `supportedPhases`, `capabilities`, `validateRequest`, `execute`, and `healthCheck`.
2. Ensure every execution request includes product ID, surface, phase, working directory, command intent, environment, correlation ID, timeout, and safety policy.
3. Ensure every result includes status, start/end time, duration, exit code, structured output references, artifact references, logs reference, and reason code.
4. Validate current adapters: `gradle-java-service`, `pnpm-vite-react`, `compose-local` if implemented, and `docker-buildx` if implemented.
5. Mark future adapters explicit `NOT_READY` if present: `xcode-ios`, `gradle-android`, `helm`, and `terraform`.
6. Add contract tests: unknown phase rejected, unsupported surface rejected, missing working directory rejected, target-only adapter returns `NOT_READY`, adapter never returns success if command fails.
7. Add command injection tests: reject shell expansion where command registry disallows it, reject unregistered command, reject command outside workspace.
8. Add structured logging hooks following local conventions.

### Validation

```bash
pnpm --dir platform/typescript/kernel-toolchains typecheck
pnpm --dir platform/typescript/kernel-toolchains test
node scripts/check-toolchain-adapter-contracts.mjs
node scripts/check-toolchain-adapter-registry-schema.mjs
pnpm check:production-stubs
```

---

## Workstream 2.5 — Harden artifacts, deployment, and release contracts

### Files to inspect first

- `platform/typescript/kernel-artifacts/src/**`
- `platform/typescript/kernel-deployment/src/**`
- `platform/typescript/kernel-release/src/**`
- `platform/typescript/kernel-providers/src/artifacts/**`
- `platform/typescript/kernel-providers/src/health/**`
- `platform/typescript/kernel-providers/src/provenance/**`
- `config/deployment-targets.json`
- `config/rollback-policies.json`
- `scripts/check-product-artifact-contracts.mjs`
- `scripts/check-product-deployment-contracts.mjs`
- `scripts/check-product-environment-contracts.mjs`

### Required implementation tasks

#### Artifact contracts

1. Define/validate artifact manifest schema with `schemaVersion`, `artifactId`, `productId`, `surface`, `phase`, `type`, `packaging`, `fingerprint`, `sourceRef`, `buildRunId`, `createdAt`, `provenance`, and `retention`.
2. Support JVM service artifact and static web bundle artifact.
3. Represent mobile bundle artifact as `NOT_READY` until adapters/manifests are real.
4. Add checksum/fingerprint validation.
5. Add artifact-to-lifecycle result linkage and artifact-to-deployment linkage placeholder contract.

#### Deployment contracts

1. Define/validate deployment manifest schema with deployment ID, product ID, environment, target, artifact refs, health check refs, rollback policy, status, and reason codes.
2. Enforce `compose-local` as current supported baseline if that is the only executable target.
3. Return explicit `NOT_READY` for unsupported deployment target.

#### Release contracts

1. Define/validate release manifest schema with release ID, product ID, source environment, target environment, artifacts, gates, approvals, rollback plan, and verification status.
2. Add promotion blocked states: missing approval, missing compliance evidence, failed health verification, missing rollback readiness.
3. Do not fake promotion success.

### Validation

```bash
pnpm --dir platform/typescript/kernel-artifacts typecheck
pnpm --dir platform/typescript/kernel-artifacts test
pnpm --dir platform/typescript/kernel-deployment typecheck
pnpm --dir platform/typescript/kernel-deployment test
pnpm --dir platform/typescript/kernel-release typecheck
pnpm --dir platform/typescript/kernel-release test
node scripts/check-product-artifact-contracts.mjs
node scripts/check-product-deployment-contracts.mjs
node scripts/check-product-environment-contracts.mjs
```

---

## Workstream 2.6 — Validate Digital Marketing as the lifecycle pilot

### Files to inspect first

- `products/digital-marketing/kernel-product.yaml`
- `products/digital-marketing/dm-domain-packs/domain-pack.json`
- `products/digital-marketing/dm-kernel-bridge/**`
- `products/digital-marketing/dm-api/**`
- `products/digital-marketing/ui/package.json`
- `scripts/kernel-product.mjs`
- `scripts/check-digital-marketing-lifecycle-pilot.mjs`
- `config/canonical-product-registry.json`

### Required implementation tasks

1. Validate Digital Marketing registry state: `lifecycleStatus=enabled`, `lifecycle.enabled=true`, `lifecycleMigration.status=ready`, readiness reason code exists.
2. Validate backend surface: path exists, Gradle module included, build/test command exists, adapter maps to `gradle-java-service`.
3. Validate web surface: package exists, build/test script exists, adapter maps to `pnpm-vite-react`.
4. Validate bridge: bridge adapter exists, bridge tests exist, no fake success, retry/DLQ behavior is tested if declared.
5. Validate lifecycle plan for build/test backend and web.
6. Validate execution for build/test backend and web.
7. Validate truth outputs: lifecycle result, artifact manifest, gate manifest, health summary.
8. Confirm failure-path tests: missing adapter fails, broken command fails, disabled product fails, missing artifact fails.
9. Do not use Digital Marketing business logic in Kernel.

### Validation

```bash
pnpm plan:build:digital-marketing-gateway
pnpm plan:test:digital-marketing-gateway
pnpm plan:build:digital-marketing-web
pnpm plan:test:digital-marketing-web
pnpm build:digital-marketing-gateway
pnpm test:digital-marketing-gateway
pnpm build:digital-marketing-web
pnpm test:digital-marketing-web
node scripts/check-digital-marketing-lifecycle-pilot.mjs
pnpm check:kernel-platform-lifecycle
```

---

## Workstream 2.7 — Keep non-ready products explicitly disabled

### Files to inspect first

- `config/canonical-product-registry.json`
- `products/phr/kernel-product.yaml`
- `products/finance/kernel-product.yaml`
- `products/flashit/kernel-product.yaml`
- `products/data-cloud/extensions/kernel-bridge/**`
- `products/yappc/kernel-bridge/**`
- `scripts/check-product-shape-capability-matrix.mjs` if present
- `scripts/check-kernel-provider-mode.mjs`
- `scripts/check-yappc-product-unit-intent-handoff.mjs`
- `scripts/check-yappc-artifact-intelligence-boundary.mjs`

### Required implementation tasks

1. Add tests proving PHR is blocked until consent, PII classification, FHIR contract validation, audit evidence, and tenant data sovereignty evidence exist.
2. Add tests proving Finance is blocked until multi-module Gradle graph validation, regulatory gates, promotion approval, and portal/operator/SDK adapters are executable.
3. Add tests proving FlashIt is blocked until mobile adapters, mobile bundle manifests, preview security, and personal data classification exist.
4. Add tests proving Data Cloud is not ordinary lifecycle product until bootstrap build, runtime truth provider, and platform provider health gate pass.
5. Add tests proving YAPPC is not ordinary lifecycle product until creator lifecycle separation, ProductUnitIntent export, and artifact intelligence evidence boundary are validated.
6. Use reason codes, not silent skips.

### Validation

```bash
pnpm check:product-registry
pnpm check:kernel-provider-mode
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:kernel-boundaries
```

---

## Phase 2 final acceptance gate

```bash
pnpm build:kernel-lifecycle-platform
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm --dir platform/typescript/kernel-providers test
pnpm --dir platform/typescript/kernel-toolchains test
pnpm --dir platform/typescript/kernel-artifacts test
pnpm --dir platform/typescript/kernel-deployment test
pnpm --dir platform/typescript/kernel-release test
pnpm check:kernel-platform-lifecycle
pnpm check:kernel-boundaries
pnpm check:product-registry
pnpm check:product-registry-artifacts
pnpm check:product-manifest-contracts
pnpm check:production-stubs
pnpm check:production-readiness
```

Optional broader gate after focused passes:

```bash
pnpm typecheck:workspace
pnpm test
./gradlew check
```

---

# Overall Plan A completion criteria

Plan A is complete only when:

1. Domain registry validates.
2. Domain boundaries validate.
3. Deprecated imports validate.
4. Current-state claims validate.
5. Duplication exceptions validate.
6. Product registry and generated artifacts are in sync.
7. Kernel contracts are strict, typed, versioned, and tested.
8. Kernel lifecycle can plan and validate Digital Marketing.
9. Digital Marketing remains the only lifecycle-enabled pilot.
10. PHR, Finance, FlashIt, Data Cloud, and YAPPC remain explicitly blocked with reason codes until their requirements are satisfied.
11. No product-specific logic leaks into Kernel.
12. No Kernel implementation imports YAPPC/Data Cloud internals.
13. All touched code has focused tests and regression gates.
14. No check is weakened to make the build pass.
