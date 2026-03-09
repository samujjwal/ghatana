/**
 * @doc.type class
 * @doc.purpose Base class for device monitoring plugins
 * @doc.layer product
 * @doc.pattern Template Method
 * 
 * Provides common functionality for all device monitors:
 * - Plugin lifecycle management (initialize, shutdown)
 * - Metrics collection abstraction
 * - Error handling with type safety
 * - Polling interval configuration
 * - Graceful shutdown with cleanup
 * 
 * @see {@link CPUMonitor}
 * @see {@link MemoryMonitor}
 * @see {@link BatteryMonitor}
 */

import { IDataCollector } from '@ghatana/dcmaar-plugin-abstractions';
import { IPlugin } from '@ghatana/dcmaar-types';

/**
 * Configuration for device monitor plugins
 * 
 * Provides polling interval and retry behavior for system metrics collection.
 */
export interface DeviceMonitorConfig {
  /** Polling interval in milliseconds */
  pollingInterval: number;
  /** Maximum retries on collection failure */
  maxRetries: number;
  /** Timeout for each collection attempt in milliseconds */
  timeout: number;
}

/**
 * Base class for device monitoring implementations
 * 
 * Implements common plugin lifecycle and provides abstract methods for metrics collection.
 * Each subclass implements the specific logic for its device type (CPU, Memory, Battery).
 * 
 * @abstract
 */
export abstract class BaseDeviceMonitor implements IDataCollector {
  // IPlugin interface properties
  readonly id: string;
  readonly name: string;
  readonly version: string = '0.1.0';
  readonly description: string;
  enabled: boolean = false;
  metadata: Record<string, unknown> = {};

  // Configuration
  protected config: DeviceMonitorConfig;

  // State
  private pollingTimer?: NodeJS.Timeout;
  private isInitialized: boolean = false;

  /**
   * Create a new device monitor instance
   * 
   * @param id - Unique identifier for the plugin
   * @param name - Human-readable name
   * @param description - Plugin description
   * @param config - Device monitor configuration
   */
  constructor(
    id: string,
    name: string,
    description: string,
    config: Partial<DeviceMonitorConfig> = {},
  ) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.config = {
      pollingInterval: config.pollingInterval ?? 5000,
      maxRetries: config.maxRetries ?? 3,
      timeout: config.timeout ?? 10000,
    };
  }

  /**
   * Initialize the device monitor
   * 
   * Implementations should:
   * - Verify system access (e.g., /proc on Linux)
   * - Set up initial state
   * - Start polling if needed
   * 
   * @throws Error if initialization fails
   */
  async initialize(): Promise<void> {
    if (this.isInitialized) {
      return;
    }

    try {
      await this.validateEnvironment();
      this.enabled = true;
      this.isInitialized = true;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      throw new Error(`Failed to initialize ${this.name}: ${message}`);
    }
  }

  /**
   * Shutdown the device monitor
   * 
   * Cleanup resources and stop polling.
   */
  async shutdown(): Promise<void> {
    if (this.pollingTimer) {
      clearInterval(this.pollingTimer);
      this.pollingTimer = undefined;
    }
    this.enabled = false;
    this.isInitialized = false;
  }

  /**
   * Execute a command (from IPlugin interface)
   * 
   * Supported commands:
   * - "collect" - Collect metrics
   * - "validate" - Validate data source
   * - "getSources" - Get available sources
   * 
   * @param command - Command name
   * @param params - Command parameters
   * @returns Command result
   * @throws Error if command is unknown
   */
  async execute(
    command: string,
    params?: Record<string, unknown>,
  ): Promise<unknown> {
    switch (command) {
      case 'collect':
        return await this.collect(String(params?.source ?? 'system'));
      case 'validate':
        return await this.validate(String(params?.source ?? 'system'));
      case 'getSources':
        return await this.getSources();
      default:
        throw new Error(`Unknown command: ${command}`);
    }
  }

  /**
   * Collect metrics from device
   * 
   * Implements retry logic with exponential backoff.
   * 
   * @param source - Data source identifier
   * @returns Promise with collected metrics
   * @throws Error if all retries fail
   */
  async collect(source: string): Promise<Record<string, unknown>> {
    if (!this.enabled) {
      throw new Error(`${this.name} is not enabled`);
    }

    for (let attempt = 1; attempt <= this.config.maxRetries; attempt++) {
      try {
        return await this.collectMetrics(source);
      } catch (error) {
        if (attempt === this.config.maxRetries) {
          const message = error instanceof Error ? error.message : String(error);
          throw new Error(`Collection failed after ${attempt} attempts: ${message}`);
        }
        // Exponential backoff: 100ms, 200ms, 400ms
        await this.delay(100 * Math.pow(2, attempt - 1));
      }
    }

    throw new Error(`Failed to collect metrics from ${source}`);
  }

  /**
   * Validate if data source is accessible
   * 
   * @param source - Data source identifier
   * @returns True if source is accessible
   */
  async validate(source: string): Promise<boolean> {
    try {
      await this.collectMetrics(source);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Get list of available data sources
   * 
   * @returns Promise with list of source identifiers
   */
  async getSources(): Promise<string[]> {
    return ['system'];
  }

  /**
   * Validate environment for this monitor
   * 
   * Called during initialization. Subclasses should check
   * for required system resources.
   * 
   * @abstract
   * @throws Error if environment is not suitable
   */
  protected abstract validateEnvironment(): Promise<void>;

  /**
   * Collect metrics from the device
   * 
   * Subclasses implement specific logic for their device type.
   * 
   * @abstract
   * @param source - Data source identifier
   * @returns Promise with collected metrics
   * @throws Error if collection fails
   */
  protected abstract collectMetrics(
    source: string,
  ): Promise<Record<string, unknown>>;

  /**
   * Helper: delay execution
   * 
   * @param ms - Milliseconds to delay
   */
  protected delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}
