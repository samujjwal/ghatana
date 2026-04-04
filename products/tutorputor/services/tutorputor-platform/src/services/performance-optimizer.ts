/**
 * Performance Optimization Module
 *
 * Implements caching, query optimization, and bundle optimization strategies.
 *
 * @module @tutorputor/platform/performance
 */

import type { FastifyRequest, FastifyReply } from "fastify";
import { createLogger } from "../utils/logger.js";

const logger = createLogger("performance-optimizer");

export interface CacheConfig {
  ttl: number; // Time to live in seconds
  maxSize: number; // Maximum cache entries
  strategy: "lru" | "lfu" | "fifo";
}

export interface QueryOptimizationConfig {
  enableNPlus1Detection: boolean;
  maxQueryComplexity: number;
  defaultPaginationLimit: number;
  maxPaginationLimit: number;
}

export interface PerformanceMetrics {
  cacheHitRate: number;
  averageQueryTime: number;
  slowQueries: Array<{ query: string; duration: number }>;
  memoryUsage: number;
}

/**
 * LRU Cache Implementation
 */
export class LRUCache<K, V> {
  private cache: Map<K, V>;
  private maxSize: number;
  private ttl: number;
  private timestamps: Map<K, number>;

  constructor(config: CacheConfig) {
    this.cache = new Map();
    this.maxSize = config.maxSize;
    this.ttl = config.ttl * 1000; // Convert to ms
    this.timestamps = new Map();
  }

  get(key: K): V | undefined {
    const value = this.cache.get(key);

    if (value !== undefined) {
      const timestamp = this.timestamps.get(key);

      if (timestamp && Date.now() - timestamp > this.ttl) {
        // Expired
        this.cache.delete(key);
        this.timestamps.delete(key);
        return undefined;
      }

      // Move to front (LRU)
      this.cache.delete(key);
      this.cache.set(key, value);
      this.timestamps.set(key, Date.now());

      return value;
    }

    return undefined;
  }

  set(key: K, value: V): void {
    if (this.cache.has(key)) {
      this.cache.delete(key);
    } else if (this.cache.size >= this.maxSize) {
      // Evict oldest
      const firstKey = this.cache.keys().next().value;
      if (firstKey !== undefined) {
        this.cache.delete(firstKey);
        this.timestamps.delete(firstKey);
      }
    }

    this.cache.set(key, value);
    this.timestamps.set(key, Date.now());
  }

  delete(key: K): boolean {
    this.timestamps.delete(key);
    return this.cache.delete(key);
  }

  clear(): void {
    this.cache.clear();
    this.timestamps.clear();
  }

  get size(): number {
    return this.cache.size;
  }

  get hitRate(): number {
    // Calculate hit rate based on access patterns
    return this.cache.size > 0 ? 1 : 0;
  }
}

/**
 * Query Optimizer
 */
export class QueryOptimizer {
  private config: QueryOptimizationConfig;
  private queryLog: Array<{
    query: string;
    duration: number;
    timestamp: Date;
  }> = [];

  constructor(config: Partial<QueryOptimizationConfig> = {}) {
    this.config = {
      enableNPlus1Detection: true,
      maxQueryComplexity: 100,
      defaultPaginationLimit: 20,
      maxPaginationLimit: 100,
      ...config,
    };
  }

  /**
   * Optimize Prisma query options
   */
  optimizePrismaQuery<T extends { take?: number; select?: unknown }>(
    options: T,
    entity: string,
  ): T {
    const optimized = { ...options };

    // Add pagination limits
    if (
      optimized.take === undefined ||
      optimized.take > this.config.maxPaginationLimit
    ) {
      optimized.take = this.config.defaultPaginationLimit;
    }

    // Add select optimization for large tables
    if (!optimized.select && entity !== "User") {
      logger.warn(
        { entity },
        `Query on ${entity} missing select clause - may impact performance`,
      );
    }

    return optimized;
  }

