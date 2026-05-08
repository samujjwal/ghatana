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

// =========================================================================
// Schema Versioning (TODO 14)
// =========================================================================

export const LEARNING_EVENT_SCHEMA_VERSION = "1.0.0";

// =========================================================================
// Module list query
// =========================================================================

export const ListModulesQuerySchema = PaginationQuerySchema.extend({
  domain: DomainSchema.optional(),
  difficulty: DifficultySchema.optional(),
  query: z.string().trim().max(200).optional(),
  status: z.literal("PUBLISHED").optional(),
}).strict();
export type ListModulesQuery = z.infer<typeof ListModulesQuerySchema>;

// =========================================================================
// Enrollment
// =========================================================================

export const EnrollRequestSchema = z
  .object({
    moduleId: ModuleIdSchema,
  })
  .strict();
export type EnrollRequest = z.infer<typeof EnrollRequestSchema>;

// =========================================================================
// Assessment submission
// =========================================================================

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

// =========================================================================
// Versioned Learning Event Schemas (TODO 14)
// =========================================================================

// Base event structure
export const BaseLearningEventSchema = z.object({
  schemaVersion: z.literal(LEARNING_EVENT_SCHEMA_VERSION),
  eventType: z.string().min(1),
  timestamp: z.string().datetime({ offset: true }).optional(),
  actor: z.object({
    id: z.string(),
    name: z.string().optional(),
    email: z.string().email().optional(),
  }),
  context: z.object({
    tenantId: z.string(),
    learningUnitId: z.string().uuid().optional(),
    claimId: z.string().optional(),
    sessionId: z.string().uuid(),
    platform: z.enum(["web", "mobile", "vr"]),
  }),
});

// Simulation events
export const SimStartEventSchema = BaseLearningEventSchema.extend({
  eventType: z.literal("sim.start"),
  object: z.object({
    simulationId: z.string().uuid(),
    simulationTitle: z.string(),
    domain: z.string(),
  }),
});

export const SimControlChangeEventSchema = BaseLearningEventSchema.extend({
  eventType: z.literal("sim.control.change"),
  object: z.object({
    simulationId: z.string().uuid(),
    parameterId: z.string(),
    action: z.string(),
    previousValue: z.unknown(),
    newValue: z.unknown(),
  }),
});

export const SimCaptureEventSchema = BaseLearningEventSchema.extend({
  eventType: z.literal("sim.capture"),
  object: z.object({
    simulationId: z.string().uuid(),
    claimId: z.string(),
    evidenceId: z.string(),
  }),
  result: z.object({
    validEvidence: z.boolean(),
    processFeatures: z.record(z.string(), z.unknown()).optional(),
  }),
});

// Assessment events
export const AssessAnswerEventSchema = BaseLearningEventSchema.extend({
  eventType: z.literal("assess.answer"),
  object: z.object({
    assessmentId: z.string().uuid(),
    itemId: z.string().uuid(),
  }),
  result: z.object({
    correct: z.boolean(),
    confidence: z.enum(["low", "medium", "high"]).optional(),
    misconceptions: z.array(z.string()).optional(),
  }),
});

export const AssistHintEventSchema = BaseLearningEventSchema.extend({
  eventType: z.literal("assist.hint"),
  object: z.object({
    claimId: z.string(),
    hintId: z.string().uuid(),
  }),
  result: z.object({
    accepted: z.boolean(),
  }),
});

// Content events
export const ContentVideoStartEventSchema = BaseLearningEventSchema.extend({
  eventType: z.literal("content.video.start"),
  object: z.object({
    assetId: z.string().uuid(),
    title: z.string(),
    durationSeconds: z.number().int().positive(),
  }),
});

export const ContentArticleCompleteEventSchema = BaseLearningEventSchema.extend({
  eventType: z.literal("content.article.complete"),
  object: z.object({
    assetId: z.string().uuid(),
    title: z.string(),
    timeSpentSeconds: z.number().int().nonnegative(),
  }),
});

// Union of all event types
export const VersionedLearningEventSchema = z.discriminatedUnion("eventType", [
  SimStartEventSchema,
  SimControlChangeEventSchema,
  SimCaptureEventSchema,
  AssessAnswerEventSchema,
  AssistHintEventSchema,
  ContentVideoStartEventSchema,
  ContentArticleCompleteEventSchema,
]);

export type VersionedLearningEvent = z.infer<typeof VersionedLearningEventSchema>;

// Backward-compatible legacy schema (for migration)
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

// =========================================================================
// Validation helpers
// =========================================================================

export function parseListModulesQuery(input: unknown): ListModulesQuery {
  return ListModulesQuerySchema.parse(input);
}

export function parseEnrollRequest(input: unknown): EnrollRequest {
  return EnrollRequestSchema.parse(input);
}

export function parseSubmitAttemptRequest(input: unknown): SubmitAttemptRequest {
  return SubmitAttemptRequestSchema.parse(input);
}

export function parseLearningEvent(input: unknown): VersionedLearningEvent {
  return VersionedLearningEventSchema.parse(input);
}

export function parseLegacyLearningEvent(input: unknown): LearningEventInput {
  return LearningEventInputSchema.parse(input);
}
