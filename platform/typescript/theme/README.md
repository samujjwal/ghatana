# @ghatana/theme

Shared theme system for the Ghatana frontend platform.

## Purpose

- provide shared theme primitives, providers, and schema helpers
- translate design tokens into runtime theme behavior for consumers

## Dependency role

- depends on `@ghatana/tokens`
- should not depend on `@ghatana/design-system`

## Boundaries

- no product-specific branding logic beyond configurable presets
- no component ownership
