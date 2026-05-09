
````md
# Ultra-Strict Product Review, Correctness, Completeness, and End-to-End Hardening Prompt

You are a principal product engineer, architect, QA lead, security reviewer, and production-readiness auditor. Your task is to deeply review the target product end-to-end and produce a concrete remediation plan, then implement safe fixes where possible.

## Target

Review the product rooted at:
products/digital-marketing

Include all product-owned code, configs, tests, scripts, routes, APIs, UI pages, backend services, database models, migrations, seed data, documentation, and dependent local libraries used by this product.

Exclude generated artifacts, build outputs, vendored dependencies, caches, lockfile-only noise, and unrelated products unless they directly affect this product.

---

## Primary Goal

Ensure the product is fully working end-to-end with:

- Complete and correct features.
- Correct business logic, computations, queries, and state transitions.
- Consistent architecture, APIs, UI behavior, naming, data contracts, and error handling.
- Production-grade implementation with no shortcuts.
- No mocks, stubs, fake data, placeholders, TODO-driven flows, hardcoded demo behavior, or incomplete implementations in production paths.
- Comprehensive automated testing across unit, integration, API E2E, UI E2E, regression, performance, reliability, accessibility, and security-relevant paths.
- Full end-to-end use case coverage from user action → UI → API → service/domain logic → database/external integration → response → rendered UI/state/audit/telemetry.
- Clear acceptance criteria and evidence that the product works as intended.

---

## Review Inputs

Inspect and cross-reference all available sources of truth:

1. Product vision, requirements, specs, PRDs, design docs, architecture docs, README files, ADRs, API docs, schema docs, and tickets if present.
2. UI routes, pages, components, forms, dashboards, modals, navigation, state management, error boundaries, loaders/actions, and client-side API calls.
3. Backend APIs, controllers/resolvers/routes, services, domain logic, workflows, jobs, queues, event handlers, middleware, auth, permissions, validation, and data access.
4. Database schema, migrations, views, indexes, constraints, transactions, seed data, query correctness, and data freshness model.
5. Tests across all tiers.
6. Runtime configuration, environment handling, scripts, build/deploy setup, observability, logging, tracing, metrics, audits, and security/privacy controls.
7. Local shared libraries and abstractions used by this product.

When requirements are missing, infer intended behavior from product context, UI labels, route names, data models, domain names, tests, and code structure. Clearly mark inferred requirements as inferred and validate them against implementation.

---

## Review Dimensions

### 1. Feature Completeness

For every feature, page, route, action, API, workflow, and background process:

- Identify the intended user/business outcome.
- Determine whether the feature is fully implemented or partially implemented.
- Check whether all success, empty, loading, validation, error, permission, retry, concurrency, and edge states are handled.
- Verify that UI, backend, database, and tests agree on the same behavior.
- Confirm that every visible UI action maps to a real backend capability unless intentionally feature-flagged.
- Confirm that every backend capability has an appropriate UI/API path or documented reason why it is internal-only.
- Identify missing features, incomplete flows, hidden assumptions, inconsistent behavior, and dead-end user journeys.

Flag anything that appears complete in UI but is not truly functional end-to-end.

---

### 2. Code Correctness

Deeply inspect implementation correctness, not just code style.

Validate:

- Business rules and domain logic.
- Calculations, aggregations, filters, sorting, pagination, totals, percentages, date/time handling, currency/locale/region handling, and rounding.
- Query correctness, joins, where clauses, permissions filters, data freshness, transaction boundaries, and race conditions.
- API request/response contracts.
- Type safety and schema validation.
- Error propagation and user-safe error messaging.
- Async behavior, retries, cancellation, idempotency, duplicate submission protection, and eventual consistency.
- State transitions and side effects.
- Cache invalidation and stale-data risks.
- Boundary cases, null/undefined handling, empty data, large data, malformed input, and unauthorized access.
- Data integrity and referential consistency.

Do not assume code is correct because tests pass. Verify against requirements and expected behavior.

---

### 3. End-to-End Use Case Validation

