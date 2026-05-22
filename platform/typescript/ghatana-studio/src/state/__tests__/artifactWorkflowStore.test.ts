/**
 * @fileoverview Tests for ArtifactWorkflowStore Jotai atoms.
 *
 * Verifies that:
 * - artifactWorkflowAtom starts with null/empty initial state
 * - setArtifactWorkflowAtom merges partial updates correctly
 * - clearArtifactWorkflowAtom resets back to initial state
 * - derived read atoms reflect the current base atom state
 *
 * All tests operate against the real atoms without mocking Jotai internals.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { createStore } from 'jotai';
import {
  artifactWorkflowAtom,
  artifactModelAtom,
  projectedBuilderDocumentAtom,
  artifactFidelityReportAtom,
  artifactPreviewSourceAtom,
  artifactEvidencePackAtom,
  artifactRoundTripDiffReportAtom,
  hasArtifactWorkflowResultAtom,
  setArtifactWorkflowAtom,
  clearArtifactWorkflowAtom,
  reloadWorkflowStateAtom,
  resolvePersistenceAdapterForEnv,
} from '../artifactWorkflowStore.js';
import { createPerfectFidelityReport } from '@ghatana/artifact-contracts';
import { createLogicalArtifactModel } from '@ghatana/artifact-contracts';

// ============================================================================
// Helpers
// ============================================================================

/** Create an isolated Jotai store per test so atoms don't leak between tests. */
function makeStore() {
  return createStore();
}

afterEach(() => {
  vi.restoreAllMocks();
});

// ============================================================================
// Initial state
// ============================================================================

describe('artifactWorkflowAtom — initial state', () => {
  it('starts with null jobResult', () => {
    const store = makeStore();
    expect(store.get(artifactWorkflowAtom).jobResult).toBeNull();
  });

  it('starts with null model', () => {
    const store = makeStore();
    expect(store.get(artifactWorkflowAtom).model).toBeNull();
  });

  it('starts with null fidelityReport', () => {
    const store = makeStore();
    expect(store.get(artifactWorkflowAtom).fidelityReport).toBeNull();
  });

  it('starts with null previewSource', () => {
    const store = makeStore();
    expect(store.get(artifactWorkflowAtom).previewSource).toBeNull();
  });

  it('starts with null lastDecompileAt', () => {
    const store = makeStore();
    expect(store.get(artifactWorkflowAtom).lastDecompileAt).toBeNull();
  });

  it('starts with null evidencePack', () => {
    const store = makeStore();
    expect(store.get(artifactWorkflowAtom).evidencePack).toBeNull();
  });

  it('starts with null roundTripDiffReport', () => {
    const store = makeStore();
    expect(store.get(artifactWorkflowAtom).roundTripDiffReport).toBeNull();
  });
});

// ============================================================================
// Derived read atoms — initial state
// ============================================================================

describe('derived read atoms — initial state', () => {
  it('hasArtifactWorkflowResultAtom is false initially', () => {
    const store = makeStore();
    expect(store.get(hasArtifactWorkflowResultAtom)).toBe(false);
  });

  it('artifactModelAtom is null initially', () => {
    const store = makeStore();
    expect(store.get(artifactModelAtom)).toBeNull();
  });

  it('projectedBuilderDocumentAtom is null initially', () => {
    const store = makeStore();
    expect(store.get(projectedBuilderDocumentAtom)).toBeNull();
  });

  it('artifactFidelityReportAtom is null initially', () => {
    const store = makeStore();
    expect(store.get(artifactFidelityReportAtom)).toBeNull();
  });

  it('artifactPreviewSourceAtom is null initially', () => {
    const store = makeStore();
    expect(store.get(artifactPreviewSourceAtom)).toBeNull();
  });

  it('artifactEvidencePackAtom is null initially', () => {
    const store = makeStore();
    expect(store.get(artifactEvidencePackAtom)).toBeNull();
  });

  it('artifactRoundTripDiffReportAtom is null initially', () => {
    const store = makeStore();
    expect(store.get(artifactRoundTripDiffReportAtom)).toBeNull();
  });
});

