import React from 'react';
import { tokens } from '@ghatana/tokens';

export interface ActionSheetItem {
  key: string;
  label: string;
  icon?: React.ReactNode;
  destructive?: boolean;
  disabled?: boolean;
  onClick: () => void;
}

export interface ActionSheetProps {
  /** Action items */
  items: ActionSheetItem[];
  /** Title */
  title?: string;
  /** Cancel handler */
  onCancel: () => void;
  /** Additional class name */
  className?: string;
}

export const ActionSheet: React.FC<ActionSheetProps> = ({
  items,
  title,
  onCancel,
  className,
}) => {
  const overlayStyles: React.CSSProperties = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.3)',
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'flex-end',
    zIndex: tokens.zIndex.modal,
  };

  const sheetStyles: React.CSSProperties = {
    backgroundColor: tokens.colors.white,
    borderTopLeftRadius: tokens.borderRadius.xl,
    borderTopRightRadius: tokens.borderRadius.xl,
    padding: tokens.spacing[4],
    maxHeight: '80vh',
    overflowY: 'auto',
    animation: 'slideUp 0.3s ease-out',
  };

  const titleStyles: React.CSSProperties = {
    fontSize: tokens.typography.fontSize.sm,
    fontWeight: tokens.typography.fontWeight.semibold,
    color: tokens.colors.neutral[500],
    textTransform: 'uppercase',
    letterSpacing: '0.5px',
    marginBottom: tokens.spacing[3],
    paddingBottom: tokens.spacing[3],
    borderBottom: `${tokens.borderWidth[1]} solid ${tokens.colors.neutral[200]}`,
  };

  const itemsContainerStyles: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: tokens.spacing[2],
  };

  const getItemStyles = (item: ActionSheetItem): React.CSSProperties => ({
    display: 'flex',
    alignItems: 'center',
    gap: tokens.spacing[3],
    padding: `${tokens.spacing[3]} ${tokens.spacing[4]}`,
    borderRadius: tokens.borderRadius.lg,
    backgroundColor: 'transparent',
    border: 'none',
    fontSize: tokens.typography.fontSize.base,
    fontWeight: tokens.typography.fontWeight.medium,
    color: item.destructive ? tokens.colors.error[600] : tokens.colors.neutral[900],
    cursor: item.disabled ? 'not-allowed' : 'pointer',
    opacity: item.disabled ? 0.5 : 1,
    transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
  });

  const cancelButtonStyles: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    padding: `${tokens.spacing[3]} ${tokens.spacing[4]}`,
    marginTop: tokens.spacing[2],
    borderRadius: tokens.borderRadius.lg,
    backgroundColor: tokens.colors.neutral[100],
    border: 'none',
    fontSize: tokens.typography.fontSize.base,
    fontWeight: tokens.typography.fontWeight.medium,
    color: tokens.colors.neutral[900],
    cursor: 'pointer',
    transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
  };

  return (
    <>
      <div style={overlayStyles} onClick={onCancel} />
      <div style={sheetStyles} className={className} role="menu">
        {title && <div style={titleStyles}>{title}</div>}
        <div style={itemsContainerStyles}>
          {items.map((item) => (
            <button
              key={item.key}
              style={getItemStyles(item)}
              onClick={() => {
                if (!item.disabled) {
                  item.onClick();
                }
              }}
              disabled={item.disabled}
              role="menuitem"
              onMouseEnter={(e) => {
                if (!item.disabled) {
                  e.currentTarget.style.backgroundColor = item.destructive
                    ? tokens.colors.error[50]
                    : tokens.colors.neutral[50];
                }
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = 'transparent';
              }}
            >
              {item.icon && (
                <span style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
                  {item.icon}
                </span>
              )}
              <span style={{ flex: 1, textAlign: 'left' }}>{item.label}</span>
            </button>
          ))}
        </div>
        <button
          style={cancelButtonStyles}
          onClick={onCancel}
          onMouseEnter={(e) => {
            e.currentTarget.style.backgroundColor = tokens.colors.neutral[200];
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.backgroundColor = tokens.colors.neutral[100];
          }}
        >
          Cancel
        </button>
      </div>
      <style>{`
        @keyframes slideUp {
          from {
            transform: translateY(100%);
            opacity: 0;
          }
          to {
            transform: translateY(0);
            opacity: 1;
          }
        }
      `}</style>
    </>
  );
};

ActionSheet.displayName = 'ActionSheet';
