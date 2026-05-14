# Kernel Lifecycle Remaining Items Tracker

This file contains only tasks that are not yet fully implemented, correct, and verified.
Items are removed **only** after code is implemented, tests pass, and behavior is verified.

**Source:** `docs/kernel/KERNEL-TODOS.md`
**Last Updated:** 2026-05-14

---

## Phase 1 — Lifecycle engine planning and execution

### 1.1 ProductLifecyclePhase.ts domain types
- [x] Consolidate `LifecyclePlan` and `ProductLifecyclePlan` (currently duplicated)
- [x] Add tests in `domain/__tests__/ProductLifecyclePhase.test.ts`

> **Done (verified):** `stepKind`, `adapterContext`, `phaseMode`, `runId` added to domain types and used by all adapters.

### 1.3 ProductLifecycleExecutor.ts — DAG + parallel execution
- [x] Implement DAG-based execution (dependency-aware, not always sequential)
- [x] Honor `phaseMode: 'parallel'` — run independent steps concurrently
- [x] Write `deployment-manifest.json` after deploy phase
- [x] Write `test-summary.json` after test phase

> **Done (verified):** `spawnSync` removed, adapter context built from `step.adapterContext`, artifacts captured, `artifact-manifest.json` written by PnpmViteReactAdapter for package phase, 5 tests pass.

### 1.4 ProductLifecycleStepRunner.ts
- [x] Expand `StepContext` with full product/surface/deployment config
- [x] Build adapter context from `step.adapterContext` (not guessed values)
- [x] Fail closed when adapter returns `skipped` for required non-dry-run step
- [x] Add tests

### 1.6 ArtifactWriter.ts — integrate with kernel-artifacts
- [x] Delegate to `ProductArtifactRegistry` / `ArtifactFingerprintCalculator`
- [x] Support container image artifacts (image/tag/digest/localImageId)
- [x] Add ArtifactWriter tests with filesystem temp dirs

> **Done (verified):** 1.2 ProductLifecyclePlanner fully implemented (routing, loadProductConfig from YAML, runId output dir, allowExperimentalAdapters validation, gates, expectedArtifacts, 19 tests pass). 1.5 ExecutionResultCollector runId added. 1.7 scripts/kernel-product.mjs uses real ToolchainAdapterRegistry with all safe adapters.

---

## Phase 2 — Adapter completion

### 2.3 GradleJavaServiceAdapter.ts
- [x] `dev` phase: use foreground process, stream logs, write `processes.json`
- [x] `dev` phase: do not validate `dist`/jar outputs — check health endpoint instead
- [x] `test` phase: validate test result directories
- [x] Expand tests for dev/test/build phases

### 2.4 PnpmViteReactAdapter.ts
- [x] `dev` phase: long-running Vite server, stream logs, write `processes.json`
- [x] `dev` phase: do not validate `dist/`
- [x] `package` phase: reject if expected artifact is container image (delegate to docker-buildx)

> **Done (verified):** 2.1 discriminated result types added. 2.2 ToolchainAdapterRegistry factory + bridge, all tests pass (84 total in kernel-toolchains). 2.4 Docker artifact manifest generation: Dockerfile detection, writes `build/artifact-manifest.json`, 2 tests pass. 2.5 DockerBuildxAdapter: SpawnCommandRunner, `docker image inspect` for validateOutputs/extractArtifacts, typed artifacts, tests pass. 2.6 ComposeLocalAdapter: verify phase, deploy with env file, rollback with `docker compose down`, fs.access unit mock, 28 tests pass.

### 2.6 ComposeLocalAdapter.ts
- [x] `verify`: run HTTP health checks from product config; support timeout/retry settings
- [x] Produce deployment manifest (project, services, env, health URLs, startedAt, completedAt)

---

## Phase 3 — Digital Marketing pilot hardening

### 3.1 products/digital-marketing/kernel-product.yaml
- [x] Fix backend health path to `/health/live` + `/health/ready` (was `/health`)
- [x] Add explicit `package` section with `docker-buildx` adapter for backend-api and web
- [x] Add explicit `deployment.local.adapter: compose-local` and all required deployment fields
- [x] Add `verify.local` section with health check URLs using `/health/ready`

### 3.2 products/digital-marketing/deploy/local.compose.yaml
- [x] Replace shared template Dockerfiles with product-local Dockerfiles
- [x] Fix backend health check path: use `/health/ready`
- [x] Use `${DMOS_API_PORT:-8080}` variable for ports
- [x] Add Kernel labels: `ghatana.kernel.product: digital-marketing` on each service

### 3.3 products/digital-marketing/deploy/local.env.example
- [x] Remove `DATABASE_PASSWORD=postgres` — replace with placeholder `<set-local-postgres-password>`
- [x] Add `DMOS_ENV=development`
- [x] Add comment explaining `local.env` is gitignored and developer-created

