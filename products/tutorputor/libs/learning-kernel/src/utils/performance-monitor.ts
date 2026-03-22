/**
 * Performance Monitoring Utilities
 * 
 * Provides lightweight performance tracking for API calls, database queries,
 * and AI operations. Part of P2 execution plan - Performance Optimization.
 * 
 * @module performance-monitor
 * @doc.layer monitoring
 * @doc.purpose Track and optimize system performance
 */

// ============================================================================
// Types and Interfaces
// ============================================================================

export interface PerformanceMetric {
  name: string;
  duration: number;
  timestamp: number;
  metadata?: Record<string, unknown>;
}

export interface QueryMetric extends PerformanceMetric {
  query: string;
  rowsAffected?: number;
}

export interface AIMetric extends PerformanceMetric {
  provider: string;
  model: string;
  tokensIn: number;
  tokensOut: number;
  cost: number;
}

export interface APIMetric extends PerformanceMetric {
  endpoint: string;
  method: string;
  statusCode: number;
  userId?: string;
}

// ============================================================================
// Metric Storage
// ============================================================================

class MetricStore {
  private metrics: PerformanceMetric[] = [];
  private maxSize: number;

  constructor(maxSize: number = 1000) {
    this.maxSize = maxSize;
  }

  add(metric: PerformanceMetric): void {
    this.metrics.push(metric);
    if (this.metrics.length > this.maxSize) {
      this.metrics.shift();
    }
  }

  getRecent(count: number = 100): PerformanceMetric[] {
    return this.metrics.slice(-count);
  }

  getAll(): PerformanceMetric[] {
    return [...this.metrics];
  }

  clear(): void {
    this.metrics = [];
  }

  // Get average duration for metrics matching a name pattern
  getAverageDuration(namePattern: string): number {
    const matching = this.metrics.filter(m => m.name.includes(namePattern));
    if (matching.length === 0) return 0;
    return matching.reduce((sum, m) => sum + m.duration, 0) / matching.length;
  }

  // Get p95 duration for metrics matching a name pattern
  getP95Duration(namePattern: string): number {
    const matching = this.metrics
      .filter(m => m.name.includes(namePattern))
      .map(m => m.duration)
      .sort((a, b) => a - b);
    
    if (matching.length === 0) return 0;
    const index = Math.floor(matching.length * 0.95);
    return matching[index] ?? 0;
  }
}

// Global metric store instance
const globalStore = new MetricStore();

// ============================================================================
// Performance Tracking Functions
// ============================================================================

/**
 * Wraps a function to track its execution time
 */
export function track<T>(
  name: string,
  fn: () => T | Promise<T>,
  metadata?: Record<string, unknown>
): Promise<T> {
  const start = performance.now();
  
  const recordMetric = (duration: number, error?: Error) => {
    globalStore.add({
      name: error ? `${name}:error` : name,
      duration,
      timestamp: Date.now(),
      metadata: { ...metadata, error: error?.message },
    });
  };

  try {
    const result = fn();
    
    if (result instanceof Promise) {
      return result
        .then(value => {
          recordMetric(performance.now() - start);
          return value;
        })
        .catch(error => {
          recordMetric(performance.now() - start, error);
          throw error;
        });
    } else {
      recordMetric(performance.now() - start);
      return Promise.resolve(result);
    }
  } catch (error) {
    recordMetric(performance.now() - start, error as Error);
    throw error;
  }
}

/**
 * Creates a timer that records duration when stopped
 */
export function createTimer(name: string, metadata?: Record<string, unknown>) {
  const start = performance.now();
  
  return {
    stop: (extraMetadata?: Record<string, unknown>) => {
      const duration = performance.now() - start;
      globalStore.add({
        name,
        duration,
        timestamp: Date.now(),
        metadata: { ...metadata, ...extraMetadata },
      });
      return duration;
    },
  };
}

// ============================================================================
// Database Query Tracking
// ============================================================================

export function trackQuery<T>(
  query: string,
  fn: () => Promise<T>,
  metadata?: Record<string, unknown>
): Promise<T> {
  const start = performance.now();
  
  return fn()
    .then(result => {
      const duration = performance.now() - start;
      globalStore.add({
        name: 'db:query',
        duration,
        timestamp: Date.now(),
        query: query.substring(0, 100), // Truncate for storage
        ...metadata,
      } as QueryMetric);
      return result;
    })
    .catch(error => {
      const duration = performance.now() - start;
      globalStore.add({
        name: 'db:query:error',
        duration,
        timestamp: Date.now(),
        query: query.substring(0, 100),
        error: error.message,
        ...metadata,
      } as QueryMetric);
      throw error;
    });
}

// ============================================================================
// AI Operation Tracking
// ============================================================================

export function trackAI(
  provider: string,
  model: string,
  tokensIn: number,
  tokensOut: number,
  duration: number,
  cost: number
): void {
  globalStore.add({
    name: 'ai:generation',
    duration,
    timestamp: Date.now(),
    provider,
    model,
    tokensIn,
    tokensOut,
    cost,
  } as AIMetric);
}

// ============================================================================
// API Endpoint Tracking
// ============================================================================

export function trackAPI(
  endpoint: string,
  method: string,
  statusCode: number,
  duration: number,
  userId?: string
): void {
  globalStore.add({
    name: `api:${method.toLowerCase()}`,
    duration,
    timestamp: Date.now(),
    endpoint,
    method,
    statusCode,
    userId,
  } as APIMetric);
}

// ============================================================================
// Analytics and Reporting
// ============================================================================

export function getPerformanceReport(): {
  db: { avg: number; p95: number };
  ai: { avg: number; p95: number };
  api: { avg: number; p95: number };
} {
  return {
    db: {
      avg: globalStore.getAverageDuration('db:'),
      p95: globalStore.getP95Duration('db:'),
    },
    ai: {
      avg: globalStore.getAverageDuration('ai:'),
      p95: globalStore.getP95Duration('ai:'),
    },
    api: {
      avg: globalStore.getAverageDuration('api:'),
      p95: globalStore.getP95Duration('api:'),
    },
  };
}

export function getSlowQueries(threshold: number = 1000): QueryMetric[] {
  return globalStore
    .getAll()
    .filter((m): m is QueryMetric => 
      m.name === 'db:query' && m.duration > threshold
    );
}

export function getMetrics(): PerformanceMetric[] {
  return globalStore.getAll();
}

export function clearMetrics(): void {
  globalStore.clear();
}

// ============================================================================
// Middleware for Express/Fastify
// ============================================================================

export function apiTrackingMiddleware(
  req: { method: string; path: string; user?: { id: string } },
  res: { statusCode: number },
  next: () => void
): void {
  const start = performance.now();
  
  const originalEnd = res.statusCode;
  
  // Track after response is sent
  process.nextTick(() => {
    const duration = performance.now() - start;
    trackAPI(
      req.path,
      req.method,
      res.statusCode || originalEnd,
      duration,
      req.user?.id
    );
  });
  
  next();
}
