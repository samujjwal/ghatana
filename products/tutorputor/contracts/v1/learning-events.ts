/**
 * Learning Event Schemas
 *
 * Versioned JSON schemas for learning event ingestion and validation.
 * These schemas ensure type safety and contract compatibility across the platform.
 *
 * @doc.type module
 * @doc.purpose Versioned JSON schemas for learning events
 * @doc.layer contracts
 * @doc.pattern SchemaDefinition
 */

import { z } from 'zod';

// =============================================================================
// Common Event Fields
// =============================================================================

export const LearningEventBaseSchema = z.object({
  type: z.string(),
  userId: z.string(),
  timestamp: z.string().datetime(),
  moduleId: z.string().optional(),
  schemaVersion: z.string().default('1.0.0'),
});

// =============================================================================
// Assessment Answer Event
// =============================================================================

export const AssessAnswerEventSchema = LearningEventBaseSchema.extend({
  type: z.string().refine((val) => val === 'assess.answer'),
  payload: z.object({
    object: z.object({
      claimId: z.string(),
      evidenceId: z.string().optional(),
    }),
    result: z.object({
      correct: z.boolean(),
      confidence: z.string().refine((val) => ['low', 'medium', 'high'].includes(val)),
      misconceptions: z.array(z.string()).optional(),
      timeSpentSeconds: z.number().optional(),
    }),
  }),
});

export type AssessAnswerEvent = z.infer<typeof AssessAnswerEventSchema>;

// =============================================================================
// Simulation Capture Event
// =============================================================================

export const SimCaptureEventSchema = LearningEventBaseSchema.extend({
  type: z.string().refine((val) => val === 'sim.capture'),
  payload: z.object({
    object: z.object({
      runId: z.string(),
      captureId: z.string(),
      claimId: z.string(),
      evidenceId: z.string().optional(),
    }),
    result: z.object({
      processFeatures: z.object({
        processScore: z.number(),
        stepsCompleted: z.number(),
        interactions: z.number(),
      }),
      finalState: z.record(z.string(), z.unknown()).optional(),
    }),
  }),
});

export type SimCaptureEvent = z.infer<typeof SimCaptureEventSchema>;

// =============================================================================
// Content View Event
// =============================================================================

export const ContentViewEventSchema = LearningEventBaseSchema.extend({
  type: z.string().refine((val) => val === 'content.view'),
  payload: z.object({
    contentType: z.string().refine((val) => ['example', 'simulation', 'animation', 'text', 'video'].includes(val)),
    contentId: z.string(),
    durationSeconds: z.number().optional(),
    completed: z.boolean().optional(),
  }),
});

export type ContentViewEvent = z.infer<typeof ContentViewEventSchema>;

// =============================================================================
// Hint Request Event
// =============================================================================

export const HintRequestEventSchema = LearningEventBaseSchema.extend({
  type: z.string().refine((val) => val === 'hint.request'),
  payload: z.object({
    itemId: z.string(),
    hintType: z.string().refine((val) => ['text', 'visual', 'step_by_step'].includes(val)),
    hintLevel: z.number().min(1).max(5),
    context: z.record(z.string(), z.unknown()).optional(),
  }),
});

export type HintRequestEvent = z.infer<typeof HintRequestEventSchema>;

// =============================================================================
// AI Tutor Message Event
// =============================================================================

export const AiTutorMessageEventSchema = LearningEventBaseSchema.extend({
  type: z.string().refine((val) => val === 'ai_tutor_message'),
  payload: z.object({
    source: z.string(),
    sessionId: z.string().optional(),
    assetId: z.string().optional(),
    trigger: z.string().optional(),
    reason: z.string().optional(),
    recommendation: z.record(z.string(), z.unknown()).optional(),
    eventType: z.string().optional(),
    variant: z.object({
      variantId: z.string(),
      family: z.string(),
      key: z.string(),
      strategy: z.string(),
    }).optional(),
    observedSignals: z.record(z.string(), z.unknown()).optional(),
    adapted: z.boolean(),
  }),
});

export type AiTutorMessageEvent = z.infer<typeof AiTutorMessageEventSchema>;

// =============================================================================
// Union Schema for All Event Types
// =============================================================================

export const LearningEventSchema = z.union([
  AssessAnswerEventSchema,
  SimCaptureEventSchema,
  ContentViewEventSchema,
  HintRequestEventSchema,
  AiTutorMessageEventSchema,
]);

export type LearningEvent = z.infer<typeof LearningEventSchema>;

// =============================================================================
// Validation Functions
// =============================================================================

export function validateLearningEvent(data: unknown): {
  success: boolean;
  data?: LearningEvent;
  errors?: z.ZodError;
} {
  const result = LearningEventSchema.safeParse(data);
  if (result.success) {
    return { success: true, data: result.data };
  }
  return { success: false, errors: result.error };
}

export function validateLearningEventWithVersion(
  data: unknown,
  expectedVersion: string = '1.0.0'
): {
  success: boolean;
  data?: LearningEvent;
  versionMismatch?: boolean;
  errors?: z.ZodError;
} {
  const result = LearningEventSchema.safeParse(data);
  if (!result.success) {
    return { success: false, errors: result.error };
  }

  if (result.data.schemaVersion !== expectedVersion) {
    return { success: false, versionMismatch: true };
  }

  return { success: true, data: result.data };
}


