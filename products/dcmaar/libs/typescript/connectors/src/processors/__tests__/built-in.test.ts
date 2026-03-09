/**
 * @fileoverview Tests for built-in processors.
 *
 * @module processors/__tests__/built-in.test
 * @since 1.1.0
 */

import { describe, it, expect, beforeEach } from 'vitest';
import {
  ValidateProcessor,
  TransformProcessor,
  FilterProcessor,
  EnrichProcessor,
  RedactionProcessor,
} from '../built-in';
import { ProcessorRegistry } from '../ProcessorRegistry';
import { ProcessorContext } from '../types';
import { Event } from '../../types';

describe('Built-in Processors', () => {
  let context: ProcessorContext;

  beforeEach(() => {
    context = {
      connectorId: 'test-connector',
      connectorType: 'source',
      logger: {
        debug: () => {},
        info: () => {},
        warn: () => {},
        error: () => {},
      },
      metrics: {
        increment: () => {},
        gauge: () => {},
        histogram: () => {},
      },
    };
  });

  describe('ValidateProcessor', () => {
    it('should validate event payload against schema', async () => {
      const processor = new ValidateProcessor({
        id: 'validate-1',
        type: 'validate',
        config: {
          schema: {
            type: 'object',
            properties: {
              name: { type: 'string' },
              age: { type: 'number' },
            },
            required: ['name'],
          },
        },
      });

      processor.initialize(context);

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { name: 'John', age: 30 },
      };

      const result = await processor.process(event, context);
      expect(result).toEqual(event);
    });

    it('should filter invalid events in filter mode', async () => {
      const processor = new ValidateProcessor({
        id: 'validate-2',
        type: 'validate',
        config: {
          schema: {
            type: 'object',
            properties: { name: { type: 'string' } },
            required: ['name'],
          },
          mode: 'filter',
        },
      });

      processor.initialize(context);

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { age: 30 }, // missing name
      };

      const result = await processor.process(event, context);
      expect(result).toBeNull();
    });
  });

  describe('TransformProcessor', () => {
    it('should map fields', async () => {
      const processor = new TransformProcessor({
        id: 'transform-1',
        type: 'transform',
        config: {
          operations: [{ type: 'map', source: 'user.fullName', target: 'name' }],
        },
      });

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { user: { fullName: 'John Doe' } },
      };

      const result = await processor.process(event, context);
      expect(result.payload).toHaveProperty('name', 'John Doe');
    });

    it('should rename fields', async () => {
      const processor = new TransformProcessor({
        id: 'transform-2',
        type: 'transform',
        config: {
          operations: [{ type: 'rename', from: 'oldName', to: 'newName' }],
        },
      });

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { oldName: 'value' },
      };

      const result = await processor.process(event, context);
      expect(result.payload).toHaveProperty('newName', 'value');
      expect(result.payload).not.toHaveProperty('oldName');
    });
  });

  describe('FilterProcessor', () => {
    it('should include events matching rule', async () => {
      const processor = new FilterProcessor({
        id: 'filter-1',
        type: 'filter',
        config: {
          mode: 'include',
          rule: {
            conditions: [{ field: 'age', operator: 'gte', value: 18 }],
          },
        },
      });

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { name: 'John', age: 25 },
      };

      const result = await processor.process(event, context);
      expect(result).toEqual(event);
    });

    it('should filter events not matching rule', async () => {
      const processor = new FilterProcessor({
        id: 'filter-2',
        type: 'filter',
        config: {
          mode: 'include',
          rule: {
            conditions: [{ field: 'age', operator: 'gte', value: 18 }],
          },
        },
      });

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { name: 'Jane', age: 16 },
      };

      const result = await processor.process(event, context);
      expect(result).toBeNull();
    });
  });

  describe('EnrichProcessor', () => {
    it('should add static values', async () => {
      const processor = new EnrichProcessor({
        id: 'enrich-1',
        type: 'enrich',
        config: {
          target: 'metadata',
          operations: [{ type: 'static', path: 'version', value: '1.0' }],
        },
      });

      processor.initialize(context);

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { name: 'John' },
      };

      const result = await processor.process(event, context);
      expect(result.metadata).toHaveProperty('version', '1.0');
    });

    it('should generate timestamps', async () => {
      const processor = new EnrichProcessor({
        id: 'enrich-2',
        type: 'enrich',
        config: {
          target: 'metadata',
          operations: [{ type: 'timestamp', path: 'processedAt', format: 'iso' }],
        },
      });

      processor.initialize(context);

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { name: 'John' },
      };

      const result = await processor.process(event, context);
      expect(result.metadata).toHaveProperty('processedAt');
      expect(typeof result.metadata?.processedAt).toBe('string');
    });

    it('should generate UUIDs', async () => {
      const processor = new EnrichProcessor({
        id: 'enrich-3',
        type: 'enrich',
        config: {
          target: 'metadata',
          operations: [{ type: 'uuid', path: 'id' }],
        },
      });

      processor.initialize(context);

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { name: 'John' },
      };

      const result = await processor.process(event, context);
      expect(result.metadata).toHaveProperty('id');
      expect(result.metadata?.id).toMatch(/^[a-f0-9-]{36}$/);
    });
  });

  describe('RedactionProcessor', () => {
    it('should redact email addresses', async () => {
      const processor = new RedactionProcessor({
        id: 'redact-1',
        type: 'redact',
        config: {
          rules: [{ type: 'email', replacement: 'mask' }],
        },
      });

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { message: 'Contact me at john@example.com' },
      };

      const result = await processor.process(event, context);
      expect(result.payload.message).not.toContain('john@example.com');
      expect(result.payload.message).toContain('*');
    });

    it('should redact specific fields', async () => {
      const processor = new RedactionProcessor({
        id: 'redact-2',
        type: 'redact',
        config: {
          rules: [{ type: 'field_path', path: 'user.ssn', replacement: 'hash' }],
        },
      });

      const event: Event = {
        id: '1',
        type: 'user',
        timestamp: Date.now(),
        payload: { user: { name: 'John', ssn: '123-45-6789' } },
      };

      const result = await processor.process(event, context);
      expect(result.payload.user.ssn).toMatch(/^\[REDACTED:/);
      expect(result.payload.user.name).toBe('John');
    });

    it('should use partial redaction', async () => {
      const processor = new RedactionProcessor({
        id: 'redact-3',
        type: 'redact',
        config: {
          rules: [
            {
              type: 'credit_card',
              replacement: 'partial',
              partialLength: 4,
              partialPosition: 'end',
            },
          ],
        },
      });

      const event: Event = {
        id: '1',
        type: 'payment',
        timestamp: Date.now(),
        payload: { cardNumber: '1234-5678-9012-3456' },
      };

      const result = await processor.process(event, context);
      expect(result.payload.cardNumber).toMatch(/^\*+3456$/);
    });
  });

  describe('ProcessorRegistry', () => {
    it('should register and create built-in processors', async () => {
      const registry = new ProcessorRegistry();

      expect(registry.hasType('validate')).toBe(true);
      expect(registry.hasType('transform')).toBe(true);
      expect(registry.hasType('filter')).toBe(true);
      expect(registry.hasType('enrich')).toBe(true);
      expect(registry.hasType('redact')).toBe(true);
    });

    it('should create processor from config', async () => {
      const registry = new ProcessorRegistry();

      const processor = await registry.createProcessor({
        id: 'test-validate',
        type: 'validate',
        config: {
          schema: {
            type: 'object',
            properties: { name: { type: 'string' } },
          },
        },
      });

      expect(processor).toBeDefined();
      expect(processor.id).toBe('test-validate');
      expect(processor.type).toBe('validate');
    });

    it('should create processor chain', async () => {
      const registry = new ProcessorRegistry();

      const chain = await registry.createProcessorChain([
        {
          id: 'validate',
          type: 'validate',
          order: 1,
          config: { schema: { type: 'object' } },
        },
        {
          id: 'transform',
          type: 'transform',
          order: 2,
          config: { operations: [{ type: 'set', path: 'processed', value: true }] },
        },
        {
          id: 'enrich',
          type: 'enrich',
          order: 3,
          config: { operations: [{ type: 'timestamp', path: 'ts', format: 'iso' }] },
        },
      ]);

      expect(chain).toHaveLength(3);
      expect(chain[0].type).toBe('validate');
      expect(chain[1].type).toBe('transform');
      expect(chain[2].type).toBe('enrich');
    });
  });
});
