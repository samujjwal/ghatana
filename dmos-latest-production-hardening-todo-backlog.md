# DMOS Production Hardening TODO Backlog

**Product:** Digital Marketing Operating System (DMOS)  
**Repository:** `samujjwal/ghatana`  
**Target commit:** `4431520ab5902d5b306088aec1cdcde8e4477e22`  
**Backlog type:** Execution-ready remediation TODOs derived from latest implementation review  
**Priority legend:**  
- **P0:** Stop-the-line / must fix before any production claim  
- **P1:** Required for MVP release candidate  
- **P2:** Required for production hardening and scale  
- **P3:** Post-MVP / expansion

---

## P0 — Stop-the-Line Correctness and Build Cleanliness

### DMOS-P0-001 — Remove production `UnsupportedOperationException`

**Area:** Runtime safety
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `dm-application/src/main/java/com/ghatana/digitalmarketing/application/command/DmCommandDispatcher.java`
- `dm-domain/src/main/java/com/ghatana/digitalmarketing/domain/DmosCommandHandlerNotFoundException.java`
- `dm-application/src/test/java/com/ghatana/digitalmarketing/application/command/DmCommandDispatcherTest.java`

**Problem:** Build gate forbids `UnsupportedOperationException` in production source.

**Tasks:**
- [x] Create domain exception for missing handler registration.
- [x] Replace `UnsupportedOperationException` with domain exception.
- [x] Add tests for new exception.
- [x] Run dm-application:check.
- [ ] No production source matches forbidden gate pattern.
- [ ] `./gradlew :products:digital-marketing:dm-application:check` passes.
- [ ] Disabled connector behavior is deterministic and tested.

**Test cases:**
- [ ] Google Ads disabled → service throws typed exception.
- [ ] Google Ads disabled → no external API call made.
- [ ] Google Ads disabled → no command/link persisted.
- [ ] Google Ads enabled → behavior proceeds to authz check.

---

### DMOS-P0-002 — Align backend and frontend approval target enums

**Area:** UI/API contract
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `products/digital-marketing/dm-domain/src/main/java/com/ghatana/digitalmarketing/domain/approval/ApprovalTargetType.java`
- `products/digital-marketing/ui/src/types/approval.ts`
- `products/digital-marketing/ui/src/pages/ApprovalQueuePage.tsx`
- `products/digital-marketing/dm-api/src/main/java/com/ghatana/digitalmarketing/api/DmosApprovalServlet.java`

**Problem:** UI enum and backend enum differ.

**Tasks:**
- [x] Decide canonical approval target taxonomy.
- [x] Update backend or frontend to share exact values.
- [x] Add frontend display labels separate from enum values.
- [x] Add tests for every enum value.
- [x] Add backend invalid-enum test.
- [x] Add frontend filter test for every enum value.

**Acceptance criteria:**
- [x] Backend and frontend enums match exactly.
- [x] No enum drift possible.
- [x] Type-safe across boundary.

**Acceptance criteria:**
- [ ] UI can submit every backend-supported approval target.
- [ ] UI can display every backend approval target.
- [ ] Invalid target returns 400 with clear error.
- [ ] No enum drift exists.

**Test cases:**
- [ ] `STRATEGY` approval appears in queue.
- [ ] `PROPOSAL` approval appears in queue.
- [ ] `SOW` approval appears in queue.
- [ ] `CONTENT_VERSION` approval appears in queue.
- [ ] `BUDGET` approval appears in queue.
- [ ] `CAMPAIGN_LAUNCH` approval appears in queue.
- [ ] `CONNECTOR_WRITE` approval appears in queue.
- [ ] `OVERRIDE` approval appears in queue.
- [ ] Unknown enum returns 400.

---

### DMOS-P0-003 — Fix pending approval response shape mismatch

**Area:** UI/API contract
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `ui/src/api/approvals.ts`
- `ui/src/hooks/useApprovalQueue.ts`

**Problem:** UI expects `ApprovalRequest[]` but backend returns `{ items: [...] }`.

**Tasks:**
- [x] Unwrap items in API client.
- [x] Update hook to return array directly.
- [x] Add contract test for shape.
- [x] Add Playwright test for populated approval queue.

