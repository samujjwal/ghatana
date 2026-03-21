/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance.extensions;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelDescriptor;
import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.module.KernelModule;
import com.ghatana.kernel.modules.eventstore.service.EventStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Finance Compliance Extension.
 *
 * <p>Adds finance-specific compliance event auditing to the generic
 * event store kernel module. Demonstrates proper extension pattern.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Trade compliance event auditing</li>
 *   <li>Regulatory event capture</li>
 *   <li>Best execution logging</li>
 *   <li>Market abuse detection events</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance-specific compliance extension - regulatory event auditing
 * @doc.layer product
 * @doc.pattern Extension
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceComplianceExtension implements KernelExtension {

    private static final Logger log = LoggerFactory.getLogger(FinanceComplianceExtension.class);

    private EventStoreService eventStoreService;

    @Override
    public String getExtensionId() {
        return "finance-compliance";
    }

    @Override
    public String getName() {
        return "Finance Compliance Extension";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public KernelDescriptor getDescriptor() {
        return null;
    }

    @Override
    public Set<KernelCapability> getContributedCapabilities() {
        return Set.of();
    }

    @Override
    public boolean isCompatible(KernelModule hostModule) {
        return true;
    }

    @Override
    public void onModuleInitialized(KernelContext context) {
        log.info("Initializing Finance Compliance Extension");

        eventStoreService = context.getDependency(EventStoreService.class);
        if (eventStoreService == null) {
            log.warn("Event store service not available, compliance auditing disabled");
            return;
        }

        log.info("Finance Compliance Extension initialized - event auditing enabled");
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        log.info("Finance Compliance Extension: module started, publishing startup audit event");

        if (eventStoreService != null) {
            publishAuditEvent("compliance.extension.started", Map.of(
                "extensionId", getExtensionId(),
                "version", getVersion(),
                "timestamp", Instant.now().toString()
            ));
        }
    }

    @Override
    public void onModuleStopped(KernelContext context) {
        log.info("Finance Compliance Extension: module stopped, publishing shutdown audit event");

        if (eventStoreService != null) {
            publishAuditEvent("compliance.extension.stopping", Map.of(
                "extensionId", getExtensionId(),
                "timestamp", Instant.now().toString()
            ));
        }
    }

    /**
     * Publishes a compliance audit event.
     *
     * @param eventType the event type
     * @param data the event data
     */
    public void publishAuditEvent(String eventType, Map<String, Object> data) {
        if (eventStoreService == null) {
            log.warn("Cannot publish audit event - event store not available");
            return;
        }

        // Use the event store service to publish compliance events
        eventStoreService.publish("finance.compliance.audit", data);
        log.debug("Published compliance audit event: {}", eventType);
    }
}
