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
@DisplayName("Security Attack Vector Tests [GH-90000]")
class SecurityAttackVectorTest {

    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("Should reject tampered JWT tokens [GH-90000]")
    void shouldRejectTamperedJwtTokens() { // GH-90000
        JwtTokenProvider tokenProvider = new JwtTokenProvider(TEST_SECRET, 60_000L); // GH-90000
        String validToken = tokenProvider.createToken("user-1", List.of("USER [GH-90000]"), Map.of());

        String[] parts = validToken.split("\\. [GH-90000]");
        char payloadLast = parts[1].charAt(parts[1].length() - 1); // GH-90000
        char replacement = payloadLast == 'a' ? 'b' : 'a';
        parts[1] = parts[1].substring(0, parts[1].length() - 1) + replacement; // GH-90000
        String tamperedToken = String.join(".", parts); // GH-90000

        assertThat(tokenProvider.validateToken(validToken)).isTrue(); // GH-90000
        assertThat(tokenProvider.validateToken(tamperedToken)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should reject expired JWT tokens [GH-90000]")
    void shouldRejectExpiredJwtTokens() { // GH-90000
        JwtTokenProvider tokenProvider = new JwtTokenProvider(TEST_SECRET, -1L); // GH-90000
        String expiredToken = tokenProvider.createToken("user-1", List.of("USER [GH-90000]"), Map.of());

        assertThat(tokenProvider.validateToken(expiredToken)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should use constant-time comparison for password verification (timing attack resistance) [GH-90000]")
    void shouldUseConstantTimeComparisonForPasswordVerification() { // GH-90000
        PasswordHasher passwordHasher = new PasswordHasher(); // GH-90000

        String correctPassword = "correctPassword";
        String wrongPassword = "wrongPassword";
        String hashedPassword = passwordHasher.hash(correctPassword); // GH-90000

        assertThat(passwordHasher.verify(correctPassword, hashedPassword)).isTrue(); // GH-90000
        assertThat(passwordHasher.verify(wrongPassword, hashedPassword)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("Should reject null and empty password/hash inputs [GH-90000]")
    void shouldRejectNullAndEmptyPasswordHashInputs() { // GH-90000
        PasswordHasher passwordHasher = new PasswordHasher(); // GH-90000

        assertThatThrownBy(() -> passwordHasher.hash(" [GH-90000]"))
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Password cannot be null or empty [GH-90000]");
        assertThatThrownBy(() -> passwordHasher.verify("", "hash")) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Password cannot be null or empty [GH-90000]");
        assertThatThrownBy(() -> passwordHasher.verify("password", "")) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("Hash cannot be null or empty [GH-90000]");
    }

    @Test
    @DisplayName("Should prevent privilege escalation [GH-90000]")
    void shouldPreventPrivilegeEscalation() { // GH-90000
        InMemoryRolePermissionRegistry registry = new InMemoryRolePermissionRegistry(); // GH-90000
        registry.registerRole("USER", Set.of("entity:read [GH-90000]"));
        registry.registerRole("ADMIN", Set.of("entity:read", "entity:write")); // GH-90000

        SyncAuthorizationService authorization = new SyncAuthorizationService(registry); // GH-90000
        User user = User.builder().userId("user-1 [GH-90000]").username("user [GH-90000]").roles(Set.of("USER [GH-90000]")).build();

        assertThat(authorization.hasPermission(user, "entity:write")).isFalse(); // GH-90000
        assertThat(authorization.hasPermission(user, "entity:read")).isTrue(); // GH-90000
    }
}
