/**
 * Memory Manager
 * 
 * Manages memory usage with:
 * - Memory profiling
 * - Leak detection
 * - Automatic cleanup
 * - Resource pooling
 */

/**
 * Memory usage snapshot
 */
export interface MemorySnapshot {
  timestamp: number;
  heapUsed: number;
  heapTotal: number;
  external: number;
  arrayBuffers: number;
}

/**
 * Memory leak detection result
 */
export interface LeakDetectionResult {
  detected: boolean;
  growthRate: number;
  suspiciousObjects: Array<{
    type: string;
    count: number;
    size: number;
  }>;
}

/**
 * Memory manager for optimization and leak detection
 */
export class MemoryManager {
  private snapshots: MemorySnapshot[] = [];
  private readonly maxSnapshots = 100;
  private pools: Map<string, unknown[]> = new Map();

  /**
   * Take memory snapshot
   */
  takeSnapshot(): MemorySnapshot {
    const snapshot: MemorySnapshot = {
      timestamp: Date.now(),
      heapUsed: (performance as any).memory?.usedJSHeapSize || 0,
      heapTotal: (performance as any).memory?.totalJSHeapSize || 0,
      external: 0,
      arrayBuffers: 0,
    };

    this.snapshots.push(snapshot);

    // Keep only recent snapshots
    if (this.snapshots.length > this.maxSnapshots) {
      this.snapshots.shift();
    }

    return snapshot;
  }

  /**
   * Get memory usage statistics
   */
  getStats(): {
    current: MemorySnapshot;
    average: number;
    peak: number;
    trend: 'increasing' | 'decreasing' | 'stable';
  } {
    const current = this.takeSnapshot();
    
    if (this.snapshots.length < 2) {
      return {
        current,
        average: current.heapUsed,
        peak: current.heapUsed,
        trend: 'stable',
      };
    }

    const heapValues = this.snapshots.map(s => s.heapUsed);
    const average = heapValues.reduce((sum, v) => sum + v, 0) / heapValues.length;
    const peak = Math.max(...heapValues);

    // Calculate trend
    const recentSnapshots = this.snapshots.slice(-10);
    const firstHalf = recentSnapshots.slice(0, 5).map(s => s.heapUsed);
    const secondHalf = recentSnapshots.slice(5).map(s => s.heapUsed);
    const firstAvg = firstHalf.reduce((sum, v) => sum + v, 0) / firstHalf.length;
    const secondAvg = secondHalf.reduce((sum, v) => sum + v, 0) / secondHalf.length;
    
    const growthRate = (secondAvg - firstAvg) / firstAvg;
    let trend: 'increasing' | 'decreasing' | 'stable' = 'stable';
    
    if (growthRate > 0.1) {
      trend = 'increasing';
    } else if (growthRate < -0.1) {
      trend = 'decreasing';
    }

    return {
      current,
      average,
      peak,
      trend,
    };
  }

  /**
   * Detect memory leaks
   */
  detectLeaks(): LeakDetectionResult {
    if (this.snapshots.length < 10) {
      return {
        detected: false,
        growthRate: 0,
        suspiciousObjects: [],
      };
    }

    // Calculate growth rate
    const recent = this.snapshots.slice(-10);
    const oldest = recent[0].heapUsed;
    const newest = recent[recent.length - 1].heapUsed;
    const growthRate = (newest - oldest) / oldest;

    // Detect leak if consistent growth > 20%
    const detected = growthRate > 0.2;

    return {
      detected,
      growthRate,
      suspiciousObjects: [], // TODO: Implement object tracking
    };
  }

  /**
   * Force garbage collection (if available)
   */
  forceGC(): void {
    if (global.gc) {
      global.gc();
    }
  }

  /**
   * Create object pool
   */
  createPool<T>(name: string, factory: () => T, size: number = 10): void {
    const pool: T[] = [];
    for (let i = 0; i < size; i++) {
      pool.push(factory());
    }
    this.pools.set(name, pool as unknown[]);
  }

  /**
   * Acquire object from pool
   */
  acquire<T>(poolName: string, factory?: () => T): T | null {
    const pool = this.pools.get(poolName);
    if (!pool || pool.length === 0) {
      return factory ? factory() : null;
    }
    return pool.pop() as T;
  }

  /**
   * Release object back to pool
   */
  release(poolName: string, obj: unknown): void {
    const pool = this.pools.get(poolName);
    if (pool) {
      pool.push(obj);
    }
  }

  /**
   * Clear all pools
   */
  clearPools(): void {
    this.pools.clear();
  }

  /**
   * Get pool statistics
   */
  getPoolStats(): Map<string, { size: number; available: number }> {
    const stats = new Map<string, { size: number; available: number }>();
    
    this.pools.forEach((pool, name) => {
      stats.set(name, {
        size: pool.length,
        available: pool.length,
      });
    });

    return stats;
  }

  /**
   * Monitor memory usage with callback
   */
  startMonitoring(
    interval: number,
    callback: (stats: ReturnType<typeof this.getStats>) => void
  ): NodeJS.Timeout {
    return setInterval(() => {
      const stats = this.getStats();
      callback(stats);

      // Auto-cleanup if memory usage is high
      if (stats.current.heapUsed > stats.peak * 0.9) {
        this.forceGC();
      }
    }, interval);
  }

  /**
   * Stop monitoring
   */
  stopMonitoring(timer: NodeJS.Timeout): void {
    clearInterval(timer);
  }
}

// Export singleton instance
export const memoryManager = new MemoryManager();
