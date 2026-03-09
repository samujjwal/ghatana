# DCMaar – Config Presets – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/config-presets`.

## 2. Layering

- Treat this package as a **pure config and schema layer**.
- Do not introduce environment-specific IO or side effects.

## 3. Schema & Preset Design

- Define Zod schemas that match actual runtime configuration needs.
- Keep presets small, composable, and documented.

This document is self-contained and defines how to structure code in `@dcmaar/config-presets`.
