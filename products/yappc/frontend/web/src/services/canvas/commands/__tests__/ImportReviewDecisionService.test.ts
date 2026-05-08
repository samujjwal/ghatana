import { afterEach, describe, expect, it, vi } from 'vitest';

import { persistImportReviewDecision } from '../ImportReviewDecisionService';

describe('persistImportReviewDecision', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts import review decisions with cookie credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          artifactId: 'artifact-1',
          reviewItemId: 'loss-0-style-header',
          kind: 'loss-point',
          decision: 'skipped',
          auditRecordId: 'audit-import-review-1',
          auditRecorded: true,
          reviewedAt: '2026-05-07T00:00:00.000Z',
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(
      persistImportReviewDecision({
        artifactId: 'artifact-1',
        reviewItemId: 'loss-0-style-header',
        kind: 'loss-point',
        decision: 'skipped',
        label: 'style at Header.tsx',
        details: 'CSS module could not be decompiled.',
        notes: 'Reviewed by operator.',
      }),
    ).resolves.toEqual({
      artifactId: 'artifact-1',
      reviewItemId: 'loss-0-style-header',
      kind: 'loss-point',
      decision: 'skipped',
      auditRecordId: 'audit-import-review-1',
      auditRecorded: true,
      reviewedAt: '2026-05-07T00:00:00.000Z',
    });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/yappc/artifacts/artifact-1/import-review-decisions',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({
          reviewItemId: 'loss-0-style-header',
          kind: 'loss-point',
          decision: 'skipped',
          label: 'style at Header.tsx',
          details: 'CSS module could not be decompiled.',
          notes: 'Reviewed by operator.',
        }),
      }),
    );
  });

  it('rejects unexpected response shapes so review decisions are not silently marked decided', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ ok: true }), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );

    await expect(
      persistImportReviewDecision({
        artifactId: 'artifact-1',
        reviewItemId: 'loss-0-style-header',
        kind: 'loss-point',
        decision: 'applied',
      }),
    ).rejects.toThrow('unexpected shape');
  });
});
