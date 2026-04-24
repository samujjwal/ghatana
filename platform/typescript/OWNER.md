# Owner: Platform TypeScript Packages

**Team:** Platform Frontend Team  
**Slack:** #platform-frontend  
**Last Updated:** 2026-04-23  

## Package Map

| Package | npm name | Purpose | Owned By |
|---------|---------|---------|---------|
| `data-grid` | `@ghatana/data-grid` | High-performance virtual data grid with column config, filtering, sorting, export | Platform Frontend |
| `charts` | `@ghatana/charts` | Recharts-based chart suite (line, bar, pie, scatter, heatmap) | Platform Frontend |
| `canvas` | `@ghatana/canvas` | Collaborative canvas with KonvaJS, real-time presence, object management | Platform Frontend |
| `code-editor` | `@ghatana/code-editor` | Monaco-based code editor with multi-language support, diff, themes | Platform Frontend |
| `design-system` | `@ghatana/design-system` | Shared design tokens, typography, spacing, component primitives | Platform Frontend |
| `theme` | `@ghatana/theme` | Dynamic theming provider, dark/light mode, tenant branding | Platform Frontend |
| `api` | `@ghatana/api` | Type-safe HTTP client helpers, API response normalization | Platform Frontend |
| `realtime` | `@ghatana/realtime` | WebSocket connection management, pub/sub, event envelope | Platform Frontend |
| `utils` | `@ghatana/utils` | Shared utility functions, date formatting, validation helpers | Platform Frontend |
| `tokens` | `@ghatana/tokens` | Design token definitions (colors, spacing, radius, shadows) | Platform Frontend |

## Dependency Rules

- Packages must not import each other except: `design-system` → `tokens` → (nothing).
- Product packages may import from `platform/typescript/*` but not vice versa.
- No package may import from a `products/*` directory.
- No circular dependencies. Enforced by `eslint-rules/ghatana-architecture-rules.js`.

## Quality Gates

- All packages must pass `tsc --noEmit --strict`.
- Vitest unit + a11y tests must pass before merge.
- Browser/a11y/performance tiers run via `.github/workflows/platform-ts-browser-a11y-perf.yml`.
- Bundle size limits enforced per package via `@next/bundle-analyzer` (web apps).

## Known Issues

- `platform/typescript/charts/tsconfig.json` contains a deprecated `--ignoreDeprecations` flag
  that produces a lint warning. This is a pre-existing issue to be resolved in the next
  charts package upgrade cycle.
