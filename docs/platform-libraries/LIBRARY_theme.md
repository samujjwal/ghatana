# Library Spec – @ghatana/theme

Unified theme system for the Ghatana platform.

---

## 1. Purpose & Scope

- Provide **theme objects, providers, hooks, and brand presets** built on top of `@ghatana/tokens`.
- Handle **dark/light modes**, brand customization, and type-safe theme access.

From `package.json`:

- Entry: `@ghatana/theme`.
- Description: "Unified theme system for Ghatana platform".
- Exports: `.`, `./provider`, `./hooks`, `./types`, `./theme`, `./schema`, `./brandPresets`, `./themeManager`.
- Depends on `@ghatana/tokens` and `zod`.

---

## 2. Responsibilities & Boundaries

**Responsibilities:**

- Construct **runtime theme objects** from tokens (e.g., palettes, component scales).
- Provide a **ThemeProvider** and hooks for React apps.
- Manage **brand presets** and dynamic theme switching (through `themeManager`).
- Validate theme shapes via `schema`.

**Non-responsibilities:**

- No direct UI components (buttons, cards) – belongs in `@ghatana/ui`.
- No app-specific layout; themes are generic across products.

---

## 3. Consumers & Typical Usage

- `@ghatana/ui` – uses theme context for styling components.
- `@ghatana/design-system` – aggregates theme + UI + tokens into a facade.
- Apps – may import ThemeProvider directly for custom setups.

Example usage (conceptual):

```tsx
import { ThemeProvider, defaultTheme } from "@ghatana/theme";

<ThemeProvider theme={defaultTheme}>
  <App />
</ThemeProvider>;
```

---

## 4. Dependencies & Relationships

- Depends on `@ghatana/tokens`.
- Peer dep on React ^19.2.0.
- Sits **between tokens and UI** in the visual stack.

---

## 5. Gaps, Duplicates, Reuse Misses

- **Version mismatch risk:**

  - React peer version here is 19.2.0, while `@ghatana/ui` peers React 18.2.0.  
    → Align React versions across the stack.

- **Potential redefinition of styles:**

  - Ensure theme does not introduce new "magic" colors or spacings that arent expressed as tokens.

- **Scattered brand presets:**
  - Brand presets should be the **only** source of brand-specific variations, not re-implemented in apps.

---

## 6. Enhancement Opportunities

1. **Theming guidelines:**

   - Document how to add new brand presets and modes (e.g., high-contrast themes).

2. **Runtime theme editor (future):**

   - Back a visual theme editor (possibly inside App Creator) using the theme schema.

3. **Cross-app consistency checks:**
   - Small script to ensure all consuming apps use compatible theme versions.

---

## 7. Usage Guidelines

- Apps should wrap their root with `ThemeProvider` (directly or via `@ghatana/design-system`).
- New theme capabilities (e.g., density, motion) should be added here so they are reusable across all products.
