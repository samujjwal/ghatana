# App Creator – Page & Flow Specs

This document summarizes the **App Creator** web app (`products/yappc/app-creator/apps/web`) based on `src/routes.ts`. For each page we note:

- **Intention** – what the page is for (plain language).
- **Content** – what is shown.
- **Why it matters** – why this is useful in an app-creation / design‑to‑code flow.
- **Key actions** – what users can do.
- **Gaps & enhancements** – opportunities to improve correctness, completeness, or usability.

---

## 0. Global Shell & Root

### 0.1 `App.tsx` – Global Providers & Router

- **Intention:** Wrap the app with GraphQL + WebSocket providers and mount the main router.
- **Content:** `GraphQLProvider`, `WebSocketProvider`, `RouterProvider` with fallback "Loading application...".
- **Why it matters:** Ensures every page can fetch data and (optionally) stream live updates.
- **Key actions:** Infrastructure only.
- **Gaps & enhancements:** Surface WebSocket/GraphQL connection state somewhere in the UI.

### 0.2 `RootLayout.tsx` – Desktop Layout

- **Intention:** Provide top app bar + sidebar + content area for all `/` children.
- **Content:** App bar (title "YAPPC App Creator", theme toggle), MUI-based sidebar via `SidebarContent`, `Outlet` for child routes.
- **Why it matters:** Gives a consistent "home base" across workspaces, canvas demos, DevSecOps, and test utilities.
- **Key actions:** Toggle theme, open/close sidebar, navigate via sidebar.
- **Gaps & enhancements:** Clarify which nav items are primary (workspaces/projects) vs advanced (canvas demos, DevSecOps, Page Builder).

---

## 1. Core App Creator Area – `/app` Shell

### 1.1 `routes/app/_shell.tsx` – App Shell

- **Intention:** Provide a simple shell for `/app/*` with sidebar nav and top bar.
- **Content:** Sidebar with `🏠 Workspaces` → `/app/workspaces`, `📁 Projects` → `/app/projects`; top bar with path, theme toggle, Settings button (placeholder), user avatar.
- **Why it matters:** Separates the app‑creator workspace/project flows from other top‑level experiences.
- **Key actions:** Navigate to workspaces/projects, toggle theme.
- **Gaps & enhancements:** Replace raw pathname with human‑readable context; wire Settings; consider showing active workspace/project in the header.

### 1.2 `/app/workspaces` – Workspace List (`routes/app/workspaces.tsx`)

- **Intention:** Let users pick a workspace as the starting context for projects.
- **Content:** `Workspaces` header + subtitle; grid of cards (e.g., Personal Projects, Team Alpha) with project count and last activity; `+ New Workspace` button (visual only).
- **Why it matters:** Workspaces map to how teams think about work (personal vs team areas) and scope projects/canvases.
- **Key actions:** Click a workspace to open `/app/w/:workspaceId/projects`.
- **Gaps & enhancements:** Replace mocks with real data; implement workspace creation; add search/filter for large orgs.

### 1.3 `/app/projects` – Projects Index Redirect (`routes/app/projects.index.tsx`)

- **Intention:** Redirect generic `/app/projects` to a concrete workspace’s projects.
- **Content:** `Redirecting to projects...` placeholder, `useEffect` that navigates to `/app/w/ws-1/projects`.
- **Why it matters:** Keeps older links/tests valid while nudging users into workspace‑scoped URLs.
- **Key actions:** N/A (auto‑redirect).
- **Gaps & enhancements:** Choose workspace dynamically (e.g., last used) rather than hard‑coded `ws-1`.

### 1.4 `/app/w/:workspaceId/projects` – Workspace Projects (`routes/app/projects.tsx`)

