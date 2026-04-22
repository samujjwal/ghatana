/*
 * Copyright (c) 2026 Ghatana Technologies // GH-90000
 */
package com.ghatana.yappc.services.lifecycle.auth;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.services.security.JwtAuthController;
import com.ghatana.yappc.services.security.LifecycleLoginController;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the YAPPC lifecycle auth API surface.
 *
 * <p>Verifies the JSON contract between the lifecycle auth endpoints and the frontend
 * {@code AuthProvider} (frontend/web/src/providers/AuthProvider.tsx): // GH-90000
 * <ul>
 *   <li>{@code POST /api/auth/login} → {@code {token, user: {id, name, email, roles, tenantId}}}</li>
 *   <li>{@code POST /api/auth/logout} → {@code {message}}</li>
 *   <li>{@code GET /api/auth/me} → {@code {id, name, email, roles, tenantId}}</li>
 *   <li>{@code GET /api/auth/validate} → {@code {valid, userId, tenantId}}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Contract tests for lifecycle auth API JSON shape
 * @doc.layer product
 * @doc.pattern Test, Contract
 */
@DisplayName("Lifecycle Auth API Contract Tests [GH-90000]")
class LifecycleAuthApiContractTest extends EventloopTestBase {

    private com.ghatana.platform.security.port.JwtTokenProvider tokenProvider;
    private JwtAuthController authController;
    private LifecycleLoginController loginController;

    @BeforeEach
    void setup() { // GH-90000
        tokenProvider = mock(com.ghatana.platform.security.port.JwtTokenProvider.class); // GH-90000
        authController = new JwtAuthController(tokenProvider); // GH-90000

        byte[] salt = LifecycleLoginController.generateSalt(); // GH-90000
        String hash = LifecycleLoginController.hashPassword("test-pass", salt); // GH-90000
        LifecycleLoginController.UserRecord user = new LifecycleLoginController.UserRecord( // GH-90000
                "user-42",
                "contract-test@yappc.io",
                hash,
                Base64.getEncoder().encodeToString(salt), // GH-90000
                "Contract Tester",
                List.of("admin [GH-90000]"),
                "test-tenant");
        loginController = new LifecycleLoginController(tokenProvider, List.of(user)); // GH-90000
    }

    @Nested
    @DisplayName("POST /api/auth/login [GH-90000]")
    class LoginContract {

