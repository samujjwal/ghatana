# Kernel Product Lifecycle Platform Stabilization & Enhancement Plan

**Repository:** `samujjwal/ghatana`  
**Target commit/head:** `30e856fd0104a1c39b43eecc2736acdf42932676`  
**Purpose:** provide an implementation-ready, file-by-file plan to stabilize and enhance the Kernel product lifecycle platform based on the current codebase and the repo rules in `.github/copilot-instructions.md`.

---

## 0. Executive summary

At this commit, Kernel lifecycle support is already partially introduced. The codebase now contains lifecycle fields in the canonical registry/schema, lifecycle profiles, toolchain adapter registry, early CLI scripts, and early TypeScript packages for lifecycle, toolchains, artifacts, deployment, and release. Digital Marketing also already has a `kernel-product.yaml` and `lifecycle.local.yaml`.

So the work should **not** restart from concept. The work should **stabilize, normalize, harden, test, and make executable** what already exists.

The core target remains:

```text
Kernel owns product lifecycle and contracts.
Toolchain adapters own concrete tool execution.
Products own business behavior and product-specific configuration.
```

A normal product developer should eventually use:

```bash
kernel product plan digital-marketing build
kernel product dev digital-marketing
kernel product validate digital-marketing
kernel product test digital-marketing
kernel product build digital-marketing
kernel product package digital-marketing
kernel product deploy digital-marketing --env local
kernel product verify digital-marketing --env local
```

They should not need to care whether Gradle, pnpm, Vite, Docker, Compose, Kubernetes, Helm, Terraform, Vitest, or Playwright is used internally.

---

## 1. Repo rules that govern this work

The implementation must strictly follow `.github/copilot-instructions.md`.

The most relevant rules are:

```text
- Reuse before creating.
- Do not deviate from the existing Ghatana repo shape.
- Keep boundaries explicit.
- No silent failures.
- No hardcoded secrets or unsafe defaults.
- Zero-warning mindset.
- Type safety is implementation-time, not later.
- Tests are part of the change.
- Public Java APIs require @doc.* documentation tags.
- Prefer existing dependencies.
- Make observability part of the feature.
```

For this initiative that means:

```text
- Extend existing kernel-lifecycle, kernel-toolchains, kernel-artifacts, kernel-deployment, and kernel-release packages.
- Do not create parallel lifecycle packages.
- Keep product-specific lifecycle config under products/<productId>/.
- Keep shared Kernel packages product-neutral.
- Use strict TypeScript and typed schemas.
- Use Zod or JSON schema at config boundaries.
- Never use shell-string execution for tool commands.
- Do not leave TODO validators in production paths.
- Add unit tests and contract tests for every behavior change.
```

---

## 2. Current-state findings at commit `30e856fd0104a1c39b43eecc2736acdf42932676`

### 2.1 Strong existing foundations

The following should be preserved and hardened:

```text
config/canonical-product-registry.json
config/canonical-product-registry-schema.json
config/product-lifecycle-profiles.json
config/toolchain-adapter-registry.json
scripts/kernel-product.mjs
scripts/run-product-task.mjs
scripts/check-product-lifecycle-contracts.mjs
platform/typescript/kernel-lifecycle
platform/typescript/kernel-toolchains
platform/typescript/kernel-artifacts
platform/typescript/kernel-deployment
platform/typescript/kernel-release
products/digital-marketing/kernel-product.yaml
products/digital-marketing/lifecycle.local.yaml
```

### 2.2 Critical gaps to fix first

#### Gap 1 — Build/test/dev scripts are plan-only

Root `package.json` contains generated scripts such as:

```json
"build:digital-marketing": "node scripts/kernel-product.mjs plan digital-marketing build"
```

That prints a plan instead of executing a build. Imperative script names must execute. Planning scripts must be explicitly named `plan:*`.

#### Gap 2 — `run-product-task.mjs` delegates build/test/dev to `plan`

`pnpm product digital-marketing build` currently delegates lifecycle-enabled tasks to:

```text
node scripts/kernel-product.mjs plan digital-marketing build
```

This must become execution:

```text
node scripts/kernel-product.mjs product build digital-marketing
```

#### Gap 3 — CLI imports are fragile

`scripts/kernel-product.mjs` imports:

```text
platform/typescript/kernel-lifecycle/src/... .js
platform/typescript/kernel-release/dist/index.js
```

This mixes source and dist imports and can fail in a clean checkout.

#### Gap 4 — hardcoded local absolute paths

`ProductLifecyclePlanner.ts` defaults to:

```text
/Users/samujjwal/Development/ghatana/config
```

This breaks CI, other developers, and any non-local execution.

#### Gap 5 — unsafe shell-string execution

Adapters use `exec` with joined command strings. Commands must be executed with `spawn`/`execFile` and explicit args arrays.

#### Gap 6 — ESM/CommonJS mixing

Adapters are ESM TypeScript but use `require('node:child_process')`. Replace with ESM imports.

#### Gap 7 — wrong pnpm package path handling

`PnpmViteReactAdapter` uses `packagePath` as the `pnpm --dir` value. For Digital Marketing, packagePath points to `package.json`; `pnpm --dir` must receive the package directory.

#### Gap 8 — wrong Gradle cwd logic

`GradleJavaServiceAdapter.resolveModulePath()` converts `:products:digital-marketing:dm-api` into `/products/digital-marketing/dm-api`, which is not the correct working directory. Gradle should run from repo root with module task args.

