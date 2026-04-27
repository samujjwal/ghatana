/**
 * AepOrchestrationClient Tests
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { submitRequirementApproved } from '../AepOrchestrationClient';

describe('AepOrchestrationClient', () => {
  const validRunRef = {
    runId: 'run-123',
    agentId: 'requirement-orchestration-agent',
    status: 'QUEUED',
    createdAt: '2026-01-01T00:00:00.000Z',
  };

  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => validRunRef,
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('calls the AEP canonical agent execute endpoint', async () => {
    await submitRequirementApproved({
      projectId: 'proj-1',
      requirementId: 'req-1',
      approvalId: 'appr-1',
    });

    const fetchMock = vi.mocked(fetch);
    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(
      '/api/v1/agents/requirement-orchestration-agent/execute',
    );
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body as string)).toMatchObject({
      type: 'requirement.approved',
      projectId: 'proj-1',
      requirementId: 'req-1',
      approvalId: 'appr-1',
    });
  });

  it('returns the run reference from the response', async () => {
    const result = await submitRequirementApproved({
      projectId: 'proj-1',
      requirementId: 'req-1',
      approvalId: 'appr-1',
    });

    expect(result.runId).toBe('run-123');
    expect(result.agentId).toBe('requirement-orchestration-agent');
    expect(result.status).toBe('QUEUED');
  });

  it('throws when the API responds with a non-ok status', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 503,
        statusText: 'Service Unavailable',
        text: async () => 'downstream error',
      }),
    );

    await expect(
      submitRequirementApproved({
        projectId: 'proj-1',
        requirementId: 'req-1',
        approvalId: 'appr-1',
      }),
    ).rejects.toThrow('AEP orchestration request failed [503]');
  });

  it('throws when the response body does not match expected shape', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ unexpected: true }),
      }),
    );

    await expect(
      submitRequirementApproved({
        projectId: 'proj-1',
        requirementId: 'req-1',
        approvalId: 'appr-1',
      }),
    ).rejects.toThrow('AEP orchestration response did not match expected shape');
  });

  it('includes a timestamp in the payload', async () => {
    const before = new Date().toISOString();

    await submitRequirementApproved({
      projectId: 'proj-1',
      requirementId: 'req-1',
      approvalId: 'appr-1',
    });

    const after = new Date().toISOString();
    const fetchMock = vi.mocked(fetch);
    const body = JSON.parse(
      (fetchMock.mock.calls[0] as [string, RequestInit])[1].body as string,
    ) as { timestamp: string };
    expect(body.timestamp >= before).toBe(true);
    expect(body.timestamp <= after).toBe(true);
  });
});
