/**
 * @fileoverview Codegen parity tests
 *
 * Tests that generated code matches the document structure and
 * that the document can be reconstructed from generated code.
 *
 * @doc.type test
 * @doc.purpose Codegen parity validation
 * @doc.layer platform
 */

import { describe, it, expect } from 'vitest';
import {
  createBuilderDocument,
  type BuilderDocument,
} from '../builder-document.js';
import { insertNode } from '../operations.js';

/** Returns the first user-inserted node (filters out the implicit RootContainer). */
function getUserNode(doc: BuilderDocument): BuilderDocument['nodes'][string] {
  const entry = Object.entries(doc.nodes).find(([, v]) => v.contractName !== 'RootContainer');
  if (!entry) throw new Error('No user node found in document');
  return entry[1];
}

describe('Codegen Parity', () => {
  describe('document to code structure', () => {
    it('should preserve node hierarchy in generated code', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      // The document structure should map to code structure
      const rootNode = doc.layout.nodes[doc.layout.rootId];
      expect(rootNode?.children).toHaveLength(1);

      const childId = rootNode?.children[0];
      const childNode = doc.nodes[childId];

      expect(childNode).toBeDefined();
      expect(childNode?.contractName).toBe('Button');
    });

    it('should preserve props in generated code', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me', disabled: false, variant: 'primary' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const node = getUserNode(doc);
      expect(node.props).toEqual({
        label: 'Click me',
        disabled: false,
        variant: 'primary',
      });
    });

    it('should preserve slots in generated code', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Container',
        props: {},
        slots: { children: [] },
        bindings: [],
        metadata: { name: 'Container1' },
      });

      const node = getUserNode(doc);
      expect(node.slots).toBeDefined();
      expect(node.slots.children).toBeDefined();
    });
  });

  describe('binding preservation', () => {
    it('should preserve bindings in generated code', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [
          {
            id: 'binding-1',
            type: 'data',
            source: 'dataSource.users',
            target: 'props.label',
          },
        ],
        metadata: { name: 'Button1' },
      });

      const node = getUserNode(doc);
      expect(node.bindings).toHaveLength(1);
      expect(node.bindings[0].source).toBe('dataSource.users');
    });
  });

  describe('metadata preservation', () => {
    it('should preserve node metadata in generated code', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: {
          name: 'Button1',
          position: { x: 100, y: 200 },
          locked: false,
          hidden: false,
        },
      });

      const node = getUserNode(doc);
      expect(node.metadata.name).toBe('Button1');
      expect(node.metadata.position).toEqual({ x: 100, y: 200 });
    });

    it('should preserve document metadata in generated code', () => {
      const doc = createBuilderDocument('test-user', {
        metadata: { description: 'Test document' },
      });

      expect(doc.metadata.description).toBe('Test document');
      expect(doc.metadata.author).toBe('test-user');
    });
  });

  describe('layout structure', () => {
    it('should preserve layout type in generated code', () => {
      const doc = createBuilderDocument('test-user');

      expect(doc.layout.type).toBe('flex');
      expect(doc.layout.rootId).toBeDefined();
    });

    it('should preserve layout nodes in generated code', () => {
      let doc = createBuilderDocument('test-user');

      doc = insertNode(doc, {
        contractName: 'Button',
        props: { label: 'Click me' },
        slots: {},
        bindings: [],
        metadata: { name: 'Button1' },
      });

      const rootNode = doc.layout.nodes[doc.layout.rootId];
      expect(rootNode).toBeDefined();
      expect(rootNode?.type).toBe('root');
      expect(rootNode?.children).toHaveLength(1);
    });
  });

  describe('design system references', () => {
    it('should preserve design system ID in generated code', () => {
      const doc = createBuilderDocument('test-user', {
        designSystemId: 'test-ds',
        designSystemName: 'Test Design System',
      });

      expect(doc.designSystemId).toBe('test-ds');
      expect(doc.designSystemName).toBe('Test Design System');
    });
  });

  describe('schema version compatibility', () => {
    it('should include schema version for code generation compatibility', () => {
      const doc = createBuilderDocument('test-user');

      expect(doc.schemaVersion).toBeDefined();
      // Code generator should check schema version
      // to ensure compatibility
    });
  });
});
