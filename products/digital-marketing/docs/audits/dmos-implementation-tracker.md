# DMOS Unified Implementation Tracker

Source inputs merged and deduplicated:
- dmos-execution-plan.md
- dmos-product-todos.md
- dmos-kernel-platform-enhancement-todos.md
- dmos-product-kernel-audit.md

Status legend:
- DONE: implemented in codebase in this execution
- IN PROGRESS: partially implemented, remaining work tracked
- TODO: not yet implemented

## P0 Production Blockers

- DONE: Register durable AI action log repository in production composition.
- IN PROGRESS: Replace Google Ads production in-memory wiring with durable repositories and fail-closed API client behavior.
- DONE: Wire governed AI workflow for strategy generation in production with explicit disable fallback path.
- DONE: Implement concrete default-deny policy validation in production bootstrap validator.
- DONE: Close tenant/workspace isolation gaps for campaigns and approval snapshots with tenant-aware persistence constraints and negative tests.

## P1 Release Blockers

- IN PROGRESS: Normalize approval servlet error envelope to canonical structure and correlation ID.
- DONE: Fix approval GET pending/status/snapshot handlers to use read context (no write idempotency requirement).
- DONE: Harden DmosHttpContextFactory to fail closed in production without identity provider.
- IN PROGRESS: Complete PII/token/privacy hardening, including DSAR and observability redaction.
- DONE: Replace static production proof with runtime production composition proof test.
- DONE: Expand Google Ads failure/retry/rate-limit test matrix.
- DONE: Add non-mocked Playwright backend+Postgres critical journey.

## P2/P3 Follow-Up

- DONE: Shared feature-unavailable UI component and boundary page consolidation.
- DONE: Lazy-load boundary/heavy UI routes and enforce bundle budget checks.
- DONE: Real readiness/health indicators for DB/kernel/plugins/connectors.
- DONE: Route-capability contract generation to prevent UI/backend drift (shared JSON artifact consumed by backend entitlement servlet and validated by frontend route-contract tests).
- TODO: Platform extraction candidates from product-agnostic backlog after DMOS stabilization.

## Implementation Notes (Current Execution)

- Added durable production wiring pieces for Google Ads connector runtime:
  - PostgresDmConnectorRepository
  - PostgresDmGoogleAdsCampaignLinkRepository
  - DataSource-backed PostgresDmGoogleAdsCredentialRepository
  - Migration V32 for connector and Google Ads tables
- Updated DmosApiServer to:
  - register PostgresAiActionLogRepository in production wiring
  - wire durable connector/credential/link repositories in PostgreSQL mode
  - fail closed when Google Ads is enabled in production but credentials are missing
  - avoid in-memory fake Google Ads success path in production
  - run bootstrap validation after service/command wiring
- Updated approval servlet error behavior toward canonical envelope + correlation ID header.
- Updated bootstrap policy validation to enforce presence/shape of DM-BP-999 default-deny boundary policy rule.
- Added migration V33 to enforce tenant-aware campaign/approval snapshot persistence constraints.
- Hardened Postgres campaign/approval snapshot repositories to derive tenant from workspace and reject cross-tenant snapshot writes.
- Added persistence integration regressions for tenant mismatch and tenant-id persistence.
- Redacted sensitive telemetry attributes (tenant/workspace/principal/idempotency) using stable hash tokens.
- Replaced static production persistence proof assertions with runtime bootstrap validator wiring tests.
- Added Google Ads adapter retry/backoff behavior for 429 and 5xx responses plus matrix coverage tests.
- Aligned DmosApprovalServletTest with fail-closed auth/capability/idempotency requirements.
- Added stateful bridge health indicator and wired `DmosHealthServlet` registration in production PostgreSQL composition.
- Expanded backend route entitlement metadata to include DMOS route-capability entries and role-gated parity with frontend route set.
- Refactored DMOS UI route manifest to lazy-load route modules and added bundle budget enforcement script in the UI build pipeline.
- Added shared route-capability contract artifact and frontend manifest parity test enforced in UI build checks.
- Consolidated reporting boundary pages (Funnel Analytics, Attribution, ROI/ROAS) onto shared feature-unavailable UX.

## Verification

- DONE: Module compile verification succeeded
  - :products:digital-marketing:dm-persistence:compileJava
  - :products:digital-marketing:dm-api:compileJava
  - :products:digital-marketing:dm-application:compileJava
- DONE: Focused DMOS test verification passed for changed areas:
  - :products:digital-marketing:dm-persistence:test --tests "*PostgresCampaignRepositoryTest" --tests "*PostgresApprovalSnapshotRepositoryIT" --tests "*FlywayMigrationValidationTest"
  - :products:digital-marketing:dm-connector-google-ads:test --tests "*HttpDmGoogleAdsCampaignApiClientAdapterTest"
  - :products:digital-marketing:dm-application:test --tests "*ProductionPersistenceWiringProofTest"
  - :products:digital-marketing:dm-api:test --tests "*DmosApprovalServletTest"
- IN PROGRESS: Broader dm-api test suites still contain pre-existing fail-closed header alignment gaps outside the scope of this focused tracker pass.
