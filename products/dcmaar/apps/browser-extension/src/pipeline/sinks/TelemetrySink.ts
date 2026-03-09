/**
 * @fileoverview Telemetry Sink
 *
 * Sink that batches and sends GuardianEvents to the backend events API.
 * Implements the Sprint 5 connector-based telemetry & error reporting design.
 *
 * @module pipeline/sinks/TelemetrySink
 */

import { BaseEventSink } from '@ghatana/dcmaar-browser-extension-core';

/**
 * Guardian event envelope (matches backend GuardianEvent contract).
 */
export interface GuardianEventPayload {
    schema_version: number;
    event_id: string;
    kind: 'usage' | 'block' | 'policy' | 'system' | 'alert';
    subtype: string;
    occurred_at: string;
    source: {
        agent_type: string;
        agent_version: string;
        device_id: string;
        child_id?: string;
        session_id?: string;
    };
    context?: Record<string, unknown>;
    payload?: Record<string, unknown>;
    privacy?: {
        pii_level: 'none' | 'low' | 'medium' | 'high';
        contains_raw_content: boolean;
    };
}

/**
 * Configuration for TelemetrySink.
 */
export interface TelemetrySinkConfig {
    /** Backend API base URL */
    apiBaseUrl: string;
    /** Device ID */
    deviceId: string;
    /** Child ID (optional) */
    childId?: string;
    /** Agent type identifier */
    agentType?: string;
    /** Agent version */
    agentVersion?: string;
    /** Auth token getter */
    getAuthToken: () => string | null;
    /** Batch size before auto-flush */
    batchSize?: number;
    /** Flush interval in milliseconds */
    flushIntervalMs?: number;
    /** Maximum buffer size */
    maxBufferSize?: number;
    /** Whether to buffer when offline */
    bufferWhenOffline?: boolean;
}

const DEFAULT_CONFIG: Omit<Required<TelemetrySinkConfig>, 'apiBaseUrl' | 'deviceId' | 'getAuthToken' | 'childId'> = {
    agentType: 'browser_extension',
    agentVersion: '1.0.0',
    batchSize: 10,
    flushIntervalMs: 30000,
    maxBufferSize: 100,
    bufferWhenOffline: true,
};

/**
 * TelemetrySink
 *
 * Batches GuardianEvents and sends them to the backend `/api/events` endpoint.
 * Handles offline buffering, automatic flushing, and error reporting.
 *
 * @example
 * ```typescript
 * const sink = new TelemetrySink({
 *   apiBaseUrl: 'https://api.guardian.example.com',
 *   deviceId: 'device-123',
 *   getAuthToken: () => localStorage.getItem('guardian_token'),
 * });
 *
 * await sink.initialize();
 *
 * // Send a usage event
 * await sink.sendEvent({
 *   kind: 'usage',
 *   subtype: 'app_session_started',
 *   context: { app_id: 'com.example.app' },
 * });
 *
 * // Send a command execution event
 * await sink.sendCommandEvent('command-123', 'processed');
 * ```
 */
export class TelemetrySink extends BaseEventSink<GuardianEventPayload> {
    readonly name = 'telemetry';

    private readonly config: Required<TelemetrySinkConfig>;
    private buffer: GuardianEventPayload[] = [];
    private flushInterval?: ReturnType<typeof setInterval>;
    private isOnline = true;

    constructor(config: TelemetrySinkConfig) {
        super();
        this.config = {
            ...DEFAULT_CONFIG,
            childId: config.childId ?? '',
            ...config,
        };
    }

    /**
     * Initialize the sink and start flush interval.
     */
    async initialize(): Promise<void> {
        // Start periodic flush
        this.flushInterval = setInterval(() => {
            void this.flush();
        }, this.config.flushIntervalMs);

        // Listen for online/offline events (browser)
        if (typeof window !== 'undefined') {
            window.addEventListener('online', () => {
                this.isOnline = true;
                void this.flush();
            });
            window.addEventListener('offline', () => {
                this.isOnline = false;
            });
            this.isOnline = navigator.onLine;
        }

        console.debug('[TelemetrySink] Initialized');
    }

