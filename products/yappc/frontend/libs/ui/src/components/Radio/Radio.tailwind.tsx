/**
 * Tailwind CSS Radio Component
 * 
 * Radio button built with native HTML and Tailwind CSS.
 * Uses native <input type="radio"> for full accessibility and keyboard support.
 * 
 * Note: Base UI does not yet have a Radio component, so this uses native HTML.
 * Will migrate to Base UI Radio when available.
 * 
 * @example
 * ```tsx
 * // Basic radio group
 * <RadioGroup value={value} onChange={setValue}>
 *   <Radio value="option1" label="Option 1" />
 *   <Radio value="option2" label="Option 2" />
 *   <Radio value="option3" label="Option 3" />
 * </RadioGroup>
 * 
 * // Different colors
 * <RadioGroup>
 *   <Radio value="1" label="Primary" colorScheme="primary" />
 *   <Radio value="2" label="Success" colorScheme="success" />
 * </RadioGroup>
 * 
 * // Sizes
 * <RadioGroup>
 *   <Radio value="1" label="Small" size="sm" />
 *   <Radio value="2" label="Large" size="lg" />
 * </RadioGroup>
 * 
 * // Disabled
 * <Radio value="1" label="Disabled" disabled />
 * ```
 */
import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 * RadioGroup component props
 */
export interface RadioGroupProps {
  /**
   * Currently selected value
   */
  value?: string;
  
  /**
   * Callback when selected value changes
   */
  onChange?: (value: string) => void;
  
  /**
   * Default value (uncontrolled)
   */
  defaultValue?: string;
  
  /**
   * Radio buttons
   */
  children: React.ReactNode;
  
  /**
   * Name attribute for radio group (shared across radios)
   */
  name: string;
  
  /**
   * Whether the group is disabled
   */
  disabled?: boolean;
  
  /**
   * Additional className for the group container
   */
  className?: string;
}

/**
 * RadioGroup Component - Container for Radio buttons
 * Provides shared state management for radio buttons
 * 
 * @param props - RadioGroup component props
 * @returns Rendered radio group
 */
export const RadioGroup: React.FC<RadioGroupProps> = ({
  value,
  onChange,
  defaultValue,
  children,
  name,
  disabled,
  className,
}) => {
  const [internalValue, setInternalValue] = React.useState(defaultValue || '');
  const isControlled = value !== undefined;
  const currentValue = isControlled ? value : internalValue;

  const handleChange = (newValue: string) => {
    if (!isControlled) {
      setInternalValue(newValue);
    }
    onChange?.(newValue);
  };

  return (
    <div className={cn('flex flex-col gap-2', className)} role="radiogroup">
      {React.Children.map(children, (child) => {
        if (React.isValidElement<RadioProps>(child) && child.type === Radio) {
          return React.cloneElement(child, {
            ...child.props,
            name,
            checked: child.props.value === currentValue,
            onChange: () => handleChange(child.props.value),
            disabled: disabled || child.props.disabled,
          } as Partial<RadioProps>);
        }
        return child;
      })}
    </div>
  );
};

RadioGroup.displayName = 'RadioGroup';

/**
 * Radio component props
 */
export interface RadioProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size' | 'type'> {
  /**
   * Value of this radio button
   */
  value: string;
  
  /**
   * Label text displayed next to radio button
   */
  label?: string;
  
  /**
   * Color scheme for the radio button
   * 
   * @default 'primary'
   */
  colorScheme?: 'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'grey';
  
  /**
   * Size of the radio button
   * 
   * - `sm`: 16px (h-4 w-4)
   * - `md`: 20px (h-5 w-5) - default
   * - `lg`: 24px (h-6 w-6)
   * 
   * @default 'md'
   */
  size?: 'sm' | 'md' | 'lg';
  
  /**
   * Additional className for the radio element
   */
  className?: string;
  
  /**
   * Additional className for the label
   */
  labelClassName?: string;
  
  /**
   * Additional className for the root container
   */
  containerClassName?: string;
}

/**
 * Radio Component - Single radio button
 * 
 * @param props - Radio component props
 * @param ref - Forwarded ref to radio input element
 * @returns Rendered radio button
 */
export const Radio = React.forwardRef<HTMLInputElement, RadioProps>(
  (
    {
      value,
      label,
      colorScheme = 'primary',
      size = 'md',
      className,
      labelClassName,
      containerClassName,
      disabled,
      checked,
      onChange,
      ...props
    },
    ref
  ) => {
    const inputId = React.useId();

    // Size-based classes
    const sizeClasses = {
      sm: {
        box: 'h-4 w-4',
        label: 'text-sm',
      },
      md: {
        box: 'h-5 w-5',
        label: 'text-base',
      },
      lg: {
        box: 'h-6 w-6',
        label: 'text-lg',
      },
    };

    // Color scheme classes (for checked state)
    const colorClasses = {
      primary: 'checked:border-primary-500 checked:bg-primary-500',
      secondary: 'checked:border-secondary-500 checked:bg-secondary-500',
      success: 'checked:border-success-500 checked:bg-success-500',
      error: 'checked:border-error-500 checked:bg-error-500',
      warning: 'checked:border-warning-500 checked:bg-warning-500',
      grey: 'checked:border-grey-700 checked:bg-grey-700',
    };

    // Focus ring colors
    const focusRingClasses = {
      primary: 'focus:ring-primary-500',
      secondary: 'focus:ring-secondary-500',
      success: 'focus:ring-success-500',
      error: 'focus:ring-error-500',
      warning: 'focus:ring-warning-500',
      grey: 'focus:ring-grey-500',
    };

    return (
      <label
        htmlFor={inputId}
        className={cn(
          'inline-flex items-center gap-2 cursor-pointer',
          disabled && 'opacity-50 cursor-not-allowed',
          containerClassName
        )}
      >
        <input
          ref={ref}
          id={inputId}
          type="radio"
          value={value}
          checked={checked}
          onChange={onChange}
          disabled={disabled}
          className={cn(
            // Base styles
            'appearance-none rounded-full border-2 transition-colors cursor-pointer',
            'bg-white border-grey-300',
            
            // Checked state - inner dot
            'checked:border-4',
            
            // Focus styles
            'focus:outline-none focus:ring-2 focus:ring-offset-2',
            focusRingClasses[colorScheme],
            
            // Size
            sizeClasses[size].box,
            
            // Color scheme
            colorClasses[colorScheme],
            
            // Disabled
            disabled && 'cursor-not-allowed',
            
            // Custom className
            className
          )}
          {...props}
        />

        {label && (
          <span
            className={cn(
              'select-none',
              sizeClasses[size].label,
              disabled && 'cursor-not-allowed',
              labelClassName
            )}
          >
            {label}
          </span>
        )}
      </label>
    );
  }
);

Radio.displayName = 'Radio';
