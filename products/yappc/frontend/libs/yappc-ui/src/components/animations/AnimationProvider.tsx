/**
 * Animation Provider
 *
 * Provides animation configuration and context for the application.
 * Integrates with Framer Motion and respects user preferences.
 *
 * @module animations/AnimationProvider
 */

import { MotionConfig } from 'framer-motion';
import React, { createContext, useContext, useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 *
 */
export interface AnimationTokens {
  duration: {
    instant: number;
    fast: number;
    normal: number;
    slow: number;
    slower: number;
  };
  easing: {
    easeIn: string;
    easeOut: string;
    easeInOut: string;
    linear: string;
    spring: string;
  };
}

/**
 *
 */
export interface AnimationConfig {
  /**
   * Animation tokens
   */
  tokens: AnimationTokens;

  /**
   * Respect reduced motion preference
   */
  reducedMotion: boolean;

  /**
   * Global animation duration multiplier
   */
  durationMultiplier: number;

  /**
   * Enable animations globally
   */
  enabled: boolean;
}

/**
 *
 */
export interface AnimationProviderProps {
  children: React.ReactNode;
  config?: Partial<AnimationConfig>;
}

// ============================================================================
// Default Configuration
// ============================================================================

export const defaultAnimationTokens: AnimationTokens = {
  duration: {
    instant: 100,
    fast: 200,
    normal: 300,
    slow: 500,
    slower: 800,
  },
  easing: {
    easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
    easeOut: 'cubic-bezier(0, 0, 0.2, 1)',
    easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
    linear: 'linear',
    spring: 'spring(1, 100, 10, 0)',
  },
};

const defaultConfig: AnimationConfig = {
  tokens: defaultAnimationTokens,
  reducedMotion: false,
  durationMultiplier: 1,
  enabled: true,
};

// ============================================================================
// Context
// ============================================================================

const AnimationContext = createContext<AnimationConfig>(defaultConfig);

/**
 *
 */
export function useAnimationConfig(): AnimationConfig {
  return useContext(AnimationContext);
}

// ============================================================================
// Animation Provider Component
// ============================================================================

/**
 * Provider for animation configuration
 *
 * @example
 * <AnimationProvider config={{ durationMultiplier: 0.5 }}>
 *   <App />
 * </AnimationProvider>
 */
export const AnimationProvider: React.FC<AnimationProviderProps> = ({
  children,
  config: userConfig,
}) => {
  // Check for reduced motion preference
  const prefersReducedMotion = usePrefersReducedMotion();

  // Merge configuration
  const config = useMemo<AnimationConfig>(() => {
    const merged = {
      ...defaultConfig,
      ...userConfig,
      tokens: {
        ...defaultConfig.tokens,
        ...userConfig?.tokens,
      },
    };

    // Override with reduced motion preference
    if (prefersReducedMotion) {
      merged.reducedMotion = true;
      merged.durationMultiplier = 0.01; // Nearly instant
    }

    return merged;
  }, [userConfig, prefersReducedMotion]);

  return (
    <AnimationContext.Provider value={config}>
      <MotionConfig
        reducedMotion={config.reducedMotion ? 'always' : 'never'}
        transition={{
          duration: config.tokens.duration.normal / 1000 * config.durationMultiplier,
          ease: config.tokens.easing.easeInOut,
        }}
      >
        {children}
      </MotionConfig>
    </AnimationContext.Provider>
  );
};

// ============================================================================
// Hooks
// ============================================================================

/**
 * Hook to check if user prefers reduced motion
 */
export function usePrefersReducedMotion(): boolean {
  const [prefersReducedMotion, setPrefersReducedMotion] = React.useState(false);

  React.useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    setPrefersReducedMotion(mediaQuery.matches);

    const handler = (event: MediaQueryListEvent) => {
      setPrefersReducedMotion(event.matches);
    };

    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, []);

  return prefersReducedMotion;
}

/**
 * Hook to get animation duration with multiplier applied
 */
export function useAnimationDuration(
  duration: keyof AnimationTokens['duration'] | number
): number {
  const config = useAnimationConfig();

  const baseDuration = typeof duration === 'number'
    ? duration
    : config.tokens.duration[duration];

  return baseDuration * config.durationMultiplier;
}

/**
 * Hook to check if animations are enabled
 */
export function useAnimationsEnabled(): boolean {
  const config = useAnimationConfig();
  return config.enabled && !config.reducedMotion;
}
