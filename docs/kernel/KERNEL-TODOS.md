# Kernel Product Lifecycle Platform Stabilization Plan

**Repository:** `samujjwal/ghatana`  
**Target commit reviewed:** `ef9d6ecbe65b967fa551b0fe6c4a94a23180ff8c`  
**Scope:** Kernel product lifecycle platform, platform plugins, product lifecycle declarations, shared TypeScript libraries, scaffolding, conformance, and Digital Marketing pilot.  
**Explicit non-goal:** Do **not** bring `yappc` or `data-cloud` into this lifecycle framework in this implementation stream.

---

## 1. Implementation North Star

Kernel should become the product developer’s end-to-end product platform.

A product developer should use:

```bash
pnpm kernel product plan digital-marketing build
pnpm kernel product dev digital-marketing
pnpm kernel product validate digital-marketing
pnpm kernel product test digital-marketing
pnpm kernel product build digital-marketing
pnpm kernel product package digital-marketing
pnpm kernel product deploy digital-marketing --env local
pnpm kernel product verify digital-marketing --env local
```

The developer should not need to know whether the underlying executor is Gradle, pnpm, Vite, Vitest, Playwright, Docker, Compose, Helm, Terraform, or something else.

Power users can override toolchains, but only through Kernel-governed adapters that still satisfy Kernel contracts.

---

## 2. Repo Instruction Constraints That Must Drive Implementation

The `.github/copilot-instructions.md` file is the governing repo-specific implementation rulebook. This plan applies the following constraints directly:

1. Reuse before creating.
2. Do not deviate from existing Ghatana repo shape.
3. Keep boundaries explicit.
4. No silent failures.
5. No unsafe defaults or hardcoded secrets.
6. Zero-warning mindset.
7. Type safety during implementation, not later.
8. Tests are part of every meaningful change.
9. Public Java APIs require `@doc.*` documentation tags.
10. Prefer existing dependencies; do not introduce overlapping stacks.
11. Make observability part of the feature.
12. Keep product-specific logic in product-owned areas.
13. Shared platform modules must stay generic and product-agnostic.
14. Do not turn shared packages into dumping grounds.
15. Favor explicit contracts and maintainability over novelty.

Immediate consequence for this work:

```text
Do not create a parallel lifecycle architecture.
Stabilize and widen the lifecycle scaffolding that already exists at this commit.
```

---

## 3. Current-State Findings at the Target Commit

The commit already contains first-pass lifecycle implementation pieces.

### 3.1 Already present

```text
config/canonical-product-registry.json
config/canonical-product-registry-schema.json
config/product-lifecycle-profiles.json
config/toolchain-adapter-registry.json
scripts/kernel-product.mjs
scripts/run-product-lifecycle.mjs
scripts/run-product-task.mjs
scripts/check-kernel-platform-lifecycle.mjs
scripts/check-product-lifecycle-contracts.mjs
platform/typescript/kernel-lifecycle
platform/typescript/kernel-toolchains
products/digital-marketing/kernel-product.yaml
products/phr/kernel-product.yaml
products/finance/kernel-product.yaml
products/flashit/kernel-product.yaml
```

### 3.2 Important current problems

#### Problem 1 — Lifecycle exists, but the implementation is split and duplicated

`kernel-product.mjs` contains its own YAML parser, registry loading, planning, execution, and command mapping. Separately, `platform/typescript/kernel-lifecycle` contains `ProductLifecyclePlanner`. Separately again, `scripts/run-product-lifecycle.mjs` imports `ProductLifecyclePlanner`.

This violates reuse and creates drift.

Required direction:

```text
scripts/kernel-product.mjs must be a thin CLI wrapper.
platform/typescript/kernel-lifecycle must own lifecycle planning/execution.
platform/typescript/kernel-toolchains must own tool-specific execution.
```

#### Problem 2 — `scripts/run-product-lifecycle.mjs` is inconsistent with the current planner

`run-product-lifecycle.mjs` calls:

```ts
new ProductLifecyclePlanner()
```

but the current `ProductLifecyclePlanner` constructor requires `repoRoot: string`.

Also, the script says execution is not implemented.

Required direction:

```text
Keep only one canonical CLI path.
Either remove run-product-lifecycle.mjs or turn it into a thin alias to kernel-product.mjs.
```

#### Problem 3 — Type definitions are duplicated

`kernel-toolchains/src/ToolchainAdapter.ts` duplicates `ProductLifecyclePhase`, `ProductSurfaceType`, and `ProductSurface`, with TODO comments saying they should be unified.

Required direction:

```text
Create a shared lifecycle contract package or move common contracts to one existing package.
Do not keep duplicated lifecycle types.
```

#### Problem 4 — Toolchain adapters still have correctness issues

Known issues:

1. `PnpmViteReactAdapter` references `context.surface.id`, but `ProductSurface` has no `id`.
2. `GradleJavaServiceAdapter` and `PnpmViteReactAdapter` generate artifact manifests internally.
3. Artifact manifest generation failures are logged as warnings and treated as non-blocking.
4. Some phase-to-task mappings return build/test tasks for unsupported lifecycle phases.
5. Output validation is too generic and does not honor product-specific expected artifacts from `kernel-product.yaml`.

Required direction:

```text
Adapters return structured outputs.
Lifecycle/artifact layer writes manifests.
Required artifact failure is fatal.
Unsupported phase must fail closed.
```

#### Problem 5 — Dependency/version drift exists in new TypeScript packages

`platform/typescript/kernel-lifecycle/package.json` currently declares local versions such as TypeScript 5.x, ESLint 8.x, Zod 3.x, and `@types/node` 20.x, while the root workspace uses newer standardized overrides/catalog patterns.

Required direction:

```text
Use workspace catalog / root-approved versions.
Do not introduce version drift.
```

#### Problem 6 — YAPPC and Data Cloud are currently partially lifecycle-annotated

The registry includes lifecycle fields for `yappc` and `data-cloud`, but the requested implementation direction says we do not bring those products into this framework.

Required direction:

```text
Explicitly exclude yappc and data-cloud from lifecycle adoption.
Remove/disable their lifecycle-specific registry fields and enforce exclusion in checks.
Keep their existing build/test scripts unchanged.
```

#### Problem 7 — Some non-Digital-Marketing `kernel-product.yaml` files are not accurate

Examples:

- `products/phr/kernel-product.yaml` refers to `products:phr:delivery:gateway`, which does not align with the registry’s current `:products:phr` and `:products:phr:launcher` modules.
- `products/finance/kernel-product.yaml` refers to delivery/worker/operator paths that do not align with the currently registered Finance modules.
- `products/flashit/kernel-product.yaml` introduces `mobile-ios` and `mobile-android` surface types, while the registry surface enum uses `mobile`.

Required direction:

```text
Digital Marketing remains enabled pilot.
PHR, Finance, and FlashIt remain planned/partial until their configs are corrected.
Lifecycle checks must distinguish enabled products from planned/partial products.
```

#### Problem 8 — Package scripts expose lifecycle partially and inconsistently

`package.json` contains lifecycle scripts for Digital Marketing, but:

- `promote:digital-marketing` passes `--from` and `--to`, while `kernel-product.mjs` does not parse those options.
- Legacy `pnpm product` still delegates only some lifecycle tasks.
- Non-lifecycle products remain on old routing, which is acceptable, but checks must make the distinction explicit.

Required direction:

```text
Make Digital Marketing lifecycle scripts correct.
Keep old product task routing for non-lifecycle products.
Do not lifecycle-migrate yappc/data-cloud.
```

#### Problem 9 — Product lifecycle docs are too thin

`docs/kernel/PRODUCT_LIFECYCLE_CONTRACT.md` exists but does not fully define phase contracts, inputs, outputs, failure policy, artifact expectations, environment expectations, gate semantics, or adapter responsibilities.

Required direction:

```text
Expand docs to be contract-grade and enforceable by schema/checks.
```

---

## 4. Product Scope Decisions

### 4.1 Lifecycle enabled now

Only:

```text
digital-marketing
```

### 4.2 Lifecycle planned, not enabled until corrected

```text
phr
finance
flashit
security-gateway
audio-video
dcmaar
tutorputor
```

These can remain in future migration scope, but they must not be treated as production-ready lifecycle products until config and adapters are correct.

### 4.3 Lifecycle explicitly excluded

```text
yappc
data-cloud
```

Do not create lifecycle config, lifecycle execution, lifecycle CI, or lifecycle migration tasks for these two in this stream.

They can continue to use existing product task scripts, Gradle, pnpm, and product-specific flows.

---

# 5. File-by-File Implementation Plan

---

## 5.1 `config/canonical-product-registry.json`

### Current issue

The registry contains lifecycle fields for many products, including `yappc` and `data-cloud`. This conflicts with the requested scope.

### Required changes

#### Digital Marketing

Keep and refine lifecycle entry.

Ensure:

```json
"lifecycleStatus": "enabled",
"lifecycleConfigPath": "products/digital-marketing/kernel-product.yaml",
"lifecycle": {
  "enabled": true
}
```

Ensure Digital Marketing lifecycle toolchain only declares adapters currently implemented and safe:

```json
"toolchain": {
  "profile": "standard-web-api-product",
  "adapters": {
    "backend-api": "gradle-java-service",
    "web": "pnpm-vite-react"
  }
}
```

Keep deployment as local only for now:

```json
"deployment": {
  "targets": ["compose-local"],
  "defaultEnvironment": "local",
  "healthChecks": ["standard-http"],
  "rollback": {
    "strategy": "previous-artifact"
  }
}
```

#### PHR, Finance, FlashIt

Set:

```json
"lifecycleStatus": "planned"
```

or:

```json
"lifecycleStatus": "partial"
```

but ensure:

```json
"lifecycle": {
  "enabled": false
}
```

Do not require their lifecycle execution to pass yet.

For PHR/Finance/FlashIt, either:

1. Keep `lifecycleConfigPath` only if the file is accurate enough for static validation, or
2. Remove `lifecycleConfigPath` until the product is actively migrated.

Preferred immediate fix:

```text
Remove lifecycleConfigPath from PHR/Finance/FlashIt until their configs are corrected,
or update checks so planned/partial entries may carry config paths that are warnings-only.
```

#### YAPPC and Data Cloud

Remove the following fields entirely from `yappc` and `data-cloud`:

```text
lifecycleProfile
lifecycleStatus
lifecycleMigration
lifecycleConfigPath
lifecycle
toolchain
artifacts
deployment
environments
```

Add no replacement lifecycle fields.

Reason:

```text
They are explicitly out of scope for this framework.
```

If a traceable exclusion is needed, add a separate top-level config file instead of polluting the product registry with disabled lifecycle metadata:

```text
config/kernel-lifecycle-exclusions.json
```

with:

```json
{
  "version": "1.0.0",
  "excludedProducts": {
    "yappc": "Out of scope for Kernel lifecycle framework in this implementation stream.",
    "data-cloud": "Out of scope for Kernel lifecycle framework in this implementation stream."
  }
}
```

### Acceptance criteria

```text
- digital-marketing is the only lifecycleStatus=enabled product.
- yappc has no lifecycle fields.
- data-cloud has no lifecycle fields.
- planned/partial products do not break lifecycle checks.
- existing non-lifecycle product scripts still work.
```

---

## 5.2 `config/canonical-product-registry-schema.json`

### Current issue

The schema supports lifecycle fields, but does not fully enforce lifecycle correctness by lifecycle status.

### Required changes

Add conditional validation:

#### Rule 1 — Enabled lifecycle products

If:

```json
"lifecycleStatus": "enabled"
```

then require:

```text
lifecycleProfile
lifecycleConfigPath
lifecycle.enabled = true
toolchain
artifacts
deployment
environments
```

#### Rule 2 — Planned/partial lifecycle products

If:

```json
"lifecycleStatus": "planned" | "partial"
```

then allow:

```text
lifecycleProfile
lifecycleMigration
```

but do not require:

```text
lifecycleConfigPath
artifacts
deployment
```

unless `lifecycle.enabled` is true.

#### Rule 3 — Excluded products

Do not hardcode `yappc` or `data-cloud` in schema. Enforce exclusions in a check script instead.

#### Rule 4 — Surface/adapters compatibility

Schema should not validate compatibility deeply; keep that in conformance scripts where registry/config files can be cross-read.

### Add definitions

```json
"LifecycleStatus": {
  "type": "string",
  "enum": ["disabled", "planned", "partial", "enabled"]
}
```

Add stricter `additionalProperties: false` for new lifecycle definitions where practical.

### Acceptance criteria

```text
- Schema allows partial migration without false failures.
- Schema requires complete declarations for lifecycleStatus=enabled.
- Schema does not special-case products.
```

---

## 5.3 `config/kernel-lifecycle-exclusions.json` — new

### Purpose

Explicitly declare products that must not be lifecycle-enabled in this implementation stream.

### Add file

```json
{
  "version": "1.0.0",
  "excludedProducts": {
    "yappc": {
      "reason": "YAPPC is out of scope for Kernel product lifecycle adoption in this stream.",
      "allowedLegacyExecution": true
    },
    "data-cloud": {
      "reason": "Data Cloud is out of scope for Kernel product lifecycle adoption in this stream.",
      "allowedLegacyExecution": true
    }
  }
}
```

### Acceptance criteria

```text
- Lifecycle checks fail if excluded products declare lifecycle.enabled=true.
- Lifecycle checks fail if excluded products declare lifecycleStatus=enabled.
- Legacy scripts for excluded products remain allowed.
```

---

## 5.4 `config/product-lifecycle-profiles.json`

### Current issue

Profiles exist, but some are too broad or reference surface types/adapters that are not ready or not schema-aligned.

### Required changes

#### Keep stable profiles

```text
standard-web-api-product
backend-only-java-service
frontend-only-web-product
```

#### Downgrade experimental profiles

Keep these but mark clearly as not safe for default:

```text
mobile-plus-api-product
sdk-product
platform-provider-product
shared-service-product
domain-pack-only-product
```

Add:

```json
"safeForDefault": false
```

to experimental profiles.

For stable profiles add:

```json
"safeForDefault": true
```

#### Add explicit phase output contracts

For each stable profile, add:

```json
"phaseOutputs": {
  "plan": ["lifecycle-plan"],
  "validate": ["lifecycle-result", "gate-results"],
  "test": ["lifecycle-result", "test-summary"],
  "build": ["lifecycle-result", "artifact-manifest"],
  "package": ["lifecycle-result", "artifact-manifest"],
  "deploy": ["lifecycle-result", "deployment-manifest"],
  "verify": ["lifecycle-result", "health-check-report"]
}
```

#### Fix mobile surface mismatch

