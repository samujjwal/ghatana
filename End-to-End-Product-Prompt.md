# Ultra-Strict End-to-End Product Correctness, Completeness, UI/UX, Backend, DB, and Production-Readiness Audit Prompt

You are a principal product engineer, staff architect, senior UI/UX reviewer, backend reviewer, database reviewer, QA architect, security reviewer, and production-readiness auditor.

Your task is to perform a deep, evidence-based, end-to-end audit of the target product/module.

The primary objective is to prove whether the product is:

1. Correct.
2. Complete.
3. Consistent.
4. Production-grade.
5. Free from production-path mocks, stubs, fake behavior, placeholders, shortcuts, and hardcoded/demo-only logic.
6. Fully traceable from intended product behavior to UI, actions, backend logic, database operations, tests, and observability.

This is not a surface-level review. This is a full product correctness and completeness audit.

---

## Target Scope

Review the following target root(s):

- `<TARGET_PRODUCT_OR_MODULE_PATH>`

Include all directly related:

- Product code.
- UI pages/routes/components.
- Frontend actions/hooks/state stores.
- API clients.
- Backend controllers/routes/resolvers.
- Services/domain logic.
- Workers/jobs/event handlers.
- Database schemas/models/migrations/seed logic.
- Shared libraries used by the product.
- Config files.
- Feature flags.
- Tests.
- Scripts.
- Documentation.
- Architecture/vision/requirements docs.
- Generated clients only when needed to validate contracts.

Exclude only obvious generated/cache/vendor artifacts unless their source generator is in scope:

- `node_modules`
- `dist`
- `build`
- `.next`
- `.turbo`
- `.nx`
- `coverage`
- `.git`
- generated SDK output unless required for contract validation

Do not audit unrelated products unless they are direct dependencies of the target product.

---

# 0. Non-Negotiable Audit Doctrine

Apply these rules throughout the audit.

## Correctness Is Primary

Do not assume the implementation is correct because it exists or because tests pass.

For every feature, flow, action, API, backend operation, and database operation, verify:

- What should happen.
- What actually happens.
- Whether the behavior matches product intent.
- Whether all success, failure, edge, permission, validation, concurrency, and recovery paths are handled.
- Whether the UI, backend, DB, and tests agree with each other.
- Whether the implementation is deterministic, safe, and production-ready.

## Completeness Is Primary

Do not audit only visible or obvious features.

You must discover and inventory every relevant:

- Route.
- Page.
- Component.
- User action.
- Form.
- Modal.
- Drawer.
- Wizard.
- API endpoint.
- GraphQL query/mutation/subscription.
- Backend handler.
- Service method.
- Domain rule.
- DB model/table.
- Migration.
- Job/worker.
- Event/message handler.
- State store.
- Cache layer.
- Feature flag.
- Integration.
- Test file.
- Config file.
- Script.
- Documentation reference.

Then validate whether each one is complete, wired, tested, observable, secure, and consistent.

## No Mocks/Stubs Is Primary

Production paths must not contain:

- Mock data.
- Stubbed services.
- Fake APIs.
- Hardcoded responses.
- Placeholder logic.
- Demo-only behavior.
- In-memory persistence.
- Test utilities.
- Disabled validation.
- Bypassed auth.
- Fake success states.
- TODO implementations pretending to work.
- Catch blocks that swallow failures.
- Console-only error handling.
- Temporary hacks.
- Sample/example data used as real behavior.

If mocks/stubs/placeholders exist in production paths, mark the finding as P0 unless it is clearly non-critical and fully hidden behind a production-safe feature flag.

A non-critical stub is allowed only when all are true:

- It is behind a clearly named feature flag.
- The feature flag is disabled by default in production.
- The incomplete feature is hidden or disabled in the UI.
- The backend prevents direct access when disabled.
- The code is clearly documented as incomplete.
- Tests verify disabled behavior in production config.
- No critical user journey depends on it.

Critical flows must be real or removed. They must not be stubbed.

---

# 1. Build a Complete Product Inventory First

Before judging quality, build a complete inventory.

Create these inventories with file paths:

## 1.1 UI Inventory

List every:

- Route.
- Page.
- Layout.
- Component.
- Form.
- Modal.
- Drawer.
- Table.
- Dashboard.
- Report.
- Wizard.
- Navigation item.
- Empty state.
- Error state.
- Loading state.
- Success state.
- Permission-denied state.

For each item, document:

| UI Item | File(s) | Purpose | User Actions | Data Dependencies | API/State Dependencies | Completeness Status | Issues |
|---|---|---|---|---|---|---|---|

## 1.2 User Action Inventory

