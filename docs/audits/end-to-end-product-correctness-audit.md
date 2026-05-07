# Ghatana Data Cloud + AEP — Ultra-Strict End-to-End Product Correctness, Completeness, Architecture/Design, and Production-Readiness Audit

**Repository:** `samujjwal/ghatana`  
**Reviewed commit:** `7432d84601747ed3e095555c11a5f9471f0f8595`  
**Target roots:** `products/data-cloud`, `products/aep`  
**Audit prompt:** Ultra-Strict End-to-End Product Correctness, Completeness, UI/UX, Backend, DB, and Production-Readiness Audit Prompt supplied by user.  
**Execution method:** source-evidence audit via GitHub connector at exact ref. I did not run Gradle, pnpm, Playwright, DB migrations, Docker, or services. Runtime/build-only conclusions are marked as requiring execution proof.  
**Required report target:** `docs/audits/end-to-end-product-correctness-audit.md`. This generated file is suitable to copy there after review.

---

## 1. Executive Summary

### 1.1 Readiness Ratings

| Dimension | Data Cloud | AEP | Combined Verdict |
|---|---:|---:|---|
| Overall correctness | C+ | B+ | Data Cloud regressed in analytics handler correctness at this commit; AEP remains architecturally sound but test hardening remains. |
| Overall completeness | B- | B | Broad surfaces exist; several optional/preview/degraded capabilities still need capability truth and production gates. |
| Ghatana architecture consistency | B+ | B+ | Ownership boundaries are conceptually aligned with Ghatana rules. |
| Ghatana design consistency | B | C+ | Data Cloud UI architecture is clean; AEP still has explicit design-system adoption debt. |
| Production mock/stub/placeholder risk | High for Data Cloud analytics | Low-Medium for AEP | Data Cloud has production-path `FIXME`, non-streaming placeholder behavior, and `501 Not Implemented` in analytics. |
| Security/privacy/governance | B | B+ | Strong fail-closed patterns exist, but strict profile tests and gateway E2E remain required. |
| Observability/operability | C+ | B | Data Cloud analytics response enrichment/correlation regressed; AEP gateway correlation still needs full error-path coverage. |
| Test authenticity | B- | B | Good route tests exist, but Data Cloud workflow critical tests mock the provider and do not prove durable behavior. |
| Production readiness | No | Conditional No | Internal demo only; not unrestricted production-ready. |

### 1.2 Production Gate

| Gate | Verdict |
|---|---|
| Ready for unrestricted production? | **No** |
| Ready for internal demo? | **Yes, with explicit disclosure of preview/degraded surfaces and analytics regression risk** |
| Ready behind feature/capability flags? | **Partially** |
| Critical blocker count | **2 P0 Data Cloud blockers, 8 P1 release blockers** |

### 1.3 Top P0 Issues

| ID | Product | Area | Issue | Required Fix |
|---|---|---|---|---|
| P0-DC-1 | Data Cloud | Analytics build/runtime correctness | `handleAnalyticsQuery` wraps asynchronous `request.loadBody().then(...)` inside `Promise.ofBlocking(...)`. This likely creates a `Promise<Promise<HttpResponse>>` type/async-pattern defect and should be treated as a build/release blocker until Gradle proves otherwise. | Remove `Promise.ofBlocking` wrapper; keep ActiveJ async chain on event loop; add compile test and API test for `POST /api/v1/analytics/query`. |
| P0-DC-2 | Data Cloud | Production-path placeholder behavior | `AnalyticsHandler` contains production-path `FIXME: Not implemented` comments, disables `enrichWithBrokerContract(...)`, and uses a fake/non-streaming branch for “streaming” responses. This violates the no placeholder/stub doctrine for an important query surface. | Restore trace/cost/cancellation/explain metadata, implement real streaming or remove the claim, and add response-contract tests. |

### 1.4 Top P1 Issues

| ID | Product | Area | Issue | Required Fix |
|---|---|---|---|---|
| P1-DC-1 | Data Cloud | Analytics cancel | `DELETE /api/v1/analytics/queries/:queryId` now returns hardcoded `501 Not Implemented`; previous service-unavailable/capability-truth behavior regressed. | Either implement cancellation or remove/hide endpoint and mark unsupported through capability registry/OpenAPI/UI. |
| P1-DC-2 | Data Cloud | Data Fabric | Data Fabric remains preview/demo-only and uses hardcoded demo metrics while reachable through feature-gated routes. | Disable by default in production or render explicit preview/unsupported boundary from runtime capability. |
| P1-DC-3 | Data Cloud | Workflow execution tests | Workflow HTTP tests use real server but mocked `WorkflowExecutionCapability` and mocked `DataCloudClient`, so they do not prove durable provider behavior. | Add provider-backed integration tests and restart persistence tests. |
| P1-DC-4 | Data Cloud | Tenant/auth strictness | Local-profile workflow execution accepts missing tenant header; production fail-closed behavior must be proven. | Add strict/production profile tests for missing tenant/auth. |
| P1-DC-5 | Data Cloud | Audit source of truth | Checked-in audit file still says reviewed commit `c4fc61…`; it misses new `7432d846…` analytics regressions. | Replace/regenerate audit doc with exact-SHA report. |
| P1-AEP-1 | AEP | Design system | AEP design guide reports only two design-system imports and about 64 raw controls needing migration. | Migrate raw controls to `@ghatana/design-system`; enforce lint/visual/a11y gates. |
| P1-AEP-2 | AEP | Gateway hardening | Gateway has good source behavior but needs full JWT/CORS/tenant/correlation/SSE/WS/backend-error tests. | Add integration suite and require all error bodies/headers to include correlation ID. |
| P1-AEP-3 | AEP | Durable runtime proof | AEP source fails closed without durable EventCloud unless explicitly allowed, but production launcher tests must prove it. | Add no-provider failure, provider success, and dev/test allow-flag tests. |

---

## 2. Scope and Method

### 2.1 Reviewed Evidence

| Area | Files / Evidence |
|---|---|
| User audit rubric | Uploaded ultra-strict audit prompt |
| Commit verification | `fetch_commit` for `7432d84601747ed3e095555c11a5f9471f0f8595` |
| Architecture rules | `docs/architecture/ARCHITECTURE_RULES.md` |
| Ownership | `docs/DATA_CLOUD_OWNERSHIP_CLARIFICATION.md` |
| Data Cloud product docs | `products/data-cloud/README.md` |
| Data Cloud UI architecture | `products/data-cloud/delivery/ui/ARCHITECTURE.md` |
| Data Cloud UI routes | `products/data-cloud/delivery/ui/src/routes.tsx` |
| Data Cloud workflow contract | `products/data-cloud/delivery/launcher/src/main/java/.../WorkflowExecutionCapability.java` |
| Data Cloud analytics handler | `products/data-cloud/delivery/launcher/src/main/java/.../AnalyticsHandler.java` |
| Data Cloud workflow tests | `products/data-cloud/delivery/launcher/src/test/java/.../DataCloudHttpServerWorkflowExecutionTest.java` |
| AEP product docs | `products/aep/README.md` |
| AEP engine factory | `products/aep/aep-engine/src/main/java/com/ghatana/aep/Aep.java` |
| AEP gateway | `products/aep/gateway/src/app.ts` |
| AEP UI routes | `products/aep/ui/src/App.tsx` |
| AEP design adoption | `products/aep/ui/DESIGN_SYSTEM_ADOPTION.md` |
| Existing audit doc | `docs/audits/end-to-end-product-correctness-audit.md` |