- **Intention:** List all projects in a workspace with quick health overview.
- **Content:** Loader with optional E2E error simulation; cards like "E‑commerce Platform", "Component Library", each showing description and build/test/deploy health; ErrorBoundary with friendly copy.
- **Why it matters:** Main **project switcher** for the app‑creator experience.
- **Key actions:** Click a project card to open `/app/w/:workspaceId/p/:projectId/overview`.
- **Gaps & enhancements:** Real GraphQL data; filters (status, owner, activity); a clear `+ New Project` CTA wired to `/app/projects/new` with workspace preselected.

### 1.5 `/app/projects/new` – New Project (`routes/app/projects.new.tsx`)

- **Intention:** Create a new project with name/description/template.
- **Content:** Form with `Project Name`, `Project Description`, `Template` select (React/Node) and detailed validation errors for name.
- **Why it matters:** Entry point into the whole project/canvas/pipeline flow.
- **Key actions:** Fill form, see inline validation, submit to create (currently stubbed) and navigate.
- **Gaps & enhancements:** Wire to a real creation mutation; show where the new project will appear (workspace context); preview what each template configures (canvas + pipelines).

### 1.6 `routes/app/project/_shell.tsx` – Project Shell & Tabs

- **Intention:** Provide a consistent project header and tabbed navigation for project‑level routes.
- **Content:** Back link to workspace projects; project avatar, name, ID; status bar (Status, Last Deploy, health icons); tab strip for **Overview, Implement (canvas), Backlog, Design, Build, Test, Deploy, Monitor, Versions, Settings**.
- **Why it matters:** This is the **spine** of the project lifecycle, tying together planning, design, delivery, and operations.
- **Key actions:** Switch between tabs; go back to workspace projects.
- **Gaps & enhancements:** Load project name from GraphQL; surface key metrics (open incidents, failing tests) directly in the header.

---

## 2. Project Tabs (Per‑Project Views)

### 2.1 Overview – `/app/w/:workspaceId/p/:projectId/overview` (`overview-RENAMED.tsx`)

- **Intention:** Serve as the project dashboard: health, key metrics, and quick links.
- **Content:** Currently dominated by a debug block proving the file loads; the rest of the file (post‑return) defines KPIs, recent activity, and metadata panels.
- **Why it matters:** Should be the first screen that answers "Is this project healthy? What should I do next?".
- **Key actions:** (Intended) Review KPIs & activity; jump to deeper tabs.
- **Gaps & enhancements:** Remove debug UI; wire to real metrics (build pass rate, deployment frequency, errors); add clear CTAs ("Open Canvas", "View latest build", "Open backlog").

### 2.2 Implement (Canvas) – `/.../canvas` & `canvas-new` (`CanvasRoute` + `CanvasScene`)

- **Intention:** Offer a **design‑to‑code canvas** for composing components, pages, and flows.
- **Content:** React Flow canvas with nodes/edges, background grid, mini‑map; **ComponentPalette**, **HistoryToolbar**, **CommentsPanel**, node property editors; node/edge counts.
- **Why it matters:** Core of App Creator: visually design apps that connect to backlog items, pipelines, and monitoring.
- **Key actions:** Add/move/connect nodes, edit properties, use comments, undo/redo, fit view.
- **Gaps & enhancements:** Add explicit "Generate code" / "Open generated page" actions; link nodes to backlog tasks and DevSecOps items; improve layman‑friendly labels/tooltips for node types.

### 2.3 Backlog – `/.../backlog` (`backlog.tsx`)

- **Intention:** Show and manage tasks for the project.
- **Content:** Columns for TODO, IN_PROGRESS, DONE, BLOCKED using mock tasks filtered by `projectId`; colored status chips; error boundary.
- **Why it matters:** Connects planning work to implementation and operations.
- **Key actions:** Scan tasks by status (future: drag/drop, open details).
- **Gaps & enhancements:** Replace mocks with real backlog; enable drag‑and‑drop; link tasks to canvas nodes and build/test items.

### 2.4 Design – `/.../design` (`design.tsx`)

