/**
 * Canvas Sync Strategy
 * 
 * Manages synchronization between local storage and remote API.
 * Implements conflict resolution and offline-first architecture.
 * 
 * @doc.type service
 * @doc.purpose Local-first sync with conflict resolution
 * @doc.layer product
 * @doc.pattern Strategy Pattern + Event Sourcing
 */

import { IndexedDBAdapter } from '../storage/IndexedDBAdapter';
import { CanvasAPIClient } from '../api/CanvasAPIClient';
import type { CanvasSnapshot } from '../CanvasPersistence';

export interface SyncOptions {
    immediate?: boolean;
    direction?: 'push' | 'pull' | 'both';
    conflictResolution?: 'local-wins' | 'remote-wins' | 'last-write-wins' | 'manual';
}

export interface SyncResult {
    pushed: number;
    pulled: number;
    conflicts: number;
    errors: number;
}

export interface Conflict {
    snapshotId: string;
    local: CanvasSnapshot;
    remote: CanvasSnapshot;
    resolution?: CanvasSnapshot;
}

export interface SyncItem {
    id: string;
    type: string;
    data: unknown;
    timestamp: number;
    version?: number;
    hash: string;
}

export type SyncStatus = 'idle' | 'syncing' | 'online' | 'offline' | 'error';

export class SyncStrategy {
    private localAdapter: IndexedDBAdapter;
    private apiClient: CanvasAPIClient;
    private syncQueue: CanvasSnapshot[] = [];
    private syncStatus: SyncStatus = 'idle';
    private syncInterval: NodeJS.Timeout | null = null;
    private onlineListenerAdded = false;

    constructor(localAdapter: IndexedDBAdapter, apiClient: CanvasAPIClient) {
        this.localAdapter = localAdapter;
        this.apiClient = apiClient;
        this.setupOnlineListener();
    }

    /**
     * Start automatic sync on interval
     */
    public startAutoSync(intervalMs: number = 60000): void {
        this.stopAutoSync();

        this.syncInterval = setInterval(async () => {
            if (this.isOnline()) {
                await this.sync();
            }
        }, intervalMs);

        // Sync immediately
        if (this.isOnline()) {
            this.sync().catch(console.error);
        }
    }

    /**
     * Stop automatic sync
     */
    public stopAutoSync(): void {
        if (this.syncInterval) {
            clearInterval(this.syncInterval);
            this.syncInterval = null;
        }
    }

    /**
     * Perform full sync
     */
    public async sync(
        projectId: string,
        canvasId: string,
        options: SyncOptions = {}
    ): Promise<SyncResult> {
        const {
            direction = 'both',
            conflictResolution = 'last-write-wins',
        } = options;

        this.syncStatus = 'syncing';

        const result: SyncResult = {
            pushed: 0,
            pulled: 0,
            conflicts: 0,
            errors: 0,
        };

        try {
            // Check online status
            if (!this.isOnline()) {
                this.syncStatus = 'offline';
                throw new Error('Cannot sync while offline');
            }

            // Pull from remote (if needed)
            if (direction === 'pull' || direction === 'both') {
                const pullResult = await this.pull(projectId, canvasId, conflictResolution);
                result.pulled = pullResult.pulled;
                result.conflicts += pullResult.conflicts;
            }

            // Push to remote (if needed)
            if (direction === 'push' || direction === 'both') {
                const pushResult = await this.push(projectId, canvasId);
                result.pushed = pushResult.pushed;
            }

            this.syncStatus = 'online';
            return result;
        } catch (error) {
            console.error('Sync failed:', error);
            this.syncStatus = 'error';
            result.errors++;
            throw error;
        } finally {
            if (this.syncStatus === 'syncing') {
                this.syncStatus = 'idle';
            }
        }
    }

