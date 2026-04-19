# @ghatana/design-system

WCAG AA-compliant UI component library for the Ghatana platform. Built on **Atomic Design** (atoms → molecules → organisms) with full Tailwind CSS theming.

---

## Installation

```bash
pnpm add @ghatana/design-system
```

### Required peer dependencies

You **must** install all of these alongside the package:

```bash
pnpm add @ghatana/theme @ghatana/tokens react react-dom react-router
```

| Package | Required version |
|---------|-----------------|
| `@ghatana/theme` | `workspace:*` (monorepo) |
| `@ghatana/tokens` | `workspace:*` (monorepo) |
| `react` | `^19.2.4` |
| `react-dom` | `^19.2.4` |
| `react-router` | `^7.0.0` |

> **Why are these peers?** The design system uses tokens and theme context from `@ghatana/theme` and `@ghatana/tokens` at runtime. Keeping them as peers prevents duplicate React contexts when consuming apps already bundle these.

---

## Usage

### Basic component

```tsx
import { Button } from '@ghatana/design-system';

export function SaveButton() {
  return <Button variant="primary" onClick={handleSave}>Save</Button>;
}
```

### Icons

```tsx
import { SaveIcon } from '@ghatana/design-system/icons';
```

### Atoms (deep import)

```tsx
import { Badge } from '@ghatana/design-system/atoms/Badge';
import { Chip } from '@ghatana/design-system/atoms/Chip';
```

### Layout components

```tsx
import { Stack, Grid } from '@ghatana/design-system/layout';
```

### Runtime composition primitives

```tsx
import {
  createSlotProps,
  useComponentTelemetry,
  compileComponentRecipe,
  useComponentRecipe,
} from '@ghatana/design-system';
```

Use these helpers when building new extensible components from primitives:

- `createSlotProps(...)` attaches normalized `data-*` metadata for slots, variants, tone, state, privacy, and observability.
- `mergePrimitiveProps(...)` composes class names, styles, event handlers, and `aria-describedby` safely.
- `useComponentTelemetry(...)` emits sanitized `ghatana:component-event` events so components remain observable without leaking arbitrary user data.

### Composition model

The preferred runtime model for new components is:

- `useComponentComposition(...)` as the single handoff point for component metadata, state, features, slot props, and layered behaviors.
- `createPressableBehavior(...)` or other behaviors to add keyboard-safe interaction without reimplementing role, tab order, and telemetry in every component.
- `getSlotProps(slot, ...)` for every first-class slot so styles, state, observability, privacy annotations, and future behavior middleware can flow through the whole component tree consistently.
- `compileComponentRecipe(...)` and `useComponentRecipe(...)` when the component should be declared as data first and later rendered, analyzed, or code-generated from a compiled render plan.
- `createPlatformRenderPlan(...)` and `plan.platform` when the output needs to stay platform-neutral for future generators beyond React.

This lets us synthesize richer components from primitives while keeping style precedence, state propagation, a11y metadata, o11y hooks, and privacy labeling aligned.

See [docs/COMPOSITION_ARCHITECTURE.md](./docs/COMPOSITION_ARCHITECTURE.md) for the design rationale and future direction.

### Hooks

```tsx
import { useTheme } from '@ghatana/design-system/hooks';
```

---

## Component Layers

| Layer | Path | Examples |
|-------|------|---------|
| **Atoms** | `atoms/` | `Button`, `Badge`, `Chip`, `Checkbox`, `Input`, `DatePicker`, `Avatar`, `Icon` |
| **Molecules** | `molecules/` | `Accordion`, `Alert`, `AppBar`, `Card`, `Autocomplete`, `Breadcrumbs` |
| **Organisms** | `organisms/` | Full-page layouts, multi-part composites |

All components are exported from the package root (`@ghatana/design-system`) for easy tree-shaking.

---

## Theming

The design system reads tokens from `@ghatana/tokens` and applies them via `@ghatana/theme`'s React context. Wrap your app once at the root:

```tsx
import { ThemeProvider } from '@ghatana/theme';

export function App() {
  return (
    <ThemeProvider>
      {/* your app */}
    </ThemeProvider>
  );
}
```

---

## Accessibility

- All interactive components meet **WCAG 2.1 AA** contrast ratios
- Keyboard navigation and ARIA roles are provided out of the box
- Use `FocusTrap` for modal patterns

---

## package.json (consumer example)

```json
{
  "dependencies": {
    "@ghatana/design-system": "workspace:*",
    "@ghatana/theme": "workspace:*",
    "@ghatana/tokens": "workspace:*",
    "react": "^19.2.4",
    "react-dom": "^19.2.4",
    "react-router": "^7.0.0"
  }
}
```

---

## Testing

Components are tested with **React Testing Library** and **Jest**. See `src/test/` for test helpers and setup.
