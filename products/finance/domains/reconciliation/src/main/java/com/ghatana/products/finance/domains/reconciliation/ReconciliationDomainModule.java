/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.reconciliation;

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
 * Reconciliation Domain Module.
 *
 * <p>Finance-specific domain module handling trade and position reconciliation,
 * including internal/external matching, break management, and exception handling.
 * This is a PRODUCT module that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Trade reconciliation matching</li>
 *   <li>Position reconciliation</li>
 *   <li>Cash reconciliation</li>
 *   <li>Break identification and management</li>
 *   <li>Exception workflow processing</li>
 *   <li>Reconciliation reporting</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Reconciliation domain - trade/position matching, breaks
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class ReconciliationDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-reconciliation";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.trade.reconciliation",
                "Trade Reconciliation",
                "Trade matching and confirmation reconciliation",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "reconciliation", "product", "finance")
            ),
            new KernelCapability(
                "finance.position.reconciliation",
                "Position Reconciliation",
                "Position and holding reconciliation across systems",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "reconciliation", "product", "finance")
            ),
            new KernelCapability(
                "finance.break.management",
                "Break Management",
                "Reconciliation break identification and resolution workflow",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "reconciliation", "product", "finance")
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
            KernelDependency.onCapability("finance.order.management"),
            KernelDependency.onCapability("finance.portfolio.management")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Reconciliation Domain module");
        this.context = context;
        log.info("Reconciliation Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Reconciliation Domain module");
        return Promise.ofBlocking(() -> {
            started = true;
            log.info("Reconciliation Domain module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Reconciliation Domain module");
        return Promise.ofBlocking(() -> {
            started = false;
            log.info("Reconciliation Domain module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Reconciliation Domain module operational")
            : HealthStatus.unhealthy("Reconciliation Domain module not started");
    }
}
