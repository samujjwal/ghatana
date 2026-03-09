# DCMaar – Shared UI Core – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/shared-ui-core`.

## 2. Layering

- Keep this package framework-agnostic; do not import React or other UI frameworks directly.
- Provide tokens, types, hooks, and utilities that higher-level UI packages build on.

## 3. Token & Type Design

- Represent tokens as typed objects with clear naming conventions.
- Keep exported APIs small and composable.

This document is self-contained and defines how to structure code in `@dcmaar/shared-ui-core`.
