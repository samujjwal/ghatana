/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, beforeEach } from 'vitest';

import {
  createCheckpointManagerState,
  setActor,
  recordAction,
  undo,
  redo,
  forceUndo,
  canUndo,
  canRedo,
  getTimelinePosition,
  createCheckpoint,
  getCheckpoint,
  getAllCheckpoints,
  restoreCheckpoint,
  deleteCheckpoint,
  updateCheckpoint,
  createBranch,
  getBranch,
  getAllBranches,
  switchBranch,
  getConflict,
  getUnresolvedConflicts,
  resolveConflict,
  clearResolvedConflicts,
  getActionsByActor,
  getActionsByType,
  getActionsInRange,
  getTimelineStats,
  searchCheckpoints,
  clearTimeline,
  exportTimeline,
  importTimeline,
  type CheckpointManagerState,
} from '../checkpointManager';

describe('checkpointManager', () => {
  let state: CheckpointManagerState;

  beforeEach(() => {
    state = createCheckpointManagerState('user1', 'Alice');
  });

  describe('State Creation', () => {
    it('should create empty state', () => {
      expect(state.timeline).toHaveLength(0);
      expect(state.currentIndex).toBe(-1);
      expect(state.checkpoints.size).toBe(0);
      expect(state.branches.size).toBe(0);
      expect(state.conflicts.size).toBe(0);
      expect(state.currentActorId).toBe('user1');
      expect(state.currentActorName).toBe('Alice');
      expect(state.maxTimelineSize).toBe(1000);
    });

    it('should create state with custom max size', () => {
      const customState = createCheckpointManagerState('user1', 'Alice', 500);
      expect(customState.maxTimelineSize).toBe(500);
    });

    it('should use default actor if not provided', () => {
      const defaultState = createCheckpointManagerState();
      expect(defaultState.currentActorId).toBe('anonymous');
      expect(defaultState.currentActorName).toBe('Anonymous User');
    });
  });

  describe('Actor Management', () => {
    it('should set current actor', () => {
      setActor(state, 'user2', 'Bob');
      expect(state.currentActorId).toBe('user2');
      expect(state.currentActorName).toBe('Bob');
    });

    it('should record actions with updated actor', () => {
      setActor(state, 'user2', 'Bob');
      const action = recordAction(state, 'create', 'Created node', { nodeId: 'n1' });
      expect(action.actorId).toBe('user2');
      expect(action.actorName).toBe('Bob');
    });
  });

  describe('Action Recording', () => {
    it('should record action', () => {
      const action = recordAction(state, 'create', 'Created node', { nodeId: 'n1' });
      
      expect(action.type).toBe('create');
      expect(action.description).toBe('Created node');
      expect(action.data).toEqual({ nodeId: 'n1' });
      expect(action.actorId).toBe('user1');
      expect(action.actorName).toBe('Alice');
      expect(state.timeline).toHaveLength(1);
      expect(state.currentIndex).toBe(0);
    });

    it('should truncate forward history', () => {
      recordAction(state, 'create', 'Action 1', {});
      recordAction(state, 'update', 'Action 2', {});
      recordAction(state, 'delete', 'Action 3', {});
      
      // Undo twice
      undo(state, {});
      undo(state, {});
      
      expect(state.currentIndex).toBe(0);
      expect(state.timeline).toHaveLength(3);
      
      // Record new action - should truncate
      recordAction(state, 'create', 'New Action', {});
      
      expect(state.timeline).toHaveLength(2);
      expect(state.currentIndex).toBe(1);
      expect(state.timeline[1].description).toBe('New Action');
    });

    it('should merge with previous action', () => {
      const action1 = recordAction(state, 'drag', 'Dragging', { x: 10, y: 10 });
      const action2 = recordAction(
        state,
        'drag',
        'Dragging',
        { x: 20, y: 20 },
        { merge: true }
      );
      
      expect(state.timeline).toHaveLength(1);
      expect(action1.id).toBe(action2.id);
      expect(action2.data).toEqual({ x: 20, y: 20 });
    });

    it('should not merge if type differs', () => {
      recordAction(state, 'drag', 'Dragging', { x: 10 });
      recordAction(state, 'resize', 'Resizing', { w: 100 }, { merge: true });
      
      expect(state.timeline).toHaveLength(2);
    });

    it('should not merge if time gap exceeds duration', async () => {
      recordAction(state, 'drag', 'Dragging', { x: 10 });
      
      // Wait longer than merge duration
      await new Promise((resolve) => setTimeout(resolve, 50));
      
      recordAction(
        state,
        'drag',
        'Dragging',
        { x: 20 },
        { merge: true, mergeDuration: 10 }
      );
      
      expect(state.timeline).toHaveLength(2);
    });

    it('should enforce max timeline size', () => {
      const smallState = createCheckpointManagerState('user1', 'Alice', 5);
      
      for (let i = 0; i < 10; i++) {
        recordAction(smallState, 'create', `Action ${i}`, { i });
      }
      
      expect(smallState.timeline).toHaveLength(5);
      expect(smallState.timeline[0].description).toBe('Action 5');
      expect(smallState.timeline[4].description).toBe('Action 9');
    });

    it('should update checkpoint indices when enforcing max size', () => {
      const smallState = createCheckpointManagerState('user1', 'Alice', 5);
      
      for (let i = 0; i < 3; i++) {
        recordAction(smallState, 'create', `Action ${i}`, { i });
      }
      
      createCheckpoint(smallState, 'Checkpoint', {});
      const checkpoint = getAllCheckpoints(smallState)[0];
      const initialIndex = checkpoint.historyIndex;
      
      // Add more actions to trigger size limit
      for (let i = 3; i < 10; i++) {
        recordAction(smallState, 'create', `Action ${i}`, { i });
      }
      
      expect(checkpoint.historyIndex).toBeLessThan(initialIndex);
    });
  });

  describe('Undo/Redo', () => {
    beforeEach(() => {
      recordAction(state, 'create', 'Action 1', { id: 1 });
      recordAction(state, 'create', 'Action 2', { id: 2 });
      recordAction(state, 'create', 'Action 3', { id: 3 });
    });

    it('should undo action', () => {
      const result = undo(state, {});
      
      expect(result.action).not.toBeNull();
      expect(result.action?.description).toBe('Action 3');
      expect(result.conflict).toBeNull();
      expect(state.currentIndex).toBe(1);
    });

    it('should not undo when at start', () => {
      undo(state, {});
      undo(state, {});
      undo(state, {});
      const result = undo(state, {});
      
      expect(result.action).toBeNull();
      expect(state.currentIndex).toBe(-1);
    });

    it('should redo action', () => {
      undo(state, {});
      const action = redo(state);
      
      expect(action).not.toBeNull();
      expect(action?.description).toBe('Action 3');
      expect(state.currentIndex).toBe(2);
    });

    it('should not redo when at end', () => {
      const action = redo(state);
      expect(action).toBeNull();
      expect(state.currentIndex).toBe(2);
    });

    it('should check if can undo', () => {
      expect(canUndo(state)).toBe(true);
      
      undo(state, {});
      undo(state, {});
      undo(state, {});
      
      expect(canUndo(state)).toBe(false);
    });

    it('should check if can redo', () => {
      expect(canRedo(state)).toBe(false);
      
      undo(state, {});
      expect(canRedo(state)).toBe(true);
    });

    it('should get timeline position', () => {
      const pos = getTimelinePosition(state);
      
      expect(pos.current).toBe(3);
      expect(pos.total).toBe(3);
      expect(pos.canUndo).toBe(true);
      expect(pos.canRedo).toBe(false);
    });
  });

  describe('Conflict Detection', () => {
    it('should detect conflict when undoing after other actor', () => {
      recordAction(state, 'create', 'Alice action', { id: 1 });
      
      setActor(state, 'user2', 'Bob');
      recordAction(state, 'update', 'Bob action', { id: 2 });
      
      setActor(state, 'user1', 'Alice');
      const result = undo(state, {});
      
      expect(result.conflict).not.toBeNull();
      expect(result.conflict?.conflictingActorName).toBe('Bob');
      expect(result.conflict?.conflictingActions).toHaveLength(1);
      expect(state.conflicts.size).toBe(1);
    });

    it('should not detect conflict for same actor', () => {
      recordAction(state, 'create', 'Action 1', {});
      recordAction(state, 'create', 'Action 2', {});
      
      const result = undo(state, {});
      
      expect(result.conflict).toBeNull();
    });

    it('should force undo despite conflict', () => {
      recordAction(state, 'create', 'Alice action', {});
      
      setActor(state, 'user2', 'Bob');
      recordAction(state, 'update', 'Bob action', {});
      
      setActor(state, 'user1', 'Alice');
      const action = forceUndo(state);
      
      expect(action).not.toBeNull();
      expect(state.currentIndex).toBe(0);
    });

    it('should get unresolved conflicts', () => {
      recordAction(state, 'create', 'Alice action', {});
      
      setActor(state, 'user2', 'Bob');
      recordAction(state, 'update', 'Bob action', {});
      
      setActor(state, 'user1', 'Alice');
      undo(state, {});
      
      const conflicts = getUnresolvedConflicts(state);
      expect(conflicts).toHaveLength(1);
    });

    it('should resolve conflict with force strategy', () => {
      recordAction(state, 'create', 'Alice action', {});
      setActor(state, 'user2', 'Bob');
      recordAction(state, 'update', 'Bob action', {});
      setActor(state, 'user1', 'Alice');
      
      const result = undo(state, {});
      const conflictId = result.conflict!.conflictId;
      
      const resolved = resolveConflict(state, conflictId, { strategy: 'force' });
      
      expect(resolved).toBe(true);
      const conflict = getConflict(state, conflictId);
      expect(conflict?.resolution).toBe('force');
    });

    it('should resolve conflict with cancel strategy', () => {
      recordAction(state, 'create', 'Alice action', {});
      setActor(state, 'user2', 'Bob');
      recordAction(state, 'update', 'Bob action', {});
      setActor(state, 'user1', 'Alice');
      
      const result = undo(state, {});
      const conflictId = result.conflict!.conflictId;
      
      resolveConflict(state, conflictId, { strategy: 'cancel' });
      
      expect(state.conflicts.has(conflictId)).toBe(false);
    });

    it('should clear resolved conflicts', () => {
      recordAction(state, 'create', 'Alice action', {});
      setActor(state, 'user2', 'Bob');
      recordAction(state, 'update', 'Bob action', {});
      setActor(state, 'user1', 'Alice');
      
      const result = undo(state, {});
      resolveConflict(state, result.conflict!.conflictId, { strategy: 'cancel' });
      
      const cleared = clearResolvedConflicts(state);
      expect(cleared).toBe(0); // Already deleted on cancel
    });
  });

  describe('Checkpoints', () => {
    beforeEach(() => {
      recordAction(state, 'create', 'Action 1', {});
      recordAction(state, 'create', 'Action 2', {});
    });

    it('should create checkpoint', () => {
      const checkpoint = createCheckpoint(state, 'My Checkpoint', { data: 'test' });
      
      expect(checkpoint.name).toBe('My Checkpoint');
      expect(checkpoint.actorId).toBe('user1');
      expect(checkpoint.historyIndex).toBe(1);
      expect(checkpoint.state).toEqual({ data: 'test' });
      expect(state.checkpoints.size).toBe(1);
    });

    it('should create checkpoint with auto name', () => {
      const checkpoint = createCheckpoint(
        state,
        '',
        {},
        { autoName: true }
      );
      
      expect(checkpoint.name).toContain('Checkpoint');
    });

    it('should create checkpoint with description and tags', () => {
      const checkpoint = createCheckpoint(
        state,
        'Feature Complete',
        {},
        {
          description: 'All features implemented',
          tags: ['milestone', 'v1.0'],
        }
      );
      
      expect(checkpoint.description).toBe('All features implemented');
      expect(checkpoint.tags).toEqual(['milestone', 'v1.0']);
    });

    it('should get checkpoint by ID', () => {
      const created = createCheckpoint(state, 'Test', {});
      const retrieved = getCheckpoint(state, created.id);
      
      expect(retrieved).toEqual(created);
    });

    it('should return null for non-existent checkpoint', () => {
      const retrieved = getCheckpoint(state, 'non-existent');
      expect(retrieved).toBeNull();
    });

    it('should get all checkpoints in order', async () => {
      createCheckpoint(state, 'Checkpoint 1', {});
      await new Promise((resolve) => setTimeout(resolve, 10));
      createCheckpoint(state, 'Checkpoint 2', {});
      await new Promise((resolve) => setTimeout(resolve, 10));
      createCheckpoint(state, 'Checkpoint 3', {});
      
      const all = getAllCheckpoints(state);
      
      expect(all).toHaveLength(3);
      expect(all[0].name).toBe('Checkpoint 1');
      expect(all[2].name).toBe('Checkpoint 3');
    });

    it('should restore to checkpoint', () => {
      const checkpoint = createCheckpoint(state, 'Save Point', {});
      
      recordAction(state, 'create', 'Action 3', {});
      expect(state.currentIndex).toBe(2);
      
      const result = restoreCheckpoint(state, checkpoint.id);
      
      expect(result).not.toBeNull();
      expect(state.currentIndex).toBe(1);
      expect(result!.checkpoint).toEqual(checkpoint);
    });

    it('should create branch when restoring to different index', () => {
      const checkpoint = createCheckpoint(state, 'Save Point', {});
      recordAction(state, 'create', 'Action 3', {});
      
      const result = restoreCheckpoint(state, checkpoint.id);
      
      expect(result!.branch).not.toBeNull();
      expect(result!.branch?.name).toContain('Save Point');
    });

    it('should not create branch when already at checkpoint', () => {
      const checkpoint = createCheckpoint(state, 'Save Point', {});
      
      const result = restoreCheckpoint(state, checkpoint.id);
      
      expect(result!.branch).toBeNull();
    });

    it('should delete checkpoint', () => {
      const checkpoint = createCheckpoint(state, 'Test', {});
      
      const deleted = deleteCheckpoint(state, checkpoint.id);
      
      expect(deleted).toBe(true);
      expect(state.checkpoints.size).toBe(0);
    });

    it('should return false when deleting non-existent checkpoint', () => {
      const deleted = deleteCheckpoint(state, 'non-existent');
      expect(deleted).toBe(false);
    });

    it('should update checkpoint metadata', () => {
      const checkpoint = createCheckpoint(state, 'Old Name', {});
      
      const updated = updateCheckpoint(state, checkpoint.id, {
        name: 'New Name',
        description: 'Updated description',
        tags: ['updated'],
      });
      
      expect(updated).not.toBeNull();
      expect(updated!.name).toBe('New Name');
      expect(updated!.description).toBe('Updated description');
      expect(updated!.tags).toEqual(['updated']);
    });

    it('should search checkpoints by name', () => {
      createCheckpoint(state, 'Feature A', {});
      createCheckpoint(state, 'Feature B', {});
      createCheckpoint(state, 'Bug Fix', {});
      
      const results = searchCheckpoints(state, 'feature');
      
      expect(results).toHaveLength(2);
      expect(results[0].name).toBe('Feature A');
    });

    it('should search checkpoints by tag', () => {
      createCheckpoint(state, 'CP1', {}, { tags: ['v1.0', 'release'] });
      createCheckpoint(state, 'CP2', {}, { tags: ['v2.0'] });
      
      const results = searchCheckpoints(state, 'release');
      
      expect(results).toHaveLength(1);
      expect(results[0].name).toBe('CP1');
    });
  });

  describe('Branches', () => {
    beforeEach(() => {
      recordAction(state, 'create', 'Action 1', {});
      createCheckpoint(state, 'Checkpoint 1', {});
    });

    it('should create branch', () => {
      const checkpoint = getAllCheckpoints(state)[0];
      const branch = createBranch(state, 'Feature Branch', checkpoint.id);
      
      expect(branch.name).toBe('Feature Branch');
      expect(branch.parentCheckpointId).toBe(checkpoint.id);
      expect(state.branches.size).toBe(1);
      expect(state.activeBranchId).toBe(branch.id);
    });

    it('should get branch by ID', () => {
      const checkpoint = getAllCheckpoints(state)[0];
      const created = createBranch(state, 'Test Branch', checkpoint.id);
      const retrieved = getBranch(state, created.id);
      
      expect(retrieved).toEqual(created);
    });

    it('should get all branches', () => {
      const checkpoint = getAllCheckpoints(state)[0];
      createBranch(state, 'Branch 1', checkpoint.id);
      createBranch(state, 'Branch 2', checkpoint.id);
      
      const all = getAllBranches(state);
      
      expect(all).toHaveLength(2);
    });

    it('should switch branch', () => {
      const checkpoint = getAllCheckpoints(state)[0];
      const branch = createBranch(state, 'Test Branch', checkpoint.id);
      
      state.activeBranchId = null;
      
      const switched = switchBranch(state, branch.id);
      
      expect(switched).toEqual(branch);
      expect(state.activeBranchId).toBe(branch.id);
    });
  });

  describe('Timeline Queries', () => {
    beforeEach(() => {
      recordAction(state, 'create', 'Action 1', { id: 1 });
      setActor(state, 'user2', 'Bob');
      recordAction(state, 'update', 'Action 2', { id: 2 });
      setActor(state, 'user1', 'Alice');
      recordAction(state, 'delete', 'Action 3', { id: 3 });
    });

    it('should get actions by actor', () => {
      const aliceActions = getActionsByActor(state, 'user1');
      
      expect(aliceActions).toHaveLength(2);
      expect(aliceActions[0].description).toBe('Action 1');
      expect(aliceActions[1].description).toBe('Action 3');
    });

    it('should get actions by type', () => {
      const createActions = getActionsByType(state, 'create');
      
      expect(createActions).toHaveLength(1);
      expect(createActions[0].description).toBe('Action 1');
    });

    it('should get actions in time range', () => {
      const startTime = state.timeline[0].timestamp;
      const endTime = state.timeline[1].timestamp;
      
      const actions = getActionsInRange(state, startTime, endTime);
      
      // Should include first two actions (startTime ≤ timestamp ≤ endTime)
      expect(actions.length).toBeGreaterThanOrEqual(2);
      expect(actions[0].timestamp).toBe(startTime);
    });

    it('should get timeline statistics', () => {
      createCheckpoint(state, 'CP1', {});
      undo(state, {});
      
      const stats = getTimelineStats(state);
      
      expect(stats.totalActions).toBe(3);
      expect(stats.actorCount).toBe(2);
      expect(stats.actionTypes).toContain('create');
      expect(stats.actionTypes).toContain('update');
      expect(stats.actionTypes).toContain('delete');
      expect(stats.checkpointCount).toBe(1);
      expect(stats.oldestAction?.description).toBe('Action 1');
      expect(stats.newestAction?.description).toBe('Action 3');
    });
  });

  describe('Timeline Management', () => {
    beforeEach(() => {
      recordAction(state, 'create', 'Action 1', {});
      recordAction(state, 'create', 'Action 2', {});
      createCheckpoint(state, 'Checkpoint', {});
    });

    it('should clear timeline', () => {
      clearTimeline(state);
      
      expect(state.timeline).toHaveLength(0);
      expect(state.currentIndex).toBe(-1);
      expect(state.checkpoints.size).toBe(0);
      expect(state.conflicts.size).toBe(0);
    });

    it('should export timeline', () => {
      const json = exportTimeline(state);
      const data = JSON.parse(json);
      
      expect(data.timeline).toHaveLength(2);
      expect(data.currentIndex).toBe(1);
      expect(data.checkpoints).toHaveLength(1);
    });

    it('should import timeline', () => {
      const json = exportTimeline(state);
      
      const newState = createCheckpointManagerState('user2', 'Bob');
      const success = importTimeline(newState, json);
      
      expect(success).toBe(true);
      expect(newState.timeline).toHaveLength(2);
      expect(newState.checkpoints.size).toBe(1);
    });

    it('should handle invalid import JSON', () => {
      const success = importTimeline(state, 'invalid json');
      expect(success).toBe(false);
    });
  });
});
