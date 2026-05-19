/**
 * @fileoverview Round-trip tests for canvas document schema.
 *
 * Tests serialization, deserialization, and migration of CanvasDocument
 * to ensure data integrity and compatibility across versions.
 *
 * @doc.type test
 * @doc.purpose Canvas document round-trip validation
 * @doc.layer canvas
 */

import { describe, it, expect } from 'vitest';
import {
  CanvasDocumentSchema,
  serializeCanvasDocument,
  deserializeCanvasDocument,
  validateCanvasDocument,
  migrateCanvasDocument,
  type CanvasDocument,
  type CanvasNode,
  type CanvasEdge,
} from '../canvas-document.schema';

describe('CanvasDocument Round-trip Tests', () => {
  describe('Schema Validation', () => {
    it('should validate a valid canvas document', () => {
      const doc: CanvasDocument = {
        schemaVersion: '1.0.0',
        documentId: 'test-doc-1',
        name: 'Test Document',
        description: 'A test document',
        elements: {
          'node-1': {
            id: 'node-1',
            type: 'node',
            position: { x: 100, y: 100 },
            size: { width: 200, height: 100 },
            rotation: 0,
            locked: false,
            hidden: false,
            data: {
              label: 'Node 1',
              contractName: 'Button',
            },
          },
        },
        viewport: { x: 0, y: 0, zoom: 1 },
        metadata: {
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
          author: 'test-user',
        },
      };

      const result = CanvasDocumentSchema.safeParse(doc);
      expect(result.success).toBe(true);
    });

    it('should reject invalid canvas document', () => {
      const invalidDoc = {
        schemaVersion: '1.0.0',
        documentId: 'test-doc-1',
        // Missing required fields
      };

      const result = CanvasDocumentSchema.safeParse(invalidDoc);
      expect(result.success).toBe(false);
    });
  });

  describe('Serialization/Deserialization', () => {
    it('should serialize and deserialize without data loss', () => {
      const original: CanvasDocument = {
        schemaVersion: '1.0.0',
        documentId: 'test-doc-1',
        name: 'Test Document',
        elements: {
          'node-1': {
            id: 'node-1',
            type: 'node',
            position: { x: 100, y: 100 },
            size: { width: 200, height: 100 },
            data: {
              label: 'Node 1',
              contractName: 'Button',
              props: { color: 'blue' },
            },
          },
          'edge-1': {
            id: 'edge-1',
            type: 'edge',
            source: 'node-1',
            target: 'node-2',
            position: { x: 0, y: 0 },
            data: {
              label: 'Edge 1',
              slot: 'default',
            },
          },
        },
        viewport: { x: 50, y: 50, zoom: 1.5 },
        metadata: {
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
      };

      const serialized = serializeCanvasDocument(original);
      const deserialized = deserializeCanvasDocument(serialized);

      expect(deserialized).toEqual(original);
    });

    it('should preserve complex nested structures', () => {
      const original: CanvasDocument = {
        schemaVersion: '1.0.0',
        documentId: 'test-doc-2',
        name: 'Complex Document',
        elements: {
          'node-1': {
            id: 'node-1',
            type: 'node',
            position: { x: 100, y: 100 },
            metadata: {
              customField: { nested: { value: 42 } },
            },
            data: {
              label: 'Node 1',
              props: {
                array: [1, 2, 3],
                object: { key: 'value' },
              },
            },
          },
        },
        viewport: { x: 0, y: 0, zoom: 1 },
        metadata: {
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
          tags: ['tag1', 'tag2'],
        },
      };

      const serialized = serializeCanvasDocument(original);
      const deserialized = deserializeCanvasDocument(serialized);

      expect(deserialized.metadata.tags).toEqual(['tag1', 'tag2']);
      expect(deserialized.elements['node-1'].metadata?.customField).toEqual({ nested: { value: 42 } });
    });
  });

  describe('Migration', () => {
    it('should migrate documents without schema version', () => {
      const legacyDoc = {
        documentId: 'legacy-doc',
        name: 'Legacy Document',
        elements: {},
        viewport: { x: 0, y: 0, zoom: 1 },
        metadata: {
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
      };

      const migrated = migrateCanvasDocument(legacyDoc);

      expect(migrated.schemaVersion).toBe('1.0.0');
      expect(migrated.documentId).toBe('legacy-doc');
    });

    it('should not migrate if already at target version', () => {
      const currentDoc: CanvasDocument = {
        schemaVersion: '1.0.0',
        documentId: 'current-doc',
        name: 'Current Document',
        elements: {},
        viewport: { x: 0, y: 0, zoom: 1 },
        metadata: {
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
      };

      const migrated = migrateCanvasDocument(currentDoc);

      expect(migrated).toEqual(currentDoc);
    });
  });

  describe('Validation', () => {
    it('should return valid result for correct document', () => {
      const doc: CanvasDocument = {
        schemaVersion: '1.0.0',
        documentId: 'test-doc',
        name: 'Test',
        elements: {},
        viewport: { x: 0, y: 0, zoom: 1 },
        metadata: {
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
      };

      const result = validateCanvasDocument(doc);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
      expect(result.document).toEqual(doc);
    });

    it('should return invalid result with errors for incorrect document', () => {
      const invalidDoc = {
        schemaVersion: '1.0.0',
        documentId: 'test-doc',
        // Missing required fields
      };

      const result = validateCanvasDocument(invalidDoc);

      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.document).toBeUndefined();
    });

    it('should validate viewport zoom constraints', () => {
      const invalidDoc: CanvasDocument = {
        schemaVersion: '1.0.0',
        documentId: 'test-doc',
        name: 'Test',
        elements: {},
        viewport: { x: 0, y: 0, zoom: 15 }, // Zoom exceeds max of 10
        metadata: {
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        },
      };

      const result = validateCanvasDocument(invalidDoc);

      expect(result.valid).toBe(false);
      expect(result.errors.some(e => e.includes('zoom'))).toBe(true);
    });
  });

  describe('Element Type Discrimination', () => {
    it('should correctly identify node elements', () => {
      const node: CanvasNode = {
        id: 'node-1',
        type: 'node',
        position: { x: 0, y: 0 },
        data: { label: 'Node' },
      };

      const result = CanvasDocumentSchema.shape.elements.elementSchema.safeParse(node);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.type).toBe('node');
      }
    });

    it('should correctly identify edge elements', () => {
      const edge: CanvasEdge = {
        id: 'edge-1',
        type: 'edge',
        source: 'node-1',
        target: 'node-2',
        position: { x: 0, y: 0 },
        data: { label: 'Edge' },
      };

      const result = CanvasDocumentSchema.shape.elements.elementSchema.safeParse(edge);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.type).toBe('edge');
      }
    });
  });
});
