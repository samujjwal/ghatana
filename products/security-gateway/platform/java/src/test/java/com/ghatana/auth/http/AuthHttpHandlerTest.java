package com.ghatana.auth.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.auth.core.port.JwtTokenProvider;
import com.ghatana.auth.core.port.JwtClaims;
import com.ghatana.auth.service.AuthenticationService;
import com.ghatana.platform.domain.auth.AuthResult;
import com.ghatana.platform.domain.auth.Session;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.UserPrincipal;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserId;
import com.ghatana.platform.domain.auth.SessionId;
import com.ghatana.platform.domain.auth.TokenId;
import com.ghatana.platform.domain.auth.TokenType;
import com.ghatana.platform.domain.auth.ClientId;
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
    private AuthenticationService authenticationService;
    private MetricsCollector metrics;
    private ObjectMapper objectMapper;

    private static final String TEST_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test";
    private static final String TEST_TENANT_ID = "tenant-test";
    private static final String TEST_USER_ID = "user-123";
    private static final String TEST_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        authenticationService = mock(AuthenticationService.class);
        metrics = new NoopMetricsCollector();
        objectMapper = new ObjectMapper();
        handler = new AuthHttpHandler(jwtTokenProvider, authenticationService, metrics, objectMapper);
    }

    @Test
    @DisplayName("Should generate tokens when login is successful")
        void shouldGenerateTokensWhenLoginSuccessful() throws Exception {
        // Mock successful authentication
        Instant now = Instant.now();
        Session session = Session.builder()
                .tenantId(TenantId.of(TEST_TENANT_ID))
                .sessionId(SessionId.of(UUID.randomUUID().toString()))
                .userId(UserId.of(TEST_USER_ID))
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofHours(8)))
                .lastAccessedAt(now)
                .ipAddress("127.0.0.1")
                .userAgent("test")
                .valid(true)
                .build();
        Token token = Token.builder()
                .tenantId(TenantId.of(TEST_TENANT_ID))
                .tokenId(TokenId.random())
                .tokenType(TokenType.ACCESS_TOKEN)
                .userId(UserId.of(TEST_USER_ID))
                .clientId(ClientId.of("default"))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .tokenValue(UUID.randomUUID().toString())
                .build();
        AuthResult authResult = AuthResult.success(session, token);

        when(authenticationService.authenticate(any(TenantId.class), anyString(), anyString()))
                .thenReturn(Promise.of(authResult));
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
        com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(response.getBody().getArray());
        assertThat(payload.get("accessToken").asText()).isEqualTo(TEST_TOKEN);
        assertThat(payload.get("refreshToken").asText()).isEqualTo(TEST_TOKEN);
        assertThat(payload.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(payload.get("expiresIn").asInt()).isEqualTo(3600);
        assertThat(payload.get("user").get("email").asText()).isEqualTo(TEST_EMAIL);

        verify(authenticationService, times(1))
                .authenticate(any(TenantId.class), eq(TEST_EMAIL), eq("password123"));
        verify(jwtTokenProvider, times(1))
                .generateToken(any(TenantId.class), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    @DisplayName("Should default tenant when login payload omits tenantId")
        void shouldDefaultTenantWhenLoginPayloadOmitsTenantId() throws Exception {
        // Mock successful authentication
        Instant now = Instant.now();
        Session session = Session.builder()
                .tenantId(TenantId.of("tenant-123"))
                .sessionId(SessionId.of(UUID.randomUUID().toString()))
                .userId(UserId.of(TEST_USER_ID))
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofHours(8)))
                .lastAccessedAt(now)
                .ipAddress("127.0.0.1")
                .userAgent("test")
                .valid(true)
                .build();
        Token token = Token.builder()
                .tenantId(TenantId.of("tenant-123"))
                .tokenId(TokenId.random())
                .tokenType(TokenType.ACCESS_TOKEN)
                .userId(UserId.of(TEST_USER_ID))
                .clientId(ClientId.of("default"))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .tokenValue(UUID.randomUUID().toString())
                .build();
        AuthResult authResult = AuthResult.success(session, token);

        when(authenticationService.authenticate(eq(TenantId.of("tenant-123")), anyString(), anyString()))
                .thenReturn(Promise.of(authResult));
        when(jwtTokenProvider.generateToken(eq(TenantId.of("tenant-123")), any(UserPrincipal.class), any(Duration.class)))
                .thenReturn(Promise.of(TEST_TOKEN));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/login")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "email", TEST_EMAIL,
                        "password", "password123"
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleLogin(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(authenticationService).authenticate(eq(TenantId.of("tenant-123")), eq(TEST_EMAIL), eq("password123"));
        verify(jwtTokenProvider).generateToken(eq(TenantId.of("tenant-123")), any(UserPrincipal.class), any(Duration.class));
    }

    @Test
    @DisplayName("Should return 500 when token generation fails")
        void shouldReturn500WhenTokenGenerationFails() throws Exception {
        // Mock successful authentication
        Instant now = Instant.now();
        Session session = Session.builder()
                .tenantId(TenantId.of(TEST_TENANT_ID))
                .sessionId(SessionId.of(UUID.randomUUID().toString()))
                .userId(UserId.of(TEST_USER_ID))
                .createdAt(now)
                .expiresAt(now.plus(Duration.ofHours(8)))
                .lastAccessedAt(now)
                .ipAddress("127.0.0.1")
                .userAgent("test")
                .valid(true)
                .build();
        Token token = Token.builder()
                .tenantId(TenantId.of(TEST_TENANT_ID))
                .tokenId(TokenId.random())
                .tokenType(TokenType.ACCESS_TOKEN)
                .userId(UserId.of(TEST_USER_ID))
                .clientId(ClientId.of("default"))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .tokenValue(UUID.randomUUID().toString())
                .build();
        AuthResult authResult = AuthResult.success(session, token);

        when(authenticationService.authenticate(any(TenantId.class), anyString(), anyString()))
                .thenReturn(Promise.of(authResult));
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
        @DisplayName("Should return 400 when login payload is missing credentials")
                void shouldReturn400WhenLoginPayloadMissingCredentials() throws Exception {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/login")
                                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                                                "email", TEST_EMAIL,
                                                "tenantId", TEST_TENANT_ID
                                )))
                                .build();

                HttpResponse response = runPromise(() -> handler.handleLogin(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("Should return 400 when login payload is invalid JSON")
                void shouldReturn400WhenLoginPayloadIsInvalidJson() {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/login")
                                .withBody("{not-json}".getBytes())
                                .build();

                HttpResponse response = runPromise(() -> handler.handleLogin(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("Should return 400 when login payload contains blank credentials")
                void shouldReturn400WhenLoginPayloadContainsBlankCredentials() throws Exception {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/login")
                                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                                                "email", "",
                                                "password", "   ",
                                                "tenantId", TEST_TENANT_ID
                                )))
                                .build();

                HttpResponse response = runPromise(() -> handler.handleLogin(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
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
                com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(response.getBody().getArray());
                assertThat(payload.get("valid").asBoolean()).isTrue();
                assertThat(payload.get("expiresAt").asText()).isEqualTo(expiresAt.toString());
                assertThat(payload.get("issuedAt").asText()).isEqualTo(now.toString());
    }

    @Test
    @DisplayName("Should validate token with default tenant when tenantId omitted")
        void shouldValidateTokenWithDefaultTenantWhenTenantIdOmitted() throws Exception {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(1));

        JwtClaims validClaims = JwtClaims.builder()
                .tokenId(UUID.randomUUID().toString())
                .subject(TEST_USER_ID)
                .issuer("auth-platform")
                .audience("api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .tenantId(TenantId.of("default-tenant"))
                .email(TEST_EMAIL)
                .name("Test User")
                .roles(Set.of("USER"))
                .permissions(Set.of("READ"))
                .build();

        when(jwtTokenProvider.validateToken(eq(TenantId.of("default-tenant")), eq(TEST_TOKEN)))
                .thenReturn(Promise.of(validClaims));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/validate")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "token", TEST_TOKEN
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleValidate(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(jwtTokenProvider).validateToken(eq(TenantId.of("default-tenant")), eq(TEST_TOKEN));
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
        @DisplayName("Should return 400 when validate payload is invalid JSON")
                void shouldReturn400WhenValidatePayloadIsInvalidJson() {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/validate")
                                .withBody("{not-json}".getBytes())
                                .build();

                HttpResponse response = runPromise(() -> handler.handleValidate(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("Should return 400 when validate payload is missing token")
                void shouldReturn400WhenValidatePayloadMissingToken() throws Exception {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/validate")
                                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                                                "tenantId", TEST_TENANT_ID
                                )))
                                .build();

                HttpResponse response = runPromise(() -> handler.handleValidate(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
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
    @DisplayName("Should use default tenant when refresh payload omits tenantId")
        void shouldUseDefaultTenantWhenRefreshPayloadOmitsTenantId() throws Exception {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(1));
        String newToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.default-refresh";

        JwtClaims refreshClaims = JwtClaims.builder()
                .tokenId(UUID.randomUUID().toString())
                .subject(TEST_USER_ID)
                .issuer("auth-platform")
                .audience("api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .tenantId(TenantId.of(TEST_TENANT_ID))
                .email(TEST_EMAIL)
                .name("Test User")
                .roles(Set.of("USER"))
                .permissions(Set.of("READ"))
                .build();

        when(jwtTokenProvider.validateToken(eq(TenantId.of("default-tenant")), eq(TEST_TOKEN)))
                .thenReturn(Promise.of(refreshClaims));
        when(jwtTokenProvider.generateToken(eq(TenantId.of(TEST_TENANT_ID)), any(UserPrincipal.class), any(Duration.class)))
                .thenReturn(Promise.of(newToken));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/refresh")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "refreshToken", TEST_TOKEN
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleRefresh(request));

        assertThat(response.getCode()).isEqualTo(200);
        verify(jwtTokenProvider).validateToken(eq(TenantId.of("default-tenant")), eq(TEST_TOKEN));
        verify(jwtTokenProvider).generateToken(eq(TenantId.of(TEST_TENANT_ID)), any(UserPrincipal.class), any(Duration.class));
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
        @DisplayName("Should return 400 when refresh payload is missing refreshToken field")
                void shouldReturn400WhenRefreshPayloadUsesWrongFieldName() throws Exception {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/refresh")
                                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                                                "token", TEST_TOKEN,
                                                "tenantId", TEST_TENANT_ID
                                )))
                                .build();

                HttpResponse response = runPromise(() -> handler.handleRefresh(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("Should return 400 when refresh payload is missing token")
                void shouldReturn400WhenRefreshPayloadMissingToken() throws Exception {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/refresh")
                                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                                                "tenantId", TEST_TENANT_ID
                                )))
                                .build();

                HttpResponse response = runPromise(() -> handler.handleRefresh(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("Should return 400 when refresh payload is invalid JSON")
                void shouldReturn400WhenRefreshPayloadIsInvalidJson() {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/refresh")
                                .withBody("{not-json}".getBytes())
                                .build();

                HttpResponse response = runPromise(() -> handler.handleRefresh(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
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
    @DisplayName("Should revoke token with default tenant when tenantId omitted")
        void shouldRevokeTokenWithDefaultTenantWhenTenantIdOmitted() throws Exception {
        when(jwtTokenProvider.revokeToken(eq(TenantId.of("default-tenant")), eq(TEST_TOKEN)))
                .thenReturn(Promise.of(true));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/revoke")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "token", TEST_TOKEN
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleRevoke(request));

        assertThat(response.getCode()).isEqualTo(204);
        verify(jwtTokenProvider).revokeToken(eq(TenantId.of("default-tenant")), eq(TEST_TOKEN));
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
        @DisplayName("Should return 400 when revoke payload is invalid JSON")
                void shouldReturn400WhenRevokePayloadIsInvalidJson() {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/revoke")
                                .withBody("{not-json}".getBytes())
                                .build();

                HttpResponse response = runPromise(() -> handler.handleRevoke(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
        }

        @Test
        @DisplayName("Should return 400 when revoke payload is missing token")
                void shouldReturn400WhenRevokePayloadMissingToken() throws Exception {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/revoke")
                                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                                                "tenantId", TEST_TENANT_ID
                                )))
                                .build();

                HttpResponse response = runPromise(() -> handler.handleRevoke(request));

                assertThat(response.getCode()).isEqualTo(400);
                verifyNoInteractions(jwtTokenProvider);
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
        @DisplayName("Should include status and components in health response body")
                void shouldIncludeStatusAndComponentsInHealthResponseBody() throws Exception {
                HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/health")
                                .build();

                HttpResponse response = runPromise(() -> handler.handleHealth(request));
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> payload = objectMapper.readValue(response.getBody().getArray(), java.util.Map.class);

                assertThat(payload)
                                .containsEntry("status", "UP")
                                .containsKey("timestamp")
                                .containsKey("components");
        }

    @Test
    @DisplayName("Should not include authorization details in health response body")
        void shouldNotIncludeAuthorizationDetailsInHealthResponseBody() throws Exception {
        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/health")
                .build();

        HttpResponse response = runPromise(() -> handler.handleHealth(request));
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> payload = objectMapper.readValue(response.getBody().getArray(), java.util.Map.class);

        assertThat(payload)
                .doesNotContainKeys("token", "secret", "authorization");
    }

        @Test
        @DisplayName("Should reject null login requests")
        void shouldRejectNullLoginRequests() {
                assertThatThrownBy(() -> handler.handleLogin(null))
                                .isInstanceOf(NullPointerException.class)
                                .hasMessage("request cannot be null");
        }

    @Test
    @DisplayName("Should reject login when authentication fails with invalid credentials")
        void shouldRejectLoginWhenAuthenticationFailsWithInvalidCredentials() throws Exception {
        when(authenticationService.authenticate(any(TenantId.class), anyString(), anyString()))
                .thenReturn(Promise.of(AuthResult.failure("Invalid email or password")));

        HttpRequest request = HttpRequest.builder(HttpMethod.GET, "http://localhost:8080/auth/login")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "email", TEST_EMAIL,
                        "password", "wrongpassword",
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> handler.handleLogin(request));

        assertThat(response.getCode()).isEqualTo(401);
        com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(response.getBody().getArray());
        assertThat(payload.get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(payload.get("message").asText()).isEqualTo("Invalid email or password");

        verify(authenticationService, times(1))
                .authenticate(any(TenantId.class), eq(TEST_EMAIL), eq("wrongpassword"));
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("Should throw NullPointerException when JWT provider is null")
        void shouldThrowExceptionWhenJwtTokenProviderNull() throws Exception {
        assertThatThrownBy(() -> new AuthHttpHandler(null, authenticationService, metrics, objectMapper))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException when authenticationService is null")
        void shouldThrowExceptionWhenAuthenticationServiceNull() throws Exception {
        assertThatThrownBy(() -> new AuthHttpHandler(jwtTokenProvider, null, metrics, objectMapper))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException when metrics is null")
        void shouldThrowExceptionWhenMetricsNull() throws Exception {
        assertThatThrownBy(() -> new AuthHttpHandler(jwtTokenProvider, authenticationService, null, objectMapper))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should throw NullPointerException when ObjectMapper is null")
        void shouldThrowExceptionWhenObjectMapperNull() throws Exception {
        assertThatThrownBy(() -> new AuthHttpHandler(jwtTokenProvider, authenticationService, metrics, null))
                .isInstanceOf(NullPointerException.class);
    }
}