#### Gap 9 — validators are placeholders

`validateOutputs()` returns valid unconditionally in adapters. This violates no-silent-failures.

#### Gap 10 — artifact manifests are untrustworthy

Artifact manifest generation uses empty hashes and size `0`. Hashes and sizes must be computed from real files/directories.

#### Gap 11 — registry/schema drift

The schema requires artifact declarations with `type` and `packaging`, where type is semantic, but registry entries use shapes like:

```json
"backend-api": {
  "type": "jar",
  "surface": "backend-api"
}
```

This conflicts with the schema.

#### Gap 12 — duplicate lifecycle types

Lifecycle phase and surface types are duplicated between `kernel-lifecycle` and `kernel-toolchains`. There must be one source of truth.

#### Gap 13 — checks are shallow

`check-product-lifecycle-contracts.mjs` checks only basic existence. It must validate config, adapters, phases, surfaces, artifacts, deployment targets, and health checks.

---

## 3. Stabilization strategy

Do the work in this order:

```text
P0 — Correct command semantics, registry/schema drift, and hardcoded paths.
P1 — Make lifecycle planning schema-backed and portable.
P2 — Make adapters safe and execution-correct.
P3 — Make artifact validation real.
P4 — Make Digital Marketing build/test/dev work through Kernel.
P5 — Add package/local-deploy dry-run.
P6 — Expand lifecycle conformance.
P7 — Migrate PHR and FlashIt after Digital Marketing is clean.
```

Do not add more planned adapters before stabilizing the current path.

---

# 4. File-by-file implementation plan

---

## 4.1 `.github/copilot-instructions.md`

### Action

Do not modify unless a repo-wide governance update is explicitly needed.

### Implementation requirement

Use this file as binding guidance for every code change.

### Validation

Each PR should state that the change follows:

```text
- reuse-first
- explicit boundaries
- no silent failures
- strict TypeScript
- test coverage
- observability
- no unsafe command execution
```

---

## 4.2 `config/canonical-product-registry-schema.json`

### Current issue

Lifecycle fields exist, but artifact and lifecycle migration semantics are not strong enough.

### Required changes

Add lifecycle status:

```json
"lifecycleStatus": {
  "type": "string",
  "enum": ["disabled", "planned", "partial", "enabled"]
}
```

Add lifecycle migration details:

```json
"lifecycleMigration": {
  "type": "object",
  "properties": {
    "status": {
      "type": "string",
      "enum": ["not-started", "partial", "ready", "blocked"]
    },
    "notes": { "type": "string" },
    "blockingFiles": {
      "type": "array",
      "items": { "type": "string" }
    }
  }
}
```

Replace hardcoded artifact keys with dynamic surface keys:

```json
"ProductArtifactsConfiguration": {
  "type": "object",
  "additionalProperties": {
    "$ref": "#/definitions/ProductArtifactDeclaration"
  }
}
```

Normalize artifact declaration:

```json
"ProductArtifactDeclaration": {
  "type": "object",
  "required": ["type", "packaging"],
  "additionalProperties": false,
  "properties": {
    "type": {
      "type": "string",
      "enum": [
        "jvm-service",
        "jvm-library",
        "node-service",
        "static-web-bundle",
        "container-image",
        "mobile-bundle",
        "sdk-package",
        "domain-pack",
        "test-report",
        "coverage-report"
      ]
    },
    "packaging": {
      "type": "string",
      "enum": [
        "jar",
        "distribution",
        "static-files",
        "container",
        "npm",
        "maven",
        "apk",
        "aab",
        "ipa",
        "json",
        "xml"
      ]
    },
    "required": { "type": "boolean", "default": true }
  }
}
```

### Acceptance criteria

```text
- Digital Marketing registry entry validates.
- Products not fully migrated can be marked partial/planned.
- Artifact declarations no longer conflict with schema.
```

---

## 4.3 `config/canonical-product-registry.json`

### Current issue

Several products contain lifecycle fields, but the implementation is only safe for Digital Marketing pilot right now. Artifact declarations also drift from schema.

### Required changes

For `digital-marketing`, set:

```json
"lifecycleStatus": "enabled",
"lifecycleProfile": "standard-web-api-product",
"lifecycleConfigPath": "products/digital-marketing/kernel-product.yaml",
"artifacts": {
  "backend-api": {
    "type": "jvm-service",
    "packaging": "jar",
    "required": true
  },
  "web": {
    "type": "static-web-bundle",
    "packaging": "static-files",
    "required": true
  }
}
```

For all other products with lifecycle fields, set either:

```json
"lifecycleStatus": "partial"
```

or:

```json
"lifecycleStatus": "planned"
```

until product-specific `kernel-product.yaml`, adapters, artifacts, and health checks are validated.

Add migration notes for partial products:

```json
"lifecycleMigration": {
  "status": "partial",
  "notes": "Lifecycle registry entry exists, but execution path is not yet validated."
}
```

### Acceptance criteria

```text
- Only Digital Marketing is marked lifecycle enabled initially.
- Partial products do not fail execution checks unless explicitly selected.
- Artifact declarations match schema.
```

---

## 4.4 `config/product-lifecycle-profiles.json`

### Current issue

Profiles include future default adapters such as Kubernetes, Helm, Terraform, Xcode, Gradle Android, etc. Some are declared before implementation is stable.

### Required changes

Add profile status:

```json
"status": "stable" | "experimental"
```

