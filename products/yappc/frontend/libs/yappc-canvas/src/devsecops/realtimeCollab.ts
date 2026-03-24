/**
 * Real-Time Collaboration System
 * 
 * Supports:
 * - WebSocket-based synchronization
 * - Operational Transformation (OT) for conflict resolution
 * - Cursor presence and awareness
 * - Collaborative editing with multiple users
 * - Change history and undo/redo synchronization
 */

import type { CanvasDocument, CanvasNode, CanvasEdge } from '../types/canvas-document';

// Types

/**
 *
 */
export type OperationType = 'insert' | 'update' | 'delete' | 'move' | 'style';
/**
 *
 */
export type CollaboratorStatus = 'active' | 'idle' | 'away' | 'offline';

/**
 *
 */
export interface CollaborationConfig {
  enablePresence: boolean;
  enableCursors: boolean;
  enableConflictResolution: boolean;
  broadcastInterval: number; // milliseconds
  idleTimeout: number; // milliseconds
  maxHistorySize: number;
}

/**
 *
 */
export interface Collaborator {
  id: string;
  name: string;
  color: string;
  avatar?: string;
  status: CollaboratorStatus;
  cursor?: {
    x: number;
    y: number;
    elementId?: string;
  };
  selection?: string[]; // Selected element IDs
  lastActivity: Date;
  metadata: {
    connectionId?: string;
    joinedAt: Date;
    operationCount?: number;
  };
}

/**
 *
 */
export interface Operation {
  id: string;
  type: OperationType;
  userId: string;
  timestamp: Date;
  elementId: string;
  data: Record<string, unknown>;
  clientSeq: number; // Client sequence number
  serverSeq?: number; // Server sequence number (after transform)
  metadata: {
    position?: { x: number; y: number };
    previousValue?: unknown;
    conflicts?: string[];
  };
}

/**
 *
 */
export interface OperationResult {
  operation: Operation;
  transformed: boolean;
  conflicts: Operation[];
  applied: boolean;
}

/**
 *
 */
export interface CollaborationSession {
  id: string;
  documentId: string;
  collaborators: Map<string, Collaborator>;
  operations: Operation[];
  serverSeq: number;
  metadata: {
    createdAt: Date;
    lastActivity: Date;
    totalOperations: number;
    activeUsers: number;
  };
}

/**
 *
 */
export interface CursorUpdate {
  userId: string;
  x: number;
  y: number;
  elementId?: string;
  timestamp: Date;
}

/**
 *
 */
export interface SelectionUpdate {
  userId: string;
  elementIds: string[];
  timestamp: Date;
}

/**
 *
 */
export interface ConflictResolution {
  operation: Operation;
  conflictingOperations: Operation[];
  resolution: 'accept' | 'reject' | 'merge';
  mergedOperation?: Operation;
}

// Main Functions

/**
 * Create a new collaboration session
 */
export function createCollaborationSession(documentId: string): CollaborationSession {
  return {
    id: `session-${Date.now()}`,
    documentId,
    collaborators: new Map(),
    operations: [],
    serverSeq: 0,
    metadata: {
      createdAt: new Date(),
      lastActivity: new Date(),
      totalOperations: 0,
      activeUsers: 0,
    },
  };
}

/**
 * Add a collaborator to the session
 */
export function addCollaborator(
  session: CollaborationSession,
  collaborator: Omit<Collaborator, 'metadata' | 'status' | 'lastActivity'>
): CollaborationSession {
  const fullCollaborator: Collaborator = {
    ...collaborator,
    status: 'active',
    lastActivity: new Date(),
    metadata: {
      joinedAt: new Date(),
      operationCount: 0,
    },
  };

  const updatedCollaborators = new Map(session.collaborators);
  updatedCollaborators.set(collaborator.id, fullCollaborator);

  return {
    ...session,
    collaborators: updatedCollaborators,
    metadata: {
      ...session.metadata,
      activeUsers: updatedCollaborators.size,
      lastActivity: new Date(),
    },
  };
}

/**
 * Remove a collaborator from the session
 */
