# Kernel Product Lifecycle Platform Stabilization and Digital Marketing Pilot Implementation Plan

**Repository:** `samujjwal/ghatana`  
**Target commit snapshot:** `4649ce641eec73b638db9428498c2409cbe54497`  
**Primary goal:** stabilize and enhance the current Kernel product lifecycle platform so it becomes the default tool-neutral developer experience for product development, build, packaging, deployment, verification, and future release/promotion flows.  
**Pilot product:** `products/digital-marketing`  
**Compatibility lens:** PHR, Finance, FlashIt, Aura, and DCMAAR must remain valid use cases for Kernel, but this implementation phase should not migrate them unless necessary to avoid breakage.

---

## 1. Implementation intent

Kernel must become the product developer’s default interface:

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

Product developers should not need to care whether Digital Marketing uses Gradle, pnpm, Vite, Docker, Compose, or a future Kubernetes/Helm/Terraform deployment path. Those tools should be hidden behind Kernel lifecycle planning and adapter execution.

Power users may override toolchains, but only through Kernel-governed adapter contracts. Overrides must still satisfy Kernel lifecycle, artifact, security, observability, deployment, and rollback contracts.

---

## 2. Non-negotiable repo rules to preserve

This plan follows the repository’s own `.github/copilot-instructions.md` rules:

1. **Reuse before creating.** Extend existing `platform/typescript/kernel-*`, `scripts/*`, `config/*`, and `products/digital-marketing/*` patterns.
2. **Do not introduce a new stack.** Continue with TypeScript packages, pnpm, Gradle, Java 21, ActiveJ, Docker/Compose, and the existing product registry model.
3. **Keep boundaries explicit.** Kernel lifecycle packages must stay product-neutral. Digital Marketing-specific runtime, deploy, health, and data config stays under `products/digital-marketing`.
4. **No silent failures.** Lifecycle planning, execution, adapter validation, output validation, and deployment verification must fail closed with actionable errors.
5. **No hardcoded secrets or unsafe defaults.** Local env examples may use placeholders, but must not encode real-looking credentials as “defaults”.
6. **Strict TypeScript.** All new TypeScript code must be fully typed, no `any`, no untyped parameters, no missing return types.
7. **Tests are part of the change.** Every lifecycle planner/executor/adapter behavior must have focused tests.
8. **Observability is part of the feature.** Lifecycle executions must emit structured plan/result files and human-readable logs.

---

## 3. Current-state picture at the target commit

### 3.1 What already exists and should be preserved

The target commit already includes a strong first slice of the Kernel lifecycle platform:

| Area | Current file(s) | Current state |
|---|---|---|
| Kernel vision/docs | `docs/kernel/00-VISION.md`, `docs/kernel/02-PRODUCT_LIFECYCLE.md`, `docs/kernel/README.md` | Canonical docs already describe Kernel as a product lifecycle platform and define tool abstraction and lifecycle orchestration goals. |
| Product registry | `config/canonical-product-registry.json` | Digital Marketing has `lifecycleStatus: enabled`; PHR, Finance, FlashIt have planned lifecycle metadata; Data Cloud and YAPPC are excluded/removed from this lifecycle slice. |
| Registry schema | `config/canonical-product-registry-schema.json` | Already includes lifecycle fields: `lifecycleProfile`, `lifecycleStatus`, `lifecycleConfigPath`, `lifecycle`, `toolchain`, `artifacts`, `deployment`, `environments`. |
| Lifecycle profiles | `config/product-lifecycle-profiles.json` | Defines `standard-web-api-product`, `backend-only-java-service`, `frontend-only-web-product`, `mobile-plus-api-product`, `sdk-product`, `platform-provider-product`, `shared-service-product`, and `domain-pack-only-product`. |
| Toolchain registry | `config/toolchain-adapter-registry.json` | Declares adapter metadata and maturity flags. `gradle-java-service` and `pnpm-vite-react` are marked implemented and safe for default use. |
| Lifecycle CLI | `scripts/kernel-product.mjs` | Provides `plan` and execution modes, writes `lifecycle-plan.json` and `lifecycle-result.json`. |
| Lifecycle engine | `platform/typescript/kernel-lifecycle` | Contains planner, executor, loaders, validators, result writer, artifact writer, domain types. |
| Toolchain adapters | `platform/typescript/kernel-toolchains` | Contains `GradleJavaServiceAdapter`, `PnpmViteReactAdapter`, partial/planned adapters for Vitest, Playwright, Docker, Compose, Kubernetes, Helm, Terraform, mobile. |
| Artifact model | `platform/typescript/kernel-artifacts` | Contains manifest/domain/fingerprint/registry/resolver/validator/storage entry points. |
| Deployment model | `platform/typescript/kernel-deployment` | Contains deployment manifest, target, plan, result, health check, promotion policy, verifier entry points. |
| Release model | `platform/typescript/kernel-release` | Contains release, release manifest, promotion, rollback, and approval gate entry points. |
| Platform check | `scripts/check-kernel-platform-lifecycle.mjs` | Aggregates lifecycle profile, toolchain registry, lifecycle contracts, artifact contracts, deployment contracts, and exclusions checks. |
| Digital Marketing lifecycle config | `products/digital-marketing/kernel-product.yaml` | Declares backend API and web surfaces, adapters, tasks/scripts, expected outputs, gates, artifacts, and local deployment metadata. |
| Digital Marketing root scripts | `package.json` | Digital Marketing scripts now call `scripts/kernel-product.mjs`; other products mostly still use legacy `pnpm product`. |

This means the correct strategy is **not** to start over. The work is to stabilize, complete, and harden the existing lifecycle platform.

---

## 4. Current critical gaps to fix before calling Kernel end-to-end

### Gap 1 — `scripts/kernel-product.mjs` does not use real toolchain adapters during normal execution

The current `ProductLifecyclePlanner` resolves shell commands directly into plan steps. The executor then detects `step.execution` and runs `spawnSync` directly. That bypasses `platform/typescript/kernel-toolchains` adapter execution, output validation, and artifact extraction.

**Impact:** `GradleJavaServiceAdapter` and `PnpmViteReactAdapter` exist, but the CLI path does not benefit from their typed adapter contracts.

**Required fix:** planner should produce adapter-backed plan steps with surface config and optional toolchain plan details; executor should invoke the real adapter registry by default. Shell execution can remain as an internal adapter implementation detail.

---

### Gap 2 — `deploy` and `verify` are not actually wired to deployment adapters

Digital Marketing declares:

```yaml
deployment:
  local:
    target: compose-local
```

But planning currently resolves phase surfaces and validates the surface adapters. For `deploy`, the profile selects `backend-api` and `web`, then the planner tries to use `gradle-java-service` and `pnpm-vite-react`, which do not support `deploy`.

**Impact:** `pnpm deploy:local:digital-marketing` and `pnpm plan:deploy:local:digital-marketing` cannot become reliable until deploy planning switches to `compose-local`.

**Required fix:** lifecycle planner must have phase-specific adapter resolution:

| Phase | Adapter source |
|---|---|
| `dev`, `validate`, `test`, `build` | surface adapters |
| `package` | packaging adapter per artifact/surface |
| `deploy`, `verify`, `rollback` | deployment target adapter |
| `release`, `promote` | release/promotion adapter |

---

### Gap 3 — `package` currently means “run build/assemble”, not package deployable artifacts

For Digital Marketing:

```yaml
backend-api.packageTask: assemble
web.packageScript: build
artifacts.package.*.type: container-image
```

The configured package outputs are container images, but the current adapter mapping for package still calls Gradle assemble or Vite build.

**Impact:** package phase does not produce container image manifests/digests.

**Required fix:** add explicit packaging surface config or deployment packaging config:

```yaml
package:
  backend-api:
    adapter: docker-buildx
    dockerfile: products/digital-marketing/dm-api/Dockerfile
    context: .
    image: ghatana/digital-marketing-api
  web:
    adapter: docker-buildx
    dockerfile: products/digital-marketing/ui/Dockerfile
    context: products/digital-marketing/ui
    image: ghatana/digital-marketing-web
```

---

### Gap 4 — artifact manifests are not yet the true bridge between phases

