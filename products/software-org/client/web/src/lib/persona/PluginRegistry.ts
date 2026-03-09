/**
 * Plugin Registry for Persona Extensions
 *
 * <p><b>Purpose</b><br>
 * Central registry for persona plugins with lifecycle management:
 * - Lazy loading of plugin components
 * - Permission-based filtering
 * - Slot-based rendering
 * - Plugin activation/deactivation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { pluginRegistry } from '@/lib/persona/PluginRegistry';
 *
 * // Register a plugin
 * pluginRegistry.register({
 *   id: 'custom-metric',
 *   type: 'metric',
 *   component: () => import('./plugins/CustomMetric'),
 * });
 *
 * // Get plugins for a slot
 * const plugins = pluginRegistry.getBySlot('dashboard-header');
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Plugin registry with lazy loading and lifecycle management
 * @doc.layer product
 * @doc.pattern Registry
 */

import type { PluginManifest } from '@/schemas/persona.schema';
import type { ComponentType } from 'react';

/**
 * Plugin component loader function
 */
export type PluginLoader = () => Promise<{ default: ComponentType<any> }>;

/**
 * Registered plugin with component loader
 */
export interface RegisteredPlugin extends Omit<PluginManifest, 'component'> {
    component?: PluginLoader;
    loadedComponent?: ComponentType<any>;
    isLoading?: boolean;
    loadError?: Error;
}

/**
 * Plugin lifecycle events
 */
export type PluginEvent = 'registered' | 'enabled' | 'disabled' | 'loaded' | 'error';

/**
 * Plugin event listener
 */
export type PluginEventListener = (plugin: RegisteredPlugin, event: PluginEvent) => void;

/**
 * Plugin Registry
 *
 * Manages plugin lifecycle with:
 * - Lazy loading for performance
 * - Permission-based access control
 * - Slot-based organization
 * - Event notifications
 */
export class PluginRegistry {
    private plugins = new Map<string, RegisteredPlugin>();
    private listeners = new Set<PluginEventListener>();

    /**
     * Register a new plugin
     *
     * @param manifest Plugin manifest
     * @param component Component loader function (lazy loaded)
     */
    register(manifest: PluginManifest, component?: PluginLoader): void {
        const plugin: RegisteredPlugin = {
            ...manifest,
            component,
        };

        this.plugins.set(manifest.id, plugin);
        this.emit(plugin, 'registered');
    }

    /**
     * Unregister a plugin
     *
     * @param pluginId Plugin ID
     */
    unregister(pluginId: string): boolean {
        return this.plugins.delete(pluginId);
    }

    /**
     * Get plugin by ID
     *
     * @param pluginId Plugin ID
     * @returns Plugin or undefined
     */
    get(pluginId: string): RegisteredPlugin | undefined {
        return this.plugins.get(pluginId);
    }

    /**
     * Get all registered plugins
     *
     * @param filter Optional filter function
     * @returns Array of plugins
     */
    getAll(filter?: (plugin: RegisteredPlugin) => boolean): RegisteredPlugin[] {
        const plugins = Array.from(this.plugins.values());
        return filter ? plugins.filter(filter) : plugins;
    }

    /**
     * Get plugins by type
     *
     * @param type Plugin type
     * @returns Array of plugins matching type
     */
    getByType(type: PluginManifest['type']): RegisteredPlugin[] {
        return this.getAll((plugin) => plugin.type === type);
    }

    /**
     * Get plugins by slot
     *
     * @param slot Slot identifier
     * @returns Array of plugins for the slot, sorted by priority
     */
    getBySlot(slot: string): RegisteredPlugin[] {
        return this.getAll((plugin) => plugin.slot === slot || plugin.slots?.includes(slot) === true).sort(
            (a, b) => b.priority - a.priority
        );
    }

    /**
     * Get enabled plugins with permission check
     *
     * @param userPermissions User's permissions
     * @param type Optional plugin type filter
     * @returns Array of enabled plugins user has access to
     */
    getEnabled(userPermissions: string[], type?: PluginManifest['type']): RegisteredPlugin[] {
        return this.getAll((plugin) => {
            // Filter by type if provided
            if (type && plugin.type !== type) return false;

            // Only enabled plugins
            if (!plugin.enabled) return false;

            // Check permissions
            return this.hasPermission(plugin, userPermissions);
        });
    }

