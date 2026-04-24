# Owner: YAPPC Frontend Libraries

**Team:** YAPPC Frontend Team  
**Slack:** #yappc-frontend  
**Parent ownership:** `products/yappc/OWNER.md`  
**Last Updated:** 2026-04-24  

---

## Package Inventory and Ownership

| Package | npm name | Status | Owner | Purpose |
|---------|---------|:---:|---|---|
| `yappc-core` | `@yappc/core` | ✅ Active | YAPPC Frontend | Core types, utilities, API client, constants |
| `yappc-state` | `@yappc/state` | ✅ Active | YAPPC Frontend | Jotai atoms, state hooks, config hooks |
| `yappc-ui` | `@yappc/ui` | ✅ Active | YAPPC Frontend | YAPPC-specific UI component library |
| `yappc-auth` | `@yappc/auth` | ✅ Active | YAPPC Frontend | Auth tokens, session management, SSO client |
| `yappc-chat` | `@yappc/chat` | ✅ Active | YAPPC Frontend | Chat UI, message threading, AI chat integration |
| `yappc-ai` | `@yappc/ai` | ✅ Active | YAPPC Frontend | AI client hooks, prompt management, model selection |
| `yappc-artifact-compiler` | `@yappc/artifact-compiler` | ✅ Active | YAPPC Frontend | Artifact compilation, preview rendering |
| `yappc-development-ui` | `@yappc/development-ui` | ✅ Active | YAPPC Frontend | Dev environment UI, task panels, run output |
| `yappc-devsecops` | `@yappc/devsecops` | ✅ Active | YAPPC Frontend | DevSecOps dashboard, risk scores, SAST integration |
| `yappc-initialization-ui` | `@yappc/initialization-ui` | ✅ Active | YAPPC Frontend | Project creation, onboarding wizards |
| `yappc-product-theme` | `@yappc/product-theme` | ✅ Active | YAPPC Frontend | YAPPC-specific theme tokens, brand overrides |
| `config-schema` | `@yappc/config-schema` | ✅ Active | YAPPC Frontend | Zod schemas for YAPPC configuration |
| `config-compiler` | `@yappc/config-compiler` | ✅ Active | YAPPC Frontend | Compiles `@yappc/config-schema` configs to runtime form |
| `ide` | `@yappc/ide` | 🟡 Review | YAPPC Frontend | IDE integration components — review vs. `@ghatana/code-editor` |
| `collab` | `@yappc/collab` | ✅ Active | YAPPC Frontend | Real-time collaboration, CRDT sync |
| `api` | `@yappc/api` | ✅ Active | YAPPC Frontend | YAPPC-specific HTTP client — wraps `@ghatana/api` |
| `shortcuts` | `@yappc/shortcuts` | ✅ Active | YAPPC Frontend | Keyboard shortcut management |
| `a11y` | `@yappc/a11y` | ✅ Active | YAPPC Frontend | YAPPC accessibility utilities — wraps `@ghatana/accessibility` |
| `mocks` | `@yappc/mocks` | ✅ Active | YAPPC Frontend | MSW mocks, test fixtures — dev/test only |

---

## Dependency Rules

**Allowed intra-library dependencies (in priority order):**

```
@yappc/core          (no @yappc deps — only @ghatana/* and external)
    ↓
@yappc/config-schema (depends on @yappc/core)
    ↓
@yappc/state         (depends on @yappc/core, @ghatana/state)
    ↓
@yappc/yappc-auth    (depends on @yappc/core, @yappc/state)
    ↓
@yappc/api           (depends on @yappc/core, @yappc/auth)
    ↓
@yappc/config-compiler (depends on @yappc/config-schema)
    ↓
Feature libs (yappc-ui, yappc-chat, yappc-ai, etc.) — may depend on any lower tier
```

**Forbidden patterns:**
- No circular dependencies between any two `@yappc/*` packages.
- `@yappc/core` must never import from other `@yappc/*` packages.
- Feature-layer packages (`yappc-chat`, `yappc-development-ui`, etc.) must not
  import from each other — shared logic goes into `@yappc/core`.
- No `@yappc/*` package may import from `products/aep/frontend/` or any other
  product frontend.

---

## Duplication Audit

The following duplicate patterns were identified and must be resolved:

### Config/Schema Duplication
- `@yappc/config-schema` and `@yappc/config-compiler` both define config types.
  **Canonical:** `@yappc/config-schema` owns all Zod schemas; `config-compiler` only
  imports from `config-schema`, never re-declares types.

### State Duplication
- `@yappc/state` (Jotai atoms) and `@yappc/yappc-core` both export state utilities.
  **Canonical:** All Jotai atoms live in `@yappc/state`. `@yappc/core` exports only
  pure utility functions with no reactive state.

### API Client Duplication
- `@yappc/api` and `@yappc/core/api` subpath both provide HTTP helpers.
  **Canonical:** `@yappc/api` is the single HTTP client package. The `@yappc/core/api`
  subpath re-exports from `@yappc/api` for backward compatibility only and will be
  removed in the next major version.

### Auth Duplication
- `@yappc/auth` and `@ghatana/sso-client` overlap on token management.
  **Rule:** `@yappc/auth` wraps `@ghatana/sso-client` and adds YAPPC-specific
  session logic. Direct use of `@ghatana/sso-client` in YAPPC app code is
  forbidden — always use `@yappc/auth`.

---

## Quality Gates

All packages must pass:
- `tsc --noEmit` with `strict: true`
- ESLint with zero warnings (`@typescript-eslint/recommended`)
- Vitest unit tests with minimum 70% coverage on critical paths
- `pnpm --filter "@yappc/*" run type-check` in CI

---

## Deprecation / Archive Policy

Packages marked 🟡 Review must be resolved within 2 sprint cycles:
1. `ide` — evaluate against `@ghatana/code-editor`; merge or keep with documented justification
2. All archived packages from Phase 1 (see `README.md`) must have redirects in place

Any new package requires sign-off from YAPPC Frontend lead and must be added to
this table before creation.
