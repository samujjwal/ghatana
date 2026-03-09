# Canvas State Consolidation

> **Status:** IN PROGRESS | **Last Updated:** 2025-01-19

## Architecture

```
Platform Canvas (canonical core)
  platform/typescript/canvas/src/hybrid/state.ts
  ↓ re-exported by ↓
YAPPC Canvas Lib (product-specific additions)
  products/yappc/frontend/libs/canvas/src/state/atoms.ts
  ↓ imported by ↓
YAPPC Web App
  apps/web/src/components/canvas/workspace/canvasAtoms.ts  ← NEW canonical (uses @xyflow/react)
  apps/web/src/state/atoms/canvasAtom.ts                   ← DEPRECATED (bridges to @ghatana/canvas)
```

## State Layers

| Layer | Location | Atoms | Status |
|---|---|---|---|
| **Platform core** | `@ghatana/canvas` (`hybrid/state.ts`) | `hybridCanvasStateAtom`, `viewportAtom`, `selectionAtom`, `toolAtom`, `elementsAtom`, `nodesAtom`, `edgesAtom`, `gridAtom` | ✅ Canonical |
| **YAPPC lib** | `libs/canvas/src/state/atoms.ts` | Re-exports platform + adds: `canvasDocumentAtom`, `canvasUIStateAtom`, `canvasPerformanceAtom`, `canvasCollaborationAtom`, `canvasHistoryAtom` | ✅ Bridged |
| **YAPPC workspace** | `canvasAtoms.ts` | `nodesAtom`, `edgesAtom`, `cameraAtom`, `commandRegistryAtom`, etc. (uses `@xyflow/react` types) | ✅ New canonical |
| **YAPPC legacy** | `canvasAtom.ts` | Legacy types + bridge to `@ghatana/canvas` | ⚠️ Deprecated |

## Migration Rules

1. **New canvas state** → Define in `canvasAtoms.ts` (workspace-level) or platform `hybrid/state.ts`
2. **Product-specific state** (collaboration, performance) → Define in `libs/canvas/src/state/atoms.ts`
3. **Core canvas operations** → Import from `@ghatana/canvas` (platform)
4. **Legacy `canvasAtom.ts`** → Do not add new imports. Migrate to `canvasAtoms.ts`

## Remaining Work

- [ ] Migrate 20+ files importing from `canvasAtom.ts` to `canvasAtoms.ts`
- [ ] Remove `StateManager.createPersistentAtom` usage (replaced by platform `atomWithStorage`)
- [ ] Align `CanvasDocument` type with `HybridCanvasState` or create adapter
- [ ] Delete `canvasAtom.ts` once all consumers migrated
