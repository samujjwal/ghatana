/**
 * Animation utilities and presets
 */

import { prefersReducedMotion } from './a11y';

/**
 *
 */
export type EasingFunction = 'linear' | 'easeIn' | 'easeOut' | 'easeInOut' | 'easeInBack' | 'easeOutBack';

/**
 * Easing functions
 */
export const easings = {
  linear: 'linear',
  easeIn: 'cubic-bezier(0.4, 0, 1, 1)',
  easeOut: 'cubic-bezier(0, 0, 0.2, 1)',
  easeInOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
  easeInBack: 'cubic-bezier(0.6, -0.28, 0.735, 0.045)',
  easeOutBack: 'cubic-bezier(0.175, 0.885, 0.32, 1.275)',
};

/**
 * Animation durations
 */
export const durations = {
  fast: 150,
  normal: 250,
  slow: 350,
  slower: 500,
};

/**
 * Common animation presets
 */
export const animations = {
  fadeIn: {
    keyframes: {
      from: { opacity: 0 },
      to: { opacity: 1 },
    },
    duration: durations.normal,
    easing: easings.easeOut,
  },
  fadeOut: {
    keyframes: {
      from: { opacity: 1 },
      to: { opacity: 0 },
    },
    duration: durations.normal,
    easing: easings.easeIn,
  },
  slideInUp: {
    keyframes: {
      from: { transform: 'translateY(100%)', opacity: 0 },
      to: { transform: 'translateY(0)', opacity: 1 },
    },
    duration: durations.normal,
    easing: easings.easeOut,
  },
  slideInDown: {
    keyframes: {
      from: { transform: 'translateY(-100%)', opacity: 0 },
      to: { transform: 'translateY(0)', opacity: 1 },
    },
    duration: durations.normal,
    easing: easings.easeOut,
  },
  slideInLeft: {
    keyframes: {
      from: { transform: 'translateX(-100%)', opacity: 0 },
      to: { transform: 'translateX(0)', opacity: 1 },
    },
    duration: durations.normal,
    easing: easings.easeOut,
  },
  slideInRight: {
    keyframes: {
      from: { transform: 'translateX(100%)', opacity: 0 },
      to: { transform: 'translateX(0)', opacity: 1 },
    },
    duration: durations.normal,
    easing: easings.easeOut,
  },
  scaleIn: {
    keyframes: {
      from: { transform: 'scale(0.8)', opacity: 0 },
      to: { transform: 'scale(1)', opacity: 1 },
    },
    duration: durations.normal,
    easing: easings.easeOutBack,
  },
  scaleOut: {
    keyframes: {
      from: { transform: 'scale(1)', opacity: 1 },
      to: { transform: 'scale(0.8)', opacity: 0 },
    },
    duration: durations.normal,
    easing: easings.easeInBack,
  },
  bounce: {
    keyframes: {
      '0%, 100%': { transform: 'translateY(0)' },
      '50%': { transform: 'translateY(-25%)' },
    },
    duration: durations.slower,
    easing: easings.easeInOut,
  },
  pulse: {
    keyframes: {
      '0%, 100%': { opacity: 1 },
      '50%': { opacity: 0.5 },
    },
    duration: durations.slower,
    easing: easings.easeInOut,
  },
  shake: {
    keyframes: {
      '0%, 100%': { transform: 'translateX(0)' },
      '10%, 30%, 50%, 70%, 90%': { transform: 'translateX(-10px)' },
      '20%, 40%, 60%, 80%': { transform: 'translateX(10px)' },
    },
    duration: durations.slower,
    easing: easings.linear,
  },
  spin: {
    keyframes: {
      from: { transform: 'rotate(0deg)' },
      to: { transform: 'rotate(360deg)' },
    },
    duration: 1000,
    easing: easings.linear,
  },
};

/**
 * Get animation with reduced motion support
 */
export function getAnimation(
  animationName: keyof typeof animations,
  respectReducedMotion = true
): typeof animations[keyof typeof animations] | null {
  if (respectReducedMotion && prefersReducedMotion()) {
    return null;
  }
  return animations[animationName];
}

/**
 * Create CSS animation string
 */
export function createAnimationString(
  name: string,
  duration: number,
  easing: string = easings.easeOut,
  delay = 0,
  iterationCount: number | 'infinite' = 1,
  direction: 'normal' | 'reverse' | 'alternate' | 'alternate-reverse' = 'normal',
  fillMode: 'none' | 'forwards' | 'backwards' | 'both' = 'both'
): string {
  return `${name} ${duration}ms ${easing} ${delay}ms ${iterationCount} ${direction} ${fillMode}`;
}

/**
 * Generate keyframes CSS
 */
export function generateKeyframes(name: string, keyframes: Record<string, unknown>): string {
  const frames = Object.entries(keyframes)
    .map(([key, value]) => {
      const props = Object.entries(value)
        .map(([prop, val]) => `${prop}: ${val};`)
        .join(' ');
      return `${key} { ${props} }`;
    })
    .join(' ');
  
  return `@keyframes ${name} { ${frames} }`;
}

/**
 * Transition presets
 */
export const transitions = {
  all: (duration = durations.normal, easing: EasingFunction = 'easeInOut') =>
    `all ${duration}ms ${easings[easing]}`,
  
  color: (duration = durations.fast, easing: EasingFunction = 'easeInOut') =>
    `color ${duration}ms ${easings[easing]}`,
  
  background: (duration = durations.fast, easing: EasingFunction = 'easeInOut') =>
    `background-color ${duration}ms ${easings[easing]}`,
  
  transform: (duration = durations.normal, easing: EasingFunction = 'easeOut') =>
    `transform ${duration}ms ${easings[easing]}`,
  
  opacity: (duration = durations.normal, easing: EasingFunction = 'easeInOut') =>
    `opacity ${duration}ms ${easings[easing]}`,
};

/**
 * Spring animation config
 */
export interface SpringConfig {
  tension: number;
  friction: number;
  mass: number;
}

export const springPresets: Record<string, SpringConfig> = {
  default: { tension: 170, friction: 26, mass: 1 },
  gentle: { tension: 120, friction: 14, mass: 1 },
  wobbly: { tension: 180, friction: 12, mass: 1 },
  stiff: { tension: 210, friction: 20, mass: 1 },
  slow: { tension: 280, friction: 60, mass: 1 },
  molasses: { tension: 280, friction: 120, mass: 1 },
};
