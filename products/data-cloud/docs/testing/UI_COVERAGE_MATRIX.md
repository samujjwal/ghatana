# Data Cloud UI Coverage Matrix

> **Week 1 (Milestone 1) Deliverable**  
> **Status**: Drafted April 4, 2026  
> **Purpose**: Track UI page and component coverage across 18+ pages, component libraries, and E2E workflows.

## Overview

**Total Pages**: 18 (from web-page-specs/INDEX.md)  
**Shared Component Libraries**: 12+ (design-system, forms, modals, etc.)  
**E2E Workflows**: 15+ (user journeys across pages)  
**Coverage Types**:
- ✅ Logic tests (component state, handlers, validation)
- ✅ Component tests (render, interaction, accessibility)
- ✅ Page E2E tests (full page flow with real API)
- ✅ Accessibility tests (WCAG 2.1 AA)

---

## Page-Level Coverage

### Section 0: Shell & Routing

| Page | Route | Component | Logic Tests | Component Tests | E2E Tests | A11y Tests | Status |
|------|-------|-----------|---|---|---|---|--------|
| Shell & Routing | `/` | `App.tsx`, `App Router` | route-resolution, nav, suspense, redirects | render-all-routes, nav-functionality | E2E-AppBootstrap | keyboard-nav, focus-mgmt | TODO |
| Not Found | `/404` | `NotFound/index.tsx` | fallback-render | render-404 | E2E-NotFoundFlow | accessible-error-msg | TODO |
| Global Error Boundary | N/A | `ErrorBoundary.tsx` | error-capture, recovery | render-error-state | crash-recovery | error-msg-clarity | TODO |
| Loading Fallback | N/A | `LoadingFallback.tsx` | show-hide-logic | render-spinner | streaming-load-state | a11y-live-region | TODO |

---

### Section 1: Core Dashboards & Collections

| Page | Route | Component | Logic Tests | Component Tests | E2E Tests | A11y Tests | Status |
|------|-------|-----------|---|---|---|---|--------|
| Dashboard (Overview) | `/` or `/dashboard` | `DashboardPage.tsx` | widget-state, refresh, metrics | render-summary, quick-actions | E2E-DashboardFlow | keyboard-nav, SR-labels | TODO |
| Dashboards & Metrics | `/dashboards` | `DashboardsPage.tsx` | custom-dashboard-crud, widget-mgmt | render-list, edit-mode | E2E-CustomDashboard | color-contrast | TODO |
| Collections List | `/collections` | `CollectionsPage.tsx` | list-filtering, sorting, pagination | render-table, bulk-actions | E2E-CollectionsListFlow | table-a11y, pagination | TODO |
| Collection Detail | `/collections/{id}` | `CollectionDetailPage.tsx` + tabs | schema-viewer, validation | render-tabs, expand-schema | E2E-CollectionDetailFlow | tab-nav, focus-mgmt | TODO |
| Create Collection | `/collections/new` | `CreateCollectionPage.tsx` | form-validation, schema-builder | schema-form-render, preview | E2E-CreateCollectionFlow | form-a11y, error-msgs | TODO |
| Edit Collection | `/collections/{id}/edit` | `EditCollectionPage.tsx` | form-state, versioning-check | form-render, cancel-confirm | E2E-EditCollectionFlow | form-a11y, validation-msgs | TODO |

---

### Section 2: Workflows & Pipeline Management

