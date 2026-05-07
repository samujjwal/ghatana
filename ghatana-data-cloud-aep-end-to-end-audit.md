# Ghatana Data Cloud + AEP — Ultra-Strict End-to-End Product Audit

**Repository:** `samujjwal/ghatana`
**Reviewed target roots:** `products/data-cloud`, `products/data-cloud/planes/action`
**Reviewed ref observed from GitHub connector:** `be380e9ebc92cece8164ce86f3b97cb6789a4db5`
**Audit basis:** user-provided ultra-strict product audit prompt, current repository source/docs, architecture rules, product READMEs, OpenAPI/API docs, product audit docs, selected source/test files.
**Repo mutation:** none. This file is ready to copy into `docs/audits/end-to-end-product-correctness-audit.md` or split into product-specific audit files.

---

## 1. Executive Summary

### Overall Gate

| Area | Data Cloud | AEP | Combined Verdict |
|---|---:|---:|---|
| Correctness | Conditional B | Conditional B+ | Good direction, but still gated by production-runtime evidence |
| Completeness | B- | B | Broad surface exists; some preview/degraded surfaces require hard product truth gates |
| Ghatana architecture consistency | B | B | Boundaries are mostly correct; must enforce with static gates and integration tests |
| Ghatana design consistency | B | C+ | Data Cloud UI architecture is mature; AEP UI still has raw-element migration debt |
| Mock/stub/demo risk | Medium | Low-Medium | No current P0 test-theatre confirmed; remaining risk is capability/mock-backed validation gaps |
| Production readiness | Not unrestricted-GA ready | Conditional, not unrestricted-GA ready | Ready for internal demo; production only after P1 gates below |

**Production release:** No, not as an unrestricted product suite.  
**Internal demo:** Yes, with explicit disclosure of preview/degraded surfaces.  
**Feature-flagged/role-gated release:** Yes for live, capability-backed surfaces only.

### Top Current Findings

| Priority | Product | Finding | Required Action |
|---|---|---|---|
| P1 | Data Cloud | Workflow execution endpoint/capability now exists, but current inspected test evidence uses mocked capability/client. Production must prove a real durable `WorkflowExecutionCapability` is registered before UI exposes execution as live. | Add real provider/durable integration test, capability-registry gate, and UI disabled state when capability absent. |
| P1 | Data Cloud | Data Fabric is documented as preview/demo-only with hardcoded metrics. | Disable by default in production or show clear preview banner and block critical decisions from demo metrics. |
| P1 | Data Cloud | Trust Center is action-backed for several governance flows, but broader policy CRUD lifecycle remains incomplete. | Hide or disable incomplete policy CRUD actions; backend must return explicit 501/503 and UI must show truthful state. |
| P1 | AEP | `Aep.create()` uses `LinearTrendForecastingEngine`, so old P0 was remediated; however `discoverEventCloud()` falls back to `InMemoryEventCloud`. Production startup must prove durable Data Cloud/EventLogStore guard is always used. | Add production-module test that fails without durable EventLogStore unless explicit allow-in-memory flag is set. |
| P1 | AEP | AEP UI design-system adoption is incomplete; design doc reports only two design-system imports and about 64 raw form controls needing migration. | Migrate raw controls to `@ghatana/design-system`, add lint rule, visual/a11y regression tests. |
| P1 | AEP | Gateway is directionally correct, but WebSocket/SSE/proxy hardening needs stronger tests and tenant/correlation propagation consistency. | Add gateway integration tests for JWT, CORS, tenant mismatch, SSE, WS tenant propagation, backend failure correlation. |
| P1 | Both | Cross-product integration intent is correct, but must be continuously enforced: AEP may consume Data Cloud public contracts/API/SPI; Data Cloud must not import AEP. | Enforce no peer-product imports, DataCloudClient/SPI-only integration, and capability-contract ownership in CI. |

---

## 2. Scope and Method

