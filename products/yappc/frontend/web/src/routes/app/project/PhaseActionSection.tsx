import React from 'react';

import { Button } from '@ghatana/design-system';

export interface PhaseActionSectionAction {
  readonly actionId: string;
  readonly testId: string;
  readonly label: string;
  readonly severity: string;
  readonly disabled: boolean;
  readonly onClick: () => void;
}

export interface PhaseActionSectionGroup {
  readonly testId: string;
  readonly title: string;
  readonly description: string;
  readonly actions: readonly PhaseActionSectionAction[];
}

interface PhaseActionSectionProps {
  readonly testId: string;
  readonly title: string;
  readonly description: string;
  readonly actions: readonly PhaseActionSectionAction[];
}

function buttonToneClass(severity: string): string {
  switch (severity.toLowerCase()) {
    case 'danger':
      return 'border-destructive bg-destructive-bg text-destructive';
    case 'warning':
      return 'border-warning-border bg-warning-bg text-warning-color';
    case 'info':
      return 'border-info-border bg-info-bg text-info-color';
    case 'success':
      return 'border-success-border bg-success-bg text-success-color';
    default:
      return 'border-border bg-surface text-fg';
  }
}

export function PhaseActionSection({
  testId,
  title,
  description,
  actions,
}: PhaseActionSectionProps): React.ReactNode {
  if (actions.length === 0) {
    return null;
  }

  return (
    <div className="rounded-xl border border-border bg-surface-raised p-4" data-testid={testId}>
      <p className="text-sm font-semibold text-fg">{title}</p>
      <p className="mt-1 text-xs text-fg-muted">{description}</p>
      <div className="mt-3 flex flex-wrap gap-2">
        {actions.map((action) => (
          <Button
            key={action.actionId}
            type="button"
            variant="outline"
            size="small"
            className={buttonToneClass(action.severity)}
            data-testid={action.testId}
            disabled={action.disabled}
            onClick={action.onClick}
          >
            {action.label}
          </Button>
        ))}
      </div>
    </div>
  );
}
