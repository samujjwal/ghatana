import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import {
  ConnectorSchema,
  ConnectorTypeSchema,
  CreateConnectorRequestSchema,
  UpdateConnectorRequestSchema,
} from '../../src/contracts/schemas';

/**
 * Connectors API Contract Tests
 *
 * Validates that connector API requests and responses conform to the expected schemas.
 * Covers connector types, CRUD operations, and error cases.
 *
 * @doc.type test
 * @doc.purpose Connectors API contract validation
 * @doc.layer testing
 */

describe('Connectors API Contract', () => {
  // ─── ConnectorSchema ──────────────────────────────────────────────────────

  describe('ConnectorSchema', () => {
    const buildValidConnector = (overrides: Partial<z.infer<typeof ConnectorSchema>> = {}) => ({
      id: 'conn-001',
      name: 'Kafka Ingest',
      type: 'kafka',
      storageProfileId: 'sp-001',
      status: 'active',
      config: { brokers: 'kafka:9092', topic: 'events.raw' },
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-10T08:00:00Z',
      ...overrides,
    });

    it('should accept a valid connector', () => {
      const result = ConnectorSchema.safeParse(buildValidConnector());
      expect(result.success).toBe(true);
    });

    it('should accept connector with empty config', () => {
      const result = ConnectorSchema.safeParse(buildValidConnector({ config: {} }));
      expect(result.success).toBe(true);
    });

    it('should reject connector missing storageProfileId', () => {
      const invalid = buildValidConnector();
      // @ts-expect-error — testing missing required field
      delete invalid.storageProfileId;
      const result = ConnectorSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('should reject connector missing id', () => {
      const invalid = buildValidConnector();
      // @ts-expect-error — testing missing required field
      delete invalid.id;
      const result = ConnectorSchema.safeParse(invalid);
      expect(result.success).toBe(false);
    });

    it('should reject connector with non-string status', () => {
      const result = ConnectorSchema.safeParse(buildValidConnector({ status: 123 as unknown as string }));
      expect(result.success).toBe(false);
    });
  });

  // ─── ConnectorTypeSchema ──────────────────────────────────────────────────

  describe('ConnectorTypeSchema', () => {
    it.each(['kafka', 'rabbitmq', 'sqs', 'pubsub', 'http-webhook', 'grpc', 'jdbc', 'custom'])(
      'should accept connector type "%s"',
      (type) => {
        const result = ConnectorTypeSchema.safeParse(type);
        expect(result.success).toBe(true);
      }
    );

    it.each(['kafka2', 'mqtt', 'nats', 'redis', 'kinesis'])(
      'should reject unknown connector type "%s"',
      (type) => {
        const result = ConnectorTypeSchema.safeParse(type);
        expect(result.success).toBe(false);
      }
    );
  });

  // ─── CreateConnectorRequestSchema ────────────────────────────────────────

  describe('CreateConnectorRequestSchema', () => {
    it('should accept a valid create connector request for Kafka', () => {
      const validRequest = {
        name: 'Kafka Events Ingest',
        type: 'kafka',
        storageProfileId: 'sp-001',
        config: {
          brokers: 'kafka.internal:9092',
          topic: 'data-cloud.events',
          groupId: 'dc-consumer-group',
        },
      };

      const result = CreateConnectorRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });

    it('should accept create request for custom connector type', () => {
      const result = CreateConnectorRequestSchema.safeParse({
        name: 'Custom Connector',
        type: 'custom',
        storageProfileId: 'sp-002',
        config: { endpoint: 'https://internal.service/ingest' },
      });
      expect(result.success).toBe(true);
    });

    it('should reject create request with empty name', () => {
      const result = CreateConnectorRequestSchema.safeParse({
        name: '',
        type: 'kafka',
        storageProfileId: 'sp-001',
        config: {},
      });
      expect(result.success).toBe(false);
    });

    it('should reject create request with invalid connector type', () => {
      const result = CreateConnectorRequestSchema.safeParse({
        name: 'Invalid Type Connector',
        type: 'mqtt', // not in enum
        storageProfileId: 'sp-001',
        config: {},
      });
      expect(result.success).toBe(false);
    });

    it('should reject create request missing storageProfileId', () => {
      const result = CreateConnectorRequestSchema.safeParse({
        name: 'New Connector',
        type: 'sqs',
        config: { queue: 'my-queue' },
        // storageProfileId missing
      });
      expect(result.success).toBe(false);
    });

    it('should reject create request missing config', () => {
      const result = CreateConnectorRequestSchema.safeParse({
        name: 'Connector',
        type: 'rabbitmq',
        storageProfileId: 'sp-003',
        // config missing
      });
      expect(result.success).toBe(false);
    });

    it('should accept create request with empty config object', () => {
      const result = CreateConnectorRequestSchema.safeParse({
        name: 'Minimal Connector',
        type: 'http-webhook',
        storageProfileId: 'sp-001',
        config: {},
      });
      expect(result.success).toBe(true);
    });
  });

  // ─── UpdateConnectorRequestSchema ────────────────────────────────────────

  describe('UpdateConnectorRequestSchema', () => {
    it('should accept full update request', () => {
      const validRequest = {
        name: 'Updated Kafka Connector',
        config: { brokers: 'new-kafka:9092', topic: 'updated-topic' },
        status: 'inactive',
      };

      const result = UpdateConnectorRequestSchema.safeParse(validRequest);
      expect(result.success).toBe(true);
    });

    it('should accept partial update with only name', () => {
      const result = UpdateConnectorRequestSchema.safeParse({ name: 'Renamed Connector' });
      expect(result.success).toBe(true);
    });

    it('should accept partial update with only status', () => {
      const result = UpdateConnectorRequestSchema.safeParse({ status: 'error' });
      expect(result.success).toBe(true);
    });

    it.each(['active', 'inactive', 'error'])(
      'should accept connector status "%s"',
      (status) => {
        const result = UpdateConnectorRequestSchema.safeParse({ status });
        expect(result.success).toBe(true);
      }
    );

    it('should reject invalid connector status', () => {
      const result = UpdateConnectorRequestSchema.safeParse({ status: 'paused' });
      expect(result.success).toBe(false);
    });

    it('should reject update with empty name', () => {
      const result = UpdateConnectorRequestSchema.safeParse({ name: '' });
      expect(result.success).toBe(false);
    });

    it('should accept empty update object (no-op patch)', () => {
      const result = UpdateConnectorRequestSchema.safeParse({});
      expect(result.success).toBe(true);
    });
  });

  // ─── GET list (paginated) ──────────────────────────────────────────────────

  describe('GET /api/v1/connectors', () => {
    it('should accept paginated connectors response', () => {
      const PaginatedConnectorResponseSchema = z.object({
        items: z.array(ConnectorSchema),
        total: z.number(),
        page: z.number(),
        pageSize: z.number(),
        hasMore: z.boolean(),
      });

      const mockResponse = {
        items: [
          {
            id: 'conn-001',
            name: 'Kafka Ingest',
            type: 'kafka',
            storageProfileId: 'sp-001',
            status: 'active',
            config: { brokers: 'kafka:9092' },
            createdAt: '2026-01-01T00:00:00Z',
            updatedAt: '2026-01-10T08:00:00Z',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 20,
        hasMore: false,
      };

      const result = PaginatedConnectorResponseSchema.safeParse(mockResponse);
      expect(result.success).toBe(true);
    });

    it('should accept empty connector list', () => {
      const PaginatedConnectorResponseSchema = z.object({
        items: z.array(ConnectorSchema),
        total: z.number(),
        page: z.number(),
        pageSize: z.number(),
        hasMore: z.boolean(),
      });

      const result = PaginatedConnectorResponseSchema.safeParse({
        items: [],
        total: 0,
        page: 1,
        pageSize: 20,
        hasMore: false,
      });
      expect(result.success).toBe(true);
    });
  });

  // ─── DELETE response ──────────────────────────────────────────────────────

  describe('DELETE /api/v1/connectors/:id', () => {
    it('should return success delete response', () => {
      const DeleteResponseSchema = z.object({ success: z.boolean() });
      const result = DeleteResponseSchema.safeParse({ success: true });
      expect(result.success).toBe(true);
    });
  });
});
