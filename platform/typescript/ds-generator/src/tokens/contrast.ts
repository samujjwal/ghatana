/**
 * @fileoverview WCAG contrast validation utilities for design system tokens.
 *
 * Provides functions to validate color contrast ratios against WCAG 2.1
 * success criteria for text and graphical components.
 *
 * @doc.type module
 * @doc.purpose WCAG contrast validation
 * @doc.layer ds-generator
 * @doc.pattern Validation
 */

/**
 * WCAG 2.1 contrast ratio requirements.
 */
export interface ContrastRequirements {
  readonly level: 'AA' | 'AAA';
  readonly size: 'normal' | 'large';
  readonly component: boolean;
}

/**
 * Result of a contrast validation check.
 */
export interface ContrastValidationResult {
  readonly isValid: boolean;
  readonly ratio: number;
  readonly requiredRatio: number;
  readonly requirements: ContrastRequirements;
  readonly foreground: string;
  readonly background: string;
  readonly errors: readonly string[];
}

/**
 * Minimum contrast ratios required by WCAG 2.1.
 */
const WCAG_MINIMUM_RATIOS: Record<
  ContrastRequirements['level'],
  Record<ContrastRequirements['size'], number>
> = {
  AA: {
    normal: 4.5,
    large: 3.0,
  },
  AAA: {
    normal: 7.0,
    large: 4.5,
  },
};

/**
 * Component (non-text) contrast requirement.
 */
const COMPONENT_MINIMUM_RATIO = 3.0;

/**
 * Parse a hex color string to RGB values.
 * @param hex - Hex color string (e.g., "#ffffff" or "ffffff")
 * @returns RGB values [r, g, b] in range 0-255
 */
export function hexToRgb(hex: string): [number, number, number] {
  const cleanHex = hex.replace('#', '');
  const isValidHex = /^[0-9a-fA-F]+$/.test(cleanHex);
  if (cleanHex.length === 3 && isValidHex) {
    const r = parseInt(cleanHex.charAt(0) + cleanHex.charAt(0), 16);
    const g = parseInt(cleanHex.charAt(1) + cleanHex.charAt(1), 16);
    const b = parseInt(cleanHex.charAt(2) + cleanHex.charAt(2), 16);
    return [r, g, b];
  }
  if (cleanHex.length === 6 && isValidHex) {
    const r = parseInt(cleanHex.substring(0, 2), 16);
    const g = parseInt(cleanHex.substring(2, 4), 16);
    const b = parseInt(cleanHex.substring(4, 6), 16);
    return [r, g, b];
  }
  throw new Error(`Invalid hex color: ${hex}`);
}

/**
 * Calculate relative luminance of a color.
 * Based on WCAG 2.0 specification: https://www.w3.org/WAI/WCAG21/working-examples/contrast-calculator/
 * @param rgb - RGB values in range 0-255
 * @returns Relative luminance in range 0-1
 */
