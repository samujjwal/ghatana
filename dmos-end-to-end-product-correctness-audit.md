# End-to-End Product Correctness, Completeness, UI/UX, Backend, DB, Kernel, and Production-Readiness Audit

**Product:** Digital Marketing Operating System (DMOS)  
**Target root:** `products/digital-marketing/`  
**Repository:** `samujjwal/ghatana`  
**Commit audited:** `561f4d48329d8509fb2f4c13f769effb13a9975a`  
**Audit date:** 2026-05-03  
**Prompt executed:** Ultra-Strict End-to-End Product Correctness, Completeness, UI/UX, Backend, DB, and Production-Readiness Audit Prompt  
**Required target report path:** `docs/audits/end-to-end-product-correctness-audit.md`

> Note: This report is a static repository audit from GitHub connector evidence at the requested ref. I did not run the Gradle/PNPM/Playwright suites in a checked-out workspace, and I did not push this report into the repository. The existing repository file at `docs/audits/end-to-end-product-correctness-audit.md` currently describes `products/data-cloud`, not DMOS, so it should be replaced or product-scoped before merge.

---

## 1. Executive Summary

### Readiness Ratings

| Dimension | Rating | Reason |
|---|---:|---|
| Overall correctness | ⚠️ Medium / Not release-safe | Core domain/service patterns exist, but auth/session, feature flags, notification, persistence, API contracts, and UI/API traceability have correctness gaps. |
| Overall completeness | ❌ Low-to-medium | Backend has broad API/service scaffolding, but UI exposes only a small subset, Google Ads workflow wiring is pending, persistence is incomplete, and multiple product capabilities are backend-only or placeholder-backed. |
| Production readiness | ❌ Not ready | README itself marks persistence, Google Ads wiring, OTel, privacy/security, and UI E2E as partial/not ready. |
| Mock/stub/shortcut risk | ❌ High | Production source includes in-memory adapters, deterministic MVP behavior, feature defaults that enable incomplete features, notification log-only success, UI placeholder states, and fallback/default auth behavior. |
| Safe to release | ❌ No | DMOS should not be released to production until P0/P1 blockers below are fixed and verified end-to-end. |
| Safe for internal demo | ⚠️ Yes, limited | Acceptable only as clearly labeled local/staging demo using in-memory/local adapters and disabled external execution. |
| Safe behind feature flag | ⚠️ Partial | Approval queue, AI action log, and read-only dashboard may be demoable. Campaign launch, Google Ads execution, public intake, PII flows, production auth, and persistence should remain gated. |

### Top P0 Blockers

| ID | Area | Issue | Required Fix |
|---|---|---|---|
| P0-1 | Auth/session | UI login accepts arbitrary values; session refresh only extends local expiry; several backend servlets default missing principal/session to anonymous/session-unknown. | Replace local login with real auth/session exchange, fail closed on missing principal/session in production, and enforce contract consistently. |
| P0-2 | Kernel notifications | `DigitalMarketingKernelAdapterImpl.notifyUser` logs “Notification dispatched” and returns success without real delivery. | Add real NotificationPlugin/EventBus-backed delivery with delivery state, retry, DLQ, and failure propagation. |
| P0-3 | Feature flags / incomplete features | Backend defaults AI and Google Ads enabled; kernel `isFeatureEnabled` default returns `true`; UI/backend feature flags are separate and inconsistent. | Centralize feature flags in Kernel, fail closed when flag service is unavailable, and default incomplete production features off. |
| P0-4 | Privacy / PII | Suppression migration claims HMAC-SHA256 but uses plain SHA-256 fallback and leaves raw `email` column. | Use platform crypto/HMAC key service, drop or encrypt raw PII, migrate safely, and test irreversible PII handling. |
| P0-5 | Persistence | Product still supports and documents in-memory adapters in production source and single-instance staging; persistence/readiness is inconsistent. | Make PostgreSQL adapters the only production profile, fail fast if in-memory is selected outside dev/test, and wire full production bootstrap. |

### Top P1 Release Blockers

| ID | Area | Issue | Required Fix |
|---|---|---|---|
| P1-1 | UI/API completeness | UI routes cover only login, dashboard, approvals, and AI actions; API contract lists many product capabilities with no visible UI. | Build route coverage or hide backend-only capabilities until available. |
| P1-2 | Approval queue correctness | UI calls `pending/:subjectId` using `tenantId` as subject, which is not a real reviewer queue. | Add proper `/approvals/pending` or `/approvals/queue` endpoint scoped by workspace/reviewer role. |
| P1-3 | API contract drift | Error envelopes, mandatory headers, request field names, and idempotency semantics differ across README/API_CONTRACT/servlets/UI. | Generate canonical API spec and test all servlet responses against it. |
| P1-4 | Idempotency | UI write client does not attach `X-Idempotency-Key`; approval servlet idempotency is optional and does not require key. | Add shared client middleware and backend mandatory idempotency for all non-idempotent writes. |
| P1-5 | Dashboard trust | Growth metrics and workflow status are placeholder/misleading: growth shows “Coming soon/Metrics loading,” workflow derives from approvals. | Replace with real workflow/growth APIs or remove/gate these widgets. |
| P1-6 | DB integrity | Core tables lack tenant_id, FK relationships, check constraints/enums, lifecycle/deletion fields, and some unique constraints. | Add tenant-scoped schema, constraints, migrations, and integrity tests. |
| P1-7 | Runtime bootstrap | No strong evidence of a complete production executable/bootstrap wiring every servlet, service, Postgres adapter, Kernel plugin, and feature gate. | Add production composition root and integration tests that start the real server against Postgres/Testcontainers. |

---

## 2. Scope and Method

### Reviewed

| Area | Evidence reviewed |
|---|---|
| Product docs | `products/digital-marketing/README.md`, `docs/API_CONTRACT.md`, local/dev/deploy docs surfaced by search, architecture/implementation plan docs surfaced by search. |
| Java modules | Settings/build files, API servlets, application services, kernel bridge, persistence migrations/adapters, infra/in-memory adapters, test files surfaced by search. |
| React UI | Root router, auth context, HTTP client, feature flags, dashboard, approval queue, approval API hooks/components, E2E file inventory surfaced by search. |
| Persistence | Campaign and AI action migrations, suppression migration, Postgres campaign repository, in-memory repository module docs/code. |
| Kernel platform integration | `DigitalMarketingKernelAdapter`, `DigitalMarketingKernelAdapterImpl`, runtime feature flag defaults, notification/risk/approval/audit interactions. |
| Tests | API servlet tests, application tests, integration tests, Playwright tests surfaced by repository search. |
| Observability | Product runbooks and monitoring dashboard files surfaced by search; `DmosObservability*` files surfaced by search; README readiness table. |

### Excluded / insufficient evidence

| Area | Reason |
|---|---|
| Build/test execution | No local checkout and no command execution against repository dependencies was performed. |
| Full recursive file tree | GitHub connector search/fetch was used selectively; this is a deep static audit but not a byte-for-byte recursive clone inspection. |
| Live external service behavior | Google Ads, auth provider, notification delivery, and OTel exporter behavior were not live-tested. |
| Dependency vulnerability scan | No SBOM/OWASP dependency-check output was available. |
| Browser execution | Playwright tests were inventoried from file paths but not executed. |

---

## 3. Complete Product Inventory

### 3.1 Module Inventory

| Module | Purpose | Production Status | Issues |
|---|---|---|---|
| `dm-core-contracts` | Typed IDs, `DmOperationContext`, actor/context propagation, security context mapping. | Stable | Needs one canonical auth/header policy shared by UI/API/Kernel. |
| `dm-domain-packs` | Boundary policy, compliance rule packs, pack validation. | Stable | Ensure rule packs are executed on every critical path, not only campaign launch. |
| `dm-kernel-bridge` | Product adapter over Kernel ports/plugins: auth, consent, approval, audit, risk, notification, feature flags. | Partial | Notification is log-only; feature flag default is true; default risk is 0.0. |
| `dm-domain` | Aggregates, invariants, approval target types, AI action log model. | Stable scaffold | Needs end-to-end mapping tests from API to DB for every aggregate. |
| `dm-application` | Campaign, strategy, budget, approval, content, connector, workflow, AI action services. | MVP scaffold | Some services deterministic/stub-like; feature flags default incomplete functionality on. |
| `dm-infra` | ConcurrentHashMap-backed repositories. | Dev/test/staging only | Production source contains fake persistence; must fail closed outside dev/test. |
| `dm-persistence` | PostgreSQL adapters and Flyway migrations. | Work in progress | Not yet complete across all repositories; schema integrity gaps. |
| `dm-connector-google-ads` | OAuth, campaign creation, performance retrieval adapters. | Partial | HTTP adapter exists, but workflow/command wiring pending. |
| `dm-api` | ActiveJ servlet layer. | MVP complete | Contract drift, missing mandatory header enforcement, optional idempotency, no verified production composition root. |
| `dm-integration-tests` | Integration suites. | Partial | Needs real production bootstrap + Postgres + Kernel plugin tests. |
| `ui` | React 19 + TypeScript frontend. | MVP complete | Only covers dashboard/approvals/AI log; many backend capabilities invisible or placeholder-backed. |

