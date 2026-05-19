/**
 * @fileoverview Round-trip tests for binding schema
 *
 * Tests that bindings and action definitions can be validated correctly.
 *
 * @doc.type test
 * @doc.purpose Binding schema validation tests
 * @doc.layer platform
 */

import { describe, it, expect } from 'vitest';
import {
  validateBinding,
  validateActionDefinition,
  validateBindings,
  validateActionDefinitions,
  validateBidirectionalBinding,
} from '../binding.schema.js';

describe('Binding Schema', () => {
  describe('binding validation', () => {
    it('should validate a valid data binding', () => {
      const binding = {
        id: 'binding-1',
        type: 'data' as const,
        source: 'dataSource.users',
        target: 'props.value',
      };

      const result = validateBinding(binding);
      expect(result.success).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should validate a valid event binding', () => {
      const binding = {
        id: 'binding-2',
        type: 'event' as const,
        source: 'events.userClick',
        target: 'props.onClick',
      };

      const result = validateBinding(binding);
      expect(result.success).toBe(true);
    });

    it('should validate a binding with transform', () => {
      const binding = {
        id: 'binding-3',
        type: 'data' as const,
        source: 'dataSource.users',
        target: 'props.value',
        transform: 'map(u => u.name)',
      };

      const result = validateBinding(binding);
      expect(result.success).toBe(true);
    });

    it('should reject binding with invalid source path', () => {
      const binding = {
        id: 'binding-1',
        type: 'data' as const,
        source: 'invalid path with spaces',
        target: 'props.value',
      };

      const result = validateBinding(binding);
      expect(result.success).toBe(false);
      expect(result.errors).toContain('Invalid source path: invalid path with spaces');
    });

    it('should reject binding with invalid target path', () => {
      const binding = {
        id: 'binding-1',
        type: 'data' as const,
        source: 'dataSource.users',
        target: 'invalid.path!',
      };

      const result = validateBinding(binding);
      expect(result.success).toBe(false);
      expect(result.errors).toContain('Invalid target path: invalid.path!');
    });

    it('should reject binding with empty transform', () => {
      const binding = {
        id: 'binding-1',
        type: 'data' as const,
        source: 'dataSource.users',
        target: 'props.value',
        transform: '   ',
      };

      const result = validateBinding(binding);
      expect(result.success).toBe(false);
      expect(result.errors).toContain('Transform expression cannot be empty');
    });

    it('should reject binding with missing required fields', () => {
      const binding = {
        id: 'binding-1',
        type: 'data' as const,
        // Missing source and target
      };

      const result = validateBinding(binding);
      expect(result.success).toBe(false);
    });
  });

  describe('action definition validation', () => {
    it('should validate a valid action definition', () => {
      const action = {
        id: 'action-1',
        label: 'Navigate to Home',
        triggerEvent: 'onClick',
        targetKind: 'navigate' as const,
        payload: { route: '/home' },
      };

      const result = validateActionDefinition(action);
      expect(result.success).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should validate action with condition', () => {
      const action = {
        id: 'action-1',
        triggerEvent: 'onChange',
        targetKind: 'update-binding' as const,
        payload: { value: 'test' },
        condition: 'props.value !== ""',
      };

      const result = validateActionDefinition(action);
      expect(result.success).toBe(true);
    });

    it('should reject action with invalid trigger event', () => {
      const action = {
        id: 'action-1',
        triggerEvent: 'click', // Should be onClick
        targetKind: 'navigate' as const,
      };

      const result = validateActionDefinition(action);
      expect(result.success).toBe(false);
      expect(result.errors).toContain('Invalid trigger event: click. Expected format: onEventName');
    });

    it('should reject action with empty condition', () => {
      const action = {
        id: 'action-1',
        triggerEvent: 'onClick',
        targetKind: 'navigate' as const,
        condition: '   ',
      };

      const result = validateActionDefinition(action);
      expect(result.success).toBe(false);
      expect(result.errors).toContain('Condition expression cannot be empty');
    });
  });

  describe('array validation', () => {
    it('should validate an array of valid bindings', () => {
      const bindings = [
        {
          id: 'binding-1',
          type: 'data' as const,
          source: 'dataSource.users',
          target: 'props.value',
        },
        {
          id: 'binding-2',
          type: 'theme' as const,
          source: 'theme.colors.primary',
          target: 'style.color',
        },
      ];

      const result = validateBindings(bindings);
      expect(result.success).toBe(true);
      expect(result.data).toHaveLength(2);
    });

    it('should reject array with one invalid binding', () => {
      const bindings = [
        {
          id: 'binding-1',
          type: 'data' as const,
          source: 'dataSource.users',
          target: 'props.value',
        },
        {
          id: 'binding-2',
          type: 'data' as const,
          source: 'invalid path',
          target: 'props.value',
        },
      ];

      const result = validateBindings(bindings);
      expect(result.success).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });

    it('should validate an array of valid actions', () => {
      const actions = [
        {
          id: 'action-1',
          triggerEvent: 'onClick',
          targetKind: 'navigate' as const,
          payload: { route: '/home' },
        },
        {
          id: 'action-2',
          triggerEvent: 'onChange',
          targetKind: 'update-binding' as const,
          payload: { value: 'test' },
        },
      ];

      const result = validateActionDefinitions(actions);
      expect(result.success).toBe(true);
      expect(result.data).toHaveLength(2);
    });
  });

  describe('bidirectional binding validation', () => {
    it('should validate bidirectional data binding', () => {
      const binding = {
        id: 'binding-1',
        type: 'data' as const,
        source: 'dataSource.users',
        target: 'props.value',
        bidirectional: true,
      };

      const result = validateBidirectionalBinding(binding);
      expect(result.success).toBe(true);
    });

    it('should reject bidirectional binding for non-data type', () => {
      const binding = {
        id: 'binding-1',
        type: 'event' as const,
        source: 'events.click',
        target: 'props.onClick',
        bidirectional: true,
      };

      const result = validateBidirectionalBinding(binding);
      expect(result.success).toBe(false);
      expect(result.errors).toContain("Bidirectional binding not supported for type 'event'");
    });

    it('should reject bidirectional binding with transform', () => {
      const binding = {
        id: 'binding-1',
        type: 'data' as const,
        source: 'dataSource.users',
        target: 'props.value',
        transform: 'map(u => u)',
        bidirectional: true,
      };

      const result = validateBidirectionalBinding(binding);
      expect(result.success).toBe(false);
      expect(result.errors).toContain('Transform expressions are not allowed with bidirectional bindings.');
    });
  });
});
