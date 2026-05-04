import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createPreviewSession,
  extendSession,
  getRemainingSessionTime,
  isResourceInScope,
  validatePreviewSession,
} from '../PreviewSession';

describe('PreviewSession', () => {
  const secretKey = 'preview-secret-key';

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-05-03T12:00:00.000Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('creates a valid HMAC-signed session', async () => {
    const session = await createPreviewSession({
      projectId: 'proj-1',
      artifactId: 'artifact-1',
      userId: 'user-1',
      secretKey,
      duration: 900,
    });

    await expect(validatePreviewSession(session, secretKey)).resolves.toEqual({ valid: true });
    expect(session.signature.startsWith('hmac_')).toBe(true);
    expect(session.scope.allowedProjectIds).toEqual(['proj-1']);
    expect(session.scope.allowedArtifactIds).toEqual(['artifact-1']);
  });

  it('rejects tampered scope or resource access outside the signed session', async () => {
    const session = await createPreviewSession({
      projectId: 'proj-1',
      artifactId: 'artifact-1',
      userId: 'user-1',
      secretKey,
      scope: {
        allowedProjectIds: ['proj-1'],
        allowedArtifactIds: ['artifact-1'],
      },
    });

    expect(isResourceInScope(session, 'proj-1', 'artifact-1')).toBe(true);
    expect(isResourceInScope(session, 'proj-2', 'artifact-1')).toBe(false);
    expect(isResourceInScope(session, 'proj-1', 'artifact-2')).toBe(false);

    const tamperedSession = {
      ...session,
      scope: {
        ...session.scope,
        allowDownload: true,
      },
    };

    await expect(validatePreviewSession(tamperedSession, secretKey)).resolves.toEqual({
      valid: false,
      reason: 'Invalid signature',
    });
  });

  it('rejects expired sessions and caps extensions to the maximum duration window', async () => {
    const session = await createPreviewSession({
      projectId: 'proj-1',
      artifactId: 'artifact-1',
      userId: 'user-1',
      secretKey,
      duration: 60,
    });

    vi.advanceTimersByTime(61_000);

    await expect(validatePreviewSession(session, secretKey)).resolves.toEqual({
      valid: false,
      reason: 'Session expired',
    });

    vi.setSystemTime(new Date('2026-05-03T12:00:00.000Z'));
    const renewed = await extendSession(session, 90_000, secretKey);
    const remaining = getRemainingSessionTime(renewed);

    expect(remaining).toBeLessThanOrEqual(86_400);
    expect(remaining).toBeGreaterThan(86_000);
    await expect(validatePreviewSession(renewed, secretKey)).resolves.toEqual({ valid: true });
  });
});
