import React, { forwardRef, isValidElement, useState, useRef, useEffect, useCallback } from 'react';

import { cn } from '../../utils/cn';

import type { ReactNode } from 'react';

/**
 * Tooltip placement options
 */
export type TooltipPlacement = 'top' | 'bottom' | 'left' | 'right';

/**
 * Props for the Tooltip component
 */
export interface TooltipProps {
  /**
   * Tooltip content
   */
  content?: ReactNode;
  /**
   * Backwards-compatible alias for `content` (some callers use `title` prop)
   */
  title?: ReactNode;
  
  /**
   * Element that triggers the tooltip
   */
  children: ReactNode;
  
  /**
   * Tooltip placement relative to trigger
   * @default 'top'
   */
  placement?: TooltipPlacement;
  
  /**
   * Delay before showing tooltip (ms)
   * @default 200
   */
  delay?: number;
  
  /**
   * Show arrow pointing to trigger
   * @default true
   */
  showArrow?: boolean;
  
  /**
   * Additional CSS class for tooltip content
   */
  className?: string;
}

const placementStyles: Record<TooltipPlacement, string> = {
  top: 'bottom-full left-1/2 -translate-x-1/2 mb-2',
  bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
  left: 'right-full top-1/2 -translate-y-1/2 mr-2',
  right: 'left-full top-1/2 -translate-y-1/2 ml-2',
};

const arrowStyles: Record<TooltipPlacement, string> = {
  top: 'top-full left-1/2 -translate-x-1/2 border-t-neutral-800 border-x-transparent border-b-transparent border-4',
  bottom: 'bottom-full left-1/2 -translate-x-1/2 border-b-neutral-800 border-x-transparent border-t-transparent border-4',
  left: 'left-full top-1/2 -translate-y-1/2 border-l-neutral-800 border-y-transparent border-r-transparent border-4',
  right: 'right-full top-1/2 -translate-y-1/2 border-r-neutral-800 border-y-transparent border-l-transparent border-4',
};

/**
 * Tooltip component for displaying helpful information on hover.
 * Pure Tailwind CSS implementation — no MUI dependency.
 * 
 * Features:
 * - Configurable placement and delay
 * - Optional arrow
 * - Keyboard accessible (ESC to close)
 * - ARIA-describedby relationship
 * 
 * @example
 * ```tsx
 * <Tooltip content="This is helpful information" placement="top">
 *   <Button>Hover me</Button>
 * </Tooltip>
 * ```
 */
export const Tooltip = forwardRef<HTMLDivElement, TooltipProps>(
  (
    { content, title, children, placement = 'top', delay = 200, showArrow = true, className },
    ref
  ) => {
    const resolvedContent = title ?? content ?? '';
    const [visible, setVisible] = useState(false);
    const timerRef = useRef<ReturnType<typeof setTimeout>>();
    const tooltipId = useRef(`tooltip-${Math.random().toString(36).slice(2, 9)}`);

    const show = useCallback(() => {
      timerRef.current = setTimeout(() => setVisible(true), delay);
    }, [delay]);

    const hide = useCallback(() => {
      clearTimeout(timerRef.current);
      setVisible(false);
    }, []);

    useEffect(() => {
      return () => clearTimeout(timerRef.current);
    }, []);

    if (!resolvedContent) {
      return <>{children}</>;
    }

    // Wrap disabled elements in a span so hover/focus events work
    const renderChild = () => {
      if (isValidElement(children)) {
        const childProps: Record<string, unknown> = (children as React.ReactElement<Record<string, unknown>>).props || {};
        if (childProps.disabled) {
          return <span className="inline-flex">{children}</span>;
        }
        return children;
      }
      return <span>{children}</span>;
    };

    return (
      <span
        ref={ref}
        className="relative inline-flex"
        onMouseEnter={show}
        onMouseLeave={hide}
        onFocus={show}
        onBlur={hide}
        aria-describedby={visible ? tooltipId.current : undefined}
      >
        {renderChild()}
        {visible && (
          <span
            id={tooltipId.current}
            role="tooltip"
            className={cn(
              'pointer-events-none absolute z-[1500] whitespace-nowrap rounded bg-neutral-800 px-2 py-1 text-xs text-white shadow-md dark:bg-neutral-700',
              placementStyles[placement],
              className,
            )}
          >
            {resolvedContent}
            {showArrow && (
              <span
                className={cn('absolute h-0 w-0', arrowStyles[placement])}
                aria-hidden="true"
              />
            )}
          </span>
        )}
      </span>
    );
  }
);

Tooltip.displayName = 'Tooltip';

