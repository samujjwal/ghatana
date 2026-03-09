import * as React from 'react';

export interface BackdropProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Whether the backdrop is visible */
  open: boolean;
  /** Called when backdrop is clicked */
  onClick?: (e: React.MouseEvent<HTMLDivElement>) => void;
  /** Whether the backdrop is invisible (transparent) @default false */
  invisible?: boolean;
  /** Transition duration in ms @default 225 */
  transitionDuration?: number;
  /** Additional CSS classes */
  className?: string;
  /** z-index @default 1200 */
  zIndex?: number;
  /** Content rendered on top of the backdrop */
  children?: React.ReactNode;
}

/**
 * Backdrop overlay component — covers the viewport with a semi-transparent overlay.
 * Drop-in replacement for MUI Backdrop using pure CSS.
 *
 * @example
 * ```tsx
 * <Backdrop open={isOpen} onClick={handleClose}>
 *   <Spinner />
 * </Backdrop>
 * ```
 */
export const Backdrop = React.forwardRef<HTMLDivElement, BackdropProps>(
  (
    {
      open,
      onClick,
      invisible = false,
      transitionDuration = 225,
      className,
      zIndex = 1200,
      children,
      style,
      ...rest
    },
    ref,
  ) => {
    if (!open) return null;

    return (
      <div
        ref={ref}
        role="presentation"
        onClick={onClick}
        className={`fixed inset-0 flex items-center justify-center ${invisible ? '' : 'bg-black/50'} ${className ?? ''}`}
        style={{
          zIndex,
          transition: `opacity ${transitionDuration}ms ease-in-out`,
          WebkitTapHighlightColor: 'transparent',
          ...style,
        }}
        {...rest}
      >
        {children}
      </div>
    );
  },
);

Backdrop.displayName = 'Backdrop';
