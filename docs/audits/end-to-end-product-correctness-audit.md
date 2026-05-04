# Ghatana Data Cloud + AEP — Ultra-Strict End-to-End Audit

**Repository:** `samujjwal/ghatana`  
**Reviewed commit:** `8e27816d76271c52ed4f35cd083db2e441f82340`  
**Target roots:** `products/data-cloud`, `products/aep`  
**Execution method:** source-evidence audit through the GitHub connector at the exact ref, plus the uploaded ultra-strict audit prompt. I did not clone the repo locally or run Gradle, pnpm, Playwright, DB migrations, or services; any runtime-only conclusion is marked as requiring execution proof.

---

## 1. Executive Summary

| Dimension | Data Cloud | AEP | Combined Verdict |
|---|---:|---:|---|
| Correctness | B | B+ | Good source evidence; release still needs runtime/provider proof. |
| Completeness | B | B | Broad surfaces exist; preview/degraded features need stricter capability truth. |
| Ghatana architecture consistency | B+ | B+ | Ownership boundaries are mostly aligned with Ghatana rules. |
| Ghatana design consistency | B | C+ | Data Cloud UI architecture is cleaner; AEP has explicit design-system migration debt. |
| Production mock/stub risk | Medium | Low-Medium | No current source-confirmed P0 fake-critical path; local/preview/optional surfaces still need gates. |
| Security/privacy/governance | B+ | B+ | Strong fail-closed patterns, but profile-specific tests must prove them. |
| Observability/operability | B | B+ | Health/metrics/runtime truth exist; degraded-state and gateway E2E metrics need hardening. |
| Test authenticity | B- | B | Older test-theatre findings are partly stale; real provider/durable E2E remains missing. |
| Release readiness | Conditional | Conditional | Internal demo ready; not unrestricted-production ready. |

**Production readiness:** No, not for unrestricted production.  
**Internal demo readiness:** Yes, with preview/degraded capability disclosure.  
**Feature/capability-gated release:** Yes, for live-backed surfaces only.

### Current top P1 issues

| ID | Product | Issue | Required Fix |
|---|---|---|---|
| P1-DC-1 | Data Cloud | Data Fabric is documented as preview/demo-only and uses hardcoded demo metrics when real metrics are still in development. | Production-disable or show an explicit runtime capability/preview boundary. |
| P1-DC-2 | Data Cloud | Workflow execution routes and `WorkflowExecutionCapability` exist, but inspected tests use mocked capability/client and do not prove real provider durability. | Add real provider integration tests and restart-persistence tests for execution snapshots/logs/checkpoints. |
| P1-DC-3 | Data Cloud | Local-profile execution route accepts missing tenant; production tenant/auth fail-closed behavior must be explicitly tested. | Add strict/production profile tests that missing tenant/auth fails. |
| P1-DC-4 | Data Cloud | Analytics cancellation is unsupported and returns service unavailable; UI/OpenAPI must not imply live cancellation. | Drive cancel UI and OpenAPI from `cancellation.supported=false` / capabilities. |
| P1-DC-5 | Data Cloud | Checked-in Data Cloud audit doc is stale relative to exact source. | Replace/regenerate the audit doc. |
| P1-AEP-1 | AEP | AEP UI design-system adoption is incomplete. | Migrate raw controls to `@ghatana/design-system`; enforce lint/visual/a11y checks. |
| P1-AEP-2 | AEP | AEP EventCloud fail-closed source behavior is strong, but production-launcher tests must prove it. | Add no-provider fail, provider success, and dev/test allow-flag tests. |
| P1-AEP-3 | AEP | AEP checked-in audit doc is stale and still reports prior P0s. | Replace/regenerate the audit doc. |
| P1-AEP-4 | AEP | Gateway is improved, but needs full JWT/CORS/tenant/correlation/SSE/WS/backend-failure tests. | Add gateway integration suite. |

No current source-confirmed P0 was found in the inspected exact-ref files.

---

## 2. Scope and Method

### Reviewed evidence

- `products/data-cloud/README.md`
- `products/data-cloud/ui/ARCHITECTURE.md`
- `products/data-cloud/ui/src/App.tsx`
- `products/data-cloud/ui/src/routes.tsx`
- `products/data-cloud/launcher/src/main/java/.../DataCloudRouterBuilder.java`
- `products/data-cloud/launcher/src/main/java/.../handlers/AnalyticsHandler.java`
- `products/data-cloud/launcher/src/main/java/.../plugins/WorkflowExecutionCapability.java`
- `products/data-cloud/launcher/src/test/java/.../DataCloudHttpServerWorkflowExecutionTest.java`
- `docs/audits/end-to-end-product-correctness-audit.md`
- `products/aep/README.md`
- `products/aep/ui/DESIGN_SYSTEM_ADOPTION.md`
- `products/aep/ui/src/App.tsx`
- `products/aep/aep-engine/src/main/java/com/ghatana/aep/Aep.java`
- `products/aep/gateway/src/app.ts`
- `products/aep/docs/audits/end-to-end-product-correctness-audit.md`
- `docs/architecture/ARCHITECTURE_RULES.md`
- `docs/DATA_CLOUD_OWNERSHIP_CLARIFICATION.md`

