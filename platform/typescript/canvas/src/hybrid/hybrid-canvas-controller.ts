/**
 * @ghatana/canvas Hybrid Canvas Controller
 *
 * Central controller for hybrid canvas operations.
 * Provides unified API for both freeform and graph layers.
 *
 * @doc.type class
 * @doc.purpose Canvas operations controller
 * @doc.layer core
 * @doc.pattern Controller
 */

import { createStore } from "jotai";
import type {
  HybridCanvasState,
  ViewportState,
  SelectionState,
  RenderingMode,
  ActiveLayer,
  CanvasElement,
  CanvasNode,
  CanvasEdge,
  Point,
  Rect,
  GridConfig,
} from "./types";
import {
  hybridCanvasStateAtom,
  viewportAtom,
  selectionAtom,
  renderingModeAtom,
  activeLayerAtom,
  elementsAtom,
  nodesAtom,
  edgesAtom,
  toolAtom,
  gridAtom,
  pushHistoryAtom,
  historyAtom,
  undoAtom,
  redoAtom,
  canUndoAtom,
  canRedoAtom,
  type HistoryEntry,
} from "./state";
import {
  screenToCanvas,
  canvasToScreen,
  snapToGrid,
  getBoundingRect,
  calculateZoomToFit,
} from "./coordinates";
import type { CanvasCommand } from "../commands/types.js";
import { CanvasCommandExecutor } from "../commands/executor.js";
import { CommandTransaction } from "../commands/types.js";

// ============================================================================
// DEPENDENCY INTERFACES
// ============================================================================

/**
 * Clock interface for time-based operations.
 * Allows deterministic time injection for testing.
 */
export interface Clock {
  readonly now: () => number;
}

/**
 * Default clock using Date.now().
 */
export const defaultClock: Clock = {
  now: () => Date.now(),
};

/**
 * ID provider interface for generating unique identifiers.
 * Allows deterministic ID generation for testing.
 */
export interface IdProvider {
  readonly generate: (prefix: string) => string;
}

/**
 * Default ID provider using `crypto.randomUUID()`.
 * Produces stable, globally-unique identifiers suitable for production use.
 */
export const defaultIdProvider: IdProvider = {
  generate: (prefix: string) => `${prefix}-${crypto.randomUUID()}`,
};

/**
 * Controller dependencies.
 */
export interface HybridCanvasControllerDependencies {
  /** Jotai store for state management. Defaults to a fresh createStore(). */
  readonly store?: ReturnType<typeof createStore>;
  /** Clock for time-based operations. Defaults to Date.now(). */
  readonly clock?: Clock;
  /** ID provider for generating unique identifiers. Defaults to timestamp + random. */
  readonly idProvider?: IdProvider;
}

/**
 * Public API for hybrid canvas operations
 */
export interface HybridCanvasAPI {
  // State
  getState(): HybridCanvasState;

  // Viewport
  getViewport(): ViewportState;
  setViewport(viewport: ViewportState): void;
  pan(dx: number, dy: number): void;
  zoom(factor: number, center?: Point): void;
  zoomTo(level: number, center?: Point): void;
  fitToContent(padding?: number): void;
  centerOn(point: Point): void;

  // Mode & Layer
  getMode(): RenderingMode;
  setMode(mode: RenderingMode): void;
  getActiveLayer(): ActiveLayer;
  setActiveLayer(layer: ActiveLayer): void;

  // Selection
  getSelection(): SelectionState;
  select(ids: {
    elements?: string[];
    nodes?: string[];
    edges?: string[];
  }): void;
  addToSelection(ids: {
    elements?: string[];
    nodes?: string[];
    edges?: string[];
  }): void;
  removeFromSelection(ids: {
    elements?: string[];
    nodes?: string[];
    edges?: string[];
  }): void;
  clearSelection(): void;
  selectAll(): void;

  // Elements (Freeform Layer)
  getElements(): CanvasElement[];
  getElementById(id: string): CanvasElement | undefined;
  addElement(
    element: Omit<CanvasElement, "id"> & { id?: string },
  ): CanvasElement;
  updateElement(id: string, updates: Partial<CanvasElement>): void;
  deleteElement(id: string): void;
  deleteElements(ids: string[]): void;

