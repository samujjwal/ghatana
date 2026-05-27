import React from 'react';

/**
 * W-002: Safe alert/error component.
 * Provides safe message display with correlation ID, no raw exception details.
 */

interface SafeErrorProps {
  title?: string;
  message: string;
  correlationId?: string;
  severity?: 'error' | 'warning' | 'info';
  onDismiss?: () => void;
}

export function SafeError({ title, message, correlationId, severity = 'error', onDismiss }: SafeErrorProps) {
  const bgColor = severity === 'error' ? 'bg-red-50' : severity === 'warning' ? 'bg-yellow-50' : 'bg-blue-50';
  const borderColor = severity === 'error' ? 'border-red-200' : severity === 'warning' ? 'border-yellow-200' : 'border-blue-200';
  const iconColor = severity === 'error' ? 'text-red-500' : severity === 'warning' ? 'text-yellow-500' : 'text-blue-500';

  return (
    <div className={`${bgColor} border ${borderColor} rounded-lg p-4`}>
      <div className="flex">
        <div className="flex-shrink-0">
          {severity === 'error' && (
            <svg className={`h-5 w-5 ${iconColor}`} viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
            </svg>
          )}
          {severity === 'warning' && (
            <svg className={`h-5 w-5 ${iconColor}`} viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
            </svg>
          )}
          {severity === 'info' && (
            <svg className={`h-5 w-5 ${iconColor}`} viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
            </svg>
          )}
        </div>
        <div className="ml-3 flex-1">
          {title && <h3 className={`text-sm font-medium ${iconColor}`}>{title}</h3>}
          <div className={`text-sm ${severity === 'error' ? 'text-red-700' : severity === 'warning' ? 'text-yellow-700' : 'text-blue-700'}`}>
            <p>{message}</p>
            {correlationId && (
              <p className="mt-1 text-xs opacity-75">Correlation ID: {correlationId}</p>
            )}
          </div>
        </div>
        {onDismiss && (
          <div className="ml-auto pl-3">
            <button
              onClick={onDismiss}
              className={`inline-flex ${iconColor} hover:opacity-75 focus:outline-none`}
            >
              <span className="sr-only">Dismiss</span>
              <svg className="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
              </svg>
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
