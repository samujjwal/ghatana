/**
 * @deprecated Use useRealtimeWebSocket from './useRealtimeWebSocket' instead.
 * This file is maintained for backward compatibility only.
 * 
 * Migration Guide:
 * ```typescript
 * // OLD
 * import { useWebSocket } from './useWebSocket';
 * const { data, isConnected } = useWebSocket(url, messageType);
 * 
 * // NEW
 * import { useRealtimeWebSocket } from './useRealtimeWebSocket';
 * const { data, isConnected } = useRealtimeWebSocket({ url, messageType });
 * ```
 */

import { useEffect, useState, useCallback, useRef } from 'react';

/**
 * WebSocket event types.
 *
 * @deprecated Use WebSocketMessage from '@ghatana/realtime' instead
 * @doc.type type
 * @doc.purpose WebSocket message event payload
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface WebSocketMessage<T = any> {
    type: string;
    data: T;
    timestamp: string;
    id?: string;
}

/**
 * WebSocket connection state.
 *
 * @deprecated Use WebSocketConnectionState from '@ghatana/realtime' instead
 * @doc.type type
 * @doc.purpose Connection status tracking
 * @doc.layer product
 * @doc.pattern Type Definition
 */
export interface WebSocketState<T = any> {
    data: T | null;
    isConnected: boolean;
    isLoading: boolean;
    error: string | null;
    lastUpdate: string | null;
}

