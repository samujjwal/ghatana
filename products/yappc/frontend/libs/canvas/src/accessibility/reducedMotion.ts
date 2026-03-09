/**
 * Reduced Motion Support for Canvas
 *
 * Respects user's `prefers-reduced-motion` setting and provides
 * controls for disabling animations and transitions.
 *
 * @module canvas/accessibility/reducedMotion
 */

import { useState, useEffect, useCallback } from 'react';

/**
 * Animation configuration that respects reduced motion preferences
 */
export interface AnimationConfig {
  /** Enable/disable animations */
  enabled: boolean;
  /** Animation duration (ms) */
  duration: number;
  /** Easing function */
  easing: string;
  /** Whether to respect system preference */
  respectSystemPreference: boolean;
}

/**
 * Default animation config
 */
export const DEFAULT_ANIMATION_CONFIG: AnimationConfig = {
  enabled: true,
  duration: 300,
  easing: 'ease-in-out',
  respectSystemPreference: true,
};

/**
 * Check if user prefers reduced motion
 */
export function prefersReducedMotion(): boolean {
  if (typeof window === 'undefined') return false;
  
  const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  return mediaQuery.matches;
}

/**
 * React hook for detecting reduced motion preference
 *
 * @returns Boolean indicating if reduced motion is preferred
 *
 * @example
 * ```tsx
 * function AnimatedComponent() {
 *   const reducedMotion = useReducedMotion();
 *
 *   return (
 *     <div
 *       style={{
 *         transition: reducedMotion ? 'none' : 'all 300ms ease'
 *       }}
 *     >
 *       Content
 *     </div>
 *   );
 * }
 * ```
 */
export function useReducedMotion(): boolean {
  const [reduced, setReduced] = useState(prefersReducedMotion);

  useEffect(() => {
    if (typeof window === 'undefined') return undefined;

    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    
    const handleChange = () => {
      setReduced(mediaQuery.matches);
    };

    // Modern API
    if (mediaQuery.addEventListener) {
      mediaQuery.addEventListener('change', handleChange);
      return () => mediaQuery.removeEventListener('change', handleChange);
    }
    
    // Legacy API (Safari < 14)
    if (mediaQuery.addListener) {
      mediaQuery.addListener(handleChange);
      return () => mediaQuery.removeListener(handleChange);
    }
    
    return undefined;
  }, []);

  return reduced;
}

/**
 * Get animation duration based on reduced motion preference
 *
 * @param normalDuration - Normal animation duration (ms)
 * @param reducedDuration - Reduced duration or 0 to disable (default: 0)
 * @returns Appropriate duration based on preference
 *
 * @example
 * ```tsx
 * const duration = getAnimationDuration(300, 100);
 * // Returns 100ms if reduced motion preferred, 300ms otherwise
 * ```
 */
export function getAnimationDuration(
  normalDuration: number,
  reducedDuration: number = 0
): number {
  return prefersReducedMotion() ? reducedDuration : normalDuration;
}

/**
 * React hook for responsive animation configuration
 *
 * @param config - Animation configuration
 * @returns Adjusted config based on reduced motion preference
 *
 * @example
 * ```tsx
 * function CanvasTransition() {
 *   const animConfig = useAnimationConfig({
 *     enabled: true,
 *     duration: 300,
 *     easing: 'ease-in-out'
 *   });
 *
 *   return (
 *     <motion.div
 *       animate={{ opacity: 1 }}
 *       transition={{
 *         duration: animConfig.duration / 1000,
 *         ease: animConfig.easing
 *       }}
 *     />
 *   );
 * }
 * ```
 */
export function useAnimationConfig(
  config: Partial<AnimationConfig> = {}
): AnimationConfig {
  const reducedMotion = useReducedMotion();
  const fullConfig = { ...DEFAULT_ANIMATION_CONFIG, ...config };

  if (fullConfig.respectSystemPreference && reducedMotion) {
    return {
      ...fullConfig,
      enabled: false,
      duration: 0,
    };
  }

  return fullConfig;
}

/**
 * CSS transition string that respects reduced motion
 *
 * @param property - CSS property to transition
 * @param duration - Duration in ms
 * @param easing - Easing function
 * @returns CSS transition string or 'none'
 *
 * @example
 * ```tsx
 * <div style={{ transition: getTransition('all', 300, 'ease') }}>
 *   Content
 * </div>
 * ```
 */
export function getTransition(
  property: string = 'all',
  duration: number = 300,
  easing: string = 'ease'
): string {
  if (prefersReducedMotion()) {
    return 'none';
  }
  return `${property} ${duration}ms ${easing}`;
}

/**
 * React hook for safe CSS transitions
 *
 * @param property - CSS property
 * @param duration - Duration in ms
 * @param easing - Easing function
 * @returns Transition string respecting reduced motion
 */
export function useTransition(
  property: string = 'all',
  duration: number = 300,
  easing: string = 'ease'
): string {
  const reducedMotion = useReducedMotion();
  
  if (reducedMotion) {
    return 'none';
  }
  
  return `${property} ${duration}ms ${easing}`;
}