Current plan/result files are written, but artifact generation is not yet integrated end-to-end. The executor can return `artifacts`, but shell execution does not populate artifacts. Artifact package exists separately but is not yet the canonical lifecycle output path.

**Impact:** `deploy` cannot safely consume “the artifact from build/package” because the artifact manifest is not guaranteed.

**Required fix:** every successful `build` and `package` must emit:

```text
.kernel/out/products/<productId>/<phase>/<runId>/
  lifecycle-plan.json
  lifecycle-result.json
  artifact-manifest.json
  build-manifest.json
  test-summary.json
```

`deploy` must consume an explicit package artifact manifest or latest successful package manifest.

---

### Gap 5 — lifecycle execution is sequential regardless of phase mode

The planner sets `dependsOn` for sequential phases, but the executor loops through steps sequentially for all phases. Parallel phase mode is not honored.

**Impact:** `dev`, `validate`, and `test` cannot actually run parallel surfaces even when configured.

**Required fix:** executor should build a dependency-aware DAG. Steps with no unmet dependencies should run concurrently when phase mode is `parallel`.

---

### Gap 6 — lifecycle gates are metadata, not executable pre/post gates

Profiles declare required gates, and Digital Marketing config declares gates, but the current planner returns `gates: []`.

**Impact:** `validate`, `build`, and `deploy` do not actually include gate steps such as manifest validation, bridge compliance, route entitlement checks, bundle budget, environment validation, and artifact validation.

**Required fix:** use `GateResolver` and add gate steps to plans. Required gates must fail closed.

---

### Gap 7 — Digital Marketing local deployment config is not production-grade yet

`products/digital-marketing/deploy/local.compose.yaml` currently references shared Docker templates as Dockerfiles. The current shared templates use placeholder variables like `${PRODUCT_*}` and need either build args support or product-specific Dockerfiles generated from templates. The web dockerfile path is also fragile relative to its build context.

`products/digital-marketing/deploy/local.env.example` currently contains `DATABASE_PASSWORD=postgres`, which violates the repo’s no-hardcoded-secrets/unsafe-defaults principle.

**Required fix:** add product-local Dockerfiles or make shared Docker templates build-arg compatible, then update compose and env examples.

---

### Gap 8 — Digital Marketing health paths do not match perfectly

`kernel-product.yaml` declares backend health path `/health`, while the API server exposes `/health/live` and `/health/ready`.

**Required fix:** standardize Digital Marketing health contract:

```yaml
health:
  livePath: /health/live
  readyPath: /health/ready
```

Compose health checks should use readiness.

---

### Gap 9 — registry and kernel-product.yaml can drift

Digital Marketing lifecycle data exists both in:

```text
config/canonical-product-registry.json
products/digital-marketing/kernel-product.yaml
```

The registry says artifacts/deployment/profile, and the YAML says surfaces/gates/deployment details. There is no strict drift check ensuring consistency.

**Required fix:** add a drift validator that compares registry lifecycle fields with `kernel-product.yaml`.

---

## 5. Target architecture after this implementation phase

```text
Developer
  ↓
pnpm kernel product <phase> <product>
  ↓
scripts/kernel-product.mjs
  ↓
@ghatana/kernel-lifecycle
  - registry loader
  - product config loader
  - lifecycle profile resolver
  - surface resolver
  - gate resolver
  - artifact resolver
  - environment resolver
  - deployment resolver
  - plan writer
  - executor
  ↓
@ghatana/kernel-toolchains
  - gradle-java-service
  - pnpm-vite-react
  - docker-buildx
  - compose-local
  ↓
@ghatana/kernel-artifacts
  - build manifest
  - package manifest
  - artifact fingerprint
  ↓
@ghatana/kernel-deployment
  - deployment plan
  - health checks
  - deployment manifest
  - verification result
```

---

## 6. Product compatibility requirements

### Digital Marketing — pilot, fully enabled

Required target behavior:

```bash
pnpm plan:dev:digital-marketing
pnpm dev:digital-marketing
pnpm plan:validate:digital-marketing
pnpm validate:digital-marketing
pnpm plan:test:digital-marketing
pnpm test:digital-marketing
pnpm plan:build:digital-marketing
pnpm build:digital-marketing
pnpm plan:package:digital-marketing
pnpm package:digital-marketing
pnpm plan:deploy:local:digital-marketing
pnpm deploy:local:digital-marketing
pnpm plan:verify:local:digital-marketing
pnpm verify:local:digital-marketing
```

### PHR — planned, must remain non-breaking

PHR uses a standard web + Java API shape. Keep lifecycle metadata planned/disabled. Do not enable until Digital Marketing proves the flow.

### Finance — planned, must shape backend-heavy support

Finance has backend API, portal, operator, SDK, and many Java modules. Kernel must not assume every product is a simple web+API product. Keep disabled, but keep profile support for backend-only, operator, and SDK surfaces.

### FlashIt — planned, must shape mobile + Node/API + web support

FlashIt has backend API, web, and mobile surfaces. Kernel must not assume mobile is deployable the same way as web/backend. Keep `mobile-plus-api-product` experimental and unsafe for default until mobile adapters are implemented.

### Aura — do not migrate in this phase

Aura should influence the platform-provider/demo/example model, but do not touch unless registry/check changes break it.

### DCMAAR — do not migrate in this phase

DCMAAR has backend/web surfaces but lacks full manifest conformance. Keep current behavior and ensure lifecycle checks do not fail unrelated products.

---

## 7. File-by-file implementation plan

---

# Phase 0 — Stabilize the current lifecycle platform contract

## 0.1 `docs/kernel/00-VISION.md`

**Current:** Correctly states Kernel is a product lifecycle platform and product developers should use `kernel product ...` commands.

**Changes:**

1. Add an explicit maturity statement:
   - Current implementation is in lifecycle pilot mode.
   - Digital Marketing is the only enabled product.
   - Other products are compatibility targets, not active migrations.
2. Add a “developer promise”:
   - Normal developers use Kernel commands.
   - Tools are adapter implementation details.
3. Add a “power-user promise”:
   - Overrides are allowed only through adapter contracts and must pass Kernel gates.

**Acceptance criteria:**

- Vision reflects pilot scope without overstating production readiness.
- Vision does not imply all products are already migrated.

---

## 0.2 `docs/kernel/02-PRODUCT_LIFECYCLE.md`

**Current:** Defines lifecycle phases and profiles.

**Changes:**

1. Replace generic examples with current repo-accurate examples:
   - Digital Marketing = `standard-web-api-product`
   - Finance = future `backend-only-java-service` + `sdk-product`/operator extensions
   - FlashIt = future `mobile-plus-api-product`
2. Add exact phase-to-adapter ownership:
   - `build` = surface adapter
   - `package` = package adapter
   - `deploy` = deployment target adapter
   - `verify` = deployment verifier
3. Add run output contract:
   - `lifecycle-plan.json`
   - `lifecycle-result.json`
   - `artifact-manifest.json`
   - `deployment-manifest.json`

**Acceptance criteria:**

- Docs match actual implementation after Phase 1/2.
- No doc says deploy uses Gradle/pnpm surface adapters.

---

## 0.3 `docs/kernel/03-TOOLCHAIN_ADAPTERS.md`

**Current:** Existing canonical doc likely describes adapters.

**Changes:**

1. Define `ToolchainAdapter` contract as implementation source of truth.
2. Require every adapter to implement:
   - `plan`
   - `execute`
   - `validateOutputs`
3. Require adapter metadata in `config/toolchain-adapter-registry.json`.
4. Define maturity fields:
   - `status`
   - `safeForDefault`
   - `planningImplemented`
   - `executionImplemented`
   - `outputValidationImplemented`
5. Define that `safeForDefault` requires all three implemented booleans to be true and tests present.

**Acceptance criteria:**

- Registry and docs match.
- Unsafe adapters cannot be used by enabled products.

---

## 0.4 `docs/kernel/04-ARTIFACTS.md`

**Changes:**

1. Define artifact manifest schema and lifecycle run location.
2. Define artifact fingerprinting rules.
3. Define build/package/deploy handoff:
   - build creates build artifact manifest
   - package consumes build manifest and creates package manifest
   - deploy consumes package manifest
