package com.ghatana.pipeline.runtime.adapter;

import com.ghatana.core.domain.pipeline.ConnectorSpec;

/**
 * Registry interface for accessing connector specifications during runtime
 * adaptation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides abstraction for retrieving connector specifications during pipeline
 * adaptation. Allows flexibility in how connectors are stored and retrieved
 * (in-memory, remote, etc.).
 *
 * <p>
 * <b>Responsibilities</b><br>
 * - Retrieve connector specs by ID - Check connector availability - List
 * connectors for validation
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ConnectorRegistry registry = connectorService;
 * ConnectorSpec spec = registry.getConnector("http-ingress-1");
 * boolean available = registry.isConnectorAvailable("http-ingress-1");
 * }</pre>
 *
 * @see ConnectorSpec
 * @doc.type interface
 * @doc.purpose Registry for connector specifications
 * @doc.layer core
 * @doc.pattern Registry
 */
public interface ConnectorRegistry {

    /**
     * Retrieves connector specification by ID.
     *
     * @param connectorId connector ID
     * @return ConnectorSpec or null if not found
     */
    ConnectorSpec getConnector(String connectorId);

    /**
     * Checks if connector is available in registry.
     *
     * @param connectorId connector ID
     * @return true if connector exists and is accessible
     */
    boolean isConnectorAvailable(String connectorId);

    /**
     * Lists all connector IDs in registry (for validation).
     *
     * @return collection of connector IDs
     */
    java.util.Collection<String> listConnectorIds();
}
