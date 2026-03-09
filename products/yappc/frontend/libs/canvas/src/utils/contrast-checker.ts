/**
 * Contrast Validation Utility
 *
 * Validates color contrast ratios for WCAG compliance.
 * Provides utilities to check token pairs and prevent
 * accessibility regressions.
 *
 * @doc.type utility
 * @doc.purpose Ensure WCAG AA contrast compliance
 * @doc.layer core
 * @doc.pattern Validation
 */

/**
 * WCAG contrast ratio thresholds
 */
export const WCAG_LEVELS = {
  /** Enhanced contrast (7:1) */
  AAA: 7,
  /** Minimum contrast for normal text (4.5:1) */
  AA: 4.5,
  /** Minimum contrast for large text (3:1) */
  AA_LARGE: 3,
} as const;

/**
 * Contrast check result
 */
export interface ContrastCheck {
  /** Foreground color */
  foreground: string;
  /** Background color */
  background: string;
  /** Calculated contrast ratio */
  ratio: number;
  /** WCAG compliance level */
  level: 'AAA' | 'AA' | 'AA_LARGE' | 'FAIL';
  /** Context description */
  context: string;
  /** Whether this passes minimum requirements */
  passes: boolean;
}

/**
 * Convert hex color to RGB
 */
function hexToRgb(hex: string): { r: number; g: number; b: number } | null {
  // Remove # if present
  hex = hex.replace(/^#/, '');

  // Handle 3-digit hex
  if (hex.length === 3) {
    hex = hex
      .split('')
      .map((char) => char + char)
      .join('');
  }

  if (hex.length !== 6) {
    return null;
  }

  const r = parseInt(hex.substr(0, 2), 16);
  const g = parseInt(hex.substr(2, 2), 16);
  const b = parseInt(hex.substr(4, 2), 16);

  return { r, g, b };
}

/**
 * Calculate relative luminance
 * https://www.w3.org/TR/WCAG20/#relativeluminancedef
 */
function getLuminance(r: number, g: number, b: number): number {
  const [rs, gs, bs] = [r, g, b].map((val) => {
    val = val / 255;
    return val <= 0.03928 ? val / 12.92 : Math.pow((val + 0.055) / 1.055, 2.4);
  });

  return 0.2126 * rs + 0.7152 * gs + 0.0722 * bs;
}

/**
 * Calculate contrast ratio between two colors
 * https://www.w3.org/TR/WCAG20/#contrast-ratiodef
 *
 * @param color1 - First color (hex)
 * @param color2 - Second color (hex)
 * @returns Contrast ratio (1-21)
 */
export function calculateContrastRatio(color1: string, color2: string): number {
  const rgb1 = hexToRgb(color1);
  const rgb2 = hexToRgb(color2);

  if (!rgb1 || !rgb2) {
    console.warn(`Invalid color format: ${color1} or ${color2}`);
    return 0;
  }

  const lum1 = getLuminance(rgb1.r, rgb1.g, rgb1.b);
  const lum2 = getLuminance(rgb2.r, rgb2.g, rgb2.b);

  const lighter = Math.max(lum1, lum2);
  const darker = Math.min(lum1, lum2);

  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Determine WCAG level from contrast ratio
 */
function getWCAGLevel(ratio: number): 'AAA' | 'AA' | 'AA_LARGE' | 'FAIL' {
  if (ratio >= WCAG_LEVELS.AAA) return 'AAA';
  if (ratio >= WCAG_LEVELS.AA) return 'AA';
  if (ratio >= WCAG_LEVELS.AA_LARGE) return 'AA_LARGE';
  return 'FAIL';
}

/**
 * Validate contrast between two colors
 *
 * @param foreground - Foreground color (text, icons)
 * @param background - Background color
 * @param context - Description of where this is used
 * @param largeText - Whether text is large (18pt+ or 14pt+ bold)
 * @returns Contrast check result
 */
export function validateContrast(
  foreground: string,
  background: string,
  context: string,
  largeText = false
): ContrastCheck {
  const ratio = calculateContrastRatio(foreground, background);
  const level = getWCAGLevel(ratio);

  const minimumRatio = largeText ? WCAG_LEVELS.AA_LARGE : WCAG_LEVELS.AA;
  const passes = ratio >= minimumRatio;

  return {
    foreground,
    background,
    ratio,
    level,
    context,
    passes,
  };
}

/**
 * Validate all phase colors for contrast compliance
 */
export function validatePhaseColors(): ContrastCheck[] {
  const checks: ContrastCheck[] = [];

  // Import phase colors dynamically to avoid circular dependency
  const { PHASE_COLORS, getAllPhases } = require('./phase-colors');

  getAllPhases().forEach((phase: string) => {
    const colors = PHASE_COLORS[phase];

    // Text on background
    checks.push(
      validateContrast(
        colors.text,
        colors.background,
        `${phase} - text on background`,
        false
      )
    );

    // Primary on white (for badges, chips)
    checks.push(
      validateContrast(
        colors.primary,
        '#ffffff',
        `${phase} - primary on white`,
        true // Usually larger text
      )
    );

    // Text on hover
    checks.push(
      validateContrast(
        colors.text,
        colors.hover,
        `${phase} - text on hover`,
        false
      )
    );
  });

  return checks;
}

/**
 * Validate semantic token colors
 */
export function validateSemanticTokens(): ContrastCheck[] {
  const checks: ContrastCheck[] = [];

  // These would come from your token system
  const semanticPairs: Array<{
    fg: string;
    bg: string;
    context: string;
    largeText?: boolean;
  }> = [
    { fg: '#757575', bg: '#ffffff', context: 'disabled-text' },
    { fg: '#757575', bg: '#f5f5f5', context: 'disabled-text-on-surface' },
    { fg: '#2e7d32', bg: '#e8f5e9', context: 'success-text' },
    { fg: '#d32f2f', bg: '#ffebee', context: 'error-text' },
    { fg: '#b34700', bg: '#fff3e0', context: 'warning-text' },
    { fg: '#1976d2', bg: '#e3f2fd', context: 'info-text' },
  ];

  semanticPairs.forEach((pair) => {
    checks.push(
      validateContrast(pair.fg, pair.bg, pair.context, pair.largeText)
    );
  });

  return checks;
}

/**
 * Check all token pairs for contrast compliance
 */
export function checkAllTokenPairs(): ContrastCheck[] {
  return [...validatePhaseColors(), ...validateSemanticTokens()];
}

/**
 * Get failing contrast checks
 */
export function getFailingChecks(checks: ContrastCheck[]): ContrastCheck[] {
  return checks.filter((check) => !check.passes);
}

/**
 * Generate contrast report
 */
export function generateContrastReport(checks: ContrastCheck[]): string {
  const failing = getFailingChecks(checks);
  const passing = checks.filter((check) => check.passes);

  let report = '# Contrast Validation Report\n\n';
  report += `Total Checks: ${checks.length}\n`;
  report += `Passing: ${passing.length} ✅\n`;
  report += `Failing: ${failing.length} ❌\n\n`;

  if (failing.length > 0) {
    report += '## Failing Checks\n\n';
    failing.forEach((check) => {
      report += `### ${check.context}\n`;
      report += `- Foreground: ${check.foreground}\n`;
      report += `- Background: ${check.background}\n`;
      report += `- Ratio: ${check.ratio.toFixed(2)}:1 (needs ≥ 4.5:1)\n`;
      report += `- Level: ${check.level}\n\n`;
    });
  }

  report += '## Summary by Level\n\n';
  const byLevel = checks.reduce(
    (acc, check) => {
      acc[check.level]++;
      return acc;
    },
    { AAA: 0, AA: 0, AA_LARGE: 0, FAIL: 0 }
  );

  Object.entries(byLevel).forEach(([level, count]) => {
    report += `- ${level}: ${count}\n`;
  });

  return report;
}

/**
 * Suggest accessible color alternatives
 *
 * @param foreground - Current foreground color
 * @param background - Current background color
 * @returns Suggested alternatives with better contrast
 */
export function suggestAccessibleAlternatives(
  foreground: string,
  background: string
): { lightened: string; darkened: string } {
  const rgb = hexToRgb(foreground);
  if (!rgb) {
    return { lightened: foreground, darkened: foreground };
  }

  // Simple approach: lighten or darken by 20%
  const lighten = (val: number) => Math.min(255, Math.floor(val * 1.2));
  const darken = (val: number) => Math.max(0, Math.floor(val * 0.8));

  const lightened = `#${lighten(rgb.r).toString(16).padStart(2, '0')}${lighten(rgb.g).toString(16).padStart(2, '0')}${lighten(rgb.b).toString(16).padStart(2, '0')}`;
  const darkened = `#${darken(rgb.r).toString(16).padStart(2, '0')}${darken(rgb.g).toString(16).padStart(2, '0')}${darken(rgb.b).toString(16).padStart(2, '0')}`;

  return { lightened, darkened };
}

/**
 * Format ratio for display
 */
export function formatRatio(ratio: number): string {
  return `${ratio.toFixed(2)}:1`;
}

/**
 * Get emoji for WCAG level
 */
export function getLevelEmoji(level: ContrastCheck['level']): string {
  switch (level) {
    case 'AAA':
      return '🏆';
    case 'AA':
      return '✅';
    case 'AA_LARGE':
      return '⚠️';
    case 'FAIL':
      return '❌';
  }
}