4. Define “latest” pointer behavior:
   - `.kernel/out/products/<product>/<phase>/latest`
   - future immutable run IDs.

**Acceptance criteria:**

- Digital Marketing package/deploy can be implemented from this contract.

---

## 0.5 `docs/kernel/05-DEPLOYMENT.md`

**Changes:**

1. Define local Compose as the only mandatory target in this phase.
2. Define future targets: Kubernetes, Helm, Terraform.
3. Define deploy/verify/rollback responsibilities.
4. Define local deploy safety:
   - no real secrets
   - explicit env file
   - health check required
   - deploy must consume package artifact manifest

**Acceptance criteria:**

- Local deploy is implementable without ambiguity.
- Production deploy remains documented but not enabled.

---

# Phase 1 — Fix lifecycle engine planning and execution

## 1.1 `platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePhase.ts`

**Current issues:**

- Types are too broad but not organized around phase-specific plans.
- `LifecyclePlanStep` mixes execution shell command with adapter identity.
- `ProductLifecyclePlan` and `LifecyclePlan` overlap.

**Changes:**

1. Consolidate `LifecyclePlan` and `ProductLifecyclePlan` into one canonical exported type.
2. Add explicit `stepKind`:
   - `gate`
   - `surface`
   - `package`
   - `deploy`
   - `verify`
   - `release`
   - `promotion`
   - `rollback`
3. Add `adapterContext` payload to each step:
   ```ts
   adapterContext: {
     surfaceConfig?: Record<string, unknown>;
     deploymentConfig?: Record<string, unknown>;
     artifactConfig?: Record<string, unknown>;
     environmentConfig?: Record<string, unknown>;
   }
   ```
4. Keep optional `execution` only for adapter-produced command plans, not planner-produced direct commands.
5. Add `phaseMode` to plan:
   ```ts
   phaseMode: 'parallel' | 'sequential' | 'dag'
   ```
6. Add `runId` to plan/result.

**Tests:**

- Add `platform/typescript/kernel-lifecycle/src/domain/__tests__/ProductLifecyclePhase.test.ts`.

**Acceptance criteria:**

- No duplicate plan type confusion.
- Plan steps can represent gates, deploy, verify, package, and surface execution consistently.

---

## 1.2 `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`

**Current issues:**

- Loads registry/profile/toolchain directly but does not use dedicated resolver classes.
- `gates` is always empty.
- `expectedArtifacts` is always empty.
- Deploy/verify are planned incorrectly through surface adapters.
- Package does not switch to package adapters.
- Command generation is duplicated here instead of delegated to toolchain adapters.

**Changes:**

1. Refactor `plan()` into smaller private methods:
   - `loadPlanningInputs`
   - `resolvePhaseMode`
   - `resolvePhaseSurfaces`
   - `resolveGateSteps`
   - `resolveSurfaceSteps`
   - `resolvePackageSteps`
   - `resolveDeploySteps`
   - `resolveVerifySteps`
   - `resolveExpectedArtifacts`
   - `resolveOutputDirectory`
2. Remove direct shell command mapping from planner.
3. For `dev`, `validate`, `test`, `build`:
   - create surface adapter steps using each surface’s adapter.
4. For `package`:
   - create package adapter steps using package config.
   - if no package config exists, fail with actionable message.
5. For `deploy`, `verify`, `rollback`:
   - resolve environment from `--env` or product default environment.
   - resolve deployment target adapter from product config.
   - create deploy/verify/rollback steps using `compose-local` for Digital Marketing local.
6. Populate `gates` from:
   - lifecycle profile required gates
   - product `kernel-product.yaml` gates
   - registry conformance flags
7. Populate `expectedArtifacts` from:
   - profile `phaseOutputs`
   - product `artifacts`
   - surface expected outputs
8. Validate `allowExperimentalAdapters`:
   - Digital Marketing has `allowExperimentalAdapters: false`.
   - If false, only adapters with `safeForDefault: true` may be used for default phases.
   - Exception: package/deploy may use `safeForDefault: false` only when explicitly in pilot config and check is set to “pilot allowed”; better target is to make `docker-buildx` and `compose-local` safe before enabling package/deploy.

**Tests:**

- `platform/typescript/kernel-lifecycle/src/planning/__tests__/ProductLifecyclePlanner.test.ts`
  - plans Digital Marketing `build` with backend then web sequential dependencies
  - plans Digital Marketing `test` with parallel-capable steps
  - plans Digital Marketing `deploy --env local` with `compose-local`, not Gradle/pnpm
  - fails deploy without environment
  - fails unsafe adapter when `allowExperimentalAdapters: false`
  - rejects excluded product
  - rejects planned product when lifecycle disabled

**Acceptance criteria:**

- `pnpm plan:build:digital-marketing` produces backend+web surface steps.
- `pnpm plan:deploy:local:digital-marketing` produces compose deployment step.
- `pnpm plan:verify:local:digital-marketing` produces health verification step.
- No deploy plan uses `gradle-java-service` or `pnpm-vite-react`.

---

## 1.3 `platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts`

**Current issues:**

- Directly executes `step.execution` via `spawnSync`.
- Bypasses adapter registry when `step.execution` exists.
- Does not honor phase parallelism.
- Does not create artifact manifests.
- Does not run gate steps differently from surface steps.

**Changes:**

1. Remove shell execution from executor except through adapter implementations.
2. Use `ProductLifecycleStepRunner` for all executable steps.
3. Implement DAG execution:
   - pending queue
   - dependencies
   - fail-closed behavior
   - parallel execution when phase mode allows
4. Add lifecycle-level failure policy:
   - default `fail-closed`
   - no `fail-open` for enabled products
5. Write structured outputs:
   - `lifecycle-result.json`
   - `test-summary.json` when phase = test
   - `build-manifest.json` when phase = build
   - `artifact-manifest.json` when phase = build/package
   - `deployment-manifest.json` when phase = deploy
6. Capture adapter artifacts into result.
7. If required artifact validation fails, fail lifecycle result.

**Tests:**

- `platform/typescript/kernel-lifecycle/src/execution/__tests__/ProductLifecycleExecutor.test.ts`
  - executes sequential dependency order
  - executes independent parallel steps
  - skips dependent step after failed dependency
  - writes result and artifact manifest
  - fail closed on missing required artifact

**Acceptance criteria:**

- Executor no longer duplicates command-running logic.
- Adapter registry is the only execution mechanism.
- Parallel mode is real.

---

## 1.4 `platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleStepRunner.ts`

**Current issues:**

- Adapter context is too thin and loses surface/deployment config.
- For adapter fallback, current CLI creates fake registry that throws if no direct execution exists.

**Changes:**

1. Expand `StepContext` to include:
   - full product config
   - full surface config
   - resolved environment config
   - artifact config
   - deployment config
   - run output directory
2. Build adapter context from `step.adapterContext`, not guessed values.
3. Ensure adapter result includes artifacts and validation errors.
4. Fail if adapter returns `skipped` for a required non-dry-run step.

**Tests:**

- `ProductLifecycleStepRunner.test.ts`
  - passes expected full context to adapter
  - returns failure with adapter errors
  - handles dry-run correctly

---

## 1.5 `platform/typescript/kernel-lifecycle/src/execution/ExecutionResultCollector.ts`

**Changes:**

1. Capture:
   - startedAt
   - completedAt
   - duration
   - step stdout/stderr truncated safely
   - artifacts
   - gate results
   - failure details
2. Include `runId`, `environment`, and `sourceRef`.
3. Ensure results are deterministic and JSON schema compatible.

**Tests:**

- Add result collector tests.

---

## 1.6 `platform/typescript/kernel-lifecycle/src/io/ArtifactWriter.ts`

**Current:** Exists, but not integrated end-to-end.

**Changes:**

1. Move artifact manifest creation behind `@ghatana/kernel-artifacts`.
2. ArtifactWriter should delegate to `ProductArtifactRegistry` and `ArtifactFingerprintCalculator`.
3. Ensure artifacts include:
   - productId
   - phase
   - surface
   - type
   - path/image
   - fingerprint/digest
   - producedBy stepId
   - createdAt
