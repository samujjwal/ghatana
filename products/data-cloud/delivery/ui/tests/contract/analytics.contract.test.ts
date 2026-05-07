import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import {
  AnalyticsQuerySchema,
  AnalyticsResultSchema,
  PaginatedAnalyticsResultSchema,
} from '../../src/contracts/schemas';

/**
 * Analytics API Contract Tests
 *
 * Validates that analytics API requests and responses conform to the expected schema.
 * Covers query validation, result aggregation, data point shapes, and error cases.
 *
 * @doc.type test
 * @doc.purpose Analytics API contract validation
 * @doc.layer testing
 */

describe('Analytics API Contract', () => {
  // ─── AnalyticsQuerySchema ───────────────────────────────────────────────

  describe('AnalyticsQuerySchema', () => {
    it('should accept a valid analytics query with all fields', () => {
      const validQuery = {
        metric: 'entity.save.latency',
        dimensions: ['tenantId', 'collection'],
        filters: { status: 'active' },
        startTime: '2026-01-01T00:00:00Z',
        endTime: '2026-01-02T00:00:00Z',
        granularity: 'hour',
        limit: 100,
      };

      const result = AnalyticsQuerySchema.safeParse(validQuery);
      expect(result.success).toBe(true);
    });

    it('should accept a minimal analytics query (required fields only)', () => {
      const minimalQuery = {
        metric: 'event.throughput',
        startTime: '2026-01-01T00:00:00Z',
        endTime: '2026-01-02T00:00:00Z',
        granularity: 'day',
      };

      const result = AnalyticsQuerySchema.safeParse(minimalQuery);
      expect(result.success).toBe(true);
    });

    it('should reject query with empty metric', () => {
      const invalidQuery = {
        metric: '',
        startTime: '2026-01-01T00:00:00Z',
        endTime: '2026-01-02T00:00:00Z',
        granularity: 'day',
      };

      const result = AnalyticsQuerySchema.safeParse(invalidQuery);
      expect(result.success).toBe(false);
    });

    it.each(['second', 'quarter', 'year', 'invalid'])(
      'should reject invalid granularity "%s"',
      (granularity) => {
        const invalidQuery = {
          metric: 'latency',
          startTime: '2026-01-01T00:00:00Z',
          endTime: '2026-01-02T00:00:00Z',
          granularity,
        };

        const result = AnalyticsQuerySchema.safeParse(invalidQuery);
        expect(result.success).toBe(false);
      }
    );

    it.each(['minute', 'hour', 'day', 'week', 'month'])(
      'should accept granularity "%s"',
      (granularity) => {
        const query = {
          metric: 'latency',
          startTime: '2026-01-01T00:00:00Z',
          endTime: '2026-01-02T00:00:00Z',
          granularity,
        };

        const result = AnalyticsQuerySchema.safeParse(query);
        expect(result.success).toBe(true);
      }
    );

    it('should reject query with non-positive limit', () => {
      const invalidQuery = {
        metric: 'throughput',
        startTime: '2026-01-01T00:00:00Z',
        endTime: '2026-01-02T00:00:00Z',
        granularity: 'hour',
        limit: 0,
      };

      const result = AnalyticsQuerySchema.safeParse(invalidQuery);
      expect(result.success).toBe(false);
    });
  });

  // ─── AnalyticsResultSchema ─────────────────────────────────────────────

  describe('AnalyticsResultSchema', () => {
    const buildValidResult = () => ({
      metric: 'entity.save.latency',
      unit: 'ms',
      dataPoints: [
        { timestamp: '2026-01-01T00:00:00Z', value: 42.5 },
        { timestamp: '2026-01-01T01:00:00Z', value: 38.1 },
      ],
      aggregation: {
        min: 38.1,
        max: 42.5,
        avg: 40.3,
        sum: 80.6,
        count: 2,
      },
      queryDurationMs: 15,
    });

    it('should accept a valid analytics result', () => {
      const result = AnalyticsResultSchema.safeParse(buildValidResult());
      expect(result.success).toBe(true);
    });

    it('should accept result without unit (optional)', () => {
      const resultWithoutUnit = { ...buildValidResult() };
      // @ts-expect-error — intentionally removing optional field
      delete resultWithoutUnit.unit;

      const result = AnalyticsResultSchema.safeParse(resultWithoutUnit);
      expect(result.success).toBe(true);
    });

    it('should accept result with empty dataPoints array', () => {
      const emptyResult = { ...buildValidResult(), dataPoints: [] };

      const result = AnalyticsResultSchema.safeParse(emptyResult);
      expect(result.success).toBe(true);
    });

    it('should accept dataPoint with optional dimensions', () => {
      const resultWithDimensions = {
        ...buildValidResult(),
        dataPoints: [
          {
            timestamp: '2026-01-01T00:00:00Z',
            value: 42.5,
            dimensions: { tenantId: 'tenant-a', collection: 'orders' },
          },
        ],
      };

      const result = AnalyticsResultSchema.safeParse(resultWithDimensions);
      expect(result.success).toBe(true);
    });

    it('should reject result missing aggregation', () => {
      const noAgg = buildValidResult();
      // @ts-expect-error — intentionally removing required field
      delete noAgg.aggregation;

      const result = AnalyticsResultSchema.safeParse(noAgg);
      expect(result.success).toBe(false);
    });

    it('should reject result where aggregation is missing min', () => {
      const brokenAgg = {
        ...buildValidResult(),
        aggregation: { max: 42.5, avg: 40.0, sum: 80.0, count: 2 }, // missing min
      };

      const result = AnalyticsResultSchema.safeParse(brokenAgg);
      expect(result.success).toBe(false);
    });
  });

  // ─── PaginatedAnalyticsResultSchema ─────────────────────────────────────

  describe('PaginatedAnalyticsResultSchema', () => {
    it('should accept a valid paginated analytics response', () => {
      const mockResponse = {
        results: [
          {
            metric: 'event.throughput',
            dataPoints: [{ timestamp: '2026-01-01T00:00:00Z', value: 1200 }],
            aggregation: { min: 1200, max: 1200, avg: 1200, sum: 1200, count: 1 },
            queryDurationMs: 7,
          },
        ],
        total: 1,
        page: 1,
        pageSize: 10,
        hasMore: false,
      };

      const result = PaginatedAnalyticsResultSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });

    it('should reject response missing total', () => {
      const invalidResponse = {
        results: [],
        page: 1,
        pageSize: 10,
        hasMore: false,
        // missing total
      };

      const result = PaginatedAnalyticsResultSchema.safeParse(invalidResponse);
      expect(result.success).toBe(false);
    });
  });
});
