/**
 * Zod schemas for runtime validation of Data Cloud API contracts.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/validation
 * after validation in Data Cloud and AEP.
 *
 * @doc.type schema
 * @doc.purpose Runtime validation with Zod schemas for API contracts
 * @doc.layer frontend
 */

import { z } from 'zod';

/**
 * Collection status enum
 */
export const CollectionStatusSchema = z.enum(['active', 'inactive', 'archived']);

/**
 * Collection schema
 */
export const CollectionSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1).max(255),
  namespace: z.string().min(1),
  status: CollectionStatusSchema,
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  recordCount: z.number().int().nonnegative(),
  sizeBytes: z.number().int().nonnegative(),
});

/**
 * Workflow status enum
 */
// DC-32: Canonical execution status aligned with OperatorState
export const ExecutionStatusSchema = z.enum(['CREATED', 'INITIALIZED', 'RUNNING', 'STOPPED', 'FAILED']);

export const WorkflowStatusSchema = z.enum(['active', 'paused', 'completed', 'failed']);

/**
 * Workflow schema
 */
export const WorkflowSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1).max(255),
  status: WorkflowStatusSchema,
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  lastRunAt: z.string().datetime().nullable(),
  runCount: z.number().int().nonnegative(),
});

/**
 * Query schema
 */
export const QuerySchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1).max(255),
  sql: z.string().min(1),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  executionCount: z.number().int().nonnegative(),
});

/**
 * Dashboard schema
 */
export const DashboardSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1).max(255),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
  widgetCount: z.number().int().nonnegative(),
});

/**
 * User activity item schema
 */
export const UserActivityItemSchema = z.object({
  id: z.string().min(1),
  action: z.string().min(1),
  target: z.string().min(1),
  timestamp: z.string().datetime(),
  type: z.enum(['create', 'update', 'delete', 'query', 'alert']),
  resourceType: z.string().optional(),
  resourceId: z.string().optional(),
});

/**
 * Continue working item schema
 */
const ContinueWorkingItemTypeSchema = z
  .enum(['collection', 'workflow', 'query', 'insight', 'dashboard'])
  .transform((type) => (type === 'dashboard' ? 'insight' : type));

export const ContinueWorkingItemSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  type: ContinueWorkingItemTypeSchema,
  lastAccessed: z.string().datetime(),
  path: z.string().min(1),
});

/**
 * Pagination params schema
 */
export const PaginationParamsSchema = z.object({
  page: z.number().int().positive().default(1),
  pageSize: z.number().int().positive().max(100).default(20),
  sortBy: z.string().optional(),
  sortOrder: z.enum(['asc', 'desc']).default('desc'),
});

/**
 * Type exports
 */
export type Collection = z.infer<typeof CollectionSchema>;
export type Workflow = z.infer<typeof WorkflowSchema>;
export type Query = z.infer<typeof QuerySchema>;
export type Dashboard = z.infer<typeof DashboardSchema>;
export type UserActivityItem = z.infer<typeof UserActivityItemSchema>;
export type ContinueWorkingItem = z.infer<typeof ContinueWorkingItemSchema>;
export type PaginationParams = z.infer<typeof PaginationParamsSchema>;
