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
import { validateDocument } from '../validation';
import type { ComponentContract } from '@ghatana/ds-schema';

// ============================================================================
// Helpers
// ============================================================================

function makeDoc(overrides: Partial<BuilderDocument> = {}): BuilderDocument {
  return {
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
    ...overrides,
  };
}

function makeInstance(id = createNodeId(), contractName = 'Button', props: Record<string, unknown> = {}): ComponentInstance {
  return { id, contractName, props, slots: {}, bindings: [], metadata: { layout: {} } };
}

/** Minimal valid ComponentContract for testing. */
function makeContract(name: string, overrides: Partial<ComponentContract> = {}): ComponentContract {
  return {
    name,
    description: 'Test contract',
    version: '1.0.0',
    props: [],
    slots: [],
    events: [],
    styles: [],
    a11y: { role: 'none', ariaRequired: [], keyboardNavigation: false, wcagCriteria: [] },
    layout: { layoutType: 'block', canContainChildren: false, fillsParent: false, forbiddenAncestors: [] },
    builder: { draggable: true, resizable: false, rotatable: false, hasConfigPanel: false, supportsDataBinding: false, supportsStateVariants: false, supportsResponsiveVariants: false },
    codegen: { framework: 'react', importPath: '@ghatana/ds', componentName: name, tagName: name.toLowerCase() },
    metadata: { category: 'test', tags: [], since: '1.0.0', deprecated: false },
    aiPolicy: { permittedActions: [], reviewRequiredProps: [] },
    ...overrides,
  };
}

