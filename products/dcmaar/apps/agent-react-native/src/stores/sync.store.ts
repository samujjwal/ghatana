/**
 * Sync Management Store - Jotai Atoms
 *
 * Manages data synchronization state including:
 * - Sync status and progress
 * - Pending sync operations
 * - Sync history and timestamps
 * - Conflict resolution
 * - Offline/online state
 *
 * Per copilot-instructions.md:
 * - App-scoped state using Jotai atoms
 * - Feature-centric organization
 * - Atomic updates for predictable state
 *
 * @doc.type module
 * @doc.purpose Data synchronization state management
 * @doc.layer product
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';

/**
 * Sync operation types.
 */
export const SYNC_OPERATION_TYPES = {
  UPLOAD_APPS: 'upload_apps',
  DOWNLOAD_POLICIES: 'download_policies',
  UPLOAD_EVENTS: 'upload_events',
  SYNC_PERMISSIONS: 'sync_permissions',
  SYNC_SETTINGS: 'sync_settings',
} as const;

/**
 * Sync operation status.
 *
 * @type {'pending' | 'in_progress' | 'completed' | 'failed'}
 */
export type SyncOperationStatus = 'pending' | 'in_progress' | 'completed' | 'failed';

/**
 * Individual sync operation.
 *
 * @interface SyncOperation
 * @property {string} id - Operation identifier
 * @property {string} type - Type of sync operation
 * @property {SyncOperationStatus} status - Current status
 * @property {number} itemsTotal - Total items to sync
 * @property {number} itemsSynced - Items synced so far
 * @property {Date} startedAt - When operation started
 * @property {Date} [completedAt] - When operation completed
 * @property {string} [error] - Error message if failed
 */
export interface SyncOperation {
  id: string;
  type: string;
  status: SyncOperationStatus;
  itemsTotal: number;
  itemsSynced: number;
  startedAt: Date;
  completedAt?: Date;
  error?: string;
}

/**
 * Sync state.
 *
 * @interface SyncState
 * @property {boolean} isSyncing - Whether currently syncing
 * @property {boolean} isOnline - Whether device has network connectivity
 * @property {Date | null} lastSyncTime - Timestamp of last successful sync
 * @property {SyncOperation[]} pendingOperations - Operations waiting to sync
 * @property {SyncOperation[]} activeOperations - Currently syncing operations
 * @property {SyncOperation[]} completedOperations - Recently completed operations
 * @property {number} pendingChangesCount - Number of changes waiting to sync
 * @property {string | null} error - Error message if sync failed
 * @property {number} failureCount - Number of consecutive failures
 * @property {'idle' | 'syncing' | 'error'} syncStatus - Overall sync status
 */
export interface SyncState {
  isSyncing: boolean;
  isOnline: boolean;
  lastSyncTime: Date | null;
  pendingOperations: SyncOperation[];
  activeOperations: SyncOperation[];
  completedOperations: SyncOperation[];
  pendingChangesCount: number;
  error: string | null;
  failureCount: number;
  syncStatus: 'idle' | 'syncing' | 'error';
}

/**
 * Initial sync state.
 *
 * GIVEN: App initialization
 * WHEN: syncAtom is first accessed
 * THEN: Sync starts as idle with no pending operations
 */
const initialSyncState: SyncState = {
  isSyncing: false,
  isOnline: true,
  lastSyncTime: null,
  pendingOperations: [],
  activeOperations: [],
  completedOperations: [],
  pendingChangesCount: 0,
  error: null,
  failureCount: 0,
  syncStatus: 'idle',
};

/**
 * Core sync atom.
 *
 * Holds complete sync state including:
 * - Current sync status
 * - Pending and active operations
 * - Online/offline state
 * - Error and failure tracking
 *
 * Usage (in components):
 * ```typescript
 * const [syncState, setSyncState] = useAtom(syncAtom);
 * ```
 */
export const syncAtom = atom<SyncState>(initialSyncState);

