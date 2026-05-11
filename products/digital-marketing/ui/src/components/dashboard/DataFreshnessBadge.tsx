import React from 'react';
import { formatDateTime } from '@/lib/i18n/format';
import type { DashboardConfidenceLevel, DashboardFreshnessStatus } from '@/types/dashboard';

interface DataFreshnessBadgeProps {
  source?: string;
  lastUpdated?: string | null;
  isPartial?: boolean;
  freshnessStatus?: DashboardFreshnessStatus;
  confidence?: DashboardConfidenceLevel;
  authorizationScope?: string;
  testId?: string;
}

export function DataFreshnessBadge({
  source,
  lastUpdated,
  isPartial = false,
  freshnessStatus,
  confidence,
  authorizationScope,
  testId = 'data-freshness-badge',
}: DataFreshnessBadgeProps): React.ReactElement {
  const status = resolveFreshnessLabel(isPartial, freshnessStatus, lastUpdated);

  return (
    <div className="mt-2 text-[11px] text-gray-500" data-testid={testId}>
      <span>{status}</span>
      {source ? <span> · Source: {source}</span> : null}
      {lastUpdated ? <span> · Updated: {formatDateTime(lastUpdated)}</span> : null}
      {confidence ? <span> · Confidence: {confidence.toLowerCase()}</span> : null}
      {authorizationScope ? <span> · Scope: {authorizationScope}</span> : null}
    </div>
  );
}

function resolveFreshnessLabel(
  isPartial: boolean,
  freshnessStatus: DashboardFreshnessStatus | undefined,
  lastUpdated: string | null | undefined,
): string {
  if (isPartial) {
    return 'Partial data';
  }
  switch (freshnessStatus) {
    case 'FRESH':
      return 'Fresh';
    case 'STALE':
      return 'Stale data';
    case 'VERY_STALE':
      return 'Very stale data';
    case 'CRITICAL':
      return 'Critical staleness';
    default:
      return lastUpdated ? 'Freshness reported' : 'Freshness unknown';
  }
}
