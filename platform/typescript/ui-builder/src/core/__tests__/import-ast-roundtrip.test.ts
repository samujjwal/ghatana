/**
 * @fileoverview Tests for AST-based TSX import roundtrip.
 *
 * Tests that TSX code can be imported using AST parsing and that the
 * resulting BuilderDocument can be exported back to TSX with acceptable
 * fidelity. This validates the AST parser implementation replaces the
 * previous regex-based heuristic.
 *
 * @doc.type test
 * @doc.purpose TSX import AST parser roundtrip validation
 * @doc.layer ui-builder
 */

import { describe, it, expect } from 'vitest';
import { importFromTsx } from '../import.js';
import { createBuilderDocument } from '../builder-document.js';

describe('TSX Import AST Roundtrip', () => {
  const designSystemContractNames = new Set(['Button', 'Card', 'Input', 'Text']);

  describe('basic component extraction', () => {
    it('should extract self-closing components with props', () => {
      const tsx = `
        <Button variant="primary" size="large" />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.status).toBe('clean');
      expect(result.addedNodeIds.length).toBe(1);
      expect(result.document.nodes).toBeDefined();
    });

    it('should extract opening and closing tags', () => {
      const tsx = `
        <Card>
          <Text>Hello</Text>
        </Card>
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.addedNodeIds.length).toBe(2);
    });

    it('should parse string props correctly', () => {
      const tsx = `
        <Button label="Click me" />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      const nodes = Object.values(result.document.nodes);
      const buttonNode = nodes.find((n: any) => n.contractName === 'Button');
      expect(buttonNode?.props.label).toBe('Click me');
    });

    it('should parse numeric props correctly', () => {
      const tsx = `
        <Input maxLength={10} />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      const nodes = Object.values(result.document.nodes);
      const inputNode = nodes.find((n: any) => n.contractName === 'Input');
      expect(inputNode?.props.maxLength).toBe(10);
    });

    it('should parse boolean props correctly', () => {
      const tsx = `
        <Button disabled />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      const nodes = Object.values(result.document.nodes);
      const buttonNode = nodes.find((n: any) => n.contractName === 'Button');
      expect(buttonNode?.props.disabled).toBe(true);
    });
  });

  describe('unknown component handling', () => {
    it('should flag unknown components as conflicts', () => {
      const tsx = `
        <UnknownComponent variant="primary" />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.status).toBe('review-required');
      expect(result.conflicts.length).toBeGreaterThan(0);
      expect(result.conflicts[0].conflictType).toBe('unsupported-pattern');
    });

    it('should not add nodes for unknown components', () => {
      const tsx = `
        <UnknownComponent variant="primary" />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.addedNodeIds.length).toBe(0);
    });
  });

  describe('complex TSX patterns', () => {
    it('should handle nested components', () => {
      const tsx = `
        <Card>
          <Button variant="primary" />
          <Button variant="secondary" />
        </Card>
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.addedNodeIds.length).toBe(3);
    });

    it('should handle mixed known and unknown components', () => {
      const tsx = `
        <Card>
          <Button variant="primary" />
          <UnknownComponent />
          <Text>Hello</Text>
        </Card>
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.status).toBe('review-required');
      expect(result.addedNodeIds.length).toBe(3); // Card, Button, Text
      expect(result.conflicts.length).toBe(1); // UnknownComponent
    });

    it('should handle expression props as loss points', () => {
      const tsx = `
        <Button label={dynamicLabel} />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      const nodes = Object.values(result.document.nodes);
      const buttonNode = nodes.find((n: any) => n.contractName === 'Button');
      // Expression should be preserved as string for review
      expect(buttonNode?.props.label).toBe('{dynamicLabel}');
    });
  });

  describe('empty and edge cases', () => {
    it('should handle empty TSX', () => {
      const tsx = '';

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.addedNodeIds.length).toBe(0);
      expect(result.fidelity.lossPoints.length).toBeGreaterThan(0);
    });

    it('should handle TSX with no design system components', () => {
      const tsx = `
        <div>Hello World</div>
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.addedNodeIds.length).toBe(0);
    });

    it('should handle TSX with only lowercase elements', () => {
      const tsx = `
        <div><span>text</span></div>
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.addedNodeIds.length).toBe(0);
    });
  });

  describe('fidelity reporting', () => {
    it('should report clean fidelity for simple imports', () => {
      const tsx = `
        <Button variant="primary" />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.fidelity.canRoundTrip).toBe(true);
      expect(result.fidelity.lossPoints).toHaveLength(0);
      expect(result.fidelity.confidence).toBe(1);
    });

    it('should report loss points for unknown components', () => {
      const tsx = `
        <UnknownComponent />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      expect(result.fidelity.canRoundTrip).toBe(false);
      expect(result.fidelity.lossPoints.length).toBeGreaterThan(0);
      expect(result.fidelity.confidence).toBeLessThan(1);
    });

    it('should report loss points for expression props', () => {
      const tsx = `
        <Button label={computedValue} />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      // Expression props are preserved but marked as loss points
      expect(result.fidelity.confidence).toBeLessThan(1);
    });
  });

  describe('AST parser vs regex comparison', () => {
    it('should correctly parse props with special characters', () => {
      const tsx = `
        <Button data-testid="submit-btn" aria-label="Submit form" />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      const nodes = Object.values(result.document.nodes);
      const buttonNode = nodes.find((n: any) => n.contractName === 'Button');
      expect(buttonNode?.props['data-testid']).toBe('submit-btn');
      expect(buttonNode?.props['aria-label']).toBe('Submit form');
    });

    it('should handle quotes within prop values', () => {
      const tsx = `
        <Button label='Click "here"' />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      const nodes = Object.values(result.document.nodes);
      const buttonNode = nodes.find((n: any) => n.contractName === 'Button');
      expect(buttonNode?.props.label).toBe('Click "here"');
    });

    it('should handle multiple props on single component', () => {
      const tsx = `
        <Input type="text" placeholder="Enter name" maxLength={50} required />
      `;

      const result = importFromTsx(
        { format: 'tsx', content: tsx },
        createBuilderDocument('test', {
          documentId: 'test-doc',
          designSystemId: 'default',
          designSystemName: 'Default',
        }),
        designSystemContractNames,
      );

      const nodes = Object.values(result.document.nodes);
      const inputNode = nodes.find((n: any) => n.contractName === 'Input');
      expect(inputNode?.props.type).toBe('text');
      expect(inputNode?.props.placeholder).toBe('Enter name');
      expect(inputNode?.props.maxLength).toBe(50);
      expect(inputNode?.props.required).toBe(true);
    });
  });
});
