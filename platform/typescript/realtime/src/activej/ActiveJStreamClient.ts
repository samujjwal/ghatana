/**
 * ActiveJ Stream Client - Bridges @ghatana/realtime with @ghatana/activej-bridge
 *
 * Provides a unified streaming interface that works seamlessly with ActiveJ
 * backend services while leveraging the production-grade WebSocket infrastructure
 * from @ghatana/realtime.
 *
 * @doc.type module
 * @doc.purpose Bridge realtime WebSocket with ActiveJ backend streaming
 * @doc.layer platform
 * @doc.pattern Adapter
 */

import { WebSocketClient, type WebSocketConfig, type WebSocketConnectionState } from '../client';

/**
 * Configuration for ActiveJ stream connections.
 */
export interface ActiveJStreamConfig {
    /**
     * Base URL of the ActiveJ service (e.g., 'ws://localhost:8080')
     */
    baseUrl: string;

    /**
     * Tenant ID for multi-tenant isolation
     */
    tenantId: string;

    /**
     * Authentication token
     */
    authToken?: string;

    /**
     * Stream endpoint path (e.g., '/events/stream')
     */
    endpoint: string;

    /**
     * Topics to subscribe to
     */
    topics?: string[];

    /**
     * Custom headers for WebSocket handshake
     */
    headers?: Record<string, string>;

    /**
     * Auto-reconnect on disconnect (default: true)
     */
    autoReconnect?: boolean;

    /**
     * Reconnect delay in ms (default: 1000)
     */
    reconnectDelay?: number;

    /**
     * Maximum reconnect attempts (default: 10)
     */
    maxReconnectAttempts?: number;

    /**
     * Heartbeat interval in ms (default: 30000)
     */
    heartbeatInterval?: number;
}

/**
 * Typed message from ActiveJ stream.
 */
export interface ActiveJStreamMessage<T = unknown> {
    /**
     * Message type (e.g., 'event', 'heartbeat', 'ack')
     */
    type: string;

    /**
     * Topic the message belongs to
     */
    topic?: string;

    /**
     * Message payload
     */
    payload: T;

    /**
     * Timestamp from server
     */
    timestamp: number;

    /**
     * Correlation ID for request tracking
     */
    correlationId?: string;
}

/**
 * Stream subscription handle for cleanup.
 */
export interface StreamSubscription {
    /**
     * Unsubscribe from the stream
     */
    unsubscribe: () => void;

    /**
     * Check if subscription is active
     */
    isActive: () => boolean;
}

/**
 * ActiveJ Stream Client - Unified streaming interface for ActiveJ backends.
 *
 * Wraps @ghatana/realtime WebSocketClient with ActiveJ-specific protocol
 * handling, topic-based subscriptions, and multi-tenant support.
 *
 * @example
 * ```typescript
 * const streamClient = new ActiveJStreamClient({
 *   baseUrl: 'ws://localhost:8080',
 *   tenantId: 'tenant-123',
 *   endpoint: '/events/stream',
 *   topics: ['pipeline-updates', 'metrics'],
 * });
 *
 * streamClient.subscribe<PipelineEvent>('pipeline-updates', (message) => {
 *   console.log('Pipeline event:', message.payload);
 * });
 *
 * await streamClient.connect();
 * ```
 */
export class ActiveJStreamClient {
    private client: WebSocketClient;
    private config: Required<ActiveJStreamConfig>;
    private subscriptions: Map<string, Set<(message: ActiveJStreamMessage) => void>> = new Map();
    private connected = false;
    private connectionPromise: Promise<void> | null = null;
    private unsubscribeStateChange: (() => void) | null = null;
    private unsubscribeMessages: (() => void) | null = null;

