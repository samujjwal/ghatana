/**
 * @tutorputor/validation — Learning schemas
 *
 * Zod schemas for learning-domain request/response validation.
 * Used by `/api/v1/learning`, `/api/v1/modules`, `/api/v1/enrollments`,
 * and `/api/v1/assessments` routes.
 *
 * @doc.type module
 * @doc.purpose Learning request/response validation schemas
 * @doc.layer product
 * @doc.pattern ValueObject
 */

import { z } from "zod";
import { DifficultySchema, DomainSchema, ModuleIdSchema, PaginationQuerySchema } from "./common.js";

// ---------------------------------------------------------------------------
// Module list query
// ---------------------------------------------------------------------------

export const ListModulesQuerySchema = PaginationQuerySchema.extend({
  domain: DomainSchema.optional(),
  difficulty: DifficultySchema.optional(),
  query: z.string().trim().max(200).optional(),
  status: z.literal("PUBLISHED").optional(),
}).strict();
export type ListModulesQuery = z.infer<typeof ListModulesQuerySchema>;

// ---------------------------------------------------------------------------
// Enrollment
// ---------------------------------------------------------------------------

export const EnrollRequestSchema = z
  .object({
    moduleId: ModuleIdSchema,
  })
  .strict();
export type EnrollRequest = z.infer<typeof EnrollRequestSchema>;

// ---------------------------------------------------------------------------
// Assessment submission
// ---------------------------------------------------------------------------

export const AssessmentAnswerSchema = z.object({
  itemId: z.string().uuid("itemId must be a valid UUID"),
  value: z.unknown(),
  confidencePercent: z.number().int().min(0).max(100).optional(),
  timeSpentSeconds: z.number().int().nonnegative().optional(),
});
export type AssessmentAnswer = z.infer<typeof AssessmentAnswerSchema>;

export const SubmitAttemptRequestSchema = z
  .object({
    answers: z.array(AssessmentAnswerSchema).min(1, "At least one answer is required"),
  })
  .strict();
export type SubmitAttemptRequest = z.infer<typeof SubmitAttemptRequestSchema>;

// ---------------------------------------------------------------------------
// Learning event
// ---------------------------------------------------------------------------

export const LearningEventInputSchema = z
  .object({
    type: z.string().min(1),
    moduleId: z.string().uuid().optional(),
    enrollmentId: z.string().uuid().optional(),
    sessionId: z.string().uuid(),
    timestamp: z.string().datetime({ offset: true }).optional(),
    payload: z.record(z.string(), z.unknown()).optional(),
  })
  .strict();
export type LearningEventInput = z.infer<typeof LearningEventInputSchema>;

// ---------------------------------------------------------------------------
// Validation helpers
// ---------------------------------------------------------------------------

export function parseListModulesQuery(input: unknown): ListModulesQuery {
  return ListModulesQuerySchema.parse(input);
}

export function parseEnrollRequest(input: unknown): EnrollRequest {
  return EnrollRequestSchema.parse(input);
}

export function parseSubmitAttemptRequest(input: unknown): SubmitAttemptRequest {
  return SubmitAttemptRequestSchema.parse(input);
}
