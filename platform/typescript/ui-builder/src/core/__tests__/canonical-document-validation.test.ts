/**
 * @fileoverview Canonical document validation tests
 *
 * Tests that the canonical BuilderDocument schema is properly validated
 * and that all operations maintain document integrity.
 *
 * @doc.type test
 * @doc.purpose Canonical document validation
 * @doc.layer platform
 */

import { describe, it, expect } from 'vitest';
import {
  createBuilderDocument,
  validateBuilderDocument,
  serializeBuilderDocument,
  deserializeBuilderDocument,
  type BuilderDocument,
} from '../builder-document.js';
import { insertNode, updateNodeProps, deleteNode, moveNode } from '../operations.js';

/** Returns the first user-inserted node ID (filters out the implicit RootContainer). */
function getUserNodeId(doc: BuilderDocument): string {
  const entry = Object.entries(doc.nodes).find(([, v]) => v.contractName !== 'RootContainer');
  if (!entry) throw new Error('No user node found in document');
  return entry[0];
}

/** Returns all user-inserted node IDs (filters out the implicit RootContainer). */
function getUserNodeIds(doc: BuilderDocument): string[] {
  return Object.entries(doc.nodes)
    .filter(([, v]) => v.contractName !== 'RootContainer')
    .map(([k]) => k);
}

describe('Canonical Document Validation', () => {
  describe('document creation', () => {
    it('should create a valid canonical document', () => {
      const doc = createBuilderDocument('test-user', {
        designSystemId: 'test-ds',
        designSystemName: 'Test Design System',
      });

      const validation = validateBuilderDocument(doc);
      expect(validation.valid).toBe(true);
      expect(validation.errors).toHaveLength(0);
    });

    it('should include required fields in canonical document', () => {
      const doc = createBuilderDocument('test-user');

      expect(doc.documentId).toBeDefined();
      expect(doc.schemaVersion).toBeDefined();
      expect(doc.nodes).toBeDefined();
      expect(doc.layout).toBeDefined();
      expect(doc.metadata).toBeDefined();
    });

    it('should validate document ID format', () => {
      const doc = createBuilderDocument('test-user');

      // Document ID should be a valid UUID or similar identifier
      expect(doc.documentId).toMatch(/^[a-f0-9-]+$/i);
    });
  });

  describe('document operations', () => {
    it('should maintain validity after insert operation', () => {
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
    });

    it('should maintain validity after update operation', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const nodeId = Object.keys(doc.nodes)[0];
      doc = updateNodeProps(doc, nodeId, { label: 'Updated' });

      const validation = validateBuilderDocument(doc);
      expect(validation.valid).toBe(true);
    });

    it('should maintain validity after delete operation', () => {
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

    it('should maintain validity after move operation', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1', position: { x: 0, y: 0 } },
      });

      const nodeId = Object.keys(doc.nodes)[0];
      doc = moveNode(doc, nodeId, { x: 100, y: 100 });

      const validation = validateBuilderDocument(doc);
      expect(validation.valid).toBe(true);
    });
  });

  describe('serialization round-trip', () => {
    it('should serialize and deserialize without data loss', () => {
      const original = createBuilderDocument('test-user', {
        designSystemId: 'test-ds',
        designSystemName: 'Test Design System',
      });

      const serialized = serializeBuilderDocument(original);
      const result = deserializeBuilderDocument(serialized);

      expect(result.success).toBe(true);
      expect(result.document).toBeDefined();

      if (result.document) {
        expect(result.document.documentId).toBe(original.documentId);
        expect(result.document.schemaVersion).toBe(original.schemaVersion);
        expect(result.document.owner).toBe(original.owner);
      }
    });

    it('should preserve nodes through serialization', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Test Button' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const serialized = serializeBuilderDocument(doc);
      const result = deserializeBuilderDocument(serialized);

      expect(result.success).toBe(true);
      expect(result.document?.nodes).toBeDefined();
      expect(getUserNodeIds(result.document as BuilderDocument)).toHaveLength(1);
    });

    it('should preserve layout through serialization', () => {
      const doc = createBuilderDocument('test-user');

      const serialized = serializeBuilderDocument(doc);
      const result = deserializeBuilderDocument(serialized);

      expect(result.success).toBe(true);
      expect(result.document?.layout).toBeDefined();
      expect(result.document?.layout.rootId).toBe(doc.layout.rootId);
    });
  });

  describe('schema versioning', () => {
    it('should include schema version in document', () => {
      const doc = createBuilderDocument('test-user');

      expect(doc.schemaVersion).toBeDefined();
      expect(typeof doc.schemaVersion).toBe('string');
    });

    it('should handle documents with missing schema version', () => {
      const doc = createBuilderDocument('test-user');
      const { schemaVersion, ...docWithoutVersion } = doc as any;

      const validation = validateBuilderDocument(docWithoutVersion);
      // Should auto-migrate and validate successfully
      expect(validation.valid).toBe(true);
    });
  });

  describe('metadata integrity', () => {
    it('should preserve metadata through operations', () => {
      let doc = createBuilderDocument('test-user', {
        metadata: { description: 'Test document' },
      });

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      expect(doc.metadata.description).toBe('Test document');
      expect(doc.metadata.author).toBe('test-user');
    });

    it('should update timestamps on operations', () => {
      let doc = createBuilderDocument('test-user');
      const originalUpdatedAt = doc.metadata.updatedAt;

      // Wait a bit to ensure timestamp difference
      const now = new Date();
      const future = new Date(now.getTime() + 10);

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      // UpdatedAt should have changed
      expect(doc.metadata.updatedAt).not.toBe(originalUpdatedAt);
    });
  });
});
