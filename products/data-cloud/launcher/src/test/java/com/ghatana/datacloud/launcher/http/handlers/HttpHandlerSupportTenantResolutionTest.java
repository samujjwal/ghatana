package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.ApiResponse;
import com.ghatana.datacloud.launcher.http.RequestMetadataAttachment;
import com.ghatana.datacloud.launcher.http.RequestTraceSupport;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Regression coverage for shared tenant identifier resolution and validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("HttpHandlerSupport tenant resolution [GH-90000]")
class HttpHandlerSupportTenantResolutionTest {

    private static final String BASE_URL = "http://localhost";
    private static final String ORIGIN = "http://localhost:3000";

    private final HttpHandlerSupport support = new HttpHandlerSupport( // GH-90000
        new ObjectMapper(), // GH-90000
        ORIGIN,
        "GET,POST,PUT,DELETE,OPTIONS",
        "Content-Type,X-Tenant-Id",
        true
    );

    @Test
    @DisplayName("accepts valid tenant header and trims whitespace [GH-90000]")
    void acceptsValidTenantHeader() { // GH-90000
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders") // GH-90000
            .withHeader(HttpHeaders.of("X-Tenant-Id [GH-90000]"), "  tenant-001  ")
            .build(); // GH-90000

        assertThat(support.requireTenantIdOrFail(request)).isEqualTo("tenant-001 [GH-90000]");
    }

    @Test
    @DisplayName("uses tenantId query parameter when header is absent [GH-90000]")
    void usesTenantIdQueryParameter() { // GH-90000
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders?tenantId=query-tenant-7").build(); // GH-90000

        assertThat(support.requireTenantIdOrFail(request)).isEqualTo("query-tenant-7 [GH-90000]");
    }

    @Test
    @DisplayName("rejects invalid tenant identifier format from header [GH-90000]")
    void rejectsInvalidHeaderTenant() { // GH-90000
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders") // GH-90000
            .withHeader(HttpHeaders.of("X-Tenant-Id [GH-90000]"), "tenant bad value")
            .build(); // GH-90000

        assertThat(support.requireTenantIdOrFail(request)).isNull(); // GH-90000
        assertThat(support.peekTenantId(request)).isNull(); // GH-90000
    }

    @Test
    @DisplayName("rejects invalid tenant identifier format from query parameter [GH-90000]")
    void rejectsInvalidQueryTenant() { // GH-90000
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders?tenantId=tenant/'oops").build(); // GH-90000

        assertThat(support.requireTenantIdOrFail(request)).isNull(); // GH-90000
    }

    @Test
    @DisplayName("prefers attached metadata tenant when already validated upstream [GH-90000]")
    void prefersAttachedMetadataTenant() { // GH-90000
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders") // GH-90000
            .withHeader(HttpHeaders.of("X-Tenant-Id [GH-90000]"), "ignored-tenant")
            .build(); // GH-90000
        request.attach( // GH-90000
            RequestMetadataAttachment.class,
            new RequestMetadataAttachment( // GH-90000
                "req-1",
                "metadata-tenant-42",
                "trace-1",
                "span-1",
                null,
                "GET",
                "/api/v1/entities/orders",
                true
            )
        );

        assertThat(support.requireTenantIdOrFail(request)).isEqualTo("metadata-tenant-42 [GH-90000]");
    }

    @Test
    @DisplayName("returns null when tenant is missing [GH-90000]")
    void returnsNullWhenTenantIsMissing() { // GH-90000
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders").build(); // GH-90000

        assertThat(support.requireTenantIdOrFail(request)).isNull(); // GH-90000
        assertThat(support.peekTenantId(request)).isNull(); // GH-90000
    }

    @Test
    @DisplayName("exposes strict tenant resolution flag [GH-90000]")
    void exposesStrictTenantResolutionFlag() { // GH-90000
        assertThat(support.isStrictTenantResolution()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("maps NOT_FOUND envelope code to HTTP 404 [GH-90000]")
    void mapsNotFoundEnvelopeTo404() { // GH-90000
        ApiResponse envelope = ApiResponse.error("ENTITY_NOT_FOUND", "entity missing", "tenant-a", "req-1"); // GH-90000

        HttpResponse response = support.envelopeResponse(envelope, new ObjectMapper()); // GH-90000

        assertThat(response.getCode()).isEqualTo(404); // GH-90000
    }

    @Test
    @DisplayName("maps timeout envelope code to HTTP 504 [GH-90000]")
    void mapsTimeoutEnvelopeTo504() { // GH-90000
        ApiResponse envelope = ApiResponse.error("QUERY_TIMEOUT", "query timed out", "tenant-a", "req-2"); // GH-90000

        HttpResponse response = support.envelopeResponse(envelope, new ObjectMapper()); // GH-90000

        assertThat(response.getCode()).isEqualTo(504); // GH-90000
    }

    @Test
    @DisplayName("maps dependency unavailable envelope code to HTTP 503 [GH-90000]")
    void mapsUnavailableEnvelopeTo503() { // GH-90000
        ApiResponse envelope = ApiResponse.error("DEPENDENCY_UNAVAILABLE", "service unavailable", "tenant-a", "req-3"); // GH-90000

        HttpResponse response = support.envelopeResponse(envelope, new ObjectMapper()); // GH-90000

        assertThat(response.getCode()).isEqualTo(503); // GH-90000
    }

    @Test
    @DisplayName("adds request and trace headers to canonical envelope responses when tracing context is present [GH-90000]")
    void addsTraceHeadersToEnvelopeResponses() { // GH-90000
        RequestTraceSupport.setCurrent(new RequestTraceSupport.TraceHeaders( // GH-90000
            "req-trace-1",
            "0123456789abcdef0123456789abcdef",
            "1111222233334444",
            "aaaabbbbccccdddd",
            true
        ));
        try {
            ApiResponse envelope = ApiResponse.error("ENTITY_NOT_FOUND", "entity missing", "tenant-a", "req-trace-1"); // GH-90000

            HttpResponse response = support.envelopeResponse(envelope, new ObjectMapper()); // GH-90000

            assertThat(response.getCode()).isEqualTo(404); // GH-90000
            assertThat(response.getHeader(HttpHeaders.of("X-Request-ID [GH-90000]"))).isEqualTo("req-trace-1 [GH-90000]");
            assertThat(response.getHeader(HttpHeaders.of("X-Correlation-ID [GH-90000]"))).isEqualTo("req-trace-1 [GH-90000]");
            assertThat(response.getHeader(HttpHeaders.of("traceparent [GH-90000]")))
                .isEqualTo("00-0123456789abcdef0123456789abcdef-1111222233334444-01 [GH-90000]");
            assertThat(response.getHeader(HttpHeaders.of("X-Parent-Span-Id [GH-90000]"))).isEqualTo("aaaabbbbccccdddd [GH-90000]");
        } finally {
            RequestTraceSupport.clearCurrent(); // GH-90000
        }
    }
}