4. For non-file artifacts like container images, support:
   - `image`
   - `tag`
   - `digest`
   - `localImageId`

**Tests:**

- Add ArtifactWriter tests with filesystem temp dirs.

---

## 1.7 `scripts/kernel-product.mjs`

**Current issues:**

- Imports from built `dist`; this requires lifecycle packages to be built before CLI use.
- Creates fallback adapter registry that throws.
- Always prints JSON; `--json` option is parsed but not used meaningfully.
- Does not know how to select package/deployment adapters.
- Uses latest output folder by default.

**Changes:**

1. Replace fallback registry with real `ToolchainAdapterRegistry`.
2. Load adapter implementations from `@ghatana/kernel-toolchains`.
3. Support:
   - `--dry-run`
   - `--json`
   - `--output-dir`
   - `--env`
   - `--surface`
   - `--artifact`
   - `--run-id`
4. Human output by default:
   - product
   - phase
   - selected surfaces
   - adapter list
   - gates
   - output directory
   - status
5. JSON only when `--json`.
6. For development convenience, keep compiled `dist` import but add a friendly error:
   - “Run `pnpm build:kernel-lifecycle-platform` first”
   - do not show raw module resolution stack trace.
7. Use immutable run directory by default:
   ```text
   .kernel/out/products/<product>/<phase>/<runId>
   ```
   Then update/copy `latest`.

**Tests:**

- Add `scripts/__tests__/kernel-product.test.mjs` or a script-level contract test under `scripts/check-kernel-product-cli.mjs`.

**Acceptance criteria:**

- CLI uses real adapters.
- `--json` returns machine-readable output only.
- Failed preconditions are actionable.

---

# Phase 2 — Complete safe adapters needed by Digital Marketing

## 2.1 `platform/typescript/kernel-toolchains/src/ToolchainAdapter.ts`

**Changes:**

1. Ensure the interface supports:
   - `plan`
   - `execute`
   - `validateOutputs`
   - artifact extraction
   - dry-run
   - environment metadata
   - structured errors
2. Add discriminated result types:
   ```ts
   type ToolchainExecutionResult =
     | { status: 'succeeded'; artifacts: ProductArtifact[]; ... }
     | { status: 'failed'; failure: ToolchainFailure; ... }
     | { status: 'skipped'; reason: string; ... };
   ```

**Acceptance criteria:**

- No adapter result uses loosely typed shape.
- No `any`.

---

## 2.2 `platform/typescript/kernel-toolchains/src/ToolchainAdapterRegistry.ts`

**Changes:**

1. Provide built-in adapter registry factory:
   ```ts
   createDefaultToolchainAdapterRegistry({ repoRoot, commandRunner })
   ```
2. Register:
   - `gradle-java-service`
   - `pnpm-vite-react`
   - `docker-buildx`
   - `compose-local`
3. Reject unknown adapter with supported adapter list.
4. Enforce adapter metadata from `config/toolchain-adapter-registry.json`.

**Tests:**

- Adapter lookup test.
- Unknown adapter failure test.
- Metadata mismatch test.

---

## 2.3 `platform/typescript/kernel-toolchains/src/adapters/GradleJavaServiceAdapter.ts`

**Current:** Implemented and includes output validation.

**Changes:**

1. Ensure task mapping supports Digital Marketing:
   - `dev` -> `runDmosApiServer`
   - `validate` -> `check`
   - `test` -> `test`
   - `build` -> `build`
   - `package` -> only if used for jar packaging, not container images
2. For `dev`, do not validate outputs after starting a long-running server.
3. Add `mode`:
   - `foreground` for dev
   - `blocking` for build/test/validate/package
4. Add process lifecycle behavior for dev:
   - stream logs
   - capture PID
   - write `.kernel/out/.../processes.json`
5. Output validation:
   - for `test`, validate test results
   - for `build`, validate jar patterns
   - for `dev`, validate health endpoint if configured, not jar outputs

**Tests:**

- Existing adapter tests must expand for dev/test/build/package.
- Test expected output patterns.
- Test no output validation for dev until health check is wired.

---

## 2.4 `platform/typescript/kernel-toolchains/src/adapters/PnpmViteReactAdapter.ts`

**Current:** Implemented and includes output validation.

**Changes:**

1. For `dev`, support long-running Vite server.
2. Do not validate `dist` for dev.
3. For `validate`, run configured `validateScript` and validate package exists.
4. For `build`, validate `dist` and `dist/index.html`.
5. For `package`, do not pretend Vite build produces container image. Package must use Docker adapter when artifact type is container image.
6. Stream logs for dev.
7. Write process metadata for dev.

**Tests:**

- dev plans correctly.
- build validates dist.
- package is rejected if expected artifact is container image and adapter is `pnpm-vite-react`.

---

## 2.5 `platform/typescript/kernel-toolchains/src/adapters/DockerBuildxAdapter.ts`

**Current:** partial/planned in registry.

**Required for Digital Marketing package.**

**Changes:**

1. Implement `plan`, `execute`, `validateOutputs`.
2. Support config:
   ```yaml
   dockerfile: products/digital-marketing/dm-api/Dockerfile
   context: .
   image: ghatana/digital-marketing-api
   tag: local
   buildArgs:
     PRODUCT_PORT: "8080"
   ```
3. Execution:
   ```bash
   docker buildx build --load -t <image>:<tag> -f <dockerfile> <context>
   ```
4. Validate:
   ```bash
   docker image inspect <image>:<tag>
   ```
5. Produce artifact:
   ```json
   {
     "type": "container-image",
     "image": "ghatana/digital-marketing-api",
     "tag": "local",
     "digest": "...optional..."
   }
   ```
6. Fail if Docker unavailable with actionable message.

**Tests:**

- Unit test command planning using fake command runner.
- Execution test with fake command runner.
- Output validation test with fake image inspect.

---

## 2.6 `platform/typescript/kernel-toolchains/src/adapters/ComposeLocalAdapter.ts`

**Current:** partial/planned.

**Required for Digital Marketing deploy/verify.**

**Changes:**

1. Implement `plan`, `execute`, `validateOutputs`.
2. Support:
   - deploy -> `docker compose --env-file <envFile> -f <composeFile> up -d`
   - verify -> run health checks from product config
   - rollback -> `docker compose ... down` or previous artifact strategy later
3. Validate:
   - compose file exists
   - env example exists
   - no checked-in local env secret file required
   - required health checks configured
4. Produce deployment manifest:
   - compose project
   - services
   - env
   - health check URLs
   - startedAt/completedAt
5. Fail if Docker Compose unavailable.

**Tests:**

- Plan deploy command.
- Plan verify health checks.
- Fail missing compose file.
- Fail missing env example.
- Do not require `local.env` in repo.

---

## 2.7 `platform/typescript/kernel-toolchains/src/adapters/VitestAdapter.ts` and `PlaywrightAdapter.ts`

**Scope for this phase:** Do not make these default adapters yet.

**Changes:**

1. Keep as non-default optional gate adapters.
2. Implement planning only if already simple.
3. Do not enable in Digital Marketing default lifecycle until command execution and output validation are implemented.

**Acceptance criteria:**

- Registry remains honest: `safeForDefault: false` unless fully implemented/tested.

---

# Phase 3 — Digital Marketing pilot hardening

## 3.1 `products/digital-marketing/kernel-product.yaml`

**Current:** Good start, but package/deploy semantics need strengthening.

**Changes:**

1. Replace backend health:
   ```yaml
   health:
     type: http
     livePath: /health/live
     readyPath: /health/ready
     portVariable: DMOS_API_PORT
     defaultPort: 8080
   ```
2. Replace web health:
   ```yaml
   health:
     type: http
     readyPath: /
     portVariable: DMOS_WEB_PORT
     defaultPort: 5173
   ```
3. Add explicit package config:
   ```yaml
   package:
     backend-api:
       adapter: docker-buildx
       context: .
       dockerfile: products/digital-marketing/dm-api/Dockerfile
       image: ghatana/digital-marketing-api
       tag: local
     web:
       adapter: docker-buildx
       context: products/digital-marketing/ui
       dockerfile: products/digital-marketing/ui/Dockerfile
       image: ghatana/digital-marketing-web
       tag: local
   ```
