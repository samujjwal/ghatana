# @ghatana/platform-utils

Canonical TypeScript utility package for shared frontend helpers.

## Purpose

- provide low-level reusable helpers used across apps and platform packages
- centralize formatting, class merging, platform checks, responsive helpers, and accessibility utilities

## Boundaries

- no UI components
- no compatibility wrappers for deprecated package names
- no product-specific business logic

## Usage guidance

- prefer direct imports from this package over local utility duplication
- keep helpers small, generic, and tree-shakeable
