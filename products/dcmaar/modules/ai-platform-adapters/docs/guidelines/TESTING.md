# DCMaar Agent Daemon – Testing Guidelines

## 1. Goals

- Ensure the agent behaves deterministically and meets resource, privacy, and reliability requirements.

## 2. Unit Tests

- Cover:
  - Core domain logic (sampling, filtering, redaction).
  - Plugin lifecycle and capability enforcement.
  - Local queue/WAL operations.
- Use mocks/fakes for server communication and OS services.

## 3. Integration Tests

- Exercise end-to-end flows:
  - Agent ↔ server gRPC interactions via `dcmaar-pb`.
  - Local HTTP API (Axum) endpoints.
  - WASM plugin execution with constrained resources.

## 4. Performance & Benchmarks

- Use the existing Criterion benches (`proto_bench`, `storage_benchmarks`, `compression_benchmarks`, `performance_suite`) to track regressions.
- Ensure CPU and memory remain within defined budgets.

## 5. Determinism & Stability

- Avoid time- or randomness-dependent behavior in tests; if needed, inject clocks/PRNGs.

This document is self-contained and explains how to test the DCMaar Agent Daemon.
