# DCMaar Agent Common – Coding Guidelines

## 1. Scope

These guidelines apply to the `dcmaar-agent-common` crate, which hosts shared models and utilities for all Rust-based agent components.

## 2. Dependencies & Layering

- Treat this crate as **domain + shared utility layer**.
- It must not depend on UI, transport, or binary-specific code.
- Use workspace dependencies (`serde`, `tokio`, `sqlx`, `tonic`, etc.) exactly as configured in the workspace.

## 3. Domain Modeling

- Use strong types for IDs, timestamps, and configuration structures.
- Keep domain types stable and backwards compatible, especially when used over the wire.

## 4. Optional Features

- Guard storage, config, gRPC, and WASM-specific logic behind features.
- Keep the default feature set suitable for agent runtimes but avoid unnecessary heavy dependencies.

## 5. Error Handling & Validation

- Use `thiserror` for domain-specific errors and `validator` for input/config validation.
- Avoid panics; return typed errors.

This document is self-contained and defines how to structure code in `dcmaar-agent-common`.
