# DMOS Latest Implementation Review and Production Remediation Plan

**Product:** Digital Marketing Operating System (DMOS)  
**Repository:** `samujjwal/ghatana`  
**Reviewed target:** `products/digital-marketing`  
**Reviewed commit:** `4431520ab5902d5b306088aec1cdcde8e4477e22`  
**Purpose:** Convert the latest implementation findings into a production-hardening plan that validates feature completeness, code correctness, test quality, UI/UX simplicity, Kernel/plugin prioritization, AI-native behavior, observability, privacy, security, and operational readiness.

---

## 1. Executive Summary

The latest DMOS implementation at commit `4431520ab5902d5b306088aec1cdcde8e4477e22` is materially more advanced than the previously reviewed state. It now includes:

- A React/Vite product UI under `products/digital-marketing/ui`.
- UI routes for login, dashboard, approval queue/detail, and AI action log.
- Backend/API additions for approvals, AI action log, ad copy, landing page generation, email follow-up, content validation, website audit, and rate limiting.
- Application-layer additions for command store, workflow lifecycle, Google Ads connector services, kill switch, analytics, attribution, recommendations, budget alerts, reports, marketplace, playbooks, experiments, custom model controls, and enterprise security config.
- Domain-layer additions for many DMOS bounded contexts.
- New `dm-persistence`, `dm-infra`, and `dm-connector-google-ads` modules in product settings.
- Basic structured logging and log-based metrics abstractions.

However, the latest implementation is **not yet production-feature-complete**. The codebase appears to be in a broad MVP scaffold phase, with several concrete blockers that must be resolved before it can be considered production-grade.

### Current milestone label

> **DMOS broad MVP scaffold with UI + execution primitives added, but blocked on contract alignment, build-gate cleanliness, production persistence/connector validation, OTel, and true E2E hardening.**

### Production readiness conclusion

| Dimension | Status | Rationale |
|---|---:|---|
| Product breadth | Good scaffold | Many slices now exist across UI, API, domain, application, connectors, workflow, command, analytics, and reporting. |
| Feature completeness | Not complete | Many features are currently service/domain skeletons or deterministic implementations without verified end-to-end runtime wiring. |
| UI/UX | Partial | Dashboard and approval pages exist, but contract mismatches and header/auth issues likely break key flows. |
| API correctness | Partial | Multiple endpoints exist, but frontend/backend DTO and enum contracts are not aligned. |
| Kernel/plugin usage | Improving | Kernel bridge, human approval, compliance, audit, and feature flag concepts exist; startup/runtime integration still needs proof. |
| AI-native behavior | Partial | AI-action transparency exists, but many “AI” services are still deterministic generators rather than governed AgentOrchestrator-backed flows. |
| Observability | Transitional | Structured logs and log-based metric abstraction exist; OpenTelemetry-first tracing/metrics/SLOs are still missing. |
| Security/privacy | Partial | New gates and security concepts exist; raw PII handling, token storage, privacy workflows, and typed policy enforcement need hardening. |
| Tests | Mixed | Test coverage breadth expanded, but thresholds remain below 100%, many tests use in-memory doubles, and UI/API contract issues indicate insufficient integration coverage. |
| Production runtime | Not ready | Persistence, connector correctness, workflow execution, UI E2E, observability, and security/privacy gates require hardening. |

---

## 2. Evidence-Based Findings

### 2.1 UI now exists, but must be contract-hardened

The UI package exists with Vite, React, React Router, TanStack Query, Jotai, shared design system dependencies, Vitest, and Playwright scripts.

**Evidence paths**

- `pnpm-workspace.yaml`
- `products/digital-marketing/ui/package.json`
- `products/digital-marketing/ui/src/App.tsx`
- `products/digital-marketing/ui/src/pages/DashboardPage.tsx`
- `products/digital-marketing/ui/src/pages/ApprovalQueuePage.tsx`

**What is good**