### 3.2 UI Inventory

| UI Item | File(s) | Purpose | User Actions | Data Dependencies | API/State Dependencies | Completeness Status | Issues |
|---|---|---|---|---|---|---|---|
| Root router | `ui/src/App.tsx` | Defines browser routes. | Navigation/redirect. | Auth state. | `AuthProvider`, React Router, QueryClient. | Partial. | Only 5 actual routes; most product capabilities have no UI. |
| Login | `ui/src/pages/LoginPage.tsx`, `ui/src/context/AuthContext.tsx` | Local sign-in. | Enter token/workspace/tenant/principal/session/roles. | User-provided local values. | Runtime auth token + sessionStorage context. | Not production-safe. | Arbitrary credentials accepted unless gated to local only. |
| Dashboard | `ui/src/pages/DashboardPage.tsx` | Workspace overview. | Navigate approvals, view widgets. | Pending approvals, AI actions. | `useApprovalQueue`, `useAiActionLog`. | Partial. | Growth/workflow data not real enough. |
| Approval widget | `ui/src/components/dashboard/ApprovalWidget.tsx` | Approval count/status. | Link to approvals. | Approval queue. | `useApprovalQueue`. | Partial. | Queue source is suspect. |
| Workflow status widget | `ui/src/components/dashboard/WorkflowStatusWidget.tsx` | Shows active workflow status. | View status only. | Approval queue. | Approval data reused as workflow data. | Incorrect/misleading. | No workflow API. |
| Growth goal widget | `ui/src/components/dashboard/GrowthGoalWidget.tsx` | Growth metric card. | None. | None. | UI env flag. | Stub/placeholder. | “Coming soon” or “Metrics loading…” with no API. |
| Risk/compliance widget | `ui/src/components/dashboard/RiskComplianceWidget.tsx` | Risk/compliance card. | View status. | Approval queue. | Approval data. | Partial. | Does not prove real compliance/risk telemetry. |
| AI action log widget | `ui/src/components/dashboard/AiActionLogWidget.tsx` | Recent AI/system action transparency. | Link to AI log. | AI action log. | `useAiActionLog`. | Partial/flagged. | Experimental UI flag separate from backend readiness. |
| Approval queue page | `ui/src/pages/ApprovalQueuePage.tsx` | List/filter pending approvals. | Filter by target type; click detail. | Pending approvals by `subjectId`. | `listPendingApprovals`. | Incorrect queue semantics. | Uses `tenantId` as `subjectId`. |
| Approval detail page | `ui/src/pages/ApprovalDetailPage.tsx` | View snapshot and decide. | Approve/reject, navigate back. | Approval status/snapshot. | `getApprovalStatus`, `getApprovalSnapshot`, `decideApproval`. | Partial. | Idempotency, DTO contract, error envelope need verification. |
| AI action log page | `ui/src/pages/AiActionLogPage.tsx` | Show AI/system action timeline. | Filter/view action. | AI action log entries. | `useAiActionLog`. | Partial. | Feature flag and sensitive redaction need E2E tests. |
| Loading/error/empty states | Multiple pages/components | User feedback. | Retry/navigation. | Query state. | React Query. | Partial. | Some error messages leak raw API text; some placeholders visible. |
| Permission denied state | Approval queue/detail | Explain lack of approver role. | View-only. | Roles from UI login. | `role-utils`. | Partial. | UI roles are user-supplied unless real auth is added. |

### 3.3 User Action Inventory

| Action | UI Source | Expected Result | Actual Handler | Backend/API Called | DB Impact | Success State | Failure State | Complete? | Issues |
|---|---|---|---|---|---|---|---|---|---|
| Login | `LoginPage`, `AuthContext.login` | Authenticated session from trusted auth provider. | Stores runtime token and context; writes workspace/tenant/principal/session to `sessionStorage`. | None. | None. | Redirect/dashboard. | Form validation only. | ❌ | Fake/local auth can become production auth if ungated. |
| Logout | `AuthContext.logout` | Invalidate server/client session. | Clears runtime token/context/sessionStorage. | None. | None. | Login state. | None. | ⚠️ | No server invalidation. |
| Refresh session | `AuthContext` interval | Renew with auth provider. | Extends local expiry. | None. | None. | Silent refresh. | Local expiry logout. | ❌ | Stub behavior. |
| Fetch dashboard approvals | `DashboardPage` | Load workspace approval queue. | `useApprovalQueue`. | `GET /approvals/pending/:subjectId`. | Read. | Widget values. | Error text. | ⚠️ | Subject id misused. |
| Filter approvals | `ApprovalQueuePage` | Filter by target type. | Local filter. | None. | None. | Filtered table. | None. | ⚠️ | Filter checks includes on `targetType`; backend should support robust filters. |
| Approve/reject | `ApprovalDetailPage` | Persist decision with authorization and audit. | `decideApproval`. | `POST /approvals/:requestId/decide`. | Approval record update + audit. | Updated status. | Error message. | ⚠️ | No idempotency key; DTO field mismatch risk. |
| View AI actions | `AiActionLogPage` | Show redacted/non-redacted action log based on permissions. | `useAiActionLog`. | `GET /ai-actions`. | Read. | Timeline. | Error state. | ⚠️ | Sensitive redaction needs E2E permission tests. |
| Create campaign | No UI route found. | Campaign draft created. | Backend only. | `POST /campaigns`. | Campaign insert. | 201. | 400/403/500. | ❌ UI missing | Backend-only capability. |
| Launch/pause campaign | No UI route found. | Real launch/pause with compliance/risk/notification. | Backend only. | `POST /campaigns/:id/launch|pause`. | Campaign update + audit. | 200. | 403/409/422/500. | ❌ UI missing / backend partial | Notification fake; risk fail-open default possible. |
| Strategy/proposal/SOW/budget/content generation | No UI route found. | AI/native generation with approval. | Backend only. | Multiple routes. | Various. | 201/200. | Errors. | ❌ UI missing | Incomplete journey. |
| Public lead intake | No UI route found. | Capture public lead with consent/PII safety. | Backend only. | `/public/v1/.../intake/leads`. | Lead/contact tables. | 201. | Errors. | ⚠️ | Requires privacy/auth/rate-limit E2E verification. |

### 3.4 API and Backend Inventory

