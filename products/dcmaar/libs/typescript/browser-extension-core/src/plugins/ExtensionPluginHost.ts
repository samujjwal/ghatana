/**
 * ExtensionPluginHost
 *
 * Composition helper that wires `@ghatana/dcmaar-plugin-abstractions` into the
 * browser-extension-core runtime for a specific extension. It uses the
 * shared PluginRegistry / PluginLifecycleManager / PluginLoader stack
 * and applies an ExtensionPluginManifest to install plugins.
 */

import {
    PluginRegistry,
    PluginLifecycleManager,
    PluginLoader,
    CorePluginManager,
} from "@ghatana/dcmaar-plugin-abstractions";
import type {
    ExtensionPluginManifest,
    ExtensionPluginDescriptor,
} from "@ghatana/dcmaar-types";

import { ExtensionConnectorBridge } from "../plugins/ExtensionConnectorBridge";

/**
 * Context passed to extension plugin factories.
 */
export interface ExtensionPluginFactoryContext {
    manifest: ExtensionPluginManifest;
    descriptor: ExtensionPluginDescriptor;
    connectorBridge: ExtensionConnectorBridge;
}

/**
 * Factory function for creating plugin instances from manifest entries.
 */
export type ExtensionPluginFactory = (
    context: ExtensionPluginFactoryContext,
) => Promise<Record<string, unknown>> | Record<string, unknown>;

/**
 * Options for constructing an ExtensionPluginHost.
 */
export interface ExtensionPluginHostOptions {
    /** Optional manifest to seed connector profiles or metadata. */
    manifest?: ExtensionPluginManifest;
    /** Optional pre-configured connector bridge instance. */
    connectorBridge?: ExtensionConnectorBridge;
}

/**
 * ExtensionPluginHost
 *
 * - Owns a PluginRegistry + PluginLifecycleManager + PluginLoader stack.
 * - Installs plugins defined in an ExtensionPluginManifest using
 *   registered factories.
 * - Exposes the underlying CorePluginManager for adapters such as the
 *   Device Health PluginSystemAdapter.
 */
export class ExtensionPluginHost {
    private readonly registry: PluginRegistry;
    private readonly lifecycle: PluginLifecycleManager;
    private readonly loader: PluginLoader;
    private readonly manager: CorePluginManager;
    private readonly connectorBridge: ExtensionConnectorBridge;
    private readonly factories: Map<string, ExtensionPluginFactory> = new Map();

    constructor(options: ExtensionPluginHostOptions = {}) {
        this.registry = new PluginRegistry();
        this.lifecycle = new PluginLifecycleManager();
        this.loader = new PluginLoader(this.registry, this.lifecycle, {
            initTimeout: 30000,
            shutdownTimeout: 10000,
            maxRetries: 3,
            autoRetry: true,
        });

        this.manager = new CorePluginManager(
            this.registry,
            this.lifecycle,
            this.loader,
        );

        this.connectorBridge =
            options.connectorBridge ??
            new ExtensionConnectorBridge({ profiles: options.manifest?.connectors ?? [] });
    }

    /**
     * Access underlying CorePluginManager for advanced integrations.
     */
    getPluginManager(): CorePluginManager {
        return this.manager;
    }

    /**
     * Access the connector bridge used by this host.
     */
    getConnectorBridge(): ExtensionConnectorBridge {
        return this.connectorBridge;
    }

    /**
     * Register a factory for a given plugin ID.
     *
     * Factories are responsible for constructing plugin instances that
     * satisfy the expectations of PluginRegistry/PluginManager.
     */
    registerFactory(pluginId: string, factory: ExtensionPluginFactory): void {
        if (!pluginId) {
            throw new Error("Plugin ID must be a non-empty string");
        }

        if (this.factories.has(pluginId)) {
            throw new Error(`Factory for plugin '${pluginId}' is already registered`);
        }

        this.factories.set(pluginId, factory);
    }

    /**
     * Install and initialize all enabled plugins from the given manifest.
     */
    async initializeFromManifest(manifest: ExtensionPluginManifest): Promise<void> {
        // Defensive checks for manifest and plugins array
        if (!manifest) {
            console.warn("[ExtensionPluginHost] Manifest is undefined");
            return;
        }

        if (!Array.isArray(manifest.plugins)) {
            console.warn("[ExtensionPluginHost] Manifest plugins is not an array", {
                manifestKeys: Object.keys(manifest),
                pluginsType: typeof manifest.plugins,
                plugins: manifest.plugins,
            });
            return;
        }

        const tasks = manifest.plugins
            .filter((descriptor: ExtensionPluginDescriptor) => descriptor.enabled !== false)
            .map(async (descriptor: ExtensionPluginDescriptor) => {
                const factory = this.factories.get(descriptor.pluginId);
                if (!factory) {
                    // No factory registered for this plugin ID; skip.
                    return;
                }

                const plugin = await factory({
                    manifest,
                    descriptor,
                    connectorBridge: this.connectorBridge,
                });

                console.log("[ExtensionPluginHost] Installing plugin:", {
                    pluginId: descriptor.pluginId,
                    pluginKeys: Object.keys(plugin as Record<string, unknown>).slice(0, 15),
                    pluginId_prop: (plugin as Record<string, unknown>).id,
                });

                await this.manager.installPlugin(plugin as Record<string, unknown>);
            });

        await Promise.all(tasks);
    }

    /**
     * Gracefully shut down all managed plugins.
     */
    async shutdown(): Promise<void> {
        const plugins = this.manager.getAllPlugins();
        const uninstallTasks = plugins.map(async (plugin: Record<string, unknown>) => {
            const id = plugin.id as string | undefined;
            if (!id) {
                return;
            }

            try {
                await this.manager.uninstallPlugin(id);
            } catch {
                // Continue with best-effort shutdown
            }
        });

        await Promise.all(uninstallTasks);
        await this.connectorBridge.shutdown();
    }
}