| Page | Route | Component | Logic Tests | Component Tests | E2E Tests | A11y Tests | Status |
|------|-------|-----------|---|---|---|---|--------|
| Workflows List | `/workflows` | `WorkflowsPage.tsx` or `DataCloudWorkflowsPage.tsx` | list-filtering, sorting, pagination | render-table, execute-btn | E2E-WorkflowsListFlow | table-a11y | TODO |
| Workflow Legacy List | `/workflows/v0` (if applicable) | `WorkflowListPage.tsx` | backward-compat-render | render-list, actions | — | keyboard-nav | TODO |
| Workflow Designer | `/workflows/{id}/edit` or `/workflows/new` | `WorkflowDesigner.tsx`, canvas | form-state, node-ops, save-logic | canvas-render, node-click, drag | E2E-WorkflowDesignFlow | keyboard-edit, focus-trap | TODO |
| Workflow AI Assist | Inside Designer | AI assist panel | fallback-behavior, confidence | panel-render, suggestion-click | E2E-AIAssistFlow | focus-mgmt | TODO |

---

### Section 3: Data Exploration & Analytics

| Page | Route | Component | Logic Tests | Component Tests | E2E Tests | A11y Tests | Status |
|------|-------|-----------|---|---|---|---|--------|
| Dataset Explorer (List) | `/datasets` | `DatasetExplorerPage.tsx` | search-filter-sort, results-update | render-list, filter-apply | E2E-DatasetSearchFlow | search-a11y, focus-mgmt | TODO |
| Dataset Detail & Insights | `/datasets/{name}` | `DatasetDetailPage.tsx`, tabs | schema-viewer, metrics-calc | render-tabs, sample-data-view | E2E-DatasetDetailFlow | tab-nav, data-table-a11y | TODO |
| Lineage Explorer | `/lineage` | `LineageExplorerPage.tsx`, canvas | graph-fetch, filter-state, drill-down | canvas-render, node-click | E2E-LineageFlow | canvas-keyboard, focus, SR | TODO |
| SQL Workspace | `/sql` | `SqlWorkspacePage.tsx` | editor-syntax, query-validation, execute | editor-render, results-table | E2E-SQLQueryFlow | editor-a11y, table-roles | TODO |

---

### Section 4: Brain, Memory & Intelligence

| Page | Route | Component | Logic Tests | Component Tests | E2E Tests | A11y Tests | Status |
|------|-------|-----------|---|---|---|---|--------|
| Memory Plane Viewer | `/memory` or `/brain/memory` | `MemoryPlaneViewerPage.tsx` | tier-filtering, search, pagination | render-memory-tree, expand | E2E-MemoryPlaneBrowse | tree-a11y, focus-mgmt | TODO |
| Intelligent Hub | `/brain` or `/intelligent-hub` | `IntelligentHub.tsx` | recommendations-fetch, live-updates | render-cards, refresh | E2E-IntelligentHubFlow | card-focus, live-region | TODO |
| Workspace Spotlight | Inside Brain | spotlight-panel | state-show-hide, item-click | panel-render, item-select | E2E-SpotlightFlow | panel-focus-trap | TODO |

---

### Section 5: Governance, Security & Admin

| Page | Route | Component | Logic Tests | Component Tests | E2E Tests | A11y Tests | Status |
|------|-------|-----------|---|---|---|---|--------|
| Governance & Security Hub | `/governance` | `GovernanceSecurityPage.tsx` | policy-list, pii-scan, audit-logs | render-tabs, policy-view | E2E-GovernanceFlow | tab-a11y, focus-mgmt | TODO |
| Trust Center | `/trust-center` or within Governance | `TrustCenter.tsx` | access-eval, permission-check | render-controls, policy-list | E2E-TrustCenterFlow | form-a11y | TODO |
| Settings Page | `/settings` | `SettingsPage.tsx` | config-save, validation | settings-form-render, toggle | E2E-SettingsFlow | form-a11y, validation | TODO |
| Storage Profiles Admin | `/admin/storage-profiles` | `StorageProfilesAdminPage.tsx` | list-crud, sync-status | form-render, status-indicator | E2E-StorageProfileFlow | form-a11y | TODO |
| Data Connectors Admin | `/admin/data-connectors` | `DataConnectorsAdminPage.tsx` | connector-crud, sync-mgmt | form-render, status-polling | E2E-ConnectorFlow | form-a11y | TODO |

