import React, { forwardRef, useId } from 'react';
import { tokens } from '@ghatana/tokens';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends Omit<React.SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  /** Options to display */
  options?: SelectOption[];
  /** Label for the select */
  label?: string;
  /** Error message */
  error?: string;
  /** Helper text */
  helperText?: string;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Full width */
  fullWidth?: boolean;
  /** Placeholder text */
  placeholder?: string;

  /** MUI-compat (ignored for native select) */
  labelId?: string;
  variant?: 'outlined' | 'filled' | 'standard';
  displayEmpty?: boolean;
  renderValue?: (value: unknown) => React.ReactNode;

  /** MUI-compat styling */
  sx?: SxProps;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  (
    {
      options,
      label,
      error,
      helperText,
      size = 'md',
      fullWidth = false,
      placeholder,
      disabled,
      className,
      children,
      sx,
      id: providedId,
      labelId: _labelId,
      variant: _variant,
      displayEmpty: _displayEmpty,
      renderValue: _renderValue,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const selectId = providedId || generatedId;
    const errorId = error ? `${selectId}-error` : undefined;
    const helperTextId = helperText ? `${selectId}-helper` : undefined;

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

    const selectStyles: React.CSSProperties = {
      ...sizeStyles[size],
      width: fullWidth ? '100%' : 'auto',
      fontFamily: tokens.typography.fontFamily.sans,
      fontWeight: tokens.typography.fontWeight.normal,
      lineHeight: tokens.typography.lineHeight.normal,
      color: disabled ? tokens.colors.neutral[400] : tokens.colors.neutral[900],
      backgroundColor: disabled ? tokens.colors.neutral[50] : tokens.colors.white,
      border: `${tokens.borderWidth[1]} solid ${error ? tokens.colors.error[500] : tokens.colors.neutral[300]
        }`,
      borderRadius: tokens.borderRadius.md,
      outline: 'none',
      cursor: disabled ? 'not-allowed' : 'pointer',
      transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
      appearance: 'none',
      backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23666' d='M6 9L1 4h10z'/%3E%3C/svg%3E")`,
      backgroundRepeat: 'no-repeat',
      backgroundPosition: `right ${tokens.spacing[2]} center`,
      paddingRight: tokens.spacing[8],
      ...sxToStyle(sx),
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
          <label htmlFor={selectId} style={labelStyles}>
            {label}
            {props.required && (
              <span style={{ color: tokens.colors.error[500], marginLeft: tokens.spacing[1] }}>
                *
              </span>
            )}
          </label>
        )}
        <select
          ref={ref}
          id={selectId}
          disabled={disabled}
          aria-invalid={!!error}
          aria-describedby={error ? errorId : helperTextId}
          style={selectStyles}
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
        >
          {placeholder && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {children ??
            options?.map((option) => (
              <option key={option.value} value={option.value} disabled={option.disabled}>
                {option.label}
              </option>
            ))}
        </select>
        {(error || helperText) && (
          <div id={error ? errorId : helperTextId} style={helperTextStyles} role={error ? 'alert' : undefined}>
            {error || helperText}
          </div>
        )}
      </div>
    );
  }
);

Select.displayName = 'Select';
