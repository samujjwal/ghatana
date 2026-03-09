/**
 * @doc.type class
 * @doc.purpose Memory metrics collection from system
 * @doc.layer product
 * @doc.pattern Adapter
 * 
 * Collects memory metrics including:
 * - Total memory available
 * - Free memory
 * - Used memory
 * - Cached memory
 * - Memory usage percentage
 * 
 * Supports both physical RAM and virtual memory metrics.
 * 
 * @see {@link BaseDeviceMonitor}
 */

import { BaseDeviceMonitor, DeviceMonitorConfig } from './BaseDeviceMonitor';

/**
 * Memory metrics snapshot
 * 
 * All memory values are in kilobytes (KB)
 */
export interface MemoryMetrics {
  /** Total system memory in KB */
  total: number;
  /** Free memory in KB */
  free: number;
  /** Used memory in KB (total - free - cached) */
  used: number;
  /** Cached memory in KB */
  cached: number;
  /** Memory usage percentage (0-100) */
  usagePercent: number;
  /** Timestamp when metrics were collected */
  timestamp: number;
}

/**
 * Memory Monitor Plugin
 * 
 * Collects memory metrics from the system. On Linux, reads from /proc/meminfo.
 * On other systems, uses Node.js process.memoryUsage() as fallback.
 * 
 * Usage:
 * ```typescript
 * const monitor = new MemoryMonitor();
 * await monitor.initialize();
 * 
 * const metrics = await monitor.collect('system');
 * console.log(`Memory Usage: ${metrics.usagePercent}%`);
 * console.log(`Available: ${metrics.free} KB`);
 * 
 * await monitor.shutdown();
 * ```
 */
export class MemoryMonitor extends BaseDeviceMonitor {
  /**
   * Create a memory monitor instance
   * 
   * @param config - Optional configuration overrides
   */
  constructor(config?: Partial<DeviceMonitorConfig>) {
    super(
      'memory-monitor',
      'Memory Monitor',
      'Collects system memory usage metrics',
      config,
    );
  }

  /**
   * Validate that we can read memory metrics
   * 
   * On Linux, checks for /proc/meminfo file.
   * On other platforms, always succeeds (uses fallback method).
   * 
   * @throws Error if /proc/meminfo is not readable on Linux
   */
  protected async validateEnvironment(): Promise<void> {
    const isLinux = process.platform === 'linux';

    if (isLinux) {
      try {
        const { readFileSync } = await import('fs');
        readFileSync('/proc/meminfo', 'utf-8');
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        throw new Error(`Cannot read /proc/meminfo: ${message}`);
      }
    }
  }

  /**
   * Collect memory metrics
   * 
   * On Linux, parses /proc/meminfo. On other systems, uses Node.js metrics.
   * 
   * @param source - Data source (always 'system' for memory monitor)
   * @returns Promise resolving to metrics object
   * @throws Error if collection fails
   */
  protected async collectMetrics(
    source: string,
  ): Promise<Record<string, unknown>> {
    if (source !== 'system') {
      throw new Error(`Unknown memory source: ${source}`);
    }

    const metrics = await this.readMemoryMetrics();

    return {
      total: metrics.total,
      free: metrics.free,
      used: metrics.used,
      cached: metrics.cached,
      usagePercent: metrics.usagePercent,
      timestamp: metrics.timestamp,
    };
  }

  /**
   * Read memory metrics from system
   * 
   * On Linux, reads from /proc/meminfo.
   * On other platforms, uses Node.js process.memoryUsage().
   * 
   * @private
   * @returns Promise resolving to MemoryMetrics
   */
  private async readMemoryMetrics(): Promise<MemoryMetrics> {
    const isLinux = process.platform === 'linux';

    if (isLinux) {
      try {
        const { readFileSync } = await import('fs');
        const content = readFileSync('/proc/meminfo', 'utf-8');
        const lines = content.split('\n');

        // Parse /proc/meminfo into a map
        const memInfo = new Map<string, number>();
        for (const line of lines) {
          const match = line.match(/^(\w+):\s+(\d+)/);
          if (match) {
            memInfo.set(match[1], parseInt(match[2], 10));
          }
        }

        const total = memInfo.get('MemTotal') ?? 0;
        const free = memInfo.get('MemFree') ?? 0;
        const cached = memInfo.get('Cached') ?? 0;
        const used = total - free - cached;
        const usagePercent =
          total > 0 ? Math.round(((total - free) / total) * 100) : 0;

        return {
          total,
          free,
          used: Math.max(0, used),
          cached,
          usagePercent,
          timestamp: Date.now(),
        };
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        throw new Error(`Failed to read memory metrics: ${message}`);
      }
    } else {
      // Use Node.js process metrics as fallback
      const memUsage = process.memoryUsage();

      // Convert bytes to KB
      const heapUsed = Math.round(memUsage.heapUsed / 1024);
      const heapTotal = Math.round(memUsage.heapTotal / 1024);
      const external = Math.round(memUsage.external / 1024);

      return {
        total: heapTotal + external,
        free: heapTotal - heapUsed,
        used: heapUsed,
        cached: external,
        usagePercent: Math.round((heapUsed / heapTotal) * 100),
        timestamp: Date.now(),
      };
    }
  }
}
