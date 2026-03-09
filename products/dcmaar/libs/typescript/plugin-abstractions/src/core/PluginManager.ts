/**
 * @doc.type interface
 * @doc.purpose High-level plugin management API
 * @doc.layer product
 * @doc.pattern Facade
 * 
 * Combines PluginRegistry, PluginLifecycleManager, and PluginLoader
 * into a unified plugin management interface.
 */
export interface IPluginManager {
  /**
   * Register and initialize a plugin
   * 
   * @param plugin - Plugin instance
   * @param options - Installation options (e.g. force replace)
   * @throws Error if registration or initialization fails
   */
  installPlugin(plugin: Record<string, unknown>, options?: { force?: boolean }): Promise<void>;

  /**
   * Unload and unregister a plugin
   * 
   * @param pluginId - Plugin identifier
   * @throws Error if unload or unregistration fails
   */
  uninstallPlugin(pluginId: string): Promise<void>;

  /**
   * Get a registered plugin
   * 
   * @param pluginId - Plugin identifier
   * @returns Plugin instance or null if not found
   */
  getPlugin(pluginId: string): Record<string, unknown> | null;

  /**
   * Get all registered plugins
   * 
   * @returns Array of plugin instances
   */
  getAllPlugins(): Record<string, unknown>[];

  /**
   * Get plugin state
   * 
   * @param pluginId - Plugin identifier
   * @returns Current lifecycle state
   */
  getPluginState(pluginId: string): string | null;

  /**
   * Check if plugin is running
   * 
   * @param pluginId - Plugin identifier
   * @returns true if plugin is in RUNNING state
   */
  isPluginRunning(pluginId: string): boolean;

  /**
   * Reload a plugin (shutdown then reinitialize)
   * 
   * @param pluginId - Plugin identifier
   * @throws Error if reload fails
   */
  reloadPlugin(pluginId: string): Promise<void>;

  /**
   * Check plugin health
   * 
   * @param pluginId - Plugin identifier
   * @returns true if plugin is healthy
   */
  checkPluginHealth(pluginId: string): Promise<boolean>;

  /**
   * Get all plugins of a specific type
   * 
   * @param type - Plugin type to search for
   * @returns Array of plugins matching type
   */
  getPluginsByType(type: string): Record<string, unknown>[];

  /**
   * Get manager statistics
   * 
   * @returns Statistics object with counts and status info
   */
  getStatistics(): PluginManagerStats;
}

import type { IPluginRegistry } from './PluginRegistry';
import type { IPluginLifecycleManager } from './PluginLifecycle';
import type { IPluginLoader } from './PluginLoader';

/**
 * @doc.type interface
 * @doc.purpose Plugin manager statistics
 * @doc.layer product
 * @doc.pattern Value Object
 */
export interface PluginManagerStats {
  /** Total registered plugins */
  totalPlugins: number;
  /** Plugins in RUNNING state */
  runningPlugins: number;
  /** Plugins in ERROR state */
  errorPlugins: number;
  /** Plugins in other states */
  otherPlugins: number;
  /** Breakdown by type */
  byType: Record<string, number>;
  /** Health status */
  unhealthyPlugins: number;
}

/**
 * @doc.type class
 * @doc.purpose Unified plugin management facade
 * @doc.layer product
 * @doc.pattern Facade
 * 
 * High-level API combining:
 * - PluginRegistry: Discovery and metadata
 * - PluginLifecycleManager: State management
 * - PluginLoader: Initialization and shutdown
 * 
 * Features:
 * - Plugin installation with automatic registration and initialization
 * - Plugin uninstallation with cleanup
 * - Health monitoring
 * - Statistics and reporting
 * - Type-based discovery
 * 
 * Usage:
 * ```typescript
 * const manager = new PluginManager(registry, lifecycle, loader);
 * 
 * // Install plugin
 * await manager.installPlugin(slackPlugin);
 * 
 * // Check status
 * const isRunning = manager.isPluginRunning('slack-notifications');
 * 
 * // Get statistics
 * const stats = manager.getStatistics();
 * console.log(`Running ${stats.runningPlugins} plugins`);
 * 
 * // Reload if needed
 * await manager.reloadPlugin('slack-notifications');
 * ```
 */