export function calculateRelativeLuminance(rgb: [number, number, number]): number {
  const linearize = (channel: number): number => {
    const normalized = channel / 255;
    return normalized <= 0.03928
      ? normalized / 12.92
      : Math.pow((normalized + 0.055) / 1.055, 2.4);
  };
  const r = linearize(rgb[0]);
  const g = linearize(rgb[1]);
  const b = linearize(rgb[2]);

  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

/**
 * Calculate contrast ratio between two colors.
 * @param foreground - Foreground color hex string
 * @param background - Background color hex string
 * @returns Contrast ratio in range 1-21
 */
export function calculateContrastRatio(
  foreground: string,
  background: string,
): number {
  const foregroundRgb = hexToRgb(foreground);
  const backgroundRgb = hexToRgb(background);

  const foregroundLuminance = calculateRelativeLuminance(foregroundRgb);
  const backgroundLuminance = calculateRelativeLuminance(backgroundRgb);

  const lighter = Math.max(foregroundLuminance, backgroundLuminance);
  const darker = Math.min(foregroundLuminance, backgroundLuminance);

  return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Validate contrast ratio against WCAG requirements.
 * @param foreground - Foreground color hex string
 * @param background - Background color hex string
 * @param requirements - WCAG requirements to validate against
 * @returns Contrast validation result
 */
export function validateContrast(
  foreground: string,
  background: string,
  requirements: ContrastRequirements,
): ContrastValidationResult {
  const ratio = calculateContrastRatio(foreground, background);
  const errors: string[] = [];

  let requiredRatio: number;
  if (requirements.component) {
    requiredRatio = COMPONENT_MINIMUM_RATIO;
  } else {
    requiredRatio = WCAG_MINIMUM_RATIOS[requirements.level][requirements.size];
  }

  const isValid = ratio >= requiredRatio;

  if (!isValid) {
    errors.push(
      `Contrast ratio ${ratio.toFixed(2)} is below required ${requiredRatio.toFixed(1)}:1 for ${requirements.level} ${requirements.size} ${requirements.component ? 'component' : 'text'}`,
    );
  }

  return {
    isValid,
    ratio,
    requiredRatio,
    requirements,
    foreground,
    background,
    errors,
  };
}

/**
 * Validate multiple color pairs against WCAG requirements.
 * @param colorPairs - Array of color pairs to validate
 * @param requirements - WCAG requirements to validate against
 * @returns Array of validation results
 */
export function validateContrastBatch(
  colorPairs: readonly { foreground: string; background: string }[],
  requirements: ContrastRequirements,
): readonly ContrastValidationResult[] {
  return colorPairs.map(({ foreground, background }) =>
    validateContrast(foreground, background, requirements),
  );
}

/**
 * Check if a color pair passes WCAG AA for normal text.
 * @param foreground - Foreground color hex string
 * @param background - Background color hex string
 * @returns True if contrast ratio meets AA requirements
 */
export function passesAA(
  foreground: string,
  background: string,
): boolean {
  return validateContrast(foreground, background, {
    level: 'AA',
    size: 'normal',
    component: false,
  }).isValid;
}

/**
 * Check if a color pair passes WCAG AAA for normal text.
 * @param foreground - Foreground color hex string
 * @param background - Background color hex string
 * @returns True if contrast ratio meets AAA requirements
 */
export function passesAAA(
  foreground: string,
  background: string,
): boolean {
  return validateContrast(foreground, background, {
    level: 'AAA',
    size: 'normal',
    component: false,
  }).isValid;
}

/**
 * Check if a color pair passes WCAG AA for large text.
 * @param foreground - Foreground color hex string
 * @param background - Background color hex string
 * @returns True if contrast ratio meets AA large text requirements
 */
export function passesAALarge(
  foreground: string,
  background: string,
): boolean {
  return validateContrast(foreground, background, {
    level: 'AA',
    size: 'large',
    component: false,
  }).isValid;
}

/**
 * Check if a color pair passes WCAG component contrast requirements.
 * @param foreground - Foreground color hex string
 * @param background - Background color hex string
 * @returns True if contrast ratio meets component requirements
 */
export function passesComponent(
  foreground: string,
  background: string,
): boolean {
  return validateContrast(foreground, background, {
    level: 'AA',
    size: 'normal',
    component: true,
  }).isValid;
}

/**
 * Suggest improvements for a color pair that fails WCAG requirements.
 * @param foreground - Foreground color hex string
 * @param background - Background color hex string
 * @param requirements - WCAG requirements to meet
 * @returns Array of suggested color adjustments
 */
export function suggestContrastImprovements(
  foreground: string,
  background: string,
  requirements: ContrastRequirements,
): readonly string[] {
  const result = validateContrast(foreground, background, requirements);
  if (result.isValid) {
    return [];
  }

  const suggestions: string[] = [];
  const foregroundRgb = hexToRgb(foreground);
  const backgroundRgb = hexToRgb(background);

  const foregroundLuminance = calculateRelativeLuminance(foregroundRgb);
  const backgroundLuminance = calculateRelativeLuminance(backgroundRgb);

  // Suggest darkening or lightening the foreground
  if (foregroundLuminance > backgroundLuminance) {
    suggestions.push(
      'Darken the foreground color or lighten the background color to improve contrast',
    );
  } else {
    suggestions.push(
      'Lighten the foreground color or darken the background color to improve contrast',
    );
  }

  // Suggest using pure black or white as fallback
  if (backgroundLuminance > 0.5) {
    suggestions.push('Consider using pure black (#000000) as foreground');
  } else {
    suggestions.push('Consider using pure white (#FFFFFF) as foreground');
  }

  return suggestions;
}
