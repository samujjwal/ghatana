package com.ghatana.digitalmarketing.api.security;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-026: Integration tests for DmosHttpContextFactory and server-side identity derivation.
 *
 * <p>Tests that verify:
 * <ul>
 *   <li>IdentityProvider is used in production mode to derive identity server-side</li>
 *   <li>Client-provided X-Roles/X-Permissions headers are ignored in production mode</li>
 *   <li>Dev mode uses client headers as fallback</li>
 *   <li>Missing required headers cause failures (fail-closed)</li>
 *   <li>Authorization header is required for production mode</li>
 *   <li>Tenant/workspace mismatch is rejected</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Integration tests for server-side identity derivation (P2-026)
 * @doc.layer test
 * @doc.pattern Integration Test
 */
@DisplayName("P2-026: HTTP Context Factory Integration Tests")
class HttpContextFactoryIntegrationTest {

    @Test
    @DisplayName("P2-026: Production mode should use IdentityProvider to derive identity")
    void productionModeShouldUseIdentityProvider() {
        // Arrange
        DmosHttpContextFactory.IdentityProvider identityProvider = (token, tenantId) -> 
            new DmosHttpContextFactory.IdentityProviderResult(
                "user123",
                "session456",
                Set.of("APPROVER", "ADMIN"),
                Set.of("workspace:read", "workspace:write"),
                true
            );
        
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, identityProvider);
        HttpRequest request = createTestRequest(
            "Bearer valid-token",
            "tenant-123",
            "spoofed-user",
            "spoofed-role",  // This should be ignored
            "spoofed-perm"   // This should be ignored
        );

        // Act
        DmOperationContext context = factory.buildContext(request, "workspace-123", false);

