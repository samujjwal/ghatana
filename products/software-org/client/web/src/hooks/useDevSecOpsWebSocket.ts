/**
 * Socket.IO Real-Time WebSocket Hook for DevSecOps Updates
 *
 * @doc.type hook
 * @doc.purpose Real-time DevSecOps stage updates via Socket.IO
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect, useState, useCallback, useRef } from 'react';
import io, { Socket } from 'socket.io-client';

export interface UseDevSecOpsWebSocketOptions {
    /** API base URL (e.g., http://localhost:3101) */
    apiUrl: string;
    /** Stage key to subscribe to (e.g., DEVSECOPS_STAGE_PLAN) */
    stageKey?: string;
    /** Auto-connect on mount */
    autoConnect?: boolean;
    /** Optional authentication token */
    token?: string;
}

export interface DevSecOpsItem {
    id: string;
    title: string;
    description?: string;
    status: 'not-started' | 'in-progress' | 'in-review' | 'blocked' | 'completed';
    priority: 'low' | 'medium' | 'high' | 'critical';
    stageKey: string;
    createdAt: string;
    updatedAt: string;
}

export interface UseDevSecOpsWebSocketReturn {
    /** Current items for the stage */
    items: DevSecOpsItem[];
    /** Is connected to WebSocket */
    isConnected: boolean;
    /** Is connecting */
    isConnecting: boolean;
    /** Last error */
    error: Error | null;
    /** Connect manually */
    connect: () => void;
    /** Disconnect manually */
    disconnect: () => void;
    /** Subscribe to a stage */
    subscribe: (stageKey: string) => void;
    /** Unsubscribe from a stage */
    unsubscribe: (stageKey: string) => void;
}

/**
 * Hook for DevSecOps WebSocket updates using Socket.IO
 *
 * Connects to backend WebSocket server and subscribes to stage updates.
 * Automatically reconnects on disconnect.
 *
 * @example
 * const { items, isConnected } = useDevSecOpsWebSocket({
 *   apiUrl: 'http://localhost:3101',
 *   stageKey: 'DEVSECOPS_STAGE_PLAN',
 * });
 */
export function useDevSecOpsWebSocket(
    options: UseDevSecOpsWebSocketOptions
): UseDevSecOpsWebSocketReturn {
    const { apiUrl, stageKey, autoConnect = true, token } = options;

    const [items, setItems] = useState<DevSecOpsItem[]>([]);
    const [isConnected, setIsConnected] = useState(false);
    const [isConnecting, setIsConnecting] = useState(false);
    const [error, setError] = useState<Error | null>(null);

    const socketRef = useRef<Socket | null>(null);
    const isMountedRef = useRef(true);
    const currentStageRef = useRef<string | null>(stageKey || null);

    // Initialize socket connection
    useEffect(() => {
        isMountedRef.current = true;

        if (!autoConnect) {
            return;
        }

        // Create socket connection
        const socket = io(apiUrl, {
            path: '/socket.io/',
            transports: ['websocket', 'polling'],
            reconnection: true,
            reconnectionDelay: 1000,
            reconnectionDelayMax: 5000,
            reconnectionAttempts: 10,
            // Include auth token if provided
            auth: token ? { token } : {},
        });

        socketRef.current = socket;

        // Handle connection
        socket.on('connect', () => {
            if (isMountedRef.current) {
                setIsConnected(true);
                setIsConnecting(false);
                setError(null);
                console.log('[DevSecOps WebSocket] Connected');

                // Subscribe to current stage if set
                if (currentStageRef.current) {
                    socket.emit('subscribe', { stageKey: currentStageRef.current });
                }
            }
        });

        // Handle connecting
        socket.on('connecting', () => {
            if (isMountedRef.current) {
                setIsConnecting(true);
            }
        });

        // Handle disconnection
        socket.on('disconnect', () => {
            if (isMountedRef.current) {
                setIsConnected(false);
                setIsConnecting(false);
                console.log('[DevSecOps WebSocket] Disconnected');
            }
        });

        // Handle stage snapshot (initial data when subscribing)
        socket.on('stage-snapshot', (data: { stageKey: string; items: DevSecOpsItem[] }) => {
            if (isMountedRef.current) {
                console.log(`[DevSecOps WebSocket] Received snapshot for ${data.stageKey}:`, data.items);
                setItems(data.items);
            }
        });

        // Handle stage updates (real-time updates)
        socket.on('stage-update', (data: { stageKey: string; items: DevSecOpsItem[] }) => {
            if (isMountedRef.current) {
                console.log(`[DevSecOps WebSocket] Stage update for ${data.stageKey}:`, data.items);
                setItems(data.items);
            }
        });

        // Handle errors
        socket.on('error', (data: any) => {
            if (isMountedRef.current) {
                const err = new Error(data.message || 'WebSocket error');
                setError(err);
                console.error('[DevSecOps WebSocket] Error:', err);
            }
        });

        // Handle generic errors
        socket.on('connect_error', (err: Error) => {
            if (isMountedRef.current) {
                setError(err);
                console.error('[DevSecOps WebSocket] Connection error:', err);
            }
        });

        // Cleanup on unmount
        return () => {
            isMountedRef.current = false;
            if (socketRef.current) {
                socketRef.current.disconnect();
                socketRef.current = null;
            }
        };
    }, [apiUrl, autoConnect, token]);

    // Subscribe to stage
    const subscribe = useCallback((stage: string) => {
        currentStageRef.current = stage;
        if (socketRef.current?.connected) {
            socketRef.current.emit('subscribe', { stageKey: stage });
            console.log(`[DevSecOps WebSocket] Subscribed to stage: ${stage}`);
        }
    }, []);

    // Unsubscribe from stage
    const unsubscribe = useCallback((stage: string) => {
        if (socketRef.current?.connected) {
            socketRef.current.emit('unsubscribe', { stageKey: stage });
            console.log(`[DevSecOps WebSocket] Unsubscribed from stage: ${stage}`);
        }
        if (currentStageRef.current === stage) {
            currentStageRef.current = null;
        }
    }, []);

    // Connect manually
    const connect = useCallback(() => {
        if (socketRef.current && !socketRef.current.connected) {
            socketRef.current.connect();
            console.log('[DevSecOps WebSocket] Connecting...');
        }
    }, []);

    // Disconnect manually
    const disconnect = useCallback(() => {
        if (socketRef.current?.connected) {
            socketRef.current.disconnect();
            console.log('[DevSecOps WebSocket] Disconnecting...');
        }
    }, []);

    return {
        items,
        isConnected,
        isConnecting,
        error,
        connect,
        disconnect,
        subscribe,
        unsubscribe,
    };
}
