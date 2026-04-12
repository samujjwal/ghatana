/**
 * @fileoverview Tests for privacy types and data classification.
 */

import { describe, it, expect } from 'vitest';
import {
  DATA_CLASSIFICATIONS,
  isValidDataClassification,
  createDefaultPrivacyPolicy,
  requiresExplicitConsent,
  getDefaultRetentionPeriod,
  DEFAULT_REDACTION_RULES,
} from '../privacy/types';

describe('Privacy Types', () => {
  describe('Data Classifications', () => {
    it('should have all 5 data classifications', () => {
      expect(DATA_CLASSIFICATIONS).toHaveLength(5);
      expect(DATA_CLASSIFICATIONS).toContain('PUBLIC');
      expect(DATA_CLASSIFICATIONS).toContain('INTERNAL');
      expect(DATA_CLASSIFICATIONS).toContain('SENSITIVE');
      expect(DATA_CLASSIFICATIONS).toContain('CREDENTIALS');
      expect(DATA_CLASSIFICATIONS).toContain('REGULATED');
    });

    it('should validate data classifications correctly', () => {
      expect(isValidDataClassification('PUBLIC')).toBe(true);
      expect(isValidDataClassification('INTERNAL')).toBe(true);
      expect(isValidDataClassification('SENSITIVE')).toBe(true);
      expect(isValidDataClassification('CREDENTIALS')).toBe(true);
      expect(isValidDataClassification('REGULATED')).toBe(true);
      expect(isValidDataClassification('INVALID')).toBe(false);
    });
  });

  describe('Privacy Policy', () => {
    it('should create default privacy policy', () => {
      const policy = createDefaultPrivacyPolicy();

      expect(policy.dataMinimization).toBe(true);
      expect(policy.retentionPeriod).toBe(90);
      expect(policy.redactionRules).toEqual([]);
      expect(policy.externalUseConsent).toBe(false);
      expect(policy.gdprCompliant).toBe(true);
      expect(policy.ccpaCompliant).toBe(true);
      expect(policy.encryptionRequired).toBe(true);
      expect(policy.allowedRegions).toContain('us');
      expect(policy.allowedRegions).toContain('eu');
      expect(policy.allowedRegions).toContain('apac');
    });
  });

  describe('Explicit Consent Requirements', () => {
    it('should require consent for sensitive classifications', () => {
      expect(requiresExplicitConsent('SENSITIVE')).toBe(true);
      expect(requiresExplicitConsent('CREDENTIALS')).toBe(true);
      expect(requiresExplicitConsent('REGULATED')).toBe(true);
    });

    it('should not require consent for public/internal data', () => {
      expect(requiresExplicitConsent('PUBLIC')).toBe(false);
      expect(requiresExplicitConsent('INTERNAL')).toBe(false);
    });
  });

  describe('Default Retention Periods', () => {
    it('should have appropriate retention periods by classification', () => {
      expect(getDefaultRetentionPeriod('PUBLIC')).toBe(365);
      expect(getDefaultRetentionPeriod('INTERNAL')).toBe(180);
      expect(getDefaultRetentionPeriod('SENSITIVE')).toBe(90);
      expect(getDefaultRetentionPeriod('CREDENTIALS')).toBe(30);
      expect(getDefaultRetentionPeriod('REGULATED')).toBe(2555); // 7 years
    });
  });

  describe('Default Redaction Rules', () => {
    it('should have redaction rules for sensitive fields', () => {
      expect(DEFAULT_REDACTION_RULES.length).toBeGreaterThan(0);

      const credentialRule = DEFAULT_REDACTION_RULES.find(
        (r) => r.classification === 'CREDENTIALS'
      );
      expect(credentialRule).toBeDefined();
      expect(credentialRule?.redactionMethod).toBe('remove');
    });

    it('should have email redaction rule', () => {
      const emailRule = DEFAULT_REDACTION_RULES.find(
        (r) => r.fieldPattern === 'email'
      );
      expect(emailRule).toBeDefined();
      expect(emailRule?.redactionMethod).toBe('hash');
    });
  });
});