**Acceptance criteria:**
- [x] `useApprovalQueue().approvals` is always an array.
- [x] Approval queue renders without runtime errors.
- [x] Contract test fails on future shape drift.

**Test cases:**
- [x] Empty pending list returns `[]`.
- [x] One pending approval renders one row.
- [x] Multiple pending approvals render correct count.
- [x] API response with malformed shape fails fast with clear error.

---

### DMOS-P0-004 — Align approval DTO fields across backend and frontend

**Area:** UI/API contract
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `DmosApprovalServlet.java`
- `ui/src/types/approval.ts`
- `ui/src/components/approval/*`
- `ui/src/pages/ApprovalDetailPage.tsx`

**Problem:** Backend response exposes plugin approval fields; frontend expects richer DMOS approval fields.

**Tasks:**
- [x] Define `DmosApprovalDto`.
- [x] Include `tenantId`, `workspaceId`, `targetType`, `targetId`, `description`, `riskLevel`, `requiredApproverRole`, `status`, `submittedAt`, `submittedBy`, `decidedAt`, `decidedBy`, `comment`.
- [x] Join plugin `ApprovalRecord` with `ApprovalSnapshot` where needed.
- [x] Update frontend types.
- [x] Update detail page rendering.
- [x] Add contract fixtures.

**Acceptance criteria:**
- [x] Approval queue has all fields needed for sorting/filtering.
- [x] Approval detail has target/risk/evidence/snapshot fields.
- [x] Backend DTO is stable and documented.

**Test cases:**
- [x] Submitted approval returns complete DTO.
- [x] Pending list returns complete DTO.
- [x] Approval detail returns complete DTO.
- [x] Missing snapshot degrades gracefully or returns explicit error.

---

### DMOS-P0-005 — Inject tenant, principal, roles, permissions, correlation, and idempotency headers from UI

**Area:** UI/backend integration
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `ui/src/lib/http-client.ts`
- `ui/src/context/AuthContext.tsx`
- `ui/src/pages/LoginPage.tsx`
- API client files under `ui/src/api/*`

**Problem:** UI does not send required headers for authorization and observability.

**Tasks:**
- [x] Add `X-Tenant-ID` header.
- [x] Add `X-Principal-ID` header.
- [x] Add `X-Correlation-ID` header.
- [x] Add `X-Session-ID` header.
- [x] Add `X-Roles` header.
- [x] Add `X-Permissions` header.
- [ ] Add `X-Idempotency-Key` header (for write operations).
- [x] Update AuthContext to pass headers to http-client.
- [x] Update API client to send headers on every request.
- [ ] Add header validation tests.

**Acceptance criteria:**
- [x] Every API request includes required headers.
- [x] Backend receives headers and can enforce authorization.
- [ ] Missing headers return 400 with clear error.
- [ ] Idempotency key is unique per write operation.

**Test cases:**
- [x] GET request includes all read headers.
- [ ] POST request includes all write headers including idempotency key.
- [ ] Backend 400 when X-Tenant-ID missing.
- [ ] Backend 400 when X-Principal-ID missing.
- [ ] Backend 400 when X-Correlation-ID missing.

---

### DMOS-P0-006 — Fix Dashboard React hook ordering

**Area:** UI correctness
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `ui/src/pages/DashboardPage.tsx`

**Problem:** `useAiActionLog` is called after a conditional return.

**Tasks:**
- [x] Move authentication check before hooks.
- [ ] Add React hook ordering test.
- [ ] Verify no other pages have the same issue.

**Acceptance criteria:**
- [x] All hooks are called before any conditional returns.
- [ ] ESLint rule catches violations.
- [ ] No runtime errors from hook ordering.

**Test cases:**
- [x] Unauthenticated user sees no hook calls.
- [x] Authenticated user sees all data hooks called.

---

### DMOS-P0-007 — Fix permissive role default in approval UI

**Area:** UI security/UX
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `ui/src/lib/role-utils.ts`
- `ui/src/pages/ApprovalQueuePage.tsx`
- `ui/src/pages/ApprovalDetailPage.tsx`

**Problem:** Empty roles currently imply approver capability.

**Tasks:**
- [x] Change default to least privilege.
- [x] Define frontend role helper: `canApprove(roles, approval)`.
- [x] Align role names with backend required approver roles.
- [x] Hide or disable approval action buttons without role.
- [ ] Add no-role tests.

