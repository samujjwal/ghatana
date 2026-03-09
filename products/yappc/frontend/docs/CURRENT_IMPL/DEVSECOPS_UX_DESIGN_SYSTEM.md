# DevSecOps UX Design System & Implementation Plan

**Scope:** DevSecOps dashboard, canvas, phases, persona views, reports, settings, templates, and shared components across web and desktop shells for `app-creator`.

**Goal:** Turn the existing DevSecOps feature set (routes, canvas, shared libs) into a **world‑class, demo‑ready multi‑persona experience** with:

- Fully sketched, clickable UI for **all personas** and flows.
- **Centralized mocking** that can later be swapped for a real backend with minimal change.
- **Zero duplication** of domain types, state, and UI patterns; everything flows through the shared DevSecOps libs.
- Consistent **design system** usage (tokens, typography, spacing) per `DESIGN_SYSTEM_GUIDELINES.md` and `UX_UI_DESIGN_AUDIT.md`.

---

## 1. Personas & Primary Journeys

### 1.1 Personas (Mapped to Existing Types/Configs)

- **Executive / CISO / Business Leader**  
  - Backed by: `personaDashboards` in `apps/web/src/mocks/handlers.ts` (CISO, compliance officer), `EXECUTIVE_KPIS` in `apps/web/src/components/canvas/devsecops/kpis.ts`, `EXECUTIVE_DASHBOARD_TEMPLATE` in `templates.ts`.
  - Key questions:
    - "Are we **on track** to deliver this quarter's objectives?"
    - "What is our **risk posture & compliance** status right now?"
    - "Where do I need to **intervene** (blocked initiatives, critical vulns)?"

- **DevSecOps Engineer / Platform Engineer**  
  - Backed by: `FULL_DEVSECOPS_TEMPLATE`, `SECURITY_FOCUSED_TEMPLATE`, and pipeline‑style KPIs in `kpis.ts`; `DevSecOpsCanvas` and `PhaseCanvas` interactions.
  - Questions:
    - "What is the **health of pipelines** and security gates today?"
    - "Which **phases** are bottlenecked or blocked?"
    - "What should I **fix or automate** next?"

- **Compliance Officer / Risk Manager**  
  - Backed by: `personaDashboards` (COMPLIANCE_OFFICER), security/compliance KPIs in `SECURITY_KPIS`, enterprise template items (`SOX`, `SOC2`, `ISO27001`).
  - Questions:
    - "Are we **audit‑ready** for SOC2/GDPR/SOX this period?"
    - "What exceptions, evidence gaps, and failed controls do we have?"

- **Product / Delivery (PM, Architect, Developer, QA, DevOps)**  
  - Backed by: `MVP_TEMPLATE`, `ENTERPRISE_TEMPLATE`, development velocity & quality KPIs, `PhaseCanvas` cards, `PhaseNav`, Kanban/Timeline/DataTable.
  - Questions:
    - "What is the **status of my features** across phases?"
    - "Which work items are **blocked** and why?"
    - "What is our **velocity and quality trend**?"

### 1.2 Primary Journeys

1. **Executive Overview → Persona Dashboard → Report:**
   - `/devsecops` dashboard: glanceable KPIs & alerts → **Persona Dashboards** grid → `/devsecops/persona/:slug` → generate reports via `/devsecops/reports`.

2. **DevSecOps Engineer Flow:**
   - `/devsecops` → **Phases** section → `/devsecops/phase/:phaseId` → switch view (Kanban, Timeline, Table) → open item side panel → drill into canvas (`DevSecOpsCanvas`) for advanced planning.

3. **Compliance Officer Flow:**
   - `/devsecops` → persona card "Compliance Officer" → `/devsecops/persona/compliance-officer-assurance` → jump into `/devsecops/reports/security` and `/devsecops/settings` → use **Compliance** tab.