Do not use `mobile-ios` or `mobile-android` as product registry surface types until the registry schema is updated. Keep those as future platform extension variants only.

Preferred now:

```text
mobile-plus-api-product remains experimental and not used by enabled products.
```

### Acceptance criteria

```text
- Stable profiles are safe for Digital Marketing.
- Experimental profiles cannot be used by lifecycleStatus=enabled products unless explicitly allowed.
- No enabled product uses mobile-ios/mobile-android surface types.
```

---

## 5.5 `config/product-lifecycle-profiles-schema.json`

### Add or update

If already present, extend it. If absent, add it.

### Validate

```text
version
profiles
profile.status
profile.safeForDefault
defaultSurfaces
requiredGates
optionalGates
defaultAdapters
expectedArtifacts
phaseOutputs
```

### Acceptance criteria

```text
- check-product-lifecycle-profiles-schema.mjs validates this schema.
- Unknown lifecycle phases fail.
- Unknown structure fails.
```

---

## 5.6 `config/toolchain-adapter-registry.json`

### Current issue

Registry includes implemented, partial, and planned adapters. Some partial/planned adapters are too easy to accidentally use.

### Required changes

For every adapter add:

```json
"safeForDefault": true | false,
"executionImplemented": true | false,
"planningImplemented": true | false,
"outputValidationImplemented": true | false
```

For current safe adapters:

```json
"gradle-java-service": {
  "status": "implemented",
  "safeForDefault": true,
  "executionImplemented": true,
  "planningImplemented": true,
  "outputValidationImplemented": true
}
```

```json
"pnpm-vite-react": {
  "status": "implemented",
  "safeForDefault": true,
  "executionImplemented": true,
  "planningImplemented": true,
  "outputValidationImplemented": true
}
```

For partial/planned adapters:

```json
"safeForDefault": false
```

#### Do not allow enabled products to use partial/planned adapters

`check-toolchain-adapter-contracts.mjs` must fail when a lifecycle-enabled product uses an adapter where:

```json
"safeForDefault": false
```

unless product lifecycle config explicitly says:

```yaml
allowExperimentalAdapters: true
```

Do not add that exception to Digital Marketing.

### Acceptance criteria

```text
- Digital Marketing uses only safe adapters.
- Planned adapters cannot be used by enabled products accidentally.
- Adapter registry describes implementation status precisely.
```

---

## 5.7 `config/toolchain-adapter-registry-schema.json`

### Required changes

Validate new fields:

```text
safeForDefault
executionImplemented
planningImplemented
outputValidationImplemented
tests
implementation
supportedPhases
supportedSurfaceTypes
requires
outputs
```

### Acceptance criteria

```text
- Toolchain registry fails on missing implementation metadata.
- Toolchain registry fails on unknown phase/surface type.
```

---

## 5.8 `products/digital-marketing/kernel-product.yaml`

### Current issue

Digital Marketing is the correct pilot, and its config is close. It needs stricter schema-backed structure and outputs.

### Required changes

Add explicit schema fields:

```yaml
schemaVersion: 1.0.0
productId: digital-marketing
lifecycleProfile: standard-web-api-product
allowExperimentalAdapters: false
```

Ensure backend surface includes explicit expected outputs:

```yaml
surfaces:
  backend-api:
    source: products/digital-marketing/dm-api
    runtime: java-service
    adapter: gradle-java-service
    gradleModule: :products:digital-marketing:dm-api
    devTask: runDmosApiServer
    validateTask: check
    testTask: test
    buildTask: build
    packageTask: assemble
    expectedOutputs:
      build:
        - products/digital-marketing/dm-api/build/libs/*.jar
      test:
        - products/digital-marketing/dm-api/build/test-results/test
        - products/digital-marketing/dm-api/build/reports/tests
```

Ensure web surface includes explicit expected outputs:

```yaml
  web:
    source: products/digital-marketing/ui
    runtime: static-web
    adapter: pnpm-vite-react
    packagePath: products/digital-marketing/ui/package.json
    devScript: dev
    validateScript: type-check
    testScript: test
    buildScript: build
    packageScript: build
    expectedOutputs:
      build:
        - products/digital-marketing/ui/dist
        - products/digital-marketing/ui/dist/index.html
```

Add local deployment section:

```yaml
deployment:
  local:
    target: compose-local
    composeFile: products/digital-marketing/deploy/local.compose.yaml
    envFile: products/digital-marketing/deploy/local.env
    envExampleFile: products/digital-marketing/deploy/local.env.example
    healthChecks:
      backend-api:
        type: http
        url: http://localhost:${DMOS_API_PORT:-8080}/health
      web:
        type: http
        url: http://localhost:${DMOS_WEB_PORT:-5173}/
```

### Acceptance criteria

```text
- Digital Marketing config validates against schema.
- Build plan includes backend-api and web.
- Build execution validates exact expected outputs.
- Deploy plan knows local compose and health check URLs.
```

---

## 5.9 `products/phr/kernel-product.yaml`

### Current issue

Current config appears inconsistent with actual registry/module paths.

### Required changes

Do not try to fully migrate PHR now.

Either:

1. Remove this file until PHR migration starts, or
2. Mark it as draft and ensure lifecycle checks do not treat it as executable.

Preferred:

```yaml
schemaVersion: 1.0.0
productId: phr
lifecycleProfile: standard-web-api-product
status: draft
executionEnabled: false
migration:
  status: planned
  reason: "PHR lifecycle config requires path/module correction before execution."
```

Do not include inaccurate Gradle modules.

### Acceptance criteria

```text
- PHR lifecycle does not run.
- PHR lifecycle draft does not break checks.
- Checks fail if someone enables PHR without correcting modules.
```

---

## 5.10 `products/finance/kernel-product.yaml`

### Current issue

Current config references modules not aligned to the registry.

### Required changes

Same approach as PHR.

Mark as draft or remove until migration starts.

Suggested draft:

```yaml
schemaVersion: 1.0.0
productId: finance
lifecycleProfile: backend-only-java-service
status: draft
executionEnabled: false
migration:
  status: planned
  reason: "Finance lifecycle must map real registered modules and surfaces before execution."
```

### Acceptance criteria

```text
- Finance lifecycle does not run.
- Finance draft config cannot be mistaken for executable lifecycle config.
```

---

## 5.11 `products/flashit/kernel-product.yaml`

### Current issue

Current config uses `mobile-ios` and `mobile-android`, but the registry uses a generic `mobile` surface. Also some adapters are partial.

### Required changes

Do not enable FlashIt lifecycle in this stabilization stream.

Mark draft:

```yaml
schemaVersion: 1.0.0
productId: flashit
lifecycleProfile: mobile-plus-api-product
status: draft
executionEnabled: false
migration:
  status: planned
  reason: "FlashIt requires mobile surface/adapter normalization before lifecycle execution."
```

### Future migration note

When FlashIt is migrated, decide whether the canonical surface model is:

```text
mobile
```

with platform variants:

```text
ios
android
```

or whether registry schema should explicitly support:

```text
mobile-ios
mobile-android
```

Do not mix both.

### Acceptance criteria

```text
- FlashIt lifecycle does not run.
- Partial mobile adapters are not used by enabled lifecycle products.
```

---

## 5.12 `products/yappc/kernel-product.yaml`

### Required change

If this file exists, delete it or move it to an archive path not consumed by lifecycle checks:

```text
products/yappc/docs/archive/kernel-product.lifecycle-excluded.yaml
```

