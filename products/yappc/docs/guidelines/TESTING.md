# YAPPC – Testing Guidelines

## 1. Goals

- Ensure backend, frontend, and E2E behavior match the YAPPC roadmap’s quality targets.
- Protect against regressions as state management and shared components are consolidated.

## 2. Backend Testing

- Maintain high coverage on:
  - HTTP controllers using ResponseBuilder.
  - Services and domain logic.
- Validate observability behavior:
  - Confirm metrics are emitted via core observability abstractions.

## 3. Frontend & App-Creator Testing

- For frontend packages (including app-creator):
  - Use modern test tooling (e.g., Jest/Vitest + Testing Library).
  - Test canvas and state flows thoroughly, especially around undo/redo and persistence.
- Ensure components using `@ghatana/ui` behave correctly and remain accessible.

## 4. E2E Testing

- Implement E2E tests for critical user flows in line with the roadmap:
  - Creation and editing of apps.
  - Backend integration flows.

These guidelines summarize key testing expectations from the YAPPC roadmap and remain valid even if roadmap documents are archived.
