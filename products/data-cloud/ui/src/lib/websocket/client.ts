/**
 * WebSocket Client
 * 
 * Real-time WebSocket client for data-cloud UI.
 * Handles connection management, reconnection, and event subscriptions.
 * 
 * @doc.type service
 * @doc.purpose WebSocket client for real-time updates
 * @doc.layer frontend
 * @doc.pattern Observer Pattern
 */

/**
 * WebSocket event types
 */
export type WebSocketEventType =
    | 'collection.created'
    | 'collection.updated'
    | 'collection.deleted'
    | 'workflow.started'
    | 'workflow.completed'
    | 'workflow.failed'
    | 'workflow.progress'
    | 'alert.created'
    | 'alert.resolved'
    | 'data.quality.changed'
    | 'system.notification';

/**
 * WebSocket event payload
 */
export interface WebSocketEvent<T = unknown> {
    type: WebSocketEventType;
    payload: T;
    timestamp: string;
    correlationId?: string;
}

/**
 * WebSocket client configuration
 */
export interface WebSocketClientConfig {
    url: string;
    reconnect?: boolean;
    reconnectInterval?: number;
    maxReconnectAttempts?: number;
    heartbeatInterval?: number;
}

/**
 * Event handler type
 */
type EventHandler<T = unknown> = (event: WebSocketEvent<T>) => void;

/**
 * Connection state
 */
export type ConnectionState = 'connecting' | 'connected' | 'disconnected' | 'reconnecting';

/**
 * WebSocket Client class
 */
export class WebSocketClient {
    private ws: WebSocket | null = null;
    private config: Required<WebSocketClientConfig>;
    private handlers: Map<WebSocketEventType | '*', Set<EventHandler>> = new Map();
    private reconnectAttempts = 0;
    private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
    private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    private _state: ConnectionState = 'disconnected';
    private stateListeners: Set<(state: ConnectionState) => void> = new Set();

    constructor(config: WebSocketClientConfig) {
        this.config = {
            url: config.url,
            reconnect: config.reconnect ?? true,
            reconnectInterval: config.reconnectInterval ?? 3000,
            maxReconnectAttempts: config.maxReconnectAttempts ?? 10,
            heartbeatInterval: config.heartbeatInterval ?? 30000,
        };
    }

    /**
     * Get current connection state
     */
    get state(): ConnectionState {
        return this._state;
    }

    /**
     * Set connection state and notify listeners
     */
    private setState(state: ConnectionState): void {
        this._state = state;
        this.stateListeners.forEach((listener) => listener(state));
    }

    /**
     * Subscribe to connection state changes
     */
    onStateChange(listener: (state: ConnectionState) => void): () => void {
        this.stateListeners.add(listener);
        return () => this.stateListeners.delete(listener);
    }

    /**
     * Connect to WebSocket server
     */
    connect(): void {
        if (this.ws?.readyState === WebSocket.OPEN) {
            return;
        }

        this.setState('connecting');

        try {
            this.ws = new WebSocket(this.config.url);

            this.ws.onopen = () => {
                this.setState('connected');
                this.reconnectAttempts = 0;
                this.startHeartbeat();
                console.log('[WebSocket] Connected');
            };

            this.ws.onmessage = (event) => {
                try {
                    const data = JSON.parse(event.data) as WebSocketEvent;
                    this.handleEvent(data);
                } catch (error) {
                    console.error('[WebSocket] Failed to parse message:', error);
                }
            };

            this.ws.onclose = (event) => {
                this.setState('disconnected');
                this.stopHeartbeat();
                console.log('[WebSocket] Disconnected:', event.code, event.reason);

                if (this.config.reconnect && !event.wasClean) {
                    this.scheduleReconnect();
                }
            };

            this.ws.onerror = (error) => {
                console.error('[WebSocket] Error:', error);
            };
        } catch (error) {
            console.error('[WebSocket] Connection failed:', error);
            this.scheduleReconnect();
        }
    }

    /**
     * Disconnect from WebSocket server
     */
    disconnect(): void {
        this.stopHeartbeat();
        this.clearReconnectTimer();

        if (this.ws) {
            this.ws.close(1000, 'Client disconnect');
            this.ws = null;
        }

        this.setState('disconnected');
    }

    /**
     * Subscribe to specific event type
     */
    subscribe<T = unknown>(
        eventType: WebSocketEventType | '*',
        handler: EventHandler<T>
    ): () => void {
        if (!this.handlers.has(eventType)) {
            this.handlers.set(eventType, new Set());
        }

        this.handlers.get(eventType)!.add(handler as EventHandler);

        // Return unsubscribe function
        return () => {
            this.handlers.get(eventType)?.delete(handler as EventHandler);
        };
    }

    /**
     * Send message to server
     */
    send(message: Record<string, unknown>): void {
        if (this.ws?.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
        } else {
            console.warn('[WebSocket] Cannot send message: not connected');
        }
    }

    /**
     * Handle incoming event
     */
    private handleEvent(event: WebSocketEvent): void {
        // Call specific handlers
        const handlers = this.handlers.get(event.type);
        handlers?.forEach((handler) => handler(event));

        // Call wildcard handlers
        const wildcardHandlers = this.handlers.get('*');
        wildcardHandlers?.forEach((handler) => handler(event));
    }

    /**
     * Start heartbeat to keep connection alive
     */
    private startHeartbeat(): void {
        this.stopHeartbeat();
        this.heartbeatTimer = setInterval(() => {
            this.send({ type: 'ping' });
        }, this.config.heartbeatInterval);
    }

    /**
     * Stop heartbeat
     */
    private stopHeartbeat(): void {
        if (this.heartbeatTimer) {
            clearInterval(this.heartbeatTimer);
            this.heartbeatTimer = null;
        }
    }

    /**
     * Schedule reconnection attempt
     */
    private scheduleReconnect(): void {
        if (this.reconnectAttempts >= this.config.maxReconnectAttempts) {
            console.error('[WebSocket] Max reconnect attempts reached');
            return;
        }

        this.setState('reconnecting');
        this.reconnectAttempts++;

        const delay = this.config.reconnectInterval * Math.pow(1.5, this.reconnectAttempts - 1);
        console.log(`[WebSocket] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

        this.reconnectTimer = setTimeout(() => {
            this.connect();
        }, delay);
    }

    /**
     * Clear reconnect timer
     */
    private clearReconnectTimer(): void {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
    }
}

const defaultWsUrl = (() => {
    if (typeof window === 'undefined') {
        return 'ws://localhost:8080/ws';
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${window.location.host}/ws`;
})();

/**
 * Default WebSocket client instance
 */
export const wsClient = new WebSocketClient({
    url: import.meta.env.VITE_WS_URL ?? defaultWsUrl,
});

export default wsClient;
