# Library Spec – @yappc/accessibility-audit

Shared accessibility auditing helpers and components (axe-core-based) for React apps.

---

## 1. Purpose & Scope

- Provide tooling to **run accessibility audits** (via axe-core / @axe-core/react) in:
  - Development (devtools/overlays).
  - Tests (Vitest/Jest).
  - CI pipelines.

From `package.json`:

- Name: `@yappc/accessibility-audit`.
- Depends on `axe-core` and `@axe-core/react`.
- Provides scripts, examples (including a CLI via `examples/cli.ts`).

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Expose utilities and components to **run a11y audits** against React trees.
- Provide **reports** that can be surfaced in CI logs or local dev.

**Non-responsibilities:**

- Generic runtime accessibility helpers for contrast, ARIA, motion, etc. (those live in `@ghatana/utils/accessibility`).
- UI styling or tokens.

---

## 3. Consumers & Typical Usage

- Component libraries (`@ghatana/ui`, `@ghatana/design-system`) in Storybook/test environments.
- App test suites wanting to **enforce a11y** on key screens.

Conceptual usage examples:

- Wrap components with axe-based auditing for Storybook stories.
- Run `pnpm accessibility-audit:cli` to scan a page or component tree.

---

## 4. Dependencies & Relationships

- Uses axe-core APIs, tightly coupled to DOM/React environments.
- Imported by `@ghatana/design-system` to integrate a11y into the design system story surface.
- Complements, but does not replace, `@ghatana/utils/accessibility`.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Overlap with utils accessibility helpers:**

  - Both `@ghatana/utils/accessibility` and this library deal with WCAG concerns.  
    Contract should be:
    - `utils`: lower-level helpers (contrast calculations, prefers-reduced-motion, ARIA labels).
    - `accessibility-audit`: axe-core integration and audit/report orchestration.

- **Missing standardized report shape:**
  - Ensure audit results are exposed in a **typed, reusable shape** so tests and CI can interpret them in a consistent way.

---

## 6. Enhancement Opportunities

1. **Storybook addon integration:**

   - Provide ready-made Storybook decorators or addon config for a11y audits.

2. **CI recipes:**

   - Document and/or script examples for running audits in CI across key routes.

3. **Severity / policy mapping:**
   - Allow mapping axe rules to org-specific severity levels (e.g., fail build vs warn).

---

## 7. Usage Guidelines

- Use this library for **formal audits**; use `@ghatana/utils/accessibility` for everyday runtime checks and calculations in components.
- When adding new audit capabilities, consider how their results will be consumed (local dev vs CI vs dashboards).
