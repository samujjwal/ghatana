/**
 * @fileoverview Provenance, confidence, and extractor metadata schemas.
 *
 * Every extracted element carries provenance and confidence to enable
 * trust-graded round-trip engineering and prioritized human review.
 */

import { z } from "zod";

// ============================================================================
// Confidence
// ============================================================================

/** Confidence score bounded to [0, 1]. */
export const ConfidenceSchema = z.number().min(0).max(1);

export type Confidence = z.infer<typeof ConfidenceSchema>;

// ============================================================================
// Provenance
// ============================================================================

/** How a model element was derived from source. */
export const ProvenanceKindSchema = z.enum([
  "exact", // Direct, unambiguous extraction
  "inferred", // Logically derived but not verbatim
  "synthesized", // Combined from multiple sources
  "manual", // Human-entered or confirmed
  "assumed", // Fallback default when extraction failed
]);

export type ProvenanceKind = z.infer<typeof ProvenanceKindSchema>;

/** Record of where a model element originated. */
export const ProvenanceRecordSchema = z.object({
  sourcePath: z.string().min(1),
  sourceLanguage: z.string().optional(),
  extractorId: z.string().min(1),
  extractorVersion: z.string().min(1),
  kind: ProvenanceKindSchema,
  location: z
    .object({
      startLine: z.number().int().nonnegative(),
      startColumn: z.number().int().nonnegative(),
      endLine: z.number().int().nonnegative(),
      endColumn: z.number().int().nonnegative(),
    })
    .optional(),
  checksum: z.string().optional(),
  extractedAt: z.string().datetime(),
});

export type ProvenanceRecord = z.infer<typeof ProvenanceRecordSchema>;

// ============================================================================
// Extractor Identity
// ============================================================================

export const ExtractorIdSchema = z.string().min(1).max(128);

export type ExtractorId = z.infer<typeof ExtractorIdSchema>;

export const ExtractorVersionSchema = z.string().min(1).max(64);

export type ExtractorVersion = z.infer<typeof ExtractorVersionSchema>;

// ============================================================================
// Confidence-Graded Value
// ============================================================================

/** A value tagged with confidence and provenance. */
export const GradedValueSchema = <T extends z.ZodTypeAny>(valueSchema: T) =>
  z.object({
    value: valueSchema,
    confidence: ConfidenceSchema,
    provenance: ProvenanceRecordSchema,
  });

// ============================================================================
// Review Requirement
// ============================================================================

export const ReviewRequirementSchema = z.object({
  required: z.boolean(),
  reason: z.string().optional(),
  confidenceThreshold: ConfidenceSchema.optional(),
});

export type ReviewRequirement = z.infer<typeof ReviewRequirementSchema>;

// ============================================================================
// Privacy / Security Flags
// ============================================================================

export const SecurityFlagSchema = z.enum([
  "contains-secrets",
  "exposes-pii",
  "has-auth-logic",
  "cross-origin",
  "unsafe-eval",
  "raw-sql",
]);

export type SecurityFlag = z.infer<typeof SecurityFlagSchema>;

export const PrivacyFlagSchema = z.enum([
  "collects-pii",
  "tracks-user",
  "stores-locally",
  "shares-third-party",
  "requires-consent",
]);

export type PrivacyFlag = z.infer<typeof PrivacyFlagSchema>;

// ============================================================================
// Combined Artifact Metadata
// ============================================================================

/** Metadata every extracted artifact node carries. */
export const ArtifactMetadataSchema = z.object({
  confidence: ConfidenceSchema,
  provenance: ProvenanceRecordSchema,
  reviewRequirement: ReviewRequirementSchema.optional(),
  securityFlags: z.array(SecurityFlagSchema).default([]),
  privacyFlags: z.array(PrivacyFlagSchema).default([]),
});

export type ArtifactMetadata = z.infer<typeof ArtifactMetadataSchema>;
