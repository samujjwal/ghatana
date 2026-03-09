# Canvas Engine Audit — Principal Architecture Review
**Date:** 2026-02-28  
**Scope:** Canvas engine internals — camera, coordinate spaces, transform pipeline, render layering, event routing, hit testing, selection model, spatial indexing.  
**Auditor:** Principal UI/UX Architecture Review

---

## 1. Executive Summary

- **Three parallel viewport stores exist simultaneously.** `state/atoms/viewportAtom.ts`, `canvasAtom.viewportPosition`/`zoomLevel`, and `workspace/canvasAtoms.ts:cameraAtom` all represent the same camera state. None are synchronized. Reads from different atoms yield stale, contradictory camera values.
- **Two entirely separate canvas state systems coexist.** The legacy `canvasAtom` (elements/connections) powers `CanvasScene.tsx`. The modern `nodesAtom`/`edgesAtom` powers `CanvasWorkspace.tsx`. Mutations to one are invisible to the other.
- **Three coordinate-space utility modules exist and none are actually used.** `platform/typescript/canvas/src/hybrid/coordinates.ts`, `canvas/utils/transform.ts`, and ReactFlow's `reactFlowInstance.screenToFlowPosition()` all do screen→world conversion. Product-layer components exclusively use the ReactFlow API, making the platform utilities dead code.
- **Spatial index rebuilds completely on every node change.** `useEffect(() => spatialIndexAPI.buildIndex(nodes), [nodes])` in CanvasWorkspace fires a full worker-side `BUILD` on every atom update — including mouse-move during drag. A 500-node canvas fires 60 full rebuilds per second during drag.
- **The selection model is permanently broken.** `onSelectionChange` in `CanvasWorkspace.tsx` contains only a comment (`// Sync ReactFlow selection → selectedNodesAtom`) with no implementation. `selectedNodesAtom` is never written by ReactFlow selection events, only by external keyboard/handler code.
- **Spatial hit-testing uses unmeasured node dimensions.** `nodeToItem` falls back to `width: 200, height: 100` until ReactFlow's `ResizeObserver` fires. All spatial queries during the first render cycle are incorrect — snapping targets wrong nodes on first drag.
- **`canvasToGraph`/`graphToCanvas` are identity functions** that add a false layer of abstraction between coordinate spaces, causing engineers to treat "canvas" and "graph" as distinct spaces when they are identical in ReactFlow's model.
- **No principled render layer stack.** `AlignmentGuides`, `GhostNodes`, `SpatialZones` render inside the ReactFlow tree. `EnhancedSketchLayer` is an absolute-positioned DOM sibling. No z-index contract exists between layers, so pointer events bleed unpredictably.
- **`CanvasScene.tsx` uses `@dnd-kit/core`; `CanvasWorkspace.tsx` uses ReactFlow native drag.** Two incompatible drag systems are active simultaneously in different routes.
- **Float drift accumulates at extreme zoom.** Zone placement positions (`PHASE_ZONE_CENTERS`) are hardcoded integer pixel values stored as IEEE-754 doubles. After many position mutations via `MoveNodesCommand`, positions accumulate sub-pixel drift that becomes visible at `zoom > 3`.

---

## 2. User Experience Audit

### Remove for simplicity
- **`CanvasScene.tsx` entirely.** It is the legacy route, uses the old state system, imports DnD-kit, and contradicts the new architecture at every layer. It creates mental overhead for every engineer and a maintenance trap.
- **`viewportAtom` in `state/atoms/viewportAtom.ts`.** This is a Konva-era remnant and is now shadowed by `cameraAtom`. Its `transformAtom` derived atom is unused. Delete the entire file.
- **`canvasToGraph` / `graphToCanvas` identity functions** in `platform/typescript/canvas/src/hybrid/coordinates.ts`. They add noise without value. ReactFlow's "flow" (graph) coordinate space IS the canvas space. Remove these two functions and their call sites.
- **`CanvasState.history` snapshot array** in the legacy `canvasAtom`. The command pattern in `canvasCommands.ts` supersedes this. Having both creates confusion about which undo system is canonical.
- **`data: Record<string, any>`** in `CanvasElement` of the legacy atom. This `any` type is the entry point for all type erosion in the old system. Eliminate it when the legacy system is removed.

### Unify for consistency
- **Camera state:** Single `cameraAtom` in `workspace/canvasAtoms.ts`. The `CameraState` interface (`{x, y, zoom}`) is already correct. All camera reads must use this atom; the other two viewport atoms must be removed.
- **Coordinate utilities:** The platform utilities in `coordinates.ts` should be the canonical implementation, with ReactFlow's `screenToFlowPosition` being a thin wrapper call into them. Right now it's the reverse — platform code is dead, ReactFlow API is the de-facto standard. Consolidate to a single module.

---

## 3. Interaction Model Audit

### Selection model is broken at the data layer
`selectedNodesAtom` is a `string[]` of IDs declared in Jotai. ReactFlow maintains its own internal selection state in its Zustand store. These two stores are never reconciled.

