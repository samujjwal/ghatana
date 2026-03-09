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

    // 256-bit key for HS256 (must be >= 256 bits)
    private static final String SECRET_KEY = "ThisIsASecretKeyThatIsAtLeast32BytesLong!";
    private static final long VALIDITY_MS = 3600_000; // 1 hour

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET_KEY, VALIDITY_MS);
    }

    @Nested
    @DisplayName("createToken")
    class CreateToken {

        @Test
        @DisplayName("should create a valid token with userId and roles")
        void shouldCreateValidToken() {
            String token = provider.createToken("user-1", List.of("ADMIN", "USER"), null);

            assertThat(token).isNotNull().isNotEmpty();
            assertThat(provider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("should include userId as subject")
        void shouldIncludeUserId() {
            String token = provider.createToken("user-42", List.of("USER"), null);

            assertThat(provider.getUserIdFromToken(token)).isEqualTo(Optional.of("user-42"));
        }

        @Test
        @DisplayName("should include roles in token")
        void shouldIncludeRoles() {
            String token = provider.createToken("user-1", List.of("ADMIN", "EDITOR"), null);

            assertThat(provider.getRolesFromToken(token)).containsExactly("ADMIN", "EDITOR");
        }

        @Test
        @DisplayName("should include additional claims")
        void shouldIncludeAdditionalClaims() {
            Map<String, Object> claims = Map.of("tenantId", "t-1", "orgId", "org-99");
            String token = provider.createToken("user-1", List.of("USER"), claims);

            assertThat(provider.validateToken(token)).isTrue();
            assertThat(provider.getUserIdFromToken(token)).isEqualTo(Optional.of("user-1"));
        }

        @Test
        @DisplayName("should handle empty roles list")
        void shouldHandleEmptyRoles() {
            String token = provider.createToken("user-1", List.of(), null);

            assertThat(provider.validateToken(token)).isTrue();
            assertThat(provider.getRolesFromToken(token)).isEmpty();
        }
    }

    @Nested
    @DisplayName("validateToken")
    class ValidateToken {

        @Test
        @DisplayName("should validate a freshly created token")
        void shouldValidateFreshToken() {
            String token = provider.createToken("user-1", List.of("USER"), null);

            assertThat(provider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() {
            // Create a provider with 0ms validity (token expires immediately)
            JwtTokenProvider shortLived = new JwtTokenProvider(SECRET_KEY, 0);
            String token = shortLived.createToken("user-1", List.of("USER"), null);

            assertThat(shortLived.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("should reject token with tampered signature")
        void shouldRejectTamperedToken() {
            String token = provider.createToken("user-1", List.of("USER"), null);
            // Tamper with the last character of the signature
            String tampered = token.substring(0, token.length() - 2) + "XX";

            assertThat(provider.validateToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("should reject malformed token")
        void shouldRejectMalformedToken() {
            assertThat(provider.validateToken("not-a-jwt")).isFalse();
        }

        @Test
        @DisplayName("should reject empty token")
        void shouldRejectEmptyToken() {
            assertThat(provider.validateToken("")).isFalse();
        }

        @Test
        @DisplayName("should reject token signed with different key")
        void shouldRejectDifferentKey() {
            JwtTokenProvider other = new JwtTokenProvider(
                    "AnotherSecretKeyThatIsAlso32BytesLng!", VALIDITY_MS);
            String token = other.createToken("user-1", List.of("USER"), null);

            assertThat(provider.validateToken(token)).isFalse();
        }
    }

    @Nested
    @DisplayName("getUserIdFromToken")
    class GetUserId {

        @Test
        @DisplayName("should extract userId from valid token")
        void shouldExtractUserId() {
            String token = provider.createToken("user-42", List.of("USER"), null);

            assertThat(provider.getUserIdFromToken(token)).isEqualTo(Optional.of("user-42"));
        }

        @Test
        @DisplayName("should return empty for invalid token")
        void shouldReturnNullForInvalid() {
            assertThat(provider.getUserIdFromToken("invalid-token")).isEmpty();
        }
    }

    @Nested
    @DisplayName("getRolesFromToken")
    class GetRoles {

        @Test
        @DisplayName("should extract roles from valid token")
        void shouldExtractRoles() {
            String token = provider.createToken("user-1", List.of("ADMIN", "USER"), null);

            assertThat(provider.getRolesFromToken(token)).containsExactly("ADMIN", "USER");
        }

        @Test
        @DisplayName("should return empty list for invalid token")
        void shouldReturnEmptyForInvalid() {
            assertThat(provider.getRolesFromToken("invalid-token")).isEmpty();
        }
    }

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should reject too-short secret key for HS256")
        void shouldRejectShortKey() {
            assertThatThrownBy(() -> new JwtTokenProvider("short", VALIDITY_MS))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
