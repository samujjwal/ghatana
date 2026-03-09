# Guardian – Agent React Native – Coding Guidelines

## 1. Scope

These guidelines apply to `@guardian/agent-react-native`.

## 2. Component & State Design

- Use function components and hooks; avoid class components.
- Use Jotai/Zustand for local state and React Query for server state where appropriate.
- Keep networking, gRPC, and agent logic in dedicated modules; UI components should stay presentational.

## 3. Platform Concerns

- Handle permissions (background activity, notifications) explicitly and visibly.
- Avoid blocking operations on the JS thread; offload heavy work to background tasks.

This document is self-contained and defines how to structure code in `@guardian/agent-react-native`.
