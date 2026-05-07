# Historical AEP First-Pass Move Plan

## Purpose

This document is retained as historical migration evidence for the first pass that moved the standalone AEP product tree under Data Cloud while keeping behavior, Java packages, and public contracts stable.

It is no longer the canonical target architecture. The current target is the plane-based architecture in `../architecture/PLANE_ARCHITECTURE.md`, where AEP is the runtime implementation behind the Data Cloud Action Plane.

Working rule for this series:

- Product root after migration: `products/data-cloud`
- Action Plane runtime root after first-pass migration: `products/data-cloud/planes/action`
- Product-level public contracts after migration: `products/data-cloud/contracts`
- Internal AEP extension/runtime contracts after migration: `products/data-cloud/planes/action/operator-contracts`
- Java packages stay unchanged in this pass: keep `com.ghatana.aep.*` and `com.ghatana.datacloud.*`

This document is path-specific because the current repository has standalone AEP Gradle modules, standalone AEP CI/CD, Node workspaces, deployment assets, generated docs, and contract sync tasks that still point at `products/aep`.

## Verified Current State

Confirmed in the repository on 2026-05-06:

- AEP and Data Cloud modules are included from the root `settings.gradle.kts`; neither product root has its own `settings.gradle.kts`.
- Root `settings.gradle.kts` includes AEP Gradle modules at `:products:aep:*` and Data Cloud modules at `:products:data-cloud:*`.
- Root `build.gradle.kts` has a `sharedProductApiAllowlist` that still enumerates AEP project paths.
- AEP public OpenAPI validation lives in `products/aep/contracts/build.gradle.kts`; the local `specFile` is `products/aep/contracts/openapi.yaml`.
- AEP server sync currently uses `platform/contracts/openapi/aep.yaml` as canonical and treats `products/aep/contracts/openapi.yaml` as a legacy alias.
- Data Cloud SDK generation currently reads `products/data-cloud/delivery/api-contract-tests/openapi.yaml`.
- Data Cloud router drift checks in `products/data-cloud/delivery/launcher/build.gradle.kts` also read `products/data-cloud/delivery/api-contract-tests/openapi.yaml`.
- `platform/contracts/build.gradle.kts` copies Data Cloud OpenAPI from `products/data-cloud/delivery/api-contract-tests/openapi.yaml`.
- `pnpm-workspace.yaml` includes `products/aep/ui` and `products/aep/gateway`.
- AEP non-Gradle surfaces exist and must move: `gateway`, `ui`, `agent-catalog`, `helm`, `k8s`, `scripts`, `test-scripts`, `docs`, `docs-generated`, `services`, root docs, and `Dockerfile`.

Known stale references that must be fixed rather than preserved:

- Workflow Gradle targets reference non-existent AEP projects: `:products:aep:launcher`, `:products:aep:platform`, `:products:aep:aep-runtime-core`, and `:products:aep:gateway`.
- `.github/workflows/pr-checks.yml` references `./products/aep/gradlew`, but `products/aep/gradlew` does not exist.
- Some docs reference old or absent surfaces such as `products/aep/docker-compose.yml`, `products/aep/api`, and `products/aep/platform-registry`.

## Execution Rules

- Move source and tests with their owning module directories.
- Do not rename Java packages, TypeScript package names, Maven group IDs, or OpenAPI operation IDs in this pass unless a build break forces a local path-only correction.
- Do not edit generated or ephemeral outputs: `build/`, `dist/`, `node_modules/`, `.react-router/`, `playwright-report/`, `.idea/`.
- Update path-bearing references in the same commit that invalidates the old path.
- Only Data Cloud launchers, distribution surfaces, integration tests, and explicitly allowed product integration modules may compose Data Cloud core with AEP implementation modules.
- Data Cloud core modules must not gain implementation dependencies on AEP runtime modules during the path move.
- Historical audit artifacts under `code-audits/**` and `docs/audits/**` may keep old paths if they are clearly historical.

## Phase 1: Create Product-Level Contracts

Create `products/data-cloud/contracts` before moving AEP server and API modules. The contracts module becomes the product-level home for public machine-readable specs.

