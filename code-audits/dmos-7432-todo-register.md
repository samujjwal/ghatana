# DMOS 7432 Audit — Complete TODO Register

**Repository:** `samujjwal/ghatana`  
**Target:** `products/digital-marketing` plus Kernel-platform enhancements  
**Source audit:** `dmos-7432-ultra-strict-product-correctness-audit.md`  
**Audited commit:** `7432d84601747ed3e095555c11a5f9471f0f8595`  

This register converts every actionable finding from the audit into implementation TODOs. It preserves all priorities present in the audit: **P0, P1, and P2**. No P3 items were identified in the audit.

---

## Priority Summary

| Priority | Meaning | Count | Release implication |
|---|---|---:|---|
| P0 | Must fix before production / release blocker | 17 | Blocks production and broad beta. |
| P1 | Must fix before release | 55 | Blocks release-quality readiness. |
| P2 | Hardening / maintainability / quality improvements | 28 | Should be completed before GA or shortly after core gates pass. |
| P3 | Future enhancement | 0 | No P3 items in this audit. |

---

# P0 TODOs — Release Blockers

## P0-001 — Add the missing campaign list backend endpoint

- **Area:** Campaigns, API/backend
- **Evidence:** `ui/src/api/campaigns.ts`, `DmosCampaignServlet.java`
- **TODO:** Add `GET /v1/workspaces/:workspaceId/campaigns` to `DmosCampaignServlet`.
- **Implementation details:**
  - Add servlet route registration.
  - Add handler method `handleListCampaigns`.
  - Build context with the same fail-closed rules as other protected reads.
  - Return a deterministic ordered list, preferably `createdAt DESC` or explicit sort.
  - Support pagination from the beginning; do not return unbounded lists.
- **Acceptance criteria:**
  - `GET /v1/workspaces/:workspaceId/campaigns` returns only campaigns in the requested workspace.
  - Missing/invalid tenant/principal/session fails according to the final shared context policy.
  - Empty workspaces return `[]`, not `404`.
  - UI campaign page loads without 404/405.
- **Tests required:** API servlet tests, API integration tests, browser test for campaign page load, tenant isolation tests.

## P0-002 — Add campaign repository list support

- **Area:** Campaigns, persistence
- **Evidence:** `PostgresCampaignRepository.java`, `V1__create_dmos_campaigns.sql`
- **TODO:** Add `CampaignRepository.listByWorkspace(...)` and implement it in PostgreSQL and in-memory adapters.
- **Implementation details:**
  - Add method to repository port.
  - Implement Postgres query using workspace filter, stable ordering, limit, and cursor/offset.
  - Add matching in-memory implementation for local/test only.
  - Ensure application service exposes list method without duplicating query logic in servlet.
- **Acceptance criteria:**
  - Campaign list is deterministic, paginated, and workspace-isolated.
  - Repository contract is implemented by every adapter.
- **Tests required:** repository unit tests, Postgres Testcontainers integration tests, service tests.

## P0-003 — Align Campaigns UI with the backend list endpoint

- **Area:** Campaigns, frontend
- **Evidence:** `CampaignsPage.tsx`, `useCampaigns.ts`, `api/campaigns.ts`
- **TODO:** Update the campaign UI to consume the new list endpoint and display loading, empty, success, and error states correctly.
- **Implementation details:**
  - Keep `listCampaigns()` only after backend support exists.
  - Add pagination/sort UI if backend supports paging.
  - Surface list failures with actionable error text and correlation ID when available.
- **Acceptance criteria:**
  - Creating a campaign causes the campaign to appear in the list without page refresh.
  - Launch/pause state transitions update the list accurately.
- **Tests required:** React component tests and Playwright campaign journey test.

## P0-004 — Replace broken `process.env` route gating in Vite UI

- **Area:** UI build/runtime, feature flags
- **Evidence:** `FeatureFlaggedRoute.tsx`
- **TODO:** Replace browser `process.env` usage with a Vite-safe and production-safe mechanism.
- **Implementation details:**
  - Use `import.meta.env.VITE_*` only for build-time flags, or preferably consume a backend `/features`/capabilities endpoint.
  - Do not access Node globals from browser code.
  - Fail closed when a flag cannot be resolved.
- **Acceptance criteria:**
  - Production Vite build succeeds without Node global polyfills.
  - Disabled flags hide or boundary features safely.
  - Enabled flags render expected pages.
- **Tests required:** Vite production build test, unit tests for enabled/disabled flags, browser route tests.

## P0-005 — Replace disabled-feature redirect-to-login behavior

- **Area:** UI/UX, feature gates
- **Evidence:** `FeatureFlaggedRoute.tsx`
- **TODO:** Render a proper “feature unavailable” boundary or redirect authenticated users to their workspace dashboard, not `/` → `/login`.
- **Implementation details:**
  - Create `FeatureUnavailablePage` or `UnsupportedSurfaceBoundary` for DMOS.
  - Include feature name, reason, and a safe navigation option.
  - Do not log the user out or imply auth failure.
- **Acceptance criteria:**
  - Authenticated user visiting a disabled feature sees a clear unavailable message.
  - User remains authenticated and can return to dashboard.
- **Tests required:** feature-flag-off browser test.

## P0-006 — Implement production authentication provider flow

- **Area:** Auth/session, frontend/backend integration
- **Evidence:** `LoginPage.tsx`, `AuthContext.tsx`, `App.tsx`
- **TODO:** Add a real production authentication flow.
- **Implementation details:**
  - Add provider login route/callback route, for example `/auth/callback`.
  - Bootstrap session from validated provider token/session.
  - Derive tenant, workspace, principal, roles, and permissions from backend-authenticated identity, not user-entered form fields.
  - Decide whether production uses bearer tokens, secure cookies, or another provider-backed session mechanism.
