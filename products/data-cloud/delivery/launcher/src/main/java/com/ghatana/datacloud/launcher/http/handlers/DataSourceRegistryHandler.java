package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.fabric.DataFabricConnector;
import com.ghatana.datacloud.feature.DataCloudFeature;
import com.ghatana.datacloud.feature.DataCloudFeatureFlags;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.datacloud.launcher.http.security.RequestContextResolver;
import com.ghatana.datacloud.operations.OperationKind;
import com.ghatana.datacloud.operations.OperationRecord;
import com.ghatana.datacloud.operations.OperationRecorder;
import com.ghatana.datacloud.operations.OperationStatus;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.observability.idempotency.IdempotencyHelper;
import com.ghatana.platform.observability.idempotency.IdempotencyStore;
import com.ghatana.platform.security.annotation.RequiresRole;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * HTTP handler for data source/connector registry (P1.1).
 *
 * <p>Manages external data source connections as metadata entities in the
 * {@code dc_connections} collection. Each connection record stores configuration,
 health status, schema snapshots, and sync state.
 *
 * <p>When a {@link DataFabricConnector} implementation is available, the handler
 * delegates test, sync, schema, and health operations to it. When unavailable,
 * the handler stores the request as a pending operation and returns a degraded
 * but truthful response.
 *
 * <p>Routes served:
 * <ul>
 *   <li>{@code GET  /api/v1/connectors}                       — list all connections for tenant</li>
 *   <li>{@code POST /api/v1/connectors}                       — register a new connection</li>
 *   <li>{@code GET  /api/v1/connectors/:connectionId}         — get connection by ID</li>
 *   <li>{@code POST /api/v1/connectors/:connectionId/test}    — test connection</li>
 *   <li>{@code POST /api/v1/connectors/:connectionId/enable}  — enable connection</li>
 *   <li>{@code POST /api/v1/connectors/:connectionId/disable} — disable connection</li>
 *   <li>{@code POST /api/v1/connectors/:connectionId/rotate-credentials} — rotate credentials</li>
 *   <li>{@code GET  /api/v1/connectors/:connectionId/health}  — health status</li>
 *   <li>{@code GET  /api/v1/connectors/:connectionId/schema}    — inferred schema</li>
 *   <li>{@code POST /api/v1/connectors/:connectionId/sync}      — trigger sync</li>
 *   <li>{@code GET  /api/v1/connectors/:connectionId/sync/status} — sync status</li>
 *   <li>{@code DELETE /api/v1/connectors/:connectionId}          — delete connection</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose HTTP surface for external data source connector registry
 * @doc.layer product
 * @doc.pattern Handler
 */
@RequiresRole("ADMIN")
public final class DataSourceRegistryHandler {

    private static final Logger log = LoggerFactory.getLogger(DataSourceRegistryHandler.class);
    private static final HttpResponse NO_IDEMPOTENCY_RESPONSE = null;
    private static final String DC_CONNECTIONS = "dc_connections";
    private static final String CREDENTIALS_KEY = "credentials";
    private static final String SECRET_REFERENCE_KEY = "secretRef";
    private static final Set<String> VALID_TYPES = Set.of(
        "POSTGRESQL", "MYSQL", "MONGODB", "S3", "REST_API", "KAFKA", "SNOWFLAKE", "BIGQUERY", "CUSTOM"
    );
    private static final Set<String> VALID_STATES = Set.of(
        "ACTIVE", "INACTIVE", "TESTING", "ERROR", "SYNCING"
    );

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final DataFabricConnector fabric;
    private final AuditService auditService;
    private final String deploymentProfile;
    private final IdempotencyStore idempotencyStore;
    private OperationRecorder operationRecorder;

    /**
     * @param client       entity store client for persisting connection metadata
     * @param http         shared HTTP helper
     * @param fabric       optional DataFabricConnector implementation; may be null
     * @param auditService optional audit service; when null audit emissions are skipped
     * @param deploymentProfile deployment profile (e.g., "local", "sovereign", "staging", "production")
     * @param idempotencyStore idempotency store for connector operations
     */
    public DataSourceRegistryHandler(DataCloudClient client, HttpHandlerSupport http,
                                     DataFabricConnector fabric, AuditService auditService,
                                     String deploymentProfile, IdempotencyStore idempotencyStore) {
        this.client = client;
        this.http = http;
        this.fabric = fabric;
        this.auditService = auditService;
        this.deploymentProfile = deploymentProfile != null ? deploymentProfile : "local";
        this.idempotencyStore = idempotencyStore;
    }

    /**
     * @param client       entity store client for persisting connection metadata
     * @param http         shared HTTP helper
     * @param fabric       optional DataFabricConnector implementation; may be null
     * @param auditService optional audit service; when null audit emissions are skipped
     * @param deploymentProfile deployment profile (e.g., "local", "sovereign", "staging", "production")
     */
    public DataSourceRegistryHandler(DataCloudClient client, HttpHandlerSupport http,
                                     DataFabricConnector fabric, AuditService auditService,
                                     String deploymentProfile) {
        this(client, http, fabric, auditService, deploymentProfile, null);
    }

    /**
     * @param client       entity store client for persisting connection metadata
     * @param http         shared HTTP helper
     * @param fabric       optional DataFabricConnector implementation; may be null
     * @param auditService optional audit service; when null audit emissions are skipped
     */
    public DataSourceRegistryHandler(DataCloudClient client, HttpHandlerSupport http,
                                     DataFabricConnector fabric, AuditService auditService) {
        this(client, http, fabric, auditService, "local");
    }

    public DataSourceRegistryHandler withOperationRecorder(OperationRecorder operationRecorder) {
        this.operationRecorder = operationRecorder;
        return this;
    }

    // ─── Helper Methods (P0-09) ─────────────────────────────────────────────────

    /**
     * P0-09: Check if connector runtime is required for the current deployment profile.
     * In production/staging/sovereign profiles, connector runtime must be available.
     */
    private boolean isConnectorRuntimeRequired() {
        if (deploymentProfile == null) return false;
        String lower = deploymentProfile.trim().toLowerCase();
        return !lower.equals("local") && !lower.equals("embedded") && !lower.equals("test");
    }

    /**
     * P0-09: Return 503 error if connector runtime is required but unavailable.
     */
    private Promise<HttpResponse> connectorRuntimeUnavailableResponse(String operation) {
        log.error("[P0-09] Connector runtime is required for {} operation in profile '{}' but is unavailable - rejecting request",
                  operation, deploymentProfile);
        return Promise.of(http.errorResponse(503,
            "Connector runtime is required for " + operation + " operations in this deployment profile. " +
            "This deployment does not have a configured connector runtime."));
    }

    // ─── Helper Methods (P0-07) ─────────────────────────────────────────────────

    /**
     * P0-07: Check idempotency for connector operations.
     */
    private Promise<HttpResponse> checkIdempotency(String tenantId, String connectionId, String routeAction, HttpRequest request) {
        if (idempotencyStore == null) {
            return Promise.of(NO_IDEMPOTENCY_RESPONSE);
        }

        String idempotencyKey = IdempotencyHelper.extractIdempotencyKey(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(NO_IDEMPOTENCY_RESPONSE);
        }

        String principalId = http.resolvePrincipalId(request);
        String scope = "connector:" + routeAction + ":" + connectionId;

        return IdempotencyHelper.checkConflict(idempotencyStore, tenantId, scope, idempotencyKey, principalId,
            IdempotencyHelper.computePayloadHash(request))
            .then(hasConflict -> {
                if (hasConflict) {
                    log.warn("[P0-07] Idempotency conflict for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
                    return Promise.of(http.errorResponse(409,
                        "Idempotency key conflict: same key used with different payload"));
                }

                return IdempotencyHelper.checkIdempotency(idempotencyStore, tenantId, scope, idempotencyKey, principalId)
                    .then(cachedResponse -> {
                        if (cachedResponse != null) {
                            log.info("[P0-07] Returning cached response for tenant={}, scope={}, key={}", tenantId, scope, idempotencyKey);
                            if (cachedResponse instanceof HttpResponse) {
                                return Promise.of(IdempotencyHelper.addIdempotencyHeaders((HttpResponse) cachedResponse, "replay"));
                            }
                            if (cachedResponse instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = (Map<String, Object>) cachedResponse;
                                return Promise.of(http.jsonResponse(map));
                            }
                            return Promise.of(http.jsonResponse(Map.of("data", cachedResponse)));
                        }
                        return Promise.of((HttpResponse) null);
                    });
            });
    }

    /**
     * P0-07: Store response for idempotent connector operations.
     */
    private Promise<Void> storeIdempotency(String tenantId, String connectionId, String routeAction,
                                          HttpRequest request, Object response) {
        if (idempotencyStore == null) {
            return Promise.of(null);
        }

        String idempotencyKey = IdempotencyHelper.extractIdempotencyKey(request);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Promise.of(null);
        }

        String principalId = http.resolvePrincipalId(request);
        String scope = "connector:" + routeAction + ":" + connectionId;
        String payloadHash = IdempotencyHelper.computePayloadHash(request);

        return IdempotencyHelper.storeResponse(idempotencyStore, tenantId, scope, idempotencyKey, principalId, payloadHash, response);
    }

