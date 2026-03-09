/**
 * Battery Monitor Plugin
 * Monitors battery level, health, and discharge rate
 *
 * Phase 3f: Monitor Plugins Implementation
 */

import { BaseMonitorPlugin } from '@ghatana/dcmaar-plugin-abstractions';

declare global {
  interface Window {
    chrome?: {
      system?: {
        power?: {
          requestKeepAwake: (type: string) => void;
          releaseKeepAwake: () => void;
        };
      };
      system?: {
        power?: { requestKeepAwake: (type: string) => void };
      };
    };
    navigator?: {
      getBattery?: () => Promise<BatteryManager>;
    };
  }
}

/**
 * Battery Manager interface from Battery Status API
 */
interface BatteryManager extends EventTarget {
  readonly level: number; // 0-1
  readonly charging: boolean;
  readonly chargingTime: number; // seconds
  readonly dischargingTime: number; // seconds
  onlevelchange?: ((event: Event) => void) | null;
  onchargingchange?: ((event: Event) => void) | null;
  onchargingtimechange?: ((event: Event) => void) | null;
  ondischargingtimechange?: ((event: Event) => void) | null;
}

/**
 * Battery metrics interface
 */
interface BatteryMetrics {
  readonly levelPercent: number;
  readonly charging: boolean;
  readonly timeRemaining: number; // seconds
  readonly timeToFullCharge: number; // seconds
  readonly drainRate: number; // percent per hour
  readonly health: 'good' | 'fair' | 'poor';
  readonly alert?: boolean;
}

/**
 * Battery historical data for drain rate calculation
 */
interface BatteryDataPoint {
  readonly timestamp: number;
  readonly level: number;
}

/**
 * Battery Monitor - Tracks battery level and health
 * Extends BaseMonitorPlugin with battery-specific monitoring
 */
export class BatteryMonitor extends BaseMonitorPlugin {
  private readonly dataPoints: BatteryDataPoint[] = [];
  private readonly maxDataPoints = 60;
  private lastLevel = 100;
  private batteryManager: BatteryManager | null = null;

  /**
   * Initialize Battery Monitor
   */
  constructor() {
    super('battery-monitor', 'Battery Monitor', 'monitor');
    this.initializeBatteryManager();
  }

  /**
   * Get available data sources for this monitor
   * @returns Array of source names
   */
  async getSources(): Promise<string[]> {
    return ['battery-level', 'battery-charging', 'battery-drain-rate', 'battery-time-remaining'];
  }

  /**
   * Initialize Battery Manager from Battery Status API
   */
  private initializeBatteryManager(): void {
    if (typeof navigator === 'undefined') {
      return;
    }

    // Try Battery Status API (deprecated but widely supported)
    const navWithBattery = navigator as unknown as { getBattery?: () => Promise<BatteryManager> };
    if (navWithBattery.getBattery) {
      navWithBattery.getBattery().then((bm) => {
        this.batteryManager = bm;
      }).catch(() => {
        console.warn('Battery Status API not available');
      });
    }
  }

  /**
   * Collect battery metrics
   * Uses Battery Status API when available, otherwise returns defaults
   *
   * @returns Battery metrics including level, charging, drain rate, health
   */
  async collect(): Promise<Record<string, unknown>> {
    try {
      if (this.batteryManager) {
        return this.collectFromBatteryManager();
      }

      // Fallback: estimate based on historical data
      return this.createDefaultMetrics();
    } catch (error) {
      console.warn('Battery collection failed:', error);
      return this.createErrorMetrics();
    }
  }

  /**
   * Collect battery metrics from Battery Manager API
   * @returns Battery metrics object
   */
  private collectFromBatteryManager(): Record<string, unknown> {
    if (!this.batteryManager) {
      return this.createDefaultMetrics();
    }

    const levelPercent = Math.round(this.batteryManager.level * 100);
    const charging = this.batteryManager.charging;
    const timeRemaining = Math.round(this.batteryManager.dischargingTime);
    const timeToFullCharge = Math.round(this.batteryManager.chargingTime);

    // Calculate drain rate from historical data
    const drainRate = this.calculateDrainRate(levelPercent);

    // Determine battery health
    const health = this.determineBatteryHealth(drainRate, levelPercent);

    // Determine if alert should be triggered
    const alert = levelPercent < 20 && !charging;

    const metrics: BatteryMetrics = {
      levelPercent,
      charging,
      timeRemaining,
      timeToFullCharge,
      drainRate,
      health,
      alert,
    };

    // Store data point for drain rate calculation
    this.addDataPoint(levelPercent);
    this.lastLevel = levelPercent;

    return metrics as unknown as Record<string, unknown>;
  }

