/**
 * @fileoverview Compile/decompile result and evidence pack contracts.
 *
 * Defines CompileResult, DecompileResult, and EvidencePack — the canonical
 * output types of the artifact compiler/decompiler pipeline.
 *
 * @doc.type module
 * @doc.purpose Compiler/decompiler result contracts and evidence packs
 * @doc.layer platform
 * @doc.pattern Contracts
 */

import { z } from "zod";
import { FidelityReportSchema, ResidualIslandReportSchema } from "./fidelity.js";
import {
  AcquisitionJobStatusSchema,
  ValidationFindingSchema,
  ValidationPipelineResultSchema,
} from "./scan.js";
import { SourceAcquisitionDescriptorSchema, SourceRefSchema } from "./source.js";

// ============================================================================
// COMPILE RESULT
// ============================================================================

/**
 * Output of the compiler pipeline: converts a LogicalArtifactModel (or
 * BuilderDocument/DesignSystemDocument) back into source code files.
 */
export const CompileResultSchema = z.object({
  /** Whether the compile succeeded. */
  success: z.boolean(),
  /**
   * All emitted source files.
   * Key = relative file path within the target workspace.
   * Value = file content string.
   */
  emittedFiles: z.record(z.string(), z.string()),
  /** Fidelity report for this compile run. */
  fidelity: FidelityReportSchema,
  /** Residual islands that could not be regenerated. */
  residuals: ResidualIslandReportSchema,
  /** Structured compilation errors (if success === false). */
  errors: z.array(
    z.object({
      code: z.string(),
      message: z.string(),
      sourceRef: SourceRefSchema.optional(),
    }),
  ),
  /** Structured warnings. */
  warnings: z.array(
    z.object({
      code: z.string(),
      message: z.string(),
      sourceRef: SourceRefSchema.optional(),
    }),
  ),
  /** ISO-8601 timestamp of this compile run. */
  compiledAt: z.string().datetime(),
  /** Duration in milliseconds. */
  durationMs: z.number().nonnegative().optional(),
});

export type CompileResult = z.infer<typeof CompileResultSchema>;

// ============================================================================
// DECOMPILE RESULT
// ============================================================================

/**
 * Output of the decompiler pipeline: converts source code files into a
 * LogicalArtifactModel ready for editing.
 */
export const DecompileResultSchema = z.object({
  /** Whether the decompile succeeded (partial success is possible). */
  success: z.boolean(),
  /** The model ID of the produced LogicalArtifactModel. */
  modelId: z.string().min(1),
  /** Total number of nodes extracted. */
  nodeCount: z.number().int().nonnegative(),
  /** Total number of edges extracted. */
  edgeCount: z.number().int().nonnegative(),
  /** Fidelity report for this decompile run. */
  fidelity: FidelityReportSchema,
  /** Residual islands that could not be modelled. */
  residuals: ResidualIslandReportSchema,
  /** Structured errors (if success === false or partial). */
  errors: z.array(
    z.object({
      code: z.string(),
      message: z.string(),
      sourceRef: SourceRefSchema.optional(),
    }),
  ),
  /** ISO-8601 timestamp of this decompile run. */
  decompiledAt: z.string().datetime(),
  /** @deprecated Use decompiledAt. Kept optional for backwards-compatible parsing. */
  decompiledat: z.string().datetime().optional(),
  /** Duration in milliseconds. */
  durationMs: z.number().nonnegative().optional(),
});

export type DecompileResult = z.infer<typeof DecompileResultSchema>;

// ============================================================================
// VALIDATION AND ACQUISITION EVIDENCE
// ============================================================================

export const EvidenceGateStatusSchema = z.enum([
  "passed",
  "failed",
  "warning",
  "not-run",
  "skipped",
]);

export type EvidenceGateStatus = z.infer<typeof EvidenceGateStatusSchema>;

export const EvidenceArtifactRefSchema = z.object({
  /** Human-readable label for this artifact or report. */
  label: z.string().min(1),
  /** Durable URI, if stored externally. */
  uri: z.string().min(1).optional(),
  /** Workspace-relative path, if materialized locally. */
  relativePath: z.string().min(1).optional(),
  /** Media/content type for display and retention policy. */
  contentType: z.string().min(1).optional(),
});

