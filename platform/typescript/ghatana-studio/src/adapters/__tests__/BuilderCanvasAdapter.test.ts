/**
 * @fileoverview Tests for the canonical BuilderCanvasAdapter.
 *
 * Verifies that the canonical adapter correctly re-exports all required
 * functionality from BuilderCanvasProjectionAdapter.
 *
 * @doc.type test
 * @doc.purpose Unit tests for canonical Builder/Canvas adapter
 * @doc.layer studio
 */

import { describe, it, expect } from 'vitest';
import {
  // Core functions
  builderToCanvas,
  canvasToBuilder,
  filterCanvasSelectionToNodeIds,
  reconcileCanvasGeometryDeltas,
  isBuilderCanvasNode,
  filterValidBuilderCanvasNodes,
  
  // Types
  type BuilderCanvasNode,
  type CanvasNodeGeometryDelta,
  
  // Constants
  BUILDER_CANVAS_GRID,
  BUILDER_CANVAS_ADAPTER_VERSION,
} from '../BuilderCanvasAdapter.js';
import type { BuilderDocument, NodeId, ComponentInstance } from '@ghatana/ui-builder';

// ============================================================================
// Test Fixtures
// ============================================================================

function createTestNodeId(id: string): NodeId {
  return id as NodeId;
}

function createTestComponentInstance(overrides: Partial<ComponentInstance> & { contractName: string }): ComponentInstance {
  const { contractName, ...restOverrides } = overrides;
  return {
    id: createTestNodeId('test-id'),
    contractName,
    props: {},
    slots: {},
    bindings: [],
    metadata: {
      name: 'Test Component',
      locked: false,
      hidden: false,
    },
    ...restOverrides,
  };
}

function createTestBuilderDocument(overrides: Partial<BuilderDocument> = {}): BuilderDocument {
  const nodeId = createTestNodeId('node-1');
  return {
    schemaVersion: '1.0.0',
    documentId: 'doc-1' as import('@ghatana/ui-builder').DocumentId,
    owner: 'test-user',
    root: nodeId,
    nodes: {
      'node-1': createTestComponentInstance({
        id: nodeId,
        contractName: 'Button',
        props: { label: 'Click me' },
        metadata: { name: 'Primary Button', locked: false, hidden: false },
      }),
    },
    bindings: [],
    layout: {
      type: 'absolute',
      nodes: {},
      rootId: nodeId,
    },
    metadata: {
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    },
    ...overrides,
  };
}

// ============================================================================
// Export Tests
// ============================================================================

describe('BuilderCanvasAdapter: exports', () => {
  it('exports all core projection functions', () => {
    expect(typeof builderToCanvas).toBe('function');
    expect(typeof canvasToBuilder).toBe('function');
    expect(typeof filterCanvasSelectionToNodeIds).toBe('function');
    expect(typeof reconcileCanvasGeometryDeltas).toBe('function');
    expect(typeof isBuilderCanvasNode).toBe('function');
    expect(typeof filterValidBuilderCanvasNodes).toBe('function');
  });

  it('exports grid layout constants', () => {
    expect(BUILDER_CANVAS_GRID.COLUMNS).toBe(4);
    expect(BUILDER_CANVAS_GRID.CELL_WIDTH).toBe(240);
    expect(BUILDER_CANVAS_GRID.CELL_HEIGHT).toBe(140);
    expect(BUILDER_CANVAS_GRID.MARGIN_X).toBe(40);
    expect(BUILDER_CANVAS_GRID.MARGIN_Y).toBe(40);
  });

  it('exports adapter version', () => {
    expect(BUILDER_CANVAS_ADAPTER_VERSION).toBe('1.0.0');
  });
});

// ============================================================================
// builderToCanvas Tests
// ============================================================================

