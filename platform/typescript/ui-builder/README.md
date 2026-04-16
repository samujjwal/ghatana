# @ghatana/ui-builder

## Purpose

`@ghatana/ui-builder` provides the foundation for the Ghatana UI Builder stack, including the builder document model, component bindings, actions, validation, preview integration, and React/web code-generation support.

## Dependencies

- `@ghatana/platform-events` for builder-related event flows
- `@ghatana/ds-schema` and `@ghatana/ds-registry` for validated design-system metadata
- `@ghatana/primitives` for low-level UI building blocks
- `zod`, `nanoid`, and `immer` for validation, identifiers, and immutable document updates

## Usage

Import the core builder model from the root package or use a subpath export for React, web, preview, or testing-specific helpers:

```ts
import * as UiBuilder from '@ghatana/ui-builder';
import * as UiBuilderReact from '@ghatana/ui-builder/react';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/ui-builder build
pnpm --filter @ghatana/ui-builder test
```

## Public API Surface

- Root package exports from `src/index.ts`
- Subpath exports for `./react`, `./web`, `./preview`, and `./testing`
- Shared builder document, codegen, validation, and preview infrastructure