4. **Hands‑On Delivery Flow:**
   - `/devsecops` → phase cards → `/devsecops/phase/development` → Kanban for items → open side panel for details, KPIs, related artifacts/integrations.

5. **Design/Planning Flow (Canvas):**
   - `/devsecops` → "Open Canvas" (planned) → `DevSecOpsCanvas` with templates per persona → drag/drop items across phases, run KPIs dashboard, generate executive reports.

---

## 2. Current Architecture & Surfaces (Summary)

### 2.1 Domain Types (Canonical, Reuse‑First)

- File: `libs/types/src/devsecops/index.ts`  
  - **Core concepts:** `PhaseKey`, `Priority`, `ItemStatus`, `UserRole`, `Phase`, `Item`, `Artifact`, `KPI`, `ItemFilter`, `ViewConfig`, `ApiResponse<T>`, `PaginatedResponse<T>`, etc.
  - **Integrations:** `JiraIntegration`, `GitHubIntegration`, `SonarQubeIntegration` etc.
  - *Plan:* All DevSecOps UIs (routes, canvas, settings, reports) **must use these types** instead of ad‑hoc shapes.

### 2.2 Shared UI Library: `libs/ui/src/components/DevSecOps`

- Navigation:
  - `TopNav`, `Breadcrumbs`, `PhaseNav`.
- Cards / Layout:
  - `KPICard`, `ItemCard`, `SidePanel`.
- Data views:
  - `KanbanBoard`, `Timeline`, `DataTable`, `FilterPanel`, `SearchBar`, `ViewModeSwitcher`.
- *Plan:* DevSecOps routes should **only compose these building blocks**, not custom MUI layouts, where possible.

### 2.3 Shared State & Mock Data

- `libs/ui/src/state/devsecops/atoms.ts` & `hooks.ts`  
  - Async Jotai atoms backed by `mockBackend` service.  
  - Derived atoms: `filteredItemsAtom`, `itemsByStatusAtom`, `phaseStatisticsAtom`, etc.  
  - Mutation atoms: `createItemAtom`, `updateItemAtom`, `deleteItemAtom`, `bulkUpdateItemsAtom`.
- `libs/ui/src/state/devsecops/mockDataService.ts`  
  - `generateMockItems`, `mockPhases`, `mockMilestones`, `initializeMockData()`.

- **Web‑app specific state:** `apps/web/src/state/devsecops.ts`  
  - Separate Jotai store & `initializeMockData()` hitting `/api/devsecops/overview` (MSW handler).

### 2.4 API & Real‑Time Layers

- `libs/api/src/devsecops/client.ts`  
  - `DevSecOpsClient`: `getPhases`, `getItems`, `getReports`, `getReport`, `updateItemStatus`, with inline mock data.
- `libs/api/src/devsecops/cache.ts`  
  - `CacheManager` (in‑mem) + `PersistentCache` (localStorage‑backed).
- `libs/api/src/devsecops/integrations.ts`  
  - `GitHubAdapter`, `JiraAdapter`, `SonarQubeAdapter`, `IntegrationManager` – all mocked.
- `libs/api/src/devsecops/websocket.ts`  
  - `DevSecOpsWebSocket` with mock event stream.

### 2.5 Canvas Experience

- `DevSecOpsCanvas.tsx`  
  - Top bar with **role selector**, **view mode switch** (canvas/kanban/timeline/dashboard), **templates** & **add item** actions.  
  - Integrates `PhaseCanvas` (card grid / Kanban / placeholder timeline) and `KPIDashboard`.
- `useDevSecOpsCanvas.ts`  
  - Local React state + `localStorage` persistence for items, reports, filters, view mode.
- `kpis.ts` / `templates.ts` / `types.ts`  
  - KPI sets and **persona/phase‑specific canvas templates**.

### 2.6 DevSecOps Routes (Web App)

