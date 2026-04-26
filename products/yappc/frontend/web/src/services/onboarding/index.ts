/**
 * Onboarding Services Index
 *
 * @doc.type module
 * @doc.purpose Export onboarding status and sync utilities
 * @doc.layer product
 * @doc.pattern Barrel Export
 */

export {
  getOnboardingStatus,
  setOnboardingStatus,
  useOnboardingStatus,
} from './OnboardingStatusService';

export type {
  OnboardingStatus,
  UpdateOnboardingRequest,
} from './OnboardingStatusService';
