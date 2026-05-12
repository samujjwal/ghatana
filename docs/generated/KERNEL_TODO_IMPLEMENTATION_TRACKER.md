# Kernel TODO Implementation Tracker

Generated/updated by Codex on 2026-05-12.

## Completed in this implementation slice

| TODO | Status | Evidence |
| --- | --- | --- |
| 1 | Partially completed | `scripts/generate-product-registry-artifacts.mjs` now validates registry obligations, generates `config/product-shape.json`, updates root `pnpm-workspace.yaml`, emits generated CI/script artifacts, and merges product scripts from generated output. |
| 2 | Partially completed | `config/canonical-product-registry.json` now declares `kind` for every entry; CI checks filter active manifest-backed business products by `kind` and status. |
| 3 | Partially completed | Added `pnpm check:kernel-boundaries` through `scripts/check-kernel-boundaries.mjs` and wired the existing ESLint platform-to-product rule. The gate enforces platform isolation and strict active manifest-backed business products; legacy YAPPC/software-org/virtual-org/Data Cloud Gradle coupling remains a broader migration. |
| 5 | Completed | Removed stale undefined `contracts` loop from `scripts/check-observability-conformance.mjs`; validation now uses `config/observability/product-observability-flows.json` as the evidence model. |
| 6 | Completed | Root `pnpm-workspace.yaml` is generated from registry `pnpmPackages`, including PHR, DMOS, and FlashIt packages. |
| 7 | Completed | Bad split-path filters were removed from generated root scripts; product scripts now route through `pnpm product`. |
| 8 | Partially completed | Active product manifests now use a top-level `schemaVersion` envelope and the loader parses JSON/YAML through one normalized path. Legacy `pack` unwrap is limited to older manifests without `schemaVersion`. |
| 9 | Completed | Finance and DMOS product-specific policy actions are namespaced; registry policy action namespaces are explicit. |
| 10 | Completed | DMOS and FlashIt resources are product-scoped; FlashIt wildcard resource was replaced with an explicit hierarchical resource. |
| 11 | Completed | Manifest-conformant registry entries now require explicit `manifestPath`, `manifestFormat`, and `buildFile`; non-manifest entries carry explicit manifest exemptions. |
| 13 | Partially completed | Product route entitlement contracts now share one public type model in `@ghatana/product-shell`; generated/contract entry points re-export the same types to reduce drift. Full backend DTO generation from product route contracts remains open. |
| 14 | Partially completed | Added shared `RoleHierarchy` / `RouteAccessEvaluator` helpers in `@ghatana/product-shell` and migrated PHR, DMOS, and FlashIt route-access wrappers to consume the shared evaluator. |
| 16 | Partially completed | Split `ProductShell` into `useProductShellState`, `ProductShellLayout`, and the convenience `ProductShell` wrapper while preserving the existing public composition path. |
| 17 | Partially completed | Added `useProductShellConfig(configParts)` and migrated the three active product shell wrappers off direct dependency-array config factories. |
| 20 | Completed | Added `scripts/run-product-task.mjs` and root `pnpm product <productId> <task> [surface]` orchestration. |
| 21 | Partially completed | Product scaffolder now emits canonical manifest envelopes and works with generated `pnpm-workspace.yaml`; `pnpm check:product-scaffolder` passes. Full registry/CI/docs/conformance bootstrap generation remains open. |
| 25 | Partially completed | Added shared design-system async UI primitives: `AsyncStateBoundary`, `LoadingState`, `ErrorState`, `AccessDeniedState`, `FeatureUnavailableState`, and `SuccessState`, with focused behavior tests. Broad product page adoption remains open. |
| 26 | Partially completed | Added shared dashboard composition primitives in `@ghatana/design-system`: `DashboardGrid`, `KpiStrip`, `DataCard`, `FilterBar`, `ChartPanel`, and `ActivityLog`, with focused component tests. Broad product dashboard/table/chart adoption remains open. |
| 27 | Partially completed | Hardened `useProductEntitlements` with runtime contract validation, entitlement cache refresh tests, and fail-closed route discovery until backend entitlements are valid. Product-wide adoption of backend-hydrated shell routes remains open. |
| 28 | Partially completed | Finance remains backend-only in generated `product-shape.json`; registry still advertises non-pnpm Gradle surfaces for portal/operator/sdk and routes them through the product runner. |
| 30 | Completed | FlashIt gateway imports route contracts directly from `@flashit/shared`; deprecated `products/flashit/backend/gateway/src/routes/route-manifest.contract.ts` was deleted. |
| 31 | Completed | Bridge compliance contracts are declared in `config/canonical-product-registry.json` under `conformance.bridgeAdapters`; `scripts/check-bridge-compliance.mjs` loads those contracts instead of hardcoding a DMOS-only expected test map. |
| 32 | Completed | Secret/default credential scanner policy now lives in `config/security-secret-scan.json`; `scripts/check-secret-default-credentials.mjs` loads include roots, allowlists, exclusions, names, extensions, and patterns from config. |
| 33 | Completed | Replaced broad `frontend/` production scan exclusion with precise generated/build frontend exclusions in `config/production-critical-scopes.config.json`. |
| 34 | Partially completed | Added `config/observability/product-observability-flows.schema.json` and script-level schema enforcement for flow shape, facets, evidence, products, and flow kinds. Java/TypeScript type generation remains outstanding. |
| 36 | Partially completed | Product registry generation/check logic is more testable and less heuristic; full conformance-core extraction remains outstanding. |
| 41 | Completed | Removed build-file inference fallback from manifest contract checks; registry must declare build ownership explicitly. |

