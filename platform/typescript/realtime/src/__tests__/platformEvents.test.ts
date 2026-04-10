import { describe, it, expect } from 'vitest';
import {
  PlatformEventSchema,
  EventSourceSchema,
} from '../events/types';

/**
 * Unit tests for @ghatana/realtime platform event base types.
 *
 * @doc.type module
 * @doc.purpose Validates platform event schema validation and type contracts
 * @doc.layer platform
 * @doc.pattern Test
 */

describe('EventSourceSchema', () => {
  it('validates a valid browser event source', () => {
    const result = EventSourceSchema.parse({ type: 'browser', id: 'tab-123' });
    expect(result.type).toBe('browser');
    expect(result.id).toBe('tab-123');
  });

  it('validates all valid source types', () => {
    const types = ['browser', 'server', 'client', 'extension'] as const;
    for (const type of types) {
      const result = EventSourceSchema.parse({ type, id: 'test-id' });
      expect(result.type).toBe(type);
    }
  });

  it('rejects invalid event source type', () => {
    expect(() => EventSourceSchema.parse({ type: 'unknown', id: 'x' })).toThrow();
  });

  it('rejects empty id', () => {
    expect(() => EventSourceSchema.parse({ type: 'browser', id: '' })).toThrow();
  });
});

describe('PlatformEventSchema', () => {
  const validEvent = {
    id: 'evt-001',
    type: 'tab.created',
    timestamp: Date.now(),
    source: { type: 'browser', id: 'tab-1' },
    data: { tabId: 42 },
  };

  it('validates a complete valid event', () => {
    const result = PlatformEventSchema.parse(validEvent);
    expect(result.id).toBe('evt-001');
    expect(result.type).toBe('tab.created');
    expect(result.source.type).toBe('browser');
  });

  it('allows optional correlationId', () => {
    const result = PlatformEventSchema.parse({
      ...validEvent,
      correlationId: 'req-xyz',
    });
    expect(result.correlationId).toBe('req-xyz');
  });

  it('omits correlationId when absent', () => {
    const result = PlatformEventSchema.parse(validEvent);
    expect(result.correlationId).toBeUndefined();
  });

  it('rejects event with empty id', () => {
    expect(() => PlatformEventSchema.parse({ ...validEvent, id: '' })).toThrow();
  });

  it('rejects event with non-positive timestamp', () => {
    expect(() => PlatformEventSchema.parse({ ...validEvent, timestamp: -1 })).toThrow();
  });

  it('rejects event with missing source', () => {
    const { source: _s, ...withoutSource } = validEvent;
    expect(() => PlatformEventSchema.parse(withoutSource)).toThrow();
  });

  it('allows data to be any unknown value', () => {
    const withStringData = { ...validEvent, data: 'plain string' };
    const result = PlatformEventSchema.parse(withStringData);
    expect(result.data).toBe('plain string');
  });
});