  // Nodes (Graph Layer)
  getNodes(): CanvasNode[];
  getNodeById(id: string): CanvasNode | undefined;
  addNode(node: Omit<CanvasNode, "id"> & { id?: string }): CanvasNode;
  updateNode(id: string, updates: Partial<CanvasNode>): void;
  deleteNode(id: string): void;
  deleteNodes(ids: string[]): void;

  // Edges (Graph Layer)
  getEdges(): CanvasEdge[];
  getEdgeById(id: string): CanvasEdge | undefined;
  addEdge(edge: Omit<CanvasEdge, "id"> & { id?: string }): CanvasEdge;
  updateEdge(id: string, updates: Partial<CanvasEdge>): void;
  deleteEdge(id: string): void;
  deleteEdges(ids: string[]): void;

  // Tools
  getTool(): string;
  setTool(tool: string): void;

  // Grid
  getGrid(): GridConfig;
  setGrid(config: Partial<GridConfig>): void;
  toggleGrid(): void;
  toggleSnap(): void;

  // History
  canUndo(): boolean;
  canRedo(): boolean;
  undo(): void;
  redo(): void;
  getHistoryDepths(): { undo: number; redo: number };
  pushHistory(params: { action: string; snapshot: HistoryEntry['snapshot'] }): void;
  /** Returns a snapshot of the mutable arrays tracked by history (elements, nodes, edges). */
  getSnapshot(): HistoryEntry['snapshot'];
  /** Execute a command, capturing exactly one undo/redo history entry. */
  execute(command: CanvasCommand): void;
  /** Begin an imperative transaction that batches multiple mutations into one undo step. */
  beginTransaction(description: string): CommandTransaction;

  // Utilities
  screenToCanvas(point: Point): Point;
  canvasToScreen(point: Point): Point;
  snapToGrid(point: Point): Point;
  deleteSelected(): void;
  duplicateSelected(): void;
  groupSelected(): void;
  ungroupSelected(): void;
}

/**
 * Hybrid Canvas Controller
 *
 * Manages all canvas operations through Jotai store.
 * Instance-scoped with injectable dependencies for testability.
 */
export class HybridCanvasController implements HybridCanvasAPI {
  private readonly store: ReturnType<typeof createStore>;
  private readonly clock: Clock;
  private readonly idProvider: IdProvider;
  private containerRef: HTMLElement | null = null;
  /**
   * Transaction context that suppresses individual history pushes.
   * When active, mutators should not call pushHistory — the transaction
   * will push a single entry on commit.
   */
  private transactionContext: {
    active: boolean;
    description: string;
    preSnapshot: HistoryEntry['snapshot'] | null;
  } | null = null;

  /**
   * Create a new hybrid canvas controller with optional dependencies.
   *
   * @param dependencies - Optional dependencies (store, clock, ID provider)
   */
  constructor(dependencies: HybridCanvasControllerDependencies = {}) {
    this.store = dependencies.store ?? createStore();
    this.clock = dependencies.clock ?? defaultClock;
    this.idProvider = dependencies.idProvider ?? defaultIdProvider;
  }

  /**
   * Set the container reference for viewport calculations
   */
  setContainer(container: HTMLElement | null): void {
    this.containerRef = container;
  }

  // ===========================================================================
  // STATE
  // ===========================================================================

  getState(): HybridCanvasState {
    return this.store.get(hybridCanvasStateAtom);
  }

  // ===========================================================================
  // VIEWPORT
  // ===========================================================================

  getViewport(): ViewportState {
    return this.store.get(viewportAtom);
  }

  setViewport(viewport: ViewportState): void {
    this.store.set(viewportAtom, viewport);
  }

  pan(dx: number, dy: number): void {
    const viewport = this.getViewport();
    this.setViewport({
      ...viewport,
      x: viewport.x + dx,
      y: viewport.y + dy,
    });
  }

  zoom(factor: number, center?: Point): void {
    const viewport = this.getViewport();
    const newZoom = Math.max(
      viewport.minZoom,
      Math.min(viewport.maxZoom, viewport.zoom * factor),
    );
    this.zoomTo(newZoom, center);
  }

