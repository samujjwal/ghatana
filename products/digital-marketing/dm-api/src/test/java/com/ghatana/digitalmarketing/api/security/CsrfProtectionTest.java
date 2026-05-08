package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.api.DmApiTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CSRF Protection Tests")
class CsrfProtectionTest extends DmApiTestBase {

    @Test
    @DisplayName("POST without CSRF token is rejected")
    void shouldRejectPostWithoutCsrfToken() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "test-tenant")
            .withHeader(HttpHeaders.of("Content-Type"), "application/json")
            .withBody("{\"name\":\"Test Campaign\"}".getBytes())
            .build();

        HttpResponse result = runPromise(() -> servlet.serve(request));

        assertThat(result.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("POST with CSRF token is accepted by base servlet")
    void shouldAcceptPostWithCsrfToken() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "test-tenant")
            .withHeader(HttpHeaders.of("X-CSRF-Token"), "valid-token")
            .withHeader(HttpHeaders.of("Content-Type"), "application/json")
            .withBody("{\"name\":\"Test Campaign\"}".getBytes())
            .build();

        HttpResponse result = runPromise(() -> servlet.serve(request));

        assertThat(result.getCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("GET does not require CSRF token")
    void shouldAllowGetWithoutCsrfToken() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "test-tenant")
            .build();

        HttpResponse result = runPromise(() -> servlet.serve(request));

        assertThat(result.getCode()).isEqualTo(200);
    }
}