List every click, submit, command, keyboard shortcut, drag/drop action, menu action, bulk action, approval action, retry action, cancel action, import/export action, and destructive action.

For each action, document:

| Action | UI Source | Expected Result | Actual Handler | Backend/API Called | DB Impact | Success State | Failure State | Complete? | Issues |
|---|---|---|---|---|---|---|---|---|---|

## 1.3 API and Backend Inventory

List every:

- REST endpoint.
- GraphQL operation.
- RPC method.
- Controller.
- Resolver.
- Handler.
- Service method.
- Domain rule.
- Middleware.
- Worker.
- Job.
- Event processor.
- Integration adapter.

For each item:

| Backend Item | File(s) | Caller(s) | Expected Behavior | Auth/AuthZ | Validation | DB Access | Side Effects | Complete? | Issues |
|---|---|---|---|---|---|---|---|---|---|

## 1.4 Database Inventory

List every:

- Table/model.
- Field.
- Relation.
- Index.
- Constraint.
- Enum.
- Migration.
- Seed.
- Repository/query method.
- Transactional operation.

For each item:

| DB Item | File(s) | Purpose | Callers | Constraints | Indexes | Data Integrity Rules | Complete? | Issues |
|---|---|---|---|---|---|---|---|---|

## 1.5 Test Inventory

List every test and classify it:

- Unit.
- Integration without browser.
- API E2E.
- DB integration.
- UI component.
- UI flow.
- Browser E2E.
- Accessibility.
- Security/permission.
- Performance/load.
- Migration.
- Contract.

For each test:

| Test | File | Type | Feature Covered | What It Proves | Real or Mocked | Valid? | Gaps |
|---|---|---|---|---|---|---|---|

---

# 2. Understand Expected Product Behavior

Infer intended behavior from:

- Vision docs.
- Product docs.
- Requirements.
- Architecture docs.
- User stories.
- API contracts.
- DB schema.
- UI flows.
- Existing tests.
- README files.
- Issue/task docs if available.
- Naming and domain model.
- Actual implementation only as supporting evidence, not as the source of truth.

Produce a Product Behavior Map:

| Capability | User/Persona | Problem Solved | Expected UX | Expected Backend Behavior | Expected Data Behavior | Success Criteria |
|---|---|---|---|---|---|---|

Also identify:

- Primary personas.
- Primary jobs-to-be-done.
- Critical journeys.
- Non-critical journeys.
- Business rules.
- Data lifecycle.
- Integration boundaries.
- Governance/compliance needs.
- Operational expectations.

---

# 3. Requirement-to-Implementation Traceability Matrix

For every capability or requirement, trace it fully:

| Requirement / Capability | UI Route/Page | User Actions | API/Backend Handler | Service/Domain Logic | DB Models/Queries | Tests | Observability | Status |
|---|---|---|---|---|---|---|---|---|

Status must be one of:

- Complete and correct.
- Complete but incorrect.
- Partially implemented.
- UI only.
- Backend only.
- DB only.
- Test only.
- Stubbed/mock only.
- Missing.
- Duplicated/inconsistent.
- Unknown due to insufficient evidence.

Any capability with missing traceability is a gap.

---

# 4. End-to-End Journey Correctness Audit

Map and validate every major user journey from start to finish.

Required journeys include, where applicable:

- First-time user journey.
- Returning user journey.
- Onboarding/setup.
- Authentication.
- Authorization/role-based access.
- Create flows.
- Read/detail flows.
- Update/edit flows.
- Delete/archive flows.
- Search/filter/sort.
- Import/export/download.
- Approval/review.
- Configuration/settings.
- Dashboard/reporting.
- Notification/alert.
- Admin/governance.
- Collaboration/multi-user.
- Error/retry/recovery.
- Empty-state journey.
- Permission-denied journey.
- Offline/degraded-mode journey if applicable.
- Product-specific critical flows.

For each journey, verify:

- Entry point exists.
- Preconditions are clear.
- User intent is obvious.
- Required data is loaded correctly.
- Validation is complete.
- Actions are wired correctly.
- API contracts match frontend usage.
- Backend logic is correct.
- DB writes and reads are correct.
- Transaction boundaries are correct.
- UI reflects backend state accurately.
- Errors are specific and recoverable.
- Permission boundaries are enforced.
- No fake/mocked/stubbed behavior exists.
- Success state is real.
- Failure state is real.
- Tests prove the journey.
- Observability can diagnose the journey.

Output:

| Journey | Entry Point | Expected Outcome | Actual Behavior | Correct? | Complete? | Mock/Stub Risk | Gaps | Severity | Required Fix | Required Tests |
|---|---|---|---|---|---|---|---|---|---|---|

