/**
 * @fileoverview Tests for BuilderCanvasProjectionAdapter.
 *
 * Verifies that:
 * - builderToCanvas converts BuilderDocument nodes into canvas nodes + edges
 * - Canvas nodes have the correct id, position, and data fields
 * - Slot relationships are emitted as canvas edges
 * - canvasToBuilder merges position changes back into a BuilderDocument
 * - canvasToBuilder leaves unmentioned nodes unchanged
 * - A full round-trip (builder→canvas→builder) preserves node count and structure
 *
 * @doc.type test
 * @doc.purpose BuilderCanvasProjectionAdapter unit tests
 * @doc.layer studio
 * @doc.pattern UnitTest
 */

import { describe, it, expect } from 'vitest';
import {
  builderToCanvas,
  canvasToBuilder,
  filterCanvasSelectionToNodeIds,
  filterValidBuilderCanvasNodes,
  isBuilderCanvasNode,
  reconcileCanvasGeometryDeltas,
} from '../BuilderCanvasProjectionAdapter.js';
import type { BuilderCanvasNode, CanvasNodeGeometryDelta } from '../BuilderCanvasProjectionAdapter.js';
import { createBuilderDocument, insertNode } from '@ghatana/ui-builder';
import type { BuilderDocument, NodeId } from '@ghatana/ui-builder';
import type { HybridCanvasNode } from '@ghatana/canvas';

// ============================================================================
// Helpers
// ============================================================================

function makeEmptyDocument(): BuilderDocument {
  return createBuilderDocument('test-owner');
}

function makeDocumentWithTwoNodes(): BuilderDocument {
  const base = createBuilderDocument('two-nodes');
  const withButton = insertNode(base, {
    contractName: 'Button',
    props: { label: 'Click' },
    slots: {},
    bindings: [],
    metadata: { name: 'Primary Button', position: { x: 100, y: 200 } },
  });
  const withCard = insertNode(withButton, {
    contractName: 'Card',
    props: { title: 'Hello' },
    slots: {},
    bindings: [],
    metadata: { name: 'Card Widget' },
  });
  return withCard;
}

// ============================================================================
// builderToCanvas
// ============================================================================

describe('builderToCanvas', () => {
  describe('empty document', () => {
    it('returns empty nodes and edges arrays for a bare document', () => {
      const doc = makeEmptyDocument();
      const { nodes, edges } = builderToCanvas(doc);
      // An empty document has only a RootContainer node which is excluded
      expect(nodes).toHaveLength(0);
      expect(edges).toHaveLength(0);
    });
  });

  describe('node projection', () => {
    it('produces one canvas node per non-root BuilderDocument node', () => {
      const doc = makeDocumentWithTwoNodes();
      const { nodes } = builderToCanvas(doc);
      // Button + Card = 2; RootContainer excluded
      expect(nodes).toHaveLength(2);
    });

    it('each canvas node has an id matching the document node key', () => {
      const doc = makeDocumentWithTwoNodes();
      const { nodes } = builderToCanvas(doc);
      const docNodeIds = Object.keys(doc.nodes).filter(
        (k) => doc.nodes[k]?.contractName !== 'RootContainer',
      );
      const canvasIds = nodes.map((n) => n.id);
      for (const id of docNodeIds) {
        expect(canvasIds).toContain(id);
      }
    });

    it('uses metadata.position when available', () => {
      const doc = makeDocumentWithTwoNodes();
      const { nodes } = builderToCanvas(doc);
      const buttonNode = nodes.find((n) => n.data.contractName === 'Button');
      expect(buttonNode?.position).toEqual({ x: 100, y: 200 });
    });

    it('falls back to a grid position when metadata.position is absent', () => {
      const doc = makeDocumentWithTwoNodes();
      const { nodes } = builderToCanvas(doc);
      const cardNode = nodes.find((n) => n.data.contractName === 'Card');
      // Position must be defined and numeric
      expect(typeof cardNode?.position?.x).toBe('number');
      expect(typeof cardNode?.position?.y).toBe('number');
    });

    it('populates canvas node data with contractName and props', () => {
      const doc = makeDocumentWithTwoNodes();
      const { nodes } = builderToCanvas(doc);
      const buttonNode = nodes.find((n) => n.data.contractName === 'Button');
      expect(buttonNode).toBeDefined();
      expect(buttonNode?.data.props).toMatchObject({ label: 'Click' });
    });
  });

  describe('edge projection', () => {
    it('emits no edges for nodes with empty slot maps', () => {
      const doc = makeDocumentWithTwoNodes();
      const { edges } = builderToCanvas(doc);
      expect(edges).toHaveLength(0);
    });

    it('emits an edge for each slot child relationship', () => {
      // Insert Card first
      const base = createBuilderDocument('slotted');
      const withCard = insertNode(base, {
        contractName: 'Card',
        props: {},
        slots: {},
        bindings: [],
        metadata: {},
      });

      // Get the Card node ID
      const cardId = Object.keys(withCard.nodes).find(
        (k) => withCard.nodes[k]?.contractName === 'Card',
      )! as NodeId;

      // Insert Button as a child of Card in the 'content' slot
      const withButton = insertNode(withCard, {
        contractName: 'Button',
        props: {},
        slots: {},
        bindings: [],
        metadata: {},
      }, cardId, 'content');

      // Get the Button node ID
      const buttonId = Object.keys(withButton.nodes).find(
        (k) => withButton.nodes[k]?.contractName === 'Button',
      )!;

      const { edges } = builderToCanvas(withButton);
      expect(edges.length).toBeGreaterThanOrEqual(1);
      // The slot edge should connect Card → Button
      const slotEdge = edges.find(
        (e) => e.source === cardId && e.target === buttonId,
      );
      expect(slotEdge).toBeDefined();
      expect((slotEdge?.data as { slotName?: string })?.slotName).toBe('content');
    });

    it('skips stale slot child IDs instead of emitting invalid canvas edges', () => {
      const base = makeDocumentWithTwoNodes();
      const buttonId = Object.keys(base.nodes).find((id) => base.nodes[id]?.contractName === 'Button')!;
      const corruptDoc: BuilderDocument = {
        ...base,
        nodes: {
          ...base.nodes,
          [buttonId]: {
            ...base.nodes[buttonId]!,
            slots: {
              content: ['missing-child' as NodeId],
            },
          },
        },
      };

      const { edges } = builderToCanvas(corruptDoc);

      expect(edges.some((edge) => edge.target === 'missing-child')).toBe(false);
    });
  });
});

