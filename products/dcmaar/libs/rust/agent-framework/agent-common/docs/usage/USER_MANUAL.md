# DCMaar Agent Common – User Manual

## 1. Audience

This manual is for Rust engineers building agent-related binaries and libraries.

## 2. Basic Usage

1. Add `dcmaar-agent-common` as a dependency via the workspace.
2. Use its domain types for configuration, IDs, and events.
3. Enable optional features (`storage`, `config`, `grpc`, `wasm`) only when required.

## 3. Best Practices

- Reuse shared types instead of duplicating domain models.
- Keep this crate free of binary-specific behavior.

This manual is self-contained and explains how to use `dcmaar-agent-common` in typical flows.
