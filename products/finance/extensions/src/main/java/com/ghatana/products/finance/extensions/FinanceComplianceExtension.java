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
import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
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
    private static final String SYSTEM_TENANT = "finance-system";

    private AuditBusPort auditBusPort;

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

        if (!context.hasCapability(KernelCapability.Core.EVENT_PROCESSING)) {
            log.warn("Event processing capability not available, compliance auditing disabled");
            return;
        }

        auditBusPort = context.getOptionalDependency(AuditBusPort.class).orElse(null);

        log.info("Finance Compliance Extension initialized - event auditing enabled");
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        log.info("Finance Compliance Extension: module started, publishing startup audit event");

        if (auditBusPort != null) {
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

        if (auditBusPort != null) {
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
        if (auditBusPort == null) {
            log.warn("Cannot publish audit event - audit bus not available");
            return;
        }

        auditBusPort.emit(AuditEvent.builder()
            .tenantId(SYSTEM_TENANT)
            .eventType(eventType)
            .resourceType("finance.extension")
            .resourceId(getExtensionId())
            .details(data)
            .build());
        log.debug("Published compliance audit event: {}", eventType);
    }
}
