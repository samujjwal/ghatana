import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  buildArtifactGraphMergeReviewRequest,
  runArtifactGraphMergeReview,
} from '../ArtifactGraphMergeReviewService';
import type { PageArtifactGraphSnapshot } from '../../../../components/canvas/page/pageArtifactDocument';

const graphSnapshot: PageArtifactGraphSnapshot = {
  graphId: 'commerce-app:graph',
  projectId: 'commerce-app',
  sourceType: 'semantic-model',
  source: 'commerce-app',
  importedAt: '2026-05-07T00:00:00.000Z',
  nodes: [
    { id: 'commerce-app:product', kind: 'product', label: 'Commerce App' },
    { id: 'home-page:page', kind: 'page', label: 'Home' },
    { id: 'home-page:residual:legacy-chart', kind: 'residual', label: 'legacy-chart' },
  ],
  edges: [
    {
      id: 'home-page:page-part-of-product',
      from: 'home-page:page',
      to: 'commerce-app:product',
      kind: 'part-of',
    },
  ],
  provenance: {
    createdBy: 'tester',
    compiler: 'yappc-artifact-compiler',
    confidence: 0.84,
    residualIslandIds: ['legacy-chart'],
  },
};

describe('ArtifactGraphMergeReviewService', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('builds graph-wide three-way merge requests from page artifact graph snapshots', () => {
    const request = buildArtifactGraphMergeReviewRequest(graphSnapshot, 'tenant-1');

    expect(request).toMatchObject({
      productId: 'commerce-app:graph',
      tenantId: 'tenant-1',
      resolutionStrategy: 'merge',
      baseModel: {
        graphId: 'commerce-app:graph',
        residualIslandIds: [],
      },
      leftModel: {
        graphId: 'commerce-app:graph',
        residualIslandIds: [],
      },
    });
    expect(request.rightModel).toMatchObject({
      graphId: 'commerce-app:graph',
      nodeIds: ['commerce-app:product', 'home-page:page', 'home-page:residual:legacy-chart'],
      edgeIds: ['home-page:page-part-of-product'],
      residualIslandIds: ['legacy-chart'],
      confidence: 0.84,
    });
  });

  it('posts merge review requests with cookie credentials and validates response shape', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          operation: 'merge',
          result: {
            mergedModel: { nodeIds: ['home-page:page'] },
            conflicts: [],
            fieldProvenance: { nodeIds: 'right' },
            conflictCount: 0,
          },
          message: 'Three-way merge completed with 0 conflicts',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(
      runArtifactGraphMergeReview(buildArtifactGraphMergeReviewRequest(graphSnapshot, 'tenant-1')),
    ).resolves.toMatchObject({
      success: true,
      operation: 'merge',
      conflictCount: 0,
      mergedModel: { nodeIds: ['home-page:page'] },
    });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/yappc/artifact/graph/merge',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
      }),
    );
  });

  it('rejects unexpected merge responses so graph review cannot silently pass', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ success: true, operation: 'merge', result: {} }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    await expect(
      runArtifactGraphMergeReview(buildArtifactGraphMergeReviewRequest(graphSnapshot, 'tenant-1')),
    ).rejects.toThrow('unexpected shape');
  });
});
