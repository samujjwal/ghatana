import { useState, useEffect, useCallback } from 'react';

import { OnboardingTourUtils } from './utils';

import type {
  UseOnboardingTourResult,
  UseAutoOnboardingOptions,
} from './types';
import type { OnboardingState } from '../../components/OnboardingTour';

/**
 * React hook for managing onboarding tours.
 *
 * Provides access to tour state and control methods via a subscription-based
 * system. Automatically syncs with the global onboarding tour manager.
 *
 * Features:
 * - Real-time state synchronization
 * - Tour navigation (next/previous)
 * - Tour lifecycle management (start/skip/complete/stop)
 * - Utility functions for querying tour status
 *
 * @returns Object with tour state and action methods
 *
 * @example
 * ```tsx
 * const {
 *   isActive,
 *   currentStep,
 *   startTour,
 *   nextStep,
 *   skipTour
 * } = useOnboardingTour();
 *
 * if (isActive && currentStep) {
 *   return (
 *     <Tooltip title={currentStep.title}>
 *       <button onClick={nextStep}>Next</button>
 *     </Tooltip>
 *   );
 * }
 * ```
 */
export const useOnboardingTour = (): UseOnboardingTourResult => {
  const [state, setState] = useState<OnboardingState>(() =>
    OnboardingTourUtils.getCurrentState()
  );

  // Subscribe to state changes
  useEffect(() => {
    const unsubscribe = OnboardingTourUtils.addListener(setState);
    return unsubscribe;
  }, []);

  // Tour navigation
  const startTour = useCallback(async (tourId: string) => {
    return await OnboardingTourUtils.startTour(tourId);
  }, []);

  const nextStep = useCallback(async () => {
    await OnboardingTourUtils.nextStep();
  }, []);

  const previousStep = useCallback(async () => {
    await OnboardingTourUtils.previousStep();
  }, []);

  // Tour lifecycle
  const skipTour = useCallback(() => {
    OnboardingTourUtils.skipTour();
  }, []);

  const completeTour = useCallback(() => {
    OnboardingTourUtils.completeTour();
  }, []);

  const stopTour = useCallback(() => {
    OnboardingTourUtils.stopTour();
  }, []);

  // Utilities
  const shouldShowOnboarding = useCallback(() => {
    return OnboardingTourUtils.shouldShowOnboarding();
  }, []);

  const getAvailableTours = useCallback(() => {
    return OnboardingTourUtils.getAvailableTours();
  }, []);

  return {
    // State
    isActive: state.isActive,
    currentTour: state.currentTour,
    currentStepIndex: state.currentStepIndex,
    currentStep: state.currentStep,
    totalSteps: state.totalSteps,
    completedTours: state.completedTours,

    // Actions
    startTour,
    nextStep,
    previousStep,
    skipTour,
    completeTour,
    stopTour,

    // Utilities
    shouldShowOnboarding,
    getAvailableTours,
  };
};

/**
 * Hook for showing onboarding automatically to new users.
 *
 * Automatically triggers tour display for new users after a configurable delay.
 * Respects user onboarding status and active tour state.
 *
 * Features:
 * - Configurable delay before showing tour
 * - Customizable tour selection
 * - Respects user onboarding preferences
 * - Avoids showing if tour already active
 *
 * @param options - Configuration options for auto-onboarding
 * @param options.enabled - Whether auto-onboarding is enabled. Default: true
 * @param options.delay - Delay in milliseconds before showing tour. Default: 1000
 * @param options.tourId - ID of the tour to show. Default: 'getting-started'
 *
 * @example
 * ```tsx
 * // Show 'getting-started' tour after 2 seconds
 * useAutoOnboarding({ delay: 2000 });
 *
 * // Show custom tour only if user hasn't seen it
 * useAutoOnboarding({ tourId: 'advanced-features' });
 *
 * // Conditionally enable based on app state
 * useAutoOnboarding({ enabled: isFirstVisit });
 * ```
 */
export const useAutoOnboarding = (
  options: UseAutoOnboardingOptions = {}
): void => {
  const { enabled = true, delay = 1000, tourId = 'getting-started' } = options;
  const { shouldShowOnboarding, startTour, isActive } = useOnboardingTour();

  useEffect(() => {
    if (!enabled || isActive) return;

    const timer = setTimeout(() => {
      if (shouldShowOnboarding()) {
        startTour(tourId);
      }
    }, delay);

    return () => clearTimeout(timer);
  }, [enabled, delay, tourId, shouldShowOnboarding, startTour, isActive]);
};

export default useOnboardingTour;