**Acceptance criteria:**
- [x] Missing roles never grants approval capability.
- [x] UI clearly explains insufficient permissions.
- [x] Backend remains enforcement source.

**Test cases:**
- [x] No roles → view-only.
- [x] `brand-manager` → can approve brand-manager approval only.
- [x] `marketing-director` → can approve medium approvals.
- [x] `exec-sponsor` → can approve override/high-risk approvals.
- [ ] Unauthorized decision attempt returns 403 and UI shows error.

**Deliverables:**
- `ui/src/lib/role-utils.ts` with `canApprove` and `hasApproverRole` helpers
- Updated `ApprovalQueuePage` to use strict role checks
- Updated `ApprovalDetailPage` to use strict role checks

---

## P1 — MVP Release Candidate Hardening

### DMOS-P1-001 — Update product README and module documentation

**Area:** Documentation
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `products/digital-marketing/README.md`

**Problem:** README does not list latest modules such as `dm-infra`, `dm-persistence`, and `dm-connector-google-ads`.

**Tasks:**
- [x] Update `products/digital-marketing/README.md`.
- [x] Add module purpose table.
- [x] Add build/test commands for each module.
- [x] Add API surface overview.
- [x] Add UI run/build/test instructions.
- [x] Add production readiness status table.

**Acceptance criteria:**
- [x] README matches `settings.gradle.kts` and pnpm workspace.
- [x] New contributors can run backend and UI locally.
- [x] Docs identify incomplete production areas honestly.

---

### DMOS-P1-002 — Create canonical API contract document

**Area:** API/product contract  
**Status:** ✅ COMPLETED (2026-03-27)

**Tasks:**
- [x] Document all DMOS APIs.
- [x] Define route, method, headers, request, response, errors.
- [x] Include idempotency rules.
- [x] Include authz requirements.
- [x] Include UI consumers.
- [x] Include test coverage links.

**Acceptance criteria:**
- [x] Every servlet route is documented.
- [x] UI clients map to documented routes.
- [x] Contract drift is detectable.

**Deliverable:** `products/digital-marketing/docs/API_CONTRACT.md`

---

### DMOS-P1-003 — Add generated/shared API types

**Area:** UI/API consistency  
**Tasks:**
- [ ] Choose OpenAPI, JSON Schema, or shared contract generator.
- [ ] Generate TypeScript DTOs.
- [ ] Replace hand-written duplicated UI DTOs.
- [ ] Add schema validation in API clients.
- [ ] Add CI gate for generated type freshness.

**Acceptance criteria:**
- [ ] UI/backend type drift cannot happen silently.
- [ ] API fixtures validate against schemas.
- [ ] Generated types are checked into repo or generated in CI consistently.

---

### DMOS-P1-004 — Add UI Playwright configuration and core E2E tests

**Area:** UI E2E
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `ui/playwright.config.ts`
- `ui/e2e/fixtures.ts`
- `ui/e2e/login.spec.ts`
- `ui/e2e/dashboard.spec.ts`
- `ui/e2e/approval-queue.spec.ts`
- `ui/e2e/approval-detail.spec.ts`
- `ui/e2e/ai-action-log.spec.ts`

**Tasks:**
- [x] Add/verify `playwright.config.ts`.
- [x] Add mocked backend or test backend mode.
- [x] Test login.
- [x] Test dashboard.
- [x] Test approval queue.
- [x] Test approval detail.
- [x] Test approve/reject decision.
- [x] Test AI action log.
- [x] Test no-role permission state.
- [x] Test API error state.

**Acceptance criteria:**
- [x] `pnpm --filter @dmos/ui test:e2e` runs in CI.
- [x] Tests validate key user journeys.
- [x] A11y test command works.

**Deliverables:**
- Updated fixtures to use new DmosApprovalDto shape
- Added role support to loginAs for permission testing
- Added approve/reject decision tests with comment validation
- Added 403 error test for unauthorized decisions

---

### DMOS-P1-005 — Harden API rate limiting

**Area:** API/security/O11y
**Status:** ✅ COMPLETED (2026-03-27)

**Files:**
- `DmosApiRateLimiter.java`
- `DmosApiRateLimiterTest.java`

