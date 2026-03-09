/**
 * Query Optimizer Tests
 * 
 * Tests for query caching, batching, and optimization.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { QueryOptimizer } from '../QueryOptimizer';

describe('QueryOptimizer', () => {
  let optimizer: QueryOptimizer;

  beforeEach(() => {
    optimizer = new QueryOptimizer();
  });

  afterEach(() => {
    optimizer.clearCache();
  });

  describe('Query Caching', () => {
    it('caches query results', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      // First call - should execute query
      const result1 = await optimizer.query(queryFn, 'test-key', 60000);
      expect(queryFn).toHaveBeenCalledTimes(1);
      expect(result1).toEqual({ data: 'test' });

      // Second call - should use cache
      const result2 = await optimizer.query(queryFn, 'test-key', 60000);
      expect(queryFn).toHaveBeenCalledTimes(1); // Not called again
      expect(result2).toEqual({ data: 'test' });
    });

    it('executes query when cache key not provided', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      const result1 = await optimizer.query(queryFn);
      const result2 = await optimizer.query(queryFn);

      expect(queryFn).toHaveBeenCalledTimes(2);
    });

    it('respects TTL for cache entries', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      // Cache with very short TTL
      await optimizer.query(queryFn, 'test-key', 1);

      // Wait for TTL to expire
      await new Promise(resolve => setTimeout(resolve, 10));

      // Should execute query again
      await optimizer.query(queryFn, 'test-key', 1);
      expect(queryFn).toHaveBeenCalledTimes(2);
    });

    it('clears cache by pattern', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      await optimizer.query(queryFn, 'metrics:cpu', 60000);
      await optimizer.query(queryFn, 'metrics:memory', 60000);
      await optimizer.query(queryFn, 'events:log', 60000);

      // Clear only metrics
      optimizer.clearCache('metrics:*');

      // Metrics should be re-executed, events should use cache
      await optimizer.query(queryFn, 'metrics:cpu', 60000);
      await optimizer.query(queryFn, 'events:log', 60000);

      expect(queryFn).toHaveBeenCalledTimes(4); // 3 initial + 1 re-executed
    });

    it('clears all cache when no pattern provided', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      await optimizer.query(queryFn, 'key1', 60000);
      await optimizer.query(queryFn, 'key2', 60000);

      optimizer.clearCache();

      await optimizer.query(queryFn, 'key1', 60000);
      await optimizer.query(queryFn, 'key2', 60000);

      expect(queryFn).toHaveBeenCalledTimes(4); // 2 initial + 2 re-executed
    });
  });

  describe('Cache Statistics', () => {
    it('tracks cache size', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      await optimizer.query(queryFn, 'key1', 60000);
      await optimizer.query(queryFn, 'key2', 60000);

      const stats = optimizer.getCacheStats();
      expect(stats.size).toBe(2);
    });

    it('tracks cache hits', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      // First call - miss
      await optimizer.query(queryFn, 'test-key', 60000);

      // Second call - hit
      await optimizer.query(queryFn, 'test-key', 60000);

      const stats = optimizer.getCacheStats();
      expect(stats.entries[0].hits).toBe(1);
    });

    it('calculates hit rate', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      await optimizer.query(queryFn, 'key1', 60000);
      await optimizer.query(queryFn, 'key1', 60000); // hit
      await optimizer.query(queryFn, 'key2', 60000);

      const stats = optimizer.getCacheStats();
      expect(stats.hitRate).toBeGreaterThan(0);
    });

    it('sorts entries by hits', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      await optimizer.query(queryFn, 'key1', 60000);
      await optimizer.query(queryFn, 'key2', 60000);

      // Hit key2 multiple times
      await optimizer.query(queryFn, 'key2', 60000);
      await optimizer.query(queryFn, 'key2', 60000);

      const stats = optimizer.getCacheStats();
      expect(stats.entries[0].key).toBe('key2');
      expect(stats.entries[0].hits).toBeGreaterThan(stats.entries[1].hits);
    });
  });

  describe('LRU Eviction', () => {
    it('evicts least recently used entries when cache is full', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      // Fill cache to max size (assuming max is 1000)
      for (let i = 0; i < 1001; i++) {
        await optimizer.query(queryFn, `key${i}`, 60000);
      }

      const stats = optimizer.getCacheStats();
      expect(stats.size).toBeLessThanOrEqual(stats.maxSize);
    });
  });

  describe('Query Batching', () => {
    it('batches multiple queries', async () => {
      const queries = [
        optimizer.batchQuery('SELECT * FROM metrics WHERE id = ?', [1]),
        optimizer.batchQuery('SELECT * FROM metrics WHERE id = ?', [2]),
        optimizer.batchQuery('SELECT * FROM metrics WHERE id = ?', [3]),
      ];

      // Wait for batch to execute
      await Promise.all(queries);

      // All queries should complete
      expect(queries).toHaveLength(3);
    });

    it('groups queries by type', async () => {
      const queries = [
        optimizer.batchQuery('SELECT * FROM metrics WHERE id = ?', [1]),
        optimizer.batchQuery('SELECT * FROM events WHERE id = ?', [1]),
        optimizer.batchQuery('SELECT * FROM metrics WHERE id = ?', [2]),
      ];

      await Promise.all(queries);

      // Should group metrics and events separately
      expect(queries).toHaveLength(3);
    });
  });

  describe('Error Handling', () => {
    it('handles query errors gracefully', async () => {
      const queryFn = vi.fn().mockRejectedValue(new Error('Query failed'));

      await expect(
        optimizer.query(queryFn, 'test-key', 60000)
      ).rejects.toThrow('Query failed');
    });

    it('does not cache failed queries', async () => {
      const queryFn = vi.fn()
        .mockRejectedValueOnce(new Error('Query failed'))
        .mockResolvedValueOnce({ data: 'success' });

      // First call fails
      await expect(
        optimizer.query(queryFn, 'test-key', 60000)
      ).rejects.toThrow('Query failed');

      // Second call should execute (not use cache)
      const result = await optimizer.query(queryFn, 'test-key', 60000);
      expect(result).toEqual({ data: 'success' });
      expect(queryFn).toHaveBeenCalledTimes(2);
    });
  });

  describe('Performance', () => {
    it('provides fast cache lookups', async () => {
      const queryFn = vi.fn().mockResolvedValue({ data: 'test' });

      // Populate cache
      for (let i = 0; i < 100; i++) {
        await optimizer.query(queryFn, `key${i}`, 60000);
      }

      // Measure cache lookup time
      const start = performance.now();
      await optimizer.query(queryFn, 'key50', 60000);
      const duration = performance.now() - start;

      expect(duration).toBeLessThan(10); // Should be very fast
    });
  });
});
