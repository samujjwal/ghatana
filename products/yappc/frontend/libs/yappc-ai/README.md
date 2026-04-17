# YAPPC AI Library

Shared AI runtime, UI adapters, hooks, and package-level integrations for YAPPC frontend experiences.

## Scope
- AI hooks, clients, orchestration helpers, and reusable UI surfaces.
- Product-specific AI contracts and interaction patterns.
- Supporting providers, notifications, messaging, and recommendation utilities.

## Key Areas
- `src/hooks`: shared AI-facing hooks.
- `src/core`: AI providers, types, and service wiring.
- `src/ui` and adjacent modules: reusable AI UI surfaces.

## Audit Notes
- Keep provider integration, UI state, and product orchestration separate.
- Document assumptions around rate limits, retries, and degraded behavior.