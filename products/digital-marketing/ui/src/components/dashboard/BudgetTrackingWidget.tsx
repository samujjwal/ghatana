/**
 * P1-015: Budget Tracking Widget for DMOS Command Center.
 *
 * Displays budget utilization and spending trends.
 *
 * @doc.type component
 * @doc.purpose Budget tracking widget for dashboard
 * @doc.layer frontend
 */
import React from 'react';
import { Link } from 'react-router-dom';
import { DataFreshnessBadge } from '@/components/dashboard/DataFreshnessBadge';
import { DashboardWidgetCard } from '@/components/dashboard/DashboardWidgetCard';
import { formatCurrency, formatPercent } from '@/lib/i18n/format';
import type { DashboardConfidenceLevel, DashboardFreshnessStatus } from '@/types/dashboard';

interface BudgetTrackingWidgetProps {
  workspaceId: string;
  totalBudget?: number | null;
  spent?: number | null;
  remaining?: number | null;
  utilizationPercent?: number | null;
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

export function BudgetTrackingWidget({
  workspaceId,
  totalBudget = null,
  spent = null,
  remaining = null,
  utilizationPercent = null,
  isLoading = false,
  isError = false,
  unavailableReason,
  source,
  lastUpdated,
  isPartial = false,
  freshnessStatus,
  confidence,
  authorizationScope,
}: BudgetTrackingWidgetProps): React.ReactElement {
  if (isLoading) {
    return (
      <DashboardWidgetCard
        testId="budget-tracking-widget"
        title="Budget Tracking"
        state="loading"
      />
    );
  }

  if (isError) {
    return (
      <DashboardWidgetCard
        testId="budget-tracking-widget"
        title="Budget Tracking"
        state="error"
        message="Failed to load budget data"
      />
    );
  }

  const hasPrimaryBudget = totalBudget !== null;
  const hasSpend = spent !== null && remaining !== null && utilizationPercent !== null;

  if (!hasPrimaryBudget && unavailableReason) {
    return (
      <DashboardWidgetCard
        testId="budget-tracking-widget"
        title="Budget Tracking"
        state="unavailable"
        message={unavailableReason}
        stateMessageTestId="budget-tracking-unavailable"
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

  const utilization = utilizationPercent ?? 0;
  const utilizationColor =
    utilization > 90
      ? 'text-red-600'
      : utilization > 75
      ? 'text-yellow-600'
      : 'text-green-800';

  return (
    <DashboardWidgetCard
      testId="budget-tracking-widget"
      title="Budget Tracking"
      footer={(
        <>
          <DataFreshnessBadge
            source={source}
            lastUpdated={lastUpdated}
            isPartial={isPartial}
            freshnessStatus={freshnessStatus}
            confidence={confidence}
            authorizationScope={authorizationScope}
          />
          {(isPartial || isStaleStatus(freshnessStatus)) && (
            <p className="mt-1 text-[11px] text-yellow-700" data-testid="budget-tracking-state">
              {isPartial ? 'Budget metrics are partial.' : 'Budget metrics are stale.'}
            </p>
          )}
          <Link
            to={`/workspaces/${workspaceId}/budget`}
            className="mt-3 block text-xs text-blue-600 hover:underline"
          >
            Manage budget →
          </Link>
        </>
      )}
    >
      <div className="space-y-2">
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-800">Total Budget</span>
          <span className="text-sm font-semibold text-gray-900">
            {formatCurrency(totalBudget)}
          </span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-800">Spent</span>
          <span className="text-sm font-semibold text-gray-900">
            {formatCurrency(spent)}
          </span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-800">Remaining</span>
          <span className="text-sm font-semibold text-gray-900">
            {formatCurrency(remaining)}
          </span>
        </div>
        <div className="border-t border-gray-200 pt-2 mt-2" data-testid="budget-utilization-block">
          <div className="flex justify-between items-center mb-1">
            <span className="text-xs font-medium text-gray-900">Utilization</span>
            <span className={`text-sm font-bold ${utilizationColor}`}>
              {formatPercent(utilizationPercent)}
            </span>
          </div>
          {hasSpend ? (
            <progress
              className={`h-2 w-full overflow-hidden rounded-full bg-gray-200 ${
                utilization > 90
                  ? '[&::-webkit-progress-value]:bg-red-500 [&::-moz-progress-bar]:bg-red-500'
                  : utilization > 75
                  ? '[&::-webkit-progress-value]:bg-yellow-500 [&::-moz-progress-bar]:bg-yellow-500'
                  : '[&::-webkit-progress-value]:bg-green-500 [&::-moz-progress-bar]:bg-green-500'
              } [&::-webkit-progress-bar]:rounded-full [&::-webkit-progress-bar]:bg-gray-200 [&::-webkit-progress-value]:rounded-full [&::-moz-progress-bar]:rounded-full`}
              max={100}
              value={Math.min(utilization, 100)}
              aria-label="Budget utilization"
            />
          ) : (
            <p className="text-[11px] text-gray-500" data-testid="budget-utilization-unavailable">
              Spend utilization is unavailable until spend telemetry is connected.
            </p>
          )}
        </div>
      </div>
    </DashboardWidgetCard>
  );
}

function isStaleStatus(status: DashboardFreshnessStatus | undefined): boolean {
  return status === 'STALE' || status === 'VERY_STALE' || status === 'CRITICAL';
}
