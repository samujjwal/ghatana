# Ghatana Implementation Plan — File 1 of 2

## Governance Truth + Product Development Kernel Foundation

**Target repository:** `samujjwal/ghatana`  
**Target commit:** `67db209aa454c7be48cd0ef9b411a44ead1a2ef4`  
**Execution scope:** Phase 1 and Phase 2 from `Ghatana Domain Workstream Map`  
**Independence:** This file can be executed independently from File 2. It produces governance, registry, contract, lifecycle, adapter, manifest, and Digital Marketing pilot validation foundations. It must not require shared UI/Studio/Data Cloud runtime-truth work from File 2 to pass.

---

## 0. Non-negotiable execution rules

1. Work against the **full codebase state at commit `67db209aa454c7be48cd0ef9b411a44ead1a2ef4`**, not a commit diff.
2. Before creating any new abstraction, scan existing locations first:
   - `platform/java/*`
   - `platform/typescript/*`
   - `platform/contracts`
   - relevant `products/*`
   - existing package/module READMEs
3. Follow existing module conventions. Do not introduce a different framework, naming scheme, package layout, build tool, or test runner.
4. Use **fix-forward** behavior. Do not add compatibility shims for deprecated packages or legacy imports.
5. TypeScript must remain fully typed: no `any`, no untyped function parameters, no missing return types, no unchecked external input.
6. Java public APIs must include required `@doc.*` tags and follow existing Java 21 conventions.
7. Every meaningful behavior change must include tests that import and execute real production code.
8. Do not commit `TODO`, `FIXME`, stub, demo, placeholder, unsafe, hack, or temp production paths.
9. Incomplete adapters must return explicit `NOT_READY` / disabled readiness states and must never fake success.
10. Every critical flow added in this plan must expose observable state: structured logs, reason codes, correlation IDs/events, and testable failure behavior.
11. Product Development Kernel must not import YAPPC internals or Data Cloud plane internals.
12. Products must not implement generic lifecycle runners.
13. Data Cloud-backed Kernel providers must live under `products/data-cloud/extensions/kernel-bridge` or another explicitly product-owned bridge location, not under platform packages.
14. Validate after each slice with the cheapest focused command before widening the validation scope.

---

## 1. Current codebase anchors verified at target commit

The plan assumes the following current anchors exist and must be reused rather than duplicated:

### Root / build / registry anchors

- `settings.gradle.kts`
- `config/generated/settings-gradle-includes.kts`
- `config/canonical-product-registry.json`
- `pnpm-workspace.yaml`
- `.github/copilot-instructions.md`
- `platform/typescript/LIBRARY_GOVERNANCE.md`

### Platform Java anchors

- `platform/java/core`
- `platform/java/database`
- `platform/java/http`
- `platform/java/observability`
- `platform/java/security`
- `platform/java/testing`
- `platform/java/workflow`
- `platform/java/ai-integration`
- `platform/java/governance`
- `platform/java/agent-core`
- `platform/java/domain`
- `platform/java/config`
- `platform/java/runtime`
- `platform/java/audit`
- `platform/java/cache`
- `platform/java/policy-as-code`
- `platform/java/tool-runtime`
- `platform/java/messaging`
- `platform/java/data-governance`
- `platform/java/identity`

### Kernel anchors

- `platform-kernel/kernel-core`
- `platform-kernel/kernel-persistence`
- `platform-kernel/kernel-plugin`
- `platform-kernel/kernel-testing`
- `platform-kernel/kernel-bom`
- `platform/typescript/kernel-product-contracts`
- `platform/typescript/kernel-lifecycle`
- `platform/typescript/kernel-providers`
- `platform/typescript/kernel-toolchains`
- `platform/typescript/kernel-artifacts`
- `platform/typescript/kernel-deployment`
- `platform/typescript/kernel-release`

### Platform plugin anchors

- `platform-plugins/plugin-audit-trail`
- `platform-plugins/plugin-compliance`
- `platform-plugins/plugin-consent`
- `platform-plugins/core-observability`
- `platform-plugins/plugin-fraud-detection`
- `platform-plugins/plugin-human-approval`
- `platform-plugins/plugin-ledger`
- `platform-plugins/plugin-notification`
- `platform-plugins/plugin-risk-management`

### Product anchors used by this file

- `products/digital-marketing/*` — executable lifecycle pilot
- `products/phr/*` — regulated shape validator; keep disabled until gates are valid
- `products/finance/*` — backend-heavy shape validator; keep disabled until graph/gates/adapters are valid
- `products/flashit/*` — mobile-plus-API shape validator; keep mobile disabled until adapters/artifacts are real
- `products/data-cloud/extensions/kernel-bridge` — Data Cloud-backed provider location, but this file must only define platform contracts and not implement Data Cloud runtime truth internals
- `products/yappc/kernel-bridge` — YAPPC bridge boundary; Kernel may consume references/contracts only

---

## 2. Delivery structure

Deliver this plan as small, reviewable PRs. Each PR must be independently buildable and must not mix unrelated refactors.

| PR | Phase | Theme | Must pass before merge |
|---|---:|---|---|
| PR-A1 | 1 | Domain registry and governance docs | schema tests, doc authority checks, no generated file drift |
| PR-A2 | 1 | Boundary and package governance checks | boundary tests, deprecated import scans, product/platform leak checks |
| PR-A3 | 2 | Kernel product contracts hardening | contract unit tests, schema parse tests, no breaking consumer imports |
| PR-A4 | 2 | Product registry validation and lifecycle planning | registry validation tests, Digital Marketing plan generation test |
| PR-A5 | 2 | Toolchain adapter contract and readiness model | adapter tests, disabled adapter tests, fake-success prevention tests |
| PR-A6 | 2 | Artifact/deployment/release manifest contracts | manifest schema tests, artifact-to-deployment linkage tests |
| PR-A7 | 2 | Digital Marketing executable pilot validation | product integration tests, lifecycle dry-run test, no PHR/Finance/FlashIt enablement regression |

