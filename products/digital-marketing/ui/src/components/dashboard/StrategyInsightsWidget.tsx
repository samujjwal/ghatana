/**
 * P1-015: Strategy Insights Widget for DMOS Command Center.
 *
 * Displays AI-generated strategy insights and recommendations.
 *
 * @doc.type component
 * @doc.purpose Strategy insights widget for dashboard
 * @doc.layer frontend
 */
import React from 'react';
import { Link } from 'react-router-dom';
import { DashboardWidgetCard } from '@/components/dashboard/DashboardWidgetCard';

interface StrategyInsightsWidgetProps {
  workspaceId: string;
  strategyCount?: number;
  recentRecommendation?: string;
  confidenceScore?: number;
  isLoading?: boolean;
  isError?: boolean;
}

export function StrategyInsightsWidget({
  workspaceId,
  strategyCount = 0,
  recentRecommendation = 'No recent recommendations',
  confidenceScore = 0,
  isLoading = false,
  isError = false,
}: StrategyInsightsWidgetProps): React.ReactElement {
  if (isLoading) {
    return (
      <DashboardWidgetCard
        testId="strategy-insights-widget"
        title="Strategy Insights"
        state="loading"
      />
    );
  }

  if (isError) {
    return (
      <DashboardWidgetCard
        testId="strategy-insights-widget"
        title="Strategy Insights"
        state="error"
        message="Failed to load strategy data"
      />
    );
  }

  return (
    <DashboardWidgetCard
      testId="strategy-insights-widget"
      title="Strategy Insights"
      footer={(
        <Link
          to={`/workspaces/${workspaceId}/strategy`}
          className="mt-3 block text-xs text-blue-600 hover:underline"
        >
          View strategies →
        </Link>
      )}
    >
      <div className="space-y-2">
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-600">Active Strategies</span>
          <span className="text-sm font-semibold text-gray-900">{strategyCount}</span>
        </div>
        <div className="flex justify-between items-center">
          <span className="text-xs text-gray-600">AI Confidence</span>
          <span className="text-sm font-semibold text-gray-900">
            {(confidenceScore * 100).toFixed(0)}%
          </span>
        </div>
        <div className="border-t border-gray-200 pt-2 mt-2">
          <p className="text-xs text-gray-600 mb-1">Latest Recommendation:</p>
          <p className="text-xs text-gray-900 line-clamp-2">{recentRecommendation}</p>
        </div>
      </div>
    </DashboardWidgetCard>
  );
}