4. Add deploy adapter config:
   ```yaml
   deployment:
     local:
       adapter: compose-local
       target: compose-local
       composeFile: products/digital-marketing/deploy/local.compose.yaml
       envExampleFile: products/digital-marketing/deploy/local.env.example
       envFile: products/digital-marketing/deploy/local.env
       requireEnvFile: false
   ```
5. Add verify config:
   ```yaml
   verify:
     local:
       adapter: compose-local
       healthChecks:
         backend-api:
           type: http
           url: http://localhost:${DMOS_API_PORT:-8080}/health/ready
         web:
           type: http
           url: http://localhost:${DMOS_WEB_PORT:-5173}/
   ```
6. Add `allowExperimentalAdapters: false` only after `docker-buildx` and `compose-local` are safeForDefault. Until then:
   - either keep package/deploy disabled
   - or use `allowPilotAdapters: [docker-buildx, compose-local]` with explicit check

**Tests:**

- Plan tests must parse this config.
- Contract checker validates package/deployment configs.

---

## 3.2 `products/digital-marketing/deploy/local.compose.yaml`

**Current issues:**

- Uses shared templates directly as Dockerfiles without passing required build args.
- Web Dockerfile path is fragile.
- Hardcodes ports and database URL.
- Health check hits `/health`, while server exposes `/health/live` and `/health/ready`.

**Changes:**

1. Use product-specific Dockerfiles:
   ```yaml
   build:
     context: ../../..
     dockerfile: products/digital-marketing/dm-api/Dockerfile
   ```
   for API.
2. Use:
   ```yaml
   build:
     context: ../ui
     dockerfile: Dockerfile
   ```
   for web, or root context with explicit file.
3. Load variables from env:
   ```yaml
   ports:
     - "${DMOS_API_PORT:-8080}:8080"
   ```
4. Change backend health:
   ```yaml
   test: ["CMD", "curl", "-f", "http://localhost:8080/health/ready"]
   ```
5. Add local Postgres service only if DMOS local is expected to use Postgres. If current dev path uses in-memory, keep DB external but do not hardcode `DATABASE_URL=jdbc:postgresql://localhost:5432/...` inside service without a network service.
6. Add labels for Kernel:
   ```yaml
   labels:
     ghatana.kernel.product: digital-marketing
     ghatana.kernel.surface: backend-api
   ```

**Acceptance criteria:**

- `docker compose -f products/digital-marketing/deploy/local.compose.yaml config` succeeds.
- Health checks match server paths.
- Compose is usable by `ComposeLocalAdapter`.

---

## 3.3 `products/digital-marketing/deploy/local.env.example`

**Current issue:** Uses `DATABASE_PASSWORD=postgres`.

**Changes:**

1. Replace unsafe defaults:
   ```env
   DATABASE_PASSWORD=<set-local-postgres-password>
   DMOS_PII_HMAC_KEY=<set-local-hmac-key>
   DMOS_CONTACT_ENCRYPTION_KEY=<set-local-encryption-key>
   ```
2. Add:
   ```env
   DMOS_ENV=development
   DMOS_PERSISTENCE_TYPE=in-memory
   ```
   for default local dev if that is the supported ergonomic mode.
3. Add comments that `local.env` is gitignored and developer-created.
4. Do not include real API keys or real-looking secrets.

**Acceptance criteria:**

- `check:secret-default-credentials` passes.
- Local defaults do not imply production-safe credentials.

---

## 3.4 `products/digital-marketing/deploy/.gitignore`

**Add file:**

```gitignore
local.env
*.local.env
```

**Acceptance criteria:**

- Local secret/config files cannot be accidentally committed.

---

## 3.5 `products/digital-marketing/dm-api/Dockerfile`

**Add file.**

**Recommended approach:** product-specific Dockerfile generated from shared Kernel Java service template, with concrete DMOS values.

Example skeleton:

```dockerfile
# Shared template source: config/docker/templates/product-java-service.Dockerfile.template
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /repo
COPY . .
RUN ./gradlew :products:digital-marketing:dm-api:build --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /repo/products/digital-marketing/dm-api/build/libs/*.jar /app/app.jar

EXPOSE 8080
USER appuser
HEALTHCHECK --interval=15s --timeout=10s --start-period=30s --retries=3 \
  CMD wget --spider -q http://localhost:8080/health/ready || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**Important:** verify actual Gradle output jar is executable. If it is not executable, add an application/distribution task or use classpath layout.

**Acceptance criteria:**

- Builds from repo root.
- Does not skip tests unless explicitly using already-built artifacts in a package-only path.
- Uses non-root user.
- Has healthcheck.
- Conforms to runtime template check.

---

## 3.6 `products/digital-marketing/ui/Dockerfile`

**Add file.**

Example:

```dockerfile
# Shared template source: config/docker/templates/product-node-web.Dockerfile.template
FROM node:18-alpine AS builder
WORKDIR /app
COPY package.json pnpm-lock.yaml* ./
RUN npm install -g pnpm@10.33.0
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:alpine AS runner
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN chown -R nginx:nginx /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
```

**Also add/update:**

```text
products/digital-marketing/ui/nginx.conf
```

**Acceptance criteria:**

- Docker image serves the Vite build.
- Healthcheck passes.
- No hardcoded product secrets.

---

## 3.7 `products/digital-marketing/dm-api/build.gradle.kts`

**Current:** Has `runDmosApiServer`, test/check, OpenAPI validation, Jacoco.

**Changes:**

1. Add or verify executable packaging task:
   - If current jar is not runnable with `java -jar`, configure application distribution or create a launcher script.
2. Add lifecycle-friendly task aliases:
   ```kotlin
   tasks.register("kernelDev") { dependsOn("runDmosApiServer") }
   tasks.register("kernelValidate") { dependsOn("check") }
   tasks.register("kernelTest") { dependsOn("test") }
   tasks.register("kernelBuild") { dependsOn("build") }
   ```
   Optional; keep Kernel config using existing tasks if aliases are unnecessary.
3. Tighten coverage TODO:
   - Keep current lower threshold if required, but add explicit tracker reference and acceptance target.
4. Ensure `validateOpenApiSpec` is part of `check` and remains stable.

**Acceptance criteria:**

- `./gradlew :products:digital-marketing:dm-api:check`
- `./gradlew :products:digital-marketing:dm-api:build`
- `./gradlew :products:digital-marketing:dm-api:runDmosApiServer`

---

## 3.8 `products/digital-marketing/dm-api/src/main/java/com/ghatana/digitalmarketing/api/DmosApiServer.java`

**Current:** Exposes `/health/live` and `/health/ready`.

**Changes:**

1. Keep `/health/live` and `/health/ready`.
2. Optionally add `/health` as alias for readiness for compatibility:
   ```java
   if ("/health".equals(path) || "/health/ready".equals(path)) { ... }
   ```
3. If adding public health semantics, include JavaDoc tags if new public class/method is created.
4. Avoid adding business logic in router. Health should be minimal but correct.

**Tests:**

- Add/update `DmosApiServer` route test if existing test harness supports it.
- Verify `/health/ready` returns 200.
- Verify `/health/live` returns 200.

---

## 3.9 `products/digital-marketing/ui/package.json`

**Current:** Strong build command: route contract test, `tsc --noEmit`, Vite build, bundle budget.

**Changes:**

1. Keep existing scripts.
2. Add aliases only if needed by Kernel:
   ```json
   "validate": "pnpm run type-check",
   "kernel:validate": "pnpm run type-check",
   "kernel:test": "pnpm run test",
   "kernel:build": "pnpm run build"
   ```
   Prefer not adding if `kernel-product.yaml` already maps scripts.
3. Ensure `test:route-contract` remains part of build.
4. Ensure no duplicate dependencies.

**Acceptance criteria:**

- `pnpm --dir products/digital-marketing/ui run type-check`
- `pnpm --dir products/digital-marketing/ui run test`
- `pnpm --dir products/digital-marketing/ui run build`

---

# Phase 4 — Artifact, package, deploy, and verify integration

## 4.1 `platform/typescript/kernel-artifacts/src/domain/ArtifactManifest.ts`

**Changes:**

1. Ensure manifest supports file and non-file artifacts:
   - jar
   - static web bundle
   - container image
   - image digest
   - deployment manifest
2. Add schema version and source metadata:
   - commit/sourceRef
   - productId
   - phase
   - runId
   - environment
3. Require fingerprints for file artifacts.
4. Require image digest or local image ID for container artifacts where possible.

**Tests:**

- Manifest validation tests.
- File fingerprint tests.
- Container artifact validation tests.

---

## 4.2 `platform/typescript/kernel-artifacts/src/fingerprint/ArtifactFingerprintCalculator.ts`

**Changes:**

1. Support:
   - single file hash
   - directory hash
   - image digest placeholder/adapter-provided digest
2. Directory hash must be deterministic:
   - sorted paths
   - normalized separators
   - include file content and relative path

**Tests:**

- Same directory content same hash.
- Modified file changes hash.
- Path order does not change hash.

---

## 4.3 `platform/typescript/kernel-artifacts/src/storage/ArtifactStorage.ts`

**Changes:**

1. Write manifests to:
   ```text
   .kernel/out/products/<product>/<phase>/<runId>/
   ```
2. Maintain:
   ```text
   .kernel/out/products/<product>/<phase>/latest
   ```
3. Do not store large artifacts by copying by default. Store references/fingerprints.

**Tests:**

- Writes run manifest.
- Updates latest pointer/copy.
- Does not overwrite previous run.

---

## 4.4 `platform/typescript/kernel-deployment/src/verifier/DeploymentVerifier.ts`

**Changes:**

1. Implement HTTP health checks from deployment config.
2. Support timeout/retry settings:
   ```yaml
   retries: 10
   intervalMs: 2000
   timeoutMs: 1000
   ```
3. Record health check result:
   - status
   - URL
   - latency
   - error
   - checkedAt
4. Fail closed if required health check fails.

**Tests:**

- Healthy HTTP server test.
- Unreachable server test.
- Retry behavior test.

---

## 4.5 `platform/typescript/kernel-deployment/src/domain/DeploymentManifest.ts`

**Changes:**

1. Include:
   - productId
   - environment
   - target
   - artifact manifest reference
   - services
   - health checks
   - status
   - deployedAt
   - verifier result
2. Add schema validation.

---

# Phase 5 — Config and conformance hardening

## 5.1 `config/toolchain-adapter-registry.json`

**Current:** Many adapters are partial/planned; `gradle-java-service` and `pnpm-vite-react` are implemented/safe.

**Changes:**

1. Set `docker-buildx` to implemented/safe only after Phase 2.5.
2. Set `compose-local` to implemented/safe only after Phase 2.6.
3. Keep Kubernetes/Helm/Terraform partial and unsafe.
4. Keep mobile adapters partial and unsafe.
5. Add tests for every implemented/safe adapter.

**Acceptance criteria:**

- Enabled Digital Marketing does not reference unsafe default adapters unless explicitly allowed and checked.
- Registry reflects reality.

---

## 5.2 `config/product-lifecycle-profiles.json`

**Changes:**

1. For `standard-web-api-product`, separate phase outputs from package/deploy:
   ```json
   "phaseOutputs": {
     "build": {
       "backend-api": ["jvm-service"],
       "web": ["static-web-bundle"]
     },
     "package": {
       "backend-api": ["container-image"],
       "web": ["container-image"]
     },
     "deploy": {
       "product": ["deployment-manifest"]
     },
     "verify": {
       "product": ["health-check-report"]
     }
   }
   ```
2. Add `verify` default surfaces or target:
   - Prefer deployment target rather than surfaces.
3. Keep `mobile-plus-api-product.safeForDefault = false`.
4. Keep platform-provider and shared-service unsafe until proven.

**Acceptance criteria:**

- Profiles model all phases used by Digital Marketing.
- Package/deploy/verify are not pretending to be build surfaces.

---

## 5.3 `config/canonical-product-registry.json`

**Changes for Digital Marketing only:**

1. Keep:
   ```json
   "lifecycleStatus": "enabled",
   "lifecycle": { "enabled": true }
   ```
2. Ensure deployment target and environments match `kernel-product.yaml`.
3. Add a `lifecyclePilot` field if needed:
   ```json
   "lifecyclePilot": {
     "package": true,
     "deployLocal": true,
     "verifyLocal": true
   }
   ```
   Or avoid this by making adapters safe.

**Changes for other products:**

1. PHR, Finance, FlashIt remain `planned` and disabled.
2. Do not add lifecycle config path unless the file exists.
3. Keep Data Cloud/YAPPC exclusions or absence explicit.
4. Do not break `pnpm product <id> build` legacy scripts.

**Acceptance criteria:**

- `pnpm check:product-lifecycle-contracts` passes.
- Only Digital Marketing is enabled.
- Planned products do not fail checks.

---

## 5.4 `config/canonical-product-registry-schema.json`

**Changes:**

1. Add stronger lifecycle invariant:
   - if `lifecycleStatus = enabled`, require:
     - `lifecycleProfile`
     - `lifecycleConfigPath`
     - `lifecycle.enabled = true`
     - `toolchain`
     - `artifacts`
     - `deployment`
     - `environments`
2. Add deploy/verify schema sections if registry owns them.
3. Add allowed surface types:
   - current schema has `mobile`, while lifecycle types use `mobile-ios` and `mobile-android`.
   - Normalize this mismatch:
     - Either add `mobile-ios` and `mobile-android` to registry schema
     - Or keep registry as `mobile` and lifecycle config splits mobile platform surfaces
   - For FlashIt future support, prefer adding explicit mobile platform surface types.

**Acceptance criteria:**

- Schema and lifecycle types agree.
- FlashIt can be modeled without schema hacks.

---

## 5.5 `config/kernel-lifecycle-exclusions.json`

**Current:** Excludes YAPPC and Data Cloud.

**Changes:**

1. Keep exclusions for this phase.
2. Add explicit `expiresWhen` or `migrationOwner` metadata:
   ```json
   "data-cloud": {
     "reason": "...",
     "allowedLegacyExecution": true,
     "migrationStatus": "deferred",
     "doNotFailLifecycleChecks": true
   }
   ```
3. Ensure excluded products cannot accidentally declare `lifecycleStatus: enabled`.

**Acceptance criteria:**

- Exclusions are intentional, not silent bypasses.

---

## 5.6 `scripts/check-product-lifecycle-contracts.mjs`

**Current:** Checks enabled products and generates plans only for `validate`, `test`, and `build`.

**Changes:**

1. Also plan:
   - `package`
   - `deploy --env local`
   - `verify --env local`
   for products that declare package/deploy local enabled.
2. Validate registry/YAML drift.
3. Validate enabled products only use safe adapters.
4. Validate lifecycle profile `safeForDefault`.
5. Validate expected artifact config.
6. Validate Digital Marketing gate IDs exist in known gate registry or scripts.

**Acceptance criteria:**

- A product cannot claim deploy support unless deploy plan works.
- Digital Marketing package/deploy/verify planning is checked.

---

## 5.7 `scripts/check-product-artifact-contracts.mjs`

**Changes:**

1. Validate:
   - every enabled product has build artifact declarations
   - every package-enabled product has package artifact declarations
   - artifact types match profile phase outputs
2. Validate Digital Marketing:
   - backend-api build -> jar
   - web build -> static-web-bundle
   - backend-api package -> container-image
   - web package -> container-image

---

## 5.8 `scripts/check-product-deployment-contracts.mjs`

**Changes:**

1. Validate:
   - local environment exists
   - deployment target exists
   - compose file exists
   - env example exists
   - health checks exist
   - rollback strategy exists
2. Validate no checked-in local secret env file.
3. Validate compose config using:
   ```bash
   docker compose --env-file <example or generated temp env> -f <composeFile> config
   ```
   Make this optional if Docker is not available; schema/static validation must still run.

---

## 5.9 `scripts/check-toolchain-adapter-contracts.mjs`

**Changes:**

1. Safe adapter invariant:
   - `safeForDefault = true` requires:
     - `status = implemented`
     - `planningImplemented = true`
     - `executionImplemented = true`
     - `outputValidationImplemented = true`
     - at least one test file exists
2. Validate implementation file exists.
3. Validate exported adapter class is included in `platform/typescript/kernel-toolchains/src/index.ts`.

---

## 5.10 `scripts/check-kernel-platform-lifecycle.mjs`

**Current:** Aggregates lifecycle checks.

**Changes:**

1. Add new checks:
   - `check-kernel-product-cli.mjs`
   - `check-digital-marketing-lifecycle-pilot.mjs`
   - `check-lifecycle-registry-config-drift.mjs`
2. Keep as one command:
   ```bash
   pnpm check:kernel-platform-lifecycle
   ```

---

# Phase 6 — Root scripts and compatibility

## 6.1 `package.json`

**Current:** Digital Marketing now uses `kernel-product.mjs`, while other products still use legacy `pnpm product`.

**Changes:**

1. Keep Digital Marketing lifecycle scripts.
2. Add `--json` to plan scripts only if needed by automated checks:
   ```json
   "plan:build:digital-marketing": "node scripts/kernel-product.mjs product plan digital-marketing build --json"
   ```
   For developer friendliness, keep non-json plan scripts readable.
3. Add:
   ```json
   "check:kernel-product-cli": "node ./scripts/check-kernel-product-cli.mjs"
   "check:digital-marketing-lifecycle-pilot": "node ./scripts/check-digital-marketing-lifecycle-pilot.mjs"
   ```
4. Keep legacy scripts for PHR, Finance, FlashIt, DCMAAR, Aura unchanged.

**Acceptance criteria:**

- `pnpm check:kernel-platform-lifecycle`
- `pnpm build:kernel-lifecycle-platform`
- `pnpm plan:build:digital-marketing`
- `pnpm build:digital-marketing`

---

## 6.2 `scripts/run-product-task.mjs`

**Current:** Legacy router.

**Changes:**

1. Preserve legacy behavior for non-enabled products.
2. For lifecycle-enabled products, delegate to Kernel lifecycle:
   ```text
   if product.lifecycleStatus === enabled:
     run scripts/kernel-product.mjs product <task> <productId> ...
   else:
     old pnpm/gradle routing
   ```
3. Support compatibility:
   ```bash
   pnpm product digital-marketing build web
   ```
   maps to:
   ```bash
   node scripts/kernel-product.mjs product build digital-marketing --surface web
   ```

**Acceptance criteria:**

- Old commands still work.
- Digital Marketing uses Kernel lifecycle through both command paths.

---

## 6.3 `scripts/generate-product-registry-artifacts.mjs`

**Current:** Generates product-shape, Gradle includes, pnpm workspace, CI matrix, package scripts.

**Changes:**

1. Generate lifecycle-aware root scripts for enabled lifecycle products.
2. Keep legacy scripts for non-enabled products.
3. Generate lifecycle CI matrix fields:
   - enabled lifecycle products
   - planned lifecycle products
   - excluded products
   - lifecycle phases by product
   - adapters by product
4. Ensure generated scripts do not overwrite hand-authored lifecycle pilot scripts incorrectly.

**Tests:**

- Extend generator tests or add static check:
  - Digital Marketing generated scripts use `kernel-product.mjs`
  - PHR/Finance/FlashIt remain legacy until enabled

---

# Phase 7 — Digital Marketing gates and quality

## 7.1 `products/digital-marketing/dm-domain-packs/build.gradle.kts`

**Current:** Strong domain pack validation and coverage.

**Changes:**

1. Ensure Kernel lifecycle `validate` includes:
   - `:products:digital-marketing:dm-domain-packs:check`
2. Add lifecycle gate ID mapping:
   - `manifest-validation` -> domain pack validation
   - `bridge-compliance` -> bridge tests/check
   - `dmos-boundary-workflow-coverage` -> existing root script/check
3. Do not duplicate domain pack validation in Kernel code; orchestrate existing Gradle tasks.

---

## 7.2 `scripts/check-dmos-boundary-workflow-coverage.mjs`

**Changes:**

1. Make this callable as a Kernel gate.
2. Expose machine-readable output with `--json`.
3. Return non-zero on failure.

---

## 7.3 `scripts/check-bridge-compliance.mjs`

**Changes:**

1. Ensure Digital Marketing bridge adapter paths from registry are validated.
2. Add machine-readable `--json` output.
3. Ensure Kernel lifecycle `validate` can call this gate.

---

## 7.4 New `scripts/check-digital-marketing-lifecycle-pilot.mjs`

**Purpose:** One focused pilot validation script.

**Checks:**

1. `kernel-product.yaml` exists and parses.
2. Registry says lifecycle enabled.
3. Build plan works.
4. Test plan works.
5. Package plan works.
6. Deploy local plan works.
7. Verify local plan works.
8. No deploy plan uses Gradle/pnpm surface adapters.
9. Package plan uses Docker adapter.
10. Deploy plan uses Compose adapter.
11. Health paths match `/health/ready` and `/health/live`.
12. Env example contains no unsafe secret defaults.
13. Compose file exists and has Kernel labels.

**Acceptance criteria:**

- Added to `check:kernel-platform-lifecycle`.

---

# Phase 8 — Product compatibility matrix

## 8.1 `docs/kernel/11-MIGRATION_GUIDE.md`

**Changes:**

Add product readiness matrix:

| Product | Current lifecycle state | Required future profile | This phase action |
|---|---|---|---|
| Digital Marketing | enabled | standard-web-api-product | Fully stabilize |
| PHR | planned | standard-web-api-product | Do not migrate; keep non-breaking |
| Finance | planned | backend-only-java-service + sdk/operator extensions | Do not migrate; ensure model supports backend-heavy products |
| FlashIt | planned | mobile-plus-api-product + pnpm-node-api | Do not migrate; keep mobile adapters unsafe |
| Aura | not active/planned/demo | demo/example or platform-provider variant | Do not migrate |
| DCMAAR | legacy/non-enabled | standard-web-api-product once manifest exists | Do not migrate |
| Data Cloud | excluded | platform-provider-product | Leave excluded |
| YAPPC | excluded | platform-provider or app-creator profile | Leave excluded |

---

## 8.2 `config/product-lifecycle-profiles.json`

**Compatibility changes:**

1. Add `operator` support for Finance in a future profile:
   - `backend-operator-sdk-product` or extend backend-only profile.
2. Add Node API support for FlashIt through `pnpm-node-api`, but keep unsafe until adapter is real.
3. Add `mobile` schema alignment for FlashIt.
4. Add demo/example profile if Aura needs lifecycle later:
   - default safe false
   - no deploy by default.

---

## 8.3 `config/toolchain-adapter-registry.json`

**Compatibility changes:**

1. Keep the following planned/partial until implemented:
   - `pnpm-node-api`
   - `gradle-java-library`
   - `xcode-ios`
   - `gradle-android`
   - `kubernetes`
   - `helm`
   - `terraform`
   - `domain-pack-validator`
2. Add `operator` support to `gradle-java-service` if Finance operator modules can be treated as Java services.
3. Add future `gradle-java-sdk`/`maven-publish` path for Finance SDK.

---

# Phase 9 — CI/CD and workflows

## 9.1 `.github/workflows/product-lifecycle.yml`

**Add workflow.**

**Triggers:**

```yaml
on:
  pull_request:
    paths:
      - 'config/**'
      - 'scripts/kernel-product.mjs'
      - 'platform/typescript/kernel-*/**'
      - 'products/digital-marketing/**'
      - '.github/workflows/product-lifecycle.yml'
