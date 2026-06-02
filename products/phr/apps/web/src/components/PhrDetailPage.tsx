import React from 'react';
import { Link } from 'react-router-dom';
import { PhrPage } from './PhrPage';
import { PhrDataState } from './PhrDataState';
import { PhrActionBar } from './PhrActionBar';
import { PhrSection } from './PhrSection';
import { t } from '../i18n/phrI18n';

/**
 * PhrDetailPage - Standard detail page component
 * Provides consistent drill-down page structure with breadcrumbs and back navigation
 * Ensures detail pages are true drill-downs from list pages
 */

interface PhrDetailPageProps {
  title: string;
  subtitle?: string;
  parentPath: string;
  parentLabel: string;
  loading: boolean;
  error: { message: string; correlationId?: string } | null;
  data: unknown;
  children?: React.ReactNode;
  actions?: Array<{
    id: string;
    label: string;
    onClick: () => void;
    variant?: 'primary' | 'secondary' | 'destructive' | 'ghost';
    disabled?: boolean;
  }>;
  onRetry?: () => void;
}

export function PhrDetailPage({
  title,
  subtitle,
  parentPath,
  parentLabel,
  loading,
  error,
  data,
  children,
  actions,
  onRetry,
}: PhrDetailPageProps): React.ReactElement {
  const breadcrumbs = [
    { label: parentLabel, href: parentPath },
    { label: title },
  ];

  const actionBar = actions && actions.length > 0 ? (
    <PhrActionBar actions={actions} align="right" />
  ) : undefined;

  return (
    <PhrPage
      title={title}
      subtitle={subtitle}
      breadcrumbs={breadcrumbs}
      actionBar={actionBar}
    >
      <PhrDataState
        loading={loading}
        error={error}
        data={data}
        onRetry={onRetry}
      >
        {children}
      </PhrDataState>
    </PhrPage>
  );
}