// ============================================================================
// setArtifactWorkflowAtom — partial update
// ============================================================================

describe('setArtifactWorkflowAtom', () => {
  it('merges a partial fidelity report update', () => {
    const store = makeStore();
    const report = createPerfectFidelityReport('test-model');
    store.set(setArtifactWorkflowAtom, { fidelityReport: report });
    expect(store.get(artifactFidelityReportAtom)).toBe(report);
    // Other fields remain null
    expect(store.get(artifactModelAtom)).toBeNull();
  });

  it('merges a previewSource update without affecting other fields', () => {
    const store = makeStore();
    store.set(setArtifactWorkflowAtom, { previewSource: '<html>preview</html>' });
    expect(store.get(artifactPreviewSourceAtom)).toBe('<html>preview</html>');
    expect(store.get(artifactFidelityReportAtom)).toBeNull();
  });

  it('allows multiple sequential partial updates to accumulate', () => {
    const store = makeStore();
    const model = createLogicalArtifactModel('m1', 'test-model');
    const report = createPerfectFidelityReport('m1');

    store.set(setArtifactWorkflowAtom, { model });
    store.set(setArtifactWorkflowAtom, { fidelityReport: report });
    store.set(setArtifactWorkflowAtom, { previewSource: 'export default function App() {}' });

    const state = store.get(artifactWorkflowAtom);
    expect(state.model).toBe(model);
    expect(state.fidelityReport).toBe(report);
    expect(state.previewSource).toBe('export default function App() {}');
  });

  it('sets hasArtifactWorkflowResultAtom to true when jobResult is set', () => {
    const store = makeStore();
    // Create a minimal DecompileJobResult-compatible object
    const minimalJobResult = {
      jobId: 'job-1',
      status: 'complete' as const,
      model: createLogicalArtifactModel('m1', 'test'),
      fidelityReport: createPerfectFidelityReport('m1'),
      residualIslandReport: { islands: [], evidenceId: 'ev-1' },
      errors: [],
      completedAt: '2024-01-01T00:00:00.000Z',
    };
    // @ts-expect-error minimal shape for store test
    store.set(setArtifactWorkflowAtom, { jobResult: minimalJobResult });
    expect(store.get(hasArtifactWorkflowResultAtom)).toBe(true);
  });
});

// ============================================================================
// clearArtifactWorkflowAtom
// ============================================================================

describe('clearArtifactWorkflowAtom', () => {
  it('resets all state to initial null values', () => {
    const store = makeStore();
    const report = createPerfectFidelityReport('test-model');

    // Populate state
    store.set(setArtifactWorkflowAtom, {
      fidelityReport: report,
      previewSource: 'some source',
      lastDecompileAt: '2024-01-01T00:00:00.000Z',
    });

    // Clear
    store.set(clearArtifactWorkflowAtom);

    const state = store.get(artifactWorkflowAtom);
    expect(state.fidelityReport).toBeNull();
    expect(state.previewSource).toBeNull();
    expect(state.lastDecompileAt).toBeNull();
    expect(store.get(hasArtifactWorkflowResultAtom)).toBe(false);
  });
});

// ============================================================================
// reloadWorkflowStateAtom — persistence recovery
// ============================================================================

