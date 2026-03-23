/**
 * @ghatana/canvas Hybrid Canvas State Management
 *
 * Jotai atoms for hybrid canvas state with derived atoms
 * for performance optimization.
 *
 * @doc.type module
 * @doc.purpose State management with Jotai
 * @doc.layer core
 * @doc.pattern State
 */

import { atom, useAtom, useAtomValue, useSetAtom } from "jotai";
import { atomWithStorage } from "jotai/utils";
import type {
  HybridCanvasState,
  ViewportState,
  SelectionState,
  RenderingMode,
  ActiveLayer,
  LayerConfig,
  CanvasElement,
  CanvasNode,
  CanvasEdge,
  GridConfig,
  HistoryState,
  HistoryEntry,
} from "./types";

// =============================================================================
// DEFAULT VALUES
// =============================================================================

const DEFAULT_VIEWPORT: ViewportState = {
  x: 0,
  y: 0,
  zoom: 1,
  minZoom: 0.1,
  maxZoom: 5,
};

const DEFAULT_SELECTION: SelectionState = {
  elementIds: [],
  nodeIds: [],
  edgeIds: [],
  isMultiSelect: false,
  bounds: null,
};

const DEFAULT_LAYER_CONFIG: LayerConfig = {
  id: "freeform",
  visible: true,
  opacity: 1,
  interactive: true,
  zIndex: 0,
};

const DEFAULT_GRID: GridConfig = {
  visible: true,
  size: 20,
  snap: true,
  color: "#e0e0e0",
  type: "lines",
};

const DEFAULT_STATE: HybridCanvasState = {
  mode: "hybrid-freeform",
  activeLayer: "both",
  viewport: DEFAULT_VIEWPORT,
  selection: DEFAULT_SELECTION,
  layers: {
    freeform: { ...DEFAULT_LAYER_CONFIG, id: "freeform", zIndex: 0 },
    graph: { ...DEFAULT_LAYER_CONFIG, id: "graph", zIndex: 1 },
    overlay: {
      ...DEFAULT_LAYER_CONFIG,
      id: "overlay",
      zIndex: 2,
      interactive: false,
    },
  },
  elements: [],
  nodes: [],
  edges: [],
  tool: "select",
  readOnly: false,
  dimensions: { width: 0, height: 0 },
  grid: DEFAULT_GRID,
};

// =============================================================================
// CORE ATOMS
// =============================================================================

/**
 * Root canvas state atom
 */
export const hybridCanvasStateAtom = atom<HybridCanvasState>(DEFAULT_STATE);

/**
 * Rendering mode atom
 */
export const renderingModeAtom = atom(
  (get) => get(hybridCanvasStateAtom).mode,
  (get, set, mode: RenderingMode) => {
    const state = get(hybridCanvasStateAtom);
    set(hybridCanvasStateAtom, { ...state, mode });
  },
);

/**
 * Active layer atom
 */
export const activeLayerAtom = atom(
  (get) => get(hybridCanvasStateAtom).activeLayer,
  (get, set, activeLayer: ActiveLayer) => {
    const state = get(hybridCanvasStateAtom);
    set(hybridCanvasStateAtom, { ...state, activeLayer });
  },
);

/**
 * Viewport state atom
 */
export const viewportAtom = atom(
  (get) => get(hybridCanvasStateAtom).viewport,
  (
    get,
    set,
    viewport: ViewportState | ((prev: ViewportState) => ViewportState),
  ) => {
    const state = get(hybridCanvasStateAtom);
    const newViewport =
      typeof viewport === "function" ? viewport(state.viewport) : viewport;
    set(hybridCanvasStateAtom, { ...state, viewport: newViewport });
  },
);

/**
 * Selection state atom
 */
export const selectionAtom = atom(
  (get) => get(hybridCanvasStateAtom).selection,
  (
    get,
    set,
    selection: SelectionState | ((prev: SelectionState) => SelectionState),
  ) => {
    const state = get(hybridCanvasStateAtom);
    const newSelection =
      typeof selection === "function" ? selection(state.selection) : selection;
    set(hybridCanvasStateAtom, { ...state, selection: newSelection });
  },
);

/**
 * Current tool atom
 */
export const toolAtom = atom(
  (get) => get(hybridCanvasStateAtom).tool,
  (get, set, tool: string) => {
    const state = get(hybridCanvasStateAtom);
    set(hybridCanvasStateAtom, { ...state, tool });
  },
);

/**
 * Read-only mode atom
 */
