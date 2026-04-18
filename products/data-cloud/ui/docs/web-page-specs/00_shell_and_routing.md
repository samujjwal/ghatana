# 0. Shell & Routing – Deep-Dive Spec

Related docs:

- Data Cloud UI design overview: `../DESIGN_ARCHITECTURE.md`

**Code files:**

- `src/App.tsx`
- `src/styles/globals.css`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide the **root React shell and route configuration** so the Intelligent Hub, Data Explorer, Pipelines, Query, and operator/admin-only surfaces run inside a consistent, provider-wrapped app.

**Primary goals:**

- Initialize **Jotai** for state management.
- Initialize **ThemeProvider** from `@ghatana/theme`.
- Define route → page mappings using **React Router**.
- Handle async page loading via `React.Suspense` with a `LoadingScreen` fallback.
- Act as a **thin, modular shell** that can be embedded inside a broader Data Cloud UI (web or Tauri host) without changing page contracts.

**Non-goals:**

- Detailed layout/navigation shell (header/sidebar) – those live in individual pages.
- Data fetching or business logic for specific pages.

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Primary user** using Data Cloud for data, pipelines, and query flows.
- **Operator/admin** using diagnostic and governance surfaces with progressive disclosure.
- **Implementer / integrator** wiring Data Cloud UI into a larger app.

**Key scenarios:**

1. **Opening Data Cloud UI**
  - User hits `/` and is routed to `IntelligentHub`.

2. **Navigating between core pages**
  - Primary users navigate to `/data`, `/pipelines`, and `/query` via links/buttons.
  - Operators switch shell role to reveal `/insights`, `/trust`, and `/events`.
   - Router mounts the appropriate page component.

3. **Handling unknown routes**
   - User enters an invalid URL → `NotFound` page.

---

## 3. Content & Layout Overview

From `src/App.tsx`:

- Lazy-loaded pages:
  - `IntelligentHub` – `/`, compatibility aliases `/dashboard`, `/hub`
  - `DataExplorer` – `/data`, `/data/:id`, `/data/:id/:view`, compatibility alias `/collections`
  - `CreateCollectionPage` – `/data/new`, compatibility alias `/collections/new`
  - `EditCollectionPage` – `/data/:id/edit`, compatibility alias `/collections/:id/edit`
  - `WorkflowsPage` – `/pipelines`, compatibility alias `/workflows`
  - `SmartWorkflowBuilder` – `/pipelines/new`, compatibility alias `/workflows/new`
  - `WorkflowDesigner` – `/pipelines/:id`, `/pipelines/:id/edit`, compatibility alias `/workflows/:id`
  - `SqlWorkspacePage` – `/query`, compatibility alias `/sql`
  - Explicit compatibility handoffs: `/lineage` → `/data?view=lineage`, `/quality` → `/data?view=quality`
  - `NotFound` – `*`

- Providers:
  - `<Provider>` from Jotai wraps the entire app.
  - `<ThemeProvider>` wraps the router.
- Routing uses `createBrowserRouter` and `RouterProvider`.
- Each route element is wrapped in `<React.Suspense fallback={<LoadingScreen />}>`.
- The `App` component intentionally keeps **global chrome minimal** – it wires providers and routes, leaving headers/sidebars/workspace framing to outer shells when used inside a larger Data Cloud experience.

---

## 4. UX Requirements – User-Friendly and Valuable

- **Smooth loading:**
  - `LoadingScreen` should present a clear loading indicator and short message.
- **Predictable navigation:**
  - `/` is the canonical home route and `/dashboard` is a compatibility alias.
  - `/query` is the canonical SQL route and `/sql` is a compatibility alias.
  - `/lineage` and `/quality` are compatibility handoffs into Data Explorer preview modes rather than standalone workspaces.
  - Unknown routes reliably hit `NotFound`.
- **Future shell alignment:**
  - As shells evolve (shared headers/layouts), they should integrate consistently across pages without changing route intent.
