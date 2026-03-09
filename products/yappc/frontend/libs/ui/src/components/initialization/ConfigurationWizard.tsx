/**
 * ConfigurationWizard Component
 *
 * @description Main multi-step wizard container for guiding users through
 * the complete initialization and configuration process with step management,
 * validation, and state persistence.
 *
 * @doc.type component
 * @doc.purpose wizard-container
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <ConfigurationWizard
 *   steps={[
 *     { id: 'project', title: 'Project Setup', content: <ProjectForm /> },
 *     { id: 'infra', title: 'Infrastructure', content: <InfraForm /> },
 *     { id: 'review', title: 'Review', content: <ReviewSummary /> },
 *   ]}
 *   initialStep={0}
 *   onComplete={(data) => submitConfiguration(data)}
 *   onCancel={() => history.back()}
 * />
 * ```
 */

import React, { useState, useCallback, useMemo, useRef, useEffect } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Step validation result
 */
export interface StepValidation {
  /** Whether the step is valid */
  valid: boolean;
  /** Validation errors */
  errors?: Record<string, string>;
  /** Validation warnings (non-blocking) */
  warnings?: Record<string, string>;
}

/**
 * Wizard step definition
 */
export interface WizardStepDefinition {
  /** Unique step identifier */
  id: string;
  /** Step title */
  title: string;
  /** Step description */
  description?: string;
  /** Step icon (SVG string or component) */
  icon?: React.ReactNode;
  /** Step content/form component */
  content: React.ReactNode;
  /** Whether this step is optional */
  optional?: boolean;
  /** Custom validation function */
  validate?: () => StepValidation | Promise<StepValidation>;
  /** Whether to show this step (for conditional steps) */
  condition?: () => boolean;
}

/**
 * Step state
 */
export interface WizardStepState {
  /** Step ID */
  id: string;
  /** Whether step has been visited */
  visited: boolean;
  /** Whether step is complete */
  completed: boolean;
  /** Step data */
  data?: Record<string, unknown>;
  /** Validation errors */
  errors?: Record<string, string>;
  /** Validation warnings */
  warnings?: Record<string, string>;
}

/**
 * Wizard context for child components
 */
export interface WizardContextValue {
  /** Current step index */
  currentStep: number;
  /** Total visible steps */
  totalSteps: number;
  /** Step states */
  stepStates: Record<string, WizardStepState>;
  /** Go to next step */
  next: () => Promise<boolean>;
  /** Go to previous step */
  previous: () => void;
  /** Go to specific step */
  goToStep: (index: number) => void;
  /** Update step data */
  updateStepData: (stepId: string, data: Record<string, unknown>) => void;
  /** Mark step as complete */
  markStepComplete: (stepId: string) => void;
  /** Get all collected data */
  getAllData: () => Record<string, Record<string, unknown>>;
  /** Whether currently validating */
  isValidating: boolean;
}

/**
 * Props for the ConfigurationWizard component
 */
export interface ConfigurationWizardProps {
  /** Step definitions */
  steps: WizardStepDefinition[];
  /** Initial step index */
  initialStep?: number;
  /** Initial data */
  initialData?: Record<string, Record<string, unknown>>;
  /** Callback when wizard completes */
  onComplete: (data: Record<string, Record<string, unknown>>) => void;
  /** Callback when wizard is cancelled */
  onCancel?: () => void;
  /** Callback on step change */
  onStepChange?: (stepIndex: number, stepId: string) => void;
  /** Callback on data change */
  onDataChange?: (data: Record<string, Record<string, unknown>>) => void;
  /** Title */
  title?: string;
  /** Subtitle */
  subtitle?: string;
  /** Whether to show step numbers */
  showStepNumbers?: boolean;
  /** Whether to allow step navigation */
  allowStepNavigation?: boolean;
  /** Whether to persist data to local storage */
  persistData?: boolean;
  /** Local storage key for persistence */
  storageKey?: string;
  /** Whether to show confirmation before cancel */
  confirmCancel?: boolean;
  /** Complete button text */
  completeButtonText?: string;
  /** Loading state */
  loading?: boolean;
  /** Custom class name */
  className?: string;
}

