# YAPPC Release Readiness Checklist

Use this checklist before declaring a YAPPC release candidate ready. It references only active workflows, modules, and docs.

## Required Sign-Offs

- Auth boundary sign-off: web, BFF/API, and Java HTTP auth behavior remain fail-closed outside explicit dev-only paths.
- CI truth sign-off: workflows describe canonical YAPPC modules and no longer treat retired modules as the primary architecture.
- AI provider wiring sign-off: configured providers, fallbacks, and safety filters are validated in the active runtime path.
- AI observability sign-off: provider latency, workflow inference failures, fallback counts, and correlation ID propagation are visible through the production metrics and tracing surfaces.
- Retry-removal sign-off: temporary recovery logic removed during hardening is not silently reintroduced.
- Compatibility cleanup sign-off: compatibility-only alias or migration-scaffolding tests are not part of the default release gate.
- Release evidence bundle sign-off: the `yappc-release-evidence-bundle` artifact is present for the candidate workflow run.
- release evidence bundle

## Required Workflow Evidence

- `.github/workflows/yappc-ci.yml` passes on the candidate branch.
- `.github/workflows/product-isolated-ci.yml` passes for YAPPC-scoped changes.
- `.github/workflows/yappc-contract-tests.yml` passes against the current OpenAPI contract in `docs/api/openapi.yaml`.
- `.github/workflows/yappc-ci.yml` uploads `yappc-release-evidence-bundle` with startup diagnostics and critical-journey artifact pointers.
- `.github/workflows/yappc-fe-ci.yml` passes `pnpm run check:duplication-boundaries` before unit tests.

## Required Local Verification

Run the current canonical checks:

```bash
./gradlew \
  :products:yappc:services:build \
  :products:yappc:core:services-platform:check \
  :products:yappc:core:services-lifecycle:check \
  :products:yappc:core:yappc-services:check \
  :products:yappc:core:yappc-infrastructure:check \
  :products:yappc:core:yappc-agents:check \
  :products:yappc:core:ai:check \
  :products:yappc:validateAgentCatalog \
  :products:yappc:validateReleaseObservability

cd products/yappc/frontend
pnpm typecheck
pnpm lint
pnpm test
pnpm exec playwright test e2e/auth.spec.ts e2e/golden-path.spec.ts
```

## Critical Journey Evidence

- Authenticated access to core web flows: `frontend/e2e/auth.spec.ts`
- Intent to generation lifecycle API flow: `core/ai/src/test/java/com/ghatana/yappc/ai/integration/AICallPathTest.java`
- Agent execution with release-like provider wiring: `core/services-lifecycle/src/test/java/com/ghatana/yappc/services/security/YappcEnvironmentConfigTest.java`
- Tenant isolation across API and persistence paths: `frontend/apps/api/src/__tests__/routes.integration.test.ts`
- Release startup diagnostics: lifecycle startup evidence attached by CI and contract tests

## Default Gate Hygiene

- Migration-era alias tests are removed or excluded from the default frontend unit gate.
- Deprecated compat package usage is blocked through `products/yappc/frontend/scripts/check-duplication-boundaries.js` rather than preserved through alias-smoke tests.

## Documentation Gate

- `START_HERE_ARCHITECTURE.md` and `MODULE_CATALOG.md` still match the active module graph in `products/yappc/settings.gradle.kts`.
- No release note, onboarding guide, or CI workflow tells contributors to use `backend:api`, `core:domain`, `core:framework`, or `core:lifecycle` as the current primary surface.
- Any intentionally retained compatibility alias is explicitly marked as compatibility-only.

## Release Decision

Ship only when all sign-offs are affirmative and the workflow/local evidence above is attached to the release candidate.