- Router: `apps/web/src/router/routes.tsx`
  - `path: /devsecops` → `DevSecOpsLayout` → children:
    - `index` → `DevSecOpsDashboard` (`apps/web/src/routes/devsecops/index.tsx`).
    - `phase/:phaseId` → Phase page.
    - `persona/:slug` → Persona dashboard.
  - Files exist for `/devsecops/reports`, `/devsecops/settings`, `/devsecops/templates`, but **not yet wired** into router.

- Layout: `_layout.tsx`  
  - Wraps children with `TopNav`, loads mock data into `apps/web` Jotai atoms via `/api/devsecops/overview` + MSW.

- Pages:
  - `index.tsx` – Dashboard with KPI strip, Persona cards, Phase overview, Alerts, Recent activity.
  - `phase/$phaseId.tsx` – Phase view with `PhaseNav`, `ViewModeSwitcher`, `SearchBar`, `FilterPanel`, `KanbanBoard` / `Timeline` / `DataTable`, `SidePanel`.
  - `persona/$slug.tsx` – Persona dashboard detail (hero banner, KPI grid, focus areas, insights, CTA).
  - `reports.tsx` – Reports hub (4 report cards, filter buttons).
  - `settings.tsx` – Settings & Governance tabs (Roles & Access, Integrations, Automation, Compliance).
  - `templates.tsx` – Templates & Playbooks accordion listing 4 templates.

### 2.7 Mock Backend (Browser)

- `apps/web/src/mocks/handlers.ts`  
  - `/api/devsecops/overview` returns **phases, milestones, items, activity, personaDashboards** for dashboard + routes.

---

## 3. Key Gaps & Issues

### 3.1 Fragmented State & Mocking

- Three separate DevSecOps data stacks:
  - **MSW** + `apps/web/src/state/devsecops.ts` for routes.
  - **Jotai + mockBackend** in `libs/ui/src/state/devsecops`.
  - **`DevSecOpsClient` + adapters** in `libs/api/src/devsecops`.
- Types differ between these stacks (simplified shapes vs canonical `libs/types/devsecops`).

**Impact:**
- Hard to **swap mocks for real backend** (must change multiple layers).
- Demo UIs can drift out of sync (routes vs canvas vs shared libs).

### 3.2 Dual Surfaces: Routes vs Canvas

- `DevSecOpsDashboard` (under `apps/web/src/devsecops`) and `DevSecOpsCanvas` are **two separate dashboards**, each with their own modeling of phases/items/KPIs.
- Personas, templates, and KPIs defined in canvas are **not visible** in the `/devsecops` routes.

**Impact:**
- Personas experience different, partially overlapping UIs.  
- Harder to tell a coherent story from exec view → phases → canvas.

### 3.3 Incomplete Navigation & IA

- Router does not yet link to:
  - `/devsecops/reports` from TopNav.
  - `/devsecops/settings` / `/devsecops/templates` pages.
- No deep report detail route (e.g. `/devsecops/reports/security`).
- No route to `DevSecOpsCanvas` from `/devsecops` shell.

### 3.4 Visual / Design System Drift

- `UX_UI_DESIGN_AUDIT.md` & `IMMEDIATE_IMPROVEMENTS.md` highlight:
  - Mixed styling (`sx`, Tailwind, inline colors).
  - Inconsistent typography scale (e.g., KPI values too small).
  - DevSecOps dashboard spacing & grouping issues.
- Several DevSecOps components (routes, canvas) use **hard‑coded colors** instead of `devsecops-tokens.css`.

### 3.5 Interaction Gaps

- Timeline views in `PhaseCanvas` and route‑phase view are **placeholder / minimal**.
- Persona dashboards are static; no per‑persona actions or cross‑links (e.g., "Open executive canvas view" or "Generate compliance report").
- Settings → Integrations, Automation, Compliance sections are **static**; they do not leverage the DevSecOps integration APIs / mocked adapters.

### 3.6 Desktop Experience

