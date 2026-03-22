/**
 * Yjs Collaboration Adapter for Physics Simulations
 *
 * Integrates with @ghatana/yappc-canvas collaboration infrastructure while
 * providing physics-specific synchronization for entities.
 *
 * Features:
 * - Real-time entity synchronization via Yjs CRDT
 * - Multi-cursor awareness
 * - Conflict-free concurrent editing
 * - Offline support with auto-reconnection
 *
 * @doc.type hook
 * @doc.purpose Real-time collaboration for physics simulations
 * @doc.layer core
 * @doc.pattern Hook
 */

import { useEffect, useState, useCallback, useRef } from 'react';
import { useSetAtom, useAtomValue } from 'jotai';
import * as Y from 'yjs';
import { WebsocketProvider } from 'y-websocket';
import { IndexeddbPersistence } from 'y-indexeddb';

import type { PhysicsEntity, PhysicsConfig } from '../types';
import { simulationEntitiesAtom, simulationPhysicsConfigAtom, loadEntitiesAtom } from '../state';

/**
 * Collaboration user with cursor and selection state
 */
export interface CollaborationUser {
    id: string;
    name: string;
    color: string;
    cursor?: { x: number; y: number };
    selectedEntityId?: string;
    lastSeen: number;
    isOnline: boolean;
}

/**
 * Collaboration state
 */
export interface PhysicsCollaborationState {
    users: Record<string, CollaborationUser>;
    currentUser: CollaborationUser;
    isConnected: boolean;
    syncStatus: 'connecting' | 'syncing' | 'synced' | 'error' | 'offline';
    roomId: string;
}

/**
 * Options for collaboration hook
 */
export interface UsePhysicsCollaborationOptions {
    /** WebSocket server URL */
    serverUrl?: string;
    /** Enable IndexedDB persistence */
    enablePersistence?: boolean;
    /** Reconnection attempts */
    maxReconnectAttempts?: number;
}

const DEFAULT_OPTIONS: UsePhysicsCollaborationOptions = {
    serverUrl: 'ws://localhost:1234',
    enablePersistence: true,
    maxReconnectAttempts: 5,
};

/**
 * Generate a random color for user avatar
 */
function generateUserColor(): string {
    const colors = [
        '#ef4444', // red
        '#f97316', // orange
        '#eab308', // yellow
        '#22c55e', // green
        '#06b6d4', // cyan
        '#3b82f6', // blue
        '#8b5cf6', // violet
        '#ec4899', // pink
    ];
    return colors[Math.floor(Math.random() * colors.length)];
}

/**
 * Hook for Yjs-based real-time collaboration in physics simulations
 */
