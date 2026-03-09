import type { OnboardingState } from '../../components/OnboardingTour';

/**
 * Result object returned by useOnboardingTour hook.
 *
 * Provides access to onboarding tour state and control methods.
 */
export interface UseOnboardingTourResult {
  // ====== State ======
  /** Whether an onboarding tour is currently active */
  isActive: boolean;
  /** ID of the current tour, or null if no tour is active */
  currentTour: OnboardingState['currentTour'];
  /** Index of the current step in the tour (0-based) */
  currentStepIndex: number;
  /** Configuration object for the current step */
  currentStep: OnboardingState['currentStep'];
  /** Total number of steps in the current tour */
  totalSteps: number;
  /** Array of tour IDs that have been completed */
  completedTours: string[];

  // ====== Actions ======
  /** Start a tour by ID. Returns true if tour was started, false if tour not found */
  startTour: (tourId: string) => Promise<boolean>;
  /** Advance to the next step in the current tour */
  nextStep: () => Promise<void>;
  /** Go back to the previous step in the current tour */
  previousStep: () => Promise<void>;
  /** Skip the current tour without marking it as complete */
  skipTour: () => void;
  /** Mark the current tour as complete and move to next */
  completeTour: () => void;
  /** Stop the current tour completely */
  stopTour: () => void;

  // ====== Utilities ======
  /** Check if onboarding should be shown to the current user */
  shouldShowOnboarding: () => boolean;
  /** Get list of all available tours */
  getAvailableTours: () => unknown;
}

/**
 * Options for the useAutoOnboarding hook.
 */
export interface UseAutoOnboardingOptions {
  /** Whether to enable auto-onboarding. Default: true */
  enabled?: boolean;
  /** Delay in milliseconds before showing tour. Default: 1000 */
  delay?: number;
  /** ID of the tour to show. Default: 'getting-started' */
  tourId?: string;
}