---

# Phase 1 — Establish governance truth

## Phase 1 objective

Create an executable governance layer that makes the domain map, product registry, package registry, ownership, current-state classification, and boundary rules testable. This phase must not implement product features. It must make incorrect ownership and drift hard to introduce.

---

## P1.1 Add authoritative domain registry

### Target files

Create:

- `config/domain-registry.schema.json`
- `config/domain-registry.json`
- `scripts/check-domain-registry.mjs`
- `scripts/__tests__/check-domain-registry.test.mjs` or nearest existing script test convention

Modify if present:

- `package.json`
- `docs/architecture/DOMAIN_WORKSTREAM_MAP.md`
- `docs/architecture/ARCHITECTURE_GOVERNANCE.md`

### Prescriptive implementation

1. Inspect existing `scripts/` patterns before adding the new script.
2. Inspect existing JSON schema validation dependencies before adding new dependencies.
3. Prefer existing `zod`, JSON schema, or Node built-ins already used by repo scripts.
4. Add `config/domain-registry.schema.json` with fields:
   - `schemaVersion`
   - `generatedFromCommit`
   - `domains[]`
   - `domains[].id`
   - `domains[].name`
   - `domains[].status`: `existing-executable | existing-partial | declared-only | target-architecture | anti-pattern`
   - `domains[].ownerType`: `platform | kernel | yappc | data-cloud | product | shared-service | cross-cutting`
   - `domains[].canonicalLocations[]`
   - `domains[].allowedConsumers[]`
   - `domains[].forbiddenImports[]`
   - `domains[].authoritativeDocs[]`
   - `domains[].validationCommands[]`
   - `domains[].phaseCoverage[]`
5. Add `config/domain-registry.json` entries for all 17 domains from the Domain Workstream Map:
   - `platform-coherence-governance`
   - `product-development-kernel-lifecycle`
   - `toolchain-adapter-execution-runtime`
   - `artifact-supply-chain-provenance`
   - `deployment-environment-release-promotion`
   - `data-cloud-runtime-truth-data-fabric`
   - `aep-agent-runtime-governance`
   - `semantic-artifact-intelligence-compiler-decompiler`
   - `canvas-diagramming-multilevel-context`
   - `ui-builder-page-builder-preview`
   - `design-system-tokens-registry-generator`
   - `ghatana-studio-unified-experience`
   - `product-domain-packs-workflows`
   - `event-streaming-operational-graph`
   - `security-privacy-identity-policy-compliance`
   - `observability-health-operations`
   - `testing-verification-quality-gates`
6. Ensure each domain maps to current paths, not desired future-only paths. If a desired path does not exist, mark it `target-architecture` or `declared-only`.
7. Add `scripts/check-domain-registry.mjs` to validate:
   - schema correctness
   - every `canonicalLocations[]` path exists or is explicitly marked target-only
   - every package domain references package names that exist in `package.json` or package registry docs
   - every Gradle module domain references a module included by `settings.gradle.kts` or generated includes
   - every product domain references an entry in `config/canonical-product-registry.json`
   - no product-named domain is mapped under a platform package unless it is a product-neutral contract
8. Add package script:
   - `check:domain-registry`: `node scripts/check-domain-registry.mjs`
9. If root `package.json` lacks a scripts section compatible with this, use existing script convention rather than inventing a parallel runner.

### Acceptance criteria

- Running `node scripts/check-domain-registry.mjs` succeeds at target commit after the registry is added.
- The script fails with a clear reason if a domain references a missing path without `target-architecture` status.
- The script fails if a product-specific domain is assigned to `platform/typescript/*` as implementation owner.
- The registry explicitly classifies Data Cloud and YAPPC as platform-provider products, not ordinary lifecycle-enabled products.
- Digital Marketing is marked as the lifecycle pilot.

### Tests

- Add tests for a valid registry.
- Add tests for missing canonical location.
- Add tests for invalid status enum.
- Add tests for product-owned path under platform.
- Add tests for missing product registry entry.

### Validation commands

```bash
node scripts/check-domain-registry.mjs
pnpm -w test -- --runInBand || pnpm -w test
```

If the repo does not support a root pnpm test command, run the nearest focused script test command used by existing scripts.

---

## P1.2 Add current-state vs target-state document guard

### Target files

Create:

- `scripts/check-current-state-claims.mjs`
- `scripts/current-state-claim-rules.json`
- `scripts/__tests__/check-current-state-claims.test.mjs`

Modify:

- `package.json`
- `docs/architecture/DOMAIN_WORKSTREAM_MAP.md`
- `docs/architecture/ARCHITECTURE_GOVERNANCE.md`
- optionally `.github/workflows/*` if existing workflows already run repo checks

### Prescriptive implementation

1. Add a low-noise claim checker that scans architecture docs for phrases that imply current executable truth, such as:
   - `is implemented`
   - `is production-ready`
   - `is enabled`
   - `owns runtime truth`
   - `executes lifecycle`
   - `validated end-to-end`
2. Require nearby labels for target/partial capabilities:
   - `Existing and executable`
   - `Existing but partial`
   - `Declared only`
   - `Target architecture`
   - `Anti-pattern`
3. Add allowlist entries only for docs that already include explicit status tables.
4. The checker must fail with actionable output:
   - file path
   - line number
   - phrase matched
   - required label
   - suggested fix
5. The checker must not parse generated files unless explicitly configured.
6. Add script:
   - `check:current-state-claims`: `node scripts/check-current-state-claims.mjs`

### Acceptance criteria

- Docs can describe future target architecture only if they classify it as target/partial/declared.
- Product registry statuses remain the source of truth for product lifecycle current state.
- The checker does not block factual current descriptions of Digital Marketing lifecycle pilot if the text uses registry-backed status.

