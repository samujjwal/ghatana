/**
 * Phase 3: Advanced Collaboration Features
 * Real-time collaboration, operational transforms, and conflict resolution
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';

import type { BaseItem } from '../core/types';

// Operational Transform types
/**
 *
 */
export type OperationType = 'create' | 'update' | 'delete' | 'move' | 'transform';

/**
 *
 */
export interface Operation<T extends BaseItem> {
    id: string;
    type: OperationType;
    itemId: string;
    data: Partial<T> | null;
    position?: { x: number; y: number };
    timestamp: number;
    userId: string;
    clientId: string;
    sequenceNumber: number;
}

/**
 *
 */
export interface CollaborationState<T extends BaseItem> {
    operations: Operation<T>[];
    cursors: Map<string, UserCursor>;
    selections: Map<string, string[]>;
    users: Map<string, CollaborationUser>;
    conflictResolution: 'last-write-wins' | 'operational-transform';
}

/**
 *
 */
export interface UserCursor {
    userId: string;
    position: { x: number; y: number };
    color: string;
    timestamp: number;
}

/**
 *
 */
export interface CollaborationUser {
    id: string;
    name: string;
    avatar?: string;
    color: string;
    isOnline: boolean;
    lastSeen: number;
}

// Operational Transform engine
/**
 *
 */
export class OperationalTransform<T extends BaseItem> {
    private operations: Operation<T>[] = [];
    private sequenceNumber = 0;

    /**
     * Apply operation and return transformed operations
     */
    applyOperation(
        operation: Operation<T>,
        concurrentOps: Operation<T>[]
    ): {
        transformedOp: Operation<T>;
        transformedConcurrent: Operation<T>[]
    } {
        // Transform operation against concurrent operations
        let transformedOp = { ...operation };
        const transformedConcurrent: Operation<T>[] = [];

        concurrentOps.forEach(concurrentOp => {
            const result = this.transformOperationPair(transformedOp, concurrentOp);
            transformedOp = result.op1;
            transformedConcurrent.push(result.op2);
        });

        this.operations.push(transformedOp);
        this.sequenceNumber++;

        return { transformedOp, transformedConcurrent };
    }

    /**
     * Transform two operations against each other
     */
    private transformOperationPair(
        op1: Operation<T>,
        op2: Operation<T>
    ): { op1: Operation<T>; op2: Operation<T> } {
        // Same item operations need special handling
        if (op1.itemId === op2.itemId) {
            return this.transformSameItem(op1, op2);
        }

        // Different items - operations are independent
        return { op1, op2 };
    }

    /**
     * Transform operations on the same item
     */
    private transformSameItem(
        op1: Operation<T>,
        op2: Operation<T>
    ): { op1: Operation<T>; op2: Operation<T> } {
        // Delete operations always win
        if (op1.type === 'delete') {
            return { op1, op2: { ...op2, type: 'delete' as OperationType, data: null } };
        }
        if (op2.type === 'delete') {
            return { op1: { ...op1, type: 'delete' as OperationType, data: null }, op2 };
        }

        // Update operations - merge data
        if (op1.type === 'update' && op2.type === 'update') {
            const mergedData = { ...op2.data, ...op1.data };
            return {
                op1: { ...op1, data: mergedData },
                op2: { ...op2, data: mergedData }
            };
        }

        // Position operations - resolve conflicts
        if (op1.type === 'move' && op2.type === 'move') {
            // Use timestamp as tiebreaker
            if (op1.timestamp > op2.timestamp) {
                return { op1, op2: { ...op2, position: op1.position } };
            } else {
                return { op1: { ...op1, position: op2.position }, op2 };
            }
        }

        return { op1, op2 };
    }

    /**
     *
     */
    getOperationHistory(): Operation<T>[] {
        return [...this.operations];
    }

    /**
     *
     */
    reset() {
        this.operations = [];
        this.sequenceNumber = 0;
    }
}

// Collaboration state management hook
/**
 *
 */
