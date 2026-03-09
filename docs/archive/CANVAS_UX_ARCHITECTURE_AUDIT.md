# YAPPC Canvas UX — Principal Architecture Audit

> **Auditor:** Principal UI/UX Architect & Interaction Systems Engineer  
> **Date:** 2025-01-20  
> **Scope:** `products/yappc/frontend/apps/web/src/components/canvas/**`  
> **Codebase Snapshot:** ~280 TypeScript files, primary orchestrator 1,414 LOC  
> **Verdict:** The canvas has promising bones — smart viewport culling, off-thread spatial indexing, atom-based undo — but is crippled by a God Component, three competing subsystems for history/selection, ~1,400 lines of dead code, and multiple runtime bugs. It is not shippable in its current state.

---

## 1. Executive Summary

| Signal | Status |
|--------|--------|
| **Maturity** | **3.2 / 10** — Prototype with scattered production-quality fragments |
| **Blocking Bugs** | 5 runtime, 3 silent data-corruption risks |
| **Dead Code** | ~1,400 LOC fully implemented but never consumed |
| **Duplicate Systems** | 3× history, 3× selection, 3× keyboard handler, 2× data model |
| **Design-System Violations** | 1 direct MUI import, 5 files using wrong barrel (`@/components/ui` vs `@ghatana/ui`) |
| **Accessibility** | Near-zero — no `role="application"`, no keyboard node traversal, no ARIA live regions |
| **Performance** | O(n) full spatial-tree rebuild on every node change; no virtualization consumed |

**Top 10 Findings (detailed in §15):**

1. **God Component** — `CanvasWorkspace.tsx` is 1,414 lines with 30+ `useCallback`s; untestable.
2. **Dead ⌘K Branch** — Identical condition fires AI Modal; Command Palette branch is unreachable.
3. **ArtifactNode `id` ReferenceError** — Ghost action callbacks reference bare `id` (not destructured from `NodeProps`).
4. **Triple History Stack** — `canvasAtoms` undo/redo, `useCanvasHistory`/`CanvasPersistence`, ReactFlow internal — never synchronized.
5. **Triple Selection System** — `selectedNodesAtom`, `updateSelectionAtom` (`@ghatana/canvas`), ReactFlow internal — never reconciled.
6. **Missing `useEffect` Dependencies** — L818: `handleZoomToPhase`, `handlePasteNodes`, `setCopiedNodes`, `setIsAIModalOpen`, `setIsCommandPaletteOpen` all used but absent from deps array.
7. **DiagramNode Shared Global Atom** — All `DiagramNode` instances read the _same_ `diagramContentAtom`; multiple diagrams show identical content.
8. **Hardcoded Fake Metrics** — Performance panel shows `FPS: 60`, `Render: 12ms` — static strings, not measured.
9. **711 LOC of Unused Performance/LOD Code** — `LODRenderer.tsx` (199 LOC) + `PerformanceOptimization.tsx` (513 LOC) are fully implemented but never imported.
10. **No Accessibility** — Canvas has no `role`, no `aria-label`, no keyboard node traversal, no `prefers-reduced-motion` support.

---

## 2. User Experience Audit

### 2.1 First-Impression Flow

The workspace opens with a loading spinner guarded by `fowStage === undefined`, which is correct. On first paint:

- **Ghost Nodes** provide zero-state guidance — **good**.
- **Spatial Zones** partition the infinite canvas by lifecycle phase — **good conceptual model**.
- **Next Best Task Card** recommends a starting point — **good on-ramp**.

However:

- **No onboarding overlay or walkthrough.** New users see an empty canvas with no explanation of shortcuts or interaction modes.
- **Mock presence data** (Alice Chen, Bob Smith) is hardcoded at L349-363 — users see phantom collaborators. This is actively misleading.
- **Bottom toolbar** (Add / Link) has only two buttons with no labels visible — discoverable only via hover tooltip after a 500ms delay (`TIMING.tooltipDelay`).

### 2.2 Interaction Friction

| Interaction | Quality | Issue |
|---|---|---|
| Double-click to create | ✅ Good | Opens `QuickCreateMenu` at click position |
| Drag from palette | ✅ Good | Uses `screenToFlowPosition` correctly |
| ⌘K for commands | ❌ Broken | Dead branch — always opens AI Modal, never Command Palette |
| ⌘Z / ⌘⇧Z undo/redo | ⚠️ Partial | Works for atom-based history but not for `useCanvasHistory`-tracked changes |
| Copy/Paste (⌘C/⌘V) | ⚠️ Partial | Copies from `generatedNodes` (server-derived), not from current canvas state — pasted nodes may be stale |
| Phase navigation (⌘1-7) | ✅ Good | Uses `reactFlowInstance.setCenter` with measured bounding boxes |
| Inspector open | ✅ Good | Click node → opens `InspectorPanel` with artifact data |
| Sketch mode | ⚠️ Risky | Canvas opacity drops to 0.6; sketch layer uses `window.innerWidth` / `window.innerHeight` at render time — no resize handling |

### 2.3 Error Handling

