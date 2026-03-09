/**
 * CPU Monitor Plugin
 * Monitors CPU usage, temperature, and throttling status
 *
 * Phase 3f: Monitor Plugins Implementation
 */

import { BaseMonitorPlugin } from '@ghatana/dcmaar-plugin-abstractions';

declare global {
  interface Window {
    chrome?: {
      system?: {
        cpu?: {
          getInfo: (callback: (info: ChromeCPUInfo) => void) => void;
        };
      };
    };
  }
}

/**
 * Chrome CPU API response structure
 */
interface ChromeCPUInfo {
  readonly processorUsage?: ReadonlyArray<{ readonly usage: number }>;
  readonly temperatures?: ReadonlyArray<number>;
}

/**
 * CPU metrics interface
 */
interface CPUMetrics {
  readonly usage: number; // 0-100 percentage
  readonly cores: number;
  readonly temperature?: number; // Celsius if available
  readonly throttled: boolean;
  readonly trend: 'rising' | 'stable' | 'falling';
  readonly alert?: boolean;
}

/**
 * CPU historical data for trend calculation
 */
interface CPUDataPoint {
  readonly timestamp: number;
  readonly usage: number;
}

/**
 * CPU Monitor - Tracks CPU usage and thermal status
 * Extends BaseMonitorPlugin with CPU-specific monitoring
 */
export class CPUMonitor extends BaseMonitorPlugin {
  private readonly dataPoints: CPUDataPoint[] = [];
  private readonly maxDataPoints = 60; // Keep 60 data points (~1 minute at 1s granularity)
  private lastUsage = 0;

  /**
   * Initialize CPU Monitor
   */
  constructor() {
    super('cpu-monitor', 'CPU Monitor', 'monitor');
  }

  /**
   * Get available data sources for this monitor
   * @returns Array of source names
   */
  async getSources(): Promise<string[]> {
    return ['cpu-usage', 'cpu-cores', 'cpu-temperature', 'cpu-throttling'];
  }

  /**
   * Collect CPU metrics
   * Uses chrome.system.cpu API to get CPU information
   *
   * @returns CPU metrics including usage, cores, temperature, throttling status
   */
  async collect(): Promise<Record<string, unknown>> {
    try {
      // Get CPU info from chrome.system API
      const cpuInfo = await this.getCPUInfo();

      if (cpuInfo === null) {
        return this.createMetrics(0);
      }

      const processorUsage = cpuInfo.processorUsage ?? [];
      const temperatures = cpuInfo.temperatures ?? [];

      // Calculate average CPU usage across all processors
      const avgUsage = this.calculateAverageUsage(processorUsage);

      // Get temperature data if available
      const temperature = temperatures.length > 0
        ? Math.max(...temperatures)
        : undefined;

      // Check if CPU is throttled (unusual pattern detection)
      const throttled = this.detectThrottling();

      // Calculate trend
      const trend = this.calculateTrend();

      // Determine if alert should be triggered
      const alert = avgUsage > 80; // Alert at >80% usage

      const metrics: CPUMetrics = {
        usage: avgUsage,
        cores: processorUsage.length || 1,
        temperature,
        throttled,
        trend,
        alert,
      };

      // Store data point for trend analysis
      this.addDataPoint(avgUsage);
      this.lastUsage = avgUsage;

      return metrics as unknown as Record<string, unknown>;
    } catch (error) {
      console.warn('CPU collection failed:', error);
      return this.createErrorMetrics();
    }
  }

  /**
   * Get CPU information from chrome.system.cpu API
   * @returns CPU info or null if API unavailable
   */
  private async getCPUInfo(): Promise<ChromeCPUInfo | null> {
    return new Promise((resolve) => {
      const chromeAPI = typeof window !== 'undefined' ? window.chrome : undefined;
      if (!chromeAPI?.system?.cpu?.getInfo) {
        console.warn('chrome.system.cpu API not available');
        resolve(null);
        return;
      }

      chromeAPI.system.cpu.getInfo((info: ChromeCPUInfo) => {
        resolve(info);
      });
    });
  }

  /**
   * Calculate average CPU usage across all processors
   * @param processorUsage Array of processor usage data
   * @returns Average usage percentage
   */
  private calculateAverageUsage(processorUsage: ReadonlyArray<{ readonly usage: number }>): number {
    if (processorUsage.length === 0) {
      return 0;
    }

    const total = Array.from(processorUsage).reduce((sum, proc) => sum + (proc.usage || 0), 0);
    return Math.round(total / processorUsage.length);
  }

  /**
   * Detect CPU throttling based on usage patterns
   * Throttling is indicated by rapid on-off patterns or unusual spikes
   *
   * @returns True if throttling detected
   */
  private detectThrottling(): boolean {
    // If we don't have enough history, can't detect throttling
    if (this.dataPoints.length < 10) {
      return false;
    }

    // Get recent data points (last 10)
    const recent = this.dataPoints.slice(-10);

    // Calculate variance to detect unusual patterns
    const mean = recent.reduce((sum, dp) => sum + dp.usage, 0) / recent.length;
    const variance = recent.reduce((sum, dp) => sum + Math.pow(dp.usage - mean, 2), 0) / recent.length;
    const stdDev = Math.sqrt(variance);

    // High variance combined with high usage suggests throttling
    return stdDev > 20 && mean > 60;
  }

  /**
   * Calculate trend based on recent data
   * @returns Trend direction: 'rising', 'stable', 'falling'
   */
  private calculateTrend(): 'rising' | 'stable' | 'falling' {
    if (this.dataPoints.length < 3) {
      return 'stable';
    }

    // Get last 3 data points
    const recent = this.dataPoints.slice(-3).map(dp => dp.usage);
    const [old, _mid, current] = recent;

    // Calculate rate of change
    const rateOfChange = current - old;

    if (rateOfChange > 10) {
      return 'rising';
    } else if (rateOfChange < -10) {
      return 'falling';
    } else {
      return 'stable';
    }
  }

  /**
   * Add data point to history for trend analysis
   * @param usage CPU usage percentage
   */
  private addDataPoint(usage: number): void {
    this.dataPoints.push({
      timestamp: Date.now(),
      usage,
    });

    // Keep only recent data points
    if (this.dataPoints.length > this.maxDataPoints) {
      this.dataPoints.shift();
    }
  }

  /**
   * Create metrics object for successful collection
   * @param usage CPU usage percentage
   * @returns Metrics object
   */
  private createMetrics(usage: number): Record<string, unknown> {
    return {
      usage,
      cores: navigator.hardwareConcurrency || 1,
      trend: 'stable',
      throttled: false,
      alert: usage > 80,
    };
  }

  /**
   * Create error metrics object
   * @returns Error metrics object
   */
  private createErrorMetrics(): Record<string, unknown> {
    return {
      usage: 0,
      cores: 0,
      temperature: undefined,
      throttled: false,
      trend: 'stable',
      alert: false,
      error: 'Failed to collect CPU metrics',
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
    this.lastUsage = 0;
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
    return this.lastUsage > 80 ? 'warning' : 'healthy';
  }
}

// Export for use in plugin manager
export default CPUMonitor;
