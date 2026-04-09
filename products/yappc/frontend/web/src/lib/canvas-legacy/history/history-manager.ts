/**
 * @ghatana/yappc-canvas - History & Document Management Utilities
 *
 * Feature 1.4: Document Management
 * Provides utilities for undo/redo enhancement, version diffing,
 * template management, and autosave coordination.
 *
 * @module libs/canvas/src/history/historyManager
 */

/**
 * History entry with enhanced metadata
 */
export interface HistoryEntry<T = unknown> {
  id: string;
  timestamp: number;
  type: string;
  before: T;
  after: T;
  metadata?: {
    userId?: string;
    description?: string;
    tags?: string[];
  };
}

/**
 * History stack configuration
 */
export interface HistoryConfig {
  maxSize?: number; // Max entries before pruning (default: 50)
  batchWindow?: number; // Time window for batching (ms, default: 500)
  enableCompression?: boolean; // Enable state compression (default: false)
}

/**
 * History manager state
 */
export interface HistoryState<T = unknown> {
  past: HistoryEntry<T>[];
  future: HistoryEntry<T>[];
  current: T | null;
  config: Required<HistoryConfig>;
}

/**
 * Version metadata for document snapshots
 */
export interface DocumentVersion<T = unknown> {
  id: string;
  documentId: string;
  version: number;
  timestamp: number;
  state: T;
  author?: string;
  description?: string;
  tags?: string[];
  parentVersion?: string; // For branching
}

/**
 * Diff result between two versions
 */
export interface VersionDiff {
  type: 'added' | 'removed' | 'modified';
  path: string[];
  before?: unknown;
  after?: unknown;
  isStructural: boolean; // vs styling-only change
}

/**
 * Template metadata
 */
export interface DocumentTemplate<T = unknown> {
  id: string;
  name: string;
  description?: string;
  preview?: string; // Base64 image or URL
  state: T;
  category?: string;
  tags?: string[];
  createdAt: number;
  updatedAt: number;
  author?: string;
}

/**
 * Autosave coordination state
 */
export interface AutosaveState {
  enabled: boolean;
  interval: number; // ms
  lastSaved: number | null;
  isDirty: boolean;
  isPending: boolean;
}

/**
 * Create a new history manager instance
 */
export function createHistoryManager<T>(
  initialState: T,
  config: HistoryConfig = {}
): HistoryState<T> {
  return {
    past: [],
    future: [],
    current: initialState,
    config: {
      maxSize: config.maxSize ?? 50,
      batchWindow: config.batchWindow ?? 500,
      enableCompression: config.enableCompression ?? false,
    },
  };
}

/**
 * Add a new history entry
 *
 * @param history - Current history state
 * @param entry - Entry to add
 * @returns Updated history state
 */
export function addHistory<T>(
  history: HistoryState<T>,
  entry: Omit<HistoryEntry<T>, 'id' | 'timestamp'>
): HistoryState<T> {
  const newEntry: HistoryEntry<T> = {
    ...entry,
    id: generateId(),
    timestamp: Date.now(),
  };

  const past = [...history.past, newEntry];

  // Prune if exceeds maxSize
  const prunedPast =
    past.length > history.config.maxSize
      ? past.slice(past.length - history.config.maxSize)
      : past;

  return {
    ...history,
    past: prunedPast,
    future: [], // Clear future when new action taken
    current: entry.after,
  };
}

/**
 * Undo last action
 *
 * @param history - Current history state
 * @returns Updated history state or null if nothing to undo
 */
export function undo<T>(history: HistoryState<T>): HistoryState<T> | null {
  if (history.past.length === 0) {
    return null;
  }

  const lastEntry = history.past[history.past.length - 1];
  const past = history.past.slice(0, -1);
  const future = [lastEntry, ...history.future];

  return {
    ...history,
    past,
    future,
    current: lastEntry.before,
  };
}

/**
 * Redo last undone action
 *
 * @param history - Current history state
 * @returns Updated history state or null if nothing to redo
 */
export function redo<T>(history: HistoryState<T>): HistoryState<T> | null {
  if (history.future.length === 0) {
    return null;
  }

  const nextEntry = history.future[0];
  const future = history.future.slice(1);
  const past = [...history.past, nextEntry];

  return {
    ...history,
    past,
    future,
    current: nextEntry.after,
  };
}

/**
 * Check if undo is available
 */
export function canUndo<T>(history: HistoryState<T>): boolean {
  return history.past.length > 0;
}

/**
 * Check if redo is available
 */
export function canRedo<T>(history: HistoryState<T>): boolean {
  return history.future.length > 0;
}

/**
 * Clear all history
 */
export function clearHistory<T>(history: HistoryState<T>): HistoryState<T> {
  return {
    ...history,
    past: [],
    future: [],
  };
}

/**
 * Batch multiple entries within time window
 *
 * Groups consecutive entries of the same type within batchWindow ms
 * into a single entry for cleaner history.
 *
 * @param history - Current history state
 * @returns History with batched entries
 */
