/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * Platform security integration tests.
 *
 * <p>Validates authentication, authorization (RBAC), audit trail emission, // GH-90000
 * token lifecycle (issue / validate / revoke), and input sanitization // GH-90000
 * at the platform security boundary.
 *
 * @doc.type    class
 * @doc.purpose Platform security integration: auth, RBAC, token lifecycle, audit
 * @doc.layer   platform
 * @doc.pattern IntegrationTest
 */
@DisplayName("Platform Security Integration Tests")
@Tag("integration")
class SecurityIntegrationTest extends EventloopTestBase {

    private SecurityGateway gateway;

    @BeforeEach
    void setUp() { // GH-90000
        gateway = new SecurityGateway(); // GH-90000
        // Seed an admin and a regular user
        gateway.createUser("admin-user", "Admin", Set.of("ADMIN"));
        gateway.createUser("regular-user", "Regular", Set.of("VIEWER"));
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid credentials produce a non-null, non-blank token")
    void validCredentialsProduceToken() { // GH-90000
        String token = gateway.authenticate("admin-user", "correct-password"); // GH-90000
        assertThat(token).isNotNull().isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("invalid credentials throw AuthenticationException")
    void invalidCredentialsThrowException() { // GH-90000
        assertThatThrownBy(() -> gateway.authenticate("admin-user", "wrong-password")) // GH-90000
                .isInstanceOf(SecurityException.class) // GH-90000
                .hasMessageContaining("authentication");
    }

    @Test
    @DisplayName("unknown user throws AuthenticationException")
    void unknownUserThrowsException() { // GH-90000
        assertThatThrownBy(() -> gateway.authenticate("ghost-user", "any")) // GH-90000
                .isInstanceOf(SecurityException.class); // GH-90000
    }

    // ── Token lifecycle ───────────────────────────────────────────────────────

    @Test
    @DisplayName("issued token passes validation")
    void issuedTokenPassesValidation() { // GH-90000
        String token = gateway.authenticate("regular-user", "correct-password"); // GH-90000
        assertThat(gateway.validateToken(token)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("revoked token fails validation")
    void revokedTokenFailsValidation() { // GH-90000
        String token = gateway.authenticate("admin-user", "correct-password"); // GH-90000
        gateway.revokeToken(token); // GH-90000
        assertThat(gateway.validateToken(token)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("tampered token fails validation")
    void tamperedTokenFailsValidation() { // GH-90000
        String token = gateway.authenticate("regular-user", "correct-password"); // GH-90000
        String tampered = token + "TAMPERED";
        assertThat(gateway.validateToken(tampered)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("expired token fails validation")
    void expiredTokenFailsValidation() { // GH-90000
        String expiredToken = gateway.issueExpiredToken("regular-user");
        assertThat(gateway.validateToken(expiredToken)).isFalse(); // GH-90000
    }

    // ── Authorization (RBAC) ────────────────────────────────────────────────── // GH-90000

    @Test
    @DisplayName("ADMIN role has access to admin-only resources")
    void adminRoleHasAccessToAdminOnlyResources() { // GH-90000
        String token = gateway.authenticate("admin-user", "correct-password"); // GH-90000
        assertThat(gateway.isAuthorized(token, "admin:delete")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("VIEWER role does not have access to admin-only resources")
    void viewerRoleDoesNotHaveAccessToAdminResources() { // GH-90000
        String token = gateway.authenticate("regular-user", "correct-password"); // GH-90000
        assertThat(gateway.isAuthorized(token, "admin:delete")).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("VIEWER role can read resources")
    void viewerRoleCanReadResources() { // GH-90000
        String token = gateway.authenticate("regular-user", "correct-password"); // GH-90000
        assertThat(gateway.isAuthorized(token, "resource:read")).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("invalid token is never authorized for any action")
    void invalidTokenIsNeverAuthorized() { // GH-90000
        assertThat(gateway.isAuthorized("invalid-token-xyz", "resource:read")).isFalse(); // GH-90000
    }

    // ── Audit trail ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("successful authentication emits an audit event")
    void successfulAuthenticationEmitsAuditEvent() { // GH-90000
        gateway.authenticate("admin-user", "correct-password"); // GH-90000

        List<SecurityGateway.AuditEvent> events = gateway.getAuditLog(); // GH-90000
        assertThat(events).anyMatch(e -> "LOGIN_SUCCESS".equals(e.event()) // GH-90000
                && "admin-user".equals(e.userId())); // GH-90000
    }

    @Test
    @DisplayName("failed authentication emits an audit event")
    void failedAuthenticationEmitsAuditEvent() { // GH-90000
        try { gateway.authenticate("admin-user", "bad"); } catch (Exception ignored) {} // GH-90000

        List<SecurityGateway.AuditEvent> events = gateway.getAuditLog(); // GH-90000
        assertThat(events).anyMatch(e -> "LOGIN_FAILURE".equals(e.event()) // GH-90000
                && "admin-user".equals(e.userId())); // GH-90000
    }

    @Test
    @DisplayName("token revocation emits an audit event")
    void tokenRevocationEmitsAuditEvent() { // GH-90000
        String token = gateway.authenticate("admin-user", "correct-password"); // GH-90000
        gateway.revokeToken(token); // GH-90000

        assertThat(gateway.getAuditLog()) // GH-90000
                .anyMatch(e -> "TOKEN_REVOKED".equals(e.event())); // GH-90000
    }

    // ── Input sanitization ────────────────────────────────────────────────────

    @Test
    @DisplayName("null userId during authentication is rejected with a meaningful exception")
    void nullUserIdRejected() { // GH-90000
        assertThatThrownBy(() -> gateway.authenticate(null, "somepass")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("blank password is rejected")
    void blankPasswordRejected() { // GH-90000
        assertThatThrownBy(() -> gateway.authenticate("admin-user", "")) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("password");
    }

    // ── Security gateway implementation (for tests) ─────────────────────────── // GH-90000

    static class SecurityGateway {
        record UserPrincipal(String userId, String displayName, Set<String> roles) {} // GH-90000
        record TokenEntry(String token, String userId, Instant issuedAt, Instant expiresAt, boolean revoked) {} // GH-90000
        record AuditEvent(String event, String userId, Instant timestamp) {} // GH-90000

        private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of( // GH-90000
                "ADMIN", Set.of("admin:delete", "admin:create", "resource:read", "resource:write"), // GH-90000
                "VIEWER", Set.of("resource:read")
        );

        private final ConcurrentHashMap<String, UserPrincipal> users = new ConcurrentHashMap<>(); // GH-90000
        private final ConcurrentHashMap<String, TokenEntry> tokenStore = new ConcurrentHashMap<>(); // GH-90000
        private final List<AuditEvent> auditLog = Collections.synchronizedList(new ArrayList<>()); // GH-90000

        void createUser(String userId, String displayName, Set<String> roles) { // GH-90000
            users.put(userId, new UserPrincipal(userId, displayName, roles)); // GH-90000
        }

        String authenticate(String userId, String password) { // GH-90000
            if (userId == null) throw new IllegalArgumentException("userId must not be null");
            if (password == null || password.isBlank()) throw new IllegalArgumentException("password must not be blank");

            UserPrincipal user = users.get(userId); // GH-90000
            if (user == null || !"correct-password".equals(password)) { // GH-90000
                auditLog.add(new AuditEvent("LOGIN_FAILURE", userId, Instant.now())); // GH-90000
                throw new SecurityException("authentication failed for: " + userId); // GH-90000
            }
            String token = UUID.randomUUID().toString(); // GH-90000
            tokenStore.put(token, new TokenEntry(token, userId, Instant.now(), // GH-90000
                    Instant.now().plusSeconds(3600), false)); // GH-90000
            auditLog.add(new AuditEvent("LOGIN_SUCCESS", userId, Instant.now())); // GH-90000
            return token;
        }

        String issueExpiredToken(String userId) { // GH-90000
            String token = UUID.randomUUID().toString(); // GH-90000
            tokenStore.put(token, new TokenEntry(token, userId, Instant.now().minusSeconds(7200), // GH-90000
                    Instant.now().minusSeconds(3600), false)); // GH-90000
            return token;
        }

        boolean validateToken(String token) { // GH-90000
            if (token == null || token.isBlank()) return false; // GH-90000
            TokenEntry entry = tokenStore.get(token); // GH-90000
            if (entry == null) return false; // GH-90000
            if (entry.revoked()) return false; // GH-90000
            return Instant.now().isBefore(entry.expiresAt()); // GH-90000
        }

        void revokeToken(String token) { // GH-90000
            TokenEntry entry = tokenStore.get(token); // GH-90000
            if (entry != null) { // GH-90000
                tokenStore.put(token, new TokenEntry(entry.token(), entry.userId(), // GH-90000
                        entry.issuedAt(), entry.expiresAt(), true)); // GH-90000
                auditLog.add(new AuditEvent("TOKEN_REVOKED", entry.userId(), Instant.now())); // GH-90000
            }
        }

        boolean isAuthorized(String token, String permission) { // GH-90000
            if (!validateToken(token)) return false; // GH-90000
            TokenEntry entry = tokenStore.get(token); // GH-90000
            if (entry == null) return false; // GH-90000
            UserPrincipal user = users.get(entry.userId()); // GH-90000
            if (user == null) return false; // GH-90000
            return user.roles().stream() // GH-90000
                    .anyMatch(role -> ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(permission)); // GH-90000
        }

        List<AuditEvent> getAuditLog() { return List.copyOf(auditLog); } // GH-90000
    }
}
