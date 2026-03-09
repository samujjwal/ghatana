/**
 * Color Contrast Testing Utilities
 * 
 * WCAG 2.1 Level AA Compliance:
 * - Normal text: 4.5:1 contrast ratio
 * - Large text (18pt/14pt bold): 3:1 contrast ratio
 * - UI components: 3:1 contrast ratio
 * 
 * @doc.type utility
 * @doc.purpose Accessibility contrast validation
 * @doc.layer core
 */

/**
 * Calculate relative luminance of a color
 * https://www.w3.org/WAI/GL/wiki/Relative_luminance
 */
function getLuminance(r: number, g: number, b: number): number {
  const [rs, gs, bs] = [r, g, b].map((c) => {
    const val = c / 255;
    return val <= 0.03928 ? val / 12.92 : Math.pow((val + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

/**
 * Parse hex color to RGB
 */
function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  return result
    ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16),
      }
    : null;
}

/**
 * Calculate contrast ratio between two colors
 * https://www.w3.org/WAI/GL/wiki/Contrast_ratio
 */
export function getContrastRatio(color1: string, color2: string): number {
  const rgb1 = hexToRgb(color1);
  const rgb2 = hexToRgb(color2);

  if (!rgb1 || !rgb2) {
    throw new Error('Invalid hex color format');
  }

  const lum1 = getLuminance(rgb1.r, rgb1.g, rgb1.b);
  const lum2 = getLuminance(rgb2.r, rgb2.g, rgb2.b);

  const lighter = Math.max(lum1, lum2);
  const darker = Math.min(lum1, lum2);

  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * WCAG 2.1 Level AA contrast requirements
 */
export const WCAG_AA = {
  normalText: 4.5,
  largeText: 3.0,
  uiComponents: 3.0,
} as const;

/**
 * WCAG 2.1 Level AAA contrast requirements
 */
export const WCAG_AAA = {
  normalText: 7.0,
  largeText: 4.5,
  uiComponents: 3.0,
} as const;

/**
 * Check if contrast meets WCAG AA standards
 */
export function meetsWCAG_AA(
  foreground: string,
  background: string,
  textSize: 'normal' | 'large' = 'normal'
): boolean {
  const ratio = getContrastRatio(foreground, background);
  const required = textSize === 'normal' ? WCAG_AA.normalText : WCAG_AA.largeText;
  return ratio >= required;
}

/**
 * Check if contrast meets WCAG AAA standards
 */
export function meetsWCAG_AAA(
  foreground: string,
  background: string,
  textSize: 'normal' | 'large' = 'normal'
): boolean {
  const ratio = getContrastRatio(foreground, background);
  const required = textSize === 'normal' ? WCAG_AAA.normalText : WCAG_AAA.largeText;
  return ratio >= required;
}

/**
 * Get contrast rating for a color pair
 */
export function getContrastRating(
  foreground: string,
  background: string,
  textSize: 'normal' | 'large' = 'normal'
): 'AAA' | 'AA' | 'AA Large' | 'Fail' {
  const ratio = getContrastRatio(foreground, background);

  if (textSize === 'normal') {
    if (ratio >= WCAG_AAA.normalText) return 'AAA';
    if (ratio >= WCAG_AA.normalText) return 'AA';
    return 'Fail';
  } else {
    if (ratio >= WCAG_AAA.largeText) return 'AAA';
    if (ratio >= WCAG_AA.largeText) return 'AA Large';
    return 'Fail';
  }
}

/**
 * Test color combinations from semantic colors
 */
export interface ContrastTestResult {
  foreground: string;
  background: string;
  ratio: number;
  rating: 'AAA' | 'AA' | 'AA Large' | 'Fail';
  passes: boolean;
}

/**
 * Batch test color combinations
 */
export function testColorCombinations(
  combinations: Array<{ fg: string; bg: string; name: string }>,
  textSize: 'normal' | 'large' = 'normal'
): Array<ContrastTestResult & { name: string }> {
  return combinations.map(({ fg, bg, name }) => {
    const ratio = getContrastRatio(fg, bg);
    const rating = getContrastRating(fg, bg, textSize);
    const passes = rating !== 'Fail';

    return {
      name,
      foreground: fg,
      background: bg,
      ratio: Math.round(ratio * 100) / 100,
      rating,
      passes,
    };
  });
}

/**
 * Generate contrast report for common UI patterns
 */
export function generateContrastReport(colors: {
  text: string;
  background: string;
  primary: string;
  primaryText: string;
  error: string;
  errorText: string;
  success: string;
  successText: string;
  warning: string;
  warningText: string;
}): ContrastTestResult[] {
  const combinations = [
    { fg: colors.text, bg: colors.background, name: 'Body text' },
    { fg: colors.primaryText, bg: colors.primary, name: 'Primary button' },
    { fg: colors.errorText, bg: colors.error, name: 'Error message' },
    { fg: colors.successText, bg: colors.success, name: 'Success message' },
    { fg: colors.warningText, bg: colors.warning, name: 'Warning message' },
  ];

  return testColorCombinations(combinations);
}

export default {
  getContrastRatio,
  meetsWCAG_AA,
  meetsWCAG_AAA,
  getContrastRating,
  testColorCombinations,
  generateContrastReport,
  WCAG_AA,
  WCAG_AAA,
};
