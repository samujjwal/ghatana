/**
 * Audit logging service for tracking role and permission changes
 * 
 * This service provides a centralized interface for logging audit events
 * with support for filtering, querying, and analytics.
 */

import { v4 as uuidv4 } from 'uuid';
import {
    AuditEvent,
    AuditAction,
    AuditResourceType,
    AuditSeverity,
    AuditMetadata,
    AuditChange,
    AuditFilter,
    AuditQueryResult,
    AuditStats,
    AuditAnalytics,
    AuditTimeSeriesPoint,
} from '@/types/audit';

/**
 * In-memory storage for audit events
 * In production, this would be replaced with a database
 */
class AuditEventStore {
    private events: AuditEvent[] = [];
    private maxEvents = 10000; // Limit to prevent memory issues

    add(event: AuditEvent): void {
        this.events.unshift(event); // Add to beginning for recent-first

        // Trim old events if exceeding max
        if (this.events.length > this.maxEvents) {
            this.events = this.events.slice(0, this.maxEvents);
        }
    }

    query(filter: AuditFilter): AuditQueryResult {
        let filtered = this.events;

        // Apply time range filter
        if (filter.startTime) {
            filtered = filtered.filter(e => e.timestamp >= filter.startTime!);
        }
        if (filter.endTime) {
            filtered = filtered.filter(e => e.timestamp <= filter.endTime!);
        }

        // Apply action filter
        if (filter.actions && filter.actions.length > 0) {
            filtered = filtered.filter(e => filter.actions!.includes(e.action));
        }

        // Apply resource type filter
        if (filter.resourceTypes && filter.resourceTypes.length > 0) {
            filtered = filtered.filter(e => filter.resourceTypes!.includes(e.resourceType));
        }

        // Apply resource ID filter
        if (filter.resourceIds && filter.resourceIds.length > 0) {
            filtered = filtered.filter(e => filter.resourceIds!.includes(e.resourceId));
        }

        // Apply user filter
        if (filter.userIds && filter.userIds.length > 0) {
            filtered = filtered.filter(e => filter.userIds!.includes(e.metadata.userId));
        }

        // Apply severity filter
        if (filter.severities && filter.severities.length > 0) {
            filtered = filtered.filter(e => filter.severities!.includes(e.severity));
        }

        // Apply search query
        if (filter.searchQuery) {
            const query = filter.searchQuery.toLowerCase();
            filtered = filtered.filter(e => {
                return (
                    e.action.toLowerCase().includes(query) ||
                    e.resourceId.toLowerCase().includes(query) ||
                    e.resourceName?.toLowerCase().includes(query) ||
                    e.metadata.userName?.toLowerCase().includes(query) ||
                    e.metadata.userEmail?.toLowerCase().includes(query) ||
                    e.metadata.reason?.toLowerCase().includes(query)
                );
            });
        }

        // Calculate total before pagination
        const total = filtered.length;

        // Apply pagination
        const offset = filter.offset || 0;
        const limit = filter.limit || 100;
        const paginated = filtered.slice(offset, offset + limit);

        return {
            events: paginated,
            total,
            hasMore: offset + paginated.length < total,
        };
    }

    getAll(): AuditEvent[] {
        return [...this.events];
    }

    clear(): void {
        this.events = [];
    }

    count(): number {
        return this.events.length;
    }
}

/**
 * Singleton audit event store
 */
const eventStore = new AuditEventStore();

/**
 * Audit logging service
 */
