package com.ghatana.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.auth.adapter.memory.InMemorySessionStore;
import com.ghatana.auth.adapter.memory.InMemoryTokenStore;
import com.ghatana.auth.adapter.memory.InMemoryUserRepository;
import com.ghatana.auth.core.port.JwtTokenProvider;
import com.ghatana.auth.core.port.JwtClaims;
import com.ghatana.auth.http.AuthHttpHandler;
import com.ghatana.auth.service.AuthenticationService;
import com.ghatana.auth.service.impl.AuthenticationServiceImpl;
import com.ghatana.auth.service.impl.JwtTokenProviderImpl;
import com.ghatana.platform.domain.auth.*;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.security.crypto.PasswordHasher;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Security Gateway login endpoint with real credential verification.
 *
 * Tests validate:
 * - Login endpoint calls AuthenticationService.authenticate()
 * - Invalid credentials are rejected with 401
 * - Valid credentials issue JWT tokens
 * - Real password hashing and verification works
 */
@DisplayName("Security Gateway Integration Tests")
class SecurityGatewayIntegrationTest extends EventloopTestBase {

    private AuthHttpHandler authHttpHandler;
    private JwtTokenProvider jwtTokenProvider;
    private AuthenticationService authenticationService;
    private InMemoryUserRepository userRepository;
    private PasswordHasher passwordHasher;
    private ObjectMapper objectMapper;

    private static final String TEST_TENANT_ID = "tenant-test";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "SecurePassword123!";
    private static final String WRONG_PASSWORD = "WrongPassword123!";

    @BeforeEach
    void setUp() {
        MetricsCollector metrics = new NoopMetricsCollector();
        userRepository = new InMemoryUserRepository();
        InMemorySessionStore sessionStore = new InMemorySessionStore();
        InMemoryTokenStore tokenStore = new InMemoryTokenStore();
        passwordHasher = new PasswordHasher();

        authenticationService = new AuthenticationServiceImpl(
                userRepository,
                sessionStore,
                tokenStore,
                passwordHasher,
                metrics
        );

        jwtTokenProvider = new JwtTokenProviderImpl(metrics);
        objectMapper = new ObjectMapper();

        authHttpHandler = new AuthHttpHandler(jwtTokenProvider, authenticationService, metrics, objectMapper);
    }

    @Test
    @DisplayName("Should reject login with invalid credentials")
    void testLoginEndpoint() throws Exception {
        // Create a user with known credentials
        TenantId tenantId = TenantId.of(TEST_TENANT_ID);
        UserId userId = UserId.of("user-123");
        
        String passwordHash = passwordHasher.hash(TEST_PASSWORD);
        User user = User.forInternalAuth()
                .tenantId(tenantId)
                .userId(userId)
                .email(TEST_EMAIL)
                .displayName("Test User")
                .username("testuser")
                .passwordHash(passwordHash)
                .active(true)
                .locked(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Save user to repository
        runPromise(() -> userRepository.save(user));

        // Attempt login with wrong password
        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost:8085/auth/login")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "email", TEST_EMAIL,
                        "password", WRONG_PASSWORD,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> authHttpHandler.handleLogin(request));

        assertThat(response.getCode()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");

        com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(response.getBody().getArray());
        assertThat(payload.get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(payload.get("message").asText()).isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("Should issue tokens on successful login with valid credentials")
    void shouldIssueTokensOnSuccessfulLogin() throws Exception {
        // Create a user with known credentials
        TenantId tenantId = TenantId.of(TEST_TENANT_ID);
        UserId userId = UserId.of("user-123");
        
        String passwordHash = passwordHasher.hash(TEST_PASSWORD);
        User user = User.forInternalAuth()
                .tenantId(tenantId)
                .userId(userId)
                .email(TEST_EMAIL)
                .displayName("Test User")
                .username("testuser")
                .passwordHash(passwordHash)
                .active(true)
                .locked(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Save user to repository
        runPromise(() -> userRepository.save(user));

        // Attempt login with correct password
        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost:8085/auth/login")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "email", TEST_EMAIL,
                        "password", TEST_PASSWORD,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> authHttpHandler.handleLogin(request));

        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");

        com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(response.getBody().getArray());
        assertThat(payload.has("accessToken")).isTrue();
        assertThat(payload.has("refreshToken")).isTrue();
        assertThat(payload.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(payload.get("expiresIn").asInt()).isEqualTo(3600);

        com.fasterxml.jackson.databind.JsonNode userNode = payload.get("user");
        assertThat(userNode.get("email").asText()).isEqualTo(TEST_EMAIL);
        assertThat(userNode.get("tenantId").asText()).isEqualTo(TEST_TENANT_ID);
    }

    @Test
    @DisplayName("Should reject login for non-existent user")
    void shouldRejectLoginForNonExistentUser() throws Exception {
        // Attempt login with email that doesn't exist
        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost:8085/auth/login")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "email", "nonexistent@example.com",
                        "password", TEST_PASSWORD,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> authHttpHandler.handleLogin(request));

        assertThat(response.getCode()).isEqualTo(401);

        com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(response.getBody().getArray());
        assertThat(payload.get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(payload.get("message").asText()).isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("Should reject login for locked account")
    void shouldRejectLoginForLockedAccount() throws Exception {
        // Create a locked user
        TenantId tenantId = TenantId.of(TEST_TENANT_ID);
        UserId userId = UserId.of("user-locked");
        
        String passwordHash = passwordHasher.hash(TEST_PASSWORD);
        User user = User.forInternalAuth()
                .tenantId(tenantId)
                .userId(userId)
                .email("locked@example.com")
                .displayName("Locked User")
                .username("lockeduser")
                .passwordHash(passwordHash)
                .active(true)
                .locked(true)  // Account is locked
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        runPromise(() -> userRepository.save(user));

        // Attempt login with correct password but locked account
        HttpRequest request = HttpRequest.builder(HttpMethod.POST, "http://localhost:8085/auth/login")
                .withBody(objectMapper.writeValueAsBytes(java.util.Map.of(
                        "email", "locked@example.com",
                        "password", TEST_PASSWORD,
                        "tenantId", TEST_TENANT_ID
                )))
                .build();

        HttpResponse response = runPromise(() -> authHttpHandler.handleLogin(request));

        assertThat(response.getCode()).isEqualTo(401);

        com.fasterxml.jackson.databind.JsonNode payload = objectMapper.readTree(response.getBody().getArray());
        assertThat(payload.get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(payload.get("message").asText()).isEqualTo("Account is locked");
    }
}
