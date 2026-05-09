/**
 * JSON Field Schemas
 *
 * Zod schemas for validating JSON fields in the database.
 * These schemas correspond to the Json fields in the Prisma schema.
 *
 * @doc.type module
 * @doc.purpose Zod schemas for JSON field validation
 * @doc.layer platform
 * @doc.pattern Schema
 */

import { z } from "zod";

// ============================================================================
// Payout Metadata Schema
// ============================================================================

export const PayoutMetadataSchema = z.object({
  transactionId: z.string().optional(),
  description: z.string().optional(),
  notes: z.array(z.string()).optional(),
  customFields: z.record(z.string(), z.any()).optional(),
});

export type PayoutMetadata = z.infer<typeof PayoutMetadataSchema>;

// ============================================================================
// Study Group Schemas
// ============================================================================

export const StudyGroupSubjectsSchema = z.array(z.string().min(1));

export const StudyGroupModulesSchema = z.array(z.string().min(1)).optional();

export type StudyGroupSubjects = z.infer<typeof StudyGroupSubjectsSchema>;
export type StudyGroupModules = z.infer<typeof StudyGroupModulesSchema>;

// ============================================================================
// Study Session Schemas
// ============================================================================

export const StudySessionLessonIdsSchema = z.array(z.string().min(1)).optional();

export const StudySessionAttachmentsSchema = z.array(
  z.object({
    id: z.string().min(1),
    name: z.string().min(1),
    url: z.string().url(),
    mimeType: z.string().optional(),
    sizeBytes: z.number().int().nonnegative().optional(),
  })
).optional();

export type StudySessionLessonIds = z.infer<typeof StudySessionLessonIdsSchema>;
export type StudySessionAttachments = z.infer<typeof StudySessionAttachmentsSchema>;

// ============================================================================
// Forum Schemas
// ============================================================================

export const ForumCategorySchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  slug: z.string().min(1),
  icon: z.string().optional(),
  description: z.string().optional(),
});

export const ForumCategoriesSchema = z.array(ForumCategorySchema).optional();

export type ForumCategory = z.infer<typeof ForumCategorySchema>;
export type ForumCategories = z.infer<typeof ForumCategoriesSchema>;

// ============================================================================
// Forum Topic Schemas
// ============================================================================

export const ForumTopicAttachmentSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  url: z.string().url(),
  mimeType: z.string().optional(),
  sizeBytes: z.number().int().nonnegative().optional(),
});

export const ForumTopicAttachmentsSchema = z.array(ForumTopicAttachmentSchema).optional();

export type ForumTopicAttachment = z.infer<typeof ForumTopicAttachmentSchema>;
export type ForumTopicAttachments = z.infer<typeof ForumTopicAttachmentsSchema>;

// ============================================================================
// Forum Post Schemas
// ============================================================================

export const ForumPostAttachmentSchema = z.object({
  id: z.string().min(1),
  name: z.string().min(1),
  url: z.string().url(),
  mimeType: z.string().optional(),
  sizeBytes: z.number().int().nonnegative().optional(),
});

export const ForumPostAttachmentsSchema = z.array(ForumPostAttachmentSchema).optional();

export const PostEditSchema = z.object({
  editedAt: z.string().datetime(),
  editedBy: z.string().min(1),
  reason: z.string().optional(),
  previousContent: z.string().optional(),
});

export const ForumPostEditHistorySchema = z.array(PostEditSchema).optional();

export type ForumPostAttachment = z.infer<typeof ForumPostAttachmentSchema>;
export type ForumPostAttachments = z.infer<typeof ForumPostAttachmentsSchema>;
export type PostEdit = z.infer<typeof PostEditSchema>;
export type ForumPostEditHistory = z.infer<typeof ForumPostEditHistorySchema>;

// ============================================================================
// Helper Functions
// ============================================================================

/**
 * Validate JSON field against schema
 */
export function validateJsonField<T>(
  value: unknown,
  schema: z.ZodSchema<T>
): { success: true; data: T } | { success: false; error: z.ZodError } {
  const result = schema.safeParse(value);
  if (result.success) {
    return { success: true, data: result.data };
  }
  return { success: false, error: result.error };
}

/**
 * Safely parse JSON field
 */
export function safeParseJsonField<T>(
  value: unknown,
  schema: z.ZodSchema<T>,
  defaultValue: T
): T {
  if (value === null || value === undefined) {
    return defaultValue;
  }

  if (typeof value === "string") {
    try {
      const parsed = JSON.parse(value);
      const result = schema.safeParse(parsed);
      if (result.success) {
        return result.data;
      }
    } catch {
      // Invalid JSON, return default
    }
    return defaultValue;
  }

  const result = schema.safeParse(value);
  if (result.success) {
    return result.data;
  }

  return defaultValue;
}