For `standard-web-api-product`, keep stable defaults only:

```json
"defaultAdapters": {
  "backend-api": "gradle-java-service",
  "web": "pnpm-vite-react",
  "deploy.local": "compose-local"
}
```

Move future deployment adapters to a separate future profile:

```text
standard-web-api-product-kubernetes
```

Add expected artifact requirements:

```json
"expectedArtifacts": {
  "build": {
    "backend-api": ["jvm-service"],
    "web": ["static-web-bundle"]
  },
  "package": {
    "backend-api": ["container-image"],
    "web": ["container-image"]
  }
}
```

### Acceptance criteria

```text
- Stable profiles reference only adapters safe for default use.
- Experimental/planned adapters are not used by default.
- Required gates and expected artifacts are phase-specific.
```

---

## 4.5 `config/toolchain-adapter-registry.json`

### Current issue

Many adapters are declared, but not all implementations are present or stable.

### Required changes

Add to every adapter:

```json
"status": "implemented" | "partial" | "planned",
"safeForDefault": true | false,
"tests": ["path/to/test.ts"]
```

Near-term statuses:

```text
gradle-java-service -> partial, safeForDefault true only after Sprint 2
pnpm-vite-react -> partial, safeForDefault true only after Sprint 2
docker-buildx -> partial/planned, safeForDefault false until package works
compose-local -> partial/planned, safeForDefault false until deploy dry-run works
kubernetes/helm/terraform -> planned, safeForDefault false
xcode-ios/gradle-android -> planned, safeForDefault false
```

### Acceptance criteria

```text
- Default lifecycle profiles never use planned adapters.
- Checker fails if safeForDefault references missing implementation/tests.
```

---

## 4.6 `config/product-lifecycle-profiles-schema.json`

### Action

Add or complete this schema.

### Must validate

```text
version
profiles
profile.status
profile.defaultSurfaces
profile.requiredGates
profile.optionalGates
profile.defaultAdapters
profile.expectedArtifacts
```

### Acceptance criteria

```bash
node scripts/check-product-lifecycle-profiles.mjs
```

validates the profile registry and fails on unknown/default planned adapters.

---

## 4.7 `config/toolchain-adapter-registry-schema.json`

### Action

Add or complete this schema.

### Must validate

```text
adapter id format
kind enum
supportedSurfaceTypes
supportedPhases
requires
outputs
implementation
status
safeForDefault
tests
```

---

# 5. Script and CLI plan

## 5.1 `scripts/kernel-product.mjs`

### Current issues

```text
- Supports plan/release/promote/rollback but not direct dev/build/test/package/deploy/verify execution.
- Imports source and dist paths inconsistently.
- Release/promote/rollback build fake manifests instead of requiring real manifests.
```

### Required changes

Support canonical grammar:

```bash
node scripts/kernel-product.mjs product plan <productId> <phase> [options]
node scripts/kernel-product.mjs product dev <productId> [options]
node scripts/kernel-product.mjs product validate <productId> [options]
node scripts/kernel-product.mjs product test <productId> [options]
node scripts/kernel-product.mjs product build <productId> [options]
node scripts/kernel-product.mjs product package <productId> [options]
node scripts/kernel-product.mjs product deploy <productId> --env local [options]
node scripts/kernel-product.mjs product verify <productId> --env local [options]
node scripts/kernel-product.mjs product release <productId> [options]
node scripts/kernel-product.mjs product promote <productId> --from staging --to prod
node scripts/kernel-product.mjs product rollback <productId> --env prod
```

Keep compatibility:

```bash
node scripts/kernel-product.mjs plan digital-marketing build
node scripts/kernel-product.mjs build digital-marketing
```

Options:

```text
--surface <surface>
--surfaces <surface1,surface2>
--env <environment>
--dry-run
--json
--output-dir <path>
--source-ref <git ref>
--artifact <manifest path>
```

Import packages consistently. Prefer built package exports:

```js
import { ProductLifecyclePlanner, ProductLifecycleExecutor } from '@ghatana/kernel-lifecycle';
```

If package is not built, fail with an actionable message:

```text
Kernel lifecycle package is not built. Run: pnpm build:kernel-lifecycle-platform
```

Do not import `src/*.js` as if TypeScript source has already been emitted.

### Acceptance criteria

```bash
node scripts/kernel-product.mjs product plan digital-marketing build --json
node scripts/kernel-product.mjs product build digital-marketing --dry-run
node scripts/kernel-product.mjs product build digital-marketing --surface web --dry-run
```

all work with correct semantics.

---

## 5.2 `scripts/run-product-task.mjs`

### Current issue

Lifecycle-enabled build/test/dev delegate to `plan`.

### Required change

Replace delegation with execution:

```js
const lifecycleArgs = ['product', phase, productId];
```

Surface option:

```js
if (requestedSurface) lifecycleArgs.push('--surface', normalizeSurfaceName(requestedSurface));
```

Keep a plan pathway:

```bash
pnpm product digital-marketing plan build
pnpm product digital-marketing build --plan
```

### Acceptance criteria

```bash
pnpm product digital-marketing build --dry-run
```

runs lifecycle dry-run execution, not old plan-only output.

---

## 5.3 `scripts/generate-product-registry-artifacts.mjs`

### Current issue

Generated lifecycle scripts call `plan` for build/test/dev/package/deploy.

### Required changes

Generate imperative execution scripts:

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

Surface scripts:

