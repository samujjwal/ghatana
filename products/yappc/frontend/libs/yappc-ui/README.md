# YAPPC UI Library

Shared product UI components, hooks, and adapters used throughout YAPPC frontend applications.

## Scope
- Reusable product-facing components beyond the core design system.
- Shared hooks and adapters that bind UI surfaces to YAPPC-specific behaviors.
- Integration helpers for complex or composite product widgets.

## Key Areas
- `src/components`: reusable UI modules and hooks.
- Product-specific adapters and composite components.
- Shared test utilities and integration coverage.

## Audit Notes
- Keep reusable UI surfaces independent from page-specific orchestration.
- Promote only stable, well-typed interfaces for downstream consumption.