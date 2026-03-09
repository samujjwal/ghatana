# Guardian – Parent Dashboard – Coding Guidelines

## 1. Scope

These guidelines apply to `@guardian/parent-dashboard`.

## 2. Component & State Design

- Use React function components with hooks.
- Use Jotai for app state where appropriate; keep state domains small and localized.
- Use React Router for navigation; avoid ad-hoc navigation patterns.

## 3. API Layer

- Centralize HTTP calls (Axios) with typed responses and Zod validation.
- Do not call backend APIs directly from deeply nested components; use hooks or data modules.

This document is self-contained and defines how to structure code in the Guardian Parent Dashboard.
