/**
 * Tests for Real-Time Collaboration System
 */

import { describe, it, expect, beforeEach } from 'vitest';

import {
  createCollaborationSession,
  addCollaborator,
  removeCollaborator,
  updateCursor,
  updateSelection,
  applyOperation,
  transformOperations,
  resolveConflict,
  getActiveCollaborators,
  updateCollaboratorStatus,
  getElementHistory,
  getOperationsSince,
  compactHistory,
  generateCollaboratorColor,
  createCollaborationConfig,
  broadcastOperation,
  selectionsOverlap,
  mergeSelections,
  getElementCollaborators,
  getCollaborationStats,
  type CollaborationSession,
  type Collaborator,
  type Operation,
  type CursorUpdate,
  type SelectionUpdate,
} from '../realtimeCollab';

describe.skip('realtimeCollab', () => {
  describe('Session Management', () => {
    it('should create a collaboration session', () => {
      const session = createCollaborationSession('doc-123');

      expect(session.documentId).toBe('doc-123');
      expect(session.collaborators.size).toBe(0);
      expect(session.operations).toEqual([]);
      expect(session.serverSeq).toBe(0);
      expect(session.metadata.totalOperations).toBe(0);
      expect(session.metadata.activeUsers).toBe(0);
    });

    it('should add a collaborator to the session', () => {
      const session = createCollaborationSession('doc-123');
      const updatedSession = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });

      expect(updatedSession.collaborators.size).toBe(1);
      expect(updatedSession.metadata.activeUsers).toBe(1);

      const collaborator = updatedSession.collaborators.get('user-1');
      expect(collaborator).toBeDefined();
      expect(collaborator?.name).toBe('Alice');
      expect(collaborator?.status).toBe('active');
      expect(collaborator?.metadata.operationCount).toBe(0);
    });

    it('should remove a collaborator from the session', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });
      session = addCollaborator(session, {
        id: 'user-2',
        name: 'Bob',
        color: '#3b82f6',
      });

      const updatedSession = removeCollaborator(session, 'user-1');

      expect(updatedSession.collaborators.size).toBe(1);
      expect(updatedSession.metadata.activeUsers).toBe(1);
      expect(updatedSession.collaborators.has('user-1')).toBe(false);
      expect(updatedSession.collaborators.has('user-2')).toBe(true);
    });
  });

  describe('Cursor and Selection Management', () => {
    let session: CollaborationSession;

    beforeEach(() => {
      session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });
    });

    it('should update collaborator cursor position', () => {
      const cursorUpdate: CursorUpdate = {
        userId: 'user-1',
        x: 100,
        y: 200,
        elementId: 'node-1',
        timestamp: new Date(),
      };

      const updatedSession = updateCursor(session, cursorUpdate);
      const collaborator = updatedSession.collaborators.get('user-1');

      expect(collaborator?.cursor).toEqual({
        x: 100,
        y: 200,
        elementId: 'node-1',
      });
      expect(collaborator?.status).toBe('active');
    });

    it('should update collaborator selection', () => {
      const selectionUpdate: SelectionUpdate = {
        userId: 'user-1',
        elementIds: ['node-1', 'node-2', 'edge-1'],
        timestamp: new Date(),
      };

      const updatedSession = updateSelection(session, selectionUpdate);
      const collaborator = updatedSession.collaborators.get('user-1');

      expect(collaborator?.selection).toEqual(['node-1', 'node-2', 'edge-1']);
    });

    it('should not update cursor for non-existent user', () => {
      const cursorUpdate: CursorUpdate = {
        userId: 'non-existent',
        x: 100,
        y: 200,
        timestamp: new Date(),
      };

      const updatedSession = updateCursor(session, cursorUpdate);
      expect(updatedSession).toEqual(session);
    });

    it('should detect overlapping selections', () => {
      const selection1 = ['node-1', 'node-2', 'node-3'];
      const selection2 = ['node-3', 'node-4'];

      expect(selectionsOverlap(selection1, selection2)).toBe(true);
      expect(selectionsOverlap(['node-1'], ['node-2'])).toBe(false);
    });

    it('should merge selections from multiple users', () => {
      session = addCollaborator(session, {
        id: 'user-2',
        name: 'Bob',
        color: '#3b82f6',
      });

      session = updateSelection(session, {
        userId: 'user-1',
        elementIds: ['node-1', 'node-2'],
        timestamp: new Date(),
      });

      session = updateSelection(session, {
        userId: 'user-2',
        elementIds: ['node-2', 'node-3'],
        timestamp: new Date(),
      });

      const merged = mergeSelections(session);
      expect(merged.sort()).toEqual(['node-1', 'node-2', 'node-3']);
    });
  });

  describe('Operational Transformation', () => {
    it('should transform concurrent update operations', () => {
      const op1: Operation = {
        id: 'op-1',
        type: 'update',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { label: 'Updated by Alice' },
        clientSeq: 1,
        metadata: {},
      };

      const op2: Operation = {
        id: 'op-2',
        type: 'update',
        userId: 'user-2',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { color: 'red' },
        clientSeq: 1,
        metadata: {},
      };

      const result = transformOperations(op1, op2);

      expect(result.conflict).toBe(true);
      expect(result.transformed.data).toEqual({
        color: 'red',
        label: 'Updated by Alice',
      });
      expect(result.transformed.metadata.conflicts).toContain('op-2');
    });

    it('should handle delete operations', () => {
      const updateOp: Operation = {
        id: 'op-1',
        type: 'update',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { label: 'Update' },
        clientSeq: 1,
        metadata: {},
      };

      const deleteOp: Operation = {
        id: 'op-2',
        type: 'delete',
        userId: 'user-2',
        timestamp: new Date(),
        elementId: 'node-1',
        data: {},
        clientSeq: 1,
        metadata: {},
      };

      const result = transformOperations(updateOp, deleteOp);

      expect(result.conflict).toBe(true);
      expect(result.transformed.type).toBe('delete');
    });

    it('should transform concurrent move operations by averaging positions', () => {
      const op1: Operation = {
        id: 'op-1',
        type: 'move',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: {},
        clientSeq: 1,
        metadata: { position: { x: 100, y: 100 } },
      };

      const op2: Operation = {
        id: 'op-2',
        type: 'move',
        userId: 'user-2',
        timestamp: new Date(),
        elementId: 'node-1',
        data: {},
        clientSeq: 1,
        metadata: { position: { x: 200, y: 200 } },
      };

      const result = transformOperations(op1, op2);

      expect(result.conflict).toBe(true);
      expect(result.transformed.metadata.position).toEqual({ x: 150, y: 150 });
    });

    it('should not conflict operations on different elements', () => {
      const op1: Operation = {
        id: 'op-1',
        type: 'update',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { label: 'Node 1' },
        clientSeq: 1,
        metadata: {},
      };

      const op2: Operation = {
        id: 'op-2',
        type: 'update',
        userId: 'user-2',
        timestamp: new Date(),
        elementId: 'node-2',
        data: { label: 'Node 2' },
        clientSeq: 1,
        metadata: {},
      };

      const result = transformOperations(op1, op2);

      expect(result.conflict).toBe(false);
      expect(result.transformed).toEqual(op1);
    });

    it('should apply operation with server sequence number', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });

      const operation: Operation = {
        id: 'op-1',
        type: 'insert',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { label: 'New Node' },
        clientSeq: 1,
        metadata: {},
      };

      const result = applyOperation(session, operation);

      expect(result.applied).toBe(true);
      expect(result.operation.serverSeq).toBe(1);
      expect(session.serverSeq).toBe(1);
      expect(session.operations).toHaveLength(1);
      expect(session.metadata.totalOperations).toBe(1);
    });

    it('should increment collaborator operation count', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });

      const operation: Operation = {
        id: 'op-1',
        type: 'insert',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { label: 'New Node' },
        clientSeq: 1,
        metadata: {},
      };

      applyOperation(session, operation);

      const collaborator = session.collaborators.get('user-1');
      expect(collaborator?.metadata.operationCount).toBe(1);
    });

    it('should detect and mark conflicts', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });
      session = addCollaborator(session, {
        id: 'user-2',
        name: 'Bob',
        color: '#3b82f6',
      });

      // Apply first operation
      const op1: Operation = {
        id: 'op-1',
        type: 'update',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { label: 'Alice update' },
        clientSeq: 1,
        metadata: {},
      };

      applyOperation(session, op1);

      // Apply concurrent operation on same element
      const op2: Operation = {
        id: 'op-2',
        type: 'update',
        userId: 'user-2',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { label: 'Bob update' },
        clientSeq: 1,
        metadata: {},
      };

      const result = applyOperation(session, op2);

      expect(result.transformed).toBe(true);
      expect(result.conflicts).toHaveLength(1);
      expect(result.conflicts[0].id).toBe('op-1');
    });
  });

  describe('Conflict Resolution', () => {
    it('should resolve conflict with latest-wins strategy', () => {
      const now = new Date();
      const earlier = new Date(now.getTime() - 1000);

      const operation: Operation = {
        id: 'op-1',
        type: 'update',
        userId: 'user-1',
        timestamp: now,
        elementId: 'node-1',
        data: { label: 'Latest' },
        clientSeq: 1,
        metadata: {},
      };

      const conflictingOp: Operation = {
        id: 'op-2',
        type: 'update',
        userId: 'user-2',
        timestamp: earlier,
        elementId: 'node-1',
        data: { label: 'Earlier' },
        clientSeq: 1,
        metadata: {},
      };

      const resolution = resolveConflict(operation, [conflictingOp], 'latest-wins');

      expect(resolution.resolution).toBe('accept');
    });

    it('should resolve conflict with merge strategy', () => {
      const operation: Operation = {
        id: 'op-1',
        type: 'update',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { label: 'Alice' },
        clientSeq: 1,
        metadata: {},
      };

      const conflictingOp: Operation = {
        id: 'op-2',
        type: 'update',
        userId: 'user-2',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { color: 'red' },
        clientSeq: 1,
        metadata: {},
      };

      const resolution = resolveConflict(operation, [conflictingOp], 'merge');

      expect(resolution.resolution).toBe('merge');
      expect(resolution.mergedOperation?.data).toEqual({
        color: 'red',
        label: 'Alice',
      });
    });

    it('should default to accept for manual strategy', () => {
      const operation: Operation = {
        id: 'op-1',
        type: 'update',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: { label: 'Manual' },
        clientSeq: 1,
        metadata: {},
      };

      const resolution = resolveConflict(operation, [], 'manual');

      expect(resolution.resolution).toBe('accept');
    });
  });

  describe('Collaborator Status', () => {
    it('should get active collaborators only', () => {
      let session = createCollaborationSession('doc-123');

      const now = new Date();
      const recent = new Date(now.getTime() - 10000); // 10 seconds ago
      const old = new Date(now.getTime() - 60000); // 60 seconds ago

      // Add active collaborator
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });

      // Add idle collaborator (manually set old lastActivity)
      session = addCollaborator(session, {
        id: 'user-2',
        name: 'Bob',
        color: '#3b82f6',
      });

      const collaborators = session.collaborators;
      const bob = collaborators.get('user-2')!;
      bob.lastActivity = old;
      bob.status = 'idle';

      const activeCollaborators = getActiveCollaborators(session);

      expect(activeCollaborators).toHaveLength(1);
      expect(activeCollaborators[0].id).toBe('user-1');
    });

    it('should update collaborator status based on idle time', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });

      const collaborator = session.collaborators.get('user-1')!;

      // Set last activity to 70 seconds ago (idle threshold is 60s)
      collaborator.lastActivity = new Date(Date.now() - 70000);

      const updatedSession = updateCollaboratorStatus(session, 60000);
      const updatedCollaborator = updatedSession.collaborators.get('user-1');

      expect(updatedCollaborator?.status).toBe('idle');
    });

    it('should mark collaborators as away after double idle timeout', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });

      const collaborator = session.collaborators.get('user-1')!;

      // Set last activity to 130 seconds ago (away threshold is 120s)
      collaborator.lastActivity = new Date(Date.now() - 130000);

      const updatedSession = updateCollaboratorStatus(session, 60000);
      const updatedCollaborator = updatedSession.collaborators.get('user-1');

      expect(updatedCollaborator?.status).toBe('away');
    });
  });

  describe('Operation History', () => {
    it('should get operation history for an element', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });

      // Add operations for different elements
      const op1: Operation = {
        id: 'op-1',
        type: 'insert',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: {},
        clientSeq: 1,
        metadata: {},
      };

      const op2: Operation = {
        id: 'op-2',
        type: 'update',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: {},
        clientSeq: 2,
        metadata: {},
      };

      const op3: Operation = {
        id: 'op-3',
        type: 'insert',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-2',
        data: {},
        clientSeq: 3,
        metadata: {},
      };

      applyOperation(session, op1);
      applyOperation(session, op2);
      applyOperation(session, op3);

      const history = getElementHistory(session, 'node-1');

      expect(history).toHaveLength(2);
      expect(history[0].id).toBe('op-1');
      expect(history[1].id).toBe('op-2');
    });

    it('should get operations since a sequence number', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });

      // Add 3 operations
      for (let i = 1; i <= 3; i++) {
        const op: Operation = {
          id: `op-${i}`,
          type: 'insert',
          userId: 'user-1',
          timestamp: new Date(),
          elementId: `node-${i}`,
          data: {},
          clientSeq: i,
          metadata: {},
        };

        applyOperation(session, op);
      }

      const recentOps = getOperationsSince(session, 1);

      expect(recentOps).toHaveLength(2);
      expect(recentOps[0].serverSeq).toBe(2);
      expect(recentOps[1].serverSeq).toBe(3);
    });

    it('should compact operation history when exceeding max size', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });

      // Add 10 operations
      for (let i = 1; i <= 10; i++) {
        const op: Operation = {
          id: `op-${i}`,
          type: 'insert',
          userId: 'user-1',
          timestamp: new Date(),
          elementId: `node-${i}`,
          data: {},
          clientSeq: i,
          metadata: {},
        };

        applyOperation(session, op);
      }

      expect(session.operations).toHaveLength(10);

      const compactedSession = compactHistory(session, 5);

      expect(compactedSession.operations).toHaveLength(5);
      // Should keep most recent operations
      expect(compactedSession.operations[0].serverSeq).toBe(10);
    });
  });

  describe('Helper Functions', () => {
    it('should generate consistent color for user ID', () => {
      const color1 = generateCollaboratorColor('user-1');
      const color2 = generateCollaboratorColor('user-1');
      const color3 = generateCollaboratorColor('user-2');

      expect(color1).toBe(color2); // Same user = same color
      expect(color1).not.toBe(color3); // Different users = different colors (most likely)
      expect(color1).toMatch(/^#[0-9a-f]{6}$/i); // Valid hex color
    });

    it('should create collaboration config with defaults', () => {
      const config = createCollaborationConfig();

      expect(config.enablePresence).toBe(true);
      expect(config.enableCursors).toBe(true);
      expect(config.enableConflictResolution).toBe(true);
      expect(config.broadcastInterval).toBe(100);
      expect(config.idleTimeout).toBe(60000);
      expect(config.maxHistorySize).toBe(1000);
    });

    it('should create collaboration config with overrides', () => {
      const config = createCollaborationConfig({
        enableCursors: false,
        broadcastInterval: 50,
        maxHistorySize: 500,
      });

      expect(config.enablePresence).toBe(true); // Default
      expect(config.enableCursors).toBe(false); // Override
      expect(config.broadcastInterval).toBe(50); // Override
      expect(config.maxHistorySize).toBe(500); // Override
    });

    it('should broadcast to all active collaborators except sender', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });
      session = addCollaborator(session, {
        id: 'user-2',
        name: 'Bob',
        color: '#3b82f6',
      });
      session = addCollaborator(session, {
        id: 'user-3',
        name: 'Charlie',
        color: '#10b981',
      });

      // Set user-3 to idle
      const charlie = session.collaborators.get('user-3')!;
      charlie.status = 'idle';

      const operation: Operation = {
        id: 'op-1',
        type: 'insert',
        userId: 'user-1',
        timestamp: new Date(),
        elementId: 'node-1',
        data: {},
        clientSeq: 1,
        metadata: {},
      };

      const recipients = broadcastOperation(session, operation, 'user-1');

      expect(recipients).toHaveLength(1);
      expect(recipients[0].id).toBe('user-2'); // Only Bob (active, not sender)
    });

    it('should get collaborators viewing/editing an element', () => {
      let session = createCollaborationSession('doc-123');
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });
      session = addCollaborator(session, {
        id: 'user-2',
        name: 'Bob',
        color: '#3b82f6',
      });

      // Alice's cursor on node-1
      session = updateCursor(session, {
        userId: 'user-1',
        x: 100,
        y: 100,
        elementId: 'node-1',
        timestamp: new Date(),
      });

      // Bob has node-1 selected
      session = updateSelection(session, {
        userId: 'user-2',
        elementIds: ['node-1', 'node-2'],
        timestamp: new Date(),
      });

      const collaborators = getElementCollaborators(session, 'node-1');

      expect(collaborators).toHaveLength(2);
      expect(collaborators.map(c => c.id).sort()).toEqual(['user-1', 'user-2']);
    });
  });

  describe('Collaboration Statistics', () => {
    it('should calculate collaboration statistics', () => {
      let session = createCollaborationSession('doc-123');

      // Add collaborators
      session = addCollaborator(session, {
        id: 'user-1',
        name: 'Alice',
        color: '#ef4444',
      });
      session = addCollaborator(session, {
        id: 'user-2',
        name: 'Bob',
        color: '#3b82f6',
      });

      // Set Bob to idle
      const bob = session.collaborators.get('user-2')!;
      bob.status = 'idle';

      // Add operations
      const op1: Operation = {
        id: 'op-1',
        type: 'insert',
        userId: 'user-1',
        timestamp: new Date(Date.now() - 200),
        elementId: 'node-1',
        data: {},
        clientSeq: 1,
        metadata: {},
      };

      const op2: Operation = {
        id: 'op-2',
        type: 'update',
        userId: 'user-1',
        timestamp: new Date(Date.now() - 100),
        elementId: 'node-1',
        data: {},
        clientSeq: 2,
        metadata: {},
      };

      const op3: Operation = {
        id: 'op-3',
        type: 'insert',
        userId: 'user-2',
        timestamp: new Date(),
        elementId: 'node-2',
        data: {},
        clientSeq: 1,
        metadata: {},
      };

      applyOperation(session, op1);
      applyOperation(session, op2);
      applyOperation(session, op3);

      const stats = getCollaborationStats(session);

      expect(stats.totalCollaborators).toBe(2);
      expect(stats.activeCollaborators).toBe(1);
      expect(stats.idleCollaborators).toBe(1);
      expect(stats.totalOperations).toBe(3);
      expect(stats.operationsPerUser['user-1']).toBe(2);
      expect(stats.operationsPerUser['user-2']).toBe(1);
      expect(stats.averageLatency).toBeGreaterThan(0);
    });
  });
});