export function useCollaboration<T extends BaseItem>(
    currentUserId: string,
    clientId: string,
    initialItems: T[] = []
): {
    collaborationState: CollaborationState<T>;
    sendOperation: (operation: Omit<Operation<T>, 'id' | 'timestamp' | 'userId' | 'clientId' | 'sequenceNumber'>) => void;
    receiveOperation: (operation: Operation<T>) => void;
    updateCursor: (position: { x: number; y: number }) => void;
    updateSelection: (selectedIds: string[]) => void;
    addUser: (user: CollaborationUser) => void;
    removeUser: (userId: string) => void;
    resolveConflicts: (items: T[], operations: Operation<T>[]) => T[];
} {
    const [state, setState] = useState<CollaborationState<T>>({
        operations: [],
        cursors: new Map(),
        selections: new Map(),
        users: new Map(),
        conflictResolution: 'operational-transform'
    });

    const otEngine = useRef(new OperationalTransform<T>());
    const sequenceNumber = useRef(0);

    const sendOperation = useCallback((
        opData: Omit<Operation<T>, 'id' | 'timestamp' | 'userId' | 'clientId' | 'sequenceNumber'>
    ) => {
        const operation: Operation<T> = {
            id: `${clientId}-${sequenceNumber.current++}`,
            timestamp: Date.now(),
            userId: currentUserId,
            clientId,
            sequenceNumber: sequenceNumber.current,
            ...opData
        };

        // Apply operation locally
        const result = otEngine.current.applyOperation(operation, []);

        setState(prev => ({
            ...prev,
            operations: [...prev.operations, result.transformedOp]
        }));

        // In real implementation, send to collaboration server
        console.log('Sending operation:', operation);
    }, [currentUserId, clientId]);

    const receiveOperation = useCallback((operation: Operation<T>) => {
        // Don't process our own operations
        if (operation.userId === currentUserId) return;

        // Get concurrent operations
        const concurrentOps = state.operations.filter(op =>
            op.timestamp >= operation.timestamp && op.userId !== operation.userId
        );

        // Transform received operation
        const result = otEngine.current.applyOperation(operation, concurrentOps);

        setState(prev => ({
            ...prev,
            operations: [...prev.operations.filter(op => !concurrentOps.includes(op)), result.transformedOp, ...result.transformedConcurrent]
        }));
    }, [state.operations, currentUserId]);

    const updateCursor = useCallback((position: { x: number; y: number }) => {
        const cursor: UserCursor = {
            userId: currentUserId,
            position,
            color: state.users.get(currentUserId)?.color || '#007acc',
            timestamp: Date.now()
        };

        setState(prev => ({
            ...prev,
            cursors: new Map(prev.cursors).set(currentUserId, cursor)
        }));
    }, [currentUserId, state.users]);

    const updateSelection = useCallback((selectedIds: string[]) => {
        setState(prev => ({
            ...prev,
            selections: new Map(prev.selections).set(currentUserId, selectedIds)
        }));
    }, [currentUserId]);

    const addUser = useCallback((user: CollaborationUser) => {
        setState(prev => ({
            ...prev,
            users: new Map(prev.users).set(user.id, user)
        }));
    }, []);

    const removeUser = useCallback((userId: string) => {
        setState(prev => {
            const newCursors = new Map(prev.cursors);
            const newSelections = new Map(prev.selections);
            const newUsers = new Map(prev.users);

            newCursors.delete(userId);
            newSelections.delete(userId);
            newUsers.delete(userId);

            return {
                ...prev,
                cursors: newCursors,
                selections: newSelections,
                users: newUsers
            };
        });
    }, []);

    const resolveConflicts = useCallback((items: T[], operations: Operation<T>[]): T[] => {
        if (state.conflictResolution === 'last-write-wins') {
            return items; // Simple approach - just use current state
        }

        // Apply operational transforms
        let resolvedItems = [...items];

        operations
            .sort((a, b) => a.timestamp - b.timestamp)
            .forEach(operation => {
                switch (operation.type) {
                    case 'create':
                        if (operation.data && !resolvedItems.find(item => item.id === operation.itemId)) {
                            resolvedItems.push(operation.data as T);
                        }
                        break;

                    case 'update':
                        resolvedItems = resolvedItems.map(item =>
                            item.id === operation.itemId
                                ? { ...item, ...operation.data } as T
                                : item
                        );
                        break;

                    case 'delete':
                        resolvedItems = resolvedItems.filter(item => item.id !== operation.itemId);
                        break;

                    case 'move':
                        if (operation.position) {
                            resolvedItems = resolvedItems.map(item =>
                                item.id === operation.itemId
                                    ? { ...item, position: operation.position } as T
                                    : item
                            );
                        }
                        break;
                }
            });

        return resolvedItems;
    }, [state.conflictResolution]);

    return {
        collaborationState: state,
        sendOperation,
        receiveOperation,
        updateCursor,
        updateSelection,
        addUser,
        removeUser,
        resolveConflicts
    };
}

