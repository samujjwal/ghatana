/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.marketdata;

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
 * Market Data Domain Module.
 *
 * <p>Finance-specific domain module handling real-time and historical market data,
 * including price feeds, market depth, and market data analytics. This is a PRODUCT
 * module that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Real-time price feed processing</li>
 *   <li>Market depth and order book management</li>
 *   <li>Historical market data storage and retrieval</li>
 *   <li>Market data normalization and validation</li>
 *   <li>Market data entitlement and permissioning</li>
 *   <li>Market data analytics and aggregation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Market Data domain - price feeds, order books, analytics
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class MarketDataDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(MarketDataDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-market-data";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.market.data.feeds",
                "Market Data Feeds",
                "Real-time price feed processing and distribution",
                KernelCapability.CapabilityType.INTEGRATION,
                Map.of("domain", "market-data", "product", "finance")
            ),
            new KernelCapability(
                "finance.order.book.management",
                "Order Book Management",
                "Market depth and order book data handling",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "market-data", "product", "finance")
            ),
            new KernelCapability(
                "finance.market.analytics",
                "Market Data Analytics",
                "Price history, volatility, and market analytics",
                KernelCapability.CapabilityType.AI_ML,
                Map.of("domain", "market-data", "product", "finance")
            ),
            KernelCapability.Core.DATA_STORAGE,
            KernelCapability.Core.EVENT_PROCESSING,
            KernelCapability.Core.RESILIENCE_PATTERNS
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
            KernelDependency.onCapability("finance.reference.data")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Market Data Domain module");
        this.context = context;
        log.info("Market Data Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Market Data Domain module");
        
            started = true;
            log.info("Market Data Domain module started successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Market Data Domain module");
        
            started = false;
            log.info("Market Data Domain module stopped successfully");
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Market Data Domain module operational")
            : HealthStatus.unhealthy("Market Data Domain module not started");
    }
}
