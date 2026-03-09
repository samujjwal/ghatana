package com.ghatana.pipeline.registry.connector;

import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec.ConnectorType;
import io.activej.promise.Promise;

/**
 * Base interface for connector operators.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a configured connector that can process events. Provides lifecycle
 * operations and event handling capabilities.
 *
 * <p>
 * <b>Lifecycle</b><br>
 * initialize() → connect() → process events → disconnect() → close()
 *
 * @doc.type interface
 * @doc.purpose Connector operator abstraction
 * @doc.layer product
 * @doc.pattern Operator
 */
public interface ConnectorOperator {

    /**
     * Get connector ID.
     *
     * @return the connector identifier
     */
    String getId();

    /**
     * Get connector type.
     *
     * @return the connector type
     */
    ConnectorType getType();

    /**
     * Initialize connector resources.
     *
     * @return Promise that completes when initialized
     */
    Promise<Void> initialize();

    /**
     * Connect to remote endpoint.
     *
     * @return Promise that completes when connected
     */
    Promise<Void> connect();

    /**
     * Disconnect from remote endpoint.
     *
     * @return Promise that completes when disconnected
     */
    Promise<Void> disconnect();

    /**
     * Close connector and release resources.
     *
     * @return Promise that completes when closed
     */
    Promise<Void> close();

    /**
     * Check if connector is healthy.
     *
     * @return Promise containing health status
     */
    Promise<Boolean> isHealthy();
}
