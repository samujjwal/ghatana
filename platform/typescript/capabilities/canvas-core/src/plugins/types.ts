/**
 * @ghatana/canvas Plugin Types
 *
 * Defines the plugin system interfaces for extending Canvas functionality.
 * Products use these interfaces to create custom elements, tools, and panels.
 *
 * @doc.type module
 * @doc.purpose Type definitions for plugin system
 * @doc.layer core
 * @doc.pattern PluginArchitecture
 */

import type { ReactNode, ComponentType } from "react";

// =============================================================================
// PLUGIN MANIFEST & METADATA
// =============================================================================

/**
 * Plugin manifest defines metadata and capabilities
 */
export interface PluginManifest {
  /** Unique plugin identifier (e.g., "@ghatana/yappc-canvas-ext", "@aep/canvas-ext") */
  readonly id: string;

  /** Display name */
  readonly name: string;

  /** Semantic version (e.g., "1.0.0") */
  readonly version: string;

  /** Plugin description */
  readonly description: string;

  /** Author information */
  readonly author?: {
    readonly name: string;
    readonly email?: string;
    readonly url?: string;
  };

  /** Minimum required Canvas version */
  readonly minCanvasVersion?: string;

  /** Plugin dependencies (other plugin IDs) */
  readonly dependencies?: readonly string[];

  /** Plugin capabilities */
  readonly capabilities?: readonly PluginCapability[];
}

/**
 * Plugin capabilities define what the plugin can do
 */
export type PluginCapability =
  | "custom-elements" // Register custom element types
  | "custom-tools" // Add custom drawing tools
  | "custom-commands" // Register commands
  | "custom-panels" // Add UI panels
  | "custom-node-types" // ReactFlow custom node types
  | "custom-edge-types" // ReactFlow custom edge types
  | "event-hooks" // Subscribe to Canvas events
  | "state-access"; // Access Canvas state

// =============================================================================
// PLUGIN LIFECYCLE
// =============================================================================

/**
 * Plugin lifecycle states
 */
export type PluginState =
  | "uninitialized"
  | "initializing"
  | "active"
  | "paused"
  | "error"
  | "disabled"
  | "uninstalling";

/**
 * Plugin context provides access to Canvas APIs
 */
export interface PluginContext {
  /** Plugin manifest */
  readonly manifest: PluginManifest;

  /** Current plugin state */
  readonly state: PluginState;

  /** Canvas API access */
  readonly canvas: PluginCanvasAPI;

  /** Event subscription API */
  readonly events: PluginEventAPI;

  /** Logger for debugging */
  readonly logger: PluginLogger;
}

// =============================================================================
// CANVAS PLUGIN INTERFACE
// =============================================================================

/**
 * Main plugin interface that products implement
 */
export interface CanvasPlugin {
  /** Plugin manifest */
  readonly manifest: PluginManifest;

  /** Custom freeform elements (for custom renderer layer) */
  readonly elements?: readonly ElementDefinition[];

  /** Custom graph node types (for ReactFlow layer) */
  readonly nodeTypes?: readonly NodeTypeDefinition[];

  /** Custom graph edge types (for ReactFlow layer) */
  readonly edgeTypes?: readonly EdgeTypeDefinition[];

  /** Custom tools */
  readonly tools?: readonly ToolDefinition[];

  /** Custom panels */
  readonly panels?: readonly PanelDefinition[];

  /** Lifecycle hooks */
  onLoad?(context: PluginContext): void | Promise<void>;
  onActivate?(context: PluginContext): void | Promise<void>;
  onDeactivate?(context: PluginContext): void | Promise<void>;
  onUninstall?(context: PluginContext): void | Promise<void>;
}

// =============================================================================
// ELEMENT DEFINITIONS (Custom Canvas Renderer)
// =============================================================================

/**
 * Definition for a custom freeform element
 */
export interface ElementDefinition {
  /** Element type identifier (e.g., 'lifecycle-phase', 'sketch') */
  readonly type: string;

  /** Display name */
  readonly label: string;

  /** Category for grouping in palette */
  readonly category: string;

  /** Icon component or URL */
  readonly icon?: ComponentType | string;

  /** Default properties */
  readonly defaultProps?: Record<string, unknown>;

  /** Property schema for validation (JSON Schema) */
  readonly schema?: Record<string, unknown>;

  /** Render function for canvas */
  readonly render: ElementRenderer;

  /** Whether element can have connections */
  readonly connectable?: boolean;

  /** Whether element can be resized */
  readonly resizable?: boolean;

  /** Whether element can be rotated */
  readonly rotatable?: boolean;
}

/**
 * Element renderer function
 */
export type ElementRenderer = (
  element: CanvasElementData,
  ctx: CanvasRenderingContext2D,
  zoom: number,
  options?: ElementRenderOptions,
) => void;

export interface ElementRenderOptions {
  readonly selected?: boolean;
  readonly showHandles?: boolean;
  readonly isPreview?: boolean;
}