    /**
     * Pull snapshots from remote
     */
    private async pull(
        projectId: string,
        canvasId: string,
        conflictResolution: SyncOptions['conflictResolution']
    ): Promise<{ pulled: number; conflicts: number }> {
        const result = { pulled: 0, conflicts: 0 };

        // Fetch remote snapshots
        const remoteSnapshots = await this.apiClient.listSnapshots(projectId, canvasId);

        // Get local snapshots
        const localSnapshots = await this.localAdapter.listSnapshots(projectId, canvasId);
        const localMap = new Map(localSnapshots.map(s => [s.id, s]));

        // Process each remote snapshot
        for (const remoteSnapshot of remoteSnapshots) {
            const localSnapshot = localMap.get(remoteSnapshot.id);

            if (!localSnapshot) {
                // New snapshot from remote, save locally
                await this.localAdapter.saveSnapshot(remoteSnapshot);
                result.pulled++;
            } else {
                // Snapshot exists locally, check for conflict
                if (this.hasConflict(localSnapshot, remoteSnapshot)) {
                    result.conflicts++;

                    // Resolve conflict
                    const resolved = await this.resolveConflict(
                        localSnapshot,
                        remoteSnapshot,
                        conflictResolution!
                    );

                    if (resolved) {
                        await this.localAdapter.saveSnapshot(resolved);
                        result.pulled++;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Push local snapshots to remote
     */
    private async push(
        projectId: string,
        canvasId: string
    ): Promise<{ pushed: number }> {
        const result = { pushed: 0 };

        // Get local snapshots
        const localSnapshots = await this.localAdapter.listSnapshots(projectId, canvasId);

        // Push each snapshot that doesn't exist remotely
        for (const snapshot of localSnapshots) {
            const existsRemotely = await this.apiClient.exists(snapshot.id);

            if (!existsRemotely) {
                await this.apiClient.saveSnapshot(snapshot);
                result.pushed++;
            }
        }

        // Also push any queued snapshots
        while (this.syncQueue.length > 0) {
            const snapshot = this.syncQueue.shift()!;
            await this.apiClient.saveSnapshot(snapshot);
            result.pushed++;
        }

        return result;
    }

    /**
     * Queue snapshot for sync (when offline)
     */
    public queueForSync(snapshot: CanvasSnapshot): void {
        this.syncQueue.push(snapshot);
    }

    /**
     * Check if there's a conflict between local and remote
     */
    private hasConflict(local: CanvasSnapshot, remote: CanvasSnapshot): boolean {
        // Conflict if timestamps differ
        if (local.timestamp !== remote.timestamp) {
            return true;
        }

        // Conflict if checksums differ
        if (local.checksum !== remote.checksum) {
            return true;
        }

        return false;
    }

    /**
     * Resolve conflict between local and remote snapshots
     */
    private async resolveConflict(
        local: CanvasSnapshot,
        remote: CanvasSnapshot,
        strategy: SyncOptions['conflictResolution']
    ): Promise<CanvasSnapshot | null> {
        switch (strategy) {
            case 'local-wins':
                return local;

            case 'remote-wins':
                return remote;

            case 'last-write-wins':
                return local.timestamp > remote.timestamp ? local : remote;

            case 'manual':
                // Emit event for manual resolution
                return this.resolveConflictManually(local, remote);

            default:
                return local.timestamp > remote.timestamp ? local : remote;
        }
    }

    /**
     * Resolve conflict manually with user interaction
     */
    private async resolveConflictManually(local: CanvasSnapshot, remote: CanvasSnapshot): Promise<CanvasSnapshot> {
        // Create conflict resolution event
        const conflictEvent = new CustomEvent('sync-conflict', {
            detail: {
                type: 'canvas-conflict',
                local,
                remote,
                resolve: (choice: 'local' | 'remote' | 'merge') => {
                    switch (choice) {
                        case 'local':
                            return local;
                        case 'remote':
                            return remote;
                        case 'merge':
                            return this.mergeSnapshots(local, remote);
                        default:
                            return local.timestamp > remote.timestamp ? local : remote;
                    }
                }
            }
        });

        // Emit event for UI to handle
        document.dispatchEvent(conflictEvent);

        // For now, return last-write-wins as fallback
        // In a real implementation, this would wait for user response
        console.warn('Manual conflict resolution event emitted - using last-write-wins as fallback');
        return local.timestamp > remote.timestamp ? local : remote;
    }

    /**
     * Merge two conflicting snapshots
     */
    private mergeSnapshots(local: CanvasSnapshot, remote: CanvasSnapshot): CanvasSnapshot {
        const merged: CanvasSnapshot = {
            id: `merged-${Date.now()}`,
            projectId: local.projectId,
            canvasId: local.canvasId,
            version: Math.max(local.version, remote.version) + 1,
            timestamp: Math.max(local.timestamp, remote.timestamp),
            checksum: this.generateChecksum(local, remote),
            data: this.mergeCanvasData(local.data, remote.data)
        };

        return merged;
    }

    /**
     * Merge canvas data from two snapshots
     */
    private mergeCanvasData(localData: unknown, remoteData: unknown): Record<string, unknown> {
        if (!localData && !remoteData) return {};
        if (!localData) return remoteData as Record<string, unknown>;
        if (!remoteData) return localData as Record<string, unknown>;

        // Simple merge strategy - combine properties
        const local = localData as Record<string, unknown>;
        const remote = remoteData as Record<string, unknown>;
        const merged = { ...local };

        // Merge remote properties that don't exist locally
        Object.keys(remote).forEach(key => {
            if (!(key in merged)) {
                merged[key] = remote[key];
            } else if (typeof merged[key] === 'object' && typeof remote[key] === 'object') {
                // Recursively merge objects
                merged[key] = this.mergeCanvasData(merged[key], remote[key]);
            }
        });

        return merged;
    }

    /**
     * Generate checksum for merged snapshot
     */
    private generateChecksum(local: CanvasSnapshot, remote: CanvasSnapshot): string {
        const combined = JSON.stringify({ local, remote });
        return btoa(combined).slice(0, 16);
    }

    /**
     * Check online status
     */
    private isOnline(): boolean {
        return typeof navigator !== 'undefined' ? navigator.onLine : true;
    }

    /**
     * Setup online/offline event listeners
     */
    private setupOnlineListener(): void {
        if (this.onlineListenerAdded || typeof window === 'undefined') return;

        window.addEventListener('online', () => {
            console.log('Back online - syncing...');
            this.syncStatus = 'online';
            this.sync('', '').catch(console.error); // Will use last project/canvas
        });

        window.addEventListener('offline', () => {
            console.log('Gone offline');
            this.syncStatus = 'offline';
        });

        this.onlineListenerAdded = true;
    }

    /**
     * Get current sync status
     */
    public getStatus(): SyncStatus {
        return this.syncStatus;
    }

    /**
     * Get queued items count
     */
    public getQueuedCount(): number {
        return this.syncQueue.length;
    }

    /**
     * Clear sync queue
     */
    public clearQueue(): void {
        this.syncQueue = [];
    }

    /**
     * Cleanup
     */
    public destroy(): void {
        this.stopAutoSync();
    }
}
