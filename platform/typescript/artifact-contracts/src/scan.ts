/**
 * @fileoverview Scan job and repository scanner contracts.
 *
 * Defines AcquisitionJob, ScanJob, ScanResult, ValidationResult, and DiffRecord
 * — the canonical contracts for repo-scale scanning, validation, and diff
 * operations in the artifact compiler/decompiler pipeline.
 *
 * @doc.type module
 * @doc.purpose Repo scanning and acquisition job contracts
 * @doc.layer platform
 * @doc.pattern Contracts
 */

import { z } from "zod";
import { SourceAcquisitionDescriptorSchema, SourceFileSchema, SourceRefSchema } from "./source.js";
import { FidelityReportSchema, ResidualIslandReportSchema } from "./fidelity.js";
import { OwnershipRegionSchema } from "./provenance.js";

// ============================================================================
// ACQUISITION JOB
// ============================================================================

/**
 * Status of a source acquisition job.
 */
export type AcquisitionJobStatus =
  | "pending"     // Job created, not yet started
  | "running"     // Acquisition in progress
  | "complete"    // Acquisition finished successfully
  | "failed"      // Acquisition failed
  | "cancelled";  // Acquisition cancelled by user

export const AcquisitionJobStatusSchema = z.enum([
  "pending",
  "running",
  "complete",
  "failed",
  "cancelled",
]);

/**
 * An acquisition job tracks the download/clone/read of a source repository or
 * archive for pipeline ingestion. Credentials are never stored here — they are
 * resolved from a secrets store at runtime.
 */
export const AcquisitionJobSchema = z.object({
  /** Stable unique ID for this job (UUID). */
  jobId: z.string().min(1),
  /** Current status of the acquisition. */
  status: AcquisitionJobStatusSchema,
  /** The source to acquire. */
  descriptor: SourceAcquisitionDescriptorSchema,
  /** ISO-8601 timestamp when the job was created. */
  createdAt: z.string().datetime(),
  /** ISO-8601 timestamp when the job started running. */
  startedAt: z.string().datetime().optional(),
  /** ISO-8601 timestamp when the job completed (success or failure). */
  completedAt: z.string().datetime().optional(),
  /** Total bytes downloaded/read (available after completion). */
  totalBytes: z.number().int().nonnegative().optional(),
  /** Number of files acquired. */
  fileCount: z.number().int().nonnegative().optional(),
  /** Local workspace path where files were written. */
  localWorkspacePath: z.string().optional(),
  /** Human-readable error message (if status === "failed"). */
  errorMessage: z.string().optional(),
  /** Correlation ID propagated from the originating request. */
  correlationId: z.string().optional(),
});

export type AcquisitionJob = z.infer<typeof AcquisitionJobSchema>;

// ============================================================================
// SCAN JOB
// ============================================================================

/**
 * Status of a repository scan job.
 */
export type ScanJobStatus =
  | "pending"
  | "running"
  | "complete"
  | "partial"   // Completed with some files failing
  | "failed"
  | "cancelled";

export const ScanJobStatusSchema = z.enum([
  "pending",
  "running",
  "complete",
  "partial",
  "failed",
  "cancelled",
]);

/**
 * A scan job tracks the analysis of an acquired source repository to produce
 * a LogicalArtifactModel. It references the acquisition job that brought the
 * source into the workspace.
 */
export const ScanJobSchema = z.object({
  /** Stable unique ID for this scan job (UUID). */
  jobId: z.string().min(1),
  /** Current status. */
  status: ScanJobStatusSchema,
  /** ID of the acquisition job whose workspace this scan reads. */
  acquisitionJobId: z.string().min(1),
  /** ID of the LogicalArtifactModel produced by this scan (set on completion). */
  modelId: z.string().optional(),
  /** Total number of files discovered in the workspace. */
  totalFileCount: z.number().int().nonnegative().optional(),
  /** Number of files successfully parsed. */
  parsedFileCount: z.number().int().nonnegative().optional(),
  /** Number of files that failed to parse. */
  failedFileCount: z.number().int().nonnegative().optional(),
  /** ISO-8601 timestamp when the job was created. */
  createdAt: z.string().datetime(),
  /** ISO-8601 timestamp when the scan started. */
  startedAt: z.string().datetime().optional(),
  /** ISO-8601 timestamp when the scan completed. */
  completedAt: z.string().datetime().optional(),
  /** Duration in milliseconds (set on completion). */
  durationMs: z.number().nonnegative().optional(),
  /** Human-readable error (if status === "failed"). */
  errorMessage: z.string().optional(),
  /** Correlation ID. */
  correlationId: z.string().optional(),
});

export type ScanJob = z.infer<typeof ScanJobSchema>;

// ============================================================================
// SCAN RESULT
// ============================================================================

/**
 * A file-level scan result: classification, size, content hash.
 * Used to build the inventory of the scanned repository.
 */
export const FileScanResultSchema = z.object({
  /** The source file metadata produced by scanning. */
  sourceFile: SourceFileSchema,
  /** Whether parsing succeeded for this file. */
  parsed: z.boolean(),
  /** Human-readable error message if parsing failed. */
  parseError: z.string().optional(),
  /** Duration to parse this file, in milliseconds. */
  parseDurationMs: z.number().nonnegative().optional(),
});

export type FileScanResult = z.infer<typeof FileScanResultSchema>;

/**
 * The aggregate result of a completed scan job.
 */
