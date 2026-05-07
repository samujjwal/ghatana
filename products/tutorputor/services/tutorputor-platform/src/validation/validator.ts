/**
 * Input Validation Utilities
 *
 * Provides validation functions for common input types.
 * Uses Zod schemas for runtime validation.
 *
 * @doc.type module
 * @doc.purpose Input validation for data integrity
 * @doc.layer platform
 * @doc.pattern Utility
 */

import { z } from 'zod';

/**
 * Common validation schemas
 */
export const emailSchema = z
  .string()
  .email('Invalid email address')
  .max(255, 'Email address too long')
  .transform(val => val.toLowerCase().trim());

export const usernameSchema = z
  .string()
  .min(3, 'Username must be at least 3 characters')
  .max(50, 'Username must be at most 50 characters')
  .regex(/^[a-zA-Z0-9_-]+$/, 'Username can only contain letters, numbers, hyphens, and underscores')
  .transform(val => val.toLowerCase().trim());

export const passwordSchema = z
  .string()
  .min(8, 'Password must be at least 8 characters')
  .regex(/[A-Z]/, 'Password must contain at least one uppercase letter')
  .regex(/[a-z]/, 'Password must contain at least one lowercase letter')
  .regex(/[0-9]/, 'Password must contain at least one number');

export const uuidSchema = z
  .string()
  .uuid('Invalid UUID format');

export const moduleIdSchema = z
  .string()
  .min(1, 'Module ID is required')
  .max(100, 'Module ID too long');

export const tenantIdSchema = z
  .string()
  .min(1, 'Tenant ID is required')
  .max(100, 'Tenant ID too long');

export const userIdSchema = z
  .string()
  .min(1, 'User ID is required')
  .max(100, 'User ID too long');

/**
 * Pagination validation
 */
export const paginationSchema = z.object({
  page: z.coerce.number().int().positive().default(1),
  limit: z.coerce.number().int().positive().max(100).default(20),
});

/**
 * AI query validation
 */
export const aiQuerySchema = z.object({
  question: z.string().min(1, 'Question is required').max(1000, 'Question too long'),
  moduleId: moduleIdSchema,
  claimIds: z.array(z.string().min(1)).min(1, 'At least one claim ID is required'),
  currentSimulationState: z.record(z.string(), z.unknown()),
  recentAttempts: z.array(z.object({
    attemptId: z.string().min(1),
    taskId: z.string().optional(),
    correct: z.boolean().optional(),
    confidence: z.enum(['low', 'medium', 'high']).optional(),
    misconceptionId: z.string().optional(),
  })).min(1, 'At least one recent attempt is required'),
  misconceptions: z.array(z.string()),
  allowedHelpMode: z.enum(['hint', 'explain', 'socratic']),
  locale: z.string().optional(),
});

/**
 * Module creation/update validation
 */
export const moduleSchema = z.object({
  title: z.string().min(1, 'Title is required').max(200, 'Title too long'),
  description: z.string().min(1, 'Description is required').max(2000, 'Description too long'),
  domain: z.enum(['physics', 'chemistry', 'biology', 'mathematics', 'engineering']),
  difficulty: z.enum(['beginner', 'intermediate', 'advanced']),
  estimatedDuration: z.coerce.number().int().positive().max(10080, 'Duration too long (max 10080 minutes)'),
});

/**
 * User registration validation
 */
export const registrationSchema = z.object({
  email: emailSchema,
  password: passwordSchema,
  name: z.string().min(1, 'Name is required').max(100, 'Name too long'),
  username: usernameSchema.optional(),
});

/**
 * User login validation
 */
export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, 'Password is required'),
});

/**
 * Validate data against a schema
 */
export function validate<T>(schema: z.ZodSchema<T>, data: unknown): T {
  return schema.parse(data);
}

/**
 * Safely validate data against a schema (returns null on failure)
 */
export function safeValidate<T>(schema: z.ZodSchema<T>, data: unknown): T | null {
  const result = schema.safeParse(data);
  return result.success ? result.data : null;
}
