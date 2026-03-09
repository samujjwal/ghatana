/**
 * Tests for contrast-checker utility
 */

import { describe, it, expect } from 'vitest';
import {
  calculateContrastRatio,
  validateContrast,
  getFailingChecks,
  WCAG_LEVELS,
} from '../contrast-checker';

describe('contrast-checker', () => {
  describe('calculateContrastRatio', () => {
    it('should calculate white on black as 21:1', () => {
      const ratio = calculateContrastRatio('#ffffff', '#000000');
      expect(ratio).toBeCloseTo(21, 1);
    });

    it('should calculate black on white as 21:1', () => {
      const ratio = calculateContrastRatio('#000000', '#ffffff');
      expect(ratio).toBeCloseTo(21, 1);
    });

    it('should calculate same color as 1:1', () => {
      const ratio = calculateContrastRatio('#ffffff', '#ffffff');
      expect(ratio).toBe(1);
    });

    it('should handle 3-digit hex colors', () => {
      const ratio = calculateContrastRatio('#fff', '#000');
      expect(ratio).toBeCloseTo(21, 1);
    });

    it('should handle colors without # prefix', () => {
      const ratio = calculateContrastRatio('ffffff', '000000');
      expect(ratio).toBeCloseTo(21, 1);
    });
  });

  describe('validateContrast', () => {
    it('should pass for AAA contrast', () => {
      const result = validateContrast('#000000', '#ffffff', 'test', false);
      expect(result.passes).toBe(true);
      expect(result.level).toBe('AAA');
      expect(result.ratio).toBeGreaterThanOrEqual(WCAG_LEVELS.AAA);
    });

    it('should pass for AA contrast', () => {
      const result = validateContrast('#595959', '#ffffff', 'test', false);
      expect(result.passes).toBe(true);
      expect(result.level).toBe('AA');
    });

    it('should fail for insufficient contrast', () => {
      const result = validateContrast('#cccccc', '#ffffff', 'test', false);
      expect(result.passes).toBe(false);
      expect(result.level).toBe('FAIL');
      expect(result.ratio).toBeLessThan(WCAG_LEVELS.AA);
    });

    it('should pass large text with lower ratio', () => {
      const result = validateContrast('#959595', '#ffffff', 'test', true);
      expect(result.passes).toBe(true);
      expect(result.ratio).toBeGreaterThanOrEqual(WCAG_LEVELS.AA_LARGE);
      expect(result.ratio).toBeLessThan(WCAG_LEVELS.AA);
    });

    it('should include context in result', () => {
      const result = validateContrast(
        '#000000',
        '#ffffff',
        'button-text',
        false
      );
      expect(result.context).toBe('button-text');
    });
  });

  describe('getFailingChecks', () => {
    it('should filter out passing checks', () => {
      const checks = [
        validateContrast('#000000', '#ffffff', 'pass', false),
        validateContrast('#cccccc', '#ffffff', 'fail', false),
        validateContrast('#595959', '#ffffff', 'pass', false),
      ];

      const failing = getFailingChecks(checks);
      expect(failing).toHaveLength(1);
      expect(failing[0].context).toBe('fail');
    });

    it('should return empty array when all checks pass', () => {
      const checks = [
        validateContrast('#000000', '#ffffff', 'pass1', false),
        validateContrast('#595959', '#ffffff', 'pass2', false),
      ];

      const failing = getFailingChecks(checks);
      expect(failing).toHaveLength(0);
    });
  });

  describe('Known color pairs', () => {
    it('should validate Material Design disabled text', () => {
      const result = validateContrast('#757575', '#ffffff', 'disabled', false);
      // Material disabled is 38% opacity black on white = #616161
      // 757575 should pass AA
      expect(result.passes).toBe(true);
    });

    it('should validate error text on error background', () => {
      const result = validateContrast('#c62828', '#ffebee', 'error', false);
      expect(result.passes).toBe(true);
    });

    it('should validate success text on success background', () => {
      const result = validateContrast('#2e7d32', '#e8f5e9', 'success', false);
      expect(result.passes).toBe(true);
    });
  });
});
