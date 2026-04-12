/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.capability;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.plugin.PluginCapability;

import java.util.Objects;

/**
 * Capability exposing Data-Cloud backed event and entity storage to AEP.
 *
 * <p>Provides access to the underlying {@link EventLogStore} for event persistence
 * and {@link EntityStore} for entity CRUD. AEP components that need storage
 * should request this capability from the plugin rather than directly depending
 * on Data-Cloud SPIs.
 *
 * @doc.type class
 * @doc.purpose Event-Cloud storage capability
 * @doc.layer product
 * @doc.pattern Capability
 */
public final class EventCloudStorageCapability implements PluginCapability {

    private final EventLogStore eventLogStore;
    private final EntityStore entityStore;

    public EventCloudStorageCapability(EventLogStore eventLogStore, EntityStore entityStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.entityStore = Objects.requireNonNull(entityStore, "entityStore required");
    }

    /** Returns the event log store for append-only event persistence. */
    public EventLogStore eventLogStore() {
        return eventLogStore;
    }

    /** Returns the entity store for CRUD operations on entities. */
    public EntityStore entityStore() {
        return entityStore;
    }
}
