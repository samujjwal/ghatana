/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle — GDPR Data Erasure & Export Controller
 */
package com.ghatana.yappc.services.lifecycle.gdpr;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * GDPR compliance controller providing tenant-scoped data deletion and export endpoints.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code DELETE /api/v1/gdpr/tenant/:tenantId} — purge all data for a tenant</li>
 *   <li>{@code GET    /api/v1/gdpr/tenant/:tenantId/export} — export all tenant data (JSON)</li>
 *   <li>{@code DELETE /api/v1/gdpr/user/:userId} — purge all personal data for a user</li>
 * </ul>
 *
 * <p>All operations are idempotent and tenant-scoped. A caller must supply the
 * {@code X-Tenant-Id} header that matches the path parameter to prevent cross-tenant
 * data deletion.
 *
 * @doc.type class
 * @doc.purpose GDPR data erasure and portability for the YAPPC lifecycle service
 * @doc.layer product
 * @doc.pattern Controller
 */
public final class GdprController {

    private static final Logger log = LoggerFactory.getLogger(GdprController.class);

    private final GdprDataService gdprDataService;
    private final ObjectMapper    objectMapper;

    /**
     * Constructs the GDPR controller.
     *
     * @param gdprDataService data erasure / export service
     * @param objectMapper    JSON serialiser
     */
    public GdprController(GdprDataService gdprDataService, ObjectMapper objectMapper) {
        this.gdprDataService = Objects.requireNonNull(gdprDataService, "gdprDataService");
        this.objectMapper    = Objects.requireNonNull(objectMapper,    "objectMapper");
    }

    // ── HTTP handlers ─────────────────────────────────────────────────────────

    /**
     * {@code DELETE /api/v1/gdpr/tenant/:tenantId}
     *
     * <p>Deletes all data (approvals, lifecycle events, audit logs, AI conversations,
     * agent states) associated with the given tenant. The calling tenant must match the
     * path parameter; mismatches are rejected with HTTP 403.
     */
    public Promise<HttpResponse> deleteTenantData(HttpRequest request) {
        String tenantId    = request.getPathParameter("tenantId");
        String callerTenant = request.getHeader(
                io.activej.http.HttpHeaders.of("X-Tenant-Id"));

        if (tenantId == null || tenantId.isBlank()) {
            return badRequest("tenantId path parameter is required");
        }
        if (!tenantId.equals(callerTenant)) {
            log.warn("GDPR delete rejected: callerTenant={} != pathTenantId={}", callerTenant, tenantId);
            return forbidden("Caller tenant does not match the target tenant");
        }

        log.info("GDPR: initiating full data erasure for tenantId={}", tenantId);

        return gdprDataService.deleteAllTenantData(tenantId)
                .map(summary -> {
                    log.info("GDPR: erasure complete for tenantId={} summary={}", tenantId, summary);
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("tenantId",   tenantId);
                    body.put("erasedAt",   Instant.now().toString());
                    body.put("summary",    summary);
                    return jsonOk(body);
                })
                .mapException(ex -> {
                    log.error("GDPR: erasure failed for tenantId={}: {}", tenantId, ex.getMessage(), ex);
                    return ex;
                });
    }

    /**
     * {@code GET /api/v1/gdpr/tenant/:tenantId/export}
     *
     * <p>Returns a JSON document containing all personal data held for the requesting tenant
     * (GDPR Article 20 – right to data portability).
     */
    public Promise<HttpResponse> exportTenantData(HttpRequest request) {
        String tenantId    = request.getPathParameter("tenantId");
        String callerTenant = request.getHeader(
                io.activej.http.HttpHeaders.of("X-Tenant-Id"));

        if (tenantId == null || tenantId.isBlank()) {
            return badRequest("tenantId path parameter is required");
        }
        if (!tenantId.equals(callerTenant)) {
            return forbidden("Caller tenant does not match the target tenant");
        }

        log.info("GDPR: initiating data export for tenantId={}", tenantId);

        return gdprDataService.exportTenantData(tenantId)
                .map(export -> {
                    log.info("GDPR: export produced {} records for tenantId={}", export.totalRecords(), tenantId);
                    return jsonOk(export);
                })
                .mapException(ex -> {
                    log.error("GDPR: export failed for tenantId={}: {}", tenantId, ex.getMessage(), ex);
                    return ex;
                });
    }

    /**
     * {@code DELETE /api/v1/gdpr/user/:userId}
     *
     * <p>Deletes all personal data associated with a specific user within the calling tenant.
     */
    public Promise<HttpResponse> deleteUserData(HttpRequest request) {
        String userId      = request.getPathParameter("userId");
        String callerTenant = request.getHeader(
                io.activej.http.HttpHeaders.of("X-Tenant-Id"));

        if (userId == null || userId.isBlank()) {
            return badRequest("userId path parameter is required");
        }
        if (callerTenant == null || callerTenant.isBlank()) {
            return badRequest("X-Tenant-Id header is required");
        }

        log.info("GDPR: initiating user data erasure for userId={} tenantId={}", userId, callerTenant);

        return gdprDataService.deleteUserData(callerTenant, userId)
                .map(summary -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("userId",   userId);
                    body.put("tenantId", callerTenant);
                    body.put("erasedAt", Instant.now().toString());
                    body.put("summary",  summary);
                    return jsonOk(body);
                })
                .mapException(ex -> {
                    log.error("GDPR: user erasure failed userId={}: {}", userId, ex.getMessage(), ex);
                    return ex;
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpResponse jsonOk(Object payload) {
        try {
            return HttpResponse.ok200()
                    .withJson(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            log.error("Failed to serialise GDPR response", e);
            return HttpResponse.ofCode(500).withJson("{\"error\":\"Serialisation failure\"}").build();
        }
    }

    private static Promise<HttpResponse> badRequest(String message) {
        return Promise.of(HttpResponse.ofCode(400)
                .withJson("{\"error\":\"" + message + "\"}").build());
    }

    private static Promise<HttpResponse> forbidden(String message) {
        return Promise.of(HttpResponse.ofCode(403)
                .withJson("{\"error\":\"" + message + "\"}").build());
    }
}
