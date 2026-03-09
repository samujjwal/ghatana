# DCMaar – Plugin Extension – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/plugin-extension`, which implements device monitoring plugins using `@dcmaar/plugin-abstractions` and `@dcmaar/types`.

## 2. Layering & Dependencies

- Treat this package as **plugin implementation**:
  - Depends on `@dcmaar/plugin-abstractions` and `@dcmaar/types`.
  - Should not implement new infrastructure; reuse connectors, config, and shared utilities.

## 3. Plugin Design

- Keep each plugin focused on a single responsibility (CPU, memory, battery, etc.).
- Make sampling intervals and thresholds configurable.

## 4. Error Handling & Resilience

- Gracefully handle missing metrics or unsupported environments.
- Prefer typed errors and structured results over throwing untyped exceptions.

This document is self-contained and defines how to structure code in `@dcmaar/plugin-extension`.