export const readOnlyAtom = atom(
  (get) => get(hybridCanvasStateAtom).readOnly,
  (get, set, readOnly: boolean) => {
    const state = get(hybridCanvasStateAtom);
    set(hybridCanvasStateAtom, { ...state, readOnly });
  },
);

/**
 * Grid config atom
 */
export const gridAtom = atom(
  (get) => get(hybridCanvasStateAtom).grid,
  (get, set, grid: GridConfig | ((prev: GridConfig) => GridConfig)) => {
    const state = get(hybridCanvasStateAtom);
    const newGrid = typeof grid === "function" ? grid(state.grid) : grid;
    set(hybridCanvasStateAtom, { ...state, grid: newGrid });
  },
);

// =============================================================================
// ELEMENT ATOMS (Freeform Layer)
// =============================================================================

/**
 * All elements atom
 */
export const elementsAtom = atom(
  (get) => get(hybridCanvasStateAtom).elements,
  (
    get,
    set,
    elements: CanvasElement[] | ((prev: CanvasElement[]) => CanvasElement[]),
  ) => {
    const state = get(hybridCanvasStateAtom);
    const newElements =
      typeof elements === "function" ? elements(state.elements) : elements;
    set(hybridCanvasStateAtom, { ...state, elements: newElements });
  },
);

/**
 * Visible elements (filtered by hidden flag)
 */
export const visibleElementsAtom = atom((get) => {
  const elements = get(elementsAtom);
  return elements.filter((e) => !e.hidden);
});

/**
 * Selected elements
 */
export const selectedElementsAtom = atom((get) => {
  const elements = get(elementsAtom);
  const selection = get(selectionAtom);
  return elements.filter((e) => selection.elementIds.includes(e.id));
});

/**
 * Get element by ID
 */
export const elementByIdAtom = (id: string) =>
  atom((get) => {
    const elements = get(elementsAtom);
    return elements.find((e) => e.id === id);
  });

// =============================================================================
// NODE ATOMS (Graph Layer)
// =============================================================================

/**
 * All nodes atom
 */
export const nodesAtom = atom(
  (get) => get(hybridCanvasStateAtom).nodes,
  (get, set, nodes: CanvasNode[] | ((prev: CanvasNode[]) => CanvasNode[])) => {
    const state = get(hybridCanvasStateAtom);
    const newNodes = typeof nodes === "function" ? nodes(state.nodes) : nodes;
    set(hybridCanvasStateAtom, { ...state, nodes: newNodes });
  },
);

/**
 * Selected nodes
 */
export const selectedNodesAtom = atom((get) => {
  const nodes = get(nodesAtom);
  const selection = get(selectionAtom);
  return nodes.filter((n) => selection.nodeIds.includes(n.id));
});

/**
 * Get node by ID
 */
export const nodeByIdAtom = (id: string) =>
  atom((get) => {
    const nodes = get(nodesAtom);
    return nodes.find((n) => n.id === id);
  });

// =============================================================================
// EDGE ATOMS (Graph Layer)
// =============================================================================

/**
 * All edges atom
 */
export const edgesAtom = atom(
  (get) => get(hybridCanvasStateAtom).edges,
  (get, set, edges: CanvasEdge[] | ((prev: CanvasEdge[]) => CanvasEdge[])) => {
    const state = get(hybridCanvasStateAtom);
    const newEdges = typeof edges === "function" ? edges(state.edges) : edges;
    set(hybridCanvasStateAtom, { ...state, edges: newEdges });
  },
);

/**
 * Selected edges
 */
export const selectedEdgesAtom = atom((get) => {
  const edges = get(edgesAtom);
  const selection = get(selectionAtom);
  return edges.filter((e) => selection.edgeIds.includes(e.id));
});

/**
 * Get edge by ID
 */
export const edgeByIdAtom = (id: string) =>
  atom((get) => {
    const edges = get(edgesAtom);
    return edges.find((e) => e.id === id);
  });

// =============================================================================
// LAYER ATOMS
// =============================================================================

/**
 * Layer configs atom
 */
export const layersAtom = atom(
  (get) => get(hybridCanvasStateAtom).layers,
  (get, set, layers: HybridCanvasState["layers"]) => {
    const state = get(hybridCanvasStateAtom);
    set(hybridCanvasStateAtom, { ...state, layers });
  },
);

/**
 * Freeform layer config
 */
export const freeformLayerAtom = atom(
  (get) => get(layersAtom).freeform,
  (get, set, config: LayerConfig) => {
    const layers = get(layersAtom);
    set(layersAtom, { ...layers, freeform: config });
  },
);

/**
 * Graph layer config
 */
