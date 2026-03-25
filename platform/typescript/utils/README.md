# @ghatana/utils — DEPRECATED

> ⚠️ **This package is deprecated and will be removed.**  
> Migrate all imports to use `@ghatana/platform-utils` directly.

---

## Why is this deprecated?

This package contains only a single line of code:

```ts
export * from '@ghatana/platform-utils';
```

It adds no value, creates a confusing extra import option, and increases dependency graph complexity.

**Audit finding:** MED-001 in [SHARED_MODULES_AUDIT_REPORT.md](../../../../SHARED_MODULES_AUDIT_REPORT.md)

---

## Migration

Replace all imports of `@ghatana/utils` with `@ghatana/platform-utils`:

```diff
- import { cn, formatDate } from '@ghatana/utils';
+ import { cn, formatDate } from '@ghatana/platform-utils';
```

```diff
- import type { PlatformInfo } from '@ghatana/utils';
+ import type { PlatformInfo } from '@ghatana/platform-utils';
```

Update your `package.json`:
```diff
- "@ghatana/utils": "workspace:*",
+ "@ghatana/platform-utils": "workspace:*",
```

---

## Removal Timeline

| Milestone | Action |
|-----------|--------|
| Now       | Package marked deprecated in `package.json` |
| Next sprint | Remove all consumer dependencies identified |
| Month 2   | Package removed from workspace |
