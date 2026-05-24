/**
 * @fileoverview ArtifactWorkflowStore — Jotai atoms for the studio artifact pipeline.
 *
 * This is the single shared state container for the import/decompile/edit/preview/fidelity
 * workflow. Routes that participate in the pipeline (Import, Canvas, Builder, Preview,
 * FidelityReport) read and write from these atoms rather than using React Router `state`
 * or independent local state.
 *
 * Includes persistence adapter for durable workflow state across page reloads.
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
import type { EvidencePack, FidelityReport, RoundTripDiffReport } from '@ghatana/artifact-contracts';
import type { LogicalArtifactModel } from '@ghatana/artifact-contracts';
import { ApiClient } from '@ghatana/api';
import { studioLogger } from '../logging/studioLogger';
import { StudioProductionProfileError, isProductionStudioProfile } from '../config/studioEnvironment';

// ============================================================================
// PERSISTENCE ADAPTER
// ============================================================================

/**
 * Audit metadata for workflow state persistence.
 */
export interface WorkflowAuditMetadata {
  /** ISO-8601 timestamp when state was last persisted. */
  readonly persistedAt: string;
  /** ISO-8601 timestamp when state was last modified. */
  readonly lastModifiedAt: string;
  /** Number of times the state has been persisted. */
  readonly persistenceVersion: number;
}

/**
 * Persisted workflow state with audit metadata.
 */
export interface PersistedWorkflowState {
  /** The workflow state. */
  readonly state: ArtifactWorkflowState;
  /** Audit metadata. */
  readonly audit: WorkflowAuditMetadata;
}

/**
 * Persistence adapter interface for workflow state.
 *
 * Implementations can use localStorage, sessionStorage, IndexedDB, or remote storage.
 */
export interface WorkflowPersistenceAdapter {
  /**
   * Persist the workflow state with audit metadata.
   */
  persist(state: ArtifactWorkflowState, audit: WorkflowAuditMetadata): Promise<void>;

  /**
   * Load the persisted workflow state with audit metadata.
   */
  load(): Promise<PersistedWorkflowState | null>;

  /**
   * Clear all persisted workflow state.
   */
  clear(): Promise<void>;
}

/**
 * LocalStorage-based persistence adapter.
 */
class LocalStoragePersistenceAdapter implements WorkflowPersistenceAdapter {
  private readonly STORAGE_KEY = 'ghatana-studio-workflow-state';

  async persist(state: ArtifactWorkflowState, audit: WorkflowAuditMetadata): Promise<void> {
    try {
      const persisted: PersistedWorkflowState = { state, audit };
      const serialized = JSON.stringify(persisted);
      localStorage.setItem(this.STORAGE_KEY, serialized);
    } catch (err) {
      studioLogger.error('Failed to persist workflow state', { error: err });
    }
  }

  async load(): Promise<PersistedWorkflowState | null> {
    try {
      const serialized = localStorage.getItem(this.STORAGE_KEY);
      if (!serialized) return null;
      const parsed = JSON.parse(serialized) as PersistedWorkflowState;
      return parsed;
    } catch (err) {
      studioLogger.error('Failed to load persisted workflow state', { error: err });
      return null;
    }
  }

  async clear(): Promise<void> {
    try {
      localStorage.removeItem(this.STORAGE_KEY);
    } catch (err) {
      studioLogger.error('Failed to clear persisted workflow state', { error: err });
    }
  }
}

class KernelWorkflowPersistenceAdapter implements WorkflowPersistenceAdapter {
  private readonly apiClient: ApiClient;

  constructor(
    private readonly config: {
      readonly baseUrl: string;
      readonly tenantId: string;
      readonly workspaceId: string;
      readonly projectId: string;
      readonly authToken: string;
      readonly enableEvidencePersistence: boolean;
    },
  ) {
    this.apiClient = new ApiClient({ baseUrl: config.baseUrl });
  }

