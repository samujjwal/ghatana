/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.surveillance;

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
 * Surveillance Domain Module.
 *
 * <p>Finance-specific domain module handling trade surveillance and market abuse detection,
 * including cross-market surveillance, algo monitoring, and behavioral analytics.
 * This is a PRODUCT module that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Trade surveillance and monitoring</li>
 *   <li>Market abuse detection (insider trading, manipulation)</li>
 *   <li>Cross-market surveillance</li>
 *   <li>Algorithm and high-frequency trading monitoring</li>
 *   <li>Communication surveillance</li>
 *   <li>Behavioral analytics and scoring</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Surveillance domain - trade monitoring, market abuse detection
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class SurveillanceDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(SurveillanceDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-surveillance";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.trade.surveillance",
                "Trade Surveillance",
                "Real-time trade monitoring and pattern detection",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "surveillance", "product", "finance")
            ),
            new KernelCapability(
                "finance.market.abuse.detection",
                "Market Abuse Detection",
                "Insider trading and market manipulation detection",
                KernelCapability.CapabilityType.AI_ML,
                Map.of("domain", "surveillance", "product", "finance")
            ),
            new KernelCapability(
                "finance.behavioral.analytics",
                "Behavioral Analytics",
                "Trader behavior scoring and anomaly detection",
                KernelCapability.CapabilityType.AI_ML,
                Map.of("domain", "surveillance", "product", "finance")
            ),
            new KernelCapability(
                "finance.algo.monitoring",
                "Algo Monitoring",
                "Algorithmic trading surveillance and controls",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "surveillance", "product", "finance")
            ),
            KernelCapability.Core.DATA_STORAGE,
            KernelCapability.Core.EVENT_PROCESSING,
            KernelCapability.Core.OBSERVABILITY_FRAMEWORK
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
            KernelDependency.onCapability("finance.execution.management"),
            KernelDependency.onCapability("finance.market.data")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Surveillance Domain module");
        this.context = context;
        log.info("Surveillance Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Surveillance Domain module");

            started = true;
            log.info("Surveillance Domain module started successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Surveillance Domain module");

            started = false;
            log.info("Surveillance Domain module stopped successfully");
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Surveillance Domain module operational")
            : HealthStatus.unhealthy("Surveillance Domain module not started");
    }
}
