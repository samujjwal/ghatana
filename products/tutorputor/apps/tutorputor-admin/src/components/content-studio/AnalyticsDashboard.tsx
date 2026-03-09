/**
 * Analytics Dashboard Component
 * 
 * Displays engagement metrics, drift signals, and recommended actions
 * for learning experiences. Supports analytics-driven regeneration.
 */

import { useState, useEffect } from 'react';
import { clsx } from 'clsx';

interface ExperienceAnalytics {
    experienceId: string;
    viewCount: number;
    completionCount: number;
    completionRate: number;
    avgTimeMinutes: number;
    dropOffRate: number;
    simulationStarts: number;
    simulationAborts: number;
    simulationErrors: number;
    hasEngagementDrift: boolean;
    hasQualityIssues: boolean;
    trends7d?: TrendData;
    driftSignals: DriftSignal[];
    recommendedActions: RecommendedAction[];
}

interface TrendData {
    dates: string[];
    views: number[];
    completions: number[];
}

interface DriftSignal {
    type: string;
    severity: 'low' | 'medium' | 'high';
    metric: string;
    currentValue: number;
    threshold: number;
    recommendation: string;
}

interface RecommendedAction {
    id: string;
    type: 'regenerate' | 'review' | 'update' | 'archive';
    priority: 'low' | 'medium' | 'high';
    description: string;
}

interface AnalyticsDashboardProps {
    experienceId: string;
    experienceTitle: string;
    onRegenerateRequested?: () => void;
    className?: string;
}