// ============================================================================
// Context
// ============================================================================

export const WizardContext = React.createContext<WizardContextValue | null>(null);

export const useWizard = (): WizardContextValue => {
  const context = React.useContext(WizardContext);
  if (!context) {
    throw new Error('useWizard must be used within a ConfigurationWizard');
  }
  return context;
};

// ============================================================================
// Sub-Components
// ============================================================================

interface StepIndicatorProps {
  steps: WizardStepDefinition[];
  currentStep: number;
  stepStates: Record<string, WizardStepState>;
  showNumbers: boolean;
  allowNavigation: boolean;
  onStepClick: (index: number) => void;
}

const StepIndicator: React.FC<StepIndicatorProps> = ({
  steps,
  currentStep,
  stepStates,
  showNumbers,
  allowNavigation,
  onStepClick,
}) => {
  return (
    <div className="wizard-step-indicator">
      {steps.map((step, index) => {
        const state = stepStates[step.id] || { visited: false, completed: false };
        const isActive = index === currentStep;
        const isPast = index < currentStep;
        const canNavigate =
          allowNavigation && (state.visited || isPast || state.completed);

        const stepClasses = [
          'wizard-step-item',
          isActive && 'wizard-step-item--active',
          isPast && 'wizard-step-item--past',
          state.completed && 'wizard-step-item--completed',
          state.errors && Object.keys(state.errors).length > 0 && 'wizard-step-item--error',
          canNavigate && 'wizard-step-item--clickable',
        ]
          .filter(Boolean)
          .join(' ');

        return (
          <React.Fragment key={step.id}>
            <div
              className={stepClasses}
              role={canNavigate ? 'button' : undefined}
              tabIndex={canNavigate ? 0 : undefined}
              onClick={canNavigate ? () => onStepClick(index) : undefined}
              onKeyDown={
                canNavigate
                  ? (e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        onStepClick(index);
                      }
                    }
                  : undefined
              }
              aria-current={isActive ? 'step' : undefined}
            >
              <div className="wizard-step-circle">
                {state.completed ? (
                  <svg viewBox="0 0 24 24" fill="currentColor">
                    <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z" />
                  </svg>
                ) : showNumbers ? (
                  index + 1
                ) : step.icon ? (
                  step.icon
                ) : (
                  <span className="wizard-step-dot" />
                )}
              </div>
              <div className="wizard-step-label">
                <span className="wizard-step-title">{step.title}</span>
                {step.optional && (
                  <span className="wizard-step-optional">Optional</span>
                )}
              </div>
            </div>

            {index < steps.length - 1 && (
              <div
                className={`wizard-step-connector ${
                  index < currentStep ? 'wizard-step-connector--completed' : ''
                }`}
              />
            )}
          </React.Fragment>
        );
      })}
    </div>
  );
};

interface WizardNavigationProps {
  currentStep: number;
  totalSteps: number;
  isValidating: boolean;
  loading: boolean;
  onPrevious: () => void;
  onNext: () => void;
  onCancel?: () => void;
  completeButtonText: string;
}

