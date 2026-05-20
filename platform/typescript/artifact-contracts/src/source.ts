/**
 * @fileoverview Source reference types for the artifact compiler/decompiler pipeline.
 *
 * Defines SourceRef, SourceFile, SourceSpan — the canonical models for
 * pointing back from any artifact node or fidelity record to its origin
 * in a version-controlled source tree.
 *
 * @doc.type module
 * @doc.purpose Source acquisition and referencing contracts
 * @doc.layer platform
 * @doc.pattern Contracts
 */

import { z } from "zod";

// ============================================================================
// SOURCE SPAN
// ============================================================================

/**
 * A precise location within a source file, identified by byte offsets and
 * optional line/column positions for human-readable error messages.
 */
export const SourceSpanSchema = z.object({
  /** Byte offset of the start of the span (inclusive). */
  startOffset: z.number().int().nonnegative(),
  /** Byte offset of the end of the span (exclusive). */
  endOffset: z.number().int().nonnegative(),
  /** Human-readable start line (1-based). */
  startLine: z.number().int().positive().optional(),
  /** Human-readable start column (1-based). */
  startColumn: z.number().int().positive().optional(),
  /** Human-readable end line (1-based). */
  endLine: z.number().int().positive().optional(),
  /** Human-readable end column (1-based). */
  endColumn: z.number().int().positive().optional(),
});

export type SourceSpan = z.infer<typeof SourceSpanSchema>;

// ============================================================================
// SOURCE FILE
// ============================================================================

/** Classification of a source file by role in the codebase. */
export type SourceFileKind =
  | "component" // React/Vue/Svelte component
  | "page" // Route-level page component
  | "hook" // Custom hook (use*)
  | "utility" // Pure utility / helper
  | "style" // CSS / Tailwind / styled file
  | "config" // Configuration (tsconfig, vite.config, etc.)
  | "schema" // Zod/JSON schema definitions
  | "test" // Test file
  | "story" // Storybook story
  | "type" // Type-only declarations
  | "asset" // Image / font / binary asset
  | "unknown"; // Unclassified

export const SourceFileKindSchema = z.enum([
  "component",
  "page",
  "hook",
  "utility",
  "style",
  "config",
  "schema",
  "test",
  "story",
  "type",
  "asset",
  "unknown",
]);

/**
 * Represents a single file in a source repository or workspace.
 */
export const SourceFileSchema = z.object({
  /** Stable path relative to the repository root. */
  relativePath: z.string().min(1),
  /** Absolute path on disk (may be absent for remote sources). */
  absolutePath: z.string().optional(),
  /** MIME type or file extension-derived content type. */
  contentType: z.string().min(1),
  /** Source file role classification. */
  kind: SourceFileKindSchema,
  /** SHA-256 digest of the raw file bytes at scan time. */
  contentHash: z.string().regex(/^[0-9a-f]{64}$/).optional(),
  /** File size in bytes. */
  sizeBytes: z.number().int().nonnegative().optional(),
  /** Language identifier (e.g. "typescript", "css", "json"). */
  language: z.string().optional(),
});

export type SourceFile = z.infer<typeof SourceFileSchema>;

// ============================================================================
// SOURCE REF
// ============================================================================

/**
 * A stable, serialisable reference to a specific region within a source file
 * inside a versioned repository or local workspace.
 *
 * Used by provenance records to point artifact nodes back to their origin.
 */
export const SourceRefSchema = z.object({
  /** Repository URL or local path identifier. */
  repositoryUri: z.string().min(1),
  /**
   * Git commit SHA, tag, or branch name.
   * Should be a pinned commit SHA for reproducible references.
   */
  commitRef: z.string().min(1),
  /** The source file this ref points to. */
  file: SourceFileSchema,
  /** Optional span within the file. Absent means the whole file. */
  span: SourceSpanSchema.optional(),
});

export type SourceRef = z.infer<typeof SourceRefSchema>;

// ============================================================================
// SOURCE ACQUISITION DESCRIPTOR
// ============================================================================

/** Type of source acquisition strategy. */
export type SourceAcquisitionKind =
  | "github" // GitHub REST or GraphQL API
  | "gitlab" // GitLab API
  | "local-folder" // Local filesystem folder
  | "archive" // ZIP / TAR archive
  | "git-clone"; // Direct git clone

export const SourceAcquisitionKindSchema = z.enum([
  "github",
  "gitlab",
  "local-folder",
  "archive",
  "git-clone",
]);

/**
 * Describes how a source repository should be acquired for scanning.
 * Credentials are intentionally excluded — they must be resolved from
 * a secrets store at runtime and never stored in these descriptors.
 */
export const SourceAcquisitionDescriptorSchema = z.object({
  kind: SourceAcquisitionKindSchema,
  /** Primary URI — GitHub HTTPS URL, local path, archive URL, etc. */
  uri: z.string().min(1),
  /** Ref to check out (branch, tag, or commit SHA). */
  ref: z.string().optional(),
  /** Optional subfolder within the repository to scope scanning. */
  subPath: z.string().optional(),
  /** Human-readable label for UI display. */
  label: z.string().optional(),
});

export type SourceAcquisitionDescriptor = z.infer<
  typeof SourceAcquisitionDescriptorSchema
>;