// ============================================================================
// canvasToBuilder
// ============================================================================

describe('canvasToBuilder', () => {
  it('returns a BuilderDocument with the same node count as the base', () => {
    const base = makeDocumentWithTwoNodes();
    const { nodes: canvasNodes } = builderToCanvas(base);
    const updated = canvasToBuilder({ baseDocument: base, canvasNodes });
    expect(Object.keys(updated.nodes)).toHaveLength(Object.keys(base.nodes).length);
  });

  it('updates position for a node that appears in canvasNodes', () => {
    const base = makeDocumentWithTwoNodes();
    const { nodes: canvasNodes } = builderToCanvas(base);

    // Move the Button node to a new position
    const buttonCanvasNode = canvasNodes.find((n) => n.data.contractName === 'Button');
    expect(buttonCanvasNode).toBeDefined();

    const movedNodes: BuilderCanvasNode[] = canvasNodes.map((n) =>
      n.id === buttonCanvasNode!.id ? { ...n, position: { x: 999, y: 888 } } : n,
    );

    const updated = canvasToBuilder({ baseDocument: base, canvasNodes: movedNodes });
    const buttonDocNode = Object.values(updated.nodes).find(
      (n) => n.contractName === 'Button',
    );
    expect(
      (buttonDocNode?.metadata as { position?: { x: number; y: number } })?.position,
    ).toEqual({ x: 999, y: 888 });
  });

  it('does not mutate the base document', () => {
    const base = makeDocumentWithTwoNodes();
    const { nodes: canvasNodes } = builderToCanvas(base);
    const moved: BuilderCanvasNode[] = canvasNodes.map((n) => ({
      ...n,
      position: { x: 500, y: 500 },
    }));

    const updated = canvasToBuilder({ baseDocument: base, canvasNodes: moved });
    expect(updated).not.toBe(base);
    // Original nodes remain unchanged
    const buttonOriginal = Object.values(base.nodes).find(
      (n) => n.contractName === 'Button',
    );
    const buttonOriginalPosition = (
      buttonOriginal?.metadata as { position?: { x: number; y: number } }
    )?.position;
    // Original should still be { x: 100, y: 200 } as set in the fixture
    expect(buttonOriginalPosition).toEqual({ x: 100, y: 200 });
  });

  it('leaves nodes not in canvasNodes unchanged', () => {
    const base = makeDocumentWithTwoNodes();
    const { nodes: canvasNodes } = builderToCanvas(base);

    // Only pass Button to canvasToBuilder; Card is omitted
    const buttonNode = canvasNodes.filter((n) => n.data.contractName === 'Button');
    const updated = canvasToBuilder({ baseDocument: base, canvasNodes: buttonNode });

    // Card node should still be present (untouched)
    const cardDocNode = Object.values(updated.nodes).find(
      (n) => n.contractName === 'Card',
    );
    expect(cardDocNode).toBeDefined();
  });

  it('ignores non-finite canvas positions instead of corrupting Builder metadata', () => {
    const base = makeDocumentWithTwoNodes();
    const { nodes: canvasNodes } = builderToCanvas(base);
    const buttonNode = canvasNodes.find((node) => node.data.contractName === 'Button');
    expect(buttonNode).toBeDefined();
    const invalidNodes: BuilderCanvasNode[] = canvasNodes.map((node) =>
      node.id === buttonNode!.id ? { ...node, position: { x: Number.NaN, y: 888 } } : node,
    );

    const updated = canvasToBuilder({ baseDocument: base, canvasNodes: invalidNodes });
    const buttonDocNode = Object.values(updated.nodes).find((node) => node.contractName === 'Button');

    expect((buttonDocNode?.metadata as { position?: { x: number; y: number } })?.position).toEqual({ x: 100, y: 200 });
  });
});

