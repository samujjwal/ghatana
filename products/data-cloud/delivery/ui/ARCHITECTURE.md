# Data Cloud UI – Architecture

> **Canonical reference.** This document is the single source of truth for the architecture of
> all Data Cloud frontend modules. `libs/ui-components/README.md` and
> `ui/docs/DESIGN_ARCHITECTURE.md` both defer to this file.

---

## DC-UI-004 Note

**G1: Updated to reflect generated-client approach.**

UI services must use generated/validated clients instead of ad hoc response types.
Generated TypeScript types are derived from OpenAPI specifications (data-cloud.yaml, action-plane.yaml).
Build integration regenerates types on OpenAPI changes.
Type/contract tests verify UI cannot compile against stale API types.

**Explicit rule:** UI services must use generated/validated clients from `contracts/schemas.ts` and generated API types.

**Runtime Truth rule:** UI must use Runtime Truth `/api/v1/surfaces` for feature availability. Surface-driven navigation should query the surface registry to determine which features are available for the current tenant and deployment profile.

**Default navigation rule:** Default nav must remain outcome-first:
- Home
- Data
- Events
- Query
- Pipelines
- Trust
- Operations

**Implementation note:** Do not run readiness checks in this implementation iteration.

## DC-SHARED-003 Note

DC-SHARED-003 requires moving Data Cloud-specific utilities out of generic shared libraries.
Product-specific semantics should live under `products/data-cloud` to ensure shared libraries are reusable across unrelated products.
Required:
- Audit shared libraries for Data Cloud-specific semantics
- Move product-specific utilities to products/data-cloud
- Dependency/API audit tests to verify shared libraries remain generic
This is a significant audit task requiring comprehensive code review.

---

## 1. Module Overview

The Data Cloud frontend is split into two co-located modules with a hard boundary:

| Module | Package name | Location | Role |
|--------|-------------|----------|------|
| Application | `@data-cloud/ui` | `products/data-cloud/delivery/ui/` | Pages, routing, stores, feature-level components |
| Component library | `@data-cloud/ui-components` | `products/data-cloud/libs/ui-components/` | Reusable presentational primitives with no app-level dependencies |

### Why two modules?

- `@data-cloud/ui-components` can be independently tested and bundled without pulling in
  routing, stores, or services.
- Encourages a clean presentation–application boundary consistent with the repo's domain-separation rules.
- Allows Storybook stories and unit tests to exercise components in isolation.

---

## 2. Module: `@data-cloud/ui` (Application)

### 2.1 Responsibilities

- Renders pages, forms, tables, and flows for Data Cloud operations.
- Wires API calls through typed service hooks; displays validation and constraint feedback.
- Manages application-level state (collection lists, selection, auth context).
- Owns routing configuration and page-level layouts.

### 2.2 Internal structure

```
ui/
  src/
    pages/          # Route-level page components
    features/       # Feature slices (collections, entities, relationships, …)
    components/     # Application-connected components (import from stores / services)
    stores/         # Jotai atoms and derived selectors
    services/       # API service hooks (TanStack Query)
    hooks/          # Shared custom hooks
    lib/            # Internal utilities
  docs/             # Extended design and operational docs (see §4)
```

### 2.3 Dependency rules

- **May** import from `@data-cloud/ui-components` (downward).
- **May** import from `@ghatana/design-system`, `@ghatana/platform-utils`, `@ghatana/theme`.
- **Must not** be imported by `@data-cloud/ui-components` (no upward dependency).
- **Must not** contain backend-specific business logic.

---

## 3. Module: `@data-cloud/ui-components` (Component Library)

### 3.1 Responsibilities

- Provides reusable, presentation-layer UI primitives that multiple features can share.
- Has **no dependency** on routing, stores, or services.
- Exports via subpath entry points for tree-shaking.

### 3.2 Exported subpaths

| Subpath | Contents |
|---------|----------|
| `@data-cloud/ui-components/common` | General-purpose layout and feedback components |
| `@data-cloud/ui-components/cards` | KPI and generic card containers |
| `@data-cloud/ui-components/lib` | Re-exported theme utilities (`cn`, `cardStyles`, etc.) |

### 3.3 Component inventory

**`/common`**

| Component | Purpose |
|-----------|---------|
| `AppErrorBoundary` | Global error boundary with graceful fallback UI |
| `Button` | Styled button with variants and loading states |
| `Container` | Responsive layout container |
| `EmptyState` | Empty/no-data state with icon, message, and action |
| `KeyboardShortcuts` | Keyboard shortcuts overlay modal |
| `LoadingState` | Loading indicator with message |
| `StatusBadge` | Semantic status badge using `@ghatana/design-system` tokens |
| `TabWorkspace` | Tabbed workspace layout with context action toolbar |
| `Timeline` | Event timeline with typed event variants |
| `ToastProvider` + `toast` | Toast notification system via `sonner` |

**`/cards`**

| Component | Purpose |
|-----------|---------|
| `BaseCard` | Generic card container with optional title and actions |
| `KPICard` | KPI metric card with trend indicators and sparkline support |

### 3.4 Dependency rules

- **May** import from `@ghatana/design-system`, `@ghatana/theme`, `@ghatana/platform-utils`.
- **Must not** import from `@data-cloud/ui` (no circular or upward dependency).
- **Must not** import routing primitives (`react-router-dom`, Next.js router, etc.).
- **Must not** reference Jotai atoms or TanStack Query hooks.

---

## 4. Supplementary Documentation

| Doc | Location | Coverage |
|-----|----------|----------|
| Coding guidelines | `ui/docs/guidelines/CODING.md` | Style, patterns, hooks |
| Testing guidelines | `ui/docs/guidelines/TESTING.md` | Vitest, RTL, Playwright setup |
| Route truth matrix | `ui/docs/ROUTE_TRUTH_MATRIX.md` | Page → route → breadcrumb mapping |
| Operations | `ui/docs/operations/` | Build, deploy, monitoring |

---

## 5. Testing Strategy

| Layer | Tooling | Coverage target |
|-------|---------|----------------|
| Component unit tests | Vitest + React Testing Library | `@data-cloud/ui-components` ≥ 80% |
| Feature integration tests | Vitest + RTL + MSW | `@data-cloud/ui` feature slices |
| Browser E2E | Playwright | Critical user flows (login → collection → entity CRUD) |

Unit tests for `@data-cloud/ui-components` live in `libs/ui-components/src/__tests__/`.
Application-level tests live co-located with their feature under `ui/src/features/`.

---

## 6. Build and Tooling

Both modules use Vite for bundling and Vitest for unit/integration tests. They share:

- `@ghatana/theme` for design tokens.
- `@ghatana/design-system` for atomic primitives.
- ESLint configuration rooted at the product workspace (`ui/eslint.config.js`).
- TypeScript strict mode (`strict: true`) — no `any` types allowed.

---

*Last updated: 2026-04-28 — Canonical document for Data Cloud frontend architecture.*