### Excluded

- Generated/cache/vendor artifacts.
- Local test execution and runtime startup.
- Unrelated products except shared architecture/design constraints.

---

## 3. Complete Product Inventory

### 3.1 Data Cloud UI Inventory

| UI Item | File(s) | Purpose | User Actions | Dependencies | Status | Issues |
|---|---|---|---|---|---|---|
| Root providers | `ui/src/App.tsx` | App shell, QueryClient, Jotai, theme, toast, onboarding | app boot/onboarding | routes, session bootstrap | Complete | Need onboarding E2E and role/tenant proof. |
| `/` | `routes.tsx`, `IntelligentHub` | Command center | navigate to data/query/workflows/trust | launcher APIs | Complete | None from route source. |
| `/data` | `DataExplorer` | Unified data explorer | browse/filter/select | entities/collections APIs | Complete | Needs large dataset proof. |
| `/data/new` | `CreateCollectionPage` | Create collection | submit/cancel | entity/collection API | Complete | Duplicate-submit/concurrency tests needed. |
| `/data/:id/edit` | `EditCollectionPage` | Edit collection | save/cancel | entity/collection API | Complete | Concurrency tests needed. |
| `/pipelines` | `WorkflowsPage` | Pipeline list/center | list/select/create | pipeline API | Mostly complete | Execution must be capability-gated. |
| `/pipelines/new` | `SmartWorkflowBuilder` | Intent-to-workflow draft | prompt/generate/review/save | AI draft + pipeline API | Partial-complete | Low-confidence clarification still limited per README. |
| `/pipelines/:id` | `WorkflowDesigner` | Pipeline design/detail | inspect/edit/execute | pipeline + execution API | Partial | Execution depends on registered capability/provider. |
| `/query` | `SqlWorkspacePage` | SQL/NLQ workspace | run/explain/query | analytics APIs | Complete/optional | Cancellation unsupported; UI must reflect. |
| `/trust` | `TrustCenter` | Governance/trust | classify/purge/redact/review | governance APIs | Partial | Broader policy/access review lifecycle incomplete. |
| `/insights` | `InsightsPage` | Runtime/operator diagnostics | inspect metrics/capabilities | capability/metrics APIs | Complete | Verify no fake metrics. |
| `/alerts` | `AlertsPage` | Alert triage | acknowledge/resolve/escalate | alerts + SSE | Complete | Role/capability-gated. |
| `/operations`, `/operations/jobs` | operations pages | Runtime/job diagnostics | inspect jobs/tools | operations/job APIs | Partial | Runtime E2E needed. |
| `/events` | `EventExplorerPage` | Event stream explorer | filter/inspect | events/SSE | Complete | Long-lived stream tests needed. |
| `/memory` | `MemoryPlaneViewerPage` | Memory plane | browse/retain/delete | memory APIs | Complete | Privacy/tenant tests needed. |
| `/entities` | `EntityBrowserPage` | Entity browser | browse/detail | entity APIs | Complete | Role/capability-gated. |
| `/context` | `ContextExplorerPage` | Context/RAG explorer | query/context actions | context/RAG APIs | Complete | Governance boundary tests needed. |
| `/fabric` | `DataFabricPage` | Data fabric topology | inspect fabric/connectors | fabric/capability APIs | Preview | P1 preview/demo risk. |
| `/agents` | `AgentPluginManagerPage` | Agent catalog/plugin view | browse/inspect | agent catalog APIs | Partial/complete | Clarify Data Cloud agent catalog vs AEP agent runtime. |
| `/settings` | `SettingsPage` | Admin settings | update settings | settings APIs | Complete | Must require durable store in production. |
| `/plugins`, `/plugins/:id` | plugin pages | Plugin lifecycle | enable/disable/upgrade | plugin APIs | Partial | Upgrade/sandbox/conformance capability truth needed. |
| `/connectors` | `DataConnectorsPageWrapper` | Connector management | create/edit connector | connector APIs | Unknown/partial | Delete route status needs exact runtime test. |

### 3.2 AEP UI Inventory

