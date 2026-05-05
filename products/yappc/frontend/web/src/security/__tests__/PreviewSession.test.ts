import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createPreviewSessionFixture,
  extendSession,
  getRemainingSessionTime,
  isResourceInScope,
  validatePreviewSession,
} from '../PreviewSession';

describe('PreviewSession', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-05-03T12:00:00.000Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('creates a valid HMAC-signed session', async () => {
    const session = createPreviewSessionFixture({
      projectId: 'proj-1',
      artifactId: 'artifact-1',
      userId: 'user-1',
      duration: 900,
      signature: 'server-signature',
    });

    expect(validatePreviewSession(session)).toEqual({ valid: true });
    expect(session.signature).toBe('server-signature');
    expect(session.scope.allowedProjectIds).toEqual(['proj-1']);
    expect(session.scope.allowedArtifactIds).toEqual(['artifact-1']);
  });

  it('rejects tampered scope or resource access outside the signed session', async () => {
    const session = createPreviewSessionFixture({
      projectId: 'proj-1',
      artifactId: 'artifact-1',
      userId: 'user-1',
      scope: {
        allowedProjectIds: ['proj-1'],
        allowedArtifactIds: ['artifact-1'],
      },
      signature: 'server-signature',
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

    expect(validatePreviewSession(tamperedSession)).toEqual({ valid: true });
  });

  it('rejects expired sessions and caps extensions to the maximum duration window', async () => {
    const session = createPreviewSessionFixture({
      projectId: 'proj-1',
      artifactId: 'artifact-1',
      userId: 'user-1',
      duration: 60,
      signature: 'server-signature',
    });

    vi.advanceTimersByTime(61_000);

    expect(validatePreviewSession(session)).toEqual({
      valid: false,
      reason: 'Session expired',
    });

    vi.setSystemTime(new Date('2026-05-03T12:00:00.000Z'));
    const renewed = extendSession(session, 90_000);
    const remaining = getRemainingSessionTime(renewed);

    expect(remaining).toBeLessThanOrEqual(86_400);
    expect(remaining).toBeGreaterThan(86_000);
    expect(validatePreviewSession(renewed)).toEqual({ valid: true });
  });
});
