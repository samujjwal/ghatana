# DMOS Ultra-Strict End-to-End Product Correctness, Completeness, UI/UX, Backend, DB, Test, and Production-Readiness Audit

**Repository:** `samujjwal/ghatana`  
**Target root:** `products/digital-marketing`  
**Commit/ref audited:** `7432d84601747ed3e095555c11a5f9471f0f8595`  
**Prompt executed:** Ultra-strict end-to-end product correctness/completeness audit, including Kernel-platform enhancement review.  
**Execution method:** Source-evidence audit through GitHub connector at the exact ref. Runtime-only conclusions require execution proof.

---

## 1. Executive Summary

| Dimension | Rating | Verdict |
|---|---:|---|
| Correctness | C+ | Important source-level improvements landed, but release-blocking contract mismatches remain. |
| Completeness | C | Backend/API/docs surface is broad; UI exposes a subset and new UI surfaces are not fully wired. |
| Production readiness | Not ready | Critical campaign, auth, feature-flag, persistence, and test proof gaps remain. |
| Mock/stub/shortcut risk | Medium-High | README still admits deterministic stubs; local scaffolding remains; gates are flawed. |
| UI/UX readiness | Partial | Dashboard/approvals/AI log exist; campaign/strategy/budget routes were added but are not release-safe. |
| Backend/API readiness | Partial | Many servlets exist; route/header/error/contract drift remains. |
| DB/persistence readiness | Partial | PostgreSQL adapters and migrations improved, but production wiring/profile proof is missing. |
| Kernel integration | Partial | Notification plugin use improved; flags/risk/auth/platform profile validation remain incomplete. |
| Test confidence | Insufficient | Changed end-to-end flows need real API/browser/DB proof. |

### Release gate

| Gate | Status |
|---|---|
| Ready for production | **No** |
| Ready for broad external beta | **No** |
| Ready for internal demo | **Yes, with explicit caveats** |
| Ready behind feature flags | **Only after feature flags are fixed and backend gates match UI gates** |
| Safe to claim “API correctness ready” | **No** |
| Safe to claim “Persistence production-ready” | **Not without runtime profile/migration proof** |

### Top blockers

| Priority | ID | Area | Finding |
|---|---|---|---|
| P0 | DMOS-P0-001 | UI/API | `CampaignsPage` calls `GET /v1/workspaces/:workspaceId/campaigns`, but `DmosCampaignServlet` registers only `POST /campaigns`, `GET /campaigns/:id`, `POST /launch`, and `POST /pause`. Campaigns page cannot load. |
| P0 | DMOS-P0-002 | UI build/runtime | `FeatureFlaggedRoute` uses `process.env` in a Vite browser app instead of `import.meta.env` or backend runtime flags. |
| P0 | DMOS-P0-003 | Auth/session | Production login is broken/incomplete: UI exposes manual bearer/workspace/tenant/principal form, while `AuthContext.login()` throws in production and no production auth callback route is present in `App.tsx`. |
| P0 | DMOS-P0-004 | Contract drift | README/API contract/UI/backend disagree on campaign route surface and campaign types. |
| P1 | DMOS-P1-001 | Kernel flags | `DigitalMarketingKernelAdapter.isFeatureEnabled()` now fails closed by default, but production implementation still does not override/delegate to `FeatureFlagPlugin`; backend runtime feature flags are not actually dynamic. |
| P1 | DMOS-P1-002 | Context/auth | Servlets duplicate context building and accept/default missing principal/session in places, conflicting with mandatory-header claims. |
| P1 | DMOS-P1-003 | Persistence | PostgreSQL adapters exist, but production bootstrap wiring, complete repository parity, migration execution, and fail-closed rejection of in-memory adapters are not proven. |
| P1 | DMOS-P1-004 | UI coverage | Campaign/strategy/budget routes were added but are feature-gated by flawed code and absent from the README UI route inventory. |
| P1 | DMOS-P1-005 | Observability | OTel remains partial; rate limiter has metrics overload but many servlets use deprecated/noop wrapper. |
| P1 | DMOS-P1-006 | Testing | Exact changed flows require real API/browser tests: campaign list/create/launch/pause, strategy, budget, auth, feature flags, and DB persistence. |

---

## 2. Scope and Method

### Reviewed target

- `products/digital-marketing`
- Directly related platform dependencies visible from DMOS:
  - `platform-kernel`
  - platform plugins used by the DMOS bridge: approval, audit, consent, risk, notification
  - shared HTTP/security/rate-limit utilities where referenced
  - root audit document path required by the prompt

### Evidence inspected

| Area | Evidence files |
|---|---|
| Product/readiness docs | `products/digital-marketing/README.md` |
| UI root/routes | `products/digital-marketing/ui/src/App.tsx` |
| Feature gating | `ui/src/components/FeatureFlaggedRoute.tsx`, `ui/src/lib/feature-flags.ts` |
| Auth/session | `ui/src/context/AuthContext.tsx`, `ui/src/pages/LoginPage.tsx`, `ui/src/lib/http-client.ts` |
| Campaign flow | `CampaignsPage.tsx`, `useCampaigns.ts`, `api/campaigns.ts`, `DmosCampaignServlet.java`, `CampaignType.java`, `PostgresCampaignRepository.java` |
| Strategy flow | `StrategyPage.tsx`, `api/strategy.ts`, `DmosStrategyServlet.java` |
| Budget flow | `api/budget.ts`, `DmosBudgetRecommendationServlet.java` |
| Kernel bridge | `DigitalMarketingKernelAdapter.java`, `DigitalMarketingKernelAdapterImpl.java` |
| Config/feature defaults | `DmosProductConfig.java` |
| Rate limiting | `DmosApiRateLimiter.java` |
| PII migration | `V6__migrate_suppression_to_contact_point_hash.sql` |
| Audit target | `docs/audits/end-to-end-product-correctness-audit.md` |

