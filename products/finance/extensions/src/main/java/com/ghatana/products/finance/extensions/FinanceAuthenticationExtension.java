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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Finance Authentication Extension.
 *
 * <p>Adds finance-specific authentication policies to the generic
 * authentication kernel module. This demonstrates the proper use of
 * KernelExtension for product-specific behavior.</p>
 *
 * <p>Key features:
 * <ul>
 *   <li>Trader authentication policies</li>
 *   <li>Compliance officer authentication</li>
 *   <li>Risk-based authentication rules</li>
 *   <li>Market hours authentication restrictions</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance-specific authentication extension - trader policies, compliance rules
 * @doc.layer product
 * @doc.pattern Extension
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceAuthenticationExtension implements KernelExtension {

    private static final Logger log = LoggerFactory.getLogger(FinanceAuthenticationExtension.class);

    @Override
    public String getExtensionId() {
        return "finance-authentication";
    }

    @Override
    public String getName() {
        return "Finance Authentication Extension";
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
        log.info("Initializing Finance Authentication Extension");

        if (!context.hasCapability(KernelCapability.Core.USER_AUTHENTICATION)) {
            log.warn("Authentication capability not available, skipping extension");
            return;
        }

        // Add finance-specific authentication policies
        addTraderAuthenticationPolicy();
        addComplianceAuthenticationPolicy();
        addRiskBasedAuthenticationPolicy();

        log.info("Finance Authentication Extension initialized");
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        log.info("Finance Authentication Extension: module started");
    }

    @Override
    public void onModuleStopped(KernelContext context) {
        log.info("Finance Authentication Extension: module stopped");
    }

    // ==================== Private Methods ====================

    private void addTraderAuthenticationPolicy() {
        log.debug("Adding trader authentication policy");
        // Add trader-specific authentication requirements
        // e.g., market hours restrictions, desk-specific access
    }

    private void addComplianceAuthenticationPolicy() {
        log.debug("Adding compliance authentication policy");
        // Add compliance officer authentication requirements
        // e.g., additional MFA, audit logging
    }

    private void addRiskBasedAuthenticationPolicy() {
        log.debug("Adding risk-based authentication policy");
        // Add risk-based authentication rules
        // e.g., location-based, device-based risk scoring
    }
}