- **Intention:** Provide design system, component library, and API contract views for the project.
- **Content:** GraphQL‑backed project context; loading/error states; cards for tokens, components, API docs.
- **Why it matters:** Aligns canvas and code with a shared design system.
- **Key actions:** Inspect components/contracts; open external docs.
- **Gaps & enhancements:** Show which canvas nodes use which components; flag mismatches between design system and implementation.

### 2.5 Build – `/.../build` (`build.tsx`)

- **Intention:** Offer a CI/CD build dashboard with real‑time updates and bulk operations.
- **Content:** GraphQL project data; WebSocket build stream; `DataTable` with builds; `BulkActionBar` for multi‑selection; artifacts and quality‑gate sections.
- **Why it matters:** Connects code produced from the canvas to automated verification.
- **Key actions:** Monitor builds; select builds; run bulk actions (e.g., re‑run, cancel); inspect artifacts.
- **Gaps & enhancements:** Align bulk actions with concrete workflows; surface summary KPIs and link to DevSecOps reports.

### 2.6 Test – `/.../test` (`test.tsx` – placeholder)

- **Intention:** Reserve a dedicated test pipeline/reporting area.
- **Content:** `PlaceholderRoute` with icon 🧪 and explanatory text.
- **Gaps & enhancements:** Implement real test runs, status filters, and links from DevSecOps failures.

### 2.7 Deploy – `/.../deploy` (`deploy.tsx`)

- **Intention:** Manage deployments across environments with canary/rollback and bulk actions.
- **Content:** WebSocket data; `SelectableTable` of deployments; `BulkActionBar`; environment/target columns.
- **Why it matters:** Operationalizes changes from canvas and pipelines.
- **Key actions:** Select deployments; trigger actions (rollback, promote, cancel); inspect deployment history.
- **Gaps & enhancements:** Make environment labels explicit (Dev/Staging/Prod); correlate deployments with Monitor alerts.

### 2.8 Monitor – `/.../monitor` (`monitor.tsx`)

- **Intention:** Provide a project‑level observability/search console.
- **Content:** WebSocket stream; `SearchProvider`, `SearchInterface`, `SearchResults` over mock alerts/errors.
- **Why it matters:** Gives feedback loop from production back into design/build decisions.
- **Key actions:** Search/filter events; inspect alerts.
- **Gaps & enhancements:** Show key KPIs (error rate, latency); deep‑link from events to deployments and canvas nodes.

### 2.9 Versions – `/.../versions` (`versions.tsx`)

- **Intention:** Manage snapshots/versions of the project.
- **Content:** Cards for snapshots; actions: compare, restore, export, delete, edit; detail view for selected snapshot.
- **Why it matters:** Enables safe experimentation and historical analysis.
- **Key actions:** Create/select snapshots; compare; restore; export.
- **Gaps & enhancements:** Clarify relationships to Git commits and DevSecOps items; map snapshots to canvas + pipeline state.

### 2.10 Settings – `/.../settings` (`settings.tsx`)

- **Intention:** Configure project‑level settings (access, environment, integrations).
- **Content:** Rich settings UI with multiple cards/tables; success toast for E2E when saving.
- **Why it matters:** Centralizes configuration that affects builds, deploys, and security.
- **Key actions:** Edit settings; save; manage collaborators and tokens (where implemented).
- **Gaps & enhancements:** Align with DevSecOps Settings & Governance; surface a concise settings summary in the project header.

---

## 3. Canvas Demos & Onboarding

### 3.1 Canvas Aliases & Demos (`/diagram`, `/canvas`, `/canvas/new`, `/app/project/canvas-new`, `/canvas-poc`, `/canvas-test`, `/canvas/demo`)

- **Intention:** Provide multiple entry points and test harnesses for canvas behavior (production, PoC, test suite, comprehensive demo).
- **Content:** All route variants ultimately mount `CanvasRoute` or specialized demo/test canvases.
- **Why it matters:** Supports experimentation and E2E testing without impacting real projects.
- **Gaps & enhancements:** Provide a human‑facing "Canvas Playground" explaining which route to use for what; gate non‑user routes behind flags.