### Exclusions / limitations

- Did not execute Gradle, pnpm, Playwright, Testcontainers, migrations, or services.
- Did not validate generated clients against actual OpenAPI output.
- Did not inspect every Java service class due connector result-size limits; findings focus on critical source-evidenced flows.
- Runtime claims such as “quality gates pass” and “61 unit tests pass” are treated as unverified unless backed by executable CI evidence.

---

## 3. Complete Product Inventory

### 3.1 Module inventory

| Module | Purpose | Claimed Status | Audit Status | Issues |
|---|---|---:|---|---|
| `dm-core-contracts` | Typed IDs, operation/security context, mapper | Stable | Source-credible | Must become single source of truth for HTTP context; servlets duplicate behavior. |
| `dm-domain-packs` | Boundary policy/compliance/rule packs | Stable | Source-credible | Need runtime startup proof that default-deny pack is loaded in production. |
| `dm-kernel-bridge` | Product adapter to Kernel/plugins | Stable | Partial | Notification improved; feature flags still not delegated. |
| `dm-domain` | Aggregates, invariants, enums | Stable scaffold | Partial | Enum/contract drift remains. |
| `dm-application` | Application services/orchestrations | MVP scaffold | Partial | README admits deterministic stubs remain. |
| `dm-infra` | In-memory repositories | Dev/test only | Risk | Must be impossible in production wiring. |
| `dm-persistence` | Flyway/PostgreSQL adapters | Production-ready claimed | Partial | Some adapters exist; full coverage/wiring/migration proof missing. |
| `dm-connector-google-ads` | OAuth/campaign/performance adapter | Partial | Partial | Adapter complete, workflow wiring pending. |
| `dm-api` | ActiveJ servlets | MVP complete | Partial | Missing campaign list endpoint, context/header inconsistency, route docs drift. |
| `dm-integration-tests` | Integration suites | Partial | Partial | Needs exact changed-flow E2E tests. |
| `ui` | React 19 + Vite UI | MVP complete; E2E pending | Partial | New routes added but feature gate/build/auth/list-route problems. |

### 3.2 UI inventory

| UI Item | File(s) | Purpose | User Actions | Data/API Dependencies | Completeness | Issues |
|---|---|---|---|---|---|---|
| Root app/router | `ui/src/App.tsx` | Providers and routes | Navigate | AuthProvider, QueryClient | Partial | README route list stale; route gates flawed. |
| `/login` | `LoginPage.tsx` | Manual sign-in | submit token/workspace/tenant/principal | `AuthContext.login` | Dev-only | Production login broken/no auth provider route. |
| `/workspaces/:workspaceId/dashboard` | `DashboardPage.tsx` | Workspace overview | view widgets/navigate | approvals, AI action log | Partial | Does not surface complete product surface. |
| `/workspaces/:workspaceId/approvals` | `ApprovalQueuePage.tsx` | Pending approvals | filter/navigate | `listPendingApprovals` | Partial | Queue semantics appear subject-scoped, not reviewer/workspace queue. |
| `/workspaces/:workspaceId/approvals/:requestId` | `ApprovalDetailPage.tsx` | Approval decision | approve/reject | approval APIs | Partial | Role/auth enforcement must be proven server-side. |
| `/workspaces/:workspaceId/ai-actions` | `AiActionLogPage.tsx` | AI transparency log | list/filter/detail | AI action APIs | Partial | Sensitive redaction proof needed. |
| `/workspaces/:workspaceId/campaigns` | `CampaignsPage.tsx` | Campaign list/create/launch/pause | create/launch/pause | campaign API client | **Broken** | Calls missing `GET /campaigns`; feature-gated by flawed env code. |
| `/workspaces/:workspaceId/strategy` | `StrategyPage.tsx` | Generate/review/approve strategy | generate/submit/approve | strategy API | Partial | Direct approve may bypass approval queue intent. |
| `/workspaces/:workspaceId/budget` | `BudgetPage.tsx` | Budget recommendations | generate/submit/approve | budget API | Partial | Same governance concerns. |
| `FeatureFlaggedRoute` | `FeatureFlaggedRoute.tsx` | Route gating | render/redirect | env var | **Broken** | Uses `process.env`, redirects disabled route to `/` → `/login`. |
| HTTP client | `http-client.ts` | API request wrapper | all API calls | fetch/runtime context | Partial | Idempotency generated per request; no typed error envelope. |
| Auth context | `AuthContext.tsx` | session state | login/logout/refresh | http-client context | Partial | Production refresh logs warning and extends local expiry; no real refresh. |

### 3.3 User action inventory

| Action | UI Source | Expected Result | Actual Handler | Backend/API Called | DB Impact | Complete? | Issues |
|---|---|---|---|---|---|---|---|
| Manual login | `LoginPage` | Authenticated session | `AuthContext.login` | none | none | Dev only | Throws in production; no alternative route. |
| Open dashboard | route | dashboard loads | React route | approvals + AI actions | read | Partial | Dashboard does not cover all critical capabilities. |
| Filter approvals | `ApprovalQueuePage` | filtered visible list | local state | pending approvals endpoint | none | Partial | Backend endpoint is subject-scoped. |
| Decide approval | `ApprovalDetailPage` | approve/reject persisted | mutation | approval decide API | approval + audit | Partial | Must prove server role enforcement and cache invalidation. |
| List campaigns | `CampaignsPage` | table of campaigns | `useCampaigns` | `GET /campaigns` | read | **No** | Backend route missing. |
| Create campaign | `CampaignsPage` | new DRAFT campaign | `useCreateCampaign` | `POST /campaigns` | insert | Partial | UI cannot refresh list; duplicate retry key risk. |
| Launch campaign | `CampaignsPage` | DRAFT → LAUNCHED | `useLaunchCampaign` | `POST /campaigns/:id/launch` | update/audit | Partial | Preflight/risk/notification proof needed. |
| Pause campaign | `CampaignsPage` | LAUNCHED → PAUSED | `usePauseCampaign` | `POST /campaigns/:id/pause` | update/audit | Partial | Mutation errors not surfaced. |
| Generate strategy | `StrategyPage` | draft strategy | `useGenerateStrategy` | `POST /strategy` | strategy write | Partial | Route gated incorrectly; AI/stub status unclear. |
| Submit strategy | `StrategyPage` | pending approval | mutation | `POST /strategy/:id/submit` | strategy/approval | Partial | Approval queue integration not proven. |
| Approve strategy | `StrategyPage` | approved strategy | mutation | `POST /strategy/:id/approve` | strategy/audit | Partial | Direct approve could confuse governance. |
| Generate budget | `BudgetPage` | recommendation created | mutation | `POST /budget-recommendation` | budget write | Partial | Needs test/provenance. |
| Submit/approve budget | `BudgetPage` | approved recommendation | mutation | budget submit/approve routes | budget/audit | Partial | Same approval issue. |

