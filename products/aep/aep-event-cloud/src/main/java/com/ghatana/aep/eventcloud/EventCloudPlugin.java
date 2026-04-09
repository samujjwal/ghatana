/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.spi.EventCloudConnector;
import com.ghatana.aep.eventcloud.capability.EventCloudRegistryCapability;
import com.ghatana.aep.eventcloud.capability.EventCloudStorageCapability;
import com.ghatana.aep.eventcloud.capability.EventCloudStreamingCapability;
import com.ghatana.aep.eventcloud.channel.EventChannel;
import com.ghatana.aep.eventcloud.channel.EventChannelRegistry;
import com.ghatana.aep.eventcloud.store.EventCloudAgentStore;
import com.ghatana.aep.eventcloud.store.EventCloudRunLedger;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.plugin.Plugin;
import com.ghatana.platform.plugin.PluginCapability;
import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;

/**
 * Event-Cloud plugin: the mandatory bridge between AEP and Data-Cloud.
 *
 * <p>This plugin is the single integration point through which AEP accesses
 * all Data-Cloud backed stores, registries, buffers, pipes, and channels.
 * Data-Cloud is mandatory for AEP and can run either embedded (in-process)
 * or as a remote service.
 *
 * <h3>Provided capabilities</h3>
 * <ul>
 *   <li>{@link EventCloudStorageCapability} - event log and entity storage</li>
 *   <li>{@link EventCloudStreamingCapability} - real-time event tailing and pub/sub</li>
 *   <li>{@link EventCloudRegistryCapability} - agent, pipeline, and operator registries</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #initialize(PluginContext)} - resolves config and creates Data-Cloud clients</li>
 *   <li>{@link #start()} - connects to Data-Cloud, creates channels, verifies health</li>
 *   <li>{@link #stop()} - closes channels and Data-Cloud connections</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Mandatory Event-Cloud plugin bridging AEP to Data-Cloud
 * @doc.layer product
 * @doc.pattern Plugin, Bridge, Facade
 */
public final class EventCloudPlugin implements Plugin {

    private static final Logger log = LoggerFactory.getLogger(EventCloudPlugin.class);

    public static final String PLUGIN_ID = "aep-event-cloud";
    public static final String PLUGIN_VERSION = "1.0.0";

    private final EventLogStore eventLogStore;
    private final EntityStore entityStore;
    private final EventCloudPluginConfig config;

    private volatile PluginState state = PluginState.UNLOADED;
    private EventCloudAdapter eventCloudAdapter;
    private EventChannelRegistry channelRegistry;
    private EventCloudRunLedger runLedger;
    private EventCloudAgentStore agentStore;

    // Capability instances (created on initialize)
    private EventCloudStorageCapability storageCapability;
    private EventCloudStreamingCapability streamingCapability;
    private EventCloudRegistryCapability registryCapability;

