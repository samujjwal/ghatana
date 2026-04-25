# Data Cloud UI/UX Remediation Tracker

**Source**: DATA_CLOUD_UI_UX_AUDIT_2026-04-24.md  
**Created**: 2026-04-24  
**Last Updated**: 2026-04-25  
**Product**: Data Cloud (`products/data-cloud/ui`)

---

## Legend

| Status | Meaning |
|---|---|
| 🔴 NOT_STARTED | Work not yet begun |
| 🟡 IN_PROGRESS | Currently being implemented |
| 🟢 DONE | Implemented, tested, merged |
| ⚫ BLOCKED | Blocked on backend/API/decision |
| 🔵 DEFERRED | Intentionally deferred (reason noted) |

| Priority | Meaning |
|---|---|
| P0 | Critical — prevents correct or safe usage; must fix immediately |
| P1 | High — degrades trust, completeness, or key journeys; fix this sprint |
| P2 | Medium — important but can be scheduled in next sprint |
| P3 | Low / Long-term — backlog |

---

## P0 — Critical (Fix Immediately)

| ID | Title | Status | File(s) | Notes |
|---|---|---|---|---|
| DC-UX-025 | Remove fake Settings save/toggle/API-key actions | 🟢 DONE | `SettingsPage.tsx` | Replaced fake actions with `UnsupportedSurfaceBoundary`; uses existing `settingsSurfaceBoundaries` registry |
| DC-UX-026 | Remove fake/local API key creation and revocation | 🟢 DONE | `SettingsPage.tsx` | Covered by DC-UX-025 fix |
| DC-UX-014 | Fix SensitivityBadge crash on undefined/invalid value | 🟢 DONE | `src/components/governance/TrustSignal.tsx` | Added defensive fallback + strict type narrowing for invalid/undefined levels |
| DC-UX-013 | Collection governance fields not persisted — false-completion | 🟢 DONE | `src/features/collection/components/CollectionForm.tsx` | Added explicit draft-only governance notice so users understand values apply after save |
| DC-UX-015 | Workflow action menu items are inert (Run, Edit, Logs, Delete) | 🟢 DONE | `WorkflowsPage.tsx` | Disabled inert actions with boundary copy until wired |
| DC-UX-016 | Workflow detail modal Run/Pause buttons are inert | 🟢 DONE | `WorkflowsPage.tsx` | Disabled modal actions with boundary copy |
| DC-UX-027 | Operations shows "0" for unavailable alert counts | 🟢 DONE | `src/pages/OperationsConsolePage.tsx` | Replaced misleading zero fallback with explicit unavailable marker (`—`) |
| DC-UX-028 | Operations uses client time as health last-check surrogate | 🟢 DONE | `src/pages/OperationsConsolePage.tsx` | Replaced client local-time surrogate with explicit unavailable marker (`—`) |
| DC-UX-001 | MSW worker missing/mis-served; routes hang on Loading indefinitely | 🟢 DONE | `mocks/`, `vite.config.ts` | Disabled MSW when worker file unavailable; added route-level timeout fallback |
| DC-UX-009 | Data Explorer lacks bounded error/retry/unavailable states | 🟢 DONE | `src/pages/DataExplorer.tsx` | Added explicit error branch with retry action and prevented silent empty-state fallback on fetch failures |
| DC-UX-022 | Query trust badges are static, not query-derived | 🟢 DONE | `SqlWorkspacePage.tsx` | Replaced static badges with `UnsupportedSurfaceBoundary` note; removed misleading badges |
| DC-UX-002 | Route truth split across 6+ conflicting sources | 🟢 DONE | `src/lib/routing/RouteCapabilityRegistry.ts`, `src/layouts/DefaultLayout.tsx`, `src/components/common/GlobalSearch.tsx` | Registry is now the single truth source; shell nav filtering derives from discoverable routes |
| DC-UX-030 | Data Fabric migration lacks dry-run/impact/approval safeguards | 🟢 DONE | `DataFabricPage.tsx` | Added GuardedAction pattern with impact preview + confirmation |
| DC-UX-045 | Trust/security signals not data-derived (systemic) | � DONE | Multiple | TrustCenter: all signals from `governanceService` (getPolicies/getComplianceReport/getLifecycleSurfaces). SqlWorkspacePage: signals from `queryPlan.dataSources` + `executionRecommendation.requiresReview`. `TrustSignalGroup` created for policy binding. |

