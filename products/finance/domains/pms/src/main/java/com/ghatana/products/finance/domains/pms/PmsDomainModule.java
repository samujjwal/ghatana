/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.pms;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Portfolio Management System (PMS) Domain Module.
 *
 * <p>Finance-specific domain module handling portfolio construction,
 * position management, and performance analytics. This is a PRODUCT module
 * that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Portfolio construction and optimization</li>
 *   <li>Position management and tracking</li>
 *   <li>Performance attribution</li>
 *   <li>Benchmark analysis</li>
 *   <li>Portfolio rebalancing</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance PMS domain - portfolio construction and performance analytics
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class PmsDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(PmsDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-pms";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.portfolio.management",
                "Portfolio Management",
                "Finance-specific portfolio construction and tracking",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "pms", "product", "finance")
            ),
            new KernelCapability(
                "finance.performance.analytics",
                "Performance Analytics",
                "Portfolio performance measurement and attribution",
                KernelCapability.CapabilityType.AI_ML,
                Map.of("domain", "pms", "product", "finance")
            ),
            KernelCapability.Core.DATA_STORAGE,
            KernelCapability.Core.EVENT_PROCESSING
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
            KernelDependency.onCapability("finance.order.management")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing PMS Domain module");
        this.context = context;
        log.info("PMS Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting PMS Domain module");
        return Promise.ofBlocking(() -> {
            started = true;
            log.info("PMS Domain module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping PMS Domain module");
        return Promise.ofBlocking(() -> {
            started = false;
            log.info("PMS Domain module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("PMS Domain module operational")
            : HealthStatus.unhealthy("PMS Domain module not started");
    }
}
