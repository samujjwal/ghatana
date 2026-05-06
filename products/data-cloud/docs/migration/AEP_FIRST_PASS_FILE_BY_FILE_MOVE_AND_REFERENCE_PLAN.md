# Data Cloud + AEP First-Pass File-by-File Move and Reference Update Plan

## Purpose

This document turns the first-pass merge decision into an execution plan tied to the current repository layout.

Working rule for this series:

- Product root after migration: `products/data-cloud`
- AEP capability root after migration: `products/data-cloud/aep`
- Public product contracts after migration: `products/data-cloud/contracts`
- Internal AEP extension/runtime contracts after migration: `products/data-cloud/aep/operator-contracts`

This plan is intentionally concrete about paths because the current repo still has standalone AEP Gradle modules, standalone AEP CI/CD, a standalone AEP gateway package, and contract/codegen flows that still point at `products/aep`.

## Ground Truth Confirmed In Repo

Confirmed current anchors:

- Standalone Gradle includes for AEP live in `settings.gradle.kts`.
- Root architecture allowlists in `build.gradle.kts` still enumerate `:products:aep:*` modules.
- AEP OpenAPI canonical validation currently lives in `products/aep/contracts/build.gradle.kts` and treats `products/aep/contracts/openapi.yaml` as canonical.
- Data Cloud SDK generation currently reads `products/data-cloud/api/openapi.yaml`, not a product-level contracts module.
- AEP also owns non-Gradle surfaces not covered by the initial merge outline: `products/aep/gateway`, `products/aep/agent-catalog`, `products/aep/helm`, `products/aep/k8s`, `products/aep/test-scripts`, `products/aep/scripts`, `products/aep/docs-generated`, and root docs/assets.

## Execution Rules

- Do not rename Java packages in this pass. Keep `com.ghatana.aep.*` and `com.ghatana.datacloud.*`.
- Move tests with their owning module directories. Do not rewrite package names in this pass.
- Do not hand-edit generated or ephemeral outputs as part of the migration: `build/`, `dist/`, `node_modules/`, `.idea/`.
- Update path-bearing references atomically with the move that invalidates them.
- Only launcher, distribution, and integration-test surfaces may compose Data Cloud core with AEP implementation modules.

## File-by-File Move Plan

### 1. Create Product-Level Contracts Module

| Current path                              | Target path                                                                          | Action                     | Notes                                                                                                                   |
| ----------------------------------------- | ------------------------------------------------------------------------------------ | -------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `products/aep/contracts/openapi.yaml`     | `products/data-cloud/contracts/openapi/aep.yaml`                                     | Move                       | New product-level public AEP spec path.                                                                                 |
| `products/aep/contracts/build.gradle.kts` | `products/data-cloud/contracts/build.gradle.kts`                                     | Replace with merged module | Do not copy verbatim. Rebuild this module so it validates `data-cloud.yaml`, `aep.yaml`, and platform spec composition. |
| `products/aep/contracts/README.md`        | `products/data-cloud/contracts/README.md`                                            | Merge/adapt                | Rewrite as Data Cloud product contracts README.                                                                         |
| `products/aep/contracts/OWNER.md`         | `products/data-cloud/contracts/OWNER.md` or fold into `products/data-cloud/OWNER.md` | Merge/adapt                | Prefer ownership in product-level owner doc unless local contract ownership needs a separate file.                      |
| `products/data-cloud/api/openapi.yaml`    | `products/data-cloud/contracts/openapi/data-cloud.yaml`                              | Copy then repoint          | Existing SDK codegen depends on this path today. Update codegen after copy.                                             |
| `platform/contracts/openapi/aep.yaml`     | keep                                                                                 | Reference sync target      | Remains platform-level registry copy, but sync tasks must be repointed to the new product contract path.                |

Required code changes in the same commit:

- `products/data-cloud/sdk/build.gradle.kts`: change SDK input from `products/data-cloud/api/openapi.yaml` to `products/data-cloud/contracts/openapi/data-cloud.yaml` or to the new unified platform spec if introduced.
- `products/aep/contracts/build.gradle.kts` logic must be rehomed into the new contracts module before deleting the old one.
- Any drift/sync test that currently compares `products/aep/contracts/openapi.yaml` against runtime copies must compare `products/data-cloud/contracts/openapi/aep.yaml` instead.

### 2. Move AEP Gradle Modules Under Data Cloud

Move each module directory as a whole, including `build.gradle.kts`, `src/main`, `src/test`, and module-local resources.

