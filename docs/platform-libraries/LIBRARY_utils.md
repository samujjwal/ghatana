# Legacy Library Note - @ghatana/utils

`@ghatana/utils` was a deprecated compatibility wrapper over `@ghatana/platform-utils` and has been removed from the active workspace.

---

## 1. Status

- Canonical replacement: `@ghatana/platform-utils`
- Wrapper status: removed after internal consumer migration
- Historical references to `@ghatana/utils` should be migrated to `@ghatana/platform-utils`

---

## 2. Migration

Replace imports of `@ghatana/utils` with `@ghatana/platform-utils`:

```ts
import { cn } from "@ghatana/platform-utils/cn";
import { formatDate } from "@ghatana/platform-utils";
```

---

## 3. Canonical Spec

See `LIBRARY_platform-utils.md` for the canonical utility library contract.

---

## 4. Compatibility Policy

- Do not add new dependencies on `@ghatana/utils`.
- Remove any residual aliases or docs that still treat the wrapper as canonical.
