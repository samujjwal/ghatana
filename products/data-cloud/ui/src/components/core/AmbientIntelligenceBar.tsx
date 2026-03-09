/**
 * Ambient Intelligence Bar Component
 *
 * Bottom bar showing aggregated quality, cost, governance, learning, execution, and health metrics.
 * Provides passive notifications with click-to-expand functionality.
 *
 * Features:
 * - Always visible at bottom of screen
 * - Shows aggregated counts with color coding
 * - Click to expand inline panel (no page navigation)
 * - AI prioritizes what to show based on urgency
 * - Execution status for running pipelines
 * - System health indicators
 *
 * @doc.type component
 * @doc.purpose Passive ambient notifications bar
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import {
  AlertCircle,
  TrendingDown,
  Shield,
  Lightbulb,
  ChevronUp,
  X,
  RefreshCw,
  Wifi,
  WifiOff,
  Play,
  AlertTriangle,
  Activity,
  Heart,
} from 'lucide-react';
import { cn } from '../../lib/theme';
import { useAmbientIntelligence } from '../../hooks/useAmbientIntelligence';
import type { AmbientMetric, AmbientMetricType } from '../../stores/ambient.store';

interface AmbientIntelligenceBarProps {
  className?: string;
}

/**
 * Icon mapping for metric types
 */
const METRIC_ICONS: Record<AmbientMetricType, React.ReactNode> = {
  quality: <AlertCircle className="h-4 w-4" />,
  cost: <TrendingDown className="h-4 w-4" />,
  governance: <Shield className="h-4 w-4" />,
  pattern: <Lightbulb className="h-4 w-4" />,
  learning: <Lightbulb className="h-4 w-4" />,
  execution: <Play className="h-4 w-4" />,
  alert: <AlertTriangle className="h-4 w-4" />,
  health: <Heart className="h-4 w-4" />,
};

/**
 * Color mapping for severity
 */
const SEVERITY_COLORS = {
  critical: {
    bg: 'bg-red-100 dark:bg-red-900/30',
    text: 'text-red-700 dark:text-red-300',
    border: 'border-red-200 dark:border-red-800',
    icon: 'text-red-500',
  },
  warning: {
    bg: 'bg-amber-100 dark:bg-amber-900/30',
    text: 'text-amber-700 dark:text-amber-300',
    border: 'border-amber-200 dark:border-amber-800',
    icon: 'text-amber-500',
  },
  info: {
    bg: 'bg-blue-100 dark:bg-blue-900/30',
    text: 'text-blue-700 dark:text-blue-300',
    border: 'border-blue-200 dark:border-blue-800',
    icon: 'text-blue-500',
  },
};

/**
 * Metric Badge Component
 */
function MetricBadge({
  metric,
  onClick,
}: {
  metric: AmbientMetric;
  onClick?: () => void;
}) {
  const colors = SEVERITY_COLORS[metric.severity];

  return (
    <button
      onClick={onClick}
      className={cn(
        'flex items-center gap-2 px-3 py-1.5 rounded-full',
        'border transition-all',
        'hover:shadow-md hover:scale-105',
        colors.bg,
        colors.text,
        colors.border
      )}
    >
      <span className={colors.icon}>{METRIC_ICONS[metric.type]}</span>
      <span className="text-xs font-medium whitespace-nowrap">
        {metric.summary}
      </span>
    </button>
  );
}

/**
 * Expanded Detail Panel
 */
function DetailPanel({
  metrics,
  type,
  onClose,
  onDismiss,
}: {
  metrics: AmbientMetric[];
  type: AmbientMetricType;
  onClose: () => void;
  onDismiss: (id: string) => void;
}) {
  const typeLabels: Record<AmbientMetricType, string> = {
    quality: 'Data Quality',
    cost: 'Cost Optimization',
    governance: 'Governance',
    pattern: 'Patterns',
    learning: 'Learning',
    execution: 'Pipeline Executions',
    alert: 'System Alerts',
    health: 'System Health',
  };

  return (
    <div
      className={cn(
        'absolute bottom-full left-0 right-0 mb-1',
        'bg-white dark:bg-gray-900',
        'border border-gray-200 dark:border-gray-700',
        'rounded-t-xl shadow-lg',
        'max-h-64 overflow-y-auto'
      )}
    >
      <div className="sticky top-0 flex items-center justify-between px-4 py-2 bg-gray-50 dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700">
        <h3 className="font-medium text-gray-900 dark:text-gray-100">
          {typeLabels[type]}
        </h3>
        <button
          onClick={onClose}
          className="p-1 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
        >
          <X className="h-4 w-4 text-gray-500" />
        </button>
      </div>
      <div className="p-4 space-y-2">
        {metrics.map((metric) => (
          <div
            key={metric.id}
            className={cn(
              'flex items-center gap-3 p-3 rounded-lg',
              SEVERITY_COLORS[metric.severity].bg
            )}
          >
            <span className={SEVERITY_COLORS[metric.severity].icon}>
              {METRIC_ICONS[metric.type]}
            </span>
            <div className="flex-1">
              <p className={cn('text-sm font-medium', SEVERITY_COLORS[metric.severity].text)}>
                {metric.summary}
              </p>
              {metric.timestamp && (
                <p className="text-xs text-gray-500 mt-0.5">
                  {new Date(metric.timestamp).toLocaleTimeString()}
                </p>
              )}
            </div>
            <button
              onClick={() => onDismiss(metric.id)}
              className="p-1 hover:bg-white/50 dark:hover:bg-black/20 rounded"
            >
              <X className="h-3 w-3 text-gray-400" />
            </button>
          </div>
        ))}
        {metrics.length === 0 && (
          <p className="text-center text-gray-500 py-4">
            No {typeLabels[type].toLowerCase()} issues
          </p>
        )}
      </div>
    </div>
  );
}

