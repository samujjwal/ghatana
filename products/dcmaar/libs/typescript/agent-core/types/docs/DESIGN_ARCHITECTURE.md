# DCMaar – Agent Types – Design & Architecture

## 1. Purpose

`@dcmaar/agent-types` provides **shared TypeScript types** for all DCMaar agent applications (desktop, browser extension, mobile guardians, etc.). It acts as the canonical TypeScript representation of agent domain concepts.

## 2. Responsibilities

- Define strongly-typed IDs, enums, and domain models for agent features.
- Keep JSON-serializable wire types aligned with Rust agent-framework and bridge-protocol contracts.
- Provide reusable utility types for configuration, telemetry, and plugin contracts.

## 3. Architectural Position

- Pure TypeScript library compiled with `tsc` to `dist/`.
- Zero runtime dependencies beyond small utility libs such as `type-fest`.
- Consumed by: Guardian agent apps, bridge-protocol, plugin abstractions, and UI surfaces that need agent-level types.

This document is self-contained and summarizes the architecture and role of `@dcmaar/agent-types`.
