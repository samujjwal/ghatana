/**
 * Plugin Registry for the Learning Evidence Platform.
 *
 * Manages registration, lifecycle, and discovery of all plugins in the system.
 * Implements the Plugin Kernel architecture where all features are plugins.
 *
 * @doc.type class
 * @doc.purpose Central registry for plugin lifecycle management
 * @doc.layer core
 * @doc.pattern Registry
 */

import type {
    Plugin,
    PluginMetadata,
    PluginStatus,
    PluginRegistration,
    EvidenceProcessor,
    Ingestor,
    AssetProvider,
    AuthoringTool,
    Notifier,
    PluginType,
} from '@ghatana/tutorputor-contracts/v1/plugin-interfaces';

/**
 * Plugin categories supported by the registry.
 * Maps to PluginType from contracts.
 */
export type PluginCategory = PluginType;

/**
 * Registered plugin wrapper with additional registry metadata.
 */
interface RegisteredPlugin<T extends Plugin = Plugin> {
    /** The plugin instance */
    readonly plugin: T;
    /** Category of the plugin */
    readonly category: PluginCategory;
    /** Registration timestamp */
    readonly registeredAt: Date;
    /** Current status */
    status: PluginStatus;
    /** Optional error if initialization failed */
    initError?: Error;
}

/**
 * Plugin filter for querying the registry.
 */
export interface PluginFilter {
    /** Filter by category */
    readonly category?: PluginCategory;
    /** Filter by tag */
    readonly tag?: string;
    /** Filter by enabled state */
    readonly enabled?: boolean;
    /** Filter by minimum priority */
    readonly minPriority?: number;
}

/**
 * Event emitted when plugin state changes.
 */
export interface PluginEvent {
    readonly type: 'registered' | 'initialized' | 'destroyed' | 'error';
    readonly pluginId: string;
    readonly category: PluginCategory;
    readonly timestamp: Date;
    readonly error?: Error;
}

/**
 * Listener for plugin events.
 */
export type PluginEventListener = (event: PluginEvent) => void;

/**
 * Central registry for managing all plugins in the Learning Evidence Platform.
 *
 * Features:
 * - Type-safe plugin registration by category
 * - Lifecycle management (init, destroy)
 * - Query and filter plugins
 * - Priority-based ordering
 * - Event notifications
 *
 * @example
 * ```typescript
 * const registry = new PluginRegistry();
 *
 * // Register an evidence processor
 * registry.registerEvidenceProcessor(new CBMProcessor());
 *
 * // Initialize all plugins
 * await registry.initializeAll();
 *
 * // Query processors by tag
 * const processors = registry.getEvidenceProcessors({ tag: 'assessment' });
 * ```
 */
export class PluginRegistry {
    private readonly plugins: Map<string, RegisteredPlugin> = new Map();
    private readonly listeners: Set<PluginEventListener> = new Set();

    /**
     * Register an Evidence Processor plugin.
     *
     * @param plugin - The evidence processor to register
     * @throws Error if a plugin with the same ID is already registered
     */
    registerEvidenceProcessor(plugin: EvidenceProcessor): void {
        this.register(plugin, 'evidence_processor');
    }

    /**
     * Register an Ingestor plugin.
     *
     * @param plugin - The ingestor to register
     * @throws Error if a plugin with the same ID is already registered
     */
    registerIngestor(plugin: Ingestor): void {
        this.register(plugin, 'ingestor');
    }

    /**
     * Register an Asset Provider plugin.
     *
     * @param plugin - The asset provider to register
     * @throws Error if a plugin with the same ID is already registered
     */
    registerAssetProvider(plugin: AssetProvider): void {
        this.register(plugin, 'asset_provider');
    }

    /**
     * Register an Authoring Tool plugin.
     *
     * @param plugin - The authoring tool to register
     * @throws Error if a plugin with the same ID is already registered
     */
    registerAuthoringTool(plugin: AuthoringTool): void {
        this.register(plugin, 'authoring_tool');
    }

    /**
     * Register a Notifier plugin.
     *
     * @param plugin - The notifier to register
     * @throws Error if a plugin with the same ID is already registered
     */
    registerNotifier(plugin: Notifier): void {
        this.register(plugin, 'notifier');
    }

