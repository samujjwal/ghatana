/**
 * Theme Validator Tests
 */

import { describe, it, expect } from 'vitest';

import { LIGHT_THEME, DARK_THEME } from '../themeManager';
import { validateTheme, validateThemeJSON, checkContrast, CanvasThemeSchema } from '../themeValidator';

describe.skip('Theme Validator', () => {
  describe('validateTheme', () => {
    it('should validate valid light theme', () => {
      const result = validateTheme(LIGHT_THEME);
      
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
      expect(result.theme).toEqual(LIGHT_THEME);
    });

    it('should validate valid dark theme', () => {
      const result = validateTheme(DARK_THEME);
      
      expect(result.valid).toBe(true);
      expect(result.errors).toHaveLength(0);
    });

    it('should reject theme with invalid hex color', () => {
      const invalidTheme = {
        ...LIGHT_THEME,
        colors: {
          ...LIGHT_THEME.colors,
          background: 'invalid-color',
        },
      };
      
      const result = validateTheme(invalidTheme);
      
      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
      expect(result.errors[0]).toContain('colors.background');
    });

    it('should reject theme with out-of-range spacing', () => {
      const invalidTheme = {
        ...LIGHT_THEME,
        spacing: {
          ...LIGHT_THEME.spacing,
          xs: 150, // Max is 100
        },
      };
      
      const result = validateTheme(invalidTheme);
      
      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain('spacing.xs');
    });

    it('should reject theme with invalid font size', () => {
      const invalidTheme = {
        ...LIGHT_THEME,
        typography: {
          ...LIGHT_THEME.typography,
          fontSize: {
            ...LIGHT_THEME.typography.fontSize,
            md: 5, // Min is 8
          },
        },
      };
      
      const result = validateTheme(invalidTheme);
      
      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain('typography.fontSize.md');
    });

    it('should reject theme with missing required fields', () => {
      const incomplete = {
        colors: {
          background: '#ffffff',
          // Missing required color fields
        },
      };
      
      const result = validateTheme(incomplete);
      
      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });
  });

  describe('validateThemeJSON', () => {
    it('should validate valid JSON theme string', () => {
      const json = JSON.stringify(LIGHT_THEME);
      const result = validateThemeJSON(json);
      
      expect(result.valid).toBe(true);
      expect(result.theme).toEqual(LIGHT_THEME);
    });

    it('should reject invalid JSON', () => {
      const result = validateThemeJSON('{ invalid json }');
      
      expect(result.valid).toBe(false);
      expect(result.errors[0]).toContain('Invalid JSON');
    });

    it('should reject valid JSON with invalid theme', () => {
      const invalidTheme = {
        colors: {
          background: 'not-a-hex-color',
        },
      };
      
      const json = JSON.stringify(invalidTheme);
      const result = validateThemeJSON(json);
      
      expect(result.valid).toBe(false);
      expect(result.errors.length).toBeGreaterThan(0);
    });
  });

  describe('checkContrast', () => {
    it('should pass contrast check for light theme', () => {
      const result = checkContrast(LIGHT_THEME);
      
      // Light theme should have good contrast
      expect(result.sufficient).toBe(true);
      expect(result.warnings).toHaveLength(0);
    });

    it('should pass contrast check for dark theme', () => {
      const result = checkContrast(DARK_THEME);
      
      expect(result.sufficient).toBe(true);
      expect(result.warnings).toHaveLength(0);
    });

    it('should warn about insufficient contrast', () => {
      const lowContrastTheme = {
        ...LIGHT_THEME,
        colors: {
          ...LIGHT_THEME.colors,
          background: '#ffffff',
          selection: '#f0f0f0', // Very low contrast with white
        },
      };
      
      const result = checkContrast(lowContrastTheme);
      
      expect(result.sufficient).toBe(false);
      expect(result.warnings.length).toBeGreaterThan(0);
      expect(result.warnings[0]).toContain('Selection color contrast');
    });

    it('should calculate contrast ratios correctly', () => {
      const theme = {
        ...LIGHT_THEME,
        colors: {
          ...LIGHT_THEME.colors,
          background: '#000000', // Black
          selection: '#ffffff', // White - maximum contrast
        },
      };
      
      const result = checkContrast(theme);
      
      // Black and white should have excellent contrast (21:1)
      expect(result.sufficient).toBe(true);
    });
  });

  describe('Zod Schema', () => {
    it('should parse valid theme with Zod', () => {
      const result = CanvasThemeSchema.safeParse(LIGHT_THEME);
      
      expect(result.success).toBe(true);
    });

    it('should fail on invalid color format', () => {
      const invalid = {
        ...LIGHT_THEME,
        colors: {
          ...LIGHT_THEME.colors,
          background: '#fff', // Should be 6 digits
        },
      };
      
      const result = CanvasThemeSchema.safeParse(invalid);
      
      expect(result.success).toBe(false);
    });

    it('should fail on negative spacing', () => {
      const invalid = {
        ...LIGHT_THEME,
        spacing: {
          ...LIGHT_THEME.spacing,
          xs: -5,
        },
      };
      
      const result = CanvasThemeSchema.safeParse(invalid);
      
      expect(result.success).toBe(false);
    });
  });
});