- **Acceptance criteria:**
  - Production users can authenticate without manual token/workspace/tenant/principal entry.
  - Manual login form is absent or blocked in production.
  - Invalid/expired provider token fails closed.
- **Tests required:** production-mode browser auth test, backend auth integration tests, expired-token test.

## P0-007 — Gate manual login to local/dev/test only

- **Area:** Auth/session, UI
- **Evidence:** `LoginPage.tsx`, `AuthContext.tsx`
- **TODO:** Make manual token/workspace/tenant/principal login a dev-only route.
- **Implementation details:**
  - Use an explicit dev/test flag, not general production detection only.
  - Hide the route and navigation in production builds.
  - Ensure direct access to `/login` in production redirects to provider login or shows provider login.
- **Acceptance criteria:**
  - Production build cannot show the manual credential form.
  - Dev mode still supports local test workflows.
- **Tests required:** prod-mode browser route test and dev-mode local login test.

## P0-008 — Remove production session refresh fake behavior

- **Area:** Auth/session
- **Evidence:** `AuthContext.tsx`
- **TODO:** Replace production `console.warn` + local expiry extension with real refresh/session validation.
- **Implementation details:**
  - Use provider refresh tokens/session introspection or backend session refresh endpoint.
  - Fail closed on refresh failure.
  - Do not silently extend local expiry in production.
- **Acceptance criteria:**
  - Expired session logs user out or refreshes through real provider.
  - No production code path extends session without provider proof.
- **Tests required:** session expiry, refresh success, refresh failure, revoked session tests.

## P0-009 — Generate and enforce a canonical API contract

- **Area:** API/docs/types/contracts
- **Evidence:** README/API contract/UI clients/servlets/enums drift
- **TODO:** Generate OpenAPI from actual backend routes/domain DTOs or otherwise make one canonical contract source.
- **Implementation details:**
  - Include campaign list route, campaign types, error envelope, required headers, auth behavior, idempotency semantics.
  - Generate TypeScript clients/types from the canonical contract.
  - Remove hand-maintained drift where possible.
- **Acceptance criteria:**
  - CI fails if UI client routes/types drift from backend contract.
  - Contract includes every servlet route and excludes non-existent routes.
- **Tests required:** contract generation CI, route parity tests, generated client compile test.

## P0-010 — Align campaign enum values across backend, UI, and docs

- **Area:** Domain/API/UI contracts
- **Evidence:** `CampaignType.java`, `CampaignsPage.tsx`, API docs/README drift
- **TODO:** Make campaign type values canonical and generated from one source.
- **Implementation details:**
  - Ensure `EMAIL`, `SOCIAL`, `PAID_SEARCH`, `PUSH`, `SMS`, `OMNICHANNEL` are documented if they are supported.
  - Remove stale `DISPLAY` references or add it to domain if actually required.
  - Validate API request body rejects unsupported types with a clear error.
- **Acceptance criteria:**
  - Backend enum, API docs, generated UI type, and dropdown options match exactly.
- **Tests required:** contract test and invalid enum API test.

## P0-011 — Standardize error envelope before clients depend on it

- **Area:** API contract, frontend error handling
- **Evidence:** servlets returning `{status,message}` while docs mention `{error,message}`
- **TODO:** Implement one canonical error envelope for all DMOS APIs.
- **Implementation details:**
  - Include `error`, `message`, `status`, `correlationId`, and optionally `details`.
  - Replace servlet-local `ErrorBody` records.
  - Update UI error parser to handle the canonical shape.
- **Acceptance criteria:**
  - Every API error response follows the same schema.
  - UI displays actionable message and correlation ID.
- **Tests required:** contract tests for 400/401/403/404/409/422/423/429/500.

## P0-012 — Add browser E2E for campaign list/create/launch/pause

- **Area:** Tests, campaign journey
- **Evidence:** campaign route mismatch would have been caught by E2E
- **TODO:** Add a real browser test against real or test-server backend for full campaign lifecycle.
- **Acceptance criteria:**
  - Test creates campaign, sees it in list, launches it, sees `LAUNCHED`, pauses it, sees `PAUSED`.
  - Test asserts API and UI state match.
- **Tests required:** Playwright + API/DB setup.

## P0-013 — Add production-mode auth E2E

- **Area:** Tests, auth/session
- **TODO:** Add production-build E2E proving real authentication works and manual login is not exposed.
- **Acceptance criteria:**
  - Production build has no manual token entry path.
  - Provider-authenticated user lands in workspace dashboard.
  - Invalid/expired auth fails closed.
- **Tests required:** Playwright production build test.

## P0-014 — Add feature-flag production build and runtime tests

- **Area:** Tests, feature flags
- **TODO:** Add tests that fail if Vite/browser feature flags use invalid globals or backend/UI flag truth diverges.
- **Acceptance criteria:**
  - Production build passes.
  - Disabled campaign/strategy/budget routes render unavailable boundary or are hidden.
  - Enabled routes render the page and can call compatible APIs.
- **Tests required:** Vite build, React unit, Playwright.

## P0-015 — Prevent spoofed identity from UI-provided headers

- **Area:** Security/auth boundary
- **Evidence:** `http-client.ts` sends tenant/principal/session/roles headers from client context; servlets use headers.
- **TODO:** Backend must derive principal, roles, permissions, tenant membership, and session validity from authenticated token/session, not trust client-supplied values.
- **Implementation details:**
  - Keep correlation ID/idempotency as client-supplied where safe.
  - Treat tenant/workspace from path/header as requested scope and validate it against server-side identity.
  - Reject user-supplied `X-Roles` unless trusted internal gateway signs/derives them.
