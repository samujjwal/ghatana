/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.config;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.config.service.ConfigService;
import com.ghatana.platform.config.ConfigManager;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Generic Configuration Kernel Module.
 *
 * <p>Provides product-agnostic configuration management capabilities.
 * This module wraps the existing platform config library and integrates
 * it with the kernel framework. Contains NO finance-specific logic.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Hierarchical configuration resolution</li>
 *   <li>Multiple configuration sources (files, env vars, system props)</li>
 *   <li>Runtime configuration updates</li>
 *   <li>Configuration validation and type safety</li>
 *   <li>Tenant-specific configuration isolation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Generic configuration kernel module - hierarchical config, multiple sources, runtime updates
 * @doc.layer kernel
 * @doc.pattern Module
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class ConfigKernelModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(ConfigKernelModule.class);

    private ConfigService configService;
    private KernelContext context;

    @Override
    public String getModuleId() {
        return "config";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            KernelCapability.Core.CONFIG_MANAGEMENT
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            KernelDependency.onCapability("observability.framework")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Configuration module");
        this.context = context;

        // Initialize config service with platform config manager
        ConfigManager platformConfigManager = createPlatformConfigManager();
        this.configService = new ConfigService(context, platformConfigManager);

        // Register service with kernel context
        context.registerService(ConfigService.class, configService);

        log.info("Configuration module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Configuration module");

        return Promise.ofBlocking(() -> {
            // Start configuration service
            configService.start();

            log.info("Configuration module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Configuration module");

        return Promise.ofBlocking(() -> {
            // Stop configuration service
            if (configService != null) {
                configService.stop();
            }

            log.info("Configuration module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            boolean configHealthy = configService != null && configService.isHealthy();
            
            return configHealthy 
                ? HealthStatus.healthy("Configuration service operational")
                : HealthStatus.unhealthy("Configuration service degraded");
        } catch (Exception e) {
            log.error("Error checking configuration module health", e);
            return HealthStatus.unhealthy("Health check failed: " + e.getMessage());
        }
    }

    /**
     * Creates the platform config manager with standard sources.
     *
     * @return configured ConfigManager instance
     */
    private ConfigManager createPlatformConfigManager() {
        // Create default config manager with standard sources
        String configFilePath = System.getProperty("config.file", System.getenv("CONFIG_FILE"));
        
        ConfigManager configManager = configFilePath != null
            ? ConfigManager.createDefault("kernel-config", configFilePath)
            : ConfigManager.createDefault("kernel-config");

        log.debug("Created platform config manager with sources: {}", 
            configManager.getSources().size());

        return configManager;
    }
}
