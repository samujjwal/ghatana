/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.aep.feature.AepCustomModelService;
import com.ghatana.aep.feature.AepDataRetentionService;
import com.ghatana.aep.feature.AepFeatureStoreClient;
import com.ghatana.aep.feature.AepModelRegistryClient;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.eventlog.adapters.jdbc.JdbcEventStore;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

/**
 * ActiveJ DI module for AEP AI Platform integration.
 *
 * <p>Wires the platform AI subsystems into the AEP dependency graph:
 * <ul>
 *   <li>{@link FeatureStoreService} — two-tier (Redis + PostgreSQL) ML
 *       feature storage for pattern recommendations and anomaly detection.</li>
 *   <li>{@link ModelRegistryService} — versioned ML model registry
 *       for registering, promoting, and querying AEP model deployments.</li>
 *   <li>{@link AepFeatureStoreClient} — ActiveJ Promise façade over
 *       {@link FeatureStoreService} so callers never block the event loop.</li>
 *   <li>{@link AepModelRegistryClient} — ActiveJ Promise façade over
 *       {@link ModelRegistryService}.</li>
 * </ul>
 *
 * <h3>Required peer modules</h3>
 * <ul>
 *   <li>{@code PostgresConfig} (or equivalent) — provides {@link DataSource}</li>
 *   <li>{@link AepCoreModule} — provides {@link ExecutorService}</li>
 *   <li>Platform {@code ObservabilityModule} — provides {@link MetricsCollector}</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepCoreModule(),
 *     new AepObservabilityModule(),
 *     new PostgresConfig(props),
 *     new AepAiModule()       // ← add this
 * );
 * AepFeatureStoreClient featureClient = injector.getInstance(AepFeatureStoreClient.class);
 * AepModelRegistryClient modelClient  = injector.getInstance(AepModelRegistryClient.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module wiring AEP Feature Store and Model Registry integration
 * @doc.layer product
 * @doc.pattern Module
 */
public class AepAiModule extends AbstractModule {

    /**
     * Provides the platform {@link FeatureStoreService}.
     *
     * <p>Uses the shared {@link DataSource} (HikariCP pool) and the platform
     * {@link MetricsCollector} already provided by peer modules.
     * The service self-bootstraps the {@code features_store} schema on first use.
     *
     * @param dataSource      JDBC connection pool (from {@code PostgresConfig})
     * @param metricsCollector platform metrics collector
     * @return singleton feature store service
     */
    @Provides
    FeatureStoreService featureStoreService(DataSource dataSource,
                                            MetricsCollector metricsCollector) {
        return new FeatureStoreService(dataSource, metricsCollector);
    }

    /**
     * Provides the platform {@link ModelRegistryService}.
     *
     * <p>Uses the same {@link DataSource} pool as the pipeline registry.
     * Maintains a separate {@code model_registry} table in the same database.
     *
     * @param dataSource      JDBC connection pool
     * @param metricsCollector platform metrics collector
     * @return singleton model registry service
     */
    @Provides
    ModelRegistryService modelRegistryService(DataSource dataSource,
                                              MetricsCollector metricsCollector) {
        return new ModelRegistryService(dataSource, metricsCollector);
    }

    /**
     * Provides the {@link AepFeatureStoreClient} — ActiveJ async wrapper for
     * the {@link FeatureStoreService}.
     *
     * @param featureStoreService underlying synchronous service
     * @param executorService     shared blocking executor (from {@link AepCoreModule})
     * @return singleton feature store client
     */
    @Provides
    AepFeatureStoreClient aepFeatureStoreClient(FeatureStoreService featureStoreService,
                                                ExecutorService executorService) {
        return new AepFeatureStoreClient(featureStoreService, executorService);
    }

    /**
     * Provides the {@link AepModelRegistryClient} — ActiveJ async wrapper for
     * the {@link ModelRegistryService}.
     *
     * @param modelRegistryService underlying synchronous service
     * @param executorService      shared blocking executor (from {@link AepCoreModule})
     * @return singleton model registry client
     */
    @Provides
    AepModelRegistryClient aepModelRegistryClient(ModelRegistryService modelRegistryService,
                                                  ExecutorService executorService) {
        return new AepModelRegistryClient(modelRegistryService, executorService);
    }

    /**
     * Provides {@link AepCustomModelService} — artifact provenance, validation
     * gates, and canary deployment management for AEP custom models.
     *
     * <p>Requires the shared {@link DataSource}, the platform
     * {@link ModelRegistryService}, and Micrometer's {@link MeterRegistry}.
     *
     * @param dataSource      shared JDBC connection pool
     * @param modelRegistry   platform model registry service (already provided)
     * @param meterRegistry   Micrometer registry for observability metrics
     * @param executorService shared blocking executor (from {@link AepCoreModule})
     * @return singleton custom model service
     */
    @Provides
    AepCustomModelService aepCustomModelService(DataSource dataSource,
                                               ModelRegistryService modelRegistry,
                                               MeterRegistry meterRegistry,
                                               ExecutorService executorService) {
        return new AepCustomModelService(dataSource, modelRegistry, meterRegistry, executorService);
    }

    /**
     * Provides {@link AepDataRetentionService} — automated multi-tenant data
     * retention enforcement with GDPR/CCPA right-to-erasure support.
     *
     * <p>Uses the shared {@link DataSource} for the retention policy table, the
     * shared {@link JdbcEventStore} as the purge target, and Micrometer for
     * observability.
     *
     * @param dataSource      shared JDBC connection pool
     * @param eventStore      JDBC-backed event store to enforce policies against
     * @param meterRegistry   Micrometer registry for observability metrics
     * @param executorService shared blocking executor (from {@link AepCoreModule})
     * @return singleton retention service
     */
    @Provides
    AepDataRetentionService aepDataRetentionService(DataSource dataSource,
                                                   JdbcEventStore eventStore,
                                                   MeterRegistry meterRegistry,
                                                   ExecutorService executorService) {
        return new AepDataRetentionService(dataSource, eventStore, meterRegistry, executorService);
    }
}
