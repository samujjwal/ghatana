/**
 * Validation Types
 *
 * Type definitions for runtime schema validation with Zod.
 * Provides schemas for API responses, errors, and common data structures.
 *
 * @doc.type types
 * @doc.purpose Runtime validation type definitions
 * @doc.layer product
 * @doc.pattern Types
 */

import { z } from 'zod';

// ============================================================================
// Base Schemas
// ============================================================================

/**
 * API error schema
 */
export const ApiErrorSchema = z.object({
  code: z.string(),
  message: z.string(),
  details: z.record(z.string(), z.unknown()).optional(),
});

/**
 * API response schema
 */
export const ApiResponseSchema = z.object({
  success: z.boolean(),
  data: z.unknown().optional(),
  error: ApiErrorSchema.optional(),
  timestamp: z.string(),
});

/**
 * Pagination params schema
 */
export const PaginationParamsSchema = z.object({
  page: z.coerce.number().int().positive().default(1),
  pageSize: z.coerce.number().int().positive().max(100).default(10),
});

/**
 * Paginated response schema
 */
export const PaginatedResponseSchema = z.object({
  items: z.array(z.unknown()),
  totalItems: z.number().int().nonnegative(),
  page: z.number().int().positive(),
  pageSize: z.number().int().positive(),
  totalPages: z.number().int().nonnegative(),
  hasNext: z.boolean(),
  hasPrevious: z.boolean(),
});

// ============================================================================
// Type Exports
// ============================================================================

export type ApiError = z.infer<typeof ApiErrorSchema>;
export type ApiResponse<T = unknown> = z.infer<typeof ApiResponseSchema> & { data?: T };
export type PaginationParams = z.infer<typeof PaginationParamsSchema>;
export type PaginatedResponse<T = unknown> = z.infer<typeof PaginatedResponseSchema> & { items: T[] };
