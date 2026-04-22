/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 */
package com.ghatana.yappc.services.security;

import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for lifecycle JWT auth endpoints.
 *
 * @doc.type class
 * @doc.purpose Verifies JwtAuthController bearer-token auth behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("JwtAuthController [GH-90000]")
class JwtAuthControllerTest extends EventloopTestBase {

    @Test
    @DisplayName("/api/auth/me returns 401 when authorization header is missing [GH-90000]")
    void currentUserMissingAuthorizationReturns401() { // GH-90000
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class); // GH-90000
        JwtAuthController controller = new JwtAuthController(tokenProvider); // GH-90000

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/auth/me").build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.currentUser(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
    }

    @Test
    @DisplayName("/api/auth/me returns 200 with user payload when token is valid [GH-90000]")
    void currentUserValidTokenReturnsUserPayload() { // GH-90000
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class); // GH-90000
        when(tokenProvider.validateToken("valid-token [GH-90000]")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid-token [GH-90000]")).thenReturn(Optional.of("user-123 [GH-90000]"));
        when(tokenProvider.extractClaims("valid-token [GH-90000]")).thenReturn(Optional.of(Map.of(
                "name", "Sam User",
                "email", "sam@example.com",
                "tenantId", "tenant-123"
        )));
        when(tokenProvider.getRolesFromToken("valid-token [GH-90000]")).thenReturn(List.of("editor", "viewer"));

        JwtAuthController controller = new JwtAuthController(tokenProvider); // GH-90000

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/auth/me") // GH-90000
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.currentUser(request)); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(body).contains("\"id\":\"user-123\""); // GH-90000
        assertThat(body).contains("\"name\":\"Sam User\""); // GH-90000
        assertThat(body).contains("\"tenantId\":\"tenant-123\""); // GH-90000
    }

    @Test
    @DisplayName("/api/auth/validate returns 200 and valid=true when token is valid [GH-90000]")
    void validateValidTokenReturns200() { // GH-90000
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class); // GH-90000
        when(tokenProvider.validateToken("valid-token [GH-90000]")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid-token [GH-90000]")).thenReturn(Optional.of("user-abc [GH-90000]"));
        when(tokenProvider.extractClaims("valid-token [GH-90000]")).thenReturn(Optional.of(Map.of("tenantId", "tenant-abc")));

        JwtAuthController controller = new JwtAuthController(tokenProvider); // GH-90000

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/auth/validate") // GH-90000
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.validate(request)); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        assertThat(body).contains("\"valid\":true"); // GH-90000
        assertThat(body).contains("\"userId\":\"user-abc\""); // GH-90000
    }

    @Test
    @DisplayName("/api/auth/validate returns 401 for invalid token [GH-90000]")
    void validateInvalidTokenReturns401() { // GH-90000
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class); // GH-90000
        when(tokenProvider.validateToken("bad-token [GH-90000]")).thenReturn(false);

        JwtAuthController controller = new JwtAuthController(tokenProvider); // GH-90000

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/auth/validate") // GH-90000
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.validate(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
    }
}
