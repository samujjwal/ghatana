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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LifecycleLoginController}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LifecycleLoginController Tests")
class LifecycleLoginControllerTest extends EventloopTestBase {

    @Mock
    private JwtTokenProvider tokenProvider;

    private LifecycleLoginController controller;
        private LifecycleLoginController.UserRecord devUser;

    @BeforeEach
    void setup() {
        byte[] salt = LifecycleLoginController.generateSalt();
        String hash = LifecycleLoginController.hashPassword("correct-password", salt);
        String base64Salt = Base64.getEncoder().encodeToString(salt);

        devUser = new LifecycleLoginController.UserRecord(
                "test-user-1",
                "test@yappc.io",
                hash,
                base64Salt,
                "Test User",
                List.of("admin", "user"),
                "test-tenant");

        controller = new LifecycleLoginController(tokenProvider, List.of(devUser));
    }

    @Test
    @DisplayName("Login with correct credentials returns 200 with token and user payload")
    void loginWithValidCredentialsReturns200() {
        when(tokenProvider.createToken(eq("test-user-1"), anyList(), any(Map.class)))
                .thenReturn("mock-jwt-token");

        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login")
                .withBody("{\"email\":\"test@yappc.io\",\"password\":\"correct-password\"}")
                .build();

        HttpResponse response = runPromise(() -> controller.login(request));

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("mock-jwt-token");
        assertThat(body).contains("test-user-1");
        assertThat(body).contains("test@yappc.io");
    }

    @Test
    @DisplayName("Login with wrong password returns 401")
    void loginWithWrongPasswordReturns401() {
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login")
                .withBody("{\"email\":\"test@yappc.io\",\"password\":\"wrong-password\"}")
                .build();

        HttpResponse response = runPromise(() -> controller.login(request));

        assertThat(response.getCode()).isEqualTo(401);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("UNAUTHORIZED");
    }

    @Test
    @DisplayName("Login with unknown email returns 401")
    void loginWithUnknownEmailReturns401() {
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login")
                .withBody("{\"email\":\"nobody@yappc.io\",\"password\":\"correct-password\"}")
                .build();

        HttpResponse response = runPromise(() -> controller.login(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Login with missing email field returns 400")
    void loginMissingEmailReturns400() {
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login")
                .withBody("{\"password\":\"correct-password\"}")
                .build();

        HttpResponse response = runPromise(() -> controller.login(request));

        assertThat(response.getCode()).isEqualTo(400);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("BAD_REQUEST");
    }

    @Test
    @DisplayName("Login with empty body returns 400")
    void loginEmptyBodyReturns400() {
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login")
                .build();

        HttpResponse response = runPromise(() -> controller.login(request));

        assertThat(response.getCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Logout always returns 200 regardless of auth header")
    void logoutAlwaysReturns200() {
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/logout")
                .build();

        HttpResponse response = runPromise(() -> controller.logout(request));

        assertThat(response.getCode()).isEqualTo(200);
        String body = response.getBody().getString(StandardCharsets.UTF_8);
        assertThat(body).contains("Logged out successfully");
    }

    @Test
    @DisplayName("Logout with valid bearer token logs user id and returns 200")
    void logoutWithTokenReturns200() {
        when(tokenProvider.getUserIdFromToken("valid-token"))
                .thenReturn(Optional.of("test-user-1"));

        HttpRequest request = HttpRequest.post("http://localhost/api/auth/logout")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();

        HttpResponse response = runPromise(() -> controller.logout(request));

        assertThat(response.getCode()).isEqualTo(200);
    }
}
