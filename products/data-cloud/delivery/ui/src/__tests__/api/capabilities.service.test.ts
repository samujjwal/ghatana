import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';

const { apiClientGet } = vi.hoisted(() => ({
  apiClientGet: vi.fn(),
}));

vi.mock('../../lib/api/client', () => ({
  apiClient: {
    get: apiClientGet,
  },
}));

import { fetchCapabilityRegistry, getCapabilitySignal } from '../../api/capabilities.service';

describe('capabilities.service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('normalizes capability registry statuses and reasons', async () => {
    apiClientGet.mockResolvedValue({
      data: {
        capabilities: {
          analytics: 'ACTIVE',
          trino: 'NOT_CONFIGURED',
          ai_assist: {
            status: 'DEGRADED',
            reason: 'OpenAI API key is not configured for this tenant.',
          },
        },
        generatedAt: '2026-04-17T10:00:00Z',
      },
      meta: {
        requestId: 'req-capabilities',
        tenantId: TEST_TENANT_ID,
        timestamp: '2026-04-17T10:00:00Z',
        apiVersion: 'v1',
      },
    });

    const snapshot = await fetchCapabilityRegistry();

    expect(snapshot.requestId).toBe('req-capabilities');
    expect(snapshot.capabilities).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ key: 'analytics', status: 'active', summary: 'ACTIVE' }),
        expect.objectContaining({ key: 'trino', status: 'unavailable', summary: 'NOT_CONFIGURED' }),
        expect.objectContaining({
          key: 'ai_assist',
          status: 'degraded',
          summary: 'DEGRADED',
          detail: 'OpenAI API key is not configured for this tenant.',
        }),
      ]),
    );
  });

  it('finds a capability by alias', () => {
    const capability = getCapabilitySignal(
      [
        {
          key: 'ai_assist',
          label: 'Ai Assist',
          status: 'degraded',
          summary: 'DEGRADED',
          detail: 'Reason',
          rawValue: 'DEGRADED',
        },
      ],
      ['assist', 'ai_assist'],
    );

    expect(capability?.key).toBe('ai_assist');
  });
});