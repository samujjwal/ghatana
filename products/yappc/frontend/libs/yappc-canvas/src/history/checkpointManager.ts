/**
 * Checkpoint Manager - Timeline UI and Advanced Undo/Redo System
 * 
 * Provides timeline-based history tracking with:
 * - Actor and timestamp tracking for collaborative environments
 * - Named checkpoints for manual save points and branching
 * - Conflict detection and resolution for collaborative undo
 * - Timeline UI data structures for history visualization
 * 
 * @module checkpointManager
 */

/**
 * Represents an action in the timeline
 */
export interface TimelineAction {
  id: string;
  type: string;
  description: string;
  actorId: string;
  actorName: string;
  timestamp: number;
  data: unknown;
  metadata?: Record<string, unknown>;
}

/**
 * Named checkpoint for branching/bookmarking
 */
export interface Checkpoint {
  id: string;
  name: string;
  description?: string;
  timestamp: number;
  actorId: string;
  actorName: string;
  historyIndex: number; // Position in timeline
  state: unknown; // Complete canvas state snapshot
  tags?: string[];
}

/**
 * Conflict information when collaborative undo is attempted
 */
export interface UndoConflict {
  conflictId: string;
  actionId: string;
  action: TimelineAction;
  conflictingActorId: string;
  conflictingActorName: string;
  conflictingActions: TimelineAction[];
  detectedAt: number;
  resolution?: 'force' | 'merge' | 'cancel';
}

/**
 * Timeline branch for divergent history paths
 */
export interface TimelineBranch {
  id: string;
  name: string;
  parentCheckpointId: string;
  createdAt: number;
  actions: TimelineAction[];
  checkpoints: Checkpoint[];
}

/**
 * Complete checkpoint manager state
 */
export interface CheckpointManagerState {
  timeline: TimelineAction[];
  currentIndex: number;
  checkpoints: Map<string, Checkpoint>;
  branches: Map<string, TimelineBranch>;
  activeBranchId: string | null;
  conflicts: Map<string, UndoConflict>;
  maxTimelineSize: number;
  currentActorId: string;
  currentActorName: string;
}

/**
 * Options for recording actions
 */
export interface RecordActionOptions {
  merge?: boolean; // Merge with previous action if same type
  mergeDuration?: number; // Max time gap for merge (default 1000ms)
  metadata?: Record<string, unknown>;
}

/**
 * Options for creating checkpoints
 */
export interface CheckpointOptions {
  description?: string;
  tags?: string[];
  autoName?: boolean; // Auto-generate name from timestamp
}

/**
 * Options for conflict resolution
 */
export interface ConflictResolutionOptions {
  strategy: 'force' | 'merge' | 'cancel';
  mergeFunction?: (current: unknown, conflicting: unknown) => any;
}

/**
 * Create empty checkpoint manager state
 */
export function createCheckpointManagerState(
  actorId: string = 'anonymous',
  actorName: string = 'Anonymous User',
  maxTimelineSize: number = 1000
): CheckpointManagerState {
  return {
    timeline: [],
    currentIndex: -1,
    checkpoints: new Map(),
    branches: new Map(),
    activeBranchId: null,
    conflicts: new Map(),
    maxTimelineSize,
    currentActorId: actorId,
    currentActorName: actorName,
  };
}

/**
 * Set current actor (user)
 */
export function setActor(
  state: CheckpointManagerState,
  actorId: string,
  actorName: string
): void {
  state.currentActorId = actorId;
  state.currentActorName = actorName;
}

/**
 * Record a new action in the timeline
 */