- **Acceptance criteria:**
  - A client cannot gain approver/admin permissions by setting `X-Roles`.
  - Tenant/workspace mismatch is rejected.
- **Tests required:** spoofed role, spoofed principal, cross-tenant, missing token tests.

## P0-016 — Hide or hard-disable critical deterministic stubs in production

- **Area:** Application services, production mock/stub policy
- **Evidence:** README admits `dm-application` contains deterministic stubs.
- **TODO:** Inventory every deterministic/stub service in `dm-application` and make each one either real, removed, or production-disabled behind backend and UI gates.
- **Acceptance criteria:**
  - No critical flow depends on deterministic stub behavior in production.
  - CI has a stub/placeholder scan with approved exceptions only.
- **Tests required:** feature-off tests, production profile tests, static scan.

## P0-017 — Update release gate to block production until all P0 tests are green

- **Area:** CI/release governance
- **TODO:** Add a release gate that fails if any P0 test suite or contract check is not green.
- **Acceptance criteria:**
  - Production release pipeline checks auth, campaign E2E, contract parity, feature flags, security header tests, and production profile startup.
- **Tests required:** CI workflow verification.

---

# P1 TODOs — Must Fix Before Release

## P1-001 — Create a shared fail-closed HTTP context builder

- **Area:** API/security
- **Evidence:** campaign/strategy/budget/approval servlet-local context builders
- **TODO:** Introduce a shared `DmosHttpContextFactory` or ActiveJ middleware.
- **Implementation details:**
  - One implementation for tenant, workspace, principal, session, roles, permissions, correlation, idempotency.
  - Enforce mandatory headers consistently.
  - Remove duplicated servlet code.
- **Acceptance criteria:**
  - All protected servlets use the shared context builder.
  - Missing mandatory principal/session/correlation behavior is consistent.
- **Tests required:** header matrix across every servlet.

## P1-002 — Reject missing principal/session according to final API contract

- **Area:** Security/API correctness
- **TODO:** Remove `anonymous` and `null` fallbacks from protected routes unless explicitly public.
- **Acceptance criteria:**
  - Protected routes reject missing principal/session with canonical auth error.
  - Public intake remains explicitly unauthenticated and isolated.
- **Tests required:** missing header tests for all protected endpoints.

## P1-003 — Derive roles/permissions server-side

- **Area:** Authorization
- **TODO:** Replace trust of `X-Roles`/`X-Permissions` from browser with server-side security context or gateway-signed claims.
- **Acceptance criteria:**
  - User cannot self-grant approval/admin permissions.
  - Role checks use trusted identity claims.
- **Tests required:** forged role denial tests.

## P1-004 — Add production profile bootstrap validator

- **Area:** Runtime config, persistence, Kernel plugins
- **TODO:** Add a bootstrap validator that fails production startup if any required durable adapter/plugin is absent.
- **Must validate:**
  - PostgreSQL repositories for all production ports.
  - No in-memory repositories wired.
  - FeatureFlagPlugin, RiskManagementPlugin, AuditTrailPlugin, NotificationPlugin, ConsentPlugin, HumanApprovalPlugin present.
  - PII HMAC/encryption keys configured.
  - Google Ads disabled unless outbox/workflow configured.
- **Acceptance criteria:**
  - Production startup fails fast with actionable errors.
- **Tests required:** production profile startup tests and negative tests.

## P1-005 — Prove full repository parity

- **Area:** Persistence
- **TODO:** Inventory every application repository port and confirm each has a production PostgreSQL adapter or approved durable adapter.
- **Acceptance criteria:**
  - Every port has local/test and production implementations.
  - ArchUnit or custom check fails if a production port only has in-memory implementation.
- **Tests required:** adapter parity test.

## P1-006 — Add full Flyway migration validation

- **Area:** Database/migrations
- **TODO:** Run all DMOS migrations from empty DB and from realistic previous versions.
- **Acceptance criteria:**
  - Fresh migration succeeds.
  - Upgrade migration succeeds.
  - Roll-forward failure behavior is documented.
- **Tests required:** Testcontainers/Flyway tests.

## P1-007 — Add enum/check constraints for campaign schema

- **Area:** DB integrity
- **Evidence:** `dmos_campaigns.status` and `type` stored as text
- **TODO:** Add `CHECK` constraints or normalized enum tables for campaign `status` and `type`.
- **Acceptance criteria:**
  - Invalid campaign status/type cannot be inserted outside application code.
- **Tests required:** migration and negative insert tests.

## P1-008 — Preserve immutable campaign creation fields

- **Area:** DB persistence
- **Evidence:** `PostgresCampaignRepository` upsert can update `created_by`
- **TODO:** Ensure upsert does not overwrite immutable fields such as `created_by` and `created_at`.
- **Acceptance criteria:**
  - Updating campaign state cannot change original creator or creation time.
- **Tests required:** repository update test.

## P1-009 — Add tenant-level integrity for AI action log

- **Area:** DB/security
- **Evidence:** `dmos_ai_action_log` is workspace-scoped but lacks explicit tenant column
- **TODO:** Add `tenant_id` or enforce workspace foreign key to tenant-owned workspace table.
- **Acceptance criteria:**
  - Cross-tenant reads are impossible even if workspace IDs collide or are guessed.
- **Tests required:** tenant isolation DB/API tests.

## P1-010 — Prove PII HMAC migration key wiring

- **Area:** Privacy/migrations
- **Evidence:** `V6__migrate_suppression_to_contact_point_hash.sql`
- **TODO:** Document and implement how `dmos.pii_hmac_key` is set from secrets during Flyway migration.
- **Acceptance criteria:**
  - Migration fails without key.
  - Migration succeeds with key.
  - Raw `email` column is dropped.
