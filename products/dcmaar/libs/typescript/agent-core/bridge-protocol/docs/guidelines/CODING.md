# DCMaar Bridge Protocol – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/bridge-protocol`, which defines TypeScript contracts and validation for the desktop ↔ extension bridge.

## 2. Layering & Responsibilities

- Treat this package as a **pure contract and validation layer**.
- Do not include transport code, UI code, or environment-specific logic.

## 3. Types & Validation

- Use TypeScript types for compile-time guarantees.
- Use Zod schemas for runtime validation of incoming/outgoing messages.

## 4. Evolution

- Prefer additive changes (new fields/types) instead of breaking existing shapes.
- Deprecate old fields via comments/docs before removing in a major version.

This document is self-contained and defines how to structure code in `@dcmaar/bridge-protocol`.