### 3.4 Backend/API inventory

| Backend Item | File(s) | Expected Behavior | Actual | Auth/AuthZ | Validation | DB Access | Complete? | Issues |
|---|---|---|---|---|---|---|---|---|
| Campaign create | `DmosCampaignServlet` | create DRAFT | registered | tenant required; actor from header | weak null checks | repository | Partial | missing strict principal/session; no list. |
| Campaign get by id | `DmosCampaignServlet` | fetch one | registered | same | path id | repository | Partial | no list. |
| Campaign list | UI/API expected | list workspace campaigns | **not registered** | n/a | n/a | n/a | Missing | P0. |
| Campaign launch | `DmosCampaignServlet`, service | preflight/risk/launch/audit/notify | registered | kernel check | path id | repository | Partial | connector/workflow proof needed. |
| Campaign pause | same | pause/audit | registered | kernel check | path id | repository | Partial | no UI mutation error display. |
| Strategy generate | `DmosStrategyServlet` | generate strategy | registered | tenant required; principal default | parse body | service | Partial | AI/stub/feature proof needed. |
| Strategy submit/approve/get | `DmosStrategyServlet` | lifecycle | registered | same | path/body | service | Partial | role/approval semantics. |
| Budget recommend/submit/approve/get | `DmosBudgetRecommendationServlet` | lifecycle | registered | same | body/path | service | Partial | role/approval semantics. |
| Approvals | `DmosApprovalServlet` | submit/decide/status/snapshot/pending | registered | tenant required | body/path | plugin/service | Partial | queue semantics and role proof. |
| AI actions | `DmosAiActionLogServlet` | transparency log | registered | permission redaction claimed | query/body | repo | Partial | redaction proof. |
| Content/research/audit/lead/public intake | servlets listed | supporting DMOS capabilities | present per README/search | unknown | unknown | service/repo | Unknown/partial | UI not exposed for many. |
| Rate limiter | `DmosApiRateLimiter` | tenant/IP limit and 429 envelope | implemented | n/a | env parse | none | Mostly | deprecated/noop metrics overload still used. |

### 3.5 Database / persistence inventory

| DB Item | File(s) | Purpose | Actual | Constraints/Indexes | Complete? | Issues |
|---|---|---|---|---|---|---|
| `dmos_campaigns` | `V1__create_dmos_campaigns.sql`, `PostgresCampaignRepository.java` | campaign persistence | table + save/find repo | `(id, workspace_id)` PK, workspace index | Partial | no list method; no enum CHECK constraints; full wiring proof missing. |
| `dmos_ai_action_log` | `V4__create_dmos_ai_action_log.sql` | AI action transparency | table exists | workspace/correlation/entity/occurred indexes | Partial | tenant not explicit; workspace must guarantee tenant isolation. |
| `dmos_suppression` migration | `V6__migrate_suppression_to_contact_point_hash.sql` | PII-safe suppression | improved HMAC and email drop | hash index | Partial | depends on DB setting/key wiring; migration run not proven. |
| Approval snapshots | repository/test names observed in prior tree | approval evidence | likely exists | unknown | Partial | exact adapter/migration proof needed. |
| In-memory repos | `dm-infra` | local/test storage | production source module | ConcurrentHashMap | Allowed only local/test | must be impossible in production profile. |

### 3.6 Test inventory summary

| Test Area | Evidence | Type | What it proves | Valid? | Gaps |
|---|---|---|---|---|---|
| UI unit tests | README claims 61 passing | unit | not runtime verified | Unknown | changed routes need tests. |
| UI E2E | README says Playwright exists but CI pending | browser | not CI-proven | Partial | campaign/strategy/budget/auth/feature flags. |
| API servlet tests | many servlet test files observed in prior tree | unit/API | per-servlet behavior | Partial | campaign list mismatch likely not covered. |
| Persistence IT | repository ITs exist for some repos | DB integration | adapter behavior | Partial | full Flyway/profile proof. |
| Integration tests | `dm-integration-tests` partial | integration | some lifecycles | Partial | new UI/API E2E absent. |
| Kernel bridge tests | not inspected | unit/integration | unknown | Unknown | notification/risk/flag plugin integration. |

---

## 4. Product Behavior Map