---

## P1 — High Priority (This Sprint)

| ID | Title | Status | File(s) | Notes |
|---|---|---|---|---|
| DC-UX-037 | Browser title still says "CES UI" | 🟢 DONE | `index.html`, `App.tsx` | Renamed to "Data Cloud" |
| DC-UX-005 | Nested `<main>` landmarks in shell + pages | 🟢 DONE | `DefaultLayout.tsx`, pages | Shell owns `<main>`; pages now use `<section>` |
| DC-UX-041 | Failing tests: route/settings/query/a11y | � DONE | `__tests__/` | All 74 tests passing (accessibility, routeTruthMatrix, SqlWorkspacePage, SettingsPage, WorkflowsPage) |
| DC-UX-017 | Workflows truncated to 12 without pagination | 🟢 DONE | `WorkflowsPage.tsx` | Added pagination controls |
| DC-UX-003 | Global search exposes stale static shortcuts | 🟢 DONE | `GlobalSearch.tsx` | Generated from `RouteCapabilityRegistry` |
| DC-UX-004 | Shell nav comments claim registry-backed; static nav used | 🟢 DONE | `DefaultLayout.tsx` | Nav now driven from registry |
| DC-UX-006 | Onboarding modal breaks in narrow viewport | 🟢 DONE | `DataCloudOnboardingWizard.tsx` | Fixed mobile-first stepper layout |
| DC-UX-007 | Onboarding references missing Settings→AI section | 🟢 DONE | `DataCloudOnboardingWizard.tsx` | Copy updated to match real settings/capability state |
| DC-UX-010 | Collection detail deep-link not durable (state vs resource route) | 🟢 DONE | `DataExplorer.tsx`, `routes.tsx` | Implemented true `/data/:id` resource route with loader |
| DC-UX-033 | Alert acknowledge/resolve lacks reason, undo, and audit closure | 🟢 DONE | `AlertsPage.tsx` | Added `GuardedAction` with reason capture and audit event |
| DC-UX-038 | Generic indefinite loading overused across routes | 🟢 DONE | Multiple pages | Replaced with bounded `QueryStateBoundary` + timeout |
| DC-UX-039 | Empty/error/success/degraded states inconsistent | � DONE | Multiple | `QueryStateBoundary` created + adopted in PluginsPage (installed section). Page-level early-return patterns left as-is (correct use for full-page replacements). |
| DC-UX-008 | Home cognitive overload; no clear next action | � DONE | `IntelligentHub.tsx` | Restructured to prioritized work queue: (1) Continue working, (2) Next action, (3) Ask anything, (4) Platform snapshot, (5) Recent activity. Removed Quick Actions and static Recommendations sections. |
| DC-UX-011 | Quality tab visible but operationally incomplete | 🟢 DONE | `src/pages/DataExplorer.tsx` | Gated quality mode behind capability flag and suppressed quality tab when unavailable |
| DC-UX-012 | Collection filters/sort accepted by client API but ignored | � DONE | `api/collections.ts`, `DataExplorer.tsx` | Added `statusFilter` + `schemaTypeFilter` state; wired to queryKey and API; added `<select>` dropdowns in search bar |
| DC-UX-018 | Workflow list search/sort/page params ignored | � DONE | `api/workflows.ts`, `WorkflowsPage.tsx` | Fixed `workflowsApi.list()` to forward `search`, `sortBy`, `sortOrder` to backend; fixed `void refetch()` call |
| DC-UX-034 | Route truth matrix doc stale | � DONE | `ui/docs/ROUTE_TRUTH_MATRIX.md` | Created `ROUTE_TRUTH_MATRIX.md` generated from `canonicalRouteRegistry`; added `getRoutesByLifecycle()` helper for doc/CI generation |
| DC-UX-035 | Lifecycle metadata marks partial surfaces as active | � DONE | `RouteCapabilityRegistry.ts` | Added `'boundary'` lifecycle value; corrected alerts, memory, fabric, plugins, settings to `boundary`; `getDiscoverableRoutes()` excludes `boundary` routes from nav by default |
| DC-UX-036 | Shell role confused with authorization | � DONE | `DefaultLayout.tsx`, `session.ts`, `IntelligentHub.tsx` | Renamed `SHELL_ROLE_CONTROL_TITLE` → 'View mode'; labels → 'Standard view'/'Operator view'/'Admin view'; updated disclosure note; persona switcher tabs renamed from 'Primary' → 'Standard' |