| Backend Item | File(s) | Caller(s) | Expected Behavior | Auth/AuthZ | Validation | DB Access | Side Effects | Complete? | Issues |
|---|---|---|---|---|---|---|---|---|---|
| Campaign API | `dm-api/.../DmosCampaignServlet.java` | Backend clients; no UI found. | Create/get/launch/pause. | Kernel auth in service; servlet requires tenant but defaults principal/session. | Basic body/enum. | Campaign repository. | Audit, metrics, notification. | Partial. | Missing strict headers, idempotency client, notification fake. |
| Approval API | `dm-api/.../DmosApprovalServlet.java` | UI approvals. | Submit/decide/status/snapshot/pending. | Service roles; servlet defaults principal/session. | Basic body enum. | Approval stores/repositories. | Audit/plugin. | Partial. | Optional idempotency, wrong queue endpoint for UI, contract drift. |
| AI action API | `DmosAiActionLogServlet.java` | UI AI action log. | Persist/list action transparency. | Permission-sensitive redaction. | Needs schema validation. | AI action repo. | Audit/observability. | Partial. | Redaction and permission E2E required. |
| Workspace API | `DmosWorkspaceServlet.java` | Not in UI except login workspace id. | Create/list/get/suspend/reactivate. | Kernel auth. | Basic. | Workspace repo. | Audit. | Partial. | No UI route; production bootstrap uncertain. |
| Strategy API | `DmosStrategyServlet.java` | No UI route found. | Generate/submit/approve/get strategy. | Kernel auth/approval. | Basic. | Strategy repo/service. | AI action log/approval. | Backend-only. | Missing UI/journey trace. |
| Proposal API | `DmosProposalServlet.java` | No UI route found. | Generate/submit/approve/get proposal. | Kernel auth/approval. | Basic. | Proposal repo/service. | AI/action approval. | Backend-only. | Missing UI/journey trace. |
| SOW API | `DmosSowServlet.java` | No UI route found. | Generate/submit/approve/export/get. | Kernel auth. | Basic. | SOW repo/service. | PDF/export. | Backend-only. | Export needs security/content tests. |
| Budget recommendation API | `DmosBudgetRecommendationServlet.java` | No UI route found. | Generate/submit/approve/get. | Kernel auth. | Basic. | Budget repo/service. | Approval. | Backend-only. | Missing UI/journey trace. |
| Content version API | `DmosContentVersionServlet.java` | No UI route found. | Content item/version lifecycle. | Kernel auth. | Basic. | Content repo. | Approval/validation. | Backend-only. | Missing UI/journey trace. |
| Content validation API | `DmosContentValidationServlet.java` | No UI route found. | Validate content against policy/brand. | Kernel auth. | Basic. | Validation repo. | Compliance events. | Backend-only. | Missing UI/journey trace. |
| Ad copy API | `DmosAdCopyServlet.java` | No UI route found. | Generate/retrieve ad copy. | Kernel auth. | Basic. | Content/version repo. | AI action log. | Backend-only. | Missing UI/journey trace. |
| Landing page API | `DmosLandingPageServlet.java` | No UI route found. | Generate/retrieve landing page. | Kernel auth. | Basic. | Content/version repo. | Publishing maybe. | Backend-only. | Missing UI/journey trace. |
| Email follow-up API | `DmosEmailFollowUpServlet.java` | No UI route found. | Generate/retrieve follow-up. | Kernel auth/consent. | Basic. | Content/contact repo. | Email draft. | Backend-only. | Consent/suppression E2E needed. |
| Competitor research API | `DmosCompetitorResearchServlet.java` | No UI route found. | Run/get research. | Kernel auth. | Basic. | Research repo. | AI action log. | Backend-only. | External data provenance needed. |
| Website audit API | `DmosWebsiteAuditServlet.java` | No UI route found. | Run/get SEO/perf audit. | Kernel auth. | Basic. | Audit repo. | External fetch? | Backend-only. | SSRF safety must be proven. |
| Lead scoring API | `DmosLeadScoringServlet.java` | No UI route found. | Generate/get lead score. | Kernel auth. | Basic. | Lead repo. | AI/model metrics. | Backend-only. | Model explainability/data consent needed. |
| Intake questionnaire API | `DmosIntakeQuestionnaireServlet.java` | No UI route found. | Save/get/submit intake. | Kernel auth. | Field limit. | Intake repo. | Workflow trigger. | Backend-only. | Onboarding UI missing. |
| Public intake API | `DmosPublicIntakeServlet.java` | External/public forms. | Capture leads without auth. | Public with rate/abuse controls. | PII/consent validation. | Lead/contact/suppression. | Lead creation. | Partial. | Needs anti-abuse, consent, suppression, PII tests. |
| Google Ads connector service | `dm-connector-google-ads`, `DmGoogleAdsCampaignConnectorServiceImpl` | Internal workflow. | Create campaigns/read performance. | Connector enabled + approval. | Payload validation. | Connector state. | External write. | Partial. | README says command/workflow wiring pending. |
| Kernel bridge | `DigitalMarketingKernelAdapter*` | All application services. | Auth, consent, approval, audit, risk, notification, flags. | Kernel ports/plugins. | Context validation. | Plugin-specific. | Audit/risk/notify. | Partial. | Notification fake; feature/risk defaults unsafe. |

### 3.5 Database Inventory

| DB Item | File(s) | Purpose | Callers | Constraints | Indexes | Data Integrity Rules | Complete? | Issues |
|---|---|---|---|---|---|---|---|---|
| `dmos_campaigns` | `dm-persistence/.../V1__create_dmos_campaigns.sql`, `PostgresCampaignRepository.java` | Campaign persistence. | `CampaignRepository`. | PK `(id, workspace_id)`. | `workspace_id`. | Required fields only. | Partial. | No tenant_id, FK workspace, check constraints on status/type, unique name rules, lifecycle/deletion fields. |
| `dmos_ai_action_log` | `V4__create_dmos_ai_action_log.sql` | AI/system action transparency. | AI action log service/API. | PK `(action_id, workspace_id)`. | workspace, correlation, entity, occurred_at. | Required fields only. | Partial. | No tenant_id, no confidence range check, no JSONB evidence validation, no retention policy. |
| `dmos_suppression` migration | `V6__migrate_suppression_to_contact_point_hash.sql` | Suppression/PII privacy. | Suppression/contact services. | Adds NOT NULL hash. | `contact_point_hash`. | Hash migration. | Incorrect. | Uses SHA-256 not HMAC; raw email column left in place. |
| Postgres campaign adapter | `PostgresCampaignRepository.java` | Production adapter. | Campaign service. | SQL upsert. | Uses table indexes. | Single row save/read. | Partial. | Upsert overwrites `created_by`; tenant isolation indirect only. |
| In-memory repositories | `dm-infra/src/main/java/...` | Local/test/staging adapters. | Dev/test bootstrap. | Map key conventions. | N/A. | Process memory. | Not production. | In production source and usable for staging; must fail closed in prod. |
| Other persistence migrations/adapters | `dm-persistence` | Approvals/content/workspace/etc. | Various services. | Not fully inspected. | Not fully inspected. | Not fully inspected. | Unknown/partial. | Need full migration inventory and Testcontainers coverage per aggregate. |

### 3.6 Test Inventory

| Test | File | Type | Feature Covered | What It Proves | Real or Mocked | Valid? | Gaps |
|---|---|---|---|---|---|---|---|
| Campaign servlet tests | `dm-api/src/test/.../DmosCampaignServletTest.java` | API unit/integration-ish | Campaign API. | Servlet response behavior. | Likely service mocks/fakes. | Needs review/run. | Must validate against API contract and real bootstrap. |
| Approval servlet tests | `DmosApprovalServletTest.java` | API unit | Approval API. | Submit/decide/status/snapshot/list. | Likely mocked service. | Needs review/run. | Must test idempotency required and header fail-closed. |
| Strategy/proposal/SOW/budget/etc. servlet tests | Multiple `Dmos*ServletTest.java` | API unit | Generated content APIs. | Handler behavior. | Likely mocked services. | Needs review/run. | Need E2E through real services/repositories. |
| Application service tests | `dm-application/src/test/.../*ServiceImplTest.java` | Unit | Domain/application logic. | Service invariants. | Often in-memory/test adapters. | Partially valid. | Must include concurrency, auth deny, DB failure, feature flag off. |
| Persistence ITs | `dm-persistence/src/test/.../Postgres*RepositoryIT.java` | DB integration | Postgres adapters. | SQL adapter behavior. | Real Testcontainers expected. | Positive direction. | Need coverage for every repository and migration rollback/validation. |
| Integration tests | `dm-integration-tests/src/test/.../CampaignLifecycleIT.java`, `WorkspaceLifecycleIT.java`, `CampaignE2EIT.java`, `CampaignLoadPerfIT.java` | Integration/performance | Campaign/workspace lifecycle. | Multi-layer flow. | Unknown. | Needs run. | Must use real production composition, Postgres, Kernel plugins. |
| UI unit tests | `ui/src/**/*.test.*` surfaced by README as 61 tests. | Unit/component | UI components/hooks. | Rendering/action behavior. | Testing library/mocks. | Needs run. | Should assert API calls, permission states, error states. |
| Playwright E2E | `ui/e2e/*.spec.ts` | Browser E2E/a11y/responsive/visual | Dashboard/approval/accessibility/kill-switch/lead analytics. | Browser behavior. | Fixtures in `e2e/fixtures.ts`. | Partial. | README says CI wiring pending; must include real backend mode. |
| Contract tests | Not clearly found. | Contract | API/UI DTO alignment. | N/A. | N/A. | Missing. | Add generated OpenAPI/Zod/Java DTO contract checks. |
| Security tests | Some role/kill-switch tests surfaced. | Security/permission | Role checks and kill-switch. | Partial. | Unknown. | Partial. | Add fail-closed auth/session/tenant tests across every servlet. |

---

## 4. Product Behavior Map

