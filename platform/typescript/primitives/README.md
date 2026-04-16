# @ghatana/primitives

## Purpose

`@ghatana/primitives` provides the internal layout and interaction primitives that sit beneath higher-level Ghatana UI packages, giving the design-system stack token-driven building blocks without exposing product-specific behavior.

## Dependencies

- `@ghatana/tokens` for token-driven styling inputs
- `clsx` and `tailwind-merge` for composition and class merging
- React peer dependencies for host rendering

## Usage

Import primitives from the root package or the subpath exports:

```tsx
import * as Primitives from '@ghatana/primitives';
import * as Layout from '@ghatana/primitives/layout';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/primitives build
pnpm --filter @ghatana/primitives test
```

## Public API Surface

- Root package exports from `src/index.ts`
- Subpath exports for `./layout` and `./interaction`
- Shared token-driven primitive components used by higher-level UI packages