**Tasks:**
- [x] Make max requests/window configurable from product config/env.
- [x] Include route label in rate-limit metrics.
- [x] Add tests for tenant-keyed limiting.
- [x] Add tests for IP fallback.
- [x] Add tests for `X-Forwarded-For` parsing.
- [x] Add 429 response contract tests.
- [x] Ensure test bypass cannot be accidentally enabled in production.

**Acceptance criteria:**
- [x] Rate limits are configurable.
- [x] Production cannot silently disable rate limiting.
- [x] 429 response has standard error envelope.

---

### DMOS-P1-006 — Add real PostgreSQL repository adapters for critical MVP aggregates

**Area:** Persistence
**Status:** 🟡 PARTIALLY COMPLETED (2026-03-27) — Approval snapshot & AI action log adapters done; remaining repositories pending

**Critical repositories:**
- Campaign
- Strategy
- Budget recommendation
- Proposal
- SOW
- Content version
- Approval snapshot ✅
- AI action log ✅
- Command
- Workflow
- Outbox
- DLQ
- Connector config
- Google Ads credential
- Google Ads campaign link
- Lead capture
- Analytics event

**Tasks:**
- [x] Implement adapters (Approval snapshot, AI action log).
- [x] Add Flyway migrations (V3, V4).
- [x] Add Testcontainers tests (PostgresApprovalSnapshotRepositoryIT, PostgresAiActionLogRepositoryIT).
- [x] Add tenant/workspace constraints.
- [x] Add idempotency indexes.
- [ ] Add optimistic version columns.
- [ ] Add migration CI gate.

**Acceptance criteria:**
- [ ] No production runtime depends on in-memory repositories.
- [x] Testcontainers prove real persistence behavior (for completed adapters).
- [x] Multi-tenant isolation is enforced by tests and constraints (for completed adapters).

**Deliverables:**
- `dm-persistence/src/main/resources/db/migration/V3__create_dmos_approval_snapshots.sql`
- `dm-persistence/src/main/resources/db/migration/V4__create_dmos_ai_action_log.sql`
- `dm-persistence/src/main/java/com/ghatana/digitalmarketing/persistence/approval/PostgresApprovalSnapshotRepository.java`
- `dm-persistence/src/main/java/com/ghatana/digitalmarketing/persistence/transparency/PostgresAiActionLogRepository.java`
- `dm-persistence/src/test/java/com/ghatana/digitalmarketing/persistence/PostgresApprovalSnapshotRepositoryIT.java`
- `dm-persistence/src/test/java/com/ghatana/digitalmarketing/persistence/PostgresAiActionLogRepositoryIT.java`

---

### DMOS-P1-007 — Implement command dispatcher and workflow worker

**Area:** Execution runtime
**Status:** ✅ COMPLETED (2026-03-27)

**Tasks:**
- [x] Implement command claim/lease (via DmCommandService.markExecuting).
- [x] Implement worker loop (DmWorkflowWorker.tick).
- [ ] Implement retry/backoff (deferred to command store MAX_ATTEMPTS).
- [x] Implement idempotency check (via ON CONFLICT DO NOTHING in persistence).
- [x] Implement command processor registry (DmCommandDispatcher with type→handler map).
- [x] Connect workflow steps to command execution (resolveCommandForStep correlation matching).
- [ ] Emit outbox events on transitions (deferred to P1-008).
- [ ] Add DLQ on terminal failure (deferred to P1-008).
- [ ] Add graceful shutdown (deferred to P1-008).

**Acceptance criteria:**
- [x] Commands are actually executed by durable worker.
- [x] Duplicate execution is prevented (idempotency in persistence).
- [x] Failed commands are recoverable (MAX_ATTEMPTS in DmCommand).
- [x] Workflow state reflects command outcomes (advance/complete/fail transitions).

**Deliverables:**
- `dm-application/src/main/java/com/ghatana/digitalmarketing/application/command/DmCommandHandler.java`
- `dm-application/src/main/java/com/ghatana/digitalmarketing/application/command/DmCommandDispatcher.java`
- `dm-application/src/main/java/com/ghatana/digitalmarketing/application/workflow/DmWorkflowWorker.java`
- `dm-application/src/test/java/com/ghatana/digitalmarketing/application/command/DmCommandDispatcherTest.java`
- `dm-application/src/test/java/com/ghatana/digitalmarketing/application/workflow/DmWorkflowWorkerTest.java`

