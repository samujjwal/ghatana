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
 * HTTP handler exposing the runtime capability registry.
 *
 * <p>Returns the feature configuration the server actually started with,
 * translated into client-facing states so the UI can avoid implying support
 * for features that are absent or degraded.
 *
 * @doc.type class
 * @doc.purpose Runtime capability registry HTTP handler
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class CapabilityRegistryHandler {

    private final HttpHandlerSupport httpSupport;
    private final ObjectMapper objectMapper;
    private final Supplier<Map<String, Object>> capabilitySnapshotSupplier;

    public CapabilityRegistryHandler(HttpHandlerSupport httpSupport,
                                     ObjectMapper objectMapper,
                                     Supplier<Map<String, Object>> capabilitySnapshotSupplier) {
        this.httpSupport = httpSupport;
        this.objectMapper = objectMapper;
        this.capabilitySnapshotSupplier = capabilitySnapshotSupplier;
    }

    public Promise<HttpResponse> handleCapabilities(HttpRequest request) {
        String tenantId = httpSupport.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(httpSupport.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = httpSupport.resolveCorrelationId(request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("capabilities", capabilitySnapshotSupplier.get());
        response.put("generatedAt", Instant.now().toString());

        return Promise.of(httpSupport.envelopeResponse(
            ApiResponse.success(response, tenantId, requestId),
            objectMapper));
    }
}