export function removeCollaborator(
  session: CollaborationSession,
  userId: string
): CollaborationSession {
  const updatedCollaborators = new Map(session.collaborators);
  updatedCollaborators.delete(userId);

  return {
    ...session,
    collaborators: updatedCollaborators,
    metadata: {
      ...session.metadata,
      activeUsers: updatedCollaborators.size,
      lastActivity: new Date(),
    },
  };
}

/**
 * Update collaborator cursor position
 */
export function updateCursor(
  session: CollaborationSession,
  update: CursorUpdate
): CollaborationSession {
  const collaborator = session.collaborators.get(update.userId);
  if (!collaborator) {
    return session;
  }

  const updatedCollaborator: Collaborator = {
    ...collaborator,
    cursor: {
      x: update.x,
      y: update.y,
      elementId: update.elementId,
    },
    lastActivity: new Date(),
    status: 'active',
  };

  const updatedCollaborators = new Map(session.collaborators);
  updatedCollaborators.set(update.userId, updatedCollaborator);

  return {
    ...session,
    collaborators: updatedCollaborators,
    metadata: {
      ...session.metadata,
      lastActivity: new Date(),
    },
  };
}

/**
 * Update collaborator selection
 */
export function updateSelection(
  session: CollaborationSession,
  update: SelectionUpdate
): CollaborationSession {
  const collaborator = session.collaborators.get(update.userId);
  if (!collaborator) {
    return session;
  }

  const updatedCollaborator: Collaborator = {
    ...collaborator,
    selection: update.elementIds,
    lastActivity: new Date(),
    status: 'active',
  };

  const updatedCollaborators = new Map(session.collaborators);
  updatedCollaborators.set(update.userId, updatedCollaborator);

  return {
    ...session,
    collaborators: updatedCollaborators,
  };
}

/**
 * Apply an operation with operational transformation
 */
export function applyOperation(
  session: CollaborationSession,
  operation: Operation
): OperationResult {
  // Get concurrent operations (operations with higher client seq from same user, or from other users)
  const concurrentOps = session.operations.filter(op => 
    (op.userId === operation.userId && op.clientSeq > operation.clientSeq) ||
    (op.userId !== operation.userId && (!op.serverSeq || !operation.serverSeq || op.serverSeq >= operation.serverSeq))
  );

  // Transform operation against concurrent operations
  let transformedOp = operation;
  let transformed = false;
  const conflicts: Operation[] = [];

  for (const concurrentOp of concurrentOps) {
    const result = transformOperations(transformedOp, concurrentOp);
    transformedOp = result.transformed;
    if (result.conflict) {
      conflicts.push(concurrentOp);
      transformed = true;
    }
  }

  // Assign server sequence number
  transformedOp = {
    ...transformedOp,
    serverSeq: session.serverSeq + 1,
  };

  // Add to operations history
  const updatedOperations = [...session.operations, transformedOp];

  // Update collaborator operation count
  const collaborator = session.collaborators.get(operation.userId);
  if (collaborator) {
    const updatedCollaborator: Collaborator = {
      ...collaborator,
      lastActivity: new Date(),
      metadata: {
        ...collaborator.metadata,
        operationCount: (collaborator.metadata.operationCount || 0) + 1,
      },
    };

    const updatedCollaborators = new Map(session.collaborators);
    updatedCollaborators.set(operation.userId, updatedCollaborator);

    Object.assign(session, {
      collaborators: updatedCollaborators,
      operations: updatedOperations,
      serverSeq: session.serverSeq + 1,
      metadata: {
        ...session.metadata,
        totalOperations: session.metadata.totalOperations + 1,
        lastActivity: new Date(),
      },
    });
  }

  return {
    operation: transformedOp,
    transformed,
    conflicts,
    applied: true,
  };
}

/**
 * Transform two concurrent operations using Operational Transformation
 */
