/**
 * React Hook for ActiveJ Streaming
 *
 * Provides a React-friendly interface for connecting to ActiveJ backend
 * streams with automatic lifecycle management, reconnection, and state tracking.
 *
 * @doc.type hook
 * @doc.purpose React integration for ActiveJ streaming
 * @doc.layer platform
 * @doc.pattern ReactHook
 */

import { useCallback, useEffect, useRef, useState } from 'react';
import {
    ActiveJStreamClient,
    type ActiveJStreamConfig,
    type ActiveJStreamMessage,
    type StreamSubscription,
} from './ActiveJStreamClient';

/**
 * Connection states for the ActiveJ stream.
 */
export type ActiveJConnectionState = 'disconnected' | 'connecting' | 'connected' | 'reconnecting' | 'error';

/**
 * Options for useActiveJStream hook.
 */
export interface UseActiveJStreamOptions extends Omit<ActiveJStreamConfig, 'baseUrl' | 'tenantId' | 'endpoint'> {
    /**
     * Whether to connect automatically on mount (default: true)
     */
    autoConnect?: boolean;

    /**
     * Callback when connection state changes
     */
    onStateChange?: (state: ActiveJConnectionState) => void;

    /**
     * Callback when an error occurs
     */
    onError?: (error: Error) => void;
}

/**
 * Return type for useActiveJStream hook.
 */
export interface UseActiveJStreamReturn<T = unknown> {
    /**
     * Current connection state
     */
    state: ActiveJConnectionState;

    /**
     * Whether currently connected
     */
    isConnected: boolean;

    /**
     * Last received message (if buffering is enabled)
     */
    lastMessage: ActiveJStreamMessage<T> | null;

    /**
     * All received messages (if buffering is enabled)
     */
    messages: ActiveJStreamMessage<T>[];

    /**
     * Connect to the stream
     */
    connect: () => Promise<void>;

    /**
     * Disconnect from the stream
     */
    disconnect: () => void;

    /**
     * Subscribe to a specific topic
     */
    subscribe: <M = T>(topic: string, callback: (message: ActiveJStreamMessage<M>) => void) => StreamSubscription;

    /**
     * Send a message to the server
     */
    send: <M = unknown>(message: ActiveJStreamMessage<M>) => void;

    /**
     * Send a request and wait for response
     */
    request: <TReq = unknown, TRes = unknown>(
        message: Omit<ActiveJStreamMessage<TReq>, 'correlationId' | 'timestamp'>,
        timeout?: number
    ) => Promise<ActiveJStreamMessage<TRes>>;

    /**
     * Any connection error that occurred
     */
    error: Error | null;

    /**
     * Clear all buffered messages
     */
    clearMessages: () => void;
}

/**
 * React hook for ActiveJ streaming connections.
 *
 * Manages WebSocket lifecycle, provides topic-based subscriptions, and
 * tracks connection state for UI feedback.
 *
 * @param baseUrl - ActiveJ server URL (e.g., 'ws://localhost:8080')
 * @param tenantId - Tenant ID for multi-tenant isolation
 * @param endpoint - Stream endpoint path (e.g., '/events/stream')
 * @param options - Additional configuration options
 * @returns Hook state and methods
 *
 * @example
 * ```tsx
 * function PipelineMonitor({ pipelineId }) {
 *   const { state, messages, subscribe } = useActiveJStream(
 *     'ws://localhost:8080',
 *     'tenant-123',
 *     '/events/stream',
 *     { topics: ['pipeline-events'] }
 *   );
 *
 *   useEffect(() => {
 *     const sub = subscribe<PipelineEvent>('pipeline-events', (msg) => {
 *       if (msg.payload.pipelineId === pipelineId) {
 *         // Handle event
 *       }
 *     });
 *     return () => sub.unsubscribe();
 *   }, [pipelineId, subscribe]);
 *
 *   if (state === 'connecting') return <LoadingSpinner />;
 *   if (state === 'error') return <ErrorMessage />;
 *
 *   return <EventList events={messages} />;
 * }
 * ```
 */