    /**
     * Load plugin component
     *
     * @param pluginId Plugin ID
     * @returns Promise resolving to loaded component
     */
    async loadComponent(pluginId: string): Promise<ComponentType<any>> {
        const plugin = this.plugins.get(pluginId);
        if (!plugin) {
            throw new Error(`Plugin not found: ${pluginId}`);
        }

        // Return cached component if already loaded
        if (plugin.loadedComponent) {
            return plugin.loadedComponent;
        }

        // Return loading promise if in progress
        if (plugin.isLoading) {
            // Wait for current load to complete
            return new Promise((resolve, reject) => {
                const checkLoaded = setInterval(() => {
                    const current = this.plugins.get(pluginId);
                    if (current?.loadedComponent) {
                        clearInterval(checkLoaded);
                        resolve(current.loadedComponent);
                    } else if (current?.loadError) {
                        clearInterval(checkLoaded);
                        reject(current.loadError);
                    }
                }, 100);

                // Timeout after 10 seconds
                setTimeout(() => {
                    clearInterval(checkLoaded);
                    reject(new Error(`Plugin load timeout: ${pluginId}`));
                }, 10000);
            });
        }

        if (!plugin.component) {
            throw new Error(`No component loader for plugin: ${pluginId}`);
        }

        try {
            plugin.isLoading = true;
            const module = await plugin.component();
            plugin.loadedComponent = module.default;
            plugin.isLoading = false;
            this.emit(plugin, 'loaded');
            return module.default;
        } catch (error) {
            plugin.loadError = error as Error;
            plugin.isLoading = false;
            this.emit(plugin, 'error');
            throw new Error(`Failed to load plugin ${pluginId}: ${error}`);
        }
    }

    /**
     * Enable a plugin
     *
     * @param pluginId Plugin ID
     */
    enable(pluginId: string): void {
        const plugin = this.plugins.get(pluginId);
        if (plugin && !plugin.enabled) {
            plugin.enabled = true;
            this.emit(plugin, 'enabled');
        }
    }

    /**
     * Disable a plugin
     *
     * @param pluginId Plugin ID
     */
    disable(pluginId: string): void {
        const plugin = this.plugins.get(pluginId);
        if (plugin && plugin.enabled) {
            plugin.enabled = false;
            this.emit(plugin, 'disabled');
        }
    }

    /**
     * Check if plugin is enabled
     *
     * @param pluginId Plugin ID
     * @returns True if enabled
     */
    isEnabled(pluginId: string): boolean {
        return this.plugins.get(pluginId)?.enabled ?? false;
    }

    /**
     * Check if user has permission to access plugin
     *
     * @param plugin Plugin to check
     * @param userPermissions User's permissions
     * @returns True if user has access
     */
    hasPermission(plugin: RegisteredPlugin, userPermissions: string[]): boolean {
        // No permissions required
        if (!plugin.permissions || plugin.permissions.length === 0) {
            return true;
        }

        // Wildcard permission grants all
        if (userPermissions.includes('*')) {
            return true;
        }

        // Check if user has any required permission
        return plugin.permissions.some((required) => {
            if (userPermissions.includes(required)) return true;

            // Check wildcard patterns
            const requiredPrefix = required.split(':')[0];
            const wildcardPattern = `${requiredPrefix}:*`;
            return userPermissions.includes(wildcardPattern);
        });
    }

    /**
     * Add event listener
     *
     * @param listener Event listener function
     */
    on(listener: PluginEventListener): void {
        this.listeners.add(listener);
    }

    /**
     * Remove event listener
     *
     * @param listener Event listener function
     */
    off(listener: PluginEventListener): void {
        this.listeners.delete(listener);
    }

    /**
     * Emit plugin event to all listeners
     *
     * @param plugin Plugin that triggered the event
     * @param event Event type
     */
    private emit(plugin: RegisteredPlugin, event: PluginEvent): void {
        this.listeners.forEach((listener) => {
            try {
                listener(plugin, event);
            } catch (error) {
                console.error('Plugin event listener error:', error);
            }
        });
    }

    /**
     * Clear all plugins and listeners
     */
    clear(): void {
        this.plugins.clear();
        this.listeners.clear();
    }

    /**
     * Get registry statistics
     */
    getStats() {
        const plugins = Array.from(this.plugins.values());
        return {
            total: plugins.length,
            enabled: plugins.filter((p) => p.enabled).length,
            disabled: plugins.filter((p) => !p.enabled).length,
            loaded: plugins.filter((p) => p.loadedComponent).length,
            byType: plugins.reduce(
                (acc, p) => {
                    acc[p.type] = (acc[p.type] || 0) + 1;
                    return acc;
                },
                {} as Record<string, number>
            ),
        };
    }
}

/**
 * Singleton plugin registry instance
 */
export const pluginRegistry = new PluginRegistry();
