# YAPPC State Library

Shared frontend state primitives, hooks, and store integration for YAPPC applications.

## Scope
- Shared atoms, selectors, hooks, and state-oriented helpers.
- Product-local state abstractions reused across packages and apps.
- Supporting configuration and state-management utilities.

## Key Areas
- `src/store`: atoms and store wiring.
- `src/hooks`: state-facing hooks.
- Shared configuration helpers for stateful flows.

## Audit Notes
- Keep server state and client state concerns distinct.
- Prefer narrow, typed state contracts over broad mutable objects.