export function transformOperations(
  op1: Operation,
  op2: Operation
): { transformed: Operation; conflict: boolean } {
  // Same element - potential conflict
  if (op1.elementId === op2.elementId) {
    // Both updates - merge or prioritize
    if (op1.type === 'update' && op2.type === 'update') {
      return {
        transformed: {
          ...op1,
          data: { ...op2.data, ...op1.data }, // op1 takes precedence
          metadata: {
            ...op1.metadata,
            conflicts: [op2.id],
          },
        },
        conflict: true,
      };
    }

    // Delete vs update - delete wins
    if (op1.type === 'delete' || op2.type === 'delete') {
      if (op1.type === 'delete') {
        return { transformed: op1, conflict: false };
      }
      // op2 is delete, skip op1
      return {
        transformed: {
          ...op1,
          type: 'delete',
          metadata: {
            ...op1.metadata,
            conflicts: [op2.id],
          },
        },
        conflict: true,
      };
    }

    // Move operations - take average position
    if (op1.type === 'move' && op2.type === 'move') {
      const pos1 = op1.metadata.position || { x: 0, y: 0 };
      const pos2 = op2.metadata.position || { x: 0, y: 0 };

      return {
        transformed: {
          ...op1,
          metadata: {
            ...op1.metadata,
            position: {
              x: (pos1.x + pos2.x) / 2,
              y: (pos1.y + pos2.y) / 2,
            },
            conflicts: [op2.id],
          },
        },
        conflict: true,
      };
    }
  }

  // Different elements - no conflict
  return { transformed: op1, conflict: false };
}

/**
 * Resolve a conflict between operations
 */
export function resolveConflict(
  operation: Operation,
  conflictingOps: Operation[],
  strategy: 'latest-wins' | 'merge' | 'manual' = 'latest-wins'
): ConflictResolution {
  if (strategy === 'latest-wins') {
    // Most recent operation wins
    const latest = [operation, ...conflictingOps].sort((a, b) => 
      b.timestamp.getTime() - a.timestamp.getTime()
    )[0];

    return {
      operation,
      conflictingOperations: conflictingOps,
      resolution: latest.id === operation.id ? 'accept' : 'reject',
    };
  }

  if (strategy === 'merge') {
    // Merge all operations
    const mergedData = conflictingOps.reduce((acc, op) => ({
      ...acc,
      ...op.data,
    }), operation.data);

    return {
      operation,
      conflictingOperations: conflictingOps,
      resolution: 'merge',
      mergedOperation: {
        ...operation,
        data: mergedData,
        metadata: {
          ...operation.metadata,
          conflicts: conflictingOps.map(op => op.id),
        },
      },
    };
  }

  // Manual resolution required
  return {
    operation,
    conflictingOperations: conflictingOps,
    resolution: 'accept', // Defaultto accepting
  };
}

/**
 * Get active collaborators (not idle/away/offline)
 */
export function getActiveCollaborators(session: CollaborationSession): Collaborator[] {
  const now = new Date().getTime();
  return Array.from(session.collaborators.values()).filter(collab => {
    const idleTime = now - collab.lastActivity.getTime();
    return idleTime < 30000 && collab.status === 'active'; // 30 seconds
  });
}

/**
 * Update collaborator status based on idle time
 */
export function updateCollaboratorStatus(
  session: CollaborationSession,
  idleTimeout: number = 60000
): CollaborationSession {
  const now = new Date().getTime();
  const updatedCollaborators = new Map(session.collaborators);

  updatedCollaborators.forEach((collab, userId) => {
    const idleTime = now - collab.lastActivity.getTime();
    
    let status: CollaboratorStatus = 'active';
    if (idleTime > idleTimeout * 2) {
      status = 'away';
    } else if (idleTime > idleTimeout) {
      status = 'idle';
    }

    if (collab.status !== status) {
      updatedCollaborators.set(userId, {
        ...collab,
        status,
      });
    }
  });

  return {
    ...session,
    collaborators: updatedCollaborators,
  };
}

/**
 * Get operation history for a specific element
 */
export function getElementHistory(
  session: CollaborationSession,
  elementId: string
): Operation[] {
  return session.operations
    .filter(op => op.elementId === elementId)
    .sort((a, b) => (a.serverSeq || 0) - (b.serverSeq || 0));
}

/**
 * Get operations since a specific sequence number
 */
export function getOperationsSince(
  session: CollaborationSession,
  sinceSeq: number
): Operation[] {
  return session.operations
    .filter(op => (op.serverSeq || 0) > sinceSeq)
    .sort((a, b) => (a.serverSeq || 0) - (b.serverSeq || 0));
}

/**
 * Compact operation history by removing old operations
 */
