/**
 * Loading State Component
 * 
 * Displays a loading spinner with optional message.
 * Uses @ghatana/ui Spinner component for consistency.
 * 
 * @doc.type component
 * @doc.purpose Display loading state with spinner
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React from 'react';
import { Spinner } from '@ghatana/ui';
import { cn, textStyles } from '../../lib/theme';

interface LoadingStateProps {
  /** Optional loading message */
  message?: string;
  /** Additional CSS classes */
  className?: string;
  /** Spinner size */
  size?: 'xs' | 'sm' | 'md' | 'lg';
}

/**
 * Loading state component with spinner and optional message.
 * 
 * @example
 * ```tsx
 * <LoadingState message="Loading collections..." />
 * ```
 */
export const LoadingState: React.FC<LoadingStateProps> = ({
  message = 'Loading...',
  className = '',
  size = 'md',
}) => (
  <div className={cn('flex items-center justify-center p-8', className)}>
    <Spinner size={size} />
    {message && <span className={cn('ml-3', textStyles.muted)}>{message}</span>}
  </div>
);

export default LoadingState;
