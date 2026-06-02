import React from 'react';
import { Button } from '@ghatana/design-system';

/**
 * PhrActionBar - Standard action bar component
 * Provides consistent action button layout across pages
 * Actions are grouped and aligned consistently
 */

interface ActionItem {
  id: string;
  label: string;
  onClick: () => void;
  variant?: 'primary' | 'secondary' | 'destructive' | 'ghost';
  disabled?: boolean;
  icon?: React.ReactNode;
}

interface PhrActionBarProps {
  actions: ActionItem[];
  align?: 'left' | 'right' | 'center';
  className?: string;
}

export function PhrActionBar({
  actions,
  align = 'right',
  className = '',
}: PhrActionBarProps): React.ReactElement {
  const alignClasses = {
    left: 'justify-start',
    right: 'justify-end',
    center: 'justify-center',
  };

  return (
    <div className={`phr-action-bar flex gap-3 ${alignClasses[align]} ${className}`}>
      {actions.map((action) => (
        <Button
          key={action.id}
          onClick={action.onClick}
          variant={action.variant || 'secondary'}
          disabled={action.disabled}
          className="flex items-center gap-2"
        >
          {action.icon && <span className="action-icon">{action.icon}</span>}
          {action.label}
        </Button>
      ))}
    </div>
  );
}