| UI Item | File(s) | Purpose | User Actions | Dependencies | Status | Issues |
|---|---|---|---|---|---|---|
| Root App/router | `ui/src/App.tsx` | Outcome shell and routes | navigation | gateway APIs/AuthContext | Complete | Raw controls/design migration debt. |
| `/operate` | `MonitoringDashboardPage` | Runtime monitoring | inspect runs/failures | AEP runtime APIs | Complete | Needs gateway E2E. |
| `/operate/costs` | `CostDashboardPage` | Cost visibility | view spend/budget | cost APIs | Partial/complete | Synthetic fallback must be verified/disclosed. |
| `/operate/reviews` | `HitlReviewPage` | HITL review | approve/reject/escalate | HITL APIs/SSE | Complete | SLA escalation tests needed. |
| `/operate/runs/:runId` | `RunDetailPage` | Run evidence/detail | cancel/inspect | runs API | Complete | Durability depends on Data Cloud/EventLogStore. |
| `/operate/operations` | `OperationCenterPage` | Operations view | inspect/cancel/retry | operation APIs | Complete | Long-running tests needed. |
| `/build/pipelines` | `PipelineListPage` | Pipeline list | create/edit/delete/publish/rollback | pipeline APIs | Complete | Version field must be guaranteed in UI. |
| `/build/pipelines/new` | `PipelineBuilderPage` | Build pipeline | NLQ/manual create | pipeline/NLQ APIs | Complete | AI fallback/provenance tests needed. |
| `/build/patterns` | `PatternStudioPage` | Pattern/learning studio | create/test/learn | pattern/learning APIs | Complete | Route `/learn/episodes` redirects here. |
| `/learn/memory` | `MemoryExplorerPage` | Memory explorer | inspect/search | memory APIs | Complete | Tenant/privacy tests. |
| `/govern` | `GovernancePage` | Governance/policies | kill switch/policy eval | governance APIs | Conditional | MFA/step-up production proof needed. |
| `/govern/privacy` | `PrivacyRequestPage` | Privacy requests | GDPR/CCPA actions | compliance APIs | Complete | Deletion verification E2E needed. |
| `/catalog/agents` | `AgentRegistryPage` | Agent registry/detail | register/execute/inspect | agent APIs | Complete | Security scan regex hardening P2. |
| `/catalog/marketplace` | `AgentMarketplacePage` | Marketplace | install/review | marketplace APIs | Complete | Install rollback tests. |
| `/catalog/workflows` | `WorkflowCatalogPage` | Workflow catalog | browse/use | workflow APIs | Complete | Role tests. |
| Auth pages | login/callback/session | Auth/session | login/callback/renew | gateway/auth | Complete | Token freshness/gateway rejection tests. |

### 3.3 Backend/API Inventory Summary

| Backend Item | Product | Expected Behavior | Status | Issues |
|---|---|---|---|---|
| Health/probes/metrics | Data Cloud | `/health`, `/ready`, `/live`, `/info`, `/metrics` | Source-complete | Needs profile runtime proof. |
| Entities/events | Data Cloud | tenant-scoped CRUD, search, event append/query/stream | Source-complete | Durability profile-dependent. |
| Pipeline metadata | Data Cloud | CRUD pipeline/checkpoints | Source-complete | Update/delete tests needed. |
| Workflow execution | Data Cloud | execute/list/detail/log/cancel/retry/rollback/checkpoint/restore | Source-partial | Mocked capability test; add real provider/durability proof. |
| Analytics | Data Cloud | query/result/plan/aggregation/explain | Source-complete/optional | Cancel unsupported; contract/UI must show unsupported. |
| Governance | Data Cloud | retention, purge, redact, compliance | Source-partial | Broader lifecycle incomplete per README. |
| Capabilities | Data Cloud | runtime truth surface | Source-complete | UI must consume consistently. |
| AEP engine factory | AEP | durable provider or explicit dev/test fallback | Source-strong | Add production-launcher tests. |
| AEP gateway | AEP | JWT/CORS/proxy/SSE/WS/tenant/correlation | Source-improved | Full integration suite missing. |
| AEP runtime APIs | AEP | pipeline/agent/HITL/governance/analytics/reporting | README-proven | Need exact controller/runtime tests before release. |

### 3.4 DB / Persistence Inventory Summary

| DB/Persistence Item | Product | Purpose | Status | Issues |
|---|---|---|---|---|
| EntityStore | Data Cloud | tenant entity storage | Implemented | Provider-dependent. |
| EventLogStore local | Data Cloud | dev event log | In-memory | Dev only; production must fail closed. |
| EventLogStore sovereign | Data Cloud | single-node durable H2 | Documented implemented | Restart tests needed. |
| EventLogStore standard/Kafka | Data Cloud | durable distributed event log | Documented | Provider/topology proof required. |
| Workflow execution provider | Data Cloud | run snapshots/logs/checkpoints | Contract exists | Real provider/restart proof needed. |
| AEP EventCloud provider | AEP | durable runtime event store | Fail-closed source behavior | Test required. |
| AEP run history | AEP | durable run evidence | Requires Data Cloud/EventLogStore | Startup/restart tests required. |
| Migrations | Both | fresh schema setup | Unknown from this audit | Run exact migration inventory. |

---

## 4. Product Behavior Map

