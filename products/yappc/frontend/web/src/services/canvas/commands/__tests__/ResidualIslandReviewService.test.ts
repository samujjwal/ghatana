import { afterEach, describe, expect, it, vi } from 'vitest';

import { persistResidualIslandReview } from '../ResidualIslandReviewService';

describe('persistResidualIslandReview', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts residual island review decisions with cookie credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          artifactId: 'artifact-1',
          residualIslandId: 'legacy-chart',
          decision: 'REJECTED',
          auditRecordId: 'audit-1',
          reviewedAt: '2026-05-07T00:00:00.000Z',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(
      persistResidualIslandReview({
        artifactId: 'artifact-1',
        residualIslandId: 'legacy-chart',
        decision: 'REJECTED',
        notes: 'Needs canonical mapping.',
      }),
    ).resolves.toEqual({
      artifactId: 'artifact-1',
      residualIslandId: 'legacy-chart',
      decision: 'REJECTED',
      auditRecordId: 'audit-1',
      reviewedAt: '2026-05-07T00:00:00.000Z',
    });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/yappc/artifacts/artifact-1/residual-islands/legacy-chart/review',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({
          decision: 'REJECTED',
          notes: 'Needs canonical mapping.',
        }),
      }),
    );
  });

  it('rejects unexpected response shapes so review actions are not silently cleared', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ ok: true }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    await expect(
      persistResidualIslandReview({
        artifactId: 'artifact-1',
        residualIslandId: 'legacy-chart',
        decision: 'ACCEPTED',
      }),
    ).rejects.toThrow('unexpected shape');
  });
});
