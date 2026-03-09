# Guardian – Agent Desktop – Design & Architecture

## 1. Purpose

`guardian-agent-desktop` is a **desktop usage collector plugin** for the DCMAAR agent daemon. It captures foreground application usage, basic activity signals, and metadata from desktop operating systems (Windows, macOS, Linux).

## 2. Responsibilities

- Collect OS-specific desktop activity and usage metrics.
- Normalize events into DCMAAR agent data models.
- Persist local state via `agent-storage` and forward events via `agent-connectors`.

## 3. Architectural Position

- Rust crate compiled as `cdylib` plugin, loaded by the DCMAAR agent daemon.
- Depends on:
  - `agent-plugin`, `agent-types`, `agent-storage` from the agent daemon framework.
  - `agent-connectors` (Rust replica of TS connectors) for outbound transport.
  - `tokio`, `sqlx`, `reqwest`, `tokio-tungstenite`, `tracing`, `thiserror`, `anyhow`.
- Uses platform-specific dependencies (Windows, macOS, Linux) for system integrations.

This document is self-contained and summarizes the architecture and role of the Guardian Agent Desktop plugin.
