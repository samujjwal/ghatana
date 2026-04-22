/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 */
package com.ghatana.yappc.services.security;

import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("LifecycleLoginController Tests [GH-90000]")
class LifecycleLoginControllerTest extends EventloopTestBase {

    @Mock
    private JwtTokenProvider tokenProvider;

    private LifecycleLoginController controller;
        private LifecycleLoginController.UserRecord devUser;

    @BeforeEach
    void setup() { // GH-90000
        byte[] salt = LifecycleLoginController.generateSalt(); // GH-90000
        String hash = LifecycleLoginController.hashPassword("correct-password", salt); // GH-90000
        String base64Salt = Base64.getEncoder().encodeToString(salt); // GH-90000

        devUser = new LifecycleLoginController.UserRecord( // GH-90000
                "test-user-1",
                "test@yappc.io",
                hash,
                base64Salt,
                "Test User",
                List.of("admin", "user"), // GH-90000
                "test-tenant");

        controller = new LifecycleLoginController(tokenProvider, List.of(devUser)); // GH-90000
    }

    @Test
    @DisplayName("Login with correct credentials returns 200 with token and user payload [GH-90000]")
    void loginWithValidCredentialsReturns200() { // GH-90000
        when(tokenProvider.createToken(eq("test-user-1 [GH-90000]"), anyList(), any(Map.class)))
                .thenReturn("mock-jwt-token [GH-90000]");

        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login [GH-90000]")
                .withBody("{\"email\":\"test@yappc.io\",\"password\":\"correct-password\"}") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.login(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("mock-jwt-token [GH-90000]");
        assertThat(body).contains("test-user-1 [GH-90000]");
        assertThat(body).contains("test@yappc.io [GH-90000]");
    }

    @Test
    @DisplayName("Login with wrong password returns 401 [GH-90000]")
    void loginWithWrongPasswordReturns401() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login [GH-90000]")
                .withBody("{\"email\":\"test@yappc.io\",\"password\":\"wrong-password\"}") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.login(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("UNAUTHORIZED [GH-90000]");
    }

    @Test
    @DisplayName("Login with unknown email returns 401 [GH-90000]")
    void loginWithUnknownEmailReturns401() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login [GH-90000]")
                .withBody("{\"email\":\"nobody@yappc.io\",\"password\":\"correct-password\"}") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.login(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(401); // GH-90000
    }

    @Test
    @DisplayName("Login with missing email field returns 400 [GH-90000]")
    void loginMissingEmailReturns400() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login [GH-90000]")
                .withBody("{\"password\":\"correct-password\"}") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.login(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("BAD_REQUEST [GH-90000]");
    }

    @Test
    @DisplayName("Login with empty body returns 400 [GH-90000]")
    void loginEmptyBodyReturns400() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/login [GH-90000]")
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.login(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(400); // GH-90000
    }

    @Test
    @DisplayName("Logout always returns 200 regardless of auth header [GH-90000]")
    void logoutAlwaysReturns200() { // GH-90000
        HttpRequest request = HttpRequest.post("http://localhost/api/auth/logout [GH-90000]")
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.logout(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
        String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
        assertThat(body).contains("Logged out successfully [GH-90000]");
    }

    @Test
    @DisplayName("Logout with valid bearer token logs user id and returns 200 [GH-90000]")
    void logoutWithTokenReturns200() { // GH-90000
        when(tokenProvider.getUserIdFromToken("valid-token [GH-90000]"))
                .thenReturn(Optional.of("test-user-1 [GH-90000]"));

        HttpRequest request = HttpRequest.post("http://localhost/api/auth/logout [GH-90000]")
            .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token") // GH-90000
                .build(); // GH-90000

        HttpResponse response = runPromise(() -> controller.logout(request)); // GH-90000

        assertThat(response.getCode()).isEqualTo(200); // GH-90000
    }
}