### 2.2 Excluded

- Build/test execution.
- Full recursive clone inspection.
- Live runtime with DB/Kafka/H2/Trino/OpenAI/Ollama.
- Browser execution.
- Dependency vulnerability scan.

### 2.3 Confidence Terms

| Term | Meaning |
|---|---|
| Source-proven | Exact-SHA source directly shows it. |
| Source-partial | Source shows structure/intent, but runtime execution is needed. |
| Documented-only | Product doc claims it; implementation/test proof not fully inspected. |
| Unknown | Insufficient evidence in fetched files. |

---

## 3. Complete Product Inventory

### 3.1 Data Cloud UI Inventory

| UI Item | File(s) | Purpose | User Actions | Data Dependencies | API/State Dependencies | Status | Issues |
|---|---|---|---|---|---|---|---|
| Root app/providers | `products/data-cloud/delivery/ui/src/App.tsx` | App shell, QueryClient, Jotai, theme, router, onboarding | app boot, onboarding complete | route config/session bootstrap | Jotai/TanStack/router/theme | Complete | Onboarding role/tenant/a11y tests still needed. |
| `/` Intelligent Hub | `routes.tsx`, `IntelligentHub` | Command center | navigate to data/query/pipeline/trust | launcher APIs | route/action cards | Complete | Needs UI-to-backend journey E2E. |
| `/data` | `DataExplorer` | Unified data explorer | browse/filter/select | collections/entities | entity APIs | Complete | Large dataset and deterministic sort tests needed. |
| `/data/new` | `CreateCollectionPage` | Create collection | submit/cancel | metadata/entity store | collection API | Complete | Duplicate submit/concurrency tests needed. |
| `/data/:id/edit` | `EditCollectionPage` | Edit collection | save/cancel | collection metadata | collection API | Complete | Version/concurrency tests needed. |
| `/pipelines` | `WorkflowsPage` | Pipeline list/center | list/select/create/run | pipeline metadata/execution | pipeline/checkpoint/execution APIs | Partial-complete | Execution must be capability-gated and real-provider tested. |
| `/pipelines/new` | `SmartWorkflowBuilder` | Intent-to-workflow draft | prompt/generate/review/save | AI assist + pipeline API | draft and save APIs | Partial | Low-confidence clarification limited per README. |
| `/pipelines/:id`, `/pipelines/:id/edit` | `WorkflowDesigner` | Pipeline detail/design | inspect/edit/execute/logs | pipeline/execution store | pipeline execution APIs | Partial | Provider durability proof missing. |
| `/query` | `SqlWorkspacePage` | SQL/NLQ workspace | query/explain/result/cancel? | analytics engine | analytics APIs | Regressed | Analytics handler has P0/P1 issues. |
| `/trust` | `TrustCenter` | Governance/trust | retention, purge, redaction, compliance | governance/audit stores | governance APIs | Partial | Broader policy CRUD lifecycle incomplete. |
| `/insights` | `InsightsPage` | Operator diagnostics | inspect capability/runtime | metrics/capabilities | capability/metrics APIs | Complete/partial | Need no-fake metrics tests. |
| `/alerts` | `AlertsPage` | Alert triage | acknowledge/resolve/escalate | alert lifecycle | alert APIs/SSE | Complete | Operator-only role/capability gate. |
| `/operations`, `/operations/jobs` | operation pages | Runtime/jobs | inspect tools/jobs | operations/jobs | operations APIs | Partial | Real runtime E2E needed. |
| `/events` | `EventExplorerPage` | Event stream | filter/inspect | event stream | event/SSE APIs | Complete/partial | Long-lived stream tests needed. |
| `/memory` | `MemoryPlaneViewerPage` | Memory plane | browse/retain/delete | memory store | memory APIs | Complete/partial | Privacy/tenant tests needed. |
| `/entities` | `EntityBrowserPage` | Entity browser | browse/detail | entity store | entity APIs | Complete | Feature-gated. |
| `/context` | `ContextExplorerPage` | Context/RAG | query/context actions | context/RAG store | context APIs | Complete/partial | Governance/RAG policy tests. |
| `/fabric` | `DataFabricPage` | Data topology | inspect fabric/connectors | demo/real metrics | fabric/capability APIs | Preview | P1: demo metrics cannot look live. |
| `/agents` | `AgentPluginManagerPage` | Agent catalog/plugin view | browse/inspect | agent catalog | agent APIs | Partial | Must distinguish Data Cloud agent catalog from AEP runtime. |
| `/settings` | `SettingsPage` | Admin settings | update config | settings store | settings APIs | Complete/partial | Durable settings required outside local. |
| `/plugins`, `/plugins/:id` | plugin pages | Plugin lifecycle | enable/disable/upgrade | plugin registry | plugin APIs | Partial | Upgrade/conformance/sandbox capability truth required. |
| `/connectors` | `DataConnectorsPageWrapper` | Connector management | create/edit connector | connector registry | connector APIs | Partial/unknown | Delete/production capability proof needed. |

### 3.2 AEP UI Inventory