```json
"build:digital-marketing-web": "node scripts/kernel-product.mjs product build digital-marketing --surface web",
"build:digital-marketing-gateway": "node scripts/kernel-product.mjs product build digital-marketing --surface backend-api"
```

### Acceptance criteria

```bash
pnpm check:product-registry-artifacts
```

fails when generated package scripts drift.

---

## 5.4 `package.json`

### Current issue

Generated product lifecycle scripts are plan-only. Kernel package build ordering is also unclear.

### Required changes

Add:

```json
"build:kernel-lifecycle-platform": "pnpm --dir platform/typescript/kernel-artifacts build && pnpm --dir platform/typescript/kernel-toolchains build && pnpm --dir platform/typescript/kernel-lifecycle build && pnpm --dir platform/typescript/kernel-deployment build && pnpm --dir platform/typescript/kernel-release build"
```

Update:

```json
"check:kernel-platform-lifecycle": "pnpm build:kernel-lifecycle-platform && node ./scripts/check-kernel-platform-lifecycle.mjs"
```

Regenerate product scripts from `generate-product-registry-artifacts.mjs`.

### Acceptance criteria

```bash
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-platform-lifecycle
pnpm build:digital-marketing --dry-run
```

or equivalent commands work.

---

# 6. `platform/typescript/kernel-lifecycle` plan

## 6.1 `platform/typescript/kernel-lifecycle/package.json`

### Current issue

Package uses direct versions that may drift from root catalog/overrides.

### Required changes

Use repo catalog where possible:

```json
"dependencies": {
  "yaml": "^2.3.4",
  "zod": "catalog:"
},
"devDependencies": {
  "@types/node": "catalog:",
  "typescript": "catalog:",
  "vitest": "catalog:",
  "eslint": "catalog:"
}
```

If TypeScript ESLint catalog entries are missing, add them via repo dependency governance rather than hardcoding isolated versions.

---

## 6.2 `platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePhase.ts`

### Current issue

This file contains too many unrelated models and overlaps with `kernel-toolchains` types.

### Required changes

Split into focused files:

```text
src/domain/ProductLifecyclePhase.ts
src/domain/ProductSurfaceType.ts
src/domain/ProductSurface.ts
src/domain/ProductLifecyclePlan.ts
src/domain/ProductLifecycleResult.ts
src/domain/ProductLifecycleStep.ts
src/domain/ProductLifecycleEvent.ts
src/domain/ProductGate.ts
src/domain/ProductEnvironment.ts
src/domain/ProductArtifact.ts
src/domain/ProductFailurePolicy.ts
```

Add schema-backed models:

```text
src/schemas/ProductLifecyclePlanSchema.ts
src/schemas/ProductLifecycleResultSchema.ts
src/schemas/KernelProductConfigurationSchema.ts
src/schemas/LifecycleProfileSchema.ts
```

### Acceptance criteria

```bash
pnpm --dir platform/typescript/kernel-lifecycle typecheck
pnpm --dir platform/typescript/kernel-lifecycle test
```

pass with no `any` and no implicit types.

---

## 6.3 `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`

### Current issue

Hardcoded local paths, direct unvalidated file reads, minimal plan output, no adapter/gate/artifact validation.

### Required changes

Constructor:

```ts
export interface ProductLifecyclePlannerOptions {
  readonly repoRoot: string;
  readonly registryPath?: string;
  readonly lifecycleProfilesPath?: string;
  readonly toolchainRegistryPath?: string;
  readonly environmentDirectory?: string;
}
```

Add loaders:

```text
src/io/RepoRootResolver.ts
src/io/CanonicalProductRegistryLoader.ts
src/io/KernelProductConfigurationLoader.ts
src/io/LifecycleProfileLoader.ts
src/io/ToolchainAdapterRegistryLoader.ts
src/io/EnvironmentLoader.ts
```

Plan output must include:

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

Planner must fail on:

```text
unknown product
lifecycle disabled
missing lifecycleConfigPath
productId mismatch
unknown phase
unknown surface
unknown adapter
adapter unsupported phase
adapter unsupported surface type
missing adapter required config
missing artifact expectations
```

### Acceptance criteria

```bash
node scripts/kernel-product.mjs product plan digital-marketing build --json
```

returns a full schema-backed plan.

---

## 6.4 Add `ProductLifecycleExecutor`

### Add

```text
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleStepRunner.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleRunContext.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleOutputWriter.ts
```

### Responsibilities

```text
- Execute validated plans.
- Use ToolchainAdapterRegistry to select adapters.
- Respect sequential/parallel dependencies.
- Fail closed on required failures.
- Write lifecycle-result.json.
- Write artifact manifest when phase requires it.
```

### Acceptance criteria

```bash
node scripts/kernel-product.mjs product build digital-marketing --dry-run
```

prints executable dry-run steps.

---

## 6.5 Lifecycle tests

### Add

```text
platform/typescript/kernel-lifecycle/src/planning/__tests__/ProductLifecyclePlanner.test.ts
platform/typescript/kernel-lifecycle/src/planning/__tests__/SurfaceSelector.test.ts
platform/typescript/kernel-lifecycle/src/planning/__tests__/LifecycleProfileResolver.test.ts
platform/typescript/kernel-lifecycle/src/planning/__tests__/ToolchainResolver.test.ts
platform/typescript/kernel-lifecycle/src/execution/__tests__/ProductLifecycleExecutor.test.ts
platform/typescript/kernel-lifecycle/src/schemas/__tests__/KernelProductConfigurationSchema.test.ts
```

