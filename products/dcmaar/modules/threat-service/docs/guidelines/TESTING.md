# DCMaar Server – Testing Guidelines

## 1. Goals

- Ensure server behavior is reliable, correct, and performant for ingest, policy, and query paths.

## 2. Unit Tests

- Cover:
  - Core domain logic (policy evaluation wrappers, query planners, aggregations).
  - Smaller services in `internal/` and `pkg/`.
- Use table-driven tests and fakes for DB/Redis/OPA clients.

## 3. Integration Tests

- Run against:
  - Real or containerized DB (ClickHouse, SQLite) and Redis.
  - End-to-end gRPC/HTTP APIs using generated clients.
- Validate behavior under realistic data volumes where feasible.

## 4. Performance & Load

- Add focused benchmarks for expensive queries and ingest flows where needed.
- Track P95/P99 latencies and throughput in CI or dedicated perf runs.

This document is self-contained and explains how to test the DCMaar Server.
