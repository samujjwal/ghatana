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
        String tenantId   = http.resolveTenantId(request);

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
}
