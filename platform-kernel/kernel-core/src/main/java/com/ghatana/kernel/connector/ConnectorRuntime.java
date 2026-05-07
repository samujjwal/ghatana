package com.ghatana.kernel.connector;

import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.util.Map;
import java.util.Optional;

/**
 * Runtime interface for managing external platform connectors.
 *
 * <p>Provides lifecycle management, health monitoring, and configuration
 * for connectors that integrate with external platforms (Meta, LinkedIn, TikTok, etc.).
 * Connectors are loaded as plugins and managed through this runtime.</p>
 *
 * @doc.type interface
 * @doc.purpose Connector runtime for external platform integrations (KERNEL-P1)
 * @doc.layer core
 * @doc.pattern Service
 */
public interface ConnectorRuntime {

    /**
     * Register a connector configuration.
     *
     * @param context kernel context
     * @param config connector configuration
     * @return Promise that completes when connector is registered
     */
    Promise<Void> registerConnector(KernelContext context, ConnectorConfig config);

    /**
     * Unregister a connector.
     *
     * @param context kernel context
     * @param connectorId connector identifier
     * @return Promise that completes when connector is unregistered
     */
    Promise<Void> unregisterConnector(KernelContext context, String connectorId);

    /**
     * Get connector configuration by ID.
     *
     * @param connectorId connector identifier
     * @return Optional containing connector config if found
     */
    Promise<Optional<ConnectorConfig>> getConnector(String connectorId);

    /**
     * List all connectors for a tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise containing list of connector configurations
     */
    Promise<Map<String, ConnectorConfig>> listConnectors(String tenantId);

    /**
     * Activate a connector.
     *
     * @param context kernel context
     * @param connectorId connector identifier
     * @return Promise that completes when connector is activated
     */
    Promise<Void> activateConnector(KernelContext context, String connectorId);

    /**
     * Deactivate a connector.
     *
     * @param context kernel context
     * @param connectorId connector identifier
     * @return Promise that completes when connector is deactivated
     */
    Promise<Void> deactivateConnector(KernelContext context, String connectorId);

    /**
     * Perform health check on a connector.
     *
     * @param connectorId connector identifier
     * @return Promise containing health check result
     */
    Promise<ConnectorHealth> healthCheck(String connectorId);

    /**
     * Get connector status.
     *
     * @param connectorId connector identifier
     * @return Promise containing connector status
     */
    Promise<ConnectorStatus> getStatus(String connectorId);

    /**
     * Update connector configuration.
     *
     * @param context kernel context
     * @param connectorId connector identifier
     * @param config new connector configuration
     * @return Promise that completes when configuration is updated
     */
    Promise<Void> updateConfig(KernelContext context, String connectorId, ConnectorConfig config);
}