/**
 * Motion-safe spring configuration for animations
 *
 * @param config - Spring configuration
 * @returns Adjusted config for reduced motion
 */
export interface SpringConfig {
  tension?: number;
  friction?: number;
  mass?: number;
  velocity?: number;
}

/**
 *
 */
export function getSpringConfig(config: SpringConfig = {}): SpringConfig {
  if (prefersReducedMotion()) {
    // Instant spring (no animation)
    return {
      tension: 300,
      friction: 10,
      mass: 0.1,
      velocity: 0,
    };
  }
  
  return {
    tension: 170,
    friction: 26,
    mass: 1,
    velocity: 0,
    ...config,
  };
}

/**
 * Canvas-specific animation presets
 */
export const CANVAS_ANIMATIONS = {
  /** Smooth pan animation */
  PAN: {
    normal: { duration: 300, easing: 'ease-out' },
    reduced: { duration: 0, easing: 'linear' },
  },
  /** Zoom transition */
  ZOOM: {
    normal: { duration: 250, easing: 'ease-in-out' },
    reduced: { duration: 0, easing: 'linear' },
  },
  /** Node drag animation */
  DRAG: {
    normal: { duration: 0, easing: 'linear' }, // Always instant during drag
    reduced: { duration: 0, easing: 'linear' },
  },
  /** Selection highlight */
  SELECT: {
    normal: { duration: 150, easing: 'ease' },
    reduced: { duration: 0, easing: 'linear' },
  },
  /** Fit-to-screen animation */
  FIT_VIEW: {
    normal: { duration: 400, easing: 'ease-in-out' },
    reduced: { duration: 100, easing: 'linear' }, // Very brief feedback
  },
  /** Edge drawing animation */
  EDGE_DRAW: {
    normal: { duration: 200, easing: 'ease-out' },
    reduced: { duration: 0, easing: 'linear' },
  },
} as const;

/**
 * Get canvas animation config
 *
 * @param type - Animation type
 * @returns Animation config respecting reduced motion
 */
export function getCanvasAnimation(
  type: keyof typeof CANVAS_ANIMATIONS
): { duration: number; easing: string } {
  const preset = CANVAS_ANIMATIONS[type];
  return prefersReducedMotion() ? preset.reduced : preset.normal;
}

/**
 * React hook for canvas animations
 *
 * @example
 * ```tsx
 * function CanvasView() {
 *   const getAnimation = useCanvasAnimations();
 *
 *   const handleFitView = () => {
 *     const config = getAnimation('FIT_VIEW');
 *     fitView({ duration: config.duration });
 *   };
 * }
 * ```
 */
export function useCanvasAnimations() {
  const reducedMotion = useReducedMotion();

  return useCallback((type: keyof typeof CANVAS_ANIMATIONS) => {
    const preset = CANVAS_ANIMATIONS[type];
    return reducedMotion ? preset.reduced : preset.normal;
  }, [reducedMotion]);
}

/**
 * Zoom resiliency utilities for high zoom levels
 */

/**
 * Check if zoom level is within safe bounds
 */
export function isZoomLevelSafe(zoom: number): boolean {
  // Safe zoom range: 10% to 400%
  return zoom >= 0.1 && zoom <= 4;
}

/**
 * Clamp zoom to safe bounds (for accessibility, not viewport)
 * Note: Renamed to avoid conflict with viewport clampZoom
 */
export function clampZoomLevel(zoom: number, min: number = 0.1, max: number = 4): number {
  return Math.max(min, Math.min(max, zoom));
}

/**
 * Get responsive font size based on zoom
 *
 * @param baseSize - Base font size in pixels
 * @param zoom - Current zoom level
 * @returns Adjusted font size
 */
export function getResponsiveFontSize(baseSize: number, zoom: number): number {
  // Ensure text remains readable at extreme zooms
  const clampedZoom = clampZoomLevel(zoom, 0.5, 2);
  return baseSize * clampedZoom;
}

/**
 * Check if browser zoom exceeds 200%
 */
export function isBrowserZoomHigh(): boolean {
  if (typeof window === 'undefined') return false;
  
  // Use window.devicePixelRatio as a proxy for zoom level
  // Note: This is an approximation and may not work perfectly across all browsers
  const ratio = window.devicePixelRatio || 1;
  
  // devicePixelRatio > 2 typically indicates high zoom or high-DPI display
  // We return false for high-DPI displays to avoid false positives
  return ratio > 2;
}

/**
 * React hook for zoom resiliency
 *
 * @returns Utilities for handling high zoom levels
 */
export function useZoomResiliency() {
  const [isHighZoom, setIsHighZoom] = useState(isBrowserZoomHigh);

  useEffect(() => {
    if (typeof window === 'undefined') return undefined;

    const checkZoom = () => {
      setIsHighZoom(isBrowserZoomHigh());
    };

    window.addEventListener('resize', checkZoom);
    return () => window.removeEventListener('resize', checkZoom);
  }, []);

  return {
    isHighZoom,
    isZoomSafe: isZoomLevelSafe,
    clampZoom: clampZoomLevel,
    getResponsiveFontSize,
  };
}
