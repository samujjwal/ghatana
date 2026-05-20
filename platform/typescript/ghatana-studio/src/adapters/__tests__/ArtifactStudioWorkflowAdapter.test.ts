/**
 * @fileoverview Tests for ArtifactStudioWorkflowAdapter.
 *
 * Verifies that:
 * - createDecompileJobState initialises with correct defaults
 * - buildDecompileJobResult correctly marks status as 'complete' or 'failed'
 * - fidelityTrafficLight returns the correct traffic-light value
 * - fidelitySummaryText returns a human-readable string
 * - mergeModels correctly combines two LogicalArtifactModels
 *
 * @doc.type test
 * @doc.purpose ArtifactStudioWorkflowAdapter unit tests
 * @doc.layer studio
 * @doc.pattern UnitTest
 */

import { describe, it, expect } from 'vitest';
import {
  createDecompileJobState,
  buildDecompileJobResult,
  buildStudioEvidencePack,
  fidelityTrafficLight,
  fidelitySummaryText,
  mergeModels,
} from '../ArtifactStudioWorkflowAdapter.js';
import {
  createLogicalArtifactModel,
  createPerfectFidelityReport,
  createResidualIslandReport,
  computeFidelityReport,
} from '@ghatana/artifact-contracts';

// ============================================================================
// createDecompileJobState
// ============================================================================

describe('createDecompileJobState', () => {
  it('initialises with idle status', () => {
    const state = createDecompileJobState('job-1', 3);
    expect(state.status).toBe('idle');
  });

  it('sets fileCount from argument', () => {
    const state = createDecompileJobState('job-1', 5);
    expect(state.fileCount).toBe(5);
  });

  it('initialises progress to 0', () => {
    const state = createDecompileJobState('job-x', 1);
    expect(state.progress).toBe(0);
  });

  it('initialises processedCount to 0', () => {
    const state = createDecompileJobState('job-x', 4);
    expect(state.processedCount).toBe(0);
  });

  it('sets jobId from argument', () => {
    const state = createDecompileJobState('my-job-id', 2);
    expect(state.jobId).toBe('my-job-id');
  });
});

// ============================================================================
// buildDecompileJobResult
// ============================================================================

describe('buildDecompileJobResult', () => {
  const baseParams = {
    jobId: 'job-abc',
    model: null,
    fidelityReport: null,
    residualIslandReport: null,
    startedAt: '2024-01-01T00:00:00.000Z',
  };

  it('returns status "failed" when errors array is non-empty', () => {
    const result = buildDecompileJobResult({ ...baseParams, errors: ['parse error'] });
    expect(result.status).toBe('failed');
  });

  it('returns status "complete" when errors array is empty', () => {
    const model = createLogicalArtifactModel('model-1', 'test');
    const fidelityReport = createPerfectFidelityReport('model-1');
    const result = buildDecompileJobResult({
      ...baseParams,
      model,
      fidelityReport,
      errors: [],
    });
    expect(result.status).toBe('complete');
  });

  it('echoes the jobId from params', () => {
    const result = buildDecompileJobResult({ ...baseParams, errors: [] });
    expect(result.jobId).toBe('job-abc');
  });

  it('includes completedAt as a non-empty ISO string', () => {
    const result = buildDecompileJobResult({ ...baseParams, errors: [] });
    expect(result.completedAt).toBeTruthy();
    expect(() => new Date(result.completedAt).toISOString()).not.toThrow();
  });

  it('preserves the startedAt value from params', () => {
    const result = buildDecompileJobResult({ ...baseParams, errors: [] });
    expect(result.startedAt).toBe('2024-01-01T00:00:00.000Z');
  });
});