export function recordAction(
  state: CheckpointManagerState,
  type: string,
  description: string,
  data: unknown,
  options: RecordActionOptions = {}
): TimelineAction {
  const now = Date.now();
  
  // Check if should merge with previous action
  if (options.merge && state.currentIndex >= 0) {
    const prevAction = state.timeline[state.currentIndex];
    const timeSincePrev = now - prevAction.timestamp;
    const mergeDuration = options.mergeDuration ?? 1000;
    
    if (
      prevAction.type === type &&
      prevAction.actorId === state.currentActorId &&
      timeSincePrev <= mergeDuration
    ) {
      // Merge data with previous action
      prevAction.data = data;
      prevAction.timestamp = now;
      if (options.metadata) {
        prevAction.metadata = { ...prevAction.metadata, ...options.metadata };
      }
      return prevAction;
    }
  }
  
  const action: TimelineAction = {
    id: `action-${now}-${Math.random().toString(36).substr(2, 9)}`,
    type,
    description,
    actorId: state.currentActorId,
    actorName: state.currentActorName,
    timestamp: now,
    data,
    metadata: options.metadata,
  };
  
  // Truncate forward history if we're not at the end
  if (state.currentIndex < state.timeline.length - 1) {
    state.timeline = state.timeline.slice(0, state.currentIndex + 1);
  }
  
  // Add action
  state.timeline.push(action);
  state.currentIndex = state.timeline.length - 1;
  
  // Enforce max timeline size
  if (state.timeline.length > state.maxTimelineSize) {
    const removeCount = state.timeline.length - state.maxTimelineSize;
    state.timeline = state.timeline.slice(removeCount);
    state.currentIndex -= removeCount;
    
    // Update checkpoint indices
    state.checkpoints.forEach((checkpoint) => {
      checkpoint.historyIndex -= removeCount;
    });
  }
  
  return action;
}

/**
 * Undo to previous action with conflict detection
 */
export function undo(
  state: CheckpointManagerState,
  currentState: unknown
): { action: TimelineAction | null; conflict: UndoConflict | null } {
  if (state.currentIndex < 0) {
    return { action: null, conflict: null };
  }
  
  const actionToUndo = state.timeline[state.currentIndex];
  
  // Check for conflict: trying to undo someone else's action  
  if (actionToUndo.actorId !== state.currentActorId) {
    // Check if there are other actions after this one
    const actionsAfterUndo = state.timeline.slice(state.currentIndex + 1);
    
    const conflict: UndoConflict = {
      conflictId: `conflict-${Date.now()}`,
      actionId: actionToUndo.id,
      action: actionToUndo,
      conflictingActorId: actionToUndo.actorId,
      conflictingActorName: actionToUndo.actorName,
      conflictingActions: actionsAfterUndo.length > 0 ? actionsAfterUndo : [actionToUndo],
      detectedAt: Date.now(),
    };
    
    state.conflicts.set(conflict.conflictId, conflict);
    return { action: actionToUndo, conflict };
  }
  
  state.currentIndex--;
  return { action: actionToUndo, conflict: null };
}

/**
 * Redo to next action
 */
export function redo(state: CheckpointManagerState): TimelineAction | null {
  if (state.currentIndex >= state.timeline.length - 1) {
    return null;
  }
  
  state.currentIndex++;
  return state.timeline[state.currentIndex];
}

/**
 * Force undo despite conflicts
 */
export function forceUndo(state: CheckpointManagerState): TimelineAction | null {
  if (state.currentIndex < 0) {
    return null;
  }
  
  const action = state.timeline[state.currentIndex];
  state.currentIndex--;
  
  // Clear related conflicts
  state.conflicts.forEach((conflict, id) => {
    if (conflict.actionId === action.id) {
      state.conflicts.delete(id);
    }
  });
  
  return action;
}

/**
 * Check if can undo
 */
export function canUndo(state: CheckpointManagerState): boolean {
  return state.currentIndex >= 0;
}

/**
 * Check if can redo
 */
export function canRedo(state: CheckpointManagerState): boolean {
  return state.currentIndex < state.timeline.length - 1;
}

/**
 * Get current timeline position info
 */
export function getTimelinePosition(state: CheckpointManagerState): {
  current: number;
  total: number;
  canUndo: boolean;
  canRedo: boolean;
} {
  return {
    current: state.currentIndex + 1,
    total: state.timeline.length,
    canUndo: canUndo(state),
    canRedo: canRedo(state),
  };
}

/**
 * Create a named checkpoint
 */
export function createCheckpoint(
  state: CheckpointManagerState,
  name: string,
  currentState: unknown,
  options: CheckpointOptions = {}
): Checkpoint {
  const now = Date.now();
  
  const checkpoint: Checkpoint = {
    id: `checkpoint-${now}-${Math.random().toString(36).substr(2, 9)}`,
    name: options.autoName ? `Checkpoint ${new Date(now).toLocaleString()}` : name,
    description: options.description,
    timestamp: now,
    actorId: state.currentActorId,
    actorName: state.currentActorName,
    historyIndex: state.currentIndex,
    state: structuredClone(currentState),
    tags: options.tags,
  };
  
  state.checkpoints.set(checkpoint.id, checkpoint);
  return checkpoint;
}