The pasted audit prompt requires a complete product inventory, behavior map, requirement-to-implementation traceability, end-to-end journey review, UI/UX audit, frontend/backend/DB/test/security/observability/performance audit, mock/stub zero-tolerance scan, duplicate/source-of-truth review, severity model, and production readiness gate.

This report follows that structure but is source-evidence based. I did not clone the repo locally, run Gradle, run pnpm, start services, or execute browser E2E. Any finding requiring runtime proof is marked as a required validation gate rather than falsely claiming pass/fail.

---

## 3. Architecture Consistency Verdict

### Intended ownership is clear and mostly consistent

**Data Cloud intended ownership**

Data Cloud is documented as the Ghatana data foundation. It owns entity storage, event persistence, analytics, governance, lineage, agent memory persistence, plugin-backed pipeline execution, and the HTTP surface other products consume. It explicitly does not own broader agentic orchestration; AEP integrates through Data Cloud contracts/events.

**AEP intended ownership**

AEP is documented as the Agentic Execution Runtime. It owns runtime surfaces for agents, pipelines, run evidence, HITL review, learning, governance/compliance summaries, analytics/reporting/lifecycle, and deployment controls. It is not merely a CEP/pattern engine.

**Cross-product boundary**

The intended dependency direction is: AEP may depend on Data Cloud public contracts/APIs; Data Cloud must not import AEP modules. Runtime integration should happen through Data Cloud-backed persistence and event-log surfaces. Ghatana architecture rules also prohibit peer-product direct imports and require cross-product integration through `platform/contracts` or shared `DataCloudClient` SPI.

### Remaining boundary risks

1. **Execution terminology overlap:** Data Cloud OpenAPI says Data Cloud exposes plugin-backed workflow execution and execution persistence. AEP says AEP owns pipeline runs and orchestration. This can stay consistent only if Data Cloud execution means “data-local plugin-backed workflow execution and persistence,” while AEP owns broader agentic orchestration and runtime governance.
2. **Data Cloud ↔ AEP coupling must be mechanically enforced:** Documentation says the right thing, but release readiness requires static import gates proving Data Cloud does not import AEP and AEP uses Data Cloud only through public client/SPI/contracts.
3. **UI-shell consistency differs:** Data Cloud has a clear two-module UI architecture (`@data-cloud/ui` and `@data-cloud/ui-components`). AEP still has a documented design-system migration backlog.

---

## 4. Product Inventory Summary

### 4.1 Data Cloud UI Inventory

| UI Surface | Status | Risk |
|---|---|---|
| Intelligent Hub `/` | Live primary launcher | Low |
| Data Explorer `/data`, `/data/new`, `/data/:id/edit` | Live | Low |
| Workflows `/pipelines`, `/pipelines/new`, `/pipelines/:id` | Live for CRUD and execution visibility | P1: execution requires real capability evidence/gating |
| SQL Workspace `/query` | Live primary launcher | P1: backing services may degrade; must truthfully report capability |
| Trust Center `/trust` | Partial but action-backed | P1: broader policy CRUD incomplete |
| Insights `/insights` | Operator truth/diagnostics | Low-Medium |
| Alerts `/alerts` | Live operator triage | Low-Medium |
| Operations `/operations` | Operator | Medium |
| Events `/events` | Live event surface | Low |
| Memory `/memory` | Live memory plane | Low-Medium |
| Entity Browser `/entities` | Live | Low |
| Context `/context` | Live | Low |
| Fabric `/fabric` | Preview/demo only | P1 unless disabled/clearly labeled |
| Agents `/agents` | Agent catalog | Medium |
| Settings `/settings` | Admin-only boundary | Medium |
| Plugins `/plugins`, `/plugins/:id` | Live plugin lifecycle | Medium |

### 4.2 Data Cloud Backend/API Inventory

