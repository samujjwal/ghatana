import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * Stepper step configuration
 */
export interface StepConfig {
  /**
   * Label for the step
   */
  label: string;

  /**
   * Optional description
   */
  description?: string;

  /**
   * Whether the step is optional
   */
  optional?: boolean;

  /**
   * Custom icon for the step
   */
  icon?: React.ReactNode;

  /**
   * Whether the step is disabled
   */
  disabled?: boolean;
}

/**
 * Stepper component props
 */
export interface StepperProps extends React.HTMLAttributes<HTMLDivElement> {
  /**
   * Array of step configurations
   */
  steps: StepConfig[];

  /**
   * Current active step index (0-based)
   * @default 0
   */
  activeStep?: number;

  /**
   * Orientation of the stepper
   * @default 'horizontal'
   */
  orientation?: 'horizontal' | 'vertical';

  /**
   * Whether non-linear navigation is allowed
   * @default false
   */
  nonLinear?: boolean;

  /**
   * Called when a step is clicked (if clickable)
   */
  onStepClick?: (step: number) => void;

  /**
   * Array of completed step indices
   */
  completed?: number[];

  /**
   * Array of error step indices
   */
  error?: number[];

  /**
   * Alternative label placement (only for horizontal)
   * @default false
   */
  alternativeLabel?: boolean;
}

/**
 * Check icon for completed steps
 */
const CheckIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="none"
    viewBox="0 0 24 24"
    stroke="currentColor"
    strokeWidth={3}
  >
    <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
  </svg>
);

/**
 * Error icon for error steps
 */
const ErrorIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="currentColor"
    viewBox="0 0 24 24"
  >
    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z" />
  </svg>
);

/**
 * Stepper component for multi-step processes
 *
 * @example
 * ```tsx
 * const steps = [
 *   { label: 'Select campaign', description: 'Choose your campaign type' },
 *   { label: 'Create an ad group', optional: true },
 *   { label: 'Create an ad' },
 * ];
 *
 * <Stepper steps={steps} activeStep={1} completed={[0]} />
 * ```
 */