| Capability | User/Persona | Problem Solved | Expected UX | Expected Backend Behavior | Expected Data Behavior | Success Criteria |
|---|---|---|---|---|---|---|
| Workspace onboarding | Marketer/admin | Create scoped marketing workspace. | Guided setup/intake, clear next actions. | Create workspace, enforce tenant membership, audit. | Durable workspace row with tenant linkage. | User can create/select workspace and resume later. |
| Campaign lifecycle | Marketer/manager | Create, validate, launch, pause campaigns. | Create/edit/review/launch with risk/compliance explanation. | Auth, compliance preflight, risk, approval when needed, connector execution, audit. | Durable campaign state transitions. | Launch only after compliance/risk/approval; no duplicate launches. |
| Approval workflow | Approver/manager | Human governance over critical actions. | Queue, detail, snapshot, approve/reject, comments. | Role-based approval, immutable snapshot, idempotent decisions. | Approval record + snapshot + audit trail. | Only authorized approvers decide once; user sees outcome. |
| AI action transparency | Operator/compliance | Make AI/system actions inspectable. | Timeline with evidence/policy/confidence and redaction. | Log every AI proposal/execution/block with permissions. | Durable action log with retention and redaction. | Every critical AI action traceable to context/evidence. |
| Content generation | Marketer/creative reviewer | Generate ad copy, landing pages, email follow-up. | AI drafts with review/validation/approval. | Generate, validate, version, route approval, audit. | Content item/version records. | Approved versions retrievable and publishable. |
| Budget recommendation | Marketer/finance approver | Recommend budget and pacing. | Recommendation with rationale, risk, approval. | AI/deterministic recommendation with constraints. | Durable recommendation and decision trail. | Recommendation cannot exceed caps/policies. |
| Google Ads connector | Campaign operator | Execute approved campaign to Google Ads. | Connector status, preview payload, approve, execute, observe. | OAuth, token refresh, idempotent external write, retry/DLQ, rollback. | Connector state + external IDs + audit. | No unapproved external writes; failures observable. |
| Public lead intake | Visitor/prospect | Capture leads from landing page forms. | Simple public form with consent. | Rate-limit, validate, consent proof, suppression check, PII protection. | Lead/contact records with hashed/encrypted PII. | No raw PII leakage; consent and suppression enforced. |
| Reporting/dashboard | Executive/operator | Understand activity, risk, outcomes. | Low-cognitive dashboard with real metrics. | Query real data and observability signals. | Metric snapshots/events. | Widgets match backend state and explain freshness. |
| Kernel governance | Platform owner | Ensure product follows platform policies. | Invisible by default, visible audit/governance surface. | Product uses Kernel auth, audit, consent, approval, feature flags, risk, notification. | Platform-level audit and metrics. | No product-specific bypasses; fail closed on missing Kernel capability. |

---

## 5. Requirement-to-Implementation Traceability Matrix

| Requirement / Capability | UI Route/Page | User Actions | API/Backend Handler | Service/Domain Logic | DB Models/Queries | Tests | Observability | Status |
|---|---|---|---|---|---|---|---|---|
| Login/auth/session | `/login` | Login/logout/session refresh. | Header parsing in servlets. | `DmSecurityContextMapper`, Kernel auth. | None. | UI tests likely. | Logs only. | Complete but incorrect for production. |
| Dashboard | `/workspaces/:id/dashboard` | View widgets. | Approvals + AI actions APIs. | Approval/AI action services. | Approval + AI log. | UI tests. | Dashboard metrics dashboards exist separately. | Partially implemented. |
| Approval queue | `/workspaces/:id/approvals` | Filter, open detail. | `DmosApprovalServlet#handleListPending`. | `ApprovalWorkflowService`. | Approval records/snapshots. | API/UI tests. | Audit/logs. | Partially incorrect. |
| Approval decision | `/workspaces/:id/approvals/:requestId` | Approve/reject. | `DmosApprovalServlet#handleDecide`. | Role decision logic. | Approval record update. | API/app tests. | Audit/logs. | Partial. |
| AI action log | `/workspaces/:id/ai-actions` | View timeline. | `DmosAiActionLogServlet`. | AI action log service. | `dmos_ai_action_log`. | API/UI tests. | Logs/dashboards. | Partial. |
| Campaign create/get/launch/pause | No UI. | None in UI. | `DmosCampaignServlet`. | `CampaignServiceImpl`. | `dmos_campaigns`. | API/app/integration tests. | Metrics/log/audit. | Backend only / partial. |
| Strategy generation/approval | No UI. | None in UI. | `DmosStrategyServlet`. | Strategy service. | Strategy persistence. | API tests. | AI action log expected. | Backend only. |
| Proposal/SOW | No UI. | None in UI. | Proposal/SOW servlets. | Proposal/SOW services. | Persistence unknown. | API tests. | Audit expected. | Backend only. |
| Budget recommendation | No UI. | None in UI. | `DmosBudgetRecommendationServlet`. | Recommendation services. | Persistence unknown. | API/app tests. | Budget dashboard exists. | Backend only. |
| Content/ad-copy/landing/email | No UI. | None in UI. | Content/ad/landing/email servlets. | Generation/version/validation services. | Content/version persistence. | API/app tests. | AI action log expected. | Backend only. |
| Public intake/leads | No UI. | Public form not found. | `DmosPublicIntakeServlet`. | Lead/contact/consent/suppression services. | Lead/contact/suppression. | API/app tests. | Privacy/security dashboard. | Backend only / privacy risk. |
| Google Ads execution | No UI. | None. | Internal connector. | Google Ads services. | Connector state. | Connector tests. | Connector dashboards/runbooks. | Partial; wiring pending. |
| Kernel notification | No UI. | Triggered by backend. | Kernel bridge. | `notifyUser`. | None. | Unknown. | Log only. | Stubbed/fake success. |
| Runtime feature flags | UI env + backend constants. | Flags gate UI/actions. | Kernel bridge default. | `DmosFeatureFlags`, `DmosProductConfig`. | None. | Unknown. | Partial. | Duplicated/inconsistent. |

---

## 6. End-to-End User Journey Audit

| Journey | Entry Point | Expected Outcome | Actual Behavior | Correct? | Complete? | Mock/Stub Risk | Gaps | Severity | Required Fix | Required Tests |
|---|---|---|---|---|---|---|---|---|---|---|
| First-time user | `/login` | Real authenticated workspace selection/setup. | User enters arbitrary token/workspace/tenant/principal/session. | ❌ | ❌ | High | No trusted auth, no workspace discovery. | P0 | Real auth/session + workspace selection. | Browser E2E against real auth stub/provider with server validation. |
| Returning user | App reload | Resume valid session or reauth. | Token is memory-only; context partly sessionStorage; reload loses token but keeps IDs. | ❌ | ❌ | High | Split token/context persistence. | P1 | Unified secure session model. | Reload/session expiry tests. |
| Approval review | `/approvals` → detail → decide | Reviewer sees true pending queue and can decide once. | Queue filtered by tenantId-as-subject; decision API exists. | ⚠️ | ⚠️ | Medium | Queue semantics incorrect; idempotency missing. | P1 | Add reviewer/workspace pending queue endpoint. | E2E with two roles and repeated submit. |
| Campaign launch | API-only | Draft campaign launches after compliance/risk/approval and real notifications. | API can launch; notification logs only; risk may default 0.0. | ❌ | ⚠️ | High | No UI, fake notification, fail-open risk. | P0/P1 | Fail closed on missing notification/risk plugins; add UI. | Full campaign E2E with high-risk block and notification failure. |
| Content generation approval | No UI | Generate draft, validate, approve, publish. | Backend endpoints/services exist; no UI. | Unknown | ❌ | Medium | No full journey. | P1 | Build content workbench or hide routes. | E2E from generate through approval. |
| Public lead capture | Public endpoint | Lead captured with consent, suppression, PII safe storage. | Backend endpoint exists; PII migration flawed. | ❌ | ⚠️ | High | Privacy gap. | P0 | HMAC/encrypt PII and enforce suppression. | Public intake + DSAR + suppression DB tests. |
| Dashboard reporting | `/dashboard` | Real overview of approvals/workflows/growth/risk. | Approval and AI data; growth placeholder; workflow from approvals. | ❌ | ⚠️ | Medium | Misleading widgets. | P1 | Replace with real metrics or gate. | UI+API freshness and source tests. |
| Google Ads execution | Internal approval trigger | External campaign created only after approval. | Connector adapter partial; wiring pending. | ❌ | ❌ | High | No command/workflow trigger. | P1 | Durable workflow and connector execution service. | Testcontainers/wiremock E2E with idempotency. |
| Error/retry/recovery | Any API/UI | Specific, recoverable errors. | Raw `API error status: text`; inconsistent envelopes. | ⚠️ | ⚠️ | Medium | Error contract drift. | P1 | Canonical error envelope + UI error mapper. | Contract tests for all status codes. |
| Permission denied | Approval page/detail/API | UI hides actions; backend denies. | UI role comes from login input; backend role enforcement service-dependent. | ⚠️ | ⚠️ | High | UI roles not trusted unless real auth. | P0/P1 | Use server-issued claims only. | AuthZ E2E with forged role header blocked or validated. |

---

## 7. UI/UX Correctness and Completeness Audit

