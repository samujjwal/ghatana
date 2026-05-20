/**
 * @fileoverview ArtifactWorkflowStore — Jotai atoms for the studio artifact pipeline.
 *
 * This is the single shared state container for the import/decompile/edit/preview/fidelity
 * workflow. Routes that participate in the pipeline (Import, Canvas, Builder, Preview,
 * FidelityReport) read and write from these atoms rather than using React Router `state`
 * or independent local state.
 *
 * Atom graph (write path):
 *   ImportDecompilePage → artifactWorkflowAtom (set jobResult + model)
 *   CanvasPage → reads artifactWorkflowAtom.projectedBuilderDocument
 *   BuilderPage → reads artifactWorkflowAtom.projectedBuilderDocument
 *   PreviewPage → reads artifactWorkflowAtom.previewSource
 *   FidelityReportPage → reads artifactWorkflowAtom.fidelityReport
 *
 * @doc.type module
 * @doc.purpose Jotai atom store for the artifact import/edit/preview workflow
 * @doc.layer studio
 * @doc.pattern Store
 */

import { atom } from 'jotai';
import type { DecompileJobResult } from '../adapters/ArtifactStudioWorkflowAdapter.js';
import type { BuilderDocument } from '@ghatana/ui-builder';
import type { FidelityReport } from '@ghatana/artifact-contracts';
import type { LogicalArtifactModel } from '@ghatana/artifact-contracts';

// ============================================================================
// STATE SHAPE
// ============================================================================

/**
 * The active artifact workflow state managed across all Studio routes.
 *
 * `null` means no decompile job has been completed in this session yet.
 */
export interface ArtifactWorkflowState {
  /**
   * The completed decompile job result.
   * Null until a successful decompile has run.
   */
  readonly jobResult: DecompileJobResult | null;

  /**
   * The LogicalArtifactModel extracted by the decompiler.
   * Null if the job failed or no job has run.
   */
  readonly model: LogicalArtifactModel | null;

  /**
   * A BuilderDocument projected from `model` for display in the Canvas and
   * Builder routes. Updated when a new model is set.
   */
  readonly projectedBuilderDocument: BuilderDocument | null;

  /**
   * The generated TypeScript/TSX source for Preview.
   * Set when the compiler has produced output from the model.
   */
  readonly previewSource: string | null;

  /**
   * The most recent fidelity report for the loaded model.
   */
  readonly fidelityReport: FidelityReport | null;

  /**
   * ISO-8601 timestamp of the last successful decompile run.
   */
  readonly lastDecompileAt: string | null;
}

// ============================================================================
// ATOMS
// ============================================================================

/** Initial state with no workflow data. */
const INITIAL_STATE: ArtifactWorkflowState = {
  jobResult: null,
  model: null,
  projectedBuilderDocument: null,
  previewSource: null,
  fidelityReport: null,
  lastDecompileAt: null,
};

/**
 * Root workflow atom. Holds the full ArtifactWorkflowState.
 *
 * Write by dispatching a partial update or a full state via `setArtifactWorkflow`.
 */
export const artifactWorkflowAtom = atom<ArtifactWorkflowState>(INITIAL_STATE);

// ============================================================================
// DERIVED READ ATOMS
// ============================================================================

/**
 * The current model (null if no job has run).
 */
export const artifactModelAtom = atom<LogicalArtifactModel | null>(
  (get) => get(artifactWorkflowAtom).model,
);

/**
 * The projected BuilderDocument (null if no job has run successfully).
 */
export const projectedBuilderDocumentAtom = atom<BuilderDocument | null>(
  (get) => get(artifactWorkflowAtom).projectedBuilderDocument,
);

/**
 * The fidelity report (null if no job has run).
 */
export const artifactFidelityReportAtom = atom<FidelityReport | null>(
  (get) => get(artifactWorkflowAtom).fidelityReport,
);

/**
 * The preview source (null if no compilation has run).
 */
export const artifactPreviewSourceAtom = atom<string | null>(
  (get) => get(artifactWorkflowAtom).previewSource,
);

/**
 * Whether a successful workflow run is available.
 */
export const hasArtifactWorkflowResultAtom = atom<boolean>(
  (get) => get(artifactWorkflowAtom).jobResult !== null,
);

// ============================================================================
// WRITE ATOMS / ACTIONS
// ============================================================================

/**
 * Action atom: merge a partial ArtifactWorkflowState update into the root atom.
 *
 * Usage:
 * ```tsx
 * const setWorkflow = useSetAtom(setArtifactWorkflowAtom);
 * setWorkflow({ model, fidelityReport, projectedBuilderDocument });
 * ```
 */
export const setArtifactWorkflowAtom = atom(
  null,
  (get, set, update: Partial<ArtifactWorkflowState>) => {
    const current = get(artifactWorkflowAtom);
    set(artifactWorkflowAtom, { ...current, ...update });
  },
);

/**
 * Action atom: clear all workflow state (e.g. when starting a new import).
 */
export const clearArtifactWorkflowAtom = atom(null, (_get, set) => {
  set(artifactWorkflowAtom, INITIAL_STATE);
});
