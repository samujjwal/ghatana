import { useGlobalStateValue } from '@ghatana/yappc-ui';
import React from 'react';

import type { Platform } from './platform';

/**
 * Native matchMedia-based media query hook (replaces MUI's useMediaQuery/useTheme)
 */
const useMediaQuery = (query: string): boolean => {
  const [matches, setMatches] = React.useState(() => {
    if (typeof window === 'undefined') return false;
    return window.matchMedia(query).matches;
  });

  React.useEffect(() => {
    if (typeof window === 'undefined') return;
    const mql = window.matchMedia(query);
    const handler = (e: MediaQueryListEvent) => setMatches(e.matches);
    mql.addEventListener('change', handler);
    setMatches(mql.matches);
    return () => mql.removeEventListener('change', handler);
  }, [query]);

  return matches;
};

/**
 * Hook to get responsive breakpoint information
 * Uses native matchMedia with Tailwind-aligned breakpoints:
 * sm: 640px, md: 768px, lg: 1024px, xl: 1280px
 * @returns Object with boolean flags for different screen sizes
 */
export const useResponsive = () => {
  return {
    isXs: useMediaQuery('(max-width: 639px)'),
    isSm: useMediaQuery('(min-width: 640px) and (max-width: 767px)'),
    isMd: useMediaQuery('(min-width: 768px) and (max-width: 1023px)'),
    isLg: useMediaQuery('(min-width: 1024px) and (max-width: 1279px)'),
    isXl: useMediaQuery('(min-width: 1280px)'),
    isSmDown: useMediaQuery('(max-width: 767px)'),
    isMdDown: useMediaQuery('(max-width: 1023px)'),
    isLgDown: useMediaQuery('(max-width: 1279px)'),
    isSmUp: useMediaQuery('(min-width: 640px)'),
    isMdUp: useMediaQuery('(min-width: 768px)'),
    isLgUp: useMediaQuery('(min-width: 1024px)'),
  };
};

/**
 * Hook to get platform and responsive information
 * @returns Object with platform and responsive information
 */
export const usePlatformResponsive = () => {
  const platform = useGlobalStateValue<Platform>('store:platform');
  const responsive = useResponsive();
  
  return {
    platform,
    isDesktop: platform === 'desktop',
    isMobile: platform === 'mobile',
    isWeb: platform === 'web',
    isMobileView: responsive.isSmDown || platform === 'mobile',
    ...responsive,
  };
};

/**
 * Helper function to get platform-specific styles
 * @param platform Current platform
 * @param styles Platform-specific styles
 * @returns Merged styles for the current platform
 */
export const getPlatformStyles = (
  platform: Platform,
  styles: {
    base?: Record<string, unknown>;
    desktop?: Record<string, unknown>;
    mobile?: Record<string, unknown>;
    web?: Record<string, unknown>;
  }
) => {
  return {
    ...(styles.base || {}),
    ...(platform === 'desktop' && styles.desktop || {}),
    ...(platform === 'mobile' && styles.mobile || {}),
    ...(platform === 'web' && styles.web || {}),
  };
};

/**
 * Helper function to get responsive styles based on breakpoints.
 * Uses standard CSS media query strings aligned to Tailwind breakpoints.
 * @param styles Responsive styles
 * @returns Merged styles with media query keys
 */
export const getResponsiveStyles = (
  styles: {
    base?: Record<string, unknown>;
    xs?: Record<string, unknown>;
    sm?: Record<string, unknown>;
    md?: Record<string, unknown>;
    lg?: Record<string, unknown>;
    xl?: Record<string, unknown>;
  }
) => {
  return {
    ...(styles.base || {}),
    '@media (max-width: 639px)': styles.xs || {},
    '@media (min-width: 640px) and (max-width: 767px)': styles.sm || {},
    '@media (min-width: 768px) and (max-width: 1023px)': styles.md || {},
    '@media (min-width: 1024px) and (max-width: 1279px)': styles.lg || {},
    '@media (min-width: 1280px)': styles.xl || {},
  };
};
