/**
 * P1-030: Mutation error display component.
 *
 * <p>Displays mutation errors with:
 * <ul>
 *   <li>Actionable error messages</li>
 *   <li>Correlation ID for support</li>
 *   <li>Retry button for retryable errors</li>
 *   <li>Appropriate styling for error severity</li>
 * </ul>
 *
 * @doc.type component
 * @doc.purpose Error display with correlation ID and retry (P1-030)
 * @doc.layer frontend
 */

import React from 'react';
import type { ApiError } from '@/hooks/useMutationError';

interface MutationErrorDisplayProps {
  error: ApiError | null;
  onRetry?: () => void;
  className?: string;
}

export const MutationErrorDisplay: React.FC<MutationErrorDisplayProps> = ({
  error,
  onRetry,
  className = '',
}) => {
  if (!error) {
    return null;
  }

  const getErrorStyles = (): string => {
    switch (error.code) {
      case 'AUTH':
        return 'bg-yellow-50 border-yellow-400 text-yellow-800';
      case 'FORBIDDEN':
        return 'bg-red-50 border-red-400 text-red-800';
      case 'VALIDATION':
        return 'bg-orange-50 border-orange-400 text-orange-800';
      case 'NETWORK_ERROR':
        return 'bg-blue-50 border-blue-400 text-blue-800';
      default:
        return 'bg-red-50 border-red-400 text-red-800';
    }
  };

  const getIcon = (): React.ReactNode => {
    switch (error.code) {
      case 'AUTH':
        return (
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
          </svg>
        );
      case 'NETWORK_ERROR':
        return (
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
            <path d="M2 11a1 1 0 011-1h2a1 1 0 011 1v5a1 1 0 01-1 1H3a1 1 0 01-1-1v-5zm6-4a1 1 0 011-1h2a1 1 0 011 1v9a1 1 0 01-1 1H9a1 1 0 01-1-1V7zm6-3a1 1 0 011-1h2a1 1 0 011 1v12a1 1 0 01-1 1h-2a1 1 0 01-1-1V4z" />
          </svg>
        );
      default:
        return (
          <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
            <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
          </svg>
        );
    }
  };

  return (
    <div
      className={`rounded-md border p-4 ${getErrorStyles()} ${className}`}
      role="alert"
      data-testid="mutation-error"
    >
      <div className="flex">
        <div className="flex-shrink-0">{getIcon()}</div>
        <div className="ml-3 flex-1">
          <h3 className="text-sm font-medium" data-testid="error-code">
            {error.code}
          </h3>
          <div className="mt-2 text-sm">
            <p data-testid="error-message">{error.message}</p>
          </div>

          {/* Correlation ID for support */}
          <div className="mt-3 text-xs bg-white/50 rounded px-2 py-1 inline-block">
            <span className="font-medium">Support ID:</span>{' '}
            <code className="font-mono" data-testid="correlation-id">
              {error.correlationId}
            </code>
          </div>

          {/* Retry button for retryable errors */}
          {((error.retryable && onRetry) || error.code === 'NETWORK_ERROR') && (
            <div className="mt-4">
              <button
                onClick={() => onRetry?.()}
                className="inline-flex items-center px-3 py-2 border border-transparent text-sm leading-4 font-medium rounded-md text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500"
                data-testid="retry-button"
                disabled={!onRetry}
              >
                <svg className="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                </svg>
                Try Again
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default MutationErrorDisplay;