/**
 * Hook for WebSocket real-time data subscriptions.
 *
 * @deprecated Use useRealtimeWebSocket from './useRealtimeWebSocket' instead.
 * This implementation is maintained for backward compatibility only.
 *
 * <p><b>Purpose</b><br>
 * Manages WebSocket connection lifecycle, handles reconnection with exponential
 * backoff, and provides typed data updates. Automatically cleans up on unmount.
 *
 * <p><b>Features</b><br>
 * - Automatic reconnection with exponential backoff (max 30s)
 * - Heartbeat monitoring (30s timeout)
 * - Message type filtering
 * - Error recovery
 * - Connection state tracking
 *
 * <p><b>Migration</b><br>
 * <pre>{@code
 * // OLD
 * const { data, isConnected, error } = useWebSocket(
 *   'ws://api.example.com/stream/hitl-actions',
 *   'action-update'
 * );
 *
 * // NEW
 * import { useRealtimeWebSocket } from './useRealtimeWebSocket';
 * const { data, isConnected, error } = useRealtimeWebSocket({
 *   url: 'ws://api.example.com/stream/hitl-actions',
 *   messageType: 'action-update'
 * });
 * }</pre>
 *
 * @param url - WebSocket URL to connect to
 * @param messageType - Optional message type to filter (null = all)
 * @param onMessage - Optional callback when message received
 *
 * @returns WebSocket state with data, connection status, and error info
 *
 * @doc.type hook
 * @doc.purpose WebSocket real-time subscription (deprecated)
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useWebSocket<T = any>(
    url: string,
    messageType?: string,
    onMessage?: (data: WebSocketMessage<T>) => void
): WebSocketState<T> {
    const [state, setState] = useState<WebSocketState<T>>({
        data: null,
        isConnected: false,
        isLoading: true,
        error: null,
        lastUpdate: null,
    });

    const wsRef = useRef<WebSocket | null>(null);
    const reconnectTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const heartbeatTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const reconnectAttemptsRef = useRef(0);

    const calculateBackoff = useCallback(() => {
        const delay = Math.min(1000 * Math.pow(2, reconnectAttemptsRef.current), 30000);
        return delay + Math.random() * 1000; // Add jitter
    }, []);

    const connect = useCallback(() => {
        try {
            // Simulate WebSocket connection (for mock/development)
            // In production, use: const ws = new WebSocket(url);
            const ws = new WebSocket(url);

            ws.onopen = () => {
                console.log('[WebSocket] Connected:', url);
                setState((prev) => ({
                    ...prev,
                    isConnected: true,
                    isLoading: false,
                    error: null,
                }));
                reconnectAttemptsRef.current = 0;

                // Send heartbeat subscription
                ws.send(JSON.stringify({ type: 'subscribe', messageType }));

                // Start heartbeat
                heartbeatTimeoutRef.current = setTimeout(() => {
                    if (ws.readyState === WebSocket.OPEN) {
                        ws.send(JSON.stringify({ type: 'ping' }));
                        heartbeatTimeoutRef.current = setTimeout(() => {
                            console.warn('[WebSocket] Heartbeat timeout, reconnecting...');
                            ws.close();
                        }, 30000);
                    }
                }, 30000);
            };

            ws.onmessage = (event) => {
                try {
                    const message: WebSocketMessage<T> = JSON.parse(event.data);

                    // Filter by message type if specified
                    if (messageType && message.type !== messageType) {
                        return;
                    }

                    console.log('[WebSocket] Message:', message);

                    // Update state
                    setState((prev) => ({
                        ...prev,
                        data: message.data,
                        lastUpdate: new Date().toISOString(),
                    }));

                    // Call optional handler
                    onMessage?.(message);

                    // Reset heartbeat on message
                    if (heartbeatTimeoutRef.current) {
                        clearTimeout(heartbeatTimeoutRef.current);
                    }
                    heartbeatTimeoutRef.current = setTimeout(() => {
                        if (ws.readyState === WebSocket.OPEN) {
                            ws.send(JSON.stringify({ type: 'ping' }));
                        }
                    }, 30000);
                } catch (err) {
                    console.error('[WebSocket] Parse error:', err);
                }
            };

            ws.onerror = (event) => {
                console.error('[WebSocket] Error:', event);
                setState((prev) => ({
                    ...prev,
                    error: 'WebSocket connection error',
                }));
            };

            ws.onclose = () => {
                console.log('[WebSocket] Closed, reconnecting in', calculateBackoff(), 'ms');
                setState((prev) => ({
                    ...prev,
                    isConnected: false,
                }));

                // Clear heartbeat
                if (heartbeatTimeoutRef.current) {
                    clearTimeout(heartbeatTimeoutRef.current);
                }

                // Attempt reconnection with backoff
                reconnectTimeoutRef.current = setTimeout(() => {
                    reconnectAttemptsRef.current += 1;
                    connect();
                }, calculateBackoff());
            };

            wsRef.current = ws;
        } catch (err) {
            console.error('[WebSocket] Connection failed:', err);
            setState((prev) => ({
                ...prev,
                isLoading: false,
                error: String(err),
            }));

            // Retry connection
            reconnectTimeoutRef.current = setTimeout(() => {
                reconnectAttemptsRef.current += 1;
                connect();
            }, calculateBackoff());
        }
    }, [url, messageType, onMessage, calculateBackoff]);

    useEffect(() => {
        connect();

        return () => {
            // Cleanup
            if (reconnectTimeoutRef.current) {
                clearTimeout(reconnectTimeoutRef.current);
            }
            if (heartbeatTimeoutRef.current) {
                clearTimeout(heartbeatTimeoutRef.current);
            }

            if (wsRef.current) {
                wsRef.current.close();
            }
        };
    }, [connect]);

    return state;
}

/**
 * Hook for HITL Console real-time action updates.
 *
 * @deprecated Use useHitlRealtimeWebSocket from './useRealtimeWebSocket' instead.
 *
 * <p><b>Migration</b><br>
 * <pre>{@code
 * // OLD
 * import { useHitlWebSocket } from './useWebSocket';
 * const { actions, isConnected } = useHitlWebSocket();
 *
 * // NEW
 * import { useHitlRealtimeWebSocket } from './useRealtimeWebSocket';
 * const { actions, isConnected, error } = useHitlRealtimeWebSocket();
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose HITL real-time action stream (deprecated)
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
export function useHitlWebSocket() {
    const wsUrl = import.meta.env.VITE_WS_URL || 'ws://localhost:8080/api/v1/hitl/stream';

    const { data, isConnected, error } = useWebSocket<any[]>(
        wsUrl,
        'action-update'
    );

    return {
        actions: data || [],
        isConnected,
        error,
    };
}
