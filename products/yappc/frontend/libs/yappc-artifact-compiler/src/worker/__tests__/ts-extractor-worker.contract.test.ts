import { describe, expect, it } from 'vitest';
import type { RepositorySnapshot } from '../../source-providers/types';
import type { SynthesisPipelineResult } from '../../synthesis/pipeline';
import {
  normalizeExtractorWorkerRequest,
  serializeExtractionWorkerResponse,
} from '../ts-extractor-worker';

function createCanonicalSnapshot(rootPath: string): RepositorySnapshot {
  return {
    snapshotRef: {
      provider: 'local-folder',
      repoId: rootPath,
    },
    localRootPath: rootPath,
    files: [
      {
        relativePath: 'src/App.tsx',
        absolutePath: `${rootPath}/src/App.tsx`,
        materialized: true,
        sizeBytes: 128,
        lastModifiedAt: '2026-05-16T00:00:00.000Z',
      },
    ],
    snapshotAt: '2026-05-16T00:00:00.000Z',
    shallow: false,
    diagnostics: [],
  };
}

function createPipelineResult(): SynthesisPipelineResult {
  return {
    snapshot: null,
    graph: {
      id: 'graph-1',
      repositoryRoot: '/tmp/repo',
      createdAt: '2026-05-16T00:00:00.000Z',
      updatedAt: '2026-05-16T00:00:00.000Z',
      version: 1,
      snapshotRef: {
        provider: 'local-folder',
        repoId: '/tmp/repo',
      },
      nodes: [
        {
          id: 'node-1',
          kind: 'component',
          label: 'App',
          sourceRef: 'src/App.tsx#component:App',
          symbolRef: 'src/App.tsx#component:App',
          sourceLocation: {
            filePath: 'src/App.tsx',
            startLine: 1,
            startColumn: 0,
            endLine: 5,
            endColumn: 1,
          },
          extractorId: 'react-component',
          extractorVersion: '1.0.0',
          confidence: 0.95,
          provenance: 'exact',
          privacySecurityFlags: [],
          residualFragmentIds: [],
          metadata: {
            framework: 'react',
          },
        },
      ],
      edges: [
        {
          id: 'edge-1',
          sourceId: 'node-1',
          targetId: 'node-2',
          kind: 'imports',
          confidence: 0.9,
          bidirectional: false,
          metadata: {
            importPath: './Button',
          },
        },
      ],
      unresolvedEdges: [
        {
          sourceId: 'node-1',
          targetRef: './Button',
          relationship: 'imports',
          targetKindHint: 'component',
          sourceLocation: {
            filePath: 'src/App.tsx',
            startLine: 1,
            startColumn: 0,
            endLine: 1,
            endColumn: 20,
          },
          confidence: 0.8,
          metadata: {},
        },
      ],
      edgeResolutionRecords: [
        {
          unresolvedEdge: {
            sourceId: 'node-1',
            targetRef: './Button',
            relationship: 'imports',
            targetKindHint: 'component',
            sourceLocation: {
              filePath: 'src/App.tsx',
              startLine: 1,
              startColumn: 0,
              endLine: 1,
              endColumn: 20,
            },
            confidence: 0.8,
            metadata: {},
          },
          status: 'resolved',
          resolvedTargetId: 'node-2',
          candidateIds: ['node-2'],
          reviewRequired: false,
        },
      ],
      nodeIndex: {
        component: ['node-1'],
      },
      edgeIndex: {
        imports: ['edge-1'],
      },
    },
    model: {
      id: 'model-1',
      repositoryRoot: '/tmp/repo',
      createdAt: '2026-05-16T00:00:00.000Z',
      updatedAt: '2026-05-16T00:00:00.000Z',
      version: 1,
      elements: [],
      elementIndex: {},
      residualIslandIds: ['residual-1'],
    },
    residualIslands: [
      {
        id: 'residual-1',
        kind: 'code',
        originalSource: 'const unresolved = true;',
        normalizedSummary: 'Residual fragment',
        reasonUnmodeled: 'unsupported extractor',
        reviewRequired: true,
        reviewReason: 'manual follow-up',
        regenerationStrategy: 'verbatim-preserve',
        sourceLocation: {
          filePath: 'src/App.tsx',
          startLine: 1,
          startColumn: 0,
          endLine: 1,
          endColumn: 10,
        },
        extractorId: 'react-component',
        extractorVersion: '1.0.0',
        extractedAt: '2026-05-16T00:00:00.000Z',
        confidence: 0.2,
        linkedModelElementIds: [],
        tags: [],
        rawFragmentRef: 'src/App.tsx#fragment-1',
        checksum: 'a'.repeat(64),
        risk: 'medium',
        relatedGraphNodeIds: ['node-1'],
      },
    ],
    extractionResults: [],
    errors: [
      {
        phase: 'extract',
        message: 'extractor failed for one artifact',
        artifactPath: 'src/Broken.tsx',
        recoverable: true,
      },
    ],
    warnings: [
      {
        phase: 'resolve',
        message: 'one edge remained unresolved',
        artifactPath: 'src/App.tsx',
      },
    ],
    durationMs: 10,
    stats: {
      scannedFiles: 1,
      eligibleArtifacts: 1,
      extractedNodes: 1,
      resolvedEdges: 1,
      unresolvedEdges: 1,
      ambiguousEdges: 0,
      crossRepoEdges: 0,
      modelElementsGenerated: 0,
      residualIslandsGenerated: 1,
    },
  };
}

