# DCMaar Server – Coding Guidelines

## 1. Scope

These guidelines apply to the Go DCMaar Server under `framework/server`.

## 2. Architecture & Layering

- Follow repository-wide layering:
  - **API**: gRPC/HTTP handlers and protobuf contracts.
  - **Application**: orchestration, query planning, policy evaluation, jobs.
  - **Domain**: core domain models and invariants.
  - **Infrastructure**: DB, caches, message buses, external services.
- Keep application/domain packages independent of specific transport or DB drivers where possible.

## 3. Dependencies

- Use approved libs:
  - gRPC/protobuf, ClickHouse/Postgres/SQLite drivers, Redis, OPA, Viper, Zap, OpenTelemetry, Prometheus.
- All API contracts must be generated from `.proto` files; no hand-written protos or stubs.

## 4. Error Handling

- Use `error` values plus `pkg/errors`/wrapping where needed.
- Do not panic on recoverable errors; return structured errors and status codes.

## 5. Observability

- Use Zap for structured logging; never log secrets or PII.
- Use OpenTelemetry spans for inbound RPCs and key operations.
- Expose Prometheus metrics for requests, queries, and background jobs.

## 6. Configuration & Policy

- Use Viper-based configuration; avoid hardcoding settings.
- Evaluate access and filtering through OPA policies where applicable.

This document is self-contained and defines how to structure code in the DCMaar Server.
