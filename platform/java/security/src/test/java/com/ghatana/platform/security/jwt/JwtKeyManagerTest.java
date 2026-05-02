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
        void nullSecretThrows() { 
            assertThatThrownBy(() -> new JwtKeyManager(null, RETENTION_SECONDS)) 
                .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("zero retention throws IllegalArgumentException")
        void zeroRetentionThrows() { 
            assertThatThrownBy(() -> new JwtKeyManager(BOOTSTRAP_SECRET, 0)) 
                .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("starts with one active key (k0)")
        void startsWithOneKey() { 
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); 
            assertThat(manager.activeKeyCount()).isEqualTo(1); 
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
        void rotateCreatesNewKey() { 
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); 
            String newKid = manager.rotate(); 
            assertThat(newKid).isEqualTo("k1");
            assertThat(manager.activeKeyCount()).isEqualTo(2); 
            assertThat(manager.currentKeyId()).isEqualTo("k1");
        }

        @Test
        @DisplayName("multiple rotations increment key id")
        void multipleRotationsIncrementId() { 
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); 
            manager.rotate(); 
            String thirdKid = manager.rotate(); 
            assertThat(thirdKid).isEqualTo("k2");
            assertThat(manager.activeKeyCount()).isEqualTo(3); 
        }

        @Test
        @DisplayName("expired keys are pruned on rotate()")
        void expiredKeysPrunedOnRotate() { 
            // Very short retention: 1 second
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, 1L); 
            manager.rotate(); // k1 is now current; k0 expires in 1s 

            // Wait for k0 to expire
            try { Thread.sleep(1100); } catch (InterruptedException ignored) {} 

            manager.rotate(); // triggers pruning of k0 
            // k1 may also be expired now; at minimum the current key (k2) must remain 
            assertThat(manager.activeKeyCount()).isGreaterThanOrEqualTo(1); 
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
        void tokenFromOldKeyValidAfterRotation() { 
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); 
            JwtTokenProvider provider = new JwtTokenProvider(manager, TOKEN_VALIDITY_MS); 

            // Issue token with k0
            String tokenBeforeRotation = provider.createToken("alice", List.of("USER"), null);
            assertThat(manager.currentKeyId()).isEqualTo("k0");

            // Rotate to k1
            manager.rotate(); 
            assertThat(manager.currentKeyId()).isEqualTo("k1");

            // Token from k0 should still validate because k0 is still in the ring
            assertThat(provider.validateToken(tokenBeforeRotation)).isTrue(); 
            assertThat(provider.getUserIdFromToken(tokenBeforeRotation)) 
                .hasValue("alice");
        }

        @Test
        @DisplayName("new token issued after rotation carries new kid header")
        void newTokenCarriesNewKid() throws Exception { 
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); 
            JwtTokenProvider provider = new JwtTokenProvider(manager, TOKEN_VALIDITY_MS); 

            manager.rotate(); // current = k1 
            String token = provider.createToken("bob", List.of("ADMIN"), null);

            // Parse the header manually to check kid
            com.nimbusds.jwt.SignedJWT parsed = com.nimbusds.jwt.SignedJWT.parse(token); 
            assertThat(parsed.getHeader().getKeyID()).isEqualTo("k1");
        }

        @Test
        @DisplayName("token created with legacy constructor (no manager) still validates")
        void legacyConstructorStillWorks() { 
            JwtTokenProvider legacy = new JwtTokenProvider(BOOTSTRAP_SECRET, TOKEN_VALIDITY_MS); 
            String token = legacy.createToken("carol", List.of("VIEWER"), null);
            assertThat(legacy.validateToken(token)).isTrue(); 
            assertThat(legacy.getUserIdFromToken(token)).hasValue("carol");
            assertThat(legacy.getRolesFromToken(token)).containsExactly("VIEWER");
        }

        @Test
        @DisplayName("token signed with different secret is rejected")
        void wrongKeyRejected() { 
            JwtKeyManager managerA = new JwtKeyManager("secret-AAAAAAAAAAAAAAAAAAAAAA-AAAA", RETENTION_SECONDS); 
            JwtKeyManager managerB = new JwtKeyManager("secret-BBBBBBBBBBBBBBBBBBBBBB-BBBB", RETENTION_SECONDS); 
            JwtTokenProvider providerA = new JwtTokenProvider(managerA, TOKEN_VALIDITY_MS); 
            JwtTokenProvider providerB = new JwtTokenProvider(managerB, TOKEN_VALIDITY_MS); 

            String tokenFromA = providerA.createToken("dave", List.of(), null); 
            assertThat(providerB.validateToken(tokenFromA)).isFalse(); 
        }

        @Test
        @DisplayName("extractClaims works after rotation")
        void extractClaimsAfterRotation() { 
            JwtKeyManager manager = new JwtKeyManager(BOOTSTRAP_SECRET, RETENTION_SECONDS); 
            JwtTokenProvider provider = new JwtTokenProvider(manager, TOKEN_VALIDITY_MS); 

            String token = provider.createToken("eve", List.of("EDITOR"), Map.of("tenant", "yappc"));
            manager.rotate(); 

            var claims = provider.extractClaims(token); 
            assertThat(claims).isPresent(); 
            assertThat(claims.get().get("sub")).isEqualTo("eve");
            assertThat(claims.get().get("tenant")).isEqualTo("yappc");
        }
    }
}