const WizardNavigation: React.FC<WizardNavigationProps> = ({
  currentStep,
  totalSteps,
  isValidating,
  loading,
  onPrevious,
  onNext,
  onCancel,
  completeButtonText,
}) => {
  const isFirstStep = currentStep === 0;
  const isLastStep = currentStep === totalSteps - 1;

  return (
    <div className="wizard-navigation">
      <div className="wizard-navigation-left">
        {onCancel && (
          <button
            type="button"
            className="wizard-btn wizard-btn--text"
            onClick={onCancel}
            disabled={loading}
          >
            Cancel
          </button>
        )}
      </div>

      <div className="wizard-navigation-right">
        {!isFirstStep && (
          <button
            type="button"
            className="wizard-btn wizard-btn--secondary"
            onClick={onPrevious}
            disabled={loading || isValidating}
          >
            Previous
          </button>
        )}

        <button
          type="button"
          className="wizard-btn wizard-btn--primary"
          onClick={onNext}
          disabled={loading || isValidating}
        >
          {isValidating ? (
            <>
              <span className="wizard-btn-spinner" />
              Validating...
            </>
          ) : isLastStep ? (
            completeButtonText
          ) : (
            'Continue'
          )}
        </button>
      </div>
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const ConfigurationWizard: React.FC<ConfigurationWizardProps> = ({
  steps,
  initialStep = 0,
  initialData = {},
  onComplete,
  onCancel,
  onStepChange,
  onDataChange,
  title,
  subtitle,
  showStepNumbers = true,
  allowStepNavigation = true,
  persistData = false,
  storageKey = 'yappc-wizard-data',
  confirmCancel = true,
  completeButtonText = 'Complete Setup',
  loading = false,
  className = '',
}) => {
  // Filter visible steps based on conditions
  const visibleSteps = useMemo(() => {
    return steps.filter((step) => !step.condition || step.condition());
  }, [steps]);

  // State
  const [currentStep, setCurrentStep] = useState(initialStep);
  const [stepStates, setStepStates] = useState<Record<string, WizardStepState>>(() => {
    // Initialize from localStorage if persisting
    if (persistData && typeof window !== 'undefined') {
      const stored = localStorage.getItem(storageKey);
      if (stored) {
        try {
          return JSON.parse(stored);
        } catch {
          // Invalid JSON, ignore
        }
      }
    }

    // Initialize from initialData
    const states: Record<string, WizardStepState> = {};
    steps.forEach((step) => {
      states[step.id] = {
        id: step.id,
        visited: false,
        completed: false,
        data: initialData[step.id] || {},
      };
    });
    return states;
  });

  const [isValidating, setIsValidating] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);

  // Mark current step as visited
  useEffect(() => {
    const currentStepDef = visibleSteps[currentStep];
    if (currentStepDef && !stepStates[currentStepDef.id]?.visited) {
      setStepStates((prev) => ({
        ...prev,
        [currentStepDef.id]: {
          ...prev[currentStepDef.id],
          visited: true,
        },
      }));
    }
  }, [currentStep, visibleSteps, stepStates]);

  // Persist data to localStorage
  useEffect(() => {
    if (persistData && typeof window !== 'undefined') {
      localStorage.setItem(storageKey, JSON.stringify(stepStates));
    }
  }, [stepStates, persistData, storageKey]);

  // Scroll content to top on step change
  useEffect(() => {
    if (contentRef.current) {
      contentRef.current.scrollTop = 0;
    }
  }, [currentStep]);

  // Get all collected data
  const getAllData = useCallback((): Record<string, Record<string, unknown>> => {
    const data: Record<string, Record<string, unknown>> = {};
    Object.entries(stepStates).forEach(([stepId, state]) => {
      if (state.data) {
        data[stepId] = state.data;
      }
    });
    return data;
  }, [stepStates]);

  // Update step data
  const updateStepData = useCallback(
    (stepId: string, data: Record<string, unknown>) => {
      setStepStates((prev) => ({
        ...prev,
        [stepId]: {
          ...prev[stepId],
          data: { ...prev[stepId]?.data, ...data },
        },
      }));
      onDataChange?.(getAllData());
    },
    [onDataChange, getAllData]
  );

  // Mark step as complete
  const markStepComplete = useCallback((stepId: string) => {
    setStepStates((prev) => ({
      ...prev,
      [stepId]: {
        ...prev[stepId],
        completed: true,
      },
    }));
  }, []);

  // Validate current step
  const validateCurrentStep = useCallback(async (): Promise<boolean> => {
    const currentStepDef = visibleSteps[currentStep];
    if (!currentStepDef?.validate) {
      return true;
    }

    setIsValidating(true);
    try {
      const result = await currentStepDef.validate();

      setStepStates((prev) => ({
        ...prev,
        [currentStepDef.id]: {
          ...prev[currentStepDef.id],
          errors: result.errors,
          warnings: result.warnings,
        },
      }));

      return result.valid;
    } catch (error) {
      console.error('Validation error:', error);
      return false;
    } finally {
      setIsValidating(false);
    }
  }, [currentStep, visibleSteps]);

  // Go to next step
  const next = useCallback(async (): Promise<boolean> => {
    const isValid = await validateCurrentStep();
    if (!isValid) {
      return false;
    }

    const currentStepDef = visibleSteps[currentStep];
    markStepComplete(currentStepDef.id);

    if (currentStep === visibleSteps.length - 1) {
      // Last step - complete wizard
      const allData = getAllData();

      // Clear persisted data
      if (persistData && typeof window !== 'undefined') {
        localStorage.removeItem(storageKey);
      }

      onComplete(allData);
      return true;
    }

    const nextStep = currentStep + 1;
    setCurrentStep(nextStep);
    onStepChange?.(nextStep, visibleSteps[nextStep].id);
    return true;
  }, [
    currentStep,
    visibleSteps,
    validateCurrentStep,
    markStepComplete,
    getAllData,
    persistData,
    storageKey,
    onComplete,
    onStepChange,
  ]);

  // Go to previous step
  const previous = useCallback(() => {
    if (currentStep > 0) {
      const prevStep = currentStep - 1;
      setCurrentStep(prevStep);
      onStepChange?.(prevStep, visibleSteps[prevStep].id);
    }
  }, [currentStep, visibleSteps, onStepChange]);

  // Go to specific step
  const goToStep = useCallback(
    (index: number) => {
      if (index >= 0 && index < visibleSteps.length) {
        const targetState = stepStates[visibleSteps[index].id];
        if (targetState?.visited || targetState?.completed || index < currentStep) {
          setCurrentStep(index);
          onStepChange?.(index, visibleSteps[index].id);
        }
      }
    },
    [visibleSteps, stepStates, currentStep, onStepChange]
  );

  // Handle cancel
  const handleCancel = useCallback(() => {
    if (confirmCancel) {
      const confirmed = window.confirm(
        'Are you sure you want to cancel? All progress will be lost.'
      );
      if (!confirmed) return;
    }

    // Clear persisted data
    if (persistData && typeof window !== 'undefined') {
      localStorage.removeItem(storageKey);
    }

    onCancel?.();
  }, [confirmCancel, persistData, storageKey, onCancel]);

  // Context value
  const contextValue = useMemo<WizardContextValue>(
    () => ({
      currentStep,
      totalSteps: visibleSteps.length,
      stepStates,
      next,
      previous,
      goToStep,
      updateStepData,
      markStepComplete,
      getAllData,
      isValidating,
    }),
    [
      currentStep,
      visibleSteps.length,
      stepStates,
      next,
      previous,
      goToStep,
      updateStepData,
      markStepComplete,
      getAllData,
      isValidating,
    ]
  );

  const currentStepDef = visibleSteps[currentStep];
  const currentState = stepStates[currentStepDef?.id];

  const containerClasses = [
    'configuration-wizard',
    loading && 'configuration-wizard--loading',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <WizardContext.Provider value={contextValue}>
      <div className={containerClasses}>
        {/* Header */}
        {(title || subtitle) && (
          <div className="wizard-header">
            {title && <h2 className="wizard-title">{title}</h2>}
            {subtitle && <p className="wizard-subtitle">{subtitle}</p>}
          </div>
        )}

        {/* Step Indicator */}
        <StepIndicator
          steps={visibleSteps}
          currentStep={currentStep}
          stepStates={stepStates}
          showNumbers={showStepNumbers}
          allowNavigation={allowStepNavigation}
          onStepClick={goToStep}
        />

        {/* Step Content */}
        <div className="wizard-content" ref={contentRef}>
          <div className="wizard-step-header">
            <h3 className="wizard-step-title">{currentStepDef?.title}</h3>
            {currentStepDef?.description && (
              <p className="wizard-step-description">
                {currentStepDef.description}
              </p>
            )}
          </div>

          {/* Validation Errors */}
          {currentState?.errors && Object.keys(currentState.errors).length > 0 && (
            <div className="wizard-errors">
              <div className="wizard-errors-title">
                Please fix the following errors:
              </div>
              <ul className="wizard-errors-list">
                {Object.entries(currentState.errors).map(([field, message]) => (
                  <li key={field}>{message}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Validation Warnings */}
          {currentState?.warnings &&
            Object.keys(currentState.warnings).length > 0 && (
              <div className="wizard-warnings">
                <div className="wizard-warnings-title">Warnings:</div>
                <ul className="wizard-warnings-list">
                  {Object.entries(currentState.warnings).map(([field, message]) => (
                    <li key={field}>{message}</li>
                  ))}
                </ul>
              </div>
            )}

          {/* Step Content */}
          <div className="wizard-step-content">{currentStepDef?.content}</div>
        </div>

        {/* Navigation */}
        <WizardNavigation
          currentStep={currentStep}
          totalSteps={visibleSteps.length}
          isValidating={isValidating}
          loading={loading}
          onPrevious={previous}
          onNext={next}
          onCancel={onCancel ? handleCancel : undefined}
          completeButtonText={completeButtonText}
        />

        {/* CSS-in-JS Styles */}
        <style>{`
          .configuration-wizard {
            display: flex;
            flex-direction: column;
            height: 100%;
            background: #fff;
            border-radius: 16px;
            box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
            overflow: hidden;
          }

          .configuration-wizard--loading {
            pointer-events: none;
            opacity: 0.8;
          }

          .wizard-header {
            padding: 1.5rem 2rem;
            border-bottom: 1px solid #E5E7EB;
          }

          .wizard-title {
            margin: 0;
            font-size: 1.25rem;
            font-weight: 600;
            color: #111827;
          }

          .wizard-subtitle {
            margin: 0.25rem 0 0;
            font-size: 0.875rem;
            color: #6B7280;
          }

          .wizard-step-indicator {
            display: flex;
            align-items: center;
            padding: 1.25rem 2rem;
            background: #F9FAFB;
            border-bottom: 1px solid #E5E7EB;
            overflow-x: auto;
          }

          .wizard-step-item {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            flex-shrink: 0;
          }

          .wizard-step-item--clickable {
            cursor: pointer;
          }

          .wizard-step-item--clickable:hover .wizard-step-circle {
            border-color: #3B82F6;
          }

          .wizard-step-circle {
            width: 32px;
            height: 32px;
            display: flex;
            align-items: center;
            justify-content: center;
            background: #fff;
            border: 2px solid #E5E7EB;
            border-radius: 50%;
            font-size: 0.75rem;
            font-weight: 600;
            color: #9CA3AF;
            transition: all 0.2s ease;
          }

          .wizard-step-item--active .wizard-step-circle {
            border-color: #3B82F6;
            background: #3B82F6;
            color: #fff;
          }

          .wizard-step-item--past .wizard-step-circle,
          .wizard-step-item--completed .wizard-step-circle {
            border-color: #10B981;
            background: #10B981;
            color: #fff;
          }

          .wizard-step-item--error .wizard-step-circle {
            border-color: #EF4444;
            background: #EF4444;
          }

          .wizard-step-circle svg {
            width: 16px;
            height: 16px;
          }

          .wizard-step-dot {
            width: 8px;
            height: 8px;
            background: currentColor;
            border-radius: 50%;
          }

          .wizard-step-label {
            display: flex;
            flex-direction: column;
          }

          .wizard-step-title {
            font-size: 0.75rem;
            font-weight: 500;
            color: #6B7280;
          }

          .wizard-step-item--active .wizard-step-title {
            color: #111827;
            font-weight: 600;
          }

          .wizard-step-optional {
            font-size: 0.625rem;
            color: #9CA3AF;
          }

          .wizard-step-connector {
            flex: 1;
            min-width: 20px;
            max-width: 60px;
            height: 2px;
            background: #E5E7EB;
            margin: 0 0.5rem;
          }

          .wizard-step-connector--completed {
            background: #10B981;
          }

          .wizard-content {
            flex: 1;
            overflow-y: auto;
            padding: 1.5rem 2rem;
          }

          .wizard-step-header {
            margin-bottom: 1.5rem;
          }

          .wizard-step-title {
            margin: 0;
            font-size: 1.125rem;
            font-weight: 600;
            color: #111827;
          }

          .wizard-step-description {
            margin: 0.25rem 0 0;
            font-size: 0.875rem;
            color: #6B7280;
          }

          .wizard-errors {
            padding: 0.75rem 1rem;
            background: #FEF2F2;
            border: 1px solid #FECACA;
            border-radius: 8px;
            margin-bottom: 1rem;
          }

          .wizard-errors-title {
            font-size: 0.75rem;
            font-weight: 600;
            color: #DC2626;
            margin-bottom: 0.375rem;
          }

          .wizard-errors-list {
            margin: 0;
            padding-left: 1rem;
            font-size: 0.75rem;
            color: #B91C1C;
          }

          .wizard-warnings {
            padding: 0.75rem 1rem;
            background: #FFFBEB;
            border: 1px solid #FED7AA;
            border-radius: 8px;
            margin-bottom: 1rem;
          }

          .wizard-warnings-title {
            font-size: 0.75rem;
            font-weight: 600;
            color: #D97706;
            margin-bottom: 0.375rem;
          }

          .wizard-warnings-list {
            margin: 0;
            padding-left: 1rem;
            font-size: 0.75rem;
            color: #B45309;
          }

          .wizard-step-content {
            min-height: 200px;
          }

          .wizard-navigation {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 1rem 2rem;
            border-top: 1px solid #E5E7EB;
            background: #F9FAFB;
          }

          .wizard-navigation-left,
          .wizard-navigation-right {
            display: flex;
            align-items: center;
            gap: 0.75rem;
          }

          .wizard-btn {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.625rem 1.25rem;
            font-size: 0.875rem;
            font-weight: 500;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .wizard-btn:disabled {
            opacity: 0.6;
            cursor: not-allowed;
          }

          .wizard-btn--text {
            color: #6B7280;
            background: transparent;
            border: none;
          }

          .wizard-btn--text:hover:not(:disabled) {
            color: #374151;
          }

          .wizard-btn--secondary {
            color: #374151;
            background: #fff;
            border: 1px solid #D1D5DB;
          }

          .wizard-btn--secondary:hover:not(:disabled) {
            background: #F9FAFB;
            border-color: #9CA3AF;
          }

          .wizard-btn--primary {
            color: #fff;
            background: linear-gradient(135deg, #3B82F6 0%, #2563EB 100%);
            border: none;
          }

          .wizard-btn--primary:hover:not(:disabled) {
            transform: translateY(-1px);
            box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4);
          }

          .wizard-btn-spinner {
            width: 14px;
            height: 14px;
            border: 2px solid rgba(255, 255, 255, 0.3);
            border-top-color: #fff;
            border-radius: 50%;
            animation: spinner-rotate 0.8s linear infinite;
          }

          @keyframes spinner-rotate {
            from { transform: rotate(0deg); }
            to { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    </WizardContext.Provider>
  );
};

ConfigurationWizard.displayName = 'ConfigurationWizard';

export default ConfigurationWizard;
