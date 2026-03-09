# DCMaar – Browser Extension UI – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/browser-extension-ui`.

## 2. Dependencies & Layering

- React-based UI library that depends on `@dcmaar/browser-extension-core`.
- Keep this package focused on presentation and interaction logic; no direct data fetching or pipeline control.

## 3. Component Design

- Prefer small, composable components.
- Accept data via props; do not hardcode platform-specific details.

This document is self-contained and defines how to structure code in `@dcmaar/browser-extension-ui`.
