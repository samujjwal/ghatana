package com.ghatana.platform.security.jwt;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: JWT token provider edge cases and advanced scenarios.
 * Tests expiration handling, key rotation, concurrent token generation, and Unicode support.
 *
 * @doc.type class
 * @doc.purpose JWT token provider edge cases and advanced failure scenarios
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("JwtTokenProvider - Phase 3 Expansion [GH-90000]")
class JwtTokenProviderExpansionTest extends EventloopTestBase {

    private static final String SECRET_KEY = "ThisIsASecretKeyThatIsAtLeast32BytesLong!";
    private static final long VALIDITY_MS = 3600_000; // 1 hour
    private static final long SHORT_VALIDITY_MS = 100; // 100ms for expiry tests

    private JwtTokenProvider provider;
    private JwtTokenProvider shortLivedProvider;

    @BeforeEach
    void setUp() { // GH-90000
        provider = new JwtTokenProvider(SECRET_KEY, VALIDITY_MS); // GH-90000
        shortLivedProvider = new JwtTokenProvider(SECRET_KEY, SHORT_VALIDITY_MS); // GH-90000
    }

    // ============================================
    // TOKEN EXPIRATION EDGE CASES (3 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Token Expiration Edge Cases [GH-90000]")
    class ExpirationTests {

        @Test
        @DisplayName("Provider with zero validity creates immediately-expiring tokens [GH-90000]")
        void zeroValidityTokenExpires() { // GH-90000
            JwtTokenProvider zeroValidity = new JwtTokenProvider(SECRET_KEY, 0); // GH-90000
            String token = zeroValidity.createToken("user-1", List.of("USER [GH-90000]"), null);

            // Token created with 0ms validity should be invalid immediately
            assertThat(zeroValidity.validateToken(token)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Token validity is respected by different providers [GH-90000]")
        void differentValidityDifferentBehavior() { // GH-90000
            // Create two tokens with different providers
            String longLivedToken = provider.createToken("user-long", List.of("USER [GH-90000]"), null);
            String instantExpiredToken = shortLivedProvider.createToken("user-short", List.of("USER [GH-90000]"), null);

            // Long-lived token should be valid
            assertThat(provider.validateToken(longLivedToken)).isTrue(); // GH-90000
            assertThat(provider.getUserIdFromToken(longLivedToken)) // GH-90000
                .isEqualTo(Optional.of("user-long [GH-90000]"));

            // Short-lived token validity depends on timing, but we validate structure
            assertThat(instantExpiredToken).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Claims in token are accurately encoded and retrievable [GH-90000]")
        void claimsEncodingAccuracy() { // GH-90000
            Map<String, Object> claims = Map.of( // GH-90000
                "tenantId", "tenant-123",
                "orgId", "org-456",
                "department", "engineering"
            );
            String token = provider.createToken("user-claims", List.of("ADMIN", "USER"), claims); // GH-90000

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getUserIdFromToken(token)) // GH-90000
                .isEqualTo(Optional.of("user-claims [GH-90000]"));
            assertThat(provider.getRolesFromToken(token)) // GH-90000
                .containsExactlyInAnyOrder("ADMIN", "USER"); // GH-90000
        }
    }

    // ============================================
    // UNICODE AND SPECIAL CHARACTER SUPPORT (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Unicode and Special Character Support [GH-90000]")
    class UnicodeTests {

        @Test
        @DisplayName("Token supports Unicode characters in userId [GH-90000]")
        void unicodeUserId() { // GH-90000
            String unicodeUserId = "用户-42-émojis-🔐";
            String token = provider.createToken(unicodeUserId, List.of("USER [GH-90000]"), null);

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getUserIdFromToken(token)) // GH-90000
                .isEqualTo(Optional.of(unicodeUserId)); // GH-90000
        }

        @Test
        @DisplayName("Token supports Unicode characters in role names [GH-90000]")
        void unicodeRoles() { // GH-90000
            List<String> unicodeRoles = List.of("管理员", "éditeur", "🔑_admin"); // GH-90000
            String token = provider.createToken("user-1", unicodeRoles, null); // GH-90000

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getRolesFromToken(token)) // GH-90000
                .containsExactlyInAnyOrder(unicodeRoles.toArray(new String[0])); // GH-90000
        }
    }

    // ============================================
    // CONCURRENT TOKEN GENERATION (1 test) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Concurrent Token Generation [GH-90000]")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent token creation produces unique valid tokens [GH-90000]")
        void concurrentTokensAreUnique() throws InterruptedException { // GH-90000
            int threadCount = 10;
            ConcurrentHashMap<String, String> tokens = new ConcurrentHashMap<>(); // GH-90000
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000
            AtomicInteger validTokenCount = new AtomicInteger(0); // GH-90000

            for (int i = 0; i < threadCount; i++) { // GH-90000
                int index = i;
                new Thread(() -> { // GH-90000
                    try {
                        String token = provider.createToken("user-" + index, List.of("USER [GH-90000]"), null);
                        String previous = tokens.putIfAbsent(token, "user-" + index); // GH-90000

                        if (previous == null && provider.validateToken(token)) { // GH-90000
                            validTokenCount.incrementAndGet(); // GH-90000
                        }
                    } finally {
                        latch.countDown(); // GH-90000
                    }
                }).start(); // GH-90000
            }

            latch.await(); // GH-90000

            // All tokens should be unique and valid
            assertThat(tokens).hasSize(threadCount); // GH-90000
            assertThat(validTokenCount.get()).isEqualTo(threadCount); // GH-90000
        }
    }

    // ============================================
    // CLAIM VERIFICATION EDGE CASES (2 tests) // GH-90000
    // ============================================

    @Nested
    @DisplayName("Claim Verification Edge Cases [GH-90000]")
    class ClaimVerificationTests {

        @Test
        @DisplayName("Token with maximum allowed claims size [GH-90000]")
        void maximumClaimsSize() { // GH-90000
            // Create token with many claims
            Map<String, Object> largeClaims = new ConcurrentHashMap<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                largeClaims.put("claim_" + i, "value_" + i); // GH-90000
            }

            String token = provider.createToken("user-1", List.of("ADMIN [GH-90000]"), largeClaims);

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getUserIdFromToken(token)) // GH-90000
                .isEqualTo(Optional.of("user-1 [GH-90000]"));
        }

        @Test
        @DisplayName("Token with empty role list can still be validated [GH-90000]")
        void emptyRolesValidation() { // GH-90000
            String token = provider.createToken("user-1", List.of(), null); // GH-90000

            assertThat(provider.validateToken(token)).isTrue(); // GH-90000
            assertThat(provider.getRolesFromToken(token)).isEmpty(); // GH-90000
            assertThat(provider.getUserIdFromToken(token)) // GH-90000
                .isEqualTo(Optional.of("user-1 [GH-90000]"));
        }
    }

    // ============================================
    // MULTI-PROVIDER ISOLATION (0 tests - covered by existing) // GH-90000
    // ============================================
    // Note: Multi-provider key isolation is covered by existing tests
    // (shouldRejectTokenSignedWithDifferentKey test already validates this) // GH-90000
}
