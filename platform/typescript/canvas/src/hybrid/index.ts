/**
 * @ghatana/canvas Hybrid Canvas System
 *
 * Combines custom HTML5 Canvas (Freeform Layer) with ReactFlow (Graph Layer)
 * for maximum flexibility and power.
 *
 * @doc.type module
 * @doc.purpose Hybrid canvas entry point
 * @doc.layer core
 * @doc.pattern Module
 */

// Types
export type {
  HybridCanvasProps,
  HybridCanvasState,
  RenderingMode,
  LayerConfig,
  ViewportState,
  SelectionState,
  CoordinateSystem,
  CanvasElement,
  CanvasNode,
  CanvasEdge,
} from "./types";

// Controller
export { HybridCanvasController } from "./hybrid-canvas-controller";
export type { HybridCanvasAPI } from "./hybrid-canvas-controller";

// State management
export {
  hybridCanvasStore,
  useHybridCanvasState,
  useViewport,
  useSelection,
  useRenderingMode,
  useActiveLayer,
} from "./state";

// Hooks
export {
  useHybridCanvas,
  useCanvasElements,
  useCanvasNodes,
  useCanvasEdges,
  useCanvasViewport,
  useCanvasSelection,
  useCanvasTool,
} from "./hooks";

// Components
export { HybridCanvas } from "./HybridCanvas";
export { FreeformLayer } from "./FreeformLayer";
export { GraphLayer } from "./GraphLayer";
export { LayerContainer } from "./LayerContainer";

// Utilities
export {
  // Canonical names (prefer these in new code)
  screenToWorld,
  worldToScreen,
  // Legacy names (kept for backwards compatibility; @deprecated, see JSDoc)
  screenToCanvas,
  canvasToScreen,
  graphToCanvas,
  canvasToGraph,
  normalizeCoordinates,
} from "./coordinates";
