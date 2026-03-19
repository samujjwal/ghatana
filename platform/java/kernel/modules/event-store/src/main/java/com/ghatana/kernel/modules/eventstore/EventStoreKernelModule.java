/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.eventstore;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.eventstore.service.EventStoreService;
import com.ghatana.core.event.cloud.EventCloud;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Generic Event Store Kernel Module.
 *
 * <p>Provides product-agnostic event storage and streaming capabilities.
 * This module wraps the existing EventCloud platform library and integrates
 * it with the kernel framework. Contains NO finance-specific logic.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Append-only immutable event log</li>
 *   <li>Real-time event streaming and tailing</li>
 *   <li>Historical event queries and scans</li>
 *   <li>Multi-tenant event isolation</li>
 *   <li>Idempotent event publishing</li>
 *   <li>Event schema validation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Generic event store kernel module - append-only log, real-time streaming, multi-tenant isolation
 * @doc.layer kernel
 * @doc.pattern Module
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class EventStoreKernelModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(EventStoreKernelModule.class);

    private EventStoreService eventStoreService;
    private KernelContext context;

    @Override
    public String getModuleId() {
        return "event-store";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            KernelCapability.Core.EVENT_PROCESSING
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            KernelDependency.onCapability("config.management"),
            KernelDependency.onCapability("data.storage"),
            KernelDependency.onCapability("observability.framework")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Event Store module");
        this.context = context;

        // Initialize event store service with EventCloud
        EventCloud eventCloud = createEventCloud();
        this.eventStoreService = new EventStoreService(context, eventCloud);

        // Register service with kernel context
        context.registerService(EventStoreService.class, eventStoreService);
        context.registerService(EventCloud.class, eventCloud);

        log.info("Event Store module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Event Store module");

        return Promise.ofBlocking(() -> {
            // Start event store service
            eventStoreService.start();

            log.info("Event Store module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Event Store module");

        return Promise.ofBlocking(() -> {
            // Stop event store service
            if (eventStoreService != null) {
                eventStoreService.stop();
            }

            log.info("Event Store module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            boolean eventStoreHealthy = eventStoreService != null && eventStoreService.isHealthy();
            
            return eventStoreHealthy 
                ? HealthStatus.healthy("Event store service operational")
                : HealthStatus.unhealthy("Event store service degraded");
        } catch (Exception e) {
            log.error("Error checking event store module health", e);
            return HealthStatus.unhealthy("Health check failed: " + e.getMessage());
        }
    }

    /**
     * Creates the EventCloud instance.
     *
     * @return configured EventCloud instance
     */
    private EventCloud createEventCloud() {
        // For now, use in-memory implementation
        // In production, this would be configured to use PostgreSQL adapter
        EventCloud eventCloud = new com.ghatana.core.event.cloud.InMemoryEventCloud();
        
        log.debug("Created EventCloud instance: {}", eventCloud.getClass().getSimpleName());
        
        return eventCloud;
    }
}
