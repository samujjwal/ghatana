/**
 * @fileoverview Local Storage Sink
 * 
 * Sink that persists events to browser local storage.
 * Aggregates usage data by domain and time period.
 * 
 * @module pipeline/sinks/LocalStorageSink
 */

import { BaseEventSink, BrowserStorageAdapter } from '@ghatana/dcmaar-browser-extension-core';
import type { PolicyEvaluatedEvent, DailyUsage, DomainUsage, TabActivityEvent, ContentScriptEvent } from '../types';

/**
 * Configuration for LocalStorageSink
 */
export interface LocalStorageSinkConfig {
    /** Storage key prefix */
    storageKeyPrefix?: string;
    /** Data retention in days */
    retentionDays?: number;
    /** Maximum events to keep in raw log */
    maxRawEvents?: number;
    /** Whether to aggregate by domain */
    aggregateByDomain?: boolean;
    /** Whether to track hourly activity */
    trackHourlyActivity?: boolean;
}

const DEFAULT_CONFIG: Required<LocalStorageSinkConfig> = {
    storageKeyPrefix: 'guardian',
    retentionDays: 7,
    maxRawEvents: 1000,
    aggregateByDomain: true,
    trackHourlyActivity: true,
};

/**
 * LocalStorageSink
 * 
 * Persists events to browser storage with aggregation.
 * Maintains:
 * - Raw event log (capped)
 * - Daily usage aggregates
 * - Domain-level statistics
 * 
 * @example
 * ```typescript
 * const sink = new LocalStorageSink({ retentionDays: 14 });
 * await sink.initialize();
 * await sink.send(policyEvent);
 * ```
 */
export class LocalStorageSink extends BaseEventSink<PolicyEvaluatedEvent> {
    readonly name = 'local-storage';

    private readonly config: Required<LocalStorageSinkConfig>;
    private readonly storage: BrowserStorageAdapter;
    private eventBuffer: PolicyEvaluatedEvent[] = [];
    private flushTimeout?: ReturnType<typeof setTimeout>;

    constructor(config: LocalStorageSinkConfig = {}) {
        super();
        this.config = { ...DEFAULT_CONFIG, ...config };
        this.storage = new BrowserStorageAdapter();
    }

    /**
     * Initialize the sink
     */
    async initialize(): Promise<void> {
        // Cleanup old data on init
        await this.cleanupOldData();
        console.debug('[LocalStorageSink] Initialized');
    }

    /**
     * Shutdown the sink
     */
    async shutdown(): Promise<void> {
        // Flush any pending events
        if (this.flushTimeout) {
            clearTimeout(this.flushTimeout);
        }
        await this.flush();
        console.debug('[LocalStorageSink] Shutdown');
    }

    /**
     * Send an event to storage
     */
    async send(event: PolicyEvaluatedEvent): Promise<void> {
        // Buffer events for batch processing
        this.eventBuffer.push(event);

        // Schedule flush if not already scheduled
        if (!this.flushTimeout) {
            this.flushTimeout = setTimeout(() => this.flush(), 1000);
        }
    }

    /**
     * Flush buffered events to storage
     */
    async flush(): Promise<void> {
        if (this.eventBuffer.length === 0) return;

        const events = [...this.eventBuffer];
        this.eventBuffer = [];
        this.flushTimeout = undefined;

        try {
            // Store raw events
            await this.storeRawEvents(events);

            // Aggregate by domain if enabled
            if (this.config.aggregateByDomain) {
                await this.aggregateEvents(events);
            }

            this.stats.sent += events.length;
        } catch (error) {
            console.error('[LocalStorageSink] Flush error:', error);
            this.stats.errors++;
            // Re-add events to buffer for retry
            this.eventBuffer.unshift(...events);
        }
    }

    /**
     * Store raw events with capping
     */
    private async storeRawEvents(events: PolicyEvaluatedEvent[]): Promise<void> {
        const key = `${this.config.storageKeyPrefix}:events`;
        const existing = await this.storage.get<PolicyEvaluatedEvent[]>(key) || [];

        const combined = [...existing, ...events];
        const capped = combined.slice(-this.config.maxRawEvents);

        await this.storage.set(key, capped);
    }

    /**
     * Aggregate events into daily usage
     */
    private async aggregateEvents(events: PolicyEvaluatedEvent[]): Promise<void> {
        const today = this.getDateKey(new Date());
        const key = `${this.config.storageKeyPrefix}:daily:${today}`;

        // Load existing daily data
        let daily = await this.storage.get<DailyUsage>(key);
        if (!daily) {
            daily = this.createEmptyDailyUsage(today);
        }

        // Process each event
        for (const event of events) {
            const domain = this.extractDomain(event);
            const duration = this.extractDuration(event);
            const category = event.category || 'other';
            const hour = new Date(event.timestamp).getHours();

            // Update total time
            daily.totalTime += duration;

            // Update category time
            daily.categories[category] = (daily.categories[category] || 0) + duration;

            // Update domain usage
            if (domain) {
                if (!daily.domains[domain]) {
                    daily.domains[domain] = {
                        time: 0,
                        visits: 0,
                        category,
                        lastVisit: event.timestamp,
                    };
                }
                daily.domains[domain].time += duration;
                daily.domains[domain].visits += 1;
                daily.domains[domain].lastVisit = event.timestamp;
            }

            // Update hourly activity
            if (this.config.trackHourlyActivity) {
                daily.hourlyActivity[hour] += duration;
            }
        }

        await this.storage.set(key, daily);
    }

