# Migration Guide: @ghatana/dcmaar-shared-ui-charts → @ghatana/charts

> **Status:** DEPRECATED — This package will be removed in v3.0
> **Target:** Migrate all chart imports to `@ghatana/charts`

## Steps

1. Add `@ghatana/charts` to your package.json
2. Replace imports: `from '@ghatana/dcmaar-shared-ui-charts'` → `from '@ghatana/charts'`
3. Review any dcmaar-specific chart configurations
4. Remove `@ghatana/dcmaar-shared-ui-charts` from dependencies
