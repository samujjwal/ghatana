/**
 * Real-time Collaboration Service
 * 
 * Provides real-time collaboration features including:
 * - Presence tracking (who's online)
 * - Cursor/selection sharing
 * - Live updates and synchronization
 * - Collaborative editing
 * - Activity feed
 * 
 * Uses WebSocket for bi-directional communication.
 * Built on top of the existing WebSocket infrastructure.
 * 
 * @doc.type service
 * @doc.purpose Real-time collaboration and presence
 * @doc.layer application
 * @doc.pattern Event-driven Service
 * 
 * @example
 * ```tsx
 * import { CollaborationProvider, usePresence, useCollaboration } from '@ghatana/ui/collaboration';
 * 
 * function App() {
 *   return (
 *     <CollaborationProvider
 *       wsUrl="wss://api.example.com/collab"
 *       userId="user-123"
 *       userName="John Doe"
 *       roomId="document-456"
 *     >
 *       <Editor />
 *     </CollaborationProvider>
 *   );
 * }
 * 
 * function Editor() {
 *   const { users, myPresence } = usePresence();
 *   const { broadcast, subscribe } = useCollaboration();
 *   
 *   // Broadcast cursor position
 *   const handleCursorMove = (x: number, y: number) => {
 *     broadcast('cursor', { x, y });
 *   };
 *   
 *   // Subscribe to changes
 *   useEffect(() => {
 *     return subscribe('document-update', (data) => {
 *       applyUpdate(data);
 *     });
 *   }, []);
 *   
 *   return (
 *     <div>
 *       {users.map(user => (
 *         <UserCursor key={user.id} user={user} />
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */

import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface CollaborationUser {
    id: string;
    name: string;
    avatar?: string;
    color: string;
    cursor?: { x: number; y: number };
    selection?: { start: number; end: number };
    lastSeen: number;
}

export interface PresenceState {
    users: CollaborationUser[];
    myPresence: CollaborationUser | null;
}

export interface CollaborationMessage {
    type: string;
    userId: string;
    roomId: string;
    timestamp: number;
    data: unknown;
}

export interface CollaborationProviderProps {
    /** WebSocket URL */
    wsUrl: string;

    /** Current user ID */
    userId: string;

    /** Current user name */
    userName: string;

    /** Room/document ID */
    roomId: string;

    /** User avatar URL */
    userAvatar?: string;

    /** User color (hex) */
    userColor?: string;

    /** Reconnection interval in ms */
    reconnectInterval?: number;

    /** Max reconnection attempts */
    maxReconnectAttempts?: number;

    /** Children components */
    children: React.ReactNode;
}

// ============================================================================
// Context
// ============================================================================

interface CollaborationContextValue {
    connected: boolean;
    users: CollaborationUser[];
    myPresence: CollaborationUser | null;
    broadcast: (type: string, data: unknown) => void;
    subscribe: (type: string, handler: (data: unknown) => void) => () => void;
}

const CollaborationContext = createContext<CollaborationContextValue | null>(null);

// ============================================================================
// Provider Component
// ============================================================================

/**
 * Generates a random user color
 */
function generateUserColor(): string {
    const colors = [
        '#EF4444', // red
        '#F59E0B', // amber
        '#10B981', // emerald
        '#3B82F6', // blue
        '#8B5CF6', // violet
        '#EC4899', // pink
        '#14B8A6', // teal
        '#F97316', // orange
    ];
    return colors[Math.floor(Math.random() * colors.length)];
}

