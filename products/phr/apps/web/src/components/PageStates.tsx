import React from 'react';
import { Button } from '@ghatana/design-system';
import { t } from '../i18n/phrI18n';

/**
 * W-001: Shared page state components reused by all pages.
 * Provides consistent loading, error, empty, and forbidden states.
 */

interface LoadingStateProps {
  message?: string;
}

export function LoadingState({ message }: LoadingStateProps) {
  return (
    <div className="flex items-center justify-center min-h-[400px]">
      <div className="text-center">
        <div className="inline-block animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4" />
        <p className="text-gray-600">{message ?? t('state.loading')}</p>
      </div>
    </div>
  );
}

interface ErrorStateProps {
  title?: string;
  message: string;
  correlationId?: string;
  onRetry?: () => void;
}

export function ErrorState({ title, message, correlationId, onRetry }: ErrorStateProps) {
  return (
    <div className="flex items-center justify-center min-h-[400px]">
      <div className="text-center max-w-md">
        <div className="text-red-500 mb-4">
          <svg className="w-16 h-16 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        </div>
        <h3 className="text-lg font-semibold text-gray-900 mb-2">{title ?? t('state.error.title')}</h3>
        <p className="text-gray-600 mb-4">{message}</p>
        {correlationId && (
          <p className="text-xs text-gray-400 mb-4">ID: {correlationId}</p>
        )}
        {onRetry && (
          <Button onClick={onRetry} variant="primary">
            {t('state.retry')}
          </Button>
        )}
      </div>
    </div>
  );
}

interface EmptyStateProps {
  title?: string;
  message?: string;
  icon?: React.ReactNode;
  action?: React.ReactNode;
}

export function EmptyState({ title, message, icon, action }: EmptyStateProps) {
  return (
    <div className="flex items-center justify-center min-h-[400px]">
      <div className="text-center max-w-md">
        {icon ?? (
          <div className="text-gray-400 mb-4">
            <svg className="w-16 h-16 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4" />
            </svg>
          </div>
        )}
        <h3 className="text-lg font-semibold text-gray-900 mb-2">{title ?? t('state.empty.title')}</h3>
        <p className="text-gray-600 mb-4">{message ?? t('state.empty.message')}</p>
        {action}
      </div>
    </div>
  );
}

interface ForbiddenStateProps {
  message?: string;
}

export function ForbiddenState({ message }: ForbiddenStateProps) {
  return (
    <div className="flex items-center justify-center min-h-[400px]">
      <div className="text-center max-w-md">
        <div className="text-yellow-500 mb-4">
          <svg className="w-16 h-16 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
          </svg>
        </div>
        <h3 className="text-lg font-semibold text-gray-900 mb-2">{t('forbidden.title')}</h3>
        <p className="text-gray-600">{message ?? t('forbidden.message')}</p>
      </div>
    </div>
  );
}
