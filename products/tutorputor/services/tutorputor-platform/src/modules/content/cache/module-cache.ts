/**
 * Module Data Cache Service
 *
 * Caches module data (modules, assessments, learning objectives) to reduce database load.
 * Uses Redis for distributed caching with intelligent invalidation.
 *
 * @doc.type service
 * @doc.purpose Module data caching for performance optimization
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from '@tutorputor/core/logger';
import type Redis from 'ioredis';

const logger = createStandaloneLogger({ component: 'ModuleCacheService' });

interface ModuleCacheEntry {
  data: unknown;
  timestamp: number;
  version: number;
}

export class ModuleCacheService {
  private redis: Redis;
  private defaultTTL: number;

  constructor(redis: Redis, defaultTTL = 1800) { // 30 minutes default
    this.redis = redis;
    this.defaultTTL = defaultTTL;
  }

  /**
   * Generate cache key for module
   */
  private generateModuleKey(tenantId: string, moduleId: string): string {
    return `module:${tenantId}:${moduleId}`;
  }

  /**
   * Generate cache key for module list
   */
  private generateModuleListKey(tenantId: string, filters: Record<string, unknown> = {}): string {
    const filterString = Object.entries(filters)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([k, v]) => `${k}=${JSON.stringify(v)}`)
      .join('&');
    return `module:list:${tenantId}:${filterString}`;
  }

  /**
   * Generate cache key for assessment
   */
  private generateAssessmentKey(tenantId: string, assessmentId: string): string {
    return `assessment:${tenantId}:${assessmentId}`;
  }

  /**
   * Get cached module
   */
  async getModule(tenantId: string, moduleId: string): Promise<unknown | null> {
    const key = this.generateModuleKey(tenantId, moduleId);
    
    try {
      const cached = await this.redis.get(key);
      
      if (!cached) {
        return null;
      }

      const entry: ModuleCacheEntry = JSON.parse(cached);
      return entry.data;
    } catch (error) {
      logger.error({ message: 'Module cache get error', key, error });
      return null;
    }
  }

  /**
   * Set cached module
   */
  async setModule(tenantId: string, moduleId: string, data: unknown, version: number = 1, ttl?: number): Promise<void> {
    const key = this.generateModuleKey(tenantId, moduleId);
    const entry: ModuleCacheEntry = {
      data,
      timestamp: Date.now(),
      version,
    };

    try {
      const ttlSeconds = ttl || this.defaultTTL;
      await this.redis.set(key, JSON.stringify(entry), 'EX', ttlSeconds);
      
      logger.debug({ message: 'Module cached', key, version });
    } catch (error) {
      logger.error({ message: 'Module cache set error', key, error });
    }
  }

  /**
   * Get cached module list
   */
  async getModuleList(tenantId: string, filters: Record<string, unknown> = {}): Promise<unknown[] | null> {
    const key = this.generateModuleListKey(tenantId, filters);
    
    try {
      const cached = await this.redis.get(key);
      
      if (!cached) {
        return null;
      }

      const entry: ModuleCacheEntry = JSON.parse(cached);
      return entry.data as unknown[];
    } catch (error) {
      logger.error({ message: 'Module list cache get error', key, error });
      return null;
    }
  }

  /**
   * Set cached module list
   */
  async setModuleList(tenantId: string, filters: Record<string, unknown>, data: unknown[], ttl?: number): Promise<void> {
    const key = this.generateModuleListKey(tenantId, filters);
    const entry: ModuleCacheEntry = {
      data,
      timestamp: Date.now(),
      version: 1,
    };

    try {
      const ttlSeconds = ttl || this.defaultTTL;
      await this.redis.set(key, JSON.stringify(entry), 'EX', ttlSeconds);
      
      logger.debug({ message: 'Module list cached', key, count: data.length });
    } catch (error) {
      logger.error({ message: 'Module list cache set error', key, error });
    }
  }

  /**
   * Get cached assessment
   */
  async getAssessment(tenantId: string, assessmentId: string): Promise<unknown | null> {
    const key = this.generateAssessmentKey(tenantId, assessmentId);
    
    try {
      const cached = await this.redis.get(key);
      
      if (!cached) {
        return null;
      }

      const entry: ModuleCacheEntry = JSON.parse(cached);
      return entry.data;
    } catch (error) {
      logger.error({ message: 'Assessment cache get error', key, error });
      return null;
    }
  }

  /**
   * Set cached assessment
   */
  async setAssessment(tenantId: string, assessmentId: string, data: unknown, version: number = 1, ttl?: number): Promise<void> {
    const key = this.generateAssessmentKey(tenantId, assessmentId);
    const entry: ModuleCacheEntry = {
      data,
      timestamp: Date.now(),
      version,
    };

    try {
      const ttlSeconds = ttl || this.defaultTTL;
      await this.redis.set(key, JSON.stringify(entry), 'EX', ttlSeconds);
      
      logger.debug({ message: 'Assessment cached', key, version });
    } catch (error) {
      logger.error({ message: 'Assessment cache set error', key, error });
    }
  }

  /**
   * Invalidate module cache
   */
  async invalidateModule(tenantId: string, moduleId: string): Promise<void> {
    const key = this.generateModuleKey(tenantId, moduleId);
    
    try {
      await this.redis.del(key);
      logger.debug({ message: 'Module cache invalidated', key });
    } catch (error) {
      logger.error({ message: 'Module cache invalidate error', key, error });
    }
  }

  /**
   * Invalidate all module list caches for a tenant
   */
  async invalidateModuleLists(tenantId: string): Promise<void> {
    try {
      const pattern = `module:list:${tenantId}:*`;
      const keys = await this.redis.keys(pattern);
      
      if (keys.length > 0) {
        for (const key of keys) {
          await this.redis.del(key);
        }
        logger.debug({ message: 'Module list caches invalidated', tenantId, count: keys.length });
      }
    } catch (error) {
      logger.error({ message: 'Module list cache invalidate error', tenantId, error });
    }
  }

  /**
   * Invalidate assessment cache
   */
  async invalidateAssessment(tenantId: string, assessmentId: string): Promise<void> {
    const key = this.generateAssessmentKey(tenantId, assessmentId);
    
    try {
      await this.redis.del(key);
      logger.debug({ message: 'Assessment cache invalidated', key });
    } catch (error) {
      logger.error({ message: 'Assessment cache invalidate error', key, error });
    }
  }

  /**
   * Warm cache with module data
   */
  async warmCache(tenantId: string, moduleIds: string[], dataFetcher: (moduleId: string) => Promise<unknown>): Promise<void> {
    const promises = moduleIds.map(async (moduleId) => {
      try {
        const data = await dataFetcher(moduleId);
        await this.setModule(tenantId, moduleId, data);
      } catch (error) {
        logger.error({ message: 'Cache warm error', tenantId, moduleId, error });
      }
    });

    await Promise.all(promises);
    logger.info({ message: 'Cache warmed', tenantId, count: moduleIds.length });
  }
}
