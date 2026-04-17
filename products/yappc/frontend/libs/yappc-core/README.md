# YAPPC Core Frontend Library

Shared core frontend primitives, configuration, and foundational hooks for YAPPC applications.

## Scope
- Foundational API hooks and shared frontend infrastructure.
- Configuration loading, common types, and core runtime utilities.
- Product-local base behavior reused across several YAPPC packages.

## Key Areas
- `src/api`: shared frontend API layer.
- `src/config`: configuration and task-loading support.
- Common types and core helpers.

## Audit Notes
- Keep this library generic within the YAPPC frontend domain.
- Avoid feature-specific drift into foundational utilities.