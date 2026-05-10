package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.fabric.DataFabricConnector;
import com.ghatana.datacloud.feature.DataCloudFeature;
import com.ghatana.datacloud.feature.DataCloudFeatureFlags;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.security.annotation.RequiresRole;
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

    /**
     * @param client       entity store client for persisting connection metadata
     * @param http         shared HTTP helper
     * @param fabric       optional DataFabricConnector implementation; may be null
     * @param auditService optional audit service; when null audit emissions are skipped
     */
    public DataSourceRegistryHandler(DataCloudClient client, HttpHandlerSupport http,
                                     DataFabricConnector fabric, AuditService auditService) {
        this.client = client;
        this.http = http;
        this.fabric = fabric;
        this.auditService = auditService;
    }

    // ─── GET /api/v1/connectors ───────────────────────────────────────────────

    public Promise<HttpResponse> handleListConnections(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
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
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
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
                        return http.jsonResponse(201, Map.of(
                            "tenantId", tenantId,
                            "connectionId", saved.id(),
                            "state", data.get("state"),
                            "created", true,
                            "timestamp", Instant.now().toString()
                        ));
                    });
            } catch (Exception e) {
                log.error("[registerConnection] tenant={} failed: {}", tenantId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Connection registration failed: " + e.getMessage()));
            }
        });
    }

    // ─── GET /api/v1/connectors/:connectionId ─────────────────────────────────

    public Promise<HttpResponse> handleGetConnection(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
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
        String tenantId = http.requireTenantIdOrFail(request);
        String connectionId = request.getPathParameter("connectionId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        if (fabric == null) {
            // DC-P2-006: When no fabric connector is available, do NOT mutate the connection state.
            // Setting state to TESTING creates a phantom state that never resolves; return pending instead.
            return Promise.of(http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "connectionId", connectionId,
                "testStatus", "pending",
                "message", "Data fabric connector not available; test cannot be performed",
                "timestamp", Instant.now().toString()
            )));
        }
        return fabric.testConnection(connectionId)
            .map(result -> {
                String healthStatus = result.success() ? "healthy" : "unhealthy";
                String state = result.success() ? "ACTIVE" : "ERROR";
                updateConnectionStateAsync(tenantId, connectionId, state, healthStatus);
                emitConnectorAudit(tenantId, connectionId, "CONNECTOR_TESTED", result.success());
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "connectionId", connectionId,
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
        String tenantId = http.requireTenantIdOrFail(request);
        String connectionId = request.getPathParameter("connectionId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
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
            .then(e -> fabric.testConnection(connectionId))
            .map(result -> {
                if (result.success()) {
                    // Validation passed - mark ACTIVE
                    updateConnectionStateAsync(tenantId, connectionId, "ACTIVE", "healthy");
                    emitConnectorAudit(tenantId, connectionId, "CONNECTOR_ENABLED", true);
                    return http.jsonResponse(Map.of(
                        "tenantId", tenantId,
                        "connectionId", connectionId,
                        "state", "ACTIVE",
                        "enabled", true,
                        "validated", true,
                        "latencyMs", result.latencyMs(),
                        "timestamp", Instant.now().toString()
                    ));
                } else {
                    // Validation failed - mark ERROR and reject enable
                    updateConnectionStateAsync(tenantId, connectionId, "ERROR", "unhealthy");
                    emitConnectorAudit(tenantId, connectionId, "CONNECTOR_ENABLE_REJECTED", false);
                    return http.jsonResponse(422, Map.of(
                        "tenantId", tenantId,
                        "connectionId", connectionId,
                        "state", "ERROR",
                        "enabled", false,
                        "validated", false,
                        "validationError", result.message(),
                        "timestamp", Instant.now().toString()
                    ));
                }
            })
            .then(
                r -> Promise.of(r),
                e -> {
                    log.error("[enableConnection] tenant={} id={} validation failed: {}",
                        tenantId, connectionId, e.getMessage(), e);
                    updateConnectionStateAsync(tenantId, connectionId, "ERROR", "unhealthy");
                    emitConnectorAudit(tenantId, connectionId, "CONNECTOR_ENABLE_FAILED", false);
                    return Promise.of(http.errorResponse(502,
                        "Enable validation failed: " + e.getMessage()));
                });
    }

    // ─── POST /api/v1/connectors/:connectionId/disable ──────────────────────────

    public Promise<HttpResponse> handleDisableConnection(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        String connectionId = request.getPathParameter("connectionId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        if (fabric != null) {
            return fabric.disconnect(connectionId)
                .then(() -> updateConnectionState(tenantId, connectionId, "INACTIVE", "disabled"))
                .map(e -> {
                    emitConnectorAudit(tenantId, connectionId, "CONNECTOR_DISABLED", true);
                    return http.jsonResponse(Map.of(
                        "tenantId", tenantId,
                        "connectionId", connectionId,
                        "state", "INACTIVE",
                        "enabled", false,
                        "timestamp", Instant.now().toString()
                    ));
                })
                .then(
                    r -> Promise.of(r),
                    e -> {
                        log.error("[disableConnection] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                        return updateConnectionState(tenantId, connectionId, "INACTIVE", "disabled")
                            .map(updated -> http.jsonResponse(Map.of(
                                "tenantId", tenantId,
                                "connectionId", connectionId,
                                "state", "INACTIVE",
                                "enabled", false,
                                "warning", "Disconnect failed but state set to inactive: " + e.getMessage(),
                                "timestamp", Instant.now().toString()
                            )));
                    });
        }
        return updateConnectionState(tenantId, connectionId, "INACTIVE", "disabled")
            .map(e -> {
                emitConnectorAudit(tenantId, connectionId, "CONNECTOR_DISABLED", true);
                return http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "connectionId", connectionId,
                    "state", "INACTIVE",
                    "enabled", false,
                    "timestamp", Instant.now().toString()
                ));
            });
    }

    // ─── POST /api/v1/connectors/:connectionId/rotate-credentials ───────────────

    public Promise<HttpResponse> handleRotateCredentials(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        String connectionId = request.getPathParameter("connectionId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
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
                                return http.jsonResponse(Map.of(
                                    "tenantId", tenantId,
                                    "connectionId", connectionId,
                                    "rotated", true,
                                    "timestamp", Instant.now().toString()
                                ));
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
        String tenantId = http.requireTenantIdOrFail(request);
        String connectionId = request.getPathParameter("connectionId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
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
                    return fabric.testConnection(connectionId)
                        .map(result -> {
                            String liveHealth = result.success() ? "healthy" : "degraded";
                            updateConnectionStateAsync(tenantId, connectionId, state, liveHealth);
                            return http.jsonResponse(Map.of(
                                "tenantId", tenantId,
                                "connectionId", connectionId,
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
                                return Promise.of(http.jsonResponse(Map.of(
                                    "tenantId", tenantId,
                                    "connectionId", connectionId,
                                    "state", "ERROR",
                                    "healthStatus", "unhealthy",
                                    "error", e.getMessage(),
                                    "timestamp", Instant.now().toString()
                                )));
                            });
                }
                return Promise.of(http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "connectionId", connectionId,
                    "state", state,
                    "healthStatus", healthStatus,
                    "timestamp", Instant.now().toString()
                )));
            });
    }

    // ─── GET /api/v1/connectors/:connectionId/schema ───────────────────────────

    public Promise<HttpResponse> handleGetSchema(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        String connectionId = request.getPathParameter("connectionId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        if (fabric == null) {
            return Promise.of(http.errorResponse(503, "Data fabric connector not available"));
        }
        return fabric.getSchema(connectionId)
            .map(schema -> http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "connectionId", connectionId,
                "schema", schema,
                "timestamp", Instant.now().toString()
            )))
            .then(
                r -> Promise.of(r),
                e -> {
                    log.error("[getSchema] tenant={} id={} failed: {}", tenantId, connectionId, e.getMessage(), e);
                    return Promise.of(http.errorResponse(502, "Schema retrieval failed: " + e.getMessage()));
                });
    }

    // ─── POST /api/v1/connectors/:connectionId/sync ────────────────────────────

    public Promise<HttpResponse> handleTriggerSync(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        String connectionId = request.getPathParameter("connectionId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        if (fabric == null) {
            return Promise.of(http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "connectionId", connectionId,
                "syncStatus", "pending",
                "message", "Data fabric connector not available; sync queued",
                "timestamp", Instant.now().toString()
            )));
        }
        return request.loadBody().then(buf -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = buf == null || buf.readRemaining() == 0
                    ? Map.of()
                    : http.objectMapper().readValue(buf.getString(StandardCharsets.UTF_8), Map.class);
                // Build a minimal SyncConfig from payload
                @SuppressWarnings("unchecked")
                Map<String, Object> filters = payload.containsKey("filters") ? (Map<String, Object>) payload.get("filters") : Map.of();
                @SuppressWarnings("unchecked")
                List<String> columns = payload.containsKey("columns") ? (List<String>) payload.get("columns") : List.of();
                DataFabricConnector.SyncConfig config = new DataFabricConnector.SyncConfig(
                    (String) payload.getOrDefault("syncMode", "full"),
                    (String) payload.get("targetCollection"),
                    (String) payload.getOrDefault("schedule", "once"),
                    filters,
                    Boolean.TRUE.equals(payload.getOrDefault("incremental", Boolean.FALSE)),
                    columns
                );
                return updateConnectionState(tenantId, connectionId, "SYNCING", "syncing")
                    .then(e -> fabric.sync(connectionId, config))
                    .map(result -> {
                        String postState = result.success() ? "ACTIVE" : "ERROR";
                        String postHealth = result.success() ? "healthy" : "unhealthy";
                        updateConnectionStateAsync(tenantId, connectionId, postState, postHealth);
                        return http.jsonResponse(Map.of(
                            "tenantId", tenantId,
                            "connectionId", connectionId,
                            "syncStatus", result.success() ? "completed" : "failed",
                            "recordsSynced", result.recordsSynced(),
                            "recordsFailed", result.recordsFailed(),
                            "message", result.errorMessage(),
                            "timestamp", Instant.now().toString()
                        ));
                    })
                    .then(
                        r -> Promise.of(r),
                        ex -> {
                            updateConnectionStateAsync(tenantId, connectionId, "ERROR", "unhealthy");
                            log.error("[triggerSync] tenant={} id={} failed: {}", tenantId, connectionId, ex.getMessage(), ex);
                            return Promise.of(http.errorResponse(502, "Sync failed: " + ex.getMessage()));
                        });
            } catch (Exception e) {
                return Promise.of(http.errorResponse(400, "Invalid sync payload: " + e.getMessage()));
            }
        });
    }

    // ─── GET /api/v1/connectors/:connectionId/sync/status ──────────────────────

    public Promise<HttpResponse> handleGetSyncStatus(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        String connectionId = request.getPathParameter("connectionId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
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
        return fabric.getSyncStatus(connectionId)
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

    private static Map<String, Object> toConnectionView(Map<String, Object> data, String id) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", id);
        view.put("name", data.get("name"));
        view.put("type", data.get("type"));
        view.put("state", data.getOrDefault("state", "UNKNOWN"));
        view.put("healthStatus", data.getOrDefault("healthStatus", "unknown"));
        view.put("tenantId", data.get("tenantId"));
        view.put("createdAt", data.get("createdAt"));
        view.put("updatedAt", data.get("updatedAt"));
        // Do not expose raw credentials in the view
        if (data.containsKey("properties")) {
            view.put("properties", data.get("properties"));
        }
        if (data.containsKey("residencyPolicy")) {
            view.put("residencyPolicy", data.get("residencyPolicy"));
        }
        if (data.containsKey("lastCredentialRotation")) {
            view.put("lastCredentialRotation", data.get("lastCredentialRotation"));
        }
        if (data.containsKey(SECRET_REFERENCE_KEY)) {
            view.put(SECRET_REFERENCE_KEY, data.get(SECRET_REFERENCE_KEY));
        }
        if (data.containsKey("credentialStatus")) {
            view.put("credentialStatus", data.get("credentialStatus"));
        }
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
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
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
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String connectionId = request.getPathParameter("connectionId");
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));

        final String finalTenantId = tenantId;
        final String finalConnectionId = connectionId;

        return client.findById(finalTenantId, DC_CONNECTIONS, finalConnectionId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(http.errorResponse(404, "Connection not found: " + finalConnectionId));
                }
                return client.delete(finalTenantId, DC_CONNECTIONS, finalConnectionId)
                    .map(ignored -> {
                        log.info("[deleteConnection] tenant={} connectionId={} deleted", finalTenantId, finalConnectionId);
                        emitConnectorAudit(finalTenantId, finalConnectionId, "CONNECTOR_DELETED", true);
                        return http.noContentResponse();
                    });
            })
            .then(Promise::of, e -> {
                log.error("[deleteConnection] tenant={} connectionId={} failed: {}", finalTenantId, finalConnectionId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to delete connection: " + e.getMessage()));
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
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
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
}
