/**
 * @fileoverview Real-Time Sync Sink
 * 
 * Sink that streams events to parent dashboard via WebSocket.
 * Implements the Real-Time Synchronization feature from the enhancement proposal.
 * 
 * @module pipeline/sinks/RealTimeSyncSink
 */

import { BaseEventSink } from '@ghatana/dcmaar-browser-extension-core';
import type { PolicyEvaluatedEvent } from '../types';

/**
 * WebSocket message types
 */
export type SyncMessageType =
    | 'activity'      // Upstream: activity event
    | 'heartbeat'     // Bidirectional: presence check
    | 'policy_update' // Downstream: policy change from parent
    | 'mode_change'   // Downstream: focus mode toggle
    | 'ack';          // Acknowledgment

/**
 * WebSocket message structure
 */
export interface SyncMessage {
    type: SyncMessageType;
    deviceId: string;
    timestamp: number;
    payload?: unknown;
    messageId?: string;
}

/**
 * Configuration for RealTimeSyncSink
 */
export interface RealTimeSyncSinkConfig {
    /** WebSocket server URL */
    serverUrl: string;
    /** Device ID for identification */
    deviceId: string;
    /** Reconnect interval in ms */
    reconnectInterval?: number;
    /** Maximum reconnect attempts */
    maxReconnectAttempts?: number;
    /** Heartbeat interval in ms */
    heartbeatInterval?: number;
    /** Whether to buffer events when disconnected */
    bufferWhenDisconnected?: boolean;
    /** Maximum buffer size */
    maxBufferSize?: number;
    /** Event types to sync in real-time */
    syncEventTypes?: string[];
}

const DEFAULT_CONFIG: Omit<Required<RealTimeSyncSinkConfig>, 'serverUrl' | 'deviceId'> = {
    reconnectInterval: 5000,
    maxReconnectAttempts: 10,
    heartbeatInterval: 30000,
    bufferWhenDisconnected: true,
    maxBufferSize: 100,
    syncEventTypes: ['tab_activity', 'page_view', 'block_event'],
};

/**
 * Connection state
 */
export type ConnectionState = 'disconnected' | 'connecting' | 'connected' | 'error';

/**
 * Downstream message handler
 */
export type DownstreamHandler = (message: SyncMessage) => void;

/**
 * RealTimeSyncSink
 * 
 * Streams events to parent dashboard via WebSocket for real-time monitoring.
 * Also receives downstream messages for instant policy updates.
 * 
 * Features:
 * - Automatic reconnection with exponential backoff
 * - Heartbeat for presence tracking
 * - Event buffering when disconnected
 * - Downstream message handling for policy updates
 * 
 * @example
 * ```typescript
 * const sink = new RealTimeSyncSink({
 *   serverUrl: 'wss://guardian.example.com/ws',
 *   deviceId: 'device-123',
 * });
 * 
 * sink.onDownstream((message) => {
 *   if (message.type === 'policy_update') {
 *     // Handle policy update
 *   }
 * });
 * 
 * await sink.initialize();
 * await sink.send(event);
 * ```
 */
export class RealTimeSyncSink extends BaseEventSink<PolicyEvaluatedEvent> {
    readonly name = 'realtime-sync';

    private readonly config: Required<RealTimeSyncSinkConfig>;
    private socket: WebSocket | null = null;
    private connectionState: ConnectionState = 'disconnected';
    private reconnectAttempts = 0;
    private reconnectTimeout?: ReturnType<typeof setTimeout>;
    private heartbeatInterval?: ReturnType<typeof setInterval>;
    private eventBuffer: PolicyEvaluatedEvent[] = [];
    private downstreamHandlers: DownstreamHandler[] = [];
    private lastHeartbeatResponse?: number;

    constructor(config: RealTimeSyncSinkConfig) {
        super();
        this.config = {
            ...DEFAULT_CONFIG,
            ...config,
        };
    }

    /**
     * Initialize and connect
     */
    async initialize(): Promise<void> {
        if (!this.config.serverUrl) {
            console.warn('[RealTimeSyncSink] No server URL configured, skipping initialization');
            return;
        }

        await this.connect();
        console.debug('[RealTimeSyncSink] Initialized');
    }

    /**
     * Shutdown and disconnect
     */
    async shutdown(): Promise<void> {
        this.stopHeartbeat();

        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = undefined;
        }

        if (this.socket) {
            this.socket.close(1000, 'Shutdown');
            this.socket = null;
        }

