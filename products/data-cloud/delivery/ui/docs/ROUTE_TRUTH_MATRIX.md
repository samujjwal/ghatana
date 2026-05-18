# Data Cloud Route Truth Matrix

> **Generated from**: `src/lib/routing/RouteSurfaceRegistry.ts` — `canonicalRouteSurfaceRegistry`
> **Generation command**: `pnpm --filter @data-cloud/ui docs:routes:generate`

## Route Matrix

| Key                      | Path                        | Label         | Min Role     | Lifecycle   | Discoverable | Capabilities                                             |
| ------------------------ | --------------------------- | ------------- | ------------ | ----------- | ------------ | -------------------------------------------------------- |
| `home`                   | `/`                         | Home          | primary-user | ✅ active   | Yes          | dashboard, overview                                      |
| `data`                   | `/data`                     | Data          | primary-user | ✅ active   | Yes          | data-explorer, collections, lineage                      |
| `connectors`             | `/connectors`               | Connectors    | operator     | ✅ active   | Yes          | data-connectors, external-data-sources                   |
| `pipelines`              | `/pipelines`                | Pipelines     | primary-user | ✅ active   | Yes          | workflows, plugin-execution                              |
| `query`                  | `/query`                    | Query         | primary-user | ✅ active   | Yes          | query, sql                                               |
| `insights`               | `/insights`                 | Insights      | operator     | ✅ active   | Yes          | analytics, automation-insights, cost                     |
| `trust`                  | `/trust`                    | Trust         | operator     | ✅ active   | Yes          | governance, compliance, audit                            |
| `events`                 | `/events`                   | Events        | operator     | ✅ active   | Yes          | event-stream, aep                                        |
| `alerts`                 | `/alerts`                   | Alerts        | operator     | 🚧 boundary | Yes          | alert-triage, monitoring                                 |
| `memory`                 | `/memory`                   | Memory        | operator     | 🔶 preview  | Yes          | memory-plane, context                                    |
| `entities`               | `/entities`                 | Entities      | operator     | 🔶 preview  | Yes          | entity-browser                                           |
| `context`                | `/context`                  | Context       | operator     | 🔶 preview  | Yes          | context-explorer                                         |
| `fabric`                 | `/fabric`                   | Data Fabric   | operator     | 🚧 boundary | Yes          | data-fabric                                              |
| `agents`                 | `/agents`                   | Agents        | operator     | 🔶 preview  | Yes          | agent-catalog                                            |
| `operations`             | `/operations`               | Operations    | admin        | ✅ active   | Yes          | ops-console, diagnostics                                 |
| `operationsJobs`         | `/operations/jobs`          | Job Center    | admin        | ✅ active   | No           | ops-jobs, background-operations                          |
| `operationsReleaseTruth` | `/operations/release-truth` | Release Truth | admin        | ✅ active   | Yes          | capability-registry, governance.audit, health.eventStore |
| `plugins`                | `/plugins`                  | Plugins       | operator     | ✅ active   | Yes          | plugin-management                                        |
| `settings`               | `/settings`                 | Settings      | admin        | 🚧 boundary | No           | settings                                                 |

## Lifecycle Counts

- active: 12
- preview: 4
- boundary: 3
- deprecated: 0
- redirect: 0
- removed: 0

## Notes

- Boundary routes are intentionally hidden from standard navigation by `getDiscoverableRoutes()` unless `includesBoundary=true` is passed.
- Update the canonical registry first; this document is generated from registry state.
