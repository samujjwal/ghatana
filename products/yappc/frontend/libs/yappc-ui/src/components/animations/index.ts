/**
 * Animations Module
 *
 * Exports animation components and utilities for smooth transitions
 */

// Animation Provider
export {
  AnimationProvider,
  useAnimationConfig,
  usePrefersReducedMotion,
  useAnimationDuration,
  useAnimationsEnabled,
  defaultAnimationTokens,
} from './AnimationProvider';

export type {
  AnimationTokens,
  AnimationConfig,
  AnimationProviderProps,
} from './AnimationProvider';

// Page Transitions
export {
  PageTransition,
  FadeTransition,
  SlideLeftTransition,
  SlideRightTransition,
  SlideUpTransition,
  SlideDownTransition,
  ScaleTransition,
  ScaleFadeTransition,
  SlideFadeLeftTransition,
  SlideFadeRightTransition,
} from './PageTransition';

export type { TransitionType, PageTransitionProps } from './PageTransition';
