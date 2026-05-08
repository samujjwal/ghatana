# DMOS Execution Plan

**Repository:** `samujjwal/ghatana`  
**Target head:** `1577a8559f3ae6a973452465a2cedf8d52c0a0ef`

---

## Phase 1 — P0 Production Blockers

### 1.1 Fix production repository wiring

- **What:** Register all durable repositories required by services, starting with `PostgresAiActionLogRepository`.
- **Where:** `products/digital-marketing/dm-api/src/main/java/com/ghatana/digitalmarketing/api/DmosApiServer.java`; `products/digital-marketing/dm-persistence`.
- **Why:** Production graph can fail because AI action services require a repository that is not registered in PostgreSQL mode.
- **Acceptance criteria:** Production server builds full component graph and AI action endpoints work with Postgres.
- **Tests:** Production graph boot IT with Testcontainers; AI action API read/write DB test.

### 1.2 Replace Google Ads in-memory production wiring

- **What:** Wire durable connector, credential, and campaign-link repositories into `DmCommandHandlerRegistry`; remove production fallback to `InMemoryDmGoogleAdsCampaignApiClient`.
- **Where:** `DmosApiServer#wireCommandHandlerRegistry`, `dm-persistence/googleads`, `dm-application/command`.
- **Why:** External campaign launch cannot rely on in-memory state or fake clients.
- **Acceptance criteria:** Production Google Ads command path uses durable repos and either real HTTP adapter or fails closed.
- **Tests:** Create/rollback command IT; connector disabled test; missing credentials test; duplicate idempotency test.

### 1.3 Wire governed AI strategy workflow or disable production strategy generation

- **What:** Provide real `GovernedAgentWorkflowService` in production composition or feature-disable strategy generation with clear 423/API/UI state.
- **Where:** `DmosApiServer#wireServices`, `dm-application/ai`, `dm-application/strategy`, `ui/src/pages/StrategyPage.tsx`.
- **Why:** AI-native strategy generation is not production-ready if it is only deterministic fallback.
- **Acceptance criteria:** Production behavior is explicit: governed AI with action log, or disabled.
- **Tests:** Production boot with AI configured; disabled mode API/UI test; AI failure fallback provenance test.

### 1.4 Implement real default-deny policy validation

- **What:** Replace skipped default-deny validation with concrete policy-pack checks.
- **Where:** `ProductionBootstrapValidator.java`, `dm-domain-packs`.
- **Why:** Governance-first product cannot claim default-deny while validation is no-op.
- **Acceptance criteria:** Missing/malformed default-deny rule fails bootstrap and CI.
- **Tests:** Pack validation tests for valid/missing/malformed packs.

### 1.5 Close tenant/workspace isolation gaps

- **What:** Add tenant-scoped constraints to campaigns and approval snapshots or enforce workspace ownership at every repository/service entry.
- **Where:** `dm-persistence/src/main/resources/db/migration`, `PostgresCampaignRepository`, `PostgresApprovalSnapshotRepository`, application services.
- **Why:** Workspace-only queries are insufficient unless ownership is proven before every access.
- **Acceptance criteria:** Cross-tenant guessed workspace/campaign/approval access fails.
- **Tests:** Cross-tenant API, service, and repository negative tests.

---

## Phase 2 — P1 Release Blockers

1. Normalize API error envelopes across all servlets.
2. Fix approval GET pending endpoints to avoid write idempotency requirements.
3. Harden `DmosHttpContextFactory` so production requires an identity provider.
4. Complete PII/token/privacy hardening, including DSAR and redaction.
5. Replace static production proof with runtime production composition proof.
6. Expand Google Ads failure/retry/rate-limit tests.
7. Add non-mocked Playwright backend+Postgres critical journey.

---

## Phase 3 — Kernel/Platform Enablers

1. Platform production composition verifier.
2. Generic connector runtime + credential vault abstraction.
3. Standard API error envelope middleware.
4. Policy pack validation harness.
5. PII-safe observability redaction policy.
6. Tenant isolation test harness.
7. Idempotent command/outbox runtime.

Each platform task must stay product-agnostic and must not contain campaign, ad, content, marketing, Google Ads, or DMOS-specific policy vocabulary.

---

## Phase 4 — Integration Hardening

- Add Google Ads retry/backoff/rate-limit handling.
- Normalize Google Ads errors and attach correlation IDs.
- Add credential revocation/refresh/token expiry flows.
- Add connector health checks.
- Add launch rollback recovery tests.
- Add partial external failure and duplicate command tests.

---

## Phase 5 — AI/Action Transparency Hardening

- Ensure every AI flow writes an AI action log.
- Persist prompt hash/input refs/output refs/status/failure/confidence.
- Link AI actions to approval requests where applicable.
- Surface deterministic fallback vs governed AI in UI.
- Add approval-required flow tests for strategy and budget.

---

## Phase 6 — UI/UX Completion

- Keep boundary reporting pages unavailable until backend data exists.
- Add shared feature-unavailable component.
- Add low-cognitive-load empty/loading/error states to every stable route.
- Add permission denied states.
- Lazy-load boundary routes.
- Add real backend E2E journey.

---

## Phase 7 — Test Completion

- Production-mode servlet tests.
- API contract tests.
- Testcontainers production composition tests.
- Tenant isolation negative tests.
- Google Ads failure matrix.
- AI provenance tests.
- Non-mocked Playwright E2E.
- Accessibility checks for stable routes.
- CI wiring for Docker-dependent tests.

---

## Phase 8 — Observability and Performance Hardening

- Replace no-op health indicator.
- Add DB/connector/kernel/workflow readiness checks.
- Redact sensitive telemetry.
- Add correlation ID propagation UI → API → command → connector.
- Add large approval queue and AI action log benchmarks.
- Add bundle-size budget and lazy-loading checks.

---

## Phase 9 — Documentation Cleanup

- Update README production readiness after fixes.
- Align deployment/local docs with actual env vars and monorepo paths.
- Document boundary vs stable routes.
- Document deterministic vs governed AI behavior.
- Document connector setup and fail-closed behavior.
- Document production release checklist.