- **Tests required:** Testcontainers migration failure/success tests.

## P1-011 — Move crypto/HMAC key management to Kernel platform

- **Area:** Kernel privacy/security
- **TODO:** Implement or use Kernel crypto/HMAC/key-rotation service instead of ad hoc product migration crypto policy.
- **Acceptance criteria:**
  - DMOS uses platform crypto APIs for hashing/encryption/key rotation.
  - Product migrations do not embed long-term crypto policy directly.
- **Tests required:** crypto provider integration and rotation tests.

## P1-012 — Align direct strategy/budget approval with approval queue governance

- **Area:** Approval workflow, UX/governance
- **Evidence:** `StrategyPage`, `BudgetPage` direct approve buttons
- **TODO:** Decide and implement one canonical approval model.
- **Options:**
  - Route all approvals through `ApprovalQueuePage`/`ApprovalDetailPage`, or
  - Keep inline approve only for trusted approvers and show role-specific UI.
- **Acceptance criteria:**
  - Non-approvers cannot see or execute approve actions.
  - Backend denies non-approvers regardless of UI.
  - Approval snapshot/audit trail is immutable.
- **Tests required:** UI role tests, API security tests, approval workflow E2E.

## P1-013 — Correct pending approval queue semantics

- **Area:** Approval workflow
- **Evidence:** pending endpoint appears subject-scoped, UI uses tenant/subject semantics
- **TODO:** Provide a reviewer/workspace pending approval queue endpoint or clarify subject-based queue design.
- **Acceptance criteria:**
  - Approver sees all pending approvals assigned to their role/workspace.
  - User does not miss approvals because UI passes tenant ID as subject ID.
- **Tests required:** pending queue API and UI tests.

## P1-014 — Add sensitive redaction tests for AI action log

- **Area:** AI transparency, privacy
- **TODO:** Prove details are redacted unless caller has sensitive-read permission.
- **Acceptance criteria:**
  - Caller without permission receives redacted details.
  - Caller with permission receives details.
  - UI clearly labels redacted entries.
- **Tests required:** API permission tests and UI tests.

## P1-015 — Expand dashboard to complete DMOS command center

- **Area:** UI/UX/product completeness
- **TODO:** Add capability/persona-aware dashboard cards for campaigns, strategy, budget, connectors, content, website audit, competitor research, lead scoring, and AI actions.
- **Acceptance criteria:**
  - Dashboard shows top-level product state without cognitive overload.
  - Cards hide/disable features not available to the user/org/tier.
- **Tests required:** dashboard rendering tests for personas/feature flags.

## P1-016 — Make all route/page availability backend-capability driven

- **Area:** UI/backend consistency
- **TODO:** Use backend capability/feature truth rather than hardcoded route flags.
- **Acceptance criteria:**
  - UI and backend agree on whether campaigns/strategy/budget/Google Ads/content are available.
  - Direct API access is blocked when backend feature is disabled.
- **Tests required:** feature on/off API + browser tests.

## P1-017 — Implement production `FeatureFlagPlugin` delegation in Kernel bridge

- **Area:** Kernel feature flags
- **Evidence:** adapter interface default false, impl does not override
- **TODO:** Inject and delegate to platform `FeatureFlagPlugin` in `DigitalMarketingKernelAdapterImpl`.
- **Acceptance criteria:**
  - Backend services can query dynamic flags at runtime.
  - Missing flag provider fails closed in production.
- **Tests required:** plugin true/false/missing-provider tests.

## P1-018 — Remove or fail-close default risk score behavior

- **Area:** Kernel risk
- **Evidence:** `DigitalMarketingKernelAdapter.evaluateRisk()` default returns `0.0`
- **TODO:** Remove default implementation or make it fail closed for critical operations.
- **Acceptance criteria:**
  - Missing risk plugin cannot produce “safe/low risk” by default in production.
  - Campaign launch blocks or requires manual review if risk cannot be evaluated.
- **Tests required:** missing risk plugin tests and launch-preflight tests.

## P1-019 — Remove or fail-close default notification no-op behavior

- **Area:** Kernel notifications
- **Evidence:** adapter interface default no-op `notifyUser`
- **TODO:** Make notification delivery explicit in production.
- **Acceptance criteria:**
  - Production does not silently succeed when notification provider is missing.
  - Non-critical notification failures are logged/metriced and routed through retry/DLQ policy.
- **Tests required:** missing provider and failure tests.

## P1-020 — Prove NotificationPlugin retry/DLQ behavior

- **Area:** Kernel notifications, operability
- **TODO:** Add tests and metrics for notification dispatch success/failure/retry/dead-letter.
- **Acceptance criteria:**
  - Failed dispatch is not fake-success.
  - Retry attempts and DLQ are observable.
- **Tests required:** plugin integration tests.

## P1-021 — Implement Kernel idempotency middleware/service

- **Area:** Kernel platform, API reliability
- **Evidence:** UI generates new idempotency key per request; approval idempotency optional
- **TODO:** Provide shared idempotency middleware for all mutating routes.
- **Acceptance criteria:**
  - Same logical user action retry reuses the same idempotency key.
  - Duplicate requests return cached response or safe conflict consistently.
- **Tests required:** duplicate submit and network retry tests.

## P1-022 — Use mutation-scoped idempotency keys in UI

- **Area:** Frontend data flow
- **TODO:** Generate idempotency key at mutation start and reuse across retry attempts for the same logical action.
- **Acceptance criteria:**
  - User double-click/retry cannot create duplicate campaign/strategy/budget/approval records.
- **Tests required:** UI duplicate submission tests and API idempotency tests.

## P1-023 — Add Google Ads workflow/outbox execution

