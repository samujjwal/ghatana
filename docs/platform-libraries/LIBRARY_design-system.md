# Library Spec – @ghatana/design-system

Unified design system **facade** for the Ghatana platform.

---

## 1. Purpose & Scope

- Provide a **single, opinionated entry point** for design system consumers:
  - Tokens, theme, components, hooks, AI helpers, and accessibility adapters.
- Make it easy for apps to adopt a consistent design system without wiring each low-level library manually.

From `package.json`:

- Entry: `@ghatana/design-system`.
- Description: "Unified design system facade for Ghatana platform (tokens, theme, components, hooks, a11y)".
- Exports: `.`, `./tokens`, `./components`, `./hooks`, `./ai`.
- Depends on `@ghatana/tokens`, `@ghatana/theme`, `@ghatana/ui`, `@ghatana/utils`, `@yappc/accessibility-audit`, `@yappc/ai`.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Re-export a **curated subset** of tokens, theme, UI components, hooks, and AI helpers.
- Provide integrated patterns (e.g., theming + a11y + AI hints) at a higher level than raw UI components.

**Non-responsibilities:**

- Should not contain foundational primitives that belong to `tokens`, `theme`, or `ui`.
- Should not be used by lower-level libraries (to avoid cycles). It is for **apps and docs**, not foundational libs.

---

## 3. Consumers & Typical Usage

- App frontends wanting a **one-line design system import**.
- Storybook instances documenting end-to-end components and patterns.

Example (conceptual):

```tsx
import {
  DesignSystemProvider,
  Button,
} from "@ghatana/design-system/components";

<DesignSystemProvider>
  <Button intent="primary">Run Pipeline</Button>
</DesignSystemProvider>;
```

---

## 4. Dependencies & Relationships

- Directly aggregates `tokens`, `theme`, `ui`, `utils`, and a11y/AI helpers.
- Intended to sit **above** other libraries; avoid using it from within those dependencies.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Boundary clarity:**

  - Some usages may import `@ghatana/design-system` from lower-level libs, which risks cycles.
  - Specs should reinforce: only **apps and high-level packages** import this facade.

- **Overlapping exports:**
  - Ensure the same token/component is not exported under multiple conflicting names.

---

## 6. Enhancement Opportunities

1. **Curated component sets:**

   - Define clear tiers: base components vs. opinionated patterns (e.g., layout shells or dashboards) and export them under distinct namespaces.

2. **App-specific presets:**

   - Provide ready-to-use presets for App Creator, AEP, and TutorPutor shells to reduce boilerplate.

3. **A11y + AI integration patterns:**
   - Offer higher-level patterns that integrate accessibility auditing and AI assistance into common UIs.

---

## 7. Usage Guidelines

- New apps should prefer consuming `@ghatana/design-system` rather than wiring `tokens`, `theme`, `ui`, and `utils` separately—**unless** they have special constraints.
- Avoid importing this library from other shared libs; keep it at the top of the dependency graph.