| Capability | Product | Persona | Problem Solved | Expected UX | Expected Backend/Data Behavior | Success Criteria |
|---|---|---|---|---|---|---|
| Trusted entity storage | Data Cloud | Data engineer/operator | Manage canonical records | Simple create/browse/edit/delete | Validate, persist, tenant-isolate, version | Accurate data visible with history. |
| Event persistence | Data Cloud | Platform/SRE | Event history and streams | Event explorer/streams | Append-only durable provider in prod | Events survive restart. |
| SQL/NLQ analytics | Data Cloud | Analyst | Query data quickly | SQL workspace with explain/suggestions | Real analytics or clear unavailable state | No fake rows; capability truth. |
| Data Fabric topology | Data Cloud | Operator/admin | Understand data topology | Fabric canvas/connectors | Real topology metrics | No demo metrics as live truth. |
| Governance/trust | Data Cloud | Compliance/admin | Retention, purge, redaction | Trust Center with safe actions | Auditable governance operations | Destructive actions confirmed/audited. |
| Data-local workflow execution | Data Cloud | Data engineer | Run data workflow/plugin | Pipeline execution and logs | Provider executes/persists snapshots/logs | Durable profile keeps execution history. |
| Agentic runtime | AEP | Operator/agent engineer | Run and govern agents | Operator cockpit/build/catalog | AEP orchestrates runs/agents/HITL | Run state/evidence durable in production. |
| HITL | AEP | Reviewer | Review low-confidence actions | Review queue | Approve/reject/escalate and learn | Decisions traceable. |
| Runtime governance | AEP | Admin/SRE | Stop/degrade unsafe automation | Kill switch/degrade/policy eval | MFA/role/audit | Immediate safe state with proof. |
| Gateway edge | AEP | User/integrator | Secure edge to AEP | API/SSE/WS through gateway | JWT/CORS/tenant/correlation | Invalid auth/tenant rejected. |

---

## 5. Requirement-to-Implementation Traceability Matrix

| Requirement | UI | Actions | Backend | Domain/Service | DB | Tests | Observability | Status |
|---|---|---|---|---|---|---|---|---|
| Entity CRUD | `/data`, `/entities` | CRUD | entity routes | entity handlers | EntityStore | launcher tests named in audit | metrics/traces | Source-partial complete |
| Event append/query | `/events` | inspect/stream | event/SSE routes | EventLogStore/SSE | EventLogStore | unknown exact | streams/metrics | Source-partial complete |
| Workflow execute | `/pipelines/:id` | execute/cancel/logs | `WorkflowExecutionHandler`/capability | plugin provider | execution store | mocked capability route test | execution logs | Partially proven |
| Analytics query | `/query` | query/explain | `AnalyticsHandler` | `AnalyticsQueryEngine` | analytics engine | unknown | traceId/cost | Complete/optional |
| Analytics cancel | `/query` if exposed | cancel | `handleAnalyticsCancelQuery` | unsupported | none | unknown | 503 retry semantics | Backend unsupported; UI must gate |
| Data Fabric | `/fabric`, `/connectors` | inspect/connectors | fabric/connector routes | registry/metrics | connector store | unknown | capability registry | Preview/gated required |
| Trust Center | `/trust` | purge/redact/classify | governance routes | lifecycle | governance/audit | governance tests named | audit | Partial-complete |
| AEP pipeline runtime | `/build/pipelines` | CRUD/publish/rollback | pipeline controller | repo/runtime | DataCloud/EventLog | README tests referenced | SLO/deep health | Complete per docs, execution proof needed |
| AEP EventCloud durability | runtime | startup | `Aep.create()` | ServiceLoader provider | EventCloud | missing | deep health | Source-proven guard; tests required |
| AEP gateway | all protected UI/API | API/SSE/WS | Fastify gateway | JWT/proxy logic | n/a | missing | correlation | Source-proven improved; tests required |
| AEP design consistency | all UI | all controls | n/a | theme/design system | n/a | missing | n/a | Incomplete |

---

## 6. End-to-End Journey Correctness Audit