- **Area:** Google Ads connector, durable side effects
- **Evidence:** README says connector adapter complete but command/workflow runtime wiring pending
- **TODO:** Execute Google Ads writes through durable outbox/workflow after approval.
- **Implementation details:**
  - Approved campaign launch creates connector command/outbox record.
  - Worker executes external call.
  - Retry/backoff/DLQ for failures.
  - Audit every external write.
  - Kill switch prevents execution.
  - Rollback/compensation story is defined.
- **Acceptance criteria:**
  - Approved launch creates auditable external command.
  - Failed external call retries and eventually DLQs.
  - Duplicate approval does not double-spend/create duplicate ads.
- **Tests required:** E2E with fake Google Ads server.

## P1-024 — Add kill switch enforcement for connector writes

- **Area:** Governance/Google Ads
- **TODO:** Ensure Google Ads and all external-write workflows check DMOS kill switch before execution and before retry.
- **Acceptance criteria:**
  - Kill switch stops queued and new connector writes.
  - Audit event records blocked action.
- **Tests required:** kill-switch connector tests.

## P1-025 — Add rollback/compensating action workflow for external writes

- **Area:** Governance/Google Ads
- **TODO:** Define and implement compensating actions for approved external changes where supported.
- **Acceptance criteria:**
  - Failed/rolled-back campaign launch has visible status and audit trail.
- **Tests required:** rollback E2E with fake connector.

## P1-026 — Wire OpenTelemetry spans across critical flows

- **Area:** Observability
- **Evidence:** README marks OTel partial
- **TODO:** Add OTel spans for UI request, servlet handler, service, repository, Kernel plugin, and connector operations.
- **Acceptance criteria:**
  - Trace ID is propagated from UI/API through backend service and DB/plugin boundaries.
  - Error UI can show/support correlation ID.
- **Tests required:** telemetry integration tests.

## P1-027 — Migrate all servlets to rate limiter metrics overload

- **Area:** Observability/API
- **Evidence:** `DmosApiRateLimiter` has deprecated noop overload
- **TODO:** Update every servlet to call `DmosApiRateLimiter.wrap(delegate, metrics, servletId)`.
- **Acceptance criteria:**
  - Metrics include servlet, method, status, latency for all endpoints.
  - Deprecated overload has no production usages.
- **Tests required:** metrics label tests and static scan.

## P1-028 — Add structured audit events for all critical actions

- **Area:** Audit/governance
- **TODO:** Ensure create/launch/pause/generate/submit/approve/reject/external-write/PII actions emit audit events.
- **Acceptance criteria:**
  - Every critical action has an audit entry with actor, tenant, workspace, entity, action, correlation ID.
- **Tests required:** audit assertions in integration tests.

## P1-029 — Add AI/model provenance for strategy and budget generation

- **Area:** AI governance
- **TODO:** Record model version, input evidence, assumptions, confidence, policy checks, and AI action log entry for strategy/budget outputs.
- **Acceptance criteria:**
  - Generated output can be traced to inputs and model/policy checks.
- **Tests required:** strategy/budget API tests with AI action log assertions.

## P1-030 — Surface mutation errors in UI

- **Area:** UX/reliability
- **Evidence:** campaign launch/pause/create mutations return errors but page does not surface them clearly
- **TODO:** Add toast or inline error states for all mutations.
- **Acceptance criteria:**
  - API failure shows actionable message and correlation ID.
  - User can retry where safe.
- **Tests required:** component/browser tests with failing API.

## P1-031 — Implement per-row action pending states

- **Area:** UX/concurrency
- **TODO:** Disable only the row/action currently mutating, not all launch/pause buttons globally.
- **Acceptance criteria:**
  - Launching one campaign does not block unrelated actions unless backend constraints require it.
- **Tests required:** UI concurrency tests.

## P1-032 — Add cache invalidation for related approval and AI action state

- **Area:** Frontend state
- **TODO:** Mutations that create approvals or AI actions must invalidate approval queue and AI action log queries.
- **Acceptance criteria:**
  - Submit strategy/budget updates approval queue without manual refresh.
  - Launch/generate actions appear in AI action log after mutation.
- **Tests required:** React Query integration tests.

## P1-033 — Add real strategy lifecycle E2E

- **Area:** Tests/strategy
- **TODO:** Test strategy generate → display → submit for approval → approve/deny path.
- **Acceptance criteria:**
  - UI, API, DB, approval snapshot, audit, AI action log all agree.
- **Tests required:** Playwright + API + DB integration.

## P1-034 — Add real budget lifecycle E2E

- **Area:** Tests/budget
- **TODO:** Test budget recommendation generate → display → submit → approve/deny.
- **Acceptance criteria:**
  - Budget values and channel allocations persist and render correctly.
  - Approval and audit behavior is correct.
- **Tests required:** Playwright + API + DB integration.

## P1-035 — Add approval role and permission matrix tests

- **Area:** Authorization/testing
- **TODO:** Test reviewer, non-reviewer, admin, missing-role, forged-role, cross-tenant scenarios.
- **Acceptance criteria:**
  - UI hides disallowed actions.
  - Backend denies disallowed actions even if called directly.
- **Tests required:** API security tests and UI tests.

## P1-036 — Inventory content generation backend-only surfaces

- **Area:** Completeness/product surface
- **TODO:** Inventory ad copy, landing page, email follow-up, content validation, website audit, competitor research, lead scoring, intake, and public intake services/routes.
- **Acceptance criteria:**
  - Each capability is classified as UI-exposed, backend-only, feature-gated, or missing.
  - No backend-only critical feature is advertised as complete in UI/docs.
- **Tests required:** route inventory/contract tests.

## P1-037 — Add UI or feature gates for backend-only marketed capabilities