export class AuditService {
    /**
     * Log an audit event
     */
    static async logEvent(
        params:
            | {
                action: AuditAction | string;
                resourceType: AuditResourceType | string;
                resourceId: string;
                resourceName?: string;
                changes?: AuditChange[];
                severity?: AuditSeverity;
                metadata?: Partial<AuditMetadata>;
                success?: boolean;
                errorMessage?: string;
                parentEventId?: string;
            }
            | {
                userId: string;
                action: string;
                resource: string;
                resourceId?: string;
                resourceName?: string;
                metadata?: Record<string, unknown>;
                severity?: AuditSeverity;
                success?: boolean;
                errorMessage?: string;
                parentEventId?: string;
            }
    ): Promise<AuditEvent> {
        const normalized =
            'resourceType' in params
                ? {
                    action: params.action,
                    resourceType: params.resourceType,
                    resourceId: params.resourceId,
                    resourceName: params.resourceName,
                    changes: params.changes,
                    severity: params.severity,
                    metadata: params.metadata,
                    success: params.success,
                    errorMessage: params.errorMessage,
                    parentEventId: params.parentEventId,
                }
                : {
                    action: params.action,
                    resourceType: params.resource,
                    resourceId: params.resourceId ?? params.resource,
                    resourceName: params.resourceName,
                    changes: [],
                    severity: params.severity,
                    metadata: { userId: params.userId, ...params.metadata },
                    success: params.success,
                    errorMessage: params.errorMessage,
                    parentEventId: params.parentEventId,
                };

        const event: AuditEvent = {
            eventId: uuidv4(),
            timestamp: new Date().toISOString(),
            action: normalized.action as AuditAction,
            resourceType: normalized.resourceType as AuditResourceType,
            resourceId: normalized.resourceId,
            resourceName: normalized.resourceName,
            changes: normalized.changes || [],
            severity: normalized.severity || AuditSeverity.INFO,
            metadata: {
                userId: normalized.metadata?.userId || 'system',
                userName: normalized.metadata?.userName as string | undefined,
                userEmail: normalized.metadata?.userEmail as string | undefined,
                sessionId: normalized.metadata?.sessionId as string | undefined,
                ipAddress: normalized.metadata?.ipAddress as string | undefined,
                userAgent: normalized.metadata?.userAgent as string | undefined,
                requestId: normalized.metadata?.requestId as string | undefined,
                apiEndpoint: normalized.metadata?.apiEndpoint as string | undefined,
                reason: normalized.metadata?.reason as string | undefined,
                tags: normalized.metadata?.tags as string[] | undefined,
                ...(normalized.metadata || {}),
            },
            success: normalized.success ?? true,
            errorMessage: normalized.errorMessage,
            parentEventId: normalized.parentEventId,
        };

        eventStore.add(event);

        // In production, also send to backend API
        // await fetch('/api/audit/events', { method: 'POST', body: JSON.stringify(event) });

        return event;
    }

    /**
     * Compatibility alias used by compliance/analytics services.
     *
     * Returns events with a convenience top-level `userId` field.
     */
    static async queryAuditTrail(filter: {
        startTime?: string;
        endTime?: string;
        actions?: string[];
        limit?: number;
        offset?: number;
    } = {}): Promise<{ events: Array<AuditEvent & { userId: string }>; total: number; hasMore: boolean }> {
        const result = await this.queryEvents({
            startTime: filter.startTime,
            endTime: filter.endTime,
            actions: filter.actions as unknown as AuditAction[],
            limit: filter.limit,
            offset: filter.offset,
        });

        return {
            ...result,
            events: result.events.map((e) => ({
                ...e,
                userId: e.metadata.userId,
            })),
        };
    }

    /**
     * Query audit events with filtering
     */
    static async queryEvents(filter: AuditFilter = {}): Promise<AuditQueryResult> {
        // In production, this would query the backend API
        // const response = await fetch('/api/audit/events?' + new URLSearchParams(filter));
        // return response.json();

        return eventStore.query(filter);
    }

