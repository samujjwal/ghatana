/**
 * P1-015: Campaign Status Widget for DMOS Command Center.
 *
 * Displays campaign status overview including active, paused, and pending campaigns.
 *
 * @doc.type component
 * @doc.purpose Campaign status overview widget for dashboard
 * @doc.layer frontend
 */
import React from 'react';
import { Link } from 'react-router-dom';
import { DataFreshnessBadge } from '@/components/dashboard/DataFreshnessBadge';
import { DashboardWidgetCard } from '@/components/dashboard/DashboardWidgetCard';
import type { DashboardConfidenceLevel, DashboardFreshnessStatus } from '@/types/dashboard';

interface CampaignStatusWidgetProps {
  workspaceId: string;
  activeCount?: number;
  pausedCount?: number;
  pendingCount?: number;
  isLoading?: boolean;
  isError?: boolean;
  unavailableReason?: string;
  source?: string;
  lastUpdated?: string | null;
  isPartial?: boolean;
  freshnessStatus?: DashboardFreshnessStatus;
  confidence?: DashboardConfidenceLevel;
  authorizationScope?: string;
}

export function CampaignStatusWidget({
  workspaceId,
  activeCount = 0,
  pausedCount = 0,
  pendingCount = 0,
  isLoading = false,
  isError = false,
  unavailableReason,
  source,
  lastUpdated,
  isPartial = false,
  freshnessStatus,
  confidence,
  authorizationScope,
}: CampaignStatusWidgetProps): React.ReactElement {
  const total = activeCount + pausedCount + pendingCount;

  if (isLoading) {
    return (
      <DashboardWidgetCard
        testId="campaign-status-widget"
        title="Campaign Status"
        state="loading"
      />
    );
  }

  if (isError) {
    return (
      <DashboardWidgetCard
        testId="campaign-status-widget"
        title="Campaign Status"
        state="error"
        message="Failed to load campaign status"
      />
    );
  }

  if (unavailableReason) {
    return (
      <DashboardWidgetCard
        testId="campaign-status-widget"
        title="Campaign Status"
        state="unavailable"
        message={unavailableReason}
        stateMessageTestId="campaign-status-unavailable"
        footer={(
          <DataFreshnessBadge
            source={source}
            lastUpdated={lastUpdated}
            isPartial={false}
            freshnessStatus={freshnessStatus}
            confidence={confidence}
            authorizationScope={authorizationScope}
          />
        )}
      />
    );
  }

  return (
    <DashboardWidgetCard
      testId="campaign-status-widget"
      title="Campaign Status"
      footer={(
        <>
          <Link
            to={`/workspaces/${workspaceId}/campaigns`}
            className="mt-3 block text-xs text-blue-600 hover:underline"
          >
            View all campaigns →
          </Link>
          <DataFreshnessBadge
            source={source}
            lastUpdated={lastUpdated}
            isPartial={isPartial}
            freshnessStatus={freshnessStatus}
            confidence={confidence}
            authorizationScope={authorizationScope}
          />
          {(isPartial || isStaleStatus(freshnessStatus)) && (
            <p className="mt-1 text-[11px] text-yellow-700" data-testid="campaign-status-state">
              {isPartial ? 'Campaign metrics are partial.' : 'Campaign metrics are stale.'}
            </p>
          )}
        </>
      )}
    >
      <div className="space-y-2">
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-600">Active</span>
          <span className="text-sm font-semibold text-green-600">{activeCount}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-600">Paused</span>
          <span className="text-sm font-semibold text-yellow-600">{pausedCount}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-600">Pending</span>
          <span className="text-sm font-semibold text-blue-600">{pendingCount}</span>
        </div>
        <div className="border-t border-gray-200 pt-2 mt-2">
          <div className="flex justify-between items-center">
            <span className="text-xs font-medium text-gray-700">Total</span>
            <span className="text-sm font-bold text-gray-900">{total}</span>
          </div>
        </div>
      </div>
    </DashboardWidgetCard>
  );
}

function isStaleStatus(status: DashboardFreshnessStatus | undefined): boolean {
  return status === 'STALE' || status === 'VERY_STALE' || status === 'CRITICAL';
}
