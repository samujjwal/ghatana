/**
 * @doc.type interface
 * @doc.purpose Plugin loader configuration
 * @doc.layer product
 * @doc.pattern Configuration
 */
export interface PluginLoaderConfig {
  /** Maximum time to wait for plugin initialization (ms) */
  initTimeout?: number;
  /** Maximum time to wait for plugin shutdown (ms) */
  shutdownTimeout?: number;
  /** Enable debug logging */
  debug?: boolean;
  /** Automatically retry failed initializations */
  autoRetry?: boolean;
  /** Max retry attempts for initialization */
  maxRetries?: number;
}

/**
 * @doc.type interface
 * @doc.purpose Plugin loader for initialization and lifecycle management
 * @doc.layer product
 * @doc.pattern Loader
 * 
 * Manages plugin loading, initialization, and error recovery.
 * Coordinates with PluginRegistry and PluginLifecycleManager.
 */
export interface IPluginLoader {
  /**
   * Load and initialize a plugin
   * 
   * @param plugin - Plugin instance to load
   * @throws Error if initialization fails after retries
   */
  load(plugin: Record<string, unknown>): Promise<void>;

  /**
   * Unload and shutdown a plugin
   * 
   * @param pluginId - Plugin identifier
   * @throws Error if shutdown fails
   */
  unload(pluginId: string): Promise<void>;

  /**
   * Load multiple plugins in sequence
   * 
   * @param plugins - Array of plugin instances
   * @returns Array of load results with success/failure status
   */
  loadMultiple(
    plugins: Record<string, unknown>[],
  ): Promise<PluginLoadResult[]>;

  /**
   * Reload a plugin (shutdown then initialize)
   * 
   * @param pluginId - Plugin identifier
   * @throws Error if reload fails
   */
  reload(pluginId: string): Promise<void>;

  /**
   * Check plugin health and connectivity
   * 
   * @param pluginId - Plugin identifier
   * @returns true if plugin is healthy
   */
  isHealthy(pluginId: string): Promise<boolean>;
}

import type { IPluginRegistry } from './PluginRegistry';
import { PluginRegistry } from './PluginRegistry';
import type { IPluginLifecycleManager, PluginLifecycleState } from './PluginLifecycle';
import { PluginLifecycleManager } from './PluginLifecycle';

/**
 * @doc.type interface
 * @doc.purpose Result of a plugin load operation
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface PluginLoadResult {
  /** Plugin ID */
  pluginId: string;
  /** Load status */
  success: boolean;
  /** Error message if load failed */
  error?: string;
  /** Time taken to initialize (ms) */
  duration: number;
  /** Retry attempts made */
  retries: number;
}

/**
 * @doc.type class
 * @doc.purpose Default plugin loader implementation
 * @doc.layer product
 * @doc.pattern Loader
 * 
 * Handles plugin initialization with timeout, retry, and error recovery.
 * 
 * Features:
 * - Automatic retry with exponential backoff
 * - Initialization timeout protection
 * - Graceful shutdown with timeout
 * - Comprehensive error handling
 * - Health check support
 * 
 * Usage:
 * ```typescript
 * const loader = new PluginLoader(registry, lifecycle, {
 *   initTimeout: 5000,
 *   maxRetries: 3,
 *   debug: true,
 * });
 * 
 * try {
 *   await loader.load(slackPlugin);
 *   console.log('Plugin loaded successfully');
 * } catch (error) {
 *   console.error('Failed to load plugin:', error);
 * }
 * ```
 */
export class PluginLoader implements IPluginLoader {
  private config: Required<PluginLoaderConfig>;
  private registry: IPluginRegistry;
  private lifecycle: IPluginLifecycleManager;

  constructor(
    registry?: IPluginRegistry,
    lifecycle?: IPluginLifecycleManager,
    config: PluginLoaderConfig = {},
  ) {
    this.registry = registry ?? new PluginRegistry();
    this.config = {
      initTimeout: config.initTimeout ?? 30000,
      shutdownTimeout: config.shutdownTimeout ?? 10000,
      debug: config.debug ?? false,
      autoRetry: config.autoRetry ?? true,
      maxRetries: config.maxRetries ?? 3,
    };

    this.lifecycle = lifecycle ?? new PluginLifecycleManager();
  }

  async load(plugin: Record<string, unknown>): Promise<void> {
    const pluginId = plugin.id as string;
    const startTime = Date.now();

    if (!pluginId) {
      throw new Error('Plugin must have an id property');
    }

    let lastError: Error | null = null;
    let attempts = 0;

    for (attempts = 1; attempts <= this.config.maxRetries; attempts++) {
      try {
        this.debug(`Loading plugin '${pluginId}' (attempt ${attempts})`);

        // Transition to LOADING state
        this.transitionState(pluginId, 'LOADING' as PluginLifecycleState);

        // Run initialization with timeout
        await this.withTimeout(
          this.initializePlugin(plugin),
          this.config.initTimeout,
          `Plugin initialization timeout after ${this.config.initTimeout}ms`,
        );

        // Mark as enabled
        (plugin as Record<string, unknown>).enabled = true;

        // Transition to RUNNING state
        this.transitionState(pluginId, 'RUNNING' as PluginLifecycleState, { duration: Date.now() - startTime });

        this.debug(`Plugin '${pluginId}' loaded successfully in ${Date.now() - startTime}ms`);
        return;
      } catch (error) {
        lastError = error instanceof Error ? error : new Error(String(error));
        this.debug(`Plugin load attempt ${attempts} failed: ${lastError.message}`);

        if (attempts < this.config.maxRetries && this.config.autoRetry) {
          // Exponential backoff: 100ms * 2^(attempt-1)
          const delay = 100 * Math.pow(2, attempts - 1);
          await this.delay(delay);
        }
      }
    }

    // All retries exhausted
    this.transitionState(pluginId, 'ERROR' as PluginLifecycleState, { error: lastError?.message });
    throw new Error(
      `Failed to load plugin '${pluginId}' after ${attempts} attempts: ${lastError?.message}`,
    );
  }

