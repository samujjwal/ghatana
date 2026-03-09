# DCMaar Desktop – Coding Guidelines

## 1. Scope

These guidelines apply to both the React+TypeScript UI and the Tauri Rust backend.

## 2. Frontend (React/TypeScript)

- Use React function components with hooks.
- Use MUI, TanStack Query, and Jotai according to existing patterns.
- Keep data-fetching logic in dedicated hooks or query utilities.
- Type everything with strict TypeScript types.

## 3. Backend (Rust/Tauri)

- Use async Rust (`tokio`) for I/O and gRPC.
- Keep Tauri commands thin: delegate logic to internal modules.
- Use `tonic` and generated protobuf types for server communication.

## 4. Cross-Cutting Concerns

- Respect separation of concerns: UI does not implement business rules that belong in server/agent.
- Use shared contracts and types where possible to avoid drift.

This document is self-contained and defines how to structure code in the DCMaar Desktop app.