| Current path | Target path | Action | Notes |
| --- | --- | --- | --- |
| `products/aep/contracts/openapi.yaml` | `products/data-cloud/contracts/openapi/aep.yaml` | Move/copy canonical AEP spec | This becomes the product-level AEP public spec. |
| `products/data-cloud/delivery/api-contract-tests/openapi.yaml` | `products/data-cloud/contracts/openapi/data-cloud.yaml` | Copy first, then repoint consumers | Keep the old file temporarily only as a compatibility alias until consumers are repointed. |
| `products/aep/contracts/build.gradle.kts` | `products/data-cloud/contracts/build.gradle.kts` | Rebuild, do not copy verbatim | Preserve validation/codegen intent, but remove stale comments and paths. |
| `products/aep/contracts/README.md` | `products/data-cloud/contracts/README.md` | Merge/adapt | Document both `data-cloud.yaml` and `aep.yaml`. |
| `products/aep/contracts/OWNER.md` | `products/data-cloud/contracts/OWNER.md` | Merge/adapt | Prefer a product-level contracts owner doc. |
| `platform/contracts/openapi/aep.yaml` | keep | Repoint sync references only | Platform registry copy remains unless a later platform-contract cleanup removes it. |

Required edits in this phase:

- `settings.gradle.kts`: add `include(":products:data-cloud:contracts")`.
- `products/data-cloud/contracts/build.gradle.kts`: add tasks for `validateDataCloudSpec`, `validateAepSpec`, and `checkProductOpenApiSync`.
- `products/data-cloud/delivery/sdk/build.gradle.kts`: change `openApiSpec` from `projectDir.parentFile.resolve("api/openapi.yaml")` to `projectDir.parentFile.resolve("contracts/openapi/data-cloud.yaml")`.
- `products/data-cloud/delivery/launcher/build.gradle.kts`: change `checkDataCloudOpenApiSync.openapiSpec` to `products/data-cloud/contracts/openapi/data-cloud.yaml`.
- `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/OpenApiRouteAlignmentTest.java`: change repo lookup from `products/data-cloud/delivery/api-contract-tests/openapi.yaml` to `products/data-cloud/contracts/openapi/data-cloud.yaml`.
- `platform/contracts/build.gradle.kts`: copy Data Cloud OpenAPI from `products/data-cloud/contracts/openapi/data-cloud.yaml`.
- `platform/contracts/src/test/java/com/ghatana/contracts/openapi/DataCloudContractTest.java`: update the documented spec path.
- `scripts/check-documentation-truth.mjs`, `scripts/check-openapi-contract-canonical.mjs`, and `scripts/check-truth-surfaces.mjs`: replace old AEP/Data Cloud spec paths with the new contract paths while preserving runtime-copy checks where they still apply.

Temporary compatibility rule:

- If `products/data-cloud/delivery/api-contract-tests/openapi.yaml` is kept during the first commit, add an explicit TODO comment or doc note that it is a temporary alias. Do not let SDK generation, router drift checks, or platform contract copying continue to treat it as canonical.

## Phase 2: Move AEP Gradle Modules

Move each Gradle-backed AEP module as a whole, including `build.gradle.kts`, `src/main`, `src/test`, resources, README, and OWNER files.