  async unload(pluginId: string): Promise<void> {
    const plugin = this.getPlugin(pluginId);
    if (!plugin) {
      throw new Error(`Plugin '${pluginId}' not found`);
    }

    try {
      this.debug(`Unloading plugin '${pluginId}'`);
      this.transitionState(pluginId, 'STOPPING' as PluginLifecycleState);

      // Run shutdown with timeout
      await this.withTimeout(
        this.shutdownPlugin(plugin),
        this.config.shutdownTimeout,
        `Plugin shutdown timeout after ${this.config.shutdownTimeout}ms`,
      );

      // Mark as disabled
      (plugin as Record<string, unknown>).enabled = false;

      this.transitionState(pluginId, 'STOPPED' as PluginLifecycleState);
      this.debug(`Plugin '${pluginId}' unloaded successfully`);
    } catch (error) {
      this.transitionState(pluginId, 'ERROR' as PluginLifecycleState, {
        error: error instanceof Error ? error.message : String(error),
      });
      throw error;
    }
  }

  async loadMultiple(
    plugins: Record<string, unknown>[],
  ): Promise<PluginLoadResult[]> {
    const results: PluginLoadResult[] = [];

    for (const plugin of plugins) {
      const pluginId = plugin.id as string;
      const startTime = Date.now();
      let retries = 0;

      try {
        // Retry loading with counting
        let lastError: Error | null = null;
        for (retries = 0; retries < this.config.maxRetries; retries++) {
          try {
            await this.load(plugin);
            break;
          } catch (error) {
            lastError = error instanceof Error ? error : new Error(String(error));
            if (retries === this.config.maxRetries - 1) throw lastError;
          }
        }

        results.push({
          pluginId,
          success: true,
          duration: Date.now() - startTime,
          retries,
        });
      } catch (error) {
        results.push({
          pluginId,
          success: false,
          error: error instanceof Error ? error.message : String(error),
          duration: Date.now() - startTime,
          retries,
        });
      }
    }

    return results;
  }

  async reload(pluginId: string): Promise<void> {
    const plugin = this.getPlugin(pluginId);
    if (!plugin) {
      throw new Error(`Plugin '${pluginId}' not found`);
    }

    await this.unload(pluginId);
    await this.load(plugin);
  }

  async isHealthy(pluginId: string): Promise<boolean> {
    const plugin = this.getPlugin(pluginId);
    if (!plugin) {
      return false;
    }

    try {
      // Check if plugin has health check method
      if (typeof (plugin as Record<string, unknown>).isAvailable === 'function') {
        return await ((plugin as Record<string, unknown>).isAvailable as () => Promise<boolean>)();
      }

      // Check enabled flag as fallback
      return (plugin as Record<string, unknown>).enabled === true;
    } catch {
      return false;
    }
  }

  /**
   * Initialize plugin by calling its initialize method
   * 
   * @private
   */
  private async initializePlugin(plugin: Record<string, unknown>): Promise<void> {
    if (typeof (plugin as Record<string, unknown>).initialize === 'function') {
      await ((plugin as Record<string, unknown>).initialize as () => Promise<void>)();
    }
  }

  /**
   * Shutdown plugin by calling its shutdown method
   * 
   * @private
   */
  private async shutdownPlugin(plugin: Record<string, unknown>): Promise<void> {
    if (typeof (plugin as Record<string, unknown>).shutdown === 'function') {
      await ((plugin as Record<string, unknown>).shutdown as () => Promise<void>)();
    }
  }

  /**
   * Get plugin from registry
   * 
   * @private
   */
  private getPlugin(pluginId: string): Record<string, unknown> | null {
    return this.registry.getPlugin(pluginId);
  }

  /**
   * Transition plugin state via lifecycle manager
   * 
   * @private
   */
  private transitionState(
    pluginId: string,
    state: PluginLifecycleState,
    metadata?: Record<string, unknown>,
  ): void {
    try {
      this.lifecycle.transitionState(pluginId, state, undefined, metadata);
    } catch {
      if (this.config.debug) {
        console.warn(`Failed to transition plugin '${pluginId}' to state '${state}'`);
      }
    }
  }

  /**
   * Execute promise with timeout protection
   * 
   * @private
   */
  private withTimeout<T>(
    promise: Promise<T>,
    timeout: number,
    message: string,
  ): Promise<T> {
    return Promise.race([
      promise,
      new Promise<T>((_, reject) =>
        setTimeout(() => reject(new Error(message)), timeout),
      ),
    ]);
  }

  /**
   * Delay for specified milliseconds
   * 
   * @private
   */
  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Debug log if enabled
   * 
   * @private
   */
  private debug(message: string): void {
    if (this.config.debug) {
      console.log(`[PluginLoader] ${message}`);
    }
  }
}
