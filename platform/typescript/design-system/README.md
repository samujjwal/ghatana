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