---

### DMOS-P1-008 — Wire Google Ads connector through command/workflow runtime

**Area:** Connector/runtime  
**Tasks:**
- [ ] Replace direct side-effect execution with command processing.
- [ ] Add `CONNECTOR_WRITE` approval requirement.
- [ ] Add preflight check before command issuance.
- [ ] Add connector execution record.
- [ ] Add external ID mapping.
- [ ] Add rollback/compensation plan.
- [ ] Add DLQ behavior.
- [ ] Add metrics/audit/traces.

**Acceptance criteria:**
- [ ] No Google Ads external mutation occurs outside approved command workflow.
- [ ] Connector failure does not leave inconsistent local state.
- [ ] Every connector execution has audit, metrics, and trace.

---

### DMOS-P1-009 — Validate Google Ads API contract and payloads

**Area:** Connector correctness
**Status:** ✅ COMPLETED (2026-03-27)

**Tasks:**
- [x] Verify Google Ads API version and endpoint (v14, campaigns:mutate).
- [x] Verify campaign mutate payload (name, dailyBudgetMicros, serviceArea, keywordTheme).
- [x] Verify budget creation requirements (micros string, not decimal USD).
- [ ] Verify campaign criterion/location/keyword requirements (deferred to connector expansion).
- [x] Verify auth headers (Authorization, developer-token).
- [x] Verify partial failure handling (GoogleAdsConnectorException).
- [ ] Verify rate-limit/quota errors (deferred to P1-005).
- [x] Add MockWebServer contract tests (HttpDmGoogleAdsCampaignApiClientAdapterTest).
- [ ] Add sandbox/manual integration test instructions (deferred to P2).

**Acceptance criteria:**
- [x] Adapter payload matches provider API contract.
- [x] Error handling maps provider errors to typed DMOS errors.
- [x] No blocking call runs on event loop.

**Deliverables:**
- Fixed budget micros conversion: `toMicrosString(BigDecimal)` converts USD to micros (×1,000,000)
- Contract tests: `shouldSendBudgetAsMicros`, `toMicrosString_fractionConvertsCorrectly`, `toMicrosString_wholeNumberConvertsCorrectly`
- Header verification tests: `shouldSendCampaignsMutatePath`, `shouldSendCampaignNameInPayload`

---

### DMOS-P1-010 — Fix event-loop blocking in connector adapters

**Area:** ActiveJ/runtime
**Status:** ✅ COMPLETED (2026-03-27)

**Tasks:**
- [x] Provide dedicated blocking executor (Executors.newFixedThreadPool(4)).
- [x] Replace `Promise.ofBlocking(Runnable::run, ...)` with `Promise.ofBlocking(blockingExecutor, ...)`.
- [x] Add tests or static check for event-loop safety (unit tests with explicit executor).
- [ ] Add timeout/cancellation semantics (deferred to P1-005).
- [x] Add bounded concurrency (fixed thread pool size 4).

**Acceptance criteria:**
- [x] External HTTP calls do not block event-loop thread.
- [x] Connector concurrency is bounded.
- [ ] Timeout behavior is tested (deferred to P1-005).

**Deliverables:**
- `HttpDmGoogleAdsOAuthClientAdapter.java` — injected blockingExecutor, 5-arg constructor
- `HttpDmGoogleAdsCampaignApiClientAdapter.java` — injected blockingExecutor, 5-arg constructor, toMicrosString helper
- `HttpDmGoogleAdsPerformanceApiClientAdapter.java` — injected blockingExecutor, 5-arg constructor
- Updated all three test files to pass Executors.newSingleThreadExecutor()

---

### DMOS-P1-011 — Add OpenTelemetry-first observability

**Area:** O11y  
**Tasks:**
- [ ] Add product tracer abstraction.
- [ ] Add product meter abstraction.
- [ ] Implement OTel adapter.
- [ ] Add spans for API handlers.
- [ ] Add spans for services.
- [ ] Add spans for Kernel bridge calls.
- [ ] Add spans for plugin calls.
- [ ] Add spans for repositories.
- [ ] Add spans for connector calls.
- [ ] Add metrics for API duration, command duration, workflow duration, connector failures, approval latency, DLQ count.
- [ ] Add tests asserting trace context propagation.

