/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    void setUp() { 
        resolver = new InMemoryIdentityResolver(); 
        service = new DefaultIdentityService(resolver); 
    }

    @Nested
    @DisplayName("resolve()")
    class ResolveTests {

        @Test
        @DisplayName("Returns empty when agent not registered")
        void returnsEmptyForUnknownAgent() { 
            Optional<AgentIdentity> result = runPromise(() -> service.resolve("t1", "unknown-agent")); 
            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("Returns registered identity")
        void returnsRegisteredIdentity() { 
            AgentIdentity identity = new AgentIdentity("t1", "agent-1", 
                "spiffe://ghatana.io/t1/agent-1", Set.of("read", "write"), Instant.now()); 
            resolver.register(identity); 

            Optional<AgentIdentity> result = runPromise(() -> service.resolve("t1", "agent-1")); 
            assertThat(result).isPresent(); 
            assertThat(result.get().agentId()).isEqualTo("agent-1");
            assertThat(result.get().tenantId()).isEqualTo("t1");
        }

        @Test
        @DisplayName("Tenant isolation: agent registered in t1 not visible in t2")
        void tenantIsolation() { 
            AgentIdentity identity = new AgentIdentity("t1", "agent-1", null, 
                Set.of("read"), Instant.now());
            resolver.register(identity); 

            Optional<AgentIdentity> result = runPromise(() -> service.resolve("t2", "agent-1")); 
            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("AgentIdentity.hasScope returns correct result")
        void hasScopeCheck() { 
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
        void issuesCredential() { 
            CredentialToken token = runPromise(() -> 
                service.issueCredential("t1", "agent-1", Duration.ofMinutes(10))); 

            assertThat(token).isNotNull(); 
            assertThat(token.agentId()).isEqualTo("agent-1");
            assertThat(token.tenantId()).isEqualTo("t1");
            assertThat(token.tokenId()).isNotBlank(); 
            assertThat(token.isExpired()).isFalse(); 
        }

        @Test
        @DisplayName("Caps TTL at 1 hour")
        void capsTtlAtOneHour() { 
            CredentialToken token = runPromise(() -> 
                service.issueCredential("t1", "agent-1", Duration.ofHours(24))); 

            // Token should expire within 1 hour + a small skew
            long ttlSeconds = Duration.between(token.issuedAt(), token.expiresAt()).toSeconds(); 
            assertThat(ttlSeconds).isLessThanOrEqualTo(3600 + 5); 
        }

        @Test
        @DisplayName("Issued credential is valid immediately")
        void issuedCredentialIsValid() { 
            CredentialToken token = runPromise(() -> 
                service.issueCredential("t1", "agent-1", Duration.ofMinutes(5))); 

            Boolean valid = runPromise(() -> service.isCredentialValid(token.tokenId())); 
            assertThat(valid).isTrue(); 
        }
    }

    @Nested
    @DisplayName("revokeCredential()")
    class RevokeTests {

        @Test
        @DisplayName("Revoked token is no longer valid")
        void revokedTokenIsInvalid() { 
            CredentialToken token = runPromise(() -> 
                service.issueCredential("t1", "agent-1", Duration.ofMinutes(5))); 

            runPromise(() -> service.revokeCredential(token.tokenId()).map(v -> v)); 
            Boolean valid = runPromise(() -> service.isCredentialValid(token.tokenId())); 
            assertThat(valid).isFalse(); 
        }

        @Test
        @DisplayName("Revoking unknown token is a no-op")
        void revokingUnknownTokenIsNoOp() { 
            // Must not throw
            runPromise(() -> service.revokeCredential("nonexistent").map(v -> v));
        }
    }

    @Nested
    @DisplayName("isCredentialValid()")
    class ValidityTests {

        @Test
        @DisplayName("Unknown token is invalid")
        void unknownTokenIsInvalid() { 
            Boolean valid = runPromise(() -> service.isCredentialValid("made-up-id"));
            assertThat(valid).isFalse(); 
        }
    }
}