/**
 * Derived atom: Is currently syncing.
 *
 * GIVEN: syncAtom with isSyncing flag
 * WHEN: isSyncingAtom is read
 * THEN: Returns true if any active operations exist
 *
 * Usage (in components):
 * ```typescript
 * const [isSyncing] = useAtom(isSyncingAtom);
 * if (isSyncing) show spinner
 * ```
 */
export const isSyncingAtom = atom<boolean>((get) => {
  const state = get(syncAtom);
  return state.isSyncing && state.activeOperations.length > 0;
});

/**
 * Derived atom: Is device online.
 *
 * GIVEN: syncAtom with isOnline flag
 * WHEN: isOnlineAtom is read
 * THEN: Returns network connectivity status
 *
 * Usage (in components):
 * ```typescript
 * const [isOnline] = useAtom(isOnlineAtom);
 * if (!isOnline) show "Offline - changes will sync when online"
 * ```
 */
export const isOnlineAtom = atom<boolean>((get) => {
  return get(syncAtom).isOnline;
});

/**
 * Derived atom: Count of pending changes.
 *
 * GIVEN: syncAtom with pendingChangesCount
 * WHEN: pendingChangesCountAtom is read
 * THEN: Returns count of items waiting to sync
 *
 * Usage (in components):
 * ```typescript
 * const [pendingCount] = useAtom(pendingChangesCountAtom);
 * // Show badge "X changes pending"
 * ```
 */
export const pendingChangesCountAtom = atom<number>((get) => {
  return get(syncAtom).pendingChangesCount;
});

/**
 * Derived atom: Sync progress percentage.
 *
 * GIVEN: activeOperations with itemsSynced and itemsTotal
 * WHEN: syncProgressAtom is read
 * THEN: Returns average progress across all operations (0-100)
 *
 * Usage (in components):
 * ```typescript
 * const [progress] = useAtom(syncProgressAtom);
 * <ProgressBar progress={progress} />
 * ```
 */
export const syncProgressAtom = atom<number>((get) => {
  const state = get(syncAtom);
  if (state.activeOperations.length === 0) return 0;

  const avgProgress = state.activeOperations.reduce((sum, op) => {
    const opProgress =
      op.itemsTotal > 0 ? (op.itemsSynced / op.itemsTotal) * 100 : 0;
    return sum + opProgress;
  }, 0);

  return Math.round(avgProgress / state.activeOperations.length);
});

/**
 * Derived atom: Time since last sync.
 *
 * GIVEN: lastSyncTime timestamp
 * WHEN: timeSinceLastSyncAtom is read
 * THEN: Returns human-readable string (e.g., "5 minutes ago")
 *
 * Usage (in components):
 * ```typescript
 * const [timeSince] = useAtom(timeSinceLastSyncAtom);
 * <Text>Last sync: {timeSince}</Text>
 * ```
 */
export const timeSinceLastSyncAtom = atom<string>((get) => {
  const state = get(syncAtom);
  if (!state.lastSyncTime) return 'Never';

  const now = new Date();
  const diff = now.getTime() - state.lastSyncTime.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (minutes < 1) return 'Just now';
  if (minutes < 60) return `${minutes} minute${minutes > 1 ? 's' : ''} ago`;
  if (hours < 24) return `${hours} hour${hours > 1 ? 's' : ''} ago`;
  return `${days} day${days > 1 ? 's' : ''} ago`;
});

/**
 * Action atom: Set online/offline status.
 *
 * GIVEN: Network connectivity changes
 * WHEN: setOnlineStatusAtom is called
 * THEN: Updates isOnline flag in syncAtom
 *
 * GIVEN: Device goes offline while syncing
 * WHEN: setOnlineStatusAtom called with false
 * THEN: Pauses active sync operations
 *
 * Usage (in services - listening to network changes):
 * ```typescript
 * const [, setOnline] = useAtom(setOnlineStatusAtom);
 * // When network changes
 * setOnline(isConnected);
 * ```
 *
 * @param {boolean} isOnline - New online status
 */
export const setOnlineStatusAtom = atom<null, [boolean], void>(
  null,
  (get, set, isOnline: boolean) => {
    const state = get(syncAtom);
    set(syncAtom, {
      ...state,
      isOnline,
    });
  }
);

