# YAPPC Canvas UI/UX & Systems Audit and Remediation Plan
**Date:** 2026-02-28
**Scope:** YAPPC Frontend (`CanvasWorkspace.tsx` and related ReactFlow + Jotai integrations)
**Target:** Principal-level stability, precision, and performance parity with industry standard infinite-canvas tools (Figma, Notion Canvas, Miro).

---

## 1. Executive Summary & Maturity Score

**Maturity Score: 40/75 (Late Beta / Needs Render Optimization)**

The YAPPC Canvas application leverages an excellent foundation (ReactFlow + Jotai) and demonstrates strong product thinking through spatial zones, ghost nodes, and phase-aware next-task recommendations. However, structural coupling in the rendering loop, monolithic input handling, and leaking coordinate abstractions pose significant scaling and experiential risks. 

**Core Critical Risks (The "Why"):**
1. **Render Storms:** Relying on global Jotai atoms for continuous drag-event mutations will block the main thread and crash at >300 nodes.
2. **Keyboard Trapping:** Canvas shortcuts blindly capture interactions, destroying the ability to type into text areas inside `ArtifactNode` components.
3. **Unsafe Abstraction Fallbacks:** Zoom-unaware fallback math during DnD creates corrupted node states if dropped early.

---

## 2. Granular Architectural Gaps Audit

### 2.1 Interaction Model & Event Swallowing
* **The Gap:** The `keydown` listener in `CanvasWorkspace.tsx` fires regardless of the current DOM focus. Pressing "1" to type a number inside a canvas text input will immediately pan the user to the "Intent" phase. Pressing `Cmd+Z` inside Monaco will trigger a canvas-level undo instead of a line undo.
* **The Physics Defect:** Sketch mode activation relies entirely on CSS `pointer-events: none` on the main ReactFlow wrapper. If modals or portals interact poorly with this, the canvas can become permanently locked.

### 2.2 The Jotai ↔ ReactFlow Synchronization Storm
* **The Gap:** The `onNodesChange` handler applies every minor coordinate delta to Jotai state immediately:
  ```typescript
  // Currently triggers Jotai notification on EVERY mouse-move pixel!
  const onNodesChange = useCallback((changes) => {
      setNodesAtom((nds) => applyNodeChanges(changes, nds));
  }, []);
  ```
* **The Impact:** Because the Inspector and Panel managers listen to these atoms, the entire application interface is forced to re-render 60 times a second during a simple node drag.

### 2.3 Coordinate Math & Drag-and-Drop Limitations
* **The Gap:** `handleCanvasDrop` has a fallback: `x: event.clientX - canvasBounds.left`. This math is ignorant of the camera zoom scale and current pan origin.
* **The Impact:** If `reactFlowInstance` is not fully initialized, dropped templates fly to wild coordinate offsets.
* **Secondary Gap:** `handlePasteNodes` uses static +50, +20px offsets. It does not look for spatial collisions or snap to a logical grid, looking messy on bulk pastes.

### 2.4 Bounding Box Fallacies
* **The Gap:** `handleZoomToPhase` calculates group bounds by hardcoding `+ 200` width and `+ 150` height.
* **The Impact:** As `ArtifactNode` content grows (e.g., expanding to show lists or diagrams), zoom calculations will fail to enclose the actual spatial bounds, cutting off content.

### 2.5 Extensibility & Modularity
* **The Gap:** `nodeTypes` and `edgeTypes` are hardcoded directly within `CanvasWorkspace.tsx`.
* **The Impact:** Introducing a new node type requires touching the core rendering engine file, violating Open/Closed principles.

---

## 3. Phased Remediation Plan

### Phase 1: Critical Path & Hotfixes (0–2 Days)
*Focus: Stop rendering crashes, prevent data-loss via UX glitches, and fix keyboard boundaries.*

- [ ] **Fix 1A: Input Guarding.** Wrap the global `useEffect` keyboard listener to early-return if `document.activeElement` is an editable element.
- [ ] **Fix 1B: Jotai Drag Throttling.** Modify `onNodesChange` to intercept pure `position` changes where `dragging: true`. Mutate ReactFlow's local copy, but heavily throttle or debounce synchronizing these partial changes to `nodesAtom` until `onNodeDragStop`.
- [ ] **Fix 1C: Prevent Corrupt Drops.** Remove the fallback coordinate math. If `reactFlowInstance` is null, do not execute the drop, or queue it.
- [ ] **Fix 1D: Explicit Event Bubbling.** Add ReactFlow's `.nodrag`, `.nopan`, and `.nowheel` CSS classes to all interactive elements inside `ArtifactNode` (e.g., buttons, textareas).

### Phase 2: Architecture Stabilization (1–2 Weeks)
*Focus: Standardize UI discovery, fix math limits, and extract domain logic from the engine.*

- [ ] **Fix 2A: Command Palette Unification.** Deprecate distinct `SearchPanel` and floating tools into a unified `Cmd+K` Command Palette (integrating AI operations and searches).
- [ ] **Fix 2B: Real Bounding Boxes.** Refactor `handleZoomToPhase` using ReactFlow's internal `useStoreApi().getState().nodeInternals` to measure true sizes.
- [ ] **Fix 2C: Extract Canvas Registry.** Move `nodeTypes` and `edgeTypes` into an injected context provider to keep `CanvasWorkspace.tsx` ignorant of specific business node types.
- [ ] **Fix 2D: Unified Interaction Context.** Replace `interactionMode` CSS trickery with a formal State Machine. Modes should dictate event router behavior natively, not just toggle visibility.

