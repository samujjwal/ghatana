/**
 * Software-Org WebSocket Hook using @ghatana/realtime
 *
 * @doc.type hook
 * @doc.purpose Real-time WebSocket communication using platform library
 * @doc.layer product
 * @doc.pattern Custom Hook
 *
 * Purpose:
 * Provides React hook for WebSocket connections using the platform-standard
 * @ghatana/realtime library. Replaces custom WebSocket implementation with
 * production-grade client featuring automatic reconnection, state management,
 * and type-safe messaging.
 *
 * Features:
 * - Automatic reconnection with exponential backoff
 * - Connection state tracking
 * - Message queueing during disconnections
 * - Type-safe message sending/receiving
 * - Heartbeat monitoring
 * - React lifecycle integration
 *
 * Architecture:
 * Frontend → @ghatana/realtime → WebSocket Server
 */

import { useEffect, useState, useCallback, useRef } from 'react';
import { WebSocketClient, WebSocketMessage, WebSocketConnectionState } from '@ghatana/realtime';

/**
 * WebSocket hook options
 */
export interface UseRealtimeWebSocketOptions<T = any> {
    /** WebSocket URL to connect to */
    url: string;
    /** Optional message type to filter */
    messageType?: string;
    /** Optional callback when message received */
    onMessage?: (message: WebSocketMessage<T>) => void;
    /** Maximum reconnection attempts (default: 5) */
    maxReconnectAttempts?: number;
    /** Reconnection delay in ms (default: 1000) */
    reconnectDelay?: number;
    /** Heartbeat interval in ms (default: 30000) */
    heartbeatInterval?: number;
    /** Connection timeout in ms (default: 10000) */
    connectionTimeout?: number;
    /** Auto-connect on mount (default: true) */
    autoConnect?: boolean;
}

/**
 * WebSocket hook return value
 */
export interface UseRealtimeWebSocketReturn<T = any> {
    /** Current data */
    data: T | null;
    /** Connection state */
    connectionState: WebSocketConnectionState;
    /** Is connected */
    isConnected: boolean;
    /** Is connecting */
    isConnecting: boolean;
    /** Is reconnecting */
    isReconnecting: boolean;
    /** Last error */
    error: Error | null;
    /** Last update timestamp */
    lastUpdate: Date | null;
    /** Send message */
    send: (message: WebSocketMessage<any>) => boolean;
    /** Connect manually */
    connect: () => Promise<void>;
    /** Disconnect manually */
    disconnect: () => void;
}

