# DCMaar Framework – Agent Daemon (agent-core-rs) – Design & Architecture

## 1. Purpose

The Agent Daemon (`agent-core-rs`) provides the **core runtime for the DCMaar agent**. It runs on user machines, captures local activity and telemetry, executes sandboxed plugins, and communicates securely with the DCMaar server and desktop UI.

## 2. Responsibilities

- Establish and maintain secure connections to the DCMaar server (mTLS, gRPC/HTTP).
- Capture events and metrics from the OS, applications, and configured connectors.
- Execute WASM-based plugins with explicit capabilities and resource limits.
- Persist a local queue/WAL for at-least-once delivery and offline buffering.
- Expose a small local HTTP API (Axum) for health, configuration, and debugging.

## 3. Architectural Position

- **Language & Runtime**: Rust (Tokio async runtime).
- **Transport**: gRPC via `tonic`, HTTP via `axum` + `tower`/`tower-http`.
- **Data & Storage**: SQLite via `sqlx` for local state and queues.
- **Plugins**: WebAssembly via `wasmtime`/`wasmtime-wasi`.
- **Telemetry**: `tracing`, `metrics`, OpenTelemetry exporters.

The agent daemon is the **edge component** in the DCMaar architecture, paired with the DCMAAR server stack (Java services and Node.js APIs), desktop app (Rust+Tauri+React), and browser extension (WebExtensions).

## 4. Key Components

- Core library `agent_rs` (`src/lib.rs`) exposes the agent runtime.
- Internal crates (e.g., `agent-http`, `agent-auth`, `agent-telemetry`, `agent-types`) structure concerns into HTTP, auth, telemetry, and domain model layers.
- `dcmaar_proto` provides generated protobuf types for contracts with the server.

## 5. Design Constraints

- Follow repository-wide rules:
  - Contracts-first via protobuf (`dcmaar-pb`); no hand-written client stubs.
  - mTLS for all server communication; certificates via workspace scripts/config.
  - Resource budgets: CPU and memory overhead must stay within agent budgets.
  - Privacy by default: capture is scoped and filtered; no PII in logs.

This document is self-contained and summarizes the current/planned architecture for the DCMaar Agent Daemon.
