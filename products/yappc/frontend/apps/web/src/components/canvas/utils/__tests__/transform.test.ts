import { describe, test, expect } from 'vitest';

import {
  toReactFlowNode,
  toReactFlowEdge,
  fromReactFlowNodes,
  fromReactFlowEdges,
  normalizeNodesForCompare,
  normalizeElementsForCompare,
} from './transform';

import type {
  CanvasElement,
  CanvasConnection,
} from '../../../components/canvas/workspace/canvasAtoms';
import type { Node, Edge } from '@xyflow/react';

describe('transform utilities', () => {
  describe('toReactFlowNode', () => {
    test('should convert CanvasElement to React Flow Node', () => {
      const element: CanvasElement = {
        id: 'test-1',
        kind: 'component',
        type: 'component',
        position: { x: 100, y: 200 },
        data: { label: 'Test Component' },
        selected: true,
      };

      const node = toReactFlowNode(element);

      expect(node.id).toBe('test-1');
      expect(node.type).toBe('component');
      expect(node.position).toEqual({ x: 100, y: 200 });
      expect(node.data).toEqual({ label: 'Test Component' });
      expect(node.selected).toBe(true);
    });

    test('should handle unknown node types', () => {
      const element: CanvasElement = {
        id: 'test-2',
        kind: 'node',
        type: 'unknown-type',
        position: { x: 0, y: 0 },
        data: {},
      };

      const node = toReactFlowNode(element);

      expect(node.type).toBeUndefined();
    });

    test('should apply default style properties', () => {
      const element: CanvasElement = {
        id: 'test-3',
        kind: 'component',
        type: 'component',
        position: { x: 0, y: 0 },
        data: {},
        style: { backgroundColor: 'red' },
      };

      const node = toReactFlowNode(element);

      expect(node.style).toHaveProperty('opacity', 1);
      expect(node.style).toHaveProperty('zIndex', 10);
      expect(node.style).toHaveProperty('backgroundColor', 'red');
    });
  });

  describe('toReactFlowEdge', () => {
    test('should convert CanvasConnection to React Flow Edge', () => {
      const connection: CanvasConnection = {
        id: 'edge-1',
        source: 'node-1',
        target: 'node-2',
        sourceHandle: 'right',
        targetHandle: 'left',
        animated: true,
      };

      const edge = toReactFlowEdge(connection);

      expect(edge.id).toBe('edge-1');
      expect(edge.source).toBe('node-1');
      expect(edge.target).toBe('node-2');
      expect(edge.sourceHandle).toBe('right');
      expect(edge.targetHandle).toBe('left');
      expect(edge.animated).toBe(true);
    });

    test('should handle undefined handles', () => {
      const connection: CanvasConnection = {
        id: 'edge-2',
        source: 'node-1',
        target: 'node-2',
        sourceHandle: 'undefined',
        targetHandle: 'undefined',
      };

      const edge = toReactFlowEdge(connection);

      expect(edge.sourceHandle).toBeUndefined();
      expect(edge.targetHandle).toBeUndefined();
    });
  });

  describe('fromReactFlowNodes', () => {
    test('should convert React Flow Nodes to CanvasElements', () => {
      const nodes: Node[] = [
        {
          id: 'node-1',
          type: 'component',
          position: { x: 100, y: 200 },
          data: { label: 'Test' },
          selected: true,
        },
      ];

      const elements = fromReactFlowNodes(nodes);

      expect(elements).toHaveLength(1);
      expect(elements[0].id).toBe('node-1');
      expect(elements[0].kind).toBe('component');
      expect(elements[0].type).toBe('component');
      expect(elements[0].position).toEqual({ x: 100, y: 200 });
      expect(elements[0].selected).toBe(true);
    });

    test('should handle nodes with dimensions', () => {
      const nodes: Node[] = [
        {
          id: 'node-2',
          type: 'api',
          position: { x: 0, y: 0 },
          data: {},
          width: 150,
          height: 100,
        },
      ];

      const elements = fromReactFlowNodes(nodes);

      expect(elements[0].size).toEqual({ width: 150, height: 100 });
    });
  });

  describe('fromReactFlowEdges', () => {
    test('should convert React Flow Edges to CanvasConnections', () => {
      const edges: Edge[] = [
        {
          id: 'edge-1',
          source: 'node-1',
          target: 'node-2',
          sourceHandle: 'right',
          targetHandle: 'left',
          animated: true,
        },
      ];

      const connections = fromReactFlowEdges(edges);

      expect(connections).toHaveLength(1);
      expect(connections[0].id).toBe('edge-1');
      expect(connections[0].source).toBe('node-1');
      expect(connections[0].target).toBe('node-2');
      expect(connections[0].animated).toBe(true);
    });
  });

  describe('round-trip conversion', () => {
    test('should preserve data through round-trip conversion', () => {
      const originalElement: CanvasElement = {
        id: 'test-round-trip',
        kind: 'component',
        type: 'component',
        position: { x: 150, y: 250 },
        data: { label: 'Round Trip Test', description: 'Testing' },
        selected: false,
      };

      const node = toReactFlowNode(originalElement);
      const [convertedElement] = fromReactFlowNodes([node]);

      expect(convertedElement.id).toBe(originalElement.id);
      expect(convertedElement.position).toEqual(originalElement.position);
      expect(convertedElement.data).toEqual(originalElement.data);
    });

    test('should preserve edge data through round-trip conversion', () => {
      const originalConnection: CanvasConnection = {
        id: 'edge-round-trip',
        source: 'node-a',
        target: 'node-b',
        sourceHandle: 'bottom',
        targetHandle: 'top',
        animated: true,
        type: 'smoothstep',
      };

      const edge = toReactFlowEdge(originalConnection);
      const [convertedConnection] = fromReactFlowEdges([edge]);

      expect(convertedConnection.id).toBe(originalConnection.id);
      expect(convertedConnection.source).toBe(originalConnection.source);
      expect(convertedConnection.target).toBe(originalConnection.target);
      expect(convertedConnection.animated).toBe(originalConnection.animated);
    });
  });

  describe('normalization utilities', () => {
    test('should normalize nodes for comparison', () => {
      const nodes: Node[] = [
        {
          id: 'b',
          type: 'api',
          position: { x: 0, y: 0 },
          data: { label: 'B' },
        },
        {
          id: 'a',
          type: 'component',
          position: { x: 100, y: 100 },
          data: { label: 'A' },
        },
      ];

      const normalized = normalizeNodesForCompare(nodes);

      expect(normalized[0].id).toBe('a');
      expect(normalized[1].id).toBe('b');
      expect(normalized).toHaveLength(2);
    });

    test('should normalize elements for comparison', () => {
      const elements: CanvasElement[] = [
        {
          id: 'z',
          kind: 'node',
          type: 'data',
          position: { x: 0, y: 0 },
          data: {},
        },
        {
          id: 'a',
          kind: 'component',
          type: 'component',
          position: { x: 0, y: 0 },
          data: {},
        },
      ];

      const normalized = normalizeElementsForCompare(elements);

      expect(normalized[0].id).toBe('a');
      expect(normalized[1].id).toBe('z');
    });
  });
});
