# Mobile / Sketch / IDE Lib Scope Classification (K-Y18)

**Decision Date**: 2026-04-27  
**Owner**: YAPPC Frontend Team  
**Status**: Authoritative

---

## Summary

Three YAPPC frontend libs have had unclear scope: `@yappc/ide`, `mobile/`, and (formerly) any Sketch adapter. This document
classifies each definitively so import and migration decisions are unambiguous.

---

## 1. `mobile/` (Capacitor shims)

**Package name**: no `package.json` — this is a build-time shim folder, not a published package.

**Scope**: **Web build compatibility shim only**. Never import in product code. Vite aliases in
`frontend/web/vite.config.ts` resolve `@capacitor/*` references to these no-ops when bundling for web targets.

**Lifecycle**: Stays indefinitely until a dedicated native app package is created under a separate app workspace.
If a native mobile app is built, it should live at `products/yappc/mobile-app/`, not inside `frontend/libs/`.

**Action required**: None. `mobile/README.md` is authoritative and complete.

---

## 2. `@yappc/ide`

**Package name**: `@yappc/ide`  
**Version**: 0.1.0  
**Description**: Collaborative Polyglot IDE core package — **DEPRECATED, sunset 2026-06-06**

**Scope**: YAPPC-product-specific IDE widget layer. Contains CRDT collaboration, syntax highlighting, AI assistant
integration, and code completion UI for the canvas-embedded code editor.

**Status**: Deprecated. Migration target is `@ghatana/canvas` (subpath `./tools` for IDE tooling) or
`@ghatana/code-editor` for the Monaco integration. The two packages should NOT co-exist after the sunset date.

**Allowed imports until sunset**: Only inside `products/yappc/frontend/web/` and `products/yappc/frontend/libs/`.
No cross-product imports permitted.

**Migration plan**:
1. Identify all files importing from `@yappc/ide` (run `grep -rn "@yappc/ide"` in `frontend/`).
2. For Monaco/LSP features → migrate to `@ghatana/code-editor`.
3. For canvas-embedded tooling (minimap, annotations) → migrate to `@ghatana/canvas/tools`.
4. Delete `frontend/libs/ide/` on or before 2026-06-06.

---

## 3. Sketch Adapter

**Status**: No Sketch-specific package exists in this repository at audit date.

If a Sketch plugin or design-token import pipeline was planned, it should be created as:
- `platform/typescript/ds-generator` for design token export
- A separate Sketch plugin repo (outside the monorepo) for native Sketch integration

Do not add a Sketch adapter inside `products/yappc/frontend/libs/`.

---

## Decision Rules (going forward)

| Lib type | Where it belongs |
|----------|-----------------|
| Native mobile runtime | `products/yappc/mobile-app/` (new workspace) |
| Capacitor web shims | `frontend/libs/mobile/` (current, shim only) |
| Monaco / code editor | `@ghatana/code-editor` |
| Canvas-embedded tooling | `@ghatana/canvas/tools` subpath |
| YAPPC-specific IDE widgets | `@yappc/ide` until sunset → then deleted |
| Sketch/Figma token export | `platform/typescript/ds-generator` |

---

## References

- [YAPPC Package Classification](../YAPPC_PACKAGE_CLASSIFICATION.md)
- [LIBRARY_GOVERNANCE.md](../../../../platform/typescript/LIBRARY_GOVERNANCE.md)
- `@yappc/ide` package.json deprecation notice
- `mobile/README.md` shim scope declaration
