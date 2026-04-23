/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.identity;

import com.ghatana.identity.spi.InMemoryIdentityResolver;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultIdentityService}.
 *
 * @doc.type class
 * @doc.purpose Comprehensive tests for identity resolution and credential lifecycle
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("DefaultIdentityService")
class IdentityServiceTest extends EventloopTestBase {

    private InMemoryIdentityResolver resolver;
    private DefaultIdentityService service;

    @BeforeEach
    void setUp() { // GH-90000
        resolver = new InMemoryIdentityResolver(); // GH-90000
        service = new DefaultIdentityService(resolver); // GH-90000
    }

    @Nested
    @DisplayName("resolve()")
    class ResolveTests {

        @Test
        @DisplayName("Returns empty when agent not registered")
        void returnsEmptyForUnknownAgent() { // GH-90000
            Optional<AgentIdentity> result = runPromise(() -> service.resolve("t1", "unknown-agent")); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("Returns registered identity")
        void returnsRegisteredIdentity() { // GH-90000
            AgentIdentity identity = new AgentIdentity("t1", "agent-1", // GH-90000
                "spiffe://ghatana.io/t1/agent-1", Set.of("read", "write"), Instant.now()); // GH-90000
            resolver.register(identity); // GH-90000

            Optional<AgentIdentity> result = runPromise(() -> service.resolve("t1", "agent-1")); // GH-90000
            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().agentId()).isEqualTo("agent-1");
            assertThat(result.get().tenantId()).isEqualTo("t1");
        }

        @Test
        @DisplayName("Tenant isolation: agent registered in t1 not visible in t2")
        void tenantIsolation() { // GH-90000
            AgentIdentity identity = new AgentIdentity("t1", "agent-1", null, // GH-90000
                Set.of("read"), Instant.now());
            resolver.register(identity); // GH-90000

            Optional<AgentIdentity> result = runPromise(() -> service.resolve("t2", "agent-1")); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("AgentIdentity.hasScope returns correct result")
        void hasScopeCheck() { // GH-90000
            AgentIdentity identity = new AgentIdentity("t1", "a1", null, Set.of("read"), Instant.now());
            assertThat(identity.hasScope("read")).isTrue();
            assertThat(identity.hasScope("write")).isFalse();
        }
    }

    @Nested
    @DisplayName("issueCredential()")
    class IssueCredentialTests {

        @Test
        @DisplayName("Issues credential with correct fields")
        void issuesCredential() { // GH-90000
            CredentialToken token = runPromise(() -> // GH-90000
                service.issueCredential("t1", "agent-1", Duration.ofMinutes(10))); // GH-90000

            assertThat(token).isNotNull(); // GH-90000
            assertThat(token.agentId()).isEqualTo("agent-1");
            assertThat(token.tenantId()).isEqualTo("t1");
            assertThat(token.tokenId()).isNotBlank(); // GH-90000
            assertThat(token.isExpired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Caps TTL at 1 hour")
        void capsTtlAtOneHour() { // GH-90000
            CredentialToken token = runPromise(() -> // GH-90000
                service.issueCredential("t1", "agent-1", Duration.ofHours(24))); // GH-90000

            // Token should expire within 1 hour + a small skew
            long ttlSeconds = Duration.between(token.issuedAt(), token.expiresAt()).toSeconds(); // GH-90000
            assertThat(ttlSeconds).isLessThanOrEqualTo(3600 + 5); // GH-90000
        }

        @Test
        @DisplayName("Issued credential is valid immediately")
        void issuedCredentialIsValid() { // GH-90000
            CredentialToken token = runPromise(() -> // GH-90000
                service.issueCredential("t1", "agent-1", Duration.ofMinutes(5))); // GH-90000

            Boolean valid = runPromise(() -> service.isCredentialValid(token.tokenId())); // GH-90000
            assertThat(valid).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("revokeCredential()")
    class RevokeTests {

        @Test
        @DisplayName("Revoked token is no longer valid")
        void revokedTokenIsInvalid() { // GH-90000
            CredentialToken token = runPromise(() -> // GH-90000
                service.issueCredential("t1", "agent-1", Duration.ofMinutes(5))); // GH-90000

            runPromise(() -> service.revokeCredential(token.tokenId()).map(v -> v)); // GH-90000
            Boolean valid = runPromise(() -> service.isCredentialValid(token.tokenId())); // GH-90000
            assertThat(valid).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Revoking unknown token is a no-op")
        void revokingUnknownTokenIsNoOp() { // GH-90000
            // Must not throw
            runPromise(() -> service.revokeCredential("nonexistent").map(v -> v));
        }
    }

    @Nested
    @DisplayName("isCredentialValid()")
    class ValidityTests {

        @Test
        @DisplayName("Unknown token is invalid")
        void unknownTokenIsInvalid() { // GH-90000
            Boolean valid = runPromise(() -> service.isCredentialValid("made-up-id"));
            assertThat(valid).isFalse(); // GH-90000
        }
    }
}