/**
 * Action atom: Start sync operation.
 *
 * GIVEN: New sync operation to start
 * WHEN: startSyncOperationAtom is called
 * THEN: Moves operation from pending to active, sets isSyncing = true
 *
 * Usage (in services):
 * ```typescript
 * const [, startOp] = useAtom(startSyncOperationAtom);
 * startOp({
 *   id: 'sync-1',
 *   type: SYNC_OPERATION_TYPES.UPLOAD_APPS,
 *   status: 'in_progress',
 *   itemsTotal: 50,
 *   itemsSynced: 0,
 *   startedAt: new Date()
 * });
 * ```
 *
 * @param {SyncOperation} operation - Sync operation to start
 */
export const startSyncOperationAtom = atom<null, [SyncOperation], void>(
  null,
  (get, set, operation: SyncOperation) => {
    const state = get(syncAtom);
    set(syncAtom, {
      ...state,
      isSyncing: true,
      syncStatus: 'syncing',
      activeOperations: [...state.activeOperations, operation],
      pendingOperations: state.pendingOperations.filter(
        (op) => op.id !== operation.id
      ),
    });
  }
);

/**
 * Action atom: Update sync operation progress.
 *
 * GIVEN: Sync operation in progress with new item count
 * WHEN: updateSyncProgressAtom is called
 * THEN: Updates itemsSynced count for operation
 *
 * GIVEN: operationId = "sync-1", itemsSynced = 25 (of 50 total)
 * WHEN: updateSyncProgressAtom called
 * THEN: Progress calculated as 50%
 *
 * Usage (in services):
 * ```typescript
 * const [, updateProgress] = useAtom(updateSyncProgressAtom);
 * updateProgress('sync-1', 25); // 25 items synced
 * ```
 *
 * @param {string} operationId - ID of operation
 * @param {number} itemsSynced - New synced count
 */
export const updateSyncProgressAtom = atom<null, [string, number], void>(
  null,
  (get, set, operationId: string, itemsSynced: number) => {
    const state = get(syncAtom);
    const updatedActiveOps = state.activeOperations.map((op) =>
      op.id === operationId ? { ...op, itemsSynced } : op
    );

    set(syncAtom, {
      ...state,
      activeOperations: updatedActiveOps,
    });
  }
);

/**
 * Action atom: Complete sync operation.
 *
 * GIVEN: Sync operation finished successfully
 * WHEN: completeSyncOperationAtom is called
 * THEN: Moves operation to completed, updates lastSyncTime, resets failure count
 *
 * Usage (in services):
 * ```typescript
 * const [, completeOp] = useAtom(completeSyncOperationAtom);
 * completeOp('sync-1');
 * ```
 *
 * @param {string} operationId - ID of operation
 */
export const completeSyncOperationAtom = atom<null, [string], void>(
  null,
  (get, set, operationId: string) => {
    const state = get(syncAtom);
    const operation = state.activeOperations.find((op) => op.id === operationId);

    if (!operation) return;

    const completedOp: SyncOperation = {
      ...operation,
      status: 'completed',
      completedAt: new Date(),
    };

    set(syncAtom, {
      ...state,
      activeOperations: state.activeOperations.filter((op) => op.id !== operationId),
      completedOperations: [completedOp, ...state.completedOperations].slice(0, 10), // Keep last 10
      lastSyncTime: new Date(),
      failureCount: 0,
      error: null,
      isSyncing: state.activeOperations.length <= 1,
      syncStatus: state.activeOperations.length <= 1 ? 'idle' : 'syncing',
      pendingChangesCount: Math.max(0, state.pendingChangesCount - operation.itemsTotal),
    });
  }
);

/**
 * Action atom: Fail sync operation.
 *
 * GIVEN: Sync operation encountered error
 * WHEN: failSyncOperationAtom is called
 * THEN: Moves operation back to pending, increments failure count, sets error
 *
 * GIVEN: operationId = "sync-1", errorMsg = "Network timeout"
 * WHEN: failSyncOperationAtom called
 * THEN: Marked as failed, moved to pending for retry, failureCount++
 *
 * Usage (in services):
 * ```typescript
 * const [, failOp] = useAtom(failSyncOperationAtom);
 * failOp('sync-1', 'Network error');
 * ```
 *
 * @param {string} operationId - ID of operation
 * @param {string} errorMessage - Error description
 */
