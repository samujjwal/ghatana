/**
 * @fileoverview Tests for security types and trust model.
 */

import { describe, it, expect } from 'vitest';
import {
  TRUST_LEVELS,
  isValidTrustLevel,
  SANDBOX_PROFILES,
  createDefaultSecurityPolicy,
  getSandboxProfile,
  isTrustLevelTransitionAllowed,
} from '../security/types';

describe('Security Types', () => {
  describe('Trust Levels', () => {
    it('should have all 4 trust levels', () => {
      expect(TRUST_LEVELS).toHaveLength(4);
      expect(TRUST_LEVELS).toContain('TRUSTED_WORKSPACE');
      expect(TRUST_LEVELS).toContain('GENERATED_TRUSTED');
      expect(TRUST_LEVELS).toContain('IMPORTED_REVIEW_REQUIRED');
      expect(TRUST_LEVELS).toContain('UNTRUSTED');
    });

    it('should validate trust levels correctly', () => {
      expect(isValidTrustLevel('TRUSTED_WORKSPACE')).toBe(true);
      expect(isValidTrustLevel('GENERATED_TRUSTED')).toBe(true);
      expect(isValidTrustLevel('IMPORTED_REVIEW_REQUIRED')).toBe(true);
      expect(isValidTrustLevel('UNTRUSTED')).toBe(true);
      expect(isValidTrustLevel('INVALID')).toBe(false);
    });
  });

  describe('Sandbox Profiles', () => {
    it('should have sandbox profiles for all trust levels', () => {
      TRUST_LEVELS.forEach((level) => {
        const profile = SANDBOX_PROFILES[level];
        expect(profile).toBeDefined();
        expect(profile.trustLevel).toBe(level);
        expect(typeof profile.iframeSandbox).toBe('string');
      });
    });

    it('should have appropriate permissions for TRUSTED_WORKSPACE', () => {
      const profile = SANDBOX_PROFILES.TRUSTED_WORKSPACE;
      expect(profile.allowScripts).toBe(true);
      expect(profile.allowSameOrigin).toBe(true);
      expect(profile.allowForms).toBe(true);
      expect(profile.allowPopups).toBe(true);
      expect(profile.iframeSandbox).toContain('allow-scripts');
      expect(profile.iframeSandbox).toContain('allow-same-origin');
    });

    it('should have restricted permissions for UNTRUSTED', () => {
      const profile = SANDBOX_PROFILES.UNTRUSTED;
      expect(profile.allowScripts).toBe(false);
      expect(profile.allowSameOrigin).toBe(false);
      expect(profile.allowForms).toBe(false);
      expect(profile.iframeSandbox).toBe('');
    });

    it('should get sandbox profile by trust level', () => {
      const profile = getSandboxProfile('GENERATED_TRUSTED');
      expect(profile.trustLevel).toBe('GENERATED_TRUSTED');
      expect(profile.allowScripts).toBe(true);
      expect(profile.allowSameOrigin).toBe(false);
    });

    it('should return UNTRUSTED profile for invalid level', () => {
      const profile = getSandboxProfile('INVALID' as TrustLevel);
      expect(profile.trustLevel).toBe('UNTRUSTED');
    });
  });

  describe('Security Policy', () => {
    it('should create default security policy', () => {
      const policy = createDefaultSecurityPolicy();

      expect(policy.sandboxLevel).toBe('iframe');
      expect(policy.allowedResourceTypes).toContain('script');
      expect(policy.allowedResourceTypes).toContain('style');
      expect(policy.inlineScriptRestrictions).toBe('hash-required');
      expect(policy.networkAccess).toBe('same-origin');
      expect(policy.storageAccess).toBe('none');
    });
  });

  describe('Trust Level Transitions', () => {
    it('should allow upgrading trust levels', () => {
      expect(isTrustLevelTransitionAllowed('UNTRUSTED', 'TRUSTED_WORKSPACE')).toBe(true);
      expect(isTrustLevelTransitionAllowed('UNTRUSTED', 'IMPORTED_REVIEW_REQUIRED')).toBe(true);
      expect(isTrustLevelTransitionAllowed('GENERATED_TRUSTED', 'TRUSTED_WORKSPACE')).toBe(true);
    });

    it('should block downgrading from TRUSTED_WORKSPACE', () => {
      expect(isTrustLevelTransitionAllowed('TRUSTED_WORKSPACE', 'UNTRUSTED')).toBe(false);
      expect(isTrustLevelTransitionAllowed('TRUSTED_WORKSPACE', 'GENERATED_TRUSTED')).toBe(false);
      expect(isTrustLevelTransitionAllowed('TRUSTED_WORKSPACE', 'IMPORTED_REVIEW_REQUIRED')).toBe(false);
    });

    it('should allow same-level transitions', () => {
      expect(isTrustLevelTransitionAllowed('TRUSTED_WORKSPACE', 'TRUSTED_WORKSPACE')).toBe(true);
      expect(isTrustLevelTransitionAllowed('UNTRUSTED', 'UNTRUSTED')).toBe(true);
    });
  });
});

type TrustLevel = 'TRUSTED_WORKSPACE' | 'GENERATED_TRUSTED' | 'IMPORTED_REVIEW_REQUIRED' | 'UNTRUSTED';
