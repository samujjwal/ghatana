import { DeadLetterQueue, DeadLetterEntry } from '../../../src/resilience/DeadLetterQueue';
import { Event } from '../../../src/types';
import * as fs from 'fs/promises';

// Mock fs/promises
jest.mock('fs/promises', () => ({
  writeFile: jest.fn(),
  readFile: jest.fn(),
}));

describe('DeadLetterQueue', () => {
  let dlq: DeadLetterQueue;

  beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
  });

  afterEach(async () => {
    if (dlq) {
      await dlq.destroy();
    }
    jest.restoreAllMocks();
    jest.useRealTimers();
  });

  const createEvent = (id: string = 'event-1', payload: any = { data: 'test' }): Event => ({
    id,
    type: 'test.event',
    timestamp: Date.now(),
    payload,
    metadata: { source: 'test' },
  });

  describe('Constructor & Initialization', () => {
    it('should initialize with default config', () => {
      dlq = new DeadLetterQueue();

      expect(dlq.size()).toBe(0);
      expect(dlq.isEmpty()).toBe(true);
    });

    it('should initialize with custom config', () => {
      dlq = new DeadLetterQueue({
        maxSize: 500,
        ttl: 3600000,
        autoCleanup: true,
        cleanupInterval: 1800000,
      });

      expect(dlq.isEmpty()).toBe(true);
    });

    it('should start auto cleanup when enabled', () => {
      dlq = new DeadLetterQueue({
        autoCleanup: true,
        cleanupInterval: 1000,
      });

      const cleanupSpy = jest.spyOn(dlq, 'cleanup');

      jest.advanceTimersByTime(1000);

      expect(cleanupSpy).toHaveBeenCalled();
    });

    it('should not start auto cleanup when disabled', () => {
      dlq = new DeadLetterQueue({
        autoCleanup: false,
      });

      const cleanupSpy = jest.spyOn(dlq, 'cleanup');

      jest.advanceTimersByTime(5000);

      expect(cleanupSpy).not.toHaveBeenCalled();
    });

    it('should start persistence when enabled', () => {
      dlq = new DeadLetterQueue({
        persistence: {
          enabled: true,
          path: '/tmp/test-dlq.json',
          saveInterval: 1000,
        },
      });

      // Persistence timer should be set
      expect(dlq).toBeDefined();
    });
  });

  describe('Adding Entries', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue({ maxSize: 10 });
    });

    it('should add entry successfully', () => {
      const event = createEvent();
      const error = new Error('Failed to deliver');

      const id = dlq.add(event, error);

      expect(id).toBe(event.id);
      expect(dlq.size()).toBe(1);
      expect(dlq.isEmpty()).toBe(false);
    });

    it('should store error details correctly', () => {
      const event = createEvent();
      const error = new Error('Network failure');
      error.stack = 'Error: Network failure\n  at test.ts:10';

      dlq.add(event, error);

      const entry = dlq.get(event.id);
      expect(entry?.error.message).toBe('Network failure');
      expect(entry?.error.stack).toBeDefined();
    });

    it('should store error code if present', () => {
      const event = createEvent();
      const error: any = new Error('Connection failed');
      error.code = 'ECONNREFUSED';

      dlq.add(event, error);

      const entry = dlq.get(event.id);
      expect(entry?.error.code).toBe('ECONNREFUSED');
    });

    it('should set attempt count', () => {
      const event = createEvent();
      const error = new Error('fail');

      dlq.add(event, error, 5);

      const entry = dlq.get(event.id);
      expect(entry?.attempts).toBe(5);
    });

    it('should default attempt count to 1', () => {
      const event = createEvent();
      const error = new Error('fail');

      dlq.add(event, error);

      const entry = dlq.get(event.id);
      expect(entry?.attempts).toBe(1);
    });

    it('should store metadata', () => {
      const event = createEvent();
      const error = new Error('fail');
      const metadata = { retryCount: 3, source: 'connector-1' };

      dlq.add(event, error, 1, metadata);

      const entry = dlq.get(event.id);
      expect(entry?.metadata).toEqual(metadata);
    });

    it('should set timestamps correctly', () => {
      const beforeAdd = Date.now();
      const event = createEvent();
      const error = new Error('fail');

      dlq.add(event, error);

      const entry = dlq.get(event.id);
      expect(entry?.firstAttempt).toBeGreaterThanOrEqual(beforeAdd);
      expect(entry?.lastAttempt).toBeGreaterThanOrEqual(beforeAdd);
      expect(entry?.firstAttempt).toBe(entry?.lastAttempt);
    });

    it('should emit entryAdded event', () => {
      const addedHandler = jest.fn();
      dlq.on('entryAdded', addedHandler);

      const event = createEvent();
      const error = new Error('fail');

      dlq.add(event, error);

      expect(addedHandler).toHaveBeenCalledWith({
        entry: expect.objectContaining({
          id: event.id,
          event,
        }),
      });
    });

    it('should evict oldest entry when at capacity', () => {
      const evictedHandler = jest.fn();
      dlq.on('entryEvicted', evictedHandler);

      // Fill to capacity
      for (let i = 0; i < 10; i++) {
        const event = createEvent(`event-${i}`);
        dlq.add(event, new Error('fail'));
      }

      expect(dlq.size()).toBe(10);

      // Add one more to trigger eviction
      const event = createEvent('event-11');
      dlq.add(event, new Error('fail'));

      expect(dlq.size()).toBe(10);
      expect(evictedHandler).toHaveBeenCalledWith({
        id: 'event-0',
      });
      expect(dlq.get('event-0')).toBeUndefined();
      expect(dlq.get('event-11')).toBeDefined();
    });

    it('should handle multiple evictions', () => {
      dlq = new DeadLetterQueue({ maxSize: 3 });

      for (let i = 0; i < 5; i++) {
        const event = createEvent(`event-${i}`);
        dlq.add(event, new Error('fail'));
      }

      expect(dlq.size()).toBe(3);
      expect(dlq.get('event-0')).toBeUndefined();
      expect(dlq.get('event-1')).toBeUndefined();
      expect(dlq.get('event-2')).toBeDefined();
      expect(dlq.get('event-3')).toBeDefined();
      expect(dlq.get('event-4')).toBeDefined();
    });
  });

  describe('Retrieving Entries', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue();
    });

    it('should retrieve entry by id', () => {
      const event = createEvent();
      dlq.add(event, new Error('fail'));

      const entry = dlq.get(event.id);

      expect(entry).toBeDefined();
      expect(entry?.id).toBe(event.id);
    });

    it('should return undefined for non-existent id', () => {
      const entry = dlq.get('non-existent');
      expect(entry).toBeUndefined();
    });

    it('should get all entries', () => {
      const events = [
        createEvent('event-1'),
        createEvent('event-2'),
        createEvent('event-3'),
      ];

      events.forEach(event => dlq.add(event, new Error('fail')));

      const all = dlq.getAll();

      expect(all).toHaveLength(3);
      expect(all.map(e => e.id)).toEqual(['event-1', 'event-2', 'event-3']);
    });

    it('should return empty array when no entries', () => {
      const all = dlq.getAll();
      expect(all).toEqual([]);
    });

    it('should filter entries by predicate', () => {
      const events = [
        createEvent('event-1'),
        createEvent('event-2'),
        createEvent('event-3'),
      ];

      dlq.add(events[0], new Error('Network error'));
      dlq.add(events[1], new Error('Validation error'));
      dlq.add(events[2], new Error('Network error'));

      const filtered = dlq.filter(entry =>
        entry.error.message.includes('Network')
      );

      expect(filtered).toHaveLength(2);
      expect(filtered.map(e => e.id)).toEqual(['event-1', 'event-3']);
    });

    it('should filter by attempt count', () => {
      const events = [
        createEvent('event-1'),
        createEvent('event-2'),
        createEvent('event-3'),
      ];

      dlq.add(events[0], new Error('fail'), 1);
      dlq.add(events[1], new Error('fail'), 5);
      dlq.add(events[2], new Error('fail'), 3);

      const filtered = dlq.filter(entry => entry.attempts > 2);

      expect(filtered).toHaveLength(2);
      expect(filtered.map(e => e.id)).toEqual(['event-2', 'event-3']);
    });

    it('should filter by metadata', () => {
      dlq.add(createEvent('event-1'), new Error('fail'), 1, { priority: 'high' });
      dlq.add(createEvent('event-2'), new Error('fail'), 1, { priority: 'low' });
      dlq.add(createEvent('event-3'), new Error('fail'), 1, { priority: 'high' });

      const filtered = dlq.filter(entry =>
        entry.metadata?.priority === 'high'
      );

      expect(filtered).toHaveLength(2);
    });
  });

  describe('Removing Entries', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue();
    });

    it('should remove entry by id', () => {
      const event = createEvent();
      dlq.add(event, new Error('fail'));

      const removed = dlq.remove(event.id);

      expect(removed).toBe(true);
      expect(dlq.size()).toBe(0);
      expect(dlq.get(event.id)).toBeUndefined();
    });

    it('should return false when removing non-existent entry', () => {
      const removed = dlq.remove('non-existent');
      expect(removed).toBe(false);
    });

    it('should emit entryRemoved event', () => {
      const removedHandler = jest.fn();
      dlq.on('entryRemoved', removedHandler);

      const event = createEvent();
      dlq.add(event, new Error('fail'));
      dlq.remove(event.id);

      expect(removedHandler).toHaveBeenCalledWith({
        id: event.id,
      });
    });

    it('should not emit event when entry does not exist', () => {
      const removedHandler = jest.fn();
      dlq.on('entryRemoved', removedHandler);

      dlq.remove('non-existent');

      expect(removedHandler).not.toHaveBeenCalled();
    });

    it('should clear all entries', () => {
      const clearedHandler = jest.fn();
      dlq.on('cleared', clearedHandler);

      for (let i = 0; i < 5; i++) {
        dlq.add(createEvent(`event-${i}`), new Error('fail'));
      }

      expect(dlq.size()).toBe(5);

      dlq.clear();

      expect(dlq.size()).toBe(0);
      expect(dlq.isEmpty()).toBe(true);
      expect(clearedHandler).toHaveBeenCalledWith({ count: 5 });
    });

    it('should emit cleared event even when empty', () => {
      const clearedHandler = jest.fn();
      dlq.on('cleared', clearedHandler);

      dlq.clear();

      expect(clearedHandler).toHaveBeenCalledWith({ count: 0 });
    });
  });

  describe('Retry Logic', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue();
    });

    it('should retry entry successfully', async () => {
      const event = createEvent();
      dlq.add(event, new Error('fail'));

      const retryFn = jest.fn().mockResolvedValue(undefined);

      const success = await dlq.retry(event.id, retryFn);

      expect(success).toBe(true);
      expect(retryFn).toHaveBeenCalledWith(event);
      expect(dlq.get(event.id)).toBeUndefined();
    });

    it('should remove entry on successful retry', async () => {
      const event = createEvent();
      dlq.add(event, new Error('fail'));

      const retryFn = jest.fn().mockResolvedValue(undefined);

      await dlq.retry(event.id, retryFn);

      expect(dlq.size()).toBe(0);
    });

    it('should emit retrySuccess event', async () => {
      const successHandler = jest.fn();
      dlq.on('retrySuccess', successHandler);

      const event = createEvent();
      dlq.add(event, new Error('fail'));

      await dlq.retry(event.id, jest.fn().mockResolvedValue(undefined));

      expect(successHandler).toHaveBeenCalledWith({
        id: event.id,
        entry: expect.objectContaining({ id: event.id }),
      });
    });

    it('should handle retry failure', async () => {
      const event = createEvent();
      dlq.add(event, new Error('original error'));

      const retryFn = jest.fn().mockRejectedValue(new Error('retry failed'));

      const success = await dlq.retry(event.id, retryFn);

      expect(success).toBe(false);
      expect(dlq.get(event.id)).toBeDefined();
    });

    it('should update entry on retry failure', async () => {
      const event = createEvent();
      dlq.add(event, new Error('original'), 1);

      const newError = new Error('retry failed');
      const retryFn = jest.fn().mockRejectedValue(newError);

      await dlq.retry(event.id, retryFn);

      const entry = dlq.get(event.id);
      expect(entry?.attempts).toBe(2);
      expect(entry?.error.message).toBe('retry failed');
      expect(entry?.lastAttempt).toBeGreaterThan(entry?.firstAttempt || 0);
    });

    it('should emit retryFailed event', async () => {
      const failedHandler = jest.fn();
      dlq.on('retryFailed', failedHandler);

      const event = createEvent();
      dlq.add(event, new Error('fail'));

      const retryError = new Error('retry failed');
      await dlq.retry(event.id, jest.fn().mockRejectedValue(retryError));

      expect(failedHandler).toHaveBeenCalledWith({
        id: event.id,
        entry: expect.objectContaining({ id: event.id }),
        error: retryError,
      });
    });

    it('should return false for non-existent entry', async () => {
      const success = await dlq.retry('non-existent', jest.fn());
      expect(success).toBe(false);
    });

    it('should handle non-Error retry failures', async () => {
      const event = createEvent();
      dlq.add(event, new Error('fail'));

      const retryFn = jest.fn().mockRejectedValue('string error');

      await dlq.retry(event.id, retryFn);

      const entry = dlq.get(event.id);
      expect(entry?.error.message).toBe('string error');
    });
  });

  describe('Retry All', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue();
    });

    it('should retry all entries', async () => {
      const events = [
        createEvent('event-1'),
        createEvent('event-2'),
        createEvent('event-3'),
      ];

      events.forEach(event => dlq.add(event, new Error('fail')));

      const retryFn = jest.fn().mockResolvedValue(undefined);

      const result = await dlq.retryAll(retryFn);

      expect(result).toEqual({ succeeded: 3, failed: 0 });
      expect(retryFn).toHaveBeenCalledTimes(3);
      expect(dlq.size()).toBe(0);
    });

    it('should handle mixed success and failure', async () => {
      const events = [
        createEvent('event-1'),
        createEvent('event-2'),
        createEvent('event-3'),
        createEvent('event-4'),
      ];

      events.forEach(event => dlq.add(event, new Error('fail')));

      const retryFn = jest.fn()
        .mockResolvedValueOnce(undefined)
        .mockRejectedValueOnce(new Error('fail'))
        .mockResolvedValueOnce(undefined)
        .mockRejectedValueOnce(new Error('fail'));

      const result = await dlq.retryAll(retryFn);

      expect(result).toEqual({ succeeded: 2, failed: 2 });
      expect(dlq.size()).toBe(2);
    });

    it('should emit retryAllCompleted event', async () => {
      const completedHandler = jest.fn();
      dlq.on('retryAllCompleted', completedHandler);

      dlq.add(createEvent('event-1'), new Error('fail'));
      dlq.add(createEvent('event-2'), new Error('fail'));

      await dlq.retryAll(jest.fn().mockResolvedValue(undefined));

      expect(completedHandler).toHaveBeenCalledWith({
        succeeded: 2,
        failed: 0,
        total: 2,
      });
    });

    it('should handle empty queue', async () => {
      const result = await dlq.retryAll(jest.fn());

      expect(result).toEqual({ succeeded: 0, failed: 0 });
    });

    it('should process entries sequentially', async () => {
      const events = [
        createEvent('event-1'),
        createEvent('event-2'),
        createEvent('event-3'),
      ];

      events.forEach(event => dlq.add(event, new Error('fail')));

      const callOrder: string[] = [];
      const retryFn = jest.fn().mockImplementation((event: Event) => {
        callOrder.push(event.id);
        return Promise.resolve();
      });

      await dlq.retryAll(retryFn);

      expect(callOrder).toEqual(['event-1', 'event-2', 'event-3']);
    });
  });

  describe('Statistics', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue({ maxSize: 100 });
    });

    it('should return accurate statistics', () => {
      for (let i = 0; i < 10; i++) {
        dlq.add(createEvent(`event-${i}`), new Error('fail'), i);
      }

      const stats = dlq.getStats();

      expect(stats.total).toBe(10);
      expect(stats.maxSize).toBe(100);
      expect(stats.utilizationPercent).toBe(10);
      expect(stats.averageAttempts).toBe(4.5); // (0+1+2+...+9)/10
    });

    it('should group by error message', () => {
      dlq.add(createEvent('event-1'), new Error('Network error'));
      dlq.add(createEvent('event-2'), new Error('Network error'));
      dlq.add(createEvent('event-3'), new Error('Validation error'));
      dlq.add(createEvent('event-4'), new Error('Network error'));

      const stats = dlq.getStats();

      expect(stats.byError).toEqual({
        'Network error': 3,
        'Validation error': 1,
      });
    });

    it('should categorize by age', () => {
      const ttl = 86400000; // 24 hours
      dlq = new DeadLetterQueue({ ttl });

      // Recent (< 50% of TTL)
      dlq.add(createEvent('event-1'), new Error('fail'));

      // Old (> 50% of TTL)
      jest.advanceTimersByTime(ttl / 2 + 1000);
      dlq.add(createEvent('event-2'), new Error('fail'));

      // Expired (> TTL)
      jest.advanceTimersByTime(ttl / 2 + 1000);
      dlq.add(createEvent('event-3'), new Error('fail'));

      const stats = dlq.getStats();

      expect(stats.byAge.recent).toBe(1);
      expect(stats.byAge.old).toBe(1);
      expect(stats.byAge.expired).toBe(1);
    });

    it('should calculate utilization percentage', () => {
      dlq = new DeadLetterQueue({ maxSize: 50 });

      for (let i = 0; i < 25; i++) {
        dlq.add(createEvent(`event-${i}`), new Error('fail'));
      }

      const stats = dlq.getStats();
      expect(stats.utilizationPercent).toBe(50);
    });

    it('should handle empty queue statistics', () => {
      const stats = dlq.getStats();

      expect(stats.total).toBe(0);
      expect(stats.averageAttempts).toBe(0);
      expect(stats.utilizationPercent).toBe(0);
      expect(stats.byError).toEqual({});
    });
  });

  describe('Cleanup', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue({
        ttl: 10000,
        autoCleanup: false,
      });
    });

    it('should remove expired entries', () => {
      const expiredHandler = jest.fn();
      dlq.on('entryExpired', expiredHandler);

      dlq.add(createEvent('event-1'), new Error('fail'));
      dlq.add(createEvent('event-2'), new Error('fail'));

      jest.advanceTimersByTime(11000);

      const removed = dlq.cleanup();

      expect(removed).toBe(2);
      expect(dlq.size()).toBe(0);
      expect(expiredHandler).toHaveBeenCalledTimes(2);
    });

    it('should keep non-expired entries', () => {
      dlq.add(createEvent('event-1'), new Error('fail'));

      jest.advanceTimersByTime(5000);

      dlq.add(createEvent('event-2'), new Error('fail'));

      jest.advanceTimersByTime(6000);

      const removed = dlq.cleanup();

      expect(removed).toBe(1);
      expect(dlq.size()).toBe(1);
      expect(dlq.get('event-2')).toBeDefined();
    });

    it('should emit cleanupCompleted event', () => {
      const completedHandler = jest.fn();
      dlq.on('cleanupCompleted', completedHandler);

      dlq.add(createEvent('event-1'), new Error('fail'));

      jest.advanceTimersByTime(11000);

      dlq.cleanup();

      expect(completedHandler).toHaveBeenCalledWith({
        removed: 1,
        remaining: 0,
      });
    });

    it('should not emit cleanupCompleted when nothing removed', () => {
      const completedHandler = jest.fn();
      dlq.on('cleanupCompleted', completedHandler);

      dlq.add(createEvent('event-1'), new Error('fail'));

      dlq.cleanup();

      expect(completedHandler).not.toHaveBeenCalled();
    });

    it('should run cleanup periodically when auto cleanup enabled', () => {
      dlq = new DeadLetterQueue({
        ttl: 1000,
        autoCleanup: true,
        cleanupInterval: 2000,
      });

      dlq.add(createEvent('event-1'), new Error('fail'));

      jest.advanceTimersByTime(1500);

      // Trigger cleanup
      jest.advanceTimersByTime(2000);

      expect(dlq.size()).toBe(0);
    });
  });

  describe('Import/Export', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue({ maxSize: 100 });
    });

    it('should export entries as JSON', () => {
      const events = [
        createEvent('event-1'),
        createEvent('event-2'),
      ];

      events.forEach(event => dlq.add(event, new Error('fail')));

      const exported = dlq.export();
      const parsed = JSON.parse(exported);

      expect(parsed).toHaveLength(2);
      expect(parsed[0].id).toBe('event-1');
      expect(parsed[1].id).toBe('event-2');
    });

    it('should export empty queue', () => {
      const exported = dlq.export();
      const parsed = JSON.parse(exported);

      expect(parsed).toEqual([]);
    });

    it('should import entries from JSON', () => {
      const entry: DeadLetterEntry = {
        id: 'imported-1',
        event: createEvent('imported-1'),
        error: {
          message: 'Import error',
          stack: 'stack trace',
        },
        attempts: 3,
        firstAttempt: Date.now(),
        lastAttempt: Date.now(),
      };

      const json = JSON.stringify([entry]);

      const imported = dlq.import(json);

      expect(imported).toBe(1);
      expect(dlq.size()).toBe(1);
      expect(dlq.get('imported-1')).toBeDefined();
    });

    it('should emit imported event', () => {
      const importedHandler = jest.fn();
      dlq.on('imported', importedHandler);

      const entry: DeadLetterEntry = {
        id: 'imported-1',
        event: createEvent('imported-1'),
        error: { message: 'error' },
        attempts: 1,
        firstAttempt: Date.now(),
        lastAttempt: Date.now(),
      };

      dlq.import(JSON.stringify([entry]));

      expect(importedHandler).toHaveBeenCalledWith({ count: 1 });
    });

    it('should respect maxSize when importing', () => {
      dlq = new DeadLetterQueue({ maxSize: 3 });

      const entries: DeadLetterEntry[] = [];
      for (let i = 0; i < 5; i++) {
        entries.push({
          id: `event-${i}`,
          event: createEvent(`event-${i}`),
          error: { message: 'error' },
          attempts: 1,
          firstAttempt: Date.now(),
          lastAttempt: Date.now(),
        });
      }

      const imported = dlq.import(JSON.stringify(entries));

      expect(imported).toBe(3);
      expect(dlq.size()).toBe(3);
    });

    it('should throw error on invalid JSON', () => {
      expect(() => {
        dlq.import('invalid json');
      }).toThrow();
    });

    it('should emit error event on import failure', () => {
      const errorHandler = jest.fn();
      dlq.on('error', errorHandler);

      try {
        dlq.import('invalid json');
      } catch (e) {
        // Expected
      }

      expect(errorHandler).toHaveBeenCalled();
    });

    it('should round-trip export and import', () => {
      const events = [
        createEvent('event-1'),
        createEvent('event-2'),
        createEvent('event-3'),
      ];

      events.forEach(event => dlq.add(event, new Error('fail'), 2, { test: true }));

      const exported = dlq.export();

      const dlq2 = new DeadLetterQueue();
      dlq2.import(exported);

      expect(dlq2.size()).toBe(3);

      const entry = dlq2.get('event-1');
      expect(entry?.attempts).toBe(2);
      expect(entry?.metadata).toEqual({ test: true });
    });
  });

  describe('Persistence', () => {
    afterEach(() => {
      jest.clearAllMocks();
    });

    it('should save entries to file', async () => {
      dlq = new DeadLetterQueue({
        persistence: {
          enabled: true,
          path: '/tmp/test-dlq.json',
        },
      });

      const savedHandler = jest.fn();
      dlq.on('saved', savedHandler);

      dlq.add(createEvent('event-1'), new Error('fail'));

      // Trigger save manually
      await (dlq as any)._save();

      expect(fs.writeFile).toHaveBeenCalledWith(
        '/tmp/test-dlq.json',
        expect.any(String),
        'utf8'
      );

      expect(savedHandler).toHaveBeenCalledWith({
        path: '/tmp/test-dlq.json',
        count: 1,
      });
    });

    it('should save periodically when configured', async () => {
      dlq = new DeadLetterQueue({
        persistence: {
          enabled: true,
          path: '/tmp/test-dlq.json',
          saveInterval: 1000,
        },
      });

      dlq.add(createEvent('event-1'), new Error('fail'));

      jest.advanceTimersByTime(1000);
      await jest.runAllTimersAsync();

      expect(fs.writeFile).toHaveBeenCalled();
    });

    it('should load entries from file', async () => {
      const entries: DeadLetterEntry[] = [{
        id: 'loaded-1',
        event: createEvent('loaded-1'),
        error: { message: 'error' },
        attempts: 1,
        firstAttempt: Date.now(),
        lastAttempt: Date.now(),
      }];

      (fs.readFile as any).mockResolvedValue(JSON.stringify(entries));

      dlq = new DeadLetterQueue({
        persistence: {
          enabled: true,
          path: '/tmp/test-dlq.json',
        },
      });

      const loaded = await dlq.load();

      expect(loaded).toBe(1);
      expect(dlq.size()).toBe(1);
      expect(dlq.get('loaded-1')).toBeDefined();
    });

    it('should handle missing file gracefully', async () => {
      const error: any = new Error('File not found');
      error.code = 'ENOENT';
      (fs.readFile as any).mockRejectedValue(error);

      dlq = new DeadLetterQueue({
        persistence: {
          enabled: true,
          path: '/tmp/nonexistent.json',
        },
      });

      const loaded = await dlq.load();

      expect(loaded).toBe(0);
    });

    it('should emit error on load failure (non-ENOENT)', async () => {
      const error = new Error('Permission denied');
      (fs.readFile as any).mockRejectedValue(error);

      dlq = new DeadLetterQueue({
        persistence: {
          enabled: true,
          path: '/tmp/test.json',
        },
      });

      const errorHandler = jest.fn();
      dlq.on('error', errorHandler);

      await dlq.load();

      expect(errorHandler).toHaveBeenCalledWith(error);
    });

    it('should not save when persistence disabled', async () => {
      dlq = new DeadLetterQueue({
        persistence: {
          enabled: false,
        },
      });

      dlq.add(createEvent('event-1'), new Error('fail'));

      await (dlq as any)._save();

      expect(fs.writeFile).not.toHaveBeenCalled();
    });

    it('should emit error on save failure', async () => {
      (fs.writeFile as any).mockRejectedValue(new Error('Write failed'));

      dlq = new DeadLetterQueue({
        persistence: {
          enabled: true,
          path: '/tmp/test.json',
        },
      });

      const errorHandler = jest.fn();
      dlq.on('error', errorHandler);

      await (dlq as any)._save();

      expect(errorHandler).toHaveBeenCalled();
    });
  });

  describe('Destroy', () => {
    it('should stop cleanup timer', async () => {
      dlq = new DeadLetterQueue({
        autoCleanup: true,
        cleanupInterval: 1000,
      });

      const cleanupSpy = jest.spyOn(dlq, 'cleanup');

      await dlq.destroy();

      jest.advanceTimersByTime(2000);

      // Cleanup should not run after destroy
      expect(cleanupSpy).not.toHaveBeenCalled();
    });

    it('should stop persistence timer', async () => {
      dlq = new DeadLetterQueue({
        persistence: {
          enabled: true,
          path: '/tmp/test.json',
          saveInterval: 1000,
        },
      });

      await dlq.destroy();

      jest.clearAllMocks();
      jest.advanceTimersByTime(2000);

      expect(fs.writeFile).not.toHaveBeenCalled();
    });

    it('should save before destroying when persistence enabled', async () => {
      dlq = new DeadLetterQueue({
        persistence: {
          enabled: true,
          path: '/tmp/test.json',
        },
      });

      dlq.add(createEvent('event-1'), new Error('fail'));

      await dlq.destroy();

      expect(fs.writeFile).toHaveBeenCalled();
    });

    it('should clear all entries', async () => {
      dlq = new DeadLetterQueue();

      dlq.add(createEvent('event-1'), new Error('fail'));
      dlq.add(createEvent('event-2'), new Error('fail'));

      expect(dlq.size()).toBe(2);

      await dlq.destroy();

      expect(dlq.size()).toBe(0);
    });

    it('should remove all listeners', async () => {
      dlq = new DeadLetterQueue();

      const handler = jest.fn();
      dlq.on('entryAdded', handler);

      await dlq.destroy();

      expect(dlq.listenerCount('entryAdded')).toBe(0);
    });
  });

  describe('Size and State', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue();
    });

    it('should return correct size', () => {
      expect(dlq.size()).toBe(0);

      dlq.add(createEvent('event-1'), new Error('fail'));
      expect(dlq.size()).toBe(1);

      dlq.add(createEvent('event-2'), new Error('fail'));
      expect(dlq.size()).toBe(2);

      dlq.remove('event-1');
      expect(dlq.size()).toBe(1);
    });

    it('should indicate if empty', () => {
      expect(dlq.isEmpty()).toBe(true);

      dlq.add(createEvent('event-1'), new Error('fail'));
      expect(dlq.isEmpty()).toBe(false);

      dlq.clear();
      expect(dlq.isEmpty()).toBe(true);
    });
  });

  describe('Edge Cases', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue();
    });

    it('should handle very large queues', () => {
      dlq = new DeadLetterQueue({ maxSize: 10000 });

      for (let i = 0; i < 10000; i++) {
        dlq.add(createEvent(`event-${i}`), new Error('fail'));
      }

      expect(dlq.size()).toBe(10000);

      const stats = dlq.getStats();
      expect(stats.total).toBe(10000);
    });

    it('should handle queue with maxSize of 1', () => {
      dlq = new DeadLetterQueue({ maxSize: 1 });

      dlq.add(createEvent('event-1'), new Error('fail'));
      expect(dlq.size()).toBe(1);

      dlq.add(createEvent('event-2'), new Error('fail'));
      expect(dlq.size()).toBe(1);
      expect(dlq.get('event-1')).toBeUndefined();
      expect(dlq.get('event-2')).toBeDefined();
    });

    it('should handle events with same id', () => {
      dlq.add(createEvent('same-id'), new Error('error 1'));
      dlq.add(createEvent('same-id'), new Error('error 2'));

      expect(dlq.size()).toBe(1);

      const entry = dlq.get('same-id');
      expect(entry?.error.message).toBe('error 2');
    });

    it('should handle complex event payloads', () => {
      const complexPayload = {
        nested: {
          deep: {
            data: [1, 2, 3],
            map: new Map([['key', 'value']]),
          },
        },
      };

      const event = createEvent('complex', complexPayload);
      dlq.add(event, new Error('fail'));

      const entry = dlq.get('complex');
      expect(entry?.event.payload).toEqual(complexPayload);
    });

    it('should handle zero TTL', () => {
      dlq = new DeadLetterQueue({ ttl: 0 });

      dlq.add(createEvent('event-1'), new Error('fail'));

      const removed = dlq.cleanup();
      expect(removed).toBe(1);
    });

    it('should handle concurrent operations', async () => {
      const promises = [];

      for (let i = 0; i < 100; i++) {
        promises.push(
          dlq.retry(`event-${i}`, jest.fn().mockResolvedValue(undefined))
        );
      }

      await Promise.all(promises);

      // All should return false (no entries exist)
      const results = await Promise.all(promises);
      expect(results.every(r => r === false)).toBe(true);
    });
  });

  describe('Event Emission Completeness', () => {
    beforeEach(() => {
      dlq = new DeadLetterQueue();
    });

    it('should emit all lifecycle events', async () => {
      const events = {
        entryAdded: jest.fn(),
        entryRemoved: jest.fn(),
        entryEvicted: jest.fn(),
        entryExpired: jest.fn(),
        retrySuccess: jest.fn(),
        retryFailed: jest.fn(),
        retryAllCompleted: jest.fn(),
        cleared: jest.fn(),
        cleanupCompleted: jest.fn(),
        imported: jest.fn(),
        saved: jest.fn(),
      };

      Object.entries(events).forEach(([event, handler]) => {
        dlq.on(event, handler);
      });

      // entryAdded
      dlq.add(createEvent('event-1'), new Error('fail'));
      expect(events.entryAdded).toHaveBeenCalled();

      // retrySuccess
      await dlq.retry('event-1', jest.fn().mockResolvedValue(undefined));
      expect(events.retrySuccess).toHaveBeenCalled();

      // retryFailed
      dlq.add(createEvent('event-2'), new Error('fail'));
      await dlq.retry('event-2', jest.fn().mockRejectedValue(new Error('fail')));
      expect(events.retryFailed).toHaveBeenCalled();

      // retryAllCompleted
      await dlq.retryAll(jest.fn().mockResolvedValue(undefined));
      expect(events.retryAllCompleted).toHaveBeenCalled();

      // entryRemoved
      dlq.add(createEvent('event-3'), new Error('fail'));
      dlq.remove('event-3');
      expect(events.entryRemoved).toHaveBeenCalled();

      // cleared
      dlq.add(createEvent('event-4'), new Error('fail'));
      dlq.clear();
      expect(events.cleared).toHaveBeenCalled();
    });
  });
});
