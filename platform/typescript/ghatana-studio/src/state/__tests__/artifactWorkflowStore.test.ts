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

import { describe, it, expect, beforeEach } from 'vitest';
import { createStore } from 'jotai';
import {
  artifactWorkflowAtom,
  artifactModelAtom,
  projectedBuilderDocumentAtom,
  artifactFidelityReportAtom,
  artifactPreviewSourceAtom,
  hasArtifactWorkflowResultAtom,
  setArtifactWorkflowAtom,
  clearArtifactWorkflowAtom,
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
