/**
 * @ghatana/ui-builder/core document operations test suite
 * Tests for BuilderDocument operations, validation, and immutability
 *
 * @test.type unit
 * @test.execution <100ms
 * @test.infra none
 */

import { describe, it, expect } from 'vitest';
import {
  createNodeId,
  createDocumentId,
  type BuilderDocument,
  type ComponentInstance,
  type InstanceMetadata,
  type DocumentMetadata,
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

  describe('createNodeId', () => {
    it('should generate a unique ID with no seed', () => {
      const id1 = createNodeId();
      const id2 = createNodeId();
      expect(id1).not.toBe(id2);
    });

    it('should use the provided seed when supplied', () => {
      const id = createNodeId('my-seed');
      expect(id).toBe('my-seed');
    });
  });

  describe('V2 InstanceMetadata fields', () => {
    it('should accept layout constraints in metadata', () => {
      const meta: InstanceMetadata = {
        layout: {
          position: 'absolute',
          top: '10px',
          left: '20px',
          width: '200px',
          height: '100px',
        },
      };
      expect(meta.layout?.position).toBe('absolute');
    });

    it('should accept responsive variants in metadata', () => {
      const nodeId = createNodeId();
      const meta: InstanceMetadata = {
        responsiveVariants: [
          {
            breakpoint: 'md',
            overrideProps: { size: 'small' },
          },
        ],
      };
      expect(meta.responsiveVariants?.[0]?.breakpoint).toBe('md');
      void nodeId; // used only for type context
    });

    it('should accept review status in metadata', () => {
      const meta: InstanceMetadata = {
        reviewStatus: {
          kind: 'approved',
          reviewedBy: 'alice',
          reviewedAt: new Date().toISOString(),
        },
      };
      expect(meta.reviewStatus?.kind).toBe('approved');
    });

    it('should accept aiLineage in metadata', () => {
      const meta: InstanceMetadata = {
        aiLineage: [
          {
            changeType: 'generated',
            model: 'gpt-4',
            prompt: 'add a button',
            confidence: 0.9,
            timestamp: new Date().toISOString(),
          },
        ],
      };
      expect(meta.aiLineage?.[0]?.model).toBe('gpt-4');
    });
  });

  describe('V2 DocumentMetadata fields', () => {
    it('should accept changeCount in document metadata', () => {
      const meta: DocumentMetadata = {
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        changeCount: 42,
      };
      expect(meta.changeCount).toBe(42);
    });

    it('should accept dataClassification in document metadata', () => {
      const meta: DocumentMetadata = {
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        dataClassification: 'internal',
      };
      expect(meta.dataClassification).toBe('internal');
    });

    it('should accept reviewStatus in document metadata', () => {
      const meta: DocumentMetadata = {
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        reviewStatus: { kind: 'pending' },
      };
      expect(meta.reviewStatus?.kind).toBe('pending');
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
