# DCMaar – Browser Extension Core – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/browser-extension-core`.

## 2. Pipeline Design

- Model extension behavior as Source–Processor–Sink pipelines.
- Keep sources, processors, and sinks small and composable.

## 3. Browser APIs & Dependencies

- Use `webextension-polyfill` for cross‑browser compatibility.
- Do not mix UI logic into core pipeline primitives.

This document is self-contained and defines how to structure code in `@dcmaar/browser-extension-core`.