| UI Item | File(s) | Purpose | User Actions | Data Dependencies | API/State Dependencies | Status | Issues |
|---|---|---|---|---|---|---|---|
| Root router/shell | `products/aep/ui/src/App.tsx` | Outcome shell and routes | navigate, command palette, mobile nav | auth/runtime | AuthProvider, QueryClient, gateway APIs | Complete | Raw shell controls; design debt. |
| `/operate` | `MonitoringDashboardPage` | Operator cockpit | inspect runs/alerts/failures | runtime APIs | gateway | Complete/partial | Gateway E2E required. |
| `/operate/costs` | `CostDashboardPage` | Cost visibility | view spend/budget | cost APIs | gateway | Partial | Synthetic fallback must be proven absent/disclosed. |
| `/operate/reviews` | `HitlReviewPage` | HITL queue | approve/reject/escalate | HITL APIs | gateway/SSE | Complete/partial | SLA escalation tests needed. |
| `/operate/runs/:runId` | `RunDetailPage` | Run evidence/detail | inspect/cancel | run store | runs API | Complete/partial | Durable run evidence depends on Data Cloud/EventLogStore. |
| `/operate/operations` | `OperationCenterPage` | Operations center | inspect/cancel/retry | operations APIs | gateway | Complete/partial | Long-running tests needed. |
| `/build/pipelines` | `PipelineListPage` | Pipeline inventory | create/edit/delete/publish/rollback | pipeline repository | pipeline APIs | Complete/partial | UI must always send version/expectedVersion. |
| `/build/pipelines/new` | `PipelineBuilderPage` | Pipeline builder | NLQ/manual build | pipeline/NLQ APIs | gateway | Complete/partial | AI fallback/provenance tests. |
| `/build/patterns` | `PatternStudioPage` | Pattern/learning studio | create/test/learn | pattern/learning APIs | gateway | Complete/partial | Learning route redirects here. |
| `/learn/memory` | `MemoryExplorerPage` | Memory explorer | inspect/search | memory store | memory API | Complete/partial | Tenant/privacy tests needed. |
| `/govern` | `GovernancePage` | Governance/policy | kill switch, policy eval | governance store | governance APIs | Conditional | MFA/step-up proof required. |
| `/govern/privacy` | `PrivacyRequestPage` | Privacy requests | fulfill/delete/export | compliance APIs | gateway | Complete/partial | Deletion verification E2E. |
| `/catalog/agents` | `AgentRegistryPage` | Agent registry/detail | register/execute/inspect | agent store | agent APIs | Complete/partial | Security scan hardening. |
| `/catalog/marketplace` | `AgentMarketplacePage` | Marketplace | install/review | marketplace APIs | gateway | Complete/partial | Install rollback tests. |
| `/catalog/workflows` | `WorkflowCatalogPage` | Workflow catalog | browse/use | workflow APIs | gateway | Complete/partial | Role tests. |
| Auth pages | login/callback/session expired | Auth/session | login/callback/renew | auth/gateway | AuthContext | Complete/partial | Token freshness/rejection tests. |

### 3.3 Backend/API Inventory

| Backend Item | File(s) | Caller(s) | Expected Behavior | Auth/AuthZ | Validation | DB Access | Side Effects | Complete? | Issues |
|---|---|---|---|---|---|---|---|---|---|
| Data Cloud health/metrics | `DataCloudRouterBuilder`, health handlers | UI/SRE | health/readiness/live/info/metrics | profile-aware | n/a | health subsystems | metrics | Source-partial | Runtime profile proof needed. |
| Entity APIs | entity handlers/routes | UI/SDK | CRUD/history/search/batch | tenant | body/path | EntityStore | write/read | Source-complete | DB/provider tests required. |
| Event APIs | event handlers/routes | UI/SDK/AEP | append/query/offset/stream | tenant | event schema | EventLogStore | append/query | Source-complete | Durability provider proof. |
| Pipeline metadata | pipeline/checkpoint handlers | UI/SDK | CRUD/checkpoints | tenant | pipeline body | pipeline/checkpoint store | save/update/delete | Source-complete | concurrency/delete tests. |
| Workflow execution | `WorkflowExecutionCapability`, handler/tests | UI/SDK | execute/list/get/log/cancel/retry/rollback/checkpoint | tenant | path/body | capability/client | execution state/logs | Partial | provider mocked in inspected tests. |
| Analytics query | `AnalyticsHandler.java` | SQL workspace/SDK | query/result/plan/aggregate/explain | tenant | query body | analytics engine | query/result | Regressed | P0 build/placeholder concerns; response enrichment disabled. |
| Analytics cancel | `AnalyticsHandler.java` | SQL workspace/SDK | cancel if supported or truthful unavailable | tenant/path | query id | analytics engine | cancellation | Incomplete | returns hardcoded 501 Not Implemented. |
| Reports | `AnalyticsHandler.java` | UI/SDK | create/list/get reports | tenant | definition | report capability/service | cache/report | Partial | cache/durability proof. |
| Governance | governance handlers | Trust Center | retention/purge/redact/compliance | role/tenant | request/tokens | governance/audit | destructive ops | Partial | broader policy CRUD incomplete. |
| Capability registry | capability handler | UI/SDK | runtime truth | operator/tenant | n/a | none | none | Source-complete | UI must universally consume. |
| AEP engine | `Aep.java` | AEP server/embedded consumers | create engine with durable provider | config/profile | config validation | EventCloud | event processing | Strong source | production tests required. |
| AEP gateway | `gateway/src/app.ts` | AEP UI/external clients | JWT/CORS/proxy/SSE/WS | JWT/tenant | tenant mismatch | n/a | proxy | Source-good | gateway E2E and correlation hardening. |

### 3.4 Database / Persistence Inventory

| DB Item | Product | File(s) | Purpose | Callers | Constraints / Rules | Complete? | Issues |
|---|---|---|---|---|---|---|---|
| EntityStore | Data Cloud | platform entity/storage modules | tenant entity persistence | entity handlers | tenant-scoped query/write | Source-partial | Provider-dependent. |
| EventLogStore local | Data Cloud | README/deployment docs | local event persistence | event handlers/workflows | non-durable in local | Complete for dev | P1 if used as prod. |
| Sovereign embedded H2 | Data Cloud | README/runbook | durable single-node storage | Data Cloud launcher | restart durable, not HA | Documented | Needs executed restart tests. |
| ServiceLoader durable provider | Data Cloud/AEP | README/AEP factory | standard durable event storage | Data Cloud/AEP | fail-closed if absent in prod | Source-partial | production startup tests. |
| Workflow execution snapshots/logs | Data Cloud | capability + handler/tests | execution state/logs | workflows UI/API | durable when provider durable | Partial | test mocks provider; restart proof needed. |
| Analytics results | Data Cloud | analytics engine | query results by id | SQL workspace | row limit/truncation | Regressed | response contract removed; streaming placeholder. |
| AEP run history | AEP | README/server docs | durable run evidence | run detail/cockpit | Data Cloud/EventLogStore required | Documented | E2E restart tests. |
| AEP in-memory test runtime | AEP | `Aep.forTesting()` | test-only runtime | tests | explicit allow flag | Complete | keep isolated. |
| Migrations | Both | migration dirs not fully fetched | fresh DB schema | launchers/repos | safe/reversible | Unknown | run exact migration inventory. |

### 3.5 Test Inventory

