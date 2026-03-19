/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.secrets;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.secrets.service.SecretsService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Generic Secrets Management Kernel Module.
 *
 * <p>Provides product-agnostic secrets management capabilities including
 * secure storage, retrieval, and rotation of sensitive configuration data.
 * This module contains NO finance-specific logic and can be reused
 * across all products in the Ghatana ecosystem.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Secure secret storage with encryption at rest</li>
 *   <li>Secret versioning and rotation</li>
 *   <li>Tenant-scoped secret isolation</li>
 *   <li>Audit logging for secret access</li>
 *   <li>Integration with external secret providers (HashiCorp Vault, AWS Secrets Manager)</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Generic secrets management kernel module - secure storage, rotation, audit
 * @doc.layer kernel
 * @doc.pattern Module
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class SecretsKernelModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(SecretsKernelModule.class);

    private SecretsService secretsService;
    private KernelContext context;

    @Override
    public String getModuleId() {
        return "secrets";
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
        log.info("Initializing Secrets Management module");
        this.context = context;

        // Initialize secrets service
        this.secretsService = new SecretsService(context);

        // Register service with kernel context
        context.registerService(SecretsService.class, secretsService);

        log.info("Secrets Management module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Secrets Management module");

        return Promise.ofBlocking(() -> {
            // Start secrets service
            secretsService.start();

            log.info("Secrets Management module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Secrets Management module");

        return Promise.ofBlocking(() -> {
            // Stop secrets service
            if (secretsService != null) {
                secretsService.stop();
            }

            log.info("Secrets Management module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        try {
            boolean secretsHealthy = secretsService != null && secretsService.isHealthy();

            return secretsHealthy
                ? HealthStatus.healthy("Secrets service operational")
                : HealthStatus.unhealthy("Secrets service degraded");
        } catch (Exception e) {
            log.error("Error checking secrets module health", e);
            return HealthStatus.unhealthy("Health check failed: " + e.getMessage());
        }
    }
}