Do not keep executable lifecycle config under:

```text
products/yappc/kernel-product.yaml
```

### Acceptance criteria

```text
- YAPPC cannot be lifecycle-planned.
- YAPPC continues to use existing product-specific build/test/dev flows.
```

---

## 5.13 `products/data-cloud/kernel-product.yaml`

### Required change

If this file exists, delete it or move it to an archive path not consumed by lifecycle checks:

```text
products/data-cloud/docs/archive/kernel-product.lifecycle-excluded.yaml
```

Do not keep executable lifecycle config under:

```text
products/data-cloud/kernel-product.yaml
```

### Acceptance criteria

```text
- Data Cloud cannot be lifecycle-planned.
- Data Cloud continues to use existing product-specific build/test/dev flows.
```

---

## 5.14 `platform/typescript/kernel-lifecycle/package.json`

### Current issue

Version drift and overlapping local dependencies.

### Required changes

Align with workspace conventions.

Change:

```json
"typescript": "^5.3.3"
"zod": "^3.22.4"
"eslint": "^8.56.0"
"vitest": "^1.0.4"
"@types/node": "^20.10.0"
```

to catalog/workspace-approved versions.

Preferred:

```json
"dependencies": {
  "yaml": "^2.3.4",
  "zod": "catalog:"
},
"devDependencies": {
  "@types/node": "catalog:",
  "typescript": "catalog:",
  "vitest": "catalog:"
}
```

Do not add `ajv` unless it is already used consistently or needed. If schema validation is performed via existing scripts without runtime `ajv`, remove it.

### Acceptance criteria

```text
- No local TypeScript/Zod/ESLint/Vitest version drift.
- Package builds through root workspace.
- Package uses repo TypeScript standards.
```

---

## 5.15 `platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePhase.ts`

### Current issue

This file contains too many unrelated types and duplicates concepts that should be shared.

### Required changes

Split into focused files:

```text
platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePhase.ts
platform/typescript/kernel-lifecycle/src/domain/ProductSurfaceType.ts
platform/typescript/kernel-lifecycle/src/domain/ProductSurface.ts
platform/typescript/kernel-lifecycle/src/domain/LifecyclePhaseConfiguration.ts
platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePlan.ts
platform/typescript/kernel-lifecycle/src/domain/ProductLifecycleResult.ts
platform/typescript/kernel-lifecycle/src/domain/ProductLifecycleStep.ts
platform/typescript/kernel-lifecycle/src/domain/ProductLifecycleEvent.ts
platform/typescript/kernel-lifecycle/src/domain/ProductArtifact.ts
platform/typescript/kernel-lifecycle/src/domain/ProductEnvironment.ts
platform/typescript/kernel-lifecycle/src/domain/ProductGate.ts
platform/typescript/kernel-lifecycle/src/domain/ProductFailurePolicy.ts
```

Export them from:

```text
platform/typescript/kernel-lifecycle/src/domain/index.ts
platform/typescript/kernel-lifecycle/src/index.ts
```

### Required typing improvements

Use discriminated unions where possible:

```ts
export type ProductLifecyclePhase =
  | 'create'
  | 'bootstrap'
  | 'dev'
  | 'validate'
  | 'test'
  | 'build'
  | 'package'
  | 'release'
  | 'deploy'
  | 'verify'
  | 'promote'
  | 'rollback'
  | 'operate'
  | 'retire';
```

Define:

```ts
export interface ProductLifecyclePlan {
  readonly schemaVersion: '1.0.0';
  readonly productId: string;
  readonly phase: ProductLifecyclePhase;
  readonly lifecycleProfile: string;
  readonly environment?: string;
  readonly sourceRef?: string;
  readonly surfaces: readonly ProductSurfaceSelection[];
  readonly gates: readonly ProductGatePlan[];
  readonly steps: readonly ProductLifecycleStep[];
  readonly expectedArtifacts: readonly ProductExpectedArtifact[];
  readonly outputDirectory: string;
  readonly estimatedDurationMs: number;
}
```

Use `readonly` where appropriate.

### Acceptance criteria

```text
- No giant multi-responsibility domain file.
- Toolchain package imports shared types instead of duplicating them.
- No `any` types.
```

---

## 5.16 `platform/typescript/kernel-toolchains/src/ToolchainAdapter.ts`

### Current issue

It duplicates lifecycle types.

### Required changes

Remove local definitions of:

```text
ProductLifecyclePhase
ProductSurfaceType
ProductSurface
```

Import them from a shared lifecycle contracts package.

Preferred long-term package:

```text
platform/typescript/kernel-product-contracts
```

with:

```text
ProductLifecyclePhase
ProductSurfaceType
ProductSurface
ProductArtifact
ProductEnvironment
ProductGate
```

Then both `kernel-lifecycle` and `kernel-toolchains` depend on `kernel-product-contracts`.

### Acceptance criteria

```text
- No duplicate lifecycle type definitions.
- No TODO comments about type unification.
- Packages remain acyclic.
```

---

## 5.17 `platform/typescript/kernel-product-contracts` — new package

### Purpose

Shared pure TypeScript contracts for lifecycle, toolchains, artifacts, deployment, and product metadata.

### Add files

```text
platform/typescript/kernel-product-contracts/package.json
platform/typescript/kernel-product-contracts/tsconfig.json
platform/typescript/kernel-product-contracts/src/index.ts
platform/typescript/kernel-product-contracts/src/lifecycle/ProductLifecyclePhase.ts
platform/typescript/kernel-product-contracts/src/lifecycle/ProductLifecyclePlan.ts
platform/typescript/kernel-product-contracts/src/lifecycle/ProductLifecycleResult.ts
platform/typescript/kernel-product-contracts/src/surface/ProductSurface.ts
platform/typescript/kernel-product-contracts/src/artifact/ProductArtifact.ts
platform/typescript/kernel-product-contracts/src/environment/ProductEnvironment.ts
platform/typescript/kernel-product-contracts/src/gate/ProductGate.ts
platform/typescript/kernel-product-contracts/src/deployment/ProductDeployment.ts
```

### Package naming

Use canonical package name:

```json
"name": "@ghatana/kernel-product-contracts"
```

### Update governance

Update:

```text
platform/typescript/LIBRARY_GOVERNANCE.md
pnpm-workspace.yaml generated entries if needed
config/workspace-dependency-policy.json if package policy requires it
```

### Acceptance criteria

```text
- Pure contract package has no Node runtime dependency.
- Both kernel-lifecycle and kernel-toolchains import from it.
- No product-specific vocabulary exists in this package.
```

---

## 5.18 `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`

### Current issue

Planner is too shallow and does not validate adapter compatibility, lifecycle status, excluded products, gates, artifacts, environments, or output directories.

### Required changes

Refactor into focused collaborators:

```text
src/planning/ProductLifecyclePlanner.ts
src/planning/ProductLifecycleGraphBuilder.ts
src/planning/LifecycleProfileResolver.ts
src/planning/ProductLifecycleConfigLoader.ts
src/planning/SurfaceSelector.ts
src/planning/ToolchainResolver.ts
src/planning/GateResolver.ts
src/planning/ArtifactExpectationResolver.ts
src/planning/EnvironmentResolver.ts
src/planning/ProductLifecycleExclusionPolicy.ts
```

### Planner must validate

