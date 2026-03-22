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
/**
 * Collaboration user with cursor and selection state
 */
export interface CollaborationUser {
    id: string;
    name: string;
    color: string;
    cursor?: {
        x: number;
        y: number;
    };
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
/**
 * Hook for Yjs-based real-time collaboration in physics simulations
 */
export declare function usePhysicsCollaboration(roomId: string, userId: string, userName: string, options?: UsePhysicsCollaborationOptions): PhysicsCollaborationState & {
    updateCursor: (x: number, y: number) => void;
    updateSelection: (entityId: string | null) => void;
};
//# sourceMappingURL=usePhysicsCollaboration.d.ts.map