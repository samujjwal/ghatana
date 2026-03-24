package com.ghatana.yappc.plugin;

import java.util.Set;

/**
 * Capabilities declared by a plugin.
 *
 * @author YAPPC Team
 * @version 1.0.0
 * @since 1.0.0
 
 * @doc.type class
 * @doc.purpose Handles plugin capabilities operations
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public final class PluginCapabilities {
    
    private final Set<String> supportedOperations;
    private final Set<String> requiredServices;
    private final boolean supportsHotReload;
    
    private PluginCapabilities(Builder builder) {
        this.supportedOperations = Set.copyOf(builder.supportedOperations);
        this.requiredServices = Set.copyOf(builder.requiredServices);
        this.supportsHotReload = builder.supportsHotReload;
    }
    
    public Set<String> getSupportedOperations() {
        return supportedOperations;
    }
    
    public Set<String> getRequiredServices() {
        return requiredServices;
    }
    
    public boolean isSupportsHotReload() {
        return supportsHotReload;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private Set<String> supportedOperations = Set.of();
        private Set<String> requiredServices = Set.of();
        private boolean supportsHotReload = false;
        
        public Builder supportedOperations(Set<String> operations) {
            this.supportedOperations = operations;
            return this;
        }
        
        public Builder requiredServices(Set<String> services) {
            this.requiredServices = services;
            return this;
        }
        
        public Builder supportsHotReload(boolean supportsHotReload) {
            this.supportsHotReload = supportsHotReload;
            return this;
        }
        
        public PluginCapabilities build() {
            return new PluginCapabilities(this);
        }
    }
}
