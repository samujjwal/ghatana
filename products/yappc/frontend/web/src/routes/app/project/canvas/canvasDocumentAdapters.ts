export interface CanvasPoint {
  x: number;
  y: number;
}

export interface CanvasTransform {
  position: CanvasPoint;
  scale: number;
  rotation: number;
}

export interface CanvasBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface CanvasElementMetadata {
  layer?: string;
  tags?: readonly string[];
  custom?: Record<string, unknown>;
  designerData?: Record<string, unknown>;
  pageBuilderData?: Record<string, unknown>;
}

export interface CanvasElement {
  id: string;
  type: string;
  transform: CanvasTransform;
  bounds: CanvasBounds;
  visible: boolean;
  locked: boolean;
  selected: boolean;
  zIndex: number;
  metadata: CanvasElementMetadata;
  version: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface CanvasNode extends CanvasElement {
  type: 'node';
  nodeType: string;
  data: Record<string, unknown>;
  inputs: readonly string[];
  outputs: readonly string[];
  style: Record<string, unknown>;
}

export interface CanvasEdge extends CanvasElement {
  type: 'edge';
  sourceId: string;
  targetId: string;
  sourceHandle?: string;
  targetHandle?: string;
  path: readonly CanvasPoint[];
  style: Record<string, unknown>;
}

interface ReactFlowNodeLike {
  id: string;
  type?: string;
  position: CanvasPoint;
  data?: Record<string, unknown>;
  width?: number;
  height?: number;
  selected?: boolean;
}

interface ReactFlowEdgeLike {
  id: string;
  source: string;
  target: string;
  sourceHandle?: string | null;
  targetHandle?: string | null;
  data?: Record<string, unknown>;
  selected?: boolean;
}

function metadataToRecord(
  metadata: CanvasElementMetadata
): Record<string, unknown> {
  return {
    ...(metadata.custom ?? {}),
    layer: metadata.layer,
    tags: metadata.tags,
    designerData: metadata.designerData,
    pageBuilderData: metadata.pageBuilderData,
  };
}

export function isCanvasNode(element: CanvasElement): element is CanvasNode {
  return element.type === 'node';
}

export function isCanvasEdge(element: CanvasElement): element is CanvasEdge {
  return element.type === 'edge';
}

export function convertReactFlowNodeToCanvasNode(
  node: ReactFlowNodeLike
): CanvasNode {
  const now = new Date();

  return {
    id: node.id,
    type: 'node',
    nodeType: node.type ?? 'default',
    transform: {
      position: node.position,
      scale: 1,
      rotation: 0,
    },
    bounds: {
      x: node.position.x,
      y: node.position.y,
      width: node.width ?? 100,
      height: node.height ?? 50,
    },
    visible: true,
    locked: false,
    selected: node.selected ?? false,
    zIndex: 0,
    metadata: {},
    version: '1.0.0',
    createdAt: now,
    updatedAt: now,
    data: node.data ?? {},
    inputs: [],
    outputs: [],
    style: {},
  };
}

export function convertCanvasNodeToReactFlowNode(node: CanvasNode) {
  return {
    id: node.id,
    type: node.nodeType,
    position: node.transform.position,
    data: node.data,
    width: node.bounds.width,
    height: node.bounds.height,
    selected: node.selected,
    dragging: false,
  };
}

export function convertReactFlowEdgeToCanvasEdge(
  edge: ReactFlowEdgeLike
): CanvasEdge {
  const now = new Date();

  return {
    id: edge.id,
    type: 'edge',
    sourceId: edge.source,
    targetId: edge.target,
    sourceHandle: edge.sourceHandle ?? undefined,
    targetHandle: edge.targetHandle ?? undefined,
    transform: {
      position: { x: 0, y: 0 },
      scale: 1,
      rotation: 0,
    },
    bounds: {
      x: 0,
      y: 0,
      width: 0,
      height: 0,
    },
    visible: true,
    locked: false,
    selected: edge.selected ?? false,
    zIndex: -1,
    metadata: edge.data ?? {},
    version: '1.0.0',
    createdAt: now,
    updatedAt: now,
    path: [],
    style: {},
  };
}

export function convertCanvasEdgeToReactFlowEdge(edge: CanvasEdge) {
  return {
    id: edge.id,
    source: edge.sourceId,
    target: edge.targetId,
    sourceHandle: edge.sourceHandle,
    targetHandle: edge.targetHandle,
    type: 'default',
    data: metadataToRecord(edge.metadata),
    selected: edge.selected,
  };
}