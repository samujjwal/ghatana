import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getOnboardingStatus,
  normalizeOnboardingStatus,
  resetOnboardingStatusServerAvailabilityForTest,
  setOnboardingStatus,
} from '../OnboardingStatusService';

describe('OnboardingStatusService', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
    resetOnboardingStatusServerAvailabilityForTest();
  });

  afterEach(() => {
    localStorage.clear();
    vi.unstubAllGlobals();
  });

  describe('contract normalization', () => {
    it('accepts a valid server-backed onboarding status contract', () => {
      expect(
        normalizeOnboardingStatus({
          completed: true,
          completedAt: '2026-05-06T10:00:00.000Z',
          primaryPersona: 'builder',
          activePersonas: ['builder', 42, 'reviewer'],
        })
      ).toEqual({
        completed: true,
        completedAt: '2026-05-06T10:00:00.000Z',
        primaryPersona: 'builder',
        activePersonas: ['builder', 'reviewer'],
      });
    });

    it('rejects missing or malformed completion flags so callers can use the local fallback', () => {
      expect(normalizeOnboardingStatus({ primaryPersona: 'builder' })).toBeNull();
      expect(normalizeOnboardingStatus({ completed: 'true' })).toBeNull();
      expect(normalizeOnboardingStatus(null)).toBeNull();
    });
  });

  describe('getOnboardingStatus', () => {
    it('returns localStorage status when server returns 404', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          status: 404,
          ok: false,
        })
      );

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

    it('falls back to localStorage when the server status contract is malformed', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(
          new Response(JSON.stringify({ primaryPersona: 'server-builder' }), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          })
        )
      );

      localStorage.setItem('onboarding_complete', JSON.stringify('true'));
      localStorage.setItem('yappc_primary_persona', JSON.stringify('local-builder'));

      const status = await getOnboardingStatus();

      expect(status.completed).toBe(true);
      expect(status.primaryPersona).toBe('local-builder');
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
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          status: 501,
          ok: false,
        })
      );

      const result = await setOnboardingStatus({
        completed: true,
        primaryPersona: 'product-owner',
      });

      expect(result.completed).toBe(true);
      expect(result.primaryPersona).toBe('product-owner');
      expect(localStorage.getItem('onboarding_complete')).toBe(JSON.stringify('true'));
    });

    it('keeps the local write when a server update response is malformed', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue(
          new Response(JSON.stringify({ completed: 'yes' }), {
            status: 200,
            headers: { 'content-type': 'application/json' },
          })
        )
      );

      const result = await setOnboardingStatus({
        completed: true,
        primaryPersona: 'builder',
      });

      expect(result).toEqual(expect.objectContaining({
        completed: true,
        primaryPersona: 'builder',
      }));
      expect(localStorage.getItem('onboarding_complete')).toBe(JSON.stringify('true'));
      expect(localStorage.getItem('yappc_primary_persona')).toBe(JSON.stringify('builder'));
    });
  });
});