### Test fixtures

```text
platform/typescript/kernel-lifecycle/src/__fixtures__/sample-repo/config/canonical-product-registry.json
platform/typescript/kernel-lifecycle/src/__fixtures__/sample-repo/config/product-lifecycle-profiles.json
platform/typescript/kernel-lifecycle/src/__fixtures__/sample-repo/config/toolchain-adapter-registry.json
platform/typescript/kernel-lifecycle/src/__fixtures__/sample-repo/products/sample/kernel-product.yaml
```

### Acceptance criteria

Tests do not call real Gradle or pnpm.

---

# 7. `platform/typescript/kernel-toolchains` plan

## 7.1 `src/ToolchainAdapter.ts`

### Current issue

Lifecycle types duplicated.

### Required changes

Import shared lifecycle types from `@ghatana/kernel-lifecycle` or introduce `@ghatana/kernel-contracts` only if circular dependencies require it.

### Acceptance criteria

There is exactly one source of truth for lifecycle phase and surface type unions.

---

## 7.2 Add shared command execution

### Add

```text
platform/typescript/kernel-toolchains/src/execution/CommandRunner.ts
platform/typescript/kernel-toolchains/src/execution/SpawnCommandRunner.ts
platform/typescript/kernel-toolchains/src/execution/FakeCommandRunner.ts
platform/typescript/kernel-toolchains/src/execution/CommandResult.ts
```

### Interface

```ts
export interface CommandRunner {
  run(command: string, args: readonly string[], options: CommandRunOptions): Promise<CommandResult>;
}
```

### Rules

```text
- no shell by default
- command and args are separate
- cwd is explicit
- timeout is supported
- stdout/stderr truncation is explicit
- tests use FakeCommandRunner
```

---

## 7.3 `src/adapters/GradleJavaServiceAdapter.ts`

### Current issues

```text
- uses require in ESM
- uses exec shell string
- wrong cwd
- wrong module path resolution
- placeholder output validation
- placeholder artifact extraction
- empty artifact hash
```

### Required changes

Constructor:

```ts
export interface GradleJavaServiceAdapterOptions {
  readonly repoRoot: string;
  readonly commandRunner: CommandRunner;
  readonly artifactResolver: ProductArtifactResolver;
}
```

Plan command:

```text
command: ./gradlew
args: [":products:digital-marketing:dm-api:build", "--no-daemon"]
cwd: repoRoot
```

For dev:

```text
args: [":products:digital-marketing:dm-api:runDmosApiServer", "--no-daemon"]
```

Validation:

```text
- build/libs/*.jar exists for build/package phases
- reports/tests exists after test/check
- coverage report exists when configured
```

Do not parse stdout for artifacts. Resolve expected artifacts from lifecycle config.

---

## 7.4 `src/adapters/PnpmViteReactAdapter.ts`

### Current issues

```text
- uses require in ESM
- uses exec shell string
- uses packagePath as directory
- placeholder output validation
- placeholder artifact extraction
```

### Required changes

If `packagePath` is `products/digital-marketing/ui/package.json`, use:

```text
packageDirectory = products/digital-marketing/ui
command = pnpm
args = ["--dir", "products/digital-marketing/ui", "run", "build"]
cwd = repoRoot
```

Validation:

```text
- package.json exists
- requested script exists
- dist exists after build
- dist/index.html exists after Vite build
```

Artifact:

```text
type: static-web-bundle
packaging: static-files
path: products/digital-marketing/ui/dist
```

---

## 7.5 Adapter tests

### Add

```text
platform/typescript/kernel-toolchains/src/adapters/__tests__/GradleJavaServiceAdapter.test.ts
platform/typescript/kernel-toolchains/src/adapters/__tests__/PnpmViteReactAdapter.test.ts
platform/typescript/kernel-toolchains/src/execution/__tests__/SpawnCommandRunner.test.ts
platform/typescript/kernel-toolchains/src/__tests__/ToolchainAdapterRegistry.test.ts
```

### Required cases

```text
- Gradle adapter builds correct args.
- Gradle adapter uses repo root cwd.
- Gradle adapter rejects missing gradleModule.
- Pnpm adapter uses package directory, not package.json path.
- Pnpm adapter rejects missing packagePath/script.
- Output validation fails on missing artifacts.
```

---

# 8. `platform/typescript/kernel-artifacts` plan

## 8.1 `src/domain/ArtifactManifest.ts`

### Current issues

```text
- artifact type model conflicts with registry schema
- md5 allowed
- hash can be empty
- size can be fake
- found defaults to false but is not resolved from filesystem
```

### Required changes

Use semantic artifact type and packaging:

```ts
export type ArtifactType =
  | 'jvm-service'
  | 'jvm-library'
  | 'node-service'
  | 'static-web-bundle'
  | 'container-image'
  | 'mobile-bundle'
  | 'sdk-package'
  | 'domain-pack'
  | 'test-report'
  | 'coverage-report'
  | 'source-map'
  | 'documentation';

export type ArtifactPackaging =
  | 'jar'
  | 'distribution'
  | 'static-files'
  | 'container'
  | 'npm'
  | 'maven'
  | 'apk'
  | 'aab'
  | 'ipa'
  | 'json'
  | 'xml';
```

Remove md5:

```ts
algorithm: 'sha256' | 'sha512'
```

