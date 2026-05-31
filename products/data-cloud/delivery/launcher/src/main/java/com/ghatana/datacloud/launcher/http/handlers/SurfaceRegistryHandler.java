package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.launcher.http.SurfaceRecord;
import com.ghatana.datacloud.launcher.http.security.RequestContext;
import com.ghatana.datacloud.launcher.http.security.RequestContextResolver;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p>P0-10: The canonical GET /api/v1/surfaces endpoint now returns typed
 * {@link SurfaceRecord} instances instead of raw capability maps. This provides
 * a single, typed contract for Runtime Truth including surface id, state, owner,
 * dependencies, dependency probe results, tenant scope, runtime profile, evidence,
 * limitations, action gates, and runtime posture metadata.
 *
 * <p>E4: Enforces canonical Action Plane permissions and includes media surfaces
 * in the runtime truth response. Media artifact processing surfaces are now
 * surfaced as part of the canonical runtime truth contract.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/v1/surfaces} — canonical Runtime Truth endpoint returning
 *       typed {@link SurfaceRecord} list with full metadata</li>
 *   <li>{@code GET /api/v1/surfaces/schema} — canonical surface schema</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Runtime Truth surface registry and schema HTTP handler
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class SurfaceRegistryHandler {

    private final HttpHandlerSupport httpSupport;
    private final ObjectMapper objectMapper;
    private final Supplier<List<SurfaceRecord>> typedSurfaceSupplier;

    /**
     * Constructs a handler with typed surface supplier.
     *
     * @param httpSupport          shared HTTP helper
     * @param objectMapper         JSON mapper
     * @param typedSurfaceSupplier typed {@link SurfaceRecord} list supplier
     */
    public SurfaceRegistryHandler(HttpHandlerSupport httpSupport,
                                  ObjectMapper objectMapper,
                                  Supplier<List<SurfaceRecord>> typedSurfaceSupplier) {
        this.httpSupport = httpSupport;
        this.objectMapper = objectMapper;
        this.typedSurfaceSupplier = typedSurfaceSupplier;
    }

    /**
     * Handle GET /api/v1/surfaces - canonical Runtime Truth endpoint.
     *
     * <p>P0-10: Returns typed {@link SurfaceRecord} instances that include the required
     * surface id, state, owner, dependencies, dependency probe results, tenant scope,
     * runtime profile, evidence, limitations, action gates, and runtime posture metadata.
     * This is the single typed canonical contract for Runtime Truth.
     */
    public Promise<HttpResponse> handleSurfaces(HttpRequest request) {
        // E4: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = httpSupport.requirePermission(request, "surface:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(httpSupport.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = httpSupport.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(httpSupport.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null || !hasExplicitTenantContext(request, contextResult)) {
            return Promise.of(httpSupport.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = httpSupport.resolveCorrelationId(request);

        List<SurfaceRecord> records = typedSurfaceSupplier.get();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("surfaces", records.stream().map(SurfaceRecord::toMap).toList());
        response.put("count", records.size());
        response.put("generatedAt", Instant.now().toString());

        return Promise.of(httpSupport.envelopeResponse(
            ApiResponse.success(response, tenantId, requestId),
            objectMapper));
    }

    /**
     * Handle GET /api/v1/surfaces/typed — deprecated, redirects to canonical endpoint.
     *
     * <p>P0-10: This endpoint is deprecated. Consumers should use the canonical
     * {@code GET /api/v1/surfaces} endpoint which now returns typed surface records
     * with full metadata. This method remains for backward compatibility and will be
     * removed in a future release.
     *
     * @deprecated Use {@link #handleSurfaces(HttpRequest)} instead
     */
    @Deprecated
    public Promise<HttpResponse> handleTypedSurfaces(HttpRequest request) {
        return handleSurfaces(request);
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
        // E4: Enforce canonical Action Plane permissions
        RequestContextResolver.ResolutionResult permissionResult = httpSupport.requirePermission(request, "surface:read");
        if (!permissionResult.isSuccess()) {
            return Promise.of(httpSupport.errorResponse(permissionResult.errorCode(), permissionResult.errorMessage()));
        }

        RequestContextResolver.ResolutionResult contextResult = httpSupport.requireRequestContext(request);
        if (!contextResult.isSuccess()) {
            return Promise.of(httpSupport.errorResponse(contextResult.errorCode(), contextResult.errorMessage()));
        }
        String tenantId = contextResult.context().map(RequestContext::tenantId).orElse(null);
        if (tenantId == null || !hasExplicitTenantContext(request, contextResult)) {
            return Promise.of(httpSupport.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = httpSupport.resolveCorrelationId(request);

        SurfaceSchemaGenerator.SurfaceSchema schema = SurfaceSchemaGenerator.generateSchema();

        return Promise.of(httpSupport.envelopeResponse(
            ApiResponse.success(schema, tenantId, requestId),
            objectMapper));
    }

    private boolean hasExplicitTenantContext(HttpRequest request, RequestContextResolver.ResolutionResult contextResult) {
        return request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-ID")) != null
            || request.getHeader(io.activej.http.HttpHeaders.of("X-Tenant-Id")) != null
            || contextResult.context().flatMap(RequestContext::principal).isPresent();
    }
}
