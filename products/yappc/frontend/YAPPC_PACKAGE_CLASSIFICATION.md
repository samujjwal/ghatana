# YAPPC `@yappc/*` Package Classification

> **F-Y003 / SIMP-Y7 / K-Y1 resolution** — declares the canonical fate of every `@yappc/*` frontend package.

## Decision: Product-Scoped, Not Platform-Grade

None of the `@yappc/*` packages contain platform-grade, product-agnostic code.
All are **product-specific** and belong in the YAPPC product tree.

The `@ghatana/*` platform scope is reserved for packages that any product can consume without YAPPC coupling (Section 32 of copilot-instructions.md). `@yappc/*` packages correctly live in `products/yappc/frontend/libs/`.

## Package Classification

| Package | Usage count | Classification | Action |
|---|---|---|---|
| `@yappc/config-schema` | 36 | Product-specific — YAPPC config domain types | Keep. Internal product package. No rename. |
| `@yappc/core` | 32 | Product-specific — YAPPC core domain types | Keep. Internal product package. No rename. |
| `@yappc/ui` | 10 | Product adapter — thin wrapper; re-exports `@ghatana/design-system` plus YAPPC MUI theming (`ThemeProvider`, `lightTheme`, `darkTheme`, `resolveMuiColor`, `getPaletteMain`) | Keep. Correct use of product adapter pattern. |
| `@yappc/auth` | 9 | Product-specific — YAPPC auth flow | Keep. Not a duplicate of `@ghatana/sso-client`; YAPPC-specific token exchange logic. |
| `@yappc/state` | 6 | Product-specific — YAPPC Jotai atoms | Keep. Product atoms must not live in `@ghatana/state`. |
| `@yappc/product-theme` | 6 | Product-specific — YAPPC lifecycle phase themes | Keep. No `@ghatana/*` equivalent exists. |
| `@yappc/collab` | 5 | Product-specific — YAPPC real-time collab | Keep. |
| `@yappc/api` | 5 | Product-specific — YAPPC API client | Keep. Migrate to generated OpenAPI client (SIMP-Y19). |
| `@yappc/ai` | 1 | Product-specific — YAPPC AI helpers | Keep. |
| `@yappc/artifact-compiler` | 1 | Product-specific — YAPPC artifact compiler | Keep. |
| `@yappc/config-compiler` | 1 | Product-specific — YAPPC config compiler | Keep. |
| `@yappc/shortcuts` | 1 | Product-specific — YAPPC keyboard shortcuts | Keep. |
| `@yappc/chat` | 0 (lib) | Product-specific — YAPPC conversation | Keep. |
| `@yappc/devsecops` | 0 (lib) | Product-specific — YAPPC devsecops | Keep. |
| `@yappc/ide` | 0 (lib) | Product-specific — IDE bridge | Keep. |

## Correct Layering Pattern

```
@ghatana/design-system  ← platform package (no YAPPC coupling)
        ↑
@yappc/ui               ← product adapter (adds YAPPC MUI theme, re-exports platform)
        ↑
components/ui/index.ts  ← app barrel (single import surface for the web app)
```

Consumer code in `web/src/` imports from `components/ui` (the app barrel), not directly from `@yappc/*` or `@ghatana/*`.

## Violations to Fix Forward

| Violation | Files | Fix |
|---|---|---|
| Direct `@yappc/ui` import bypassing barrel | 1 (`components/ui/index.ts` itself — acceptable) | None. |
| `@deprecated` JSDoc on `phaseTheme.ts` re-export | `theme/phaseTheme.ts` | Remove the deprecated tag; re-export from `@yappc/product-theme` is correct. |
| `@yappc/yappc-ui` import (invalid double-scoped name) | 1 | Fix to `@yappc/ui`. |
| `@yappc/initialization-ui` (2 references, no such lib) | 2 | Investigate and remove. |

## What This Is NOT

- `@yappc/*` packages are **not** in `platform/` — they are in `products/yappc/frontend/libs/`. No platform-vs-product boundary violation. ✅
- `@yappc/*` packages are **not** duplicating `@ghatana/*` packages — they extend them with product logic. ✅
- No packages need to be promoted to `@ghatana/*` — none are product-agnostic. ✅

## Remaining Work (SIMP-Y19)

`@yappc/api` is a hand-coded API client. Replace with generated OpenAPI client per SIMP-Y19.