### Add

```text
platform/typescript/kernel-artifacts/src/fingerprint/ArtifactFingerprintCalculator.ts
```

Responsibilities:

```text
- calculate SHA-256 for files
- calculate deterministic SHA-256 for directories using sorted relative file paths
- calculate real sizeBytes
- fail for missing required artifacts
```

### Acceptance criteria

```text
- no empty hashes
- no fake sizeBytes
- required missing artifacts fail validation
```

---

## 8.2 Artifact schemas

### Add/update

```text
config/schemas/product-artifact-manifest.schema.json
config/schemas/product-build-manifest.schema.json
config/schemas/product-release-manifest.schema.json
config/schemas/product-deployment-manifest.schema.json
```

### Acceptance criteria

Manifests written by Kernel packages validate against schemas.

---

# 9. `platform/typescript/kernel-deployment` plan

## 9.1 Deployment MVP scope

Do not implement production Kubernetes first. Start with local deploy dry-run.

### Add

```text
platform/typescript/kernel-deployment/src/adapters/ComposeDeploymentAdapter.ts
platform/typescript/kernel-deployment/src/adapters/__tests__/ComposeDeploymentAdapter.test.ts
platform/typescript/kernel-deployment/src/planning/DeploymentPlanResolver.ts
platform/typescript/kernel-deployment/src/planning/__tests__/DeploymentPlanResolver.test.ts
```

### Behavior

```text
- deploy consumes package artifact manifest
- deploy validates environment
- deploy validates compose file path
- deploy validates env example path
- deploy validates health check definitions
- deploy emits deployment-manifest.json in dry-run and real mode
```

### Acceptance criteria

```bash
node scripts/kernel-product.mjs product deploy digital-marketing --env local --dry-run
```

prints a valid deployment plan.

---

# 10. `platform/typescript/kernel-release` plan

## 10.1 Current issue

`scripts/kernel-product.mjs` creates synthetic release/promote/rollback data.

### Required changes

Release manager must consume real manifests:

```text
artifact-manifest.json
deployment-manifest.json
release-manifest.json
```

### Update/add

```text
platform/typescript/kernel-release/src/ProductReleaseManager.ts
platform/typescript/kernel-release/src/ProductPromotionPlanManager.ts
platform/typescript/kernel-release/src/ProductRollbackPlanManager.ts
platform/typescript/kernel-release/src/ProductApprovalGateManager.ts
```

### Acceptance criteria

Release/promote/rollback fail when required manifests do not exist or do not validate.

---

# 11. Digital Marketing pilot plan

## 11.1 `products/digital-marketing/kernel-product.yaml`

### Current issue

Good start, but artifacts, gates, package, and deploy are incomplete.

### Required changes

Add artifacts:

```yaml
artifacts:
  build:
    backend-api:
      type: jvm-service
      packaging: jar
      required: true
      paths:
        - products/digital-marketing/dm-api/build/libs/*.jar
    web:
      type: static-web-bundle
      packaging: static-files
      required: true
      paths:
        - products/digital-marketing/ui/dist
  package:
    backend-api:
      type: container-image
      packaging: container
      required: true
    web:
      type: container-image
      packaging: container
      required: true
```

Add gates:

```yaml
gates:
  validate:
    - registry-validation
    - manifest-validation
    - lifecycle-contract-validation
    - bridge-compliance
    - dmos-boundary-workflow-coverage
  build:
    - backend-check
    - web-route-contract
    - web-typecheck
    - web-bundle-budget
```

Add deploy:

```yaml
deployment:
  local:
    target: compose-local
    composeFile: products/digital-marketing/deploy/local.compose.yaml
    envFile: products/digital-marketing/deploy/local.env
    envExampleFile: products/digital-marketing/deploy/local.env.example
    healthChecks:
      - backend-api
      - web
```

### Acceptance criteria

Digital Marketing lifecycle config fully describes dev/test/build/package/deploy local without implicit heuristics.

---

## 11.2 `products/digital-marketing/lifecycle.local.yaml`

### Current issue

References local env file but does not distinguish example vs real local file.

### Required changes

Add:

```yaml
deployment:
  target: compose-local
  composeFile: products/digital-marketing/deploy/local.compose.yaml
  envFile: products/digital-marketing/deploy/local.env
  envExampleFile: products/digital-marketing/deploy/local.env.example
```

Do not commit real `local.env`.

---

## 11.3 Add Digital Marketing deploy files

### Add

```text
products/digital-marketing/deploy/local.compose.yaml
products/digital-marketing/deploy/local.env.example
products/digital-marketing/deploy/health-checks.json
```

### `local.env.example`

```env
DMOS_API_PORT=8080
DMOS_WEB_PORT=5173
DATABASE_URL=jdbc:postgresql://localhost:5432/digital_marketing
LOG_LEVEL=debug
VITE_API_BASE_URL=http://localhost:8080
VITE_ENV=local
```

No secrets.

---

## 11.4 `products/digital-marketing/dm-api/build.gradle.kts`

### Current state

Already has useful tasks and quality gates.

### Required change

Prefer no changes initially. Use existing:

```text
check
test
build
runDmosApiServer
```

Only add a lifecycle-specific Gradle task if needed after adapter testing.

---

## 11.5 `products/digital-marketing/ui/package.json`

### Current state

Already has strong scripts:

```text
dev
build
test
test:route-contract
type-check
```

### Required change

