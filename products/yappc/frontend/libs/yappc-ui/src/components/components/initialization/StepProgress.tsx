/**
 * StepProgress Component
 *
 * @description Displays progress indicator for multi-step initialization
 * wizard with step status, timing, and navigation.
 *
 * @doc.type component
 * @doc.purpose step-progress-visualization
 * @doc.layer ui
 * @doc.phase initialization
 *
 * @example
 * ```tsx
 * <StepProgress
 *   steps={[
 *     { id: 'repo', label: 'Repository', status: 'complete' },
 *     { id: 'infra', label: 'Infrastructure', status: 'in-progress' },
 *     { id: 'cicd', label: 'CI/CD', status: 'pending' },
 *   ]}
 *   currentStepId="infra"
 *   onStepClick={(stepId) => navigate(stepId)}
 * />
 * ```
 */

import React, { useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Status of an individual step in the wizard
 */
export type StepStatus =
  | 'pending'
  | 'in-progress'
  | 'complete'
  | 'error'
  | 'warning'
  | 'skipped';

/**
 * Individual step configuration
 */
export interface WizardStep {
  /** Unique identifier for the step */
  id: string;
  /** Display label */
  label: string;
  /** Optional description */
  description?: string;
  /** Current status */
  status: StepStatus;
  /** Error message if status is 'error' */
  errorMessage?: string;
  /** Warning message if status is 'warning' */
  warningMessage?: string;
  /** Duration in milliseconds (for completed steps) */
  durationMs?: number;
  /** Whether this step is optional */
  optional?: boolean;
  /** Icon identifier */
  icon?: string;
}

/**
 * Props for the StepProgress component
 */
export interface StepProgressProps {
  /** Array of steps to display */
  steps: WizardStep[];
  /** ID of the currently active step */
  currentStepId?: string;
  /** Whether to allow clicking on steps for navigation */
  allowNavigation?: boolean;
  /** Callback when a step is clicked */
  onStepClick?: (stepId: string) => void;
  /** Visual variant */
  variant?: 'horizontal' | 'vertical' | 'compact';
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Whether to show step numbers */
  showNumbers?: boolean;
  /** Whether to show duration for completed steps */
  showDuration?: boolean;
  /** Custom class name */
  className?: string;
}

// ============================================================================
// Utility Functions
// ============================================================================

const formatDuration = (ms: number): string => {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  const minutes = Math.floor(ms / 60000);
  const seconds = Math.floor((ms % 60000) / 1000);
  return `${minutes}m ${seconds}s`;
};

const getStatusIcon = (status: StepStatus, stepNumber: number): React.ReactNode => {
  switch (status) {
    case 'complete':
      return (
        <svg
          className="step-icon step-icon--check"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="3"
          aria-hidden="true"
        >
          <polyline points="20 6 9 17 4 12" />
        </svg>
      );
    case 'error':
      return (
        <svg
          className="step-icon step-icon--error"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          aria-hidden="true"
        >
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </svg>
      );
    case 'warning':
      return (
        <svg
          className="step-icon step-icon--warning"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          aria-hidden="true"
        >
          <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
          <line x1="12" y1="9" x2="12" y2="13" />
          <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
      );
    case 'skipped':
      return (
        <svg
          className="step-icon step-icon--skipped"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          aria-hidden="true"
        >
          <polygon points="5 4 15 12 5 20 5 4" />
          <line x1="19" y1="5" x2="19" y2="19" />
        </svg>
      );
    case 'in-progress':
      return (
        <span className="step-icon step-icon--spinner" aria-hidden="true">
          <svg
            className="step-spinner"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <circle cx="12" cy="12" r="10" strokeDasharray="31.4" strokeDashoffset="10" />
          </svg>
        </span>
      );
    case 'pending':
    default:
      return <span className="step-number">{stepNumber}</span>;
  }
};

const getStepClasses = (
  step: WizardStep,
  isCurrent: boolean,
  isClickable: boolean,
  variant: string,
  size: string
): string => {
  const classes = [
    'step-item',
    `step-item--${step.status}`,
    `step-item--${variant}`,
    `step-item--${size}`,
  ];

  if (isCurrent) classes.push('step-item--current');
  if (isClickable) classes.push('step-item--clickable');
  if (step.optional) classes.push('step-item--optional');

  return classes.join(' ');
};

// ============================================================================
// Sub-Components
// ============================================================================

interface StepItemProps {
  step: WizardStep;
  stepNumber: number;
  isCurrent: boolean;
  isClickable: boolean;
  variant: 'horizontal' | 'vertical' | 'compact';
  size: 'sm' | 'md' | 'lg';
  showDuration: boolean;
  onClick?: () => void;
}

const StepItem: React.FC<StepItemProps> = ({
  step,
  stepNumber,
  isCurrent,
  isClickable,
  variant,
  size,
  showDuration,
  onClick,
}) => {
  const handleClick = () => {
    if (isClickable && onClick) {
      onClick();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if ((e.key === 'Enter' || e.key === ' ') && isClickable && onClick) {
      e.preventDefault();
      onClick();
    }
  };

  const statusLabel = useMemo(() => {
    switch (step.status) {
      case 'complete':
        return 'Completed';
      case 'in-progress':
        return 'In progress';
      case 'error':
        return 'Error';
      case 'warning':
        return 'Warning';
      case 'skipped':
        return 'Skipped';
      case 'pending':
      default:
        return 'Pending';
    }
  }, [step.status]);

  return (
    <div
      className={getStepClasses(step, isCurrent, isClickable, variant, size)}
      role="listitem"
      aria-current={isCurrent ? 'step' : undefined}
      tabIndex={isClickable ? 0 : -1}
      onClick={handleClick}
      onKeyDown={handleKeyDown}
    >
      <div className="step-indicator">
        <div
          className={`step-circle step-circle--${step.status}`}
          aria-label={`Step ${stepNumber}: ${step.label} - ${statusLabel}`}
        >
          {getStatusIcon(step.status, stepNumber)}
        </div>
      </div>

      <div className="step-content">
        <div className="step-header">
          <span className="step-label">{step.label}</span>
          {step.optional && <span className="step-optional">(Optional)</span>}
        </div>

        {step.description && variant !== 'compact' && (
          <span className="step-description">{step.description}</span>
        )}

        {step.status === 'error' && step.errorMessage && (
          <span className="step-error-message" role="alert">
            {step.errorMessage}
          </span>
        )}

        {step.status === 'warning' && step.warningMessage && (
          <span className="step-warning-message" role="status">
            {step.warningMessage}
          </span>
        )}

        {showDuration && step.status === 'complete' && step.durationMs && (
          <span className="step-duration">{formatDuration(step.durationMs)}</span>
        )}
      </div>
    </div>
  );
};

// ============================================================================
// Connector Component
// ============================================================================

interface StepConnectorProps {
  fromStatus: StepStatus;
  toStatus: StepStatus;
  variant: 'horizontal' | 'vertical' | 'compact';
}

const StepConnector: React.FC<StepConnectorProps> = ({ fromStatus, toStatus, variant }) => {
  const isComplete = fromStatus === 'complete';
  const hasError = fromStatus === 'error' || toStatus === 'error';

  const connectorClasses = [
    'step-connector',
    `step-connector--${variant}`,
    isComplete && 'step-connector--complete',
    hasError && 'step-connector--error',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={connectorClasses} aria-hidden="true">
      <div className="step-connector-line" />
    </div>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const StepProgress: React.FC<StepProgressProps> = ({
  steps,
  currentStepId,
  allowNavigation = false,
  onStepClick,
  variant = 'horizontal',
  size = 'md',
  showNumbers = true,
  showDuration = true,
  className = '',
}) => {
  const currentIndex = useMemo(() => {
    if (!currentStepId) return -1;
    return steps.findIndex((s) => s.id === currentStepId);
  }, [steps, currentStepId]);

  const progressPercent = useMemo(() => {
    const completedCount = steps.filter((s) => s.status === 'complete').length;
    return Math.round((completedCount / steps.length) * 100);
  }, [steps]);

  const containerClasses = [
    'step-progress',
    `step-progress--${variant}`,
    `step-progress--${size}`,
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div
      className={containerClasses}
      role="list"
      aria-label={`Progress: ${progressPercent}% complete`}
    >
      {/* Progress summary for screen readers */}
      <div className="sr-only" aria-live="polite">
        Step {currentIndex + 1} of {steps.length}: {steps[currentIndex]?.label || 'Unknown'}
      </div>

      {/* Steps */}
      <div className="step-progress-track">
        {steps.map((step, index) => {
          const isCurrent = step.id === currentStepId || index === currentIndex;
          const isClickable = allowNavigation && step.status === 'complete' && !!onStepClick;
          const isLast = index === steps.length - 1;

          return (
            <React.Fragment key={step.id}>
              <StepItem
                step={step}
                stepNumber={showNumbers ? index + 1 : 0}
                isCurrent={isCurrent}
                isClickable={isClickable}
                variant={variant}
                size={size}
                showDuration={showDuration}
                onClick={isClickable ? () => onStepClick?.(step.id) : undefined}
              />

              {!isLast && (
                <StepConnector
                  fromStatus={step.status}
                  toStatus={steps[index + 1]?.status || 'pending'}
                  variant={variant}
                />
              )}
            </React.Fragment>
          );
        })}
      </div>

      {/* Overall progress bar (compact variant) */}
      {variant === 'compact' && (
        <div className="step-progress-bar" aria-hidden="true">
          <div
            className="step-progress-bar-fill"
            style={{ width: `${progressPercent}%` }}
          />
        </div>
      )}

      {/* CSS-in-JS Styles */}
      <style>{`
        .step-progress {
          --step-size-sm: 24px;
          --step-size-md: 32px;
          --step-size-lg: 40px;
          --step-font-sm: 0.75rem;
          --step-font-md: 0.875rem;
          --step-font-lg: 1rem;
          --step-spacing-sm: 0.5rem;
          --step-spacing-md: 1rem;
          --step-spacing-lg: 1.5rem;
          --color-pending: #6B7280;
          --color-progress: #3B82F6;
          --color-complete: #10B981;
          --color-error: #EF4444;
          --color-warning: #F59E0B;
          --color-skipped: #9CA3AF;
        }

        .step-progress--horizontal .step-progress-track {
          display: flex;
          align-items: flex-start;
          justify-content: space-between;
        }

        .step-progress--vertical .step-progress-track {
          display: flex;
          flex-direction: column;
        }

        .step-progress--compact .step-progress-track {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          margin-bottom: 0.5rem;
        }

        .step-item {
          display: flex;
          flex-direction: column;
          align-items: center;
          text-align: center;
          flex: 1;
          min-width: 0;
        }

        .step-progress--vertical .step-item {
          flex-direction: row;
          align-items: flex-start;
          text-align: left;
          padding: var(--step-spacing-md) 0;
        }

        .step-progress--compact .step-item {
          flex: 0 0 auto;
        }

        .step-item--clickable {
          cursor: pointer;
        }

        .step-item--clickable:hover .step-circle {
          transform: scale(1.1);
        }

        .step-item--clickable:focus {
          outline: none;
        }

        .step-item--clickable:focus .step-circle {
          box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.3);
        }

        .step-indicator {
          position: relative;
          z-index: 1;
        }

        .step-circle {
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: 50%;
          background: #fff;
          border: 2px solid var(--color-pending);
          color: var(--color-pending);
          font-weight: 600;
          transition: all 0.2s ease;
        }

        .step-item--sm .step-circle {
          width: var(--step-size-sm);
          height: var(--step-size-sm);
          font-size: var(--step-font-sm);
        }

        .step-item--md .step-circle {
          width: var(--step-size-md);
          height: var(--step-size-md);
          font-size: var(--step-font-md);
        }

        .step-item--lg .step-circle {
          width: var(--step-size-lg);
          height: var(--step-size-lg);
          font-size: var(--step-font-lg);
        }

        .step-circle--in-progress {
          border-color: var(--color-progress);
          color: var(--color-progress);
          animation: pulse 2s infinite;
        }

        .step-circle--complete {
          border-color: var(--color-complete);
          background: var(--color-complete);
          color: #fff;
        }

        .step-circle--error {
          border-color: var(--color-error);
          background: var(--color-error);
          color: #fff;
        }

        .step-circle--warning {
          border-color: var(--color-warning);
          background: var(--color-warning);
          color: #fff;
        }

        .step-circle--skipped {
          border-color: var(--color-skipped);
          background: var(--color-skipped);
          color: #fff;
        }

        @keyframes pulse {
          0%, 100% { box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.4); }
          50% { box-shadow: 0 0 0 8px rgba(59, 130, 246, 0); }
        }

        .step-icon {
          width: 60%;
          height: 60%;
        }

        .step-spinner {
          animation: spin 1s linear infinite;
        }

        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }

        .step-content {
          margin-top: var(--step-spacing-sm);
          max-width: 120px;
        }

        .step-progress--vertical .step-content {
          margin-top: 0;
          margin-left: var(--step-spacing-md);
          max-width: none;
        }

        .step-progress--compact .step-content {
          display: none;
        }

        .step-header {
          display: flex;
          align-items: center;
          gap: 0.25rem;
          flex-wrap: wrap;
          justify-content: center;
        }

        .step-progress--vertical .step-header {
          justify-content: flex-start;
        }

        .step-label {
          font-weight: 500;
          color: #111827;
          font-size: var(--step-font-md);
        }

        .step-item--current .step-label {
          color: var(--color-progress);
        }

        .step-item--complete .step-label {
          color: var(--color-complete);
        }

        .step-item--error .step-label {
          color: var(--color-error);
        }

        .step-optional {
          font-size: 0.75rem;
          color: #6B7280;
        }

        .step-description {
          display: block;
          font-size: 0.75rem;
          color: #6B7280;
          margin-top: 0.25rem;
        }

        .step-error-message {
          display: block;
          font-size: 0.75rem;
          color: var(--color-error);
          margin-top: 0.25rem;
        }

        .step-warning-message {
          display: block;
          font-size: 0.75rem;
          color: var(--color-warning);
          margin-top: 0.25rem;
        }

        .step-duration {
          display: block;
          font-size: 0.625rem;
          color: #9CA3AF;
          margin-top: 0.25rem;
        }

        .step-connector {
          position: relative;
          flex: 1;
          min-width: 20px;
        }

        .step-progress--horizontal .step-connector {
          height: 2px;
          margin-top: calc(var(--step-size-md) / 2);
          margin-left: -8px;
          margin-right: -8px;
        }

        .step-progress--vertical .step-connector {
          position: absolute;
          left: calc(var(--step-size-md) / 2);
          top: calc(var(--step-size-md) + var(--step-spacing-md));
          width: 2px;
          height: calc(100% - var(--step-size-md) - var(--step-spacing-md) * 2);
        }

        .step-connector-line {
          position: absolute;
          inset: 0;
          background: #E5E7EB;
        }

        .step-connector--complete .step-connector-line {
          background: var(--color-complete);
        }

        .step-connector--error .step-connector-line {
          background: var(--color-error);
        }

        .step-progress-bar {
          height: 4px;
          background: #E5E7EB;
          border-radius: 2px;
          overflow: hidden;
        }

        .step-progress-bar-fill {
          height: 100%;
          background: var(--color-complete);
          transition: width 0.3s ease;
        }

        .sr-only {
          position: absolute;
          width: 1px;
          height: 1px;
          padding: 0;
          margin: -1px;
          overflow: hidden;
          clip: rect(0, 0, 0, 0);
          white-space: nowrap;
          border: 0;
        }
      `}</style>
    </div>
  );
};

StepProgress.displayName = 'StepProgress';

export default StepProgress;
