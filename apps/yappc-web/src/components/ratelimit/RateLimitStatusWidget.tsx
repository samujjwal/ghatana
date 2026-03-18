/**
 * Rate Limit Status Widget Component
 *
 * <p><b>Purpose</b><br>
 * Real-time visualization of rate limit usage with progress bar, countdown timer,
 * tier display, and upgrade CTA. Updates automatically and shows color-coded
 * status indicators.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <RateLimitStatusWidget
 *   userId="user-123"
 *   onUpgrade={handleUpgrade}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Rate limit usage visualization
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React, { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';

/**
 * Rate limit status interface
 */
interface RateLimitStatus {
  identifier: string;
  tier: string;
  used: number;
  limit: number;
  remaining: number;
  percentage: number;
  resetTime: Date;
  isLimited: boolean;
  lastRequestAt?: Date;
}

/**
 * Props for RateLimitStatusWidget component
 */
interface RateLimitStatusWidgetProps {
  userId?: string;
  autoRefresh?: boolean;
  refreshInterval?: number;
  onUpgrade?: () => void;
  onViewDetails?: () => void;
}

/**
 * RateLimitStatusWidget component
 */
export const RateLimitStatusWidget: React.FC<RateLimitStatusWidgetProps> = ({
  userId,
  autoRefresh = true,
  refreshInterval = 10000, // 10 seconds
  onUpgrade,
  onViewDetails,
}) => {
  const [timeUntilReset, setTimeUntilReset] = useState<string>('');

  // Fetch rate limit status
  const { data: status, isLoading, refetch } = useQuery<RateLimitStatus>({
    queryKey: ['rateLimitStatus', userId],
    queryFn: async () => {
      const url = userId
        ? `/api/rate-limit/status/${userId}`
        : '/api/rate-limit/status/me';
      const response = await fetch(url);
      if (!response.ok) throw new Error('Failed to fetch rate limit status');
      return response.json();
    },
    refetchInterval: autoRefresh ? refreshInterval : false,
  });

  /**
   * Updates countdown timer
   */
  useEffect(() => {
    if (!status?.resetTime) return;

    const updateTimer = () => {
      const now = new Date();
      const reset = new Date(status.resetTime);
      const diff = reset.getTime() - now.getTime();

      if (diff <= 0) {
        setTimeUntilReset('Resetting...');
        refetch();
        return;
      }

      const minutes = Math.floor(diff / 60000);
      const seconds = Math.floor((diff % 60000) / 1000);

      if (minutes > 60) {
        const hours = Math.floor(minutes / 60);
        setTimeUntilReset(`${hours}h ${minutes % 60}m`);
      } else {
        setTimeUntilReset(`${minutes}m ${seconds}s`);
      }
    };

    updateTimer();
    const interval = setInterval(updateTimer, 1000);

    return () => clearInterval(interval);
  }, [status?.resetTime, refetch]);

  /**
   * Gets progress bar color
   */
  const getProgressColor = () => {
    if (!status) return 'bg-gray-400';
    if (status.percentage >= 90) return 'bg-red-600';
    if (status.percentage >= 70) return 'bg-orange-500';
    if (status.percentage >= 50) return 'bg-yellow-500';
    return 'bg-green-500';
  };

  /**
   * Gets tier badge color
   */
  const getTierBadgeColor = () => {
    if (!status) return 'bg-gray-100 text-gray-800';
    switch (status.tier.toLowerCase()) {
      case 'enterprise':
        return 'bg-purple-100 text-purple-800';
      case 'pro':
        return 'bg-blue-100 text-blue-800';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  /**
   * Gets status label
   */
  const getStatusLabel = () => {
    if (!status) return 'Unknown';
    if (status.isLimited) return 'Limited';
    if (status.percentage >= 90) return 'Critical';
    if (status.percentage >= 70) return 'High';
    if (status.percentage >= 50) return 'Medium';
    return 'Low';
  };

  /**
   * Gets status label color
   */
  const getStatusLabelColor = () => {
    if (!status) return 'text-gray-600';
    if (status.isLimited) return 'text-red-600';
    if (status.percentage >= 90) return 'text-red-600';
    if (status.percentage >= 70) return 'text-orange-600';
    if (status.percentage >= 50) return 'text-yellow-600';
    return 'text-green-600';
  };

  if (isLoading) {
    return (
      <div className="bg-white rounded-lg shadow p-6 animate-pulse">
        <div className="h-4 bg-gray-200 rounded w-1/2 mb-4"></div>
        <div className="h-8 bg-gray-200 rounded mb-2"></div>
        <div className="h-2 bg-gray-200 rounded"></div>
      </div>
    );
  }

  if (!status) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <div className="text-red-600">Failed to load rate limit status</div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-lg shadow p-6">
      {/* Header */}
      <div className="flex justify-between items-start mb-4">
        <div>
          <h3 className="text-lg font-semibold mb-1">API Rate Limit</h3>
          <div className="flex items-center gap-2">
            <span className={`px-2 py-1 rounded text-xs font-medium ${getTierBadgeColor()}`}>
              {status.tier.toUpperCase()} TIER
            </span>
            <span className={`text-sm font-medium ${getStatusLabelColor()}`}>
              {getStatusLabel()}
            </span>
          </div>
        </div>
        {onUpgrade && status.tier.toLowerCase() !== 'enterprise' && (
          <button
            onClick={onUpgrade}
            className="px-3 py-1 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
          >
            Upgrade
          </button>
        )}
      </div>

      {/* Usage stats */}
      <div className="mb-4">
        <div className="flex justify-between items-baseline mb-2">
          <span className="text-2xl font-bold">
            {status.used.toLocaleString()}
            <span className="text-sm text-gray-500 font-normal">
              {' '}
              / {status.limit.toLocaleString()} requests
            </span>
          </span>
          <span className="text-sm text-gray-600">{status.percentage.toFixed(1)}% used</span>
        </div>

        {/* Progress bar */}
        <div className="w-full bg-gray-200 rounded-full h-3 overflow-hidden">
          <div
            className={`h-full transition-all duration-500 ${getProgressColor()}`}
            style={{ width: `${Math.min(status.percentage, 100)}%` }}
          />
        </div>
      </div>

      {/* Reset timer */}
      <div className="flex items-center justify-between py-3 border-t border-gray-100">
        <div className="text-sm text-gray-600">
          <div className="font-medium">Resets in</div>
          <div className="text-gray-500">{timeUntilReset}</div>
        </div>
        <div className="text-right text-sm text-gray-600">
          <div className="font-medium">{status.remaining.toLocaleString()}</div>
          <div className="text-gray-500">remaining</div>
        </div>
      </div>

      {/* Warning message */}
      {status.isLimited && (
        <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded-lg">
          <div className="flex items-start">
            <span className="text-red-600 text-lg mr-2">⚠️</span>
            <div className="flex-1">
              <div className="text-sm font-medium text-red-800">Rate limit exceeded</div>
              <div className="text-xs text-red-600 mt-1">
                You've reached your request limit. Please wait for the reset or upgrade your plan.
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Approaching limit warning */}
      {!status.isLimited && status.percentage >= 80 && (
        <div className="mt-4 p-3 bg-yellow-50 border border-yellow-200 rounded-lg">
          <div className="flex items-start">
            <span className="text-yellow-600 text-lg mr-2">⚡</span>
            <div className="flex-1">
              <div className="text-sm font-medium text-yellow-800">Approaching limit</div>
              <div className="text-xs text-yellow-600 mt-1">
                You've used {status.percentage.toFixed(0)}% of your quota. Consider upgrading for higher limits.
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Last request */}
      {status.lastRequestAt && (
        <div className="mt-4 text-xs text-gray-500">
          Last request: {new Date(status.lastRequestAt).toLocaleString()}
        </div>
      )}

      {/* View details link */}
      {onViewDetails && (
        <button
          onClick={onViewDetails}
          className="mt-4 w-full text-center text-sm text-blue-600 hover:text-blue-700"
        >
          View detailed usage →
        </button>
      )}
    </div>
  );
};

export default RateLimitStatusWidget;