    constructor(config: ActiveJStreamConfig) {
        this.config = {
            baseUrl: config.baseUrl,
            tenantId: config.tenantId,
            authToken: config.authToken ?? '',
            endpoint: config.endpoint,
            topics: config.topics ?? [],
            headers: config.headers ?? {},
            autoReconnect: config.autoReconnect ?? true,
            reconnectDelay: config.reconnectDelay ?? 1000,
            maxReconnectAttempts: config.maxReconnectAttempts ?? 10,
            heartbeatInterval: config.heartbeatInterval ?? 30000,
        };

        // Build WebSocket URL with tenant and auth
        const wsUrl = this.buildWebSocketUrl();

        // Create underlying WebSocket client from @ghatana/realtime
        const wsConfig: WebSocketConfig = {
            url: wsUrl,
            reconnectDelay: this.config.reconnectDelay,
            maxReconnectAttempts: this.config.maxReconnectAttempts,
            heartbeatInterval: this.config.heartbeatInterval,
        };

        this.client = new WebSocketClient(wsConfig);
        this.setupMessageHandler();
    }

    /**
     * Build WebSocket URL with tenant ID and auth token.
     */
    private buildWebSocketUrl(): string {
        const url = new URL(this.config.endpoint, this.config.baseUrl);
        url.searchParams.set('tenantId', this.config.tenantId);

        if (this.config.authToken) {
            url.searchParams.set('token', this.config.authToken);
        }

        if (this.config.topics.length > 0) {
            url.searchParams.set('topics', this.config.topics.join(','));
        }

        return url.toString();
    }

    /**
     * Setup message handler for incoming ActiveJ messages.
     */
    private setupMessageHandler(): void {
        // Subscribe to all messages from the WebSocket client
        // The client.ts WebSocketClient uses subscribe(messageType, handler)
        // We use '*' or a special type for all ActiveJ messages
        this.unsubscribeMessages = this.client.subscribe('activej:message', (wsMessage) => {
            try {
                // The payload should be the ActiveJ message
                const message = wsMessage.payload as ActiveJStreamMessage;
                this.dispatchMessage(message);
            } catch (error) {
                console.error('[ActiveJStreamClient] Failed to process message:', error);
            }
        });

        // Listen for connection state changes
        this.unsubscribeStateChange = this.client.onStateChange((state: WebSocketConnectionState) => {
            const wasConnected = this.connected;
            this.connected = state.status === 'connected';

            if (this.connected && !wasConnected) {
                // Just connected - subscribe to topics
                this.sendTopicSubscriptions();
            }
        });
    }

    /**
     * Dispatch message to topic subscribers.
     */
    private dispatchMessage(message: ActiveJStreamMessage): void {
        const topic = message.topic ?? '*';

        // Dispatch to topic-specific subscribers
        const topicSubscribers = this.subscriptions.get(topic);
        if (topicSubscribers) {
            topicSubscribers.forEach((callback) => callback(message));
        }

        // Dispatch to wildcard subscribers
        const wildcardSubscribers = this.subscriptions.get('*');
        if (wildcardSubscribers) {
            wildcardSubscribers.forEach((callback) => callback(message));
        }
    }

    /**
     * Send topic subscription requests to server.
     */
    private sendTopicSubscriptions(): void {
        const topics = Array.from(this.subscriptions.keys()).filter((t) => t !== '*');
        if (topics.length > 0) {
            this.send({
                type: 'subscribe',
                topic: undefined,
                payload: { topics },
                timestamp: Date.now(),
            });
        }
    }

    /**
     * Connect to the ActiveJ stream server.
     */
    async connect(): Promise<void> {
        if (this.connected) {
            return;
        }

        if (this.connectionPromise) {
            return this.connectionPromise;
        }

        this.connectionPromise = this.client.connect();

        try {
            await this.connectionPromise;
            // Connection state change handler will set this.connected = true
        } finally {
            this.connectionPromise = null;
        }
    }

    /**
     * Disconnect from the ActiveJ stream server.
     */
    disconnect(): void {
        // Cleanup subscriptions
        if (this.unsubscribeMessages) {
            this.unsubscribeMessages();
            this.unsubscribeMessages = null;
        }
        if (this.unsubscribeStateChange) {
            this.unsubscribeStateChange();
            this.unsubscribeStateChange = null;
        }

        this.client.disconnect();
        this.connected = false;
        this.subscriptions.clear();
    }

