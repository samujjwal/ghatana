import { forwardRef, useId } from 'react';

import type { InputHTMLAttributes } from 'react';

/**
 *
 */
export interface RadioProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  /**
   * Radio label
   */
  label?: string;
  
  /**
   * Radio size variant
   */
  size?: 'small' | 'medium' | 'large';
}

/**
 * Radio button component for single selection
 * 
 * @example
 * ```tsx
 * <Radio
 *   name="plan"
 *   value="basic"
 *   label="Basic Plan"
 *   checked={plan === 'basic'}
 *   onChange={(e) => setPlan(e.target.value)}
 * />
 * ```
 */
export const Radio = forwardRef<HTMLInputElement, RadioProps>(
  (
    {
      label,
      size = 'medium',
      className = '',
      id,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const radioId = id || generatedId;

    const sizeMap = {
      small: '16px',
      medium: '20px',
      large: '24px',
    };

    const labelContainerStyle: React.CSSProperties = {
      display: 'inline-flex',
      alignItems: 'center',
      gap: '0.5rem',
      cursor: props.disabled ? 'not-allowed' : 'pointer',
      opacity: props.disabled ? 0.6 : 1,
    };

    const radioStyle: React.CSSProperties = {
      width: sizeMap[size],
      height: sizeMap[size],
      cursor: props.disabled ? 'not-allowed' : 'pointer',
      accentColor: '#2196f3',
    };

    const labelTextStyle: React.CSSProperties = {
      fontSize: size === 'small' ? '0.875rem' : size === 'large' ? '1.125rem' : '1rem',
      color: '#212121',
      userSelect: 'none',
    };

    return (
      <label htmlFor={radioId} style={labelContainerStyle} className={className}>
        <input
          ref={ref}
          type="radio"
          id={radioId}
          style={radioStyle}
          {...props}
        />
        {label && <span style={labelTextStyle}>{label}</span>}
      </label>
    );
  }
);

Radio.displayName = 'Radio';

/**
 *
 */
export interface RadioGroupProps {
  /**
   * Radio group label
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
   * Radio buttons
   */
  children: React.ReactNode;
  
  /**
   * Layout direction
   */
  direction?: 'row' | 'column';
  
  /**
   * Gap between radio buttons
   */
  gap?: string;
}

/**
 * Radio group component for organizing radio buttons
 * 
 * @example
 * ```tsx
 * <RadioGroup label="Select a plan" direction="column">
 *   <Radio name="plan" value="basic" label="Basic" />
 *   <Radio name="plan" value="pro" label="Pro" />
 *   <Radio name="plan" value="enterprise" label="Enterprise" />
 * </RadioGroup>
 * ```
 */
export function RadioGroup({
  label,
  helperText,
  error,
  children,
  direction = 'column',
  gap = '0.75rem',
}: RadioGroupProps) {
  const hasError = Boolean(error);

  const containerStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
  };

  const groupStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: direction,
    gap,
  };

  const labelStyle: React.CSSProperties = {
    fontSize: '0.875rem',
    fontWeight: 500,
    color: hasError ? '#f44336' : '#424242',
  };

  const helperStyle: React.CSSProperties = {
    fontSize: '0.75rem',
    color: hasError ? '#f44336' : '#757575',
  };

  return (
    <div style={containerStyle}>
      {label && <div style={labelStyle}>{label}</div>}
      <div style={groupStyle} role="radiogroup">
        {children}
      </div>
      {(helperText || error) && (
        <div style={helperStyle} role={error ? 'alert' : undefined}>
          {error || helperText}
        </div>
      )}
    </div>
  );
}
