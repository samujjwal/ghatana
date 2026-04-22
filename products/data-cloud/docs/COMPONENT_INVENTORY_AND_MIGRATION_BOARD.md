# Data Cloud UI — Component Inventory & Migration Board

> **DS-005**: Canonical reference for all UI components, hooks, services, and planned migrations.
> Last updated: 2026-04-23

## Inventory Summary

| Category | Count | Status |
|---|---|---|
| Components (`@doc.type component`) | 116 | ✅ Documented |
| Pages (`@doc.type page`) | 18 | ✅ Documented |
| Hooks (`@doc.type hook`) | 18 | ✅ Documented |
| Services (`@doc.type service`) | 28 | ✅ Documented |
| Stores (`@doc.type store`) | 8 | ✅ Documented |
| Tests (`@doc.type test`) | 32 | ✅ Active |
| Stories (`@doc.type storybook`) | 9 | ✅ Active |

---

## Component Registry (by Domain)

### Core Layout
| Component | File | Purpose | Layer |
|---|---|---|---|
| `AppShell` | `components/layout/AppShell.tsx` | Global app wrapper with search & shortcuts | `frontend` |
| `PageHeader` | `components/layout/PageLayout.tsx` | Page title, icon, actions | `frontend` |
| `PageContent` | `components/layout/PageLayout.tsx` | Content + optional sidebars | `frontend` |
| `PageSection` | `components/layout/PageLayout.tsx` | Section with actions | `frontend` |
| `ContextPanel` | `components/layout/PageLayout.tsx` | Right sidebar panel | `frontend` |
| `SuggestionCard` | `components/layout/PageLayout.tsx` | Inline suggestion card | `frontend` |

### Navigation & Shell
| Component | File | Purpose | Layer |
|---|---|---|---|
| `CommandBar` | `components/core/CommandBar.tsx` | Global command palette | `frontend` |
| `ContextSidebar` | `components/core/BrainSidebar.tsx` | Collapsible assistance sidebar | `frontend` |
| `AmbientIntelligenceBar` | `components/core/AmbientIntelligenceBar.tsx` | Contextual status bar | `frontend` |

### Common / Shared
| Component | File | Purpose | Layer |
|---|---|---|---|
| `LabeledInput` | `components/common/LabeledInput.tsx` | Accessible input with label | `shared` |
| `LabeledSelect` | `components/common/LabeledInput.tsx` | Accessible select with label | `shared` |
| `SearchFilterBar` | `components/common/SearchFilterBar.tsx` | Search + filter chips | `shared` |
| `LoadingState` | `components/common/LoadingState.tsx` | Skeleton & spinner | `shared` |
| `EmptyState` | `components/common/EmptyState.tsx` | Empty state illustration | `shared` |
| `StatusBadge` | `components/common/StatusBadge.tsx` | Status indicator badge | `shared` |
| `GlobalSearch` | `components/common/GlobalSearch.tsx` | Global search modal | `shared` |
| `AppErrorBoundary` | `components/common/AppErrorBoundary.tsx` | App-level error boundary | `shared` |
| `RouteErrorBoundary` | `components/common/RouteErrorBoundary.tsx` | Route-level error boundary | `shared` |
| `Toast` | `components/common/Toast.tsx` | Toast notification | `shared` |

### AI / Assistance Components (post-AI-013 rename)
| Component | File | Purpose | Layer |
|---|---|---|---|
| `Assistant` | `components/ai/AiAssistant.tsx` | Chat interface for data assistance | `frontend` |
| `SmartSQLAssistant` | `components/ai/SmartSQLAssistant.tsx` | NL-to-SQL generation | `frontend` |
| `AIConfidenceIndicator` | `components/ai/AIConfidenceIndicator.tsx` | Confidence score badge | `frontend` |
| `AIFallbackUI` | `components/ai/AIFallbackUI.tsx` | Fallback when assistance offline | `frontend` |