| API Family | Status | Risk |
|---|---|---|
| Health/probes/metrics | Documented | Must verify implementation and production auth posture |
| Entities/search/export/validate/similar | Implemented/documented | Low-Medium |
| Events append/query/SSE | Implemented/documented | Low-Medium |
| Pipelines/checkpoints | Implemented/documented | Medium |
| Workflow execution/cancel/logs/retry/rollback/checkpoints | Endpoint/capability exists | P1 production provider/durability proof |
| Memory/Brain/Learning | Implemented/documented | Medium |
| Analytics/reports/models/features | Implemented but may degrade | P1 capability truth/gating |
| Governance/privacy/lineage/context/data-products | Mostly implemented | P1 for incomplete policy CRUD lifecycle |
| Capabilities/autonomy/plugins/agents | Implemented/documented | Medium |
| Voice/federated/cost/migration/WS | Documented | Medium; needs runtime proof |

### 4.3 AEP UI Inventory

| UI Surface | Status | Risk |
|---|---|---|
| `/operate` monitoring dashboard | Live operator cockpit | Medium |
| `/operate/reviews` HITL | Live | Medium |
| `/operate/costs` cost dashboard | Live | P1 if synthetic fallback undisclosed |
| `/operate/runs/:runId` run detail | Live | Medium |
| `/operate/operations` operation center | Live | Medium |
| `/build/pipelines`, `/build/pipelines/new` | Live | Medium |
| `/build/patterns`, learning tab | Live | Medium |
| `/learn/memory` | Live | Medium |
| `/govern`, `/govern/privacy` | Live | Medium; MFA/guard proof needed |
| `/catalog/agents`, `/catalog/agents/:id`, `/catalog/marketplace`, `/catalog/workflows` | Live | Medium |
| `/login`, `/session-expired`, `/sso/callback` | Live | Medium |
| Design-system adoption | Incomplete | P1 for consistency |

### 4.4 AEP Backend/API Inventory

| API Family | Status | Risk |
|---|---|---|
| Health/readiness/deep health/metrics/SLO | Implemented/documented | Low-Medium |
| Pipeline CRUD/NLQ/versioning/publish/rollback | Implemented/documented | Medium |
| Agents/register/execute/memory | Implemented/documented | Medium |
| Runs/list/detail/cancel | Implemented/documented | Medium |
| HITL pending/approve/reject/escalate | Implemented/documented | Medium |
| Learning episodes/reflection/policies | Implemented/documented | Medium |
| Governance/kill-switch/degradation/policy eval/security scans | Implemented/documented | P1 production MFA/guard proof |
| Analytics/reporting/deployments/patterns | Implemented/documented | Medium |
| Gateway BFF | Implemented | P1 hardening/integration test gaps |

---

## 5. Product Behavior Map

| Capability | Product Owner | Persona | Expected UX | Expected Backend/Data Behavior | Current Assessment |
|---|---|---|---|---|---|
| Entity and collection management | Data Cloud | Data engineer/operator | Create, browse, edit, delete trusted records | Tenant-scoped entity APIs, validation, version/history | Mostly complete |
| Event persistence and query | Data Cloud | Platform/operator | Append, inspect, stream event history | Durable EventLog provider in production | Complete if durable provider configured |
| Analytics/query/reporting | Data Cloud | Analyst/operator | SQL/NLQ query and insights | Capability-aware analytics provider; no fake rows | Conditional |
| Data governance and trust | Data Cloud | Compliance/operator | Retention, PII, purge, redaction, audit | Governed actions with auditable backend | Partial; policy lifecycle incomplete |
| Data product publishing | Data Cloud | Data product owner | Publish/discover/subscribe | Data product descriptors and subscriptions | Implemented/documented |
| Plugin-backed workflow execution | Data Cloud | Data engineer/operator | Execute data-local workflows and inspect logs | WorkflowExecutionCapability + durable execution records | Conditional; needs real provider evidence |
| Agent runtime | AEP | Agent engineer/operator | Register/inspect/execute agents | AEP agent store, security scan, memory integration | Mostly complete |
| Pipeline orchestration | AEP | Operator/engineer | Build, version, publish, run, cancel pipelines | AEP pipeline repository/run history, Data Cloud-backed durability in production | Mostly complete; durability guard must be proven |
| HITL review | AEP | Reviewer/operator | Pending queue, approve/reject/escalate | Queue/service returns truthful configured state | Mostly complete |
| Learning loop | AEP | Operator/architect | Review episodes, propose policies | Real learning pipeline and review queue | Mostly complete |
| Runtime governance | AEP | Admin/SRE | Kill switch, degradation mode, policy eval | Strong auth, MFA, audit chain | Conditional until production guard tests prove |
| Operator observability | AEP + Data Cloud | SRE/operator | Deep health, metrics, SLOs, logs | Correlation IDs, metrics, traces, audit events | Good, with gateway propagation gaps |