describe('BuilderCanvasAdapter: builderToCanvas', () => {
  it('projects a BuilderDocument to canvas nodes and edges', () => {
    const doc = createTestBuilderDocument();
    const result = builderToCanvas(doc);

    expect(result.nodes).toHaveLength(1);
    expect(result.nodes[0]!.id).toBe('node-1');
    expect(result.nodes[0]!.data.contractName).toBe('Button');
    expect(result.edges).toHaveLength(0);
  });

  it('creates edges for slot relationships', () => {
    const parentId = createTestNodeId('parent-1');
    const childId = createTestNodeId('child-1');
    
    const doc = createTestBuilderDocument({
      root: parentId,
      nodes: {
        'parent-1': createTestComponentInstance({
          id: parentId,
          contractName: 'Card',
          props: { title: 'Card Title' },
          slots: {
            content: [childId],
          },
          metadata: { name: 'Card', locked: false, hidden: false },
        }),
        'child-1': createTestComponentInstance({
          id: childId,
          contractName: 'Button',
          props: { label: 'Action' },
          metadata: { name: 'Action Button', locked: false, hidden: false },
        }),
      },
    });

    const result = builderToCanvas(doc);

    expect(result.nodes).toHaveLength(2);
    expect(result.edges).toHaveLength(1);
    const [edge] = result.edges;
    expect(edge).toBeDefined();
    expect(edge!.source).toBe('parent-1');
    expect(edge!.target).toBe('child-1');
    expect(edge!.data).toEqual(expect.objectContaining({ slotName: 'content' }));
  });

  it('excludes RootContainer from canvas projection', () => {
    const childId = createTestNodeId('child-1');
    const rootId = createTestNodeId('root-1');
    
    const doc = createTestBuilderDocument({
      root: rootId,
      nodes: {
        'root-1': createTestComponentInstance({
          id: rootId,
          contractName: 'RootContainer',
          props: {},
          metadata: { name: 'Root', locked: false, hidden: false },
        }),
        'child-1': createTestComponentInstance({
          id: childId,
          contractName: 'Button',
          props: { label: 'Action' },
          metadata: { name: 'Button', locked: false, hidden: false },
        }),
      },
    });

    const result = builderToCanvas(doc);

    expect(result.nodes).toHaveLength(1);
    expect(result.nodes[0]!.data.contractName).toBe('Button');
  });
});

// ============================================================================
// canvasToBuilder Tests
// ============================================================================

describe('BuilderCanvasAdapter: canvasToBuilder', () => {
  it('merges canvas positions back into BuilderDocument', () => {
    const nodeId = createTestNodeId('node-1');
    const doc = createTestBuilderDocument({
      root: nodeId,
      nodes: {
        'node-1': createTestComponentInstance({
          id: nodeId,
          contractName: 'Button',
          props: { label: 'Click me' },
          metadata: { name: 'Primary Button', locked: false, hidden: false },
        }),
      },
    });

    const canvasNodes: BuilderCanvasNode[] = [
      {
        id: 'node-1',
        type: 'default',
        position: { x: 100, y: 200 },
        data: {
          nodeId,
          contractName: 'Button',
          props: { label: 'Click me' },
          label: 'Button',
        },
      },
    ];

    const result = canvasToBuilder({ baseDocument: doc, canvasNodes });

    expect(result.nodes['node-1']!.metadata.position).toEqual({ x: 100, y: 200 });
  });

  it('leaves unchanged nodes intact', () => {
    const node1Id = createTestNodeId('node-1');
    const node2Id = createTestNodeId('node-2');
    
    const doc = createTestBuilderDocument({
      root: node1Id,
      nodes: {
        'node-1': createTestComponentInstance({
          id: node1Id,
          contractName: 'Button',
          props: { label: 'Click me' },
          metadata: { name: 'Primary Button', locked: false, hidden: false },
        }),
        'node-2': createTestComponentInstance({
          id: node2Id,
          contractName: 'Card',
          props: { title: 'Card Title' },
          metadata: { name: 'Card', locked: false, hidden: false, position: { x: 50, y: 50 } },
        }),
      },
    });

    const canvasNodes: BuilderCanvasNode[] = [
      {
        id: 'node-1',
        type: 'default',
        position: { x: 100, y: 200 },
        data: {
          nodeId: node1Id,
          contractName: 'Button',
          props: { label: 'Click me' },
          label: 'Button',
        },
      },
    ];

    const result = canvasToBuilder({ baseDocument: doc, canvasNodes });

    // node-1 should have new position
    expect(result.nodes['node-1']!.metadata.position).toEqual({ x: 100, y: 200 });
    // node-2 should retain its original position
    expect(result.nodes['node-2']!.metadata.position).toEqual({ x: 50, y: 50 });
  });
});

// ============================================================================
// filterCanvasSelectionToNodeIds Tests
// ============================================================================

