/**
 * @fileoverview Artifact inventory schemas - repository-wide file classification.
 *
 * Scans the repository and classifies every file into artifact families,
 * capturing checksums, language, framework, and extractor eligibility.
 */

import { z } from "zod";
import { SnapshotRefSchema } from '../graph/types';

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
// Skipped Artifact
// ============================================================================

export const SkippedArtifactSchema = z.object({
  /**
   * Relative path of the skipped file from repository root.
   */
  relativePath: z.string().min(1),
  /**
   * Human-readable reason for skipping (e.g., "excluded by gitignore", "oversized", "binary").
   */
  reason: z.string().min(1),
  /**
   * File size in bytes, if available.
   */
  sizeBytes: z.number().int().nonnegative().optional(),
  /**
   * The glob pattern or rule that caused the file to be skipped.
   */
  matchedPattern: z.string().optional(),
  /**
   * Source of the skip decision (e.g., "gitignore", "excludeGlobs", "maxFileSize").
   */
  source: z.enum(["gitignore", "excludeGlobs", "maxFileSize", "readError", "symlink"]),
  /**
   * ISO 8601 timestamp when the skip was detected.
   */
  detectedAt: z.string().datetime(),
});

export type SkippedArtifact = z.infer<typeof SkippedArtifactSchema>;

// ============================================================================
// Package Boundary
// ============================================================================

export const PackageBoundarySchema = z.object({
  name: z.string().min(1),
  relativePath: z.string().min(1),
  system: z.enum(["pnpm", "npm", "yarn", "gradle", "maven", "cargo", "unknown"]),
  manifestFile: z.string().min(1),
});

export type PackageBoundary = z.infer<typeof PackageBoundarySchema>;

// ============================================================================
// Artifact Record
// ============================================================================

export const ArtifactRecordSchema = z.object({
  /**
   * Deterministic URN (artifact://<provider>/…#file:<path>) when snapshotRef is available,
   * or a random UUID for ad-hoc scans without a stable source anchor.
   */
  id: z.string().min(1),
  relativePath: z.string().min(1),
  absolutePath: z.string().min(1),
  kind: ArtifactKindSchema,
  language: ArtifactLanguageSchema,
  framework: ArtifactFrameworkSchema.default("unknown"),
  /** True when the file is auto-generated (e.g. .d.ts, compiled output, lockfiles). */
  isGenerated: z.boolean().default(false),
  /** True when the file is a binary (image, font, archive, etc.) and should not be parsed. */
  isBinary: z.boolean().default(false),
  /** Package boundary this artifact belongs to, if detected. */
  packageBoundary: PackageBoundarySchema.optional(),
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
  /**
   * Snapshot reference used to produce deterministic artifact IDs.
   * Present when the scan was performed against a known provider/commit.
   */
  snapshotRef: SnapshotRefSchema.optional(),
  artifacts: z.array(ArtifactRecordSchema),
  /** Files that were skipped during scanning with reasons. */
  skippedArtifacts: z.array(SkippedArtifactSchema).default([]),
  /** Detected package boundaries (package.json, build.gradle, Cargo.toml, etc.). */
  packageBoundaries: z.array(PackageBoundarySchema).default([]),
  /** Workspace-level boundaries (pnpm-workspace.yaml, nx.json, etc.). */
  workspaceBoundaries: z.array(PackageBoundarySchema).default([]),
  summary: z.object({
    totalFiles: z.number().int().nonnegative(),
    byKind: z.record(z.string(), z.number().int().nonnegative()),
    byLanguage: z.record(z.string(), z.number().int().nonnegative()),
    byFramework: z.record(z.string(), z.number().int().nonnegative()),
    eligibleForExtraction: z.number().int().nonnegative(),
    generatedFiles: z.number().int().nonnegative(),
    binaryFiles: z.number().int().nonnegative(),
    ignoredFiles: z.number().int().nonnegative(),
  }),
});

export type ArtifactInventory = z.infer<typeof ArtifactInventorySchema>;
