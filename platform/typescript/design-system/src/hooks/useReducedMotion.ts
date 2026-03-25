import { usePrefersReducedMotion } from './usePrefersReducedMotion';

/**
 * Motion Preferences Hook
 *
 * Detects the user's OS-level motion preference via the
 * `(prefers-reduced-motion: reduce)` media query.
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
 *   return (
 *     <div className={prefersReducedMotion ? '' : 'animate-fadeIn'}>
 *       Content
 *     </div>
 *   );
 * }
 * ```
 */
export function useReducedMotion(): boolean {
  return usePrefersReducedMotion();
}

/**
 * Animation Classes Hook
 *
 * Returns an animation class string that respects the user's motion preference.
 * Returns an empty string when reduced-motion mode is active.
 *
 * @example
 * ```tsx
 * function Component() {
 *   const animationClass = useAnimationClass('animate-fadeIn duration-300');
 *   return <div className={animationClass}>Content</div>;
 * }
 * ```
 */
export function useAnimationClass(animationClass: string): string {
  return usePrefersReducedMotion() ? '' : animationClass;
}

/**
 * Safe Motion Hook
 *
 * Returns the appropriate value based on motion preference.
 * Useful for inline styles and dynamic animations.
 *
 * @example
 * ```tsx
 * function Component() {
 *   const duration = useSafeMotion(300, 0); // 300ms or 0ms
 *   const scale = useSafeMotion(1.1, 1.0);
 *
 *   return (
 *     <motion.div animate={{ scale }} transition={{ duration: duration / 1000 }}>
 *       Content
 *     </motion.div>
 *   );
 * }
 * ```
 */
export function useSafeMotion<T>(withMotion: T, withoutMotion: T): T {
  return usePrefersReducedMotion() ? withoutMotion : withMotion;
}

export default useReducedMotion;