### 3.4 products/digital-marketing/deploy/.gitignore
- [x] Add file with `local.env` and `*.local.env`

### 3.5 products/digital-marketing/dm-api/Dockerfile
- [x] Create product-specific Dockerfile (multi-stage, non-root user, healthcheck on `/health/ready`)

### 3.6 products/digital-marketing/ui/Dockerfile
- [x] Create product-specific Dockerfile (multi-stage node+nginx, healthcheck)

### 3.6b products/digital-marketing/ui/nginx.conf
- [x] Create nginx config for serving Vite SPA

### 3.8 products/digital-marketing/dm-api/src — DmosApiServer.java
- [x] Verify `/health/live` and `/health/ready` routes exist
- [x] Optionally add `/health` alias for readiness

---

## Phase 4 — Artifact, package, deploy, verify integration

### 4.4 DeploymentVerifier.ts
- [x] Implement HTTP health checks from deployment config with timeout/retry
- [x] Record health check result (status, URL, latency, error, checkedAt)
- [x] Fail closed if required health check fails
- [x] Add tests

### 4.5 DeploymentManifest.ts
- [x] Add `productId`, `environment`, `target`, `artifactManifestRef`, `services`, `healthChecks`, `status`, `deployedAt`, `verifierResult`
- [x] Add schema validation

---

## Phase 5 — Config and conformance hardening

### 5.1 config/toolchain-adapter-registry.json
- [x] Set `docker-buildx` to `status: implemented`, `safeForDefault: true` once Phase 2.5 is complete
- [x] Set `compose-local` to `status: implemented`, `safeForDefault: true` once Phase 2.6 is complete

### 5.2 config/product-lifecycle-profiles.json
- [x] Add `phaseOutputs` for `package`, `deploy`, `verify` phases (not just `build`)
- [x] Keep `mobile-plus-api-product.safeForDefault: false`

### 5.4 config/canonical-product-registry-schema.json
- [x] Require `lifecycleProfile`, `lifecycleConfigPath`, `lifecycle.enabled`, `toolchain`, `artifacts`, `deployment`, `environments` when `lifecycleStatus = enabled`

### 5.6 scripts/check-product-lifecycle-contracts.mjs
- [x] Also plan `package`, `deploy --env local`, `verify --env local` for enabled products
- [x] Validate registry/YAML drift
- [x] Validate enabled products use only safe adapters

### 5.9 scripts/check-toolchain-adapter-contracts.mjs
- [x] Enforce: `safeForDefault: true` requires `status: implemented` + all three `*Implemented: true`
- [x] Validate implementation file exists
- [x] Validate exported in `kernel-toolchains/src/index.ts`

### 5.10 New: scripts/check-digital-marketing-lifecycle-pilot.mjs
- [x] Validate `kernel-product.yaml` parses correctly
- [x] Validate registry says lifecycle enabled
- [x] Validate build/test/package/deploy/verify plans work
- [x] Validate no deploy plan uses Gradle/pnpm surface adapters
- [x] Validate package uses Docker adapter
- [x] Validate deploy uses Compose adapter
- [x] Validate health paths match `/health/ready` and `/health/live`
- [x] Validate env example has no unsafe secret defaults
- [x] Validate compose file exists and has Kernel labels

### 5.10b New: scripts/check-lifecycle-registry-config-drift.mjs
- [x] Compare registry lifecycle fields with `kernel-product.yaml` per enabled product

---

## Phase 6 — Root scripts and compatibility

### 6.1 package.json
- [x] Add `check:digital-marketing-lifecycle-pilot` script
- [x] Add `check:lifecycle-registry-config-drift` script
- [x] Ensure `pnpm check:kernel-platform-lifecycle` triggers all new checks

### 6.3 scripts/generate-product-registry-artifacts.mjs
- [x] Generate lifecycle-aware root scripts for lifecycle-enabled products

---

## Phase 7 — Digital Marketing gates and quality

### 7.4 New: scripts/check-digital-marketing-lifecycle-pilot.mjs (full implementation)
- [x] See 5.10 above (combined)

---

## Phase 8 — Product compatibility matrix

### 8.1 docs/kernel/11-MIGRATION_GUIDE.md
- [x] Add product readiness matrix (Digital Marketing, PHR, Finance, FlashIt, Aura, DCMAAR, Data Cloud, YAPPC)

---

## Phase 9 — CI/CD

### 9.1 .github/workflows/product-lifecycle.yml
- [x] Add workflow for PRs touching Kernel lifecycle or Digital Marketing
- [x] Jobs: install, build kernel packages, check, plan phases, validate/test/build
- [x] Do not deploy in PR
