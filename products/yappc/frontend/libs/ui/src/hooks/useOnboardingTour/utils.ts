import {
  onboardingTourManager,
  type OnboardingState,
} from '../../components/OnboardingTour';

/**
 * Utility class for onboarding tour operations.
 *
 * Provides helper methods that wrap the global onboardingTourManager API
 * for easier consumption in hooks.
 */
export class OnboardingTourUtils {
  /**
   * Get the current onboarding tour state.
   *
   * @returns The current state from the tour manager
   */
  static getCurrentState() {
    return onboardingTourManager.getCurrentState();
  }

  /**
   * Subscribe to onboarding state changes.
   *
   * @param listener - Callback function called when state changes
   * @returns Unsubscribe function
   */
  static addListener(listener: (state: OnboardingState) => void) {
    return onboardingTourManager.addListener(listener);
  }

  /**
   * Start a tour by ID.
   *
   * @param tourId - The ID of the tour to start
   * @returns Promise resolving to true if tour started, false if not found
   */
  static async startTour(tourId: string): Promise<boolean> {
    return await onboardingTourManager.startTour(tourId);
  }

  /**
   * Move to the next step in the current tour.
   *
   * @returns Promise that resolves when step is advanced
   */
  static async nextStep(): Promise<void> {
    await onboardingTourManager.nextStep();
  }

  /**
   * Move to the previous step in the current tour.
   *
   * @returns Promise that resolves when step is moved back
   */
  static async previousStep(): Promise<void> {
    await onboardingTourManager.previousStep();
  }

  /**
   * Skip the current tour without completion.
   */
  static skipTour(): void {
    onboardingTourManager.skipTour();
  }

  /**
   * Mark the current tour as complete.
   */
  static completeTour(): void {
    onboardingTourManager.completeTour();
  }

  /**
   * Stop the current tour immediately.
   */
  static stopTour(): void {
    onboardingTourManager.stopTour();
  }

  /**
   * Check if onboarding should be shown to the user.
   *
   * @returns true if onboarding should be shown, false otherwise
   */
  static shouldShowOnboarding(): boolean {
    return onboardingTourManager.shouldShowOnboarding();
  }

  /**
   * Get all available tours.
   *
   * @returns Array or collection of available tour definitions
   */
  static getAvailableTours() {
    return onboardingTourManager.getAvailableTours();
  }
}