    /**
     * Generic registration method for any plugin type.
     */
    private register(plugin: Plugin, category: PluginCategory): void {
        const { id } = plugin.metadata;

        if (this.plugins.has(id)) {
            throw new Error(`Plugin with ID '${id}' is already registered`);
        }

        const registered: RegisteredPlugin = {
            plugin,
            category,
            registeredAt: new Date(),
            status: 'inactive',
        };

        this.plugins.set(id, registered);
        this.emit({
            type: 'registered',
            pluginId: id,
            category,
            timestamp: registered.registeredAt,
        });
    }

    /**
     * Unregister a plugin by ID.
     *
     * @param pluginId - The ID of the plugin to unregister
     * @returns true if the plugin was found and removed
     */
    async unregister(pluginId: string): Promise<boolean> {
        const registered = this.plugins.get(pluginId);
        if (!registered) {
            return false;
        }

        // Call shutdown if implemented and active
        if (registered.status === 'active' && this.hasLifecycle(registered.plugin)) {
            try {
                await registered.plugin.shutdown?.();
            } catch (error) {
                // Log but continue with unregistration
                console.warn(`Error shutting down plugin ${pluginId}:`, error);
            }
        }

        this.plugins.delete(pluginId);
        this.emit({
            type: 'destroyed',
            pluginId,
            category: registered.category,
            timestamp: new Date(),
        });

        return true;
    }

    /**
     * Initialize all registered plugins.
     * Plugins are initialized in priority order (highest first).
     *
     * @returns Array of plugin IDs that failed initialization
     */
    async initializeAll(): Promise<string[]> {
        const failed: string[] = [];
        const sorted = this.getSortedPlugins();

        for (const { pluginId, registered } of sorted) {
            if (registered.status === 'active') {
                continue;
            }

            try {
                if (this.hasLifecycle(registered.plugin)) {
                    await registered.plugin.initialize?.();
                }
                registered.status = 'active';
                this.emit({
                    type: 'initialized',
                    pluginId,
                    category: registered.category,
                    timestamp: new Date(),
                });
            } catch (error) {
                registered.initError = error instanceof Error ? error : new Error(String(error));
                registered.status = 'error';
                failed.push(pluginId);
                this.emit({
                    type: 'error',
                    pluginId,
                    category: registered.category,
                    timestamp: new Date(),
                    error: registered.initError,
                });
            }
        }

        return failed;
    }

    /**
     * Destroy all plugins and clear the registry.
     */
    async destroyAll(): Promise<void> {
        const pluginIds = Array.from(this.plugins.keys());
        for (const pluginId of pluginIds) {
            await this.unregister(pluginId);
        }
    }

    /**
     * Get a plugin by ID.
     *
     * @param pluginId - The plugin ID
     * @returns The plugin or undefined if not found
     */
    get<T extends Plugin>(pluginId: string): T | undefined {
        return this.plugins.get(pluginId)?.plugin as T | undefined;
    }

    /**
     * Get all Evidence Processor plugins, optionally filtered.
     *
     * @param filter - Optional filter criteria
     * @returns Array of matching evidence processors, sorted by priority
     */
    getEvidenceProcessors(filter?: Omit<PluginFilter, 'category'>): EvidenceProcessor[] {
        return this.getPluginsByCategory<EvidenceProcessor>('evidence_processor', filter);
    }

    /**
     * Get all Ingestor plugins, optionally filtered.
     *
     * @param filter - Optional filter criteria
     * @returns Array of matching ingestors, sorted by priority
     */
    getIngestors(filter?: Omit<PluginFilter, 'category'>): Ingestor[] {
        return this.getPluginsByCategory<Ingestor>('ingestor', filter);
    }

    /**
     * Get all Asset Provider plugins, optionally filtered.
     *
     * @param filter - Optional filter criteria
     * @returns Array of matching asset providers, sorted by priority
     */
    getAssetProviders(filter?: Omit<PluginFilter, 'category'>): AssetProvider[] {
        return this.getPluginsByCategory<AssetProvider>('asset_provider', filter);
    }