### Tests

- Test a doc with `Target architecture` passes.
- Test a doc with `is production-ready` and no status fails.
- Test generated files are ignored by default.
- Test false-positive allowlist requires a reason.

### Validation commands

```bash
node scripts/check-current-state-claims.mjs
node scripts/check-domain-registry.mjs
```

---

## P1.3 Add platform/product boundary guard

### Target files

Create:

- `scripts/check-platform-product-boundaries.mjs`
- `scripts/boundary-rules.json`
- `scripts/__tests__/check-platform-product-boundaries.test.mjs`

Modify:

- `package.json`
- `platform/typescript/.dependency-cruiser.cjs` only if existing dependency-cruiser rules should be extended rather than duplicated
- `.github/workflows/*` only using existing CI conventions

### Prescriptive implementation

1. Inspect existing dependency cruiser config and scripts.
2. Extend existing dependency-cruiser for TypeScript where possible.
3. Add a repo-wide boundary script for checks not covered by dependency-cruiser:
   - `platform/**` must not import from `products/**`
   - `platform/**` must not contain product-named implementation modules: `yappc`, `data-cloud`, `phr`, `finance`, `flashit`, `tutorputor`, `dcmaar`, unless listed as product-neutral contract names in `boundary-rules.json`
   - `platform/typescript/kernel-*` must not import `products/yappc/**` or `products/data-cloud/**`
   - Kernel may consume Data Cloud/YAPPC through bridge/provider contracts only.
   - Data Cloud provider implementation must live under `products/data-cloud/extensions/kernel-bridge` or `products/data-cloud/planes/action/kernel-bridge`.
   - YAPPC bridge implementation must live under `products/yappc/kernel-bridge`.
4. Add allowlist entries only with fields:
   - `path`
   - `ruleId`
   - `reason`
   - `owner`
   - `expiresOn` or `reviewBy`
5. The script must scan `.ts`, `.tsx`, `.java`, `.kt`, `.kts`, `.json`, `.yaml`, `.yml`, and package manifests where practical.
6. Add scripts:
   - `check:platform-product-boundaries`
   - `check:architecture-boundaries` combining domain registry, current-state claims, package checks, and boundary checks.

### Acceptance criteria

- A direct import from `platform/typescript/kernel-lifecycle` into `products/yappc` fails.
- A product-specific Java package under `platform/java` fails unless documented as product-neutral.
- Existing legal bridge modules pass.
- The check outputs a short, actionable message.

### Tests

- Fixture: platform imports product fails.
- Fixture: product imports platform passes.
- Fixture: Data Cloud bridge location passes.
- Fixture: Kernel imports bridge implementation directly fails.
- Fixture: allowlist without reason fails.

### Validation commands

```bash
node scripts/check-platform-product-boundaries.mjs
pnpm --filter @ghatana/kernel-lifecycle typecheck
pnpm --filter @ghatana/kernel-product-contracts typecheck
```

---

## P1.4 Add deprecated package and orphan module checks

### Target files

Create:

- `scripts/check-deprecated-packages.mjs`
- `scripts/check-orphan-modules.mjs`
- `scripts/__tests__/check-deprecated-packages.test.mjs`
- `scripts/__tests__/check-orphan-modules.test.mjs`

Modify:

- `package.json`
- `platform/typescript/LIBRARY_GOVERNANCE.md` only to reference authoritative registry/checks, not to redefine canonical package rules

### Prescriptive implementation

1. Use `.github/copilot-instructions.md` Section 32 as the authoritative canonical package source.
2. Check for forbidden imports:
   - `@ghatana/ui`
   - `@ghatana/utils`
   - `@ghatana/accessibility-audit`
   - `@ghatana/canvas-core`
   - `@ghatana/canvas-plugins`
   - `@ghatana/canvas-tools`
   - `@ghatana/canvas-react`
   - `@ghatana/canvas-chrome`
   - product-prefixed `@ghatana/yappc-*`, `@ghatana/dcmaar-*`
3. Check for forbidden folder-only shells under `platform/typescript/*` that contain only `index.ts` and no `package.json`.
4. Check Java modules under `platform/java/*` have `build.gradle.kts` or are explicitly listed in an allowlist with rationale.
5. Check TypeScript packages under `platform/typescript/*` have `package.json`, `tsconfig.json`, and `README.md`, except explicit target-only folders.
6. Do not delete code in this PR unless the check finds a trivial stale folder with zero consumers. Otherwise create a follow-up tracker list inside the generated report.

### Acceptance criteria

- Deprecated package names fail immediately.
- Orphan platform module folders fail unless explicitly allowlisted.
- The script generates a deterministic report path, for example `build/reports/architecture/orphan-modules.json`.

### Tests

- Fixture with deprecated import fails.
- Fixture with valid canonical import passes.
- Fixture with folder-only shell fails.
- Fixture with valid package passes.

### Validation commands

```bash
node scripts/check-deprecated-packages.mjs
node scripts/check-orphan-modules.mjs
node scripts/check-platform-product-boundaries.mjs
```

---

## P1.5 Add product registry drift guard

### Target files

Create:

- `scripts/check-product-registry-drift.mjs`
- `scripts/__tests__/check-product-registry-drift.test.mjs`

Modify:

- `scripts/generate-product-registry-artifacts.mjs` only if needed and only after inspecting it
- `package.json`
- CI workflow running generated-file checks, if present

### Prescriptive implementation

1. Treat `config/canonical-product-registry.json` as the input source.
2. Validate generated outputs:
   - `config/generated/settings-gradle-includes.kts`
   - `pnpm-workspace.yaml`
   - any generated product registry artifact already emitted by existing generator
