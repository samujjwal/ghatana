package com.ghatana.servicemanager.config;

import com.ghatana.servicemanager.service.ServiceConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service configuration holder.
 * 
 * Provides configuration for service manager including enabled services,
 * feature flags, and environment-specific settings.
 * 
 * @doc.type class
 * @doc.purpose Service configuration
 * @doc.layer orchestration
 * @doc.pattern Configuration
 */
public class ServiceConfig {
    
    private final List<ServiceConfiguration> enabledServices;
    private final Map<String, String> environmentVariables;
    private final Map<String, Boolean> featureFlags;
    
    private ServiceConfig(Builder builder) {
        this.enabledServices = Collections.unmodifiableList(new ArrayList<>(builder.enabledServices));
        this.environmentVariables = Collections.unmodifiableMap(new ConcurrentHashMap<>(builder.environmentVariables));
        this.featureFlags = Collections.unmodifiableMap(new ConcurrentHashMap<>(builder.featureFlags));
    }
    
    /**
     * Returns list of enabled services.
     */
    public List<ServiceConfiguration> getEnabledServices() {
        return enabledServices;
    }
    
    /**
     * Returns environment variables.
     */
    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }
    
    /**
     * Returns feature flags.
     */
    public Map<String, Boolean> getFeatureFlags() {
        return featureFlags;
    }
    
    /**
     * Check if a feature is enabled.
     */
    public boolean isFeatureEnabled(String feature) {
        return featureFlags.getOrDefault(feature, false);
    }
    
    /**
     * Get an environment variable value.
     */
    public String getEnv(String key, String defaultValue) {
        return environmentVariables.getOrDefault(key, defaultValue);
    }
    
    /**
     * Creates a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for ServiceConfig.
     */
    public static class Builder {
        private final List<ServiceConfiguration> enabledServices = new ArrayList<>();
        private final Map<String, String> environmentVariables = new ConcurrentHashMap<>();
        private final Map<String, Boolean> featureFlags = new ConcurrentHashMap<>();
        
        public Builder addService(ServiceConfiguration service) {
            this.enabledServices.add(service);
            return this;
        }
        
        public Builder addServices(List<ServiceConfiguration> services) {
            this.enabledServices.addAll(services);
            return this;
        }
        
        public Builder addEnv(String key, String value) {
            this.environmentVariables.put(key, value);
            return this;
        }
        
        public Builder addEnvs(Map<String, String> envs) {
            this.environmentVariables.putAll(envs);
            return this;
        }
        
        public Builder addFeatureFlag(String feature, boolean enabled) {
            this.featureFlags.put(feature, enabled);
            return this;
        }
        
        public Builder addFeatureFlags(Map<String, Boolean> flags) {
            this.featureFlags.putAll(flags);
            return this;
        }
        
        public ServiceConfig build() {
            return new ServiceConfig(this);
        }
    }
}