describe('@ghatana/ui-builder/core - Validation', () => {
  describe('Document Validation', () => {
    it('should validate document structure', () => {
      const document = makeDoc();
      expect(document.id).toBeTruthy();
      expect(document.version).toBeTruthy();
      expect(document.name).toBeTruthy();
      expect(document.designSystem).toBeTruthy();
    });

    it('should validate root nodes exist in nodes map', () => {
      const nodeId = createNodeId();
      const document = makeDoc({
        rootNodes: [nodeId],
        nodes: new Map([[nodeId, makeInstance(nodeId)]]),
      });
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
      const document = makeDoc();
      expect(document.designSystem.id).toBeTruthy();
      expect(document.designSystem.name).toBeTruthy();
      expect(document.designSystem.version).toBeTruthy();
      expect(document.designSystem.themeId).toBeTruthy();
    });
  });

  // ============================================================================
  // validateDocument() — new rules
  // ============================================================================

  describe('validateDocument — MISSING_CONTRACT', () => {
    it('errors when a node references a contract not in the contracts map', () => {
      const node = makeInstance(createNodeId(), 'Ghost');
      const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
      const result = validateDocument(doc, new Map());
      expect(result.valid).toBe(false);
      expect(result.errors.some((e) => e.code === 'MISSING_CONTRACT')).toBe(true);
    });
  });

  describe('validateDocument — MISSING_REQUIRED_PROP', () => {
    it('errors when a required prop is absent', () => {
      const node = makeInstance(createNodeId(), 'Button');
      const contract = makeContract('Button', {
        props: [{ name: 'label', type: 'string', required: true, description: '', defaultValue: undefined, tokenTypes: undefined, aiPolicy: { permittedActions: [] } }],
      });
      const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
      const result = validateDocument(doc, new Map([['Button', contract]]));
      expect(result.errors.some((e) => e.code === 'MISSING_REQUIRED_PROP')).toBe(true);
    });

    it('passes when all required props are present', () => {
      const node = makeInstance(createNodeId(), 'Button', { label: 'Hello' });
      const contract = makeContract('Button', {
        props: [{ name: 'label', type: 'string', required: true, description: '', defaultValue: undefined, tokenTypes: undefined, aiPolicy: { permittedActions: [] } }],
      });
      const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
      const result = validateDocument(doc, new Map([['Button', contract]]));
      expect(result.errors.filter((e) => e.code === 'MISSING_REQUIRED_PROP')).toHaveLength(0);
    });
  });

  describe('validateDocument — SLOT constraints', () => {
    it('errors when a slot exceeds maxChildren', () => {
      const parent = makeInstance(createNodeId(), 'Container');
      const child1 = makeInstance(createNodeId(), 'Item');
      const child2 = makeInstance(createNodeId(), 'Item');
      parent.slots = { default: [child1.id, child2.id] };
      const contract = makeContract('Container', {
        slots: [{ name: 'default', maxChildren: 1, allowedComponents: [], minChildren: 0 }],
      });
      const itemContract = makeContract('Item');
      const doc = makeDoc({
        rootNodes: [parent.id],
        nodes: new Map([[parent.id, parent], [child1.id, child1], [child2.id, child2]]),
      });
      const contracts = new Map([['Container', contract], ['Item', itemContract]]);
      const result = validateDocument(doc, contracts);
      expect(result.errors.some((e) => e.code === 'SLOT_MAX_CHILDREN')).toBe(true);
    });

    it('errors when a disallowed component is placed in a slot', () => {
      const parent = makeInstance(createNodeId(), 'Container');
      const child = makeInstance(createNodeId(), 'ForbiddenWidget');
      parent.slots = { default: [child.id] };
      const contract = makeContract('Container', {
        slots: [{ name: 'default', allowedComponents: ['AllowedWidget'], minChildren: 0 }],
      });
      const doc = makeDoc({
        rootNodes: [parent.id],
        nodes: new Map([[parent.id, parent], [child.id, child]]),
      });
      const contracts = new Map([['Container', contract], ['ForbiddenWidget', makeContract('ForbiddenWidget')]]);
      const result = validateDocument(doc, contracts);
      expect(result.errors.some((e) => e.code === 'DISALLOWED_COMPONENT')).toBe(true);
    });
  });

  describe('validateDocument — ORPHANED_NODE', () => {
    it('warns when a node is not reachable from root', () => {
      const orphan = makeInstance(createNodeId(), 'Button');
      const doc = makeDoc({
        rootNodes: [],
        nodes: new Map([[orphan.id, orphan]]),
      });
      const result = validateDocument(doc, new Map([['Button', makeContract('Button')]]));
      expect(result.warnings.some((w) => w.code === 'ORPHANED_NODE')).toBe(true);
    });
  });

  describe('validateDocument — responsive consistency warnings', () => {
    it('warns when a variant overrides a prop not in responsiveProps', () => {
      const node = makeInstance(createNodeId(), 'Text');
      node.metadata = { layout: {}, responsiveVariants: [{ breakpoint: 'md', props: { forbidden: 'val' } }] };
      const contract = makeContract('Text', {
        responsive: {
          isResponsive: true,
          breakpoints: ['md'],
          responsiveProps: ['size'],
          supportsContainerQuery: false,
          responsiveScale: 'none',
        },
      });
      const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
      const result = validateDocument(doc, new Map([['Text', contract]]));
      expect(result.warnings.some((w) => w.code === 'RESPONSIVE_PROP_NOT_DECLARED')).toBe(true);
    });

    it('does not warn when there are no responsiveProps restrictions', () => {
      const node = makeInstance(createNodeId(), 'Text');
      node.metadata = { layout: {}, responsiveVariants: [{ breakpoint: 'md', props: { anything: 'val' } }] };
      const contract = makeContract('Text', {
        responsive: {
          isResponsive: true,
          breakpoints: ['md'],
          responsiveProps: [],
          supportsContainerQuery: false,
          responsiveScale: 'none',
        },
      });
      const doc = makeDoc({ rootNodes: [node.id], nodes: new Map([[node.id, node]]) });
      const result = validateDocument(doc, new Map([['Text', contract]]));
      expect(result.warnings.some((w) => w.code === 'RESPONSIVE_PROP_NOT_DECLARED')).toBe(false);
    });
  });

  describe('validateDocument — trust policy', () => {
    it('errors when document trust level is below component minimum', () => {
      const node = makeInstance(createNodeId(), 'SecureWidget');
      const contract = makeContract('SecureWidget', {
        preview: {
          minimumTrustLevel: 'trusted-local',
          requiresNetwork: false,
          requiresStorage: false,
          requiresConsent: false,
          allowedHosts: [],
          cspDirectives: {},
        },
      });
      const doc = makeDoc({
        rootNodes: [node.id],
        nodes: new Map([[node.id, node]]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          trustLevel: 'UNTRUSTED' as never,
        },
      });
      const result = validateDocument(doc, new Map([['SecureWidget', contract]]));
      expect(result.errors.some((e) => e.code === 'TRUST_LEVEL_INSUFFICIENT')).toBe(true);
    });
  });
});


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
