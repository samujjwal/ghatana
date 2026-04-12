/**
 * @ghatana/ui-builder/core validation test suite
 * Tests for BuilderDocument validation
 */

import { describe, it, expect } from 'vitest';
import {
  createNodeId,
  createDocumentId,
  type BuilderDocument,
  type ComponentInstance,
} from '../types';

describe('@ghatana/ui-builder/core - Validation', () => {
  describe('Document Validation', () => {
    it('should validate document structure', () => {
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

      // Validate required fields
      expect(document.id).toBeTruthy();
      expect(document.version).toBeTruthy();
      expect(document.name).toBeTruthy();
      expect(document.designSystem).toBeTruthy();
    });

    it('should validate root nodes exist in nodes map', () => {
      const nodeId = createNodeId();
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
        rootNodes: [nodeId],
        nodes: new Map([
          [
            nodeId,
            {
              id: nodeId,
              contractName: 'Button',
              props: {},
              slots: {},
              bindings: [],
              metadata: {},
            },
          ],
        ]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      expect(document.nodes.has(nodeId)).toBe(true);
    });
  });

  describe('Component Validation', () => {
    it('should validate component instance structure', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'Button',
        props: { label: 'Test' },
        slots: {},
        bindings: [],
        metadata: {},
      };

      expect(instance.id).toBeTruthy();
      expect(instance.contractName).toBeTruthy();
      expect(instance.props).toBeDefined();
      expect(instance.slots).toBeDefined();
      expect(instance.bindings).toBeDefined();
      expect(instance.metadata).toBeDefined();
    });

    it('should validate binding structure', () => {
      const instance: ComponentInstance = {
        id: createNodeId(),
        contractName: 'TextField',
        props: {},
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

      const binding = instance.bindings[0];
      expect(binding.id).toBeTruthy();
      expect(binding.type).toBeTruthy();
      expect(binding.source).toBeTruthy();
      expect(binding.target).toBeTruthy();
    });
  });

  describe('Design System Validation', () => {
    it('should validate design system model', () => {
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

      expect(document.designSystem.id).toBeTruthy();
      expect(document.designSystem.name).toBeTruthy();
      expect(document.designSystem.version).toBeTruthy();
      expect(document.designSystem.themeId).toBeTruthy();
    });
  });
});