3. The check should run generator in memory or temporary directory and compare output, not mutate the working tree by default.
4. Fail if generated includes are stale.
5. Validate every registry Gradle module is included in generated Gradle includes unless intentionally separately included in root settings.
6. Validate every `pnpmPackages[]` pattern appears in `pnpm-workspace.yaml`.
7. Validate every lifecycle-enabled product has:
   - `lifecycle.enabled: true`
   - `lifecycleConfigPath`
   - at least one surface
   - toolchain mapping for every default surface
   - artifact declaration for every required build surface
   - deployment target
   - health check
8. Validate every lifecycle-disabled product has explicit reason codes.
9. Validate Digital Marketing remains `lifecycleStatus: enabled` and PHR/Finance/FlashIt remain disabled/planned until the plan explicitly enables them through Phase 5 validation.

### Acceptance criteria

- Changing the product registry without regenerating generated files fails.
- Enabling a product without gates/reason/evidence fails.
- Missing `pnpmPackages` workspace mapping fails.

### Tests

- Fixture with stale generated Gradle include fails.
- Fixture with enabled product missing toolchain fails.
- Fixture with disabled product missing reason code fails.
- Fixture with Digital Marketing enabled passes.

### Validation commands

```bash
node scripts/check-product-registry-drift.mjs
node scripts/generate-product-registry-artifacts.mjs --check || node scripts/check-product-registry-drift.mjs
```

Use the existing generator interface if it already has a check mode; do not invent a conflicting CLI shape.

---

# Phase 2 — Stabilize Kernel lifecycle foundation

## Phase 2 objective

Make the Product Development Kernel contract/lifecycle foundation coherent and testable, using Digital Marketing as the executable pilot while keeping PHR, Finance, FlashIt, Data Cloud, and YAPPC lifecycle states honest and disabled/partial until their gates are validated.

---

## P2.1 Harden `@ghatana/kernel-product-contracts`

### Target files

Modify or create under:

- `platform/typescript/kernel-product-contracts/src/index.ts`
- `platform/typescript/kernel-product-contracts/src/product-unit/*`
- `platform/typescript/kernel-product-contracts/src/lifecycle/*`
- `platform/typescript/kernel-product-contracts/src/gates/*`
- `platform/typescript/kernel-product-contracts/src/artifacts/*`
- `platform/typescript/kernel-product-contracts/src/deployment/*`
- `platform/typescript/kernel-product-contracts/src/release/*`
- `platform/typescript/kernel-product-contracts/src/events/*`
- `platform/typescript/kernel-product-contracts/src/agentic-actions/*`
- `platform/typescript/kernel-product-contracts/src/artifact-intelligence/*`
- co-located `__tests__/*`

### Prescriptive implementation

1. Inspect existing exports and do not break public consumers.
2. Group contracts by capability; do not create vague `utils.ts` or `types.ts` dumping grounds.
3. Add or harden Zod schemas and inferred TypeScript types for:
   - `ProductUnitId`
   - `ProductUnitKind`
   - `ProductUnitSurface`
   - `ProductUnit`
   - `ProductUnitIntent`
   - `LifecyclePhase`
   - `LifecycleMode`
   - `LifecyclePlan`
   - `LifecyclePlanStep`
   - `LifecycleRunId`
   - `LifecycleRunStatus`
   - `LifecycleResult`
   - `LifecycleFailureReasonCode`
   - `GateDefinition`
   - `GateResult`
   - `GateResultManifest`
   - `LifecycleArtifactReference`
   - `ArtifactManifest`
   - `DeploymentReference`
   - `DeploymentManifest`
   - `VerifyHealthReport`
   - `ReleaseManifest`
   - `RollbackManifest`
   - `LifecycleEvent`
   - `AgentLifecycleActionRequest`
   - `AgentLifecycleActionDecision`
   - `SemanticArtifactReference`
   - `ArtifactGraphSummary`
   - `ProductShapeEvidence`
   - `DependencyGraphEvidence`
   - `ResidualIslandReport`
   - `RiskHotspotReport`
   - `GeneratedChangeSetSummary`
4. Add discriminated unions for status and action results.
5. Add `schemaVersion` to externally persisted DTOs.
6. Add `correlationId`, `tenantId`/`workspaceId` where the surrounding contract already expects scoping; if current contracts do not include these, add optional extension fields with validation and a migration note rather than silently changing all required shapes.
7. Add parse helpers that accept `unknown` and return typed objects or typed validation errors.
8. Add stable public exports from `src/index.ts`.
9. Avoid product-specific enums such as `finance-compliance` in platform contracts; product-specific gates should be string IDs validated through registry/gate metadata.

### Acceptance criteria

- Every public persisted contract has a Zod schema.
- Every external parse helper accepts `unknown`.
- Invalid lifecycle phase fails validation with actionable errors.
- Artifact intelligence contracts exist in platform contracts, but no YAPPC implementation imports exist in platform.
- Agentic lifecycle action contracts exist, but no AEP implementation import exists in platform.

### Tests

Add tests for:

- valid `ProductUnit`
- invalid missing surface path
- valid Digital Marketing lifecycle shape
- invalid enabled lifecycle missing toolchain mapping
- valid lifecycle event with correlation ID
- valid artifact intelligence reference
- invalid artifact reference without type/fingerprint
- valid agentic lifecycle action request requiring approval
- schema version required on persisted manifests

### Validation commands

```bash
pnpm --filter @ghatana/kernel-product-contracts typecheck
pnpm --filter @ghatana/kernel-product-contracts test
pnpm --filter @ghatana/kernel-product-contracts build
```

---

## P2.2 Harden product registry validation against Kernel contracts

### Target files

Modify or create under:

- `platform/typescript/kernel-product-contracts/src/product-registry/*` if contract-level only
- `platform/typescript/kernel-lifecycle/src/registry/*` if lifecycle-specific
- `scripts/check-product-registry-drift.mjs`
- `config/canonical-product-registry.json` only when adding missing reason/evidence metadata; do not enable products prematurely
- co-located tests

### Prescriptive implementation

1. Add a registry parser that loads `config/canonical-product-registry.json` as `unknown` and validates it using Kernel schemas.
2. Validate product entries:
   - `id`, `name`, `kind`, `type`
   - `gradleModules[]` references
   - `surfaces[]`
   - `pnpmPackages[]`
   - `lifecycleStatus`
   - `lifecycleMigration.status`
   - `lifecycleReadiness.reasonCodes[]` for disabled/partial products
   - `toolchain.adapters`
   - `artifacts`
   - `deployment`
   - `environments`
3. Add rule: product `lifecycle.enabled: true` requires `lifecycleStatus: enabled` and `lifecycleMigration.status: ready`.
4. Add rule: product `lifecycle.enabled: false` with toolchain/artifact declarations is allowed only if readiness reason codes exist.
5. Add rule: Data Cloud/YAPPC cannot be treated as ordinary business-product lifecycle targets while `kind: platform-provider` and manifest is null.
6. Add rule: Digital Marketing must remain executable pilot unless explicitly replaced by a new registry field `pilot: false` with rationale.
7. Add rule: mobile adapters such as `xcode-ios` and `gradle-android` are `NOT_READY` until the adapter registry marks them executable.

### Acceptance criteria

- Registry validation passes with current target commit state plus planned additions.
- Incorrectly enabling Finance without regulatory gates fails.
- Incorrectly enabling FlashIt mobile without mobile artifacts fails.
- Incorrectly enabling Data Cloud as ordinary lifecycle product fails.
- Digital Marketing enabled pilot passes.

### Tests

- Valid current registry fixture.
- Finance enabled without `regulatory-compliance` gate fails.
- FlashIt enabled without `mobile-ipa-artifact-manifest` fails.
- Data Cloud enabled without bootstrap separation fails.
- YAPPC enabled without creator/kernel separation fails.

### Validation commands

```bash
node scripts/check-product-registry-drift.mjs
pnpm --filter @ghatana/kernel-product-contracts test
pnpm --filter @ghatana/kernel-lifecycle test
```

---

## P2.3 Implement lifecycle planning as deterministic pure logic

### Target files

Modify or create under:

- `platform/typescript/kernel-lifecycle/src/index.ts`
- `platform/typescript/kernel-lifecycle/src/planning/*`
- `platform/typescript/kernel-lifecycle/src/registry/*`
- `platform/typescript/kernel-lifecycle/src/gates/*`
- `platform/typescript/kernel-lifecycle/src/results/*`
- `platform/typescript/kernel-lifecycle/src/events/*`
- `platform/typescript/kernel-lifecycle/src/__tests__/*`

### Prescriptive implementation

1. Keep lifecycle planning pure and separately testable.
2. Define a `LifecyclePlanBuilder` that accepts:
   - validated `ProductUnit`
   - requested lifecycle phase(s)
   - selected surfaces
   - environment
   - execution mode
   - gate policy inputs
3. Output a `LifecyclePlan` with deterministic step ordering.
4. Add a deterministic sort rule:
   - registry order first
   - explicit dependency order second
   - lexical fallback last
5. Add `PlanReasonCode` values:
   - `surface-selected`
   - `surface-skipped`
   - `toolchain-resolved`
   - `toolchain-not-ready`
   - `gate-required`
   - `artifact-required`
   - `deployment-target-resolved`
   - `product-lifecycle-disabled`
6. Add clear disabled behavior:
   - disabled product returns a blocked plan with reason codes, not an exception unless input is invalid
   - invalid registry entry throws validation error before planning
7. Add `DigitalMarketingLifecyclePlanTest` using actual registry fixture or a focused fixture copied from current product registry.
8. Add `FinanceDisabledLifecyclePlanTest`, `PhrDisabledLifecyclePlanTest`, and `FlashItDisabledLifecyclePlanTest` to prevent accidental enablement.
9. Add event emission plan only as contract/output; do not require Data Cloud.

### Acceptance criteria

- Digital Marketing plan includes backend-api and web surfaces for configured phases.
- Finance/PHR/FlashIt produce blocked/disabled plan with current reason codes.
- Planning is deterministic across runs.
- Planning output validates against `LifecyclePlan` schema.
- No YAPPC/Data Cloud implementation imports are introduced.

### Tests

- Plan Digital Marketing build sequential mode.
- Plan Digital Marketing dev parallel mode.
- Plan disabled Finance and assert `requires-regulatory-gates` / `requires-multi-module-build-validation` reason codes.
- Plan disabled PHR and assert consent/PII/FHIR/data sovereignty gates.
- Plan disabled FlashIt and assert mobile adapter/artifact reasons.
- Snapshot plan output after normalizing generated IDs/timestamps.

### Validation commands

```bash
pnpm --filter @ghatana/kernel-lifecycle typecheck
pnpm --filter @ghatana/kernel-lifecycle test
pnpm --filter @ghatana/kernel-lifecycle build
```

---

## P2.4 Implement lifecycle truth writer with file-backed bootstrap mode

### Target files

Modify or create under:

- `platform/typescript/kernel-lifecycle/src/truth/*`
- `platform/typescript/kernel-lifecycle/src/events/*`
- `platform/typescript/kernel-providers/src/*`
- `platform/typescript/kernel-providers/src/file-backed/*`
- `platform/typescript/kernel-providers/src/__tests__/*`

### Prescriptive implementation

