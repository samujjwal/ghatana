/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.pipeline.registry.connector;

import com.ghatana.platform.domain.event.Event;
import io.activej.promise.Promise;

/**
 * Interface for connector operators in the pipeline registry.
  * @doc.type interface
 * @doc.purpose Provides connector operator functionality.
 * @doc.layer product
 * @doc.pattern Operator
*/
public interface ConnectorOperator {

    /**
     * Get the unique identifier for this connector operator.
     */
    String getId();

    /**
     * Get the name of this connector operator.
     */
    String getName();

    /**
     * Get the type of connector (source, sink, processor).
     */
    String getType();

    /**
     * Process an event through this connector.
     */
    Promise<Event> process(Event event);

    /**
     * Check if this connector is healthy.
     */
    Promise<Boolean> isHealthy();

    /**
     * Start the connector.
     */
    Promise<Void> start();

    /**
     * Stop the connector.
     */
    Promise<Void> stop();

    /**
     * Get connector configuration.
     */
    Object getConfiguration();

    /**
     * Update connector configuration.
     */
    Promise<Void> updateConfiguration(Object configuration);
}
