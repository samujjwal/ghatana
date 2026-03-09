import * as React from 'react';
import { cn } from '@ghatana/utils';
import { lightColors, darkColors, componentRadius } from '@ghatana/tokens';
import { useTheme } from '@ghatana/theme';

export type SkeletonVariant = 'text' | 'circular' | 'rectangular';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: SkeletonVariant;
  width?: number | string;
  height?: number | string;
  animation?: 'pulse' | 'wave' | 'none';
}

/**
 * Skeleton loading placeholder.
 */
export const Skeleton = React.forwardRef<HTMLDivElement, SkeletonProps>((props, ref) => {
  const {
    variant = 'text',
    width,
    height,
    style,
    className,
    animation = 'pulse',
    ...rest
  } = props;

  const { resolvedTheme } = useTheme();
  const _surface = resolvedTheme === 'dark' ? darkColors : lightColors;

  const defaultHeight = variant === 'text' ? '1em' : variant === 'circular' ? 40 : 96;
  const resolvedWidth = typeof width === 'number' ? `${width}px` : width ?? '100%';
  const resolvedHeight = typeof height === 'number' ? `${height}px` : height ?? defaultHeight;

  const borderRadius =
    variant === 'circular'
      ? componentRadius.avatar
      : variant === 'rectangular'
      ? componentRadius.panel
      : componentRadius.card;

  const animationStyle: React.CSSProperties =
    animation === 'pulse'
      ? { animation: 'gh-skeleton-pulse 1.6s ease-in-out infinite' }
      : animation === 'wave'
      ? {
          position: 'relative',
          overflow: 'hidden',
        }
      : {};

  return (
    <div
      ref={ref}
      className={cn('gh-skeleton', className)}
      style={{
        width: resolvedWidth,
        height: resolvedHeight,
        borderRadius,
        background:
          resolvedTheme === 'dark'
            ? 'linear-gradient(90deg, rgba(71,85,105,0.55) 25%, rgba(100,116,139,0.45) 37%, rgba(71,85,105,0.55) 63%)'
            : 'linear-gradient(90deg, rgba(226,232,240,0.9) 25%, rgba(248,250,252,0.95) 37%, rgba(226,232,240,0.9) 63%)',
        backgroundSize: '400% 100%',
        ...animationStyle,
        ...style,
      }}
      aria-busy="true"
      aria-live="polite"
      {...rest}
    >
      {animation === 'wave' ? (
        <span
          aria-hidden="true"
          style={{
            position: 'absolute',
            transform: 'translateX(-100%)',
            top: 0,
            left: 0,
            height: '100%',
            width: '100%',
            background:
              resolvedTheme === 'dark'
                ? 'linear-gradient(90deg, transparent, rgba(148,163,184,0.35), transparent)'
                : 'linear-gradient(90deg, transparent, rgba(255,255,255,0.7), transparent)',
            animation: 'gh-skeleton-wave 1.6s linear infinite',
          }}
        />
      ) : null}
      <style>
        {`
        @keyframes gh-skeleton-pulse {
          0% { background-position: 0% 50%; }
          50% { background-position: 100% 50%; }
          100% { background-position: 0% 50%; }
        }
        @keyframes gh-skeleton-wave {
          0% { transform: translateX(-100%); }
          100% { transform: translateX(100%); }
        }
        `}
      </style>
    </div>
  );
});

Skeleton.displayName = 'Skeleton';