| UI Area | File(s) | Finding | Correctness Impact | Completeness Impact | Severity | Required Fix | Tests |
|---|---|---|---|---|---|---|---|
| Route coverage | `ui/src/App.tsx` | UI covers only login/dashboard/approvals/AI actions. | Users cannot execute most product capabilities. | Major. | P1 | Add module routes or remove/gate backend capabilities. | Route inventory test against capability registry. |
| Login/auth | `AuthContext.tsx` | Local auth is not real; session refresh is fake. | Broken trust boundary if deployed. | Major. | P0 | Replace with auth provider/session API; dev login behind dev-only flag. | Production build test ensuring dev login disabled. |
| Approval queue | `ApprovalQueuePage.tsx`, `useApprovalQueue.ts` | Uses tenant ID as subject ID. | Queue may show wrong/missing approvals. | Major. | P1 | Add reviewer-scoped pending queue endpoint. | E2E with multiple target subjects. |
| Dashboard growth | `GrowthGoalWidget.tsx` | Placeholder-backed “Coming soon” / “Metrics loading…” UI. | Misleads user. | Medium. | P1 | Gate or connect to real metrics. | Feature-flag-off and on tests. |
| Dashboard workflow | `WorkflowStatusWidget.tsx` | Displays approvals as workflows. | Incorrect label/mental model. | Medium. | P1 | Use workflow service/API or relabel as “Approval workflow”. | Widget data source tests. |
| AI action log | `AiActionLogPage`, `AiActionLogWidget` | Experimental UI flag separate from backend. | May hide/show inconsistently. | Medium. | P1 | Centralize feature flag source. | Flag matrix E2E. |
| Error states | `http-client.ts`, pages | Raw backend text exposed as JS errors. | Poor UX and possible sensitive leak. | Medium. | P1 | Parse canonical error envelopes; redact sensitive details. | Error envelope UI tests. |
| Accessibility | Playwright a11y spec exists. | Good direction; execution unknown. | Unknown. | Medium. | P2 | Wire a11y suite in CI. | CI browser a11y runs. |

---

## 8. Frontend Actions, State, and Data Flow Audit

| Action/State Flow | File(s) | Expected | Actual | Correct? | Complete? | Production Mock/Stub? | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|
| Auth token/context | `AuthContext.tsx`, `http-client.ts` | Server-issued session and claims. | User-provided context stored locally; token memory-only. | ❌ | ❌ | Yes, local auth. | Add real auth API/session exchange and server-issued roles. | Login, reload, expiration, forged-role tests. |
| Session refresh | `AuthContext.tsx` | Refresh through backend/auth provider. | Extends expiry locally. | ❌ | ❌ | Yes. | Implement refresh endpoint or remove. | Expiry/refresh E2E. |
| Required headers | `http-client.ts` | Attach tenant/principal/session/correlation/idempotency as required. | Attaches tenant/principal/session/correlation, not idempotency. | ⚠️ | ⚠️ | No. | Generate idempotency per write. | POST retry tests. |
| Approval queue | `useApprovalQueue.ts` | Fetch reviewer/workspace pending queue. | Fetches pending by subjectId. | ❌ | ⚠️ | No. | Add correct queue API. | Multi-subject approval tests. |
| Approval decision | `approvals.ts` | Idempotent decision, cache invalidation/refetch. | POST without idempotency; query invalidation unknown. | ⚠️ | ⚠️ | No. | Add mutation hook with invalidation and idempotency. | Duplicate-click/rollback tests. |
| Feature flags | `feature-flags.ts` | Kernel-controlled product flags. | Frontend Vite env flags. | ❌ | ⚠️ | No. | Fetch runtime flags from backend/kernel. | Flag parity contract tests. |
| API errors | `http-client.ts` | Typed error object. | Throws raw Error string. | ⚠️ | ⚠️ | No. | Typed `DmosApiError` with code, status, correlation. | Error rendering tests. |

---

## 9. Backend/API/Domain Logic Audit

| Backend Flow | File(s) | Expected Behavior | Actual Behavior | Correct? | Complete? | Mock/Stub? | Security/Data Risk | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|---|
| Campaign create | `CampaignServiceImpl`, `DmosCampaignServlet` | Auth, validate, save, audit, idempotent. | Mostly present; idempotency required by servlet but not implemented at service/repo. | ⚠️ | ⚠️ | No. | Duplicate creates. | Add persistent idempotency and validation. | Duplicate POST tests. |
| Campaign launch | `CampaignServiceImpl` | Compliance + risk + approval + real notification + connector. | Compliance/risk present; notification fake; connector trigger unclear. | ❌ | ⚠️ | Yes. | External action may lack reliable governance. | Fail closed on missing risk/notification; wire connector workflow. | Launch success/block/failure E2E. |
| Approval submit | `DmosApprovalServlet` | Mandatory idempotency; snapshotSummary contract. | Idempotency optional; code uses `description`. | ❌ | ⚠️ | No. | Duplicate approvals/contract drift. | Align DTO/spec and require idempotency. | Contract + duplicate submit tests. |
| Approval decide | `DmosApprovalServlet` | Role enforcement, single decision. | Service-dependent; UI roles user-provided in local mode. | ⚠️ | ⚠️ | No. | Forged roles if backend trusts headers without auth. | Use Kernel-authenticated claims. | Forged role deny E2E. |
| Header contract | Multiple servlets/API docs | Consistent mandatory tenant/principal/session/correlation. | API_CONTRACT/README/servlets disagree; servlets default values. | ❌ | ❌ | No. | Broken auth/audit attribution. | Central servlet context builder. | Missing-header tests for every route. |
| Error contract | Multiple servlets/API docs | `{error, message}`. | Many servlets return `{status|code, message}`. | ❌ | ❌ | No. | UI parsing inconsistency. | Shared error response factory. | Contract tests. |
| Feature gates | `DmosProductConfig`, `DmosFeatureFlags`, Kernel adapter | Incomplete prod features off by default; runtime toggle. | AI/Google Ads default true; adapter default true. | ❌ | ❌ | Yes. | Incomplete feature exposure. | Kernel flag plugin with fail-closed fallback. | Prod config tests. |
| Notification | `DigitalMarketingKernelAdapterImpl.notifyUser` | Real user notification. | Logs and returns success. | ❌ | ❌ | Yes. | False success, missed approvals/alerts. | Real NotificationPlugin/EventBus/DLQ. | Notification delivery/failure tests. |
| Risk | `DigitalMarketingKernelAdapter.evaluateRisk` | Real risk or fail closed for critical actions. | Default returns 0.0. | ❌ if default used | ⚠️ | Yes. | Launch can pass with missing risk. | Remove default or fail closed for critical flows. | Missing risk plugin test. |
| Google Ads connector | README/connector services | Approved external writes. | Adapter complete; wiring pending. | ❌ | ❌ | Partial. | Unclear execution safety. | Durable command workflow + rollback/idempotency. | WireMock connector E2E. |

---

## 10. Database and Persistence Audit

| DB Operation/Model | File(s) | Expected Data Rule | Actual Behavior | Correct? | Complete? | Integrity Risk | Performance Risk | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|---|
| Campaign save/find | `V1__create_dmos_campaigns.sql`, `PostgresCampaignRepository.java` | Tenant/workspace-isolated durable campaign lifecycle. | PK id/workspace, no tenant, no FK/checks. | ⚠️ | ⚠️ | Medium-high. | Low/medium. | Add tenant_id, FK, checks, lifecycle fields. | Migration + repository IT. |
| AI action log | `V4__create_dmos_ai_action_log.sql` | Immutable, tenant-scoped, redaction-aware timeline. | Workspace-scoped table, no tenant, no confidence check. | ⚠️ | ⚠️ | Medium. | Medium as log grows. | Add tenant_id, retention partitions/indexes, constraints. | Redaction + pagination IT. |
| Suppression hash migration | `V6__migrate_suppression_to_contact_point_hash.sql` | HMAC keyed irreversible lookup; raw email removed/encrypted. | Uses plain SHA-256; raw email remains. | ❌ | ❌ | High privacy risk. | Low. | Use HMAC/crypto service, safe rollout, drop raw PII. | Migration privacy tests. |
| In-memory adapter use | `dm-infra/*` | Dev/test only. | Production source + staging documented. | ❌ for production | ❌ | Data loss. | Single-node only. | Fail fast in non-dev/test; separate test fixtures. | Prod profile boot test. |
| Postgres adapter coverage | `dm-persistence` | Adapter for every repository port. | Campaign exists; other adapters unknown/partial. | ⚠️ | ⚠️ | Data loss if fallback in-memory. | Unknown. | Complete all adapters and composition. | Testcontainers full product E2E. |
| Transactions | Service/repository layer | Multi-step writes transactional. | Campaign save + audit are separate async operations. | ⚠️ | ⚠️ | Partial write/audit drift. | Low. | Use outbox/unit-of-work for domain write + audit. | Failure injection tests. |
| Deletes/retention | Migrations/services | Clear retention/deletion/DSAR. | README claims DSAR; DB reviewed insufficient. | Unknown | ⚠️ | Privacy risk. | Unknown. | Add retention/deletion schema and tests. | DSAR E2E tests. |