**Evidence:** In `CanvasWorkspace.tsx`:
```tsx
onSelectionChange={({ nodes: sel }) => {
    // Sync ReactFlow selection → selectedNodesAtom
}}
```
The callback body is empty. ReactFlow selection (via click, rubber-band, Shift+click) never updates `selectedNodesAtom`. Handlers like `handleDeleteSelected` operate on `selectedNodesAtom` — they will always delete zero nodes when the user selects via mouse.

**Fix:** Wire the callback:
```tsx
onSelectionChange={({ nodes: sel, edges: selEdges }) => {
    setSelectedNodes(sel.map(n => n.id));
    setSelectedEdges(selEdges.map(e => e.id));
}}
```
Then remove `selectNodesOnDrag={false}` only if rubber-band should update selection — currently it can't because the callback is empty.

### `onNodesChange` suppresses drag position updates
```ts
const structural = changes.filter(c =>
    c.type !== 'position' || (c.type === 'position' && !c.dragging)
);
```
This discards all mid-drag position changes, meaning:
1. The spatial index cannot be updated incrementally during drag.
2. External position consumers (alignment guides) never receive position updates until drag ends — guides appear only on drop, not during movement.
3. `useNodePositions` cannot track velocity for future inertia/momentum features.

Position changes during drag should be allowed through. The concern was preventing atom churn — that is solved by batching, not filtering.

### Rubber-band selection coordinate mismatch
ReactFlow handles rubber-band selection entirely internally. Its internal hit test uses DOM bounding rects measured in screen space, then converts to flow space using the internal `useStore(s => s.transform)`. This internal transform is independent of `cameraAtom`. If `cameraAtom` and ReactFlow's internal Zustand store ever diverge (they will, since `cameraAtom` lags by one React render cycle), rubber-band selection includes/excludes wrong nodes.

---

## 4. Canvas Engine Audit

### Problem 1: Three parallel camera stores
| Store | Location | Written by | Read by |
|---|---|---|---|
| `viewportAtom` | `state/atoms/viewportAtom.ts` | Nobody (stale) | `transformAtom` (Konva, dead) |
| `canvasAtom.viewportPosition` | `state/atoms/canvasAtom.ts` | `handleInit`, `handleDrag` in CanvasScene | CanvasScene components |
| `cameraAtom` | `workspace/canvasAtoms.ts` | `CanvasWorkspace onMove` | CanvasWorkspace consumers |

At any given time, reading `cameraAtom.zoom` vs `canvasAtom.zoomLevel` will return different values. Any component that renders across both routes (e.g., shared overlay panels) will use the wrong zoom level for coordinate transforms.

**Root cause:** Three separate migrations (Konva → ReactFlow, old scene → new workspace) were done incrementally without removing the previous camera implementation.

**Fix:** One atom, one writer, zero alternatives. See Section 14 (Golden Architecture) for the canonical implementation.

### Problem 2: `canvasToGraph`/`graphToCanvas` identity functions introduce phantom complexity

In ReactFlow's model, there are exactly two coordinate spaces:
- **Screen space**: pixels relative to the browser viewport. `(clientX, clientY)`
- **Flow/world space**: pixels in the infinite canvas. `node.position`

The transform is: `world = (screen - [offset.x, offset.y]) / zoom`

`platform/typescript/canvas/src/hybrid/coordinates.ts` introduces a third "canvas" space that is mathematically identical to "graph" space:
```ts
export function canvasToGraph(point: Point): Point {
  return { x: point.x, y: point.y }; // identity!
}
```
This creates cargo-cult code where engineers call `canvasToGraph(screenToCanvas(p, vp))` instead of just `screenToCanvas(p, vp)`, hiding the actual transform behind two function calls.

**Fix:** Delete `canvasToGraph` and `graphToCanvas`. Rename `screenToCanvas` → `screenToWorld` and `canvasToScreen` → `worldToScreen` to match industry-standard terminology. Add a `ViewportStore` interface so the same functions work regardless of which atom provides the viewport.

### Problem 3: ReactFlow's internal transform is the ground truth, not `cameraAtom`

ReactFlow manages its own viewport in a Zustand store (`useStore(s => s.transform)`). `cameraAtom` is written from `onMove` — a React event that fires _after_ ReactFlow has already applied the transform to the DOM. This means:

- There is always a minimum **one frame of lag** between the actual DOM transform and what `cameraAtom` reports.
- Any overlay that reads `cameraAtom` to position itself is one frame behind the canvas content — visible as overlay jitter on fast pan/zoom.
- `onlyRenderVisibleElements` uses ReactFlow's internal viewport, not `cameraAtom` — so culling is performed against the true viewport while overlays draw against the stale one.

**Fix:** For overlays that must be pixel-accurate, read the transform from `useStore` via a ReactFlow context hook rather than from `cameraAtom`. Use `cameraAtom` only for application-level reactions (minimap, zoom display, analytics) that tolerate one-frame lag.

### Problem 4: Float accumulation in zone placement

