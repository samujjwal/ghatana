/**
 * Theme Validator
 *
 * Validates custom theme uploads against the CanvasTheme schema.
 * Ensures theme integrity and provides helpful error messages.
 *
 * @module theming/themeValidator
 */

import { z } from 'zod';

import type { CanvasTheme } from '../types/canvas-document';

/**
 * Zod schema for CanvasTheme validation
 */
export const CanvasThemeSchema = z.object({
  colors: z.object({
    background: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Must be a valid hex color'),
    grid: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Must be a valid hex color'),
    selection: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Must be a valid hex color'),
    hover: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Must be a valid hex color'),
    focus: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Must be a valid hex color'),
    error: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Must be a valid hex color'),
    success: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Must be a valid hex color'),
    warning: z.string().regex(/^#[0-9a-fA-F]{6}$/, 'Must be a valid hex color'),
  }),
  spacing: z.object({
    xs: z.number().min(0).max(100),
    sm: z.number().min(0).max(100),
    md: z.number().min(0).max(100),
    lg: z.number().min(0).max(100),
    xl: z.number().min(0).max(100),
  }),
  borderRadius: z.object({
    sm: z.number().min(0).max(50),
    md: z.number().min(0).max(50),
    lg: z.number().min(0).max(50),
  }),
  shadows: z.object({
    sm: z.string(),
    md: z.string(),
    lg: z.string(),
  }),
  typography: z.object({
    fontFamily: z.string().min(1),
    fontSize: z.object({
      xs: z.number().min(8).max(32),
      sm: z.number().min(8).max(32),
      md: z.number().min(8).max(32),
      lg: z.number().min(8).max(32),
      xl: z.number().min(8).max(32),
    }),
    fontWeight: z.object({
      normal: z.number().min(100).max(900),
      medium: z.number().min(100).max(900),
      bold: z.number().min(100).max(900),
    }),
  }),
});

/**
 * Validation result
 */
export interface ThemeValidationResult {
  readonly valid: boolean;
  readonly errors: readonly string[];
  readonly theme?: CanvasTheme;
}

/**
 * Validate a theme object
 */
export function validateTheme(theme: unknown): ThemeValidationResult {
  const result = CanvasThemeSchema.safeParse(theme);

  if (result.success) {
    return {
      valid: true,
      errors: [],
      theme: result.data as CanvasTheme,
    };
  }

  const errors = result.error.issues.map((issue) => {
    const path = issue.path.join('.');
    return `${path}: ${issue.message}`;
  });

  return {
    valid: false,
    errors,
  };
}

/**
 * Validate theme JSON string
 */
export function validateThemeJSON(json: string): ThemeValidationResult {
  try {
    const parsed = JSON.parse(json);
    return validateTheme(parsed);
  } catch (error) {
    return {
      valid: false,
      errors: [`Invalid JSON: ${  error instanceof Error ? error.message : String(error)}`],
    };
  }
}

/**
 * Check if theme has sufficient contrast ratios (WCAG AA)
 */
export function checkContrast(theme: CanvasTheme): {
  readonly sufficient: boolean;
  readonly warnings: readonly string[];
} {
  const warnings: string[] = [];

  // Simple contrast check (hex to luminance calculation)
  const hexToLuminance = (hex: string): number => {
    const rgb = parseInt(hex.slice(1), 16);
    const r = ((rgb >> 16) & 0xff) / 255;
    const g = ((rgb >> 8) & 0xff) / 255;
    const b = (rgb & 0xff) / 255;

    const [rL, gL, bL] = [r, g, b].map((c) => {
      return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    });

    return 0.2126 * rL + 0.7152 * gL + 0.0722 * bL;
  };

  const contrastRatio = (l1: number, l2: number): number => {
    const lighter = Math.max(l1, l2);
    const darker = Math.min(l1, l2);
    return (lighter + 0.05) / (darker + 0.05);
  };

  const bgLuminance = hexToLuminance(theme.colors.background);

  // Check selection contrast
  const selectionLuminance = hexToLuminance(theme.colors.selection);
  const selectionContrast = contrastRatio(bgLuminance, selectionLuminance);
  if (selectionContrast < 3) {
    warnings.push(`Selection color contrast is ${selectionContrast.toFixed(2)}, should be at least 3:1`);
  }

  // Check error contrast
  const errorLuminance = hexToLuminance(theme.colors.error);
  const errorContrast = contrastRatio(bgLuminance, errorLuminance);
  if (errorContrast < 3) {
    warnings.push(`Error color contrast is ${errorContrast.toFixed(2)}, should be at least 3:1`);
  }

  return {
    sufficient: warnings.length === 0,
    warnings,
  };
}
