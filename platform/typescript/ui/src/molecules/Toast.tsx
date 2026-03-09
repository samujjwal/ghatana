import React, { useEffect, useState } from 'react';
import { tokens } from '@ghatana/tokens';

export interface ToastProps {
  /** Toast message */
  message: string;
  /** Toast variant */
  variant?: 'info' | 'success' | 'warning' | 'error';
  /** Duration in milliseconds (0 for persistent) */
  duration?: number;
  /** Position */
  position?: 'top-left' | 'top-center' | 'top-right' | 'bottom-left' | 'bottom-center' | 'bottom-right';
  /** Show close button */
  showClose?: boolean;
  /** Close handler */
  onClose?: () => void;
  /** Icon */
  icon?: React.ReactNode;
  /** Additional class name */
  className?: string;
}

export const Toast: React.FC<ToastProps> = ({
  message,
  variant = 'info',
  duration = 5000,
  position = 'top-right',
  showClose = true,
  onClose,
  icon,
  className,
}) => {
  const [isVisible, setIsVisible] = useState(true);
  const [isExiting, setIsExiting] = useState(false);

  useEffect(() => {
    if (duration > 0) {
      const timer = setTimeout(() => {
        handleClose();
      }, duration);

      return () => clearTimeout(timer);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [duration]);

  const handleClose = () => {
    setIsExiting(true);
    setTimeout(() => {
      setIsVisible(false);
      onClose?.();
    }, 300); // Match animation duration
  };

  if (!isVisible) return null;

  const variantConfig = {
    info: {
      backgroundColor: tokens.colors.primary[50],
      borderColor: tokens.colors.primary[500],
      color: tokens.colors.primary[900],
      iconColor: tokens.colors.primary[600],
    },
    success: {
      backgroundColor: tokens.colors.success[50],
      borderColor: tokens.colors.success[500],
      color: tokens.colors.success[900],
      iconColor: tokens.colors.success[600],
    },
    warning: {
      backgroundColor: tokens.colors.warning[50],
      borderColor: tokens.colors.warning[500],
      color: tokens.colors.warning[900],
      iconColor: tokens.colors.warning[600],
    },
    error: {
      backgroundColor: tokens.colors.error[50],
      borderColor: tokens.colors.error[500],
      color: tokens.colors.error[900],
      iconColor: tokens.colors.error[600],
    },
  };

  const config = variantConfig[variant];

  const positionStyles: Record<string, React.CSSProperties> = {
    'top-left': { top: tokens.spacing[4], left: tokens.spacing[4] },
    'top-center': { top: tokens.spacing[4], left: '50%', transform: 'translateX(-50%)' },
    'top-right': { top: tokens.spacing[4], right: tokens.spacing[4] },
    'bottom-left': { bottom: tokens.spacing[4], left: tokens.spacing[4] },
    'bottom-center': { bottom: tokens.spacing[4], left: '50%', transform: 'translateX(-50%)' },
    'bottom-right': { bottom: tokens.spacing[4], right: tokens.spacing[4] },
  };

  const containerStyles: React.CSSProperties = {
    ...positionStyles[position],
    position: 'fixed',
    display: 'flex',
    alignItems: 'flex-start',
    gap: tokens.spacing[3],
    minWidth: '300px',
    maxWidth: '500px',
    padding: tokens.spacing[4],
    backgroundColor: config.backgroundColor,
    border: `${tokens.borderWidth[1]} solid ${config.borderColor}`,
    borderRadius: tokens.borderRadius.lg,
    boxShadow: tokens.shadows.lg,
    fontFamily: tokens.typography.fontFamily.sans,
    fontSize: tokens.typography.fontSize.sm,
    color: config.color,
    zIndex: tokens.zIndex.toast,
    animation: isExiting
      ? 'toast-exit 0.3s ease-out forwards'
      : 'toast-enter 0.3s ease-out',
  };

  const iconStyles: React.CSSProperties = {
    flexShrink: 0,
    color: config.iconColor,
  };

  const messageStyles: React.CSSProperties = {
    flex: 1,
    lineHeight: tokens.typography.lineHeight.relaxed,
  };

  const closeButtonStyles: React.CSSProperties = {
    flexShrink: 0,
    background: 'none',
    border: 'none',
    color: config.color,
    cursor: 'pointer',
    padding: 0,
    fontSize: tokens.typography.fontSize.lg,
    lineHeight: 1,
    opacity: 0.7,
    transition: `opacity ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
  };

  const defaultIcons = {
    info: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <line x1="12" y1="16" x2="12" y2="12" />
        <line x1="12" y1="8" x2="12.01" y2="8" />
      </svg>
    ),
    success: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
        <polyline points="22 4 12 14.01 9 11.01" />
      </svg>
    ),
    warning: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
        <line x1="12" y1="9" x2="12" y2="13" />
        <line x1="12" y1="17" x2="12.01" y2="17" />
      </svg>
    ),
    error: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
        <circle cx="12" cy="12" r="10" />
        <line x1="15" y1="9" x2="9" y2="15" />
        <line x1="9" y1="9" x2="15" y2="15" />
      </svg>
    ),
  };

  return (
    <>
      <div
        role="alert"
        aria-live={variant === 'error' ? 'assertive' : 'polite'}
        style={containerStyles}
        className={className}
      >
        <div style={iconStyles}>{icon || defaultIcons[variant]}</div>
        <div style={messageStyles}>{message}</div>
        {showClose && (
          <button
            style={closeButtonStyles}
            onClick={handleClose}
            aria-label="Close notification"
            onMouseEnter={(e) => {
              e.currentTarget.style.opacity = '1';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.opacity = '0.7';
            }}
          >
            ×
          </button>
        )}
      </div>
      <style>{`
        @keyframes toast-enter {
          from {
            opacity: 0;
            transform: ${position.includes('top') ? 'translateY(-20px)' : 'translateY(20px)'};
          }
          to {
            opacity: 1;
            transform: translateY(0);
          }
        }
        @keyframes toast-exit {
          from {
            opacity: 1;
            transform: translateY(0);
          }
          to {
            opacity: 0;
            transform: ${position.includes('top') ? 'translateY(-20px)' : 'translateY(20px)'};
          }
        }
      `}</style>
    </>
  );
};

Toast.displayName = 'Toast';