export type EvidenceArtifactRef = z.infer<typeof EvidenceArtifactRefSchema>;

export const ValidationEvidenceStageIdSchema = z.enum([
  "typecheck",
  "lint",
  "build",
  "test",
  "preview-smoke",
  "source-acquisition",
]);

export type ValidationEvidenceStageId = z.infer<typeof ValidationEvidenceStageIdSchema>;

export const ValidationEvidenceStageResultSchema = z.object({
  /** Stable stage identifier for UI grouping and release gates. */
  stageId: ValidationEvidenceStageIdSchema,
  /** Outcome of this validation stage. */
  status: EvidenceGateStatusSchema,
  /** Human-readable stage summary. */
  summary: z.string().min(1),
  /** Findings emitted by this stage. */
  findings: z.array(ValidationFindingSchema).default([]),
  /** Optional runner/tool version for auditability. */
  runner: z.string().min(1).optional(),
  /** Optional started timestamp. */
  startedAt: z.string().datetime().optional(),
  /** Optional completed timestamp. */
  completedAt: z.string().datetime().optional(),
  /** Duration in milliseconds. */
  durationMs: z.number().nonnegative().optional(),
  /** Durable report/log reference for this stage. */
  report: EvidenceArtifactRefSchema.optional(),
});

export type ValidationEvidenceStageResult = z.infer<typeof ValidationEvidenceStageResultSchema>;

/**
 * Canonical evidence for generated workspace validation.
 *
 * The aggregate ValidationPipelineResult remains available for compatibility,
 * while this record carries stage-level gate status and durable report refs for
 * Studio, release gates, and persisted evidence packs.
 */
export const GeneratedArtifactValidationEvidenceSchema = z.object({
  /** ID of the model/compile/round-trip target that was validated. */
  targetId: z.string().min(1),
  /** Overall validation outcome. */
  passed: z.boolean(),
  /** Aggregate validation pipeline output. */
  pipeline: ValidationPipelineResultSchema,
  /** TypeScript diagnostics surfaced separately for fast UI drill-down. */
  typeScriptDiagnostics: z.array(ValidationFindingSchema).default([]),
  /** Stage-level validation status for typecheck/lint/build/test/preview. */
  stages: z.array(ValidationEvidenceStageResultSchema).default([]),
  /** Generated workspace files and durable validation reports. */
  artifacts: z.array(EvidenceArtifactRefSchema).default([]),
  /** ISO-8601 timestamp when this validation evidence was finalized. */
  validatedAt: z.string().datetime(),
  /** Duration in milliseconds. */
  durationMs: z.number().nonnegative().optional(),
}).superRefine((value, ctx) => {
  if (value.passed !== value.pipeline.passed) {
    ctx.addIssue({
      code: "custom",
      path: ["passed"],
      message: "generated validation evidence must agree with pipeline.passed",
    });
  }

  const failedStage = value.stages.find((stage) => stage.status === "failed");
  if (value.passed && failedStage !== undefined) {
    ctx.addIssue({
      code: "custom",
      path: ["stages"],
      message: `passed validation evidence cannot include failed stage ${failedStage.stageId}`,
    });
  }
});

export type GeneratedArtifactValidationEvidence = z.infer<typeof GeneratedArtifactValidationEvidenceSchema>;

export const PreviewValidationEvidenceSchema = z.object({
  /** ID of the preview target. */
  targetId: z.string().min(1),
  /** Preview execution mode. */
  mode: z.enum(["safe-static", "isolated-runtime"]),
  /** Preview smoke outcome. */
  status: EvidenceGateStatusSchema,
  /** Human-readable summary. */
  summary: z.string().min(1),
  /** Preview findings or captured errors. */
  findings: z.array(ValidationFindingSchema).default([]),
  /** Optional rendered preview/report reference. */
  preview: EvidenceArtifactRefSchema.optional(),
  /** Sandbox policy snapshot. */
  sandboxPolicy: z.object({
    allowScripts: z.boolean(),
    allowSameOrigin: z.boolean(),
    allowPopups: z.boolean(),
    allowForms: z.boolean(),
    contentSecurityPolicy: z.string().min(1).optional(),
  }),
  /** ISO-8601 timestamp when preview evidence was produced. */
  renderedAt: z.string().datetime(),
  /** Render duration in milliseconds. */
  durationMs: z.number().nonnegative().optional(),
});

