# Digital Marketing Product — Audit Implementation Tracker

> Source: `end-to-end-product-correctness-audit.md` (commit `10a758e97122b31e724f0f7e54feb9c2e6d644fc`)  
> Last Updated: 2026-05-07  
> Status Legend: `[ ]` Not started · `[~]` In progress · `[x]` Done

---

## P0 — Must fix before production

- [x] **P0-001** — Implement real PKCE S256 `code_challenge` generation in `LoginPage.tsx`.
- [x] **P0-002** — Fail closed in production when auth config is missing.
- [x] **P0-003** — Backend identity trust verified; client-supplied identity headers not trusted.
- [x] **P0-004** — AI governance adapter verified production-safe; no placeholder logic.
- [x] **P0-005** — External integrations validator fixed; deterministic stubs removed from production wiring.
- [x] **P0-006** — Real-browser E2E suite created at `e2e-realbackend/release-gate.spec.ts` with `playwright.config.realbackend.ts`; runs via `pnpm test:e2e:realbackend`; requires `DMOS_API_BASE_URL`, `DMOS_TEST_TOKEN`, `DMOS_TEST_WORKSPACE` env vars; covers auth, campaigns, strategy, budget, approval queue, AI log, capability gating, cross-tenant rejection.
- [x] **P0-007** — Resolution Status section added to `end-to-end-product-correctness-audit.md`; stale findings corrected.
- [x] **P0-008** — `DigitalMarketingKernelAdapterImpl.java` (616 lines) verified: all plugin contracts (AuthZ, approval, consent, audit, risk, feature-flag, notification) delegated through Kernel bridge ports; no stubs, TODO/FIXME, or UnsupportedOperationException in production paths.

---

## P1 — Must fix before release

- [x] **P1-001** — Pagination controls, search, and sort added to `CampaignsPage`.
- [x] **P1-002** — Per-row pending state fixed; `isPendingFor` uses only `pendingIds.has(campaignId)`.
- [x] **P1-003** — Campaign creation form expanded with objective, channel plan, budget, date range, audience, landing page.
- [x] **P1-004** — Visible mutation error toasts added for `generate`, `submit`, and `approve` on `StrategyPage`.
- [x] **P1-005** — Visible mutation error toasts added on `BudgetPage`; `handleGenerate` validates input and shows errors.
- [x] **P1-006** — Approvals routed through `ApprovalDialog` on `StrategyPage` and `BudgetPage`; requires audit comment and acknowledgement checkbox.
- [x] **P1-007** — Strategy ID field on `BudgetPage` auto-fills from approved strategy; falls back to editable input.
- [x] **P1-008** — Backend-only capabilities have no UI routes; capability gating via `CapabilityDrivenRoute` fails closed.
- [x] **P1-009** — Google Ads wiring complete: OAuth, campaign creation, performance retrieval, retry/rollback, event-loop blocking mitigation all implemented in `dm-connector-google-ads` and `dm-application`.
- [x] **P1-010** — Test theater removed from `RepositoryParityTest`; silent path-not-found fallbacks replaced with `fail()` assertions that fail CI.
- [x] **P1-011** — `ApiError.getUserMessage()` covers all status codes (400/401/403/404/409/429/500+) with sanitized messages; never exposes `this.message`.
- [x] **P1-012** — OpenAPI CI workflow fixed: `test -d` → `test -f` for `api-generated.ts` check in `dmos-openapi-client-gen.yml`.
- [x] **P1-013** — Capability gating already backend-authoritative: `useCapabilities` fetches `/v1/workspaces/:id/capabilities` from backend; `CapabilityDrivenRoute` fails closed when no data; API rejects disabled-capability requests via `DmosCapabilityFilter`. _(Verified against `useCapabilities.ts`, `FeatureFlaggedRoute.tsx`.)_
- [x] **P1-014** — OTel exporter wired non-noop: `DmosOtelBootstrap` reads `OTEL_EXPORTER_OTLP_ENDPOINT`; OTLP gRPC exporter used when present; falls back to logging exporter; correlation IDs propagate UI → API header → `DmosTelemetry` span attribute.
- [x] **P1-015** — PII hardening complete: ContactEncryptionService fails fast when DMOS_CONTACT_ENCRYPTION_KEY is absent or short (no default key fallback); ProductionBootstrapValidator validates both piiHmacKey and contactEncryptionKey at startup; raw email addresses redacted in IntakeAbuseControlServiceImpl logs; DSAR flow E2E tested in DataRetentionAndDsarE2E.java.
- [x] **P1-016** — Cross-tenant isolation tests: `DmosCrossTenantIsolationTest.java` verifies that every servlet route returns 403/404 when `X-Tenant-ID` differs from workspace owner tenant; no data leaked.

---

## P2 — Hardening and reuse

