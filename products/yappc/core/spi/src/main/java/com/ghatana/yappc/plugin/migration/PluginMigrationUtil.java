package com.ghatana.yappc.plugin.migration;

import com.ghatana.yappc.plugin.YAPPCPlugin;
import com.ghatana.yappc.plugin.adapter.LegacyPluginAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Utility for migrating legacy plugins to the unified plugin system.
 * 
 * <p>Provides helper methods to:
 * <ul>
 *   <li>Discover legacy plugins</li>
 *   <li>Automatically wrap them with adapters</li>
 *   <li>Register them with the new plugin registry</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>{@code
 * // Discover and wrap all legacy plugins
 * List<YAPPCPlugin> unifiedPlugins = PluginMigrationUtil.discoverAndWrapLegacyPlugins();
 * 
 * // Register with new plugin registry
 * PluginRegistry registry = PluginRegistry.getInstance();
 * unifiedPlugins.forEach(registry::register);
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Plugin migration utilities
 * @doc.layer integration
 * @doc.pattern Utility
 */
public final class PluginMigrationUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginMigrationUtil.class);
    
    private PluginMigrationUtil() {
        // Utility class
    }
    
    /**
     * Discovers all legacy framework plugins and wraps them in adapters.
     * 
     * @return list of unified plugins (wrapped legacy plugins)
     */
    public static List<YAPPCPlugin> discoverAndWrapFrameworkPlugins() {
        List<YAPPCPlugin> plugins = new ArrayList<>();
        
        try {
            ServiceLoader<com.ghatana.yappc.framework.api.plugin.YappcPlugin> loader =
                ServiceLoader.load(com.ghatana.yappc.framework.api.plugin.YappcPlugin.class);
            
            for (com.ghatana.yappc.framework.api.plugin.YappcPlugin legacyPlugin : loader) {
                try {
                    YAPPCPlugin wrappedPlugin = new LegacyPluginAdapter(legacyPlugin);
                    plugins.add(wrappedPlugin);
                    logger.info("Wrapped legacy framework plugin: {}", legacyPlugin.getName());
                } catch (Exception e) {
                    logger.error("Failed to wrap legacy framework plugin: {}", legacyPlugin.getName(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to discover legacy framework plugins", e);
        }
        
        return plugins;
    }
    
    /**
     * Discovers and wraps all legacy plugins.
     * 
     * @return combined list of all wrapped legacy plugins
     */
    public static List<YAPPCPlugin> discoverAndWrapAllLegacyPlugins() {
        List<YAPPCPlugin> allPlugins = new ArrayList<>();
        allPlugins.addAll(discoverAndWrapFrameworkPlugins());
        
        logger.info("Discovered and wrapped {} legacy plugins", allPlugins.size());
        return allPlugins;
    }
    
    /**
     * Checks if a plugin is a legacy plugin wrapped in an adapter.
     * 
     * @param plugin the plugin to check
     * @return true if it's a wrapped legacy plugin
     */
    public static boolean isLegacyPlugin(YAPPCPlugin plugin) {
        return plugin instanceof LegacyPluginAdapter;
    }
    
    /**
     * Extracts the original legacy plugin from a wrapped plugin.
     * 
     * @param plugin the wrapped plugin
     * @return the original legacy plugin, or null if not wrapped
     */
    public static Object extractLegacyPlugin(YAPPCPlugin plugin) {
        if (plugin instanceof LegacyPluginAdapter adapter) {
            return adapter.getLegacyPlugin();
        }
        return null;
    }
}
