import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { releaseReadinessService, type ReleaseReadiness } from '../../api/release-readiness.service';

const readiness: ReleaseReadiness = {
  id: 'phr-1-0-0-production',
  productId: 'phr',
  productVersion: '1.0.0',
  releaseTarget: 'production',
  releaseVerdict: 'pass',
  averageScore: 0.98,
  releaseTargetScore: 0.96,
  generatedAt: '2026-05-25T12:00:00Z',
  evidence: { source: 'runtime-truth' },
  blockingGaps: [],
  belowTargetDimensions: [],
  tenantId: 'tenant-123',
  commitSha: 'bdcee47c1e304454e7af848be60d981b24da1151',
  evidenceEnvironment: 'production',
  createdAt: '2026-05-25T12:00:00Z',
  updatedAt: '2026-05-25T12:00:00Z',
};

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 404 ? 'Not Found' : 'OK',
    json: async () => body,
  } as Response;
}

describe('releaseReadinessService', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts runtime-bound release readiness evidence', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response(readiness));
    const { id: _id, createdAt: _createdAt, updatedAt: _updatedAt, ...payload } = readiness;

    const result = await releaseReadinessService.produceReleaseReadiness(payload);

    expect(result.commitSha).toBe('bdcee47c1e304454e7af848be60d981b24da1151');
    expect(fetch).toHaveBeenCalledWith('/api/v1/release-readiness', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: expect.stringContaining('"evidenceEnvironment":"production"'),
    });
  });

  it('lists cockpit records with tenant, product, target, and verdict filters', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response([readiness]));

    const result = await releaseReadinessService.listReleaseReadiness({
      tenantId: 'tenant-123',
      productId: 'phr',
      productVersion: '1.0.0',
      releaseTarget: 'production',
      releaseVerdict: 'pass',
      limit: 25,
      offset: 0,
    });

    expect(result).toHaveLength(1);
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/release-readiness?productId=phr&productVersion=1.0.0&releaseTarget=production&releaseVerdict=pass&tenantId=tenant-123&limit=25'
    );
  });

  it('returns null for missing product readiness record', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response({ message: 'missing' }, 404));

    await expect(
      releaseReadinessService.getReleaseReadiness('phr', '1.0.0', 'production', 'tenant-123')
    ).resolves.toBeNull();
  });

  it('fetches release readiness stats for the tenant cockpit', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(response({
      totalReleases: 1,
      passedReleases: 1,
      failedReleases: 0,
      averageScore: 0.98,
      byProduct: { phr: { total: 1, passed: 1, failed: 0 } },
      byTarget: { production: { total: 1, passed: 1, failed: 0 } },
    }));

    const result = await releaseReadinessService.getReleaseReadinessStats('tenant-123');

    expect(result.passedReleases).toBe(1);
    expect(fetch).toHaveBeenCalledWith('/api/v1/release-readiness/stats?tenantId=tenant-123');
  });
});
