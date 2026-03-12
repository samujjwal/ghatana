/**
 * Alerts Page
 * 
 * System alerts and notifications management with AI-powered triage.
 * 
 * Features:
 * - Smart alert grouping by root cause
 * - AI-suggested resolutions
 * - Automated correlation detection
 * - One-click bulk actions
 * 
 * @doc.type page
 * @doc.purpose AI-powered alert management
 * @doc.layer frontend
 * @doc.pattern Page Component
 */

import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
    Sparkles,
    Zap,
    AlertTriangle,
    Link2,
    ChevronDown,
    ChevronRight,
    Lightbulb,
    Play,
    X,
} from 'lucide-react';
import {
    cn,
    cardStyles,
    textStyles,
    bgStyles,
    buttonStyles,
    metricCardStyles,
} from '../lib/theme';
import { AlertRuleForm, type AlertRule } from '../components/alerts/AlertRuleForm';
import { alertsService } from '../api/alerts.service';
import type { Alert, AlertGroup, ResolutionSuggestion, AlertSeverity, AlertStatus } from '../api/alerts.service';

/**
 * Severity styles
 */
const severityStyles: Record<AlertSeverity, string> = {
    critical: 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200',
    warning: 'bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200',
    info: 'bg-blue-100 dark:bg-blue-900 text-blue-800 dark:text-blue-200',
};

/**
 * Status styles
 */
const statusStyles: Record<AlertStatus, string> = {
    active: 'bg-red-100 dark:bg-red-900 text-red-800 dark:text-red-200',
    acknowledged: 'bg-yellow-100 dark:bg-yellow-900 text-yellow-800 dark:text-yellow-200',
    resolved: 'bg-green-100 dark:bg-green-900 text-green-800 dark:text-green-200',
};

/**
 * AI Alert Group Card
 */