  zoomTo(level: number, center?: Point): void {
    const viewport = this.getViewport();
    const clampedZoom = Math.max(
      viewport.minZoom,
      Math.min(viewport.maxZoom, level),
    );

    if (center) {
      // Zoom towards center point
      const canvasCenter = screenToCanvas(center, viewport);
      const newX = center.x - canvasCenter.x * clampedZoom;
      const newY = center.y - canvasCenter.y * clampedZoom;

      this.setViewport({
        ...viewport,
        x: newX,
        y: newY,
        zoom: clampedZoom,
      });
    } else {
      // Zoom towards container center
      if (this.containerRef) {
        const rect = this.containerRef.getBoundingClientRect();
        const containerCenter = { x: rect.width / 2, y: rect.height / 2 };
        this.zoomTo(clampedZoom, containerCenter);
      } else {
        this.setViewport({ ...viewport, zoom: clampedZoom });
      }
    }
  }

  fitToContent(padding = 50): void {
    const elements = this.getElements();
    const nodes = this.getNodes();

    const rects: Rect[] = [
      ...elements.map((e) => ({
        x: e.position.x,
        y: e.position.y,
        width: e.size.width,
        height: e.size.height,
      })),
      ...nodes.map((n) => ({
        x: n.position.x,
        y: n.position.y,
        width: (n.measured?.width ?? n.width ?? 200) as number,
        height: (n.measured?.height ?? n.height ?? 100) as number,
      })),
    ];

    const bounds = getBoundingRect(rects);
    if (!bounds || !this.containerRef) return;

    const rect = this.containerRef.getBoundingClientRect();
    const newViewport = calculateZoomToFit(
      bounds,
      rect.width,
      rect.height,
      padding,
    );
    this.setViewport(newViewport);
  }

  centerOn(point: Point): void {
    if (!this.containerRef) return;

    const viewport = this.getViewport();
    const rect = this.containerRef.getBoundingClientRect();

    this.setViewport({
      ...viewport,
      x: rect.width / 2 - point.x * viewport.zoom,
      y: rect.height / 2 - point.y * viewport.zoom,
    });
  }

  // ===========================================================================
  // MODE & LAYER
  // ===========================================================================

  getMode(): RenderingMode {
    return this.store.get(renderingModeAtom);
  }

  setMode(mode: RenderingMode): void {
    this.store.set(renderingModeAtom, mode);
  }

  getActiveLayer(): ActiveLayer {
    return this.store.get(activeLayerAtom);
  }

  setActiveLayer(layer: ActiveLayer): void {
    this.store.set(activeLayerAtom, layer);
  }

  // ===========================================================================
  // SELECTION
  // ===========================================================================

  getSelection(): SelectionState {
    return this.store.get(selectionAtom);
  }

  select(ids: {
    elements?: string[];
    nodes?: string[];
    edges?: string[];
  }): void {
    const nodeIds = ids.nodes ?? [];
    const elementIds = ids.elements ?? [];
    let bounds: Rect | null = null;
    if (nodeIds.length > 0) {
      const allNodes = this.store.get(nodesAtom);
      const selected = allNodes.filter((n) => nodeIds.includes(n.id));
      if (selected.length > 0) {
        const xs = selected.map((n) => n.position.x);
        const ys = selected.map((n) => n.position.y);
        const x2s = selected.map((n) => n.position.x + ((n as { measured?: { width?: number } }).measured?.width ?? 100));
        const y2s = selected.map((n) => n.position.y + ((n as { measured?: { height?: number } }).measured?.height ?? 40));
        const minX = Math.min(...xs);
        const minY = Math.min(...ys);
        bounds = { x: minX, y: minY, width: Math.max(...x2s) - minX, height: Math.max(...y2s) - minY };
      }
    }
    this.store.set(selectionAtom, {
      elementIds,
      nodeIds,
      edgeIds: ids.edges ?? [],
      isMultiSelect: false,
      bounds,
    });
  }

