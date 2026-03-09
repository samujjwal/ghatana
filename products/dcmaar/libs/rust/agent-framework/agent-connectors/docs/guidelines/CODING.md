# DCMaar Agent Connectors – Coding Guidelines

## 1. Scope

These guidelines apply to the `agent-connectors` crate.

## 2. Connector Design

- Implement connector traits using `async-trait` and Tokio.
- Keep connectors focused on IO and mapping; do not embed business logic.

## 3. Error Handling

- Use `anyhow` for connector-level errors and typed errors where exposed.
- Implement sensible retries and backoffs.

This document is self-contained and defines how to structure code in `agent-connectors`.
