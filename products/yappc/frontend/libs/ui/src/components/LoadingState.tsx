/**
 * Loading State Component
 * 
 * Reusable loading state with skeleton screens and spinners.
 * 
 * @module ui/components
 */

import React from 'react';

export interface LoadingStateProps {
  /** Loading variant */
  variant?: 'spinner' | 'skeleton' | 'dots' | 'pulse';
  
  /** Size */
  size?: 'sm' | 'md' | 'lg';
  
  /** Loading message */
  message?: string;
  
  /** Full screen overlay */
  fullScreen?: boolean;
  
  /** Additional CSS classes */
  className?: string;
}

/**
 * Loading State Component
 * 
 * @example
 * ```tsx
 * <LoadingState variant="spinner" message="Loading data..." />
 * <LoadingState variant="skeleton" />
 * <LoadingState variant="dots" fullScreen />
 * ```
 */
export const LoadingState: React.FC<LoadingStateProps> = ({
  variant = 'spinner',
  size = 'md',
  message,
  fullScreen = false,
  className = '',
}) => {
  const sizeClasses = {
    sm: 'w-4 h-4',
    md: 'w-8 h-8',
    lg: 'w-12 h-12',
  };

  const renderSpinner = () => (
    <div className={`animate-spin rounded-full border-2 border-violet-600 border-t-transparent ${sizeClasses[size]}`} />
  );

  const renderSkeleton = () => (
    <div className="space-y-3 w-full max-w-md">
      <div className="h-4 bg-zinc-800 rounded animate-pulse" />
      <div className="h-4 bg-zinc-800 rounded animate-pulse w-5/6" />
      <div className="h-4 bg-zinc-800 rounded animate-pulse w-4/6" />
    </div>
  );

  const renderDots = () => (
    <div className="flex gap-2">
      <div className={`${sizeClasses[size]} bg-violet-600 rounded-full animate-bounce`} style={{ animationDelay: '0ms' }} />
      <div className={`${sizeClasses[size]} bg-violet-600 rounded-full animate-bounce`} style={{ animationDelay: '150ms' }} />
      <div className={`${sizeClasses[size]} bg-violet-600 rounded-full animate-bounce`} style={{ animationDelay: '300ms' }} />
    </div>
  );

  const renderPulse = () => (
    <div className={`${sizeClasses[size]} bg-violet-600 rounded-full animate-pulse`} />
  );

  const renderLoader = () => {
    switch (variant) {
      case 'spinner':
        return renderSpinner();
      case 'skeleton':
        return renderSkeleton();
      case 'dots':
        return renderDots();
      case 'pulse':
        return renderPulse();
      default:
        return renderSpinner();
    }
  };

  const content = (
    <div className={`flex flex-col items-center justify-center gap-4 ${className}`}>
      {renderLoader()}
      {message && (
        <p className="text-sm text-zinc-400">{message}</p>
      )}
    </div>
  );

  if (fullScreen) {
    return (
      <div className="fixed inset-0 bg-zinc-950/80 backdrop-blur-sm flex items-center justify-center z-50">
        {content}
      </div>
    );
  }

  return content;
};

export default LoadingState;
