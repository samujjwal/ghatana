import { forwardRef, useState, useId } from 'react';

import type { InputHTMLAttributes } from 'react';

/**
 *
 */
export type SliderColor = 'primary' | 'success' | 'error' | 'warning';
/**
 *
 */
export type SliderSize = 'small' | 'medium' | 'large';

/**
 *
 */
export interface SliderProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size' | 'type'> {
  /**
   * Slider label
   */
  label?: string;
  
  /**
   * Slider color
   */
  color?: SliderColor;
  
  /**
   * Slider size
   */
  size?: SliderSize;
  
  /**
   * Show value label
   */
  showValue?: boolean;
  
  /**
   * Value formatter
   */
  formatValue?: (value: number) => string;
  
  /**
   * Show marks
   */
  marks?: boolean | { value: number; label: string }[];
  
  /**
   * Helper text
   */
  helperText?: string;

  /**
   * Alias for MUI compatibility. When set to `auto` or `on`, value labels are shown.
   */
  valueLabelDisplay?: 'off' | 'auto' | 'on';
}

/**
 * Slider component for selecting values from a range
 * 
 * @example
 * ```tsx
 * <Slider
 *   label="Volume"
 *   min={0}
 *   max={100}
 *   value={volume}
 *   onChange={(e) => setVolume(Number(e.target.value))}
 *   showValue
 * />
 * ```
 */
export const Slider = forwardRef<HTMLInputElement, SliderProps>(
  (
    {
      label,
      color = 'primary',
      size = 'medium',
      showValue = false,
      formatValue = (v) => String(v),
      marks = false,
      helperText,
      className = '',
      id,
      min = 0,
      max = 100,
      step = 1,
      value,
      defaultValue,
      disabled = false,
      valueLabelDisplay = 'off',
      onChange,
      ...inputProps
    },
    ref
  ) => {
    const generatedId = useId();
    const sliderId = id || generatedId;
    const [internalValue, setInternalValue] = useState(defaultValue || min);
    const currentValue = value !== undefined ? value : internalValue;
    const shouldDisplayValue = showValue || valueLabelDisplay === 'auto' || valueLabelDisplay === 'on';

    const colorMap = {
      primary: 'var(--ds-primary-500)',
      success: 'var(--ds-success-500)',
      error: 'var(--ds-error-500)',
      warning: 'var(--ds-warning-500)',
    };

    const sizeMap = {
      small: { height: '4px', thumb: '12px' },
      medium: { height: '6px', thumb: '16px' },
      large: { height: '8px', thumb: '20px' },
    };

    const sliderColor = colorMap[color];
    const dimensions = sizeMap[size];
    const percentage = ((Number(currentValue) - Number(min)) / (Number(max) - Number(min))) * 100;

    const containerStyle: React.CSSProperties = {
      display: 'flex',
      flexDirection: 'column',
      gap: '0.5rem',
      width: '100%',
    };

    const headerStyle: React.CSSProperties = {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
    };

    const labelStyle: React.CSSProperties = {
      fontSize: 'var(--ds-text-sm)',
      fontWeight: 500,
      color: 'var(--ds-neutral-700)',
    };

    const valueStyle: React.CSSProperties = {
      fontSize: 'var(--ds-text-sm)',
      fontWeight: 500,
      color: sliderColor,
    };

    const sliderContainerStyle: React.CSSProperties = {
      position: 'relative',
      width: '100%',
      padding: `${dimensions.thumb} 0`,
    };

    const trackStyle: React.CSSProperties = {
      position: 'absolute',
      width: '100%',
      height: dimensions.height,
      backgroundColor: 'var(--ds-neutral-200)',
      borderRadius: dimensions.height,
      top: '50%',
      transform: 'translateY(-50%)',
    };

    const fillStyle: React.CSSProperties = {
      position: 'absolute',
      height: '100%',
      backgroundColor: sliderColor,
      borderRadius: dimensions.height,
      width: `${percentage}%`,
      transition: 'width 0.1s',
    };

    const inputStyle: React.CSSProperties = {
      position: 'absolute',
      width: '100%',
      height: dimensions.height,
      opacity: 0,
      cursor: disabled ? 'not-allowed' : 'pointer',
      top: '50%',
      transform: 'translateY(-50%)',
      margin: 0,
    };

    const thumbStyle: React.CSSProperties = {
      position: 'absolute',
      width: dimensions.thumb,
      height: dimensions.thumb,
      backgroundColor: sliderColor,
      borderRadius: '50%',
      top: '50%',
      left: `${percentage}%`,
      transform: 'translate(-50%, -50%)',
      boxShadow: '0 2px 4px rgba(0, 0, 0, 0.2)',
      pointerEvents: 'none',
      transition: 'left 0.1s',
    };

    const helperStyle: React.CSSProperties = {
      fontSize: 'var(--ds-text-xs)',
      color: 'var(--ds-neutral-500)',
    };

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      setInternalValue(Number(e.target.value));
      onChange?.(e);
    };

    return (
      <div style={containerStyle} className={className}>
        {(label || shouldDisplayValue) && (
          <div style={headerStyle}>
            {label && <label htmlFor={sliderId} style={labelStyle}>{label}</label>}
            {shouldDisplayValue && <span style={valueStyle}>{formatValue(Number(currentValue))}</span>}
          </div>
        )}

        <div style={sliderContainerStyle}>
          <div style={trackStyle}>
            <div style={fillStyle} />
          </div>
          <div style={thumbStyle} />
          <input
            ref={ref}
            type="range"
            id={sliderId}
            min={min}
            max={max}
            step={step}
            value={currentValue}
            defaultValue={defaultValue}
            disabled={disabled}
            style={inputStyle}
            onChange={handleChange}
            {...inputProps}
          />
        </div>

        {helperText && <div style={helperStyle}>{helperText}</div>}
      </div>
    );
  }
);

Slider.displayName = 'Slider';
