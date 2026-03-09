/**
 * Core Types for Ghatana Canvas
 * Provides type definitions for canvas rendering, elements, and interactions
 */

/**
 * Represents a 2D point in canvas space
 */
export interface Point {
  /** X coordinate */
  x: number;
  /** Y coordinate */
  y: number;
}

/**
 * Rectangle bounds with position and size
 */
export interface Bounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

/**
 * Transform properties for positioning elements
 */
export interface Transform {
  position: Point;
  scale: number;
  rotation: number;
}

export type SerializedXYWH = string;

export interface Bound {
  x: number;
  y: number;
  w: number;
  h: number;

  containsPoint(point: Point): boolean;
  expand(padding: number | Point): Bound;
  serialize(): SerializedXYWH;
}

export interface BoundConstructor {
  deserialize(xywh: SerializedXYWH): Bound;
  fromXYWH(x: number, y: number, w: number, h: number): Bound;
}

export interface ElementProps {
  id: string;
  xywh: SerializedXYWH;
  rotate?: number;
}

export interface BaseElementProps extends ElementProps {
  index: string;
}

/**
 * Canvas element types - includes AFFiNE parity types + extended types
 */
export type CanvasElementType =
  | "shape"
  | "text"
  | "connector"
  | "brush"
  | "code"
  | "diagram"
  | "group"
  | "frame"
  | "mindmap"
  | "highlighter"
  | "pipeline-node"
  // NEW: Rich content types (AFFiNE parity)
  | "image"
  | "attachment"
  | "embed"
  | "rich-text"
  | "note"
  | "table"
  | "callout"
  | "list"
  | "bookmark"
  | "divider"
  | "latex";

/**
 * Connectable interface - elements that can have connectors attached
 * Matches AFFiNE's Connectable interface
 */
export interface Connectable {
  /** Whether this element can be connected to */
  connectable: boolean;
}

export interface PointTestOptions {
  useElementBound?: boolean;
  ignoreTransparent?: boolean;
}

export interface ResizeConstraint {
  lockRatio?: boolean;
  minWidth?: number;
  minHeight?: number;
  maxWidth?: number;
  maxHeight?: number;
}

export interface ToolOptions {
  type: string;
  color?: string;
  strokeWidth?: number;
  fill?: boolean;
}

/**
 * Provider for color resolution and theming
 * Allows custom color schemes and fallback handling
 */
export interface ColorProvider {
  /**
   * Resolve a color value with optional fallback
   * @param color - The color to resolve
   * @param fallback - Fallback color if primary is undefined
   * @returns Resolved color string
   */
  getColorValue(color?: string, fallback?: string): string;

  /**
   * Get the current color scheme
   * @returns "light" or "dark"
   */
  getColorScheme?(): "light" | "dark";
}

/**
 * Provider for feature flags
 * Enables/disables optional canvas features
 */
export interface FlagProvider {
  /**
   * Get a feature flag value
   * @param name - Flag name (e.g., 'enableStackingCanvas')
   * @param defaultValue - Default value if flag is not set
   * @returns Flag value
   */
  getFlag(name: string, defaultValue?: boolean): boolean;
}

/**
 * Provider for telemetry and analytics
 * Tracks canvas events and user interactions
 */
export interface TelemetryProvider {
  /**
   * Track an event with optional properties
   * @param event - Event name (e.g., 'canvas.loaded')
   * @param properties - Event metadata
   */
  track(event: string, properties?: Record<string, unknown>): void;
}

/**
 * Configuration options for canvas initialization
 */
export interface CanvasOptions {
  /** Canvas width in pixels */
  width: number;
  /** Canvas height in pixels */
  height: number;
  /** Initial tool configuration */
  tool?: ToolOptions;
  /** Color theme */
  theme?: "light" | "dark";
  /** Optional color provider for custom theming */
  colorProvider?: ColorProvider;
  /** Optional feature flag provider */
  flagProvider?: FlagProvider;
  /** Optional telemetry provider for analytics */
  telemetry?: TelemetryProvider;
  /** Enable multi-layer stacking canvases for performance */
  enableStackingCanvas?: boolean;
  /** Enable DOM-based renderer (experimental) */
  enableDomRenderer?: boolean;
}

// =============================================================================
// CANVAS DOCUMENT TYPES (for product integration)
// =============================================================================

/**
 * Canvas viewport configuration
 */
export interface CanvasViewport {
  center: Point;
  zoom: number;
}

/**
 * Canvas element metadata
 */
export interface CanvasElementMetadata {
  custom?: Record<string, unknown>;
  version?: string;
}

/**
 * Canvas document capabilities
 */
export interface CanvasCapabilities {
  canEdit: boolean;
  canZoom: boolean;
  canPan: boolean;
  canSelect: boolean;
  canUndo: boolean;
  canRedo: boolean;
  canExport: boolean;
  canImport: boolean;
  canCollaborate: boolean;
  canPersist: boolean;
  allowedElementTypes: string[];
}

/**
 * Canvas element base interface (for document storage)
 */
export interface CanvasElement {
  id: string;
  type: string;
  transform: Transform;
  bounds: Bounds;
  visible: boolean;
  locked: boolean;
  selected: boolean;
  zIndex: number;
  metadata: CanvasElementMetadata;
  version: string;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Canvas node element (extends base element)
 */
export interface CanvasNode extends CanvasElement {
  type: "node";
  nodeType: string;
  data: Record<string, unknown>;
  inputs: Array<{ id: string; name: string }>;
  outputs: Array<{ id: string; name: string }>;
  style: Record<string, unknown>;
}

/**
 * Canvas edge element (extends base element)
 */
export interface CanvasEdge extends CanvasElement {
  type: "edge";
  sourceId: string;
  targetId: string;
  path: Point[];
  style: Record<string, unknown>;
}

/**
 * Complete canvas document structure
 */
export interface CanvasDocument {
  version: string;
  id: string;
  title: string;
  viewport: CanvasViewport;
  elements: Record<string, CanvasElement>;
  elementOrder: string[];
  metadata: Record<string, unknown>;
  capabilities: CanvasCapabilities;
  createdAt: Date;
  updatedAt: Date;
}
