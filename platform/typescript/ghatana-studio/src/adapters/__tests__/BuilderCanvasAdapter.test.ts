/**
 * @fileoverview Tests for BuilderCanvasAdapter.
 *
 * Verifies that:
 * - projectBuilderDocumentToCanvas produces canvas nodes/edges from a BuilderDocument
 * - filterCanvasSelectionToNodeIds validates and brands canvas string IDs against document keys
 * - reconcileCanvasGeometryDeltas converts geometry changes into typed builder operations
 *
 * All tests import and invoke the real production adapter functions.
 */

import { describe, it, expect } from 'vitest';
import {
  projectBuilderDocumentToCanvas,
  filterCanvasSelectionToNodeIds,
  reconcileCanvasGeometryDeltas,
} from '../BuilderCanvasAdapter.js';
import { createBuilderDocument, insertNode } from '@ghatana/ui-builder';

// ============================================================================
// Test fixture helpers
// ============================================================================

function makeDocumentWithNodes() {
  const base = createBuilderDocument('test-owner');
  const withButton = insertNode(base, {
    contractName: 'Button',
    props: { label: 'Click me' },
    slots: {},
    bindings: [],
    metadata: { name: 'My Button' },
  });
  const withCard = insertNode(withButton, {
    contractName: 'Card',
    props: { title: 'Card Title' },
    slots: {},
    bindings: [],
    metadata: { name: 'My Card', position: { x: 100, y: 200 } },
  });
  return withCard;
}

// ============================================================================
// projectBuilderDocumentToCanvas
// ============================================================================

describe('projectBuilderDocumentToCanvas', () => {
  it('converts non-root ComponentInstances to canvas nodes', () => {
    const doc = makeDocumentWithNodes();
    const { nodes } = projectBuilderDocumentToCanvas(doc);
    // Should have 2 non-root nodes (Button + Card); RootContainer is excluded
    expect(nodes).toHaveLength(2);
    expect(nodes.every((n) => n.type === 'node')).toBe(true);
    const labels = nodes.map((n) => n.data.contractName);
    expect(labels).toContain('Button');
    expect(labels).toContain('Card');
  });

  it('excludes RootContainer from canvas nodes', () => {
    const doc = makeDocumentWithNodes();
    const { nodes } = projectBuilderDocumentToCanvas(doc);
    expect(nodes.every((n) => n.data.contractName !== 'RootContainer')).toBe(true);
  });

  it('applies position from metadata when present', () => {
    const doc = makeDocumentWithNodes();
    const { nodes } = projectBuilderDocumentToCanvas(doc);
    const cardNode = nodes.find((n) => n.data.contractName === 'Card');
    expect(cardNode?.position).toEqual({ x: 100, y: 200 });
  });

  it('defaults to {x:0, y:0} when metadata.position is absent', () => {
    const doc = makeDocumentWithNodes();
    const { nodes } = projectBuilderDocumentToCanvas(doc);
    const buttonNode = nodes.find((n) => n.data.contractName === 'Button');
    expect(buttonNode?.position).toEqual({ x: 0, y: 0 });
  });

  it('marks selected nodes with selected=true', () => {
    const doc = makeDocumentWithNodes();
    const { nodes } = projectBuilderDocumentToCanvas(doc);
    // Find the Button node ID
    const buttonNode = nodes.find((n) => n.data.contractName === 'Button');
    expect(buttonNode).toBeDefined();
    const selectedNodeIds = [buttonNode!.data.nodeId];

    const { nodes: nodesWithSelection } = projectBuilderDocumentToCanvas(doc, selectedNodeIds);
    const selected = nodesWithSelection.find((n) => n.data.contractName === 'Button');
    expect(selected?.selected).toBe(true);

    const unselected = nodesWithSelection.find((n) => n.data.contractName === 'Card');
    expect(unselected?.selected).toBe(false);
  });

  it('emits no edges for nodes with empty slots', () => {
    const doc = makeDocumentWithNodes();
    const { edges } = projectBuilderDocumentToCanvas(doc);
    // Button and Card have no slot children, so no edges
    expect(edges).toHaveLength(0);
  });

  it('returns empty nodes and edges for an empty document', () => {
    const doc = createBuilderDocument('owner');
    const { nodes, edges } = projectBuilderDocumentToCanvas(doc);
    expect(nodes).toHaveLength(0);
    expect(edges).toHaveLength(0);
  });
});

