import * as React from 'react';

export interface CollapseProps {
  /** Whether the content is visible */
  in: boolean;
  /** Duration of the transition in milliseconds @default 300 */
  timeout?: number | { enter?: number; exit?: number };
  /** CSS easing function @default 'ease-in-out' */
  easing?: string;
  /** Collapsed height @default '0px' */
  collapsedSize?: string | number;
  /** Content orientation @default 'vertical' */
  orientation?: 'vertical' | 'horizontal';
  /** Callback fired when enter transition starts */
  onEnter?: () => void;
  /** Callback fired when enter transition completes */
  onEntered?: () => void;
  /** Callback fired when exit transition starts */
  onExit?: () => void;
  /** Callback fired when exit transition completes */
  onExited?: () => void;
  /** Additional CSS classes */
  className?: string;
  /** Additional inline styles */
  style?: React.CSSProperties;
  /** Child content */
  children?: React.ReactNode;
  /** Unmount on exit @default false */
  unmountOnExit?: boolean;
  /** Component to render as @default 'div' */
  component?: React.ElementType;
}

/**
 * Collapse transition component — expands/collapses content with a smooth animation.
 * Drop-in replacement for MUI Collapse using pure CSS transitions.
 *
 * @example
 * ```tsx
 * <Collapse in={isOpen}>
 *   <div>Collapsible content</div>
 * </Collapse>
 * ```
 */
export const Collapse = React.forwardRef<HTMLDivElement, CollapseProps>(
  (
    {
      in: isOpen,
      timeout = 300,
      easing = 'ease-in-out',
      collapsedSize = '0px',
      orientation = 'vertical',
      onEnter,
      onEntered,
      onExit,
      onExited,
      className,
      style,
      children,
      unmountOnExit = false,
      component: Component = 'div',
      ...rest
    },
    ref,
  ) => {
    const innerRef = React.useRef<HTMLDivElement>(null);
    const [height, setHeight] = React.useState<string | number>(isOpen ? 'auto' : collapsedSize);
    const [mounted, setMounted] = React.useState(isOpen);

    const enterMs = typeof timeout === 'number' ? timeout : (timeout.enter ?? 300);
    const exitMs = typeof timeout === 'number' ? timeout : (timeout.exit ?? 300);

    React.useEffect(() => {
      if (isOpen) {
        setMounted(true);
        onEnter?.();
        const el = innerRef.current;
        if (el) {
          const dim =
            orientation === 'vertical' ? el.scrollHeight : el.scrollWidth;
          setHeight(dim);
          const timer = setTimeout(() => {
            setHeight('auto');
            onEntered?.();
          }, enterMs);
          return () => clearTimeout(timer);
        }
      } else {
        onExit?.();
        const el = innerRef.current;
        if (el) {
          const dim =
            orientation === 'vertical' ? el.scrollHeight : el.scrollWidth;
          setHeight(dim);
          // Force reflow before collapsing
          requestAnimationFrame(() => {
            requestAnimationFrame(() => {
              setHeight(collapsedSize);
            });
          });
          const timer = setTimeout(() => {
            if (unmountOnExit) setMounted(false);
            onExited?.();
          }, exitMs);
          return () => clearTimeout(timer);
        }
      }
    }, [isOpen]); // eslint-disable-line react-hooks/exhaustive-deps

    if (unmountOnExit && !mounted && !isOpen) {
      return null;
    }

    const isHorizontal = orientation === 'horizontal';
    const sizeProperty = isHorizontal ? 'width' : 'height';
    const transitionDuration = isOpen ? enterMs : exitMs;

    return (
      <Component
        ref={ref}
        className={className}
        style={{
          [sizeProperty]: typeof height === 'number' ? `${height}px` : height,
          overflow: 'hidden',
          transition: `${sizeProperty} ${transitionDuration}ms ${easing}`,
          visibility: !isOpen && height === collapsedSize ? 'hidden' : undefined,
          ...style,
        }}
        {...rest}
      >
        <div ref={innerRef}>{children}</div>
      </Component>
    );
  },
);

Collapse.displayName = 'Collapse';