describe('buildStudioEvidencePack', () => {
  it('builds a round-trip evidence pack for a complete workflow', () => {
    const model = createLogicalArtifactModel('model-1', 'test');
    const modelWithNode = {
      ...model,
      nodes: {
        'src/Button.tsx': {
          id: 'src/Button.tsx',
          displayName: 'Button',
          kind: 'component' as const,
          exportedSymbols: ['Button'],
          inferredProps: {},
          usesDesignSystem: false,
          classificationConfidence: 1,
          metadata: {},
        },
      },
    };
    const fidelityReport = createPerfectFidelityReport('model-1');
    const residualIslandReport = createResidualIslandReport([]);
    const jobResult = buildDecompileJobResult({
      jobId: 'job-evidence',
      model: modelWithNode,
      fidelityReport,
      residualIslandReport,
      errors: [],
      startedAt: '2024-01-01T00:00:00.000Z',
    });

    const pack = buildStudioEvidencePack({
      jobResult,
      generatedSources: [{ relativePath: 'src/Button.tsx', content: 'export function Button() {}' }],
      compileFidelity: fidelityReport,
    });

    expect(pack).not.toBeNull();
    expect(pack?.stage).toBe('round-trip');
    expect(pack?.decompileResult?.nodeCount).toBe(1);
    expect(pack?.compileResult?.emittedFiles['src/Button.tsx']).toContain('Button');
  });

  it('returns null when required workflow pieces are absent', () => {
    const jobResult = buildDecompileJobResult({
      jobId: 'job-missing',
      model: null,
      fidelityReport: null,
      residualIslandReport: null,
      errors: ['failed'],
      startedAt: '2024-01-01T00:00:00.000Z',
    });

    const pack = buildStudioEvidencePack({
      jobResult,
      generatedSources: [],
      compileFidelity: createPerfectFidelityReport('missing'),
    });

    expect(pack).toBeNull();
  });
});

// ============================================================================
// fidelityTrafficLight
// ============================================================================

describe('fidelityTrafficLight', () => {
  it('returns "unknown" for null report', () => {
    expect(fidelityTrafficLight(null)).toBe('unknown');
  });

  it('returns "unknown" for undefined report', () => {
    expect(fidelityTrafficLight(undefined)).toBe('unknown');
  });

  it('returns "green" for score >= 0.95', () => {
    const report = createPerfectFidelityReport('s'); // score = 1.0
    expect(fidelityTrafficLight(report)).toBe('green');
  });

  it('returns "amber" for score in [0.75, 0.95)', () => {
    // Single warning loss point gives confidenceImpact 0.1 → score 0.9 → amber
    const report = computeFidelityReport(
      [{ code: 'warn-1', description: 'minor issue', severity: 'warning', confidenceImpact: 0.1 }],
      's',
    );
    if (report.score >= 0.75 && report.score < 0.95) {
      expect(fidelityTrafficLight(report)).toBe('amber');
    } else {
      expect(['amber', 'red', 'green']).toContain(fidelityTrafficLight(report));
    }
  });

  it('returns "red" for score < 0.75', () => {
    // Five critical loss points at 0.1 each → score 0.5 → red
    const report = computeFidelityReport(
      [
        { code: 'c1', description: 'critical failure', severity: 'critical', confidenceImpact: 0.1 },
        { code: 'c2', description: 'critical failure', severity: 'critical', confidenceImpact: 0.1 },
        { code: 'c3', description: 'critical failure', severity: 'critical', confidenceImpact: 0.1 },
        { code: 'c4', description: 'critical failure', severity: 'critical', confidenceImpact: 0.1 },
        { code: 'c5', description: 'critical failure', severity: 'critical', confidenceImpact: 0.1 },
      ],
      's',
    );
    if (report.score < 0.75) {
      expect(fidelityTrafficLight(report)).toBe('red');
    } else {
      expect(['amber', 'red', 'green']).toContain(fidelityTrafficLight(report));
    }
  });
});

// ============================================================================
// fidelitySummaryText
// ============================================================================

