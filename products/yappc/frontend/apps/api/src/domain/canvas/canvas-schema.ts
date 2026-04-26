/**
 * Unified Canvas Schema
 *
 * Canonical canvas data model for consistent representation across
 * REST API, WebSocket real-time, and client-side state.
 *
 * @doc.type module
 * @doc.purpose Canonical canvas schema definitions
 * @doc.layer domain
 * @doc.pattern Schema/Contract
 */

// ============================================================================
// Core Types
// ============================================================================

/**
 * Unique identifier for canvas elements
 */
export type CanvasElementId = string & { readonly brand: unique symbol };

/**
 * Canvas element kinds
 */
export type CanvasElementKind = 'node' | 'edge' | 'group' | 'layer';

/**
 * Canvas element types
 */
export type CanvasElementType =
  | 'component'
  | 'service'
  | 'database'
  | 'endpoint'
  | 'queue'
  | 'cache'
  | 'ui'
  | 'function'
  | 'note'
  | 'shape';

/**
 * 2D Position
 */
export interface CanvasPosition {
  x: number;
  y: number;
}

/**
 * 2D Size
 */
export interface CanvasSize {
  width: number;
  height: number;
}

/**
 * Viewport state
 */
export interface CanvasViewport {
  x: number;
  y: number;
  zoom: number;
}

// ============================================================================
// Element Definitions
// ============================================================================

/**
 * Base properties for all canvas elements
 */
export interface CanvasElementBase {
  id: string;
  kind: CanvasElementKind;
  type: CanvasElementType;
  position: CanvasPosition;
  size?: CanvasSize;
  data?: Record<string, unknown>;
  style?: Record<string, unknown>;
  selected?: boolean;
  locked?: boolean;
  visible?: boolean;
  metadata?: Record<string, unknown>;
}

/**
 * Canvas node element
 */
export interface CanvasNode extends CanvasElementBase {
  kind: 'node';
  label?: string;
  description?: string;
}

/**
 * Canvas edge/connection element
 */
export interface CanvasEdge extends Omit<CanvasElementBase, 'position' | 'size'> {
  kind: 'edge';
  source: string;
  target: string;
  sourceHandle?: string;
  targetHandle?: string;
  label?: string;
  animated?: boolean;
  style?: Record<string, unknown>;
}

/**
 * Canvas group element
 */
export interface CanvasGroup extends CanvasElementBase {
  kind: 'group';
  children: string[];
  label?: string;
  collapsed?: boolean;
}

/**
 * Canvas layer element
 */
export interface CanvasLayer {
  id: string;
  name: string;
  visible: boolean;
  locked: boolean;
  elements: string[];
  order: number;
  metadata?: Record<string, unknown>;
}

/**
 * Union type for all canvas elements
 */
export type CanvasElement = CanvasNode | CanvasEdge | CanvasGroup;

// ============================================================================
// Canvas State
// ============================================================================

/**
 * Canonical canvas state structure
 * Used for REST API, WebSocket, and persistence
 */
export interface CanvasState {
  projectId: string;
  canvasId: string;
  nodes: CanvasNode[];
  connections: CanvasEdge[];
  groups: CanvasGroup[];
  layers: CanvasLayer[];
  viewport: CanvasViewport;
  lastSaved?: string | null;
  metadata?: Record<string, unknown>;
}

/**
 * Empty canvas state factory
 */
export function createEmptyCanvasState(
  projectId: string,
  canvasId: string = 'unified-canvas'
): CanvasState {
  return {
    projectId,
    canvasId,
    nodes: [],
    connections: [],
    groups: [],
    layers: [
      {
        id: 'default-layer',
        name: 'Default',
        visible: true,
        locked: false,
        elements: [],
        order: 0,
      },
    ],
    viewport: { x: 0, y: 0, zoom: 1 },
    lastSaved: null,
  };
}

// ============================================================================
// Snapshot & Versioning
// ============================================================================

/**
 * Canvas snapshot for versioning
 */
export interface CanvasSnapshot {
  id: string;
  projectId: string;
  canvasId: string;
  version: number;
  timestamp: number;
  data: CanvasState;
  checksum: string;
  label?: string;
  description?: string;
  author?: string;
  tags?: string[];
  parentSnapshotId?: string;
}

/**
 * Version change metadata
 */
export type ChangeType = 'MANUAL_SAVE' | 'AUTO_SAVE' | 'RESTORE' | 'MERGE' | 'REALTIME';

/**
 * Version history entry
 */
export interface VersionHistoryEntry {
  versionId: string;
  version: number;
  timestamp: string;
  author?: string;
  changeType: ChangeType;
  changeSummary?: string;
  snapshotId: string;
}

// ============================================================================
// API Contracts
// ============================================================================

/**
 * Save canvas request body
 */
export interface SaveCanvasRequest {
  projectId: string;
  canvasId: string;
  data: CanvasState;
  changeType?: ChangeType;
  changeSummary?: string;
  parentVersion?: number;
}

/**
 * Save canvas response
 */
export interface SaveCanvasResponse {
  success: boolean;
  projectId: string;
  canvasId: string;
  version: number;
  timestamp: string;
  snapshotId?: string;
}

/**
 * Load canvas response
 */
export interface LoadCanvasResponse extends CanvasState {
  lastSaved: string | null;
}

