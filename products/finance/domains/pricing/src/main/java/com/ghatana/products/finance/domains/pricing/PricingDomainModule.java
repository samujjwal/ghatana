/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.pricing;

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
 * Pricing Domain Module.
 *
 * <p>Finance-specific domain module handling asset pricing, valuation,
 * and pricing model management. This is a PRODUCT module that uses kernel
 * capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Asset valuation and pricing</li>
 *   <li>Pricing model management</li>
 *   <li>Yield curve construction</li>
 *   <li>Discount factor calculation</li>
 *   <li>Volatility surface modeling</li>
 *   <li>Price discovery and validation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Pricing domain - asset valuation, pricing models
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class PricingDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(PricingDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-pricing";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.asset.pricing",
                "Asset Pricing",
                "Asset valuation and pricing calculation",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "pricing", "product", "finance")
            ),
            new KernelCapability(
                "finance.pricing.models",
                "Pricing Models",
                "Financial pricing model management and execution",
                KernelCapability.CapabilityType.AI_ML,
                Map.of("domain", "pricing", "product", "finance")
            ),
            new KernelCapability(
                "finance.valuation.engine",
                "Valuation Engine",
                "Portfolio and position valuation calculations",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "pricing", "product", "finance")
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
            KernelDependency.onCapability("finance.market.data")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Pricing Domain module");
        this.context = context;
        log.info("Pricing Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Pricing Domain module");
        
            started = true;
            log.info("Pricing Domain module started successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Pricing Domain module");
        
            started = false;
            log.info("Pricing Domain module stopped successfully");
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Pricing Domain module operational")
            : HealthStatus.unhealthy("Pricing Domain module not started");
    }
}
