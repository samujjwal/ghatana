# DCMaar – Connectors – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/connectors`.

## 2. Connector Design

- Implement connectors as composable, testable components with clear responsibilities.
- Keep connector logic focused on IO, batching, and resilience—not business logic.

## 3. Resilience & Observability

- Implement retries, backoff, and circuit breakers where appropriate.
- Emit structured logs and metrics for connector behavior.

This document is self-contained and defines how to structure code in `@dcmaar/connectors`.
