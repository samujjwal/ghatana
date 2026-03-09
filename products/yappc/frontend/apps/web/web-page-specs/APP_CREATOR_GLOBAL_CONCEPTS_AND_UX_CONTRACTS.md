# App Creator – Global Concepts & UX Contracts

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Comprehensive revision for implementation alignment

This document centralizes **cross-cutting concepts and UX contracts** for the App Creator web app so that all page specs and implementations stay aligned.

It plays the same role for App Creator that `WEB_GLOBAL_CONCEPTS_AND_UX_CONTRACTS.md` does for the Software Org app.

---

## 1. Navigation & Shell Layers

App Creator has **four primary shells**:

### 1.1 Root Layout (Global Frame)
**File:** `routes/_root.tsx`

- Provides the **global technical frame** for the entire application
- Owns: E2E testing helpers, accessibility landmarks, skip-to-content link
- Wraps all routes in `ShortcutProvider` and error boundary
- **Note:** This is NOT the same as the old `RootLayout.tsx` mentioned in previous specs – the current implementation uses `_root.tsx`

### 1.2 `/app` Shell (Workspace/Project Flows)
**File:** `routes/app/_shell.tsx`

- Owns the left nav for **Workspaces** and **Projects**, plus a simple top bar
- Hosts all authenticated App Creator flows under `/app/*`:
  - Workspaces list (`/app/workspaces`)
  - Workspace projects (`/app/w/:workspaceId/projects`)
  - Project shell + tabs (`/app/w/:workspaceId/p/:projectId/*`)
- Features:
  - Session timeout check (E2E support via `localStorage.E2E_SESSION_EXPIRED`)
  - Theme toggle button (`data-testid="theme-toggle"`)
  - Active nav styling with right border indicator
  - User avatar badge

### 1.3 DevSecOps Shell
**File:** `routes/devsecops/_layout.tsx`

- Completely separate shell with its own `TopNav` component from `@yappc/ui`
- Manages its own state via Jotai atoms (`itemsAtom`, `phasesAtom`, etc.)
- Initializes mock data on mount
- Features:
  - User context display (hardcoded: "John Doe, Executive")
  - Tauri desktop shell detection with online/offline indicator
  - Navigation handler for all DevSecOps sub-routes
  - Error boundary with "Back to Dashboard" recovery

### 1.4 Mobile Shell
**File:** `routes/mobile/_shell.tsx`

- Owns mobile header and bottom navigation (Dashboard, Projects, Alerts)
- Hosts all `/mobile/*` routes
- Capacitor-ready with native plugin integration

**Contracts:**

- All **workspace/project** functionality must live under `/app/*` within the `/app` shell
- All **DevSecOps** functionality must live under `/devsecops/*` within the DevSecOps shell
- All **mobile** views must live under `/mobile/*` within the mobile shell
- Experimental and DevSecOps routes should remain discoverable but clearly separated from primary `/app` flows in nav and copy
- **The root route (`/`) redirects to `/devsecops` by default** (current implementation)

---

## 2. Core Entities & URLs

### 2.1 Key Entities

- **Workspace** – logical grouping of projects
- **Project** – the main unit of work in App Creator (has canvas, pipelines, settings, etc.)
- **DevSecOps Item** – risk, task, or check in DevSecOps flows
- **Phase** – a stage in the DevSecOps lifecycle (Plan, Code, Build, Test, Deploy, Run)
- **Persona** – a role-based dashboard view (Security Lead, SRE, Engineering Manager)
- **Workflow** – automated or semi-automated process templates

### 2.2 Canonical URL Patterns