export interface CanvasElementData {
  readonly id: string;
  readonly type: string;
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
  readonly rotation?: number;
  readonly props: Record<string, unknown>;
}

// =============================================================================
// NODE/EDGE DEFINITIONS (ReactFlow Layer)
// =============================================================================

/**
 * Definition for a custom ReactFlow node type
 */
export interface NodeTypeDefinition {
  /** Node type identifier (e.g., 'pipeline-stage', 'data-source') */
  readonly type: string;

  /** Display name */
  readonly label: string;

  /** Category for grouping in palette */
  readonly category: string;

  /** Icon component or URL */
  readonly icon?: ComponentType | string;

  /** Default node data */
  readonly defaultData?: Record<string, unknown>;

  /** React component for rendering */
  readonly component: ComponentType<NodeComponentProps>;

  /** Port definitions */
  readonly ports?: readonly PortDefinition[];

  /** Validation function for node data */
  readonly validate?: (data: Record<string, unknown>) => boolean;
}

export interface NodeComponentProps {
  readonly id: string;
  readonly data: Record<string, unknown>;
  readonly selected: boolean;
  readonly isConnectable: boolean;
}

export interface PortDefinition {
  readonly id: string;
  readonly type: "input" | "output";
  readonly position: "top" | "bottom" | "left" | "right";
  readonly label?: string;
  readonly maxConnections?: number;
}

/**
 * Definition for a custom ReactFlow edge type
 */
export interface EdgeTypeDefinition {
  /** Edge type identifier (e.g., 'pipeline-edge', 'dependency') */
  readonly type: string;

  /** Display name */
  readonly label: string;

  /** React component for rendering */
  readonly component: ComponentType<EdgeComponentProps>;

  /** Whether edge has arrow */
  readonly animated?: boolean;

  /** Connection rules for allowed source/target node types */
  readonly connectionRules?: {
    readonly allowedSources?: readonly string[];
    readonly allowedTargets?: readonly string[];
  };
}

export interface EdgeComponentProps {
  readonly id: string;
  readonly source: string;
  readonly target: string;
  readonly data?: Record<string, unknown>;
  readonly selected: boolean;
}

// =============================================================================
// TOOL DEFINITIONS
// =============================================================================

/**
 * Definition for a custom tool
 */
export interface ToolDefinition {
  /** Tool identifier */
  readonly id: string;

  /** Display name */
  readonly label: string;

  /** Tool category */
  readonly category?:
    | "select"
    | "draw"
    | "shape"
    | "text"
    | "connector"
    | "other";

  /** Icon component or URL */
  readonly icon?: ComponentType | string;

  /** Keyboard shortcut */
  readonly shortcut?: string;

  /** Cursor style when tool is active */
  readonly cursor?: string;

  /** Tool activation handler */
  readonly onActivate?: (context: ToolContext) => void;

  /** Tool deactivation handler */
  readonly onDeactivate?: (context: ToolContext) => void;

  /** Whether this tool is exclusive (deactivates other tools) */
  readonly exclusive?: boolean;

  /** Mouse/touch event handlers */
  readonly handlers?: ToolHandlers;
}

export interface ToolContext {
  readonly canvas: PluginCanvasAPI;
  readonly viewport: ViewportState;
}

export interface ToolHandlers {
  onPointerDown?(event: CanvasPointerEvent): void;
  onPointerMove?(event: CanvasPointerEvent): void;
  onPointerUp?(event: CanvasPointerEvent): void;
  onKeyDown?(event: KeyboardEvent): void;
  onKeyUp?(event: KeyboardEvent): void;
}

export interface CanvasPointerEvent {
  readonly x: number;
  readonly y: number;
  readonly canvasX: number;
  readonly canvasY: number;
  readonly pressure?: number;
  readonly button: number;
  readonly shiftKey: boolean;
  readonly ctrlKey: boolean;
  readonly altKey: boolean;
}

// =============================================================================
// PANEL DEFINITIONS
// =============================================================================

/**
 * Definition for a custom panel
 */
export interface PanelDefinition {
  /** Panel identifier */
  readonly id: string;

  /** Display name */
  readonly label: string;

  /** Panel position */
  readonly position: "left" | "right" | "bottom" | "top" | "floating";

  /** Icon component or URL */
  readonly icon?: ComponentType | string;

  /** React component for panel content */
  readonly component: ComponentType<PanelComponentProps>;

  /** Default width/height */
  readonly defaultSize?: { width?: number; height?: number };

  /** Whether panel is collapsible */
  readonly collapsible?: boolean;

  /** Display order within position group */
  readonly order?: number;

  /** Whether panel is initially visible */
  readonly initiallyVisible?: boolean;
}

export interface PanelComponentProps {
  readonly canvas: PluginCanvasAPI;
  readonly selection: readonly string[];
}

// =============================================================================
// PLUGIN APIS
// =============================================================================

/**
 * Canvas API available to plugins
 */
