# YAPPC E2E Test Setup Guide (YAPPC-006)

## Overview

YAPPC has two E2E test suites:

1. **Java E2E Tests** (`e2e-tests/src/test/java/com/ghatana/yappc/e2e/`)
   - `FullWorkflowE2ETest.java` — Complete intent-to-delivery workflow
   - `AuthenticationFlowE2ETest.java` — Auth flows and token handling
   - `ProjectLifecycleE2ETest.java` — Project CRUD and lifecycle
   - `TenantIsolationE2ETest.java` — Multi-tenant isolation
   - `AgentExecutionE2ETest.java` — Agent execution integration

2. **Playwright E2E Tests** (`frontend/e2e/current-release/`)
   - 32 spec files covering UI flows, canvas interactions, auth, governance, etc.
   - Configured via `frontend/playwright.config.ts`

## Prerequisites

### Java E2E Tests
- Java 21 installed
- Gradle wrapper available (`./gradlew`)
- Database accessible (for integration tests)

### Playwright E2E Tests
- Node.js 18+ installed
- pnpm installed (`npm install -g pnpm`)
- Frontend dependencies installed (`cd frontend && pnpm install`)
- Playwright browsers installed (`cd frontend && pnpm exec playwright install`)

## Running E2E Tests

### Quick Start (Basic Mode)

```bash
# Run all E2E tests (basic, no canvas seeding)
./scripts/run-e2e-tests.sh
```

### With Canvas Seeding (Heavy Canvas Tests)

```bash
# Enable canvas seeding for diagram/canvas-heavy tests
./scripts/run-e2e-tests.sh --canvas
```

### Java E2E Tests Only

```bash
# Run only Java E2E tests
./scripts/run-e2e-tests.sh --java-only
```

### Playwright E2E Tests Only

```bash
# Run only Playwright E2E tests
./scripts/run-e2e-tests.sh --playwright-only
```

### CI Mode

```bash
# CI mode: no dev server reuse, no HTML report serving
./scripts/run-e2e-tests.sh --ci
```

## Manual Playwright Execution

```bash
cd frontend

# Set environment variables
export PLAYWRIGHT_BASE_URL="http://localhost:7002"
export PLAYWRIGHT_ENABLE_CANVAS="true"  # optional, for canvas tests
export PLAYWRIGHT_SERVE_HTML="1"        # optional, serve HTML report

# Run Playwright tests
pnpm exec playwright test

# Run specific spec
pnpm exec playwright test e2e/current-release/smoke.spec.ts

# Run with UI
pnpm exec playwright test --ui
```

## Manual Java E2E Execution

```bash
cd products/yappc

# Run all E2E tests
./gradlew :products:yappc:e2e-tests:test

# Run specific test class
./gradlew :products:yappc:e2e-tests:test --tests com.ghatana.yappc.e2e.FullWorkflowE2ETest

# Run with rerun
./gradlew :products:yappc:e2e-tests:test --rerun-tasks
```

## Environment Variables

### Playwright
- `PLAYWRIGHT_BASE_URL` — Base URL for the web app (default: `http://localhost:7002`)
- `PLAYWRIGHT_ENABLE_CANVAS` — Enable canvas seeding for heavy canvas tests (default: `false`)
- `PLAYWRIGHT_SERVE_HTML` — Serve HTML report after tests (default: `0`)
- `PLAYWRIGHT_QUARANTINE_RETRIES` — Number of retries for flaky tests (default: `0`)
- `PLAYWRIGHT_ALWAYS_TRACE` — Always trace (default: `on-first-retry`)
- `CI` — CI mode flag (default: unset)

### Java
- Database connection configured via application properties or environment variables
- See `e2e-tests/src/test/resources/` for configuration

## Known Limitations

1. **Live Environment Required** — Both test suites require a live environment:
   - Playwright: Dev server started automatically via `webServer` config
   - Java: Database and service dependencies must be available

2. **Canvas Seeding** — Heavy canvas/diagram tests require `PLAYWRIGHT_ENABLE_CANVAS=true` to seed localStorage with test data

3. **Database Seed** — Java E2E tests require database seeding for multi-tenant and isolation tests

## Troubleshooting

### Playwright Tests Fail to Start
```bash
# Ensure browsers are installed
cd frontend && pnpm exec playwright install

# Check dev server is accessible
curl http://localhost:7002
```

### Java E2E Tests Fail to Connect to Database
```bash
# Check database is running
# Verify connection settings in e2e-tests/src/test/resources/application.properties
```

### Canvas Tests Fail
```bash
# Enable canvas seeding
export PLAYWRIGHT_ENABLE_CANVAS=true
./scripts/run-e2e-tests.sh --canvas
```

## CI Integration

Add to your CI pipeline:

```yaml
- name: Run E2E Tests
  run: ./scripts/run-e2e-tests.sh --ci
  env:
    PLAYWRIGHT_ENABLE_CANVAS: true
```

## References

- Playwright Config: `frontend/playwright.config.ts`
- Global Setup: `frontend/e2e/global-setup.ts`
- Java E2E Tests: `e2e-tests/src/test/java/com/ghatana/yappc/e2e/`
- Playwright Tests: `frontend/e2e/current-release/`
