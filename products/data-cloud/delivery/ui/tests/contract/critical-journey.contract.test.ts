import { describe,expect,it } from 'vitest';
import { z } from 'zod';
import {
CollectionContextResponseSchema,
CollectionEntityListResponseSchema,
DataQualityTrustScoresResponseSchema,
} from '../../src/contracts/schemas';

const ConnectorSyncTriggerResponseSchema = z.object({
  jobId: z.string().min(1),
});

const ConnectorSyncStatisticsSchema = z.object({
  connectorId: z.string(),
  totalRecords: z.number().int().nonnegative(),
  lastSyncRecords: z.number().int().nonnegative(),
  totalDuration: z.number().int().nonnegative(),
  lastSyncDuration: z.number().int().nonnegative(),
  errorCount: z.number().int().nonnegative(),
  lastError: z.string().optional(),
});

/**
 * Critical Journey Contract Tests
 *
 * Validates backend-owned contract continuity for the canonical user flow:
 * connector -> sync -> collection -> schema -> quality -> lineage -> trust.
 */
describe('Critical Journey Contract', () => {
  it('validates connector sync trigger and statistics contracts', () => {
    const trigger = ConnectorSyncTriggerResponseSchema.safeParse({
      jobId: 'sync-job-001',
    });

    const stats = ConnectorSyncStatisticsSchema.safeParse({
      connectorId: 'connector-orders',
      totalRecords: 12000,
      lastSyncRecords: 480,
      totalDuration: 7200,
      lastSyncDuration: 180,
      errorCount: 0,
    });

    expect(trigger.success).toBe(true);
    expect(stats.success).toBe(true);
  });

  it('validates collection registry metadata contract after sync', () => {
    const collectionList = CollectionEntityListResponseSchema.safeParse({
      entities: [
        {
          id: 'dc-col-orders',
          data: {
            name: 'Orders',
            description: 'Canonical collection created from connector sync',
            schemaType: 'entity',
            status: 'active',
            entityCount: 12000,
            schema: {
              fields: [
                { name: 'order_id', type: 'string', required: true },
                { name: 'total_amount', type: 'number', required: true },
              ],
            },
            lifecycleStatus: 'PUBLISHED',
            operationalStatus: 'healthy',
            qualityScore: 0.93,
            qualityMetrics: {
              completeness: 0.98,
              consistency: 0.94,
            },
            owner: 'data-platform',
            storageSizeBytes: 1048576,
            tags: ['sales', 'trusted'],
            createdBy: 'connector-runtime',
          },
          createdAt: '2026-05-28T10:00:00Z',
          updatedAt: '2026-05-28T10:10:00Z',
        },
      ],
      count: 1,
    });

    expect(collectionList.success).toBe(true);
  });

  it('validates schema and lineage context contract for the collection', () => {
    const context = CollectionContextResponseSchema.safeParse({
      collection: 'dc-col-orders',
      tenantId: 'tenant-a',
      requestId: 'ctx-req-001',
      generatedAt: '2026-05-28T10:11:00Z',
      generationTimeMs: 42,
      schema: {
        fields: [
          { name: 'order_id', type: 'string', required: true },
          { name: 'total_amount', type: 'number', required: true },
        ],
      },
      lineage: {
        upstream: ['connector-orders', 'raw-orders-topic'],
        downstream: ['daily-sales-mart', 'trust-score-model'],
      },
      governance: {
        retentionTier: 'gold',
        complianceStatus: 'compliant',
        piiFields: [],
      },
      freshness: {
        sampledAt: '2026-05-28T10:11:00Z',
        lastEntityUpdatedAt: '2026-05-28T10:10:00Z',
      },
      statisticalProfile: {
        entityCount: 12000,
        sampleSize: 500,
        nullRates: {
          order_id: 0,
          total_amount: 0.01,
        },
        topValues: {
          status: [
            { value: 'COMPLETE', count: 420 },
            { value: 'PENDING', count: 80 },
          ],
        },
      },
      relationships: [
        {
          id: 'rel-1',
          source: 'dc-col-orders',
          target: 'daily-sales-mart',
          type: 'feeds',
        },
      ],
    });

    expect(context.success).toBe(true);
  });

  it('validates quality and trust envelope contract for journey outputs', () => {
    const qualityTrust = DataQualityTrustScoresResponseSchema.safeParse({
      data: {
        tenantId: 'tenant-a',
        count: 1,
        generatedAt: '2026-05-28T10:12:00Z',
        scores: [
          {
            collection: 'dc-col-orders',
            qualityScore: 0.93,
            trustScore: 91,
            lifecycleStatus: 'PUBLISHED',
            operationalStatus: 'healthy',
            qualityMetrics: {
              completeness: 0.98,
              consistency: 0.94,
            },
            computedAt: '2026-05-28T10:12:00Z',
          },
        ],
      },
      meta: {
        requestId: 'qt-req-001',
        tenantId: 'tenant-a',
        timestamp: '2026-05-28T10:12:00Z',
        apiVersion: 'v1',
      },
    });

    expect(qualityTrust.success).toBe(true);
  });
});