export function AnalyticsDashboard({
    experienceId,
    experienceTitle,
    onRegenerateRequested,
    className,
}: AnalyticsDashboardProps) {
    const [analytics, setAnalytics] = useState<ExperienceAnalytics | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchAnalytics = async () => {
            setIsLoading(true);
            setError(null);

            try {
                const response = await fetch(`/api/content-studio/experiences/${experienceId}/analytics`);
                if (!response.ok) {
                    throw new Error('Failed to fetch analytics');
                }
                const data = await response.json();
                setAnalytics(data);
            } catch (err) {
                setError(err instanceof Error ? err.message : 'Failed to load analytics');
            } finally {
                setIsLoading(false);
            }
        };

        fetchAnalytics();
    }, [experienceId]);

    if (isLoading) {
        return (
            <div className={clsx('rounded-lg border border-gray-200 bg-white p-8', className)}>
                <div className="flex items-center justify-center">
                    <svg className="h-8 w-8 animate-spin text-blue-600" fill="none" viewBox="0 0 24 24">
                        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
                    </svg>
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className={clsx('rounded-lg border border-red-200 bg-red-50 p-6', className)}>
                <p className="text-sm text-red-700">{error}</p>
            </div>
        );
    }

    if (!analytics) {
        return null;
    }

    return (
        <div className={clsx('space-y-6', className)}>
            {/* Header */}
            <div className="rounded-lg border border-gray-200 bg-white p-6">
                <div className="flex items-center justify-between">
                    <div>
                        <h3 className="text-lg font-semibold text-gray-900">Analytics Dashboard</h3>
                        <p className="text-sm text-gray-500">{experienceTitle}</p>
                    </div>
                    {(analytics.hasEngagementDrift || analytics.hasQualityIssues) && (
                        <div className="flex items-center gap-2">
                            {analytics.hasEngagementDrift && (
                                <span className="rounded-full bg-amber-100 px-2 py-1 text-xs font-medium text-amber-800">
                                    Engagement Drift
                                </span>
                            )}
                            {analytics.hasQualityIssues && (
                                <span className="rounded-full bg-red-100 px-2 py-1 text-xs font-medium text-red-800">
                                    Quality Issues
                                </span>
                            )}
                        </div>
                    )}
                </div>
            </div>

            {/* Key Metrics */}
            <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
                <MetricCard
                    label="Views"
                    value={analytics.viewCount.toLocaleString()}
                    trend={null}
                />
                <MetricCard
                    label="Completions"
                    value={analytics.completionCount.toLocaleString()}
                    trend={null}
                />
                <MetricCard
                    label="Completion Rate"
                    value={`${Math.round(analytics.completionRate * 100)}%`}
                    trend={analytics.completionRate >= 0.7 ? 'up' : analytics.completionRate < 0.5 ? 'down' : null}
                />
                <MetricCard
                    label="Avg. Time"
                    value={`${Math.round(analytics.avgTimeMinutes)} min`}
                    trend={null}
                />
            </div>

            {/* Simulation Metrics */}
            <div className="rounded-lg border border-gray-200 bg-white p-6">
                <h4 className="mb-4 text-sm font-medium text-gray-700">Simulation Performance</h4>
                <div className="grid grid-cols-3 gap-4">
                    <div className="text-center">
                        <div className="text-2xl font-bold text-gray-900">{analytics.simulationStarts}</div>
                        <div className="text-xs text-gray-500">Starts</div>
                    </div>
                    <div className="text-center">
                        <div className={clsx(
                            'text-2xl font-bold',
                            analytics.simulationAborts > analytics.simulationStarts * 0.3 ? 'text-amber-600' : 'text-gray-900'
                        )}>
                            {analytics.simulationAborts}
                        </div>
                        <div className="text-xs text-gray-500">Aborts</div>
                    </div>
                    <div className="text-center">
                        <div className={clsx(
                            'text-2xl font-bold',
                            analytics.simulationErrors > 0 ? 'text-red-600' : 'text-gray-900'
                        )}>
                            {analytics.simulationErrors}
                        </div>
                        <div className="text-xs text-gray-500">Errors</div>
                    </div>
                </div>
            </div>

            {/* Trends Chart (Simplified) */}
            {analytics.trends7d && (
                <div className="rounded-lg border border-gray-200 bg-white p-6">
                    <h4 className="mb-4 text-sm font-medium text-gray-700">7-Day Trend</h4>
                    <div className="flex h-32 items-end gap-2">
                        {analytics.trends7d.views.map((views, idx) => (
                            <div key={idx} className="flex flex-1 flex-col items-center gap-1">
                                <div
                                    className="w-full rounded-t bg-blue-500"
                                    style={{ height: `${(views / Math.max(...analytics.trends7d!.views)) * 100}%` }}
                                />
                                <span className="text-xs text-gray-500">{analytics.trends7d!.dates[idx]}</span>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Drift Signals */}
            {analytics.driftSignals.length > 0 && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-6">
                    <h4 className="mb-4 text-sm font-medium text-amber-800">Drift Signals Detected</h4>
                    <div className="space-y-3">
                        {analytics.driftSignals.map((signal, idx) => (
                            <div key={idx} className="rounded-md bg-white p-3">
                                <div className="flex items-center justify-between">
                                    <span className="font-medium text-gray-900">
                                        {signal.type.replace(/_/g, ' ')}
                                    </span>
                                    <span className={clsx(
                                        'rounded-full px-2 py-0.5 text-xs font-medium',
                                        signal.severity === 'high' && 'bg-red-100 text-red-800',
                                        signal.severity === 'medium' && 'bg-yellow-100 text-yellow-800',
                                        signal.severity === 'low' && 'bg-gray-100 text-gray-800'
                                    )}>
                                        {signal.severity}
                                    </span>
                                </div>
                                <p className="mt-1 text-sm text-gray-600">{signal.recommendation}</p>
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Recommended Actions */}
            {analytics.recommendedActions.length > 0 && (
                <div className="rounded-lg border border-gray-200 bg-white p-6">
                    <h4 className="mb-4 text-sm font-medium text-gray-700">Recommended Actions</h4>
                    <div className="space-y-2">
                        {analytics.recommendedActions.map((action) => (
                            <div key={action.id} className="flex items-center justify-between rounded-md bg-gray-50 p-3">
                                <div>
                                    <span className="font-medium text-gray-900">{action.description}</span>
                                </div>
                                {action.type === 'regenerate' && onRegenerateRequested && (
                                    <button
                                        onClick={onRegenerateRequested}
                                        className="rounded-md bg-blue-600 px-3 py-1 text-sm font-medium text-white hover:bg-blue-700"
                                    >
                                        Regenerate
                                    </button>
                                )}
                            </div>
                        ))}
                    </div>
                </div>
            )}

            {/* Regenerate Button */}
            {onRegenerateRequested && (analytics.hasEngagementDrift || analytics.hasQualityIssues) && (
                <button
                    onClick={onRegenerateRequested}
                    className="w-full rounded-md bg-blue-600 px-4 py-3 text-sm font-medium text-white hover:bg-blue-700"
                >
                    Trigger AI Regeneration
                </button>
            )}
        </div>
    );
}

interface MetricCardProps {
    label: string;
    value: string;
    trend: 'up' | 'down' | null;
}

function MetricCard({ label, value, trend }: MetricCardProps) {
    return (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
            <div className="text-xs font-medium text-gray-500">{label}</div>
            <div className="mt-1 flex items-center gap-2">
                <span className="text-2xl font-bold text-gray-900">{value}</span>
                {trend && (
                    <span className={clsx(
                        'text-sm',
                        trend === 'up' ? 'text-green-600' : 'text-red-600'
                    )}>
                        {trend === 'up' ? '↑' : '↓'}
                    </span>
                )}
            </div>
        </div>
    );
}
