/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.referencedata;

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
 * Reference Data Domain Module.
 *
 * <p>Finance-specific domain module handling securities master data,
 * counterparty data, and financial instrument reference data management.
 * This is a PRODUCT module that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Securities master data management</li>
 *   <li>Counterparty and issuer data</li>
 *   <li>Financial instrument classification</li>
 *   <li>Corporate hierarchy management</li>
 *   <li>Market identifier management (ISIN, CUSIP, SEDOL, etc.)</li>
 *   <li>Reference data quality and validation</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Reference Data domain - securities master, counterparty data
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class ReferenceDataDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-reference-data";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.securities.master",
                "Securities Master Data",
                "Securities reference data management and identifiers",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "reference-data", "product", "finance")
            ),
            new KernelCapability(
                "finance.counterparty.data",
                "Counterparty Data",
                "Counterparty and issuer reference data management",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "reference-data", "product", "finance")
            ),
            new KernelCapability(
                "finance.instrument.classification",
                "Instrument Classification",
                "Financial instrument classification and taxonomy",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "reference-data", "product", "finance")
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
            KernelDependency.onCapability("observability.framework")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Reference Data Domain module");
        this.context = context;
        log.info("Reference Data Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Reference Data Domain module");
        return Promise.ofBlocking(() -> {
            started = true;
            log.info("Reference Data Domain module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Reference Data Domain module");
        return Promise.ofBlocking(() -> {
            started = false;
            log.info("Reference Data Domain module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Reference Data Domain module operational")
            : HealthStatus.unhealthy("Reference Data Domain module not started");
    }
}
