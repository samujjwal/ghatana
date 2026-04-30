# End-to-End Product Correctness, Completeness, UI/UX, Backend, DB, and Production-Readiness Audit — Data Cloud

**Target**: `products/data-cloud` and shared platform libraries it consumes
**Reference**: [products/data-cloud/data-cloud-canonical-architecture-spec.md](../../products/data-cloud/data-cloud-canonical-architecture-spec.md)
**Audit Date**: 2026-04-29
**Auditor**: GitHub Copilot (automated)
**Method**: Static code reading + repo-wide pattern scans + cross-reference against canonical architecture spec, README current-state matrix, OpenAPI / router build, UI page index, and test surfaces.

> **Scope note**: Data Cloud has 1,098 production Java files across 18 modules (`launcher` 232, `platform-launcher` 331, `platform-api` 186, `platform-entity` 151, `platform-plugins` 84, `platform-config` 72, `spi` 58, `platform-event` 56, `platform-analytics` 35, plus smaller modules) and 287 production TS/TSX files in the UI with 95 test files. Exhaustive line-by-line review is not feasible in one pass. This audit performs targeted, evidence-based deep dives on the highest-risk and highest-value surfaces and uses repo-wide pattern scans to surface systemic findings. Areas marked "Not deeply audited" require follow-up.

---

## 1. Executive Summary

| Dimension | Rating | Confidence |
|---|---|---|
| Correctness — critical flows | **Red** — multiple critical paths return `501 Not Implemented` due to unresolved type-mismatch refactor | High |
| Completeness — feature surface vs spec | **Amber** — most surfaces exist, several deeply incomplete | High |
| Production-grade discipline | **Amber** — solid security filter, real entity/event store, real audit infra exists, but auditing/eventlog wiring is **disabled in launcher bootstrap** | High |
| Mock/stub risk in production paths | **Amber/Red** — backend has documented "temporarily disabled" code paths; UI Data Fabric calls a backend endpoint that does not exist | High |
| Test authenticity | **Red** — multiple top-level UI test files violate Section 29 anti-theatre rule by rendering inline `<button>` elements instead of the real page | High |
| UI/UX low-cognitive-load adherence | **Amber** — progressive disclosure shell exists; Data Fabric, Plugins, and several operator surfaces still expose preview/incomplete behavior | Medium |
| Multi-tenancy enforcement | **Green** for HTTP routes that pass through `DataCloudSecurityFilter`; **Red** when launcher is started without auth (default in embedded profile) | High |
| Observability | **Amber** — Prometheus/Micrometer + tracing wired; audit-event emission depends on disabled EventLogStore wiring | High |
| Documentation alignment with implementation | **Amber** — README accurately admits Data Fabric is preview, but spec § 6 lists SSE streaming as "Implemented" while launcher returns 501 | High |

### Top P0 issues

