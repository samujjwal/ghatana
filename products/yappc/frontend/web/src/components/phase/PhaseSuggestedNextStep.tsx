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
  confidence: number;
  evidence: readonly string[];
  riskLevel: 'low' | 'medium' | 'high';
  applyMode: 'one-click' | 'manual' | 'review-required';
  approvalRequired: boolean;
  rollbackSupported: boolean;
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

const RISK_COLORS: Record<SuggestedStep['riskLevel'], string> = {
  low: 'bg-success-bg text-success-color dark:bg-success-bg/30 dark:text-success-color',
  medium: 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color',
  high: 'bg-destructive-bg text-destructive dark:bg-destructive-bg/30 dark:text-destructive',
};

const SECTION_LABELS: Record<SuggestedStep['type'], string> = {
  automation: 'Automation suggestions',
  review: 'Review gates',
  manual: 'Manual suggestions',
};

function getActionLabel(step: SuggestedStep): string {
  if (step.applyMode === 'one-click') {
    return 'Run guided action';
  }
  if (step.applyMode === 'review-required') {
    return 'Open review';
  }
  return 'Review details';
}

function groupStepsByType(steps: readonly SuggestedStep[]): Array<{
  readonly type: SuggestedStep['type'];
  readonly steps: readonly SuggestedStep[];
}> {
  return (['automation', 'review', 'manual'] as const)
    .map((type) => ({
      type,
      steps: steps.filter((step) => step.type === type),
    }))
    .filter((group) => group.steps.length > 0);
}

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

  const groupedSteps = groupStepsByType(steps);

  return (
    <div className={`phase-suggested-next-step ${className}`}>
        <h3 className="text-sm font-medium text-fg dark:text-fg-muted mb-3">
          Suggested Actions ({steps.length})
        </h3>
        <p className="sr-only" data-testid="suggestion-accessibility-explanation">
          Confidence explains how strongly the available evidence supports a suggestion. Review gates mean a human approval step is required before the action can change project state.
        </p>
        <div className="space-y-3">
          {groupedSteps.map((group) => (
            <section key={group.type} data-testid={`suggestion-section-${group.type}`}>
              <h4 className="mb-2 text-xs font-semibold uppercase tracking-[0.14em] text-fg-muted">
                {SECTION_LABELS[group.type]}
              </h4>
              <div className="space-y-3">
                {group.steps.map((step) => (
                  <Card key={step.id} variant="outlined">
                    <CardContent className="p-4">
                      <div className="flex items-start justify-between gap-3 mb-2">
                        <div className="flex-1">
                          <div className="flex flex-wrap items-center gap-2 mb-1">
                            <h5 className="font-medium text-fg dark:text-fg-muted">
                              {step.title}
                            </h5>
                            <Chip
                              label={step.type}
                              size="sm"
                              className={STEP_TYPE_COLORS[step.type]}
                            />
                            <Chip
                              label={`${step.riskLevel} risk`}
                              size="sm"
                              className={RISK_COLORS[step.riskLevel]}
                            />
                            <Chip
                              label={`${Math.round(step.confidence * 100)}% confidence`}
                              size="sm"
                              variant="outlined"
                            />
                          </div>
                          <p className="text-sm text-fg-muted dark:text-fg-muted mb-3">
                            {step.description}
                          </p>
                          <dl className="mb-3 grid gap-2 text-xs text-fg-muted sm:grid-cols-2">
                            <div>
                              <dt className="font-semibold text-fg">Apply mode</dt>
                              <dd>{step.applyMode}</dd>
                            </div>
                            <div>
                              <dt className="font-semibold text-fg">Approval</dt>
                              <dd>{step.approvalRequired ? 'Required' : 'Not required'}</dd>
                            </div>
                            <div>
                              <dt className="font-semibold text-fg">Rollback</dt>
                              <dd>{step.rollbackSupported ? 'Supported' : 'Not available'}</dd>
                            </div>
                            {step.estimatedTime && (
                              <div>
                                <dt className="font-semibold text-fg">Estimate</dt>
                                <dd>{step.estimatedTime}</dd>
                              </div>
                            )}
                          </dl>
                          {step.evidence.length > 0 && (
                            <div className="mb-3 rounded-lg border border-border bg-surface-muted/50 p-2">
                              <p className="text-xs font-semibold text-fg">Evidence</p>
                              <ul className="mt-1 list-disc space-y-1 pl-4 text-xs text-fg-muted">
                                {step.evidence.map((item) => (
                                  <li key={item}>{item}</li>
                                ))}
                              </ul>
                            </div>
                          )}
                        </div>
                      </div>
                      <div className="flex items-center gap-2">
                        <Button onClick={step.onAccept} className="flex-1" size="sm">
                          {getActionLabel(step)}
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
            </section>
          ))}
        </div>
      </div>
    );
  };

export default PhaseSuggestedNextStep;
