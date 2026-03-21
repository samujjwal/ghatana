/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.observability;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.observability.service.ObservabilityService;
import io.activej.promise.Promise;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Generic Observability Kernel Module.
 *
 * <p>Provides product-agnostic observability capabilities including
 * metrics collection, distributed tracing, and health monitoring.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Metrics collection and export (Prometheus format)</li>
 *   <li>Distributed tracing (OpenTelemetry compatible)</li>
 *   <li>Structured logging with correlation IDs</li>
 *   <li>Health checks and readiness probes</li>
 *   <li>Performance monitoring and alerting</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Generic observability kernel module - metrics, tracing, logging, health
 * @doc.layer kernel
 * @doc.pattern Module
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class ObservabilityKernelModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityKernelModule.class);

    private ObservabilityService observabilityService;
    private KernelContext context;

    @Override
    public String getModuleId() {
        return "observability";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            KernelCapability.Core.OBSERVABILITY_FRAMEWORK
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            KernelDependency.onCapability("config.management")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Observability module");
        this.context = context;

        this.observabilityService = new ObservabilityService(context);
        context.registerService(ObservabilityService.class, observabilityService);

        log.info("Observability module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Observability module");

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            observabilityService.start();
            log.info("Observability module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Observability module");

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            if (observabilityService != null) {
                observabilityService.stop();
            }
            log.info("Observability module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            boolean healthy = observabilityService != null && observabilityService.isHealthy();
            return healthy
                ? HealthStatus.healthy("Observability service operational")
                : HealthStatus.unhealthy("Observability service degraded");
        } catch (Exception e) {
            log.error("Error checking observability module health", e);
            return HealthStatus.unhealthy("Health check failed: " + e.getMessage());
        }
    }
}
