/**
 * WebSocket Service for Real-Time AI Streaming
 *
 * Provides WebSocket-based real-time communication for AI operations:
 * - Streaming AI completions (token-by-token)
 * - Live prediction updates
 * - Real-time anomaly alerts
 * - Copilot chat streaming
 *
 * @module services/websocket/WebSocketService
 * @doc.type class
 * @doc.purpose Real-time AI streaming via WebSocket
 * @doc.layer platform
 * @doc.pattern Publisher-Subscriber
 */

import { EventEmitter } from 'events';

/**
 * WebSocket message types
 */
export enum WSMessageType {
    // Client → Server
    SUBSCRIBE = 'subscribe',
    UNSUBSCRIBE = 'unsubscribe',
    PING = 'ping',

    // Server → Client
    COPILOT_CHUNK = 'copilot_chunk',
    COPILOT_COMPLETE = 'copilot_complete',
    COPILOT_ERROR = 'copilot_error',

    ANOMALY_ALERT = 'anomaly_alert',
    PREDICTION_UPDATE = 'prediction_update',

    PONG = 'pong',
    ERROR = 'error',
    SUBSCRIBED = 'subscribed',
    UNSUBSCRIBED = 'unsubscribed',
}

/**
 * WebSocket message structure
 */
export interface WSMessage {
    type: WSMessageType;
    requestId?: string;
    data?: unknown;
    error?: {
        code: string;
        message: string;
    };
    timestamp: number;
}

/**
 * Subscription channel types
 */
export type ChannelType =
    | 'copilot'
    | 'anomaly'
    | 'prediction'
    | 'insights'
    | 'metrics';

/**
 * Subscription configuration
 */
export interface Subscription {
    userId: string;
    channel: ChannelType;
    filter?: Record<string, unknown>;
    connectionId: string;
}

/**
 * WebSocket connection metadata
 */
export interface WSConnection {
    id: string;
    userId: string;
    connectedAt: number;
    lastActivity: number;
    subscriptions: Set<string>; // channel IDs
}

/**
 * WebSocket Service class
 * Manages WebSocket connections and message routing
 */
export class WebSocketService extends EventEmitter {
    private connections = new Map<string, WSConnection>();
    private subscriptions = new Map<string, Set<string>>(); // channelId → Set<connectionId>
    private heartbeatInterval: NodeJS.Timeout | null = null;

    constructor() {
        super();
        this.startHeartbeat();
    }

    /**
     * Register a new WebSocket connection
     */
    registerConnection(connectionId: string, userId: string): void {
        this.connections.set(connectionId, {
            id: connectionId,
            userId,
            connectedAt: Date.now(),
            lastActivity: Date.now(),
            subscriptions: new Set(),
        });

        this.emit('connection', connectionId);
    }

    /**
     * Remove a WebSocket connection
     */
    removeConnection(connectionId: string): void {
        const connection = this.connections.get(connectionId);
        if (!connection) return;

        // Unsubscribe from all channels
        for (const channelId of connection.subscriptions) {
            this.unsubscribe(connectionId, channelId);
        }

        this.connections.delete(connectionId);
        this.emit('disconnection', connectionId);
    }

    /**
     * Subscribe a connection to a channel
     */
    subscribe(
        connectionId: string,
        channel: ChannelType,
        filter?: Record<string, unknown>
    ): string {
        const connection = this.connections.get(connectionId);
        if (!connection) {
            throw new Error(`Connection ${connectionId} not found`);
        }

        // Generate channel ID
        const channelId = this.getChannelId(channel, filter);

        // Add to subscriptions map
        if (!this.subscriptions.has(channelId)) {
            this.subscriptions.set(channelId, new Set());
        }
        this.subscriptions.get(channelId)!.add(connectionId);

        // Add to connection subscriptions
        connection.subscriptions.add(channelId);
        connection.lastActivity = Date.now();

        this.emit('subscribe', { connectionId, channelId, channel, filter });

        return channelId;
    }

    /**
     * Unsubscribe a connection from a channel
     */
    unsubscribe(connectionId: string, channelId: string): void {
        const connection = this.connections.get(connectionId);
        if (!connection) return;

        // Remove from subscriptions map
        const subscribers = this.subscriptions.get(channelId);
        if (subscribers) {
            subscribers.delete(connectionId);
            if (subscribers.size === 0) {
                this.subscriptions.delete(channelId);
            }
        }

        // Remove from connection subscriptions
        connection.subscriptions.delete(channelId);
        connection.lastActivity = Date.now();

        this.emit('unsubscribe', { connectionId, channelId });
    }

    /**
     * Publish a message to a specific channel
     */
    publishToChannel(
        channel: ChannelType,
        message: Omit<WSMessage, 'timestamp'>,
        filter?: Record<string, unknown>
    ): void {
        const channelId = this.getChannelId(channel, filter);
        const subscribers = this.subscriptions.get(channelId);

        if (!subscribers || subscribers.size === 0) {
            return; // No subscribers
        }

        const fullMessage: WSMessage = {
            ...message,
            timestamp: Date.now(),
        };

        for (const connectionId of subscribers) {
            this.sendToConnection(connectionId, fullMessage);
        }
    }