- Desktop shell exists (`apps/desktop`) but current DevSecOps UIs are optimized mainly for web.  
- No explicit **desktop framing** (window chrome, offline state, connection indicators) around DevSecOps.

---

## 4. Target Experience: Page‑Level Designs & Mockups

Below mockups are **structural and behavioral**; implementation should re‑use existing components and design tokens.

### 4.1 `/devsecops` – Executive Dashboard & Persona Hub

**Layout Mockup (High‑Level)**

- **Global shell** (from `DevSecOpsLayout`)
  - `TopNav` with:
    - User menu (role‑aware, e.g., Executive / DevSecOps Engineer / Compliance Officer).
    - Navigation tabs: Dashboard · Phases · Persona · Reports · Settings · Canvas.
    - Notifications badge (WebSocket‑driven in future).

- **Section A – Hero & KPI strip**
  - Full‑width hero card:
    - Title: "DevSecOps Executive Dashboard".
    - Subtitle: brief explanation of what this dashboard shows.
    - Small metadata row: last updated timestamp, environment, total phases & items.
  - KPI strip (3–4 `KPICard`s), pulling from **aggregated KPIs** (via central state) and styled per `IMMEDIATE_IMPROVEMENTS.md` (h2 metrics, improved hover, 3‑column layout on desktop).

- **Section B – Persona Dashboards** (already present, to be refined)
  - Grid of persona cards using **canonical persona data** from central store (not only MSW).  
  - Each card shows:
    - Persona label (e.g., `CISO`, `DevSecOps Engineer`, `Compliance Officer`).
    - Title + summary.
    - 2–3 focus areas.
    - 2 KPI highlights.
    - Primary CTA: "Open Dashboard" → `/devsecops/persona/:slug`.

- **Section C – Phases Overview**
  - `PhaseNav` row in a bordered card per `UX_UI_DESIGN_AUDIT.md` recommendations.
  - Below, grid of **Phase Overview cards** (already implemented in route dashboard) showing per‑phase metrics.
  - Each card clickable → `/devsecops/phase/:phaseId`.

- **Section D – Initiatives, Alerts & Activity**
  - Left column: "Initiatives & Alerts" list using `KPICard`/chips for critical items (blocked work, incidents, upcoming audits).
  - Right column: "Recent Activity" list powered by activity feed.

- **Section E – Canvas Entry**
  - Optional card at bottom: "Open DevSecOps Canvas" with description and CTA → new canvas route.

**Key Interactions**

- Changing active persona (via TopNav or persona cards) should influence:
  - KPI set (exec vs engineering vs compliance emphasis).
  - Default filters applied to `Phase` and `Canvas` views.
- Clicking KPIs opens SidePanel with detailed metrics + link to relevant report or canvas section.
- Responsive behavior:
  - On mobile: single column stacked layout; persona cards and phases become full‑width.

---

### 4.2 `/devsecops/phase/:phaseId` – Phase Workboard

**Layout**

- Breadcumbs: DevSecOps → [Phase name].
- Header: phase icon, name, description, summary stats (items by status, completion %) using `phaseStatisticsAtom` (after refactor to shared state).
- `PhaseNav` row for quick switching between phases.
- Toolbar:
  - `ViewModeSwitcher` (Kanban / Timeline / Table).  
  - `SearchBar` for text search.  
  - Compact filter chip(s) summarizing active filters; full `FilterPanel` visible on desktop and in drawer on mobile.
- Main content:
  - **Kanban view:** `KanbanBoard` with columns as statuses; DnD moves items (persisted via central state).  
  - **Timeline view:** `Timeline` showing items along sprint / release calendar, plus milestones.  
  - **Table view:** `DataTable` for sortable, filterable tabular view.
- SidePanel: item details when clicking a card or row:
  - Details tab: description, status, priority, assignees, dates, tags.
  - Integrations tab: GitHub/Jira/Sonar data (from `IntegrationManager` mocks initially).
  - Activity tab: audit trail.