| Capability | Persona | Problem Solved | Expected UX | Expected Backend Behavior | Expected Data Behavior | Status |
|---|---|---|---|---|---|---|
| Production auth | marketer/admin | secure workspace access | SSO/auth provider, not manual IDs | validate token and tenant membership | session/audit | Missing |
| Dashboard | marketer/operator | command center | approvals, campaigns, budgets, risk, AI actions | aggregate real data | read-only views | Partial |
| Campaign management | marketer | create/launch/pause campaigns | list + create + actions | create/list/get/launch/pause with preflight/risk | persisted campaigns | Broken list path |
| Strategy generation | marketer/AI | AI-assisted 30-day strategy | form → draft → approval | generate with evidence/model/provenance | persisted strategy | Partial |
| Budget recommendation | marketer/approver | channel budget plan | generate/submit/approve | calculate and govern | persisted recommendation | Partial |
| Approval workflow | approver | human governance | queue/detail/approve/reject | role enforcement, immutable snapshot | approval/audit | Partial |
| Content generation | marketer | ad copy/landing/email drafts | not clearly routed | servlets/services exist | content versions | Backend-only/partial |
| Research/audit/lead scoring | marketer | diagnostics and lead insights | not routed | servlets/services exist | reports/leads | Backend-only/partial |
| AI action transparency | approver/admin | see AI/system actions | timeline/list/detail | record/list/redact | AI action table | Partial |
| Google Ads connector | operator | execute approved campaign writes | hidden/automatic | adapter + workflow command | connector state/audit | Partial |
| Kernel governance | platform/admin | shared auth/audit/risk/notification/flags | invisible guardrails | delegate to plugins | audit/risk/notification | Partial |

---

## 5. Requirement-to-Implementation Traceability Matrix

| Requirement | UI | Actions | API/Backend | Service/Domain | DB | Tests | Observability | Status |
|---|---|---|---|---|---|---|---|---|
| Secure production auth | `/login` manual only | manual login | backend trusts headers | AuthContext throws in prod | none | unknown | none | Missing |
| Campaign list | `CampaignsPage` | list | UI calls `GET /campaigns`; servlet lacks route | repo lacks list method | table exists | missing | none | Missing / broken |
| Campaign create | `CampaignsPage` | create | `POST /campaigns` | `CampaignService.createCampaign` | `dmos_campaigns` | partial | audit + metrics | Partial |
| Campaign launch/pause | `CampaignsPage` | launch/pause | registered | preflight/risk/audit/notify | campaign update | partial | audit/metrics | Partial |
| Strategy generate | `StrategyPage` | generate | registered | strategy service | unknown | partial | unknown | Partial |
| Strategy approval | strategy + approvals | submit/approve | strategy + approval servlets | approval workflow | approval snapshot | partial | audit | Partial |
| Budget lifecycle | `BudgetPage` | generate/submit/approve | registered | budget service | unknown | partial | unknown | Partial |
| Approval queue | approvals page | filter/decide | subject-scoped pending endpoint | approval service/plugin | snapshot | partial | audit | Partial/semantics risk |
| AI action log | AI actions page | list/detail | registered | log service | `dmos_ai_action_log` | partial | log table | Partial |
| Feature-gated routes | `FeatureFlaggedRoute` | render/redirect | frontend env only | no backend gate | none | missing | none | Broken |
| Runtime feature flags | backend services | service checks | adapter default false | impl does not override | none | missing | none | Incomplete |
| Notifications | campaign launch | notify actor | bridge calls `NotificationPlugin.dispatch` | plugin dependent | plugin store | missing | logs | Improved/unproven |
| PII suppression | suppression service | check/suppress | service/migration | HMAC hashing | migration | unknown | unknown | Improved/unproven |
| OTel tracing | all flows | n/a | README partial | not wired proof | n/a | missing | partial | Incomplete |

---

## 6. End-to-End Journey Correctness Audit

| Journey | Entry Point | Expected Outcome | Actual Behavior | Correct? | Complete? | Mock/Stub Risk | Severity | Required Fix | Required Tests |
|---|---|---|---|---|---|---|---|---|---|
| Production login | `/login` | real auth session | manual form + prod throw | No | No | High | P0 | Add auth provider/callback/session bootstrap; hide manual login in prod. | Browser + auth API |
| Campaign list | `/campaigns` | campaigns load | UI calls missing backend route | No | No | Low | P0 | Add `GET /campaigns` + repository list, or remove list UI. | API + UI E2E |
| Campaign create | form | campaign appears in list | create route exists; list broken | Partial | No | Low | P0/P1 | Fix list and mutation error state. | API/browser |
| Campaign launch | button | preflight/risk/launch/audit/notify | route/service exists | Unknown | Partial | Medium | P1 | prove compliance/risk/notification/plugin behavior. | integration |
| Strategy generate | `/strategy` | generated draft | route/service exists | Unknown | Partial | Medium | P1 | verify model/stub status and persistence. | API/browser |
| Strategy submit/approve | `/strategy` | governed approval | direct submit/approve actions | Risky | Partial | Medium | P1 | enforce roles and align with approval queue. | permission/API/UI |
| Budget generate/approve | `/budget` | governed recommendation | routes exist | Unknown | Partial | Medium | P1 | same as strategy. | API/browser |
| Approval review | `/approvals/:id` | reviewer decides with snapshot | routes exist | Unknown | Partial | Low | P1 | prove role denial and immutable snapshot. | API/UI |
| AI action log | `/ai-actions` | entries list with redaction | route/table exist | Unknown | Partial | Low | P1/P2 | prove permission-based redaction. | API/UI/security |
| Google Ads execution | approved campaign | connector write after approval | adapter partial/wiring pending | No | No | Medium | P1 | command/workflow outbox and connector lifecycle. | E2E fake external |
| Feature flag off | route | feature hidden/unavailable | broken `process.env` gate | No | No | High | P0 | Vite-safe flag client + backend gate. | build + browser |
| Production DB startup | deployment | fails closed without Postgres | not proven | Unknown | Partial | High | P1 | production profile validator. | startup tests |

---

## 7. UI/UX Correctness and Completeness Audit

