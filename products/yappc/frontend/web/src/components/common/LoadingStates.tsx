/**
 * Loading States Component
 *
 * Provides reusable loading state components with different styles and animations.
 * Supports skeleton loaders, spinners, and progress indicators.
 *
 * @doc.type component
 * @doc.purpose Reusable loading state components
 * @doc.layer product
 * @doc.pattern Component
 */

import React from 'react';
import { Loader2 } from 'lucide-react';
import { Typography, Box } from '@ghatana/design-system';

interface LoadingStateProps {
  message?: string;
  size?: 'sm' | 'md' | 'lg';
  fullScreen?: boolean;
  className?: string;
}

/**
 * Basic loading spinner with optional message
 */
export function LoadingSpinner({ message, size = 'md', fullScreen = false, className = '' }: LoadingStateProps) {
  const sizeClasses = {
    sm: 'w-4 h-4',
    md: 'w-8 h-8',
    lg: 'w-12 h-12',
  };

  const containerClass = fullScreen 
    ? 'fixed inset-0 flex items-center justify-center bg-white/80 dark:bg-gray-900/80 backdrop-blur-sm z-50'
    : 'flex items-center justify-center';

  return (
    <div className={`${containerClass} ${className}`}>
      <div className="flex flex-col items-center gap-3">
        <Loader2 className={`${sizeClasses[size]} animate-spin text-blue-600`} />
        {message && (
          <Typography className="text-sm text-gray-600 dark:text-gray-400">
            {message}
          </Typography>
        )}
      </div>
    </div>
  );
}

interface SkeletonLoaderProps {
  count?: number;
  className?: string;
}

/**
 * Skeleton loader for content placeholders
 */
export function SkeletonLoader({ count = 3, className = '' }: SkeletonLoaderProps) {
  return (
    <div className={`space-y-3 ${className}`}>
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse"
          style={{ animationDelay: `${i * 0.1}s` }}
        />
      ))}
    </div>
  );
}

interface CardSkeletonProps {
  className?: string;
}

/**
 * Card skeleton for card-based layouts
 */
export function CardSkeleton({ className = '' }: CardSkeletonProps) {
  return (
    <div className={`p-4 border border-gray-200 dark:border-gray-700 rounded-lg ${className}`}>
      <div className="h-5 bg-gray-200 dark:bg-gray-700 rounded animate-pulse mb-3 w-3/4" />
      <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded animate-pulse mb-2 w-1/2" />
      <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded animate-pulse w-1/3" />
    </div>
  );
}

interface TableSkeletonProps {
  rows?: number;
  columns?: number;
  className?: string;
}

/**
 * Table skeleton for table-based layouts
 */
export function TableSkeleton({ rows = 5, columns = 4, className = '' }: TableSkeletonProps) {
  return (
    <div className={`w-full ${className}`}>
      <div className="flex gap-4 mb-4">
        {Array.from({ length: columns }).map((_, i) => (
          <div
            key={`header-${i}`}
            className="h-4 bg-gray-200 dark:bg-gray-700 rounded animate-pulse flex-1"
          />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, rowIndex) => (
        <div key={`row-${rowIndex}`} className="flex gap-4 mb-3">
          {Array.from({ length: columns }).map((_, colIndex) => (
            <div
              key={`cell-${rowIndex}-${colIndex}`}
              className="h-3 bg-gray-200 dark:bg-gray-700 rounded animate-pulse flex-1"
              style={{ animationDelay: `${(rowIndex * columns + colIndex) * 0.05}s` }}
            />
          ))}
        </div>
      ))}
    </div>
  );
}

interface ProgressLoadingProps {
  progress?: number;
  message?: string;
  className?: string;
}

/**
 * Progress loading indicator with percentage
 */
export function ProgressLoading({ progress = 0, message, className = '' }: ProgressLoadingProps) {
  return (
    <div className={`w-full ${className}`}>
      <div className="flex items-center justify-between mb-2">
        {message && (
          <Typography className="text-sm text-gray-600 dark:text-gray-400">
            {message}
          </Typography>
        )}
        <Typography className="text-sm font-medium text-gray-600 dark:text-gray-400">
          {Math.round(progress)}%
        </Typography>
      </div>
      <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
        <div
          className="bg-blue-600 h-2 rounded-full transition-all duration-300 ease-out"
          style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
        />
      </div>
    </div>
  );
}

interface InlineLoadingProps {
  message?: string;
  className?: string;
}

/**
 * Inline loading indicator for use within text
 */
export function InlineLoading({ message = 'Loading...', className = '' }: InlineLoadingProps) {
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <Loader2 className="w-4 h-4 animate-spin text-blue-600" />
      <span className="text-sm text-gray-600 dark:text-gray-400">{message}</span>
    </span>
  );
}

interface FullPageLoadingProps {
  message?: string;
  logo?: React.ReactNode;
  className?: string;
}

/**
 * Full page loading screen with logo
 */
export function FullPageLoading({ message = 'Loading...', logo, className = '' }: FullPageLoadingProps) {
  return (
    <div className={`fixed inset-0 flex flex-col items-center justify-center bg-white dark:bg-gray-900 ${className}`}>
      <div className="flex flex-col items-center gap-6">
        {logo && <div className="mb-4">{logo}</div>}
        <Loader2 className="w-16 h-16 animate-spin text-blue-600" />
        <Typography className="text-lg text-gray-600 dark:text-gray-400">
          {message}
        </Typography>
      </div>
    </div>
  );
}
