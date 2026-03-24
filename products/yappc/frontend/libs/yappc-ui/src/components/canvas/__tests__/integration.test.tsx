/**
 * Integration Tests for Canvas System
 *
 * Tests the complete workflow from schema to rendered component.
 */

import { render, screen } from '@testing-library/react';
import React from 'react';

import { ComponentNodeAdapter } from '../adapters/ComponentNodeAdapter';
import { CodeGenerator } from '../codegen/CodeGenerator';
import { RendererComponentRegistry } from '../renderer/ComponentRegistry';
import { NodeRenderer } from '../renderer/NodeRenderer';
import { ThemeApplicator } from '../renderer/ThemeApplicator';
import { CanvasValidator } from '../validation/CanvasValidator';

import type { ComponentSchema } from '../types/ComponentSchema';

// Mock component
const TestButton: React.FC<unknown> = ({ label, color, onClick }) => (
  <button onClick={onClick} style={{ color }}>
    {label}
  </button>
);

describe.skip('Canvas Integration Tests', () => {
  beforeAll(() => {
    RendererComponentRegistry.register('Button', TestButton);
  });

  afterAll(() => {
    RendererComponentRegistry.clear();
  });

  describe('Schema to Node to Render Pipeline', () => {
    it('should transform schema to node and render successfully', () => {
      // Step 1: Define component schema
      const schema: ComponentSchema = {
        type: 'Button',
        props: {
          label: 'Click Me',
          variant: 'primary',
        },
      };

      // Step 2: Transform to canvas node
      const context = {
        theme: 'base' as const,
        offset: { x: 0, y: 0 },
        tokens: {},
      };

      const result = ComponentNodeAdapter.schemaToNode(schema, context);
      expect(result.errors).toHaveLength(0);
      expect(result.data.data.componentType).toBe('Button');

      // Step 3: Render the node
      const themeContext = ThemeApplicator.createDefaultContext();

      render(
        <NodeRenderer
          componentType="Button"
          nodeData={result.data.data}
          themeContext={themeContext}
        />
      );

      expect(screen.getByText('Click Me')).toBeInTheDocument();
    });

    it('should handle theme tokens in the pipeline', () => {
      const schema: ComponentSchema = {
        type: 'Button',
        props: {
          label: 'Themed Button',
        },
      };

      const themeContext = ThemeApplicator.createDefaultContext();
      themeContext.tokens.base = {
        color: { primary: { 500: '#1976d2' } },
      };

      const context = {
        theme: 'base' as const,
        offset: { x: 0, y: 0 },
        tokens: themeContext.tokens.base,
      };

      const result = ComponentNodeAdapter.schemaToNode(schema, context);

      // Add token after transformation
      result.data.data.tokens = { color: '$color.primary.500' };

      render(
        <NodeRenderer
          componentType="Button"
          nodeData={result.data.data}
          themeContext={themeContext}
        />
      );

      const button = screen.getByText('Themed Button');
      expect(button).toHaveStyle({ color: '#1976d2' });
    });
  });

  describe('Validation Integration', () => {
    it('should validate node configuration', () => {
      const nodeData = {
        componentType: 'Button',
        props: {
          label: 'Test',
        },
      };

      const metadata = {
        type: 'Button',
        displayName: 'Button',
        category: 'atoms' as const,
        propDefinitions: [
          {
            name: 'label',
            type: 'string' as const,
            label: 'Label',
            required: true,
          },
        ],
      };

      const issues = CanvasValidator.validateNode(
        'btn-1',
        'Button',
        nodeData,
        metadata
      );

      expect(issues).toHaveLength(0);
    });

    it('should detect missing required props', () => {
      const nodeData = {
        componentType: 'Button',
        props: {},
      };

      const metadata = {
        type: 'Button',
        displayName: 'Button',
        category: 'atoms' as const,
        propDefinitions: [
          {
            name: 'label',
            type: 'string' as const,
            label: 'Label',
            required: true,
          },
        ],
      };

      const issues = CanvasValidator.validateNode(
        'btn-1',
        'Button',
        nodeData,
        metadata
      );

      expect(issues.length).toBeGreaterThan(0);
      expect(issues[0].severity).toBe('error');
      expect(issues[0].category).toBe('component');
    });
  });

  describe('Code Generation Integration', () => {
    it('should generate valid code from node data', () => {
      const nodeData = {
        componentType: 'Button',
        props: {
          label: 'Submit',
          variant: 'primary',
        },
      };

      const code = CodeGenerator.generateFile('Button', nodeData, {
        typescript: true,
        includeComments: true,
        includeImports: true,
      });

      expect(code).toContain('import { Button } from');
      expect(code).toContain('export function GeneratedButton');
      expect(code).toContain('label="Submit"');
      expect(code).toContain('variant="primary"');
    });

    it('should generate data binding code', () => {
      const nodeData = {
        componentType: 'Button',
        props: {
          label: 'Submit',
        },
        dataBinding: {
          source: 'formData',
          path: 'isValid',
          mode: 'one-way' as const,
        },
      };

      const generated = CodeGenerator.generateComponent('Button', nodeData, {
        includeDataBinding: true,
      });

      expect(generated.hooks).toContain('useDataBinding');
      expect(generated.hooks).toContain('formData');
      expect(generated.hooks).toContain('isValid');
    });

    it('should generate event handler code', () => {
      const nodeData = {
        componentType: 'Button',
        props: {
          label: 'Click Me',
        },
        events: {
          onClick: {
            emit: 'buttonClicked',
            payload: { source: 'button' },
          },
        },
      };

      const generated = CodeGenerator.generateComponent('Button', nodeData, {
        includeEvents: true,
      });

      expect(generated.handlers).toContain('handleClick');
      expect(generated.handlers).toContain('buttonClicked');
      expect(generated.handlers).toContain('eventBus.emit');
    });
  });

  describe('Round-trip Transformation', () => {
    it('should maintain data integrity through round-trip', () => {
      const originalSchema: ComponentSchema = {
        type: 'Button',
        props: {
          label: 'Test Button',
          variant: 'primary',
          size: 'medium',
        },
      };

      const context = {
        theme: 'base' as const,
        offset: { x: 0, y: 0 },
        tokens: {},
      };

      const validation = ComponentNodeAdapter.validateRoundtrip(
        originalSchema,
        context,
        { preserveIds: true }
      );

      expect(validation.valid).toBe(true);
      expect(validation.errors).toHaveLength(0);
    });
  });

  describe('Performance Validation', () => {
    it('should warn about large canvas', () => {
      const issues = CanvasValidator.validatePerformance(150);

      expect(issues.length).toBeGreaterThan(0);
      expect(issues[0].severity).toBe('warning');
      expect(issues[0].category).toBe('performance');
    });

    it('should error on very large canvas', () => {
      const issues = CanvasValidator.validatePerformance(250);

      const errors = issues.filter((i) => i.severity === 'error');
      expect(errors.length).toBeGreaterThan(0);
    });
  });
});
