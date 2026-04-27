/**
 * Design tokens index — YAPPC-specific tokens extending @ghatana/tokens.
 * Consumers should import from @yappc/ui (or @yappc/ui/components) to access these.
 * For platform-level tokens not overridden here, import directly from @ghatana/tokens.
 */
// Re-export YAPPC token modules consumed by the local theme implementation.
export * from './colors';
export * from './typography';
export * from './spacing';
export * from './shadows';
export * from './breakpoints';
export * from './transitions';
// YAPPC-specific local overrides (zIndex, shape, and YAPPC MUI-derived tokens)
export * from './zIndex';
// Named exports from shape to avoid conflicting star export for 'borderRadius'
// (which is also exported by @ghatana/tokens above). The YAPPC shape values
// take precedence; borderRadius is re-exported explicitly below via the alias block.
export { borderWidth, componentRadius, shapeVariants } from './shape';
// Backward-compatible named aliases (previously from @ghatana/yappc-tokens)
import { borderRadius } from './shape';
import { spacing } from './spacing';
import { fontSize, fontWeight } from '@ghatana/tokens';
// Re-export YAPPC-specific borderRadius (overrides @ghatana/tokens platform value)
export { borderRadius };
// Spacing aliases
export const spacingXs = spacing[1]; // 4px
export const spacingSm = spacing[2]; // 8px
export const spacingMd = spacing[4]; // 16px
export const spacingLg = spacing[6]; // 24px
export const spacingXl = spacing[8]; // 32px
// Border radius aliases
export const borderRadiusSm = borderRadius.sm; // 4px
export const borderRadiusMd = borderRadius.md; // 8px
export const borderRadiusLg = borderRadius.lg; // 12px
export const borderRadiusFull = borderRadius.full; // 9999px
// Font size aliases (rem)
export const fontSizeXs = fontSize.xs; // 0.75rem (12px)
export const fontSizeSm = fontSize.sm; // 0.875rem (14px)
export const fontSizeMd = fontSize.md; // 1rem (16px)
export const fontSizeLg = fontSize.lg; // 1.125rem (18px)
export const fontSizeXl = fontSize.xl; // 1.25rem (20px)
// Font weight aliases
export const fontWeightLight = fontWeight.light; // 300
export const fontWeightRegular = fontWeight.regular; // 400
export const fontWeightMedium = fontWeight.medium; // 500
export const fontWeightSemibold = fontWeight.semibold; // 600
export const fontWeightBold = fontWeight.bold; // 700
