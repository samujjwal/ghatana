import { forwardRef, useId } from 'react';

import type { InputHTMLAttributes } from 'react';

/**
 *
 */
export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  /**
   * Checkbox label
   */
  label?: string;
  
  /**
   * Helper text displayed below the checkbox
   */
  helperText?: string;
  
  /**
   * Error message
   */
  error?: string;
  
  /**
   * Checkbox size variant
   */
  size?: 'small' | 'medium' | 'large';
  
  /**
   * Indeterminate state
   */
  indeterminate?: boolean;
}

/**
 * Checkbox component for boolean selection
 * 
 * @example
 * ```tsx
 * <Checkbox
 *   label="Accept terms and conditions"
 *   checked={accepted}
 *   onChange={(e) => setAccepted(e.target.checked)}
 * />
 * ```
 */
export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(
  (
    {
      label,
      helperText,
      error,
      size = 'medium',
      indeterminate = false,
      className = '',
      id,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const checkboxId = id || generatedId;
    const hasError = Boolean(error);

    const sizeMap = {
      small: '16px',
      medium: '20px',
      large: '24px',
    };

    const containerStyle: React.CSSProperties = {
      display: 'inline-flex',
      flexDirection: 'column',
      gap: '0.25rem',
    };

    const labelContainerStyle: React.CSSProperties = {
      display: 'flex',
      alignItems: 'center',
      gap: '0.5rem',
      cursor: props.disabled ? 'not-allowed' : 'pointer',
      opacity: props.disabled ? 0.6 : 1,
    };

    const checkboxStyle: React.CSSProperties = {
      width: sizeMap[size],
      height: sizeMap[size],
      cursor: props.disabled ? 'not-allowed' : 'pointer',
      accentColor: hasError ? '#f44336' : '#2196f3',
    };

    const labelTextStyle: React.CSSProperties = {
      fontSize: size === 'small' ? '0.875rem' : size === 'large' ? '1.125rem' : '1rem',
      color: hasError ? '#f44336' : '#212121',
      userSelect: 'none',
    };

    const helperStyle: React.CSSProperties = {
      fontSize: '0.75rem',
      color: hasError ? '#f44336' : '#757575',
      marginLeft: `calc(${sizeMap[size]} + 0.5rem)`,
    };

    return (
      <div style={containerStyle} className={className}>
        <label htmlFor={checkboxId} style={labelContainerStyle}>
          <input
            ref={ref}
            type="checkbox"
            id={checkboxId}
            style={checkboxStyle}
            aria-invalid={hasError}
            aria-describedby={
              error ? `${checkboxId}-error` : helperText ? `${checkboxId}-helper` : undefined
            }
            {...props}
          />
          {label && <span style={labelTextStyle}>{label}</span>}
        </label>

        {(helperText || error) && (
          <div
            id={error ? `${checkboxId}-error` : `${checkboxId}-helper`}
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

Checkbox.displayName = 'Checkbox';
