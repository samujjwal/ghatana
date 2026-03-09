# Migration Guide: @ghatana/dcmaar-shared-ui-tailwind → @ghatana/ui

> **Status:** DEPRECATED — This package will be removed in v3.0
> **Target:** Migrate all component imports to `@ghatana/ui`

## Component Migration Map

| dcmaar Component | Platform Replacement |
|-----------------|---------------------|
| `Button` | `@ghatana/ui` → `Button` |
| `Card` | `@ghatana/ui` → `Card` |
| `Input` | `@ghatana/ui` → `Input` |
| `Select` | `@ghatana/ui` → `Select` |
| `Toggle` | `@ghatana/ui` → `Toggle` |
| `Badge` | `@ghatana/ui` → `Badge` |
| `Spinner` | `@ghatana/ui` → `Spinner` |
| `Skeleton` | `@ghatana/ui` → `Skeleton` |

## Steps

1. Add `@ghatana/ui` to your package.json
2. Replace imports: `from '@ghatana/dcmaar-shared-ui-tailwind'` → `from '@ghatana/ui'`
3. Review any dcmaar-specific prop extensions and file issues for platform parity
4. Remove `@ghatana/dcmaar-shared-ui-tailwind` from dependencies