Prefer no change initially. Adapter should use existing scripts.

Optional later:

```json
"kernel:validate": "pnpm run lint && pnpm run type-check && pnpm run test:route-contract"
```

Only add if it reduces adapter special cases without duplication.

---

# 12. Conformance scripts plan

## 12.1 `scripts/check-product-lifecycle-contracts.mjs`

### Current issue

Shallow validation.

### Required validation

```text
- lifecycleStatus is valid
- enabled products have lifecycleProfile/lifecycleConfigPath/lifecycle/toolchain/artifacts
- lifecycleConfigPath exists
- YAML parses
- YAML productId matches registry key
- profile exists
- phase references are valid
- surface references exist
- adapter exists
- adapter supports requested phase
- adapter supports requested surface type
- adapter required fields exist
- artifact declarations match schema
- deployment targets exist for deployable products
- health checks exist for deployable surfaces
```

### Acceptance criteria

Script catches current registry/schema drift and missing product lifecycle files.

---

## 12.2 `scripts/check-toolchain-adapter-contracts.mjs`

### Required validation

```text
- every implemented/partial adapter has implementation path
- implementation file exists
- test file exists for implemented adapters
- planned adapters are safeForDefault=false
- stable lifecycle profiles do not reference planned adapters
```

---

## 12.3 `scripts/check-product-artifact-contracts.mjs`

### Required validation

```text
- artifact type values match schema
- enabled products declare build artifacts
- deployable products declare package/deployment artifacts
- generated manifests cannot contain empty hashes
```

---

## 12.4 `scripts/check-product-deployment-contracts.mjs`

### Required validation

```text
- deployable products have deployment target
- local target has compose file or planned marker
- health checks exist
- prod target requires rollback policy and approval policy
```

---

## 12.5 Add `scripts/check-kernel-platform-lifecycle.mjs`

One orchestrator check script that runs all lifecycle platform checks and prints grouped actionable failures.

### Acceptance criteria

`pnpm check:kernel-platform-lifecycle` uses this script.

---

# 13. Product shell and UI plan

Do not start with UI. Add lifecycle UI after core outputs are stable.

Later update:

```text
platform/typescript/product-shell/src/contracts/product-lifecycle.ts
platform/typescript/product-shell/src/contracts/product-artifact.ts
platform/typescript/product-shell/src/contracts/product-deployment.ts
platform/typescript/product-shell/src/contracts/product-environment.ts
```

Add components later:

```text
ProductLifecycleStatusPanel
ProductLifecyclePlanView
ProductArtifactList
ProductDeploymentStatusCard
ProductEnvironmentBadge
ProductHealthCheckPanel
ProductConformanceSummaryCard
```

Acceptance criteria later:

```text
Products do not create custom lifecycle/deployment status UI.
```

---

# 14. Documentation plan

## 14.1 Add/update docs

```text
docs/kernel/PRODUCT_LIFECYCLE_CONTRACT.md
docs/kernel/PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md
docs/kernel/PRODUCT_ARTIFACT_CONTRACT.md
docs/kernel/PRODUCT_DEPLOYMENT_CONTRACT.md
docs/kernel/PRODUCT_LIFECYCLE_MIGRATION_GUIDE.md
```

## 14.2 Update existing docs

```text
docs/kernel/KERNEL_CONSUMPTION_GUIDE.md
docs/kernel/KERNEL_PRODUCT_BOUNDARY.md
docs/kernel/PRODUCT_CONFORMANCE_SPEC.md
docs/kernel/CI_GATE_MATRIX.md
```

## 14.3 Documentation rules

Docs must clearly distinguish:

```text
plan -> no execution
dry-run -> execution path without running tools
build/test/dev/package/deploy -> execution
```

Docs must not claim Kubernetes/prod deployment support until it is real.

---

# 15. Sprint plan

## Sprint 1 — Stabilize lifecycle command semantics and config validation

### Files

```text
scripts/kernel-product.mjs
scripts/run-product-task.mjs
scripts/generate-product-registry-artifacts.mjs
package.json
config/canonical-product-registry-schema.json
config/canonical-product-registry.json
config/product-lifecycle-profiles.json
config/toolchain-adapter-registry.json
platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts
platform/typescript/kernel-lifecycle/src/domain/*
```

### Deliverables

```text
- no hardcoded absolute paths
- build/test/dev scripts execute lifecycle, not plan
- plan remains available explicitly
- registry/schema artifact mismatch fixed
- Digital Marketing plan works
```

### Validation

```bash
pnpm build:kernel-lifecycle-platform
node scripts/kernel-product.mjs product plan digital-marketing build --json
node scripts/kernel-product.mjs product build digital-marketing --dry-run
pnpm product digital-marketing build --dry-run
pnpm check:product-lifecycle-contracts
```

---

## Sprint 2 — Stabilize adapters and execution

### Files

```text
platform/typescript/kernel-toolchains/src/ToolchainAdapter.ts
platform/typescript/kernel-toolchains/src/execution/CommandRunner.ts
platform/typescript/kernel-toolchains/src/execution/SpawnCommandRunner.ts
platform/typescript/kernel-toolchains/src/execution/FakeCommandRunner.ts
platform/typescript/kernel-toolchains/src/adapters/GradleJavaServiceAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/PnpmViteReactAdapter.ts
platform/typescript/kernel-toolchains/src/ToolchainAdapterRegistry.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts
```

### Validation