// ============================================================================
// Round-trip: builderToCanvas → canvasToBuilder
// ============================================================================

describe('round-trip: builderToCanvas → canvasToBuilder', () => {
  it('preserves the node count through a full round-trip', () => {
    const original = makeDocumentWithTwoNodes();
    const { nodes: canvasNodes } = builderToCanvas(original);
    const roundTripped = canvasToBuilder({ baseDocument: original, canvasNodes });

    expect(Object.keys(roundTripped.nodes)).toHaveLength(
      Object.keys(original.nodes).length,
    );
  });

  it('preserves contractName for every node through a full round-trip', () => {
    const original = makeDocumentWithTwoNodes();
    const { nodes: canvasNodes } = builderToCanvas(original);
    const roundTripped = canvasToBuilder({ baseDocument: original, canvasNodes });

    const originalContracts = Object.values(original.nodes).map((n) => n.contractName);
    const roundTrippedContracts = Object.values(roundTripped.nodes).map(
      (n) => n.contractName,
    );

    for (const name of originalContracts) {
      expect(roundTrippedContracts).toContain(name);
    }
  });
});

// ============================================================================
// Negative validation
// ============================================================================

describe('adapter validation boundaries', () => {
  it('rejects malformed canvas nodes and stale IDs without unsafe casts', () => {
    const doc = makeDocumentWithTwoNodes();
    const { nodes } = builderToCanvas(doc);
    const valid = nodes[0]!;
    const malformed: HybridCanvasNode<Record<string, unknown>>[] = [
      {
        ...valid,
        id: 'stale-id',
      },
      {
        ...valid,
        position: { x: Number.POSITIVE_INFINITY, y: 10 },
      },
      {
        ...valid,
        data: {
          ...valid.data,
          nodeId: 'different-node',
        },
      },
      {
        ...valid,
        data: {
          ...valid.data,
          props: 'not-an-object',
        },
      },
    ];

    expect(malformed.every((node) => !isBuilderCanvasNode(doc, node))).toBe(true);
    expect(filterValidBuilderCanvasNodes(doc, [valid, ...malformed])).toEqual([valid]);
  });

  it('filters unknown canvas selections to known Builder node IDs', () => {
    const doc = makeDocumentWithTwoNodes();
    const { nodes } = builderToCanvas(doc);

    const result = filterCanvasSelectionToNodeIds(doc, [nodes[0]!.id, 'missing-selection']);

    expect(result).toEqual([nodes[0]!.id]);
  });

  it('drops geometry deltas with unknown nodes, non-finite positions, or negative sizes', () => {
    const doc = makeDocumentWithTwoNodes();
    const { nodes } = builderToCanvas(doc);
    const validNode = nodes[0]!;
    const deltas: CanvasNodeGeometryDelta[] = [
      {
        canvasNodeId: validNode.id,
        position: { x: 150, y: 250 },
      },
      {
        canvasNodeId: 'unknown-node',
        position: { x: 300, y: 400 },
      },
      {
        canvasNodeId: validNode.id,
        position: { x: Number.NaN, y: 400 },
      },
      {
        canvasNodeId: validNode.id,
        size: { width: -1, height: 20 },
      },
    ];

    const result = reconcileCanvasGeometryDeltas(doc, deltas);

    expect(result).toEqual([
      {
        kind: 'update-node-geometry',
        nodeId: validNode.id,
        position: { x: 150, y: 250 },
      },
    ]);
  });
});
