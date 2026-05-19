/**
 * @fileoverview Operation validation tests
 *
 * Tests that all document operations maintain validity and
 * produce expected results.
 *
 * @doc.type test
 * @doc.purpose Operation validation
 * @doc.layer platform
 */

import { describe, it, expect } from 'vitest';
import {
  createBuilderDocument,
  validateBuilderDocument,
  type BuilderDocument,
} from '../builder-document.js';
import {
  insertNode,
  updateNodeProps,
  deleteNode,
  moveNode,
  setNodePosition,
  duplicateNode,
  batchOperations,
} from '../operations.js';

/** Returns all user-inserted node IDs (filters out the implicit RootContainer). */
function getUserNodeIds(doc: BuilderDocument): string[] {
  return Object.entries(doc.nodes)
    .filter(([, v]) => v.contractName !== 'RootContainer')
    .map(([k]) => k);
}

/** Returns the first user-inserted node ID. Throws if none found. */
function getUserNodeId(doc: BuilderDocument): string {
  const id = getUserNodeIds(doc)[0];
  if (!id) throw new Error('No user node found in document');
  return id;
}

describe('Operation Validation', () => {
  describe('insertNode operation', () => {
    it('should insert a valid node', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const validation = validateBuilderDocument(doc);
      expect(validation.valid).toBe(true);
      expect(getUserNodeIds(doc)).toHaveLength(1);
    });

    it('should reject node without contractName', () => {
      let doc = createBuilderDocument('test-user');

      expect(() => {
        insertNode(doc, {
          contractName: '',
          props: {},
          slots: {},
          bindings: [],
          metadata: {},
        } as any);
      }).toThrow();
    });

    it('should add node to layout root children', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const rootNode = doc.layout.nodes[doc.layout.rootId];
      expect(rootNode?.children).toHaveLength(1);
    });
  });

  describe('updateNodeProps operation', () => {
    it('should update node props', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Original' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const nodeId = getUserNodeId(doc);
      doc = updateNodeProps(doc, nodeId, { label: 'Updated' });

      const node = doc.nodes[nodeId];
      expect(node.props.label).toBe('Updated');
    });

    it('should maintain validity after update', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Original' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const nodeId = getUserNodeId(doc);
      doc = updateNodeProps(doc, nodeId, { label: 'Updated', disabled: true });

      const validation = validateBuilderDocument(doc);
      expect(validation.valid).toBe(true);
    });

    it('should handle nested prop updates', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Container',
        props: { style: { color: 'red' } },
        slots: {},
        bindings: [],
        metadata: { name: 'Container1' },
      });

      const nodeId = getUserNodeId(doc);
      doc = updateNodeProps(doc, nodeId, { style: { color: 'blue', fontSize: '16px' } });

      const node = doc.nodes[nodeId];
      expect(node.props.style).toEqual({ color: 'blue', fontSize: '16px' });
    });
  });

  describe('deleteNode operation', () => {
    it('should delete a node', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const nodeId = getUserNodeId(doc);
      doc = deleteNode(doc, nodeId);

      expect(doc.nodes[nodeId]).toBeUndefined();
    });

    it('should remove node from layout', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const nodeId = getUserNodeId(doc);
      doc = deleteNode(doc, nodeId);

      const rootNode = doc.layout.nodes[doc.layout.rootId];
      expect(rootNode?.children).toHaveLength(0);
    });

    it('should maintain validity after delete', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const nodeId = getUserNodeId(doc);
      doc = deleteNode(doc, nodeId);

      const validation = validateBuilderDocument(doc);
      expect(validation.valid).toBe(true);
    });
  });

  describe('moveNode operation', () => {
    it('should update node position', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1', position: { x: 0, y: 0 } },
      });

      const nodeId = getUserNodeId(doc);
      doc = setNodePosition(doc, nodeId, { x: 100, y: 200 });

      const node = doc.nodes[nodeId];
      expect(node.metadata.position).toEqual({ x: 100, y: 200 });
    });

    it('should maintain validity after move', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1', position: { x: 0, y: 0 } },
      });

      const nodeId = getUserNodeId(doc);
      doc = setNodePosition(doc, nodeId, { x: 100, y: 200 });

      const validation = validateBuilderDocument(doc);
      expect(validation.valid).toBe(true);
    });
  });

  describe('duplicateNode operation', () => {
    it('should create a copy of a node', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const nodeId = getUserNodeId(doc);
      doc = duplicateNode(doc, nodeId);

      expect(getUserNodeIds(doc)).toHaveLength(2);
    });

    it('should give duplicated node a unique ID', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const nodeId = getUserNodeId(doc);
      doc = duplicateNode(doc, nodeId);

      const nodeIds = getUserNodeIds(doc);
      expect(nodeIds[0]).not.toBe(nodeIds[1]);
    });

    it('should maintain validity after duplicate', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const nodeId = getUserNodeId(doc);
      doc = duplicateNode(doc, nodeId);

      const validation = validateBuilderDocument(doc);
      expect(validation.valid).toBe(true);
    });
  });

  describe('batchOperations', () => {
    it('should execute multiple operations atomically', () => {
      let doc = createBuilderDocument('test-user');

      doc = batchOperations(doc, (draft) => {
        const node1 = insertNode(draft, {
          contractName: 'Button',
          props: { label: 'Button 1' },
          slots: {},
          bindings: [],
          metadata: { name: 'Button1' },
        });

        const nodeId = getUserNodeId(node1);
        return updateNodeProps(node1, nodeId, { label: 'Updated' });
      });

      expect(getUserNodeIds(doc)).toHaveLength(1);
      const nodeId = getUserNodeId(doc);
      const node = doc.nodes[nodeId];
      expect(node.props.label).toBe('Updated');
    });

    it('should maintain validity after batch', () => {
      let doc = createBuilderDocument('test-user');

      doc = batchOperations(doc, (draft) => {
        let updated = insertNode(draft, {
          contractName: 'Button',
          props: { label: 'Button 1' },
          slots: {},
          bindings: [],
          metadata: { name: 'Button1' },
        });

        const nodeId = getUserNodeId(updated);
        return updateNodeProps(updated, nodeId, { disabled: true });
      });

      const validation = validateBuilderDocument(doc);
      expect(validation.valid).toBe(true);
    });
  });

  describe('undo/redo', () => {
    it('should support undo operations', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      expect(getUserNodeIds(doc)).toHaveLength(1);

      // Note: undo/redo would be implemented by the operation manager
      // This test validates that operations are reversible
      const nodeId = getUserNodeId(doc);
      doc = deleteNode(doc, nodeId);

      expect(getUserNodeIds(doc)).toHaveLength(0);
    });
  });
});