| UI Area | Finding | Correctness Impact | Completeness Impact | Severity | Required Fix | Tests |
|---|---|---|---|---|---|---|
| Login | Manual token/workspace/tenant/principal form remains production route. | Production route unusable because `login()` throws. | Missing real auth journey. | P0 | Add real auth provider/callback or dev-only gate. | Production build E2E |
| Feature route gates | `FeatureFlaggedRoute` uses `process.env`, not Vite `import.meta.env`. | Build/runtime risk. | New pages not safely gated. | P0 | Use `import.meta.env.VITE_*` or backend `/features`. | build + route tests |
| Feature route redirect | Disabled route redirects to `/`, then `/login`. | Authenticated user loses context. | Bad UX. | P1 | Route to dashboard or render “feature unavailable.” | browser |
| Campaigns page | Calls list API that does not exist. | Page load fails. | Core campaign management incomplete. | P0 | Add list endpoint or change UI. | browser/API |
| Campaign mutation errors | Mutation errors not surfaced near action buttons. | User sees no actionable failure. | Incomplete recoverability. | P1 | Add error banners/toasts and retry. | UI tests |
| Strategy approve | Direct approve button in strategy page may bypass queue mental model. | Governance confusion. | Partial workflow clarity. | P1 | Send to approval detail/queue or enforce role-specific visibility. | role UI tests |
| Budget approve | Same direct approval issue. | Governance confusion. | Partial. | P1 | Same. | role UI tests |
| Navigation/docs | New routes omitted from README route inventory. | Discovery/documentation drift. | Moderate. | P2 | Update docs/navigation. | doc route test |
| Dashboard | Does not expose full product surface. | Incomplete command center. | Major product gap. | P1 | Dashboard cards based on backend capabilities/persona. | UI/API |
| Accessibility | Core forms have labels; unavailable states weak. | Baseline positive but unproven. | Needs a11y proof. | P2 | Run axe/keyboard E2E. | Playwright a11y |

---

## 8. Frontend Action, State, and Data Flow Audit

| Flow | Expected | Actual | Correct? | Complete? | Production Mock/Stub? | Required Fix | Tests |
|---|---|---|---|---|---|---|---|
| HTTP headers | mandatory headers attached | client throws if context missing, generates correlation/idempotency | Partial | Partial | No | real auth context and retry-stable idempotency. | API client tests |
| Idempotency | same user action retry uses same key | key generated per request | Partial | No | No | mutation-scoped idempotency key persisted across retry. | duplicate submit tests |
| Campaign list | list campaign data | calls missing endpoint | No | No | No | backend list route/repo method. | browser/API |
| Campaign create | create and refresh list | invalidates list query, but list broken | Partial | No | No | fix list. | E2E |
| Launch/pause | mutate and refresh | invalidates list, but no error surfaced | Partial | Partial | No | show mutation errors; disable per row only. | UI |
| Feature gates | use runtime/product flag truth | checks browser `process.env` | No | No | No | `import.meta.env` + backend flags. | build/route |
| Production auth | use auth provider session | dev login throws in production | No | No | No | real provider. | prod-mode E2E |
| Strategy/budget approvals | clear review path | direct submit/approve | Partial | Partial | No | align with approval queue and role visibility. | workflow UI |
| Cache invalidation | affected queries invalidated | local key invalidation only | Partial | Partial | No | invalidate approval/action log where needed. | integration |

---

## 9. Backend/API/Domain Logic Audit

| Backend Flow | Expected | Actual | Correct? | Complete? | Mock/Stub? | Security/Data Risk | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|
| Campaign list | `GET /campaigns` lists workspace campaigns | no servlet route | No | Missing | No | broken UI | Add endpoint + repo list with pagination. | API/UI |
| Campaign create | validate input, auth, idempotency | route exists; weak servlet validation | Partial | Partial | No | invalid/default actor risk | central validation/context. | API |
| Campaign launch | auth/compliance/risk/audit/notify | service orchestrates | Unknown | Partial | No | plugin wiring not proven | integration with real Kernel plugins. | IT |
| HTTP context | mandatory tenant/principal/session/correlation | per-servlet duplicates; defaults allowed | No | Partial | No | anonymous actor risk | shared fail-closed middleware. | security |
| Error envelope | `{error,message}` | several servlets use `{status,message}` | No | Partial | No | client inconsistency | unified error model. | contract |
| Rate limiting | prod enforce + metrics | implemented, but deprecated/noop wrapper used | Partial | Partial | No | weak metrics | migrate to metrics overload. | metrics tests |
| Feature flags | dynamic runtime flags | env defaults + adapter default false; impl no override | Partial | No | No | stale flags/no backend gate | plugin delegation. | integration |
| Notification | durable dispatch | impl calls `NotificationPlugin.dispatch` | Improved | Unknown | No | retry/DLQ not proven | plugin integration tests. | IT |
| PII migration | HMAC hash/drop raw email | migration does this via DB setting | Improved | Unknown | No | key wiring risk | migration config/runbook/test. | migration IT |
| Google Ads | approved connector write only | README says wiring pending | No | Partial | No | incomplete connector | workflow command/outbox. | E2E |
| API docs | actual route surface | README/API/UI drift | No | No | No | wrong client generation | generate OpenAPI from routes. | contract |

---

## 10. Database and Data Integrity Audit

| DB Operation/Model | Expected Data Rule | Actual Behavior | Correct? | Complete? | Integrity Risk | Performance Risk | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|
| Campaign save | upsert by workspace/id | `PostgresCampaignRepository.save` upserts | Partial | Partial | created_by can be overwritten | low | preserve immutable created fields; add updated_by. | DB IT |
| Campaign find | find by workspace/id | implemented | Yes | Partial | relies on workspace tenant mapping | low | tenant/workspace FK if workspace table exists. | DB IT |
| Campaign list | list workspace campaigns | missing | No | Missing | broken UI | unknown | add paginated deterministic list. | DB + API |
| Campaign schema | status/type constrained | text fields no enum CHECK | Partial | Partial | invalid DB values possible | low | add CHECK constraints. | migration test |
| AI action log | tenant/workspace-scoped log | workspace indexed; no tenant column | Partial | Partial | tenant leakage if workspace ownership weak | medium | add tenant_id or workspace FK. | DB security |
| Suppression migration | HMAC; no raw email | improved HMAC and drop email | Likely | Needs proof | high if key setting absent | low | test migration with/without key. | Flyway/Testcontainers |
| In-memory repos | local/test only | module in main source | Risk | Partial | data loss if wired prod | low | production profile validator. | startup tests |
| Full persistence parity | every repo has Postgres adapter | not fully proven | Unknown | Unknown | high for missing adapters | unknown | adapter inventory by port. | ArchUnit/IT |
| Transactions | multi-step writes atomic | per-repo JDBC; orchestration not proven transactional | Unknown | Partial | partial writes | medium | unit-of-work/outbox pattern. | failure IT |