`PHASE_ZONE_CENTERS` uses integer pixel values, but repeated `MoveNodesCommand` executions accumulate `Number` arithmetic drift:

```ts
moveTo[n.id] = { x: n.position.x + dx, y: n.position.y + dy };
```

After many keyboard moves (each adding `dx=8` or `dx=-8`), `position.x` can become `400.0000000000001` instead of `400`. At high zoom this manifests as sub-pixel positioning bugs and misaligned snap targets.

**Fix:** Round all position values to grid resolution before storing:
```ts
const snap = (v: number, grid = 0.5) => Math.round(v / grid) * grid;
moveTo[n.id] = { x: snap(n.position.x + dx), y: snap(n.position.y + dy) };
```

---

## 5. Drag & Drop Audit

### `@dnd-kit` vs ReactFlow native drag: two incompatible systems

`CanvasScene.tsx` wraps everything in `<DndContext>` from `@dnd-kit/core`. `CanvasWorkspace.tsx` uses ReactFlow's native `onNodeDrag`/`onNodeDragStop`. These two systems cannot coexist — `@dnd-kit` intercepts all pointer events and prevents ReactFlow's drag from getting them. The routes are separate so they don't literally conflict at runtime, but any attempt to share drag logic between them is impossible.

**Fix:** Delete `CanvasScene.tsx` and `@dnd-kit` dependency. ReactFlow native drag is the correct choice for canvas nodes.

### Snap-to-grid applies after commit, not during drag

In `useCanvasDragDrop.onNodeDrag`:
```ts
reactFlowInstance.setNodes(nds => nds.map(n => ...snap...));
```
This calls `setNodes` which triggers a React re-render. At 60fps drag, this creates 60 `setNodes` calls per second. ReactFlow's `applyNodeChanges` is designed for this but the intermediate snap positions are never fed back to ReactFlow's internal state synchronously — they go through atom → re-render → ReactFlow prop update, adding a full React cycle of lag between the cursor and the snapped visual.

**Fix:** Use ReactFlow's built-in `snapToGrid` and `snapGrid` props (already set to `[16,16]`). ReactFlow applies snap synchronously in its internal reducer before rendering, eliminating the React-cycle latency entirely. Remove the manual `setNodes` snap in `onNodeDrag`.

### Alignment guide queries fire on every pointer move

`spatialIndexAPI.findCollisions` is called on every `onNodeDrag` event via the `lastMsgIdRef` deduplication guard. Even with deduplication, the call still posts a message to the Web Worker on every animation frame. Worker `postMessage` is not free — it serializes the message and crosses the thread boundary.

**Fix:** Throttle the spatial query to 100ms or use a `requestAnimationFrame` gate. Alignment guides do not need 60fps update rate.

### Drag cancel restores positions but does not restore edges

In `useCanvasDragDrop`, Escape key handling:
```ts
reactFlowInstance.setNodes(nds => nds.map(n => 
    savedPositions[n.id] ? { ...n, position: savedPositions[n.id] } : n
));
```
Only node positions are restored. If a connection was made during the drag (a port hover triggered an auto-connect), that edge is not removed on cancel. The `dragStartPositionsRef` should capture edges too, or edge creation should be deferred until drag commit.

---

## 6. Zoom & Pan Audit

### Cursor-centered zoom: delegated to ReactFlow correctly

ReactFlow's zoom behavior is cursor-centered by default when `zoomOnScroll` is true. This is the correct approach and no custom implementation is needed. ✅

### Zoom bounds are defined but not enforced for `fitView`

`minZoom={0.1}` and `maxZoom={2}` are set on `<ReactFlow>`, but `zoom.handleFitView` calls `reactFlowInstance.fitView({ duration: 600 })` without passing `minZoom`/`maxZoom`. If the content bounding box is very small, `fitView` will zoom past `maxZoom=2` to fit it.

**Fix:**
```ts
reactFlowInstance.fitView({ duration: 600, minZoom: 0.1, maxZoom: 2 });
```

### Pinch zoom on mobile: `zoomOnPinch={true}` is set

This is correct. No issues. ✅

### `setCenter` ignores zoom bounds

`handleStartTask` calls:
```ts
reactFlowInstance.setCenter(x, y, { zoom: 1.2, duration: 600 });
```
`zoom: 1.2` is hardcoded. If the task node is very large (e.g., a diagram node), 1.2x may show only part of it. Should use `fitBounds` with the node's measured rect instead:
```ts
reactFlowInstance.fitBounds({ x, y, width: w, height: h }, { padding: 0.2, duration: 600 });
```

---

## 7. Arbitrary Content Nodes Audit

### `CanvasContentWrapper` is correct but not used in `DiagramNode`'s inner slots

`CanvasContentWrapper` applies `nodrag nopan nowheel` + pointer propagation stops. `DiagramNode` does apply these classes manually (`className="...nodrag nopan nowheel"`), but `MermaidDiagram` is a third-party component — if it ever attaches its own wheel/pointer listeners, they will still propagate. Using `CanvasContentWrapper` as the wrapping element guarantees isolation regardless of child behavior.