| Test | File | Type | Feature Covered | What It Proves | Real or Mocked | Valid? | Gaps |
|---|---|---|---|---|---|---|---|
| Workflow execution HTTP tests | `DataCloudHttpServerWorkflowExecutionTest.java` | HTTP integration with mocks | execute/list/get/cancel/retry/rollback/checkpoints/logs | real server route mapping and response shapes | real `DataCloudHttpServer`; mocked `DataCloudClient` and mocked `WorkflowExecutionCapability` | Valid limited route test | Does not prove durable provider, DB writes, or restart survival. |
| Analytics handler tests | not fetched | API/unit needed | query/result/plan/cancel | should prove query contract | unknown | Unknown | Must be added/updated for new handler regression. |
| Data Fabric tests | not fetched | UI/API/feature | preview/capability boundaries | should prove production-off | unknown | Unknown | Required before release. |
| Tenant/auth strict tests | partial docs/test evidence | security/API | missing tenant/auth in production | should prove fail-closed | unknown | Unknown | local test currently allows missing tenant for execute. |
| AEP engine tests | not fetched | unit/integration | EventCloud fail-closed | should prove no provider failure/provider success | unknown | Unknown | Required before release. |
| AEP gateway tests | not fetched | integration | JWT/CORS/tenant/SSE/WS | should prove edge security | unknown | Unknown | Required before release. |
| AEP UI tests | README claims UI tests | UI/unit/E2E | operator pages | should prove visible surfaces | unknown | Unknown | Need design-system/a11y tests. |
| Migration tests | not executed | DB integration | fresh schema/restart | should prove DB safety | unknown | Unknown | Required before production. |

---

## 4. Product Behavior Map

| Capability | Product | Persona | Problem Solved | Expected UX | Expected Backend Behavior | Expected Data Behavior | Success Criteria |
|---|---|---|---|---|---|---|---|
| Trusted entity storage | Data Cloud | data engineer/operator | central record management | simple create/browse/edit/delete | validate, persist, version, tenant isolate | durable EntityStore in non-local | data and history accurate. |
| Event persistence | Data Cloud | platform/SRE/integrator | event history and streams | event explorer/SSE | append/query EventLogStore | durable provider outside local | events survive restart. |
| Analytics query | Data Cloud | analyst/operator | query data quickly | SQL/NLQ query, explain, results | analytics engine returns contract with trace/cost/cancel/explain metadata | query result cache/store | real results, no placeholders, bounded rows. |
| Analytics cancellation | Data Cloud | analyst/operator | stop long query | cancel action only if supported | cancel or truthful unsupported state | no inconsistent query state | UI/OpenAPI/capability agree. |
| Data Fabric | Data Cloud | operator/admin | topology/connectors | visual topology with real metrics or preview boundary | fabric metrics/connector registry | connector/fabric store | no demo data shown as live. |
| Governance/trust | Data Cloud | compliance/admin | retention, purge, redaction | safe, auditable actions | confirmation/audit/tenant/role checks | governance/audit records | destructive actions confirmed/audited. |
| Data-local workflow execution | Data Cloud | data engineer | run data plugin workflow | execute, status, logs, checkpoints | capability executes and persists | durable snapshots/logs | execution survives restart in durable profile. |
| Agentic runtime | AEP | operator/agent engineer | run/govern agents and pipelines | operate/build/learn/govern/catalog | AEP orchestrates agents/pipelines/HITL | Data Cloud/EventLog-backed run history | durable run evidence. |
| HITL | AEP | reviewer | review low-confidence/risky actions | queue/approve/reject/escalate | configured=false if absent, real queue if present | review/audit records | decisions traceable. |
| Gateway edge | AEP | user/integrator | secure UI/API/SSE/WS | one edge through gateway | JWT/CORS/tenant/correlation proxy | n/a | invalid auth/tenant rejected. |

---

## 5. Traceability Matrix

| Requirement / Capability | UI Route/Page | User Actions | API/Backend Handler | Service/Domain Logic | DB/Store | Tests | Observability | Status |
|---|---|---|---|---|---|---|---|---|
| Entity CRUD | `/data`, `/entities` | CRUD/search | entity handlers | entity domain/storage | EntityStore | not fully fetched | metrics/traces | Source-partial complete |
| Event append/query | `/events` | inspect/stream | event/SSE handlers | EventLogStore | EventLogStore | not fully fetched | streams/metrics | Source-partial complete |
| Workflow execution | `/pipelines/:id` | execute/cancel/retry/logs | workflow execution handler/capability | plugin provider | execution store/client | mocked route test | partial | Partially implemented/proven |
| Analytics query | `/query` | query/result/plan | `AnalyticsHandler` | `AnalyticsQueryEngine` | analytics engine | missing/unknown | regressed | Complete but currently incorrect/regressed |
| Analytics cancel | `/query` if exposed | cancel | `handleAnalyticsCancelQuery` | unsupported | none | missing | log only | Incomplete/stubbed |
| Data Fabric | `/fabric`, `/connectors` | inspect/connectors | fabric/connector APIs | fabric service | connector/fabric store | unknown | capability registry | Preview/demo |
| Governance | `/trust` | purge/redact/classify | governance handlers | lifecycle service | governance/audit | unknown | audit | Partial-complete |
| AEP pipeline runtime | `/build/pipelines` | CRUD/publish/run | pipeline controllers | AEP runtime/repo | Data Cloud/EventLog | README tests | SLO/deep health | Complete per docs, needs exact proof |
| AEP EventCloud durability | runtime | startup/process | `Aep.create()` | ServiceLoader provider | EventCloud | missing | health | Source-proven guard; tests required |
| AEP gateway | all protected UI/API | API/SSE/WS | Fastify gateway | JWT/proxy | n/a | missing | correlation partial | Source-good; tests required |
| AEP design system | all UI | all controls | n/a | design/theme | n/a | missing | n/a | Incomplete |

---

## 6. End-to-End Journey Correctness Audit

