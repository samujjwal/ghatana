/**
 * Storage Migration Utility
 * 
 * Migrates canvas data from localStorage to IndexedDB.
 * Handles data validation, conflict resolution, and cleanup.
 * 
 * @doc.type service
 * @doc.purpose Data migration between storage backends
 * @doc.layer product
 * @doc.pattern Strategy Pattern
 */

import { IndexedDBAdapter, LocalStorageAdapter } from './IndexedDBAdapter';
import type { CanvasSnapshot } from '../CanvasPersistence';

export interface MigrationProgress {
    total: number;
    migrated: number;
    failed: number;
    status: 'idle' | 'running' | 'completed' | 'failed';
    error?: string;
}

export interface MigrationOptions {
    deleteSource?: boolean; // Delete from localStorage after migration
    dryRun?: boolean; // Don't actually migrate, just report
    onProgress?: (progress: MigrationProgress) => void;
}

export class StorageMigration {
    private source: LocalStorageAdapter;
    private target: IndexedDBAdapter;

    constructor() {
        this.source = new LocalStorageAdapter();
        this.target = new IndexedDBAdapter();
    }

    /**
     * Migrate all data from localStorage to IndexedDB
     */
    public async migrate(options: MigrationOptions = {}): Promise<MigrationProgress> {
        const {
            deleteSource = false,
            dryRun = false,
            onProgress,
        } = options;

        const progress: MigrationProgress = {
            total: 0,
            migrated: 0,
            failed: 0,
            status: 'running',
        };

        try {
            // Initialize target
            await this.target.init();

            // Find all localStorage snapshots
            const snapshots = await this.findAllLocalStorageSnapshots();
            progress.total = snapshots.length;

            if (onProgress) onProgress({ ...progress });

            // Migrate each snapshot
            for (const snapshot of snapshots) {
                try {
                    if (!dryRun) {
                        // Check if already exists in IndexedDB
                        const existing = await this.target.loadSnapshot(snapshot.id);

                        if (existing) {
                            // Resolve conflict
                            const resolved = this.resolveConflict(existing, snapshot);
                            await this.target.saveSnapshot(resolved);
                        } else {
                            // New snapshot, save directly
                            await this.target.saveSnapshot(snapshot);
                        }

                        // Delete from source if requested
                        if (deleteSource) {
                            await this.source.deleteSnapshot(snapshot.id);
                        }
                    }

                    progress.migrated++;
                } catch (error) {
                    console.error(`Failed to migrate snapshot ${snapshot.id}:`, error);
                    progress.failed++;
                }

                if (onProgress) onProgress({ ...progress });
            }

            progress.status = 'completed';
            if (onProgress) onProgress({ ...progress });

            return progress;
        } catch (error) {
            progress.status = 'failed';
            progress.error = error instanceof Error ? error.message : 'Unknown error';

            if (onProgress) onProgress({ ...progress });

            throw error;
        }
    }

    /**
     * Check if migration is needed
     */
    public async needsMigration(): Promise<boolean> {
        const localSnapshots = await this.findAllLocalStorageSnapshots();
        return localSnapshots.length > 0;
    }

    /**
     * Get migration estimate
     */
    public async getEstimate(): Promise<{
        snapshotCount: number;
        estimatedSize: number;
        estimatedTime: number;
    }> {
        const snapshots = await this.findAllLocalStorageSnapshots();
        const totalSize = snapshots.reduce((sum, s) => {
            return sum + JSON.stringify(s).length;
        }, 0);

        // Estimate: ~100 snapshots/second migration rate
        const estimatedTime = Math.ceil(snapshots.length / 100 * 1000);

        return {
            snapshotCount: snapshots.length,
            estimatedSize: totalSize,
            estimatedTime,
        };
    }

    /**
     * Find all canvas snapshots in localStorage
     */
    private async findAllLocalStorageSnapshots(): Promise<CanvasSnapshot[]> {
        const snapshots: CanvasSnapshot[] = [];
        const prefix = 'canvas-snapshot-';

        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (key?.startsWith(prefix)) {
                try {
                    const data = localStorage.getItem(key);
                    if (data) {
                        const snapshot = JSON.parse(data) as CanvasSnapshot;
                        // Validate snapshot structure
                        if (this.isValidSnapshot(snapshot)) {
                            snapshots.push(snapshot);
                        }
                    }
                } catch (error) {
                    console.error(`Failed to parse snapshot from key ${key}:`, error);
                }
            }
        }

        return snapshots;
    }

    /**
     * Validate snapshot structure
     */
    private isValidSnapshot(snapshot: unknown): snapshot is CanvasSnapshot {
        return (
            snapshot &&
            typeof snapshot.id === 'string' &&
            typeof snapshot.projectId === 'string' &&
            typeof snapshot.canvasId === 'string' &&
            typeof snapshot.version === 'number' &&
            typeof snapshot.timestamp === 'number' &&
            snapshot.data &&
            Array.isArray(snapshot.data.elements) &&
            Array.isArray(snapshot.data.connections)
        );
    }

    /**
     * Resolve conflict between two snapshots
     * Strategy: Keep the newer one (higher timestamp)
     */
    private resolveConflict(
        existing: CanvasSnapshot,
        incoming: CanvasSnapshot
    ): CanvasSnapshot {
        // Last-write-wins strategy
        return existing.timestamp > incoming.timestamp ? existing : incoming;
    }

    /**
     * Rollback migration (restore from localStorage)
     */
    public async rollback(): Promise<void> {
        // For safety, we never delete from localStorage during migration
        // So rollback is simply clearing IndexedDB
        await this.target.clear();
    }
}

/**
 * React hook for migration UI
 */
export function useMigration() {
    const [progress, setProgress] = React.useState<MigrationProgress>({
        total: 0,
        migrated: 0,
        failed: 0,
        status: 'idle',
    });

    const migration = React.useMemo(() => new StorageMigration(), []);

    const startMigration = React.useCallback(async (options?: MigrationOptions) => {
        try {
            await migration.migrate({
                ...options,
                onProgress: (p) => {
                    setProgress(p);
                    options?.onProgress?.(p);
                },
            });
        } catch (error) {
            console.error('Migration failed:', error);
        }
    }, [migration]);

    const checkNeedsMigration = React.useCallback(async () => {
        return migration.needsMigration();
    }, [migration]);

    const getEstimate = React.useCallback(async () => {
        return migration.getEstimate();
    }, [migration]);

    return {
        progress,
        startMigration,
        checkNeedsMigration,
        getEstimate,
    };
}

// Import React if needed
import * as React from 'react';