### Monaco editor isolation: not audited (no Monaco in current codebase)

The codebase references Monaco via comments and type stubs but no actual Monaco node component was found. When added, it must:
1. Render inside `CanvasContentWrapper`
2. Disable `pointer-events: none` on the overlay element during pan mode
3. Capture focus/blur to toggle `nodesDraggable` on the ReactFlow instance (Monaco Cmd+Z must not trigger canvas undo)

### `NodeResizer` added to `ArtifactNode` and `DiagramNode`: correct ✅

Both nodes now use `<NodeResizer minWidth={220} minHeight={80} isVisible={selected} />`. Handles are DOM elements inside the node — they correctly inherit the `nodrag` class boundary.

### Problem: `NodeResizer` handle pointer events leak when `interactionMode !== 'navigate'`

When `interactionMode === 'sketch'` or `'diagram'`, `nodesDraggable={false}` and `nodesConnectable={false}` are set, but `NodeResizer` handles are still interactive because `elementsSelectable` remains true. In sketch mode, clicking a node to resize it is a surprise — the user expects to draw.

**Fix:** Pass `isVisible={selected && interactionMode === 'navigate'}` to `NodeResizer` in both node components.

---

## 8. Diagramming Audit

### `MermaidDiagram` error state: not implemented

If `diagramContent` contains invalid Mermaid syntax, Mermaid throws synchronously inside the render. There is no error boundary around `<MermaidDiagram>`. A single malformed diagram will crash the entire canvas.

**Fix:** Add an `<CanvasErrorBoundary>` wrapper around `<MermaidDiagram>` in `DiagramNode`.

### Edge routing: ReactFlow default `bezier` — acceptable for current node count

With < 200 nodes, bezier routing is sufficient. At > 500 nodes, switch to `type: 'smoothstep'` for reduced visual noise. ✅ (not a blocker)

### No group/frame node type

The codebase has no `GroupNode` or `FrameNode` that can enclose multiple artifacts. This makes spatial organization of large canvases impossible without zone layers. Add a `FrameNode` that:
- Has `extend: true` (ReactFlow prop) to resize when children are dragged outside its bounds
- Renders a `NodeResizer`
- Uses a drop target to accept other node types

---

## 9. Design System & Visual Polish

### `NodeResizer` handles use ReactFlow default styles

ReactFlow's default resize handles are 7x7px dots. At zoom < 0.5 they become invisible. Override with explicit `handleStyle` that scales inversely with zoom:
```tsx
<NodeResizer
  handleStyle={{ width: 10 / zoom, height: 10 / zoom }}
  isVisible={selected}
/>
```
Read `zoom` from `cameraZoomAtom`.

### `Handle` elements use hardcoded `#2196F3`

All `<Handle style={{ background: '#2196F3' }}>` should reference CSS custom properties:
```tsx
style={{ background: 'var(--color-primary)' }}
```
The current hardcoded value breaks dark mode and theme switching.

### MiniMap colors are hardcoded

```tsx
nodeColor={(node) => {
    if (data.status === 'blocked') return 'var(--color-error, #ef5350)';
```
The `var()` fallback is correct but the rgba mask color `rgba(240, 242, 245, 0.6)` is hardcoded. When dark mode is active, the MiniMap background should be `rgba(17, 24, 39, 0.6)`.

---

## 10. Accessibility & Keyboard-First UX

### Focus model: `nodesFocusable={true}` is now set ✅

ReactFlow will add `tabIndex={0}` to nodes. However `ArtifactNode` also sets `tabIndex={selected ? 0 : -1}` — this creates duplicate tabIndex management. ReactFlow's outer wrapper div and the inner `<Card>` both have tabIndex, meaning Tab stops twice on each node.

**Fix:** Remove `tabIndex` from `ArtifactNode`'s `<Card>`. Let ReactFlow manage the node's outer tabIndex via `nodesFocusable`. The `<Card>` is inside the node's wrapper and should not be independently tabbable.

### ARIA: `role="application"` on canvas wrapper is correct ✅

`role="application"` tells screen readers this is an interactive region requiring custom keyboard handling. The live region `aria-live="polite"` for announcements is also correct.

### `onSelectionChange` not wired → keyboard selection announcements never fire

`selectedNodesAtom` is never updated by mouse selection (see Section 3). This means the accessibility announcement in `useCanvasAccessibility` ("Node X of Y: title") only fires for keyboard-driven selection, never mouse. From a screen reader's perspective, clicking a node produces no feedback.

### Missing: `aria-flowto` between connected nodes

For users navigating the graph with a screen reader, there is no way to traverse edges. Each `ArtifactNode` should include `aria-flowto="node-id-1 node-id-2"` listing the IDs of connected nodes. This is computed from `edges`:
```tsx
const outgoingEdgeTargets = edges
    .filter(e => e.source === id)
    .map(e => e.target)
    .join(' ');
// <div aria-flowto={outgoingEdgeTargets}>
```

