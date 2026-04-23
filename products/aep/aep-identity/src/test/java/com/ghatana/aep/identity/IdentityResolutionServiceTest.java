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
    void withResolversRejectsEmptyResolverList() { // GH-90000
        assertThatThrownBy(() -> IdentityResolutionService.withResolvers(List.of())) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("At least one IdentityResolver is required");
    }

    @Test
    @DisplayName("resolveIdentity returns the first resolver hit in the chain")
    void resolveIdentityReturnsFirstResolverHitInChain() { // GH-90000
        IdentityResolver miss = (tenantId, agentId) -> Promise.of(Optional.empty()); // GH-90000
        AepLocalIdentityResolver hit = new AepLocalIdentityResolver(); // GH-90000
        AgentIdentity identity = identity("tenant-a", "agent-1"); // GH-90000
        hit.register(identity); // GH-90000

        IdentityResolutionService service = IdentityResolutionService.withResolvers(List.of(miss, hit)); // GH-90000
        Optional<AgentIdentity> resolved = runPromise(() -> service.resolveIdentity("tenant-a", "agent-1")); // GH-90000

        assertThat(resolved).contains(identity); // GH-90000
    }

    @Test
    @DisplayName("issued credentials can be validated and revoked")
    void issuedCredentialsCanBeValidatedAndRevoked() { // GH-90000
        AepLocalIdentityResolver resolver = new AepLocalIdentityResolver(); // GH-90000
        resolver.register(identity("tenant-a", "agent-2")); // GH-90000
        IdentityResolutionService service = IdentityResolutionService.withResolvers(List.of(resolver)); // GH-90000

        CredentialToken token = runPromise(() -> service.issueCredential("tenant-a", "agent-2")); // GH-90000

        assertThat(runPromise(() -> service.isCredentialValid(token.tokenId()))).isTrue(); // GH-90000
        runBlocking(() -> service.revoke(token.tokenId())); // GH-90000
        assertThat(runPromise(() -> service.isCredentialValid(token.tokenId()))).isFalse(); // GH-90000
    }

    private static AgentIdentity identity(String tenantId, String agentId) { // GH-90000
        return new AgentIdentity( // GH-90000
            tenantId,
            agentId,
            "spiffe://ghatana/" + tenantId + "/" + agentId,
            Set.of("aep:execute"),
            Instant.now()); // GH-90000
    }
}
