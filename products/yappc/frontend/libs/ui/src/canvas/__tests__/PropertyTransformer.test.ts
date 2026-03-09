/**
 * Property Transformer Tests
 */

import { describe, it, expect } from 'vitest';

import { PropertyTransformer } from '../adapters/PropertyTransformer';

describe('PropertyTransformer', () => {
  describe('propsToNodeData', () => {
    it('should transform simple props', () => {
      const result = PropertyTransformer.propsToNodeData('Button', {
        label: 'Click me',
        variant: 'primary',
        size: 'medium',
      });

      expect(result.componentType).toBe('Button');
      expect(result.props).toEqual({
        label: 'Click me',
        variant: 'primary',
        size: 'medium',
      });
      expect(result.tokens).toBeUndefined();
    });

    it('should extract token references', () => {
      const result = PropertyTransformer.propsToNodeData('Button', {
        label: 'Click me',
        color: '$color.primary.500',
        backgroundColor: '$color.neutral.100',
      });

      expect(result.tokens).toBeDefined();
      expect(result.tokens?.color).toBe('$color.primary.500');
      expect(result.tokens?.backgroundColor).toBe('$color.neutral.100');
    });

    it('should handle mixed props and tokens', () => {
      const result = PropertyTransformer.propsToNodeData('Button', {
        label: 'Click me',
        variant: 'primary',
        color: '$color.primary.500',
        size: 'medium',
      });

      expect(result.props?.label).toBe('Click me');
      expect(result.props?.variant).toBe('primary');
      expect(result.props?.size).toBe('medium');
      expect(result.tokens?.color).toBe('$color.primary.500');
    });

    it('should handle nested objects', () => {
      const result = PropertyTransformer.propsToNodeData('Component', {
        style: {
          padding: 16,
          margin: 8,
        },
      });

      expect(result.props?.style).toEqual({
        padding: 16,
        margin: 8,
      });
    });

    it('should handle arrays', () => {
      const result = PropertyTransformer.propsToNodeData('Component', {
        items: ['one', 'two', 'three'],
      });

      expect(result.props?.items).toEqual(['one', 'two', 'three']);
    });

    it('should handle null and undefined', () => {
      const result = PropertyTransformer.propsToNodeData('Component', {
        value: null,
        optional: undefined,
      });

      expect(result.props?.value).toBeNull();
      expect(result.props?.optional).toBeUndefined();
    });
  });

  describe('nodeDataToProps', () => {
    it('should convert node data to props', () => {
      const nodeData: any = {
        componentType: 'Button',
        props: {
          label: 'Test',
          variant: 'primary',
        },
      };

      const props = PropertyTransformer.nodeDataToProps(nodeData);

      expect(props).toEqual({
        label: 'Test',
        variant: 'primary',
      });
    });

    it('should preserve token references in props', () => {
      const nodeData: any = {
        componentType: 'Button',
        props: {
          label: 'Test',
          color: '$color.primary.500',
        },
        tokens: {
          color: '$color.primary.500',
        },
      };

      const props = PropertyTransformer.nodeDataToProps(nodeData);

      expect(props.color).toBe('$color.primary.500');
    });
  });

  describe('isTokenReference', () => {
    it('should identify token references', () => {
      expect(PropertyTransformer.isTokenReference('$color.primary')).toBe(true);
      expect(PropertyTransformer.isTokenReference('$typography.fontSize.large')).toBe(true);
    });

    it('should reject non-token values', () => {
      expect(PropertyTransformer.isTokenReference('blue')).toBe(false);
      expect(PropertyTransformer.isTokenReference('16px')).toBe(false);
      expect(PropertyTransformer.isTokenReference(123)).toBe(false);
      expect(PropertyTransformer.isTokenReference(null)).toBe(false);
    });
  });

  describe('getTokenPath', () => {
    it('should extract token path', () => {
      expect(PropertyTransformer.getTokenPath('$color.primary.500')).toBe('color.primary.500');
      expect(PropertyTransformer.getTokenPath('$typography.fontSize')).toBe('typography.fontSize');
    });
  });

  describe('createTokenReference', () => {
    it('should create token reference', () => {
      expect(PropertyTransformer.createTokenReference('color.primary.500')).toBe('$color.primary.500');
      expect(PropertyTransformer.createTokenReference('spacing.md')).toBe('$spacing.md');
    });
  });

  describe('validate', () => {
    it('should validate matching props', () => {
      const original = { label: 'Test', variant: 'primary' };
      const transformed = { label: 'Test', variant: 'primary' };

      const result = PropertyTransformer.validate(original, transformed);

      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should detect missing properties', () => {
      const original = { label: 'Test', variant: 'primary', size: 'medium' };
      const transformed = { label: 'Test', variant: 'primary' };

      const result = PropertyTransformer.validate(original, transformed);

      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Missing property: size');
    });

    it('should detect unexpected properties', () => {
      const original = { label: 'Test' };
      const transformed = { label: 'Test', variant: 'primary' };

      const result = PropertyTransformer.validate(original, transformed);

      expect(result.valid).toBe(false);
      expect(result.errors).toContain('Unexpected property: variant');
    });
  });

  describe('mergeProps', () => {
    it('should merge simple props', () => {
      const base = { a: 1, b: 2 };
      const override = { b: 3, c: 4 };

      const result = PropertyTransformer.mergeProps(base, override);

      expect(result).toEqual({ a: 1, b: 3, c: 4 });
    });

    it('should deep merge nested objects', () => {
      const base = { style: { padding: 16, margin: 8 } };
      const override = { style: { margin: 16, color: 'blue' } };

      const result = PropertyTransformer.mergeProps(base, override);

      expect(result).toEqual({
        style: { padding: 16, margin: 16, color: 'blue' },
      });
    });

    it('should not merge arrays', () => {
      const base = { items: ['a', 'b'] };
      const override = { items: ['c', 'd'] };

      const result = PropertyTransformer.mergeProps(base, override);

      expect(result.items).toEqual(['c', 'd']); // Arrays are replaced, not merged
    });
  });

  describe('extractMetadata', () => {
    it('should extract metadata from props', () => {
      const props = {
        label: 'Test',
        variant: 'primary',
        _meta: { category: 'buttons', deprecated: false },
      };

      const result = PropertyTransformer.extractMetadata(props);

      expect(result.props).toEqual({
        label: 'Test',
        variant: 'primary',
      });
      expect(result.metadata).toEqual({
        category: 'buttons',
        deprecated: false,
      });
    });

    it('should handle multiple metadata keys', () => {
      const props = {
        label: 'Test',
        _meta: { category: 'buttons' },
        metadata: { version: '1.0' },
      };

      const result = PropertyTransformer.extractMetadata(props);

      expect(result.props).toEqual({ label: 'Test' });
      expect(result.metadata).toEqual({
        category: 'buttons',
        version: '1.0',
      });
    });

    it('should return empty metadata if none present', () => {
      const props = { label: 'Test', variant: 'primary' };

      const result = PropertyTransformer.extractMetadata(props);

      expect(result.props).toEqual(props);
      expect(result.metadata).toEqual({});
    });
  });
});
