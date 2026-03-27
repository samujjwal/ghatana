/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.identity;

import com.ghatana.identity.AgentIdentity;
import com.ghatana.identity.CredentialToken;
import com.ghatana.identity.DefaultIdentityService;
import com.ghatana.identity.IdentityService;
import com.ghatana.identity.spi.IdentityResolver;
import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;

/**
 * AEP-specific facade over the platform {@link IdentityService}.
 *
 * <p>Provides convenience methods for the AEP agent lifecycle:
 * <ul>
 *   <li>Bootstrapping an agent identity on first start.</li>
 *   <li>Refreshing credentials before expiry.</li>
 *   <li>Resolving identities using pluggable {@link IdentityResolver} SPI.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose AEP agent identity lifecycle facade over the platform identity module
 * @doc.layer product
 * @doc.pattern Service
 */
public final class IdentityResolutionService {

    /** Default credential TTL for AEP agents: 30 minutes. */
    public static final Duration DEFAULT_AGENT_CREDENTIAL_TTL = Duration.ofMinutes(30);

    private final IdentityService identityService;

    /**
     * Construct with a pre-configured {@link IdentityService}.
     *
     * @param identityService the platform identity service to delegate to
     */
    public IdentityResolutionService(IdentityService identityService) {
        this.identityService = identityService;
    }

    /**
     * Convenience factory: build an {@code IdentityResolutionService} backed by the
     * provided resolvers using the default in-memory identity service.
     *
     * @param resolvers ordered list of {@link IdentityResolver} implementations
     * @return a new, fully-initialised service
     */
    public static IdentityResolutionService withResolvers(List<IdentityResolver> resolvers) {
        return new IdentityResolutionService(new DefaultIdentityService(resolvers));
    }

    /**
     * Resolve the identity of an agent.
     *
     * @param tenantId the owning tenant
     * @param agentId  the agent identifier
     * @return promise resolving to the {@link AgentIdentity}, or empty if not found
     */
    public Promise<java.util.Optional<AgentIdentity>> resolveIdentity(
            String tenantId, String agentId) {
        return identityService.resolve(tenantId, agentId);
    }

    /**
     * Issue a short-lived credential for the specified agent using the default TTL.
     *
     * @param tenantId the owning tenant
     * @param agentId  the agent to issue a credential for
     * @return promise resolving to the issued {@link CredentialToken}
     */
    public Promise<CredentialToken> issueCredential(String tenantId, String agentId) {
        return identityService.issueCredential(tenantId, agentId, DEFAULT_AGENT_CREDENTIAL_TTL);
    }

    /**
     * Revoke a specific credential token (e.g. during agent shutdown or incident).
     *
     * @param tokenId the credential token ID to revoke
     * @return completed promise on success
     */
    public Promise<Void> revoke(String tokenId) {
        return identityService.revokeCredential(tokenId);
    }

    /**
     * Check whether a credential token is still valid.
     *
     * @param tokenId the token to validate
     * @return promise resolving to {@code true} if the token is valid and not expired
     */
    public Promise<Boolean> isCredentialValid(String tokenId) {
        return identityService.isCredentialValid(tokenId);
    }
}