### Brain / Spotlight
| Component | File | Purpose | Layer |
|---|---|---|---|
| `SpotlightRing` | `components/brain/SpotlightRing.tsx` | Highlighted suggestions | `frontend` |
| `AutonomyTimeline` | `components/brain/AutonomyTimeline.tsx` | Autonomy action history | `frontend` |
| `AutonomyControl` | `components/brain/AutonomyControl.tsx` | Autonomy level toggle | `frontend` |
| `AutonomyShutoffBanner` | `components/brain/AutonomyShutoffBanner.tsx` | Safety banner | `frontend` |
| `FeedbackWidget` | `components/brain/FeedbackWidget.tsx` | Inline feedback | `frontend` |
| `O11yPanel` | `components/core/O11yPanel.tsx` | Observability mini-panel | `frontend` |

### Pages
| Page | File | Role | Layer |
|---|---|---|---|
| `IntelligentHub` | `pages/IntelligentHub.tsx` | Primary user home | `frontend` |
| `DataExplorer` | `pages/DataExplorer.tsx` | Collections viewer | `frontend` |
| `EntityBrowserPage` | `pages/EntityBrowserPage.tsx` | Entity explorer | `frontend` |
| `EventExplorerPage` | `pages/EventExplorerPage.tsx` | AEP event stream | `frontend` |
| `ContextExplorerPage` | `pages/ContextExplorerPage.tsx` | Context graph | `frontend` |
| `MemoryPlaneViewerPage` | `pages/MemoryPlaneViewerPage.tsx` | Memory plane | `frontend` |
| `DataFabricPage` | `pages/DataFabricPage.tsx` | Tier topology | `frontend` |
| `TrustCenter` | `pages/TrustCenter.tsx` | Governance & compliance | `frontend` |
| `AlertsPage` | `pages/AlertsPage.tsx` | Alert triage | `frontend` |
| `InsightsPage` | `pages/InsightsPage.tsx` | Analytics & diagnostics | `frontend` |
| `OperationsConsolePage` | `pages/OperationsConsolePage.tsx` | Operator console | `frontend` |
| `SqlWorkspacePage` | `pages/SqlWorkspacePage.tsx` | SQL editor | `frontend` |
| `SmartWorkflowBuilder` | `pages/SmartWorkflowBuilder.tsx` | Pipeline builder | `frontend` |
| `WorkflowsPage` | `pages/WorkflowsPage.tsx` | Workflow list | `frontend` |
| `PluginsPage` | `pages/PluginsPage.tsx` | Plugin management | `frontend` |
| `AgentPluginManagerPage` | `pages/AgentPluginManagerPage.tsx` | Agent registry | `frontend` |
| `SettingsPage` | `pages/SettingsPage.tsx` | Admin settings | `frontend` |

### API Services
| Service | File | Endpoint Base | Layer |
|---|---|---|---|
| `alertsService` | `api/alerts.service.ts` | `/alerts` | `frontend` |
| `analyticsService` | `api/analytics.service.ts` | `/analytics` | `frontend` |
| `brainService` | `api/brain.service.ts` | `/brain` | `frontend` |
| `capabilitiesService` | `api/capabilities.service.ts` | `/capabilities` | `frontend` |
| `costService` | `api/cost.service.ts` | `/cost` | `frontend` |
| `eventsService` | `api/events.service.ts` | `/events` | `frontend` |
| `governanceService` | `api/governance.service.ts` | `/governance` | `frontend` |
| `lineageService` | `api/lineage.service.ts` | `/lineage` | `frontend` |
| `memoryService` | `api/memory.service.ts` | `/memory` | `frontend` |
| `pluginService` | `api/plugin.service.ts` | `/plugins` | `frontend` |
| `qualityService` | `api/quality.service.ts` | `/quality` | `frontend` |
| `schemaService` | `api/schema.service.ts` | `/schema` | `frontend` |
| `agentRegistryService` | `api/agent-registry.service.ts` | `/agents` | `frontend` |

