/**
 * Global Transition Tokens for Ghatana Platform
 *
 * Unified animation and transition system
 *
 * @migrated-from @ghatana/yappc-ui/tokens/transitions
 */

/**
 * Duration values in milliseconds
 */
export const durations = {
  /**
   * Instant transitions (no animation)
   */
  instant: 0,

  /**
   * Very fast transitions (75ms)
   * Use for micro-interactions
   */
  fastest: 75,

  /**
   * Fast transitions (150ms)
   * Use for simple hover states
   */
  fast: 150,

  /**
   * Normal transitions (250ms)
   * Default for most interactions
   */
  normal: 250,

  /**
   * Slow transitions (350ms)
   * Use for complex state changes
   */
  slow: 350,

  /**
   * Very slow transitions (500ms)
   * Use for page transitions or major layout changes
   */
  slowest: 500,
} as const;

/**
 * Easing functions for natural motion
 */
export const easings = {
  /**
   * Linear easing - constant speed
   */
  linear: 'linear',

  /**
   * Ease - gradual acceleration and deceleration
   * Best for general use
   */
  ease: 'ease',

  /**
   * Ease in - slow start, fast end
   */
  easeIn: 'cubic-bezier(0.4, 0, 1, 1)',

  /**
   * Ease out - fast start, slow end
   * Best for enter animations
   */
  easeOut: 'cubic-bezier(0, 0, 0.2, 1)',

  /**
   * Ease in-out - slow start and end
   * Best for state changes
   */
  easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',

  /**
   * Sharp - quick start and end
   * Use for temporary objects (tooltips, popovers)
   */
  sharp: 'cubic-bezier(0.4, 0, 0.6, 1)',

  /**
   * Emphasized - pronounced ease
   * Use for attention-grabbing animations
   */
  emphasized: 'cubic-bezier(0.2, 0, 0, 1)',

  /**
   * Bounce - playful bounce effect
   * Use sparingly for delight
   */
  bounce: 'cubic-bezier(0.68, -0.55, 0.265, 1.55)',
} as const;

/**
 * Common transition properties
 */
export const properties = {
  /**
   * All properties
   */
  all: 'all',

  /**
   * Color properties
   */
  colors: 'background-color, border-color, color, fill, stroke',

  /**
   * Opacity
   */
  opacity: 'opacity',

  /**
   * Shadow
   */
  shadow: 'box-shadow',

  /**
   * Transform
   */
  transform: 'transform',

  /**
   * Layout properties
   */
  layout: 'width, height, padding, margin',
} as const;

/**
 * Semantic transitions for common use cases
 */
export const transitions = {
  /**
   * Default transition for most elements
   */
  default: `${properties.all} ${durations.normal}ms ${easings.easeInOut}`,

  /**
   * Hover state transition
   */
  hover: `${properties.colors} ${durations.fast}ms ${easings.easeOut}, ${properties.shadow} ${durations.fast}ms ${easings.easeOut}`,

  /**
   * Focus state transition
   */
  focus: `${properties.colors} ${durations.fast}ms ${easings.easeOut}, box-shadow ${durations.fast}ms ${easings.easeOut}`,

  /**
   * Active/pressed state
   */
  active: `${properties.transform} ${durations.fastest}ms ${easings.sharp}`,

  /**
   * Fade in/out
   */
  fade: `${properties.opacity} ${durations.normal}ms ${easings.easeInOut}`,

  /**
   * Slide transitions
   */
  slide: `${properties.transform} ${durations.normal}ms ${easings.easeOut}`,

  /**
   * Scale transitions
   */
  scale: `${properties.transform} ${durations.fast}ms ${easings.easeOut}`,

  /**
   * Color transitions
   */
  color: `${properties.colors} ${durations.normal}ms ${easings.easeInOut}`,

  /**
   * Shadow transitions (for elevation changes)
   */
  elevation: `${properties.shadow} ${durations.normal}ms ${easings.easeOut}`,

  /**
   * Layout transitions (use sparingly - can cause reflows)
   */
  layout: `${properties.layout} ${durations.slow}ms ${easings.easeInOut}`,
} as const;

/**
 * Animation keyframes (for more complex animations)
 */
export const animations = {
  /**
   * Spin animation
   */
  spin: {
    keyframes: {
      from: { transform: 'rotate(0deg)' },
      to: { transform: 'rotate(360deg)' },
    },
    duration: durations.slowest,
    timing: easings.linear,
    iteration: 'infinite' as const,
  },

  /**
   * Pulse animation
   */
  pulse: {
    keyframes: {
      '0%, 100%': { opacity: 1 },
      '50%': { opacity: 0.5 },
    },
    duration: durations.slowest * 2,
    timing: easings.easeInOut,
    iteration: 'infinite' as const,
  },

  /**
   * Ping animation (expanding ring)
   */
  ping: {
    keyframes: {
      '0%': { transform: 'scale(1)', opacity: 1 },
      '75%, 100%': { transform: 'scale(2)', opacity: 0 },
    },
    duration: durations.slowest,
    timing: easings.easeOut,
    iteration: 'infinite' as const,
  },

  /**
   * Bounce animation
   */
  bounceIn: {
    keyframes: {
      '0%': { transform: 'scale(0)', opacity: 0 },
      '50%': { transform: 'scale(1.1)', opacity: 1 },
      '100%': { transform: 'scale(1)', opacity: 1 },
    },
    duration: durations.slow,
    timing: easings.bounce,
  },

  /**
   * Slide in from top
   */
  slideInTop: {
    keyframes: {
      from: { transform: 'translateY(-100%)', opacity: 0 },
      to: { transform: 'translateY(0)', opacity: 1 },
    },
    duration: durations.normal,
    timing: easings.easeOut,
  },

  /**
   * Slide in from bottom
   */
  slideInBottom: {
    keyframes: {
      from: { transform: 'translateY(100%)', opacity: 0 },
      to: { transform: 'translateY(0)', opacity: 1 },
    },
    duration: durations.normal,
    timing: easings.easeOut,
  },
} as const;

/**
 * Reduced motion preferences
 *
 * @example
 * ```css
 * @media (prefers-reduced-motion: reduce) {
 *   * {
 *     transition-duration: ${reducedMotion.duration}ms;
 *   }
 * }
 * ```
 */
export const reducedMotion = {
  /**
   * Duration for reduced motion (very short or none)
   */
  duration: durations.fastest,

  /**
   * Easing for reduced motion (simple)
   */
  easing: easings.linear,

  /**
   * Transition for reduced motion
   */
  transition: `all ${durations.fastest}ms ${easings.linear}`,
} as const;

// Type exports
export type DurationKey = keyof typeof durations;
export type EasingKey = keyof typeof easings;
export type PropertyKey = keyof typeof properties;
export type TransitionKey = keyof typeof transitions;
export type AnimationKey = keyof typeof animations;