---

## 11. Production Mock/Stub/Shortcut Zero-Tolerance Audit

| File | Evidence | Production Reachable? | Critical Flow? | Feature Flagged? | Allowed? | Severity | Required Action |
|---|---|---:|---:|---:|---:|---|---|
| `ui/src/context/AuthContext.tsx` | Session refresh comment: would call API in production; for now extends expiry. | Yes if UI shipped. | Yes. | No. | No. | P0 | Implement real refresh or remove/gate. |
| `ui/src/context/AuthContext.tsx` | Login accepts arbitrary token/workspace/tenant/principal/session. | Yes if UI shipped. | Yes. | No. | No. | P0 | Dev-only login behind production-disabled flag; real auth. |
| `dm-kernel-bridge/.../DigitalMarketingKernelAdapterImpl.java` | `notifyUser` logs and returns success; comment says placeholder. | Yes. | Yes. | No. | No. | P0 | Implement notification plugin/event bus. |
| `dm-kernel-bridge/.../DigitalMarketingKernelAdapter.java` | Default `isFeatureEnabled` returns true. | Yes if implementation missing override. | Yes. | No. | No. | P0 | Fail closed or delegate to real flag service. |
| `dm-kernel-bridge/.../DigitalMarketingKernelAdapter.java` | Default `evaluateRisk` returns 0.0. | Yes if implementation missing override. | Yes for launch. | No. | No. | P0/P1 | Fail closed on critical risk. |
| `dm-application/.../DmosProductConfig.java` | AI and Google Ads default true. | Yes. | Yes. | Env only. | No. | P0/P1 | Default incomplete external/AI writes off in prod. |
| `dm-infra/src/main/java/...InMemory*Repository.java` | ConcurrentHashMap persistence in production source. | Yes if wired. | Yes. | Profile/docs only. | No for prod. | P0/P1 | Move to test/dev module or guard composition. |
| `dm-infra/README.md` | “Single-instance staging — non-persistent but fully functional.” | Yes in staging. | Yes. | No. | No for release. | P1 | Disallow staging data loss except explicit ephemeral demo. |
| `ui/src/components/dashboard/GrowthGoalWidget.tsx` | “Coming soon” / “Metrics loading…” placeholder. | Yes. | Non-critical. | Experimental flag. | Only if disabled by default and hidden. | P1/P2 | Remove/gate or wire real API. |
| `dm-persistence/.../V6__migrate_suppression_to_contact_point_hash.sql` | Comment says HMAC; SQL uses plain SHA-256 fallback; raw email drop commented. | Yes. | Yes privacy. | No. | No. | P0 | HMAC/encrypt/drop raw PII. |
| `README.md` | Google Ads “wiring through command/workflow runtime pending.” | Yes if feature enabled. | Yes external write. | Backend default true. | No. | P1 | Disable until workflow is wired. |
| `README.md` | UI E2E pending/partial. | Yes release process. | Yes release quality. | N/A. | No. | P1 | Wire CI E2E. |

---

## 12. Duplicate and Source-of-Truth Audit

| Duplicate Area | Files | Why It Is Duplicate | Risk | Canonical Owner | Delete/Merge Plan | Required Tests |
|---|---|---|---|---|---|---|
| Feature flags | `ui/src/lib/feature-flags.ts`, `DmosFeatureFlags.java`, `DmosProductConfig.java`, Kernel adapter default. | UI env flags, backend env constants, runtime keys, and Kernel defaults disagree. | Incomplete features exposed or hidden inconsistently. | Kernel FeatureFlagPlugin + generated product flag manifest. | Generate TS/Java constants from a single `dmos-feature-flags.yaml`; remove default-true fallback. | UI/backend flag parity tests. |
| Auth/header requirements | README, `docs/API_CONTRACT.md`, servlet context builders, `http-client.ts`. | Required headers differ; servlets default principal/session. | Broken audit/auth attribution. | Shared platform HTTP context builder. | Centralize in `dm-api`/platform module and update docs/generated spec. | Missing-header contract tests for all servlets. |
| Error envelope | API_CONTRACT vs servlet `ErrorBody`. | Contract says `{error,message}`, servlets return `{code|status,message}`. | UI cannot reliably parse errors. | Shared DMOS API error factory/spec. | Replace per-servlet error DTOs. | OpenAPI response schema tests. |
| Approval DTO names | API_CONTRACT says `snapshotSummary`; servlet `SubmitRequest` uses `description`; UI types need alignment. | Same concept named differently. | Failed API requests or missing snapshot. | Approval domain/API schema. | Generate DTOs from OpenAPI/schema. | Submit approval contract test. |
| Workflow concept | Dashboard workflow widget uses approvals. | Approval workflow status is not general workflow status. | Misleading UX and future drift. | Workflow service/capability registry. | Rename to approval workflow or add real workflow API. | Widget source contract test. |
| Persistence adapters | `dm-infra` in-memory and `dm-persistence` Postgres. | Same repository ports can be wired differently without strict profile gate. | Data loss in staging/prod. | Production composition root/profile validator. | Make adapter selection explicit and fail closed. | Prod boot test rejects in-memory. |
| Observability dashboards | Product runbooks/dashboards and application `DmosObservability*`. | Good, but signals not uniformly emitted. | Dashboards may be empty/misleading. | Kernel/platform observability registry. | Standardize metrics contract per flow. | Metrics existence tests. |

---

## 13. Security, Privacy, Governance, and Permission Audit

| Area | File(s) | Risk | Correct Behavior | Actual Behavior | Severity | Required Fix | Tests |
|---|---|---|---|---|---|---|---|
| Authentication | `AuthContext.tsx`, servlets | Arbitrary local auth and backend defaults can bypass identity. | Real token/session validated server-side. | UI user-provided; servlets default principal/session. | P0 | Add real auth/session and fail closed. | Auth bypass tests. |
| Authorization | `CampaignServiceImpl`, approval services | Role/permission checks depend on Kernel/context correctness. | Kernel-issued claims only. | Headers/roles may be user-supplied in local mode. | P0/P1 | Server validates token and extracts claims. | Forged role denied. |
| Tenant isolation | DB schemas/repositories | DB lacks tenant_id in reviewed core tables. | Tenant_id present and enforced with workspace FK. | Workspace-only scoping. | P1 | Add tenant_id and RLS/queries where appropriate. | Cross-tenant DB tests. |
| PII hashing | `V6__migrate_suppression...sql` | Plain SHA-256 email hash is dictionary-attackable. | HMAC with secret key or encrypted searchable token. | SHA-256 fallback. | P0 | Platform crypto/HMAC service and key rotation. | Known dictionary attack regression test. |
| Raw PII retention | `V6__...sql` | Raw email column remains. | Drop or encrypt raw PII after migration. | Drop commented out. | P0/P1 | Two-phase migration with verification and drop. | Migration tests. |
| Public intake | `DmosPublicIntakeServlet` | Abuse, spam, PII, consent. | Rate limiting, consent proof, suppression, anti-abuse. | Endpoint exists; full proof not reviewed. | P1 | Add explicit public intake hardening doc/tests. | Abuse/consent E2E. |
| Error messages | `http-client.ts`, servlets | Raw backend text in UI. | Redacted user-safe errors with correlation ID. | Raw `API error status: text`. | P1 | Typed error mapper. | Sensitive error redaction tests. |
| Audit logging | Kernel bridge, services | Incomplete if fake notification/risk/flags. | Every critical action has durable audit. | Audit plugin used, but some side effects fake. | P1 | Outbox/audit consistency. | Failure injection audit tests. |
| Secrets | Google Ads/OAuth, env flags | Secret leakage/incorrect default. | Secrets never logged; connectors disabled without config. | Not fully inspected; connector default enabled. | P1 | Secret config validator. | Missing-secret boot tests. |

---

## 14. Observability and Operability Audit

