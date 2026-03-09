# DCMaar – Libraries – Design & Architecture

## 1. Purpose

This document describes the **library layer** of the DCMaar platform: Rust agent-framework crates and TypeScript libraries used by apps and frameworks.

## 2. Responsibilities

- Provide reusable building blocks for agents, backends, and UIs.
- Encapsulate cross-cutting concerns (types, connectors, plugins, shared UI) behind stable APIs.

## 3. Architectural Position

- Rust libraries under `libs/rust/agent-framework/*` implement the core agent runtime, plugins, and configuration types.
- TypeScript libraries under `libs/typescript/*` cover agent-core, browser-extension-core/ui, connectors, plugins, shared UI, and types.

This document is self-contained and summarizes the role of libraries in the DCMaar architecture.
