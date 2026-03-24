package com.ghatana.yappc.plugin;

import java.util.Map;

/**
 * Context provided to plugins during initialization.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type interface
 * @doc.purpose Defines the contract for plugin context
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface PluginContext {
    
    /**
     * Gets the plugin configuration.
     *
     * @return the configuration map
     */
    Map<String, Object> getConfiguration();
    
    /**
     * Gets a configuration value.
     *
     * @param key the configuration key
     * @return the configuration value
     */
    Object getConfigValue(String key);
    
    /**
     * Gets a configuration value with a default.
     *
     * @param key the configuration key
     * @param defaultValue the default value
     * @param <T> the value type
     * @return the configuration value or default
     */
    <T> T getConfigValue(String key, T defaultValue);
    
    /**
     * Gets the YAPPC version.
     *
     * @return the YAPPC version
     */
    String getYappcVersion();
    
    /**
     * Gets the plugin directory.
     *
     * @return the plugin directory path
     */
    String getPluginDirectory();
}
