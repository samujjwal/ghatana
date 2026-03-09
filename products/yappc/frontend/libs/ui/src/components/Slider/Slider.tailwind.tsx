/**
 * Slider Component (Tailwind CSS + Base UI)
 * 
 * A range input slider component using Base UI Slider primitives styled with Tailwind CSS.
 * Supports single value, range (min/max), step increments, and marks.
 * 
 * @example
 * ```tsx
 * <SliderTailwind
 *   label="Volume"
 *   min={0}
 *   max={100}
 *   value={volume}
 *   onChange={setVolume}
 * />
 * ```
 */

import { Slider as BaseSlider } from '@base-ui/react/slider';
import React from 'react';

import { cn } from '../../utils/cn';

/**
 * Props for Slider component
 */
export interface SliderProps {
  /** Label for the slider */
  label?: string;
  /** Minimum value */
  min?: number;
  /** Maximum value */
  max?: number;
  /** Step increment */
  step?: number;
  /** Current value (single) or values (range) */
  value?: number | number[];
  /** Default value (uncontrolled) */
  defaultValue?: number | number[];
  /** Callback when value changes */
  onChange?: (value: number | number[]) => void;
  /** Whether the slider is disabled */
  disabled?: boolean;
  /** Whether the slider is vertical */
  orientation?: 'horizontal' | 'vertical';
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Color scheme */
  colorScheme?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';
  /** Show value label */
  showValue?: boolean;
  /** Format function for value display */
  formatValue?: (value: number) => string;
  /** Helper text */
  helperText?: string;
  /** Error message */
  error?: string;
  /** Mark positions */
  marks?: Array<{ value: number; label?: string }>;
  /** Additional CSS classes for container */
  className?: string;
  /** Additional CSS classes for label */
  labelClassName?: string;
}

/**
 * Slider component for range input
 * 
 * Built with Base UI Slider primitives and styled with Tailwind CSS.
 * Supports single value, range selection, and custom marks.
 */
