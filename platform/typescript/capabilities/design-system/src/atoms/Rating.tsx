import React, { useState } from 'react';
import { tokens } from '@ghatana/tokens';

type RatingOnChange =
  | ((value: number) => void)
  | ((event: React.SyntheticEvent<Element> | null, value: number | null) => void);

function isMuiRatingOnChange(fn: RatingOnChange): fn is (
  event: React.SyntheticEvent<Element> | null,
  value: number | null
) => void {
  return fn.length >= 2;
}

export interface RatingProps {
  /** Current rating value */
  value?: number;
  /** Default rating value (uncontrolled) */
  defaultValue?: number;
  /** Maximum rating value */
  max?: number;
  /** Rating change handler */
  onChange?: RatingOnChange;
  /** Size variant */
  size?: 'sm' | 'md' | 'lg' | 'small' | 'medium' | 'large';
  /** MUI-compat: precision step (e.g. 0.5) */
  precision?: number;
  /** Read-only mode */
  readOnly?: boolean;
  /** Disabled state */
  disabled?: boolean;
  /** Allow half stars */
  allowHalf?: boolean;
  /** Custom icon */
  icon?: React.ReactNode;
  /** Empty icon */
  emptyIcon?: React.ReactNode;
  /** Color */
  color?: string;
  /** Show value label */
  showValue?: boolean;
  /** Additional class name */
  className?: string;
}

export const Rating: React.FC<RatingProps> = ({
  value: controlledValue,
  defaultValue,
  max = 5,
  onChange,
  size = 'md',
  readOnly = false,
  disabled = false,
  allowHalf = false,
  precision,
  icon,
  emptyIcon,
  color = tokens.colors.warning[500],
  showValue = false,
  className,
}) => {
  const [hoverValue, setHoverValue] = useState<number | null>(null);
  const [internalValue, setInternalValue] = useState(defaultValue ?? 0);

  const resolvedSize: 'sm' | 'md' | 'lg' =
    size === 'small' ? 'sm' : size === 'medium' ? 'md' : size === 'large' ? 'lg' : size;

  const resolvedAllowHalf = precision === 0.5 ? true : allowHalf;

  const value = controlledValue !== undefined ? controlledValue : internalValue;
  const displayValue = hoverValue !== null ? hoverValue : value;

  const sizeConfig = {
    sm: { size: '16px', gap: tokens.spacing[1] },
    md: { size: '24px', gap: tokens.spacing[2] },
    lg: { size: '32px', gap: tokens.spacing[3] },
  };

  const config = sizeConfig[resolvedSize];

  const handleClick = (starValue: number) => {
    if (readOnly || disabled) return;
    setInternalValue(starValue);
    if (!onChange) return;
    if (isMuiRatingOnChange(onChange)) {
      onChange(null, starValue);
      return;
    }
    onChange(starValue);
  };

  const handleMouseEnter = (starValue: number) => {
    if (readOnly || disabled) return;
    setHoverValue(starValue);
  };

  const handleMouseLeave = () => {
    if (readOnly || disabled) return;
    setHoverValue(null);
  };

  const containerStyles: React.CSSProperties = {
    display: 'inline-flex',
    alignItems: 'center',
    gap: config.gap,
    cursor: readOnly || disabled ? 'default' : 'pointer',
    opacity: disabled ? 0.5 : 1,
  };

  const starsContainerStyles: React.CSSProperties = {
    display: 'flex',
    gap: tokens.spacing[1],
  };

  const starStyles: React.CSSProperties = {
    width: config.size,
    height: config.size,
    transition: `all ${tokens.transitions.duration.fast} ${tokens.transitions.easing.easeInOut}`,
  };

  const labelStyles: React.CSSProperties = {
    fontSize: resolvedSize === 'sm' ? tokens.typography.fontSize.sm : tokens.typography.fontSize.base,
    fontWeight: tokens.typography.fontWeight.medium,
    color: tokens.colors.neutral[700],
    marginLeft: tokens.spacing[2],
  };

  const defaultIcon = (filled: boolean, fillPercentage: number = 100) => (
    <svg
      width={config.size}
      height={config.size}
      viewBox="0 0 24 24"
      fill={filled ? color : 'none'}
      stroke={filled ? color : tokens.colors.neutral[300]}
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      style={starStyles}
    >
      {fillPercentage < 100 && fillPercentage > 0 ? (
        <>
          <defs>
            <linearGradient id={`gradient-${fillPercentage}`}>
              <stop offset={`${fillPercentage}%`} stopColor={color} />
              <stop offset={`${fillPercentage}%`} stopColor="transparent" />
            </linearGradient>
          </defs>
          <polygon
            points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"
            fill={`url(#gradient-${fillPercentage})`}
            stroke={tokens.colors.neutral[300]}
          />
        </>
      ) : (
        <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
      )}
    </svg>
  );

  const renderStar = (index: number) => {
    const starValue = index + 1;
    const isFilled = displayValue >= starValue;
    const isHalfFilled =
      resolvedAllowHalf && displayValue >= starValue - 0.5 && displayValue < starValue;
    const fillPercentage = isHalfFilled ? 50 : isFilled ? 100 : 0;

    return (
      <span
        key={index}
        onClick={() => handleClick(starValue)}
        onMouseEnter={() => handleMouseEnter(starValue)}
        onMouseMove={(e) => {
          if (!resolvedAllowHalf || readOnly || disabled) return;
          const rect = e.currentTarget.getBoundingClientRect();
          const x = e.clientX - rect.left;
          const halfValue = x < rect.width / 2 ? starValue - 0.5 : starValue;
          setHoverValue(halfValue);
        }}
        style={{
          display: 'inline-block',
          lineHeight: 0,
        }}
        role="radio"
        aria-checked={isFilled}
        aria-label={`${starValue} star${starValue > 1 ? 's' : ''}`}
      >
        {icon || emptyIcon
          ? isFilled
            ? icon || emptyIcon
            : emptyIcon || icon
          : defaultIcon(isFilled || isHalfFilled, fillPercentage)}
      </span>
    );
  };

  return (
    <div
      style={containerStyles}
      className={className}
      onMouseLeave={handleMouseLeave}
      role="radiogroup"
      aria-label={`Rating: ${value} out of ${max}`}
    >
      <div style={starsContainerStyles}>
        {Array.from({ length: max }, (_, i) => renderStar(i))}
      </div>
      {showValue && (
        <span style={labelStyles}>
          {displayValue.toFixed(resolvedAllowHalf ? 1 : 0)} / {max}
        </span>
      )}
    </div>
  );
};

Rating.displayName = 'Rating';