---

## 6. Requirement-to-Implementation Traceability Matrix

| Requirement / Capability | UI | API/Backend | Data/Persistence | Tests | Status |
|---|---|---|---|---|---|
| Data Cloud entity CRUD | Present | Entity APIs | Entity storage provider | Not fully inspected | Likely complete |
| Data Cloud event append/query | Present | Event APIs/SSE | EventLogStore | Not fully inspected | Likely complete |
| Data Cloud pipeline metadata | Present | Pipeline APIs | Pipeline/checkpoint store | Not fully inspected | Likely complete |
| Data Cloud workflow execution | Present | HTTP routes + `WorkflowExecutionCapability` | Depends on provider | HTTP tests use mocked capability/client | Partially proven; P1 |
| Data Cloud Data Fabric | Present | Real fabric metrics in development | Demo metrics documented | Unknown | Preview only; P1/P2 |
| Data Cloud Trust Center policy lifecycle | Present | Some actions backed | Partial policy CRUD | Unknown | Partial; P1 |
| AEP pipeline CRUD/versioning | Present | Pipeline controller | Repo/DataCloud or in-memory | Existing tests referenced | Mostly complete |
| AEP run history durability | Present | AEP runtime + Data Cloud/EventLogStore | Requires Data Cloud in production | Guard not fully inspected | Conditional; P1 |
| AEP forecasting | Present | LinearTrend in `Aep.create()` | N/A | Real engine tests exist | Old P0 remediated |
| AEP Data Cloud integration | Present | Real store classes tested with mocked client | Data Cloud client/SPI | Better tests now exist; no real Data Cloud E2E inspected | Partially proven; P1 |
| AEP gateway/BFF | Present | Fastify gateway | N/A | Not fully inspected | Implemented; P1 hardening |
| AEP design-system consistency | Present | N/A | N/A | Not inspected | Incomplete; P1 |

---

## 7. End-to-End Journey Audit

| Journey | Expected Outcome | Current Evidence | Correct? | Complete? | Severity | Required Fix |
|---|---|---|---:|---:|---|---|
| Data Cloud create/browse entity | Entity persists and appears in UI | README/API docs show live entity APIs | Likely | Likely | — | Add full browser-to-backend E2E proof |
| Data Cloud run workflow | Execution launches, logs/status visible, durable if provider durable | HTTP tests with mocked capability; capability interface exists | Partially | Partially | P1 | Add real capability provider/durable test and UI capability gate |
| Data Cloud absent workflow provider | UI must hide/disable execution; backend truthfully 501 | Tests show 501/empty behavior when capability absent | Yes backend | UI unknown | P1 | Add frontend disabled-state test from `/capabilities` |
| Data Cloud Fabric | User sees real topology metrics or clear preview | Docs say preview/demo only | Partial | No | P1/P2 | Production flag off or explicit preview banner |
| Data Cloud Trust policy lifecycle | User can manage policies or sees unavailable state | Docs say partial, broader CRUD incomplete | Partial | Partial | P1 | Hide/disable incomplete CRUD; add tests |
| AEP build/edit/publish pipeline | Pipeline lifecycle with version conflict handling | README says 409/428 concurrency enforcement | Likely | Likely | — | Keep contract tests |
| AEP durable run history | Production run history survives restart | README requires Data Cloud EventLogStore; factory fallback exists | Conditional | Conditional | P1 | Fail-closed production module tests |
| AEP forecasting | Non-dev engine used outside tests | `Aep.create()` uses LinearTrend; tests invoke real engines | Yes | Yes | — | Seed noisy tests to avoid flake |
| AEP Data Cloud integration | Real adapters persist/read through Data Cloud | Tests call real stores with mocked DataCloudClient | Partial | Partial | P1 | Add real Data Cloud launcher contract test |
| AEP gateway auth/proxy | JWT, CORS, tenant, correlation enforced | Gateway code implements most | Mostly | Partial | P1 | Add gateway integration tests; fix WS tenant/correlation propagation |
| AEP design-system UI | Consistent Ghatana components/tokens | Adoption doc says raw controls remain | No | No | P1 | Complete migration |

