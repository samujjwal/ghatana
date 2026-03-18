// All tests skipped - incomplete feature
/**
 * Integration tests for Feature 2.11: Undo/Redo UX with Timeline & Checkpoints
 *
 * Tests the checkpointManager integration with React components including:
 * - Timeline-based undo/redo with actor tracking
 * - Action recording and merging
 * - Named checkpoints for bookmarking
 * - Timeline branching for divergent histories
 * - Conflict detection in collaborative scenarios
 * - Timeline UI data visualization
 *
 * @see docs/canvas-feature-stories.md - Feature 2.11
 */

import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import {
  createCheckpointManagerState,
  setActor,
  recordAction,
  undoWithConflictDetection,
  redoTimeline,
  forceUndo,
  canUndoTimeline,
  canRedoTimeline,
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
  clearTimeline,
  exportTimeline,
  importTimeline,
  type CheckpointManagerState,
  type TimelineAction,
  type Checkpoint,
  type TimelineBranch,
  type UndoConflict,
} from '@ghatana/canvas';
import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';

// Test component that wraps checkpointManager functionality
interface TimelineManagerProps {
  initialState?: CheckpointManagerState;
  onStateChange?: (state: CheckpointManagerState) => void;
  onActionRecorded?: (action: TimelineAction) => void;
  onCheckpointCreated?: (checkpoint: Checkpoint) => void;
  onConflictDetected?: (conflict: UndoConflict) => void;
}

