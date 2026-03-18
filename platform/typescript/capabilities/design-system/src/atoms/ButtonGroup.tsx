import * as React from 'react';

export interface ButtonGroupProps extends React.HTMLAttributes<HTMLDivElement> {
  /** Variant for all buttons @default 'outlined' */
  variant?: 'contained' | 'outlined' | 'text';
  /** Size for all buttons @default 'md' */
  size?: 'sm' | 'md' | 'lg';
  /** Orientation @default 'horizontal' */
  orientation?: 'horizontal' | 'vertical';
  /** Whether the group takes full width */
  fullWidth?: boolean;
  /** Disable all buttons in the group */
  disabled?: boolean;
  /** Color theme @default 'primary' */
  color?: 'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success';
  /** Additional CSS classes */
  className?: string;
  /** Button elements */
  children: React.ReactNode;
}

/**
 * ButtonGroup — groups related buttons together with connected styling.
 * Drop-in replacement for MUI ButtonGroup using Tailwind CSS.
 *
 * @example
 * ```tsx
 * <ButtonGroup variant="outlined">
 *   <button>One</button>
 *   <button>Two</button>
 *   <button>Three</button>
 * </ButtonGroup>
 * ```
 */
export const ButtonGroup = React.forwardRef<HTMLDivElement, ButtonGroupProps>(
  (
    {
      variant = 'outlined',
      size = 'md',
      orientation = 'horizontal',
      fullWidth = false,
      disabled = false,
      color = 'primary',
      className,
      children,
      ...rest
    },
    ref,
  ) => {
    const isVertical = orientation === 'vertical';

    const baseClasses = [
      'inline-flex',
      isVertical ? 'flex-col' : 'flex-row',
      fullWidth ? 'w-full' : '',
      disabled ? 'opacity-50 pointer-events-none' : '',
    ]
      .filter(Boolean)
      .join(' ');

    const childVariantClass =
      variant === 'outlined'
        ? 'border border-current'
        : variant === 'contained'
          ? 'bg-current text-white'
          : '';

    const sizeClasses: Record<string, string> = {
      sm: 'px-2 py-1 text-xs',
      md: 'px-3 py-1.5 text-sm',
      lg: 'px-4 py-2 text-base',
    };

    const colorClasses: Record<string, string> = {
      primary: 'text-blue-600 dark:text-blue-400',
      secondary: 'text-purple-600 dark:text-purple-400',
      error: 'text-red-600 dark:text-red-400',
      warning: 'text-amber-600 dark:text-amber-400',
      info: 'text-cyan-600 dark:text-cyan-400',
      success: 'text-green-600 dark:text-green-400',
    };

    return (
      <div
        ref={ref}
        role="group"
        className={`${baseClasses} ${className ?? ''}`}
        {...rest}
      >
        {React.Children.map(children, (child, index) => {
          if (!React.isValidElement(child)) return child;

          const isFirst = index === 0;
          const isLast = index === React.Children.count(children) - 1;

          const roundingClass = isVertical
            ? `${isFirst ? 'rounded-t-md' : 'rounded-t-none'} ${isLast ? 'rounded-b-md' : 'rounded-b-none'} ${!isFirst ? '-mt-px' : ''}`
            : `${isFirst ? 'rounded-l-md' : 'rounded-l-none'} ${isLast ? 'rounded-r-md' : 'rounded-r-none'} ${!isFirst ? '-ml-px' : ''}`;

          return React.cloneElement(child as React.ReactElement<Record<string, unknown>>, {
            className: `${childVariantClass} ${sizeClasses[size]} ${colorClasses[color]} ${roundingClass} hover:opacity-80 transition-opacity ${(child.props as Record<string, unknown>).className ?? ''}`.trim(),
            disabled: disabled || (child.props as Record<string, unknown>).disabled,
          });
        })}
      </div>
    );
  },
);

ButtonGroup.displayName = 'ButtonGroup';