    /**
     * Send a message to a specific connection
     */
    sendToConnection(connectionId: string, message: WSMessage): void {
        const connection = this.connections.get(connectionId);
        if (connection) {
            connection.lastActivity = Date.now();
            this.emit('message', { connectionId, message });
        }
    }

    /**
     * Broadcast a message to all connections
     */
    broadcast(message: Omit<WSMessage, 'timestamp'>): void {
        const fullMessage: WSMessage = {
            ...message,
            timestamp: Date.now(),
        };

        for (const connectionId of this.connections.keys()) {
            this.sendToConnection(connectionId, fullMessage);
        }
    }

    /**
     * Get all connections for a user
     */
    getUserConnections(userId: string): WSConnection[] {
        return Array.from(this.connections.values()).filter(
            (conn) => conn.userId === userId
        );
    }

    /**
     * Get connection statistics
     */
    getStats(): {
        totalConnections: number;
        totalSubscriptions: number;
        connectionsByUser: Map<string, number>;
        subscriptionsByChannel: Map<string, number>;
    } {
        const connectionsByUser = new Map<string, number>();
        for (const conn of this.connections.values()) {
            connectionsByUser.set(
                conn.userId,
                (connectionsByUser.get(conn.userId) || 0) + 1
            );
        }

        const subscriptionsByChannel = new Map<string, number>();
        for (const [channelId, subscribers] of this.subscriptions.entries()) {
            subscriptionsByChannel.set(channelId, subscribers.size);
        }

        return {
            totalConnections: this.connections.size,
            totalSubscriptions: this.subscriptions.size,
            connectionsByUser,
            subscriptionsByChannel,
        };
    }

    /**
     * Generate channel ID from type and filter
     */
    private getChannelId(
        channel: ChannelType,
        filter?: Record<string, unknown>
    ): string {
        if (!filter || Object.keys(filter).length === 0) {
            return channel;
        }

        // Create deterministic channel ID from filter
        const filterStr = JSON.stringify(
            Object.keys(filter)
                .sort()
                .reduce((acc, key) => {
                    acc[key] = filter[key];
                    return acc;
                }, {} as Record<string, unknown>)
        );

        return `${channel}:${filterStr}`;
    }

    /**
     * Start heartbeat mechanism to detect dead connections
     */
    private startHeartbeat(): void {
        this.heartbeatInterval = setInterval(() => {
            const now = Date.now();
            const timeout = 60000; // 60 seconds

            for (const [connectionId, connection] of this.connections.entries()) {
                if (now - connection.lastActivity > timeout) {
                    // Connection is stale, remove it
                    this.removeConnection(connectionId);
                }
            }
        }, 30000); // Check every 30 seconds
    }

    /**
     * Stop the service and clean up
     */
    stop(): void {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
        }

        this.connections.clear();
        this.subscriptions.clear();
        this.removeAllListeners();
    }
}

/**
 * Default WebSocket service instance
 */
export const defaultWSService = new WebSocketService();

/**
 * AI-specific streaming helpers
 */
export class AIStreamingService {
    constructor(private wsService: WebSocketService) { }

    /**
     * Stream copilot response token by token
     */
    async streamCopilotResponse(
        userId: string,
        sessionId: string,
        responseGenerator: AsyncGenerator<string>
    ): Promise<void> {
        const requestId = `copilot-${sessionId}-${Date.now()}`;

        try {
            for await (const chunk of responseGenerator) {
                this.wsService.publishToChannel(
                    'copilot',
                    {
                        type: WSMessageType.COPILOT_CHUNK,
                        requestId,
                        data: { chunk, sessionId },
                    },
                    { sessionId }
                );
            }

            // Send completion message
            this.wsService.publishToChannel(
                'copilot',
                {
                    type: WSMessageType.COPILOT_COMPLETE,
                    requestId,
                    data: { sessionId },
                },
                { sessionId }
            );
        } catch (error) {
            this.wsService.publishToChannel(
                'copilot',
                {
                    type: WSMessageType.COPILOT_ERROR,
                    requestId,
                    error: {
                        code: 'STREAMING_ERROR',
                        message: error instanceof Error ? error.message : 'Unknown error',
                    },
                    data: { sessionId },
                },
                { sessionId }
            );
        }
    }

    /**
     * Broadcast anomaly alert
     */
    broadcastAnomaly(anomaly: {
        id: string;
        type: string;
        severity: string;
        title: string;
        description: string;
        affectedItems: string[];
    }): void {
        this.wsService.publishToChannel('anomaly', {
            type: WSMessageType.ANOMALY_ALERT,
            data: anomaly,
        });
    }

    /**
     * Send prediction update
     */
    sendPredictionUpdate(prediction: {
        targetId: string;
        targetType: string;
        probability: number;
        confidence: number;
    }): void {
        this.wsService.publishToChannel(
            'prediction',
            {
                type: WSMessageType.PREDICTION_UPDATE,
                data: prediction,
            },
            { targetId: prediction.targetId }
        );
    }
}

/**
 * Default AI streaming service instance
 */
export const defaultAIStreamingService = new AIStreamingService(defaultWSService);
