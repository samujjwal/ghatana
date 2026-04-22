package com.ghatana.platform.security.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JwtTokenProvider}.
 *
 * @doc.type class
 * @doc.purpose JWT token creation, validation, and parsing tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("JwtTokenProvider [GH-90000]")
class JwtTokenProviderTest {

    // 256-bit key for HS256 (must be >= 256 bits) // GH-90000
    private static final String SECRET_KEY = "ThisIsASecretKeyThatIsAtLeast32BytesLong!";
    private static final long VALIDITY_MS = 3600_000; // 1 hour

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() { // GH-90000
        provider = new JwtTokenProvider(SECRET_KEY, VALIDITY_MS); // GH-90000
    }

    @Nested
    @DisplayName("createToken [GH-90000]")
    class CreateToken {

        @Test
        @DisplayName("should create a valid token with userId and roles [GH-90000]")
        void shouldCreateValidToken() { // GH-90000
            String token = provider.createToken("user-1", List.of("ADMIN", "USER"), null); // GH-90000

            assertThat(token).isNotNull().isNotEmpty(); // GH-90000
            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should include userId as subject [GH-90000]")
        void shouldIncludeUserId() { // GH-90000
            String token = provider.createToken("user-42", List.of("USER [GH-90000]"), null);

            assertThat(provider.getUserIdFromToken(token)).isEqualTo(Optional.of("user-42 [GH-90000]"));
        }

        @Test
        @DisplayName("should include roles in token [GH-90000]")
        void shouldIncludeRoles() { // GH-90000
            String token = provider.createToken("user-1", List.of("ADMIN", "EDITOR"), null); // GH-90000

            assertThat(provider.getRolesFromToken(token)).containsExactly("ADMIN", "EDITOR"); // GH-90000
        }

        @Test
        @DisplayName("should include additional claims [GH-90000]")
        void shouldIncludeAdditionalClaims() { // GH-90000
            Map<String, Object> claims = Map.of("tenantId", "t-1", "orgId", "org-99"); // GH-90000
            String token = provider.createToken("user-1", List.of("USER [GH-90000]"), claims);

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getUserIdFromToken(token)).isEqualTo(Optional.of("user-1 [GH-90000]"));
        }

        @Test
        @DisplayName("should handle empty roles list [GH-90000]")
        void shouldHandleEmptyRoles() { // GH-90000
            String token = provider.createToken("user-1", List.of(), null); // GH-90000

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getRolesFromToken(token)).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("validateToken [GH-90000]")
    class ValidateToken {

        @Test
        @DisplayName("should validate a freshly created token [GH-90000]")
        void shouldValidateFreshToken() { // GH-90000
            String token = provider.createToken("user-1", List.of("USER [GH-90000]"), null);

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should reject expired token [GH-90000]")
        void shouldRejectExpiredToken() { // GH-90000
            // Create a provider with 0ms validity (token expires immediately) // GH-90000
            JwtTokenProvider shortLived = new JwtTokenProvider(SECRET_KEY, 0); // GH-90000
            String token = shortLived.createToken("user-1", List.of("USER [GH-90000]"), null);

            assertThat(shortLived.validateToken(token)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should reject token with tampered signature [GH-90000]")
        void shouldRejectTamperedToken() { // GH-90000
            String token = provider.createToken("user-1", List.of("USER [GH-90000]"), null);
            // Tamper with the last character of the signature
            String tampered = token.substring(0, token.length() - 2) + "XX"; // GH-90000

            assertThat(provider.validateToken(tampered)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should reject malformed token [GH-90000]")
        void shouldRejectMalformedToken() { // GH-90000
            assertThat(provider.validateToken("not-a-jwt [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("should reject empty token [GH-90000]")
        void shouldRejectEmptyToken() { // GH-90000
            assertThat(provider.validateToken(" [GH-90000]")).isFalse();
        }

        @Test
        @DisplayName("should reject token signed with different key [GH-90000]")
        void shouldRejectDifferentKey() { // GH-90000
            JwtTokenProvider other = new JwtTokenProvider( // GH-90000
                    "AnotherSecretKeyThatIsAlso32BytesLng!", VALIDITY_MS);
            String token = other.createToken("user-1", List.of("USER [GH-90000]"), null);

            assertThat(provider.validateToken(token)).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("getUserIdFromToken [GH-90000]")
    class GetUserId {

        @Test
        @DisplayName("should extract userId from valid token [GH-90000]")
        void shouldExtractUserId() { // GH-90000
            String token = provider.createToken("user-42", List.of("USER [GH-90000]"), null);

            assertThat(provider.getUserIdFromToken(token)).isEqualTo(Optional.of("user-42 [GH-90000]"));
        }

        @Test
        @DisplayName("should return empty for invalid token [GH-90000]")
        void shouldReturnNullForInvalid() { // GH-90000
            assertThat(provider.getUserIdFromToken("invalid-token [GH-90000]")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getRolesFromToken [GH-90000]")
    class GetRoles {

        @Test
        @DisplayName("should extract roles from valid token [GH-90000]")
        void shouldExtractRoles() { // GH-90000
            String token = provider.createToken("user-1", List.of("ADMIN", "USER"), null); // GH-90000

            assertThat(provider.getRolesFromToken(token)).containsExactly("ADMIN", "USER"); // GH-90000
        }

        @Test
        @DisplayName("should return empty list for invalid token [GH-90000]")
        void shouldReturnEmptyForInvalid() { // GH-90000
            assertThat(provider.getRolesFromToken("invalid-token [GH-90000]")).isEmpty();
        }
    }

    @Nested
    @DisplayName("constructor [GH-90000]")
    class Constructor {

        @Test
        @DisplayName("should reject too-short secret key for HS256 [GH-90000]")
        void shouldRejectShortKey() { // GH-90000
            assertThatThrownBy(() -> new JwtTokenProvider("short", VALIDITY_MS)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }
}