**App Creator (`/app`)**
| Route Pattern | Purpose |
|--------------|---------|
| `/app/workspaces` | Workspace list |
| `/app/w/:workspaceId/projects` | Projects within a workspace |
| `/app/w/:workspaceId/p/:projectId/overview` | Project overview dashboard |
| `/app/w/:workspaceId/p/:projectId/canvas` | Visual design canvas (Implement) |
| `/app/w/:workspaceId/p/:projectId/backlog` | Kanban task board |
| `/app/w/:workspaceId/p/:projectId/design` | Design system & tokens |
| `/app/w/:workspaceId/p/:projectId/build` | CI/CD build pipeline |
| `/app/w/:workspaceId/p/:projectId/test` | Test hub (placeholder) |
| `/app/w/:workspaceId/p/:projectId/deploy` | Deployment pipeline |
| `/app/w/:workspaceId/p/:projectId/monitor` | Observability console |
| `/app/w/:workspaceId/p/:projectId/versions` | Snapshots & history |
| `/app/w/:workspaceId/p/:projectId/settings` | Project configuration |
| `/app/projects` | Legacy redirect to workspace projects |
| `/app/projects/new` | New project creation form |

**DevSecOps (`/devsecops`)**
| Route Pattern | Purpose |
|--------------|---------|
| `/devsecops` | Main DevSecOps dashboard (index) |
| `/devsecops/phase/:phaseId` | Phase board (Kanban/Timeline/Table) |
| `/devsecops/persona/:slug` | Persona-specific dashboard |
| `/devsecops/canvas` | Visual DevSecOps workflow modeling |
| `/devsecops/domains` | Domain management |
| `/devsecops/domains/:domainId` | Domain detail |
| `/devsecops/phases` | Phases list view |
| `/devsecops/phases/:phaseId` | Phase detail |
| `/devsecops/workflows` | Workflow management |
| `/devsecops/workflows/:workflowId` | Workflow detail |
| `/devsecops/tasks` | Tasks list |
| `/devsecops/task/:taskId` | Task detail |
| `/devsecops/team-board` | Team-oriented task view |
| `/devsecops/task-board` | Task board view |
| `/devsecops/settings` | Settings & governance |
| `/devsecops/admin` | Admin console |
| `/devsecops/operations` | Operations dashboard |
| `/devsecops/reports` | Reports hub |
| `/devsecops/reports/:reportId` | Report detail |
| `/devsecops/templates` | Templates & playbooks |
| `/devsecops/item/:itemId` | Item detail view |
| `/devsecops/diagram/:diagramId` | Diagram viewer |

**Workflows (`/workflows`)**
| Route Pattern | Purpose |
|--------------|---------|
| `/workflows` | Workflow list (index) |
| `/workflows/new` | New workflow creation |
| `/workflows/:workflowId` | Workflow detail/editor |

**Mobile (`/mobile`)**
| Route Pattern | Purpose |
|--------------|---------|
| `/mobile/projects` | Project list for mobile |
| `/mobile/overview` | Dashboard overview |
| `/mobile/p/:projectId/overview` | Project-specific overview |
| `/mobile/p/:projectId/backlog` | Project backlog |
| `/mobile/notifications` | Notifications/alerts |

**Other Top-Level Routes**
| Route Pattern | Purpose |
|--------------|---------|
| `/` | Root (redirects to `/devsecops`) |
| `/canvas` | Standalone canvas (primary) |
| `/page-designer` | Page designer route |
| `/login` | Authentication |

**Contracts:**

- New project-level pages must use the project URL pattern and plug into the project shell tabs
- New DevSecOps pages must live under `/devsecops/*` and use DevSecOps entity IDs
- Route IDs should be descriptive and follow the pattern `area-feature` (e.g., `devsecops-phase`, `project-canvas`)

---

## 3. Status, Severity, and Priority Semantics

To keep urgency and health signals consistent:

### 3.1 Build/Deploy/Monitor Status

Common statuses:

- **Succeeded / Healthy** – operation completed successfully.
- **Running / In progress** – currently executing.
- **Queued** – waiting to start.
- **Failed** – completed with errors.
- **Canceled / Rolled back** – explicitly stopped or reverted.

**Contract:**

- Build, Deploy, Monitor, and DevSecOps reports should reuse this family of statuses and colors.

### 3.2 Severity & Priority

Used across Monitor, DevSecOps, and related views:

