import * as React from 'react';
import { createPortal } from 'react-dom';
import { cn } from '@ghatana/utils';
import { lightColors, darkColors, fontSize as tokenFontSize, spacing, componentRadius } from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';
import { useId } from '../hooks/useId';

export type TooltipPlacement = 'top' | 'bottom' | 'left' | 'right';

export interface TooltipProps {
  content?: React.ReactNode;
  title?: React.ReactNode;
  children: React.ReactNode;
  placement?: TooltipPlacement;
  delay?: number;
  showArrow?: boolean;
  className?: string;
  /** Render tooltip inline instead of in a portal (useful in SSR) */
  disablePortal?: boolean;
}

/**
 * Lightweight tooltip without external dependencies.
 */
export function Tooltip({
  content,
  title,
  children,
  placement = 'top',
  delay = 200,
  showArrow = true,
  className,
  disablePortal = false,
}: TooltipProps) {
  const [visible, setVisible] = React.useState(false);
  const [coords, setCoords] = React.useState<{ top: number; left: number; width: number; height: number } | null>(null);
  const timeoutRef = React.useRef<number | null>(null);
  const wrapperRef = React.useRef<HTMLSpanElement>(null);
  const tooltipId = useId('gh-tooltip');

  const { resolvedTheme } = useTheme();
  const surface = resolvedTheme === 'dark' ? darkColors : lightColors;
  const spacingMap = spacing as Record<string, number>;
  const gap = spacingMap['1.5'] ?? 6;

  const tooltipContent = title ?? content ?? '';

  const clearDelay = () => {
    if (timeoutRef.current && typeof window !== 'undefined') {
      window.clearTimeout(timeoutRef.current);
      timeoutRef.current = null;
    }
  };

  const show = () => {
    clearDelay();
    if (typeof window === 'undefined') {
      setVisible(true);
      return;
    }
    timeoutRef.current = window.setTimeout(() => {
      if (wrapperRef.current) {
        const rect = wrapperRef.current.getBoundingClientRect();
        setCoords({
          top: rect.top + window.scrollY,
          left: rect.left + window.scrollX,
          width: rect.width,
          height: rect.height,
        });
        setVisible(true);
      }
    }, delay);
  };

  const hide = () => {
    clearDelay();
    setVisible(false);
  };

  React.useEffect(() => {
    return () => {
      clearDelay();
    };
  }, []);

  const renderTooltip = () => {
    if (!visible || !coords) return null;

    const styles: React.CSSProperties = {
      position: disablePortal ? 'absolute' : 'fixed',
      zIndex: 2000,
      pointerEvents: 'none',
      maxWidth: 280,
      backgroundColor: surface.background.elevated,
      color: surface.text.primary,
      borderRadius: componentRadius.tooltip,
      padding: `${spacingMap['1.5'] ?? 6}px ${spacingMap['2'] ?? 8}px`,
      boxShadow: `0 12px 40px rgba(15, 23, 42, ${resolvedTheme === 'dark' ? 0.45 : 0.18})`,
      fontSize: tokenFontSize.sm,
      lineHeight: 1.4,
      border: `1px solid ${surface.border}`,
    };

    const arrowSize = 8;
    const offset = gap + arrowSize;
    let top = coords.top;
    let left = coords.left;

    switch (placement) {
      case 'top':
        top = coords.top - offset;
        left = coords.left + coords.width / 2;
        break;
      case 'bottom':
        top = coords.top + coords.height + offset / 2;
        left = coords.left + coords.width / 2;
        break;
      case 'left':
        top = coords.top + coords.height / 2;
        left = coords.left - offset;
        break;
      case 'right':
        top = coords.top + coords.height / 2;
        left = coords.left + coords.width + offset / 2;
        break;
    }

    const transform =
      placement === 'top' || placement === 'bottom'
        ? 'translateX(-50%)'
        : 'translateY(-50%)';

    const contentNode = (
      <div
        id={tooltipId}
        role="tooltip"
        className={cn('gh-tooltip', className)}
        style={{ ...styles, top, left, transform }}
      >
        {tooltipContent}
        {showArrow && (
          <span
            aria-hidden="true"
            style={{
              position: 'absolute',
              width: 0,
              height: 0,
              borderStyle: 'solid',
              ...(placement === 'top' && {
                borderWidth: `${arrowSize}px ${arrowSize}px 0 ${arrowSize}px`,
                borderColor: `${surface.background.elevated} transparent transparent transparent`,
                bottom: -arrowSize,
                left: '50%',
                transform: 'translateX(-50%)',
              }),
              ...(placement === 'bottom' && {
                borderWidth: `0 ${arrowSize}px ${arrowSize}px ${arrowSize}px`,
                borderColor: `transparent transparent ${surface.background.elevated} transparent`,
                top: -arrowSize,
                left: '50%',
                transform: 'translateX(-50%)',
              }),
              ...(placement === 'left' && {
                borderWidth: `${arrowSize}px 0 ${arrowSize}px ${arrowSize}px`,
                borderColor: `transparent transparent transparent ${surface.background.elevated}`,
                right: -arrowSize,
                top: '50%',
                transform: 'translateY(-50%)',
              }),
              ...(placement === 'right' && {
                borderWidth: `${arrowSize}px ${arrowSize}px ${arrowSize}px 0`,
                borderColor: `transparent ${surface.background.elevated} transparent transparent`,
                left: -arrowSize,
                top: '50%',
                transform: 'translateY(-50%)',
              }),
            }}
          />
        )}
      </div>
    );

    if (disablePortal || typeof document === 'undefined') {
      return contentNode;
    }

    const container = document.body;
    return createPortal(contentNode, container);
  };

  const child =
    typeof children === 'string' || typeof children === 'number'
      ? <span>{children}</span>
      : children;

  const childProps: Record<string, unknown> = {
    onMouseEnter: (event: React.MouseEvent) => {
      (child as React.ReactElement)?.props?.onMouseEnter?.(event);
      show();
    },
    onMouseLeave: (event: React.MouseEvent) => {
      (child as React.ReactElement)?.props?.onMouseLeave?.(event);
      hide();
    },
    onFocus: (event: React.FocusEvent) => {
      (child as React.ReactElement)?.props?.onFocus?.(event);
      show();
    },
    onBlur: (event: React.FocusEvent) => {
      (child as React.ReactElement)?.props?.onBlur?.(event);
      hide();
    },
    'aria-describedby': tooltipContent ? tooltipId : undefined,
  };

  if (React.isValidElement(child)) {
    const childDisabled = (child.props as Record<string, unknown>)?.disabled;
    if (childDisabled) {
      return (
        <span ref={wrapperRef} style={{ display: 'inline-flex' }}>
          {React.cloneElement(child, { ...childProps })}
          {renderTooltip()}
        </span>
      );
    }

    return (
      <span ref={wrapperRef} style={{ display: 'inline-flex' }}>
        {React.cloneElement(child, { ...childProps })}
        {renderTooltip()}
      </span>
    );
  }

  return (
    <span ref={wrapperRef} style={{ display: 'inline-flex' }} {...childProps}>
      {child}
      {renderTooltip()}
    </span>
  );
}
