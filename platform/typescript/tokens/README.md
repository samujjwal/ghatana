# @ghatana/tokens

Framework-agnostic design tokens for the Ghatana platform.

## Purpose

- define canonical colors, spacing, typography, shadows, borders, breakpoints, transitions, and z-index values
- act as the lowest-level design package in the TypeScript UI graph

## Dependency role

This package should stay at the bottom of the frontend dependency graph.

- it may be consumed by `@ghatana/theme` and other UI packages
- it should not depend on higher-level UI packages

## Boundaries

- no React code
- no product-specific visual decisions