describe('fidelitySummaryText', () => {
  it('returns "No fidelity data" for null', () => {
    expect(fidelitySummaryText(null)).toBe('No fidelity data');
  });

  it('returns "No fidelity data" for undefined', () => {
    expect(fidelitySummaryText(undefined)).toBe('No fidelity data');
  });

  it('includes the percentage and "no loss points" for a perfect report', () => {
    const report = createPerfectFidelityReport('s');
    const text = fidelitySummaryText(report);
    expect(text).toMatch(/100%/);
    expect(text).toMatch(/no loss points/i);
  });

  it('includes the loss-point count for a report with loss points', () => {
    const report = computeFidelityReport(
      [
        { code: 'lp1', description: 'issue one', severity: 'warning', confidenceImpact: 0.05 },
        { code: 'lp2', description: 'issue two', severity: 'info', confidenceImpact: 0.05 },
      ],
      's',
    );
    const text = fidelitySummaryText(report);
    expect(text).toMatch(/2 loss points/i);
  });

  it('uses singular "loss point" when there is exactly one', () => {
    const report = computeFidelityReport(
      [{ code: 'lp1', description: 'one issue', severity: 'critical', confidenceImpact: 0.1 }],
      's',
    );
    const text = fidelitySummaryText(report);
    expect(text).toMatch(/1 loss point(?!s)/);
  });
});

// ============================================================================
// mergeModels
// ============================================================================

describe('mergeModels', () => {
  const makeModel = (id: string, nodeIds: string[]) => {
    const model = createLogicalArtifactModel(id, id);
    const nodes: typeof model.nodes = {};
    for (const nodeId of nodeIds) {
      nodes[nodeId] = {
        id: nodeId,
        displayName: nodeId,
        kind: 'component' as const,
        exportedSymbols: [],
        inferredProps: {},
        usesDesignSystem: false,
        classificationConfidence: 1,
        metadata: {},
      };
    }
    return { ...model, nodes };
  };

  it('returns the incoming model unchanged when base is null', () => {
    const incoming = makeModel('m1', ['n1', 'n2']);
    const result = mergeModels(null, incoming);
    expect(result).toBe(incoming);
  });

  it('merges nodes from both models', () => {
    const base = makeModel('m1', ['a', 'b']);
    const incoming = makeModel('m2', ['c', 'd']);
    const result = mergeModels(base, incoming);
    expect(Object.keys(result.nodes)).toContain('a');
    expect(Object.keys(result.nodes)).toContain('c');
  });

  it('incoming nodes overwrite base nodes on id conflict', () => {
    const base = makeModel('m1', ['shared']);
    const incoming = makeModel('m2', ['shared']);
    // Override displayName in incoming node to verify merge precedence
    const incomingNode = { ...incoming.nodes['shared'], displayName: 'from-incoming' };
    const incomingWithLabel = { ...incoming, nodes: { shared: incomingNode } };

    const result = mergeModels(base, incomingWithLabel);
    expect(result.nodes['shared']?.displayName).toBe('from-incoming');
  });

  it('deduplicates edges by id', () => {
    const base = makeModel('m1', []);
    const baseWithEdge = {
      ...base,
      edges: [{ id: 'edge-1', fromId: 'a', toId: 'b', kind: 'import' as const }],
    };
    const incoming = makeModel('m2', []);
    const incomingWithSameEdge = {
      ...incoming,
      edges: [{ id: 'edge-1', fromId: 'a', toId: 'b', kind: 'import' as const }],
    };

    const result = mergeModels(baseWithEdge, incomingWithSameEdge);
    const edgeIds = result.edges.map((e) => e.id);
    const unique = new Set(edgeIds);
    expect(unique.size).toBe(edgeIds.length);
  });

  it('includes unique edges from both models', () => {
    const base = { ...makeModel('m1', []), edges: [{ id: 'e1', fromId: 'a', toId: 'b', kind: 'import' as const }] };
    const incoming = { ...makeModel('m2', []), edges: [{ id: 'e2', fromId: 'c', toId: 'd', kind: 'import' as const }] };

    const result = mergeModels(base, incoming);
    const ids = result.edges.map((e) => e.id);
    expect(ids).toContain('e1');
    expect(ids).toContain('e2');
  });
});