---

## 11. Production Mock/Stub/Shortcut Zero-Tolerance Audit

| File | Evidence | Production Reachable? | Critical Flow? | Feature Flagged? | Allowed? | Severity | Required Action |
|---|---|---:|---:|---:|---:|---|---|
| `README.md` | `dm-application` “some services are deterministic stubs” | likely | yes for AI/content | partial | No for critical | P1 | inventory each stub and gate/remove. |
| `LoginPage.tsx` | manual token/login form | yes | yes auth | no | No | P0 | dev-only gate + real auth. |
| `AuthContext.tsx` | production refresh logs warning and extends expiry | yes | yes auth | no | No | P0/P1 | real refresh/session validation. |
| `FeatureFlaggedRoute.tsx` | `process.env` browser flag check | yes | yes route safety | yes but broken | No | P0 | replace with Vite/backend flag system. |
| `campaigns.ts` + `DmosCampaignServlet` | UI calls route not registered | yes | yes campaigns | route-gated but gate broken | No | P0 | add list endpoint or remove UI. |
| `DigitalMarketingKernelAdapter.java` | default risk 0.0 | yes if impl missing | yes launch risk | no | No in prod | P1 | fail closed / abstract method / no default. |
| `DigitalMarketingKernelAdapter.java` | default notify no-op | yes if impl missing | important | no | No in prod | P1 | fail closed / abstract method. |
| `DmosProductConfig.java` | AI/Google Ads dev defaults true | dev/local | non-prod | env | Allowed dev only | P2 | strict profile checks. |
| `dm-infra` | in-memory repos in main source | potentially | yes persistence | profile | local/test only | P1 | production wiring guard. |

---

## 12. Duplicate and Source-of-Truth Audit

| Duplicate Area | Files | Why Duplicate | Risk | Canonical Owner | Delete/Merge Plan | Tests |
|---|---|---|---|---|---|---|
| HTTP context building | campaign/strategy/budget/approval servlets | parse headers differently | auth drift | shared `DmosHttpContextFactory` middleware | replace per-servlet code | servlet + security tests |
| Error envelope | servlets vs API contract | `{status,message}` vs `{error,message}` | client/contract drift | shared error DTO/helper | single error model | contract tests |
| Feature flags | UI route flags, UI lib flags, product config, Kernel adapter | multiple systems | inconsistent exposure | Kernel `FeatureFlagPlugin` + generated UI client | remove env-only route flags | feature tests |
| Campaign type enum | API contract vs domain/UI | contract drift | invalid requests/docs | domain enum generated OpenAPI | generate types from backend enum | contract tests |
| Approval lifecycle | strategy/budget direct approve vs approval queue | multiple approval paths | governance bypass/confusion | approval workflow service | route through queue or enforce role UI | E2E |
| Persistence adapters | in-memory + Postgres | valid pattern but profile risk | accidental data loss | production profile validator | enforce via DI/profile | startup tests |
| Audit docs | root audit doc describes Data Cloud + AEP, not DMOS | wrong output target | stale evidence | product-specific audit path | replace/rename | docs CI |

---

## 13. Security, Privacy, Governance, and Permission Audit

| Area | Risk | Correct Behavior | Actual | Severity | Required Fix | Tests |
|---|---|---|---|---|---|---|
| Production login | no production auth path | provider/callback/session bootstrap | manual form + throw in production | P0 | implement auth provider and prod-mode route. | browser/gateway |
| Header trust | spoofed principal/roles | backend derives principal/roles from token | UI sends headers; servlets default in places | P1 | derive actor from token/security context. | security |
| Missing session | anonymous/null session accepted | reject mandatory session/principal | servlets default/allow missing | P1 | shared fail-closed context. | API |
| Approval roles | non-approver decisions | server enforces required role | claimed but not proven | P1 | test denial and role escalation. | API/security |
| PII suppression | raw PII | HMAC + no raw suppression email | migration improved; runtime key setup unproven | P1 | key management/runbook/tests. | migration/security |
| AI action details | sensitive exposure | redact unless permission | claimed | P1/P2 | permission tests and UI state. | API/UI |
| Google Ads connector | unauthorized external spend | approval + kill switch + rollback | wiring pending | P1 | outbox/approval/kill-switch. | E2E |
| Secrets | log/client leaks | env/secret manager | not fully inspected | P2 | secret scan CI. | CI |
| CSRF/XSS | token-bearing browser app | hardened auth/session | manual bearer form | P2 | cookie/session or token hardening strategy. | security |

---

## 14. Observability and Operability Audit

| Flow | Logs | Metrics | Traces | Audit Events | Gaps | Required Fix |
|---|---|---|---|---|---|---|
| Campaign create | service logs + audit | metrics increment | no OTel proof | `recordAudit` | no end-to-end trace | wire OTel spans/labels. |
| Campaign launch | logs + audit + notify | metrics increment | no OTel proof | `recordAudit` | notification trace missing | trace external connector/notification. |
| API rate limit | 429 envelope | only if metrics overload used | no proof | n/a | servlet label/noop issue | migrate all servlets to metrics overload. |
| Strategy/budget | error logs | unknown | no proof | likely | AI/model provenance metrics absent | standard AI action/audit for generate. |
| Approval decision | logs warnings | unknown | no proof | likely | no trace proof | span + audit id. |
| PII migration | DB notices | none | n/a | migration event absent | ops visibility missing | migration audit/deployment metric. |
| Google Ads | unknown | unknown | no proof | must audit | connector pending | outbox/DLQ/retry dashboards. |
| UI errors | local render only | none | no frontend traces | n/a | correlation not surfaced to user | include correlation ID in error UI. |

