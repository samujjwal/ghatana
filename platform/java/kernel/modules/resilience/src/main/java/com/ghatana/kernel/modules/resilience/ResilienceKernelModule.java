/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.resilience;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.resilience.service.ResilienceService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Generic Resilience Kernel Module.
 *
 * <p>Provides product-agnostic resilience patterns including circuit breaker,
 * retry mechanisms, bulkhead pattern, timeout management, and fallback patterns.
 * This module contains NO finance-specific logic and can be reused across all products.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Circuit breaker pattern for fault tolerance</li>
 *   <li>Retry mechanism with exponential backoff</li>
 *   <li>Bulkhead pattern for resource isolation</li>
 *   <li>Timeout management for operations</li>
 *   <li>Fallback pattern for graceful degradation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Generic resilience kernel module - circuit breaker, retry, bulkhead, timeout, fallback
 * @doc.layer kernel
 * @doc.pattern Module
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class ResilienceKernelModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(ResilienceKernelModule.class);

    private ResilienceService resilienceService;
    private KernelContext context;

    @Override
    public String getModuleId() {
        return "resilience";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            KernelCapability.Core.RESILIENCE_PATTERNS,
            KernelCapability.Core.CIRCUIT_BREAKER,
            KernelCapability.Core.RETRY_MECHANISM,
            KernelCapability.Core.BULKHEAD_PATTERN
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            KernelDependency.onCapability("config.management"),
            KernelDependency.onCapability("observability.framework")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Resilience module");
        this.context = context;

        // Initialize resilience service with generic configuration
        this.resilienceService = new ResilienceService(context);

        // Register service with kernel context
        context.registerService(ResilienceService.class, resilienceService);

        log.info("Resilience module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Resilience module");

        return Promise.ofBlocking(() -> {
            // Start resilience service
            resilienceService.start();

            log.info("Resilience module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Resilience module");

        return Promise.ofBlocking(() -> {
            // Stop resilience service
            if (resilienceService != null) {
                resilienceService.stop();
            }

            log.info("Resilience module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            boolean resilienceHealthy = resilienceService != null && resilienceService.isHealthy();

            return resilienceHealthy 
                ? HealthStatus.healthy("Resilience service operational")
                : HealthStatus.unhealthy("Resilience service degraded");
        } catch (Exception e) {
            log.error("Error checking resilience module health", e);
            return HealthStatus.unhealthy("Health check failed: " + e.getMessage());
        }
    }
}