---

# 5. UI/UX Completeness and Correctness Audit

Review every UI item from the inventory.

Check:

- Purpose is clear.
- UX is simple and low cognitive load.
- Primary action is obvious.
- Secondary/destructive actions are clear.
- Similar components behave consistently.
- Layout is responsive.
- No visual clutter.
- No redundant UI.
- No duplicate flows.
- No inconsistent terminology.
- No broken links.
- No dead buttons.
- No orphan pages.
- No unreachable pages unless intentionally hidden.
- No UI-only features without backend support.
- No fake data.
- No fake loading/success states.
- Empty/loading/error/success states are real.
- Validation messages are actionable.
- Accessibility is acceptable.
- Keyboard navigation works.
- Focus management works.
- Destructive actions require appropriate confirmation.
- AI/automation is implicit where useful, but user can review, interrupt, override, or retry.

Output:

| UI Area | File(s) | Finding | Correctness Impact | Completeness Impact | Severity | Required Fix | Tests |
|---|---|---|---|---|---|---|---|

---

# 6. Frontend Action, State, and Data Flow Audit

Review every frontend action and state transition.

Check:

- Event handlers are wired correctly.
- Form values map correctly to API payloads.
- API responses map correctly to UI state.
- Cache invalidation is correct.
- Optimistic updates roll back correctly.
- Loading states are accurate.
- Errors are not swallowed.
- Race conditions are handled.
- Long-running actions can be cancelled/retried where needed.
- Duplicate submissions are prevented.
- Destructive actions are protected.
- State stores are not duplicated.
- Shared hooks/services are reused.
- Business logic is not duplicated in UI when it belongs in domain/shared logic.
- No mock API client is used in production.
- No test fixture is imported into production code.

Output:

| Action/State Flow | File(s) | Expected | Actual | Correct? | Complete? | Production Mock/Stub? | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|

---

# 7. API, Backend, and Domain Logic Correctness Audit

Review every backend item from the inventory.

Check:

- API contract is explicit.
- Request validation is complete.
- Response shape is correct.
- Error shape is consistent.
- Status codes are correct.
- Domain rules are centralized.
- Authorization is enforced server-side.
- Tenant/workspace/user boundaries are enforced.
- Idempotency exists where retries are possible.
- Transactions protect multi-step writes.
- Concurrency is safe.
- Error handling is explicit.
- No silent catch blocks.
- No hardcoded IDs, users, roles, tenants, regions, dates, or environment logic.
- No mocked services in production dependency injection.
- No fake persistence.
- No placeholder implementation.
- No duplicated backend logic.
- Integrations fail safely.
- Background jobs are observable and retryable.
- Audit events exist for critical actions.

Output:

| Backend Flow | File(s) | Expected Behavior | Actual Behavior | Correct? | Complete? | Mock/Stub? | Security/Data Risk | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|---|

---

# 8. Database and Data Integrity Audit

Review every DB item and DB operation.

Check:

- Schema matches domain.
- Required fields are enforced.
- Relationships are correct.
- Foreign keys exist where appropriate.
- Unique constraints prevent duplicates.
- Indexes support real queries.
- Enums are appropriate and not duplicated.
- Migrations are safe and reversible where required.
- Seeds are not required for production logic unless explicitly valid.
- Transaction boundaries are correct.
- No partial writes leave invalid state.
- Deletes are correct: hard delete, soft delete, archive, cascade.
- Tenant/workspace/user isolation is enforced.
- Audit fields exist for critical data.
- Data retention/deletion rules are clear.
- Pagination is safe.
- Sorting/filtering is deterministic.
- Query performance is acceptable.
- No production path uses in-memory DB or fake repository.
- No schema/model duplicates represent the same concept.

Output:

| DB Operation/Model | File(s) | Expected Data Rule | Actual Behavior | Correct? | Complete? | Integrity Risk | Performance Risk | Required Fix | Tests |
|---|---|---|---|---|---|---|---|---|---|

---

# 9. Production Mock/Stub/Shortcut Zero-Tolerance Audit

Perform a dedicated scan for:

- `mock`
- `stub`
- `fake`
- `dummy`
- `sample`
- `fixture`
- `placeholder`
- `TODO`
- `FIXME`
- `HACK`
- `TEMP`
- `not implemented`
- `coming soon`
- `demo`
- `hardcoded`
- `return []`
- `return null`
- `return true`
- `console.log`
- `throw new Error("Not implemented")`
- test utilities imported outside tests
- dev-only clients used outside dev/test
- in-memory repositories
- static JSON used as live product data
- feature flags that default incomplete features on

