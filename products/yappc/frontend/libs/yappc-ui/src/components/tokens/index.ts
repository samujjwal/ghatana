/**
 * Design tokens index — exports all tokens from local source files.
 * Consumers should import from @yappc/ui (or @yappc/ui/components) to access these.
 */

// Core token modules
export * from './colors';
export * from './typography';
export * from './spacing';
export * from './shadows';
export * from './shape';
export * from './breakpoints';
export * from './transitions';
export * from './zIndex';

// Backward-compatible named aliases (previously from @ghatana/yappc-tokens)
import { borderRadius } from './shape';
import { spacing } from './spacing';
import { fontSizes, fontWeights } from './typography';

// Spacing aliases
export const spacingXs = spacing[1];   // 4px
export const spacingSm = spacing[2];   // 8px
export const spacingMd = spacing[4];   // 16px
export const spacingLg = spacing[6];   // 24px
export const spacingXl = spacing[8];   // 32px

// Border radius aliases
export const borderRadiusSm = borderRadius.sm;    // 4px
export const borderRadiusMd = borderRadius.md;    // 8px
export const borderRadiusLg = borderRadius.lg;    // 12px
export const borderRadiusFull = borderRadius.full; // 9999px

// Font size aliases (rem)
export const fontSizeXs = fontSizes.xs;   // 0.75rem (12px)
export const fontSizeSm = fontSizes.sm;   // 0.875rem (14px)
export const fontSizeMd = fontSizes.md;   // 1rem (16px)
export const fontSizeLg = fontSizes.lg;   // 1.125rem (18px)
export const fontSizeXl = fontSizes.xl;   // 1.25rem (20px)

// Font weight aliases
export const fontWeightLight    = fontWeights.light;    // 300
export const fontWeightRegular  = fontWeights.regular;  // 400
export const fontWeightMedium   = fontWeights.medium;   // 500
export const fontWeightSemibold = fontWeights.semiBold; // 600
export const fontWeightBold     = fontWeights.bold;     // 700