Every single error path uses `console.error` or `console.warn` — **zero user-visible error feedback.** Counted 20+ `console.log` / `console.warn` / `console.error` calls used as sole error handling. No toast notifications, no error boundaries around individual panels.

---

## 3. Interaction Model Audit

### 3.1 Mode System

Four modes: `navigate`, `sketch`, `code`, `diagram`. Stored in `canvasInteractionModeAtom`.

**Assessment:**

- **Navigate mode** correctly disables node dragging/connecting via ReactFlow boolean props (`panOnDrag`, `nodesDraggable`, `nodesConnectable`, `elementsSelectable`) — **good**.
- **Sketch mode** overlays a Konva layer with `pointer-events-auto` and `z-10` — correctly captures input. But the overlay uses `window.innerWidth/Height` snapshot, not a resize observer. **Sketch layer will be clipped/misaligned after window resize.**
- **Diagram mode** auto-inserts a `DiagramNode` on mode switch (L127-142) but all instances share `diagramContentAtom` — **multi-diagram is broken**.
- **Code mode** is declared in the atom type but has **no implementation** in `CanvasWorkspace.tsx`.

### 3.2 Keyboard Architecture — Three Competing Systems

| System | Location | Scope | Used by CanvasWorkspace? |
|---|---|---|---|
| Inline `useEffect` | `CanvasWorkspace.tsx` L730-818 | ⌘K, ⌘F, ⌘Z, ⌘⇧Z, ⌘C, ⌘V, ⌘1-7 | ✅ Yes (primary) |
| `useCanvasNavigation` | `hooks/useCanvasNavigation.ts` | ⌘1-7, ⌘←/→, ⌘K, ⌘F, ⌘0, Esc | ✅ Yes (also imported at L567) |
| `useCanvasKeyboard` | `hooks/useCanvasKeyboard.ts` | Generic shortcut registrations | ❌ Never imported |

**Critical conflict:** Both the inline `useEffect` AND `useCanvasNavigation` register `window.addEventListener('keydown')`. Shortcuts ⌘K, ⌘F, and ⌘1-7 fire in **both** handlers with potentially different behaviors. Execution order is non-deterministic (depends on mount order and React commit timing).

**Dead ⌘K branch (L738-747):**

```typescript
// ⌘K or Ctrl+K - AI Assistant
if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
    event.preventDefault();
    setIsAIModalOpen(true);
}
// ⌘K or Ctrl+K - Command Palette  ← UNREACHABLE
else if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
    event.preventDefault();
    setIsCommandPaletteOpen(true);
}
```

The `else if` has an identical condition — it can never execute.

### 3.3 Missing `useEffect` Dependencies (L818)

```typescript
}, [canUndo, canRedo, undo, redo, selectedNodes, generatedNodes, copiedNodes]);
```

**Missing:** `handleZoomToPhase`, `handlePasteNodes`, `setCopiedNodes`, `setIsAIModalOpen`, `setIsCommandPaletteOpen`. These are captured in stale closures — shortcuts may operate on outdated state.

### 3.4 Input Guard

The typing guard (L731-736) is **good** — it checks `tagName` and `isContentEditable` and allows Escape to blur. However, it does not check for `[role="textbox"]` (used by some rich-text editors) or Shadow DOM inputs.

---

## 4. Canvas Engine Audit

### 4.1 ReactFlow Integration

| Aspect | Status | Detail |
|---|---|---|
| Node types | ✅ Registry | `useCanvasRegistry()` provides `nodeTypes`/`edgeTypes` via context |
| Coordinate transforms | ✅ Correct | `screenToFlowPosition` used for drops (L841) |
| Viewport sync | ⚠️ Laggy | `onMove` writes to local `useState` + `viewportAtom` — `useComputedView` reads the atom, which may lag behind ReactFlow's internal viewport |
| Zoom config | ⚠️ Missing | No `zoomOnPinch`, no `snapToGrid`. Min 0.1 / Max 2 is reasonable |
| Selection sync | ❌ Broken | `selectNodesOnDrag={false}` + internal selection ≠ `selectedNodesAtom` |
| Measured dimensions | ✅ Good | `handleZoomToPhase` correctly uses `node.measured?.width` (L624) |

### 4.2 Dual Data Model

Two parallel models exist:

1. **ReactFlow `Node<ArtifactNodeData>` / `Edge<DependencyEdgeData>`** — used by `CanvasWorkspace`, atoms, rendering.
2. **`CanvasElement` / `CanvasConnection`** — defined in `@ghatana/canvas` library, used by `useCanvasHistory`, `useCanvasPersistence`, `transform.ts`.

`transform.ts` has a **string-literal `'undefined'` comparison bug**:

```typescript
// Checks for literal string "undefined" instead of value undefined
if (element.type === 'undefined') { ... }
```

The dual model means every mutation must be mirrored across both representations — but **no single codepath does this consistently.** `setNodesAtom()` updates ReactFlow state but not `CanvasElement` state, and vice versa.

### 4.3 Node Change Handler — Smart Optimization

The `onNodesChange` handler (L196-207) separates structural changes from transient drag coordinates:

```typescript
const structuralChanges = changes.filter(c => 
    c.type !== 'position' || (c.type === 'position' && !c.dragging)
);
```

This avoids re-rendering the entire atom tree on every mouse-move during drag. **This is excellent and production-quality.**

---

## 5. Drag & Drop Audit

### 5.1 Template Drag from Palette

`UnifiedLeftPanel` sets `draggedTemplateAtom` on drag start → `handleCanvasDrop` reads it on drop → calls `screenToFlowPosition` → calls `handleCreateArtifact`.

**Assessment:** Correct flow. The `draggedTemplate` is cleared after creation (`setDraggedTemplate(null)` at L847). `handleCanvasDragOver` sets `dropEffect = 'copy'`.

**Issues:**

- No **drag preview** or **drop zone highlight** — user gets no visual feedback during drag.
- No **cancel handling** — if user drags outside the drop zone, `draggedTemplateAtom` remains set until next drop. Should be cleared on `onDragLeave` or `onDragEnd`.
- `draggedTemplate` null check at L835 is good ("Canvas not ready for drops or no template dragged").

### 5.2 Node Repositioning

Uses ReactFlow's built-in `nodesDraggable` prop, gated by `interactionMode === 'navigate'`. During drag, `onNodeDrag` queries the spatial index for alignment snapping.

**Issue:** Spatial index queries are `async` (Web Worker round-trip). During fast mouse moves, the Promise may resolve _after_ the next drag event, showing stale alignment guides. No debounce or cancellation mechanism.

### 5.3 Spatial Index Architecture

```
spatialIndexService.ts → postMessage → spatial.worker.ts (RBush)
```

**Critical flaws:**

- **Full rebuild on every nodes change** (`spatialIndexAPI.buildIndex(nodes)` in `useEffect` at L157) — O(n log n) on every add/remove/position-change. Should use incremental insert/remove.
- **No error handling** — if the Worker throws, the Promise hangs forever (no `worker.onerror` handler, no timeout).
- **No cleanup** — `resolvers` Map grows without bound; resolved entries are never deleted.
- **No Transferable optimization** — node data is structurally cloned, not transferred.

---

## 6. Zoom & Pan Audit

### 6.1 Configuration

```typescript
minZoom={0.1}
maxZoom={2}
panOnDrag={interactionMode === 'navigate'}
panOnScroll={true}
zoomOnScroll={true}
```

**Missing:**

| Feature | Status |
|---|---|
| `zoomOnPinch` | ❌ Not set (defaults to `true` in ReactFlow, but should be explicit) |
| `zoomOnDoubleClick` | Not set (defaults to `true` — conflicts with `handleCanvasDoubleClick` for `QuickCreateMenu`) |
| `snapToGrid` | ❌ Not set — node positions are continuous floats |
| Cursor-centered zoom | ✅ Default ReactFlow behavior — zoom centers on cursor |
| Smooth zoom transitions | ✅ `reactFlowInstance.setCenter` uses `duration: 800` |

### 6.2 Phase Navigation (⌘1-7)

`handleZoomToPhase` (L614-660) is **well-implemented:**

- Uses `reactFlowInstance.getNodes()` to get live measured dimensions.
- Calculates bounding box with `node.measured?.width ?? 200` fallback.
- Computes optimal zoom level to fit the phase's bounding box with padding.
- Falls back to hardcoded zone center positions when no nodes exist in the phase.

**Issue:** Hardcoded fallback zone positions (L647-656) duplicate the values in `handleZoneClick` (L962-971). These should be extracted to a shared constant.

### 6.3 Abstraction Level Navigation

`AbstractionLevelNavigator` provides breadcrumb-based drill-down (System → Phase → Group → Artifact). The implementation updates `currentAbstractionLevel` state and trims breadcrumbs, but **does not actually filter or cluster nodes.** The drill-down just pans+zooms to a parent node — it doesn't hide other content or reveal child detail. **Semantic zoom is a shell without implementation.**

---

## 7. Arbitrary Content Nodes Audit

### 7.1 Node Type Registry

`useCanvasRegistry()` returns `nodeTypes` and `edgeTypes` from `CanvasRegistryProvider`. Registration uses `as any` casts on all types — **type safety gap.**

Registered node types:
- `artifact` → `ArtifactNode`
- `diagram` → `DiagramNode`

### 7.2 ArtifactNode — Runtime Bug

```typescript
// ArtifactNode.tsx — destructures { data, selected } from NodeProps
// BUT ghost action handlers reference bare `id`:
onEdit: (id) => { ... }  // `id` here is the callback parameter, OK
// However, the component body references `data.id` in some places
// and bare `id` in others — inconsistent
```

**Additional ArtifactNode issues:**

- Each node calls `useCodeAssociations(data.id)` — N nodes = N independent API calls. Should be batched at the workspace level.
- Duplicate `className` prop on a `Typography` component — only the last value applies.
- Status color encoding uses **color-only differentiation** (green/amber/red) — inaccessible to color-blind users.

### 7.3 DiagramNode — Shared Global State

```typescript
// DiagramNode.tsx
const diagramContent = useAtomValue(diagramContentAtom);
```