describe('reloadWorkflowStateAtom', () => {
  it('restores state from persistence adapter', async () => {
    const store = makeStore();
    const model = createLogicalArtifactModel('m1', 'test-model');
    const report = createPerfectFidelityReport('m1');

    // Set initial state
    store.set(setArtifactWorkflowAtom, {
      model,
      fidelityReport: report,
      lastDecompileAt: '2024-01-01T00:00:00.000Z',
    });

    // Create a new store to simulate reload
    const newStore = makeStore();
    await newStore.set(reloadWorkflowStateAtom);

    // Verify state was restored
    expect(newStore.get(artifactModelAtom)).toEqual(model);
    expect(newStore.get(artifactFidelityReportAtom)).toEqual(report);
    expect(newStore.get(artifactWorkflowAtom).lastDecompileAt).toBe('2024-01-01T00:00:00.000Z');
  });

  it('handles null persistence gracefully', async () => {
    const store = makeStore();
    // Clear any existing persisted state before testing
    await store.set(clearArtifactWorkflowAtom);
    await store.set(reloadWorkflowStateAtom);

    // Should remain in initial state
    expect(store.get(artifactModelAtom)).toBeNull();
    expect(store.get(artifactFidelityReportAtom)).toBeNull();
  });

  it('restores complete workflow state including all fields', async () => {
    const store = makeStore();
    const model = createLogicalArtifactModel('m1', 'test-model');
    const report = createPerfectFidelityReport('m1');
    const residuals = {
      islands: [],
      totalCount: 0,
      blockingCount: 0,
      canCompileWithResiduals: true,
    };

    // Set complete state with proper contract types
    store.set(setArtifactWorkflowAtom, {
      jobResult: {
        jobId: 'job-1',
        status: 'complete',
        model,
        fidelityReport: report,
        residualIslandReport: residuals,
        errors: [],
        completedAt: '2024-01-01T00:00:00.000Z',
        startedAt: '2024-01-01T00:00:00.000Z',
      },
      model,
      fidelityReport: report,
      previewSource: 'export default function App() {}',
      evidencePack: {
        evidenceId: 'ev-1',
        createdAt: '2024-01-01T00:00:00.000Z',
        modelId: 'm1',
        label: 'test-model',
        stage: 'decompile',
        fidelity: report,
        residuals,
        decompileResult: {
          success: true,
          modelId: 'm1',
          nodeCount: 1,
          edgeCount: 0,
          fidelity: report,
          residuals,
          errors: [],
          decompiledAt: '2024-01-01T00:00:00.000Z',
        },
        reviewStatus: 'pending',
      },
      roundTripDiffReport: {
        reportId: 'diff-1',
        modelId: 'm1',
        diffs: [],
        fidelity: report,
        residuals,
        isLossless: true,
        generatedAt: '2024-01-01T00:00:00.000Z',
      },
      lastDecompileAt: '2024-01-01T00:00:00.000Z',
    });

    // Reload in new store
    const newStore = makeStore();
    await newStore.set(reloadWorkflowStateAtom);

    // Verify all fields restored
    const state = newStore.get(artifactWorkflowAtom);
    expect(state.jobResult).toBeDefined();
    expect(state.model).toEqual(model);
    expect(state.fidelityReport).toEqual(report);
    expect(state.previewSource).toBe('export default function App() {}');
    expect(state.evidencePack).toBeDefined();
    expect(state.roundTripDiffReport).toBeDefined();
    expect(state.lastDecompileAt).toBe('2024-01-01T00:00:00.000Z');
    expect(newStore.get(hasArtifactWorkflowResultAtom)).toBe(true);
  });
});

