/**
 * Persona Sync Hook - Real-time WebSocket synchronization
 *
 * Purpose:
 * Provides real-time synchronization of persona preferences across multiple tabs/devices.
 * Automatically invalidates React Query cache when remote updates are detected.
 *
 * Features:
 * - Auto-reconnection with exponential backoff
 * - JWT authentication
 * - Workspace room management
 * - Cache invalidation on remote updates
 * - Connection state tracking
 *
 * Architecture:
 * User updates in Tab 1 → HTTP POST → Backend broadcasts via Socket.IO
 * → Tab 2 receives event → React Query cache invalidated → UI auto-updates
 *
 * Usage:
 * ```typescript
 * const { isConnected, error } = usePersonaSync('workspace-123');
 * // That's it! Cache invalidation happens automatically
 * ```
 *
 * @doc.type hook
 * @doc.purpose WebSocket real-time sync for persona preferences
 * @doc.layer product
 * @doc.pattern React Hook + WebSocket
 */

import { useEffect, useState, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { io, Socket } from 'socket.io-client';
import { personaKeys } from './usePersonaQueries';

/**
 * Persona update event from backend
 */
interface PersonaUpdateEvent {
    workspaceId: string;
    userId: string;
    activeRoles: string[];
    preferences: Record<string, unknown>;
    timestamp: string;
}

/**
 * Persona delete event from backend
 */
interface PersonaDeleteEvent {
    workspaceId: string;
    userId: string;
    timestamp: string;
}

/**
 * WebSocket connection configuration
 */
interface PersonaSyncConfig {
    /**
     * Backend WebSocket URL
     * @default process.env.VITE_WS_URL || 'http://localhost:3001'
     */
    url?: string;

    /**
     * Enable debug logging
     * @default false
     */
    debug?: boolean;

    /**
     * Reconnection attempts before giving up
     * @default 5
     */
    maxReconnectAttempts?: number;
}

/**
 * Hook return value
 */
interface UsePersonaSyncReturn {
    /** Whether WebSocket is connected */
    isConnected: boolean;
    /** Connection error (if any) */
    error: Error | null;
    /** Manually trigger reconnection */
    reconnect: () => void;
}

/**
 * Real-time persona sync hook
 *
 * Automatically invalidates React Query cache when persona updates
 * are received from other clients in the same workspace.
 *
 * @param workspaceId Workspace ID to sync
 * @param config Optional configuration
 * @returns Connection state and controls
 *
 * @example
 * ```typescript
 * function Dashboard() {
 *   const { isConnected, error } = usePersonaSync('workspace-123');
 *
 *   return (
 *     <div>
 *       {!isConnected && <Banner>Connecting to real-time sync...</Banner>}
 *       {error && <Banner>Sync error: {error.message}</Banner>}
 *     </div>
 *   );
 * }
 * ```
 */
export function usePersonaSync(
    workspaceId: string,
    config: PersonaSyncConfig = {}
): UsePersonaSyncReturn {
    const {
        url = import.meta.env.VITE_WS_URL || 'http://localhost:3001',
        debug = false,
        maxReconnectAttempts = 5,
    } = config;

    const queryClient = useQueryClient();
    const [isConnected, setIsConnected] = useState(false);
    const [error, setError] = useState<Error | null>(null);
    const socketRef = useRef<Socket | null>(null);
    const reconnectAttemptsRef = useRef(0);

    useEffect(() => {
        // Get JWT token from localStorage
        const token = localStorage.getItem('token');
        if (!token) {
            setError(new Error('Authentication token not found'));
            return;
        }

        if (debug) {
            console.log('[PersonaSync] Connecting to', url, 'workspace:', workspaceId);
        }

        // Create Socket.IO connection
        const socket = io(url, {
            auth: {
                token,
            },
            transports: ['websocket', 'polling'],
            reconnection: true,
            reconnectionDelay: 1000,
            reconnectionDelayMax: 5000,
            reconnectionAttempts: maxReconnectAttempts,
        });

        socketRef.current = socket;

        // Connection event handlers
        socket.on('connect', () => {
            if (debug) console.log('[PersonaSync] Connected, socket ID:', socket.id);
            setIsConnected(true);
            setError(null);
            reconnectAttemptsRef.current = 0;

            // Join workspace room
            socket.emit('persona:join-workspace', workspaceId);
        });

        socket.on('disconnect', (reason: string) => {
            if (debug) console.log('[PersonaSync] Disconnected, reason:', reason);
            setIsConnected(false);

            if (reason === 'io server disconnect') {
                // Server closed connection, reconnect manually
                socket.connect();
            }
        });

        socket.on('connect_error', (err: Error) => {
            console.error('[PersonaSync] Connection error:', err);
            setError(err);
            reconnectAttemptsRef.current++;

            if (reconnectAttemptsRef.current >= maxReconnectAttempts) {
                setError(new Error('Max reconnection attempts reached'));
                socket.close();
            }
        });

        socket.on('persona:workspace-joined', (data: { workspaceId: string }) => {
            if (debug) console.log('[PersonaSync] Joined workspace:', data.workspaceId);
        });

        // Persona update event (from other clients)
        socket.on('persona:updated', (event: PersonaUpdateEvent) => {
            if (debug) {
                console.log('[PersonaSync] Persona updated remotely:', event);
            }

            // Invalidate React Query cache to trigger refetch
            queryClient.invalidateQueries({
                queryKey: personaKeys.preference(event.workspaceId),
            });

            // Also invalidate permissions (derived from preference)
            queryClient.invalidateQueries({
                queryKey: personaKeys.permissions(event.activeRoles),
            });

            if (debug) console.log('[PersonaSync] Cache invalidated, UI will refetch');
        });

        // Persona delete event (from other clients)
        socket.on('persona:deleted', (event: PersonaDeleteEvent) => {
            if (debug) {
                console.log('[PersonaSync] Persona deleted remotely:', event);
            }

            // Invalidate and reset cache to null
            queryClient.setQueryData(personaKeys.preference(event.workspaceId), null);

            if (debug) console.log('[PersonaSync] Cache reset to null');
        });

        // Cleanup on unmount
        return () => {
            if (debug) console.log('[PersonaSync] Disconnecting...');
            socket.emit('persona:leave-workspace', workspaceId);
            socket.disconnect();
            socketRef.current = null;
        };
    }, [workspaceId, url, debug, maxReconnectAttempts, queryClient]);

    const reconnect = () => {
        if (socketRef.current) {
            reconnectAttemptsRef.current = 0;
            setError(null);
            socketRef.current.connect();
        }
    };

    return {
        isConnected,
        error,
        reconnect,
    };
}
