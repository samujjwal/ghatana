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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Dedicated tests for {@link IdentityResolutionService}.
 *
 * @doc.type class
 * @doc.purpose Verify identity resolution, credential issuance, and chained resolvers
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("IdentityResolutionServiceTest")
class IdentityResolutionServiceTest extends EventloopTestBase {

    @Test
    @DisplayName("withResolvers rejects an empty resolver list")
    void withResolversRejectsEmptyResolverList() {
        assertThatThrownBy(() -> IdentityResolutionService.withResolvers(List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one IdentityResolver is required");
    }

    @Test
    @DisplayName("resolveIdentity returns the first resolver hit in the chain")
    void resolveIdentityReturnsFirstResolverHitInChain() {
        IdentityResolver miss = (tenantId, agentId) -> Promise.of(Optional.empty());
        AepLocalIdentityResolver hit = new AepLocalIdentityResolver();
        AgentIdentity identity = identity("tenant-a", "agent-1");
        hit.register(identity);

        IdentityResolutionService service = IdentityResolutionService.withResolvers(List.of(miss, hit));
        Optional<AgentIdentity> resolved = runPromise(() -> service.resolveIdentity("tenant-a", "agent-1"));

        assertThat(resolved).contains(identity);
    }

    @Test
    @DisplayName("issued credentials can be validated and revoked")
    void issuedCredentialsCanBeValidatedAndRevoked() {
        AepLocalIdentityResolver resolver = new AepLocalIdentityResolver();
        resolver.register(identity("tenant-a", "agent-2"));
        IdentityResolutionService service = IdentityResolutionService.withResolvers(List.of(resolver));

        CredentialToken token = runPromise(() -> service.issueCredential("tenant-a", "agent-2"));

        assertThat(runPromise(() -> service.isCredentialValid(token.tokenId()))).isTrue();
        runBlocking(() -> service.revoke(token.tokenId()));
        assertThat(runPromise(() -> service.isCredentialValid(token.tokenId()))).isFalse();
    }

    private static AgentIdentity identity(String tenantId, String agentId) {
        return new AgentIdentity(
            tenantId,
            agentId,
            "spiffe://ghatana/" + tenantId + "/" + agentId,
            Set.of("aep:execute"),
            Instant.now());
    }
}
