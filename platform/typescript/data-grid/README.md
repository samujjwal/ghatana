# @ghatana/data-grid

## Purpose

`@ghatana/data-grid` provides the shared platform data-grid components used across Ghatana products for accessible table rendering, sorting, filtering, and pagination.

## Dependencies

- `@ghatana/design-system` for shared UI primitives
- `@ghatana/platform-utils` for shared utility helpers
- `@tanstack/react-table` for table state and rendering support

## Usage

Install through the workspace and import from the package entry point:

```tsx
import { DataGrid, type ColumnDef } from '@ghatana/data-grid';

const columns: ColumnDef<{ id: string; name: string }>[] = [
  {
    accessorKey: 'name',
    header: 'Name',
  },
];
```

Build and validate locally:

```bash
pnpm --filter @ghatana/data-grid build
pnpm --filter @ghatana/data-grid test
```

## Public API Surface

- `DataGrid` and `Pagination`
- Shared table types including `ColumnDef`, sort, filter, and pagination state types
- Typed package entry point at `src/index.ts`