## Explicitly deferred broader migrations

TODOs 4, 12, 15, 18-19, 22-24, 29, 35, 37-40, and 42-44 require broad product and platform migrations beyond this slice. TODOs 13, 14, 16, 17, 25, 26, 27, and 34 now have Kernel primitives/schema enforcement and initial tests, but still need broader render-count/component coverage, generated backend DTO/type adoption, and removal of remaining thin product wrappers or page-local state/dashboard components.

## Validation commands

- `node scripts/generate-product-registry-artifacts.mjs`
- `pnpm check:product-workspace-registration`
- `pnpm check:product-ci-matrices`
- `pnpm check:observability-conformance`
- `pnpm check:bridge-compliance`
- `pnpm check:product-manifest-contracts`
- `pnpm --dir platform/typescript/product-shell exec tsc --noEmit`
- `pnpm --dir platform/typescript/product-shell exec vitest run src/__tests__/product-shell.test.tsx`
- `pnpm --dir products/digital-marketing/ui exec tsc --noEmit`
- `pnpm --dir platform/typescript/design-system exec tsc --noEmit`
- `pnpm --dir platform/typescript/design-system exec vitest run src/molecules/__tests__/AsyncState.test.tsx src/organisms/__tests__/DashboardPrimitives.test.tsx`
- `pnpm product digital-marketing build --dry-run`
- `pnpm product data-cloud build --dry-run`
- `pnpm product audio-video build --dry-run`
- `pnpm check:kernel-boundaries`
- `pnpm check:secret-default-credentials`
- `pnpm check:product-scaffolder`
- `rg -n "route-manifest\\.contract" products/flashit`

## Known unrelated blockers observed

- `pnpm typecheck:workspace` currently fails in `platform/typescript/accessibility` before reaching this slice.
- `git diff --check` currently reports trailing whitespace in pre-existing dirty data-cloud Java changes outside this slice.
- `pnpm check:production-stubs` now scans frontend paths and fails on the existing production placeholder backlog: 756 critical findings, 1442 warnings.
- `pnpm --dir products/flashit/backend/gateway exec tsc --noEmit` currently fails on existing FlashIt gateway type/dependency issues unrelated to the route-manifest re-export removal.
- `pnpm --dir products/flashit/client/web exec tsc --noEmit` currently fails on existing FlashIt client type/dependency issues; the product-shell/route lifecycle compatibility errors are cleared.