- Product UI is now included in pnpm workspace.
- Root routes exist for login, dashboard, approval queue/detail, and AI action log.
- Dashboard has widgets for approvals, workflow, growth goals, risk/compliance, and AI action log.
- Approval queue supports filtering by target type and risk.
- UI depends on shared Ghatana design/theme/tokens packages.

**Problems**

1. **Approval pending API shape mismatch**
   - UI expects `ApprovalRequest[]`.
   - Backend returns `{ items: [...] }`.
   - Runtime result: `approvals.filter(...)` may fail because `approvals` is an object, not an array.

2. **Approval enum mismatch**
   - Frontend values: `STRATEGY`, `CAMPAIGN`, `CONTENT`, `AUDIENCE_SEGMENT`, `BUDGET_PLAN`, `ANALYTICS_REPORT`, `INTEGRATION_CONFIG`.
   - Backend values: `STRATEGY`, `PROPOSAL`, `SOW`, `CONTENT_VERSION`, `BUDGET`, `CAMPAIGN_LAUNCH`, `CONNECTOR_WRITE`, `OVERRIDE`.

3. **Approval DTO mismatch**
   - Frontend `ApprovalRequest` expects `workspaceId`, `tenantId`, `targetType`, `targetId`, `description`, `riskLevel`, `requiredApproverRole`, `submittedAt`, `decidedBy`, `comment`.
   - Backend `ApprovalRecordResponse` returns plugin-oriented fields: `requestId`, `subjectId`, `requestedBy`, `action`, `status`, `requestedAt`, `expiresAt`, `decidedAt`, `reviewerId`, `reviewerNotes`.

4. **Required backend headers are missing**
   - UI `apiRequest` only sends `Content-Type` and optional `Authorization`.
   - Backend servlets require or use `X-Tenant-ID`, `X-Principal-ID`, `X-Correlation-ID`, `X-Session-ID`, `X-Roles`, `X-Permissions`, and for write operations often `X-Idempotency-Key`.

5. **React hook ordering issue**
   - `DashboardPage` calls `useAiActionLog` after a conditional unauthenticated return.
   - Hooks must be called unconditionally in the same order.

6. **Permissive role fallback**
   - Approval queue treats empty roles as approver-capable.
   - Missing roles should be least-privilege, not permissive.

---

### 2.2 Backend feature breadth increased significantly

The latest commit adds many API and application services.

**Examples of added API files**

- `DmosAdCopyServlet.java`
- `DmosAiActionLogServlet.java`
- `DmosApprovalServlet.java`
- `DmosContentValidationServlet.java`
- `DmosEmailFollowUpServlet.java`
- `DmosLandingPageServlet.java`
- `DmosWebsiteAuditServlet.java`
- `DmosApiRateLimiter.java`

**Examples of added application areas**

- `approval`
- `command`
- `workflow`
- `event`
- `googleads`
- `landingpage`
- `email`
- `recommendation`
- `analytics`
- `attribution`
- `report`
- `rollback`
- `killswitch`
- `security`
- `marketplace`
- `playbook`
- `experiment`
- `model`
- `dashboard`
- `transparency`

**What is good**

- The product is no longer only a campaign backend slice.
- Execution primitives are now present.
- Domain shape is moving toward the canonical architecture.
- Approval, command, workflow, event, connector, analytics, and rollback concepts are represented.

**Problems**

1. Many services appear to be deterministic/scaffold-level rather than fully wired runtime flows.
2. Repository interfaces exist widely, but durable implementations and migrations need validation.
3. API documentation is stale relative to the actual API surface.
4. End-to-end runtime composition is not yet proven.

---

### 2.3 Quality gates improved, but current code likely violates them

The shared DMOS quality gate now includes:

- No production stub/mock/TODO markers.
- No disabled/test theater patterns.
- No mock framework usage.
- No hardcoded secret/security anti-patterns.
- No insecure TLS patterns.
- No MD5/SHA-1 security-sensitive usage.

**Critical issue**

The gate forbids `UnsupportedOperationException` in production source, but the Google Ads connector service throws `new UnsupportedOperationException(...)` when the connector is disabled.

