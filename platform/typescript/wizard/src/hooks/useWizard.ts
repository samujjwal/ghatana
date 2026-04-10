import { useState, useCallback, useMemo } from 'react';

export interface WizardStep {
  id: string;
  title: string;
  description?: string;
  isValid?: () => boolean;
}

export interface WizardState {
  currentStepIndex: number;
  totalSteps: number;
  currentStep: WizardStep;
  isFirstStep: boolean;
  isLastStep: boolean;
  completedSteps: Set<string>;
}

export interface WizardActions {
  goToNext: () => void;
  goToPrev: () => void;
  goToStep: (index: number) => void;
  markComplete: (stepId: string) => void;
  reset: () => void;
}

export type UseWizardReturn = WizardState & WizardActions;

/**
 * @doc.type hook
 * @doc.purpose Manages multi-step wizard navigation state.
 * @doc.layer platform
 * @doc.pattern Custom Hook
 */
export function useWizard(steps: WizardStep[], initialStep = 0): UseWizardReturn {
  const [currentStepIndex, setCurrentStepIndex] = useState(initialStep);
  const [completedSteps, setCompletedSteps] = useState<Set<string>>(new Set());

  const currentStep = steps[currentStepIndex]!;
  const isFirstStep = currentStepIndex === 0;
  const isLastStep = currentStepIndex === steps.length - 1;

  const goToNext = useCallback(() => {
    if (isLastStep) return;
    const stepId = steps[currentStepIndex]?.id;
    if (stepId) {
      setCompletedSteps((prev) => new Set(prev).add(stepId));
    }
    setCurrentStepIndex((i) => i + 1);
  }, [currentStepIndex, isLastStep, steps]);

  const goToPrev = useCallback(() => {
    if (isFirstStep) return;
    setCurrentStepIndex((i) => i - 1);
  }, [isFirstStep]);

  const goToStep = useCallback(
    (index: number) => {
      if (index >= 0 && index < steps.length) {
        setCurrentStepIndex(index);
      }
    },
    [steps.length]
  );

  const markComplete = useCallback((stepId: string) => {
    setCompletedSteps((prev) => new Set(prev).add(stepId));
  }, []);

  const reset = useCallback(() => {
    setCurrentStepIndex(initialStep);
    setCompletedSteps(new Set());
  }, [initialStep]);

  return useMemo(
    () => ({
      currentStepIndex,
      totalSteps: steps.length,
      currentStep,
      isFirstStep,
      isLastStep,
      completedSteps,
      goToNext,
      goToPrev,
      goToStep,
      markComplete,
      reset,
    }),
    [
      currentStepIndex,
      steps.length,
      currentStep,
      isFirstStep,
      isLastStep,
      completedSteps,
      goToNext,
      goToPrev,
      goToStep,
      markComplete,
      reset,
    ]
  );
}
