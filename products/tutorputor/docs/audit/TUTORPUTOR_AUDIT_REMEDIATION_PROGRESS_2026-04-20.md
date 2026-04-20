# Tutorputor Audit Remediation Progress

Reference audit: `TUTORPUTOR_DEEP_PRODUCT_REALITY_AUDIT_2026-04-19.md`

## Reconciliation Result

The audit was partially stale by the time remediation work started. A code-to-audit reconciliation was completed first to avoid duplicating already-landed fixes or reintroducing drift.

## Already Fixed Before This Pass

- Consent enforcement was already registered in platform bootstrap.
- Auth refresh and logout routes were already present.
- The web app was already wrapped with the auth provider.
- The content-generation launcher already had real bootstrap and health behavior.
- The local `ttr-dev` flow already enabled queue and worker startup by default.

## Completed In This Pass

- Hardened AI routes to require authenticated or trusted-proxy request context instead of fabricating `default` tenant or `anonymous` user identities.
- Hardened shared request-context helpers to fail closed when tenant or user context is missing.
- Removed mobile-side default tenant fabrication and stopped emitting `X-Tenant-ID: default`.
- Made dashboard fetching require an authenticated tenant session.
- Standardized the non-production Stripe placeholder to `stripe_test_placeholder_secret` and aligned runtime validation plus local dev bootstrap behavior.
- Updated AI route tests to assert the stricter authentication contract and fixed mock leakage in the suite.
- Added learning route regression coverage for dashboard access under authenticated tenant context and missing-context rejection.
- Added web API client regression coverage for strict tenant-context requirements and fail-closed request behavior.
- Added mobile session storage and dashboard fetch regression coverage to verify no fabricated tenant context is emitted.
- Added minimal Tutorputor mobile Jest and Babel configuration so the new mobile tests execute inside the app workspace.
- Expanded learner E2E coverage with a resumable dashboard journey that asserts authenticated dashboard hydration, personalized recommendation rendering, and resume-learning navigation to the canonical module route.
- Added publish-provenance regression coverage in content studio so publish-time audit and revision records must capture validation metadata, evidence-bundle confidence, and latest AI generation context.
- Fixed the consent-enforcement route map to cover the real `/api/v1/integration/*` namespace and added regression coverage for third-party-sharing consent requirements.
- Replaced the Content Studio E2E suite's trusted-header API shortcut with a real signed bearer token so authoring lifecycle verification exercises the authenticated backend path.
- Aligned Docker Compose and deployment guidance with the neutral Stripe placeholder required by stricter startup validation.
- Updated Tutorputor architecture truth-surface links to point at the current deep audit instead of the stale 2026-04-18 audit reference.
- Added manual publish review gating in Content Studio so low-confidence or contradictory evidence bundles now block direct publish, create reviewer work items, and emit `REVIEW_SUBMITTED` lifecycle events instead of silently promoting risky content.
- Tightened the Content Studio browser flow so the admin authoring E2E bootstraps the page through `/api/v1/auth/me` with a real signed bearer token in browser storage, not only through request-level API auth.

## Verification

- `src/modules/ai/__tests__/routes.test.ts`: 30 passed.
- `src/__tests__/setupPlatform.integration.test.ts`: 43 passed.
- `src/modules/learning/__tests__/routes.test.ts`: 25 passed.
- `src/api/__tests__/tutorputorClient.test.ts`: 9 passed.
- `src/storage/__tests__/NativeSessionStorage.test.ts`: passed.
- `src/hooks/__tests__/useDashboard.test.ts`: passed.
- `src/modules/content/studio/__tests__/service.test.ts`: 47 passed.
- `src/core/middleware/__tests__/consent-enforcement.test.ts`: expanded route-mapping coverage for integration consent.
- `src/__tests__/setupPlatform.integration.test.ts`: expanded runtime consent coverage for integration routes.
- `src/modules/content/studio/__tests__/service.test.ts`: expanded publish-path coverage for manual review gating on low-confidence and contradictory evidence bundles.
- `tests/e2e/LearnerJourney.spec.ts`: compiles in isolation with TypeScript.
- `tests/e2e/ContentStudio.spec.ts`: now signs a real HS256 bearer token instead of relying on trusted proxy identity headers.
- Follow-up verification: `npx vitest run src/core/middleware/__tests__/consent-enforcement.test.ts src/__tests__/setupPlatform.integration.test.ts` passed with 62 tests green across the two suites.
- Follow-up verification: `npx playwright test --list ContentStudio.spec.ts` successfully discovered the updated authoring lifecycle test.
- Follow-up verification: `npx vitest run src/modules/content/studio/__tests__/service.test.ts src/core/middleware/__tests__/consent-enforcement.test.ts src/__tests__/setupPlatform.integration.test.ts` passed with 111 tests green across the three suites.
- Follow-up verification: `npx playwright test --list ContentStudio.spec.ts LearnerJourney.spec.ts` discovered 3 browser tests across the updated Tutorputor authoring and learner flows.
- Playwright runtime execution is currently blocked in this workspace because the Tutorputor gateway/platform startup path requires explicit `DATABASE_URL`, `REDIS_URL`, and `JWT_SECRET` configuration plus reachable backing services.
- A direct Playwright execution attempt in this workspace currently fails before browser startup with `spawn /bin/sh ENOENT`, so browser-runtime validation remains environment-blocked rather than code-blocked.
- Total targeted test verification for this remediation slice: 111 additional targeted tests passed in this follow-up batch, with Playwright spec discovery succeeding for 3 browser tests.

## Remaining Work

The following audit themes remain broader product workstreams rather than safe single-pass fixes:

- Pedagogical quality controls, golden datasets, and evaluator-driven content validation.
- Deeper end-to-end coverage across mobile, web, and cross-service learning flows.
- Broader UX and workflow simplification items identified in the audit.
- Product-level provenance, review, and operational maturity items that require separate design slices.
- Independent evaluator-driven publish gating is now enforced for evidence confidence and contradiction signals, but broader evaluator integration and golden-dataset validation are still separate workstreams.

## Next Recommended Execution Shape

Continue in small audited batches:

1. cross-surface end-to-end learning workflow coverage
2. pedagogical evaluation and provenance infrastructure
3. broader product UX and operational maturity slices from the audit