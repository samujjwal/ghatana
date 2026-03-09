/**
 * Offline Queue Tests
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';

import { OfflineQueue } from '../offlineQueue';

import type { CanvasChange } from '../types';

describe('OfflineQueue', () => {
  let queue: OfflineQueue;

  beforeEach(() => {
    // Mock localStorage
    const storage: Record<string, string> = {};
    global.localStorage = {
      getItem: (key: string) => storage[key] || null,
      setItem: (key: string, value: string) => {
        storage[key] = value;
      },
      removeItem: (key: string) => {
        delete storage[key];
      },
      clear: () => Object.keys(storage).forEach(key => delete storage[key]),
      key: (index: number) => Object.keys(storage)[index] || null,
      length: Object.keys(storage).length,
    };

    queue = new OfflineQueue({
      maxQueueSize: 100,
      conflictStrategy: 'last-write-wins',
      enablePersistence: true,
      storageKey: 'test-queue',
      retry: {
        maxRetries: 3,
        backoffMultiplier: 2,
        initialDelay: 100,
      },
    });
  });

  describe('Enqueue Operations', () => {
    it('should enqueue operation', () => {
      queue.enqueue({
        documentId: 'doc-123',
        operation: 'update',
        data: { foo: 'bar' },
        maxRetries: 3,
      });

      expect(queue.size()).toBe(1);
      const operations = queue.getAll();
      expect(operations[0].status).toBe('pending');
    });

    it('should throw error when queue full', () => {
      const smallQueue = new OfflineQueue({
        maxQueueSize: 2,
        conflictStrategy: 'last-write-wins',
        enablePersistence: false,
      });

      smallQueue.enqueue({ documentId: 'doc-1', operation: 'update', data: {}, maxRetries: 3 });
      smallQueue.enqueue({ documentId: 'doc-2', operation: 'update', data: {}, maxRetries: 3 });

      expect(() => {
        smallQueue.enqueue({ documentId: 'doc-3', operation: 'update', data: {}, maxRetries: 3 });
      }).toThrow('Queue full');
    });

    it('should generate unique IDs', () => {
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: {}, maxRetries: 3 });
      queue.enqueue({ documentId: 'doc-2', operation: 'update', data: {}, maxRetries: 3 });

      const operations = queue.getAll();
      expect(operations[0].id).not.toBe(operations[1].id);
    });
  });

  describe('Queue Management', () => {
    beforeEach(() => {
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { a: 1 }, maxRetries: 3 });
      queue.enqueue({ documentId: 'doc-2', operation: 'update', data: { b: 2 }, maxRetries: 3 });
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { c: 3 }, maxRetries: 3 });
    });

    it('should get all operations', () => {
      const all = queue.getAll();
      expect(all).toHaveLength(3);
    });

    it('should filter by document', () => {
      const doc1Ops = queue.getByDocument('doc-1');
      expect(doc1Ops).toHaveLength(2);
      expect(doc1Ops.every(op => op.documentId === 'doc-1')).toBe(true);
    });

    it('should clear specific document queue', () => {
      queue.clear('doc-1');
      expect(queue.size()).toBe(1);
      expect(queue.getByDocument('doc-1')).toHaveLength(0);
      expect(queue.getByDocument('doc-2')).toHaveLength(1);
    });

    it('should clear entire queue', () => {
      queue.clear();
      expect(queue.size()).toBe(0);
    });
  });

  describe('Replay Operations', () => {
    it('should replay queued operations successfully', async () => {
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { a: 1 }, maxRetries: 3 });
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { b: 2 }, maxRetries: 3 });

      const mockPush = vi.fn().mockResolvedValue({ success: true });
      const results = await queue.replay(mockPush);

      expect(results).toHaveLength(2);
      expect(results.every(r => r.success)).toBe(true);
      expect(queue.size()).toBe(0); // Queue cleared after successful replay
    });

    it('should handle replay failures', async () => {
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { a: 1 }, maxRetries: 3 });

      const mockPush = vi.fn().mockResolvedValue({ 
        success: false, 
        error: { message: 'Server error' } 
      });
      
      const results = await queue.replay(mockPush);

      expect(results).toHaveLength(1);
      expect(results[0].success).toBe(false);
      expect(queue.getAll()[0].retryCount).toBe(1);
    });

    it('should remove operations after max retries', async () => {
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { a: 1 }, maxRetries: 2 });

      const mockPush = vi.fn().mockResolvedValue({ success: false, error: {} });
      
      // Retry 3 times (initial + 2 retries)
      await queue.replay(mockPush);
      await queue.replay(mockPush);
      await queue.replay(mockPush);

      expect(queue.size()).toBe(0);
    });

    it('should batch operations by document', async () => {
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { a: 1 }, maxRetries: 3 });
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { b: 2 }, maxRetries: 3 });
      queue.enqueue({ documentId: 'doc-2', operation: 'update', data: { c: 3 }, maxRetries: 3 });

      const mockPush = vi.fn().mockResolvedValue({ success: true });
      await queue.replay(mockPush);

      expect(mockPush).toHaveBeenCalledTimes(2); // 2 documents
    });
  });

  describe('Conflict Detection', () => {
    it('should detect conflicts with server changes', () => {
      const queuedTime = Date.now();
      queue.enqueue({ 
        documentId: 'doc-1', 
        operation: 'update', 
        data: { a: 1 }, 
        maxRetries: 3 
      });

      const serverChanges: CanvasChange[] = [
        {
          id: 'server-1',
          documentId: 'doc-1',
          operation: 'update',
          timestamp: queuedTime + 1000, // Server change is newer
          userId: 'user-2',
          data: { a: 2 },
          version: 2,
        },
      ];

      const conflicts = queue.detectConflicts(serverChanges);
      expect(conflicts).toHaveLength(1);
    });

    it('should not detect conflicts for different documents', () => {
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { a: 1 }, maxRetries: 3 });

      const serverChanges: CanvasChange[] = [
        {
          id: 'server-1',
          documentId: 'doc-2',
          operation: 'update',
          timestamp: Date.now(),
          userId: 'user-2',
          data: {},
          version: 1,
        },
      ];

      const conflicts = queue.detectConflicts(serverChanges);
      expect(conflicts).toHaveLength(0);
    });
  });

  describe('Conflict Resolution', () => {
    it('should resolve with server-wins strategy', () => {
      const queuedOp = queue.getAll()[0] || {
        id: 'q1',
        documentId: 'doc-1',
        operation: 'update' as const,
        data: { local: true },
        timestamp: 1000,
        retryCount: 0,
        maxRetries: 3,
        status: 'pending' as const,
      };

      const serverChange: CanvasChange = {
        id: 's1',
        documentId: 'doc-1',
        operation: 'update',
        timestamp: 2000,
        userId: 'user-2',
        data: { server: true },
        version: 2,
      };

      const resolved = queue.resolveConflict(queuedOp, serverChange, 'server-wins');
      expect(resolved).toEqual(serverChange);
    });

    it('should resolve with last-write-wins strategy', () => {
      const queuedOp = {
        id: 'q1',
        documentId: 'doc-1',
        operation: 'update' as const,
        data: { local: true },
        timestamp: 3000, // Newer
        retryCount: 0,
        maxRetries: 3,
        status: 'pending' as const,
      };

      const serverChange: CanvasChange = {
        id: 's1',
        documentId: 'doc-1',
        operation: 'update',
        timestamp: 2000,
        userId: 'user-2',
        data: { server: true },
        version: 2,
      };

      const resolved = queue.resolveConflict(queuedOp, serverChange, 'last-write-wins');
      expect(resolved.timestamp).toBe(3000);
    });

    it('should merge data with merge strategy', () => {
      const queuedOp = {
        id: 'q1',
        documentId: 'doc-1',
        operation: 'update' as const,
        data: { local: true, shared: 'local' },
        timestamp: 1000,
        retryCount: 0,
        maxRetries: 3,
        status: 'pending' as const,
      };

      const serverChange: CanvasChange = {
        id: 's1',
        documentId: 'doc-1',
        operation: 'update',
        timestamp: 2000,
        userId: 'user-2',
        data: { server: true, shared: 'server' },
        version: 2,
      };

      const resolved = queue.resolveConflict(queuedOp, serverChange, 'merge');
      expect(resolved.data).toEqual({
        server: true,
        local: true,
        shared: 'local', // Local overwrites in merge
      });
    });
  });

  describe('Persistence', () => {
    it('should persist queue to localStorage', () => {
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { a: 1 }, maxRetries: 3 });
      
      const stored = localStorage.getItem('test-queue');
      expect(stored).toBeTruthy();
      
      const parsed = JSON.parse(stored!);
      expect(parsed).toHaveLength(1);
      expect(parsed[0].documentId).toBe('doc-1');
    });

    it('should load queue from localStorage', () => {
      queue.enqueue({ documentId: 'doc-1', operation: 'update', data: { a: 1 }, maxRetries: 3 });
      
      // Create new queue instance
      const newQueue = new OfflineQueue({
        maxQueueSize: 100,
        conflictStrategy: 'last-write-wins',
        enablePersistence: true,
        storageKey: 'test-queue',
      });
      
      expect(newQueue.size()).toBe(1);
    });
  });
});
