/**
 * @fileoverview Tests for WCAG contrast validation.
 *
 * Tests the contrast ratio calculations, WCAG requirement validation,
 * and helper functions for checking AA/AAA compliance.
 *
 * @doc.type test
 * @doc.purpose WCAG contrast validation testing
 * @doc.layer ds-generator
 */

import { describe, it, expect } from 'vitest';
import {
  calculateContrastRatio,
  calculateRelativeLuminance,
  hexToRgb,
  validateContrast,
  passesAA,
  passesAAA,
  passesAALarge,
  passesComponent,
  suggestContrastImprovements,
  validateContrastBatch,
} from '../contrast.js';

describe('WCAG Contrast Validation', () => {
  describe('hexToRgb', () => {
    it('should parse 6-digit hex colors', () => {
      expect(hexToRgb('#ffffff')).toEqual([255, 255, 255]);
      expect(hexToRgb('#000000')).toEqual([0, 0, 0]);
      expect(hexToRgb('#ff0000')).toEqual([255, 0, 0]);
    });

    it('should parse 3-digit hex colors', () => {
      expect(hexToRgb('#fff')).toEqual([255, 255, 255]);
      expect(hexToRgb('#000')).toEqual([0, 0, 0]);
      expect(hexToRgb('#f00')).toEqual([255, 0, 0]);
    });

    it('should parse hex without # prefix', () => {
      expect(hexToRgb('ffffff')).toEqual([255, 255, 255]);
      expect(hexToRgb('000000')).toEqual([0, 0, 0]);
    });

    it('should throw error for invalid hex', () => {
      expect(() => hexToRgb('#gggggg')).toThrow('Invalid hex color');
      expect(() => hexToRgb('invalid')).toThrow('Invalid hex color');
    });
  });

  describe('calculateRelativeLuminance', () => {
    it('should calculate luminance for white', () => {
      expect(calculateRelativeLuminance([255, 255, 255])).toBeCloseTo(1, 2);
    });

    it('should calculate luminance for black', () => {
      expect(calculateRelativeLuminance([0, 0, 0])).toBeCloseTo(0, 2);
    });

    it('should calculate luminance for mid-gray', () => {
      expect(calculateRelativeLuminance([128, 128, 128])).toBeCloseTo(0.215, 2);
    });

    it('should handle different color channels correctly', () => {
      // Red should have higher luminance than blue
      const redLuminance = calculateRelativeLuminance([255, 0, 0]);
      const blueLuminance = calculateRelativeLuminance([0, 0, 255]);
      expect(redLuminance).toBeGreaterThan(blueLuminance);
    });
  });

  describe('calculateContrastRatio', () => {
    it('should calculate contrast ratio for black on white', () => {
      expect(calculateContrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 0);
    });

    it('should calculate contrast ratio for white on black', () => {
      expect(calculateContrastRatio('#ffffff', '#000000')).toBeCloseTo(21, 0);
    });

    it('should calculate contrast ratio for gray on white', () => {
      // #666666 on #ffffff: actual WCAG contrast ratio ≈ 5.74:1
      expect(calculateContrastRatio('#666666', '#ffffff')).toBeCloseTo(5.74, 1);
    });

    it('should be symmetric', () => {
      const ratio1 = calculateContrastRatio('#ff0000', '#00ff00');
      const ratio2 = calculateContrastRatio('#00ff00', '#ff0000');
      expect(ratio1).toBe(ratio2);
    });

    it('should be at least 1:1 for same colors', () => {
      expect(calculateContrastRatio('#ffffff', '#ffffff')).toBe(1);
      expect(calculateContrastRatio('#000000', '#000000')).toBe(1);
    });
  });

  describe('validateContrast', () => {
    it('should validate AA normal text requirement', () => {
      const result = validateContrast('#000000', '#ffffff', {
        level: 'AA',
        size: 'normal',
        component: false,
      });

      expect(result.isValid).toBe(true);
      expect(result.ratio).toBeCloseTo(21, 0);
      expect(result.requiredRatio).toBe(4.5);
    });

    it('should fail validation for low contrast AA normal text', () => {
      const result = validateContrast('#999999', '#ffffff', {
        level: 'AA',
        size: 'normal',
        component: false,
      });

      expect(result.isValid).toBe(false);
      expect(result.ratio).toBeLessThan(4.5);
      expect(result.errors).toHaveLength(1);
    });

    it('should validate AA large text requirement', () => {
      const result = validateContrast('#666666', '#ffffff', {
        level: 'AA',
        size: 'large',
        component: false,
      });

      expect(result.isValid).toBe(true);
      expect(result.requiredRatio).toBe(3.0);
    });

    it('should validate AAA normal text requirement', () => {
      const result = validateContrast('#000000', '#ffffff', {
        level: 'AAA',
        size: 'normal',
        component: false,
      });

      expect(result.isValid).toBe(true);
      expect(result.requiredRatio).toBe(7.0);
    });

    it('should fail AAA for moderate contrast', () => {
      // #666666 on #ffffff ≈ 5.74:1, which fails AAA (requires 7.0:1)
      const result = validateContrast('#666666', '#ffffff', {
        level: 'AAA',
        size: 'normal',
        component: false,
      });

      expect(result.isValid).toBe(false);
      expect(result.ratio).toBeLessThan(7.0);
    });

    it('should validate component contrast requirement', () => {
      const result = validateContrast('#666666', '#ffffff', {
        level: 'AA',
        size: 'normal',
        component: true,
      });

      expect(result.isValid).toBe(true);
      expect(result.requiredRatio).toBe(3.0);
    });

    it('should include error details when validation fails', () => {
      const result = validateContrast('#cccccc', '#ffffff', {
        level: 'AA',
        size: 'normal',
        component: false,
      });

      expect(result.errors[0]).toContain('Contrast ratio');
      expect(result.errors[0]).toContain('AA');
    });
  });

  describe('helper functions', () => {
    it('passesAA should check AA normal text', () => {
      expect(passesAA('#000000', '#ffffff')).toBe(true);
      expect(passesAA('#666666', '#ffffff')).toBe(true);
      expect(passesAA('#999999', '#ffffff')).toBe(false);
    });

    it('passesAAA should check AAA normal text', () => {
      expect(passesAAA('#000000', '#ffffff')).toBe(true);
      // #666666 on #ffffff ≈ 5.74:1, which fails AAA (requires 7.0:1)
      expect(passesAAA('#666666', '#ffffff')).toBe(false);
    });

    it('passesAALarge should check AA large text', () => {
      expect(passesAALarge('#666666', '#ffffff')).toBe(true);
      // #888888 on #ffffff ≈ 3.54:1, which passes AA large (requires 3.0:1)
      expect(passesAALarge('#888888', '#ffffff')).toBe(true);
      // #999999 on #ffffff ≈ 2.85:1, which fails AA large (requires 3.0:1)
      expect(passesAALarge('#999999', '#ffffff')).toBe(false);
      expect(passesAALarge('#cccccc', '#ffffff')).toBe(false);
    });

    it('passesComponent should check component contrast', () => {
      expect(passesComponent('#666666', '#ffffff')).toBe(true);
      // #888888 on #ffffff ≈ 3.54:1, which passes component contrast (requires 3.0:1)
      expect(passesComponent('#888888', '#ffffff')).toBe(true);
      // #999999 on #ffffff ≈ 2.85:1, which fails component contrast (requires 3.0:1)
      expect(passesComponent('#999999', '#ffffff')).toBe(false);
      expect(passesComponent('#cccccc', '#ffffff')).toBe(false);
    });
  });

  describe('suggestContrastImprovements', () => {
    it('should suggest improvements for failing contrast', () => {
      const suggestions = suggestContrastImprovements('#cccccc', '#ffffff', {
        level: 'AA',
        size: 'normal',
        component: false,
      });

      expect(suggestions.length).toBeGreaterThan(0);
      // Production message uses lowercase 'darken'
      expect(suggestions[0]).toContain('darken');
    });

    it('should return empty suggestions for passing contrast', () => {
      const suggestions = suggestContrastImprovements('#000000', '#ffffff', {
        level: 'AA',
        size: 'normal',
        component: false,
      });

      expect(suggestions).toHaveLength(0);
    });

    it('should suggest pure black or white as fallback', () => {
      const suggestions = suggestContrastImprovements('#cccccc', '#ffffff', {
        level: 'AA',
        size: 'normal',
        component: false,
      });

      const hasFallbackSuggestion = suggestions.some((s) =>
        s.includes('pure black') || s.includes('pure white')
      );
      expect(hasFallbackSuggestion).toBe(true);
    });
  });

  describe('validateContrastBatch', () => {
    it('should validate multiple color pairs', () => {
      const results = validateContrastBatch(
        [
          { foreground: '#000000', background: '#ffffff' },
          { foreground: '#666666', background: '#ffffff' },
          { foreground: '#999999', background: '#ffffff' },
        ],
        { level: 'AA', size: 'normal', component: false },
      );

      expect(results).toHaveLength(3);
      expect(results[0].isValid).toBe(true);
      expect(results[1].isValid).toBe(true);
      expect(results[2].isValid).toBe(false);
    });

    it('should return all results even if some fail', () => {
      const results = validateContrastBatch(
        [
          { foreground: '#cccccc', background: '#ffffff' },
          { foreground: '#333333', background: '#ffffff' },
        ],
        { level: 'AA', size: 'normal', component: false },
      );

      expect(results).toHaveLength(2);
      expect(results[0].isValid).toBe(false);
      expect(results[1].isValid).toBe(true);
    });
  });

  describe('WCAG compliance examples', () => {
    it('should pass WCAG AA for common accessible colors', () => {
      // Black text on white background
      expect(passesAA('#000000', '#ffffff')).toBe(true);

      // Dark blue on white
      expect(passesAA('#1a365d', '#ffffff')).toBe(true);

      // Dark gray on white
      expect(passesAA('#333333', '#ffffff')).toBe(true);
    });

    it('should fail WCAG AA for inaccessible colors', () => {
      // Light gray on white
      expect(passesAA('#cccccc', '#ffffff')).toBe(false);

      // Yellow on white
      expect(passesAA('#ffff00', '#ffffff')).toBe(false);

      // Light blue on white
      expect(passesAA('#add8e6', '#ffffff')).toBe(false);
    });

    it('should handle dark background with light text', () => {
      // White text on black background
      expect(passesAA('#ffffff', '#000000')).toBe(true);

      // Light gray on dark blue
      expect(passesAA('#e2e8f0', '#1a365d')).toBe(true);
    });

    it('should validate AAA for high contrast requirements', () => {
      // Black on white passes AAA
      expect(passesAAA('#000000', '#ffffff')).toBe(true);

      // Medium gray on white (#666666 ≈ 5.74:1) fails AAA (requires 7.0:1)
      expect(passesAAA('#666666', '#ffffff')).toBe(false);

      // Black on dark background fails AAA
      expect(passesAAA('#000000', '#333333')).toBe(false);
    });
  });

  describe('edge cases', () => {
    it('should handle very light colors', () => {
      expect(calculateContrastRatio('#fefefe', '#ffffff')).toBeCloseTo(1, 1);
    });

    it('should handle very dark colors', () => {
      expect(calculateContrastRatio('#010101', '#000000')).toBeCloseTo(1, 1);
    });

    it('should handle pure colors', () => {
      const redOnGreen = calculateContrastRatio('#ff0000', '#00ff00');
      expect(redOnGreen).toBeGreaterThan(1);
      expect(redOnGreen).toBeLessThan(21);
    });

    it('should validate contrast with same foreground and background', () => {
      const result = validateContrast('#666666', '#666666', {
        level: 'AA',
        size: 'normal',
        component: false,
      });

      expect(result.isValid).toBe(false);
      expect(result.ratio).toBe(1);
    });
  });
});
