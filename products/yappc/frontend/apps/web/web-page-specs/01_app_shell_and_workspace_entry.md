# 1. Core App Creator Area – `/app` Shell & Workspace Entry – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated with actual implementation details

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 1. Core App Creator Area – `/app` Shell](../APP_CREATOR_PAGE_SPECS.md#1-core-app-creator-area--app-shell)

**Code files:**

| File | Purpose |
|------|---------|
| `src/routes/app/_shell.tsx` | App shell with sidebar navigation |
| `src/routes/app/workspaces.tsx` | Workspace listing page |
| `src/routes/app/projects.index.tsx` | Legacy redirect to workspace projects |
| `src/routes/app/projects.tsx` | Projects listing within workspace |
| `src/routes/app/projects.new.tsx` | New project creation form |
| `src/routes/app/project/_shell.tsx` | Project shell with lifecycle tabs |

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide the **core App Creator workspace experience** – a dedicated `/app` shell, workspace list, project list, and project shell – so users can reliably move from "which workspace?" → "which project?" → "which lifecycle view?".

**Primary goals:**

- **Separate authenticated app flows** under `/app` from other top-level experiences (canvas demos, DevSecOps, login, etc.).
- **Let users choose a workspace** as their primary context.
- **List projects within a workspace** with health summaries.
- **Provide a project-level shell** with tabs for Overview, Canvas, Backlog, Design, Build, Test, Deploy, Monitor, Versions, and Settings.

**Non-goals:**

- Implement all project details (that lives in the per-tab routes).
- Handle authentication (login is separate; this shell assumes a valid session).
- Own DevSecOps or mobile experiences (those have their own shells).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Product Engineer / Frontend Engineer:** Works on a single App Creator project at a time, but may switch workspaces.
- **Tech Lead / Architect:** Browses multiple projects across teams to understand health and status.
- **Platform / DevEx engineer:** Uses these flows to verify that new projects and templates appear correctly.

**Key scenarios:**

1. **Starting a new day in App Creator**
   - User opens `/app/workspaces` and sees a list of workspaces (personal, team, sandbox).
   - They click a workspace card and land on `/app/w/:workspaceId/projects`.
   - They choose a project card and land on the project shell with tabs.

2. **Creating a new project**
   - From a workspace’s projects list, user clicks **"New Project"**.
   - `/app/projects/new` opens with fields for name, description, and template.
   - On submit (currently stubbed), they are navigated to the new project’s overview (intended behavior).

3. **Handling legacy links**
   - Older links or tests point to `/app/projects` without workspace in the URL.
   - `projects.index.tsx` redirects automatically to `/app/w/ws-1/projects` (placeholder), preserving basic compatibility.

---

## 3. Content & Layout Overview

### 3.1 `/app/_shell.tsx` – App Shell Layout

**Structure:**

- **Session timeout check (E2E support):**
  - `useEffect` periodically checks `localStorage.E2E_SESSION_EXPIRED`.
  - If set to `'true'`, it clears the flag and navigates to `/auth/login?reason=session-expired`.
  - Purpose: simulate session expiry for end-to-end tests.

- **Sidebar navigation (left column):**
  - Fixed 240px width, light background, right border.
  - Header:
    - `YAPPC` title (`data-testid="app-logo"`).
    - Tagline `Yet Another Project Planning Canvas` (`data-testid="app-tagline"`).
  - Nav items:
    - `🏠 Workspaces` → `/app/workspaces` (`data-testid="nav-workspaces"`).
    - `📁 Projects` → `/app/projects` (`data-testid="nav-projects"`).
  - Active nav styling:
    - Primary color text and background.
    - Right border bar indicating active item.

- **Top bar (within main content):**
  - Shows current `location.pathname` in small text.
  - Right-side actions:
    - **Theme** button (`data-testid="theme-toggle"`): toggles `data-theme` on `documentElement` between `light` and `dark`.
    - **Settings** button (`data-testid="settings-button"`): visual only for now.
    - **User avatar** (`data-testid="user-menu"`): blue circular badge with "U".

- **Main content area:**
  - Uses `<Outlet />` to render whatever `/app/*` child route is active (workspaces list, projects list, project shell).
  - Background color uses `--color-background` token; scrollable.

**Why this matters:**

- Clearly separates **authenticated App Creator journeys** from other parts of the product.
- Provides a small but clear **brand + tagline**, reinforcing that this area is about **project planning and creation**.
- Offers a simple, testable nav model (`data-testid` hooks) used in E2E tests.

### 3.2 `/app/workspaces` – Workspace List

_(based on `routes/app/workspaces.tsx`)_

- Header: `Workspaces` with subtitle explaining that workspaces group projects.
- Content:
  - Grid of workspace cards with:
    - Name (e.g., **Personal Projects**, **Team Alpha**).
    - Project count.
    - Last activity.
  - `+ New Workspace` button (currently visual only).
- Behavior:
  - Clicking a workspace navigates to `/app/w/:workspaceId/projects`.
  - Data is currently mocked.

### 3.3 `/app/projects` – Projects Index Redirect

_(based on `routes/app/projects.index.tsx`)_

- Simple component that:
  - Renders placeholder text like "Redirecting to projects...".
  - Uses `useEffect` + `useNavigate` to redirect to `/app/w/ws-1/projects`.
- Purpose: keep existing links/tests functioning while encouraging workspace-scoped URLs.

### 3.4 `/app/w/:workspaceId/projects` – Workspace Project List

_(based on `routes/app/projects.tsx`)_

- Loader fetches projects (currently mock data) and supports E2E error simulation.
- UI:
  - Each project is shown as a **card** with:
    - Name, description.
    - Indicators for build/test/deploy health.
  - Button or link to create a new project.
- Clicking a project opens `/app/w/:workspaceId/p/:projectId/overview` inside the project shell.

### 3.5 `/app/projects/new` – New Project Form

_(based on `routes/app/projects.new.tsx`)_

- Form fields:
  - `Project Name` (required, validated with clear error messages).
  - `Project Description`.
  - `Template` dropdown (e.g., React/Node options).
- Behavior:
  - Client-side validation of name (length, characters).
  - On submit, simulate project creation and navigate to a project route (stubbed for now).

### 3.6 `routes/app/project/_shell.tsx` – Project Shell & Tabs

- Header region:
  - Back link to workspace projects.
  - Project avatar, name, ID.
  - Status bar summarizing project health (e.g., last deploy, status icons).
- Tab strip:
  - **Overview**
  - **Implement (Canvas)**
  - **Backlog**
  - **Design**
  - **Build**
  - **Test**
  - **Deploy**
  - **Monitor**
  - **Versions**
  - **Settings**
- `<Outlet />` renders the selected tab route.

---

## 4. UX Requirements – User-Friendly and Valuable

### 4.1 Navigation & Wayfinding within `/app`

- **Clear workspace vs project levels:**
  - `/app/workspaces` is about **which workspace**.
  - `/app/w/:workspaceId/projects` is about **which project**.
  - `/app/w/:workspaceId/p/:projectId/...` is about **what you are doing** inside a project.
- **Breadcrumb-like behavior:**
  - Project shell provides a back link so users can quickly return to the workspace’s project list.
- **Active nav state:**
  - `🏠 Workspaces` and `📁 Projects` links in the side nav must show visually which area is active.

### 4.2 Forms & Validation (New Project)

- **Inline validation:**
  - Show errors directly under the `Project Name` field in plain language.
- **Template clarity:**
  - Each template option should explain what it configures (e.g., React SPA with Node backend scaffolding).

### 4.3 Session & Theme Handling

- **Session expiration:**
  - When E2E sets `E2E_SESSION_EXPIRED`, redirect clearly to login with a query param reason; later, the login page should display friendly copy explaining the timeout.
- **Theme toggle:**
  - The `Theme` button should give immediate feedback (background/text change) and be accessible to keyboard users.

---

## 5. Completeness and Real-World Coverage

The `/app` shell + workspace/project flows should support:

1. **Many workspaces and projects:**
   - Lists should scale with search/filter/pagination for real organizations.

2. **Workspace-specific configuration:**
   - Projects are always scoped to a workspace; URLs should consistently include `workspaceId`.

3. **Seamless project lifecycle navigation:**
   - Once in a project, tabs cover the full lifecycle (plan → design → build → deploy → monitor).

4. **Error handling:**
   - Workspaces and projects list pages should handle loader errors with clear UI, not just console logs.

---

## 6. Modern UI/UX Nuances and Features

- **Typography & spacing:**
  - Workspace/project lists should use clear headings, card spacing, and legible hierarchies.
- **Empty states:**
  - Workspaces with no projects, or users with no workspaces, should show friendly guidance.
- **Hover & focus feedback:**
  - Nav links and project cards must have noticeable hover/focus styles.
- **Responsiveness:**
  - The layout should remain usable on narrower desktop windows; long workspace names should truncate gracefully.

---

## 7. Coherence and Consistency Across the App

This `/app` shell must:

- Match the **global RootLayout** in tone and visual style.
- Reuse the same theme and token system (via `data-theme` and CSS variables).
- Provide a predictable mental model:
  - Left = navigation across workspaces/projects.
  - Top = context (path, theme, user).
  - Main = current list or project view.

New `/app` routes should plug into:

- The existing sidebar (`Workspaces` / `Projects`) or future `/app` nav items.
- The shared top bar pattern.

---

## 8. Links to More Detail & Working Entry Points

**Docs & inventory:**

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#1-core-app-creator-area--app-shell`

**Code entry points:**

- App shell: `src/routes/app/_shell.tsx`
- Workspace list: `src/routes/app/workspaces.tsx`
- Projects redirect: `src/routes/app/projects.index.tsx`
- Workspace projects list: `src/routes/app/projects.tsx`
- New project form: `src/routes/app/projects.new.tsx`
- Project shell & tabs: `src/routes/app/project/_shell.tsx`

---

## 9. Open Gaps & Enhancement Plan

1. **Human-readable header context:**
   - Replace raw `location.pathname` in the top bar with a structured breadcrumb like `Workspace: Team Alpha / Projects`.

2. **Settings wiring:**
   - Connect the `Settings` button to a real settings or profile/preferences surface.

3. **Persisted theme for `/app` shell:**
   - Align theme behavior with the global RootLayout so users don’t see conflicting theme mechanisms.

4. **Real workspace/project data:**
   - Replace mock data with GraphQL-backed lists, including owner, status, and last activity.

5. **Improved redirect behavior:**
   - Instead of hard-coding `ws-1`, choose the last used workspace or a default from the user profile.

---

## 10. State Management

### Current Implementation (`_shell.tsx`)

```typescript
// Uses Jotai for theme state (from @yappc/store)
import { themeAtom } from '@yappc/store';
const [theme, setTheme] = useAtom(themeAtom);

// E2E testing helpers via data attributes
<Box data-testid="app-shell" data-theme={theme}>
```

### Routes Configuration

```typescript
// From src/routes.tsx
{
  id: 'app-shell',
  path: 'app',
  lazy: () => import('./routes/app/_shell'),
  children: [
    { id: 'app-workspaces', path: 'workspaces', lazy: () => import('./routes/app/workspaces') },
    { id: 'app-projects-index', index: true, path: 'projects', lazy: () => import('./routes/app/projects.index') },
    { 
      id: 'app-workspace',
      path: 'w/:workspaceId',
      children: [
        { id: 'app-workspace-projects', path: 'projects', lazy: () => import('./routes/app/projects') },
        {
          id: 'project-shell',
          path: 'p/:projectId',
          lazy: () => import('./routes/app/project/_shell'),
          children: [/* project tabs */]
        }
      ]
    }
  ]
}
```

---

## 11. Testing Contracts

### E2E Tests (Playwright)

```typescript
test.describe('App Shell', () => {
  test('sidebar navigation works', async ({ page }) => {
    await page.goto('/app/workspaces');
    await expect(page.getByTestId('sidebar')).toBeVisible();
    await page.getByRole('link', { name: /workspaces/i }).click();
    await expect(page).toHaveURL('/app/workspaces');
  });

  test('theme toggle changes appearance', async ({ page }) => {
    await page.goto('/app/workspaces');
    const shell = page.getByTestId('app-shell');
    await page.getByRole('button', { name: /toggle theme/i }).click();
    await expect(shell).toHaveAttribute('data-theme', /dark|light/);
  });

  test('legacy projects route redirects', async ({ page }) => {
    await page.goto('/app/projects');
    await expect(page).toHaveURL(/\/app\/w\/[\w-]+\/projects/);
  });
});
```

---

## 12. Mockup / Expected Layout & Content

### 12.1 `/app` Shell (Desktop)

```text
+--------------------------------------------------------------------------------+
| Top Bar: [ /app/w/ws-1/projects ]                         [Theme] [Settings] U |
+------------------------+-------------------------------------------------------+
| Sidebar                | Main Area                                            |
|                        |                                                       |
|  YAPPC                 |  [Workspaces or Project list, or Project tabs]       |
|  Yet Another Project   |                                                       |
|  Planning Canvas       |                                                       |
|                        |                                                       |
|  🏠 Workspaces         |                                                       |
|  📁 Projects           |                                                       |
+------------------------+-------------------------------------------------------+
```

### 12.2 Workspace → Project Selection Flow

```text
1. /app/workspaces
   - Grid of workspace cards
   - Click "Team Alpha" → /app/w/team-alpha/projects

2. /app/w/team-alpha/projects
   - List of projects with health chips
   - Click "Component Library" → /app/w/team-alpha/p/component-library/overview

3. /app/w/team-alpha/p/component-library/overview
   - Project shell with tabs, starting on Overview
```

This spec should be used by designers and engineers as the contract for how users enter the App Creator experience, choose a workspace, select a project, and land in the project shell.
