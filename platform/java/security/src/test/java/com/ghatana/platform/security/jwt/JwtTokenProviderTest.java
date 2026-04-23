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
@DisplayName("JwtTokenProvider")
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
    @DisplayName("createToken")
    class CreateToken {

        @Test
        @DisplayName("should create a valid token with userId and roles")
        void shouldCreateValidToken() { // GH-90000
            String token = provider.createToken("user-1", List.of("ADMIN", "USER"), null); // GH-90000

            assertThat(token).isNotNull().isNotEmpty(); // GH-90000
            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should include userId as subject")
        void shouldIncludeUserId() { // GH-90000
            String token = provider.createToken("user-42", List.of("USER"), null);

            assertThat(provider.getUserIdFromToken(token)).isEqualTo(Optional.of("user-42"));
        }

        @Test
        @DisplayName("should include roles in token")
        void shouldIncludeRoles() { // GH-90000
            String token = provider.createToken("user-1", List.of("ADMIN", "EDITOR"), null); // GH-90000

            assertThat(provider.getRolesFromToken(token)).containsExactly("ADMIN", "EDITOR"); // GH-90000
        }

        @Test
        @DisplayName("should include additional claims")
        void shouldIncludeAdditionalClaims() { // GH-90000
            Map<String, Object> claims = Map.of("tenantId", "t-1", "orgId", "org-99"); // GH-90000
            String token = provider.createToken("user-1", List.of("USER"), claims);

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getUserIdFromToken(token)).isEqualTo(Optional.of("user-1"));
        }

        @Test
        @DisplayName("should handle empty roles list")
        void shouldHandleEmptyRoles() { // GH-90000
            String token = provider.createToken("user-1", List.of(), null); // GH-90000

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getRolesFromToken(token)).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("should validate a freshly created token")
        void shouldValidateFreshToken() { // GH-90000
            String token = provider.createToken("user-1", List.of("USER"), null);

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() { // GH-90000
            // Create a provider with 0ms validity (token expires immediately) // GH-90000
            JwtTokenProvider shortLived = new JwtTokenProvider(SECRET_KEY, 0); // GH-90000
            String token = shortLived.createToken("user-1", List.of("USER"), null);

            assertThat(shortLived.validateToken(token)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should reject token with tampered signature")
        void shouldRejectTamperedToken() { // GH-90000
            String token = provider.createToken("user-1", List.of("USER"), null);
            // Tamper with the last character of the signature
            String tampered = token.substring(0, token.length() - 2) + "XX"; // GH-90000

            assertThat(provider.validateToken(tampered)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should reject malformed token")
        void shouldRejectMalformedToken() { // GH-90000
            assertThat(provider.validateToken("not-a-jwt")).isFalse();
        }

        @Test
        @DisplayName("should reject empty token")
        void shouldRejectEmptyToken() { // GH-90000
            assertThat(provider.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("should reject token signed with different key")
        void shouldRejectDifferentKey() { // GH-90000
            JwtTokenProvider other = new JwtTokenProvider( // GH-90000
                    "AnotherSecretKeyThatIsAlso32BytesLng!", VALIDITY_MS);
            String token = other.createToken("user-1", List.of("USER"), null);

            assertThat(provider.validateToken(token)).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("getUserIdFromToken")
    class GetUserId {

        @Test
        @DisplayName("should extract userId from valid token")
        void shouldExtractUserId() { // GH-90000
            String token = provider.createToken("user-42", List.of("USER"), null);

            assertThat(provider.getUserIdFromToken(token)).isEqualTo(Optional.of("user-42"));
        }

        @Test
        @DisplayName("should return empty for invalid token")
        void shouldReturnNullForInvalid() { // GH-90000
            assertThat(provider.getUserIdFromToken("invalid-token")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getRolesFromToken")
    class GetRoles {

        @Test
        @DisplayName("should extract roles from valid token")
        void shouldExtractRoles() { // GH-90000
            String token = provider.createToken("user-1", List.of("ADMIN", "USER"), null); // GH-90000

            assertThat(provider.getRolesFromToken(token)).containsExactly("ADMIN", "USER"); // GH-90000
        }

        @Test
        @DisplayName("should return empty list for invalid token")
        void shouldReturnEmptyForInvalid() { // GH-90000
            assertThat(provider.getRolesFromToken("invalid-token")).isEmpty();
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject too-short secret key for HS256")
        void shouldRejectShortKey() { // GH-90000
            assertThatThrownBy(() -> new JwtTokenProvider("short", VALIDITY_MS)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }
}