  addToSelection(ids: {
    elements?: string[];
    nodes?: string[];
    edges?: string[];
  }): void {
    const selection = this.getSelection();
    this.store.set(selectionAtom, {
      ...selection,
      elementIds: [
        ...new Set([...selection.elementIds, ...(ids.elements ?? [])]),
      ],
      nodeIds: [...new Set([...selection.nodeIds, ...(ids.nodes ?? [])])],
      edgeIds: [...new Set([...selection.edgeIds, ...(ids.edges ?? [])])],
      isMultiSelect: true,
    });
  }

  removeFromSelection(ids: {
    elements?: string[];
    nodes?: string[];
    edges?: string[];
  }): void {
    const selection = this.getSelection();
    this.store.set(selectionAtom, {
      ...selection,
      elementIds: selection.elementIds.filter(
        (id) => !ids.elements?.includes(id),
      ),
      nodeIds: selection.nodeIds.filter((id) => !ids.nodes?.includes(id)),
      edgeIds: selection.edgeIds.filter((id) => !ids.edges?.includes(id)),
    });
  }

  clearSelection(): void {
    this.select({ elements: [], nodes: [], edges: [] });
  }

  selectAll(): void {
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    this.select({
      elements: elements.filter((e) => !e.locked).map((e) => e.id),
      nodes: nodes.map((n) => n.id),
      edges: edges.map((e) => e.id),
    });
  }

  // ===========================================================================
  // ELEMENTS (Freeform Layer)
  // ===========================================================================

  getElements(): CanvasElement[] {
    return this.store.get(elementsAtom);
  }

  getElementById(id: string): CanvasElement | undefined {
    return this.getElements().find((e) => e.id === id);
  }

  addElement(
    element: Omit<CanvasElement, "id"> & { id?: string },
  ): CanvasElement {
    const newElement: CanvasElement = {
      ...element,
      id: element.id ?? this.generateId("element"),
    };

    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    this.store.set(elementsAtom, [...elements, newElement]);
    this.pushHistory({
      action: `Add element: ${newElement.type}`,
      snapshot: { elements, nodes, edges },
    });

    return newElement;
  }

  updateElement(id: string, updates: Partial<CanvasElement>): void {
    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    const index = elements.findIndex((e) => e.id === id);
    if (index === -1) return;

    const newElements = [...elements];
    newElements[index] = { ...newElements[index], ...updates };
    this.store.set(elementsAtom, newElements);

    this.pushHistory({
      action: `Update element: ${id}`,
      snapshot: { elements, nodes, edges },
    });
  }

  deleteElement(id: string): void {
    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    this.store.set(
      elementsAtom,
      elements.filter((e) => e.id !== id),
    );
    this.removeFromSelection({ elements: [id] });
    this.pushHistory({
      action: "Delete element",
      snapshot: { elements, nodes, edges },
    });
  }

  deleteElements(ids: string[]): void {
    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    this.store.set(
      elementsAtom,
      elements.filter((e) => !ids.includes(e.id)),
    );
    this.removeFromSelection({ elements: ids });
    this.pushHistory({
      action: `Delete ${ids.length} elements`,
      snapshot: { elements, nodes, edges },
    });
  }

  // ===========================================================================
  // NODES (Graph Layer)
  // ===========================================================================

  getNodes(): CanvasNode[] {
    return this.store.get(nodesAtom);
  }

  getNodeById(id: string): CanvasNode | undefined {
    return this.getNodes().find((n) => n.id === id);
  }

  addNode(node: Omit<CanvasNode, "id"> & { id?: string }): CanvasNode {
    const newNode: CanvasNode = {
      ...node,
      id: node.id ?? this.generateId("node"),
    };

    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    this.store.set(nodesAtom, [...nodes, newNode]);
    this.pushHistory({
      action: `Add node: ${newNode.type ?? "default"}`,
      snapshot: { elements, nodes, edges },
    });

    return newNode;
  }

  updateNode(id: string, updates: Partial<CanvasNode>): void {
    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    const index = nodes.findIndex((n) => n.id === id);
    if (index === -1) return;

    const newNodes = [...nodes];
    newNodes[index] = { ...newNodes[index], ...updates };
    this.store.set(nodesAtom, newNodes);

    this.pushHistory({
      action: `Update node: ${id}`,
      snapshot: { elements, nodes, edges },
    });
  }