- **Area:** UI/completeness
- **TODO:** For every advertised DMOS capability, either add UI/access path or hide/mark feature unavailable based on capability flags.
- **Acceptance criteria:**
  - Product docs, nav, dashboard, and backend capabilities agree.
- **Tests required:** navigation/capability tests.

## P1-038 — Add public intake abuse controls

- **Area:** Security/public endpoints
- **TODO:** Ensure public intake has rate limiting, spam/bot controls, validation, PII protection, and tenant/workspace binding.
- **Acceptance criteria:**
  - Public endpoint cannot be used to flood tenant data or bypass suppression/consent rules.
- **Tests required:** public intake security/load tests.

## P1-039 — Add data retention and DSAR end-to-end proof

- **Area:** Privacy/compliance
- **TODO:** Prove DSAR/right-to-be-forgotten and retention flows across contact, lead, suppression, AI action, content, and audit boundaries.
- **Acceptance criteria:**
  - PII is removed/anonymized according to policy without breaking required audit records.
- **Tests required:** DSAR E2E tests.

## P1-040 — Add production startup test that default-deny policy pack is loaded

- **Area:** Governance/domain packs
- **Evidence:** `dm-domain-packs` default-deny policy requirement
- **TODO:** Verify `DM-BP-999` default-deny boundary policy is active in production startup.
- **Acceptance criteria:**
  - Unknown/unsupported operations deny by default.
- **Tests required:** production profile policy tests.

## P1-041 — Add ArchUnit/lint rule against product logic in Kernel/platform plugins

- **Area:** Architecture boundaries
- **TODO:** Enforce no DMOS-specific logic in Kernel/platform plugin production modules and only public Kernel interfaces consumed by product code.
- **Acceptance criteria:**
  - Dependency direction violations fail CI.
- **Tests required:** ArchUnit/static checks.

## P1-042 — Add static scan for test-only utilities in production code

- **Area:** Production mock/stub policy
- **TODO:** Scan production source for test utilities, mocks, fake services, hardcoded demo paths, and in-memory production wiring.
- **Acceptance criteria:**
  - CI fails on unapproved mock/stub/fake usage in production paths.
- **Tests required:** static scan CI.

## P1-043 — Add exact changed-flow API integration suite

- **Area:** Testing
- **TODO:** Add API integration tests for campaigns, strategy, budget, approvals, AI actions, feature flags, auth, and persistence.
- **Acceptance criteria:**
  - Real handlers and services are exercised; not just mocks.
- **Tests required:** Gradle integration suite.

## P1-044 — Add exact changed-flow browser E2E suite

- **Area:** Testing
- **TODO:** Add Playwright suite covering auth, dashboard, campaigns, strategy, budget, approval queue/detail, and AI action log.
- **Acceptance criteria:**
  - CI blocks if UI/API contract breaks again.
- **Tests required:** Playwright in CI.

## P1-045 — Add DB state assertions to integration tests

- **Area:** Testing/data integrity
- **TODO:** For every DB-writing flow, assert final DB state, not only API response.
- **Acceptance criteria:**
  - Create/update/approve flows verify persisted state, immutable fields, audit rows, and tenant isolation.
- **Tests required:** Testcontainers integration tests.

## P1-046 — Add feature-flag off/on backend tests

- **Area:** Feature flags/testing
- **TODO:** Verify backend direct API access is blocked when a feature is disabled, not only hidden in UI.
- **Acceptance criteria:**
  - Disabled campaign/strategy/budget/Google Ads/content features return canonical disabled error.
- **Tests required:** API tests.

## P1-047 — Add feature-flag off/on UI tests

- **Area:** UI/testing
- **TODO:** Verify navigation, route access, and action visibility for enabled and disabled features.
- **Acceptance criteria:**
  - Disabled features do not appear as broken or login redirects.
- **Tests required:** Playwright + component tests.

## P1-048 — Add OpenAPI/client generation CI

- **Area:** Contracts/CI
- **TODO:** Run OpenAPI generation and TypeScript client generation in CI.
- **Acceptance criteria:**
  - Generated types compile.
  - Handwritten client drift is removed or detected.
- **Tests required:** contract CI.

## P1-049 — Add production persistence wiring proof

- **Area:** Deployment/runtime
- **TODO:** Add a production-like boot test with PostgreSQL, Kernel plugins, and no in-memory adapters.
- **Acceptance criteria:**
  - App starts with production profile only when durable dependencies are configured.
- **Tests required:** production profile integration test.

## P1-050 — Add migration audit/deployment metric

- **Area:** Observability/DB operations
- **TODO:** Emit or record migration status including PII migration success/failure.
- **Acceptance criteria:**
  - Operators can verify migration version and PII migration status after deployment.
- **Tests required:** deployment smoke test.

## P1-051 — Add frontend correlation ID display/support

- **Area:** Observability/UX
- **TODO:** Surface correlation ID from errors in UI so support can diagnose failures.
- **Acceptance criteria:**
  - User-facing error includes support-safe correlation ID.
- **Tests required:** failing API UI tests.

## P1-052 — Add distributed-safe rate limiting or document single-node limits

- **Area:** Scalability/security
- **Evidence:** in-memory `RateLimitFilter` appears single-node
- **TODO:** Use distributed rate limiter for multi-replica deployments or document single-node limitation and block multi-replica prod without it.
- **Acceptance criteria:**
  - Rate limits cannot be bypassed by load balancing across replicas.
- **Tests required:** HA/load test.

## P1-053 — Add connector chaos/retry tests

- **Area:** External integration reliability
- **TODO:** Test Google Ads timeouts, 429s, 5xx, auth failures, invalid payloads, duplicate requests, rollback path.
- **Acceptance criteria:**
  - Each external failure class maps to safe retry/blocked/DLQ state.
