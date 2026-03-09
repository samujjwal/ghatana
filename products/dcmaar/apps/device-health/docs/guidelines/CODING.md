# DCMaar – Device Health Extension – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/device-health-extension`.

## 2. Layering & Dependencies

- UI: React components consuming shared UI libraries.
- Logic: uses plugin and connector abstractions; avoid embedding low-level connector logic directly in views.
- Config: use `@dcmaar/config-presets` and Zod validation to manage configuration.

## 3. Testing & Quality

- Use Vitest for unit/integration tests, Playwright for e2e.
- Follow the existing scripts in `package.json` for CI quality gates (lint, type-check, coverage, perf).

This document is self-contained and defines how to structure code in the Device Health extension app.
