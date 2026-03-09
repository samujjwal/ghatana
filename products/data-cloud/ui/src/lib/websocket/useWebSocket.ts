/**
 * WebSocket Hook
 * 
 * React hook for WebSocket subscriptions and real-time updates.
 * 
 * @doc.type hook
 * @doc.purpose WebSocket subscription hook
 * @doc.layer frontend
 * @doc.pattern Custom Hook
 */

import { useEffect, useState, useCallback } from 'react';
import { wsClient, WebSocketEvent, WebSocketEventType, ConnectionState } from './client';

/**
 * Hook to get WebSocket connection state
 * 
 * @example
 * ```tsx
 * const connectionState = useWebSocketState();
 * // 'connecting' | 'connected' | 'disconnected' | 'reconnecting'
 * ```
 */
export function useWebSocketState(): ConnectionState {
    const [state, setState] = useState<ConnectionState>(wsClient.state);

    useEffect(() => {
        const unsubscribe = wsClient.onStateChange(setState);
        return unsubscribe;
    }, []);

    return state;
}

/**
 * Hook to subscribe to WebSocket events
 * 
 * @example
 * ```tsx
 * useWebSocketEvent('workflow.completed', (event) => {
 *   console.log('Workflow completed:', event.payload);
 * });
 * ```
 */
export function useWebSocketEvent<T = unknown>(
    eventType: WebSocketEventType | '*',
    handler: (event: WebSocketEvent<T>) => void
): void {
    useEffect(() => {
        const unsubscribe = wsClient.subscribe(eventType, handler);
        return unsubscribe;
    }, [eventType, handler]);
}

/**
 * Hook to connect/disconnect WebSocket
 * 
 * @example
 * ```tsx
 * const { connect, disconnect, isConnected } = useWebSocketConnection();
 * ```
 */
export function useWebSocketConnection() {
    const state = useWebSocketState();

    const connect = useCallback(() => {
        wsClient.connect();
    }, []);

    const disconnect = useCallback(() => {
        wsClient.disconnect();
    }, []);

    return {
        connect,
        disconnect,
        state,
        isConnected: state === 'connected',
        isConnecting: state === 'connecting',
        isReconnecting: state === 'reconnecting',
    };
}

/**
 * Hook to auto-connect WebSocket on mount
 * 
 * @example
 * ```tsx
 * function App() {
 *   useWebSocketAutoConnect();
 *   return <div>...</div>;
 * }
 * ```
 */
export function useWebSocketAutoConnect(): void {
    useEffect(() => {
        const enabled = import.meta.env.PROD || Boolean(import.meta.env.VITE_WS_URL);
        if (!enabled) {
            return;
        }

        wsClient.connect();
        return () => {
            // Don't disconnect on unmount to maintain connection across route changes
            // wsClient.disconnect();
        };
    }, []);
}

/**
 * Hook to subscribe to multiple event types
 * 
 * @example
 * ```tsx
 * useWebSocketEvents(
 *   ['workflow.started', 'workflow.completed', 'workflow.failed'],
 *   (event) => {
 *     console.log('Workflow event:', event);
 *   }
 * );
 * ```
 */
export function useWebSocketEvents<T = unknown>(
    eventTypes: WebSocketEventType[],
    handler: (event: WebSocketEvent<T>) => void
): void {
    useEffect(() => {
        const unsubscribes = eventTypes.map((type) =>
            wsClient.subscribe(type, handler)
        );

        return () => {
            unsubscribes.forEach((unsubscribe) => unsubscribe());
        };
    }, [eventTypes, handler]);
}

/**
 * Hook to get latest event of a specific type
 * 
 * @example
 * ```tsx
 * const latestAlert = useLatestWebSocketEvent('alert.created');
 * ```
 */
export function useLatestWebSocketEvent<T = unknown>(
    eventType: WebSocketEventType
): WebSocketEvent<T> | null {
    const [latestEvent, setLatestEvent] = useState<WebSocketEvent<T> | null>(null);

    useWebSocketEvent<T>(eventType, (event) => {
        setLatestEvent(event);
    });

    return latestEvent;
}
