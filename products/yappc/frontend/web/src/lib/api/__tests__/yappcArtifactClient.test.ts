import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { yappcArtifactClient } from '../yappcArtifactClient';

const previewContext = {
  tenantId: 'tenant-1',
  workspaceId: 'workspace-1',
  projectId: 'project-1',
  artifactId: 'artifact-1',
  userId: 'user-1',
} as const;

describe('yappcArtifactClient previewSessions', () => {
  beforeEach((): void => {
    window.localStorage.clear();
  });

  afterEach((): void => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it('sends bearer auth and scope headers when issuing preview sessions', async (): Promise<void> => {
    window.localStorage.setItem('auth_token', 'token-1');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          sessionId: 'session-1',
          sessionToken: 'session-token-1',
          expiresAt: '2026-05-28T12:00:00Z',
          previewUrl: '/preview/session-1',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );

    await yappcArtifactClient.previewSessions.issue(previewContext);

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/preview/session/create',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer token-1',
          'X-Tenant-ID': 'tenant-1',
          'X-Workspace-ID': 'workspace-1',
          'X-Project-ID': 'project-1',
        }),
      }),
    );
  });

  it('sends bearer auth when validating preview sessions', async (): Promise<void> => {
    window.localStorage.setItem('auth_token', 'token-2');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ valid: true, sessionId: 'session-2' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await yappcArtifactClient.previewSessions.validate('session-token-2');

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/preview/session/validate',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer token-2',
        }),
      }),
    );
  });
});