| Journey | Entry Point | Expected Outcome | Source-observed Actual | Correct? | Complete? | Mock/Stub Risk | Gaps | Severity | Fix | Tests |
|---|---|---|---|---|---|---|---|---|---|---|
| Data Cloud onboarding | app load | onboarding only when incomplete | wizard rendered from App state | Likely | Partial | Low | role/tenant/a11y tests | P2 | test onboarding lifecycle | Browser E2E |
| Create collection | `/data/new` | collection persists and appears | route exists | Likely | Partial | Low | API/DB proof | P2 | add create/read/delete E2E | API/browser |
| Execute pipeline | `/pipelines/:id` | run starts, logs/status visible | routes/capability exist; tests mock provider | Partial | No | Medium | real provider/restart missing | P1 | durable provider + capability UI gate | Provider IT/browser |
| Missing workflow capability | `/pipelines/:id` | UI disables; backend blocks | tests show backend unavailable behavior | Backend likely | UI unknown | Medium | disabled UI test missing | P1 | UI consumes `/capabilities` | UI/API |
| SQL query | `/query` | query executes or unavailable state | handler supports query; null engine 503 | Yes | Partial | Low | UI 503 handling | P1/P2 | explicit unavailable UX | UI/API |
| Query cancellation | `/query` | cancel only if supported | backend says unsupported via 503 | Backend yes | UI unknown | Low | OpenAPI/UI gate | P1 | hide cancel unless supported | UI/contract |
| Fabric topology | `/fabric` | real topology or preview boundary | docs say preview/demo | Partial | No | High if visible as live | production flag/boundary | P1 | feature/capability boundary | browser |
| Trust purge/redaction | `/trust` | dry run/confirm/audit | README says wired | Likely | Partial | Low | audit proof | P1/P2 | audit assertions | API E2E |
| AEP login/session | `/login` | authenticated API session | routes exist; gateway auth | Likely | Partial | Low | gateway E2E | P1 | add rejection/expiry tests | gateway/UI |
| AEP pipeline edit | `/build/pipelines/:id/edit` | stale writes rejected | README says 409/428 | Likely | Partial | Low | exact controller/UI proof | P1/P2 | verify version always sent | API/UI |
| AEP run detail | `/operate/runs/:runId` | durable run evidence | README requires durable provider | Conditional | Partial | Medium | restart proof | P1 | run-history restart tests | E2E |
| AEP WS tail | `/tail/events` | tenant-scoped authenticated tail | gateway forwards auth/tenant/correlation | Source-yes | Needs tests | Low | WS mismatch/backpressure tests | P1 | gateway WS suite | Gateway E2E |
| AEP kill switch | `/govern` | admin+MFA activates/audits | documented | Conditional | Partial | Medium | exact production MFA tests | P1 | verify MFA gate | Security tests |

---

## 7. UI/UX Correctness and Completeness Audit

| UI Area | Finding | Impact | Severity | Required Fix | Tests |
|---|---|---|---|---|---|
| Data Cloud route IA | Simplified IA with core routes and compatibility routes. | Positive | — | Maintain. | Route tests. |
| Data Cloud Data Fabric | Preview/demo-only surface can look production-live. | False operational confidence. | P1 | Capability/feature gate and preview boundary. | Feature + browser tests. |
| Data Cloud workflow actions | Execution provider proof is partial. | Users may see actions before capability is live. | P1 | Bind to `/api/v1/capabilities`. | UI action tests. |
| Data Cloud onboarding | First-run wizard overlays content. | Could block users if state wrong. | P2 | Test role/state/a11y. | Browser tests. |
| AEP outcome IA | Operate/Build/Learn/Govern/Catalog is strong. | Positive | — | Keep. | Route tests. |
| AEP design system | ~64 raw controls reported. | Inconsistent UX/a11y. | P1 | Migrate to design system. | ESLint + visual/a11y. |
| AEP mobile nav | Raw shell button/svg. | Minor consistency issue. | P2 | Use design-system control if available. | Component test. |
| Runtime truth banners | AEP uses runtime truth banner pattern; Data Cloud has capability registry. | Positive but must be universal. | P1/P2 | Apply to all optional surfaces. | UI tests. |

---

## 8. Frontend Action, State, and Data Flow Audit

| Flow | Expected | Actual | Correct? | Complete? | Mock/Stub? | Required Fix | Tests |
|---|---|---|---|---|---|---|---|
| Data Cloud lazy routing | Lazy pages with error/loading states | route config with lazy/error boundaries | Yes | Yes | No | Keep. | Route load tests. |
| Data Cloud optional pages | Gate disabled surfaces | feature gates for several pages | Mostly | Partial | No | Ensure production defaults match backend. | Feature tests. |
| Workflow execute | Call only when capability live | backend ready; UI proof not inspected | Unknown | Partial | Potential UI risk | Add capability gate. | UI action tests. |
| Analytics cancel | Hide unless supported | backend unsupported | Unknown UI | Partial | No backend fake | Gate cancel UI. | UI test. |
| AEP command palette | Navigate canonical routes | route helper list present | Yes | Yes | No | Keep. | keyboard tests. |
| AEP API through gateway | Protected pages use AuthContext/gateway | source-partial | Partial | Partial | No | static no-direct-backend rule. | static + E2E. |
| AEP WS proxy | Forward auth/tenant/correlation | source does this | Yes | Needs tests | No | Add WS tests. | Gateway E2E. |
| AEP raw controls | Use design system | incomplete | No | No | No | migrate controls. | visual/a11y. |

---

## 9. Backend/API/Domain Logic Audit

