/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Infrastructure Service Module
 */
package com.ghatana.yappc.services.infrastructure;

import com.ghatana.yappc.infrastructure.datacloud.adapter.SecurityServiceAdapter;
import com.ghatana.yappc.infrastructure.security.OsvScannerAdapter;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
     * Provides SecurityServiceAdapter with composite scanner (SAST + dependency scanning).
     *
     * <p>Composes StaticAnalysisScanner (SAST) + OsvScannerAdapter (dependency scanning)
     * for comprehensive security coverage.</p>
     */
    @Provides
    SecurityServiceAdapter securityServiceAdapter() {
        logger.info("Creating SecurityServiceAdapter with CompositeSecurityScanner");
        Executor executor = Executors.newCachedThreadPool();
        
        com.ghatana.yappc.infrastructure.datacloud.adapter.StaticAnalysisScanner staticAnalysisScanner = 
            new com.ghatana.yappc.infrastructure.datacloud.adapter.StaticAnalysisScanner(executor);
        
        OsvScannerAdapter osvScanner = new OsvScannerAdapter(executor);
        
        com.ghatana.yappc.infrastructure.security.CompositeSecurityScanner compositeScanner =
            new com.ghatana.yappc.infrastructure.security.CompositeSecurityScanner(
                List.of(staticAnalysisScanner, osvScanner)
            );
        
        return new SecurityServiceAdapter(compositeScanner);
    }
}
