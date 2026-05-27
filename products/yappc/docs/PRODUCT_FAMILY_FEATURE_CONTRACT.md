# YAPPC Product-Family Feature Contract

Last updated: 2026-05-26

## Purpose

The product-family control plane lets YAPPC operators inspect release readiness, reusable assets, promotion state, documentation truth warnings, reuse recommendations, and Kernel lifecycle visibility across related products.

## Ownership Boundary

| Owner | Owns | Boundary rule |
| --- | --- | --- |
| YAPPC | Product-family read model, route manifest entries, backend authorization, promotion audit records, and product shell UX. | YAPPC may aggregate and promote reusable YAPPC assets, but it must not overwrite source product code or Kernel lifecycle truth. |
| Data Cloud | Tenant-scoped records for `product_release_readiness`, `product_family_assets`, `product_family_asset_history`, `yappc_truth_checks`, and `product_family_reuse_recommendations`. | Missing or malformed records must render explicit empty/degraded states instead of fabricated readiness. |
| Kernel | Product-unit lifecycle timeline, rollback visibility, and execution truth used by the Kernel tab. | YAPPC reads Kernel visibility through controller read models; Kernel remains the execution authority. |
| Frontend | `ProductFamilyControlPlanePage`, capability-gated navigation, filters, tabs, promotion controls, and accessible empty/loading/error states. | The frontend can hide or disable controls, but backend `ProductFamilyControlPlaneController` remains the authorization boundary. |

## Canonical Routes

The canonical route source is `products/yappc/docs/api/route-manifest.yaml`; OpenAPI and generated clients must remain in parity.

| Operation | Method | Path | Backend handler | Frontend client |
| --- | --- | --- | --- | --- |
| Release readiness | `GET` | `/api/v1/yappc/product-family/releases/{productKey}` | `ProductFamilyControlPlaneController.getReleaseReadiness` | `ProductFamilyService.getProductFamilyReleaseReadiness` |
| Reusable assets | `GET` | `/api/v1/yappc/product-family/assets` | `ProductFamilyControlPlaneController.listAssets` | `ProductFamilyService.listProductFamilyAssets` |
| Asset promotion | `POST` | `/api/v1/yappc/product-family/assets/{assetId}/promotions` | `ProductFamilyControlPlaneController.promoteAsset` | `ProductFamilyService.promoteProductFamilyAsset` |
| Documentation truth | `GET` | `/api/v1/yappc/product-family/doc-truth` | `ProductFamilyControlPlaneController.listDocTruthWarnings` | `ProductFamilyService.listProductFamilyDocTruthWarnings` |
| Reuse recommendations | `GET` | `/api/v1/yappc/product-family/reuse-recommendations/{targetProduct}` | `ProductFamilyControlPlaneController.listGuidedReuse` | `ProductFamilyService.listProductFamilyReuseRecommendations` |
| Kernel timeline | `GET` | `/api/v1/yappc/product-family/kernel-timeline/{productUnitId}` | `ProductFamilyControlPlaneController.getKernelTimeline` | `ProductFamilyService.getProductFamilyKernelTimeline` |

## Data Sources

| Data | Canonical source | Fallback ingestion |
| --- | --- | --- |
| Release readiness | Data Cloud `product_release_readiness` | `.kernel/evidence/product-release-readiness.<product>.json` |
| Reusable assets | Data Cloud `product_family_assets` | `.kernel/evidence/<product>/reusable-assets-registration.json` |
| Promotion history | Data Cloud `product_family_asset_history` | None; write path is backend only |
| Documentation truth | Data Cloud `yappc_truth_checks` | Controller degraded/empty state |
| Reuse recommendations | Data Cloud `product_family_reuse_recommendations` | Controller degraded/empty state |
| Kernel timeline | Kernel/Data Cloud lifecycle truth exposed by controller read model | Controller degraded/empty state |

## Promotion Write Path

Asset promotion is the only mutating product-family operation today. `ProductFamilyControlPlaneController.promoteAsset` checks the authenticated principal, rejects callers without project-write product-family roles, updates the canonical `product_family_assets` record, and appends a reversible `product_family_asset_history` record with previous state, target state, rollback target state, actor, correlation ID, request payload, and evidence refs.

Promotions must be treated as product-family metadata changes. They do not directly apply generated code, change product release gates, or mutate Kernel product-unit lifecycle truth.

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

## Failure Semantics

| Failure | Expected behavior |
| --- | --- |
| Data Cloud read failure | Return explicit empty/degraded payload details from the controller; UI shows the shared error or empty state with retry/correlation context. |
| Missing readiness evidence | Show missing release evidence rather than marking the product ready. |
| Unauthorized route access | Hide the route in navigation and render the product-family unavailable state if reached directly. |
| Unauthorized promotion API call | Return a rejected response from `ProductFamilyControlPlaneController.promoteAsset` and do not update `product_family_assets` or `product_family_asset_history`. |
| Kernel timeline unavailable | Keep product-family release/assets visible while the Kernel tab shows the unavailable timeline state. |

## Validation Evidence

Focused validation commands:

```powershell
.\gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.ProductFamilyControlPlaneControllerTest"
.\gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.RouteManifestParityTest" --tests "com.ghatana.yappc.api.RouteAuthorizationRegistryParityTest" --tests "com.ghatana.yappc.api.RouteAuthorizationRegistryTest"
pnpm -C products/yappc/frontend/web exec vitest run src/routes/app/__tests__/product-family-gate.test.tsx src/pages/product-family/__tests__/ProductFamilyControlPlanePage.accessibility.test.tsx
pnpm -C products/yappc/frontend/web type-check
```

## Change Rules

1. Update `docs/api/route-manifest.yaml`, `docs/api/openapi.yaml`, generated clients, backend handlers, and this contract together for every product-family route change.
2. Keep mutating operations backend-authorized even when the frontend capability gate hides the control.
3. Persist promotion history in `product_family_asset_history` whenever `product_family_assets` changes through the promotion API.
4. Use explicit empty/degraded states for missing Data Cloud or Kernel truth; do not synthesize readiness.
5. Add or update `ProductFamilyControlPlaneControllerTest`, `product-family-gate.test.tsx`, and `ProductFamilyControlPlanePage.accessibility.test.tsx` for behavior changes.
