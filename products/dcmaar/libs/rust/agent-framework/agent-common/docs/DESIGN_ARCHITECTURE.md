# DCMaar Agent Framework – dcmaar-agent-common – Design & Architecture

## 1. Purpose

`dcmaar-agent-common` provides **shared models, types, and utilities** used across DCMaar agent implementations (agent daemon, desktop Tauri backend, and other Rust-based agent components).

## 2. Responsibilities

- Define core agent domain types (IDs, timestamps, events, configuration models).
- Provide validation helpers for configuration and runtime state.
- Offer optional integrations with storage (SQLx), gRPC/protobuf, and WASM.

## 3. Architectural Position

- Language: Rust 2021.
- Part of the `agent-framework` workspace and used by multiple binaries (agent-daemon, desktop backend, etc.).
- Serves as the **domain/model layer** for agent-oriented code; does not implement transport or UI.

## 4. Feature Flags (from Cargo)

- `storage` – enables SQLx-backed persistence helpers.
- `config` – enables Figment-based configuration loading.
- `grpc` – enables Prost/Tonic types and helpers.
- `wasm` – enables Wasmtime-related helpers.
- `full` – convenience for enabling all features.

## 5. Design Constraints

- Keep types **backwards compatible** wherever they cross process boundaries.
- Separate pure domain logic from IO; storage and gRPC remain optional features.

This document is self-contained and describes the architecture and role of `dcmaar-agent-common` in the DCMaar agent framework.