  /**
   * Calculate battery drain rate from historical data
   * @param currentLevel Current battery level percentage
   * @returns Drain rate (percent per hour)
   */
  private calculateDrainRate(currentLevel: number): number {
    if (this.dataPoints.length < 2) {
      return 0;
    }

    // Get oldest and newest data points
    const oldest = this.dataPoints[0];
    const newest = this.dataPoints[this.dataPoints.length - 1];

    // Calculate level change
    const levelChange = oldest.level - newest.level;

    // Calculate time elapsed in minutes
    const timeElapsedMinutes = (newest.timestamp - oldest.timestamp) / 1000 / 60;

    if (timeElapsedMinutes === 0) {
      return 0;
    }

    // Calculate drain rate per hour
    const drainRatePerHour = (levelChange / timeElapsedMinutes) * 60;

    return Math.max(0, Math.round(drainRatePerHour * 10) / 10);
  }

  /**
   * Determine battery health based on drain rate and level
   * @param drainRate Battery drain rate (percent per hour)
   * @param _level Current battery level percentage
   * @returns Battery health: 'good', 'fair', or 'poor'
   */
  private determineBatteryHealth(drainRate: number, _level: number): 'good' | 'fair' | 'poor' {
    // Drain rate > 20% per hour indicates poor health
    if (drainRate > 20) {
      return 'poor';
    }

    // Drain rate > 10% per hour indicates fair health
    if (drainRate > 10) {
      return 'fair';
    }

    // Normal drain rate indicates good health
    return 'good';
  }

  /**
   * Add data point to history for drain rate calculation
   * @param level Battery level percentage
   */
  private addDataPoint(level: number): void {
    this.dataPoints.push({
      timestamp: Date.now(),
      level,
    });

    // Keep only recent data points
    if (this.dataPoints.length > this.maxDataPoints) {
      this.dataPoints.shift();
    }
  }

  /**
   * Create default metrics when Battery Manager is unavailable
   * @returns Default metrics object
   */
  private createDefaultMetrics(): Record<string, unknown> {
    // Try to detect battery level from system (fallback methods)
    const level = this.estimateBatteryLevel();

    return {
      levelPercent: level,
      charging: false,
      timeRemaining: 0,
      timeToFullCharge: 0,
      drainRate: 0,
      health: 'good',
      alert: level < 20,
    };
  }

  /**
   * Estimate battery level using fallback methods
   * @returns Estimated battery level percentage
   */
  private estimateBatteryLevel(): number {
    // If we have historical data, use the most recent
    if (this.dataPoints.length > 0) {
      return this.dataPoints[this.dataPoints.length - 1].level;
    }

    // Default to 100% if no data available
    return 100;
  }

  /**
   * Create error metrics object
   * @returns Error metrics object
   */
  private createErrorMetrics(): Record<string, unknown> {
    return {
      levelPercent: 100,
      charging: false,
      timeRemaining: 0,
      timeToFullCharge: 0,
      drainRate: 0,
      health: 'good',
      alert: false,
      error: 'Failed to collect battery metrics',
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
    this.lastLevel = 100;
    this.initializeBatteryManager();
  }

  /**
   * Shutdown monitor (cleanup)
   * @returns Promise that resolves when shut down
   */
  async shutdown(): Promise<void> {
    // Clear data on shutdown
    this.dataPoints.length = 0;
    this.batteryManager = null;
  }

  /**
   * Get current status of monitor
   * @returns Status string
   */
  getStatus(): string {
    if (this.lastLevel < 20) {
      return 'critical';
    } else if (this.lastLevel < 40) {
      return 'warning';
    } else {
      return 'healthy';
    }
  }
}

// Export for use in plugin manager
export default BatteryMonitor;
