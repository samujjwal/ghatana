import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { getOnboardingStatus, setOnboardingStatus } from '../OnboardingStatusService';

describe('OnboardingStatusService', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  afterEach(() => {
    localStorage.clear();
  });

  describe('getOnboardingStatus', () => {
    it('returns localStorage status when server returns 404', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        status: 404,
        ok: false,
      }));

      localStorage.setItem('onboarding_complete', JSON.stringify('true'));
      localStorage.setItem('yappc_primary_persona', JSON.stringify('developer'));
      localStorage.setItem('yappc_active_personas', JSON.stringify(['developer', 'designer']));

      const status = await getOnboardingStatus();

      expect(status.completed).toBe(true);
      expect(status.primaryPersona).toBe('developer');
      expect(status.activePersonas).toEqual(['developer', 'designer']);
    });

    it('returns localStorage defaults when server is unavailable and localStorage is empty', async () => {
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')));

      const status = await getOnboardingStatus();

      expect(status.completed).toBe(false);
      expect(status.primaryPersona).toBeUndefined();
      expect(status.activePersonas).toBeUndefined();
    });
  });

  describe('setOnboardingStatus', () => {
    it('writes to localStorage even when server is unavailable', async () => {
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')));

      const result = await setOnboardingStatus({
        completed: true,
        primaryPersona: 'designer',
        activePersonas: ['designer', 'devops'],
      });

      expect(result.completed).toBe(true);
      expect(result.primaryPersona).toBe('designer');
      expect(localStorage.getItem('onboarding_complete')).toBe(JSON.stringify('true'));
      expect(localStorage.getItem('yappc_primary_persona')).toBe(JSON.stringify('designer'));
      expect(localStorage.getItem('yappc_active_personas')).toBe(JSON.stringify(['designer', 'devops']));
    });

    it('handles server 501 gracefully and falls back to localStorage', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        status: 501,
        ok: false,
      }));

      const result = await setOnboardingStatus({
        completed: true,
        primaryPersona: 'product-owner',
      });

      expect(result.completed).toBe(true);
      expect(result.primaryPersona).toBe('product-owner');
      expect(localStorage.getItem('onboarding_complete')).toBe(JSON.stringify('true'));
    });
  });
});
