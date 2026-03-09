# Virtual-Org – Workflow Library – Testing Guidelines

## 1. Goals

- Ensure workflow primitives and integration helpers behave correctly and deterministically.

## 2. Unit Tests

- Cover:
  - Workflow state transitions.
  - Mapping between workflow types and operator/Virtual‑Org/domain-model types.
- Use in-memory fakes for operator and Virtual‑Org interactions.

## 3. Integration Tests

- For critical workflows, add tests that exercise:
  - End-to-end flows through Virtual‑Org + operator framework.
  - Async behavior using ActiveJ promises.

## 4. Determinism & Isolation

- Avoid real network/IO in tests; keep them deterministic and fast.
- Control timeouts and scheduling when testing async behavior.

This document is self-contained and explains how to test the Virtual‑Org Workflow library.
