# DMOS Testing

## Purpose

This document defines the self-contained testing strategy for DMOS. Testing must prove product correctness, not merely coverage numbers. It must prevent fake production paths, contract drift, authorization gaps, test theater, and incomplete end-to-end flows.

## Quality Bar

DMOS is not production-ready unless tests prove:

- UI, API, backend, domain, persistence, connector, audit, and telemetry behavior agree.
- Production code uses real implementations or fails closed.
- Tenant, workspace, role, permission, and capability boundaries are enforced.
- Every critical workflow has happy-path, negative-path, and regression coverage.
- AI-assisted flows expose provenance and safe behavior.
- Connector operations handle retries, idempotency, kill switches, and failure modes.
- Reports and analytics are computed from real data with known freshness.

## Required Test Tiers

| Tier | Scope | Examples |
|---|---|---|
| Unit | Pure logic and domain rules | Status transitions, validators, mappers, calculations |
| Application integration | Service orchestration | Campaign create, approval request, budget recommendation |
| Persistence integration | Database behavior | Migrations, constraints, tenant filters, repository queries |
| API/servlet tests | HTTP boundary | Headers, auth, errors, idempotency, OpenAPI drift |
| Connector tests | External adapter behavior | OAuth, retries, DLQ, kill switch, response mapping |
| UI unit/integration | Components and state | Forms, route gates, empty/error states, rendering |
| Browser E2E | Full user journeys | Campaign lifecycle, strategy approval, budget approval |
| Accessibility | User-facing UI | Keyboard navigation, labels, focus, contrast |
| Security/privacy | Boundaries and redaction | Tenant leakage, spoofed identity, PII redaction |
| Performance/reliability | Realistic load and failure | Dashboard queries, rate limits, connector chaos |
| Regression | Every discovered bug | Test added before or with fix |

## Anti-Test-Theater Rules

Forbidden:

- Dummy assertions.
- Tests that only assert non-null without behavior.
- Tests that mock away the behavior under review.
- Snapshot-only tests for critical business logic.
- UI tests that ignore API/data correctness.
- API tests that bypass auth/tenant checks.
- Tests that only prove implementation details.
- Fake green tests for feature-gated or incomplete behavior.
- Coverage padding with trivial getters/setters.

Required:

- Behavior-oriented names.
- Realistic fixtures.
- Deterministic setup/teardown.
- Clear expected outcomes.
- Negative-path checks.
- Cross-layer proof for critical flows.
- Regression tests for every bug.

## Critical Flow Coverage Matrix

| Flow | Required Coverage |
|---|---|
| Login/auth context | Dev/prod behavior, missing auth, spoofed headers, session derivation |
| Dashboard | Real data, empty, error, stale, disabled, unauthorized, correlation ID |
| Campaign create | Validation, success, duplicate idempotency, tenant isolation, audit |
| Campaign lifecycle | Launch, pause, complete, archive, rollback, invalid transitions |
| Strategy generation | Input validation, AI provenance, review, approval, rejection |
| Budget recommendation | Assumptions, thresholds, approvals, updates, audit |
| Content generation | Prompt input, generated content, validation, versioning, approval |
| Approval queue | Permission filters, risk filters, detail, approve/reject, conflicts |
| AI action log | Redaction, provenance, search/filter, linked entity |
| Google Ads connector | OAuth, execution, retry, DLQ, kill switch, rollback, error mapping |
| Feature gates | Off/on backend and UI behavior |
| Tenant/workspace security | No cross-scope reads, writes, counts, or decisions |
| Reports/analytics | Source data, formulas, freshness, empty/partial states |
| DSAR/retention | Export/delete/redaction behavior where PII exists |

## Contract Tests

API contract tests must prove:

- Every servlet route is represented in OpenAPI.
- No orphaned OpenAPI route exists.
- HTTP methods and paths match.
- Required headers are documented and enforced.
- Request and response schemas match implementation.
- Error envelopes are consistent.
- Client types are generated or validated from the spec.

## Authorization Tests

Every API family must test:

- Missing authentication.
- Missing tenant.
- Missing workspace.
- Missing permission.
- Disabled capability.
- Spoofed roles/permissions ignored in production.
- Cross-tenant access denied.
- Cross-workspace access denied.
- Reviewer lacks approval permission.
- Admin-only action blocked for non-admin.

## Persistence Tests

Persistence tests must prove:

- Migrations apply from empty database.
- Migrations are repeatable in CI.
- Constraints reject invalid state.
- Repository methods filter by tenant and workspace.
- Idempotency records prevent duplicate writes.
- Audit/approval immutability is preserved.
- Indexes support expected access patterns.
- Production code does not require seed data.

## Connector Tests

Connector tests must cover:

- OAuth token missing/expired/invalid.
- Successful campaign creation.
- External validation error.
- Retryable transient error.
- Non-retryable error.
- Timeout.
- Rate limit.
- DLQ after max attempts.
- Kill switch blocks execution.
- Idempotency prevents duplicate external calls.
- Compensation/rollback where supported.
- Sensitive token redaction.

## UI Test Requirements

Every user-facing page must test:

- Initial render.
- Loading state.
- Empty state.
- Error state.
- Unauthorized state.
- Feature-disabled state.
- Real data render.
- Primary action.
- Form validation.
- Pending action state.
- Success and failure feedback.
- Browser refresh/deep link behavior.
- Keyboard accessibility for critical controls.

## E2E Test Journeys

Minimum E2E journeys:

1. Login into workspace and view dashboard.
2. Create draft campaign.
3. Generate strategy and submit for approval.
4. Approve strategy.
5. Generate budget recommendation and submit for approval.
6. Approve budget.
7. Generate and validate content.
8. Launch campaign with connector disabled and see locked state.
9. Launch campaign with connector enabled in test environment.
10. Pause and rollback campaign.
11. Review AI action log and audit history.
12. Verify unauthorized user cannot perform restricted actions.

## Acceptance Gates

A PR touching DMOS critical paths must pass:

- Module unit tests.
- Product integration tests.
- API contract tests.
- Changed-flow API tests.
- Changed-flow browser E2E tests.
- Security/privacy tests for touched boundaries.
- Lint/typecheck/build gates.
- Static scans for production mocks/stubs/test-only imports.
- Migration validation if persistence changes.

## Testing Definition of Done

A feature is done only when:

- It has test coverage at the right tiers.
- It has meaningful negative tests.
- It has no production fake path.
- It proves tenant/workspace authorization.
- It proves UI/API contract alignment.
- It has regression tests for discovered bugs.
- It documents remaining unsupported states explicitly.

## Recovered Quality Gates and Pack Tests

## Workflow Replay Tests

Durable workflows require replay tests proving:

- Workflow can resume after service restart.
- Commands are not duplicated after retry.
- Idempotency key prevents double external execution.
- Failed command enters retry or DLQ correctly.
- Rollback plan can execute where supported.
- Kill switch pauses pending/running commands.
- Audit events are emitted once.

## Pack Contract Tests

DMOS domain packs require tests proving:

- `BoundaryPolicyStore.loadRules()` returns non-empty rules.
- Last boundary rule is default-deny.
- Sensitive reads require consent and audit where applicable.
- Rule IDs use `DM-`.
- Compliance rule packs are non-empty.
- Compliance rule IDs use `DM-`.
- Pack classes do not extend Kernel implementation classes.
- Pack classes use Kernel public interfaces only.
- Regulatory rules are in product packs, not Kernel/plugin source.

## Preflight Safety Tests

Campaign launch preflight tests must cover:

- Missing conversion tracking blocks launch.
- Missing consent banner blocks launch where required.
- Budget cap missing or exceeded blocks launch.
- Claim validation failure blocks launch.
- Forbidden claim blocks launch.
- Missing disclosure blocks launch.
- Audience region restriction blocks launch.
- Landing page unavailable blocks launch.
- CRM/form mapping failure blocks launch.
- Missing rollback plan blocks external execution.
- Missing approval blocks launch.
- Approved override is audited when policy allows it.

## Consent and Privacy Tests

Required tests:

- Consent captured with proof.
- Consent revoked.
- Suppression enforced before email/SMS/audience sync/CRM export.
- Purpose mismatch blocks data use.
- DSAR export includes appropriate records.
- DSAR delete/anonymize respects audit/legal retention constraints.
- AI prompt/action logs redact sensitive inputs and outputs.
- Tokens and secrets are never logged.

## Marketing Success Metrics Tests

Calculation tests must prove:

- Lead capture rate.
- CPL / cost per lead.
- CPA / cost per acquisition.
- CAC estimate.
- ROI.
- ROAS.
- Conversion rate.
- Funnel drop-off.
- Budget pacing.
- Attribution model output for last-click MVP.
- Export/report values match dashboard values.