describe('resolvePersistenceAdapterForEnv', () => {
  it('uses local persistence when kernel profile is disabled', async () => {
    const adapter = resolvePersistenceAdapterForEnv({
      VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE: 'false',
    });

    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    await adapter.persist(
      {
        jobResult: null,
        model: null,
        projectedBuilderDocument: null,
        previewSource: null,
        fidelityReport: null,
        evidencePack: null,
        roundTripDiffReport: null,
        lastDecompileAt: null,
      },
      {
        persistedAt: '2026-01-01T00:00:00.000Z',
        lastModifiedAt: '2026-01-01T00:00:00.000Z',
        persistenceVersion: 1,
      },
    );

    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('uses local fallback when kernel profile is enabled but identity is incomplete', async () => {
    const adapter = resolvePersistenceAdapterForEnv({
      VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE: 'true',
      VITE_GHATANA_KERNEL_API_BASE_URL: 'https://kernel.local',
      VITE_STUDIO_TENANT_ID: 'tenant-a',
      VITE_STUDIO_WORKSPACE_ID: 'workspace-a',
      VITE_STUDIO_PROJECT_ID: 'project-a',
      // Missing auth token on purpose.
    });

    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    await adapter.persist(
      {
        jobResult: null,
        model: null,
        projectedBuilderDocument: null,
        previewSource: null,
        fidelityReport: null,
        evidencePack: null,
        roundTripDiffReport: null,
        lastDecompileAt: null,
      },
      {
        persistedAt: '2026-01-01T00:00:00.000Z',
        lastModifiedAt: '2026-01-01T00:00:00.000Z',
        persistenceVersion: 1,
      },
    );

    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('fails closed when production requires kernel persistence but identity is incomplete', () => {
    expect(() =>
      resolvePersistenceAdapterForEnv({
        VITE_STUDIO_DEPLOYMENT_PROFILE: 'production',
        VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE: 'true',
        VITE_GHATANA_KERNEL_API_BASE_URL: 'https://kernel.local',
        VITE_STUDIO_TENANT_ID: 'tenant-a',
        VITE_STUDIO_WORKSPACE_ID: 'workspace-a',
        VITE_STUDIO_PROJECT_ID: 'project-a',
      }),
    ).toThrow(/requires kernel base URL, tenant, workspace, project, and auth token/);
  });

  it('fails closed when kernel persistence is explicitly required but disabled', () => {
    expect(() =>
      resolvePersistenceAdapterForEnv({
        VITE_STUDIO_REQUIRE_KERNEL_WORKFLOW_PERSISTENCE: 'true',
        VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE: 'false',
      }),
    ).toThrow(/requires VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE=true/);
  });

  it('persists workflow state and evidence through kernel endpoints when profile is fully enabled', async () => {
    const adapter = resolvePersistenceAdapterForEnv({
      VITE_STUDIO_ENABLE_KERNEL_WORKFLOW_PERSISTENCE: 'true',
      VITE_GHATANA_KERNEL_API_BASE_URL: 'https://kernel.local',
      VITE_STUDIO_TENANT_ID: 'tenant-a',
      VITE_STUDIO_WORKSPACE_ID: 'workspace-a',
      VITE_STUDIO_PROJECT_ID: 'project-a',
      VITE_STUDIO_AUTH_TOKEN: 'token-a',
      VITE_STUDIO_ENABLE_KERNEL_EVIDENCE_PERSISTENCE: 'true',
    });

    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue({ ok: true, status: 200 } as Response);

    await adapter.persist(
      {
        jobResult: null,
        model: null,
        projectedBuilderDocument: null,
        previewSource: null,
        fidelityReport: null,
        evidencePack: {
          evidenceId: 'ev-1',
          createdAt: '2026-01-01T00:00:00.000Z',
          modelId: 'model-1',
          label: 'Evidence',
          stage: 'round-trip',
          fidelity: createPerfectFidelityReport('model-1'),
          residuals: {
            islands: [],
            totalCount: 0,
            blockingCount: 0,
            canCompileWithResiduals: true,
          },
          decompileResult: {
            success: true,
            modelId: 'model-1',
            nodeCount: 0,
            edgeCount: 0,
            fidelity: createPerfectFidelityReport('model-1'),
            residuals: {
              islands: [],
              totalCount: 0,
              blockingCount: 0,
              canCompileWithResiduals: true,
            },
            errors: [],
            decompiledAt: '2026-01-01T00:00:00.000Z',
          },
          reviewStatus: 'pending',
        },
        roundTripDiffReport: null,
        lastDecompileAt: null,
      },
      {
        persistedAt: '2026-01-01T00:00:00.000Z',
        lastModifiedAt: '2026-01-01T00:00:00.000Z',
        persistenceVersion: 1,
      },
    );

    expect(fetchSpy).toHaveBeenCalledTimes(2);
    expect(fetchSpy).toHaveBeenNthCalledWith(
      1,
      'https://kernel.local/api/v1/studio/workflow-state',
      expect.objectContaining({ method: 'PUT' }),
    );
    expect(fetchSpy).toHaveBeenNthCalledWith(
      2,
      'https://kernel.local/api/v1/studio/workflow-evidence',
      expect.objectContaining({ method: 'PUT' }),
    );
  });
});