All `DiagramNode` instances read the same global atom. If a user creates multiple diagram nodes, they all display identical content. The content should be stored in `node.data.diagramContent` per-node.

### 7.4 Content Isolation

ReactFlow nodes are rendered as React components inside the flow viewport. Their internal content (text inputs, buttons) can bubble events to the canvas. The codebase has no explicit `event.stopPropagation()` barriers or `noDragClassName` / `noWheelClassName` attributes on interactive content within nodes. **Content within nodes can accidentally trigger canvas pan/zoom.**

---

## 8. Diagramming Audit

### 8.1 Diagram Mode Integration

Switching to `diagram` mode auto-creates a `DiagramNode` if none exists (L127-142). The `DiagramToolbar` appears. The `DiagramNode` wraps a `MermaidDiagram` component.

**Issues:**

- **Single diagram limitation** — mode switch checks `prev.some(n => n.type === 'diagram')` and skips creation if any diagram node exists. User cannot create multiple diagrams intentionally.
- **No diagram-to-artifact linking** — diagram components cannot reference or be linked to artifact nodes.
- **Diagram toolbar** appears/disappears on mode toggle but has no "save" or "export" action — content persists only via the global atom (which is shared, per §7.3).

### 8.2 SmartGuides — Non-Functional Stub

```typescript
// SmartGuides.tsx — 27 lines
// Renders empty <Box> elements with no SVG/Canvas drawing
```

This component is imported and rendered but produces no visual output. It occupies DOM space without purpose.

---

## 9. Design System & Visual Polish

### 9.1 Component Library Compliance

| Import | Files | Correct? |
|---|---|---|
| `@ghatana/ui` | ~95% of files | ✅ Yes |
| `@/components/ui` | 5 files | ❌ Wrong barrel — should use `@ghatana/ui` |
| `@mui/material` | `PanelDock.tsx` | ❌ **Direct MUI import — forbidden by project rules** |

### 9.2 Inline Styles vs Tailwind

ReactFlow's `<Controls>` and `<MiniMap>` use inline `style` objects (L1270-1290, L1296-1310). This is acceptable for ReactFlow-specific components that don't support `className`. However, 15+ other locations use inline styles where Tailwind classes would suffice.

### 9.3 Color System

- Minimap node colors use hardcoded hex values (`#ef5350`, `#66bb6a`, `#42a5f5`, `#e0e0e0`) — not design tokens.
- Edge marker color is hardcoded `#1976d2` — MUI Blue 700, not a design token.
- Background grid color is `#aaa` — not a token.

### 9.4 Dark Mode

Sketch toolbar references `dark:bg-gray-900` (Tailwind dark variant) but the canvas `<ReactFlow>` component does not apply dark-mode theming. **Dark mode would show a white canvas with dark-mode chrome — visual collision.**

---

## 10. Accessibility & Keyboard-First UX

### 10.1 WCAG Compliance

| Criterion | Status | Detail |
|---|---|---|
| `role="application"` on canvas | ❌ Missing | Canvas wrapper is a plain `<Box>` with no role |
| Keyboard node traversal (Tab/Arrow) | ❌ Missing | No mechanism to move focus between nodes via keyboard |
| `aria-label` on canvas | ❌ Missing | No label on the interactive region |
| `aria-live` for state changes | ❌ Missing | No live region announces undo/redo, node creation, mode changes |
| Skip-to-content link | ❌ Missing | No way to bypass the left panel |
| Focus trap in modals | ⚠️ Unclear | Depends on `@ghatana/ui` `Dialog` implementation |
| `prefers-reduced-motion` | ❌ Missing | All animations (800ms zoom, 200ms opacity) are unconditional |
| Color-only status encoding | ❌ Fail | Minimap and ArtifactNode use color-only (red/green/amber) for status — no icon/pattern differentiation |
| Screen reader announcements | ❌ Missing | Node count, zoom level, mode changes are not announced |

### 10.2 Keyboard Shortcut Discoverability

- No shortcut cheat-sheet or help overlay.
- `CommandPalette` lists some commands but is unreachable via ⌘K (dead branch).
- Tooltips on toolbar buttons show shortcut hints but with 500ms delay.

### 10.3 Focus Management

- Escape key correctly blurs active input (L735) — **good**.
- No focus ring styling on canvas nodes — keyboard selection is invisible.
- No roving tabindex on node list.

---

## 11. Performance & Scalability Audit

### 11.1 Rendering Pipeline

```
artifacts (server) → generatedNodes (useMemo) → nodesAtom (useEffect sync)
→ useComputedView (viewport cull + filter) → styledNodes (useMemo) → ReactFlow
```

**Issues:**

- `useEffect` sync from `generatedNodes` → `nodesAtom` (L461-463) is a **wasteful extra render cycle.** Every server data change triggers: render with old atoms → effect fires → atoms update → re-render with new atoms. Should be a derived atom or direct memo.
- `styledNodes` creates new objects on every render for all nodes (spread + new `style` + new `data`) — breaks React.memo in node components.
- `computedView` applies viewport culling but the viewport atom may lag — nodes can pop in/out at viewport edges.