export function batchHistory<T>(history: HistoryState<T>): HistoryState<T> {
  if (history.past.length < 2) {
    return history;
  }

  const batched: HistoryEntry<T>[] = [];
  let currentBatch: HistoryEntry<T>[] = [];

  for (const entry of history.past) {
    if (currentBatch.length === 0) {
      currentBatch.push(entry);
      continue;
    }

    const lastInBatch = currentBatch[currentBatch.length - 1];
    const timeDiff = entry.timestamp - lastInBatch.timestamp;
    const sameType = entry.type === lastInBatch.type;

    if (sameType && timeDiff <= history.config.batchWindow) {
      // Add to current batch
      currentBatch.push(entry);
    } else {
      // Finalize current batch and start new one
      if (currentBatch.length === 1) {
        batched.push(currentBatch[0]);
      } else {
        // Merge batch into single entry
        const merged: HistoryEntry<T> = {
          id: currentBatch[0].id,
          type: currentBatch[0].type,
          timestamp: currentBatch[0].timestamp,
          before: currentBatch[0].before,
          after: currentBatch[currentBatch.length - 1].after,
          metadata: {
            ...currentBatch[0].metadata,
            description: `Batched ${currentBatch.length} ${currentBatch[0].type} actions`,
          },
        };
        batched.push(merged);
      }
      currentBatch = [entry];
    }
  }

  // Handle final batch
  if (currentBatch.length > 0) {
    if (currentBatch.length === 1) {
      batched.push(currentBatch[0]);
    } else {
      const merged: HistoryEntry<T> = {
        id: currentBatch[0].id,
        type: currentBatch[0].type,
        timestamp: currentBatch[0].timestamp,
        before: currentBatch[0].before,
        after: currentBatch[currentBatch.length - 1].after,
        metadata: {
          ...currentBatch[0].metadata,
          description: `Batched ${currentBatch.length} ${currentBatch[0].type} actions`,
        },
      };
      batched.push(merged);
    }
  }

  return {
    ...history,
    past: batched,
  };
}

/**
 * Create a version snapshot
 *
 * @param documentId - Document identifier
 * @param state - Current document state
 * @param metadata - Optional version metadata
 * @returns New version object
 */
export function createVersion<T>(
  documentId: string,
  state: T,
  metadata?: Partial<
    Omit<DocumentVersion<T>, 'id' | 'documentId' | 'timestamp' | 'state'>
  >
): DocumentVersion<T> {
  return {
    id: generateId(),
    documentId,
    version: metadata?.version ?? 1,
    timestamp: Date.now(),
    state,
    author: metadata?.author,
    description: metadata?.description,
    tags: metadata?.tags,
    parentVersion: metadata?.parentVersion,
  };
}

/**
 * Compare two versions and generate diff
 *
 * @param before - Previous version state
 * @param after - Current version state
 * @returns Array of differences
 */
export function diffVersions<T extends Record<string, unknown>>(
  before: T,
  after: T
): VersionDiff[] {
  const diffs: VersionDiff[] = [];

  // Helper to determine if change is structural vs styling
  const isStructuralChange = (path: string[]): boolean => {
    const structuralKeys = [
      'position',
      'data',
      'type',
      'connections',
      'layerIndex',
    ];
    return path.some((key) => structuralKeys.includes(key));
  };

  // Recursive diff helper
  /**
   *
   */
  function diffObjects(
    obj1: unknown,
    obj2: unknown,
    path: string[] = []
  ): void {
    if (obj1 === obj2) return;

    // Handle arrays
    if (Array.isArray(obj1) && Array.isArray(obj2)) {
      const maxLen = Math.max(obj1.length, obj2.length);
      for (let i = 0; i < maxLen; i++) {
        if (i >= obj1.length) {
          diffs.push({
            type: 'added',
            path: [...path, String(i)],
            after: obj2[i],
            isStructural: isStructuralChange([...path, String(i)]),
          });
        } else if (i >= obj2.length) {
          diffs.push({
            type: 'removed',
            path: [...path, String(i)],
            before: obj1[i],
            isStructural: isStructuralChange([...path, String(i)]),
          });
        } else if (obj1[i] !== obj2[i]) {
          diffObjects(obj1[i], obj2[i], [...path, String(i)]);
        }
      }
      return;
    }

    // Handle objects
    if (
      typeof obj1 === 'object' &&
      obj1 !== null &&
      typeof obj2 === 'object' &&
      obj2 !== null
    ) {
      const keys1 = Object.keys(obj1 as Record<string, unknown>);
      const keys2 = Object.keys(obj2 as Record<string, unknown>);
      const allKeys = new Set([...keys1, ...keys2]);

      for (const key of allKeys) {
        const val1 = (obj1 as Record<string, unknown>)[key];
        const val2 = (obj2 as Record<string, unknown>)[key];

        if (!(key in (obj1 as Record<string, unknown>))) {
          diffs.push({
            type: 'added',
            path: [...path, key],
            after: val2,
            isStructural: isStructuralChange([...path, key]),
          });
        } else if (!(key in (obj2 as Record<string, unknown>))) {
          diffs.push({
            type: 'removed',
            path: [...path, key],
            before: val1,
            isStructural: isStructuralChange([...path, key]),
          });
        } else if (val1 !== val2) {
          if (
            typeof val1 === 'object' &&
            val1 !== null &&
            typeof val2 === 'object' &&
            val2 !== null
          ) {
            diffObjects(val1, val2, [...path, key]);
          } else {
            diffs.push({
              type: 'modified',
              path: [...path, key],
              before: val1,
              after: val2,
              isStructural: isStructuralChange([...path, key]),
            });
          }
        }
      }
      return;
    }

    // Primitive values differ
    diffs.push({
      type: 'modified',
      path,
      before: obj1,
      after: obj2,
      isStructural: isStructuralChange(path),
    });
  }

  diffObjects(before, after);
  return diffs;
}