  deleteNode(id: string): void {
    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    // Delete connected edges
    const connectedEdgeIds = edges
      .filter((e) => e.source === id || e.target === id)
      .map((e) => e.id);

    this.store.set(
      nodesAtom,
      nodes.filter((n) => n.id !== id),
    );
    this.store.set(
      edgesAtom,
      edges.filter((e) => !connectedEdgeIds.includes(e.id)),
    );
    this.removeFromSelection({ nodes: [id], edges: connectedEdgeIds });
    this.pushHistory({
      action: "Delete node",
      snapshot: { elements, nodes, edges },
    });
  }

  deleteNodes(ids: string[]): void {
    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    // Delete connected edges
    const connectedEdgeIds = edges
      .filter((e) => ids.includes(e.source) || ids.includes(e.target))
      .map((e) => e.id);

    this.store.set(
      nodesAtom,
      nodes.filter((n) => !ids.includes(n.id)),
    );
    this.store.set(
      edgesAtom,
      edges.filter((e) => !connectedEdgeIds.includes(e.id)),
    );
    this.removeFromSelection({ nodes: ids, edges: connectedEdgeIds });
    this.pushHistory({
      action: `Delete ${ids.length} nodes`,
      snapshot: { elements, nodes, edges },
    });
  }

  // ===========================================================================
  // EDGES (Graph Layer)
  // ===========================================================================

  getEdges(): CanvasEdge[] {
    return this.store.get(edgesAtom);
  }

  getEdgeById(id: string): CanvasEdge | undefined {
    return this.getEdges().find((e) => e.id === id);
  }

  addEdge(edge: Omit<CanvasEdge, "id"> & { id?: string }): CanvasEdge {
    const newEdge: CanvasEdge = {
      ...edge,
      id: edge.id ?? this.generateId("edge"),
    };

    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    this.store.set(edgesAtom, [...edges, newEdge]);
    this.pushHistory({
      action: "Add edge",
      snapshot: { elements, nodes, edges },
    });

    return newEdge;
  }

  updateEdge(id: string, updates: Partial<CanvasEdge>): void {
    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    const index = edges.findIndex((e) => e.id === id);
    if (index === -1) return;

    const newEdges = [...edges];
    newEdges[index] = { ...newEdges[index], ...updates };
    this.store.set(edgesAtom, newEdges);

    this.pushHistory({
      action: `Update edge: ${id}`,
      snapshot: { elements, nodes, edges },
    });
  }

  deleteEdge(id: string): void {
    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    this.store.set(
      edgesAtom,
      edges.filter((e) => e.id !== id),
    );
    this.removeFromSelection({ edges: [id] });
    this.pushHistory({
      action: "Delete edge",
      snapshot: { elements, nodes, edges },
    });
  }

  deleteEdges(ids: string[]): void {
    // Snapshot state BEFORE mutation for correct undo behavior
    const elements = this.getElements();
    const nodes = this.getNodes();
    const edges = this.getEdges();

    this.store.set(
      edgesAtom,
      edges.filter((e) => !ids.includes(e.id)),
    );
    this.removeFromSelection({ edges: ids });
    this.pushHistory({
      action: `Delete ${ids.length} edges`,
      snapshot: { elements, nodes, edges },
    });
  }

  // ===========================================================================
  // TOOLS
  // ===========================================================================

  getTool(): string {
    return this.store.get(toolAtom);
  }

  setTool(tool: string): void {
    this.store.set(toolAtom, tool);
  }

  // ===========================================================================
  // GRID
  // ===========================================================================

  getGrid(): GridConfig {
    return this.store.get(gridAtom);
  }

  setGrid(config: Partial<GridConfig>): void {
    const current = this.getGrid();
    this.store.set(gridAtom, { ...current, ...config });
  }

  toggleGrid(): void {
    const grid = this.getGrid();
    this.setGrid({ visible: !grid.visible });
  }

  toggleSnap(): void {
    const grid = this.getGrid();
    this.setGrid({ snap: !grid.snap });
  }

  // ===========================================================================
  // HISTORY
  // ===========================================================================

  canUndo(): boolean {
    return this.store.get(canUndoAtom);
  }

  canRedo(): boolean {
    return this.store.get(canRedoAtom);
  }

