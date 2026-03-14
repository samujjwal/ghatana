import React from 'react';
import { tokens } from '@ghatana/tokens';

export interface ProgressProps {
  /** Progress value (0-100) */
  value: number;
  /** Maximum value */
  max?: number;
  /** Progress variant */
  variant?: 'linear' | 'circular';
  /** Size variant */
  size?: 'sm' | 'md' | 'lg';
  /** Color variant */
  color?: 'primary' | 'success' | 'warning' | 'error';
  /** Show label */
  showLabel?: boolean;
  /** Label formatter */
  formatLabel?: (value: number, max: number) => string;
  /** Indeterminate state (loading) */
  indeterminate?: boolean;
  /** Additional class name */
  className?: string;
}

export const Progress: React.FC<ProgressProps> = ({
  value,
  max = 100,
  variant = 'linear',
  size = 'md',
  color = 'primary',
  showLabel = false,
  formatLabel = (val, maximum) => `${Math.round((val / maximum) * 100)}%`,
  indeterminate = false,
  className,
}) => {
  const percentage = Math.min(Math.max((value / max) * 100, 0), 100);

  const colorMap = {
    primary: tokens.colors.primary[600],
    success: tokens.colors.success[600],
    warning: tokens.colors.warning[600],
    error: tokens.colors.error[600],
  };

  const progressColor = colorMap[color];

  if (variant === 'circular') {
    const sizeMap = {
      sm: 32,
      md: 48,
      lg: 64,
    };

    const circleSize = sizeMap[size];
    const strokeWidth = size === 'sm' ? 3 : size === 'md' ? 4 : 5;
    const radius = (circleSize - strokeWidth) / 2;
    const circumference = 2 * Math.PI * radius;
    const offset = circumference - (percentage / 100) * circumference;

    const containerStyles: React.CSSProperties = {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      position: 'relative',
      width: `${circleSize}px`,
      height: `${circleSize}px`,
    };

    const labelStyles: React.CSSProperties = {
      position: 'absolute',
      fontSize: size === 'sm' ? tokens.typography.fontSize.xs : tokens.typography.fontSize.sm,
      fontWeight: tokens.typography.fontWeight.semibold,
      color: tokens.colors.neutral[700],
    };

    return (
      <div style={containerStyles} className={className}>
        <svg width={circleSize} height={circleSize}>
          {/* Background circle */}
          <circle
            cx={circleSize / 2}
            cy={circleSize / 2}
            r={radius}
            fill="none"
            stroke={tokens.colors.neutral[200]}
            strokeWidth={strokeWidth}
          />
          {/* Progress circle */}
          <circle
            cx={circleSize / 2}
            cy={circleSize / 2}
            r={radius}
            fill="none"
            stroke={progressColor}
            strokeWidth={strokeWidth}
            strokeDasharray={circumference}
            strokeDashoffset={indeterminate ? 0 : offset}
            strokeLinecap="round"
            transform={`rotate(-90 ${circleSize / 2} ${circleSize / 2})`}
            style={{
              transition: indeterminate
                ? 'none'
                : `stroke-dashoffset ${tokens.transitions.duration.normal} ${tokens.transitions.easing.easeInOut}`,
              animation: indeterminate ? 'spin 1.5s linear infinite' : 'none',
            }}
          />
        </svg>
        {showLabel && !indeterminate && (
          <div style={labelStyles}>{formatLabel(value, max)}</div>
        )}
      </div>
    );
  }

  // Linear variant
  const heightMap = {
    sm: '4px',
    md: '8px',
    lg: '12px',
  };

  const containerStyles: React.CSSProperties = {
    width: '100%',
  };

  const trackStyles: React.CSSProperties = {
    width: '100%',
    height: heightMap[size],
    backgroundColor: tokens.colors.neutral[200],
    borderRadius: tokens.borderRadius.full,
    overflow: 'hidden',
    position: 'relative',
  };

  const barStyles: React.CSSProperties = {
    height: '100%',
    width: indeterminate ? '30%' : `${percentage}%`,
    backgroundColor: progressColor,
    borderRadius: tokens.borderRadius.full,
    transition: indeterminate
      ? 'none'
      : `width ${tokens.transitions.duration.normal} ${tokens.transitions.easing.easeInOut}`,
    animation: indeterminate ? 'progress-indeterminate 1.5s ease-in-out infinite' : 'none',
  };

  const labelStyles: React.CSSProperties = {
    marginTop: tokens.spacing[1],
    fontSize: tokens.typography.fontSize.sm,
    fontWeight: tokens.typography.fontWeight.medium,
    color: tokens.colors.neutral[700],
    textAlign: 'right',
  };

  return (
    <div style={containerStyles} className={className}>
      <div style={trackStyles}>
        <div style={barStyles} role="progressbar" aria-valuenow={value} aria-valuemin={0} aria-valuemax={max} />
      </div>
      {showLabel && !indeterminate && (
        <div style={labelStyles}>{formatLabel(value, max)}</div>
      )}
      <style>{`
        @keyframes progress-indeterminate {
          0% {
            transform: translateX(-100%);
          }
          100% {
            transform: translateX(400%);
          }
        }
        @keyframes spin {
          0% {
            transform: rotate(-90deg);
          }
          100% {
            transform: rotate(270deg);
          }
        }
      `}</style>
    </div>
  );
};

Progress.displayName = 'Progress';
