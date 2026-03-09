/**
 * Memory Monitor Plugin
 * Monitors memory usage, garbage collection, and memory trends
 *
 * Phase 3f: Monitor Plugins Implementation
 */

import { BaseMonitorPlugin } from '@ghatana/dcmaar-plugin-abstractions';

declare global {
  interface Window {
    chrome?: {
      system?: {
        memory?: {
          getInfo: (callback: (info: ChromeMemoryInfo) => void) => void;
        };
      };
    };
  }
}

/**
 * Chrome Memory API response structure
 */
interface ChromeMemoryInfo {
  readonly availableCapacity?: number;
  readonly capacity?: number;
}

/**
 * Memory metrics interface
 */
interface MemoryMetrics {
  readonly usageMB: number;
  readonly usagePercent: number;
  readonly totalMB: number;
  readonly availableMB: number;
  readonly gcActivity: 'low' | 'moderate' | 'high';
  readonly trend: 'rising' | 'stable' | 'falling';
  readonly alert?: boolean;
}

/**
 * Memory historical data for trend calculation
 */
interface MemoryDataPoint {
  readonly timestamp: number;
  readonly usageMB: number;
  readonly usagePercent: number;
}

/**
 * Memory Monitor - Tracks memory usage and garbage collection
 * Extends BaseMonitorPlugin with memory-specific monitoring
 */
export class MemoryMonitor extends BaseMonitorPlugin {
  private readonly dataPoints: MemoryDataPoint[] = [];
  private readonly maxDataPoints = 60; // Keep 60 data points
  private gcEventCount = 0;
  private lastMemoryUsage = 0;

  /**
   * Initialize Memory Monitor
   */
  constructor() {
    super('memory-monitor', 'Memory Monitor', 'monitor');
    // Monitor garbage collection events
    if (typeof window !== 'undefined' && window.performance) {
      this.setupGCMonitoring();
    }
  }

  /**
   * Get available data sources for this monitor
   * @returns Array of source names
   */
  async getSources(): Promise<string[]> {
    return ['memory-usage', 'memory-total', 'memory-available', 'gc-activity'];
  }

  /**
   * Collect memory metrics
   * Uses chrome.system.memory API and performance APIs
   *
   * @returns Memory metrics including usage, available, GC activity, trends
   */
  async collect(): Promise<Record<string, unknown>> {
    try {
      // Get memory info from chrome.system API
      const memoryInfo = await this.getMemoryInfo();

      if (memoryInfo === null) {
        return this.createDefaultMetrics();
      }

      const totalMB = (memoryInfo.capacity ?? 0) / (1024 * 1024);
      const availableMB = (memoryInfo.availableCapacity ?? 0) / (1024 * 1024);
      const usageMB = totalMB - availableMB;
      const usagePercent = totalMB > 0 ? Math.round((usageMB / totalMB) * 100) : 0;

      // Determine GC activity level
      const gcActivity = this.determineGCActivity();

      // Calculate trend
      const trend = this.calculateTrend(usagePercent);

      // Determine if alert should be triggered
      const alert = usagePercent > 85; // Alert at >85% usage

      const metrics: MemoryMetrics = {
        usageMB: Math.round(usageMB),
        usagePercent,
        totalMB: Math.round(totalMB),
        availableMB: Math.round(availableMB),
        gcActivity,
        trend,
        alert,
      };

      // Store data point for trend analysis
      this.addDataPoint(usageMB, usagePercent);
      this.lastMemoryUsage = usageMB;

      return metrics as unknown as Record<string, unknown>;
    } catch (error) {
      console.warn('Memory collection failed:', error);
      return this.createErrorMetrics();
    }
  }

  /**
   * Get memory information from chrome.system.memory API
   * @returns Memory info or null if API unavailable
   */
  private async getMemoryInfo(): Promise<ChromeMemoryInfo | null> {
    return new Promise((resolve) => {
      const chromeAPI = typeof window !== 'undefined' ? window.chrome : undefined;
      if (!chromeAPI?.system?.memory?.getInfo) {
        console.warn('chrome.system.memory API not available');
        resolve(null);
        return;
      }

      chromeAPI.system.memory.getInfo((info: ChromeMemoryInfo) => {
        resolve(info);
      });
    });
  }