```text
- product exists
- product is not lifecycle-excluded
- lifecycleStatus=enabled for execution plan
- lifecycle.enabled=true for execution plan
- lifecycle profile exists
- lifecycle config file exists
- productId in config matches registry productId
- selected surfaces exist in registry and config
- adapters exist
- adapters support requested phase
- adapters support surface type
- adapter is safeForDefault for enabled products
- required adapter config fields exist
- expected artifacts are known artifact types
- environment exists for deploy/verify/promote/rollback
```

### Plan must include

```text
schemaVersion
productId
phase
lifecycleProfile
environment
sourceRef
surfaces
gates
steps
expectedArtifacts
outputDirectory
estimatedDurationMs
```

### Acceptance criteria

```text
- Planner performs all static validation before execution.
- Planner can be tested without running tools.
- Planner rejects YAPPC/Data Cloud lifecycle planning.
- Planner allows Digital Marketing.
```

---

## 5.19 `platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts` — add/complete

### Current issue

Execution currently happens in `scripts/kernel-product.mjs` and not through lifecycle package.

### Required changes

Add executor package files:

```text
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleStepRunner.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleResultCollector.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleFailureHandler.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleLogger.ts
```

### Executor responsibilities

```text
1. Accept ProductLifecyclePlan.
2. Resolve adapters through ToolchainAdapterRegistry.
3. Execute sequential/parallel steps as planned.
4. Run pre-gates.
5. Run adapter execution.
6. Validate adapter outputs.
7. Collect artifacts.
8. Run post-gates.
9. Emit lifecycle-result.json.
10. Delegate artifact manifest generation to kernel-artifacts.
11. Fail closed on required artifact/check failures.
```

### Important rule

Do not generate artifact manifests inside adapters.

### Acceptance criteria

```text
- CLI script no longer owns execution logic.
- Executor unit tests use fake adapters.
- Real execution remains behind integration tests.
```

---

## 5.20 `scripts/kernel-product.mjs`

### Current issue

This script is doing too much:

```text
custom YAML parsing
registry loading
planning
adapter validation
execution
result writing
```

### Required changes

Turn it into a thin CLI wrapper.

### New behavior

```text
1. Parse CLI args.
2. Call compiled lifecycle package.
3. Print JSON or human output.
4. Exit with correct status.
```

### Remove

```text
custom YAML parser
inline registry loading
inline buildPlan
inline executePlan
inline command mapping
```

### Keep

```text
CLI compatibility
--json
--dry-run
--surface
--surfaces
--env
--output-dir
--source-ref
--artifact
```

### Add missing options

```text
--from
--to
--approval-id
--release-id
```

for promote/release flows.

### Acceptance criteria

```text
- Script is under ~150 lines.
- No lifecycle business logic in script.
- All lifecycle behavior is in platform/typescript/kernel-lifecycle.
- promote:digital-marketing script no longer fails argument parsing.
```

---

## 5.21 `scripts/run-product-lifecycle.mjs`

### Current issue

The script is inconsistent and says execution is not implemented.

### Required changes

Pick one:

#### Preferred option

Delete it and update references to use:

```text
scripts/kernel-product.mjs
```

#### Compatibility option

Keep it as a thin alias:

```js
import { spawnSync } from 'node:child_process';
spawnSync('node', ['scripts/kernel-product.mjs', 'product', phase, productId, ...args])
```

### Acceptance criteria

```text
- No direct import from TypeScript src/*.js.
- No duplicate lifecycle planning.
- No constructor mismatch.
- No “execution not implemented” path remains.
```

---

## 5.22 `scripts/run-product-task.mjs`

### Current issue

Legacy routing remains necessary, but delegation behavior must be clearer and safer.

### Required changes

1. Keep legacy behavior for non-lifecycle products.
2. Delegate to Kernel lifecycle only when:

```text
product.lifecycleStatus === "enabled"
product.lifecycle.enabled === true
```

3. Do not delegate YAPPC/Data Cloud.
4. Add explicit aliases:

```text
gateway -> backend-api
backend -> backend-api
api -> backend-api
```

5. For lifecycle products, translate old commands:

```text
dev -> dev
build -> build
test -> test
lint -> validate
typecheck -> validate
```

6. If task is unsupported, fail with actionable error unless `--legacy` is passed.

### Acceptance criteria

```text
- `pnpm product digital-marketing build` delegates to lifecycle.
- `pnpm product yappc build` remains legacy.
- `pnpm product data-cloud build` remains legacy.
- `pnpm product flashit build` remains legacy until enabled.
```

---

## 5.23 `platform/typescript/kernel-toolchains/src/adapters/GradleJavaServiceAdapter.ts`

### Current issue

Adapter is useful but mixes execution, output validation, and artifact manifest writing.

### Required changes

1. Remove artifact manifest generation from adapter.
2. Return artifact paths in `ToolchainExecutionResult`.
3. Treat required output validation failure as fatal.
4. Make output validation use expected outputs from context.
5. Validate that `gradleModule` begins with `:`.
6. Validate that configured Gradle task is phase-safe.
7. Do not map unsupported phases to `build`.

### New phase mapping

```ts
const supportedTaskByPhase = {
  dev: 'devTask',
  validate: 'validateTask',
  test: 'testTask',
  build: 'buildTask',
  package: 'packageTask'
} as const;
```

If phase is not in map, throw.

### Add tests

```text
platform/typescript/kernel-toolchains/src/adapters/__tests__/GradleJavaServiceAdapter.test.ts
```

Test cases:

```text
- plans Gradle command with args array
- rejects missing gradleModule
- rejects gradleModule without leading colon
- maps validate to validateTask
- maps dev to devTask
- fails unsupported phase
- validates expected jar output
- fails when expected artifact missing
- dry-run does not invoke command runner
```

### Acceptance criteria

```text
- No manifest writing in adapter.
- No non-blocking artifact failure.
- No unsupported phase fallback.
```

---

## 5.24 `platform/typescript/kernel-toolchains/src/adapters/PnpmViteReactAdapter.ts`

### Current issue

Adapter references `context.surface.id`, maps unsupported phases, and writes manifests internally.

### Required changes

1. Remove `context.surface.id` usage.
2. Remove artifact manifest writing from adapter.
3. Return artifact paths only.
4. Treat missing required outputs as fatal.
5. Use explicit phase-to-script map.
6. Reject unsupported phases.
7. Validate `packagePath` exists and points to `package.json`.
8. Validate script exists in package.json before execution.
9. For `validate`, prefer configured `validateScript`; if absent use `type-check`, not `lint`, because Digital Marketing UI has `type-check`.
10. For `test`, validate test command is present.

### Add tests

```text
platform/typescript/kernel-toolchains/src/adapters/__tests__/PnpmViteReactAdapter.test.ts
```

Test cases:

```text
- plans pnpm command with --dir
- rejects missing packagePath
- rejects missing package.json
- rejects missing script
- maps validate to validateScript
- maps build to buildScript
- fails unsupported phase
- validates dist/index.html for build
- dry-run does not invoke command runner
```

### Acceptance criteria

```text
- No TypeScript error from context.surface.id.
- No manifest side effects.
- No unsupported phase fallback.
```

---

## 5.25 `platform/typescript/kernel-toolchains/src/execution/CommandRunner.ts`

### Required changes

Ensure command runner interface is typed and observable:

```ts
export interface CommandRunner {
  run(
    command: string,
    args: readonly string[],
    options: CommandRunnerOptions,
  ): Promise<CommandRunnerResult>;
}
```

`CommandRunnerOptions`:

```ts
export interface CommandRunnerOptions {
  readonly cwd: string;
  readonly env?: Readonly<Record<string, string | undefined>>;
  readonly timeoutMs?: number;
}
```

`CommandRunnerResult`:

```ts
export interface CommandRunnerResult {
  readonly exitCode: number;
  readonly stdout: string;
  readonly stderr: string;
  readonly durationMs: number;
}
```

### Acceptance criteria

```text
- No shell string execution.
- Args array used everywhere.
- Timeout supported.
- stdout/stderr truncation happens at lifecycle result collection, not command runner.
```

---

## 5.26 `platform/typescript/kernel-artifacts`

### Current issue

Artifact manifest generation appears referenced from adapters, but lifecycle should own it centrally.

### Required changes

Ensure package owns:

```text
ArtifactManifestGenerator
ArtifactFingerprint
ArtifactManifestWriter
ArtifactExpectationValidator
ArtifactSchemaValidator
```

### Required files

```text
platform/typescript/kernel-artifacts/src/ArtifactManifestGenerator.ts
platform/typescript/kernel-artifacts/src/ArtifactFingerprint.ts
platform/typescript/kernel-artifacts/src/ArtifactManifestWriter.ts
platform/typescript/kernel-artifacts/src/ArtifactExpectationValidator.ts
platform/typescript/kernel-artifacts/src/ArtifactSchemaValidator.ts
```

### Required behavior

```text
- Accept ProductLifecyclePlan and ToolchainExecutionResult[].
- Validate required artifacts.
- Compute fingerprints.
- Write artifact-manifest.json under .kernel/out/products/<product>/<phase>/<runId>/.
- Fail if required artifacts missing.
```

### Acceptance criteria

```text
- Adapters no longer write manifests.
- Build phase emits artifact manifest centrally.
- Package/deploy phases consume artifact manifest.
```

---

## 5.27 `platform/typescript/kernel-deployment`

### Current issue

Deployment package exists in root build script but local deploy behavior must be made real and safe.

### Required files

```text
platform/typescript/kernel-deployment/src/DeploymentPlan.ts
platform/typescript/kernel-deployment/src/DeploymentResult.ts
platform/typescript/kernel-deployment/src/DeploymentEnvironment.ts
platform/typescript/kernel-deployment/src/DeploymentHealthCheck.ts
platform/typescript/kernel-deployment/src/adapters/ComposeLocalDeploymentAdapter.ts
platform/typescript/kernel-deployment/src/validation/DeploymentContractValidator.ts
```

### First target

Only implement:

```text
compose-local
```

Do not implement Kubernetes/Helm/Terraform in this stabilization slice beyond schemas and placeholders marked planned.

### ComposeLocal adapter must

```text
- validate compose file exists
- validate env example exists
- require env file or generate from example only in local mode
- run docker compose config before docker compose up
- run health checks
- write deployment-manifest.json
```

### Acceptance criteria

```text
- `pnpm kernel product deploy digital-marketing --env local --dry-run` works.
- Real deploy is either fully implemented or explicitly disabled with actionable error.
- No partial deploy pretends success.
```

---

## 5.28 `products/digital-marketing/deploy/local.compose.yaml`

### Current status

The Digital Marketing lifecycle config references this file.

### Required changes

Ensure file exists and is Kernel-compatible.

Minimum content:

```yaml
services:
  digital-marketing-api:
    build:
      context: ../../..
      dockerfile: products/digital-marketing/dm-api/Dockerfile
    environment:
      DMOS_API_PORT: ${DMOS_API_PORT:-8080}
    ports:
      - "${DMOS_API_PORT:-8080}:8080"
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:8080/health"]
      interval: 30s
      timeout: 5s
      retries: 3

  digital-marketing-web:
    build:
      context: ./ui
    environment:
      DMOS_WEB_PORT: ${DMOS_WEB_PORT:-5173}
    ports:
      - "${DMOS_WEB_PORT:-5173}:5173"
```

Adjust exact paths based on existing Dockerfiles. If Dockerfiles are not present, do not claim local deploy is implemented. Instead make deploy dry-run only and add Dockerfile tasks.

### Acceptance criteria

```text
- compose config validates.
- health checks exist.
- no hardcoded secrets.
```

---

## 5.29 `products/digital-marketing/deploy/local.env.example`

### Add or update

Required values:

```dotenv
DMOS_API_PORT=8080
DMOS_WEB_PORT=5173
DMOS_ENV=local
```

Do not include secrets.

### Acceptance criteria

```text
- No real secrets.
- Local deploy can copy this to local.env.
```

---

## 5.30 `scripts/check-product-lifecycle-contracts.mjs`

### Current issue

The script validates products with lifecycleProfile, but does not enforce exclusion or properly distinguish enabled vs planned/partial.

### Required changes

1. Load:

```text
config/kernel-lifecycle-exclusions.json
```

2. Fail when excluded product has:

```text
lifecycleStatus=enabled
lifecycle.enabled=true
```

3. For excluded products, also fail if package scripts use `kernel product` lifecycle commands.

4. For planned/partial products:
   - require valid lifecycleProfile if present
   - do not run plan generation
   - warn on inaccurate/missing config instead of failing unless config claims `executionEnabled: true`

5. For enabled products:
   - require config
   - require plan generation for `validate`, `test`, `build`
   - require adapters safeForDefault
   - require artifact declarations
   - require deployment target for deployable phases

### Acceptance criteria

```text
- Digital Marketing passes.
- YAPPC/Data Cloud are excluded.
- PHR/Finance/FlashIt do not fail unless marked enabled.
- Errors are actionable.
```

---

## 5.31 `scripts/check-toolchain-adapter-contracts.mjs`

### Required changes

Validate:

```text
- every adapter has implementation path
- implementation path exists if status=implemented
- tests exist if status=implemented
- safeForDefault=true only allowed when status=implemented
- every supported phase is known
- every supported surface type is known
- every enabled product uses only safeForDefault adapters
```

### Acceptance criteria

```text
- Partial adapters cannot be used by enabled products.
- Unknown adapter references fail.
```

---

## 5.32 `scripts/check-product-artifact-contracts.mjs`

### Required changes

Validate:

```text
- enabled lifecycle products declare artifacts
- declared artifact types are known
- declared artifact paths are present in kernel-product.yaml where build/package expects them
- artifact schema files exist
```

### Acceptance criteria

```text
- Digital Marketing artifact declarations pass.
- Missing expected artifact patterns fail before deploy.
```

---

## 5.33 `scripts/check-product-deployment-contracts.mjs`

### Required changes

Validate only lifecycle-enabled deployable products.

For Digital Marketing:

```text
- deployment local target exists
- compose file path exists
- env example path exists
- health checks declared for backend-api and web
- rollback strategy declared
```

For non-enabled products:

```text
- warn only
```

For excluded products:

```text
- fail if deploy lifecycle is declared
```

### Acceptance criteria

```text
- No deployable product lacks health checks.
- Local deploy target is schema-backed.
```

---

## 5.34 `scripts/check-kernel-platform-lifecycle.mjs`

### Current issue

It invokes several checks but should become the canonical lifecycle gate.

### Required changes

Run checks in this order:

```text
check-product-lifecycle-profiles-schema.mjs
check-toolchain-adapter-registry-schema.mjs
check-product-lifecycle-contracts.mjs
check-toolchain-adapter-contracts.mjs
check-product-artifact-contracts.mjs
check-product-deployment-contracts.mjs
check-kernel-lifecycle-exclusions.mjs
```

