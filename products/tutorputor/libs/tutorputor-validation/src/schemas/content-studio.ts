/**
 * @tutorputor/validation — Content Studio schemas
 *
 * Zod schemas for content-studio request/response validation.
 * Used by `/api/content-studio` routes.
 *
 * @doc.type module
 * @doc.purpose Content Studio validation schemas
 * @doc.layer product
 * @doc.pattern ValueObject
 */

import { z } from "zod";
import { DifficultySchema, DomainSchema, NonEmptyStringSchema, HttpUrlSchema } from "./common.js";

// ---------------------------------------------------------------------------
// Learning experience
// ---------------------------------------------------------------------------

export const CreateExperienceRequestSchema = z
  .object({
    title: NonEmptyStringSchema.max(200),
    description: z.string().trim().max(2000).optional(),
    domain: DomainSchema,
    difficulty: DifficultySchema,
    tags: z.array(z.string().trim().min(1)).max(20).default([]),
    objectives: z.array(NonEmptyStringSchema).max(10).optional(),
  })
  .strict();
export type CreateExperienceRequest = z.infer<typeof CreateExperienceRequestSchema>;

export const UpdateExperienceRequestSchema = CreateExperienceRequestSchema.partial().strict();
export type UpdateExperienceRequest = z.infer<typeof UpdateExperienceRequestSchema>;

// ---------------------------------------------------------------------------
// Claims
// ---------------------------------------------------------------------------

export const ClaimSchema = z
  .object({
    statement: NonEmptyStringSchema.max(500),
    evidence: z.array(NonEmptyStringSchema).max(10).default([]),
    sourceUrls: z.array(HttpUrlSchema).max(5).default([]),
  })
  .strict();
export type ClaimInput = z.infer<typeof ClaimSchema>;

// ---------------------------------------------------------------------------
// Publish gate
// ---------------------------------------------------------------------------

export const PublishGateResultSchema = z.object({
  canPublish: z.boolean(),
  trustScore: z.number().min(0).max(1),
  blockers: z.array(z.string()),
  warnings: z.array(z.string()),
});
export type PublishGateResult = z.infer<typeof PublishGateResultSchema>;

// ---------------------------------------------------------------------------
// AI generation request
// ---------------------------------------------------------------------------

export const GenerateArtifactRequestSchema = z
  .object({
    experienceId: z.string().uuid(),
    artifactType: z.enum(["claim", "example", "simulation", "animation", "assessment"]),
    prompt: z.string().trim().min(10).max(2000).optional(),
    domain: DomainSchema,
  })
  .strict();
export type GenerateArtifactRequest = z.infer<typeof GenerateArtifactRequestSchema>;

// ---------------------------------------------------------------------------
// Validation helpers
// ---------------------------------------------------------------------------

export function parseCreateExperienceRequest(input: unknown): CreateExperienceRequest {
  return CreateExperienceRequestSchema.parse(input);
}

export function parseUpdateExperienceRequest(input: unknown): UpdateExperienceRequest {
  return UpdateExperienceRequestSchema.parse(input);
}

export function parseClaimInput(input: unknown): ClaimInput {
  return ClaimSchema.parse(input);
}

export function parsePublishGateResult(input: unknown): PublishGateResult {
  return PublishGateResultSchema.parse(input);
}
