/**
 * Contextual Observability Bar Component (OBS-001)
 *
 * Collapsible bottom bar showing quality, cost, governance, execution, and health
 * metrics only when relevant. Starts collapsed by default; surfaces as a compact
 * floating trigger until the user explicitly expands it. Unsupported placeholder
 * metrics are filtered out to avoid ambient noise.
 *
 * Features:
 * - Collapsed by default (persistent via localStorage)
 * - Compact floating trigger with severity counts
 * - Auto-expands when critical metrics appear (once per session)
 * - Click to expand inline panel (no page navigation)
 * - Explicit close control for user dismissal
 * - Unsupported boundary metrics are suppressed
 *
 * @doc.type component
 * @doc.purpose Contextual system status notifications bar (OBS-001)
 * @doc.layer frontend
 * @doc.pattern Component
 */

import React, { useEffect, useRef, useState } from 'react';
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

/** localStorage key for collapsed preference */
const COLLAPSED_LS_KEY = 'ambient-intelligence-collapsed';

/** Boundary summaries that are placeholder noise — suppressed in contextual mode */
const UNSUPPORTED_PHRASES = [
  'unavailable in the standalone launcher',
  'summary unavailable',
];

function isUnsupportedMetric(metric: AmbientMetric): boolean {
  return UNSUPPORTED_PHRASES.some((p) => metric.summary.toLowerCase().includes(p));
}

function readCollapsedPreference(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_LS_KEY) !== 'false';
  } catch {
    return true;
  }
}

function writeCollapsedPreference(collapsed: boolean): void {
  try {
    localStorage.setItem(COLLAPSED_LS_KEY, String(collapsed));
  } catch {
    /* ignore */
  }
}

/**
 * Compact Floating Trigger — shown when the bar is collapsed and actionable
 * metrics exist. Clicking expands the full bar.
 */
function CollapsedTrigger({
  criticalCount,
  warningCount,
  onExpand,
}: {
  criticalCount: number;
  warningCount: number;
  onExpand: () => void;
}) {
  const total = criticalCount + warningCount;
  if (total === 0) return null;

  return (
    <button
      onClick={onExpand}
      aria-label={`Expand observability bar. ${criticalCount} critical, ${warningCount} warning`}
      className={cn(
        'fixed bottom-4 right-4 z-50',
        'flex items-center gap-2 px-3 py-2 rounded-full',
        'shadow-lg border transition-all hover:scale-105',
        criticalCount > 0
          ? 'bg-red-50 dark:bg-red-900/30 border-red-200 dark:border-red-800 text-red-700 dark:text-red-300'
          : 'bg-amber-50 dark:bg-amber-900/30 border-amber-200 dark:border-amber-800 text-amber-700 dark:text-amber-300'
      )}
    >
      <Activity className="h-4 w-4" />
      <span className="text-xs font-medium">
        {criticalCount > 0 && (
          <span className="text-red-600 dark:text-red-300 font-bold">{criticalCount}</span>
        )}
        {criticalCount > 0 && warningCount > 0 && (
          <span className="mx-1 text-gray-400">|</span>
        )}
        {warningCount > 0 && (
          <span className="text-amber-600 dark:text-amber-300 font-bold">{warningCount}</span>
        )}
      </span>
      <ChevronUp className="h-3 w-3" />
    </button>
  );
}

/**
 * Ambient Intelligence Bar Component — OBS-001 contextual mode
 */
export function AmbientIntelligenceBar({ className }: AmbientIntelligenceBarProps) {
  const {
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
  const [isCollapsed, setIsCollapsed] = useState(readCollapsedPreference);
  const hasAutoExpanded = useRef(false);

  // Auto-expand once per session when critical metrics appear and user hasn't
  // explicitly interacted with the bar yet.
  useEffect(() => {
    if (criticalCount > 0 && isCollapsed && !hasAutoExpanded.current) {
      hasAutoExpanded.current = true;
      setIsCollapsed(false);
      writeCollapsedPreference(false);
    }
    // hasAutoExpanded is a ref — identity never changes; included for lint completeness
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [criticalCount, isCollapsed]);

  // Filter unsupported placeholder metrics to reduce ambient noise
  const filterActionable = (list: AmbientMetric[]) => list.filter((m) => !isUnsupportedMetric(m));

  const actionableQuality = filterActionable(qualityMetrics);
  const actionableCost = filterActionable(costMetrics);
  const actionableGovernance = filterActionable(governanceMetrics);
  const actionableLearning = filterActionable(learningMetrics);
  const actionableExecution = filterActionable(executionMetrics);
  const actionableAlert = filterActionable(alertMetrics);
  const actionableHealth = filterActionable(healthMetrics);

  // Group metrics by type and get the highest priority one per type
  const displayMetrics: AmbientMetric[] = [];

  if (actionableQuality.length > 0) {
    const critical = actionableQuality.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || actionableQuality[0]);
  }
  if (actionableCost.length > 0) {
    displayMetrics.push(actionableCost[0]);
  }
  if (actionableGovernance.length > 0) {
    const critical = actionableGovernance.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || actionableGovernance[0]);
  }
  if (actionableExecution.length > 0) {
    const critical = actionableExecution.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || actionableExecution[0]);
  }
  if (actionableHealth.length > 0) {
    const critical = actionableHealth.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || actionableHealth[0]);
  }
  if (actionableAlert.length > 0) {
    const critical = actionableAlert.find((m) => m.severity === 'critical');
    displayMetrics.push(critical || actionableAlert[0]);
  }
  if (actionableLearning.length > 0) {
    displayMetrics.push(actionableLearning[0]);
  }

  const handleBadgeClick = (type: AmbientMetricType) => {
    setExpandedType(expandedType === type ? null : type);
  };

  const getMetricsForType = (type: AmbientMetricType): AmbientMetric[] => {
    switch (type) {
      case 'quality':
        return actionableQuality;
      case 'cost':
        return actionableCost;
      case 'governance':
        return actionableGovernance;
      case 'pattern':
      case 'learning':
        return actionableLearning;
      case 'execution':
        return actionableExecution;
      case 'alert':
        return actionableAlert;
      case 'health':
        return actionableHealth;
      default:
        return [];
    }
  };

  const actionableCritical = displayMetrics.filter((m) => m.severity === 'critical').length;
  const actionableWarning = displayMetrics.filter((m) => m.severity === 'warning').length;
  const hasActionable = displayMetrics.length > 0 || isLoading;

  // Completely hidden when there is nothing actionable
  if (!hasActionable) {
    return null;
  }

  // Collapsed mode — show compact floating trigger only
  if (isCollapsed) {
    return (
      <CollapsedTrigger
        criticalCount={actionableCritical}
        warningCount={actionableWarning}
        onExpand={() => {
          setIsCollapsed(false);
          writeCollapsedPreference(false);
        }}
      />
    );
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
              All systems healthy
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
          aria-label="Refresh"
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

        {/* Collapse — OBS-001 explicit close control */}
        <button
          onClick={() => {
            setIsCollapsed(true);
            writeCollapsedPreference(true);
            setExpandedType(null);
          }}
          aria-label="Collapse status bar"
          className="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
        >
          <X className="h-4 w-4 text-gray-400" />
        </button>
      </div>
    </div>
  );
}

export default AmbientIntelligenceBar;

