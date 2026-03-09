# DCMaar – Agent Types – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/agent-types`.

## 2. Design Principles

- **Types first**: add or change types before implementing new agent features.
- **Compatibility**: evolve contracts additively; avoid breaking existing fields.
- **Serialization-friendly**: keep types JSON-serializable and align with Rust/grpc models.

## 3. Practices

- Prefer **branded/opaque IDs** over plain strings where appropriate.
- Keep domain types small and focused; extract shared pieces into reusable utility types.
- Avoid importing app-specific modules; this package must be dependency-light and reusable.

This document is self-contained and defines how to structure code in `@dcmaar/agent-types`.
