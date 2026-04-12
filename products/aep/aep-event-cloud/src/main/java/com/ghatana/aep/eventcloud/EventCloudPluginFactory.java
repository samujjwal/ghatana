/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStoreAdapters;
import org.jetbrains.annotations.NotNull;


/**
 * Factory for creating {@link EventCloudPlugin} instances.
 *
 * <p>Provides convenience methods for the three standard deployment modes:
 * <ul>
 *   <li><strong>Embedded</strong> - Data-Cloud runs in-process with AEP</li>
 *   <li><strong>Standalone</strong> - AEP connects to a remote Data-Cloud service</li>
 *   <li><strong>From environment</strong> - Auto-detect mode from env vars</li>
 * </ul>
 *
 * <p>In all modes, the caller must provide the resolved {@link EventLogStore}
 * and {@link EntityStore} instances. For embedded mode, these come from
 * {@code DataCloudClientFactory.embedded()}'s internal stores. For standalone
 * mode, the Data-Cloud SDK client provides remote-backed store implementations.
 *
 * @doc.type class
 * @doc.purpose Factory for EventCloudPlugin creation
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class EventCloudPluginFactory {

    private EventCloudPluginFactory() {}

    /**
     * Creates a plugin with the given stores and configuration.
     *
     * @param eventLogStore Data-Cloud event log store
     * @param entityStore   Data-Cloud entity store
     * @param config        plugin configuration
     * @return a new EventCloudPlugin (not yet initialized)
     */
    public static @NotNull EventCloudPlugin create(
            @NotNull EventLogStore eventLogStore,
            @NotNull EntityStore entityStore,
            @NotNull EventCloudPluginConfig config) {
        return new EventCloudPlugin(
            EventLogStoreAdapters.toPlatformStore(eventLogStore), entityStore, config);
    }

    /**
     * Creates a plugin in embedded mode.
     *
     * @param eventLogStore Data-Cloud event log store (from embedded Data-Cloud)
     * @param entityStore   Data-Cloud entity store (from embedded Data-Cloud)
     * @return a new EventCloudPlugin configured for embedded mode
     */
    public static @NotNull EventCloudPlugin embedded(
            @NotNull EventLogStore eventLogStore,
            @NotNull EntityStore entityStore) {
        return new EventCloudPlugin(
            EventLogStoreAdapters.toPlatformStore(eventLogStore),
            entityStore, EventCloudPluginConfig.embeddedMode());
    }

    /**
     * Creates a plugin in standalone mode connecting to a remote Data-Cloud.
     *
     * @param eventLogStore Data-Cloud event log store (from remote SDK)
     * @param entityStore   Data-Cloud entity store (from remote SDK)
     * @param serviceUrl    Data-Cloud service URL
     * @return a new EventCloudPlugin configured for standalone mode
     */
    public static @NotNull EventCloudPlugin standalone(
            @NotNull EventLogStore eventLogStore,
            @NotNull EntityStore entityStore,
            @NotNull String serviceUrl) {
        return new EventCloudPlugin(
            EventLogStoreAdapters.toPlatformStore(eventLogStore),
            entityStore, EventCloudPluginConfig.standalone(serviceUrl));
    }

    /**
     * Creates a plugin with configuration resolved from environment variables.
     *
     * @param eventLogStore Data-Cloud event log store
     * @param entityStore   Data-Cloud entity store
     * @return a new EventCloudPlugin configured from environment
     */
    public static @NotNull EventCloudPlugin fromEnvironment(
            @NotNull EventLogStore eventLogStore,
            @NotNull EntityStore entityStore) {
        return new EventCloudPlugin(
            EventLogStoreAdapters.toPlatformStore(eventLogStore),
            entityStore, EventCloudPluginConfig.fromEnvironment());
    }
}