---

## 11. Performance & Scalability Audit

### Critical: Full spatial index rebuild on every atom update (O(n) per frame during drag)

```ts
// CanvasWorkspace.tsx
useEffect(() => { spatialIndexAPI.buildIndex(nodes); }, [nodes]);
```

`nodes` is an array reference. Every `onNodesChange` call (including position changes during drag) creates a new array reference, triggering `buildIndex`. With 500 nodes, `buildIndex` serializes 500 node objects, sends them via `postMessage`, and the worker inserts them all into RBush.

**At 60fps during drag:** 60 × 500 = 30,000 node serializations per second. The worker can't keep up; the query queue backs up; alignment guides show stale data.

**Fix — incremental spatial index updates:**
```ts
// Track previous nodes
const prevNodesRef = useRef<Map<string, Node>>(new Map());

useEffect(() => {
    const prevMap = prevNodesRef.current;
    const nextMap = new Map(nodes.map(n => [n.id, n]));

    // Find added nodes
    for (const [id, node] of nextMap) {
        if (!prevMap.has(id)) spatialIndexAPI.insertNode(node);
    }

    // Find removed nodes
    for (const [id] of prevMap) {
        if (!nextMap.has(id)) spatialIndexAPI.removeNode(id);
    }

    // Find moved nodes (only during non-drag state, or throttled during drag)
    for (const [id, node] of nextMap) {
        const prev = prevMap.get(id);
        if (prev && (prev.position.x !== node.position.x || prev.position.y !== node.position.y)) {
            spatialIndexAPI.removeNode(id);
            spatialIndexAPI.insertNode(node);
        }
    }

    prevNodesRef.current = nextMap;
}, [nodes]);
```

The worker already has `INSERT` and `REMOVE` message handlers. They are just not used from the product layer.

### `nodeToItem` uses unmeasured dimensions for spatial hit testing

```ts
function nodeToItem(n: Node): SpatialItem {
    const w = n.measured?.width ?? 200;  // WRONG until DOM measures
    const h = n.measured?.height ?? 100; // WRONG until DOM measures
    ...
}
```

On first render, all spatial items have `width=200, height=100` regardless of actual node size. Alignment snapping and collision detection during the first drag are systematically wrong.

**Fix:** Gate spatial index insertion on `n.measured` being defined:
```ts
function nodeToItem(n: Node): SpatialItem | null {
    if (!n.measured?.width || !n.measured?.height) return null;
    ...
}

// In buildIndex:
const items = nodes.map(nodeToItem).filter(Boolean);
```

And in `CanvasWorkspace`, rebuild the index when `onNodesChange` reports a `dimensions` change type (ReactFlow fires this when ResizeObserver triggers):
```ts
const onNodesChange = (changes: NodeChange[]) => {
    const hasDimensionChange = changes.some(c => c.type === 'dimensions');
    if (hasDimensionChange) {
        // Schedule incremental spatial index update
        scheduleSpatialUpdate();
    }
    ...
};
```

### `onlyRenderVisibleElements` is set but not correctly bounded to the camera

`onlyRenderVisibleElements` uses ReactFlow's internal viewport (Zustand) to cull nodes. Since `cameraAtom` lags ReactFlow's internal state by one frame, and the culling is done against the internal state, the culling itself is correct. However, any external effect (e.g., the `prevNodesRef` incremental spatial index) is computed against the full `nodesAtom` array — it cannot distinguish visible from invisible nodes. This is acceptable since the spatial index should index all nodes, not just visible ones.

### `React.memo` comparators on `ArtifactNode` and `DiagramNode`: correct ✅

Both use custom comparators. `ArtifactNode`'s comparator checks `id`, `selected`, `status`, `title`, `phase`, `isGhost`, `linkedCount`, `blockerCount`. This covers all data-driven re-render triggers. ✅

### Global `commandRegistryAtom` causes re-render on every command registration

`commandRegistryAtom` is an `atom<CanvasCommandAction[]>([])`. Every component that calls `useAtomValue(commandRegistryAtom)` (including `CommandPalette`) re-renders whenever any plugin registers a new command. Commands are registered on mount — this causes a burst of re-renders at workspace initialization.

**Fix:** Use `atom<Map<string, CanvasCommandAction>>` keyed by `id`. `CommandPalette` can derive a sorted display list with a derived atom. Map identity only changes when entries change, not on every render.

---

## 12. Extensibility Audit

### Node registry: `useCanvasRegistry()` is the correct abstraction ✅

`nodeTypes` and `edgeTypes` are derived from a registry hook, not hardcoded in the ReactFlow props. New node types require only registration, not modification of `CanvasWorkspace`. ✅

### Plugin commands: `commandRegistryAtom` + `registerCommandsAtom` is the correct abstraction ✅

Plugins can call `set(registerCommandsAtom, newCommands)` on mount and `set(unregisterCommandsAtom, ids)` on unmount. The CommandPalette reads from the atom. ✅

