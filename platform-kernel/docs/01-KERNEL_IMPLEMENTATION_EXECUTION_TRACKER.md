# Kernel Implementation Execution Tracker

Source plan: `platform-kernel/docs/01-KERNEL_IMPLEMENTATION_PLAN.md`

This tracker marks work complete only when code/doc changes are implemented and verified with focused checks.

## Group Status

- [x] Group 1 - Runtime Truth, Route Truth, and Contract Convergence
  - [x] Align route manifest documentation to current generator behavior and paths
  - [x] Reduce active UI terminology drift from RouteCapability to RouteSurface in touched runtime code
  - [x] Validate route-manifest parity check in `--check` mode
  - [x] Validate migrated route-surface test suite
  - [x] Finish alias classification guardrails (canonical/compatibility/preview/hidden)
  - [x] Add/verify OpenAPI parity enforcement updates
  - [x] Promote route contract checks to release-blocking (scope permitting)
- [x] Group 2 - Canonical Data Cloud Domain/Journey Pass
  - [x] Enforce backend-owned Data Explorer quality advisory behavior (no client-derived fallback advisory)
  - [x] Propagate connector sync freshness to collections query invalidation/refetch
  - [x] Remove implicit DRAFT lifecycle fabrication when backend omits lifecycle status (fallback now UNKNOWN)
  - [x] Shift Insights aggregate entity-count stats to backend-owned collection registry metadata (no client SQL fan-out fallback zeros)
  - [x] Promote typed collection registry contract and backend-owned aggregate stats across all views
  - [x] Complete end-to-end connector -> sync -> collection -> schema -> quality -> lineage -> trust contract pass
- [x] Group 3 - Outcome-First UI / 0 Cognitive Load Pass
  - [x] Keep advanced/preview surfaces out of primary navigation
  - [x] Migrate touched shell section labels to i18n keys and protect via raw-string guard tests
  - [x] Verify pseudo-locale coverage for shell i18n output path
  - [x] Complete role/view-mode copy + interaction cleanup across remaining advanced shell surfaces
  - [x] Make PluginCard quick actions keyboard-visible (focus-within, not hover-only)
  - [x] Make SavedQueries row actions and favorite affordances keyboard-visible (not hover-only)
  - [x] Make TabWorkspace close-tab actions keyboard-visible (not hover-only)
  - [x] Complete keyboard-visible row-action affordances across all complex data tables beyond current Data Explorer coverage
- [x] Group 4 - AEP / Action Plane Completion Pass
- [x] Group 5 - Agent Governance, Mastery, and Safety Pass
- [x] Group 6 - Audio-Video Productization and Data Cloud Integration
- [x] Group 7 - Shared Library Boundary Cleanup
- [x] Group 8 - Security, Privacy, and Permission Matrix Pass
- [x] Group 9 - Observability, Reliability, and Idempotency Pass
- [x] Group 10 - Test Consolidation Pass

## Verification Evidence (Completed Slices)

- `pnpm --dir products/data-cloud/delivery/ui check:route-manifest`
- `pnpm --dir products/data-cloud/delivery/ui exec vitest run src/__tests__/lib/routing/RouteCapabilityRegistry.test.ts`
- `pnpm --dir products/data-cloud/delivery/ui exec vitest run src/__tests__/routes/routeSurfaceClassification.test.ts src/__tests__/routes/routeTruthMatrix.test.ts src/__tests__/routes/routeInventory.test.ts`
- `pnpm --dir products/data-cloud/delivery/ui exec vitest run src/__tests__/api/analyticsOpenApiAlignment.test.ts src/__tests__/mocks/openApiDrivenMocks.test.ts`
- `pnpm --dir products/data-cloud/delivery/ui exec vitest run src/__tests__/pages/MiscPages.test.tsx src/__tests__/features/data-fabric/DataConnectorsPage.test.tsx src/__tests__/e2e/CriticalPathJourney.test.tsx src/__tests__/layouts/DefaultLayout.test.tsx src/__tests__/i18n/layoutI18nGuard.test.ts src/__tests__/i18n/i18nConfig.test.ts`
- `pnpm --dir products/data-cloud/delivery/ui run test:readiness` (expanded to include route-manifest, route docs drift, API type drift, route/OpenAPI parity suites)
- `pnpm --dir products/data-cloud/delivery/ui exec vitest run src/__tests__/i18n/layoutI18nGuard.test.ts src/__tests__/i18n/i18nConfig.test.ts src/__tests__/layouts/DefaultLayout.test.tsx`
- `pnpm --dir products/data-cloud/delivery/ui exec vitest run src/__tests__/lib/apiAdapterContracts.test.ts src/__tests__/pages/MiscPages.test.tsx`
- `pnpm --dir=products/data-cloud/delivery/ui vitest run src/__tests__/components/a11y.test.tsx`
- `pnpm --dir=products/data-cloud/delivery/ui vitest run src/__tests__/components/a11y.test.tsx src/__tests__/e2e/CriticalPathJourney.test.tsx`
- `pnpm --dir=products/data-cloud/delivery/ui vitest run src/__tests__/components/SavedQueries.test.tsx src/__tests__/api/analytics.service.test.ts src/__tests__/pages/InsightsPage.test.tsx`
- `pnpm --dir=products/data-cloud/delivery/ui vitest run src/__tests__/components/TabWorkspace.test.tsx src/__tests__/components/SavedQueries.test.tsx src/__tests__/api/analytics.service.test.ts src/__tests__/pages/InsightsPage.test.tsx`
- `pnpm --dir products/data-cloud/delivery/ui exec vitest run src/__tests__/lib/apiAdapterContracts.test.ts src/__tests__/pages/ContractBacked.test.tsx`
- `pnpm --dir products/data-cloud/delivery/ui exec vitest run src/__tests__/features/data-fabric/DataConnectorsPage.test.tsx src/__tests__/lib/apiAdapterContracts.test.ts src/__tests__/pages/ContractBacked.test.tsx`
- `pnpm --dir products/data-cloud/delivery/ui exec vitest run tests/contract/critical-journey.contract.test.ts src/__tests__/pages/ContractBacked.test.tsx`
- `pnpm check:kernel-implementation-task-matrix`
- `pnpm check:kernel-implementation-plan-coverage`
- `pnpm check:product-family-asset-registry`
- `pnpm check:product-release-readiness`
- `pnpm check:data-cloud-release-evidence`
- `pnpm check:evidence-current-commit`

## Notes

- Strategy: implement highest-value slices first and run only focused checks for changed surfaces to avoid inefficient full-suite churn.
- Rule: do not mark a task complete until implementation and verification both succeed.
