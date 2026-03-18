import React from 'react';
import { tokens } from '@ghatana/tokens';

export interface ConfirmDialogProps {
  /** Dialog title */
  title: string;
  /** Dialog message */
  message: string;
  /** Confirm button text */
  confirmText?: string;
  /** Cancel button text */
  cancelText?: string;
  /** Confirm button variant */
  confirmVariant?: 'primary' | 'danger' | 'warning';
  /** Loading state */
  loading?: boolean;
  /** Confirm handler */
  onConfirm: () => void;
  /** Cancel handler */
  onCancel: () => void;
  /** Icon */
  icon?: React.ReactNode;
  /** Additional class name */
  className?: string;
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  confirmVariant = 'primary',
  loading = false,
  onConfirm,
  onCancel,
  icon,
  className,
}) => {
  const variantColors = {
    primary: tokens.colors.primary[600],
    danger: tokens.colors.error[600],
    warning: tokens.colors.warning[600],
  };

  const overlayStyles: React.CSSProperties = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: tokens.zIndex.modal,
  };

  const dialogStyles: React.CSSProperties = {
    backgroundColor: tokens.colors.white,
    borderRadius: tokens.borderRadius.lg,
    boxShadow: tokens.shadows.xl,
    padding: tokens.spacing[6],
    maxWidth: '400px',
    width: '90%',
  };

  const headerStyles: React.CSSProperties = {
    display: 'flex',
    alignItems: 'flex-start',
    gap: tokens.spacing[3],
    marginBottom: tokens.spacing[4],
  };

  const iconStyles: React.CSSProperties = {
    width: '32px',
    height: '32px',
    borderRadius: tokens.borderRadius.full,
    backgroundColor: variantColors[confirmVariant] + '20',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: variantColors[confirmVariant],
    flexShrink: 0,
  };

  const titleStyles: React.CSSProperties = {
    fontSize: tokens.typography.fontSize.lg,
    fontWeight: tokens.typography.fontWeight.semibold,
    color: tokens.colors.neutral[900],
    margin: 0,
  };

  const messageStyles: React.CSSProperties = {
    fontSize: tokens.typography.fontSize.base,
    color: tokens.colors.neutral[600],
    lineHeight: tokens.typography.lineHeight.relaxed,
    marginBottom: tokens.spacing[6],
  };

  const footerStyles: React.CSSProperties = {
    display: 'flex',
    gap: tokens.spacing[3],
    justifyContent: 'flex-end',
  };

  const buttonBaseStyles: React.CSSProperties = {
    padding: `${tokens.spacing[2]} ${tokens.spacing[4]}`,
    borderRadius: tokens.borderRadius.md,
    border: 'none',
    fontSize: tokens.typography.fontSize.sm,
    fontWeight: tokens.typography.fontWeight.medium,
    cursor: loading ? 'not-allowed' : 'pointer',
    transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    opacity: loading ? 0.6 : 1,
  };

  const cancelButtonStyles: React.CSSProperties = {
    ...buttonBaseStyles,
    backgroundColor: tokens.colors.neutral[100],
    color: tokens.colors.neutral[900],
  };

  const confirmButtonStyles: React.CSSProperties = {
    ...buttonBaseStyles,
    backgroundColor: variantColors[confirmVariant],
    color: tokens.colors.white,
  };

  return (
    <div style={overlayStyles} onClick={onCancel}>
      <div
        style={dialogStyles}
        className={className}
        onClick={(e) => e.stopPropagation()}
        role="alertdialog"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-message"
      >
        <div style={headerStyles}>
          {icon ? (
            <div style={iconStyles}>{icon}</div>
          ) : (
            <div style={iconStyles}>
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
            </div>
          )}
          <h2 id="confirm-title" style={titleStyles}>
            {title}
          </h2>
        </div>

        <p id="confirm-message" style={messageStyles}>
          {message}
        </p>

        <div style={footerStyles}>
          <button
            style={cancelButtonStyles}
            onClick={onCancel}
            disabled={loading}
            onMouseEnter={(e) => {
              if (!loading) e.currentTarget.style.backgroundColor = tokens.colors.neutral[200];
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = tokens.colors.neutral[100];
            }}
          >
            {cancelText}
          </button>
          <button
            style={confirmButtonStyles}
            onClick={onConfirm}
            disabled={loading}
            onMouseEnter={(e) => {
              if (!loading) {
                const hoverColor = confirmVariant === 'primary' ? tokens.colors.primary[700] : confirmVariant === 'danger' ? tokens.colors.error[700] : tokens.colors.warning[700];
                e.currentTarget.style.backgroundColor = hoverColor;
              }
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = variantColors[confirmVariant];
            }}
          >
            {loading ? 'Loading...' : confirmText}
          </button>
        </div>
      </div>
    </div>
  );
};

ConfirmDialog.displayName = 'ConfirmDialog';