| Current path | Target path | New Gradle project path |
| --- | --- | --- |
| `products/aep/aep-operator-contracts` | `products/data-cloud/planes/action/operator-contracts` | `:products:data-cloud:planes:action:operator-contracts` |
| `products/aep/aep-central-runtime` | `products/data-cloud/planes/action/central-runtime` | `:products:data-cloud:planes:action:central-runtime` |
| `products/aep/aep-engine` | `products/data-cloud/planes/action/engine` | `:products:data-cloud:planes:action:engine` |
| `products/aep/aep-registry` | `products/data-cloud/planes/action/registry` | `:products:data-cloud:planes:action:registry` |
| `products/aep/aep-analytics` | `products/data-cloud/planes/action/analytics` | `:products:data-cloud:planes:action:analytics` |
| `products/aep/aep-security` | `products/data-cloud/planes/action/security` | `:products:data-cloud:planes:action:security` |
| `products/aep/aep-event-cloud` | `products/data-cloud/planes/action/event-bridge` | `:products:data-cloud:planes:action:event-bridge` |
| `products/aep/aep-agent-runtime` | `products/data-cloud/planes/action/agent-runtime` | `:products:data-cloud:planes:action:agent-runtime` |
| `products/aep/aep-api` | `products/data-cloud/planes/action/api` | `:products:data-cloud:planes:action:api` |
| `products/aep/aep-scaling` | `products/data-cloud/planes/action/scaling` | `:products:data-cloud:planes:action:scaling` |
| `products/aep/aep-observability` | `products/data-cloud/planes/action/observability` | `:products:data-cloud:planes:action:observability` |
| `products/aep/orchestrator` | `products/data-cloud/planes/action/orchestrator` | `:products:data-cloud:planes:action:orchestrator` |
| `products/aep/server` | `products/data-cloud/planes/action/server` | `:products:data-cloud:planes:action:server` |
| `products/aep/aep-identity` | `products/data-cloud/planes/action/identity` | `:products:data-cloud:planes:action:identity` |
| `products/aep/aep-compliance` | `products/data-cloud/planes/action/compliance` | `:products:data-cloud:planes:action:compliance` |
| `products/aep/kernel-bridge` | `products/data-cloud/planes/action/kernel-bridge` | `:products:data-cloud:planes:action:kernel-bridge` |

Required edits in this phase:

- `settings.gradle.kts`: remove `include(":products:aep:...")` lines and add the new `:products:data-cloud:planes:action:*` includes above.
- Root `build.gradle.kts`: replace AEP allowlist entries with the new project paths.
- Every moved AEP `build.gradle.kts`: rewrite internal AEP dependencies using the mapping table below.
- `integration-tests/cross-service-workflow/build.gradle.kts`: replace `:products:data-cloud:planes:action:orchestrator` with `:products:data-cloud:planes:action:orchestrator`.
- Cross-product consumers in `products/yappc/**`, `products/virtual-org/**`, and `products/software-org/**`: replace only the exact AEP project path dependency with the mapped project path. Do not introduce new dependencies.

Gradle dependency rewrite map:

| Old project path | New project path |
| --- | --- |
| `:products:data-cloud:planes:action:operator-contracts` | `:products:data-cloud:planes:action:operator-contracts` |
| `:products:data-cloud:planes:action:central-runtime` | `:products:data-cloud:planes:action:central-runtime` |
| `:products:data-cloud:planes:action:engine` | `:products:data-cloud:planes:action:engine` |
| `:products:data-cloud:planes:action:registry` | `:products:data-cloud:planes:action:registry` |
| `:products:data-cloud:planes:action:analytics` | `:products:data-cloud:planes:action:analytics` |
| `:products:data-cloud:planes:action:security` | `:products:data-cloud:planes:action:security` |
| `:products:data-cloud:planes:action:event-bridge` | `:products:data-cloud:planes:action:event-bridge` |
| `:products:data-cloud:planes:action:agent-runtime` | `:products:data-cloud:planes:action:agent-runtime` |
| `:products:data-cloud:planes:action:api` | `:products:data-cloud:planes:action:api` |
| `:products:data-cloud:planes:action:scaling` | `:products:data-cloud:planes:action:scaling` |
| `:products:data-cloud:planes:action:observability` | `:products:data-cloud:planes:action:observability` |
| `:products:data-cloud:planes:action:orchestrator` | `:products:data-cloud:planes:action:orchestrator` |
| `:products:data-cloud:planes:action:server` | `:products:data-cloud:planes:action:server` |
| `:products:data-cloud:planes:action:identity` | `:products:data-cloud:planes:action:identity` |
| `:products:data-cloud:planes:action:compliance` | `:products:data-cloud:planes:action:compliance` |
| `:products:data-cloud:planes:action:kernel-bridge` | `:products:data-cloud:planes:action:kernel-bridge` |
| `:products:data-cloud:contracts` | `:products:data-cloud:contracts` |

Stale Gradle references to remove or retarget:

- Replace `:products:aep:launcher` workflow references with `:products:data-cloud:planes:action:server` or the appropriate Data Cloud distribution task. There is no current AEP launcher module.
- Replace `:products:aep:platform` workflow references with the concrete new module path that owns the tested behavior. There is no current AEP platform module.
- Replace `:products:aep:aep-runtime-core` references with `:products:data-cloud:planes:action:agent-runtime` only if the intended surface is advanced agent runtime; otherwise use `:products:data-cloud:planes:action:engine`.
- Remove Gradle references to `:products:aep:gateway`; `products/aep/gateway` is a Node workspace, not a Gradle module.
- Replace `./products/aep/gradlew` commands with root `./gradlew` commands.