---

## 15. Performance and Scalability Audit

| Area | Risk | Evidence | Impact | Required Fix | Tests/Benchmarks |
|---|---|---|---|---|---|
| Campaign list | no backend list/pagination | route missing | page broken; future unbounded risk | add paginated endpoint | API perf |
| React routes | eager imports for new pages | App imports pages directly | bundle growth | lazy-load pages | bundle analysis |
| Query retries | retry=1 globally | QueryClient config | okay but non-specific | per-flow retry/backoff | UI tests |
| Idempotency | new key every request | http-client | retry duplicates possible | mutation-scoped key | duplicate-submit tests |
| JDBC blocking | `Promise.ofBlocking` | Postgres repo | acceptable if executor sized | bounded executor/backpressure | load tests |
| Rate limiter | in-memory filter | `DmosApiRateLimiter` | single-node only | distributed limiter if multi-replica | load/HA |
| Strategy/budget generation | AI/deterministic compute unknown | service not fully inspected | latency unpredictable | async jobs for long tasks | load/timeouts |
| Connector writes | Google Ads pending | README | external latency/failure | outbox + retries + DLQ | chaos tests |

---

## 16. Test Correctness and Coverage Audit

| Capability/Flow | Existing Tests | Missing Tests | Invalid/Stale Tests | Required Tests | Priority |
|---|---|---|---|---|---|
| Campaign list | unknown | API should fail today because route missing | likely not covered | `GET /campaigns` API + UI load | P0 |
| Campaign create/launch/pause | partial likely | browser workflow with real backend | unknown | create → list → launch → pause → DB/audit | P0/P1 |
| FeatureFlaggedRoute | unknown | Vite production build + disabled/enabled route | n/a | build proof; route visibility | P0 |
| Production auth | unknown | prod-mode login/auth callback | n/a | manual login hidden; real auth works | P0 |
| Context/header fail-closed | partial | missing principal/session/correlation rejection | n/a | strict header matrix across all servlets | P1 |
| Error envelope | unknown | contract tests per route | n/a | JSON schema/error code tests | P1 |
| Strategy lifecycle | partial | generate/submit/approve with permissions | n/a | UI/API/DB/audit | P1 |
| Budget lifecycle | partial | same | n/a | UI/API/DB/audit | P1 |
| Approval roles | partial | non-approver denied; approver allowed | n/a | API/security/UI | P1 |
| PII migration | unknown | migration with/without HMAC key | n/a | Testcontainers/Flyway | P1 |
| Kernel notification | unknown | dispatch failure/retry/DLQ | n/a | plugin integration | P1 |
| Feature flag plugin | unknown | backend flag true/false dynamic | n/a | Kernel plugin integration | P1 |
| Google Ads outbox | pending | approved launch executes/retries/rollbacks | n/a | connector E2E with fake external server | P1 |
| OTel | missing | trace propagation UI→API→DB | n/a | telemetry integration | P1/P2 |

---

## 17. Kernel Platform Enhancement Gaps

| Kernel Capability | Current DMOS Evidence | Gap | Required Kernel Enhancement | Acceptance Criteria |
|---|---|---|---|---|
| Auth/session | manual login, per-servlet header parsing | product owns too much auth context | shared Kernel auth/session middleware and UI bootstrap | no servlet accepts spoofed principal/roles; UI has real auth callback. |
| Feature flags | backend default false, no impl override; UI env gate broken | no runtime truth source | `FeatureFlagPlugin` + generated UI/backend flag client | flags change without restart; UI/backend agree. |
| Notifications | `NotificationPlugin.dispatch` now used | delivery semantics not proven | durable notification plugin with retry/DLQ/metrics | failed delivery retried and visible. |
| Risk | risk plugin injected, default adapter still low score | fail-open if impl absent | make risk abstract/fail-closed | no default low risk in prod. |
| Idempotency | UI generates per request; approval service optional | not universal/stable | Kernel idempotency middleware/service | duplicate retries return same response. |
| Outbox/workflow | Google Ads wiring pending | external side effects unsafe | durable workflow/outbox plugin | approved writes transactional/retryable/audited. |
| Privacy crypto | migration uses DB setting | key management not platformized | Kernel crypto/HMAC/key-rotation plugin | product migrations do not embed ad hoc crypto policy. |
| Observability | partial OTel | inconsistent logs/metrics | Kernel span/metric/audit SDK | every critical flow has correlation trace. |
| Capability registry | docs/UI/backend drift | route gates not runtime-driven | Kernel capability registry to UI/OpenAPI | UI/action generated from capability truth. |
| Production profile validator | in-memory repos in source | accidental non-durable prod wiring | Kernel/product bootstrap validator | prod fails if fake/in-memory adapter is wired. |

---

## 18. Prioritized Remediation Plan