export const Stepper = React.forwardRef<HTMLDivElement, StepperProps>(
  (
    {
      steps,
      activeStep = 0,
      orientation = 'horizontal',
      nonLinear = false,
      onStepClick,
      completed = [],
      error = [],
      alternativeLabel = false,
      className,
      ...props
    },
    ref
  ) => {
    const isStepComplete = (index: number) => completed.includes(index);
    const isStepError = (index: number) => error.includes(index);
    const isStepActive = (index: number) => index === activeStep;

    const handleStepClick = (index: number) => {
      if (steps[index].disabled) return;
      if (!nonLinear && index > activeStep && !completed.includes(activeStep)) return;

      onStepClick?.(index);
    };

    const getStepIcon = (index: number, step: StepConfig) => {
      if (step.icon) return step.icon;
      if (isStepError(index)) return <ErrorIcon className="w-5 h-5" />;
      if (isStepComplete(index)) return <CheckIcon className="w-5 h-5" />;
      return index + 1;
    };

    const getStepIconClasses = (index: number) => {
      const base = 'flex items-center justify-center w-8 h-8 rounded-full text-sm font-semibold transition-colors';

      if (isStepError(index)) {
        return cn(base, 'bg-error-500 text-white');
      }

      if (isStepComplete(index)) {
        return cn(base, 'bg-success-500 text-white');
      }

      if (isStepActive(index)) {
        return cn(base, 'bg-primary-500 text-white');
      }

      return cn(base, 'bg-grey-300 text-grey-700');
    };

    const getConnectorClasses = (index: number) => {
      const base = 'transition-colors';

      if (orientation === 'horizontal') {
        return cn(base, 'flex-1 h-0.5 mx-2');
      }

      return cn(base, 'w-0.5 h-full ml-4 my-1');
    };

    const getConnectorColor = (index: number) => {
      if (isStepComplete(index) || (isStepActive(index) && isStepComplete(index - 1))) {
        return 'bg-success-500';
      }
      return 'bg-grey-300';
    };

    const isClickable = (index: number) => {
      if (steps[index].disabled) return false;
      if (nonLinear) return true;
      return index <= activeStep || completed.includes(activeStep);
    };

    if (orientation === 'vertical') {
      return (
        <div ref={ref} className={cn('flex flex-col', className)} {...props}>
          {steps.map((step, index) => {
            const clickable = isClickable(index);
            const showConnector = index < steps.length - 1;

            return (
              <div key={index} className="flex">
                <div className="flex flex-col items-center mr-4">
                  {/* Step Icon */}
                  <button
                    type="button"
                    onClick={() => clickable && handleStepClick(index)}
                    disabled={!clickable}
                    className={cn(
                      getStepIconClasses(index),
                      clickable && 'cursor-pointer hover:opacity-80',
                      !clickable && 'cursor-not-allowed opacity-60'
                    )}
                    aria-label={`Step ${index + 1}: ${step.label}`}
                    aria-current={isStepActive(index) ? 'step' : undefined}
                  >
                    {getStepIcon(index, step)}
                  </button>

                  {/* Connector */}
                  {showConnector && (
                    <div className={cn(getConnectorClasses(index), getConnectorColor(index))} />
                  )}
                </div>

                {/* Step Content */}
                <div className="pb-8 flex-1">
                  <div
                    className={cn(
                      'font-medium',
                      isStepActive(index) ? 'text-grey-900' : 'text-grey-600',
                      steps[index].disabled && 'opacity-60'
                    )}
                  >
                    {step.label}
                    {step.optional && (
                      <span className="ml-2 text-xs text-grey-500">(Optional)</span>
                    )}
                  </div>
                  {step.description && (
                    <div className="text-sm text-grey-500 mt-1">{step.description}</div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      );
    }

    // Horizontal orientation
    return (
      <div
        ref={ref}
        className={cn('flex items-center w-full', className)}
        {...props}
      >
        {steps.map((step, index) => {
          const clickable = isClickable(index);
          const showConnector = index < steps.length - 1;

          return (
            <React.Fragment key={index}>
              {/* Step */}
              <div
                className={cn(
                  'flex',
                  alternativeLabel ? 'flex-col items-center' : 'items-center',
                  !alternativeLabel && 'flex-1'
                )}
              >
                {/* Step Icon */}
                <button
                  type="button"
                  onClick={() => clickable && handleStepClick(index)}
                  disabled={!clickable}
                  className={cn(
                    getStepIconClasses(index),
                    clickable && 'cursor-pointer hover:opacity-80',
                    !clickable && 'cursor-not-allowed opacity-60'
                  )}
                  aria-label={`Step ${index + 1}: ${step.label}`}
                  aria-current={isStepActive(index) ? 'step' : undefined}
                >
                  {getStepIcon(index, step)}
                </button>

                {/* Step Label */}
                {!alternativeLabel && (
                  <div className="ml-3 flex-1">
                    <div
                      className={cn(
                        'text-sm font-medium',
                        isStepActive(index) ? 'text-grey-900' : 'text-grey-600',
                        steps[index].disabled && 'opacity-60'
                      )}
                    >
                      {step.label}
                      {step.optional && (
                        <span className="ml-1 text-xs text-grey-500">(Optional)</span>
                      )}
                    </div>
                    {step.description && (
                      <div className="text-xs text-grey-500 mt-0.5">{step.description}</div>
                    )}
                  </div>
                )}

                {alternativeLabel && (
                  <div className="mt-2 text-center">
                    <div
                      className={cn(
                        'text-xs font-medium',
                        isStepActive(index) ? 'text-grey-900' : 'text-grey-600',
                        steps[index].disabled && 'opacity-60'
                      )}
                    >
                      {step.label}
                      {step.optional && (
                        <span className="block text-grey-500">(Optional)</span>
                      )}
                    </div>
                  </div>
                )}
              </div>

              {/* Connector */}
              {showConnector && !alternativeLabel && (
                <div className={cn(getConnectorClasses(index), getConnectorColor(index))} />
              )}

              {showConnector && alternativeLabel && (
                <div className={cn('flex-1 h-0.5 mx-2', getConnectorColor(index))} />
              )}
            </React.Fragment>
          );
        })}
      </div>
    );
  }
);

Stepper.displayName = 'Stepper';
