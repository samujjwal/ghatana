/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.di;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloud.DataCloudConfig;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.StoragePluginRegistry;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

/**
 * ActiveJ DI module for data-cloud core runtime.
 *
 * <p>Provides the foundational data-cloud services — the client facade,
 * configuration, and plugin registry:
 * <ul>
 *   <li>{@link DataCloudConfig} — instance ID, connection limits, caching flags</li>
 *   <li>{@link DataCloudClient} — unified data operations API</li>
 *   <li>{@link StoragePluginRegistry} — singleton plugin discovery registry</li>
 * </ul>
 *
 * <p>The default {@link DataCloudClient} uses in-memory stores (suitable for
 * development and testing). For production, override the client binding with
 * a persistence-backed implementation.
 *
 * <p><b>Dependencies:</b> None — this module is self-contained.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(new DataCloudCoreModule());
 * DataCloudClient client = injector.getInstance(DataCloudClient.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for data-cloud core runtime
 * @doc.layer product
 * @doc.pattern Module, Factory
 * @see DataCloud
 * @see DataCloudClient
 * @see StoragePluginRegistry
 */
public class DataCloudCoreModule extends AbstractModule {

    /**
     * Provides the data-cloud configuration with production defaults.
     *
     * <p>Defaults: instance ID {@code "dc-default"}, max 100 connections/tenant,
     * caching enabled, metrics enabled.
     *
     * @return default data-cloud config
     */
    @Provides
    DataCloudConfig dataCloudConfig() {
        return DataCloudConfig.defaults();
    }

    /**
     * Provides the data-cloud client.
     *
     * <p>Creates a client via {@link DataCloud#create(DataCloudConfig)} with
     * internal in-memory entity and event log stores. Override this binding
     * to use a persistent storage backend.
     *
     * @param config data-cloud configuration
     * @return data-cloud client
     */
    @Provides
    DataCloudClient dataCloudClient(DataCloudConfig config) {
        return DataCloud.create(config);
    }

    /**
     * Provides the storage plugin registry singleton.
     *
     * <p>The registry discovers storage plugins via ServiceLoader and
     * maintains a thread-safe registry of available storage backends.
     *
     * @return storage plugin registry
     */
    @Provides
    StoragePluginRegistry storagePluginRegistry() {
        return StoragePluginRegistry.getInstance();
    }
}