| Flow | Expected | Actual | Correct? | Complete? | Mock/Stub? | Risk | Fix | Tests |
|---|---|---|---|---|---|---|---|---|
| Data Cloud pipeline routes | CRUD + execution if handler present | source registers conditional execution | Yes | Partial | No | capability mismatch | align handler/capability registry | route/capability |
| Workflow capability | execute/list/get/cancel/retry/logs | interface exists | Yes | Interface only | No | provider absent | provider implementation proof | Provider IT |
| Workflow HTTP tests | prove routes | real server, mocked capability/client | Valid limited | Partial | test mocks | no durability proof | provider-backed tests | restart IT |
| Analytics query | validate tenant/query and submit | implemented; null engine 503 | Yes | Yes | No | query/log sensitivity | security/log review | API/security |
| Analytics cancellation | unsupported cancel | returns service-unavailable | Correct | Partial | No | UI/contract drift | OpenAPI/UI supported=false | contract/UI |
| AEP EventCloud discovery | fail closed | implemented source | Yes | Strong | No | startup provider config | production tests | unit/IT |
| AEP testing fallback | allowed only testing | explicit `allowInMemoryEventCloud(true)` | Yes | Yes | Dev/test only | none | keep isolated | unit |
| AEP gateway auth | reject missing/invalid token | HTTP/SSE/WS checks | Yes | Needs tests | No | auth bypass if untested | gateway tests | E2E |
| AEP gateway tenant | reject mismatch | HTTP/SSE rejects; WS derives tenant | Mostly | Needs tests | No | WS mismatch ambiguity | define/test WS mismatch | E2E |
| AEP gateway errors | structured/correlated | readiness has correlation; some 502 bodies do not | Partial | Partial | No | diagnosability | include correlation everywhere | tests |

---

## 10. Database and Data Integrity Audit

| Model/Operation | Expected Rule | Actual | Correct? | Complete? | Integrity Risk | Performance Risk | Fix | Tests |
|---|---|---|---|---|---|---|---|---|
| Data Cloud local store | dev only, non-durable | documented | Yes | Yes | high if prod misuse | low | production fail-closed | startup tests |
| Sovereign H2 store | durable single-node | documented | source-partial | needs run | medium | single-node | restart tests | sovereign IT |
| Standard provider/Kafka | durable standard mode | documented | source-partial | needs run | high if absent | provider-dependent | production startup tests | launcher IT |
| Workflow execution store | durable snapshots/logs | README claims; test mocks | unknown/partial | partial | medium | unknown | real provider tests | restart IT |
| Analytics results | query/result by ID | handler delegates engine | source-partial | unknown | medium | row limits | row/paging limits | API/perf |
| AEP EventCloud | durable provider or explicit allow flag | source fail-closed | Yes | source-proven | startup config | provider-dependent | launcher tests | unit/IT |
| AEP run history | durable Data Cloud/EventLogStore | documented | source-partial | needs run | high if misconfigured | provider-dependent | restart tests | E2E |
| Migrations | fresh schema migrates | not executed | unknown | unknown | high if gaps | n/a | exact migration inventory | Flyway tests |

---

## 11. Production Mock/Stub/Shortcut Audit

| File / Area | Evidence | Reachable? | Critical? | Feature Flagged? | Allowed? | Severity | Action |
|---|---|---:|---:|---:|---:|---|---|
| Data Fabric | README says preview/demo-only | yes if enabled | important | route gated | only if clearly preview/off | P1 | production flag + boundary |
| Workflow execution tests | mocked capability/client | test only | coverage for critical path | n/a | valid but insufficient | P1 gap | provider/durability tests |
| Analytics cancel | unsupported 503 | yes | optional | no | allowed if UI/docs truthful | P1/P2 | OpenAPI/capability/UI truth |
| Local in-memory profile | documented non-durable | local only | critical if prod misuse | profile | allowed local | P1 | production fail-closed tests |
| AEP raw controls | design debt | yes | UX consistency | no | temporary only | P1 | design migration |
| AEP `forTesting()` | in-memory + naive engine | test/dev | no | explicit allow flag | allowed | — | keep isolated |
| AEP audit doc | stale P0s | docs | engineering workflow | n/a | no | P1 | regenerate |
| Data Cloud audit doc | stale findings | docs | engineering workflow | n/a | no | P1 | regenerate |

---

## 12. Duplicate and Source-of-Truth Audit

| Duplicate Area | Files | Issue | Risk | Canonical Owner | Merge/Delete Plan | Tests |
|---|---|---|---|---|---|---|
| Audit truth | `docs/audits/...`, `products/aep/docs/audits/...` | stale docs contradict source | wasted work / missed gaps | product audit docs + generated index | regenerate and mark old superseded | doc truth CI |
| Execution terminology | Data Cloud vs AEP docs/routes | both use pipeline/execution | ownership confusion | terminology ADR | label Data Cloud as data-local plugin execution; AEP as orchestration | contract tests |
| Design controls | Data Cloud components vs AEP raw controls | inconsistent UI stack | a11y/UX drift | `@ghatana/design-system` | migrate AEP controls | lint/visual |
| Capability truth | README/OpenAPI/runtime/UI gates | drift risk | visible unavailable features | runtime capability registry | generate docs/UI gates from schema | contract tests |
| Gateway/backend auth | gateway trusted headers vs backend trust | drift/misconfig | bypass risk | propagation contracts | gateway-backend contract tests | security E2E |

