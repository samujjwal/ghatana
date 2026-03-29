/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.regulatoryreporting;

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
 * Regulatory Reporting Domain Module.
 *
 * <p>Finance-specific domain module handling regulatory report generation,
 * including MiFID II, EMIR, SFTR, and other regulatory reporting requirements.
 * This is a PRODUCT module that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Transaction reporting (MiFID II, EMIR, SFTR)</li>
 *   <li>Position reporting</li>
 *   <li>Best execution reporting</li>
 *   <li>RTS 27/28 reporting</li>
 *   <li>Regulatory data formatting and validation</li>
 *   <li>Report submission and tracking</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Regulatory Reporting domain - MiFID II, EMIR, SFTR reports
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class RegulatoryReportingDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryReportingDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-regulatory-reporting";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.transaction.reporting",
                "Transaction Reporting",
                "MiFID II, EMIR, SFTR transaction reporting generation",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "regulatory-reporting", "product", "finance")
            ),
            new KernelCapability(
                "finance.position.reporting",
                "Position Reporting",
                "Regulatory position and holding reports",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "regulatory-reporting", "product", "finance")
            ),
            new KernelCapability(
                "finance.best.execution.reporting",
                "Best Execution Reporting",
                "RTS 27/28 best execution quality reports",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "regulatory-reporting", "product", "finance")
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
            KernelDependency.onCapability("finance.portfolio.management"),
            KernelDependency.onCapability("finance.compliance.checking")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Regulatory Reporting Domain module");
        this.context = context;
        log.info("Regulatory Reporting Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Regulatory Reporting Domain module");
        
            started = true;
            log.info("Regulatory Reporting Domain module started successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Regulatory Reporting Domain module");
        
            started = false;
            log.info("Regulatory Reporting Domain module stopped successfully");
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Regulatory Reporting Domain module operational")
            : HealthStatus.unhealthy("Regulatory Reporting Domain module not started");
    }
}
