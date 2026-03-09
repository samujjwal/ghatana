/**
 * @doc.type class
 * @doc.purpose CPU metrics collection from system
 * @doc.layer product
 * @doc.pattern Adapter
 * 
 * Collects CPU metrics including:
 * - User time (time spent in user mode)
 * - System time (time spent in system/kernel mode)
 * - Idle time (time CPU was not used)
 * - I/O wait time
 * 
 * Supports both single-core and aggregate metrics depending on platform.
 * 
 * @see {@link BaseDeviceMonitor}
 */

import { BaseDeviceMonitor, DeviceMonitorConfig } from './BaseDeviceMonitor';

/**
 * CPU metrics snapshot
 * 
 * All time values are in system ticks (100th of a second on most systems)
 */
export interface CPUMetrics {
  /** User-mode time */
  user: number;
  /** System/kernel-mode time */
  system: number;
  /** Idle time */
  idle: number;
  /** I/O wait time */
  iowait: number;
  /** Timestamp when metrics were collected */
  timestamp: number;
  /** CPU usage percentage (0-100) */
  usagePercent: number;
}

/**
 * CPU Monitor Plugin
 * 
 * Collects CPU metrics from the system. On Linux, reads from /proc/stat.
 * On other systems, provides simulated data (for testing/demo purposes).
 * 
 * Usage:
 * ```typescript
 * const monitor = new CPUMonitor();
 * await monitor.initialize();
 * 
 * const metrics = await monitor.collect('system');
 * console.log(`CPU Usage: ${metrics.usagePercent}%`);
 * 
 * await monitor.shutdown();
 * ```
 */
export class CPUMonitor extends BaseDeviceMonitor {
  private previousMetrics: CPUMetrics | null = null;

  /**
   * Create a CPU monitor instance
   * 
   * @param config - Optional configuration overrides
   */
  constructor(config?: Partial<DeviceMonitorConfig>) {
    super(
      'cpu-monitor',
      'CPU Monitor',
      'Collects CPU usage metrics from the system',
      config,
    );
  }

  /**
   * Validate that we can read CPU metrics
   * 
   * On Linux, checks for /proc/stat file.
   * On other platforms, always succeeds (returns simulated data).
   * 
   * @throws Error if /proc/stat is not readable on Linux
   */
  protected async validateEnvironment(): Promise<void> {
    // Check if running on Linux
    const isLinux = process.platform === 'linux';

    if (isLinux) {
      // Try to read /proc/stat to verify access
      try {
        // Dynamic import to avoid compile-time dependency on 'fs'
        const { readFileSync } = await import('fs');
        readFileSync('/proc/stat', 'utf-8');
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        throw new Error(`Cannot read /proc/stat: ${message}`);
      }
    }
  }

  /**
   * Collect CPU metrics
   * 
   * On Linux, parses /proc/stat. On other systems, returns simulated data.
   * Calculates usage percentage based on delta from previous reading.
   * 
   * @param source - Data source (always 'system' for CPU monitor)
   * @returns Promise resolving to metrics object
   * @throws Error if collection fails
   */
  protected async collectMetrics(
    source: string,
  ): Promise<Record<string, unknown>> {
    if (source !== 'system') {
      throw new Error(`Unknown CPU source: ${source}`);
    }

    const metrics = await this.readCPUMetrics();

    // Calculate usage percentage
    if (this.previousMetrics) {
      const deltaNonIdle =
        (metrics.user - this.previousMetrics.user) +
        (metrics.system - this.previousMetrics.system) +
        (metrics.iowait - this.previousMetrics.iowait);

      const deltaTotal =
        deltaNonIdle +
        (metrics.idle - this.previousMetrics.idle);

      if (deltaTotal > 0) {
        metrics.usagePercent = Math.round((deltaNonIdle / deltaTotal) * 100);
      } else {
        metrics.usagePercent = 0;
      }
    } else {
      metrics.usagePercent = 0;
    }

    this.previousMetrics = metrics;

    return {
      user: metrics.user,
      system: metrics.system,
      idle: metrics.idle,
      iowait: metrics.iowait,
      timestamp: metrics.timestamp,
      usagePercent: metrics.usagePercent,
    };
  }

  /**
   * Read CPU metrics from system
   * 
   * On Linux, reads from /proc/stat.
   * On other platforms, returns simulated metrics.
   * 
   * @private
   * @returns Promise resolving to CPUMetrics
   */
  private async readCPUMetrics(): Promise<CPUMetrics> {
    const isLinux = process.platform === 'linux';

    if (isLinux) {
      try {
        const { readFileSync } = await import('fs');
        const content = readFileSync('/proc/stat', 'utf-8');
        const lines = content.split('\n');
        const cpuLine = lines.find(line => line.startsWith('cpu '));

        if (!cpuLine) {
          throw new Error('No cpu line found in /proc/stat');
        }

        const parts = cpuLine.split(/\s+/).filter(p => p.length > 0);

        // Format: cpu user nice system idle iowait ...
        return {
          user: parseInt(parts[1] ?? '0', 10),
          system: parseInt(parts[3] ?? '0', 10),
          idle: parseInt(parts[4] ?? '0', 10),
          iowait: parseInt(parts[5] ?? '0', 10),
          timestamp: Date.now(),
          usagePercent: 0, // Calculated later
        };
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        throw new Error(`Failed to read CPU metrics: ${message}`);
      }
    } else {
      // Simulated data for testing/demo on non-Linux systems
      return {
        user: Math.floor(Math.random() * 10000),
        system: Math.floor(Math.random() * 5000),
        idle: Math.floor(Math.random() * 50000),
        iowait: Math.floor(Math.random() * 1000),
        timestamp: Date.now(),
        usagePercent: 0, // Calculated later
      };
    }
  }
}