/**
 * Get checkpoint by ID
 */
export function getCheckpoint(
  state: CheckpointManagerState,
  checkpointId: string
): Checkpoint | null {
  return state.checkpoints.get(checkpointId) || null;
}

/**
 * Get all checkpoints in chronological order
 */
export function getAllCheckpoints(state: CheckpointManagerState): Checkpoint[] {
  return Array.from(state.checkpoints.values()).sort(
    (a, b) => a.timestamp - b.timestamp
  );
}

/**
 * Restore to a checkpoint (creates a branch if not at that point)
 */
export function restoreCheckpoint(
  state: CheckpointManagerState,
  checkpointId: string
): { checkpoint: Checkpoint; branch: TimelineBranch | null } | null {
  const checkpoint = state.checkpoints.get(checkpointId);
  if (!checkpoint) {
    return null;
  }
  
  // If we're not at the checkpoint index, create a branch
  let branch: TimelineBranch | null = null;
  if (state.currentIndex !== checkpoint.historyIndex) {
    branch = createBranch(
      state,
      `Branch from ${checkpoint.name}`,
      checkpointId
    );
  }
  
  // Restore to checkpoint index
  state.currentIndex = checkpoint.historyIndex;
  
  return { checkpoint, branch };
}

/**
 * Delete a checkpoint
 */
export function deleteCheckpoint(
  state: CheckpointManagerState,
  checkpointId: string
): boolean {
  return state.checkpoints.delete(checkpointId);
}

/**
 * Update checkpoint metadata
 */
export function updateCheckpoint(
  state: CheckpointManagerState,
  checkpointId: string,
  updates: Partial<Pick<Checkpoint, 'name' | 'description' | 'tags'>>
): Checkpoint | null {
  const checkpoint = state.checkpoints.get(checkpointId);
  if (!checkpoint) {
    return null;
  }
  
  if (updates.name !== undefined) checkpoint.name = updates.name;
  if (updates.description !== undefined) checkpoint.description = updates.description;
  if (updates.tags !== undefined) checkpoint.tags = updates.tags;
  
  return checkpoint;
}

/**
 * Create a new branch from current position
 */
export function createBranch(
  state: CheckpointManagerState,
  name: string,
  parentCheckpointId: string
): TimelineBranch {
  const now = Date.now();
  
  const branch: TimelineBranch = {
    id: `branch-${now}-${Math.random().toString(36).substr(2, 9)}`,
    name,
    parentCheckpointId,
    createdAt: now,
    actions: [],
    checkpoints: [],
  };
  
  state.branches.set(branch.id, branch);
  state.activeBranchId = branch.id;
  
  return branch;
}

/**
 * Get branch by ID
 */
export function getBranch(
  state: CheckpointManagerState,
  branchId: string
): TimelineBranch | null {
  return state.branches.get(branchId) || null;
}

/**
 * Get all branches
 */
export function getAllBranches(state: CheckpointManagerState): TimelineBranch[] {
  return Array.from(state.branches.values());
}

/**
 * Switch to a different branch
 */
export function switchBranch(
  state: CheckpointManagerState,
  branchId: string
): TimelineBranch | null {
  const branch = state.branches.get(branchId);
  if (!branch) {
    return null;
  }
  
  state.activeBranchId = branchId;
  
  // Restore timeline to branch state
  // (In real implementation, would need to reconstruct timeline from branch)
  
  return branch;
}

/**
 * Get conflict by ID
 */
export function getConflict(
  state: CheckpointManagerState,
  conflictId: string
): UndoConflict | null {
  return state.conflicts.get(conflictId) || null;
}

/**
 * Get all unresolved conflicts
 */
export function getUnresolvedConflicts(state: CheckpointManagerState): UndoConflict[] {
  return Array.from(state.conflicts.values()).filter((c) => !c.resolution);
}

/**
 * Resolve a conflict
 */
