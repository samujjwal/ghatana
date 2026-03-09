import * as React from 'react';

import { cn } from '../../utils/cn';

import type { HTMLAttributes } from 'react';

/**
 *
 */
export type RatingSize = 'small' | 'medium' | 'large';

/**
 * Rating component props
 */
export interface RatingProps extends Omit<HTMLAttributes<HTMLDivElement>, 'onChange'> {
  /**
   * Current rating value
   */
  value?: number;
  
  /**
   * Default value
   */
  defaultValue?: number;
  
  /**
   * Maximum rating
   */
  max?: number;
  
  /**
   * Allow half ratings
   */
  precision?: 0.5 | 1;
  
  /**
   * Rating size
   */
  size?: RatingSize;
  
  /**
   * Read-only mode
   */
  readOnly?: boolean;
  
  /**
   * Disabled state
   */
  disabled?: boolean;
  
  /**
   * Change handler
   */
  onChange?: (value: number) => void;
  
  /**
   * Show value label
   */
  showValue?: boolean;
  
  /**
   * Show empty stars
   * @default true
   */
  showEmpty?: boolean;
  
  /**
   * Custom icon for filled state
   */
  icon?: React.ReactNode;
  
  /**
   * Custom icon for empty state
   */
  emptyIcon?: React.ReactNode;
  
  /**
   * Highlight selected star on hover
   * @default true
   */
  highlightSelectedOnly?: boolean;
  
  /**
   * Name attribute for form integration
   */
  name?: string;
}

/**
 * Default star icon (filled)
 */
const StarIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="currentColor"
    viewBox="0 0 24 24"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
  </svg>
);

/**
 * Default star icon (empty)
 */
const StarBorderIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg
    className={className}
    fill="currentColor"
    viewBox="0 0 24 24"
    xmlns="http://www.w3.org/2000/svg"
  >
    <path d="M22 9.24l-7.19-.62L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21 12 17.27 18.18 21l-1.63-7.03L22 9.24zM12 15.4l-3.76 2.27 1-4.28-3.32-2.88 4.38-.38L12 6.1l1.71 4.04 4.38.38-3.32 2.88 1 4.28L12 15.4z" />
  </svg>
);

/**
 * Rating component for displaying and collecting star ratings
 *
 * @example
 * ```tsx
 * <Rating value={3.5} precision={0.5} onChange={(value) => console.log(value)} />
 * ```
 */
