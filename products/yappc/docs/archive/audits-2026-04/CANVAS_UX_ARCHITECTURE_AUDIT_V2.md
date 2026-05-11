# Canvas UX Architecture Audit — v2 (Post-Refactor)
**Date:** 2026-02-28 | **Auditor:** Principal UI/UX Architecture Review  
**Baseline:** Audit v1 (2026-01-19, 3.2/10) → Current Refactor State (5.8/10)  
**Scope:** `products/yappc/frontend/apps/web/src/components/canvas/`

---

## 1. Executive Summary

The Phase 0 / Phase 1 refactor (Jan–Feb 2026) delivered real, measurable improvements: the god component shrank from 1,414 → 757 LOC, three competing keyboard systems collapsed into one, MUI was eliminated, a real FPS monitor replaced hardcoded values, snap-to-grid was wired, and an ARIA live region was added. That is meaningful progress.

However, the following **architectural ceilings remain** and cap the product at mid-grade quality:

- **No Camera Abstraction.** Viewport state lives in two places simultaneously — `viewportAtom` (Jotai) and `const [viewport, setViewport] = useState(...)` (CanvasWorkspace local state). ReactFlow's internal camera is the actual single source of truth; both app-side variables lag one event behind. Cursor-centered zoom works only because ReactFlow does it internally — the app cannot control or extend it.
- **Snapshot-Based Undo, Not Command Pattern.** `pushHistoryAtom` take full node+edge snapshots. Every node move during drag fires this (via `onNodeDragStop` → `pushAction`). At 200 nodes an undo entry is ~200 serialised objects. The redo-kill-on-mutate (standard) is implemented, but batching and partial undo (e.g., "undo just the type change") are impossible.
- **Node Click → Inspector is Broken.** `handleNodeClick` is a no-op body (`if (!artifacts) return; // Inspector opens via node click`). The Inspector Panel **never opens from a node click**. `selectedArtifactAtom` is never set by any event in the current codebase.
- **Alignment Guides Show but Do Not Snap.** `useCanvasDragDrop.onNodeDrag` computes `vSnap`/`hSnap` within `SNAP_THRESHOLD=15px` and sets `alignmentGuidesAtom`. It does **not** mutate the dragging node's position to match the snap line. The user sees a guide but the node does not jump to it.
- **`onlyRenderVisibleElements` Not Set.** ReactFlow's built-in viewport culling is opt-in. With the default setting, every node in `styledNodes` renders DOM regardless of whether it is on screen. At 500+ nodes this causes measurable frame-rate degradation.
- **N+1 Query Pattern in ArtifactNode.** `useCodeAssociations(data.id)` is called inside every `ArtifactNode` render. 100 nodes = 100 concurrent fetch requests. No batching, no prefetch, no per-workspace aggregate query.
- **Dual Data Model Not Resolved.** `generatedNodes` from server (`useArtifacts`) overwrites `nodesAtom` via `useEffect` on every refetch. User-dragged positions are destroyed on the next server poll. `suppressGeneratedSyncAtom` is a valid band-aid but not a real solution — position persistence requires a layout-layer separate from the artifact model.
- **Critical No-Op Stubs.** Five prominent actions route to `() => {}` bodies: `onTogglePanel`, `onValidate`, `onGenerate`, `onExport`, `onSave` in `CommandPalette`; `onShowShortcuts` in the keyboard shortcut shortcut itself; and `onSkip` in `NextBestTaskCard`.
- **Accessibility Shallow.** Tab traversal sets `selectedNodesAtom` but never moves DOM focus. Screen readers cannot read node titles because `ArtifactNode` has no `role`, `aria-label`, or `aria-describedby`. The skip link target `#canvas-surface` points to the outer `Box`, not a focusable element.
- **Content Canvases Completely Broken.** 28 files in `content/` produce 2,055 TypeScript errors (unterminated strings, unclosed JSX, MUI imports). These are the actual rich-content node surfaces (Monaco, Mermaid, sequence diagrams). The canvas is effectively empty on any non-artifact view.

**Revised Maturity Score: 5.8 / 10**  
**Hard Requirements Met: 4.5 / 10**

---

## 2. User Experience Audit

### What to Remove

| Element | Location | Problem |
|---|---|---|
| `const [viewport, setViewport] = useState(...)` | `CanvasWorkspace.tsx:L135` | Duplicates `viewportAtom`; is never read back — pure dead state |
| `handleNodeClick` body | `CanvasWorkspace.tsx:L408` | Empty comment, no implementation — remove or implement |
| `onSkip={() => {}}` | `CanvasWorkspace.tsx:L601` | Drop stub or wire to dismissal atom |
| `History` WorkspacePanel content | `CanvasWorkspace.tsx:L498` | Shows "No history snapshots" placeholder — remove the panel until HistoryPanel is real |
| `PresenceIndicator users={[]}` | `CanvasWorkspace.tsx:L454` | Hardcoded empty array; either wire real presence or remove |
| 28 content canvas files | `canvas/content/*.tsx` | 2,055 compile errors make them unreachable; remove them from the registry until they compile |

### What to Unify

