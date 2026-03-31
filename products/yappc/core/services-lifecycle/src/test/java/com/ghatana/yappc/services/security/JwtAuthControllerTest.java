/*
 * Copyright (c) 2026 Ghatana Technologies
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
@DisplayName("JwtAuthController")
class JwtAuthControllerTest extends EventloopTestBase {

    @Test
    @DisplayName("/api/auth/me returns 401 when authorization header is missing")
    void currentUserMissingAuthorizationReturns401() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        JwtAuthController controller = new JwtAuthController(tokenProvider);

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/auth/me").build();

        HttpResponse response = runPromise(() -> controller.currentUser(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("/api/auth/me returns 200 with user payload when token is valid")
    void currentUserValidTokenReturnsUserPayload() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid-token")).thenReturn(Optional.of("user-123"));
        when(tokenProvider.extractClaims("valid-token")).thenReturn(Optional.of(Map.of(
                "name", "Sam User",
                "email", "sam@example.com",
                "tenantId", "tenant-123"
        )));
        when(tokenProvider.getRolesFromToken("valid-token")).thenReturn(List.of("editor", "viewer"));

        JwtAuthController controller = new JwtAuthController(tokenProvider);

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/auth/me")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();

        HttpResponse response = runPromise(() -> controller.currentUser(request));
        String body = response.getBody().getString(StandardCharsets.UTF_8);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).contains("\"id\":\"user-123\"");
        assertThat(body).contains("\"name\":\"Sam User\"");
        assertThat(body).contains("\"tenantId\":\"tenant-123\"");
    }

    @Test
    @DisplayName("/api/auth/validate returns 200 and valid=true when token is valid")
    void validateValidTokenReturns200() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid-token")).thenReturn(Optional.of("user-abc"));
        when(tokenProvider.extractClaims("valid-token")).thenReturn(Optional.of(Map.of("tenantId", "tenant-abc")));

        JwtAuthController controller = new JwtAuthController(tokenProvider);

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/auth/validate")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();

        HttpResponse response = runPromise(() -> controller.validate(request));
        String body = response.getBody().getString(StandardCharsets.UTF_8);

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(body).contains("\"valid\":true");
        assertThat(body).contains("\"userId\":\"user-abc\"");
    }

    @Test
    @DisplayName("/api/auth/validate returns 401 for invalid token")
    void validateInvalidTokenReturns401() {
        JwtTokenProvider tokenProvider = mock(JwtTokenProvider.class);
        when(tokenProvider.validateToken("bad-token")).thenReturn(false);

        JwtAuthController controller = new JwtAuthController(tokenProvider);

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost/api/auth/validate")
                .withHeader(HttpHeaders.AUTHORIZATION, "Bearer bad-token")
                .build();

        HttpResponse response = runPromise(() -> controller.validate(request));

        assertThat(response.getCode()).isEqualTo(401);
    }
}