| Current path                          | Target path                                  |
| ------------------------------------- | -------------------------------------------- |
| `products/aep/aep-operator-contracts` | `products/data-cloud/aep/operator-contracts` |
| `products/aep/aep-central-runtime`    | `products/data-cloud/aep/central-runtime`    |
| `products/aep/aep-engine`             | `products/data-cloud/aep/engine`             |
| `products/aep/aep-registry`           | `products/data-cloud/aep/registry`           |
| `products/aep/aep-analytics`          | `products/data-cloud/aep/analytics`          |
| `products/aep/aep-security`           | `products/data-cloud/aep/security`           |
| `products/aep/aep-event-cloud`        | `products/data-cloud/aep/event-cloud-bridge` |
| `products/aep/aep-agent-runtime`      | `products/data-cloud/aep/agent-runtime`      |
| `products/aep/aep-api`                | `products/data-cloud/aep/api`                |
| `products/aep/aep-scaling`            | `products/data-cloud/aep/scaling`            |
| `products/aep/aep-observability`      | `products/data-cloud/aep/observability`      |
| `products/aep/orchestrator`           | `products/data-cloud/aep/orchestrator`       |
| `products/aep/server`                 | `products/data-cloud/aep/server`             |
| `products/aep/aep-identity`           | `products/data-cloud/aep/identity`           |
| `products/aep/aep-compliance`         | `products/data-cloud/aep/compliance`         |
| `products/aep/kernel-bridge`          | `products/data-cloud/aep/kernel-bridge`      |

### 3. Move AEP Runtime and Delivery Surfaces Missing From The Original Module List

These are present in the repo today and need explicit handling in the same migration series.

| Current path                  | Target path                                                                  | Action                | Why it matters                                                               |
| ----------------------------- | ---------------------------------------------------------------------------- | --------------------- | ---------------------------------------------------------------------------- |
| `products/aep/gateway`        | `products/data-cloud/aep/gateway`                                            | Move                  | `pnpm-workspace.yaml`, CI, and OpenAPI drift checks depend on it today.      |
| `products/aep/ui`             | `products/data-cloud/aep/ui`                                                 | Move in first UI pass | Keep shell intact for first pass.                                            |
| `products/aep/agent-catalog`  | `products/data-cloud/aep/agent-catalog`                                      | Move                  | AEP engine tests and platform agent-catalog roots reference this exact path. |
| `products/aep/helm`           | `products/data-cloud/aep/helm`                                               | Move                  | GitHub workflow reads `products/aep/helm/aep/values.yaml`.                   |
| `products/aep/k8s`            | `products/data-cloud/aep/k8s`                                                | Move                  | Gitea CD applies manifests from here.                                        |
| `products/aep/scripts`        | `products/data-cloud/aep/scripts`                                            | Move                  | Keep operational scripts colocated with AEP capability.                      |
| `products/aep/test-scripts`   | `products/data-cloud/aep/test-scripts`                                       | Move                  | Benchmark docs reference exact `k6` script path.                             |
| `products/aep/docs-generated` | `products/data-cloud/docs/aep/generated`                                     | Move                  | Generated docs should remain reachable but not block backend module moves.   |
| `products/aep/Dockerfile`     | `products/data-cloud/aep/Dockerfile` or fold into product distribution later | Move for first pass   | CI references this exact file now.                                           |

### 4. Move AEP Docs And Product Metadata

| Current path                                 | Target path                                                  |
| -------------------------------------------- | ------------------------------------------------------------ |
| `products/aep/README.md`                     | `products/data-cloud/docs/aep/README.md`                     |
| `products/aep/OWNER.md`                      | `products/data-cloud/docs/aep/OWNER.md`                      |
| `products/aep/AEP_COMPREHENSIVE_OVERVIEW.md` | `products/data-cloud/docs/aep/AEP_COMPREHENSIVE_OVERVIEW.md` |
| `products/aep/docs/**`                       | `products/data-cloud/docs/aep/**`                            |

Then update these existing Data Cloud product docs in place rather than moving them:

- `products/data-cloud/README.md`
- `products/data-cloud/OWNER.md`
- `products/data-cloud/data-cloud-canonical-architecture-spec.md`
- `products/data-cloud/DEVELOPER_MANUAL.md`
- `products/data-cloud/TEST_MANUAL.md`
- `products/data-cloud/USER_MANUAL.md`
- `products/data-cloud/RUNBOOK.md`
- `products/data-cloud/REST_API_DOCUMENTATION.md`

### 5. Stray Or Unmapped AEP Root Content That Needs An Explicit Decision

These surfaces exist today but are not clearly modeled in the first-pass merge outline.

| Current path                                         | Proposed handling                                                                                                                      | Note                                                                    |
| ---------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| `products/aep/services/AnomalyDetectionService.java` | Move to `products/data-cloud/aep/services/AnomalyDetectionService.java` or absorb into an owning module before deleting `products/aep` | This is a loose root source file, not a declared Gradle module.         |
| `products/aep/agent-catalog/docs`                    | Move with `products/data-cloud/aep/agent-catalog/docs`                                                                                 | Keep catalog documentation with canonical catalog root.                 |
| `products/aep/agent-catalog/capabilities`            | Move with catalog                                                                                                                      | AEP capability registry content must stay stable during path migration. |
| `products/aep/agent-catalog/operators`               | Move with catalog                                                                                                                      | Current engine test hardcodes this path.                                |

