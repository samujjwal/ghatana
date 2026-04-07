# YAPPC Critical Journeys

This map keeps release gating focused on the paths users and operators actually depend on.

## Journey Map

| Journey | Owning automated path | Why it matters |
|---|---|---|
| Authenticated access to core web flows | `frontend/e2e/auth.spec.ts`, `core/services-lifecycle/src/test/java/com/ghatana/yappc/services/lifecycle/auth/LifecycleAuthApiContractTest.java` | Confirms login, session persistence, and fail-closed auth behavior remain intact. |
| Intent to generation lifecycle API flow | `core/ai/src/test/java/com/ghatana/yappc/ai/integration/AICallPathTest.java` | Verifies the AI service facade can route request-to-response generation with metrics recording. |
| Agent execution with real provider wiring in a release-like environment | `core/services-lifecycle/src/test/java/com/ghatana/yappc/services/security/YappcEnvironmentConfigTest.java`, `core/services-lifecycle/src/test/java/com/ghatana/yappc/services/lifecycle/LifecycleServiceModuleAiRuntimeTest.java` | Blocks production drift where AI startup, provider configuration, or tracing is missing. |
| Tenant isolation across API and persistence paths | `frontend/apps/api/src/__tests__/routes.integration.test.ts`, `core/services-lifecycle/src/test/java/com/ghatana/yappc/services/lifecycle/auth/AuthFilterChainE2ETest.java` | Confirms tenant context is enforced at HTTP and downstream workflow boundaries. |
| Release candidate startup with observability and health endpoints enabled | `.github/workflows/yappc-ci.yml` startup diagnostics step, `.github/workflows/yappc-contract-tests.yml` HTTP diagnostics step, `core/services-lifecycle/src/test/java/com/ghatana/yappc/services/lifecycle/YappcMetricsRegistryTest.java` | Ensures the shipped process exposes liveness, readiness, and authenticated metrics. |

## Retry Policy

- Vitest runs with `retry: 0` by default.
- Playwright runs with `retries: 0` by default.
- Any quarantined Playwright rerun must opt in with `PLAYWRIGHT_QUARANTINE_RETRIES` and stay outside the default release gate.

## Gate Hygiene

- Compatibility-only alias and migration-scaffolding tests do not belong in the default release gate.
- Frontend release confidence comes from current user and operator journeys plus active-code boundary checks such as `pnpm run check:duplication-boundaries`.