    /**
     * Subscribe to a topic for streaming messages.
     *
     * @param topic - Topic name to subscribe to (use '*' for all messages)
     * @param callback - Callback function for incoming messages
     * @returns Subscription handle for cleanup
     */
    subscribe<T = unknown>(
        topic: string,
        callback: (message: ActiveJStreamMessage<T>) => void
    ): StreamSubscription {
        if (!this.subscriptions.has(topic)) {
            this.subscriptions.set(topic, new Set());
        }

        const callbacks = this.subscriptions.get(topic)!;
        callbacks.add(callback as (message: ActiveJStreamMessage) => void);

        // Send subscription request if already connected
        if (this.connected && topic !== '*') {
            this.send({
                type: 'subscribe',
                topic: undefined,
                payload: { topics: [topic] },
                timestamp: Date.now(),
            });
        }

        let active = true;

        return {
            unsubscribe: () => {
                if (active) {
                    callbacks.delete(callback as (message: ActiveJStreamMessage) => void);
                    active = false;

                    // Send unsubscribe request if connected
                    if (this.connected && topic !== '*') {
                        this.send({
                            type: 'unsubscribe',
                            topic: undefined,
                            payload: { topics: [topic] },
                            timestamp: Date.now(),
                        });
                    }
                }
            },
            isActive: () => active,
        };
    }

    /**
     * Send a message to the ActiveJ server.
     *
     * @param message - Message to send
     */
    send<T = unknown>(message: ActiveJStreamMessage<T>): void {
        if (!this.connected) {
            console.warn('[ActiveJStreamClient] Cannot send message: not connected');
            return;
        }

        // Wrap the ActiveJ message in the WebSocket message format
        this.client.send({
            type: message.type,
            payload: message,
        });
    }

    /**
     * Send a request and wait for a response.
     *
     * @param message - Request message
     * @param timeout - Timeout in milliseconds (default: 30000)
     * @returns Promise resolving to response message
     */
    async request<TRequest = unknown, TResponse = unknown>(
        message: Omit<ActiveJStreamMessage<TRequest>, 'correlationId' | 'timestamp'>,
        timeout = 30000
    ): Promise<ActiveJStreamMessage<TResponse>> {
        const correlationId = `req-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                subscription.unsubscribe();
                reject(new Error(`Request timeout after ${timeout}ms`));
            }, timeout);

            const subscription = this.subscribe<TResponse>('*', (response) => {
                if (response.correlationId === correlationId) {
                    clearTimeout(timer);
                    subscription.unsubscribe();
                    resolve(response);
                }
            });

            this.send({
                ...message,
                correlationId,
                timestamp: Date.now(),
            } as ActiveJStreamMessage<TRequest>);
        });
    }

    /**
     * Get current connection state.
     */
    get isConnected(): boolean {
        return this.connected;
    }

    /**
     * Get configured tenant ID.
     */
    get tenantId(): string {
        return this.config.tenantId;
    }
}

/**
 * Create an async iterable stream from an ActiveJ subscription.
 *
 * @param client - ActiveJStreamClient instance
 * @param topic - Topic to subscribe to
 * @returns AsyncIterable that yields messages until disconnection
 *
 * @example
 * ```typescript
 * const stream = createAsyncStream(client, 'events');
 * for await (const message of stream) {
 *   console.log('Event:', message.payload);
 * }
 * ```
 */
export async function* createAsyncStream<T = unknown>(
    client: ActiveJStreamClient,
    topic: string
): AsyncGenerator<ActiveJStreamMessage<T>, void, undefined> {
    const queue: ActiveJStreamMessage<T>[] = [];
    let resolveNext: ((value: IteratorResult<ActiveJStreamMessage<T>>) => void) | null = null;
    let done = false;

    const subscription = client.subscribe<T>(topic, (message) => {
        if (resolveNext) {
            resolveNext({ value: message, done: false });
            resolveNext = null;
        } else {
            queue.push(message);
        }
    });

    try {
        while (!done) {
            if (queue.length > 0) {
                yield queue.shift()!;
            } else if (!client.isConnected) {
                done = true;
            } else {
                yield await new Promise<ActiveJStreamMessage<T>>((resolve) => {
                    resolveNext = (result) => {
                        if (result.done) {
                            done = true;
                        } else {
                            resolve(result.value);
                        }
                    };
                });
            }
        }
    } finally {
        subscription.unsubscribe();
    }
}