    /**
     * Get audit statistics
     */
    static async getStats(filter: Partial<AuditFilter> = {}): Promise<AuditStats> {
        const allEvents = eventStore.query({ ...filter, limit: 10000 }).events;

        // Count by action
        const eventsByAction: Record<AuditAction, number> = {} as any;
        for (const action of Object.values(AuditAction)) {
            eventsByAction[action] = 0;
        }
        allEvents.forEach(e => {
            eventsByAction[e.action] = (eventsByAction[e.action] || 0) + 1;
        });

        // Count by resource type
        const eventsByResourceType: Record<AuditResourceType, number> = {} as any;
        for (const type of Object.values(AuditResourceType)) {
            eventsByResourceType[type] = 0;
        }
        allEvents.forEach(e => {
            eventsByResourceType[e.resourceType] = (eventsByResourceType[e.resourceType] || 0) + 1;
        });

        // Count by severity
        const eventsBySeverity: Record<AuditSeverity, number> = {} as any;
        for (const severity of Object.values(AuditSeverity)) {
            eventsBySeverity[severity] = 0;
        }
        allEvents.forEach(e => {
            eventsBySeverity[e.severity] = (eventsBySeverity[e.severity] || 0) + 1;
        });

        // Top users
        const userCounts = new Map<string, { userId: string; userName?: string; count: number }>();
        allEvents.forEach(e => {
            const existing = userCounts.get(e.metadata.userId);
            if (existing) {
                existing.count++;
            } else {
                userCounts.set(e.metadata.userId, {
                    userId: e.metadata.userId,
                    userName: e.metadata.userName,
                    count: 1,
                });
            }
        });
        const topUsers = Array.from(userCounts.values())
            .sort((a, b) => b.count - a.count)
            .slice(0, 10);

        // Recent activity
        const recentActivity = allEvents.slice(0, 10);

        return {
            totalEvents: allEvents.length,
            eventsByAction,
            eventsByResourceType,
            eventsBySeverity,
            topUsers,
            recentActivity,
        };
    }

    /**
     * Get analytics data with time series
     */
    static async getAnalytics(params: {
        startTime: string;
        endTime: string;
        interval?: 'hour' | 'day' | 'week';
        groupBy?: 'action' | 'resourceType' | 'severity';
    }): Promise<AuditAnalytics> {
        const filter: AuditFilter = {
            startTime: params.startTime,
            endTime: params.endTime,
            limit: 10000,
        };

        const events = eventStore.query(filter).events;
        const stats = await this.getStats(filter);

        // Generate time series
        const interval = params.interval || 'day';
        const timeSeries = this.generateTimeSeries(events, params.startTime, params.endTime, interval, params.groupBy);

        return {
            timeSeries,
            stats,
            period: {
                start: params.startTime,
                end: params.endTime,
            },
        };
    }

    /**
     * Generate time series data from events
     */
    private static generateTimeSeries(
        events: AuditEvent[],
        startTime: string,
        endTime: string,
        interval: 'hour' | 'day' | 'week',
        groupBy?: 'action' | 'resourceType' | 'severity'
    ): AuditTimeSeriesPoint[] {
        const start = new Date(startTime);
        const end = new Date(endTime);
        const points: AuditTimeSeriesPoint[] = [];

        // Determine interval duration in ms
        const intervalMs = interval === 'hour' ? 3600000 : interval === 'day' ? 86400000 : 604800000;

        // Generate time buckets
        let current = new Date(start);
        while (current <= end) {
            const bucketStart = current.toISOString();
            const bucketEnd = new Date(current.getTime() + intervalMs).toISOString();

            // Filter events in this bucket
            const bucketEvents = events.filter(e => e.timestamp >= bucketStart && e.timestamp < bucketEnd);

            const point: AuditTimeSeriesPoint = {
                timestamp: bucketStart,
                count: bucketEvents.length,
            };

            // Add breakdown if requested
            if (groupBy && bucketEvents.length > 0) {
                point.breakdown = {};
                bucketEvents.forEach(e => {
                    const key = groupBy === 'action' ? e.action : groupBy === 'resourceType' ? e.resourceType : e.severity;
                    point.breakdown![key] = (point.breakdown![key] || 0) + 1;
                });
            }

            points.push(point);
            current = new Date(current.getTime() + intervalMs);
        }

        return points;
    }

    /**
     * Clear all audit events (for testing)
     */
    static clearAll(): void {
        eventStore.clear();
    }

    /**
     * Get total event count
     */
    static getEventCount(): number {
        return eventStore.count();
    }
}

// Export singleton instance for convenience
export const auditService = AuditService;