- **Tests required:** fake connector chaos suite.

## P1-054 — Replace stale root audit doc or move DMOS audit to product audit path

- **Area:** Documentation/governance
- **Evidence:** `docs/audits/end-to-end-product-correctness-audit.md` is Data Cloud + AEP, not DMOS
- **TODO:** Decide canonical DMOS audit path and update docs.
- **Recommended path:** `products/digital-marketing/docs/audits/end-to-end-product-correctness-audit.md`
- **Acceptance criteria:**
  - Audit doc title/product/commit match DMOS.
  - Old docs are not mistaken as current DMOS audit.
- **Tests required:** docs CI/link check.

## P1-055 — Freeze feature expansion until P0/P1 gates are closed

- **Area:** Delivery governance
- **TODO:** Create a hardening milestone that blocks new feature work until P0 and core P1 tasks are complete.
- **Acceptance criteria:**
  - Milestone board has all release blockers assigned.
  - CI release gate is defined and required.
- **Tests required:** process/release checklist verification.

---

# P2 TODOs — Hardening and Quality Improvements

## P2-001 — Lazy-load route pages

- **Area:** Frontend performance
- **Evidence:** `App.tsx` imports new pages directly
- **TODO:** Convert campaign, strategy, budget, approval, dashboard, and AI action pages to route-level lazy imports.
- **Acceptance criteria:** initial bundle size decreases or stays within budget.
- **Tests required:** bundle budget check.

## P2-002 — Add bundle size budget CI

- **Area:** Frontend performance
- **TODO:** Add bundle analysis and fail on unexpected growth.
- **Acceptance criteria:** CI reports initial and route chunk sizes.
- **Tests required:** build/bundle CI.

## P2-003 — Update README UI route inventory

- **Area:** Documentation
- **TODO:** Add campaigns, strategy, and budget routes to README route table or clarify feature-gated status.
- **Acceptance criteria:** README route inventory matches `App.tsx`.
- **Tests required:** route-doc parity script.

## P2-004 — Update API docs to reflect actual campaign types and routes

- **Area:** Documentation/contracts
- **TODO:** Remove stale route/type docs and link to generated OpenAPI.
- **Acceptance criteria:** docs no longer list unsupported routes/types.
- **Tests required:** docs contract check.

## P2-005 — Add keyboard and accessibility tests for new pages

- **Area:** Accessibility
- **TODO:** Run axe and keyboard navigation tests for login, dashboard, campaigns, strategy, budget, approvals, and AI actions.
- **Acceptance criteria:** no critical axe violations; keyboard can complete primary flows.
- **Tests required:** Playwright a11y.

## P2-006 — Improve feature-unavailable UX copy

- **Area:** UX
- **TODO:** Standardize copy for unavailable/locked features.
- **Acceptance criteria:** user sees why feature is unavailable and what action to take.
- **Tests required:** component snapshot/DOM tests.

## P2-007 — Add design-system consistency pass

- **Area:** UI consistency
- **TODO:** Ensure all DMOS UI pages use the same layout primitives, tokens, typography, button styles, table styles, alert states, and form components.
- **Acceptance criteria:** no raw inconsistent controls where design-system components exist.
- **Tests required:** visual regression/lint.

## P2-008 — Add responsive layout tests

- **Area:** UI responsiveness
- **TODO:** Test core routes at mobile, tablet, and desktop widths.
- **Acceptance criteria:** no clipped forms/tables/actions.
- **Tests required:** Playwright viewport tests.

## P2-009 — Add loading/empty/error/success state tests for every page

- **Area:** UI state completeness
- **TODO:** Cover each state on dashboard, approvals, AI actions, campaigns, strategy, and budget.
- **Acceptance criteria:** every page has real state coverage.
- **Tests required:** component tests.

## P2-010 — Add destructive/sensitive action confirmation patterns

- **Area:** UX/governance
- **TODO:** Standardize confirmations for approve/reject, launch, pause, connector execution, DSAR/deletion, and rollback where appropriate.
- **Acceptance criteria:** critical irreversible or external-write actions require confirmation/review.
- **Tests required:** browser tests.

## P2-011 — Add user/persona/tier-aware backend content filtering

- **Area:** UI simplification/security
- **TODO:** Filter available content/actions on backend by user/org/tier so UI does not duplicate complex `canAccess` checks everywhere.
- **Acceptance criteria:** UI receives a capability/action model already filtered for the principal.
- **Tests required:** persona/tier API tests.

## P2-012 — Add route/nav generation from backend capabilities

- **Area:** DRY/source-of-truth
- **TODO:** Generate navigation items from capability metadata rather than maintaining route docs/UI nav manually.
- **Acceptance criteria:** disabled/unlicensed/unavailable features do not appear in nav.
- **Tests required:** nav capability tests.

## P2-013 — Add secret scanning for DMOS product

- **Area:** Security CI
- **TODO:** Add CI scan for secrets in source, logs, configs, frontend bundles, and test fixtures.
- **Acceptance criteria:** CI fails on detected secrets.
- **Tests required:** secret scan CI.

## P2-014 — Define CSRF/token storage posture

- **Area:** Browser security
- **TODO:** Decide whether production auth uses secure cookies, bearer token memory storage, CSRF tokens, or provider session.
- **Acceptance criteria:** threat model and tests exist for chosen approach.
- **Tests required:** CSRF/security tests.

## P2-015 — Add XSS/output encoding review for AI/content surfaces

- **Area:** Security
- **TODO:** Ensure generated content, AI action details, landing page/ad copy previews, and audit details are safely rendered.
- **Acceptance criteria:** no raw HTML/script injection in rendered UI.
- **Tests required:** XSS payload tests.

