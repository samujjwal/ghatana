/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.posttrade;

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
 * Post-Trade Domain Module.
 *
 * <p>Finance-specific domain module handling post-trade processing,
 * including trade confirmation, settlement, clearing, and custody operations.
 * This is a PRODUCT module that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Trade confirmation matching</li>
 *   <li>Settlement processing and tracking</li>
 *   <li>Clearing operations</li>
 *   <li>Custody and safekeeping</li>
 *   <li>Failed trade management</li>
 *   <li>Settlement risk monitoring</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Post-Trade domain - settlement, clearing, custody
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class PostTradeDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(PostTradeDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-post-trade";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.settlement.processing",
                "Settlement Processing",
                "Trade settlement lifecycle management and tracking",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "post-trade", "product", "finance")
            ),
            new KernelCapability(
                "finance.clearing.operations",
                "Clearing Operations",
                "Trade clearing and netting operations",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "post-trade", "product", "finance")
            ),
            new KernelCapability(
                "finance.custody.management",
                "Custody Management",
                "Securities safekeeping and custody services",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "post-trade", "product", "finance")
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
            KernelDependency.onCapability("finance.execution.management")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Post-Trade Domain module");
        this.context = context;
        log.info("Post-Trade Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Post-Trade Domain module");

            started = true;
            log.info("Post-Trade Domain module started successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Post-Trade Domain module");

            started = false;
            log.info("Post-Trade Domain module stopped successfully");
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Post-Trade Domain module operational")
            : HealthStatus.unhealthy("Post-Trade Domain module not started");
    }
}
