/**
 * React hooks for analytics features
 * 
 * Provides hooks for:
 * - Analytics dashboard data
 * - Permission usage metrics
 * - Role effectiveness metrics
 * - Security insights
 * - Anomaly detection
 * - Recommendations
 * - Trend analysis
 */

import { useState, useEffect, useCallback } from 'react';
import {
    AnalyticsPeriod,
    AnalyticsDashboard,
    PermissionUsageMetric,
    RoleEffectivenessMetric,
    SecurityInsight,
    AnomalyDetection,
    Recommendation,
    AnalyticsFilter,
    AnalyticsStats,
    TrendAnalysis,
} from '../types/analytics';
import { AnalyticsService } from '../services/analyticsService';

/**
 * Hook for analytics dashboard
 */
export function useAnalyticsDashboard(period: AnalyticsPeriod = AnalyticsPeriod.LAST_30_DAYS) {
    const [dashboard, setDashboard] = useState<AnalyticsDashboard | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const loadDashboard = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await AnalyticsService.getDashboard(period);
            setDashboard(result);
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to load analytics dashboard');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, [period]);

    const refresh = useCallback(() => {
        loadDashboard();
    }, [loadDashboard]);

    useEffect(() => {
        loadDashboard();
    }, [loadDashboard]);

    return {
        dashboard,
        loading,
        error,
        refresh,
    };
}

/**
 * Hook for permission usage analysis
 */
export function usePermissionUsage(permissionId: string | null, period: AnalyticsPeriod = AnalyticsPeriod.LAST_30_DAYS) {
    const [metric, setMetric] = useState<PermissionUsageMetric | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        if (!permissionId) {
            setMetric(null);
            return;
        }

        const analyze = async () => {
            setLoading(true);
            setError(null);
            try {
                const result = await AnalyticsService.analyzePermissionUsage(permissionId, period);
                setMetric(result);
            } catch (err) {
                const error = err instanceof Error ? err : new Error('Failed to analyze permission usage');
                setError(error);
            } finally {
                setLoading(false);
            }
        };

        analyze();
    }, [permissionId, period]);

    return {
        metric,
        loading,
        error,
    };
}

/**
 * Hook for role effectiveness analysis
 */
export function useRoleEffectiveness(roleId: string | null, period: AnalyticsPeriod = AnalyticsPeriod.LAST_30_DAYS) {
    const [metric, setMetric] = useState<RoleEffectivenessMetric | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        if (!roleId) {
            setMetric(null);
            return;
        }

        const analyze = async () => {
            setLoading(true);
            setError(null);
            try {
                const result = await AnalyticsService.analyzeRoleEffectiveness(roleId, period);
                setMetric(result);
            } catch (err) {
                const error = err instanceof Error ? err : new Error('Failed to analyze role effectiveness');
                setError(error);
            } finally {
                setLoading(false);
            }
        };

        analyze();
    }, [roleId, period]);

    return {
        metric,
        loading,
        error,
    };
}

/**
 * Hook for security insights
 */
export function useSecurityInsights(filter?: AnalyticsFilter) {
    const [insights, setInsights] = useState<SecurityInsight[]>([]);
    const [total, setTotal] = useState(0);
    const [hasMore, setHasMore] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const loadInsights = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const results = await AnalyticsService.generateInsights(filter);
            setInsights(results);
            setTotal(results.length);
            setHasMore(false);
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to load security insights');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, [filter]);

    const refresh = useCallback(() => {
        loadInsights();
    }, [loadInsights]);

    useEffect(() => {
        loadInsights();
    }, [loadInsights]);

    return {
        insights,
        total,
        hasMore,
        loading,
        error,
        refresh,
    };
}

/**
 * Hook for anomaly detection
 */
export function useAnomalyDetection(filter?: AnalyticsFilter) {
    const [anomalies, setAnomalies] = useState<AnomalyDetection[]>([]);
    const [total, setTotal] = useState(0);
    const [hasMore, setHasMore] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const detectAnomalies = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const results = await AnalyticsService.detectAnomalies(filter);
            setAnomalies(results);
            setTotal(results.length);
            setHasMore(false);
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to detect anomalies');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, [filter]);

    const refresh = useCallback(() => {
        detectAnomalies();
    }, [detectAnomalies]);

    useEffect(() => {
        detectAnomalies();
    }, [detectAnomalies]);

    return {
        anomalies,
        total,
        hasMore,
        loading,
        error,
        refresh,
    };
}

/**
 * Hook for recommendations
 */
export function useRecommendations(filter?: AnalyticsFilter) {
    const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
    const [total, setTotal] = useState(0);
    const [hasMore, setHasMore] = useState(false);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const generateRecommendations = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const results = await AnalyticsService.generateRecommendations(filter);
            setRecommendations(results);
            setTotal(results.length);
            setHasMore(false);
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to generate recommendations');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, [filter]);

    const refresh = useCallback(() => {
        generateRecommendations();
    }, [generateRecommendations]);

    useEffect(() => {
        generateRecommendations();
    }, [generateRecommendations]);

    return {
        recommendations,
        total,
        hasMore,
        loading,
        error,
        refresh,
    };
}

/**
 * Hook for trend analysis
 */
export function useTrendAnalysis(metric: string | null, period: AnalyticsPeriod = AnalyticsPeriod.LAST_30_DAYS) {
    const [trend, setTrend] = useState<TrendAnalysis | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    useEffect(() => {
        if (!metric) {
            setTrend(null);
            return;
        }

        const analyze = async () => {
            setLoading(true);
            setError(null);
            try {
                const result = await AnalyticsService.analyzeTrend(metric, period);
                setTrend(result);
            } catch (err) {
                const error = err instanceof Error ? err : new Error('Failed to analyze trend');
                setError(error);
            } finally {
                setLoading(false);
            }
        };

        analyze();
    }, [metric, period]);

    return {
        trend,
        loading,
        error,
    };
}

/**
 * Hook for analytics statistics
 */
export function useAnalyticsStats() {
    const [stats, setStats] = useState<AnalyticsStats | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const loadStats = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await AnalyticsService.getStats();
            setStats(result);
        } catch (err) {
            const error = err instanceof Error ? err : new Error('Failed to load analytics statistics');
            setError(error);
        } finally {
            setLoading(false);
        }
    }, []);

    const refresh = useCallback(() => {
        loadStats();
    }, [loadStats]);

    useEffect(() => {
        loadStats();
    }, [loadStats]);

    return {
        stats,
        loading,
        error,
        refresh,
    };
}

/**
 * Hook for combined analytics data (dashboard + insights + anomalies + recommendations)
 */
export function useFullAnalytics(period: AnalyticsPeriod = AnalyticsPeriod.LAST_30_DAYS) {
    const dashboard = useAnalyticsDashboard(period);
    const insights = useSecurityInsights({ period });
    const anomalies = useAnomalyDetection({ period });
    const recommendations = useRecommendations({ period });
    const stats = useAnalyticsStats();

    const loading = dashboard.loading || insights.loading || anomalies.loading || recommendations.loading || stats.loading;
    const error = dashboard.error || insights.error || anomalies.error || recommendations.error || stats.error;

    const refresh = useCallback(() => {
        dashboard.refresh();
        insights.refresh();
        anomalies.refresh();
        recommendations.refresh();
        stats.refresh();
    }, [dashboard, insights, anomalies, recommendations, stats]);

    return {
        dashboard: dashboard.dashboard,
        insights: insights.insights,
        anomalies: anomalies.anomalies,
        recommendations: recommendations.recommendations,
        stats: stats.stats,
        loading,
        error,
        refresh,
    };
}
