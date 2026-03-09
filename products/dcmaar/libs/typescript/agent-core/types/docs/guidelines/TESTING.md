# DCMaar – Agent Types – Testing Guidelines

## 1. Goals

- Ensure shared agent types remain correct, backwards compatible, and aligned with runtime behavior.

## 2. Tests

- Use Vitest for unit tests on helper/utility types where applicable (e.g., runtime guards).
- Add contract tests in consuming projects to validate JSON payloads against `@dcmaar/agent-types` definitions.

This document is self-contained and explains how to test `@dcmaar/agent-types`.