export function resolveConflict(
  state: CheckpointManagerState,
  conflictId: string,
  options: ConflictResolutionOptions
): boolean {
  const conflict = state.conflicts.get(conflictId);
  if (!conflict) {
    return false;
  }
  
  conflict.resolution = options.strategy;
  
  if (options.strategy === 'force') {
    // Force undo by moving index back (but keep conflict record with resolution)
    if (state.currentIndex >= 0) {
      state.currentIndex--;
    }
  } else if (options.strategy === 'cancel') {
    // Do nothing with state, just mark resolved and remove conflict
    state.conflicts.delete(conflictId);
  } else if (options.strategy === 'merge' && options.mergeFunction) {
    // Custom merge logic (would need current state passed in)
    // This is a placeholder - real implementation would apply merge
  }
  
  return true;
}

/**
 * Clear resolved conflicts
 */
export function clearResolvedConflicts(state: CheckpointManagerState): number {
  let cleared = 0;
  state.conflicts.forEach((conflict, id) => {
    if (conflict.resolution) {
      state.conflicts.delete(id);
      cleared++;
    }
  });
  return cleared;
}

/**
 * Get timeline actions for a specific actor
 */
export function getActionsByActor(
  state: CheckpointManagerState,
  actorId: string
): TimelineAction[] {
  return state.timeline.filter((action) => action.actorId === actorId);
}

/**
 * Get timeline actions by type
 */
export function getActionsByType(
  state: CheckpointManagerState,
  type: string
): TimelineAction[] {
  return state.timeline.filter((action) => action.type === type);
}

/**
 * Get timeline actions in time range
 */
export function getActionsInRange(
  state: CheckpointManagerState,
  startTime: number,
  endTime: number
): TimelineAction[] {
  return state.timeline.filter(
    (action) => action.timestamp >= startTime && action.timestamp <= endTime
  );
}

/**
 * Get timeline statistics
 */
export function getTimelineStats(state: CheckpointManagerState): {
  totalActions: number;
  actorCount: number;
  actionTypes: string[];
  oldestAction: TimelineAction | null;
  newestAction: TimelineAction | null;
  checkpointCount: number;
  branchCount: number;
  conflictCount: number;
} {
  const actors = new Set(state.timeline.map((a) => a.actorId));
  const types = new Set(state.timeline.map((a) => a.type));
  
  return {
    totalActions: state.timeline.length,
    actorCount: actors.size,
    actionTypes: Array.from(types),
    oldestAction: state.timeline[0] || null,
    newestAction: state.timeline[state.timeline.length - 1] || null,
    checkpointCount: state.checkpoints.size,
    branchCount: state.branches.size,
    conflictCount: state.conflicts.size,
  };
}

/**
 * Search checkpoints by name or tag
 */
export function searchCheckpoints(
  state: CheckpointManagerState,
  query: string
): Checkpoint[] {
  const lowerQuery = query.toLowerCase();
  
  return Array.from(state.checkpoints.values()).filter((checkpoint) => {
    const nameMatch = checkpoint.name.toLowerCase().includes(lowerQuery);
    const descMatch = checkpoint.description?.toLowerCase().includes(lowerQuery);
    const tagMatch = checkpoint.tags?.some((tag) =>
      tag.toLowerCase().includes(lowerQuery)
    );
    
    return nameMatch || descMatch || tagMatch;
  });
}

/**
 * Clear all timeline history
 */
export function clearTimeline(state: CheckpointManagerState): void {
  state.timeline = [];
  state.currentIndex = -1;
  state.checkpoints.clear();
  state.conflicts.clear();
}

/**
 * Export timeline as JSON
 */
export function exportTimeline(state: CheckpointManagerState): string {
  return JSON.stringify({
    timeline: state.timeline,
    currentIndex: state.currentIndex,
    checkpoints: Array.from(state.checkpoints.entries()),
    branches: Array.from(state.branches.entries()),
    conflicts: Array.from(state.conflicts.entries()),
  }, null, 2);
}

/**
 * Import timeline from JSON
 */
export function importTimeline(
  state: CheckpointManagerState,
  json: string
): boolean {
  try {
    const data = JSON.parse(json);
    
    state.timeline = data.timeline || [];
    state.currentIndex = data.currentIndex ?? -1;
    state.checkpoints = new Map(data.checkpoints || []);
    state.branches = new Map(data.branches || []);
    state.conflicts = new Map(data.conflicts || []);
    
    return true;
  } catch (error) {
    return false;
  }
}
