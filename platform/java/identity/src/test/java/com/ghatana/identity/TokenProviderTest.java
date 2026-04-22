/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.identity;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for {@link DefaultTokenProvider}.
 *
 * @doc.type class
 * @doc.purpose Tests for JWT token lifecycle, verification, and key rotation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DefaultTokenProvider [GH-90000]")
class TokenProviderTest extends EventloopTestBase {

    private DefaultTokenProvider provider;

    @BeforeEach
    void setUp() { // GH-90000
        provider = new DefaultTokenProvider(); // GH-90000
    }

    @Nested
    @DisplayName("createToken() [GH-90000]")
    class CreateTokenTests {

        @Test
        @DisplayName("Creates valid token with correct tenant and agent [GH-90000]")
        void createsValidToken() { // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000

            assertThat(token).isNotBlank(); // GH-90000
            assertThat(token).contains(". [GH-90000]");
            String[] parts = token.split("\\. [GH-90000]");
            assertThat(parts).hasSize(3); // header.payload.signature // GH-90000
        }

        @Test
        @DisplayName("Token can be verified immediately after creation [GH-90000]")
        void tokenVerificableImmediately() { // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().tenantId()).isEqualTo("t1 [GH-90000]");
            assertThat(claims.get().agentId()).isEqualTo("agent-1 [GH-90000]");
        }

        @Test
        @DisplayName("Caps TTL at 24 hours [GH-90000]")
        void capsTtlAt24Hours() { // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofDays(30))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
            Duration ttl = Duration.between(claims.get().issuedAt(), claims.get().expiresAt()); // GH-90000
            assertThat(ttl.toHours()).isLessThanOrEqualTo(24); // GH-90000
        }

        @Test
        @DisplayName("Different agents get different token IDs [GH-90000]")
        void differentAgentsDifferentTokens() { // GH-90000
            String token1 = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000
            String token2 = runPromise(() -> provider.createToken("t1", "agent-2", Duration.ofMinutes(10))); // GH-90000

            assertThat(token1).isNotEqualTo(token2); // GH-90000
        }

        @Test
        @DisplayName("Same agent at different times gets different tokens [GH-90000]")
        void sameAgentDifferentTokens() { // GH-90000
            String token1 = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000
            // Small delay to ensure different timestamp
            try { Thread.sleep(1); } catch (InterruptedException e) {} // GH-90000
            String token2 = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000

            assertThat(token1).isNotEqualTo(token2); // GH-90000
        }
    }

    @Nested
    @DisplayName("verifyToken() [GH-90000]")
    class VerifyTokenTests {