**Impact**

`dm-application:check` should fail if the gate is applied correctly.

**Required fix**

Replace the thrown type with a domain-specific checked/unchecked exception, for example:

- `FeatureDisabledException`
- `ConnectorDisabledException`
- `DmosFeatureDisabledException`

Acceptance criteria:

- No production source contains `UnsupportedOperationException`.
- `./gradlew :products:digital-marketing:dm-application:check` passes.
- Tests validate disabled feature behavior maps to a clean API response.

---

### 2.4 O11y improved but is still not first-class OpenTelemetry

**Current state**

- `DmosObservabilityBaseline` provides MDC helpers and structured log helpers.
- `DmosMetricsCollector` defines DMOS KPI metric names.
- `LoggingDmosMetricsCollector` emits structured log lines.
- `DmosApiRateLimiter` can emit request-duration observations through metrics collector.

**What is good**

- Correlation/tenant/workspace log context is now formalized.
- Metric names are centrally defined.
- API timing is considered.

**Remaining gaps**

1. No product-level OpenTelemetry tracer abstraction.
2. No spans for API → service → Kernel bridge → plugin → repository → connector.
3. No native histograms/counters/gauges.
4. No SLO definitions.
5. No alert rules.
6. No dashboard definitions.
7. No trace correlation tests.
8. Metrics currently use logs as a temporary workaround.

**Production target**

Every externally visible operation should produce:

- Correlation ID.
- Tenant ID.
- Workspace ID.
- Actor ID.
- Operation name.
- Command/workflow/event IDs where applicable.
- Span with success/failure status.
- Duration metric.
- Audit record if governance-relevant.
- Structured error classification.

---

### 2.5 Google Ads connector exists but needs production hardening

**Current state**

- `dm-connector-google-ads` module exists.
- It depends on OkHttp, Jackson, and ActiveJ Promise.
- It includes adapters for OAuth, campaign API, and performance API.
- Application service validates connector status, credential expiry, campaign type/status, feature flag, and tenant ownership.

**Problems**

1. Blocking HTTP is wrapped with `Promise.ofBlocking(Runnable::run, ...)`, which likely executes on the calling thread and may block the ActiveJ event loop.
2. Google Ads API payload and endpoint shape require validation against the actual Google Ads API contract.
3. No evidence yet of robust retry/backoff, idempotency, quota handling, rate-limit handling, or partial failure handling.
4. Credential storage and token refresh security need review.
5. Connector execution should be command/workflow/outbox-driven, not merely direct service invocation.
6. Connector side effects must have rollback/compensating action plans where possible.
7. Connector errors should map to typed error categories.

**Production target**

Google Ads execution must flow through:

```text
Approved plan
→ preflight
→ command store
→ workflow execution
→ connector execution job
→ idempotent external API call
→ result persisted
→ event emitted
→ metrics + audit
→ rollback/compensation registered
```

---

### 2.6 Command and workflow exist, but are lifecycle services rather than full execution runtime

**Current state**

- `DmCommandServiceImpl` supports issue, find, pending list, executing, succeeded, failed, rolled back, count by status.
- `DmWorkflowServiceImpl` supports initiate, start, advance step, complete, fail, pause, resume, rollback, find, list active, count by status.

**What is good**

- Tenant isolation is present.
- Authorization is checked for many state-changing operations.
- Lifecycle transitions are tested.

**Remaining gaps**

1. No dispatcher/worker runtime is proven.
2. No durable scheduling/backoff engine is proven.
3. No lease/lock/claim mechanism for command execution is visible in sampled code.
4. No idempotent command processor registry is proven.
5. No cross-service transaction/outbox integration is proven.
6. Workflow steps are lifecycle records, not necessarily executable steps.
7. No command-to-connector integration E2E evidence.
8. No failure recovery/DLQ operational runbook evidence.

---

### 2.7 Persistence module exists but needs verification

`dm-persistence` exists and declares PostgreSQL, Flyway, and Testcontainers dependencies.

**What is good**