| Inconsistency | Evidence |
|---|---|
| **Viewport state split** | `viewportAtom` (atoms.ts) + `useState({x,y,zoom})` (CanvasWorkspace) — same data, two owners |
| **Search shortcut conflict** | `⌘F` opens CommandPalette (shortcuts.ts) but `isSearchOpenAtom` implies a separate search UI |
| **Inspector open path** | Inspector opened via atom (`isInspectorOpenAtom`) but never triggered by node click |
| **Phase navigation duplication** | `useCanvasZoom.{handlePrevPhase,handleNextPhase}` + `CommandPalette.onPhaseTransition` + keyboard shortcut `⌘←/⌘→` — three paths to the same action, no shared source |

### Missing Core Surfaces

- **Contextual node toolbar** above the selected node (copy, delete, duplicate, pin, link) — the only toolbar is a static bottom `Panel` with two icon buttons
- **Right-click context menu** on canvas background — `handleCanvasDoubleClick` opens QuickCreate but right-click is unmapped
- **Paste feedback** — `handlePasteNodes` runs silently; no ARIA announcement, no visual flash
- **Drag ghost preview** — when dragging from the left panel DnD source, there is no custom `dragImage`; the browser default semi-opaque clone is shown

---

## 3. Interaction Model Audit

### Selection

`selectedNodesAtom` is set by ReactFlow via `onSelectionChange` (not wired in current `CanvasWorkspace`). The atom is only written by:
1. `useCanvasHandlers.handleSelectAll` → sets all node IDs
2. `useCanvasAccessibility.focusNextNode/focusPrevNode` → sets single ID
3. `useCanvasShortcuts` → calls `onSelectAll`

**ReactFlow's own selection state** (via lasso drag, Shift+click) is never synced back to `selectedNodesAtom`. This means the shortcut system's `onCopy` reads `selectedNodesAtom` and finds nothing selected even after the user lasso-dragged a group.

**Fix:** Add `onSelectionChange={({ nodes }) => setSelectedNodes(nodes.map(n => n.id))}` to `<ReactFlow>`.

### Focus Management

`focusNextNode()` does:
```typescript
setSelectedNodes([node.id]);
announce(`Focused: ${node.data.title}`);
```
It never calls `.focus()` on a DOM element. When a screen reader user presses Tab, the announcement fires but keyboard focus remains on the canvas `div`. The focused node's ReactFlow wrapper has no `tabIndex` attribute, so it cannot receive hardware keyboard focus — only programmatic selection state changes.

**Fix:** The ReactFlow node wrapper must receive `tabIndex={isSelected ? 0 : -1}` and be `.focus()`-called after selection. Use `useCallback` in the node component or a portal listener.

### Gestures

| Gesture | Behavior | Problem |
|---|---|---|
| Two-finger pinch | ✅ `zoomOnPinch` — ReactFlow native | None |
| Two-finger pan | ✅ `panOnDrag` when navigate mode | Only when mode=navigate |
| Escape during drag | ❌ No cancel | `useCanvasDragDrop` has no drag-cancel handler |
| Long-press (touch) | ❌ No handler | On mobile/tablet, long-press should open context menu |
| Middle-mouse drag | ✅ ReactFlow native pan | None |

### Keyboard Shortcut Gaps

| Missing Shortcut | Standard | Priority |
|---|---|---|
| `⌘D` | Duplicate selected | High |
| `⌘G` / `⌘⇧G` | Group / Ungroup | High |
| `⌘[` / `⌘]` | Send backward / Bring forward (z-index) | Medium |
| `⌘⇧A` | Deselect all | Medium |
| `F2` | Rename selected node | Medium |
| `⌘E` | Export selection | Low |
| `?` | Show keyboard shortcut legend | Low — `onShowShortcuts` is `() => {}` |

**`?` → KeyboardShortcutLegend.tsx exists but is never opened.** Wire `onShowShortcuts` to a panel toggle atom.

---

## 4. Canvas Engine Audit

### Camera Abstraction — NOT MET

**Current state:**
```
ReactFlow internal camera (source of truth)
    ↓ onMove callback
useState({ x, y, zoom }) in CanvasWorkspace   ← stale by 1 frame
    ↓ never written back to:
viewportAtom in canvasAtoms.ts               ← always stale
```

`viewportAtom` has an `updateViewportAtom` action atom but `CanvasWorkspace` never calls it — the `onMove` handler only calls `setViewport` (local state). The Jotai atom is permanently at `{x:0, y:0, zoom:1}`.

**Required Camera struct:**
```typescript
interface CameraState {
  x: number;       // pan offset
  y: number;
  zoom: number;    // scale factor [0.1, 2.0]
}

// Single atom, single writer
const cameraAtom = atom<CameraState>({ x: 0, y: 0, zoom: 1 });
```

`onMove={(_, vp) => setCamera(vp)}` — one writer, one atom. All consumers subscribe to `cameraAtom`. The local `useState` is deleted.

### Scale-Aware Overlays

**AlignmentGuides.tsx** — not fully audited but the alignment guide positions (`alignmentGuidesAtom.vertical`, `.horizontal`) are in **flow coordinates** (canvas space). If `AlignmentGuides` renders `<div style={{ left: vSnap }}` using raw pixel values without applying the viewport transform `(x: vSnap * zoom + viewport.x)`, guides will appear at the wrong screen position at any zoom != 1. The viewport atom being stale compounds this.