- **Severity:** `Critical`, `High`, `Medium`, `Low`, `Info`.
- **Priority:** `P0`, `P1`, `P2`, `P3` (when shown).

**Contract:**

- Severity and priority names must be consistent across:
  - Project Monitor tab.
  - DevSecOps dashboard, phase board, and item detail.
  - Any future alert/notifications UIs (including mobile).

---

## 4. Page Header Pattern

For desktop `/app` and DevSecOps pages:

- **H1:** Clear page title in plain language (e.g., "Builds", "Deployments", "Monitor").
- **Subtitle:** One-line explanation of what the page helps the user do.
- **Optional CTAs:** Primary/secondary actions (e.g., "Open Canvas", "New Snapshot").

For project tabs, headers are visually combined with the project shell:

- Project shell gives **workspace/project context + status bar**.
- Each tab contributes **its own title/subtitle** for the inner page.

**Contract:**

- All new pages and tabs must follow this header + subtitle pattern.
- Subtitles must be readable by non-experts; avoid internal jargon.

---

## 5. Cross-Page Relationships

App Creator is strongest when pages are **linked**, not isolated:

- **Overview → Tabs:**
  - Overview dashboard should link into Canvas, Build, Deploy, Monitor, Backlog.

- **Canvas → Backlog / DevSecOps:**
  - Canvas nodes should be linkable to backlog tasks and DevSecOps items.

- **Build ↔ Deploy ↔ Monitor:**
  - Builds feed Deployments; Deployments show up in Monitor and DevSecOps reports.

- **Versions ↔ Everything:**
  - Snapshots should represent a coherent state across canvas, pipelines, and settings.

**Contract:**

- Where a spec mentions another page, it should do so using these relationships and standard route patterns, not ad‑hoc links.

---

## 6. Error, Loading, and Placeholder Behavior

- **Global loading:**
  - `App.tsx` uses a centered "Loading application..." state while routes are prepared.

- **Route-level loading & error states:**
  - Feature pages should show simple, friendly loading and error messages.

- **Placeholder routes:**
  - Use `PlaceholderRoute` to clearly indicate future areas (Test tab, some mobile pages) without pretending they are implemented.

**Contract:**

- Do not silently fail or leave blank pages; always show an intentional loading or error surface.
- Placeholder pages must:
  - State that the feature is not yet implemented.
  - Briefly describe the intended future behavior.

---

## 7. Desktop vs Mobile UX Contracts

- Desktop experiences live under `_root.tsx` and `/app` shell
- Mobile experiences live under `/mobile/*` and are:
  - **Information-dense but interaction-light**
  - Focused on quick checks and simple actions
  - Capacitor-native with offline support

**Contract:**

- Mobile must reuse the same entity names, statuses, and metrics as desktop
- Mobile should not invent new status names or semantics

---

## 8. Documentation & Spec Alignment

- `APP_CREATOR_PAGE_SPECS.md` holds the **inventory + light summary** of each page
- `web-page-specs/*.md` holds **deep-dive specs** per page/area
- This global doc holds **shared contracts**

**Contract:**

- When updating behavior that affects many pages (e.g., status semantics, navigation), update **this global doc first**, then adjust individual page specs as needed

---

## 9. State Management Architecture

### 9.1 Technology Stack

| Concern | Technology | Usage |
|---------|------------|-------|
| **Local App State** | Jotai | DevSecOps atoms, UI state, global preferences |
| **Server State** | TanStack Query + GraphQL | Project data, builds, deployments |
| **Form State** | React Hook Form / local state | Project creation, settings forms |
| **Global Store** | `@yappc/store` | Shared DevSecOps state across components |

### 9.2 Key State Atoms (DevSecOps)

Located in `state/devsecops.ts`:
- `itemsAtom` – DevSecOps work items
- `phasesAtom` – Lifecycle phases
- `milestonesAtom` – Project milestones
- `activityAtom` – Recent activity feed
- `personaDashboardsAtom` – Persona-specific dashboards

**Contract:**