// ============================================================================
// filterCanvasSelectionToNodeIds
// ============================================================================

describe('filterCanvasSelectionToNodeIds', () => {
  it('returns document NodeIds for known canvas IDs', () => {
    const doc = makeDocumentWithNodes();
    const { nodes } = projectBuilderDocumentToCanvas(doc);
    const knownIds = nodes.map((n) => n.id);
    const result = filterCanvasSelectionToNodeIds(doc, knownIds);
    expect(result).toHaveLength(knownIds.length);
    // All returned IDs are strings (branded NodeId is a string at runtime)
    expect(result.every((id) => typeof id === 'string')).toBe(true);
  });

  it('filters out unknown canvas IDs that are not in the document', () => {
    const doc = makeDocumentWithNodes();
    const result = filterCanvasSelectionToNodeIds(doc, ['unknown-canvas-group-id', 'another-fake-id']);
    // Neither ID is a document node key → both filtered out
    expect(result).toHaveLength(0);
  });

  it('returns only valid IDs from a mixed known/unknown list', () => {
    const doc = makeDocumentWithNodes();
    const { nodes } = projectBuilderDocumentToCanvas(doc);
    const known = nodes[0]!.id;
    const result = filterCanvasSelectionToNodeIds(doc, [known, 'fake-id', 'another-fake']);
    expect(result).toHaveLength(1);
    expect(result[0]).toBe(known);
  });

  it('returns empty array for empty canvas selection', () => {
    const doc = makeDocumentWithNodes();
    expect(filterCanvasSelectionToNodeIds(doc, [])).toHaveLength(0);
  });
});

// ============================================================================
// reconcileCanvasGeometryDeltas
// ============================================================================

describe('reconcileCanvasGeometryDeltas', () => {
  it('produces builder geometry operations for known nodes', () => {
    const doc = makeDocumentWithNodes();
    const { nodes } = projectBuilderDocumentToCanvas(doc);
    const nodeId = nodes[0]!.id;

    const ops = reconcileCanvasGeometryDeltas(doc, [
      { canvasNodeId: nodeId, position: { x: 50, y: 75 } },
    ]);

    expect(ops).toHaveLength(1);
    expect(ops[0]?.kind).toBe('update-node-geometry');
    expect(ops[0]?.nodeId).toBe(nodeId);
    expect(ops[0]?.position).toEqual({ x: 50, y: 75 });
  });

  it('drops deltas for unknown canvas node IDs', () => {
    const doc = makeDocumentWithNodes();
    const ops = reconcileCanvasGeometryDeltas(doc, [
      { canvasNodeId: 'virtual-canvas-group', position: { x: 0, y: 0 } },
    ]);
    expect(ops).toHaveLength(0);
  });

  it('emits both position and size when both are present in a delta', () => {
    const doc = makeDocumentWithNodes();
    const { nodes } = projectBuilderDocumentToCanvas(doc);
    const nodeId = nodes[0]!.id;

    const ops = reconcileCanvasGeometryDeltas(doc, [
      { canvasNodeId: nodeId, position: { x: 10, y: 20 }, size: { width: 300, height: 150 } },
    ]);

    expect(ops).toHaveLength(1);
    expect(ops[0]?.position).toEqual({ x: 10, y: 20 });
    expect(ops[0]?.size).toEqual({ width: 300, height: 150 });
  });

  it('returns empty array for empty deltas', () => {
    const doc = makeDocumentWithNodes();
    expect(reconcileCanvasGeometryDeltas(doc, [])).toHaveLength(0);
  });
});