Create a use case inventory covering all major flows.

For each use case, document:

- User/persona.
- Preconditions.
- Entry point.
- Steps.
- UI components involved.
- API calls involved.
- Backend services/domain logic involved.
- Database reads/writes involved.
- External integrations involved, if any.
- Expected outputs.
- Expected UI state changes.
- Expected audit/log/telemetry events.
- Failure modes.
- Existing test coverage.
- Missing test coverage.
- Whether it works end-to-end today.

At minimum, cover:

- Happy path.
- Validation failures.
- Empty state.
- Not found.
- Unauthorized/forbidden.
- Backend error.
- Network failure/retry.
- Duplicate action.
- Concurrent update.
- Large data set.
- Refresh/reload/deep link.
- Role/persona-specific behavior.
- Feature flag disabled/enabled behavior.
- Data consistency after create/update/delete.

---

### 4. No Mocks, Stubs, Fake Data, or Shortcuts in Production

Strictly scan for production-path shortcuts.

Flag and remediate:

- Mock APIs used by production UI.
- Stubbed service methods.
- Fake repositories.
- Hardcoded demo users, orgs, IDs, tokens, permissions, prices, totals, dates, statuses, or responses.
- TODO/FIXME/HACK/temporary production logic.
- Placeholder pages, fake dashboards, sample-only data, canned charts, and static metrics.
- In-memory stores used where durable storage is required.
- Disabled validation.
- Bypassed auth.
- Fake success responses.
- Console-only observability.
- Tests or stories accidentally imported into production code.
- Environment checks that silently switch to mock behavior.
- Feature code that only works with seed/demo data.

Acceptable only when:

- Clearly isolated to tests, Storybook, local development, or explicit demo mode.
- Protected by a non-production feature flag or environment guard.
- Impossible to load in production builds.
- Documented with owner and removal criteria.

Production code must use real implementations or safely fail with a clear disabled/unavailable state.

---

### 5. Consistency and Architecture Quality

Review for consistency across the full stack.

Check:

- Naming conventions.
- Folder/module boundaries.
- Shared abstractions.
- API conventions.
- Error response format.
- Validation approach.
- Auth and permission model.
- Data transfer objects and schema reuse.
- UI component reuse.
- State management patterns.
- Form handling patterns.
- Loading/empty/error states.
- Table/list/detail patterns.
- Date/time/currency/number formatting.
- Logging, metrics, tracing, and audit patterns.
- Configuration and environment handling.
- Test style, fixtures, factories, and naming.
- Dependency usage and duplicate libraries.

Flag duplicate implementations, overlapping utilities, inconsistent patterns, unnecessary abstractions, tight coupling, module sprawl, circular dependencies, and logic implemented in the wrong layer.

Prefer reuse, simplicity, correctness, explicit contracts, and clear ownership.

---

### 6. Test Coverage and Test Correctness

Do not only measure coverage. Validate whether tests are meaningful and prove expected behavior.

Review all existing tests for:

- Correct assertions.
- Valid expected outputs.
- Realistic fixtures.
- No dummy assertions.
- No testing implementation details when behavior should be tested.
- No over-mocking that hides real integration failures.
- No brittle timing or ordering assumptions.
- Deterministic setup and teardown.
- Correct use of factories/seeds/fixtures.
- Clear test names.
- Proper tier classification.

Required test tiers:

1. Unit tests for pure logic, validators, mappers, reducers, utilities, domain rules, calculations, and state transitions.
2. Integration tests for service + database behavior, repository queries, transactions, constraints, migrations, and data freshness.
3. API E2E tests for request validation, auth, permissions, response contracts, errors, pagination, sorting, filtering, idempotency, and persistence.
4. UI integration tests for components, forms, loaders/actions, state transitions, rendering rules, and error states.
5. UI E2E tests for critical user journeys through the browser.
6. Regression tests for every bug or gap discovered.
7. Accessibility tests for important UI flows.
8. Performance/scalability tests for expensive queries, large tables, dashboards, reports, imports/exports, and background jobs.
9. Security/privacy tests for auth, permissions, tenant isolation, data leakage, audit behavior, and unsafe inputs.