    /**
     * Get all Authoring Tool plugins, optionally filtered.
     *
     * @param filter - Optional filter criteria
     * @returns Array of matching authoring tools, sorted by priority
     */
    getAuthoringTools(filter?: Omit<PluginFilter, 'category'>): AuthoringTool[] {
        return this.getPluginsByCategory<AuthoringTool>('authoring_tool', filter);
    }

    /**
     * Get all Notifier plugins, optionally filtered.
     *
     * @param filter - Optional filter criteria
     * @returns Array of matching notifiers, sorted by priority
     */
    getNotifiers(filter?: Omit<PluginFilter, 'category'>): Notifier[] {
        return this.getPluginsByCategory<Notifier>('notifier', filter);
    }

    /**
     * Get all plugins matching a filter.
     *
     * @param filter - Filter criteria
     * @returns Array of matching plugins
     */
    getAll(filter?: PluginFilter): Plugin[] {
        return this.filterPlugins(filter).map((r) => r.registered.plugin);
    }

    /**
     * Get metadata for all registered plugins.
     *
     * @returns Array of plugin metadata with category
     */
    listAll(): Array<PluginMetadata & { category: PluginCategory }> {
        return Array.from(this.plugins.values()).map((r) => ({
            ...r.plugin.metadata,
            category: r.category,
        }));
    }

    /**
     * Check if a plugin is registered.
     *
     * @param pluginId - The plugin ID to check
     * @returns true if the plugin is registered
     */
    has(pluginId: string): boolean {
        return this.plugins.has(pluginId);
    }

    /**
     * Get the count of registered plugins.
     *
     * @param category - Optional category to count
     * @returns Number of registered plugins
     */
    count(category?: PluginCategory): number {
        if (!category) {
            return this.plugins.size;
        }
        return Array.from(this.plugins.values()).filter((r) => r.category === category).length;
    }

    /**
     * Add an event listener for plugin lifecycle events.
     *
     * @param listener - The event listener
     * @returns Unsubscribe function
     */
    addEventListener(listener: PluginEventListener): () => void {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    }

    /**
     * Get plugins by category with optional filtering.
     */
    private getPluginsByCategory<T extends Plugin>(
        category: PluginCategory,
        filter?: Omit<PluginFilter, 'category'>
    ): T[] {
        return this.filterPlugins({ ...filter, category }).map((r) => r.registered.plugin as T);
    }

    /**
     * Filter and sort plugins.
     */
    private filterPlugins(
        filter?: PluginFilter
    ): Array<{ pluginId: string; registered: RegisteredPlugin }> {
        const results: Array<{ pluginId: string; registered: RegisteredPlugin }> = [];

        for (const [pluginId, registered] of this.plugins) {
            const { metadata } = registered.plugin;

            // Category filter
            if (filter?.category && registered.category !== filter.category) {
                continue;
            }

            // Tag filter
            if (filter?.tag && !metadata.tags?.includes(filter.tag)) {
                continue;
            }

            // Enabled filter
            if (filter?.enabled !== undefined && metadata.enabled !== filter.enabled) {
                continue;
            }

            // Min priority filter
            if (filter?.minPriority !== undefined && (metadata.priority ?? 0) < filter.minPriority) {
                continue;
            }

            results.push({ pluginId, registered });
        }

        // Sort by priority (highest first)
        return results.sort(
            (a, b) => (b.registered.plugin.metadata.priority ?? 0) - (a.registered.plugin.metadata.priority ?? 0)
        );
    }

    /**
     * Get sorted plugins by priority.
     */
    private getSortedPlugins(): Array<{ pluginId: string; registered: RegisteredPlugin }> {
        return this.filterPlugins();
    }

    /**
     * Type guard for plugins with lifecycle methods.
     */
    private hasLifecycle(plugin: Plugin): plugin is Plugin & { initialize?: () => Promise<void>; shutdown?: () => Promise<void> } {
        return 'initialize' in plugin || 'shutdown' in plugin;
    }

    /**
     * Emit an event to all listeners.
     */
    private emit(event: PluginEvent): void {
        for (const listener of this.listeners) {
            try {
                listener(event);
            } catch (error) {
                console.error('Error in plugin event listener:', error);
            }
        }
    }
}

/**
 * Global singleton registry instance.
 * Use this for application-wide plugin management.
 */
export const globalRegistry = new PluginRegistry();