### Hooks
| Hook | File | Purpose | Layer |
|---|---|---|---|
| `useAsyncState` | `hooks/useAsyncState.ts` | Discriminated async state | `shared` |
| `useRouteEntryState` | `hooks/useRouteEntryState.ts` | URL search-param state | `shared` |
| `useValidation` | `components/hooks/useValidation.ts` | Form validation | `frontend` |
| `useWebSocket` | `components/hooks/useWebSocket.ts` | WS connection | `frontend` |
| `useCollectionData` | `features/collection/hooks/useCollectionData.ts` | Collection records | `product` |

---

## Migration Board

### Completed Migrations
| Task | From | To | Date | Status |
|---|---|---|---|---|
| AI-013: Remove AI branding | `BrainSidebar`, `AI Spotlight`, `AI Truth Snapshot` | `ContextSidebar`, `Assistance Spotlight`, `Model Telemetry` | 2026-04-23 | ✅ Done |
| DS-001: Remove dead PageShell | `PageShell.tsx` (0 imports) | Removed | 2026-04-23 | ✅ Done |
| A11Y-006: Accessible inputs | Raw inputs with placeholder-only | Added `aria-label` / visible labels | 2026-04-23 | ✅ Done |
| OPS-004: Wire real data | Empty stubs in `OperationsConsolePage` | `useCapabilityRegistry` + `useAlertsSummary` | 2026-04-23 | ✅ Done |

### Pending / In-Flight Migrations
| Task | Scope | Priority | Blocker |
|---|---|---|---|
| ADMIN-004: Settings backend CRUD | Java REST endpoints for profile, prefs, notifications, API keys | Medium | Needs Java implementation |
| Theme token hardening | Hardcoded colors → `@ghatana/theme` CSS variables | Medium | Design tokens not fully exported |
| Jotai state consolidation | Mixed Jotai + Zustand → Jotai only | Medium | Feature flag refactor |
| Monaco SQL editor | Textarea in `SqlWorkspacePage` → Monaco Editor | Low | `@yappc/code-editor` integration |

### Deprecated / Removed
| Item | Reason | Replacement |
|---|---|---|
| `PageShell.tsx` | Zero imports, superseded by `PageHeader`/`PageContent` | `PageLayout.tsx` |
| `BrainSidebar` export name | AI branding | `ContextSidebar` |
| `AISidebar` export name | AI branding | `ContextPanel` |
| `AISuggestion` export name | AI branding | `SuggestionCard` |
| `aiPowered` prop on `PageHeader` | AI branding | Removed |
| `aiSidebar` prop on `PageContent` | AI branding | `contextSidebar` |

---

## Adoption Targets

### Standardized Patterns (enforce in new code)
1. **Page structure**: `PageHeader` + `PageContent` from `PageLayout.tsx`
2. **Form inputs**: `LabeledInput` / `LabeledSelect` (never raw `<input placeholder="…" />`)
3. **Async data**: `useAsyncState` + discriminated union rendering
4. **Server state**: TanStack Query hooks (never manual `fetch` + `useEffect`)
5. **Error boundaries**: `AppErrorBoundary` or `RouteErrorBoundary`
6. **Doc tags**: Every exported symbol must have `@doc.type`, `@doc.purpose`, `@doc.layer`

### Component Reuse Priority
| Local Component | Reuse Target | Action |
|---|---|---|
| `BaseCard` (cards/) | `@ghatana/ui` Card | Migrate when tokens align |
| `StatusBadge` (common/) | `@ghatana/ui` Badge | Migrate when tokens align |
| `LoadingState` (common/) | `@ghatana/ui` Skeleton | Migrate when tokens align |
| `DashboardCard` (cards/) | Keep local (product-specific) | None |

---

## Health Signals

- **Test coverage target**: 80% critical paths
- **Lint policy**: Zero warnings, zero `any` types
- **Accessibility**: All inputs must have visible or screen-reader labels
- **Doc-tag compliance**: 100% of exported public symbols
