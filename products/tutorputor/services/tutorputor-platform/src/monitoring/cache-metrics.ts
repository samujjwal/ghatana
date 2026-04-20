/**
 * Cache Performance Metrics Service
 *
 * Collects and reports cache performance metrics including hit rates,
 * eviction rates, and memory usage for Redis and in-memory caches.
 *
 * @doc.type service
 * @doc.purpose Cache performance monitoring and metrics
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';
import type Redis from 'ioredis';

const logger = createStandaloneLogger({ component: 'CacheMetricsService' });

export interface CacheMetrics {
  hits: number;
  misses: number;
  hitRate: number;
  evictions: number;
  keyspaceHits: number;
  keyspaceMisses: number;
  memoryUsage: number;
  memoryMax: number;
  memoryUsagePercent: number;
}

export class CacheMetricsService {
  private redis: Redis;
  private metrics: Map<string, CacheMetrics> = new Map();
  private customMetrics: Map<string, { hits: number; misses: number }> = new Map();

  constructor(redis: Redis) {
    this.redis = redis;
  }

  /**
   * Record cache hit
   */
  recordHit(cacheName: string): void {
    const metrics = this.customMetrics.get(cacheName) || { hits: 0, misses: 0 };
    metrics.hits++;
    this.customMetrics.set(cacheName, metrics);
  }

  /**
   * Record cache miss
   */
  recordMiss(cacheName: string): void {
    const metrics = this.customMetrics.get(cacheName) || { hits: 0, misses: 0 };
    metrics.misses++;
    this.customMetrics.set(cacheName, metrics);
  }

  /**
   * Get custom cache metrics
   */
  getCustomMetrics(cacheName: string): { hits: number; misses: number; hitRate: number } {
    const metrics = this.customMetrics.get(cacheName) || { hits: 0, misses: 0 };
    const total = metrics.hits + metrics.misses;
    return {
      hits: metrics.hits,
      misses: metrics.misses,
      hitRate: total > 0 ? metrics.hits / total : 0,
    };
  }

  /**
   * Reset custom cache metrics
   */
  resetCustomMetrics(cacheName?: string): void {
    if (cacheName) {
      this.customMetrics.delete(cacheName);
    } else {
      this.customMetrics.clear();
    }
  }

  /**
   * Collect Redis metrics
   */
  async collectRedisMetrics(): Promise<CacheMetrics> {
    try {
      const redisInfoClient = this.redis as Redis & {
        info: (section?: string) => Promise<string>;
      };
      const info = await redisInfoClient.info('stats');
      const memoryInfo = await redisInfoClient.info('memory');

      const stats = this.parseInfo(info);
      const memory = this.parseInfo(memoryInfo);

      const keyspaceHits = parseInt(stats.keyspace_hits || '0', 10);
      const keyspaceMisses = parseInt(stats.keyspace_misses || '0', 10);
      const total = keyspaceHits + keyspaceMisses;

      const memoryUsed = parseInt(memory.used_memory || '0', 10);
      const memoryMax = parseInt(memory.maxmemory || '0', 10) || memoryUsed * 2; // Default to 2x current if not set

      const metrics: CacheMetrics = {
        hits: keyspaceHits,
        misses: keyspaceMisses,
        hitRate: total > 0 ? keyspaceHits / total : 0,
        evictions: parseInt(stats.keyspace_misses || '0', 10),
        keyspaceHits,
        keyspaceMisses,
        memoryUsage: memoryUsed,
        memoryMax,
        memoryUsagePercent: memoryMax > 0 ? (memoryUsed / memoryMax) * 100 : 0,
      };

      this.metrics.set('redis', metrics);
      return metrics;
    } catch (error) {
      logger.error({ message: 'Failed to collect Redis metrics', error });
      return this.getDefaultMetrics();
    }
  }

  /**
   * Get metrics for a specific cache
   */
  getMetrics(cacheName: string): CacheMetrics | undefined {
    return this.metrics.get(cacheName);
  }

  /**
   * Get all metrics
   */
  getAllMetrics(): Map<string, CacheMetrics> {
    return new Map(this.metrics);
  }

  /**
   * Get cache health status
   */
  getHealthStatus(cacheName: string): 'healthy' | 'warning' | 'critical' {
    const metrics = this.metrics.get(cacheName);
    
    if (!metrics) {
      return 'warning';
    }

    if (metrics.hitRate < 0.5 || metrics.memoryUsagePercent > 90) {
      return 'critical';
    }

    if (metrics.hitRate < 0.7 || metrics.memoryUsagePercent > 75) {
      return 'warning';
    }

    return 'healthy';
  }

  /**
   * Get overall cache health
   */
  getOverallHealth(): {
    status: 'healthy' | 'warning' | 'critical';
    caches: Array<{ name: string; health: string }>;
  } {
    const cacheHealths = Array.from(this.metrics.keys()).map(name => ({
      name,
      health: this.getHealthStatus(name),
    }));

    const hasCritical = cacheHealths.some(c => c.health === 'critical');
    const hasWarning = cacheHealths.some(c => c.health === 'warning');

    return {
      status: hasCritical ? 'critical' : hasWarning ? 'warning' : 'healthy',
      caches: cacheHealths,
    };
  }

  /**
   * Get performance summary
   */
  getPerformanceSummary(): {
    status: string;
    redisMetrics: CacheMetrics;
    customMetrics: Map<string, { hits: number; misses: number; hitRate: number }>;
    recommendations: string[];
  } {
    const redisMetrics = this.metrics.get('redis') || this.getDefaultMetrics();
    const status = this.getHealthStatus('redis');
    const recommendations: string[] = [];

    if (redisMetrics.hitRate < 0.7) {
      recommendations.push('Consider increasing cache TTL or cache size');
    }

    if (redisMetrics.memoryUsagePercent > 75) {
      recommendations.push('Consider increasing Redis memory limit or implementing cache eviction policies');
    }

    if (redisMetrics.evictions > 1000) {
      recommendations.push('High eviction rate detected - consider optimizing cache usage');
    }

    return {
      status,
      redisMetrics,
      customMetrics: new Map(
        Array.from(this.customMetrics.entries()).map(([name, metrics]) => [
          name,
          this.getCustomMetrics(name),
        ]),
      ),
      recommendations,
    };
  }

  /**
   * Parse Redis INFO output
   */
  private parseInfo(info: string): Record<string, string> {
    const lines = info.split('\r\n');
    const result: Record<string, string> = {};

    for (const line of lines) {
      if (line.startsWith('#') || !line.includes(':')) {
        continue;
      }

      const [key, value] = line.split(':');
      if (key && value) {
        result[key.trim()] = value.trim();
      }
    }

    return result;
  }

  /**
   * Get default metrics
   */
  private getDefaultMetrics(): CacheMetrics {
    return {
      hits: 0,
      misses: 0,
      hitRate: 0,
      evictions: 0,
      keyspaceHits: 0,
      keyspaceMisses: 0,
      memoryUsage: 0,
      memoryMax: 0,
      memoryUsagePercent: 0,
    };
  }
}

export function createCacheMetricsService(redis: Redis): CacheMetricsService {
  return new CacheMetricsService(redis);
}
