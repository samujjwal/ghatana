# Library Spec – @ghatana/utils

Shared TypeScript utility functions and helpers for frontend projects.

---

## 1. Purpose & Scope

- Provide **small, focused utilities** (formatters, classnames, platform/responsive checks, accessibility helpers).
- Avoid duplicating common logic across apps and libraries.

From `package.json`:

- Name: `@ghatana/utils`.
- Exports: `.`, `./formatters`, `./cn`, `./platform`, `./responsive`, `./accessibility`.
- Depends on `clsx`, `tailwind-merge`.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- `cn`: merge Tailwind/utility classes using `clsx` + `tailwind-merge`.
- `accessibility`: WCAG 2.1 AA helpers (contrast, text color, reduced motion, high contrast, ARIA label cleaning, etc.).
- `formatters`: date/number/string/collection helpers.
- `platform` & `responsive`: environment and viewport/feature checks.

**Non-responsibilities:**

- No React components.
- No product-specific domain logic.

---

## 3. Consumers & Typical Usage

- `@ghatana/ui` – for `cn`, responsive and a11y utilities.
- `@ghatana/theme` – for some responsive/platform information when needed.
- Apps – may import formatters directly.

Example (`cn`):

```ts
import { cn } from "@ghatana/utils/cn";

const buttonClass = cn(
  "px-4 py-2 rounded-md",
  isPrimary && "bg-blue-600 text-white",
  disabled && "opacity-50 cursor-not-allowed"
);
```

---

## 4. Dependencies & Relationships

- Should remain **low in the dependency graph**:
  - Safe for other libraries to consume.
  - Should not depend on internal UI or theme libs.

---

## 5. Gaps, Duplicates, Reuse Misses

- **A11y overlap with @yappc/accessibility-audit:**

  - This library handles runtime helpers, while `accessibility-audit` handles axe-based audits.
  - Need clear docs linking the two (which helper lives where).

- **Potential scattered utilities in apps:**
  - Some apps may still have local `cn` or responsive helpers; these should be migrated here.

---

## 6. Enhancement Opportunities

1. **API docs/examples:**

   - Expand `docs/usage` with concrete examples for each export.

2. **Tree-shaking friendliness:**

   - Ensure each module is independent to minimize bundle impact when importing just one helper.

3. **Platform detection standardization:**
   - Centralize environment checks here instead of ad-hoc `typeof window` in apps.

---

## 7. Usage Guidelines

- When adding a new generic helper, ask: could it be reused by multiple products? If so, it belongs here.
- Prefer small focused modules over a giant `index` that does everything.