---

### Section 6: Alerts, Learning & Notifications

| Page | Route | Component | Logic Tests | Component Tests | E2E Tests | A11y Tests | Status |
|------|-------|-----------|---|---|---|---|--------|
| Alerts & Notifications | `/alerts` | `AlertsPage.tsx` | alert-list, filtering, acknowledge | render-list, action-buttons | E2E-AlertsFlow | list-a11y, focus | TODO |
| Learning Review Queue | `/learning/review` | `LearningReviewPage.tsx` | review-item-fetch, approve-reject | queue-render, button-click | E2E-ReviewQueueFlow | form-a11y | TODO |

---

### Section 7: Plugins & Models

| Page | Route | Component | Logic Tests | Component Tests | E2E Tests | A11y Tests | Status |
|------|-------|-----------|---|---|---|---|--------|
| Plugins Page | `/plugins` | `PluginsPage.tsx` | plugin-list-fetch, lifecycle | render-list, enable-disable | E2E-PluginsFlow | list-a11y, focus | TODO |
| Plugin Details | `/plugins/{id}` | `PluginDetailsPage.tsx` | config-render, capability-display | render-config, error-msgs | E2E-PluginDetailFlow | form-a11y | TODO |
| Agent/Plugin Manager | `/admin/agents` or `/admin/plugins` | `AgentPluginManagerPage.tsx` | agent-list, registry-display | render-list, register-flow | E2E-AgentRegistryFlow | form-a11y | TODO |
| Model Registry | `/models` | `ModelRegistryPage.tsx` | model-list, promotion-logic | render-list, promote-btn | E2E-ModelPromotionFlow | list-a11y | TODO |

---

### Section 8: Analytics & Reporting

| Page | Route | Component | Logic Tests | Component Tests | E2E Tests | A11y Tests | Status |
|------|-------|-----------|---|---|---|---|--------|
| Insights Page | `/insights` | `InsightsPage.tsx` | metrics-calc, chart-render | charts-render, drill-down | E2E-InsightsFlow | chart-a11y (role=img) | TODO |
| Data Explorer | `/data-explorer` | `DataExplorer.tsx` | result-fetch, drill-down | table-render, sort/filter | E2E-DataExplorerFlow | table-a11y | TODO |
| Reports | `/reports` | `ReportsPage.tsx` | report-gen, list-fetch | render-list, download-btn | E2E-ReportsFlow | list-a11y | TODO |

---

### Section 9: Voice & Realtime

| Page | Route | Component | Functionality | Test Type | Status |
|------|-------|-----------|---|---|--------|
| Voice Controls (widget) | Embedded in pages | `VoiceWidget.tsx` | transcript-show, intent-exec | Logic + Component + E2E | TODO |
| Realtime WebSocket | Embedded (whole-app) | `useRealtimeChannel.ts`, wslib | connection, message, reconnect | Integration + E2E | TODO |

---

## Shared Component Library Coverage

### Design System Components

| Component | Location | Props Tested | States Tested | A11y Tested | E2E Used In | Status |
|-----------|----------|---|---|---|---|--------|
| Button | `@ghatana/design-system` | variant, size, icon, loading | default, hover, disabled, loading | focus, color-contrast | All pages | TODO |
| Input | `@ghatana/design-system` | type, placeholder, error, disabled | default, error, disabled, focus | label-assoc, ARIA | Forms (5+) | TODO |
| Select/Dropdown | `@ghatana/design-system` | options, value, multi, disabled | open, closed, disabled | keyboard-open-close | Filters (4+) | TODO |
| Modal | `@ghatana/design-system` | title, onClose, size | open, closed, backdrop | focus-trap, restore-focus | Dialogs (6+) | TODO |
| Tabs | `@ghatana/design-system` | tabs[], activeTab, onChange | active, hover | keyboard-nav, ARIA | Collections (2+) | TODO |
| Table | `@ghatana/design-system` | columns[], rows[], sort, paginate | empty, loading, sorted, paginated | table-role, header-scope | Lists (8+) | TODO |
| Toast/Alert | `@ghatana/design-system` | type, message, onClose | success, error, warning, info | polite-live-region | All pages | TODO |
| Spinner | `@ghatana/design-system` | size, color | animated | aria-busy, SR-hidden | Async ops (all) | TODO |

