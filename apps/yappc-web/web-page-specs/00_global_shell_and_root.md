# 0. Global Shell & Root – Deep-Dive Spec

> **Document Version:** 2.0.0 (2025-12-29)  
> **Last Updated:** December 29, 2025 – Updated to reflect actual implementation

Related inventory entry: [APP_CREATOR_PAGE_SPECS.md – 0. Global Shell & Root](../APP_CREATOR_PAGE_SPECS.md#0-global-shell--root)

**Code files:**

- `src/App.tsx` – Application providers and router setup
- `src/routes/_root.tsx` – Root layout component
- `src/routes.tsx` – Route configuration

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide the **global technical frame** for the App Creator web app – GraphQL, WebSocket, routing, accessibility landmarks, and E2E testing support – so every feature page runs inside a consistent, data-enabled shell.

**Primary goals:**

- **Global data plumbing:** Ensure all routes can use GraphQL and (optionally) WebSocket streams without re-wiring providers per page.
- **Accessibility landmarks:** Provide skip-to-content links and proper HTML structure.
- **E2E testing support:** Include helpers for Playwright tests (overlay disabling, session simulation).
- **Resilience & loading:** Handle initial router creation safely with loading fallback.

**Non-goals:**

- Implement page-specific business logic, filters, or project/workspace selection.
- Own `/app`-specific navigation details (those live in the App Shell).
- Own DevSecOps layout (that has its own shell with TopNav).
- Surface detailed health metrics or DevSecOps data (those belong to project tabs and DevSecOps pages).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas using this shell (indirectly):**

- **App Creator user (developer / designer):** Enters via workspaces and projects, but always inside this global layout.
- **Platform / DevEx engineer:** Uses the global shell to validate navigation, theming, and connection behavior.
- **SRE / Ops engineer:** May open monitoring/build/deploy views, still wrapped by this shell.
- **QA / Test engineers:** Rely on E2E testing helpers built into the root.

**Key real-world scenarios:**

1. **Cold start into App Creator**
   - User opens the App Creator URL.
   - `App.tsx` mounts GraphQL + WebSocket providers and creates the router.
   - While routes are lazily prepared, a centered "Loading application..." message is shown.
   - When ready, root layout renders with nested content via `<Outlet />`.
   - Default redirect sends users to `/devsecops`.

2. **E2E Testing**
   - Playwright tests set `E2E_DISABLE_OVERLAYS` in localStorage.
   - Root layout automatically disables pointer events on modal overlays.
   - Tests can trigger session expiration via `E2E_SESSION_EXPIRED` flag.
   - Project creation handoff uses `window.__E2E_CREATED`.

3. **Accessibility navigation**
   - Screen reader users can use skip-to-content link.
   - Proper landmarks (`role="navigation"`, `role="main"`) are set in child shells.
   - They click the theme toggle in the app bar (RootLayout).
   - The theme change is global – all subsequent pages respect the new theme mode via shared state.

3. **Router feature compatibility**
   - Newer `react-router-dom` versions may use the `future.v7_startTransition` flag.
   - `App.tsx` attempts to pass this flag safely and falls back to basic configuration if unsupported, ensuring the app keeps working across versions.

---

## 3. Content & Layout Overview

### 3.1 `App.tsx` – Providers, Router, and Loading State

**Key elements:**

- **GraphQLProvider (`@yappc/graphql`):**
  - Wraps the entire app to provide a GraphQL client and cache.
  - Intention: all pages should be able to call hooks like `useQuery` / `useMutation` without redefining providers.

- **WebSocketProvider (`./contexts/WebSocketContext`):**
  - Props:
    - `wsUrl`: `import.meta.env.VITE_WEBSOCKET_URL` or fallback `ws://localhost:3001/ws`.
    - `autoConnect`:
      - Only `true` when `VITE_ENABLE_REAL_WS === 'true'` as a string.
      - Default is `false`, so local dev/tests do not accidentally connect to a live WebSocket.
  - Intention: allow pages like Build/Deploy/Monitor to stream updates over WebSocket without each managing its own connection.

- **RouterProvider (`react-router-dom`):**
  - Router is created from `routes` (`src/routes.tsx`), using `createBrowserRouter`.
  - Tries to opt into `future: { v7_startTransition: true }` but catches errors and retries without `future` when unsupported.
  - `fallbackElement`:
    - Full-viewport flex container.
    - Simple text: **"Loading application..."** with neutral color.
    - Purpose: show a graceful loading state while route elements are lazy-loaded.

**Why this structure matters:**

- Keeps **data and realtime concerns** centralized.
- Ensures **React Router upgrades** can be tested safely without bricking the app.
- Establishes a **consistent loading experience** that does not depend on individual pages.

### 3.2 `routes/_root.tsx` – Root Layout Component

> **Note:** Previous specs referenced `RootLayout.tsx` which no longer exists. The actual implementation is `routes/_root.tsx`.

**Key elements:**

- **E2E Testing Helpers:**
  - Reads `window.__E2E_CREATED` for project creation handoff
  - Displays temporary success toast for E2E test verification
  - Injects CSS and JS to disable pointer events on modal overlays during E2E tests
  - Uses MutationObserver to catch dynamically-mounted modals
  - Detects Playwright via `navigator.webdriver`

- **Accessibility:**
  - Skip-to-content link (`<a href="#main-content" className="sr-only">`)
  - Proper HTML structure with landmark roles

- **Container:**
  - Simple `<div id="root-layout">` with `minHeight: 100vh`
  - Wraps content in `ShortcutProvider` for keyboard shortcuts
  - `<Outlet />` renders the active child route

- **Error Boundary:**
  - `RouteErrorBoundary` imported from `components/route/ErrorBoundary`

**Important architectural note:**

The root layout does NOT include:
- App bar or sidebar (these are in individual shells: `/app/_shell.tsx`, `devsecops/_layout.tsx`)
- Theme toggle (each shell manages its own)
- Global navigation chrome

This differs from the original design. Each area (App Creator, DevSecOps, Mobile) has its **own shell with navigation**, while `_root.tsx` provides only technical infrastructure.

### 3.3 Route Configuration (`routes.tsx`)

**Key patterns:**

```typescript
export const routes: RouteObject[] = [
  {
    id: 'root',
    path: '/',
    lazy: () => import('./routes/_root'),
    children: [
      { index: true, element: <Navigate to="/devsecops" replace /> },
      { id: 'canvas', path: 'canvas', lazy: () => import('./routes/app/project/canvas/CanvasRoute') },
      { id: 'login', path: 'login', lazy: () => import('./routes/login') },
      { id: 'app-shell', path: 'app', lazy: () => import('./routes/app/_shell'), children: [...] },
      { id: 'devsecops', path: 'devsecops', lazy: () => import('./routes/devsecops/_layout'), children: [...] },
      { id: 'workflows', path: 'workflows', lazy: () => import('./routes/workflows/_layout'), children: [...] },
      { id: 'mobile-shell', path: 'mobile', lazy: () => import('./routes/mobile/_shell'), children: [...] },
    ]
  }
];
```

**Route ID conventions:**
- `area-feature` pattern (e.g., `devsecops-phase`, `project-canvas`)
- Descriptive and unique across the application

---

## 4. UX Requirements – User-Friendly and Valuable

### 4.1 Navigation & Wayfinding (Global)

- **Consistent header:**
  - App title should always be visible in the app bar so users know they are inside App Creator.
- **Sidebar behavior:**
  - On desktop, sidebar remains visible when `sidebarOpen` is true; users should not lose navigation accidentally.
  - On mobile, the temporary drawer should be obviously dismissible (tap outside / ESC).
- **Link semantics:**
  - `Home` should lead to a predictable landing page (today this is the root route; in future, likely `/app/workspaces`).
  - `Workspaces` should always route to the canonical workspace list.

### 4.2 Theme & Visual Consistency

- **Clear toggle meaning:**
  - Tooltip should explicitly say which mode you will switch to (not which you are in).
- **Persistent theme state (expected):**
  - `themeMode` stored via `useGlobalState` is expected to persist across reloads (per StateManager behavior) so users don’t re-choose on every visit.
- **Stable colors:**
  - Light/dark theme changes must not make nav text unreadable.

### 4.3 Loading & Error States

- **Initial loading:**
  - "Loading application..." should appear quickly if route resolution is delayed.
  - No flashing or layout jump should occur when routes mount.
- **Router failure:**
  - If the `future` config is rejected, the fallback router path must still lead to a working app; users should not see a blank screen.

---

## 5. Completeness and Real-World Coverage

To be realistic in production, the global shell must support:

1. **Multiple feature areas:**
   - Workspaces/projects, DevSecOps, canvas demos, Page Builder tests – all should mount under RootLayout without custom wrappers.

2. **Environment-aware configuration:**
   - WebSocket URLs and auto-connect flags should be derived from environment configuration, with sensible defaults for local dev and tests.

3. **Incremental rollout of navigation:**
   - As more primary destinations are added (e.g., DevSecOps, Canvas Playground, Settings), the sidebar should grow in a controlled way with clear labels and grouping.

4. **Cross-cutting theming:**
   - Light/dark toggle must be honored by all pages that use `@yappc/ui` components or theme tokens.

---

## 6. Modern UI/UX Nuances and Features

- **Responsive layout:**
  - App bar width and main content area adjust based on whether the sidebar is open.
  - On small screens, the hamburger should be the primary way to open navigation.
- **Animation & smoothness:**
  - Drawer open/close transitions should be smooth and not stutter on low-end devices.
- **Keyboard accessibility:**
  - Hamburger button and theme toggle must be reachable via keyboard and have proper `aria-label`s.
- **Tooltip accessibility:**
  - `wrapForTooltip` should ensure the theme toggle remains a single, focusable control despite Tooltip’s internals.

---

## 7. Coherence and Consistency Across the App

The global shell sets expectations for:

- **Header pattern:**
  - Single app title on the left, utility actions (theme, future account menu) on the right.
- **Navigation mental model:**
  - Left side = "where" (global nav), main area = "what" (current page), right side of header = "how" (theme, user context).
- **Design system usage:**
  - `@yappc/ui` components should be the default choice for layout and controls under this shell.
- **State consistency:**
  - `useGlobalState` keys for sidebar/theme must be reused rather than redefined in feature pages.

Any new route that mounts under RootLayout should:

- Avoid redefining global providers.
- Rely on the shared theme state.
- Use the existing header and content area rather than introducing competing shells.

---

## 8. Links to More Detail & Working Entry Points

**Docs & inventory:**

- Inventory summary: `../APP_CREATOR_PAGE_SPECS.md#0-global-shell--root`

**Code entry points:**

- Global providers and router: `src/App.tsx`
- Desktop layout and nav: `src/router/RootLayout.tsx`
- Route configuration: `src/routes.ts`

---

## 9. Open Gaps & Enhancement Plan

1. **Surface connection state:**
   - Expose GraphQL and WebSocket connection status in the header (e.g., small indicator icon or text like "Connected" / "Offline").

2. **Expand sidebar navigation:**
   - Clearly separate **primary App Creator flows** (Workspaces, Projects, Canvas) from **advanced/demo areas** (DevSecOps, canvas test routes, Page Builder test).

3. **Global context display:**
   - Consider adding workspace/project context into the header for routes that are under `/app`.

4. **Error surface for provider failures:**
   - If GraphQL/WebSocket initialization fails, show a friendly message and troubleshooting link rather than a blank app.

---

## 10. Mockup / Expected Layout & Content

### 10.1 Desktop Layout (Conceptual)

```text
+----------------------------------------------------------------------------+
| App Bar: [☰]  YAPPC App Creator                             [Theme Toggle] |
+---------------------------+------------------------------------------------+
| Sidebar (Drawer)         | Main Content (RootLayout Outlet)               |
|                          |                                                |
|  Home                    |  [Page-specific header + content               |
|  Workspaces              |   rendered by child routes]                    |
|  ... (future nav)        |                                                |
|                          |                                                |
+---------------------------+------------------------------------------------+
```

### 10.2 Loading State (Router Fallback)

```text
+----------------------------------------------------------+
|                                                          |
|   Loading application...                                 |
|                                                          |
+----------------------------------------------------------+
```

This mockup defines the **expected frame** every desktop page in App Creator should live within when not using a mobile-specific shell.
