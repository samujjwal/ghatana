package com.ghatana.servicemanager.config;

import com.ghatana.servicemanager.service.ServiceManager;
import com.ghatana.servicemanager.service.ServiceManagerImpl;
import com.ghatana.servicemanager.service.ServiceConfiguration;

import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

import java.util.List;
import java.util.Map;

/**
 * ActiveJ DI Module for Service Manager.
 * 
 * Provides dependency injection configuration for the service manager
 * including ServiceConfig and ServiceManager instances.
 * 
 * @doc.type class
 * @doc.purpose DI configuration module
 * @doc.layer orchestration
 * @doc.pattern Dependency Injection Module
 */
public class ServiceManagerConfig extends AbstractModule {
    
    @Provides
    ServiceConfig serviceConfig() {
        // Build service configuration from environment and defaults
        return ServiceConfig.builder()
                .addServices(getDefaultServices())
                .addEnvs(getEnvironmentVariables())
                .addFeatureFlags(getDefaultFeatureFlags())
                .build();
    }
    
    @Provides
    ServiceManager serviceManager(ServiceConfig config) {
        return new ServiceManagerImpl(config);
    }
    
    /**
     * Get default service configurations.
     */
    private List<ServiceConfiguration> getDefaultServices() {
        String enabledServices = System.getenv().getOrDefault(
            "AEP_ENABLED_SERVICES", 
            "detection-engine,learning-system,pattern-management"
        );
        
        return List.of(enabledServices.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::createServiceConfiguration)
                .toList();
    }
    
    /**
     * Create service configuration from service name.
     */
    private ServiceConfiguration createServiceConfiguration(String serviceName) {
        return ServiceConfiguration.builder()
                .name(serviceName)
                .enabled(true)
                .build();
    }
    
    /**
     * Get environment variables to pass to services.
     */
    private Map<String, String> getEnvironmentVariables() {
        return Map.of(
            "AEP_ENV", System.getenv().getOrDefault("AEP_ENV", "development"),
            "LOG_LEVEL", System.getenv().getOrDefault("LOG_LEVEL", "INFO"),
            "METRICS_ENABLED", System.getenv().getOrDefault("METRICS_ENABLED", "true")
        );
    }
    
    /**
     * Get default feature flags.
     */
    private Map<String, Boolean> getDefaultFeatureFlags() {
        return Map.of(
            "metrics.enabled", true,
            "tracing.enabled", true,
            "health.checks.enabled", true
        );
    }
}
