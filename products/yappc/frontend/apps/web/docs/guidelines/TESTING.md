# YAPPC App-Creator Web – Testing Guidelines

## 1. Goals

- Maintain strong coverage for canvas/editor flows and backend integration points.
- Protect against regressions as state management and design system usage evolve.

## 2. Unit & Integration Tests

- Use Jest or Vitest with Testing Library for component and hook tests.
- Test:
  - Canvas interactions (adding/removing/moving components).
  - State persistence and undo/redo behavior.
  - Integration with key backend endpoints where appropriate.

## 3. E2E Tests

- Implement E2E tests for critical app-creator flows:
  - Creating and editing applications.
  - Saving, loading, and publishing.

These guidelines are self-contained and aligned with the overall YAPPC testing roadmap.
