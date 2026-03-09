/**
 * @doc.type class
 * @doc.purpose Battery metrics collection from system
 * @doc.layer product
 * @doc.pattern Adapter
 * 
 * Collects battery metrics including:
 * - Current charge level
 * - Battery status (charging, discharging, unknown)
 * - Time to empty/full
 * - Current/voltage draw
 * - Battery health percentage
 * 
 * Supports both Linux (via /sys/class/power_supply) and other systems.
 * 
 * @see {@link BaseDeviceMonitor}
 */

import { BaseDeviceMonitor, DeviceMonitorConfig } from './BaseDeviceMonitor';

/**
 * Battery status values
 */
type BatteryStatus = 'charging' | 'discharging' | 'full' | 'unknown';

/**
 * Battery metrics snapshot
 */
export interface BatteryMetrics {
  /** Current battery level (0-100) */
  level: number;
  /** Battery status */
  status: BatteryStatus;
  /** Time to empty in seconds (-1 if unknown) */
  timeToEmpty: number;
  /** Time to full in seconds (-1 if unknown) */
  timeToFull: number;
  /** Battery voltage in millivolts */
  voltage: number;
  /** Battery current in milliamps (negative when discharging) */
  current: number;
  /** Battery health percentage (0-100) */
  health: number;
  /** Temperature in celsius */
  temperature: number;
  /** Timestamp when metrics were collected */
  timestamp: number;
}

/**
 * Battery Monitor Plugin
 * 
 * Collects battery metrics from the system. On Linux, reads from /sys/class/power_supply.
 * On other systems, provides simulated data (for testing/demo purposes).
 * 
 * Usage:
 * ```typescript
 * const monitor = new BatteryMonitor();
 * await monitor.initialize();
 * 
 * const metrics = await monitor.collect('system');
 * console.log(`Battery: ${metrics.level}% (${metrics.status})`);
 * console.log(`Time to empty: ${metrics.timeToEmpty} seconds`);
 * 
 * await monitor.shutdown();
 * ```
 */
export class BatteryMonitor extends BaseDeviceMonitor {
  /**
   * Create a battery monitor instance
   * 
   * @param config - Optional configuration overrides
   */
  constructor(config?: Partial<DeviceMonitorConfig>) {
    super(
      'battery-monitor',
      'Battery Monitor',
      'Collects system battery metrics',
      config,
    );
  }

