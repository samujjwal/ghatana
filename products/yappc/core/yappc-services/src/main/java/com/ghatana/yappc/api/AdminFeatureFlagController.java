package com.ghatana.yappc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.platform.governance.security.Principal;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Admin feature-flag API backed by canonical tenant-scoped Data Cloud records.
 *
 * @doc.type class
 * @doc.purpose Provides audited tenant feature-flag list, update, and audit-log APIs for the admin UI
 * @doc.layer api
 * @doc.pattern Controller
 */
public final class AdminFeatureFlagController {

    private static final Logger log = LoggerFactory.getLogger(AdminFeatureFlagController.class);
    public static final String FLAG_COLLECTION = "yappc_feature_flags";
    public static final String AUDIT_COLLECTION = "yappc_feature_flag_audit";

    private final DataCloudClient dataCloudClient;
    private final ObjectMapper objectMapper;

    public AdminFeatureFlagController(@NotNull DataCloudClient dataCloudClient, @NotNull ObjectMapper objectMapper) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public Promise<HttpResponse> listFlags(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"tenant context required\"}").build());
        }
        DataCloudClient.Query query = DataCloudClient.Query.builder().limit(500).build();
        return dataCloudClient.query(tenantId, FLAG_COLLECTION, query)
                .map(records -> records.stream()
                        .map(record -> normalizeFlag(tenantId, record.id(), record.data()))
                        .toList())
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "feature flags", tenantId, null));
    }

    public Promise<HttpResponse> setFlag(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"tenant context required\"}").build());
        }
        Principal principal = request.getAttachment(Principal.class);
        String actorId = principal == null ? "system" : principal.getName();
        String flagKey = request.getPathParameter("flagKey");
        if (flagKey == null || flagKey.isBlank()) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"flagKey is required\"}").build());
        }
        return request.loadBody().then(body -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(body.asString(StandardCharsets.UTF_8), Map.class);
                boolean enabled = Boolean.TRUE.equals(payload.get("enabled"));
                String reason = stringValue(payload.get("reason"));
                if (reason.isBlank()) {
                    return Promise.of(HttpResponse.ofCode(400)
                            .withJson("{\"error\":\"reason is required\"}")
                            .build());
                }
                int rolloutPercentage = rolloutPercentage(payload.get("rolloutPercentage"));
                return findFlag(tenantId, flagKey)
                        .then(existing -> {
                            Map<String, Object> previous = existing == null ? Map.of() : existing.data();
                            Map<String, Object> updated = flagRecord(
                                    existing == null ? flagKey : existing.id(),
                                    flagKey,
                                    tenantId,
                                    enabled,
                                    rolloutPercentage,
                                    stringValue(payload.get("description")),
                                    actorId,
                                    previous);
                            Map<String, Object> audit = auditRecord(flagKey, tenantId, actorId, reason, previous, updated, request);
                            return dataCloudClient.save(tenantId, FLAG_COLLECTION, updated)
                                    .then(saved -> dataCloudClient.save(tenantId, AUDIT_COLLECTION, audit)
                                            .map(ignored -> normalizeFlag(tenantId, saved.id(), saved.data())))
                                    .map(this::jsonResponse);
                        });
            } catch (Exception error) {
                return Promise.of(HttpResponse.ofCode(400)
                        .withJson("{\"error\":\"Invalid feature flag request\"}")
                        .build());
            }
        }).then((response, error) -> errorResponse(response, error, "feature flag update", tenantId, flagKey));
    }

    public Promise<HttpResponse> listAudit(HttpRequest request) {
        String tenantId = resolveTenantId(request);
        if (tenantId == null) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"tenant context required\"}").build());
        }
        String flagKey = request.getPathParameter("flagKey");
        if (flagKey == null || flagKey.isBlank()) {
            return Promise.of(HttpResponse.ofCode(400).withJson("{\"error\":\"flagKey is required\"}").build());
        }
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filter(DataCloudClient.Filter.eq("flagKey", flagKey))
                .limit(100)
                .build();
        return dataCloudClient.query(tenantId, AUDIT_COLLECTION, query)
                .map(records -> records.stream().map(record -> normalizeAudit(record.data())).toList())
                .map(this::jsonResponse)
                .then((response, error) -> errorResponse(response, error, "feature flag audit", tenantId, flagKey));
    }

    private Promise<DataCloudClient.Entity> findFlag(String tenantId, String flagKey) {
        return dataCloudClient.findById(tenantId, FLAG_COLLECTION, flagKey)
                .map(found -> found.orElse(null));
    }

    private Map<String, Object> flagRecord(
            String id,
            String flagKey,
            String tenantId,
            boolean enabled,
            int rolloutPercentage,
            String description,
            String actorId,
            Map<String, Object> previous) {
        Instant now = Instant.now();
        Map<String, Object> record = new LinkedHashMap<>(previous);
        record.put("id", id);
        record.put("key", flagKey);
        record.put("tenantId", tenantId);
        record.put("description", description.isBlank()
                ? String.valueOf(previous.getOrDefault("description", flagKey))
                : description);
        record.put("enabled", enabled);
        record.put("rolloutPercentage", rolloutPercentage);
        record.put("provider", "DATA_CLOUD_CANONICAL");
        record.put("createdAt", String.valueOf(previous.getOrDefault("createdAt", now.toString())));
        record.put("updatedAt", now.toString());
        record.put("updatedBy", actorId);
        return record;
    }

    private Map<String, Object> auditRecord(
            String flagKey,
            String tenantId,
            String actorId,
            String reason,
            Map<String, Object> previous,
            Map<String, Object> updated,
            HttpRequest request) {
        return Map.of(
                "id", UUID.randomUUID().toString(),
                "flagKey", flagKey,
                "tenantId", tenantId,
                "previousValue", Boolean.TRUE.equals(previous.get("enabled")),
                "newValue", Boolean.TRUE.equals(updated.get("enabled")),
                "changedBy", actorId,
                "reason", reason,
                "correlationId", correlationId(request),
                "timestamp", String.valueOf(updated.get("updatedAt")));
    }

    private Map<String, Object> normalizeFlag(String tenantId, String id, Map<String, Object> data) {
        return Map.of(
                "id", stringOr(id, stringValue(data.get("id"))),
                "key", stringValue(data.get("key")),
                "description", stringValue(data.get("description")),
                "enabled", Boolean.TRUE.equals(data.get("enabled")),
                "tenantId", stringOr(tenantId, stringValue(data.get("tenantId"))),
                "rolloutPercentage", rolloutPercentage(data.get("rolloutPercentage")),
                "createdAt", stringValue(data.get("createdAt")),
                "updatedAt", stringValue(data.get("updatedAt")),
                "updatedBy", stringValue(data.get("updatedBy")),
                "provider", stringOr("DATA_CLOUD_CANONICAL", stringValue(data.get("provider"))));
    }

    private Map<String, Object> normalizeAudit(Map<String, Object> data) {
        return Map.of(
                "id", stringValue(data.get("id")),
                "flagKey", stringValue(data.get("flagKey")),
                "previousValue", Boolean.TRUE.equals(data.get("previousValue")),
                "newValue", Boolean.TRUE.equals(data.get("newValue")),
                "changedBy", stringValue(data.get("changedBy")),
                "reason", stringValue(data.get("reason")),
                "timestamp", stringValue(data.get("timestamp")));
    }

    private int rolloutPercentage(Object value) {
        int percentage = value instanceof Number number ? number.intValue() : 100;
        return Math.max(0, Math.min(100, percentage));
    }

    private String resolveTenantId(HttpRequest request) {
        Principal principal = request.getAttachment(Principal.class);
        if (principal != null && principal.getTenantId() != null && !principal.getTenantId().isBlank()) {
            return principal.getTenantId();
        }
        String queryTenant = request.getQueryParameter("tenantId");
        return queryTenant == null || queryTenant.isBlank() ? null : queryTenant;
    }

    private String correlationId(HttpRequest request) {
        String value = request.getHeader(HttpHeaders.of("X-Correlation-ID"));
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringOr(String fallback, String value) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private HttpResponse jsonResponse(Object value) {
        try {
            return HttpResponse.ok200().withJson(objectMapper.writeValueAsString(value)).build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize feature flag response", e);
        }
    }

    private Promise<HttpResponse> errorResponse(
            HttpResponse response,
            Exception error,
            String operation,
            String tenantId,
            String flagKey) {
        if (error == null) {
            return Promise.of(response);
        }
        log.error("Data Cloud {} failed: tenantId={} flagKey={}", operation, tenantId, flagKey, error);
        return Promise.of(HttpResponse.ofCode(503)
                .withJson("{\"error\":\"feature flag service unavailable\"}")
                .build());
    }
}