### Schema versioning: absent

`ArtifactNodeData` has no version field. When the schema changes (new required field), persisted data in localStorage/IndexedDB will fail to deserialize silently. `useNodePositions` writes raw `{x, y}` objects to `canvas-positions-v1-{projectId}` — the `v1` in the key is a manual convention, not enforced.

**Fix:** Add a `_v: number` discriminant to every persisted type. Add a migration runner that upgrades stored data on load:
```ts
interface NodePositionStore {
    _v: 1;
    positions: Record<string, NodePosition>;
}
```

### `CanvasCommand` interface has no versioning

If a command is serialized to IndexedDB for collaborative undo, the `CanvasCommand` class instances cannot be deserialized (they carry prototype methods). For offline-first collaboration, commands need a serializable form (plain DTO + server-side rehydration).

---

## 13. Prioritized Refactor Plan

### Phase 0 — Fix Now (0–2 days), no regressions permitted

| # | Fix | File | Impact |
|---|---|---|---|
| 1 | **Wire `onSelectionChange`** → `setSelectedNodes(sel.map(n => n.id))` | `CanvasWorkspace.tsx:L~555` | Critical: delete/copy/paste broken for mouse selection |
| 2 | **Incremental spatial index** — replace `buildIndex` useEffect with the `prevNodesRef` diff approach | `CanvasWorkspace.tsx` | Critical: 30k serializations/sec during drag |
| 3 | **Gate `nodeToItem` on `n.measured`** — skip nodes with no measured dimensions | `spatialIndexService.ts` | Critical: spatial queries wrong on first render |
| 4 | **Remove manual snap in `onNodeDrag`** — use ReactFlow's `snapToGrid`/`snapGrid` props instead | `useCanvasDragDrop.ts` | High: eliminates React-cycle snap latency |
| 5 | **Pass zoom bounds to `fitView`** and use `fitBounds` in `handleStartTask` | `useCanvasZoom.ts` | Medium: prevents zoom escaping bounds |
| 6 | **`NodeResizer` visibility gate** — `isVisible={selected && interactionMode === 'navigate'}` | `ArtifactNode.tsx`, `DiagramNode.tsx` | Medium: prevents resize handles in sketch mode |

### Phase 1 — Architecture Repair (1–2 weeks)

| # | Fix | Effort |
|---|---|---|
| 7 | **Delete `CanvasScene.tsx`** and the entire legacy canvas route. Migrate any features not yet in `CanvasWorkspace.tsx` first. | 3d |
| 8 | **Delete `state/atoms/viewportAtom.ts`** and `canvasAtom.viewportPosition`/`zoomLevel`. Redirect all reads to `cameraAtom`. | 1d |
| 9 | **Consolidate coordinate utilities** — promote `platform/typescript/canvas/src/hybrid/coordinates.ts` as the canonical module. Delete `canvas/utils/transform.ts`. Add `screenToWorld`/`worldToScreen` named exports. Make product layer use them instead of raw `reactFlowInstance.screenToFlowPosition`. | 2d |
| 10 | **`tabIndex` deduplication** — remove `tabIndex` from `ArtifactNode`'s `<Card>`. Rely on ReactFlow's `nodesFocusable` for node focus management. | 0.5d |
| 11 | **`Handle` colors via CSS custom properties** — replace `#2196F3` hardcodes with `var(--color-primary)`. | 0.5d |
| 12 | **Fix `onNodesChange`** — remove the drag-position filter that suppresses `{type: 'position', dragging: true}` changes. Guidance overlays need these to update in real time. | 0.5d |
| 13 | **Float snap rounding** — round all stored positions to 0.5px grid in `MoveNodesCommand` and `useNodePositions.persistPosition`. | 0.5d |

### Phase 2 — Golden Architecture (1–2 months)

| # | Fix | Effort |
|---|---|---|
| 14 | **Single camera store** per "Golden Architecture" below — one atom, zero alternatives, ReactFlow internal store used only for overlays that need frame-accurate transforms via `useStore`. | 1w |
| 15 | **Principled render layer stack** — explicit z-index contract between Content / Edges / Overlays / UI portals (see Section 14). | 1w |
| 16 | **`aria-flowto`** on artifact nodes — computed from edges for screen reader graph traversal. | 1d |
| 17 | **`FrameNode` / `GroupNode`** — enclosed selection, resize-extends-to-fit-children. | 1w |
| 18 | **Schema versioning + migration runner** for `useNodePositions` persisted data. | 2d |
| 19 | **RBush grid-cell optimization** — switch from flat RBush to a grid-partitioned index for > 1000 nodes. | 1w |
| 20 | **Monaco isolation node** — with `CanvasContentWrapper`, focus handoff disabling canvas undo while editor is focused, and `Cmd+Z` intercept. | 1w |

---

## 14. Key Code Patterns — Golden Architecture

### Camera Store (single source of truth)

