/**
 * Phase Suggested Next Step Component
 *
 * Displays suggested automation or next steps that can help the user progress.
 * These are optional but recommended actions.
 *
 * @doc.type component
 * @doc.purpose Suggested automation display for phase cockpits
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';

export interface SuggestedStep {
  id: string;
  title: string;
  description: string;
  type: 'automation' | 'manual' | 'review';
  estimatedTime?: string;
  onAccept: () => void;
  onDismiss?: () => void;
}

export interface PhaseSuggestedNextStepProps {
  /** List of suggested steps */
  steps: SuggestedStep[];
  /** Custom className */
  className?: string;
}

/**
 * Step type badge colors
 */
const STEP_TYPE_COLORS: Record<SuggestedStep['type'], string> = {
  automation: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-300',
  manual: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300',
  review: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300',
};

/**
 * Phase Suggested Next Step Component
 *
 * Displays suggested steps with:
 * - Type-based badges
 * - Clear title and description
 * - Estimated time when available
 * - Accept/dismiss actions
 * - Empty state when no suggestions
 */
export const PhaseSuggestedNextStep: React.FC<PhaseSuggestedNextStepProps> = ({
  steps,
  className = '',
}) => {
  if (steps.length === 0) {
    return null;
  }

  return (
    <div className={`phase-suggested-next-step ${className}`}>
      <h3 className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
        Suggested Actions ({steps.length})
      </h3>
      <div className="space-y-3">
        {steps.map((step) => (
          <div
            key={step.id}
            className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4"
          >
            <div className="flex items-start justify-between gap-3 mb-2">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-1">
                  <h4 className="font-medium text-gray-900 dark:text-gray-100">
                    {step.title}
                  </h4>
                  <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${STEP_TYPE_COLORS[step.type]}`}>
                    {step.type}
                  </span>
                </div>
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-3">
                  {step.description}
                </p>
                {step.estimatedTime && (
                  <p className="text-xs text-gray-500 dark:text-gray-500 mb-3">
                    ⏱️ Est. {step.estimatedTime}
                  </p>
                )}
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={step.onAccept}
                className="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
              >
                Accept
              </button>
              {step.onDismiss && (
                <button
                  onClick={step.onDismiss}
                  className="px-4 py-2 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 text-sm font-medium rounded transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
                >
                  Dismiss
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default PhaseSuggestedNextStep;
