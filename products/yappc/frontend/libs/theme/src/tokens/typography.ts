/**
 * Typography tokens for the design system
 * 
 * These tokens define the typography styles for the application,
 * with semantic naming for consistent usage across components.
 */

// Font families
export const fontFamilies = {
  primary: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
  code: '"Fira Code", "Roboto Mono", "Courier New", monospace',
};

// Font weights
export const fontWeights = {
  light: 300,
  regular: 400,
  medium: 500,
  semiBold: 600,
  bold: 700,
};

// Font sizes (in rem)
export const fontSizes = {
  xs: 0.75,    // 12px
  sm: 0.875,   // 14px
  md: 1,       // 16px
  lg: 1.125,   // 18px
  xl: 1.25,    // 20px
  '2xl': 1.5,  // 24px
  '3xl': 1.875, // 30px
  '4xl': 2.25,  // 36px
  '5xl': 3,     // 48px
  '6xl': 3.75,  // 60px
};

// Line heights
export const lineHeights = {
  none: 1,
  tight: 1.25,
  snug: 1.375,
  normal: 1.5,
  relaxed: 1.625,
  loose: 2,
};

// Letter spacing
export const letterSpacing = {
  tighter: '-0.05em',
  tight: '-0.025em',
  normal: '0',
  wide: '0.025em',
  wider: '0.05em',
  widest: '0.1em',
};

// Typography variants
export const typographyVariants = {
  h1: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.bold,
    fontSize: `${fontSizes['5xl']}rem`,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.tight,
  },
  h2: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.bold,
    fontSize: `${fontSizes['4xl']}rem`,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.tight,
  },
  h3: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.semiBold,
    fontSize: `${fontSizes['3xl']}rem`,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.normal,
  },
  h4: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.semiBold,
    fontSize: `${fontSizes['2xl']}rem`,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.normal,
  },
  h5: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.semiBold,
    fontSize: `${fontSizes.xl}rem`,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.normal,
  },
  h6: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.semiBold,
    fontSize: `${fontSizes.lg}rem`,
    lineHeight: lineHeights.tight,
    letterSpacing: letterSpacing.normal,
  },
  subtitle1: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.medium,
    fontSize: `${fontSizes.lg}rem`,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.normal,
  },
  subtitle2: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.medium,
    fontSize: `${fontSizes.md}rem`,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.normal,
  },
  body1: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.regular,
    fontSize: `${fontSizes.md}rem`,
    lineHeight: lineHeights.relaxed,
    letterSpacing: letterSpacing.normal,
  },
  body2: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.regular,
    fontSize: `${fontSizes.sm}rem`,
    lineHeight: lineHeights.relaxed,
    letterSpacing: letterSpacing.normal,
  },
  button: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.medium,
    fontSize: `${fontSizes.sm}rem`,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.wide,
    textTransform: 'uppercase',
  },
  caption: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.regular,
    fontSize: `${fontSizes.xs}rem`,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.normal,
  },
  overline: {
    fontFamily: fontFamilies.primary,
    fontWeight: fontWeights.medium,
    fontSize: `${fontSizes.xs}rem`,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.widest,
    textTransform: 'uppercase',
  },
  code: {
    fontFamily: fontFamilies.code,
    fontWeight: fontWeights.regular,
    fontSize: `${fontSizes.sm}rem`,
    lineHeight: lineHeights.normal,
    letterSpacing: letterSpacing.normal,
  },
};
