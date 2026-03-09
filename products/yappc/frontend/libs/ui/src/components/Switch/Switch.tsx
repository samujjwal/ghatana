import { forwardRef, useId } from 'react';

import type { InputHTMLAttributes } from 'react';

/**
 *
 */
export type SwitchSize = 'small' | 'medium' | 'large';
/**
 *
 */
export type SwitchColor = 'primary' | 'success' | 'error' | 'warning';

/**
 *
 */
export interface SwitchProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'type'> {
  /**
   * Switch label
   */
  label?: string;
  
  /**
   * Switch size
   */
  size?: SwitchSize;
  
  /**
   * Switch color
   */
  color?: SwitchColor;
  
  /**
   * Helper text
   */
  helperText?: string;
  
  /**
   * Error message
   */
  error?: string;
}

/**
 * Switch component for toggle controls
 * 
 * @example
 * ```tsx
 * <Switch
 *   label="Enable notifications"
 *   checked={enabled}
 *   onChange={(e) => setEnabled(e.target.checked)}
 * />
 * ```
 */
export const Switch = forwardRef<HTMLInputElement, SwitchProps>(
  (
    {
      label,
      size = 'medium',
      color = 'primary',
      helperText,
      error,
      className = '',
      id,
      disabled = false,
      ...props
    },
    ref
  ) => {
    const generatedId = useId();
    const switchId = id || generatedId;
    const hasError = Boolean(error);

    const sizeMap = {
      small: { width: '32px', height: '18px', thumb: '14px' },
      medium: { width: '44px', height: '24px', thumb: '20px' },
      large: { width: '56px', height: '30px', thumb: '26px' },
    };

    const colorMap = {
      primary: 'var(--color-info-main, #2196f3)',
      success: 'var(--color-success-main, #4caf50)',
      error: 'var(--color-error-main, #f44336)',
      warning: 'var(--color-warning-main, #ff9800)',
    };

    const dimensions = sizeMap[size];
    const activeColor = colorMap[color];

    const containerStyle: React.CSSProperties = {
      display: 'inline-flex',
      flexDirection: 'column',
      gap: '0.25rem',
    };

    const labelContainerStyle: React.CSSProperties = {
      display: 'flex',
      alignItems: 'center',
      gap: '0.75rem',
      cursor: disabled ? 'not-allowed' : 'pointer',
      opacity: disabled ? 0.6 : 1,
    };

    const switchContainerStyle: React.CSSProperties = {
      position: 'relative',
      width: dimensions.width,
      height: dimensions.height,
      flexShrink: 0,
    };

    const inputStyle: React.CSSProperties = {
      opacity: 0,
      width: 0,
      height: 0,
      position: 'absolute',
    };

    const trackStyle: React.CSSProperties = {
      position: 'absolute',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      backgroundColor: hasError ? 'var(--color-error-main, #f44336)' : 'var(--color-grey-400, #bdbdbd)',
      borderRadius: dimensions.height,
      transition: 'background-color 0.2s',
      cursor: disabled ? 'not-allowed' : 'pointer',
    };

    const thumbStyle: React.CSSProperties = {
      position: 'absolute',
      top: '50%',
      left: '2px',
      transform: 'translateY(-50%)',
      width: dimensions.thumb,
      height: dimensions.thumb,
      backgroundColor: 'var(--color-common-white, #ffffff)',
      borderRadius: '50%',
      transition: 'transform 0.2s',
      boxShadow: '0 2px 4px rgba(0, 0, 0, 0.2)',
    };

    const labelTextStyle: React.CSSProperties = {
      fontSize: size === 'small' ? '0.875rem' : size === 'large' ? '1.125rem' : '1rem',
      color: hasError ? 'var(--color-error-main, #f44336)' : 'var(--color-text-primary, #212121)',
      userSelect: 'none',
    };

    const helperStyle: React.CSSProperties = {
      fontSize: '0.75rem',
      color: hasError ? 'var(--color-error-main, #f44336)' : 'var(--color-text-secondary, #757575)',
      marginLeft: `calc(${dimensions.width} + 0.75rem)`,
    };

    return (
      <div style={containerStyle} className={className}>
        <label htmlFor={switchId} style={labelContainerStyle}>
          <div style={switchContainerStyle}>
            <input
              ref={ref}
              type="checkbox"
              id={switchId}
              style={inputStyle}
              disabled={disabled}
              aria-invalid={hasError}
              aria-describedby={
                error ? `${switchId}-error` : helperText ? `${switchId}-helper` : undefined
              }
              {...props}
            />
            <span style={trackStyle} />
            <span style={thumbStyle} />
            <style>{`
              #${switchId}:checked + span {
                background-color: ${activeColor};
              }
              #${switchId}:checked + span + span {
                transform: translateY(-50%) translateX(calc(${dimensions.width} - ${dimensions.thumb} - 4px));
              }
              #${switchId}:focus-visible + span {
                outline: 2px solid ${activeColor};
                outline-offset: 2px;
              }
            `}</style>
          </div>
          {label && <span style={labelTextStyle}>{label}</span>}
        </label>

        {(helperText || error) && (
          <div
            id={error ? `${switchId}-error` : `${switchId}-helper`}
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

Switch.displayName = 'Switch';
