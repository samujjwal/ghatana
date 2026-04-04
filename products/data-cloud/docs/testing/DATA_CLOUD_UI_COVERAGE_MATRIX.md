# Data Cloud UI Coverage Matrix

> **Status**: Milestone 1 - Week 1 (Draft)
> **Purpose**: Track UI/frontend coverage by page, component, and E2E journey
> **Last Updated**: April 4, 2026
> **Sources**: products/data-cloud/ui/docs/web-page-specs/INDEX.md, UI package.json dependencies

---

## Overview

This matrix tracks coverage for Data Cloud UI surfaces:
- **UI Pages**: React page components (file, route, spec source)
- **API Integration**: Frontend service wrappers (api, lib/api)
- **Shared Components**: Design system + custom components
- **E2E Journeys**: End-to-end user workflows
- **Accessibility**: WCAG 2.1 AA compliance testing

---

## Section A: Core Pages (Collections, Workflows, Data)

### A.1 Collections Management

| Page | File | Spec Source | Test Suite | Contract? | Logic? | E2E? | A11y? | Status | Priority |
|------|------|-------------|-----------|-----------|--------|-------|-------|--------|----------|
| Collections List | `CollectionsPage.tsx` | `02_collections_page.md` | CollectionsPageTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P1** |
| Create Collection | `CreateCollectionPage.tsx` | `03_create_collection_page.md` | CreateCollectionTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P1** |
| Edit Collection | `EditCollectionPage.tsx` | `04_edit_collection_page.md` | EditCollectionTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P1** |
| Entity Browser | `EntityBrowserPage.tsx` | web-page-specs | EntityBrowserTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P1** |

**Required Test Suite**: `CollectionsUIIntegrationTest.ts`

**Coverage Targets**:
- LogicTests: form validation, state management, field updates
- ContractTests: POST /api/v1/collections schema validation
- E2ETest: user creates collection → adds entities → queries data
- AccessibilityTests: keyboard nav, form labels, error messages

---

### A.2 Workflows & Pipeline Designer

| Page | File | Spec Source | Test Suite | Contract? | Logic? | E2E? | A11y? | Status | Priority |
|------|------|-------------|-----------|-----------|--------|-------|-------|--------|----------|
| Workflows List | `WorkflowsPage.tsx` | `05_workflows_page.md, 07_workflow_list_page.md` | WorkflowsPageTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P1** |
| Workflow Designer | `WorkflowDesigner*.tsx` | `06_workflow_designer_canvas.md` | WorkflowDesignerTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |
| Workflow Legacy List | (legacy page) | `07_workflow_list_page.md` | Legacy (deprecated) | — | — | — | — | DEPRECATED | **P3** |

**Required Test Suites**: 
- `WorkflowsPageTest` (Week 11, list + CRUD operations)
- `WorkflowDesignerCanvasTest` (Week 11, canvas interactions, drag-drop, node editing)

---

### A.3 Data Exploration

| Page | File | Spec Source | Test Suite | Contract? | Logic? | E2E? | A11y? | Status | Priority |
|------|------|-------------|-----------|-----------|--------|-------|-------|--------|----------|
| Dataset Explorer | `DatasetExplorerPage.tsx` | `11_dataset_explorer_list_page.md` | DatasetExplorerTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P1** |
| Dataset Detail/Insights | `DatasetDetailPage.tsx` | `12_dataset_detail_insights_page.md` | DatasetDetailTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P1** |
| Lineage Explorer | `LineageExplorerPage.tsx` | `13_lineage_explorer_page.md` | LineageExplorerTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |
| SQL Workspace | `SqlWorkspacePage.tsx` | `14_sql_workspace_page.md` | SqlWorkspaceTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P1** |
| Event Explorer | `EventExplorerPage.tsx` | (custom) | EventExplorerTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |

**Required Test Suites**:
- `DatasetExplorerTest` (Week 10-11, search, filter, sort, drill-down)
- `SqlWorkspaceTest` (Week 10-11, query editor, results, history, AI suggest)
- `LineageExplorerTest` (Week 12, graph correctness, interaction)

---

## Section B: Analytics & Intelligence

### B.1 Dashboard & Analytics

| Page | File | Spec Source | Test Suite | Contract? | Logic? | E2E? | A11y? | Status | Priority |
|------|------|-------------|-----------|-----------|--------|-------|-------|--------|----------|
| Dashboard | `DashboardPage.tsx` | `01_dashboard_page.md` | DashboardPageTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P1** |
| Dashboards List | `DashboardsPage.tsx` | (custom) | DashboardsPageTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |
| Insights Page | `InsightsPage.tsx` | (custom) | InsightsPageTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |

**Coverage**:
- Widget refresh + quick actions (Dashboard)
- Dashboard CRUD + sharing (Dashboards)
- Metrics correctness + drill-down (Insights)

---

### B.2 AI Assistance

| Component | File | Location | Test Suite | Contract? | Logic? | Status | Priority |
|-----------|------|----------|-----------|-----------|--------|--------|----------|
| AI Assistant Chat | `AiAssistant.tsx` | components/ | AiAssistantTest | ❌ | ❌ | NOT_STARTED | **P2** |
| Semantic Search | (integrated) | (integrated) | (covered in api) | ✅ (API level) | ❌ | PARTIAL | **P2** |
| AI Suggestions | (inline) | (various pages) | (covered in page tests) | ❌ | ❌ | NOT_STARTED | **P2** |

---

## Section C: Governance, Admin, & Infrastructure

### C.1 Governance & Trust

| Page | File | Spec Source | Test Suite | Contract? | Logic? | E2E? | A11y? | Status | Priority |
|------|------|-------------|-----------|-----------|--------|-------|-------|--------|----------|
| Trust Center | `TrustCenterPage.tsx` | `16_governance_and_security_hub_page.md` | TrustCenterTest | ❌ | ❌ | ❌ | ⚠️ | NOT_STARTED | **P2** |
| Governance Hub | `GovernanceHub.tsx` | (merged with Trust Center) | (same suite) | ❌ | ❌ | ❌ | ⚠️ | NOT_STARTED | **P2** |
| Security Hub | `SecurityHub.tsx` | (merged with Trust Center) | (same suite) | ❌ | ❌ | ❌ | ⚠️ | NOT_STARTED | **P2** |

**Required Test Suite**: `TrustCenterAccessibilityTest` (Week 11)
- Policy display + interpretation
- Access control verification (who can execute what?)
- Accessibility: keyboard nav, screen reader labels

---

### C.2 Settings & Admin

| Page | File | Spec Source | Test Suite | Contract? | Logic? | E2E? | A11y? | Status | Priority |
|------|------|-------------|-----------|-----------|--------|-------|-------|--------|----------|
| Settings | `SettingsPage.tsx` | (custom) | SettingsPageTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |
| Alerts & Notifications | `AlertsPage.tsx` | `17_alerts_and_notifications_page.md` | AlertsPageTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |
| Data Fabric Admin | `DataFabricAdminPage.tsx` | inline | DataFabricAdminTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |
| Storage Profiles | `StorageProfilesPage.tsx` | `09_storage_profiles_admin_page.md` | (included in DataFabric) | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |
| Data Connectors | `DataConnectorsPage.tsx` | `10_data_connectors_admin_page.md` | (included in DataFabric) | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |

---

### C.3 Advanced Features

| Page | File | Spec Source | Test Suite | Contract? | Logic? | E2E? | A11y? | Status | Priority |
|------|------|-------------|-----------|-----------|--------|-------|-------|--------|----------|
| Memory Plane Viewer | `MemoryPlaneViewerPage.tsx` | (custom) | MemoryPlaneTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |
| Intelligent Hub | `IntelligentHubPage.tsx` | (custom) | IntelligentHubTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P2** |
| Plugins Manager | `PluginManagerPage.tsx` | (custom) | PluginManagerTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P3** |
| Agent/Plugin Manager | `AgentPluginManagerPage.tsx` | (custom) | AgentPluginManagerTest | ❌ | ❌ | ❌ | ❌ | NOT_STARTED | **P3** |

---

## Section D: Cross-Cutting

### D.1 Shell & Routing

| Component | File | Purpose | Test Suite | Contract? | Logic? | E2E? | Status | Priority |
|-----------|------|---------|-----------|-----------|--------|-------|--------|----------|
| App Router | `App.tsx` | Route resolution, 404 | UiShellRoutingTest | ⚠️ | ❌ | ❌ | NOT_STARTED | **P1** |
| Navigation Bar | `Navigation.tsx` | Navigation state, active route | (covered in routing) | ⚠️ | ❌ | ❌ | NOT_STARTED | **P1** |
| Layout Shell | `Layout.tsx` | Sidebar, main, responsive | (covered in page tests) | ⚠️ | ❌ | ❌ | PARTIAL | **P1** |
| 404 / Not Found | `NotFoundPage.tsx` | Missing resource fallback | (covered in routing) | ⚠️ | ⚠️ | ❌ | NOT_STARTED | **P1** |

**Required Test Suite**: `UiShellRoutingContractTest` (Week 10-11)
- Route resolution correctness
- 404 state + error message
- Unauthorized redirect to login
- Forbidden (403) error display

