# @ghatana/ui

## Purpose

`@ghatana/ui` provides the internal component implementations that underpin the public `@ghatana/design-system` surface, allowing the platform to keep implementation details modular while preserving a stable higher-level API.

## Dependencies

- `@ghatana/primitives` for low-level layout and interaction building blocks
- `@ghatana/tokens` for token-driven styling
- `@ghatana/ds-schema` and `@ghatana/ds-registry` for design-system schema and registry support
- `clsx` and `tailwind-merge` for component composition

## Usage

Import internal UI surfaces only from platform-owned packages that are expected to depend on them directly:

```tsx
import * as InternalUi from '@ghatana/ui';
import * as Components from '@ghatana/ui/components';
```

Build and validate locally:

```bash
pnpm --filter @ghatana/ui build
pnpm --filter @ghatana/ui test
```

## Public API Surface

- Root package exports from `src/index.ts`
- Subpath exports for `./components`, `./compositions`, `./hooks`, and `./utils`
- Internal component implementations intended to be re-exported through `@ghatana/design-system`