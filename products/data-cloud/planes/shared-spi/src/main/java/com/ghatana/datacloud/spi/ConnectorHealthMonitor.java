package com.ghatana.datacloud.spi;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for monitoring connector health and performing health checks.
 *
 * <p><b>Purpose</b><br>
 * Provides health monitoring capabilities for connectors, including periodic
 * health checks, status tracking, and failure detection. All operations are
 * tenant-scoped and return ActiveJ Promises for non-blocking execution.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * ConnectorHealthMonitor monitor = new DefaultConnectorHealthMonitorImpl();
 * Promise<Boolean> promise = monitor.performHealthCheck("tenant-123", "connector-456");
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Service for connector health monitoring
 * @doc.layer product
 * @doc.pattern Service Provider Interface
 */
public interface ConnectorHealthMonitor {

    /**
     * Performs a health check on a specific connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing true if the connector is healthy
     */
    Promise<Boolean> performHealthCheck(String tenantId, String connectorId);

    /**
     * Performs health checks on all connectors for a tenant.
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing health check results for all connectors
     */
    Promise<Map<String, HealthCheckResult>> performHealthChecksForTenant(String tenantId);

    /**
     * Starts periodic health monitoring for a connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param intervalSeconds the health check interval in seconds
     * @return Promise containing true if monitoring started successfully
     */
    Promise<Boolean> startHealthMonitoring(String tenantId, String connectorId, int intervalSeconds);

    /**
     * Stops periodic health monitoring for a connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing true if monitoring stopped successfully
     */
    Promise<Boolean> stopHealthMonitoring(String tenantId, String connectorId);

    /**
     * Gets the current health status of a connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @return Promise containing the current health status
     */
    Promise<ConnectorHealthStatus> getHealthStatus(String tenantId, String connectorId);

    /**
     * Gets health history for a connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param fromTime the start time for the history (optional)
     * @param toTime the end time for the history (optional)
     * @return Promise containing list of health check results
     */
    Promise<List<HealthCheckResult>> getHealthHistory(String tenantId, String connectorId,
                                                      Instant fromTime, Instant toTime);

    /**
     * Sets up health alerting for a connector.
     *
     * @param tenantId the tenant ID (required)
     * @param connectorId the connector ID (required)
     * @param alertConfig the alert configuration (required)
     * @return Promise containing true if alerting configured successfully
     */
    Promise<Boolean> configureHealthAlerts(String tenantId, String connectorId,
                                          HealthAlertConfig alertConfig);

    /**
     * Gets connectors with health issues for a tenant.
     *
     * @param tenantId the tenant ID (required)
     * @return Promise containing list of unhealthy connectors
     */
    Promise<List<String>> getUnhealthyConnectors(String tenantId);

    /**
     * Health check result.
     */
    record HealthCheckResult(
        String connectorId,
        String tenantId,
        boolean healthy,
        String status,
        long responseTimeMs,
        String errorMessage,
        Map<String, Object> metrics,
        Instant timestamp
    ) {}

    /**
     * Connector health status.
     */
    record ConnectorHealthStatus(
        String connectorId,
        String tenantId,
        boolean healthy,
        String status,
        Instant lastHealthCheck,
        Instant lastSuccessfulCheck,
        int consecutiveFailures,
        long averageResponseTimeMs,
        String lastError,
        boolean monitoringActive
    ) {}

    /**
     * Health alert configuration.
     */
    record HealthAlertConfig(
        boolean enabled,
        int failureThreshold,
        int alertIntervalMinutes,
        List<String> alertChannels,
        Map<String, Object> customRules
    ) {}

    /**
     * Health status enumeration.
     */
    enum HealthStatus {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN,
        CHECKING
    }
}
