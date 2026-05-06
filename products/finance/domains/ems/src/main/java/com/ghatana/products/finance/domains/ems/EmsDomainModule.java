/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.ems;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.platform.health.HealthStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Execution Management System (EMS) Domain Module.
 *
 * <p>Finance-specific domain module handling trade execution,
 * market connectivity, and execution algorithms. This is a PRODUCT module
 * that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Trade execution and routing</li>
 *   <li>Market connectivity management</li>
 *   <li>Execution algorithms (VWAP, TWAP, etc.)</li>
 *   <li>Smart order routing</li>
 *   <li>Execution quality analysis</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance EMS domain - trade execution and market connectivity
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class EmsDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(EmsDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-ems";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.execution.management",
                "Execution Management",
                "Finance-specific trade execution and routing",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "ems", "product", "finance")
            ),
            new KernelCapability(
                "finance.market.connectivity",
                "Market Connectivity",
                "Exchange and market connectivity management",
                KernelCapability.CapabilityType.INTEGRATION,
                Map.of("domain", "ems", "product", "finance")
            ),
            KernelCapability.Core.EVENT_PROCESSING,
            KernelCapability.Core.DATA_STORAGE,
            KernelCapability.Core.RESILIENCE_PATTERNS
        );
    }

    @Override
    public Set<KernelDependency> getDependencies() {
        return Set.of(
            KernelDependency.onCapability("user.authentication"),
            KernelDependency.onCapability("event.processing"),
            KernelDependency.onCapability("data.storage"),
            KernelDependency.onCapability("config.management"),
            KernelDependency.onCapability("observability.framework"),
            KernelDependency.onCapability("finance.order.management")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing EMS Domain module");
        this.context = context;
        log.info("EMS Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting EMS Domain module");

            started = true;
            log.info("EMS Domain module started successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping EMS Domain module");

            started = false;
            log.info("EMS Domain module stopped successfully");
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("EMS Domain module operational")
            : HealthStatus.unhealthy("EMS Domain module not started");
    }
}