- Use Jotai for UI and DevSecOps state
- Use TanStack Query / GraphQL for project/workspace data
- Never mix state management approaches within a single feature

---

## 10. 🔁 REUSE-FIRST Principles (CRITICAL)

> **Golden Rule:** Check existing components and hooks BEFORE writing new code.

### 10.1 Component Reuse Hierarchy

**Check in this order:**

1. **`@yappc/ui`** - Primary UI components
2. **`@yappc/ui/components/DevSecOps`** - DevSecOps-specific components  
3. **`@yappc/store`** - Shared hooks and state
4. **Existing route components** - e.g., `routes/devsecops/components/`

### 10.2 Available Components (USE THESE)

**From `@yappc/ui/components/DevSecOps`:**

| Component | Purpose | DO NOT duplicate |
|-----------|---------|------------------|
| `KPICard` | Metrics display | ❌ No local KPI cards |
| `TopNav` | Navigation bar | ❌ No custom nav bars |
| `DataTable` | Sortable tables | ❌ No custom tables |
| `KanbanBoard` | Kanban view | ❌ No custom boards |
| `Timeline` | Timeline view | ❌ No custom timelines |
| `ItemCard` | Item display | ❌ No custom item cards |
| `SearchBar` | Search input | ❌ No custom search |
| `FilterPanel` | Filter UI | ❌ No custom filters |
| `SidePanel` | Side overlay | ❌ No custom panels |
| `Breadcrumbs` | Navigation trail | ❌ No custom breadcrumbs |

**From `@yappc/store`:**

| Hook | Purpose | DO NOT recreate |
|------|---------|-----------------|
| `useKpiStats()` | KPI metrics | ❌ No local KPI calc |
| `usePhases()` | Get phases | ❌ No local phase state |
| `useItems()` | Get items | ❌ No local items state |
| `useActivity()` | Activity feed | ❌ No local activity |
| `usePersonaDashboards()` | Persona config | ❌ No local dashboards |

### 10.3 Anti-Patterns (FORBIDDEN)

```tsx
// ❌ WRONG: Creating local component that exists in @yappc/ui
const MyKPICard = ({ title, value }) => (
  <Card>...</Card>
);

// ✅ CORRECT: Import from @yappc/ui
import { KPICard } from '@yappc/ui/components/DevSecOps';
<KPICard title={title} value={value} />

// ❌ WRONG: Importing MUI directly
import { Box } from '@mui/material';

// ✅ CORRECT: Import from @yappc/ui (which wraps MUI)
import { Box } from '@yappc/ui';

// ❌ WRONG: Creating local state for shared data
const [kpis, setKpis] = useState([]);
useEffect(() => { fetchKpis()... }, []);

// ✅ CORRECT: Use shared hook
import { useKpiStats } from '@yappc/store';
const kpis = useKpiStats();
```

### 10.4 Before Creating New Code - Checklist

- [ ] Does this component exist in `@yappc/ui`?
- [ ] Does this hook exist in `@yappc/store`?
- [ ] Is there a similar component in another route I can extract?
- [ ] If I need to create new, should it go in `@yappc/ui` or be local?

**Reference:** See [SIMPLIFICATION_AND_REUSE_PLAN.md](./SIMPLIFICATION_AND_REUSE_PLAN.md) for detailed consolidation plan.

---

## 11. 🎯 Simplified User Journey

### The 6-Step Flow: Idea → Operation

Every user task should follow this dead-simple flow:

```
PLAN → DESIGN → CODE → TEST → DEPLOY → MONITOR
```

| Step | Tab | Primary Action | One Click? |
|------|-----|----------------|------------|
| **Plan** | Backlog | Add task | ✅ Yes |
| **Design** | Canvas | Draw flow | ✅ Drag-drop |
| **Code** | Build | Run pipeline | ✅ Yes |
| **Test** | Test | View results | ✅ Auto |
| **Deploy** | Deploy | Promote env | ✅ Yes |
| **Monitor** | Monitor | Check health | ✅ Auto |

### Simplification Principles