describe('ts-extractor-worker contract', () => {
  it('normalizes canonical snapshot requests without drift', () => {
    const snapshot = createCanonicalSnapshot('/tmp/repo');

    const normalized = normalizeExtractorWorkerRequest({ snapshot });

    expect(normalized).toEqual(snapshot);
  });

  it('rejects non-canonical worker request shapes', () => {
    expect(() =>
      normalizeExtractorWorkerRequest({
        snapshotId: 'snapshot-1',
        provider: 'local-folder',
        repoId: '/tmp/repo',
      }),
    ).toThrow();
  });

  it('serializes pipeline results into Java-consumable DTO fields', () => {
    const response = serializeExtractionWorkerResponse(createPipelineResult(), 120000);

    expect(response.nodes[0]).toMatchObject({
      id: 'node-1',
      type: 'component',
      name: 'App',
      filePath: 'src/App.tsx',
      extractorId: 'react-component',
      extractorVersion: '1.0.0',
      confidence: 0.95,
      provenance: 'exact',
      sourceRef: 'src/App.tsx#component:App',
    });
    expect(response.edges[0]).toMatchObject({
      edgeId: 'edge-1',
      sourceNodeId: 'node-1',
      targetNodeId: 'node-2',
      relationshipType: 'imports',
    });
    expect(response.unresolvedEdges[0]?.id).toBeTruthy();
    expect(response.edgeResolutionRecords[0]?.unresolvedEdgeId).toBe(response.unresolvedEdges[0]?.id);
    // P0: residualIslands is now a full payload array, not IDs only
    expect(response.residualIslands).toHaveLength(1);
    expect(response.residualIslands[0]?.id).toBe('residual-1');
    expect(response.residualIslands[0]?.islandType).toBeTruthy();
    expect(response.residualIslands[0]?.sourceSpan).toBeTruthy();
    expect(typeof response.residualIslands[0]?.confidence).toBe('number');
    expect(typeof response.residualIslands[0]?.reviewRequired).toBe('boolean');
    expect(response.semanticModels[0]).toMatchObject({
      elementId: 'node-1',
      elementType: 'component',
      sourceRef: 'src/App.tsx#component:App',
      extractorId: 'react-component',
      extractorVersion: '1.0.0',
    });
    expect(response.diagnostics).toEqual([
      {
        level: 'WARNING',
        code: 'PIPELINE_RESOLVE_WARNING',
        message: 'one edge remained unresolved',
        filePath: 'src/App.tsx',
        line: 0,
        column: 0,
      },
      {
        level: 'ERROR',
        code: 'PIPELINE_EXTRACT_ERROR',
        message: 'extractor failed for one artifact',
        filePath: 'src/Broken.tsx',
        line: 0,
        column: 0,
      },
    ]);
    expect(response.versionMetadata.timeoutMs).toBe(120000);
  });
});