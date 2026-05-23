/**
 * Release gate evidence API tests.
 *
 * @doc.type test
 * @doc.purpose Verify admin release-gate evidence loads from API and CI artifact fallback
 * @doc.layer product
 */

import { describe, expect, it, vi } from 'vitest';
import { loadReleaseGateEvidence } from '../releaseGateEvidenceApi';

function jsonResponse(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('releaseGateEvidenceApi', () => {
  it('loads release gate evidence from the live admin API when available', async () => {
    const fetchImpl = vi.fn<typeof fetch>();
    fetchImpl.mockResolvedValueOnce(jsonResponse({
      items: [
        {
          id: 'product-slo-budgets',
          label: 'Product SLO budgets',
          status: 'passed',
          evidenceHref: '/evidence/slo.json',
          generatedAt: '2026-05-23T01:00:00.000Z',
          summary: 'SLO budgets passed',
        },
        {
          id: 'openapi-breaking-changes',
          label: 'OpenAPI breaking changes',
          passed: false,
          evidenceHref: '/evidence/openapi.json',
          generatedAt: '2026-05-23T01:01:00.000Z',
          summary: 'Breaking change review required',
        },
      ],
    }));

    const records = await loadReleaseGateEvidence(fetchImpl);

    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(fetchImpl).toHaveBeenCalledWith('/api/admin/observability/release-gates', {
      headers: { Accept: 'application/json' },
    });
    expect(records).toEqual([
      {
        id: 'product-slo-budgets',
        label: 'Product SLO budgets',
        status: 'healthy',
        evidenceHref: '/evidence/slo.json',
        refreshedAt: '2026-05-23T01:00:00.000Z',
        summary: 'SLO budgets passed',
      },
      {
        id: 'openapi-breaking-changes',
        label: 'OpenAPI breaking changes',
        status: 'down',
        evidenceHref: '/evidence/openapi.json',
        refreshedAt: '2026-05-23T01:01:00.000Z',
        summary: 'Breaking change review required',
      },
    ]);
  });

  it('falls back to CI evidence artifacts when the live admin API is not deployed', async () => {
    const fetchImpl = vi.fn<typeof fetch>();
    fetchImpl
      .mockResolvedValueOnce(jsonResponse({ message: 'not found' }, 404))
      .mockResolvedValueOnce(jsonResponse({ status: 'healthy', summary: 'SLO evidence ready', timestamp: '2026-05-23T02:00:00.000Z' }))
      .mockResolvedValueOnce(jsonResponse({ passed: true, summary: 'Cost evidence ready', timestamp: '2026-05-23T02:01:00.000Z' }))
      .mockResolvedValueOnce(jsonResponse({ warnings: ['domain warning'], summary: 'Domain invariant warning', timestamp: '2026-05-23T02:02:00.000Z' }))
      .mockResolvedValueOnce(jsonResponse({ message: 'missing artifact' }, 503));

    const records = await loadReleaseGateEvidence(fetchImpl);

    expect(fetchImpl).toHaveBeenCalledTimes(5);
    expect(records).toHaveLength(4);
    expect(records[0]).toMatchObject({
      id: 'product-slo-budgets',
      status: 'healthy',
      summary: 'SLO evidence ready',
    });
    expect(records[2]).toMatchObject({
      id: 'product-domain-invariants',
      status: 'degraded',
      summary: 'Domain invariant warning',
    });
    expect(records[3]).toMatchObject({
      id: 'openapi-breaking-changes',
      status: 'down',
      summary: 'OpenAPI breaking changes evidence is unavailable: missing artifact',
    });
  });

  it('surfaces live API errors instead of silently replacing them with stale artifacts', async () => {
    const fetchImpl = vi.fn<typeof fetch>();
    fetchImpl.mockResolvedValueOnce(jsonResponse({ message: 'database unavailable' }, 500));

    await expect(loadReleaseGateEvidence(fetchImpl)).rejects.toThrow(
      'Failed to load release gate evidence: database unavailable'
    );
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });
});
