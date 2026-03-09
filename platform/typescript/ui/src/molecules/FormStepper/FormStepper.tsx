import { ReactNode } from 'react';
import { Check } from 'lucide-react';

export interface FormStep {
  /** Step label */
  label: string;
  /** Optional description */
  description?: string;
  /** Optional icon */
  icon?: ReactNode;
}

export interface FormStepperProps {
  /** Array of step definitions */
  steps: FormStep[];
  /** Current active step (0-indexed) */
  currentStep: number;
  /** Callback when step is clicked (if clickable) */
  onStepClick?: (stepIndex: number) => void;
  /** Whether completed steps are clickable */
  clickable?: boolean;
  /** Orientation */
  orientation?: 'horizontal' | 'vertical';
  /** Custom class */
  className?: string;
}

/**
 * Form Stepper Component
 * 
 * Progress indicator for multi-step forms.
 * WCAG 2.1 AA compliant with proper ARIA attributes.
 * 
 * @doc.type component
 * @doc.purpose Multi-step form progress indicator
 * @doc.layer core
 * @doc.pattern Form Component
 * 
 * @example
 * ```tsx
 * const steps: FormStep[] = [
 *   { label: 'Basic Info', description: 'Name and email' },
 *   { label: 'Preferences', description: 'Your settings' },
 *   { label: 'Review', description: 'Confirm details' },
 * ];
 * 
 * <FormStepper
 *   steps={steps}
 *   currentStep={1}
 *   onStepClick={(step) => setCurrentStep(step)}
 *   clickable
 * />
 * ```
 */
