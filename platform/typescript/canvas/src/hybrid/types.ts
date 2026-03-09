/**
 * @ghatana/canvas Hybrid Canvas Types
 *
 * Type definitions for the hybrid canvas system.
 *
 * @doc.type module
 * @doc.purpose Hybrid canvas type definitions
 * @doc.layer core
 * @doc.pattern TypeDefinitions
 */

import type { Node, Edge, Viewport as RFViewport } from "@xyflow/react";
import type { ComponentType, ReactNode } from "react";

// =============================================================================
// RENDERING MODES
// =============================================================================

/**
 * Rendering mode determines which layers are active
 */
export type RenderingMode =
  | "hybrid-freeform" // Both layers, freeform primary
  | "hybrid-graph" // Both layers, graph primary
  | "freeform-only" // Only custom canvas
  | "graph-only"; // Only ReactFlow

/**
 * Which layer receives interactions
 */
export type ActiveLayer = "freeform" | "graph" | "both";

// =============================================================================
// VIEWPORT
// =============================================================================

/**
 * Unified viewport state shared by both layers
 */
export interface ViewportState {
  /** X offset (pan) */
  x: number;
  /** Y offset (pan) */
  y: number;
  /** Zoom level (1 = 100%) */
  zoom: number;
  /** Minimum zoom allowed */
  minZoom: number;
  /** Maximum zoom allowed */
  maxZoom: number;
}

/**
 * Viewport bounds in canvas coordinates
 */
export interface ViewportBounds {
  left: number;
  top: number;
  right: number;
  bottom: number;
  width: number;
  height: number;
}

// =============================================================================
// COORDINATE SYSTEMS
// =============================================================================

/**
 * Coordinate system identifier
 */
export type CoordinateSystem = "screen" | "canvas" | "graph";

/**
 * Point in any coordinate system
 */
export interface Point {
  x: number;
  y: number;
}

/**
 * Rectangle in any coordinate system
 */
export interface Rect {
  x: number;
  y: number;
  width: number;
  height: number;
}

// =============================================================================
// SELECTION
// =============================================================================

/**
 * Selection state across both layers
 */
export interface SelectionState {
  /** Selected freeform element IDs */
  elementIds: string[];
  /** Selected ReactFlow node IDs */
  nodeIds: string[];
  /** Selected ReactFlow edge IDs */
  edgeIds: string[];
  /** Is multi-select active (shift held) */
  isMultiSelect: boolean;
  /** Selection bounds in canvas coordinates */
  bounds: Rect | null;
}

// =============================================================================
// CANVAS ELEMENTS (Freeform Layer)
// =============================================================================

/**
 * Base interface for all freeform canvas elements
 */
export interface CanvasElement {
  /** Unique element ID */
  id: string;
  /** Element type (registered via plugin) */
  type: string;
  /** Position in canvas coordinates */
  position: Point;
  /** Element size */
  size: { width: number; height: number };
  /** Rotation in degrees */
  rotation?: number;
  /** Element opacity (0-1) */
  opacity?: number;
  /** Z-index for stacking */
  zIndex?: number;
  /** Is element locked */
  locked?: boolean;
  /** Is element hidden */
  hidden?: boolean;
  /** Element-specific data */
  data: Record<string, unknown>;
  /** Parent element ID (for grouping) */
  parentId?: string;
  /** Custom styles */
  style?: CanvasElementStyle;
}

/**
 * Canvas element visual style
 */
export interface CanvasElementStyle {
  fill?: string;
  stroke?: string;
  strokeWidth?: number;
  strokeDasharray?: string;
  shadow?: CanvasShadow;
  borderRadius?: number;
}

/**
 * Shadow definition
 */
export interface CanvasShadow {
  offsetX: number;
  offsetY: number;
  blur: number;
  color: string;
}

// =============================================================================
// GRAPH NODES & EDGES (Graph Layer)
// =============================================================================

/**
 * Extended ReactFlow node with canvas integration
 */
export interface CanvasNode<T extends Record<string, unknown> = Record<string, unknown>> extends Node<T> {
  /** Canvas-specific metadata */
  __canvas?: {
    /** Sync position with freeform element */
    syncElement?: string;
    /** Layer this node belongs to */
    layer?: "graph" | "overlay";
  };
}

/**
 * Extended ReactFlow edge with canvas integration
 */
export interface CanvasEdge<T extends Record<string, unknown> = Record<string, unknown>> extends Edge<T> {
  /** Canvas-specific metadata */
  __canvas?: {
    /** Sync with freeform connection */
    syncConnection?: string;
    /** Layer this edge belongs to */
    layer?: "graph" | "overlay";
  };
}

// =============================================================================
// LAYER CONFIGURATION
// =============================================================================

/**
 * Layer configuration
 */
