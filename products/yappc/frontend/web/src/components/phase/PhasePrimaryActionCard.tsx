/**
 * Phase Primary Action Card Component
 *
 * Prominent card displaying the single primary next action for the current phase.
 * This is the most important UI element in the phase cockpit - users should be able
 * to understand "what to do next" in under 5 seconds.
 *
 * @doc.type component
 * @doc.purpose Primary action display card for phase cockpits
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';

export interface PhasePrimaryActionCardProps {
  /** Action title (outcome-oriented, not technical) */
  title: string;
  /** Action description explaining what this action accomplishes */
  description: string;
  /** Primary CTA button label */
  actionLabel: string;
  /** Callback when action is triggered */
  onAction: () => void;
  /** Optional secondary action */
  secondaryActionLabel?: string;
  /** Callback for secondary action */
  onSecondaryAction?: () => void;
  /** Action icon (emoji or component) */
  icon?: React.ReactNode;
  /** Confidence score for suggested actions (0-1) */
  confidence?: number;
  /** Whether action is disabled */
  disabled?: boolean;
  /** Disabled reason */
  disabledReason?: string;
  /** Custom className */
  className?: string;
}

/**
 * Phase Primary Action Card Component
 *
 * Displays the single most important action for the current phase with:
 * - Prominent CTA button
 * - Clear outcome-oriented title and description
 * - Optional confidence indicator for suggested actions
 * - Optional secondary action
 * - Disabled state with explanation
 */
export const PhasePrimaryActionCard: React.FC<PhasePrimaryActionCardProps> = ({
  title,
  description,
  actionLabel,
  onAction,
  secondaryActionLabel,
  onSecondaryAction,
  icon,
  confidence,
  disabled = false,
  disabledReason,
  className = '',
}) => {
  return (
    <div className={`phase-primary-action-card bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-6 shadow-sm ${className}`}>
      {/* Header with icon and title */}
      <div className="flex items-start gap-4 mb-4">
        {icon && (
          <div className="flex-shrink-0 text-3xl" aria-hidden="true">
            {icon}
          </div>
        )}
        <div className="flex-1">
          <h2 className="text-xl font-semibold text-gray-900 dark:text-gray-100 mb-2">
            {title}
          </h2>
          <p className="text-gray-600 dark:text-gray-400">
            {description}
          </p>
        </div>
        {/* Confidence indicator for suggested actions */}
        {confidence !== undefined && confidence < 1.0 && (
          <div className="flex-shrink-0">
            <div className="text-xs text-gray-500 dark:text-gray-400 mb-1">
              Confidence
            </div>
            <div className="w-24 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                className="h-full bg-blue-500 dark:bg-blue-400 transition-all"
                style={{ width: `${confidence * 100}%` }}
                aria-label={`${Math.round(confidence * 100)}% confidence`}
                role="progressbar"
              />
            </div>
          </div>
        )}
      </div>

      {/* Action buttons */}
      <div className="flex items-center gap-3 mt-6">
        <button
          onClick={onAction}
          disabled={disabled}
          className="flex-1 px-6 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 dark:disabled:bg-gray-600 text-white font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
          aria-describedby={disabled ? 'disabled-reason' : undefined}
        >
          {actionLabel}
        </button>
        {secondaryActionLabel && onSecondaryAction && (
          <button
            onClick={onSecondaryAction}
            className="px-6 py-3 border border-gray-300 dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-300 font-medium rounded-lg transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-900"
          >
            {secondaryActionLabel}
          </button>
        )}
      </div>

      {/* Disabled reason */}
      {disabled && disabledReason && (
        <p id="disabled-reason" className="mt-3 text-sm text-gray-500 dark:text-gray-400">
          {disabledReason}
        </p>
      )}
    </div>
  );
};

export default PhasePrimaryActionCard;