export const graphLayerAtom = atom(
  (get) => get(layersAtom).graph,
  (get, set, config: LayerConfig) => {
    const layers = get(layersAtom);
    set(layersAtom, { ...layers, graph: config });
  },
);

// =============================================================================
// HISTORY ATOMS
// =============================================================================

const DEFAULT_HISTORY: HistoryState = {
  past: [],
  future: [],
  maxSize: 50,
};

/**
 * History state atom
 */
export const historyAtom = atom<HistoryState>(DEFAULT_HISTORY);

/**
 * Can undo
 */
export const canUndoAtom = atom((get) => get(historyAtom).past.length > 0);

/**
 * Can redo
 */
export const canRedoAtom = atom((get) => get(historyAtom).future.length > 0);

/**
 * Push history entry
 */
export const pushHistoryAtom = atom(null, (get, set, action: string) => {
  const state = get(hybridCanvasStateAtom);
  const history = get(historyAtom);

  const entry: HistoryEntry = {
    timestamp: Date.now(),
    action,
    snapshot: {
      elements: state.elements,
      nodes: state.nodes,
      edges: state.edges,
    },
  };

  const past = [entry, ...history.past].slice(0, history.maxSize);

  set(historyAtom, {
    ...history,
    past,
    future: [], // Clear redo stack on new action
  });
});

/**
 * Undo action
 */
export const undoAtom = atom(null, (get, set) => {
  const history = get(historyAtom);
  const state = get(hybridCanvasStateAtom);

  if (history.past.length === 0) return;

  const [entry, ...rest] = history.past;

  // Create current state entry for future
  const currentEntry: HistoryEntry = {
    timestamp: Date.now(),
    action: "undo",
    snapshot: {
      elements: state.elements,
      nodes: state.nodes,
      edges: state.edges,
    },
  };

  // Restore previous state
  set(hybridCanvasStateAtom, {
    ...state,
    elements: entry.snapshot.elements,
    nodes: entry.snapshot.nodes,
    edges: entry.snapshot.edges,
  });

  // Update history
  set(historyAtom, {
    ...history,
    past: rest,
    future: [currentEntry, ...history.future],
  });
});

/**
 * Redo action
 */
export const redoAtom = atom(null, (get, set) => {
  const history = get(historyAtom);
  const state = get(hybridCanvasStateAtom);

  if (history.future.length === 0) return;

  const [entry, ...rest] = history.future;

  // Create current state entry for past
  const currentEntry: HistoryEntry = {
    timestamp: Date.now(),
    action: "redo",
    snapshot: {
      elements: state.elements,
      nodes: state.nodes,
      edges: state.edges,
    },
  };

  // Restore future state
  set(hybridCanvasStateAtom, {
    ...state,
    elements: entry.snapshot.elements,
    nodes: entry.snapshot.nodes,
    edges: entry.snapshot.edges,
  });

  // Update history
  set(historyAtom, {
    ...history,
    past: [currentEntry, ...history.past],
    future: rest,
  });
});

// =============================================================================
// STORE FACADE
// =============================================================================

/**
 * Hybrid canvas store object for easy access
 */
export const hybridCanvasStore = {
  state: hybridCanvasStateAtom,
  mode: renderingModeAtom,
  activeLayer: activeLayerAtom,
  viewport: viewportAtom,
  selection: selectionAtom,
  tool: toolAtom,
  readOnly: readOnlyAtom,
  grid: gridAtom,
  elements: elementsAtom,
  visibleElements: visibleElementsAtom,
  selectedElements: selectedElementsAtom,
  nodes: nodesAtom,
  selectedNodes: selectedNodesAtom,
  edges: edgesAtom,
  selectedEdges: selectedEdgesAtom,
  layers: layersAtom,
  freeformLayer: freeformLayerAtom,
  graphLayer: graphLayerAtom,
  history: historyAtom,
  canUndo: canUndoAtom,
  canRedo: canRedoAtom,
  pushHistory: pushHistoryAtom,
  undo: undoAtom,
  redo: redoAtom,
};

// =============================================================================
// HOOKS
// =============================================================================

/**
 * Hook to get full hybrid canvas state
 */
export function useHybridCanvasState() {
  return useAtomValue(hybridCanvasStateAtom);
}

/**
 * Hook for viewport state
 */
export function useViewport() {
  return useAtom(viewportAtom);
}

/**
 * Hook for selection state
 */
export function useSelection() {
  return useAtom(selectionAtom);
}

/**
 * Hook for rendering mode
 */
export function useRenderingMode() {
  return useAtom(renderingModeAtom);
}

/**
 * Hook for active layer
 */
export function useActiveLayer() {
  return useAtom(activeLayerAtom);
}
