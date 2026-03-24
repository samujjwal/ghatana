package com.ghatana.yappc.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for YAPPC client.
 * 
 * <p>Provides a fluent builder API for configuring YAPPC clients.
 * 
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles yappc config operations
 * @doc.layer core
 * @doc.pattern Configuration
*/
public final class YAPPCConfig {
    
    private final String storagePlugin;
    private final String aiProvider;
    private final Map<String, Object> properties;
    
    private YAPPCConfig(Builder builder) {
        this.storagePlugin = builder.storagePlugin;
        this.aiProvider = builder.aiProvider;
        this.properties = Map.copyOf(builder.properties);
    }
    
    public String getStoragePlugin() {
        return storagePlugin;
    }
    
    public String getAiProvider() {
        return aiProvider;
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    public <T> T getProperty(String key, Class<T> type) {
        Object value = properties.get(key);
        return type.cast(value);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String storagePlugin = "memory";
        private String aiProvider = "ollama";
        private final Map<String, Object> properties = new HashMap<>();
        
        public Builder storagePlugin(String plugin) {
            this.storagePlugin = Objects.requireNonNull(plugin, "Storage plugin cannot be null");
            return this;
        }
        
        public Builder aiProvider(String provider) {
            this.aiProvider = Objects.requireNonNull(provider, "AI provider cannot be null");
            return this;
        }
        
        public Builder property(String key, Object value) {
            Objects.requireNonNull(key, "Property key cannot be null");
            this.properties.put(key, value);
            return this;
        }
        
        public Builder properties(Map<String, Object> props) {
            if (props != null) {
                this.properties.putAll(props);
            }
            return this;
        }
        
        public YAPPCConfig build() {
            return new YAPPCConfig(this);
        }
    }
}
