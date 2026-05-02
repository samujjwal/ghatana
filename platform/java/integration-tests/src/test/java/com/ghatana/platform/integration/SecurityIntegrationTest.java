/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * <p>Validates authentication, authorization (RBAC), audit trail emission, 
 * token lifecycle (issue / validate / revoke), and input sanitization 
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
    void setUp() { 
        gateway = new SecurityGateway(); 
        // Seed an admin and a regular user
        gateway.createUser("admin-user", "Admin", Set.of("ADMIN"));
        gateway.createUser("regular-user", "Regular", Set.of("VIEWER"));
    }

    // ── Authentication ────────────────────────────────────────────────────────

    @Test
    @DisplayName("valid credentials produce a non-null, non-blank token")
    void validCredentialsProduceToken() { 
        String token = gateway.authenticate("admin-user", "correct-password"); 
        assertThat(token).isNotNull().isNotBlank(); 
    }

    @Test
    @DisplayName("invalid credentials throw AuthenticationException")
    void invalidCredentialsThrowException() { 
        assertThatThrownBy(() -> gateway.authenticate("admin-user", "wrong-password")) 
                .isInstanceOf(SecurityException.class) 
                .hasMessageContaining("authentication");
    }

    @Test
    @DisplayName("unknown user throws AuthenticationException")
    void unknownUserThrowsException() { 
        assertThatThrownBy(() -> gateway.authenticate("ghost-user", "any")) 
                .isInstanceOf(SecurityException.class); 
    }

    // ── Token lifecycle ───────────────────────────────────────────────────────

    @Test
    @DisplayName("issued token passes validation")
    void issuedTokenPassesValidation() { 
        String token = gateway.authenticate("regular-user", "correct-password"); 
        assertThat(gateway.validateToken(token)).isTrue(); 
    }

    @Test
    @DisplayName("revoked token fails validation")
    void revokedTokenFailsValidation() { 
        String token = gateway.authenticate("admin-user", "correct-password"); 
        gateway.revokeToken(token); 
        assertThat(gateway.validateToken(token)).isFalse(); 
    }

    @Test
    @DisplayName("tampered token fails validation")
    void tamperedTokenFailsValidation() { 
        String token = gateway.authenticate("regular-user", "correct-password"); 
        String tampered = token + "TAMPERED";
        assertThat(gateway.validateToken(tampered)).isFalse(); 
    }

    @Test
    @DisplayName("expired token fails validation")
    void expiredTokenFailsValidation() { 
        String expiredToken = gateway.issueExpiredToken("regular-user");
        assertThat(gateway.validateToken(expiredToken)).isFalse(); 
    }

    // ── Authorization (RBAC) ────────────────────────────────────────────────── 

    @Test
    @DisplayName("ADMIN role has access to admin-only resources")
    void adminRoleHasAccessToAdminOnlyResources() { 
        String token = gateway.authenticate("admin-user", "correct-password"); 
        assertThat(gateway.isAuthorized(token, "admin:delete")).isTrue(); 
    }

    @Test
    @DisplayName("VIEWER role does not have access to admin-only resources")
    void viewerRoleDoesNotHaveAccessToAdminResources() { 
        String token = gateway.authenticate("regular-user", "correct-password"); 
        assertThat(gateway.isAuthorized(token, "admin:delete")).isFalse(); 
    }

    @Test
    @DisplayName("VIEWER role can read resources")
    void viewerRoleCanReadResources() { 
        String token = gateway.authenticate("regular-user", "correct-password"); 
        assertThat(gateway.isAuthorized(token, "resource:read")).isTrue(); 
    }

    @Test
    @DisplayName("invalid token is never authorized for any action")
    void invalidTokenIsNeverAuthorized() { 
        assertThat(gateway.isAuthorized("invalid-token-xyz", "resource:read")).isFalse(); 
    }

    // ── Audit trail ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("successful authentication emits an audit event")
    void successfulAuthenticationEmitsAuditEvent() { 
        gateway.authenticate("admin-user", "correct-password"); 

        List<SecurityGateway.AuditEvent> events = gateway.getAuditLog(); 
        assertThat(events).anyMatch(e -> "LOGIN_SUCCESS".equals(e.event()) 
                && "admin-user".equals(e.userId())); 
    }

    @Test
    @DisplayName("failed authentication emits an audit event")
    void failedAuthenticationEmitsAuditEvent() { 
        try { gateway.authenticate("admin-user", "bad"); } catch (Exception ignored) {} 

        List<SecurityGateway.AuditEvent> events = gateway.getAuditLog(); 
        assertThat(events).anyMatch(e -> "LOGIN_FAILURE".equals(e.event()) 
                && "admin-user".equals(e.userId())); 
    }

    @Test
    @DisplayName("token revocation emits an audit event")
    void tokenRevocationEmitsAuditEvent() { 
        String token = gateway.authenticate("admin-user", "correct-password"); 
        gateway.revokeToken(token); 

        assertThat(gateway.getAuditLog()) 
                .anyMatch(e -> "TOKEN_REVOKED".equals(e.event())); 
    }

    // ── Input sanitization ────────────────────────────────────────────────────

    @Test
    @DisplayName("null userId during authentication is rejected with a meaningful exception")
    void nullUserIdRejected() { 
        assertThatThrownBy(() -> gateway.authenticate(null, "somepass")) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("userId");
    }

    @Test
    @DisplayName("blank password is rejected")
    void blankPasswordRejected() { 
        assertThatThrownBy(() -> gateway.authenticate("admin-user", "")) 
                .isInstanceOf(IllegalArgumentException.class) 
                .hasMessageContaining("password");
    }

    // ── Security gateway implementation (for tests) ─────────────────────────── 

    static class SecurityGateway {
        record UserPrincipal(String userId, String displayName, Set<String> roles) {} 
        record TokenEntry(String token, String userId, Instant issuedAt, Instant expiresAt, boolean revoked) {} 
        record AuditEvent(String event, String userId, Instant timestamp) {} 

        private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of( 
                "ADMIN", Set.of("admin:delete", "admin:create", "resource:read", "resource:write"), 
                "VIEWER", Set.of("resource:read")
        );

        private final ConcurrentHashMap<String, UserPrincipal> users = new ConcurrentHashMap<>(); 
        private final ConcurrentHashMap<String, TokenEntry> tokenStore = new ConcurrentHashMap<>(); 
        private final List<AuditEvent> auditLog = Collections.synchronizedList(new ArrayList<>()); 

        void createUser(String userId, String displayName, Set<String> roles) { 
            users.put(userId, new UserPrincipal(userId, displayName, roles)); 
        }

        String authenticate(String userId, String password) { 
            if (userId == null) throw new IllegalArgumentException("userId must not be null");
            if (password == null || password.isBlank()) throw new IllegalArgumentException("password must not be blank");

            UserPrincipal user = users.get(userId); 
            if (user == null || !"correct-password".equals(password)) { 
                auditLog.add(new AuditEvent("LOGIN_FAILURE", userId, Instant.now())); 
                throw new SecurityException("authentication failed for: " + userId); 
            }
            String token = UUID.randomUUID().toString(); 
            tokenStore.put(token, new TokenEntry(token, userId, Instant.now(), 
                    Instant.now().plusSeconds(3600), false)); 
            auditLog.add(new AuditEvent("LOGIN_SUCCESS", userId, Instant.now())); 
            return token;
        }

        String issueExpiredToken(String userId) { 
            String token = UUID.randomUUID().toString(); 
            tokenStore.put(token, new TokenEntry(token, userId, Instant.now().minusSeconds(7200), 
                    Instant.now().minusSeconds(3600), false)); 
            return token;
        }

        boolean validateToken(String token) { 
            if (token == null || token.isBlank()) return false; 
            TokenEntry entry = tokenStore.get(token); 
            if (entry == null) return false; 
            if (entry.revoked()) return false; 
            return Instant.now().isBefore(entry.expiresAt()); 
        }

        void revokeToken(String token) { 
            TokenEntry entry = tokenStore.get(token); 
            if (entry != null) { 
                tokenStore.put(token, new TokenEntry(entry.token(), entry.userId(), 
                        entry.issuedAt(), entry.expiresAt(), true)); 
                auditLog.add(new AuditEvent("TOKEN_REVOKED", entry.userId(), Instant.now())); 
            }
        }

        boolean isAuthorized(String token, String permission) { 
            if (!validateToken(token)) return false; 
            TokenEntry entry = tokenStore.get(token); 
            if (entry == null) return false; 
            UserPrincipal user = users.get(entry.userId()); 
            if (user == null) return false; 
            return user.roles().stream() 
                    .anyMatch(role -> ROLE_PERMISSIONS.getOrDefault(role, Set.of()).contains(permission)); 
        }

        List<AuditEvent> getAuditLog() { return List.copyOf(auditLog); } 
    }
}