## Phase 3: Move AEP Non-Gradle Runtime and Delivery Surfaces

Move these after the Gradle project paths compile, unless a phase needs one of these paths earlier for tests.

| Current path | Target path | Action | Required reference updates |
| --- | --- | --- | --- |
| `products/aep/gateway` | `products/data-cloud/planes/action/gateway` | Move Node workspace | `pnpm-workspace.yaml`, `pnpm-lock.yaml` regeneration, CI gateway commands, OpenAPI drift checks. |
| `products/aep/ui` | `products/data-cloud/planes/action/ui` | Move Node workspace | `pnpm-workspace.yaml`, `pnpm-lock.yaml` regeneration, UI CI, e2e artifact paths. |
| `products/aep/agent-catalog` | `products/data-cloud/planes/action/agent-catalog` | Move | `platform/agent-catalog/catalog-roots.txt`, `AepCentralCatalogService`, catalog tests. |
| `products/aep/helm` | `products/data-cloud/planes/action/helm` | Move | Workflow paths such as `products/aep/helm/aep/values.yaml`. |
| `products/aep/k8s` | `products/data-cloud/planes/action/k8s` | Move | Gitea CD paths and kustomize README examples. |
| `products/aep/scripts` | `products/data-cloud/planes/action/scripts` | Move | Any local script path references. |
| `products/aep/test-scripts` | `products/data-cloud/planes/action/test-scripts` | Move | Benchmark docs and k6 examples. |
| `products/aep/docs-generated` | `products/data-cloud/docs/aep/generated` | Move | Keep generated docs out of runtime subtree. |
| `products/aep/Dockerfile` | `products/data-cloud/planes/action/Dockerfile` | Move for first pass | Update COPY paths and Gradle tasks inside the Dockerfile. |

Do not move these generated/ephemeral child directories:

- `products/aep/gateway/dist`
- `products/aep/gateway/node_modules`
- `products/aep/ui/node_modules`
- `products/aep/ui/.react-router`
- `products/aep/ui/playwright-report`

Required `pnpm` edits:

- `pnpm-workspace.yaml`: replace `products/aep/ui` with `products/data-cloud/planes/action/ui`.
- `pnpm-workspace.yaml`: replace `products/aep/gateway` with `products/data-cloud/planes/action/gateway`.
- `pnpm-lock.yaml`: regenerate after moving the workspaces. Do not hand-edit the lockfile.
- Moved `package.json` scripts: check relative paths after the move before running CI.

## Phase 4: Move AEP Docs, Metadata, and Loose Source

| Current path | Target path | Action |
| --- | --- | --- |
| `products/aep/README.md` | `products/data-cloud/docs/aep/README.md` | Move/adapt |
| `products/aep/OWNER.md` | `products/data-cloud/docs/aep/OWNER.md` | Move/adapt |
| `products/aep/AEP_COMPREHENSIVE_OVERVIEW.md` | `products/data-cloud/docs/aep/AEP_COMPREHENSIVE_OVERVIEW.md` | Move/adapt |
| `products/aep/AI_SUGGESTIONS_FLOW.md` | `products/data-cloud/docs/aep/AI_SUGGESTIONS_FLOW.md` | Move/adapt |
| `products/aep/docs/**` | `products/data-cloud/docs/aep/**` | Move |
| `products/aep/services/AnomalyDetectionService.java` | `products/data-cloud/planes/action/services/AnomalyDetectionService.java` | Move as loose source first |

Follow-up for `AnomalyDetectionService.java`:

- After the path migration compiles, either add an owning Gradle module for `products/data-cloud/planes/action/services` or absorb the file into the existing AEP module that actually owns anomaly detection. Do not delete `products/aep` until this loose file is accounted for.

Update these existing Data Cloud product docs in place:

- `products/data-cloud/README.md`
- `products/data-cloud/OWNER.md`
- `products/data-cloud/data-cloud-canonical-architecture-spec.md`
- `products/data-cloud/DEVELOPER_MANUAL.md`
- `products/data-cloud/TEST_MANUAL.md`
- `products/data-cloud/USER_MANUAL.md`
- `products/data-cloud/RUNBOOK.md`
- `products/data-cloud/REST_API_DOCUMENTATION.md`

## Phase 5: Update CI/CD, Ownership, and Governance

Files with confirmed direct `products/aep` or `:products:aep:` references:

- `.github/CODEOWNERS`
- `.gitea/CODEOWNERS`
- `.github/workflows/aep-ci.yml`
- `.github/workflows/aep-data-cloud-architecture-gates.yml`
- `.github/workflows/ci.yml`
- `.github/workflows/doc-governance.yml`
- `.github/workflows/e2e-tests.yml`
- `.github/workflows/infra-backed-ci.yml`
- `.github/workflows/platform-module-tests.yml`
- `.github/workflows/pr-checks.yml`
- `.github/workflows/product-isolated-ci.yml`
- `.github/workflows/production-hardening.yml`
- `.github/workflows/plugin-contract-tests.yml`
- `.github/workflows/sonarqube.yml`
- `.github/workflows/ui-package-gates.yml`
- `.gitea/workflows/aep-ci.yml`
- `.gitea/workflows/aep-cd.yml`
- `.gitea/workflows/sonarqube.yml`

Required CI/CD changes:

- Replace path filters from `products/aep/**` with `products/data-cloud/planes/action/**`.
- Add `products/data-cloud/contracts/**` to workflows that validate OpenAPI, SDKs, product architecture, or AEP runtime/API compatibility.
- Replace all `:products:aep:*` Gradle task paths with the new mapped project paths.
- Remove or fix stale non-existent targets listed in Phase 2.
- Replace deployment paths: `products/aep/Dockerfile`, `products/aep/k8s`, and `products/aep/helm/aep/values.yaml`.
- Replace UI commands that run in `products/aep/ui`.
- Replace gateway commands that run in `products/aep/gateway`.
- Update artifact globs from `products/aep/**/build/...` to `products/data-cloud/planes/action/**/build/...`.

Required governance edits:

- `.github/CODEOWNERS`: replace `/products/aep/` with `/products/data-cloud/planes/action/`; add `/products/data-cloud/contracts/` ownership if needed.
- `.gitea/CODEOWNERS`: replace `products/aep/` with `products/data-cloud/planes/action/`; add contracts ownership if needed.
- `platform/agent-catalog/catalog-roots.txt`: replace `products/aep/agent-catalog` with `products/data-cloud/planes/action/agent-catalog`.
- `gradle/product-isolation.gradle` and product-isolation docs/tests: verify product-name derivation still handles nested `products/data-cloud/planes/action` paths as Data Cloud capability paths, not as a separate top-level product.

## Phase 6: Update Source, Test, Script, and Runtime Path Literals

Confirmed active path literals that must change:

| Current file | Required update |
| --- | --- |
| `products/data-cloud/planes/action/engine/src/main/java/com/ghatana/aep/catalog/AepCentralCatalogService.java` | Replace fallback catalog root `products/aep/agent-catalog` with `products/data-cloud/planes/action/agent-catalog`; update Javadoc path. |
| `products/data-cloud/planes/action/orchestrator/src/test/java/com/ghatana/aep/integration/registry/CatalogCanonicalValuesTest.java` | Replace `products/aep/agent-catalog/operators` with `products/data-cloud/planes/action/agent-catalog/operators`. |
| `products/data-cloud/planes/action/server/build.gradle.kts` | Replace `products/aep/contracts/openapi.yaml`, `:products:data-cloud:planes:action:server:syncOpenApiSpec`, and drift messages with the new contract/server paths. |
| `products/data-cloud/planes/action/server/src/test/java/com/ghatana/aep/server/http/AepOpenApiSurfaceDriftTest.java` | Replace contract lookup with `products/data-cloud/contracts/openapi/aep.yaml`; replace server runtime resource path with the moved server path. |
| `products/data-cloud/planes/action/docs/AEP_BENCHMARKS.md` | Replace `products/aep/test-scripts/...` examples. |
| `products/data-cloud/planes/action/k8s/multi-region/README.md` | Replace baseline kustomize paths. |
| `products/data-cloud/planes/action/Dockerfile` | Replace `COPY products/aep/ ...`, Gradle task paths, and output jar paths with new paths. |
| `products/data-cloud/planes/action/ui/DESIGN_SYSTEM_ADOPTION.md` | Replace scan examples from `products/aep/ui/src` to `products/data-cloud/planes/action/ui/src`. |
| `products/data-cloud/planes/action/gateway/README.md` | Replace workspace path in overview. |
| `scripts/generate-audit-todo-burndown.mjs` | Replace AEP root path if the script should include the migrated AEP subtree. |
| `scripts/scan-production-placeholders.sh` | Replace scanned AEP root. |