| Priority | Area | Issue | Evidence/File(s) | Required Fix | Acceptance Criteria | Tests Required |
|---|---|---|---|---|---|---|
| P0 | Campaigns | UI list calls missing endpoint | `api/campaigns.ts`, `DmosCampaignServlet.java` | Add `GET /campaigns` and repository `listByWorkspace`, or remove list UI. | Campaigns page loads real data. | API + browser + DB |
| P0 | UI flags | Vite browser app uses `process.env` | `FeatureFlaggedRoute.tsx` | Replace with `import.meta.env` or backend flag API; render unavailable boundary. | Production build works; disabled route does not redirect to login. | Vite build + browser |
| P0 | Auth | Production login path broken | `LoginPage.tsx`, `AuthContext.tsx`, `App.tsx` | Add real auth provider/callback/session bootstrap; gate manual login to dev. | Production user authenticates; manual token form absent in prod. | prod-mode E2E |
| P0 | Contracts | API/docs/UI/backend route/type drift | README, API clients, servlets, enums | Generate OpenAPI/types from backend routes/domain; CI drift check. | Contract tests compare UI types/routes. | contract CI |
| P1 | Header/security | servlets default principal/session | servlet context builders | Shared fail-closed context builder. | all protected routes require validated actor/session. | security matrix |
| P1 | Persistence | production Postgres wiring unproven | README + adapters | Add profile bootstrap validation; wire all repos; block in-memory. | prod startup fails without durable repos. | startup + ArchUnit |
| P1 | Campaign DB | no list/pagination, weak constraints | repo + V1 schema | Add paginated list; enum CHECKs; immutable created fields. | deterministic list, invalid enum impossible. | DB IT |
| P1 | Approval workflow | direct approve actions | strategy/budget pages/servlets | Align with approval queue or enforce role-specific direct approve. | non-approver cannot see/use approve; backend denies. | UI/API role tests |
| P1 | Kernel flags | no production delegation | adapter interface/impl | Inject/delegate `FeatureFlagPlugin`. | dynamic flag impacts backend and UI. | integration |
| P1 | Kernel risk | default risk 0.0 | adapter interface | remove default or fail closed. | missing risk plugin blocks launch. | unit/integration |
| P1 | Notifications | delivery unproven | adapter impl | Add failure/retry/DLQ tests and metrics. | failed dispatch not fake-success. | integration |
| P1 | Google Ads | wiring pending | README | durable outbox/workflow after approval. | approved campaign creates external command and audit. | E2E fake server |
| P1 | Observability | OTel partial | README/rate limiter | Wire spans/metrics across critical flows. | trace ID visible in logs/metrics/UI errors. | telemetry tests |
| P1 | PII | migration key setup unproven | V6 migration | Document/set DB setting from secret; test fail/success. | migration fails without key, succeeds with key, raw email gone. | Flyway IT |
| P1 | Tests | changed flows not proven | README E2E pending | Add real browser/API/DB integration suite. | CI blocks regressions. | Playwright + Gradle |
| P2 | Bundle | eager page imports | `App.tsx` | lazy-load route pages. | smaller initial bundle. | bundle budget |
| P2 | Docs | required audit target wrong product | `docs/audits/...` | replace or move to DMOS-specific path. | audit doc matches DMOS and commit. | doc CI |

---

## 19. Production Readiness Gate

| Question | Answer |
|---|---|
| Ready for production? | **No** |
| Ready for internal demo? | **Yes, with caveats and dev-only disclosure** |
| Ready behind feature flag? | **Not until feature flag implementation is fixed** |
| Can critical campaign journey work end-to-end? | **No, campaign list route is missing** |
| Can production user authenticate through UI? | **No evidence; manual login throws in production** |
| Can product claim “Persistence Ready”? | **Not safely without production profile/migration/wiring proof** |
| Can product claim “Kernel-integrated”? | **Partially; notification improved, flags/risk/auth still incomplete** |

### Minimum fixes before release

1. Fix production authentication.
2. Fix feature flag implementation.
3. Fix campaign list route/repository/API/UI.
4. Centralize fail-closed HTTP context/auth/error handling.
5. Add contract tests proving UI API clients match servlets/OpenAPI.
6. Add production profile startup validation for durable persistence and real Kernel plugins.
7. Add E2E tests for campaigns, approvals, strategy, budget, AI action redaction, and feature flags.
8. Gate/hide incomplete Google Ads and deterministic AI/service stubs.
9. Prove PII migration with HMAC key in Testcontainers.
10. Replace stale root audit doc or move this report to `products/digital-marketing/docs/audits/end-to-end-product-correctness-audit.md`.

---

## 20. Final Checklist

| Checklist Item | Status |
|---|---|
| Correctness verified end-to-end | ❌ |
| Complete route/API/UI inventory aligned | ❌ |
| No production mocks/stubs/placeholders | ❌ / not proven |
| Production auth ready | ❌ |
| UI low cognitive load and feature complete | ⚠️ |
| Backend/API contracts consistent | ❌ |
| DB integrity constraints complete | ⚠️ |
| Production persistence wired and fail-closed | ⚠️ / not proven |
| Security/privacy/tenant boundaries proven | ⚠️ |
| Approval governance proven | ⚠️ |
| Kernel plugins production-ready | ⚠️ |
| Google Ads external write safe | ❌ |
| Observability/O11y complete | ❌ |
| Performance/load/scalability proven | ❌ |
| Tests authentic and sufficient | ❌ |
| Documentation current and self-consistent | ❌ |

---

## 21. Critical Engineering Notes

### Improvements since prior head

- UI now includes campaign, strategy, and budget route surfaces.
- `DmosProductConfig` now fails closed for AI and Google Ads in production by default.
- `DigitalMarketingKernelAdapterImpl` now requires and calls `NotificationPlugin`.
- PII suppression migration improved from SHA fallback to HMAC with fail-fast key requirement and raw email drop.
- HTTP client now generates `X-Idempotency-Key` for write requests.

### Why product is still not release-safe

The current tree shows “surface added before end-to-end closure”:

- UI pages exist before backend route parity is complete.
- Feature flags exist before runtime feature truth is properly wired.
- Auth claims exist before production login/session exists.
- Persistence adapters exist before production wiring/profile proof is available.
- Kernel bridge APIs exist before all production plugin semantics are proven.
- Tests likely cover units/servlets, but the exact user journeys are not proven against real backend and DB.

The next engineering step should be a hardening sprint that closes campaign/auth/flag/persistence/test gates before adding more marketing surfaces.
