/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.spi.EventCloudConnector;
import com.ghatana.platform.domain.eventstore.EventLogStore;

import java.util.Objects;

/**
 * Default {@link EventCloudAdapter} backed by Data-Cloud's {@link EventLogStore}.
 *
 * @doc.type class
 * @doc.purpose Provide the default Data-Cloud-backed EventCloudAdapter implementation
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class DataCloudEventCloudAdapter implements EventCloudAdapter {

    private final DataCloudBackedEventCloud eventCloud;
    private final DataCloudEventCloudConnector connector;

    /**
     * Creates the adapter using a shared EventLogStore for both facade styles.
     *
     * @param eventLogStore backing event log store
     */
    public DataCloudEventCloudAdapter(EventLogStore eventLogStore) {
        Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.eventCloud = new DataCloudBackedEventCloud(eventLogStore);
        this.connector = new DataCloudEventCloudConnector(eventLogStore);
    }

    @Override
    public EventCloud eventCloud() {
        return eventCloud;
    }

    @Override
    public EventCloudConnector connector() {
        return connector;
    }

    @Override
    public String adapterName() {
        return "data-cloud";
    }
}