---

## 13. Security, Privacy, Governance, and Permission Audit

| Area | Risk | Correct Behavior | Actual | Severity | Fix | Tests |
|---|---|---|---|---|---|---|
| Data Cloud tenant isolation | cross-tenant leakage | tenant required outside local | docs say tenant; local execution test permits missing tenant | P1 | strict profile tests | API security |
| Data Cloud production auth | unauthenticated prod | API key/JWT required | documented | P1/P2 | startup proof | launcher tests |
| Data Cloud purge/redaction | data loss/privacy | confirmation + audit | source-partial positive | P1/P2 | audit event tests | API E2E |
| AEP gateway JWT | bypass | reject missing/invalid | implemented | P1 test gap | add tests | gateway auth |
| AEP tenant mismatch | cross-tenant data | reject mismatch | HTTP/SSE implemented; WS needs policy test | P1 | WS mismatch tests | gateway WS |
| AEP EventCloud durability | event loss | fail closed without durable provider | implemented source | P1 test gap | production tests | unit/IT |
| AEP kill switch MFA | unauthorized stop | admin+MFA+audit | documented, not exact verified | P1 | verify production module | security tests |
| Secrets/logs | exposure | env-only, no secret logs | source-partial | P2 | secret scan | CI scan |

---

## 14. Observability and Operability Audit

| Flow | Logs | Metrics | Traces | Audit Events | Gaps | Fix |
|---|---|---|---|---|---|---|
| Data Cloud health | routes present | `/metrics` route | source-partial | n/a | run profile-specific tests | health CI |
| Analytics | errors logged | HTTP metrics object | traceId response | n/a | cancellation capability UI/doc drift | expose supported=false |
| Workflow execution | unknown | unknown | unknown | should audit | real provider metrics not proven | add execution metrics/audit |
| Capability registry | runtime truth per README | unknown | n/a | n/a | UI must universally consume | UI capability tests |
| AEP gateway HTTP | backend errors logged | none visible | correlation for many paths | n/a | some error bodies lack correlation | include correlation everywhere |
| AEP gateway SSE/WS | WS errors logged | none visible | forwards correlation | n/a | no gateway stream metrics | add metrics |
| AEP runtime deep health | documented | SLO metrics documented | source-partial | n/a | durable provider state proof | deep-health test |

---

## 15. Performance and Scalability Audit

| Area | Risk | Evidence | Impact | Fix | Tests |
|---|---|---|---|---|---|
| Data Cloud workflow execution | single-process plugin not distributed scheduler | README | HA expectation mismatch | document limits and no HA claims | load/restart |
| Analytics results | large rows/cancel unsupported | handler returns rows | memory/latency | enforce limits/paging/timeouts | large result tests |
| Streams | long-lived SSE/WS | routes present | stale connections | backpressure/cleanup metrics | soak tests |
| Data Fabric | demo metrics | README | bad decisions | disable preview in prod | n/a |
| AEP gateway proxy | buffers responses via `text()` | source | memory under large responses | stream/cap response bodies | proxy load |
| AEP WS proxy | long-lived socket pairs | source | leak risk | heartbeat/idle timeout/metrics | WS soak |
| HITL scans | potential O(n) | older audit only | scale risk | paginate/limit | load tests |

---

## 16. Test Correctness and Coverage Audit

| Capability | Existing Tests | Missing Tests | Invalid/Stale Tests | Required Tests | Priority |
|---|---|---|---|---|---|
| Data Cloud workflow HTTP | `DataCloudHttpServerWorkflowExecutionTest` | real provider, restart, strict tenant | none in current route test | provider IT + restart IT | P1 |
| Analytics cancel | unknown | UI disabled, OpenAPI optional, 503 contract | stale doc says 501 | contract + UI | P1 |
| Data Fabric | unknown | production-off, capability boundary, preview banner | older audit may be stale | feature + browser | P1 |
| Tenant/auth | tests named in audit | production startup/missing tenant | unknown | security tests | P1 |
| AEP EventCloud fail-closed | source guard | no-provider/provider/allow tests | stale doc says old behavior | unit + launcher IT | P1 |
| AEP gateway | unknown | JWT/CORS/SSE/WS/correlation/backend errors | unknown | gateway integration suite | P1 |
| AEP design | doc notes debt | lint/visual/a11y | n/a | raw-control lint + Playwright | P1 |
| AEP stale P0 docs | docs stale | doc truth checks | stale audit doc | regenerate + current behavior tests | P1 |
| DB migrations | not executed | fresh schema tests | older audit claims gaps | Flyway empty DB test | P1 |

---

## 17. Prioritized Remediation Plan

