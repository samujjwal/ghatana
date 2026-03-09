/**
 * Onboarding & Feature Discovery (Epic 10)
 *
 * Guided tour system for first-time users and contextual hints for feature discovery.
 * Persists progress in localStorage, respects user preferences.
 *
 * @doc.type barrel-export
 * @doc.purpose Onboarding system public API
 * @doc.layer product
 * @doc.pattern Facade
 */

export {
  OnboardingTour,
  useOnboardingTour,
  CANVAS_TOUR_STEPS,
  type TourStep,
  type TooltipPosition,
} from './OnboardingTour';

export {
  FeatureHintsManager,
  useFeatureHints,
  CANVAS_FEATURE_HINTS,
  type FeatureHint,
  type HintTrigger,
} from './FeatureHints';
