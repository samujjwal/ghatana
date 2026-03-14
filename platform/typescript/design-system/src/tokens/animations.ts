/**
 * Animation Tokens
 * 
 * Standardized animation durations, easing curves, and keyframes
 * for consistent micro-interactions across the platform.
 * 
 * Respects prefers-reduced-motion for accessibility.
 * 
 * @doc.type tokens
 * @doc.purpose Unified animation system with motion preferences
 * @doc.layer core
 */

/**
 * Check if user prefers reduced motion
 * @returns true if user has requested reduced motion
 */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Get animation duration respecting user preferences
 * @param duration - Desired duration
 * @returns Duration value or '0ms' if motion is reduced
 */
export function getAnimationDuration(duration: string): string {
  return prefersReducedMotion() ? '0ms' : duration;
}

export const animations = {
  // Duration tokens
  duration: {
    fast: '150ms',      // Hover, focus, instant feedback
    base: '200ms',      // Default transitions
    slow: '300ms',      // Layout shifts, complex animations
    slower: '500ms',    // Page transitions, modals
    none: '0ms',        // Instant (no animation)
  },
  
  // Easing curves
  easing: {
    easeOut: 'cubic-bezier(0.16, 1, 0.3, 1)',      // Enter animations
    easeIn: 'cubic-bezier(0.7, 0, 0.84, 0)',       // Exit animations
    easeInOut: 'cubic-bezier(0.87, 0, 0.13, 1)',   // Bidirectional
    spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',   // Bouncy feel
  },
  
  // Tailwind CSS class utilities
  tw: {
    // Duration classes
    durationFast: 'duration-150',
    durationBase: 'duration-200',
    durationSlow: 'duration-300',
    durationSlower: 'duration-500',
    
    // Easing classes
    easeOut: 'ease-out',
    easeIn: 'ease-in',
    easeInOut: 'ease-in-out',
    
    // Common patterns
    transition: 'transition-all duration-200 ease-out',
    transitionColors: 'transition-colors duration-150 ease-out',
    transitionOpacity: 'transition-opacity duration-200 ease-out',
    transitionTransform: 'transition-transform duration-200 ease-out',
    
    // Named animations (require corresponding keyframes in Tailwind config)
    fadeIn: 'animate-fadeIn',
    fadeOut: 'animate-fadeOut',
    slideUp: 'animate-slideUp',
    slideDown: 'animate-slideDown',
    slideLeft: 'animate-slideLeft',
    slideRight: 'animate-slideRight',
    scaleIn: 'animate-scaleIn',
    scaleOut: 'animate-scaleOut',
    spin: 'animate-spin',
    pulse: 'animate-pulse',
    bounce: 'animate-bounce',
  },
} as const;

// Keyframe definitions for Tailwind config
export const keyframes = {
  fadeIn: {
    '0%': { opacity: '0' },
    '100%': { opacity: '1' },
  },
  fadeOut: {
    '0%': { opacity: '1' },
    '100%': { opacity: '0' },
  },
  slideUp: {
    '0%': { transform: 'translateY(10px)', opacity: '0' },
    '100%': { transform: 'translateY(0)', opacity: '1' },
  },
  slideDown: {
    '0%': { transform: 'translateY(-10px)', opacity: '0' },
    '100%': { transform: 'translateY(0)', opacity: '1' },
  },
  slideLeft: {
    '0%': { transform: 'translateX(10px)', opacity: '0' },
    '100%': { transform: 'translateX(0)', opacity: '1' },
  },
  slideRight: {
    '0%': { transform: 'translateX(-10px)', opacity: '0' },
    '100%': { transform: 'translateX(0)', opacity: '1' },
  },
  scaleIn: {
    '0%': { transform: 'scale(0.95)', opacity: '0' },
    '100%': { transform: 'scale(1)', opacity: '1' },
  },
  scaleOut: {
    '0%': { transform: 'scale(1)', opacity: '1' },
    '100%': { transform: 'scale(0.95)', opacity: '0' },
  },
} as const;

export type AnimationDuration = keyof typeof animations.duration;
export type AnimationEasing = keyof typeof animations.easing;
export type AnimationKeyframe = keyof typeof keyframes;