## P2-016 — Add SSRF protection for website audit/competitor research

- **Area:** Security/integrations
- **TODO:** If DMOS fetches URLs for audits/research, add allow/deny network policy, private IP blocking, timeouts, and safe redirects.
- **Acceptance criteria:** internal metadata/private network URLs are blocked.
- **Tests required:** SSRF tests.

## P2-017 — Add pagination and deterministic sorting for all list endpoints

- **Area:** API performance/data consistency
- **TODO:** Audit and update list endpoints beyond campaigns: approvals, AI actions, content, leads, research, audits.
- **Acceptance criteria:** no unbounded list endpoint in production API.
- **Tests required:** API pagination tests.

## P2-018 — Add N+1/query performance checks

- **Area:** DB performance
- **TODO:** Inspect service/repository queries for N+1 patterns and missing indexes.
- **Acceptance criteria:** realistic workspace data loads within defined latency budgets.
- **Tests required:** load/performance tests.

## P2-019 — Add latency budgets and SLOs for critical DMOS flows

- **Area:** Operability
- **TODO:** Define SLOs for login, dashboard load, campaign list/create/launch, approval decision, strategy/budget generation, and connector execution.
- **Acceptance criteria:** dashboards/alerts exist for SLOs.
- **Tests required:** smoke/perf tests.

## P2-020 — Add audit runbook for production incidents

- **Area:** Operations/docs
- **TODO:** Document how to diagnose campaign launch failures, approval failures, Google Ads failures, PII migration failures, and feature-flag issues.
- **Acceptance criteria:** runbook includes logs/metrics/traces/DB checks and rollback steps.
- **Tests required:** runbook drill.

## P2-021 — Add migration rollback/forward-only policy docs

- **Area:** DB operations
- **TODO:** Document migration recovery, especially irreversible PII email drop.
- **Acceptance criteria:** operators know backup/verification requirements before migration.
- **Tests required:** migration rehearsal.

## P2-022 — Add schema/model documentation

- **Area:** Documentation/data model
- **TODO:** Document tables, relationships, retention rules, PII classification, and tenant/workspace boundaries.
- **Acceptance criteria:** schema docs match migrations.
- **Tests required:** schema-doc parity check.

## P2-023 — Add domain capability map docs

- **Area:** Product docs
- **TODO:** Document which DMOS capabilities are GA, beta, internal, hidden, or backend-only.
- **Acceptance criteria:** docs and runtime flags agree.
- **Tests required:** docs/capability parity.

## P2-024 — Add TODO/FIXME/stub exception registry

- **Area:** Code hygiene
- **TODO:** Create an allowlisted registry for any remaining TODO/stub/demo/fake/in-memory usages.
- **Acceptance criteria:** every exception has owner, expiration, feature flag, and test coverage.
- **Tests required:** static scan.

## P2-025 — Add mutation rollback/optimistic update policy

- **Area:** Frontend reliability
- **TODO:** Decide when to use optimistic updates and how to roll back on failure.
- **Acceptance criteria:** no UI shows fake success for failed backend operations.
- **Tests required:** failed mutation tests.

## P2-026 — Add central typed API error parser

- **Area:** Frontend/API consistency
- **TODO:** Replace raw `throw new Error(status:text)` with typed `ApiError` that preserves status, code, message, correlation ID, and details.
- **Acceptance criteria:** UI can branch on `403`, `409`, `422`, `423`, `429`, etc.
- **Tests required:** API client tests.

## P2-027 — Add global retry/backoff policy

- **Area:** Frontend/API reliability
- **TODO:** Define retry behavior by status/action/idempotency safety.
- **Acceptance criteria:** non-idempotent writes do not retry unsafely; 429 honors `Retry-After`.
- **Tests required:** retry policy tests.

## P2-028 — Add final release checklist automation

- **Area:** Release governance
- **TODO:** Convert audit final checklist into CI/release checklist automation.
- **Acceptance criteria:** release cannot proceed until correctness, completeness, security, DB, O11y, performance, tests, docs gates pass or have approved waivers.
- **Tests required:** pipeline dry run.

---

# Cross-Cutting Execution Order

## Phase 0 — Stop release risk immediately

1. Fix production auth path and hide manual login.
2. Fix feature flag mechanism and disabled-feature UX.
3. Add campaign list backend/repository/API/UI support.
4. Generate canonical API contract and remove route/type drift.
5. Add P0 browser/API tests so these regressions cannot recur.

## Phase 1 — Security, persistence, and Kernel hardening

1. Centralize fail-closed HTTP context/auth handling.
2. Derive identity/roles server-side.
3. Add production profile validator and repository parity checks.
4. Wire Kernel FeatureFlagPlugin, fail-closed risk, notification reliability, idempotency, and crypto services.
5. Prove PII migration and production persistence startup.

## Phase 2 — Complete core product journeys

1. Campaign full lifecycle.
2. Strategy full lifecycle.
3. Budget full lifecycle.
4. Approval workflow role/queue/detail lifecycle.
5. AI action log redaction and transparency lifecycle.
6. Google Ads approved external-write lifecycle.

## Phase 3 — UX, observability, performance, and docs hardening

1. Dashboard completion.
2. Capability-driven navigation.
3. Full OTel/metrics/audit coverage.
4. Load/performance/security/a11y test suites.
5. Documentation and runbooks aligned to code.

---

# Definition of Done for This TODO Register

The TODO register is complete only when:

- All P0 items are closed with tests and release gate proof.
- All P1 items are closed or explicitly waived with owner/date/risk acceptance.
- P2 items are scheduled with owners and tracked.
- The audit document is updated to reflect the fixed commit.
- CI proves contract, security, persistence, UI E2E, DB migration, OTel, and release checklist gates.
