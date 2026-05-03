/**
 * Builder Document Adapter test suite
 * Tests for YAPPC PageDesigner BuilderDocument migration adapter
 */

import { describe, it, expect } from 'vitest';
import {
  componentDataToBuilderDocument,
  componentDataToBuilderProps,
  componentDataToInsertableInstance,
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

      expect(node?.props).toEqual({
        variant: 'outlined',
        color: 'secondary',
        size: 'large',
        disabled: true,
        fullWidth: true,
        children: 'Test Button',
      });
    });

    it('should include design system model', () => {
      const components: ComponentData[] = [];

      const document = componentDataToBuilderDocument(components);
      expect(document.designSystem).toBeDefined();
      expect(document.designSystem.id).toBe('ghatana-ds-v1');
      expect(document.designSystem.name).toBe('Ghatana Design System');
    });

    it('should preserve document identity and provenance when reconciling updates', () => {
      const original = componentDataToBuilderDocument([
        {
          id: 'button-1',
          type: 'button',
          text: 'Original',
          variant: 'contained',
          color: 'primary',
          size: 'medium',
          disabled: false,
          fullWidth: false,
        },
      ]);

      const updated = componentDataToBuilderDocument(
        [
          {
            id: 'button-1',
            type: 'button',
            text: 'Updated',
            variant: 'contained',
            color: 'primary',
            size: 'medium',
            disabled: false,
            fullWidth: false,
          },
        ],
        { existingDocument: original },
      );

      expect(updated.id).toBe(original.id);
      expect(updated.metadata.createdAt).toBe(original.metadata.createdAt);
      expect(updated.metadata.author).toBe('yappc-page-designer-adapter');
      expect(updated.metadata.tags).toContain('legacy-component-data-v1');
      expect(updated.nodes.get('button-1')?.metadata.provenance?.source).toBe(
        'yappc-page-designer-adapter',
      );
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

    it('should preserve all currently supported component types on round-trip', () => {
      const original: ComponentData[] = [
        {
          id: 'button-1',
          type: 'button',
          text: 'Save',
          variant: 'contained',
          color: 'success',
          size: 'small',
          disabled: false,
          fullWidth: false,
        },
        {
          id: 'card-1',
          type: 'card',
          label: 'Profile card',
          elevation: 4,
          title: 'Profile',
          subtitle: 'Active user',
          content: 'Summary content',
          showActions: true,
        },
        {
          id: 'textfield-1',
          type: 'textfield',
          label: 'Email',
          placeholder: 'name@example.com',
          variant: 'filled',
          size: 'small',
          required: true,
          disabled: false,
          fullWidth: true,
          multiline: false,
          rows: 1,
        },
        {
          id: 'typography-1',
          type: 'typography',
          label: 'Heading copy',
          variant: 'h4',
          text: 'Welcome back',
          color: 'primary',
          align: 'center',
        },
        {
          id: 'box-1',
          type: 'box',
          label: 'Layout wrapper',
          padding: 3,
          margin: 1,
          backgroundColor: '#ffffff',
          borderRadius: 2,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'stretch',
        },
      ];

      const document = componentDataToBuilderDocument(original);
      const converted = builderDocumentToComponentData(document);

      expect(converted).toEqual([
        {
          id: 'button-1',
          type: 'button',
          label: undefined,
          text: 'Save',
          variant: 'contained',
          color: 'success',
          size: 'small',
          disabled: false,
          fullWidth: false,
        },
        {
          id: 'card-1',
          type: 'card',
          label: 'Profile card',
          elevation: 4,
          title: 'Profile',
          subtitle: 'Active user',
          content: 'Summary content',
          showActions: true,
        },
        {
          id: 'textfield-1',
          type: 'textfield',
          label: 'Email',
          placeholder: 'name@example.com',
          variant: 'filled',
          size: 'small',
          required: true,
          disabled: false,
          fullWidth: true,
          multiline: false,
          rows: 1,
        },
        {
          id: 'typography-1',
          type: 'typography',
          label: 'Heading copy',
          variant: 'h4',
          text: 'Welcome back',
          color: 'primary',
          align: 'center',
        },
        {
          id: 'box-1',
          type: 'box',
          label: 'Layout wrapper',
          padding: 3,
          margin: 1,
          backgroundColor: '#ffffff',
          borderRadius: 2,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'stretch',
        },
      ]);
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

  describe('operation helpers', () => {
    it('should expose insertable instances without legacy structural fields in props', () => {
      const instance = componentDataToInsertableInstance({
        id: 'button-1',
        type: 'button',
        text: 'Click Me',
        variant: 'contained',
        color: 'primary',
        size: 'medium',
        disabled: false,
        fullWidth: false,
      });

      expect(instance.contractName).toBe('Button');
      expect(instance.props).toEqual({
        variant: 'contained',
        color: 'primary',
        size: 'medium',
        disabled: false,
        fullWidth: false,
        children: 'Click Me',
      });
    });

    it('should expose prop payloads for document update operations', () => {
      expect(
        componentDataToBuilderProps({
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
        }),
      ).toEqual({
        label: 'Name',
        placeholder: 'Enter name',
        variant: 'outlined',
        size: 'medium',
        required: true,
        disabled: false,
        fullWidth: true,
        multiline: false,
        rows: 1,
      });
    });
  });
});
