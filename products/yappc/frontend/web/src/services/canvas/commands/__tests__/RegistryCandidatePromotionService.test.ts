import { afterEach, describe, expect, it, vi } from 'vitest';

import { promoteResidualIslandToRegistryCandidate } from '../RegistryCandidatePromotionService';

describe('promoteResidualIslandToRegistryCandidate', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts registry candidate promotions with cookie credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          candidateId: 'candidate-1',
          artifactId: 'artifact-1',
          residualIslandId: 'legacy-chart',
          proposedContractName: 'LegacyChartCandidate',
          status: 'NEEDS_REVIEW',
          auditRecordId: 'audit-candidate-1',
          createdAt: '2026-05-07T00:00:00.000Z',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    await expect(
      promoteResidualIslandToRegistryCandidate({
        artifactId: 'artifact-1',
        residualIslandId: 'legacy-chart',
        proposedContractName: 'LegacyChartCandidate',
        source: 'decompiled-import',
        notes: 'Promote decompiled island for registry review.',
      }),
    ).resolves.toEqual({
      candidateId: 'candidate-1',
      artifactId: 'artifact-1',
      residualIslandId: 'legacy-chart',
      proposedContractName: 'LegacyChartCandidate',
      status: 'NEEDS_REVIEW',
      auditRecordId: 'audit-candidate-1',
      createdAt: '2026-05-07T00:00:00.000Z',
    });

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/yappc/artifacts/artifact-1/residual-islands/legacy-chart/registry-candidates',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
        body: JSON.stringify({
          proposedContractName: 'LegacyChartCandidate',
          source: 'decompiled-import',
          notes: 'Promote decompiled island for registry review.',
        }),
      }),
    );
  });

  it('rejects unexpected response shapes so promoted residuals are not silently cleared', async () => {
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
      promoteResidualIslandToRegistryCandidate({
        artifactId: 'artifact-1',
        residualIslandId: 'legacy-chart',
        proposedContractName: 'LegacyChartCandidate',
        source: 'decompiled-import',
      }),
    ).rejects.toThrow('unexpected shape');
  });
});