  async persist(state: ArtifactWorkflowState, audit: WorkflowAuditMetadata): Promise<void> {
    const payload: PersistedWorkflowState = { state, audit };
    try {
      await this.apiClient.put<void>('/api/v1/studio/workflow-state', {
        headers: this.headersWithIdempotency(`studio-workflow-state:${audit.persistenceVersion}:${audit.lastModifiedAt}`),
        body: payload,
      });

      if (this.config.enableEvidencePersistence && state.evidencePack !== null) {
        await this.persistEvidencePack(state.evidencePack);
      }
    } catch (err) {
      studioLogger.error('Failed to persist workflow state via kernel API', { error: err });
      throw err;
    }
  }

  async load(): Promise<PersistedWorkflowState | null> {
    try {
      const response = await this.apiClient.get<PersistedWorkflowState>('/api/v1/studio/workflow-state', {
        headers: this.headers,
      });
      return response.data;
    } catch (err) {
      if (isApiStatus(err, 404)) {
        return null;
      }
      studioLogger.error('Failed to load workflow state via kernel API', { error: err });
      return null;
    }
  }

  async clear(): Promise<void> {
    try {
      await this.apiClient.delete<void>('/api/v1/studio/workflow-state', {
        headers: this.headers,
      });
    } catch (err) {
      if (isApiStatus(err, 404)) {
        return;
      }
      studioLogger.error('Failed to clear workflow state via kernel API', { error: err });
    }
  }

