package com.ghatana.datacloud.spi;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for managing connector lifecycle and state.
 *
 * <p><b>Purpose</b><br>
 * Provides a centralized registry for connector registration, activation,
 * deactivation, and status tracking. All operations are tenant-scoped and
 * return ActiveJ Promises for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ConnectorRegistry registry = new JpaConnectorRegistryImpl();
 * Promise<String> promise = registry.registerConnector(
 *     "tenant-123", "connector-456", "RELATIONAL", config, "user-789");
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Registry for connector lifecycle management
 * @doc.layer product
 * @doc.pattern Service Provider Interface
 */
public interface ConnectorRegistry {

    /**
     * Registers a new connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param connectorType the connector type (required)
     * @param configuration the connector configuration (required)
     * @param userId the user registering the connector (for audit)
     * @return Promise containing the connector ID
     */
    Promise<String> registerConnector(String tenantId, String connectorId, String connectorType,
                                    Map<String, Object> configuration, String userId);

    /**
     * Activates a registered connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing true if activated successfully
     */
    Promise<Boolean> activateConnector(String tenantId, String connectorId);

    /**
     * Deactivates a connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing true if deactivated successfully
     */
    Promise<Boolean> deactivateConnector(String tenantId, String connectorId);

    /**
     * Gets the status of a connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing connector status information
     */
    Promise<Map<String, Object>> getConnectorStatus(String tenantId, String connectorId);

    /**
     * Lists all connectors for a tenant.
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing list of connector information
     */
    Promise<java.util.List<Map<String, Object>>> listConnectors(String tenantId);

    /**
     * Removes a connector from the registry.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing true if removed successfully
     */
    Promise<Boolean> removeConnector(String tenantId, String connectorId);

    /**
     * Updates connector configuration.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param configuration the new configuration (required)
     * @param userId the user updating the configuration (for audit)
     * @return Promise containing true if updated successfully
     */
    Promise<Boolean> updateConnectorConfiguration(String tenantId, String connectorId,
                                                Map<String, Object> configuration, String userId);

    /**
     * Sets a fallback connector for primary connector failure.
     *
     * @param tenantId the tenant ID (required)
     * @param primaryConnectorId the primary connector ID (required)
     * @param fallbackConnectorId the fallback connector ID (required)
     * @return Promise containing true if fallback set successfully
     */
    Promise<Boolean> setFallbackConnector(String tenantId, String primaryConnectorId,
                                        String fallbackConnectorId);

    /**
     * Gets the fallback connector for a primary connector.
     *
     * @param tenantId the tenant ID (required)
     * @param primaryConnectorId the primary connector ID (required)
     * @return Promise containing optional fallback connector ID
     */
    Promise<Optional<String>> getFallbackConnector(String tenantId, String primaryConnectorId);

    /**
     * Connector status enumeration.
     */
    enum ConnectorStatus {
        REGISTERED,
        ACTIVE,
        FAILED,
        DEACTIVATED,
        HEALTH_CHECKING
    }

    /**
     * Connector state information.
     */
    record ConnectorState(
        String connectorId,
        String tenantId,
        String connectorType,
        ConnectorStatus status,
        Map<String, Object> configuration,
        String lastError,
        Instant lastHealthCheck,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy
    ) {}
}