  /**
   * Setup GC monitoring using performance APIs
   */
  private setupGCMonitoring(): void {
    // Monitor long tasks which are often associated with GC
    if (window.PerformanceObserver) {
      try {
        const observer = new PerformanceObserver((list: unknown) => {
          const typedList = list as { getEntries: () => Array<{ readonly duration: number }> };
          const entries = typedList.getEntries();
          for (const entry of entries) {
            // Long tasks might indicate GC activity
            if (entry.duration > 50) {
              this.gcEventCount += 1;
            }
          }
        });

        observer.observe({ entryTypes: ['longtask'] });
      } catch {
        // Long task API may not be available
      }
    }
  }

  /**
   * Determine garbage collection activity level based on event count
   * @returns GC activity level: 'low', 'moderate', or 'high'
   */
  private determineGCActivity(): 'low' | 'moderate' | 'high' {
    // Reset counter every 60 seconds
    const threshold = 5;

    if (this.gcEventCount < threshold) {
      return 'low';
    } else if (this.gcEventCount < threshold * 2) {
      return 'moderate';
    } else {
      return 'high';
    }
  }

  /**
   * Calculate trend based on recent memory usage data
   * @param _currentPercent Current memory usage percentage
   * @returns Trend direction: 'rising', 'stable', 'falling'
   */
  private calculateTrend(_currentPercent: number): 'rising' | 'stable' | 'falling' {
    if (this.dataPoints.length < 3) {
      return 'stable';
    }

    // Get last 3 data points
    const recent = this.dataPoints.slice(-3).map(dp => dp.usagePercent);
    const [old, _mid, current] = recent;

    // Calculate rate of change
    const rateOfChange = current - old;

    if (rateOfChange > 5) {
      return 'rising';
    } else if (rateOfChange < -5) {
      return 'falling';
    } else {
      return 'stable';
    }
  }

  /**
   * Add data point to history for trend analysis
   * @param usageMB Memory usage in MB
   * @param usagePercent Memory usage percentage
   */
  private addDataPoint(usageMB: number, usagePercent: number): void {
    this.dataPoints.push({
      timestamp: Date.now(),
      usageMB,
      usagePercent,
    });

    // Keep only recent data points
    if (this.dataPoints.length > this.maxDataPoints) {
      this.dataPoints.shift();
    }
  }

  /**
   * Create default metrics when API is unavailable
   * @returns Default metrics object
   */
  private createDefaultMetrics(): Record<string, unknown> {
    // Try to get JavaScript heap info if available
    const performance = typeof window !== 'undefined' ? window.performance : undefined;
    const heapInfo = (performance as unknown as { readonly memory?: { readonly usedJSHeapSize: number; readonly jsHeapSizeLimit: number } })?.memory;

    if (heapInfo) {
      const usageMB = heapInfo.usedJSHeapSize / (1024 * 1024);
      const totalMB = heapInfo.jsHeapSizeLimit / (1024 * 1024);
      const usagePercent = Math.round((usageMB / totalMB) * 100);

      return {
        usageMB: Math.round(usageMB),
        usagePercent,
        totalMB: Math.round(totalMB),
        availableMB: Math.round(totalMB - usageMB),
        gcActivity: 'low',
        trend: 'stable',
        alert: usagePercent > 85,
      };
    }

    return {
      usageMB: 0,
      usagePercent: 0,
      totalMB: 0,
      availableMB: 0,
      gcActivity: 'low',
      trend: 'stable',
      alert: false,
    };
  }

  /**
   * Create error metrics object
   * @returns Error metrics object
   */
  private createErrorMetrics(): Record<string, unknown> {
    return {
      usageMB: 0,
      usagePercent: 0,
      totalMB: 0,
      availableMB: 0,
      gcActivity: 'low',
      trend: 'stable',
      alert: false,
      error: 'Failed to collect memory metrics',
    };
  }

  /**
   * Get plugin type
   * @returns Plugin type string
   */
  getType(): string {
    return 'monitor';
  }

  /**
   * Initialize monitor (optional setup)
   * @returns Promise that resolves when ready
   */
  async initialize(): Promise<void> {
    // Initialize data collection
    this.dataPoints.length = 0;
    this.gcEventCount = 0;
    this.lastMemoryUsage = 0;
  }

  /**
   * Shutdown monitor (cleanup)
   * @returns Promise that resolves when shut down
   */
  async shutdown(): Promise<void> {
    // Clear data on shutdown
    this.dataPoints.length = 0;
  }

  /**
   * Get current status of monitor
   * @returns Status string
   */
  getStatus(): string {
    return this.lastMemoryUsage > 0 && this.lastMemoryUsage * 1024 > 2000 // > 2GB
      ? 'warning'
      : 'healthy';
  }
}

// Export for use in plugin manager
export default MemoryMonitor;
