# YAPPC Chat Library

Shared chat and messaging functionality for YAPPC frontend packages.

## Scope
- Chat UI primitives, transport helpers, and shared state hooks.
- Product-local chat behaviors reused across multiple frontend surfaces.
- Supporting message types and interaction contracts.

## Key Areas
- Chat components and hooks.
- Messaging types and transport adapters.
- Shared helpers for rendering and state transitions.

## Audit Notes
- Keep chat transport separate from presentation concerns.
- Ensure message and session state remain explicit and recoverable.