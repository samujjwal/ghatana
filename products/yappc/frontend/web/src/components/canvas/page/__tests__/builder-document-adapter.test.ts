/**
 * Builder Document Adapter test suite
 * Tests for YAPPC PageDesigner BuilderDocument migration adapter
 */

import { describe, it, expect } from 'vitest';
import {
  componentDataToBuilderDocument,
  builderDocumentToComponentData,
  isBuilderDocument,
} from '../builder-document-adapter';
import type { ComponentData } from '../schemas';

describe('Builder Document Adapter', () => {
  describe('componentDataToBuilderDocument', () => {
    it('should convert ComponentData array to BuilderDocument', () => {
      const components: ComponentData[] = [
        {
          id: 'button-1',
          type: 'button',
          text: 'Click Me',
          variant: 'contained',
          color: 'primary',
          size: 'medium',
          disabled: false,
          fullWidth: false,
        },
      ];

      const document = componentDataToBuilderDocument(components);

      expect(document).toBeDefined();
      expect(document.nodes.size).toBe(1);
      expect(document.rootNodes).toHaveLength(1);
    });

    it('should preserve component properties', () => {
      const components: ComponentData[] = [
        {
          id: 'button-1',
          type: 'button',
          text: 'Test Button',
          variant: 'outlined',
          color: 'secondary',
          size: 'large',
          disabled: true,
          fullWidth: true,
        },
      ];

      const document = componentDataToBuilderDocument(components);
      const node = document.nodes.get('button-1');

      expect(node?.props).toEqual(components[0]);
    });

    it('should include design system model', () => {
      const components: ComponentData[] = [];

      const document = componentDataToBuilderDocument(components);
      expect(document.designSystem).toBeDefined();
      expect(document.designSystem.id).toBe('ghatana-ds-v1');
      expect(document.designSystem.name).toBe('Ghatana Design System');
    });
  });

  describe('builderDocumentToComponentData', () => {
    it('should convert BuilderDocument to ComponentData array', () => {
      const components: ComponentData[] = [
        {
          id: 'button-1',
          type: 'button',
          text: 'Click Me',
          variant: 'contained',
          color: 'primary',
          size: 'medium',
          disabled: false,
          fullWidth: false,
        },
      ];

      const document = componentDataToBuilderDocument(components);
      const converted = builderDocumentToComponentData(document);

      expect(converted).toHaveLength(1);
      expect(converted[0].id).toBe('button-1');
      expect(converted[0].type).toBe('button');
    });

    it('should preserve all component data on round-trip', () => {
      const original: ComponentData[] = [
        {
          id: 'textfield-1',
          type: 'textfield',
          label: 'Name',
          placeholder: 'Enter name',
          variant: 'outlined',
          size: 'medium',
          required: true,
          disabled: false,
          fullWidth: true,
          multiline: false,
          rows: 1,
        },
      ];

      const document = componentDataToBuilderDocument(original);
      const converted = builderDocumentToComponentData(document);

      expect(converted[0]).toEqual(original[0]);
    });
  });

  describe('isBuilderDocument', () => {
    it('should identify BuilderDocument objects', () => {
      const components: ComponentData[] = [];
      const document = componentDataToBuilderDocument(components);

      expect(isBuilderDocument(document)).toBe(true);
    });

    it('should reject non-BuilderDocument objects', () => {
      expect(isBuilderDocument(null)).toBe(false);
      expect(isBuilderDocument(undefined)).toBe(false);
      expect(isBuilderDocument({})).toBe(false);
      expect(isBuilderDocument({ id: 'test' })).toBe(false);
    });

    it('should accept ComponentData arrays as non-BuilderDocument', () => {
      const components: ComponentData[] = [];

      expect(isBuilderDocument(components)).toBe(false);
    });
  });
});
