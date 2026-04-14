# Test Tier Inventory

> **Purpose**: Canonical record of every test surface in `products/yappc` and
> `platform/typescript`, the tier each surface correctly occupies, its CI owner,
> and the authoritative person/team responsible for keeping it green.
>
> Update this document whenever a suite is added, removed, relocated, or
> reclassified. It is machine-referenced by the CI enforcement step in
> `.github/workflows/release-gate.yml`.
>
> Machine-readable companion: `docs/trackers/TEST_TIER_INVENTORY.json`
>
> **Last Updated**: 2026-04-13

---

## Tier Definitions

| Tier | Code | Environment | Real infra? |
|------|------|-------------|-------------|
| Unit | `U` | any | No |
| Integration (non-browser) | `I-nb` | Node / JVM | Varies |
| Integration (browser) | `I-br` | jsdom / headless browser | No (mocked) |
| Contract | `C` | Node | No |
| API E2E | `A-e2e` | Node (real stack) | Yes |
| UI / Browser E2E | `B-e2e` | Chromium (Playwright) | Yes |
| Performance / Load | `P` | dedicated | Yes |
| Resilience / Recovery | `R` | Node / isolated | Partial |
| Smoke / Release Gate | `S` | Chromium + real stack | Yes |

---

## `platform/typescript` — per-package inventory

