/**
 * Canvas Document Adapters
 *
 * Provides conversion utilities between different canvas state models
 * for backwards compatibility and migration.
 */

import type {
  CanvasDocument,
  CanvasElement,
  CanvasElementMetadata,
  CanvasNode,
  CanvasEdge,
  Point,
  Transform,
  Bounds,
} from './canvas-document';

function metadataToRecord(metadata: CanvasElementMetadata): Record<string, unknown> {
  return {
    ...(metadata.custom ?? {}),
    layer: metadata.layer,
    tags: metadata.tags,
    designerData: metadata.designerData,
    pageBuilderData: metadata.pageBuilderData,
  };
}

// Legacy types from existing codebase (for migration)
/**
 *
 */
export interface LegacyBaseItem {
  id: string;
  type: string;
  data: Record<string, unknown>;
  metadata?: Record<string, unknown>;
  position?: { x: number; y: number };
}

/**
 *
 */
export interface LegacyCanvasElement {
  id: string;
  kind?: string;
  type?: string;
  position: { x: number; y: number };
  data?: Record<string, unknown>;
}

/**
 *
 */
export interface LegacyReactFlowNode {
  id: string;
  type: string;
  position: { x: number; y: number };
  data: Record<string, unknown>;
  width?: number;
  height?: number;
  selected?: boolean;
  dragging?: boolean;
}

/**
 *
 */
export interface LegacyReactFlowEdge {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string;
  targetHandle?: string;
  type?: string;
  data?: Record<string, unknown>;
  selected?: boolean;
}

/**
 * Convert legacy BaseItem to CanvasElement
 */
export const convertBaseItemToCanvasElement = (
  item: LegacyBaseItem
): CanvasElement => {
  const now = new Date();
  const position: Point = item.position || { x: 0, y: 0 };
  const transform: Transform = {
    position,
    scale: 1,
    rotation: 0,
  };
  const bounds: Bounds = {
    x: position.x,
    y: position.y,
    width: 100, // Default width
    height: 50, // Default height
  };

  return {
    id: item.id,
    type: item.type === 'node' ? 'node' : 'custom',
    transform,
    bounds,
    visible: true,
    locked: false,
    selected: false,
    zIndex: 0,
    metadata: item.metadata || {},
    version: '1.0.0',
    createdAt: now,
    updatedAt: now,
  };
};

/**
 * Convert CanvasElement to legacy BaseItem format
 */
export const convertCanvasElementToBaseItem = (
  element: CanvasElement
): LegacyBaseItem => {
  const metadataRecord = metadataToRecord(element.metadata);

  return {
    id: element.id,
    type: element.type,
    data: metadataRecord,
    metadata: metadataRecord,
    position: element.transform.position,
  };
};

/**
 * Convert legacy ReactFlow node to CanvasNode
 */
export const convertReactFlowNodeToCanvasNode = (
  node: LegacyReactFlowNode
): CanvasNode => {
  const now = new Date();
  const transform: Transform = {
    position: node.position,
    scale: 1,
    rotation: 0,
  };
  const bounds: Bounds = {
    x: node.position.x,
    y: node.position.y,
    width: node.width || 100,
    height: node.height || 50,
  };

  return {
    id: node.id,
    type: 'node',
    nodeType: node.type,
    transform,
    bounds,
    visible: true,
    locked: false,
    selected: node.selected || false,
    zIndex: 0,
    metadata: {},
    version: '1.0.0',
    createdAt: now,
    updatedAt: now,
    data: node.data,
    inputs: [],
    outputs: [],
    style: {},
  };
};

/**
 * Convert CanvasNode to ReactFlow node format
 */
export const convertCanvasNodeToReactFlowNode = (
  node: CanvasNode
): LegacyReactFlowNode => ({
  id: node.id,
  type: node.nodeType,
  position: node.transform.position,
  data: node.data,
  width: node.bounds.width,
  height: node.bounds.height,
  selected: node.selected,
  dragging: false,
});

/**
 * Convert legacy ReactFlow edge to CanvasEdge
 */
export const convertReactFlowEdgeToCanvasEdge = (
  edge: LegacyReactFlowEdge
): CanvasEdge => {
  const now = new Date();
  const transform: Transform = {
    position: { x: 0, y: 0 },
    scale: 1,
    rotation: 0,
  };
  const bounds: Bounds = {
    x: 0,
    y: 0,
    width: 0,
    height: 0,
  };

  return {
    id: edge.id,
    type: 'edge',
    sourceId: edge.source,
    targetId: edge.target,
    sourceHandle: edge.sourceHandle,
    targetHandle: edge.targetHandle,
    transform,
    bounds,
    visible: true,
    locked: false,
    selected: edge.selected || false,
    zIndex: -1,
    metadata: edge.data || {},
    version: '1.0.0',
    createdAt: now,
    updatedAt: now,
    path: [],
    style: {},
  };
};

