import { describe, test, expect } from 'vitest';

import {
  ButtonSchema,
  CardSchema,
  TextFieldSchema,
  TypographySchema,
  BoxSchema,
  getDefaultComponentData,
  validateComponentData,
  getSchemaForType,
} from './schemas';

describe('Component Schemas', () => {
  describe('ButtonSchema', () => {
    test('should validate valid button data', () => {
      const data = {
        id: 'btn-1',
        type: 'button',
        variant: 'contained',
        color: 'primary',
        size: 'medium',
        disabled: false,
        fullWidth: false,
        text: 'Click me',
      };

      const result = ButtonSchema.safeParse(data);
      expect(result.success).toBe(true);
    });

    test('should apply default values', () => {
      const data = {
        id: 'btn-1',
        type: 'button',
      };

      const result = ButtonSchema.parse(data);
      expect(result.variant).toBe('contained');
      expect(result.color).toBe('primary');
      expect(result.size).toBe('medium');
      expect(result.text).toBe('Button');
    });

    test('should reject invalid variant', () => {
      const data = {
        id: 'btn-1',
        type: 'button',
        variant: 'invalid',
      };

      const result = ButtonSchema.safeParse(data);
      expect(result.success).toBe(false);
    });
  });

  describe('CardSchema', () => {
    test('should validate valid card data', () => {
      const data = {
        id: 'card-1',
        type: 'card',
        elevation: 4,
        title: 'Card Title',
        subtitle: 'Subtitle',
        content: 'Card content',
      };

      const result = CardSchema.safeParse(data);
      expect(result.success).toBe(true);
    });

    test('should enforce elevation bounds', () => {
      const invalidData = {
        id: 'card-1',
        type: 'card',
        elevation: 30,
      };

      const result = CardSchema.safeParse(invalidData);
      expect(result.success).toBe(false);
    });
  });

  describe('TextFieldSchema', () => {
    test('should validate multiline text field', () => {
      const data = {
        id: 'tf-1',
        type: 'textfield',
        label: 'Comments',
        multiline: true,
        rows: 4,
      };

      const result = TextFieldSchema.safeParse(data);
      expect(result.success).toBe(true);
      if (result.success) {
        expect(result.data.rows).toBe(4);
      }
    });
  });

  describe('TypographySchema', () => {
    test('should validate all variant types', () => {
      const variants = ['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'body1', 'body2', 'caption'];

      variants.forEach((variant) => {
        const data = {
          id: 'typo-1',
          type: 'typography',
          variant,
          text: 'Test',
        };

        const result = TypographySchema.safeParse(data);
        expect(result.success).toBe(true);
      });
    });
  });

  describe('BoxSchema', () => {
    test('should validate flex layout properties', () => {
      const data = {
        id: 'box-1',
        type: 'box',
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
      };

      const result = BoxSchema.safeParse(data);
      expect(result.success).toBe(true);
    });
  });

  describe('getDefaultComponentData', () => {
    test('should return default button data', () => {
      const data = getDefaultComponentData('button');
      
      expect(data.type).toBe('button');
      expect(data.id).toBeTruthy();
      expect((data as unknown).variant).toBe('contained');
    });

    test('should return default card data', () => {
      const data = getDefaultComponentData('card');
      
      expect(data.type).toBe('card');
      expect((data as unknown).elevation).toBe(2);
    });

    test('should handle unknown types', () => {
      const data = getDefaultComponentData('unknown');
      
      expect(data.type).toBe('unknown');
      expect(data.id).toBeTruthy();
    });
  });

  describe('validateComponentData', () => {
    test('should validate correct component data', () => {
      const data = {
        id: 'btn-1',
        type: 'button',
        variant: 'contained',
        color: 'primary',
        size: 'medium',
        disabled: false,
        fullWidth: false,
        text: 'Click',
      };

      const result = validateComponentData(data);
      expect(result.valid).toBe(true);
      expect(result.errors).toBeUndefined();
    });

    test('should return errors for invalid data', () => {
      const data = {
        id: 'btn-1',
        type: 'button',
        variant: 'invalid',
      };

      const result = validateComponentData(data);
      expect(result.valid).toBe(false);
      expect(result.errors).toBeTruthy();
      expect(result.errors!.length).toBeGreaterThan(0);
    });
  });

  describe('getSchemaForType', () => {
    test('should return correct schema for each type', () => {
      expect(getSchemaForType('button')).toBe(ButtonSchema);
      expect(getSchemaForType('card')).toBe(CardSchema);
      expect(getSchemaForType('textfield')).toBe(TextFieldSchema);
      expect(getSchemaForType('typography')).toBe(TypographySchema);
      expect(getSchemaForType('box')).toBe(BoxSchema);
    });

    test('should return null for unknown type', () => {
      expect(getSchemaForType('unknown')).toBeNull();
    });
  });
});