- **Consistent behavior across web and desktop containers:**
  - When this App is hosted inside a Tauri shell, navigation semantics and loading behavior should remain identical; only the outer window chrome and menus differ.

---

## 5. Completeness and Real-World Coverage

This shell should support:

1. Adding new pages (e.g., `Executions`, `Compliance`) without major refactors.
2. Embedding **canvas-like designers** (WorkflowDesigner) that may have complex internal state.
3. Potential integration with **global app shells** (e.g., App Creator) by treating Data Cloud as a sub-app routed under a parent.
4. Being hosted in both **browser** and **desktop (Tauri)** containers with minimal conditional logic in the App itself.
5. Attaching cross-cutting providers (e.g., realtime events, notifications, AI assistant) at the shell or host level without breaking existing page contracts.

---

## 6. Modern UI/UX Nuances and Features

- **Code-splitting:**
  - Leveraging `React.lazy` + `Suspense` to load pages on demand.
- **Provider layering:**
  - Keep providers close to the root so all pages share theme/state.
- **Realtime-ready provider stack (future):**
  - The root shell is the right place to mount shared realtime clients (e.g., WebSocket/EventSource via `@ghatana/realtime`) once live insight summaries, alerts, and workflow status streams are implemented.

---

## 7. Coherence with App Creator / Canvas & Platform

- Like App Creator and AEP shells, Data Cloud uses:
  - A slim App component as a **routing + provider shell**.
  - Pages responsible for their own content and local layout.
- `WorkflowDesigner` is the bridge into **canvas-based editing**; the shell must support mounting such experiences under canonical `/pipelines/...` routes while preserving compatibility aliases only for deep-link continuity.
- Within the broader Data Cloud product, this App can be treated as the **"Data Cloud workspace module"**, alongside other workspaces (Dataset Explorer, SQL Workspace, Governance), each mounted by a higher-level host shell.

---

## 8. Links to More Detail & Working Entry Points

- Data Cloud design overview: `../DESIGN_ARCHITECTURE.md`
- Workflow canvas component: `src/components/workflow/WorkflowCanvas.tsx`
- Data fabric feature integration: `src/features/data-fabric/INTEGRATION_GUIDE.md`

---

## 9. Gaps & Enhancement Plan

1. **Global nav & layout:**
   - Introduce a shared app header/sidebar so pages don’t each reinvent top-level chrome.

2. **Error boundaries:**
   - Wrap Router or page boundaries in an error boundary to handle runtime errors gracefully.

3. **Auth & multi-tenant context (future):**
   - Integrate auth/tenant providers consistent with other products.

4. **Realtime event channels:**
  - Add a shared realtime client/provider (WebSockets or EventSource, likely via `@ghatana/realtime`) at the shell or host level so insight summaries, workflows, and alerts can update live without manual refresh.

5. **Tauri desktop host integration:**
   - Document and standardize how this App is embedded in a Tauri shell (window chrome, deep linking, file/protocol handlers) while keeping route definitions unchanged.

6. **Global workspace shell:**
   - Define a pattern for top nav, side nav, global search, notifications, and multi-tab workspace framing around `RouterProvider`, consistent with other Data Cloud workspaces.

---

## 10. Mockup / Expected Layout & Content

```text
Providers
-------------------------------------------------------------------------------
[Jotai Provider]
  [ThemeProvider]
    [RouterProvider]
      Routes:
      - /                → IntelligentHub
      - /data            → DataExplorer
      - /data/new        → CreateCollectionPage
      - /data/:id        → DataExplorer (detail)
      - /data/:id/edit   → EditCollectionPage
      - /pipelines       → WorkflowsPage
      - /pipelines/new   → SmartWorkflowBuilder
      - /pipelines/:id   → WorkflowDesigner
      - /query           → SqlWorkspacePage
      - /lineage         → Navigate(/data?view=lineage)
      - /quality         → Navigate(/data?view=quality)
      - *                → NotFound

All route elements are wrapped in Suspense with a shared LoadingScreen.
```
