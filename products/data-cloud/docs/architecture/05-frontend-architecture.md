# Frontend Architecture

See [`diagrams/frontend-routes.mmd`](diagrams/frontend-routes.mmd), [`diagrams/frontend-components.mmd`](diagrams/frontend-components.mmd), and [`diagrams/frontend-state-api-flow.mmd`](diagrams/frontend-state-api-flow.mmd).

## Frontend Shell And Tooling

**Implemented**
- The UI is a React 19 + TypeScript + Vite application with TanStack Query, Jotai, React Router v7, Tailwind, Playwright, Vitest, and Storybook (`ui/package.json`).
- `App.tsx` sets up `QueryClientProvider`, `Jotai` `Provider`, `ThemeProvider`, router, and toast/error boundaries.
- `DefaultLayout.tsx` is the active layout shell for navigation, notifications, search, AI assistant, and WebSocket status.

**Missing**
- `components/layout/AppShell.tsx` exists but appears unused by the route tree.

## Route / Page Hierarchy

### Primary Information Architecture

| Route | Page | Main Data Sources |
|---|---|---|
| `/` | `IntelligentHub` | `brainService`, `collectionsApi`, `workflowsApi`, user activity |
| `/data` and `/data/:id` | `DataExplorer` | `lib/api/collections.ts` |
| `/pipelines` | `WorkflowsPage` | `lib/api/workflows.ts`, AI hints |
| `/pipelines/new`, `/pipelines/:id` | `SmartWorkflowBuilder`, `WorkflowDesigner` | workflow APIs and builder-local state |
| `/query` | `SqlWorkspacePage` | `api/analytics.service.ts`, `dataCloudApi` |
| `/trust` | `TrustCenter` | `api/governance.service.ts` |
| `/insights` | `InsightsPage` | `brainService`, `costService`, `analytics.service.ts`, `workflowsApi` |
| `/alerts` | `AlertsPage` | `api/alerts.service.ts` |
| `/events` | `EventExplorerPage` | `api/events.service.ts` |
| `/memory` | `MemoryPlaneViewerPage` | `api/memory.service.ts` |
| `/entities` | `EntityBrowserPage` | raw `apiClient`, selection hook, user activity |
| `/fabric` | `DataFabricPage` | raw `apiClient`, canvas integrations |
| `/agents` | `AgentPluginManagerPage` | `api/agent-registry.service.ts` |
| `/plugins`, `/plugins/:id` | `PluginsPage`, `PluginDetailsPage` | `api/plugin.service.ts` |
| `/settings` | `SettingsPage` | mostly local/stateful UI |

### Legacy Compatibility Routes

**Implemented**
- `routes.tsx` keeps legacy aliases such as `/collections`, `/workflows`, `/sql`, `/governance`, `/brain`, `/dashboards`, and `/cost`.

**Inferred**
- The UI is in the middle of an information-architecture consolidation rather than a clean greenfield design.

## State Management And Data Flow

| Layer | Implemented Evidence | Role |
|---|---|---|
| Router state | `routes.tsx`, `DefaultLayout.tsx` | Page-level navigation and backward compatibility |
| Server state | TanStack Query hooks in `hooks/useCollections.ts` and similar | API fetch, cache, invalidation |
| Local/global UI state | Jotai store files under `stores/` | command bar, ambient state, feature UI state |
| WebSocket state | `lib/websocket/**` | connection lifecycle and event subscriptions |
| Form state | React Hook Form + Zod listed in `package.json` and validation files in `lib/forms`, `lib/schemas.ts` | typed form validation |

## UI-To-API Interaction Patterns

### Canonical-ish path

- `hooks/*` → `lib/api/*` → `ApiClient` → `/api/v1/*`

### Additional parallel paths

- `pages/*` → `api/*.service.ts`
- `pages/*` → `lib/api/data-cloud-api.ts`
- `services/*` → direct `fetch`
- `src/api/client.ts` → separate zod-validating client with a different route model

## Duplicated And Divergent Client Layers

| Layer | Evidence | Assessment |
|---|---|---|
| `src/lib/api/*` | collections/workflows route adapters | closest thing to current canonical page-facing layer |
| `src/api/*` | alerts, analytics, brain, events, governance, plugins, schema, suggestions | active parallel service layer |
| `src/services/*` | `collections-impl.ts`, `workflows-impl.ts`, `dashboard-impl.ts` | service abstraction layer, often direct fetch |
| `src/api/client.ts` | expects `/api/v1/collections`, `/datasets`, `/lineage`, `/query/execute`, `/search` | partially stale relative to current launcher routes |

## Validation, Permissions, And UI States

**Implemented**
- Validation helpers exist in `lib/forms/validation.ts`, `lib/schemas.ts`, `components/hooks/useValidation.ts`, and page-local validation logic.
- Permission-aware component surface exists via `components/security/RBACGuard.tsx`, but it is re-exported from a shared package and not central to the current route layout.
- Loading and error states are handled via `LoadingState`, route-level suspense, route error boundary, and page-local query state handling.

**Missing**
- No evidence of one consistent permission model wired across all routes/pages.

## Frontend Risks

| Risk | Evidence |
|---|---|
| Route and API drift | E2E specs still target `/collections` and `/api/v1/collections*`; current page data often uses `/data` and generic entity endpoints |
| Duplicate state ownership | Jotai stores, query hooks, direct page fetches, and service classes coexist |
| Reuse confusion | `AppShell` appears unused while `DefaultLayout` reimplements similar shell concerns |
| Contract mismatch | `src/api/client.ts` models collections/datasets/lineage/search endpoints not clearly present in the live launcher |

## Recommended

1. Collapse page data loading onto one client layer, preferably `lib/api/*` + hooks.
2. Remove or explicitly deprecate unused service/client layers.
3. Refresh Playwright specs to target the current IA and route model.
4. Decide whether `AppShell` or `DefaultLayout` is the real shell and delete the other abstraction.