  private get headers(): Record<string, string> {
    return {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${this.config.authToken}`,
      'x-ghatana-tenant-id': this.config.tenantId,
      'x-ghatana-workspace-id': this.config.workspaceId,
      'x-ghatana-project-id': this.config.projectId,
      'x-tenant-id': this.config.tenantId,
      'x-workspace-id': this.config.workspaceId,
      'x-project-id': this.config.projectId,
    };
  }

  private headersWithIdempotency(idempotencyKey: string): Record<string, string> {
    return {
      ...this.headers,
      'Idempotency-Key': idempotencyKey,
    };
  }

  private async persistEvidencePack(evidencePack: EvidencePack): Promise<void> {
    await this.apiClient.put<void>('/api/v1/studio/workflow-evidence', {
      headers: this.headersWithIdempotency(`studio-workflow-evidence:${evidencePack.evidenceId}`),
      body: evidencePack,
    });
  }
}

function isApiStatus(error: unknown, status: number): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'status' in error &&
    (error as { readonly status?: unknown }).status === status
  );
}

export function resolvePersistenceAdapterForEnv(
  env?: Record<string, string | undefined>,
): WorkflowPersistenceAdapter {
  const enableKernelPersistence = env?.VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE === 'true';
  const requireKernelPersistence =
    env?.VITE_STUDIO_REQUIRE_KERNEL_WORKFLOW_PERSISTENCE === 'true' ||
    isProductionStudioProfile(env);

  if (!enableKernelPersistence) {
    if (requireKernelPersistence) {
      throw new StudioProductionProfileError(
        'Production Studio requires VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE=true.',
      );
    }
    return new LocalStoragePersistenceAdapter();
  }

  const baseUrl = env?.VITE_GHATANA_KERNEL_API_BASE_URL?.trim();
  const tenantId = env?.VITE_STUDIO_TENANT_ID?.trim();
  const workspaceId = env?.VITE_STUDIO_WORKSPACE_ID?.trim();
  const projectId = env?.VITE_STUDIO_PROJECT_ID?.trim();
  const authToken = env?.VITE_STUDIO_AUTH_TOKEN?.trim();
  const enableEvidencePersistence = env?.VITE_STUDIO_ENABLE_KERNEL_EVIDENCE_PERSISTENCE !== 'false';

  if (!baseUrl || !tenantId || !workspaceId || !projectId || !authToken) {
    if (requireKernelPersistence) {
      throw new StudioProductionProfileError(
        'Kernel workflow persistence requires kernel base URL, tenant, workspace, project, and auth token.',
      );
    }
    studioLogger.warn('Kernel workflow persistence is enabled but runtime identity is incomplete; using local storage fallback.', {
      hasBaseUrl: Boolean(baseUrl),
      hasTenantId: Boolean(tenantId),
      hasWorkspaceId: Boolean(workspaceId),
      hasProjectId: Boolean(projectId),
      hasAuthToken: Boolean(authToken),
    });
    return new LocalStoragePersistenceAdapter();
  }

  return new KernelWorkflowPersistenceAdapter({
    baseUrl,
    tenantId,
    workspaceId,
    projectId,
    authToken,
    enableEvidencePersistence,
  });
}

function resolvePersistenceAdapter(): WorkflowPersistenceAdapter {
  const env = (import.meta as ImportMeta & { readonly env?: Record<string, string | undefined> }).env;
  return resolvePersistenceAdapterForEnv(env);
}

/**
 * Default persistence adapter using localStorage.
 */
export const defaultPersistenceAdapter: WorkflowPersistenceAdapter = resolvePersistenceAdapter();

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
   * Durable evidence pack for the latest import/compile/preview workflow.
   */
  readonly evidencePack: EvidencePack | null;

  /**
   * Source/model/source diff report for the latest round-trip.
   */
  readonly roundTripDiffReport: RoundTripDiffReport | null;

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
  evidencePack: null,
  roundTripDiffReport: null,
  lastDecompileAt: null,
};

function loadInitialWorkflowState(): ArtifactWorkflowState {
  if (typeof globalThis.localStorage === 'undefined') {
    return INITIAL_STATE;
  }

  try {
    const serialized = globalThis.localStorage.getItem('ghatana-studio-workflow-state');
    if (!serialized) {
      return INITIAL_STATE;
    }
    const parsed = JSON.parse(serialized) as Partial<PersistedWorkflowState>;
    return parsed.state ?? INITIAL_STATE;
  } catch (err) {
    studioLogger.error('Failed to hydrate workflow state', { error: err });
    return INITIAL_STATE;
  }
}

/**
 * Root workflow atom. Holds the full ArtifactWorkflowState.
 *
 * Write by dispatching a partial update or a full state via `setArtifactWorkflow`.
 */
export const artifactWorkflowAtom = atom<ArtifactWorkflowState>(loadInitialWorkflowState());

/**
 * Internal audit metadata tracking (not part of public state).
 */
let currentAuditVersion = 0;

/**
 * Write atom for updating workflow state with persistence.
 *
 * Automatically persists to the configured adapter on write with audit metadata.
 */
export const setArtifactWorkflowAtom = atom(
  null,
  async (get, set, update: Partial<ArtifactWorkflowState>) => {
    const current = get(artifactWorkflowAtom);
    const newState = { ...current, ...update };
    set(artifactWorkflowAtom, newState);

    // Increment audit version
    currentAuditVersion += 1;

    // Persist with audit metadata
    const audit: WorkflowAuditMetadata = {
      persistedAt: new Date().toISOString(),
      lastModifiedAt: newState.lastDecompileAt ?? current.lastDecompileAt ?? new Date().toISOString(),
      persistenceVersion: currentAuditVersion,
    };

    await defaultPersistenceAdapter.persist(newState, audit);
  },
);

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

export const artifactEvidencePackAtom = atom<EvidencePack | null>(
  (get) => get(artifactWorkflowAtom).evidencePack,
);

export const artifactRoundTripDiffReportAtom = atom<RoundTripDiffReport | null>(
  (get) => get(artifactWorkflowAtom).roundTripDiffReport,
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
 * This atom is defined earlier with persistence support.
 *
 * Usage:
 * ```tsx
 * const setWorkflow = useSetAtom(setArtifactWorkflowAtom);
 * setWorkflow({ model, fidelityReport, projectedBuilderDocument });
 * ```
 */

/**
 * Action atom: clear all workflow state (e.g. when starting a new import).
 * Also clears persisted state.
 */
export const clearArtifactWorkflowAtom = atom(null, async (_get, set) => {
  set(artifactWorkflowAtom, INITIAL_STATE);
  currentAuditVersion = 0;
  await defaultPersistenceAdapter.clear();
});

/**
 * Action atom: reload workflow state from persistence.
 *
 * Call this on app initialization to restore the previous session.
 */
export const reloadWorkflowStateAtom = atom(null, async (_get, set) => {
  const persisted = await defaultPersistenceAdapter.load();
  if (persisted) {
    set(artifactWorkflowAtom, persisted.state);
    currentAuditVersion = persisted.audit.persistenceVersion;
  }
});
