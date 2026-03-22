/**
 * Database Query Optimization Configuration
 * Part of Execution Plan item #11: Performance Optimization
 *
 * Provides Prisma query optimization strategies including
 * connection pooling, query caching, and performance monitoring.
 */

import { PrismaClient } from "../../generated/prisma/index.js";
import createPrismaRedisCache from "prisma-redis-cache";
import Redis from "ioredis";

/**
 * Optimized Prisma client configuration
 */
export function createOptimizedPrismaClient(): PrismaClient {
  const prisma = new PrismaClient({
    log:
      process.env.NODE_ENV === "development"
        ? ["query", "info", "warn", "error"]
        : ["error"],
  });

  // Note: $use middleware is deprecated in Prisma 5+
  // Use Prisma Client Extensions instead for query logging
  // See: https://www.prisma.io/docs/concepts/components/prisma-client/client-extensions

  return prisma;
}

/**
 * Redis-backed query cache for Prisma
 * TODO: Update to match current prisma-redis-cache API
 */
export function createPrismaCache(prisma: PrismaClient, redis: Redis) {
  // Temporarily disabled - prisma-redis-cache API has changed
  // Need to investigate proper configuration for current version
  console.warn("Prisma cache not configured - using direct queries");
  return null;
  
  // return createPrismaRedisCache({
  //   models: [
  //     // Note: Only cache models that exist in the Prisma schema
  //     { model: "Assessment", ttl: 600 }, // 10 minutes
  //     { model: "Content", ttl: 600 },
  //     { model: "User", ttl: 60 }, // 1 minute
  //   ],
  //   redis,
  //   onHit: (key: string) => {
  //     console.log(`[CACHE HIT] ${key}`);
  //   },
  //   onMiss: (key: string) => {
  //     console.log(`[CACHE MISS] ${key}`);
  //   },
  // });
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
    orderBy: { createdAt: "desc" },
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
    queryFn: (batch: T[]) => Promise<R>,
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
    const stats: Record<string, { avg: number; max: number; count: number }> =
      {};

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
    {
      table: "Simulation",
      columns: ["domain", "difficulty"],
      reason: "Filter by domain and difficulty",
    },
    {
      table: "Simulation",
      columns: ["createdAt"],
      reason: "Sort by creation date",
    },
    {
      table: "Animation",
      columns: ["domain", "tags"],
      reason: "Filter and search by tags",
    },
    {
      table: "Assessment",
      columns: ["userId", "status"],
      reason: "User assessment queries",
    },
    {
      table: "Content",
      columns: ["type", "published"],
      reason: "Content listing",
    },
    {
      table: "LearningPath",
      columns: ["userId", "progress"],
      reason: "User progress tracking",
    },
  ],

  // Composite indexes for complex queries
  compositeIndexes: [
    {
      table: "Simulation",
      columns: ["domain", "difficulty", "createdAt"],
      reason: "Combined filter and sort",
    },
    {
      table: "Content",
      columns: ["type", "domain", "published"],
      reason: "Content explorer filters",
    },
  ],
};

/**
 * Query builder with optimization hints
 */
export class OptimizedQueryBuilder {
  constructor(private prisma: PrismaClient) {}

  // TODO: Re-enable when Simulation model is added to Prisma schema
  // async findSimulations(filters: {
  //   domain?: string;
  //   difficulty?: string;
  //   tags?: string[];
  //   cursor?: string;
  //   limit?: number;
  // }) {
  //   return this.prisma.simulation.findMany({
  //     ...queryOptimization.cursorPagination(filters.cursor, filters.limit),
  //     where: {
  //       ...(filters.domain && { domain: filters.domain }),
  //       ...(filters.difficulty && { difficulty: filters.difficulty }),
  //       ...(filters.tags && { tags: { hasSome: filters.tags } }),
  //     },
  //     select: {
  //       id: true,
  //       name: true,
  //       description: true,
  //       domain: true,
  //       difficulty: true,
  //       duration: true,
  //       thumbnail: true,
  //       _count: {
  //         select: { runs: true },
  //       },
  //     },
  //   });
  // }

  // TODO: Re-enable when Animation model is added to Prisma schema
  // async findAnimations(filters: {
  //   domain?: string;
  //   style?: string;
  //   cursor?: string;
  //   limit?: number;
  // }) {
  //   return this.prisma.animation.findMany({
  //     ...queryOptimization.cursorPagination(filters.cursor, filters.limit),
  //     where: {
  //       ...(filters.domain && { domain: filters.domain }),
  //       ...(filters.style && { style: filters.style }),
  //     },
  //     select: {
  //       id: true,
  //       name: true,
  //       description: true,
  //       domain: true,
  //       style: true,
  //       duration: true,
  //       previewUrl: true,
  //     },
  //   });
  // }

  async getUserLearningPath(userId: string) {
    // Use proper unique constraint (id) instead of userId
    return this.prisma.learningPath.findFirst({
      where: { userId },
      include: {
        // TODO: Update include to match actual Prisma schema relations
        // modules: {
        //   orderBy: { order: "asc" },
        //   include: {
        //     content: {
        //       select: {
        //         id: true,
        //         title: true,
        //         type: true,
        //         duration: true,
        //       },
        //     },
        //   },
        // },
        // progress: {
        //   select: {
        //     completedModules: true,
        //     totalModules: true,
        //     percentage: true,
        //   },
        // },
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
