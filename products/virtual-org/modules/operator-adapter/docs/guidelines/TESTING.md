# Virtual-Org – Core Operator Adapter – Testing Guidelines

## 1. Goals

- Ensure operator adapters correctly integrate Virtual‑Org agents with the operator framework and observability stack.

## 2. Unit Tests

- Mock agents and operator interfaces to validate:
  - Input and output mappings.
  - Error handling and timeout behavior.
  - Metrics and tracing hooks are invoked as expected.

## 3. Integration Tests

- For critical adapters, add integration tests that:
  - Exercise an in-memory operator runtime with stub agents.
  - Verify correct propagation of metrics and traces.

## 4. Determinism

- Avoid real network calls; use controlled fixtures for protobuf/domain payloads.

This document is self-contained and explains how to test the core Operator Adapter library.