For every finding, determine:

- Is it in production code?
- Is it reachable in production?
- Is it part of a critical flow?
- Is it hidden behind a feature flag?
- Is the feature flag disabled by default in production?
- Is the UI hidden/disabled?
- Is backend access blocked?
- Are tests verifying disabled behavior?
- Should it be completed, removed, or isolated?

Output:

| File | Evidence | Production Reachable? | Critical Flow? | Feature Flagged? | Allowed? | Severity | Required Action |
|---|---|---|---|---|---|---|---|

Hard rule:

- Any production-reachable mock/stub/fake/placeholder in a critical flow is P0.
- Any incomplete feature visible to users without correct backend behavior is P0 or P1 depending on criticality.
- Any feature flag that exposes incomplete behavior by default in production is P0.
- Any test-only utility imported into production code is P0.
- Any fake DB/repository used in production path is P0.

---

# 10. Duplicate and Source-of-Truth Audit

Find duplicate or overlapping implementations across:

- UI components.
- Forms.
- Validators.
- Hooks.
- State stores.
- API clients.
- DTOs.
- Types.
- Enums.
- Constants.
- Domain models.
- Backend services.
- Repositories.
- Query builders.
- Error handling.
- Logging.
- Auth checks.
- Permission checks.
- Feature flags.
- Config.
- Test fixtures.
- Scripts.

For each duplicate:

| Duplicate Area | Files | Why It Is Duplicate | Risk | Canonical Owner | Delete/Merge Plan | Required Tests |
|---|---|---|---|---|---|---|

Rules:

- There must be one canonical source of truth for each concept.
- Shared concepts must live in shared/domain libraries where appropriate.
- Do not preserve duplicate legacy paths unless explicitly required.
- If replacing duplicates, update all usages and remove old code.
- Tests must prove no behavior regression.

---

# 11. Security, Privacy, Governance, and Permission Correctness Audit

Review:

- Authentication.
- Authorization.
- Role checks.
- Permission checks.
- Tenant/workspace isolation.
- Sensitive data exposure.
- Secret handling.
- Input sanitization.
- Output encoding.
- XSS/CSRF/SSRF/injection risks.
- File upload/download safety.
- Audit logging.
- Data retention.
- Data deletion.
- Privacy defaults.
- Secure error messages.
- No secrets in logs/traces/client bundles.
- No bypassed auth in production.
- No mock users in production.
- No hardcoded admin role or test user.

Output:

| Area | File(s) | Risk | Correct Behavior | Actual Behavior | Severity | Required Fix | Tests |
|---|---|---|---|---|---|---|---|

---

# 12. Observability and Operability Audit

Verify:

- Logs exist for critical actions.
- Logs are structured.
- Logs avoid sensitive data.
- Metrics exist for important flows.
- Traces cover frontend-to-backend where possible.
- Correlation/request IDs are propagated.
- Audit events exist for critical user/system actions.
- Background jobs are observable.
- Failures are diagnosable.
- Health/readiness checks exist.
- Retry/dead-letter behavior is visible where applicable.
- Operational dashboards or hooks can be created from emitted signals.

Output:

| Flow | Logs | Metrics | Traces | Audit Events | Gaps | Required Fix |
|---|---|---|---|---|---|---|

---

# 13. Performance and Scalability Audit

Review:

- UI rendering cost.
- Bundle size.
- Lazy loading.
- Data fetching patterns.
- API latency.
- N+1 queries.
- Index usage.
- Pagination.
- Filtering/sorting.
- Caching.
- Cache invalidation.
- Batch/stream handling.
- Long-running operation handling.
- Memory leaks.
- Retry/backoff.
- Rate limiting.
- Concurrency.
- Queue/job scalability.
- Large dataset behavior.

Output:

| Area | Risk | Evidence | Impact | Required Fix | Tests/Benchmarks |
|---|---|---|---|---|---|

---

# 14. Test Correctness and Coverage Audit

Tests must prove intended behavior, not simply implementation details.

For each capability, verify test coverage for:

- Success path.
- Validation failure.
- Backend failure.
- DB failure.
- Permission failure.
- Empty state.
- Edge cases.
- Concurrency/race conditions where applicable.
- Retry/cancel behavior where applicable.
- Feature-flag-off behavior for incomplete features.
- Security boundaries.
- Data integrity.
- API contract.
- UI behavior.

Reject tests that:

- Use `expect(true).toBe(true)`.
- Only snapshot large output.
- Only assert that a component renders.
- Assert mocks instead of real outcomes where integration is required.
- Do not validate DB state when DB writes matter.
- Do not validate API response shape.
- Do not validate permission boundaries.
- Ignore error paths.
- Use unrealistic fixtures.
- Depend on execution order.
- Hide broken behavior.
- Use excessive mocking for logic that should be integration-tested.

Output:

| Capability/Flow | Existing Tests | Missing Tests | Invalid Tests | Required Tests | Priority |
|---|---|---|---|---|---|

---

# 15. Final Severity Model

Classify findings as:

## P0 — Must Fix Before Production

Use P0 for:

- Incorrect critical flow.
- Missing critical flow.
- Production-reachable mock/stub/fake behavior in critical flow.
- Broken auth/security boundary.
- Data corruption or data loss risk.
- DB integrity failure.
- UI exposes incomplete critical feature.
- Backend accepts invalid critical operation.
- Tests falsely pass while behavior is broken.
- Production code imports test-only utilities.
- Feature flag exposes incomplete feature by default.

## P1 — Must Fix Before Release

Use P1 for:

- Important but non-critical incorrect behavior.
- Incomplete non-critical visible feature.
- Significant UX confusion.
- Missing important error handling.
- Missing important tests.
- Duplicated logic likely to cause drift.
- Missing observability for important flows.
- Performance issue likely under realistic load.

## P2 — Hardening

Use P2 for:

- Maintainability issues.
- Reuse opportunities.
- Minor consistency issues.
- Expanded test coverage.
- Documentation gaps.
- Non-critical observability improvements.

## P3 — Future Enhancement

Use P3 only for non-blocking improvements.

---

# 16. Required Final Report

Create or update:

`docs/audits/end-to-end-product-correctness-audit.md`

The report must include:

## 1. Executive Summary

Include:

- Overall correctness rating.
- Overall completeness rating.
- Production readiness rating.
- Mock/stub risk rating.
- Top P0 issues.
- Top P1 issues.
- Whether the product is safe to release.

## 2. Scope and Method

Explain what was reviewed and what was excluded.

## 3. Complete Product Inventory

Include UI, action, backend, DB, test, config, and feature flag inventories.

## 4. Product Behavior Map

Document intended behavior.

## 5. Requirement-to-Implementation Traceability Matrix

Show whether each capability is fully implemented and tested.

## 6. End-to-End User Journey Audit

Include all journey findings.

## 7. UI/UX Correctness and Completeness Audit

Include screen/component-level findings.

## 8. Frontend Actions, State, and Data Flow Audit

Include action-level findings.

## 9. Backend/API/Domain Logic Audit

Include backend correctness findings.

## 10. Database and Persistence Audit

Include data integrity findings.

## 11. Production Mock/Stub/Shortcut Audit

Include all evidence and required actions.

## 12. Duplicate and Source-of-Truth Audit

Include canonicalization/refactor plan.

## 13. Security, Privacy, Governance, and Permission Audit

Include risk findings.

## 14. Observability and Operability Audit

Include diagnosability gaps.

## 15. Performance and Scalability Audit

Include bottlenecks.

## 16. Test Correctness and Coverage Audit

Include invalid/missing tests.

## 17. Prioritized Remediation Plan

Use this table:

| Priority | Area | Issue | Evidence/File(s) | Required Fix | Acceptance Criteria | Tests Required |
|---|---|---|---|---|---|---|

## 18. Production Readiness Gate

State clearly:

- Ready for production: Yes/No.
- Ready for internal demo: Yes/No.
- Ready behind feature flag: Yes/No.
- Critical blockers.
- Minimum required fixes before release.

## 19. Final Checklist

Include a release checklist covering:

- Correctness.
- Completeness.
- No production mocks/stubs.
- UI/UX.
- Backend/API.
- DB/data integrity.
- Security/privacy.
- Observability.
- Performance.
- Tests.
- Documentation.

---

# 17. Strict Execution Rules

- Be specific.
- Use file paths.
- Use evidence.
- Do not give generic advice.
- Do not skip inventories.
- Do not skip hidden flows.
- Do not trust tests blindly.
- Do not trust implementation as specification.
- Do not ignore TODOs or placeholders.
- Do not allow production mocks/stubs.
- Do not allow fake success states.
- Do not allow incomplete visible critical features.
- Do not allow duplicated source-of-truth.
- Do not allow silent errors.
- Do not allow hardcoded users, tenants, roles, dates, regions, locale, currency, tax, config, or environment-specific assumptions.
- Every finding must have severity.
- Every significant finding must have a required fix.
- Every fix must have acceptance criteria.
- Every critical fix must have tests.
- If evidence is insufficient, mark the area as unknown and explain what must be inspected next.