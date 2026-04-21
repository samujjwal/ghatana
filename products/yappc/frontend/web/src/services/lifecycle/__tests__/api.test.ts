import { afterEach, describe, expect, it, vi } from 'vitest';

import { lifecycleAPI } from '../api';
import { FOWStage } from '@/types/fow-stages';

describe('lifecycleAPI.gates.transitionStage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('posts the current and target stages using the mounted API contract', async () => {
    const response = {
      success: true,
      currentStage: FOWStage.BUILD_INTEGRATE,
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => response,
      text: async () => JSON.stringify(response),
    } as Response);

    await expect(
      lifecycleAPI.gates.transitionStage(
        'project-1',
        FOWStage.DELIVERY_PLANNING,
        FOWStage.BUILD_INTEGRATE
      )
    ).resolves.toEqual(response);

    expect(fetchSpy).toHaveBeenCalledWith(
      expect.stringContaining('/api/projects/project-1/stages/transition'),
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          'Content-Type': 'application/json',
        }),
        body: JSON.stringify({
          fromStage: FOWStage.DELIVERY_PLANNING,
          toStage: FOWStage.BUILD_INTEGRATE,
        }),
      })
    );
  });

  it('surfaces backend validation failures with the API error message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 422,
      json: async () => ({ message: 'Complete required artifacts before advancing' }),
      text: async () => JSON.stringify({ message: 'Complete required artifacts before advancing' }),
    } as Response);

    await expect(
      lifecycleAPI.gates.transitionStage(
        'project-1',
        FOWStage.DELIVERY_PLANNING,
        FOWStage.BUILD_INTEGRATE
      )
    ).rejects.toMatchObject({
      message: 'Complete required artifacts before advancing',
      status: 422,
    });
  });
});