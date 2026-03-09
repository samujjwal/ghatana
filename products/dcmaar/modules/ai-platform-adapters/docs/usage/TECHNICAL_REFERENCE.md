# DCMaar Agent Daemon – Technical Reference

## 1. Overview

This reference summarizes the main technical aspects of the DCMaar Agent Daemon runtime.

## 2. Core Concepts (Conceptual)

- Agent runtime orchestrating capture, plugins, and communication.
- Local storage (SQLite) for queues, state, and metadata.
- WASM-based plugin system via Wasmtime.
- Telemetry and metrics exported via OpenTelemetry and Prometheus.

## 3. Intended Consumers

- Platform engineers integrating agent behavior with the server, desktop app, and observability stack.

This technical reference is self-contained and describes the primary surfaces of the DCMaar Agent Daemon.