---

### D.2 Shared Components (from @ghatana/design-system)

| Component | Package | Purpose | Test Suite | Coverage | Status | Notes |
|-----------|---------|---------|-----------|----------|--------|-------|
| Button | @ghatana/design-system | Interactive element | ButtonTest (design-system) | ✅ Design system repo | EXTERNAL | Verify data-cloud uses canonical versions |
| Form Inputs | @ghatana/design-system | Text fields, selects | FormInputTest (design-system) | ✅ Design system repo | EXTERNAL | Verify validation in data-cloud forms |
| Modal | @ghatana/design-system | Dialogs | ModalTest (design-system) | ✅ Design system repo | EXTERNAL | Verify accessibility in data-cloud modals |
| Pagination | @ghatana/design-system | List pagination | PaginationTest (design-system) | ✅ Design system repo | EXTERNAL | Verify integration with list pages |
| Tabs | @ghatana/design-system | Tab navigation | TabsTest (design-system) | ✅ Design system repo | EXTERNAL | Verify keyboard nav in data-cloud pages |
| Theme Provider | @ghatana/theme | Styling + dark mode | ThemeTest (theme package) | ✅ Theme repo | EXTERNAL | Verify applied to data-cloud pages |

**Data Cloud Responsibility**:
- Verify canonical component imports (no custom duplicates)
- Test component integration in context (e.g., Button in forms)
- Accessibility compliance at page level

---

### D.3 Shared Service Integration

| Service | File | Purpose | Test Suite | Coverage | Status | Notes |
|---------|------|---------|-----------|----------|--------|-------|
| Realtime | `ui/src/lib/integrations/realtime-integration.tsx` | WebSocket, SSE | RealtimeIntegrationTest | ❌ | NOT_STARTED | **P2**: Test WS lifecycle, reconnect, message ordering |
| Auth | `ui/src/lib/auth` | JWT, token storage | AuthServiceTest | ⚠️ | PARTIAL | Verify token refresh + expiration handling |
| Performance | `ui/src/lib/performance` | Telemetry, profiling | PerformanceTest | ⚠️ | PARTIAL | Verify Core Web Vitals tracking |
| Accessibility | `ui/src/lib/accessibility` | a11y helpers | AccessibilityTest | ⚠️ | PARTIAL | Verify ARIA labels, keyboard support |
| Persistence | `ui/src/lib/persistence` | LocalStorage, IndexedDB | PersistenceTest | ⚠️ | PARTIAL | Verify state saved/restored correctly |

---

## Section E: API Integration (Frontend Service Layer)

| API Layer | Files | Coverage | Test Suite | Status | Notes |
|-----------|-------|----------|-----------|--------|-------|
| Collections Service | `ui/src/api/collections.service.ts` | CRUD calls | CollectionsServiceTest | NOT_STARTED | **P1** (Week 1-2): Zod schema validation |
| Events Service | `ui/src/api/events.service.ts` | Append, query, stream | EventsServiceTest | NOT_STARTED | **P1** (Week 2): Stream error handling |
| Analytics Service | `ui/src/api/analytics.service.ts` | Query, reports | AnalyticsServiceTest | NOT_STARTED | **P1** (Week 2): Query validation |
| Memory Service | `ui/src/api/memory.service.ts` | Get, search, delete | MemoryServiceTest | NOT_STARTED | **P1** (Week 2): Search result validation |
| Brain Service | `ui/src/api/brain.service.ts` | Workspace, stats | BrainServiceTest | NOT_STARTED | **P1** (Week 2): Workspace schema |
| Governance Service | `ui/src/api/governance.service.ts` | Policies, purge, redact | GovernanceServiceTest | NOT_STARTED | **P2**: Dry-run schema |
| Plugins Service | `ui/src/api/plugins.service.ts` | Discovery, install | PluginsServiceTest | NOT_STARTED | **P2**: Plugin lifecycle schema |
| Agents Service | `ui/src/api/agents.service.ts` | Registry, lookup | AgentsServiceTest | NOT_STARTED | **P2**: Capability matching schema |

**Template**:
```typescript
// ui/src/api/__tests__/collections.service.test.ts
import { z } from "zod";
import { CollectionSchema } from "../contracts/collection.contract";

describe("CollectionsService", () => {
  it("should validate API response schema", () => {
    const apiResponse = { id: "c1", name: "test", ... };
    const parsed = CollectionSchema.parse(apiResponse);
    expect(parsed.id).toBe("c1");
  });

  it("should reject invalid schema", () => {
    const badResponse = { name: "test" }; // missing id
    expect(() => CollectionSchema.parse(badResponse))
      .toThrow("Required");
  });
});
```

