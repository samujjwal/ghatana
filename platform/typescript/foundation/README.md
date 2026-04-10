# `platform/typescript/foundation`

Foundation-level TypeScript utilities for the Ghatana platform.

## Packages

| Package | Name | Description |
|---------|------|-------------|
| [`platform-utils/`](./platform-utils/) | `@ghatana/platform-utils` | Shared utility functions (string, date, validation, formatting) used across all Ghatana TypeScript packages |

## Purpose

The `foundation/` directory holds generic utilities that have no product-specific concerns and no dependency on React, browser APIs, or other platform packages. These may safely be imported by any layer of the system.