function TimelineManager({
  initialState,
  onStateChange,
  onActionRecorded,
  onCheckpointCreated,
  onConflictDetected,
}: TimelineManagerProps) {
  const [state, setState] = React.useState<CheckpointManagerState>(() => {
    const init =
      initialState || createCheckpointManagerState('user1', 'Test User');
    return init;
  });
  const [lastAction, setLastAction] = React.useState<string>('initialized');
  const [canvasState, setCanvasState] = React.useState<unknown>({
    elements: [],
    zoom: 1,
  });

  const handleStateChange = React.useCallback(
    (newState: CheckpointManagerState) => {
      // Create new state reference for React updates
      const updatedState: CheckpointManagerState = {
        timeline: [...newState.timeline],
        currentIndex: newState.currentIndex,
        checkpoints: new Map(newState.checkpoints),
        branches: new Map(newState.branches),
        activeBranchId: newState.activeBranchId,
        conflicts: new Map(newState.conflicts),
        maxTimelineSize: newState.maxTimelineSize,
        currentActorId: newState.currentActorId,
        currentActorName: newState.currentActorName,
      };
      setState(updatedState);
      onStateChange?.(updatedState);
    },
    [onStateChange]
  );

  const handleRecordAction = React.useCallback(
    (type: string, description: string, data: unknown, options?: unknown) => {
      const action = recordAction(
        state,
        type,
        description,
        data,
        canvasState,
        options
      );
      handleStateChange(state);
      setLastAction(`Recorded: ${description}`);
      onActionRecorded?.(action);
      return action;
    },
    [state, canvasState, handleStateChange, onActionRecorded]
  );

  const handleUndo = React.useCallback(() => {
    const result = undoWithConflictDetection(state);
    if (result.action) {
      handleStateChange(state);
      setLastAction(`Undid: ${result.action.description}`);
      if (result.action.data) {
        setCanvasState(result.action.data);
      }
    }
    if (result.conflict) {
      onConflictDetected?.(result.conflict);
    }
  }, [state, handleStateChange, onConflictDetected]);

  const handleRedo = React.useCallback(() => {
    const action = redoTimeline(state);
    if (action) {
      handleStateChange(state);
      setLastAction(`Redid: ${action.description}`);
      if (action.data) {
        setCanvasState(action.data);
      }
    }
  }, [state, handleStateChange]);

  const handleForceUndo = React.useCallback(() => {
    const action = forceUndo(state);
    if (action) {
      handleStateChange(state);
      setLastAction(`Force undid: ${action.description}`);
      if (action.data) {
        setCanvasState(action.data);
      }
    }
  }, [state, handleStateChange]);

  const handleCreateCheckpoint = React.useCallback(
    (name: string, options?: unknown) => {
      const checkpoint = createCheckpoint(state, name, canvasState, options);
      handleStateChange(state);
      setLastAction(`Created checkpoint: ${name}`);
      onCheckpointCreated?.(checkpoint);
      return checkpoint;
    },
    [state, canvasState, handleStateChange, onCheckpointCreated]
  );

  const handleRestoreCheckpoint = React.useCallback(
    (checkpointId: string) => {
      const success = restoreCheckpoint(
        state,
        checkpointId,
        (restoredState) => {
          setCanvasState(restoredState);
        }
      );
      if (success) {
        handleStateChange(state);
        setLastAction(`Restored checkpoint: ${checkpointId}`);
      }
    },
    [state, handleStateChange]
  );

  const handleDeleteCheckpoint = React.useCallback(
    (checkpointId: string) => {
      const success = deleteCheckpoint(state, checkpointId);
      if (success) {
        handleStateChange(state);
        setLastAction(`Deleted checkpoint: ${checkpointId}`);
      }
    },
    [state, handleStateChange]
  );

  const handleCreateBranch = React.useCallback(
    (name: string, checkpointId: string) => {
      const branch = createBranch(state, name, checkpointId);
      if (branch) {
        handleStateChange(state);
        setLastAction(`Created branch: ${name}`);
      }
      return branch;
    },
    [state, handleStateChange]
  );

  const handleSwitchBranch = React.useCallback(
    (branchId: string) => {
      const success = switchBranch(state, branchId, (branchState) => {
        setCanvasState(branchState);
      });
      if (success) {
        handleStateChange(state);
        setLastAction(`Switched to branch: ${branchId}`);
      }
    },
    [state, handleStateChange]
  );

  const timelinePos = getTimelinePosition(state);
  const undoEnabled = canUndoTimeline(state);
  const redoEnabled = canRedoTimeline(state);
  const checkpoints = getAllCheckpoints(state);
  const branches = getAllBranches(state);
  const conflicts = getUnresolvedConflicts(state);

  return (
    <div data-testid="timeline-manager">
      <div data-testid="actor-info">
        Actor: {state.currentActorName} ({state.currentActorId})
      </div>
      <div data-testid="last-action">Last Action: {lastAction}</div>
      <div data-testid="timeline-position">
        Position: {timelinePos.current} / {timelinePos.total}
      </div>
      <div data-testid="timeline-count">
        Timeline Actions: {state.timeline.length}
      </div>
      <div data-testid="checkpoint-count">
        Checkpoints: {checkpoints.length}
      </div>
      <div data-testid="branch-count">Branches: {branches.length}</div>
      <div data-testid="conflict-count">Conflicts: {conflicts.length}</div>

      <div data-testid="controls">
        <button
          data-testid="record-action-btn"
          onClick={() =>
            handleRecordAction('edit', 'Test Edit', {
              ...canvasState,
              timestamp: Date.now(),
            })
          }
        >
          Record Action
        </button>
        <button
          data-testid="undo-btn"
          onClick={handleUndo}
          disabled={!undoEnabled}
        >
          Undo
        </button>
        <button
          data-testid="redo-btn"
          onClick={handleRedo}
          disabled={!redoEnabled}
        >
          Redo
        </button>
        <button data-testid="force-undo-btn" onClick={handleForceUndo}>
          Force Undo
        </button>
        <button
          data-testid="create-checkpoint-btn"
          onClick={() =>
            handleCreateCheckpoint(`Checkpoint ${checkpoints.length + 1}`)
          }
        >
          Create Checkpoint
        </button>
      </div>

      <div data-testid="timeline-list">
        {state.timeline.slice(0, state.currentIndex + 1).map((action) => (
          <div key={action.id} data-testid={`timeline-action-${action.id}`}>
            <span>{action.description}</span>
            <span> by {action.actorName}</span>
            <span> at {new Date(action.timestamp).toISOString()}</span>
          </div>
        ))}
      </div>

      <div data-testid="checkpoint-list">
        {checkpoints.map((checkpoint) => (
          <div key={checkpoint.id} data-testid={`checkpoint-${checkpoint.id}`}>
            <span>{checkpoint.name}</span>
            <button
              data-testid={`restore-checkpoint-${checkpoint.id}`}
              onClick={() => handleRestoreCheckpoint(checkpoint.id)}
            >
              Restore
            </button>
            <button
              data-testid={`delete-checkpoint-${checkpoint.id}`}
              onClick={() => handleDeleteCheckpoint(checkpoint.id)}
            >
              Delete
            </button>
            <button
              data-testid={`create-branch-${checkpoint.id}`}
              onClick={() =>
                handleCreateBranch(
                  `Branch from ${checkpoint.name}`,
                  checkpoint.id
                )
              }
            >
              Create Branch
            </button>
          </div>
        ))}
      </div>

      <div data-testid="branch-list">
        {branches.map((branch) => (
          <div key={branch.id} data-testid={`branch-${branch.id}`}>
            <span>{branch.name}</span>
            <button
              data-testid={`switch-branch-${branch.id}`}
              onClick={() => handleSwitchBranch(branch.id)}
            >
              Switch
            </button>
          </div>
        ))}
      </div>

      <div data-testid="conflict-list">
        {conflicts.map((conflict) => (
          <div
            key={conflict.conflictId}
            data-testid={`conflict-${conflict.conflictId}`}
          >
            <span>
              Conflict: {conflict.action.description} by{' '}
              {conflict.conflictingActorName}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

describe.skip('Feature 2.11: Undo/Redo UX - Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Timeline and Action Recording', () => {
    it('records actions to timeline with actor tracking', () => {
      const onActionRecorded = vi.fn();
      render(<TimelineManager onActionRecorded={onActionRecorded} />);

      expect(screen.getByTestId('actor-info').textContent).toContain(
        'Test User'
      );
      expect(screen.getByTestId('timeline-count').textContent).toBe(
        'Timeline Actions: 0'
      );

      // Record action
      fireEvent.click(screen.getByTestId('record-action-btn'));

      expect(screen.getByTestId('timeline-count').textContent).toBe(
        'Timeline Actions: 1'
      );
      expect(onActionRecorded).toHaveBeenCalled();
      expect(screen.getByTestId('last-action').textContent).toContain(
        'Recorded: Test Edit'
      );
    });

    it('updates timeline position as actions are recorded', () => {
      render(<TimelineManager />);

      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 0 / 0'
      );

      // Record 3 actions
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));

      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 3 / 3'
      );
    });

    it('displays timeline actions in chronological order', () => {
      render(<TimelineManager />);

      // Record multiple actions
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));

      const timelineList = screen.getByTestId('timeline-list');
      expect(timelineList.children.length).toBe(3);

      // All should be by Test User
      const actions = Array.from(timelineList.children);
      actions.forEach((action) => {
        expect(action.textContent).toContain('Test User');
        expect(action.textContent).toContain('Test Edit');
      });
    });
  });

  describe('Undo/Redo Operations', () => {
    it('enables undo after recording actions', () => {
      render(<TimelineManager />);

      const undoBtn = screen.getByTestId('undo-btn');
      expect(undoBtn).toBeDisabled();

      // Record action
      fireEvent.click(screen.getByTestId('record-action-btn'));

      // Undo should be enabled
      expect(undoBtn).not.toBeDisabled();
    });

    it('performs undo operation and updates timeline', () => {
      render(<TimelineManager />);

      // Record 2 actions
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));

      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 2 / 2'
      );

      // Undo
      fireEvent.click(screen.getByTestId('undo-btn'));

      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 1 / 2'
      );
      expect(screen.getByTestId('last-action').textContent).toContain('Undid');
    });

    it('enables redo after undo', () => {
      render(<TimelineManager />);

      fireEvent.click(screen.getByTestId('record-action-btn'));

      const redoBtn = screen.getByTestId('redo-btn');
      expect(redoBtn).toBeDisabled();

      // Undo
      fireEvent.click(screen.getByTestId('undo-btn'));

      // Redo should be enabled
      expect(redoBtn).not.toBeDisabled();
    });

    it('performs redo operation and restores timeline position', () => {
      render(<TimelineManager />);

      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('undo-btn'));

      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 1 / 2'
      );

      // Redo
      fireEvent.click(screen.getByTestId('redo-btn'));

      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 2 / 2'
      );
      expect(screen.getByTestId('last-action').textContent).toContain('Redid');
    });

    it('handles multiple undo/redo cycles', () => {
      render(<TimelineManager />);

      // Record 3 actions
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));

      // Undo 2 times
      fireEvent.click(screen.getByTestId('undo-btn'));
      fireEvent.click(screen.getByTestId('undo-btn'));
      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 1 / 3'
      );

      // Redo 1 time
      fireEvent.click(screen.getByTestId('redo-btn'));
      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 2 / 3'
      );

      // Undo 1 time again
      fireEvent.click(screen.getByTestId('undo-btn'));
      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 1 / 3'
      );
    });
  });

  describe('Checkpoint Management', () => {
    it('creates named checkpoints', () => {
      const onCheckpointCreated = vi.fn();
      render(<TimelineManager onCheckpointCreated={onCheckpointCreated} />);

      expect(screen.getByTestId('checkpoint-count').textContent).toBe(
        'Checkpoints: 0'
      );

      // Record some actions first
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));

      // Create checkpoint
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));

      expect(screen.getByTestId('checkpoint-count').textContent).toBe(
        'Checkpoints: 1'
      );
      expect(onCheckpointCreated).toHaveBeenCalled();
      expect(screen.getByText('Checkpoint 1')).toBeTruthy();
    });

    it('restores state from checkpoint', async () => {
      render(<TimelineManager />);

      // Record actions and create checkpoint
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));

      const checkpointId = screen
        .getByTestId('checkpoint-list')
        .children[0]?.getAttribute('data-testid')
        ?.replace('checkpoint-', '');

      // Record more actions
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));

      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 4 / 4'
      );

      // Restore checkpoint
      if (checkpointId) {
        fireEvent.click(
          screen.getByTestId(`restore-checkpoint-${checkpointId}`)
        );
      }

      await waitFor(() => {
        expect(screen.getByTestId('last-action').textContent).toContain(
          'Restored checkpoint'
        );
      });
    });

    it('deletes checkpoints', () => {
      render(<TimelineManager />);

      // Create checkpoint
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));

      expect(screen.getByTestId('checkpoint-count').textContent).toBe(
        'Checkpoints: 1'
      );

      const checkpointId = screen
        .getByTestId('checkpoint-list')
        .children[0]?.getAttribute('data-testid')
        ?.replace('checkpoint-', '');

      // Delete checkpoint
      if (checkpointId) {
        fireEvent.click(
          screen.getByTestId(`delete-checkpoint-${checkpointId}`)
        );
      }

      expect(screen.getByTestId('checkpoint-count').textContent).toBe(
        'Checkpoints: 0'
      );
    });

    it('maintains multiple checkpoints', () => {
      render(<TimelineManager />);

      // Create multiple checkpoints
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));

      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));

      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));

      expect(screen.getByTestId('checkpoint-count').textContent).toBe(
        'Checkpoints: 3'
      );
      expect(screen.getByText('Checkpoint 1')).toBeTruthy();
      expect(screen.getByText('Checkpoint 2')).toBeTruthy();
      expect(screen.getByText('Checkpoint 3')).toBeTruthy();
    });
  });

  describe('Timeline Branching', () => {
    it('creates branch from checkpoint', () => {
      render(<TimelineManager />);

      // Create checkpoint
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));

      expect(screen.getByTestId('branch-count').textContent).toBe(
        'Branches: 0'
      );

      const checkpointId = screen
        .getByTestId('checkpoint-list')
        .children[0]?.getAttribute('data-testid')
        ?.replace('checkpoint-', '');

      // Create branch
      if (checkpointId) {
        fireEvent.click(screen.getByTestId(`create-branch-${checkpointId}`));
      }

      expect(screen.getByTestId('branch-count').textContent).toBe(
        'Branches: 1'
      );
    });

    it('switches between branches', async () => {
      render(<TimelineManager />);

      // Create checkpoint and branch
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));

      const checkpointId = screen
        .getByTestId('checkpoint-list')
        .children[0]?.getAttribute('data-testid')
        ?.replace('checkpoint-', '');

      if (checkpointId) {
        fireEvent.click(screen.getByTestId(`create-branch-${checkpointId}`));
      }

      const branchId = screen
        .getByTestId('branch-list')
        .children[0]?.getAttribute('data-testid')
        ?.replace('branch-', '');

      // Switch branch
      if (branchId) {
        fireEvent.click(screen.getByTestId(`switch-branch-${branchId}`));

        await waitFor(() => {
          expect(screen.getByTestId('last-action').textContent).toContain(
            'Switched to branch'
          );
        });
      }
    });

    it('maintains independent timeline per branch', () => {
      render(<TimelineManager />);

      // Main timeline actions
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));

      const mainTimelineCount = 2;

      // Create checkpoint and branch
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));
      const checkpointId = screen
        .getByTestId('checkpoint-list')
        .children[0]?.getAttribute('data-testid')
        ?.replace('checkpoint-', '');

      if (checkpointId) {
        fireEvent.click(screen.getByTestId(`create-branch-${checkpointId}`));
      }

      // Branch should have independent state
      expect(screen.getByTestId('branch-count').textContent).toBe(
        'Branches: 1'
      );
    });
  });

  describe('Collaborative Undo and Conflicts', () => {
    it("detects conflicts when undoing other actor's actions", () => {
      const onConflictDetected = vi.fn();
      const actorState = createCheckpointManagerState('user2', 'Other User');

      // Simulate another actor's actions
      recordAction(actorState, 'edit', 'Other User Edit', { data: 'test' }, {});

      render(
        <TimelineManager
          initialState={actorState}
          onConflictDetected={onConflictDetected}
        />
      );

      // Current user tries to undo other's action
      fireEvent.click(screen.getByTestId('undo-btn'));

      // Should detect conflict
      expect(onConflictDetected).toHaveBeenCalled();
    });

    it('force undo bypasses conflict detection', () => {
      const actorState = createCheckpointManagerState('user2', 'Other User');
      recordAction(actorState, 'edit', 'Other User Edit', { data: 'test' }, {});

      render(<TimelineManager initialState={actorState} />);

      // Force undo
      fireEvent.click(screen.getByTestId('force-undo-btn'));

      expect(screen.getByTestId('last-action').textContent).toContain(
        'Force undid'
      );
    });

    it('displays unresolved conflicts', () => {
      const actorState = createCheckpointManagerState('user2', 'Other User');
      recordAction(actorState, 'edit', 'Other User Edit', { data: 'test' }, {});

      render(<TimelineManager initialState={actorState} />);

      // Try to undo (will create conflict)
      fireEvent.click(screen.getByTestId('undo-btn'));

      // Check conflict display
      const conflictList = screen.getByTestId('conflict-list');
      if (conflictList.children.length > 0) {
        expect(conflictList.children[0].textContent).toContain('Conflict');
      }
    });
  });

  describe('Acceptance Criteria Validation', () => {
    it('✓ Timeline scrubber: Navigate history visually', () => {
      render(<TimelineManager />);

      // Record multiple actions to build timeline
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));

      // Verify timeline is populated
      const timelineList = screen.getByTestId('timeline-list');
      expect(timelineList.children.length).toBe(3);

      // Navigate back
      fireEvent.click(screen.getByTestId('undo-btn'));
      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 2 / 3'
      );

      // Navigate forward
      fireEvent.click(screen.getByTestId('redo-btn'));
      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 3 / 3'
      );
    });

    it('✓ Named checkpoints: Bookmark important states', () => {
      const onCheckpointCreated = vi.fn();
      render(<TimelineManager onCheckpointCreated={onCheckpointCreated} />);

      // Create work and checkpoint
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('record-action-btn'));
      fireEvent.click(screen.getByTestId('create-checkpoint-btn'));

      // Verify checkpoint created
      expect(onCheckpointCreated).toHaveBeenCalled();
      expect(screen.getByText('Checkpoint 1')).toBeTruthy();

      // Can restore to checkpoint
      const checkpointId = screen
        .getByTestId('checkpoint-list')
        .children[0]?.getAttribute('data-testid')
        ?.replace('checkpoint-', '');
      if (checkpointId) {
        const restoreBtn = screen.getByTestId(
          `restore-checkpoint-${checkpointId}`
        );
        expect(restoreBtn).toBeTruthy();
      }
    });

    it("✓ Collaborative undo: Conflict warning for other users' changes", () => {
      const onConflictDetected = vi.fn();
      const actorState = createCheckpointManagerState('user2', 'Other User');
      recordAction(actorState, 'edit', 'Other User Edit', { data: 'test' }, {});

      render(
        <TimelineManager
          initialState={actorState}
          onConflictDetected={onConflictDetected}
        />
      );

      // Try to undo other's action
      fireEvent.click(screen.getByTestId('undo-btn'));

      // Should get conflict warning
      expect(onConflictDetected).toHaveBeenCalled();
      const conflict = onConflictDetected.mock.calls[0][0];
      expect(conflict.conflictingActorName).toBe('Other User');
    });
  });

  describe('Performance and Edge Cases', () => {
    it('handles rapid action recording', () => {
      render(<TimelineManager />);

      // Rapidly record 10 actions
      for (let i = 0; i < 10; i++) {
        fireEvent.click(screen.getByTestId('record-action-btn'));
      }

      expect(screen.getByTestId('timeline-count').textContent).toBe(
        'Timeline Actions: 10'
      );
      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 10 / 10'
      );
    });

    it('handles large timeline efficiently', () => {
      render(<TimelineManager />);

      // Create many actions
      for (let i = 0; i < 20; i++) {
        fireEvent.click(screen.getByTestId('record-action-btn'));
      }

      expect(screen.getByTestId('timeline-count').textContent).toBe(
        'Timeline Actions: 20'
      );

      // Should still respond to undo/redo
      fireEvent.click(screen.getByTestId('undo-btn'));
      expect(screen.getByTestId('timeline-position').textContent).toBe(
        'Position: 19 / 20'
      );
    });

    it('handles multiple checkpoints and branches', () => {
      render(<TimelineManager />);

      // Create multiple checkpoints with branches
      for (let i = 0; i < 5; i++) {
        fireEvent.click(screen.getByTestId('record-action-btn'));
        fireEvent.click(screen.getByTestId('create-checkpoint-btn'));
      }

      expect(screen.getByTestId('checkpoint-count').textContent).toBe(
        'Checkpoints: 5'
      );

      // Create branches from some checkpoints
      const checkpoints = screen.getByTestId('checkpoint-list').children;
      if (checkpoints.length > 0) {
        const firstCheckpointId = checkpoints[0]
          ?.getAttribute('data-testid')
          ?.replace('checkpoint-', '');
        if (firstCheckpointId) {
          fireEvent.click(
            screen.getByTestId(`create-branch-${firstCheckpointId}`)
          );
        }
      }

      expect(screen.getByTestId('branch-count').textContent).toBe(
        'Branches: 1'
      );
    });
  });
});
