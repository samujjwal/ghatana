/**
 * Performance Monitoring Utility
 * Tracks and reports performance metrics
 */

interface PerformanceMetric {
  name: string;
  value: number;
  timestamp: number;
  category: 'timing' | 'memory' | 'network' | 'custom';
}

interface MemoryInfo {
  usedJSHeapSize: number;
  totalJSHeapSize: number;
  jsHeapSizeLimit: number;
}

class PerformanceMonitor {
  private metrics: PerformanceMetric[] = [];
  private readonly maxMetrics = 1000;
  private enabled = false;

  /**
   * Initialize performance monitoring
   */
  public init(): void {
    this.enabled = process.env.NODE_ENV === 'development' || 
                   process.env.VITE_ENABLE_PERF_MONITORING === 'true';
    
    if (this.enabled) {
      this.setupObservers();
    }
  }

  /**
   * Set up performance observers
   */
  private setupObservers(): void {
    if (typeof PerformanceObserver === 'undefined') {
      return;
    }

    try {
      // Observe navigation timing
      const navObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          this.recordMetric({
            name: entry.name,
            value: entry.duration,
            timestamp: Date.now(),
            category: 'timing',
          });
        }
      });
      navObserver.observe({ entryTypes: ['navigation'] });

      // Observe resource timing
      const resourceObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          this.recordMetric({
            name: `resource:${entry.name}`,
            value: entry.duration,
            timestamp: Date.now(),
            category: 'network',
          });
        }
      });
      resourceObserver.observe({ entryTypes: ['resource'] });

      // Observe measures
      const measureObserver = new PerformanceObserver((list) => {
        for (const entry of list.getEntries()) {
          this.recordMetric({
            name: entry.name,
            value: entry.duration,
            timestamp: Date.now(),
            category: 'custom',
          });
        }
      });
      measureObserver.observe({ entryTypes: ['measure'] });
    } catch (error) {
      console.error('[PerformanceMonitor] Error setting up observers:', error);
    }
  }

  /**
   * Record a performance metric
   */
  private recordMetric(metric: PerformanceMetric): void {
    this.metrics.push(metric);
    
    // Keep only the most recent metrics
    if (this.metrics.length > this.maxMetrics) {
      this.metrics = this.metrics.slice(-this.maxMetrics);
    }
  }

  /**
   * Start a performance measurement
   */
  public startMeasure(name: string): void {
    if (!this.enabled) return;
    
    try {
      performance.mark(`${name}-start`);
    } catch (error) {
      console.error('[PerformanceMonitor] Error starting measure:', error);
    }
  }

  /**
   * End a performance measurement
   */
  public endMeasure(name: string): number | null {
    if (!this.enabled) return null;
    
    try {
      performance.mark(`${name}-end`);
      performance.measure(name, `${name}-start`, `${name}-end`);
      
      const measure = performance.getEntriesByName(name, 'measure')[0];
      return measure ? measure.duration : null;
    } catch (error) {
      console.error('[PerformanceMonitor] Error ending measure:', error);
      return null;
    }
  }

  /**
   * Record a custom metric
   */
  public recordCustomMetric(name: string, value: number): void {
    if (!this.enabled) return;
    
    this.recordMetric({
      name,
      value,
      timestamp: Date.now(),
      category: 'custom',
    });
  }

  /**
   * Get memory usage information
   */
  public getMemoryUsage(): MemoryInfo | null {
    if (!this.enabled) return null;
    
    try {
      const memory = (performance as { memory?: MemoryInfo }).memory;
      if (memory) {
        this.recordMetric({
          name: 'memory:usedJSHeapSize',
          value: memory.usedJSHeapSize,
          timestamp: Date.now(),
          category: 'memory',
        });
        
        return {
          usedJSHeapSize: memory.usedJSHeapSize,
          totalJSHeapSize: memory.totalJSHeapSize,
          jsHeapSizeLimit: memory.jsHeapSizeLimit,
        };
      }
    } catch (error) {
      console.error('[PerformanceMonitor] Error getting memory usage:', error);
    }
    
    return null;
  }

  /**
   * Get all recorded metrics
   */
  public getMetrics(category?: PerformanceMetric['category']): PerformanceMetric[] {
    if (category) {
      return this.metrics.filter(m => m.category === category);
    }
    return [...this.metrics];
  }

  /**
   * Get metrics summary
   */
  public getSummary(): Record<string, { count: number; avg: number; min: number; max: number }> {
    const summary: Record<string, { count: number; total: number; min: number; max: number }> = {};
    
    for (const metric of this.metrics) {
      if (!summary[metric.name]) {
        summary[metric.name] = {
          count: 0,
          total: 0,
          min: Infinity,
          max: -Infinity,
        };
      }
      
      const s = summary[metric.name];
      s.count++;
      s.total += metric.value;
      s.min = Math.min(s.min, metric.value);
      s.max = Math.max(s.max, metric.value);
    }
    
    // Calculate averages
    const result: Record<string, { count: number; avg: number; min: number; max: number }> = {};
    for (const [name, data] of Object.entries(summary)) {
      result[name] = {
        count: data.count,
        avg: data.total / data.count,
        min: data.min,
        max: data.max,
      };
    }
    
    return result;
  }

  /**
   * Clear all metrics
   */
  public clear(): void {
    this.metrics = [];
  }

  /**
   * Export metrics as JSON
   */
  public export(): string {
    return JSON.stringify({
      metrics: this.metrics,
      summary: this.getSummary(),
      timestamp: Date.now(),
    }, null, 2);
  }
}

// Singleton instance
export const performanceMonitor = new PerformanceMonitor();

// Auto-initialize
performanceMonitor.init();

export default performanceMonitor;