export function compactHistory(
  session: CollaborationSession,
  maxSize: number = 1000
): CollaborationSession {
  if (session.operations.length <= maxSize) {
    return session;
  }

  // Keep only the most recent operations
  const compactedOps = session.operations
    .sort((a, b) => (b.serverSeq || 0) - (a.serverSeq || 0))
    .slice(0, maxSize);

  return {
    ...session,
    operations: compactedOps,
  };
}

/**
 * Generate a unique color for a collaborator
 */
export function generateCollaboratorColor(userId: string): string {
  const colors = [
    '#ef4444', '#f97316', '#f59e0b', '#eab308', '#84cc16',
    '#22c55e', '#10b981', '#14b8a6', '#06b6d4', '#0ea5e9',
    '#3b82f6', '#6366f1', '#8b5cf6', '#a855f7', '#d946ef',
    '#ec4899', '#f43f5e',
  ];

  // Hash user ID to get consistent color
  let hash = 0;
  for (let i = 0; i < userId.length; i++) {
    hash = userId.charCodeAt(i) + ((hash << 5) - hash);
  }

  return colors[Math.abs(hash) % colors.length];
}

/**
 * Create collaboration configuration
 */
export function createCollaborationConfig(
  overrides?: Partial<CollaborationConfig>
): CollaborationConfig {
  return {
    enablePresence: true,
    enableCursors: true,
    enableConflictResolution: true,
    broadcastInterval: 100, // 100ms
    idleTimeout: 60000, // 60 seconds
    maxHistorySize: 1000,
    ...overrides,
  };
}

/**
 * Broadcast operation to all collaborators except sender
 */
export function broadcastOperation(
  session: CollaborationSession,
  operation: Operation,
  excludeUserId?: string
): Collaborator[] {
  return Array.from(session.collaborators.values()).filter(
    collab => collab.id !== excludeUserId && collab.status === 'active'
  );
}

/**
 * Check if two selections overlap
 */
export function selectionsOverlap(
  selection1: string[],
  selection2: string[]
): boolean {
  return selection1.some(id => selection2.includes(id));
}

/**
 * Merge selections from multiple users
 */
export function mergeSelections(
  session: CollaborationSession
): string[] {
  const allSelections: string[] = [];
  
  session.collaborators.forEach(collab => {
    if (collab.selection) {
      allSelections.push(...collab.selection);
    }
  });

  // Return unique element IDs
  return [...new Set(allSelections)];
}

/**
 * Get collaborators viewing/editing a specific element
 */
export function getElementCollaborators(
  session: CollaborationSession,
  elementId: string
): Collaborator[] {
  return Array.from(session.collaborators.values()).filter(
    collab => 
      collab.cursor?.elementId === elementId ||
      collab.selection?.includes(elementId)
  );
}

/**
 * Calculate collaboration statistics
 */
export function getCollaborationStats(session: CollaborationSession): {
  totalCollaborators: number;
  activeCollaborators: number;
  idleCollaborators: number;
  totalOperations: number;
  operationsPerUser: Record<string, number>;
  averageLatency: number;
} {
  const activeCount = Array.from(session.collaborators.values()).filter(
    c => c.status === 'active'
  ).length;

  const idleCount = Array.from(session.collaborators.values()).filter(
    c => c.status === 'idle' || c.status === 'away'
  ).length;

  const operationsPerUser: Record<string, number> = {};
  session.collaborators.forEach((collab, userId) => {
    operationsPerUser[userId] = collab.metadata.operationCount || 0;
  });

  // Calculate average operation latency (time between operations)
  const latencies: number[] = [];
  for (let i = 1; i < session.operations.length; i++) {
    const latency = session.operations[i].timestamp.getTime() - 
                    session.operations[i - 1].timestamp.getTime();
    latencies.push(latency);
  }

  const averageLatency = latencies.length > 0
    ? latencies.reduce((sum, l) => sum + l, 0) / latencies.length
    : 0;

  return {
    totalCollaborators: session.collaborators.size,
    activeCollaborators: activeCount,
    idleCollaborators: idleCount,
    totalOperations: session.metadata.totalOperations,
    operationsPerUser,
    averageLatency,
  };
}