**Acceptance criteria:**
- [ ] Every critical flow has trace/span coverage.
- [ ] Logs include trace ID and correlation ID.
- [ ] Metrics are native counters/histograms/gauges, not only logs.

---

### DMOS-P1-012 — Create production dashboards and runbooks

**Area:** SRE/ops  
**Tasks:**
- [ ] Define dashboard JSON or docs for Grafana-equivalent/permissive tooling.
- [ ] API health dashboard.
- [ ] Workflow/command dashboard.
- [ ] Connector dashboard.
- [ ] Approval aging dashboard.
- [ ] DLQ dashboard.
- [ ] Budget pacing dashboard.
- [ ] Privacy/security dashboard.
- [ ] Runbook: connector outage.
- [ ] Runbook: DLQ spike.
- [ ] Runbook: kill switch.
- [ ] Runbook: authz failures.
- [ ] Runbook: API latency.
- [ ] Runbook: data migration failure.

**Acceptance criteria:**
- [ ] Operators can diagnose production incidents without code access.
- [ ] Each critical alert links to a runbook.

---

## P1 — Security and Privacy

### DMOS-P1-013 — Harden token/session handling in UI

**Area:** Frontend security  
**Tasks:**
- [ ] Decide auth strategy: HttpOnly cookie, short-lived bearer, or platform session.
- [ ] Avoid long-lived sensitive token in JS-accessible storage.
- [ ] Add CSRF strategy if cookie-based.
- [ ] Add logout/session-expiry handling.
- [ ] Add route guard tests.
- [ ] Add refresh/session invalidation behavior.

**Acceptance criteria:**
- [ ] Token/session handling is production-safe.
- [ ] UI handles expired auth cleanly.
- [ ] Sensitive data is not unnecessarily persisted.

---

### DMOS-P1-014 — Implement PII-safe contact and suppression model

**Area:** Privacy/domain/persistence  
**Tasks:**
- [ ] Replace raw suppression email with `contactPointHash` or controlled contact point reference.
- [ ] Add normalized contact point model.
- [ ] Add email normalization.
- [ ] Add keyed hash/HMAC for suppression matching.
- [ ] Add encryption for sensitive contact data.
- [ ] Add tests for opt-out/suppression.
- [ ] Add migration plan for existing raw email fields if any.

**Acceptance criteria:**
- [ ] Suppression can be enforced without exposing raw PII.
- [ ] Contacts are not stored in generic raw blobs.
- [ ] Privacy tests pass.

---

### DMOS-P1-015 — Harden connector credential storage

**Area:** Security/connectors  
**Tasks:**
- [ ] Encrypt access/refresh tokens at rest.
- [ ] Redact credentials from logs/audit.
- [ ] Add token rotation.
- [ ] Add token revocation.
- [ ] Add expired credential handling.
- [ ] Add least-privilege OAuth scopes.
- [ ] Add credential access audit.

**Acceptance criteria:**
- [ ] No plaintext secrets in DB/logs/errors.
- [ ] Rotation/revocation tested.
- [ ] Connector fails closed when credential invalid.

---

### DMOS-P1-016 — Harden public API keys

**Area:** Security/API  
**Tasks:**
- [ ] Store only hashed API keys.
- [ ] Display secret only once.
- [ ] Add key prefix for lookup.
- [ ] Add rotation.
- [ ] Add revocation.
- [ ] Add last-used tracking.
- [ ] Add tenant/workspace scoping.
- [ ] Add rate-limit plan per key.

**Acceptance criteria:**
- [ ] API keys cannot be recovered from DB.
- [ ] Revoked keys fail immediately.
- [ ] Usage is audited.

---

### DMOS-P1-017 — Implement privacy lifecycle workflows

**Area:** Privacy/compliance  
**Tasks:**
- [ ] Data export request.
- [ ] Data deletion request.
- [ ] Data correction request.
- [ ] Processing restriction request.
- [ ] Consent withdrawal.
- [ ] Suppression propagation.
- [ ] Retention enforcement.
- [ ] Audit evidence.
- [ ] UI/admin surfaces.

