import { forwardRef } from 'react';

import type { HTMLAttributes } from 'react';

/**
 *
 */
export type ProgressVariant = 'linear' | 'circular';
/**
 *
 */
export type ProgressColor = 'primary' | 'success' | 'error' | 'warning' | 'info';

/**
 *
 */
export interface ProgressProps extends HTMLAttributes<HTMLDivElement> {
  /**
   * Progress value (0-100)
   */
  value?: number;
  
  /**
   * Progress variant
   */
  variant?: ProgressVariant;
  
  /**
   * Progress color
   */
  color?: ProgressColor;
  
  /**
   * Show label
   */
  showLabel?: boolean;
  
  /**
   * Progress size
   */
  size?: 'small' | 'medium' | 'large';
  
  /**
   * Indeterminate state (loading)
   */
  indeterminate?: boolean;
}

/**
 * Progress component for showing completion status
 * 
 * @example
 * ```tsx
 * <Progress value={75} color="primary" showLabel />
 * <Progress variant="circular" value={50} />
 * <Progress indeterminate />
 * ```
 */
export const Progress = forwardRef<HTMLDivElement, ProgressProps>(
  (
    {
      value = 0,
      variant = 'linear',
      color = 'primary',
      showLabel = false,
      size = 'medium',
      indeterminate = false,
      className = '',
      ...props
    },
    ref
  ) => {
    const clampedValue = Math.min(100, Math.max(0, value));

    const colorMap = {
      primary: '#2196f3',
      success: '#4caf50',
      error: '#f44336',
      warning: '#ff9800',
      info: '#03a9f4',
    };

    const progressColor = colorMap[color];

    if (variant === 'circular') {
      const sizeMap = {
        small: 32,
        medium: 48,
        large: 64,
      };

      const circleSize = sizeMap[size];
      const strokeWidth = circleSize / 8;
      const radius = (circleSize - strokeWidth) / 2;
      const circumference = 2 * Math.PI * radius;
      const offset = circumference - (clampedValue / 100) * circumference;

      const containerStyle: React.CSSProperties = {
        width: circleSize,
        height: circleSize,
        position: 'relative',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
      };

      const svgStyle: React.CSSProperties = {
        transform: 'rotate(-90deg)',
      };

      const labelStyle: React.CSSProperties = {
        position: 'absolute',
        fontSize: size === 'small' ? '0.625rem' : size === 'large' ? '0.875rem' : '0.75rem',
        fontWeight: 500,
        color: '#424242',
      };

      return (
        <div ref={ref} style={containerStyle} className={className} {...props}>
          <svg width={circleSize} height={circleSize} style={svgStyle}>
            <circle
              cx={circleSize / 2}
              cy={circleSize / 2}
              r={radius}
              fill="none"
              stroke="#e0e0e0"
              strokeWidth={strokeWidth}
            />
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
              style={{
                transition: 'stroke-dashoffset 0.3s ease',
                animation: indeterminate ? 'spin 1.4s linear infinite' : 'none',
              }}
            />
          </svg>
          {showLabel && !indeterminate && (
            <span style={labelStyle}>{Math.round(clampedValue)}%</span>
          )}
          <style>{`
            @keyframes spin {
              0% { stroke-dashoffset: ${circumference}; transform: rotate(0deg); }
              50% { stroke-dashoffset: ${circumference / 4}; transform: rotate(180deg); }
              100% { stroke-dashoffset: ${circumference}; transform: rotate(360deg); }
            }
          `}</style>
        </div>
      );
    }

    // Linear variant
    const heightMap = {
      small: '4px',
      medium: '8px',
      large: '12px',
    };

    const containerStyle: React.CSSProperties = {
      width: '100%',
      backgroundColor: '#e0e0e0',
      borderRadius: '4px',
      overflow: 'hidden',
      height: heightMap[size],
      position: 'relative',
    };

    const barStyle: React.CSSProperties = {
      height: '100%',
      backgroundColor: progressColor,
      width: indeterminate ? '30%' : `${clampedValue}%`,
      transition: 'width 0.3s ease',
      animation: indeterminate ? 'progress-indeterminate 1.5s ease-in-out infinite' : 'none',
    };

    const labelContainerStyle: React.CSSProperties = {
      display: 'flex',
      alignItems: 'center',
      gap: '0.5rem',
    };

    const labelStyle: React.CSSProperties = {
      fontSize: '0.875rem',
      fontWeight: 500,
      color: '#424242',
      minWidth: '3rem',
      textAlign: 'right',
    };

    return (
      <div ref={ref} style={labelContainerStyle} className={className} {...props}>
        <div style={containerStyle}>
          <div style={barStyle} />
        </div>
        {showLabel && !indeterminate && (
          <span style={labelStyle}>{Math.round(clampedValue)}%</span>
        )}
        <style>{`
          @keyframes progress-indeterminate {
            0% { transform: translateX(-100%); }
            100% { transform: translateX(400%); }
          }
        `}</style>
      </div>
    );
  }
);

Progress.displayName = 'Progress';
