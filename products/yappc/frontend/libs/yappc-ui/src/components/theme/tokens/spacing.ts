/**
 * Spacing tokens for the design system
 * 
 * These tokens define the spacing scale for the application,
 * with a consistent scale for margins, paddings, and gaps.
 */

// Base spacing unit (in pixels)
const BASE_UNIT = 4;

// Spacing scale (in pixels)
export const spacing = {
  0: 0,
  0.5: BASE_UNIT / 2, // 2px
  1: BASE_UNIT,       // 4px
  1.5: BASE_UNIT * 1.5, // 6px
  2: BASE_UNIT * 2,   // 8px
  2.5: BASE_UNIT * 2.5, // 10px
  3: BASE_UNIT * 3,   // 12px
  3.5: BASE_UNIT * 3.5, // 14px
  4: BASE_UNIT * 4,   // 16px
  5: BASE_UNIT * 5,   // 20px
  6: BASE_UNIT * 6,   // 24px
  7: BASE_UNIT * 7,   // 28px
  8: BASE_UNIT * 8,   // 32px
  9: BASE_UNIT * 9,   // 36px
  10: BASE_UNIT * 10, // 40px
  12: BASE_UNIT * 12, // 48px
  14: BASE_UNIT * 14, // 56px
  16: BASE_UNIT * 16, // 64px
  20: BASE_UNIT * 20, // 80px
  24: BASE_UNIT * 24, // 96px
  28: BASE_UNIT * 28, // 112px
  32: BASE_UNIT * 32, // 128px
  36: BASE_UNIT * 36, // 144px
  40: BASE_UNIT * 40, // 160px
  44: BASE_UNIT * 44, // 176px
  48: BASE_UNIT * 48, // 192px
  52: BASE_UNIT * 52, // 208px
  56: BASE_UNIT * 56, // 224px
  60: BASE_UNIT * 60, // 240px
  64: BASE_UNIT * 64, // 256px
  72: BASE_UNIT * 72, // 288px
  80: BASE_UNIT * 80, // 320px
  96: BASE_UNIT * 96, // 384px
};

// Semantic spacing (in pixels)
export const semanticSpacing = {
  // Size aliases (most commonly used)
  xs: spacing[1],    // 4px
  sm: spacing[2],    // 8px
  md: spacing[4],    // 16px
  lg: spacing[6],    // 24px
  xl: spacing[8],    // 32px
  xxl: spacing[12],  // 48px
  
  // Component internal spacing
  componentPadding: spacing[4], // 16px
  componentGap: spacing[2],     // 8px
  
  // Layout spacing
  layoutGap: spacing[6],        // 24px
  sectionGap: spacing[10],      // 40px
  
  // Form spacing
  formGap: spacing[4],          // 16px
  formGroupGap: spacing[6],     // 24px
  
  // Content spacing
  contentGap: spacing[4],       // 16px
  paragraphGap: spacing[3],     // 12px
  
  // Insets
  insetXs: spacing[1],          // 4px
  insetSm: spacing[2],          // 8px
  insetMd: spacing[4],          // 16px
  insetLg: spacing[6],          // 24px
  insetXl: spacing[8],          // 32px
};

// Density variants
export const density = {
  comfortable: {
    componentPadding: semanticSpacing.componentPadding,
    componentGap: semanticSpacing.componentGap,
    layoutGap: semanticSpacing.layoutGap,
  },
  compact: {
    componentPadding: spacing[3], // 12px
    componentGap: spacing[1.5],   // 6px
    layoutGap: spacing[4],        // 16px
  },
};
