# Library Spec – @ghatana/ui

Global React UI component library for the Ghatana platform.

---

## 1. Purpose & Scope

- Provide **WCAG-conscious, Atomic Design-based React components** (atoms, molecules, organisms) for use across products.
- Encapsulate common layout, form, navigation, and feedback patterns.

From `package.json`:

- Entry: `@ghatana/ui`.
- Description: "Global UI component library for Ghatana platform - WCAG AA compliant, Atomic Design".
- Exports: main index plus `./atoms/*`, `./molecules/*`, `./organisms/*`, `./hooks`.
- Peer deps: `@ghatana/theme`, `@ghatana/tokens`, `@ghatana/utils`, React, React DOM, React Router DOM.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Implement **reusable UI components** (buttons, inputs, modals, layouts, etc.)
- Respect **tokens** and **theme** for colors, spacing, and typography.
- Use `@ghatana/utils` for classnames (`cn`) and accessibility helpers where needed.

**Non-responsibilities:**

- No direct API calls; components receive data and callbacks as props.
- No product-specific flows; those belong in app-level components.

---

## 3. Consumers & Typical Usage

- Apps: `@ghatana/ui` is the primary source of UI primitives.
- `@ghatana/charts`: uses UI components for wrappers around chart primitives.
- `@ghatana/design-system`: may re-export curated sets of components.

Example usage (conceptual):

```tsx
import { Button } from "@ghatana/ui/atoms/Button";

<Button variant="primary" onClick={handleClick}>
  Save
</Button>;
```

---

## 4. Dependencies & Relationships

- Depends on `@ghatana/theme`, `@ghatana/tokens`, `@ghatana/utils`.
- Used by Storybook (`@ghatana/storybook`) for component docs.
- Should not depend directly on `@ghatana/design-system` (design-system sits above it).

---

## 5. Gaps, Duplicates, Reuse Misses

- **Classname utility duplication (historical):**

  - `cn` was migrated from `@yappc/ui/utils/cn` into `@ghatana/utils`.
  - Ensure all UI components now import `cn` from `@ghatana/utils`, not re-implementing it.

- **Accessibility helpers split:**

  - Some runtime a11y helpers live in `@ghatana/utils/accessibility`.
  - UI components should **reuse** those helpers instead of custom calculations.

- **React version mismatch:**
  - UI peers React 18.2.0 while other libs peer React 19.2.0.  
    Aligning these versions will simplify consumer configuration.

---

## 6. Enhancement Opportunities

1. **Component coverage audit:**

   - Compare App Creator/AEP UI designs against existing components; identify missing primitives that should live here.

2. **A11y contracts:**

   - Define and document guarantees per component type (e.g., focus traps, ARIA patterns) using helpers from `@ghatana/utils` and `@yappc/accessibility-audit`.

3. **Themed variants:**
   - Provide standardized sizing, density, and tone variants driven by `@ghatana/theme`.

---

## 7. Usage Guidelines

- Prefer `@ghatana/ui` components for all new UI in apps; avoid local one-off components where a shared primitive could be used.
- When adding a new component, first check if it belongs here vs. as a domain-specific component in a product.