---

## 8. UI/UX Correctness and Completeness Audit

| UI Area | Finding | Severity | Required Fix |
|---|---|---|---|
| Data Cloud shell | Role-aware disclosure is well documented | — | Maintain route truth matrix |
| Data Cloud Fabric | Preview/demo metrics risk being perceived as live | P1/P2 | Production disable by default or show strong preview warning |
| Data Cloud Trust Center | Partial lifecycle may confuse operators if actions appear enabled | P1 | Gate incomplete actions by capability and role |
| Data Cloud workflow execution UI | Must not expose execution as live unless capability is registered | P1 | `/capabilities`-driven disabled states and tests |
| AEP UI controls | Design adoption doc reports many raw buttons/inputs/selects | P1 | Migrate to design system, add lint and visual tests |
| AEP operator cockpit | Broad coverage appears strong | P2 | Verify no duplicate layouts/styles across pages |
| AEP gateway/auth flows | Login/session/SSO present | P2 | Add full journey browser E2E through gateway |

---

## 9. Frontend Action, State, and Data Flow Audit

| Product | Action/Flow | Risk | Required Fix |
|---|---|---|---|
| Data Cloud | Workflow execute button/action | Must be capability-driven; avoid fake success when provider absent | Disable/hide when capability absent; show 501/503 as actionable unavailable state |
| Data Cloud | Analytics/NLQ query | Optional backing services degrade | UI must show capability/fallback/provenance, never fake rows |
| Data Cloud | Trust actions | Some write lifecycle incomplete | Capability-gated actions; tests for disabled state |
| AEP | UI API routing | README says UI must go exclusively through gateway | Add static test that no direct Java backend URL/API base is imported |
| AEP | SSE/WebSocket events | Gateway supports SSE/WS; tenant/correlation propagation incomplete for WS | Forward tenant/correlation and add tests |
| AEP | Cost/forecasting surfaces | Historical audit flagged synthetic fallback; current source not fully inspected | Verify response has provenance/estimated flag and UI disclaimer |

---

## 10. Backend/API/Domain Logic Audit

| Product | Backend Flow | Finding | Severity | Required Fix |
|---|---|---|---|---|
| Data Cloud | Workflow execution | Endpoint/capability exists; tests mock capability/client | P1 | Add real provider integration and durable restart test |
| Data Cloud | Capability registry | Intended runtime truth surface | P1 | Every optional UI/action must bind to capability state |
| Data Cloud | Governance policy CRUD | Broader lifecycle incomplete | P1 | Implement or explicitly return unsupported and hide UI |
| Data Cloud | Local/sovereign profiles | Local non-durable; sovereign durable single-node documented | P1 | Production profile must fail closed without durable provider |
| AEP | Forecasting | Current factory uses `LinearTrendForecastingEngine`; old P0 remediated | — | Keep production-module regression test |
| AEP | EventCloud discovery | `Aep.create()` can fall back to in-memory if ServiceLoader empty | P1 | Production launcher/module must prohibit fallback unless explicit env override |
| AEP | Gateway auth/proxy | Good JWT/CORS/tenant checks for HTTP/SSE | P1 | Add missing WS tenant/correlation propagation and tests |
| AEP | Data Cloud stores | Real store classes tested with mocked client | P1 | Add full AEP↔Data Cloud contract/E2E test |

