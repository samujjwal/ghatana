/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.capability;

import com.ghatana.aep.eventcloud.store.EventCloudAgentStore;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.plugin.PluginCapability;

import java.util.Objects;

/**
 * Capability exposing Data-Cloud backed registries to AEP.
 *
 * <p>Provides access to the agent registry and the underlying entity store
 * for pipeline and operator registries. AEP registry components should
 * access Data-Cloud through this capability.
 *
 * @doc.type class
 * @doc.purpose Event-Cloud registry capability
 * @doc.layer product
 * @doc.pattern Capability
 */
public final class EventCloudRegistryCapability implements PluginCapability {

    private final EntityStore entityStore;
    private final EventCloudAgentStore agentStore;

    public EventCloudRegistryCapability(
            EntityStore entityStore,
            EventCloudAgentStore agentStore) {
        this.entityStore = Objects.requireNonNull(entityStore, "entityStore required");
        this.agentStore = Objects.requireNonNull(agentStore, "agentStore required");
    }

    /** Returns the entity store for pipeline and operator registries. */
    public EntityStore entityStore() {
        return entityStore;
    }

    /** Returns the agent store for agent registry operations. */
    public EventCloudAgentStore agentStore() {
        return agentStore;
    }
}