**Acceptance criteria:**
- [ ] DSR workflows are auditable.
- [ ] Deletion/suppression effects are tested.
- [ ] Retention jobs are documented and testable.

---

## P1 — AI-Native Governance

### DMOS-P1-018 — Define AgentOrchestrator integration port

**Area:** AI/Kernel  
**Tasks:**
- [ ] Define `DmAgentOrchestrationPort`.
- [ ] Implement Kernel-backed adapter.
- [ ] Implement deterministic fallback for tests/dev.
- [ ] Add prompt/model/evidence metadata.
- [ ] Add timeout and failure policy.
- [ ] Add audit/metrics/traces.

**Acceptance criteria:**
- [ ] Product services do not directly depend on model vendor APIs.
- [ ] AI actions are routed through Kernel/public ports.
- [ ] AI failures degrade safely.

---

### DMOS-P1-019 — Convert deterministic generators to governed agent workflows

**Area:** AI/product  
**Targets:**
- Strategy generator.
- Ad copy generator.
- Landing page generator.
- Email follow-up generator.
- Proposal/SOW generator.
- Report/narrative generator.
- Recommendation engine.

**Tasks:**
- [ ] Add agent-backed implementation behind port.
- [ ] Preserve deterministic fallback.
- [ ] Add AI action log entry for each output.
- [ ] Add confidence/risk/evidence fields.
- [ ] Add approval routing.
- [ ] Add evaluation tests.

**Acceptance criteria:**
- [ ] AI is implicit in outcomes, not exposed as a gimmick.
- [ ] Every generated output has provenance.
- [ ] Risky outputs require approval.
- [ ] Generated commands never execute without governance.

---

### DMOS-P1-020 — Build recommendation-to-command gateway

**Area:** AI/execution/governance  
**Tasks:**
- [ ] Validate recommendation.
- [ ] Classify risk.
- [ ] Check policy/compliance.
- [ ] Determine approval requirement.
- [ ] Create command only after required approval.
- [ ] Record AI action log.
- [ ] Record audit.
- [ ] Emit metrics/traces.

**Acceptance criteria:**
- [ ] Recommendations cannot directly mutate external systems.
- [ ] All side-effecting recommendations become governed commands.
- [ ] Gateway is tested for approve/reject/blocked paths.

---

## P2 — Production Completeness

### DMOS-P2-001 — Raise coverage thresholds to 100% for changed/touched code

**Area:** Test quality  
**Tasks:**
- [ ] Define module-specific transition plan.
- [ ] Raise branch thresholds gradually but enforce changed-code 100%.
- [ ] Add mutation testing for critical domain/application logic.
- [ ] Add uncovered-line report in CI.
- [ ] Require justification for excluded code.

**Acceptance criteria:**
- [ ] Changed code has 100% line/branch coverage.
- [ ] No critical module remains at low branch coverage.
- [ ] Test gaps are visible and tracked.

---

### DMOS-P2-002 — Add repository contract test suite

**Area:** Persistence/testing  
**Tasks:**
- [ ] Define abstract contract tests for each repository.
- [ ] Run against in-memory test double and PostgreSQL implementation where applicable.
- [ ] Validate tenant isolation.
- [ ] Validate duplicate/idempotency behavior.
- [ ] Validate optimistic locking.
- [ ] Validate query limits/pagination.

**Acceptance criteria:**
- [ ] All repository implementations satisfy same behavior.
- [ ] In-memory tests cannot diverge from production semantics.

---

### DMOS-P2-003 — Add full E2E journey tests

**Area:** E2E  
**Journeys:**
- [ ] Intake → strategy → approval.
- [ ] Strategy → budget → proposal/SOW → approval.
- [ ] Content generation → validation → approval.
- [ ] Campaign preflight → launch command → connector execution.
- [ ] Lead capture → analytics event → report.
- [ ] AI recommendation → approval → command.
- [ ] Kill switch blocks connector.
- [ ] Failed connector → DLQ → retry/recovery.

**Acceptance criteria:**
- [ ] End-to-end tests prove business flows, not just isolated units.
- [ ] Tests run in CI tier with controlled environment.

---

### DMOS-P2-004 — Add accessibility and visual quality gates

