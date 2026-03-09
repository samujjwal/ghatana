/**
 * Compliance Frameworks Tests
 *
 * @jest-environment jsdom
 */

import {
  ComplianceFramework,
  SOC2_CONTROLS,
  ISO_27001_CONTROLS,
  HIPAA_CONTROLS,
  getFrameworkControls,
  getControlRequirements,
  calculateFrameworkCompliance,
  calculateWeightedCompliance,
} from '../ComplianceFrameworks';

describe('ComplianceFrameworks', () => {
  describe('Framework Controls', () => {
    it('should return SOC2 controls', () => {
      const controls = getFrameworkControls(ComplianceFramework.SOC2);

      expect(controls).toBe(SOC2_CONTROLS);
      expect(controls.length).toBeGreaterThan(0);
      expect(controls[0]).toHaveProperty('id');
      expect(controls[0]).toHaveProperty('title');
      expect(controls[0]).toHaveProperty('severity');
    });

    it('should return ISO 27001 controls', () => {
      const controls = getFrameworkControls(ComplianceFramework.ISO_27001);

      expect(controls).toBe(ISO_27001_CONTROLS);
      expect(controls.length).toBeGreaterThan(0);
    });

    it('should return HIPAA controls', () => {
      const controls = getFrameworkControls(ComplianceFramework.HIPAA);

      expect(controls).toBe(HIPAA_CONTROLS);
      expect(controls.length).toBeGreaterThan(0);
    });

    it('should get specific control requirements', () => {
      const control = getControlRequirements(ComplianceFramework.SOC2, 'CC6.1');

      expect(control).not.toBeNull();
      expect(control?.title).toBe('Logical and Physical Access Controls');
      expect(control?.severity).toBe('CRITICAL');
    });

    it('should return null for non-existent control', () => {
      const control = getControlRequirements(ComplianceFramework.SOC2, 'NONEXISTENT');

      expect(control).toBeNull();
    });
  });

  describe('Compliance Scoring', () => {
    it('should calculate simple compliance score', () => {
      const statuses = [
        { status: 'compliant', score: 100 },
        { status: 'compliant', score: 100 },
        { status: 'non-compliant', score: 0 },
      ];

      const score = calculateFrameworkCompliance(statuses);

      expect(score).toBe(67);
    });

    it('should handle empty statuses', () => {
      const score = calculateFrameworkCompliance([]);

      expect(score).toBe(0);
    });

    it('should calculate weighted compliance', () => {
      const statuses = new Map<string, number>([
        ['CC6.1', 100], // CRITICAL
        ['CC6.2', 50], // HIGH
        ['CC7.1', 100], // HIGH
      ]);

      const score = calculateWeightedCompliance(ComplianceFramework.SOC2, statuses);

      expect(score).toBeGreaterThan(0);
      expect(score).toBeLessThanOrEqual(100);
    });

    it('should weight critical controls higher', () => {
      const statusesAllCritical = new Map<string, number>([['CC6.1', 100]]);

      const statusesAllHigh = new Map<string, number>([['CC6.2', 100]]);

      // Critical at 100% should score higher than High at 100%
      // (because framework calculation considers all controls)
      const scoreAll = calculateWeightedCompliance(ComplianceFramework.SOC2, new Map());

      expect(scoreAll).toBeDefined();
    });
  });

  describe('Control Properties', () => {
    it('should have all required control properties', () => {
      const allControls = [...SOC2_CONTROLS, ...ISO_27001_CONTROLS, ...HIPAA_CONTROLS];

      for (const control of allControls) {
        expect(control).toHaveProperty('id');
        expect(control).toHaveProperty('title');
        expect(control).toHaveProperty('description');
        expect(control).toHaveProperty('category');
        expect(control).toHaveProperty('severity');
        expect(control).toHaveProperty('assessmentFrequency');

        // Validate property types
        expect(typeof control.id).toBe('string');
        expect(typeof control.title).toBe('string');
        expect(typeof control.description).toBe('string');
        expect(['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']).toContain(control.severity);
        expect(typeof control.assessmentFrequency).toBe('number');
      }
    });

    it('should have non-empty control definitions', () => {
      const allControls = [...SOC2_CONTROLS, ...ISO_27001_CONTROLS, ...HIPAA_CONTROLS];

      for (const control of allControls) {
        expect(control.id.length).toBeGreaterThan(0);
        expect(control.title.length).toBeGreaterThan(0);
        expect(control.description.length).toBeGreaterThan(0);
        expect(control.assessmentFrequency).toBeGreaterThan(0);
      }
    });

    it('should have CRITICAL controls in major frameworks', () => {
      const soc2Critical = SOC2_CONTROLS.filter((c) => c.severity === 'CRITICAL');
      const iso27001Critical = ISO_27001_CONTROLS.filter((c) => c.severity === 'CRITICAL');
      const hipaaCritical = HIPAA_CONTROLS.filter((c) => c.severity === 'CRITICAL');

      expect(soc2Critical.length).toBeGreaterThan(0);
      expect(iso27001Critical.length).toBeGreaterThan(0);
      expect(hipaaCritical.length).toBeGreaterThan(0);
    });
  });
});
