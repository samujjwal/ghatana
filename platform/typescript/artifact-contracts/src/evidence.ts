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
import { ValidationPipelineResultSchema } from "./scan.js";
import { SourceRefSchema } from "./source.js";

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