/**
 * Real-time canvas update (WebSocket)
 */
export interface RealtimeCanvasUpdate {
  type: 'node-add' | 'node-update' | 'node-delete' | 'edge-add' | 'edge-update' | 'edge-delete' | 'viewport-update';
  projectId: string;
  canvasId: string;
  elementId?: string;
  data?: unknown;
  timestamp: number;
  author: string;
  version?: number;
}

// ============================================================================
// Validation
// ============================================================================

/**
 * Validation result
 */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
}

export interface ValidationError {
  path: string;
  message: string;
  code: string;
}

export interface ValidationWarning {
  path: string;
  message: string;
  suggestion?: string;
}

/**
 * Validate canvas state structure
 */
export function validateCanvasState(state: unknown): ValidationResult {
  const errors: ValidationError[] = [];
  const warnings: ValidationWarning[] = [];

  if (!state || typeof state !== 'object') {
    errors.push({
      path: '',
      message: 'Canvas state must be an object',
      code: 'INVALID_TYPE',
    });
    return { valid: false, errors, warnings };
  }

  const s = state as Record<string, unknown>;

  // Check required fields
  if (!s.projectId || typeof s.projectId !== 'string') {
    errors.push({
      path: 'projectId',
      message: 'projectId is required and must be a string',
      code: 'MISSING_PROJECT_ID',
    });
  }

  if (!Array.isArray(s.nodes)) {
    errors.push({
      path: 'nodes',
      message: 'nodes must be an array',
      code: 'INVALID_NODES',
    });
  }

  if (!Array.isArray(s.connections)) {
    errors.push({
      path: 'connections',
      message: 'connections must be an array',
      code: 'INVALID_CONNECTIONS',
    });
  }

  // Check viewport
  if (!s.viewport || typeof s.viewport !== 'object') {
    errors.push({
      path: 'viewport',
      message: 'viewport is required',
      code: 'MISSING_VIEWPORT',
    });
  } else {
    const vp = s.viewport as Record<string, unknown>;
    if (typeof vp.x !== 'number' || typeof vp.y !== 'number' || typeof vp.zoom !== 'number') {
      errors.push({
        path: 'viewport',
        message: 'viewport must have numeric x, y, and zoom',
        code: 'INVALID_VIEWPORT',
      });
    }
  }

  // Warn about empty canvas
  if (Array.isArray(s.nodes) && s.nodes.length === 0) {
    warnings.push({
      path: 'nodes',
      message: 'Canvas has no nodes',
      suggestion: 'Add elements to the canvas',
    });
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
  };
}

// ============================================================================
// Migration Utilities
// ============================================================================

/**
 * Migrate legacy canvas state to canonical format
 */
export function migrateToCanonicalState(legacy: unknown): CanvasState | null {
  if (!legacy || typeof legacy !== 'object') {
    return null;
  }

  const l = legacy as Record<string, unknown>;

  // Extract nodes (handle various legacy formats)
  const nodes: CanvasNode[] = [];
  if (Array.isArray(l.nodes)) {
    nodes.push(...l.nodes.map((n: unknown) => migrateNode(n)));
  } else if (Array.isArray(l.elements)) {
    nodes.push(...l.elements.map((e: unknown) => migrateNode(e)));
  }

  // Extract connections
  const connections: CanvasEdge[] = [];
  if (Array.isArray(l.connections)) {
    connections.push(...l.connections.map((c: unknown) => migrateEdge(c)));
  } else if (Array.isArray(l.edges)) {
    connections.push(...l.edges.map((e: unknown) => migrateEdge(e)));
  }

  // Extract viewport
  const viewport = migrateViewport(l.viewport);

  return {
    projectId: (l.projectId as string) || 'unknown',
    canvasId: (l.canvasId as string) || 'unified-canvas',
    nodes,
    connections,
    groups: [],
    layers: [],
    viewport,
    lastSaved: (l.lastSaved as string) || null,
  };
}

function migrateNode(node: unknown): CanvasNode {
  const n = (node || {}) as Record<string, unknown>;
  return {
    id: (n.id as string) || `node-${Date.now()}`,
    kind: 'node',
    type: (n.type as CanvasElementType) || 'component',
    position: {
      x: Number(n.x) || 0,
      y: Number(n.y) || 0,
    },
    size: n.width && n.height
      ? { width: Number(n.width), height: Number(n.height) }
      : undefined,
    data: (n.data as Record<string, unknown>) || {},
    label: (n.label as string) || (n.name as string),
  };
}

function migrateEdge(edge: unknown): CanvasEdge {
  const e = (edge || {}) as Record<string, unknown>;
  return {
    id: (e.id as string) || `edge-${Date.now()}`,
    kind: 'edge',
    type: 'component',
    source: (e.source as string) || '',
    target: (e.target as string) || '',
    sourceHandle: e.sourceHandle as string | undefined,
    targetHandle: e.targetHandle as string | undefined,
    label: e.label as string | undefined,
    data: (e.data as Record<string, unknown>) || {},
  };
}

function migrateViewport(vp: unknown): CanvasViewport {
  const v = (vp || {}) as Record<string, unknown>;
  return {
    x: Number(v.x) || 0,
    y: Number(v.y) || 0,
    zoom: Number(v.zoom) || 1,
  };
}
