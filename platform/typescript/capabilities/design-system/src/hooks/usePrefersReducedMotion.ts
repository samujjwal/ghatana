import * as React from 'react';

const QUERY = '(prefers-reduced-motion: reduce)';

/**
 * React hook that reads OS level reduced motion preference.
 */
export function usePrefersReducedMotion(): boolean {
  const getPreference = React.useCallback(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return false;
    }

    return window.matchMedia(QUERY).matches;
  }, []);

  const [prefersReducedMotion, setPrefersReducedMotion] = React.useState(getPreference);

  React.useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return;
    }

    const mediaQuery = window.matchMedia(QUERY);

    const handler = (event: MediaQueryListEvent) => {
      setPrefersReducedMotion(event.matches);
    };

    if (typeof mediaQuery.addEventListener === 'function') {
      mediaQuery.addEventListener('change', handler);
      return () => mediaQuery.removeEventListener('change', handler);
    }

    // Legacy Safari
    mediaQuery.addListener(handler);
    return () => mediaQuery.removeListener(handler);
  }, [getPreference]);

  return prefersReducedMotion;
}