export interface LayerConfig {
  /** Layer identifier */
  id: "freeform" | "graph" | "overlay";
  /** Is layer visible */
  visible: boolean;
  /** Layer opacity */
  opacity: number;
  /** Can layer receive interactions */
  interactive: boolean;
  /** Z-index of layer */
  zIndex: number;
}

// =============================================================================
// CANVAS STATE
// =============================================================================

/**
 * Complete hybrid canvas state
 */
export interface HybridCanvasState {
  /** Current rendering mode */
  mode: RenderingMode;
  /** Active layer for interactions */
  activeLayer: ActiveLayer;
  /** Viewport state */
  viewport: ViewportState;
  /** Selection state */
  selection: SelectionState;
  /** Layer configurations */
  layers: {
    freeform: LayerConfig;
    graph: LayerConfig;
    overlay: LayerConfig;
  };
  /** Freeform elements */
  elements: CanvasElement[];
  /** Graph nodes */
  nodes: CanvasNode[];
  /** Graph edges */
  edges: CanvasEdge[];
  /** Current tool */
  tool: string;
  /** Is canvas read-only */
  readOnly: boolean;
  /** Canvas dimensions */
  dimensions: { width: number; height: number };
  /** Grid settings */
  grid: GridConfig;
}

/**
 * Grid configuration
 */
export interface GridConfig {
  /** Show grid */
  visible: boolean;
  /** Grid cell size */
  size: number;
  /** Snap to grid */
  snap: boolean;
  /** Grid color */
  color: string;
  /** Grid type */
  type: "lines" | "dots" | "none";
}

// =============================================================================
// CANVAS PROPS
// =============================================================================

/**
 * Props for the HybridCanvas component
 */
export interface HybridCanvasProps {
  /** Initial rendering mode */
  mode?: RenderingMode;
  /** Initial elements */
  elements?: CanvasElement[];
  /** Initial nodes */
  nodes?: CanvasNode[];
  /** Initial edges */
  edges?: CanvasEdge[];
  /** Initial viewport */
  viewport?: Partial<ViewportState>;
  /** Grid configuration */
  grid?: Partial<GridConfig>;
  /** Is canvas read-only */
  readOnly?: boolean;
  /** Canvas width */
  width?: number | string;
  /** Canvas height */
  height?: number | string;
  /** Class name for container */
  className?: string;
  /** Custom node types for ReactFlow */
  nodeTypes?: Record<string, ComponentType<unknown>>;
  /** Custom edge types for ReactFlow */
  edgeTypes?: Record<string, ComponentType<unknown>>;
  /** Children (overlays, etc.) */
  children?: ReactNode;

  // Callbacks
  onElementsChange?: (elements: CanvasElement[]) => void;
  onNodesChange?: (nodes: CanvasNode[]) => void;
  onEdgesChange?: (edges: CanvasEdge[]) => void;
  onSelectionChange?: (selection: SelectionState) => void;
  onViewportChange?: (viewport: ViewportState) => void;
  onModeChange?: (mode: RenderingMode) => void;
  onConnect?: (connection: {
    source: string;
    target: string;
    sourceHandle?: string;
    targetHandle?: string;
  }) => void;
  onElementClick?: (element: CanvasElement) => void;
  onNodeClick?: (node: CanvasNode) => void;
  onEdgeClick?: (edge: CanvasEdge) => void;
  onCanvasClick?: (point: Point) => void;
  onDrop?: (event: DragEvent, point: Point) => void;
}

// =============================================================================
// HISTORY
// =============================================================================

/**
 * History entry for undo/redo
 */
export interface HistoryEntry {
  /** Entry timestamp */
  timestamp: number;
  /** Action description */
  action: string;
  /** Previous state snapshot */
  snapshot: {
    elements: CanvasElement[];
    nodes: CanvasNode[];
    edges: CanvasEdge[];
  };
}

/**
 * History state
 */
export interface HistoryState {
  /** Past entries (for undo) */
  past: HistoryEntry[];
  /** Future entries (for redo) */
  future: HistoryEntry[];
  /** Maximum history size */
  maxSize: number;
}

// =============================================================================
// EVENTS
// =============================================================================

/**
 * Canvas event types
 */
export type CanvasEventType =
  | "element:add"
  | "element:update"
  | "element:delete"
  | "element:select"
  | "node:add"
  | "node:update"
  | "node:delete"
  | "node:select"
  | "edge:add"
  | "edge:update"
  | "edge:delete"
  | "edge:select"
  | "viewport:change"
  | "selection:change"
  | "mode:change"
  | "tool:change"
  | "history:push"
  | "history:undo"
  | "history:redo";

/**
 * Canvas event payload
 */
export interface CanvasEvent<T = unknown> {
  type: CanvasEventType;
  payload: T;
  timestamp: number;
}
