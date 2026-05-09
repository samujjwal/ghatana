package com.ghatana.digitalmarketing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared DMOS API error-response helper.
 *
 * <p>Ensures all servlet error responses use the canonical JSON envelope and
 * propagate a correlation ID for diagnostics.</p>
 *
 * @doc.type class
 * @doc.purpose Standard JSON error envelope builder for DMOS API servlets
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class DmosApiErrorResponses {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DmosApiErrorResponses() {
        // Utility class
    }

    public static HttpResponse error(int status, String message, HttpRequest request) {
        return error(status, message, DmosApiHeaderValidator.getCorrelationId(request), Map.of());
    }

    public static HttpResponse error(int status, String message, String correlationId) {
        return error(status, message, correlationId, Map.of());
    }

    public static HttpResponse error(
        int status,
        String message,
        String correlationId,
        Map<String, String> details
    ) {
        try {
            StandardErrorEnvelope envelope = details.isEmpty()
                ? StandardErrorEnvelope.of(status, message, correlationId)
                : StandardErrorEnvelope.withDetails(status, message, correlationId, details);

            return HttpResponse.ofCode(status)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
                .withBody(OBJECT_MAPPER.writeValueAsBytes(envelope))
                .build();
        } catch (Exception serializationError) {
            String fallback = "{\"error\":\"INTERNAL_ERROR\",\"message\":\"Failed to serialize error response\",\"status\":500,\"correlationId\":\""
                + correlationId + "\",\"details\":{}}";
            return HttpResponse.ofCode(500)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
                .withBody(fallback.getBytes(StandardCharsets.UTF_8))
                .build();
        }
    }
}