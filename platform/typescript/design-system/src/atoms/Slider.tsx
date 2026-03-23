import React, { forwardRef, useState, useRef } from 'react';
import { tokens } from '@ghatana/tokens';

export interface SliderProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'type' | 'size'> {
  /** Label for the slider */
  label?: string;
  /** Minimum value */
  min?: number;
  /** Maximum value */
  max?: number;
  /** Step increment */
  step?: number;
  /** Show value label */
  showValue?: boolean;
  /** Value formatter */
  formatValue?: (value: number) => string;
  /** Full width */
  fullWidth?: boolean;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
}

export const Slider = forwardRef<HTMLInputElement, SliderProps>(
  (
    {
      label,
      min = 0,
      max = 100,
      step = 1,
      showValue = true,
      formatValue = (val) => val.toString(),
      fullWidth = false,
      size = 'md',
      disabled,
      className,
      value,
      defaultValue,
      onChange,
      ...props
    },
    ref
  ) => {
    const [internalValue, setInternalValue] = useState<number>(
      (value as number) ?? (defaultValue as number) ?? min
    );
    const sliderId = useRef(`slider-${Math.random().toString(36).substr(2, 9)}`).current;

    const currentValue = value !== undefined ? (value as number) : internalValue;
    const percentage = ((currentValue - min) / (max - min)) * 100;

    const sizeConfig = {
      sm: { height: '4px', thumbSize: '12px' },
      md: { height: '6px', thumbSize: '16px' },
      lg: { height: '8px', thumbSize: '20px' },
    };

    const config = sizeConfig[size];

    const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      const newValue = parseFloat(e.target.value);
      setInternalValue(newValue);
      onChange?.(e);
    };

    const containerStyles: React.CSSProperties = {
      width: fullWidth ? '100%' : 'auto',
    };

    const labelStyles: React.CSSProperties = {
      display: 'flex',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginBottom: tokens.spacing[2],
      fontSize: tokens.typography.fontSize.sm,
      fontWeight: tokens.typography.fontWeight.medium,
      color: disabled ? tokens.colors.neutral[400] : tokens.colors.neutral[700],
    };

    const valueStyles: React.CSSProperties = {
      fontSize: tokens.typography.fontSize.sm,
      fontWeight: tokens.typography.fontWeight.semibold,
      color: disabled ? tokens.colors.neutral[400] : tokens.colors.primary[600],
    };

    const sliderWrapperStyles: React.CSSProperties = {
      position: 'relative',
      width: '100%',
      height: config.height,
    };

    const trackStyles: React.CSSProperties = {
      position: 'absolute',
      width: '100%',
      height: '100%',
      backgroundColor: disabled ? tokens.colors.neutral[200] : tokens.colors.neutral[300],
      borderRadius: tokens.borderRadius.full,
      overflow: 'hidden',
    };

    const fillStyles: React.CSSProperties = {
      position: 'absolute',
      height: '100%',
      width: `${percentage}%`,
      backgroundColor: disabled ? tokens.colors.neutral[400] : tokens.colors.primary[500],
      transition: `width ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
    };

    const inputStyles: React.CSSProperties = {
      position: 'absolute',
      width: '100%',
      height: config.thumbSize,
      top: '50%',
      transform: 'translateY(-50%)',
      margin: 0,
      padding: 0,
      opacity: 0,
      cursor: disabled ? 'not-allowed' : 'pointer',
      zIndex: 2,
    };

    const thumbStyles: React.CSSProperties = {
      position: 'absolute',
      top: '50%',
      left: `${percentage}%`,
      transform: 'translate(-50%, -50%)',
      width: config.thumbSize,
      height: config.thumbSize,
      backgroundColor: disabled ? tokens.colors.neutral[400] : tokens.colors.primary[600],
      borderRadius: tokens.borderRadius.full,
      border: `2px solid ${tokens.colors.white}`,
      boxShadow: tokens.shadows.sm,
      transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
      pointerEvents: 'none',
      zIndex: 1,
    };

    return (
      <div style={containerStyles} className={className}>
        {(label || showValue) && (
          <div style={labelStyles}>
            {label && <label htmlFor={sliderId}>{label}</label>}
            {showValue && <span style={valueStyles}>{formatValue(currentValue)}</span>}
          </div>
        )}
        <div style={sliderWrapperStyles}>
          <div style={trackStyles}>
            <div style={fillStyles} />
          </div>
          <div style={thumbStyles} />
          <input
            ref={ref}
            id={sliderId}
            type="range"
            min={min}
            max={max}
            step={step}
            value={currentValue}
            disabled={disabled}
            onChange={handleChange}
            style={inputStyles}
            {...props}
          />
        </div>
      </div>
    );
  }
);

Slider.displayName = 'Slider';
