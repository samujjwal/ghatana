import { forwardRef, useId } from 'react';

import type { SelectHTMLAttributes } from 'react';

/**
 *
 */
export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

/**
 *
 */
export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  /**
   * Select label
   */
  label?: string;
  
  /**
   * Helper text
   */
  helperText?: string;
  
  /**
   * Error message
   */
  error?: string;
  
  /**
   * Select size variant
   */
  size?: 'small' | 'medium' | 'large';
  
  /**
   * Full width select
   */
  fullWidth?: boolean;
  
  /**
   * Select options
   */
  options: SelectOption[];
  
  /**
   * Placeholder text
   */
  placeholder?: string;
}

/**
 * Select component for choosing from a list of options
 * 
 * @example
 * ```tsx
 * <Select
 *   label="Country"
 *   placeholder="Select a country"
 *   options={[
 *     { value: 'us', label: 'United States' },
 *     { value: 'uk', label: 'United Kingdom' },
 *     { value: 'ca', label: 'Canada' },
 *   ]}
 *   onChange={(e) => setCountry(e.target.value)}
 * />
 * ```
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  (
    {
      label,
      helperText,
      error,
      size = 'medium',
      fullWidth = false,
      options,
      placeholder,
      className = '',
      id,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const selectId = id || generatedId;
    const hasError = Boolean(error);

    const sizeStyles = {
      small: {
        padding: '0.375rem 2rem 0.375rem 0.75rem',
        fontSize: '0.875rem',
      },
      medium: {
        padding: '0.5rem 2.5rem 0.5rem 1rem',
        fontSize: '1rem',
      },
      large: {
        padding: '0.75rem 3rem 0.75rem 1.25rem',
        fontSize: '1.125rem',
      },
    };

    const containerStyle: React.CSSProperties = {
      display: 'flex',
      flexDirection: 'column',
      gap: '0.25rem',
      width: fullWidth ? '100%' : 'auto',
    };

    const selectStyle: React.CSSProperties = {
      ...sizeStyles[size],
      width: '100%',
      border: '1px solid',
      borderColor: hasError ? 'var(--color-error-main, #f44336)' : 'var(--color-grey-300, #e0e0e0)',
      borderRadius: '0.375rem',
      outline: 'none',
      transition: 'all 0.2s ease-in-out',
      backgroundColor: 'var(--color-background-paper, #ffffff)',
      cursor: 'pointer',
      appearance: 'none',
      backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23757575' d='M6 9L1 4h10z'/%3E%3C/svg%3E")`,
      backgroundRepeat: 'no-repeat',
      backgroundPosition: 'right 0.75rem center',
    };

    const labelStyle: React.CSSProperties = {
      fontSize: '0.875rem',
      fontWeight: 500,
      color: hasError ? 'var(--color-error-main, #f44336)' : 'var(--color-text-primary, #424242)',
      marginBottom: '0.25rem',
    };

    const helperStyle: React.CSSProperties = {
      fontSize: '0.75rem',
      color: hasError ? 'var(--color-error-main, #f44336)' : 'var(--color-text-secondary, #757575)',
      marginTop: '0.25rem',
    };

    return (
      <div style={containerStyle} className={className}>
        {label && (
          <label htmlFor={selectId} style={labelStyle}>
            {label}
            {props.required && <span style={{ color: 'var(--color-error-main, #f44336)', marginLeft: '0.25rem' }}>*</span>}
          </label>
        )}
        
        <select
          ref={ref}
          id={selectId}
          style={selectStyle}
          aria-invalid={hasError}
          aria-describedby={
            error ? `${selectId}-error` : helperText ? `${selectId}-helper` : undefined
          }
          {...props}
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {options.map((option) => (
            <option
              key={option.value}
              value={option.value}
              disabled={option.disabled}
            >
              {option.label}
            </option>
          ))}
        </select>

        {(helperText || error) && (
          <div
            id={error ? `${selectId}-error` : `${selectId}-helper`}
            style={helperStyle}
            role={error ? 'alert' : undefined}
          >
            {error || helperText}
          </div>
        )}
      </div>
    );
  }
);

Select.displayName = 'Select';
