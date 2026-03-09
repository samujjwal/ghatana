# Library Spec – @ghatana/tokens

Global design tokens for the Ghatana platform.

---

## 1. Purpose & Scope

- Provide a **single source of truth** for design tokens:
  - Colors, spacing, typography, shadows, borders, breakpoints, transitions, z-index, etc.
- Be **framework-agnostic**: tokens should be consumable by React, CSS, or other frontends.

From `package.json`:

- Entry: `@ghatana/tokens`
- Description: "Global design tokens for Ghatana platform - framework-agnostic design tokens".
- Exports:
  - `.` → main index.
  - `./colors`, `./spacing`, `./typography`, `./shadows`, `./borders`, `./breakpoints`, `./transitions`, `./z-index`, `./registry`, `./css`, `./validation`.
- Scripts include `tokens:css` and `tokens:validate` for generating and validating token output.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Own **raw, semantic tokens** (e.g., `color.primary.500`, `space.4`, `font.body.md`).
- Provide **validated token definitions** (via `validation` and `zod`).
- Generate **CSS-friendly artifacts** for consumption by CSS/Tailwind/etc.

**Non-responsibilities:**

- No React components or JSX.
- No theme-specific logic (that lives in `@ghatana/theme`).
- No layout or styling rules (e.g., "card padding" belongs in theme/UI).

---

## 3. Consumers & Typical Usage

- `@ghatana/theme` – constructs theme objects and brand presets from tokens.
- `@ghatana/ui` – uses tokens for component styling, often via theme.
- `@ghatana/design-system` – re-exports or curates token subsets.
- Apps or CSS layers – may import `@ghatana/tokens/css` for CSS variables.

Example usage (conceptual):

```ts
import { colors, spacing, typography } from "@ghatana/tokens";

buttonStyles = {
  backgroundColor: colors.primary[500],
  paddingInline: spacing[4],
  fontFamily: typography.body.fontFamily,
};
```

---

## 4. Dependencies & Relationships

- Depends on `zod` for validation.
- Used by `@ghatana/theme` (declared in `@ghatana/theme` dependencies).
- Forms the **lowest visual layer**; should not depend on any other internal UI libs.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Risk of hard-coded values in other libs:**

  - Some components/themes may still use raw hex codes or spacing values directly.
  - Opportunity: codemods or guidelines to migrate to tokens.

- **Token registry vs theme duplication:**
  - Ensure that any token transformations in `@ghatana/theme` dont re-encode raw values (colors, spacing) that already exist here.

---

## 6. Enhancement Opportunities

1. **Token documentation / catalog:**

   - Generate a simple tokens gallery (colors, spacing scale, typography) consumed by Storybook or docs.

2. **Stricter validation:**

   - Use `validation` exports to enforce invariants (e.g., contrast requirements for certain brand palettes).

3. **Design-tool integration:**
   - Export formats for Figma or other design tools from the same token source.

---

## 7. Usage Guidelines

- Always import visual constants from `@ghatana/tokens` (or `@ghatana/theme`), **never hard-code** design values in components.
- Add new tokens **semantically** (e.g., `color.intent.success`) rather than purely structural names.