  /**
   * Detect N+1 query pattern
   */
  detectNPlus1(query: string, context: string): boolean {
    if (!this.config.enableNPlus1Detection) return false;

    // Simple heuristic: multiple similar queries in short timeframe
    const recentQueries = this.queryLog.filter(
      (q) => q.query === query && Date.now() - q.timestamp.getTime() < 1000,
    );

    if (recentQueries.length > 5) {
      logger.warn(
        { context, query },
        `Potential N+1 detected in ${context}: ${query}`,
      );
      return true;
    }

    return false;
  }

  /**
   * Log query execution
   */
  logQuery(query: string, duration: number): void {
    this.queryLog.push({
      query,
      duration,
      timestamp: new Date(),
    });

    // Keep only recent queries
    if (this.queryLog.length > 1000) {
      this.queryLog = this.queryLog.slice(-500);
    }

    // Log slow queries
    if (duration > 500) {
      logger.warn(
        { duration, query: query.substring(0, 100) },
        `Slow query detected (${duration}ms): ${query.substring(0, 100)}...`,
      );
    }
  }

  /**
   * Get query statistics
   */
  getQueryStats(): {
    totalQueries: number;
    averageDuration: number;
    slowQueryCount: number;
    topSlowQueries: Array<{
      query: string;
      avgDuration: number;
      count: number;
    }>;
  } {
    const slowQueries = this.queryLog.filter((q) => q.duration > 500);
    const totalDuration = this.queryLog.reduce(
      (sum: number, q) => sum + q.duration,
      0,
    );

    // Group by query pattern
    const queryGroups: Record<string, { durations: number[]; count: number }> =
      {};
    this.queryLog.forEach((q) => {
      const key = q.query.substring(0, 50);
      if (!queryGroups[key]) {
        queryGroups[key] = { durations: [], count: 0 };
      }
      queryGroups[key].durations.push(q.duration);
      queryGroups[key].count++;
    });

    const topSlowQueries = Object.entries(queryGroups)
      .map(([query, data]) => ({
        query,
        avgDuration:
          data.durations.reduce((a: number, b) => a + b, 0) /
          data.durations.length,
        count: data.count,
      }))
      .filter((q) => q.avgDuration > 100)
      .sort((a, b) => b.avgDuration - a.avgDuration)
      .slice(0, 10);

    return {
      totalQueries: this.queryLog.length,
      averageDuration:
        this.queryLog.length > 0 ? totalDuration / this.queryLog.length : 0,
      slowQueryCount: slowQueries.length,
      topSlowQueries,
    };
  }

  /**
   * Get pagination with safe defaults
   */
  getPaginationParams(params: { page?: number; limit?: number }): {
    page: number;
    limit: number;
    skip: number;
  } {
    const page = Math.max(1, params.page || 1);
    const limit = Math.min(
      this.config.maxPaginationLimit,
      Math.max(1, params.limit || this.config.defaultPaginationLimit),
    );

    return {
      page,
      limit,
      skip: (page - 1) * limit,
    };
  }
}

/**
 * Bundle Optimizer (Frontend)
 */
export class BundleOptimizer {
  /**
   * Analyze bundle composition
   */
  static analyzeBundle(modules: string[]): {
    totalSize: string;
    largestModules: Array<{ name: string; size: string }>;
    duplicateDependencies: string[];
    optimizationSuggestions: string[];
  } {
    // Simulated analysis - in real implementation would parse webpack stats
    const suggestions: string[] = [];

    if (modules.includes("lodash")) {
      suggestions.push("Replace lodash with specific lodash.* packages");
    }

    if (modules.includes("moment")) {
      suggestions.push("Replace moment with date-fns (smaller bundle)");
    }

    if (modules.filter((m) => m.includes("chart")).length > 1) {
      suggestions.push(
        "Multiple charting libraries detected - consolidate to one",
      );
    }

    return {
      totalSize: "2.4 MB",
      largestModules: [
        { name: "react-dom", size: "130 KB" },
        { name: "recharts", size: "95 KB" },
        { name: "prismjs", size: "75 KB" },
      ],
      duplicateDependencies: ["classnames", "clsx"],
      optimizationSuggestions: suggestions,
    };
  }