// Collaboration UI components
export const CollaborationCursors: React.FC<{
    cursors: Map<string, UserCursor>;
    currentUserId: string;
}> = ({ cursors, currentUserId }) => {
    return (
        <>
            {Array.from(cursors.entries())
                .filter(([userId]) => userId !== currentUserId)
                .map(([userId, cursor]) => (
                    <div
                        key={userId}
                        style={{
                            position: 'absolute',
                            left: cursor.position.x,
                            top: cursor.position.y,
                            pointerEvents: 'none',
                            zIndex: 1000
                        }}
                    >
                        <svg width="20" height="20" viewBox="0 0 20 20">
                            <path
                                d="M0 0L0 16L5 12L8 20L12 18L8 10L16 10Z"
                                fill={cursor.color}
                                stroke="white"
                                strokeWidth="1"
                            />
                        </svg>
                    </div>
                ))}
        </>
    );
};

export const CollaborationUserList: React.FC<{
    users: Map<string, CollaborationUser>;
    currentUserId: string;
}> = ({ users, currentUserId }) => {
    return (
        <div style={{
            position: 'fixed',
            top: 10,
            left: 10,
            background: 'white',
            border: '1px solid #ddd',
            borderRadius: '4px',
            padding: '8px',
            maxWidth: '200px'
        }}>
            <div style={{ fontSize: '12px', fontWeight: 'bold', marginBottom: '4px' }}>
                Collaborators ({users.size})
            </div>
            {Array.from(users.values()).map(user => (
                <div
                    key={user.id}
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        marginBottom: '4px',
                        fontSize: '12px'
                    }}
                >
                    <div
                        style={{
                            width: '8px',
                            height: '8px',
                            borderRadius: '50%',
                            backgroundColor: user.color,
                            marginRight: '6px'
                        }}
                    />
                    <span style={{
                        fontWeight: user.id === currentUserId ? 'bold' : 'normal'
                    }}>
                        {user.name} {user.id === currentUserId && '(you)'}
                    </span>
                    {!user.isOnline && (
                        <span style={{ color: '#888', marginLeft: '4px' }}>
                            (offline)
                        </span>
                    )}
                </div>
            ))}
        </div>
    );
};

// Mock collaboration server for demo
/**
 *
 */
export class MockCollaborationServer<T extends BaseItem> {
    private clients: Map<string, {
        clientId: string;
        userId: string;
        onOperation: (op: Operation<T>) => void;
        onCursor: (cursor: UserCursor) => void;
        onUserJoin: (user: CollaborationUser) => void;
        onUserLeave: (userId: string) => void;
    }> = new Map();

    /**
     *
     */
    connect(
        clientId: string,
        userId: string,
        callbacks: {
            onOperation: (op: Operation<T>) => void;
            onCursor: (cursor: UserCursor) => void;
            onUserJoin: (user: CollaborationUser) => void;
            onUserLeave: (userId: string) => void;
        }
    ) {
        this.clients.set(clientId, { clientId, userId, ...callbacks });

        // Notify other clients of new user
        const user: CollaborationUser = {
            id: userId,
            name: `User ${userId.slice(0, 8)}`,
            color: this.generateUserColor(userId),
            isOnline: true,
            lastSeen: Date.now()
        };

        this.broadcast('onUserJoin', user, clientId);
    }

    /**
     *
     */
    disconnect(clientId: string) {
        const client = this.clients.get(clientId);
        if (client) {
            this.broadcast('onUserLeave', client.userId, clientId);
            this.clients.delete(clientId);
        }
    }

    /**
     *
     */
    sendOperation(clientId: string, operation: Operation<T>) {
        // Broadcast to all other clients
        this.broadcast('onOperation', operation, clientId);
    }

    /**
     *
     */
    sendCursor(clientId: string, cursor: UserCursor) {
        // Broadcast to all other clients
        this.broadcast('onCursor', cursor, clientId);
    }

    /**
     *
     */
    private broadcast<K extends keyof typeof this.clients.values>(
        method: 'onOperation' | 'onCursor' | 'onUserJoin' | 'onUserLeave',
        data: unknown,
        excludeClient?: string
    ) {
        this.clients.forEach((client, clientId) => {
            if (clientId !== excludeClient) {
                client[method](data);
            }
        });
    }

    /**
     *
     */
    private generateUserColor(userId: string): string {
        const colors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7', '#DDA0DD', '#98D8C8'];
        const hash = userId.split('').reduce((a, b) => a + b.charCodeAt(0), 0);
        return colors[hash % colors.length];
    }

    /**
     *
     */
    getConnectedUsers(): CollaborationUser[] {
        return Array.from(this.clients.values()).map(client => ({
            id: client.userId,
            name: `User ${client.userId.slice(0, 8)}`,
            color: this.generateUserColor(client.userId),
            isOnline: true,
            lastSeen: Date.now()
        }));
    }
}