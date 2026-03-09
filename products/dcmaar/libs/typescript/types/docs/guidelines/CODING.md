# DCMaar – Types – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/types`.

## 2. Layering

- Treat this package as the **foundation for shared TS types**.
- Do not introduce runtime dependencies or environment-specific logic.

## 3. Type Design

- Prefer explicit types and enums over `any` or broad unions.
- Keep exported types stable; deprecate before removing.

This document is self-contained and defines how to structure code in `@dcmaar/types`.