- Module intent is correct.
- Dependency stack aligns with production persistence.

**Need to verify/complete**

1. Actual repository adapters for all critical repositories.
2. Flyway migrations for all domain entities.
3. Testcontainers integration tests.
4. Multi-tenant uniqueness constraints.
5. Idempotency constraints.
6. Event/command/outbox transaction boundaries.
7. PII encryption or hashing where required.
8. Audit linkage columns.
9. Query performance indexes.
10. Migration rollback or forward-fix strategy.

---

### 2.8 Security/privacy is improving but still incomplete

**What is good**

- Security anti-pattern quality gate was added.
- API rate limiting was added.
- Tenant/workspace context exists.
- Kernel authorization remains central.
- Approval and audit exist for governance-relevant flows.

**Problems**

1. UI token is runtime memory only and tenant/workspace are in localStorage.
2. Auth model is not fully integrated with backend headers.
3. UI role default is permissive.
4. Approval role enforcement in UI and backend must be aligned.
5. Contact/suppression PII handling still needs a PII-safe model review.
6. Connector credentials need encryption, rotation, redaction, and audit.
7. Public API keys were added, but hashing/secret display-once/rotation/revocation must be verified.
8. Privacy workflows need full support: export, deletion, correction, restriction, opt-out, suppression, consent proof, retention.

---

### 2.9 Test quality is better, but not at the required bar

**What is good**

- Many tests were added.
- Anti-theater gates exist.
- Sampled workflow tests validate real behavior, not trivial assertions.
- In-memory test doubles are behavior-oriented.

**Problems**

1. Coverage thresholds are not 100%.
2. Connector thresholds are especially low for production side-effect code.
3. UI/API contract mismatch indicates insufficient contract/E2E testing.
4. UI tests/config were not verified in sampled files.
5. Many backend tests are still service-level with in-memory repositories.
6. Real DB/Testcontainers coverage needs verification.
7. Real HTTP servlet + frontend integration coverage needs expansion.
8. No evidence yet of visual regression, a11y, error-state, loading-state, and permission-state UI E2E coverage.

---

## 3. Required Production Readiness Definition

DMOS should be considered production-ready only when the following loop works end to end:

```text
Business intake
→ AI/agent-assisted strategy
→ budget recommendation
→ proposal/SOW
→ approval
→ generated content assets
→ validation/preflight
→ command issuance
→ durable workflow execution
→ connector or safe export
→ lead capture
→ analytics ingestion
→ attribution/report
→ next-best-action recommendation
→ audit/observability/privacy evidence
```

Every step must support:

- Tenant/workspace isolation.
- Role/persona-aware authorization.
- Idempotency.
- Auditability.
- OTel traces and metrics.
- Privacy/consent checks.
- Human takeover/delegation.
- Failure recovery.
- API/UI contract tests.
- E2E tests.
- Runbooks.

---

## 4. Detailed Remediation Plan

### Phase 0 — Stop-the-line correctness fixes

**Goal:** Make the latest code build cleanly and prevent obvious runtime breakage.

#### R0.1 Fix quality gate violation

Replace production `UnsupportedOperationException` with a DMOS-specific exception.

Acceptance criteria:

- No production source violates `validateNoProductionStubs`.
- `dm-application:check` passes.
- Disabled Google Ads feature maps to a typed error and correct HTTP response.

#### R0.2 Align approval frontend/backend contracts

Create a canonical approval API DTO shared between frontend and backend.

Acceptance criteria:

- Backend pending endpoint either returns an array or frontend parses `{ items }`.
- Frontend/backend approval target enums match.
- Approval response includes all fields required by UI or UI types are corrected.
- Contract test validates JSON shape.

#### R0.3 Fix required headers in UI HTTP client

`apiRequest` must inject tenant, principal, roles, permissions, correlation ID, and idempotency key for writes.

Acceptance criteria:

- UI calls to approval endpoints no longer fail due missing `X-Tenant-ID`.
- Write calls include `X-Idempotency-Key`.
- Correlation ID appears in logs/audit.
- Tests cover header injection.