```ts
// workspace/canvasAtoms.ts — THE only camera atom
export interface CameraState { x: number; y: number; zoom: number; }
export const cameraAtom = atom<CameraState>({ x: 0, y: 0, zoom: 1 });

// Derived convenience atoms
export const cameraZoomAtom = atom(get => get(cameraAtom).zoom);
export const cameraOriginAtom = atom(get => {
    const { x, y } = get(cameraAtom);
    return { x, y };
});
```

```tsx
// CanvasWorkspace.tsx — single writer
<ReactFlow
    onMove={(_, viewport) => setCamera(viewport)}  // setCamera = useSetAtom(cameraAtom)
    // ...
/>
```

```ts
// For pixel-accurate overlay rendering (within ReactFlow context):
// Use ReactFlow's internal store — NOT cameraAtom — for zero-lag access
import { useStore } from '@xyflow/react';
const [tx, ty, zoom] = useStore(s => s.transform);
// Apply: element.style.transform = `translate(${x * zoom + tx}px, ${y * zoom + ty}px)`;
```

### World↔Screen Conversion Utilities (canonical module)

```ts
// platform/typescript/canvas/src/hybrid/coordinates.ts — PROMOTED as canonical

export interface ViewportStore {
    x: number;  // pan x
    y: number;  // pan y
    zoom: number;
}

/** Convert browser screen coordinates to canvas world coordinates */
export function screenToWorld(screenPt: Point, vp: ViewportStore): Point {
    return {
        x: (screenPt.x - vp.x) / vp.zoom,
        y: (screenPt.y - vp.y) / vp.zoom,
    };
}

/** Convert canvas world coordinates to browser screen coordinates */
export function worldToScreen(worldPt: Point, vp: ViewportStore): Point {
    return {
        x: worldPt.x * vp.zoom + vp.x,
        y: worldPt.y * vp.zoom + vp.y,
    };
}

/** Compute the visible world-space bounding rect for a given container size */
export function visibleWorldRect(
    containerW: number,
    containerH: number,
    vp: ViewportStore,
): Rect {
    return {
        x: -vp.x / vp.zoom,
        y: -vp.y / vp.zoom,
        width: containerW / vp.zoom,
        height: containerH / vp.zoom,
    };
}

// DELETED: canvasToGraph, graphToCanvas — they were identity functions
```

### Render Layer Stack (z-index contract)

```tsx
// CanvasWorkspace.tsx — explicit layer ordering
<Box className="relative w-full h-full">

    {/* LAYER 0: Canvas content (ReactFlow manages its own z-index internally) */}
    <ReactFlowProvider>
        <ReactFlow ...>
            {/* LAYER 0a: Infinite canvas content — nodes, edges */}
            {/* LAYER 0b: ReactFlow overlays: MiniMap, Controls, Panels */}
            <Background />
            <Controls />
            <MiniMap style={{ zIndex: 5 }} />

            {/*
                LAYER 1: Spatial zone markers (world-space, inside ReactFlow viewport)
                z-index: 1, pointer-events: none
            */}
            <SpatialZones />

            {/*
                LAYER 2: Ghost / suggestion nodes (world-space)
                z-index: 2, pointer-events: auto (accept/reject buttons)
            */}
            <GhostNodes />
        </ReactFlow>

        {/*
            LAYER 3: Alignment guides (screen-space overlay, zero transforms)
            Rendered OUTSIDE ReactFlow tree to avoid transform composition.
            position: absolute, inset: 0, pointer-events: none, z-index: 20
        */}
        <AlignmentGuides />
    </ReactFlowProvider>

    {/*
        LAYER 4: Sketch/draw surface (screen-space canvas element)
        When active: pointer-events: auto, z-index: 30
        When inactive: display: none (NOT pointer-events: none — avoids compositing layer)
    */}
    {interactionMode === 'sketch' && (
        <div className="absolute inset-0 z-[30] pointer-events-auto">
            <EnhancedSketchLayer ... />
        </div>
    )}

    {/*
        LAYER 5: UI portals (inspector, palette, command palette)
        position: fixed, z-index: 50
        These must be portalled out of the canvas subtree entirely.
    */}
    {/* These already render via portals or fixed positioning — no change needed */}
</Box>
```

### Deterministic Hit-Testing (RBush spatial index — incremental)

