package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.api.DmApiTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XSS Protection Tests")
class XssProtectionTest extends DmApiTestBase {

    @Test
    @DisplayName("response includes CSP and anti-sniff headers")
    void shouldIncludeSecurityHeaders() {
        HttpRequest request = HttpRequest.get("http://localhost/api/v1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "test-tenant")
            .build();

        HttpResponse result = runPromise(() -> servlet.serve(request));

        assertThat(result.getHeader(HttpHeaders.of("X-Content-Type-Options"))).isEqualTo("nosniff");
        assertThat(result.getHeader(HttpHeaders.of("X-Frame-Options"))).isEqualTo("DENY");
        assertThat(result.getHeader(HttpHeaders.of("Content-Security-Policy"))).contains("default-src");
    }

    @Test
    @DisplayName("request payload is not reflected as raw script in response")
    void shouldNotReflectRawScriptPayload() {
        String payload = "<script>alert('xss')</script>";
        HttpRequest request = HttpRequest.post("http://localhost/api/v1/campaigns")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "test-tenant")
            .withHeader(HttpHeaders.of("X-CSRF-Token"), "valid-token")
            .withHeader(HttpHeaders.of("Content-Type"), "application/json")
            .withBody(("{\"name\":\"" + payload + "\"}").getBytes())
            .build();

        HttpResponse result = runPromise(() -> servlet.serve(request));
        String body = result.getBody().getString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("<script>");
    }
}