### 11.2 Spatial Index Rebuild

```typescript
useEffect(() => {
    spatialIndexAPI.buildIndex(nodes);
}, [nodes]);
```

Full O(n log n) rebuild on **every** nodes array change. For 500 nodes, this sends the entire array to the worker on every drag position update that passes through `onNodesChange`. The structural/transient split (§4.3) mitigates this somewhat, but `onNodeDragStop` still triggers a full rebuild.

### 11.3 Unused Performance Infrastructure (711 LOC)

| File | LOC | Status |
|---|---|---|
| `LODRenderer.tsx` | 199 | Fully implemented level-of-detail renderer — **never imported** |
| `PerformanceOptimization.tsx` | 513 | FPS monitor, virtualization, memoized node renderer — **never imported** |

These components implement exactly the optimizations the canvas needs (viewport culling, LOD rendering, FPS monitoring) but are completely disconnected from `CanvasWorkspace.tsx`.

### 11.4 N+1 API Calls

`ArtifactNode` calls `useCodeAssociations(data.id)` individually. With 100 nodes on screen, this fires 100 independent API requests. Should be a single batch query at the workspace level, injected via node data.

### 11.5 Memory Leaks

- `useCanvasPersistence.ts` uses a module-level `Map` as a singleton store with no cleanup — entries persist across component unmounts and route changes.
- `pushHistoryAtom` stores full `{ nodes, edges }` snapshots with **no size cap** — undo history grows without bound.
- `spatialIndexService.ts` `resolvers` Map: entries are added on each query but never deleted after resolution.

---

## 12. Extensibility Audit

### 12.1 Plugin System — Dead Code

```
DefaultPlugins.tsx    — 405 LOC — Plugin factory definitions
AccessibilityTool.tsx — 221 LOC — Mock contrast checker tool
LayoutTool.tsx        — 142 LOC — Auto-layout tool
```

A full plugin architecture with tool registration, activation, and rendering is defined but **never wired into `CanvasWorkspace.tsx`.** The canvas has no `<PluginHost>` or `usePlugins()` integration point.

### 12.2 Canvas Registry

`CanvasRegistryProvider` / `useCanvasRegistry()` provides a typed registry for node and edge types. This is **well-designed** — adding a new node type requires registering it in the provider. However, the `as any` casts weaken the type safety.

### 12.3 Panel System

`PanelManager` + `WorkspacePanelConfig` provides a draggable, dockable panel system. Panels are defined as data with JSX `content` fields in a `useMemo` (L1095-1165). This is functional but:

- JSX in `useMemo` data is an anti-pattern — it couples rendering to data definition.
- Panel positions use `window.innerWidth` at memo time — breaks on resize.
- No panel persistence (positions reset on every render).

### 12.4 Command Palette — Underconnected

`CommandPalette.tsx` (596 lines) defines 15+ handler props:

```typescript
onFitView, onZoomIn, onZoomOut, onToggleGrid, onToggleMinimap,
onToggleGuides, onExportSVG, onExportPNG, onSelectAll, onDeselectAll,
onAlignHorizontal, onAlignVertical, onDistributeHorizontal, onDistributeVertical,
onGroup, onUngroup, onLock, onUnlock, ...
```

`CanvasWorkspace` passes only **4**: `onFitView`, `onZoomIn`, `onZoomOut`, and empty `actions=[]`. The remaining 11+ props are undefined — their commands either silently don't render or fail without error.

---

## 13. Prioritized Refactor Plan

### Phase 0: Critical Fixes (0-2 days)

| # | Fix | Impact | Effort | File(s) |
|---|---|---|---|---|
| P0-1 | **Fix dead ⌘K branch** — change second condition to `⌘⇧K` or use toggle pattern | Runtime bug | 30 min | `CanvasWorkspace.tsx` L738-747 |
| P0-2 | **Fix ArtifactNode `id` reference** — ensure ghost actions use `data.id` | Runtime crash | 30 min | `ArtifactNode.tsx` |
| P0-3 | **Fix `useEffect` deps** — add all missing dependencies to keyboard handler | Stale closure bugs | 30 min | `CanvasWorkspace.tsx` L818 |
| P0-4 | **Remove mock presence data** — show empty state or hide indicator | Misleading UX | 15 min | `CanvasWorkspace.tsx` L349-363 |
| P0-5 | **Fix DiagramNode shared atom** — read content from `node.data` instead of global atom | Data corruption | 1 hr | `DiagramNode.tsx`, `canvasAtoms.ts` |
| P0-6 | **Remove hardcoded metrics** — either connect real FPS measurement or remove panel | Misleading data | 30 min | `CanvasWorkspace.tsx` L1147-1149 |
| P0-7 | **Fix MUI import** — replace `@mui/material` with `@ghatana/ui` in PanelDock | Design system violation | 30 min | `PanelDock.tsx` |
| P0-8 | **Add Worker error handler** — attach `onerror` + timeout to spatial index worker | Potential hang | 1 hr | `spatialIndexService.ts` |

### Phase 1: Architecture Cleanup (1-2 weeks)

