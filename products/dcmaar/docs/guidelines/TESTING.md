# DCMaar – Guardian App – Testing Guidelines

## 1. Goals

- Ensure Guardian apps, backend, and shared libs are correct, resilient, and consistent with platform contracts.

## 2. Scope

- `apps/*` – Frontend/dashboard apps.
- `backend` – Guardian backend services.
- `libs/*` / `packages/*` – shared utilities.

## 3. Recommended Strategy

- Follow DCMaar-wide testing practices (unit, integration, e2e) via the workspace commands in `package.json`:
  - `test`, `lint`, and `type-check` filtered across apps, backend, libs.
- Prefer contract and integration tests for cross-component flows.

## 4. Testing Pyramid and Responsibilities

- Unit tests: fast, isolated tests for functions, reducers, and domain services.
- Integration tests: validate interactions between modules (e.g., backend → DB, frontend → API). Use Testcontainers or equivalent where appropriate.
- End-to-end (E2E): cover critical parent flows (signup, onboarding child device, policy enforcement) using Playwright/Cypress.

## 5. Examples (commands)

Run tests for a specific package (example for backend):

```bash
pnpm --filter @yappc/guardian-backend test
```

Run all tests in the workspace (CI):

```bash
pnpm -w test
```

## 6. Contract Tests

- For any feature that crosses product boundaries (agents ↔ server), add contract tests that validate the protobuf/JSON contracts.
- Keep contract test fixtures under `contracts/tests` or `apps/*/contracts-tests` and run them as part of CI.

## 7. CI Requirements

- CI must run `pnpm -w test`, `pnpm -w lint`, and build checks (`pnpm -w build`) for changed packages.

This document now provides explicit guidance for test types, commands, and CI requirements.
