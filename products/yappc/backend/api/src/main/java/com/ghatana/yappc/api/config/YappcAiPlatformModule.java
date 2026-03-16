/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api.config;

import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.api.ai.platform.YappcFeatureStoreClient;
import com.ghatana.yappc.api.ai.platform.YappcModelRegistryClient;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ActiveJ DI module that wires YAPPC's access to the platform AI shared services.
 *
 * <h3>Provided bindings</h3>
 * <dl>
 *   <dt>{@link FeatureStoreService}</dt>
 *   <dd>Two-tier (Redis hot + PostgreSQL cold) feature store; uses the tenant-isolated
 *       YAPPC {@link DataSource}.</dd>
 *
 *   <dt>{@link ModelRegistryService}</dt>
 *   <dd>JDBC-backed model registry for registering, querying, and lifecycle-managing
 *       AI model versions across YAPPC tenants.</dd>
 *
 *   <dt>{@link YappcFeatureStoreClient}</dt>
 *   <dd>ActiveJ {@code Promise}-based async façade over {@link FeatureStoreService}
 *       suitable for direct use on the event-loop.</dd>
 *
 *   <dt>{@link YappcModelRegistryClient}</dt>
 *   <dd>ActiveJ {@code Promise}-based async façade over {@link ModelRegistryService}.</dd>
 * </dl>
 *
 * <h3>Installation</h3>
 * <pre>{@code
 * // In ProductionModule.configure():
 * install(new YappcAiPlatformModule());
 * }</pre>
 *
 * <h3>Dependencies</h3>
 * The module requires the following bindings to already be present in the injector
 * (supplied by {@link DataSourceModule} and {@link ProductionModule}):
 * <ul>
 *   <li>{@link DataSource} — from {@link DataSourceModule}</li>
 *   <li>{@link MetricsCollector} — from {@link ProductionModule#metricsCollector()}</li>
 * </ul>
 *
 * <h3>Thread pool</h3>
 * An internal bounded thread pool ({@code yappc-ai-platform-N} daemon threads)
 * is used to dispatch blocking JDBC and Redis calls off the ActiveJ event-loop.
 * The pool is sized to the number of available processors with a minimum of 2.
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI configuration for YAPPC AI platform shared services
 * @doc.layer product
 * @doc.pattern Module, Adapter
 */
public final class YappcAiPlatformModule extends AbstractModule {

    private static final Logger log = LoggerFactory.getLogger(YappcAiPlatformModule.class);

    /**
     * Shared blocking executor used by all AI platform client façades.
     * Daemon threads so they do not prevent JVM shutdown.
     */
    private final ExecutorService blockingExecutor = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()),
            r -> {
                Thread t = new Thread(r, "yappc-ai-platform");
                t.setDaemon(true);
                return t;
            });

    // =========================================================================
    // Platform service providers
    // =========================================================================

    /**
     * Provides the platform {@link FeatureStoreService} backed by the YAPPC
     * {@link DataSource} and shared {@link MetricsCollector}.
     *
     * @param dataSource       YAPPC JDBC connection pool
     * @param metricsCollector platform observability collector
     * @return singleton feature store service
     * @doc.layer platform
     * @doc.pattern Service
     */
    @Provides
    FeatureStoreService featureStoreService(DataSource dataSource, MetricsCollector metricsCollector) {
        log.info("Initialising FeatureStoreService for YAPPC");
        return new FeatureStoreService(dataSource, metricsCollector);
    }

    /**
     * Provides the platform {@link ModelRegistryService} backed by the YAPPC
     * {@link DataSource} and shared {@link MetricsCollector}.
     *
     * @param dataSource       YAPPC JDBC connection pool
     * @param metricsCollector platform observability collector
     * @return singleton model registry service
     * @doc.layer platform
     * @doc.pattern Service + Repository
     */
    @Provides
    ModelRegistryService modelRegistryService(DataSource dataSource, MetricsCollector metricsCollector) {
        log.info("Initialising ModelRegistryService for YAPPC");
        return new ModelRegistryService(dataSource, metricsCollector);
    }

    // =========================================================================
    // Async client façades
    // =========================================================================

    /**
     * Provides the YAPPC-specific ActiveJ async façade over {@link FeatureStoreService}.
     *
     * @param featureStoreService underlying platform service
     * @return feature store client suitable for Promise-based event-loop use
     * @doc.layer product
     * @doc.pattern Adapter
     */
    @Provides
    YappcFeatureStoreClient yappcFeatureStoreClient(FeatureStoreService featureStoreService) {
        return new YappcFeatureStoreClient(featureStoreService, blockingExecutor);
    }

    /**
     * Provides the YAPPC-specific ActiveJ async façade over {@link ModelRegistryService}.
     *
     * @param modelRegistryService underlying platform service
     * @return model registry client suitable for Promise-based event-loop use
     * @doc.layer product
     * @doc.pattern Adapter
     */
    @Provides
    YappcModelRegistryClient yappcModelRegistryClient(ModelRegistryService modelRegistryService) {
        return new YappcModelRegistryClient(modelRegistryService, blockingExecutor);
    }
}
