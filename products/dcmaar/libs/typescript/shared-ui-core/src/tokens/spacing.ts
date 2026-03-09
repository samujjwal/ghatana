/**
 * Shared Spacing Scale
 * Used across all DCMAAR apps
 */

export const SPACING = {
  xs: 4,
  sm: 8,
  md: 16,
  lg: 24,
  xl: 32,
  '2xl': 48,
  '3xl': 64,
} as const;

export type SpacingKey = keyof typeof SPACING;

/**
 * Get spacing value in pixels
 */
export function getSpacing(key: SpacingKey): number {
  return SPACING[key];
}

/**
 * Get spacing value as CSS string
 */
export function getSpacingCSS(key: SpacingKey): string {
  return `${SPACING[key]}px`;
}
