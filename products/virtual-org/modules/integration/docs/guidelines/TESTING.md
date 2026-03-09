# Virtual-Org – Integration Library – Testing Guidelines

## 1. Goals

- Ensure integration code correctly connects Virtual‑Org, operator framework, AEP, and domain models, and is safe to evolve.

## 2. Unit Tests

- Mock upstream/downstream components to verify:
  - Correct mapping between types.
  - Error and edge-case handling.
- Use fixtures for typical mapping scenarios (Virtual‑Org event → operator invocation → AEP pattern input, etc.).

## 3. Integration Tests

- For critical flows, add tests that exercise integration end-to-end in a controlled environment (e.g., minimal in-memory Virtual‑Org + operator + AEP setup).

## 4. Determinism

- Keep tests deterministic by avoiding real network calls.
- Control timeouts and scheduling in tests.

This document is self-contained and explains how to test the Virtual‑Org Integration library.
