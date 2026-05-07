import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import {
  EventSchema,
  PaginatedEventResponseSchema,
  AppendEventRequestSchema,
  AppendEventResponseSchema,
  EventQueryRequestSchema,
} from '../../src/contracts/schemas';

/**
 * Events API Contract Tests
 *
 * Validates that event API requests and responses conform to the expected schemas.
 * Covers event append, query, pagination, and error cases.
 *
 * @doc.type test
 * @doc.purpose Events API contract validation
 * @doc.layer testing
 */

describe('Events API Contract', () => {
  // ─── EventSchema ─────────────────────────────────────────────────────────

  describe('EventSchema', () => {
    const buildValidEvent = (overrides: Partial<z.infer<typeof EventSchema>> = {}) => ({
      id: 'evt-001',
      tenantId: 'tenant-abc',
      type: 'USER_SIGNED_UP',
      payload: { userId: 'u-1', email: 'test@example.com' },
      headers: { 'x-source-system': 'auth-service' },
      offset: 42,
      timestamp: '2026-01-15T10:30:00Z',
      source: 'auth-service',
      correlationId: 'corr-xyz',
      schemaVersion: '1.0',
      ...overrides,
    });

    it('should accept a valid event with all fields', () => {
      const result = EventSchema.safeParse(buildValidEvent());
      expect(result.success).toBe(true);
    });

    it('should accept event with only required fields', () => {
      const minimalEvent = {
        id: 'evt-min',
        tenantId: 'tenant-abc',
        type: 'MINIMAL_EVENT',
        payload: {},
        offset: 0,
        timestamp: '2026-01-15T10:00:00Z',
      };

      const result = EventSchema.safeParse(minimalEvent);
      expect(result.success).toBe(true);
    });

    it('should reject event with empty type', () => {
      const result = EventSchema.safeParse(buildValidEvent({ type: '' }));
      expect(result.success).toBe(false);
    });

    it('should accept event missing tenantId because tenant may be supplied by the enclosing response', () => {
      const eventWithoutTenant = {
        id: 'evt-001',
        type: 'SOME_EVENT',
        payload: {},
        offset: 1,
        timestamp: '2026-01-15T10:00:00Z',
      };

      const result = EventSchema.safeParse(eventWithoutTenant);
      expect(result.success).toBe(true);
    });

    it('should reject event with non-numeric offset', () => {
      const result = EventSchema.safeParse(buildValidEvent({ offset: 'not-a-number' as unknown as number }));
      expect(result.success).toBe(false);
    });

    it('should accept event with complex nested payload', () => {
      const complexEvent = buildValidEvent({
        payload: {
          user: { id: 'u-1', profile: { name: 'Alice', age: 30 } },
          tags: ['premium', 'verified'],
          metadata: null,
        },
      });

      const result = EventSchema.safeParse(complexEvent);
      expect(result.success).toBe(true);
    });
  });

  // ─── AppendEventRequestSchema ─────────────────────────────────────────────

  describe('AppendEventRequestSchema', () => {
    it('should accept a valid event append request', () => {
      const validRequest = {
        type: 'ORDER_PLACED',
        payload: { orderId: 'ord-123', total: 59.99 },
        headers: { 'x-trace-id': 'trace-abc' },
        source: 'order-service',
        correlationId: 'corr-001',
        schemaVersion: '2.1',
      };

      const result = AppendEventRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });

    it('should accept minimal append request with type and payload only', () => {
      const minRequest = {
        type: 'MINIMAL_EVENT',
        payload: { k: 'v' },
      };

      const result = AppendEventRequestSchema.safeParse(minRequest);
      expect(result.success).toBe(true);
    });

    it('should reject append request with empty type', () => {
      const result = AppendEventRequestSchema.safeParse({
        type: '',
        payload: { k: 'v' },
      });
      expect(result.success).toBe(false);
    });

    it('should reject append request missing type field', () => {
      const result = AppendEventRequestSchema.safeParse({ payload: { k: 'v' } });
      expect(result.success).toBe(false);
    });

    it('should reject append request missing payload field', () => {
      const result = AppendEventRequestSchema.safeParse({ type: 'TEST_EVENT' });
      expect(result.success).toBe(false);
    });
  });

  // ─── AppendEventResponseSchema ────────────────────────────────────────────

  describe('AppendEventResponseSchema', () => {
    it('should accept a valid append response', () => {
      const response = {
        offset: 100,
        type: 'ORDER_PLACED',
        timestamp: '2026-01-15T10:00:00Z',
      };

      const result = AppendEventResponseSchema.safeParse(response);
      expect(result.success).toBe(true);
    });

    it('should reject response missing offset', () => {
      const result = AppendEventResponseSchema.safeParse({
        type: 'ORDER_PLACED',
        timestamp: '2026-01-15T10:00:00Z',
      });
      expect(result.success).toBe(false);
    });

    it('should reject response where offset is non-numeric', () => {
      const result = AppendEventResponseSchema.safeParse({
        offset: 'abc',
        type: 'ORDER_PLACED',
        timestamp: '2026-01-15T10:00:00Z',
      });
      expect(result.success).toBe(false);
    });
  });

  // ─── EventQueryRequestSchema ──────────────────────────────────────────────

  describe('EventQueryRequestSchema', () => {
    it('should accept a valid event query with all filters', () => {
      const validQuery = {
        tenantId: 'tenant-abc',
        type: 'ORDER_PLACED',
        limit: 500,
        from: 0,
      };

      const result = EventQueryRequestSchema.safeParse(validQuery);
      expect(result.success).toBe(true);
    });

    it('should accept query with no type filter (all types)', () => {
      const result = EventQueryRequestSchema.safeParse({ limit: 50 });
      expect(result.success).toBe(true);
    });

    it('should reject query with negative from offset', () => {
      const result = EventQueryRequestSchema.safeParse({
        limit: 50,
        from: -1,
      });
      expect(result.success).toBe(false);
    });

    it('should reject query where limit exceeds 10000', () => {
      const result = EventQueryRequestSchema.safeParse({ limit: 10001 });
      expect(result.success).toBe(false);
    });

    it('should reject query with non-positive limit', () => {
      const result = EventQueryRequestSchema.safeParse({ limit: 0 });
      expect(result.success).toBe(false);
    });

    it('should accept query with no from offset', () => {
      const result = EventQueryRequestSchema.safeParse({ limit: 50 });
      expect(result.success).toBe(true);
    });

    it('should ignore a legacy offset alias that is not part of the canonical contract', () => {
      const result = EventQueryRequestSchema.safeParse({ limit: 50, offset: -1 });
      expect(result.success).toBe(true);
    });
  });

  // ─── PaginatedEventResponseSchema ────────────────────────────────────────

  describe('PaginatedEventResponseSchema', () => {
    it('should accept a valid paginated event response', () => {
      const mockResponse = {
        events: [
          {
            id: 'evt-001',
            tenantId: 'tenant-abc',
            type: 'ORDER_PLACED',
            payload: { orderId: 'ord-1' },
            offset: 1,
            timestamp: '2026-01-15T10:00:00Z',
          },
        ],
        count: 1,
        fromOffset: 1,
        nextOffset: 2,
        tenantId: 'tenant-abc',
        timestamp: '2026-01-15T10:00:01Z',
      };

      const result = PaginatedEventResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });

    it('should accept an empty paginated response envelope', () => {
      const mockResponse = {
        events: [],
        count: 0,
        fromOffset: 0,
        nextOffset: 0,
        tenantId: 'tenant-abc',
        timestamp: '2026-01-15T10:00:01Z',
      };

      const result = PaginatedEventResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });

    it('should reject paginated response with invalid event in items', () => {
      const mockResponse = {
        events: [
          { id: 'evt-bad', type: '' }, // missing required fields, empty type
        ],
        count: 1,
        fromOffset: 0,
        nextOffset: 1,
        tenantId: 'tenant-abc',
        timestamp: '2026-01-15T10:00:01Z',
      };

      const result = PaginatedEventResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(false);
    });

    it('should reject paginated response missing total', () => {
      const invalid = {
        events: [],
        fromOffset: 0,
        nextOffset: 0,
        tenantId: 'tenant-abc',
        timestamp: '2026-01-15T10:00:01Z',
      };

      const result = PaginatedEventResponseSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });
  });
});