**Key Interactions**

- **Status change via Kanban DnD** updates underlying `Item.status` and derived KPIs.
- Row/action menu includes shortcuts: change status, open in canvas, open in integrations.
- Filters follow `ItemFilter` shape; `FilterPanel` should map 1:1 to `ItemFilter` fields.

---

### 4.3 `/devsecops/persona/:slug` – Role‑Specific Dashboard

**Layout**

- Hero banner (already implemented) using gradient per design system.  
- KPI grid: 4–6 cards built with `KPICard`, using persona‑specific KPIs (from central persona config) instead of custom layout.
- Focus areas section (current bullet list) – may become `ItemCard`/`Chip` grid linked to saved filters (e.g., "Open Security Hotspots" filters phase board).
- Key insights section: cards with highlights and recommended actions.
- Primary CTA: persona‑appropriate action:
  - e.g., for CISO: "Open Executive Canvas View"; for DevSecOps Engineer: "View Pipeline Health"; for Compliance Officer: "Open Compliance Report".

**Key Interactions**

- Each focus area list item acts as a **filter preset** across DevSecOps:
  - Clicking "Regulatory compliance status" → `/devsecops/reports/security` with filters applied.
- KPI cards clickable to open deeper views (reports, phase board, canvas) via SidePanel or route navigation.

---

### 4.4 `/devsecops/reports` & `/devsecops/reports/:reportId`

**Reports Hub (`/devsecops/reports`)**

- Grid of report cards (Executive Summary, Release Readiness, Security & Compliance, Operational Health).  
- Each card uses design‑system card layout with short summary, tag chips (target personas), and CTA "View".
- Filter controls: time range presets (currently stubbed) that will feed into `ReportConfig`.

**Report Detail (`/devsecops/reports/:reportId`)** – new route

- Hero: report title, generation time, date range, tags (phases, personas).  
- KPI section: grid of KPIs, using `KPICard` in a 2–3 column layout per guidelines.
- Findings & risks: list of risk cards with severity chips.
- Recommended actions: list referencing phases & items.
- Actions: Download (PDF/CSV), share link, schedule recurring report.

**Key Interactions**

- Generate new report from hub or persona dash; new instance appears at top of recent list and is accessible under `/devsecops/reports/:reportId`.
- Time range & phase filters update all KPIs & charts for detail view.

---

### 4.5 `/devsecops/settings` – Settings & Governance

**Tabs (already present)**

- Roles & Access (Executive, DevOps, Developer, Security, etc.).
- Integrations (GitHub, Jira, SonarQube, Slack...).
- Automation (Webhooks, automation rules).
- Compliance (Data retention, audit trail).

**Planned Enhancements**

- Hook Integrations tab to `IntegrationManager` (even in mock) to display connection status, last sync time.
- Automation tab: show sample automation rules referencing DevSecOps items (e.g., "When critical vuln opened → create Jira issue + Slack alert").
- Compliance tab: connect to reports and persona dashboards (evidence exports, audit log links).

---

### 4.6 `/devsecops/templates` – Templates & Playbooks

**Layout**

- Header: context and persona‑driven explanation.
- Accordion list of templates (already implemented): Executive, MVP, Enterprise, Security‑First.  
- Each template panel shows KPIs, workflow tips, and **"Apply template"** CTA.

**Planned Integration**

- `Apply Template` should:
  - Call `loadTemplate(templateId)` in `useDevSecOpsCanvas`.
  - Navigate to `/devsecops/canvas?template=<id>`.

---

### 4.7 `/devsecops/canvas` – Unified Canvas Experience

**Layout**

- Full‑screen `DevSecOpsCanvas` component under `/devsecops` shell.
- Top bar: role selector, view mode (canvas/kanban/timeline/dashboard), template and create item actions.
- Central canvas: `PhaseCanvas` or aggregated multi‑phase view depending on mode.
- Right or bottom region: `KPIDashboard` for selected phase/persona.

