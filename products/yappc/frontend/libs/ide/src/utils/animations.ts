/**
 * @ghatana/yappc-ide - Animation Utilities
 * 
 * Smooth animations and transitions for enhanced user experience
 * with spring physics, easing functions, and performance optimizations.
 * 
 * @doc.type module
 * @doc.purpose Animation utilities for IDE interactions
 * @doc.layer product
 * @doc.pattern Utility Module
 */

/**
 * Animation timing presets
 */
export const TIMING = {
  instant: '0ms',
  fast: '150ms',
  normal: '250ms',
  slow: '400ms',
  slower: '600ms',
} as const;

/**
 * Animation easing functions
 */
export const EASING = {
  // Linear
  linear: 'cubic-bezier(0, 0, 1, 1)',
  
  // Ease
  ease: 'cubic-bezier(0.25, 0.1, 0.25, 1)',
  easeIn: 'cubic-bezier(0.42, 0, 1, 1)',
  easeOut: 'cubic-bezier(0, 0, 0.58, 1)',
  easeInOut: 'cubic-bezier(0.42, 0, 0.58, 1)',
  
  // Custom spring-like easings
  spring: 'cubic-bezier(0.68, -0.55, 0.265, 1.55)',
  springGentle: 'cubic-bezier(0.25, 0.46, 0.45, 0.94)',
  bounce: 'cubic-bezier(0.68, -0.6, 0.32, 1.6)',
  
  // Material design inspired
  accelerate: 'cubic-bezier(0.4, 0, 1, 1)',
  decelerate: 'cubic-bezier(0, 0, 0.2, 1)',
  standard: 'cubic-bezier(0.4, 0, 0.2, 1)',
} as const;

/**
 * Animation variants for common UI patterns
 */
export const VARIANTS = {
  // Fade animations
  fadeIn: {
    from: { opacity: 0 },
    to: { opacity: 1 },
  },
  fadeOut: {
    from: { opacity: 1 },
    to: { opacity: 0 },
  },
  
  // Slide animations
  slideUp: {
    from: { transform: 'translateY(20px)', opacity: 0 },
    to: { transform: 'translateY(0)', opacity: 1 },
  },
  slideDown: {
    from: { transform: 'translateY(-20px)', opacity: 0 },
    to: { transform: 'translateY(0)', opacity: 1 },
  },
  slideLeft: {
    from: { transform: 'translateX(20px)', opacity: 0 },
    to: { transform: 'translateX(0)', opacity: 1 },
  },
  slideRight: {
    from: { transform: 'translateX(-20px)', opacity: 0 },
    to: { transform: 'translateX(0)', opacity: 1 },
  },
  
  // Scale animations
  scaleIn: {
    from: { transform: 'scale(0.9)', opacity: 0 },
    to: { transform: 'scale(1)', opacity: 1 },
  },
  scaleOut: {
    from: { transform: 'scale(1)', opacity: 1 },
    to: { transform: 'scale(0.9)', opacity: 0 },
  },
  
  // Bounce animations
  bounceIn: {
    '0%': { transform: 'scale(0.3)', opacity: 0 },
    '50%': { transform: 'scale(1.05)' },
    '70%': { transform: 'scale(0.9)' },
    '100%': { transform: 'scale(1)', opacity: 1 },
  },
  
  // Rotate animations
  rotateIn: {
    from: { transform: 'rotate(-180deg) scale(0.5)', opacity: 0 },
    to: { transform: 'rotate(0deg) scale(1)', opacity: 1 },
  },
} as const;

/**
 * CSS animation utilities
 */