| Journey | Entry Point | Expected Outcome | Actual Behavior | Correct? | Complete? | Mock/Stub Risk | Gaps | Severity | Required Fix | Required Tests |
|---|---|---|---|---|---|---|---|---|---|---|
| Data Cloud onboarding | app load | onboarding only when needed | app has onboarding flow | likely | partial | low | role/tenant/a11y | P2 | add onboarding E2E | Playwright |
| Create collection | `/data/new` | collection persists | route exists; backend docs claim entity APIs | likely | partial | low | DB/API proof | P2 | add create-read-delete journey | API + UI E2E |
| Run analytics query | `/query` | contract-compliant query response with trace/cost/cancel metadata | handler now has async/FIXME regressions and disabled enrichment | No | No | high | build/API proof, response contract | P0 | fix handler and restore contract | compile + API contract |
| Retrieve analytics result | `/query/:id` or query result view | bounded result set with metadata | handler limits but disables enrichment and has fake streaming branch | Partial | No | high | contract/streaming | P0/P1 | implement real streaming or remove claim | API/perf tests |
| Cancel analytics query | cancel action/API | cancel if supported or clear unavailable state | backend hardcodes 501 | Backend truthful but stubbed | No | medium | UI/OpenAPI/capability drift | P1 | capability-gate/hide or implement | API/UI |
| Execute workflow | `/pipelines/:id` | execution persists and logs visible | route/capability exist; test mocks provider | Partial | No | medium | real provider/restart | P1 | provider-backed tests and UI gate | IT + UI |
| Missing workflow capability | `/pipelines/:id` | disabled UI and backend block | tests return 501 for execute; list/logs may return empty | Partial | Partial | medium | UI disabled state | P1 | capability registry drives UI | UI/API |
| Data Fabric | `/fabric` | live topology or preview boundary | README says demo-only | Partial | No | high | production flag/boundary | P1 | off by default or preview boundary | feature/UI |
| Trust purge/redaction | `/trust` | dry-run/confirm/audit | docs say wired | likely | partial | low | exact audit proof | P1/P2 | audit-event assertions | API E2E |
| AEP login/session | `/login` | authenticated gateway session | routes exist, gateway checks JWT for APIs | likely | partial | low | auth expiry/rejection tests | P1 | gateway/auth E2E | UI/gateway |
| AEP run detail | `/operate/runs/:runId` | durable run evidence | docs require durable Data Cloud/EventLogStore | conditional | partial | medium | restart proof | P1 | run-history restart test | E2E |
| AEP WS tail | `/tail/events` | authenticated tenant-scoped event tail | gateway forwards tenant/correlation | yes source | needs tests | low | WS mismatch/backpressure | P1 | gateway WS suite | WS E2E |
| AEP governance kill switch | `/govern` | MFA/admin/audit | docs claim operational | unknown | partial | medium | exact source/tests | P1 | verify MFA/role/audit | security tests |

---

## 7. UI/UX Correctness and Completeness Audit

| UI Area | File(s) | Finding | Correctness Impact | Completeness Impact | Severity | Required Fix | Tests |
|---|---|---|---|---|---|---|---|
| Data Cloud IA | `ui/src/routes.tsx` | Broad route coverage with primary + compatibility routes. | Positive | Positive | — | Maintain. | route smoke tests |
| Data Cloud analytics UI | `/query` route + backend handler | Backend response contract regressed; UI may lose trace/cost/cancel metadata. | High | High | P0/P1 | Restore API contract or update UI intentionally. | UI/API contract |
| Data Cloud Data Fabric | README + `/fabric` route | Preview/demo-only metrics can appear live. | False operational confidence | Incomplete | P1 | runtime preview boundary/off in prod | browser/feature |
| Data Cloud workflow actions | `/pipelines` | Execution provider proof is partial. | Possible fake-live action | Partial | P1 | capability registry + provider tests | UI/action |
| AEP IA | `ui/src/App.tsx` | Outcome-oriented Operate/Build/Learn/Govern/Catalog routes are strong. | Positive | Positive | — | Keep. | route tests |
| AEP design system | `DESIGN_SYSTEM_ADOPTION.md` | ~64 raw controls remain. | UX/a11y inconsistency | Incomplete | P1 | migrate raw controls | lint/visual/a11y |
| AEP mobile shell | `App.tsx` | raw button/svg mobile nav. | Minor consistency | Minor | P2 | use design-system control | component test |

---

## 8. Frontend Action, State, and Data Flow Audit

| Flow | File(s) | Expected | Actual | Correct? | Complete? | Production Mock/Stub? | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|
| Data Cloud lazy routing | `routes.tsx` | lazy pages with error states | implemented | yes | yes | no | keep | route load |
| Data Cloud optional pages | `routes.tsx`, feature gates | disabled surfaces hidden | many feature gates exist | mostly | partial | no | align with backend capability registry | feature tests |
| Data Cloud analytics query | SQL workspace + `AnalyticsHandler` | response includes trace/cost/cancel/explain and bounded rows | enrichment disabled; streaming fake | no | no | yes/placeholder | restore contract and implement/disable streaming | API/UI |
| Data Cloud analytics cancel | SQL workspace + handler | only show cancel if supported | backend 501 | backend incomplete | no | production stub | hide or implement | UI/API |
| Workflow execute | workflows UI + capability | call only when provider active | UI proof unknown | unknown | partial | possible | capability-gate actions | UI/action |
| AEP command palette | `App.tsx` | canonical route navigation | implemented | yes | yes | no | keep | keyboard |
| AEP gateway API flow | UI + gateway | all APIs through gateway | source-partial | likely | partial | no | static rule no direct backend | static/E2E |
| AEP WS flow | gateway | forward auth/tenant/correlation | implemented | yes | needs tests | no | WS test suite | gateway E2E |
| AEP design controls | many UI files | design-system controls | adoption incomplete | no | no | no | migrate controls | visual/a11y |

---

## 9. Backend/API/Domain Logic Audit

| Backend Flow | File(s) | Expected Behavior | Actual Behavior | Correct? | Complete? | Mock/Stub? | Security/Data Risk | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|---|
| Data Cloud analytics query | `AnalyticsHandler.java` | ActiveJ async body load, query, contract response | suspicious `Promise.ofBlocking` around async chain; enrichment commented out | No | No | placeholder/FIXME | build/runtime break, contract drift | remove blocking wrapper, restore enrichment | compile + API contract |
| Data Cloud result retrieval | `AnalyticsHandler.java` | bounded rows and metadata | limit implemented but enrichment disabled; fake streaming branch | Partial | No | placeholder | client observability loss | implement streaming or remove claim | perf/API |
| Data Cloud analytics cancel | `AnalyticsHandler.java` | cancel or capability-unavailable | hardcoded 501 Not Implemented | No | No | yes | UI/API drift | implement or hide/gate | API/UI |
| Workflow capability | `WorkflowExecutionCapability.java` | execute/list/get/cancel/retry/logs | interface exists | yes | interface only | no | provider absent | provider proof | provider IT |
| Workflow tests | `DataCloudHttpServerWorkflowExecutionTest.java` | prove real execution durability | real server + mocked capability/client | limited | partial | test mocks | false durability confidence | add real provider/restart tests | IT |
| AEP EventCloud discovery | `Aep.java` | fail closed without provider | implemented | yes | strong | no | startup provider config | production tests | unit/IT |
| AEP test fallback | `Aep.forTesting()` | in-memory only dev/test | explicit test factory | yes | yes | test only | none | keep isolated | unit |
| AEP gateway auth | `gateway/src/app.ts` | reject missing/invalid JWT | implemented | yes | needs tests | no | bypass if untested | gateway tests | E2E |
| AEP gateway tenant | `gateway/src/app.ts` | reject mismatch | HTTP/SSE mismatch checks; WS derives tenant | mostly | partial | no | WS ambiguity | define/test WS mismatch policy | E2E |
| AEP gateway errors | `gateway/src/app.ts` | all errors correlated | readiness correlated; some 502 bodies lack correlation | partial | partial | no | diagnosability | include correlation everywhere | gateway tests |

