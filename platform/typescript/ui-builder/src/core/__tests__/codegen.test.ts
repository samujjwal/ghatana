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
      expect(content).toContain('</Button>');
      expect(content).toContain('<Typography');
      expect(content).toContain('</Typography>');
      expect(content).toContain('header slot');
      expect(content).toContain('content slot');
    });

    it('should track ownership metadata for generated code', () => {
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

      expect(result.ownership).toHaveLength(1);
      expect(result.ownership[0].region).toBe('component');
      expect(result.ownership[0].type).toBe('builder-generated');
      expect(result.ownership[0].builderNodeIds).toContain(buttonId);
      expect(result.ownership[0].lineStart).toBe(1);
      expect(result.ownership[0].lineEnd).toBeGreaterThan(0);
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
    });
  });
});
