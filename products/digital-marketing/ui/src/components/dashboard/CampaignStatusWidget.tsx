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

interface CampaignStatusWidgetProps {
  workspaceId: string;
  activeCount?: number;
  pausedCount?: number;
  pendingCount?: number;
  isLoading?: boolean;
  isError?: boolean;
}

export function CampaignStatusWidget({
  workspaceId,
  activeCount = 0,
  pausedCount = 0,
  pendingCount = 0,
  isLoading = false,
  isError = false,
}: CampaignStatusWidgetProps): React.ReactElement {
  const total = activeCount + pausedCount + pendingCount;

  if (isLoading) {
    return (
      <div
        data-testid="campaign-status-widget"
        className="bg-white border border-gray-200 rounded-lg p-4"
      >
        <h2 className="text-sm font-semibold text-gray-700 mb-3">Campaign Status</h2>
        <div className="animate-pulse h-20 bg-gray-100 rounded" />
      </div>
    );
  }

  if (isError) {
    return (
      <div
        data-testid="campaign-status-widget"
        className="bg-white border border-gray-200 rounded-lg p-4"
      >
        <h2 className="text-sm font-semibold text-gray-700 mb-3">Campaign Status</h2>
        <div className="text-sm text-red-600">Failed to load campaign status</div>
      </div>
    );
  }

  return (
    <div
      data-testid="campaign-status-widget"
      className="bg-white border border-gray-200 rounded-lg p-4"
    >
      <h2 className="text-sm font-semibold text-gray-700 mb-3">Campaign Status</h2>
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
      <Link
        to={`/workspaces/${workspaceId}/campaigns`}
        className="mt-3 block text-xs text-blue-600 hover:underline"
      >
        View all campaigns →
      </Link>
    </div>
  );
}