/**
 * Create a template from current state
 *
 * @param state - Document state to save as template
 * @param metadata - Template metadata
 * @returns New template object
 */
export function createTemplate<T>(
  state: T,
  metadata: Pick<DocumentTemplate<T>, 'name'> &
    Partial<
      Omit<DocumentTemplate<T>, 'id' | 'state' | 'createdAt' | 'updatedAt'>
    >
): DocumentTemplate<T> {
  const now = Date.now();
  return {
    id: generateId(),
    state,
    createdAt: now,
    updatedAt: now,
    ...metadata,
  };
}

/**
 * Update template metadata
 *
 * @param template - Existing template
 * @param updates - Metadata updates
 * @returns Updated template
 */
export function updateTemplate<T>(
  template: DocumentTemplate<T>,
  updates: Partial<Omit<DocumentTemplate<T>, 'id' | 'createdAt'>>
): DocumentTemplate<T> {
  return {
    ...template,
    ...updates,
    updatedAt: Date.now(),
  };
}

/**
 * Filter templates by criteria
 *
 * @param templates - Array of templates
 * @param filters - Filter criteria
 * @returns Filtered templates
 */
export function filterTemplates<T>(
  templates: DocumentTemplate<T>[],
  filters: {
    category?: string;
    tags?: string[];
    author?: string;
    searchText?: string;
  }
): DocumentTemplate<T>[] {
  return templates.filter((template) => {
    if (filters.category && template.category !== filters.category) {
      return false;
    }

    if (filters.author && template.author !== filters.author) {
      return false;
    }

    if (filters.tags && filters.tags.length > 0) {
      if (
        !template.tags ||
        !filters.tags.some((tag) => template.tags!.includes(tag))
      ) {
        return false;
      }
    }

    if (filters.searchText) {
      const text = filters.searchText.toLowerCase();
      const matchName = template.name.toLowerCase().includes(text);
      const matchDesc =
        template.description?.toLowerCase().includes(text) ?? false;
      if (!matchName && !matchDesc) {
        return false;
      }
    }

    return true;
  });
}

/**
 * Create autosave coordinator state
 *
 * @param config - Autosave configuration
 * @returns Initial autosave state
 */
export function createAutosaveState(
  config: {
    enabled?: boolean;
    interval?: number;
  } = {}
): AutosaveState {
  return {
    enabled: config.enabled ?? true,
    interval: config.interval ?? 5000, // 5 seconds default
    lastSaved: null,
    isDirty: false,
    isPending: false,
  };
}

/**
 * Check if autosave should trigger
 *
 * @param state - Current autosave state
 * @param now - Current timestamp (default: Date.now())
 * @returns true if save should trigger
 */
export function shouldAutosave(
  state: AutosaveState,
  now: number = Date.now()
): boolean {
  if (!state.enabled || !state.isDirty || state.isPending) {
    return false;
  }

  if (state.lastSaved === null) {
    return true; // First save
  }

  return now - state.lastSaved >= state.interval;
}

/**
 * Mark state as dirty (needs save)
 *
 * @param state - Current autosave state
 * @returns Updated autosave state
 */
export function markDirty(state: AutosaveState): AutosaveState {
  return {
    ...state,
    isDirty: true,
  };
}

/**
 * Mark save as complete
 *
 * @param state - Current autosave state
 * @returns Updated autosave state
 */
export function markSaved(
  state: AutosaveState,
  timestamp: number = Date.now()
): AutosaveState {
  return {
    ...state,
    isDirty: false,
    isPending: false,
    lastSaved: timestamp,
  };
}

/**
 * Mark save as pending
 *
 * @param state - Current autosave state
 * @returns Updated autosave state
 */
export function markSavePending(state: AutosaveState): AutosaveState {
  return {
    ...state,
    isPending: true,
  };
}

// Helper: Generate unique ID
/**
 *
 */
function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}
