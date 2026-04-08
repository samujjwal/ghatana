/**
 * Zod schemas for runtime validation of AEP API contracts.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/validation
 * after validation in AEP and Data Cloud.
 *
 * @doc.type schema
 * @doc.purpose Runtime validation with Zod schemas for API contracts
 * @doc.layer frontend
 */

import { z } from 'zod';

/**
 * Pipeline run status enum
 */
export const PipelineRunStatusSchema = z.enum(['RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED']);

/**
 * Pipeline run schema
 */
export const PipelineRunSchema = z.object({
  id: z.string().min(1),
  pipelineId: z.string().min(1),
  pipelineName: z.string().min(1),
  status: PipelineRunStatusSchema,
  startedAt: z.string().datetime(),
  finishedAt: z.string().datetime().nullable(),
  eventsProcessed: z.number().int().nonnegative(),
  errorsCount: z.number().int().nonnegative(),
});

/**
 * Pipeline metrics schema
 */
export const PipelineMetricsSchema = z.object({
  pipelineId: z.string().min(1),
  pipelineName: z.string().min(1),
  throughputPerSec: z.number().nonnegative(),
  errorRate: z.number().min(0).max(1),
  avgLatencyMs: z.number().nonnegative(),
  activeRuns: z.number().int().nonnegative(),
  totalRuns: z.number().int().nonnegative(),
});

/**
 * Review item status enum
 */
export const ReviewItemStatusSchema = z.enum(['PENDING', 'APPROVED', 'REJECTED']);

/**
 * Review item type enum
 */
export const ReviewItemTypeSchema = z.enum(['POLICY', 'PATTERN', 'AGENT_DECISION']);

/**
 * Review item schema
 */
export const ReviewItemSchema = z.object({
  reviewId: z.string().min(1),
  itemType: ReviewItemTypeSchema,
  runId: z.string().min(1),
  pipelineId: z.string().min(1),
  confidenceScore: z.number().min(0).max(1),
  createdAt: z.string().datetime(),
  description: z.string().min(1),
  status: ReviewItemStatusSchema,
});

/**
 * Learned policy status enum
 */
export const LearnedPolicyStatusSchema = z.enum([
  'PENDING_REVIEW',
  'APPROVED',
  'REJECTED',
  'ACTIVE',
  'DEPRECATED',
]);

/**
 * Learned policy schema
 */
export const LearnedPolicySchema = z.object({
  id: z.string().min(1),
  skillId: z.string().min(1),
  version: z.number().int().positive(),
  confidenceScore: z.number().min(0).max(1),
  status: LearnedPolicyStatusSchema,
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
});

/**
 * Consent record schema
 */
export const ConsentRecordSchema = z.object({
  id: z.string().min(1),
  userId: z.string().min(1),
  purpose: z.enum(['voice_processing', 'ai_suggestions', 'analytics']),
  granted: z.boolean(),
  timestamp: z.string().datetime(),
  expiresAt: z.string().datetime().nullable(),
  metadata: z.record(z.string(), z.unknown()).optional(),
});

/**
 * Audit event schema
 */
export const AuditEventSchema = z.object({
  id: z.string().min(1),
  timestamp: z.string().datetime(),
  userId: z.string().min(1),
  action: z.string().min(1),
  resource: z.string().min(1),
  resourceId: z.string().min(1),
  details: z.record(z.string(), z.unknown()).optional(),
});

/**
 * AI suggestion type enum
 */
export const AiSuggestionTypeSchema = z.enum(['anomaly', 'optimization', 'warning', 'recommendation']);

/**
 * AI suggestion severity enum
 */
export const AiSuggestionSeveritySchema = z.enum(['low', 'medium', 'high', 'critical']);

/**
 * AI suggestion schema
 */
export const AiSuggestionSchema = z.object({
  id: z.string().min(1),
  type: AiSuggestionTypeSchema,
  severity: AiSuggestionSeveritySchema,
  message: z.string().min(1),
  resourceId: z.string().min(1).optional(),
  resourceType: z.enum(['pipeline', 'run', 'agent', 'policy']).optional(),
  confidence: z.number().min(0).max(1).optional(),
});

/**
 * Pagination params schema
 */
export const PaginationParamsSchema = z.object({
  page: z.number().int().positive().default(1),
  limit: z.number().int().positive().max(100).default(20),
  sortBy: z.string().optional(),
  sortOrder: z.enum(['asc', 'desc']).default('desc'),
});

/**
 * Type exports
 */
export type PipelineRun = z.infer<typeof PipelineRunSchema>;
export type PipelineMetrics = z.infer<typeof PipelineMetricsSchema>;
export type ReviewItem = z.infer<typeof ReviewItemSchema>;
export type LearnedPolicy = z.infer<typeof LearnedPolicySchema>;
export type ConsentRecord = z.infer<typeof ConsentRecordSchema>;
export type AuditEvent = z.infer<typeof AuditEventSchema>;
export type AiSuggestion = z.infer<typeof AiSuggestionSchema>;
export type PaginationParams = z.infer<typeof PaginationParamsSchema>;
