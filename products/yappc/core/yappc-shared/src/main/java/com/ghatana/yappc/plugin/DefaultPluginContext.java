package com.ghatana.yappc.plugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of PluginContext.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles default plugin context operations
 * @doc.layer core
 * @doc.pattern Implementation
*/
public final class DefaultPluginContext implements PluginContext {
    
    private final Map<String, Object> configuration;
    private final String yappcVersion;
    private final String pluginDirectory;
    
    private DefaultPluginContext(Builder builder) {
        this.configuration = Map.copyOf(builder.configuration);
        this.yappcVersion = builder.yappcVersion;
        this.pluginDirectory = builder.pluginDirectory;
    }
    
    @Override
    public Map<String, Object> getConfiguration() {
        return configuration;
    }
    
    @Override
    public Object getConfigValue(String key) {
        return configuration.get(key);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getConfigValue(String key, T defaultValue) {
        Object value = configuration.get(key);
        return value != null ? (T) value : defaultValue;
    }
    
    @Override
    public String getYappcVersion() {
        return yappcVersion;
    }
    
    @Override
    public String getPluginDirectory() {
        return pluginDirectory;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private final Map<String, Object> configuration = new HashMap<>();
        private String yappcVersion = "1.0.0";
        private String pluginDirectory = "./plugins";
        
        public Builder configuration(Map<String, Object> configuration) {
            this.configuration.putAll(configuration);
            return this;
        }
        
        public Builder configValue(String key, Object value) {
            this.configuration.put(key, value);
            return this;
        }
        
        public Builder yappcVersion(String yappcVersion) {
            this.yappcVersion = yappcVersion;
            return this;
        }
        
        public Builder pluginDirectory(String pluginDirectory) {
            this.pluginDirectory = pluginDirectory;
            return this;
        }
        
        public DefaultPluginContext build() {
            return new DefaultPluginContext(this);
        }
    }
}
