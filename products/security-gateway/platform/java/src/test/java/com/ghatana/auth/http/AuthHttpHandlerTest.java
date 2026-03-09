package com.ghatana.auth.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.auth.core.port.JwtTokenProvider;
import com.ghatana.auth.core.port.JwtClaims;
import com.ghatana.platform.domain.auth.UserPrincipal;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for AuthHttpHandler.
 *
 * Tests validate:
 * - All 5 endpoint handlers (login, validate, refresh, revoke, health)
 * - Success and failure scenarios
 * - HTTP status codes and response headers
 * - Constructor parameter validation
 * - Promise-based async operations
 */
@DisplayName("AuthHttpHandler Tests")
class AuthHttpHandlerTest extends EventloopTestBase {

    private AuthHttpHandler handler;
    private JwtTokenProvider jwtTokenProvider;
    private MetricsCollector metrics;
    private ObjectMapper objectMapper;

    private static final String TEST_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test";
    private static final String TEST_TENANT_ID = "tenant-test";
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        metrics = new NoopMetricsCollector();
        objectMapper = new ObjectMapper();
        handler = new AuthHttpHandler(jwtTokenProvider, metrics, objectMapper);
    }

    @Test
    @DisplayName("Should generate tokens when login is successful")
        void shouldGenerateTokensWhenLoginSuccessful() throws Exception {
        when(jwtTokenProvider.generateToken(any(TenantId.class), any(UserPrincipal.class), any(Duration.class)))
                .thenReturn(Promise.of(TEST_TOKEN));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/login")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "email", TEST_EMAIL,
                        "password", "password123",
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleLogin(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(response.getBody().readRemaining()).isGreaterThan(0);

        verify(jwtTokenProvider, times(1))
                .generateToken(any(TenantId.class), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    @DisplayName("Should return 500 when token generation fails")
        void shouldReturn500WhenTokenGenerationFails() throws Exception {
        when(jwtTokenProvider.generateToken(any(TenantId.class), any(UserPrincipal.class), any(Duration.class)))
                .thenReturn(Promise.ofException(new RuntimeException("Token generation failed")));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/login")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "email", TEST_EMAIL,
                        "password", "password123",
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleLogin(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("Should validate token and return 200 when token is valid")
        void shouldValidateTokenAndReturn200WhenValid() throws Exception {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(1));

        JwtClaims validClaims = JwtClaims.builder()
                .tokenId(UUID.randomUUID().toString())
                .subject(TEST_USER_ID)
                .issuer("auth-platform")
                .audience("api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .tenantId(com.ghatana.platform.domain.auth.TenantId.of(TEST_TENANT_ID))
                .email(TEST_EMAIL)
                .name("Test User")
                .roles(Set.of("USER"))
                .permissions(Set.of("READ", "WRITE"))
                .build();

        when(jwtTokenProvider.validateToken(any(TenantId.class), anyString()))
                .thenReturn(Promise.of(validClaims));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/validate")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "token", TEST_TOKEN,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleValidate(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
    }

    @Test
    @DisplayName("Should return 401 when token validation fails")
        void shouldReturn401WhenTokenValidationFails() throws Exception {
        when(jwtTokenProvider.validateToken(any(TenantId.class), anyString()))
                .thenReturn(Promise.ofException(new RuntimeException("Invalid token")));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/validate")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "token", TEST_TOKEN,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleValidate(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should generate new token when refresh is successful")
        void shouldGenerateNewTokenWhenRefreshSuccessful() throws Exception {
        String newToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.new";
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(1));

        JwtClaims refreshClaims = JwtClaims.builder()
                .tokenId(UUID.randomUUID().toString())
                .subject(TEST_USER_ID)
                .issuer("auth-platform")
                .audience("api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .tenantId(com.ghatana.platform.domain.auth.TenantId.of(TEST_TENANT_ID))
                .email(TEST_EMAIL)
                .name("Test User")
                .roles(Set.of("USER"))
                .permissions(Set.of("READ"))
                .build();

        when(jwtTokenProvider.validateToken(any(TenantId.class), anyString()))
                .thenReturn(Promise.of(refreshClaims));

        when(jwtTokenProvider.generateToken(any(TenantId.class), any(UserPrincipal.class), any(Duration.class)))
                .thenReturn(Promise.of(newToken));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/refresh")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "refreshToken", TEST_TOKEN,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleRefresh(request));

        assertThat(response.getCode()).isEqualTo(200);

        verify(jwtTokenProvider, times(1))
                .validateToken(any(TenantId.class), anyString());
        verify(jwtTokenProvider, times(1))
                .generateToken(any(TenantId.class), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    @DisplayName("Should return 401 when refresh token validation fails")
        void shouldReturn401WhenRefreshTokenValidationFails() throws Exception {
        when(jwtTokenProvider.validateToken(any(TenantId.class), anyString()))
                .thenReturn(Promise.ofException(new RuntimeException("Refresh token expired")));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/refresh")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "refreshToken", TEST_TOKEN,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleRefresh(request));

        assertThat(response.getCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Should return 204 when revocation is successful")
        void shouldReturn204WhenRevocationSuccessful() throws Exception {
        when(jwtTokenProvider.revokeToken(any(TenantId.class), anyString()))
                .thenReturn(Promise.of(true));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/revoke")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "token", TEST_TOKEN,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleRevoke(request));

        assertThat(response.getCode()).isEqualTo(204);

        verify(jwtTokenProvider, times(1))
                .revokeToken(any(TenantId.class), anyString());
    }

    @Test
    @DisplayName("Should return 500 when revocation fails")
        void shouldReturn500WhenRevocationFails() throws Exception {
        when(jwtTokenProvider.revokeToken(any(TenantId.class), anyString()))
                .thenReturn(Promise.ofException(new RuntimeException("Revocation failed")));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/revoke")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "token", TEST_TOKEN,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleRevoke(request));

        assertThat(response.getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("Should return 200 with UP status for health check")
        void shouldReturn200WithUpStatusForHealthCheck() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/health")
                .build();

        HttpResponse response = runPromise(() -> handler.handleHealth(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(response.getBody().readRemaining()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should throw NullPointerException when JWT provider is null")
        void shouldThrowExceptionWhenJwtTokenProviderNull() throws Exception {
        assertThatThrownBy(() -> new AuthHttpHandler(null, metrics, objectMapper))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException when metrics is null")
        void shouldThrowExceptionWhenMetricsNull() throws Exception {
        assertThatThrownBy(() -> new AuthHttpHandler(jwtTokenProvider, null, objectMapper))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException when ObjectMapper is null")
        void shouldThrowExceptionWhenObjectMapperNull() throws Exception {
        assertThatThrownBy(() -> new AuthHttpHandler(jwtTokenProvider, metrics, null))
                .isInstanceOf(NullPointerException.class);
    }
}