    /**
     * Create empty daily usage structure
     */
    private createEmptyDailyUsage(date: string): DailyUsage {
        return {
            date,
            totalTime: 0,
            categories: {},
            domains: {},
            hourlyActivity: new Array(24).fill(0),
        };
    }

    /**
 * Cleanup data older than retention period
 */
    private async cleanupOldData(): Promise<void> {
        const cutoffDate = new Date();
        cutoffDate.setDate(cutoffDate.getDate() - this.config.retentionDays);
        const cutoffKey = this.getDateKey(cutoffDate);

        // Get all storage keys from browser local storage to remove old daily usage
        const chromeApi = typeof chrome !== 'undefined' ? chrome : undefined;
        if (!chromeApi?.storage?.local) return;

        try {
            const allData = await chromeApi.storage.local.get(null);
            const keysToRemove: string[] = [];

            for (const key of Object.keys(allData)) {
                if (key.startsWith(`${this.config.storageKeyPrefix}:daily:`)) {
                    const dateKey = key.replace(
                        `${this.config.storageKeyPrefix}:daily:`,
                        '',
                    );
                    if (dateKey < cutoffKey) {
                        keysToRemove.push(key);
                    }
                }
            }

            if (keysToRemove.length > 0) {
                await chromeApi.storage.local.remove(keysToRemove);
                console.debug(
                    '[LocalStorageSink] Cleaned up',
                    keysToRemove.length,
                    'old daily records',
                );
            }

            // Also cleanup old raw policy events
            const eventsKey = `${this.config.storageKeyPrefix}:events`;
            const events =
                (await this.storage.get<PolicyEvaluatedEvent[]>(eventsKey)) || [];
            const cutoffTimestamp = cutoffDate.getTime();
            const filtered = events.filter((e) => e.timestamp > cutoffTimestamp);

            if (filtered.length < events.length) {
                await this.storage.set(eventsKey, filtered);
                console.debug(
                    '[LocalStorageSink] Cleaned up',
                    events.length - filtered.length,
                    'old events',
                );
            }
        } catch (error) {
            console.warn('[LocalStorageSink] Cleanup error:', error);
        }
    }

    /**
     * Get date key in YYYY-MM-DD format
     */
    private getDateKey(date: Date): string {
        return date.toISOString().split('T')[0];
    }

    /**
     * Extract domain from event
     */
    private extractDomain(event: PolicyEvaluatedEvent): string | null {
        const e = event as unknown as Record<string, unknown>;
        if ('domain' in e && typeof e.domain === 'string') {
            return e.domain;
        }
        return null;
    }

    /**
     * Extract duration from event
     */
    private extractDuration(event: PolicyEvaluatedEvent): number {
        const e = event as unknown as Record<string, unknown>;
        if ('duration' in e && typeof e.duration === 'number') {
            return e.duration;
        }
        return 0;
    }

    /**
     * Get daily usage for a specific date
     */
    async getDailyUsage(date: Date): Promise<DailyUsage | null> {
        const key = `${this.config.storageKeyPrefix}:daily:${this.getDateKey(date)}`;
        const result = await this.storage.get<DailyUsage>(key);
        return result ?? null;
    }

    /**
     * Get usage for date range
     */
    async getUsageRange(startDate: Date, endDate: Date): Promise<DailyUsage[]> {
        const results: DailyUsage[] = [];
        const current = new Date(startDate);

        while (current <= endDate) {
            const usage = await this.getDailyUsage(current);
            if (usage) {
                results.push(usage);
            }
            current.setDate(current.getDate() + 1);
        }

        return results;
    }

    /**
     * Get raw events
     */
    async getRawEvents(limit?: number): Promise<PolicyEvaluatedEvent[]> {
        const key = `${this.config.storageKeyPrefix}:events`;
        const events = await this.storage.get<PolicyEvaluatedEvent[]>(key) || [];
        return limit ? events.slice(-limit) : events;
    }

    /**
     * Send batch of events
     */
    async sendBatch(events: PolicyEvaluatedEvent[]): Promise<void> {
        for (const event of events) {
            await this.send(event);
        }
    }

    /**
     * Clear all stored data
     */
    async clearAll(): Promise<void> {
        const chromeApi = typeof chrome !== 'undefined' ? chrome : undefined;
        if (!chromeApi?.storage?.local) return;

        const allData = await chromeApi.storage.local.get(null);
        const keysToRemove = Object.keys(allData).filter((key) =>
            key.startsWith(this.config.storageKeyPrefix)
        );

        if (keysToRemove.length > 0) {
            await chromeApi.storage.local.remove(keysToRemove);
        }
    }
}
