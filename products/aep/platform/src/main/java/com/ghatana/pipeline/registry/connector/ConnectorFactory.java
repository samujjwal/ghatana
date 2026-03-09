package com.ghatana.pipeline.registry.connector;

import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec.ConnectorType;
import io.activej.promise.Promise;

/**
 * Factory for creating connector operators from ConnectorSpec definitions.
 *
 * <p>
 * <b>Purpose</b><br>
 * Maps declarative ConnectorSpec configurations to concrete operator chains for
 * EventCloud sources/sinks, queues, and HTTP ingress/egress connectors.
 *
 * <p>
 * <b>Responsibilities</b><br>
 * - Validate ConnectorSpec configurations - Create operators for supported
 * connector types - Wire operators with appropriate configuration (endpoints,
 * topics, auth) - Return clear errors for unsupported or misconfigured
 * connectors - Support tenant-aware connector instantiation
 *
 * <p>
 * <b>Supported Connector Types</b><br>
 * - `EVENT_CLOUD_SOURCE`: Read events from EventCloud (tail operation) -
 * `EVENT_CLOUD_SINK`: Write events to EventCloud (append operation) -
 * `QUEUE_SOURCE`: Read from message queue (future enhancement) - `QUEUE_SINK`:
 * Write to message queue (future enhancement) - `HTTP_INGRESS`: HTTP endpoint
 * for event ingestion (future enhancement) - `HTTP_EGRESS`: HTTP client for
 * event delivery (future enhancement)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ConnectorFactory factory = new ConnectorFactoryImpl();
 * ConnectorSpec spec = ConnectorSpec.builder()
 *     .id("eventcloud-source-1")
 *     .type(ConnectorType.EVENT_CLOUD_SOURCE)
 *     .endpoint("eventcloud-cluster")
 *     .topicOrStream("user_events")
 *     .tenantId("tenant-123")
 *     .build();
 * ConnectorOperator operator = factory.createConnector(spec).getResult();
 * }</pre>
 *
 * @see ConnectorSpec
 * @see ConnectorOperator
 * @doc.type interface
 * @doc.purpose Connector factory abstraction
 * @doc.layer product
 * @doc.pattern Factory
 */
public interface ConnectorFactory {

    /**
     * Create a connector operator from specification.
     *
     * <p>
     * GIVEN: Valid ConnectorSpec with supported type WHEN: createConnector() is
     * called THEN: Configured ConnectorOperator is returned
     *
     * @param spec the connector specification
     * @return Promise containing connector operator
     * @throws IllegalArgumentException if spec is invalid or type unsupported
     */
    Promise<ConnectorOperator> createConnector(ConnectorSpec spec);

    /**
     * Validate a connector specification.
     *
     * @param spec the specification to validate
     * @return Promise containing true if valid
     */
    Promise<Boolean> validateConnectorSpec(ConnectorSpec spec);

    /**
     * Check if a connector type is supported by this factory.
     *
     * @param type the connector type
     * @return Promise containing support flag
     */
    Promise<Boolean> isConnectorTypeSupported(ConnectorType type);
}