### 3.2 `/canvas/onboarding` – Canvas Onboarding

- **Intention:** Guide new users with an onboarding checklist for the canvas.
- **Content:** Centered container that lazy‑loads `OnboardingChecklist` with spinner fallback.
- **Gaps & enhancements:** Ensure steps cover the full design‑to‑code loop (design → link tasks → run build → monitor) and track per‑user completion.

---

## 4. DevSecOps Area – `/devsecops/*`

High‑level summary (details are in the route files):

- **Dashboard (`index.tsx`):** KPIs, phases, recent activity, CTA to DevSecOps canvas.  
  _Gap:_ Link metrics clearly back to App Creator projects.
- **Phase page (`phase/$phaseId.tsx`):** Kanban/Timeline/Table views of items in a phase, with side panel.  
  _Gap:_ Make item→project/canvas linkage explicit.
- **Persona dashboard (`persona/$slug.tsx`):** Role‑specific KPIs and insights, plus focus‑area links.  
  _Gap:_ Show which concrete projects each persona view is summarizing.
- **DevSecOps canvas (`canvas.tsx`):** Visual canvas for a demo DevSecOps project.  
  _Gap:_ Show relationship to real App Creator projects.
- **Reports (`reports.tsx`, `reports/$reportId.tsx`):** Report catalog + detailed KPI/risk view with export buttons.  
  _Gap:_ Drill‑down from reports to specific pipelines/projects.
- **Settings & Governance (`settings.tsx`):** Integrations (GitHub/Jira/SonarQube) and policy settings with KPIs.  
  _Gap:_ Ensure changes are reflected in project pipelines/settings.
- **Templates (`templates.tsx`):** Persona‑oriented DevSecOps templates with recommended KPIs and workflow tips.  
  _Gap:_ Clarify how applying a template reshapes boards/canvases.
- **Item detail (`item/$itemId.tsx`):** Single DevSecOps item with actions (Edit, View Implementation Plan, Open in GitHub).  
  _Gap:_ Connect item to specific App Creator project and nodes.
- **Diagram viewer (`diagram/$diagramId.tsx`):** Architecture/infra diagrams with annotations.  
  _Gap:_ Integrate with canvases so diagrams reflect live system structure.

---

## 5. Mobile Shell & Views – `/mobile/*`

- **Mobile shell (`mobile/_shell.tsx`):** Mobile layout with bottom nav (Dashboard, Projects, Alerts), header, and simple drawer for quick links.  
  _Gap:_ Align terminology and routes with desktop equivalents.
- **Mobile projects (`mobile/projects.tsx`):** Touch‑first project list with search, filters, favorites, offline awareness (Capacitor), and snackbars for errors.  
  _Gap:_ Ensure project identities and statuses stay in sync with `/app` projects.
- **Mobile overview (`mobile/overview.tsx`):** Mobile dashboard with KPIs, activity, and drawers for more detail.  
  _Gap:_ Explicitly tie metrics to specific projects/pipelines.
- **Mobile backlog & notifications (`mobile/backlog.tsx`, `mobile/notifications.tsx`):** Placeholder routes indicating future mobile‑optimized backlog and notifications flows.

---

## 6. Login & Page Builder

- **Login (`routes/login.tsx`):** Simple login form with username/password, inline error alert, and `Continue as Demo User` button to `/app/workspaces`.  
  _Gap:_ Hook into real auth/session management and show where the user will land (workspace/project context).
- **Page Builder Manual Test (`routes/PageBuilderManualTest.lazy.tsx`):** Lazy‑loaded manual test surface for the Page Builder, with custom error boundary and Chrome‑extension‑aware loader.  
  _Gap:_ Decide whether to expose a user‑facing Page Builder entry point or keep this as a test‑only tool.