```

**Jobs:**

1. Install pnpm.
2. Build Kernel lifecycle packages.
3. Run lifecycle platform checks.
4. Plan Digital Marketing phases.
5. Run Digital Marketing validate/test/build.
6. Optionally package local images if Docker available.
7. Do not deploy in PR by default.

**Acceptance criteria:**

- PRs touching Kernel lifecycle or Digital Marketing pilot cannot bypass lifecycle checks.

---

## 9.2 Existing workflows

**Do not remove existing workflows.**

Update only as needed:

```text
.github/workflows/product-coverage-gates.yml
.github/workflows/api-contract-conformance.yml
.github/workflows/e2e-tests.yml
.github/workflows/performance-budgets.yml
.github/workflows/visual-regression.yml
.github/workflows/accessibility.yml
```

**Policy:**

- Digital Marketing lifecycle workflow complements existing gates.
- Do not break PHR/Finance/FlashIt/DCMAAR legacy CI.

---

# Phase 10 — Scaffolder alignment

## 10.1 `scripts/scaffold-product.mjs`

**Current:** Generates product skeleton, docs, conformance fixtures, workspace/registry/CI registration.

**Changes:**

1. Generate `kernel-product.yaml` for new products.
2. Generate package/deploy skeleton only for profiles that support deploy.
3. Generate product-local Dockerfiles from Kernel templates.
4. Generate lifecycle-compatible docs:
   - `docs/05-OPERATIONS.md`
   - `docs/06-IMPLEMENTATION_PLAN.md`
5. Register lifecycle metadata in canonical registry:
   - default `lifecycleStatus: planned`
   - `lifecycle.enabled: false`
   - `lifecycleConfigPath` set only if file generated
6. Do not enable lifecycle automatically for new products unless `--enable-lifecycle` is passed.

**Acceptance criteria:**

- Scaffolder creates lifecycle-ready but not lifecycle-enabled products by default.
- Generated product passes lifecycle contract checks in planned mode.

---

## 10.2 `scripts/check-product-scaffolder.mjs`

**Changes:**

1. Validate generated `kernel-product.yaml`.
2. Validate lifecycle registry metadata.
3. Validate generated Dockerfiles.
4. Validate generated local deploy config.
5. Validate generated product remains planned/disabled unless explicitly enabled.
6. Validate no unsafe env defaults.

---

# 11. Validation commands by milestone

## After Phase 1

```bash
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-platform-lifecycle
pnpm plan:build:digital-marketing
pnpm plan:test:digital-marketing
pnpm plan:validate:digital-marketing
```

## After Phase 2

```bash
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-platform-lifecycle
pnpm build:digital-marketing
pnpm test:digital-marketing
```

## After Phase 3

```bash
pnpm plan:package:digital-marketing
pnpm plan:deploy:local:digital-marketing
pnpm plan:verify:local:digital-marketing
docker compose -f products/digital-marketing/deploy/local.compose.yaml config
```

## After Phase 4

```bash
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
```

## Final pilot acceptance

```bash
pnpm clean
pnpm install
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-platform-lifecycle
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
```

---

# 12. Definition of done for this implementation stream

The stream is complete when:

1. Digital Marketing can be developed, validated, tested, built, packaged, locally deployed, and verified through Kernel commands.
2. Product developers do not need to invoke Gradle/pnpm/Vite/Docker/Compose directly for the normal Digital Marketing flow.
3. Kernel plan output clearly explains which adapters and gates are used.
4. Kernel execution uses real toolchain adapters.
5. Build/package artifacts are recorded in artifact manifests.
6. Deploy consumes package artifacts and writes deployment manifests.
7. Health verification uses the product’s declared health contract.
8. PHR, Finance, FlashIt, Aura, and DCMAAR legacy scripts still work.
9. Lifecycle checks fail closed for enabled products.
10. Planned/excluded products do not create false failures.
11. All touched TypeScript code is strict and fully typed.
12. Java public API additions include required doc tags.
13. No new unsafe defaults or hardcoded secrets are introduced.
14. CI includes lifecycle checks for Kernel and the Digital Marketing pilot.

---

# 13. Recommended implementation order

Use small PR-sized slices.

## Slice 1 — Planning correctness

Files:

```text
platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePhase.ts
platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts
platform/typescript/kernel-lifecycle/src/planning/__tests__/ProductLifecyclePlanner.test.ts
scripts/kernel-product.mjs
```

Outcome:

```bash
pnpm plan:build:digital-marketing
pnpm plan:deploy:local:digital-marketing
pnpm plan:verify:local:digital-marketing
```

all produce correct plans.

---

## Slice 2 — Adapter execution path

Files:

```text
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts
platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleStepRunner.ts
platform/typescript/kernel-toolchains/src/ToolchainAdapterRegistry.ts
platform/typescript/kernel-toolchains/src/index.ts
scripts/kernel-product.mjs
```

Outcome:

Kernel execution uses adapters, not shell bypass.

---

## Slice 3 — Digital Marketing build/test through Kernel

Files:

```text
products/digital-marketing/kernel-product.yaml
products/digital-marketing/dm-api/build.gradle.kts
products/digital-marketing/ui/package.json
scripts/check-digital-marketing-lifecycle-pilot.mjs
```

Outcome:

```bash
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
```

work through Kernel.

---

## Slice 4 — Package/deploy/verify local

Files:

```text
platform/typescript/kernel-toolchains/src/adapters/DockerBuildxAdapter.ts
platform/typescript/kernel-toolchains/src/adapters/ComposeLocalAdapter.ts
platform/typescript/kernel-deployment/src/verifier/DeploymentVerifier.ts
products/digital-marketing/dm-api/Dockerfile
products/digital-marketing/ui/Dockerfile
products/digital-marketing/ui/nginx.conf
products/digital-marketing/deploy/local.compose.yaml
products/digital-marketing/deploy/local.env.example
products/digital-marketing/deploy/.gitignore
products/digital-marketing/kernel-product.yaml
```

Outcome:

```bash
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
```

work through Kernel.

---

## Slice 5 — Conformance and CI

Files:

```text
scripts/check-product-lifecycle-contracts.mjs
scripts/check-product-artifact-contracts.mjs
scripts/check-product-deployment-contracts.mjs
scripts/check-toolchain-adapter-contracts.mjs
scripts/check-kernel-platform-lifecycle.mjs
.github/workflows/product-lifecycle.yml
package.json
```

Outcome:

Kernel lifecycle and Digital Marketing pilot are guarded in CI.

---

## Slice 6 — Scaffolder and docs

Files:

```text
scripts/scaffold-product.mjs
scripts/check-product-scaffolder.mjs
docs/kernel/00-VISION.md
docs/kernel/02-PRODUCT_LIFECYCLE.md
docs/kernel/03-TOOLCHAIN_ADAPTERS.md
docs/kernel/04-ARTIFACTS.md
docs/kernel/05-DEPLOYMENT.md
docs/kernel/09-PRODUCT_DEVELOPER_GUIDE.md
docs/kernel/10-POWER_USER_EXTENSION_GUIDE.md
docs/kernel/11-MIGRATION_GUIDE.md
```

Outcome:

Future products can be scaffolded into lifecycle-ready shape without becoming automatically enabled.

---

# 14. Explicit non-goals for this phase

Do **not** do these in this phase:

1. Do not migrate PHR, Finance, FlashIt, Aura, DCMAAR to enabled lifecycle.
2. Do not enable Kubernetes/Helm/Terraform deployment.
3. Do not enable mobile lifecycle for FlashIt.
4. Do not replace existing Gradle or pnpm tooling.
5. Do not move Digital Marketing domain logic into Kernel.
6. Do not add new libraries when existing repo tooling works.
7. Do not remove legacy product scripts until lifecycle coverage is proven.
8. Do not treat documentation-only lifecycle declarations as implementation complete.

---

# 15. Summary

At commit `4649ce641eec73b638db9428498c2409cbe54497`, Kernel already has the right skeleton:

```text
kernel-product CLI
kernel-lifecycle package
kernel-toolchains package
kernel-artifacts package
kernel-deployment package
kernel-release package
Digital Marketing lifecycle config
Digital Marketing lifecycle root scripts
lifecycle profiles
toolchain adapter registry
registry lifecycle schema
```

The next implementation must focus on **stabilization and completion**:

```text
1. Use real adapters instead of shell bypass.
2. Fix package/deploy/verify planning.
3. Emit real artifact/deployment manifests.
4. Harden Digital Marketing local packaging/deploy.
5. Add fail-closed conformance checks.
6. Preserve compatibility for other products.
```

Once Digital Marketing works end-to-end through Kernel, Kernel can then safely migrate PHR, Finance, FlashIt, Data Cloud, YAPPC, TutorPutor, Aura, and DCMAAR using product-specific lifecycle profiles instead of one-size-fits-all scripts.
