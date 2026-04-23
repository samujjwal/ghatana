/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.identity;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.identity.CredentialToken;
import com.ghatana.identity.spi.IdentityResolver;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link IdentityResolutionService} and {@link AepLocalIdentityResolver}.
 */
@DisplayName("AEP Identity")
class AepIdentityTest extends EventloopTestBase {

    private AepLocalIdentityResolver localResolver;
    private IdentityResolutionService service;

    @BeforeEach
    void setUp() { // GH-90000
        localResolver = new AepLocalIdentityResolver(); // GH-90000
        service = IdentityResolutionService.withResolvers(List.of(localResolver)); // GH-90000
    }

    private static AgentIdentity identity(String tenantId, String agentId) { // GH-90000
        return new AgentIdentity( // GH-90000
            tenantId, agentId,
            "spiffe://ghatana/" + tenantId + "/" + agentId,
            Set.of("aep:execute"),
            Instant.now()); // GH-90000
    }

    @Nested
    @DisplayName("AepLocalIdentityResolver")
    class ResolverTests {

        @Test
        @DisplayName("register and resolve returns the identity")
        void registerAndResolve() { // GH-90000
            AgentIdentity id = identity("t1", "agent1"); // GH-90000
            localResolver.register(id); // GH-90000
            Optional<AgentIdentity> result = runPromise(() -> // GH-90000
                localResolver.resolve("t1", "agent1")); // GH-90000
            assertThat(result).isPresent().hasValue(id); // GH-90000
        }

        @Test
        @DisplayName("resolve for unknown agent returns empty")
        void resolveUnknownReturnsEmpty() { // GH-90000
            Optional<AgentIdentity> result = runPromise(() -> // GH-90000
                localResolver.resolve("t1", "unknown")); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("supports returns true only for registered agents")
        void supportsOnlyRegistered() { // GH-90000
            localResolver.register(identity("t1", "agent1")); // GH-90000
            assertThat(localResolver.supports("t1", "agent1")).isTrue(); // GH-90000
            assertThat(localResolver.supports("t1", "agent2")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("deregister removes the identity")
        void deregisterRemovesIdentity() { // GH-90000
            localResolver.register(identity("t1", "agent1")); // GH-90000
            localResolver.deregister("t1", "agent1"); // GH-90000
            assertThat(localResolver.size()).isZero(); // GH-90000
        }

        @Test
        @DisplayName("tenants are isolated")
        void tenantsAreIsolated() { // GH-90000
            localResolver.register(identity("tenantA", "agent1")); // GH-90000
            Optional<AgentIdentity> result = runPromise(() -> // GH-90000
                localResolver.resolve("tenantB", "agent1")); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("IdentityResolutionService")
    class ServiceTests {

        @Test
        @DisplayName("resolveIdentity returns empty when no resolver knows the agent")
        void resolveUnknownReturnsEmpty() { // GH-90000
            Optional<AgentIdentity> result = runPromise(() -> // GH-90000
                service.resolveIdentity("t1", "ghost-agent")); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("resolveIdentity delegates to local resolver")
        void resolveDelegatesToLocalResolver() { // GH-90000
            AgentIdentity id = identity("t1", "agent1"); // GH-90000
            localResolver.register(id); // GH-90000
            Optional<AgentIdentity> result = runPromise(() -> // GH-90000
                service.resolveIdentity("t1", "agent1")); // GH-90000
            assertThat(result).isPresent().hasValue(id); // GH-90000
        }

        @Test
        @DisplayName("issueCredential returns a non-null token with correct metadata")
        void issueCredentialReturnsToken() { // GH-90000
            localResolver.register(identity("t1", "agent1")); // GH-90000
            CredentialToken token = runPromise(() -> // GH-90000
                service.issueCredential("t1", "agent1")); // GH-90000
            assertThat(token.tokenId()).isNotNull(); // GH-90000
            assertThat(token.agentId()).isEqualTo("agent1");
            assertThat(token.tenantId()).isEqualTo("t1");
            assertThat(token.isExpired()).isFalse(); // GH-90000
            assertThat(runPromise(() -> service.isCredentialValid(token.tokenId()))).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("revoke then isCredentialValid returns false")
        void revokeInvalidatesToken() { // GH-90000
            localResolver.register(identity("t1", "agent1")); // GH-90000
            CredentialToken token = runPromise(() -> service.issueCredential("t1", "agent1")); // GH-90000
            runBlocking(() -> service.revoke(token.tokenId())); // GH-90000
            boolean valid = runPromise(() -> service.isCredentialValid(token.tokenId())); // GH-90000
            assertThat(valid).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("withResolvers chains until one resolver finds the identity")
        void withResolversChainsResolvers() { // GH-90000
            IdentityResolver miss = (tenantId, agentId) -> Promise.of(Optional.empty()); // GH-90000
            AepLocalIdentityResolver hit = new AepLocalIdentityResolver(); // GH-90000
            AgentIdentity id = identity("t1", "agent1"); // GH-90000
            hit.register(id); // GH-90000

            IdentityResolutionService chainedService = IdentityResolutionService.withResolvers( // GH-90000
                List.of(miss, hit)); // GH-90000

            Optional<AgentIdentity> result = runPromise(() -> // GH-90000
                chainedService.resolveIdentity("t1", "agent1")); // GH-90000

            assertThat(result).isPresent().hasValue(id); // GH-90000
        }
    }
}
