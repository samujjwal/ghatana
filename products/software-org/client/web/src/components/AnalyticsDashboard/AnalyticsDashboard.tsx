/**
 * Analytics Dashboard Component
 * 
 * Comprehensive analytics dashboard with:
 * - Permission usage analytics
 * - Role effectiveness metrics
 * - Security insights
 * - Anomaly detection alerts
 * - ML-powered recommendations
 * - Interactive charts and graphs
 * - Trend analysis
 * - Export capabilities
 */

import React, { useState } from 'react';
import {
    useAnalyticsDashboard,
    useSecurityInsights,
    useAnomalyDetection,
    useRecommendations,
} from '../../hooks/useAnalytics';
import {
    AnalyticsPeriod,
    InsightSeverity,
    InsightCategory,
    AnomalyType,
    RecommendationType,
} from '../../types/analytics';

interface AnalyticsDashboardProps {
    onInsightClick?: (insightId: string) => void;
    onAnomalyClick?: (anomalyId: string) => void;
    onRecommendationClick?: (recommendationId: string) => void;
    onExport?: () => void;
}

export function AnalyticsDashboard({
    onInsightClick,
    onAnomalyClick,
    onRecommendationClick,
    onExport,
}: AnalyticsDashboardProps) {
    const [period, setPeriod] = useState<AnalyticsPeriod>(AnalyticsPeriod.LAST_30_DAYS);

    const { dashboard, loading: dashboardLoading, error: dashboardError, refresh } = useAnalyticsDashboard(period);
    const { insights, loading: insightsLoading, error: insightsError } = useSecurityInsights({ period });
    const { anomalies, loading: anomaliesLoading, error: anomaliesError } = useAnomalyDetection({ period });
    const { recommendations, loading: recommendationsLoading, error: recommendationsError } = useRecommendations({ period });

    const loading = dashboardLoading || insightsLoading || anomaliesLoading || recommendationsLoading;
    const error = dashboardError || insightsError || anomaliesError || recommendationsError;

    const getSeverityColor = (severity: InsightSeverity): string => {
        switch (severity) {
            case InsightSeverity.CRITICAL:
                return 'text-red-600 dark:text-rose-400 bg-red-50 dark:bg-red-900/30 border-red-200 dark:border-red-700';
            case InsightSeverity.WARNING:
                return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/30 border-yellow-200 dark:border-yellow-700';
            case InsightSeverity.INFO:
                return 'text-blue-600 dark:text-indigo-400 bg-blue-50 dark:bg-blue-900/30 border-blue-200 dark:border-blue-700';
            case InsightSeverity.POSITIVE:
                return 'text-green-600 dark:text-green-400 bg-green-50 dark:bg-green-900/30 border-green-200 dark:border-green-700';
            default:
                return 'text-slate-600 dark:text-neutral-400 bg-slate-50 dark:bg-neutral-800 border-slate-200 dark:border-neutral-600';
        }
    };

    const getCategoryIcon = (category: InsightCategory): string => {
        switch (category) {
            case InsightCategory.SECURITY:
                return '🔒';
            case InsightCategory.COMPLIANCE:
                return '✓';
            case InsightCategory.PERFORMANCE:
                return '⚡';
            case InsightCategory.USAGE:
                return '📊';
            case InsightCategory.COST:
                return '💰';
            case InsightCategory.OPTIMIZATION:
                return '🎯';
            default:
                return '📌';
        }
    };

    const getAnomalyIcon = (type: AnomalyType): string => {
        switch (type) {
            case AnomalyType.UNUSUAL_ACCESS:
                return '👁️';
            case AnomalyType.PRIVILEGE_ESCALATION:
                return '⬆️';
            case AnomalyType.BULK_CHANGES:
                return '📦';
            case AnomalyType.OFF_HOURS_ACTIVITY:
                return '🌙';
            case AnomalyType.GEOGRAPHIC_ANOMALY:
                return '🌍';
            case AnomalyType.PERMISSION_CREEP:
                return '📈';
            default:
                return '⚠️';
        }
    };

    const getRecommendationIcon = (type: RecommendationType): string => {
        switch (type) {
            case RecommendationType.PERMISSION_OPTIMIZATION:
                return '🎯';
            case RecommendationType.ROLE_CONSOLIDATION:
                return '🔗';
            case RecommendationType.ACCESS_REVIEW:
                return '🔍';
            case RecommendationType.SECURITY_HARDENING:
                return '🔒';
            case RecommendationType.COMPLIANCE_IMPROVEMENT:
                return '✓';
            case RecommendationType.AUTOMATION:
                return '🤖';
            default:
                return '💡';
        }
    };

    const getPriorityColor = (priority: string): string => {
        switch (priority) {
            case 'high':
                return 'text-red-600 dark:text-rose-400 bg-red-50 dark:bg-red-900/30';
            case 'medium':
                return 'text-yellow-600 dark:text-yellow-400 bg-yellow-50 dark:bg-yellow-900/30';
            case 'low':
                return 'text-blue-600 dark:text-indigo-400 bg-blue-50 dark:bg-blue-900/30';
            default:
                return 'text-slate-600 dark:text-neutral-400 bg-slate-50 dark:bg-neutral-800';
        }
    };

    if (loading && !dashboard) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-12 w-12 border-4 border-blue-500 border-t-transparent"></div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-700 rounded-md p-4 text-red-800 dark:text-red-300">
                <p className="font-semibold">Error loading analytics dashboard</p>
                <p className="text-sm mt-1">{error.message}</p>
                <button
                    onClick={refresh}
                    className="mt-2 px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700"
                >
                    Retry
                </button>
            </div>
        );
    }

    if (!dashboard) {
        return (
            <div className="bg-slate-50 dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-md p-4 text-slate-600 dark:text-neutral-400">
                <p>No analytics data available</p>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                        Analytics & Insights
                    </h2>
                    <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                        AI-powered analytics and recommendations
                    </p>
                </div>
                <div className="flex gap-2">
                    <select
                        value={period}
                        onChange={(e) => setPeriod(e.target.value as AnalyticsPeriod)}
                        className="px-4 py-2 border border-slate-300 rounded-md bg-white dark:bg-neutral-800 dark:border-neutral-600"
                    >
                        <option value={AnalyticsPeriod.LAST_24_HOURS}>Last 24 Hours</option>
                        <option value={AnalyticsPeriod.LAST_7_DAYS}>Last 7 Days</option>
                        <option value={AnalyticsPeriod.LAST_30_DAYS}>Last 30 Days</option>
                        <option value={AnalyticsPeriod.LAST_90_DAYS}>Last 90 Days</option>
                        <option value={AnalyticsPeriod.LAST_YEAR}>Last Year</option>
                    </select>
                    {onExport && (
                        <button
                            onClick={onExport}
                            className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700"
                        >
                            📊 Export
                        </button>
                    )}
                    <button
                        onClick={refresh}
                        disabled={loading}
                        className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
                    >
                        {loading ? 'Refreshing...' : '🔄 Refresh'}
                    </button>
                </div>
            </div>

            {/* Summary Cards */}
            <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Security Score</p>
                            <p className="text-3xl font-bold text-green-600 mt-1">{dashboard.summary.securityScore.toFixed(1)}%</p>
                        </div>
                        <div className="text-4xl">🔒</div>
                    </div>
                </div>
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Active Permissions</p>
                            <p className="text-3xl font-bold text-blue-600 mt-1">{dashboard.summary.activePermissions}</p>
                        </div>
                        <div className="text-4xl">✓</div>
                    </div>
                </div>
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Effective Roles</p>
                            <p className="text-3xl font-bold text-purple-600 mt-1">{dashboard.summary.effectiveRoles}</p>
                        </div>
                        <div className="text-4xl">👥</div>
                    </div>
                </div>
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Active Users</p>
                            <p className="text-3xl font-bold text-indigo-600 mt-1">{dashboard.summary.activeUsers}</p>
                        </div>
                        <div className="text-4xl">👤</div>
                    </div>
                </div>
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-sm text-slate-600 dark:text-neutral-400">Compliance</p>
                            <p className="text-3xl font-bold text-green-600 mt-1">{dashboard.summary.complianceScore.toFixed(1)}%</p>
                        </div>
                        <div className="text-4xl">📋</div>
                    </div>
                </div>
            </div>

            {/* Main Content Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Security Insights */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <h3 className="text-lg font-semibold mb-4 flex items-center gap-2">
                        🔍 Security Insights
                        <span className="text-sm font-normal text-slate-600 dark:text-neutral-400">
                            ({insights.length})
                        </span>
                    </h3>
                    {insights.length === 0 ? (
                        <p className="text-sm text-slate-600 dark:text-neutral-400">No insights available</p>
                    ) : (
                        <div className="space-y-3">
                            {insights.slice(0, 5).map((insight) => (
                                <div
                                    key={insight.insightId}
                                    onClick={() => onInsightClick?.(insight.insightId)}
                                    className={`border rounded-lg p-3 cursor-pointer hover:shadow-md transition-shadow ${getSeverityColor(
                                        insight.severity
                                    )}`}
                                >
                                    <div className="flex items-start justify-between">
                                        <div className="flex items-start gap-2 flex-1">
                                            <span className="text-2xl">{getCategoryIcon(insight.category)}</span>
                                            <div className="flex-1">
                                                <p className="font-medium">{insight.title}</p>
                                                <p className="text-sm mt-1">{insight.description}</p>
                                                <div className="flex items-center gap-2 mt-2">
                                                    <span className="text-xs px-2 py-1 bg-white dark:bg-slate-900 rounded">
                                                        {insight.category}
                                                    </span>
                                                    {insight.actionable && (
                                                        <span className="text-xs px-2 py-1 bg-white dark:bg-slate-900 rounded">
                                                            Actionable
                                                        </span>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Anomaly Detection */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <h3 className="text-lg font-semibold mb-4 flex items-center gap-2">
                        ⚠️ Anomaly Detection
                        <span className="text-sm font-normal text-slate-600 dark:text-neutral-400">
                            ({anomalies.length})
                        </span>
                    </h3>
                    {anomalies.length === 0 ? (
                        <p className="text-sm text-slate-600 dark:text-neutral-400">No anomalies detected</p>
                    ) : (
                        <div className="space-y-3">
                            {anomalies.slice(0, 5).map((anomaly) => (
                                <div
                                    key={anomaly.anomalyId}
                                    onClick={() => onAnomalyClick?.(anomaly.anomalyId)}
                                    className={`border rounded-lg p-3 cursor-pointer hover:shadow-md transition-shadow ${getSeverityColor(
                                        anomaly.severity
                                    )}`}
                                >
                                    <div className="flex items-start gap-2">
                                        <span className="text-2xl">{getAnomalyIcon(anomaly.type)}</span>
                                        <div className="flex-1">
                                            <p className="font-medium">{anomaly.title}</p>
                                            <p className="text-sm mt-1">{anomaly.description}</p>
                                            <div className="flex items-center gap-2 mt-2 text-xs">
                                                <span className="px-2 py-1 bg-white dark:bg-slate-900 rounded">
                                                    Deviation: {anomaly.deviationPercentage.toFixed(0)}%
                                                </span>
                                                <span className="px-2 py-1 bg-white dark:bg-slate-900 rounded">
                                                    Confidence: {anomaly.confidenceScore.toFixed(0)}%
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>

            {/* Recommendations */}
            <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                <h3 className="text-lg font-semibold mb-4 flex items-center gap-2">
                    💡 AI-Powered Recommendations
                    <span className="text-sm font-normal text-slate-600 dark:text-neutral-400">
                        ({recommendations.length})
                    </span>
                </h3>
                {recommendations.length === 0 ? (
                    <p className="text-sm text-slate-600 dark:text-neutral-400">No recommendations available</p>
                ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {recommendations.slice(0, 6).map((rec) => (
                            <div
                                key={rec.recommendationId}
                                onClick={() => onRecommendationClick?.(rec.recommendationId)}
                                className="border border-slate-200 dark:border-neutral-600 rounded-lg p-4 cursor-pointer hover:shadow-md transition-shadow"
                            >
                                <div className="flex items-start gap-3">
                                    <span className="text-3xl">{getRecommendationIcon(rec.type)}</span>
                                    <div className="flex-1">
                                        <div className="flex items-start justify-between mb-2">
                                            <p className="font-medium">{rec.title}</p>
                                            <span className={`text-xs px-2 py-1 rounded ${getPriorityColor(rec.priority)}`}>
                                                {rec.priority.toUpperCase()}
                                            </span>
                                        </div>
                                        <p className="text-sm text-slate-600 dark:text-neutral-400 mb-2">
                                            {rec.description}
                                        </p>
                                        <div className="flex items-center gap-2 text-xs">
                                            <span className="px-2 py-1 bg-slate-100 dark:bg-neutral-700 rounded">
                                                Effort: {rec.estimatedEffort}
                                            </span>
                                            <span className="px-2 py-1 bg-slate-100 dark:bg-neutral-700 rounded">
                                                {rec.type.replace(/_/g, ' ')}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            {/* Permission & Role Metrics */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Top Used Permissions */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <h3 className="text-lg font-semibold mb-4">📊 Most Used Permissions</h3>
                    <div className="space-y-3">
                        {dashboard.topMetrics.mostUsedPermissions.slice(0, 5).map((perm, index) => (
                            <div key={perm.permissionId} className="flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                    <span className="text-lg font-bold text-slate-400">#{index + 1}</span>
                                    <div>
                                        <p className="text-sm font-medium">{perm.permissionName}</p>
                                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                                            {perm.uniqueUsers} users • {perm.usageCount} uses
                                        </p>
                                    </div>
                                </div>
                                <div className="text-right">
                                    <p className={`text-sm font-semibold ${perm.trend === 'increasing' ? 'text-green-600' : 'text-red-600'
                                        }`}>
                                        {perm.trend === 'increasing' ? '↑' : '↓'} {Math.abs(perm.trendPercentage).toFixed(1)}%
                                    </p>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>

                {/* Top Effective Roles */}
                <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                    <h3 className="text-lg font-semibold mb-4">🏆 Most Effective Roles</h3>
                    <div className="space-y-3">
                        {dashboard.topMetrics.mostEffectiveRoles.slice(0, 5).map((role, index) => (
                            <div key={role.roleId} className="flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                    <span className="text-lg font-bold text-slate-400">#{index + 1}</span>
                                    <div>
                                        <p className="text-sm font-medium">{role.roleName}</p>
                                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                                            {role.assignedUsers} users • {role.permissionsCount} permissions
                                        </p>
                                    </div>
                                </div>
                                <div className="text-right">
                                    <p className="text-sm font-semibold text-green-600">
                                        {role.effectivenessScore.toFixed(0)}%
                                    </p>
                                    <p className="text-xs text-slate-600 dark:text-neutral-400">
                                        {role.utilizationRate.toFixed(0)}% utilized
                                    </p>
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            </div>

            {/* Trend Charts (Simple ASCII visualization) */}
            <div className="bg-white dark:bg-neutral-800 border border-slate-200 dark:border-neutral-600 rounded-lg p-4">
                <h3 className="text-lg font-semibold mb-4">📈 Trends</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div>
                        <p className="text-sm font-medium mb-2">Security Score</p>
                        <div className="space-y-1">
                            {dashboard.trends.securityScore.series[0].data.slice(-7).map((point, index) => (
                                <div key={index} className="flex items-center gap-2">
                                    <span className="text-xs w-16 text-slate-600 dark:text-neutral-400">
                                        {new Date(point.timestamp).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                                    </span>
                                    <div className="flex-1 bg-slate-100 dark:bg-neutral-700 rounded-full h-4 relative">
                                        <div
                                            className="bg-green-500 h-full rounded-full flex items-center justify-end pr-1"
                                            style={{ width: `${point.value}%` }}
                                        >
                                            <span className="text-xs text-white font-semibold">{point.value.toFixed(0)}%</span>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                    <div>
                        <p className="text-sm font-medium mb-2">User Activity</p>
                        <div className="space-y-1">
                            {dashboard.trends.userActivity.series[0].data.slice(-7).map((point, index) => (
                                <div key={index} className="flex items-center gap-2">
                                    <span className="text-xs w-16 text-slate-600 dark:text-neutral-400">
                                        {new Date(point.timestamp).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                                    </span>
                                    <div className="flex-1 bg-slate-100 dark:bg-neutral-700 rounded-full h-4 relative">
                                        <div
                                            className="bg-blue-500 h-full rounded-full flex items-center justify-end pr-1"
                                            style={{ width: `${point.value}%` }}
                                        >
                                            <span className="text-xs text-white font-semibold">{point.value.toFixed(0)}</span>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
