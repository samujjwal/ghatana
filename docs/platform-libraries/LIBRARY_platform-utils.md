# Library Spec - @ghatana/platform-utils

Canonical TypeScript utility library for shared frontend helpers.

---

## 1. Purpose & Scope

- Provide small, reusable utilities shared across apps and libraries.
- Centralize class merging, formatting, platform/responsive checks, and runtime accessibility helpers.
- Serve as the only canonical home for cross-cutting frontend utility logic.

From `package.json`:

- Name: `@ghatana/platform-utils`.
- Exports include the root package plus focused utility modules such as `./cn`.
- Depends on `clsx` and `tailwind-merge` for class merging support.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- `cn`: merge utility classes using `clsx` and `tailwind-merge`.
- Generic formatters and value transforms.
- Platform and responsive helpers.
- Runtime accessibility helpers used by shared UI components.

**Non-responsibilities:**

- No React components.
- No product-specific domain logic.
- No compatibility facades for deprecated package names.

---

## 3. Consumers & Typical Usage

- `@ghatana/ui` and `@ghatana/design-system` for class merging and runtime accessibility helpers.
- Product apps for formatting and platform helpers.
- Shared frontend libraries that need low-level utility primitives.

Example (`cn`):

```ts
import { cn } from "@ghatana/platform-utils/cn";

const buttonClass = cn(
  "px-4 py-2 rounded-md",
  isPrimary && "bg-blue-600 text-white",
  disabled && "opacity-50 cursor-not-allowed"
);
```

---

## 4. Dependencies & Relationships

- Should remain low in the dependency graph.
- Safe for UI, design-system, and apps to depend on directly.
- Should not depend on UI components, themes, or product code.

---

## 5. Migration Guidance

- `@ghatana/utils` was a deprecated compatibility wrapper and should not be reintroduced.
- New code should import directly from `@ghatana/platform-utils`.
- Historical docs that mention `@ghatana/utils` should be interpreted as referring to this package.

---

## 6. Usage Guidelines

- Add a helper here only if it is generic, reusable, and product-agnostic.
- Prefer focused modules over a large catch-all index.
- Keep APIs explicit and tree-shakeable.