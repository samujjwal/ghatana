# @ghatana/ds-schema

## Purpose

`@ghatana/ds-schema` provides the DTCG-aligned schema layer for the Ghatana design system, including token, component, theme, pattern, compatibility, and validation contracts used by higher-level design-system packages.

## Dependencies

- `zod` for runtime schema definition and validation

## Usage

Import schema contracts from the root package or from the relevant subpath export:

```ts
import * as DesignSystemSchema from '@ghatana/ds-schema';
import { z } from '@ghatana/ds-schema';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/ds-schema build
pnpm --filter @ghatana/ds-schema test
```

## Public API Surface

- Token schemas under `./tokens`
- Component schemas under `./components`
- Theme, pattern, compatibility, and validation subpath exports
- Root package exports from `src/index.ts`, including a re-exported `zod` namespace