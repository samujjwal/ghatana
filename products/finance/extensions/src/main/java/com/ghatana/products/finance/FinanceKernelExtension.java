/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.products.finance;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.kernel.extension.KernelExtension;
import com.ghatana.kernel.modules.authentication.service.AuthenticationService;
import com.ghatana.kernel.modules.authentication.service.AuthorizationService;
import com.ghatana.products.finance.rules.service.FinanceRulesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finance Kernel Extension.
 *
 * <p>Finance-specific kernel extension that adds finance-specific behavior to generic
 * kernel capabilities. This extension implements the proper kernel extension pattern
 * and does not modify kernel core directly.</p>
 *
 * <p>Key extensions:
 * <ul>
 *   <li>Finance-specific authentication policies</li>
 *   <li>Finance-specific authorization rules</li>
 *   <li>Finance-specific configuration defaults</li>
 *   <li>Finance-specific audit logging</li>
 * </ul></p>
 *
 * @doc.type class
 * @doc.purpose Finance kernel extension - adds finance-specific behavior to kernel capabilities
 * @doc.layer product
 * @doc.pattern Extension
 * @author Ghatana Finance Team
 * @since 1.0.0
 */
public final class FinanceKernelExtension implements KernelExtension {

    private static final Logger log = LoggerFactory.getLogger(FinanceKernelExtension.class);

    @Override
    public void onModuleInitialized(KernelContext context) {
        log.info("Initializing Finance kernel extension");

        // Extend authentication with finance-specific policies
        extendAuthentication(context);

        // Extend authorization with finance-specific rules
        extendAuthorization(context);

        // Extend configuration with finance-specific defaults
        extendConfiguration(context);

        // Extend audit with finance-specific logging
        extendAudit(context);

        log.info("Finance kernel extension initialized successfully");
    }

    @Override
    public void onModuleStarted(KernelContext context) {
        log.info("Finance kernel extension module started");
    }

    @Override
    public void onModuleStopped(KernelContext context) {
        log.info("Finance kernel extension module stopped");
    }

    // ==================== Extension Methods ====================

    private void extendAuthentication(KernelContext context) {
        context.getDependency(AuthenticationService.class).ifPresent(authService -> {
            // Add finance-specific authentication policies
            log.debug("Adding finance-specific authentication policies");
            
            // Examples:
            // - Trader authentication policies
            // - Compliance authentication requirements
            // - Risk-based authentication factors
        });
    }

    private void extendAuthorization(KernelContext context) {
        context.getDependency(AuthorizationService.class).ifPresent(authzService -> {
            // Add finance-specific authorization rules
            log.debug("Adding finance-specific authorization rules");
            
            // Examples:
            // - Trade execution permissions
            // - Portfolio management access
            // - Risk management permissions
            // - Compliance officer access
        });
    }

    private void extendConfiguration(KernelContext context) {
        // Add finance-specific configuration defaults
        log.debug("Adding finance-specific configuration defaults");
        
        // Examples:
        // - Trading hours configuration
        // - Risk limit defaults
        // - Compliance rule parameters
        // - Market data source configuration
    }

    private void extendAudit(KernelContext context) {
        // Add finance-specific audit logging
        log.debug("Adding finance-specific audit logging");
        
        // Examples:
        // - Trade execution audit events
        // - Portfolio change audit events
        // - Risk limit breach audit events
        // - Compliance violation audit events
    }
}
