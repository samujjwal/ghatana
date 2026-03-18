/**
 * Sync Strategy Tests
 * 
 * Unit tests for SyncStrategy including push/pull,
 * conflict resolution, and offline queue.
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { SyncStrategy } from '../sync/SyncStrategy';
import { IndexedDBAdapter } from '../storage/IndexedDBAdapter';
import { CanvasAPIClient } from '../api/CanvasAPIClient';
import type { CanvasSnapshot } from '../CanvasPersistence';

describe('SyncStrategy', () => {
    let strategy: SyncStrategy;
    let localAdapter: IndexedDBAdapter;
    let apiClient: CanvasAPIClient;

    const mockSnapshot: CanvasSnapshot = {
        id: 'snap-1',
        projectId: 'proj-1',
        canvasId: 'canvas-1',
        version: 1,
        timestamp: Date.now(),
        data: {
            elements: [
                {
                    id: 'node-1',
                    type: 'component',
                    position: { x: 100, y: 100 },
                    data: { label: 'Test Node' },
                },
            ],
            connections: [],
        },
        metadata: {
            author: 'test-user',
            description: 'Test snapshot',
        },
    };

    beforeEach(() => {
        // Mock dependencies
        localAdapter = {
            init: vi.fn(),
            saveSnapshot: vi.fn(),
            loadSnapshot: vi.fn(),
            listSnapshots: vi.fn(),
            deleteSnapshot: vi.fn(),
            clear: vi.fn(),
            getQuota: vi.fn(),
        } as unknown;

        apiClient = {
            saveSnapshot: vi.fn(),
            loadSnapshot: vi.fn(),
            listSnapshots: vi.fn(),
            deleteSnapshot: vi.fn(),
            batchSave: vi.fn(),
        } as unknown;

        strategy = new SyncStrategy({
            localAdapter,
            apiClient,
            conflictStrategy: 'last-write-wins',
            autoSync: false,
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('sync', () => {
        it('should perform full sync successfully', async () => {
            // Mock pull - remote has newer snapshot
            const remoteSnapshot = { ...mockSnapshot, version: 2, timestamp: Date.now() };
            vi.mocked(apiClient.listSnapshots).mockResolvedValue({
                snapshots: [remoteSnapshot],
                total: 1,
            });

            // Mock local - has older snapshot
            vi.mocked(localAdapter.listSnapshots).mockResolvedValue([mockSnapshot]);

            const result = await strategy.sync('proj-1', 'canvas-1');

            expect(result.pulled).toBe(1);
            expect(result.pushed).toBe(0);
            expect(result.conflicts).toHaveLength(0);
            expect(localAdapter.saveSnapshot).toHaveBeenCalledWith(remoteSnapshot);
        });

        it('should push local changes to remote', async () => {
            const localSnapshot = { ...mockSnapshot, timestamp: Date.now() };

            // Mock pull - remote is empty
            vi.mocked(apiClient.listSnapshots).mockResolvedValue({
                snapshots: [],
                total: 0,
            });

            // Mock local - has new snapshot
            vi.mocked(localAdapter.listSnapshots).mockResolvedValue([localSnapshot]);

            // Mock remote save
            vi.mocked(apiClient.saveSnapshot).mockResolvedValue({ success: true });

            const result = await strategy.sync('proj-1', 'canvas-1');

            expect(result.pulled).toBe(0);
            expect(result.pushed).toBe(1);
            expect(apiClient.saveSnapshot).toHaveBeenCalledWith(localSnapshot);
        });

        it('should handle conflicts with last-write-wins', async () => {
            const now = Date.now();
            const localSnapshot = { ...mockSnapshot, version: 2, timestamp: now };
            const remoteSnapshot = { ...mockSnapshot, version: 2, timestamp: now - 1000 };

            vi.mocked(apiClient.listSnapshots).mockResolvedValue({
                snapshots: [remoteSnapshot],
                total: 1,
            });
            vi.mocked(localAdapter.listSnapshots).mockResolvedValue([localSnapshot]);
            vi.mocked(localAdapter.loadSnapshot).mockResolvedValue(localSnapshot);
            vi.mocked(apiClient.loadSnapshot).mockResolvedValue(remoteSnapshot);

            const result = await strategy.sync('proj-1', 'canvas-1');

            // Local is newer, should win
            expect(result.conflicts).toHaveLength(0);
            expect(apiClient.saveSnapshot).toHaveBeenCalledWith(localSnapshot);
        });
    });

    describe('pull', () => {
        it('should pull remote changes', async () => {
            const remoteSnapshots = [mockSnapshot];
            vi.mocked(apiClient.listSnapshots).mockResolvedValue({
                snapshots: remoteSnapshots,
                total: 1,
            });

            const pulled = await strategy['pull']('proj-1', 'canvas-1');

            expect(pulled).toBe(1);
            expect(localAdapter.saveSnapshot).toHaveBeenCalledWith(mockSnapshot);
        });

        it('should skip if remote is empty', async () => {
            vi.mocked(apiClient.listSnapshots).mockResolvedValue({
                snapshots: [],
                total: 0,
            });

            const pulled = await strategy['pull']('proj-1', 'canvas-1');

            expect(pulled).toBe(0);
            expect(localAdapter.saveSnapshot).not.toHaveBeenCalled();
        });
    });

    describe('push', () => {
        it('should push local changes', async () => {
            vi.mocked(localAdapter.listSnapshots).mockResolvedValue([mockSnapshot]);
            vi.mocked(apiClient.saveSnapshot).mockResolvedValue({ success: true });

            const pushed = await strategy['push']('proj-1', 'canvas-1');

            expect(pushed).toBe(1);
            expect(apiClient.saveSnapshot).toHaveBeenCalledWith(mockSnapshot);
        });

        it('should skip if local is empty', async () => {
            vi.mocked(localAdapter.listSnapshots).mockResolvedValue([]);

            const pushed = await strategy['push']('proj-1', 'canvas-1');

            expect(pushed).toBe(0);
            expect(apiClient.saveSnapshot).not.toHaveBeenCalled();
        });
    });

    describe('conflict resolution', () => {
        const localSnapshot: CanvasSnapshot = {
            ...mockSnapshot,
            version: 2,
            timestamp: Date.now(),
            data: {
                elements: [{ id: 'local-node', type: 'component', position: { x: 0, y: 0 }, data: {} }],
                connections: [],
            },
        };

        const remoteSnapshot: CanvasSnapshot = {
            ...mockSnapshot,
            version: 2,
            timestamp: Date.now() - 1000,
            data: {
                elements: [{ id: 'remote-node', type: 'component', position: { x: 100, y: 100 }, data: {} }],
                connections: [],
            },
        };

        it('should resolve with local-wins strategy', async () => {
            const localWinsStrategy = new SyncStrategy({
                localAdapter,
                apiClient,
                conflictStrategy: 'local-wins',
            });

            const resolved = await localWinsStrategy.resolveConflict(
                { local: localSnapshot, remote: remoteSnapshot },
                'local-wins'
            );

            expect(resolved).toEqual(localSnapshot);
        });

        it('should resolve with remote-wins strategy', async () => {
            const remoteWinsStrategy = new SyncStrategy({
                localAdapter,
                apiClient,
                conflictStrategy: 'remote-wins',
            });

            const resolved = await remoteWinsStrategy.resolveConflict(
                { local: localSnapshot, remote: remoteSnapshot },
                'remote-wins'
            );

            expect(resolved).toEqual(remoteSnapshot);
        });

        it('should resolve with last-write-wins strategy', async () => {
            const resolved = await strategy.resolveConflict(
                { local: localSnapshot, remote: remoteSnapshot },
                'last-write-wins'
            );

            // Local has newer timestamp
            expect(resolved).toEqual(localSnapshot);
        });

        it('should merge changes with merge strategy', async () => {
            const mergeStrategy = new SyncStrategy({
                localAdapter,
                apiClient,
                conflictStrategy: 'merge',
            });

            const resolved = await mergeStrategy.resolveConflict(
                { local: localSnapshot, remote: remoteSnapshot },
                'merge'
            );

            // Should contain elements from both
            expect(resolved.data.elements).toHaveLength(2);
            expect(resolved.data.elements.some(e => e.id === 'local-node')).toBe(true);
            expect(resolved.data.elements.some(e => e.id === 'remote-node')).toBe(true);
        });
    });

    describe('offline queue', () => {
        it('should queue changes when offline', () => {
            strategy.queueForSync(mockSnapshot);

            const queue = strategy['offlineQueue'];
            expect(queue).toContain(mockSnapshot);
        });

        it('should sync queue when coming online', async () => {
            // Add to queue
            strategy.queueForSync(mockSnapshot);

            // Mock going online
            vi.mocked(apiClient.saveSnapshot).mockResolvedValue({ success: true });

            // Trigger sync
            await strategy['processOfflineQueue']();

            expect(apiClient.saveSnapshot).toHaveBeenCalledWith(mockSnapshot);
            expect(strategy['offlineQueue']).toHaveLength(0);
        });

        it('should keep in queue if sync fails', async () => {
            strategy.queueForSync(mockSnapshot);

            // Mock failing to save
            vi.mocked(apiClient.saveSnapshot).mockRejectedValue(new Error('Network error'));

            await strategy['processOfflineQueue']();

            // Should still be in queue
            expect(strategy['offlineQueue']).toHaveLength(1);
        });
    });

    describe('auto-sync', () => {
        it('should auto-sync at intervals', async () => {
            vi.useFakeTimers();

            const autoSyncStrategy = new SyncStrategy({
                localAdapter,
                apiClient,
                autoSync: true,
                syncInterval: 1000,
            });

            const syncSpy = vi.spyOn(autoSyncStrategy as unknown, 'sync');

            // Fast-forward time
            vi.advanceTimersByTime(1000);

            expect(syncSpy).toHaveBeenCalled();

            vi.useRealTimers();
        });
    });

    describe('network state handling', () => {
        it('should detect online state', () => {
            Object.defineProperty(navigator, 'onLine', {
                writable: true,
                value: true,
            });

            expect(strategy['isOnline']()).toBe(true);
        });

        it('should detect offline state', () => {
            Object.defineProperty(navigator, 'onLine', {
                writable: true,
                value: false,
            });

            expect(strategy['isOnline']()).toBe(false);
        });
    });
});
