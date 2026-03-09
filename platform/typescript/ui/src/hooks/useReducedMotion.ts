import { useState, useEffect } from 'react';

/**
 * Motion Preferences Hook
 * 
 * Detect and respond to user's motion preferences.
 * Respects prefers-reduced-motion media query.
 * 
 * @doc.type hook
 * @doc.purpose Accessible animation control
 * @doc.layer core
 * @doc.pattern Accessibility Hook
 * 
 * @example
 * ```tsx
 * function Component() {
 *   const prefersReducedMotion = useReducedMotion();
 *   
 *   return (
 *     <div className={prefersReducedMotion ? '' : 'animate-fadeIn'}>
 *       Content
 *     </div>
 *   );
 * }
 * ```
 */
export function useReducedMotion(): boolean {
  const [prefersReducedMotion, setPrefersReducedMotion] = useState(false);

  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    
    // Set initial value
    setPrefersReducedMotion(mediaQuery.matches);

    // Listen for changes
    const handleChange = (event: MediaQueryListEvent) => {
      setPrefersReducedMotion(event.matches);
    };

    // Modern browsers
    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', handleChange);
      return () => mediaQuery.removeEventListener('change', handleChange);
    }
    // Legacy browsers
    else {
      mediaQuery.addListener(handleChange);
      return () => mediaQuery.removeListener(handleChange);
    }
  }, []);

  return prefersReducedMotion;
}

/**
 * Animation Classes Hook
 * 
 * Returns animation class string respecting motion preferences.
 * Automatically removes animations if user prefers reduced motion.
 * 
 * @example
 * ```tsx
 * function Component() {
 *   const animationClass = useAnimationClass('animate-fadeIn duration-300');
 *   // Returns '' if motion is reduced, 'animate-fadeIn duration-300' otherwise
 *   
 *   return <div className={animationClass}>Content</div>;
 * }
 * ```
 */
export function useAnimationClass(animationClass: string): string {
  const prefersReducedMotion = useReducedMotion();
  return prefersReducedMotion ? '' : animationClass;
}

/**
 * Safe Motion Hook
 * 
 * Returns appropriate animation value based on motion preferences.
 * Useful for inline styles and dynamic animations.
 * 
 * @example
 * ```tsx
 * function Component() {
 *   const duration = useSafeMotion(300, 0); // 300ms or 0ms
 *   const scale = useSafeMotion(1.1, 1.0);  // 1.1 or 1.0
 *   
 *   return (
 *     <motion.div
 *       animate={{ scale }}
 *       transition={{ duration: duration / 1000 }}
 *     >
 *       Content
 *     </motion.div>
 *   );
 * }
 * ```
 */
export function useSafeMotion<T>(
  withMotion: T,
  withoutMotion: T
): T {
  const prefersReducedMotion = useReducedMotion();
  return prefersReducedMotion ? withoutMotion : withMotion;
}

export default useReducedMotion;
