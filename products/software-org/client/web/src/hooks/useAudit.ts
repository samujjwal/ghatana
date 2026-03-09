/**
 * React hook for audit logging and querying
 * 
 * Provides a convenient interface for components to interact with
 * the audit service.
 */

import { useState, useEffect, useCallback } from 'react';
import { auditService } from '@/services/auditService';
import type {
    AuditEvent,
    AuditAction,
    AuditResourceType,
    AuditSeverity,
    AuditFilter,
    AuditQueryResult,
    AuditStats,
    AuditAnalytics,
    AuditChange,
    AuditMetadata,
} from '@/types/audit';

/**
 * Hook for logging audit events
 */
export function useAuditLog() {
    const logEvent = useCallback(async (params: {
        action: AuditAction;
        resourceType: AuditResourceType;
        resourceId: string;
        resourceName?: string;
        changes?: AuditChange[];
        severity?: AuditSeverity;
        metadata?: Partial<AuditMetadata>;
        success?: boolean;
        errorMessage?: string;
    }): Promise<AuditEvent> => {
        return auditService.logEvent(params);
    }, []);

    return { logEvent };
}

/**
 * Hook for querying audit events
 */
export function useAuditQuery(initialFilter: AuditFilter = {}) {
    const [result, setResult] = useState<AuditQueryResult>({
        events: [],
        total: 0,
        hasMore: false,
    });
    const [filter, setFilter] = useState<AuditFilter>(initialFilter);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const fetchEvents = useCallback(async (newFilter?: AuditFilter) => {
        setLoading(true);
        setError(null);

        try {
            const filterToUse = newFilter || filter;
            const queryResult = await auditService.queryEvents(filterToUse);
            setResult(queryResult);
            if (newFilter) {
                setFilter(newFilter);
            }
        } catch (err) {
            setError(err as Error);
        } finally {
            setLoading(false);
        }
    }, [filter]);

    const loadMore = useCallback(async () => {
        if (!result.hasMore || loading) return;

        const newFilter: AuditFilter = {
            ...filter,
            offset: (filter.offset || 0) + (filter.limit || 100),
        };

        setLoading(true);
        setError(null);

        try {
            const queryResult = await auditService.queryEvents(newFilter);
            setResult(prev => ({
                events: [...prev.events, ...queryResult.events],
                total: queryResult.total,
                hasMore: queryResult.hasMore,
            }));
            setFilter(newFilter);
        } catch (err) {
            setError(err as Error);
        } finally {
            setLoading(false);
        }
    }, [filter, result.hasMore, loading]);

    const refresh = useCallback(() => {
        fetchEvents({ ...filter, offset: 0 });
    }, [filter, fetchEvents]);

    useEffect(() => {
        fetchEvents();
    }, []); // Only run on mount

    return {
        events: result.events,
        total: result.total,
        hasMore: result.hasMore,
        loading,
        error,
        filter,
        setFilter: fetchEvents,
        loadMore,
        refresh,
    };
}

/**
 * Hook for audit statistics
 */
export function useAuditStats(filter: Partial<AuditFilter> = {}) {
    const [stats, setStats] = useState<AuditStats | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const fetchStats = useCallback(async () => {
        setLoading(true);
        setError(null);

        try {
            const result = await auditService.getStats(filter);
            setStats(result);
        } catch (err) {
            setError(err as Error);
        } finally {
            setLoading(false);
        }
    }, [filter]);

    useEffect(() => {
        fetchStats();
    }, [fetchStats]);

    return {
        stats,
        loading,
        error,
        refresh: fetchStats,
    };
}

/**
 * Hook for audit analytics
 */
export function useAuditAnalytics(params: {
    startTime: string;
    endTime: string;
    interval?: 'hour' | 'day' | 'week';
    groupBy?: 'action' | 'resourceType' | 'severity';
}) {
    const [analytics, setAnalytics] = useState<AuditAnalytics | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const fetchAnalytics = useCallback(async () => {
        setLoading(true);
        setError(null);

        try {
            const result = await auditService.getAnalytics(params);
            setAnalytics(result);
        } catch (err) {
            setError(err as Error);
        } finally {
            setLoading(false);
        }
    }, [params.startTime, params.endTime, params.interval, params.groupBy]);

    useEffect(() => {
        fetchAnalytics();
    }, [fetchAnalytics]);

    return {
        analytics,
        loading,
        error,
        refresh: fetchAnalytics,
    };
}

/**
 * Hook for real-time audit event subscription
 * In production, this would use WebSocket or Server-Sent Events
 */
export function useAuditSubscription(filter?: AuditFilter) {
    const [latestEvent, setLatestEvent] = useState<AuditEvent | null>(null);

    useEffect(() => {
        // Poll for new events every 5 seconds
        // In production, replace with WebSocket subscription
        const interval = setInterval(async () => {
            const result = await auditService.queryEvents({
                ...filter,
                limit: 1,
            });

            if (result.events.length > 0) {
                const event = result.events[0];
                setLatestEvent(prev => {
                    // Only update if it's a new event
                    if (!prev || event.eventId !== prev.eventId) {
                        return event;
                    }
                    return prev;
                });
            }
        }, 5000);

        return () => clearInterval(interval);
    }, [filter]);

    return { latestEvent };
}
