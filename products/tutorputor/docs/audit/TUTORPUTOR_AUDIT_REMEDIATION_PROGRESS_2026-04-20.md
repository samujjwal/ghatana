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
- Aligned the authoring E2E with the actual Tutorputor route boundary: Content Studio APIs use real bearer auth, while `/api/sim-author/*` direct calls use trusted-proxy identity headers because those routes are outside the global JWT guard.
- Updated learner browser E2E coverage to match the current assessments empty state, stub the real assessments API dependency explicitly, and scope dashboard progress assertions to the active enrollment card instead of an ambiguous duplicate percentage label.
- Replaced the stale critical-journey shell helper with a product-local live E2E runner that starts Tutorputor Postgres and Redis on non-conflicting ports, launches the platform, gateway, learner app, and admin app with the validated auth topology, waits for health checks, and executes the canonical Playwright browser specs.
- Documented the live E2E runner in Tutorputor development and testing guides so the browser validation path is repeatable without manual terminal choreography.
- Added direct learner-auth UI regression coverage for SSO provider discovery, provider error states, and login-page SSO error handling so the learner sign-in surface is tested independently of the auth context and E2E suites.
- Replaced the stale placeholder `StudentOnboarding.spec.ts` with canonical learner-browser onboarding coverage that exercises the real login entrypoint, first-visit dashboard, analytics navigation, and module exploration flow against the supported route map.
- Replaced the stale placeholder `EducatorWorkflow.spec.ts` with canonical admin-browser coverage that validates the real authoring workspace panels for a created experience and the consolidated analytics dashboard route.
- Expanded content-generation launcher verification with HTTP health and readiness smoke tests so the Java runtime bootstrap now has direct endpoint-level regression coverage in addition to port parsing tests.
- Integrated the independent generated-content validator into the Content Studio validate/publish path so evaluator-driven review requirements now block direct publish, flow through the canonical validation checks, and persist into analytics plus publish provenance snapshots.

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
- `tests/e2e/LearnerJourney.spec.ts`: compiles in isolation with TypeScript and now matches the current learner UI/API contract for assessments and dashboard recommendations.
- `tests/e2e/ContentStudio.spec.ts`: now uses real HS256 bearer auth for Content Studio APIs and the required trusted-proxy identity headers for direct `/api/sim-author/*` calls.
- Follow-up verification: `npx vitest run src/core/middleware/__tests__/consent-enforcement.test.ts src/__tests__/setupPlatform.integration.test.ts` passed with 62 tests green across the two suites.
- Follow-up verification: `npx playwright test --list ContentStudio.spec.ts` successfully discovered the updated authoring lifecycle test.
- Follow-up verification: `npx vitest run src/modules/content/studio/__tests__/service.test.ts src/core/middleware/__tests__/consent-enforcement.test.ts src/__tests__/setupPlatform.integration.test.ts` passed with 111 tests green across the three suites.
- Follow-up verification: `npx playwright test --list ContentStudio.spec.ts LearnerJourney.spec.ts` discovered 3 browser tests across the updated Tutorputor authoring and learner flows.
- Follow-up verification: a live manual Tutorputor stack was started with explicit local `DATABASE_URL`, `REDIS_URL`, `JWT_SECRET`, and trusted-proxy configuration, bypassing the workspace-specific Playwright `webServer` launcher failure (`spawn /bin/sh ENOENT`).
- Follow-up verification: `PLAYWRIGHT_SKIP_WEBSERVER=true BASE_URL=http://127.0.0.1:3201 ADMIN_URL=http://127.0.0.1:3202 GATEWAY_URL=http://127.0.0.1:3200 PLATFORM_URL=http://127.0.0.1:7105 npx playwright test ContentStudio.spec.ts LearnerJourney.spec.ts` passed with 3/3 browser tests green against the live Tutorputor stack.
- Follow-up verification: `bash ./scripts/run-critical-journey-e2e.sh` now succeeds in default mode, brings up the local Tutorputor live E2E topology, runs `ContentStudio.spec.ts` and `LearnerJourney.spec.ts`, reports 3/3 browser tests green, and cleans up the app processes it started.
- Follow-up verification: `cd apps/tutorputor-web && npx vitest run src/components/auth/__tests__/SsoLogin.test.tsx src/contexts/__tests__/AuthContext.test.tsx` passed with 17/17 tests green for learner auth/session coverage.
- Follow-up verification: `npx playwright test --list StudentOnboarding.spec.ts EducatorWorkflow.spec.ts` discovered the canonical learner onboarding and educator workflow browser specs under the supported Tutorputor Playwright topology.
- Follow-up verification: `pnpm exec playwright test StudentOnboarding.spec.ts EducatorWorkflow.spec.ts --project=chromium` passed with 4/4 canonical learner/admin browser tests green.
- Follow-up verification: `./gradlew :products:tutorputor:services:tutorputor-content-generation:test --tests com.ghatana.tutorputor.contentgeneration.ContentGenerationLauncherTest` passed with launcher port parsing plus health/readiness smoke coverage green.
- Follow-up verification: `npx vitest run src/modules/content/studio/__tests__/service.test.ts src/modules/content/studio/__tests__/lu-lifecycle.test.ts` passed with 62/62 tests green for independent-validator publish gating, analytics, and provenance coverage.
- Total targeted test verification for this remediation slice: 190 targeted Vitest tests passed across the follow-up batches, plus 7/7 live Playwright browser tests passed.

## Remaining Work

The following audit themes remain broader product workstreams rather than safe single-pass fixes:

- Pedagogical quality controls, golden datasets, and evaluator-driven content validation.
- Deeper end-to-end coverage across mobile, web, and cross-service learning flows.
- Broader UX and workflow simplification items identified in the audit.
- Product-level provenance, review, and operational maturity items that require separate design slices.
- Independent evaluator-driven publish gating is now enforced in the canonical Content Studio validate/publish path and for evidence confidence and contradiction signals, but broader golden-dataset validation and pedagogical benchmark governance are still separate workstreams.

This pass closes the audit's stale E2E coverage gap for learner onboarding and educator browser workflows, and it closes the missing launcher health smoke gap for the content-generation service. The remaining items are now the genuinely broader product workstreams above rather than leftover single-file regressions.

## Next Recommended Execution Shape

Continue in small audited batches:

1. cross-surface end-to-end learning workflow coverage
2. pedagogical evaluation and provenance infrastructure
3. broader product UX and operational maturity slices from the audit