/**
 * Convert CanvasEdge to ReactFlow edge format
 */
export const convertCanvasEdgeToReactFlowEdge = (
  edge: CanvasEdge
): LegacyReactFlowEdge => ({
  id: edge.id,
  source: edge.sourceId,
  target: edge.targetId,
  sourceHandle: edge.sourceHandle,
  targetHandle: edge.targetHandle,
  type: 'default',
  data: metadataToRecord(edge.metadata),
  selected: edge.selected,
});

/**
 * Migrate legacy persistence data to CanvasDocument format
 */
export const migrateLegacyPersistence = (payload: unknown): CanvasDocument => {
  // Handle different legacy formats
  if (isLegacyReactFlowFormat(payload)) {
    return convertLegacyReactFlowData(payload);
  }

  if (isLegacyCanvasFormat(payload)) {
    return convertLegacyCanvasData(payload);
  }

  // If it's already in the new format, validate and return
  if (isCanvasDocument(payload)) {
    return payload;
  }

  // Fallback to empty document
  return {
    version: '1.0.0',
    id: crypto.randomUUID(),
    title: 'Migrated Canvas',
    viewport: {
      center: { x: 0, y: 0 },
      zoom: 1,
    },
    elements: {},
    elementOrder: [],
    metadata: { migrated: true, originalFormat: 'unknown' },
    capabilities: {
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
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
};

// Type guards for legacy format detection
/**
 *
 */
function isLegacyReactFlowFormat(payload: unknown): payload is {
  nodes: LegacyReactFlowNode[];
  edges: LegacyReactFlowEdge[];
  viewport?: { x: number; y: number; zoom: number };
} {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'nodes' in payload &&
    'edges' in payload &&
    Array.isArray((payload as unknown).nodes) &&
    Array.isArray((payload as unknown).edges)
  );
}

/**
 *
 */
function isLegacyCanvasFormat(payload: unknown): payload is {
  elements: LegacyCanvasElement[];
  connections?: unknown[];
} {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'elements' in payload &&
    Array.isArray((payload as unknown).elements)
  );
}

/**
 *
 */
function isCanvasDocument(payload: unknown): payload is CanvasDocument {
  return (
    typeof payload === 'object' &&
    payload !== null &&
    'version' in payload &&
    'id' in payload &&
    'elements' in payload &&
    'viewport' in payload
  );
}

/**
 *
 */
function convertLegacyReactFlowData(data: {
  nodes: LegacyReactFlowNode[];
  edges: LegacyReactFlowEdge[];
  viewport?: { x: number; y: number; zoom: number };
}): CanvasDocument {
  const elements: Record<string, CanvasElement> = {};
  const elementOrder: string[] = [];

  // Convert nodes
  data.nodes.forEach((node) => {
    const canvasNode = convertReactFlowNodeToCanvasNode(node);
    elements[canvasNode.id] = canvasNode;
    elementOrder.push(canvasNode.id);
  });

  // Convert edges
  data.edges.forEach((edge) => {
    const canvasEdge = convertReactFlowEdgeToCanvasEdge(edge);
    elements[canvasEdge.id] = canvasEdge;
    elementOrder.push(canvasEdge.id);
  });

  return {
    version: '1.0.0',
    id: crypto.randomUUID(),
    title: 'Migrated ReactFlow Canvas',
    viewport: data.viewport
      ? {
          center: { x: data.viewport.x, y: data.viewport.y },
          zoom: data.viewport.zoom,
        }
      : { center: { x: 0, y: 0 }, zoom: 1 },
    elements,
    elementOrder,
    metadata: { migrated: true, originalFormat: 'reactflow' },
    capabilities: {
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
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}

/**
 *
 */
function convertLegacyCanvasData(data: {
  elements: LegacyCanvasElement[];
}): CanvasDocument {
  const elements: Record<string, CanvasElement> = {};
  const elementOrder: string[] = [];

  data.elements.forEach((legacyElement) => {
    const baseItem: LegacyBaseItem = {
      id: legacyElement.id,
      type: legacyElement.kind || legacyElement.type || 'custom',
      data: legacyElement.data || {},
      position: legacyElement.position,
    };

    const canvasElement = convertBaseItemToCanvasElement(baseItem);
    elements[canvasElement.id] = canvasElement;
    elementOrder.push(canvasElement.id);
  });

  return {
    version: '1.0.0',
    id: crypto.randomUUID(),
    title: 'Migrated Canvas',
    viewport: {
      center: { x: 0, y: 0 },
      zoom: 1,
    },
    elements,
    elementOrder,
    metadata: { migrated: true, originalFormat: 'legacy-canvas' },
    capabilities: {
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
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };
}