export type PreviewValidationEvidence = z.infer<typeof PreviewValidationEvidenceSchema>;

export const SourceAcquisitionEvidenceSchema = z.object({
  /** Stable acquisition evidence ID or job ID. */
  acquisitionId: z.string().min(1),
  /** Original source descriptor with credentials excluded/redacted. */
  descriptor: SourceAcquisitionDescriptorSchema.optional(),
  /** Durable acquisition job status. */
  status: AcquisitionJobStatusSchema,
  /** Tenant/workspace/project ownership scope. */
  scope: z.object({
    tenantId: z.string().min(1),
    workspaceId: z.string().min(1),
    projectId: z.string().min(1),
  }).optional(),
  /** Number of materialized files. */
  fileCount: z.number().int().nonnegative().optional(),
  /** Total accepted source bytes. */
  totalBytes: z.number().int().nonnegative().optional(),
  /** Inventory/report pointer for materialized source files. */
  inventory: EvidenceArtifactRefSchema.optional(),
  /** Acquisition findings, including safe error summaries. */
  findings: z.array(ValidationFindingSchema).default([]),
  /** ISO-8601 timestamp when acquisition evidence was recorded. */
  recordedAt: z.string().datetime(),
  /** Correlation ID propagated from API/worker execution. */
  correlationId: z.string().min(1).optional(),
});

export type SourceAcquisitionEvidence = z.infer<typeof SourceAcquisitionEvidenceSchema>;

// ============================================================================
// EVIDENCE PACK
// ============================================================================

/**
 * A durable, inspectable artifact capturing all evidence from a pipeline run.
 * Can be stored in the artifact registry and reviewed in Studio.
 */
export const EvidencePackSchema = z.object({
  /** Stable ID for this evidence pack. */
  evidenceId: z.string().min(1),
  /** ISO-8601 creation timestamp. */
  createdAt: z.string().datetime(),
  /** ID of the LogicalArtifactModel this evidence covers. */
  modelId: z.string().min(1),
  /** Label for human-readable display. */
  label: z.string().min(1),
  /**
   * Pipeline stage that produced this evidence.
   */
  stage: z.enum(["decompile", "compile", "round-trip", "validation"]),
  /** Fidelity report for the run. */
  fidelity: FidelityReportSchema,
  /** Residual island report for the run. */
  residuals: ResidualIslandReportSchema,
  /**
   * Decompile result, if stage includes decompile.
   */
  decompileResult: DecompileResultSchema.optional(),
  /**
   * Compile result, if stage includes compile.
   */
  compileResult: CompileResultSchema.optional(),
  /**
   * Generated artifact validation result, if build/typecheck/lint/test gates were run.
   */
  validationResult: ValidationPipelineResultSchema.optional(),
  /**
   * Stage-level generated workspace validation evidence, including durable
   * typecheck/lint/build/test/preview report references.
   */
  generatedValidationEvidence: GeneratedArtifactValidationEvidenceSchema.optional(),
  /**
   * Preview smoke/runtime evidence for the generated artifact.
   */
  previewEvidence: PreviewValidationEvidenceSchema.optional(),
  /**
   * Source acquisition evidence for repository/archive-backed workflows.
   */
  sourceAcquisitionEvidence: SourceAcquisitionEvidenceSchema.optional(),
  /**
   * Human-readable summary of the overall pipeline outcome.
   */
  summary: z.string().optional(),
  /**
   * Review status. Initially "pending", moves to "approved" or "rejected".
   */
  reviewStatus: z.enum(["pending", "approved", "rejected", "needs-revision"]).default("pending"),
  /** User ID of the reviewer (if reviewed). */
  reviewedBy: z.string().optional(),
  /** ISO-8601 timestamp of review. */
  reviewedAt: z.string().datetime().optional(),
  /** Reviewer notes. */
  reviewNotes: z.string().optional(),
});

export type EvidencePack = z.infer<typeof EvidencePackSchema>;