| Flow | Logs | Metrics | Traces | Audit Events | Gaps | Required Fix |
|---|---|---|---|---|---|---|
| Campaign create/launch/pause | Service logs and metric increments. | `DmosMetricsCollector` increments. | OTel partial per README. | Kernel audit recorded. | Notification delivery not real; DB/audit atomicity. | Add outbox, trace spans, notification status metrics. |
| Approval submit/decide | Servlet/service logs. | Unknown. | Partial. | Approval/audit expected. | Idempotency optional; errors inconsistent. | Metrics for pending/decision latency/failures. |
| AI action log | API/logs. | Dashboard files exist. | Partial. | Durable AI action log. | Redaction and retention signals. | Add permission/redaction metrics and retention jobs. |
| Google Ads connector | Runbooks/dashboard surfaced. | Connector dashboards exist. | Partial. | Should audit external writes. | Workflow wiring pending; external retries/DLQ need proof. | Durable workflow spans, DLQ, external idempotency metrics. |
| Public intake | Unknown. | Privacy/security dashboard exists. | Partial. | Consent/audit expected. | Anti-abuse and privacy metric gaps. | Add consent/suppression/PII-safe metrics. |
| Feature flags | Logs unknown. | Unknown. | N/A. | Should audit flag decisions for critical flows. | UI/backend inconsistency. | Kernel flag decision audit and startup config metric. |
| Persistence profile | Unknown. | Unknown. | N/A. | N/A. | In-memory vs Postgres not fail-closed. | Emit adapter profile metric and fail-fast in prod. |
| Health/readiness | README docs imply API quality gates. | Unknown. | Unknown. | N/A. | No verified production server bootstrap. | Health checks must include DB, Kernel plugins, connectors disabled/ready state. |

---

## 15. Performance and Scalability Audit

| Area | Risk | Evidence | Impact | Required Fix | Tests/Benchmarks |
|---|---|---|---|---|---|
| In-memory repositories | Data loss and no multi-instance consistency. | `dm-infra` ConcurrentHashMap adapters. | Production/staging correctness failure. | Ban outside dev/test. | Prod boot failure test. |
| JDBC adapters | Blocking JDBC can block if not isolated. | `PostgresCampaignRepository` uses `Promise.ofBlocking(executor, ...)`. | Acceptable if executor sized/tuned. | Standardize executor/backpressure. | Load test with saturated DB. |
| DB indexes | Limited reviewed indexes. | Campaign only workspace index; AI log indexes. | Large datasets may slow filters/reports. | Query-driven index review. | Explain plan tests. |
| UI rendering | Dashboard simple. | Small route set. | Good for MVP. | Add virtualization for large queues/logs. | 1k/10k approval/action list tests. |
| API pagination | AI action log contract has limit; approval queue pending list unknown. | API_CONTRACT. | Large approval queues risk. | Add cursor pagination. | Pagination contract tests. |
| External connector | Google Ads blocking/external latency. | README notes event-loop blocking mitigation pending. | Event loop stalls/external write delays. | Async worker/queue workflow. | WireMock latency/load tests. |
| Rate limiting | README says rate limiter. | DmosApiRateLimiter wraps servlets. | Good if tenant-aware. | Verify public intake/IP fallback and prod bypass impossible. | Rate limit E2E. |
| Bundle/CI | UI build scripts exist. | README scripts. | Unknown. | Add bundle budget. | CI bundle-size checks. |

---

## 16. Test Correctness and Coverage Audit

| Capability/Flow | Existing Tests | Missing Tests | Invalid Tests | Required Tests | Priority |
|---|---|---|---|---|---|
| Auth/session | UI tests likely; no proof of real auth. | Production login disabled, session refresh real, forged headers denied. | Local-only tests would be insufficient. | Browser + API auth E2E. | P0 |
| Header enforcement | Servlet tests exist. | Every authenticated route must reject missing tenant/principal/session/correlation in prod. | Tests accepting anonymous defaults are invalid. | Parameterized all-route header tests. | P0 |
| Feature flags | Some kill-switch/flag tests surfaced. | Default-off incomplete features, Kernel flag failure, UI/backend parity. | Tests relying on default true invalid. | Runtime flag contract test. | P0 |
| Notification | Unknown. | Delivery success/failure/retry/DLQ. | Log-only tests invalid. | NotificationPlugin integration test. | P0 |
| PII/suppression | Suppression/contact tests surfaced. | HMAC migration, raw PII absence, DSAR, dictionary-attack regression. | SHA fallback tests invalid. | Testcontainers migration + privacy tests. | P0 |
| Persistence | Postgres ITs exist for some repos. | Every repository port, prod composition uses Postgres only. | In-memory-only integration not release-grade. | Full product E2E with Testcontainers. | P1 |
| Approval queue | API/UI tests likely. | Correct reviewer queue, multi-subject, multi-role, duplicate decisions. | TenantId-as-subject tests invalid. | Approval workflow browser E2E. | P1 |
| Campaign launch | Campaign lifecycle tests exist. | Missing risk plugin fail-closed, notification failure, connector execution/rollback. | Tests with fake risk=0 and fake notification invalid. | End-to-end launch suite. | P1 |
| API contract | API tests exist. | Error envelope, DTO names, status codes against canonical spec. | Per-servlet DTO tests alone insufficient. | OpenAPI/JSON schema tests. | P1 |
| UI E2E | Playwright files exist. | CI wiring, real backend mode, a11y gate, visual baseline. | Fixture-only E2E insufficient for prod. | Playwright against real API/Testcontainers. | P1 |
| Performance | Campaign load perf IT exists. | Connector latency, queue depth, DB query plans. | In-memory perf tests invalid for production. | Postgres/external latency benchmarks. | P2 |

---

## 17. Prioritized Remediation Plan

| Priority | Area | Issue | Evidence/File(s) | Required Fix | Acceptance Criteria | Tests Required |
|---|---|---|---|---|---|---|
| P0 | Auth/session | Local auth and fake refresh. | `ui/src/context/AuthContext.tsx`, servlet context builders. | Real auth provider/session endpoint; dev login disabled in prod. | Production build cannot authenticate from arbitrary values; backend rejects missing/invalid principal/session. | Auth E2E, forged header tests. |
| P0 | Kernel notification | Notification log-only success. | `DigitalMarketingKernelAdapterImpl.notifyUser`. | Implement NotificationPlugin/EventBus delivery with retry/DLQ. | Campaign/approval notifications have durable delivery records and failure propagation. | Delivery, retry, DLQ tests. |
| P0 | Kernel feature flags | Default true and backend defaults expose incomplete features. | `DigitalMarketingKernelAdapter.isFeatureEnabled`, `DmosProductConfig`. | Kernel FeatureFlagPlugin with fail-closed fallback; generated flag manifest. | Missing flag service disables non-GA critical features; prod defaults safe. | Flag-off and service-down tests. |
| P0 | Risk fail-open | Default risk score 0.0 can allow launch. | `DigitalMarketingKernelAdapter.evaluateRisk`. | Remove low-risk default; critical flows fail closed if risk unavailable. | Launch cannot proceed when risk plugin unavailable unless explicit approved override. | Missing risk plugin test. |
| P0 | PII suppression | Plain SHA-256 + raw email retained. | `V6__migrate_suppression_to_contact_point_hash.sql`. | HMAC/encryption migration; raw email drop/encrypt; key rotation plan. | No raw email remains in suppression table; hashes differ per secret. | Migration/privacy tests. |
| P1 | Persistence | In-memory adapters in main/staging path. | `dm-infra/README.md`, `InMemoryCampaignRepository.java`. | Production composition fails if in-memory selected. | `DMOS_ENV=production` boot rejects in-memory repositories. | Prod profile boot test. |
| P1 | Production bootstrap | No proven composition root wiring all servlets/services/plugins/adapters. | Search did not reveal main/router; README only describes module checks. | Add `DmosApiServer`/launcher with environment profile validation. | One command starts real DMOS API with Postgres + Kernel plugins. | Smoke E2E. |
| P1 | Approval queue | UI uses tenantId as subject. | `ApprovalQueuePage.tsx`, `useApprovalQueue.ts`. | Add reviewer/workspace-scoped queue endpoint and UI hook. | Approver sees all pending approvals for role/workspace. | Multi-role approval queue E2E. |
| P1 | Idempotency | UI writes no `X-Idempotency-Key`; approval optional. | `http-client.ts`, `approvals.ts`, `DmosApprovalServlet`. | Shared write client middleware + required backend idempotency. | Duplicate POST returns cached same result or 409 safely. | Duplicate submit/decision tests. |
| P1 | API contract | Error/header/DTO drift. | `docs/API_CONTRACT.md`, servlets. | Generate DTOs and response helpers from one schema. | All routes pass schema/status/header contract tests. | Contract test suite. |
| P1 | UI coverage | Most product capabilities lack UI routes. | `ui/src/App.tsx`, API contract. | Add low-cognitive module UI or hide routes/features. | Every visible capability has route/action/API/DB/test traceability. | Route-to-capability tests. |
| P1 | Dashboard correctness | Growth and workflow widgets are placeholder/misleading. | `GrowthGoalWidget.tsx`, `WorkflowStatusWidget.tsx`. | Wire real APIs or remove/gate. | No “coming soon” or fake “metrics loading” in prod. | Dashboard E2E. |
| P1 | Google Ads wiring | Connector adapter partial; workflow wiring pending. | README. | Durable command workflow with approval, idempotency, retry, rollback. | Approved launch triggers exactly one external write; failures observable. | WireMock connector E2E. |
| P1 | DB integrity | Missing tenant_id/FK/checks in reviewed tables. | `V1`, `V4` migrations. | Add tenant-scoped schema and constraints. | Cross-tenant writes/reads impossible at DB/repo level. | Migration + cross-tenant tests. |
| P2 | Observability | OTel partial and dashboards may lack emitted signals. | README, dashboards. | Define per-flow metrics/spans and startup readiness signals. | Dashboards populate from emitted metrics in integration test. | Metrics scrape tests. |
| P2 | Test gates | UI E2E CI pending; persistence coverage only CI-enabled. | README/build.gradle. | CI matrix for backend, persistence, UI unit, Playwright, contract. | Release gate fails on missing E2E/contract/security tests. | CI workflow checks. |
| P2 | Duplicate flags/docs | UI/backend/docs flags and headers duplicate. | Multiple files. | Canonical manifests + generated docs. | No hand-maintained drift in docs/code. | Drift detection tests. |

