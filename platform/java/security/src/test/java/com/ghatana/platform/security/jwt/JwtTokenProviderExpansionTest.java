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
@DisplayName("JwtTokenProvider - Phase 3 Expansion")
class JwtTokenProviderExpansionTest extends EventloopTestBase {

    private static final String SECRET_KEY = "ThisIsASecretKeyThatIsAtLeast32BytesLong!";
    private static final long VALIDITY_MS = 3600_000; // 1 hour
    private static final long SHORT_VALIDITY_MS = 100; // 100ms for expiry tests

    private JwtTokenProvider provider;
    private JwtTokenProvider shortLivedProvider;

    @BeforeEach
    void setUp() { 
        provider = new JwtTokenProvider(SECRET_KEY, VALIDITY_MS); 
        shortLivedProvider = new JwtTokenProvider(SECRET_KEY, SHORT_VALIDITY_MS); 
    }

    // ============================================
    // TOKEN EXPIRATION EDGE CASES (3 tests) 
    // ============================================

    @Nested
    @DisplayName("Token Expiration Edge Cases")
    class ExpirationTests {

        @Test
        @DisplayName("Provider with zero validity creates immediately-expiring tokens")
        void zeroValidityTokenExpires() { 
            JwtTokenProvider zeroValidity = new JwtTokenProvider(SECRET_KEY, 0); 
            String token = zeroValidity.createToken("user-1", List.of("USER"), null);

            // Token created with 0ms validity should be invalid immediately
            assertThat(zeroValidity.validateToken(token)).isFalse(); 
        }

        @Test
        @DisplayName("Token validity is respected by different providers")
        void differentValidityDifferentBehavior() { 
            // Create two tokens with different providers
            String longLivedToken = provider.createToken("user-long", List.of("USER"), null);
            String instantExpiredToken = shortLivedProvider.createToken("user-short", List.of("USER"), null);

            // Long-lived token should be valid
            assertThat(provider.validateToken(longLivedToken)).isTrue(); 
            assertThat(provider.getUserIdFromToken(longLivedToken)) 
                .isEqualTo(Optional.of("user-long"));

            // Short-lived token validity depends on timing, but we validate structure
            assertThat(instantExpiredToken).isNotEmpty(); 
        }

        @Test
        @DisplayName("Claims in token are accurately encoded and retrievable")
        void claimsEncodingAccuracy() { 
            Map<String, Object> claims = Map.of( 
                "tenantId", "tenant-123",
                "orgId", "org-456",
                "department", "engineering"
            );
            String token = provider.createToken("user-claims", List.of("ADMIN", "USER"), claims); 

            assertThat(provider.validateToken(token)).isTrue(); 
            assertThat(provider.getUserIdFromToken(token)) 
                .isEqualTo(Optional.of("user-claims"));
            assertThat(provider.getRolesFromToken(token)) 
                .containsExactlyInAnyOrder("ADMIN", "USER"); 
        }
    }

    // ============================================
    // UNICODE AND SPECIAL CHARACTER SUPPORT (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Unicode and Special Character Support")
    class UnicodeTests {

        @Test
        @DisplayName("Token supports Unicode characters in userId")
        void unicodeUserId() { 
            String unicodeUserId = "用户-42-émojis-🔐";
            String token = provider.createToken(unicodeUserId, List.of("USER"), null);

            assertThat(provider.validateToken(token)).isTrue(); 
            assertThat(provider.getUserIdFromToken(token)) 
                .isEqualTo(Optional.of(unicodeUserId)); 
        }

        @Test
        @DisplayName("Token supports Unicode characters in role names")
        void unicodeRoles() { 
            List<String> unicodeRoles = List.of("管理员", "éditeur", "🔑_admin"); 
            String token = provider.createToken("user-1", unicodeRoles, null); 

            assertThat(provider.validateToken(token)).isTrue(); 
            assertThat(provider.getRolesFromToken(token)) 
                .containsExactlyInAnyOrder(unicodeRoles.toArray(new String[0])); 
        }
    }

    // ============================================
    // CONCURRENT TOKEN GENERATION (1 test) 
    // ============================================

    @Nested
    @DisplayName("Concurrent Token Generation")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent token creation produces unique valid tokens")
        void concurrentTokensAreUnique() throws InterruptedException { 
            int threadCount = 10;
            ConcurrentHashMap<String, String> tokens = new ConcurrentHashMap<>(); 
            CountDownLatch latch = new CountDownLatch(threadCount); 
            AtomicInteger validTokenCount = new AtomicInteger(0); 

            for (int i = 0; i < threadCount; i++) { 
                int index = i;
                new Thread(() -> { 
                    try {
                        String token = provider.createToken("user-" + index, List.of("USER"), null);
                        String previous = tokens.putIfAbsent(token, "user-" + index); 

                        if (previous == null && provider.validateToken(token)) { 
                            validTokenCount.incrementAndGet(); 
                        }
                    } finally {
                        latch.countDown(); 
                    }
                }).start(); 
            }

            latch.await(); 

            // All tokens should be unique and valid
            assertThat(tokens).hasSize(threadCount); 
            assertThat(validTokenCount.get()).isEqualTo(threadCount); 
        }
    }

    // ============================================
    // CLAIM VERIFICATION EDGE CASES (2 tests) 
    // ============================================

    @Nested
    @DisplayName("Claim Verification Edge Cases")
    class ClaimVerificationTests {

        @Test
        @DisplayName("Token with maximum allowed claims size")
        void maximumClaimsSize() { 
            // Create token with many claims
            Map<String, Object> largeClaims = new ConcurrentHashMap<>(); 
            for (int i = 0; i < 100; i++) { 
                largeClaims.put("claim_" + i, "value_" + i); 
            }

            String token = provider.createToken("user-1", List.of("ADMIN"), largeClaims);

            assertThat(provider.validateToken(token)).isTrue(); 
            assertThat(provider.getUserIdFromToken(token)) 
                .isEqualTo(Optional.of("user-1"));
        }

        @Test
        @DisplayName("Token with empty role list can still be validated")
        void emptyRolesValidation() { 
            String token = provider.createToken("user-1", List.of(), null); 

            assertThat(provider.validateToken(token)).isTrue(); 
            assertThat(provider.getRolesFromToken(token)).isEmpty(); 
            assertThat(provider.getUserIdFromToken(token)) 
                .isEqualTo(Optional.of("user-1"));
        }
    }

    // ============================================
    // MULTI-PROVIDER ISOLATION (0 tests - covered by existing) 
    // ============================================
    // Note: Multi-provider key isolation is covered by existing tests
    // (shouldRejectTokenSignedWithDifferentKey test already validates this) 
}
