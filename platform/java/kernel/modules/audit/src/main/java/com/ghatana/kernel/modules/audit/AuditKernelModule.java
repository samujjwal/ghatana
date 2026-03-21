/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.audit;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.audit.service.AuditServiceWrapper;
import com.ghatana.platform.audit.AuditService;
import io.activej.promise.Promise;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Generic Audit Kernel Module.
 *
 * <p>Provides product-agnostic audit logging capabilities.
 * This module wraps the existing audit platform library and integrates
 * it with the kernel framework. Contains NO finance-specific logic.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Audit event recording and storage</li>
 *   <li>Audit trail querying and reporting</li>
 *   <li>Multi-tenant audit isolation</li>
 *   <li>Immutable audit records</li>
 *   <li>Compliance and governance support</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Generic audit kernel module - audit logging, trail querying, compliance support
 * @doc.layer kernel
 * @doc.pattern Module
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class AuditKernelModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(AuditKernelModule.class);

    private AuditServiceWrapper auditServiceWrapper;
    private KernelContext context;

    @Override
    public String getModuleId() {
        return "audit";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            KernelCapability.Core.SECURITY_FRAMEWORK
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
        log.info("Initializing Audit module");
        this.context = context;

        // Initialize audit service wrapper with platform audit service
        AuditService platformAuditService = createPlatformAuditService();
        this.auditServiceWrapper = new AuditServiceWrapper(context, platformAuditService);

        // Register services with kernel context
        context.registerService(AuditServiceWrapper.class, auditServiceWrapper);
        context.registerService(AuditService.class, platformAuditService);

        log.info("Audit module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Audit module");

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            // Start audit service wrapper
            auditServiceWrapper.start();

            log.info("Audit module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Audit module");

        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            // Stop audit service wrapper
            if (auditServiceWrapper != null) {
                auditServiceWrapper.stop();
            }

            log.info("Audit module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            boolean auditHealthy = auditServiceWrapper != null && auditServiceWrapper.isHealthy();
            
            return auditHealthy 
                ? HealthStatus.healthy("Audit service operational")
                : HealthStatus.unhealthy("Audit service degraded");
        } catch (Exception e) {
            log.error("Error checking audit module health", e);
            return HealthStatus.unhealthy("Health check failed: " + e.getMessage());
        }
    }

    /**
     * Creates the platform audit service instance.
     *
     * @return configured AuditService instance
     */
    private AuditService createPlatformAuditService() {
        // For now, use in-memory implementation
        // In production, this would be configured to use JPA implementation
        AuditService auditService = new com.ghatana.platform.audit.InMemoryAuditQueryService();
        
        log.debug("Created AuditService instance: {}", auditService.getClass().getSimpleName());
        
        return auditService;
    }
}
