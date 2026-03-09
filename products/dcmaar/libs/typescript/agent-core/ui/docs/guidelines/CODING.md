# DCMaar – Agent UI – Coding Guidelines

## 1. Scope

These guidelines apply to `@dcmaar/agent-ui`.

## 2. Component Design

- Keep components small, composable, and focused on presentation.
- Accept data and callbacks via props; avoid embedding business logic.

## 3. Dependencies

- React/ReactDOM as peers; do not depend on app-specific state/routers directly.
- Use Radix UI, lucide-react, and Tailwind utilities per existing patterns.

This document is self-contained and defines how to structure code in `@dcmaar/agent-ui`.