```bash
node scripts/kernel-product.mjs product build digital-marketing --surface web --dry-run
node scripts/kernel-product.mjs product build digital-marketing --surface backend-api --dry-run
node scripts/kernel-product.mjs product build digital-marketing --surface web
node scripts/kernel-product.mjs product build digital-marketing --surface backend-api
```

---

## Sprint 3 — Artifact correctness

### Files

```text
platform/typescript/kernel-artifacts/src/domain/ArtifactManifest.ts
platform/typescript/kernel-artifacts/src/fingerprint/ArtifactFingerprintCalculator.ts
platform/typescript/kernel-artifacts/src/validator/ProductArtifactValidator.ts
config/schemas/product-artifact-manifest.schema.json
products/digital-marketing/kernel-product.yaml
```

### Validation

```bash
node scripts/kernel-product.mjs product build digital-marketing --surface web
test -f .kernel/out/products/digital-marketing/build/*/artifact-manifest.json
```

---

## Sprint 4 — Package and local deploy dry-run

### Files

```text
platform/typescript/kernel-deployment/*
platform/typescript/kernel-toolchains/src/adapters/DockerBuildxAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/ComposeDeploymentAdapter.ts
products/digital-marketing/deploy/local.compose.yaml
products/digital-marketing/deploy/local.env.example
products/digital-marketing/deploy/health-checks.json
```

### Validation

```bash
node scripts/kernel-product.mjs product package digital-marketing --dry-run
node scripts/kernel-product.mjs product deploy digital-marketing --env local --dry-run
```

---

## Sprint 5 — Lifecycle conformance hardening

### Files

```text
scripts/check-product-lifecycle-contracts.mjs
scripts/check-toolchain-adapter-contracts.mjs
scripts/check-product-artifact-contracts.mjs
scripts/check-product-deployment-contracts.mjs
scripts/check-kernel-platform-lifecycle.mjs
package.json
```

### Validation

```bash
pnpm check:kernel-platform-lifecycle
```

---

## Sprint 6 — Migrate PHR after Digital Marketing is clean

Do not migrate PHR before Digital Marketing lifecycle execution is clean.

### Files

```text
products/phr/kernel-product.yaml
products/phr/lifecycle.local.yaml
products/phr/deploy/local.compose.yaml
products/phr/deploy/local.env.example
config/canonical-product-registry.json
```

---

# 16. Immediate checklist

```text
[ ] Fix registry artifact declarations to match schema.
[ ] Add lifecycleStatus and lifecycleMigration to registry schema.
[ ] Mark Digital Marketing enabled; mark other lifecycle entries partial/planned.
[ ] Remove hardcoded absolute paths from ProductLifecyclePlanner.
[ ] Add RepoRootResolver.
[ ] Split lifecycle domain types into focused files.
[ ] Make kernel-product CLI support product build/test/dev execution.
[ ] Change run-product-task to delegate execution, not plan.
[ ] Regenerate package scripts so build/test/dev execute.
[ ] Add CommandRunner abstraction.
[ ] Replace exec shell strings in adapters.
[ ] Fix Gradle adapter cwd and args.
[ ] Fix pnpm adapter package directory handling.
[ ] Implement real output validation for Gradle and pnpm adapters.
[ ] Implement SHA-256 artifact fingerprinting.
[ ] Add Digital Marketing artifact declarations.
[ ] Add Digital Marketing local deploy files.
[ ] Expand lifecycle conformance checks.
[ ] Add focused unit tests for planner, adapters, artifacts, and checkers.
```

---

# 17. Definition of done for stabilized Kernel lifecycle platform

```text
[ ] `pnpm product digital-marketing build` executes lifecycle build.
[ ] `pnpm build:digital-marketing` executes lifecycle build.
[ ] `plan` commands only plan.
[ ] `--dry-run` produces executable plan without running tools.
[ ] No code has hardcoded developer-local absolute paths.
[ ] No adapter uses shell-string execution.
[ ] No validator returns unconditional valid.
[ ] Artifact manifests have real hashes and sizes.
[ ] Registry artifact declarations validate against schema.
[ ] Digital Marketing can build web and backend through Kernel.
[ ] Digital Marketing emits artifact manifests.
[ ] Local deploy can be planned with health checks.
[ ] Lifecycle conformance catches config drift.
[ ] Tests cover planner, adapters, artifact validation, and check scripts.
[ ] Kernel platform code remains product-neutral.
```

---

# 18. Long-term expansion after stabilization

After Digital Marketing passes end-to-end:

```text
1. Migrate PHR.
2. Migrate FlashIt.
3. Migrate Finance backend-heavy lifecycle.
4. Migrate Data Cloud platform-provider lifecycle.
5. Migrate YAPPC after lifecycle platform is stable enough for product generation.
6. Add Kernel Studio lifecycle UI.
7. Add Kubernetes/Helm/Terraform production deployment.
8. Add release/promotion/rollback approval gates.
9. Add plugin lifecycle hooks.
```

Do not expand to all products before Digital Marketing proves the lifecycle path.

---

# 19. Final implementation principle

Do not build another wrapper around Gradle and pnpm.

Build a Kernel-owned lifecycle platform where:

```text
Kernel owns lifecycle, contracts, gates, artifacts, and deployment orchestration.
Toolchain adapters own concrete tool execution.
Products own business behavior and product-specific configuration.
```

This is the stable path to a real Kernel product lifecycle platform without making Kernel a god product.