Add:

```text
scripts/check-kernel-lifecycle-exclusions.mjs
```

### Acceptance criteria

```text
- Single lifecycle gate catches all current drift.
- Errors are explicit.
```

---

## 5.35 `scripts/check-kernel-lifecycle-exclusions.mjs` — new

### Purpose

Ensure excluded products are not accidentally pulled into lifecycle.

### Behavior

Load:

```text
config/kernel-lifecycle-exclusions.json
config/canonical-product-registry.json
package.json
```

Fail if excluded product has:

```text
lifecycleStatus=enabled
lifecycle.enabled=true
lifecycleConfigPath
package scripts using "kernel-product.mjs product" for that product
```

Warn if excluded product has old legacy `pnpm product` scripts. Those are allowed.

### Acceptance criteria

```text
- YAPPC/Data Cloud lifecycle adoption is blocked.
- Legacy scripts remain allowed.
```

---

## 5.36 `scripts/generate-product-registry-artifacts.mjs`

### Current issue

Generator creates package scripts and generated artifacts. It must become lifecycle-aware without accidentally migrating excluded products.

### Required changes

1. Generate lifecycle scripts only for products where:

```text
lifecycleStatus === "enabled"
lifecycle.enabled === true
```

2. Do not generate lifecycle scripts for:

```text
yappc
data-cloud
```

3. Keep legacy scripts for all non-lifecycle products.

4. Add generated lifecycle matrix fields:

```json
"productsWithLifecycle": ["digital-marketing"],
"excludedFromLifecycle": ["yappc", "data-cloud"],
"lifecycleProfiles": {
  "digital-marketing": "standard-web-api-product"
}
```

5. Ensure generated scripts for Digital Marketing use correct CLI syntax:

```json
"build:digital-marketing": "node scripts/kernel-product.mjs product build digital-marketing",
"plan:build:digital-marketing": "node scripts/kernel-product.mjs product plan digital-marketing build"
```

6. Do not generate invalid promote script until promote is implemented.

Either remove:

```json
"promote:digital-marketing"
```

or make CLI parse it correctly.

Preferred now:

```text
Do not generate release/promote/rollback scripts until those phases are implemented beyond plan/dry-run.
```

### Acceptance criteria

```text
- Generated package scripts are deterministic.
- Digital Marketing gets lifecycle scripts.
- YAPPC/Data Cloud do not get lifecycle scripts.
- No generated script invokes unsupported CLI options.
```

---

## 5.37 `package.json`

### Current issue

Contains lifecycle scripts, but some are premature or invalid.

### Required changes

Keep:

```json
"kernel": "node ./scripts/kernel-product.mjs",
"build:kernel-lifecycle-platform": "pnpm --dir platform/typescript/kernel-product-contracts build && pnpm --dir platform/typescript/kernel-artifacts build && pnpm --dir platform/typescript/kernel-lifecycle build && pnpm --dir platform/typescript/kernel-toolchains build && pnpm --dir platform/typescript/kernel-deployment build",
"check:kernel-platform-lifecycle": "pnpm build:kernel-lifecycle-platform && node ./scripts/check-kernel-platform-lifecycle.mjs"
```

Digital Marketing scripts:

```json
"build:digital-marketing": "node scripts/kernel-product.mjs product build digital-marketing",
"test:digital-marketing": "node scripts/kernel-product.mjs product test digital-marketing",
"dev:digital-marketing": "node scripts/kernel-product.mjs product dev digital-marketing",
"validate:digital-marketing": "node scripts/kernel-product.mjs product validate digital-marketing",
"package:digital-marketing": "node scripts/kernel-product.mjs product package digital-marketing",
"deploy:local:digital-marketing": "node scripts/kernel-product.mjs product deploy digital-marketing --env local",
"verify:local:digital-marketing": "node scripts/kernel-product.mjs product verify digital-marketing --env local",
"plan:build:digital-marketing": "node scripts/kernel-product.mjs product plan digital-marketing build"
```

Remove until implemented:

```text
release:digital-marketing
promote:digital-marketing
rollback:digital-marketing
```

or mark as plan-only:

```json
"plan:promote:digital-marketing": "node scripts/kernel-product.mjs product plan digital-marketing promote --from staging --to prod"
```

### Acceptance criteria

```text
- No package script is known-invalid.
- YAPPC/Data Cloud scripts remain legacy only.
- Digital Marketing scripts use Kernel lifecycle.
```

---

## 5.38 `pnpm-workspace.yaml`

### Required changes

Ensure new package is included:

```yaml
- "platform/typescript/kernel-product-contracts"
- "platform/typescript/kernel-lifecycle"
- "platform/typescript/kernel-toolchains"
- "platform/typescript/kernel-artifacts"
- "platform/typescript/kernel-deployment"
```

If generated, update generator instead of manual edit.

### Acceptance criteria

```text
- All lifecycle packages are installable and buildable by pnpm.
```

---

## 5.39 `platform/typescript/LIBRARY_GOVERNANCE.md`

### Required changes

Add canonical entries:

```text
@ghatana/kernel-product-contracts
@ghatana/kernel-lifecycle
@ghatana/kernel-toolchains
@ghatana/kernel-artifacts
@ghatana/kernel-deployment
```

Each entry must include:

```text
owner
purpose
status
allowed dependencies
public API stability
```

### Acceptance criteria

```text
- New lifecycle packages are governed.
- No deprecated or unregistered package names.
```

---

## 5.40 `docs/kernel/PRODUCT_LIFECYCLE_CONTRACT.md`

### Current issue

Too thin for implementation.

### Required changes

Expand sections:

```text
1. Purpose
2. Non-goals
3. Product developer contract
4. Power-user override contract
5. Lifecycle phases
6. Phase input/output contract
7. Plan contract
8. Result contract
9. Gate contract
10. Artifact contract
11. Environment contract
12. Deployment contract
13. Failure policy
14. Observability requirements
15. Security/privacy requirements
16. Product adoption status model
17. Excluded product policy
```

### Must document

```text
lifecycleStatus=enabled
lifecycleStatus=planned
lifecycleStatus=partial
lifecycleStatus=disabled
```

### Acceptance criteria

```text
- Docs explain why Digital Marketing is enabled.
- Docs explain why YAPPC/Data Cloud are excluded.
- Docs match schema and checks.
```

---

## 5.41 `docs/kernel/PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md`

### Required changes

Document:

```text
adapter interface
adapter status model
safeForDefault
planningImplemented
executionImplemented
outputValidationImplemented
required fields
supported phases
supported surfaces
no shell strings
structured command execution
output validation
observability
testing requirements
```

### Acceptance criteria

```text
- Adapter authors know what to implement.
- Power users can add adapters without bypassing Kernel.
```

---

## 5.42 `docs/kernel/PRODUCT_ARTIFACT_CONTRACT.md`

### Required changes

Document:

```text
artifact-manifest.json
build-manifest.json
deployment-manifest.json
fingerprints
required vs optional artifacts
artifact producer
artifact consumer
fail-closed behavior
```

### Acceptance criteria

```text
- Artifact contract matches kernel-artifacts package.
```

---

## 5.43 `docs/kernel/PRODUCT_DEPLOYMENT_CONTRACT.md`

### Required changes

Document:

```text
deploy phase
verify phase
environment resolution
local compose target
health checks
rollback policy
promotion policy
non-local gate requirements
```

