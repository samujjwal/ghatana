# YAPPC Product-Family Feature Contract

Last updated: 2026-05-26

## Purpose

The product-family control plane lets YAPPC operators inspect release readiness, reusable assets, promotion state, documentation truth warnings, reuse recommendations, and Kernel lifecycle visibility across related products.

## Canonical Routes

The canonical route source is `products/yappc/docs/api/route-manifest.yaml`; OpenAPI and generated clients must remain in parity.

| Operation | Method | Path | Backend handler | Frontend client |
| --- | --- | --- | --- | --- |
| Release readiness | `GET` | `/api/v1/yappc/product-family/releases/{productKey}` | `ProductFamilyControlPlaneController.getReleaseReadiness` | `getReleaseReadiness` |
| Reusable assets | `GET` | `/api/v1/yappc/product-family/assets` | `ProductFamilyControlPlaneController.listAssets` | `listProductAssets` |
| Asset promotion | `POST` | `/api/v1/yappc/product-family/assets/{assetId}/promotions` | `ProductFamilyControlPlaneController.promoteAsset` | `promoteProductAsset` |
| Documentation truth | `GET` | `/api/v1/yappc/product-family/doc-truth` | `ProductFamilyControlPlaneController.listDocTruthWarnings` | `listDocTruthWarnings` |
| Reuse recommendations | `GET` | `/api/v1/yappc/product-family/reuse-recommendations/{targetProduct}` | `ProductFamilyControlPlaneController.listGuidedReuse` | `listGuidedReuse` |
| Kernel timeline | `GET` | `/api/v1/yappc/product-family/kernel-timeline/{productUnitId}` | `ProductFamilyControlPlaneController.getKernelTimeline` | `getKernelTimeline` |

## Data Sources

| Data | Canonical source | Fallback ingestion |
| --- | --- | --- |
| Release readiness | Data Cloud `product_release_readiness` | `.kernel/evidence/product-release-readiness.<product>.json` |
| Reusable assets | Data Cloud `product_family_assets` | `.kernel/evidence/<product>/reusable-assets-registration.json` |
| Promotion history | Data Cloud `product_family_asset_history` | None; write path is backend only |
| Documentation truth | Data Cloud `yappc_truth_checks` | Controller degraded/empty state |
| Reuse recommendations | Data Cloud `product_family_reuse_recommendations` | Controller degraded/empty state |
| Kernel timeline | Kernel/Data Cloud lifecycle truth exposed by controller read model | Controller degraded/empty state |

## Permissions

The frontend route is guarded by backend-owned capability `product-family:control-plane`. The navigation item is hidden unless `useCapabilityGate('product-family:control-plane')` grants access. Backend routes are registered in the route manifest and authorization registry; asset promotion is a mutating restricted operation and must remain server-authorized.

Allowed UI roles for the route gate are `OWNER`, `ADMIN`, `LEAD`, and `DEVELOPER`. Viewer-only users must not see the product-family page or execute promotion actions.

Asset promotion is also fail-closed in `ProductFamilyControlPlaneController.promoteAsset` for direct API calls. Authorized promotion requests persist the updated `product_family_assets` record and a `product_family_asset_history` audit record with actor, correlation ID, previous state, target state, rollback target state, evidence refs, and reversibility metadata.

## UI Behavior

`ProductFamilyControlPlanePage` renders five tabs:

| Tab | Behavior |
| --- | --- |
| PHR | Shows release readiness, evidence counts, commit alignment, gates, blockers, and foundation readiness for `phr`. |
| Digital Marketing | Shows the same release readiness contract plus connector, approval, and AI action gates for `digital-marketing`. |
| Assets | Lists reusable assets with search/product/type/maturity/reuse/domain/compatibility filters and promotion actions. |
| Truth | Shows doc-truth warnings plus guided reuse recommendations for Tutorputor and Flashit. |
| Kernel | Shows product-unit Kernel timeline and rollback visibility. |

Candidate-blocked products display an explicit candidate status rather than a false ready state.

## Validation Evidence

Focused validation commands:

```powershell
.\gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.ProductFamilyControlPlaneControllerTest"
.\gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.RouteManifestParityTest" --tests "com.ghatana.yappc.api.RouteAuthorizationRegistryParityTest" --tests "com.ghatana.yappc.api.RouteAuthorizationRegistryTest"
pnpm -C products/yappc/frontend/web exec vitest run src/routes/app/__tests__/product-family-gate.test.tsx src/pages/product-family/__tests__/ProductFamilyControlPlanePage.accessibility.test.tsx
pnpm -C products/yappc/frontend/web type-check
```
