/**
 * API Contract Schemas
 *
 * Single source of truth for API response/request shapes.
 * Used by contract tests to validate correctness and by MSW handlers
 * to guarantee mock data matches real API contracts.
 *
 * @doc.type config
 * @doc.purpose Zod schemas defining frontend-backend API contracts
 * @doc.layer frontend
 * @doc.pattern Contract / Schema
 */

import { z } from 'zod';

// ---------------------------------------------------------------------------
// Collections
// ---------------------------------------------------------------------------

export const CollectionSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  schemaType: z.enum(['entity', 'event', 'timeseries', 'graph', 'document']),
  status: z.enum(['active', 'draft', 'archived', 'processing']),
  entityCount: z.number(),
  schema: z.record(z.string(), z.unknown()),
  tags: z.array(z.string()),
  createdAt: z.string(),
  updatedAt: z.string(),
  createdBy: z.string(),
});

export const PaginatedCollectionResponseSchema = z.object({
  items: z.array(CollectionSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  hasMore: z.boolean(),
});

export const CreateCollectionRequestSchema = z.object({
  name: z.string().min(1),
  description: z.string(),
  schemaType: z.enum(['entity', 'event', 'timeseries', 'graph', 'document']),
  schema: z.record(z.string(), z.unknown()),
  tags: z.array(z.string()).optional(),
});

export const UpdateCollectionRequestSchema = z.object({
  name: z.string().min(1).optional(),
  description: z.string().optional(),
  schema: z.record(z.string(), z.unknown()).optional(),
  tags: z.array(z.string()).optional(),
  status: z.enum(['active', 'draft', 'archived', 'processing']).optional(),
});

// ---------------------------------------------------------------------------
// Workflows
// ---------------------------------------------------------------------------

export const WorkflowSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  status: z.enum(['active', 'inactive', 'draft']),
  executionCount: z.number(),
  lastExecutedAt: z.string().optional(),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const PaginatedWorkflowResponseSchema = z.object({
  items: z.array(WorkflowSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  hasMore: z.boolean(),
});

export const CreateWorkflowRequestSchema = z.object({
  name: z.string().min(1),
  description: z.string(),
  definition: z.record(z.string(), z.unknown()).optional(),
});

export const ExecutionSchema = z.object({
  id: z.string(),
  workflowId: z.string(),
  status: z.enum(['pending', 'running', 'completed', 'failed', 'cancelled']),
  startedAt: z.string(),
  completedAt: z.string().optional(),
  duration: z.number().optional(),
  input: z.record(z.string(), z.unknown()).optional(),
  output: z.record(z.string(), z.unknown()).optional(),
  error: z.string().optional(),
});

// ---------------------------------------------------------------------------
// Data Fabric — Storage Profiles
// ---------------------------------------------------------------------------

export const StorageProfileSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: z.string(),
  isDefault: z.boolean(),
  status: z.string(),
  config: z.record(z.string(), z.unknown()),
  createdAt: z.string(),
  updatedAt: z.string(),
});

export const StorageProfileMetricsSchema = z.object({
  storageUsedBytes: z.number(),
  storageTotalBytes: z.number(),
  readOpsPerSec: z.number(),
  writeOpsPerSec: z.number(),
  latencyP99Ms: z.number(),
  lastUpdated: z.string(),
});

// ---------------------------------------------------------------------------
// Data Fabric — Connectors
// ---------------------------------------------------------------------------

export const ConnectorSchema = z.object({
  id: z.string(),
  name: z.string(),
  type: z.string(),
  storageProfileId: z.string(),
  status: z.string(),
  config: z.record(z.string(), z.unknown()),
  createdAt: z.string(),
  updatedAt: z.string(),
});

// ---------------------------------------------------------------------------
// Paginated generic
// ---------------------------------------------------------------------------

export function paginatedSchema<T extends z.ZodTypeAny>(itemSchema: T) {
  return z.object({
    items: z.array(itemSchema),
    total: z.number(),
    page: z.number(),
    pageSize: z.number(),
    hasMore: z.boolean(),
  });
}

// ---------------------------------------------------------------------------
// Analytics
// ---------------------------------------------------------------------------

export const AnalyticsQuerySchema = z.object({
  metric: z.string().min(1),
  dimensions: z.array(z.string()).optional(),
  filters: z.record(z.string(), z.string()).optional(),
  startTime: z.string(),
  endTime: z.string(),
  granularity: z.enum(['minute', 'hour', 'day', 'week', 'month']),
  limit: z.number().int().positive().optional(),
});

export const AnalyticsDataPointSchema = z.object({
  timestamp: z.string(),
  value: z.number(),
  dimensions: z.record(z.string(), z.string()).optional(),
});

export const AnalyticsResultSchema = z.object({
  metric: z.string(),
  unit: z.string().optional(),
  dataPoints: z.array(AnalyticsDataPointSchema),
  aggregation: z.object({
    min: z.number(),
    max: z.number(),
    avg: z.number(),
    sum: z.number(),
    count: z.number(),
  }),
  queryDurationMs: z.number(),
});

