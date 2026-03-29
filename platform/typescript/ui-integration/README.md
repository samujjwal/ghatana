# @ghatana/ui-integration

Higher-level UI integration helpers for AI features, collaboration, and page-builder composition.

## Purpose

- compose shared UI primitives into integration-oriented helpers
- provide a reusable seam between apps and the lower-level platform UI packages

## Dependency role

This package sits above `@ghatana/design-system`, `@ghatana/theme`, `@ghatana/tokens`, and `@ghatana/platform-utils` in the dependency graph.

## Boundaries

- no app routing or product-owned page logic
- no direct ownership of foundational utility or token concerns