---

## E2E User Journeys (Selective, High-Value Only)

### Three Critical Journeys to Test

| Journey | Pages Involved | Test Suite | Effort | Estimated Week |
|---------|----------------|-----------|--------|-----------------|
| **Journey 1: Data Explorer** | Collections → Create → Add Entities → Query | DataExplorerE2ETest | 1 day | Week 11 |
| **Journey 2: Analytics** | Dashboard → Create Report → View Results | AnalyticsE2ETest | 1.5 day | Week 11 |
| **Journey 3: SQL Workspace** | SQL Editor → Execute → View Results → History | SqlWorkspaceE2ETest | 1 day | Week 12 |

**Note**: Not exhaustive E2E (would take 8+ weeks). Cover critical paths only. Other pages verified via logic/contract tests.

---

## Coverage Targets by Milestone

### Milestone 1 (Week 1-4)
- API contract validation: Zod schemas added to services
- Design system audit: verify canonical component usage

### Milestone 2 (Week 5-8)
- No UI testing (focus on backend real integrations)

### Milestone 3 (Week 9-12)
- **Week 10-11**: Contract tests for all pages
- **Week 11-12**: 3 critical E2E journeys
- **Week 12**: Accessibility pass (keyboard nav, screen reader)

### Milestone 4 (Week 13-16)
- Final E2E verification
- Performance regression baselines

---

## Code Quality Standards (TypeScript Tests)

### Required for All UI Tests

```typescript
// Example: CollectionsPageTest.tsx
/**
 * @test Collections page integration tests
 * Tests form validation, API calls, state management, accessibility  
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { CollectionsPage } from "./CollectionsPage";

describe("CollectionsPage", () => {
  // Setup
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // Test: page loads + initial state
  it("should render collection list on mount", async () => {
    render(<CollectionsPage />);
    expect(screen.getByRole("heading", { name: /collections/i }))
      .toBeInTheDocument();
  });

  // Test: form validation
  it("should reject empty collection name (boundary test)", () => {
    const { getByRole } = render(<CollectionsPage />);
    const input = getByRole("textbox", { name: /collection name/i });
    fireEvent.change(input, { target: { value: "" } });
    const submitBtn = getByRole("button", { name: /create/i });
    fireEvent.click(submitBtn);
    expect(screen.getByText(/collection name is required/i))
      .toBeInTheDocument();
  });

  // Test: API call + response validation
  it("should call API with valid schema", async () => {
    const mockCreate = vi.fn().mockResolvedValue({
      id: "c1", name: "test"
    });
    
    // Inject mock...
    // Fill form + submit
    // Verify: mockCreate called with validated schema
    // Verify: response parsed with Zod
  });

  // Test: accessibility
  it("should be keyboard navigable (a11y)", () => {
    render(<CollectionsPage />);
    const button = screen.getByRole("button", { name: /create/i });
    expect(button).toHaveAttribute("tabIndex", "0");
    // Tab key test...
  });
});
```

---

## CI Gate (UI Specific)

```bash
# Type check + lint UI tests
pnpm -F @ghatana/data-cloud-ui lint
pnpm -F @ghatana/data-cloud-ui type-check

# Run tests
pnpm -F @ghatana/data-cloud-ui test

# Coverage minimum
pnpm -F @ghatana/data-cloud-ui test:coverage
# Assert: ≥70% line, ≥65% branch for critical pages (P1)
```

---

## Weekly Execution (Milestone 3, Weeks 10-12)

### Week 10: Shell + Collections (2-3 days)
- [ ] UiShellRoutingContractTest
- [ ] CollectionsUIIntegrationTest (contract + logic)

### Week 11: Data Exploration + SQL (2-3 days)
- [ ] DatasetExplorerTest
- [ ] SqlWorkspaceE2ETest (critical journey 2-3)
- [ ] Accessibility audit for primary pages

### Week 12: Final E2E (1-2 days)
- [ ] All E2E journeys passing
- [ ] Performance baseline captured
- [ ] Accessibility compliance verified

---

## References

- UI Specs Index: [products/data-cloud/ui/docs/web-page-specs/INDEX.md](../../ui/docs/web-page-specs/INDEX.md)
- Design System: [@ghatana/design-system package](../../../../../platform/typescript/design-system)
- Vitest Docs: [vitest.dev](https://vitest.dev)
- Testing Library: [testing-library.com](https://testing-library.com/react)
- Zod Schemas: [zod.dev](https://zod.dev)

---

**Status**: Ready for Milestone 3 (Week 9-12) kickoff  
**Next Action**: Review page specs (web-page-specs/) + identify shared components
