/**
 * Responsive Utilities
 *
 * Framework-agnostic responsive utilities
 * React hooks are optional and only used when React is available
 *
 * @migrated-from @ghatana/yappc-ui/utils/responsive
 */

import type { Platform } from './platform';

/**
 * Breakpoint names matching @ghatana/tokens
 */
export type Breakpoint = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

/**
 * Breakpoint values in pixels (matching @ghatana/tokens)
 */
export const breakpointValues = {
  xs: 0,
  sm: 600,
  md: 960,
  lg: 1280,
  xl: 1920,
} as const;

/**
 * Check if window width matches a breakpoint
 *
 * @param breakpoint - Breakpoint to check
 * @param direction - 'up' (min-width) or 'down' (max-width)
 * @returns True if window width matches
 *
 * @example
 * ```typescript
 * if (matchesBreakpoint('md', 'up')) {
 *   // Window is >= 960px
 * }
 * ```
 */
export function matchesBreakpoint(
  breakpoint: Breakpoint,
  direction: 'up' | 'down' = 'up'
): boolean {
  if (typeof window === 'undefined') return false;

  const width = window.innerWidth;
  const value = breakpointValues[breakpoint];

  if (direction === 'up') {
    return width >= value;
  } else {
    // For 'down', use the next breakpoint's value - 1
    const keys = Object.keys(breakpointValues) as Breakpoint[];
    const index = keys.indexOf(breakpoint);
    const nextKey = keys[index + 1];
    const maxWidth = nextKey ? breakpointValues[nextKey] - 1 : Infinity;
    return width < value || width <= maxWidth;
  }
}

/**
 * Check if window width is between two breakpoints
 *
 * @param min - Minimum breakpoint
 * @param max - Maximum breakpoint
 * @returns True if window width is in range
 */
export function matchesBetween(min: Breakpoint, max: Breakpoint): boolean {
  if (typeof window === 'undefined') return false;

  const width = window.innerWidth;
  const minValue = breakpointValues[min];
  const maxValue = breakpointValues[max];

  return width >= minValue && width < maxValue;
}

/**
 * Get current breakpoint
 *
 * @returns Current breakpoint name
 */
export function getCurrentBreakpoint(): Breakpoint {
  if (typeof window === 'undefined') return 'md';

  const width = window.innerWidth;

  if (width >= breakpointValues.xl) return 'xl';
  if (width >= breakpointValues.lg) return 'lg';
  if (width >= breakpointValues.md) return 'md';
  if (width >= breakpointValues.sm) return 'sm';
  return 'xs';
}

/**
 * Helper function to get platform-specific values
 *
 * @param platform - Current platform
 * @param values - Platform-specific values
 * @returns Value for current platform
 *
 * @example
 * ```typescript
 * const padding = getPlatformValue('mobile', {
 *   base: 16,
 *   desktop: 24,
 *   mobile: 12,
 * }); // Returns 12
 * ```
 */
export function getPlatformValue<T>(
  platform: Platform,
  values: {
    base?: T;
    desktop?: T;
    mobile?: T;
    web?: T;
  }
): T | undefined {
  // Platform-specific value takes precedence
  if (platform === 'desktop' && values.desktop !== undefined) {
    return values.desktop;
  }
  if (platform === 'mobile' && values.mobile !== undefined) {
    return values.mobile;
  }
  if (platform === 'web' && values.web !== undefined) {
    return values.web;
  }

  // Fall back to base value
  return values.base;
}

/**
 * Helper function to get responsive value based on breakpoint
 *
 * @param breakpoint - Current breakpoint
 * @param values - Breakpoint-specific values
 * @returns Value for current breakpoint
 *
 * @example
 * ```typescript
 * const columns = getResponsiveValue('md', {
 *   base: 1,
 *   sm: 2,
 *   md: 3,
 *   lg: 4,
 * }); // Returns 3
 * ```
 */
export function getResponsiveValue<T>(
  breakpoint: Breakpoint,
  values: {
    base?: T;
    xs?: T;
    sm?: T;
    md?: T;
    lg?: T;
    xl?: T;
  }
): T | undefined {
  // Get the most specific value that applies
  const keys: Breakpoint[] = ['xl', 'lg', 'md', 'sm', 'xs'];
  const currentIndex = keys.indexOf(breakpoint);

  // Start from current breakpoint and go down
  for (let i = currentIndex; i < keys.length; i++) {
    const key = keys[i];
    if (values[key] !== undefined) {
      return values[key];
    }
  }

  // Fall back to base
  return values.base;
}

/**
 * Create a media query string
 *
 * @param breakpoint - Breakpoint name
 * @param direction - 'up' or 'down'
 * @returns Media query string
 *
 * @example
 * ```typescript
 * const query = createMediaQuery('md', 'up');
 * // Returns "(min-width: 960px)"
 * ```
 */
export function createMediaQuery(
  breakpoint: Breakpoint,
  direction: 'up' | 'down' = 'up'
): string {
  const value = breakpointValues[breakpoint];

  if (direction === 'up') {
    return `(min-width: ${value}px)`;
  } else {
    const keys = Object.keys(breakpointValues) as Breakpoint[];
    const index = keys.indexOf(breakpoint);
    const nextKey = keys[index + 1];
    const maxValue = nextKey ? breakpointValues[nextKey] - 1 : 9999;
    return `(max-width: ${maxValue}px)`;
  }
}

/**
 * React Hook: Use media query (only available when React is loaded)
 *
 * @param query - Media query string
 * @returns True if media query matches
 */
export function useMediaQuery(query: string): boolean {
  // This will be implemented when React is available
  // For now, return a fallback value
  if (typeof window === 'undefined') return false;
  if (typeof window.matchMedia === 'undefined') return false;

  try {
    return window.matchMedia(query).matches;
  } catch {
    return false;
  }
}

/**
 * Get responsive information object
 *
 * @returns Object with breakpoint flags
 */
export function getResponsiveInfo() {
  const breakpoint = getCurrentBreakpoint();

  return {
    breakpoint,
    isXs: breakpoint === 'xs',
    isSm: breakpoint === 'sm',
    isMd: breakpoint === 'md',
    isLg: breakpoint === 'lg',
    isXl: breakpoint === 'xl',
    isSmDown: ['xs', 'sm'].includes(breakpoint),
    isMdDown: ['xs', 'sm', 'md'].includes(breakpoint),
    isLgDown: ['xs', 'sm', 'md', 'lg'].includes(breakpoint),
    isSmUp: ['sm', 'md', 'lg', 'xl'].includes(breakpoint),
    isMdUp: ['md', 'lg', 'xl'].includes(breakpoint),
    isLgUp: ['lg', 'xl'].includes(breakpoint),
  };
}