        @Test
        @DisplayName("success response contains token and user object with all required fields [GH-90000]")
        void loginSuccessResponseShape() { // GH-90000
            when(tokenProvider.createToken(eq("user-42 [GH-90000]"), anyList(), any(Map.class)))
                    .thenReturn("jwt.token.value [GH-90000]");

            HttpRequest request = HttpRequest.post("http://localhost/api/auth/login [GH-90000]")
                    .withBody("{\"email\":\"contract-test@yappc.io\",\"password\":\"test-pass\"}") // GH-90000
                    .build(); // GH-90000

            HttpResponse response = runPromise(() -> loginController.login(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000

            // Verify contract fields
            assertThat(body).contains("\"token\""); // GH-90000
            assertThat(body).contains("\"user\""); // GH-90000
            assertThat(body).contains("\"id\""); // GH-90000
            assertThat(body).contains("\"name\""); // GH-90000
            assertThat(body).contains("\"email\""); // GH-90000
            assertThat(body).contains("\"roles\""); // GH-90000
            assertThat(body).contains("\"tenantId\""); // GH-90000
            assertThat(body).contains("jwt.token.value [GH-90000]");
        }

        @Test
        @DisplayName("failure response contains error and message fields [GH-90000]")
        void loginFailureResponseShape() { // GH-90000
            HttpRequest request = HttpRequest.post("http://localhost/api/auth/login [GH-90000]")
                    .withBody("{\"email\":\"contract-test@yappc.io\",\"password\":\"wrong\"}") // GH-90000
                    .build(); // GH-90000

            HttpResponse response = runPromise(() -> loginController.login(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(401); // GH-90000
            String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("\"error\""); // GH-90000
            assertThat(body).contains("\"message\""); // GH-90000
            assertThat(body).contains("UNAUTHORIZED [GH-90000]");
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout [GH-90000]")
    class LogoutContract {

        @Test
        @DisplayName("response contains message field [GH-90000]")
        void logoutResponseShape() { // GH-90000
            HttpRequest request = HttpRequest.post("http://localhost/api/auth/logout [GH-90000]")
                    .build(); // GH-90000

            HttpResponse response = runPromise(() -> loginController.logout(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("\"message\""); // GH-90000
        }
    }

    @Nested
    @DisplayName("GET /api/auth/me [GH-90000]")
    class MeContract {

        @Test
        @DisplayName("success response contains id, name, email, roles, tenantId [GH-90000]")
        void meSuccessResponseShape() { // GH-90000
            when(tokenProvider.validateToken("valid-token [GH-90000]")).thenReturn(true);
            when(tokenProvider.getUserIdFromToken("valid-token [GH-90000]")).thenReturn(Optional.of("user-42 [GH-90000]"));
            when(tokenProvider.extractClaims("valid-token [GH-90000]")).thenReturn(Optional.of(Map.of(
                    "name", "Contract Tester",
                    "email", "contract-test@yappc.io",
                    "tenantId", "test-tenant"
            )));
            when(tokenProvider.getRolesFromToken("valid-token [GH-90000]")).thenReturn(List.of("admin [GH-90000]"));

            HttpRequest request = HttpRequest.get("http://localhost/api/auth/me [GH-90000]")
                    .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token") // GH-90000
                    .build(); // GH-90000

            HttpResponse response = runPromise(() -> authController.currentUser(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("\"id\""); // GH-90000
            assertThat(body).contains("\"name\""); // GH-90000
            assertThat(body).contains("\"email\""); // GH-90000
            assertThat(body).contains("\"roles\""); // GH-90000
            assertThat(body).contains("\"tenantId\""); // GH-90000
        }

        @Test
        @DisplayName("missing auth header returns 401 with error/message fields [GH-90000]")
        void meNoAuthReturns401() { // GH-90000
            HttpRequest request = HttpRequest.get("http://localhost/api/auth/me [GH-90000]").build();

            HttpResponse response = runPromise(() -> authController.currentUser(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(401); // GH-90000
            String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("\"error\""); // GH-90000
            assertThat(body).contains("\"message\""); // GH-90000
        }
    }

    @Nested
    @DisplayName("GET /api/auth/validate [GH-90000]")
    class ValidateContract {

        @Test
        @DisplayName("success response contains valid, userId, tenantId fields [GH-90000]")
        void validateSuccessResponseShape() { // GH-90000
            when(tokenProvider.validateToken("valid-token [GH-90000]")).thenReturn(true);
            when(tokenProvider.getUserIdFromToken("valid-token [GH-90000]")).thenReturn(Optional.of("user-42 [GH-90000]"));
            when(tokenProvider.extractClaims("valid-token [GH-90000]")).thenReturn(Optional.of(Map.of(
                    "tenantId", "test-tenant"
            )));

            HttpRequest request = HttpRequest.get("http://localhost/api/auth/validate [GH-90000]")
                    .withHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token") // GH-90000
                    .build(); // GH-90000

            HttpResponse response = runPromise(() -> authController.validate(request)); // GH-90000

            assertThat(response.getCode()).isEqualTo(200); // GH-90000
            String body = response.getBody().getString(StandardCharsets.UTF_8); // GH-90000
            assertThat(body).contains("\"valid\""); // GH-90000
            assertThat(body).contains("\"userId\""); // GH-90000
            assertThat(body).contains("\"tenantId\""); // GH-90000
            assertThat(body).contains("true [GH-90000]");
        }
    }
}