Contract literal updates:

- Replace `products/aep/contracts/openapi.yaml` with `products/data-cloud/contracts/openapi/aep.yaml`.
- Replace `products/data-cloud/delivery/api-contract-tests/openapi.yaml` with `products/data-cloud/contracts/openapi/data-cloud.yaml` in active validation/codegen/docs.
- Keep `products/data-cloud/planes/action/server/src/main/resources/openapi.yaml` as the runtime copy path after server moves.
- Keep `platform/contracts/openapi/aep.yaml` as the platform registry copy unless a later contract consolidation removes it.

## Documentation Reference Policy

Update active docs in the same migration series:

- `docs/ONBOARDING.md`
- `docs/CROSS_PRODUCT_INTEGRATION_POINTS.md`
- `docs/DATA_CLOUD_OWNERSHIP_CLARIFICATION.md`
- `docs/SHARED_SERVICES_ARCHITECTURE.md`
- `docs/GOVERNANCE.md`
- `docs/README.md`
- `docs/SECRETS_CLASSIFICATION.md`
- `docs/agent-system/README.md`
- `docs/agent-system/ARCHITECTURE.md`
- `docs/architecture/ARCHITECTURE_RULES.md`
- `docs/architecture/DEPENDENCY_DIRECTION_REMEDIATION_PLAN_2026-04-23.md`
- `docs/architecture/UNIFIED_PLATFORM_IMPLEMENTATION_PLAN.md`
- `docs/adr/ADR-012-keep-aep-gateway.md`
- `docs/adr/ADR-020-agent-system-five-layer-architecture.md`
- `docs/runbooks/aep-degraded-mode-runbook.md`
- `docs/generated/CAPABILITY_MATRIX.md`
- `docs/kernel/BUILD.md`
- `docs/kernel/COVERAGE_MATRIX.md`
- `docs/kernel/PRODUCT_KERNEL_CAPABILITY_MAP.md`
- `gradle/PRODUCT_BUILD_GUIDE.md`
- `shared-services/infrastructure/k8s/README.md`

Preserve historical path references unless explicitly archived/annotated:

- `code-audits/**`
- `docs/audits/**`
- product archive folders such as `products/yappc/docs/archive/**`
- root audit snapshots such as `ghatana-data-cloud-aep-end-to-end-audit.md`

## Search Buckets To Clear Before Deleting `products/aep`

Run these searches after each migration commit that changes paths:

```text
git grep -n ":products:aep:" -- . ":(exclude)code-audits/**" ":(exclude)docs/audits/**" ":(exclude)products/data-cloud/docs/migration/**"
git grep -n "products/aep/" -- . ":(exclude)code-audits/**" ":(exclude)docs/audits/**" ":(exclude)products/data-cloud/docs/migration/**"
git grep -n "products/aep" -- . ":(exclude)code-audits/**" ":(exclude)docs/audits/**" ":(exclude)products/data-cloud/docs/migration/**"
git grep -n "products/data-cloud/delivery/api-contract-tests/openapi.yaml" -- . ":(exclude)products/data-cloud/docs/migration/**"
git grep -n "products/aep/contracts/openapi.yaml" -- . ":(exclude)code-audits/**" ":(exclude)docs/audits/**" ":(exclude)products/data-cloud/docs/migration/**"
git grep -n "products/aep/server/src/main/resources/openapi.yaml" -- . ":(exclude)code-audits/**" ":(exclude)docs/audits/**" ":(exclude)products/data-cloud/docs/migration/**"
git grep -n "products/aep/gateway\\|products/aep/ui\\|products/aep/agent-catalog\\|products/aep/helm\\|products/aep/k8s\\|products/aep/test-scripts" -- . ":(exclude)code-audits/**" ":(exclude)docs/audits/**" ":(exclude)products/data-cloud/docs/migration/**"
```

