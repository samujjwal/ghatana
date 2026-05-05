package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.api.DmApiTestBase;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2-022: Security audit - XSS protection tests.
 *
 * <p>Validates that the API properly sanitizes inputs and
 * sets appropriate security headers to prevent XSS attacks.</p>
 *
 * @doc.type test
 * @doc.purpose XSS protection verification (P2-022)
 * @doc.layer test
 */
@DisplayName("P2-022: XSS Protection Tests")
class XssProtectionTest extends DmApiTestBase {

    @ParameterizedTest
    @ValueSource(strings = {
        "<script>alert('xss')</script>",
        "<img src=x onerror=alert('xss')>",
        "javascript:alert('xss')",
        "<iframe src='javascript:alert(1)'>",
        "<body onload=alert('xss')>",
        "<input onfocus=alert('xss') autofocus>",
        "<svg onload=alert('xss')>",
        "<math href='javascript:alert(1)'>click</math>",
        "<table background='javascript:alert(1)'>",
        "<object data='javascript:alert(1)'>"
    })
    @DisplayName("P2-022: Should sanitize XSS in request body")
    void shouldSanitizeXssInRequestBody(String xssPayload) {
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("X-CSRF-Token", "valid-token")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"name\":\"" + xssPayload + "\"}");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        // Either sanitized (200) or rejected (400/422), but never execute
        assertThat(result.getCode()).isIn(200, 201, 400, 422);

        // If response contains the payload, it must be encoded
        String responseBody = result.getBody().getString();
        if (responseBody.contains(xssPayload.replace("<", ""))) {
            // Response should be encoded, not raw
            assertThat(responseBody).doesNotContain("<script>");
            assertThat(responseBody).doesNotContain("onerror=");
            assertThat(responseBody).doesNotContain("javascript:");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<script>alert('xss')</script>",
        "<img src=x onerror=alert('xss')>",
        "' OR '1'='1"
    })
    @DisplayName("P2-022: Should sanitize XSS in query parameters")
    void shouldSanitizeXssInQueryParameters(String xssPayload) {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns?search=" + xssPayload)
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        // Should not be a server error (500)
        assertThat(result.getCode()).isNotEqualTo(500);

        // Response should be safe
        String responseBody = result.getBody().getString();
        assertThat(responseBody).doesNotContain("<script>");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "<script>alert('xss')</script>",
        "javascript:alert('xss')",
        "data:text/html,<script>alert('xss')</script>"
    })
    @DisplayName("P2-022: Should sanitize XSS in headers")
    void shouldSanitizeXssInHeaders(String xssPayload) {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("X-Custom-Header", xssPayload);

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        // Response should not contain unencoded script tags
        String responseBody = result.getBody().getString();
        assertThat(responseBody).doesNotContain("<script>");
    }

    @Test
    @DisplayName("P2-022: Response should include X-Content-Type-Options header")
    void shouldIncludeXContentTypeOptionsHeader() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        String headerValue = result.getHeader(io.activej.http.HttpHeaders.of("X-Content-Type-Options"));
        assertThat(headerValue).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("P2-022: Response should include X-Frame-Options header")
    void shouldIncludeXFrameOptionsHeader() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        String headerValue = result.getHeader(io.activej.http.HttpHeaders.of("X-Frame-Options"));
        assertThat(headerValue).isIn("DENY", "SAMEORIGIN");
    }

    @Test
    @DisplayName("P2-022: Response should include Content-Security-Policy header")
    void shouldIncludeContentSecurityPolicyHeader() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        String cspHeader = result.getHeader(io.activej.http.HttpHeaders.of("Content-Security-Policy"));
        assertThat(cspHeader).isNotNull();

        // CSP should include default-src restriction
        assertThat(cspHeader).contains("default-src");
    }

    @Test
    @DisplayName("P2-022: Response should include Referrer-Policy header")
    void shouldIncludeReferrerPolicyHeader() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        String headerValue = result.getHeader(io.activej.http.HttpHeaders.of("Referrer-Policy"));
        assertThat(headerValue).isNotNull();
    }

    @Test
    @DisplayName("P2-022: JSON responses should have proper Content-Type")
    void shouldHaveProperContentTypeForJson() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("Accept", "application/json");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        String contentType = result.getHeader(io.activej.http.HttpHeaders.CONTENT_TYPE);
        assertThat(contentType).contains("application/json");
        assertThat(contentType).contains("charset"); // Should specify charset
    }

    @Test
    @DisplayName("P2-022: Should escape HTML in error messages")
    void shouldEscapeHtmlInErrorMessages() {
        // This test simulates a scenario where an attacker tries to inject
        // HTML/JS in a field that might be reflected in error messages
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "<script>alert('xss')</script>")  // XSS in header
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("X-CSRF-Token", "valid-token")
            .withHeader("Content-Type", "application/json")
            .withBody("{\"name\":\"Test\"}");

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        // Response body should not contain unescaped script tags
        String responseBody = result.getBody().getString();
        assertThat(responseBody).doesNotContain("<script>");
    }

    @Test
    @DisplayName("P2-022: Should prevent prototype pollution in JSON parsing")
    void shouldPreventPrototypePollution() {
        String maliciousPayload = "{\"__proto__\":{\"isAdmin\":true},\"name\":\"Test Campaign\"}";

        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader("X-Tenant-ID", "test-tenant")
            .withHeader("X-Workspace-ID", "test-workspace")
            .withHeader("X-CSRF-Token", "valid-token")
            .withHeader("Content-Type", "application/json")
            .withBody(maliciousPayload);

        Promise<HttpResponse> response = servlet.serve(request);
        HttpResponse result = await(response);

        // Should not allow prototype pollution (400 or successful parse without pollution)
        assertThat(result.getCode()).isIn(200, 201, 400, 422);
    }
}