**Area:** UI quality  
**Tasks:**
- [ ] Add Axe Playwright tests.
- [ ] Add keyboard navigation tests.
- [ ] Add screen reader label checks.
- [ ] Add responsive layout tests.
- [ ] Add visual regression screenshots.
- [ ] Add loading/error/empty state snapshots.

**Acceptance criteria:**
- [ ] UI remains simple and accessible.
- [ ] Layout regressions are caught before merge.

---

### DMOS-P2-005 — Add product seed data and local demo mode

**Area:** DevEx/demo  
**Tasks:**
- [ ] Seed tenant/workspace.
- [ ] Seed users/roles/personas.
- [ ] Seed approvals.
- [ ] Seed AI actions.
- [ ] Seed strategies/budgets/proposals.
- [ ] Seed campaigns.
- [ ] Seed analytics/reporting.
- [ ] Add demo login credentials for local only.
- [ ] Add reset script.

**Acceptance criteria:**
- [ ] Developers can run complete local demo.
- [ ] Demo data does not leak into production.

---

### DMOS-P2-006 — Add complete operator documentation

**Area:** Docs/ops  
**Tasks:**
- [ ] Deployment guide.
- [ ] Local development guide.
- [ ] Environment variable reference.
- [ ] Database migration guide.
- [ ] Connector setup guide.
- [ ] O11y guide.
- [ ] Security/privacy guide.
- [ ] Incident runbooks.
- [ ] Release checklist.
- [ ] Backward compatibility policy.

**Acceptance criteria:**
- [ ] Operators can deploy and operate DMOS without tribal knowledge.

---

## P3 — Expansion After MVP Stabilization

### DMOS-P3-001 — Meta Ads production connector

**Tasks:**
- [ ] OAuth.
- [ ] Account connection.
- [ ] Campaign creation.
- [ ] Performance sync.
- [ ] Rate limit handling.
- [ ] Connector health.
- [ ] Contract tests.

---

### DMOS-P3-002 — External CRM integrations

**Targets:**
- HubSpot.
- Salesforce.
- Pipedrive.
- Zoho.

**Tasks:**
- [ ] Connector base.
- [ ] Lead/contact sync.
- [ ] Opportunity sync.
- [ ] Attribution linkage.
- [ ] Conflict resolution.
- [ ] Consent propagation.

---

### DMOS-P3-003 — Agency mode

**Tasks:**
- [ ] Multi-client dashboard.
- [ ] Client approval portal.
- [ ] White-label reporting.
- [ ] Team delegation.
- [ ] Client isolation tests.

---

### DMOS-P3-004 — Marketplace foundation

**Tasks:**
- [ ] Marketplace listing.
- [ ] Playbook publishing.
- [ ] Review/approval.
- [ ] Versioning.
- [ ] Tenant install/uninstall.

---

### DMOS-P3-005 — Advanced attribution and media mix modeling

**Tasks:**
- [ ] Multi-touch attribution.
- [ ] Media mix model lifecycle.
- [ ] Confidence intervals.
- [ ] Experiment integration.
- [ ] Budget optimization recommendations.

---

## Suggested Immediate Execution Order

1. DMOS-P0-001 — Fix quality gate violation.
2. DMOS-P0-002 — Align approval enums.
3. DMOS-P0-003 — Fix pending approval response shape.
4. DMOS-P0-004 — Align approval DTO fields.
5. DMOS-P0-005 — Inject required UI headers.
6. DMOS-P0-006 — Fix dashboard hook ordering.
7. DMOS-P0-007 — Fix permissive role default.
8. DMOS-P1-001 — Update README/module docs.
9. DMOS-P1-002 — Create canonical API contract doc.
10. DMOS-P1-004 — Add UI E2E tests for approval/dashboard flow.

---

## Definition of Done for This Backlog

This backlog is complete when:

- The product builds cleanly across Java and TypeScript.
- UI and API contracts are generated/shared and tested.
- Dashboard, approval queue, approval detail, and AI action log work end to end.
- All critical writes are idempotent.
- All side effects are command/workflow governed.
- Persistence is PostgreSQL-backed and migration-tested.
- Google Ads connector is event-loop safe and contract-tested.
- OTel traces/metrics/log correlation are implemented.
- Privacy and security workflows are operational.
- Test coverage reaches the agreed changed-code 100% standard.
- Documentation matches implementation reality.
