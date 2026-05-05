package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.api.DmApiTestBase;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-022: Security audit - CSRF protection tests.
 *
 * <p>Validates that mutating operations require proper CSRF tokens
 * or alternative protection mechanisms.</p>
 *
 * @doc.type test
 * @doc.purpose CSRF protection verification (P2-022)
 * @doc.layer test
 */
@DisplayName("P2-022: CSRF Protection Tests")
class CsrfProtectionTest extends DmApiTestBase {

    @Test
    @DisplayName("P2-022: POST without CSRF token should be rejected")
    void shouldRejectPostWithoutCsrfToken() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"name\":\"Test Campaign\"}");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        // Should return 403 Forbidden for missing CSRF
        assertThat(result.getCode()).isEqualTo(403);
        assertThat(result.getBody().getString()).containsIgnoringCase("csrf");
    }

    @Test
    @DisplayName("P2-022: POST with valid CSRF token should be accepted")
    void shouldAcceptPostWithValidCsrfToken() {
        // First obtain a CSRF token
        HttpRequest tokenRequest = HttpRequest.get("http://localhost/api/v1/csrf-token")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace");

        Promise<HttpResponse> tokenResponse = servlet.serve(tokenRequest);
        HttpResponse tokenResult = await(tokenResponse);

        assertThat(tokenResult.getCode()).isEqualTo(200);
        String csrfToken = extractCsrfToken(tokenResult);

        // Then make mutating request with token
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("X-CSRF-Token", csrfToken)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"name\":\"Test Campaign\"}");

        // Note: May still fail for other reasons, but should not fail CSRF check
        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        // Should not be 403 due to CSRF
        assertThat(result.getCode()).isNotEqualTo(403);
    }

    @Test
    @DisplayName("P2-022: PUT without CSRF token should be rejected")
    void shouldRejectPutWithoutCsrfToken() {
        HttpRequest request = HttpRequest.put("http://localhost/api/v1/campaigns/123")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"name\":\"Updated Campaign\"}");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        assertThat(result.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("P2-022: DELETE without CSRF token should be rejected")
    void shouldRejectDeleteWithoutCsrfToken() {
        HttpRequest request = HttpRequest.delete("http://localhost/api/v1/campaigns/123")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        assertThat(result.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("P2-022: PATCH without CSRF token should be rejected")
    void shouldRejectPatchWithoutCsrfToken() {
        HttpRequest request = HttpRequest.of(HttpMethod.PATCH, "http://localhost/api/v1/campaigns/123")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"status\":\"PAUSED\"}");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        assertThat(result.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("P2-022: GET requests should not require CSRF token")
    void shouldAllowGetWithoutCsrfToken() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        // Should not be 403 due to CSRF (may be 200, 401, etc. but not 403 CSRF)
        assertThat(result.getCode()).isNotEqualTo(403);
    }

    @Test
    @DisplayName("P2-022: HEAD requests should not require CSRF token")
    void shouldAllowHeadWithoutCsrfToken() {
        HttpRequest request = HttpRequest.head("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        assertThat(result.getCode()).isNotEqualTo(403);
    }

    @Test
    @DisplayName("P2-022: Invalid CSRF token should be rejected")
    void shouldRejectInvalidCsrfToken() {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("X-CSRF-Token", "invalid-token-12345")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"name\":\"Test Campaign\"}");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        assertThat(result.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("P2-022: Expired CSRF token should be rejected")
    void shouldRejectExpiredCsrfToken() {
        // Use an expired/reused token
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("X-CSRF-Token", "expired-token-from-previous-session")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"name\":\"Test Campaign\"}");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        assertThat(result.getCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("P2-022: Same-site cookie should be enforced")
    void shouldEnforceSameSiteCookie() {
        // Request from different origin
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("X-CSRF-Token", "valid-token")
            .withHeader("Origin", "https://malicious-site.com")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"name\":\"Test Campaign\"}");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        // Should reject cross-origin requests
        assertThat(result.getCode()).isIn(403, 401);
    }

    private String extractCsrfToken(HttpResponse response) {
        // Extract token from JSON response body
        String body = response.getBody().getString();
        // Simple extraction - in reality would use JSON parsing
        return body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
    }
}