**Key Interactions**

- Load template from Templates page or Canvas header.
- Create / update / delete items from canvas and have them **immediately reflected** in `/devsecops` dashboard and phase views via shared state.

---

## 5. Central Data & Mocking Strategy

### 5.1 Canonical Domain & API Shapes

- **Domain:** `libs/types/src/devsecops` remains the canonical domain model.
- **API Contracts:** define REST/HTTP endpoints in terms of these types, e.g.:
  - `GET /api/devsecops/overview` → `ApiResponse<{ phases: Phase[]; items: Item[]; kpis: KPI[]; activity: ActivityLog[]; personaDashboards: PersonaDashboardSummary[]; }>`.
  - `GET /api/devsecops/items` → `PaginatedResponse<Item>`.
  - `PATCH /api/devsecops/items/:id` → `ApiResponse<Item>`.
  - `GET /api/devsecops/reports` → `PaginatedResponse<ReportConfig>`.

### 5.2 Single Mocking Layer

**Target:**

- **MSW** remains the primary mocking tool for browser apps.
- `DevSecOpsClient` in `libs/api` should:
  - Use real `fetch` calls to `/api/devsecops/...` (no inline mock data).
  - The mocks are provided exclusively by **MSW handlers**, using canonical domain types and fixtures from shared modules.

**Plan:**

1. Extract current mock data from:
   - `apps/web/src/mocks/handlers.ts`.
   - `libs/ui/src/state/devsecops/mockDataService.ts`.
   - `DevSecOpsClient` inline mocks.
2. Consolidate into **shared fixture module(s)** under `libs/testing` or `libs/types/devsecops/fixtures` (to be chosen):
   - `devsecopsFixtures.phases`, `...items`, `...personaDashboards`, `...kpis`, etc.
3. Update:
   - MSW handler to use these fixtures and return **ApiResponse** shapes.
   - `DevSecOpsClient` to call `/api/devsecops` endpoints only.
   - `libs/ui/src/state/devsecops/atoms.ts` to depend on `DevSecOpsClient` instead of `mockBackend`.
   - `apps/web/src/state/devsecops.ts` to either:
     - be a thin wrapper around `libs/ui` atoms/hooks, or
     - be deprecated in favor of shared state module.

### 5.3 Jotai Store Consolidation

- **Preferred source:** `libs/ui/src/state/devsecops` as platform‑independent state.  
- **Strategy:**
  - Refactor route pages and canvas to use **shared hooks** such as `usePhases()`, `useFilteredItems()`, `usePhaseStatistics()`, etc., from `libs/ui` rather than duplicating state logic.
  - Where additional route‑specific atoms are needed (e.g., viewMode, filter drawer), keep them in `apps/web` but **compose** with shared atoms.

### 5.4 Real‑Time & Integrations (Mock‑First)

- `DevSecOpsWebSocket` remains a mocked event stream emitting domain‑level events (e.g., `item:updated`).
- Route pages and canvas can optionally subscribe to WebSocket events to update Jotai atoms for a richer demo.
- Integration adapters (`IntegrationManager`) should provide persona‑specific data slices for side panels and reports; for now, they stay mocked but typed.

---

## 6. Implementation Plan (Phased)

Each phase below assumes full type safety, design token usage, and reuse of existing modules per `copilot-instructions.md` and `DESIGN_SYSTEM_GUIDELINES.md`.

### Phase 1 – Data & State Unification (Foundations)

1. **Introduce shared DevSecOps fixtures** for phases, items, KPIs, persona dashboards, activity. _(Status: Completed)_  
2. **Refactor `DevSecOpsClient`** to use `fetch` against `/api/devsecops/...` and remove inline mocks. _(Status: Completed)_  
3. **Update MSW handlers** to use fixtures and return canonical domain types wrapped in `ApiResponse`. _(Status: Completed)_  
4. **Point `libs/ui` Jotai atoms** at `DevSecOpsClient` instead of `mockBackend`. _(Status: Completed)_  
5. **Refactor `apps/web/src/state/devsecops.ts`** to re‑export or compose shared DevSecOps state rather than duplicating it. _(Status: Completed)_