---

## 11. Database and Persistence Audit

| Product | Persistence Area | Risk | Required Fix |
|---|---|---|---|
| Data Cloud | Entity/event stores | Provider-specific durability determines SLA | Production profile must assert durable provider |
| Data Cloud | Workflow execution state | Capability contract exists; durable provider not proven in inspected test | Add restart/durability integration test |
| Data Cloud | Settings | README says in-memory settings blocked in strict/production-like profiles | Add/keep production startup tests |
| AEP | Run history | README requires Data Cloud EventLogStore for production durability | Add fail-closed test and restart durability test |
| AEP | In-memory fallback | Acceptable in dev/test only | Production startup must reject accidentally in-memory runtime |
| AEP | Migrations | Existing audit flagged missing baseline migrations, but current files were not fully inspected | Re-run migration inventory and fresh-schema Flyway test |

---

## 12. Production Mock/Stub/Shortcut Audit

| Product | Evidence | Current Assessment | Severity |
|---|---|---|---|
| Data Cloud | Data Fabric documented as preview/demo hardcoded metrics | Allowed only if hidden/flagged/clearly labeled | P1/P2 |
| Data Cloud | Workflow execution tests mock capability/client | Acceptable unit/integration style, not sufficient production proof | P1 validation gap |
| Data Cloud | Existing audit doc claims missing execution endpoint | Stale; current tests/capability show remediation | P2 doc stale |
| AEP | Old audit claimed forecasting/test-theatre P0s | Current source/tests show remediation | No current P0 |
| AEP | `NaiveForecastingEngine` in `forTesting()` | Acceptable test/dev path | — |
| AEP | DataCloud integration test uses mocked DataCloudClient | Acceptable for adapter tests, not sufficient E2E | P1 validation gap |
| AEP | Gateway `console.error` at startup | Acceptable fail-fast logging, but standardize structured logs | P2 |

---

## 13. Duplicate and Source-of-Truth Audit

| Area | Risk | Required Fix |
|---|---|---|
| Data Cloud vs AEP pipeline execution semantics | Overlap may confuse product ownership | Rename/label Data Cloud execution as data-local plugin execution; AEP as orchestration/runtime |
| UI component primitives | AEP still has raw controls while Data Cloud has clearer UI component split | Create shared design migration gate across both products |
| Cross-product contracts | Product-local APIs can drift from `platform/contracts` or OpenAPI | Contract compatibility gate in CI; generated clients from canonical specs |
| Capability truth | Docs, OpenAPI, UI, backend may drift | Single capability registry consumed by UI, docs, tests |

---

## 14. Security, Privacy, Governance, and Permission Audit

| Area | Finding | Severity | Required Fix |
|---|---|---|---|
| AEP gateway JWT | Fails closed when missing token and on JWT errors | — | Keep |
| AEP gateway tenant mismatch | HTTP/SSE mismatch checks present | — | Keep |
| AEP WebSocket | Auth checked, but tenant/correlation propagation to backend WS not proven | P1 | Include tenant/correlation in backend WS headers/query and tests |
| AEP trusted proxy | README documents trusted proxy CIDR behavior and metrics | P2 | Verify tests and ops docs |
| Data Cloud tenant boundary | REST docs define `X-Tenant-ID` and strict multi-tenancy | P1 | Verify every route rejects/isolates missing/wrong tenant |
| Data Cloud optional auth | Local can run no-auth; non-local should use API key/JWT | P1 | Production startup fail-closed tests |
| Governance actions | Kill-switch, purge, redaction, compliance must be audited | P1 | Add end-to-end audit-event assertions |

