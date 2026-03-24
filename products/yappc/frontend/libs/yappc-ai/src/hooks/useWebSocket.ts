/**
 * WebSocket Hooks for Real-Time AI Streaming
 *
 * React hooks for consuming WebSocket-based real-time AI updates
 *
 * @module ai/hooks/useWebSocket
 * @doc.type hooks
 * @doc.purpose Real-time AI streaming client hooks
 * @doc.layer product
 * @doc.pattern Observer
 */

import { useEffect, useState, useCallback, useRef } from 'react';

/**
 * WebSocket message types (matches backend)
 */
export enum WSMessageType {
    SUBSCRIBE = 'subscribe',
    UNSUBSCRIBE = 'unsubscribe',
    PING = 'ping',

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
 * WebSocket connection status
 */
export type WSStatus = 'connecting' | 'connected' | 'disconnected' | 'error';

/**
 * Base WebSocket hook
 */
export function useWebSocket(url: string, options?: {
    autoReconnect?: boolean;
    reconnectInterval?: number;
    maxReconnectAttempts?: number;
}) {
    const [status, setStatus] = useState<WSStatus>('disconnected');
    const [error, setError] = useState<Error | null>(null);
    const wsRef = useRef<WebSocket | null>(null);
    const reconnectAttemptsRef = useRef(0);
    const reconnectTimeoutRef = useRef<NodeJS.Timeout>();

    const {
        autoReconnect = true,
        reconnectInterval = 5000,
        maxReconnectAttempts = 5,
    } = options || {};

    const connect = useCallback(() => {
        if (wsRef.current?.readyState === WebSocket.OPEN) {
            return;
        }

        setStatus('connecting');
        const ws = new WebSocket(url);

        ws.onopen = () => {
            setStatus('connected');
            setError(null);
            reconnectAttemptsRef.current = 0;
        };

        ws.onerror = (event) => {
            setError(new Error('WebSocket error'));
            setStatus('error');
        };

        ws.onclose = () => {
            setStatus('disconnected');
            wsRef.current = null;

            // Auto-reconnect logic
            if (autoReconnect && reconnectAttemptsRef.current < maxReconnectAttempts) {
                reconnectAttemptsRef.current++;
                reconnectTimeoutRef.current = setTimeout(() => {
                    connect();
                }, reconnectInterval);
            }
        };

        wsRef.current = ws;
    }, [url, autoReconnect, reconnectInterval, maxReconnectAttempts]);

    const disconnect = useCallback(() => {
        if (reconnectTimeoutRef.current) {
            clearTimeout(reconnectTimeoutRef.current);
        }
        if (wsRef.current) {
            wsRef.current.close();
            wsRef.current = null;
        }
        setStatus('disconnected');
    }, []);

    const send = useCallback((message: Partial<WSMessage>) => {
        if (wsRef.current?.readyState === WebSocket.OPEN) {
            wsRef.current.send(JSON.stringify({
                ...message,
                timestamp: Date.now(),
            }));
        }
    }, []);

    useEffect(() => {
        connect();
        return () => {
            disconnect();
        };
    }, [connect, disconnect]);

    return {
        status,
        error,
        send,
        reconnect: connect,
        disconnect,
        ws: wsRef.current,
    };
}

/**
 * Hook for streaming copilot responses
 */
export function useCopilotStream(sessionId: string | null, options?: {
    wsUrl?: string;
    onChunk?: (chunk: string) => void;
    onComplete?: () => void;
    onError?: (error: Error) => void;
}) {
    const [chunks, setChunks] = useState<string[]>([]);
    const [isStreaming, setIsStreaming] = useState(false);
    const [streamError, setStreamError] = useState<Error | null>(null);

    const wsUrl = options?.wsUrl || `ws://${window.location.host}/ws/ai`;
    const { status, send, ws } = useWebSocket(wsUrl);

    useEffect(() => {
        if (!ws || !sessionId) return;

        const handleMessage = (event: MessageEvent) => {
            try {
                const message: WSMessage = JSON.parse(event.data);

                switch (message.type) {
                    case WSMessageType.COPILOT_CHUNK:
                        const chunk = (message.data as { chunk: string }).chunk;
                        setChunks((prev) => [...prev, chunk]);
                        setIsStreaming(true);
                        options?.onChunk?.(chunk);
                        break;

                    case WSMessageType.COPILOT_COMPLETE:
                        setIsStreaming(false);
                        options?.onComplete?.();
                        break;

                    case WSMessageType.COPILOT_ERROR:
                        const error = new Error(message.error?.message || 'Streaming error');
                        setStreamError(error);
                        setIsStreaming(false);
                        options?.onError?.(error);
                        break;
                }
            } catch (err) {
                console.error('Failed to parse WebSocket message:', err);
            }
        };

        ws.addEventListener('message', handleMessage);

        // Subscribe to copilot channel
        if (status === 'connected') {
            send({
                type: WSMessageType.SUBSCRIBE,
                data: { channel: 'copilot', filter: { sessionId } },
            });
        }

        return () => {
            ws.removeEventListener('message', handleMessage);
            // Unsubscribe
            if (status === 'connected') {
                send({
                    type: WSMessageType.UNSUBSCRIBE,
                    data: { channel: 'copilot', filter: { sessionId } },
                });
            }
        };
    }, [ws, sessionId, status, send, options]);

    const clearChunks = useCallback(() => {
        setChunks([]);
        setStreamError(null);
    }, []);

    return {
        chunks,
        fullText: chunks.join(''),
        isStreaming,
        streamError,
        connectionStatus: status,
        clearChunks,
    };
}

/**
 * Hook for anomaly alerts
 */
export function useAnomalyAlerts(options?: {
    wsUrl?: string;
    onAlert?: (anomaly: {
        id: string;
        type: string;
        severity: string;
        title: string;
        description: string;
        affectedItems: string[];
    }) => void;
}) {
    const [alerts, setAlerts] = useState<Array<{
        id: string;
        type: string;
        severity: string;
        title: string;
        description: string;
        affectedItems: string[];
        timestamp: number;
    }>>([]);

    const wsUrl = options?.wsUrl || `ws://${window.location.host}/ws/ai`;
    const { status, send, ws } = useWebSocket(wsUrl);

    useEffect(() => {
        if (!ws) return;

        const handleMessage = (event: MessageEvent) => {
            try {
                const message: WSMessage = JSON.parse(event.data);

                if (message.type === WSMessageType.ANOMALY_ALERT) {
                    const anomaly = message.data as {
                        id: string;
                        type: string;
                        severity: string;
                        title: string;
                        description: string;
                        affectedItems: string[];
                    };

                    setAlerts((prev) => [
                        ...prev,
                        { ...anomaly, timestamp: message.timestamp },
                    ]);
                    options?.onAlert?.(anomaly);
                }
            } catch (err) {
                console.error('Failed to parse WebSocket message:', err);
            }
        };

        ws.addEventListener('message', handleMessage);

        // Subscribe to anomaly channel
        if (status === 'connected') {
            send({
                type: WSMessageType.SUBSCRIBE,
                data: { channel: 'anomaly' },
            });
        }

        return () => {
            ws.removeEventListener('message', handleMessage);
            if (status === 'connected') {
                send({
                    type: WSMessageType.UNSUBSCRIBE,
                    data: { channel: 'anomaly' },
                });
            }
        };
    }, [ws, status, send, options]);

    const dismissAlert = useCallback((alertId: string) => {
        setAlerts((prev) => prev.filter((alert) => alert.id !== alertId));
    }, []);

    const clearAllAlerts = useCallback(() => {
        setAlerts([]);
    }, []);

    return {
        alerts,
        unreadCount: alerts.length,
        connectionStatus: status,
        dismissAlert,
        clearAllAlerts,
    };
}

/**
 * Hook for prediction updates
 */
export function usePredictionUpdates(targetId: string | null, options?: {
    wsUrl?: string;
    onUpdate?: (prediction: {
        targetId: string;
        targetType: string;
        probability: number;
        confidence: number;
    }) => void;
}) {
    const [latestPrediction, setLatestPrediction] = useState<{
        targetId: string;
        targetType: string;
        probability: number;
        confidence: number;
        timestamp: number;
    } | null>(null);

    const wsUrl = options?.wsUrl || `ws://${window.location.host}/ws/ai`;
    const { status, send, ws } = useWebSocket(wsUrl);

    useEffect(() => {
        if (!ws || !targetId) return;

        const handleMessage = (event: MessageEvent) => {
            try {
                const message: WSMessage = JSON.parse(event.data);

                if (message.type === WSMessageType.PREDICTION_UPDATE) {
                    const prediction = message.data as {
                        targetId: string;
                        targetType: string;
                        probability: number;
                        confidence: number;
                    };

                    setLatestPrediction({
                        ...prediction,
                        timestamp: message.timestamp,
                    });
                    options?.onUpdate?.(prediction);
                }
            } catch (err) {
                console.error('Failed to parse WebSocket message:', err);
            }
        };

        ws.addEventListener('message', handleMessage);

        // Subscribe to prediction channel for specific target
        if (status === 'connected') {
            send({
                type: WSMessageType.SUBSCRIBE,
                data: { channel: 'prediction', filter: { targetId } },
            });
        }

        return () => {
            ws.removeEventListener('message', handleMessage);
            if (status === 'connected') {
                send({
                    type: WSMessageType.UNSUBSCRIBE,
                    data: { channel: 'prediction', filter: { targetId } },
                });
            }
        };
    }, [ws, targetId, status, send, options]);

    return {
        prediction: latestPrediction,
        isStale: latestPrediction
            ? Date.now() - latestPrediction.timestamp > 60000
            : false,
        connectionStatus: status,
    };
}
