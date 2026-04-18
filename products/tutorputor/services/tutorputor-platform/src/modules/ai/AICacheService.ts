/**
 * AI Cache Service
 *
 * Caches AI responses to reduce latency and costs.
 * Uses Redis for distributed caching with TTL-based expiration.
 *
 * @doc.type class
 * @doc.purpose AI response caching for performance optimization
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';
import type Redis from 'ioredis';

const logger = createStandaloneLogger({ component: 'AICacheService' });

interface CacheEntry<T> {
  data: T;
  timestamp: number;
  hits: number;
}

export class AICacheService<T> {
  private redis: Redis;
  private defaultTTL: number;
  private totalHits: number = 0;
  private totalMisses: number = 0;

  constructor(redis: Redis, defaultTTL = 3600000) {
    this.redis = redis;
    this.defaultTTL = defaultTTL;
  }

  /**
   * Generate cache key from parameters
   */
  private generateKey(prefix: string, params: Record<string, unknown>): string {
    const sorted = Object.entries(params).sort(([a], [b]) => a.localeCompare(b));
    const paramString = sorted.map(([k, v]) => `${k}=${JSON.stringify(v)}`).join('&');
    return `ai:${prefix}:${paramString}`;
  }

  /**
   * Get cached value
   */
  async get(prefix: string, params: Record<string, unknown>): Promise<T | null> {
    const key = this.generateKey(prefix, params);
    
    try {
      const cached = await this.redis.get(key);
      
      if (!cached) {
        this.totalMisses++;
        return null;
      }

      const entry: CacheEntry<T> = JSON.parse(cached);
      const now = Date.now();
      
      if (now - entry.timestamp > this.defaultTTL) {
        await this.redis.del(key);
        this.totalMisses++;
        return null;
      }

      entry.hits++;
      this.totalHits++;
      
      // Update hit count in Redis
      await this.redis.set(key, JSON.stringify(entry), 'EX', Math.floor((this.defaultTTL - (now - entry.timestamp)) / 1000));
      
      logger.debug({
        message: 'AI cache hit',
        key,
        hits: entry.hits,
      });

      return entry.data;
    } catch (error) {
      logger.error({ message: 'AI cache get error', key, error });
      this.totalMisses++;
      return null;
    }
  }

  /**
   * Set cached value
   */
  async set(prefix: string, params: Record<string, unknown>, data: T, ttl?: number): Promise<void> {
    const key = this.generateKey(prefix, params);
    const entry: CacheEntry<T> = {
      data,
      timestamp: Date.now(),
      hits: 0,
    };

    try {
      const ttlSeconds = Math.floor((ttl || this.defaultTTL) / 1000);
      await this.redis.set(key, JSON.stringify(entry), 'EX', ttlSeconds);

      logger.debug({
        message: 'AI cache set',
        key,
        ttl: ttl || this.defaultTTL,
      });
    } catch (error) {
      logger.error({ message: 'AI cache set error', key, error });
    }
  }

  /**
   * Invalidate cache entry
   */
  async invalidate(prefix: string, params: Record<string, unknown>): Promise<void> {
    const key = this.generateKey(prefix, params);
    
    try {
      await this.redis.del(key);
      logger.debug({ message: 'AI cache invalidated', key });
    } catch (error) {
      logger.error({ message: 'AI cache invalidate error', key, error });
    }
  }

  /**
   * Get cache statistics
   */
  getStats() {
    return {
      totalHits: this.totalHits,
      totalMisses: this.totalMisses,
      hitRate: this.totalHits + this.totalMisses > 0 
        ? this.totalHits / (this.totalHits + this.totalMisses) 
        : 0,
    };
  }

  /**
   * Clear all cache entries for a prefix
   */
  async clearPrefix(prefix: string): Promise<void> {
    try {
      const pattern = `ai:${prefix}:*`;
      const keys = await this.redis.keys(pattern);
      
      if (keys.length > 0) {
        await this.redis.del(...keys);
        logger.debug({ message: 'AI cache prefix cleared', prefix, count: keys.length });
      }
    } catch (error) {
      logger.error({ message: 'AI cache clear prefix error', prefix, error });
    }
  }
}