**ReactFlow Handles** — React Flow renders handle overlays inside the node wrapper which is already transformed, so handles scale correctly. This is not a problem.

**Quick-Create Menu** — `quickCreateMenuPositionAtom` stores `{ x: event.clientX, y: event.clientY }` (screen coordinates). The menu is rendered as a fixed/absolute overlay using those screen coords — correct, no zoom issue.

### Hit-Testing

ReactFlow uses DOM pointer detection (browser event.target traversal), not the spatial index. The spatial index (`RBush` via worker) is used only for alignment guide computation during drag. This is architecturally correct — ReactFlow's approach is reliable and GPU-accelerated. No change needed here.

The `findCollisions(node, 100)` call in `onNodeDrag` fires asynchronously on every `mousemove`. At high drag speed (`mousemove` fires at 60-240Hz), multiple in-flight worker messages can accumulate. The worker handles them serially (no cancellation). A `AbortController` or `messageId` — last-one-wins pattern is needed.

---

## 5. Drag & Drop Audit

### Critical: Alignment Guides Don't Snap

In `useCanvasDragDrop.onNodeDrag`:
```typescript
const vSnap = collisions.find(c => Math.abs(c.minX - node.position.x) < SNAP_THRESHOLD)?.minX;
const hSnap = collisions.find(c => Math.abs(c.minY - node.position.y) < SNAP_THRESHOLD)?.minY;
setAlignmentGuidesAtom({ vertical: vSnap, horizontal: hSnap });
// ← Node position is NOT corrected
```

The guide lines appear but the node continues dragging freely. Figma, FigJam, Miro all snap the node to the guide on threshold crossing. Fix:

```typescript
if (vSnap !== undefined || hSnap !== undefined) {
    reactFlowInstance.setNodes(nds =>
        nds.map(n => n.id === node.id
            ? { ...n, position: { x: vSnap ?? n.position.x, y: hSnap ?? n.position.y } }
            : n
        )
    );
}
```

### Drag Cancelability

No Escape-key handler during active drag. `useCanvasDragDrop` has no drag-start position stored. The `onNodeDrag` callback overwrites position without saving the original. If a user presses Escape mid-drag, nothing happens. Fix: Store `dragStartPosition` in `onNodeDragStart`, restore on `keydown[Escape]`, then `onNodeDragStop`.

### Drop Feedback

`isDragOver` shows `ring-2 ring-indigo-400` on the canvas surface. This is good. Missing: the drop indicator should show **where** the node will land (a ghost outline at the snapped position), not just highlight the whole canvas.

### Multi-Select Drag

ReactFlow handles multi-select drag natively when nodes are selected via lasso. But because `selectedNodesAtom` is not wired to `onSelectionChange`, the shortcut system doesn't know what's selected. Multi-select drag works visually (ReactFlow internal state) but `handleDeleteSelected`, `handleCopyNodes`, `handleMoveSelectedNodes` operate on `selectedNodesAtom` (stale) and affect zero nodes.

---

## 6. Zoom & Pan Audit

### Cursor-Centered Zoom — DELEGATED, NOT IMPLEMENTED

ReactFlow implements cursor-centered zoom internally when `zoomOnScroll` is true. The app doesn't add to or override this — it works correctly for scroll-to-zoom. **However:** the `handleZoomToPhase`, `handleFitView`, and `handleLevelChange` functions call `reactFlowInstance.setCenter(x, y, { zoom, duration })` which animates to a fixed coordinate, not to the cursor. This is correct behavior for "zoom to phase" but is not cursor-centered. No issue here, but it should be documented clearly.

### Bounds

