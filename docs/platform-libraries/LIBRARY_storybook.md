# Library Spec – @ghatana/storybook

Shared Storybook configuration, decorators, and utilities for React UI libraries.

---

## 1. Purpose & Scope

- Provide a **central Storybook workspace** for `@ghatana/ui` and related design-system components.
- Standardize Storybook config, addons, and decorators across projects.

From `package.json`:

- Name: `@ghatana/storybook` (private workspace).
- Description: "Storybook workspace for @ghatana/ui components".
- Depends on `@ghatana/ui`, `@ghatana/theme`, `@ghatana/tokens`, `@ghatana/design-system` and Storybook packages.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Define a **baseline Storybook configuration** (addons, manager, preview).
- Provide common decorators (e.g., ThemeProvider, layout wrappers).
- Host stories for shared components.

**Non-responsibilities:**

- App-specific stories or bespoke Storybook configs.

---

## 3. Consumers & Typical Usage

- Component libraries and apps wanting to **reuse Storybook setup** instead of redefining it.

Conceptual usage:

- App-level `.storybook` can extend or import configuration from `@ghatana/storybook`.

---

## 4. Dependencies & Relationships

- Depends on design system stack (tokens, theme, ui, design-system).
- Intended as a **dev-only** tool, not a runtime dependency.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Per-app Storybook configs:**
  - Some apps may maintain their own Storybook setups.  
    → Aim to converge them on this shared configuration.

---

## 6. Enhancement Opportunities

1. **Config as package:**

   - Export a `storybookConfig` object or helpers for apps to import.

2. **Cross-library story patterns:**
   - Document how to add stories for shared libs (tokens gallery, theme playground, charts).

---

## 7. Usage Guidelines

- Treat `@ghatana/storybook` as the **canonical place** for platform Storybook conventions.
- When adding new addons or decorators, do it here to benefit all consumers.
