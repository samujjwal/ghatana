/**
 * @fileoverview Artifact inventory schemas - repository-wide file classification.
 *
 * Scans the repository and classifies every file into artifact families,
 * capturing checksums, language, framework, and extractor eligibility.
 */

import { z } from "zod";

// ============================================================================
// Artifact Kind
// ============================================================================

export const ArtifactKindSchema = z.enum([
  "component-implementation",
  "page-route",
  "story-example",
  "token-theme-style",
  "api-schema",
  "db-schema-migration",
  "domain-service-code",
  "configuration-build",
  "state-management",
  "cache-configuration",
  "messaging-configuration",
  "workflow-ci-cd",
  "script-utility",
  "unknown-manual",
]);

export type ArtifactKind = z.infer<typeof ArtifactKindSchema>;

// ============================================================================
// Language
// ============================================================================

export const ArtifactLanguageSchema = z.enum([
  "typescript",
  "tsx",
  "javascript",
  "jsx",
  "java",
  "sql",
  "prisma",
  "css",
  "scss",
  "html",
  "yaml",
  "json",
  "xml",
  "markdown",
  "shell",
  "python",
  "rust",
  "go",
  "unknown",
]);

export type ArtifactLanguage = z.infer<typeof ArtifactLanguageSchema>;

// ============================================================================
// Framework
// ============================================================================

export const ArtifactFrameworkSchema = z.enum([
  "react",
  "nextjs",
  "vue",
  "angular",
  "svelte",
  "spring-boot",
  "activej",
  "express",
  "nest",
  "prisma",
  "storybook",
  "tailwind",
  "none",
  "unknown",
]);

export type ArtifactFramework = z.infer<typeof ArtifactFrameworkSchema>;

// ============================================================================
// Import/Export Summary
// ============================================================================

export const ImportExportSummarySchema = z.object({
  imports: z
    .array(
      z.object({
        source: z.string(),
        specifiers: z.array(z.string()),
        isRelative: z.boolean(),
      }),
    )
    .default([]),
  exports: z
    .array(
      z.object({
        name: z.string(),
        kind: z.enum(["default", "named", "namespace", "all"]),
      }),
    )
    .default([]),
});

export type ImportExportSummary = z.infer<typeof ImportExportSummarySchema>;

// ============================================================================
// Extractor Eligibility
// ============================================================================

export const ExtractorEligibilitySchema = z.object({
  extractorId: z.string(),
  eligible: z.boolean(),
  reason: z.string().optional(),
});

export type ExtractorEligibility = z.infer<typeof ExtractorEligibilitySchema>;

// ============================================================================
// Artifact Record
// ============================================================================

export const ArtifactRecordSchema = z.object({
  id: z.string().uuid(),
  relativePath: z.string().min(1),
  absolutePath: z.string().min(1),
  kind: ArtifactKindSchema,
  language: ArtifactLanguageSchema,
  framework: ArtifactFrameworkSchema.default("unknown"),
  extractorEligibility: z.array(ExtractorEligibilitySchema).default([]),
  importExportSummary: ImportExportSummarySchema.default({
    imports: [],
    exports: [],
  }),
  checksum: z.string().min(1),
  sizeBytes: z.number().int().nonnegative(),
  lastModifiedAt: z.string().datetime(),
  incrementalParseToken: z.string().optional(),
});

export type ArtifactRecord = z.infer<typeof ArtifactRecordSchema>;

// ============================================================================
// Inventory Result
// ============================================================================

export const ArtifactInventorySchema = z.object({
  repositoryRoot: z.string().min(1),
  scannedAt: z.string().datetime(),
  artifacts: z.array(ArtifactRecordSchema),
  summary: z.object({
    totalFiles: z.number().int().nonnegative(),
    byKind: z.record(z.string(), z.number().int().nonnegative()),
    byLanguage: z.record(z.string(), z.number().int().nonnegative()),
    byFramework: z.record(z.string(), z.number().int().nonnegative()),
    eligibleForExtraction: z.number().int().nonnegative(),
  }),
});

export type ArtifactInventory = z.infer<typeof ArtifactInventorySchema>;