For every feature and use case, map required tests to existing tests and identify gaps.

Aim for 100% coverage of product-critical code, logic branches, workflows, API contracts, and user-visible behaviors. Coverage must be meaningful, not artificial.

---

### 7. UI/UX End-to-End Correctness

For every page and user-facing flow:

- Verify the page has a clear purpose.
- Verify the page shows the correct data from the correct source.
- Verify every card, table, chart, metric, field, badge, action, filter, and detail panel has a real source and correct computation.
- Verify loading, empty, error, unauthorized, stale, and partial-data states.
- Verify navigation, breadcrumbs, deep links, refresh behavior, and browser back/forward behavior.
- Verify forms validate correctly and show actionable messages.
- Verify destructive actions require appropriate confirmation.
- Verify optimistic updates are safe and revert on failure.
- Verify role/persona/tier-based visibility and actions.
- Verify UI does not duplicate confusing information.
- Verify UI remains simple, complete, and low cognitive load.

No UI component should display fake, stale, duplicated, misleading, or untraceable data.

---

### 8. Backend/API/Data Correctness

For every API and backend workflow:

- Confirm endpoint/resolver intent.
- Confirm input validation.
- Confirm auth and authorization.
- Confirm tenant/org/workspace scoping.
- Confirm service/domain logic correctness.
- Confirm database query correctness.
- Confirm transaction safety.
- Confirm response contract correctness.
- Confirm errors are explicit and safe.
- Confirm audit/log/metrics/traces are emitted where needed.
- Confirm tests prove all major paths.
- Confirm performance is acceptable for realistic data volume.

For database:

- Validate schema supports intended use cases.
- Validate constraints protect integrity.
- Validate indexes support access patterns.
- Validate migrations are safe.
- Validate seed data is not required for production correctness.
- Validate computed values are stored or derived consistently.
- Validate soft-delete/archive behavior if present.
- Validate data freshness and reporting semantics.

---

### 9. Security, Privacy, Observability, and Production Readiness

Review:

- Authentication.
- Authorization.
- Role/persona/tier-based access.
- Tenant isolation.
- Input validation and sanitization.
- Secret handling.
- Sensitive data exposure.
- Audit logs.
- Data retention/deletion.
- Error message safety.
- Dependency risk.
- CSRF/CORS where applicable.
- Rate limiting and abuse prevention.
- Logging without leaking secrets or PII.
- OpenTelemetry tracing, metrics, structured logs, and correlation IDs.
- Health checks and readiness checks.
- Runtime config validation.
- Deployment and rollback readiness.
- Build reproducibility.
- Failure recovery.

Flag every production-readiness gap with severity and remediation.

---

## Required Output

Produce a single markdown report at:

`<OUTPUT_REPORT_PATH>`

Use this structure:

```md
# Product End-to-End Review and Hardening Report

## 1. Executive Summary
- Overall status: Ready / Not Ready / Partially Ready
- Top blockers
- Highest-risk areas
- Recommended next steps

## 2. Scope Reviewed
- Paths reviewed
- Product modules reviewed
- Libraries reviewed
- Tests reviewed
- Config/docs reviewed
- Explicit exclusions

## 3. Product Intent and Feature Inventory
| Area | Feature | Intended Outcome | Source of Truth | Status | Notes |
|---|---|---|---|---|---|

## 4. End-to-End Use Case Matrix
| Use Case | Persona | UI Route | API/Backend | Data Stores | Expected Result | Current Status | Test Coverage | Gaps |
|---|---|---|---|---|---|---|---|---|

## 5. Feature Completeness Findings
| Severity | Feature | Finding | Impact | Evidence | Recommended Fix |
|---|---|---|---|---|---|

## 6. Code Correctness Findings
| Severity | Area | Finding | Expected Behavior | Actual Behavior | Evidence | Fix |
|---|---|---|---|---|---|---|

## 7. Mock/Stub/Fake/Placeholder Findings
| Severity | Location | Type | Production Risk | Allowed? | Required Remediation |
|---|---|---|---|---|---|

## 8. Consistency and Architecture Findings
| Severity | Area | Inconsistency | Impact | Recommended Standard |
|---|---|---|---|---|

## 9. Test Coverage and Test Correctness Review
| Feature/Flow | Existing Tests | Missing Tests | Invalid/Weak Tests | Required Tests |
|---|---|---|---|---|

## 10. UI/UX Correctness Review
| Route/Page | Data Shown | Source | Actions | States Covered | Gaps |
|---|---|---|---|---|---|

## 11. Backend/API/Data Review
| API/Service/Table | Responsibility | Correctness Status | Missing Cases | Risks | Fixes |
|---|---|---|---|---|---|

## 12. Security, Privacy, Observability, and Production Readiness
| Area | Status | Gaps | Required Fix |
|---|---|---|---|

## 13. Required Remediation Plan
| Priority | Task | Why | Files/Areas | Implementation Notes | Acceptance Criteria | Required Tests |
|---|---|---|---|---|---|---|

## 14. Implementation Checklist
- [ ] Remove or isolate all production mocks/stubs/placeholders
- [ ] Fix correctness bugs
- [ ] Complete missing feature flows
- [ ] Add missing API/backend tests
- [ ] Add missing UI tests
- [ ] Add missing E2E use case tests
- [ ] Add regression tests for discovered bugs
- [ ] Validate production config
- [ ] Validate observability/audit/security paths
- [ ] Re-run full test suite and document results

## 15. Final Readiness Decision
- Ready / Not Ready / Partially Ready
- Remaining blockers
- Evidence required before release
````

---

## Severity Rules

Use the following severity model:

* **Blocker:** Product cannot be considered end-to-end working, or production path depends on mock/stub/fake behavior.
* **Critical:** Incorrect business logic, data corruption risk, broken core flow, auth/privacy issue, or missing critical test coverage.
* **High:** Major incomplete feature, inconsistent contract, unreliable workflow, or important untested path.
* **Medium:** Usability issue, non-critical inconsistency, partial coverage, maintainability concern.
* **Low:** Minor cleanup, naming issue, documentation improvement.

Do not downgrade severity because a workaround exists.

---

## Fixing Rules

When safe and within scope, implement fixes directly.

For each fix:

1. Explain the issue.
2. Update production code.
3. Add or update meaningful tests.
4. Remove or isolate mocks/stubs/placeholders.
5. Update docs/configs if behavior changes.
6. Run targeted tests.
7. Run broader relevant test suites.
8. Document evidence.

Do not introduce new mocks to make tests pass unless the mock is strictly test-local and the real integration is covered elsewhere.

Do not fake coverage. Do not add dummy tests. Do not hide broken features behind silent success states.

---

## Acceptance Criteria

The review is complete only when:

* Every feature, route, API, workflow, and major data path has been inventoried.
* Every production mock/stub/fake/placeholder has been identified.
* Every critical end-to-end use case has a test plan.
* Core use cases have automated tests.
* Code correctness has been reviewed against expected behavior, not just current implementation.
* UI and backend behavior are contractually aligned.
* Database reads/writes are validated for correctness and integrity.
* Security/privacy/observability gaps are documented.
* The report clearly states whether the product is ready, partially ready, or not ready.
* All recommended fixes include concrete acceptance criteria and test cases.

---

## Execution Instructions

Proceed in this order:

1. Discover product structure and sources of truth.
2. Build feature and use case inventory.
3. Trace each use case end-to-end through UI, API, backend, and data.
4. Review production code for correctness and completeness.
5. Search for mocks, stubs, fake data, placeholders, TODOs, and hardcoded shortcuts.
6. Review tests for coverage and correctness.
7. Review consistency, architecture, security, privacy, observability, and production readiness.
8. Implement safe fixes where possible.
9. Add or update tests.
10. Produce the final markdown report.

Be strict. Be concrete. Prefer evidence over opinion. Do not claim something is working unless verified by code, tests, or executable behavior.

```
```
