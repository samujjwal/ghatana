/**
 * Phase Blocker Panel Component
 *
 * Displays current blockers preventing phase progression.
 * Blockers explain exactly what to do to move forward.
 *
 * @doc.type component
 * @doc.purpose Blocker display panel for phase cockpits
 * @doc.layer product
 * @doc.pattern Presentation Component
 */

import React from 'react';

import { Button, Card, CardContent } from '@ghatana/design-system';

export interface Blocker {
  id: string;
  title: string;
  description: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  actionLabel?: string;
  onAction?: () => void;
}

export interface PhaseBlockerPanelProps {
  /** List of blockers */
  blockers: Blocker[];
  /** Custom className */
  className?: string;
}

/**
 * Severity color mapping
 */
const SEVERITY_COLORS: Record<Blocker['severity'], string> = {
  critical: 'bg-destructive-bg border-destructive-border text-destructive dark:bg-destructive-bg/20 dark:border-destructive-border dark:text-destructive',
  high: 'bg-warning-bg border-warning-border text-warning-color dark:bg-warning-bg/20 dark:border-warning-border dark:text-warning-color',
  medium: 'bg-warning-bg border-warning-border text-warning-color dark:bg-warning-bg/20 dark:border-warning-border dark:text-warning-color',
  low: 'bg-info-bg border-info-border text-info-color dark:bg-info-bg/20 dark:border-info-border dark:text-info-color',
};

/**
 * Phase Blocker Panel Component
 *
 * Displays blockers with:
 * - Severity-based color coding
 * - Clear title and description
 * - Actionable CTA when available
 * - Empty state when no blockers
 */
export const PhaseBlockerPanel: React.FC<PhaseBlockerPanelProps> = ({
  blockers,
  className = '',
}) => {
  if (blockers.length === 0) {
    return (
      <div className={`phase-blocker-panel ${className}`}>
        <div className="bg-success-bg dark:bg-success-bg/20 border border-success-border dark:border-success-border rounded-lg p-4 text-center">
          <p className="text-success-color dark:text-success-color text-sm font-medium">
            ✓ No blockers - ready to proceed
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className={`phase-blocker-panel ${className}`}>
      <h3 className="text-sm font-medium text-fg dark:text-fg-muted mb-3">
        Blockers ({blockers.length})
      </h3>
      <div className="space-y-3">
        {blockers.map((blocker) => (
          <Card
            key={blocker.id}
            variant="outlined"
            className={SEVERITY_COLORS[blocker.severity]}
          >
            <CardContent className="p-4">
              <div className="flex items-start justify-between gap-3">
                <div className="flex-1">
                  <h4 className="font-medium mb-1">{blocker.title}</h4>
                  <p className="text-sm opacity-90">{blocker.description}</p>
                </div>
                {blocker.actionLabel && blocker.onAction && (
                  <Button
                    onClick={blocker.onAction}
                    variant="outline"
                    size="sm"
                  >
                    {blocker.actionLabel}
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

export default PhaseBlockerPanel;
