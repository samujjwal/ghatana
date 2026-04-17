package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.launcher.http.RequestMetadataAttachment;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Regression coverage for shared tenant identifier resolution and validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("HttpHandlerSupport tenant resolution")
class HttpHandlerSupportTenantResolutionTest {

    private static final String BASE_URL = "http://localhost";
    private static final String ORIGIN = "http://localhost:3000";

    private final HttpHandlerSupport support = new HttpHandlerSupport(
        new ObjectMapper(),
        ORIGIN,
        "GET,POST,PUT,DELETE,OPTIONS",
        "Content-Type,X-Tenant-Id",
        true
    );

    @Test
    @DisplayName("accepts valid tenant header and trims whitespace")
    void acceptsValidTenantHeader() {
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "  tenant-001  ")
            .build();

        assertThat(support.requireTenantIdOrFail(request)).isEqualTo("tenant-001");
    }

    @Test
    @DisplayName("uses tenantId query parameter when header is absent")
    void usesTenantIdQueryParameter() {
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders?tenantId=query-tenant-7").build();

        assertThat(support.requireTenantIdOrFail(request)).isEqualTo("query-tenant-7");
    }

    @Test
    @DisplayName("rejects invalid tenant identifier format from header")
    void rejectsInvalidHeaderTenant() {
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "tenant bad value")
            .build();

        assertThat(support.requireTenantIdOrFail(request)).isNull();
        assertThat(support.peekTenantId(request)).isNull();
    }

    @Test
    @DisplayName("rejects invalid tenant identifier format from query parameter")
    void rejectsInvalidQueryTenant() {
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders?tenantId=tenant/'oops").build();

        assertThat(support.requireTenantIdOrFail(request)).isNull();
    }

    @Test
    @DisplayName("prefers attached metadata tenant when already validated upstream")
    void prefersAttachedMetadataTenant() {
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders")
            .withHeader(HttpHeaders.of("X-Tenant-Id"), "ignored-tenant")
            .build();
        request.attach(
            RequestMetadataAttachment.class,
            new RequestMetadataAttachment(
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

        assertThat(support.requireTenantIdOrFail(request)).isEqualTo("metadata-tenant-42");
    }

    @Test
    @DisplayName("returns null when tenant is missing")
    void returnsNullWhenTenantIsMissing() {
        HttpRequest request = HttpRequest.get(BASE_URL + "/api/v1/entities/orders").build();

        assertThat(support.requireTenantIdOrFail(request)).isNull();
        assertThat(support.peekTenantId(request)).isNull();
    }

    @Test
    @DisplayName("exposes strict tenant resolution flag")
    void exposesStrictTenantResolutionFlag() {
        assertThat(support.isStrictTenantResolution()).isTrue();
    }
}