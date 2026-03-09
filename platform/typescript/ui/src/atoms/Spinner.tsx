import * as React from 'react';
import { cn } from '@ghatana/utils';

type SpinnerSize = 'xs' | 'sm' | 'md' | 'lg' | 'small' | 'medium' | 'large';

const sizeMap: Record<SpinnerSize, number> = {
  xs: 12,
  sm: 16,
  md: 20,
  lg: 24,
  small: 16,
  medium: 20,
  large: 24,
};

export interface SpinnerProps extends React.SVGProps<SVGSVGElement> {
  /**
   * Diameter of the spinner.
   */
  size?: SpinnerSize;

  /**
   * Stroke color.
   */
  color?: string;
}

/**
 * Accessible loading spinner.
 */
export const Spinner = React.forwardRef<SVGSVGElement, SpinnerProps>((props, ref) => {
  const { size = 'md', color = 'currentColor', className, ...rest } = props;
  const dimension = sizeMap[size];

  return (
    <svg
      ref={ref}
      role="status"
      viewBox="0 0 24 24"
      width={dimension}
      height={dimension}
      className={cn('gh-spinner', className)}
      style={{
        color,
        animation: `gh-spinner-rotate 0.9s linear infinite`,
      }}
      {...rest}
    >
      <title>Loading</title>
      <circle
        cx="12"
        cy="12"
        r="10"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
        fill="none"
        style={{
          opacity: 0.25,
        }}
      />
      <path
        d="M12 2a10 10 0 0 1 10 10"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
        fill="none"
        style={{
          opacity: 0.9,
        }}
      />
      <style>
        {`
          @keyframes gh-spinner-rotate {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}
      </style>
    </svg>
  );
});

Spinner.displayName = 'Spinner';
