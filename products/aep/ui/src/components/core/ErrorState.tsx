/**
 * ErrorState — consistent error state component.
 *
 * @doc.type component
 * @doc.purpose Error state display component
 * @doc.layer frontend
 */
/* eslint-disable ghatana/prefer-design-system-primitives */
import React from 'react';

interface ErrorStateProps {
  title?: string;
  message?: string;
  onRetry?: () => void;
  isDegraded?: boolean;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = 'Something went wrong',
  message,
  onRetry,
  isDegraded = false,
}) => {
  return (
    <div className="flex flex-col items-center justify-center h-40 gap-2 text-center px-4">
      <div
        className={[
          'inline-flex items-center justify-center w-8 h-8 rounded-full mb-1',
          isDegraded
            ? 'bg-amber-50 dark:bg-amber-950 text-amber-500'
            : 'bg-red-50 dark:bg-red-950 text-red-500',
        ].join(' ')}
      >
        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
          />
        </svg>
      </div>
      <p className="text-sm font-medium text-gray-700 dark:text-gray-300">{title}</p>
      {message && (
        <p className="text-xs text-gray-400 dark:text-gray-500 max-w-sm">{message}</p>
      )}
      {isDegraded && (
        <p className="text-xs text-amber-600 dark:text-amber-400 max-w-sm">
          The service is temporarily unavailable. Data shown may be incomplete.
        </p>
      )}
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-1 text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
        >
          Retry
        </button>
      )}
    </div>
  );
};
