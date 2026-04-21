import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  PhaseTransitionApiError,
  phaseTransitionAPI,
  type PhaseTransitionPreview,
} from '../phase-transition-api';
import { LifecyclePhase } from '../../../types/lifecycle';

describe('phaseTransitionAPI', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('requests the next phase preview for a project', async () => {
    const response: PhaseTransitionPreview = {
      projectId: 'project-1',
      currentPhase: LifecyclePhase.EXECUTE,
      nextPhase: LifecyclePhase.VERIFY,
      canAdvance: true,
      readiness: 100,
      blockers: [],
      requiredArtifacts: ['Source Code', 'Documentation', 'Build Artifacts'],
      completedArtifacts: ['Source Code', 'Documentation', 'Build Artifacts'],
      estimatedReadyIn: 'Ready now',
      estimatedReadyInHours: 0,
      predictionConfidence: 0.95,
      checkedAt: '2026-04-06T12:00:00.000Z',
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => response,
      text: async () => JSON.stringify(response),
    } as Response);

    await expect(
      phaseTransitionAPI.getNextPhase(LifecyclePhase.EXECUTE, 'project-1')
    ).resolves.toEqual(response);

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/api/phases/EXECUTE/next?projectId=project-1'),
      expect.objectContaining({
        method: 'GET',
      })
    );
  });

  it('throws a typed error when the request fails', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ error: 'Invalid phase' }),
      text: async () => JSON.stringify({ error: 'Invalid phase' }),
    } as Response);

    await expect(
      phaseTransitionAPI.getNextPhase(LifecyclePhase.INTENT, 'project-2')
    ).rejects.toEqual(new PhaseTransitionApiError('Invalid phase', 400));
  });
});
