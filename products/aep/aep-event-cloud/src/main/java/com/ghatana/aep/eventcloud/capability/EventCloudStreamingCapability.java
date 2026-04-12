/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.capability;

import com.ghatana.aep.eventcloud.channel.EventChannelRegistry;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.plugin.PluginCapability;

import java.util.Objects;

/**
 * Capability exposing real-time event streaming and channel management.
 *
 * <p>Provides access to event tailing via {@link EventLogStore} and
 * named channel management via {@link EventChannelRegistry}. AEP pipelines
 * and operators use this capability to subscribe to event streams and
 * manage event channels.
 *
 * @doc.type class
 * @doc.purpose Event-Cloud streaming capability
 * @doc.layer product
 * @doc.pattern Capability
 */
public final class EventCloudStreamingCapability implements PluginCapability {

    private final EventLogStore eventLogStore;
    private final EventChannelRegistry channelRegistry;

    public EventCloudStreamingCapability(
            EventLogStore eventLogStore,
            EventChannelRegistry channelRegistry) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.channelRegistry = Objects.requireNonNull(channelRegistry, "channelRegistry required");
    }

    /** Returns the event log store for tailing operations. */
    public EventLogStore eventLogStore() {
        return eventLogStore;
    }

    /** Returns the channel registry for named event stream management. */
    public EventChannelRegistry channelRegistry() {
        return channelRegistry;
    }
}