| # | Fix | Impact | Effort |
|---|---|---|---|
| P1-1 | **Unify keyboard handling** — delete `useCanvasKeyboard.ts`, merge `useCanvasNavigation` into a single `useCanvasShortcuts` hook; remove inline `useEffect` | Eliminates 3→1 system | 2 days |
| P1-2 | **Unify history system** — choose ONE (atom-based undo/redo in `canvasAtoms.ts`); delete `useCanvasHistory.ts` + `useCanvasPersistence.ts`; add history size cap (e.g., 100 entries) | Eliminates 3→1 system, fixes memory leak | 2 days |
| P1-3 | **Unify selection system** — use ReactFlow's `onSelectionChange` to sync `selectedNodesAtom`; delete `useCanvasSelection.ts` | Eliminates 3→1 system | 1 day |
| P1-4 | **Decompose God Component** — extract from `CanvasWorkspace.tsx`: `useCanvasHandlers()` (CRUD callbacks), `useCanvasSync()` (artifact→node sync), `useCanvasDragDrop()` (DnD), `CanvasToolbars` (mode-specific toolbars), `CanvasPanels` (panel config) | Testability, maintainability | 3 days |
| P1-5 | **Delete dead code** — remove `useCanvasKeyboard.ts`, `useCanvasSelection.ts`, `LODRenderer.tsx`, `PerformanceOptimization.tsx`, `SmartGuides.tsx`, `SimplifiedCanvasWorkspace.tsx`, `DefaultPlugins.tsx`, `AccessibilityTool.tsx`, `LayoutTool.tsx` | -1,400 LOC | 1 day |
| P1-6 | **Eliminate dual data model** — remove `CanvasElement`/`CanvasConnection` layer; use ReactFlow `Node`/`Edge` as single source of truth; fix `transform.ts` `'undefined'` bug | Eliminates sync bugs | 2 days |
| P1-7 | **Fix N+1 queries** — batch `useCodeAssociations` into workspace-level query; inject results via `node.data` | Performance | 1 day |
| P1-8 | **Replace `@/components/ui` imports** — remap 5 files to `@ghatana/ui` | Design system compliance | 2 hrs |

### Phase 2: Production Quality (1-2 months)

| # | Fix | Impact | Effort |
|---|---|---|---|
| P2-1 | **Add `role="application"` + ARIA** — announce node count, zoom level, mode changes via `aria-live`; add keyboard node traversal (Arrow keys + Tab) | Accessibility | 1 week |
| P2-2 | **Wire LODRenderer** — integrate the existing (but unused) `LODRenderer.tsx` into the render pipeline; replace viewport culling in `useComputedView` | Performance at scale | 1 week |
| P2-3 | **Connect CommandPalette** — pass all 15+ handler props from `CanvasWorkspace`; enable ⌘K → palette (not AI modal) | Power-user productivity | 3 days |
| P2-4 | **Implement real performance monitoring** — use `requestAnimationFrame` loop or the existing `PerformanceOptimization.tsx` FPS monitor | Observability | 3 days |
| P2-5 | **Incremental spatial index** — replace full `buildIndex` with `insert`/`remove`/`update` operations on the RBush worker | Performance at scale | 1 week |
| P2-6 | **Add `snapToGrid`** — enable ReactFlow's `snapToGrid` with configurable grid size (16px default) | Precision UX | 2 hrs |
| P2-7 | **Implement semantic zoom** — make `AbstractionLevelNavigator` drill-down actually filter/cluster nodes by abstraction level | Feature completion | 2 weeks |
| P2-8 | **Error boundaries + toast notifications** — replace all `console.error/warn` with user-visible feedback via toast system | Error UX | 1 week |
| P2-9 | **`prefers-reduced-motion` support** — wrap all `duration` parameters in a motion preference check | Accessibility | 2 days |
| P2-10 | **Dark mode canvas theming** — apply dark-mode tokens to ReactFlow background, grid, controls, minimap | Visual consistency | 3 days |

---

## 14. Key Code Patterns

### 14.1 Recommended: Extract Canvas Shortcuts Hook

```typescript
// hooks/useCanvasShortcuts.ts — SINGLE keyboard handler
export function useCanvasShortcuts(opts: {
  onUndo: () => void;
  onRedo: () => void;
  onCopy: () => void;
  onPaste: () => void;
  onZoomToPhase: (phase: LifecyclePhase) => void;
  onOpenCommandPalette: () => void;
  onOpenAI: () => void;
  canUndo: boolean;
  canRedo: boolean;
  enabled: boolean;
}) {
  useEffect(() => {
    if (!opts.enabled) return;
    
    const handler = (e: KeyboardEvent) => {
      // Input guard
      const el = document.activeElement as HTMLElement;
      if (el?.tagName === 'INPUT' || el?.tagName === 'TEXTAREA' 
          || el?.isContentEditable) {
        if (e.key === 'Escape') el.blur();
        return;
      }
      
      const mod = e.metaKey || e.ctrlKey;
      
      if (mod && e.key === 'k' && !e.shiftKey) {
        e.preventDefault();
        opts.onOpenCommandPalette();
      } else if (mod && e.key === 'k' && e.shiftKey) {
        e.preventDefault();
        opts.onOpenAI();
      } else if (mod && e.key === 'z' && !e.shiftKey && opts.canUndo) {
        e.preventDefault();
        opts.onUndo();
      } else if (mod && (e.shiftKey && e.key === 'z' || e.key === 'y') && opts.canRedo) {
        e.preventDefault();
        opts.onRedo();
      } else if (mod && e.key === 'c') {
        e.preventDefault();
        opts.onCopy();
      } else if (mod && e.key === 'v') {
        e.preventDefault();
        opts.onPaste();
      } else if (mod && /^[1-7]$/.test(e.key)) {
        e.preventDefault();
        const phases = Object.values(LifecyclePhase);
        opts.onZoomToPhase(phases[parseInt(e.key) - 1]);
      }
    };
    
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [opts]); // Single stable opts object via useRef or useMemo
}
```