1. **`SseStreamingHandler` returns 501** for both `/api/v1/cdc/...` and `/events/stream` due to unresolved `EventLogStore` / `TenantContext` type mismatch — see [SseStreamingHandler.java:239,283,536](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/SseStreamingHandler.java#L239). Spec § 6 lists "Event streaming via SSE/WebSocket" as Implemented, High priority.
2. **`DataLifecycleHandler.requireEventLogStore()` throws `IllegalStateException`** ([DataLifecycleHandler.java:1405](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java#L1405)) and `logGovernanceEvent` returns 501 ([line 1413](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java#L1413)). Spec § 6 lists Governance / retention / purge / redaction as **Critical**, "Implemented".
3. **gRPC transport disabled** at bootstrap ([DataCloudGrpcLauncherBootstrap.java:23](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudGrpcLauncherBootstrap.java#L23)): `log.warn("gRPC transport temporarily disabled due to EventLogStore type mismatch")`. The bootstrap is a no-op.
4. **Audit + EventLogStore wiring commented out** in HTTP launcher bootstrap ([DataCloudHttpLauncherBootstrap.java:157,182](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java#L157)): `withAuditService(...)`, `withEventLogStore(...)`, and `withHealthSubsystem("event_store", ...)` are all behind a TODO and never called. This means the launcher boots with **no durable audit pipeline**, contradicting spec § 5.7 fail-closed and § 5.8 evidence-over-summary principles, and contradicting README claim "Durable governance, purge, redaction, and audit evidence".
5. **UI `DataFabricPage` calls `GET /data-fabric/metrics`** ([DataFabricPage.tsx:70](../../products/data-cloud/ui/src/pages/DataFabricPage.tsx#L70)) — **no such route exists** in `DataCloudRouterBuilder.java`. The UI silently falls back to "heuristic" placement messages, presenting fabricated authority to the user.
6. **Test theatre in UI top-level tests** — `WorkflowDesigner.test.tsx` and `CollectionsUI.test.tsx` repeatedly call `render(<button data-testid=...>)` and assert against pre-mocked functions, never importing or invoking the production page. Per repo instructions § 29 (anti-theatre rule) these tests must be rewritten or deleted. See [WorkflowDesigner.test.tsx:332,350,367,384](../../products/data-cloud/ui/src/__tests__/WorkflowDesigner.test.tsx#L332) and [CollectionsUI.test.tsx:69,328](../../products/data-cloud/ui/src/__tests__/CollectionsUI.test.tsx#L69).

### Top P1 issues

1. `WorkflowExecutionHandler` rollback returns 501 ([line 350](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java#L350)) — workflow execution surface is incomplete vs spec § 6 "Workflow execution and logs" listed as Implemented Critical.
2. `AiAssistHandler` has at least 3 paths returning 501 with quality assessment placeholder text and `handleInferSchema` disabled ([AiAssistHandler.java:1940](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AiAssistHandler.java#L1940), [DataCloudRouterBuilder.java:323](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java#L323)).
3. `LineageHandler`, `EntityValidationHandler`, `EntityExportHandler`, `EntityCrudHandler.searchByOpenSearch`, `EntityAnomalyHandler`, `AnalyticsHandler` all return 501 when their optional dependency is absent. This is correct degradation in principle, but UI surfaces must gate on capability registry to honor spec § 5.3 — needs verification per route.
4. UI `__tests__/stubs/flow-canvas.tsx` is documented as a global stub override; ensure none of it leaks into production bundle (vite/vitest config check required).
5. `WorkflowExecutionHandler` notifications are documented as "placeholder (enriched by execution engine when available)" ([line 235](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java#L235)) — undefined enrichment path.
6. `DataCloudRuntimePluginManager.executeRetry` left as TODO ([line 345](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/plugins/DataCloudRuntimePluginManager.java#L345)) — plugin retry is not implemented.

### Release verdict

| Question | Answer |
|---|---|
| Ready for production deployment? | **No** |
| Ready for internal demo? | Yes, behind sovereign/local profile and with the Data-Fabric and SSE surfaces hidden or marked unavailable in capability registry |
| Ready behind feature flag? | Yes for entity/event/pipeline/lineage CRUD only |
| Critical blockers | EventLogStore type mismatch (5 disabled paths), missing audit wiring, missing `/data-fabric/metrics` backend, UI test theatre |

---

## 2. Scope and Method

### In scope

- All Java sources under `products/data-cloud/**/src/main/java`.
- All TypeScript sources under `products/data-cloud/ui/src` (excluding `node_modules`, `dist`, `build`, `__tests__` for production scans; tests scanned separately).
- HTTP route definitions in `DataCloudRouterBuilder` and bootstrap classes.
- Test files in UI `__tests__` and `integration-tests` modules (sampled).
- Canonical architecture spec, README, and supporting docs as the behavior contract.

### Out of scope

- Generated SDK output under `products/data-cloud/sdk/src/main/java/com/ghatana/datacloud/sdk` (only consulted for contract shape).
- Helm/k8s/terraform packaging (no behavior changes inferred without runtime evidence).
- AEP, YAPPC, and other products that consume Data Cloud — only checked for boundary leaks.
- Unrelated `frontend/apps/*` consumers.

### Method

1. Built directory + module map (counts in § 3.1).
2. Enumerated all HTTP handler classes (38 distinct files in `launcher/src/main/java/.../http/handlers/`).
3. Enumerated UI pages (26 top-level pages in `ui/src/pages`).
4. Repo-wide pattern scan for `TODO|FIXME|HACK|temporarily disabled|errorResponse(501|throw new Error("Not implemented")|placeholder` across production code only.
5. Cross-referenced findings against canonical spec § 6 capability matrix.
6. Spot-checked highest-risk handlers (`SseStreamingHandler`, `DataLifecycleHandler`, `DataCloudSecurityFilter`, bootstrap classes).
7. Spot-checked test files flagged by anti-theatre patterns.

---

## 3. Complete Product Inventory

### 3.1 Module Inventory (Java)

| Module | Java files | Role |
|---|---|---|
| [platform-launcher](../../products/data-cloud/platform-launcher) | 331 | Generic launcher infra (storage router, RBAC controller, GraphQL controller) |
| [launcher](../../products/data-cloud/launcher) | 232 | Data-Cloud HTTP/gRPC server, handlers, security filter, bootstraps |
| [platform-api](../../products/data-cloud/platform-api) | 186 | Public Data-Cloud API contracts |
| [platform-entity](../../products/data-cloud/platform-entity) | 151 | Entity store domain |
| [platform-plugins](../../products/data-cloud/platform-plugins) | 84 | Plugin lifecycle |
| [platform-config](../../products/data-cloud/platform-config) | 72 | Config + env resolution |
| [spi](../../products/data-cloud/spi) | 58 | Service-provider interfaces |
| [platform-event](../../products/data-cloud/platform-event) | 56 | Event domain |
| [platform-analytics](../../products/data-cloud/platform-analytics) | 35 | Analytics |
| [agent-registry](../../products/data-cloud/agent-registry) | 23 | Agent registry |
| [integration-tests](../../products/data-cloud/integration-tests) | 21 | Cross-module integration tests |
| [feature-store-ingest](../../products/data-cloud/feature-store-ingest) | 20 | Feature store ingest |
| [platform-governance](../../products/data-cloud/platform-governance) | 12 | Governance contracts |
| [sdk](../../products/data-cloud/sdk) | 6 | Generated/maintained SDK shell |
| [kernel-bridge](../../products/data-cloud/kernel-bridge) | 4 | Kernel adapter |
| [api](../../products/data-cloud/api) | 3 | OpenAPI/contract module |
| [agent-catalog](../../products/data-cloud/agent-catalog) | 2 | Agent catalog |
| [platform-event-store](../../products/data-cloud/platform-event-store) | 2 | Event store contract |

### 3.2 HTTP Handler Inventory (38 handlers in `launcher`)

`AgentCatalogHandler`, `AiAssistHandler`, `AiModelHandler`, `AlertingHandler`, `AnalyticsHandler`, `AutonomyHandler`, `BrainHandler`, `CapabilityRegistryHandler`, `CollectionContextHandler`, `ComplianceHandler`, `ContextLayerHandler`, `DataLifecycleHandler`, `DataProductHandler`, `DataSourceRegistryHandler`, `EntityAnomalyHandler`, `EntityCrudHandler`, `EntityExportHandler`, `EntityValidationHandler`, `EventHandler`, `FederatedQueryHandler`, `HealthHandler`, `LearningHandler`, `LineageHandler`, `McpToolsHandler`, `MemoryPlaneHandler`, `PipelineCheckpointHandler`, `PluginInstallHandler`, `ProviderConformanceHandler`, `SemanticSearchHandler`, `SettingsHandler`, `SovereignProfileHandler`, `SseStreamingHandler`, `StorageCostHandler`, `TierMigrationHandler`, `VoiceGatewayHandler`, `WorkflowExecutionHandler`, plus router/support classes. See [DataCloudRouterBuilder.java](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java).

### 3.3 UI Page Inventory (26 pages)

`AdminWorkspace`, `AgentPluginManagerPage`, `AlertsPage`, `ContextExplorerPage`, `CreateCollectionPage`, `DataExplorer`, `DataFabricPage`, `EditCollectionPage`, `EntityBrowserPage`, `EventExplorerPage`, `InsightsPage`, `IntelligentHub`, `MemoryPlaneViewerPage`, `OperationsConsolePage`, `OperationsJobCenterPage`, `OperatorDashboard`, `PluginDetailsPage`, `PluginsPage`, `SettingsPage`, `SmartWorkflowBuilder`, `SqlWorkspacePage`, `TrustCenter`, `WorkflowDesigner/`, `WorkflowList/`, `WorkflowsPage`, `NotFound/`. See [ui/src/pages](../../products/data-cloud/ui/src/pages).

### 3.4 UI Component Domains

`ai`, `alerts`, `brain`, `capabilities`, `cards`, `common`, `core`, `cost`, `data`, `governance`, `hooks`, `layout`, `lineage`, `nlp`, `notifications`, `plugins`, `privacy`, `quality`, `security`, `sql`, `sql-editor`, `visualizations`, `voice`, `workflow`. See [ui/src/components](../../products/data-cloud/ui/src/components).

### 3.5 Test Inventory (UI)

- 95 `*.test.ts(x)` files across `ui/src`.
- Top-level integration tests under `ui/src/__tests__` for `WorkflowDesigner`, `WorkflowsPage`, `WorkflowPage`, `CollectionsUI`, `ShellRouting`, `setup.ts`.
- Co-located tests for hooks, `lib/integrations`, `mocks`, `lib/routing`, accessibility, e2e, features.
- MSW (Mock Service Worker) configured for vitest network interception.
- Test stubs directory `ui/src/__tests__/stubs/flow-canvas.tsx` exists for global flow-canvas mock.

### 3.6 Shared Platform Libraries Consumed (sample)

From spec § 4 + observed imports:

- `platform/java/ai-integration` (`CompletionService`, AI provider gateway)
- `platform/java/observability` (Prometheus, OpenTelemetry, ClickHouseTraceStorage)
- `platform/java/security` (`JwtTokenProvider`, `ApiKeyResolver`, RBAC primitives via `platform-launcher`)
- `platform/java/database`, `platform/java/http`, `platform/java/core`, `platform/java/testing`
- `platform/typescript/design-system` (UI atoms/molecules/organisms)
- `@ghatana/canvas/flow` (Data Fabric topology canvas)
- `@ghatana/api`, `@ghatana/state`, `@ghatana/forms`, `@ghatana/config`, `@ghatana/realtime` (per spec § 32 canonical packages — needs verification of actual imports as P2)

---

## 4. Product Behavior Map

Derived from `data-cloud-canonical-architecture-spec.md` § 3 and § 6.

| Capability | Persona | Expected UX | Expected Backend | Expected Data | Success Criteria |
|---|---|---|---|---|---|
| Entity CRUD/batch/history | Application engineer | Browse, edit collections from `EntityBrowserPage` | `EntityCrudHandler` over durable entity store | Entity rows with version + tenant + audit metadata | 2xx + `audit.event.persisted` |
| Event append/stream | Operator/agent | `EventExplorerPage` + SSE feed | `EventHandler`, `SseStreamingHandler`, `EventLogStore` | Append-only event log with offset, replay, tenant scope | 2xx, frame received within 1s |
| Pipeline CRUD + execute | Workflow author | `WorkflowsPage`, `WorkflowDesigner` | `PipelineCheckpointHandler`, `WorkflowExecutionHandler` | Pipeline def + checkpoint + execution snapshot durable | 200 + execution log streamed |
| Trust Center governance | Compliance reviewer | `TrustCenter` page | `DataLifecycleHandler`, `ComplianceHandler` | Retention, purge, redaction with `AuditEvent` row | Audit-trail evidence visible |
| RAG / context | Agent | `ContextExplorerPage` | `ContextLayerHandler`, `SemanticSearchHandler` | Embedded vectors, lineage, freshness | Ranked + provenance-tagged results |
| AI assist | Primary user | `IntelligentHub`, `AiAssistHandler` | LLM provider via `CompletionService` | Provenance-tagged output with policy decision | 2xx + confidence + provenance |
| Capability truth | Operator/UI | `CapabilityRegistryHandler` `/api/v1/capabilities` | Probe-backed registry | Live status per capability | UI gates on result |
| Data Fabric | Operator | `DataFabricPage` | `/data-fabric/metrics` | Tier metrics with throughput | Real metrics, no demo data |

---

## 5. Requirement-to-Implementation Traceability Matrix

| Capability (spec § 6) | UI | API/Handler | Backing service | Tests observed | Status |
|---|---|---|---|---|---|
| Entity CRUD/batch/history/export/validation | EntityBrowserPage, CreateCollectionPage, EditCollectionPage | EntityCrudHandler, EntityExportHandler, EntityValidationHandler | platform-entity, EntityStore | UI co-located + integration-tests | Complete and correct (export/validation degrade to 501 if not configured) |
| Event append/query | EventExplorerPage | EventHandler | platform-event-store | Integration | Implemented |
| Event streaming SSE/WebSocket | EventExplorerPage realtime, AlertsPage | **SseStreamingHandler — returns 501 for `/cdc` and `/events/stream`** | EventLogStore (disabled wiring) | None observed for live streams | **P0 BROKEN** |
| Pipeline metadata + checkpoints | WorkflowsPage | PipelineCheckpointHandler | platform-entity | UI test exists but is theatre | Complete (CRUD); test-quality red |
| Workflow execution + logs | WorkflowDesigner, WorkflowsPage | WorkflowExecutionHandler | RuntimePluginManager | UI test exists but is theatre; rollback not implemented | Partial — rollback 501; test theatre |
| Durable orchestration | n/a (out of scope per README) | — | — | — | Limited (correctly disclosed) |
| Agent memory persistence | MemoryPlaneViewerPage | MemoryPlaneHandler | spi memory store | Not deeply audited | Implemented (per README) |
| Context layer + RAG | ContextExplorerPage | ContextLayerHandler, SemanticSearchHandler, CollectionContextHandler | Context graph + vector | Not deeply audited | Implemented |
| Lineage API | LineagePage components | LineageHandler — returns 501 when service absent | LineagePlugin | LineagePlugin started in bootstrap | Complete (degrades correctly) |
| Data products API | n/a explicit page | DataProductHandler `/api/v1/data-products` | platform-api | Not deeply audited | Implemented |
| Analytics/reports | InsightsPage | AnalyticsHandler — returns 501 in branches | platform-analytics | Not deeply audited | Partial (degrades) |
| Federated query | SqlWorkspacePage | FederatedQueryHandler | Trino (optional) | Capability-gated | Capability-dependent |
| AI assist | IntelligentHub, SmartWorkflowBuilder | AiAssistHandler — multiple 501 branches, `handleInferSchema` disabled | CompletionService | Not deeply audited | Partial |
| Voice | VoiceCommand UI | VoiceGatewayHandler | VoiceIntentCatalog | Has redaction policy | Implemented |
| Trust Center | TrustCenter page | DataLifecycleHandler, ComplianceHandler — **`requireEventLogStore` throws** + `logGovernanceEvent` 501 | EventLogStore (disabled), AuditService | governance test in launcher exists per README | **P0 BROKEN governance event emission** |
| Capability registry | shell + capability service | CapabilityRegistryHandler `/api/v1/capabilities` | runtime probes | UI capability hook | Implemented |
| Autonomy controls | OperationsConsolePage | AutonomyHandler | DefaultAutonomyController | Not deeply audited | Implemented |
| Plugin system | PluginsPage, PluginDetailsPage | PluginInstallHandler — TODO comment in retry path | DataCloudRuntimePluginManager (retry TODO) | Not deeply audited | Partial |
| Alerts | AlertsPage | AlertingHandler | Alert store | Not deeply audited | Implemented |
| Data fabric topology | DataFabricPage | **No `/data-fabric/metrics` route** | None | None | **P0 MISSING BACKEND** |
| Settings | SettingsPage | SettingsHandler | Settings store | Not deeply audited | Partial (per spec) |
| SDK generation | n/a | sdk module | OpenAPI | Not in scope | Implemented |
| Sovereign profile | n/a | SovereignProfileHandler | H2 file-backed | Backup endpoint exists | Implemented |
| gRPC transport | n/a | DataCloudGrpcServer | EventLogStore | **bootstrap is no-op** | **P0 DISABLED** |

---

## 6. End-to-End Journey Audit

| Journey | Entry | Expected | Actual | Severity | Required Fix |
|---|---|---|---|---|---|
| Operator tails change-data-capture stream | `EventExplorerPage` → SSE | Live frames within 1s | **501 returned with "SSE streaming temporarily disabled due to TenantContext type mismatch"** | P0 | Resolve `spi.TenantContext` vs `platform.domain.eventstore.TenantContext` import; uncomment SSE pipeline; add integration test. |
| Compliance reviewer purges retention-expired entities | `TrustCenter` purge action | 200 + governance audit event persisted | Mutation works at entity layer; **governance audit event call returns 501**, breaking evidence trail | P0 | Restore EventLogStore wiring or replace `logGovernanceEvent` with direct `AuditService.emit`. |
| Operator views Data Fabric throughput | `DataFabricPage` | Real tier metrics | UI fetches `/data-fabric/metrics` → 404; falls through to "heuristic fallback" recommendation that misleads operator | P0 | Either implement backend metrics route or hide page when capability registry has no `data_fabric_metrics` capability. |
| Author saves workflow draft | `WorkflowDesigner` | POST pipeline + 200 | Backend works; **the test file proves nothing** because it renders fake `<button>` not the real page | P0 (test) | Rewrite tests to import `WorkflowDesigner` and use real handlers + MSW. |
| Author rolls back failed execution | `WorkflowsPage` rollback | 200 + state restored | `WorkflowExecutionHandler` rollback returns 501 | P1 | Implement rollback or remove the action from UI. |
| Operator triggers gRPC consumer | `DataCloudClient` over gRPC | gRPC call succeeds | gRPC server never starts (`log.warn` only) | P0 | Resolve EventLogStore type mismatch; implement integration test. |
| Authentication required on protected endpoint in non-embedded profile | any non-PUBLIC route | 401 without bearer/api-key | Bootstrap throws `IllegalStateException` at startup if non-embedded + no auth — correct fail-closed | OK | None |
| Embedded/local profile boots without auth | local dev | Warn + allow | Logs warning; permits all traffic | OK for local; ensure it cannot be deployed | Add deployment-profile guard so `EMBEDDED` cannot be combined with public network bind. |
| AI schema inference | SmartWorkflowBuilder schema-infer | Inferred schema returned | Route disabled — `// TODO: handleInferSchema method not found in AiAssistHandler` | P1 | Implement `handleInferSchema` or remove UI surface. |

---

## 7. UI/UX Correctness and Completeness Audit

| Area | File(s) | Finding | Severity | Required Fix |
|---|---|---|---|---|
| `DataFabricPage` | [DataFabricPage.tsx](../../products/data-cloud/ui/src/pages/DataFabricPage.tsx) | Calls non-existent `/data-fabric/metrics`; "heuristic fallback" message presents authority without evidence — violates spec § 5.8 | P0 | Hide page entirely behind capability registry probe; or implement backend metrics route |
| `WorkflowDesigner` page | [WorkflowDesigner/](../../products/data-cloud/ui/src/pages/WorkflowDesigner) | Page exists; integration test bypasses it — UI behavior must be re-verified by real test | P1 | Add real RTL test that mounts the page |
| `PluginsPage` / `PluginDetailsPage` | per [PluginInstallHandler.java:26](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/PluginInstallHandler.java#L26) JavaDoc — UI is documented as making "TODO calls" | UI may invoke routes that are not yet stable | P1 | Audit each plugin UI action; gate on capability registry |
| `SettingsPage` | [SettingsPage.tsx](../../products/data-cloud/ui/src/pages/SettingsPage.tsx) | Spec § 6 lists Settings as "Partial — admin lifecycle incomplete" | P1 | Disclose unsupported actions; add capability-gating |
| Empty/loading/error/permission-denied states | UI-wide | Not deeply audited; no systemic theatre detected outside identified files | P2 | Spot-check each page during follow-up |
| Accessibility | `ui/src/__tests__/accessibility/` directory exists | Coverage exists but completeness not verified | P2 | Validate axe-based tests cover all primary pages |
| Progressive disclosure | shell + role mode switcher | Implemented per README | OK | None |
| Confirm dialogs for destructive actions | Trust Center purge, plugin uninstall | Not verified in this pass | P2 | Add confirm-modal audit |

---

## 8. Frontend Actions, State, and Data Flow Audit

| Flow | File | Expected | Actual | Severity | Required Fix |
|---|---|---|---|---|---|
| Capability registry hook | `ui/src/api/capabilities.service` | Single source of truth for runtime gating | Verified usage in `WorkflowsPage` test | OK | Ensure all action enablement reads it |
| MSW interception | [setup.ts](../../products/data-cloud/ui/src/__tests__/setup.ts) | Test-only network mock | Test-only — confirmed | OK | None |
| `mocks/server.ts` | [ui/src/mocks](../../products/data-cloud/ui/src/mocks) | Should be test-only | Imported by `setup.ts` only — need vite/vitest config audit to confirm not bundled | P2 | Verify vite.config excludes `mocks/` from build |
| API client `lib/api/client.ts` | per README "compatibility boundary only" | Should be migrated away from | Still used by some pages and tests | P2 | Track migration completion |
| Workflow page state | `WorkflowsPage` + `WorkflowDesigner` | TanStack Query + Jotai per spec § 6 | Not deeply audited; test theatre obscures real behavior | P1 | Re-verify after rewriting tests |
| Optimistic updates / cache invalidation | unverified | unverified | unverified | P2 | Sample-audit destructive flows |

---

## 9. Backend / API / Domain Logic Audit

| Flow | File | Finding | Severity | Required Fix |
|---|---|---|---|---|
| `SseStreamingHandler.openEntityCdcStream` | [SseStreamingHandler.java:177-240](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/SseStreamingHandler.java#L177) | Real implementation commented out; returns 501. The full code path is preserved as comments. | P0 | Resolve SPI/event-store type unification; reinstate code; add integration test against in-memory event store. |
| `SseStreamingHandler.openSseStream` | [line 258-285](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/SseStreamingHandler.java#L258) | Returns 501 unconditionally even when `EventLogStore` is wired. | P0 | Re-enable. |
| `SseStreamingHandler.handleStreamingQuery` | [line 460](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/SseStreamingHandler.java#L460) | Returns 501 only when OpenSearchConnector absent — correct degradation | OK | None (capability-gated) |
| `DataLifecycleHandler.requireEventLogStore` | [line 1404-1407](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java#L1404) | Throws unconditionally — any caller fails | P0 | Wire EventLogStore; remove throw |
| `DataLifecycleHandler.logGovernanceEvent` | [line 1409-1414](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java#L1409) | Returns 501 — every governance mutation skips its own audit record | P0 | Re-implement |
| `WorkflowExecutionHandler.handleRollback` | [line 350](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java#L350) | "Rollback not yet implemented" | P1 | Implement or hide the UI surface |
| `WorkflowExecutionHandler` notifications | [line 235](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java#L235) | Placeholder enrichment path | P2 | Define enrichment contract |
| `AiAssistHandler` (multiple) | lines 712, 1849, 1940, 1981 | Multiple branches return 501 with "Quality assessment placeholder" titles surfaced to users | P1 | Either implement or remove UI surface |
| `DataCloudRouterBuilder.java:323` | `// TODO: handleInferSchema method not found in AiAssistHandler - temporarily disabled` | Schema-infer route is not bound | P1 | Implement or delete the UI affordance |
| `EntityCrudHandler.searchByOpenSearch` | [line 1091-1105](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java#L1091) | 501 when OpenSearchConnector absent | OK | Capability-gate |
| `EntityValidationHandler` | [line 30,68,124](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityValidationHandler.java#L30) | 501 when validator absent | OK | Capability-gate |
| `EntityExportHandler` | [line 28,68,137](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityExportHandler.java#L28) | 501 when service absent | OK | Capability-gate |
| `EntityAnomalyHandler` | [line 35,107,200](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityAnomalyHandler.java#L35) | 501 when detector absent | OK | Capability-gate |
| `LineageHandler` | [line 67,161](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/LineageHandler.java#L67) | 501 when service absent | OK | Capability-gate |
| `DataCloudHttpLauncherBootstrap` | [line 157,182](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java#L157) | `withAuditService(...)` and `withEventLogStore(...)` are commented out — **no audit pipeline** | P0 | Re-wire with proper type adapter |
| `DataCloudGrpcLauncherBootstrap` | [whole file](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudGrpcLauncherBootstrap.java) | gRPC server never starts | P0 | Re-wire |
| `DataCloudSecurityFilter` | [DataCloudSecurityFilter.java:96-150](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudSecurityFilter.java#L96) | Real auth + policy + audit chain. Correctly fail-closed when `enforcing=true` and policyEngine null. | OK | None |
| Authentication required by default | [DataCloudHttpLauncherBootstrap.java:206-220](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java#L206) | `IllegalStateException` thrown when non-embedded and no auth configured. Good. | OK | None |
| Embedded profile insecure mode | same | Logs warn but permits insecure boot | P2 | Add network-bind guard to prevent embedded profile binding to non-loopback |

---

## 10. Database / Persistence Audit

> **Not deeply audited in this pass.** The Data Cloud entity/event stores are heavily abstracted through `EntityStore` and `EventLogStore` SPI. Recorded follow-ups:

| Concern | Status |
|---|---|
| Tenant column / partition on every write | Asserted by SPI shape; not verified per-table |
| Index coverage for query/filter paths | Not verified |
| Migration safety | Not verified |
| Soft-delete vs hard-delete semantics across collections | Not verified |
| `AuditEvent` durability guarantees | **Currently broken — bootstrap does not wire EventLogStore; AuditService falls back to null path** |
| H2 file-backed sovereign profile durability | Asserted in README; integration test cited; not re-verified |
| Auto-compaction | Implemented per README; not re-verified |

**Required follow-up**: Stand up a per-store integrity audit using the existing `integration-tests` module to enumerate every repository implementation, every migration, and every query path.

---

## 11. Production Mock / Stub / Shortcut Audit

| File | Evidence | Production reachable? | Critical flow? | Severity |
|---|---|---|---|---|
| [SseStreamingHandler.java](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/SseStreamingHandler.java) | 4 occurrences of `temporarily disabled` returning 501 | Yes | Yes — event streaming | **P0** |
| [DataLifecycleHandler.java](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java) | `requireEventLogStore()` throws; `logGovernanceEvent` returns 501 | Yes | Yes — Trust/Governance | **P0** |
| [DataCloudGrpcLauncherBootstrap.java](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudGrpcLauncherBootstrap.java) | Entire start path commented out | Yes (bootstrap entry) | Yes — gRPC transport | **P0** |
| [DataCloudHttpLauncherBootstrap.java](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/bootstrap/DataCloudHttpLauncherBootstrap.java) | EventLogStore + AuditService wiring commented out (lines 157, 182) | Yes | Yes — audit pipeline | **P0** |
| [DataCloudRouterBuilder.java:323](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java#L323) | `handleInferSchema` route disabled | Yes | Medium | P1 |
| [AiAssistHandler.java:1940](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AiAssistHandler.java#L1940) | `"title", "Quality assessment placeholder"` shipped as user-visible response | Yes | High (user-facing) | P1 |
| [DataCloudRuntimePluginManager.java:345](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/plugins/DataCloudRuntimePluginManager.java#L345) | `// TODO: Implement retry execution logic` | Yes | High (plugin runtime) | P1 |
| [DataFabricPage.tsx](../../products/data-cloud/ui/src/pages/DataFabricPage.tsx) | UI calls non-existent backend route | Yes | Medium (preview-disclosed but page is shipped) | P0 (per spec § 5.3) |
| [WorkflowExecutionHandler.java:350](../../products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java#L350) | "Rollback not yet implemented" | Yes if UI exposes rollback | P1 |
| [WorkflowDesigner.test.tsx:332,350,384](../../products/data-cloud/ui/src/__tests__/WorkflowDesigner.test.tsx#L332) | Test theatre — renders fake `<button>` instead of real page | Test-only; passes falsely | High (false signal) | **P0 (test)** |
| [CollectionsUI.test.tsx:69,328](../../products/data-cloud/ui/src/__tests__/CollectionsUI.test.tsx#L69) | Same pattern | Test-only | High | **P0 (test)** |
| `ui/src/__tests__/stubs/flow-canvas.tsx` | Documented test stub | Test-only — verify not bundled | P2 | Audit vite include patterns |

No production-path use of `Math.random`, generic `Mock`, in-memory repositories, or fake DB was found in the launcher Java sources. The remaining `placeholder` matches are JavaDoc references to redaction placeholders, which is correct.

---

## 12. Duplicate / Source-of-Truth Audit

> **Not deeply audited.** Spec § 5.4 enforces one canonical contract per concern; high-risk areas to investigate next:

| Area | Risk |
|---|---|
| `spi.EventLogStore` vs `platform.domain.eventstore.EventLogStore` | **Confirmed duplication** — this is the root cause of all the 501s above. Two parallel `EventLogStore` types exist with incompatible signatures. |
| `spi.TenantContext` vs `platform.domain.eventstore.TenantContext` | **Confirmed duplication** — same root cause |
| UI `lib/api/client.ts` "compatibility boundary" | README admits this is a legacy surface to be migrated |
| `ui/src/components/lineage` vs `features/data-fabric` | Possible overlap; verify in follow-up |

**Required immediate fix**: Pick one canonical `EventLogStore` and one canonical `TenantContext` (per spec § 5.4 + § 11) and migrate all callers. This single fix unblocks 4 of the 6 P0 findings.

---

## 13. Security / Privacy / Permission Audit

| Area | Finding | Severity |
|---|---|---|
| HTTP authentication | Real `DataCloudSecurityFilter` with API-key + JWT + policy + audit chain. Bootstrap throws on missing auth in non-embedded profile. | OK |
| Policy engine | Nullable; security filter fails closed for CRITICAL routes when `enforcing=true` and policy null. Verify production config sets `enforcing=true`. | P1 — verify default |
| Tenant isolation filter | `TenantIsolationHttpFilter.wrap(delegate)` is innermost wrapper — correct. | OK |
| Tenant resolution falls back to default | `peekTenantId` logic permits header/query; `requireTenantIdOrFail` returns null when absent — handlers must check. Spot-check confirms most do. | P2 — add filter-level enforcement |
| Audit pipeline | **Disabled at bootstrap** — `withAuditService` is commented out. Fail-closed for sensitive mutations is broken. | **P0** |
| Voice transcript redaction | Real `VoiceTranscriptRetentionPolicy` with PII patterns and labeled redaction. | OK |
| Embedded/local insecure mode | Permits no-auth boot. No network-bind guard. | P2 |
| Rate limiting | `withRateLimitConfig` wired in bootstrap. | OK |
| Payload size limit | `payloadSizeLimitFilter` wired. | OK |
| Sensitive data in logs | Not deeply audited. | P2 |
| API key storage | Resolver injected; storage details outside scope. | Not audited |

---

## 14. Observability / Operability Audit

| Area | Status | Severity |
|---|---|---|
| Prometheus metrics | `MetricsCollectorFactory` + `PrometheusMeterRegistry` wired in bootstrap | OK |
| Tracing | `TraceExportService` + `traceSamplingRate` wired; ClickHouse exporter | OK |
| Health probes | `HealthHandler` exists; **`event_store` health subsystem registration is commented out** in bootstrap | P0 (consistency with audit gap) |
| Structured logs | Logger usage idiomatic; not exhaustively audited | P2 |
| Audit events | **Broken — see § 13** | P0 |
| Anomaly detection task | Started in HTTP server only when `anomalyDetector != null && eventLogStore != null` — **eventLogStore is null in current bootstrap, so continuous anomaly detection never starts** | **P0** |
| Correlation IDs | Not verified in this pass | P2 |
| Capability registry SLA | Implemented; needs SLO definition | P2 |

---

## 15. Performance and Scalability Audit

> **Not deeply audited.** Recorded concerns:

| Area | Concern | Severity |
|---|---|---|
| SSE queue capacity | `SSE_QUEUE_CAPACITY` constant; behavior on queue full logs warn and drops — needs load test | P2 |
| Event tail single-process | README admits not a distributed scheduler | OK (disclosed) |
| UI bundle size | `ui/` has many feature modules; `@next/bundle-analyzer` not detected (Vite project — need vite-bundle-visualizer) | P2 |
| H2 file-backed write throughput | Not benchmarked | P2 |
| Plugin retry storms | Retry is TODO — no backoff implemented | P1 |

---

## 16. Test Correctness and Coverage Audit

| Capability | Existing tests | Invalid / theatre | Required tests |
|---|---|---|---|
| `WorkflowDesigner` save / publish / execute | `__tests__/WorkflowDesigner.test.tsx` | **All M004 cases assert against inline `<button>` elements, not the real component** | Real RTL test that imports `WorkflowDesigner`, mounts within `MemoryRouter` + QueryClient, exercises actions through MSW. |
| Workflow rollback | None observed | n/a | Add test once handler is implemented (currently 501) |
| Collections pagination/export | `__tests__/CollectionsUI.test.tsx` lines 69, 328 | Inline `<button>` tests, not real component | Rewrite |
| SSE CDC stream | None observed for the active 501 path | n/a | Add integration test covering tenant scoping + offset semantics once handler is restored |
| Governance audit emission | `DataCloudHttpServerGovernanceTest` cited in README | If this currently passes despite `logGovernanceEvent` returning 501, the test is asserting incorrect behavior | **Audit this test immediately** |
| gRPC transport | None | n/a | Add startup smoke test once bootstrap is restored |
| Capability registry | Hook test referenced in WorkflowsPage | Acceptable; verify per-capability coverage | Expand |
| Anti-theatre rule (repo § 29) | Repository policy | **Violated** in 2+ files | Enforce via lint; delete or rewrite offenders |

---

## 17. Prioritized Remediation Plan

| Priority | Area | Issue | Evidence | Required Fix | Acceptance Criteria | Tests Required |
|---|---|---|---|---|---|---|
| P0 | SPI | Two `EventLogStore` and two `TenantContext` types diverge | `// TODO: Fix EventLogStore type mismatch` in 6 files | Choose canonical type per spec § 5.4; migrate all callers; remove duplicate | `grep -r "TODO: Fix EventLogStore type mismatch"` returns 0 hits | Compile + integration tests pass |
| P0 | SSE streaming | `/cdc/...` and `/events/stream` return 501 | SseStreamingHandler.java:239,283,536 | Re-enable code in same file once SPI is unified | SSE frame received within 1s for tenant-scoped event | Integration test with in-memory EventLogStore |
| P0 | Governance audit | `DataLifecycleHandler` cannot emit governance events | DataLifecycleHandler.java:1404,1413 | Re-enable EventLogStore wiring or replace with `AuditService.emit` | Every retention/purge/redaction emits an `AuditEvent` row | Integration test asserting persisted AuditEvent |
| P0 | Bootstrap audit pipeline | `withAuditService` and `withEventLogStore` commented out | DataCloudHttpLauncherBootstrap.java:157,182 | Re-wire after SPI unification; restore `event_store` health probe; restore `AnomalyDetectionTask` | Bootstrap logs `[DC-P3.6] Continuous anomaly detection active` in non-embedded profile | Smoke test on launcher startup |
| P0 | gRPC transport | Server never starts | DataCloudGrpcLauncherBootstrap.java:23 | Restore wiring after SPI unification | gRPC `health` RPC returns SERVING | Startup test |
| P0 | Data Fabric UI lying | `/data-fabric/metrics` 404 hidden behind heuristic fallback | DataFabricPage.tsx:70 | Either: (a) implement `DataFabricMetricsHandler` and route, or (b) gate the page on `data_fabric_metrics` capability and show "not available" empty state | UI does not display fabricated recommendations | UI test asserting empty state when capability absent |
| P0 | Test theatre | Inline-button tests pass falsely | WorkflowDesigner.test.tsx, CollectionsUI.test.tsx | Rewrite to import real component; cover M004 acceptance criteria | Tests fail if production component breaks | RTL + MSW tests |
| P1 | Workflow rollback | 501 returned | WorkflowExecutionHandler.java:350 | Implement using checkpoint store, or hide UI affordance | Rollback succeeds end-to-end | Integration test |
| P1 | AI schema infer | Route disabled | DataCloudRouterBuilder.java:323 | Implement `handleInferSchema` or remove UI trigger | Inferred schema returned with confidence + provenance | Test |
| P1 | AI 501 placeholders | "Quality assessment placeholder" surfaced | AiAssistHandler.java:1940 and 3 other 501 sites | Implement or remove | No "placeholder" text reaches the API surface | Contract test |
| P1 | Plugin retry | TODO stub | DataCloudRuntimePluginManager.java:345 | Implement bounded retry with jittered backoff and audit | Retry observed in tests | Integration test |
| P1 | Embedded insecure mode network bind | Permits insecure boot | bootstrap line 215 | Add guard: refuse to bind non-loopback when `INSECURE_MODE && !auth` | Startup fails fast | Test |
| P1 | Capability gating verification | Multiple 501-on-degrade paths | EntityValidation/Export/Anomaly/Lineage handlers | Confirm UI reads capability registry before exposing actions | UI hides actions when capability absent | UI test |
| P2 | Duplicate source-of-truth | `lib/api/client.ts` legacy | per README | Complete migration to canonical adapters; delete legacy client | Single canonical API client | Repo grep |
| P2 | Vite bundle hygiene | `mocks/` directory in src | DataFabricPage observation | Confirm `vite.config.ts` excludes `mocks/` from production build | Bundle analyzer shows no `msw` in prod build | Bundle audit |
| P2 | Tenant filter at boundary | per-handler `requireTenantIdOrFail` | HttpHandlerSupport.java | Move to a global `TenantContextFilter` so handlers cannot forget | All requests rejected without tenant in non-embedded | Test |
| P2 | DB integrity audit | Not done in this pass | n/a | Per-table tenant column + index audit | Documented evidence | DB audit |
| P3 | Bundle analyzer | Not configured | n/a | Add vite-bundle-visualizer | CI artifact | n/a |

---

## 18. Production Readiness Gate

| Gate | Status |
|---|---|
| Ready for production deployment | **No** |
| Ready for internal demo | **Conditional** — only with SSE, gRPC, Data Fabric, and rollback hidden, and only in sovereign/embedded profile |
| Ready behind feature flag | **Yes** for the entity / event-write / pipeline-CRUD / lineage / data-product / capability-registry surfaces |
| Critical blockers | (1) EventLogStore + TenantContext SPI duplication; (2) audit pipeline wiring; (3) SSE 501; (4) governance audit 501; (5) gRPC bootstrap no-op; (6) Data Fabric backend missing; (7) UI test theatre |

### Minimum required fixes before release

1. Unify `EventLogStore` and `TenantContext` types and migrate all callers (one PR, atomic).
2. Restore HTTP launcher audit + EventLogStore + health-probe + anomaly-detection wiring.
3. Restore gRPC bootstrap.
4. Restore SSE streaming code paths and add integration tests.
5. Restore `DataLifecycleHandler.logGovernanceEvent`.
6. Either build `/data-fabric/metrics` route or gate `DataFabricPage` on capability registry.
7. Rewrite the two flagged test files to exercise real components.
8. Re-verify `DataCloudHttpServerGovernanceTest` is asserting real success, not silently passing because `logGovernanceEvent` returns 501.
9. Add a CI rule that fails the build on any production-source `temporarily disabled` or `// TODO: Fix .* type mismatch` comments.

---

## 19. Final Release Checklist

- [ ] All `temporarily disabled` markers removed from production code
- [ ] No production handler returns 501 unless behind a documented capability gate
- [ ] EventLogStore single canonical type; all callers compile against it
- [ ] TenantContext single canonical type; all callers compile against it
- [ ] `withAuditService(...)` and `withEventLogStore(...)` called in launcher bootstrap
- [ ] `event_store` health probe registered
- [ ] `AnomalyDetectionTask` starts in non-embedded profile
- [ ] gRPC server starts and `health` RPC returns SERVING
- [ ] SSE `/api/v1/cdc/...` and `/events/stream` deliver real frames in integration tests
- [ ] `DataFabricPage` shows real metrics or is hidden via capability registry
- [ ] All UI integration tests import their target component (no inline `<button>` theatre)
- [ ] Anti-theatre lint rule added to UI eslint config
- [ ] Authentication required by default in all non-embedded profiles (already enforced; verify no regression)
- [ ] Embedded profile cannot bind to non-loopback when no auth configured
- [ ] Tenant isolation enforced via filter, not solely per-handler
- [ ] Per-capability runtime gating verified for AI, validation, export, anomaly, lineage, federated query
- [ ] `lib/api/client.ts` legacy migration tracked and completed
- [ ] DB integrity audit completed (separate workstream)
- [ ] Documentation aligned: spec § 6 status table updated to reflect actual implementation state after fixes

---

## 20. Appendix — Patterns Searched

```
TODO|FIXME|HACK|XXX|temporarily disabled|errorResponse\(501|throw new Error\("Not implemented"\)
Mock|Stub|placeholder|hardcoded|Math\.random|fake|dummy
render\(<button data-testid|expect\(true\)\.toBe\(true\)
JwtAuth|ApiKeyAuth|AuthFilter|requireAuth|requireTenantIdOrFail
withApiKeyResolver|withJwtProvider
data-fabric|/data-fabric/
```

All scans were scoped to `products/data-cloud/**` excluding `build/`, `node_modules/`, `dist/`. Test paths were scanned separately from production paths to keep the mock/stub/theatre signal clean.
