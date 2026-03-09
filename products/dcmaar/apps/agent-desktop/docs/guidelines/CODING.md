# Guardian – Agent Desktop – Coding Guidelines

## 1. Scope

These guidelines apply to the `guardian-agent-desktop` Rust plugin.

## 2. Design Principles

- **Plugin isolation**: implement the agent plugin traits without leaking OS-specific details to callers.
- **Event-time semantics**: timestamp events at source and preserve original timestamps through storage and transport.
- **Non-blocking**: use `tokio` async APIs; avoid blocking calls on async tasks.

## 3. Structure

- Keep OS-specific code in dedicated modules behind a small trait-based abstraction.
- Use `agent-types` models for payload shape and `agent-connectors` for sending.
- Log via `tracing` with structured, non-PII fields.

This document is self-contained and defines how to structure code in the Guardian Agent Desktop plugin.