  undo(): void {
    this.store.set(undoAtom);
  }

  redo(): void {
    this.store.set(redoAtom);
  }

  getHistoryDepths(): { undo: number; redo: number } {
    const history = this.store.get(historyAtom);
    return {
      undo: history.past.length,
      redo: history.future.length,
    };
  }

  pushHistory(params: { action: string; snapshot: HistoryEntry['snapshot'] }): void {
    // Suppress individual history pushes when inside a transaction.
    // The transaction will push a single entry on commit.
    if (this.transactionContext?.active) {
      return;
    }
    this.store.set(pushHistoryAtom, params);
  }

  /**
   * Check if currently inside a transaction context.
   * Used by mutators to determine whether to skip individual history pushes.
   */
  isInTransaction(): boolean {
    return this.transactionContext?.active ?? false;
  }

  getSnapshot(): HistoryEntry['snapshot'] {
    const state = this.store.get(hybridCanvasStateAtom);
    return {
      elements: state.elements,
      nodes: state.nodes,
      edges: state.edges,
    };
  }

  execute(command: CanvasCommand): void {
    const executor = new CanvasCommandExecutor(this);
    executor.execute(command);
  }

  beginTransaction(description: string): CommandTransaction {
    // Capture pre-snapshot and activate transaction context
    const preSnapshot = this.getSnapshot();
    this.transactionContext = {
      active: true,
      description,
      preSnapshot,
    };

    return new CommandTransaction(this, description);
  }

  /**
   * Internal method called by CommandTransaction on commit.
   * Pushes the transaction's single history entry and clears the context.
   */
  commitTransaction(): void {
    if (!this.transactionContext?.active) {
      return;
    }

    const { description, preSnapshot } = this.transactionContext;
    if (preSnapshot !== null) {
      this.store.set(pushHistoryAtom, {
        action: description,
        snapshot: preSnapshot,
      });
    }

    this.transactionContext = null;
  }

  /**
   * Internal method called by CommandTransaction on abort.
   * Clears the transaction context without pushing history.
   */
  abortTransaction(): void {
    this.transactionContext = null;
  }

  // ===========================================================================
  // UTILITIES
  // ===========================================================================

  screenToCanvas(point: Point): Point {
    return screenToCanvas(point, this.getViewport());
  }

  canvasToScreen(point: Point): Point {
    return canvasToScreen(point, this.getViewport());
  }

  snapToGrid(point: Point): Point {
    const grid = this.getGrid();
    if (!grid.snap) return point;
    return snapToGrid(point, grid.size);
  }

  deleteSelected(): void {
    const selection = this.getSelection();

    if (selection.elementIds.length > 0) {
      this.deleteElements(selection.elementIds);
    }
    if (selection.nodeIds.length > 0) {
      this.deleteNodes(selection.nodeIds);
    }
    if (selection.edgeIds.length > 0) {
      this.deleteEdges(selection.edgeIds);
    }
  }

  duplicateSelected(): void {
    const selection = this.getSelection();
    const offset = 20;

    // Snapshot BEFORE any mutation — one history entry for the entire duplicate op.
    const snapshotElements = this.getElements();
    const snapshotNodes = this.getNodes();
    const snapshotEdges = this.getEdges();

    const newElementIds: string[] = [];
    let elements = snapshotElements;
    for (const id of selection.elementIds) {
      const element = elements.find((e) => e.id === id);
      if (element) {
        const newId = this.generateId("element");
        const newElement: CanvasElement = {
          ...element,
          id: newId,
          position: {
            x: element.position.x + offset,
            y: element.position.y + offset,
          },
        };
        elements = [...elements, newElement];
        newElementIds.push(newId);
      }
    }

    const newNodeIds: string[] = [];
    let nodes = snapshotNodes;
    for (const id of selection.nodeIds) {
      const node = nodes.find((n) => n.id === id);
      if (node) {
        const newId = this.generateId("node");
        const newNode: CanvasNode = {
          ...node,
          id: newId,
          position: {
            x: node.position.x + offset,
            y: node.position.y + offset,
          },
        };
        nodes = [...nodes, newNode];
        newNodeIds.push(newId);
      }
    }

    // Apply all mutations at once, then push ONE history entry.
    this.store.set(elementsAtom, elements);
    this.store.set(nodesAtom, nodes);
    this.pushHistory({
      action: `Duplicate ${selection.elementIds.length + selection.nodeIds.length} items`,
      snapshot: { elements: snapshotElements, nodes: snapshotNodes, edges: snapshotEdges },
    });

    this.select({ elements: newElementIds, nodes: newNodeIds, edges: [] });
  }