Allowed temporary exceptions:

- This migration plan and other active migration docs under `products/data-cloud/docs/migration/**`.
- Historical audit/archive docs only when they are clearly marked as historical.

## Suggested Commit Order

1. Update this migration plan and add/adjust any stale-path enforcement script.
2. Create `products/data-cloud/contracts`; move/copy OpenAPI specs; repoint SDK, router drift, platform contract copy, and contract truth scripts.
3. Move Gradle-backed AEP modules under `products/data-cloud/planes/action`; update root `settings.gradle.kts`, root `build.gradle.kts`, and all Gradle project dependencies.
4. Fix stale CI Gradle targets that point at non-existent AEP projects.
5. Move gateway, UI, agent catalog, deployment assets, scripts, generated docs, Dockerfile, and loose `services` source.
6. Update CI/CD, CODEOWNERS, pnpm workspace paths, and product-isolated filters; regenerate `pnpm-lock.yaml`.
7. Update path literals in source, tests, runtime sync checks, scripts, and active docs.
8. Add or update boundary tests/stale-reference enforcement.
9. Delete residual `products/aep` only after no active unmapped content remains.

## Validation Gates

Run after Phase 1:

```text
./gradlew help
./gradlew :products:data-cloud:contracts:build
./gradlew :products:data-cloud:delivery:sdk:build
./gradlew :products:data-cloud:delivery:launcher:test --tests "*OpenApi*"
./gradlew :platform:contracts:check
```

Run after Phase 2:

```text
./gradlew help
./gradlew :products:data-cloud:planes:action:operator-contracts:test
./gradlew :products:data-cloud:planes:action:engine:test
./gradlew :products:data-cloud:planes:action:registry:test
./gradlew :products:data-cloud:planes:action:agent-runtime:test
./gradlew :products:data-cloud:planes:action:orchestrator:test
./gradlew :products:data-cloud:planes:action:server:test
./gradlew :products:data-cloud:planes:action:kernel-bridge:test
./gradlew :integration-tests:cross-service-workflow:test
```

Run after Phase 3:

```text
pnpm --dir products/data-cloud/planes/action/ui install --frozen-lockfile
pnpm --dir products/data-cloud/planes/action/ui typecheck
pnpm --dir products/data-cloud/planes/action/ui test -- --run
pnpm --dir products/data-cloud/planes/action/ui build
npm --prefix products/data-cloud/planes/action/gateway install
npm --prefix products/data-cloud/planes/action/gateway run build
```

Run after Phase 5 and Phase 6:

```text
./gradlew help
./gradlew :products:data-cloud:contracts:build
./gradlew :products:data-cloud:planes:action:server:verifyOpenApiSync
./gradlew :products:data-cloud:planes:action:server:test
./gradlew :products:data-cloud:delivery:launcher:test
./gradlew :products:data-cloud:delivery:api-contract-tests:test
./gradlew :products:data-cloud:delivery:sdk:build
./gradlew :products:data-cloud:integration-tests:test
```

If a listed validation task does not exist after the move, fix the plan or the build path immediately. Do not leave a dead task path in CI.

## Exit Criteria

The first-pass path migration is complete only when all of these are true:

- No active Gradle includes remain under `:products:aep:*`.
- No active runtime, CI, workspace, script, or source path references remain under `products/aep` except explicitly marked historical docs.
- `products/data-cloud/contracts/openapi/aep.yaml` is the product-level AEP public contract source.
- `products/data-cloud/contracts/openapi/data-cloud.yaml` is the Data Cloud SDK and router-drift contract source.
- AEP source, tests, gateway, UI, catalog, deployment assets, scripts, generated docs, and loose service source all live under `products/data-cloud/planes/action` or `products/data-cloud/docs/aep`.
- `products/aep` is empty or deleted.
- Data Cloud core modules still do not depend on AEP implementation modules.
- CI no longer references non-existent AEP modules such as `launcher`, `platform`, `aep-runtime-core`, or Gradle `gateway`.