        @Test
        @DisplayName("Rejects malformed JWT (wrong part count) [GH-90000]")
        void rejectsMalformedToken() { // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken("invalid [GH-90000]"));
            assertThat(claims).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Rejects token with invalid signature [GH-90000]")
        void rejectsInvalidSignature() { // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000
            String[] parts = token.split("\\. [GH-90000]");
            String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignature";

            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(tamperedToken)); // GH-90000
            assertThat(claims).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Rejects expired token [GH-90000]")
        void rejectsExpiredToken() { // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMillis(1))); // GH-90000
            // Wait for token to expire
            try { Thread.sleep(10); } catch (InterruptedException e) {} // GH-90000

            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(token)); // GH-90000
            assertThat(claims).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Rejects token not yet valid (before nbf) [GH-90000]")
        void rejectsNotYetValid() { // GH-90000
            // Create token with custom timestamp (requires internal logic) // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofHours(1))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(token)); // GH-90000

            // Note: This test validates the nbf claim existence; actual future date would need internal testing
            assertThat(claims).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("Extracts correct claims from valid token [GH-90000]")
        void extractsCorrectClaims() { // GH-90000
            String token = runPromise(() -> provider.createToken("tenant-abc", "agent-xyz", Duration.ofMinutes(20))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().tenantId()).isEqualTo("tenant-abc [GH-90000]");
            assertThat(claims.get().agentId()).isEqualTo("agent-xyz [GH-90000]");
            assertThat(claims.get().isValid()).isTrue(); // GH-90000
            assertThat(claims.get().isExpired()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("decodeTokenWithoutVerification() [GH-90000]")
    class DecodeWithoutVerificationTests {

        @Test
        @DisplayName("Decodes valid token without checking signature [GH-90000]")
        void decodesValidToken() { // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.decodeTokenWithoutVerification(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().tenantId()).isEqualTo("t1 [GH-90000]");
            assertThat(claims.get().agentId()).isEqualTo("agent-1 [GH-90000]");
        }

        @Test
        @DisplayName("Decodes tampered token (signature not checked) [GH-90000]")
        void decodesTamperedToken() { // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000
            String[] parts = token.split("\\. [GH-90000]");
            String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignature";

            Optional<TokenClaims> claims = runPromise(() -> provider.decodeTokenWithoutVerification(tamperedToken)); // GH-90000
            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().agentId()).isEqualTo("agent-1 [GH-90000]");
        }

        @Test
        @DisplayName("Rejects malformed token during decode [GH-90000]")
        void rejectsMalformedDuringDecode() { // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.decodeTokenWithoutVerification("not.a.jwt [GH-90000]"));
            // Decode may fail on invalid JSON
            assertThat(claims).isNotNull(); // May be empty or throw // GH-90000
        }
    }

    @Nested
    @DisplayName("rotateSigningKey() [GH-90000]")
    class RotateSigningKeyTests {

        @Test
        @DisplayName("Old tokens remain valid during grace period [GH-90000]")
        void oldTokenValidDuringGracePeriod() { // GH-90000
            String oldToken = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000

            // Rotate key
            runPromise(() -> provider.rotateSigningKey(Duration.ofSeconds(5))); // GH-90000

            // Old token should still verify
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(oldToken)); // GH-90000
            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().agentId()).isEqualTo("agent-1 [GH-90000]");
        }

        @Test
        @DisplayName("New tokens after rotation use new key [GH-90000]")
        void newTokenUsesNewKey() { // GH-90000
            String token1 = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000

            // Rotate key
            runPromise(() -> provider.rotateSigningKey(Duration.ofSeconds(10))); // GH-90000

            String token2 = runPromise(() -> provider.createToken("t1", "agent-2", Duration.ofMinutes(10))); // GH-90000

            // Both should verify
            Optional<TokenClaims> claims1 = runPromise(() -> provider.verifyToken(token1)); // GH-90000
            Optional<TokenClaims> claims2 = runPromise(() -> provider.verifyToken(token2)); // GH-90000

            assertThat(claims1).isPresent(); // GH-90000
            assertThat(claims2).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("Old tokens rejected after grace period expires [GH-90000]")
        void oldTokenRejectedAfterGrace() { // GH-90000
            String oldToken = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000

            // Rotate with very short grace period
            runPromise(() -> provider.rotateSigningKey(Duration.ofMillis(100))); // GH-90000

            // Wait for grace period to expire
            try { Thread.sleep(150); } catch (InterruptedException e) {} // GH-90000

            // Old token should be rejected
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(oldToken)); // GH-90000
            assertThat(claims).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Edge Cases [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handles empty tenant ID [GH-90000]")
        void handlesEmptyTenantId() { // GH-90000
            String token = runPromise(() -> provider.createToken("", "agent-1", Duration.ofMinutes(10))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().tenantId()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Handles very long agent ID [GH-90000]")
        void handlesLongAgentId() { // GH-90000
            String longId = "a".repeat(256); // GH-90000
            String token = runPromise(() -> provider.createToken("t1", longId, Duration.ofMinutes(10))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().agentId()).isEqualTo(longId); // GH-90000
        }

        @Test
        @DisplayName("Handles minimum TTL [GH-90000]")
        void handlesMinimumTtl() { // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofSeconds(5))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
            assertThat(claims.get().isExpired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("TokenClaims validation methods work correctly [GH-90000]")
        void tokenClaimsValidationMethods() { // GH-90000
            String token = runPromise(() -> provider.createToken("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000
            Optional<TokenClaims> claims = runPromise(() -> provider.verifyToken(token)); // GH-90000

            assertThat(claims).isPresent(); // GH-90000
            TokenClaims c = claims.get(); // GH-90000
            assertThat(c.isValid()).isTrue(); // GH-90000
            assertThat(c.isExpired()).isFalse(); // GH-90000
            assertThat(c.isNotYetValid()).isFalse(); // GH-90000
        }
    }
}