---

## 15. Observability and Operability Audit

| Flow | Current State | Gap | Required Fix |
|---|---|---|---|
| Data Cloud capabilities | `/api/v1/capabilities` is intended runtime truth | Every optional UI must consume it | Add route/action tests |
| Data Cloud workflow execution | Snapshot/log endpoints documented/tested with mock capability | Need real provider telemetry | Metrics/logs/traces for real execution lifecycle |
| AEP gateway | Correlation ID for HTTP proxy/readiness | 502 and WS correlation not complete | Always return/pass correlation ID |
| AEP runtime | Deep health/SLO metrics documented | Need durable dependency checks proven in production module | Production health integration test |
| AEP learning/HITL | Operationally live per README | Need metrics completeness verification | Add episode/review counters |

---

## 16. Performance and Scalability Audit

| Product | Area | Risk | Required Fix |
|---|---|---|---|
| Data Cloud | Workflow execution | Single-process workflow plugin is not distributed scheduler | Document limits and avoid HA claims |
| Data Cloud | Analytics/query | Large result sets/backing service degrade | Enforce limits, timeouts, cancellation |
| Data Cloud | SSE/WS | Streams need backpressure and stale connection cleanup | Load test and metrics |
| AEP | EventCloud fallback | In-memory fallback unsafe in prod | Production fail-closed |
| AEP | Gateway proxy | Proxy buffers response as text | For large/streaming responses, stream bodies or cap size |
| AEP | SSE/WS | Long-lived connections require cleanup/backpressure | Gateway load test |

---

## 17. Test Correctness and Coverage Audit

### Positive current evidence

- AEP forecasting validation now invokes real `NaiveForecastingEngine`, `LinearTrendForecastingEngine`, and `StatisticalForecastingEngine`.
- AEP Data Cloud integration validation now invokes real store implementations with a mocked `DataCloudClient`, which is a legitimate adapter-test pattern.
- Data Cloud workflow execution HTTP tests now exercise real `DataCloudHttpServer` routes with mocked `WorkflowExecutionCapability`.

### Remaining test gaps

| Product | Gap | Priority |
|---|---|---|
| Data Cloud | Real `WorkflowExecutionCapability` provider + durable restart integration test | P1 |
| Data Cloud | UI disabled/hidden behavior when capability absent | P1 |
| Data Cloud | Data Fabric preview/demo production gating test | P1 |
| Data Cloud | Full Trust Center policy lifecycle tests or disabled-action tests | P1 |
| AEP | Production startup fails without durable Data Cloud/EventLogStore | P1 |
| AEP | AEP ↔ Data Cloud contract/E2E against real Data Cloud launcher | P1 |
| AEP | Gateway JWT/CORS/SSE/WS/tenant/correlation integration tests | P1 |
| AEP | Design-system migration lint/visual tests | P1 |
| AEP | Forecasting test determinism: replace `Math.random()` with seeded/noise fixture | P2 |

---

## 18. Prioritized Remediation Plan