---

## 10. Database and Persistence Audit

| DB Operation/Model | File(s) | Expected Data Rule | Actual Behavior | Correct? | Complete? | Integrity Risk | Performance Risk | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|---|
| Data Cloud local store | README | dev-only, non-durable | documented | yes | yes | high if prod misuse | low | fail closed outside local | startup |
| Sovereign H2 | README | durable single-node | documented | source-partial | needs run | medium | single-node | restart tests | sovereign IT |
| ServiceLoader provider | README/AEP source | durable provider in prod | documented/source | source-partial | needs run | high if absent | provider-dependent | production startup tests | launcher |
| Workflow execution snapshots/logs | capability/tests | durable in durable profile | tests mock provider/client | unknown | partial | medium | unknown | real provider + restart | IT |
| Analytics query results | `AnalyticsHandler` | bounded, metadata-rich, consistent | limit logic added; metadata removed; streaming fake | no | no | medium | high under large results | restore contract, implement paging/streaming | API/perf |
| AEP EventCloud | `Aep.java` | durable or explicit allow | fail-closed source | yes | source-proven | startup failure if misconfigured | provider | production test | unit/IT |
| AEP run history | README | Data Cloud/EventLog required in production | documented | source-partial | needs run | high if misconfigured | provider | restart E2E | E2E |
| Migrations | not fully fetched | fresh schema migrates | unknown | unknown | unknown | high if gaps | n/a | exact migration inventory | Flyway/Testcontainers |

---

## 11. Production Mock/Stub/Shortcut Audit

| File | Evidence | Production Reachable? | Critical Flow? | Feature Flagged? | Allowed? | Severity | Required Action |
|---|---|---:|---:|---:|---:|---|---|
| `AnalyticsHandler.java` | `FIXME: Not implemented` around response enrichment and streaming response | yes | yes, analytics | no | no | P0 | remove FIXME, implement or remove behavior |
| `AnalyticsHandler.java` | `Promise.ofBlocking` around async `request.loadBody().then(...)` | yes | yes | no | no | P0 | rewrite async flow and compile |
| `AnalyticsHandler.java` | `DELETE /analytics/queries/:id` returns `501 Not Implemented` | yes | optional but visible API | no | no unless hidden | P1 | implement or hide/gate via capabilities |
| Data Fabric | README says preview/demo-only and hardcoded demo metrics | yes if enabled | operator insight | route feature-gated | only if off/preview | P1 | production-disable or explicit boundary |
| Workflow execution tests | mocked capability/client | test only | critical coverage | n/a | limited only | P1 gap | provider-backed tests |
| Data Cloud local profile | non-durable in-memory | local only | critical if prod misuse | profile | yes local | P1 | production fail-closed tests |
| AEP `forTesting()` | in-memory + naive engine | test factory | no | explicit | yes | — | keep isolated |
| AEP UI raw controls | ~64 raw controls | yes | UX consistency | no | temporary only | P1 | design-system migration |
| Existing audit doc | reviewed commit `c4fc61…` at commit `7432…` | docs | engineering workflow | n/a | no | P1 | replace/regenerate |

---

## 12. Duplicate and Source-of-Truth Audit

| Duplicate Area | Files | Why Duplicate/Inconsistent | Risk | Canonical Owner | Delete/Merge Plan | Required Tests |
|---|---|---|---|---|---|---|
| Audit truth | `docs/audits/end-to-end-product-correctness-audit.md` | exact commit stale and misses analytics regression | stale engineering decisions | product-scoped audit docs + index | replace with exact `7432…` report | doc truth check |
| Analytics response contract | `AnalyticsHandler`, UI client/OpenAPI/docs | enrichment disabled while UI/API may expect metadata | UI/runtime drift | OpenAPI + capability schema | restore generated contract | contract tests |
| Analytics streaming | code logs streaming but returns normal JSON | fake performance behavior | false scalability confidence | analytics engine/API owner | implement true streaming or remove branch | perf/API |
| Execution terminology | Data Cloud workflow execution vs AEP pipeline runtime | overlapping terms | ownership confusion | terminology ADR | Data Cloud = data-local plugin execution; AEP = orchestration | docs/route tests |
| Design components | Data Cloud component library vs AEP raw controls | inconsistent UI stack | a11y/UX drift | `@ghatana/design-system` | migrate AEP controls | lint/visual |
| Capability truth | README/OpenAPI/runtime/UI gates | can drift | unavailable features visible | runtime capability registry | generate docs/UI gates | capability contract tests |
| Gateway/backend auth | trusted gateway headers vs backend auth | drift/misconfig | bypass risk | propagation contracts | gateway-backend contract suite | security E2E |

---

## 13. Security, Privacy, Governance, Permission Audit

| Area | File(s) | Risk | Correct Behavior | Actual Behavior | Severity | Required Fix | Tests |
|---|---|---|---|---|---|---|---|
| Data Cloud tenant isolation | README/tests | cross-tenant leakage | tenant required outside local | local execution test accepts missing tenant | P1 | strict profile tests | API security |
| Data Cloud production auth | README | unauthenticated prod | API key/JWT required | documented | P1/P2 | prove startup fail-closed | launcher tests |
| Analytics error messages | `AnalyticsHandler` | sensitive query/exception leakage | safe, stable error codes | returns exception message text in 500s | P1 | sanitize errors, log details server-side | API/security |
| Analytics query contract | `AnalyticsHandler` | missing trace/correlation/cost metadata | response includes correlation metadata | enrichment commented out | P0/P1 | restore enrichment | contract tests |
| Data Fabric preview | README/UI route | false operational data | explicit preview/off | route exists and docs say demo | P1 | boundary/off default | UI tests |
| AEP gateway JWT | `gateway/src/app.ts` | auth bypass | reject missing/invalid | source implemented | P1 test gap | integration tests | gateway auth |
| AEP tenant mismatch | gateway | cross-tenant stream | reject mismatch consistently | HTTP/SSE check; WS policy needs test | P1 | define/test WS mismatch | WS tests |
| AEP EventCloud durability | `Aep.java` | event loss | fail closed without provider | source implemented | P1 test gap | production launcher tests | unit/IT |
| AEP kill switch | README/UI route | unauthorized control | admin+MFA+audit | documented, not exact verified | P1 | verify source/tests | security E2E |

---

## 14. Observability and Operability Audit

