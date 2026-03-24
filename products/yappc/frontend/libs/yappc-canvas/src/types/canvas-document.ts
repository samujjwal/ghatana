/**
 * Core Canvas Types and Document Model
 *
 * Defines the unified data model for all canvas operations.
 * Follows versioned schema for backwards compatibility.
 */

// Core document schema version
export const CANVAS_DOCUMENT_VERSION = '1.0.0';

// Base geometry and positioning
/**
 *
 */
export interface Point {
  readonly x: number;
  readonly y: number;
}

/**
 *
 */
export interface Dimensions {
  readonly width: number;
  readonly height: number;
}

/**
 *
 */
export interface Bounds extends Point, Dimensions {}

// Element positioning and transforms
/**
 *
 */
export interface Transform {
  readonly position: Point;
  readonly scale: number;
  readonly rotation: number;
}

// Core element types
/**
 *
 */
export type CanvasElementType =
  | 'node'
  | 'edge'
  | 'group'
  | 'annotation'
  | 'background'
  | 'custom';

// Base element - common properties for all canvas items
/**
 *
 */
/**
 * Layer identifier for hierarchical canvas organization
 */
export type CanvasLayer = 'architecture' | 'design' | 'component' | 'page' | 'custom';

/**
 * Designer-specific element data (for @ghatana/yappc-designer compatibility)
 */
export interface DesignerElementData {
  readonly paletteItemId?: string;
  readonly componentType?: string;
  readonly props?: Record<string, unknown>;
}

/**
 * PageBuilder trait definition for component properties
 * Supports 7 built-in trait types with validation and options
 */
export interface PageBuilderTrait {
  readonly type: 'text' | 'number' | 'select' | 'checkbox' | 'color' | 'button' | 'content';
  readonly name: string;
  readonly label: string;
  readonly value: unknown;
  readonly placeholder?: string;
  readonly min?: number;
  readonly max?: number;
  readonly step?: number;
  readonly valueTrue?: unknown;
  readonly valueFalse?: unknown;
  readonly options?: readonly { id: string; name: string }[];
  readonly command?: string;
}

/**
 * PageBuilder-specific element data (for @ghatana/yappc-page-builder)
 * Stores HTML-like structure with traits and styling
 */
export interface PageBuilderElementData {
  readonly tagName?: string;
  readonly classes?: readonly string[];
  readonly attributes?: Record<string, string>;
  readonly styles?: Record<string, string>;
  readonly content?: string;
  readonly components?: readonly string[];
  readonly traits?: readonly PageBuilderTrait[];
}

/**
 * Unified metadata for canvas elements supporting multiple layers
 * Allows elements to carry layer-specific data while maintaining compatibility
 */
export interface CanvasElementMetadata {
  readonly layer?: CanvasLayer;
  readonly tags?: readonly string[];
  readonly custom?: Record<string, unknown>;
  readonly designerData?: DesignerElementData;
  readonly pageBuilderData?: PageBuilderElementData;
}

/**
 * Base canvas element with unified metadata supporting all layers
 * Extensible type allows custom element types beyond core types
 */
export interface CanvasElement {
  readonly id: string;
  readonly type: CanvasElementType | string;
  readonly transform: Transform;
  readonly bounds: Bounds;
  readonly visible: boolean;
  readonly locked: boolean;
  readonly selected: boolean;
  readonly zIndex: number;
  readonly metadata: CanvasElementMetadata;
  readonly version: string;
  readonly createdAt: Date;
  readonly updatedAt: Date;
}

// Specialized element types
/**
 *
 */
export interface CanvasNode extends CanvasElement {
  readonly type: 'node';
  readonly nodeType: string;
  readonly data: Record<string, unknown>;
  readonly inputs: readonly string[];
  readonly outputs: readonly string[];
  readonly style: Record<string, unknown>;
}

/**
 *
 */
export interface CanvasEdge extends CanvasElement {
  readonly type: 'edge';
  readonly sourceId: string;
  readonly targetId: string;
  readonly sourceHandle?: string;
  readonly targetHandle?: string;
  readonly path: readonly Point[];
  readonly style: Record<string, unknown>;
}

/**
 *
 */
export interface CanvasGroup extends CanvasElement {
  readonly type: 'group';
  readonly childIds: readonly string[];
  readonly collapsed: boolean;
  readonly style: Record<string, unknown>;
}

// Document-level model
/**
 *
 */
export interface CanvasDocument {
  readonly version: string;
  readonly id: string;
  readonly title: string;
  readonly description?: string;
  readonly viewport: {
    readonly center: Point;
    readonly zoom: number;
  };
  readonly elements: Record<string, CanvasElement>;
  readonly elementOrder: readonly string[];
  readonly metadata: Record<string, unknown>;
  readonly capabilities: CanvasCapabilities;
  readonly createdAt: Date;
  readonly updatedAt: Date;
}