export const Slider = React.forwardRef<HTMLDivElement, SliderProps>(
  (
    {
      label,
      min = 0,
      max = 100,
      step = 1,
      value,
      defaultValue,
      onChange,
      disabled = false,
      orientation = 'horizontal',
      size = 'md',
      colorScheme = 'primary',
      showValue = false,
      formatValue = (v) => v.toString(),
      helperText,
      error,
      marks,
      className,
      labelClassName,
    },
    _ref
  ) => {
    const hasError = Boolean(error);
    const isHorizontal = orientation === 'horizontal';

    // Normalize value to array for consistent handling
    const normalizedValue = Array.isArray(value) ? value : value !== undefined ? [value] : undefined;
    const displayValue = normalizedValue ? (normalizedValue.length === 1 ? formatValue(normalizedValue[0]) : `${formatValue(normalizedValue[0])} - ${formatValue(normalizedValue[1])}`) : '';

    // Size classes for track and thumb
    const trackSizeClasses = {
      sm: isHorizontal ? 'h-1' : 'w-1',
      md: isHorizontal ? 'h-2' : 'w-2',
      lg: isHorizontal ? 'h-3' : 'w-3',
    };

    const thumbSizeClasses = {
      sm: 'h-4 w-4',
      md: 'h-5 w-5',
      lg: 'h-6 w-6',
    };

    // Color scheme classes
    const colorClasses = {
      primary: {
        indicator: 'bg-primary-500',
        thumb: 'border-primary-600 bg-white ring-primary-500',
      },
      secondary: {
        indicator: 'bg-secondary-500',
        thumb: 'border-secondary-600 bg-white ring-secondary-500',
      },
      success: {
        indicator: 'bg-success-500',
        thumb: 'border-success-600 bg-white ring-success-500',
      },
      error: {
        indicator: 'bg-error-500',
        thumb: 'border-error-600 bg-white ring-error-500',
      },
      warning: {
        indicator: 'bg-warning-500',
        thumb: 'border-warning-600 bg-white ring-warning-500',
      },
      grey: {
        indicator: 'bg-grey-500',
        thumb: 'border-grey-600 bg-white ring-grey-500',
      },
    };

    return (
      <div
        className={cn(
          'w-full',
          isHorizontal ? 'py-2' : 'h-64 px-2',
          className
        )}
      >
        {(label || showValue) && (
          <div className="mb-4 flex items-center justify-between">
            {label && (
              <label
                className={cn(
                  'block text-sm font-medium text-grey-700',
                  disabled && 'opacity-50',
                  labelClassName
                )}
              >
                {label}
              </label>
            )}
            {showValue && (
              <span className="text-sm font-medium text-grey-700">
                {displayValue}
              </span>
            )}
          </div>
        )}

        <BaseSlider.Root
          min={min}
          max={max}
          step={step}
          value={value}
          defaultValue={defaultValue}
          onValueChange={(newValue) => onChange?.(Array.isArray(newValue) && newValue.length === 1 ? newValue[0] : newValue)}
          disabled={disabled}
          orientation={orientation}
          className={cn(
            'relative flex touch-none select-none items-center',
            isHorizontal ? 'w-full' : 'h-full flex-col',
            disabled && 'opacity-50 cursor-not-allowed'
          )}
        >
          <BaseSlider.Control
            className={cn(
              'relative flex-1',
              isHorizontal ? 'w-full' : 'h-full'
            )}
          >
            <BaseSlider.Track
              className={cn(
                'relative rounded-full bg-grey-200',
                trackSizeClasses[size],
                isHorizontal ? 'w-full' : 'h-full'
              )}
            >
              <BaseSlider.Indicator
                className={cn(
                  'absolute rounded-full',
                  trackSizeClasses[size],
                  hasError
                    ? 'bg-error-500'
                    : colorClasses[colorScheme].indicator
                )}
              />
            </BaseSlider.Track>

            <BaseSlider.Thumb
              className={cn(
                'block rounded-full border-2 shadow-md transition-shadow',
                'focus:outline-none focus:ring-2 focus:ring-offset-2',
                thumbSizeClasses[size],
                hasError
                  ? 'border-error-600 bg-white ring-error-500'
                  : colorClasses[colorScheme].thumb,
                disabled ? 'cursor-not-allowed' : 'cursor-grab active:cursor-grabbing'
              )}
            />

            {/* Second thumb for range slider */}
            {Array.isArray(value) && value.length > 1 && (
              <BaseSlider.Thumb
                className={cn(
                  'block rounded-full border-2 shadow-md transition-shadow',
                  'focus:outline-none focus:ring-2 focus:ring-offset-2',
                  thumbSizeClasses[size],
                  hasError
                    ? 'border-error-600 bg-white ring-error-500'
                    : colorClasses[colorScheme].thumb,
                  disabled ? 'cursor-not-allowed' : 'cursor-grab active:cursor-grabbing'
                )}
              />
            )}
          </BaseSlider.Control>

          {/* Marks */}
          {marks && marks.length > 0 && (
            <div
              className={cn(
                'absolute flex',
                isHorizontal
                  ? 'left-0 right-0 top-full mt-2 justify-between'
                  : 'bottom-0 left-full ml-2 top-0 flex-col justify-between'
              )}
            >
              {marks.map((mark) => {
                const position = ((mark.value - min) / (max - min)) * 100;
                return (
                  <div
                    key={mark.value}
                    className={cn(
                      'absolute text-xs text-grey-600',
                      isHorizontal
                        ? '-translate-x-1/2'
                        : '-translate-y-1/2'
                    )}
                    style={
                      isHorizontal
                        ? { left: `${position}%` }
                        : { top: `${100 - position}%` }
                    }
                  >
                    {mark.label || mark.value}
                  </div>
                );
              })}
            </div>
          )}
        </BaseSlider.Root>

        {(helperText || error) && (
          <p
            className={cn(
              'mt-2 text-sm',
              hasError ? 'text-error-600' : 'text-grey-500'
            )}
          >
            {error || helperText}
          </p>
        )}
      </div>
    );
  }
);
Slider.displayName = 'Slider';

// Export with Tailwind suffix for consistency
export { Slider as SliderTailwind };