/**
 * Ambient Intelligence Bar Component
 */
export function AmbientIntelligenceBar({ className }: AmbientIntelligenceBarProps) {
  const {
    metrics,
    qualityMetrics,
    costMetrics,
    governanceMetrics,
    learningMetrics,
    executionMetrics,
    alertMetrics,
    healthMetrics,
    criticalCount,
    warningCount,
    isLoading,
    connectionStatus,
    refresh,
    dismissMetric,
  } = useAmbientIntelligence();

  const [expandedType, setExpandedType] = useState<AmbientMetricType | null>(null);

  // Group metrics by type and get the highest priority one per type
  const displayMetrics: AmbientMetric[] = [];

  // Add one representative from each type with issues
  if (qualityMetrics.length > 0) {
    // Find the most severe one
    const critical = qualityMetrics.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || qualityMetrics[0]);
  }
  if (costMetrics.length > 0) {
    displayMetrics.push(costMetrics[0]);
  }
  if (governanceMetrics.length > 0) {
    const critical = governanceMetrics.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || governanceMetrics[0]);
  }
  if (executionMetrics.length > 0) {
    const critical = executionMetrics.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || executionMetrics[0]);
  }
  if (healthMetrics.length > 0) {
    const critical = healthMetrics.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || healthMetrics[0]);
  }
  if (alertMetrics.length > 0) {
    const critical = alertMetrics.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || alertMetrics[0]);
  }
  if (learningMetrics.length > 0) {
    displayMetrics.push(learningMetrics[0]);
  }

  const handleBadgeClick = (type: AmbientMetricType) => {
    setExpandedType(expandedType === type ? null : type);
  };

  const getMetricsForType = (type: AmbientMetricType): AmbientMetric[] => {
    switch (type) {
      case 'quality':
        return qualityMetrics;
      case 'cost':
        return costMetrics;
      case 'governance':
        return governanceMetrics;
      case 'pattern':
      case 'learning':
        return learningMetrics;
      case 'execution':
        return executionMetrics;
      case 'alert':
        return alertMetrics;
      case 'health':
        return healthMetrics;
      default:
        return [];
    }
  };

  // Don't show if no metrics
  if (displayMetrics.length === 0 && !isLoading) {
    return null;
  }

  return (
    <div className={cn('relative', className)}>
      {/* Expanded Panel */}
      {expandedType && (
        <DetailPanel
          metrics={getMetricsForType(expandedType)}
          type={expandedType}
          onClose={() => setExpandedType(null)}
          onDismiss={dismissMetric}
        />
      )}

      {/* Main Bar */}
      <div
        className={cn(
          'flex items-center gap-3 px-4 py-2',
          'bg-white dark:bg-gray-900',
          'border-t border-gray-200 dark:border-gray-700',
          'shadow-lg'
        )}
      >
        {/* Connection Status */}
        <div className="flex items-center gap-1">
          {connectionStatus === 'connected' ? (
            <Wifi className="h-3 w-3 text-green-500" />
          ) : connectionStatus === 'reconnecting' ? (
            <RefreshCw className="h-3 w-3 text-amber-500 animate-spin" />
          ) : (
            <WifiOff className="h-3 w-3 text-gray-400" />
          )}
        </div>

        {/* Divider */}
        <div className="w-px h-6 bg-gray-200 dark:bg-gray-700" />

        {/* Metrics */}
        <div className="flex-1 flex items-center gap-2 overflow-x-auto">
          {isLoading ? (
            <div className="flex items-center gap-2">
              <RefreshCw className="h-4 w-4 text-gray-400 animate-spin" />
              <span className="text-xs text-gray-500">Loading...</span>
            </div>
          ) : displayMetrics.length > 0 ? (
            displayMetrics.map((metric) => (
              <MetricBadge
                key={metric.id}
                metric={metric}
                onClick={() => handleBadgeClick(metric.type)}
              />
            ))
          ) : (
            <span className="text-xs text-gray-500">
              ✨ All systems healthy
            </span>
          )}
        </div>

        {/* Summary Counts */}
        <div className="flex items-center gap-3">
          {criticalCount > 0 && (
            <div className="flex items-center gap-1 text-red-500">
              <AlertCircle className="h-4 w-4" />
              <span className="text-xs font-bold">{criticalCount}</span>
            </div>
          )}
          {warningCount > 0 && (
            <div className="flex items-center gap-1 text-amber-500">
              <AlertCircle className="h-4 w-4" />
              <span className="text-xs font-bold">{warningCount}</span>
            </div>
          )}
        </div>

        {/* Refresh Button */}
        <button
          onClick={refresh}
          disabled={isLoading}
          className={cn(
            'p-1.5 rounded-lg',
            'hover:bg-gray-100 dark:hover:bg-gray-800',
            'transition-colors',
            isLoading && 'opacity-50 cursor-not-allowed'
          )}
        >
          <RefreshCw
            className={cn('h-4 w-4 text-gray-400', isLoading && 'animate-spin')}
          />
        </button>
      </div>
    </div>
  );
}

export default AmbientIntelligenceBar;

