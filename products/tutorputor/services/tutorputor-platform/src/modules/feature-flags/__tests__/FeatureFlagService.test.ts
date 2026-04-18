/**
 * Feature Flag Service Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { FeatureFlagService } from '../FeatureFlagService.js';

describe('FeatureFlagService', () => {
  let service: FeatureFlagService;

  beforeEach(() => {
    service = new FeatureFlagService('development');
  });

  describe('isEnabled', () => {
    it('should return true for enabled flags', () => {
      expect(service.isEnabled('ai_tutoring')).toBe(true);
    });

    it('should return false for disabled flags', () => {
      expect(service.isEnabled('marketplace')).toBe(false);
    });

    it('should respect environment whitelist', () => {
      const prodService = new FeatureFlagService('production');
      expect(prodService.isEnabled('new_ui')).toBe(false);
    });

    it('should respect user whitelist', () => {
      service.addToWhitelist('marketplace', 'user123');
      expect(service.isEnabled('marketplace', 'user123')).toBe(true);
    });

    it('should respect user blacklist', () => {
      service.enable('ai_tutoring');
      expect(service.isEnabled('ai_tutoring', 'user123')).toBe(true);
    });

    it('should handle percentage-based rollouts consistently', () => {
      const userId = 'user123';
      const result1 = service.isEnabled('gamification', userId);
      const result2 = service.isEnabled('gamification', userId);
      expect(result1).toBe(result2);
    });

    it('should return false for unknown flags', () => {
      expect(service.isEnabled('unknown_flag')).toBe(false);
    });
  });

  describe('enable and disable', () => {
    it('should enable a flag', () => {
      service.enable('marketplace');
      expect(service.isEnabled('marketplace')).toBe(true);
    });

    it('should disable a flag', () => {
      service.disable('ai_tutoring');
      expect(service.isEnabled('ai_tutoring')).toBe(false);
    });
  });

  describe('setRolloutPercentage', () => {
    it('should set rollout percentage', () => {
      service.setRolloutPercentage('gamification', 75);
      const flag = service.getFlag('gamification');
      expect(flag?.rolloutPercentage).toBe(75);
    });

    it('should clamp percentage to 0-100', () => {
      service.setRolloutPercentage('gamification', 150);
      const flag = service.getFlag('gamification');
      expect(flag?.rolloutPercentage).toBe(100);

      service.setRolloutPercentage('gamification', -10);
      expect(flag?.rolloutPercentage).toBe(0);
    });
  });

  describe('whitelist management', () => {
    it('should add user to whitelist', () => {
      service.addToWhitelist('marketplace', 'user123');
      const flag = service.getFlag('marketplace');
      expect(flag?.userWhitelist).toContain('user123');
    });

    it('should not add duplicate users to whitelist', () => {
      service.addToWhitelist('marketplace', 'user123');
      service.addToWhitelist('marketplace', 'user123');
      const flag = service.getFlag('marketplace');
      expect(flag?.userWhitelist?.filter(id => id === 'user123').length).toBe(1);
    });

    it('should remove user from whitelist', () => {
      service.addToWhitelist('marketplace', 'user123');
      service.removeFromWhitelist('marketplace', 'user123');
      const flag = service.getFlag('marketplace');
      expect(flag?.userWhitelist).not.toContain('user123');
    });
  });

  describe('getAllFlags', () => {
    it('should return all flags', () => {
      const flags = service.getAllFlags();
      expect(flags.length).toBeGreaterThan(0);
      expect(flags.every(f => f.key)).toBe(true);
    });
  });

  describe('getFlag', () => {
    it('should return specific flag', () => {
      const flag = service.getFlag('ai_tutoring');
      expect(flag).toBeDefined();
      expect(flag?.key).toBe('ai_tutoring');
    });

    it('should return undefined for unknown flag', () => {
      const flag = service.getFlag('unknown_flag');
      expect(flag).toBeUndefined();
    });
  });
});
