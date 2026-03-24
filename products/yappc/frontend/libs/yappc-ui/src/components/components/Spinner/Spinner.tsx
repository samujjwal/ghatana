import type { CSSProperties } from 'react';

/**
 *
 */
export interface SpinnerProps {
  /**
   * Spinner size
   */
  size?: 'small' | 'medium' | 'large' | number;
  
  /**
   * Spinner color
   */
  color?: string;
  
  /**
   * Additional class name
   */
  className?: string;
  
  /**
   * Accessibility label
   */
  label?: string;
}

/**
 * Loading spinner component
 * 
 * @example
 * ```tsx
 * <Spinner size="medium" color="#2196f3" label="Loading..." />
 * ```
 */
export function Spinner({
  size = 'medium',
  color = '#2196f3',
  className = '',
  label = 'Loading...',
}: SpinnerProps) {
  const sizeMap = {
    small: 16,
    medium: 24,
    large: 40,
  };

  const spinnerSize = typeof size === 'number' ? size : sizeMap[size];

  const containerStyle: CSSProperties = {
    display: 'inline-block',
    width: spinnerSize,
    height: spinnerSize,
  };

  const spinnerStyle: CSSProperties = {
    width: '100%',
    height: '100%',
    border: `${spinnerSize / 8}px solid ${color}20`,
    borderTopColor: color,
    borderRadius: '50%',
    animation: 'spin 0.8s linear infinite',
  };

  return (
    <div
      style={containerStyle}
      className={className}
      role="status"
      aria-label={label}
    >
      <div style={spinnerStyle} />
      <style>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}

/**
 *
 */
export interface SkeletonProps {
  /**
   * Skeleton width
   */
  width?: string | number;
  
  /**
   * Skeleton height
   */
  height?: string | number;
  
  /**
   * Skeleton variant
   */
  variant?: 'text' | 'circular' | 'rectangular';
  
  /**
   * Additional class name
   */
  className?: string;
}

/**
 * Skeleton loading placeholder
 * 
 * @example
 * ```tsx
 * <Skeleton variant="text" width="100%" height="20px" />
 * <Skeleton variant="circular" width="40px" height="40px" />
 * <Skeleton variant="rectangular" width="100%" height="200px" />
 * ```
 */
export function Skeleton({
  width = '100%',
  height = '1rem',
  variant = 'text',
  className = '',
}: SkeletonProps) {
  const baseStyle: CSSProperties = {
    backgroundColor: '#e0e0e0',
    backgroundImage: 'linear-gradient(90deg, #e0e0e0 0%, #f5f5f5 50%, #e0e0e0 100%)',
    backgroundSize: '200% 100%',
    animation: 'skeleton-loading 1.5s ease-in-out infinite',
    width: typeof width === 'number' ? `${width}px` : width,
    height: typeof height === 'number' ? `${height}px` : height,
  };

  const variantStyles: Record<string, CSSProperties> = {
    text: {
      borderRadius: '0.25rem',
    },
    circular: {
      borderRadius: '50%',
    },
    rectangular: {
      borderRadius: '0.375rem',
    },
  };

  const style: CSSProperties = {
    ...baseStyle,
    ...variantStyles[variant],
  };

  return (
    <>
      <div
        style={style}
        className={className}
        aria-busy="true"
        aria-live="polite"
      />
      <style>{`
        @keyframes skeleton-loading {
          0% { background-position: 200% 0; }
          100% { background-position: -200% 0; }
        }
      `}</style>
    </>
  );
}