```ts
// workspace/spatialIndexService.ts — add to exports
export const spatialIndexAPI = {
    // ... existing buildIndex, findCollisions ...

    /** Incrementally update the index when nodes change */
    syncNodes: async (
        added: Node[],
        removed: string[],
        moved: Node[],
    ): Promise<void> => {
        // These all fire in parallel to the worker
        const ops: Promise<void>[] = [
            ...added.map(n => spatialIndexAPI.insertNode(n)),
            ...removed.map(id => spatialIndexAPI.removeNode(id)),
            ...moved.flatMap(n => [
                spatialIndexAPI.removeNode(n.id),
                spatialIndexAPI.insertNode(n),
            ]),
        ];
        await Promise.all(ops);
    },
};

// CanvasWorkspace.tsx — incremental sync effect
const prevNodesRef = useRef<Map<string, Node>>(new Map());

useEffect(() => {
    if (nodes.length === 0 && prevNodesRef.current.size === 0) return;

    const prev = prevNodesRef.current;
    const next = new Map(nodes.filter(n => n.measured?.width).map(n => [n.id, n]));

    const added: Node[] = [];
    const removed: string[] = [];
    const moved: Node[] = [];

    for (const [id, n] of next) {
        const p = prev.get(id);
        if (!p) { added.push(n); continue; }
        if (p.position.x !== n.position.x || p.position.y !== n.position.y ||
            p.measured?.width !== n.measured?.width) {
            moved.push(n);
        }
    }
    for (const id of prev.keys()) {
        if (!next.has(id)) removed.push(id);
    }

    if (added.length || removed.length || moved.length) {
        spatialIndexAPI.syncNodes(added, removed, moved);
    }

    prevNodesRef.current = next;
}, [nodes]);
```

### Viewport Culling Strategy

ReactFlow's `onlyRenderVisibleElements` handles culling for standard nodes. For large canvases (> 1000 nodes), supplement with:

```ts
// Derived atom: IDs of nodes in the current viewport
export const visibleNodeIdsAtom = atom((get) => {
    const camera = get(cameraAtom);
    const nodes = get(nodesAtom);
    const W = window.innerWidth;
    const H = window.innerHeight;

    const vr = visibleWorldRect(W, H, camera);
    const margin = 200; // render margin in world px

    return new Set(
        nodes
            .filter(n => {
                const w = n.measured?.width ?? 200;
                const h = n.measured?.height ?? 100;
                return (
                    n.position.x + w > vr.x - margin &&
                    n.position.x < vr.x + vr.width + margin &&
                    n.position.y + h > vr.y - margin &&
                    n.position.y < vr.y + vr.height + margin
                );
            })
            .map(n => n.id)
    );
});
```

This atom can gate expensive per-node computations (code association loading, computed views) to only visible nodes.

### `onSelectionChange` — the correct wiring

```tsx
// CanvasWorkspace.tsx
const setSelectedNodes = useSetAtom(selectedNodesAtom);
// optionally: const setSelectedEdges = useSetAtom(selectedEdgesAtom);

<ReactFlow
    onSelectionChange={({ nodes: sel }) => {
        setSelectedNodes(sel.map(n => n.id));
    }}
    // ...
/>
```

This must be implemented before any selection-dependent feature (delete, copy, inspect) can work via mouse.

---

## 15. Maturity Score & Top 10 Fixes

### Score: **4.2 / 10**

The foundation is sound — ReactFlow is the right engine, Jotai is the right state primitive, the command pattern is correctly designed, and the spatial index architecture (RBush in a Web Worker) is industry-appropriate. However, the critical failure is in **integration**: the systems are defined but not connected. Selection is never synced. The spatial index is rebuilt destructively on every frame. Three camera atoms coexist. The coordinate utilities exist in one module but are used from a different, inconsistent module. The dual canvas route systems mean no architectural decision has been fully committed to.

### Top 10 Most Important Fixes (ranked)

| Rank | Issue | Severity |
|---|---|---|
| 1 | **Wire `onSelectionChange` → `selectedNodesAtom`** — without this, delete/copy/paste are broken for all mouse-based selection. | 🔴 Critical |
| 2 | **Replace full `buildIndex` with incremental `syncNodes`** — full rebuild at 60fps during drag degrades to unusable at > 100 nodes. | 🔴 Critical |
| 3 | **Delete `CanvasScene.tsx` and the `canvasAtom` legacy state system** — two parallel state stores are the root cause of the viewport atom proliferation. | 🔴 Critical |
| 4 | **Gate spatial items on `n.measured`** — all alignment snapping is wrong on first drag until DOM measurement completes. | 🔴 Critical |
| 5 | **Consolidate to one viewport atom** — delete `state/atoms/viewportAtom.ts`. Alias nothing; delete everything that isn't `cameraAtom`. | 🟠 High |
| 6 | **Remove manual snap in `onNodeDrag`** — use ReactFlow's native `snapToGrid`/`snapGrid`. Eliminates a React-cycle of snap feedback latency. | 🟠 High |
| 7 | **Remove `tabIndex` from inner `<Card>` in `ArtifactNode`** — creates double Tab stops; conflicts with `nodesFocusable`. | 🟡 Medium |
| 8 | **Consolidate coordinate utilities** — delete `canvasToGraph`/`graphToCanvas` identity functions; rename to `screenToWorld`/`worldToScreen`; import from one canonical module. | 🟡 Medium |
| 9 | **Apply `position` snap-rounding in `MoveNodesCommand`** — prevents IEEE-754 float drift at high zoom. | 🟡 Medium |
| 10 | **`NodeResizer` visibility gate on `interactionMode`** — resize handles should be invisible in sketch and diagram modes. | 🟡 Medium |

---

*Audit produced against codebase state as of 2026-02-28. Re-audit recommended after Phase 1 completion.*
