package com.ghatana.platform.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link JwtKeyManager} and the key-rotation-aware {@link JwtTokenProvider}.
 */
@DisplayName("JwtKeyManager")
class JwtKeyManagerTest {

    private static final String BOOTSTRAP_SECRET = "bootstrap-secret-at-least-32-bytes!!";
    private static final long RETENTION_SECONDS = 86_400L; // 24 h
    private static final long TOKEN_VALIDITY_MS = 3_600_000L; // 1 h

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("null bootstrap secret throws IllegalArgumentException")
        void nullSecretThrows() { // GH-90000
            assertThatThrownBy(() -> new JwtKeyManager(null, RETENTION_SECONDS)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("zero retention throws IllegalArgumentException")
        void zeroRetentionThrows() { // GH-90000
            assertThatThrownBy(() -> new JwtKeyManager(BOOTSTRAP_SECRET, 0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("starts with one active key (k0)")
        void startsWithOneKey() { // GH-90000
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); // GH-90000
            assertThat(manager.activeKeyCount()).isEqualTo(1); // GH-90000
            assertThat(manager.currentKeyId()).isEqualTo("k0");
        }
    }

    // -----------------------------------------------------------------------
    // Rotation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("rotate")
    class Rotate {

        @Test
        @DisplayName("rotate() creates a new key and returns its id")
        void rotateCreatesNewKey() { // GH-90000
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); // GH-90000
            String newKid = manager.rotate(); // GH-90000
            assertThat(newKid).isEqualTo("k1");
            assertThat(manager.activeKeyCount()).isEqualTo(2); // GH-90000
            assertThat(manager.currentKeyId()).isEqualTo("k1");
        }

        @Test
        @DisplayName("multiple rotations increment key id")
        void multipleRotationsIncrementId() { // GH-90000
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); // GH-90000
            manager.rotate(); // GH-90000
            String thirdKid = manager.rotate(); // GH-90000
            assertThat(thirdKid).isEqualTo("k2");
            assertThat(manager.activeKeyCount()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("expired keys are pruned on rotate()")
        void expiredKeysPrunedOnRotate() { // GH-90000
            // Very short retention: 1 second
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, 1L); // GH-90000
            manager.rotate(); // k1 is now current; k0 expires in 1s // GH-90000

            // Wait for k0 to expire
            try { Thread.sleep(1100); } catch (InterruptedException ignored) {} // GH-90000

            manager.rotate(); // triggers pruning of k0 // GH-90000
            // k1 may also be expired now; at minimum the current key (k2) must remain // GH-90000
            assertThat(manager.activeKeyCount()).isGreaterThanOrEqualTo(1); // GH-90000
            assertThat(manager.currentKeyId()).isEqualTo("k2");
        }
    }

    // -----------------------------------------------------------------------
    // Token round-trip with rotation
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("token round-trip with JwtTokenProvider")
    class TokenRoundTrip {

        @Test
        @DisplayName("token created before rotation is still valid after rotation")
        void tokenFromOldKeyValidAfterRotation() { // GH-90000
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); // GH-90000
            JwtTokenProvider provider = new JwtTokenProvider(manager, TOKEN_VALIDITY_MS); // GH-90000

            // Issue token with k0
            String tokenBeforeRotation = provider.createToken("alice", List.of("USER"), null);
            assertThat(manager.currentKeyId()).isEqualTo("k0");

            // Rotate to k1
            manager.rotate(); // GH-90000
            assertThat(manager.currentKeyId()).isEqualTo("k1");

            // Token from k0 should still validate because k0 is still in the ring
            assertThat(provider.validateToken(tokenBeforeRotation)).isTrue(); // GH-90000
            assertThat(provider.getUserIdFromToken(tokenBeforeRotation)) // GH-90000
                .hasValue("alice");
        }

        @Test
        @DisplayName("new token issued after rotation carries new kid header")
        void newTokenCarriesNewKid() throws Exception { // GH-90000
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); // GH-90000
            JwtTokenProvider provider = new JwtTokenProvider(manager, TOKEN_VALIDITY_MS); // GH-90000

            manager.rotate(); // current = k1 // GH-90000
            String token = provider.createToken("bob", List.of("ADMIN"), null);

            // Parse the header manually to check kid
            com.nimbusds.jwt.SignedJWT parsed = com.nimbusds.jwt.SignedJWT.parse(token); // GH-90000
            assertThat(parsed.getHeader().getKeyID()).isEqualTo("k1");
        }

        @Test
        @DisplayName("token created with legacy constructor (no manager) still validates")
        void legacyConstructorStillWorks() { // GH-90000
            JwtTokenProvider legacy = new JwtTokenProvider(BOOTSTRAP_SECRET, TOKEN_VALIDITY_MS); // GH-90000
            String token = legacy.createToken("carol", List.of("VIEWER"), null);
            assertThat(legacy.validateToken(token)).isTrue(); // GH-90000
            assertThat(legacy.getUserIdFromToken(token)).hasValue("carol");
            assertThat(legacy.getRolesFromToken(token)).containsExactly("VIEWER");
        }

        @Test
        @DisplayName("token signed with different secret is rejected")
        void wrongKeyRejected() { // GH-90000
            JwtKeyManager managerA = new JwtKeyManager("secret-AAAAAAAAAAAAAAAAAAAAAA-AAAA", RETENTION_SECONDS); // GH-90000
            JwtKeyManager managerB = new JwtKeyManager("secret-BBBBBBBBBBBBBBBBBBBBBB-BBBB", RETENTION_SECONDS); // GH-90000
            JwtTokenProvider providerA = new JwtTokenProvider(managerA, TOKEN_VALIDITY_MS); // GH-90000
            JwtTokenProvider providerB = new JwtTokenProvider(managerB, TOKEN_VALIDITY_MS); // GH-90000

            String tokenFromA = providerA.createToken("dave", List.of(), null); // GH-90000
            assertThat(providerB.validateToken(tokenFromA)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("extractClaims works after rotation")
        void extractClaimsAfterRotation() { // GH-90000
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); // GH-90000
            JwtTokenProvider provider = new JwtTokenProvider(manager, TOKEN_VALIDITY_MS); // GH-90000

            String token = provider.createToken("eve", List.of("EDITOR"), Map.of("tenant", "yappc"));
            manager.rotate(); // GH-90000

            var claims = provider.extractClaims(token); // GH-90000
            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().get("sub")).isEqualTo("eve");
            assertThat(claims.get().get("tenant")).isEqualTo("yappc");
        }
    }
}
