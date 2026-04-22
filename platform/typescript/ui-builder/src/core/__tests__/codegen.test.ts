/**
 * @ghatana/ui-builder/core codegen test suite
 * Tests for React code generation from BuilderDocument
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
  generateReactCode,
  type GenerateOptions,
} from '../index';

describe('@ghatana/ui-builder/core - Code Generation', () => {
  describe('React Code Generation', () => {
    it('should generate valid React code for simple document', () => {
      const buttonId = createNodeId();
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
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: { label: 'Click Me' },
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

      const contracts = new Map([
        [
          'Button',
          {
            name: 'Button',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'input',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/Button',
                componentName: 'Button',
              },
            },
          },
        ],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      expect(result.language).toBe('tsx');
      expect(result.files).toHaveLength(1);
      expect(result.files[0].path).toBe('TestComponent.tsx');
      expect(result.files[0].content).toContain('import * as React from');
      expect(result.files[0].content).toContain('// Builder platform targets: react, html, web-components');
      expect(result.files[0].content).toContain('export interface TestComponentProps');
      expect(result.files[0].content).toContain('export const TestComponent');
      expect(result.files[0].content).toContain('Button');
      expect(result.files[0].content).toContain('label="Click Me"');
    });

    it('should generate syntactically valid TypeScript code', () => {
      const buttonId = createNodeId();
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
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: { label: 'Test' },
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

      const contracts = new Map([
        [
          'Button',
          {
            name: 'Button',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'input',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/Button',
                componentName: 'Button',
              },
            },
          },
        ],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      // Verify TypeScript syntax by checking for proper patterns
      const content = result.files[0].content;
      expect(content).toMatch(/import \* as React from 'react';/);
      expect(content).toMatch(/export interface \w+Props/);
      expect(content).toMatch(/export const \w+: React\.FC<\w+Props>/);
      expect(content).toMatch(/return \(/);
      expect(content).toMatch(/\);/);
      expect(content).toMatch(/export default \w+;/);
    });

    it('should generate code with correct component imports', () => {
      const buttonId = createNodeId();
      const textFieldId = createNodeId();
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
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: { label: 'Click' },
              slots: {},
              bindings: [],
              metadata: {},
            },
          ],
          [
            textFieldId,
            {
              id: textFieldId,
              contractName: 'TextField',
              props: { value: '' },
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

      const contracts = new Map([
        [
          'Button',
          {
            name: 'Button',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'input',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/Button',
                componentName: 'Button',
              },
            },
          },
        ],
        [
          'TextField',
          {
            name: 'TextField',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'input',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/TextField',
                componentName: 'TextField',
              },
            },
          },
        ],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      const content = result.files[0].content;
      expect(content).toContain("import { Button } from '@ghatana/design-system/Button';");
      expect(content).toContain("import { TextField } from '@ghatana/design-system/TextField';");
    });

    it('should generate JSX with correct props', () => {
      const buttonId = createNodeId();
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
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: {
                label: 'Submit',
                variant: 'primary',
                disabled: false,
              },
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

      const contracts = new Map([
        [
          'Button',
          {
            name: 'Button',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'input',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/Button',
                componentName: 'Button',
              },
            },
          },
        ],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      const content = result.files[0].content;
      expect(content).toContain('label="Submit"');
      expect(content).toContain('variant="primary"');
      expect(content).toContain('disabled={false}');
    });

    it('should generate JSX with nested children from slots', () => {
      const cardId = createNodeId();
      const buttonId = createNodeId();
      const textId = createNodeId();
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
        rootNodes: [cardId],
        nodes: new Map([
          [
            cardId,
            {
              id: cardId,
              contractName: 'Card',
              props: { title: 'Test Card' },
              slots: {
                header: [buttonId],
                content: [textId],
              },
              bindings: [],
              metadata: {},
            },
          ],
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: { label: 'Action' },
              slots: {},
              bindings: [],
              metadata: {},
            },
          ],
          [
            textId,
            {
              id: textId,
              contractName: 'Typography',
              props: { variant: 'body', children: 'Test text' },
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

      const contracts = new Map([
        [
          'Card',
          {
            name: 'Card',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'layout',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/Card',
                componentName: 'Card',
              },
            },
          },
        ],
        [
          'Button',
          {
            name: 'Button',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'input',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/Button',
                componentName: 'Button',
              },
            },
          },
        ],
        [
          'Typography',
          {
            name: 'Typography',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'display',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/Typography',
                componentName: 'Typography',
              },
            },
          },
        ],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      const content = result.files[0].content;
      expect(content).toContain('<Card');
      expect(content).toContain('</Card>');
      expect(content).toContain('<Button');
      // Button has no slot children — it renders self-closing
      expect(content).not.toContain('</Button>');
      expect(content).toContain('<Typography');
      // Typography has no slot children — it renders self-closing
      expect(content).not.toContain('</Typography>');
      expect(content).toContain('header slot');
      expect(content).toContain('content slot');
    });

    it('should track per-root-node ownership in the code projection', () => {
      const buttonId = createNodeId();
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
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: { label: 'Test' },
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

      const contracts = new Map([
        [
          'Button',
          {
            name: 'Button',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'input',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/Button',
                componentName: 'Button',
              },
            },
          },
        ],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      // One root node → one per-root-node ownership region.
      expect(result.ownership).toHaveLength(1);
      // Region is keyed by root node id.
      expect(result.ownership[0].region).toBe(`node-${buttonId}`);
      expect(result.ownership[0].type).toBe('builder-generated');
      expect(result.ownership[0].builderNodeIds).toContain(buttonId);
      // JSX starts after imports + component scaffold, so lineStart > 1.
      expect(result.ownership[0].lineStart).toBeGreaterThan(1);
      expect(result.ownership[0].lineEnd).toBeGreaterThanOrEqual(result.ownership[0].lineStart);
    });

    it('should detect loss points for custom code components', () => {
      const buttonId = createNodeId();
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
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'CustomButton',
              props: { label: 'Test' },
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

      const contracts = new Map([
        [
          'CustomButton',
          {
            name: 'CustomButton',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'input',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: './custom/Button',
                componentName: 'CustomButton',
              },
            },
          },
        ],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      expect(result.roundTripFidelity.canRoundTrip).toBe(false);
      expect(result.roundTripFidelity.lossPoints).toHaveLength(1);
      expect(result.roundTripFidelity.lossPoints[0].type).toBe('custom-code');
      expect(result.roundTripFidelity.lossPoints[0].location).toBe(buttonId);
      expect(result.roundTripFidelity.confidence).toBe(0.8);
    });

    it('should handle empty documents', () => {
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Empty Document',
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

      const contracts = new Map();
      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'EmptyComponent',
      };

      const result = generateReactCode(document, contracts, options);

      expect(result.files).toHaveLength(1);
      expect(result.files[0].content).toContain('return (');
      expect(result.files[0].content).toContain(');');
      expect(result.roundTripFidelity.canRoundTrip).toBe(true);
      expect(result.roundTripFidelity.confidence).toBe(1.0);
    });

    it('should handle documents with circular references (self-referential)', () => {
      const buttonId = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Circular Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: { label: 'Test' },
              slots: {
                content: [buttonId], // Self-reference
              },
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

      const contracts = new Map([
        [
          'Button',
          {
            name: 'Button',
            version: '1.0.0',
            props: [],
            slots: [],
            events: [],
            styles: {},
            metadata: {
              category: 'input',
              status: 'stable',
              platforms: ['web'],
            },
            builder: {
              codegen: {
                importPath: '@ghatana/design-system/Button',
                componentName: 'Button',
              },
            },
          },
        ],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'CircularComponent',
      };

      // Should not throw, but handle gracefully
      const result = generateReactCode(document, contracts, options);

      expect(result.files).toHaveLength(1);
      expect(result.files[0].content).toContain('Button');
    });

    it('should handle documents with invalid component contracts', () => {
      const buttonId = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Invalid Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'NonExistentComponent',
              props: { label: 'Test' },
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

      const contracts = new Map();
      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'InvalidComponent',
      };

      // Should not throw, use fallback component name
      const result = generateReactCode(document, contracts, options);

      expect(result.files).toHaveLength(1);
      expect(result.files[0].content).toContain('NonExistentComponent');
      // Missing contract → unsupported-pattern loss point.
      expect(result.roundTripFidelity.lossPoints).toHaveLength(1);
      expect(result.roundTripFidelity.lossPoints[0].type).toBe('unsupported-pattern');
      expect(result.roundTripFidelity.canRoundTrip).toBe(false);
    });

    it('should detect loss points for user-authored node ownership', () => {
      const buttonId = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'User-Authored Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: { label: 'Test' },
              slots: {},
              bindings: [],
              metadata: { ownership: 'user-authored' },
            },
          ],
        ]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      const contracts = new Map([
        ['Button', {
          name: 'Button',
          version: '1.0.0',
          props: [],
          slots: [],
          events: [],
          styles: {},
          metadata: { category: 'input', status: 'stable' as const, platforms: ['web' as const] },
          builder: { codegen: { importPath: '@ghatana/design-system', componentName: 'Button' } },
        }],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      const customCodeLossPoints = result.roundTripFidelity.lossPoints.filter(
        (lp) => lp.type === 'custom-code',
      );
      expect(customCodeLossPoints.length).toBeGreaterThanOrEqual(1);
      expect(customCodeLossPoints[0].location).toBe(buttonId);
      expect(result.roundTripFidelity.canRoundTrip).toBe(false);
      expect(result.roundTripFidelity.confidence).toBeLessThan(1.0);
    });

    it('should detect loss points for nodes with active data bindings', () => {
      const buttonId = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Bound Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: { label: 'Dynamic' },
              slots: {},
              bindings: [
                { id: 'b1', type: 'data', source: 'store.label', target: 'label' },
              ],
              metadata: {},
            },
          ],
        ]),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      };

      const contracts = new Map([
        ['Button', {
          name: 'Button',
          version: '1.0.0',
          props: [],
          slots: [],
          events: [],
          styles: {},
          metadata: { category: 'input', status: 'stable' as const, platforms: ['web' as const] },
          builder: { codegen: { importPath: '@ghatana/design-system', componentName: 'Button' } },
        }],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      const bindingLossPoints = result.roundTripFidelity.lossPoints.filter(
        (lp) => lp.type === 'unsupported-pattern' && lp.location === buttonId,
      );
      expect(bindingLossPoints.length).toBeGreaterThanOrEqual(1);
      expect(result.roundTripFidelity.canRoundTrip).toBe(false);
    });

    it('should produce 1.0 confidence for a clean document with no loss points', () => {
      const buttonId = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Clean Document',
        designSystem: {
          id: 'test-ds',
          name: 'Test Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: [buttonId],
        nodes: new Map([
          [
            buttonId,
            {
              id: buttonId,
              contractName: 'Button',
              props: { label: 'Clean' },
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

      const contracts = new Map([
        ['Button', {
          name: 'Button',
          version: '1.0.0',
          props: [],
          slots: [],
          events: [],
          styles: {},
          metadata: { category: 'input', status: 'stable' as const, platforms: ['web' as const] },
          builder: { codegen: { importPath: '@ghatana/design-system', componentName: 'Button' } },
        }],
      ]);

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'TestComponent',
      };

      const result = generateReactCode(document, contracts, options);

      expect(result.roundTripFidelity.canRoundTrip).toBe(true);
      expect(result.roundTripFidelity.lossPoints).toHaveLength(0);
      expect(result.roundTripFidelity.confidence).toBe(1.0);
    });

    it('should produce a fallback ownership region for empty documents', () => {
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Empty Document',
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

      const options: GenerateOptions = {
        format: 'functional',
        typescript: true,
        importPath: '@ghatana/design-system',
        componentName: 'EmptyComponent',
      };

      const result = generateReactCode(document, new Map(), options);

      // Empty document → falls back to single 'component' region.
      expect(result.ownership).toHaveLength(1);
      expect(result.ownership[0].region).toBe('component');
      expect(result.ownership[0].builderNodeIds).toHaveLength(0);
    });

    it('should detect loss points for protected node ownership', () => {
      const nodeId = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Protected Document',
        designSystem: { id: 'ds', name: 'DS', version: '1.0.0', tokenSetIds: [], componentContracts: [], themeId: 'default' },
        rootNodes: [nodeId],
        nodes: new Map([[nodeId, { id: nodeId, contractName: 'Button', props: {}, slots: {}, bindings: [], metadata: { ownership: 'protected' } }]]),
        metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      };
      const contracts = new Map([['Button', { name: 'Button', version: '1.0.0', props: [], slots: [], events: [], styles: {}, metadata: { category: 'input', status: 'stable' as const, platforms: ['web' as const] }, builder: { codegen: { importPath: '@ghatana/design-system', componentName: 'Button' } } }]]);
      const options: GenerateOptions = { format: 'functional', typescript: true, importPath: '@ghatana/design-system', componentName: 'TestComponent' };

      const result = generateReactCode(document, contracts, options);

      const protectedLoss = result.roundTripFidelity.lossPoints.filter((lp) => lp.type === 'custom-code' && lp.location === nodeId);
      expect(protectedLoss.length).toBeGreaterThanOrEqual(1);
      expect(protectedLoss[0].description).toMatch(/protected/i);
      expect(result.roundTripFidelity.canRoundTrip).toBe(false);
    });

    it('should detect loss points for manual-merge-required node ownership', () => {
      const nodeId = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'ManualMerge Document',
        designSystem: { id: 'ds', name: 'DS', version: '1.0.0', tokenSetIds: [], componentContracts: [], themeId: 'default' },
        rootNodes: [nodeId],
        nodes: new Map([[nodeId, { id: nodeId, contractName: 'Button', props: {}, slots: {}, bindings: [], metadata: { ownership: 'manual-merge-required' } }]]),
        metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      };
      const contracts = new Map([['Button', { name: 'Button', version: '1.0.0', props: [], slots: [], events: [], styles: {}, metadata: { category: 'input', status: 'stable' as const, platforms: ['web' as const] }, builder: { codegen: { importPath: '@ghatana/design-system', componentName: 'Button' } } }]]);
      const options: GenerateOptions = { format: 'functional', typescript: true, importPath: '@ghatana/design-system', componentName: 'TestComponent' };

      const result = generateReactCode(document, contracts, options);

      const manualMergeLoss = result.roundTripFidelity.lossPoints.filter((lp) => lp.type === 'custom-code' && lp.location === nodeId);
      expect(manualMergeLoss.length).toBeGreaterThanOrEqual(1);
      expect(result.roundTripFidelity.canRoundTrip).toBe(false);
    });

    it('should detect loss points for nodes with state variants', () => {
      const nodeId = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'StateVariant Document',
        designSystem: { id: 'ds', name: 'DS', version: '1.0.0', tokenSetIds: [], componentContracts: [], themeId: 'default' },
        rootNodes: [nodeId],
        nodes: new Map([[nodeId, { id: nodeId, contractName: 'Button', props: {}, slots: {}, bindings: [], metadata: { stateVariants: ['hover', 'focus'] } }]]),
        metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      };
      const contracts = new Map([['Button', { name: 'Button', version: '1.0.0', props: [], slots: [], events: [], styles: {}, metadata: { category: 'input', status: 'stable' as const, platforms: ['web' as const] }, builder: { codegen: { importPath: '@ghatana/design-system', componentName: 'Button' } } }]]);
      const options: GenerateOptions = { format: 'functional', typescript: true, importPath: '@ghatana/design-system', componentName: 'TestComponent' };

      const result = generateReactCode(document, contracts, options);

      const stateVariantLoss = result.roundTripFidelity.lossPoints.filter(
        (lp) => lp.type === 'unsupported-pattern' && lp.description.includes('state variant'),
      );
      expect(stateVariantLoss.length).toBeGreaterThanOrEqual(1);
      expect(result.roundTripFidelity.canRoundTrip).toBe(false);
    });

    it('should detect loss points for nodes with responsive variants', () => {
      const nodeId = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'ResponsiveVariant Document',
        designSystem: { id: 'ds', name: 'DS', version: '1.0.0', tokenSetIds: [], componentContracts: [], themeId: 'default' },
        rootNodes: [nodeId],
        nodes: new Map([[nodeId, { id: nodeId, contractName: 'Button', props: {}, slots: {}, bindings: [], metadata: { responsiveVariants: ['sm', 'lg'] } }]]),
        metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      };
      const contracts = new Map([['Button', { name: 'Button', version: '1.0.0', props: [], slots: [], events: [], styles: {}, metadata: { category: 'input', status: 'stable' as const, platforms: ['web' as const] }, builder: { codegen: { importPath: '@ghatana/design-system', componentName: 'Button' } } }]]);
      const options: GenerateOptions = { format: 'functional', typescript: true, importPath: '@ghatana/design-system', componentName: 'TestComponent' };

      const result = generateReactCode(document, contracts, options);

      const responsiveLoss = result.roundTripFidelity.lossPoints.filter(
        (lp) => lp.type === 'unsupported-pattern' && lp.description.includes('responsive variant'),
      );
      expect(responsiveLoss.length).toBeGreaterThanOrEqual(1);
      expect(result.roundTripFidelity.canRoundTrip).toBe(false);
    });

    it('should decay confidence below 0.6 with multiple loss points', () => {
      const nodeId1 = createNodeId();
      const nodeId2 = createNodeId();
      const nodeId3 = createNodeId();
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Multi-Loss Document',
        designSystem: { id: 'ds', name: 'DS', version: '1.0.0', tokenSetIds: [], componentContracts: [], themeId: 'default' },
        rootNodes: [nodeId1, nodeId2, nodeId3],
        nodes: new Map([
          [nodeId1, { id: nodeId1, contractName: 'Missing1', props: {}, slots: {}, bindings: [], metadata: {} }],
          [nodeId2, { id: nodeId2, contractName: 'Missing2', props: {}, slots: {}, bindings: [], metadata: { ownership: 'user-authored' } }],
          [nodeId3, { id: nodeId3, contractName: 'Missing3', props: {}, slots: {}, bindings: [{ id: 'b1', type: 'data', source: 'store.x', target: 'x' }], metadata: {} }],
        ]),
        metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      };
      // No contracts registered → all nodes are "missing contract" + additional losses
      const options: GenerateOptions = { format: 'functional', typescript: true, importPath: '@ghatana/design-system', componentName: 'TestComponent' };

      const result = generateReactCode(document, new Map(), options);

      expect(result.roundTripFidelity.canRoundTrip).toBe(false);
      expect(result.roundTripFidelity.lossPoints.length).toBeGreaterThanOrEqual(3);
      expect(result.roundTripFidelity.confidence).toBeLessThan(0.6);
    });

    it('should not go below 0 confidence regardless of loss point count', () => {
      // Create enough loss points to theoretically exceed 1.0 total decay
      const nodeIds = Array.from({ length: 10 }, () => createNodeId());
      const nodes = new Map(
        nodeIds.map((id) => [
          id,
          {
            id,
            contractName: `Missing-${id}`,
            props: {},
            slots: {},
            bindings: [{ id: `b-${id}`, type: 'data' as const, source: 'x', target: 'y' }],
            metadata: { ownership: 'user-authored' as const, stateVariants: ['hover'], responsiveVariants: ['sm'] },
          },
        ]),
      );
      const document: BuilderDocument = {
        id: createDocumentId(),
        version: '1',
        name: 'Catastrophic Loss Document',
        designSystem: { id: 'ds', name: 'DS', version: '1.0.0', tokenSetIds: [], componentContracts: [], themeId: 'default' },
        rootNodes: nodeIds,
        nodes,
        metadata: { createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
      };
      const options: GenerateOptions = { format: 'functional', typescript: true, importPath: '@ghatana/design-system', componentName: 'TestComponent' };

      const result = generateReactCode(document, new Map(), options);

      expect(result.roundTripFidelity.confidence).toBeGreaterThanOrEqual(0);
    });
  });
});
