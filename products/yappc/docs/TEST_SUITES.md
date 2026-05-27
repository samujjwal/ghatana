# YAPPC Test Suites

YAPPC checks are grouped by the behavior they protect so local runs and CI jobs can target the smallest useful slice.

## Suite Boundaries

| Suite | Boundary | Primary signal | Avoid |
| --- | --- | --- | --- |
| Unit | One component, hook, service, mapper, validator, or domain collaborator with direct inputs. | Fast behavior proof for local logic and edge cases. | Network calls, browser navigation, object-literal assertions, or broad fixture orchestration. |
| Integration | Multiple YAPPC collaborators wired together at a real boundary such as Data Cloud adapters, phase packet assembly, route panels, or service composition. | Boundary correctness across persistence, packet assembly, generated clients, or UI panels. | Mocking the subject under test so deeply that the integration disappears. |
| Contract | Schema, route, manifest, generated client, i18n key, ProductUnitIntent, or serialized packet parity. | Drift detection between producer and consumer contracts. | Hand-maintained snapshots that are not generated from or asserted against production code. |
| E2E | Browser/API lifecycle journeys through mounted routes and backend-shaped fixtures. | User-visible workflow correctness, authorization behavior, and degraded dependency handling. | Replacing route behavior with static object assertions or skipping required lifecycle steps. |
| A11y | Keyboard, semantics, labels, landmarks, focus, and axe route checks. | WCAG-oriented route safety for key YAPPC journeys. | Pure snapshot tests without semantic assertions. |
| Performance | Canvas, phase cockpit, bundle, memory, and route budget checks. | Budget regression detection with actionable failing dimensions. | Unbounded timing assertions that are unstable across machines. |
| Security/Privacy/Governance | Tenant scope, roles, scopes, feature flags, policy allow/deny/error, redaction, and restricted route behavior. | Fail-closed behavior for sensitive or mutating paths. | UI-only authorization proof without backend denial tests. |

When a change crosses suite boundaries, run the smallest command in each affected row. For example, a phase action change usually needs backend governance tests, phase packet contract tests, and the frontend phase cockpit integration suite.

| Category | Command | Scope |
| --- | --- | --- |
| Unit | `pnpm -C products/yappc/frontend/web test:unit` | Focused component and hook behavior with direct fixtures. |
| Integration | `pnpm -C products/yappc/frontend/web test:integration` | Phase cockpit route/panel integration with backend-shaped packets. |
| Contract | `pnpm -C products/yappc/frontend/web test:contract` | Route inventory, E2E matrix metadata, API client, and i18n contract checks. |
| E2E | `pnpm -C products/yappc/frontend/web test:e2e` | Browser lifecycle journeys. |
| A11y | `pnpm -C products/yappc/frontend/web test:a11y` | Playwright axe/semantic route matrix. |
| Performance | `pnpm -C products/yappc/frontend/web test:performance` | Canvas budgets and browser performance/memory checks. |
| Regression Group Guard | `pnpm -C products/yappc/frontend/web test:regression:groups:check` | Verifies every suite command remains present and points at existing files. |
| Fast Regression | `pnpm -C products/yappc/frontend/web test:regression` | Type-check plus unit, integration, and contract suites. |
| Release Evidence | `node products/yappc/scripts/generate-yappc-scorecard-evidence.mjs products/yappc/build/release-evidence artifacts && node products/yappc/scripts/check-yappc-scorecard-evidence.mjs products/yappc/build/release-evidence/yappc-scorecard-evidence.json` | Generates and validates the YAPPC scorecard release-evidence JSON included in CI artifacts. |
| Doc Evidence | `node products/yappc/scripts/check-doc-evidence-links.mjs products/yappc/docs` | Fails production-readiness doc claims that do not carry nearby evidence links. |

Backend-focused slices remain Gradle-native:

| Category | Command |
| --- | --- |
| Contract | `./gradlew :products:yappc:core:scaffold:api:test --tests "com.ghatana.yappc.kernel.ProductUnitIntentExporterTest" :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.PhasePacketContractTest"` |
| Integration | `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.integration.DataCloudPhasePacketTruthIntegrationTest"` |
| Security | `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.YappcSecurityMatrixTest"` |
| Privacy | `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.api.YappcPrivacyContractTest"` |
| Governance | `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.phase.PhaseActionAuthorizationServiceTest"` |