    // ─── GET /api/v1/connectors ───────────────────────────────────────────────

    public Promise<HttpResponse> handleListConnections(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        return client.query(tenantId, DC_CONNECTIONS, DataCloudClient.Query.limit(500))
            .map(entities -> {
                List<Map<String, Object>> items = entities.stream()
                    .map(e -> toConnectionView(e.data(), e.id()))
                    .toList();
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "connections", items,
                    "count", items.size(),
                    "fabricAvailable", fabric != null,
                    "timestamp", Instant.now().toString()
                ));
            });
    }

    // ─── POST /api/v1/connectors ──────────────────────────────────────────────

    public Promise<HttpResponse> handleRegisterConnection(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        // Require permission for connector registration
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:register");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        // P0-07: Check idempotency before processing
        return request.loadBody().then(buf -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = http.objectMapper().readValue(
                    buf.getString(StandardCharsets.UTF_8), Map.class);

                Optional<String> validation = validateConnectionPayload(payload);
                if (validation.isPresent()) {
                    return Promise.of(http.errorResponse(400, validation.get()));
                }

                String id = payload.containsKey("id") ? (String) payload.get("id") : java.util.UUID.randomUUID().toString();

                // P0-07: Check idempotency for register operation
                return checkIdempotency(tenantId, id, "register", request)
                    .then(idempotencyResponse -> {
                        if (idempotencyResponse != null) {
                            return Promise.of(idempotencyResponse);
                        }

                        Map<String, Object> data = new LinkedHashMap<>(payload);
                        applyCredentialStoragePolicy(data, payload);
                        data.put("tenantId", tenantId);
                        data.put("id", id);
                        data.put("createdAt", Instant.now().toString());
                        data.put("updatedAt", Instant.now().toString());
                        data.put("state", data.getOrDefault("state", "INACTIVE"));
                        if (!data.containsKey("healthStatus")) {
                            data.put("healthStatus", "unknown");
                        }

                        return client.save(tenantId, DC_CONNECTIONS, data)
                            .map(saved -> {
                                emitConnectorAudit(tenantId, saved.id(), "CONNECTOR_CREATED", true);
                                Map<String, Object> responseBody = Map.of(
                                    "tenantId", tenantId,
                                    "connectionId", saved.id(),
                                    "state", data.get("state"),
                                    "created", true,
                                    "timestamp", Instant.now().toString()
                                );

                                // P0-07: Store response for idempotency
                                storeIdempotency(tenantId, saved.id(), "register", request, responseBody);

                                return http.jsonResponse(201, responseBody);
                            });
                    });
            } catch (Exception e) {
                log.error("[registerConnection] tenant={} failed: {}", tenantId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Connection registration failed: " + e.getMessage()));
            }
        });
    }

    // ─── GET /api/v1/connectors/:connectionId ─────────────────────────────────

    public Promise<HttpResponse> handleGetConnection(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        return client.findById(tenantId, DC_CONNECTIONS, connectionId)
            .map(opt -> opt.map(e -> http.jsonResponse(toConnectionView(e.data(), e.id())))
                .orElseGet(() -> http.errorResponse(404, "Connection not found: " + connectionId)));
    }

    // ─── POST /api/v1/connectors/:connectionId/test ───────────────────────────

    public Promise<HttpResponse> handleTestConnection(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:test");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }

        // P0-09: Fail closed when connector runtime is unavailable in production/staging/sovereign profiles
        if (isConnectorRuntimeRequired() && fabric == null) {
            recordConnectorOperation(
                tenantId,
                connectionId,
                OperationKind.CONNECTOR_TEST,
                OperationStatus.BLOCKED,
                "Connector test",
                "Connector runtime is required but unavailable",
                request,
                Map.of("deploymentProfile", deploymentProfile));
            return connectorRuntimeUnavailableResponse("connector test");
        }

        if (fabric == null) {
            // DC-P2-006: When no fabric connector is available, do NOT mutate the connection state.
            // Setting state to TESTING creates a phantom state that never resolves; return pending instead.
            // This path is only allowed in local/test profiles where connector runtime is optional.
            OperationRecord operation = recordConnectorOperation(
                tenantId,
                connectionId,
                OperationKind.CONNECTOR_TEST,
                OperationStatus.BLOCKED,
                "Connector test",
                "Data fabric connector not available; test cannot be performed",
                request,
                Map.of("deploymentProfile", deploymentProfile));
            return Promise.of(http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "connectionId", connectionId,
                "operationId", operation == null ? "" : operation.operationId(),
                "testStatus", "pending",
                "message", "Data fabric connector not available; test cannot be performed",
                "timestamp", Instant.now().toString()
            )));
        }
        OperationRecord operation = recordConnectorOperation(
            tenantId,
            connectionId,
            OperationKind.CONNECTOR_TEST,
            OperationStatus.RUNNING,
            "Connector test",
            "Connector test requested",
            request,
            Map.of());
        String operationId = operation == null ? "" : operation.operationId();
        return fabric.testConnection(tenantId, connectionId)
            .map(result -> {
                String healthStatus = result.success() ? "healthy" : "unhealthy";
                String state = result.success() ? "ACTIVE" : "ERROR";
                updateConnectionStateAsync(tenantId, connectionId, state, healthStatus);
                emitConnectorAudit(tenantId, connectionId, "CONNECTOR_TESTED", result.success());
                transitionOperation(
                    tenantId,
                    operationId,
                    result.success() ? OperationStatus.SUCCEEDED : OperationStatus.FAILED,
                    result.message(),
                    Map.of("latencyMs", result.latencyMs(), "healthStatus", healthStatus));
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "connectionId", connectionId,
                    "operationId", operationId,
                    "testStatus", result.success() ? "passed" : "failed",
                    "message", result.message(),
                    "latencyMs", result.latencyMs(),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(
                r -> Promise.of(r),
                e -> {
                    log.error("[testConnection] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                    updateConnectionStateAsync(tenantId, connectionId, "ERROR", "unhealthy");
                    emitConnectorAudit(tenantId, connectionId, "CONNECTOR_TEST_FAILED", false);
                    transitionOperation(tenantId, operationId, OperationStatus.FAILED, e.getMessage(), Map.of());
                    return Promise.of(http.errorResponse(502, "Test failed: " + e.getMessage()));
                });
    }

    // ─── POST /api/v1/connectors/:connectionId/enable ─────────────────────────

    /**
     * Enables a connector after live validation.
     *
     * <p>In production profiles, enable requires successful test connection validation.
     * The connector state transitions: TESTING -> (validation) -> ACTIVE|ERROR.
     * This prevents marking connectors active when they cannot actually connect.
     *
     * @param request HTTP request with connectionId path parameter
     * @return Promise with enable result
     */
    public Promise<HttpResponse> handleEnableConnection(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:update");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }

        // P0-09: Fail closed when connector runtime is unavailable in production/staging/sovereign profiles
        if (isConnectorRuntimeRequired() && fabric == null) {
            return connectorRuntimeUnavailableResponse("connector enable");
        }

        // P0-07: Check idempotency before processing
        return checkIdempotency(tenantId, connectionId, "enable", request)
            .then(idempotencyResponse -> {
                if (idempotencyResponse != null) {
                    return Promise.of(idempotencyResponse);
                }

                // P1-6: Require fabric connector for production-grade enable
                if (fabric == null) {
                    log.warn("[enableConnection] tenant={} id={} - Cannot enable without DataFabricConnector", tenantId, connectionId);
                    return Promise.of(http.errorResponse(503,
                        "Connector enable requires DataFabricConnector. " +
                        "This deployment does not have a configured connector runtime."));
                }

                // Set state to TESTING during validation
                return updateConnectionState(tenantId, connectionId, "TESTING", "validating")
                    .then(e -> fabric.testConnection(tenantId, connectionId))
                    .map(result -> {
                        if (result.success()) {
                            // Validation passed - mark ACTIVE
                            updateConnectionStateAsync(tenantId, connectionId, "ACTIVE", "healthy");
                            emitConnectorAudit(tenantId, connectionId, "CONNECTOR_ENABLED", true);
                            Map<String, Object> responseBody = Map.of(
                                "tenantId", tenantId,
                                "connectionId", connectionId,
                                "state", "ACTIVE",
                                "enabled", true,
                                "timestamp", Instant.now().toString()
                            );
                            storeIdempotency(tenantId, connectionId, "enable", request, responseBody);
                            return http.jsonResponse(responseBody);
                        } else {
                            // Validation failed - mark ERROR
                            updateConnectionStateAsync(tenantId, connectionId, "ERROR", "validation_failed");
                            return http.jsonResponse(400, Map.of(
                                "tenantId", tenantId,
                                "connectionId", connectionId,
                                "state", "ERROR",
                                "enabled", false,
                                "error", result.message(),
                                "timestamp", Instant.now().toString()
                            ));
                        }
                    })
                    .then(Promise::of, e -> {
                        log.error("[enableConnection] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                        updateConnectionStateAsync(tenantId, connectionId, "ERROR", "internal_error");
                        return Promise.of(http.errorResponse(500, "Failed to enable connection: " + e.getMessage()));
                    });
            })
            .then(Promise::of, e -> {
                log.error("[enableConnection] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to enable connection: " + e.getMessage()));
            });
    }

    // ─── POST /api/v1/connectors/:connectionId/disable ──────────────────────────

    public Promise<HttpResponse> handleDisableConnection(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:update");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }

        // P0-07: Check idempotency before processing
        return checkIdempotency(tenantId, connectionId, "disable", request)
            .then(idempotencyResponse -> {
                if (idempotencyResponse != null) {
                    return Promise.of(idempotencyResponse);
                }

                if (fabric != null) {
                    // Note: disconnect is not in the new DataFabricConnector interface
                    // This is a placeholder for when it's added
                    return updateConnectionState(tenantId, connectionId, "INACTIVE", "disabled")
                        .map(e -> {
                            emitConnectorAudit(tenantId, connectionId, "CONNECTOR_DISABLED", true);
                            Map<String, Object> responseBody = Map.of(
                                "tenantId", tenantId,
                                "connectionId", connectionId,
                                "state", "INACTIVE",
                                "enabled", false,
                                "timestamp", Instant.now().toString()
                            );
                            storeIdempotency(tenantId, connectionId, "disable", request, responseBody);
                            return http.jsonResponse(responseBody);
                        })
                        .then(Promise::of, e -> {
                            log.error("[disableConnection] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                            return updateConnectionState(tenantId, connectionId, "INACTIVE", "disabled")
                                .map(updated -> http.jsonResponse(Map.of(
                                    "tenantId", tenantId,
                                    "connectionId", connectionId,
                                    "state", "INACTIVE",
                                    "enabled", false,
                                    "timestamp", Instant.now().toString(),
                                    "message", "Connector disabled (fabric error, state updated)"
                                )));
                        });
                }
                return updateConnectionState(tenantId, connectionId, "INACTIVE", "disabled")
                    .map(e -> {
                        emitConnectorAudit(tenantId, connectionId, "CONNECTOR_DISABLED", true);
                        Map<String, Object> responseBody = Map.of(
                            "tenantId", tenantId,
                            "connectionId", connectionId,
                            "state", "INACTIVE",
                            "enabled", false,
                            "timestamp", Instant.now().toString()
                        );
                        storeIdempotency(tenantId, connectionId, "disable", request, responseBody);
                        return http.jsonResponse(responseBody);
                    });
            })
            .then(Promise::of, e -> {
                log.error("[disableConnection] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to disable connection: " + e.getMessage()));
            });
    }

    // ─── POST /api/v1/connectors/:connectionId/rotate-credentials ───────────────

    public Promise<HttpResponse> handleRotateCredentials(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        // Require permission for credential rotation
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:rotate-credentials");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = buf == null || buf.readRemaining() == 0
                    ? Map.of()
                    : http.objectMapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                if (payload.containsKey(CREDENTIALS_KEY)) {
                    return Promise.of(http.errorResponse(400,
                        "Raw credentials payload is not accepted. Provide secretRef metadata instead."));
                }
                return client.findById(tenantId, DC_CONNECTIONS, connectionId)
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.of(http.errorResponse(404, "Connection not found: " + connectionId));
                        }
                        OperationRecord operation = recordConnectorOperation(
                            tenantId,
                            connectionId,
                            OperationKind.CONNECTOR_CREDENTIAL_ROTATION,
                            OperationStatus.RUNNING,
                            "Connector credential rotation",
                            "Credential rotation requested",
                            request,
                            Map.of("secretRefProvided", payload.containsKey(SECRET_REFERENCE_KEY)));
                        String operationId = operation == null ? "" : operation.operationId();
                        DataCloudClient.Entity existing = opt.get();
                        Map<String, Object> data = new LinkedHashMap<>(existing.data());
                        data.put("updatedAt", Instant.now().toString());
                        data.put("lastCredentialRotation", Instant.now().toString());
                        data.remove(CREDENTIALS_KEY);
                        if (payload.containsKey(SECRET_REFERENCE_KEY)) {
                            data.put(SECRET_REFERENCE_KEY, payload.get(SECRET_REFERENCE_KEY));
                            data.put("credentialStatus", "rotated");
                        }
                        return client.save(tenantId, DC_CONNECTIONS, data)
                            .map(saved -> {
                                emitConnectorAudit(tenantId, connectionId, "CONNECTOR_CREDENTIALS_ROTATED", true);
                                transitionOperation(
                                    tenantId,
                                    operationId,
                                    OperationStatus.SUCCEEDED,
                                    "Credential rotation metadata stored",
                                    Map.of("credentialStatus", data.getOrDefault("credentialStatus", "unchanged")));
                                return http.jsonResponse(Map.of(
                                    "tenantId", tenantId,
                                    "connectionId", connectionId,
                                    "operationId", operationId,
                                    "rotated", true,
                                    "timestamp", Instant.now().toString()
                                ));
                            })
                            .then(
                                r -> Promise.of(r),
                                e -> {
                                    transitionOperation(tenantId, operationId, OperationStatus.FAILED, e.getMessage(), Map.of());
                                    return Promise.of(http.errorResponse(500, "Credential rotation failed: " + e.getMessage()));
                                });
                    });
            } catch (Exception e) {
                log.error("[rotateCredentials] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Credential rotation failed: " + e.getMessage()));
            }
        });
    }

    // ─── GET /api/v1/connectors/:connectionId/health ──────────────────────────

    public Promise<HttpResponse> handleGetHealth(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        // Require permission for health check
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        return client.findById(tenantId, DC_CONNECTIONS, connectionId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(http.errorResponse(404, "Connection not found: " + connectionId));
                }
                Map<String, Object> data = opt.get().data();
                String state = String.valueOf(data.getOrDefault("state", "UNKNOWN"));
                String healthStatus = String.valueOf(data.getOrDefault("healthStatus", "unknown"));

                if (fabric != null && "ACTIVE".equals(state)) {
                    OperationRecord operation = recordConnectorOperation(
                        tenantId,
                        connectionId,
                        OperationKind.CONNECTOR_HEALTH,
                        OperationStatus.RUNNING,
                        "Connector health",
                        "Live connector health requested",
                        request,
                        Map.of("previousHealthStatus", healthStatus));
                    String operationId = operation == null ? "" : operation.operationId();
                    return fabric.testConnection(tenantId, connectionId)
                        .map(result -> {
                            String liveHealth = result.success() ? "healthy" : "degraded";
                            updateConnectionStateAsync(tenantId, connectionId, state, liveHealth);
                            transitionOperation(
                                tenantId,
                                operationId,
                                result.success() ? OperationStatus.SUCCEEDED : OperationStatus.FAILED,
                                result.message(),
                                Map.of("latencyMs", result.latencyMs(), "healthStatus", liveHealth));
                            return http.jsonResponse(Map.of(
                                "tenantId", tenantId,
                                "connectionId", connectionId,
                                "operationId", operationId,
                                "state", state,
                                "healthStatus", liveHealth,
                                "lastTestMessage", result.message(),
                                "latencyMs", result.latencyMs(),
                                "timestamp", Instant.now().toString()
                            ));
                        })
                        .then(
                            r -> Promise.of(r),
                            e -> {
                                updateConnectionStateAsync(tenantId, connectionId, "ERROR", "unhealthy");
                                transitionOperation(tenantId, operationId, OperationStatus.FAILED, e.getMessage(), Map.of());
                                return Promise.of(http.jsonResponse(Map.of(
                                    "tenantId", tenantId,
                                    "connectionId", connectionId,
                                    "operationId", operationId,
                                    "state", "ERROR",
                                    "healthStatus", "unhealthy",
                                    "error", e.getMessage(),
                                    "timestamp", Instant.now().toString()
                                )));
                            });
                }
                OperationRecord operation = recordConnectorOperation(
                    tenantId,
                    connectionId,
                    OperationKind.CONNECTOR_HEALTH,
                    OperationStatus.SUCCEEDED,
                    "Connector health",
                    "Cached connector health returned",
                    request,
                    Map.of("healthStatus", healthStatus, "liveCheck", false));
                return Promise.of(http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "connectionId", connectionId,
                    "operationId", operation == null ? "" : operation.operationId(),
                    "state", state,
                    "healthStatus", healthStatus,
                    "timestamp", Instant.now().toString()
                )));
            });
    }

    // ─── GET /api/v1/connectors/:connectionId/schema ───────────────────────────

    public Promise<HttpResponse> handleGetSchema(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        // Require permission for schema read
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }

        // P0-09: Fail closed when connector runtime is unavailable in production/staging/sovereign profiles
        if (isConnectorRuntimeRequired() && fabric == null) {
            recordConnectorOperation(
                tenantId,
                connectionId,
                OperationKind.CONNECTOR_SCHEMA,
                OperationStatus.BLOCKED,
                "Connector schema",
                "Connector runtime is required but unavailable",
                request,
                Map.of("deploymentProfile", deploymentProfile));
            return connectorRuntimeUnavailableResponse("connector schema retrieval");
        }

        if (fabric == null) {
            // This path is only allowed in local/test profiles where connector runtime is optional.
            recordConnectorOperation(
                tenantId,
                connectionId,
                OperationKind.CONNECTOR_SCHEMA,
                OperationStatus.BLOCKED,
                "Connector schema",
                "Data fabric connector not available",
                request,
                Map.of("deploymentProfile", deploymentProfile));
            return Promise.of(http.errorResponse(503, "Data fabric connector not available"));
        }
        OperationRecord operation = recordConnectorOperation(
            tenantId,
            connectionId,
            OperationKind.CONNECTOR_SCHEMA,
            OperationStatus.RUNNING,
            "Connector schema",
            "Connector schema requested",
            request,
            Map.of());
        String operationId = operation == null ? "" : operation.operationId();
        return fabric.inferSchema(tenantId, connectionId, Map.of())
            .map(schema -> {
                transitionOperation(tenantId, operationId, OperationStatus.SUCCEEDED, "Connector schema retrieved", Map.of());
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "connectionId", connectionId,
                    "operationId", operationId,
                    "schema", schema,
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(
                r -> Promise.of(r),
                e -> {
                    log.error("[getSchema] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                    transitionOperation(tenantId, operationId, OperationStatus.FAILED, e.getMessage(), Map.of());
                    return Promise.of(http.errorResponse(502, "Schema retrieval failed: " + e.getMessage()));
                });
    }

    // ─── POST /api/v1/connectors/:connectionId/sync ────────────────────────────

    public Promise<HttpResponse> handleTriggerSync(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        // Require permission for sync trigger
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:sync");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }

        // P0-09: Fail closed when connector runtime is unavailable in production/staging/sovereign profiles
        if (isConnectorRuntimeRequired() && fabric == null) {
            recordConnectorOperation(
                tenantId,
                connectionId,
                OperationKind.CONNECTOR_SYNC,
                OperationStatus.BLOCKED,
                "Connector sync",
                "Connector runtime is required but unavailable",
                request,
                Map.of("deploymentProfile", deploymentProfile));
            return connectorRuntimeUnavailableResponse("connector sync");
        }

        // P0-07: Check idempotency before processing
        return checkIdempotency(tenantId, connectionId, "sync", request)
            .then(idempotencyResponse -> {
                if (idempotencyResponse != null) {
                    return Promise.of(idempotencyResponse);
                }

                try {
                    var buf = request.getBody();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = buf == null || buf.readRemaining() == 0
                        ? Map.of()
                    : http.objectMapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                // Build a minimal SyncRequest from payload
                @SuppressWarnings("unchecked")
                Map<String, Object> filters = payload.containsKey("filters") ? (Map<String, Object>) payload.get("filters") : Map.of();
                @SuppressWarnings("unchecked")
                Map<String, Object> mapping = payload.containsKey("mapping") ? (Map<String, Object>) payload.get("mapping") : Map.of();
                @SuppressWarnings("unchecked")
                List<String> columns = payload.containsKey("columns") ? (List<String>) payload.get("columns") : List.of();
                DataFabricConnector.SyncRequest syncRequest = new DataFabricConnector.SyncRequest(
                    (String) payload.get("targetCollection"),
                    (String) payload.getOrDefault("syncMode", "FULL"),
                    filters,
                    mapping,
                    Boolean.TRUE.equals(payload.getOrDefault("incremental", Boolean.FALSE)),
                    null // idempotencyKey
                );
                OperationRecord operation = recordConnectorOperation(
                    tenantId,
                    connectionId,
                    OperationKind.CONNECTOR_SYNC,
                    OperationStatus.RUNNING,
                    "Connector sync",
                    "Connector sync requested",
                    request,
                    Map.of(
                        "syncMode", syncRequest.mode(),
                        "targetCollection", syncRequest.datasetId() == null ? "" : syncRequest.datasetId()
                    ));
                String operationId = operation == null ? "" : operation.operationId();

                return updateConnectionState(tenantId, connectionId, "SYNCING", "syncing")
                        .then(e -> fabric.sync(tenantId, connectionId, syncRequest))
                        .map(result -> {
                            String postState = result.success() ? "ACTIVE" : "ERROR";
                            String postHealth = result.success() ? "healthy" : "unhealthy";
                            updateConnectionStateAsync(tenantId, connectionId, postState, postHealth);
                            transitionOperation(
                                tenantId,
                                operationId,
                                result.success() ? OperationStatus.SUCCEEDED : OperationStatus.FAILED,
                                result.errorMessage(),
                                Map.of(
                                    "jobId", result.syncId() == null ? "" : result.syncId(),
                                    "recordsSynced", result.recordsProcessed(),
                                    "recordsFailed", result.recordsFailed()
                                ));

                            // P4.4: Link connector sync output to target collection registry
                            String targetCollection = syncRequest.datasetId();
                            if (targetCollection != null && !targetCollection.isBlank()) {
                                updateCollectionSyncMetadata(tenantId, targetCollection, connectionId, result);
                            }

                            Map<String, Object> responseBody = Map.of(
                                "tenantId", tenantId,
                                "connectionId", connectionId,
                                "jobId", result.syncId(),
                                "operationId", operationId,
                                "syncStatus", result.success() ? "completed" : "failed",
                                "recordsSynced", result.recordsProcessed(),
                                "recordsFailed", result.recordsFailed(),
                                "message", result.errorMessage(),
                                "targetCollection", targetCollection,
                                "timestamp", Instant.now().toString()
                            );
                            storeIdempotency(tenantId, connectionId, "sync", request, responseBody);
                            return http.jsonResponse(responseBody);
                        })
                        .then(
                            r -> Promise.of(r),
                            ex -> {
                                updateConnectionStateAsync(tenantId, connectionId, "ERROR", "unhealthy");
                                transitionOperation(
                                    tenantId,
                                    operationId,
                                    OperationStatus.FAILED,
                                    ex.getMessage(),
                                    Map.of());
                                log.error("[triggerSync] tenant={} id={} failed: {}", tenantId, connectionId, ex.getMessage(), ex);
                                return Promise.of(http.errorResponse(502, "Sync failed: " + ex.getMessage()));
                            });
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid sync payload: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[triggerSync] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to trigger sync: " + e.getMessage()));
            });
    }

    // ─── GET /api/v1/connectors/:connectionId/sync/status ──────────────────────

    public Promise<HttpResponse> handleGetSyncStatus(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        if (fabric == null) {
            return client.findById(tenantId, DC_CONNECTIONS, connectionId)
                .map(opt -> {
                    String state = opt.map(e -> String.valueOf(e.data().getOrDefault("state", "UNKNOWN"))).orElse("NOT_FOUND");
                    return http.jsonResponse(Map.of(
                        "tenantId", tenantId,
                        "connectionId", connectionId,
                        "syncStatus", state,
                        "message", "Data fabric connector not available",
                        "timestamp", Instant.now().toString()
                    ));
                });
        }
        return fabric.getSyncStatus(tenantId, connectionId)
            .map(status -> http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "connectionId", connectionId,
                "syncStatus", status.state(),
                "totalRecords", status.totalRecords(),
                "syncedRecords", status.syncedRecords(),
                "failedRecords", status.failedRecords(),
                "progressPercent", status.progressPercent(),
                "startedAt", status.startedAt().toString(),
                "estimatedCompletionAt", status.estimatedCompletionAt() != null ? status.estimatedCompletionAt().toString() : null,
                "timestamp", Instant.now().toString()
            )))
            .then(
                r -> Promise.of(r),
                e -> {
                    log.error("[getSyncStatus] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                    return Promise.of(http.errorResponse(502, "Sync status retrieval failed: " + e.getMessage()));
                });
    }

    // ─── GET /api/v1/connectors/:connectionId/dataset-link ─────────────────────

    /**
     * Gets the dataset linkage for a connector.
     *
     * <p>GET /api/v1/connectors/:connectionId/dataset-link
     *
     * @param request HTTP request
     * @return Promise with dataset link information
     */
    public Promise<HttpResponse> handleGetDatasetLink(HttpRequest request) {
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }

        // P0-09: Fail closed when connector runtime is unavailable in production/staging/sovereign profiles
        if (isConnectorRuntimeRequired() && fabric == null) {
            return connectorRuntimeUnavailableResponse("dataset link retrieval");
        }

        if (fabric == null) {
            // This path is only allowed in local/test profiles where connector runtime is optional.
            return client.findById(tenantId, DC_CONNECTIONS, connectionId)
                .map(opt -> {
                    String state = opt.map(e -> String.valueOf(e.data().getOrDefault("state", "UNKNOWN"))).orElse("NOT_FOUND");
                    return http.jsonResponse(Map.of(
                        "tenantId", tenantId,
                        "connectionId", connectionId,
                        "linked", false,
                        "message", "Data fabric connector not available",
                        "timestamp", Instant.now().toString()
                    ));
                });
        }

        OperationRecord operation = recordConnectorOperation(
            tenantId,
            connectionId,
            OperationKind.CONNECTOR_LINK_DATASET,
            OperationStatus.RUNNING,
            "Connector dataset link",
            "Dataset link retrieval requested",
            request,
            Map.of());
        String operationId = operation == null ? "" : operation.operationId();

        return fabric.getDatasetLink(tenantId, connectionId)
            .map(link -> {
                transitionOperation(tenantId, operationId, OperationStatus.SUCCEEDED, "Dataset link retrieved", Map.of());
                if (link == null) {
                    return http.jsonResponse(Map.of(
                        "tenantId", tenantId,
                        "connectionId", connectionId,
                        "operationId", operationId,
                        "linked", false,
                        "timestamp", Instant.now().toString()
                    ));
                }
                Map<String, Object> responseBody = new LinkedHashMap<>();
                responseBody.put("tenantId", tenantId);
                responseBody.put("connectionId", connectionId);
                responseBody.put("operationId", operationId);
                responseBody.put("linkId", link.linkId());
                responseBody.put("datasetId", link.datasetId());
                responseBody.put("syncDirection", link.syncDirection());
                responseBody.put("lastSyncVersion", link.lastSyncVersion());
                responseBody.put("linkedAt", link.linkedAt().toString());
                responseBody.put("linkedBy", link.linkedBy());
                responseBody.put("linked", true);
                responseBody.put("timestamp", Instant.now().toString());
                return http.jsonResponse(responseBody);
            })
            .then(
                r -> Promise.of(r),
                e -> {
                    log.error("[getDatasetLink] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                    transitionOperation(tenantId, operationId, OperationStatus.FAILED, e.getMessage(), Map.of());
                    return Promise.of(http.errorResponse(502, "Dataset link retrieval failed: " + e.getMessage()));
                });
    }

    // ─── POST /api/v1/connectors/:connectionId/dataset-link ────────────────────

    /**
     * Creates or updates the dataset linkage for a connector.
     *
     * <p>POST /api/v1/connectors/:connectionId/dataset-link
     *
     * @param request HTTP request with dataset link payload
     * @return Promise with link result
     */
    public Promise<HttpResponse> handleLinkDataset(HttpRequest request) {
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:link-dataset");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }

        // P0-09: Fail closed when connector runtime is unavailable in production/staging/sovereign profiles
        if (isConnectorRuntimeRequired() && fabric == null) {
            return connectorRuntimeUnavailableResponse("dataset link creation");
        }

        // P0-07: Check idempotency before processing
        return checkIdempotency(tenantId, connectionId, "link-dataset", request)
            .then(idempotencyResponse -> {
                if (idempotencyResponse != null) {
                    return Promise.of(idempotencyResponse);
                }

                return request.loadBody().then(buf -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> payload = http.objectMapper().readValue(
                            buf.getString(StandardCharsets.UTF_8), Map.class);

                        String datasetId = (String) payload.get("datasetId");
                        if (datasetId == null || datasetId.isBlank()) {
                            return Promise.of(http.errorResponse(400, "datasetId is required"));
                        }

                        String userId = http.resolvePrincipalId(request);

                        if (fabric == null) {
                            return Promise.of(http.errorResponse(503, "Data fabric connector not available"));
                        }

                        OperationRecord operation = recordConnectorOperation(
                            tenantId,
                            connectionId,
                            OperationKind.CONNECTOR_LINK_DATASET,
                            OperationStatus.RUNNING,
                            "Connector dataset link",
                            "Dataset link requested: " + datasetId,
                            request,
                            Map.of("datasetId", datasetId));
                        String operationId = operation == null ? "" : operation.operationId();

                        return fabric.linkDataset(tenantId, connectionId, datasetId, userId)
                            .map(link -> {
                                transitionOperation(tenantId, operationId, OperationStatus.SUCCEEDED, "Dataset linked", Map.of("datasetId", datasetId));
                                Map<String, Object> responseBody = Map.of(
                                    "tenantId", tenantId,
                                    "connectionId", connectionId,
                                    "operationId", operationId,
                                    "linkId", link.linkId(),
                                    "datasetId", link.datasetId(),
                                    "syncDirection", link.syncDirection(),
                                    "linkedAt", link.linkedAt().toString(),
                                    "linkedBy", link.linkedBy(),
                                    "linked", true,
                                    "timestamp", Instant.now().toString()
                                );
                                storeIdempotency(tenantId, connectionId, "link-dataset", request, responseBody);
                                return http.jsonResponse(responseBody);
                            })
                            .then(
                                r -> Promise.of(r),
                                e -> {
                                    log.error("[linkDataset] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                                    transitionOperation(tenantId, operationId, OperationStatus.FAILED, e.getMessage(), Map.of("datasetId", datasetId));
                                    return Promise.of(http.errorResponse(502, "Dataset link failed: " + e.getMessage()));
                                });
                    } catch (Exception e) {
                        return Promise.of(http.errorResponse(400, "Invalid link payload: " + e.getMessage()));
                    }
                });
            })
            .then(Promise::of, e -> {
                log.error("[linkDataset] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to link dataset: " + e.getMessage()));
            });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private static Optional<String> validateConnectionPayload(Map<String, Object> payload) {
        if (payload == null) {
            return Optional.of("Payload must not be null");
        }
        String name = (String) payload.get("name");
        if (name == null || name.isBlank()) {
            return Optional.of("'name' is required");
        }
        String type = (String) payload.get("type");
        if (type == null || !VALID_TYPES.contains(type)) {
            return Optional.of("'type' must be one of: " + VALID_TYPES);
        }
        return Optional.empty();
    }

    /**
     * H3: Align response shape with OpenAPI Connector schema
     * Returns canonical Connector shape without sensitive fields
     */
    private static Map<String, Object> toConnectionView(Map<String, Object> data, String id) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", id);
        view.put("name", data.get("name"));
        view.put("type", data.get("type"));
        view.put("state", data.getOrDefault("state", "UNKNOWN"));
        view.put("tenantId", data.get("tenantId"));
        view.put("createdAt", data.get("createdAt"));
        view.put("updatedAt", data.get("updatedAt"));
        
        // Optional fields from OpenAPI schema
        if (data.containsKey("properties")) {
            view.put("properties", data.get("properties"));
        }
        if (data.containsKey("residencyPolicy")) {
            view.put("residencyPolicy", data.get("residencyPolicy"));
        }
        if (data.containsKey("schedule")) {
            view.put("schedule", data.get("schedule"));
        }
        if (data.containsKey("targetCollection")) {
            view.put("targetCollection", data.get("targetCollection"));
        }
        
        // Do not expose raw credentials or sensitive metadata in the view
        // These are write-only fields per OpenAPI schema
        return view;
    }

    private static void applyCredentialStoragePolicy(Map<String, Object> data, Map<String, Object> payload) {
        data.remove(CREDENTIALS_KEY);
        if (payload.containsKey(SECRET_REFERENCE_KEY)) {
            data.put(SECRET_REFERENCE_KEY, payload.get(SECRET_REFERENCE_KEY));
            data.put("credentialStatus", "referenced");
            return;
        }
        if (payload.containsKey(CREDENTIALS_KEY)) {
            data.put("credentialStatus", "external_reference_required");
        }
    }

    /**
     * Updates connection state with validation.
     *
     * <p>P1-6: In production profiles, this method validates the connection exists
     * and does not allow synthesizing missing records via state updates.
     *
     * @param tenantId tenant ID
     * @param connectionId connection ID
     * @param state new state
     * @param healthStatus health status
     * @return Promise with updated entity
     */
    private Promise<DataCloudClient.Entity> updateConnectionState(String tenantId, String connectionId, String state, String healthStatus) {
        return client.findById(tenantId, DC_CONNECTIONS, connectionId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    // P1-6: Do not synthesize missing connections by state update
                    log.warn("[updateConnectionState] Connection not found: tenant={} id={}", tenantId, connectionId);
                    return Promise.<DataCloudClient.Entity>ofException(
                        new IllegalStateException("Connection not found: " + connectionId));
                }
                Map<String, Object> data = new LinkedHashMap<>(opt.get().data());
                data.put("state", state);
                data.put("healthStatus", healthStatus);
                data.put("updatedAt", Instant.now().toString());
                return client.save(tenantId, DC_CONNECTIONS, data);
            });
    }

    private void updateConnectionStateAsync(String tenantId, String connectionId, String state, String healthStatus) {
        updateConnectionState(tenantId, connectionId, state, healthStatus)
            .whenResult(e -> log.debug("[updateConnectionState] {} -> {} ({})", connectionId, state, healthStatus))
            .whenException(e -> log.error("[updateConnectionState] {} failed: {}", connectionId, e.getMessage()));
    }

    /**
     * P4.4: Update collection metadata with sync information from connector.
     * Links connector sync output to the collection registry.
     */
    private void updateCollectionSyncMetadata(String tenantId, String collectionId, String connectionId, DataFabricConnector.SyncResult result) {
        try {
            Map<String, Object> syncMetadata = new LinkedHashMap<>();
            syncMetadata.put("lastSyncConnectionId", connectionId);
            syncMetadata.put("lastSyncTimestamp", Instant.now().toString());
            syncMetadata.put("lastSyncStatus", result.status());
            syncMetadata.put("lastSyncRecordsProcessed", result.recordsProcessed());
            syncMetadata.put("lastSyncRecordsInserted", result.recordsInserted());
            syncMetadata.put("lastSyncRecordsUpdated", result.recordsUpdated());
            syncMetadata.put("lastSyncRecordsFailed", result.recordsFailed());
            if (result.errorMessage() != null) {
                syncMetadata.put("lastSyncError", result.errorMessage());
            }
            if (result.datasetVersion() != null) {
                syncMetadata.put("lastSyncDatasetVersion", result.datasetVersion());
            }

            Map<String, Object> collectionUpdate = new LinkedHashMap<>();
            collectionUpdate.put("syncMetadata", syncMetadata);
            collectionUpdate.put("updatedAt", Instant.now().toString());

            client.updateEntity("dc_collections", collectionId, collectionUpdate, tenantId)
                .whenResult(e -> log.info("[updateCollectionSyncMetadata] Updated collection {} with sync from connector {}", collectionId, connectionId))
                .whenException(e -> log.warn("[updateCollectionSyncMetadata] Failed to update collection {}: {}", collectionId, e.getMessage()));
        } catch (Exception e) {
            log.error("[updateCollectionSyncMetadata] Unexpected error updating collection {}: {}", collectionId, e.getMessage(), e);
        }
    }

    // ─── GET /api/v1/data-fabric/metrics ────────────────────────────────────────

    /**
     * Handles GET /api/v1/data-fabric/metrics - returns fabric tier metrics from real storage profiles.
     *
     * <p>Aggregates metrics from storage profiles across tiers (HOT, WARM, COOL, COLD)
     * to provide real-time fabric topology metrics for the Data Fabric UI.
     *
     * <p>This endpoint requires real storage profile entities to be configured.
     * When no storage profiles exist, it returns an empty metrics response rather than
     * synthetic demo data. This is a preview capability and is disabled by default
     * in production profiles.
     *
     * @param request HTTP request
     * @return Promise with fabric metrics response
     */
    public Promise<HttpResponse> handleGetFabricMetrics(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        // DC-P1-002: Gate behind DATA_CLOUD_DATA_FABRIC feature flag (disabled by default).
        // Live fabric metrics require a real DataFabricConnector implementation.
        if (!DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_DATA_FABRIC)) {
            log.info("[getFabricMetrics] tenant={} - Data Fabric metrics are unavailable: DATA_CLOUD_DATA_FABRIC feature flag is disabled (default)",
                tenantId);
            return Promise.of(http.jsonResponse(Map.of(
                "tiers", List.of(),
                "totalEventsPerSec", 0.0,
                "totalStorageGb", 0.0,
                "lastUpdated", Instant.now().toString(),
                "capability", "unavailable",
                "preview", true,
                "disabled", true,
                "message", "Data Fabric metrics are not available in this deployment. " +
                    "Check the dataFabric entry at GET /api/v1/surfaces for current capability status."
            )));
        }

        // Check if fabric metrics capability is enabled (preview capability, disabled in production by default).
        // Reads env var first (production deployment), then system property (test/CI override).
        String profile = System.getenv().getOrDefault("DATACLOUD_PROFILE",
                System.getProperty("DATACLOUD_PROFILE", "local"));
        boolean isProductionProfile = "production".equalsIgnoreCase(profile) || "staging".equalsIgnoreCase(profile);

        if (isProductionProfile) {
            log.warn("[getFabricMetrics] tenant={} - Data Fabric metrics are preview-only and disabled in {} profile",
                tenantId, profile);
            return Promise.of(http.jsonResponse(Map.of(
                "tiers", List.of(),
                "totalEventsPerSec", 0.0,
                "totalStorageGb", 0.0,
                "lastUpdated", Instant.now().toString(),
                "capability", "preview",
                "preview", true,
                "disabled", true,
                "message", "Data Fabric metrics are a preview capability and disabled in production profiles"
            )));
        }

        // Query storage profiles to get tier information
        return client.query(tenantId, DC_CONNECTIONS, DataCloudClient.Query.limit(500))
            .map(entities -> {
                // Build real tier metrics from storage profile entities
                List<Map<String, Object>> tiers = buildRealTierMetrics(entities);

                if (tiers.isEmpty()) {
                    log.info("[getFabricMetrics] tenant={} - no storage profiles configured, returning empty metrics",
                        tenantId);
                    return http.jsonResponse(Map.of(
                        "tiers", List.of(),
                        "totalEventsPerSec", 0.0,
                        "totalStorageGb", 0.0,
                        "lastUpdated", Instant.now().toString(),
                        "preview", true,
                        "message", "No storage profiles configured. Data Fabric metrics require configured storage profiles."
                    ));
                }

                // Calculate totals from real data
                double totalEventsPerSec = tiers.stream()
                    .mapToDouble(t -> ((Number) t.getOrDefault("throughputEps", 0)).doubleValue())
                    .sum();
                double totalStorageGb = tiers.stream()
                    .mapToDouble(t -> {
                        Object storage = t.get("storageGb");
                        return storage instanceof Number ? ((Number) storage).doubleValue() : 0.0;
                    })
                    .sum();

                return http.jsonResponse(Map.of(
                    "tiers", tiers,
                    "totalEventsPerSec", totalEventsPerSec,
                    "totalStorageGb", totalStorageGb,
                    "lastUpdated", Instant.now().toString(),
                    "preview", true
                ));
            })
            .then(
                r -> Promise.of(r),
                e -> {
                    log.error("[getFabricMetrics] tenant={} failed: {}", tenantId, e.getMessage(), e);
                    // Return degraded but truthful response on error
                    return Promise.of(http.jsonResponse(Map.of(
                        "tiers", List.of(),
                        "totalEventsPerSec", 0.0,
                        "totalStorageGb", 0.0,
                        "lastUpdated", Instant.now().toString(),
                        "preview", true,
                        "degraded", true,
                        "error", "Metrics temporarily unavailable"
                    )));
                });
    }

    /**
     * Builds real tier metrics from storage profile entities.
     *
     * <p>Extracts actual metrics from configured storage profile entities.
     * Returns empty list if no storage profiles are configured for a tier.
     * No synthetic or demo metrics are generated.
     *
     * @param entities storage profile entities
     * @return list of tier metrics maps from real storage profile data
     */
    private List<Map<String, Object>> buildRealTierMetrics(List<DataCloudClient.Entity> entities) {
        List<Map<String, Object>> tiers = new java.util.ArrayList<>();

        // Group entities by tier based on their type/configuration
        Map<String, List<DataCloudClient.Entity>> tierGroups = new java.util.LinkedHashMap<>();
        tierGroups.put("HOT", new java.util.ArrayList<>());
        tierGroups.put("WARM", new java.util.ArrayList<>());
        tierGroups.put("COOL", new java.util.ArrayList<>());
        tierGroups.put("COLD", new java.util.ArrayList<>());

        for (DataCloudClient.Entity entity : entities) {
            Map<String, Object> data = entity.data();
            String type = String.valueOf(data.getOrDefault("type", "")).toLowerCase();
            String tierConfig = String.valueOf(data.getOrDefault("tier", "")).toLowerCase();

            if (type.contains("redis") || tierConfig.contains("hot")) {
                tierGroups.get("HOT").add(entity);
            } else if (type.contains("postgresql") || tierConfig.contains("warm")) {
                tierGroups.get("WARM").add(entity);
            } else if (type.contains("iceberg") || tierConfig.contains("cool")) {
                tierGroups.get("COOL").add(entity);
            } else if (type.contains("s3") || tierConfig.contains("cold") || tierConfig.contains("archive")) {
                tierGroups.get("COLD").add(entity);
            }
        }

        // Build metrics for each tier that has configured storage profiles
        for (Map.Entry<String, List<DataCloudClient.Entity>> entry : tierGroups.entrySet()) {
            String tier = entry.getKey();
            List<DataCloudClient.Entity> tierEntities = entry.getValue();

            if (tierEntities.isEmpty()) {
                continue; // Skip tiers with no configured storage profiles
            }

            // Aggregate metrics from real storage profile entities
            double totalThroughput = 0.0;
            double totalLatency = 0.0;
            double totalErrorRate = 0.0;
            int totalQueueDepth = 0;
            double totalStorage = 0.0;
            int healthyCount = 0;
            int warningCount = 0;
            int errorCount = 0;

            for (DataCloudClient.Entity entity : tierEntities) {
                Map<String, Object> data = entity.data();

                // Extract metrics from entity data if present
                Object throughput = data.get("throughputEps");
                if (throughput instanceof Number) {
                    totalThroughput += ((Number) throughput).doubleValue();
                }

                Object latency = data.get("latencyP99Ms");
                if (latency instanceof Number) {
                    totalLatency += ((Number) latency).doubleValue();
                }

                Object errorRate = data.get("errorRate");
                if (errorRate instanceof Number) {
                    totalErrorRate += ((Number) errorRate).doubleValue();
                }

                Object queueDepth = data.get("queueDepth");
                if (queueDepth instanceof Number) {
                    totalQueueDepth += ((Number) queueDepth).intValue();
                }

                Object storage = data.get("storageGb");
                if (storage instanceof Number) {
                    totalStorage += ((Number) storage).doubleValue();
                }

                // Count health status
                String healthStatus = String.valueOf(data.getOrDefault("healthStatus", "unknown"));
                if ("healthy".equalsIgnoreCase(healthStatus)) {
                    healthyCount++;
                } else if ("warning".equalsIgnoreCase(healthStatus)) {
                    warningCount++;
                } else if ("error".equalsIgnoreCase(healthStatus)) {
                    errorCount++;
                }
            }

            int instanceCount = tierEntities.size();
            double avgThroughput = instanceCount > 0 ? totalThroughput / instanceCount : 0.0;
            double avgLatency = instanceCount > 0 ? totalLatency / instanceCount : 0.0;
            double avgErrorRate = instanceCount > 0 ? totalErrorRate / instanceCount : 0.0;
            double avgQueueDepth = instanceCount > 0 ? (double) totalQueueDepth / instanceCount : 0.0;

            // Determine overall tier status
            String status;
            if (errorCount > 0) {
                status = "error";
            } else if (warningCount > 0 || avgErrorRate > 0.01) {
                status = "warning";
            } else if (healthyCount > 0) {
                status = "healthy";
            } else {
                status = "inactive";
            }

            // Determine default backend label based on tier
            String defaultBackend = switch (tier) {
                case "HOT" -> "Redis";
                case "WARM" -> "PostgreSQL";
                case "COOL" -> "Iceberg";
                case "COLD" -> "S3/Archive";
                default -> "Unknown";
            };

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("tier", tier);
            metrics.put("label", tier + " Tier (" + defaultBackend + ")");
            metrics.put("throughputEps", avgThroughput);
            metrics.put("latencyP99Ms", avgLatency);
            metrics.put("errorRate", avgErrorRate);
            metrics.put("queueDepth", (int) avgQueueDepth);
            metrics.put("status", status);
            metrics.put("instanceCount", instanceCount);

            if (!tier.equals("HOT")) {
                metrics.put("storageGb", totalStorage);
            }

            tiers.add(metrics);
        }

        return tiers;
    }
    // ─── DELETE /api/v1/connectors/:connectionId ─────────────────────────────

    /**
     * Deletes a registered data source connection and removes it from the {@code dc_connections}
     * entity store.
     *
     * <p>Returns HTTP 204 on success, HTTP 404 when the connection does not exist for the tenant.
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to the HTTP response
     */
    public Promise<HttpResponse> handleDeleteConnection(HttpRequest request) {
        // Require permission for connector deletion
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:delete");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        final String finalTenantId = tenantId;
        final String finalConnectionId = connectionId;

        // P0-07: Check idempotency before processing
        return checkIdempotency(finalTenantId, finalConnectionId, "delete", request)
            .then(idempotencyResponse -> {
                if (idempotencyResponse != null) {
                    return Promise.of(idempotencyResponse);
                }

                return client.findById(finalTenantId, DC_CONNECTIONS, finalConnectionId)
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.of(http.errorResponse(404, "Connection not found: " + finalConnectionId));
                        }
                        return client.delete(finalTenantId, DC_CONNECTIONS, finalConnectionId)
                            .map(ignored -> {
                                log.info("[deleteConnection] tenant={} connectionId={} deleted", finalTenantId, finalConnectionId);
                                emitConnectorAudit(finalTenantId, finalConnectionId, "CONNECTOR_DELETED", true);

                                // P0-07: Store response for idempotency (204 No Content)
                                HttpResponse response = http.noContentResponse();
                                storeIdempotency(finalTenantId, finalConnectionId, "delete", request, response);

                                return response;
                            })
                            .then(Promise::of, e -> {
                                log.error("[deleteConnection] tenant={} connectionId={} failed: {}", finalTenantId, finalConnectionId, e.getMessage(), e);
                                return Promise.of(http.errorResponse(500, "Failed to delete connection: " + e.getMessage()));
                            });
                    });
            });
    }

    // ─── PUT /api/v1/connectors/:connectionId ─────────────────────────────────

    /**
     * Updates mutable fields on a registered data source connection (DC-P2-006).
     *
     * <p>Identity fields ({@code id}, {@code tenantId}, {@code type}) are immutable.
     * Only {@code name}, {@code properties}, {@code residencyPolicy}, and {@code state}
     * may be updated via this operation.
     *
     * <p>Returns HTTP 200 on success, HTTP 404 when the connection does not exist.
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to the HTTP response
     */
    public Promise<HttpResponse> handleUpdateConnection(HttpRequest request) {
        // Require permission for connector update
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:update");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        return request.loadBody().then(buf -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = http.objectMapper().readValue(
                    buf.getString(StandardCharsets.UTF_8), Map.class);

                return client.findById(tenantId, DC_CONNECTIONS, connectionId)
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.of(http.errorResponse(404, "Connection not found: " + connectionId));
                        }
                        Map<String, Object> existing = new LinkedHashMap<>(opt.get().data());
                        // Only mutable fields may be updated; identity and tenantId are preserved
                        if (payload.containsKey("name")) existing.put("name", payload.get("name"));
                        if (payload.containsKey("properties")) existing.put("properties", payload.get("properties"));
                        if (payload.containsKey("residencyPolicy")) existing.put("residencyPolicy", payload.get("residencyPolicy"));
                        if (payload.containsKey("state") && VALID_STATES.contains(String.valueOf(payload.get("state")))) {
                            existing.put("state", payload.get("state"));
                        }
                        existing.put("updatedAt", Instant.now().toString());
                        return client.save(tenantId, DC_CONNECTIONS, existing)
                            .map(saved -> {
                                emitConnectorAudit(tenantId, connectionId, "CONNECTOR_UPDATED", true);
                                return http.jsonResponse(Map.of(
                                    "tenantId", tenantId,
                                    "connectionId", connectionId,
                                    "updated", true,
                                    "timestamp", Instant.now().toString()
                                ));
                            });
                    });
            } catch (Exception e) {
                log.error("[updateConnection] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Connection update failed: " + e.getMessage()));
            }
        });
    }

    // ─── GET /api/v1/connectors/:connectionId/capabilities ─────────────────────

    public Promise<HttpResponse> handleGetCapabilities(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }

        return client.findById(tenantId, DC_CONNECTIONS, connectionId)
            .map(opt -> opt.map(e -> {
                Map<String, Object> data = e.data();
                String connectorType = (String) data.getOrDefault("type", "UNKNOWN");
                
                // Return connector capabilities based on type
                Map<String, Object> capabilities = Map.of(
                    "connectorId", connectionId,
                    "type", connectorType,
                    "supportsSchemaInference", true,
                    "supportsSync", true,
                    "supportsIncrementalSync", Set.of("POSTGRESQL", "MYSQL", "MONGODB").contains(connectorType),
                    "supportsQuery", Set.of("POSTGRESQL", "MYSQL", "SNOWFLAKE", "BIGQUERY").contains(connectorType),
                    "supportsBulkExport", true,
                    "supportsStreaming", Set.of("KAFKA", "REST_API").contains(connectorType),
                    "timestamp", Instant.now().toString()
                );
                return http.jsonResponse(capabilities);
            }).orElseGet(() -> http.errorResponse(404, "Connection not found: " + connectionId)));
    }

    // ─── POST /api/v1/connectors/:connectionId/dataset-link ─────────────────────

    public Promise<HttpResponse> handleDatasetLink(HttpRequest request) {
        // Use centralized request context resolution
        RequestContextResolver.ResolutionResult contextResult = http.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(http.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }

        // Require permission for dataset linking
        RequestContextResolver.ResolutionResult permissionResult = http.requirePermission(request, "connector:link-dataset");
        if (!permissionResult.isSuccess()) {
            return Promise.of(http.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        String tenantId = contextResult.context().map(com.ghatana.datacloud.launcher.http.security.RequestContext::tenantId).orElse(null);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(401, "Authentication required: valid tenant context not found"));
        }

        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }

        return request.loadBody()
            .then(buf -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = http.objectMapper().readValue(
                        buf.getString(StandardCharsets.UTF_8), Map.class);

                    String datasetId = (String) payload.get("datasetId");
                    if (datasetId == null || datasetId.isBlank()) {
                        return Promise.of(http.errorResponse(400, "datasetId is required in request body"));
                    }

                    // Verify connection exists
                    return client.findById(tenantId, DC_CONNECTIONS, connectionId)
                        .then(opt -> {
                            if (opt.isEmpty()) {
                                return Promise.of(http.errorResponse(404, "Connection not found: " + connectionId));
                            }

                            DataCloudClient.Entity entity = opt.get();
                            Map<String, Object> updates = new LinkedHashMap<>(entity.data());
                            @SuppressWarnings("unchecked")
                            List<String> linkedDatasets = (List<String>) updates.getOrDefault("linkedDatasets", new java.util.ArrayList<String>());
                            
                            if (linkedDatasets.contains(datasetId)) {
                                return Promise.of(http.jsonResponse(Map.of(
                                    "tenantId", tenantId,
                                    "connectionId", connectionId,
                                    "datasetId", datasetId,
                                    "linked", true,
                                    "message", "Dataset already linked to connection",
                                    "timestamp", Instant.now().toString()
                                )));
                            }

                            linkedDatasets.add(datasetId);
                            updates.put("linkedDatasets", linkedDatasets);
                            updates.put("updatedAt", Instant.now().toString());
                            
                            return client.save(tenantId, DC_CONNECTIONS, updates)
                                .map(saved -> {
                                    emitConnectorAudit(tenantId, connectionId, "DATASET_LINKED", true);
                                    return http.jsonResponse(Map.of(
                                        "tenantId", tenantId,
                                        "connectionId", connectionId,
                                        "datasetId", datasetId,
                                        "linked", true,
                                        "timestamp", Instant.now().toString()
                                    ));
                                });
                        });
                } catch (Exception e) {
                    log.error("[datasetLink] tenant={} connectionId={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                    return Promise.of(http.errorResponse(500, "Failed to link dataset: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[datasetLink] tenant={} connectionId={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to link dataset: " + e.getMessage()));
            });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Audit helper
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Emits a connector lifecycle audit event.
     *
     * <p>When {@link #auditService} is null, the call is a no-op so callers
     * do not need to guard against null service references.
     *
     * @param tenantId     tenant that owns the connection
     * @param connectionId the connection being acted on
     * @param eventType    lifecycle event type (e.g. {@code CONNECTOR_CREATED})
     * @param success      whether the operation succeeded
     */
    private void emitConnectorAudit(String tenantId, String connectionId,
                                    String eventType, boolean success) {
        if (auditService == null) return;
        AuditEvent event = AuditEvent.builder()
            .tenantId(tenantId)
            .eventType(eventType)
            .resourceType("CONNECTOR")
            .resourceId(connectionId)
            .success(success)
            .build();
        auditService.record(event).whenException(e ->
            log.warn("[connector-audit] emission failed eventType={} connectionId={}: {}", eventType, connectionId, e.getMessage()));
    }

    private OperationRecord recordConnectorOperation(
            String tenantId,
            String connectionId,
            OperationKind kind,
            OperationStatus status,
            String action,
            String summary,
            HttpRequest request,
            Map<String, Object> metadata) {
        if (operationRecorder == null) {
            return null;
        }
        OperationRecord record = OperationRecord.create(
                tenantId,
                kind,
                status,
                "connector",
                connectionId,
                action,
                summary,
                http.resolvePrincipalId(request),
                http.resolveCorrelationId(request),
                false,
                metadata);
        return operationRecorder.record(record);
    }

    private void transitionOperation(
            String tenantId,
            String operationId,
            OperationStatus status,
            String detail,
            Map<String, Object> metadata) {
        if (operationRecorder == null || operationId == null) {
            return;
        }
        operationRecorder.transition(tenantId, operationId, status, detail, metadata);
    }
}
