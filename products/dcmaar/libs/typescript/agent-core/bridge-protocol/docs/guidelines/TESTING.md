# DCMaar Bridge Protocol – Testing Guidelines

## 1. Goals

- Ensure bridge messages are correctly typed and validated on both sides.

## 2. Unit Tests

- Use Vitest to cover:
  - Zod schema validation for typical and edge-case messages.
  - Type narrowing and type-level utilities where used.

## 3. Integration Tests

- Where possible, add tests that simulate desktop ↔ extension flows using the shared contracts.

## 4. Determinism

- Keep tests deterministic and side-effect free.

This document is self-contained and explains how to test `@dcmaar/bridge-protocol`.
