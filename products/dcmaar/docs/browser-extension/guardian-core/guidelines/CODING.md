# Guardian – Browser Extension – Coding Guidelines

## 1. Scope

These guidelines apply to `@yappc/guardian-browser-extension`.

## 2. Design Principles

- Respect MV3 constraints (service worker lifecycle, messaging) and keep logic modular.
- Use `@dcmaar/browser-extension-core` for common bridge logic instead of duplicating.
- Avoid long-running work in the UI thread; delegate to background where possible.

## 3. UI & State

- Keep React components presentational and use small hooks for state and side effects.

This document is self-contained and defines how to structure code in the Guardian Browser Extension.