Do not delete `products/aep` until every item in this section has an assigned target path.

## Reference Update Plan

### A. Gradle Settings And Dependency Paths

Update these first because they define whether the repo still compiles after the move.

| File or surface                                             | Required update                                                                                                                                    |
| ----------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `settings.gradle.kts`                                       | Remove `include(":products:aep:...")`; add `include(":products:data-cloud:contracts")` and `include(":products:data-cloud:aep:...")` replacements. |
| `build.gradle.kts`                                          | Replace AEP entries in `sharedProductApiAllowlist` with new `:products:data-cloud:aep:*` paths.                                                    |
| `products/aep/**/build.gradle.kts`                          | Rewrite `project(":products:aep:...")` dependencies to new paths after folder moves.                                                               |
| `products/data-cloud/**/build.gradle.kts`                   | Add new dependencies only where composition is allowed; do not introduce core-to-AEP reverse dependencies.                                         |
| `integration-tests/cross-service-workflow/build.gradle.kts` | Replace `testImplementation(project(":products:aep:orchestrator"))`.                                                                               |
| Consumer build files across products                        | Replace any implementation/testImplementation references to `:products:aep:*`.                                                                     |

Confirmed non-module consumers already present in repo:

- `integration-tests/cross-service-workflow/build.gradle.kts`
- `products/yappc/settings.gradle.kts`
- `docs/architecture/DEPENDENCY_DIRECTION_REMEDIATION_PLAN_2026-04-23.md`

### B. Contract, OpenAPI, And SDK References

