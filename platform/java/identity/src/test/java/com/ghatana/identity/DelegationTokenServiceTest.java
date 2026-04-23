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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultDelegationTokenService}.
 *
 * @doc.type class
 * @doc.purpose Tests for delegation token issuance, validation, chain tracking, and revocation
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DefaultDelegationTokenService")
class DelegationTokenServiceTest extends EventloopTestBase {

    private DefaultDelegationTokenService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new DefaultDelegationTokenService(); // GH-90000
    }

    @Nested
    @DisplayName("delegate()")
    class DelegateTests {

        @Test
        @DisplayName("Issues delegation token with correct principal info")
        void issuesDelegationToken() { // GH-90000
            DelegationToken token = runPromise(() -> // GH-90000
                service.delegate("t1", "agent-a", "agent-b", // GH-90000
                    Set.of("read", "query"), Duration.ofMinutes(30))); // GH-90000

            assertThat(token).isNotNull(); // GH-90000
            assertThat(token.delegator()).isEqualTo("agent-a");
            assertThat(token.delegatee()).isEqualTo("agent-b");
            assertThat(token.tenantId()).isEqualTo("t1");
            assertThat(token.scopes()).containsExactlyInAnyOrder("read", "query"); // GH-90000
            assertThat(token.isExpired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Principal chain includes both delegator and delegatee")
        void chainIncludesBothPrincipals() { // GH-90000
            DelegationToken token = runPromise(() -> // GH-90000
                service.delegate("t1", "agent-a", "agent-b", // GH-90000
                    Set.of("read"), Duration.ofHours(1)));

            assertThat(token.chain()).containsExactly("agent-a", "agent-b"); // GH-90000
        }

        @Test
        @DisplayName("Caps TTL at 8 hours")
        void capsTtlAtEightHours() { // GH-90000
            DelegationToken token = runPromise(() -> // GH-90000
                service.delegate("t1", "a", "b", Set.of("read"), Duration.ofDays(7)));

            long ttlHours = Duration.between(token.issuedAt(), token.expiresAt()).toHours(); // GH-90000
            assertThat(ttlHours).isLessThanOrEqualTo(8); // GH-90000
        }
    }

    @Nested
    @DisplayName("validate()")
    class ValidateTests {

        @Test
        @DisplayName("Valid token resolves to the original token")
        void validTokenResolves() { // GH-90000
            DelegationToken issued = runPromise(() -> // GH-90000
                service.delegate("t1", "a", "b", Set.of("read"), Duration.ofMinutes(10)));

            Optional<DelegationToken> found = runPromise(() -> service.validate(issued.tokenId())); // GH-90000
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().tokenId()).isEqualTo(issued.tokenId()); // GH-90000
        }

        @Test
        @DisplayName("Unknown tokenId returns empty")
        void unknownTokenReturnsEmpty() { // GH-90000
            Optional<DelegationToken> result = runPromise(() -> service.validate("nonexistent"));
            assertThat(result).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("revoke()")
    class RevokeTests {

        @Test
        @DisplayName("Revoked token is no longer valid")
        void revokedTokenIsInvalid() { // GH-90000
            DelegationToken token = runPromise(() -> // GH-90000
                service.delegate("t1", "a", "b", Set.of("x"), Duration.ofMinutes(5)));

            runPromise(() -> service.revoke(token.tokenId()).map(v -> v)); // GH-90000
            Optional<DelegationToken> found = runPromise(() -> service.validate(token.tokenId())); // GH-90000
            assertThat(found).isEmpty(); // GH-90000
        }
    }
}