export const ScanResultSchema = z.object({
  /** The scan job this result belongs to. */
  scanJobId: z.string().min(1),
  /** ID of the model produced. */
  modelId: z.string().min(1),
  /** Per-file scan results. */
  files: z.array(FileScanResultSchema),
  /** Overall fidelity report for the scan run. */
  fidelity: FidelityReportSchema,
  /** Residual islands detected during scanning. */
  residuals: ResidualIslandReportSchema,
  /** ISO-8601 completion timestamp. */
  completedAt: z.string().datetime(),
  /** Total scan duration in milliseconds. */
  durationMs: z.number().nonnegative(),
});

export type ScanResult = z.infer<typeof ScanResultSchema>;

// ============================================================================
// VALIDATION PIPELINE RESULT
// ============================================================================

/**
 * Severity of a validation finding.
 */
export type ValidationFindingSeverity = "error" | "warning" | "info";

export const ValidationFindingSeveritySchema = z.enum(["error", "warning", "info"]);

/**
 * A single validation finding (type error, lint error, accessibility issue, etc.).
 */
export const ValidationFindingSchema = z.object({
  /** Stable machine-readable code (e.g. "TS2304", "a11y/missing-label"). */
  code: z.string().min(1),
  /** Human-readable description. */
  message: z.string().min(1),
  /** Severity level. */
  severity: ValidationFindingSeveritySchema,
  /** Source location if known. */
  sourceRef: SourceRefSchema.optional(),
  /**
   * Category of the finding.
   * Allows Studio to group findings by validation type.
   */
  category: z.enum([
    "typescript",    // TypeScript compiler error
    "eslint",        // ESLint rule violation
    "accessibility", // Accessibility audit finding
    "security",      // OWASP/security gate finding
    "bundle-size",   // Bundle size limit exceeded
    "contract",      // API/event contract violation
    "fidelity",      // Fidelity gate failure
    "other",
  ]),
  /** Suggested fix text or action. */
  suggestion: z.string().optional(),
});

export type ValidationFinding = z.infer<typeof ValidationFindingSchema>;

/**
 * Aggregate result of running the full validation pipeline over generated output.
 */
export const ValidationPipelineResultSchema = z.object({
  /** ID of the model or compile run this validates. */
  targetId: z.string().min(1),
  /** Whether all gates passed (no errors). */
  passed: z.boolean(),
  /** All findings (errors, warnings, infos). */
  findings: z.array(ValidationFindingSchema),
  /** Count of findings by severity. */
  errorCount: z.number().int().nonnegative(),
  warningCount: z.number().int().nonnegative(),
  infoCount: z.number().int().nonnegative(),
  /** Fidelity gate result (if run). */
  fidelity: FidelityReportSchema.optional(),
  /** ISO-8601 timestamp. */
  validatedAt: z.string().datetime(),
  /** Duration in milliseconds. */
  durationMs: z.number().nonnegative().optional(),
});

export type ValidationPipelineResult = z.infer<typeof ValidationPipelineResultSchema>;

// ============================================================================
// DIFF RECORD
// ============================================================================

/**
 * The type of a diff hunk.
 */
export type DiffHunkKind = "added" | "removed" | "changed" | "unchanged";

export const DiffHunkKindSchema = z.enum(["added", "removed", "changed", "unchanged"]);

/**
 * A single hunk within a diff, referencing a line/region in the source.
 */
export const DiffHunkSchema = z.object({
  kind: DiffHunkKindSchema,
  /** Start line (1-based) in the original file. Absent for "added". */
  originalStart: z.number().int().positive().optional(),
  /** Start line (1-based) in the generated file. Absent for "removed". */
  generatedStart: z.number().int().positive().optional(),
  /** Number of lines in this hunk. */
  lineCount: z.number().int().positive(),
  /** Snippet of the original content (first 500 chars). */
  originalSnippet: z.string().optional(),
  /** Snippet of the generated content (first 500 chars). */
  generatedSnippet: z.string().optional(),
});

export type DiffHunk = z.infer<typeof DiffHunkSchema>;

/**
 * A diff record between an original source file and its generated counterpart.
 */
export const DiffRecordSchema = z.object({
  /** Stable ID for this diff record. */
  diffId: z.string().min(1),
  /** Relative path of the original file. */
  originalPath: z.string().min(1),
  /** Relative path of the generated file. */
  generatedPath: z.string().min(1),
  /** Source ref for the original file. */
  originalRef: SourceRefSchema.optional(),
  /** Whether the files are semantically equivalent. */
  semanticallyEquivalent: z.boolean(),
  /** Summary diff hunks. */
  hunks: z.array(DiffHunkSchema),
  /** Count of added lines. */
  addedLines: z.number().int().nonnegative(),
  /** Count of removed lines. */
  removedLines: z.number().int().nonnegative(),
  /** Count of unchanged lines. */
  unchangedLines: z.number().int().nonnegative(),
  /** Ownership regions in the generated file. */
  ownershipRegions: z.array(OwnershipRegionSchema).optional(),
  /** ISO-8601 timestamp when this diff was computed. */
  diffedAt: z.string().datetime(),
});

export type DiffRecord = z.infer<typeof DiffRecordSchema>;

/**
 * A set of diffs for a full round-trip: source → model → generated.
 */
export const RoundTripDiffReportSchema = z.object({
  /** Stable ID for this round-trip report. */
  reportId: z.string().min(1),
  /** ID of the model used in the round-trip. */
  modelId: z.string().min(1),
  /** Per-file diff records. */
  diffs: z.array(DiffRecordSchema),
  /** Overall fidelity after round-trip. */
  fidelity: FidelityReportSchema,
  /** Residuals that were not regenerated. */
  residuals: ResidualIslandReportSchema,
  /** Whether the round-trip can be considered lossless. */
  isLossless: z.boolean(),
  /** ISO-8601 timestamp. */
  generatedAt: z.string().datetime(),
});

export type RoundTripDiffReport = z.infer<typeof RoundTripDiffReportSchema>;