### 14.2 Recommended: Bounded History Atom

```typescript
// canvasAtoms.ts — Replace unbounded history
const MAX_HISTORY = 100;

const pushHistoryAtom = atom(null, (get, set) => {
  const nodes = get(nodesAtom);
  const edges = get(edgesAtom);
  const past = get(historyPastAtom);
  
  // Enforce size cap
  const newPast = [...past, { nodes, edges }].slice(-MAX_HISTORY);
  set(historyPastAtom, newPast);
  set(historyFutureAtom, []); // Clear redo stack on new action
});
```

### 14.3 Recommended: Per-Node Diagram Content

```typescript
// DiagramNode.tsx — Read from node data, not global atom
const DiagramNode: React.FC<NodeProps> = ({ data }) => {
  const content = data.diagramContent ?? 'graph TD\n  A-->B';
  const type = data.diagramType ?? 'mermaid';
  
  return (
    <NodeWrapper>
      <MermaidDiagram content={content} type={type} />
    </NodeWrapper>
  );
};
```

### 14.4 Recommended: Worker Error Handling

```typescript
// spatialIndexService.ts — Add timeout + error handling
const WORKER_TIMEOUT = 5000;

function queryWorker<T>(message: WorkerMessage): Promise<T> {
  return new Promise((resolve, reject) => {
    const id = nextId++;
    const timer = setTimeout(() => {
      resolvers.delete(id);
      reject(new Error(`Spatial worker timeout after ${WORKER_TIMEOUT}ms`));
    }, WORKER_TIMEOUT);
    
    resolvers.set(id, { 
      resolve: (val: T) => { clearTimeout(timer); resolve(val); },
      reject: (err: Error) => { clearTimeout(timer); reject(err); },
    });
    
    worker.postMessage({ ...message, id });
  });
}

worker.onerror = (event) => {
  // Reject all pending queries
  for (const [id, { reject }] of resolvers) {
    reject(new Error(`Worker error: ${event.message}`));
  }
  resolvers.clear();
};
```

---

## 15. Maturity Score & Top 10 Fixes

### Maturity Scorecard

| Dimension | Score (0-10) | Notes |
|---|---|---|
| **UX Completeness** | 4 | Good zero-state, broken ⌘K, mock data |
| **Interaction Model** | 3 | 3 competing keyboard systems, no mode docs |
| **Canvas Engine** | 5 | Smart onNodesChange, correct coordinate transforms, laggy viewport |
| **Drag & Drop** | 4 | Correct math, no visual feedback, no cancel |
| **Zoom & Pan** | 5 | Good phase navigation, missing pinch/snap config |
| **Content Nodes** | 3 | Runtime bugs, N+1 queries, shared diagram state |
| **Diagramming** | 2 | Single-diagram limitation, stub SmartGuides |
| **Design System** | 4 | Mostly @ghatana/ui, 1 MUI violation, no tokens for colors |
| **Accessibility** | 1 | Near-zero ARIA, no keyboard traversal, color-only encoding |
| **Performance** | 3 | Good transient-change filter, full rebuild spatial index, 711 LOC unused perf code |
| **Extensibility** | 3 | Registry exists, plugin system dead, CommandPalette 4/15 props connected |
| **Code Quality** | 2 | God Component, 10+ `as any`, 20+ console.log error handling |
| **Overall** | **3.2** | **Prototype-to-alpha transition; not production-ready** |

### Hard Requirements Checklist

