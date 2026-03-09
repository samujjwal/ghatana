/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Domain Service Module
 */
package com.ghatana.yappc.services.domain;

import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.shape.ShapeService;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ DI module for YAPPC Domain services.
 *
 * <p>Provides the {@link DomainServiceFacade} which aggregates
 * core domain services (IntentService, ShapeService, etc.) behind
 * a unified API. The individual lifecycle services are sourced from
 * {@code LifecycleServiceModule} and injected here.</p>
 *
 * @doc.type class
 * @doc.purpose DI module for domain service facade
 * @doc.layer product
 * @doc.pattern Module
 */
public class DomainServiceModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(DomainServiceModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC Domain Service DI bindings");
    }

    /**
     * Provides the unified domain service facade.
     *
     * <p>Wraps lifecycle phase services (intent, shape) into a single facade.
     * Additional phase services can be added as dependencies grow.</p>
     *
     * @param intentService the intent capture service
     * @param shapeService the architecture shaping service
     * @return configured domain facade
     */
    @Provides
    DomainServiceFacade domainServiceFacade(
            IntentService intentService,
            ShapeService shapeService) {
        logger.info("Creating DomainServiceFacade");
        return new DomainServiceFacade(intentService, shapeService);
    }
}
