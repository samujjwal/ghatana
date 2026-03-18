import * as React from 'react';

// ──────────────────────────────────────────────────────────────────────────
// Shared transition types
// ──────────────────────────────────────────────────────────────────────────

export interface TransitionProps {
  /** Whether the content is visible */
  in: boolean;
  /** Duration in ms @default 225 */
  timeout?: number | { enter?: number; exit?: number };
  /** Easing function @default 'ease-in-out' */
  easing?: string;
  /** Unmount when hidden @default false */
  unmountOnExit?: boolean;
  /** Mount when enter @default false */
  mountOnEnter?: boolean;
  /** Called when enter starts */
  onEnter?: () => void;
  /** Called when enter completes */
  onEntered?: () => void;
  /** Called when exit starts */
  onExit?: () => void;
  /** Called when exit completes */
  onExited?: () => void;
  /** Additional CSS classes */
  className?: string;
  /** Additional inline styles */
  style?: React.CSSProperties;
  /** Content to transition */
  children?: React.ReactNode;
}

function resolveTimeout(timeout: number | { enter?: number; exit?: number } | undefined, phase: 'enter' | 'exit'): number {
  if (typeof timeout === 'number') return timeout;
  if (typeof timeout === 'object') return timeout[phase] ?? 225;
  return 225;
}

// ──────────────────────────────────────────────────────────────────────────
// Fade
// ──────────────────────────────────────────────────────────────────────────

export interface FadeProps extends TransitionProps {}

/**
 * Fade transition — animates opacity from 0 ↔ 1.
 */
export const Fade = React.forwardRef<HTMLDivElement, FadeProps>(
  ({ in: isIn, timeout = 225, easing = 'ease-in-out', unmountOnExit = false, onEnter, onEntered, onExit, onExited, className, style, children }, ref) => {
    const [mounted, setMounted] = React.useState(isIn);
    const enterMs = resolveTimeout(timeout, 'enter');
    const exitMs = resolveTimeout(timeout, 'exit');

    React.useEffect(() => {
      if (isIn) {
        setMounted(true);
        onEnter?.();
        const t = setTimeout(() => onEntered?.(), enterMs);
        return () => clearTimeout(t);
      } else {
        onExit?.();
        const t = setTimeout(() => { if (unmountOnExit) setMounted(false); onExited?.(); }, exitMs);
        return () => clearTimeout(t);
      }
    }, [isIn]); // eslint-disable-line react-hooks/exhaustive-deps

    if (unmountOnExit && !mounted && !isIn) return null;

    return (
      <div
        ref={ref}
        className={className}
        style={{
          opacity: isIn ? 1 : 0,
          transition: `opacity ${isIn ? enterMs : exitMs}ms ${easing}`,
          ...style,
        }}
      >
        {children}
      </div>
    );
  },
);
Fade.displayName = 'Fade';

// ──────────────────────────────────────────────────────────────────────────
// Slide
// ──────────────────────────────────────────────────────────────────────────

export interface SlideProps extends TransitionProps {
  /** Direction the element slides from @default 'down' */
  direction?: 'up' | 'down' | 'left' | 'right';
}

const slideTransforms: Record<string, string> = {
  up: 'translateY(100%)',
  down: 'translateY(-100%)',
  left: 'translateX(100%)',
  right: 'translateX(-100%)',
};

/**
 * Slide transition — slides content in/out from a given direction.
 */
