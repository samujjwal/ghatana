# Frontend Library Migration Guide

> **Status:** Active — owners must complete migration by the dates listed below.  
> **Last Updated:** 2026-01-19

---

## Summary

Four legacy libraries in `products/yappc/frontend/libs/` are **deprecated**.
They will be deleted after their sunset dates. All new feature work must target
the replacement libraries listed below.

| Legacy (`@ghatana/yappc-*`) | Replacement (`@ghatana/*` or `@yappc/*`) | Sunset Date |
| --------------------------- | ---------------------------------------- | ----------- |
| `@ghatana/yappc-canvas`     | `@ghatana/canvas` (platform library)     | 2026-06-30  |
| `@ghatana/yappc-ai`         | `@yappc/ai`                              | 2026-06-30  |
| `@ghatana/yappc-ui`         | `@yappc/ui`                              | 2026-06-30  |
| `@ghatana/yappc-ide`        | `@ghatana/canvas` (platform library)     | 2026-06-06  |

---

## Migration Guides

### `@ghatana/yappc-canvas` → `@ghatana/canvas`

**Why:** `@ghatana/yappc-canvas` ships un-bundled TypeScript source, has no
separate chunk splitting, and exports internal state atoms publicly.
Use the platform-level `@ghatana/canvas` library instead, which provides
a stable, shared canvas implementation across all Ghatana products.

**Steps:**

1. Update your import in `package.json`:

   ```json
   // Before
   "@ghatana/yappc-canvas": "workspace:*"
   // After
   "@ghatana/canvas": "workspace:*"
   ```

2. Update import paths:

   ```ts
   // Before
   import { CanvasEditor } from '@ghatana/yappc-canvas';
   import { canvasAtoms } from '@ghatana/yappc-canvas/state';

   // After
   import { CanvasEditor } from '@ghatana/canvas';
   // Use YAPPC-specific state atoms from libs/canvas if needed
   ```

3. The `<EditorCanvas>` component API is unchanged.  
   Platform canvas provides the core canvas functionality, while
   YAPPC-specific extensions are in libs/canvas.

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

### `@ghatana/yappc-ide` → `@yappc/canvas`

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
   "@yappc/canvas": "workspace:*"
   ```

3. IDE-specific panels (`CommandPalette`, `FileTree`) have been moved to
   `@yappc/canvas/panels`.

---

## Enforcement

From **2026-03-01** onward, the ESLint rule `no-restricted-imports` in
[products/yappc/frontend/eslint.config.mjs](../eslint.config.mjs) will be
updated to **error** on imports from the four legacy packages. PRs importing
from them will fail CI lint checks.

---

## Compat Package Status (2026-04-06)

The deleted compat package names below must not appear in active code or config.
They are no longer part of the supported frontend import surface.

| Moved package | New path |
| --- | --- |
| `@yappc/base-ui` | `@yappc/ui` |
| `@yappc/development-ui` | `@yappc/ui` |
| `@yappc/initialization-ui` | `@yappc/ui` |
| `@yappc/navigation-ui` | `@yappc/ui` |
| `@yappc/messaging` | `@yappc/ai` |
| `@yappc/notifications` | `@yappc/ai` |
| `@yappc/config-hooks` | `@yappc/config` or `@yappc/state` |
| `@yappc/crdt` | `@yappc/collab` |
| `@yappc/types` | `@yappc/core/types` |
| `@yappc/utils` | `@yappc/config` or feature-local utilities |

New code must **not** depend on deleted compat names or legacy `reactflow` imports.
Use `@xyflow/react` directly for flow primitives.

---

## Questions?

Open an issue in the `#yappc-frontend` channel or tag `@platform-team` in
your PR.