| Priority | Area | Issue | Evidence/File(s) | Required Fix | Acceptance Criteria | Tests |
|---|---|---|---|---|---|---|
| P1 | Data Cloud UI truth | Data Fabric preview/demo-only risk | README, `ui/src/routes.tsx` | Gate `/fabric` by production flag and capability; show preview boundary. | No production user sees demo metrics as live. | Feature + browser + capability tests. |
| P1 | Data Cloud execution | Real provider/durability not proven | `WorkflowExecutionCapability.java`, `DataCloudHttpServerWorkflowExecutionTest.java` | Provider-backed execution and restart tests. | snapshots/logs/checkpoints survive restart in durable profiles. | Provider/restart IT. |
| P1 | Data Cloud tenant/auth | Local accepts missing tenant | workflow execution test | Strict/production missing tenant/auth tests. | Missing tenant/auth rejected outside local/embedded. | API security. |
| P1 | Data Cloud analytics cancel | Unsupported route could be shown | `AnalyticsHandler.java` | Capability/OpenAPI/UI supported=false. | No cancel action shown unless supported. | Contract/UI. |
| P1 | Data Cloud docs | stale audit doc | `docs/audits/...` | Replace with current audit. | Docs match exact source. | doc truth check. |
| P1 | AEP design | raw controls | `DESIGN_SYSTEM_ADOPTION.md` | Migrate controls to design system. | raw controls only in approved primitives. | lint/visual/a11y. |
| P1 | AEP EventCloud | fail-closed needs tests | `Aep.java` | no-provider fail, provider success, test allow flag. | production cannot start accidentally in-memory. | unit/launcher IT. |
| P1 | AEP gateway | incomplete edge tests | `gateway/src/app.ts` | JWT/CORS/tenant/SSE/WS/correlation tests. | invalid auth/tenant rejected; correlation preserved. | gateway E2E. |
| P1 | AEP docs | stale audit doc | `products/aep/docs/audits/...` | Replace with current audit. | no stale P0s remain. | doc truth check. |
| P2 | Both capability truth | drift risk | README/OpenAPI/runtime/UI | single capability schema drives docs/UI. | no visible unavailable features. | contract tests. |
| P2 | Browser E2E | critical journeys not proven here | not executed | add full Playwright flows. | CI proves real UX outcomes. | browser E2E. |

---

## 18. Production Readiness Gate

### Data Cloud

- **Ready for production:** No, conditional.
- **Ready for internal demo:** Yes.
- **Ready behind feature/capability gates:** Yes.
- **Critical blockers:** Data Fabric preview gating; real workflow execution provider/durability tests; strict production tenant/auth tests; stale audit doc replacement.
- **Minimum release fixes:** P1-DC-1 through P1-DC-5.

### AEP

- **Ready for production:** No, conditional.
- **Ready for internal demo:** Yes.
- **Ready behind feature/capability gates:** Yes.
- **Critical blockers:** design-system migration or approved waiver; EventCloud production fail-closed tests; gateway E2E hardening; stale audit doc replacement.
- **Minimum release fixes:** P1-AEP-1 through P1-AEP-4.

---

## 19. Final Checklist

| Checklist | Data Cloud | AEP | Required Before Release |
|---|---|---|---|
| Correctness | Mostly source-proven | Mostly source-proven | Execute full tests and provider E2E. |
| Completeness | Preview/optional gaps | Design/system proof gaps | Gate incomplete surfaces. |
| No production mocks/stubs | no current source-confirmed P0 | no current source-confirmed P0 | production profile tests. |
| UI/UX | strong IA, preview risks | strong IA, design debt | capability truth + design migration. |
| Backend/API | strong route structure | strong runtime/gateway improvements | contract/gateway E2E. |
| DB/data integrity | profile-dependent | provider-dependent | restart/durability tests. |
| Security/privacy | strong documented posture | strong source posture | tenant/auth/MFA tests. |
| Observability | health/metrics present | deep health/gateway correlation present | degraded metrics/correlation everywhere. |
| Performance | large result/stream tests needed | gateway/WS load tests | load/soak tests. |
| Tests | real route tests but mocks remain | fail-closed/gateway/design tests needed | P1 tests added. |
| Documentation | stale audit doc exists | stale audit doc exists | regenerate docs. |

---

## 20. Final Recommendation

Do not prioritize feature expansion until release-truth hardening is complete.

At commit `8e27816…`, the current source is materially better than some checked-in audit docs imply:

1. AEP normal factory paths use `LinearTrendForecastingEngine`, while `NaiveForecastingEngine` is isolated to test factory paths.
2. AEP now fails closed when no durable EventCloud provider is discovered unless `allowInMemoryEventCloud=true` is explicitly configured.
3. AEP gateway now forwards tenant/correlation/gateway trust headers for WebSocket backend connections.
4. Data Cloud workflow execution routes and capability contracts exist.
5. Data Cloud analytics cancellation now returns service-unavailable semantics rather than the older hardcoded 501 finding.

The remaining work is to make production truth impossible to misrepresent: every UI action must be backed by live capability truth, every durable dependency must fail closed in production, and stale audit documents must be replaced so engineers are not working from obsolete P0s.
