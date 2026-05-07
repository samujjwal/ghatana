/**
 * Canvas Sync Service
 *
 * Unified sync state machine and history for canvas data.
 * Consolidates multiple persistence systems (localStorage, IndexedDB, API)
 * into a single observable status model: local-only → syncing → saved / failed / stale / conflict.
 *
 * @doc.type service
 * @doc.purpose Unified canvas sync state with truthful status reporting
 * @doc.layer product
 * @doc.pattern Service + State Machine
 */

import { logger } from '../../utils/Logger';
import type { CanvasSyncStatus } from './canvasSyncStatus';

export type CanvasSyncState = CanvasSyncStatus;

export interface SyncHistoryEntry {
  id: string;
  timestamp: number;
  state: CanvasSyncState;
  message: string;
  actor: 'user' | 'system' | 'remote';
}

export interface CanvasSyncSnapshot {
  projectId: string;
  state: CanvasSyncState;
  lastSyncedAt: number | null;
  remoteVersion: number | null;
  localVersion: number;
  history: SyncHistoryEntry[];
}

type SyncStateListener = (snapshot: CanvasSyncSnapshot) => void;

const MAX_HISTORY = 50;

function generateId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function trimHistory<T>(arr: T[], max: number): T[] {
  return arr.length > max ? arr.slice(arr.length - max) : arr;
}

/**
 * CanvasSyncService
 *
 * Single source of truth for canvas sync status.
 * Replaces scattered localStorage / IndexedDB / API status checks with one state machine.
 */
export class CanvasSyncService {
  private snapshot: CanvasSyncSnapshot;
  private listeners: SyncStateListener[] = [];
  private stateTimeouts: Map<string, ReturnType<typeof setTimeout>> = new Map();

  constructor(projectId: string) {
    this.snapshot = {
      projectId,
      state: 'local-only',
      lastSyncedAt: null,
      remoteVersion: null,
      localVersion: 0,
      history: [],
    };
  }

  public getSnapshot(): Readonly<CanvasSyncSnapshot> {
    return Object.freeze({ ...this.snapshot });
  }

  public getState(): CanvasSyncState {
    return this.snapshot.state;
  }

  public subscribe(listener: SyncStateListener): () => void {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter((l) => l !== listener);
    };
  }

  private emit(): void {
    const frozen = this.getSnapshot();
    for (const listener of this.listeners) {
      try {
        listener(frozen);
      } catch (error) {
        logger.warn('CanvasSyncService listener threw', 'canvas-sync', {
          error: error instanceof Error ? error.message : String(error),
        });
      }
    }
  }

  private logTransition(
    from: CanvasSyncState,
    to: CanvasSyncState,
    message: string,
    actor: SyncHistoryEntry['actor'] = 'system'
  ): void {
    if (from === to) return;

    const entry: SyncHistoryEntry = {
      id: generateId(),
      timestamp: Date.now(),
      state: to,
      message,
      actor,
    };

    this.snapshot = {
      ...this.snapshot,
      state: to,
      history: trimHistory([...this.snapshot.history, entry], MAX_HISTORY),
    };

    logger.info(`Canvas sync: ${from} → ${to}`, 'canvas-sync', { message, actor });
    this.emit();
  }

  /**
   * User initiated a local change.
   * If previously synced, mark as stale until sync completes.
   */
  public markLocalChange(): void {
    const from = this.snapshot.state;
    if (from === 'local-only') return;

    const to: CanvasSyncState = from === 'conflict' ? 'conflict' : 'stale';
    this.logTransition(from, to, 'Local modifications detected', 'user');
  }

  /**
   * Sync process started.
   */
  public startSync(): void {
    const from = this.snapshot.state;
    if (from === 'syncing') return;

    this.logTransition(from, 'syncing', 'Sync started', 'system');
  }

  /**
   * Sync completed successfully.
   */
  public syncSucceeded(remoteVersion?: number): void {
    const from = this.snapshot.state;
    if (from !== 'syncing' && from !== 'stale' && from !== 'local-only') {
      logger.warn('Unexpected syncSucceeded from state', 'canvas-sync', { from });
    }

    this.snapshot = {
      ...this.snapshot,
      lastSyncedAt: Date.now(),
      remoteVersion: remoteVersion ?? this.snapshot.remoteVersion,
      localVersion: remoteVersion ?? this.snapshot.localVersion + 1,
    };

    this.logTransition(from, 'remote-saved', 'Sync completed successfully', 'remote');
  }

  /**
   * Sync failed.
   */
  public syncFailed(errorMessage?: string): void {
    const from = this.snapshot.state;
    this.logTransition(
      from,
      'remote-failed',
      `Sync failed${errorMessage ? `: ${errorMessage}` : ''}`,
      'system'
    );
  }

  /**
   * Detected a conflict between local and remote.
   */
  public reportConflict(message?: string): void {
    const from = this.snapshot.state;
    this.logTransition(from, 'conflict', message ?? 'Conflict between local and remote state', 'remote');
  }

  /**
   * Mark stale explicitly (e.g., after detecting remote changes).
   */
  public markStale(reason?: string): void {
    const from = this.snapshot.state;
    if (from === 'stale' || from === 'conflict') return;
    this.logTransition(from, 'stale', reason ?? 'Remote state newer than local', 'remote');
  }

  /**
   * Resolve a conflict by choosing local or remote.
   */
  public resolveConflict(resolution: 'local' | 'remote'): void {
    const from = this.snapshot.state;
    if (from !== 'conflict') {
      logger.warn('resolveConflict called outside conflict state', 'canvas-sync', { from });
      return;
    }

    const to: CanvasSyncState = resolution === 'local' ? 'local-only' : 'syncing';
    this.logTransition(
      from,
      to,
      `Conflict resolved with ${resolution} winning`,
      'user'
    );
  }

  /**
   * Transition from any failed/conflict state back to local-only.
   */
  public reset(): void {
    const from = this.snapshot.state;
    this.logTransition(from, 'local-only', 'Sync state reset', 'system');
    this.snapshot = {
      ...this.snapshot,
      lastSyncedAt: null,
      remoteVersion: null,
      localVersion: this.snapshot.localVersion + 1,
    };
  }

  /**
   * Clean up timeouts and listeners.
   */
  public dispose(): void {
    for (const timeout of this.stateTimeouts.values()) {
      clearTimeout(timeout);
    }
    this.stateTimeouts.clear();
    this.listeners = [];
  }
}

// ============================================================================
// Singleton registry per project
// ============================================================================

const serviceRegistry = new Map<string, CanvasSyncService>();

export function getCanvasSyncService(projectId: string): CanvasSyncService {
  if (!serviceRegistry.has(projectId)) {
    serviceRegistry.set(projectId, new CanvasSyncService(projectId));
  }
  return serviceRegistry.get(projectId)!;
}

export function clearCanvasSyncService(projectId: string): void {
  const service = serviceRegistry.get(projectId);
  if (service) {
    service.dispose();
    serviceRegistry.delete(projectId);
  }
}