    /**
     * Shutdown the sink and flush remaining events.
     */
    async shutdown(): Promise<void> {
        if (this.flushInterval) {
            clearInterval(this.flushInterval);
            this.flushInterval = undefined;
        }

        // Final flush
        await this.flush();

        console.debug('[TelemetrySink] Shutdown');
    }

    /**
     * Send a raw event (for pipeline compatibility).
     */
    async send(event: GuardianEventPayload): Promise<void> {
        this.buffer.push(event);
        this.stats.sent++;

        // Auto-flush if batch size reached
        if (this.buffer.length >= this.config.batchSize) {
            await this.flush();
        }
    }

    /**
     * Send batch of events (for pipeline compatibility).
     */
    async sendBatch(events: GuardianEventPayload[]): Promise<void> {
        for (const event of events) {
            await this.send(event);
        }
    }

    /**
     * Send a custom event with automatic envelope wrapping.
     */
    async sendEvent(partial: {
        kind: GuardianEventPayload['kind'];
        subtype: string;
        context?: Record<string, unknown>;
        payload?: Record<string, unknown>;
        piiLevel?: 'none' | 'low' | 'medium' | 'high';
    }): Promise<void> {
        const event: GuardianEventPayload = {
            schema_version: 1,
            event_id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
            kind: partial.kind,
            subtype: partial.subtype,
            occurred_at: new Date().toISOString(),
            source: {
                agent_type: this.config.agentType,
                agent_version: this.config.agentVersion,
                device_id: this.config.deviceId,
                child_id: this.config.childId || undefined,
            },
            context: partial.context,
            payload: partial.payload,
            privacy: {
                pii_level: partial.piiLevel ?? 'none',
                contains_raw_content: false,
            },
        };

        await this.send(event);
    }

    /**
     * Send a command execution event.
     */
    async sendCommandEvent(
        commandId: string,
        status: 'started' | 'completed' | 'failed',
        errorReason?: string
    ): Promise<void> {
        await this.sendEvent({
            kind: 'system',
            subtype: `command_execution_${status}`,
            context: {
                command_id: commandId,
            },
            payload: {
                status,
                error_reason: errorReason,
            },
        });
    }

    /**
     * Send an agent error event.
     */
    async sendErrorEvent(
        errorType: string,
        message: string,
        details?: Record<string, unknown>
    ): Promise<void> {
        await this.sendEvent({
            kind: 'system',
            subtype: 'agent_error',
            payload: {
                error_type: errorType,
                message,
                details,
            },
        });
    }

    /**
     * Flush buffered events to backend.
     */
    async flush(): Promise<void> {
        if (this.buffer.length === 0) {
            return;
        }

        if (!this.isOnline && this.config.bufferWhenOffline) {
            console.debug('[TelemetrySink] Offline, skipping flush');
            return;
        }

        const token = this.config.getAuthToken();
        if (!token) {
            console.warn('[TelemetrySink] No auth token, skipping flush');
            return;
        }

        // Take current buffer and clear
        const events = [...this.buffer];
        this.buffer = [];

        try {
            const url = `${this.config.apiBaseUrl}/api/events`;

            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ events }),
            });

            if (!response.ok) {
                throw new Error(`Events API returned ${response.status}`);
            }

            console.debug(`[TelemetrySink] Flushed ${events.length} events`);
        } catch (error) {
            console.error('[TelemetrySink] Flush error:', error);

            // Re-buffer events on failure (up to max)
            if (this.config.bufferWhenOffline) {
                const remaining = this.config.maxBufferSize - this.buffer.length;
                if (remaining > 0) {
                    this.buffer.unshift(...events.slice(0, remaining));
                }
            }
        }
    }

    /**
     * Get current buffer size.
     */
    getBufferSize(): number {
        return this.buffer.length;
    }
}
