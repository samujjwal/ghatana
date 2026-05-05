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

import { Button, Card, CardContent, Chip } from '@ghatana/design-system';

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
  /** Test id for the card container */
  testId?: string;
  /** Test id for the primary action */
  actionTestId?: string;
  /** Test id for the secondary action */
  secondaryActionTestId?: string;
  /** Optional aria-label for the primary action */
  actionAriaLabel?: string;
  /** Optional aria-label for the secondary action */
  secondaryActionAriaLabel?: string;
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
  testId,
  actionTestId,
  secondaryActionTestId,
  actionAriaLabel,
  secondaryActionAriaLabel,
}) => {
  return (
    <Card
      variant="outlined"
      className={`phase-primary-action-card shadow-sm ${className}`}
      data-testid={testId}
    >
      <CardContent className="p-6">
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
          {confidence !== undefined && confidence < 1.0 && (
            <div className="flex-shrink-0 space-y-2">
              <div className="text-xs text-gray-500 dark:text-gray-400">
                Confidence
              </div>
              <Chip
                label={`${Math.round(confidence * 100)}%`}
                size="sm"
                variant="outlined"
              />
            </div>
          )}
        </div>

        <div className="flex items-center gap-3 mt-6">
          <Button
            onClick={onAction}
            disabled={disabled}
            data-testid={actionTestId}
            aria-label={actionAriaLabel ?? actionLabel}
            aria-describedby={disabled ? 'disabled-reason' : undefined}
            className="flex-1"
          >
            {actionLabel}
          </Button>
          {secondaryActionLabel && onSecondaryAction && (
            <Button
              onClick={onSecondaryAction}
              data-testid={secondaryActionTestId}
              aria-label={secondaryActionAriaLabel ?? secondaryActionLabel}
              variant="outline"
            >
              {secondaryActionLabel}
            </Button>
          )}
        </div>

        {disabled && disabledReason && (
          <p id="disabled-reason" className="mt-3 text-sm text-gray-500 dark:text-gray-400">
            {disabledReason}
          </p>
        )}
      </CardContent>
    </Card>
  );
};

export default PhasePrimaryActionCard;
