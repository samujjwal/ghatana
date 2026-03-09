/**
 * Breakpoint tokens for the design system
 * 
 * These tokens define the responsive breakpoints for the application,
 * following a mobile-first approach.
 */

/**
 * Breakpoint values in pixels
 * 
 * Mobile-first approach: Design for mobile, enhance for larger screens.
 * Use theme.breakpoints.up() for progressive enhancement.
 */
export const breakpoints = {
  /**
   * Extra small devices (phones)
   * @default 0px
   */
  xs: 0,
  
  /**
   * Small devices (large phones, small tablets)
   * @default 600px
   */
  sm: 600,
  
  /**
   * Medium devices (tablets, small laptops)
   * @default 960px
   */
  md: 960,
  
  /**
   * Large devices (desktops)
   * @default 1280px
   */
  lg: 1280,
  
  /**
   * Extra large devices (large desktops)
   * @default 1920px
   */
  xl: 1920,
};

/**
 * Semantic breakpoint aliases
 */
export const semanticBreakpoints = {
  mobile: breakpoints.xs,
  tablet: breakpoints.sm,
  desktop: breakpoints.md,
  wide: breakpoints.lg,
  ultraWide: breakpoints.xl,
};

/**
 * Media query helpers
 * 
 * @example
 * ```css
 * @media ${mediaQueries.up('md')} {
 *   // Styles for medium screens and up
 * }
 * ```
 */
export const mediaQueries = {
  /**
   * Min-width media query
   */
  up: (breakpoint: keyof typeof breakpoints) => 
    `(min-width: ${breakpoints[breakpoint]}px)`,
  
  /**
   * Max-width media query
   */
  down: (breakpoint: keyof typeof breakpoints) => 
    `(max-width: ${breakpoints[breakpoint] - 1}px)`,
  
  /**
   * Between two breakpoints
   */
  between: (min: keyof typeof breakpoints, max: keyof typeof breakpoints) => 
    `(min-width: ${breakpoints[min]}px) and (max-width: ${breakpoints[max] - 1}px)`,
  
  /**
   * Exact breakpoint match
   */
  only: (breakpoint: keyof typeof breakpoints) => {
    const keys = Object.keys(breakpoints) as (keyof typeof breakpoints)[];
    const index = keys.indexOf(breakpoint);
    const nextBreakpoint = keys[index + 1];
    
    if (!nextBreakpoint) {
      return `(min-width: ${breakpoints[breakpoint]}px)`;
    }
    
    return `(min-width: ${breakpoints[breakpoint]}px) and (max-width: ${breakpoints[nextBreakpoint] - 1}px)`;
  },
};

/**
 * Container max-widths at different breakpoints
 */
export const containerMaxWidths = {
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  '2xl': 1536,
};

/**
 * Touch target minimum sizes (WCAG 2.1 AA compliance)
 */
export const touchTargets = {
  /**
   * Minimum touch target size for mobile (44x44px)
   * WCAG 2.1 Level AA requirement
   */
  minimum: 44,
  
  /**
   * Recommended touch target size for better UX (48x48px)
   */
  recommended: 48,
  
  /**
   * Small touch targets (use sparingly, 36x36px)
   * Only for dense interfaces where space is critical
   */
  small: 36,
};
