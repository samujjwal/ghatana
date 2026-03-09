# DCMaar Common – Coding Guidelines

## 1. Scope

These guidelines apply to the `dcmaar-common` crate, which provides shared utilities and error types for the DCMaar agent framework.

## 2. Dependencies & Layering

- Keep this crate focused on cross-cutting concerns (errors, logging helpers, small utilities).
- Do not add dependencies on transport, storage, or UI-specific crates.

## 3. Error Types

- Define reusable error enums and types used across agent framework crates.
- Use `thiserror` for ergonomic error definitions.

## 4. Logging & Tracing Helpers

- Provide helpers that wrap `tracing` and `tracing-subscriber` configuration.
- Ensure defaults are safe for both dev and production environments.

This document is self-contained and defines how to structure code in `dcmaar-common`.