describe('BuilderCanvasAdapter: filterCanvasSelectionToNodeIds', () => {
  it('filters valid node IDs from canvas selection', () => {
    const node1Id = createTestNodeId('node-1');
    const node2Id = createTestNodeId('node-2');
    
    const doc = createTestBuilderDocument({
      root: node1Id,
      nodes: {
        'node-1': createTestComponentInstance({
          id: node1Id,
          contractName: 'Button',
          metadata: { name: 'Button', locked: false, hidden: false },
        }),
        'node-2': createTestComponentInstance({
          id: node2Id,
          contractName: 'Card',
          metadata: { name: 'Card', locked: false, hidden: false },
        }),
      },
    });

    const selection = ['node-1', 'node-2', 'invalid-node'];
    const result = filterCanvasSelectionToNodeIds(doc, selection);

    expect(result).toHaveLength(2);
    expect(result.map((id: NodeId) => id as string)).toContain('node-1');
    expect(result.map((id: NodeId) => id as string)).toContain('node-2');
  });

  it('returns empty array for empty selection', () => {
    const doc = createTestBuilderDocument({ nodes: {} });
    const result = filterCanvasSelectionToNodeIds(doc, []);
    expect(result).toHaveLength(0);
  });
});

// ============================================================================
// reconcileCanvasGeometryDeltas Tests
// ============================================================================

describe('BuilderCanvasAdapter: reconcileCanvasGeometryDeltas', () => {
  it('converts geometry deltas to builder operations', () => {
    const nodeId = createTestNodeId('node-1');
    const doc = createTestBuilderDocument({
      root: nodeId,
      nodes: {
        'node-1': createTestComponentInstance({
          id: nodeId,
          contractName: 'Button',
          metadata: { name: 'Button', locked: false, hidden: false },
        }),
      },
    });

    const deltas: CanvasNodeGeometryDelta[] = [
      {
        canvasNodeId: 'node-1',
        position: { x: 150, y: 250 },
        size: { width: 200, height: 60 },
      },
    ];

    const result = reconcileCanvasGeometryDeltas(doc, deltas);

    expect(result).toHaveLength(1);
    expect(result[0]!.kind).toBe('update-node-geometry');
    expect(result[0]!.nodeId as string).toBe('node-1');
    expect(result[0]!.position).toEqual({ x: 150, y: 250 });
    expect(result[0]!.size).toEqual({ width: 200, height: 60 });
  });

  it('drops deltas for unknown nodes', () => {
    const nodeId = createTestNodeId('node-1');
    const doc = createTestBuilderDocument({
      root: nodeId,
      nodes: {
        'node-1': createTestComponentInstance({
          id: nodeId,
          contractName: 'Button',
          metadata: { name: 'Button', locked: false, hidden: false },
        }),
      },
    });

    const deltas: CanvasNodeGeometryDelta[] = [
      {
        canvasNodeId: 'node-1',
        position: { x: 150, y: 250 },
      },
      {
        canvasNodeId: 'unknown-node',
        position: { x: 300, y: 400 },
      },
    ];

    const result = reconcileCanvasGeometryDeltas(doc, deltas);

    expect(result).toHaveLength(1);
    expect(result[0]!.nodeId as string).toBe('node-1');
  });
});

// ============================================================================
// isBuilderCanvasNode Tests
// ============================================================================

describe('BuilderCanvasAdapter: isBuilderCanvasNode', () => {
  it('validates canvas nodes against document', () => {
    const nodeId = createTestNodeId('node-1');
    const doc = createTestBuilderDocument({
      root: nodeId,
      nodes: {
        'node-1': createTestComponentInstance({
          id: nodeId,
          contractName: 'Button',
          props: { label: 'Click' },
          metadata: { name: 'Button', locked: false, hidden: false },
        }),
      },
    });

    const validNode = {
      id: 'node-1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: {
        nodeId,
        contractName: 'Button',
        props: { label: 'Click' },
        label: 'Button',
      },
    };

    expect(isBuilderCanvasNode(doc, validNode)).toBe(true);
  });

  it('rejects nodes with mismatched IDs', () => {
    const nodeId = createTestNodeId('node-1');
    const doc = createTestBuilderDocument({
      root: nodeId,
      nodes: {
        'node-1': createTestComponentInstance({
          id: nodeId,
          contractName: 'Button',
          metadata: { name: 'Button', locked: false, hidden: false },
        }),
      },
    });

    const invalidNode = {
      id: 'node-2', // Not in document
      type: 'default',
      position: { x: 100, y: 100 },
      data: {
        nodeId: createTestNodeId('node-2'),
        contractName: 'Button',
        props: {},
        label: 'Button',
      },
    };

    expect(isBuilderCanvasNode(doc, invalidNode)).toBe(false);
  });
});
