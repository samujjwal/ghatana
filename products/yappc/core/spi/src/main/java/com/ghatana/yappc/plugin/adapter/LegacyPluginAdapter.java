package com.ghatana.yappc.plugin.adapter;

import com.ghatana.yappc.plugin.*;
import io.activej.promise.Promise;

/**
 * Adapter that bridges legacy YappcPlugin interface to unified YAPPCPlugin.
 * 
 * <p>This adapter allows legacy plugins to work with the new unified plugin system
 * without requiring immediate migration. It wraps a legacy plugin and adapts its
 * methods to the new interface.
 * 
 * <p>Usage:
 * <pre>{@code
 * // Wrap legacy plugin
 * com.ghatana.yappc.framework.api.plugin.YappcPlugin legacyPlugin = ...;
 * YAPPCPlugin unifiedPlugin = new LegacyPluginAdapter(legacyPlugin);
 * 
 * // Use with new PluginRegistry
 * pluginRegistry.register(unifiedPlugin);
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Backward compatibility adapter for legacy plugins
 * @doc.layer integration
 * @doc.pattern Adapter
 */
public final class LegacyPluginAdapter implements YAPPCPlugin {
    
    private final Object legacyPlugin;
    private final PluginMetadata metadata;
    private PluginContext context;
    
    /**
     * Creates an adapter for a legacy framework plugin.
     * 
     * @param legacyPlugin the legacy plugin to adapt
     */
    public LegacyPluginAdapter(com.ghatana.yappc.framework.api.plugin.YappcPlugin legacyPlugin) {
        this.legacyPlugin = legacyPlugin;
        this.metadata = PluginMetadata.builder()
            .id(legacyPlugin.getName())
            .name(legacyPlugin.getName())
            .version(legacyPlugin.getVersion())
            .description(legacyPlugin.getDescription())
            .build();
    }
    
    @Override
    public Promise<Void> initialize(PluginContext context) {
        this.context = context;
        
        try {
            if (legacyPlugin instanceof com.ghatana.yappc.framework.api.plugin.YappcPlugin plugin) {
                // Legacy plugins don't have explicit async initialize method
                // They initialize through constructor or first use
            }
        } catch (Exception e) {
            return Promise.ofException(
                new PluginException("Failed to initialize legacy plugin: " + metadata.getId(), e));
        }
        return Promise.complete();
    }
    
    @Override
    public Promise<Void> start() {
        // Legacy plugins don't have explicit start method
        // They are considered started after initialization
        return Promise.complete();
    }
    
    @Override
    public Promise<Void> stop() {
        // Legacy plugins don't have explicit stop method
        return Promise.complete();
    }
    
    @Override
    public Promise<Void> shutdown() {
        // Legacy plugins may have cleanup in their close/dispose methods
        // But the interface doesn't define this, so we just complete
        return Promise.complete();
    }
    
    @Override
    public PluginMetadata getMetadata() {
        return metadata;
    }
    
    @Override
    public PluginCapabilities getCapabilities() {
        return PluginCapabilities.builder()
            .supportsHotReload(false) // Legacy plugins don't support hot reload
            .build();
    }
    
    @Override
    public Promise<HealthStatus> checkHealth() {
        // Legacy plugins don't have health checks
        return Promise.of(HealthStatus.healthy());
    }
    
    /**
     * Gets the wrapped legacy plugin.
     * 
     * @return the legacy plugin instance
     */
    public Object getLegacyPlugin() {
        return legacyPlugin;
    }
}
