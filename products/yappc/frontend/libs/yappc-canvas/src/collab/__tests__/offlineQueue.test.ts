/**
 * Tests for OfflineQueueManager
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

import {
  createOfflineQueue,
  type EditOperation,
  type EditConflict,

  OfflineQueueManager} from '../offlineQueue';

describe('OfflineQueueManager', () => {
  let manager: OfflineQueueManager;

  beforeEach(() => {
    vi.useFakeTimers();
    manager = createOfflineQueue({
      autoSync: false, // Disable auto-sync for most tests
    });
  });

  afterEach(() => {
    manager.destroy();
    vi.restoreAllMocks();
  });

  describe('Initialization', () => {
    it('should initialize with default configuration', () => {
      const config = manager.getConfig();
      expect(config.maxQueueSize).toBe(1000);
      expect(config.maxRetryAttempts).toBe(5);
      expect(config.initialRetryDelay).toBe(1000);
      expect(config.maxRetryDelay).toBe(60000);
    });

    it('should initialize with custom configuration', () => {
      const custom = createOfflineQueue({
        maxQueueSize: 500,
        maxRetryAttempts: 3,
      });

      const config = custom.getConfig();
      expect(config.maxQueueSize).toBe(500);
      expect(config.maxRetryAttempts).toBe(3);
    });

    it('should start with idle sync status', () => {
      expect(manager.getSyncStatus()).toBe('idle');
    });

    it('should start with empty queue', () => {
      expect(manager.getQueue()).toEqual([]);
    });
  });

  describe('Queue Management', () => {
    it('should queue an edit operation', async () => {
      const operation: EditOperation = {
        id: 'op-1',
        type: 'insert',
        elementId: 'el-1',
        data: { content: 'test' },
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      await manager.queueEdit(operation);

      const queue = manager.getQueue();
      expect(queue).toHaveLength(1);
      expect(queue[0].operation).toEqual(operation);
    });

    it('should track queue statistics', async () => {
      const operation: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      await manager.queueEdit(operation);

      const stats = manager.getStatistics();
      expect(stats.totalQueued).toBe(1);
      expect(stats.currentQueueSize).toBe(1);
    });

    it('should reject edits when queue is full', async () => {
      manager.updateConfig({ maxQueueSize: 2 });

      const op1: EditOperation = {
        id: 'op-1',
        type: 'insert',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      const op2: EditOperation = {
        id: 'op-2',
        type: 'insert',
        elementId: 'el-2',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      const op3: EditOperation = {
        id: 'op-3',
        type: 'insert',
        elementId: 'el-3',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      await manager.queueEdit(op1);
      await manager.queueEdit(op2);

      await expect(manager.queueEdit(op3)).rejects.toThrow('Queue size limit reached');
    });

    it('should clear queue', async () => {
      const operation: EditOperation = {
        id: 'op-1',
        type: 'delete',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      await manager.queueEdit(operation);
      manager.clearQueue();

      expect(manager.getQueue()).toEqual([]);
      expect(manager.getStatistics().currentQueueSize).toBe(0);
    });
  });

  describe('Sync Operations', () => {
    it('should sync successfully', async () => {
      const operation: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      const syncFn = vi.fn().mockResolvedValue(undefined);
      manager.updateConfig({ syncOperation: syncFn });

      await manager.queueEdit(operation);
      await manager.syncQueue();

      expect(syncFn).toHaveBeenCalledWith(operation);
      expect(manager.getQueue()).toHaveLength(0);
      expect(manager.getStatistics().successfullySynced).toBe(1);
    });

    it('should handle sync failures', async () => {
      const operation: EditOperation = {
        id: 'op-1',
        type: 'insert',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      const syncFn = vi.fn().mockRejectedValue(new Error('Network error'));
      manager.updateConfig({ syncOperation: syncFn });

      await manager.queueEdit(operation);
      await manager.syncQueue();

      const queue = manager.getQueue();
      expect(queue).toHaveLength(1);
      expect(queue[0].retryAttempts).toBe(1);
      expect(queue[0].lastError).toBe('Network error');
      expect(manager.getStatistics().failedSyncs).toBe(1);
    });

    it('should retry with exponential backoff', async () => {
      const operation: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      const syncFn = vi.fn().mockRejectedValue(new Error('Network error'));
      manager.updateConfig({ 
        syncOperation: syncFn,
        initialRetryDelay: 1000,
      });

      await manager.queueEdit(operation);

      // First attempt
      await manager.syncQueue();
      let queue = manager.getQueue();
      expect(queue[0].retryAttempts).toBe(1);

      // Advance time and retry
      await vi.advanceTimersByTimeAsync(1000);
      queue = manager.getQueue();
      expect(queue[0].retryAttempts).toBe(2);
    });

    it('should remove edit after max retries', async () => {
      const operation: EditOperation = {
        id: 'op-1',
        type: 'delete',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      const syncFn = vi.fn().mockRejectedValue(new Error('Network error'));
      manager.updateConfig({ 
        syncOperation: syncFn,
        maxRetryAttempts: 3,
      });

      await manager.queueEdit(operation);

      // Try multiple times
      for (let i = 0; i < 3; i++) {
        await manager.syncQueue();
        await vi.advanceTimersByTimeAsync(10000);
      }

      expect(manager.getQueue()).toHaveLength(0);
    });

    it('should skip edits not ready for retry', async () => {
      const operation: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      const syncFn = vi.fn().mockRejectedValue(new Error('Network error'));
      manager.updateConfig({ 
        syncOperation: syncFn,
        initialRetryDelay: 5000,
      });

      await manager.queueEdit(operation);
      await manager.syncQueue(); // First attempt, will schedule retry

      // Try to sync again before retry time
      await manager.syncQueue();

      // Should only be called once since retry time hasn't passed
      expect(syncFn).toHaveBeenCalledTimes(1);
    });
  });

  describe('Conflict Detection', () => {
    it('should detect concurrent edit conflicts', () => {
      const local: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: { text: 'local' },
        timestamp: 1000,
        userId: 'user-1',
        version: 1,
      };

      const remote: EditOperation = {
        id: 'op-2',
        type: 'update',
        elementId: 'el-1',
        data: { text: 'remote' },
        timestamp: 1100, // Within 1 second
        userId: 'user-2',
        version: 1,
      };

      const conflict = manager.detectConflict(local, remote);

      expect(conflict).not.toBeNull();
      expect(conflict!.type).toBe('concurrent');
      expect(conflict!.local).toEqual(local);
      expect(conflict!.remote).toEqual(remote);
    });

    it('should detect version mismatch conflicts', () => {
      const local: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: 1000,
        userId: 'user-1',
        version: 1,
      };

      const remote: EditOperation = {
        id: 'op-2',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: 5000,
        userId: 'user-2',
        version: 2, // Different version
      };

      const conflict = manager.detectConflict(local, remote);

      expect(conflict).not.toBeNull();
      expect(conflict!.type).toBe('version-mismatch');
    });

    it('should not detect conflict for different elements', () => {
      const local: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: 1000,
        userId: 'user-1',
        version: 1,
      };

      const remote: EditOperation = {
        id: 'op-2',
        type: 'update',
        elementId: 'el-2', // Different element
        data: {},
        timestamp: 1100,
        userId: 'user-2',
        version: 1,
      };

      const conflict = manager.detectConflict(local, remote);

      expect(conflict).toBeNull();
    });

    it('should track conflict statistics', () => {
      const local: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: 1000,
        userId: 'user-1',
        version: 1,
      };

      const remote: EditOperation = {
        id: 'op-2',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: 1100,
        userId: 'user-2',
        version: 1,
      };

      manager.detectConflict(local, remote);

      const stats = manager.getStatistics();
      expect(stats.conflictsDetected).toBe(1);
      expect(stats.pendingConflicts).toBe(1);
    });
  });

  describe('Conflict Resolution', () => {
    it('should resolve conflict with local strategy', async () => {
      const local: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: { text: 'local' },
        timestamp: 1000,
        userId: 'user-1',
        version: 1,
      };

      const remote: EditOperation = {
        id: 'op-2',
        type: 'update',
        elementId: 'el-1',
        data: { text: 'remote' },
        timestamp: 1100,
        userId: 'user-2',
        version: 1,
      };

      const conflict = manager.detectConflict(local, remote);
      await manager.resolveConflict(conflict!.id, 'local', 'user-1');

      const conflicts = manager.getConflicts();
      expect(conflicts).toHaveLength(0);

      const stats = manager.getStatistics();
      expect(stats.conflictsResolved).toBe(1);
      expect(stats.pendingConflicts).toBe(0);
    });

    it('should resolve conflict with remote strategy', async () => {
      const local: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: { text: 'local' },
        timestamp: 1000,
        userId: 'user-1',
        version: 1,
      };

      const remote: EditOperation = {
        id: 'op-2',
        type: 'update',
        elementId: 'el-1',
        data: { text: 'remote' },
        timestamp: 1100,
        userId: 'user-2',
        version: 1,
      };

      await manager.queueEdit(local);
      const conflict = manager.detectConflict(local, remote);
      await manager.resolveConflict(conflict!.id, 'remote', 'user-1');

      // Local operation should be removed from queue
      expect(manager.getQueue()).toHaveLength(0);
    });

    it('should resolve conflict with merge strategy', async () => {
      const local: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: { text: 'local', prop1: 'a' },
        timestamp: 1000,
        userId: 'user-1',
        version: 1,
      };

      const remote: EditOperation = {
        id: 'op-2',
        type: 'update',
        elementId: 'el-1',
        data: { text: 'remote', prop2: 'b' },
        timestamp: 1100,
        userId: 'user-2',
        version: 1,
      };

      const conflict = manager.detectConflict(local, remote);
      await manager.resolveConflict(conflict!.id, 'merge', 'user-1');

      // Merged operation should be queued
      const queue = manager.getQueue();
      expect(queue).toHaveLength(1);
      expect(queue[0].operation.data).toEqual({
        text: 'local',
        prop1: 'a',
        prop2: 'b',
      });
    });

    it('should handle manual resolution', async () => {
      const local: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: 1000,
        userId: 'user-1',
        version: 1,
      };

      const remote: EditOperation = {
        id: 'op-2',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: 1100,
        userId: 'user-2',
        version: 1,
      };

      const conflict = manager.detectConflict(local, remote);
      const result = await manager.resolveConflict(conflict!.id, 'manual', 'user-1');

      expect(result).toBe(true);
      expect(manager.getConflicts()).toHaveLength(0);
    });

    it('should return false for non-existent conflict', async () => {
      const result = await manager.resolveConflict('non-existent', 'local', 'user-1');
      expect(result).toBe(false);
    });
  });

  describe('Configuration', () => {
    it('should get configuration', () => {
      const config = manager.getConfig();
      expect(config).toBeDefined();
      expect(config.maxQueueSize).toBeDefined();
    });

    it('should update configuration', () => {
      manager.updateConfig({
        maxQueueSize: 500,
        maxRetryAttempts: 3,
      });

      const config = manager.getConfig();
      expect(config.maxQueueSize).toBe(500);
      expect(config.maxRetryAttempts).toBe(3);
    });

    it('should merge configuration updates', () => {
      const originalMax = manager.getConfig().maxQueueSize;

      manager.updateConfig({
        maxRetryAttempts: 10,
      });

      const config = manager.getConfig();
      expect(config.maxQueueSize).toBe(originalMax);
      expect(config.maxRetryAttempts).toBe(10);
    });
  });

  describe('Auto-Sync', () => {
    it('should auto-sync when enabled', async () => {
      const syncFn = vi.fn().mockResolvedValue(undefined);
      const autoManager = createOfflineQueue({
        autoSync: true,
        syncOperation: syncFn,
      });

      const operation: EditOperation = {
        id: 'op-1',
        type: 'insert',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      await autoManager.queueEdit(operation);

      expect(syncFn).toHaveBeenCalled();
      autoManager.destroy();
    });

    it('should not auto-sync when disabled', async () => {
      const syncFn = vi.fn().mockResolvedValue(undefined);
      const manualManager = createOfflineQueue({
        autoSync: false,
        syncOperation: syncFn,
      });

      const operation: EditOperation = {
        id: 'op-1',
        type: 'insert',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      await manualManager.queueEdit(operation);

      expect(syncFn).not.toHaveBeenCalled();
      manualManager.destroy();
    });
  });

  describe('Edge Cases', () => {
    it('should handle sync when queue is empty', async () => {
      await manager.syncQueue();
      expect(manager.getSyncStatus()).toBe('idle');
    });

    it('should not sync if already syncing', async () => {
      let resolveSync: (() => void) | undefined;
      const syncFn = vi.fn().mockImplementation(() => {
        return new Promise<void>((resolve) => {
          resolveSync = resolve;
        });
      });

      manager.updateConfig({ syncOperation: syncFn });

      const operation: EditOperation = {
        id: 'op-1',
        type: 'insert',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      await manager.queueEdit(operation);

      // Start sync (won't complete immediately)
      const sync1 = manager.syncQueue();
      // Try to sync again while first is in progress
      await manager.syncQueue();

      // Resolve the first sync
      resolveSync?.();
      await sync1;

      // Should only be called once
      expect(syncFn).toHaveBeenCalledTimes(1);
    });

    it('should clear conflicts', () => {
      const local: EditOperation = {
        id: 'op-1',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: 1000,
        userId: 'user-1',
        version: 1,
      };

      const remote: EditOperation = {
        id: 'op-2',
        type: 'update',
        elementId: 'el-1',
        data: {},
        timestamp: 1100,
        userId: 'user-2',
        version: 1,
      };

      manager.detectConflict(local, remote);
      manager.clearConflicts();

      expect(manager.getConflicts()).toEqual([]);
    });

    it('should clean up timers on destroy', () => {
      const operation: EditOperation = {
        id: 'op-1',
        type: 'insert',
        elementId: 'el-1',
        data: {},
        timestamp: Date.now(),
        userId: 'user-1',
        version: 1,
      };

      manager.queueEdit(operation);
      manager.destroy();

      // Should not throw
      expect(() => manager.destroy()).not.toThrow();
    });
  });
});
