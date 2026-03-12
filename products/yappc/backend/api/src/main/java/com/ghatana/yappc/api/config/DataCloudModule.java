/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.config;

import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.client.DataCloudClientFactory;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ DI module that provides a {@link DataCloudClient} singleton for YAPPC services.
 *
 * <p>Reads Data-Cloud connection settings via {@link DataCloudClientFactory#fromEnvironment()},
 * which honours {@code DC_DEPLOYMENT_MODE}, {@code DC_SERVER_URL}, and
 * {@code DC_CLUSTER_URLS} environment variables (see {@code DataCloudEnvConfig}).
 *
 * <p><b>Typical environment settings</b>
 * <pre>
 * DC_DEPLOYMENT_MODE=embedded          # local dev — no external server required
 * DC_DEPLOYMENT_MODE=standalone        # single remote server
 * DC_SERVER_URL=https://dc.example.com
 * DC_DEPLOYMENT_MODE=distributed       # cluster
 * DC_CLUSTER_URLS=https://n1.dc.example.com,https://n2.dc.example.com
 * </pre>
 *
 * <p>Install this module in {@code ProductionModule} and {@code DevelopmentModule} via
 * {@code install(new DataCloudModule())}.
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module providing DataCloudClient singleton for YAPPC
 * @doc.layer product
 * @doc.pattern Module, Dependency Injection, Factory
 */
public class DataCloudModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(DataCloudModule.class);

    /**
     * Provides a fully initialised {@link DataCloudClient} singleton.
     *
     * <p>Uses {@link DataCloudClientFactory#fromEnvironment()} which validates configuration
     * and selects the correct client strategy (embedded / standalone / distributed).
     *
     * @return DataCloudClient instance ready for use
     */
    @Provides
    DataCloudClient dataCloudClient() {
        log.info("Initialising Data-Cloud client from environment");
        DataCloudClient client = DataCloudClientFactory.fromEnvironment();
        log.info("Data-Cloud client ready: {}", client.getClass().getSimpleName());
        return client;
    }
}
