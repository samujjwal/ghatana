# @ghatana/canvas — Internal Modules

Directories and files in `src/` that are **not** re-exported via `src/public/index.ts`
or a named subpath in `package.json` are **internal** to the canvas package.

## Rules

1. **Never import internal modules from product code.** Use the documented subpath exports:
   - `@ghatana/canvas` — main public surface
   - `@ghatana/canvas/react` — React component surface
   - `@ghatana/canvas/types` — type-only surface
   - `@ghatana/canvas/hybrid` — hybrid renderer
   - `@ghatana/canvas/topology` — topology canvas
   - `@ghatana/canvas/flow` — flow/graph canvas (replaces `@ghatana/flow-canvas`)
   - `@ghatana/canvas/ai` — AI capability group
   - `@ghatana/canvas/telemetry` — telemetry sink + events
   - `@ghatana/canvas/testing` — test helpers only (never in production bundles)
   - `@ghatana/canvas/plugins` — plugin registry
   - `@ghatana/canvas/tools` — built-in tools
   - `@ghatana/canvas/collaboration` — collaboration adapters
   - `@ghatana/canvas/export` — export utilities

2. **All additions to the public surface go through `src/public/index.ts`.**  
   Adding a new export to `src/public/index.ts` is a considered act — it is a public API contract.

3. **Internal helpers, implementation details, and framework glue stay in `src/`.  
   Do not surface them unless they have clear external consumer value.**

## What lives here

Currently this directory contains this README only.  Internal modules live
directly in their `src/` subdirectories and are kept internal by not appearing
in any subpath export.
