# Data Cloud Route Truth Matrix

> **Generated from**: `src/lib/routing/RouteCapabilityRegistry.ts` — `canonicalRouteRegistry`  
> **Rule (DC-UX-034)**: This document must stay in sync with the registry. If the registry is the source of truth, update the registry first; this doc is regenerated from it.

## Lifecycle Legend

| Lifecycle | Meaning |
|-----------|---------|
| `active` | Fully functional and fully implemented |
| `preview` | Functional but explicitly flagged as preview/partial |
| `boundary` | Route exists but surface is handled by `UnsupportedSurfaceBoundary` — reads as "not ready" to users |
| `deprecated` | Still routes but scheduled for removal |
| `redirect` | Redirects to another route |
| `removed` | No longer available |

## Route Matrix

| Key | Path | Label | Min Role | Lifecycle | Discoverable | Capabilities |
|-----|------|-------|----------|-----------|--------------|--------------|
| `home` | `/` | Home | primary-user | ✅ active | Yes | dashboard, overview |
| `data` | `/data` | Data | primary-user | ✅ active | Yes | data-explorer, collections, lineage |
| `pipelines` | `/pipelines` | Pipelines | primary-user | ✅ active | Yes | workflows, orchestration |
| `query` | `/query` | Query | primary-user | ✅ active | Yes | query, sql |
| `insights` | `/insights` | Insights | operator | ✅ active | Yes | analytics, ai-brain, cost |
| `trust` | `/trust` | Trust | operator | ✅ active | Yes | governance, compliance, audit |
| `events` | `/events` | Events | operator | ✅ active | Yes | event-stream, aep |
| `operations` | `/operations` | Operations | admin | ✅ active | Yes | ops-console, diagnostics |
| `entities` | `/entities` | Entities | operator | 🔶 preview | No | entity-browser |
| `context` | `/context` | Context | operator | 🔶 preview | No | context-explorer |
| `agents` | `/agents` | Agents | operator | 🔶 preview | No | agent-catalog |
| `alerts` | `/alerts` | Alerts | operator | 🚧 boundary | Yes* | alert-triage, monitoring |
| `memory` | `/memory` | Memory | operator | 🚧 boundary | No | memory-plane, context |
| `fabric` | `/fabric` | Data Fabric | operator | 🚧 boundary | No | data-fabric |
| `plugins` | `/plugins` | Plugins | primary-user | 🚧 boundary | Yes* | plugin-management |
| `settings` | `/settings` | Settings | admin | 🚧 boundary | No | settings |

> \* Discoverable=true in registry, but `getDiscoverableRoutes()` excludes `boundary` routes from navigation by default.  
> Pass `includesBoundary=true` to opt in (e.g., admin tools, audit dashboards).

## Role Access Summary

### primary-user
- **Active**: Home, Data, Pipelines, Query
- **Boundary (excluded from nav)**: Plugins

### operator (inherits primary-user)
- **Active**: Insights, Trust, Events
- **Preview**: Entities, Context, Agents
- **Boundary**: Alerts, Memory, Data Fabric

### admin (inherits operator)
- **Active**: Operations
- **Boundary**: Settings

## Surfaces Backed by UnsupportedSurfaceBoundary

These surfaces render a placeholder boundary component instead of a functional UI. They should NOT appear in primary navigation until promoted to `active`.

| Surface | Route | Registry Key | Boundary Reason |
|---------|-------|-------------|-----------------|
| Alerts | `/alerts` | `alerts` | alertsSurfaceBoundary — alert grouping/triage not implemented |
| Memory | `/memory` | `memory` | memoryPlaneBoundary — memory plane viewer not implemented |
| Data Fabric | `/fabric` | `fabric` | dataFabricMetricsBoundary — metrics surface not implemented |
| Plugins | `/plugins` | `plugins` | pluginDependencyBoundary — dependency resolution not implemented |
| Settings | `/settings` | `settings` | All 4 settings sections use UnavailablePanel |

## Keeping This Document in Sync

This matrix is manually maintained but derived from `canonicalRouteRegistry`. To regenerate it:

1. Update `src/lib/routing/RouteCapabilityRegistry.ts` first.
2. Run `getRoutesByLifecycle()` to get the grouped routes.
3. Update the tables above to match.

**CI gate (future)**: A snapshot test in `src/lib/routing/__tests__/RouteCapabilityRegistry.test.ts` asserts the matrix row count per lifecycle matches this document.

## Audit History

| Date | Change | DC-UX Item |
|------|--------|-----------|
| 2026-04 | Added `lifecycle` field to RouteCapabilitySchema | DC-UX-035 |
| 2026-04 | Corrected alerts, memory, fabric, plugins, settings to `boundary` | DC-UX-035 |
| 2026-04 | Created this document from registry | DC-UX-034 |
| 2026-04 | Added `getRoutesByLifecycle()` helper for doc generation | DC-UX-034 |
