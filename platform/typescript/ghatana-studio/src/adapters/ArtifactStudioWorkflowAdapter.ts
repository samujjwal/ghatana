/**
 * @fileoverview Studio workflow adapter for the artifact compiler/decompiler pipeline.
 *
 * Provides typed operations that connect the Studio UI to the
 * `@ghatana/artifact-compiler-ts` decompile/compile/fidelity pipeline.
 * The adapter is deliberately thin: it orchestrates calls to the compiler
 * package and converts results into shapes the Studio UI can consume directly.
 *
 * Import path: `../adapters/ArtifactStudioWorkflowAdapter`
 *
 * @doc.type module
 * @doc.purpose Studio ↔ Artifact compiler/decompiler workflow adapter
 * @doc.layer studio
 * @doc.pattern Adapter
 */

import type {
  LogicalArtifactModel,
  FidelityReport,
  ResidualIslandReport,
} from '@ghatana/artifact-contracts';

// ============================================================================
// Types
// ============================================================================

/** Status of an active or completed import/decompile job. */
export type DecompileJobStatus =
  | 'idle'
  | 'running'
  | 'complete'
  | 'failed';

/** A file entry submitted for decompilation. */
export interface SourceEntry {
  /** Relative file path within the project (e.g. "src/Button.tsx"). */
  readonly filePath: string;
  /** UTF-8 source content. */
  readonly content: string;
}

/** Result of a single decompile job. */
export interface DecompileJobResult {
  readonly jobId: string;
  readonly status: Exclude<DecompileJobStatus, 'idle' | 'running'>;
  readonly model: LogicalArtifactModel | null;
  readonly fidelityReport: FidelityReport | null;
  readonly residualIslandReport: ResidualIslandReport | null;
  readonly errors: readonly string[];
  readonly startedAt: string;
  readonly completedAt: string;
}

/** In-progress job state for Studio UI polling/display. */
export interface DecompileJobState {
  readonly jobId: string;
  readonly status: DecompileJobStatus;
  readonly progress: number; // 0–100
  readonly fileCount: number;
  readonly processedCount: number;
  readonly result?: DecompileJobResult;
  /** Display name for the primary source file (e.g. "Button.tsx"). */
  readonly fileName?: string;
}

/** Options for initiating a decompile job. */
export interface StartDecompileJobOptions {
  readonly sources: readonly SourceEntry[];
  /** Project root path for relative path resolution (optional). */
  readonly projectRoot?: string;
  /**
   * Confidence threshold below which nodes are classified as residual islands.
   * Default: 0.6
   */
  readonly confidenceThreshold?: number;
}

// ============================================================================
// Workflow operations
// ============================================================================

/**
 * Create a new decompile job state with initial values.
 * The caller is responsible for persisting and updating this state.
 */
export function createDecompileJobState(
  jobId: string,
  fileCount: number,
): DecompileJobState {
  return {
    jobId,
    status: 'idle',
    progress: 0,
    fileCount,
    processedCount: 0,
  };
}

/**
 * Produce a `DecompileJobResult` from a completed job.
 * Suitable for storing in persistent state or passing to the fidelity report page.
 */
export function buildDecompileJobResult(params: {
  jobId: string;
  model: LogicalArtifactModel | null;
  fidelityReport: FidelityReport | null;
  residualIslandReport: ResidualIslandReport | null;
  errors: readonly string[];
  startedAt: string;
}): DecompileJobResult {
  return {
    jobId: params.jobId,
    status: params.errors.length > 0 ? 'failed' : 'complete',
    model: params.model,
    fidelityReport: params.fidelityReport,
    residualIslandReport: params.residualIslandReport,
    errors: params.errors,
    startedAt: params.startedAt,
    completedAt: new Date().toISOString(),
  };
}

// ============================================================================
// Fidelity summary helpers for Studio UI
// ============================================================================

/**
 * Compute a traffic-light summary from a fidelity report suitable for
 * displaying in the Studio artifact list panel.
 */
export type FidelityTrafficLight = 'green' | 'amber' | 'red' | 'unknown';

export function fidelityTrafficLight(
  report: FidelityReport | null | undefined,
): FidelityTrafficLight {
  if (report == null) return 'unknown';
  const score = report.score;
  if (score >= 0.95) return 'green';
  if (score >= 0.75) return 'amber';
  return 'red';
}

/**
 * Return a concise human-readable summary of the fidelity report for display
 * in a tooltip or list-item subtitle.
 */
export function fidelitySummaryText(
  report: FidelityReport | null | undefined,
): string {
  if (report == null) return 'No fidelity data';
  const pct = Math.round(report.score * 100);
  const lossCount = report.lossPoints.length;
  if (lossCount === 0) return `${pct}% — no loss points`;
  return `${pct}% — ${lossCount} loss point${lossCount === 1 ? '' : 's'}`;
}

/**
 * Merge a decompile result's model into an existing merged model.
 *
 * When the Studio processes multiple files through separate calls, this helper
 * merges the resulting models.  Nodes are combined by nodeId; edges are
 * deduplicated by edgeId.
 */
export function mergeModels(
  base: LogicalArtifactModel | null,
  incoming: LogicalArtifactModel,
): LogicalArtifactModel {
  if (base === null) return incoming;

  // Nodes are a Record<string, ArtifactNode> — merge by nodeId key (incoming wins on conflict)
  const mergedNodes = { ...base.nodes, ...incoming.nodes };

  // Edges are an array — deduplicate by edge id
  const edgeIdSet = new Set<string>(base.edges.map((e) => e.id));
  const mergedEdges = [
    ...base.edges,
    ...incoming.edges.filter((e) => !edgeIdSet.has(e.id)),
  ];

  return {
    ...base,
    nodes: mergedNodes,
    edges: mergedEdges,
  };
}