1. Define a provider-neutral `LifecycleTruthWriter` interface in Kernel contracts or lifecycle package, depending on existing ownership.
2. Implement file-backed provider in `@ghatana/kernel-providers`, not in products.
3. Write canonical outputs:
   - `lifecycle-plan.json`
   - `lifecycle-result.json`
   - `gate-result-manifest.json`
   - `artifact-manifest.json`
   - `deployment-manifest.json`
   - `verify-health-report.json`
   - `rollback-manifest.json`
   - `lifecycle-health-snapshot.json`
   - `lifecycle-events.json`
4. Every output must include:
   - `schemaVersion`
   - `productUnitId`
   - `lifecycleRunId`
   - `correlationId`
   - `createdAt`
   - `sourceCommitSha` when available
   - `status`
   - `reasonCodes[]`
5. Add atomic write behavior:
   - write to temp file
   - validate output JSON
   - rename to target
6. Add explicit failure behavior if target directory is not writable.
7. Do not add Data Cloud-backed writer here. Add only provider interface/hook so File 2 can implement Data Cloud bridge.

### Acceptance criteria

- File-backed truth writer works without Data Cloud.
- Writes are schema-validated before final rename.
- Failed writes return observable, testable errors.
- No silent success on failed file writes.

### Tests

- Writes valid lifecycle plan.
- Rejects invalid result.
- Fails on read-only/nonexistent target directory with typed error.
- Writes event stream in deterministic order.
- Does not import product code.

### Validation commands

```bash
pnpm --filter @ghatana/kernel-providers typecheck
pnpm --filter @ghatana/kernel-providers test
pnpm --filter @ghatana/kernel-lifecycle test
```

---

## P2.5 Harden toolchain adapter contracts and readiness

### Target files

Modify or create under:

- `platform/typescript/kernel-toolchains/src/index.ts`
- `platform/typescript/kernel-toolchains/src/contracts/*`
- `platform/typescript/kernel-toolchains/src/registry/*`
- `platform/typescript/kernel-toolchains/src/adapters/gradle-java-service/*`
- `platform/typescript/kernel-toolchains/src/adapters/pnpm-vite-react/*`
- `platform/typescript/kernel-toolchains/src/adapters/not-ready/*`
- `platform/typescript/kernel-toolchains/src/__tests__/*`

### Prescriptive implementation

1. Define or harden:
   - `ToolchainAdapterId`
   - `ToolchainCapability`
   - `ToolchainExecutionRequest`
   - `ToolchainExecutionResult`
   - `ToolchainReadiness`
   - `ToolchainOutputKind`
   - `ToolchainFailureReasonCode`
2. Add explicit readiness states:
   - `EXECUTABLE`
   - `NOT_READY`
   - `DISABLED_BY_POLICY`
   - `UNSUPPORTED_SURFACE`
3. Register current known adapters:
   - `gradle-java-service`: executable only if implementation already exists or is implemented in this slice
   - `pnpm-vite-react`: executable only if implementation already exists or is implemented in this slice
   - `xcode-ios`: `NOT_READY`
   - `gradle-android`: `NOT_READY`
   - `docker-buildx`: `NOT_READY` until implemented
   - `compose-local`: `NOT_READY` or executable depending on existing implementation; do not claim executable without tests
4. Ensure `NOT_READY` adapters produce blocked results, not fake success.
5. Add command safety policy:
   - allowed executable
   - allowed args
   - working directory inside repo
   - environment allowlist
   - timeout
   - output size limit
6. Add output normalization:
   - stdout/stderr chunks
   - exit code
   - startedAt/finishedAt
   - durationMs
   - reason codes
   - produced artifact references if available
7. Add focused adapter tests using safe commands/fixtures. Do not require full product builds in unit tests.
8. Integration tests may exercise Digital Marketing dry-run or no-op/dry-run mode.

### Acceptance criteria

- Adapter registry rejects unknown adapter IDs.
- Mobile adapters return `NOT_READY`.
- A blocked adapter result is distinguishable from failed execution.
- Adapter output cannot be interpreted as success unless exit code/result status is valid.
- Toolchain request validation prevents path traversal outside repo.

### Tests

- Valid Gradle request parse.
- Invalid workdir path traversal fails.
- Unknown adapter ID fails.
- `xcode-ios` returns `NOT_READY`.
- `gradle-android` returns `NOT_READY`.
- Fake success string in stdout with nonzero exit code still fails.
- Timeout path returns timed-out reason code.

### Validation commands

```bash
pnpm --filter @ghatana/kernel-toolchains typecheck
pnpm --filter @ghatana/kernel-toolchains test
pnpm --filter @ghatana/kernel-toolchains build
```

---

## P2.6 Harden artifact manifest system

### Target files

Modify or create under:

- `platform/typescript/kernel-artifacts/src/index.ts`
- `platform/typescript/kernel-artifacts/src/manifest/*`
- `platform/typescript/kernel-artifacts/src/fingerprint/*`
- `platform/typescript/kernel-artifacts/src/validation/*`
- `platform/typescript/kernel-artifacts/src/__tests__/*`
- `platform/typescript/kernel-product-contracts/src/artifacts/*` if contract schemas belong there

### Prescriptive implementation

1. Define or harden artifact manifest schema for:
   - `jvm-service`
   - `static-web-bundle`
   - `container-image`
   - `mobile-bundle` as schema-only / not executable until adapters are ready
2. Add required fields:
   - `schemaVersion`
   - `artifactId`
   - `artifactType`
   - `productUnitId`
   - `surfaceType`
   - `sourceCommitSha`
   - `buildToolchainAdapterId`
   - `fingerprints[]`
   - `createdAt`
   - `provenanceRefs[]`
   - `trustState`
3. Add fingerprint utility using existing Node crypto; do not add dependency unless necessary.
4. Add artifact validation errors with reason codes.
5. Add artifact-to-deployment linkage field that `kernel-deployment` can consume.
6. Add mobile bundle manifest contracts (`ipa`, `aab`) but mark them `contract-ready` / not executable until adapters exist.