---

## 18. Kernel Platform Enhancement Plan

DMOS depends on Kernel as a first-class execution and governance substrate. The audit shows DMOS has product-local bridges, but Kernel needs stronger platform capabilities so products do not re-implement or silently bypass critical controls.

### KE-P0 Enhancements

| ID | Kernel Capability | Current Gap | Required Platform Enhancement | Acceptance Criteria |
|---|---|---|---|---|
| KE-P0-1 | NotificationPlugin | DMOS notification is log-only and returns success. | Provide durable notification plugin with templates, channels, delivery state, retries, DLQ, and audit. | Product call cannot report success unless delivery is queued/delivered with durable ID. |
| KE-P0-2 | FeatureFlagPlugin | Product feature flags default true or env-local. | Runtime, tenant/workspace/role-aware flags with fail-closed defaults and generated constants. | Missing flag service disables non-GA features in production. |
| KE-P0-3 | RiskManagement fail-closed contract | Adapter default risk score is 0.0. | Kernel risk port must distinguish `score`, `unavailable`, `not_applicable`; critical policies fail closed. | Campaign launch blocks on unavailable critical risk model. |
| KE-P0-4 | Auth/session context builder | Each servlet builds/defaults context differently. | Shared Kernel/platform HTTP context middleware for tenant/principal/session/correlation/claims. | Product servlets cannot default anonymous in production. |
| KE-P0-5 | Privacy crypto service | Product migration used SHA fallback. | Platform HMAC/encryption/key-rotation service and migration helper. | No product implements ad-hoc PII hashing. |
| KE-P0-6 | Idempotency service | Approval idempotency optional; UI has no key. | Tenant-scoped persistent idempotency plugin/middleware for all writes. | Duplicate writes are safe across restarts and replicas. |
| KE-P0-7 | Production profile validator | In-memory adapters can be wired outside dev/test. | Kernel/environment validator blocks fake adapters, fake plugins, and log-only implementations in prod. | `DMOS_ENV=production` fails startup with any fake/in-memory critical adapter. |

### KE-P1 Enhancements

| ID | Kernel Capability | Current Gap | Required Platform Enhancement |
|---|---|---|---|
| KE-P1-1 | Product capability registry | UI and backend capabilities drift. | Kernel-hosted capability registry that UI reads to render only enabled/ready capabilities. |
| KE-P1-2 | Contract registry | API docs/servlets drift. | OpenAPI/JSON Schema registry with generated Java/TS DTOs and contract tests. |
| KE-P1-3 | Workflow engine | Google Ads and approval-triggered execution wiring pending. | Durable workflow engine with command handlers, retries, compensation, audit, and visibility. |
| KE-P1-4 | Observability registry | Dashboards exist but signal completeness unknown. | Kernel observability contract per product capability: required metrics, spans, logs, audit events. |
| KE-P1-5 | Outbox/audit consistency | Domain writes and audit/notifications are separate. | Transactional outbox pattern for DB writes + audit + notifications + external actions. |
| KE-P1-6 | Consent/suppression primitive | Public intake/email follow-up need consistent consent enforcement. | Consent + suppression plugin with PII-safe lookups, proof records, and DSAR integration. |
| KE-P1-7 | Product bootstrap template | No obvious complete DMOS production composition root. | Kernel product launcher template wiring auth, DB, plugins, flags, OTel, health/readiness. |

---

## 19. Production Readiness Gate

| Gate | Result | Notes |
|---|---:|---|
| Ready for production | ❌ No | P0/P1 blockers around auth, Kernel notification/flags/risk, PII, persistence, contract drift, and UI completeness. |
| Ready for internal demo | ⚠️ Yes, limited | Only with explicit demo profile, in-memory/non-persistent notice, external writes disabled, and fake/local auth labeled as dev-only. |
| Ready behind feature flag | ⚠️ Partial | Approval and AI action read-only flows can be exposed to trusted testers. Campaign launch, public intake, Google Ads, content generation, and PII-heavy flows should remain disabled. |
| Minimum before release | ❌ Not met | Must fix P0s and top P1s, then run full backend/UI/E2E/security/contract suite. |

### Critical blockers before any release candidate

1. Replace local/fake auth/session and enforce mandatory principal/session/tenant/correlation headers consistently.
2. Implement Kernel notification delivery or fail closed on notification-required flows.
3. Implement Kernel feature flags with fail-closed production defaults and product capability registry.
4. Fix PII suppression migration with HMAC/encryption and raw PII removal.
5. Prove production persistence bootstrap uses PostgreSQL only and rejects in-memory adapters.
6. Align README/API_CONTRACT/servlet DTOs/error envelopes/headers/idempotency.
7. Add full production composition root and E2E tests against Postgres/Testcontainers and Kernel plugin implementations.
8. Gate or remove incomplete UI placeholders and backend-only capabilities until end-to-end complete.

---

## 20. Final Release Checklist

| Category | Checklist |
|---|---|
| Correctness | Every critical flow has expected vs actual behavior documented; all P0/P1 closed; no fail-open risk/feature/notification paths. |
| Completeness | Every API capability has UI/API/service/DB/test/observability traceability, or is hidden and disabled. |
| No production mocks/stubs | No in-memory/fake/log-only/default-success adapter is reachable in production. |
| UI/UX | No “coming soon,” misleading widgets, dead actions, or invisible backend-only critical journeys. |
| Backend/API | Shared context builder, canonical DTOs, canonical error envelope, mandatory idempotency on writes. |
| DB/data integrity | Tenant-scoped schemas, FK/check/unique constraints, retention/deletion rules, Testcontainers migration tests. |
| Security/privacy | Real auth, server-issued roles, consent/suppression enforced, no raw PII leakage, secrets safe. |
| Kernel/platform | Notification, feature flags, risk, auth context, idempotency, privacy crypto, workflow/outbox are platform-owned and fail closed. |
| Observability | Logs/metrics/traces/audit events exist for every critical journey; dashboards backed by real emitted signals. |
| Performance | DB queries indexed and paginated; external connector workflows asynchronous and retryable; UI lists scalable. |
| Tests | Unit, API, DB integration, browser E2E, a11y, security, contract, performance, and feature-flag-off tests all wired in CI. |
| Documentation | README/API_CONTRACT/runbooks/readiness docs match actual behavior and production gates. |

---

## Appendix A — High-Value Next Inspection Commands

Run from a clean checkout at the audited ref:

```bash
git checkout 561f4d48329d8509fb2f4c13f769effb13a9975a

# Backend module checks
./gradlew :products:digital-marketing:dm-core-contracts:check
./gradlew :products:digital-marketing:dm-domain-packs:check
./gradlew :products:digital-marketing:dm-kernel-bridge:check
./gradlew :products:digital-marketing:dm-domain:check
./gradlew :products:digital-marketing:dm-application:check
./gradlew :products:digital-marketing:dm-infra:check
./gradlew :products:digital-marketing:dm-persistence:check
./gradlew :products:digital-marketing:dm-connector-google-ads:check
./gradlew :products:digital-marketing:dm-api:check
./gradlew :products:digital-marketing:dm-integration-tests:check

# UI checks
cd products/digital-marketing/ui
pnpm install
pnpm type-check
pnpm lint
pnpm test
pnpm test:e2e
pnpm test:e2e:a11y
```

Add release-blocking tests before accepting green CI:

```bash
# Required new gates
./gradlew :products:digital-marketing:dm-api:test --tests '*MissingHeader*'
./gradlew :products:digital-marketing:dm-integration-tests:test --tests '*ProductionBootstrap*'
./gradlew :products:digital-marketing:dm-persistence:test --tests '*MigrationValidation*'
./gradlew :products:digital-marketing:dm-integration-tests:test --tests '*KernelFailClosed*'
```