| File or surface                                         | Required update                                                                                          |
| ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/sdk/build.gradle.kts`              | Repoint codegen input away from `products/data-cloud/api/openapi.yaml` if contracts become canonical.    |
| `products/aep/contracts/build.gradle.kts` successor     | Repoint canonical spec path and sync checks to `products/data-cloud/contracts/openapi/aep.yaml`.         |
| `products/aep/server/src/main/resources/openapi.yaml`   | Keep runtime copy but update sync source path.                                                           |
| `platform/contracts/openapi/aep.yaml`                   | Update sync task references, not necessarily file location.                                              |
| `products/aep/gateway/src/openapi/aep.yaml` if retained | Update diff/sync checks against new product contract path.                                               |
| Data Cloud product-level SDK generation config          | Validate that AEP public SDK generation now reads from `products/data-cloud/contracts/openapi/aep.yaml`. |

### C. Workspace And Package Manager References

| File or surface                                      | Required update                                                                                 |
| ---------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `pnpm-workspace.yaml`                                | Change `products/aep/gateway` to `products/data-cloud/aep/gateway`; add new UI path when moved. |
| `pnpm-lock.yaml`                                     | Regenerate after workspace path changes. Do not hand-edit.                                      |
| Any package.json scripts under moved Node workspaces | Update relative path assumptions after the move.                                                |

### D. CI/CD And Deployment References

Confirmed workflow files with direct AEP path references:

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

Required updates inside those files:

- Replace path filters from `products/aep/**` to `products/data-cloud/aep/**` plus `products/data-cloud/contracts/**` where contract changes should trigger jobs.
- Replace Gradle task paths from `:products:aep:*` to `:products:data-cloud:aep:*` and `:products:data-cloud:contracts:*`.
- Replace deployment file paths such as `products/aep/Dockerfile`, `products/aep/k8s`, and `products/aep/helm/aep/values.yaml`.
- Replace UI and gateway package commands that run against `products/aep/ui` and `products/aep/gateway`.

### E. Ownership, Governance, And Repo Metadata

| File or surface                            | Required update                                                                    |
| ------------------------------------------ | ---------------------------------------------------------------------------------- |
| `.github/CODEOWNERS`                       | Change `/products/aep/` ownership scope to the new Data Cloud AEP subtree.         |
| `.gitea/CODEOWNERS`                        | Change `products/aep/` ownership scope to the new subtree.                         |
| `platform/agent-catalog/catalog-roots.txt` | Replace `products/aep/agent-catalog` with `products/data-cloud/aep/agent-catalog`. |
| `build.gradle.kts`                         | Keep shared API allowlist aligned with the new paths.                              |

### F. Source, Test, And Runtime Path Literals

These need explicit search-and-replace attention because they hardcode filesystem paths rather than Gradle project paths.

| File or surface                                                                                                | Required update                                   |
| -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| `products/aep/orchestrator/src/test/java/com/ghatana/aep/integration/registry/CatalogCanonicalValuesTest.java` | Replace `products/aep/agent-catalog/operators`.   |
| `products/aep/aep-engine/src/main/java/com/ghatana/aep/catalog/AepCentralCatalogService.java`                  | Replace catalog root filesystem path literals.    |
| `products/aep/docs/AEP_BENCHMARKS.md`                                                                          | Replace `products/aep/test-scripts/...` examples. |
| `products/aep/k8s/multi-region/README.md`                                                                      | Replace baseline kustomize paths.                 |

### G. Documentation References Outside Product Tree

Confirmed repo docs with direct `products/aep` references already exist in:

- `docs/ONBOARDING.md`
- `docs/CROSS_PRODUCT_INTEGRATION_POINTS.md`
- `docs/DATA_CLOUD_OWNERSHIP_CLARIFICATION.md`
- `docs/SHARED_SERVICES_ARCHITECTURE.md`
- `docs/GOVERNANCE.md`
- `docs/README.md`
- `docs/agent-system/README.md`
- `docs/agent-system/ARCHITECTURE.md`
- `docs/architecture/ARCHITECTURE_RULES.md`
- `docs/architecture/UNIFIED_PLATFORM_IMPLEMENTATION_PLAN.md`
- `docs/architecture/CANVAS_PLATFORM_ENHANCEMENT_AND_IMPLEMENTATION_PLAN.md`
- `docs/architecture/DESIGN_SYSTEM_GENERATOR_PLATFORM_ARCHITECTURE_HARDENED.md`
- `docs/adr/ADR-012-keep-aep-gateway.md`
- `docs/adr/ADR-020-agent-system-five-layer-architecture.md`
- `docs/runbooks/aep-degraded-mode-runbook.md`
- `docs/generated/CAPABILITY_MATRIX.md`
- `docs/kernel/BUILD.md`
- `docs/kernel/COVERAGE_MATRIX.md`
- `docs/kernel/PRODUCT_KERNEL_CAPABILITY_MAP.md`

Handling rule:

- Update active architecture, onboarding, runbook, and governance docs in the same series.
- Preserve historical audit artifacts under `code-audits/**` and `docs/audits/**` unless the migration explicitly archives and annotates them.

## Search Buckets To Clear Before Deleting `products/aep`

Run all of these after each migration commit that changes paths:

```text
:products:aep:
products/aep/
products/aep
products/aep/contracts/openapi.yaml
products/aep/server/src/main/resources/openapi.yaml
products/aep/gateway
products/aep/ui
products/aep/agent-catalog
products/aep/helm
products/aep/k8s
products/aep/test-scripts
```

Approved temporary exceptions:

- `products/data-cloud/docs/migration/**`
- `code-audits/**`
- `docs/audits/**`

## Suggested Commit Order

1. Add this migration doc, Data Cloud boundary docs, and stale-path check script.
2. Create `products/data-cloud/contracts` and repoint contract validation and SDK generation.
3. Move Gradle-backed AEP modules under `products/data-cloud/aep` and update `settings.gradle.kts` plus project dependencies.
4. Move gateway, UI, agent-catalog, deployment assets, scripts, and docs-generated surfaces.
5. Update CI/CD, CODEOWNERS, pnpm workspace paths, and product-isolated filters.
6. Update path literals in source, tests, and runtime sync checks.
7. Add merged boundary tests and stale-reference enforcement.
8. Delete residual `products/aep` only after the unmapped-surface checklist is empty.

## Validation Gate Per Phase

Use these commands as the narrow validation gates for the migration series:

```text
./gradlew help
./gradlew :products:data-cloud:contracts:build
./gradlew :products:data-cloud:aep:operator-contracts:test
./gradlew :products:data-cloud:aep:engine:test
./gradlew :products:data-cloud:aep:registry:test
./gradlew :products:data-cloud:aep:server:test
./gradlew :products:data-cloud:launcher:test
./gradlew :products:data-cloud:api:test
./gradlew :products:data-cloud:sdk:build
./gradlew :products:data-cloud:integration-tests:test
```

If the path migration touches Node workspaces in that commit, add:

```text
pnpm --dir products/data-cloud/aep/ui test
pnpm --dir products/data-cloud/aep/ui build
pnpm --dir products/data-cloud/aep/gateway build
```

## Exit Criteria For Path Migration

The path migration is complete when all of the following are true:

- No active Gradle includes remain under `:products:aep:*`.
- No active runtime, CI, or workspace path references remain under `products/aep` except explicitly archived historical docs.
- Public AEP OpenAPI is served from `products/data-cloud/contracts/openapi/aep.yaml` as the product-level contract source.
- AEP module source, tests, gateway, UI, catalog, deployment assets, and scripts all live under `products/data-cloud/aep`.
- Data Cloud core modules still do not depend on AEP implementation modules.