export function FormStepper({
  steps,
  currentStep,
  onStepClick,
  clickable = false,
  orientation = 'horizontal',
  className = '',
}: FormStepperProps) {
  const isStepCompleted = (index: number) => index < currentStep;
  const isStepCurrent = (index: number) => index === currentStep;
  const isStepClickable = (index: number) => clickable && isStepCompleted(index);

  const getStepStatus = (index: number): 'completed' | 'current' | 'upcoming' => {
    if (isStepCompleted(index)) return 'completed';
    if (isStepCurrent(index)) return 'current';
    return 'upcoming';
  };

  const handleStepClick = (index: number) => {
    if (isStepClickable(index) && onStepClick) {
      onStepClick(index);
    }
  };

  if (orientation === 'vertical') {
    return (
      <nav
        aria-label="Progress"
        className={className}
      >
        <ol className="space-y-4" role="list">
          {steps.map((step, index) => {
            const status = getStepStatus(index);
            
            return (
              <li key={index} className="relative">
                {/* Connector line */}
                {index < steps.length - 1 && (
                  <div
                    className={`absolute left-4 top-8 w-0.5 h-full -ml-px ${
                      isStepCompleted(index)
                        ? 'bg-primary-600'
                        : 'bg-gray-300 dark:bg-gray-600'
                    }`}
                    aria-hidden="true"
                  />
                )}

                <div
                  className={`relative flex items-start group ${
                    isStepClickable(index) ? 'cursor-pointer' : ''
                  }`}
                  onClick={() => handleStepClick(index)}
                  role={isStepClickable(index) ? 'button' : undefined}
                  tabIndex={isStepClickable(index) ? 0 : undefined}
                  onKeyDown={(e) => {
                    if ((e.key === 'Enter' || e.key === ' ') && isStepClickable(index)) {
                      e.preventDefault();
                      handleStepClick(index);
                    }
                  }}
                  aria-current={isStepCurrent(index) ? 'step' : undefined}
                >
                  {/* Step indicator */}
                  <span
                    className={`flex h-8 w-8 items-center justify-center rounded-full border-2 flex-shrink-0 ${
                      status === 'completed'
                        ? 'bg-primary-600 border-primary-600'
                        : status === 'current'
                        ? 'border-primary-600 bg-white dark:bg-gray-800'
                        : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800'
                    } ${
                      isStepClickable(index)
                        ? 'group-hover:border-primary-700 transition-colors'
                        : ''
                    }`}
                  >
                    {status === 'completed' ? (
                      <Check className="w-4 h-4 text-white" />
                    ) : step.icon ? (
                      <span
                        className={
                          status === 'current'
                            ? 'text-primary-600'
                            : 'text-gray-500 dark:text-gray-400'
                        }
                      >
                        {step.icon}
                      </span>
                    ) : (
                      <span
                        className={`text-sm font-medium ${
                          status === 'current'
                            ? 'text-primary-600'
                            : 'text-gray-500 dark:text-gray-400'
                        }`}
                      >
                        {index + 1}
                      </span>
                    )}
                  </span>

                  {/* Step content */}
                  <div className="ml-4 min-w-0">
                    <span
                      className={`text-sm font-medium ${
                        status === 'current'
                          ? 'text-primary-600'
                          : status === 'completed'
                          ? 'text-gray-900 dark:text-white'
                          : 'text-gray-500 dark:text-gray-400'
                      }`}
                    >
                      {step.label}
                    </span>
                    {step.description && (
                      <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
                        {step.description}
                      </p>
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ol>
      </nav>
    );
  }

  // Horizontal orientation
  return (
    <nav aria-label="Progress" className={className}>
      <ol className="flex items-center" role="list">
        {steps.map((step, index) => {
          const status = getStepStatus(index);
          const isLast = index === steps.length - 1;

          return (
            <li
              key={index}
              className={`relative ${isLast ? '' : 'flex-1'}`}
            >
              <div
                className={`flex items-center ${
                  isStepClickable(index) ? 'cursor-pointer' : ''
                }`}
                onClick={() => handleStepClick(index)}
                role={isStepClickable(index) ? 'button' : undefined}
                tabIndex={isStepClickable(index) ? 0 : undefined}
                onKeyDown={(e) => {
                  if ((e.key === 'Enter' || e.key === ' ') && isStepClickable(index)) {
                    e.preventDefault();
                    handleStepClick(index);
                  }
                }}
                aria-current={isStepCurrent(index) ? 'step' : undefined}
              >
                {/* Step indicator */}
                <span
                  className={`flex h-10 w-10 items-center justify-center rounded-full border-2 flex-shrink-0 transition-colors ${
                    status === 'completed'
                      ? 'bg-primary-600 border-primary-600'
                      : status === 'current'
                      ? 'border-primary-600 bg-white dark:bg-gray-800'
                      : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800'
                  } ${
                    isStepClickable(index)
                      ? 'hover:border-primary-700 hover:bg-primary-700'
                      : ''
                  }`}
                >
                  {status === 'completed' ? (
                    <Check className="w-5 h-5 text-white" />
                  ) : step.icon ? (
                    <span
                      className={
                        status === 'current'
                          ? 'text-primary-600'
                          : 'text-gray-500 dark:text-gray-400'
                      }
                    >
                      {step.icon}
                    </span>
                  ) : (
                    <span
                      className={`text-sm font-medium ${
                        status === 'current'
                          ? 'text-primary-600'
                          : 'text-gray-500 dark:text-gray-400'
                      }`}
                    >
                      {index + 1}
                    </span>
                  )}
                </span>

                {/* Step label (shown on larger screens) */}
                <span className="hidden sm:block ml-3">
                  <span
                    className={`text-sm font-medium ${
                      status === 'current'
                        ? 'text-primary-600'
                        : status === 'completed'
                        ? 'text-gray-900 dark:text-white'
                        : 'text-gray-500 dark:text-gray-400'
                    }`}
                  >
                    {step.label}
                  </span>
                  {step.description && (
                    <span className="block text-xs text-gray-500 dark:text-gray-400">
                      {step.description}
                    </span>
                  )}
                </span>
              </div>

              {/* Connector line */}
              {!isLast && (
                <div
                  className={`hidden sm:block absolute top-5 left-full w-full h-0.5 ${
                    isStepCompleted(index)
                      ? 'bg-primary-600'
                      : 'bg-gray-300 dark:bg-gray-600'
                  }`}
                  style={{ width: 'calc(100% - 2.5rem)', marginLeft: '0.75rem' }}
                  aria-hidden="true"
                />
              )}
            </li>
          );
        })}
      </ol>

      {/* Mobile step label */}
      <div className="sm:hidden mt-4 text-center">
        <span className="text-sm font-medium text-gray-900 dark:text-white">
          Step {currentStep + 1} of {steps.length}
        </span>
        <span className="block text-sm text-gray-500 dark:text-gray-400">
          {steps[currentStep]?.label}
        </span>
      </div>
    </nav>
  );
}

export default FormStepper;