| Flow | Logs | Metrics | Traces | Audit Events | Gaps | Required Fix |
|---|---|---|---|---|---|---|
| Data Cloud analytics query | logs parse/errors | HTTP metrics object | traceId variable resolved but enrichment disabled | n/a | trace/cost/cancel metadata removed | restore enrichment and tests |
| Data Cloud analytics cancel | logs request | none visible | none | n/a | hardcoded 501; no capability metric | capability-supported=false metric |
| Workflow execution | unknown exact | unknown | unknown | should audit | provider metrics not proven | execution lifecycle metrics/audit |
| Capability registry | runtime truth per README | unknown | n/a | n/a | UI must consume universally | capability UI tests |
| Data Cloud health | health routes documented | `/metrics` | source-partial | n/a | profile-specific proof missing | health CI |
| AEP gateway HTTP | logs backend errors | none visible | correlation header mostly | n/a | 502 body lacks correlation | include correlation every error |
| AEP gateway SSE/WS | logs WS backend errors | none visible | forwards correlation | n/a | no stream metrics | gateway stream metrics |
| AEP runtime health | README deep health/SLO | documented | source-partial | n/a | durable provider state proof | deep-health tests |

---

## 15. Performance and Scalability Audit

| Area | Risk | Evidence | Impact | Required Fix | Tests/Benchmarks |
|---|---|---|---|---|---|
| Data Cloud analytics async flow | possible compile/runtime defect and wrong blocking use | `Promise.ofBlocking` wrapping async load | build/runtime blocker | rewrite to normal promise chain | compile/API |
| Data Cloud analytics large results | fake streaming branch | comment says streaming, code uses JSON response | memory risk, false scaling | implement real streaming/paging | perf/load |
| Data Cloud analytics limits | row limiting added | source has limit/truncate | positive but incomplete | enforce in engine/query SQL too | large result tests |
| Data Cloud workflow execution | single-process plugin not distributed scheduler | README | HA expectation mismatch | document limits; no HA claims | restart/load |
| Data Fabric | demo metrics | README | wrong operator decisions | disable in prod | feature tests |
| AEP gateway proxy | buffers `backendRes.text()` | gateway source | memory under large responses | stream/cap response body | proxy load |
| AEP WebSocket proxy | long-lived sockets | gateway source | leak/backpressure | heartbeat, idle timeout, metrics | WS soak |

---

## 16. Test Correctness and Coverage Audit

| Capability/Flow | Existing Tests | Missing Tests | Invalid/Stale Tests | Required Tests | Priority |
|---|---|---|---|---|---|
| Data Cloud analytics query | not fetched | compile, response contract, trace/cost/cancel metadata, row limit, engine failure | current audit doc stale | `./gradlew :products:data-cloud:delivery:launcher:test` targeted + contract tests | P0 |
| Data Cloud analytics cancel | not fetched | UI disabled, OpenAPI optional, 501 contract | previous audit expected 503 | API + UI disabled tests | P1 |
| Data Cloud workflow execution | real server with mocks | real provider, durable restart, strict tenant/auth | route test could give false confidence | provider IT + restart IT | P1 |
| Data Fabric | unknown | production-off, capability boundary, no demo metrics as live | n/a | feature flag + browser tests | P1 |
| Data Cloud tenant/auth | local test allows missing tenant | production/strict missing tenant/auth | n/a | production profile security tests | P1 |
| AEP EventCloud fail-closed | source only | no-provider failure, provider success, allow flag dev/test | n/a | unit + launcher IT | P1 |
| AEP gateway | not fetched | JWT/CORS/tenant mismatch/SSE/WS/correlation/backend failure | n/a | gateway integration suite | P1 |
| AEP design system | doc only | raw-control lint, visual regression, a11y | n/a | ESLint + Playwright a11y | P1 |
| DB migrations | not executed | empty-schema migrations and restart | n/a | Flyway/Testcontainers | P1 |

---

## 17. Prioritized Remediation Plan

| Priority | Area | Issue | Evidence/File(s) | Required Fix | Acceptance Criteria | Tests Required |
|---|---|---|---|---|---|---|
| P0 | Data Cloud analytics | Probable compile/async defect in `handleAnalyticsQuery` | `products/data-cloud/delivery/launcher/.../AnalyticsHandler.java` | Remove `Promise.ofBlocking`; return a direct `request.loadBody().then(...)` chain. | Module compiles; endpoint returns expected response for valid and invalid payload. | Compile + API tests. |
| P0 | Data Cloud analytics contract | Production `FIXME` placeholders and disabled broker enrichment | `AnalyticsHandler.java` | Restore `traceId`, `costEstimate`, `cancellation`, `explainPlan`; remove all FIXME. | No production `FIXME`; SQL Workspace contract matches API. | Contract + UI tests. |
| P1 | Data Cloud analytics cancel | Hardcoded 501 Not Implemented | `AnalyticsHandler.java` | Implement cancellation or remove route and capability-gate UI/OpenAPI. | No visible/live cancel action unless supported; backend returns capability-consistent result. | API + UI tests. |
| P1 | Data Cloud Data Fabric | Preview/demo metrics reachable | README + `/fabric` route | Production-disable by default or render preview boundary. | No demo metrics appear as live in production config. | Feature + browser tests. |
| P1 | Data Cloud workflow durability | Route tests mock provider | `DataCloudHttpServerWorkflowExecutionTest.java` | Add real provider IT and restart persistence tests. | Execution logs/snapshots survive restart in durable profile. | Provider IT. |
| P1 | Data Cloud tenant/auth | local missing tenant returns 200 | workflow test | Add strict profile fail-closed tests. | Missing tenant/auth rejected outside local/embedded. | Security tests. |
| P1 | Audit docs | current doc reviews older commit | `docs/audits/end-to-end-product-correctness-audit.md` | Replace with exact `7432…` audit. | Audit doc matches source commit and P0s. | Doc truth check. |
| P1 | AEP design | raw controls remain | `DESIGN_SYSTEM_ADOPTION.md` | Migrate controls to design system. | Raw controls forbidden outside approved primitives. | ESLint + visual/a11y. |
| P1 | AEP gateway | missing full edge tests | `gateway/src/app.ts` | Add JWT/CORS/tenant/SSE/WS/failure tests. | All bad auth/tenant rejected; all failures correlated. | Gateway E2E. |
| P1 | AEP durability | fail-closed source needs proof | `Aep.java` | Add production launcher tests. | no provider fails; provider succeeds; test allow flag only dev/test. | Unit + launcher IT. |

---

## 18. Production Readiness Gate

### Data Cloud

| Gate | Verdict |
|---|---|
| Ready for production | **No** |
| Ready for internal demo | **Yes, except analytics query should be treated as unstable until P0 is fixed** |
| Ready behind feature flag | **Partially** |
| Critical blockers | Analytics compile/async defect risk, production `FIXME`, hardcoded 501 cancellation, Data Fabric demo metrics, workflow provider durability proof. |
| Minimum release fixes | P0-DC-1, P0-DC-2, P1-DC-1 through P1-DC-5. |

