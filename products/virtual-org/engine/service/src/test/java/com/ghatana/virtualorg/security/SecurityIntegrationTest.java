package com.ghatana.virtualorg.security;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.security.impl.EncryptedMessageChannel;
import com.ghatana.virtualorg.security.impl.JWTAuthenticationService;
import com.ghatana.virtualorg.security.impl.RBACAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the security subsystem.
 *
 * Tests cover:
 * - Authentication flow (token generation and validation)
 * - Authorization with RBAC
 * - Secure messaging between agents
 * - End-to-end security scenarios
 */
@DisplayName("Security Integration Tests")
class SecurityIntegrationTest extends EventloopTestBase {

    private JWTAuthenticationService authService;
    private RBACAuthorizationService authzService;
    private EncryptedMessageChannel secureChannel;

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-security";
    private static final String SHARED_SECRET = "shared-secret-for-encryption-must-be-long-enough-256bits";

    @BeforeEach
    void setUp() {
        authService = new JWTAuthenticationService(eventloop(), TEST_SECRET, 3600);
        authzService = new RBACAuthorizationService(eventloop());
        secureChannel = new EncryptedMessageChannel(eventloop(), SHARED_SECRET);
    }

    @Test
    @DisplayName("Complete authentication and authorization flow")
    void testAuthFlow() {
        // Create principal
        Principal agent = new Principal(
            "agent-123",
            PrincipalType.AGENT,
            Set.of("senior-engineer"),
            Map.of("team", "backend")
        );

        // Generate token
        String token = runPromise(() ->
            authService.generateToken(agent, 3600)
        );

        assertNotNull(token);
        assertFalse(token.isBlank());

        // Authenticate with token
        AuthenticationResult result = runPromise(() ->
            authService.authenticate(token)
        );

        assertTrue(result.authenticated());
        assertNotNull(result.principal());
        assertEquals("agent-123", result.principal().id());
        assertEquals(PrincipalType.AGENT, result.principal().type());
        assertTrue(result.principal().hasRole("senior-engineer"));

        // Check authorization
        Boolean canWrite = runPromise(() ->
            authzService.isAuthorized(result.principal(), "code:write", "file-123")
        );

        assertTrue(canWrite);

        Boolean cannotDeleteInfra = runPromise(() ->
            authzService.isAuthorized(result.principal(), "infrastructure:delete", "server-1")
        );

        assertFalse(cannotDeleteInfra);
    }

    @Test
    @DisplayName("Token expiry and refresh")
    void testTokenExpiry() {
        Principal agent = new Principal(
            "agent-456",
            PrincipalType.AGENT,
            Set.of("engineer"),
            Map.of()
        );

        // Generate short-lived token (1 second)
        String token = runPromise(() ->
            authService.generateToken(agent, 1)
        );

        // Should be valid initially
        Boolean valid = runPromise(() ->
            authService.validateToken(token)
        );

        assertTrue(valid);

        // Refresh token
        String newToken = runPromise(() ->
            authService.refreshToken(token)
        );

        assertNotNull(newToken);
        assertNotEquals(token, newToken);

        // Old token should be revoked
        Boolean oldValid = runPromise(() ->
            authService.validateToken(token)
        );

        assertFalse(oldValid);
    }

    @Test
    @DisplayName("RBAC role hierarchy and escalation")
    void testRoleEscalation() {
        Principal juniorEngineer = new Principal(
            "agent-junior",
            PrincipalType.AGENT,
            Set.of("junior-engineer"),
            Map.of()
        );

        // Junior engineer can escalate to senior engineer
        Boolean canEscalate = runPromise(() ->
            authzService.canEscalate(juniorEngineer, "senior-engineer")
        );

        assertTrue(canEscalate);

        // But cannot escalate directly to architect
        Boolean cannotEscalate = runPromise(() ->
            authzService.canEscalate(juniorEngineer, "architect")
        );

        assertFalse(cannotEscalate);
    }

    @Test
    @DisplayName("Secure message exchange")
    void testSecureMessaging() {
        Principal sender = new Principal(
            "agent-sender",
            PrincipalType.AGENT,
            Set.of("senior-engineer"),
            Map.of()
        );

        Principal receiver = new Principal(
            "agent-receiver",
            PrincipalType.AGENT,
            Set.of("engineer"),
            Map.of()
        );

        String payload = "{\"task_id\": \"task-789\", \"action\": \"review\"}";

        // Send message
        String messageId = runPromise(() ->
            secureChannel.sendMessage(sender, receiver.id(), "task:assignment", payload)
        );

        assertNotNull(messageId);

        // Receive message (automatically verifies signature internally)
        SecureMessage received = runPromise(() ->
            secureChannel.receiveMessage(receiver, messageId)
        );

        // Verify decrypted message contents
        assertEquals(sender.id(), received.senderId());
        assertEquals(receiver.id(), received.receiverId());
        assertEquals("task:assignment", received.messageType());
        assertEquals(payload, received.payload());

        // No need to call verifyMessage again - receiveMessage already verified it
    }

    @Test
    @DisplayName("Unauthorized message access")
    void testUnauthorizedMessageAccess() {
        Principal sender = new Principal(
            "agent-sender",
            PrincipalType.AGENT,
            Set.of("senior-engineer"),
            Map.of()
        );

        Principal receiver = new Principal(
            "agent-receiver",
            PrincipalType.AGENT,
            Set.of("engineer"),
            Map.of()
        );

        Principal attacker = new Principal(
            "agent-attacker",
            PrincipalType.AGENT,
            Set.of("engineer"),
            Map.of()
        );

        // Send message
        String messageId = runPromise(() ->
            secureChannel.sendMessage(sender, receiver.id(), "task:assignment", "secret data")
        );

        // Attacker tries to receive message - the promise should be rejected with SecurityException
        try {
            runPromise(() ->
                secureChannel.receiveMessage(attacker, messageId)
            );
            fail("Expected SecurityException to be thrown");
        } catch (SecurityException e) {
            assertEquals("Unauthorized message access", e.getMessage());
        }
    }

    @Test
    @DisplayName("Permission wildcard matching")
    void testPermissionWildcards() {
        // Admin with wildcard permission
        Principal admin = new Principal(
            "admin-1",
            PrincipalType.USER,
            Set.of("admin"),
            Map.of()
        );

        // Should have all permissions
        Boolean canDoAnything = runPromise(() ->
            authzService.isAuthorized(admin, "anything:read", "any-resource")
        );

        assertTrue(canDoAnything);
    }

    @Test
    @DisplayName("Token revocation")
    void testTokenRevocation() {
        Principal agent = new Principal(
            "agent-revoke",
            PrincipalType.AGENT,
            Set.of("engineer"),
            Map.of()
        );

        String token = runPromise(() ->
            authService.generateToken(agent, 3600)
        );

        // Token should be valid
        Boolean valid = runPromise(() ->
            authService.validateToken(token)
        );

        assertTrue(valid);

        // Revoke token
        Boolean revoked = runPromise(() ->
            authService.revokeToken(token)
        );

        assertTrue(revoked);

        // Token should now be invalid
        AuthenticationResult result = runPromise(() ->
            authService.authenticate(token)
        );

        assertFalse(result.authenticated());
        assertEquals("Token has been revoked", result.failureReason());
    }
}
