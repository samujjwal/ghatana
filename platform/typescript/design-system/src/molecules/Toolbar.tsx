import React from 'react';
import { tokens } from '@ghatana/tokens';

export interface ToolbarAction {
  id: string;
  label: string;
  icon?: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  divider?: boolean;
}

export interface ToolbarProps {
  actions: ToolbarAction[];
  variant?: 'default' | 'compact' | 'dense';
  position?: 'top' | 'bottom';
  sticky?: boolean;
  className?: string;
}

/**
 * Toolbar component for action bars
 */
export const Toolbar: React.FC<ToolbarProps> = ({
  actions,
  variant = 'default',
  position = 'top',
  sticky,
  className,
}) => {
  const sizeConfig = {
    default: { padding: tokens.spacing[3], gap: tokens.spacing[2] },
    compact: { padding: tokens.spacing[2], gap: tokens.spacing[1] },
    dense: { padding: tokens.spacing[1], gap: tokens.spacing[1] },
  };

  const config = sizeConfig[variant];

  const toolbarStyles: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: config.gap,
    padding: config.padding,
    backgroundColor: tokens.colors.neutral[50],
    borderBottom: position === 'top' ? `1px solid ${tokens.colors.neutral[200]}` : 'none',
    borderTop: position === 'bottom' ? `1px solid ${tokens.colors.neutral[200]}` : 'none',
    position: sticky ? 'sticky' : 'relative',
    top: position === 'top' ? 0 : 'auto',
    bottom: position === 'bottom' ? 0 : 'auto',
    zIndex: sticky ? 10 : 'auto',
    flexWrap: 'wrap',
  };

  return (
    <div style={toolbarStyles} className={className} role="toolbar">
      {actions.map((action, _index) => (
        <React.Fragment key={action.id}>
          {action.divider && (
            <div
              style={{
                width: '1px',
                height: '24px',
                backgroundColor: tokens.colors.neutral[200],
                margin: `0 ${tokens.spacing[1]}`,
              }}
            />
          )}
          <button
            onClick={action.onClick}
            disabled={action.disabled}
            title={action.label}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: tokens.spacing[1],
              padding: `${tokens.spacing[1]} ${tokens.spacing[2]}`,
              backgroundColor: 'transparent',
              border: `1px solid ${tokens.colors.neutral[200]}`,
              borderRadius: tokens.borderRadius.md,
              cursor: action.disabled ? 'not-allowed' : 'pointer',
              opacity: action.disabled ? 0.5 : 1,
              color: tokens.colors.neutral[700],
              fontSize: tokens.typography.fontSize.sm,
              fontWeight: 500,
              transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
            }}
            onMouseEnter={(e) => {
              if (!action.disabled) {
                e.currentTarget.style.backgroundColor = tokens.colors.neutral[100];
                e.currentTarget.style.borderColor = tokens.colors.primary[300];
              }
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = 'transparent';
              e.currentTarget.style.borderColor = tokens.colors.neutral[200];
            }}
            aria-label={action.label}
          >
            {action.icon && <span style={{ display: 'flex', alignItems: 'center' }}>{action.icon}</span>}
            {variant !== 'dense' && <span>{action.label}</span>}
          </button>
        </React.Fragment>
      ))}
    </div>
  );
};

Toolbar.displayName = 'Toolbar';