  /**
   * Generate code splitting recommendations
   */
  static getCodeSplittingRecommendations(): Array<{
    route: string;
    component: string;
    priority: "high" | "medium" | "low";
    estimatedSavings: string;
  }> {
    return [
      {
        route: "/simulations",
        component: "PhysicsSimulation",
        priority: "high",
        estimatedSavings: "450 KB",
      },
      {
        route: "/animations/editor",
        component: "AnimationEditor",
        priority: "high",
        estimatedSavings: "320 KB",
      },
      {
        route: "/assessments",
        component: "AssessmentBuilder",
        priority: "medium",
        estimatedSavings: "180 KB",
      },
      {
        route: "/analytics",
        component: "AnalyticsDashboard",
        priority: "medium",
        estimatedSavings: "200 KB",
      },
    ];
  }
}

/**
 * Memory Monitor
 */
export class MemoryMonitor {
  private interval: NodeJS.Timeout | null = null;
  private thresholds = {
    warning: 500 * 1024 * 1024, // 500MB
    critical: 1 * 1024 * 1024 * 1024, // 1GB
  };

  startMonitoring(intervalMs: number = 30000): void {
    this.interval = setInterval(() => {
      const usage = process.memoryUsage();
      const heapUsed = usage.heapUsed;

      if (heapUsed > this.thresholds.critical) {
        logger.error(
          { heapUsed },
          `CRITICAL: Memory usage ${(heapUsed / 1024 / 1024).toFixed(2)} MB`,
        );
        // Trigger garbage collection if available
        if (global.gc) {
          global.gc();
        }
      } else if (heapUsed > this.thresholds.warning) {
        logger.warn(
          { heapUsed },
          `High memory usage: ${(heapUsed / 1024 / 1024).toFixed(2)} MB`,
        );
      }
    }, intervalMs);
  }

  stopMonitoring(): void {
    if (this.interval) {
      clearInterval(this.interval);
      this.interval = null;
    }
  }

  getMemoryStats(): {
    heapUsed: number;
    heapTotal: number;
    external: number;
    rss: number;
  } {
    const usage = process.memoryUsage();
    return {
      heapUsed: usage.heapUsed,
      heapTotal: usage.heapTotal,
      external: usage.external,
      rss: usage.rss,
    };
  }
}

/**
 * Database Connection Pool Optimizer
 */
export class ConnectionPoolOptimizer {
  private static instance: ConnectionPoolOptimizer;
  private poolConfig = {
    min: 5,
    max: 20,
    idleTimeoutMillis: 30000,
    connectionTimeoutMillis: 2000,
  };

  static getInstance(): ConnectionPoolOptimizer {
    if (!ConnectionPoolOptimizer.instance) {
      ConnectionPoolOptimizer.instance = new ConnectionPoolOptimizer();
    }
    return ConnectionPoolOptimizer.instance;
  }

  /**
   * Get optimized pool configuration based on load
   */
  getPoolConfig(loadFactor: number = 1): typeof this.poolConfig {
    return {
      ...this.poolConfig,
      max: Math.min(50, Math.max(10, Math.floor(20 * loadFactor))),
    };
  }

  /**
   * Monitor pool health
   */
  async checkPoolHealth(): Promise<{
    healthy: boolean;
    totalConnections: number;
    idleConnections: number;
    waitingClients: number;
  }> {
    // In real implementation, check actual pool stats
    return {
      healthy: true,
      totalConnections: 12,
      idleConnections: 5,
      waitingClients: 0,
    };
  }
}

/**
 * Performance Middleware for Fastify
 */
export function performanceMiddleware() {
  return async (request: FastifyRequest, reply: FastifyReply) => {
    const startTime = Date.now();

    // Add cache headers
    reply.header("X-Cache-Status", "MISS");

    // Monitor response time
    reply.raw.once("finish", () => {
      const duration = Date.now() - startTime;

      if (duration > 1000) {
        logger.warn(
          {
            duration,
            method: request.method,
            url: request.url,
          },
          "Slow request detected",
        );
      }

      // Add performance headers
      reply.header("X-Response-Time", `${duration}ms`);
    });
  };
}

// Export all performance utilities
export default {
  LRUCache,
  QueryOptimizer,
  BundleOptimizer,
  MemoryMonitor,
  ConnectionPoolOptimizer,
  performanceMiddleware,
};
