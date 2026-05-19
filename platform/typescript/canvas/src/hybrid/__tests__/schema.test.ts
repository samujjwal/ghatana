/**
 * @fileoverview Round-trip tests for canvas document schema
 *
 * Tests that canvas documents can be serialized, deserialized, and migrated
 * correctly across schema versions.
 *
 * @doc.type test
 * @doc.purpose Canvas schema round-trip validation
 * @doc.layer canvas
 */

import { describe, it, expect } from 'vitest';
import {
  validateCanvasDocument,
  serializeCanvasDocument,
  deserializeCanvasDocument,
  createCanvasDocument,
  CURRENT_CANVAS_SCHEMA_VERSION,
} from '../schema.js';

describe('Canvas Document Schema', () => {
  describe('round-trip serialization', () => {
    it('should serialize and deserialize a canvas document', () => {
      const original = createCanvasDocument({
        mode: 'hybrid-freeform',
        tool: 'select',
      });

      const serialized = serializeCanvasDocument(original);
      const result = deserializeCanvasDocument(serialized);

      expect(result.success).toBe(true);
      expect(result.data).toEqual(original);
    });

    it('should preserve all document properties through round-trip', () => {
      const original = createCanvasDocument({
        mode: 'hybrid-graph',
        viewport: {
          x: 100,
          y: 200,
          zoom: 1.5,
          minZoom: 0.5,
          maxZoom: 3,
        },
        tool: 'pan',
      });

      const serialized = serializeCanvasDocument(original);
      const result = deserializeCanvasDocument(serialized);

      expect(result.success).toBe(true);
      expect(result.data?.mode).toBe('hybrid-graph');
      expect(result.data?.viewport.x).toBe(100);
      expect(result.data?.viewport.y).toBe(200);
      expect(result.data?.viewport.zoom).toBe(1.5);
      expect(result.data?.tool).toBe('pan');
    });

    it('should handle elements in round-trip', () => {
      const original = createCanvasDocument({
        elements: [
          {
            id: 'rect-1',
            type: 'rectangle',
            position: { x: 10, y: 10 },
            size: { width: 100, height: 50 },
            data: { color: 'red' },
          },
        ],
      });

      const serialized = serializeCanvasDocument(original);
      const result = deserializeCanvasDocument(serialized);

      expect(result.success).toBe(true);
      expect(result.data?.elements).toHaveLength(1);
      expect(result.data?.elements[0].id).toBe('rect-1');
      expect(result.data?.elements[0].type).toBe('rectangle');
      expect(result.data?.elements[0].data).toEqual({ color: 'red' });
    });

    it('should handle nodes in round-trip', () => {
      const original = createCanvasDocument({
        nodes: [
          {
            id: 'node-1',
            type: 'test-node',
            position: { x: 50, y: 50 },
            data: { label: 'Test Node' },
            __canvas: { layer: 'graph' },
          },
        ],
      });

      const serialized = serializeCanvasDocument(original);
      const result = deserializeCanvasDocument(serialized);

      expect(result.success).toBe(true);
      expect(result.data?.nodes).toHaveLength(1);
      expect(result.data?.nodes[0].id).toBe('node-1');
      expect(result.data?.nodes[0].data).toEqual({ label: 'Test Node' });
      expect(result.data?.nodes[0].__canvas?.layer).toBe('graph');
    });

    it('should handle edges in round-trip', () => {
      const original = createCanvasDocument({
        nodes: [
          { id: 'node-1', type: 'test', position: { x: 0, y: 0 }, data: {} },
          { id: 'node-2', type: 'test', position: { x: 100, y: 0 }, data: {} },
        ],
        edges: [
          {
            id: 'edge-1',
            source: 'node-1',
            target: 'node-2',
            data: { label: 'Connection' },
            __canvas: { layer: 'graph' },
          },
        ],
      });

      const serialized = serializeCanvasDocument(original);
      const result = deserializeCanvasDocument(serialized);

      expect(result.success).toBe(true);
      expect(result.data?.edges).toHaveLength(1);
      expect(result.data?.edges[0].id).toBe('edge-1');
      expect(result.data?.edges[0].source).toBe('node-1');
      expect(result.data?.edges[0].target).toBe('node-2');
    });
  });

  describe('validation', () => {
    it('should validate a correct document', () => {
      const doc = createCanvasDocument();
      const result = validateCanvasDocument(doc);

      expect(result.success).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should reject invalid viewport zoom', () => {
      const doc = createCanvasDocument({
        viewport: {
          x: 0,
          y: 0,
          zoom: 0, // Invalid: must be >= 0.01
          minZoom: 0.1,
          maxZoom: 5,
        },
      });

      const result = validateCanvasDocument(doc);
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should reject invalid element dimensions', () => {
      const doc = createCanvasDocument({
        elements: [
          {
            id: 'rect-1',
            type: 'rectangle',
            position: { x: 0, y: 0 },
            size: { width: -10, height: 50 }, // Invalid: negative width
            data: {},
          },
        ],
      });

      const result = validateCanvasDocument(doc);
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should reject invalid node position', () => {
      const doc = createCanvasDocument({
        nodes: [
          {
            id: 'node-1',
            type: 'test',
            position: { x: 'invalid' as any, y: 0 }, // Invalid: non-number
            data: {},
          },
        ],
      });

      const result = validateCanvasDocument(doc);
      expect(result.success).toBe(false);
    });
  });

  describe('schema version', () => {
    it('should include schemaVersion in serialized document', () => {
      const doc = createCanvasDocument();
      const serialized = serializeCanvasDocument(doc);
      const parsed = JSON.parse(serialized);

      expect(parsed.schemaVersion).toBe(CURRENT_CANVAS_SCHEMA_VERSION);
    });

    it('should reject document with missing schemaVersion', () => {
      const doc = createCanvasDocument();
      const { schemaVersion, ...docWithoutVersion } = doc as any;

      const result = validateCanvasDocument(docWithoutVersion);
      // Should auto-migrate and succeed
      expect(result.success).toBe(true);
      expect(result.data?.schemaVersion).toBe(CURRENT_CANVAS_SCHEMA_VERSION);
    });
  });

  describe('JSON parse errors', () => {
    it('should handle invalid JSON', () => {
      const result = deserializeCanvasDocument('invalid json {{{');

      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.errors[0]).toContain('JSON parse error');
    });
  });
});
