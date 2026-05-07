# AEP UI — Design System Adoption Guide (P7-3e)

## Current State

- **Design system imports**: 2 (ErrorBoundary, ThemeProvider)
- **Raw HTML elements**: ~64 `<button>`, `<input>`, `<select>` needing migration
- **Theme provider**: ✅ Added to root `main.tsx`

## Migration Map

| Raw HTML Element | Design System Replacement | Import |
|---|---|---|
| `<button>` | `<Button>` | `@ghatana/design-system` |
| `<input>` | `<Input>` | `@ghatana/design-system` |
| `<select>` | `<Select>` | `@ghatana/design-system` |
| `<input type="checkbox">` | `<Checkbox>` | `@ghatana/design-system` |
| `<textarea>` | `<TextArea>` | `@ghatana/design-system` |
| Loading spinners | `<Spinner>` | `@ghatana/design-system` |
| Tooltips | `<Tooltip>` | `@ghatana/design-system` |
| Tags/labels | `<Badge>`, `<Chip>` | `@ghatana/design-system` |

## Priority Files (most raw elements)

Migrate these files first when doing the full adoption pass:

```bash
# Find files with most raw HTML form elements
grep -rn "<button\|<input\|<select" products/data-cloud/planes/action/ui/src/ --include="*.tsx" \
  | cut -d: -f1 | sort | uniq -c | sort -rn | head -10
```

## Theme Integration

The `ThemeProvider` from `@ghatana/theme` is now wired into `main.tsx`.
Use `useTheme()` hook in components to access token values:

```tsx
import { useTheme } from '@ghatana/theme';

function MyComponent() {
  const { theme } = useTheme();
  // Access theme tokens...
}
```
