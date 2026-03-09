/**
 * @fileoverview Alert Panel Component
 *
 * Displays active alerts with filtering, grouping,
 * and management capabilities.
 *
 * @module ui/components/alerts
 * @since 2.0.0
 */

import React, { useState, useMemo, useEffect } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';
import type { Alert } from '../../../analytics/AlertManager';
import { getMetricGlossaryEntry } from '../../../analytics/metrics/MetricGlossary';
import { MetricTooltip } from '../common/MetricTooltip';
import { hasPlaybook } from '../../../analytics/guidance/ActionPlaybooks';
import { ActionGuidancePanel } from '../guidance/ActionGuidancePanel';
import { useGuidance } from '../../context/GuidanceContext';

interface AlertPanelProps {
  alerts: Alert[];
  maxVisible?: number;
  showFilters?: boolean;
  onDismiss?: (alertId: string) => void;
  onResolve?: (alertId: string) => void;
}

/**
 * Alert Panel Component
 *
 * Displays and manages alerts with filtering and grouping.
 */
export const AlertPanel: React.FC<AlertPanelProps> = ({
  alerts,
  maxVisible = 10,
  showFilters = true,
  onDismiss,
  onResolve,
}) => {
  const [filter, setFilter] = useState<'all' | 'critical' | 'warning' | 'info'>('all');
  const [searchTerm, setSearchTerm] = useState('');
  const { openGuidance } = useGuidance();
  const [expandedAlertId, setExpandedAlertId] = useState<string | null>(null);

  // Filter alerts based on criteria
  const filteredAlerts = useMemo(() => {
    let result = [...alerts];

    // Filter by severity
    if (filter !== 'all') {
      result = result.filter(alert => alert.severity === filter);
    }

    // Filter by search term
    if (searchTerm) {
      const q = String(searchTerm).toLowerCase();
      result = result.filter((alert) => {
        const metric = String(alert?.metric ?? '').toLowerCase();
        const message = String(alert?.message ?? '').toLowerCase();
        return metric.includes(q) || message.includes(q);
      });
    }

    // Sort by timestamp (newest first) and severity
    return result.sort((a, b) => {
      const severityOrder = { critical: 3, warning: 2, info: 1 };
      const aSeverity = severityOrder[a.severity as keyof typeof severityOrder];
      const bSeverity = severityOrder[b.severity as keyof typeof severityOrder];

      if (aSeverity !== bSeverity) {
        return bSeverity - aSeverity;
      }

      return b.timestamp - a.timestamp;
    });
  }, [alerts, filter, searchTerm]);

  const visibleAlerts = filteredAlerts.slice(0, maxVisible);
  const hasMore = filteredAlerts.length > maxVisible;

  useEffect(() => {
    if (expandedAlertId && !visibleAlerts.some(alert => alert.id === expandedAlertId)) {
      setExpandedAlertId(null);
    }
  }, [expandedAlertId, visibleAlerts]);

  // Get alert count by severity
  const alertCounts = useMemo(() => {
    return alerts.reduce((acc, alert) => {
      acc[alert.severity] = (acc[alert.severity] || 0) + 1;
      return acc;
    }, {} as Record<string, number>);
  }, [alerts]);

  // Get severity styling
  const getSeverityStyles = (severity: Alert['severity']) => {
    switch (severity) {
      case 'critical':
        return {
          bg: 'bg-rose-50',
          border: 'border-rose-200',
          text: 'text-rose-700',
          badge: 'bg-rose-100 text-rose-700',
          icon: '🚨',
        };
      case 'warning':
        return {
          bg: 'bg-amber-50',
          border: 'border-amber-200',
          text: 'text-amber-700',
          badge: 'bg-amber-100 text-amber-700',
          icon: '⚠️',
        };
      case 'info':
        return {
          bg: 'bg-blue-50',
          border: 'border-blue-200',
          text: 'text-blue-700',
          badge: 'bg-blue-100 text-blue-700',
          icon: 'ℹ️',
        };
      default:
        return {
          bg: 'bg-slate-50',
          border: 'border-slate-200',
          text: 'text-slate-700',
          badge: 'bg-slate-100 text-slate-700',
          icon: '📋',
        };
    }
  };

  // Format timestamp
  const formatTimestamp = (timestamp: number): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    const diffHours = Math.floor(diffMs / 3600000);
    const diffDays = Math.floor(diffMs / 86400000);

    if (diffMins < 1) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString();
  };

  // Render filters
  const renderFilters = () => {
    if (!showFilters) return null;

    return (
      <div className="space-y-3">
        {/* Severity filter */}
        <div className="flex flex-wrap gap-2">
          {(['all', 'critical', 'warning', 'info'] as const).map(severity => {
            const count = severity === 'all' ? alerts.length : (alertCounts[severity] || 0);
            const isActive = filter === severity;

            return (
              <button
                key={severity}
                onClick={() => setFilter(severity)}
                className={`px-3 py-1 text-sm rounded-full transition-colors ${isActive
                    ? 'bg-blue-600 text-white'
                    : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600'
                  }`}
              >
                {severity === 'all' ? 'All' : severity.charAt(0).toUpperCase() + severity.slice(1)}
                {count > 0 && (
                  <span className="ml-1 text-xs">({count})</span>
                )}
              </button>
            );
          })}
        </div>

        {/* Search */}
        <input
          type="text"
          placeholder="Search alerts..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="w-full px-3 py-2 text-sm border border-slate-300 dark:border-slate-600 rounded-md bg-white dark:bg-slate-800 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>
    );
  };

  // Render alert item
  const renderAlert = (alert: Alert) => {
    const styles = getSeverityStyles(alert.severity);
    let metricKeyForLookup = alert.metric;
    let glossaryEntry = getMetricGlossaryEntry(alert.metric);

    if (!glossaryEntry) {
      metricKeyForLookup = String(alert?.metric ?? '').toLowerCase();
      glossaryEntry = getMetricGlossaryEntry(metricKeyForLookup);
    }

    const unit = glossaryEntry?.unit ?? '';
    const formatMetricValue = (value?: number): string => {
      if (typeof value !== 'number' || Number.isNaN(value)) return '—';
      if (Number.isInteger(value)) {
        return `${value.toLocaleString()}${unit}`;
      }
      const precision = Math.abs(value) < 1 ? 2 : 1;
      return `${value.toFixed(precision)}${unit}`;
    };

    const severityLabel: Record<Alert['severity'], string> = {
      critical: 'is critically out of range',
      warning: 'needs attention',
      info: 'has a new insight',
    };

    const severityLine = glossaryEntry
      ? `${glossaryEntry.fullName} ${severityLabel[alert.severity]}`
      : alert.message;

    const targetSymbol = (glossaryEntry?.direction ?? 'lower-is-better') === 'higher-is-better' ? '≥' : '≤';
    const goodTarget = glossaryEntry ? `${targetSymbol} ${formatMetricValue(glossaryEntry.goodThreshold)}` : undefined;
    const mappedSeverity = alert.severity === 'critical' ? 'critical' : alert.severity === 'warning' ? 'warning' : undefined;
    const hasGuidance = Boolean(mappedSeverity && metricKeyForLookup && hasPlaybook(metricKeyForLookup, mappedSeverity));
    const isExpanded = expandedAlertId === alert.id;

    return (
      <div
        key={alert.id}
        className={`p-3 border rounded-lg ${styles.bg} ${styles.border}`}
      >
        <div className="flex items-start justify-between mb-2">
          <div className="flex items-center gap-2">
            <span className="text-lg">{styles.icon}</span>
            <div>
              <h4 className="font-semibold text-slate-900">
                {glossaryEntry ? (
                  <MetricTooltip metricKey={metricKeyForLookup}>
                    <span className="inline-flex items-center gap-1">
                      {`${glossaryEntry.fullName} (${glossaryEntry.shortName})`}
                      <span aria-hidden className="text-xs text-slate-400">ℹ️</span>
                    </span>
                  </MetricTooltip>
                ) : (
                  alert.metric
                )}
              </h4>
              <span className={`inline-block px-2 py-0.5 text-xs font-medium rounded-full ${styles.badge}`}>
                {alert.severity.toUpperCase()}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">
              {formatTimestamp(alert.timestamp)}
            </span>
            {hasGuidance && (
              <button
                className="rounded-md border border-blue-200 px-2 py-1 text-xs font-medium text-blue-600 hover:bg-blue-50"
                onClick={() => setExpandedAlertId(isExpanded ? null : alert.id)}
              >
                {isExpanded ? 'Hide Fix Guide' : 'Show Fix Guide'}
              </button>
            )}
            {(onDismiss || onResolve) && (
              <div className="flex gap-1">
                {onResolve && (
                  <button
                    onClick={() => onResolve(alert.id)}
                    className="p-1 text-slate-400 hover:text-green-600"
                    title="Resolve"
                  >
                    ✓
                  </button>
                )}
                {onDismiss && (
                  <button
                    onClick={() => onDismiss(alert.id)}
                    className="p-1 text-slate-400 hover:text-slate-600"
                    title="Dismiss"
                  >
                    ×
                  </button>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="space-y-2">
          <p className={`text-sm ${styles.text}`}>
            {severityLine}
          </p>

          <ul className="space-y-1 text-xs text-slate-600">
            <li>• Current: {formatMetricValue(alert.value)}</li>
            {alert.threshold !== undefined && (
              <li>• Critical threshold: {formatMetricValue(alert.threshold)}</li>
            )}
            {goodTarget && (
              <li>• Good target: {goodTarget}</li>
            )}
            {glossaryEntry && (
              <li>• Impact: {glossaryEntry.why}</li>
            )}
            {alert.message && (!glossaryEntry || severityLine !== alert.message) && (
              <li>• Note: {alert.message}</li>
            )}
            {hasGuidance && (
              <li>
                •{' '}
                <button
                  type="button"
                  className="text-blue-600 hover:text-blue-700 hover:underline"
                  onClick={() =>
                    openGuidance({
                      metric: metricKeyForLookup,
                      severity: mappedSeverity ?? 'critical',
                      currentValue: alert.value,
                      source: 'alert',
                    })
                  }
                >
                  Open in guidance workspace
                </button>
              </li>
            )}
          </ul>
        </div>

        {hasGuidance && isExpanded && mappedSeverity && (
          <div className="mt-3 rounded-lg border border-blue-100 bg-white p-3">
            <ActionGuidancePanel
              metric={metricKeyForLookup}
              severity={mappedSeverity}
              currentValue={alert.value}
            />
          </div>
        )}
      </div>
    );
  };

  return (
    <Card
      title={`Alerts${alerts.length > 0 ? ` (${alerts.length})` : ''}`}
      description={alerts.length === 0 ? 'No active alerts' : undefined}
    >
      <div className="space-y-4">
        {renderFilters()}

        {visibleAlerts.length === 0 ? (
          <div className="text-center py-8 text-slate-500">
            <div className="text-lg font-medium mb-2">No Alerts Found</div>
            <div className="text-sm">
              {alerts.length === 0
                ? 'All systems are operating normally'
                : 'Try adjusting your filters'
              }
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            {visibleAlerts.map(renderAlert)}

            {hasMore && (
              <div className="text-center">
                <button className="text-sm text-blue-600 hover:text-blue-700">
                  Show {filteredAlerts.length - maxVisible} more alerts
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </Card>
  );
};

export default AlertPanel;
