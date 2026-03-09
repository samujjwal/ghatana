import * as React from 'react';

export interface SnackbarProps {
  /** Whether the Snackbar is visible */
  open: boolean;
  /** Called when the Snackbar requests to close */
  onClose?: (event?: React.SyntheticEvent | Event, reason?: string) => void;
  /** Time in ms before auto-close. Set to `null` to disable. @default 5000 */
  autoHideDuration?: number | null;
  /** Content to display */
  message?: React.ReactNode;
  /** Action element displayed after the message */
  action?: React.ReactNode;
  /** Anchor position @default { vertical: 'bottom', horizontal: 'left' } */
  anchorOrigin?: {
    vertical: 'top' | 'bottom';
    horizontal: 'left' | 'center' | 'right';
  };
  /** Additional CSS classes */
  className?: string;
  /** Additional inline styles */
  style?: React.CSSProperties;
  /** Content override (if you want to provide a custom Alert/element as a child) */
  children?: React.ReactNode;
}

const positionClasses: Record<string, string> = {
  'bottom-left': 'bottom-4 left-4',
  'bottom-center': 'bottom-4 left-1/2 -translate-x-1/2',
  'bottom-right': 'bottom-4 right-4',
  'top-left': 'top-4 left-4',
  'top-center': 'top-4 left-1/2 -translate-x-1/2',
  'top-right': 'top-4 right-4',
};

/**
 * Snackbar notification component — displays a brief message at the bottom of the screen.
 * Drop-in replacement for MUI Snackbar using Tailwind CSS.
 *
 * @example
 * ```tsx
 * <Snackbar
 *   open={showMessage}
 *   autoHideDuration={3000}
 *   onClose={() => setShowMessage(false)}
 *   message="Item saved successfully"
 * />
 * ```
 */
export const Snackbar = React.forwardRef<HTMLDivElement, SnackbarProps>(
  (
    {
      open,
      onClose,
      autoHideDuration = 5000,
      message,
      action,
      anchorOrigin = { vertical: 'bottom', horizontal: 'left' },
      className,
      style,
      children,
      ...rest
    },
    ref,
  ) => {
    React.useEffect(() => {
      if (open && autoHideDuration != null && onClose) {
        const timer = setTimeout(() => onClose(undefined, 'timeout'), autoHideDuration);
        return () => clearTimeout(timer);
      }
    }, [open, autoHideDuration, onClose]);

    if (!open) return null;

    const posKey = `${anchorOrigin.vertical}-${anchorOrigin.horizontal}`;
    const posClass = positionClasses[posKey] ?? positionClasses['bottom-left'];

    return (
      <div
        ref={ref}
        role="presentation"
        className={`fixed z-[1400] ${posClass} ${className ?? ''}`}
        style={style}
        {...rest}
      >
        {children ?? (
          <div className="flex items-center gap-2 rounded-md bg-neutral-800 px-4 py-3 text-sm text-white shadow-lg dark:bg-neutral-700">
            {message && <span className="flex-1">{message}</span>}
            {action}
          </div>
        )}
      </div>
    );
  },
);

Snackbar.displayName = 'Snackbar';
