# Migration Guide: @ghatana/dcmaar-shared-ui-core → Platform Libs

> **Status:** DEPRECATED — This package will be removed in v3.0
> **Target:** Migrate all imports to `@ghatana/tokens` and `@ghatana/utils`

## Migration Map

| dcmaar-shared-ui-core Import | Platform Replacement |
|------------------------------|---------------------|
| `@ghatana/dcmaar-shared-ui-core/tokens` | `@ghatana/tokens` |
| `@ghatana/dcmaar-shared-ui-core/tokens` → colors | `@ghatana/tokens/colors` |
| `@ghatana/dcmaar-shared-ui-core/tokens` → spacing | `@ghatana/tokens/spacing` |
| `@ghatana/dcmaar-shared-ui-core/tokens` → typography | `@ghatana/tokens/typography` |
| `@ghatana/dcmaar-shared-ui-core/utils` | `@ghatana/utils` |
| `@ghatana/dcmaar-shared-ui-core/types` | Keep in dcmaar (product-specific) |
| `@ghatana/dcmaar-shared-ui-core/hooks` | Keep in dcmaar (product-specific) |

## Steps

1. Add `@ghatana/tokens` and `@ghatana/utils` to your package.json
2. Replace token imports: `from '@ghatana/dcmaar-shared-ui-core/tokens'` → `from '@ghatana/tokens'`
3. Replace utility imports: `from '@ghatana/dcmaar-shared-ui-core/utils'` → `from '@ghatana/utils'`
4. Product-specific types and hooks remain in this package until further consolidation