| Priority | Product | Area | Issue | Required Fix | Acceptance Criteria | Tests Required |
|---|---|---|---|---|---|---|
| P1 | Data Cloud | Workflow execution | Production provider/durability not proven by inspected tests | Wire real durable `WorkflowExecutionCapability`; expose capability state | Execute/list/log/cancel survives restart with durable profile | Provider IT + restart IT + UI capability test |
| P1 | Data Cloud | Capability truth | Optional/degraded services may appear live | UI actions derive from `/api/v1/capabilities` | No unavailable action appears as active | Route/action disabled-state tests |
| P1 | Data Cloud | Data Fabric | Preview/demo metrics documented | Disable in production by default or show preview banner | No user can mistake demo metrics for live | Feature flag and visual tests |
| P1 | Data Cloud | Trust Center | Policy CRUD lifecycle incomplete | Implement CRUD or hide/disable actions | UI and backend agree on availability | Contract + UI tests |
| P1 | AEP | Runtime durability | Factory fallback to in-memory can be unsafe if prod path misuses it | Production module/launcher fail closed without Data Cloud/EventLogStore | Startup fails unless durable provider or explicit allow flag | Production module tests |
| P1 | AEP | Design system | Raw controls remain | Migrate to `@ghatana/design-system` | No raw controls except approved low-level primitives | ESLint + visual/a11y tests |
| P1 | AEP | Gateway | WS tenant/correlation and failure propagation gaps | Pass tenant/correlation to backend WS and all 5xx responses | Logs/responses include correlation; tenant mismatch rejected | Gateway integration tests |
| P1 | AEP | Data Cloud integration | Adapter tests use mock client only | Add real Data Cloud launcher contract/E2E suite | AEP pipeline/agent/run data persists through real Data Cloud APIs | Docker/local stack E2E |
| P2 | Both | Audit docs | Existing product audit docs contain stale findings | Regenerate audits from current source | No stale P0 remains in docs | Doc truth CI |
| P2 | AEP | Forecast tests | `Math.random()` may create flake | Seed deterministic noise data | Repeat runs stable | Flakiness test |

---

## 19. Production Readiness Gate

### Data Cloud

- **Ready for production:** No, not unrestricted.
- **Ready for internal demo:** Yes.
- **Ready behind feature flag/role/capability gates:** Yes.
- **Critical release gates:**
  1. Real durable workflow execution provider evidence.
  2. Data Fabric preview/demo gated or labeled.
  3. Trust Center incomplete lifecycle hidden or completed.
  4. Production startup tests for auth/durable providers.
  5. Full UI capability truth tests.

### AEP

- **Ready for production:** Conditional; not unrestricted until P1s are closed.
- **Ready for internal demo:** Yes.
- **Ready behind feature flag/role/capability gates:** Yes.
- **Critical release gates:**
  1. Production durability fail-closed test for Data Cloud/EventLogStore.
  2. AEP UI design-system migration or explicit accepted debt gate.
  3. Gateway JWT/CORS/SSE/WS/tenant/correlation integration tests.
  4. AEP ↔ Data Cloud real contract/E2E validation.
  5. Deterministic forecasting tests.

---

## 20. Final Checklist

| Checklist Item | Data Cloud | AEP |
|---|---|---|
| Correctness proven for critical flows | Partial | Mostly |
| Completeness of visible surfaces | Partial | Mostly |
| No production mock/stub/demo leaks | Needs Fabric/capability gating | Needs gateway/design validation |
| UI/UX simple and consistent | Mostly | Not yet fully design-system consistent |
| Backend/API contract explicit | Strong | Strong |
| DB/persistence production-safe | Conditional | Conditional |
| Security/privacy/governance | Conditional | Conditional |
| Observability/operability | Good but needs real provider metrics | Good but gateway propagation gaps |
| Performance/scalability | Needs load/backpressure tests | Needs gateway/SSE/WS tests |
| Tests prove intended behavior | Partial | Improved, still needs E2E |
| Documentation current | Some stale audit docs | Some stale audit docs |

---

## 21. Final Recommendation

Treat **Data Cloud and AEP as architecturally aligned but not yet unrestricted-production-ready**. The current head shows meaningful remediation versus older audit findings: Data Cloud now has workflow execution route/capability tests, and AEP no longer appears to use the development-only forecasting engine in normal factory paths. The next milestone should not be more feature expansion; it should be **release-truth hardening**:

1. Capability-gated UI everywhere.
2. Real provider integration tests where mocks currently stand in.
3. Durable production startup guards.
4. AEP design-system migration.
5. Gateway hardening.
6. Fresh audit docs that remove stale P0s and reflect current evidence.
