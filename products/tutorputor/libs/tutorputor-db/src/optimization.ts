/**
 * Database Query Optimization Configuration
 * Part of Execution Plan item #11: Performance Optimization
 * 
 * Provides Prisma query optimization strategies including
 * connection pooling, query caching, and performance monitoring.
 */

import { PrismaClient } from '@prisma/client';
import { createPrismaRedisCache } from 'prisma-redis-cache';
import Redis from 'ioredis';

/**
 * Optimized Prisma client configuration
 */
export function createOptimizedPrismaClient(): PrismaClient {
  const prisma = new PrismaClient({
    log: process.env.NODE_ENV === 'development' 
      ? ['query', 'info', 'warn', 'error']
      : ['error'],
    
    // Connection pool optimization
    datasources: {
      db: {
        url: process.env.DATABASE_URL,
      },
    },
  });

  // Apply query logging for performance monitoring
  prisma.$use(async (params, next) => {
    const start = Date.now();
    const result = await next(params);
    const duration = Date.now() - start;

    // Log slow queries (> 100ms)
    if (duration > 100) {
      console.warn(`[SLOW QUERY] ${params.model}.${params.action} took ${duration}ms`);
    }

    return result;
  });

  return prisma;
}

/**
 * Redis-backed query cache for Prisma
 */
export function createPrismaCache(prisma: PrismaClient, redis: Redis) {
  return createPrismaRedisCache({
    models: [
      { model: 'Simulation', ttl: 300 }, // 5 minutes
      { model: 'Animation', ttl: 300 },
      { model: 'Assessment', ttl: 600 }, // 10 minutes
      { model: 'Content', ttl: 600 },
      { model: 'User', ttl: 60 }, // 1 minute
    ],
    redis,
    onHit: (key) => {
      console.log(`[CACHE HIT] ${key}`);
    },
    onMiss: (key) => {
      console.log(`[CACHE MISS] ${key}`);
    },
  });
}

/**
 * Query optimization strategies
 */
export const queryOptimization = {
  /**
   * Select only required fields
   */
  selectMinimal: <T extends Record<string, any>>(fields: (keyof T)[]) => {
    const select: Record<string, boolean> = {};
    fields.forEach((field) => {
      select[field as string] = true;
    });
    return { select };
  },

  /**
   * Efficient pagination with cursor-based pagination
   */
  cursorPagination: (cursor?: string, limit: number = 20) => ({
    take: limit,
    skip: cursor ? 1 : 0,
    cursor: cursor ? { id: cursor } : undefined,
    orderBy: { createdAt: 'desc' },
  }),

  /**
   * Include related data efficiently
   */
  efficientInclude: (relations: string[]) => {
    const include: Record<string, boolean | object> = {};
    relations.forEach((relation) => {
      include[relation] = {
        select: {
          id: true,
          name: true,
        },
      };
    });
    return { include };
  },

  /**
   * Batch queries for N+1 prevention
   */
  batchQuery: async <T, R>(
    items: T[],
    batchSize: number,
    queryFn: (batch: T[]) => Promise<R>
  ): Promise<R[]> => {
    const results: R[] = [];
    for (let i = 0; i < items.length; i += batchSize) {
      const batch = items.slice(i, i + batchSize);
      const result = await queryFn(batch);
      results.push(result);
    }
    return results;
  },
};

/**
 * Database performance monitoring
 */
export class QueryPerformanceMonitor {
  private queryTimes: Map<string, number[]> = new Map();
  private slowQueryThreshold = 100; // ms

  recordQuery(query: string, duration: number) {
    if (!this.queryTimes.has(query)) {
      this.queryTimes.set(query, []);
    }
    this.queryTimes.get(query)!.push(duration);

    if (duration > this.slowQueryThreshold) {
      console.warn(`[SLOW QUERY] ${query}: ${duration}ms`);
    }
  }

