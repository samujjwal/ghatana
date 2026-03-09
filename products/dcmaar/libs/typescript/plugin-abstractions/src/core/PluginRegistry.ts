/**
 * @doc.type interface
 * @doc.purpose Plugin registry for discovery and metadata
 * @doc.layer product
 * @doc.pattern Registry
 * 
 * Central registry for managing plugin metadata, versions, and discovery.
 * Provides search, filtering, and version management capabilities.
 */
export interface IPluginRegistry {
  /**
   * Register a plugin with the registry
   * 
   * @param plugin - Plugin instance implementing IPlugin
   * @throws Error if plugin with same ID already registered
   */
  register(plugin: Record<string, unknown>): void;

  /**
   * Unregister a plugin from the registry
   * 
   * @param pluginId - Plugin identifier
   * @returns true if plugin was unregistered, false if not found
   */
  unregister(pluginId: string): boolean;

  /**
   * Get plugin by ID
   * 
   * @param pluginId - Plugin identifier
   * @returns Plugin instance or null if not found
   */
  getPlugin(pluginId: string): Record<string, unknown> | null;

  /**
   * Get all registered plugins
   * 
   * @returns Array of all registered plugins
   */
  getAllPlugins(): Record<string, unknown>[];

  /**
   * Search plugins by criteria
   * 
   * @param criteria - Search filter function
   * @returns Array of plugins matching criteria
   */
  search(
    criteria: (plugin: Record<string, unknown>) => boolean,
  ): Record<string, unknown>[];

  /**
   * Find plugins by type (INotification, IStorage, IDataCollector)
   * 
   * @param type - Plugin interface type name
   * @returns Array of plugins matching type
   */
  findByType(type: string): Record<string, unknown>[];

  /**
   * Get plugins by version range
   * 
   * @param pluginId - Plugin identifier
   * @param version - Semantic version (e.g., "0.1.0")
   * @returns Plugin version info
   */
  getVersion(
    pluginId: string,
  ): { version: string; compatibleVersions: string[] } | null;

  /**
   * Check if plugin is registered
   * 
   * @param pluginId - Plugin identifier
   * @returns true if plugin is registered
   */
  has(pluginId: string): boolean;

  /**
   * Get total count of registered plugins
   * 
   * @returns Number of registered plugins
   */
  count(): number;

  /**
   * Clear all plugins from registry
   */
  clear(): void;

  /**
   * Get registry statistics
   * 
   * @returns Registry metadata and statistics
   */
  getStats(): {
    totalPlugins: number;
    pluginTypes: Record<string, number>;
    versions: Record<string, string[]>;
  };
}

/**
 * @doc.type class
 * @doc.purpose In-memory plugin registry implementation
 * @doc.layer product
 * @doc.pattern Registry
 * 
 * Provides fast in-memory storage and retrieval of plugin metadata.
 * Suitable for single-process deployments. For distributed systems,
 * consider EventCloud-backed or database-backed implementations.
 * 
 * Usage:
 * ```typescript
 * const registry = new PluginRegistry();
 * registry.register(slackPlugin);
 * registry.register(memoryStoragePlugin);
 * 
 * const notificationPlugins = registry.findByType('INotification');
 * const webhookPlugin = registry.getPlugin('webhook-notifications');
 * 
 * console.log(registry.getStats());
 * ```
 */
export class PluginRegistry implements IPluginRegistry {
  private plugins: Map<string, Record<string, unknown>> = new Map();
  private typeIndex: Map<string, Set<string>> = new Map();
  private versions: Map<string, { version: string; compatibleVersions: string[] }> = new Map();

  register(plugin: Record<string, unknown>): void {
    const id = plugin.id as string;

    if (!id) {
      throw new Error('Plugin must have an id property');
    }

    if (this.plugins.has(id)) {
      throw new Error(`Plugin with id '${id}' is already registered`);
    }

    // Store plugin
    this.plugins.set(id, plugin);

    // Index by type (detect interface implementation)
    const pluginKeys = Object.keys(plugin);
    const interfacePatterns = ['send', 'notify']; // INotification
    const storagePatterns = ['set', 'get', 'delete', 'exists']; // IStorage
    const collectorPatterns = ['collect', 'start']; // IDataCollector

    let type = 'IPlugin'; // Default

    if (pluginKeys.some(k => interfacePatterns.includes(k))) {
      type = 'INotification';
    } else if (pluginKeys.some(k => storagePatterns.includes(k))) {
      type = 'IStorage';
    } else if (pluginKeys.some(k => collectorPatterns.includes(k))) {
      type = 'IDataCollector';
    }

    // Update type index
    if (!this.typeIndex.has(type)) {
      this.typeIndex.set(type, new Set());
    }
    this.typeIndex.get(type)!.add(id);

    // Store version info
    const version = (plugin.version as string) || '0.0.0';
    this.versions.set(id, {
      version,
      compatibleVersions: [version],
    });
  }

  unregister(pluginId: string): boolean {
    if (!this.plugins.has(pluginId)) {
      return false;
    }

    // Remove from main storage
    this.plugins.delete(pluginId);

    // Remove from type index
    for (const typeSet of this.typeIndex.values()) {
      typeSet.delete(pluginId);
    }

    // Remove version info
    this.versions.delete(pluginId);

    return true;
  }

  getPlugin(pluginId: string): Record<string, unknown> | null {
    return this.plugins.get(pluginId) ?? null;
  }

  getAllPlugins(): Record<string, unknown>[] {
    return Array.from(this.plugins.values());
  }

  search(
    criteria: (plugin: Record<string, unknown>) => boolean,
  ): Record<string, unknown>[] {
    return this.getAllPlugins().filter(criteria);
  }

  findByType(type: string): Record<string, unknown>[] {
    const pluginIds = this.typeIndex.get(type) ?? new Set();
    return Array.from(pluginIds)
      .map(id => this.plugins.get(id)!)
      .filter(Boolean);
  }

  getVersion(
    pluginId: string,
  ): { version: string; compatibleVersions: string[] } | null {
    return this.versions.get(pluginId) ?? null;
  }

  has(pluginId: string): boolean {
    return this.plugins.has(pluginId);
  }

  count(): number {
    return this.plugins.size;
  }

  clear(): void {
    this.plugins.clear();
    this.typeIndex.clear();
    this.versions.clear();
  }

  getStats(): {
    totalPlugins: number;
    pluginTypes: Record<string, number>;
    versions: Record<string, string[]>;
  } {
    const pluginTypes: Record<string, number> = {};
    for (const [type, pluginIds] of this.typeIndex.entries()) {
      pluginTypes[type] = pluginIds.size;
    }

    const versions: Record<string, string[]> = {};
    for (const [pluginId, versionInfo] of this.versions.entries()) {
      versions[pluginId] = versionInfo.compatibleVersions;
    }

    return {
      totalPlugins: this.plugins.size,
      pluginTypes,
      versions,
    };
  }
}
