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

import { Button, Card, CardContent, Chip } from '@ghatana/design-system';

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
  automation: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
  manual: 'bg-info-bg text-info-color dark:bg-info-bg/30 dark:text-info-color',
  review: 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color',
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
        <h3 className="text-sm font-medium text-fg dark:text-fg-muted mb-3">
          Suggested Actions ({steps.length})
        </h3>
        <div className="space-y-3">
          {steps.map((step) => (
            <Card key={step.id} variant="outlined">
              <CardContent className="p-4">
                <div className="flex items-start justify-between gap-3 mb-2">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <h4 className="font-medium text-fg dark:text-fg-muted">
                        {step.title}
                      </h4>
                      <Chip
                        label={step.type}
                        size="sm"
                        className={STEP_TYPE_COLORS[step.type]}
                      />
                    </div>
                    <p className="text-sm text-fg-muted dark:text-fg-muted mb-3">
                      {step.description}
                    </p>
                    {step.estimatedTime && (
                      <p className="text-xs text-fg-muted dark:text-fg-muted mb-3">
                        Est. {step.estimatedTime}
                      </p>
                    )}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Button onClick={step.onAccept} className="flex-1" size="sm">
                    Accept
                  </Button>
                  {step.onDismiss && (
                    <Button onClick={step.onDismiss} variant="outline" size="sm">
                      Dismiss
                    </Button>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    );
  };

export default PhaseSuggestedNextStep;