  getStats() {
    const stats: Record<string, { avg: number; max: number; count: number }> = {};
    
    this.queryTimes.forEach((times, query) => {
      const avg = times.reduce((a, b) => a + b, 0) / times.length;
      const max = Math.max(...times);
      stats[query] = { avg: Math.round(avg), max, count: times.length };
    });

    return stats;
  }

  getSlowQueries() {
    const slow: Array<{ query: string; avg: number; max: number }> = [];
    
    this.queryTimes.forEach((times, query) => {
      const avg = times.reduce((a, b) => a + b, 0) / times.length;
      const max = Math.max(...times);
      
      if (avg > this.slowQueryThreshold || max > this.slowQueryThreshold * 2) {
        slow.push({ query, avg: Math.round(avg), max });
      }
    });

    return slow.sort((a, b) => b.avg - a.avg);
  }
}

/**
 * Index optimization recommendations
 */
export const indexRecommendations = {
  // High-frequency query indexes
  requiredIndexes: [
    { table: 'Simulation', columns: ['domain', 'difficulty'], reason: 'Filter by domain and difficulty' },
    { table: 'Simulation', columns: ['createdAt'], reason: 'Sort by creation date' },
    { table: 'Animation', columns: ['domain', 'tags'], reason: 'Filter and search by tags' },
    { table: 'Assessment', columns: ['userId', 'status'], reason: 'User assessment queries' },
    { table: 'Content', columns: ['type', 'published'], reason: 'Content listing' },
    { table: 'LearningPath', columns: ['userId', 'progress'], reason: 'User progress tracking' },
  ],

  // Composite indexes for complex queries
  compositeIndexes: [
    { table: 'Simulation', columns: ['domain', 'difficulty', 'createdAt'], reason: 'Combined filter and sort' },
    { table: 'Content', columns: ['type', 'domain', 'published'], reason: 'Content explorer filters' },
  ],
};

/**
 * Query builder with optimization hints
 */
export class OptimizedQueryBuilder {
  constructor(private prisma: PrismaClient) {}

  async findSimulations(filters: {
    domain?: string;
    difficulty?: string;
    tags?: string[];
    cursor?: string;
    limit?: number;
  }) {
    return this.prisma.simulation.findMany({
      ...queryOptimization.cursorPagination(filters.cursor, filters.limit),
      where: {
        ...(filters.domain && { domain: filters.domain }),
        ...(filters.difficulty && { difficulty: filters.difficulty }),
        ...(filters.tags && { tags: { hasSome: filters.tags } }),
      },
      select: {
        id: true,
        name: true,
        description: true,
        domain: true,
        difficulty: true,
        duration: true,
        thumbnail: true,
        _count: {
          select: { runs: true },
        },
      },
    });
  }

  async findAnimations(filters: {
    domain?: string;
    style?: string;
    cursor?: string;
    limit?: number;
  }) {
    return this.prisma.animation.findMany({
      ...queryOptimization.cursorPagination(filters.cursor, filters.limit),
      where: {
        ...(filters.domain && { domain: filters.domain }),
        ...(filters.style && { style: filters.style }),
      },
      select: {
        id: true,
        name: true,
        description: true,
        domain: true,
        style: true,
        duration: true,
        previewUrl: true,
      },
    });
  }

  async getUserLearningPath(userId: string) {
    return this.prisma.learningPath.findUnique({
      where: { userId },
      include: {
        modules: {
          orderBy: { order: 'asc' },
          include: {
            content: {
              select: {
                id: true,
                title: true,
                type: true,
                duration: true,
              },
            },
          },
        },
        progress: {
          select: {
            completedModules: true,
            totalModules: true,
            percentage: true,
          },
        },
      },
    });
  }
}

export default {
  createOptimizedPrismaClient,
  createPrismaCache,
  queryOptimization,
  QueryPerformanceMonitor,
  indexRecommendations,
  OptimizedQueryBuilder,
};
