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
