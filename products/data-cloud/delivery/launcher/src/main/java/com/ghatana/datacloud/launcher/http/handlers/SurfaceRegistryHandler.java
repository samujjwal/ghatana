package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * HTTP handler exposing the runtime surface registry and surface schema.
 *
 * <p>Returns the feature configuration the server actually started with,
 * translated into client-facing states so the UI can avoid implying support
 * for surfaces that are absent or degraded.
 *
 * <p>Also serves the unified surface schema which is the single source of truth
 * for all surface-based feature gates, preventing drift between docs/UI/runtime.
 *
 * @doc.type class
 * @doc.purpose Runtime Truth surface registry and schema HTTP handler
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class SurfaceRegistryHandler {

    private final HttpHandlerSupport httpSupport;
    private final ObjectMapper objectMapper;
    private final Supplier<Map<String, Object>> surfaceSnapshotSupplier;

    public SurfaceRegistryHandler(HttpHandlerSupport httpSupport,
                                     ObjectMapper objectMapper,
                                     Supplier<Map<String, Object>> surfaceSnapshotSupplier) {
        this.httpSupport = httpSupport;
        this.objectMapper = objectMapper;
        this.surfaceSnapshotSupplier = surfaceSnapshotSupplier;
    }

    private Promise<HttpResponse> handleRuntimeTruthSnapshot(HttpRequest request) {
        String tenantId = httpSupport.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(httpSupport.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = httpSupport.resolveCorrelationId(request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("surfaces", surfaceSnapshotSupplier.get());
        response.put("generatedAt", Instant.now().toString());

        return Promise.of(httpSupport.envelopeResponse(
            ApiResponse.success(response, tenantId, requestId),
            objectMapper));
    }

    /**
     * Handle GET /api/v1/surfaces - canonical Runtime Truth endpoint.
     * DC-P1.12: Removed compatibility /api/v1/capabilities handler; use this canonical endpoint only.
     */
    public Promise<HttpResponse> handleSurfaces(HttpRequest request) {
        return handleRuntimeTruthSnapshot(request);
    }

    /**
     * Handle GET /api/v1/surfaces/schema - canonical Runtime Truth schema endpoint.
     * DC-P1.12: Removed compatibility /api/v1/capabilities/schema handler; use this canonical endpoint only.
     * 
     * P2-CAP-1: This endpoint serves the surface schema which is the single source
     * of truth for all surface-based feature gates, preventing drift between
     * docs/UI/runtime.
     */
    public Promise<HttpResponse> handleSurfaceSchema(HttpRequest request) {
        String tenantId = httpSupport.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(httpSupport.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = httpSupport.resolveCorrelationId(request);

        // Generate the surface schema from SPI interfaces
        SurfaceSchemaGenerator.SurfaceSchema schema = SurfaceSchemaGenerator.generateSchema();

        return Promise.of(httpSupport.envelopeResponse(
            ApiResponse.success(schema, tenantId, requestId),
            objectMapper));
    }
}