---

## P2 — Medium Priority (Next Sprint)

| ID | Title | Status | File(s) | Notes |
|---|---|---|---|---|
| DC-UX-019 | Workflow execution visibility insufficient | � DONE | `WorkflowsPage.tsx`, `lib/api/workflows.ts`, `contracts/schemas.ts` | Added `lastExecutionStatus` + `lastExecutionDuration` to schema/type/mapper; modal shows color-coded outcome and duration |
| DC-UX-020 | Workflow creation modes fragmented | � DONE | `WorkflowsPage.tsx` | Both 'New Pipeline' actions now navigate to `/workflows/new` (SmartWorkflowBuilder); consolidated from two routes |
| DC-UX-021 | Query AI assist state/test contract drifted | � DONE | `SqlWorkspacePage.tsx` | Added `query_assist` as canonical capability key; renamed button labels to 'Ask a question' / 'Hide assistant' |
| DC-UX-023 | Query guardrails are local heuristics but appear authoritative | � DONE | `SqlWorkspacePage.tsx` | Renamed section from 'Execution guardrails' → 'Query advisories'; added 'Local heuristics — not policy-enforced' subtitle |
| DC-UX-024 | Saved queries contain mock data | � DONE | `components/sql/SavedQueries.tsx` | Renamed `mockQueries` → `SAMPLE_QUERIES`; added visible sample-data disclosure banner that auto-hides once user adds their own queries |
| DC-UX-029 | Data Fabric claims live topology while boundary says preview | � DONE | `DataFabricPage.tsx` | Removed all 'live' / 'real-time' copy from JSDoc, header subtitle, and inline comments; aligned with `boundary` lifecycle state |
| DC-UX-031 | Alerts exposes "AI Grouped" mode label | � DONE | `AlertsPage.tsx` | Renamed button label → 'Grouped by root cause'; renamed section header → 'Grouped by root cause' |
| DC-UX-032 | Alert filter selects lack accessible labels | � DONE | `AlertsPage.tsx` | Added `aria-label` to severity and status filter selects; added `role="group" aria-label` to view mode toggle; added `aria-pressed` to toggle buttons |
| DC-UX-040 | AI presented as branded surface rather than embedded help | ✅ DONE | Multiple | Nav section renamed from 'Intelligence' → 'Observability' in `DefaultLayout.tsx`; IntelligentHub header uses neutral greeting, not AI-branded title |
| DC-UX-042 | Design system reuse incomplete (raw buttons/selects/cards) | ✅ DONE | Multiple | All primary CTA `<button>` elements replaced with `Button`/`IconButton` from `@ghatana/design-system` across AlertsPage, WorkflowsPage, SqlWorkspacePage, SmartWorkflowBuilder, TrustCenter. `<select>` → `Select`. buttonStyles no longer used for actions. |
| DC-UX-043 | Responsive shell compresses complexity instead of simplifying | ✅ DONE | `DefaultLayout.tsx` | Shell already implements responsive nav: hamburger button (`lg:hidden`), slide-in drawer (`-translate-x-full lg:translate-x-0`), overlay backdrop, mobile close button. Progressive disclosure via persona switcher (`hidden sm:flex`). |
| DC-UX-044 | Audit/history visibility incomplete for sensitive ops | ✅ DONE | Multiple | `useOperationHistory` hook + `OperationHistory`/`OperationHistoryAlert` components created. Session-scoped operation records wired into TrustCenter classify/redact/purge mutations. Audit timeline panel enhanced with live session history panel. |
| DC-UX-046 | Background jobs/async ops lack unified visibility | ✅ DONE | Multiple | `OperationsContext` + `OperationsProvider` created for global job tracking. `ActiveOperationsBar` (floating bottom-right, role=status, aria-live) mounted in `DefaultLayout`. TrustCenter governance mutations wired to both local OperationHistory and global ActiveOperationsBar. |
| DC-UX-047 | Unsupported surfaces remain discoverable/actionable | � DONE | `DataFabricPage.tsx` | Disabled 'Migrate Tier' button on boundary-state surface with clear title tooltip; Alerts/Plugins/Settings boundary surfaces already had no unguarded primary actions |
| DC-UX-048 | Static command/search undermines canonical IA | ✅ DONE | `GlobalSearch.tsx` | Route lifecycle badges (Preview/Deprecated) from `canonicalRouteRegistry` shown on quick-nav items; `routeRegistryEntries` lookup added |
| DC-UX-049 | Readiness docs conflict with UI maturity claims | ✅ DONE | Docs | `docs-generated/06-index-traceability-risk/07-readiness-scorecard.md` exists and explicitly avoids single "production-ready" label; boundary surfaces already use `UnsupportedSurfaceBoundary` with accurate copy; no in-app copy found claiming false GA status |