### Form Components

| Component | Location | Feature | Test Type | Status |
|-----------|----------|---------|---|--------|
| Form Builder | `ui/src/lib/forms` | schema-to-form, validation | Logic + Component | TODO |
| Date Picker | `ui/src/lib/forms` | calendar, keyboard | Component + A11y | TODO |
| Schema Editor (JSON) | `ui/src/components/schema-editor` | json-edit, validation | Component + E2E | TODO |
| Code Editor | `ui/src/components/code-editor` | syntax, mode-switch | Component + E2E | TODO |

### Data Visualization Components

| Component | Location | Features | Test Type | Status |
|-----------|----------|---------|---|--------|
| Chart (line, bar, pie) | `@ghatana/charts` | data-bind, legend, tooltip | Component | TODO |
| Graph / Canvas | `@ghatana/canvas`, `@ghatana/flow-canvas` | render, zoom, pan, drag | Component + E2E | TODO |
| Tree / Hierarchy View | `ui/src/components/tree` | expand, collapse, select | Component + A11y | TODO |

### Specialized Components

| Component | Location | Purpose | Test Type | Status |
|-----------|----------|---------|---|--------|
| Lineage Canvas | `ui/src/components/lineage-canvas` | DAG render, drill-down | Component + E2E | TODO |
| Workflow Canvas | `ui/src/components/workflow-canvas` | node ops, drag, link | Component + E2E | TODO |
| Memory Browser | `ui/src/components/memory-browser` | tree-view, search, tier | Component + E2E | TODO |
| Voice Transcript | `ui/src/components/voice` | live transcript, intent icons | Component + A11y | TODO |

---

## Route-Level API Integration Tests

These are E2E tests that verify the React page works end-to-end with the **real API**.

| Page | Route | API Dependency | E2E Test Suite | Status |
|------|-------|---|---|--------|
| Collections List | `/collections` | `GET /api/v1/entities/*` (implied) | `CollectionsPageE2E` | TODO |
| Workflows List | `/workflows` | `GET /api/v1/pipelines` | `WorkflowsPageE2E` | TODO |
| Dataset Explorer | `/datasets` | `GET /api/v1/entities/.../search` | `DatasetExplorerPageE2E` | TODO |
| Lineage Explorer | `/lineage` | Lineage service (implied) | `LineagePageE2E` | TODO |
| SQL Workspace | `/sql` | `POST /api/v1/analytics/query` | `SQLWorkspacePageE2E` | TODO |
| Dashboard | `/dashboard` | Multiple metrics APIs | `DashboardPageE2E` | TODO |
| Memory Viewer | `/memory` | `GET /api/v1/memory/*` | `MemoryPageE2E` | TODO |

---

## Workflow/Use-Case E2E Tests

These test **complete user journeys** (multiple pages + APIs).

