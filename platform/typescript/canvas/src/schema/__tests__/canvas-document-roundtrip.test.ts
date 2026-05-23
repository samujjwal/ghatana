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
  CanvasElementSchema,
  createCanvasDocument,
  deserializeCanvasDocument,
  migrateCanvasDocument,
  serializeCanvasDocument,
  validateCanvasDocument,
  type CanvasDocument,
  type CanvasEdge,
  type CanvasGroup,
  type CanvasNode,
} from '../canvas-document.schema';

function createValidDocument(overrides: Partial<CanvasDocument> = {}): CanvasDocument {
  return {
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
          props: { color: 'blue' },
        },
      },
      'node-2': {
        id: 'node-2',
        type: 'node',
        position: { x: 420, y: 100 },
        rotation: 0,
        locked: false,
        hidden: false,
        data: {
          label: 'Node 2',
        },
      },
      'edge-1': {
        id: 'edge-1',
        type: 'edge',
        source: 'node-1',
        target: 'node-2',
        position: { x: 0, y: 0 },
        rotation: 0,
        locked: false,
        hidden: false,
        data: {
          label: 'Edge 1',
          slot: 'default',
        },
      },
      'group-1': {
        id: 'group-1',
        type: 'group',
        position: { x: 80, y: 80 },
        size: { width: 640, height: 260 },
        rotation: 0,
        locked: false,
        hidden: false,
        children: ['node-1', 'node-2'],
        collapsed: false,
        data: {
          label: 'Primary group',
        },
      },
    },
    viewport: { x: 50, y: 50, zoom: 1.5 },
    selection: {
      elementIds: ['node-1', 'group-1'],
      nodeIds: ['node-1'],
      edgeIds: ['edge-1'],
      groupIds: ['group-1'],
    },
    layers: {
      foreground: {
        id: 'foreground',
        name: 'Foreground',
        visible: true,
        locked: false,
        zIndex: 10,
        elementIds: ['group-1', 'node-1', 'node-2', 'edge-1'],
      },
    },
    metadata: {
      createdAt: '2024-01-01T00:00:00Z',
      updatedAt: '2024-01-01T00:00:00Z',
      author: 'test-user',
      tags: ['tag1', 'tag2'],
    },
    ...overrides,
  };
}

describe('CanvasDocument Round-trip Tests', () => {
  describe('Schema Validation', () => {
    it('validates nodes, edges, groups, selections, viewport, and layers', () => {
      const result = CanvasDocumentSchema.safeParse(createValidDocument());

      expect(result.success).toBe(true);
    });

    it('rejects invalid canvas documents before render/edit', () => {
      const invalidDoc = {
        schemaVersion: '1.0.0',
        documentId: 'test-doc-1',
      };

      const result = CanvasDocumentSchema.safeParse(invalidDoc);

      expect(result.success).toBe(false);
    });

    it('rejects edges that reference missing nodes', () => {
      const doc = createValidDocument({
        elements: {
          ...createValidDocument().elements,
          'edge-1': {
            ...createValidDocument().elements['edge-1'] as CanvasEdge,
            target: 'missing-node',
          },
        },
      });

      const result = validateCanvasDocument(doc);

      expect(result.valid).toBe(false);
      expect(result.errors.some((error) => error.includes('missing-node'))).toBe(true);
    });

    it('rejects groups, layers, and selections with stale element ids', () => {
      const base = createValidDocument();
      const doc = createValidDocument({
        elements: {
          ...base.elements,
          'group-1': {
            ...base.elements['group-1'] as CanvasGroup,
            children: ['node-1', 'missing-child'],
          },
        },
        layers: {
          foreground: {
            ...base.layers.foreground,
            elementIds: ['node-1', 'missing-layer-element'],
          },
        },
        selection: {
          ...base.selection,
          nodeIds: ['missing-selected-node'],
        },
      });

      const result = validateCanvasDocument(doc);

      expect(result.valid).toBe(false);
      expect(result.errors.some((error) => error.includes('missing-child'))).toBe(true);
      expect(result.errors.some((error) => error.includes('missing-layer-element'))).toBe(true);
      expect(result.errors.some((error) => error.includes('missing-selected-node'))).toBe(true);
    });
  });

  describe('Serialization/Deserialization', () => {
    it('serializes and deserializes without data loss', () => {
      const original = createValidDocument();

      const serialized = serializeCanvasDocument(original);
      const deserialized = deserializeCanvasDocument(serialized);

      expect(deserialized).toEqual(original);
    });

    it('preserves complex nested structures', () => {
      const original = createValidDocument({
        elements: {
          ...createValidDocument().elements,
          'node-1': {
            ...createValidDocument().elements['node-1'] as CanvasNode,
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
      });

      const serialized = serializeCanvasDocument(original);
      const deserialized = deserializeCanvasDocument(serialized);

      expect(deserialized.metadata.tags).toEqual(['tag1', 'tag2']);
      expect(deserialized.elements['node-1'].metadata?.customField).toEqual({ nested: { value: 42 } });
    });
  });

  describe('Migration', () => {
    it('migrates documents without schema version into the current runtime shape', () => {
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
      expect(migrated.selection).toEqual({
        elementIds: [],
        nodeIds: [],
        edgeIds: [],
        groupIds: [],
      });
      expect(migrated.layers).toEqual({});
    });

    it('does not migrate if already at target version', () => {
      const currentDoc = createValidDocument({ documentId: 'current-doc', name: 'Current Document' });

      const migrated = migrateCanvasDocument(currentDoc);

      expect(migrated).toEqual(currentDoc);
    });

    it('fails safely when no migration path exists', () => {
      const futureDoc = createValidDocument({ schemaVersion: '9.0.0' });

      expect(() => migrateCanvasDocument(futureDoc)).toThrow('No canvas document migration');
    });
  });

  describe('Validation', () => {
    it('returns valid result for correct documents', () => {
      const doc = createValidDocument();

      const result = validateCanvasDocument(doc);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
      expect(result.document).toEqual(doc);
    });

    it('returns invalid result with errors for incorrect documents', () => {
      const invalidDoc = {
        schemaVersion: '1.0.0',
        documentId: 'test-doc',
      };

      const result = validateCanvasDocument(invalidDoc);

      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.document).toBeUndefined();
    });

    it('validates viewport zoom constraints', () => {
      const result = validateCanvasDocument(createValidDocument({
        viewport: { x: 0, y: 0, zoom: 15 },
      }));

      expect(result.valid).toBe(false);
      expect(result.errors.some((error) => error.includes('zoom'))).toBe(true);
    });
  });

  describe('Element Type Discrimination', () => {
    it('identifies node elements', () => {
      const node: CanvasNode = createValidDocument().elements['node-1'] as CanvasNode;

      const result = CanvasElementSchema.safeParse(node);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.type).toBe('node');
      }
    });

    it('identifies edge elements', () => {
      const edge: CanvasEdge = createValidDocument().elements['edge-1'] as CanvasEdge;

      const result = CanvasElementSchema.safeParse(edge);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.type).toBe('edge');
      }
    });

    it('identifies group elements', () => {
      const group: CanvasGroup = createValidDocument().elements['group-1'] as CanvasGroup;

      const result = CanvasElementSchema.safeParse(group);

      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.type).toBe('group');
      }
    });
  });

  describe('Factory', () => {
    it('creates renderable empty documents with current schema defaults', () => {
      const document = createCanvasDocument('empty-doc', 'Empty');

      expect(validateCanvasDocument(document).valid).toBe(true);
      expect(document.selection.nodeIds).toEqual([]);
      expect(document.layers).toEqual({});
    });
  });
});