| Package | Tier(s) | Test location | Runner | CI job | Status |
|---------|---------|---------------|--------|--------|--------|
| `@ghatana/design-system` | U, I-br, C | `src/**/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/canvas` | U, I-br | `src/**/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/audit-components` | U, I-br | `src/**/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/ui-builder` | U, I-br | `src/**/__tests__/` | Vitest | `ui-package-gates` | ⚠️ Shallow |
| `@ghatana/accessibility` | U, I-br | `src/**/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/accessibility-audit` | U | `src/**/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/realtime` | U, C, R | `src/__tests__/` | Vitest | `ui-package-gates` | ⚠️ R incomplete |
| `@ghatana/platform-events` | U, C | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/api` | U, C | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/tokens` | U, C | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/theme` | U | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/ds-schema` | U, C | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/ds-governance` | U | `src/__tests__/` | Vitest | `ui-package-gates` | ⚠️ Shallow |
| `@ghatana/config` | U | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |
| `@ghatana/sso-client` | U | `src/__tests__/` | Jest | `ui-package-gates` | ✅ Active (was permissive) |
| `@ghatana/forms` | U, I-br | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Added 2026-04-13 |
| `@ghatana/patterns` | U, I-br | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Added 2026-04-13 |
| `@ghatana/primitives` | U, I-br | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Added 2026-04-13 |
| `@ghatana/wizard` | U, I-br | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Added 2026-04-13 |
| `@ghatana/data-grid` | U, I-br | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Added 2026-04-13 |
| `@ghatana/ui` | U | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Added 2026-04-13 |
| `@ghatana/canvas-core` | C | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Added 2026-04-13 (facade) |
| `@ghatana/canvas-tools` | C | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Added 2026-04-13 (facade) |
| `@ghatana/canvas-plugins` | C | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Added 2026-04-13 (facade) |
| `@ghatana/testing` | P | `src/` | Vitest | `ui-package-gates` | ✅ Replaced 2026-04-13 |
| `@ghatana/platform-utils` | U | `src/__tests__/` | Vitest | `ui-package-gates` | ✅ Active |

---

## `products/yappc` — per-area inventory

### Backend (Java)

| Module | Tier(s) | Test location | Runner | CI job | Status |
|--------|---------|---------------|--------|--------|--------|
| `core/domain` | U | `src/test/java/...` | JUnit 5 + EventloopTestBase | `yappc-ci / backend` | ✅ Active |
| `core/ai` | U, I-nb | `src/test/java/...` | JUnit 5 | `yappc-ci / backend` | ✅ Active |
| `core/services-platform` | U | `src/test/java/...` | JUnit 5 | `yappc-ci / backend` | ✅ Active |
| `api` | I-nb, C | `src/test/java/...` | JUnit 5 | `yappc-ci / backend` | ⚠️ MockCodeGenApiClient (reclassified 2026-04-13) |
| `e2e-tests` (Java) | I-nb | `src/test/java/...` | JUnit 5 | `yappc-ci / backend` | ⚠️ MockAuthService (reclassified 2026-04-13) |
| `e2e-tests real stack` | A-e2e | `src/test/java/...` | JUnit 5 + Testcontainers | `yappc-ci / backend` | 🆕 Added 2026-04-13 |

### Frontend (TypeScript / React)

| Module | Tier(s) | Test location | Runner | CI job | Status |
|--------|---------|---------------|--------|--------|--------|
| `apps/api` — route tests | I-nb (mock Prisma) | `src/__tests__/routes.integration.test.ts` | Vitest | `yappc-ci / frontend` | ⚠️ Mock Prisma (reclassified 2026-04-13) |
| `apps/api` — real DB route tests | I-nb (real Prisma) | `src/__tests__/auth-flow.api-e2e.test.ts` | Vitest | `yappc-ci / frontend` (needs DB) | 🆕 Added 2026-04-13 |
| `apps/api` — AI fallback | U / I-nb | `src/__tests__/integration/ai-service.integration.test.ts` | Vitest | `yappc-ci / frontend` | ✅ Active |
| `apps/api` — RBAC | U | `src/services/auth/__tests__/rbac.service.test.ts` | Vitest | `yappc-ci / frontend` | ✅ Active |
| `libs/yappc-ai` — BehaviorTracker | U | `src/ml/tracking/__tests__/BehaviorTracker.test.ts` | Vitest | `yappc-ci / frontend` | ✅ Unskipped 2026-04-13 |
| `libs/yappc-ai` — RecommendationEngine | U | `src/ml/recommendations/__tests__/RecommendationEngine.test.ts` | Vitest | `yappc-ci / frontend` | ✅ Unskipped 2026-04-13 |
| `libs/yappc-ai` — ABTestFramework | U | `src/ml/testing/__tests__/ABTestFramework.test.ts` | Vitest | `yappc-ci / frontend` | ✅ Unskipped 2026-04-13 |
| `web` — canvas unit | U, I-br | `src/components/canvas/__tests__/` | Vitest | `yappc-ci / frontend` | ⚠️ Some skipped |
| `web` — canvas route integration | I-br | `src/routes/__tests__/` | Vitest | `yappc-ci / frontend` | ⚠️ Some skipped |
| `e2e/auth` | I-br (intercepted) | `products/yappc/frontend/e2e/auth.spec.ts` | Playwright | `yappc-ci / playwright` | ⚠️ Reclassified as browser-integration 2026-04-13 |
| `e2e/smoke` | I-br | `products/yappc/frontend/e2e/smoke.spec.ts` | Playwright | `yappc-ci / playwright` | ⚠️ No real backend |
| `e2e/golden-path` | I-br | `products/yappc/frontend/e2e/golden-path.spec.ts` | Playwright | `yappc-ci / playwright` | ⚠️ Heavy flag injection |
| `e2e/release-gate` | S (real backend) | `products/yappc/frontend/e2e/release-gate.spec.ts` | Playwright | `release-gate` | 🆕 Added 2026-04-13 |
| `e2e/diagram` | — | `products/yappc/frontend/e2e/diagram.spec.ts` | Playwright | — | ❌ All skipped — container absent |
| `e2e/canvas-sprint-5-7` | — | `products/yappc/frontend/e2e/canvas-sprint-5-7.spec.ts` | Playwright | — | ❌ Mostly skipped — unimplemented |
| `web/perf` | P | `web/perf/infinite-canvas.bench.spec.ts` | Vitest | nightly | ❌ Skipped |

### Contracts

| Surface | Tier | Test location | Runner | CI job | Status |
|---------|------|---------------|--------|--------|--------|
| OpenAPI (YAPPC API) | C | Schemathesis in CI | Schemathesis | `yappc-ci / contract-tests` | ✅ Active |
| Event schemas (agent-dispatch, agent-result, etc.) | C | `config/agents/event-schemas/__tests__/` | Vitest | `yappc-ci / frontend` | 🆕 Added 2026-04-13 |

---

## CI job to tier mapping

| CI workflow / job | Tiers it owns | Authoritative? |
|-------------------|---------------|----------------|
| `.github/workflows/yappc-ci.yml / backend` | U, I-nb, C (Java) | ✅ Yes |
| `.github/workflows/yappc-ci.yml / frontend` | U, I-br | ✅ Yes |
| `.github/workflows/yappc-ci.yml / contract-tests` | C (OpenAPI) | ✅ Yes |
| `.github/workflows/yappc-ci.yml / playwright` | I-br (mocked E2E) | ✅ Yes |
| `.github/workflows/release-gate.yml` | S | ✅ Yes — authoritative release gate |
| `.github/workflows/yappc-fe-coverage.yml` | metrics only | advisory |
| `.github/workflows/yappc-fe-e2e-full.yml` | B-e2e (nightly) | nightly only |
| `products/yappc/.github/workflows/quality-gates.yml` | U, I-nb, lint | advisory |
| `.github/workflows/ui-package-gates.yml` | U, I-br, C (TS) | ✅ Yes |

---

## Authoritative release gate definition

The **authoritative release gate** is `.github/workflows/release-gate.yml`.

A build is considered **release-ready** only when ALL of the following pass:

1. All backend unit and integration tests pass (`yappc-ci / backend`)
2. All frontend unit and browser-integration tests pass (`yappc-ci / frontend`)
3. OpenAPI contract tests pass (`yappc-ci / contract-tests`)
4. Event-schema contract tests pass (inside `yappc-ci / frontend`)
5. Real-backend Playwright release-gate suite passes (`release-gate`)
6. All active `platform/typescript` package test suites pass (`ui-package-gates`)

---

## Reclassification log

| Date | Suite | Old classification | Correct classification | Action taken |
|------|-------|--------------------|----------------------|--------------|
| 2026-04-13 | `AuthenticationFlowE2ETest.java` | API E2E | Mock-backed service integration | Renamed intent in JavaDoc |
| 2026-04-13 | `YappcCodeGenApiOpenApiIntegrationTest.java` | Contract / Integration | Mock contract (schema-shape only) | Clarified in class JavaDoc |
| 2026-04-13 | `routes.integration.test.ts` | Real integration | HTTP-layer with mock Prisma | Reclassified in test header |
| 2026-04-13 | `e2e/auth.spec.ts` | UI E2E | Browser integration (intercepted) | Clarified in test header |
| 2026-04-13 | `e2e/smoke.spec.ts` | Release gate | Browser integration (no real backend) | Preserved; real gate is `release-gate.spec.ts` |

---

## Skipped-test backlog

Suites with skip markers that require implementation to be unblocked (not coverage gaps):

| Suite | Skip reason | Action |
|-------|-------------|--------|
| `e2e/diagram.spec.ts` (all 7) | Canvas container absent | ❌ Block on canvas implementation |
| `e2e/canvas-poc.spec.ts` (5) | react-flow/tool buttons absent | ❌ Block on canvas implementation |
| `e2e/canvas-phase1-2.spec.ts` (10+) | Notifications/marquee unimplemented | ❌ Block on canvas implementation |
| `canvas-sprint-5-7.spec.ts` (3 groups) | Features unimplemented | ❌ Block on sprint delivery |
| `web/perf/infinite-canvas.bench.spec.ts` | Performance infra incomplete | 🔧 Fix with Phase 7 performance work |
| `web/src/routes/__tests__/integration/` | Canvas scene infra issues | 🔧 Fixed 2026-04-13 |
| `libs/yappc-ai/src/ml/**` | Unknown (no message) | ✅ Unskipped 2026-04-13 |

---

*Maintained by: Platform Engineering*
