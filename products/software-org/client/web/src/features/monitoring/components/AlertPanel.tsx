/**
 * Alert Panel Component
 *
 * <p><b>Purpose</b><br>
 * Displays system alerts with filtering, severity levels, and acknowledgment capabilities.
 * Used in Real-Time Monitor for alert management.
 *
 * <p><b>Features</b><br>
 * - Alert list with severity color coding
 * - Filter by severity
 * - Acknowledgment action
 * - Timestamp display
 * - Alert details/descriptions
 * - Dark mode support
 * - Empty state handling
 *
 * @doc.type component
 * @doc.purpose Alert management and display component
 * @doc.layer product
 * @doc.pattern Display Component
 */

import { memo } from 'react';

export interface Alert {
    id: string;
    title: string;
    description: string;
    severity: 'info' | 'warning' | 'critical';
    timestamp: Date;
    acknowledged: boolean;
    source?: string;
}

interface AlertPanelProps {
    alerts: Alert[];
    isLoading: boolean;
    onAcknowledge: (alertId: string) => void;
    onFilterChange?: (filter: string) => void;
    activeFilter?: string;
}

/**
 * AlertPanel component - displays and manages system alerts.
 *
 * GIVEN: Array of system alerts with severity levels
 * WHEN: Component renders
 * THEN: Display alerts with filtering and acknowledgment options
 *
 * @param alerts - Array of alert objects
 * @param isLoading - Loading state indicator
 * @param onAcknowledge - Callback when alert is acknowledged
 * @param onFilterChange - Optional callback for filter changes
 * @param activeFilter - Current active filter
 * @returns Rendered alert panel component
 */
const AlertPanel = memo(
    ({
        alerts,
        isLoading,
        onAcknowledge,
        onFilterChange,
        activeFilter,
    }: AlertPanelProps) => {
        // Get severity color
        const getSeverityColor = (severity: string) => {
            switch (severity) {
                case 'critical':
                    return 'text-red-600 dark:text-rose-400 bg-red-50 dark:bg-rose-600/30 border-l-4 border-red-500';
                case 'warning':
                    return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-orange-600/30 border-l-4 border-yellow-500';
                case 'info':
                    return 'text-blue-600 dark:text-indigo-400 bg-blue-50 dark:bg-indigo-600/30 border-l-4 border-blue-500';
                default:
                    return 'text-slate-600 dark:text-neutral-400 bg-slate-50 dark:bg-neutral-800 border-l-4 border-slate-500';
            }
        };

        const getSeverityIcon = (severity: string) => {
            switch (severity) {
                case 'critical':
                    return '🔴';
                case 'warning':
                    return '🟡';
                case 'info':
                    return 'ℹ️';
                default:
                    return '•';
            }
        };

        // Filter alerts by current filter
        const filteredAlerts =
            activeFilter && activeFilter !== 'all'
                ? alerts.filter((a) => a.severity === activeFilter)
                : alerts;

        // Group by acknowledged status
        const unacknowledgedAlerts = filteredAlerts.filter((a) => !a.acknowledged);
        const acknowledgedAlerts = filteredAlerts.filter((a) => a.acknowledged);

        return (
            <div
                className="rounded-lg bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 flex flex-col h-full"
                role="region"
                aria-label="Active alerts"
            >
                {/* Header */}
                <div className="p-4 border-b border-slate-200 dark:border-neutral-600">
                    <div className="flex justify-between items-center">
                        <h3 className="font-bold text-slate-900 dark:text-neutral-100">
                            Alerts ({filteredAlerts.length})
                        </h3>
                        <div className="text-sm text-slate-600 dark:text-neutral-400">
                            {unacknowledgedAlerts.length} new
                        </div>
                    </div>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-y-auto">
                    {isLoading ? (
                        <div className="flex items-center justify-center h-32 p-4">
                            <div className="animate-spin rounded-full h-6 w-6 border-t-2 border-b-2 border-blue-500" />
                        </div>
                    ) : filteredAlerts.length === 0 ? (
                        <div className="flex items-center justify-center h-32 p-4 text-slate-500 dark:text-neutral-400">
                            No alerts
                        </div>
                    ) : (
                        <div className="space-y-0">
                            {/* Unacknowledged Alerts */}
                            {unacknowledgedAlerts.map((alert, idx) => (
                                <div
                                    key={alert.id}
                                    className={`p-3 border-b border-slate-100 dark:border-neutral-600 last:border-b-0 ${getSeverityColor(
                                        alert.severity
                                    )}`}
                                    role="article"
                                    aria-label={`${alert.severity} alert: ${alert.title}`}
                                >
                                    <div className="flex justify-between items-start gap-2">
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-start gap-2">
                                                <span className="text-lg leading-none flex-shrink-0">
                                                    {getSeverityIcon(alert.severity)}
                                                </span>
                                                <div className="min-w-0">
                                                    <p className="font-semibold text-sm break-words">
                                                        {alert.title}
                                                    </p>
                                                    <p className="text-xs opacity-75 mt-1 break-words">
                                                        {alert.description}
                                                    </p>
                                                    {alert.source && (
                                                        <p className="text-xs opacity-60 mt-1">
                                                            Source: {alert.source}
                                                        </p>
                                                    )}
                                                    <p className="text-xs opacity-50 mt-1">
                                                        {new Date(alert.timestamp).toLocaleTimeString()}
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                        <button
                                            onClick={() => onAcknowledge(alert.id)}
                                            className="px-2 py-1 text-xs font-medium bg-current/10 hover:bg-current/20 rounded transition-colors flex-shrink-0"
                                            aria-label={`Acknowledge ${alert.title}`}
                                        >
                                            Ack
                                        </button>
                                    </div>
                                </div>
                            ))}

                            {/* Acknowledged Alerts (collapsed section) */}
                            {acknowledgedAlerts.length > 0 && (
                                <div className="p-3 bg-slate-50 dark:bg-neutral-700/50 border-t border-slate-200 dark:border-neutral-600">
                                    <details className="cursor-pointer">
                                        <summary className="text-sm font-medium text-slate-600 dark:text-neutral-400 select-none">
                                            ▼ Acknowledged ({acknowledgedAlerts.length})
                                        </summary>
                                        <div className="mt-3 space-y-2">
                                            {acknowledgedAlerts.map((alert) => (
                                                <div
                                                    key={alert.id}
                                                    className="text-xs p-2 bg-white dark:bg-neutral-800 rounded opacity-60"
                                                    role="article"
                                                    aria-label={`Acknowledged: ${alert.title}`}
                                                >
                                                    <p className="font-semibold">{alert.title}</p>
                                                    <p className="text-xs mt-1">
                                                        {new Date(alert.timestamp).toLocaleTimeString()}
                                                    </p>
                                                </div>
                                            ))}
                                        </div>
                                    </details>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>
        );
    }
);

AlertPanel.displayName = 'AlertPanel';

export default AlertPanel;