#### R0.4 Fix React hook ordering

Move `useAiActionLog` before conditional returns or split dashboard content into an authenticated child component.

Acceptance criteria:

- ESLint React hooks rule passes.
- Dashboard renders correctly for unauthenticated and authenticated states.
- Tests cover both paths.

#### R0.5 Fix permissive role default

Empty roles must not imply approver permissions.

Acceptance criteria:

- Users with no roles can view only what policy allows.
- Approval action buttons hidden/disabled unless allowed.
- Backend remains source of truth.
- UI tests cover no-role, approver, admin, and unauthorized states.

---

### Phase 1 — Contract and API hardening

**Goal:** Make the UI and backend impossible to drift silently.

Tasks:

1. Create canonical API schemas for approvals, AI action log, dashboard, campaign, strategy, budget, content, and connector flows.
2. Generate or share TypeScript types from backend contracts.
3. Add response-envelope standards.
4. Add error-envelope standards.
5. Add idempotency semantics to all write APIs.
6. Add validation for request DTOs.
7. Add servlet tests for missing headers, invalid enums, malformed JSON, authz denial, not found, conflict, compliance violation, and success.
8. Add UI API-client contract tests using fixture JSON from backend contract tests.

Acceptance criteria:

- No frontend/backend DTO duplication without contract source.
- All API clients have runtime Zod validation or generated types.
- Contract tests fail if backend response shape changes.
- All write operations require idempotency.

---

### Phase 2 — UI/UX production hardening

**Goal:** Make the UI simple, complete, role-aware, and low cognitive load.

Required pages:

1. Login / workspace selection.
2. Home dashboard.
3. Approval queue.
4. Approval detail.
5. AI action log.
6. Strategy plan review.
7. Budget review.
8. Proposal/SOW review.
9. Content review.
10. Campaign launch/preflight.
11. Workflow/command operations.
12. Analytics/reporting.
13. Connector setup/status.
14. Privacy/consent/suppression admin.
15. Settings/security/audit.

Dashboard content should include:

- Growth health.
- Current goal.
- Active campaigns.
- AI actions.
- Needs approval.
- Results.
- Next best actions.
- Risk/compliance.
- Tracking health.
- Budget safety.
- Workflow/connector health.

Acceptance criteria:

- All critical backend capabilities have user-visible surfaces or are intentionally operator-only.
- No duplicate or noisy dashboard content.
- Every action has loading, error, empty, success, and permission states.
- All risky actions have review/confirmation/approval flows.
- Playwright E2E validates core journeys.
- Axe a11y tests pass.
- UI uses Ghatana design system/tokens consistently.

---

### Phase 3 — AI-native and Kernel orchestration hardening

**Goal:** Make AI implicit, pervasive, governed, and useful—not a visible gimmick.

Required work:

1. Define `DmAgentOrchestrationPort`.
2. Integrate Kernel `AgentOrchestrator` through a product-owned bridge.
3. Add prompt/model/tool/evidence metadata.
4. Add confidence/risk/actionability scoring.
5. Add AI action log entries for all agent outputs.
6. Add recommendation-to-command gateway.
7. Add human approval routing for risky agent recommendations.
8. Add agent evaluation suite.
9. Add deterministic fallback path for offline/dev.
10. Add model governance controls.

Acceptance criteria:

- Strategy, ad copy, landing page, email, report, and recommendations can be generated through governed agent flows.
- Every AI output has provenance, evidence, risk, confidence, and audit metadata.
- Human review is required only where policy/risk requires it.
- AI outputs never execute external side effects directly.
- All AI recommendations become commands only through governed gateway.

---

### Phase 4 — Execution runtime and connector hardening

**Goal:** Make side effects durable, idempotent, observable, reversible where possible, and safe.

Required work:

1. Command dispatcher.
2. Workflow worker.
3. Outbox dispatcher.
4. Inbox deduplication.
5. Dead-letter queue.
6. Retry/backoff policy.
7. Lease/claim mechanism.
8. Idempotency table.
9. Connector execution records.
10. Rollback/compensating action records.
11. Kill switch enforcement.
12. Google Ads connector contract validation.
13. Google Ads quota/rate-limit handling.
14. OAuth refresh and credential rotation.
15. Performance sync job.
16. Connector health UI.

Acceptance criteria:

- No external connector side effect runs outside a command/workflow.
- Duplicate commands do not duplicate external side effects.
- Failed commands land in DLQ with reason and recovery action.
- Kill switch prevents connector execution.
- Connector calls are traced, timed, audited, and linked to commands.
- OAuth secrets are encrypted/redacted.
- Tests use MockWebServer and Testcontainers.

---

### Phase 5 — Persistence and data integrity

**Goal:** Make the domain durable and multi-tenant safe.

Required work:

1. PostgreSQL schema for all critical aggregates.
2. Flyway migrations.
3. Repository adapters.
4. Repository contract tests.
5. Testcontainers integration suite.
6. Multi-tenant indexes and constraints.
7. Idempotency constraints.
8. Optimistic concurrency/version fields.
9. Audit linkage.
10. Event/outbox transaction boundaries.
11. PII-safe columns and encrypted fields.
12. Migration validation in CI.

Acceptance criteria:

- All production services can run without in-memory repositories.
- Every repository has integration tests against PostgreSQL.
- Multi-tenant isolation is enforced by query constraints and tests.
- Sensitive fields are encrypted/hashed/redacted as required.
- Migrations are repeatable in clean DB and upgrade DB.

---

### Phase 6 — Observability, SRE, and operations

**Goal:** Make O11y first-class rather than log-only.

Required work:

1. Product OTel tracer.
2. Product OTel meter.
3. Span naming conventions.
4. Metric naming conventions.
5. Log correlation.
6. RED metrics for APIs.
7. USE metrics for workers/connectors.
8. Business KPIs.
9. SLOs and alerts.
10. Dashboards.
11. Runbooks.
12. Synthetic checks.
13. Incident workflows.

Acceptance criteria:

- Every API request has trace/span/metrics/log correlation.
- Every workflow/command/connector action has trace context.
- Dashboards show health, failures, latency, approval aging, connector errors, DLQ, and budget pacing.
- Alerts exist for critical failures.
- Runbooks exist for connector outage, DLQ spike, auth failure, API latency, budget anomaly, and kill-switch activation.

---

### Phase 7 — Security, privacy, and compliance

**Goal:** Make privacy/security first-class objects.

Required work:

1. PII classification model enforcement.
2. Contact point hashing for suppression.
3. Encrypted credential storage.
4. Token rotation/revocation.
5. API key hashing and display-once semantics.
6. Consent proof storage.
7. Suppression enforcement before sends/exports.
8. Data subject export.
9. Data subject deletion.
10. Data correction/restriction.
11. Retention policy.
12. Privacy audit trail.
13. Security review gates.
14. Threat model.
15. Connector least-privilege scopes.

Acceptance criteria:

- No raw PII in generic blobs.
- Suppression lists use hashes or controlled contact-point references.
- Credentials and public API keys are never stored as plaintext.
- Privacy lifecycle tests pass.
- Security gates run in CI.
- Threat model is documented and linked to tests.

---

### Phase 8 — Test hardening and no-test-theater enforcement

**Goal:** Reach production-grade confidence, not just coverage numbers.

Required test categories:

1. Domain unit tests.
2. Application service tests.
3. API servlet tests.
4. Repository Testcontainers tests.
5. Contract tests.
6. UI component tests.
7. UI E2E tests.
8. Accessibility tests.
9. Visual/layout tests.
10. Workflow/command integration tests.
11. Connector contract tests.
12. OTel/metrics assertion tests.
13. Security/privacy tests.
14. Performance/load tests.
15. Chaos/failure tests.

Acceptance criteria:

