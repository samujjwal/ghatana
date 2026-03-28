/*
 * Copyright (c) 2026 Ghatana Inc.
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
    void setUp() {
        localResolver = new AepLocalIdentityResolver();
        service = IdentityResolutionService.withResolvers(List.of(localResolver));
    }

    private static AgentIdentity identity(String tenantId, String agentId) {
        return new AgentIdentity(
            tenantId, agentId,
            "spiffe://ghatana/" + tenantId + "/" + agentId,
            Set.of("aep:execute"),
            Instant.now());
    }

    @Nested
    @DisplayName("AepLocalIdentityResolver")
    class ResolverTests {

        @Test
        @DisplayName("register and resolve returns the identity")
        void registerAndResolve() {
            AgentIdentity id = identity("t1", "agent1");
            localResolver.register(id);
            Optional<AgentIdentity> result = runPromise(() ->
                localResolver.resolve("t1", "agent1"));
            assertThat(result).isPresent().hasValue(id);
        }

        @Test
        @DisplayName("resolve for unknown agent returns empty")
        void resolveUnknownReturnsEmpty() {
            Optional<AgentIdentity> result = runPromise(() ->
                localResolver.resolve("t1", "unknown"));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("supports returns true only for registered agents")
        void supportsOnlyRegistered() {
            localResolver.register(identity("t1", "agent1"));
            assertThat(localResolver.supports("t1", "agent1")).isTrue();
            assertThat(localResolver.supports("t1", "agent2")).isFalse();
        }

        @Test
        @DisplayName("deregister removes the identity")
        void deregisterRemovesIdentity() {
            localResolver.register(identity("t1", "agent1"));
            localResolver.deregister("t1", "agent1");
            assertThat(localResolver.size()).isZero();
        }

        @Test
        @DisplayName("tenants are isolated")
        void tenantsAreIsolated() {
            localResolver.register(identity("tenantA", "agent1"));
            Optional<AgentIdentity> result = runPromise(() ->
                localResolver.resolve("tenantB", "agent1"));
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("IdentityResolutionService")
    class ServiceTests {

        @Test
        @DisplayName("resolveIdentity returns empty when no resolver knows the agent")
        void resolveUnknownReturnsEmpty() {
            Optional<AgentIdentity> result = runPromise(() ->
                service.resolveIdentity("t1", "ghost-agent"));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("resolveIdentity delegates to local resolver")
        void resolveDelegatesToLocalResolver() {
            AgentIdentity id = identity("t1", "agent1");
            localResolver.register(id);
            Optional<AgentIdentity> result = runPromise(() ->
                service.resolveIdentity("t1", "agent1"));
            assertThat(result).isPresent().hasValue(id);
        }

        @Test
        @DisplayName("issueCredential returns a non-null token with correct metadata")
        void issueCredentialReturnsToken() {
            localResolver.register(identity("t1", "agent1"));
            CredentialToken token = runPromise(() ->
                service.issueCredential("t1", "agent1"));
            assertThat(token.tokenId()).isNotNull();
            assertThat(token.agentId()).isEqualTo("agent1");
            assertThat(token.tenantId()).isEqualTo("t1");
            assertThat(token.isExpired()).isFalse();
            assertThat(runPromise(() -> service.isCredentialValid(token.tokenId()))).isTrue();
        }

        @Test
        @DisplayName("revoke then isCredentialValid returns false")
        void revokeInvalidatesToken() {
            localResolver.register(identity("t1", "agent1"));
            CredentialToken token = runPromise(() -> service.issueCredential("t1", "agent1"));
            runBlocking(() -> service.revoke(token.tokenId()));
            boolean valid = runPromise(() -> service.isCredentialValid(token.tokenId()));
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("withResolvers chains until one resolver finds the identity")
        void withResolversChainsResolvers() {
            IdentityResolver miss = (tenantId, agentId) -> Promise.of(Optional.empty());
            AepLocalIdentityResolver hit = new AepLocalIdentityResolver();
            AgentIdentity id = identity("t1", "agent1");
            hit.register(id);

            IdentityResolutionService chainedService = IdentityResolutionService.withResolvers(
                List.of(miss, hit));

            Optional<AgentIdentity> result = runPromise(() ->
                chainedService.resolveIdentity("t1", "agent1"));

            assertThat(result).isPresent().hasValue(id);
        }
    }
}
