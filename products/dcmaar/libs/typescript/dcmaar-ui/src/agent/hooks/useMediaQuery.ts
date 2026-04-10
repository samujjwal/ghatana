import { useState, useEffect } from 'react';

export type MediaQuery = `(max-width: ${number}px)` | `(min-width: ${number}px)` | string;

/**
 * A custom React hook that tracks the state of a CSS media query.
 * @param query - The media query to match against.
 * @returns A boolean indicating whether the media query matches.
 *
 * @example
 * const isMobile = useMediaQuery('(max-width: 768px)');
 * 
 * return (
 *   <div>
 *     {isMobile ? 'Mobile view' : 'Desktop view'}
 *   </div>
 * );
 */
export function useMediaQuery(query: MediaQuery): boolean {
  const [matches, setMatches] = useState<boolean>(false);

  useEffect(() => {
    // Only run on the client side
    if (typeof window === 'undefined') {
      return;
    }

    const media = window.matchMedia(query);
    
    // Set the initial value
    setMatches(media.matches);
    
    // Create event listener
    const listener = (event: MediaQueryListEvent) => {
      setMatches(event.matches);
    };
    
    // Add listener for changes
    if (media.addEventListener) {
      media.addEventListener('change', listener);
    } else {
      // Fallback for older browsers
      media.addListener(listener);
    }
    
    // Clean up
    return () => {
      if (media.removeEventListener) {
        media.removeEventListener('change', listener);
      } else {
        media.removeListener(listener);
      }
    };
  }, [query]);

  return matches;
}

/**
 * A set of common media query breakpoints.
 */
export const breakpoints = {
  sm: '(min-width: 640px)',
  md: '(min-width: 768px)',
  lg: '(min-width: 1024px)',
  xl: '(min-width: 1280px)',
  '2xl': '(min-width: 1536px)',
} as const;

/**
 * A set of custom hooks for common breakpoints.
 */
export function useBreakpoints() {
  return {
    isSm: useMediaQuery(breakpoints.sm),
    isMd: useMediaQuery(breakpoints.md),
    isLg: useMediaQuery(breakpoints.lg),
    isXl: useMediaQuery(breakpoints.xl),
    is2Xl: useMediaQuery(breakpoints['2xl']),
  };
}
