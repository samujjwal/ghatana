/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.risk;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDependency;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.kernel.module.KernelModule;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Risk Management Domain Module.
 *
 * <p>Finance-specific domain module handling risk calculation,
 * limit management, and risk reporting. This is a PRODUCT module
 * that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Market risk calculation (VaR, CVaR)</li>
 *   <li>Credit risk assessment</li>
 *   <li>Operational risk tracking</li>
 *   <li>Limit management and monitoring</li>
 *   <li>Risk reporting and dashboards</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Risk domain - risk calculation and limit management
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class RiskDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(RiskDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-risk";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.risk.management",
                "Risk Management",
                "Finance-specific risk calculation and monitoring",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "risk", "product", "finance")
            ),
            new KernelCapability(
                "finance.limit.management",
                "Limit Management",
                "Trading limit and exposure management",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "risk", "product", "finance")
            ),
            KernelCapability.Core.DATA_STORAGE,
            KernelCapability.Core.EVENT_PROCESSING,
            KernelCapability.Core.AI_ML_FRAMEWORK
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
            KernelDependency.onCapability("ai.ml.framework"),
            KernelDependency.onCapability("finance.portfolio.management")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Risk Domain module");
        this.context = context;
        log.info("Risk Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Risk Domain module");
        
            started = true;
            log.info("Risk Domain module started successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Risk Domain module");
        
            started = false;
            log.info("Risk Domain module stopped successfully");
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Risk Domain module operational")
            : HealthStatus.unhealthy("Risk Domain module not started");
    }
}