// Feature capabilities - controls what UI/interactions are enabled
/**
 *
 */
export interface CanvasCapabilities {
  readonly canEdit: boolean;
  readonly canZoom: boolean;
  readonly canPan: boolean;
  readonly canSelect: boolean;
  readonly canUndo: boolean;
  readonly canRedo: boolean;
  readonly canExport: boolean;
  readonly canImport: boolean;
  readonly canCollaborate: boolean;
  readonly canPersist: boolean;
  readonly maxElements?: number;
  readonly allowedElementTypes: readonly CanvasElementType[];
}

// Interaction and selection state
/**
 *
 */
export interface CanvasSelection {
  readonly selectedIds: readonly string[];
  readonly focusedId?: string;
  readonly hoveredId?: string;
}

// Viewport and camera state
/**
 *
 */
export interface CanvasViewport {
  readonly center: Point;
  readonly zoom: number;
  readonly bounds: Bounds;
}

// History and undo/redo state
/**
 *
 */
export interface CanvasHistoryEntry {
  readonly id: string;
  readonly timestamp: Date;
  readonly action: string;
  readonly elementIds: readonly string[];
  readonly beforeState: Partial<CanvasDocument>;
  readonly afterState: Partial<CanvasDocument>;
}

// Event types for canvas interactions
/**
 *
 */
export type CanvasEventType =
  | 'element:created'
  | 'element:updated'
  | 'element:deleted'
  | 'element:selected'
  | 'element:deselected'
  | 'viewport:changed'
  | 'document:saved'
  | 'document:loaded'
  | 'collaboration:user-joined'
  | 'collaboration:user-left'
  | 'error:occurred';

/**
 *
 */
export interface CanvasEvent<T = unknown> {
  readonly type: CanvasEventType;
  readonly timestamp: Date;
  readonly elementId?: string;
  readonly data: T;
  readonly userId?: string;
}

// UI interaction state
/**
 *
 */
export interface CanvasUIState {
  readonly isDragging: boolean;
  readonly isSelecting: boolean;
  readonly isPanning: boolean;
  readonly isLoading: boolean;
  readonly error?: string;
  readonly mode: 'select' | 'pan' | 'draw' | 'edit';
}

// Performance tracking metrics
/**
 *
 */
export interface CanvasPerformanceMetrics {
  readonly renderTime: number;
  readonly fps: number;
  readonly lastUpdate: Date;
}

// Theme and styling
/**
 *
 */
export interface CanvasTheme {
  readonly colors: {
    readonly background: string;
    readonly grid: string;
    readonly selection: string;
    readonly hover: string;
    readonly focus: string;
    readonly error: string;
    readonly success: string;
    readonly warning: string;
  };
  readonly spacing: {
    readonly xs: number;
    readonly sm: number;
    readonly md: number;
    readonly lg: number;
    readonly xl: number;
  };
  readonly borderRadius: {
    readonly sm: number;
    readonly md: number;
    readonly lg: number;
  };
  readonly shadows: {
    readonly sm: string;
    readonly md: string;
    readonly lg: string;
  };
  readonly typography: {
    readonly fontFamily: string;
    readonly fontSize: {
      readonly xs: number;
      readonly sm: number;
      readonly md: number;
      readonly lg: number;
      readonly xl: number;
    };
    readonly fontWeight: {
      readonly normal: number;
      readonly medium: number;
      readonly bold: number;
    };
  };
}

// Type guards
export const isCanvasNode = (element: CanvasElement): element is CanvasNode =>
  element.type === 'node';

export const isCanvasEdge = (element: CanvasElement): element is CanvasEdge =>
  element.type === 'edge';

export const isCanvasGroup = (element: CanvasElement): element is CanvasGroup =>
  element.type === 'group';

// Default values and factories
export const createDefaultCapabilities = (): CanvasCapabilities => ({
  canEdit: true,
  canZoom: true,
  canPan: true,
  canSelect: true,
  canUndo: true,
  canRedo: true,
  canExport: true,
  canImport: true,
  canCollaborate: false,
  canPersist: true,
  allowedElementTypes: ['node', 'edge', 'group', 'annotation'],
});

export const createDefaultViewport = (): CanvasViewport => ({
  center: { x: 0, y: 0 },
  zoom: 1,
  bounds: { x: -1000, y: -1000, width: 2000, height: 2000 },
});

export const createDefaultDocument = (
  id: string = crypto.randomUUID(),
  title: string = 'Untitled Canvas'
): CanvasDocument => ({
  version: CANVAS_DOCUMENT_VERSION,
  id,
  title,
  viewport: {
    center: { x: 0, y: 0 },
    zoom: 1,
  },
  elements: {},
  elementOrder: [],
  metadata: {},
  capabilities: createDefaultCapabilities(),
  createdAt: new Date(),
  updatedAt: new Date(),
});
