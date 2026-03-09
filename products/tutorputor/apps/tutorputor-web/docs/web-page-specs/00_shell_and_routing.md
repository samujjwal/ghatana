# 0. Shell & Routing – Deep-Dive Spec

Related docs:

- App overview / README (when present in this repo or product-level docs).

**Code files:**

- `src/main.tsx`
- `src/App.tsx`
- `src/router/routes.tsx`
- `src/components/AppLayout.tsx`

---

## 1. Intention (Clear & Unambiguous)

**One-sentence intent:**

> Provide the **global React Query + router shell and layout** for TutorPutor so every learner page runs inside a consistent frame with predictable navigation.

**Primary goals:**

- Initialize a **QueryClient** and provide React Query across the app.
- Define the **main route tree** and link pages under a shared layout.
- Surface a simple top navigation (brand + Dashboard link).

**Non-goals:**

- Implement user authentication or profile management (assumed handled elsewhere).
- Define detailed lesson or content views (those will be separate pages later).

---

## 2. Users, Personas, and Real-World Scenarios

**Personas:**

- **Learner** (developer or student) consuming modules.
- **Tutor or content author** (future views) reusing the same shell.

**Key scenarios:**

1. **Learner opens TutorPutor**

   - `main.tsx` renders `<App />` into `#root`.
   - `App` wraps the app in `QueryClientProvider` and `RouterProvider`.
   - Router renders `AppLayout` and the requested child route.

2. **Learner navigates to Dashboard or a module**
   - From the nav bar, clicks `Dashboard` → `DashboardPage`.
   - From dashboard, clicks a module card → `ModulePage` for that `slug`.

---

## 3. Content & Layout Overview

### 3.1 `src/main.tsx` – React Root

- Uses `createRoot` + `StrictMode` to render `<App />` into `#root`.

### 3.2 `src/App.tsx` – Data & Router Shell

- Creates a `QueryClient` with:
  - `refetchOnWindowFocus: false`.
  - `retry: 1`.
- Wraps the app with:
  - `<QueryClientProvider client={queryClient}>`.
  - `<RouterProvider router={router} />`.
  - `<ReactQueryDevtools initialIsOpen={false} />`.

### 3.3 `src/router/routes.tsx` – Route Tree

- `createBrowserRouter` with root:
  - `path: "/"`, `element: <AppLayout />`.
  - `children`:
    - Index route → `<DashboardPage />`.
    - `/dashboard` → `<DashboardPage />`.
    - `/modules/:slug` → `<ModulePage />`.

### 3.4 `src/components/AppLayout.tsx` – Layout Shell

- Layout structure:
  - Full-page `div` with background.
  - `nav` bar with brand and Dashboard link.
  - `<main>` with `<Outlet />` for page content.

**Why this structure matters:**

- Centralizes **data fetching** and **routing** concerns.
- Ensures a **stable navigation frame** across all TutorPutor pages.
- Keeps page components focused on domain content (dashboard, module details).

---

## 4. UX Requirements – User-Friendly and Valuable

- **Consistent header:**
  - Top nav should always show `TutorPutor` brand and a clear `Dashboard` link.
- **Predictable routing:**
  - `/` and `/dashboard` should both land on the dashboard.
- **Graceful loading & errors:**
  - Global shell should not show blank screens when child routes handle loading.

---

## 5. Completeness and Real-World Coverage

A robust shell and routing layer should:

1. Support future pages (e.g., Lessons, Assessments, Reports) via the same layout.
2. Integrate auth/tenant providers when TutorPutor grows into multi-tenant or multi-role usage.
3. Provide top-level error boundaries (future).

---

## 6. Modern UI/UX Nuances and Features

- **Responsive nav bar:**
  - Links should remain usable on smaller viewports.
- **Devtools toggle:**
  - React Query Devtools should be available but unobtrusive (default closed).

---

## 7. Coherence with App Creator / Canvas & Platform

- Like App Creator and AEP shells, TutorPutor’s shell separates:
  - **Frame** (header/nav, providers) from **content** (pages).
- In future, lesson or module content could be represented via canvas-like views that plug into this same shell.

---

## 8. Links to More Detail & Working Entry Points

- Code:
  - React entry: `src/main.tsx`
  - Shell: `src/App.tsx`
  - Routes: `src/router/routes.tsx`
  - Layout: `src/components/AppLayout.tsx`

---

## 9. Gaps & Enhancement Plan

1. **Header content:**

   - Add user avatar/profile and sign-out when auth is wired in.

2. **Breadcrumbs (future):**

   - Provide breadcrumb or secondary nav for nested pages (e.g., module lessons).

3. **Error boundaries:**
   - Introduce a top-level error boundary for route/render failures.

---

## 10. Mockup / Expected Layout & Content

```text
+----------------------------------------------------------------------------+
| TutorPutor header                                                          |
+----------------------------------------------------------------------------+
| TutorPutor                                       [ Dashboard ]            |
+----------------------------------------------------------------------------+

[ Outlet: page content ]
- For `/` or `/dashboard`: Dashboard page (see 01 spec).
- For `/modules/:slug`: Module page (see 02 spec).
```
