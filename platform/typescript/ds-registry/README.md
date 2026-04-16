# @ghatana/ds-registry

## Purpose

`@ghatana/ds-registry` provides the design-system registry used to register, query, validate, and compare component, token, theme, and pattern metadata across the Ghatana platform.

## Dependencies

- `@ghatana/ds-schema` for validated registry contracts
- `zod` for schema-backed validation and parsing

## Usage

Import registry store helpers or use the subpath exports for narrower surfaces:

```ts
import { createRegistryStore, getRegistryStore } from '@ghatana/ds-registry';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/ds-registry build
pnpm --filter @ghatana/ds-registry test
```

## Public API Surface

- Registry store creation and query helpers from the root package
- Subpath exports for `./registry`, `./validation`, and `./queries`
- Shared registry entry types for components, tokens, themes, and patterns