| # | Requirement | Met? | Evidence |
|---|---|---|---|
| 1 | **Camera Abstraction** (viewport as first-class object) | ⚠️ Partial | Viewport stored in `useState` + atom, but not abstracted — raw `{ x, y, zoom }` |
| 2 | **Cursor-Centered Zoom** | ✅ Yes | ReactFlow default behavior; confirmed by `zoomOnScroll={true}` |
| 3 | **Scale-Aware Overlays** (UI elements don't scale with zoom) | ✅ Yes | Toolbar, panels, minimap, controls are in ReactFlow `<Panel>` slots — independent of zoom |
| 4 | **Deterministic Hit-Testing** (spatial index for collision) | ⚠️ Partial | RBush spatial index exists but has no error handling, full rebuild on every change |
| 5 | **Advanced DnD** (ghost preview, snap, cross-boundary) | ❌ No | No drag preview, no drop zone highlighting, snap via spatial index but async with no debounce |
| 6 | **Isolated Node Content** (nodes don't leak events to canvas) | ❌ No | No `noDragClassName`, no `event.stopPropagation()` barriers in node components |
| 7 | **Command-Based Undo/Redo** (single authoritative stack) | ❌ No | 3 independent history systems; atom-based has no size cap |
| 8 | **Discoverable Shortcuts** (cheat-sheet, palette shows bindings) | ❌ No | ⌘K dead branch; no shortcut overlay; CommandPalette mostly disconnected |
| 9 | **Scalable Performance** (virtualization, LOD, incremental index) | ❌ No | Virtualization/LOD code exists but is never consumed; spatial index is brute-force |
| 10 | **Accessible Canvas** (WCAG AA keyboard+screen reader) | ❌ No | No roles, no ARIA, no keyboard traversal, color-only encoding |

**Requirements met: 2.5 / 10** (2 full + 2 partial)

### Top 10 Fixes — Ordered by Impact / Effort

| Priority | Fix | Impact | Effort | Section |
|---|---|---|---|---|
| 🔴 1 | Fix dead ⌘K branch (⌘K → Palette, ⌘⇧K → AI) | Unblocks power users | 30 min | §3.2 |
| 🔴 2 | Fix ArtifactNode `id` reference | Prevents runtime crash | 30 min | §7.2 |
| 🔴 3 | Fix `useEffect` dependencies | Prevents stale closures | 30 min | §3.3 |
| 🔴 4 | Unify keyboard handling → single hook | Eliminates conflicts | 2 days | §3.2 |
| 🔴 5 | Unify history → atom-based with size cap | Single undo stack | 2 days | §4.2 |
| 🟡 6 | Decompose God Component | Testability | 3 days | §4 |
| 🟡 7 | Delete ~1,400 LOC dead code | Maintainability | 1 day | §11.3, §12.1 |
| 🟡 8 | Fix DiagramNode shared atom | Multi-diagram support | 1 hr | §7.3 |
| 🟡 9 | Add Worker error handling + timeout | Prevents hangs | 1 hr | §5.3 |
| 🟢 10 | Add `role="application"` + keyboard traversal | Accessibility | 1 week | §10 |

---

## Appendix: File Inventory

| File | LOC | Status | Purpose |
|---|---|---|---|
| `CanvasWorkspace.tsx` | 1,414 | 🔴 God Component | Main orchestrator |
| `canvasAtoms.ts` | ~230 | ⚠️ Unbounded history | Jotai state |
| `ArtifactNode.tsx` | ~260 | 🔴 `id` bug | Primary node type |
| `DiagramNode.tsx` | ~40 | 🔴 Shared atom | Diagram node |
| `CommandPalette.tsx` | 596 | ⚠️ 4/15 props | Power-user commands |
| `InspectorPanel.tsx` | 557 | ✅ Functional | Artifact detail panel |
| `PerformanceOptimization.tsx` | 513 | ☠️ Dead code | Unused virtualization |
| `DefaultPlugins.tsx` | 405 | ☠️ Dead code | Unused plugin system |
| `EnhancedSketchLayer.tsx` | 380 | ⚠️ Different atom | Konva sketch overlay |
| `useComputedView.ts` | ~300 | ⚠️ Dead RBAC filters | View composition |
| `useCanvasNavigation.ts` | 263 | ⚠️ Conflict | Duplicate shortcuts |
| `AccessibilityTool.tsx` | 221 | ☠️ Dead code | Unused mock tool |
| `SimplifiedCanvasWorkspace.tsx` | 205 | ☠️ Dead code | Parallel implementation |
| `LODRenderer.tsx` | 199 | ☠️ Dead code | Unused LOD |
| `useCanvasPersistence.ts` | ~180 | ☠️ Dead / leak | Memory leak singleton |
| `LayoutTool.tsx` | 142 | ☠️ Dead code | Unused tool |
| `useCanvasKeyboard.ts` | ~110 | ☠️ Dead code | Unused keyboard hook |
| `transform.ts` | ~110 | 🔴 `'undefined'` bug | Data model bridge |
| `useCanvasHistory.ts` | ~105 | ☠️ Duplicate | Second history stack |
| `useCanvasSelection.ts` | ~100 | ☠️ Dead code | Third selection system |
| `spatialIndexService.ts` | ~55 | ⚠️ No error handling | Worker communication |
| `spatial.worker.ts` | ~40 | ✅ Functional | RBush worker |
| `AlignmentGuides.tsx` (root) | ~40 | ✅ Functional | Atom-based guides |
| `guides/AlignmentGuides.tsx` | 53 | ☠️ Duplicate | Props-based guides |
| `SmartGuides.tsx` | 27 | ☠️ Stub | Empty render |
| `CanvasRegistryProvider.tsx` | ~50 | ⚠️ `as any` | Node/edge type registry |

**Total dead/duplicate code: ~1,906 LOC** (including duplicate AlignmentGuides and SmartGuides stub)

---

*End of Principal Architecture Audit.*
