/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.security;

import java.time.Duration;

/**
 * T-20: Pluggable session token store for {@link SessionFilter}.
 *
 * <p>Abstracts the storage backend for AEP session tokens so that
 * {@link InMemorySessionStore} (dev/test) can be swapped for
 * {@link RedisSessionStore} (production) without changing filter logic.
 *
 * @doc.type interface
 * @doc.purpose Pluggable session store for AEP session tokens
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface SessionStore {

    /**
     * Stores a session token with the given TTL.
     *
     * @param token session token string (must not be null or blank)
     * @param ttl   how long the token should be valid (must be positive)
     */
    void put(String token, Duration ttl);

    /**
     * Returns {@code true} if the token exists and has not expired.
     *
     * @param token session token string
     * @return true when valid
     */
    boolean isValid(String token);

    /**
     * Invalidates a token, removing it from the store.
     *
     * @param token session token to remove
     */
    void remove(String token);
}