export function CollaborationProvider({
    wsUrl,
    userId,
    userName,
    roomId,
    userAvatar,
    userColor = generateUserColor(),
    reconnectInterval = 3000,
    maxReconnectAttempts = 5,
    children,
}: CollaborationProviderProps) {
    const [connected, setConnected] = useState(false);
    const [users, setUsers] = useState<CollaborationUser[]>([]);
    const [myPresence, setMyPresence] = useState<CollaborationUser | null>(null);

    const wsRef = useRef<WebSocket | null>(null);
    const subscribersRef = useRef<Map<string, Set<(data: unknown) => void>>>(new Map());
    const reconnectAttemptsRef = useRef(0);
    const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);

    // Initialize WebSocket connection
    const connect = useCallback(() => {
        if (wsRef.current?.readyState === WebSocket.OPEN) {
            return; // Already connected
        }

        try {
            const ws = new WebSocket(wsUrl);
            wsRef.current = ws;

            ws.onopen = () => {
                console.log('[Collaboration] Connected to WebSocket');
                setConnected(true);
                reconnectAttemptsRef.current = 0;

                // Join room
                const joinMessage: CollaborationMessage = {
                    type: 'join',
                    userId,
                    roomId,
                    timestamp: Date.now(),
                    data: {
                        name: userName,
                        avatar: userAvatar,
                        color: userColor,
                    },
                };
                ws.send(JSON.stringify(joinMessage));

                // Set own presence
                setMyPresence({
                    id: userId,
                    name: userName,
                    avatar: userAvatar,
                    color: userColor,
                    lastSeen: Date.now(),
                });
            };

            ws.onmessage = (event) => {
                try {
                    const message: CollaborationMessage = JSON.parse(event.data);

                    // Handle presence updates
                    if (message.type === 'presence') {
                        setUsers(message.data.users || []);
                    }

                    // Notify subscribers
                    const subscribers = subscribersRef.current.get(message.type);
                    if (subscribers) {
                        subscribers.forEach(handler => handler(message.data));
                    }
                } catch (error) {
                    console.error('[Collaboration] Failed to parse message:', error);
                }
            };

            ws.onerror = (error) => {
                console.error('[Collaboration] WebSocket error:', error);
            };

            ws.onclose = () => {
                console.log('[Collaboration] Disconnected from WebSocket');
                setConnected(false);
                wsRef.current = null;

                // Attempt reconnection
                if (reconnectAttemptsRef.current < maxReconnectAttempts) {
                    reconnectAttemptsRef.current++;
                    console.log(`[Collaboration] Reconnecting... (attempt ${reconnectAttemptsRef.current}/${maxReconnectAttempts})`);

                    reconnectTimeoutRef.current = setTimeout(() => {
                        connect();
                    }, reconnectInterval);
                } else {
                    console.error('[Collaboration] Max reconnection attempts reached');
                }
            };
        } catch (error) {
            console.error('[Collaboration] Failed to connect:', error);
        }
    }, [wsUrl, userId, userName, roomId, userAvatar, userColor, reconnectInterval, maxReconnectAttempts]);

    // Disconnect on unmount
    useEffect(() => {
        connect();

        return () => {
            if (reconnectTimeoutRef.current) {
                clearTimeout(reconnectTimeoutRef.current);
            }

            if (wsRef.current) {
                // Send leave message
                const leaveMessage: CollaborationMessage = {
                    type: 'leave',
                    userId,
                    roomId,
                    timestamp: Date.now(),
                    data: {},
                };

                if (wsRef.current.readyState === WebSocket.OPEN) {
                    wsRef.current.send(JSON.stringify(leaveMessage));
                }

                wsRef.current.close();
                wsRef.current = null;
            }
        };
    }, [connect, userId, roomId]);

    // Broadcast message to all users in room
    const broadcast = useCallback((type: string, data: unknown) => {
        if (wsRef.current?.readyState === WebSocket.OPEN) {
            const message: CollaborationMessage = {
                type,
                userId,
                roomId,
                timestamp: Date.now(),
                data,
            };
            wsRef.current.send(JSON.stringify(message));
        } else {
            console.warn('[Collaboration] Cannot broadcast - not connected');
        }
    }, [userId, roomId]);

    // Subscribe to specific message types
    const subscribe = useCallback((type: string, handler: (data: unknown) => void) => {
        if (!subscribersRef.current.has(type)) {
            subscribersRef.current.set(type, new Set());
        }

        const subscribers = subscribersRef.current.get(type)!;
        subscribers.add(handler);

        // Return unsubscribe function
        return () => {
            subscribers.delete(handler);
            if (subscribers.size === 0) {
                subscribersRef.current.delete(type);
            }
        };
    }, []);

    // Heartbeat to keep connection alive
    useEffect(() => {
        const interval = setInterval(() => {
            if (connected && myPresence) {
                broadcast('heartbeat', { timestamp: Date.now() });
            }
        }, 30000); // Every 30 seconds

        return () => clearInterval(interval);
    }, [connected, broadcast, myPresence]);

    const value: CollaborationContextValue = {
        connected,
        users,
        myPresence,
        broadcast,
        subscribe,
    };

    return (
        <CollaborationContext.Provider value={value}>
            {children}
        </CollaborationContext.Provider>
    );
}

// ============================================================================
// Hooks
// ============================================================================

/**
 * Hook to access collaboration context
 */
export function useCollaboration() {
    const context = useContext(CollaborationContext);
    if (!context) {
        throw new Error('useCollaboration must be used within CollaborationProvider');
    }
    return context;
}

/**
 * Hook for presence tracking
 */
export function usePresence(): PresenceState {
    const { users, myPresence } = useCollaboration();
    return { users, myPresence };
}

/**
 * Hook for broadcasting cursor position
 */
export function useCursor() {
    const { broadcast, myPresence } = useCollaboration();

    const updateCursor = useCallback((x: number, y: number) => {
        broadcast('cursor', { x, y });
    }, [broadcast]);

    const hideCursor = useCallback(() => {
        broadcast('cursor', { x: -1, y: -1 });
    }, [broadcast]);

    return { updateCursor, hideCursor, myPresence };
}

/**
 * Hook for selection tracking
 */
export function useSelection() {
    const { broadcast } = useCollaboration();

    const updateSelection = useCallback((start: number, end: number) => {
        broadcast('selection', { start, end });
    }, [broadcast]);

    const clearSelection = useCallback(() => {
        broadcast('selection', { start: -1, end: -1 });
    }, [broadcast]);

    return { updateSelection, clearSelection };
}

/**
 * Hook for activity feed
 */
export function useActivity() {
    const { subscribe } = useCollaboration();
    const [activities, setActivities] = useState<CollaborationMessage[]>([]);

    useEffect(() => {
        const unsubscribe = subscribe('activity', (data) => {
            setActivities(prev => [
                {
                    type: 'activity',
                    userId: data.userId,
                    roomId: data.roomId,
                    timestamp: data.timestamp,
                    data,
                },
                ...prev.slice(0, 49), // Keep last 50 activities
            ]);
        });

        return unsubscribe;
    }, [subscribe]);

    return activities;
}

export default CollaborationProvider;