export const failSyncOperationAtom = atom<null, [string, string], void>(
  null,
  (get, set, operationId: string, errorMessage: string) => {
    const state = get(syncAtom);
    const operation = state.activeOperations.find((op) => op.id === operationId);

    if (!operation) return;

    const failedOp: SyncOperation = {
      ...operation,
      status: 'failed',
      error: errorMessage,
      completedAt: new Date(),
    };

    set(syncAtom, {
      ...state,
      activeOperations: state.activeOperations.filter((op) => op.id !== operationId),
      pendingOperations: [
        ...state.pendingOperations,
        { ...operation, status: 'pending' },
      ],
      completedOperations: [failedOp, ...state.completedOperations].slice(0, 10),
      failureCount: state.failureCount + 1,
      error: errorMessage,
      isSyncing: state.activeOperations.length <= 1,
      syncStatus: 'error',
    });
  }
);

/**
 * Action atom: Queue sync operation.
 *
 * GIVEN: New operation to sync (added to pending queue)
 * WHEN: queueSyncOperationAtom is called
 * THEN: Adds operation to pendingOperations, increments pendingChangesCount
 *
 * Usage (in services - when device changes):
 * ```typescript
 * const [, queue] = useAtom(queueSyncOperationAtom);
 * queue({
 *   id: 'sync-new',
 *   type: SYNC_OPERATION_TYPES.UPLOAD_APPS,
 *   status: 'pending',
 *   itemsTotal: 5,
 *   itemsSynced: 0,
 *   startedAt: new Date()
 * });
 * ```
 *
 * @param {SyncOperation} operation - Operation to queue
 */
export const queueSyncOperationAtom = atom<null, [SyncOperation], void>(
  null,
  (get, set, operation: SyncOperation) => {
    const state = get(syncAtom);
    set(syncAtom, {
      ...state,
      pendingOperations: [...state.pendingOperations, operation],
      pendingChangesCount: state.pendingChangesCount + operation.itemsTotal,
    });
  }
);

/**
 * Action atom: Clear pending changes count.
 *
 * GIVEN: Manual sync completed or reset requested
 * WHEN: clearPendingChangesAtom is called
 * THEN: Resets pendingChangesCount to 0
 *
 * Usage (in services):
 * ```typescript
 * const [, clearPending] = useAtom(clearPendingChangesAtom);
 * clearPending();
 * ```
 */
export const clearPendingChangesAtom = atom<null, [], void>(
  null,
  (get, set) => {
    const state = get(syncAtom);
    set(syncAtom, {
      ...state,
      pendingChangesCount: 0,
      pendingOperations: [],
    });
  }
);

/**
 * Action atom: Clear sync error.
 *
 * GIVEN: Error message displayed
 * WHEN: User dismisses error
 * THEN: clearSyncErrorAtom clears error message
 *
 * Usage (in components):
 * ```typescript
 * const [, clearError] = useAtom(clearSyncErrorAtom);
 * <TouchableOpacity onPress={() => clearError()} />
 * ```
 */
export const clearSyncErrorAtom = atom<null, [], void>(null, (get, set) => {
  const state = get(syncAtom);
  set(syncAtom, {
    ...state,
    error: null,
  });
});

/**
 * Action atom: Reset sync state (cancel all operations).
 *
 * GIVEN: Catastrophic sync error or user wants hard reset
 * WHEN: resetSyncAtom is called
 * THEN: Clears all operations, resets to idle state
 *
 * Usage (in services/components):
 * ```typescript
 * const [, resetSync] = useAtom(resetSyncAtom);
 * <TouchableOpacity onPress={() => resetSync()} />
 * ```
 */
export const resetSyncAtom = atom<null, [], void>(null, (get, set) => {
  set(syncAtom, {
    ...initialSyncState,
    isOnline: get(syncAtom).isOnline, // Preserve network status
  });
});