export function usePhysicsCollaboration(
    roomId: string,
    userId: string,
    userName: string,
    options: UsePhysicsCollaborationOptions = {}
): PhysicsCollaborationState & {
    updateCursor: (x: number, y: number) => void;
    updateSelection: (entityId: string | null) => void;
} {
    const config = { ...DEFAULT_OPTIONS, ...options };

    // Jotai atoms
    const entities = useAtomValue(simulationEntitiesAtom);
    const physicsConfig = useAtomValue(simulationPhysicsConfigAtom);
    const loadEntities = useSetAtom(loadEntitiesAtom);

    // Refs for Yjs objects
    const ydocRef = useRef<Y.Doc | null>(null);
    const providerRef = useRef<WebsocketProvider | null>(null);
    const persistenceRef = useRef<IndexeddbPersistence | null>(null);
    const awarenessRef = useRef<any>(null);

    // State
    const [collaborationState, setCollaborationState] = useState<PhysicsCollaborationState>({
        users: {},
        currentUser: {
            id: userId,
            name: userName,
            color: generateUserColor(),
            lastSeen: Date.now(),
            isOnline: true,
        },
        isConnected: false,
        syncStatus: 'connecting',
        roomId,
    });

    // Initialize Yjs
    useEffect(() => {
        const ydoc = new Y.Doc();
        ydocRef.current = ydoc;

        // Create shared types
        const yEntities = ydoc.getArray<PhysicsEntity>('entities');
        const yPhysicsConfig = ydoc.getMap<any>('physicsConfig');

        // WebSocket provider
        const provider = new WebsocketProvider(config.serverUrl!, `physics-${roomId}`, ydoc);
        providerRef.current = provider;
        awarenessRef.current = provider.awareness;

        // IndexedDB persistence
        if (config.enablePersistence) {
            const persistence = new IndexeddbPersistence(`physics-${roomId}`, ydoc);
            persistenceRef.current = persistence;

            persistence.on('synced', () => {
                setCollaborationState((prev) => ({
                    ...prev,
                    syncStatus: 'synced',
                }));
            });
        }

        // Set up awareness (user presence)
        provider.awareness.setLocalStateField('user', {
            id: userId,
            name: userName,
            color: collaborationState.currentUser.color,
        });

        // Handle connection status
        provider.on('status', (event: { status: string }) => {
            setCollaborationState((prev) => ({
                ...prev,
                isConnected: event.status === 'connected',
                syncStatus: event.status === 'connected' ? 'syncing' : 'offline',
            }));
        });

        // Handle awareness changes (user cursors, selections)
        provider.awareness.on('change', () => {
            const states = provider.awareness.getStates();
            const users: Record<string, CollaborationUser> = {};

            states.forEach((state: any, _clientId: number) => {
                if (state.user && state.user.id !== userId) {
                    users[state.user.id] = {
                        id: state.user.id,
                        name: state.user.name,
                        color: state.user.color,
                        cursor: state.cursor,
                        selectedEntityId: state.selectedEntityId,
                        lastSeen: Date.now(),
                        isOnline: true,
                    };
                }
            });

            setCollaborationState((prev) => ({
                ...prev,
                users,
            }));
        });

        // Sync entities from Yjs to local state
        const syncFromYjs = () => {
            const remoteEntities = yEntities.toArray();
            const remoteConfig = yPhysicsConfig.toJSON() as PhysicsConfig | undefined;

            if (remoteEntities.length > 0 || remoteConfig) {
                loadEntities({
                    entities: remoteEntities,
                    physics: remoteConfig,
                });
            }
        };

        // Listen for remote changes
        yEntities.observe(syncFromYjs);
        yPhysicsConfig.observe(syncFromYjs);

        // Initial sync
        syncFromYjs();

        return () => {
            provider.disconnect();
            provider.destroy();
            ydoc.destroy();
            persistenceRef.current?.destroy();
        };
    }, [roomId, userId, userName, config.serverUrl, config.enablePersistence, loadEntities]);

    // Sync local entities to Yjs when they change
    useEffect(() => {
        if (!ydocRef.current) return;

        const yEntities = ydocRef.current.getArray<PhysicsEntity>('entities');
        const yPhysicsConfig = ydocRef.current.getMap<any>('physicsConfig');

        // Batch update to Yjs
        ydocRef.current.transact(() => {
            // Clear and repopulate entities
            yEntities.delete(0, yEntities.length);
            yEntities.push(entities);

            // Update physics config
            Object.entries(physicsConfig).forEach(([key, value]) => {
                yPhysicsConfig.set(key, value);
            });
        });
    }, [entities, physicsConfig]);

    // Update cursor position
    const updateCursor = useCallback((x: number, y: number) => {
        if (awarenessRef.current) {
            awarenessRef.current.setLocalStateField('cursor', { x, y });
        }
    }, []);

    // Update selection
    const updateSelection = useCallback((entityId: string | null) => {
        if (awarenessRef.current) {
            awarenessRef.current.setLocalStateField('selectedEntityId', entityId);
        }
    }, []);

    return {
        ...collaborationState,
        updateCursor,
        updateSelection,
    };
}