1. **One-Click Actions** - Primary action is always ONE click
2. **No Dead Ends** - Every page shows "what's next"
3. **Progressive Disclosure** - Hide complexity until needed
4. **Consistent Patterns** - Same component = same behavior

### Quick Actions Pattern

Every page should include contextual quick actions:

```tsx
<QuickActionBar>
  <QuickAction icon={Add} label="New Task" primary />
  <QuickAction icon={Play} label="Run Build" />
  <QuickAction icon={Rocket} label="Deploy" />
</QuickActionBar>
```

---

## 12. Testing & E2E Contracts

### 10.1 Test Identifiers

All interactive elements must include `data-testid` attributes:

```
data-testid="nav-workspaces"
data-testid="nav-projects"
data-testid="theme-toggle"
data-testid="settings-button"
data-testid="user-menu"
data-testid="app-logo"
data-testid="app-tagline"
data-testid="success-toast"
```

### 10.2 E2E Test Helpers

The root layout includes several E2E testing aids:

- `localStorage.E2E_SESSION_EXPIRED` – Trigger session timeout
- `localStorage.E2E_DISABLE_OVERLAYS` – Disable modal overlays for testing
- `window.__E2E_TEST_NO_POINTER_BLOCK` – Disable pointer event blocking
- `window.__E2E_CREATED` – Project creation handoff

**Contract:**

- All new interactive elements must include `data-testid`
- E2E tests must use the documented helpers, not implementation details

---

## 11. UI Component Library Usage

### 11.1 Component Sources

| Source | Usage |
|--------|-------|
| `@yappc/ui` | Primary UI components (Box, Typography, Paper, Stack, Button) |
| `@yappc/ui/components/DevSecOps` | DevSecOps-specific components (TopNav, KPICard) |
| Local components | Route-specific components |

### 11.2 CSS Variables

The app uses CSS custom properties for theming:

```css
--bg-default
--bg-paper
--bg-surface
--text-primary
--text-secondary
--primary-color
--primary-50
--success-color
--error-color
--divider
--border-radius-sm
--border-radius-md
--transition-fast
```

**Contract:**

- Always use `@yappc/ui` components where available
- Use CSS variables for colors and spacing, never hardcode
- Follow the design token system from `@ghatana/ui`

---

## 12. Proposed Implementation Improvements

Based on spec review, the following implementation changes are recommended:

### 12.1 High Priority

1. **Fix Overview Route Debug Block**
   - File: `routes/app/project/overview.tsx`
   - Issue: Contains temporary debug code returning test message
   - Action: Implement proper overview dashboard per spec

2. **Align Root Layout References**
   - Issue: Specs reference `RootLayout.tsx` but implementation uses `_root.tsx`
   - Action: Update all specs to reference correct file path

3. **Add Missing Sidebar Navigation**
   - File: `routes/app/_shell.tsx`
   - Issue: Only has Workspaces and Projects in sidebar
   - Action: Consider adding DevSecOps, Settings, Canvas Demo links per spec

### 12.2 Medium Priority

4. **Implement Human-Readable Breadcrumbs**
   - Replace raw `location.pathname` display with structured breadcrumbs
   
5. **Wire Settings Button**
   - Currently visual-only; needs to navigate to settings/preferences

6. **Align Theme Toggle Across Shells**
   - `/app` shell and DevSecOps shell have different theme mechanisms

### 12.3 Low Priority

7. **Add GraphQL/WebSocket Connection Status**
   - Display connection health in header area

8. **Expand DevSecOps Navigation**
   - Add more navigation options to DevSecOps TopNav

---

## Appendix A: Route Configuration Reference

The complete route configuration is defined in `routes.tsx` using React Router v7 data router patterns.

Key architectural decisions:
- All routes use lazy loading (`lazy: () => import(...)`)
- Routes export `Component` (required), `loader` (optional), `action` (optional), `ErrorBoundary` (optional)
- Route IDs follow `area-feature` naming (e.g., `devsecops-phase`, `project-canvas`)
- Nested routes use `<Outlet />` for child rendering