export class PluginManager implements IPluginManager {
  private registry: IPluginRegistry;
  private lifecycle: IPluginLifecycleManager;
  private loader: IPluginLoader;

  constructor(
    registry: IPluginRegistry,
    lifecycle: IPluginLifecycleManager,
    loader: IPluginLoader,
  ) {
    this.registry = registry;
    this.lifecycle = lifecycle;
    this.loader = loader;
  }

  async installPlugin(plugin: Record<string, unknown>): Promise<void> {
    const pluginId = plugin.id as string;

    if (!pluginId) {
      throw new Error('Plugin must have an id property');
    }

    // If already registered, respect options.force to replace
    if (this.getPlugin(pluginId)) {
      // If caller requested force, uninstall first (best-effort)
      // Note: uninstallPlugin will attempt to unload via loader and unregister
      if ((arguments[1] as { force?: boolean } | undefined)?.force) {
        try {
          await this.uninstallPlugin(pluginId);
        } catch (err) {
          // Continue - we'll attempt to re-register regardless
          if (typeof console !== 'undefined' && console.warn) {
            console.warn(`Failed to uninstall existing plugin '${pluginId}' during force install: ${(err as Error).message}`);
          }
        }
      } else {
        if (typeof console !== 'undefined' && console.warn) {
          console.warn(`Plugin '${pluginId}' is already registered - skipping installation`);
        }
        return;
      }
    }

    // Register in registry
    this.registerPlugin(plugin);

    // Initialize via loader
    try {
      await this.loader.load(plugin);
    } catch (error) {
      // Unregister on failure
      this.unregisterPlugin(pluginId);
      throw error;
    }
  }

  async uninstallPlugin(pluginId: string): Promise<void> {
    const plugin = this.getPlugin(pluginId);
    if (!plugin) {
      throw new Error(`Plugin '${pluginId}' not found`);
    }

    // Shutdown via loader
    try {
      await this.loader.unload(pluginId);
    } catch {
      // Continue with unregistration even if unload fails
    }

    // Unregister from registry
    this.unregisterPlugin(pluginId);
  }

  getPlugin(pluginId: string): Record<string, unknown> | null {
    return this.registry.getPlugin(pluginId);
  }

  getAllPlugins(): Record<string, unknown>[] {
    return this.registry.getAllPlugins();
  }

  getPluginState(pluginId: string): string | null {
    return this.lifecycle.getState(pluginId);
  }

  isPluginRunning(pluginId: string): boolean {
    const state = this.getPluginState(pluginId);
    return state === 'RUNNING';
  }

  async reloadPlugin(pluginId: string): Promise<void> {
    await this.loader.reload(pluginId);
  }

  async checkPluginHealth(pluginId: string): Promise<boolean> {
    return await this.loader.isHealthy(pluginId);
  }

  getPluginsByType(type: string): Record<string, unknown>[] {
    return this.registry.findByType(type);
  }

  getStatistics(): PluginManagerStats {
    const allPlugins = this.getAllPlugins();
    const stats: PluginManagerStats = {
      totalPlugins: allPlugins.length,
      runningPlugins: 0,
      errorPlugins: 0,
      otherPlugins: 0,
      byType: {},
      unhealthyPlugins: 0,
    };

    for (const plugin of allPlugins) {
      const pluginId = plugin.id as string;
      const state = this.getPluginState(pluginId);
      const type = (plugin as Record<string, unknown>).type as string;

      // Count by state
      if (state === 'RUNNING') {
        stats.runningPlugins++;
      } else if (state === 'ERROR') {
        stats.errorPlugins++;
      } else {
        stats.otherPlugins++;
      }

      // Count by type
      if (type) {
        stats.byType[type] = (stats.byType[type] || 0) + 1;
      }

      // Track unhealthy (not running)
      if (state !== 'RUNNING') {
        stats.unhealthyPlugins++;
      }
    }

    return stats;
  }

  /**
   * Register plugin in registry
   * 
   * @private
   */
  private registerPlugin(plugin: Record<string, unknown>): void {
    this.registry.register(plugin);
  }

  /**
   * Unregister plugin from registry
   * 
   * @private
   */
  private unregisterPlugin(pluginId: string): void {
    this.registry.unregister(pluginId);
  }
}