`minZoom={0.1}` `maxZoom={2}` — reasonable. For large graphs (5k nodes), `minZoom=0.05` is needed to see the full canvas at once (PHASE_ZONE_CENTERS span ~4900px wide, at `0.1` zoom that's 490px on screen — barely fits a 1440px monitor).

### Phase Zone Center Constants

```typescript
INTENT:{400,300}, SHAPE:{1250,300}, ..., IMPROVE:{5300,300}
```

At `minZoom=0.1`, the full 5300px width = 530px — fits a screen but nodes at `maxX=5300+200=5500` will be partially off-screen. With `maxZoom=2`, `handleZoomToPhase` caps `targetZoom` at `1.2` — inconsistent with the `maxZoom=2` prop. Raise to 1.5 or match maxZoom.

### Smoothness

`prefersReducedMotionAtom` is read in `useCanvasZoom` and the duration is set to `0` when true. ✅ Correct implementation. One issue: the atom is initialized synchronously with `window.matchMedia(...)` — in SSR environments (Next.js) this throws. Use `typeof window !== 'undefined' ? window.matchMedia(...) : { matches: false }`.

---

## 7. Arbitrary Content Nodes Audit

### Content Canvases — Completely Non-Functional

`canvas/content/` contains 28 canvas implementations (CodeEditorCanvas, SequenceDiagramCanvas, MindMapCanvas, etc.). The TypeScript compiler reports **2,055 errors** across these files — unterminated strings, unclosed JSX tags, and MUI imports. They **cannot render**. The node registry in `CanvasRegistryProvider` only registers `artifact`, `simpleUnified`, `diagram` — the content canvases are not registered as node types at all.

**Root cause:** These files were written using MUI + `@/components/ui` pattern and were never migrated to `@ghatana/ui`. They may be legacy code that predates the architecture.

**Action required:** Audit each file. Migrate or delete. Do not register broken components in the node registry.

### Monaco / CodeMirror Isolation

`CodeEditorCanvas.tsx` exists but has compile errors. From `DiagramNode.tsx` we can see the correct pattern:

```tsx
<div className="nodrag nopan nowheel overflow-auto">
    <MermaidDiagram content={content} zoom={zoom} />
</div>
```

`nodrag` → prevents ReactFlow from treating mousedown as a drag initiation  
`nopan` → prevents canvas pan on pointer events within the area  
`nowheel` → prevents scroll events from zooming the canvas  

This is the correct pattern for isolating rich editors. It must be applied consistently in all content node wrappers. A shared `<CanvasContentWrapper>` component should enforce this:

```tsx
const CanvasContentWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
    <div className="nodrag nopan nowheel overflow-auto w-full h-full" onPointerDown={e => e.stopPropagation()}>
        {children}
    </div>
);
```

### Resizing

ReactFlow v12 supports `NodeResizer` from `@xyflow/react`. Neither `ArtifactNode` nor `DiagramNode` implements it. Nodes have fixed dimensions with only CSS `minWidth`/`minHeight`. Users cannot resize any node. This is a major UX gap for a canvas-first product.

### Portals and Z-Index

`QuickCreateMenu` and `InspectorPanel` are rendered as children of the main component tree (inside the outermost `Box`). They are not portaled. This means they can be occluded by ReactFlow node z-indices (ReactFlow sets high z-indices on selected nodes). Both should use React Portals:

```tsx
createPortal(<QuickCreateMenu ... />, document.body)
```

---

## 8. Diagramming Audit

### Edge Types

Only `dependency` edge type is registered (`DependencyEdge`). No edges for: composition, inheritance, data flow, async message. For a software-architecture canvas, the minimum viable edge vocabulary is: `uses`, `extends`, `implements`, `calls`, `emits`, `depends-on`.

### DiagramNode

`DiagramNode` embeds Mermaid. The content is stored in `data.diagramContent` (fixed in this refactor). Remaining issues:

1. **No edit affordance for node content.** The diagram silently uses `DEFAULT_CONTENT` if `data.diagramContent` is falsy. There is no inline editor — users see a diagram they cannot edit without opening a separate inspector.
2. **`data.diagramZoom` must propagate.** When the user zooms the canvas, `data.diagramZoom` stays fixed. The Mermaid SVG inside scales at 1× when the canvas is at 0.3× — text becomes illegible. The diagram zoom should auto-scale relative to canvas zoom.
3. **Mermaid parse errors are silent.** If `diagramContent` is invalid Mermaid, the component must show a parse error message, not blank space.

### Grouping

No group node type exists in the registry. ReactFlow v12 supports parent/child node relationships via `parentId`. Without groups, users have no way to organize clusters of related nodes.

### Alignment Constraints

Beyond the snap-guide (which doesn't snap), there are no alignment tools: no "align left edges", no "distribute evenly horizontally", no "same width/height". These appear in every professional canvas tool.

---

## 9. Design System & Visual Polish

### Current State — Mostly Clean

After the refactor, zero `@/components/ui` imports and zero `@mui/material` imports remain in the canvas hooks, workspace, nodes, and panels directories. ✅

### Remaining Issues

**1. Tailwind Classes for Behavior Inlined Everywhere**  
`'ring-2 ring-indigo-400 ring-inset'` for drag highlight (CanvasWorkspace), `'border-red-200 bg-red-50 dark:border-red-800 dark:bg-red-950'` in CanvasErrorBoundary — these hardcode color values rather than using design tokens. The internal `@ghatana/ui` should expose semantic color tokens like `ring-interactive-focus` or `bg-error-subtle`.

**2. Inline `style` Prop on ReactFlow Elements**  
Controls `style={{ margin: 16, display: 'flex', gap: 4, border: 'none', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', borderRadius: 12 }}` bypasses the design token system entirely. Use `className` with token-mapped utilities or an `<ui.Controls>` wrapper.

**3. MiniMap Colors as CSS Custom Properties**  
`nodeColor` uses `var(--color-error, #ef5350)` fallback values — good. But `maskColor="rgba(240, 242, 245, 0.6)"` is a hardcoded RGBA. Map to a design token.

**4. Motion**  
`prefers-reduced-motion` is respected for zoom animations. ✅  
But `ReactFlow`'s built-in transition CSS (`transition: all 200ms ease` on node positions) is not affected by `prefersReducedMotionAtom`. Add a global CSS rule:

```css
@media (prefers-reduced-motion: reduce) {
    .react-flow__node { transition: none !important; }
    .react-flow__edge { transition: none !important; }
}
```

**5. Typography Scale**  
`ArtifactNode` uses direct `className="text-sm"`, `text-xs"` — should use `@ghatana/ui` `<Typography variant="label">` consistently.

**6. Dark Mode — Partial**  
`className="dark:bg-gray-950"` on ReactFlow, `dark:bg-gray-900` on Controls, `dark:bg-gray-900/90` on MiniMap — all present. ✅  
But `Background` uses `color="var(--canvas-grid-color, #aaa)"` with `className="dark:[--canvas-grid-color:#444]"` — valid but fragile (relies on Tailwind JIT arbitrary property support which varies).

---

## 10. Accessibility & Keyboard-First UX

### WCAG 2.2 Compliance Matrix

| Criterion | Status | Finding |
|---|---|---|
| 1.3.1 Info and Relationships | ❌ Fail | Node content has no semantic structure — all `div`s |
| 1.3.4 Orientation | ✅ Pass | No orientation lock |
| 2.1.1 Keyboard | ⚠️ Partial | Tab traversal exists but no DOM focus movement |
| 2.1.2 No Keyboard Trap | ✅ Pass | Escape closes modals |
| 2.4.1 Bypass Blocks | ✅ Pass | Skip link present |
| 2.4.3 Focus Order | ❌ Fail | No logical focus order for canvas nodes |
| 2.4.6 Headings and Labels | ❌ Fail | `ArtifactNode` has no `aria-label` |
| 2.5.3 Label in Name | ❌ Fail | Icon buttons lack visible/accessible name |
| 3.2.2 On Input | ✅ Pass | Mode changes explicitly triggered |
| 4.1.2 Name, Role, Value | ❌ Fail | Custom nodes have no `role`, `aria-label` |
| 4.1.3 Status Messages | ✅ Pass | `aria-live` region present |

### Specific Fixes Required

**1. ArtifactNode must be a landmark:**
```tsx
<div
    role="article"
    aria-label={`${data.type} artifact: ${data.title}, status: ${data.status}`}
    aria-describedby={`node-${data.id}-desc`}
>
```

**2. DOM focus must move on Tab traversal:**  
ReactFlow node wrappers need `tabIndex`. Use `useUpdateNodeInternals` to add `tabIndex` dynamically, or use `<NodeWrapper tabIndex={isSelected ? 0 : -1}>`.

**3. Skip link must target a focusable element:**  
```tsx
<a href="#canvas-surface">Skip to canvas</a>
// #canvas-surface must have tabIndex={-1} and be a focusable element
<div id="canvas-surface" tabIndex={-1} ... />
```
Currently the `id="canvas-surface"` `Box` has no `tabIndex` — a screen reader skip link target must be programmatically focusable.

**4. Icon Buttons need visible labels:**  
`<IconButton aria-label="Add new artifact to canvas">` is present ✅ but the two bottom toolbar buttons do not have visible tooltips that appear on focus (only on hover via `Tooltip`). WCAG 2.5.3 requires visible labels for input controls — add a visually-hidden span or show label on focus.

**5. Color not sole distinguisher:**  
MiniMap `nodeColor` uses color only (red/green/blue/gray). Nodes should also use pattern fills or shape differences in the minimap for colorblind users. `ArtifactNode` status uses CSS variable colors — pair with icons (already present) and ensure those icons have `aria-hidden="true"`.

---

## 11. Performance & Scalability Audit

### Viewport Culling — NOT ENABLED

```tsx
<ReactFlow
    nodes={styledNodes}
    // ← Missing: onlyRenderVisibleElements
```

ReactFlow v12 supports `onlyRenderVisibleElements` prop. Without it, every node in `styledNodes` is mounted in the DOM regardless of viewport. At 1,000 nodes this creates ~1,000 DOM subtrees (each ArtifactNode has ~12 DOM elements = 12,000 elements). Frame time degrades linearly.

**Fix:** `<ReactFlow onlyRenderVisibleElements>` — one line, requires testing for selection-box interactions (ReactFlow temporarily un-culls selected nodes on mount).

### Spatial Index — Full Rebuild on Every Change

```typescript
// CanvasWorkspace.tsx
useEffect(() => { spatialIndexAPI.buildIndex(nodes); }, [nodes]);
```

`nodes` changes on every `applyNodeChanges` call — including position-only dragging changes filtered by `onNodesChange`. `buildIndex` sends all nodes to the worker, which rebuilds the entire RBush tree. The worker supports `INSERT`/`REMOVE` incremental ops but they are never called. Fix:

```typescript
// Replace buildIndex useEffect with:
const prevNodesRef = useRef<Node[]>([]);
useEffect(() => {
    const prev = prevNodesRef.current;
    const added = nodes.filter(n => !prev.find(p => p.id === n.id));
    const removed = prev.filter(p => !nodes.find(n => n.id === p.id));
    added.forEach(n => spatialIndexAPI.insertNode(n));
    removed.forEach(n => spatialIndexAPI.removeNode(n.id));
    prevNodesRef.current = nodes;
}, [nodes]);
```

### N+1 Query in ArtifactNode

```typescript
// ArtifactNode.tsx
const { codeLinks } = useCodeAssociations(data.id); // ← per node
```

100 nodes → 100 `/api/code-associations?artifactId=X` requests in parallel. Fix: batch at workspace level.

```typescript
// In useCanvasHandlers or a new useCodeAssociationsBatch:
const { data: allAssociations } = useQuery(['code-associations', projectId], 
    () => api.getCodeAssociationsForProject(projectId)
);
// Store in atom: codeAssociationsAtom: Map<artifactId, CodeLink[]>
// ArtifactNode reads from atom: useAtomValue(codeAssociationsAtom).get(data.id) ?? []
```

### styledNodes Recalculation

```typescript
const styledNodes = useMemo(() =>
    computedView.visibleNodes.map(node => ({
        ...node,
        style: { opacity: ..., boxShadow: ... },
        data: { ...node.data, isLocked: ..., isBlocked: ... },
    })),
[computedView]
);
```

This creates new object references for **every** node on every `computedView` change. `computedView` is computed by `useComputedView(...)` which re-evaluates when `viewMode`, `userRole`, or `userId` change — relatively infrequent. This is acceptable. However, `styledNodes` is passed as `nodes` to `<ReactFlow>` which calls `isEqual` comparison per node — if references differ but values are equal, ReactFlow bails on re-render. This is already the case (spreading creates new objects). Use `Object.assign` with selective mutation or memoize per-node.

### ArtifactNode — Missing React.memo

`ArtifactNode` is a ReactFlow custom node. ReactFlow calls the component on every `nodes` array reference change. Since `generatedNodes` creates new objects each time `useArtifacts` refetches, all artifact nodes re-render together. `ArtifactNode` should be wrapped in `React.memo` with a custom comparator:

```typescript
export const ArtifactNode = React.memo(ArtifactNodeInner, (prev, next) =>
    prev.data.id === next.data.id &&
    prev.data.status === next.data.status &&
    prev.data.title === next.data.title &&
    prev.selected === next.selected
);
```

---

## 12. Extensibility Audit

### Node Registry — Functional

`CanvasRegistryProvider` accepts `additionalNodeTypes` and `additionalEdgeTypes` props. Type-safe since the `as any` removal. ✅ However:

- **No hot-reload support.** Registry is computed once in `useMemo([])`. Adding a node type at runtime requires unmounting and remounting the provider.
- **No validation.** A plugin can register a node type with a key collision (overwriting `artifact` or `diagram`). The spread `{ artifact: ArtifactNode, ...additionalNodeTypes }` allows silent overrides.
- **No schema versioning for node data.** `migrateData()` in `useCanvasHandlers.handleTypeChange` exists but only handles type-to-type conversion, not version-to-version.

### Plugin System

`canvas/plugins/DefaultPlugins.tsx` exists in the directory. It was fixed from `@/components/ui` → `@ghatana/ui` imports but is otherwise unaudited. The question is whether a plugin can register:

- [ ] New node types (via `additionalNodeTypes`) ✅ possible via provider props
- [ ] New edge types ✅ possible
- [ ] New toolbar items ❌ no plugin hook for toolbar
- [ ] New shortcut handlers ❌ `useCanvasShortcuts` is a closed hook
- [ ] New command palette commands ❌ `CommandPalette actions={[]}` hardcodes empty array

**`CommandPalette actions={[]}` is passed an empty array.** The command palette renders no commands. The only actions in the palette are the hardwired prop callbacks — users cannot search for anything.

### Schema Versioning

`canvasAtoms.ts` stores raw ReactFlow `Node<ArtifactNodeData>` objects. No version field. If `ArtifactNodeData` shape changes in a future release, saved canvas state in localStorage/server becomes unreadable. A `schemaVersion` field in `CanvasHistoryEntry` and a migration runner at load time are required.

---

## 13. Prioritized Refactor Plan

### Phase 0 — Critical Fixes (0–2 Days)

| # | Fix | File | Effort |
|---|---|---|---|
| P0.1 | **Wire `onSelectionChange` → `selectedNodesAtom`** | CanvasWorkspace.tsx | 15 min |
| P0.2 | **Implement `handleNodeClick` → open Inspector** | CanvasWorkspace.tsx | 1 hr |
| P0.3 | **Add `onlyRenderVisibleElements` to `<ReactFlow>`** | CanvasWorkspace.tsx | 5 min |
| P0.4 | **Fix guide snapping in onNodeDrag** | useCanvasDragDrop.ts | 2 hr |
| P0.5 | **Wire `viewportAtom` in `onMove`; delete local `useState`** | CanvasWorkspace.tsx | 30 min |
| P0.6 | **Add `tabIndex={-1}` to `#canvas-surface` div** | CanvasWorkspace.tsx | 5 min |
| P0.7 | **Remove stub `History` panel or replace** | CanvasWorkspace.tsx | 30 min |
| P0.8 | **Wire `onShowShortcuts` → `KeyboardShortcutLegend`** | CanvasWorkspace.tsx | 30 min |

### Phase 1 — Architecture Fixes (1–2 Weeks)

| # | Fix | Effort |
|---|---|---|
| P1.1 | **Incremental spatial index inserts/removes** | 1 day |
| P1.2 | **Batch `useCodeAssociations` → workspace-level atom** | 1 day |
| P1.3 | **React.memo on ArtifactNode with custom comparator** | 2 hr |
| P1.4 | **Drag cancelability (Escape key → restore position)** | 1 day |
| P1.5 | **NodeResizer on ArtifactNode and DiagramNode** | 2 days |
| P1.6 | **Position persistence layer** (separate from artifact model) | 3 days |
| P1.7 | **Wire `CommandPalette actions` from a command registry atom** | 2 days |
| P1.8 | **DOM focus movement in `focusNextNode`/`focusPrevNode`** | 1 day |
| P1.9 | **`aria-label` on ArtifactNode, `role="article"`, `tabIndex`** | 1 day |
| P1.10 | **`CanvasContentWrapper` shared component** | 2 hr |

### Phase 2 — Platform Capabilities (1–2 Months)

| # | Capability | Effort |
|---|---|---|
| P2.1 | **Command pattern undo/redo** (replace snapshot) | 2 weeks |
| P2.2 | **Group nodes** (ReactFlow parentId-based) | 1 week |
| P2.3 | **Alignment tools** (align left/right/top/bottom, distribute) | 1 week |
| P2.4 | **Content canvases** — migrate or rewrite 28 `content/*.tsx` files | 3+ weeks |
| P2.5 | **Node plugin hook** for toolbar + shortcut registration | 1 week |
| P2.6 | **Schema versioning + migration runner** | 3 days |
| P2.7 | **Right-click context menu** on canvas background and nodes | 1 week |
| P2.8 | **Custom drag ghost preview** from left panel | 2 days |
| P2.9 | **Real presence** (wire PresenceIndicator) | 1 week |
| P2.10 | **WCAG 2.2 AA full compliance** audit + remediation | 2 weeks |

---

## 14. Key Code Patterns

### Pattern A — Single Camera Atom

```typescript
// canvasAtoms.ts — replace viewportAtom + local useState
export const cameraAtom = atom<CameraState>({ x: 0, y: 0, zoom: 1 });
// Derivative
export const cameraZoomAtom = atom((get) => get(cameraAtom).zoom);

// CanvasWorkspace.tsx
const setCamera = useSetAtom(cameraAtom);
// <ReactFlow onMove={(_, vp) => setCamera(vp)} />
// Delete: const [viewport, setViewport] = useState(...)
```

### Pattern B — Correct `onSelectionChange` Wiring

```tsx
// CanvasWorkspace.tsx
const setSelectedNodes = useSetAtom(selectedNodesAtom);

<ReactFlow
    onSelectionChange={({ nodes: selected }) =>
        setSelectedNodes(selected.map(n => n.id))
    }
    // ... rest of props
/>
```

### Pattern C — Command Pattern Undo/Redo

```typescript
interface CanvasCommand {
    execute(): void;   // apply change to atoms
    undo(): void;      // revert change in atoms
    label: string;     // for history panel display
    merge?(other: CanvasCommand): CanvasCommand | null; // optional batch merging
}

// Example: MoveNodeCommand
class MoveNodeCommand implements CanvasCommand {
    constructor(
        private nodeId: string,
        private from: { x: number; y: number },
        private to: { x: number; y: number },
        private setNodes: SetAtom<Node[]>
    ) {}
    execute() { this.setNodes(move(this.nodeId, this.to)); }
    undo() { this.setNodes(move(this.nodeId, this.from)); }
    label = `Move ${this.nodeId}`;
    merge(other: CanvasCommand) {
        if (other instanceof MoveNodeCommand && other.nodeId === this.nodeId) {
            return new MoveNodeCommand(this.nodeId, this.from, other.to, this.setNodes);
        }
        return null;
    }
}

// commandHistoryAtom: { commands: CanvasCommand[]; ptr: number }
// undoAtom → execute commands[ptr].undo(); ptr--
// redoAtom → ptr++; execute commands[ptr].execute()
```

### Pattern D — Alignment Guide Snapping

```typescript
// useCanvasDragDrop.ts — inside onNodeDrag callback
const onNodeDrag = useCallback(async (_: React.MouseEvent, node: Node) => {
    try {
        const collisions = await spatialIndexAPI.findCollisions(node, 100);
        const vSnap = collisions
            .filter(c => c.id !== node.id)
            .find(c => Math.abs(c.minX - node.position.x) < SNAP_THRESHOLD)?.minX;
        const hSnap = collisions
            .filter(c => c.id !== node.id)
            .find(c => Math.abs(c.minY - node.position.y) < SNAP_THRESHOLD)?.minY;

        setAlignmentGuidesAtom({ vertical: vSnap, horizontal: hSnap });

        // ← THIS IS WHAT'S MISSING: actually snap the node
        if (vSnap !== undefined || hSnap !== undefined) {
            config.reactFlowInstance?.setNodes(nds =>
                nds.map(n => n.id === node.id
                    ? { ...n, position: { x: vSnap ?? n.position.x, y: hSnap ?? n.position.y } }
                    : n
                )
            );
        }
    } catch {
        setAlignmentGuidesAtom({ vertical: undefined, horizontal: undefined });
    }
}, [config.reactFlowInstance, setAlignmentGuidesAtom]);
```

### Pattern E — Batched Code Associations

```typescript
// New atom in canvasAtoms.ts
export const codeAssociationsAtom = atom<Map<string, CodeLink[]>>(new Map());

// New hook: useCodeAssociationsBatch.ts
export function useCodeAssociationsBatch(projectId: string) {
    const setAssociations = useSetAtom(codeAssociationsAtom);
    const nodeIds = useAtomValue(nodesAtom).map(n => n.id);
    
    useQuery(
        ['code-associations-batch', projectId, nodeIds.join(',')],
        () => api.getCodeAssociationsBatch(projectId, nodeIds),
        {
            onSuccess: (data) => setAssociations(new Map(data.map(d => [d.artifactId, d.links]))),
            staleTime: 30_000,
        }
    );
}

// Call once in CanvasWorkspace.tsx — replaces per-node useCodeAssociations calls
useCodeAssociationsBatch(projectId);

// ArtifactNode.tsx — read from atom instead of fetching
const codeLinks = useAtomValue(codeAssociationsAtom).get(data.id) ?? [];
```

### Pattern F — ArtifactNode Accessible Markup

```tsx
// ArtifactNode.tsx
export const ArtifactNode = React.memo(({ data, selected }: NodeProps<ArtifactNodeData>) => (
    <div
        role="article"
        aria-label={`${data.type}: ${data.title}`}
        aria-selected={selected}
        aria-describedby={`artifact-status-${data.id}`}
        tabIndex={selected ? 0 : -1}
        className={cn('...', selected && 'ring-2 ring-indigo-500')}
    >
        <span id={`artifact-status-${data.id}`} className="sr-only">
            Status: {data.status}. Phase: {data.phase}.
            {data.linkedCount ? `${data.linkedCount} links.` : ''}
        </span>
        {/* ... rest of node */}
    </div>
), (prev, next) =>
    prev.data.id === next.data.id &&
    prev.data.status === next.data.status &&
    prev.data.title === next.data.title &&
    prev.selected === next.selected
);
```

---

## 15. Maturity Score & Top 10 Fixes

### Scoring (0–10 per dimension)

| Dimension | v1 (Jan) | v2 (Feb) | Delta |
|---|---|---|---|
| Architecture / Code Health | 3 | 7 | +4 |
| UX Simplicity & Completeness | 2 | 5 | +3 |
| Interaction Model | 2 | 5 | +3 |
| Canvas Engine | 3 | 5 | +2 |
| Drag & Drop | 2 | 4 | +2 |
| Zoom & Pan | 4 | 6 | +2 |
| Arbitrary Content Nodes | 1 | 2 | +1 |
| Diagramming | 2 | 4 | +2 |
| Design System | 2 | 8 | +6 |
| Accessibility | 1 | 4 | +3 |
| Performance & Scalability | 2 | 5 | +3 |
| Extensibility | 3 | 5 | +2 |
| **Overall (avg)** | **2.25** | **5.0** | **+2.75** |
| **Weighted (UX/Engine heavy)** | **3.2** | **5.8** | **+2.6** |

### Hard Requirements

| Requirement | v1 | v2 | Evidence |
|---|---|---|---|
| Camera Abstraction | ❌ | ❌ | Dual viewport state (viewportAtom + useState) |
| Cursor-Centered Zoom | ❌ | ⚠️ | ReactFlow native — works but app can't extend |
| Scale-Aware Overlays | ❌ | ⚠️ | ReactFlow handles handles; AlignmentGuides unverified |
| Deterministic Hit-Testing | ❌ | ⚠️ | Spatial index exists; DOM hit-test still primary |
| Advanced Drag & Drop | ❌ | ❌ | Guides show but don't snap; no cancelability |
| Isolated Node Content | ❌ | ⚠️ | DiagramNode correct; content canvases broken |
| Command-Based Undo/Redo | ❌ | ❌ | Snapshot-based only |
| Discoverable Shortcuts | ❌ | ⚠️ | Shortcuts exist; `onShowShortcuts` is no-op |
| Scalable Performance | ❌ | ❌ | `onlyRenderVisibleElements` missing; N+1 queries |
| Accessible Canvas | ❌ | ❌ | Tab traversal exists; no DOM focus; no node ARIA |
| **Total** | **2.5/10** | **4.5/10** | |

### Top 10 Individual Fixes (Ranked by Impact × Effort)

| Rank | Fix | Impact | Effort | Dimension |
|---|---|---|---|---|
| 1 | Wire `onSelectionChange` → `selectedNodesAtom` | 🔴 Critical | 15 min | Selection, Copy, Delete |
| 2 | Add `onlyRenderVisibleElements` to `<ReactFlow>` | 🔴 Critical | 5 min | Performance (500+ node viability) |
| 3 | Implement `handleNodeClick` — open Inspector | 🔴 Critical | 1 hr | Core UX (inspector never opens) |
| 4 | Snap nodes to alignment guides in `onNodeDrag` | 🟠 High | 2 hr | DnD (guides are decorative only) |
| 5 | Delete local `useState` viewport; wire `viewportAtom` via `onMove` | 🟠 High | 30 min | Camera Abstraction |
| 6 | Batch `useCodeAssociations` at workspace level (N+1 fix) | 🟠 High | 1 day | Performance |
| 7 | `React.memo` on `ArtifactNode` with key comparator | 🟡 Medium | 2 hr | Performance |
| 8 | `tabIndex={-1}` on `#canvas-surface`; DOM `.focus()` in `focusNextNode` | 🟡 Medium | 2 hr | Accessibility |
| 9 | `role="article"` + `aria-label` on ArtifactNode | 🟡 Medium | 1 hr | Accessibility (WCAG 4.1.2) |
| 10 | Wire `CommandPalette actions` from a command registry | 🟡 Medium | 2 days | Discoverability (empty palette) |

---

*Next review gate: when all Top 10 fixes land in main. Target score: 7.5/10 maturity, 7/10 hard requirements.*
