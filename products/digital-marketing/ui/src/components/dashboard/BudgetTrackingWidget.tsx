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

interface BudgetTrackingWidgetProps {
  workspaceId: string;
  totalBudget?: number;
  spent?: number;
  remaining?: number;
  utilizationPercent?: number;
  isLoading?: boolean;
  isError?: boolean;
}

export function BudgetTrackingWidget({
  workspaceId,
  totalBudget = 0,
  spent = 0,
  remaining = 0,
  utilizationPercent = 0,
  isLoading = false,
  isError = false,
}: BudgetTrackingWidgetProps): React.ReactElement {
  if (isLoading) {
    return (
      <div
        data-testid="budget-tracking-widget"
        className="bg-white border border-gray-200 rounded-lg p-4"
      >
        <h2 className="text-sm font-semibold text-gray-700 mb-3">Budget Tracking</h2>
        <div className="animate-pulse h-20 bg-gray-100 rounded" />
      </div>
    );
  }

  if (isError) {
    return (
      <div
        data-testid="budget-tracking-widget"
        className="bg-white border border-gray-200 rounded-lg p-4"
      >
        <h2 className="text-sm font-semibold text-gray-700 mb-3">Budget Tracking</h2>
        <div className="text-sm text-red-600">Failed to load budget data</div>
      </div>
    );
  }

  const utilizationColor =
    utilizationPercent > 90
      ? 'text-red-600'
      : utilizationPercent > 75
      ? 'text-yellow-600'
      : 'text-green-600';

  return (
    <div
      data-testid="budget-tracking-widget"
      className="bg-white border border-gray-200 rounded-lg p-4"
    >
      <h2 className="text-sm font-semibold text-gray-700 mb-3">Budget Tracking</h2>
      <div className="space-y-2">
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-600">Total Budget</span>
          <span className="text-sm font-semibold text-gray-900">
            ${totalBudget.toLocaleString()}
          </span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-600">Spent</span>
          <span className="text-sm font-semibold text-gray-900">
            ${spent.toLocaleString()}
          </span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-600">Remaining</span>
          <span className="text-sm font-semibold text-gray-900">
            ${remaining.toLocaleString()}
          </span>
        </div>
        <div className="border-t border-gray-200 pt-2 mt-2">
          <div className="flex justify-between items-center mb-1">
            <span className="text-xs font-medium text-gray-700">Utilization</span>
            <span className={`text-sm font-bold ${utilizationColor}`}>
              {utilizationPercent.toFixed(1)}%
            </span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-2">
            <div
              className={`h-2 rounded-full ${
                utilizationPercent > 90
                  ? 'bg-red-500'
                  : utilizationPercent > 75
                  ? 'bg-yellow-500'
                  : 'bg-green-500'
              }`}
              style={{ width: `${Math.min(utilizationPercent, 100)}%` }}
            />
          </div>
        </div>
      </div>
      <Link
        to={`/workspaces/${workspaceId}/budget`}
        className="mt-3 block text-xs text-blue-600 hover:underline"
      >
        Manage budget →
      </Link>
    </div>
  );
}
