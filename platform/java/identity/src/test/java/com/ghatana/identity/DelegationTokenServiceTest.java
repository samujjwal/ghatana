/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        service = new DefaultDelegationTokenService(); 
    }

    @Nested
    @DisplayName("delegate()")
    class DelegateTests {

        @Test
        @DisplayName("Issues delegation token with correct principal info")
        void issuesDelegationToken() { 
            DelegationToken token = runPromise(() -> 
                service.delegate("t1", "agent-a", "agent-b", 
                    Set.of("read", "query"), Duration.ofMinutes(30))); 

            assertThat(token).isNotNull(); 
            assertThat(token.delegator()).isEqualTo("agent-a");
            assertThat(token.delegatee()).isEqualTo("agent-b");
            assertThat(token.tenantId()).isEqualTo("t1");
            assertThat(token.scopes()).containsExactlyInAnyOrder("read", "query"); 
            assertThat(token.isExpired()).isFalse(); 
        }

        @Test
        @DisplayName("Principal chain includes both delegator and delegatee")
        void chainIncludesBothPrincipals() { 
            DelegationToken token = runPromise(() -> 
                service.delegate("t1", "agent-a", "agent-b", 
                    Set.of("read"), Duration.ofHours(1)));

            assertThat(token.chain()).containsExactly("agent-a", "agent-b"); 
        }

        @Test
        @DisplayName("Caps TTL at 8 hours")
        void capsTtlAtEightHours() { 
            DelegationToken token = runPromise(() -> 
                service.delegate("t1", "a", "b", Set.of("read"), Duration.ofDays(7)));

            long ttlHours = Duration.between(token.issuedAt(), token.expiresAt()).toHours(); 
            assertThat(ttlHours).isLessThanOrEqualTo(8); 
        }
    }

    @Nested
    @DisplayName("validate()")
    class ValidateTests {

        @Test
        @DisplayName("Valid token resolves to the original token")
        void validTokenResolves() { 
            DelegationToken issued = runPromise(() -> 
                service.delegate("t1", "a", "b", Set.of("read"), Duration.ofMinutes(10)));

            Optional<DelegationToken> found = runPromise(() -> service.validate(issued.tokenId())); 
            assertThat(found).isPresent(); 
            assertThat(found.get().tokenId()).isEqualTo(issued.tokenId()); 
        }

        @Test
        @DisplayName("Unknown tokenId returns empty")
        void unknownTokenReturnsEmpty() { 
            Optional<DelegationToken> result = runPromise(() -> service.validate("nonexistent"));
            assertThat(result).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("revoke()")
    class RevokeTests {

        @Test
        @DisplayName("Revoked token is no longer valid")
        void revokedTokenIsInvalid() { 
            DelegationToken token = runPromise(() -> 
                service.delegate("t1", "a", "b", Set.of("x"), Duration.ofMinutes(5)));

            runPromise(() -> service.revoke(token.tokenId()).map(v -> v)); 
            Optional<DelegationToken> found = runPromise(() -> service.validate(token.tokenId())); 
            assertThat(found).isEmpty(); 
        }
    }
}
