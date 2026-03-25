# Frontend Library Migration Guide

> **Status:** Active — owners must complete migration by the dates listed below.  
> **Last Updated:** 2026-01-19

---

## Summary

Four legacy libraries in `products/yappc/frontend/libs/` are **deprecated**.
They will be deleted after their sunset dates. All new feature work must target
the replacement libraries listed below.

| Legacy (`@ghatana/yappc-*`) | Replacement (`@yappc/*`) | Sunset Date |
| --------------------------- | ------------------------ | ----------- |
| `@ghatana/yappc-canvas`     | `@yappc/canvas`          | 2026-06-30  |
| `@ghatana/yappc-ai`         | `@yappc/ai`              | 2026-06-30  |
| `@ghatana/yappc-ui`         | `@yappc/ui`              | 2026-06-30  |
| `@ghatana/yappc-ide`        | `@ghatana/yappc-canvas`  | 2026-06-06  |

---

## Migration Guides

### `@ghatana/yappc-canvas` → `@yappc/canvas`

**Why:** `@ghatana/yappc-canvas` ships un-bundled TypeScript source, has no
separate chunk splitting, and exports internal state atoms publicly.
`@yappc/canvas` ships a pre-bundled ESM artifact with a stable public API.

**Steps:**

1. Update your import in `package.json`:

   ```json
   // Before
   "@ghatana/yappc-canvas": "workspace:*"
   // After
   "@yappc/canvas": "workspace:*"
   ```

2. Update import paths:

   ```ts
   // Before
   import { CanvasEditor } from '@ghatana/yappc-canvas';
   import { canvasAtoms } from '@ghatana/yappc-canvas/state';

   // After
   import { CanvasEditor } from '@yappc/canvas';
   import { canvasAtoms } from '@yappc/canvas/atoms';
   ```

3. The `<EditorCanvas>` component API is unchanged.  
   The `useCanvasStore()` hook replaces direct atom subscriptions.

---

### `@ghatana/yappc-ai` → `@yappc/ai`

**Why:** `@ghatana/yappc-ai` is a thin wrapper with no bundling guarantees.
`@yappc/ai` consolidates AI features (chat, completions, streaming, tool-use)
and ships a versioned API surface.

**Steps:**

1. Update `package.json`:

   ```json
   // Before
   "@ghatana/yappc-ai": "workspace:*"
   // After
   "@yappc/ai": "workspace:*"
   ```

2. Update imports:

   ```ts
   // Before
   import { useAICompletion } from '@ghatana/yappc-ai';

   // After
   import { useAICompletion } from '@yappc/ai';
   ```

3. The streaming hook `useAIStream()` replaces the old `useAICompletion({ stream: true })` pattern.

---

### `@ghatana/yappc-ui` → `@yappc/ui`

**Why:** `@ghatana/yappc-ui` is a private, unversioned package.
`@yappc/ui` provides the same component set, is publicly versioned, exports
tokens via `@yappc/ui/tokens`, and is covered by visual regression tests.

**Steps:**

1. Update `package.json`:

   ```json
   // Before
   "@ghatana/yappc-ui": "workspace:*"
   // After
   "@yappc/ui": "workspace:*"
   ```

2. Update imports:

   ```ts
   // Before
   import { Button, Input } from '@ghatana/yappc-ui';

   // After
   import { Button, Input } from '@yappc/ui';
   ```

3. Design token CSS variables are exposed via `@yappc/ui/tokens` — no longer
   bundled into component CSS. Import the token stylesheet in your app root:
   ```ts
   import '@yappc/ui/tokens/index.css';
   ```

---

### `@ghatana/yappc-ide` → `@ghatana/yappc-canvas`

**Why:** IDE drag editor functionality has been merged into the canvas engine.
The standalone `ide` library has no active maintainer and is frozen at v0.1.0.

**Steps:**

1. Remove the dependency:

   ```json
   // Before
   "@ghatana/yappc-ide": "workspace:*"
   ```

2. Use canvas instead:

   ```json
   "@ghatana/yappc-canvas": "workspace:*"
   ```

   > **Note:** After completing the canvas → `@yappc/canvas` migration above,
   > the final target is `@yappc/canvas`.

3. IDE-specific panels (`CommandPalette`, `FileTree`) have been moved to
   `@yappc/canvas/panels`.

---

## Enforcement

From **2026-03-01** onward, the ESLint rule `no-restricted-imports` in
[products/yappc/frontend/eslint.config.mjs](../eslint.config.mjs) will be
updated to **error** on imports from the four legacy packages. PRs importing
from them will fail CI lint checks.

---

## Compat Package Location (2026-03-24)

As part of the YAPPC structure simplification, 11 pre-consolidation packages
that had no active downstream dependents have been **moved from `libs/` to
`compat/`**. They remain in the pnpm workspace (`compat/*`) and can still be
resolved by any code that imports them, but they are visually separated from
the canonical library surface.

| Moved package | New path |
| --- | --- |
| `@yappc/base-ui` | `frontend/compat/base-ui` |
| `@yappc/development-ui` | `frontend/compat/development-ui` |
| `@yappc/initialization-ui` | `frontend/compat/initialization-ui` |
| `@yappc/navigation-ui` | `frontend/compat/navigation-ui` |
| `@yappc/messaging` | `frontend/compat/messaging` |
| `@yappc/realtime` | `frontend/compat/realtime` |
| `@yappc/notifications` | `frontend/compat/notifications` |
| `@yappc/config-hooks` | `frontend/compat/config-hooks` |
| `@yappc/crdt` | `frontend/compat/crdt` |
| `@yappc/types` | `frontend/compat/types` |
| `@yappc/utils` | `frontend/compat/utils` |

`@yappc/theme` remains in `libs/` because it is still consumed by `@yappc/ui`
and `@yappc/ai`.

New code must **not** depend on any package in `compat/`. Existing consumers
should migrate to the canonical replacements:

| Compat package | Canonical replacement |
| --- | --- |
| `@yappc/base-ui` | `@yappc/ui` |
| `@yappc/navigation-ui` | `@yappc/ui/navigation` |
| `@yappc/development-ui` / `@yappc/initialization-ui` | `@yappc/core` |
| `@yappc/messaging` / `@yappc/notifications` | `@yappc/ai` (chat features) |
| `@yappc/realtime` | `@yappc/state` |
| `@yappc/config-hooks` | `@yappc/core` config utilities |
| `@yappc/crdt` | `@yappc/collab` |
| `@yappc/types` / `@yappc/utils` | `@yappc/core` |

---

## Questions?

Open an issue in the `#yappc-frontend` channel or tag `@platform-team` in
your PR.