    /**
     * Creates the plugin with the required Data-Cloud stores.
     *
     * @param eventLogStore Data-Cloud EventLogStore for event persistence
     * @param entityStore   Data-Cloud EntityStore for entity CRUD
     * @param config        plugin configuration
     */
    public EventCloudPlugin(
            @NotNull EventLogStore eventLogStore,
            @NotNull EntityStore entityStore,
            @NotNull EventCloudPluginConfig config) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.entityStore = Objects.requireNonNull(entityStore, "entityStore required");
        this.config = Objects.requireNonNull(config, "config required");
    }

    // =========================================================================
    // Plugin lifecycle
    // =========================================================================

    @Override
    public @NotNull PluginMetadata metadata() {
        return PluginMetadata.builder()
            .id(PLUGIN_ID)
            .name("AEP Event-Cloud Plugin")
            .version(PLUGIN_VERSION)
            .description("Mandatory bridge between AEP and Data-Cloud for event processing "
                + "stores, registries, buffers, pipes, and channels")
            .type(PluginType.STORAGE)
            .author("Ghatana")
            .license("Proprietary")
            .tags(Set.of("event-cloud", "data-cloud", "aep", "event-processing",
                         "storage", "streaming", "registry"))
            .capabilities(Set.of(
                "event-storage", "event-streaming", "entity-registry",
                "pipeline-store", "agent-store", "run-ledger",
                "event-channels", "event-buffer"))
            .build();
    }

    @Override
    public @NotNull PluginState getState() {
        return state;
    }

    @Override
    public @NotNull Promise<Void> initialize(@NotNull PluginContext context) {
        log.info("[event-cloud] Initializing plugin (embedded={})", config.embedded());

        this.eventCloudAdapter = new DataCloudEventCloudAdapter(eventLogStore);

        // Create the channel registry for named event streams
        this.channelRegistry = new EventChannelRegistry(eventLogStore);

        // Create the run ledger for pipeline execution records
        this.runLedger = new EventCloudRunLedger(eventLogStore);

        // Create the agent store for agent registry
        this.agentStore = new EventCloudAgentStore(entityStore);

        // Create capabilities
        this.storageCapability = new EventCloudStorageCapability(
            eventLogStore, entityStore);
        this.streamingCapability = new EventCloudStreamingCapability(
            eventLogStore, channelRegistry);
        this.registryCapability = new EventCloudRegistryCapability(
            entityStore, agentStore);

        state = PluginState.INITIALIZED;
        log.info("[event-cloud] Plugin initialized");
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> start() {
        log.info("[event-cloud] Starting plugin");
        state = PluginState.STARTING;

        // Register default channels
        channelRegistry.registerChannel(EventChannel.EVENTS_INTAKE);
        channelRegistry.registerChannel(EventChannel.PIPELINE_RUNS);
        channelRegistry.registerChannel(EventChannel.AGENT_DECISIONS);
        channelRegistry.registerChannel(EventChannel.LEARNING_EPISODES);
        channelRegistry.registerChannel(EventChannel.POLICY_PROMOTIONS);

        state = PluginState.RUNNING;
        log.info("[event-cloud] Plugin started with {} channels",
            channelRegistry.channelCount());
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<Void> stop() {
        log.info("[event-cloud] Stopping plugin");
        state = PluginState.STOPPING;

        if (channelRegistry != null) {
            channelRegistry.close();
        }

        state = PluginState.STOPPED;
        log.info("[event-cloud] Plugin stopped");
        return Promise.complete();
    }

    @Override
    public @NotNull Promise<HealthStatus> healthCheck() {
        if (state != PluginState.RUNNING) {
            return Promise.of(HealthStatus.unhealthy("Plugin not running: " + state));
        }
        // Verify Data-Cloud connectivity via a lightweight offset read
        return eventLogStore.getLatestOffset(
                com.ghatana.datacloud.spi.TenantContext.of("_health_check"))
            .map(offset -> HealthStatus.ok())
            .then(Promise::of,
                e -> Promise.of(HealthStatus.unhealthy(
                    "Data-Cloud unreachable: " + e.getMessage())));
    }

    @Override
    public @NotNull Set<PluginCapability> getCapabilities() {
        if (storageCapability == null) {
            return Set.of();
        }
        return Set.of(storageCapability, streamingCapability, registryCapability);
    }

    // =========================================================================
    // Public accessors for AEP integration
    // =========================================================================

    /**
     * Returns the AEP EventCloud facade backed by Data-Cloud.
     * This is the primary interface AEP operators and pipelines use.
     */
    public @NotNull EventCloud eventCloud() {
        return adapter().eventCloud();
    }

    /**
     * Returns the EventCloudConnector for transport-level integration.
     */
    public @NotNull EventCloudConnector connector() {
        return adapter().connector();
    }

    /**
     * Returns the event-cloud adapter abstraction.
     */
    public @NotNull EventCloudAdapter adapter() {
        Objects.requireNonNull(eventCloudAdapter, "Plugin not initialized");
        return eventCloudAdapter;
    }

    /**
     * Returns the channel registry for named event streams.
     */
    public @NotNull EventChannelRegistry channelRegistry() {
        Objects.requireNonNull(channelRegistry, "Plugin not initialized");
        return channelRegistry;
    }

    /**
     * Returns the run ledger for pipeline execution records.
     */
    public @NotNull EventCloudRunLedger runLedger() {
        Objects.requireNonNull(runLedger, "Plugin not initialized");
        return runLedger;
    }

    /**
     * Returns the agent store for agent registry operations.
     */
    public @NotNull EventCloudAgentStore agentStore() {
        Objects.requireNonNull(agentStore, "Plugin not initialized");
        return agentStore;
    }

    /**
     * Returns the underlying Data-Cloud EventLogStore.
     * Prefer using the higher-level abstractions when possible.
     */
    public @NotNull EventLogStore eventLogStore() {
        return eventLogStore;
    }

    /**
     * Returns the underlying Data-Cloud EntityStore.
     * Prefer using the higher-level abstractions when possible.
     */
    public @NotNull EntityStore entityStore() {
        return entityStore;
    }
}
