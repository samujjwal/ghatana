/**
 * Design tokens index file
 * 
 * This file exports all design tokens for the design system.
 * 
 * @packageDocumentation
 */

// Import tokens for backward-compatible exports
import { borderRadius } from './shape';
import { spacing } from './spacing';
import { fontSizes, fontWeights } from './typography';

// Core tokens
export * from './colors';
export * from './typography';
export * from './spacing';
export * from './shadows';
export * from './shape';
export * from './breakpoints';
export * from './transitions';
export * from './zIndex';

/**
 * Re-export commonly used tokens for convenience
 */
export {
  palette,
  lightColors,
  darkColors,
} from './colors';

export {
  spacing,
  semanticSpacing,
} from './spacing';

export {
  borderRadius,
  borderWidth,
  componentRadius,
} from './shape';

export {
  fontFamilies,
  fontWeights,
  fontSizes,
  lineHeights,
  typographyVariants,
} from './typography';

export {
  lightShadows,
  darkShadows,
  elevationLevels,
} from './shadows';

export {
  breakpoints,
  semanticBreakpoints,
  mediaQueries,
  containerMaxWidths,
  touchTargets,
} from './breakpoints';

export {
  durations,
  easings,
  transitions,
  animations,
  reducedMotion,
} from './transitions';

export {
  zIndex,
  semanticZIndex,
  componentZIndex,
  getZIndex,
  getRelativeZIndex,
} from './zIndex';

/**
 * Backward-compatible individual exports for migration from @ghatana/yappc-tokens
 * 
 * These provide the same names used in the old @ghatana/yappc-tokens package
 * to facilitate gradual migration. Components can import these directly.
 * 
 * @deprecated Prefer importing from specific token modules above
 */

// Spacing
export const spacingXs = spacing[1];     // 4px
export const spacingSm = spacing[2];     // 8px
export const spacingMd = spacing[4];     // 16px
export const spacingLg = spacing[6];     // 24px
export const spacingXl = spacing[8];     // 32px

// Border Radius
export const borderRadiusSm = borderRadius.sm;    // 4px
export const borderRadiusMd = borderRadius.md;    // 8px
export const borderRadiusLg = borderRadius.lg;    // 12px
export const borderRadiusFull = borderRadius.full; // 9999px

// Font Sizes (in rem, converted from our token system)
export const fontSizeXs = fontSizes.xs;     // 0.75rem (12px)
export const fontSizeSm = fontSizes.sm;     // 0.875rem (14px)
export const fontSizeMd = fontSizes.md;     // 1rem (16px)
export const fontSizeLg = fontSizes.lg;     // 1.125rem (18px)
export const fontSizeXl = fontSizes.xl;     // 1.25rem (20px)

// Font Weights
export const fontWeightLight = fontWeights.light;       // 300
export const fontWeightRegular = fontWeights.regular;   // 400
export const fontWeightMedium = fontWeights.medium;     // 500
export const fontWeightSemibold = fontWeights.semiBold; // 600
export const fontWeightBold = fontWeights.bold;         // 700