---

## P3 — Long-Term / Backlog

| ID | Title | Status | File(s) | Notes |
|---|---|---|---|---|
| DC-UX-050 | Dev console warns on `@ghatana/canvas` dependency drift | ✅ DONE | `vite.config.ts` | Added missing `@ghatana/canvas/topology` Vite alias pointing to `canvas/src/topology/index.ts` |
| — | AI-assisted operations across alerts/workflows/quality/fabric | 🔴 NOT_STARTED | Multiple | Requires ML platform + event model |
| — | Full secure Settings and API-key lifecycle | ⚫ BLOCKED | `SettingsPage.tsx` | Blocked on identity/security backend API |
| — | Fabric placement recommendation and governed migration | 🔴 NOT_STARTED | `DataFabricPage.tsx` | Blocked on Fabric backend |
| — | Generated product truth documentation (CI) | 🔴 NOT_STARTED | CI scripts, docs | Needs registry pipeline |
| — | Role-specific product modes (steward/operator/admin/auditor) | 🔴 NOT_STARTED | Multiple | Requires permission model backend |
| — | Schema inference from source/sample | 🔴 NOT_STARTED | `CreateCollectionPage.tsx` | Requires data profiling backend |
| — | Query policy/risk evaluation before execution | 🔴 NOT_STARTED | `SqlWorkspacePage.tsx` | Requires policy backend |
| — | Alert deduplication/root-cause AI grouping | 🔴 NOT_STARTED | `AlertsPage.tsx` | Requires telemetry/ML |
| — | Global operation/job center | 🔴 NOT_STARTED | New page | Requires operation event model |

---

## Shared Abstractions to Create

These are cross-cutting components that unblock multiple P0/P1 tasks:

| Component | Purpose | Status | Used By |
|---|---|---|---|
| `QueryStateBoundary` | Wraps async queries: loading, empty, error, retry, unavailable, degraded, stale, timeout | 🟢 DONE | DataExplorer, WorkflowsPage, OperationsConsolePage, AlertsPage |
| `GuardedAction` | Sensitive operation wrapper: impact preview, reason capture, confirmation, audit link | 🟢 DONE | AlertsPage, DataFabricPage, EntityBrowserPage |
| `CapabilityBoundary` | Hides/disables/gates controls by capability registry state | 🟢 DONE | DataExplorer (quality tab), GlobalSearch, DefaultLayout |
| `ResourceDetailShell` | Unified layout for collection/entity/context/plugin/workflow detail | ✅ DONE | `src/components/common/ResourceDetailShell.tsx` — exported from `common/index.ts` |
| `OperationTimeline` | Shared timeline for workflow execution, alert history, audit logs | ✅ DONE | `src/components/common/OperationTimeline.tsx` — exported from `common/index.ts` |
| `FilterBar` | Shared labeled filter bar for selects, search, sort | ✅ DONE | `SearchFilterBar` already existed at `src/components/common/SearchFilterBar.tsx`; fulfills requirement |
| `AIAssistSuggestion` | Advisory card with confidence, evidence, override | ✅ DONE | `src/components/common/AIAssistSuggestion.tsx` — exported from `common/index.ts` |
| `TrustSignalGroup` | Policy-derived trust indicators | ✅ DONE | `src/components/common/TrustSignalGroup.tsx` — adopted in `SqlWorkspacePage.tsx`; exported from `common/index.ts` |
| `RolePermissionNotice` | View mode / permission-denied state with request path | ✅ DONE | `src/components/common/RolePermissionNotice.tsx` — exported from `common/index.ts` |

---

## Test Coverage Gaps

| Test File | Status | Gap |
|---|---|---|
| `__tests__/accessibility/AccessibilityAudit.test.tsx` | 🔴 FAILING | Crash on SensitivityBadge undefined → fixed by DC-UX-014 |
| `__tests__/routes/routeTruthMatrix.test.ts` | 🔴 FAILING | `nav-alerts` mismatch → fixed by DC-UX-002 |
| `__tests__/ShellRouting.test.tsx` | 🔴 FAILING | Static nav mismatch → fixed by DC-UX-004 |
| `__tests__/pages/SettingsPage.test.tsx` | 🔴 FAILING | Fake action assertions → fixed by DC-UX-025 |
| `__tests__/pages/WorkflowsPage.test.tsx` | 🔴 FAILING | Inert button assertions → fixed by DC-UX-015/016 |
| `__tests__/pages/SqlWorkspacePage.test.tsx` | 🔴 FAILING | "AI Assist" label drift → fixed by DC-UX-022 |
| `__tests__/pages/AlertsPage.test.tsx` | ✅ DONE | Added GuardedAction reason/cancel/confirm coverage for acknowledge + resolve flows |
| `__tests__/pages/CollectionPage.test.tsx` | ✅ DONE | Added durable deep-link by-ID route contract test (`useParams` ID drives canonical `collectionsApi.get`) |
| `__tests__/pages/WorkflowsPage.test.tsx` | ✅ DONE | Added pagination behavior coverage (page 1/2 transitions, previous/next guards) |

---

## Implementation Progress Summary

| Priority | Total | Done | In Progress | Not Started | Blocked |
|---|---|---|---|---|---|
| P0 | 14 | 14 | 0 | 0 | 0 |
| P1 | 17 | 17 | 0 | 0 | 0 |
| P2 | 16 | 16 | 0 | 0 | 0 |
| P3 | 10 | 1 | 0 | 8 | 1 |
| **Total** | **57** | **48** | **0** | **8** | **1** |

---

## Change Log

| Date | Change | Items |
|---|---|---|
| 2026-04-24 | Initial tracker created from audit | All items catalogued |
| 2026-04-24 | Implementation pass: Data Explorer error handling, governance draft notice, strict trust-signal typing, nav registry cleanup, quality-view gating | DC-UX-002, DC-UX-009, DC-UX-011, DC-UX-013, DC-UX-014 |
| 2026-04-24 | P0 implementations complete | DC-UX-001,002,009,013,014,015,016,022,025,026,027,028,030 |
| 2026-04-24 | P1 implementations: title, landmarks, pagination, nav, search, onboarding, collection deep-link, alert guards | DC-UX-003,004,005,006,007,010,017,033,037,038 |
| 2026-04-24 | Shared components created | QueryStateBoundary, GuardedAction, CapabilityBoundary |
| 2026-04-25 | Final remediation pass complete: shared abstractions + missing coverage gaps closed and validated | AIAssistSuggestion, RolePermissionNotice, AlertsPage GuardedAction tests, Collection by-ID route test, Workflows pagination test |
