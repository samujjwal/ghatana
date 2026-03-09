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

import { getDefaultStore } from "jotai";
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
  undoAtom,
  redoAtom,
  canUndoAtom,
  canRedoAtom,
} from "./state";
import {
  screenToCanvas,
  canvasToScreen,
  snapToGrid,
  getBoundingRect,
  calculateZoomToFit,
} from "./coordinates";

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
  pushHistory(action: string): void;

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
 */
export class HybridCanvasController implements HybridCanvasAPI {
  private store = getDefaultStore();
  private containerRef: HTMLElement | null = null;

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
    this.store.set(selectionAtom, {
      elementIds: ids.elements ?? [],
      nodeIds: ids.nodes ?? [],
      edgeIds: ids.edges ?? [],
      isMultiSelect: false,
      bounds: null, // TODO: Calculate bounds
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

    const elements = this.getElements();
    this.store.set(elementsAtom, [...elements, newElement]);
    this.pushHistory(`Add element: ${newElement.type}`);

    return newElement;
  }

  updateElement(id: string, updates: Partial<CanvasElement>): void {
    const elements = this.getElements();
    const index = elements.findIndex((e) => e.id === id);
    if (index === -1) return;

    const newElements = [...elements];
    newElements[index] = { ...newElements[index], ...updates };
    this.store.set(elementsAtom, newElements);
  }

  deleteElement(id: string): void {
    const elements = this.getElements();
    this.store.set(
      elementsAtom,
      elements.filter((e) => e.id !== id),
    );
    this.removeFromSelection({ elements: [id] });
    this.pushHistory("Delete element");
  }

  deleteElements(ids: string[]): void {
    const elements = this.getElements();
    this.store.set(
      elementsAtom,
      elements.filter((e) => !ids.includes(e.id)),
    );
    this.removeFromSelection({ elements: ids });
    this.pushHistory(`Delete ${ids.length} elements`);
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

    const nodes = this.getNodes();
    this.store.set(nodesAtom, [...nodes, newNode]);
    this.pushHistory(`Add node: ${newNode.type ?? "default"}`);

    return newNode;
  }

  updateNode(id: string, updates: Partial<CanvasNode>): void {
    const nodes = this.getNodes();
    const index = nodes.findIndex((n) => n.id === id);
    if (index === -1) return;

    const newNodes = [...nodes];
    newNodes[index] = { ...newNodes[index], ...updates };
    this.store.set(nodesAtom, newNodes);
  }

  deleteNode(id: string): void {
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
    this.pushHistory("Delete node");
  }

  deleteNodes(ids: string[]): void {
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
    this.pushHistory(`Delete ${ids.length} nodes`);
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

    const edges = this.getEdges();
    this.store.set(edgesAtom, [...edges, newEdge]);
    this.pushHistory("Add edge");

    return newEdge;
  }

  updateEdge(id: string, updates: Partial<CanvasEdge>): void {
    const edges = this.getEdges();
    const index = edges.findIndex((e) => e.id === id);
    if (index === -1) return;

    const newEdges = [...edges];
    newEdges[index] = { ...newEdges[index], ...updates };
    this.store.set(edgesAtom, newEdges);
  }

  deleteEdge(id: string): void {
    const edges = this.getEdges();
    this.store.set(
      edgesAtom,
      edges.filter((e) => e.id !== id),
    );
    this.removeFromSelection({ edges: [id] });
    this.pushHistory("Delete edge");
  }

  deleteEdges(ids: string[]): void {
    const edges = this.getEdges();
    this.store.set(
      edgesAtom,
      edges.filter((e) => !ids.includes(e.id)),
    );
    this.removeFromSelection({ edges: ids });
    this.pushHistory(`Delete ${ids.length} edges`);
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

  pushHistory(action: string): void {
    this.store.set(pushHistoryAtom, action);
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

    // Duplicate elements
    const elements = this.getElements();
    const newElementIds: string[] = [];

    for (const id of selection.elementIds) {
      const element = elements.find((e) => e.id === id);
      if (element) {
        const newElement = this.addElement({
          ...element,
          id: undefined,
          position: {
            x: element.position.x + offset,
            y: element.position.y + offset,
          },
        });
        newElementIds.push(newElement.id);
      }
    }

    // Duplicate nodes
    const nodes = this.getNodes();
    const newNodeIds: string[] = [];

    for (const id of selection.nodeIds) {
      const node = nodes.find((n) => n.id === id);
      if (node) {
        const newNode = this.addNode({
          ...node,
          id: undefined,
          position: {
            x: node.position.x + offset,
            y: node.position.y + offset,
          },
        });
        newNodeIds.push(newNode.id);
      }
    }

    // Select duplicated items
    this.select({
      elements: newElementIds,
      nodes: newNodeIds,
      edges: [],
    });
  }

  groupSelected(): void {
    // TODO: Implement grouping
    console.warn("groupSelected not yet implemented");
  }

  ungroupSelected(): void {
    // TODO: Implement ungrouping
    console.warn("ungroupSelected not yet implemented");
  }

  // ===========================================================================
  // PRIVATE HELPERS
  // ===========================================================================

  private generateId(prefix: string): string {
    return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
  }
}

// Singleton instance
let controllerInstance: HybridCanvasController | null = null;

/**
 * Get the hybrid canvas controller singleton
 */
export function getHybridCanvasController(): HybridCanvasController {
  if (!controllerInstance) {
    controllerInstance = new HybridCanvasController();
  }
  return controllerInstance;
}

/**
 * Reset the controller (for testing)
 */
export function resetHybridCanvasController(): void {
  controllerInstance = null;
}
