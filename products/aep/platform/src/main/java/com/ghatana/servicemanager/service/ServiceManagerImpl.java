package com.ghatana.servicemanager.service;

import com.ghatana.servicemanager.config.ServiceConfig;
import com.ghatana.servicemanager.process.ServiceProcess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service Manager implementation.
 * 
 * Manages AEP service processes using configuration-driven approach.
 * Supports environment variables, configuration files, and feature flags.
 * 
 * @doc.type class
 * @doc.purpose Service orchestration implementation
 * @doc.layer orchestration
 * @doc.pattern Service
 */
public class ServiceManagerImpl implements ServiceManager {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceManagerImpl.class);

    private final ServiceConfig serviceConfig;
    private final Map<String, ServiceProcess> runningServices = new ConcurrentHashMap<>();
    private final Map<String, ServiceConfiguration> serviceConfigurations = new HashMap<>();

    public ServiceManagerImpl(ServiceConfig serviceConfig) {
        this.serviceConfig = serviceConfig;
        initializeServiceConfigurations();
    }

    @Override
    public void startEnabledServices() throws Exception {
        LOG.info("Starting enabled services based on configuration");
        
        List<ServiceConfiguration> enabledServices = serviceConfig.getEnabledServices();
        
        if (enabledServices.isEmpty()) {
            LOG.warn("No services enabled in configuration");
            return;
        }

        for (ServiceConfiguration serviceConfig : enabledServices) {
            try {
                startService(serviceConfig.getName());
            } catch (Exception e) {
                LOG.error("Failed to start service: {}", serviceConfig.getName(), e);
                // If core fails to start, it's critical
                if (serviceConfig.isRequired()) {
                    LOG.error("Required service {} failed to start - aborting startup", serviceConfig.getName());
                    throw e;
                }
                // Continue starting other services for optional ones
            }
        }

        LOG.info("Service startup completed. Running services: {}", runningServices.keySet());
    }

    @Override
    public void startService(String serviceName) throws Exception {
        if (runningServices.containsKey(serviceName)) {
            LOG.warn("Service {} is already running", serviceName);
            return;
        }

        ServiceConfiguration config = serviceConfigurations.get(serviceName);
        if (config == null) {
            throw new IllegalArgumentException("Unknown service: " + serviceName);
        }

        if (!config.isEnabled()) {
            LOG.warn("Service {} is disabled in configuration", serviceName);
            return;
        }

        LOG.info("Starting service: {} on port: {}", serviceName, config.getPort());
        
        ServiceProcess process = new ServiceProcess(config);
        process.start();
        
        runningServices.put(serviceName, process);
        
        // Poll for readiness instead of blocking Thread.sleep
        boolean ready = awaitServiceReady(process, serviceName, 2000, 100);
        
        if (!ready || !process.isAlive()) {
            runningServices.remove(serviceName);
            throw new RuntimeException("Service " + serviceName + " failed to start");
        }

        LOG.info("Service {} started successfully", serviceName);
    }

    /**
     * Polls for service readiness with a timeout instead of blocking
     * the thread with Thread.sleep.
     *
     * @param process       the service process to check
     * @param serviceName   name for logging
     * @param timeoutMs     total time to wait before giving up
     * @param pollIntervalMs interval between readiness checks
     * @return true if the process is alive within the timeout
     */
    private boolean awaitServiceReady(ServiceProcess process, String serviceName,
                                      long timeoutMs, long pollIntervalMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            if (process.isAlive()) {
                return true;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.warn("Interrupted while waiting for service {} readiness", serviceName);
                return false;
            }
        }
        LOG.warn("Service {} did not become ready within {}ms", serviceName, timeoutMs);
        return false;
    }

    @Override
    public void stopService(String serviceName) throws Exception {
        ServiceProcess process = runningServices.get(serviceName);
        if (process == null) {
            LOG.warn("Service {} is not running", serviceName);
            return;
        }

        LOG.info("Stopping service: {}", serviceName);
        
        process.stop();
        runningServices.remove(serviceName);
        
        LOG.info("Service {} stopped", serviceName);
    }

    @Override
    public void shutdownAll() throws Exception {
        LOG.info("Shutting down all running services");
        
        // Create a copy to avoid concurrent modification
        Set<String> servicesToStop = new HashSet<>(runningServices.keySet());
        
        for (String serviceName : servicesToStop) {
            try {
                stopService(serviceName);
            } catch (Exception e) {
                LOG.error("Error stopping service: {}", serviceName, e);
            }
        }
        
        LOG.info("All services shut down");
    }

    @Override
    public Set<String> getRunningServices() {
        return new HashSet<>(runningServices.keySet());
    }

    @Override
    public List<ServiceConfiguration> getAllServices() {
        return new ArrayList<>(serviceConfigurations.values());
    }

    @Override
    public boolean isServiceEnabled(String serviceName) {
        ServiceConfiguration config = serviceConfigurations.get(serviceName);
        return config != null && config.isEnabled();
    }

    @Override
    public ServiceStatus getServiceStatus(String serviceName) {
        ServiceProcess process = runningServices.get(serviceName);
        if (process == null) {
            ServiceConfiguration config = serviceConfigurations.get(serviceName);
            if (config == null) {
                return ServiceStatus.UNKNOWN;
            }
            return config.isEnabled() ? ServiceStatus.STOPPED : ServiceStatus.DISABLED;
        }

        if (process.isAlive()) {
            return ServiceStatus.RUNNING;
        } else {
            runningServices.remove(serviceName);
            return ServiceStatus.FAILED;
        }
    }

    /**
     * Initializes service configurations.
     */
    private void initializeServiceConfigurations() {
        // Core service - complete event processing with integrated pattern detection
        serviceConfigurations.put("core", ServiceConfiguration.builder()
            .name("core")
            .port(7106)
            .mainClass("com.ghatana.core.CoreServiceApplication")
            .enabled(true)
            .required(true)
            .description("Complete event processing service with integrated pattern detection and management")
            .build());

        // Analytics service - enabled by feature flag or environment
        boolean analyticsEnabled = Boolean.parseBoolean(
            System.getenv().getOrDefault("ANALYTICS_ENABLED", "false")
        );
        serviceConfigurations.put("analytics", ServiceConfiguration.builder()
            .name("analytics")
            .port(7107)
            .mainClass("com.ghatana.analytics.AnalyticsServiceApplication")
            .enabled(analyticsEnabled)
            .required(false)
            .description("Advanced analytics and detection service")
            .build());

        // Learning service - enabled by feature flag or environment
        boolean learningEnabled = Boolean.parseBoolean(
            System.getenv().getOrDefault("LEARNING_ENABLED", "false")
        );
        serviceConfigurations.put("learning", ServiceConfiguration.builder()
            .name("learning")
            .port(7109)
            .mainClass("com.ghatana.learning.LearningServiceApplication")
            .enabled(learningEnabled)
            .required(false)
            .description("Machine learning and adaptation service")
            .build());

        LOG.info("Initialized {} service configurations", serviceConfigurations.size());
        LOG.info("Core service includes complete pattern detection and management capabilities");
    }
}
