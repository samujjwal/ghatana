package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.fabric.DataFabricConnector;
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
    private static final Set<String> VALID_TYPES = Set.of(
        "POSTGRESQL", "MYSQL", "MONGODB", "S3", "REST_API", "KAFKA", "SNOWFLAKE", "BIGQUERY", "CUSTOM"
    );
    private static final Set<String> VALID_STATES = Set.of(
        "ACTIVE", "INACTIVE", "TESTING", "ERROR", "SYNCING"
    );

    private final DataCloudClient client;
    private final HttpHandlerSupport http;
    private final DataFabricConnector fabric;

    /**
     * @param client entity store client for persisting connection metadata
     * @param http   shared HTTP helper
     * @param fabric optional DataFabricConnector implementation; may be null
     */
    public DataSourceRegistryHandler(DataCloudClient client, HttpHandlerSupport http, DataFabricConnector fabric) {
        this.client = client;
        this.http = http;
        this.fabric = fabric;
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
                data.put("tenantId", tenantId);
                data.put("id", id);
                data.put("createdAt", Instant.now().toString());
                data.put("updatedAt", Instant.now().toString());
                data.put("state", data.getOrDefault("state", "INACTIVE"));
                if (!data.containsKey("healthStatus")) {
                    data.put("healthStatus", "unknown");
                }

                return client.save(tenantId, DC_CONNECTIONS, data)
                    .map(saved -> http.jsonResponse(201, Map.of(
                        "tenantId", tenantId,
                        "connectionId", saved.id(),
                        "state", data.get("state"),
                        "created", true,
                        "timestamp", Instant.now().toString()
                    )));
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
            return updateConnectionState(tenantId, connectionId, "TESTING", "test_pending")
                .map(e -> http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "connectionId", connectionId,
                    "testStatus", "pending",
                    "message", "Data fabric connector not available; test queued",
                    "timestamp", Instant.now().toString()
                )));
        }
        return fabric.testConnection(connectionId)
            .map(result -> {
                String healthStatus = result.success() ? "healthy" : "unhealthy";
                String state = result.success() ? "ACTIVE" : "ERROR";
                updateConnectionStateAsync(tenantId, connectionId, state, healthStatus);
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
                    return Promise.of(http.errorResponse(502, "Connection test failed: " + e.getMessage()));
                });
    }

    // ─── POST /api/v1/connectors/:connectionId/enable ─────────────────────────

    public Promise<HttpResponse> handleEnableConnection(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        String connectionId = request.getPathParameter("connectionId");
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        if (connectionId == null || connectionId.isBlank()) {
            return Promise.of(http.errorResponse(400, "connectionId path parameter is required"));
        }
        return updateConnectionState(tenantId, connectionId, "ACTIVE", "healthy")
            .map(e -> http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "connectionId", connectionId,
                "state", "ACTIVE",
                "enabled", true,
                "timestamp", Instant.now().toString()
            )));
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
                .map(e -> http.jsonResponse(Map.of(
                    "tenantId", tenantId,
                    "connectionId", connectionId,
                    "state", "INACTIVE",
                    "enabled", false,
                    "timestamp", Instant.now().toString()
                )))
                .then(
                    r -> Promise.of(r),
                    e -> {
                        log.error("[disableConnection] tenant={} id={} disconnect failed: {}", tenantId, connectionId, e.getMessage(), e);
                        return updateConnectionState(tenantId, connectionId, "INACTIVE", "disabled")
                            .map(ent -> http.jsonResponse(Map.of(
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
            .map(e -> http.jsonResponse(Map.of(
                "tenantId", tenantId,
                "connectionId", connectionId,
                "state", "INACTIVE",
                "enabled", false,
                "timestamp", Instant.now().toString()
            )));
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
                return client.findById(tenantId, DC_CONNECTIONS, connectionId)
                    .then(opt -> {
                        if (opt.isEmpty()) {
                            return Promise.of(http.errorResponse(404, "Connection not found: " + connectionId));
                        }
                        DataCloudClient.Entity existing = opt.get();
                        Map<String, Object> data = new LinkedHashMap<>(existing.data());
                        data.put("updatedAt", Instant.now().toString());
                        data.put("lastCredentialRotation", Instant.now().toString());
                        if (payload.containsKey("credentials")) {
                            data.put("credentials", payload.get("credentials"));
                        }
                        return client.save(tenantId, DC_CONNECTIONS, data)
                            .map(saved -> http.jsonResponse(Map.of(
                                "tenantId", tenantId,
                                "connectionId", connectionId,
                                "rotated", true,
                                "timestamp", Instant.now().toString()
                            )));
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
            return updateConnectionState(tenantId, connectionId, "SYNCING", "sync_pending")
                .map(e -> http.jsonResponse(Map.of(
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
        return view;
    }

    private Promise<DataCloudClient.Entity> updateConnectionState(String tenantId, String connectionId, String state, String healthStatus) {
        return client.findById(tenantId, DC_CONNECTIONS, connectionId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.of(DataCloudClient.Entity.of(connectionId, DC_CONNECTIONS, Map.of(
                        "id", connectionId,
                        "tenantId", tenantId,
                        "state", state,
                        "healthStatus", healthStatus,
                        "updatedAt", Instant.now().toString()
                    )));
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
}