- 100% changed-code line/branch coverage unless explicitly justified.
- No skipped/disabled tests.
- No trivial assertions.
- No production mocks/stubs.
- In-memory doubles limited to unit tests.
- Real DB tests cover repository behavior.
- UI E2E covers happy/error/empty/permission states.
- CI clearly separates fast unit, integration, browser integration, E2E, perf, and chaos tiers.

---

## 5. Release Gate Checklist

A DMOS MVP release candidate must satisfy all gates below.

### Build gates

- [ ] All Gradle modules pass `check`.
- [ ] UI passes `pnpm build`.
- [ ] UI passes `pnpm lint`.
- [ ] UI passes `pnpm test`.
- [ ] UI E2E passes.
- [ ] No production quality gate violations.
- [ ] No stale README/module list.

### Contract gates

- [ ] Approval UI/API contract aligned.
- [ ] AI action log UI/API contract aligned.
- [ ] Dashboard API contract aligned.
- [ ] Campaign/strategy/budget/proposal/content API contracts aligned.
- [ ] Error envelope standardized.
- [ ] Idempotency standardized.

### Runtime gates

- [ ] Postgres persistence wired.
- [ ] Flyway migrations run.
- [ ] Plugin startup wiring verified.
- [ ] Kernel bridge lifecycle verified.
- [ ] Command/worker execution verified.
- [ ] Outbox/DLQ verified.
- [ ] Google Ads connector verified against contract.
- [ ] Kill switch verified.

### O11y gates

- [ ] OTel traces.
- [ ] OTel metrics.
- [ ] Structured logs.
- [ ] Dashboards.
- [ ] Alerts.
- [ ] Runbooks.
- [ ] Trace/audit correlation.

### Security/privacy gates

- [ ] Tenant isolation.
- [ ] Role/persona authorization.
- [ ] PII-safe storage.
- [ ] Credential encryption.
- [ ] API key hashing.
- [ ] Consent enforcement.
- [ ] Suppression enforcement.
- [ ] Privacy lifecycle workflows.
- [ ] Threat model.

### UX gates

- [ ] Dashboard is low cognitive load.
- [ ] Approval flow works end to end.
- [ ] AI action log is explainable.
- [ ] Empty/loading/error states exist.
- [ ] Permission states are correct.
- [ ] A11y passes.
- [ ] No duplicate/noisy content.

---

## 6. Recommended Next Sprint

### Sprint theme

> Stabilize latest broad scaffold into a build-clean, contract-aligned, UI/API-working baseline.

### Sprint goals

1. Fix quality gate violation.
2. Align approval UI/API contract.
3. Add UI header/idempotency injection.
4. Fix React hook ordering.
5. Fix role-based UI access.
6. Add contract tests for approvals.
7. Add UI E2E for login → dashboard → approval queue → approval detail.
8. Update README and architecture status docs.
9. Verify `dm-application`, `dm-api`, `dm-domain`, `dm-connector-google-ads`, and UI builds.

### Sprint exit criteria

- `./gradlew :products:digital-marketing:dm-domain:check :products:digital-marketing:dm-application:check :products:digital-marketing:dm-api:check :products:digital-marketing:dm-connector-google-ads:check`
- `pnpm --filter @dmos/ui build`
- `pnpm --filter @dmos/ui lint`
- `pnpm --filter @dmos/ui test`
- `pnpm --filter @dmos/ui test:e2e`
- Approval dashboard and approval queue work against backend contract fixtures.
- No production quality gate violation remains.

---

## 7. Summary

The latest commit is a strong acceleration in breadth. DMOS now has the outlines of a real product: UI shell, approvals, AI transparency, content generation, command/workflow primitives, connector scaffolding, metrics, and persistence modules.

The next challenge is **not adding more slices**. The next challenge is hardening what exists:

- Make contracts exact.
- Make builds clean.
- Make UI flows actually work.
- Make persistence real.
- Make connector side effects safe.
- Make AI governed.
- Make O11y OpenTelemetry-first.
- Make privacy/security operational.
- Make tests prove end-to-end behavior.

Only after that should the product be considered a production-grade feature-complete MVP.
