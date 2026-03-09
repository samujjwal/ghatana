import { Connection } from '@xyflow/react';

import type {
  CanvasElement,
  CanvasConnection,
} from '../../../components/canvas/workspace/canvasAtoms';
import type { Node, Edge } from '@xyflow/react';

// Node and edge types will be defined inside the component with useMemo
export const KNOWN_NODE_TYPES = new Set(['component', 'api', 'data', 'flow']);

export const toReactFlowNode = (element: CanvasElement): Node => {
  const type = KNOWN_NODE_TYPES.has(element.type as string)
    ? element.type
    : undefined;
  const safeStyle = {
    opacity: 1,
    zIndex: 10,
    ...(element.style || {}),
  } as React.CSSProperties;

  return {
    id: element.id,
    type,
    position: element.position,
    data: element.data,
    style: safeStyle,
    selected: element.selected,
  } as Node;
};

export const toReactFlowEdge = (connection: CanvasConnection): Edge => ({
  id: connection.id,
  source: connection.source,
  target: connection.target,
  sourceHandle:
    connection.sourceHandle == null
      ? undefined
      : connection.sourceHandle,
  targetHandle:
    connection.targetHandle == null
      ? undefined
      : connection.targetHandle,
  type: connection.type || 'default',
  animated: connection.animated,
  style: connection.style,
  data: connection.data,
});

export const fromReactFlowNodes = (nodes: Node[]): CanvasElement[] =>
  nodes.map((node) => ({
    id: node.id,
    kind: (node.type === 'component' ? 'component' : 'node') as
      | 'component'
      | 'node',
    type: node.type || 'default',
    position: node.position,
    data: node.data || {},
    style: node.style,
    selected: node.selected,
    size:
      node.width && node.height
        ? { width: node.width, height: node.height }
        : undefined,
  }));

export const fromReactFlowEdges = (edges: Edge[]): CanvasConnection[] =>
  edges.map((edge) => ({
    id: edge.id,
    source: edge.source,
    target: edge.target,
    sourceHandle:
      edge.sourceHandle == null
        ? undefined
        : edge.sourceHandle || undefined,
    targetHandle:
      edge.targetHandle == null
        ? undefined
        : edge.targetHandle || undefined,
    type: edge.type,
    animated: edge.animated,
    style: edge.style,
    data: edge.data,
  }));

// Normalization helpers to produce stable comparable shapes
export const normalizeNodesForCompare = (nodes: Node[]) =>
  nodes
    .map((n) => ({
      id: n.id,
      type: n.type || 'default',
      position: n.position,
      data: n.data || {},
    }))
    .sort((a, b) => a.id.localeCompare(b.id));

export const normalizeElementsForCompare = (elements: CanvasElement[]) =>
  elements
    .map((e) => ({
      id: e.id,
      type: e.type || 'default',
      position: e.position,
      data: e.data || {},
    }))
    .sort((a, b) => a.id.localeCompare(b.id));

export const normalizeEdgesForCompare = (edges: Edge[]) =>
  edges
    .map((e) => ({
      id: e.id,
      source: e.source,
      target: e.target,
      sourceHandle: e.sourceHandle,
      targetHandle: e.targetHandle,
    }))
    .sort((a, b) => a.id.localeCompare(b.id));

export const normalizeConnectionsForCompare = (conns: CanvasConnection[]) =>
  conns
    .map((c) => ({
      id: c.id,
      source: c.source,
      target: c.target,
      sourceHandle: c.sourceHandle,
      targetHandle: c.targetHandle,
    }))
    .sort((a, b) => a.id.localeCompare(b.id));