export interface PluginCanvasAPI {
  /** Get current document */
  getDocument(): CanvasDocument;

  /** Get current selection */
  getSelection(): readonly string[];

  /** Set selection */
  setSelection(ids: readonly string[]): void;

  /** Add element to freeform layer */
  addElement(element: Omit<CanvasElementData, "id">): string;

  /** Update element */
  updateElement(id: string, updates: Partial<CanvasElementData>): void;

  /** Remove element */
  removeElement(id: string): void;

  /** Add node to graph layer */
  addNode(node: GraphNodeData): string;

  /** Update node */
  updateNode(id: string, updates: Partial<GraphNodeData>): void;

  /** Remove node */
  removeNode(id: string): void;

  /** Add edge to graph layer */
  addEdge(edge: GraphEdgeData): string;

  /** Remove edge */
  removeEdge(id: string): void;

  /** Get viewport state */
  getViewport(): ViewportState;

  /** Set viewport */
  setViewport(viewport: Partial<ViewportState>): void;

  /** Execute undo */
  undo(): void;

  /** Execute redo */
  redo(): void;
}

export interface CanvasDocument {
  readonly id: string;
  readonly title: string;
  readonly elements: readonly CanvasElementData[];
  readonly nodes: readonly GraphNodeData[];
  readonly edges: readonly GraphEdgeData[];
}

export interface GraphNodeData {
  readonly id: string;
  readonly type: string;
  readonly position: { x: number; y: number };
  readonly data: Record<string, unknown>;
}

export interface GraphEdgeData {
  readonly id: string;
  readonly type?: string;
  readonly source: string;
  readonly target: string;
  readonly sourceHandle?: string;
  readonly targetHandle?: string;
  readonly data?: Record<string, unknown>;
}

export interface ViewportState {
  readonly x: number;
  readonly y: number;
  readonly zoom: number;
}

/**
 * Event API for plugins
 */
export interface PluginEventAPI {
  on<T extends CanvasEventType>(
    event: T,
    handler: CanvasEventHandler<T>,
  ): () => void;

  once<T extends CanvasEventType>(
    event: T,
    handler: CanvasEventHandler<T>,
  ): () => void;

  emit<T extends CanvasEventType>(event: T, data: CanvasEventData<T>): void;
}

export type CanvasEventType =
  | "selection:changed"
  | "element:added"
  | "element:updated"
  | "element:removed"
  | "node:added"
  | "node:updated"
  | "node:removed"
  | "edge:added"
  | "edge:removed"
  | "viewport:changed"
  | "history:changed";

export type CanvasEventHandler<T extends CanvasEventType> = (
  data: CanvasEventData<T>,
) => void;

export type CanvasEventData<T extends CanvasEventType> =
  T extends "selection:changed"
    ? { ids: readonly string[] }
    : T extends "element:added"
      ? { element: CanvasElementData }
      : T extends "element:updated"
        ? { id: string; updates: Partial<CanvasElementData> }
        : T extends "element:removed"
          ? { id: string }
          : T extends "node:added"
            ? { node: GraphNodeData }
            : T extends "node:updated"
              ? { id: string; updates: Partial<GraphNodeData> }
              : T extends "node:removed"
                ? { id: string }
                : T extends "edge:added"
                  ? { edge: GraphEdgeData }
                  : T extends "edge:removed"
                    ? { id: string }
                    : T extends "viewport:changed"
                      ? ViewportState
                      : T extends "history:changed"
                        ? { canUndo: boolean; canRedo: boolean }
                        : never;

/**
 * Logger API for plugins
 */
export interface PluginLogger {
  debug(message: string, ...args: unknown[]): void;
  info(message: string, ...args: unknown[]): void;
  warn(message: string, ...args: unknown[]): void;
  error(message: string, ...args: unknown[]): void;
}

// =============================================================================
// KEYBOARD SHORTCUTS & CONTEXT MENU
// =============================================================================

/**
 * Keyboard shortcut definition
 */
export interface KeyboardShortcut {
  /** Shortcut key combination (e.g., "Ctrl+S", "Cmd+Shift+Z") */
  readonly key: string;

  /** Display name */
  readonly name: string;

  /** Handler function */
  readonly handler: (event: KeyboardEvent) => void;

  /** Whether shortcut is enabled */
  readonly enabled?: boolean;
}

/**
 * Context menu item definition
 */
export interface ContextMenuItem {
  /** Item identifier */
  readonly id: string;

  /** Display label */
  readonly label: string;

  /** Icon component or emoji */
  readonly icon?: string;

  /** Target context (e.g., "element", "canvas", "node", "edge") */
  readonly target: "element" | "canvas" | "node" | "edge" | "selection";

  /** Handler function */
  readonly handler: (context: Record<string, unknown>) => void;

  /** Sort order */
  readonly order?: number;

  /** Whether item is a separator */
  readonly separator?: boolean;

  /** Submenu items */
  readonly submenu?: readonly ContextMenuItem[];
}
