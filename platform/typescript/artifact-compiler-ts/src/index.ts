/**
 * @fileoverview @ghatana/artifact-compiler-ts — Barrel export.
 *
 * TypeScript/TSX artifact compiler and decompiler for the Ghatana artifact
 * pipeline. Provides:
 *
 * - TSX → LogicalArtifactModel decompiler (compiler API-based)
 * - LogicalArtifactModel → React/TSX compiler
 * - Projection to BuilderDocument, CanvasDocument, and DS Generator config
 * - Fidelity scoring utilities
 * - Residual island detection
 *
 * @doc.type module
 * @doc.purpose @ghatana/artifact-compiler-ts public API
 * @doc.layer platform
 * @doc.pattern Contracts
 */

// Decompiler
export {
  decompileTsx,
  type DecompileTsxInput,
  type DecompileTsxResult,
  type DecompileSourceFile,
} from "./decompile/tsx.js";

// Compiler
export {
  compileReact,
  type CompileReactOptions,
  type CompileReactResult,
  type EmittedFile,
} from "./compile/react.js";

// Projections
export {
  projectToBuilder,
  type ProjectBuilderOptions,
  type ProjectBuilderResult,
  type ProjectedBuilderDocument,
  type ProjectedComponentInstance,
} from "./projection/builder.js";

export {
  projectToCanvas,
  type ProjectCanvasOptions,
  type ProjectCanvasResult,
  type ProjectedCanvasDocument,
  type ProjectedCanvasNode,
  type ProjectedCanvasEdge,
  type ProjectedCanvasPosition,
} from "./projection/canvas.js";

export {
  projectToDs,
  type ProjectDsResult,
  type ProjectedDsConfig,
  type ProjectedDsComponentContract,
  type DsTokenCategory,
} from "./projection/ds.js";

// Fidelity scoring
export {
  aggregateFidelityReports,
  fidelityGate,
  scoreArtifactNode,
  FIDELITY_THRESHOLDS,
  computeFidelityReport,
  createPerfectFidelityReport,
  type FidelityGate,
  type FidelityReport,
  type LossPoint,
} from "./fidelity/scorer.js";

// Residual islands
export {
  detectResidualIslands,
  type ParsedSourceFile,
} from "./residual/residual-islands.js";

// Round-trip diff
export {
  buildRoundTripDiffReport,
  createNotRunValidationPipelineResult,
  type BuildRoundTripDiffReportOptions,
  type RoundTripDiffSourceFile,
} from "./diff/roundtrip-diff.js";

// Generated artifact validation
export {
  validateGeneratedArtifacts,
  type GeneratedArtifactValidationSource,
  type ValidationStageId,
  type ValidationStageResult,
  type ValidateGeneratedArtifactsOptions,
} from "./validate/generated-artifacts.js";

// Repository scan facade
export {
  scanRepositorySources,
  type RepositoryScanOptions,
  type RepositoryScanOutput,
  type RepositoryScanSourceEntry,
} from "./scan/repository-scan.js";
