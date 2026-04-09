/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.identity;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default in-memory {@link DelegationTokenService} implementation.
 *
 * <p>Suitable for testing and single-node deployments. Production deployments
 * should use a distributed token store backed by Redis or a secrets manager to
 * support cross-node revocation.
 *
 * @doc.type class
 * @doc.purpose Default in-memory delegation token service with principal chain tracking
 * @doc.layer platform
 * @doc.pattern Service
 */
public final class DefaultDelegationTokenService implements DelegationTokenService {

    private static final Logger log = LoggerFactory.getLogger(DefaultDelegationTokenService.class);
    private static final Duration MAX_DELEGATION_TTL = Duration.ofHours(8);

    private final Map<String, DelegationToken> store = new ConcurrentHashMap<>();

    @Override
    public Promise<DelegationToken> delegate(String tenantId,
                                              String delegatorId,
                                              String delegateeId,
                                              Set<String> scopes,
                                              Duration ttl) {
        Duration effective = ttl.compareTo(MAX_DELEGATION_TTL) > 0 ? MAX_DELEGATION_TTL : ttl;
        Instant now = Instant.now();
        String tokenId = UUID.randomUUID().toString();

        DelegationToken token = new DelegationToken(
            tokenId, tenantId, delegatorId, delegateeId, scopes,
            now, now.plus(effective), null, List.of(delegatorId, delegateeId)
        );
        store.put(tokenId, token);
        log.debug("Delegation {} issued: {} -> {} scopes={}", tokenId, delegatorId, delegateeId, scopes);
        return Promise.of(token);
    }

    @Override
    public Promise<Optional<DelegationToken>> validate(String tokenId) {
        DelegationToken token = store.get(tokenId);
        if (token == null) {
            return Promise.of(Optional.empty());
        }
        if (token.isExpired()) {
            store.remove(tokenId);
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.of(token));
    }

    @Override
    public Promise<Void> revoke(String tokenId) {
        store.remove(tokenId);
        log.debug("Delegation {} revoked", tokenId);
        return Promise.complete();
    }
}
