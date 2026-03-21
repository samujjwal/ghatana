/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.compliance;

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
 * Compliance Domain Module.
 *
 * <p>Finance-specific domain module handling regulatory compliance,
 * surveillance, and reporting. This is a PRODUCT module
 * that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Regulatory rule enforcement</li>
 *   <li>Trade surveillance</li>
 *   <li>Regulatory reporting (MiFID II, etc.)</li>
 *   <li>Best execution monitoring</li>
 *   <li>Audit trail management</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Compliance domain - regulatory compliance and surveillance
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class ComplianceDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(ComplianceDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-compliance";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.regulatory.compliance",
                "Regulatory Compliance",
                "Finance-specific regulatory rule enforcement",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "compliance", "product", "finance")
            ),
            new KernelCapability(
                "finance.trade.surveillance",
                "Trade Surveillance",
                "Real-time trade monitoring and surveillance",
                KernelCapability.CapabilityType.MONITORING,
                Map.of("domain", "compliance", "product", "finance")
            ),
            new KernelCapability(
                "finance.regulatory.reporting",
                "Regulatory Reporting",
                "Automated regulatory report generation",
                KernelCapability.CapabilityType.COMPLIANCE,
                Map.of("domain", "compliance", "product", "finance")
            ),
            KernelCapability.Core.EVENT_PROCESSING,
            KernelCapability.Core.DATA_STORAGE,
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
            KernelDependency.onCapability("finance.order.management"),
            KernelDependency.onCapability("finance.execution.management")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Compliance Domain module");
        this.context = context;
        log.info("Compliance Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Compliance Domain module");
        
            started = true;
            log.info("Compliance Domain module started successfully");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Compliance Domain module");
        
            started = false;
            log.info("Compliance Domain module stopped successfully");
        return Promise.complete();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Compliance Domain module operational")
            : HealthStatus.unhealthy("Compliance Domain module not started");
    }
}
