/**
 * Throttle Alert Banner Component
 *
 * <p><b>Purpose</b><br>
 * Alert banner displayed when user approaches or exceeds rate limit.
 * Dismissible notification with action buttons for upgrade and details.
 * Auto-dismisses after configurable timeout.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <ThrottleAlertBanner
 *   threshold={80}
 *   onUpgrade={handleUpgrade}
 *   onDismiss={handleDismiss}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Rate limit alert notification
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React, { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';

/**
 * Alert severity level
 */
type AlertSeverity = 'warning' | 'error' | 'info';

/**
 * Props for ThrottleAlertBanner component
 */
interface ThrottleAlertBannerProps {
  threshold?: number;
  autoDismissTimeout?: number;
  onUpgrade?: () => void;
  onViewDetails?: () => void;
  onDismiss?: () => void;
}

/**
 * ThrottleAlertBanner component
 */
export const ThrottleAlertBanner: React.FC<ThrottleAlertBannerProps> = ({
  threshold = 80,
  autoDismissTimeout = 0, // 0 = no auto dismiss
  onUpgrade,
  onViewDetails,
  onDismiss,
}) => {
  const [isDismissed, setIsDismissed] = useState(false);
  const [severity, setSeverity] = useState<AlertSeverity>('info');

  // Fetch rate limit status
  const { data: status } = useQuery({
    queryKey: ['rateLimitStatus'],
    queryFn: async () => {
      const response = await fetch('/api/rate-limit/status/me');
      if (!response.ok) throw new Error('Failed to fetch rate limit status');
      return response.json();
    },
    refetchInterval: 5000, // Check every 5 seconds
  });

  /**
   * Determines if alert should be shown
   */
  const shouldShowAlert = () => {
    if (!status || isDismissed) return false;
    return status.percentage >= threshold || status.isLimited;
  };

  /**
   * Updates severity based on usage
   */
  useEffect(() => {
    if (!status) return;

    if (status.isLimited) {
      setSeverity('error');
    } else if (status.percentage >= 90) {
      setSeverity('error');
    } else if (status.percentage >= threshold) {
      setSeverity('warning');
    } else {
      setSeverity('info');
    }
  }, [status, threshold]);

  /**
   * Auto-dismiss timer
   */
  useEffect(() => {
    if (autoDismissTimeout > 0 && shouldShowAlert()) {
      const timer = setTimeout(() => {
        handleDismiss();
      }, autoDismissTimeout);

      return () => clearTimeout(timer);
    }
  }, [autoDismissTimeout, shouldShowAlert]);

  /**
   * Handles dismiss
   */
  const handleDismiss = () => {
    setIsDismissed(true);
    onDismiss?.();
  };

  /**
   * Gets alert colors
   */
  const getAlertColors = () => {
    switch (severity) {
      case 'error':
        return {
          bg: 'bg-red-50',
          border: 'border-red-200',
          text: 'text-red-800',
          icon: 'text-red-600',
          button: 'bg-red-600 hover:bg-red-700',
        };
      case 'warning':
        return {
          bg: 'bg-yellow-50',
          border: 'border-yellow-200',
          text: 'text-yellow-800',
          icon: 'text-yellow-600',
          button: 'bg-yellow-600 hover:bg-yellow-700',
        };
      default:
        return {
          bg: 'bg-blue-50',
          border: 'border-blue-200',
          text: 'text-blue-800',
          icon: 'text-blue-600',
          button: 'bg-blue-600 hover:bg-blue-700',
        };
    }
  };

  /**
   * Gets alert icon
   */
  const getAlertIcon = () => {
    switch (severity) {
      case 'error':
        return '⚠️';
      case 'warning':
        return '⚡';
      default:
        return 'ℹ️';
    }
  };

  /**
   * Gets alert title
   */
  const getAlertTitle = () => {
    if (!status) return '';
    if (status.isLimited) return 'Rate limit exceeded';
    if (status.percentage >= 90) return 'Rate limit almost exceeded';
    return 'Approaching rate limit';
  };

  /**
   * Gets alert message
   */
  const getAlertMessage = () => {
    if (!status) return '';

    if (status.isLimited) {
      return `You've used all ${status.limit.toLocaleString()} requests for this hour. Your limit will reset in a few minutes, or you can upgrade for higher limits.`;
    }

    if (status.percentage >= 90) {
      return `You've used ${status.percentage.toFixed(0)}% (${status.used.toLocaleString()}/${status.limit.toLocaleString()}) of your hourly quota. Consider upgrading to avoid interruptions.`;
    }

    return `You've used ${status.percentage.toFixed(0)}% of your hourly quota. ${status.remaining.toLocaleString()} requests remaining.`;
  };

  if (!shouldShowAlert()) {
    return null;
  }

  const colors = getAlertColors();

  return (
    <div
      className={`${colors.bg} ${colors.border} border-l-4 rounded-lg shadow-lg p-4 mb-4 transition-all duration-300`}
      role="alert"
    >
      <div className="flex items-start">
        {/* Icon */}
        <span className={`${colors.icon} text-2xl mr-3 flex-shrink-0`}>
          {getAlertIcon()}
        </span>

        {/* Content */}
        <div className="flex-1 min-w-0">
          <h4 className={`${colors.text} font-semibold text-sm mb-1`}>
            {getAlertTitle()}
          </h4>
          <p className={`${colors.text} text-sm opacity-90`}>
            {getAlertMessage()}
          </p>

          {/* Actions */}
          <div className="flex flex-wrap gap-2 mt-3">
            {onUpgrade && status && status.tier.toLowerCase() !== 'enterprise' && (
              <button
                onClick={onUpgrade}
                className={`px-4 py-2 ${colors.button} text-white text-sm font-medium rounded transition-colors`}
              >
                Upgrade Plan
              </button>
            )}
            {onViewDetails && (
              <button
                onClick={onViewDetails}
                className={`px-4 py-2 border ${colors.border} ${colors.text} text-sm font-medium rounded hover:bg-white hover:bg-opacity-50 transition-colors`}
              >
                View Details
              </button>
            )}
          </div>
        </div>

        {/* Dismiss button */}
        <button
          onClick={handleDismiss}
          className={`${colors.text} opacity-60 hover:opacity-100 ml-3 flex-shrink-0`}
          aria-label="Dismiss alert"
        >
          <svg
            className="w-5 h-5"
            fill="currentColor"
            viewBox="0 0 20 20"
          >
            <path
              fillRule="evenodd"
              d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
              clipRule="evenodd"
            />
          </svg>
        </button>
      </div>

      {/* Progress indicator (for warnings only) */}
      {severity === 'warning' && status && (
        <div className="mt-3 ml-11">
          <div className="flex justify-between text-xs mb-1">
            <span className={colors.text}>Usage</span>
            <span className={colors.text}>{status.percentage.toFixed(1)}%</span>
          </div>
          <div className="w-full bg-white bg-opacity-50 rounded-full h-1.5 overflow-hidden">
            <div
              className={`h-full ${colors.button} transition-all duration-500`}
              style={{ width: `${Math.min(status.percentage, 100)}%` }}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default ThrottleAlertBanner;