export const css = {
  /**
   * Create a transition string
   */
  transition: (
    properties: string[],
    duration: keyof typeof TIMING = 'normal',
    easing: keyof typeof EASING = 'ease'
  ): string => {
    return properties
      .map(prop => `${prop} ${TIMING[duration]} ${EASING[easing]}`)
      .join(', ');
  },

  /**
   * Create animation keyframes
   */
  keyframes: (name: string, frames: Record<string, Record<string, string>>): string => {
    const keyframeRules = Object.entries(frames)
      .map(([percentage, styles]) => {
        const styleDeclarations = Object.entries(styles)
          .map(([property, value]) => `${property}: ${value}`)
          .join('; ');
        return `${percentage} { ${styleDeclarations} }`;
      })
      .join('\n');

    return `@keyframes ${name} {\n${keyframeRules}\n}`;
  },

  /**
   * Apply animation to an element
   */
  animate: (
    animation: string,
    duration: keyof typeof TIMING = 'normal',
    easing: keyof typeof EASING = 'ease',
    fillMode: 'none' | 'forwards' | 'backwards' | 'both' = 'none'
  ): Record<string, string> => {
    return {
      animation: `${animation} ${TIMING[duration]} ${EASING[easing]} ${fillMode}`,
    };
  },
};

/**
 * Spring animation utilities
 */
export const spring = {
  /**
   * Spring configuration presets
   */
  presets: {
    gentle: { tension: 280, friction: 60 },
    wobbly: { tension: 180, friction: 12 },
    stiff: { tension: 210, friction: 20 },
    slow: { tension: 280, friction: 120 },
    bouncy: { tension: 300, friction: 10 },
  },

  /**
   * Generate spring animation parameters
   */
  generate: (config: { tension: number; friction: number }) => {
    const { tension, friction } = config;
    
    // Calculate approximate duration based on spring physics
    const duration = Math.round(1000 * Math.sqrt(tension / friction));
    
    // Generate cubic-bezier approximation
    const damping = friction / (2 * Math.sqrt(tension));
    
    let easing: string;
    if (damping < 1) {
      // Underdamped (oscillatory)
      easing = 'cubic-bezier(0.68, -0.55, 0.265, 1.55)';
    } else if (damping === 1) {
      // Critically damped
      easing = 'cubic-bezier(0.25, 0.46, 0.45, 0.94)';
    } else {
      // Overdamped
      easing = 'cubic-bezier(0.4, 0, 0.2, 1)';
    }

    return {
      duration: `${duration}ms`,
      easing,
    };
  },
};

/**
 * Performance optimization utilities
 */
export const performance = {
  /**
   * Check if reduced motion is preferred
   */
  prefersReducedMotion: (): boolean => {
    if (typeof window === 'undefined') return false;
    return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  },

  /**
   * Get optimized animation settings based on user preferences
   */
  getOptimizedSettings: (
    duration: keyof typeof TIMING = 'normal',
    easing: keyof typeof EASING = 'ease'
  ) => {
    if (performance.prefersReducedMotion()) {
      return {
        duration: TIMING.instant,
        easing: EASING.linear,
      };
    }

    return {
      duration: TIMING[duration],
      easing: EASING[easing],
    };
  },

  /**
   * Create will-change hint for better performance
   */
  willChange: (properties: string[]): Record<string, string> => {
    return {
      willChange: properties.join(', '),
    };
  },

  /**
   * Optimize for GPU acceleration
   */
  gpuAccelerated: {
    transform: 'translateZ(0)',
    backfaceVisibility: 'hidden' as const,
    perspective: '1000px',
  },
};

/**
 * Animation hooks for React
 */
export const hooks = {
  /**
   * Create animated style object
   */
  animatedStyle: (
    baseStyles: Record<string, string> = {},
    animation?: {
      name: string;
      duration?: keyof typeof TIMING;
      easing?: keyof typeof EASING;
      fillMode?: 'none' | 'forwards' | 'backwards' | 'both';
    }
  ): Record<string, string> => {
    if (!animation) return baseStyles;

    const { duration = 'normal', easing = 'ease', fillMode = 'none' } = animation;

    return {
      ...baseStyles,
      ...css.animate(animation.name, duration as keyof typeof TIMING, easing as keyof typeof EASING, fillMode),
    };
  },

  /**
   * Create transition style object
   */
  transitionStyle: (
    properties: string[],
    duration: keyof typeof TIMING = 'normal',
    easing: keyof typeof EASING = 'ease'
  ): Record<string, string> => {
    const settings = performance.getOptimizedSettings(duration, easing);
    return {
      transition: properties
        .map(prop => `${prop} ${settings.duration} ${settings.easing}`)
        .join(', '),
    };
  },
};

