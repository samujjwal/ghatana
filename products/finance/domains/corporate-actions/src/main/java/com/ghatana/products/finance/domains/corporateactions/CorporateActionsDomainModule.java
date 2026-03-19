/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.domains.corporateactions;

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
 * Corporate Actions Domain Module.
 *
 * <p>Finance-specific domain module handling corporate action processing,
 * including dividends, stock splits, mergers, acquisitions, and rights offerings.
 * This is a PRODUCT module that uses kernel capabilities for generic functionality.</p>
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Dividend processing (cash and stock)</li>
 *   <li>Stock split and reverse split handling</li>
 *   <li>Merger and acquisition processing</li>
 *   <li>Rights and warrant offerings</li>
 *   <li>Spin-off and demerger processing</li>
 *   <li>Corporate action notification and election</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance Corporate Actions domain - dividend, split, merger processing
 * @doc.layer product
 * @doc.pattern DomainModule
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class CorporateActionsDomainModule implements KernelModule {

    private static final Logger log = LoggerFactory.getLogger(CorporateActionsDomainModule.class);

    private KernelContext context;
    private volatile boolean started = false;

    @Override
    public String getModuleId() {
        return "finance-corporate-actions";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public Set<KernelCapability> getCapabilities() {
        return Set.of(
            new KernelCapability(
                "finance.corporate.actions.processing",
                "Corporate Actions Processing",
                "Finance-specific corporate action lifecycle management",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "corporate-actions", "product", "finance")
            ),
            new KernelCapability(
                "finance.dividend.management",
                "Dividend Management",
                "Cash and stock dividend processing and entitlement",
                KernelCapability.CapabilityType.BUSINESS_LOGIC,
                Map.of("domain", "corporate-actions", "product", "finance")
            ),
            new KernelCapability(
                "finance.event.notification",
                "Corporate Event Notification",
                "Automated notification for corporate events and elections",
                KernelCapability.CapabilityType.NOTIFICATION,
                Map.of("domain", "corporate-actions", "product", "finance")
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
            KernelDependency.onCapability("finance.reference.data")
        );
    }

    @Override
    public void initialize(KernelContext context) {
        log.info("Initializing Corporate Actions Domain module");
        this.context = context;
        log.info("Corporate Actions Domain module initialized successfully");
    }

    @Override
    public Promise<Void> start() {
        log.info("Starting Corporate Actions Domain module");
        return Promise.ofBlocking(() -> {
            started = true;
            log.info("Corporate Actions Domain module started successfully");
            return null;
        });
    }

    @Override
    public Promise<Void> stop() {
        log.info("Stopping Corporate Actions Domain module");
        return Promise.ofBlocking(() -> {
            started = false;
            log.info("Corporate Actions Domain module stopped successfully");
            return null;
        });
    }

    @Override
    public HealthStatus getHealthStatus() {
        return started
            ? HealthStatus.healthy("Corporate Actions Domain module operational")
            : HealthStatus.unhealthy("Corporate Actions Domain module not started");
    }
}
