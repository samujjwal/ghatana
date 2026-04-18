/**
 * Cache Invalidation Hooks
 *
 * Provides hooks to invalidate caches when data changes.
 * Integrates with Prisma middleware for automatic cache invalidation.
 *
 * @doc.type middleware
 * @doc.purpose Automatic cache invalidation on data changes
 * @doc.layer platform
 * @doc.pattern Middleware
 */

import { Prisma } from '@tutorputor/core/db';
import type Redis from 'ioredis';
import { createStandaloneLogger } from '@tutorputor/core/logger';

const logger = createStandaloneLogger({ component: 'CacheInvalidationMiddleware' });

export class CacheInvalidationMiddleware {
  private redis: Redis;

  constructor(redis: Redis) {
    this.redis = redis;
  }

  /**
   * Create Prisma middleware for cache invalidation
   */
  createPrismaMiddleware() {
    return Prisma.defineExtension((prisma) => {
      return prisma.$use(async (params, next) => {
        const result = await next(params);

        // Invalidate caches based on the operation
        await this.invalidateBasedOnOperation(params, result);

        return result;
      });
    });
  }

  /**
   * Invalidate caches based on Prisma operation
   */
  private async invalidateBasedOnOperation(params: Prisma.MiddlewareParams, result: unknown): Promise<void> {
    const { model, action, args } = params;

    try {
      switch (model) {
        case 'Module':
          await this.invalidateModuleCache(action, args, result);
          break;
        case 'Enrollment':
          await this.invalidateEnrollmentCache(action, args);
          break;
        case 'Assessment':
          await this.invalidateAssessmentCache(action, args, result);
          break;
        case 'AssessmentAttempt':
          await this.invalidateAssessmentAttemptCache(action, args);
          break;
        default:
          // No cache invalidation for other models
          break;
      }
    } catch (error) {
      logger.error({ message: 'Cache invalidation error', model, action, error });
    }
  }

  /**
   * Invalidate module cache
   */
  private async invalidateModuleCache(action: string, args: Prisma.MiddlewareParams['args'], result: unknown): Promise<void> {
    const tenantId = this.extractTenantId(args, result);

    if (!tenantId) {
      return;
    }

    if (action === 'update' || action === 'delete') {
      const moduleId = this.extractModuleId(args, result);
      if (moduleId) {
        await this.redis.del(`module:${tenantId}:${moduleId}`);
        await this.invalidateModuleLists(tenantId);
        logger.debug({ message: 'Module cache invalidated', tenantId, moduleId, action });
      }
    }

    if (action === 'create' || action === 'update' || action === 'delete') {
      await this.invalidateModuleLists(tenantId);
    }
  }

  /**
   * Invalidate enrollment cache
   */
  private async invalidateEnrollmentCache(action: string, args: Prisma.MiddlewareParams['args']): Promise<void> {
    const tenantId = this.extractTenantId(args);

    if (!tenantId) {
      return;
    }

    if (action === 'create' || action === 'update' || action === 'delete') {
      await this.invalidateModuleLists(tenantId);
      logger.debug({ message: 'Enrollment change - module lists invalidated', tenantId, action });
    }
  }

  /**
   * Invalidate assessment cache
   */
  private async invalidateAssessmentCache(action: string, args: Prisma.MiddlewareParams['args'], result: unknown): Promise<void> {
    const tenantId = this.extractTenantId(args, result);

    if (!tenantId) {
      return;
    }

    if (action === 'update' || action === 'delete') {
      const assessmentId = this.extractAssessmentId(args, result);
      if (assessmentId) {
        await this.redis.del(`assessment:${tenantId}:${assessmentId}`);
        logger.debug({ message: 'Assessment cache invalidated', tenantId, assessmentId, action });
      }
    }
  }

  /**
   * Invalidate assessment attempt cache
   */
  private async invalidateAssessmentAttemptCache(action: string, args: Prisma.MiddlewareParams['args']): Promise<void> {
    const tenantId = this.extractTenantId(args);

    if (!tenantId) {
      return;
    }

    if (action === 'create' || action === 'update') {
      const assessmentId = args?.data?.assessmentId;
      if (assessmentId) {
        await this.redis.del(`assessment:${tenantId}:${assessmentId}`);
        logger.debug({ message: 'Assessment attempt - assessment cache invalidated', tenantId, assessmentId });
      }
    }
  }

  /**
   * Invalidate all module lists for a tenant
   */
  private async invalidateModuleLists(tenantId: string): Promise<void> {
    try {
      const pattern = `module:list:${tenantId}:*`;
      const keys = await this.redis.keys(pattern);
      
      if (keys.length > 0) {
        for (const key of keys) {
          await this.redis.del(key);
        }
      }
    } catch (error) {
      logger.error({ message: 'Failed to invalidate module lists', tenantId, error });
    }
  }

  /**
   * Extract tenant ID from args or result
   */
  private extractTenantId(args: Prisma.MiddlewareParams['args'], result?: unknown): string | null {
    // Try to get from args.data
    if (args?.data?.tenantId) {
      return args.data.tenantId as string;
    }

    // Try to get from args.where
    if (args?.where?.tenantId) {
      return args.where.tenantId as string;
    }

    // Try to get from result
    if (result && typeof result === 'object' && 'tenantId' in result) {
      return (result as { tenantId: string }).tenantId;
    }

    return null;
  }

  /**
   * Extract module ID from args or result
   */
  private extractModuleId(args: Prisma.MiddlewareParams['args'], result?: unknown): string | null {
    // Try to get from args.where
    if (args?.where?.id) {
      return args.where.id as string;
    }

    // Try to get from result
    if (result && typeof result === 'object' && 'id' in result) {
      return (result as { id: string }).id;
    }

    return null;
  }

  /**
   * Extract assessment ID from args or result
   */
  private extractAssessmentId(args: Prisma.MiddlewareParams['args'], result?: unknown): string | null {
    // Try to get from args.where
    if (args?.where?.id) {
      return args.where.id as string;
    }

    // Try to get from args.data
    if (args?.data?.assessmentId) {
      return args.data.assessmentId as string;
    }

    // Try to get from result
    if (result && typeof result === 'object' && 'id' in result) {
      return (result as { id: string }).id;
    }

    return null;
  }
}

/**
 * Create cache invalidation middleware for Fastify
 */
export function createCacheInvalidationMiddleware(redis: Redis) {
  const middleware = new CacheInvalidationMiddleware(redis);
  return middleware.createPrismaMiddleware();
}
