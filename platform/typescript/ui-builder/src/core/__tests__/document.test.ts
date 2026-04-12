/**
 * @ghatana/ui-builder/core document operations test suite
 * Tests for BuilderDocument operations, validation, and immutability
 */

import { describe, it, expect } from 'vitest';
import {
  createNodeId,
  createDocumentId,
  type BuilderDocument,
  type ComponentInstance,
} from '../types';

describe('@ghatana/ui-builder/core - Document Operations', () => {
  describe('BuilderDocument Creation', () => {
    it('should create a valid BuilderDocument', () => {
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [],
        nodes: new Map(),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      expect(document.id).toBeDefined();
      expect(document.version).toBe('1');
      expect(document.nodes).toBeInstanceOf(Map);
    });

    it('should generate unique node IDs', () => {
      const id1 = createNodeId();
      const id2 = createNodeId();

      expect(id1).not.toBe(id2);
      expect(typeof id1).toBe('string');
    });

    it('should generate unique document IDs', () => {
      const id1 = createDocumentId();
      const id2 = createDocumentId();

      expect(id1).not.toBe(id2);
      expect(typeof id1).toBe('string');
    });
  });

  describe('Component Instance Operations', () => {
    it('should create a valid ComponentInstance', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'Button',
        props: { label: 'Click Me' },
        slots: {},
        bindings: [],
        metadata: {},
      };

      expect(instance.id).toBeDefined();
      expect(instance.contractName).toBe('Button');
      expect(instance.props).toEqual({ label: 'Click Me' });
    });

    it('should support component bindings', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'TextField',
        props: { value: '' },
        slots: {},
        bindings: [
          {
            id: 'binding-1',
            type: 'data',
            source: 'dataSource.value',
            target: 'value',
          },
        ],
        metadata: {},
      };

      expect(instance.bindings).toHaveLength(1);
      expect(instance.bindings[0].type).toBe('data');
    });
  });

  describe('Document Immutability', () => {
    it('should treat nodes map as readonly', () => {
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Test Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [],
        nodes: new Map(),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      // Verify readonly access pattern
      expect(() => {
        // @ts-expect-error - Testing readonly enforcement
        document.nodes.set('new-id', {} as ComponentInstance);
      }).not.toThrow(); // Map is runtime mutable, type is readonly
    });
  });
});