### Phase 3: Best-In-Class Features (1–2 Months)
*Focus: Scale capability towards 5k+ nodes and pro-level user mechanics.*

- [ ] **Fix 3A: Spatial Indexing.** Connect `nodesAtom` to an off-thread RBush (spatial tree) to perform immediate multi-node collisions, proximity auto-linking, and alignment guides.
- [ ] **Fix 3B: Diagram Native Integration.** Convert `MermaidDiagram` from an absolute DOM overlay into a First-Class Canvas Node tracked by the camera context, so it zooms and pans seamlessly alongside artifact nodes.
- [ ] **Fix 3C: Undo/Redo Engine Synchronization.** Move from basic action logging to an Atomic Transaction model bridging Jotai and ReactFlow, ensuring drag actions and text keystrokes are batched logically.

---

## 4. Golden Patterns & Implementations

### Golden Pattern: Safe Global Keyboard Tracking
Replace the monolithic `handleKeyDown` with DOM-aware guards to protect component isolation.

```typescript
const handleKeyDown = (event: KeyboardEvent) => {
    // 1. Check for Active User Inputs
    const activeEl = document.activeElement as HTMLElement;
    const isTyping = 
        activeEl?.tagName === 'INPUT' || 
        activeEl?.tagName === 'TEXTAREA' || 
        activeEl?.tagName === 'SELECT' || 
        activeEl?.isContentEditable;

    if (isTyping) {
        // Allow user to escape out of a text input, returning focus to canvas
        if (event.key === 'Escape') {
            activeEl.blur();
        }
        // Swallow event so canvas doesn't steal backspace, undo, or commands
        return; 
    }

    // 2. Process Safe Canvas Shortcuts
    if ((event.metaKey || event.ctrlKey) && event.key === 'z') {
        event.preventDefault();
        // ... invoke undo
    }
};
```

### Golden Pattern: ReactFlow vs. External State Decoupling
To avoid global rendering bottlenecks, strictly separate structural state from transient coordinate state.

```typescript
const onNodesChange = useCallback((changes: NodeChange[]) => {
    // Separate structural vs transient coordinate changes
    const structuralChanges = changes.filter(c => 
        c.type !== 'position' || (c.type === 'position' && !c.dragging)
    );
    
    // Immediate global flush for structure (adds, removes, dimension updates)
    if (structuralChanges.length > 0) {
        setNodesAtom(prev => applyNodeChanges(structuralChanges, prev));
    }
    
    // Do NOT aggressively sync `nodesAtom` if the change is purely mouse-tracking coordinates.
    // ReactFlow internally updates the UI without needing React Context validation.
    // Sync transient data heavily debounced, or strictly on NodeDragStop callbacks.
}, [setNodesAtom]);
```

### Golden Pattern: Dynamic Bounding Box Resolution
Replace hardcoded geometry (`+ 200, + 150`) with exact DOM-calculated bounds via the ReactFlow internal store.

```typescript
// Inside a component wrapped with <ReactFlowProvider>
import { useStoreApi } from '@xyflow/react';

const store = useStoreApi();

const handleZoomToPhase = useCallback((phase: LifecyclePhase) => {
    const { nodeInternals } = store.getState();
    const phaseNodeIds = generatedNodes.filter(n => n.data.phase === phase).map(n => n.id);
    
    if (phaseNodeIds.length === 0) return;

    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;

    phaseNodeIds.forEach(id => {
        const node = nodeInternals.get(id);
        if (node && node.computed?.width && node.computed?.height) {
            minX = Math.min(minX, node.position.x);
            minY = Math.min(minY, node.position.y);
            maxX = Math.max(maxX, node.position.x + node.computed.width);
            maxY = Math.max(maxY, node.position.y + node.computed.height);
        }
    });
    
    if (minX !== Infinity && reactFlowInstance) {
        // Calculate center precisely
        const centerX = minX + ((maxX - minX) / 2);
        const centerY = minY + ((maxY - minY) / 2);
        reactFlowInstance.setCenter(centerX, centerY, { zoom: 0.8, duration: 800 });
    }
}, [generatedNodes, reactFlowInstance, store]);
```

---

## 5. Required Definition of Done (DoD) for Canvas Changes
*Any PR affecting `CanvasWorkspace.tsx` MUST comply with:*

1. [ ] **No Render Storms**: Dragging nodes does not trigger full-page DOM re-paints.
2. [ ] **Keyboard Boundary Safety**: Writing inside a text input safely swallows keyboard shortcuts.
3. [ ] **Pan/Zoom Math Integrity**: All drop-events, calculations, and ghost-node positioning exclusively use `reactFlowInstance.screenToFlowPosition`, never global DOM metrics.
4. [ ] **Class Isolation**: Nested portals or editors explicitly invoke `.nodrag` and `.nopan` boundaries.
5. [ ] **Clean Fallbacks**: Operations relying on `reactFlowInstance` elegantly abort or queue if invoked prior to canvas mounting.