### Phase 2 – UX Polish & Layout Consistency

1. Apply `UX_UI_DESIGN_AUDIT.md` and `IMMEDIATE_IMPROVEMENTS.md` to: _(Status: Completed)_
   - `KPICard` typography and hover states updated to emphasize metric values and use theme-based hover styling.
   - Dashboard grid layouts implemented: KPI strip now uses a responsive 3–4 column grid with design-system spacing.
   - Phase overview grid and "Initiatives, Alerts & Activity" sections reorganized into card-based layouts for clearer grouping and consistent spacing.
2. Refine `/devsecops` route per Section 4.1. _(Status: Completed)_
   - DevSecOps shell now uses shared `TopNav` with Dashboard, Phases, Persona, Reports, Settings, and Canvas tabs wired to `/devsecops` routes with correct active-state mapping.
3. Align `DevSecOpsDashboard` (canvas demo) and `/devsecops` route dashboard visually (same token usage, typography scale, spacing) while retaining different entry stories. _(Status: Completed)_
   - Canvas demo KPI grid now uses the same responsive 1/2/3/4-column layout as the `/devsecops` route, and overall typography/spacing is aligned via shared components and tokens.

### Phase 3 – Persona, Phase, and Canvas Integration

1. **Wire missing routes** in `router/routes.tsx`: _(Status: Completed)_
   - `/devsecops/reports`, `/devsecops/settings`, `/devsecops/templates`, `/devsecops/canvas`.
2. **Persona dashboards**: _(Status: Completed)_
   - Persona dashboards now use canonical `personaDashboards` config and KPIs from `libs/types/src/devsecops/fixtures.ts`, hydrated via the shared DevSecOps store.  
   - All personas (CISO, DevSecOps Engineer, Compliance Officer) implement focus‑area → filter preset interactions using shared `useFilterManagement`, with the DevSecOps Engineer persona also setting the active phase before navigating to the phase board.
3. **Phase view**: _(Status: Completed)_
   - Route phase view uses shared `Item` type and Jotai hooks (`@yappc/store/devsecops`) for phases, milestones, view mode, filters, and side panel.  
   - Kanban DnD status changes are wired to the shared `useMoveItem` mutation, updating the central store.  
   - The phase route now simulates WebSocket‑driven updates by subscribing to a mock real‑time event stream (using `useUpdateItem` under the hood) that periodically patches item status, giving the board a live‑updating demo experience.
4. **Canvas**: _(Status: Completed)_
   - `/devsecops/canvas` mounts `DevSecOpsCanvas` with a stable demo `projectId` and accepts a `template` query param that is passed as `initialTemplateId` into `useDevSecOpsCanvas`.  
   - `Apply Template` in `/devsecops/templates` and the CISO persona primary action now navigate to `/devsecops/canvas?template=...`, causing the corresponding canvas template to auto-load on first visit.

### Phase 4 – Reports & Governance

1. Implement `/devsecops/reports/:reportId` detail page using shared `KPI`, `ReportConfig`, and `ExecutiveReport` types. _(Status: Completed)_
   - `/devsecops/reports/:reportId` now fetches report data via `devsecopsClient.getReport`, renders a KPI grid using `KPICard`, and displays findings & risks in card-based sections, matching the Phase 4.1 layout spec.
2. Integrate `IntegrationManager` into **Settings → Integrations** and SidePanel "Integrations" tab. _(Status: Completed)_
   - Settings → Integrations now calls the shared `integrationManager.getIntegrationStatuses()` mock and surfaces GitHub, Jira, and SonarQube connection states with appropriate Configure/Connect/Disconnect actions.
