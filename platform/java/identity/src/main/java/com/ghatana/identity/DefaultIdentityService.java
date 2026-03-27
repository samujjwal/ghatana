/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import com.ghatana.identity.spi.IdentityResolver;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link IdentityService} implementation.
 *
 * <p>Delegates identity resolution to a pluggable {@link IdentityResolver} SPI.
 * Credential issuance is backed by an in-memory revocation registry (suitable for
 * single-node or testing). Production deployments should replace the credential store
 * with a distributed cache (e.g. Redis).
 *
 * @doc.type class
 * @doc.purpose Default identity service: resolution delegation + in-memory credential lifecycle
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DefaultIdentityService implements IdentityService {

    private static final Logger log = LoggerFactory.getLogger(DefaultIdentityService.class);
    private static final Duration MAX_TTL = Duration.ofHours(1);

    private final IdentityResolver resolver;
    /** tokenId → expiry; serves also as revocation registry (absent = revoked or expired). */
    private final Map<String, Instant> tokenExpiries = new ConcurrentHashMap<>();
    private final Map<String, CredentialToken> tokens = new ConcurrentHashMap<>();

    public DefaultIdentityService(IdentityResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Promise<Optional<AgentIdentity>> resolve(String tenantId, String agentId) {
        return resolver.resolve(tenantId, agentId);
    }

    @Override
    public Promise<CredentialToken> issueCredential(String tenantId, String agentId, Duration ttl) {
        Duration effectiveTtl = ttl.compareTo(MAX_TTL) > 0 ? MAX_TTL : ttl;
        Instant now = Instant.now();
        Instant expiresAt = now.plus(effectiveTtl);
        String tokenId = UUID.randomUUID().toString();
        // Minimal JWT simulation — production should use JwtTokenProvider from security/
        String fakeJwt = "Bearer-" + tokenId;
        CredentialToken token = new CredentialToken(tokenId, agentId, tenantId, now, expiresAt, fakeJwt);

        tokenExpiries.put(tokenId, expiresAt);
        tokens.put(tokenId, token);
        log.debug("Issued credential {} for agent {}/{} ttl={}", tokenId, tenantId, agentId, effectiveTtl);
        return Promise.of(token);
    }

    @Override
    public Promise<Void> revokeCredential(String tokenId) {
        tokenExpiries.remove(tokenId);
        tokens.remove(tokenId);
        log.debug("Revoked credential {}", tokenId);
        return Promise.complete();
    }

    @Override
    public Promise<Boolean> isCredentialValid(String tokenId) {
        Instant expiry = tokenExpiries.get(tokenId);
        if (expiry == null) {
            return Promise.of(false); // revoked or unknown
        }
        boolean valid = Instant.now().isBefore(expiry);
        if (!valid) {
            // Lazy eviction
            tokenExpiries.remove(tokenId);
            tokens.remove(tokenId);
        }
        return Promise.of(valid);
    }
}
