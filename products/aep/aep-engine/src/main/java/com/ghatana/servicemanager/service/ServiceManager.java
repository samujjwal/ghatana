package com.ghatana.servicemanager.service;

import java.util.List;
import java.util.Set;

/**
 * Service Manager interface for orchestrating AEP services.
 * 
 * Manages the lifecycle of multiple AEP services based on
 * configuration and feature flags.
 * 
 * @doc.type interface
 * @doc.purpose Service orchestration interface
 * @doc.layer orchestration
 * @doc.pattern Service
 */
public interface ServiceManager {

    /**
     * Starts all enabled services based on configuration.
     */
    void startEnabledServices() throws Exception;

    /**
     * Starts a specific service by name.
     * 
     * @param serviceName the name of the service to start
     */
    void startService(String serviceName) throws Exception;

    /**
     * Stops a specific service by name.
     * 
     * @param serviceName the name of the service to stop
     */
    void stopService(String serviceName) throws Exception;

    /**
     * Shuts down all running services.
     */
    void shutdownAll() throws Exception;

    /**
     * Gets the list of currently running services.
     * 
     * @return set of running service names
     */
    Set<String> getRunningServices();

    /**
     * Gets the list of all configured services.
     * 
     * @return list of all service configurations
     */
    List<ServiceConfiguration> getAllServices();

    /**
     * Checks if a service is enabled in configuration.
     * 
     * @param serviceName the service name to check
     * @return true if enabled, false otherwise
     */
    boolean isServiceEnabled(String serviceName);

    /**
     * Gets service status information.
     * 
     * @param serviceName the service name
     * @return service status
     */
    ServiceStatus getServiceStatus(String serviceName);
}
