/**
 * Cache Metrics Service Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { CacheMetricsService } from '../cache-metrics';
import Redis from 'ioredis';

describe('CacheMetricsService', () => {
  let service: CacheMetricsService;
  let mockRedis: Redis;

  beforeEach(() => {
    mockRedis = {
      info: vi.fn(),
    } as unknown as Redis;
    service = new CacheMetricsService(mockRedis);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe('recordHit', () => {
    it('should record cache hit', () => {
      service.recordHit('test-cache');

      const metrics = service.getCustomMetrics('test-cache');
      expect(metrics.hits).toBe(1);
      expect(metrics.misses).toBe(0);
      expect(metrics.hitRate).toBe(1);
    });
  });

  describe('recordMiss', () => {
    it('should record cache miss', () => {
      service.recordMiss('test-cache');

      const metrics = service.getCustomMetrics('test-cache');
      expect(metrics.hits).toBe(0);
      expect(metrics.misses).toBe(1);
      expect(metrics.hitRate).toBe(0);
    });
  });

  describe('getCustomMetrics', () => {
    it('should calculate hit rate correctly', () => {
      service.recordHit('test-cache');
      service.recordHit('test-cache');
      service.recordMiss('test-cache');

      const metrics = service.getCustomMetrics('test-cache');
      expect(metrics.hitRate).toBe(0.6666666666666666);
    });

    it('should return zero metrics for unknown cache', () => {
      const metrics = service.getCustomMetrics('unknown');
      expect(metrics.hits).toBe(0);
      expect(metrics.misses).toBe(0);
      expect(metrics.hitRate).toBe(0);
    });
  });

  describe('resetCustomMetrics', () => {
    it('should reset metrics for specific cache', () => {
      service.recordHit('test-cache');
      service.recordMiss('test-cache');

      service.resetCustomMetrics('test-cache');

      const metrics = service.getCustomMetrics('test-cache');
      expect(metrics.hits).toBe(0);
      expect(metrics.misses).toBe(0);
    });

    it('should reset all metrics when no cache specified', () => {
      service.recordHit('cache1');
      service.recordHit('cache2');

      service.resetCustomMetrics();

      expect(service.getCustomMetrics('cache1').hits).toBe(0);
      expect(service.getCustomMetrics('cache2').hits).toBe(0);
    });
  });

  describe('collectRedisMetrics', () => {
    it('should collect Redis metrics', async () => {
      (mockRedis.info as any)
        .mockResolvedValueOnce('keyspace_hits:1000\nkeyspace_misses:200')
        .mockResolvedValueOnce('used_memory:1048576\nmaxmemory:2097152');

      const metrics = await service.collectRedisMetrics();

      expect(metrics.hits).toBe(1000);
      expect(metrics.misses).toBe(200);
      expect(metrics.hitRate).toBe(0.8333333333333334);
      expect(metrics.memoryUsage).toBe(1048576);
      expect(metrics.memoryMax).toBe(2097152);
    });

    it('should return default metrics on error', async () => {
      (mockRedis.info as any).mockRejectedValue(new Error('Redis error'));

      const metrics = await service.collectRedisMetrics();

      expect(metrics.hits).toBe(0);
      expect(metrics.misses).toBe(0);
      expect(metrics.hitRate).toBe(0);
    });
  });

  describe('getHealthStatus', () => {
    it('should return healthy for good metrics', () => {
      service['metrics'].set('redis', {
        hits: 800,
        misses: 200,
        hitRate: 0.8,
        evictions: 0,
        keyspaceHits: 800,
        keyspaceMisses: 200,
        memoryUsage: 1000,
        memoryMax: 10000,
        memoryUsagePercent: 10,
      });

      const health = service.getHealthStatus('redis');
      expect(health).toBe('healthy');
    });

    it('should return critical for low hit rate', () => {
      service['metrics'].set('redis', {
        hits: 300,
        misses: 700,
        hitRate: 0.3,
        evictions: 0,
        keyspaceHits: 300,
        keyspaceMisses: 700,
        memoryUsage: 1000,
        memoryMax: 10000,
        memoryUsagePercent: 10,
      });

      const health = service.getHealthStatus('redis');
      expect(health).toBe('critical');
    });

    it('should return critical for high memory usage', () => {
      service['metrics'].set('redis', {
        hits: 800,
        misses: 200,
        hitRate: 0.8,
        evictions: 0,
        keyspaceHits: 800,
        keyspaceMisses: 200,
        memoryUsage: 9500,
        memoryMax: 10000,
        memoryUsagePercent: 95,
      });

      const health = service.getHealthStatus('redis');
      expect(health).toBe('critical');
    });
  });
});
