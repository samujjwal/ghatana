# Ghatana Shared TypeScript Libraries – Index & Layering

This index summarizes the **shared TypeScript libraries under `libs/typescript`**, their intended responsibilities, layering, overlaps, and enhancement opportunities.

Libraries:

- `@yappc/accessibility-audit`
- `@ghatana/api`
- `@ghatana/charts`
- `@ghatana/design-system`
- `@ghatana/org-events`
- `@ghatana/realtime`
- `@ghatana/state`
- `@ghatana/storybook`
- `@ghatana/test-utils`
- `@ghatana/theme`
- `@ghatana/tokens`
- `@ghatana/ui`
- `@ghatana/utils`

Each library has a dedicated spec file:

- `LIBRARY_tokens.md`
- `LIBRARY_theme.md`
- `LIBRARY_ui.md`
- `LIBRARY_design-system.md`
- `LIBRARY_accessibility-audit.md`
- `LIBRARY_utils.md`
- `LIBRARY_api.md`
- `LIBRARY_realtime.md`
- `LIBRARY_state.md`
- `LIBRARY_charts.md`
- `LIBRARY_org-events.md`
- `LIBRARY_storybook.md`
- `LIBRARY_test-utils.md`

---

## 1. High-Level Layering

Intended layering (from low-level to high-level):

1. **Tokens** – `@ghatana/tokens`  
   Framework-agnostic design tokens (colors, spacing, typography, shadows, etc.).

2. **Theme** – `@ghatana/theme`  
   Theme objects, providers, hooks, brand presets built on tokens.

3. **Utils** – `@ghatana/utils`  
   Cross-cutting utilities (formatters, platform/responsive, classnames, accessibility helpers).

4. **UI Components** – `@ghatana/ui`  
   Atomic Design-based React components using tokens, theme, utils.

5. **Design System Facade** – `@ghatana/design-system`  
   Opinionated facade combining tokens, theme, UI, utils, a11y audit, and AI hooks.

6. **Visualization** – `@ghatana/charts`  
   Chart primitives built on top of `recharts` + theme + UI.

7. **API & State** – `@ghatana/api`, `@ghatana/state`, `@ghatana/realtime`  
   Fetch-based client, state helpers, and realtime helpers for apps and libraries.

8. **Testing & Storybook** – `@ghatana/test-utils`, `@ghatana/storybook`, `@yappc/accessibility-audit`  
   Shared test utilities, Storybook configuration, and accessibility auditing.

9. **Domain Contracts** – `@ghatana/org-events`  
   Protobuf-generated DTOs and event contracts.

---

## 2. Cross-Cutting Observations

- **Accessibility spread across libs:**

  - `@ghatana/utils` exposes generic accessibility helpers (contrast, reduced motion, ARIA label helpers).
  - `@yappc/accessibility-audit` focuses on axe-core based auditing and CI/test integration.
  - **Contract:** keep runtime component-level a11y helpers in `utils`, and deep auditing/reporting in `accessibility-audit`.

- **Design system layering:**

  - `tokens` → `theme` → `ui` → `design-system` is the intended stack.
  - Apps should generally depend on `design-system` **or** a curated subset, not mix random imports from all four libraries unless necessary.

- **Storybook & test utilities as shared infra:**

  - `@ghatana/storybook` should centralize Storybook config for all component libs.
  - `@ghatana/test-utils` should be the preferred source for React Testing Library setups.

- **State & realtime alignment:**

  - `@ghatana/state` and `@ghatana/realtime` should share patterns for subscriptions and store updates (e.g., consistent hooks/signatures).

- **API consistency:**
  - `@ghatana/api` is the canonical fetch client layer; ad-hoc `fetch` wrappers in apps should be migrated here where possible.

---

## 3. Common Gaps, Duplicates, and Reuse Misses (High-Level)

- **Accessibility duplication risk:**

  - Some a11y helpers (contrast, focus styles, ARIA) live in `@ghatana/utils`.  
    Specs recommend keeping `utils` as the **canonical** home for generic a11y helpers and using `@yappc/accessibility-audit` only for axe/CI-specific features.

- **Design tokens & theme leakage:**

  - It’s easy for apps or components to hardcode colors/spacings instead of using `tokens`/`theme`.  
    Specs recommend: all visual constants come from `@ghatana/tokens` or `@ghatana/theme`.

- **Multiple React version ranges:**

  - Some libs peer-depend on React 18, others on React 19.2.
  - Specs call this out as a **risk** and recommend consolidating peerDependencies for React across all UI libs.

- **Ad-hoc Storybook configs:**

  - Apps may ship their own `.storybook` rather than layering on `@ghatana/storybook`.

- **Ad-hoc test helpers:**
  - Older apps may have local RTL helpers instead of importing `@ghatana/test-utils`.

Each individual library spec goes into specific gaps and enhancement ideas.

---

## 4. How to Use These Specs

- When adding or refactoring shared code, check the relevant `LIBRARY_*.md` first:
  - Does the change belong in `utils` vs `ui` vs `design-system`?
  - Is there an existing helper or component that should be reused?
- When creating new libraries, use this layering and naming as a guide.
- When updating peer deps or React versions, update the **entire stack** consistently.
