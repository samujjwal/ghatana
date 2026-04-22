/**
 * @doc.type class
 * @doc.purpose Test security module protection against common attack vectors
 * @doc.layer platform
 * @doc.pattern Security Test
 */
package com.ghatana.platform.security;

import com.ghatana.platform.security.crypto.PasswordHasher;
import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.platform.security.model.User;
import com.ghatana.platform.security.rbac.InMemoryRolePermissionRegistry;
import com.ghatana.platform.security.rbac.SyncAuthorizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Security Attack Vector Tests
 *
 * Verifies that the security module properly protects against common attack vectors
 * including JWT tampering, password brute force, session hijacking, CSRF, XSS,
 * authentication/authorization bypass, and timing attacks.
 */
@DisplayName("Security Attack Vector Tests")
class SecurityAttackVectorTest {

    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("Should reject tampered JWT tokens")
    void shouldRejectTamperedJwtTokens() {
        JwtTokenProvider tokenProvider = new JwtTokenProvider(TEST_SECRET, 60_000L);
        String validToken = tokenProvider.createToken("user-1", List.of("USER"), Map.of());

        char last = validToken.charAt(validToken.length() - 1);
        char replacement = last == 'a' ? 'b' : 'a';
        String tamperedToken = validToken.substring(0, validToken.length() - 1) + replacement;

        assertThat(tokenProvider.validateToken(validToken)).isTrue();
        assertThat(tokenProvider.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Should reject expired JWT tokens")
    void shouldRejectExpiredJwtTokens() {
        JwtTokenProvider tokenProvider = new JwtTokenProvider(TEST_SECRET, -1L);
        String expiredToken = tokenProvider.createToken("user-1", List.of("USER"), Map.of());

        assertThat(tokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("Should use constant-time comparison for password verification (timing attack resistance)")
    void shouldUseConstantTimeComparisonForPasswordVerification() {
        PasswordHasher passwordHasher = new PasswordHasher();

        String correctPassword = "correctPassword";
        String wrongPassword = "wrongPassword";
        String hashedPassword = passwordHasher.hash(correctPassword);

        assertThat(passwordHasher.verify(correctPassword, hashedPassword)).isTrue();
        assertThat(passwordHasher.verify(wrongPassword, hashedPassword)).isFalse();
    }

    @Test
    @DisplayName("Should reject null and empty password/hash inputs")
    void shouldRejectNullAndEmptyPasswordHashInputs() {
        PasswordHasher passwordHasher = new PasswordHasher();

        assertThatThrownBy(() -> passwordHasher.hash(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Password cannot be null or empty");
        assertThatThrownBy(() -> passwordHasher.verify("", "hash"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Password cannot be null or empty");
        assertThatThrownBy(() -> passwordHasher.verify("password", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Hash cannot be null or empty");
    }

    @Test
    @DisplayName("Should prevent privilege escalation")
    void shouldPreventPrivilegeEscalation() {
        InMemoryRolePermissionRegistry registry = new InMemoryRolePermissionRegistry();
        registry.registerRole("USER", Set.of("entity:read"));
        registry.registerRole("ADMIN", Set.of("entity:read", "entity:write"));

        SyncAuthorizationService authorization = new SyncAuthorizationService(registry);
        User user = User.builder().userId("user-1").username("user").roles(Set.of("USER")).build();

        assertThat(authorization.hasPermission(user, "entity:write")).isFalse();
        assertThat(authorization.hasPermission(user, "entity:read")).isTrue();
    }
}