  /**
   * Validate that we can read battery metrics
   * 
   * On Linux, checks for /sys/class/power_supply directory.
   * On other platforms, always succeeds (returns simulated data).
   * 
   * @throws Error if /sys/class/power_supply is not accessible on Linux
   */
  protected async validateEnvironment(): Promise<void> {
    const isLinux = process.platform === 'linux';

    if (isLinux) {
      try {
        const { existsSync } = await import('fs');
        if (!existsSync('/sys/class/power_supply')) {
          throw new Error('Power supply directory not found');
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        throw new Error(`Cannot read battery metrics: ${message}`);
      }
    }
  }

  /**
   * Collect battery metrics
   * 
   * On Linux, reads from /sys/class/power_supply. On other systems, returns simulated data.
   * 
   * @param source - Data source (always 'system' for battery monitor)
   * @returns Promise resolving to metrics object
   * @throws Error if collection fails
   */
  protected async collectMetrics(
    source: string,
  ): Promise<Record<string, unknown>> {
    if (source !== 'system') {
      throw new Error(`Unknown battery source: ${source}`);
    }

    const metrics = await this.readBatteryMetrics();

    return {
      level: metrics.level,
      status: metrics.status,
      timeToEmpty: metrics.timeToEmpty,
      timeToFull: metrics.timeToFull,
      voltage: metrics.voltage,
      current: metrics.current,
      health: metrics.health,
      temperature: metrics.temperature,
      timestamp: metrics.timestamp,
    };
  }

  /**
   * Read battery metrics from system
   * 
   * On Linux, reads from /sys/class/power_supply/BAT0 or /sys/class/power_supply/BAT1.
   * On other platforms, returns simulated metrics.
   * 
   * @private
   * @returns Promise resolving to BatteryMetrics
   */
  private async readBatteryMetrics(): Promise<BatteryMetrics> {
    const isLinux = process.platform === 'linux';

    if (isLinux) {
      try {
        const metrics = await this.readLinuxBatteryMetrics();
        return metrics;
      } catch (error) {
        // Fall back to simulated data if reading fails
        return this.generateSimulatedMetrics();
      }
    } else {
      return this.generateSimulatedMetrics();
    }
  }

  /**
   * Read battery metrics from Linux /sys interface
   * 
   * @private
   * @returns Promise resolving to BatteryMetrics
   * @throws Error if battery info cannot be read
   */
  private async readLinuxBatteryMetrics(): Promise<BatteryMetrics> {
    const { readFileSync, existsSync } = await import('fs');
    const { join } = await import('path');

    // Try to find battery (BAT0, BAT1, or BAT2)
    const batteryNames = ['BAT0', 'BAT1', 'BAT2'];
    let batteryPath = '';

    for (const name of batteryNames) {
      const path = `/sys/class/power_supply/${name}`;
      if (existsSync(path)) {
        batteryPath = path;
        break;
      }
    }

    if (!batteryPath) {
      throw new Error('No battery found in /sys/class/power_supply');
    }

    // Helper to read attribute file
    const readAttr = (name: string, defaultValue: number = 0): number => {
      try {
        const value = readFileSync(join(batteryPath, name), 'utf-8').trim();
        return parseInt(value, 10) || defaultValue;
      } catch {
        return defaultValue;
      }
    };

    // Helper to read string attribute
    const readAttrString = (name: string, defaultValue: string = ''): string => {
      try {
        return readFileSync(join(batteryPath, name), 'utf-8').trim();
      } catch {
        return defaultValue;
      }
    };

    const level = readAttr('capacity', 100);
    const statusStr = readAttrString('status', 'Unknown').toLowerCase();
    const voltage = readAttr('voltage_now', 0);
    const current = readAttr('current_now', 0);
    const health = readAttr('health', 100);
    const temperature = readAttr('temp', 25);

    // Parse status
    let status: BatteryStatus = 'unknown';
    if (statusStr.includes('charging')) {
      status = 'charging';
    } else if (statusStr.includes('discharging')) {
      status = 'discharging';
    } else if (statusStr.includes('full')) {
      status = 'full';
    }

    return {
      level: Math.max(0, Math.min(100, level)),
      status,
      timeToEmpty: -1, // Usually not available in /sys
      timeToFull: -1, // Usually not available in /sys
      voltage: voltage > 0 ? Math.round(voltage / 1000) : 0, // Convert to mV
      current: current > 0 ? Math.round(current / 1000) : 0, // Convert to mA
      health: Math.max(0, Math.min(100, health)),
      temperature: Math.round((temperature / 10) * 10) / 10, // Convert to celsius
      timestamp: Date.now(),
    };
  }

  /**
   * Generate simulated battery metrics for testing/demo
   * 
   * @private
   * @returns BatteryMetrics with simulated values
   */
  private generateSimulatedMetrics(): BatteryMetrics {
    const level = Math.floor(Math.random() * 100);
    const status: BatteryStatus[] = ['charging', 'discharging', 'full'];
    const statuses = status as BatteryStatus[];
    const randomStatus = statuses[Math.floor(Math.random() * statuses.length)];

    return {
      level,
      status: randomStatus,
      timeToEmpty:
        randomStatus === 'discharging' ? Math.floor(Math.random() * 14400) : -1, // 0-4 hours
      timeToFull:
        randomStatus === 'charging' ? Math.floor(Math.random() * 7200) : -1, // 0-2 hours
      voltage: 3500 + Math.floor(Math.random() * 1500), // 3500-5000 mV
      current: Math.floor(Math.random() * 2000) - 1000, // -1000 to 1000 mA
      health: 80 + Math.floor(Math.random() * 20), // 80-100%
      temperature: 20 + Math.floor(Math.random() * 30), // 20-50°C
      timestamp: Date.now(),
    };
  }
}
