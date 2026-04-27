package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.analytics.export.EntityExportService;
import com.ghatana.datacloud.launcher.http.ApiInputValidator;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles entity bulk-export HTTP endpoints (CSV and NDJSON).
 *
 * <p>Extracted from {@code EntityCrudHandler} to respect the single-responsibility
 * principle (DC-004). Registered in the server via method reference:
 * <pre>{@code
 * .with(HttpMethod.GET, "/api/v1/entities/:collection/export", exportHandler::handleExportEntities)
 * }</pre>
 *
 * <p>Returns {@code 501 Not Implemented} when no {@link EntityExportService} is
 * configured on this instance.
 *
 * @doc.type    class
 * @doc.purpose HTTP handler for entity export in CSV and NDJSON formats
 * @doc.layer   product
 * @doc.pattern Handler
 */
public class EntityExportHandler {

    private static final Logger log = LoggerFactory.getLogger(EntityExportHandler.class);

    private final EntityExportService exportService;
    private final HttpHandlerSupport http;

    /**
     * Creates an export handler.
     *
     * @param exportService the export service; may be {@code null} — handler returns 501 in that case
     * @param http          shared HTTP helpers
     */
    public EntityExportHandler(EntityExportService exportService, HttpHandlerSupport http) {
        this.exportService = exportService;
        this.http = http;
    }

    /**
     * Exports entities for a collection in CSV (default) or NDJSON format.
     *
     * <p>Query parameters:
     * <ul>
     *   <li>{@code format} — {@code csv} (default) or {@code ndjson}</li>
     *   <li>{@code limit} — maximum number of entities to export (default 10 000)</li>
     * </ul>
     *
     * @param request the incoming HTTP request
     * @return a Promise resolving to the HTTP response
     */
    public Promise<HttpResponse> handleExportEntities(HttpRequest request) {
        if (exportService == null) {
            return Promise.of(http.errorResponse(501, "Export service not configured on this server"));
        }

        String collection = request.getPathParameter("collection");
        String tenantId   = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        String format = request.getQueryParameter("format");
        if (format == null) format = "csv";

        int limit = 10_000;
        String limitStr = request.getQueryParameter("limit");
        if (limitStr != null) {
            try {
                limit = Integer.parseInt(limitStr);
            } catch (NumberFormatException e) {
                return Promise.of(http.errorResponse(400, "Invalid 'limit' query parameter: " + limitStr));
            }
        }

        final String finalTenant     = tenantId;
        final String finalCollection = collection;
        final int    finalLimit      = limit;

        if ("ndjson".equalsIgnoreCase(format)) {
            return exportService.exportNdjson(finalTenant, finalCollection, Map.of(), finalLimit)
                    .map(data -> HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/x-ndjson; charset=utf-8"))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(http.corsAllowOrigin()))
                            .withBody(data.getBytes(StandardCharsets.UTF_8))
                            .build())
                    .then(Promise::of, e -> {
                        log.error("NDJSON export failed tenant={} collection={}", finalTenant, finalCollection, e);
                        return Promise.of(http.errorResponse(500, "Export failed: " + e.getMessage()));
                    });
        } else {
            return exportService.exportCsv(finalTenant, finalCollection, Map.of(), finalLimit)
                    .map(data -> HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/csv; charset=utf-8"))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(http.corsAllowOrigin()))
                            .withBody(data.getBytes(StandardCharsets.UTF_8))
                            .build())
                    .then(Promise::of, e -> {
                        log.error("CSV export failed tenant={} collection={}", finalTenant, finalCollection, e);
                        return Promise.of(http.errorResponse(500, "Export failed: " + e.getMessage()));
                    });
        }
    }