### Explicitly state

```text
Only local compose deployment is in scope for this stabilization plan.
Kubernetes/Helm/Terraform remain planned adapters until implemented and tested.
```

### Acceptance criteria

```text
- No one assumes production deploy is complete.
- Local deploy is the only initial executable target.
```

---

# 6. Testing Plan

## 6.1 Unit tests

Add or update:

```text
platform/typescript/kernel-lifecycle/src/planning/__tests__/ProductLifecyclePlanner.test.ts
platform/typescript/kernel-lifecycle/src/execution/__tests__/ProductLifecycleExecutor.test.ts
platform/typescript/kernel-toolchains/src/adapters/__tests__/GradleJavaServiceAdapter.test.ts
platform/typescript/kernel-toolchains/src/adapters/__tests__/PnpmViteReactAdapter.test.ts
platform/typescript/kernel-artifacts/src/__tests__/ArtifactManifestGenerator.test.ts
platform/typescript/kernel-deployment/src/__tests__/ComposeLocalDeploymentAdapter.test.ts
```

## 6.2 Script tests

Add:

```text
scripts/check-kernel-lifecycle-exclusions.test.mjs
scripts/check-product-lifecycle-contracts.test.mjs
scripts/kernel-product.test.mjs
scripts/run-product-task.lifecycle.test.mjs
```

## 6.3 Product pilot tests

Digital Marketing:

```text
products/digital-marketing/conformance/lifecycle-plan.test.json
products/digital-marketing/conformance/artifact-fixtures.json
products/digital-marketing/conformance/deployment-fixtures.json
```

## 6.4 Validation commands

Run focused checks first:

```bash
pnpm --dir platform/typescript/kernel-product-contracts build
pnpm --dir platform/typescript/kernel-lifecycle build
pnpm --dir platform/typescript/kernel-toolchains build
pnpm --dir platform/typescript/kernel-artifacts build
pnpm --dir platform/typescript/kernel-deployment build
node scripts/check-kernel-platform-lifecycle.mjs
node scripts/kernel-product.mjs product plan digital-marketing build --json
node scripts/kernel-product.mjs product build digital-marketing --dry-run --json
```

Then run broader:

```bash
pnpm check:product-registry-artifacts
pnpm check:kernel-platform-lifecycle
pnpm check:kernel-boundaries
pnpm check:product-manifest-contracts
pnpm check:runtime-template-conformance
```

---

# 7. Sprint-by-Sprint Implementation Order

## Sprint 1 — Stabilize scope and contracts

1. Add `config/kernel-lifecycle-exclusions.json`.
2. Remove lifecycle fields from `yappc` and `data-cloud`.
3. Mark PHR/Finance/FlashIt lifecycle configs as draft or planned-only.
4. Expand lifecycle docs.
5. Fix package scripts that are invalid.
6. Update lifecycle conformance scripts to distinguish enabled/planned/excluded.

Success:

```bash
pnpm check:kernel-platform-lifecycle
node scripts/kernel-product.mjs product plan digital-marketing build --json
```

## Sprint 2 — Type and package cleanup

1. Add `@ghatana/kernel-product-contracts`.
2. Split lifecycle domain types.
3. Remove duplicated types from toolchains.
4. Align lifecycle/toolchain package dependencies to workspace catalog.
5. Add/repair tests.

Success:

```bash
pnpm build:kernel-lifecycle-platform
```

## Sprint 3 — Planner/executor consolidation

1. Move planning from `kernel-product.mjs` into `ProductLifecyclePlanner`.
2. Add `ProductLifecycleExecutor`.
3. Make `kernel-product.mjs` thin.
4. Remove or alias `run-product-lifecycle.mjs`.
5. Make `run-product-task.mjs` delegate only enabled products.

Success:

```bash
pnpm kernel product plan digital-marketing build --json
pnpm kernel product build digital-marketing --dry-run --json
pnpm product digital-marketing build --dry-run
```

## Sprint 4 — Adapter correctness

1. Fix Gradle adapter.
2. Fix pnpm adapter.
3. Add adapter output validation.
4. Remove manifest side effects from adapters.
5. Move artifact manifest writing into `kernel-artifacts`.

Success:

```bash
pnpm kernel product build digital-marketing --surface backend-api --dry-run
pnpm kernel product build digital-marketing --surface web --dry-run
```

## Sprint 5 — Artifact and local deploy

1. Implement central artifact manifests.
2. Implement local compose deployment dry-run.
3. Add Digital Marketing local deploy files.
4. Add health checks.
5. Add deployment manifests.

Success:

```bash
pnpm kernel product package digital-marketing --dry-run
pnpm kernel product deploy digital-marketing --env local --dry-run
```

## Sprint 6 — CI/generation hardening

1. Update generated registry artifacts.
2. Update CI matrix generation.
3. Add lifecycle CI workflows or extend existing workflows.
4. Ensure no lifecycle scripts generated for YAPPC/Data Cloud.

Success:

```bash
pnpm check:product-registry-artifacts
pnpm check:kernel-platform-lifecycle
```

---

# 8. Final Acceptance Criteria

The stabilization is complete when:

```text
[ ] Digital Marketing is the only enabled lifecycle product.
[ ] YAPPC and Data Cloud are explicitly excluded from lifecycle adoption.
[ ] PHR/Finance/FlashIt are planned/partial only and cannot accidentally execute lifecycle.
[ ] Kernel CLI is thin and delegates to lifecycle package.
[ ] Lifecycle package owns planning and execution.
[ ] Toolchain package owns tool-specific adapters only.
[ ] Shared contracts are not duplicated.
[ ] Artifact manifests are generated centrally.
[ ] Required artifact failures are fatal.
[ ] Local deploy is either fully implemented or explicitly dry-run only.
[ ] Package scripts contain no invalid lifecycle commands.
[ ] All new TypeScript packages use workspace-approved dependency versions.
[ ] Lifecycle checks are schema-backed and actionable.
[ ] Digital Marketing build plan includes backend-api and web.
[ ] `pnpm product digital-marketing build` delegates to lifecycle.
[ ] `pnpm product yappc build` remains legacy.
[ ] `pnpm product data-cloud build` remains legacy.
[ ] Tests exist for planner, executor, adapters, conformance scripts, and CLI behavior.
```

---

# 9. Do Not Do

Do not:

```text
- migrate YAPPC into lifecycle
- migrate Data Cloud into lifecycle
- hide incomplete deployment behind a successful command
- keep duplicate lifecycle type definitions
- let adapters write manifests as side effects
- introduce more local dependency/version drift
- add broad abstractions without Digital Marketing proving them
- add shell-string command execution
- allow partial/planned adapters in enabled product lifecycle
- treat warnings as success for required outputs
```

---

# 10. Summary

The target commit already made a useful first move toward Kernel product lifecycle orchestration. The next implementation should not restart. It should stabilize and harden what exists.

The correct next direction is:

```text
1. Scope lifecycle to Digital Marketing only for now.
2. Explicitly exclude YAPPC and Data Cloud.
3. Make lifecycle planning/execution package-owned, not script-owned.
4. Unify duplicated contracts.
5. Make adapters safe, typed, and output-validating.
6. Make artifacts and deployment first-class but fail-closed.
7. Keep legacy product build/dev/test behavior for non-lifecycle products.
```

This gives Kernel a real product-lifecycle foundation without turning Kernel into a god product and without forcing every product into the framework before the platform is stable.
