import React, { forwardRef, useRef } from 'react';
import { tokens } from '@ghatana/tokens';

export interface DatePickerProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  /** Label for the date picker */
  label?: string;
  /** Error message */
  error?: string;
  /** Helper text */
  helperText?: string;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Full width */
  fullWidth?: boolean;
  /** Date picker mode */
  mode?: 'date' | 'datetime-local' | 'time' | 'month' | 'week';
}

export const DatePicker = forwardRef<HTMLInputElement, DatePickerProps>(
  (
    {
      label,
      error,
      helperText,
      size = 'md',
      fullWidth = false,
      mode = 'date',
      disabled,
      className,
      ...props
    },
    ref
  ) => {
    const datePickerId = useRef(`datepicker-${Math.random().toString(36).substr(2, 9)}`).current;
    const errorId = error ? `${datePickerId}-error` : undefined;
    const helperTextId = helperText ? `${datePickerId}-helper` : undefined;

    const sizeStyles = {
      sm: {
        padding: `${tokens.spacing[1]} ${tokens.spacing[2]}`,
        fontSize: tokens.typography.fontSize.sm,
        height: '32px',
      },
      md: {
        padding: `${tokens.spacing[2]} ${tokens.spacing[3]}`,
        fontSize: tokens.typography.fontSize.base,
        height: '40px',
      },
      lg: {
        padding: `${tokens.spacing[3]} ${tokens.spacing[4]}`,
        fontSize: tokens.typography.fontSize.lg,
        height: '48px',
      },
    };

    const inputStyles: React.CSSProperties = {
      ...sizeStyles[size],
      width: fullWidth ? '100%' : 'auto',
      fontFamily: tokens.typography.fontFamily.sans,
      fontWeight: tokens.typography.fontWeight.normal,
      lineHeight: tokens.typography.lineHeight.normal,
      color: disabled ? tokens.colors.neutral[400] : tokens.colors.neutral[900],
      backgroundColor: disabled ? tokens.colors.neutral[50] : tokens.colors.white,
      border: `${tokens.borderWidth[1]} solid ${
        error ? tokens.colors.error[500] : tokens.colors.neutral[300]
      }`,
      borderRadius: tokens.borderRadius.md,
      outline: 'none',
      cursor: disabled ? 'not-allowed' : 'pointer',
      transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    };

    const labelStyles: React.CSSProperties = {
      display: 'block',
      marginBottom: tokens.spacing[1],
      fontSize: tokens.typography.fontSize.sm,
      fontWeight: tokens.typography.fontWeight.medium,
      color: tokens.colors.neutral[700],
    };

    const helperTextStyles: React.CSSProperties = {
      marginTop: tokens.spacing[1],
      fontSize: tokens.typography.fontSize.xs,
      color: error ? tokens.colors.error[600] : tokens.colors.neutral[600],
    };

    return (
      <div style={{ width: fullWidth ? '100%' : 'auto' }} className={className}>
        {label && (
          <label htmlFor={datePickerId} style={labelStyles}>
            {label}
            {props.required && (
              <span style={{ color: tokens.colors.error[500], marginLeft: tokens.spacing[1] }}>
                *
              </span>
            )}
          </label>
        )}
        <input
          ref={ref}
          id={datePickerId}
          type={mode}
          disabled={disabled}
          aria-invalid={!!error}
          aria-describedby={error ? errorId : helperTextId}
          style={inputStyles}
          onFocus={(e) => {
            if (!disabled && !error) {
              e.currentTarget.style.borderColor = tokens.colors.primary[500];
              e.currentTarget.style.boxShadow = `0 0 0 3px ${tokens.colors.primary[100]}`;
            }
            props.onFocus?.(e);
          }}
          onBlur={(e) => {
            e.currentTarget.style.borderColor = error
              ? tokens.colors.error[500]
              : tokens.colors.neutral[300];
            e.currentTarget.style.boxShadow = 'none';
            props.onBlur?.(e);
          }}
          {...props}
        />
        {(error || helperText) && (
          <div id={error ? errorId : helperTextId} style={helperTextStyles} role={error ? 'alert' : undefined}>
            {error || helperText}
          </div>
        )}
      </div>
    );
  }
);

DatePicker.displayName = 'DatePicker';