export const Slide = React.forwardRef<HTMLDivElement, SlideProps>(
  ({ in: isIn, direction = 'down', timeout = 225, easing = 'ease-in-out', unmountOnExit = false, onEnter, onEntered, onExit, onExited, className, style, children }, ref) => {
    const [mounted, setMounted] = React.useState(isIn);
    const enterMs = resolveTimeout(timeout, 'enter');
    const exitMs = resolveTimeout(timeout, 'exit');

    React.useEffect(() => {
      if (isIn) {
        setMounted(true);
        onEnter?.();
        const t = setTimeout(() => onEntered?.(), enterMs);
        return () => clearTimeout(t);
      } else {
        onExit?.();
        const t = setTimeout(() => { if (unmountOnExit) setMounted(false); onExited?.(); }, exitMs);
        return () => clearTimeout(t);
      }
    }, [isIn]); // eslint-disable-line react-hooks/exhaustive-deps

    if (unmountOnExit && !mounted && !isIn) return null;

    return (
      <div
        ref={ref}
        className={className}
        style={{
          transform: isIn ? 'translate(0, 0)' : slideTransforms[direction],
          transition: `transform ${isIn ? enterMs : exitMs}ms ${easing}`,
          ...style,
        }}
      >
        {children}
      </div>
    );
  },
);
Slide.displayName = 'Slide';

// ──────────────────────────────────────────────────────────────────────────
// Grow
// ──────────────────────────────────────────────────────────────────────────

export interface GrowProps extends TransitionProps {}

/**
 * Grow transition — scales content from 0 ↔ 1 with opacity.
 */
export const Grow = React.forwardRef<HTMLDivElement, GrowProps>(
  ({ in: isIn, timeout = 225, easing = 'ease-in-out', unmountOnExit = false, onEnter, onEntered, onExit, onExited, className, style, children }, ref) => {
    const [mounted, setMounted] = React.useState(isIn);
    const enterMs = resolveTimeout(timeout, 'enter');
    const exitMs = resolveTimeout(timeout, 'exit');

    React.useEffect(() => {
      if (isIn) {
        setMounted(true);
        onEnter?.();
        const t = setTimeout(() => onEntered?.(), enterMs);
        return () => clearTimeout(t);
      } else {
        onExit?.();
        const t = setTimeout(() => { if (unmountOnExit) setMounted(false); onExited?.(); }, exitMs);
        return () => clearTimeout(t);
      }
    }, [isIn]); // eslint-disable-line react-hooks/exhaustive-deps

    if (unmountOnExit && !mounted && !isIn) return null;

    return (
      <div
        ref={ref}
        className={className}
        style={{
          opacity: isIn ? 1 : 0,
          transform: isIn ? 'scale(1)' : 'scale(0.75)',
          transformOrigin: 'center',
          transition: `opacity ${isIn ? enterMs : exitMs}ms ${easing}, transform ${isIn ? enterMs : exitMs}ms ${easing}`,
          ...style,
        }}
      >
        {children}
      </div>
    );
  },
);
Grow.displayName = 'Grow';

// ──────────────────────────────────────────────────────────────────────────
// Zoom
// ──────────────────────────────────────────────────────────────────────────

export interface ZoomProps extends TransitionProps {}

/**
 * Zoom transition — scales content from 0 ↔ 1 (centered).
 */
export const Zoom = React.forwardRef<HTMLDivElement, ZoomProps>(
  ({ in: isIn, timeout = 225, easing = 'ease-in-out', unmountOnExit = false, onEnter, onEntered, onExit, onExited, className, style, children }, ref) => {
    const [mounted, setMounted] = React.useState(isIn);
    const enterMs = resolveTimeout(timeout, 'enter');
    const exitMs = resolveTimeout(timeout, 'exit');

    React.useEffect(() => {
      if (isIn) {
        setMounted(true);
        onEnter?.();
        const t = setTimeout(() => onEntered?.(), enterMs);
        return () => clearTimeout(t);
      } else {
        onExit?.();
        const t = setTimeout(() => { if (unmountOnExit) setMounted(false); onExited?.(); }, exitMs);
        return () => clearTimeout(t);
      }
    }, [isIn]); // eslint-disable-line react-hooks/exhaustive-deps

    if (unmountOnExit && !mounted && !isIn) return null;

    return (
      <div
        ref={ref}
        className={className}
        style={{
          transform: isIn ? 'scale(1)' : 'scale(0)',
          transformOrigin: 'center',
          transition: `transform ${isIn ? enterMs : exitMs}ms ${easing}`,
          ...style,
        }}
      >
        {children}
      </div>
    );
  },
);
Zoom.displayName = 'Zoom';