3. Enhance **Settings → Compliance** to show compliance KPIs (from `SECURITY_KPIS`) and shortcuts to relevant reports. _(Status: Completed)_
   - Settings → Compliance now highlights key security/compliance KPIs using `KPICard` and includes direct shortcuts into the Security & Compliance and Executive Summary reports.

### Phase 5 – Desktop Shell & Final Demo Polish

1. Wrap `/devsecops` and `/devsecops/canvas` flows into desktop shell (`apps/desktop`) using same shared components and state. _(Status: Completed)_
   - `apps/desktop` is a Tauri 2 app configured via `src-tauri/tauri.conf.json` to run the existing web app (`apps/web`) both in dev (`beforeDevCommand`, `devPath`) and in production (`beforeBuildCommand`, `distDir`), so the DevSecOps dashboard and canvas routes run unchanged inside the desktop shell.
2. Introduce desktop‑specific chrome (window frame, offline/online indicator) without diverging layout. _(Status: Completed)_
   - The OS-native Tauri window frame is used as-is, and `DevSecOpsLayout` now shows a subtle Tauri-only "Desktop Shell · Online/Offline" status bar below the DevSecOps TopNav, driven by `window.__TAURI__` detection and `navigator.onLine` events.
3. Finalize Storybook stories for **all DevSecOps components** and demo scenarios. _(Status: Completed)_
   - `Routes/DevSecOps` stories now render the real route components (dashboard, phases, persona, reports, settings, templates, canvas, detail pages) inside `DevSecOpsLayout` using `MemoryRouter`, plus a dedicated Canvas story.
4. Ensure e2e tests under `e2e/devsecops-*.spec.ts` cover the main persona journeys. _(Status: Completed)_
   - Dashboard, phase detail, secondary pages (reports, settings, templates), persona dashboards, and canvas template flows are covered by `devsecops-dashboard.spec.ts`, `devsecops-phase-detail.spec.ts`, `devsecops-secondary-pages.spec.ts`, and `devsecops-persona-and-canvas.spec.ts`.

---

## 7. Demo Scenarios & Validation Checklist

**Demo Scenarios**

1. **Executive Risk Posture**  
   - Open `/devsecops` as executive.  
   - Scan KPIs & alerts; click CISO persona card → persona dashboard.  
   - Generate security report → view `/devsecops/reports/security`.

2. **DevSecOps Engineer Daily Board**  
   - Open `/devsecops/phase/development`.  
   - Switch to Kanban; drag blocked items; change filters; open SidePanel and view integrations.

3. **Compliance Audit Preparation**  
   - Open compliance persona dashboard → jump to `/devsecops/settings` → Compliance tab.  
   - Inspect compliance KPIs and open relevant reports.

4. **Strategic Planning in Canvas**  
   - From `/devsecops`, open Canvas.  
   - Load Executive Dashboard template; see pre‑placed items and KPIs.  
   - Modify items, generate an executive report, and show how it appears in `/devsecops/reports`.

**Validation Checklist**

- Shared domain types (`libs/types/devsecops`) used everywhere (no ad‑hoc types).  
- Single data & mocking path: `DevSecOpsClient` → `/api/devsecops` → MSW handlers → fixtures.  
- Jotai state consolidated in `libs/ui/src/state/devsecops` and reused by routes & canvas.  
- Design tokens from `devsecops-tokens.css` used for colors, spacing, typography.  
- All DevSecOps components have accessible keyboard navigation, ARIA where needed, and pass WCAG AA contrast.  
- Storybook documents each DevSecOps component and the main routes under `Routes/DevSecOps`, including a Canvas story for strategic planning demos.  
- E2E tests under `e2e/devsecops-*.spec.ts` cover the primary persona journeys, reports, settings, templates, and canvas flows.

---

*This document is the single source of truth for the DevSecOps UX design system and implementation plan in the `app-creator` project. All future DevSecOps UI/UX work should reference and update this file as part of the change process.*