function AlertGroupCard({
    group,
    alerts,
    onResolveGroup,
}: {
    group: AlertGroup;
    alerts: Alert[];
    onResolveGroup: () => void;
}) {
    const [expanded, setExpanded] = useState(false);
    const groupedAlerts = alerts.filter((a) => group.alertIds.includes(a.id));

    return (
        <div className={cn(
            'bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20',
            'border border-purple-200 dark:border-purple-800',
            'rounded-xl overflow-hidden mb-4'
        )}>
            <div
                onClick={() => setExpanded(!expanded)}
                className="w-full flex items-center justify-between px-4 py-3 hover:bg-purple-100/50 dark:hover:bg-purple-900/30 transition-colors cursor-pointer"
            >
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-purple-100 dark:bg-purple-900/50 rounded-lg">
                        <Link2 className="h-4 w-4 text-purple-600 dark:text-purple-400" />
                    </div>
                    <div className="text-left">
                        <div className="flex items-center gap-2">
                            <span className="font-medium text-gray-900 dark:text-gray-100">
                                {group.title}
                            </span>
                            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 bg-purple-100 dark:bg-purple-900/50 text-purple-600 dark:text-purple-400 text-xs rounded">
                                <Sparkles className="h-3 w-3" />
                                {Math.round(group.aiConfidence * 100)}% confidence
                            </span>
                            <span className="text-xs px-1.5 py-0.5 bg-gray-100 dark:bg-gray-700 text-gray-500 rounded">
                                {groupedAlerts.length} related alerts
                            </span>
                        </div>
                        <p className="text-sm text-gray-500 mt-0.5">{group.rootCause}</p>
                    </div>
                </div>
                <div className="flex items-center gap-3">
                    {group.suggestedActionType === 'auto' && (
                        <button
                            onClick={(e) => {
                                e.stopPropagation();
                                onResolveGroup();
                            }}
                            className={cn(
                                'flex items-center gap-1 px-3 py-1.5 rounded-lg',
                                'bg-green-600 hover:bg-green-700',
                                'text-white text-sm font-medium',
                                'transition-colors'
                            )}
                        >
                            <Zap className="h-3 w-3" />
                            Auto-resolve
                        </button>
                    )}
                    {expanded ? (
                        <ChevronDown className="h-5 w-5 text-gray-400" />
                    ) : (
                        <ChevronRight className="h-5 w-5 text-gray-400" />
                    )}
                </div>
            </div>

            {expanded && (
                <div className="px-4 pb-4 border-t border-purple-200 dark:border-purple-800">
                    <div className="mt-3 p-3 bg-white/50 dark:bg-gray-800/50 rounded-lg">
                        <div className="flex items-start gap-2">
                            <Lightbulb className="h-4 w-4 text-amber-500 mt-0.5" />
                            <div>
                                <p className="text-sm font-medium text-gray-700 dark:text-gray-300">
                                    Suggested Action
                                </p>
                                <p className="text-sm text-gray-500">{group.suggestedAction}</p>
                            </div>
                        </div>
                    </div>
                    <div className="mt-3 space-y-2">
                        {groupedAlerts.map((alert) => (
                            <div
                                key={alert.id}
                                className="flex items-center gap-3 p-2 bg-white/50 dark:bg-gray-800/50 rounded-lg"
                            >
                                <AlertTriangle className={cn(
                                    'h-4 w-4',
                                    alert.severity === 'critical' ? 'text-red-500' :
                                    alert.severity === 'warning' ? 'text-amber-500' : 'text-blue-500'
                                )} />
                                <span className="text-sm text-gray-700 dark:text-gray-300 flex-1">
                                    {alert.title}
                                </span>
                                <span className="text-xs text-gray-400">{alert.source}</span>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

/**
 * AI Resolution Suggestion Card
 */
function ResolutionSuggestionCard({
    suggestion,
    onApply,
    onDismiss,
}: {
    suggestion: ResolutionSuggestion;
    onApply: () => void;
    onDismiss: () => void;
}) {
    const [showSteps, setShowSteps] = useState(false);

    return (
        <div className={cn(
            'p-3 rounded-lg',
            'bg-green-50 dark:bg-green-900/20',
            'border border-green-200 dark:border-green-800'
        )}>
            <div className="flex items-start justify-between gap-3">
                <div className="flex items-start gap-2">
                    <Lightbulb className="h-4 w-4 text-green-600 dark:text-green-400 mt-0.5" />
                    <div>
                        <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                            {suggestion.suggestion}
                        </p>
                        <div className="flex items-center gap-2 mt-1">
                            <span className="text-xs text-gray-500">
                                {Math.round(suggestion.confidence * 100)}% confidence
                            </span>
                            {suggestion.steps && (
                                <button
                                    onClick={() => setShowSteps(!showSteps)}
                                    className="text-xs text-green-600 dark:text-green-400 hover:underline"
                                >
                                    {showSteps ? 'Hide steps' : 'Show steps'}
                                </button>
                            )}
                        </div>
                        {showSteps && suggestion.steps && (
                            <ol className="mt-2 ml-4 text-xs text-gray-500 list-decimal space-y-1">
                                {suggestion.steps.map((step, i) => (
                                    <li key={i}>{step}</li>
                                ))}
                            </ol>
                        )}
                    </div>
                </div>
                <div className="flex items-center gap-2">
                    {suggestion.canAutoResolve && (
                        <button
                            onClick={onApply}
                            className={cn(
                                'flex items-center gap-1 px-2 py-1 rounded',
                                'bg-green-600 hover:bg-green-700',
                                'text-white text-xs',
                                'transition-colors'
                            )}
                        >
                            <Play className="h-3 w-3" />
                            Apply
                        </button>
                    )}
                    <button
                        onClick={onDismiss}
                        className="p-1 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    >
                        <X className="h-4 w-4 text-gray-400" />
                    </button>
                </div>
            </div>
        </div>
    );
}

/**
 * Alerts Page Component
 */
export function AlertsPage(): React.ReactElement {
    const queryClient = useQueryClient();
    const [filter, setFilter] = useState<'all' | AlertSeverity>('all');
    const [statusFilter, setStatusFilter] = useState<'all' | AlertStatus>('all');
    const [isRuleFormOpen, setIsRuleFormOpen] = useState(false);
    const [viewMode, setViewMode] = useState<'list' | 'grouped'>('grouped');
    const [dismissedSuggestions, setDismissedSuggestions] = useState<string[]>([]);

    const { data: allAlerts = [] } = useQuery({
        queryKey: ['alerts'],
        queryFn: () => alertsService.getAlerts(),
    });

    const { data: alertGroups = [] } = useQuery({
        queryKey: ['alerts', 'groups'],
        queryFn: () => alertsService.getAlertGroups(),
    });

    const { data: suggestions = [] } = useQuery({
        queryKey: ['alerts', 'suggestions'],
        queryFn: () => alertsService.getResolutionSuggestions(),
    });

    const acknowledgeMutation = useMutation({
        mutationFn: (alertId: string) => alertsService.acknowledgeAlert(alertId),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
    });

    const resolveMutation = useMutation({
        mutationFn: (alertId: string) => alertsService.resolveAlert(alertId),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
    });

    const resolveGroupMutation = useMutation({
        mutationFn: (groupId: string) => alertsService.resolveGroup(groupId),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
    });

    const applySuggestionMutation = useMutation({
        mutationFn: (suggestionId: string) => alertsService.applySuggestion(suggestionId),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
    });

    // SSE stream for live alert events
    useEffect(() => {
        const es = alertsService.openStream();
        es.addEventListener('message', () => {
            queryClient.invalidateQueries({ queryKey: ['alerts'] });
        });
        return () => es.close();
    }, [queryClient]);

    const filteredAlerts = allAlerts.filter((alert) => {
        if (filter !== 'all' && alert.severity !== filter) return false;
        if (statusFilter !== 'all' && alert.status !== statusFilter) return false;
        return true;
    });

    const alertCounts = {
        critical: allAlerts.filter((a) => a.severity === 'critical' && a.status === 'active').length,
        warning: allAlerts.filter((a) => a.severity === 'warning' && a.status === 'active').length,
        info: allAlerts.filter((a) => a.severity === 'info' && a.status === 'active').length,
        total: allAlerts.filter((a) => a.status === 'active').length,
    };

    const handleSaveRule = (_rule: AlertRule) => {
        // TODO: Integrate with alert rules API
    };

    return (
        <div className={cn('min-h-screen p-6', bgStyles.page)}>
            {/* Alert Rule Form Modal */}
            <AlertRuleForm
                isOpen={isRuleFormOpen}
                onClose={() => setIsRuleFormOpen(false)}
                onSave={handleSaveRule}
            />

            {/* Header */}
            <div className="flex justify-between items-center mb-6">
                <div>
                    <h1 className={textStyles.h1}>Alerts</h1>
                    <p className={textStyles.muted}>Monitor and manage system alerts</p>
                </div>
                <div className="flex gap-2">
                    <button className={buttonStyles.secondary}>Configure Rules</button>
                    <button
                        onClick={() => setIsRuleFormOpen(true)}
                        className={buttonStyles.primary}
                    >
                        + Create Alert Rule
                    </button>
                </div>
            </div>

            {/* Stats */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
                <div className={metricCardStyles.red}>
                    <p className={textStyles.muted}>Critical</p>
                    <p className={cn(textStyles.h2, 'text-red-600 dark:text-red-400')}>{alertCounts.critical}</p>
                </div>
                <div className={metricCardStyles.yellow}>
                    <p className={textStyles.muted}>Warning</p>
                    <p className={cn(textStyles.h2, 'text-yellow-600 dark:text-yellow-400')}>{alertCounts.warning}</p>
                </div>
                <div className={metricCardStyles.blue}>
                    <p className={textStyles.muted}>Info</p>
                    <p className={cn(textStyles.h2, 'text-blue-600 dark:text-blue-400')}>{alertCounts.info}</p>
                </div>
                <div className={metricCardStyles.base}>
                    <p className={textStyles.muted}>Total Active</p>
                    <p className={textStyles.h2}>{alertCounts.total}</p>
                </div>
            </div>

            {/* Filters */}
            <div className="flex items-center justify-between gap-4 mb-6">
                <div className="flex gap-4">
                    <select
                        value={filter}
                        onChange={(e) => setFilter(e.target.value as 'all' | AlertSeverity)}
                        className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm"
                    >
                        <option value="all">All Severities</option>
                        <option value="critical">Critical</option>
                        <option value="warning">Warning</option>
                        <option value="info">Info</option>
                    </select>
                    <select
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value as 'all' | AlertStatus)}
                        className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-700 text-sm"
                    >
                        <option value="all">All Statuses</option>
                        <option value="active">Active</option>
                        <option value="acknowledged">Acknowledged</option>
                        <option value="resolved">Resolved</option>
                    </select>
                </div>

                {/* View Mode Toggle */}
                <div className="flex items-center gap-2 bg-gray-100 dark:bg-gray-800 p-1 rounded-lg">
                    <button
                        onClick={() => setViewMode('grouped')}
                        className={cn(
                            'flex items-center gap-2 px-3 py-1.5 rounded-md text-sm',
                            viewMode === 'grouped'
                                ? 'bg-white dark:bg-gray-700 shadow-sm text-gray-900 dark:text-gray-100'
                                : 'text-gray-600 dark:text-gray-400'
                        )}
                    >
                        <Sparkles className="h-4 w-4" />
                        AI Grouped
                    </button>
                    <button
                        onClick={() => setViewMode('list')}
                        className={cn(
                            'flex items-center gap-2 px-3 py-1.5 rounded-md text-sm',
                            viewMode === 'list'
                                ? 'bg-white dark:bg-gray-700 shadow-sm text-gray-900 dark:text-gray-100'
                                : 'text-gray-600 dark:text-gray-400'
                        )}
                    >
                        List View
                    </button>
                </div>
            </div>

            {/* AI Grouped View */}
            {viewMode === 'grouped' && alertGroups.length > 0 && (
                <div className="mb-6">
                    <div className="flex items-center gap-2 mb-4">
                        <Sparkles className="h-5 w-5 text-purple-500" />
                        <h2 className={textStyles.h3}>AI-Detected Correlations</h2>
                        <span className="text-xs px-2 py-0.5 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded">
                            {alertGroups.length} groups found
                        </span>
                    </div>
                    {alertGroups.map((group) => (
                        <AlertGroupCard
                            key={group.id}
                            group={group}
                            alerts={allAlerts}
                            onResolveGroup={() => resolveGroupMutation.mutate(group.id)}
                        />
                    ))}
                </div>
            )}

            {/* AI Resolution Suggestions */}
            {viewMode === 'list' && suggestions.filter(s => !dismissedSuggestions.includes(s.id)).length > 0 && (
                <div className="mb-6">
                    <div className="flex items-center gap-2 mb-4">
                        <Lightbulb className="h-5 w-5 text-amber-500" />
                        <h2 className={textStyles.h3}>AI Resolution Suggestions</h2>
                    </div>
                    <div className="space-y-3">
                        {suggestions
                            .filter(s => !dismissedSuggestions.includes(s.id))
                            .map((suggestion) => (
                                <ResolutionSuggestionCard
                                    key={suggestion.id}
                                    suggestion={suggestion}
                                    onApply={() => applySuggestionMutation.mutate(suggestion.id)}
                                    onDismiss={() => setDismissedSuggestions([...dismissedSuggestions, suggestion.id])}
                                />
                            ))}
                    </div>
                </div>
            )}

            {/* Alert List */}
            <div className="space-y-4">
                {filteredAlerts.map((alert) => (
                    <div
                        key={alert.id}
                        className={cn(
                            cardStyles.base,
                            cardStyles.padded,
                            alert.severity === 'critical' && alert.status === 'active' && 'border-l-4 border-red-500'
                        )}
                    >
                        <div className="flex items-start justify-between">
                            <div className="flex-1">
                                <div className="flex items-center gap-3 mb-2">
                                    <span className={cn('px-2 py-1 rounded text-xs font-medium', severityStyles[alert.severity])}>
                                        {alert.severity.toUpperCase()}
                                    </span>
                                    <span className={cn('px-2 py-1 rounded text-xs font-medium', statusStyles[alert.status])}>
                                        {alert.status}
                                    </span>
                                    <span className={textStyles.xs}>{alert.source}</span>
                                </div>
                                <h3 className={textStyles.h3}>{alert.title}</h3>
                                <p className={cn(textStyles.muted, 'mt-1')}>{alert.description}</p>
                                <p className={cn(textStyles.xs, 'mt-2')}>
                                    Created: {new Date(alert.createdAt).toLocaleString()}
                                    {alert.acknowledgedAt && ` • Acknowledged: ${new Date(alert.acknowledgedAt).toLocaleString()}`}
                                    {alert.resolvedAt && ` • Resolved: ${new Date(alert.resolvedAt).toLocaleString()}`}
                                </p>
                            </div>
                            <div className="flex gap-2">
                                {alert.status === 'active' && (
                                    <button
                                        onClick={() => acknowledgeMutation.mutate(alert.id)}
                                        className={cn(buttonStyles.secondary, buttonStyles.sm)}
                                    >
                                        Acknowledge
                                    </button>
                                )}
                                {alert.status !== 'resolved' && (
                                    <button
                                        onClick={() => resolveMutation.mutate(alert.id)}
                                        className={cn(buttonStyles.success, buttonStyles.sm)}
                                    >
                                        Resolve
                                    </button>
                                )}
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            {filteredAlerts.length === 0 && (
                <div className={cn(cardStyles.base, cardStyles.padded, 'text-center py-12')}>
                    <p className={textStyles.muted}>No alerts match the current filters</p>
                </div>
            )}
        </div>
    );
}

export default AlertsPage;