    /**
     * POST endpoint for PII export with two-step approval flow.
     *
     * <p>Body fields:
     * <ul>
     *   <li>{@code dryRun} — when {@code true}, returns a preview and a confirmation token</li>
     *   <li>{@code confirmationToken} — required to authorise the actual export</li>
     *   <li>{@code format} — {@code csv} (default) or {@code ndjson}</li>
     *   <li>{@code limit} — maximum number of entities to export (default 10 000)</li>
     * </ul>
     */
    public Promise<HttpResponse> handleExportEntitiesWithApproval(HttpRequest request) {
        if (exportService == null) {
            return Promise.of(http.errorResponse(501, "Export service not configured on this server"));
        }

        String collection = request.getPathParameter("collection");
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        Optional<String> tenantErr = ApiInputValidator.validateTenantId(tenantId);
        if (tenantErr.isPresent()) return Promise.of(http.errorResponse(400, tenantErr.get()));
        Optional<String> collErr = ApiInputValidator.validateCollection(collection);
        if (collErr.isPresent()) return Promise.of(http.errorResponse(400, collErr.get()));

        return request.loadBody().then(buf -> {
            try {
                String body = buf.getString(StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = http.objectMapper().readValue(body, Map.class);

                boolean dryRun = Boolean.TRUE.equals(payload.get("dryRun"));
                String confirmationToken = payload.getOrDefault("confirmationToken", "").toString();
                String format = (String) payload.getOrDefault("format", "csv");
                int limit = 10_000;
                Object rawLimit = payload.get("limit");
                if (rawLimit instanceof Number n) {
                    limit = n.intValue();
                }

                final String finalTenant = tenantId;
                final String finalCollection = collection;
                final int finalLimit = limit;

                if (dryRun) {
                    long issuedAtMs = Instant.now().toEpochMilli();
                    String token = DestructiveActionToken.buildToken(
                        "export-pii", tenantId, collection, issuedAtMs);
                    log.info("[export-pii] DRY RUN tenant={} collection={} format={} limit={}",
                        tenantId, collection, format, limit);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("collection", collection);
                    result.put("format", format);
                    result.put("limit", limit);
                    result.put("dryRun", true);
                    result.put("status", "DRY_RUN_COMPLETE");
                    result.put("confirmationToken", token);
                    result.put("tokenExpiresInSec", DestructiveActionToken.TOKEN_VALIDITY_MS / 1000);
                    return Promise.of(http.jsonResponse(result));
                }

                if (confirmationToken.isBlank()) {
                    return Promise.of(http.errorResponse(400,
                        "confirmationToken is required to authorise PII export. " +
                        "Perform a dry-run first to obtain a valid token."));
                }

                DestructiveActionToken.TokenValidationResult tokenResult =
                    DestructiveActionToken.validateToken(confirmationToken, "export-pii", tenantId, collection);
                if (!tokenResult.valid()) {
                    log.warn("[export-pii] REJECTED invalid token: {} collection={} tenant={}",
                        tokenResult.reason(), collection, tenantId);
                    return Promise.of(http.errorResponse(403,
                        "Confirmation token is invalid or expired: " + tokenResult.reason()));
                }

                if ("ndjson".equalsIgnoreCase(format)) {
                    return exportService.exportNdjson(finalTenant, finalCollection, Map.of(), finalLimit)
                        .map(data -> HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("application/x-ndjson; charset=utf-8"))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(http.corsAllowOrigin()))
                            .withBody(data.getBytes(StandardCharsets.UTF_8))
                            .build())
                        .then(Promise::of, e -> {
                            log.error("NDJSON export failed tenant={} collection={}", finalTenant, finalCollection, e);
                            return Promise.of(http.errorResponse(500, "Export failed: " + e.getMessage()));
                        });
                } else {
                    return exportService.exportCsv(finalTenant, finalCollection, Map.of(), finalLimit)
                        .map(data -> HttpResponse.ok200()
                            .withHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValue.of("text/csv; charset=utf-8"))
                            .withHeader(HttpHeaders.of("Access-Control-Allow-Origin"), HttpHeaderValue.of(http.corsAllowOrigin()))
                            .withBody(data.getBytes(StandardCharsets.UTF_8))
                            .build())
                        .then(Promise::of, e -> {
                            log.error("CSV export failed tenant={} collection={}", finalTenant, finalCollection, e);
                            return Promise.of(http.errorResponse(500, "Export failed: " + e.getMessage()));
                        });
                }
            } catch (Exception e) {
                log.error("Error parsing export approval request", e);
                return Promise.of(http.errorResponse(400, "Invalid export request body: " + e.getMessage()));
            }
        });
    }
}
