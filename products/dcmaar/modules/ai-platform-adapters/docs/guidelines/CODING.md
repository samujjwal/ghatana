# DCMaar Agent Daemon – Coding Guidelines

## 1. Scope

These guidelines apply to the Rust Agent Daemon (`agent-core-rs`), including its internal crates (`agent-http`, `agent-auth`, `agent-telemetry`, `agent-types`, etc.).

## 2. Architecture & Layering

- Follow the platform split of concerns:
  - **API**: Local HTTP API (Axum) and gRPC clients.
  - **Application**: Agent orchestration, scheduling, plugin lifecycle, sampling.
  - **Domain**: Agent configuration, event and metric models, policies.
  - **Infrastructure**: SQLite storage, OS integration, TLS, WASM runtime.
- Keep domain logic independent of transport or storage details.

## 3. Dependencies

- Use only approved crates from the workspace where possible (Tokio, Tonic, Axum, Wasmtime, OpenTelemetry, Sqlx, etc.).
- All protobuf/gRPC types must come from generated crates (`dcmaar-pb`); do not handcraft stubs.

## 4. Error Handling & Resilience

- Use `anyhow` for application-level errors and `thiserror`/typed errors at boundaries.
- Prefer explicit error paths over `unwrap`/`expect`.
- Use timeouts and retries when calling remote services (server, plugins, etc.).

## 5. Telemetry & Logging

- Use `tracing` for structured logs and spans, with correlation IDs from the server when available.
- Use `metrics`/Prometheus exporters for internal metrics (CPU, queue sizes, plugin timings).

## 6. Privacy & Security

- Never log raw sensitive payloads; redact or hash where needed.
- Use `rustls` and `tokio-rustls` for TLS; never disable certificate validation outside of clearly marked dev code.

This document is self-contained and defines how to structure code within the DCMaar Agent Daemon.