### Acceptance criteria

- Digital Marketing backend artifact manifest validates as JVM service.
- Digital Marketing web artifact manifest validates as static bundle.
- FlashIt mobile artifact schemas exist but lifecycle remains disabled.
- Invalid missing fingerprint fails.
- Artifact-to-deployment reference can be validated.

### Tests

- Valid JVM artifact manifest.
- Valid static web bundle manifest.
- Invalid missing productUnitId.
- Invalid unsupported artifact type.
- Mobile IPA/AAB schema parse tests without claiming executable lifecycle.
- Fingerprint deterministic test using fixture file.

### Validation commands

```bash
pnpm --filter @ghatana/kernel-artifacts typecheck
pnpm --filter @ghatana/kernel-artifacts test
pnpm --filter @ghatana/kernel-artifacts build
```

---

## P2.7 Harden deployment and release contracts

### Target files

Modify or create under:

- `platform/typescript/kernel-deployment/src/index.ts`
- `platform/typescript/kernel-deployment/src/plan/*`
- `platform/typescript/kernel-deployment/src/manifest/*`
- `platform/typescript/kernel-deployment/src/health/*`
- `platform/typescript/kernel-deployment/src/__tests__/*`
- `platform/typescript/kernel-release/src/index.ts`
- `platform/typescript/kernel-release/src/promotion/*`
- `platform/typescript/kernel-release/src/rollback/*`
- `platform/typescript/kernel-release/src/approval/*`
- `platform/typescript/kernel-release/src/__tests__/*`

### Prescriptive implementation

1. Define deployment target contracts:
   - `compose-local`
   - `kubernetes` as target-only/not-ready if not implemented
   - `static-preview` if already used by repo, otherwise do not invent
2. Define environment contracts:
   - `local`
   - `dev`
   - `staging`
   - `prod`
3. Define deployment manifest fields:
   - `schemaVersion`
   - `deploymentId`
   - `productUnitId`
   - `environment`
   - `artifactRefs[]`
   - `healthChecks[]`
   - `rollbackStrategy`
   - `status`
   - `reasonCodes[]`
4. Define health report fields:
   - `status`: `healthy | degraded | blocked | failed | unknown`
   - `checks[]`
   - `observedAt`
   - `correlationId`
5. Define release manifest fields:
   - `releaseId`
   - `promotionPath`
   - `approvalRefs[]`
   - `riskResult`
   - `rollbackManifestRef`
6. Add policy for prod promotion requiring approval gate by default.
7. Add tests that prevent deployment without artifact linkage.
8. Add tests that prevent release without rollback readiness.

### Acceptance criteria

- Deployment manifest validates for Digital Marketing local deployment shape.
- Release manifest refuses prod promotion without approval/risk evidence.
- Rollback manifest can point to previous artifact.
- Health report status vocabulary matches shared status vocabulary.

### Tests

- Valid compose-local deployment manifest.
- Invalid deployment with missing artifact refs.
- Valid rollback manifest.
- Invalid prod release without approval gate.
- Valid local release without prod approval where policy permits.

### Validation commands

```bash
pnpm --filter @ghatana/kernel-deployment typecheck
pnpm --filter @ghatana/kernel-deployment test
pnpm --filter @ghatana/kernel-release typecheck
pnpm --filter @ghatana/kernel-release test
```

---

## P2.8 Validate Digital Marketing as executable pilot

### Target files

Inspect and modify only if needed:

- `products/digital-marketing/kernel-product.yaml`
- `products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java`
- `products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java`
- `products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java`
- `products/digital-marketing/dm-api/*`
- `products/digital-marketing/ui/package.json`
- `platform/typescript/kernel-lifecycle/src/__tests__/digital-marketing-lifecycle.test.ts`
- `integration-tests/cross-service-workflow/*` if it already has lifecycle test conventions

### Prescriptive implementation

1. Do not change Digital Marketing business behavior unless required by lifecycle contract validation.
2. Validate Digital Marketing registry entry maps to actual Gradle and pnpm workspace modules.
3. Validate backend-api surface uses `gradle-java-service`.
4. Validate web surface uses `pnpm-vite-react`.
5. Add a lifecycle dry-run test:
   - load product registry
   - select Digital Marketing
   - build lifecycle plan for `build`
   - resolve adapters
   - produce expected artifact manifest placeholders from dry-run outputs
   - write lifecycle truth to temp directory
   - validate all generated JSON outputs
6. Add bridge tests for product-to-Kernel adapter if bridge currently exists.
7. Ensure failure path tests exist for missing product manifest, missing adapter, and failed surface.
8. Ensure lifecycle pilot does not require Data Cloud.

### Acceptance criteria

- Digital Marketing can produce a valid lifecycle dry-run result.
- The dry-run result produces valid plan, result, gate, artifact, deployment, health, rollback/event outputs where applicable.
- Product registry still marks Digital Marketing as enabled/ready.
- No other product becomes lifecycle-enabled.

### Tests

- Digital Marketing lifecycle dry-run success.
- Missing UI package path fails registry validation.
- Missing backend Gradle module fails registry validation.
- Bridge notification retry/DLQ tests still pass.
- Digital Marketing business tests still pass.

### Validation commands

```bash
./gradlew :products:digital-marketing:dm-kernel-bridge:test
./gradlew :products:digital-marketing:dm-api:test
pnpm --filter @ghatana/kernel-lifecycle test
pnpm --filter products/digital-marketing/ui test || pnpm --filter ./products/digital-marketing/ui test
```

Use actual package filter syntax available in this workspace; do not create a new package manager convention.

---

## P2.9 Preserve disabled/partial state for PHR, Finance, FlashIt, Data Cloud, and YAPPC

### Target files

Inspect and modify only if needed:

- `config/canonical-product-registry.json`
- `products/phr/kernel-product.yaml`
- `products/finance/kernel-product.yaml`
- `products/flashit/kernel-product.yaml`
- `products/data-cloud/extensions/kernel-bridge/*`
- `products/yappc/kernel-bridge/*`
- `platform/typescript/kernel-lifecycle/src/__tests__/*disabled*.test.ts`

### Prescriptive implementation

1. Add tests that assert current lifecycle readiness constraints:
   - PHR requires consent, PII classification, audit evidence, FHIR validation, tenant/data-sovereignty gate.
   - Finance requires regulatory gates, promotion approval, multi-module build validation, portal/operator/SDK adapter readiness.
   - FlashIt requires mobile adapters, preview security, personal data classification, mobile bundle artifacts.
   - Data Cloud requires platform-provider mode, bootstrap separation, runtime truth provider.
   - YAPPC requires creator lifecycle distinct from Kernel and artifact intelligence evidence contracts.
2. Do not enable any of these products in Phase 2.
3. Add blocked plan tests rather than execution tests.
4. Add reason-code coverage to avoid silent status changes.

### Acceptance criteria

- Disabled products return blocked lifecycle plans with reason codes.
- Registry drift check fails if any disabled product is enabled without corresponding gates/adapters/evidence.
- Data Cloud and YAPPC remain platform-provider products.

### Tests

- PHR disabled reason codes.
- Finance disabled reason codes.
- FlashIt disabled reason codes.
- Data Cloud platform provider blocked state.
- YAPPC creator lifecycle separation blocked state.

### Validation commands

```bash
pnpm --filter @ghatana/kernel-lifecycle test
node scripts/check-product-registry-drift.mjs
node scripts/check-domain-registry.mjs
```

---

## 3. Cross-phase validation matrix for File 1

Run these before considering File 1 complete.

### Focused checks

```bash
node scripts/check-domain-registry.mjs
node scripts/check-current-state-claims.mjs
node scripts/check-platform-product-boundaries.mjs
node scripts/check-deprecated-packages.mjs
node scripts/check-orphan-modules.mjs
node scripts/check-product-registry-drift.mjs
```

### Kernel package checks

```bash
pnpm --filter @ghatana/kernel-product-contracts typecheck
pnpm --filter @ghatana/kernel-product-contracts test
pnpm --filter @ghatana/kernel-product-contracts build

pnpm --filter @ghatana/kernel-providers typecheck
pnpm --filter @ghatana/kernel-providers test
pnpm --filter @ghatana/kernel-providers build

pnpm --filter @ghatana/kernel-lifecycle typecheck
pnpm --filter @ghatana/kernel-lifecycle test
pnpm --filter @ghatana/kernel-lifecycle build

pnpm --filter @ghatana/kernel-toolchains typecheck
pnpm --filter @ghatana/kernel-toolchains test
pnpm --filter @ghatana/kernel-toolchains build

pnpm --filter @ghatana/kernel-artifacts typecheck
pnpm --filter @ghatana/kernel-artifacts test
pnpm --filter @ghatana/kernel-artifacts build

pnpm --filter @ghatana/kernel-deployment typecheck
pnpm --filter @ghatana/kernel-deployment test
pnpm --filter @ghatana/kernel-deployment build

pnpm --filter @ghatana/kernel-release typecheck
pnpm --filter @ghatana/kernel-release test
pnpm --filter @ghatana/kernel-release build
```

### Java checks

```bash
./gradlew :platform-kernel:kernel-core:check
./gradlew :platform-kernel:kernel-plugin:check
./gradlew :platform-kernel:kernel-testing:check
./gradlew :products:digital-marketing:dm-kernel-bridge:test
./gradlew :products:digital-marketing:dm-api:test
```

### Broader no-regression checks

```bash
pnpm -w typecheck
pnpm -w test
./gradlew check
```

If root-wide checks are too expensive or fail due unrelated existing failures, capture the failure list and prove all touched modules pass focused validation. Do not claim global clean state unless global commands actually pass.

---

## 4. Regression prevention checklist

Before merging File 1 implementation:

- [ ] No product lifecycle status changed except through explicit registry rule and tests.
- [ ] Digital Marketing remains enabled and validates as lifecycle pilot.
- [ ] PHR remains disabled/planned with regulated gate reason codes.
- [ ] Finance remains disabled/planned with compliance/multi-module reason codes.
- [ ] FlashIt remains disabled/planned with mobile/preview/privacy reason codes.
- [ ] Data Cloud remains platform-provider and is not required for bootstrap Kernel execution.
- [ ] YAPPC remains platform-provider and does not own Kernel lifecycle execution.
- [ ] Platform packages do not import product code.
- [ ] Kernel packages do not import YAPPC/Data Cloud internals.
- [ ] Deprecated package imports fail checks.
- [ ] All new public contracts have schemas and tests.
- [ ] All generated lifecycle truth files validate against schemas.
- [ ] No production TODO/FIXME/stub/fake-success behavior added.
- [ ] No object-literal test theater added.
- [ ] No new dependency added without documented justification.
- [ ] No generated-file drift.

---

## 5. Definition of done for File 1

File 1 is complete when:

1. Domain governance exists as executable checks, not just documentation.
2. Product registry drift is detected automatically.
3. Current-state vs target-state claims are checked.
4. Platform/product boundary violations are checked.
5. Deprecated package imports are checked.
6. Kernel product contracts are typed, schema-validated, and tested.
7. Lifecycle planning is deterministic and validated.
8. File-backed lifecycle truth works without Data Cloud.
9. Toolchain readiness is explicit and fake success is impossible.
10. Artifact/deployment/release manifest contracts are validated.
11. Digital Marketing validates as the first executable lifecycle pilot.
12. PHR/Finance/FlashIt/Data Cloud/YAPPC remain honestly disabled/partial until their Phase 5 readiness work is complete.
