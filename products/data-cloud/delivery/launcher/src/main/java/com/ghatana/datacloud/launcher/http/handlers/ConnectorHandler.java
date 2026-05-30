package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.application.ConnectorService;
import com.ghatana.datacloud.launcher.http.HttpHandlerSupport;
import com.ghatana.platform.security.annotation.RequiresRole;
import com.ghatana.platform.security.annotation.Secured;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Handles connector management HTTP endpoints for production workflow.
 *
 * <p><b>Purpose</b><br>
 * Provides REST API endpoints for connector registration, activation, deactivation,
 * status monitoring, and health checks. Integrates with ConnectorService for
 * business logic and enforces RBAC at the HTTP level.
 *
 * <p><b>Security</b><br>
 * All connector operations require authentication. Role-based access control is enforced
 * based on the operation sensitivity and user roles. Write operations require EDITOR or higher role.
 *
 * @doc.type class
 * @doc.purpose Connector management HTTP handlers for production workflow
 * @doc.layer product
 * @doc.pattern Handler
 */
@Secured
public class ConnectorHandler {

    private static final Logger log = LoggerFactory.getLogger(ConnectorHandler.class);

    private final ConnectorService connectorService;
    private final HttpHandlerSupport http;

    /**
     * Creates a new connector handler.
     *
     * @param connectorService the connector service (required)
     * @param http the HTTP helper methods (required)
     */
    public ConnectorHandler(ConnectorService connectorService, HttpHandlerSupport http) {
        this.connectorService = connectorService;
        this.http = http;
    }

    /**
     * Registers and activates a connector for a data source.
     *
     * <p>POST /api/v1/connectors/{dataSourceName}/register
     *
     * @param request the HTTP request
     * @return promise that completes with the HTTP response
     */
    @RequiresRole({"EDITOR", "ADMIN"})
    public Promise<HttpResponse> registerConnector(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String dataSourceName = request.getPathParameter("dataSourceName");
        String userId = http.resolvePrincipalId(request);

        if (tenantId == null || dataSourceName == null || userId == null) {
            return Promise.of(http.errorResponse(400, "Missing required parameters"));
        }

        return connectorService.registerAndActivateConnector(tenantId, dataSourceName, userId)
            .map(result -> http.jsonResponse(Map.of(
                "message", "Connector registered and activated successfully",
                "dataSource", dataSourceName,
                "timestamp", java.time.Instant.now().toString()
            )))
            .mapEx(ex -> {
                log.error("Failed to register connector: tenantId={}, dataSource={}", 
                    tenantId, dataSourceName, ex);
                return http.errorResponse(500, "Failed to register connector: " + ex.getMessage());
            });
    }

    /**
     * Deactivates a connector for a data source.
     *
     * <p>POST /api/v1/connectors/{dataSourceName}/deactivate
     *
     * @param request the HTTP request
     * @return promise that completes with the HTTP response
     */
    @RequiresRole({"EDITOR", "ADMIN"})
    public Promise<HttpResponse> deactivateConnector(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String dataSourceName = request.getPathParameter("dataSourceName");
        String userId = http.resolvePrincipalId(request);

        if (tenantId == null || dataSourceName == null || userId == null) {
            return Promise.of(http.errorResponse(400, "Missing required parameters"));
        }

        return connectorService.deactivateConnector(tenantId, dataSourceName, userId)
            .map(result -> http.jsonResponse(Map.of(
                "message", "Connector deactivated successfully",
                "dataSource", dataSourceName,
                "timestamp", java.time.Instant.now().toString()
            )))
            .mapEx(ex -> {
                log.error("Failed to deactivate connector: tenantId={}, dataSource={}", 
                    tenantId, dataSourceName, ex);
                return http.errorResponse(500, "Failed to deactivate connector: " + ex.getMessage());
            });
    }

    /**
     * Gets connector status for a data source.
     *
     * <p>GET /api/v1/connectors/{dataSourceName}/status
     *
     * @param request the HTTP request
     * @return promise that completes with the HTTP response
     */
    @RequiresRole({"VIEWER", "EDITOR", "ADMIN"})
    public Promise<HttpResponse> getConnectorStatus(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String dataSourceName = request.getPathParameter("dataSourceName");

        if (tenantId == null || dataSourceName == null) {
            return Promise.of(http.errorResponse(400, "Missing required parameters"));
        }

        return connectorService.getConnectorStatus(tenantId, dataSourceName)
            .map(status -> http.jsonResponse(status))
            .mapEx(ex -> {
                log.error("Failed to get connector status: tenantId={}, dataSource={}", 
                    tenantId, dataSourceName, ex);
                return http.errorResponse(500, "Failed to get connector status: " + ex.getMessage());
            });
    }