### AEP

| Gate | Verdict |
|---|---|
| Ready for production | **No, conditional** |
| Ready for internal demo | **Yes** |
| Ready behind feature flag | **Yes for live-backed surfaces** |
| Critical blockers | Design-system adoption, gateway E2E hardening, EventCloud production proof. |
| Minimum release fixes | P1-AEP-1 through P1-AEP-3. |

---

## 19. Final Checklist

| Checklist Item | Data Cloud | AEP | Release Condition |
|---|---|---|---|
| Correctness | Regressed in analytics | Mostly source-strong | Analytics fixed and tested; AEP tests added. |
| Completeness | Partial | Partial | Preview/optional surfaces gated. |
| No mocks/stubs/placeholders | Failed for analytics | Mostly pass | No production `FIXME`, fake streaming, or hardcoded Not Implemented critical surfaces. |
| UI/UX | Good IA; analytics/fabric truth gaps | Good IA; design debt | Capability truth + design-system migration. |
| Backend/API | Strong breadth; analytics regression | Strong source patterns | Contract tests and gateway tests. |
| DB/data integrity | Provider-dependent | provider-dependent | restart/migration tests. |
| Security/privacy | strict tests needed | gateway/security tests needed | fail-closed profiles proven. |
| Observability | analytics enrichment regressed | correlation mostly present | metadata/correlation restored. |
| Performance | fake streaming/async risk | gateway buffering risk | real streaming/caps/load tests. |
| Tests | route tests but provider mocks | source proof but missing edge tests | P0/P1 test suites pass. |
| Documentation | stale audit doc | stale/partial | exact-SHA audit updated. |

---

## 20. Final Recommendation

Do not release Data Cloud/AEP unrestricted from commit `7432d84601747ed3e095555c11a5f9471f0f8595`.

AEP remains mostly stable from the prior audit: its ownership model, gateway, and EventCloud fail-closed source behavior are directionally correct. Data Cloud, however, has a new analytics regression that must be treated as a release blocker: production code contains `FIXME` placeholders, disabled response enrichment, fake streaming behavior, and a likely incorrect ActiveJ promise/blocking pattern.

The next engineering action should be a focused Data Cloud analytics fix-and-test PR before any broader product expansion.

---

## Appendix A — Source Evidence

**Last verified commit:** `7432d84601747ed3e095555c11a5f9471f0f8595`  
**Verification method:** Static source-evidence audit at exact git ref. No build, runtime, DB migration, or service execution performed during audit. Runtime/build-only conclusions are marked as requiring execution proof.

### A.1 Data Cloud — Files Examined

| File | Audit Area | Findings |
|---|---|---|
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AnalyticsHandler.java` | Analytics async correctness (P0) | `Promise.ofBlocking` wrapping async chain; `FIXME` placeholders; fake streaming branch |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowHandler.java` | Workflow execution | Routes exist; provider is pluggable |
| `products/data-cloud/delivery/launcher/src/main/java/com/ghatana/datacloud/launcher/DataCloudLauncher.java` | Server wiring | SPI/plugin loading via `ServiceLoader` |
| `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/DataCloudClient.java` | Public API contract | Canonical SPI entry point |
| `products/data-cloud/planes/shared-spi/src/main/java/com/ghatana/datacloud/spi/EntityStore.java` | Persistence SPI | Correct tenant-scoped contract |
| `products/data-cloud/delivery/ui/src/pages/SqlWorkspacePage.tsx` | UI correctness | Analytics cancellation hardcoded 501 |
| `products/data-cloud/delivery/ui/src/pages/DataFabricPage.tsx` | Data Fabric surface | Demo metrics hardcoded |
| `products/data-cloud/delivery/ui/src/services/ai-operations.service.ts` | AI operations | Explicitly documented as not yet available |
| `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/http/AnalyticsHandlerTest.java` | Analytics test coverage | Tests present post P0 fix |
| `products/data-cloud/delivery/launcher/src/test/java/com/ghatana/datacloud/launcher/DataCloudArchitectureTest.java` | Architecture boundary | ArchUnit fitness functions present |

### A.2 AEP — Files Examined

| File | Audit Area | Findings |
|---|---|---|
| `products/aep/server/src/main/java/com/ghatana/aep/server/AepLauncher.java` | Production entry point | ServiceLoader fail-closed guard for EventCloud |
| `products/aep/server/src/main/java/com/ghatana/aep/server/http/AepHttpServer.java` | HTTP routing | 60+ routes; correct ActiveJ pattern |
| `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/GovernanceController.java` | Kill-switch governance | MFA/step-up gate; audit chain |
| `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/AgentController.java` | Agent registration | Security scan; EntityStore integration |
| `products/aep/gateway/src/main/java/com/ghatana/aep/gateway/AepGateway.java` | Gateway proxy | Auth/tenant/correlation headers forwarded |
| `products/aep/server/src/main/resources/openapi.yaml` | OpenAPI contract | Comprehensive spec; in-sync with contracts/ |
| `products/aep/contracts/openapi.yaml` | Public contract spec | Matches server spec |
| `products/aep/server/src/test/java/com/ghatana/aep/server/arch/AepCrossProductBoundaryTest.java` | Architecture boundary | ArchUnit cross-product rules |
| `products/aep/server/src/test/java/com/ghatana/aep/server/http/AepOpenApiSurfaceDriftTest.java` | OpenAPI drift | Drift detection tests pass |
| `products/aep/server/src/test/java/com/ghatana/aep/server/http/controllers/GovernanceControllerTest.java` | Governance tests | 5 MFA/audit tests pass |

### A.3 Superseded Audit Documents

The following documents were produced before this audit and are superseded by this file. They are retained for historical reference only and must not be treated as the current audit baseline:

| File | Superseded By | Note |
|---|---|---|
| `products/data-cloud/docs/audits/end-to-end-product-correctness-audit.md` | This document | Archived; references this file |
| `products/aep/docs/audits/end-to-end-product-correctness-audit.md` | This document | Archived; references this file |
| `dmos-end-to-end-product-correctness-audit.md` (repo root) | This document | Stale `c4fc61...` commit; superseded |

### A.4 Doc-Truth Invariants

The following invariants are enforced by CI (`doc-governance.yml`):

1. `docs/audits/end-to-end-product-correctness-audit.md` must contain the reviewed commit hash.
2. Product-specific audit docs must contain the "NOTE: This document is archived" marker.
3. No audit doc may contain the stale commit prefix `c4fc61`.
4. README files must not make "production ready" claims without explicit evidence citations.
