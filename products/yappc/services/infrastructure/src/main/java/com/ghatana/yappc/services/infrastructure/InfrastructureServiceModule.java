/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure Service Module
 */
package com.ghatana.yappc.services.infrastructure;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityServiceAdapter;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ DI module for YAPPC Infrastructure services.
 *
 * <p>Provides bindings for persistence, data integration, and
 * external service adapters:
 * <ul>
 *   <li>{@link SecurityServiceAdapter} — Security scanning and SBOM</li>
 *   <li>{@link InfrastructureServiceFacade} — Unified infrastructure facade</li>
 * </ul>
 *
 * <p>All IO-bound operations use {@code Promise.ofBlocking(executor, ...)}
 * per Golden Rule #3 (never block the event loop).</p>
 *
 * @doc.type class
 * @doc.purpose DI module for infrastructure/persistence services
 * @doc.layer product
 * @doc.pattern Module
 */
public class InfrastructureServiceModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(InfrastructureServiceModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC Infrastructure Service DI bindings");
    }

    /**
     * Provides the unified infrastructure facade.
     *
     * <p>Aggregates all infrastructure adapters behind a single Promise-returning API.</p>
     */
    @Provides
    InfrastructureServiceFacade infrastructureServiceFacade(
            SecurityServiceAdapter securityAdapter) {
        logger.info("Creating InfrastructureServiceFacade");
        return new InfrastructureServiceFacade(securityAdapter);
    }

    /**
     * Provides SecurityServiceAdapter for security scanning and SBOM.
     *
     * <p>No-arg constructor; scans project paths for vulnerabilities
     * and generates Software Bill of Materials.</p>
     */
    @Provides
    SecurityServiceAdapter securityServiceAdapter() {
        logger.info("Creating SecurityServiceAdapter");
        return new SecurityServiceAdapter();
    }
}
