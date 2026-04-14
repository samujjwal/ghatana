# @yappc/product-theme

YAPPC-specific theme layer. Provides lifecycle phase visual presets and the MUI bridge adapter.

## What lives here

- **`lifecycle-presets`** — 8-phase lifecycle color themes (intent, shape, validate, generate, build, run, observe, improve). Colors are WCAG AA compliant.
- **`mui-bridge`** — `MuiThemeConnector` component that reads `@ghatana/theme` resolved values and applies them to a MUI `ThemeProvider`.

## What does NOT live here

| Concern | Canonical location |
|---|---|
| Token primitives (spacing, ramps) | `@ghatana/tokens` |
| Theme runtime / CSS variables | `@ghatana/theme` |
| MUI theme objects (lightTheme, darkTheme) | `@yappc/ui` |
| Generic UI components | `@ghatana/design-system` |

## Usage

```tsx
import { getPhaseTheme, type LifecyclePhase } from '@yappc/product-theme';
import { MuiThemeConnector } from '@yappc/product-theme/mui-bridge';
import { ThemeProvider as GhatanaThemeProvider } from '@ghatana/theme';

// In your app root:
<GhatanaThemeProvider defaultTheme="system">
  <MuiThemeConnector>
    {children}
  </MuiThemeConnector>
</GhatanaThemeProvider>

// Lifecycle phase colors:
const phase: LifecyclePhase = 'intent';
const { canvasBg, text, accent } = getPhaseTheme(phase);
```

## Dependency chain

```
@yappc/product-theme
  → @ghatana/tokens    (token primitives)
  → @ghatana/theme     (theme runtime)
  → @yappc/ui          (MUI lightTheme / darkTheme objects)
  → @mui/material      (peer)
```
