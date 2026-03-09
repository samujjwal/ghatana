/**
 * Loading State Component
 *
 * Wrapper component that handles loading, error, and empty states
 */

import React, { ReactNode } from 'react';
import { LoadingSpinner } from './LoadingSpinner';

export interface LoadingStateProps {
  /** Whether data is currently loading */
  isLoading: boolean;
  /** Error message if any */
  error?: Error | string | null;
  /** Whether the data is empty */
  isEmpty?: boolean;
  /** Custom loading component */
  loadingComponent?: ReactNode;
  /** Custom error component */
  errorComponent?: ReactNode;
  /** Custom empty state component */
  emptyComponent?: ReactNode;
  /** Loading message */
  loadingMessage?: string;
  /** Empty state message */
  emptyMessage?: string;
  /** Children to render when loaded and not empty */
  children: ReactNode;
}

export const LoadingState: React.FC<LoadingStateProps> = ({
  isLoading,
  error,
  isEmpty = false,
  loadingComponent,
  errorComponent,
  emptyComponent,
  loadingMessage = 'Loading...',
  emptyMessage = 'No data available',
  children,
}) => {
  // Show loading state
  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        {loadingComponent || <LoadingSpinner size="medium" label={loadingMessage} />}
      </div>
    );
  }

  // Show error state
  if (error) {
    const errorMessage = error instanceof Error ? error.message : String(error);

    return (
      <div className="flex items-center justify-center p-8">
        {errorComponent || (
          <div className="text-center">
            <div className="text-red-600 text-lg font-semibold mb-2">Error</div>
            <div className="text-gray-700">{errorMessage}</div>
          </div>
        )}
      </div>
    );
  }

  // Show empty state
  if (isEmpty) {
    return (
      <div className="flex items-center justify-center p-8">
        {emptyComponent || (
          <div className="text-center text-gray-500">
            <div className="text-lg mb-2">📭</div>
            <div>{emptyMessage}</div>
          </div>
        )}
      </div>
    );
  }

  // Show content
  return <>{children}</>;
};