| Workflow | Pages Involved | APIs Called | E2E Test Suite | Status |
|----------|---|---|---|--------|
| Create Collection | Workspace → Collections → Create → Detail | POST /entities, schema validation | `UserCreateCollectionFlow` | TODO |
| Design & Run Workflow | Workflows → Designer → Save → (execute from AEP) | POST /pipelines, PUT /pipelines, optimization hints | `UserWorkflowDesignFlow` | TODO |
| Query Dataset | Dataset Explorer → SQL Workspace → Results → Export | GET /entities/search, POST /analytics/query, GET /export | `UserQueryDataFlow` | TODO |
| Govern Data | Collections → Settings → Governance Hub → Apply Policy → Monitor Purge | POST governance/* APIs | `UserGovernanceFlow` | TODO |
| Agent Learning | LearningReview → Approve Policies → Monitor Trigger | Learning/Brain APIs | `UserAgentLearningFlow` | TODO |

---

## Accessibility Testing Matrix

| Criterion | Pages Affected | Testing Method | Status |
|-----------|---|---|--------|
| **Keyboard Navigation** | All pages | E2E with Tab, Arrow, Enter, Escape | TODO |
| **Screen Reader (SR)** | All pages | axe-core + manual testing with NVDA/JAWS | TODO |
| **Color Contrast** | All pages (especially charts) | axe-core contrast checker (WCAG AA) | TODO |
| **Focus Visibility** | All interactive (buttons, links, inputs) | Visual + axe-core | TODO |
| **ARIA Labels** | Forms, modals, alerts, live regions | axe-core + SR testing | TODO |
| **Semantic HTML** | All pages | Lighthouse audit + manual | TODO |
| **Zoom & Text Scaling** | All pages | Browser zoom @200%, 1.5x text scaling | TODO |
| **Motion & Animation** | Dashboard, real-time updates | prefers-reduced-motion testing | TODO |

---

## Coverage Summary

### Pages

| Status | Count |
|--------|-------|
| 📋 Assigned to test suites | 18 |
| ✅ Logic tests written | 0 (Week 1 target: 5) |
| ✅ Component tests written | 0 (Week 1 target: 5) |
| ✅ E2E tests written | 0 (Week 1 target: 2) |
| ✅ A11y tests written | 0 (Week 1 target: 3 pages) |

### Component Libraries

| Status | Count |
|--------|-------|
| 📋 Assigned to test suites | 20+ |
| ✅ Tests written | 0 (Week 1 target: Design System core 8 components) |

### Workflows

| Status | Count |
|--------|-------|
| 📋 Critical user journeys identified | 5 |
| ✅ E2E tests written | 0 (Week 1 target: Start 1) |

---

## P1 Tests (Week 1-2 Priority)

**MUST START**:
1. `CollectionsPageE2E` — Core user feature
2. `WorkflowsPageE2E` — Core user feature
3. Design System core components (Button, Input, Modal, Table)
4. `UserCreateCollectionFlow` — Critical user journey
5. Dashboard basic render + widget tests

---

## Testing Stack & Tools

**Setup Required**:
- [ ] Playwright (E2E)
- [ ] React Testing Library (component logic)
- [ ] axe-core (accessibility)
- [ ] Vitest (unit tests)
- [ ] @testing-library/user-event (interaction testing)

**Test File Placement** (per Ghatana Rule 16):
```
ui/src/
  pages/
    CollectionsPage.tsx
    __tests__/
      CollectionsPage.test.tsx
  components/
    Button.tsx
    __tests__/
      Button.test.tsx
  services/
    api.service.ts
    __tests__/
      api.service.test.ts
  e2e/  ← Playwright E2E tests
    collections-flow.spec.ts
    workflow-flow.spec.ts
```

---

## Success Criteria (Per Page)

**Each page is "done"** when:
1. ✅ All user-facing logic has unit tests (>80% coverage)
2. ✅ All interactive elements have component tests
3. ✅ Page-level E2E test with real API passing
4. ✅ Accessibility audit passes (axe-core)
5. ✅ Keyboard navigation works (all interactive elements reachable)
6. ✅ Screen reader tested (labels, live regions, roles correct)
7. ✅ No visual regressions (screenshot baseline exists if visual testing enabled)
8. ✅ Code review passed (Ghatana conventions, Rule 7 — strict TypeScript, no `any`)

---

**Maintainer**: Data Cloud Platform Engineering  
**Last Updated**: April 4, 2026  
**Version**: 1.0 (Draft)