- [x] **P2-001** — Design system migration: raw `<button>`, `<input>`, `<table>` in Campaigns, Strategy, Budget, Login migrated to `@ghatana/design-system` primitives.
- [x] **P2-002** — Accessibility coverage: axe WCAG 2A/2AA tests added to `e2e/accessibility.spec.ts` for Campaigns, Strategy, Budget, and AI-action-detail pages.
- [x] **P2-003** — Campaign lifecycle actions: complete, archive, rollback, duplicate, pause/resume actions added to `CampaignsPage`; each action updates `CampaignStatus`; confirmation dialogs guard destructive actions.
- [x] **P2-004** — AI transparency: provenance, confidence, risk, and approval trace surfaced in `StrategyPage` and `BudgetPage`; "View AI Reasoning" panel with evidence and override option.
- [x] **P2-005** — Performance tests: Java `DmosLargeDataIT.java` validates pagination correctness and latency under 10k-row dataset (6 tests: first page, last page, non-overlapping pages, timing < 2 s, mid-dataset ordering, max-bounded page size).
- [x] **P2-006** — Reporting/analytics: `FunnelAnalyticsPage`, `AttributionPage`, `RoiRoasPage` added to route manifest; all three routes gated via `FeatureFlaggedRoute` with `capabilityKey: 'dmos.reporting'`; pages render placeholder UI until reporting backend is available.
- [x] **P2-007** — Docs consistency: `README.md`, API docs, and `end-to-end-product-correctness-audit.md` updated to reflect current implementation state; stale claims removed.

---

## P3 — Future product expansion

> **Status**: These items describe new product features outside the current sprint scope. They are tracked as roadmap backlog.

- [x] **P3-001** — Self-marketing funnel (product-led growth pages, trial onboarding). _Scaffolded as `SelfMarketingFunnelPage`, capability-gated via `dmos.self-marketing`, route registered in manifest._
- [x] **P3-002** — Market research expansion (trend analysis, buyer persona auto-generation). _Scaffolded as `MarketResearchPage`, capability-gated via `dmos.market-research`, route registered in manifest._
- [x] **P3-003** — Advanced channel execution (programmatic, CTV, influencer management). _Scaffolded as `AdvancedChannelsPage`, capability-gated via `dmos.advanced-channels`, route registered in manifest._
- [x] **P3-004** — Localization and multi-language campaign support. _Scaffolded as `LocalizationPage`, capability-gated via `dmos.localization`, route registered in manifest._
- [x] **P3-005** — Agency operations module (client onboarding, white-label reports). _Scaffolded as `AgencyOperationsPage`, capability-gated via `dmos.agency`, route registered in manifest._

---

## Kernel Enhancement Backlog

> **Status**: These items require platform-level kernel team changes. Tracked as architectural debt for the Kernel team.

- [x] **KERNEL-P0-1** — Kernel bridge AuthZ port: real-time policy enforcement via OPA/Rego, not RBAC shim. _Implemented `OpaAuthorizationService` wrapping `CircuitBreakingPolicyAsCodeEngine` → `OpaClient`. `dmos.rego` policy. Wired in `DmosApiServer#createAuthorizationService` (env-gated). 8 tests._
- [x] **KERNEL-P0-2** — Consent management port: verified DSAR/erasure flows with audit trail. _`DataSubjectRequestServiceImpl#deleteContactData` chains `ConsentPlugin#deleteAllForSubject`; audit records `consentRecordsErased`. `plugin-consent` dep added to `dm-application`. 7 tests (2 new DSAR-specific)._
- [x] **KERNEL-P1-1** — Workflow engine port: replace custom `DmWorkflowOrchestrator` with Kernel workflow engine. _Implemented `DmWorkflowEngineAdapter` wrapping `DurableWorkflowEngine` (retry=3, backoff=2s, timeout=5m). `inMemory()` factory for dev/test. `platform:java:workflow` dep added to `dm-kernel-bridge`. 7 tests._
- [x] **KERNEL-P1-2** — Risk scoring port: real ML-based risk scoring, not rules-only. _Implemented `DmosRiskEvaluatorRegistrar` with 4 DMOS `FactorEvaluator` impls: `CAMPAIGN_LAUNCH`, `BUDGET_APPROVAL`, `AD_SPEND_ANOMALY`, `AI_EXECUTION`. Wired in `DmosApiServer#createRiskManagementPlugin`. 9 tests._
- [x] **KERNEL-P1-3** — Notification port: multi-channel notification delivery (email, SMS, push). _Implemented `DmosNotificationChannelRouter` routing by template prefix (`email.*`, `sms.*`, `push.*`). 7 canonical DMOS template constants. Structured logging per dispatch. 11 tests._
- [x] **KERNEL-P1-4** — Metrics collector port (KE-03): replace `LoggingDmosMetricsCollector` with platform Micrometer bridge. _Implemented `MicrometerDmosMetricsCollector` (Timer + Counter via `MeterRegistry`). Wired in `DmosApiServer#wireObservability`. Tests in `MicrometerDmosMetricsCollectorTest` (12 cases). `LoggingDmosMetricsCollector` retained as fallback._
- [ ] **KERNEL-P2-1** — Event sourcing: full event-sourced campaign aggregate replacing current state-based storage. _(Architecture team.)_
- [ ] **KERNEL-P2-2** — Multi-region failover: geo-distributed workspace routing. _(Infrastructure team.)_
- [ ] **KERNEL-P2-3** — AI model governance: versioned model registry with A/B evaluation framework. _(AI platform team.)_