export const Rating = React.forwardRef<HTMLDivElement, RatingProps>(
  (
    {
      value = 0,
      defaultValue,
      max = 5,
      onChange,
      readOnly = false,
      disabled = false,
      size = 'medium',
      precision = 1,
      showEmpty = true,
      icon,
      emptyIcon,
      highlightSelectedOnly = true,
      name,
      showValue = false,
      className,
      ...props
    },
    ref
  ) => {
    const [internalValue, setInternalValue] = React.useState(defaultValue ?? value);
    const [hoverValue, setHoverValue] = React.useState<number | null>(null);

    const currentValue = value !== undefined ? value : internalValue;
    const isInteractive = !readOnly && !disabled;

    // Size classes
    const sizeClasses = {
      small: 'w-4 h-4',
      medium: 'w-6 h-6',
      large: 'w-8 h-8',
    };

    const gapClasses = {
      small: 'gap-0.5',
      medium: 'gap-1',
      large: 'gap-1.5',
    };

    // Calculate the display value (hover takes precedence)
    const displayValue = hoverValue !== null ? hoverValue : currentValue;

    // Handle click on a star
    const handleClick = (index: number, isHalf: boolean) => {
      if (!isInteractive) return;

      const newValue = isHalf ? index + 0.5 : index + 1;
      setInternalValue(newValue);
      onChange?.(newValue);
    };

    // Handle mouse enter on a star
    const handleMouseEnter = (index: number, isHalf: boolean) => {
      if (!isInteractive) return;

      const newValue = isHalf ? index + 0.5 : index + 1;
      setHoverValue(newValue);
    };

    // Handle mouse leave from container
    const handleMouseLeave = () => {
      if (!isInteractive) return;
      setHoverValue(null);
    };

    // Handle keyboard navigation
    const handleKeyDown = (e: React.KeyboardEvent) => {
      if (!isInteractive) return;

      const step = precision;

      switch (e.key) {
        case 'ArrowRight':
        case 'ArrowUp':
          e.preventDefault();
          onChange?.(Math.min(currentValue + step, max));
          break;

        case 'ArrowLeft':
        case 'ArrowDown':
          e.preventDefault();
          onChange?.(Math.max(currentValue - step, 0));
          break;

        case 'Home':
          e.preventDefault();
          onChange?.(0);
          break;

        case 'End':
          e.preventDefault();
          onChange?.(max);
          break;

        default:
          // Check for number keys
          const num = parseInt(e.key, 10);
          if (!isNaN(num) && num >= 0 && num <= max) {
            e.preventDefault();
            onChange?.(num);
          }
          break;
      }
    };

    // Render a single star
    const renderStar = (index: number) => {
      const starValue = index + 1;
      const isFilled = displayValue >= starValue;
      const isHalfFilled = precision === 0.5 && displayValue >= starValue - 0.5 && displayValue < starValue;

      const showHover = isInteractive && !highlightSelectedOnly;
      const isHovered = hoverValue !== null && hoverValue >= starValue - (precision === 0.5 ? 0.5 : 0);

      return (
        <div
          key={index}
          className={cn('relative inline-flex', sizeClasses[size])}
          onMouseMove={(e) => {
            if (precision === 0.5 && isInteractive) {
              const rect = e.currentTarget.getBoundingClientRect();
              const isLeft = e.clientX - rect.left < rect.width / 2;
              handleMouseEnter(index, isLeft);
            }
          }}
          onMouseEnter={() => {
            if (precision === 1) {
              handleMouseEnter(index, false);
            }
          }}
          onClick={(e) => {
            if (precision === 0.5 && isInteractive) {
              const rect = e.currentTarget.getBoundingClientRect();
              const isLeft = e.clientX - rect.left < rect.width / 2;
              handleClick(index, isLeft);
            } else {
              handleClick(index, false);
            }
          }}
        >
          {/* Empty star (background) */}
          {showEmpty && (
            <div className={cn(
              'absolute inset-0',
              disabled ? 'text-grey-300' : 'text-grey-400',
              sizeClasses[size]
            )}>
              {emptyIcon || <StarBorderIcon className={sizeClasses[size]} />}
            </div>
          )}

          {/* Filled star (foreground) */}
          <div
            className={cn(
              'relative overflow-hidden transition-colors',
              disabled ? 'text-grey-400' : 'text-warning-500',
              showHover && isHovered && 'text-warning-600',
              isInteractive && 'cursor-pointer',
              sizeClasses[size]
            )}
            style={{
              width: isHalfFilled ? '50%' : isFilled ? '100%' : '0%',
            }}
          >
            {icon || <StarIcon className={sizeClasses[size]} />}
          </div>
        </div>
      );
    };

    return (
      <div
        ref={ref}
        className={cn(
          'inline-flex items-center',
          gapClasses[size],
          disabled && 'opacity-60 cursor-not-allowed',
          className
        )}
        onMouseLeave={handleMouseLeave}
        onKeyDown={handleKeyDown}
        tabIndex={isInteractive ? 0 : -1}
        role="radiogroup"
        aria-label={`Rating ${currentValue} out of ${max}`}
        aria-disabled={disabled}
        aria-readonly={readOnly}
        {...props}
      >
        {Array.from({ length: max }, (_, i) => renderStar(i))}

        {/* Show value label */}
        {showValue && (
          <span className={cn(
            'ml-2 font-medium',
            size === 'small' && 'text-sm',
            size === 'medium' && 'text-base',
            size === 'large' && 'text-lg',
            disabled ? 'text-grey-400' : 'text-grey-700'
          )}>
            {currentValue.toFixed(precision === 0.5 ? 1 : 0)}
          </span>
        )}

        {/* Hidden input for form integration */}
        {name && (
          <input
            type="hidden"
            name={name}
            value={currentValue}
          />
        )}
      </div>
    );
  }
);

Rating.displayName = 'Rating';