    /**
     * Lists all connectors for a tenant.
     *
     * <p>GET /api/v1/connectors
     *
     * @param request the HTTP request
     * @return promise that completes with the HTTP response
     */
    @RequiresRole({"VIEWER", "EDITOR", "ADMIN"})
    public Promise<HttpResponse> listConnectors(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);

        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Missing tenant ID"));
        }

        return connectorService.listConnectors(tenantId)
            .map(connectors -> http.jsonResponse(Map.of(
                "connectors", connectors,
                "count", connectors.size(),
                "tenantId", tenantId,
                "timestamp", java.time.Instant.now().toString()
            )))
            .mapEx(ex -> {
                log.error("Failed to list connectors: tenantId={}", tenantId, ex);
                return http.errorResponse(500, "Failed to list connectors: " + ex.getMessage());
            });
    }

    /**
     * Performs health checks on all connectors for a tenant.
     *
     * <p>POST /api/v1/connectors/health-check
     *
     * @param request the HTTP request
     * @return promise that completes with the HTTP response
     */
    @RequiresRole({"VIEWER", "EDITOR", "ADMIN"})
    public Promise<HttpResponse> performHealthChecks(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);

        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "Missing tenant ID"));
        }

        return connectorService.performHealthChecks(tenantId)
            .map(results -> http.jsonResponse(results))
            .mapEx(ex -> {
                log.error("Failed to perform health checks: tenantId={}", tenantId, ex);
                return http.errorResponse(500, "Failed to perform health checks: " + ex.getMessage());
            });
    }

    /**
     * Gets health check status for a specific connector.
     *
     * <p>GET /api/v1/connectors/{dataSourceName}/health
     *
     * @param request the HTTP request
     * @return promise that completes with the HTTP response
     */
    @RequiresRole({"VIEWER", "EDITOR", "ADMIN"})
    public Promise<HttpResponse> getConnectorHealth(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String dataSourceName = request.getPathParameter("dataSourceName");

        if (tenantId == null || dataSourceName == null) {
            return Promise.of(http.errorResponse(400, "Missing required parameters"));
        }

        return connectorService.getConnectorStatus(tenantId, dataSourceName)
            .then(status -> {
                // Extract health-related information from status
                Map<String, Object> healthInfo = Map.of(
                    "connectorId", status.get("connectorId"),
                    "dataSourceName", dataSourceName,
                    "status", status.get("status"),
                    "lastHealthCheck", status.get("lastHealthCheck"),
                    "lastError", status.get("lastError"),
                    "syncStats", status.get("syncStats"),
                    "lastSyncedAt", status.get("lastSyncedAt"),
                    "healthy", "ACTIVE".equals(status.get("status")) && 
                              status.get("lastError") == null
                );
                return Promise.of(http.jsonResponse(healthInfo));
            })
            .mapEx(ex -> {
                log.error("Failed to get connector health: tenantId={}, dataSource={}", 
                    tenantId, dataSourceName, ex);
                return http.errorResponse(500, "Failed to get connector health: " + ex.getMessage());
            });
    }

    /**
     * Restarts a connector (deactivate then reactivate).
     *
     * <p>POST /api/v1/connectors/{dataSourceName}/restart
     *
     * @param request the HTTP request
     * @return promise that completes with the HTTP response
     */
    @RequiresRole({"EDITOR", "ADMIN"})
    public Promise<HttpResponse> restartConnector(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String dataSourceName = request.getPathParameter("dataSourceName");
        String userId = http.resolvePrincipalId(request);

        if (tenantId == null || dataSourceName == null || userId == null) {
            return Promise.of(http.errorResponse(400, "Missing required parameters"));
        }

        return connectorService.deactivateConnector(tenantId, dataSourceName, userId)
            .then(result -> connectorService.registerAndActivateConnector(tenantId, dataSourceName, userId))
            .map(regResult -> http.jsonResponse(Map.of(
                "message", "Connector restarted successfully",
                "dataSource", dataSourceName,
                "timestamp", java.time.Instant.now().toString()
            )))
            .mapEx(ex -> {
                log.error("Failed to restart connector: tenantId={}, dataSource={}", 
                    tenantId, dataSourceName, ex);
                return http.errorResponse(500, "Failed to restart connector: " + ex.getMessage());
            });
    }

    /**
     * Gets connector metrics for monitoring dashboards.
     *
     * <p>GET /api/v1/connectors/{dataSourceName}/metrics
     *
     * @param request the HTTP request
     * @return promise that completes with the HTTP response
     */
    @RequiresRole({"VIEWER", "EDITOR", "ADMIN"})
    public Promise<HttpResponse> getConnectorMetrics(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String dataSourceName = request.getPathParameter("dataSourceName");

        if (tenantId == null || dataSourceName == null) {
            return Promise.of(http.errorResponse(400, "Missing required parameters"));
        }

        return connectorService.getConnectorStatus(tenantId, dataSourceName)
            .then(status -> {
                // Extract and format metrics for monitoring
                Map<String, Object> metrics = Map.of(
                    "connectorId", status.get("connectorId"),
                    "dataSourceName", dataSourceName,
                    "type", status.get("type"),
                    "status", status.get("status"),
                    "lastHealthCheck", status.get("lastHealthCheck"),
                    "syncStats", status.get("syncStats"),
                    "lastSyncedAt", status.get("lastSyncedAt"),
                    "healthy", "ACTIVE".equals(status.get("status")) && 
                              status.get("lastError") == null,
                    "hasError", status.get("lastError") != null,
                    "isSyncing", status.get("syncStats") != null &&
                                   ((Map<String, Object>) status.get("syncStats")).containsKey("status") &&
                                   !"stopped".equals(((Map<String, Object>) status.get("syncStats")).get("status"))
                );
                return Promise.of(http.jsonResponse(metrics));
            })
            .mapEx(ex -> {
                log.error("Failed to get connector metrics: tenantId={}, dataSource={}", 
                    tenantId, dataSourceName, ex);
                return http.errorResponse(500, "Failed to get connector metrics: " + ex.getMessage());
            });
    }
}
