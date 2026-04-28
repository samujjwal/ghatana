/**
 * RiskApiClient.processDecision — C-Y6 idempotency test
 *
 * Verifies that when the server returns 409 (approval already decided),
 * the client silently falls back to GET /decisions/queue/:id and
 * returns the existing item rather than throwing.
 */

import { describe, expect, it, vi, beforeEach } from 'vitest';
import { RiskApiClient } from '../RiskApiClient';

// ── Helpers ────────────────────────────────────────────────────────────────────

function makeApprovedItem(id: string) {
  return {
    id,
    status: 'approved' as const,
    type: 'approval' as const,
    decision: 'approve',
  };
}

function makeClient() {
  return new RiskApiClient({ mode: 'live', baseUrl: 'http://test', tenantId: 'tenant-1' });
}

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('RiskApiClient.processDecision — C-Y6 idempotency', () => {
  let client: RiskApiClient;

  beforeEach(() => {
    client = makeClient();
  });

  it('returns the current item when POST returns 409 (already decided)', async () => {
    const existingItem = makeApprovedItem('item-99');
    const conflictError = Object.assign(new Error('Conflict'), {
      response: { status: 409 },
    });

    const postSpy = vi
      .spyOn(client as unknown as { post: (...args: unknown[]) => unknown }, 'post')
      .mockRejectedValue(conflictError);

    const getSpy = vi
      .spyOn(client as unknown as { get: (...args: unknown[]) => unknown }, 'get')
      .mockResolvedValue({ success: true, data: existingItem });

    const result = await client.processDecision('item-99', { decision: 'approve', reason: 'retry' });

    expect(postSpy).toHaveBeenCalledWith('/decisions/queue/item-99/process', { decision: 'approve', reason: 'retry' });
    expect(getSpy).toHaveBeenCalledWith('/decisions/queue/item-99');
    expect(result).toEqual({ success: true, data: existingItem });
  });

  it('re-throws non-409 errors', async () => {
    const serverError = Object.assign(new Error('Internal Server Error'), {
      response: { status: 500 },
    });

    vi.spyOn(client as unknown as { post: (...args: unknown[]) => unknown }, 'post')
      .mockRejectedValue(serverError);

    await expect(
      client.processDecision('item-99', { decision: 'approve' })
    ).rejects.toThrow('Internal Server Error');
  });
});