export const PaginatedAnalyticsResultSchema = z.object({
  results: z.array(AnalyticsResultSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  hasMore: z.boolean(),
});

// ---------------------------------------------------------------------------
// Events
// ---------------------------------------------------------------------------

export const EventSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  type: z.string().min(1),
  payload: z.record(z.string(), z.unknown()),
  headers: z.record(z.string(), z.string()).optional(),
  offset: z.number(),
  timestamp: z.string(),
  source: z.string().optional(),
  correlationId: z.string().optional(),
  schemaVersion: z.string().optional(),
});

export const AppendEventRequestSchema = z.object({
  type: z.string().min(1),
  payload: z.record(z.string(), z.unknown()),
  headers: z.record(z.string(), z.string()).optional(),
  source: z.string().optional(),
  correlationId: z.string().optional(),
  schemaVersion: z.string().optional(),
});

export const AppendEventResponseSchema = z.object({
  offset: z.number(),
  eventId: z.string(),
  timestamp: z.string(),
});

export const EventQueryRequestSchema = z.object({
  eventTypes: z.array(z.string()).min(1).optional(),
  startTime: z.string().optional(),
  endTime: z.string().optional(),
  limit: z.number().int().positive().max(10000).default(100),
  offset: z.number().int().nonnegative().optional(),
  filters: z.record(z.string(), z.unknown()).optional(),
});

export const PaginatedEventResponseSchema = z.object({
  items: z.array(EventSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
  hasMore: z.boolean(),
  nextOffset: z.number().optional(),
});

// ---------------------------------------------------------------------------
// Storage Profile — Create / Update requests
// ---------------------------------------------------------------------------

export const CreateStorageProfileRequestSchema = z.object({
  name: z.string().min(1),
  type: z.enum(['postgresql', 'timescaledb', 'clickhouse', 's3', 'gcs', 'azure-blob', 'in-memory']),
  config: z.record(z.string(), z.unknown()),
  isDefault: z.boolean().optional(),
});

export const UpdateStorageProfileRequestSchema = z.object({
  name: z.string().min(1).optional(),
  config: z.record(z.string(), z.unknown()).optional(),
  isDefault: z.boolean().optional(),
  status: z.enum(['active', 'inactive', 'maintenance']).optional(),
});

// ---------------------------------------------------------------------------
// Connectors — Create / Update requests
// ---------------------------------------------------------------------------

export const ConnectorTypeSchema = z.enum([
  'kafka', 'rabbitmq', 'sqs', 'pubsub', 'http-webhook', 'grpc', 'jdbc', 'custom',
]);

export const CreateConnectorRequestSchema = z.object({
  name: z.string().min(1),
  type: ConnectorTypeSchema,
  storageProfileId: z.string(),
  config: z.record(z.string(), z.unknown()),
});

export const UpdateConnectorRequestSchema = z.object({
  name: z.string().min(1).optional(),
  config: z.record(z.string(), z.unknown()).optional(),
  status: z.enum(['active', 'inactive', 'error']).optional(),
});

// ---------------------------------------------------------------------------
// Type exports
// ---------------------------------------------------------------------------

export type Collection = z.infer<typeof CollectionSchema>;
export type PaginatedCollectionResponse = z.infer<typeof PaginatedCollectionResponseSchema>;
export type Workflow = z.infer<typeof WorkflowSchema>;
export type PaginatedWorkflowResponse = z.infer<typeof PaginatedWorkflowResponseSchema>;
export type Execution = z.infer<typeof ExecutionSchema>;
export type StorageProfile = z.infer<typeof StorageProfileSchema>;
export type StorageProfileMetrics = z.infer<typeof StorageProfileMetricsSchema>;
export type Connector = z.infer<typeof ConnectorSchema>;
export type AnalyticsQuery = z.infer<typeof AnalyticsQuerySchema>;
export type AnalyticsDataPoint = z.infer<typeof AnalyticsDataPointSchema>;
export type AnalyticsResult = z.infer<typeof AnalyticsResultSchema>;
export type PaginatedAnalyticsResult = z.infer<typeof PaginatedAnalyticsResultSchema>;
export type Event = z.infer<typeof EventSchema>;
export type AppendEventRequest = z.infer<typeof AppendEventRequestSchema>;
export type AppendEventResponse = z.infer<typeof AppendEventResponseSchema>;
export type EventQueryRequest = z.infer<typeof EventQueryRequestSchema>;
export type PaginatedEventResponse = z.infer<typeof PaginatedEventResponseSchema>;
export type CreateStorageProfileRequest = z.infer<typeof CreateStorageProfileRequestSchema>;
export type UpdateStorageProfileRequest = z.infer<typeof UpdateStorageProfileRequestSchema>;
export type ConnectorType = z.infer<typeof ConnectorTypeSchema>;
export type CreateConnectorRequest = z.infer<typeof CreateConnectorRequestSchema>;
export type UpdateConnectorRequest = z.infer<typeof UpdateConnectorRequestSchema>;