  groupSelected(): void {
    const selection = this.getSelection();
    if (selection.elementIds.length < 2) return;

    const allElements = this.getElements();
    const elements = selection.elementIds
      .map((id) => allElements.find((e) => e.id === id))
      .filter((e): e is CanvasElement => e !== undefined && !e.locked);
    if (elements.length < 2) return;

    // Snapshot BEFORE any mutation — one history entry for the entire group op.
    const snapshotElements = allElements;
    const snapshotNodes = this.getNodes();
    const snapshotEdges = this.getEdges();

    const minX = Math.min(...elements.map((e) => e.position.x));
    const minY = Math.min(...elements.map((e) => e.position.y));
    const maxX = Math.max(...elements.map((e) => e.position.x + e.size.width));
    const maxY = Math.max(...elements.map((e) => e.position.y + e.size.height));

    const childIds = elements.map((e) => e.id);
    const groupId = this.generateId("element");
    const group: CanvasElement = {
      id: groupId,
      type: "group",
      position: { x: minX, y: minY },
      size: { width: maxX - minX, height: maxY - minY },
      data: { childIds },
    } as CanvasElement;

    // Wire children to their new parent and add the group — all in one update.
    const updatedElements = allElements.map((e) =>
      childIds.includes(e.id) ? { ...e, parentId: groupId } : e,
    );
    this.store.set(elementsAtom, [...updatedElements, group]);

    // One history entry for the entire operation.
    this.pushHistory({
      action: `Group ${childIds.length} elements`,
      snapshot: { elements: snapshotElements, nodes: snapshotNodes, edges: snapshotEdges },
    });

    this.select({ elements: [groupId] });
  }

  ungroupSelected(): void {
    const selection = this.getSelection();
    const groupIds = selection.elementIds.filter((id) => {
      const e = this.getElementById(id);
      return e?.type === "group";
    });
    if (groupIds.length === 0) return;

    // Snapshot BEFORE any mutation — one history entry for the entire ungroup op.
    const snapshotElements = this.getElements();
    const snapshotNodes = this.getNodes();
    const snapshotEdges = this.getEdges();

    let elements = snapshotElements;
    const ungroupedIds: string[] = [];

    for (const groupId of groupIds) {
      const group = elements.find((e) => e.id === groupId);
      if (!group || group.type !== "group") continue;

      const childIds = (group.data.childIds as string[] | undefined) ?? [];
      // Clear parentId from all children, then remove the group.
      elements = elements
        .filter((e) => e.id !== groupId)
        .map((e) => (childIds.includes(e.id) ? { ...e, parentId: undefined } : e));
      ungroupedIds.push(...childIds);
    }

    // Apply all mutations at once.
    this.store.set(elementsAtom, elements);
    this.pushHistory({
      action: `Ungroup ${groupIds.length} group(s)`,
      snapshot: { elements: snapshotElements, nodes: snapshotNodes, edges: snapshotEdges },
    });

    if (ungroupedIds.length > 0) {
      this.select({ elements: ungroupedIds });
    }
  }

  // ===========================================================================
  // PRIVATE HELPERS
  // ===========================================================================

  private generateId(prefix: string): string {
    return this.idProvider.generate(prefix);
  }
}

/**
 * @deprecated Use `new HybridCanvasController()` directly.
 * Singleton pattern is deprecated in favor of instance-scoped controllers.
 * This function is kept for backward compatibility and will be removed in a future version.
 */
export function getHybridCanvasController(): HybridCanvasController {
  return new HybridCanvasController();
}

/**
 * @deprecated No longer needed with instance-scoped controllers.
 * This function is kept for backward compatibility and will be removed in a future version.
 */
export function resetHybridCanvasController(): void {
  // No-op with instance-scoped controllers
}