/**
 * Common animation patterns
 */
export const patterns = {
  /**
   * Hover effect with scale and shadow
   */
  hover: {
    transition: hooks.transitionStyle(['transform', 'box-shadow'], 'fast', 'easeOut'),
    '&:hover': {
      transform: 'scale(1.02)',
      boxShadow: '0 10px 25px rgba(0, 0, 0, 0.15)',
    },
  },

  /**
   * Focus effect with outline and scale
   */
  focus: {
    transition: hooks.transitionStyle(['outline-offset', 'outline-color'], 'fast', 'easeOut'),
    '&:focus': {
      outlineOffset: '2px',
      outlineColor: '#3b82f6',
    },
  },

  /**
   * Loading spinner animation
   */
  spinner: {
    animation: 'spin 1s linear infinite',
    '@keyframes spin': {
      from: { transform: 'rotate(0deg)' },
      to: { transform: 'rotate(360deg)' },
    },
  },

  /**
   * Pulse animation for attention
   */
  pulse: {
    animation: 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
    '@keyframes pulse': {
      '0%, 100%': { opacity: 1 },
      '50%': { opacity: 0.5 },
    },
  },

  /**
   * Slide and fade entrance
   */
  slideFadeIn: {
    ...hooks.animatedStyle(
      {},
      { name: 'slideFadeIn', duration: 'normal', easing: 'easeOut', fillMode: 'both' }
    ),
    '@keyframes slideFadeIn': {
      from: {
        opacity: 0,
        transform: 'translateY(20px)',
      },
      to: {
        opacity: 1,
        transform: 'translateY(0)',
      },
    },
  },

  /**
   * Scale and fade entrance
   */
  scaleFadeIn: {
    ...hooks.animatedStyle(
      {},
      { name: 'scaleFadeIn', duration: 'fast', easing: 'spring', fillMode: 'both' }
    ),
    '@keyframes scaleFadeIn': {
      from: {
        opacity: 0,
        transform: 'scale(0.9)',
      },
      to: {
        opacity: 1,
        transform: 'scale(1)',
      },
    },
  },
};

/**
 * Utility functions for common animation tasks
 */
export const utils = {
  /**
   * Debounce rapid animations
   */
  debounceAnimation: <T extends (...args: unknown[]) => void>(
    fn: T,
    delay: number = 100
  ): ((...args: Parameters<T>) => void) => {
    let timeoutId: NodeJS.Timeout;
    return (...args: Parameters<T>) => {
      clearTimeout(timeoutId);
      timeoutId = setTimeout(() => fn(...args), delay);
    };
  },

  /**
   * Stagger animation for multiple elements
   */
  stagger: (
    baseDelay: number,
    index: number,
    staggerAmount: number = 50
  ): number => {
    return baseDelay + (index * staggerAmount);
  },

  /**
   * Create staggered animation styles
   */
  staggeredChildren: (
    baseDelay: number = 0,
    staggerAmount: number = 50
  ): Record<string, Record<string, string>> => {
    return Array.from({ length: 10 }, (_, index) => ({
      [`&:nth-child(${index + 1})`]: {
        animationDelay: `${baseDelay + (index * staggerAmount)}ms`,
      },
    })).reduce((acc, styles) => ({ ...acc, ...styles }), {});
  },

  /**
   * Check if element is in viewport for scroll-triggered animations
   */
  isInViewport: (element: HTMLElement, threshold: number = 0.1): boolean => {
    const rect = element.getBoundingClientRect();
    const windowHeight = window.innerHeight || document.documentElement.clientHeight;
    const windowWidth = window.innerWidth || document.documentElement.clientWidth;

    const verticalThreshold = windowHeight * threshold;
    const horizontalThreshold = windowWidth * threshold;

    return (
      rect.top >= -verticalThreshold &&
      rect.left >= -horizontalThreshold &&
      rect.bottom <= windowHeight + verticalThreshold &&
      rect.right <= windowWidth + horizontalThreshold
    );
  },
};

export default {
  TIMING,
  EASING,
  VARIANTS,
  css,
  spring,
  performance,
  hooks,
  patterns,
  utils,
};
