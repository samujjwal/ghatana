/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.sanctions;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Sanctions Domain Module.
 *
 * <p>Finance-specific domain module handling sanctions screening and compliance,
 * including watchlist screening, PEP screening, and adverse media monitoring.
 * This is a PRODUCT module that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Sanctions list screening (OFAC, UN, EU, HMT)</li>
 *   <li>Politically Exposed Person (PEP) screening</li>
 *   <li>Adverse media monitoring</li>
 *   <li>Real-time transaction screening</li>
 *   <li>Batch screening and monitoring</li>
 *   <li>Alert management and case workflow</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Sanctions domain - watchlist screening, PEP checks
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class SanctionsDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(SanctionsDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-sanctions";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.sanctions.screening",
                "Sanctions Screening",
                "Real-time and batch sanctions list screening",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "sanctions", "product", "finance")
            ),
            new KernelCapability(
                "finance.pep.screening",
                "PEP Screening",
                "Politically Exposed Person identification and screening",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "sanctions", "product", "finance")
            ),
            new KernelCapability(
                "finance.adverse.media",
                "Adverse Media Monitoring",
                "Negative news and adverse media screening",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "sanctions", "product", "finance")
            ),
            new KernelCapability(
                "finance.alert.management",
                "Alert Management",
                "Sanctions alert workflow and case management",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "sanctions", "product", "finance")
            ),
            KernelCapability.Core.DATA_STORAGE,
            KernelCapability.Core.EVENT_PROCESSING,
            KernelCapability.Core.AUDIT_LOGGING
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            KernelDependency.onCapability("user.authentication"),
            KernelDependency.onCapability("data.storage"),
            KernelDependency.onCapability("event.processing"),
            KernelDependency.onCapability("config.management"),
            KernelDependency.onCapability("observability.framework"),
            KernelDependency.onCapability("finance.reference.data"),
            KernelDependency.onCapability("finance.counterparty.data")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Sanctions Domain module");
        this.context = context;
        log.info("Sanctions Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Sanctions Domain module");
        return Promise.ofBlocking(() -> {
            started = true;
            log.info("Sanctions Domain module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Sanctions Domain module");
        return Promise.ofBlocking(() -> {
            started = false;
            log.info("Sanctions Domain module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Sanctions Domain module operational")
            : HealthStatus.unhealthy("Sanctions Domain module not started");
    }
}
