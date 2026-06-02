import React from 'react';
import { LoadingState, ErrorState, EmptyState } from './PageStates';
import { t } from '../i18n/phrI18n';

/**
 * PhrDataState - Standard data state component
 * Provides consistent data loading, error, and empty states
 * Wraps PageStates components with PHR-specific defaults
 */

interface PhrDataStateProps {
  loading: boolean;
  error: { message: string; correlationId?: string } | null;
  data: unknown;
  emptyMessage?: string;
  onRetry?: () => void;
  children: React.ReactNode;
}

export function PhrDataState({
  loading,
  error,
  data,
  emptyMessage,
  onRetry,
  children,
}: PhrDataStateProps): React.ReactElement {
  if (loading) {
    return <LoadingState message={t('state.loading')} />;
  }

  if (error) {
    return (
      <ErrorState
        title={t('state.error.title')}
        message={error.message}
        correlationId={error.correlationId}
        onRetry={onRetry}
      />
    );
  }

  if (!data) {
    return <EmptyState message={emptyMessage || t('state.empty.message')} />;
  }

  return <>{children}</>;
}