        this.connectionState = 'disconnected';
        console.debug('[RealTimeSyncSink] Shutdown');
    }

    /**
     * Send an event
     */
    async send(event: PolicyEvaluatedEvent): Promise<void> {
        // Check if event type should be synced
        if (!this.config.syncEventTypes.includes(event.type)) {
            return;
        }

        if (this.connectionState === 'connected' && this.socket?.readyState === WebSocket.OPEN) {
            this.sendMessage({
                type: 'activity',
                deviceId: this.config.deviceId,
                timestamp: Date.now(),
                payload: this.sanitizeEvent(event),
            });
            this.stats.sent++;
        } else if (this.config.bufferWhenDisconnected) {
            // Buffer event for later
            this.eventBuffer.push(event);
            if (this.eventBuffer.length > this.config.maxBufferSize) {
                this.eventBuffer.shift(); // Remove oldest
            }
        }
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
     * Register downstream message handler
     */
    onDownstream(handler: DownstreamHandler): void {
        this.downstreamHandlers.push(handler);
    }

    /**
     * Remove downstream message handler
     */
    offDownstream(handler: DownstreamHandler): void {
        const index = this.downstreamHandlers.indexOf(handler);
        if (index !== -1) {
            this.downstreamHandlers.splice(index, 1);
        }
    }

    /**
     * Get current connection state
     */
    getConnectionState(): ConnectionState {
        return this.connectionState;
    }

    /**
     * Get last heartbeat response time
     */
    getLastHeartbeat(): number | undefined {
        return this.lastHeartbeatResponse;
    }

    /**
     * Connect to WebSocket server
     */
    private async connect(): Promise<void> {
        if (this.connectionState === 'connecting' || this.connectionState === 'connected') {
            return;
        }

        this.connectionState = 'connecting';

        try {
            this.socket = new WebSocket(this.config.serverUrl);

            this.socket.onopen = () => {
                console.debug('[RealTimeSyncSink] Connected');
                this.connectionState = 'connected';
                this.reconnectAttempts = 0;
                this.startHeartbeat();
                this.flushBuffer();
            };

            this.socket.onclose = (event) => {
                console.debug('[RealTimeSyncSink] Disconnected:', event.code, event.reason);
                this.connectionState = 'disconnected';
                this.stopHeartbeat();
                this.scheduleReconnect();
            };

            this.socket.onerror = (error) => {
                console.error('[RealTimeSyncSink] Error:', error);
                this.connectionState = 'error';
                this.stats.errors++;
            };

            this.socket.onmessage = (event) => {
                this.handleMessage(event.data);
            };
        } catch (error) {
            console.error('[RealTimeSyncSink] Connection error:', error);
            this.connectionState = 'error';
            this.scheduleReconnect();
        }
    }

    /**
     * Schedule reconnection with exponential backoff
     */
    private scheduleReconnect(): void {
        if (this.reconnectAttempts >= this.config.maxReconnectAttempts) {
            console.warn('[RealTimeSyncSink] Max reconnect attempts reached');
            return;
        }

        const delay = this.config.reconnectInterval * Math.pow(2, this.reconnectAttempts);
        this.reconnectAttempts++;

        console.debug('[RealTimeSyncSink] Reconnecting in', delay, 'ms (attempt', this.reconnectAttempts, ')');

        this.reconnectTimeout = setTimeout(() => {
            this.connect();
        }, delay);
    }

    /**
     * Start heartbeat interval
     */
    private startHeartbeat(): void {
        this.stopHeartbeat();

        this.heartbeatInterval = setInterval(() => {
            if (this.connectionState === 'connected') {
                this.sendMessage({
                    type: 'heartbeat',
                    deviceId: this.config.deviceId,
                    timestamp: Date.now(),
                });
            }
        }, this.config.heartbeatInterval);
    }

    /**
     * Stop heartbeat interval
     */
    private stopHeartbeat(): void {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = undefined;
        }
    }

    /**
     * Send message to server
     */
    private sendMessage(message: SyncMessage): void {
        if (this.socket?.readyState === WebSocket.OPEN) {
            this.socket.send(JSON.stringify(message));
        }
    }

    /**
     * Handle incoming message
     */
    private handleMessage(data: string): void {
        try {
            const message = JSON.parse(data) as SyncMessage;

            switch (message.type) {
                case 'heartbeat':
                    this.lastHeartbeatResponse = Date.now();
                    break;

                case 'policy_update':
                case 'mode_change':
                    // Notify downstream handlers
                    for (const handler of this.downstreamHandlers) {
                        try {
                            handler(message);
                        } catch (error) {
                            console.error('[RealTimeSyncSink] Downstream handler error:', error);
                        }
                    }
                    break;

                case 'ack':
                    // Acknowledgment received
                    break;

                default:
                    console.debug('[RealTimeSyncSink] Unknown message type:', message.type);
            }
        } catch (error) {
            console.error('[RealTimeSyncSink] Message parse error:', error);
        }
    }

    /**
     * Flush buffered events
     */
    private flushBuffer(): void {
        if (this.eventBuffer.length === 0) return;

        console.debug('[RealTimeSyncSink] Flushing', this.eventBuffer.length, 'buffered events');

        const events = [...this.eventBuffer];
        this.eventBuffer = [];

        for (const event of events) {
            this.send(event);
        }
    }

    /**
     * Sanitize event for transmission (remove sensitive data)
     */
    private sanitizeEvent(event: PolicyEvaluatedEvent): Record<string, unknown> {
        // Create a copy without potentially sensitive metadata
        const { metadata: _metadata, ...sanitized } = event;
        return sanitized as Record<string, unknown>;
    }

    /**
     * Force reconnect
     */
    forceReconnect(): void {
        if (this.socket) {
            this.socket.close();
        }
        this.reconnectAttempts = 0;
        this.connect();
    }

    /**
     * Get buffer size
     */
    getBufferSize(): number {
        return this.eventBuffer.length;
    }
}