/**
 * Hook for WebSocket real-time data subscriptions using @ghatana/realtime.
 *
 * <p><b>Purpose</b><br>
 * Manages WebSocket connection lifecycle using the platform-standard client.
 * Provides automatic reconnection, state tracking, and type-safe messaging.
 *
 * <p><b>Features</b><br>
 * - Automatic reconnection with exponential backoff
 * - Connection state tracking
 * - Message queueing during disconnections
 * - Type-safe message sending/receiving
 * - Heartbeat monitoring
 * - React lifecycle integration
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { data, isConnected, send } = useRealtimeWebSocket({
 *   url: 'ws://api.example.com/stream',
 *   messageType: 'action-update',
 *   onMessage: (msg) => console.log('Received:', msg)
 * });
 *
 * if (!isConnected) return <div>Connecting...</div>;
 * return <div>Data: {JSON.stringify(data)}</div>;
 * }</pre>
 *
 * @param options - WebSocket configuration options
 * @returns WebSocket state and control functions
 *
 * @doc.type hook
 * @doc.purpose WebSocket real-time subscription with @ghatana/realtime
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useRealtimeWebSocket<T = any>(
    options: UseRealtimeWebSocketOptions<T>
): UseRealtimeWebSocketReturn<T> {
    const {
        url,
        messageType,
        onMessage,
        maxReconnectAttempts = 5,
        reconnectDelay = 1000,
        heartbeatInterval = 30000,
        connectionTimeout = 10000,
        autoConnect = true,
    } = options;

    // State
    const [data, setData] = useState<T | null>(null);
    const [connectionState, setConnectionState] = useState<WebSocketConnectionState>({
        status: 'disconnected',
        reconnectAttempt: 0,
    });
    const [lastUpdate, setLastUpdate] = useState<Date | null>(null);

    // Refs
    const clientRef = useRef<WebSocketClient | null>(null);
    const unsubscribeRef = useRef<(() => void) | null>(null);
    const stateUnsubscribeRef = useRef<(() => void) | null>(null);
    const onMessageRef = useRef(onMessage);
    const isMountedRef = useRef(true);

    // Keep onMessage ref updated without triggering reconnection
    useEffect(() => {
        onMessageRef.current = onMessage;
    }, [onMessage]);

    // Track mounted state
    useEffect(() => {
        isMountedRef.current = true;
        return () => {
            isMountedRef.current = false;
        };
    }, []);

    // Initialize client - stable dependencies only
    useEffect(() => {
        // Prevent reconnection if already connected with same URL
        if (clientRef.current && url === clientRef.current['url']) {
            return;
        }

        // Cleanup existing connection
        if (clientRef.current) {
            unsubscribeRef.current?.();
            stateUnsubscribeRef.current?.();
            clientRef.current.disconnect();
        }

        const client = new WebSocketClient({
            url,
            maxReconnectAttempts,
            reconnectDelay,
            heartbeatInterval,
            connectionTimeout,
        });

        // Store URL for comparison
        (client as any)['url'] = url;
        clientRef.current = client;

        // Subscribe to connection state changes
        stateUnsubscribeRef.current = client.onStateChange((state) => {
            if (isMountedRef.current) {
                setConnectionState(state);
            }
        });

        // Subscribe to messages
        if (messageType) {
            unsubscribeRef.current = client.subscribe<T>(messageType, (message) => {
                if (isMountedRef.current) {
                    setData(message.payload);
                    setLastUpdate(new Date());
                    onMessageRef.current?.(message);
                }
            });
        }

        // Auto-connect if enabled
        if (autoConnect) {
            client.connect().catch((error) => {
                if (isMountedRef.current) {
                    console.error('[useRealtimeWebSocket] Connection failed:', error);
                }
            });
        }

        // Cleanup
        return () => {
            unsubscribeRef.current?.();
            stateUnsubscribeRef.current?.();
            client.disconnect();
        };
    }, [url, messageType, maxReconnectAttempts, reconnectDelay, heartbeatInterval, connectionTimeout, autoConnect]);

    // Send message
    const send = useCallback((message: WebSocketMessage<any>): boolean => {
        if (!clientRef.current) {
            console.warn('[useRealtimeWebSocket] Client not initialized');
            return false;
        }
        return clientRef.current.send(message);
    }, []);

    // Connect manually
    const connect = useCallback(async (): Promise<void> => {
        if (!clientRef.current) {
            throw new Error('Client not initialized');
        }
        return clientRef.current.connect();
    }, []);

    // Disconnect manually
    const disconnect = useCallback((): void => {
        if (!clientRef.current) {
            return;
        }
        clientRef.current.disconnect();
    }, []);

    // Derived state
    const isConnected = connectionState.status === 'connected';
    const isConnecting = connectionState.status === 'connecting';
    const isReconnecting = connectionState.status === 'reconnecting';
    const error = connectionState.lastError || null;

    return {
        data,
        connectionState,
        isConnected,
        isConnecting,
        isReconnecting,
        error,
        lastUpdate,
        send,
        connect,
        disconnect,
    };
}

/**
 * Hook for HITL Console real-time action updates using @ghatana/realtime.
 *
 * <p><b>Purpose</b><br>
 * Specialized hook for HITL action stream with automatic reconnection
 * and type-safe action handling.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { actions, isConnected, error } = useHitlRealtimeWebSocket();
 *
 * if (!isConnected) return <div>Connecting to HITL stream...</div>;
 * if (error) return <div>Error: {error.message}</div>;
 * return <ActionList actions={actions} />;
 * }</pre>
 *
 * @returns HITL actions, connection state, and error info
 *
 * @doc.type hook
 * @doc.purpose HITL real-time action stream with @ghatana/realtime
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useHitlRealtimeWebSocket() {
    const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/api/v1/hitl/stream';

    const { data, isConnected, error } = useRealtimeWebSocket<any[]>({
        url: wsUrl,
        messageType: 'action-update',
    });

    return {
        actions: data || [],
        isConnected,
        error,
    };
}

/**
 * Hook for department events real-time stream using @ghatana/realtime.
 *
 * <p><b>Purpose</b><br>
 * Specialized hook for department event stream with automatic reconnection
 * and type-safe event handling.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { events, isConnected } = useDepartmentRealtimeWebSocket('engineering');
 *
 * if (!isConnected) return <div>Connecting...</div>;
 * return <EventList events={events} />;
 * }</pre>
 *
 * @param departmentId - Department ID to subscribe to
 * @returns Department events, connection state
 *
 * @doc.type hook
 * @doc.purpose Department event stream with @ghatana/realtime
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useDepartmentRealtimeWebSocket(departmentId: string) {
    const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/api/v1/departments/stream';

    const { data, isConnected, error } = useRealtimeWebSocket<any[]>({
        url: `${wsUrl}/${departmentId}`,
        messageType: 'department-event',
    });

    return {
        events: data || [],
        isConnected,
        error,
    };
}

/**
 * Hook for real-time metrics stream using @ghatana/realtime.
 *
 * <p><b>Purpose</b><br>
 * Specialized hook for metrics stream with automatic reconnection
 * and type-safe metrics handling.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const { metrics, isConnected } = useMetricsRealtimeWebSocket();
 *
 * if (!isConnected) return <div>Connecting to metrics...</div>;
 * return <MetricsDisplay metrics={metrics} />;
 * }</pre>
 *
 * @returns Metrics data, connection state
 *
 * @doc.type hook
 * @doc.purpose Real-time metrics stream with @ghatana/realtime
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useMetricsRealtimeWebSocket() {
    const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/api/v1/metrics/stream';

    const { data, isConnected, error } = useRealtimeWebSocket<Record<string, any>>({
        url: wsUrl,
        messageType: 'metrics-update',
    });

    return {
        metrics: data || {},
        isConnected,
        error,
    };
}