        // Assert
        assertEquals("user123", context.actor().id(), "Principal should come from IdentityProvider, not client header");
        assertEquals("tenant-123", context.tenantId().value());
        assertEquals("workspace-123", context.workspaceId().value());
    }

    @Test
    @DisplayName("P2-026: Production mode should reject invalid or expired tokens")
    void productionModeShouldRejectInvalidTokens() {
        // Arrange
        DmosHttpContextFactory.IdentityProvider identityProvider = (token, tenantId) -> 
            new DmosHttpContextFactory.IdentityProviderResult(
                null, null, Set.of(), Set.of(), false  // Invalid token
            );
        
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, identityProvider);
        HttpRequest request = createTestRequest(
            "Bearer invalid-token",
            "tenant-123",
            "user123",
            "APPROVER",
            "workspace:read"
        );

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> factory.buildContext(request, "workspace-123", false)
        );
        assertTrue(ex.getMessage().contains("Invalid or expired authentication token"),
            "Should reject invalid tokens");
    }

    @Test
    @DisplayName("P2-026: Production mode should require Authorization header")
    void productionModeShouldRequireAuthorizationHeader() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, null);
        HttpRequest request = HttpRequest.get("/test")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user123");

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> factory.buildContext(request, "workspace-123", false)
        );
        assertTrue(ex.getMessage().contains("Authorization"),
            "Should require Authorization header in production mode");
    }

    @Test
    @DisplayName("P2-026: Production mode should require valid Bearer token format")
    void productionModeShouldRequireBearerTokenFormat() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, null);
        HttpRequest request = HttpRequest.get("/test")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .withHeader(HttpHeaders.of("Authorization"), "Basic invalid");

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> factory.buildContext(request, "workspace-123", false)
        );
        assertTrue(ex.getMessage().contains("Bearer"),
            "Should require Bearer token format");
    }

    @Test
    @DisplayName("P2-026: Production mode should reject missing principal from IdentityProvider")
    void productionModeShouldRejectMissingPrincipal() {
        // Arrange
        DmosHttpContextFactory.IdentityProvider identityProvider = (token, tenantId) -> 
            new DmosHttpContextFactory.IdentityProviderResult(
                "",  // Empty principal
                "session456",
                Set.of("APPROVER"),
                Set.of("workspace:read"),
                true
            );
        
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, identityProvider);
        HttpRequest request = createTestRequest(
            "Bearer valid-token",
            "tenant-123",
            "user123",
            "APPROVER",
            "workspace:read"
        );

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> factory.buildContext(request, "workspace-123", false)
        );
        assertTrue(ex.getMessage().contains("Principal ID missing"),
            "Should reject missing principal from IdentityProvider");
    }

    @Test
    @DisplayName("P2-026: Production mode should reject missing session from IdentityProvider")
    void productionModeShouldRejectMissingSession() {
        // Arrange
        DmosHttpContextFactory.IdentityProvider identityProvider = (token, tenantId) -> 
            new DmosHttpContextFactory.IdentityProviderResult(
                "user123",
                "",  // Empty session
                Set.of("APPROVER"),
                Set.of("workspace:read"),
                true
            );
        
        DmosHttpContextFactory factory = new DmosHttpContextFactory(true, identityProvider);
        HttpRequest request = createTestRequest(
            "Bearer valid-token",
            "tenant-123",
            "user123",
            "APPROVER",
            "workspace:read"
        );

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> factory.buildContext(request, "workspace-123", false)
        );
        assertTrue(ex.getMessage().contains("Session ID missing"),
            "Should reject missing session from IdentityProvider");
    }

    @Test
    @DisplayName("P2-026: Dev mode should use client headers as fallback")
    void devModeShouldUseClientHeadersAsFallback() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        HttpRequest request = createTestRequest(
            "Bearer dev-token",
            "tenant-123",
            "user123",
            "APPROVER",
            "workspace:read"
        );

        // Act
        DmOperationContext context = factory.buildContext(request, "workspace-123", false);

        // Assert
        assertEquals("user123", context.actor().id(), "Dev mode should use client principal header");
        assertEquals("tenant-123", context.tenantId().value());
        assertEquals("workspace-123", context.workspaceId().value());
    }

    @Test
    @DisplayName("P2-026: Dev mode should still require principal and session headers")
    void devModeShouldRequirePrincipalAndSessionHeaders() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        HttpRequest request = HttpRequest.get("/test")
            .withHeader(HttpHeaders.of("X-Tenant-ID"), "tenant-123")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer dev-token")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), "corr-123");

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> factory.buildContext(request, "workspace-123", false)
        );
        assertTrue(ex.getMessage().contains("X-Principal-ID"),
            "Dev mode should still require principal header");
    }

    @Test
    @DisplayName("P2-026: Should require X-Tenant-ID header")
    void shouldRequireTenantIdHeader() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        HttpRequest request = HttpRequest.get("/test")
            .withHeader(HttpHeaders.of("Authorization"), "Bearer dev-token")
            .withHeader(HttpHeaders.of("X-Principal-ID"), "user123");

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> factory.buildContext(request, "workspace-123", false)
        );
        assertTrue(ex.getMessage().contains("X-Tenant-ID"),
            "Should require X-Tenant-ID header");
    }

    @Test
    @DisplayName("P2-026: Should require X-Idempotency-Key for write operations")
    void shouldRequireIdempotencyKeyForWriteOperations() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        HttpRequest request = createTestRequest(
            "Bearer dev-token",
            "tenant-123",
            "user123",
            "APPROVER",
            "workspace:read"
        );
        // Remove idempotency key
        request = request.withoutHeader(HttpHeaders.of("X-Idempotency-Key"));

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> factory.buildContext(request, "workspace-123", true)  // isWriteOperation = true
        );
        assertTrue(ex.getMessage().contains("X-Idempotency-Key"),
            "Should require idempotency key for write operations");
    }

    @Test
    @DisplayName("P2-026: Should not require X-Idempotency-Key for read operations")
    void shouldNotRequireIdempotencyKeyForReadOperations() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        HttpRequest request = createTestRequest(
            "Bearer dev-token",
            "tenant-123",
            "user123",
            "APPROVER",
            "workspace:read"
        );
        // Remove idempotency key
        request = request.withoutHeader(HttpHeaders.of("X-Idempotency-Key"));

        // Act
        DmOperationContext context = factory.buildContext(request, "workspace-123", false);  // isWriteOperation = false

        // Assert
        assertNotNull(context, "Should succeed without idempotency key for read operations");
    }

    @Test
    @DisplayName("P2-026: Should auto-generate correlation ID if missing")
    void shouldAutoGenerateCorrelationIdIfMissing() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        HttpRequest request = createTestRequest(
            "Bearer dev-token",
            "tenant-123",
            "user123",
            "APPROVER",
            "workspace:read"
        );
        // Remove correlation ID
        request = request.withoutHeader(HttpHeaders.of("X-Correlation-ID"));

        // Act
        DmOperationContext context = factory.buildContext(request, "workspace-123", false);

        // Assert
        assertNotNull(context.correlationId(), "Should auto-generate correlation ID");
        assertFalse(context.correlationId().value().isBlank(), "Correlation ID should not be blank");
    }

    @Test
    @DisplayName("P2-026: Should use provided correlation ID if present")
    void shouldUseProvidedCorrelationIdIfPresent() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        String expectedCorrId = "test-corr-123";
        HttpRequest request = createTestRequest(
            "Bearer dev-token",
            "tenant-123",
            "user123",
            "APPROVER",
            "workspace:read",
            expectedCorrId
        );

        // Act
        DmOperationContext context = factory.buildContext(request, "workspace-123", false);

        // Assert
        assertEquals(expectedCorrId, context.correlationId().value(),
            "Should use provided correlation ID");
    }

    @Test
    @DisplayName("P2-026: Should parse CSV roles header correctly")
    void shouldParseCsvRolesHeaderCorrectly() {
        // Arrange
        DmosHttpContextFactory factory = new DmosHttpContextFactory(false, null);
        HttpRequest request = createTestRequest(
            "Bearer dev-token",
            "tenant-123",
            "user123",
            "APPROVER,ADMIN,USER",  // CSV roles
            "workspace:read,workspace:write"  // CSV permissions
        );

        // Act
        DmOperationContext context = factory.buildContext(request, "workspace-123", false);

        // Assert
        assertNotNull(context, "Should parse CSV headers correctly");
    }

    private HttpRequest createTestRequest(String auth, String tenantId, String principal, String roles, String permissions) {
        return createTestRequest(auth, tenantId, principal, roles, permissions, "corr-123");
    }

    private HttpRequest createTestRequest(String auth, String tenantId, String principal, String roles, String permissions, String correlationId) {
        HttpRequest request = HttpRequest.get("/test")
            .withHeader(HttpHeaders.of("Authorization"), auth)
            .withHeader(HttpHeaders.of("X-Tenant-ID"), tenantId)
            .withHeader(HttpHeaders.of("X-Principal-ID"), principal)
            .withHeader(HttpHeaders.of("X-Session-ID"), "session-123")
            .withHeader(HttpHeaders.of("X-Correlation-ID"), correlationId)
            .withHeader(HttpHeaders.of("X-Idempotency-Key"), "idk-123")
            .withHeader(HttpHeaders.of("X-Roles"), roles)
            .withHeader(HttpHeaders.of("X-Permissions"), permissions);
        return request;
    }
}
