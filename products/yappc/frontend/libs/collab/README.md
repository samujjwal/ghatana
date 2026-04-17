# YAPPC Frontend Collaboration Library

Shared collaboration primitives for realtime editing, shared state, and multi-user workflows in YAPPC frontend experiences.

## Scope
- Collaboration-specific types, helpers, and service integration.
- Shared logic for multi-user state synchronization.
- Product-local adapters used by collaborative UI surfaces.

## Key Areas
- Realtime collaboration helpers.
- Shared collaboration state or protocol types.
- Integration points with YAPPC UI surfaces.

## Audit Notes
- Keep transport and domain coordination separate.
- Make conflict, sync, and failure behavior explicit and testable.