export function useActiveJStream<T = unknown>(
    baseUrl: string,
    tenantId: string,
    endpoint: string,
    options: UseActiveJStreamOptions = {}
): UseActiveJStreamReturn<T> {
    const {
        autoConnect = true,
        onStateChange,
        onError,
        authToken,
        topics,
        headers,
        autoReconnect = true,
        reconnectDelay = 1000,
        maxReconnectAttempts = 10,
        heartbeatInterval = 30000,
    } = options;

    const [state, setState] = useState<ActiveJConnectionState>('disconnected');
    const [lastMessage, setLastMessage] = useState<ActiveJStreamMessage<T> | null>(null);
    const [messages, setMessages] = useState<ActiveJStreamMessage<T>[]>([]);
    const [error, setError] = useState<Error | null>(null);

    const clientRef = useRef<ActiveJStreamClient | null>(null);
    const subscriptionsRef = useRef<StreamSubscription[]>([]);

    // Create client on mount
    useEffect(() => {
        const client = new ActiveJStreamClient({
            baseUrl,
            tenantId,
            endpoint,
            authToken,
            topics,
            headers,
            autoReconnect,
            reconnectDelay,
            maxReconnectAttempts,
            heartbeatInterval,
        });

        clientRef.current = client;

        // Subscribe to all messages to update state
        const wildcardSub = client.subscribe<T>('*', (message) => {
            setLastMessage(message);
            setMessages((prev) => [...prev, message]);
        });
        subscriptionsRef.current.push(wildcardSub);

        return () => {
            // Cleanup all subscriptions
            subscriptionsRef.current.forEach((sub) => sub.unsubscribe());
            subscriptionsRef.current = [];
            client.disconnect();
            clientRef.current = null;
        };
    }, [
        baseUrl,
        tenantId,
        endpoint,
        authToken,
        // Serialize topics for dependency check
        topics?.join(','),
        // Serialize headers for dependency check
        headers ? JSON.stringify(headers) : undefined,
        autoReconnect,
        reconnectDelay,
        maxReconnectAttempts,
        heartbeatInterval,
    ]);

    // Handle state changes
    useEffect(() => {
        onStateChange?.(state);
    }, [state, onStateChange]);

    // Auto-connect on mount
    useEffect(() => {
        if (autoConnect && clientRef.current) {
            connect();
        }
    }, [autoConnect]);

    const connect = useCallback(async () => {
        if (!clientRef.current) {
            return;
        }

        setState('connecting');
        setError(null);

        try {
            await clientRef.current.connect();
            setState('connected');
        } catch (err) {
            const error = err instanceof Error ? err : new Error(String(err));
            setError(error);
            setState('error');
            onError?.(error);
        }
    }, [onError]);

    const disconnect = useCallback(() => {
        if (clientRef.current) {
            clientRef.current.disconnect();
            setState('disconnected');
        }
    }, []);

    const subscribe = useCallback(
        <M = T>(topic: string, callback: (message: ActiveJStreamMessage<M>) => void): StreamSubscription => {
            if (!clientRef.current) {
                return {
                    unsubscribe: () => { },
                    isActive: () => false,
                };
            }

            const subscription = clientRef.current.subscribe<M>(topic, callback);
            subscriptionsRef.current.push(subscription);
            return subscription;
        },
        []
    );

    const send = useCallback(<M = unknown>(message: ActiveJStreamMessage<M>) => {
        if (clientRef.current) {
            clientRef.current.send(message);
        }
    }, []);

    const request = useCallback(
        async <TReq = unknown, TRes = unknown>(
            message: Omit<ActiveJStreamMessage<TReq>, 'correlationId' | 'timestamp'>,
            timeout?: number
        ): Promise<ActiveJStreamMessage<TRes>> => {
            if (!clientRef.current) {
                throw new Error('Not connected');
            }
            return clientRef.current.request<TReq, TRes>(message, timeout);
        },
        []
    );

    const clearMessages = useCallback(() => {
        setMessages([]);
        setLastMessage(null);
    }, []);

    return {
        state,
        isConnected: state === 'connected',
        lastMessage,
        messages,
        connect,
        disconnect,
        subscribe,
        send,
        request,
        error,
        clearMessages,
    };
}

/**
 * Hook for subscribing to a single topic with automatic cleanup.
 *
 * @param client - ActiveJStreamClient or useActiveJStream return value
 * @param topic - Topic to subscribe to
 * @param callback - Callback for messages
 * @param deps - Additional dependencies to trigger resubscription
 *
 * @example
 * ```tsx
 * const { subscribe } = useActiveJStream(...);
 *
 * useActiveJSubscription(
 *   subscribe,
 *   'pipeline-events',
 *   (msg) => handlePipelineEvent(msg),
 *   [handlePipelineEvent]
 * );
 * ```
 */
export function useActiveJSubscription<T = unknown>(
    subscribe: <M = T>(topic: string, callback: (message: ActiveJStreamMessage<M>) => void) => StreamSubscription,
    topic: string,
    callback: (message: ActiveJStreamMessage<T>) => void,
    deps: React.DependencyList = []
): void {
    useEffect(() => {
        const subscription = subscribe<T>(topic, callback);
        return () => subscription.unsubscribe();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [subscribe, topic, ...deps]);
}
