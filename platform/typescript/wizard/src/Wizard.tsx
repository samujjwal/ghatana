import React from 'react';
import { useWizard } from './hooks/useWizard';
import type { WizardStep } from './hooks/useWizard';

export interface WizardProps {
  steps: WizardStep[];
  renderStep: (stepId: string, index: number) => React.ReactNode;
  onComplete?: () => void;
  onCancel?: () => void;
  completedText?: string;
  nextText?: string;
  prevText?: string;
  cancelText?: string;
}

/**
 * @doc.type component
 * @doc.purpose Multi-step wizard with accessible navigation.
 * @doc.layer platform
 * @doc.pattern UI Component
 */
export function Wizard({
  steps,
  renderStep,
  onComplete,
  onCancel,
  completedText = 'Finish',
  nextText = 'Next',
  prevText = 'Back',
  cancelText = 'Cancel',
}: WizardProps) {
  const {
    currentStep,
    currentStepIndex,
    totalSteps,
    isFirstStep,
    isLastStep,
    completedSteps,
    goToNext,
    goToPrev,
    goToStep,
  } = useWizard(steps);

  return (
    <div role="region" aria-label="Wizard">
      <nav aria-label="Progress">
        <ol>
          {steps.map((step, idx) => (
            <li
              key={step.id}
              aria-current={idx === currentStepIndex ? 'step' : undefined}
            >
              <button
                type="button"
                onClick={() => goToStep(idx)}
                aria-label={`Step ${idx + 1}: ${step.title}`}
                disabled={!completedSteps.has(step.id) && idx > currentStepIndex}
              >
                <span aria-hidden="true">{idx + 1}. </span>
                <span>{step.title}</span>
                {completedSteps.has(step.id) && (
                  <span aria-label="Completed"> ✓</span>
                )}
              </button>
            </li>
          ))}
        </ol>
      </nav>

      <div aria-live="polite">
        <h2>{currentStep.title}</h2>
        {currentStep.description && <p>{currentStep.description}</p>}
        {renderStep(currentStep.id, currentStepIndex)}
      </div>

      <footer>
        <span>
          Step {currentStepIndex + 1} of {totalSteps}
        </span>
        <div>
          {onCancel && (
            <button type="button" onClick={onCancel}>
              {cancelText}
            </button>
          )}
          {!isFirstStep && (
            <button type="button" onClick={goToPrev}>
              {prevText}
            </button>
          )}
          {isLastStep ? (
            <button type="button" onClick={onComplete}>
              {completedText}
            </button>
          ) : (
            <button type="button" onClick={goToNext}>
              {nextText}
            </button>
          )}
        </div>
      </footer>
    </div>
  );
}
